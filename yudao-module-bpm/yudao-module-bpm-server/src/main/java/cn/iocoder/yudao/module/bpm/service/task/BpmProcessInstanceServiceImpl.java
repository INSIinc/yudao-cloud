package cn.iocoder.yudao.module.bpm.service.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.*;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;
import cn.iocoder.yudao.framework.common.util.object.PageUtils;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelMetaInfoVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.*;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailRespVO.ActivityNodeTask;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRespVO;
import cn.iocoder.yudao.module.bpm.convert.task.BpmProcessInstanceConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.dal.redis.BpmProcessIdRedisDAO;
import cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmSimpleModelNodeTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmReasonEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.event.BpmProcessInstanceEventPublisher;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.SimpleModelUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.message.BpmMessageService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.constants.BpmnXMLConstants;
import org.flowable.bpmn.model.*;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceBuilder;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailRespVO.ActivityNode;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants.START_USER_NODE_ID;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_NEED_SIMULATE_PREFIX;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.parseNodeType;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.flowable.bpmn.constants.BpmnXMLConstants.*;

/**
 * 流程实例 Service 实现类
 * <p>
 * ProcessDefinition & ProcessInstance & Execution & Task 的关系：
 * 1. <a href="https://blog.csdn.net/bobozai86/article/details/105210414" />
 * <p>
 * HistoricProcessInstance & ProcessInstance 的关系：
 * 1. <a href=" https://my.oschina.net/843294669/blog/71902" />
 * <p>
 * 简单来说，前者 = 历史 + 运行中的流程实例，后者仅是运行中的流程实例
 *
 * @author 芋道源码
 */
@Service
@Validated
@Slf4j
public class BpmProcessInstanceServiceImpl implements BpmProcessInstanceService {

    // ========== Flowable 核心服务 ==========
    
    /**
     * Flowable 运行时服务
     * 用于管理运行中的流程实例、执行流程、设置流程变量等操作
     */
    @Resource
    private RuntimeService runtimeService;
    
    /**
     * Flowable 历史服务
     * 用于查询已完成或运行中的流程实例历史记录、任务历史等
     */
    @Resource
    private HistoryService historyService;

    // ========== 业务服务 ==========
    
    /**
     * 流程定义服务
     * 用于获取流程定义信息、BPMN 模型等
     */
    @Resource
    private BpmProcessDefinitionService processDefinitionService;
    
    /**
     * 流程任务服务
     * 用于处理流程任务的审批、驳回、加签等操作
     */
    @Resource
    @Lazy // 避免循环依赖：BpmTaskService 也依赖 BpmProcessInstanceService
    private BpmTaskService taskService;
    
    /**
     * 流程消息服务
     * 用于发送流程相关的消息通知（如审批通过、审批拒绝等）
     */
    @Resource
    private BpmMessageService messageService;

    // ========== 远程服务 ==========
    
    /**
     * 用户服务 API
     * 用于获取用户信息（昵称、部门等）
     */
    @Resource
    private AdminUserApi adminUserApi;
    
    /**
     * 部门服务 API
     * 用于获取部门信息
     */
    @Resource
    private DeptApi deptApi;

    // ========== 事件与工具 ==========
    
    /**
     * 流程实例事件发布器
     * 用于发布流程实例状态变更事件，支持业务系统监听流程状态
     */
    @Resource
    private BpmProcessInstanceEventPublisher processInstanceEventPublisher;

    /**
     * 任务候选人计算器
     * 用于根据候选人策略（如部门负责人、角色、用户等）计算任务的候选审批人
     */
    @Resource
    private BpmTaskCandidateInvoker taskCandidateInvoker;

    /**
     * 流程实例 ID Redis DAO
     * 用于生成自定义格式的流程实例编号（如带日期前缀的流水号）
     */
    @Resource
    private BpmProcessIdRedisDAO processIdRedisDAO;

// ========== Query 查询相关方法 ==========

    /**
     * 根据流程实例 ID 查询运行中的流程实例（包含流程变量）
     *
     * @param id 流程实例 ID
     * @return 运行中的流程实例对象，若不存在则返回 null
     */
    @Override
    public ProcessInstance getProcessInstance(String id) {
        return runtimeService.createProcessInstanceQuery()
                .includeProcessVariables()      // 包含流程变量
                .processInstanceId(id)         // 指定流程实例 ID
                .singleResult();               // 返回单个结果
    }

    /**
     * 根据多个流程实例 ID 查询运行中的流程实例列表（包含流程变量）
     *
     * @param ids 流程实例 ID 集合
     * @return 流程实例列表
     */
    @Override
    public List<ProcessInstance> getProcessInstances(Set<String> ids) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceIds(ids)       // 指定多个流程实例 ID
                .includeProcessVariables()     // 包含流程变量
                .list();                       // 返回列表
    }

    /**
     * 根据流程实例 ID 查询历史流程实例（包含流程变量）
     *
     * @param id 流程实例 ID
     * @return 历史流程实例对象，若不存在则返回 null
     */
    @Override
    public HistoricProcessInstance getHistoricProcessInstance(String id) {
        return historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(id)         // 指定流程实例 ID
                .includeProcessVariables()     // 包含流程变量
                .singleResult();               // 返回单个结果
    }

    /**
     * 根据多个流程实例 ID 查询历史流程实例列表（包含流程变量）
     *
     * @param ids 流程实例 ID 集合
     * @return 历史流程实例列表
     */
    @Override
    public List<HistoricProcessInstance> getHistoricProcessInstances(Set<String> ids) {
        return historyService.createHistoricProcessInstanceQuery()
                .processInstanceIds(ids)       // 指定多个流程实例 ID
                .includeProcessVariables()     // 包含流程变量
                .list();                       // 返回列表
    }

    /**
     * 从 BPMN 模型中解析指定活动节点（activity）的表单字段权限配置
     *
     * @param bpmnModel   BPMN 模型对象
     * @param activityId  活动节点 ID（可能为空）
     * @param taskId      任务 ID（用于在 activityId 为空时回退获取 activityId）
     * @return 表单字段权限映射（字段名 -> 权限字符串），若无法解析则返回 null
     */
    private Map<String, String> getFormFieldsPermission(BpmnModel bpmnModel,
                                                        String activityId, String taskId) {
        // 1. 如果 activityId 为空但 taskId 有值，尝试从历史任务中获取对应的 activityId（即 taskDefinitionKey）
        if (StrUtil.isEmpty(activityId) && StrUtil.isNotEmpty(taskId)) {
            activityId = Optional.ofNullable(taskService.getHistoricTask(taskId))
                    .map(HistoricTaskInstance::getTaskDefinitionKey).orElse(null);
        }
        // 若仍无法获取 activityId，则无法解析权限，返回 null
        if (StrUtil.isEmpty(activityId)) {
            return null;
        }

        // 2. 使用工具类从 BPMN 模型中解析该活动节点的表单字段权限配置
        return BpmnModelUtils.parseFormFieldsPermission(bpmnModel, activityId);
    }

    /**
     * 获取审批详情（包含已办、待办、预测节点等信息）
     *
     * @param loginUserId 当前登录用户 ID
     * @param reqVO       请求参数对象
     * @return 审批详情响应 VO
     */
    @Override
    public BpmApprovalDetailRespVO getApprovalDetail(Long loginUserId, BpmApprovalDetailReqVO reqVO) {
        // ========== 1. 初始化基础数据 ==========

        // 1.1 初始化公共变量
        Long startUserId = loginUserId; // 默认流程发起人为当前登录用户
        HistoricProcessInstance historicProcessInstance = null; // 历史流程实例（可能为空）
        Integer processInstanceStatus = BpmProcessInstanceStatusEnum.NOT_START.getStatus(); // 默认状态：未启动
        Map<String, Object> processVariables = new HashMap<>(); // 流程变量容器

        // 1.2 如果请求中提供了流程实例 ID，则加载该流程实例的详细数据
        if (reqVO.getProcessInstanceId() != null) {
            historicProcessInstance = getHistoricProcessInstance(reqVO.getProcessInstanceId());
            if (historicProcessInstance == null) {
                throw exception(ErrorCodeConstants.PROCESS_INSTANCE_NOT_EXISTS); // 实例不存在，抛异常
            }
            // 覆盖发起人（从流程实例中读取）
            startUserId = Long.valueOf(historicProcessInstance.getStartUserId());
            // 获取流程状态（结束、运行中等）
            processInstanceStatus = FlowableUtils.getProcessInstanceStatus(historicProcessInstance);
            // 合并历史流程实例中的变量到 processVariables（DB 中的变量）
            if (CollUtil.isNotEmpty(historicProcessInstance.getProcessVariables())) {
                processVariables.putAll(historicProcessInstance.getProcessVariables());
            }
        }

        // 合并前端传入的流程变量（前端变量优先级更高，会覆盖 DB 中的同名变量）
        if (CollUtil.isNotEmpty(reqVO.getProcessVariables())) {
            processVariables.putAll(reqVO.getProcessVariables());
        }

        // 特殊处理：如果流程尚未发起（即 historicProcessInstance == null），
        // 则手动注入发起人变量，用于“发起流程”界面中表单逻辑判断
        if (historicProcessInstance == null) {
            processVariables.put(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_ID, loginUserId);
        }

        // 1.3 加载流程定义相关信息
        ProcessDefinition processDefinition = processDefinitionService.getProcessDefinition(
                historicProcessInstance != null ? historicProcessInstance.getProcessDefinitionId()
                        : reqVO.getProcessDefinitionId()); // 使用流程实例中的定义ID，或请求参数中的定义ID
        BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService
                .getProcessDefinitionInfo(processDefinition.getId()); // 获取扩展信息（如表单配置）
        BpmnModel bpmnModel = processDefinitionService.getProcessDefinitionBpmnModel(processDefinition.getId()); // 获取 BPMN 模型

        // ========== 2. 查询活动节点信息 ==========

        List<ActivityNode> endActivityNodes = null; // 已结束的活动节点（已完成审批）
        List<ActivityNode> runActivityNodes = null; // 正在运行的活动节点（当前待办）
        List<HistoricActivityInstance> activities = null; // 所有历史活动实例

        // 如果流程已发起，则查询相关活动和任务数据
        if (reqVO.getProcessInstanceId() != null) {
            activities = taskService.getActivityListByProcessInstanceId(reqVO.getProcessInstanceId());
            List<HistoricTaskInstance> tasks = taskService.getTaskListByProcessInstanceId(reqVO.getProcessInstanceId(), true);
            // 构建已结束的活动节点列表
            endActivityNodes = getEndActivityNodeList(startUserId, bpmnModel, processDefinitionInfo,
                    historicProcessInstance, processInstanceStatus, activities, tasks);
            // 构建正在运行的活动节点列表
            runActivityNodes = getRunApproveNodeList(startUserId, bpmnModel, processDefinition, processVariables,
                    activities, tasks);
        }

        // 2.2 如果流程已经结束，无需预测后续节点，直接返回结果
        if (BpmProcessInstanceStatusEnum.isProcessEndStatus(processInstanceStatus)) {
            return buildApprovalDetail(reqVO, bpmnModel, processDefinition, processDefinitionInfo,
                    historicProcessInstance,
                    processInstanceStatus, endActivityNodes, runActivityNodes, null, null);
        }

        // ========== 3. 处理待办任务与预测节点 ==========

        // 3.1 查询当前登录用户在该流程中的待办任务（可能是指定 taskId，或根据流程实例查找）
        BpmTaskRespVO todoTask = taskService.getTodoTask(loginUserId, reqVO.getTaskId(), reqVO.getProcessInstanceId());

        // 3.2 获取因“退回”操作而需要模拟预测的节点
        // Flowable 在退回时会设置形如 "needSimulate_taskKey" 的流程变量，标记需要重新走哪些节点
        Set<String> needSimulateTaskDefKeysByReturn = new HashSet<>();
        if (StrUtil.isNotEmpty(reqVO.getProcessInstanceId())) {
            // 从运行时变量中读取所有 needSimulate_ 开头的变量
            Map<String, Object> variables = runtimeService.getVariables(reqVO.getProcessInstanceId());
            Map<String, Object> simulateTaskVariables = MapUtil.filter(variables,
                    item -> item.getKey().startsWith(PROCESS_INSTANCE_VARIABLE_NEED_SIMULATE_PREFIX));
            // 提取 taskDefinitionKey（去掉前缀）
            simulateTaskVariables.forEach((key, value) ->
                    needSimulateTaskDefKeysByReturn.add(StrUtil.removePrefix(key, PROCESS_INSTANCE_VARIABLE_NEED_SIMULATE_PREFIX)));
        }

        // 移除已经在运行中的节点（避免重复预测）
        if (CollUtil.isNotEmpty(runActivityNodes)) {
            runActivityNodes.forEach(activityNode -> needSimulateTaskDefKeysByReturn.remove(activityNode.getId()));
        }

        // 3.3 预测尚未执行但可能执行的节点（例如未来审批人、条件分支后的节点等）
        List<ActivityNode> simulateActivityNodes = getSimulateApproveNodeList(startUserId, bpmnModel,
                processDefinitionInfo,
                processVariables, activities, needSimulateTaskDefKeysByReturn);

        // ========== 4. 构建并返回最终审批详情 ==========

        return buildApprovalDetail(reqVO, bpmnModel, processDefinition, processDefinitionInfo, historicProcessInstance,
                processInstanceStatus, endActivityNodes, runActivityNodes, simulateActivityNodes, todoTask);
    }

    @Override
    public List<ActivityNode> getNextApprovalNodes(Long loginUserId, BpmApprovalDetailReqVO reqVO) {
        // 1.1 校验任务存在，且是当前用户的
        Task task = taskService.validateTask(loginUserId, reqVO.getTaskId());
        // 1.2 校验流程实例存在
        ProcessInstance instance = getProcessInstance(task.getProcessInstanceId());
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }
        HistoricProcessInstance historicProcessInstance = getHistoricProcessInstance(task.getProcessInstanceId());
        if (historicProcessInstance == null) {
            throw exception(ErrorCodeConstants.PROCESS_INSTANCE_NOT_EXISTS);
        }
        // 1.3 校验BpmnModel
        BpmnModel bpmnModel = processDefinitionService.getProcessDefinitionBpmnModel(task.getProcessDefinitionId());
        if (bpmnModel == null) {
            return null;
        }

        // 2. 设置流程变量
        Map<String, Object> processVariables = new HashMap<>();
        // 2.1 获取历史中流程变量
        if (CollUtil.isNotEmpty(historicProcessInstance.getProcessVariables())) {
            processVariables.putAll(historicProcessInstance.getProcessVariables());
        }
        // 2.2 合并前端传递的流程变量，以前端为准
        if (CollUtil.isNotEmpty(reqVO.getProcessVariables())) {
            processVariables.putAll(reqVO.getProcessVariables());
        }

        // 3. 获取下一个将要执行的节点集合
        FlowElement flowElement = bpmnModel.getFlowElement(task.getTaskDefinitionKey());
        List<FlowNode> nextFlowNodes = BpmnModelUtils.getNextFlowNodes(flowElement, bpmnModel, processVariables);
        // 仅仅获取 UserTask 节点  TODO add from jason：如果网关节点和网关节点相连，获取下个 UserTask. 貌似有点不准。
        List<FlowNode> nextUserTaskList = CollectionUtils.filterList(nextFlowNodes, node -> node instanceof UserTask);
        List<ActivityNode> nextActivityNodes = convertList(nextUserTaskList, node -> new ActivityNode().setId(node.getId())
                .setName(node.getName()).setNodeType(BpmSimpleModelNodeTypeEnum.APPROVE_NODE.getType())
                .setStatus(BpmTaskStatusEnum.RUNNING.getStatus())
                .setCandidateStrategy(BpmnModelUtils.parseCandidateStrategy(node))
                .setCandidateUserIds(getTaskCandidateUserList(bpmnModel, node.getId(),
                        loginUserId, historicProcessInstance.getProcessDefinitionId(), processVariables)));
        if (CollUtil.isEmpty(nextActivityNodes)) {
            return nextActivityNodes;
        }

        // 4. 拼接基础信息
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                convertSetByFlatMap(nextActivityNodes, ActivityNode::getCandidateUserIds, Collection::stream));
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(convertSet(userMap.values(), AdminUserRespDTO::getDeptId));
        nextActivityNodes.forEach(node -> node.setCandidateUsers(convertList(node.getCandidateUserIds(), userId -> {
            AdminUserRespDTO user = userMap.get(userId);
            if (user != null) {
                return BpmProcessInstanceConvert.INSTANCE.buildUser(userId, userMap, deptMap);
            }
            return null;
        })));
        return nextActivityNodes;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PageResult<HistoricProcessInstance> getProcessInstancePage(Long userId,
                                                                      BpmProcessInstancePageReqVO pageReqVO) {
        // 1. 构建查询条件
        HistoricProcessInstanceQuery processInstanceQuery = historyService.createHistoricProcessInstanceQuery()
                .includeProcessVariables()
                .processInstanceTenantId(FlowableUtils.getTenantId())
                .orderByProcessInstanceStartTime().desc();
        if (userId != null) { // 【我的流程】菜单时，需要传递该字段
            processInstanceQuery.startedBy(String.valueOf(userId));
        } else if (pageReqVO.getStartUserId() != null) { // 【管理流程】菜单时，才会传递该字段
            processInstanceQuery.startedBy(String.valueOf(pageReqVO.getStartUserId()));
        }
        if (StrUtil.isNotEmpty(pageReqVO.getName())) {
            processInstanceQuery.processInstanceNameLike("%" + pageReqVO.getName() + "%");
        }
        if (StrUtil.isNotEmpty(pageReqVO.getProcessDefinitionKey())) {
            processInstanceQuery.processDefinitionKey(pageReqVO.getProcessDefinitionKey());
        }
        if (StrUtil.isNotEmpty(pageReqVO.getCategory())) {
            processInstanceQuery.processDefinitionCategory(pageReqVO.getCategory());
        }
        if (pageReqVO.getStatus() != null) {
            processInstanceQuery.variableValueEquals(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                    pageReqVO.getStatus());
        }
        if (ArrayUtil.isNotEmpty(pageReqVO.getCreateTime())) {
            processInstanceQuery.startedAfter(DateUtils.of(pageReqVO.getCreateTime()[0]));
            processInstanceQuery.startedBefore(DateUtils.of(pageReqVO.getCreateTime()[1]));
        }
        if (ArrayUtil.isNotEmpty(pageReqVO.getEndTime())) {
            processInstanceQuery.finishedAfter(DateUtils.of(pageReqVO.getEndTime()[0]));
            processInstanceQuery.finishedBefore(DateUtils.of(pageReqVO.getEndTime()[1]));
        }
        // 表单字段查询
        Map<String, Object> formFieldsParams = JsonUtils.parseObject(pageReqVO.getFormFieldsParams(), Map.class);
        if (CollUtil.isNotEmpty(formFieldsParams)) {
            formFieldsParams.forEach((key, value) -> {
                if (StrUtil.isEmpty(String.valueOf(value))) {
                    return;
                }
                // TODO @lesan：应支持多种类型的查询方式，目前只有字符串全等
                processInstanceQuery.variableValueEquals(key, value);
            });
        }

        // 2.1 查询数量
        long processInstanceCount = processInstanceQuery.count();
        if (processInstanceCount == 0) {
            return PageResult.empty(processInstanceCount);
        }
        // 2.2 查询列表
        List<HistoricProcessInstance> processInstanceList = processInstanceQuery.listPage(PageUtils.getStart(pageReqVO),
                pageReqVO.getPageSize());
        return new PageResult<>(processInstanceList, processInstanceCount);
    }

    /**
     * 拼接审批详情的最终数据
     * <p>
     * 主要是，拼接审批人的用户信息、部门信息
     */
    private BpmApprovalDetailRespVO buildApprovalDetail(BpmApprovalDetailReqVO reqVO,
                                                        BpmnModel bpmnModel,
                                                        ProcessDefinition processDefinition,
                                                        BpmProcessDefinitionInfoDO processDefinitionInfo,
                                                        HistoricProcessInstance processInstance,
                                                        Integer processInstanceStatus,
                                                        List<ActivityNode> endApprovalNodeInfos,
                                                        List<ActivityNode> runningApprovalNodeInfos,
                                                        List<ActivityNode> simulateApprovalNodeInfos,
                                                        BpmTaskRespVO todoTask) {
        // 1. 获取所有需要读取用户信息的 userIds
        List<ActivityNode> approveNodes = newArrayList(
                asList(endApprovalNodeInfos, runningApprovalNodeInfos, simulateApprovalNodeInfos));
        Set<Long> userIds = BpmProcessInstanceConvert.INSTANCE.parseUserIds(processInstance, approveNodes, todoTask);
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(userIds);
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(convertSet(userMap.values(), AdminUserRespDTO::getDeptId));

        // 2. 表单权限
        String taskId = reqVO.getTaskId() == null && todoTask != null ? todoTask.getId() : reqVO.getTaskId();
        Map<String, String> formFieldsPermission = getFormFieldsPermission(bpmnModel, reqVO.getActivityId(), taskId);

        // 3. 拼接数据
        return BpmProcessInstanceConvert.INSTANCE.buildApprovalDetail(bpmnModel, processDefinition,
                processDefinitionInfo, processInstance,
                processInstanceStatus, approveNodes, todoTask, formFieldsPermission, userMap, deptMap);
    }

    /**
     * 获得【已结束】的活动节点们
     */
    private List<ActivityNode> getEndActivityNodeList(Long startUserId, BpmnModel bpmnModel,
                                                      BpmProcessDefinitionInfoDO processDefinitionInfo,
                                                      HistoricProcessInstance historicProcessInstance, Integer processInstanceStatus,
                                                      List<HistoricActivityInstance> activities, List<HistoricTaskInstance> tasks) {
        // 遍历 tasks 列表，只处理已结束的 UserTask
        // 为什么不通过 activities 呢？因为，加签场景下，它只存在于 tasks，没有 activities，导致如果遍历 activities 的话，它无法成为一个节点
        List<HistoricTaskInstance> endTasks = filterList(tasks, task -> task.getEndTime() != null);
        List<ActivityNode> approvalNodes = convertList(endTasks, task -> {
            FlowElement flowNode = BpmnModelUtils.getFlowElementById(bpmnModel, task.getTaskDefinitionKey());
            ActivityNode activityNode = new ActivityNode().setId(task.getTaskDefinitionKey()).setName(task.getName())
                    .setNodeType(START_USER_NODE_ID.equals(task.getTaskDefinitionKey())
                            ? BpmSimpleModelNodeTypeEnum.START_USER_NODE.getType()
                            : ObjUtil.defaultIfNull(parseNodeType(flowNode), // 目的：解决“办理节点”的识别
                            BpmSimpleModelNodeTypeEnum.APPROVE_NODE.getType()))
                    .setStatus(getEndActivityNodeStatus(task))
                    .setCandidateStrategy(BpmnModelUtils.parseCandidateStrategy(flowNode))
                    .setStartTime(DateUtils.of(task.getCreateTime())).setEndTime(DateUtils.of(task.getEndTime()))
                    .setTasks(singletonList(BpmProcessInstanceConvert.INSTANCE.buildApprovalTaskInfo(task)));
            // 如果是取消状态，则跳过
            if (BpmTaskStatusEnum.isCancelStatus(activityNode.getStatus())) {
                return null;
            }
            return activityNode;
        });

        // 遍历 activities，只处理已结束的 StartEvent、EndEvent
        List<HistoricActivityInstance> endActivities = filterList(activities, activity -> activity.getEndTime() != null
                && (StrUtil.equalsAny(activity.getActivityType(), ELEMENT_EVENT_START, ELEMENT_CALL_ACTIVITY, ELEMENT_EVENT_END)));
        endActivities.forEach(activity -> {
            // StartEvent：只处理 BPMN 的场景。因为，SIMPLE 情况下，已经有 START_USER_NODE 节点
            if (ELEMENT_EVENT_START.equals(activity.getActivityType())
                    && BpmModelTypeEnum.BPMN.getType().equals(processDefinitionInfo.getModelType())
                    && !CollUtil.contains(activities, // 特殊：如果已经存在用户手动创建的 START_USER_NODE_ID 节点，则忽略 StartEvent
                    historicActivity -> historicActivity.getActivityId().equals(START_USER_NODE_ID))) {
                ActivityNodeTask startTask = new ActivityNodeTask().setId(BpmnModelConstants.START_USER_NODE_ID)
                        .setAssignee(startUserId).setStatus(BpmTaskStatusEnum.APPROVE.getStatus());
                ActivityNode startNode = new ActivityNode().setId(startTask.getId())
                        .setName(BpmSimpleModelNodeTypeEnum.START_USER_NODE.getName())
                        .setNodeType(BpmSimpleModelNodeTypeEnum.START_USER_NODE.getType())
                        .setStatus(startTask.getStatus()).setTasks(ListUtil.of(startTask))
                        .setStartTime(DateUtils.of(activity.getStartTime()))
                        .setEndTime(DateUtils.of(activity.getEndTime()));
                approvalNodes.add(0, startNode);
                return;
            }
            // EndEvent
            if (ELEMENT_EVENT_END.equals(activity.getActivityType())) {
                if (BpmProcessInstanceStatusEnum.isRejectStatus(processInstanceStatus)) {
                    // 拒绝情况下，不需要展示 EndEvent 结束节点。原因是：前端已经展示 x 效果，无需重复展示
                    return;
                }
                ActivityNode endNode = new ActivityNode().setId(activity.getId())
                        .setName(BpmSimpleModelNodeTypeEnum.END_NODE.getName())
                        .setNodeType(BpmSimpleModelNodeTypeEnum.END_NODE.getType()).setStatus(processInstanceStatus)
                        .setStartTime(DateUtils.of(activity.getStartTime()))
                        .setEndTime(DateUtils.of(activity.getEndTime()));
                String reason = FlowableUtils.getProcessInstanceReason(historicProcessInstance);
                if (StrUtil.isNotEmpty(reason)) {
                    endNode.setTasks(singletonList(new ActivityNodeTask().setId(endNode.getId())
                            .setStatus(endNode.getStatus()).setReason(reason)));
                }
                approvalNodes.add(endNode);
            }
            // CallActivity
            if (ELEMENT_CALL_ACTIVITY.equals(activity.getActivityType())) {
                ActivityNode callActivity = new ActivityNode().setId(activity.getId())
                        .setName(BpmSimpleModelNodeTypeEnum.CHILD_PROCESS.getName())
                        .setNodeType(BpmSimpleModelNodeTypeEnum.CHILD_PROCESS.getType()).setStatus(processInstanceStatus)
                        .setStartTime(DateUtils.of(activity.getStartTime()))
                        .setEndTime(DateUtils.of(activity.getEndTime()))
                        .setProcessInstanceId(activity.getCalledProcessInstanceId());
                approvalNodes.add(callActivity);
            }
        });

        // 按照时间排序
        approvalNodes.sort(Comparator.comparing(ActivityNode::getStartTime));
        return approvalNodes;
    }

    /**
     * 获取结束节点的状态
     */
    private Integer getEndActivityNodeStatus(HistoricTaskInstance task) {
        Integer status = FlowableUtils.getTaskStatus(task);
        if (status != null) {
            return status;
        }
        // 结束节点未获取到状态，为跳过状态。可见 bpmn 或者 simple 的 skipExpression
        return BpmTaskStatusEnum.SKIP.getStatus();
    }

    /**
     * 获得【进行中】的活动节点们
     * 
     * 说明：运行中的节点是指当前正在执行但尚未完成的审批节点或子流程节点
     * 
     * @param startUserId 流程发起人用户 ID
     * @param bpmnModel BPMN 流程模型
     * @param processDefinition 流程定义
     * @param processVariables 流程变量
     * @param activities 历史活动实例列表
     * @param tasks 历史任务实例列表
     * @return 运行中的活动节点列表
     */
    private List<ActivityNode> getRunApproveNodeList(Long startUserId,
                                                     BpmnModel bpmnModel,
                                                     ProcessDefinition processDefinition,
                                                     Map<String, Object> processVariables,
                                                     List<HistoricActivityInstance> activities,
                                                     List<HistoricTaskInstance> tasks) {
        // 1. 筛选运行中的任务和子流程（endTime 为 null 表示尚未结束）
        // 只关注用户任务（ELEMENT_TASK_USER）和子流程调用活动（ELEMENT_CALL_ACTIVITY）
        List<HistoricActivityInstance> runActivities = filterList(activities, activity -> activity.getEndTime() == null
                && (StrUtil.equalsAny(activity.getActivityType(), ELEMENT_TASK_USER, ELEMENT_CALL_ACTIVITY)));
        // 按 activityId 分组，方便后续处理（同一个活动节点可能有多个任务实例，如会签场景）
        Map<String, List<HistoricActivityInstance>> runningTaskMap = convertMultiMap(runActivities,
                HistoricActivityInstance::getActivityId);

        // 2. 构建任务 ID 到任务实例的映射，用于快速查找任务详情
        Map<String, HistoricTaskInstance> taskMap = convertMap(tasks, HistoricTaskInstance::getId);
        
        // 3. 遍历每个活动节点（activityId），构建活动节点信息
        return convertList(runningTaskMap.entrySet(), entry -> {
            String activityId = entry.getKey();
            List<HistoricActivityInstance> taskActivities = entry.getValue();
            // 构建活动节点
            FlowElement flowNode = BpmnModelUtils.getFlowElementById(bpmnModel, activityId);
            HistoricActivityInstance firstActivity = CollUtil.getFirst(taskActivities); // 取第一个任务，会签/或签的任务，开始时间相同
            ActivityNode activityNode = new ActivityNode().setId(firstActivity.getActivityId())
                    .setName(firstActivity.getActivityName())
                    .setNodeType(ObjUtil.defaultIfNull(parseNodeType(flowNode), // 目的：解决“办理节点”和"子流程"的识别
                            BpmSimpleModelNodeTypeEnum.APPROVE_NODE.getType()))
                    .setStatus(BpmTaskStatusEnum.RUNNING.getStatus())
                    .setCandidateStrategy(BpmnModelUtils.parseCandidateStrategy(flowNode))
                    .setStartTime(DateUtils.of(CollUtil.getFirst(taskActivities).getStartTime()))
                    .setTasks(new ArrayList<>());
            // 处理每个任务的 tasks 属性
            for (HistoricActivityInstance activity : taskActivities) {
                HistoricTaskInstance task = taskMap.get(activity.getTaskId());
                // 特殊情况：子流程节点 ChildProcess 仅存在于 activity 中，并且没有自身的 task，需要跳过执行
                // TODO @芋艿：后续看看怎么优化！
                if (task == null) {
                    continue;
                }
                activityNode.getTasks().add(BpmProcessInstanceConvert.INSTANCE.buildApprovalTaskInfo(task));
                // 加签子任务，需要过滤掉已经完成的加签子任务
                List<HistoricTaskInstance> childrenTasks = filterList(
                        taskService.getAllChildrenTaskListByParentTaskId(activity.getTaskId(), tasks),
                        childTask -> childTask.getEndTime() == null);
                if (CollUtil.isNotEmpty(childrenTasks)) {
                    activityNode.getTasks().addAll(
                            convertList(childrenTasks, BpmProcessInstanceConvert.INSTANCE::buildApprovalTaskInfo));
                }
            }
            // 处理每个任务的 candidateUsers 属性：如果是依次审批，需要预测它的后续审批人。因为 Task 是审批完一个，创建一个新的 Task
            if (BpmnModelUtils.isSequentialUserTask(flowNode)) {
                List<Long> candidateUserIds = getTaskCandidateUserList(bpmnModel, flowNode.getId(),
                        startUserId, processDefinition.getId(), processVariables);
                // 截取当前审批人位置后面的候选人，不包含当前审批人
                ActivityNodeTask approvalTaskInfo = CollUtil.getFirst(activityNode.getTasks());
                Assert.notNull(approvalTaskInfo, "任务不能为空");
                int index = CollUtil.indexOf(candidateUserIds,
                        userId -> ObjectUtils.equalsAny(userId, approvalTaskInfo.getOwner(),
                                approvalTaskInfo.getAssignee())); // 委派或者向前加签情况，需要先比较 owner
                activityNode.setCandidateUserIds(CollUtil.sub(candidateUserIds, index + 1, candidateUserIds.size()));
            }
            if (BpmSimpleModelNodeTypeEnum.CHILD_PROCESS.getType().equals(activityNode.getNodeType())) {
                activityNode.setProcessInstanceId(firstActivity.getCalledProcessInstanceId());
            }
            return activityNode;
        });
    }

    /**
     * 获得【预测（未来）】的活动节点们
     * 
     * 说明：预测节点是指尚未执行但根据流程定义和当前流程变量推测可能执行的审批节点
     * 这个功能主要用于：
     * 1. 流程发起时，预测整个流程的审批路径，让发起人了解流程走向
     * 2. 流程审批中，预测后续的审批节点和审批人
     * 3. 条件分支场景下，根据流程变量预测实际会走的分支路径
     * 
     * @param startUserId 流程发起人用户 ID
     * @param bpmnModel BPMN 流程模型
     * @param processDefinitionInfo 流程定义扩展信息
     * @param processVariables 流程变量（用于条件判断）
     * @param activities 历史活动实例列表
     * @param needSimulateTaskDefKeysByReturn 因退回操作需要重新模拟的任务定义 Key 集合
     * @return 预测的活动节点列表
     */
    private List<ActivityNode> getSimulateApproveNodeList(Long startUserId, BpmnModel bpmnModel,
                                                          BpmProcessDefinitionInfoDO processDefinitionInfo,
                                                          Map<String, Object> processVariables,
                                                          List<HistoricActivityInstance> activities,
                                                          Set<String> needSimulateTaskDefKeysByReturn) {
        // TODO @芋艿：【可优化】在驳回场景下，未来的预测准确性不高。原因是，驳回后，HistoricActivityInstance
        // 包括了历史的操作，不是只有 startEvent 到当前节点的记录
        Set<String> runActivityIds = convertSet(activities, HistoricActivityInstance::getActivityId);
        // 情况一：BPMN 设计器
        if (Objects.equals(BpmModelTypeEnum.BPMN.getType(), processDefinitionInfo.getModelType())) {
            List<FlowElement> flowElements = BpmnModelUtils.simulateProcess(bpmnModel, processVariables);
            return convertList(flowElements, flowElement -> buildNotRunApproveNodeForBpmn(
                    startUserId, bpmnModel, flowElements,
                    processDefinitionInfo, processVariables, flowElement, runActivityIds, needSimulateTaskDefKeysByReturn));
        }
        // 情况二：SIMPLE 设计器
        if (Objects.equals(BpmModelTypeEnum.SIMPLE.getType(), processDefinitionInfo.getModelType())) {
            BpmSimpleModelNodeVO simpleModel = JsonUtils.parseObject(processDefinitionInfo.getSimpleModel(),
                    BpmSimpleModelNodeVO.class);
            List<BpmSimpleModelNodeVO> simpleNodes = SimpleModelUtils.simulateProcess(simpleModel, processVariables);
            return convertList(simpleNodes, simpleNode -> buildNotRunApproveNodeForSimple(
                    startUserId, bpmnModel,
                    processDefinitionInfo, processVariables, simpleNode, runActivityIds, needSimulateTaskDefKeysByReturn));
        }
        throw new IllegalArgumentException("未知设计器类型：" + processDefinitionInfo.getModelType());
    }

    private ActivityNode buildNotRunApproveNodeForSimple(Long startUserId, BpmnModel bpmnModel,
                                                         BpmProcessDefinitionInfoDO processDefinitionInfo, Map<String, Object> processVariables,
                                                         BpmSimpleModelNodeVO node, Set<String> runActivityIds,
                                                         Set<String> needSimulateTaskDefKeysByReturn) {
        // TODO @芋艿：【可优化】在驳回场景下，未来的预测准确性不高。原因是，驳回后，HistoricActivityInstance
        // 包括了历史的操作，不是只有 startEvent 到当前节点的记录
        if (runActivityIds.contains(node.getId())
                && !needSimulateTaskDefKeysByReturn.contains(node.getId())) { // 特殊：回退操作时候，会记录需要预测的节点到流程变量中。即使在历史操作中，也需要预测
            return null;
        }
        Integer status = BpmTaskStatusEnum.NOT_START.getStatus();
        // 如果节点被跳过。设置状态为跳过
        if (SimpleModelUtils.isSkipNode(node, processVariables)) {
            status = BpmTaskStatusEnum.SKIP.getStatus();
        }
        ActivityNode activityNode = new ActivityNode().setId(node.getId()).setName(node.getName())
                .setNodeType(node.getType()).setCandidateStrategy(node.getCandidateStrategy())
                .setStatus(status);

        // 1. 开始节点/审批节点
        if (ObjectUtils.equalsAny(node.getType(),
                BpmSimpleModelNodeTypeEnum.START_USER_NODE.getType(),
                BpmSimpleModelNodeTypeEnum.APPROVE_NODE.getType(),
                BpmSimpleModelNodeTypeEnum.TRANSACTOR_NODE.getType())) {
            List<Long> candidateUserIds = getTaskCandidateUserList(bpmnModel, node.getId(),
                    startUserId, processDefinitionInfo.getProcessDefinitionId(), processVariables);
            activityNode.setCandidateUserIds(candidateUserIds);
            return activityNode;
        }

        // 2. 结束节点
        if (BpmSimpleModelNodeTypeEnum.END_NODE.getType().equals(node.getType())) {
            return activityNode;
        }

        // 3. 抄送节点
        if (CollUtil.isEmpty(runActivityIds) && // 流程发起时：需要展示抄送节点，用于选择抄送人
                BpmSimpleModelNodeTypeEnum.COPY_NODE.getType().equals(node.getType())) {
            List<Long> candidateUserIds = getTaskCandidateUserList(bpmnModel, node.getId(),
                    startUserId, processDefinitionInfo.getProcessDefinitionId(), processVariables);
            activityNode.setCandidateUserIds(candidateUserIds);
            return activityNode;
        }

        // 4. 子流程节点
        if (BpmSimpleModelNodeTypeEnum.CHILD_PROCESS.getType().equals(node.getType())) {
            return activityNode;
        }
        return null;
    }

    private ActivityNode buildNotRunApproveNodeForBpmn(Long startUserId, BpmnModel bpmnModel, List<FlowElement> flowElements,
                                                       BpmProcessDefinitionInfoDO processDefinitionInfo,
                                                       Map<String, Object> processVariables,
                                                       FlowElement node, Set<String> runActivityIds,
                                                       Set<String> needSimulateTaskDefKeysByReturn) {
        // 回退操作时候，会记录需要预测的节点到流程变量中。即使节点在历史操作中，也需要预测。
        if (!needSimulateTaskDefKeysByReturn.contains(node.getId()) && runActivityIds.contains(node.getId())) {
            return null;
        }

        Integer status = BpmTaskStatusEnum.NOT_START.getStatus();
        // 如果节点被跳过，状态设置为跳过
        if (BpmnModelUtils.isSkipNode(node, processVariables)) {
            status = BpmTaskStatusEnum.SKIP.getStatus();
        }
        ActivityNode activityNode = new ActivityNode().setId(node.getId())
                .setStatus(status);

        // 1. 开始节点
        if (node instanceof StartEvent) {
            if (CollUtil.contains(flowElements, // 特殊：如果已经存在用户手动创建的 START_USER_NODE_ID 节点，则忽略 StartEvent
                    flowElement -> flowElement.getId().equals(START_USER_NODE_ID))) {
                return null;
            }
            return activityNode.setName(BpmSimpleModelNodeTypeEnum.START_USER_NODE.getName())
                    .setNodeType(BpmSimpleModelNodeTypeEnum.START_USER_NODE.getType());
        }

        // 2. 审批节点
        if (node instanceof UserTask) {
            List<Long> candidateUserIds = getTaskCandidateUserList(bpmnModel, node.getId(),
                    startUserId, processDefinitionInfo.getProcessDefinitionId(), processVariables);
            return activityNode.setName(node.getName()).setNodeType(BpmSimpleModelNodeTypeEnum.APPROVE_NODE.getType())
                    .setCandidateStrategy(BpmnModelUtils.parseCandidateStrategy(node))
                    .setCandidateUserIds(candidateUserIds);
        }

        // 3. 结束节点
        if (node instanceof EndEvent) {
            return activityNode.setName(BpmSimpleModelNodeTypeEnum.END_NODE.getName())
                    .setNodeType(BpmSimpleModelNodeTypeEnum.END_NODE.getType());
        }
        return null;
    }

    private List<Long> getTaskCandidateUserList(BpmnModel bpmnModel, String activityId,
                                                Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        Set<Long> userIds = taskCandidateInvoker.calculateUsersByActivity(bpmnModel, activityId,
                startUserId, processDefinitionId, processVariables);
        return new ArrayList<>(userIds);
    }

    @Override
    public BpmProcessInstanceBpmnModelViewRespVO getProcessInstanceBpmnModelView(String id) {
        // 1.1 获得流程实例
        HistoricProcessInstance processInstance = getHistoricProcessInstance(id);
        if (processInstance == null) {
            return null;
        }
        // 1.2 获得流程定义
        BpmnModel bpmnModel = processDefinitionService
                .getProcessDefinitionBpmnModel(processInstance.getProcessDefinitionId());
        if (bpmnModel == null) {
            return null;
        }
        BpmSimpleModelNodeVO simpleModel = null;
        BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService.getProcessDefinitionInfo(
                processInstance.getProcessDefinitionId());
        if (processDefinitionInfo != null
                && BpmModelTypeEnum.SIMPLE.getType().equals(processDefinitionInfo.getModelType())) {
            simpleModel = JsonUtils.parseObject(processDefinitionInfo.getSimpleModel(), BpmSimpleModelNodeVO.class);
        }
        // 1.3 获得流程实例对应的活动实例列表 + 任务列表
        List<HistoricActivityInstance> activities = taskService.getActivityListByProcessInstanceId(id);
        List<HistoricTaskInstance> tasks = taskService.getTaskListByProcessInstanceId(id, true);

        // 2.1 拼接进度信息
        Set<String> unfinishedTaskActivityIds = convertSet(activities, HistoricActivityInstance::getActivityId,
                activityInstance -> activityInstance.getEndTime() == null);
        Set<String> finishedTaskActivityIds = convertSet(activities, HistoricActivityInstance::getActivityId,
                activityInstance -> activityInstance.getEndTime() != null
                        && ObjectUtil.notEqual(activityInstance.getActivityType(),
                        BpmnXMLConstants.ELEMENT_SEQUENCE_FLOW));
        Set<String> finishedSequenceFlowActivityIds = convertSet(activities, HistoricActivityInstance::getActivityId,
                activityInstance -> activityInstance.getEndTime() != null
                        && ObjectUtil.equals(activityInstance.getActivityType(),
                        BpmnXMLConstants.ELEMENT_SEQUENCE_FLOW));
        // 特殊：会签情况下，会有部分已完成（审批）、部分未完成（待审批），此时需要 finishedTaskActivityIds 移除掉
        finishedTaskActivityIds.removeAll(unfinishedTaskActivityIds);
        // 特殊：如果流程实例被拒绝，则需要计算是哪个活动节点。
        // 注意，只取最后一个。因为会存在多次拒绝的情况，拒绝驳回到指定节点
        Set<String> rejectTaskActivityIds = CollUtil.newHashSet();
        if (BpmProcessInstanceStatusEnum.isRejectStatus(FlowableUtils.getProcessInstanceStatus(processInstance))) {
            tasks.stream()
                    .filter(task -> BpmTaskStatusEnum.isRejectStatus(FlowableUtils.getTaskStatus(task)))
                    .max(Comparator.comparing(HistoricTaskInstance::getEndTime))
                    .ifPresent(reject -> rejectTaskActivityIds.add(reject.getTaskDefinitionKey()));
            finishedTaskActivityIds.removeAll(rejectTaskActivityIds);
        }

        // 2.2 拼接基础信息
        Set<Long> userIds = BpmProcessInstanceConvert.INSTANCE.parseUserIds02(processInstance, tasks);
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(userIds);
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(convertSet(userMap.values(), AdminUserRespDTO::getDeptId));
        return BpmProcessInstanceConvert.INSTANCE.buildProcessInstanceBpmnModelView(processInstance, tasks, bpmnModel,
                simpleModel,
                unfinishedTaskActivityIds, finishedTaskActivityIds, finishedSequenceFlowActivityIds,
                rejectTaskActivityIds,
                userMap, deptMap);
    }

    // ========== Update 写入相关方法 ==========

    /**
     * 创建流程实例（通过前端页面发起）
     * 
     * @param userId 发起人用户 ID
     * @param createReqVO 创建流程实例请求参数（包含流程定义 ID、流程变量等）
     * @return 流程实例 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createProcessInstance(Long userId, @Valid BpmProcessInstanceCreateReqVO createReqVO) {
        // 1. 获取流程定义
        ProcessDefinition definition = processDefinitionService
                .getProcessDefinition(createReqVO.getProcessDefinitionId());
        
        // 2. 发起流程实例
        return createProcessInstance0(userId, definition, createReqVO.getVariables(), null,
                createReqVO.getStartUserSelectAssignees());
    }

    /**
     * 创建流程实例（通过 API 接口发起，用于业务系统集成）
     * 
     * @param userId 发起人用户 ID
     * @param createReqDTO 创建流程实例请求 DTO（包含流程定义 Key、业务 Key、流程变量等）
     * @return 流程实例 ID
     */
    @Override
    public String createProcessInstance(Long userId, @Valid BpmProcessInstanceCreateReqDTO createReqDTO) {
        return FlowableUtils.executeAuthenticatedUserId(userId, () -> {
            // 获得流程定义
            ProcessDefinition definition = processDefinitionService
                    .getActiveProcessDefinition(createReqDTO.getProcessDefinitionKey());
            // 发起流程
            return createProcessInstance0(userId, definition, createReqDTO.getVariables(),
                    createReqDTO.getBusinessKey(),
                    createReqDTO.getStartUserSelectAssignees());
        });
    }

    /**
     * 创建流程实例的核心方法（内部方法）
     * 
     * 此方法封装了流程实例创建的核心逻辑，包括：
     * 1. 校验流程定义和发起权限
     * 2. 设置流程变量
     * 3. 生成流程实例 ID（可自定义编号规则）
     * 4. 生成流程实例名称（支持动态标题）
     * 5. 启动流程实例
     * 
     * @param userId 发起人用户 ID
     * @param definition 流程定义对象
     * @param variables 流程变量（业务数据）
     * @param businessKey 业务 Key（用于关联业务表单）
     * @param startUserSelectAssignees 发起人自选审批人映射（节点 ID -> 审批人 ID 列表）
     * @return 流程实例 ID
     */
    private String createProcessInstance0(Long userId, ProcessDefinition definition,
                                          Map<String, Object> variables, String businessKey,
                                          Map<String, List<Long>> startUserSelectAssignees) {
        // ========== 1. 校验阶段 ==========
        
        // 1.1 校验流程定义是否存在
        if (definition == null) {
            throw exception(PROCESS_DEFINITION_NOT_EXISTS);
        }
        // 1.2 校验流程定义是否被挂起（挂起的流程不能发起）
        if (definition.isSuspended()) {
            throw exception(PROCESS_DEFINITION_IS_SUSPENDED);
        }
        // 1.3 获取流程定义扩展信息（包含表单配置、权限配置等）
        BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService
                .getProcessDefinitionInfo(definition.getId());
        if (processDefinitionInfo == null) {
            throw exception(PROCESS_DEFINITION_NOT_EXISTS);
        }
        // 1.4 校验当前用户是否有权限发起该流程
        // 流程可以配置哪些用户/角色/部门可以发起
        if (!processDefinitionService.canUserStartProcessDefinition(processDefinitionInfo, userId)) {
            throw exception(PROCESS_INSTANCE_START_USER_CAN_START);
        }
        // 1.5 校验发起人自选审批人配置是否完整
        // 某些节点可能配置为"发起人自选审批人"策略，需要在发起时指定审批人
        validateStartUserSelectAssignees(userId, definition, startUserSelectAssignees, variables);

        // ========== 2. 准备流程变量 ==========
        
        // 2.1 初始化流程变量容器
        if (variables == null) {
            variables = new HashMap<>();
        }
        
        // 2.2 过滤掉系统保留的流程变量，避免业务变量与系统变量冲突
        FlowableUtils.filterProcessInstanceFormVariable(variables);
        
        // 2.3 设置发起人 ID（用于记录流程发起人，后续可用于权限判断、消息通知等）
        variables.put(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_ID, userId);
        
        // 2.4 设置流程实例状态为"审批中"
        variables.put(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                BpmProcessInstanceStatusEnum.RUNNING.getStatus());
        
        // 2.5 启用跳过表达式功能（允许通过 skipExpression 配置节点跳过条件）
        variables.put(BpmnVariableConstants.PROCESS_INSTANCE_SKIP_EXPRESSION_ENABLED, true);
        
        // 2.6 设置发起人自选审批人（如果有配置）
        if (CollUtil.isNotEmpty(startUserSelectAssignees)) {
            variables.put(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES,
                    startUserSelectAssignees);
        }

        // ========== 3. 构建并启动流程实例 ==========
        
        // 3.1 创建流程实例构建器
        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder()
                .processDefinitionId(definition.getId())  // 流程定义 ID
                .businessKey(businessKey)  // 业务 Key（用于关联业务表单）
                .variables(variables);  // 流程变量
        
        // 3.2 生成自定义流程实例 ID（如果配置了编号规则）
        // 支持自定义编号格式，例如：LEAVE-20250101-0001
        BpmModelMetaInfoVO.ProcessIdRule processIdRule = processDefinitionInfo.getProcessIdRule();
        if (processIdRule != null && Boolean.TRUE.equals(processIdRule.getEnable())) {
            processInstanceBuilder.predefineProcessInstanceId(processIdRedisDAO.generate(processIdRule));
        }
        
        // 3.3 生成流程实例名称（支持动态标题）
        processInstanceBuilder.name(generateProcessInstanceName(userId, definition, processDefinitionInfo, variables));
        
        // 3.4 启动流程实例
        ProcessInstance instance = processInstanceBuilder.start();
        
        // 3.5 返回流程实例 ID
        return instance.getId();
    }

    /**
     * 校验发起人自选审批人配置
     * 
     * 说明：当流程中某些节点配置为"发起人自选审批人"策略时，需要在流程发起时校验：
     * 1. 发起人是否为所有自选节点都指定了审批人
     * 2. 指定的审批人是否都是有效用户
     * 
     * 使用场景：
     * - 临时项目审批流程，由发起人指定评审专家
     * - 跨部门协作流程，由发起人指定协作人员
     * 
     * @param userId 发起人用户 ID
     * @param definition 流程定义
     * @param startUserSelectAssignees 发起人选择的审批人映射（节点 ID -> 审批人 ID 列表）
     * @param variables 流程变量
     */
    private void validateStartUserSelectAssignees(Long userId, ProcessDefinition definition,
                                                  Map<String, List<Long>> startUserSelectAssignees,
                                                  Map<String, Object> variables) {
        // 1. 获取流程的预测节点信息（根据流程变量预测会经过哪些节点）
        BpmApprovalDetailRespVO detailRespVO = getApprovalDetail(userId, new BpmApprovalDetailReqVO()
                .setProcessDefinitionId(definition.getId())
                .setProcessVariables(variables));
        List<ActivityNode> activityNodes = detailRespVO.getActivityNodes();
        if (CollUtil.isEmpty(activityNodes)) {
            return;
        }

        // 2. 筛选出需要"发起人自选审批人"的节点
        activityNodes.removeIf(task ->
                ObjectUtil.notEqual(BpmTaskCandidateStrategyEnum.START_USER_SELECT.getStrategy(), 
                        task.getCandidateStrategy()));
        
        // 3. 校验每个自选节点是否都配置了审批人
        // 只校验预测的节点，因为实际执行时可能因条件分支走向不同而跳过某些节点
        activityNodes.forEach(task -> {
            // 3.1 获取该节点的审批人列表
            List<Long> assignees = startUserSelectAssignees != null ? 
                    startUserSelectAssignees.get(task.getId()) : null;
            
            // 3.2 校验是否为该节点配置了审批人
            if (CollUtil.isEmpty(assignees)) {
                throw exception(PROCESS_INSTANCE_START_USER_SELECT_ASSIGNEES_NOT_CONFIG, task.getName());
            }
            
            // 3.3 校验配置的审批人是否都是有效用户
            Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(assignees);
            assignees.forEach(assignee -> {
                if (userMap.get(assignee) == null) {
                    throw exception(PROCESS_INSTANCE_START_USER_SELECT_ASSIGNEES_NOT_EXISTS, 
                            task.getName(), assignee);
                }
            });
        });
    }

    /**
     * 生成流程实例名称
     * 
     * 说明：根据配置的标题模板生成动态流程实例名称
     * 支持使用流程变量作为占位符，例如：
     * - ${startUserName}的请假申请
     * - ${startTime} 报销单
     * - ${amount}元采购申请
     * 
     * @param userId 发起人用户 ID
     * @param definition 流程定义
     * @param definitionInfo 流程定义扩展信息（包含标题配置）
     * @param variables 流程变量
     * @return 生成的流程实例名称
     */
    private String generateProcessInstanceName(Long userId,
                                               ProcessDefinition definition,
                                               BpmProcessDefinitionInfoDO definitionInfo,
                                               Map<String, Object> variables) {
        // 1. 基础校验
        if (definition == null || definitionInfo == null) {
            return null;
        }
        
        // 2. 获取标题配置
        BpmModelMetaInfoVO.TitleSetting titleSetting = definitionInfo.getTitleSetting();
        // 如果未配置标题或未启用，则使用流程定义名称
        if (titleSetting == null || !BooleanUtil.isTrue(titleSetting.getEnable())) {
            return definition.getName();
        }
        
        // 3. 准备标题模板变量
        AdminUserRespDTO user = adminUserApi.getUser(userId).getCheckedData();
        Map<String, Object> cloneVariables = new HashMap<>(variables);
        // 添加系统变量：发起人昵称
        cloneVariables.put(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_ID, user.getNickname());
        // 添加系统变量：流程发起时间
        cloneVariables.put(BpmnVariableConstants.PROCESS_START_TIME, DateUtil.now());
        // 添加系统变量：流程定义名称
        cloneVariables.put(BpmnVariableConstants.PROCESS_DEFINITION_NAME, definition.getName().trim());
        
        // 4. 使用模板引擎替换占位符，生成最终标题
        return StrUtil.format(definitionInfo.getTitleSetting().getTitle(), cloneVariables);
    }

    /**
     * 发起人取消流程实例
     * 
     * 说明：允许流程发起人在审批过程中撤销自己发起的流程申请
     * 使用场景：发起人发现申请内容有误，或者不需要走该流程了，可以主动取消
     * 
     * @param userId 当前登录用户 ID（必须是流程发起人）
     * @param cancelReqVO 取消请求参数（包含流程实例 ID 和取消原因）
     */
    @Override
    public void cancelProcessInstanceByStartUser(Long userId, @Valid BpmProcessInstanceCancelReqVO cancelReqVO) {
        // ========== 1. 校验阶段 ==========
        
        // 1.1 校验流程实例是否存在
        ProcessInstance instance = getProcessInstance(cancelReqVO.getId());
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_CANCEL_FAIL_NOT_EXISTS);
        }
        
        // 1.2 校验只能取消自己发起的流程
        if (!Objects.equals(instance.getStartUserId(), String.valueOf(userId))) {
            throw exception(PROCESS_INSTANCE_CANCEL_FAIL_NOT_SELF);
        }
        
        // 1.3 校验流程定义是否允许撤销
        // 有些流程可能不允许撤销，例如财务审批流程
        BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService
                .getProcessDefinitionInfo(instance.getProcessDefinitionId());
        Assert.notNull(processDefinitionInfo, "流程定义({})不存在", processDefinitionInfo);
        if (processDefinitionInfo.getAllowCancelRunningProcess() != null // 防止未配置时，默认为可取消
                && BooleanUtil.isFalse(processDefinitionInfo.getAllowCancelRunningProcess())) {
            throw exception(PROCESS_INSTANCE_CANCEL_FAIL_NOT_ALLOW);
        }
        
        // 1.4 子流程不允许取消（子流程由父流程控制）
        if (StrUtil.isNotBlank(instance.getSuperExecutionId())) {
            throw exception(PROCESS_INSTANCE_CANCEL_CHILD_FAIL_NOT_ALLOW);
        }

        // ========== 2. 执行取消操作 ==========
        
        updateProcessInstanceCancel(cancelReqVO.getId(),
                BpmReasonEnum.CANCEL_PROCESS_INSTANCE_BY_START_USER.format(cancelReqVO.getReason()));
    }

    /**
     * 管理员取消流程实例
     * 
     * 说明：允许系统管理员强制取消任何流程实例
     * 使用场景：
     * 1. 流程异常需要人工干预
     * 2. 业务需要紧急中止某个流程
     * 3. 发起人无法自行取消时，由管理员协助处理
     * 
     * @param userId 管理员用户 ID
     * @param cancelReqVO 取消请求参数（包含流程实例 ID 和取消原因）
     */
    @Override
    public void cancelProcessInstanceByAdmin(Long userId, BpmProcessInstanceCancelReqVO cancelReqVO) {
        // 1. 校验流程实例是否存在
        ProcessInstance instance = getProcessInstance(cancelReqVO.getId());
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_CANCEL_FAIL_NOT_EXISTS);
        }

        // 2. 执行取消操作（记录管理员信息和取消原因）
        AdminUserRespDTO user = adminUserApi.getUser(userId).getCheckedData();
        updateProcessInstanceCancel(cancelReqVO.getId(),
                BpmReasonEnum.CANCEL_PROCESS_INSTANCE_BY_ADMIN.format(user.getNickname(), cancelReqVO.getReason()));
    }

    /**
     * 更新流程实例为取消状态（内部方法）
     * 
     * 此方法执行取消流程的核心逻辑：
     * 1. 更新流程实例状态为"已取消"
     * 2. 递归取消所有子流程
     * 3. 结束流程实例
     * 
     * @param id 流程实例 ID
     * @param reason 取消原因
     */
    private void updateProcessInstanceCancel(String id, String reason) {
        // 1. 更新流程实例状态为"已取消"，并记录取消原因
        runtimeService.setVariable(id, BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                BpmProcessInstanceStatusEnum.CANCEL.getStatus());
        runtimeService.setVariable(id, BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_REASON, reason);

        // 2. 递归取消所有子流程（如果该流程包含子流程调用）
        // 父流程取消时，所有子流程也必须取消
        List<ProcessInstance> childProcessInstances = runtimeService.createProcessInstanceQuery()
                .superProcessInstanceId(id).list();
        childProcessInstances.forEach(processInstance -> updateProcessInstanceCancel(
                processInstance.getProcessInstanceId(), 
                BpmReasonEnum.CANCEL_CHILD_PROCESS_INSTANCE_BY_MAIN_PROCESS.getReason()));

        // 3. 结束流程实例（将流程直接跳转到结束节点）
        taskService.moveTaskToEnd(id, reason);
    }

    /**
     * 更新流程实例为拒绝状态
     * 
     * 说明：当审批人拒绝任务时，会调用此方法更新流程实例状态
     * 拒绝后，流程会根据配置跳转到结束节点或返回到指定节点
     * 
     * @param processInstance 流程实例对象
     * @param reason 拒绝原因
     */
    @Override
    public void updateProcessInstanceReject(ProcessInstance processInstance, String reason) {
        // 1. 更新流程实例状态为"已拒绝"
        runtimeService.setVariable(processInstance.getProcessInstanceId(),
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                BpmProcessInstanceStatusEnum.REJECT.getStatus());
        
        // 2. 记录拒绝原因
        runtimeService.setVariable(processInstance.getProcessInstanceId(),
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_REASON,
                BpmReasonEnum.REJECT_TASK.format(reason));
    }

    @Override
    public void updateProcessInstanceVariables(String id, Map<String, Object> variables) {
        runtimeService.setVariables(id, variables);
    }

    @Override
    public void removeProcessInstanceVariables(String id, Collection<String> variableNames) {
        runtimeService.removeVariables(id, variableNames);
    }

    // ========== Event 事件相关方法 ==========

    /**
     * 处理流程实例完成事件
     * 
     * 说明：当流程实例执行到结束节点时，Flowable 会触发此方法
     * 此方法负责：
     * 1. 判断流程最终状态（通过/拒绝/取消）
     * 2. 处理子流程拒绝导致父流程拒绝的场景
     * 3. 发送消息通知
     * 4. 发布流程状态事件
     * 5. 执行流程后置触发器（HTTP 回调）
     * 
     * @param instance 已完成的流程实例对象
     */
    @Override
    public void processProcessInstanceCompleted(ProcessInstance instance) {
        // ========== 1. 判断流程最终状态 ==========
        
        // 1.1 获取流程变量中的状态和原因
        Integer status = (Integer) instance.getProcessVariables()
                .get(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS);
        String reason = (String) instance.getProcessVariables()
                .get(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_REASON);
        
        // 1.2 判断流程是否正常通过
        // 如果流程走到结束节点且状态仍是"审批中"，说明是正常审批通过
        // （取消和拒绝状态在相应操作时已经设置）
        if (Objects.equals(status, BpmProcessInstanceStatusEnum.RUNNING.getStatus())) {
            status = BpmProcessInstanceStatusEnum.APPROVE.getStatus();
            runtimeService.setVariable(instance.getId(), BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                    status);
        }

        // ========== 2. 处理子流程拒绝场景 ==========
        
        // 1.3 如果子流程被拒绝，需要同步拒绝父流程
        // 相关问题链接：https://t.zsxq.com/kZhyb
        // 业务逻辑：子流程拒绝意味着整个流程不能继续，父流程也应该被标记为拒绝
        if (Objects.equals(status, BpmProcessInstanceStatusEnum.REJECT.getStatus())
                && StrUtil.isNotBlank(instance.getSuperExecutionId())) {
            
            // 1.3.1 获取父流程实例并标记为拒绝状态
            Execution execution = runtimeService.createExecutionQuery()
                    .executionId(instance.getSuperExecutionId()).singleResult();
            ProcessInstance parentProcessInstance = getProcessInstance(execution.getProcessInstanceId());
            updateProcessInstanceReject(parentProcessInstance, BpmReasonEnum.REJECT_CHILD_PROCESS.getReason());

            // 1.3.2 结束父流程（需要在子流程事务提交后执行）
            // 使用事务同步机制确保子流程完全结束后再结束父流程
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCompletion(int transactionStatus) {
                    // 如果事务回滚，则不执行父流程结束操作
                    if (ObjectUtil.equal(transactionStatus, TransactionSynchronization.STATUS_ROLLED_BACK)) {
                        return;
                    }
                    // 将父流程移动到结束节点
                    taskService.moveTaskToEnd(parentProcessInstance.getId(), 
                            BpmReasonEnum.REJECT_CHILD_PROCESS.getReason());
                }
            });
        }

        // ========== 3. 发送消息通知 ==========
        
        // 2.1 流程审批通过通知（通知发起人和相关人员）
        if (Objects.equals(status, BpmProcessInstanceStatusEnum.APPROVE.getStatus())) {
            messageService.sendMessageWhenProcessInstanceApprove(
                    BpmProcessInstanceConvert.INSTANCE.buildProcessInstanceApproveMessage(instance));
        } 
        // 2.2 流程审批拒绝通知（通知发起人和相关人员）
        else if (Objects.equals(status, BpmProcessInstanceStatusEnum.REJECT.getStatus())) {
            messageService.sendMessageWhenProcessInstanceReject(
                    BpmProcessInstanceConvert.INSTANCE.buildProcessInstanceRejectMessage(instance, reason));
        }

        // ========== 4. 发布流程状态事件 ==========
        
        // 3. 发送流程实例状态事件（供业务系统监听处理）
        // 业务系统可以监听此事件，执行后续业务逻辑，如更新订单状态、发送邮件等
        processInstanceEventPublisher.sendProcessInstanceResultEvent(
                BpmProcessInstanceConvert.INSTANCE.buildProcessInstanceStatusEvent(this, instance, status, reason));

        // ========== 5. 执行流程后置触发器 ==========
        
        // 4. 流程审批通过后，执行 HTTP 回调（如果配置了后置触发器）
        // 使用场景：流程结束后需要调用外部系统接口，如更新 ERP 系统数据
        if (Objects.equals(status, BpmProcessInstanceStatusEnum.APPROVE.getStatus())) {
            BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService
                    .getProcessDefinitionInfo(instance.getProcessDefinitionId());
            if (ObjUtil.isNotNull(processDefinitionInfo) &&
                    ObjUtil.isNotNull(processDefinitionInfo.getProcessAfterTriggerSetting())) {
                BpmModelMetaInfoVO.HttpRequestSetting setting = processDefinitionInfo.getProcessAfterTriggerSetting();

                BpmHttpRequestUtils.executeBpmHttpRequest(instance,
                        setting.getUrl(), setting.getHeader(), setting.getBody(), true, setting.getResponse());
            }
        }
    }

    /**
     * 处理流程实例创建事件
     * 
     * 说明：当流程实例刚创建时，Flowable 会触发此方法
     * 此方法负责：
     * 1. 生成流程实例名称（特别是处理子流程标题）
     * 2. 执行流程前置触发器（HTTP 回调）
     * 
     * 注意：这些操作必须在事务提交后执行，确保流程变量已正确保存
     * 
     * @param instance 新创建的流程实例对象
     */
    @Override
    public void processProcessInstanceCreated(ProcessInstance instance) {
        // 1. 获取流程定义信息
        BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService
                .getProcessDefinitionInfo(instance.getProcessDefinitionId());
        ProcessDefinition processDefinition = processDefinitionService
                .getProcessDefinition(instance.getProcessDefinitionId());
        
        // 如果流程定义不存在，直接返回
        if (processDefinition == null || processDefinitionInfo == null) {
            return;
        }

        // 2. 注册事务同步回调（在事务提交后执行）
        // 必须使用事务同步机制，否则流程变量可能还未保存到数据库
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                // ========== 2.1 生成并设置流程实例名称 ==========
                
                // 重新生成流程实例名称（主要用于处理子流程的标题）
                // 子流程在创建时可能无法获取完整的流程变量，需要在事务提交后重新生成标题
                String name = generateProcessInstanceName(Long.valueOf(instance.getStartUserId()),
                        processDefinition, processDefinitionInfo, instance.getProcessVariables());
                if (ObjUtil.notEqual(instance.getName(), name)) {
                    runtimeService.setProcessInstanceName(instance.getProcessInstanceId(), name);
                }

                // ========== 2.2 执行流程前置触发器 ==========
                
                // 流程启动后立即执行 HTTP 回调（如果配置了前置触发器）
                // 使用场景：流程启动时需要通知外部系统，如发送短信、更新业务状态等
                // 相关问题链接：https://t.zsxq.com/DF7Kq
                if (ObjUtil.isNull(processDefinitionInfo.getProcessBeforeTriggerSetting())) {
                    return;
                }
                BpmModelMetaInfoVO.HttpRequestSetting setting = processDefinitionInfo.getProcessBeforeTriggerSetting();
                BpmHttpRequestUtils.executeBpmHttpRequest(instance,
                        setting.getUrl(), setting.getHeader(), setting.getBody(), true, setting.getResponse());
            }

        });
    }

}
