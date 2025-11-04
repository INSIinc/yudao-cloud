package cn.iocoder.yudao.module.bpm.service.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.*;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;
import cn.iocoder.yudao.framework.common.util.object.PageUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelMetaInfoVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.*;
import cn.iocoder.yudao.module.bpm.convert.task.BpmTaskConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.definition.*;
import cn.iocoder.yudao.module.bpm.enums.task.BpmCommentTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmReasonEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskSignTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmFormService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.message.BpmMessageService;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskTimeoutReqDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.*;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.ActivityInstance;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.DelegationState;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskInfo;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.flowable.task.service.impl.persistence.entity.TaskEntityImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.stream.Stream;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants.START_USER_NODE_ID;
//import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.*;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.*;

/**
 * 流程任务实例 Service 实现类
 *
 * @author 芋道源码
 * @author jason
 */
@Slf4j
@Service
public class BpmTaskServiceImpl implements BpmTaskService {

    @Resource
    private TaskService taskService;
    @Resource
    private HistoryService historyService;
    @Resource
    private RuntimeService runtimeService;
    @Resource
    private ManagementService managementService;

    @Resource
    private BpmProcessInstanceService processInstanceService;
    @Resource
    private BpmProcessDefinitionService bpmProcessDefinitionService;
    @Resource
    private BpmProcessInstanceCopyService processInstanceCopyService;
    @Resource
    private BpmModelService modelService;
    @Resource
    private BpmMessageService messageService;
    @Resource
    private BpmFormService formService;

    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;

    // ========== Query 查询相关方法 ==========

    @Override
    public PageResult<Task> getTaskTodoPage(Long userId, BpmTaskPageReqVO pageVO) {
        TaskQuery taskQuery = taskService.createTaskQuery()
                .taskAssignee(String.valueOf(userId)) // 分配给自己
                .active()
                .includeProcessVariables()
                .orderByTaskCreateTime().desc(); // 创建时间倒序
        if (StrUtil.isNotBlank(pageVO.getName())) {
            taskQuery.taskNameLike("%" + pageVO.getName() + "%");
        }
        if (StrUtil.isNotEmpty(pageVO.getCategory())) {
            taskQuery.taskCategory(pageVO.getCategory());
        }
        if (StrUtil.isNotEmpty(pageVO.getProcessDefinitionKey())) {
            taskQuery.processDefinitionKey(pageVO.getProcessDefinitionKey());
        }
        if (ArrayUtil.isNotEmpty(pageVO.getCreateTime())) {
            taskQuery.taskCreatedAfter(DateUtils.of(pageVO.getCreateTime()[0]));
            taskQuery.taskCreatedBefore(DateUtils.of(pageVO.getCreateTime()[1]));
        }
        long count = taskQuery.count();
        if (count == 0) {
            return PageResult.empty();
        }
        List<Task> tasks = taskQuery.listPage(PageUtils.getStart(pageVO), pageVO.getPageSize());
        return new PageResult<>(tasks, count);
    }

    @Override
    public BpmTaskRespVO getTodoTask(Long userId, String taskId, String processInstanceId) {
        // 1.1 获取指定的用户待办任务
        Task todoTask = getMyTodoTask(userId, taskId);
        // 1.2 获取不到，则获取该流程实例下，第一个用户的待办任务
        if (todoTask == null) {
            todoTask = getMyFirstTodoTask(userId, processInstanceId);
        }
        if (todoTask == null) {
            return null;
        }

        // 2. 查询该任务的子任务
        List<Task> childrenTasks = getAllChildrenTaskListByParentTaskId(todoTask.getId(), CollUtil.newArrayList(todoTask));

        // 3. 转换返回
        BpmnModel bpmnModel = bpmProcessDefinitionService.getProcessDefinitionBpmnModel(todoTask.getProcessDefinitionId());
        Map<Integer, BpmTaskRespVO.OperationButtonSetting> buttonsSetting = BpmnModelUtils.parseButtonsSetting(
                bpmnModel, todoTask.getTaskDefinitionKey());
        Boolean signEnable = parseSignEnable(bpmnModel, todoTask.getTaskDefinitionKey());
        Boolean reasonRequire = parseReasonRequire(bpmnModel, todoTask.getTaskDefinitionKey());
        Integer nodeType = parseNodeType(BpmnModelUtils.getFlowElementById(bpmnModel, todoTask.getTaskDefinitionKey()));

        // 4. 任务表单
        BpmFormDO taskForm = null;
        if (StrUtil.isNotBlank(todoTask.getFormKey())) {
            taskForm = formService.getForm(NumberUtils.parseLong(todoTask.getFormKey()));
        }

        return BpmTaskConvert.INSTANCE.buildTodoTask(todoTask, childrenTasks, buttonsSetting, taskForm)
                .setNodeType(nodeType).setSignEnable(signEnable).setReasonRequire(reasonRequire);
    }

    /**
     * 获得用户指定 taskId 任务编号的“待办”（未审批、且可审核）的任务
     *
     * @param userId 用户编号
     * @param taskId 任务编号
     * @return 任务
     */
    private Task getMyTodoTask(Long userId, String taskId) {
        if (StrUtil.isEmpty(taskId)) {
            return null;
        }
        Task task = getTask(taskId);
        if (task == null) {
            return null;
        }
        if (!isAssignUserTask(userId, task) && !isAddSignUserTask(userId, task)) {
            return null;
        }
        return task;
    }

    /**
     * 获得用户指定 processInstanceId 流程编号下的首个“待办”（未审批、且可审核）的任务
     *
     * @param userId            用户编号
     * @param processInstanceId 流程编号
     * @return 任务
     */
    private Task getMyFirstTodoTask(Long userId, String processInstanceId) {
        if (processInstanceId == null) {
            return null;
        }
        // 1. 查询所有任务
        List<Task> tasks = taskService.createTaskQuery()
                .active()
                .processInstanceId(processInstanceId)
                .includeTaskLocalVariables()
                .includeProcessVariables()
                .orderByTaskCreateTime().asc() // 按创建时间升序
                .list();

        // 2. 查询我的首个任务
        return CollUtil.findOne(tasks, task -> {
            return isAssignUserTask(userId, task) // 当前用户为审批人
                    || isAddSignUserTask(userId, task); // 当前用户为加签人（为了减签）
        });
    }

    @Override
    public PageResult<HistoricTaskInstance> getTaskDonePage(Long userId, BpmTaskPageReqVO pageVO) {
        HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery()
                .finished() // 已完成
                .taskAssignee(String.valueOf(userId)) // 分配给自己
                .includeTaskLocalVariables()
                .orderByHistoricTaskInstanceEndTime().desc(); // 审批时间倒序
        if (StrUtil.isNotBlank(pageVO.getName())) {
            taskQuery.taskNameLike("%" + pageVO.getName() + "%");
        }
        if (pageVO.getStatus() != null) {
            taskQuery.taskVariableValueEquals(BpmnVariableConstants.TASK_VARIABLE_STATUS, pageVO.getStatus());
        }
//        if (ArrayUtil.isNotEmpty(pageVO.getCreateTime())) {
//            taskQuery.taskCreatedAfter(DateUtils.of(pageVO.getCreateTime()[0]));
//            taskQuery.taskCreatedBefore(DateUtils.of(pageVO.getCreateTime()[1]));
//        }
        // 执行查询
        long count = taskQuery.count();
        if (count == 0) {
            return PageResult.empty();
        }
        List<HistoricTaskInstance> tasks = taskQuery.listPage(PageUtils.getStart(pageVO), pageVO.getPageSize());

        // 特殊：强制移除自动完成的“发起人”节点
        // 补充说明：由于 taskQuery 无法方面的过滤，所以暂时通过内存过滤
        tasks.removeIf(task -> task.getTaskDefinitionKey().equals(START_USER_NODE_ID));
        // TODO @芋艿：https://t.zsxq.com/MNzqp 【flowable bug】：taskCreatedAfter、taskCreatedBefore 拼接的是 OR
        if (ArrayUtil.isNotEmpty(pageVO.getCreateTime())) {
            tasks.removeIf(task -> task.getCreateTime() == null
                    || task.getCreateTime().before(DateUtils.of(pageVO.getCreateTime()[0]))
                    || task.getCreateTime().after(DateUtils.of(pageVO.getCreateTime()[1])));
        }
        return new PageResult<>(tasks, count);
    }

    @Override
    public PageResult<HistoricTaskInstance> getTaskPage(Long userId, BpmTaskPageReqVO pageVO) {
        HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery()
                .includeTaskLocalVariables()
                .taskTenantId(FlowableUtils.getTenantId())
                .orderByHistoricTaskInstanceEndTime().desc(); // 审批时间倒序
        if (StrUtil.isNotBlank(pageVO.getName())) {
            taskQuery.taskNameLike("%" + pageVO.getName() + "%");
        }
        if (StrUtil.isNotEmpty(pageVO.getCategory())) {
            taskQuery.taskCategory(pageVO.getCategory());
        }
//        if (ArrayUtil.isNotEmpty(pageVO.getCreateTime())) {
//            taskQuery.taskCreatedAfter(DateUtils.of(pageVO.getCreateTime()[0]));
//            taskQuery.taskCreatedBefore(DateUtils.of(pageVO.getCreateTime()[1]));
//        }
        // 执行查询
        long count = taskQuery.count();
        if (count == 0) {
            return PageResult.empty();
        }
        List<HistoricTaskInstance> tasks = taskQuery.listPage(PageUtils.getStart(pageVO), pageVO.getPageSize());
        // TODO @芋艿：https://t.zsxq.com/MNzqp 【flowable bug】：taskCreatedAfter、taskCreatedBefore 拼接的是 OR
        if (ArrayUtil.isNotEmpty(pageVO.getCreateTime())) {
            tasks.removeIf(task -> task.getCreateTime() == null
                    || task.getCreateTime().before(DateUtils.of(pageVO.getCreateTime()[0]))
                    || task.getCreateTime().after(DateUtils.of(pageVO.getCreateTime()[1])));
        }
        return new PageResult<>(tasks, count);
    }

    @Override
    public List<Task> getTasksByProcessInstanceIds(List<String> processInstanceIds) {
        if (CollUtil.isEmpty(processInstanceIds)) {
            return Collections.emptyList();
        }
        return taskService.createTaskQuery().processInstanceIdIn(processInstanceIds).list();
    }

    @Override
    public List<HistoricTaskInstance> getTaskListByProcessInstanceId(String processInstanceId, Boolean asc) {
        HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery()
                .includeTaskLocalVariables()
                .processInstanceId(processInstanceId);
        if (Boolean.TRUE.equals(asc)) {
            query.orderByHistoricTaskInstanceStartTime().asc();
        } else {
            query.orderByHistoricTaskInstanceStartTime().desc();
        }
        return query.list();
    }

    @Override
    public Task validateTask(Long userId, String taskId) {
        // 1. 首先检查任务是否存在，如果不存在会抛出异常
        Task task = validateTaskExist(taskId);

        // 2. 判断任务是否指定了具体的处理人（assignee）
        //    - 如果 assignee 为空（比如某些自动审批任务），说明任何人都可以操作，
        //      或系统会自动处理（如自动审批通过），此时 userId 可能为 null，也允许继续
        //    - 如果 assignee 不为空，则必须确保当前操作用户（userId）就是该任务指定的处理人
        if (StrUtil.isNotBlank(task.getAssignee())  // assignee 有值（不是空或空白字符串）
                && ObjectUtil.notEqual(userId, NumberUtils.parseLong(task.getAssignee()))) {
            // 如果当前用户不是任务指定的处理人，则抛出“无权操作此任务”的异常
            throw exception(TASK_OPERATE_FAIL_ASSIGN_NOT_SELF);
        }

        // 3. 验证通过，返回该任务对象
        return task;
    }
    private Task validateTaskExist(String id) {
        Task task = getTask(id);
        if (task == null) {
            throw exception(TASK_NOT_EXISTS);
        }
        return task;
    }

    @Override
    public Task getTask(String id) {
        return taskService.createTaskQuery().taskId(id).includeTaskLocalVariables().singleResult();
    }

    @Override
    public HistoricTaskInstance getHistoricTask(String id) {
        return historyService.createHistoricTaskInstanceQuery().taskId(id).includeTaskLocalVariables().singleResult();
    }

    @Override
    public List<HistoricTaskInstance> getHistoricTasks(Collection<String> taskIds) {
        return historyService.createHistoricTaskInstanceQuery().taskIds(taskIds).includeTaskLocalVariables().list();
    }

    @Override
    public List<Task> getRunningTaskListByProcessInstanceId(String processInstanceId, Boolean assigned, String defineKey) {
        Assert.notNull(processInstanceId, "processInstanceId 不能为空");
        TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(processInstanceId).active()
                .includeTaskLocalVariables();
        if (BooleanUtil.isTrue(assigned)) {
            taskQuery.taskAssigned();
        } else if (BooleanUtil.isFalse(assigned)) {
            taskQuery.taskUnassigned();
        }
        if (StrUtil.isNotEmpty(defineKey)) {
            taskQuery.taskDefinitionKey(defineKey);
        }
        return taskQuery.list();
    }

    @Override
    public List<UserTask> getUserTaskListByReturn(String id) {
        // 1.1 校验当前任务 task 存在
        Task task = validateTaskExist(id);
        // 1.2 根据流程定义获取流程模型信息
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(task.getProcessDefinitionId());
        FlowElement source = BpmnModelUtils.getFlowElementById(bpmnModel, task.getTaskDefinitionKey());
        if (source == null) {
            throw exception(TASK_NOT_EXISTS);
        }

        // 2.1 查询该任务的前置任务节点的 key 集合
        List<UserTask> previousUserList = BpmnModelUtils.getPreviousUserTaskList(source, null, null);
        if (CollUtil.isEmpty(previousUserList)) {
            return Collections.emptyList();
        }
        // 2.2 过滤：只有串行可到达的节点，才可以退回。类似非串行、子流程无法退回
        previousUserList.removeIf(userTask -> !BpmnModelUtils.isSequentialReachable(source, userTask, null));
        return previousUserList;
    }

    @Override
    public <T extends TaskInfo> List<T> getAllChildrenTaskListByParentTaskId(String parentTaskId, List<T> tasks) {
        if (CollUtil.isEmpty(tasks)) {
            return Collections.emptyList();
        }
        Map<String, List<T>> parentTaskMap = convertMultiMap(
                filterList(tasks, task -> StrUtil.isNotEmpty(task.getParentTaskId())), TaskInfo::getParentTaskId);
        if (CollUtil.isEmpty(parentTaskMap)) {
            return Collections.emptyList();
        }

        List<T> result = new ArrayList<>();
        // 1. 递归获取子级
        Stack<String> stack = new Stack<>();
        stack.push(parentTaskId);
        // 2. 递归遍历
        for (int i = 0; i < Short.MAX_VALUE; i++) {
            if (stack.isEmpty()) {
                break;
            }
            // 2.1 获取子任务们
            String taskId = stack.pop();
            List<T> childTaskList = filterList(tasks, task -> StrUtil.equals(task.getParentTaskId(), taskId));
            // 2.2 如果非空，则添加到 stack 进一步递归
            if (CollUtil.isNotEmpty(childTaskList)) {
                stack.addAll(convertList(childTaskList, TaskInfo::getId));
                result.addAll(childTaskList);
            }
        }
        return result;
    }

    /**
     * 获得所有子任务列表
     *
     * @param parentTask 父任务
     * @return 所有子任务列表
     */
    private List<Task> getAllChildTaskList(Task parentTask) {
        List<Task> result = new ArrayList<>();
        // 1. 递归获取子级
        Stack<Task> stack = new Stack<>();
        stack.push(parentTask);
        // 2. 递归遍历
        for (int i = 0; i < Short.MAX_VALUE; i++) {
            if (stack.isEmpty()) {
                break;
            }
            // 2.1 获取子任务们
            Task task = stack.pop();
            List<Task> childTaskList = getTaskListByParentTaskId(task.getId());
            // 2.2 如果非空，则添加到 stack 进一步递归
            if (CollUtil.isNotEmpty(childTaskList)) {
                stack.addAll(childTaskList);
                result.addAll(childTaskList);
            }
        }
        return result;
    }

    @Override
    public List<Task> getTaskListByParentTaskId(String parentTaskId) {
        String tableName = managementService.getTableName(TaskEntity.class);
        // taskService.createTaskQuery() 没有 parentId 参数，所以写 sql 查询
        String sql = "select ID_,NAME_,OWNER_,ASSIGNEE_ from " + tableName + " where PARENT_TASK_ID_=#{parentTaskId}";
        return taskService.createNativeTaskQuery().sql(sql).parameter("parentTaskId", parentTaskId).list();
    }

    /**
     * 获取子任务个数
     *
     * @param parentTaskId 父任务 ID
     * @return 剩余子任务个数
     */
    private Long getTaskCountByParentTaskId(String parentTaskId) {
        String tableName = managementService.getTableName(TaskEntity.class);
        String sql = "SELECT COUNT(1) from " + tableName + " WHERE PARENT_TASK_ID_=#{parentTaskId}";
        return taskService.createNativeTaskQuery().sql(sql).parameter("parentTaskId", parentTaskId).count();
    }

    /**
     * 获得任务根任务的父任务编号
     *
     * @param task 任务
     * @return 根任务的父任务编号
     */
    private String getTaskRootParentId(Task task) {
        if (task == null || task.getParentTaskId() == null) {
            return null;
        }
        for (int i = 0; i < Short.MAX_VALUE; i++) {
            Task parentTask = getTask(task.getParentTaskId());
            if (parentTask == null) {
                return null;
            }
            if (parentTask.getParentTaskId() == null) {
                return parentTask.getId();
            }
            task = parentTask;
        }
        throw new IllegalArgumentException(String.format("Task(%s) 层级过深，无法获取父节点编号", task.getId()));
    }

    @Override
    public List<HistoricActivityInstance> getActivityListByProcessInstanceId(String processInstanceId) {
        return historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc().list();
    }

    @Override
    public List<HistoricActivityInstance> getHistoricActivityListByExecutionId(String executionId) {
        return historyService.createHistoricActivityInstanceQuery().executionId(executionId).list();
    }

    @Override
    public List<HistoricTaskInstance> getFinishedTaskListByProcessInstanceIdWithoutCancel(String processInstanceId) {
        return historyService.createHistoricTaskInstanceQuery()
                .finished()
                .includeTaskLocalVariables()
                .processInstanceId(processInstanceId)
                .taskVariableValueNotEquals(BpmnVariableConstants.TASK_VARIABLE_STATUS,
                        BpmTaskStatusEnum.CANCEL.getStatus())
                .orderByHistoricTaskInstanceStartTime().asc().list();
    }

    /**
     * 判断指定用户，是否是当前任务的审批人
     *
     * @param userId 用户编号
     * @param task   任务
     * @return 是否
     */
    private boolean isAssignUserTask(Long userId, Task task) {
        Long assignee = NumberUtil.parseLong(task.getAssignee(), null);
        return ObjectUtil.equals(userId, assignee);
    }

    /**
     * 判断指定用户，是否是当前任务的拥有人
     *
     * @param userId 用户编号
     * @param task   任务
     * @return 是否
     */
    private boolean isOwnerUserTask(Long userId, Task task) {
        Long assignee = NumberUtil.parseLong(task.getOwner(), null);
        return ObjectUtil.equal(userId, assignee);
    }

    /**
     * 判断指定用户，是否是当前任务的加签人
     *
     * @param userId 用户 Id
     * @param task   任务
     * @return 是否
     */
    private boolean isAddSignUserTask(Long userId, Task task) {
        return (isAssignUserTask(userId, task) || isOwnerUserTask(userId, task))
                && BpmTaskSignTypeEnum.of(task.getScopeType()) != null;
    }

    // ========== Update 写入相关方法 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveTask(Long userId, @Valid BpmTaskApproveReqVO reqVO) {
        // 1.1 校验任务存在
        Task task = validateTask(userId, reqVO.getId());
        // 1.2 校验流程实例存在
        ProcessInstance instance = processInstanceService.getProcessInstance(task.getProcessInstanceId());
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }
        // 1.3 校验签名
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(task.getProcessDefinitionId());
        Boolean signEnable = parseSignEnable(bpmnModel, task.getTaskDefinitionKey());
        if (signEnable && StrUtil.isEmpty(reqVO.getSignPicUrl())) {
            throw exception(TASK_SIGNATURE_NOT_EXISTS);
        }
        // 1.4 校验审批意见
        Boolean reasonRequire = parseReasonRequire(bpmnModel, task.getTaskDefinitionKey());
        if (reasonRequire && StrUtil.isEmpty(reqVO.getReason())) {
            throw exception(TASK_REASON_REQUIRE);
        }

        // 情况一：被委派的任务，不调用 complete 去完成任务
        if (DelegationState.PENDING.equals(task.getDelegationState())) {
            approveDelegateTask(reqVO, task);
            return;
        }

        // 情况二：审批有【后】加签的任务
        if (BpmTaskSignTypeEnum.AFTER.getType().equals(task.getScopeType())) {
            approveAfterSignTask(task, reqVO);
            return;
        }

        // 情况三：审批普通的任务。大多数情况下，都是这样
        // 2.1 更新 task 状态、原因、签字
        updateTaskStatusAndReason(task.getId(), BpmTaskStatusEnum.APPROVE.getStatus(), reqVO.getReason());
        if (signEnable) {
            taskService.setVariableLocal(task.getId(), BpmnVariableConstants.TASK_SIGN_PIC_URL, reqVO.getSignPicUrl());
        }
        // 2.2 添加评论
        taskService.addComment(task.getId(), task.getProcessInstanceId(), BpmCommentTypeEnum.APPROVE.getType(),
                BpmCommentTypeEnum.APPROVE.formatComment(reqVO.getReason()));

        // 3. 设置流程变量。如果流程变量前端传空，需要从历史实例中获取，原因：前端表单如果在当前节点无可编辑的字段时 variables 一定会为空
        // 场景一：A 节点发起，B 节点表单无可编辑字段，审批通过时，C 节点需要流程变量获取下一个执行节点，但因为 B 节点无可编辑的字段，variables 为空，流程可能出现问题。
        // 场景二：A 节点发起，B 节点只有某一个字段可编辑（比如 day），但 C 节点需要多个节点。
        //       （比如 work + day 变量，在发起时填写，因为 B 节点只有 day 的编辑权限，在审批后，variables 会缺少 work 的值）
        Map<String, Object> processVariables = new HashMap<>();
        if (CollUtil.isNotEmpty(instance.getProcessVariables())) { // 获取历史中流程变量
            processVariables.putAll(instance.getProcessVariables());
        }
        if (CollUtil.isNotEmpty(reqVO.getVariables())) { // 合并前端传递的流程变量，以前端为准
            processVariables.putAll(reqVO.getVariables());
        }

        // 4. 校验并处理 APPROVE_USER_SELECT 当前审批人，选择下一节点审批人的逻辑
        Map<String, Object> variables = validateAndSetNextAssignees(task.getTaskDefinitionKey(), processVariables,
                bpmnModel, reqVO.getNextAssignees(), instance);
        runtimeService.setVariables(task.getProcessInstanceId(), variables);

        // 5. 移除辅助预测的流程变量，这些变量在回退操作中设置
        // todo @jason：可以直接 + 拼接哈
        String simulateVariableName = StrUtil.concat(false,
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_NEED_SIMULATE_PREFIX, task.getTaskDefinitionKey());
        runtimeService.removeVariable(task.getProcessInstanceId(), simulateVariableName);

        // 6. 调用 BPM complete 去完成任务
        taskService.complete(task.getId(), variables, true);

        // 【加签专属】处理加签任务
        handleParentTaskIfSign(task.getParentTaskId());
    }

    /**
     * 校验选择的下一个节点的审批人，是否合法
     * <p>
     * 1. 是否有漏选：没有选择审批人
     * 2. 是否有多选：非下一个节点
     *
     * @param taskDefinitionKey 当前任务节点标识
     * @param variables         流程变量
     * @param bpmnModel         流程模型
     * @param nextAssignees     下一个节点审批人集合（参数）
     * @param processInstance   流程实例
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> validateAndSetNextAssignees(String taskDefinitionKey, Map<String, Object> variables, BpmnModel bpmnModel,
                                                            Map<String, List<Long>> nextAssignees, ProcessInstance processInstance) {
        // simple 设计器第一个节点默认为发起人节点，不校验是否存在审批人
        if (Objects.equals(taskDefinitionKey, START_USER_NODE_ID)) {
            return variables;
        }
        // 1. 获取下一个将要执行的节点集合
        FlowElement flowElement = bpmnModel.getFlowElement(taskDefinitionKey);
        List<FlowNode> nextFlowNodes = getNextFlowNodes(flowElement, bpmnModel, variables);

        // 2. 校验选择的下一个节点的审批人，是否合法
        for (FlowNode nextFlowNode : nextFlowNodes) {
            Integer candidateStrategy = parseCandidateStrategy(nextFlowNode);
            // 2.1 情况一：如果节点中的审批人策略为 发起人自选
            if (ObjUtil.equals(candidateStrategy, BpmTaskCandidateStrategyEnum.START_USER_SELECT.getStrategy())) {
                // 特殊：如果当前节点已经存在审批人，则不允许覆盖
                Map<String, List<Long>> startUserSelectAssignees = FlowableUtils.getStartUserSelectAssignees(processInstance.getProcessVariables());
                if (startUserSelectAssignees != null && CollUtil.isNotEmpty(startUserSelectAssignees.get(nextFlowNode.getId()))) {
                    continue;
                }
                // 如果节点存在，但未配置审批人
                List<Long> assignees = nextAssignees != null ? nextAssignees.get(nextFlowNode.getId()) : null;
                if (CollUtil.isEmpty(assignees)) {
                    throw exception(PROCESS_INSTANCE_START_USER_SELECT_ASSIGNEES_NOT_CONFIG, nextFlowNode.getName());
                }

                // 设置 PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES
                if (startUserSelectAssignees == null) {
                    startUserSelectAssignees = new HashMap<>();
                }
                startUserSelectAssignees.put(nextFlowNode.getId(), assignees);
                variables.put(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES, startUserSelectAssignees);
                continue;
            }

            // 2.2 情况二：如果节点中的审批人策略为 审批人，在审批时选择下一个节点的审批人，并且该节点的审批人为空
            if (ObjUtil.equals(candidateStrategy, BpmTaskCandidateStrategyEnum.APPROVE_USER_SELECT.getStrategy())) {
                // 如果节点存在，但未配置审批人
                Map<String, List<Long>> approveUserSelectAssignees = FlowableUtils.getApproveUserSelectAssignees(processInstance.getProcessVariables());
                List<Long> assignees = nextAssignees != null ? nextAssignees.get(nextFlowNode.getId()) : null;
                if (CollUtil.isEmpty(assignees)) {
                    throw exception(PROCESS_INSTANCE_APPROVE_USER_SELECT_ASSIGNEES_NOT_CONFIG, nextFlowNode.getName());
                }

                // 设置 PROCESS_INSTANCE_VARIABLE_APPROVE_USER_SELECT_ASSIGNEES
                if (approveUserSelectAssignees == null) {
                    approveUserSelectAssignees = new HashMap<>();
                }
                approveUserSelectAssignees.put(nextFlowNode.getId(), assignees);
                Map<String, List<Long>> existingApproveUserSelectAssignees = (Map<String, List<Long>>) variables.get(
                        BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_APPROVE_USER_SELECT_ASSIGNEES);
                if (CollUtil.isNotEmpty(existingApproveUserSelectAssignees)) {
                    approveUserSelectAssignees.putAll(existingApproveUserSelectAssignees);
                }
                variables.put(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_APPROVE_USER_SELECT_ASSIGNEES, approveUserSelectAssignees);
            }
        }
        return variables;
    }

    /**
     * 审批通过存在“后加签”的任务。
     * <p>
     * 注意：该任务不能马上完成，需要一个中间状态（APPROVING），并激活剩余所有子任务（PROCESS）为可审批处理
     * 如果马上完成，则会触发下一个任务，甚至如果没有下一个任务则流程实例就直接结束了！
     *
     * @param task  当前任务
     * @param reqVO 前端请求参数
     */
    private void approveAfterSignTask(Task task, BpmTaskApproveReqVO reqVO) {
        // 更新父 task 状态 + 原因
        updateTaskStatusAndReason(task.getId(), BpmTaskStatusEnum.APPROVING.getStatus(), reqVO.getReason());

        // 2. 激活子任务
        List<Task> childrenTaskList = getTaskListByParentTaskId(task.getId());
        for (Task childrenTask : childrenTaskList) {
            taskService.resolveTask(childrenTask.getId());
            // 更新子 task 状态
            updateTaskStatus(childrenTask.getId(), BpmTaskStatusEnum.RUNNING.getStatus());
        }
    }

    /**
     * 如果父任务是有前后【加签】的任务，如果它【加签】出来的子任务都被处理，需要处理父任务：
     * <p>
     * 1. 如果是【向前】加签，则需要重新激活父任务，让它可以被审批
     * 2. 如果是【向后】加签，则需要完成父任务，让它完成审批
     *
     * @param parentTaskId 父任务编号
     */
    private void handleParentTaskIfSign(String parentTaskId) {
        if (StrUtil.isBlank(parentTaskId)) {
            return;
        }
        // 1.1 判断是否还有子任务。如果没有，就不处理
        Long childrenTaskCount = getTaskCountByParentTaskId(parentTaskId);
        if (childrenTaskCount > 0) {
            return;
        }
        // 1.2 只处理加签的父任务
        Task parentTask = validateTaskExist(parentTaskId);
        String scopeType = parentTask.getScopeType();
        if (BpmTaskSignTypeEnum.of(scopeType) == null) {
            return;
        }

        // 2. 子任务已处理完成，清空 scopeType 字段，修改 parentTask 信息，方便后续可以继续向前后向后加签
        TaskEntityImpl parentTaskImpl = (TaskEntityImpl) parentTask;
        parentTaskImpl.setScopeType(null);
        taskService.saveTask(parentTaskImpl);

        // 3.1 情况一：处理向【向前】加签
        if (BpmTaskSignTypeEnum.BEFORE.getType().equals(scopeType)) {
            // 3.1.1 owner 重新赋值给父任务的 assignee，这样它就可以被审批
            taskService.resolveTask(parentTaskId);
            // 3.1.2 更新流程任务 status
            updateTaskStatus(parentTaskId, BpmTaskStatusEnum.RUNNING.getStatus());
            // 3.2 情况二：处理向【向后】加签
        } else if (BpmTaskSignTypeEnum.AFTER.getType().equals(scopeType)) {
            // 只有 parentTask 处于 APPROVING 的情况下，才可以继续 complete 完成
            // 否则，一个未审批的 parentTask 任务，在加签出来的任务都被减签的情况下，就直接完成审批，这样会存在问题
            Integer status = (Integer) parentTask.getTaskLocalVariables().get(BpmnVariableConstants.TASK_VARIABLE_STATUS);
            if (ObjectUtil.notEqual(status, BpmTaskStatusEnum.APPROVING.getStatus())) {
                return;
            }
            // 3.2.2 完成自己（因为它已经没有子任务，所以也可以完成）
            updateTaskStatus(parentTaskId, BpmTaskStatusEnum.APPROVE.getStatus());
            taskService.complete(parentTaskId);
        }

        // 4. 递归处理父任务
        handleParentTaskIfSign(parentTask.getParentTaskId());
    }

    /**
     * 审批被委派的任务
     *
     * @param reqVO 前端请求参数，包含当前任务ID，审批意见等
     * @param task  当前被审批的任务
     */
    private void approveDelegateTask(BpmTaskApproveReqVO reqVO, Task task) {
        // 1. 添加审批意见
        AdminUserRespDTO currentUser = adminUserApi.getUser(WebFrameworkUtils.getLoginUserId()).getCheckedData();
        AdminUserRespDTO ownerUser = adminUserApi.getUser(NumberUtils.parseLong(task.getOwner())).getCheckedData(); // 发起委托的用户
        Assert.notNull(ownerUser, "委派任务找不到原审批人，需要检查数据");
        taskService.addComment(reqVO.getId(), task.getProcessInstanceId(), BpmCommentTypeEnum.DELEGATE_END.getType(),
                BpmCommentTypeEnum.DELEGATE_END.formatComment(currentUser.getNickname(), ownerUser.getNickname(), reqVO.getReason()));

        // 2.1 调用 resolveTask 完成任务。
        // 底层调用 TaskHelper.changeTaskAssignee(task, task.getOwner())：将 owner 设置为 assignee
        taskService.resolveTask(task.getId());
        // 2.2 更新 task 状态 + 原因
        updateTaskStatusAndReason(task.getId(), BpmTaskStatusEnum.RUNNING.getStatus(), reqVO.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectTask(Long userId, @Valid BpmTaskRejectReqVO reqVO) {
        // 1.1 校验任务存在
        Task task = validateTask(userId, reqVO.getId());
        // 1.2 校验流程实例存在
        ProcessInstance instance = processInstanceService.getProcessInstance(task.getProcessInstanceId());
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }

        // 2.1 更新流程任务为不通过
        updateTaskStatusAndReason(task.getId(), BpmTaskStatusEnum.REJECT.getStatus(), reqVO.getReason());
        // 2.2 添加流程评论
        taskService.addComment(task.getId(), task.getProcessInstanceId(), BpmCommentTypeEnum.REJECT.getType(),
                BpmCommentTypeEnum.REJECT.formatComment(reqVO.getReason()));
        // 2.3 如果当前任务时被加签的，则加它的根任务也标记成未通过
        // 疑问：为什么要标记未通过呢？
        // 回答：例如说 A 任务被向前加签除 B 任务时，B 任务被审批不通过，此时 A 会被取消。而 yudao-ui-admin-vue3 不展示“已取消”的任务，导致展示不出审批不通过的细节。
        if (task.getParentTaskId() != null) {
            String rootParentId = getTaskRootParentId(task);
            updateTaskStatusAndReason(rootParentId, BpmTaskStatusEnum.REJECT.getStatus(),
                    BpmCommentTypeEnum.REJECT.formatComment("加签任务不通过"));
            taskService.addComment(rootParentId, task.getProcessInstanceId(), BpmCommentTypeEnum.REJECT.getType(),
                    BpmCommentTypeEnum.REJECT.formatComment("加签任务不通过"));
        }

        // 3. 根据不同的 RejectHandler 处理策略
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(task.getProcessDefinitionId());
        FlowElement userTaskElement = BpmnModelUtils.getFlowElementById(bpmnModel, task.getTaskDefinitionKey());
        // 3.1 情况一：驳回到指定的任务节点
        BpmUserTaskRejectHandlerTypeEnum userTaskRejectHandlerType = BpmnModelUtils.parseRejectHandlerType(userTaskElement);
        if (userTaskRejectHandlerType == BpmUserTaskRejectHandlerTypeEnum.RETURN_USER_TASK) {
            String returnTaskId = BpmnModelUtils.parseReturnTaskId(userTaskElement);
            Assert.notNull(returnTaskId, "退回的节点不能为空");
            returnTask(userId, new BpmTaskReturnReqVO().setId(task.getId())
                    .setTargetTaskDefinitionKey(returnTaskId).setReason(reqVO.getReason()));
            return;
        }

        // 3.2 情况二： 标记流程为不通过并结束流程
        processInstanceService.updateProcessInstanceReject(instance, reqVO.getReason()); // 标记不通过
        moveTaskToEnd(task.getProcessInstanceId(), BpmCommentTypeEnum.REJECT.formatComment(reqVO.getReason())); // 结束流程
    }

    /**
     * 更新流程任务的 status 状态
     *
     * @param id     任务编号
     * @param status 状态
     */
    private void updateTaskStatus(String id, Integer status) {
        taskService.setVariableLocal(id, BpmnVariableConstants.TASK_VARIABLE_STATUS, status);
    }

    /**
     * 更新流程任务的 status 状态、reason 理由
     *
     * @param id     任务编号
     * @param status 状态
     * @param reason 理由（审批通过、审批不通过的理由）
     */
    private void updateTaskStatusAndReason(String id, Integer status, String reason) {
        updateTaskStatus(id, status);
        taskService.setVariableLocal(id, BpmnVariableConstants.TASK_VARIABLE_REASON, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnTask(Long userId, BpmTaskReturnReqVO reqVO) {
        // 1.1 当前任务 task
        Task task = validateTask(userId, reqVO.getId());
        if (task.isSuspended()) {
            throw exception(TASK_IS_PENDING);
        }
        // 1.2 获取流程模型信息
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(task.getProcessDefinitionId());
        // 1.3 校验源头和目标节点的关系，并返回目标元素
        FlowElement targetElement = validateTargetTaskCanReturn(bpmnModel, task.getTaskDefinitionKey(),
                reqVO.getTargetTaskDefinitionKey());

        // 2. 调用 Flowable 框架的退回逻辑
        returnTask(userId, bpmnModel, task, targetElement, reqVO);
    }

    /**
     * 退回流程节点时，校验目标任务节点是否可退回
     *
     * @param bpmnModel 流程模型
     * @param sourceKey 当前任务节点 Key
     * @param targetKey 目标任务节点 key
     * @return 目标任务节点元素
     */
    private FlowElement validateTargetTaskCanReturn(BpmnModel bpmnModel, String sourceKey, String targetKey) {
        // 1.1 获取当前任务节点元素
        FlowElement source = BpmnModelUtils.getFlowElementById(bpmnModel, sourceKey);
        // 1.2 获取跳转的节点元素
        FlowElement target = BpmnModelUtils.getFlowElementById(bpmnModel, targetKey);
        if (target == null) {
            throw exception(TASK_TARGET_NODE_NOT_EXISTS);
        }

        // 2. 只有串行可到达的节点，才可以退回。类似非串行、子流程无法退回
        if (!BpmnModelUtils.isSequentialReachable(source, target, null)) {
            throw exception(TASK_RETURN_FAIL_SOURCE_TARGET_ERROR);
        }
        return target;
    }

    /**
     * 执行退回逻辑
     *
     * @param userId        用户编号
     * @param bpmnModel     流程模型
     * @param currentTask   当前退回的任务
     * @param targetElement 需要退回到的目标任务
     * @param reqVO         前端参数封装
     */
    public void returnTask(Long userId, BpmnModel bpmnModel, Task currentTask, FlowElement targetElement, BpmTaskReturnReqVO reqVO) {
        // 1. 获得所有需要回撤的任务 taskDefinitionKey，用于稍后的 moveActivityIdsToSingleActivityId 回撤
        // 1.1 获取所有正常进行的任务节点 Key
        List<Task> taskList = taskService.createTaskQuery().processInstanceId(currentTask.getProcessInstanceId()).list();
        List<String> runTaskKeyList = convertList(taskList, Task::getTaskDefinitionKey);
        // 1.2 通过 targetElement 的出口连线，计算在 runTaskKeyList 有哪些 key 需要被撤回
        // 为什么不直接使用 runTaskKeyList 呢？因为可能存在多个审批分支，例如说：A -> B -> C 和 D -> F，而只要 C 撤回到 A，需要排除掉 F
        List<UserTask> returnUserTaskList = BpmnModelUtils.iteratorFindChildUserTasks(targetElement, runTaskKeyList, null, null);
        List<String> returnTaskKeyList = convertList(returnUserTaskList, UserTask::getId);

        List<String> runExecutionIds = new ArrayList<>();
        // 2. 给当前要被退回的 task 数组，设置退回意见
        taskList.forEach(task -> {
            // 需要排除掉，不需要设置退回意见的任务
            if (!returnTaskKeyList.contains(task.getTaskDefinitionKey())) {
                return;
            }
            if (task.getExecutionId() != null) {
                runExecutionIds.add(task.getExecutionId());
            }

            // 判断是否分配给自己任务，因为会签任务，一个节点会有多个任务
            if (isAssignUserTask(userId, task)) { // 情况一：自己的任务，进行 RETURN 标记
                // 2.1.1 添加评论
                taskService.addComment(task.getId(), currentTask.getProcessInstanceId(), BpmCommentTypeEnum.RETURN.getType(),
                        BpmCommentTypeEnum.RETURN.formatComment(reqVO.getReason()));
                // 2.1.2 更新 task 状态 + 原因
                updateTaskStatusAndReason(task.getId(), BpmTaskStatusEnum.RETURN.getStatus(), reqVO.getReason());
            } else { // 情况二：别人的任务，进行 CANCEL 标记
                processTaskCanceled(task.getId());
            }
        });

        // 3. 构建需要预测的任务流程变量
        // TODO @jason：【驳回预测相关】是不是搞成一个变量，里面是 set 更简洁一点呀？
        Set<String> needSimulateTaskDefinitionKeys = getNeedSimulateTaskDefinitionKeys(bpmnModel, currentTask, targetElement);
        Map<String, Object> needSimulateVariables = convertMap(needSimulateTaskDefinitionKeys,
                key -> StrUtil.concat(false, BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_NEED_SIMULATE_PREFIX, key), item -> Boolean.TRUE);


        // 4. 执行驳回
        // 使用 moveExecutionsToSingleActivityId 替换 moveActivityIdsToSingleActivityId 原因：
        // 当多实例任务回退的时候有问题。相关 issue: https://github.com/flowable/flowable-engine/issues/3944
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(currentTask.getProcessInstanceId())
                .moveExecutionsToSingleActivityId(runExecutionIds, reqVO.getTargetTaskDefinitionKey())
                // 设置需要预测的任务流程变量，用于辅助预测
                .processVariables(needSimulateVariables)
                 // 设置流程变量（local）节点退回标记, 用于退回到节点，不执行 BpmUserTaskAssignStartUserHandlerTypeEnum 策略，导致自动通过
                .localVariable(reqVO.getTargetTaskDefinitionKey(),
                        String.format(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_RETURN_FLAG, reqVO.getTargetTaskDefinitionKey()),
                        Boolean.TRUE)
                .changeState();
    }

    private Set<String> getNeedSimulateTaskDefinitionKeys(BpmnModel bpmnModel, Task currentTask, FlowElement targetElement) {
        // 1. 获取需要预测的任务的 definition key。因为当前任务还没完成，也需要预测
        Set<String> taskDefinitionKeys = CollUtil.newHashSet(currentTask.getTaskDefinitionKey());

        // 2.1 获取已结束任务按时间倒序排序
        List<HistoricTaskInstance> endTaskList = CollectionUtils.filterList(
                getTaskListByProcessInstanceId(currentTask.getProcessInstanceId(), Boolean.FALSE),
                item -> item.getEndTime() != null);
        // 2.2 从结束任务中找到最近一个的目标任务
        HistoricTaskInstance targetTask = findFirst(endTaskList,
                item -> item.getTaskDefinitionKey().equals(targetElement.getId()));
        if (targetTask == null) {
            return taskDefinitionKeys;
        }
        // 2.3 遍历已结束的任务，找到在 targetTask 之后生成的任务，且串行可达的任务
        endTaskList.forEach(item -> {
            FlowElement element = getFlowElementById(bpmnModel, item.getTaskDefinitionKey());
            // 如果已结束的任务在回退目标节点之后生成，且串行可达，则加到需要预测节点中
            // TODO 串行可达的方法需要和判断可回退节点 validateTargetTaskCanReturn 分开吗？ 并行网关可能会有问题。
            if (item.getCreateTime().compareTo(targetTask.getCreateTime()) > 0
                    && BpmnModelUtils.isSequentialReachable(element, targetElement, null)) {
                taskDefinitionKeys.add(item.getTaskDefinitionKey());
            }
        });
        return taskDefinitionKeys;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delegateTask(Long userId, BpmTaskDelegateReqVO reqVO) {
        String taskId = reqVO.getId();
        // 1.1 校验任务
        Task task = validateTask(userId, reqVO.getId());
        if (task.getAssignee().equals(reqVO.getDelegateUserId().toString())) { // 校验当前审批人和被委派人不是同一人
            throw exception(TASK_DELEGATE_FAIL_USER_REPEAT);
        }
        // 1.2 校验目标用户存在
        AdminUserRespDTO delegateUser = adminUserApi.getUser(reqVO.getDelegateUserId()).getCheckedData();
        if (delegateUser == null) {
            throw exception(TASK_DELEGATE_FAIL_USER_NOT_EXISTS);
        }

        // 2. 添加委托意见
        AdminUserRespDTO currentUser = adminUserApi.getUser(userId).getCheckedData();
        taskService.addComment(taskId, task.getProcessInstanceId(), BpmCommentTypeEnum.DELEGATE_START.getType(),
                BpmCommentTypeEnum.DELEGATE_START.formatComment(currentUser.getNickname(), delegateUser.getNickname(), reqVO.getReason()));

        // 3.1 设置任务所有人 (owner) 为原任务的处理人 (assignee)
        // 特殊：如果已经被委派（owner 非空），则不需要更新 owner：https://gitee.com/zhijiantianya/yudao-cloud/issues/ICJ153
        if (StrUtil.isEmpty(task.getOwner())) {
            taskService.setOwner(taskId, task.getAssignee());
        }
        // 3.2 执行委派，将任务委派给 delegateUser
        taskService.delegateTask(taskId, reqVO.getDelegateUserId().toString());
        // 补充说明：委托不单独设置状态。如果需要，可通过 Task 的 DelegationState 字段，判断是否为 DelegationState.PENDING 委托中
    }
    @Override
    public void transferTask(Long userId, BpmTaskTransferReqVO reqVO) {
        String taskId = reqVO.getId();

        // 1.1 校验当前任务是否存在，并且当前用户是否有权限操作该任务
        Task task = validateTask(userId, reqVO.getId());

        // 判断当前任务的审批人（assignee）是否和要转派的人是同一个，如果是则不允许转派（自己不能转给自己）
        if (task.getAssignee().equals(reqVO.getAssigneeUserId().toString())) {
            throw exception(TASK_TRANSFER_FAIL_USER_REPEAT);
        }

        // 1.2 校验目标用户（即要转派给谁）是否存在
        AdminUserRespDTO assigneeUser = adminUserApi.getUser(reqVO.getAssigneeUserId()).getCheckedData();
        if (assigneeUser == null) {
            throw exception(TASK_TRANSFER_FAIL_USER_NOT_EXISTS);
        }

        // 2. 添加一条“任务转派”的评论，记录是谁转给了谁，以及转派原因（用于流程历史追踪）
        AdminUserRespDTO currentUser = adminUserApi.getUser(userId).getCheckedData();
        taskService.addComment(
                taskId,
                task.getProcessInstanceId(),
                BpmCommentTypeEnum.TRANSFER.getType(),
                BpmCommentTypeEnum.TRANSFER.formatComment(
                        currentUser.getNickname(),      // 当前操作人昵称
                        assigneeUser.getNickname(),    // 被转派人昵称
                        reqVO.getReason()              // 转派原因
                )
        );

        // 3.1 设置任务的“所有人”（owner）为原来的审批人（assignee）
        // 注意：如果任务已经被转派过（即 owner 已经有值），就不再修改 owner，避免覆盖历史信息
        // 参考：https://gitee.com/zhijiantianya/yudao-cloud/issues/ICJ153
        if (StrUtil.isEmpty(task.getOwner())) {
            taskService.setOwner(taskId, task.getAssignee());
        }

        // 3.2 真正执行任务转派：将任务的当前审批人（assignee）更新为新指定的用户
        // 注意：这里用的是 setAssignee（转派），而不是 delegateTask（委托），两者语义不同！
        taskService.setAssignee(taskId, reqVO.getAssigneeUserId().toString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveTaskToEnd(String processInstanceId, String reason) {
        // 步骤0：获取当前流程实例中所有还在运行的任务（即尚未完成或取消的任务）
        List<Task> taskList = getRunningTaskListByProcessInstanceId(processInstanceId, null, null);

        // 如果没有正在运行的任务，说明流程已经结束，无需操作，直接返回
        if (CollUtil.isEmpty(taskList)) {
            return;
        }

        // 步骤1：逐个取消所有未完成的任务
        // 为什么需要手动取消？因为在“加签”等特殊场景下，任务可能由系统自动创建，
        // 如果不提前取消，直接跳转会引发状态不一致的问题
        taskList.forEach(task -> {
            // 从任务的本地变量中读取当前任务的状态（比如：待办、已取消、已完成等）
            Integer otherTaskStatus = (Integer) task.getTaskLocalVariables().get(BpmnVariableConstants.TASK_VARIABLE_STATUS);

            // 如果该任务已经是结束状态（例如已完成或已取消），就跳过，不再处理
            if (BpmTaskStatusEnum.isEndStatus(otherTaskStatus)) {
                return;
            }

            // 调用取消任务的方法，触发任务取消逻辑（比如记录日志、更新状态等）
            processTaskCanceled(task.getId());
        });

        // 步骤2：将所有正在运行的任务（对应流程中的活动节点）一次性跳转到流程的结束节点
        // 首先，根据任务获取对应的流程定义模型（BPMN 文件的内存表示）
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(taskList.get(0).getProcessDefinitionId());

        // 收集所有要跳转的“起点”节点ID（即当前活跃的任务节点）
        List<String> activityIds = CollUtil.newArrayList(convertSet(taskList, Task::getTaskDefinitionKey));

        // 从流程模型中找到唯一的“结束节点”（EndEvent）
        EndEvent endEvent = BpmnModelUtils.getEndEvent(bpmnModel);
        Assert.notNull(endEvent, "流程定义中必须包含一个结束节点，否则无法跳转！");

        // 执行跳转：把 activityIds 中的所有活动节点，直接移动到结束节点，从而快速结束流程
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(processInstanceId)
                .moveActivityIdsToSingleActivityId(activityIds, endEvent.getId())
                .changeState();

        // 步骤3：兜底处理——防止并行流程中出现“残留执行流”
        // 有时候，即使跳转到了结束节点，流程实例仍未完全结束（比如存在多个并行分支）
        // 这时需要强制删除整个流程实例，确保干净退出
        List<Execution> executions = runtimeService.createExecutionQuery().processInstanceId(processInstanceId).list();
        if (CollUtil.isNotEmpty(executions)) {
            // 记录警告日志，说明发生了异常情况
            log.warn("[moveTaskToEnd] 跳转到结束节点后，流程实例仍未结束，强制删除流程实例");
            // 强制删除流程实例，并附上删除原因（如“用户手动结束”）
            runtimeService.deleteProcessInstance(processInstanceId, reason);
        }
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createSignTask(Long userId, BpmTaskSignCreateReqVO reqVO) {
        // 1. 获取并校验原始任务是否允许执行“加签”操作（比如当前用户是否有权限）
        TaskEntityImpl taskEntity = validateTaskCanCreateSign(userId, reqVO);

        // 获取要加签的用户列表，并校验这些用户是否存在
        List<AdminUserRespDTO> userList = adminUserApi.getUserList(reqVO.getUserIds()).getCheckedData();
        if (CollUtil.isEmpty(userList)) {
            throw exception(TASK_SIGN_CREATE_USER_NOT_EXIST);
        }

        // 2. 修改原始任务状态，为加签做准备
        // 2.1 启用子任务计数功能（会在数据库 ACT_RU_TASK 表中记录子任务数量，便于后续处理）
        taskEntity.setCountEnabled(true);

        // 2.2 如果是“向前加签”（即新加的人先审批，原审批人等他们完成后才能继续）
        if (reqVO.getType().equals(BpmTaskSignTypeEnum.BEFORE.getType())) {
            // 把原审批人设为“任务所有人”（owner），表示他暂时不处理，等子任务完成
            taskEntity.setOwner(taskEntity.getAssignee());
            // 清空当前审批人（assignee），表示任务暂停，等待子任务完成
            taskEntity.setAssignee(null);
        }

        // 2.4 记录加签类型（向前 or 向后），后续完成任务时需要根据类型做不同处理
        taskEntity.setScopeType(reqVO.getType());

        // 2.5 保存对原始任务的修改
        taskService.saveTask(taskEntity);

        // 2.6 如果是向前加签，把原始任务状态设为“等待”（WAIT），表示暂停
        if (reqVO.getType().equals(BpmTaskSignTypeEnum.BEFORE.getType())) {
            updateTaskStatus(taskEntity.getId(), BpmTaskStatusEnum.WAIT.getStatus());
        }

        // 3. 为每个加签用户创建新的子任务
        createSignTaskList(convertList(reqVO.getUserIds(), String::valueOf), taskEntity);

        // 4. 添加一条“加签”评论，记录操作人、加签类型、加签人员和原因（用于审计和查看）
        AdminUserRespDTO currentUser = adminUserApi.getUser(userId).getCheckedData();
        String comment = StrUtil.format(
                BpmCommentTypeEnum.ADD_SIGN.getComment(),
                currentUser.getNickname(),                             // 操作人昵称
                BpmTaskSignTypeEnum.nameOfType(reqVO.getType()),       // 加签类型：向前/向后
                String.join(",", convertList(userList, AdminUserRespDTO::getNickname)), // 加签人昵称列表
                reqVO.getReason()                                      // 加签原因
        );
        taskService.addComment(
                reqVO.getId(),
                taskEntity.getProcessInstanceId(),
                BpmCommentTypeEnum.ADD_SIGN.getType(),
                comment
        );
    }

    /**
     * 校验当前任务是否允许创建加签（即添加额外审批人）。
     * 加签有两种类型：向前加签（让某人提前审批）和向后加签（让某人后续审批）。
     * 校验规则如下：
     * <p>
     * 1. 同一个任务不能同时存在“向前加签”和“向后加签”——一旦已有某种类型的加签，新的加签必须是同一类型。
     * 2. 对于同一个流程节点（即相同 taskDefinitionKey），不能重复添加已经参与审批（或作为加签人/所有者）的用户。
     *
     * @param userId 当前操作用户的 ID（通常是发起加签的人）
     * @param reqVO  包含加签请求信息的对象，包括：要加签的原始任务 ID 和加签类型（向前或向后）
     * @return 校验通过后的原始任务实体（TaskEntityImpl 类型）
     */
    private TaskEntityImpl validateTaskCanCreateSign(Long userId, BpmTaskSignCreateReqVO reqVO) {
        // 第一步：校验当前用户是否有权限操作该任务（复用已有校验逻辑），并获取任务详情
        TaskEntityImpl taskEntity = (TaskEntityImpl) validateTask(userId, reqVO.getId());

        // 第二步：检查加签类型是否冲突
        // 如果该任务之前已经被加签过（scopeType 不为 null），那么新请求的加签类型必须和原来一致
        if (taskEntity.getScopeType() != null
                && ObjectUtil.notEqual(taskEntity.getScopeType(), reqVO.getType())) {
            // 类型不一致，抛出错误：比如原来是“向前加签”，现在又试图“向后加签”
            throw exception(TASK_SIGN_CREATE_TYPE_ERROR,
                    BpmTaskSignTypeEnum.nameOfType(taskEntity.getScopeType()),
                    BpmTaskSignTypeEnum.nameOfType(reqVO.getType()));
        }

        // 第三步：防止重复添加审批人
        // 查询当前流程实例中，所有具有相同任务定义 key（即同一审批节点）的任务
        List<Task> taskList = taskService.createTaskQuery()
                .processInstanceId(taskEntity.getProcessInstanceId())
                .taskDefinitionKey(taskEntity.getTaskDefinitionKey())
                .list();

        // 收集这些任务中已有的审批人（assignee）和所有者（owner）的用户 ID
        // 注意：向后加签的任务可能还没有 assignee（审批人），但会设置 owner（所有者），所以两者都要检查
        List<Long> currentAssigneeList = convertListByFlatMap(taskList, task ->
                Stream.of(
                        NumberUtils.parseLong(task.getAssignee()), // 当前审批人
                        NumberUtils.parseLong(task.getOwner())     // 加签任务的所有者（可能用于向后加签）
                )
        );

        // 检查请求中要加签的用户是否已经在上述列表中（即是否重复）
        if (CollUtil.containsAny(currentAssigneeList, reqVO.getUserIds())) {
            // 如果有重复，获取这些重复用户的昵称，用于提示错误信息
            List<AdminUserRespDTO> userList = adminUserApi.getUserList(
                    CollUtil.intersection(currentAssigneeList, reqVO.getUserIds())
            ).getCheckedData();
            // 抛出“用户已存在”异常，并列出重复用户的昵称
            throw exception(TASK_SIGN_CREATE_USER_REPEAT,
                    String.join(",", convertList(userList, AdminUserRespDTO::getNickname)));
        }

        // 所有校验通过，返回任务实体
        return taskEntity;
    }

    /**
     * 创建加签子任务
     *
     * @param userIds    被加签的用户 ID
     * @param taskEntity 被加签的任务
     */
    private void createSignTaskList(List<String> userIds, TaskEntityImpl taskEntity) {
        if (CollUtil.isEmpty(userIds)) {
            return;
        }
        // 创建加签人的新任务，全部基于 taskEntity 为父任务来创建
        for (String addSignId : userIds) {
            if (StrUtil.isBlank(addSignId)) {
                continue;
            }
            createSignTask(taskEntity, addSignId);
        }
    }

    /**
     * 创建一个“加签”子任务（即在原有审批流程中临时插入的新审批人任务）。
     * <p>
     * 加签分为两种类型：
     * - 向前加签（BEFORE）：新审批人必须先审批，原任务才能继续。
     * - 向后加签（AFTER）：原任务审批完成后，再由新加的人审批。
     * <p>
     * 本方法会根据父任务的加签类型，正确设置子任务的执行人（assignee）或所有者（owner），
     * 并控制子任务的初始状态。
     *
     * @param parentTask 原始的父任务（即被加签的任务）
     * @param assignee   要加签的用户 ID（即新审批人的 ID，字符串格式）
     */
    private void createSignTask(TaskEntityImpl parentTask, String assignee) {
        // 第一步：创建一个新的子任务（作为加签任务）
        // 使用 UUID 生成唯一任务 ID
        TaskEntityImpl task = (TaskEntityImpl) taskService.newTask(IdUtil.fastSimpleUUID());
        // 复制父任务的大部分属性（如流程实例 ID、任务定义 key、业务 key 等）到子任务
        BpmTaskConvert.INSTANCE.copyTo(parentTask, task);

        // 第二步：根据加签类型，决定如何分配这个子任务
        if (BpmTaskSignTypeEnum.BEFORE.getType().equals(parentTask.getScopeType())) {
            // 情况1：向前加签
            // 子任务立即分配给指定用户，该用户需要先审批
            task.setAssignee(assignee); // 设置执行人（审批人）
        } else {
            // 情况2：向后加签
            // 子任务暂时不分配给任何人执行（不设 assignee），
            // 而是把 assignee 设为 owner（所有者），表示“将来要由这个人处理”
            // 这样可以避免和父任务同时被处理
            task.setOwner(assignee); // 设置所有者，等父任务完成后才激活
        }

        // 第三步：将子任务保存到数据库
        taskService.saveTask(task);

        // 第四步：如果是向后加签，需要将子任务状态设为“等待中”（WAIT）
        // 因为此时父任务还没完成，子任务不能被处理
        if (BpmTaskSignTypeEnum.AFTER.getType().equals(parentTask.getScopeType())) {
            // 更新子任务状态为 WAIT，防止用户提前操作
            updateTaskStatus(task.getId(), BpmTaskStatusEnum.WAIT.getStatus());
        }

        // 注意：向前加签的子任务默认是“待处理”状态，可立即审批，无需额外设置状态
    }

    /**
     * 删除一个加签任务（即“减签”操作）。
     * <p>
     * 减签是指将之前通过“加签”添加的额外审批人任务移除。
     * 该操作会：
     * - 校验任务是否可以被删除（必须是加签产生的子任务）
     * - 找到要删除的审批人（可能是 assignee 或 owner）
     * - 同时删除该任务及其所有下级子任务（比如加签任务又被加签的情况）
     * - 将这些任务标记为“已取消”
     * - 在父任务上记录操作日志
     * - 触发父任务的后续处理（比如继续审批流程）
     *
     * @param userId 当前执行减签操作的用户 ID
     * @param reqVO  请求参数，包含要删除的加签任务 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // 整个操作要么全部成功，要么全部回滚，保证数据一致性
    @SuppressWarnings("DataFlowIssue")
    public void deleteSignTask(Long userId, BpmTaskSignDeleteReqVO reqVO) {
        // 第一步：校验这个任务是否允许被“减签”
        // validateTaskCanSignDelete 会检查该任务是否是加签产生的、状态是否允许删除等
        Task task = validateTaskCanSignDelete(reqVO.getId());

        // 第二步：确定这个被删除的任务原本是分配给谁的（即“被减签的人”）
        AdminUserRespDTO cancelUser = null;

        // 优先看 assignee（实际审批人），例如向前加签的任务会有 assignee
        if (StrUtil.isNotBlank(task.getAssignee())) {
            cancelUser = adminUserApi.getUser(NumberUtils.parseLong(task.getAssignee())).getCheckedData();
        }

        // 如果没有 assignee，再看 owner（所有者），例如向后加签的任务可能只有 owner
        if (cancelUser == null && StrUtil.isNotBlank(task.getOwner())) {
            cancelUser = adminUserApi.getUser(NumberUtils.parseLong(task.getOwner())).getCheckedData();
        }

        // 如果连 owner 和 assignee 都没有，说明数据异常，直接报错
        Assert.notNull(cancelUser, "任务中没有所有者和审批人，数据错误");

        // 第三步：获取这个任务及其所有“后代子任务”（比如：A 加签了 B，B 又加签了 C，那么删除 A 时也要删 B 和 C）
        List<Task> childTaskList = getAllChildTaskList(task);
        childTaskList.add(task); // 把当前任务自己也加入列表

        // 第四步：将所有这些任务的状态统一改为“已取消”，并记录取消原因
        String cancelReason = StrUtil.format("任务被取消，原因：由于[{}]操作[减签]，", cancelUser.getNickname());
        childTaskList.forEach(childTask ->
                updateTaskStatusAndReason(
                        childTask.getId(),
                        BpmTaskStatusEnum.CANCEL.getStatus(), // 状态设为“已取消”
                        cancelReason
                )
        );

        // 第五步：从流程引擎中彻底删除这些任务（数据库中移除）
        taskService.deleteTasks(convertList(childTaskList, Task::getId));

        // 第六步：在父任务上添加一条操作日志（说明谁对谁执行了减签）
        // 注意：必须在 handleParentTask 之前记录！
        // 因为 handleParentTask 可能会完成或删除父任务，导致后续无法添加评论（会报错）
        AdminUserRespDTO user = adminUserApi.getUser(userId).getCheckedData(); // 当前操作人
        taskService.addComment(
                task.getParentTaskId(),           // 父任务 ID
                task.getProcessInstanceId(),      // 所属流程实例
                BpmCommentTypeEnum.SUB_SIGN.getType(), // 评论类型：减签
                StrUtil.format(BpmCommentTypeEnum.SUB_SIGN.getComment(), user.getNickname(), cancelUser.getNickname())
        );

        // 第七步：检查并处理父任务（比如：如果父任务的所有加签都处理完了，可能需要继续推进流程）
        handleParentTaskIfSign(task.getParentTaskId());
    }

    @Override
    public void copyTask(Long userId, BpmTaskCopyReqVO reqVO) {
        processInstanceCopyService.createProcessInstanceCopy(reqVO.getCopyUserIds(), reqVO.getReason(), reqVO.getId());
    }
    /**
     * 撤回一个已提交的审批任务。
     * <p>
     * 用户在完成某个审批后，如果发现填错了或想修改，可以“撤回”该任务。
     * 撤回意味着：
     * - 取消后续已经生成但还未完成的审批任务
     * - 把流程“跳回”到用户刚才审批的那个节点，让用户重新处理
     * <p>
     * 注意：撤回有严格限制，比如：
     * - 只能撤回自己审批过的任务
     * - 后续节点必须还没被其他人审批（不能“时光倒流”）
     * - 流程定义必须明确允许撤回
     *
     * @param userId   当前用户 ID（发起撤回的人）
     * @param taskId   要撤回的历史任务 ID（即用户之前完成的那次审批）
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // 整个操作必须原子执行：成功就全成功，失败就全部回滚
    public void withdrawTask(Long userId, String taskId) {

        // =============== 第一步：校验是否可以撤回 ===============

        // 1.1 查询“当前用户”是否真的完成过这个任务（必须是本人操作过的已完成任务）
        HistoricTaskInstance taskInstance = historyService.createHistoricTaskInstanceQuery()
                .taskId(taskId)                // 指定任务 ID
                .taskAssignee(userId.toString()) // 必须是当前用户审批的
                .finished()                    // 必须是已完成的任务（已办事项）
                .singleResult();               // 只允许一个结果

        if (ObjUtil.isNull(taskInstance)) {
            throw exception(TASK_WITHDRAW_FAIL_TASK_NOT_EXISTS); // 任务不存在或不是你办的
        }

        // 1.2 检查整个流程是否还在运行中（如果流程已经结束，就不能撤回了）
        ProcessInstance processInstance = processInstanceService.getProcessInstance(taskInstance.getProcessInstanceId());
        if (ObjUtil.isNull(processInstance)) {
            throw exception(TASK_WITHDRAW_FAIL_PROCESS_NOT_RUNNING); // 流程已结束
        }

        // 1.3 检查这个流程是否允许“撤回”功能（需要在流程设计时开启）
        BpmProcessDefinitionInfoDO processDefinitionInfo = bpmProcessDefinitionService.getProcessDefinitionInfo(
                processInstance.getProcessDefinitionId());
        if (ObjUtil.isNull(processDefinitionInfo) || !Boolean.TRUE.equals(processDefinitionInfo.getAllowWithdrawTask())) {
            throw exception(TASK_WITHDRAW_FAIL_NOT_ALLOW); // 流程不允许撤回
        }

        // 1.4 找出“当前任务节点”的下一个审批节点有哪些
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(taskInstance.getProcessDefinitionId());
        // 从 BPMN 模型中找到用户刚才审批的那个节点（UserTask）
        UserTask userTask = (UserTask) BpmnModelUtils.getFlowElementById(bpmnModel, taskInstance.getTaskDefinitionKey());
        // 获取该节点后面连接的所有“用户任务”（即下一个或多个审批人节点）
        List<String> nextUserTaskKeys = convertList(BpmnModelUtils.getNextUserTasks(userTask), UserTask::getId);

        if (CollUtil.isEmpty(nextUserTaskKeys)) {
            throw exception(TASK_WITHDRAW_FAIL_NEXT_TASK_NOT_ALLOW); // 没有下一个节点，无法撤回（比如已经是最后一个节点）
        }

        // 1.5 检查“下一个节点”的任务是否已经被别人完成了
        // 只有在“当前任务完成之后”创建的后续任务才算（用 taskCreatedAfter 过滤）
        long nextUserTaskFinishedCount = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstance.getProcessInstanceId())
                .taskDefinitionKeys(nextUserTaskKeys)
                .taskCreatedAfter(taskInstance.getEndTime()) // 只查当前任务完成后产生的后续任务
                .finished() // 已完成的
                .count();

        if (nextUserTaskFinishedCount > 0) {
            // 如果有后续任务已经审批完成，就不能撤回（否则会破坏流程一致性）
            throw exception(TASK_WITHDRAW_FAIL_NEXT_TASK_NOT_ALLOW);
        }

        // 1.6 查询当前正在运行的“下一个节点”任务（即已经生成但还没审批的任务）
        List<Task> runningTasks = taskService.createTaskQuery()
                .processInstanceId(processInstance.getProcessInstanceId())
                .taskDefinitionKeys(nextUserTaskKeys)
                .active() // 只查活跃（未完成）的任务
                .list();

        if (CollUtil.isEmpty(runningTasks)) {
            // 如果连运行中的任务都没有，说明流程可能卡住了，也无法撤回
            throw exception(TASK_WITHDRAW_FAIL_NEXT_TASK_NOT_ALLOW);
        }

        // =============== 第二步：执行撤回操作 ===============

        // 2.1 先把所有“下一个节点”的任务标记为“已取消”，并添加撤回日志
        List<String> withdrawExecutionIds = new ArrayList<>();
        for (Task task : runningTasks) {
            // 添加评论：说明是因为前一节点撤回而取消
            taskService.addComment(
                    task.getId(),
                    taskInstance.getProcessInstanceId(),
                    BpmCommentTypeEnum.CANCEL.getType(),
                    BpmCommentTypeEnum.CANCEL.formatComment("前一节点撤回")
            );
            // 更新任务状态为“已取消”
            updateTaskStatusAndReason(
                    task.getId(),
                    BpmTaskStatusEnum.CANCEL.getStatus(),
                    BpmReasonEnum.CANCEL_BY_WITHDRAW.getReason()
            );
            // 记录这些任务对应的“执行流 ID”（executionId），用于下一步跳转
            withdrawExecutionIds.add(task.getExecutionId());
        }

        // 2.2 使用 Flowable 的“改变流程状态”功能，将流程跳回到用户刚才审批的节点
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(processInstance.getProcessInstanceId()) // 指定流程实例
                .moveExecutionsToSingleActivityId( // 把多个执行流（可能有并行分支）都跳回到同一个节点
                        withdrawExecutionIds,
                        taskInstance.getTaskDefinitionKey() // 目标节点：用户之前审批的节点
                )
                .changeState(); // 执行跳转

        // 撤回成功后，用户会再次看到自己之前的审批任务，可以重新提交
    }

    /**
     * 校验任务是否能被减签
     *
     * @param id 任务编号
     * @return 任务信息
     */
    private Task validateTaskCanSignDelete(String id) {
        Task task = validateTaskExist(id);
        if (task.getParentTaskId() == null) {
            throw exception(TASK_SIGN_DELETE_NO_PARENT);
        }
        Task parentTask = getTask(task.getParentTaskId());
        if (parentTask == null) {
            throw exception(TASK_SIGN_DELETE_NO_PARENT);
        }
        if (BpmTaskSignTypeEnum.of(parentTask.getScopeType()) == null) {
            throw exception(TASK_SIGN_DELETE_NO_PARENT);
        }
        return task;
    }

    // ========== Event 事件相关方法 ==========

    /**
     * 当一个新审批任务被流程引擎创建时，自动触发此方法进行初始化处理。
     * <p>
     * 主要工作包括：
     * 1. 设置任务状态为“待办中”（RUNNING）
     * 2. 检查流程配置，看是否需要在任务创建时调用外部 HTTP 接口（如通知系统）
     * 3. 处理“自动审批”逻辑：比如配置为“无人审批时自动通过”，或任务本身就是“自动通过”
     * <p>
     * ⚠️ 注意：自动审批不能在当前事务中立即执行（会导致流程引擎异常），
     *        所以要注册一个“事务完成后的回调”，等数据库提交成功后再处理。
     *
     * @param task 刚刚被创建的新任务对象
     */
    @Override
    public void processTaskCreated(Task task) {

        // =============== 第一步：设置任务状态 ===============
        // 每个任务都有一个自定义状态字段（存在 local variables 中），用作业务状态（如待办、已办、已取消等）
        Integer status = (Integer) task.getTaskLocalVariables().get(BpmnVariableConstants.TASK_VARIABLE_STATUS);

        // 防止重复处理：如果任务已经有状态了，说明可能被多次触发，直接跳过
        if (status != null) {
            log.error("[processTaskCreated][任务 {} 已经有状态 {}，跳过处理]", task.getId(), status);
            return;
        }

        // 设置任务状态为“待办中”（RUNNING）
        updateTaskStatus(task.getId(), BpmTaskStatusEnum.RUNNING.getStatus());

        // =============== 第二步：获取流程实例 ===============
        ProcessInstance processInstance = processInstanceService.getProcessInstance(task.getProcessInstanceId());
        if (processInstance == null) {
            log.error("[processTaskCreated][找不到任务 {} 所属的流程实例]", task.getId());
            return;
        }

        // =============== 第三步：获取流程的扩展配置 ===============
        // 比如：是否开启任务创建前通知？是否允许自动审批？等
        BpmProcessDefinitionInfoDO processDefinitionInfo = bpmProcessDefinitionService
                .getProcessDefinitionInfo(processInstance.getProcessDefinitionId());
        if (processDefinitionInfo == null) {
            log.error("[processTaskCreated][找不到流程定义 {} 的扩展信息]", processInstance.getProcessDefinitionId());
            return;
        }

        // =============== 第四步：如果配置了“任务创建前触发 HTTP 请求”，就执行它 ===============
        // 例如：通知企业微信/钉钉，或调用第三方系统接口
        if (ObjUtil.isNotNull(processDefinitionInfo.getTaskBeforeTriggerSetting())) {
            BpmModelMetaInfoVO.HttpRequestSetting setting = processDefinitionInfo.getTaskBeforeTriggerSetting();
            BpmHttpRequestUtils.executeBpmHttpRequest(
                    processInstance,
                    setting.getUrl(),     // 目标地址
                    setting.getHeader(),  // 请求头（如 token）
                    setting.getBody(),    // 请求体（可含流程变量）
                    true,                 // 异步执行（不阻塞当前流程）
                    setting.getResponse() // 如何处理返回结果（比如只记录日志）
            );
        }

        // =============== 第五步：读取当前任务（UserTask）的审批配置 ===============
        // 从 BPMN 流程模型中找到这个任务节点（比如某个“部门经理审批”节点）
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(processInstance.getProcessDefinitionId());
        FlowElement userTaskElement = BpmnModelUtils.getFlowElementById(bpmnModel, task.getTaskDefinitionKey());

        // 解析两个关键配置：
        // 1. approveType：这个任务是“人工审批”还是“自动通过/自动拒绝”？
        Integer approveType = BpmnModelUtils.parseApproveType(userTaskElement);
        // 2. assignEmptyHandlerType：如果没人被分配（审批人为空），该怎么处理？
        Integer assignEmptyHandlerType = BpmnModelUtils.parseAssignEmptyHandlerType(userTaskElement);

        // =============== 第六步：注册“事务完成后的回调”来处理自动审批 ===============
        // 为什么不能现在就审批？因为当前数据库事务还没提交，直接操作流程会导致异常！
        // 所以要等事务成功提交后，再执行自动审批。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            /**
             * 在事务完成后调用（无论提交还是回滚）。
             * 说明：有些特殊场景（如第一个任务就是自动审批），Spring 的 afterCommit 不会被调用，
             * 但 afterCompletion 一定会被调用，所以在这里处理更安全。
             * 相关问题参考：https://gitee.com/zhijiantianya/yudao-cloud/issues/IB7V7Q
             */
            @Override
            public void afterCompletion(int transactionStatus) {
                // 如果事务回滚了，就不做任何事
                if (ObjectUtil.equal(transactionStatus, TransactionSynchronization.STATUS_ROLLED_BACK)) {
                    return;
                }

                // 如果事务状态未知（比如异常中断），且任务已经被删除，也跳过
                if (ObjectUtil.equal(transactionStatus, TransactionSynchronization.STATUS_UNKNOWN)
                        && getTask(task.getId()) == null) {
                    return;
                }

                // ---------------- 情况一：人工审批任务，但没人被分配 ----------------
                if (ObjectUtil.equal(approveType, BpmUserTaskApproveTypeEnum.USER.getType())) {
                    // 如果任务已经有审批人（assignee）或所有者（owner），说明正常分配了，不用自动处理
                    if (!ObjectUtil.isAllEmpty(task.getAssignee(), task.getOwner())) {
                        return;
                    }

                    // 根据流程设计时的配置决定行为：
                    if (ObjectUtil.equal(assignEmptyHandlerType, BpmUserTaskAssignEmptyHandlerTypeEnum.APPROVE.getType())) {
                        // 自动通过
                        getSelf().approveTask(null, new BpmTaskApproveReqVO()
                                .setId(task.getId())
                                .setReason(BpmReasonEnum.ASSIGN_EMPTY_APPROVE.getReason()));
                    } else if (ObjectUtil.equal(assignEmptyHandlerType, BpmUserTaskAssignEmptyHandlerTypeEnum.REJECT.getType())) {
                        // 自动拒绝
                        getSelf().rejectTask(null, new BpmTaskRejectReqVO()
                                .setId(task.getId())
                                .setReason(BpmReasonEnum.ASSIGN_EMPTY_REJECT.getReason()));
                    }
                    // 如果配置是“不做处理”，就什么都不做，任务会一直挂着

                    // ---------------- 情况二：任务本身就是自动审批类型 ----------------
                } else {
                    if (ObjectUtil.equal(approveType, BpmUserTaskApproveTypeEnum.AUTO_APPROVE.getType())) {
                        // 自动通过
                        getSelf().approveTask(null, new BpmTaskApproveReqVO()
                                .setId(task.getId())
                                .setReason(BpmReasonEnum.APPROVE_TYPE_AUTO_APPROVE.getReason()));
                    } else if (ObjectUtil.equal(approveType, BpmUserTaskApproveTypeEnum.AUTO_REJECT.getType())) {
                        // 自动拒绝
                        getSelf().rejectTask(null, new BpmTaskRejectReqVO()
                                .setId(task.getId())
                                .setReason(BpmReasonEnum.APPROVE_TYPE_AUTO_REJECT.getReason()));
                    }
                }
            }
        });
    }
    /**
     * 重要说明：这个方法主要用于处理“任务被取消”的场景，主要有两种情况：
     *
     * 1. 【或签】场景：比如 A、B、C 三人是“或签”（任意一人审批即可），
     *    如果 A 审批通过了，Flowable 会自动删除 B 和 C 的任务。
     *    这时我们需要把 B 和 C 的任务状态更新为“已取消”。
     *
     * 2. 【审批不通过】时：比如主审批人拒绝了，但之前加签了其他人（比如抄送人），
     *    这些加签任务不会被 Flowable 自动删除，我们也需要手动把它们标记为“已取消”。
     */
    @Override
    public void processTaskCanceled(String taskId) {
        // 先尝试获取任务对象（注意：被删除的任务可能已经变成历史任务，此时拿不到）
        Task task = getTask(taskId);
        if (task == null) {
            log.error("[processTaskCanceled][任务 {} 不存在（可能已被删除）]", taskId);
            return;
        }

        // 检查任务当前状态，如果已经是“结束状态”（比如已通过、已拒绝），就不用再处理了
        Integer status = (Integer) task.getTaskLocalVariables().get(BpmnVariableConstants.TASK_VARIABLE_STATUS);
        if (BpmTaskStatusEnum.isEndStatus(status)) {
            log.error("[processTaskCanceled][任务 {} 已是结束状态 {}，无需更新]", taskId, status);
            return;
        }

        // 将任务状态更新为“已取消”，并记录取消原因（系统自动取消）
        updateTaskStatusAndReason(
                taskId,
                BpmTaskStatusEnum.CANCEL.getStatus(),
                BpmReasonEnum.CANCEL_BY_SYSTEM.getReason()
        );

        // 注意：一旦任务被 Flowable 删除（变成历史任务），就无法再用 addComment 添加审批意见了，
        // 所以这里只能通过本地状态记录取消原因，无法在流程引擎里留痕。
    }

    @Override
    @DataPermission(enable = false) // 忽略数据权限，避免因为过滤，导致找不到候选人
    public void processTaskAssigned(Task task) {
        // 发送通知。在事务提交时，批量执行操作，所以直接查询会无法查询到 ProcessInstance，所以这里是通过监听事务的提交来实现。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            /**
             * 特殊情况：部分情况下，TransactionSynchronizationManager 注册 afterCommit 监听时，不会被调用，但是 afterCompletion 可以
             * 例如说：第一个 task 就是配置【自动通过】或者【自动拒绝】时
             * 参见 <a href="https://gitee.com/zhijiantianya/yudao-cloud/issues/IB7V7Q">issue</a> 反馈
             */
            @Override
            public void afterCompletion(int transactionStatus) {
                // 回滚情况，直接返回
                if (ObjectUtil.equal(transactionStatus, TransactionSynchronization.STATUS_ROLLED_BACK)) {
                    return;
                }
                // 特殊情况：第一个 task 【自动通过】时，第二个任务设置审批人时 transactionStatus 会为 STATUS_UNKNOWN，不知道啥原因
                if (ObjectUtil.equal(transactionStatus, TransactionSynchronization.STATUS_UNKNOWN)
                        && getTask(task.getId()) == null) {
                    return;
                }
                if (StrUtil.isEmpty(task.getAssignee())) {
                    log.error("[processTaskAssigned][taskId({}) 没有分配到负责人]", task.getId());
                    return;
                }
                ProcessInstance processInstance = processInstanceService.getProcessInstance(task.getProcessInstanceId());
                if (processInstance == null) {
                    log.error("[processTaskAssigned][taskId({}) 没有找到流程实例]", task.getId());
                    return;
                }

                // 自动去重，通过自动审批的方式 TODO @芋艿 驳回的情况得考虑一下；@lesan：驳回后，又自动审批么？
                BpmProcessDefinitionInfoDO processDefinitionInfo = bpmProcessDefinitionService.getProcessDefinitionInfo(task.getProcessDefinitionId());
                if (processDefinitionInfo == null) {
                    log.error("[processTaskAssigned][taskId({}) 没有找到流程定义({})]", task.getId(), task.getProcessDefinitionId());
                    return;
                }
                if (processDefinitionInfo.getAutoApprovalType() != null) {
                    HistoricTaskInstanceQuery sameAssigneeQuery = historyService.createHistoricTaskInstanceQuery()
                            .processInstanceId(task.getProcessInstanceId())
                            .taskAssignee(task.getAssignee()) // 相同审批人
                            .taskVariableValueEquals(BpmnVariableConstants.TASK_VARIABLE_STATUS, BpmTaskStatusEnum.APPROVE.getStatus())
                            .finished();
                    if (BpmAutoApproveTypeEnum.APPROVE_ALL.getType().equals(processDefinitionInfo.getAutoApprovalType())
                            && sameAssigneeQuery.count() > 0) {
                        getSelf().approveTask(Long.valueOf(task.getAssignee()), new BpmTaskApproveReqVO().setId(task.getId())
                                .setReason(BpmAutoApproveTypeEnum.APPROVE_ALL.getName()));
                        return;
                    }
                    if (BpmAutoApproveTypeEnum.APPROVE_SEQUENT.getType().equals(processDefinitionInfo.getAutoApprovalType())) {
                        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(processInstance.getProcessDefinitionId());
                        if (bpmnModel == null) {
                            log.error("[processTaskAssigned][taskId({}) 没有找到流程模型({})]", task.getId(), task.getProcessDefinitionId());
                            return;
                        }
                        List<String> sourceTaskIds = convertList(BpmnModelUtils.getElementIncomingFlows( // 获取所有上一个节点
                                        BpmnModelUtils.getFlowElementById(bpmnModel, task.getTaskDefinitionKey())),
                                SequenceFlow::getSourceRef);
                        if (sameAssigneeQuery.taskDefinitionKeys(sourceTaskIds).count() > 0) {
                            getSelf().approveTask(Long.valueOf(task.getAssignee()), new BpmTaskApproveReqVO().setId(task.getId())
                                    .setReason(BpmAutoApproveTypeEnum.APPROVE_SEQUENT.getName()));
                            return;
                        }
                    }
                }

                // 获取发起人节点
                BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(processInstance.getProcessDefinitionId());
                if (bpmnModel == null) {
                    log.error("[processTaskAssigned][taskId({}) 没有找到流程模型]", task.getId());
                    return;
                }
                FlowElement userTaskElement = BpmnModelUtils.getFlowElementById(bpmnModel, task.getTaskDefinitionKey());
                // 判断是否为退回或者驳回：如果是退回或者驳回不走这个策略（使用 local variable）
                Boolean returnTaskFlag = runtimeService.getVariableLocal(task.getExecutionId(),
                        String.format(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_RETURN_FLAG, task.getTaskDefinitionKey()), Boolean.class);
                Boolean skipStartUserNodeFlag = Convert.toBool(runtimeService.getVariable(processInstance.getProcessInstanceId(),
                        BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_SKIP_START_USER_NODE, String.class));
                if (userTaskElement.getId().equals(START_USER_NODE_ID)
                        && (skipStartUserNodeFlag == null // 目的：一般是“主流程”，发起人节点，自动通过审核
                        || BooleanUtil.isTrue(skipStartUserNodeFlag)) // 目的：一般是“子流程”，发起人节点，按配置自动通过审核
                        && ObjUtil.notEqual(returnTaskFlag, Boolean.TRUE)) {
                    getSelf().approveTask(Long.valueOf(task.getAssignee()), new BpmTaskApproveReqVO().setId(task.getId())
                            .setReason(BpmReasonEnum.ASSIGN_START_USER_APPROVE_WHEN_SKIP_START_USER_NODE.getReason()));
                    return;
                }
                // 当不为发起人节点时，审批人与提交人为同一人时，根据 BpmUserTaskAssignStartUserHandlerTypeEnum 策略进行处理
                if (ObjectUtil.notEqual(userTaskElement.getId(), START_USER_NODE_ID)
                        && StrUtil.equals(task.getAssignee(), processInstance.getStartUserId())) {
                    if (ObjUtil.notEqual(returnTaskFlag, Boolean.TRUE)) {
                        Integer assignStartUserHandlerType = BpmnModelUtils.parseAssignStartUserHandlerType(userTaskElement);

                        // 情况一：自动跳过
                        if (ObjectUtils.equalsAny(assignStartUserHandlerType,
                                BpmUserTaskAssignStartUserHandlerTypeEnum.SKIP.getType())) {
                            getSelf().approveTask(Long.valueOf(task.getAssignee()), new BpmTaskApproveReqVO().setId(task.getId())
                                    .setReason(BpmReasonEnum.ASSIGN_START_USER_APPROVE_WHEN_SKIP.getReason()));
                            return;
                        }
                        // 情况二：转交给部门负责人审批
                        if (ObjectUtils.equalsAny(assignStartUserHandlerType,
                                BpmUserTaskAssignStartUserHandlerTypeEnum.TRANSFER_DEPT_LEADER.getType())) {
                            AdminUserRespDTO startUser = adminUserApi.getUser(Long.valueOf(processInstance.getStartUserId())).getCheckedData();
                            Assert.notNull(startUser, "提交人({})信息为空", processInstance.getStartUserId());
                            DeptRespDTO dept = startUser.getDeptId() != null ? deptApi.getDept(startUser.getDeptId()).getCheckedData() : null;
                            Assert.notNull(dept, "提交人({})部门({})信息为空", processInstance.getStartUserId(), startUser.getDeptId());
                            // 找不到部门负责人的情况下，自动审批通过
                            // noinspection DataFlowIssue
                            if (dept.getLeaderUserId() == null) {
                                getSelf().approveTask(Long.valueOf(task.getAssignee()), new BpmTaskApproveReqVO().setId(task.getId())
                                        .setReason(BpmReasonEnum.ASSIGN_START_USER_APPROVE_WHEN_DEPT_LEADER_NOT_FOUND.getReason()));
                                return;
                            }
                            // 找得到部门负责人的情况下，修改负责人
                            if (ObjectUtil.notEqual(dept.getLeaderUserId(), startUser.getId())) {
                                getSelf().transferTask(Long.valueOf(task.getAssignee()), new BpmTaskTransferReqVO()
                                        .setId(task.getId()).setAssigneeUserId(dept.getLeaderUserId())
                                        .setReason(BpmReasonEnum.ASSIGN_START_USER_TRANSFER_DEPT_LEADER.getReason()));
                                return;
                            }
                            // 如果部门负责人是自己，还是自己审批吧~
                        }
                    }
                }
                // 注意：需要基于 instance 设置租户编号，避免 Flowable 内部异步时，丢失租户编号
                FlowableUtils.execute(processInstance.getTenantId(), () -> {
                    AdminUserRespDTO startUser = adminUserApi.getUser(Long.valueOf(processInstance.getStartUserId())).getCheckedData();
                    messageService.sendMessageWhenTaskAssigned(BpmTaskConvert.INSTANCE.convert(processInstance, startUser, task));
                });
            }

        });
    }

    @Override
    public void processTaskCompleted(Task task) {
        ProcessInstance processInstance = processInstanceService.getProcessInstance(task.getProcessInstanceId());
        if (processInstance == null) {
            log.error("[processTaskCompleted][taskId({}) 没有找到流程实例]", task.getId());
            return;
        }
        BpmProcessDefinitionInfoDO processDefinitionInfo = bpmProcessDefinitionService.
                getProcessDefinitionInfo(processInstance.getProcessDefinitionId());
        if (processDefinitionInfo == null) {
            log.error("[processTaskCompleted][processDefinitionId({}) 没有找到流程定义]", processInstance.getProcessDefinitionId());
            return;
        }

        // 任务后置通知
        if (ObjUtil.isNotNull(processDefinitionInfo.getTaskAfterTriggerSetting())) {
            BpmModelMetaInfoVO.HttpRequestSetting setting = processDefinitionInfo.getTaskAfterTriggerSetting();
            BpmHttpRequestUtils.executeBpmHttpRequest(processInstance,
                    setting.getUrl(), setting.getHeader(), setting.getBody(), true, setting.getResponse());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processTaskTimeout(String processInstanceId, String taskDefineKey, Integer handlerType) {
        ProcessInstance processInstance = processInstanceService.getProcessInstance(processInstanceId);
        if (processInstance == null) {
            log.error("[processTaskTimeout][processInstanceId({}) 没有找到流程实例]", processInstanceId);
            return;
        }
        List<Task> taskList = getRunningTaskListByProcessInstanceId(processInstanceId, true, taskDefineKey);
        // TODO 优化：未来需要考虑加签的情况
        if (CollUtil.isEmpty(taskList)) {
            log.error("[processTaskTimeout][processInstanceId({}) 定义Key({}) 没有找到任务]", processInstanceId, taskDefineKey);
            return;
        }

        taskList.forEach(task -> FlowableUtils.execute(task.getTenantId(), () -> {
            // 情况一：自动提醒
            if (Objects.equals(handlerType, BpmUserTaskTimeoutHandlerTypeEnum.REMINDER.getType())) {
                messageService.sendMessageWhenTaskTimeout(new BpmMessageSendWhenTaskTimeoutReqDTO()
                        .setProcessInstanceId(processInstanceId).setProcessInstanceName(processInstance.getName())
                        .setTaskId(task.getId()).setTaskName(task.getName()).setAssigneeUserId(Long.parseLong(task.getAssignee())));
                return;
            }

            // 情况二：自动同意
            if (Objects.equals(handlerType, BpmUserTaskTimeoutHandlerTypeEnum.APPROVE.getType())) {
                approveTask(Long.parseLong(task.getAssignee()),
                        new BpmTaskApproveReqVO().setId(task.getId()).setReason(BpmReasonEnum.TIMEOUT_APPROVE.getReason()));
                return;
            }

            // 情况三：自动拒绝
            if (Objects.equals(handlerType, BpmUserTaskTimeoutHandlerTypeEnum.REJECT.getType())) {
                rejectTask(Long.parseLong(task.getAssignee()),
                        new BpmTaskRejectReqVO().setId(task.getId()).setReason(BpmReasonEnum.REJECT_TASK.getReason()));
            }
        }));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processChildProcessTimeout(String processInstanceId, String taskDefineKey) {
        List<ActivityInstance> activityInstances = runtimeService.createActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityId(taskDefineKey).list();
        activityInstances.forEach(activityInstance -> FlowableUtils.execute(activityInstance.getTenantId(),
                () -> moveTaskToEnd(activityInstance.getCalledProcessInstanceId(), BpmReasonEnum.TIMEOUT_APPROVE.getReason())));
    }

    @Override
    public void triggerTask(String processInstanceId, String taskDefineKey) {
        Execution execution = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .activityId(taskDefineKey)
                .singleResult();
        if (execution == null) {
            log.error("[triggerTask][processInstanceId({}) activityId({}) 没有找到执行活动]", processInstanceId, taskDefineKey);
            return;
        }

        // 若存在直接触发接收任务，执行后续节点
        FlowableUtils.execute(execution.getTenantId(),
                () -> runtimeService.trigger(execution.getId()));
    }

    /**
     * 获得自身的代理对象，解决 AOP 生效问题
     *
     * @return 自己
     */
    private BpmTaskServiceImpl getSelf() {
        return SpringUtil.getBean(getClass());
    }

}
