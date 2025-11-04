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
 * @author yunlongn
 * @author 芋道源码
 * @author jason
 */
@Service
@Validated
@Slf4j
public class BpmModelServiceImpl implements BpmModelService {

    // Flowable 原生服务
    @Resource
    private RepositoryService repositoryService;

    // 内部业务服务
    @Resource
    private BpmProcessDefinitionService processDefinitionService;
    @Resource
    private BpmFormService bpmFormService;
    @Resource
    private BpmTaskCandidateInvoker taskCandidateInvoker;
    @Resource
    private HistoryService historyService;
    @Resource
    private RuntimeService runtimeService;
    @Resource
    private TaskService taskService;
    @Resource
    private BpmProcessInstanceCopyService processInstanceCopyService;

    // =================== 查询相关 ===================

    /**
     * 根据模型名称模糊查询流程模型列表（支持租户隔离）
     *
     * @param name 模型名称（可为空）
     * @return 模型列表
     */
    @Override
    public List<Model> getModelList(String name) {
        ModelQuery modelQuery = repositoryService.createModelQuery();
        if (StrUtil.isNotEmpty(name)) {
            modelQuery.modelNameLike("%" + name + "%");
        }
        modelQuery.modelTenantId(FlowableUtils.getTenantId());
        return modelQuery.list();
    }

    /**
     * 统计指定分类下的模型数量（租户隔离）
     *
     * @param category 模型分类（如：请假、报销等）
     * @return 数量
     */
    @Override
    public Long getModelCountByCategory(String category) {
        return repositoryService.createModelQuery()
                .modelCategory(category)
                .modelTenantId(FlowableUtils.getTenantId())
                .count();
    }

    // =================== 创建/更新模型 ===================

    /**
     * 创建新的流程模型（支持 BPMN 或 SIMPLE 类型）
     *
     * @param createReqVO 创建请求
     * @return 模型 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createModel(@Valid BpmModelSaveReqVO createReqVO) {
        // 1. 校验 key 符合 XML NCName 规范（字母/数字/下划线，不能以数字开头等）
        if (!ValidationUtils.isXmlNCName(createReqVO.getKey())) {
            throw exception(MODEL_KEY_VALID);
        }
        // 2. 校验 key 唯一性（同租户下）
        Model keyModel = getModelByKey(createReqVO.getKey());
        if (keyModel != null) {
            throw exception(MODEL_KEY_EXISTS, createReqVO.getKey());
        }

        // 3. 构建 Model 对象
        createReqVO.setSort(System.currentTimeMillis()); // 用时间戳作为默认排序
        Model model = repositoryService.newModel();
        BpmModelConvert.INSTANCE.copyToModel(model, createReqVO);
        model.setTenantId(FlowableUtils.getTenantId());

        // 4. 保存模型及对应的 BPMN/XML 或 Simple JSON
        saveModel(model, createReqVO);
        return model.getId();
    }

    /**
     * 更新流程模型（仅管理员可操作）
     *
     * @param userId      当前操作用户 ID
     * @param updateReqVO 更新请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModel(Long userId, BpmModelSaveReqVO updateReqVO) {
        // 1. 校验模型存在，且当前用户是管理员
        Model model = validateModelManager(updateReqVO.getId(), userId);

        // 2. 更新模型基本信息
        BpmModelConvert.INSTANCE.copyToModel(model, updateReqVO);

        // 3. 保存模型及流程图
        saveModel(model, updateReqVO);
    }

    /**
     * 通用方法：保存模型基础信息 + 流程图（根据模型类型区分处理）
     *
     * @param model       Flowable Model 对象
     * @param saveReqVO   保存请求体
     */
    private void saveModel(Model model, BpmModelSaveReqVO saveReqVO) {
        // 1. 保存模型元信息（名称、key、分类、管理员等）
        repositoryService.saveModel(model);

        // 2. 根据模型类型保存流程图
        if (ObjUtil.equals(BpmModelTypeEnum.BPMN.getType(), saveReqVO.getType())
                && StrUtil.isNotEmpty(saveReqVO.getBpmnXml())) {
            // BPMN 类型：直接保存 XML
            updateModelBpmnXml(model.getId(), saveReqVO.getBpmnXml());
        } else if (ObjUtil.equals(BpmModelTypeEnum.SIMPLE.getType(), saveReqVO.getType())
                && saveReqVO.getSimpleModel() != null) {
            // SIMPLE 类型：将 JSON 转为 BPMN 模型，再保存 XML 和原始 JSON
            BpmnModel bpmnModel = SimpleModelUtils.buildBpmnModel(model.getKey(), model.getName(),
                    saveReqVO.getSimpleModel());
            updateModelBpmnXml(model.getId(), BpmnModelUtils.getBpmnXml(bpmnModel));
            updateModelSimpleJson(model.getId(), saveReqVO.getSimpleModel());
        }
    }

    // =================== 排序批量更新 ===================

    /**
     * 批量更新模型排序（拖拽排序场景）
     *
     * @param userId 当前用户
     * @param ids    模型 ID 列表（按前端顺序）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModelSortBatch(Long userId, List<String> ids) {
        // 1.1 批量校验模型存在（且属于当前租户）
        List<Model> models = repositoryService.createModelQuery()
                .modelTenantId(FlowableUtils.getTenantId()).list();
        models.removeIf(model -> !ids.contains(model.getId()));
        if (ids.size() != models.size()) {
            throw exception(MODEL_NOT_EXISTS);
        }
        Map<String, Model> modelMap = convertMap(models, Model::getId);

        // 1.2 校验每个模型当前用户是否有管理权限
        ids.forEach(id -> validateModelManager(id, userId));

        // 2. 按倒序分配时间戳作为排序值（越靠前，时间戳越大）
        long sort = System.currentTimeMillis();
        for (int i = ids.size() - 1; i >= 0; i--) { // 注意：从后往前，保证顺序
            Model model = modelMap.get(ids.get(i));
            BpmModelMetaInfoVO metaInfo = BpmModelConvert.INSTANCE.parseMetaInfo(model).setSort(sort);
            model.setMetaInfo(JsonUtils.toJsonString(metaInfo));
            repositoryService.saveModel(model);

            // 同步更新对应流程定义的排序（用于流程启动页展示）
            processDefinitionService.updateProcessDefinitionSortByModelId(model.getId(), sort);
            sort--; // 递减，确保顺序
        }
    }

    // =================== 模型校验工具方法 ===================

    /**
     * 校验模型存在（通用）
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
     * @param id     模型 ID
     * @param userId 用户 ID
     * @return 模型对象
     */
    private Model validateModelManager(String id, Long userId) {
        Model model = validateModelExists(id);
        BpmModelMetaInfoVO metaInfo = BpmModelConvert.INSTANCE.parseMetaInfo(model);
        if (metaInfo == null || !CollUtil.contains(metaInfo.getManagerUserIds(), userId)) {
            throw exception(MODEL_UPDATE_FAIL_NOT_MANAGER, model.getName());
        }
        return model;
    }

    // =================== 部署模型 ===================

    /**
     * 部署流程模型为可运行的流程定义（核心操作）
     *
     * @param userId 当前用户
     * @param id     模型 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deployModel(Long userId, String id) {
        // 1.1 校验模型存在且用户是管理员
        Model model = validateModelManager(id, userId);
        BpmModelMetaInfoVO metaInfo = BpmModelConvert.INSTANCE.parseMetaInfo(model);

        // 1.2 获取 BPMN XML 字节流并校验
        byte[] bpmnBytes = getModelBpmnXML(model.getId());
        validateBpmnXml(bpmnBytes, metaInfo.getType());

        // 1.3 校验表单已配置
        BpmFormDO form = validateFormConfig(metaInfo);

        // 1.4 校验任务分配规则（如：审批人、角色等）是否合法
        taskCandidateInvoker.validateBpmnConfig(bpmnBytes);

        // 1.5 获取简易模型的 JSON（用于后续记录）
        String simpleJson = getModelSimpleJson(model.getId());

        // 2.1 创建流程定义（保存到自定义表 bpm_process_definition）
        String definitionId = processDefinitionService.createProcessDefinition(
                model, metaInfo, bpmnBytes, simpleJson, form);

        // 2.2 挂起旧的流程定义（确保只有最新版本可启动）
        updateProcessDefinitionSuspended(model.getDeploymentId());

        // 2.3 将新部署的 deploymentId 关联到模型（用于后续查询）
        ProcessDefinition definition = processDefinitionService.getProcessDefinition(definitionId);
        model.setDeploymentId(definition.getDeploymentId());
        repositoryService.saveModel(model);
    }

    /**
     * 校验 BPMN XML 的合法性（部署前关键检查）
     *
     * @param bpmnBytes BPMN 字节流
     * @param type      模型类型（BPMN 或 SIMPLE）
     */
    private void validateBpmnXml(byte[] bpmnBytes, Integer type) {
        BpmnModel bpmnModel = BpmnModelUtils.getBpmnModel(bpmnBytes);
        if (bpmnModel == null) {
            throw exception(MODEL_NOT_EXISTS); // 实际应为 XML 解析失败
        }

        // 1. 必须有开始事件
        StartEvent startEvent = BpmnModelUtils.getStartEvent(bpmnModel);
        if (startEvent == null) {
            throw exception(MODEL_DEPLOY_FAIL_BPMN_START_EVENT_NOT_EXISTS);
        }

        // 2. 所有用户任务必须有名称（用于审批节点展示）
        List<UserTask> userTasks = BpmnModelUtils.getBpmnModelElements(bpmnModel, UserTask.class);
        userTasks.forEach(userTask -> {
            if (StrUtil.isEmpty(userTask.getName())) {
                throw exception(MODEL_DEPLOY_FAIL_BPMN_USER_TASK_NAME_NOT_EXISTS, userTask.getId());
            }
        });

        // 3. 第一个审批节点不能是“审批人自选”（防止流程无法启动）
        //    - BPMN：第一个 UserTask
        //    - SIMPLE：第二个 UserTask（第一个是发起人）
        int index = BpmModelTypeEnum.BPMN.getType().equals(type) ? 0 : 1;
        UserTask firUserTask = CollUtil.get(userTasks, index);
        if (firUserTask != null) {
            Integer candidateStrategy = parseCandidateStrategy(firUserTask);
            if (Objects.equals(candidateStrategy, BpmTaskCandidateStrategyEnum.APPROVE_USER_SELECT.getStrategy())) {
                throw exception(MODEL_DEPLOY_FAIL_FIRST_USER_TASK_CANDIDATE_STRATEGY_ERROR, firUserTask.getName());
            }
        }
    }

    // =================== 删除/清理 ===================

    /**
     * 删除模型（仅删除模型本身，不影响已部署的流程定义）
     *
     * @param userId 当前用户
     * @param id     模型 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(Long userId, String id) {
        Model model = validateModelManager(id, userId);
        repositoryService.deleteModel(id);
        // 同时挂起关联的流程定义（防止误用旧版本）
        updateProcessDefinitionSuspended(model.getDeploymentId());
    }

    /**
     * 彻底清理模型相关的所有运行数据（危险操作！）
     * 适用于：测试环境清理、流程废弃后数据清除
     *
     * @param userId 当前用户
     * @param id     模型 ID
     */
    @Override
    public void cleanModel(Long userId, String id) {
        Model model = validateModelManager(id, userId);

        // 2.1 终止所有运行中的流程实例
        List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(model.getKey()).list();
        processInstances.forEach(processInstance -> {
            runtimeService.deleteProcessInstance(processInstance.getId(),
                    BpmReasonEnum.CANCEL_BY_SYSTEM.getReason());
            historyService.deleteHistoricProcessInstance(processInstance.getId());
            processInstanceCopyService.deleteProcessInstanceCopy(processInstance.getId());
        });

        // 2.2 清理历史流程实例（未被正常删除的）
        List<HistoricProcessInstance> historicProcessInstances = historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey(model.getKey()).list();
        historicProcessInstances.forEach(historicProcessInstance -> {
            historyService.deleteHistoricProcessInstance(historicProcessInstance.getId());
            processInstanceCopyService.deleteProcessInstanceCopy(historicProcessInstance.getId());
        });

        // 2.3 清理残留任务（理论上不应存在，但保险起见）
        List<Task> tasks = taskService.createTaskQuery()
                .processDefinitionKey(model.getKey()).list();
        tasks.forEach(task -> taskService.deleteTask(task.getId(), BpmReasonEnum.CANCEL_BY_PROCESS_CLEAN.getReason()));
    }

    // =================== 状态管理 ===================

    /**
     * 更新流程定义的启用/停用状态（通过模型 ID 触发）
     *
     * @param userId 当前用户
     * @param id     模型 ID
     * @param state  状态：1-激活，2-挂起
     */
    @Override
    public void updateModelState(Long userId, String id, Integer state) {
        Model model = validateModelManager(id, userId);
        ProcessDefinition definition = processDefinitionService
                .getProcessDefinitionByDeploymentId(model.getDeploymentId());
        if (definition == null) {
            throw exception(PROCESS_DEFINITION_NOT_EXISTS);
        }
        processDefinitionService.updateProcessDefinitionState(definition.getId(), state);
    }

    // =================== 获取模型数据 ===================

    /**
     * 根据流程定义 ID 获取 BPMN 模型（用于流程图展示）
     */
    @Override
    public BpmnModel getBpmnModelByDefinitionId(String processDefinitionId) {
        return repositoryService.getBpmnModel(processDefinitionId);
    }

    /**
     * 获取简易模型的 JSON 结构（用于简易设计器回显）
     *
     * @param modelId 模型 ID
     * @return 简易模型节点 VO
     */
    @Override
    public BpmSimpleModelNodeVO getSimpleModel(String modelId) {
        Model model = validateModelExists(modelId);
        String json = getModelSimpleJson(model.getId());
        return JsonUtils.parseObject(json, BpmSimpleModelNodeVO.class);
    }

    /**
     * 更新简易模型（设计器保存时调用）
     *
     * @param userId 当前用户
     * @param reqVO  更新请求
     */
    @Override
    public void updateSimpleModel(Long userId, BpmSimpleModelUpdateReqVO reqVO) {
        Model model = validateModelManager(reqVO.getId(), userId);

        // 将 JSON 转为 BPMN 模型并保存 XML 和 JSON
        BpmnModel bpmnModel = SimpleModelUtils.buildBpmnModel(model.getKey(), model.getName(), reqVO.getSimpleModel());
        updateModelBpmnXml(model.getId(), BpmnModelUtils.getBpmnXml(bpmnModel));
        updateModelSimpleJson(model.getId(), reqVO.getSimpleModel());
    }

    // =================== 表单校验 ===================

    /**
     * 校验流程表单是否已正确配置（部署前必须）
     *
     * @param metaInfo 模型元信息
     * @return 表单 DO（普通表单）或 null（自定义表单）
     */
    private BpmFormDO validateFormConfig(BpmModelMetaInfoVO metaInfo) {
        if (metaInfo == null || metaInfo.getFormType() == null) {
            throw exception(MODEL_DEPLOY_FAIL_FORM_NOT_CONFIG);
        }

        if (Objects.equals(metaInfo.getFormType(), BpmModelFormTypeEnum.NORMAL.getType())) {
            // 普通表单：必须指定表单 ID 且表单存在
            if (metaInfo.getFormId() == null) {
                throw exception(MODEL_DEPLOY_FAIL_FORM_NOT_CONFIG);
            }
            BpmFormDO form = bpmFormService.getForm(metaInfo.getFormId());
            if (form == null) {
                throw exception(FORM_NOT_EXISTS);
            }
            return form;
        } else {
            // 自定义表单：必须提供创建和查看页面路径
            if (StrUtil.isEmpty(metaInfo.getFormCustomCreatePath())
                    || StrUtil.isEmpty(metaInfo.getFormCustomViewPath())) {
                throw exception(MODEL_DEPLOY_FAIL_FORM_NOT_CONFIG);
            }
            return null;
        }
    }

    // =================== 底层存储操作 ===================

    /**
     * 更新模型的 BPMN XML（存储到 Flowable 的 EDITOR_SOURCE_VALUE_ID_ 字段）
     */
    @Override
    public void updateModelBpmnXml(String id, String bpmnXml) {
        if (StrUtil.isEmpty(bpmnXml)) {
            return;
        }
        repositoryService.addModelEditorSource(id, StrUtil.utf8Bytes(bpmnXml));
    }

    /**
     * 从 Flowable 获取模型的简易 JSON（存储在 EDITOR_SOURCE_EXTRA_VALUE_ID_）
     */
    private String getModelSimpleJson(String id) {
        byte[] bytes = repositoryService.getModelEditorSourceExtra(id);
        if (ArrayUtil.isEmpty(bytes)) {
            return null;
        }
        return StrUtil.utf8Str(bytes);
    }

    /**
     * 更新模型的简易 JSON 数据
     */
    private void updateModelSimpleJson(String id, BpmSimpleModelNodeVO node) {
        if (node == null) {
            return;
        }
        byte[] bytes = JsonUtils.toJsonByte(node);
        repositoryService.addModelEditorSourceExtra(id, bytes);
    }

    /**
     * 挂起指定 deploymentId 对应的流程定义（用于部署新版本时停用旧版）
     *
     * @param deploymentId 部署 ID
     */
    private void updateProcessDefinitionSuspended(String deploymentId) {
        if (StrUtil.isEmpty(deploymentId)) {
            return;
        }
        ProcessDefinition oldDefinition = processDefinitionService.getProcessDefinitionByDeploymentId(deploymentId);
        if (oldDefinition == null) {
            return;
        }
        processDefinitionService.updateProcessDefinitionState(oldDefinition.getId(),
                SuspensionState.SUSPENDED.getStateCode());
    }

    // =================== 工具方法 ===================

    /**
     * 根据 key 查询模型（租户隔离）
     */
    private Model getModelByKey(String key) {
        return repositoryService.createModelQuery()
                .modelTenantId(FlowableUtils.getTenantId())
                .modelKey(key).singleResult();
    }

    /**
     * 根据 ID 获取模型（不校验权限）
     */
    @Override
    public Model getModel(String id) {
        return repositoryService.getModel(id);
    }

    /**
     * 获取模型的 BPMN XML 字节流
     */
    @Override
    public byte[] getModelBpmnXML(String id) {
        return repositoryService.getModelEditorSource(id);
    }

}