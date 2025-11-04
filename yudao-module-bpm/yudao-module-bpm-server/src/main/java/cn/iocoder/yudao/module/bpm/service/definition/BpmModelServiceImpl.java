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
 * 流程模型实现类：主要负责 Flowable {@link Model} 的创建、更新、部署、删除等全生命周期管理。
 * 支持两种模型类型：
 *   - BPMN：标准 BPMN 2.0 XML 格式（专业流程设计器）
 *   - SIMPLE：仿钉钉快搭的简易流程模型（使用 JSON 描述，后端自动转换为 BPMN）
 *
 * 【核心职责】
 * 1. 管理 Flowable 流程模型（BPMN 设计器和 Simple 仿钉钉设计器）
 * 2. 流程模型的部署和发布
 * 3. 流程模型的生命周期管理（创建、更新、删除、启用/挂起）
 *
 * 【Flowable 核心概念】
 * - Model：流程模型，包含流程的设计信息（BPMN XML 或简易模型 JSON）
 * - ProcessDefinition：流程定义，Model 部署后生成，可执行的流程蓝图
 * - Deployment：部署，一次部署操作会生成一个或多个 ProcessDefinition
 * - ProcessInstance：流程实例，ProcessDefinition 的运行时实例
 *
 * @author yunlongn
 * @author 芋道源码
 * @author jason
 */
@Service
@Validated
@Slf4j
public class BpmModelServiceImpl implements BpmModelService {

    // ==================== Flowable 核心服务 ====================

    @Resource
    private RepositoryService repositoryService; // 仓库服务：管理流程定义、模型、部署等静态资源

    @Resource
    private RuntimeService runtimeService; // 运行时服务：管理流程实例、执行对象等运行时数据

    @Resource
    private HistoryService historyService; // 历史服务：查询已完成的流程实例、任务等历史数据

    @Resource
    private TaskService taskService; // 任务服务：管理用户任务的查询、完成、委派等操作

    // ==================== 业务服务 ====================

    @Resource
    private BpmProcessDefinitionService processDefinitionService; // 流程定义业务服务：处理流程定义的创建、查询、状态管理

    @Resource
    private BpmFormService bpmFormService; // 流程表单服务：管理流程关联的动态表单

    @Resource
    private BpmTaskCandidateInvoker taskCandidateInvoker; // 任务候选人计算器：根据配置的规则动态计算任务的审批人

    @Resource
    private BpmProcessInstanceCopyService processInstanceCopyService; // 流程抄送服务：管理流程实例的抄送记录

    /**
     * 查询流程模型列表
     *
     * @param name 模型名称（支持模糊查询）
     * @return 流程模型列表
     */
    @Override
    public List<Model> getModelList(String name) {
        // 创建模型查询构建器
        ModelQuery modelQuery = repositoryService.createModelQuery();

        // 按名称模糊查询
        if (StrUtil.isNotEmpty(name)) {
            modelQuery.modelNameLike("%" + name + "%");
        }

        // 多租户隔离：只查询当前租户的模型
        modelQuery.modelTenantId(FlowableUtils.getTenantId());

        return modelQuery.list();
    }

    /**
     * 统计指定分类下的流程模型数量
     *
     * @param category 流程模型分类
     * @return 模型数量
     */
    @Override
    public Long getModelCountByCategory(String category) {
        return repositoryService.createModelQuery()
                .modelCategory(category)
                .modelTenantId(FlowableUtils.getTenantId())
                .count();
    }

    /**
     * 创建流程模型
     *
     * 【核心流程】
     * 1. 校验流程标识（key）的合法性和唯一性
     * 2. 创建 Flowable Model 对象
     * 3. 保存模型基本信息和流程图（BPMN XML 或简易模型 JSON）
     *
     * @param createReqVO 创建请求参数
     * @return 模型ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createModel(@Valid BpmModelSaveReqVO createReqVO) {
        // 1.1 校验流程标识格式是否合法（必须符合 XML NCName 规范：字母开头，不含特殊字符）
        if (!ValidationUtils.isXmlNCName(createReqVO.getKey())) {
            throw exception(MODEL_KEY_VALID);
        }

        // 1.2 校验流程标识唯一性（同一租户下不能重复）
        Model keyModel = getModelByKey(createReqVO.getKey());
        if (keyModel != null) {
            throw exception(MODEL_KEY_EXISTS, createReqVO.getKey());
        }

        // 2. 创建 Flowable Model 对象
        createReqVO.setSort(System.currentTimeMillis()); // 使用时间戳作为排序字段
        Model model = repositoryService.newModel();

        // 将前端传入的参数复制到 Model 对象（包括 key、name、category、description 等）
        BpmModelConvert.INSTANCE.copyToModel(model, createReqVO);

        // 设置租户ID（多租户隔离）
        model.setTenantId(FlowableUtils.getTenantId());

        // 3. 保存模型（包括基本信息和流程图）
        saveModel(model, createReqVO);
        return model.getId();
    }

    /**
     * 更新流程模型
     *
     * 【核心流程】
     * 1. 校验模型存在且用户有管理权限
     * 2. 更新模型的基本信息（名称、描述、分类等）
     * 3. 更新流程图（BPMN XML 或简易模型 JSON）
     *
     * 【注意事项】
     * - 只能更新模型本身，不会影响已部署的流程定义
     * - 修改后需要重新部署才能生效
     *
     * @param userId 用户ID（用于权限校验）
     * @param updateReqVO 更新请求参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModel(Long userId, BpmModelSaveReqVO updateReqVO) {
        // 1. 校验流程模型存在且用户有管理权限
        Model model = validateModelManager(updateReqVO.getId(), userId);

        // 2. 将前端传入的更新参数复制到 Model 对象
        BpmModelConvert.INSTANCE.copyToModel(model, updateReqVO);

        // 3. 保存模型（包括基本信息和流程图）
        saveModel(model, updateReqVO);
    }

    /**
     * 保存模型的基本信息和流程图
     *
     * 【存储说明】
     * - 基本信息存储在 ACT_RE_MODEL 表
     * - BPMN XML 存储在 ACT_GE_BYTEARRAY 表（通过 EDITOR_SOURCE_VALUE_ID_ 字段关联）
     * - Simple JSON 存储在 ACT_GE_BYTEARRAY 表（通过 EDITOR_SOURCE_EXTRA_VALUE_ID_ 字段关联）
     *
     * @param model 模型对象
     * @param saveReqVO 保存信息（包含 BPMN XML 或简易模型 JSON）
     */
    private void saveModel(Model model, BpmModelSaveReqVO saveReqVO) {
        // 1. 保存模型的基础信息到 ACT_RE_MODEL 表
        repositoryService.saveModel(model);

        // 2. 保存流程图
        // 2.1 BPMN 设计器模式：保存 BPMN XML
        if (ObjUtil.equals(BpmModelTypeEnum.BPMN.getType(), saveReqVO.getType())
                && StrUtil.isNotEmpty(saveReqVO.getBpmnXml())) {
            // BPMN 类型：直接保存 XML
            updateModelBpmnXml(model.getId(), saveReqVO.getBpmnXml());
        }
        // 2.2 Simple 仿钉钉设计器模式：需要保存两份数据
        else if (ObjUtil.equals(BpmModelTypeEnum.SIMPLE.getType(), saveReqVO.getType())
                && saveReqVO.getSimpleModel() != null) {
            // 将 JSON 转换成 BpmnModel 对象
            BpmnModel bpmnModel = SimpleModelUtils.buildBpmnModel(model.getKey(), model.getName(),
                    saveReqVO.getSimpleModel());

            // 保存转换后的 BPMN XML（用于流程引擎执行）
            updateModelBpmnXml(model.getId(), BpmnModelUtils.getBpmnXml(bpmnModel));

            // 保存原始的 JSON 数据（用于前端设计器回显）
            updateModelSimpleJson(model.getId(), saveReqVO.getSimpleModel());
        }
    }

    /**
     * 批量更新流程模型的排序顺序
     *
     * 【使用场景】
     * - 前端拖拽排序
     * - 批量调整流程模型的显示顺序
     *
     * 【排序规则】
     * - 使用时间戳作为排序基准
     * - 排序值越大，显示越靠前
     * - 同时更新模型和已部署的流程定义的排序
     *
     * @param userId 用户ID（用于权限校验）
     * @param ids 模型ID列表（按期望的顺序排列）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModelSortBatch(Long userId, List<String> ids) {
        // 1.1 批量校验模型存在（且属于当前租户）
        List<Model> models = repositoryService.createModelQuery()
                .modelTenantId(FlowableUtils.getTenantId()).list();

        // 过滤出传入的模型ID对应的模型
        models.removeIf(model -> !ids.contains(model.getId()));

        // 校验所有模型ID都存在
        if (ids.size() != models.size()) {
            throw exception(MODEL_NOT_EXISTS);
        }

        // 将模型列表转为 Map，方便后续按ID查找
        Map<String, Model> modelMap = convertMap(models, Model::getId);

        // 1.2 校验当前用户对所有模型都有管理权限
        ids.forEach(id -> validateModelManager(id, userId));

        // 2. 保存排序（使用时间戳递减，确保唯一性）
        long sort = System.currentTimeMillis();
        for (int i = ids.size() - 1; i > 0; i--) {
            Model model = modelMap.get(ids.get(i));

            // 更新模型的 metaInfo 中的排序字段
            BpmModelMetaInfoVO metaInfo = BpmModelConvert.INSTANCE.parseMetaInfo(model).setSort(sort);
            model.setMetaInfo(JsonUtils.toJsonString(metaInfo));
            repositoryService.saveModel(model);

            // 更新关联的流程定义的排序
            processDefinitionService.updateProcessDefinitionSortByModelId(model.getId(), sort);

            sort--; // 递减排序值
        }
    }

    /**
     * 校验流程模型是否存在
     *
     * @param id 模型ID
     * @return 流程模型对象
     * @throws 业务异常 如果模型不存在
     */
    private Model validateModelExists(String id) {
        Model model = repositoryService.getModel(id);
        if (model == null) {
            throw exception(MODEL_NOT_EXISTS);
        }
        return model;
    }

    /**
     * 校验用户是否为模型管理员（用于更新/删除/部署等操作）
     *
     * 【权限控制】
     * - 只有模型的管理员才能修改、部署、删除模型
     * - 管理员列表存储在 Model 的 metaInfo 字段中的 managerUserIds 属性
     *
     * @param id 流程模型编号
     * @param userId 用户编号
     * @return 流程模型对象
     * @throws 业务异常 如果模型不存在或用户无管理权限
     */
    private Model validateModelManager(String id, Long userId) {
        // 校验模型存在
        Model model = validateModelExists(id);

        // 解析模型的元数据信息
        BpmModelMetaInfoVO metaInfo = BpmModelConvert.INSTANCE.parseMetaInfo(model);

        // 校验当前用户是否在管理员列表中
        if (metaInfo == null || !CollUtil.contains(metaInfo.getManagerUserIds(), userId)) {
            throw exception(MODEL_UPDATE_FAIL_NOT_MANAGER, model.getName());
        }

        return model;
    }

    /**
     * 部署流程模型（将模型转为可执行的流程定义）
     *
     * 【核心流程】
     * 1. 校验阶段：权限、流程图、表单配置、任务分配规则
     * 2. 部署阶段：创建 ProcessDefinition，挂起旧版本
     * 3. 关联阶段：更新 Model 的 deploymentId
     *
     * 【关键概念】
     * - Model：设计态，可编辑的流程模型
     * - ProcessDefinition：运行态，可执行的流程定义（由 Model 部署生成）
     * - Deployment：一次部署操作，可包含多个 ProcessDefinition
     *
     * 【版本管理】
     * - 每次部署都会生成新版本的 ProcessDefinition
     * - 旧版本会被挂起（SUSPENDED），不能再发起新的流程实例
     * - 已运行的流程实例不受影响
     *
     * @param userId 用户ID（用于权限校验）
     * @param id 模型ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deployModel(Long userId, String id) {
        // ==================== 第一阶段：校验 ====================

        // 1.1 校验流程模型存在且当前用户有管理权限
        Model model = validateModelManager(id, userId);
        BpmModelMetaInfoVO metaInfo = BpmModelConvert.INSTANCE.parseMetaInfo(model);

        // 1.2 校验流程图格式正确（BPMN XML 能正常解析）
        byte[] bpmnBytes = getModelBpmnXML(model.getId());
        validateBpmnXml(bpmnBytes, metaInfo.getType());

        // 1.3 校验表单已配置（流程必须关联表单或自定义表单路径）
        BpmFormDO form = validateFormConfig(metaInfo);

        // 1.4 校验任务分配规则已配置（每个 UserTask 节点都必须配置审批人规则）
        taskCandidateInvoker.validateBpmnConfig(bpmnBytes);

        // 1.5 获取仿钉钉流程设计器的原始 JSON 数据（如果是 Simple 模式）
        String simpleJson = getModelSimpleJson(model.getId());

        // ==================== 第二阶段：部署 ====================

        // 2.1 创建流程定义（生成 ProcessDefinition，存储到 ACT_RE_PROCDEF 表）
        String definitionId = processDefinitionService.createProcessDefinition(model, metaInfo, bpmnBytes, simpleJson,
                form);

        // 2.2 挂起旧的流程定义（只有最新部署的版本才能发起新流程）
        updateProcessDefinitionSuspended(model.getDeploymentId());

        // ==================== 第三阶段：关联 ====================

        // 2.3 更新 Model 的 deploymentId，建立模型与部署的关联关系
        ProcessDefinition definition = processDefinitionService.getProcessDefinition(definitionId);
        model.setDeploymentId(definition.getDeploymentId());
        repositoryService.saveModel(model);
    }

    /**
     * 校验 BPMN XML 的合法性
     *
     * 【校验规则】
     * 1. 必须包含开始事件（StartEvent）
     * 2. 所有用户任务（UserTask）必须配置名称
     * 3. 第一个用户任务不能使用"审批人自选"策略（避免无法自动分配）
     *
     * @param bpmnBytes BPMN XML 字节数组
     * @param type 模型类型（BPMN 或 Simple）
     */
    private void validateBpmnXml(byte[] bpmnBytes, Integer type) {
        // 解析 BPMN XML 为 BpmnModel 对象
        BpmnModel bpmnModel = BpmnModelUtils.getBpmnModel(bpmnBytes);
        if (bpmnModel == null) {
            throw exception(MODEL_NOT_EXISTS); // 实际应为 XML 解析失败
        }

        // 1. 校验必须有开始事件（StartEvent）
        StartEvent startEvent = BpmnModelUtils.getStartEvent(bpmnModel);
        if (startEvent == null) {
            throw exception(MODEL_DEPLOY_FAIL_BPMN_START_EVENT_NOT_EXISTS);
        }

        // 2. 校验所有 UserTask 都配置了名称（用于流程图显示和任务列表）
        List<UserTask> userTasks = BpmnModelUtils.getBpmnModelElements(bpmnModel, UserTask.class);
        userTasks.forEach(userTask -> {
            if (StrUtil.isEmpty(userTask.getName())) {
                throw exception(MODEL_DEPLOY_FAIL_BPMN_USER_TASK_NAME_NOT_EXISTS, userTask.getId());
            }
        });

        // 3. 校验第一个用户任务的分配策略
        // - BPMN 设计器：校验第一个 UserTask（索引 0）
        // - Simple 设计器：第一个节点固定为发起人，所以校验第二个 UserTask（索引 1）
        UserTask firstUserTask = CollUtil.get(userTasks, BpmModelTypeEnum.BPMN.getType().equals(type) ? 0 : 1);
        if (firstUserTask == null) {
            return; // 没有用户任务，不需要校验
        }

        // 解析任务的候选人策略
        Integer candidateStrategy = parseCandidateStrategy(firstUserTask);

        // 第一个任务不能使用"审批人自选"策略（因为此时还没有人可以选择审批人）
        if (Objects.equals(candidateStrategy, BpmTaskCandidateStrategyEnum.APPROVE_USER_SELECT.getStrategy())) {
            throw exception(MODEL_DEPLOY_FAIL_FIRST_USER_TASK_CANDIDATE_STRATEGY_ERROR, firstUserTask.getName());
        }
    }

    /**
     * 删除流程模型
     *
     * 【删除范围】
     * 1. 删除流程模型（Model）
     * 2. 挂起关联的流程定义（ProcessDefinition）
     *
     * 【注意事项】
     * - 不会删除已部署的流程定义，只是将其挂起
     * - 不会删除流程实例和历史数据
     * - 如需彻底清理，请先调用 cleanModel() 方法
     *
     * @param userId 用户ID（用于权限校验）
     * @param id 模型ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(Long userId, String id) {
        // 1. 校验流程模型存在且用户有管理权限
        Model model = validateModelManager(id, userId);

        // 2. 删除流程模型
        repositoryService.deleteModel(id);

        // 3. 挂起关联的流程定义（防止继续发起新流程）
        updateProcessDefinitionSuspended(model.getDeploymentId());
    }

    /**
     * 清理流程模型的所有运行数据
     *
     * 【清理范围】
     * 1. 正在运行的流程实例（ProcessInstance）
     * 2. 历史流程实例（HistoricProcessInstance）
     * 3. 流程任务（Task）
     * 4. 流程抄送记录
     *
     * 【使用场景】
     * - 测试环境清理测试数据
     * - 流程重构后清理旧数据
     * - 流程下线前的数据清理
     *
     * 【注意事项】
     * - 这是一个危险操作，会删除所有相关的流程数据
     * - 删除操作不可恢复
     * - 不会删除流程模型（Model）和流程定义（ProcessDefinition）本身
     *
     * @param userId 用户ID（用于权限校验）
     * @param id 模型ID
     */
    @Override
    public void cleanModel(Long userId, String id) {
        // 1. 校验流程模型存在且当前用户有管理权限
        Model model = validateModelManager(id, userId);

        // 2. 清理所有流程数据

        // 2.1 先取消所有正在运行的流程实例
        List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(model.getKey()).list();
        processInstances.forEach(processInstance -> {
            // 删除运行中的流程实例（会自动终止所有未完成的任务）
            runtimeService.deleteProcessInstance(processInstance.getId(),
                    BpmReasonEnum.CANCEL_BY_SYSTEM.getReason());

            // 删除历史记录
            historyService.deleteHistoricProcessInstance(processInstance.getId());

            // 删除抄送记录
            processInstanceCopyService.deleteProcessInstanceCopy(processInstance.getId());
        });

        // 2.2 再从历史中删除所有相关的流程数据（处理已完成的流程实例）
        List<HistoricProcessInstance> historicProcessInstances = historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey(model.getKey()).list();
        historicProcessInstances.forEach(historicProcessInstance -> {
            // 删除历史流程实例
            historyService.deleteHistoricProcessInstance(historicProcessInstance.getId());

            // 删除抄送记录
            processInstanceCopyService.deleteProcessInstanceCopy(historicProcessInstance.getId());
        });

        // 2.3 清理所有相关的任务（处理孤立的任务）
        List<Task> tasks = taskService.createTaskQuery()
                .processDefinitionKey(model.getKey()).list();
        tasks.forEach(task -> taskService.deleteTask(task.getId(), BpmReasonEnum.CANCEL_BY_PROCESS_CLEAN.getReason()));
    }

    /**
     * 更新流程模型的状态（启用/挂起）
     *
     * 【状态说明】
     * - ACTIVE (1)：激活状态，可以发起新的流程实例
     * - SUSPENDED (2)：挂起状态，不能发起新的流程实例
     *
     * 【影响范围】
     * - 只影响流程定义（ProcessDefinition）
     * - 不影响已运行的流程实例
     *
     * @param userId 用户ID（用于权限校验）
     * @param id 模型ID
     * @param state 目标状态（1=激活，2=挂起）
     */
    @Override
    public void updateModelState(Long userId, String id, Integer state) {
        // 1.1 校验流程模型存在且用户有管理权限
        Model model = validateModelManager(id, userId);

        // 1.2 校验流程定义存在（模型必须已部署）
        ProcessDefinition definition = processDefinitionService
                .getProcessDefinitionByDeploymentId(model.getDeploymentId());
        if (definition == null) {
            throw exception(PROCESS_DEFINITION_NOT_EXISTS);
        }

        // 2. 更新流程定义的状态
        processDefinitionService.updateProcessDefinitionState(definition.getId(), state);
    }

    /**
     * 根据流程定义ID获取 BPMN 模型对象
     *
     * @param processDefinitionId 流程定义ID
     * @return BpmnModel 对象（包含流程图的完整结构）
     */
    @Override
    public BpmnModel getBpmnModelByDefinitionId(String processDefinitionId) {
        return repositoryService.getBpmnModel(processDefinitionId);
    }

    /**
     * 获取仿钉钉设计器的模型数据
     *
     * 【数据说明】
     * - 返回 Simple 模型的原始 JSON 数据
     * - 用于前端设计器的回显和编辑
     * - 数据存储在 ACT_GE_BYTEARRAY 表（通过 EDITOR_SOURCE_EXTRA_VALUE_ID_ 关联）
     *
     * @param modelId 模型ID
     * @return 简易模型节点数据
     */
    @Override
    public BpmSimpleModelNodeVO getSimpleModel(String modelId) {
        // 校验模型存在
        Model model = validateModelExists(modelId);

        // 获取 Simple 模型的 JSON 数据
        String json = getModelSimpleJson(model.getId());

        // 将 JSON 解析为对象返回
        return JsonUtils.parseObject(json, BpmSimpleModelNodeVO.class);
    }

    /**
     * 更新仿钉钉设计器的模型数据
     *
     * 【更新内容】
     * 1. 将 Simple JSON 转换为 BPMN XML（供流程引擎执行）
     * 2. 保存 BPMN XML
     * 3. 保存 Simple JSON（供前端设计器回显）
     *
     * @param userId 用户ID（用于权限校验）
     * @param reqVO 更新请求（包含 Simple 模型数据）
     */
    @Override
    public void updateSimpleModel(Long userId, BpmSimpleModelUpdateReqVO reqVO) {
        // 1. 校验流程模型存在且用户有管理权限
        Model model = validateModelManager(reqVO.getId(), userId);

        // 2.1 将 Simple JSON 转换成 BpmnModel 对象
        BpmnModel bpmnModel = SimpleModelUtils.buildBpmnModel(model.getKey(), model.getName(), reqVO.getSimpleModel());

        // 2.2 保存转换后的 BPMN XML（供流程引擎使用）
        updateModelBpmnXml(model.getId(), BpmnModelUtils.getBpmnXml(bpmnModel));

        // 2.3 保存原始的 JSON 数据（供前端设计器回显）
        updateModelSimpleJson(model.getId(), reqVO.getSimpleModel());
    }

    // =================== 表单校验 ===================

    /**
     * 校验流程表单配置
     *
     * 【表单类型】
     * 1. 普通表单（NORMAL）：使用系统的动态表单配置
     *    - 必须配置 formId，关联到 BpmFormDO
     * 2. 自定义表单（CUSTOM）：使用自定义的前端页面
     *    - 必须配置 formCustomCreatePath（发起流程的页面路径）
     *    - 必须配置 formCustomViewPath（查看流程的页面路径）
     *
     * @param metaInfo 流程模型元数据（包含表单配置信息）
     * @return 表单配置对象（如果是普通表单）；null（如果是自定义表单）
     */
    private BpmFormDO validateFormConfig(BpmModelMetaInfoVO metaInfo) {
        // 基础校验：必须配置表单类型
        if (metaInfo == null || metaInfo.getFormType() == null) {
            throw exception(MODEL_DEPLOY_FAIL_FORM_NOT_CONFIG);
        }

        // 校验普通表单
        if (Objects.equals(metaInfo.getFormType(), BpmModelFormTypeEnum.NORMAL.getType())) {
            // 必须配置表单ID
            if (metaInfo.getFormId() == null) {
                throw exception(MODEL_DEPLOY_FAIL_FORM_NOT_CONFIG);
            }

            // 校验表单是否存在
            BpmFormDO form = bpmFormService.getForm(metaInfo.getFormId());
            if (form == null) {
                throw exception(FORM_NOT_EXISTS);
            }
            return form;
        }
        // 校验自定义表单
        else {
            // 必须配置自定义表单的页面路径
            if (StrUtil.isEmpty(metaInfo.getFormCustomCreatePath())
                    || StrUtil.isEmpty(metaInfo.getFormCustomViewPath())) {
                throw exception(MODEL_DEPLOY_FAIL_FORM_NOT_CONFIG);
            }
            return null;
        }
    }

    /**
     * 更新模型的 BPMN XML
     *
     * 【存储位置】
     * - 数据存储在 ACT_GE_BYTEARRAY 表
     * - Model 通过 EDITOR_SOURCE_VALUE_ID_ 字段关联
     *
     * @param id 模型ID
     * @param bpmnXml BPMN XML 字符串
     */
    @Override
    public void updateModelBpmnXml(String id, String bpmnXml) {
        if (StrUtil.isEmpty(bpmnXml)) {
            return;
        }
        // 将 BPMN XML 字符串转为字节数组，存储到 Flowable 的字节数组表
        repositoryService.addModelEditorSource(id, StrUtil.utf8Bytes(bpmnXml));
    }

    /**
     * 获取仿钉钉设计器的 JSON 数据
     *
     * 【存储位置】
     * - 数据存储在 ACT_GE_BYTEARRAY 表
     * - Model 通过 EDITOR_SOURCE_EXTRA_VALUE_ID_ 字段关联
     *
     * @param id 模型ID
     * @return JSON 字符串（Simple 模型的原始数据）
     */
    @SuppressWarnings("JavaExistingMethodCanBeUsed")
    private String getModelSimpleJson(String id) {
        byte[] bytes = repositoryService.getModelEditorSourceExtra(id);
        if (ArrayUtil.isEmpty(bytes)) {
            return null;
        }
        return StrUtil.utf8Str(bytes);
    }

    /**
     * 更新仿钉钉设计器的 JSON 数据
     *
     * @param id 模型ID
     * @param node 简易模型节点数据
     */
    private void updateModelSimpleJson(String id, BpmSimpleModelNodeVO node) {
        if (node == null) {
            return;
        }
        // 将 JSON 对象转为字节数组，存储到 Flowable 的字节数组表
        byte[] bytes = JsonUtils.toJsonByte(node);
        repositoryService.addModelEditorSourceExtra(id, bytes);
    }

    /**
     * 挂起指定部署ID对应的流程定义
     *
     * 【版本管理策略】
     * - 每次部署都会生成新版本的 ProcessDefinition
     * - 旧版本自动挂起，不能再发起新的流程实例
     * - 已运行的流程实例不受影响，可以继续执行
     *
     * 【注意事项】
     * - 一个 Deployment 通常只关联一个 ProcessDefinition
     * - 挂起状态：SuspensionState.SUSPENDED (state=2)
     * - 激活状态：SuspensionState.ACTIVE (state=1)
     *
     * @param deploymentId 流程部署ID
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

        // 将旧版本的流程定义设置为挂起状态
        processDefinitionService.updateProcessDefinitionState(oldDefinition.getId(),
                SuspensionState.SUSPENDED.getStateCode());
    }

    /**
     * 根据流程标识（key）查询流程模型
     *
     * @param key 流程标识（processDefinitionKey）
     * @return 流程模型对象；null（如果不存在）
     */
    private Model getModelByKey(String key) {
        return repositoryService.createModelQuery()
                .modelTenantId(FlowableUtils.getTenantId())
                .modelKey(key).singleResult();
    }

    /**
     * 查询流程模型
     *
     * @param id 模型ID
     * @return 流程模型对象
     */
    @Override
    public Model getModel(String id) {
        return repositoryService.getModel(id);
    }

    /**
     * 获取模型的 BPMN XML
     *
     * @param id 模型ID
     * @return BPMN XML 字节数组
     */
    @Override
    public byte[] getModelBpmnXML(String id) {
        return repositoryService.getModelEditorSource(id);
    }

}