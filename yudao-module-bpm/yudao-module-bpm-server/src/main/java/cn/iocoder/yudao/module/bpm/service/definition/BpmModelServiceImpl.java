package cn.iocoder.yudao.module.bpm.service.definition;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelMetaInfoVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelUpdateReqVO;
import cn.iocoder.yudao.module.bpm.convert.definition.BpmModelConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelFormTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmReasonEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.SimpleModelUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceCopyService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.*;
import org.flowable.common.engine.impl.db.SuspensionState;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.Model;
import org.flowable.engine.repository.ModelQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.parseCandidateStrategy;

/**
 * 流程模型服务实现类
 *
 * 【核心职责】
 * 这个类负责管理工作流程的模型（可以理解为"流程模板"），就像是：
 * - 设计一个请假流程应该怎么走（员工申请 -> 主管审批 -> HR备案）
 * - 设计一个报销流程应该怎么审批（提交报销单 -> 财务审核 -> 总经理批准）
 *
 * 【支持的模型类型】
 * 1. BPMN：标准的流程描述语言（类似专业的流程图，用XML格式存储）
 * 2. SIMPLE：简易模式（仿钉钉的拖拽式设计，用JSON存储，后台自动转成BPMN）
 *
 * 【主要功能】
 * - 创建/更新/删除流程模型
 * - 部署模型（让模型变成可以实际使用的流程）
 * - 管理模型状态（启用/停用）
 * - 校验流程设计是否合法
 *
 * @author yunlongn
 * @author 芋道源码
 * @author jason
 */
@Service  // Spring服务层注解，告诉Spring这是一个业务逻辑类
@Validated  // 启用参数校验功能
@Slf4j  // Lombok注解，自动生成日志对象log
public class BpmModelServiceImpl implements BpmModelService {

    // =================== 依赖注入的服务 ===================

    /**
     * Flowable仓库服务
     * 作用：管理流程定义、模型等资源，就像一个"流程仓库管理员"
     */
    @Resource
    private RepositoryService repositoryService;

    /**
     * 流程定义服务
     * 作用：管理已部署的流程定义（可以理解为"可执行的流程版本"）
     */
    @Resource
    private BpmProcessDefinitionService processDefinitionService;

    /**
     * 表单服务
     * 作用：管理流程表单（流程启动时填写的页面）
     */
    @Resource
    private BpmFormService bpmFormService;

    /**
     * 任务候选人调用器
     * 作用：处理"谁来审批"的逻辑（角色、部门、指定人等）
     */
    @Resource
    private BpmTaskCandidateInvoker taskCandidateInvoker;

    /**
     * 历史服务
     * 作用：查询和管理已完成的流程数据
     */
    @Resource
    private HistoryService historyService;

    /**
     * 运行时服务
     * 作用：管理正在运行中的流程实例
     */
    @Resource
    private RuntimeService runtimeService;

    /**
     * 任务服务
     * 作用：管理流程中的待办任务
     */
    @Resource
    private TaskService taskService;

    /**
     * 流程抄送服务
     * 作用：管理流程的抄送记录（谁被抄送了这个流程）
     */
    @Resource
    private BpmProcessInstanceCopyService processInstanceCopyService;

    // =================== 查询相关方法 ===================

    /**
     * 查询流程模型列表
     *
     * 【使用场景】
     * 在管理后台展示所有流程模型，支持按名称搜索
     *
     * 【租户隔离】
     * 每个租户（公司）只能看到自己的流程模型
     *
     * @param name 模型名称，支持模糊查询（如输入"请假"可以查到"员工请假流程"）
     * @return 模型列表
     */
    @Override
    public List<Model> getModelList(String name) {
        // 创建查询构造器
        ModelQuery modelQuery = repositoryService.createModelQuery();

        // 如果传入了名称，添加模糊查询条件
        if (StrUtil.isNotEmpty(name)) {
            modelQuery.modelNameLike("%" + name + "%");  // %表示通配符，匹配任意字符
        }

        // 添加租户过滤（多租户隔离的关键）
        modelQuery.modelTenantId(FlowableUtils.getTenantId());

        // 执行查询并返回结果
        return modelQuery.list();
    }

    /**
     * 统计某个分类下有多少个模型
     *
     * 【使用场景】
     * - 删除分类前检查是否有模型在使用
     * - 统计各分类的流程数量
     *
     * @param category 分类标识（如：OA、HR、财务等）
     * @return 该分类下的模型数量
     */
    @Override
    public Long getModelCountByCategory(String category) {
        return repositoryService.createModelQuery()
                .modelCategory(category)  // 按分类过滤
                .modelTenantId(FlowableUtils.getTenantId())  // 租户隔离
                .count();  // 统计数量而非查询列表
    }

    // =================== 创建/更新模型 ===================

    /**
     * 创建新的流程模型
     *
     * 【核心流程】
     * 1. 校验模型key是否符合规范（不能有中文、特殊符号等）
     * 2. 校验key是否已被使用（同一租户下key必须唯一）
     * 3. 创建Model对象并保存基本信息
     * 4. 根据模型类型保存流程图（BPMN的XML或Simple的JSON）
     *
     * 【重要概念】
     * Model Key：流程的唯一标识，类似身份证号，创建后不能修改
     *
     * @param createReqVO 创建请求对象（包含流程名称、key、类型、流程图等）
     * @return 新创建的模型ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)  // 事务注解：任何异常都回滚
    public String createModel(@Valid BpmModelSaveReqVO createReqVO) {
        // 步骤1：校验key的合法性
        // XML NCName规范：只能包含字母、数字、下划线、连字符，不能以数字开头
        if (!ValidationUtils.isXmlNCName(createReqVO.getKey())) {
            throw exception(MODEL_KEY_VALID);  // 抛出异常：key格式不合法
        }

        // 步骤2：检查key是否已存在（防止重复）
        Model keyModel = getModelByKey(createReqVO.getKey());
        if (keyModel != null) {
            // 如果已存在同名key，抛出异常
            throw exception(MODEL_KEY_EXISTS, createReqVO.getKey());
        }

        // 步骤3：构建Model对象
        createReqVO.setSort(System.currentTimeMillis()); // 使用当前时间戳作为排序值（新的排在前面）
        Model model = repositoryService.newModel();  // 创建空的Model对象
        BpmModelConvert.INSTANCE.copyToModel(model, createReqVO);  // 将请求参数复制到Model对象
        model.setTenantId(FlowableUtils.getTenantId());  // 设置租户ID（实现多租户隔离）

        // 步骤4：保存模型及流程图
        saveModel(model, createReqVO);

        return model.getId();  // 返回新创建的模型ID
    }

    /**
     * 更新流程模型
     *
     * 【权限控制】
     * 只有模型的管理员才能更新，普通用户不能修改
     *
     * 【注意事项】
     * - 模型的key不能修改（唯一标识）
     * - 更新后需要重新部署才能生效
     *
     * @param userId      当前登录用户ID
     * @param updateReqVO 更新请求对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModel(Long userId, BpmModelSaveReqVO updateReqVO) {
        // 步骤1：校验模型存在，并且当前用户是管理员
        Model model = validateModelManager(updateReqVO.getId(), userId);

        // 步骤2：更新模型的基本信息（名称、描述、分类等）
        BpmModelConvert.INSTANCE.copyToModel(model, updateReqVO);

        // 步骤3：保存模型及流程图
        saveModel(model, updateReqVO);
    }

    /**
     * 保存模型的通用方法（创建和更新都会调用）
     *
     * 【处理逻辑】
     * 1. 保存模型基础信息（到act_re_model表）
     * 2. 根据模型类型保存流程图：
     *    - BPMN类型：直接保存XML字符串
     *    - SIMPLE类型：先将JSON转为BPMN模型，再保存XML和原始JSON
     *
     * @param model       Flowable的Model对象
     * @param saveReqVO   保存请求对象
     */
    private void saveModel(Model model, BpmModelSaveReqVO saveReqVO) {
        // 步骤1：保存模型基础信息（名称、key、分类、描述、管理员等）
        repositoryService.saveModel(model);

        // 步骤2：根据模型类型保存流程图
        if (ObjUtil.equals(BpmModelTypeEnum.BPMN.getType(), saveReqVO.getType())
                && StrUtil.isNotEmpty(saveReqVO.getBpmnXml())) {
            // 情况1：BPMN类型 - 直接保存XML字符串
            updateModelBpmnXml(model.getId(), saveReqVO.getBpmnXml());

        } else if (ObjUtil.equals(BpmModelTypeEnum.SIMPLE.getType(), saveReqVO.getType())
                && saveReqVO.getSimpleModel() != null) {
            // 情况2：SIMPLE类型 - 需要转换
            // 将JSON格式的简易模型转换为标准的BPMN模型
            BpmnModel bpmnModel = SimpleModelUtils.buildBpmnModel(model.getKey(), model.getName(),
                    saveReqVO.getSimpleModel());

            // 保存转换后的BPMN XML（用于实际执行）
            updateModelBpmnXml(model.getId(), BpmnModelUtils.getBpmnXml(bpmnModel));

            // 同时保存原始的JSON（用于设计器回显）
            updateModelSimpleJson(model.getId(), saveReqVO.getSimpleModel());
        }
    }

    // =================== 排序批量更新 ===================

    /**
     * 批量更新模型的显示顺序
     *
     * 【使用场景】
     * 管理员在后台拖拽调整流程模型的显示顺序
     *
     * 【实现原理】
     * 使用时间戳作为排序字段：
     * - 越靠前的模型，时间戳越大
     * - 从后往前遍历，逐个递减时间戳
     *
     * 【同步操作】
     * 同时更新流程定义的排序，保证启动页的顺序一致
     *
     * @param userId 当前用户ID
     * @param ids    模型ID列表（按前端拖拽后的顺序排列）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModelSortBatch(Long userId, List<String> ids) {
        // 步骤1.1：批量查询模型并校验数量
        List<Model> models = repositoryService.createModelQuery()
                .modelTenantId(FlowableUtils.getTenantId()).list();  // 查询当前租户的所有模型

        // 过滤出需要排序的模型
        models.removeIf(model -> !ids.contains(model.getId()));

        // 校验：如果传入的ID数量和查询出的模型数量不一致，说明有ID不存在
        if (ids.size() != models.size()) {
            throw exception(MODEL_NOT_EXISTS);
        }

        // 将模型列表转为Map，方便后续根据ID快速获取
        Map<String, Model> modelMap = convertMap(models, Model::getId);

        // 步骤1.2：校验每个模型的管理权限
        ids.forEach(id -> validateModelManager(id, userId));

        // 步骤2：按倒序分配排序值
        long sort = System.currentTimeMillis();  // 获取当前时间戳作为初始值

        // 从后往前遍历（重要！保证第一个元素的sort最大）
        for (int i = ids.size() - 1; i >= 0; i--) {
            Model model = modelMap.get(ids.get(i));

            // 解析模型的元信息并更新排序值
            BpmModelMetaInfoVO metaInfo = BpmModelConvert.INSTANCE.parseMetaInfo(model).setSort(sort);
            model.setMetaInfo(JsonUtils.toJsonString(metaInfo));
            repositoryService.saveModel(model);

            // 同步更新对应��程定义的排序（保证启动页顺序一致）
            processDefinitionService.updateProcessDefinitionSortByModelId(model.getId(), sort);

            sort--;  // 递减，下一个模型的排序值更小
        }
    }

    // =================== 模型校验工具方法 ===================

    /**
     * 校验模型是否存在
     *
     * 【作用】
     * 防止操作不存在的模型导致空指针异常
     *
     * @param id 模型ID
     * @return 模型对象
     * @throws 模型不存在异常
     */
    private Model validateModelExists(String id) {
        Model model = repositoryService.getModel(id);
        if (model == null) {
            throw exception(MODEL_NOT_EXISTS);  // 抛出业务异常：模型不存在
        }
        return model;
    }

    /**
     * 校验用户是否为模型管理员
     *
     * 【权限控制】
     * 只有模型的管理员才能进行：
     * - 更新模型
     * - 删除模型
     * - 部署模型
     * - 修改模型状态
     *
     * 【实现原理】
     * 从模型的元信息中读取管理员ID列表，判断当前用户是否在列表中
     *
     * @param id     模型ID
     * @param userId 用户ID
     * @return 模型对象
     * @throws 模型不存在或无权限异常
     */
    private Model validateModelManager(String id, Long userId) {
        // 先校验模型存在
        Model model = validateModelExists(id);

        // 解析模型的元信息（包含管理员列表）
        BpmModelMetaInfoVO metaInfo = BpmModelConvert.INSTANCE.parseMetaInfo(model);

        // 判断当前用户是否在管理员列表中
        if (metaInfo == null || !CollUtil.contains(metaInfo.getManagerUserIds(), userId)) {
            throw exception(MODEL_UPDATE_FAIL_NOT_MANAGER, model.getName());
        }

        return model;
    }

    // =================== 部署模型 ===================

    /**
     * 部署流程模型（核心功能！）
     *
     * 【什么是部署】
     * 就像发布软件一样，把设计好的流程模型"发布上线"，让它可以被实际使用
     *
     * 【部署前的严格校验】
     * 1. 用户权限校验：必须是管理员
     * 2. BPMN合法性校验：流程图必须符合规范
     * 3. 表单配置校验：必须配置流程表单
     * 4. 任务分配规则校验：审批人配置必须合法
     *
     * 【部署后的操作】
     * 1. 创建新的流程定义（版本号+1）
     * 2. 挂起旧版本（防止误用）
     * 3. 关联deploymentId到模型
     *
     * @param userId 当前用户ID
     * @param id     模型ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deployModel(Long userId, String id) {
        // ==== 第一阶段：校验 ====

        // 1.1 校验用户是模型管理员
        Model model = validateModelManager(id, userId);
        BpmModelMetaInfoVO metaInfo = BpmModelConvert.INSTANCE.parseMetaInfo(model);

        // 1.2 获取BPMN XML并校验其合法性
        byte[] bpmnBytes = getModelBpmnXML(model.getId());
        validateBpmnXml(bpmnBytes, metaInfo.getType());

        // 1.3 校验流程表单已配置
        BpmFormDO form = validateFormConfig(metaInfo);

        // 1.4 校验任务分配规则（审批人、角色等配置）
        taskCandidateInvoker.validateBpmnConfig(bpmnBytes);

        // 1.5 获取简易模型的JSON（如果是SIMPLE类型）
        String simpleJson = getModelSimpleJson(model.getId());

        // ==== 第二阶段：部署 ====

        // 2.1 创建流程定义（保存到数据库）
        String definitionId = processDefinitionService.createProcessDefinition(
                model, metaInfo, bpmnBytes, simpleJson, form);

        // 2.2 挂起旧版本的流程定义（确保只有最新版本可用）
        updateProcessDefinitionSuspended(model.getDeploymentId());

        // 2.3 更新模型的deploymentId（关联到新部署）
        ProcessDefinition definition = processDefinitionService.getProcessDefinition(definitionId);
        model.setDeploymentId(definition.getDeploymentId());
        repositoryService.saveModel(model);
    }

    /**
     * 校验BPMN XML的合法性
     *
     * 【校验规则】
     * 1. XML必须能正确解析为BpmnModel
     * 2. 必须有开始事件（流程的起点）
     * 3. 所有用户任务必须有名称（用于展示审批节点）
     * 4. 第一个审批节点不能是"审批人自选"（否则流程无法启动）
     *
     * 【BPMN vs SIMPLE的区别】
     * - BPMN：第一个UserTask就是第一个审批节点
     * - SIMPLE：第二个UserTask才是第一个审批节点（第一个是发起人节点）
     *
     * @param bpmnBytes BPMN XML的字节数组
     * @param type      模型类型（BPMN或SIMPLE）
     */
    private void validateBpmnXml(byte[] bpmnBytes, Integer type) {
        // 解析BPMN XML为对象模型
        BpmnModel bpmnModel = BpmnModelUtils.getBpmnModel(bpmnBytes);
        if (bpmnModel == null) {
            throw exception(MODEL_NOT_EXISTS);  // XML解析失败
        }

        // 规则1：必须有开始事件
        StartEvent startEvent = BpmnModelUtils.getStartEvent(bpmnModel);
        if (startEvent == null) {
            throw exception(MODEL_DEPLOY_FAIL_BPMN_START_EVENT_NOT_EXISTS);
        }

        // 规则2：获取所有用户任务（UserTask = 需要人工审批的节点）
        List<UserTask> userTasks = BpmnModelUtils.getBpmnModelElements(bpmnModel, UserTask.class);
        userTasks.forEach(userTask -> {
            // 每个用户任务必须有名称
            if (StrUtil.isEmpty(userTask.getName())) {
                throw exception(MODEL_DEPLOY_FAIL_BPMN_USER_TASK_NAME_NOT_EXISTS, userTask.getId());
            }
        });

        // 规则3：第一个审批节点不能是"审批人自选"
        // 确定第一个审批节点的索引
        int index = BpmModelTypeEnum.BPMN.getType().equals(type) ? 0 : 1;
        UserTask firUserTask = CollUtil.get(userTasks, index);

        if (firUserTask != null) {
            // 解析任务的候选人策略
            Integer candidateStrategy = parseCandidateStrategy(firUserTask);

            // 如果是"审批人自选"策略，抛出异常
            if (Objects.equals(candidateStrategy, BpmTaskCandidateStrategyEnum.APPROVE_USER_SELECT.getStrategy())) {
                throw exception(MODEL_DEPLOY_FAIL_FIRST_USER_TASK_CANDIDATE_STRATEGY_ERROR, firUserTask.getName());
            }
        }
    }

    // =================== 删除/清理 ===================

    /**
     * 删除流程模型
     *
     * 【删除范围】
     * 只删除模型本身，不影响：
     * - 已部署的流程定义（可以继续使用）
     * - 正在运行的流程实例（继续执行）
     *
     * 【同时操作】
     * 挂起关联的流程定义（防止误用旧版本）
     *
     * @param userId 当前用户ID
     * @param id     模型ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(Long userId, String id) {
        // 校验权限
        Model model = validateModelManager(id, userId);

        // 删除模型
        repositoryService.deleteModel(id);

        // 挂起关联的流程定义（防止继续使用）
        updateProcessDefinitionSuspended(model.getDeploymentId());
    }

    /**
     * 彻底清理模型相关的所有数据（危险操作！）
     *
     * 【警告】
     * 这是一个非常危险的操作，会删除：
     * - 所有运行中的流程实例（强制终止）
     * - 所有历史流程记录
     * - 所有相关任务
     *
     * 【适用场景】
     * - 测试环境清理脏数据
     * - 流程废弃后彻底清除
     *
     * 【生产环境慎用】
     *
     * @param userId 当前用户ID
     * @param id     模型ID
     */
    @Override
    public void cleanModel(Long userId, String id) {
        // 校验权限
        Model model = validateModelManager(id, userId);

        // 步骤1：清理所有运行中的流程实例
        List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(model.getKey()).list();

        processInstances.forEach(processInstance -> {
            // 删除运行时数据
            runtimeService.deleteProcessInstance(processInstance.getId(),
                    BpmReasonEnum.CANCEL_BY_SYSTEM.getReason());

            // 删除历史记录
            historyService.deleteHistoricProcessInstance(processInstance.getId());

            // 删除抄送记录
            processInstanceCopyService.deleteProcessInstanceCopy(processInstance.getId());
        });

        // 步骤2：清理历史流程实例（已完成但未删除的）
        List<HistoricProcessInstance> historicProcessInstances = historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey(model.getKey()).list();

        historicProcessInstances.forEach(historicProcessInstance -> {
            historyService.deleteHistoricProcessInstance(historicProcessInstance.getId());
            processInstanceCopyService.deleteProcessInstanceCopy(historicProcessInstance.getId());
        });

        // 步骤3：清理残留的任务（理论上不应存在，保险起见）
        List<Task> tasks = taskService.createTaskQuery()
                .processDefinitionKey(model.getKey()).list();

        tasks.forEach(task -> taskService.deleteTask(task.getId(),
                BpmReasonEnum.CANCEL_BY_PROCESS_CLEAN.getReason()));
    }

    // =================== 状态管理 ===================

    /**
     * 更新流程的启用/停用状态
     *
     * 【状态说明】
     * - 激活（ACTIVE）：流程可以正常启动和使用
     * - 挂起（SUSPENDED）：流程被暂停，无法启动新实例
     *
     * 【使用场景】
     * - 流程需要临时下线维护
     * - 某些流程在特定时期不允许使用
     *
     * @param userId 当前用户ID
     * @param id     模型ID
     * @param state  目标状态（1=激活，2=挂起）
     */
    @Override
    public void updateModelState(Long userId, String id, Integer state) {
        // 校验权限
        Model model = validateModelManager(id, userId);

        // 根据deploymentId查找流程定义
        ProcessDefinition definition = processDefinitionService
                .getProcessDefinitionByDeploymentId(model.getDeploymentId());

        if (definition == null) {
            throw exception(PROCESS_DEFINITION_NOT_EXISTS);
        }

        // 更新流程定义的状态
        processDefinitionService.updateProcessDefinitionState(definition.getId(), state);
    }

    // =================== 获取模型数据 ===================

    /**
     * 根据流程定义ID获取BPMN模型
     *
     * 【使用场景】
     * - 流程图展示（可视化显示流程走向）
     * - 流程跟踪（高亮显示当前执行到哪个节点）
     *
     * @param processDefinitionId 流程定义ID
     * @return BPMN模型对象
     */
    @Override
    public BpmnModel getBpmnModelByDefinitionId(String processDefinitionId) {
        return repositoryService.getBpmnModel(processDefinitionId);
    }

    /**
     * 获取简易模型的JSON结构
     *
     * 【使用场景】
     * 简易设计器打开模型时，需要回显原始的JSON结构
     *
     * @param modelId 模型ID
     * @return 简易模型节点VO（包含流程节点、连线等信息）
     */
    @Override
    public BpmSimpleModelNodeVO getSimpleModel(String modelId) {
        // 校验模型存在
        Model model = validateModelExists(modelId);

        // 获取存储的JSON字符串
        String json = getModelSimpleJson(model.getId());

        // 解析为对象并返回
        return JsonUtils.parseObject(json, BpmSimpleModelNodeVO.class);
    }

    /**
     * 更新简易模型
     *
     * 【处理流程】
     * 1. 将前端传来的JSON转换为BPMN模型
     * 2. 保存BPMN XML（用于执行）
     * 3. 保存原始JSON（用于设计器回显）
     *
     * @param userId 当前用户ID
     * @param reqVO  更新请求对象
     */
    @Override
    public void updateSimpleModel(Long userId, BpmSimpleModelUpdateReqVO reqVO) {
        // 校验权限
        Model model = validateModelManager(reqVO.getId(), userId);

        // 将JSON转为BPMN模型
        BpmnModel bpmnModel = SimpleModelUtils.buildBpmnModel(model.getKey(), model.getName(), reqVO.getSimpleModel());

        // 保存BPMN XML
        updateModelBpmnXml(model.getId(), BpmnModelUtils.getBpmnXml(bpmnModel));

        // 保存原始JSON
        updateModelSimpleJson(model.getId(), reqVO.getSimpleModel());
    }

    // =================== 表单校验 ===================

    /**
     * 校验流程表单配置是否正确
     *
     * 【表单类型】
     * 1. 普通表单：使用系统的表单设计器设计的表单
     * 2. 自定义表单：开发者自己开发的前端页面
     *
     * 【校验规则】
     * - 普通表单：必须指定表单ID，且表单必须存在
     * - 自定义表单：必须提供创建页面和查看页面的路径
     *
     * @param metaInfo 模型元信息
     * @return 表单对象（普通表单）或null（自定义表单）
     */
    private BpmFormDO validateFormConfig(BpmModelMetaInfoVO metaInfo) {
        // 检查是否配置了表单类型
        if (metaInfo == null || metaInfo.getFormType() == null) {
            throw exception(MODEL_DEPLOY_FAIL_FORM_NOT_CONFIG);
        }

        if (Objects.equals(metaInfo.getFormType(), BpmModelFormTypeEnum.NORMAL.getType())) {
            // 情况1：普通表单
            if (metaInfo.getFormId() == null) {
                throw exception(MODEL_DEPLOY_FAIL_FORM_NOT_CONFIG);
            }

            // 查询表单是否存在
            BpmFormDO form = bpmFormService.getForm(metaInfo.getFormId());
            if (form == null) {
                throw exception(FORM_NOT_EXISTS);
            }
            return form;

        } else {
            // 情况2：自定义表单
            // 必须配置创建页面和查看页面的路径
            if (StrUtil.isEmpty(metaInfo.getFormCustomCreatePath())
                    || StrUtil.isEmpty(metaInfo.getFormCustomViewPath())) {
                throw exception(MODEL_DEPLOY_FAIL_FORM_NOT_CONFIG);
            }
            return null;
        }
    }

    // =================== 底层存储操作 ===================

    /**
     * 更新模型的BPMN XML
     *
     * 【存储位置】
     * Flowable的act_ge_bytearray表，通过EDITOR_SOURCE_VALUE_ID_字段关联
     *
     * 【用途】
     * 存储流程的可执行定义（引擎运行时使用）
     *
     * @param id      模型ID
     * @param bpmnXml BPMN XML字符串
     */
    @Override
    public void updateModelBpmnXml(String id, String bpmnXml) {
        if (StrUtil.isEmpty(bpmnXml)) {
            return;
        }
        // 将XML字符串转为UTF-8字节数组并保存
        repositoryService.addModelEditorSource(id, StrUtil.utf8Bytes(bpmnXml));
    }

    /**
     * 获取模型的简易JSON
     *
     * 【存储位置】
     * Flowable的act_ge_bytearray表，通过EDITOR_SOURCE_EXTRA_VALUE_ID_字段关联
     *
     * 【用途】
     * 存储简易设计器的原始JSON，用于设计器回显
     *
     * @param id 模型ID
     * @return JSON字符串
     */
    private String getModelSimpleJson(String id) {
        // 获取字节数组
        byte[] bytes = repositoryService.getModelEditorSourceExtra(id);
        if (ArrayUtil.isEmpty(bytes)) {
            return null;
        }
        // 转换为UTF-8字符串
        return StrUtil.utf8Str(bytes);
    }

    /**
     * 更新模型的简易JSON
     *
     * @param id   模型ID
     * @param node 简易模型节点对象
     */
    private void updateModelSimpleJson(String id, BpmSimpleModelNodeVO node) {
        if (node == null) {
            return;
        }
        // 将对象转为JSON字节数组并保存
        byte[] bytes = JsonUtils.toJsonByte(node);
        repositoryService.addModelEditorSourceExtra(id, bytes);
    }

    /**
     * 挂起流程定义
     *
     * 【什么是挂起】
     * 让流程定义进入"暂停"状态，无法启动新的流程实例
     *
     * 【使用场景】
     * - 部署新版本时，挂起旧版本
     * - 删除模型时，挂起相关定义
     *
     * @param deploymentId 部署ID
     */
    private void updateProcessDefinitionSuspended(String deploymentId) {
        if (StrUtil.isEmpty(deploymentId)) {
            return;
        }

        // 根据部署ID查询流程定义
        ProcessDefinition oldDefinition = processDefinitionService.getProcessDefinitionByDeploymentId(deploymentId);
        if (oldDefinition == null) {
            return;
        }

        // 更新状态为"挂起"
        processDefinitionService.updateProcessDefinitionState(oldDefinition.getId(),
                SuspensionState.SUSPENDED.getStateCode());
    }

    // =================== 工具方法 ===================

    /**
     * 根据key查询模型
     *
     * 【租户隔离】
     * 只查询当前租户的模型
     *
     * @param key 模型key
     * @return 模型对象，不存在则返回null
     */
    private Model getModelByKey(String key) {
        return repositoryService.createModelQuery()
                .modelTenantId(FlowableUtils.getTenantId())
                .modelKey(key).singleResult();  // singleResult()表示只返回一条记录
    }

    /**
     * 根据ID获取模型（不校验权限）
     *
     * 【注意】
     * 这个方法不校验权限，适用于内部调用
     * 如果是用户请求，应该先调用validateModelManager
     *
     * @param id 模型ID
     * @return 模型对象
     */
    @Override
    public Model getModel(String id) {
        return repositoryService.getModel(id);
    }

    /**
     * 获取模型的BPMN XML字节流
     *
     * 【返回格式】
     * UTF-8编码的字节数组
     *
     * @param id 模型ID
     * @return BPMN XML的字节数组
     */
    @Override
    public byte[] getModelBpmnXML(String id) {
        return repositoryService.getModelEditorSource(id);
    }

}