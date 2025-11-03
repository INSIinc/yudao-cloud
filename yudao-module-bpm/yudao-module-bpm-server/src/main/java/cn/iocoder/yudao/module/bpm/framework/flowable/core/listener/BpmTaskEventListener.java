package cn.iocoder.yudao.module.bpm.framework.flowable.core.listener;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmBoundaryEventTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import com.google.common.collect.ImmutableSet;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener;
import org.flowable.engine.delegate.event.FlowableActivityCancelledEvent;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.job.api.Job;
import org.flowable.task.api.Task;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Flowable 引擎事件监听器，用于监听任务（Task）生命周期中的关键事件，包括：
 * - 任务创建（TASK_CREATED）
 * - 任务分配（TASK_ASSIGNED）
 * - 任务完成（TASK_COMPLETED）
 * - 活动取消（ACTIVITY_CANCELLED）：用于处理任务被取消的场景
 * - 定时器触发（TIMER_FIRED）：主要用于处理边界定时器（如审批超时、延迟触发等）
 *
 * 该监听器通过 Flowable 的事件机制，在对应事件发生时调用业务服务完成状态记录、通知等操作。
 *
 * @author jason
 */
@Component
@Slf4j
public class BpmTaskEventListener extends AbstractFlowableEngineEventListener {

    /**
     * BPM 模型服务，用于获取流程定义对应的 BPMN 模型。
     * 使用 @Lazy 解决可能的循环依赖问题。
     */
    @Resource
    @Lazy
    private BpmModelService modelService;

    /**
     * BPM 任务服务，用于处理任务创建、分配、完成、取消、超时等业务逻辑。
     * 使用 @Lazy 解决可能的循环依赖问题。
     */
    @Resource
    @Lazy
    private BpmTaskService taskService;

    /**
     * 定义本监听器关注的 Flowable 引擎事件类型集合。
     * 只监听以下事件：
     * - TASK_CREATED：任务被创建时
     * - TASK_ASSIGNED：任务被分配（即 assignee 被设置）时
     * - TASK_COMPLETED：任务被完成时（注意：实际审批状态可能已在完成前设置，此处主要用于后置处理）
     * - ACTIVITY_CANCELLED：活动被取消（如流程回退、终止）时
     * - TIMER_FIRED：定时器触发时（用于处理超时边界事件）
     */
    public static final Set<FlowableEngineEventType> TASK_EVENTS = ImmutableSet.<FlowableEngineEventType>builder()
            .add(FlowableEngineEventType.TASK_CREATED)
            .add(FlowableEngineEventType.TASK_ASSIGNED)
            .add(FlowableEngineEventType.TASK_COMPLETED)
            .add(FlowableEngineEventType.ACTIVITY_CANCELLED)
            .add(FlowableEngineEventType.TIMER_FIRED)
            .build();

    /**
     * 构造函数：注册需要监听的事件类型。
     */
    public BpmTaskEventListener() {
        super(TASK_EVENTS);
    }

    // ========================= 任务事件处理 =========================

    /**
     * 当任务被创建时触发。
     * 此时任务已生成，但尚未分配处理人（assignee 可能为空）。
     *
     * @param event Flowable 引擎事件对象，包含任务实体
     */
    @Override
    protected void taskCreated(FlowableEngineEntityEvent event) {
        Task entity = (Task) event.getEntity();
        // 在对应租户上下文中执行业务逻辑
        FlowableUtils.execute(entity.getTenantId(), () -> taskService.processTaskCreated(entity));
    }

    /**
     * 当任务被分配时触发（即 assignee 被设置）。
     * 此时任务已指派给具体处理人。
     *
     * @param event Flowable 引擎事件对象，包含任务实体
     */
    @Override
    protected void taskAssigned(FlowableEngineEntityEvent event) {
        Task entity = (Task) event.getEntity();
        FlowableUtils.execute(entity.getTenantId(), () -> taskService.processTaskAssigned(entity));
    }

    /**
     * 当任务被完成时触发。
     * 注意：在 Yudao 的设计中，审批结果（通过/拒绝）通常在完成前已记录，
     * 此处主要用于后置操作，如记录日志、发送通知、更新关联业务状态等。
     *
     * @param event Flowable 引擎事件对象，包含任务实体
     */
    @Override
    protected void taskCompleted(FlowableEngineEntityEvent event) {
        Task entity = (Task) event.getEntity();
        FlowableUtils.execute(entity.getTenantId(), () -> taskService.processTaskCompleted(entity));
    }

    /**
     * 当活动（Activity）被取消时触发。
     * 常见于流程回退、终止、或边界事件触发导致原任务被取消。
     *
     * 此方法通过 executionId 查询历史活动实例，找到关联的任务并标记为“已取消”。
     *
     * @param event Flowable 活动取消事件
     */
    @Override
    protected void activityCancelled(FlowableActivityCancelledEvent event) {
        // 根据 executionId 查询所有历史活动实例（可能包含多个任务）
        List<HistoricActivityInstance> activityList = taskService.getHistoricActivityListByExecutionId(event.getExecutionId());
        if (CollUtil.isEmpty(activityList)) {
            log.error("[activityCancelled][使用 executionId({}) 查找不到对应的活动实例]", event.getExecutionId());
            return;
        }

        // 遍历所有历史活动，只处理具有 taskId 的（即用户任务）
        activityList.forEach(activity -> {
            if (StrUtil.isEmpty(activity.getTaskId())) {
                return; // 跳过非任务类型的活动（如网关、服务任务等）
            }
            // 调用任务服务，标记该任务为已取消
            taskService.processTaskCanceled(activity.getTaskId());
        });
    }

    /**
     * 当定时器（Timer）触发时调用。
     * 主要用于处理边界定时器（Boundary Timer Event），例如：
     * - 用户任务超时
     * - 延迟自动通过
     * - 子流程执行超时
     *
     * 注意：Flowable 的 TIMER_FIRED 事件中的 elementId 有时为空（尤其在旧版本或特定 Job 类型下），
     * 因此需从 Job 的配置中解析 activityId。
     *
     * @param event Flowable 引擎事件（实体为 Job）
     */
    @Override
    @SuppressWarnings("PatternVariableCanBeUsed")
    protected void timerFired(FlowableEngineEntityEvent event) {
        // 1. 获取流程定义和定时器关联的 BPMN 元素
        String processDefinitionId = event.getProcessDefinitionId();
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(processDefinitionId);
        Job entity = (Job) event.getEntity();

        // 1.1 获取 elementId（即定时器绑定的 BPMN 元素 ID）
        String elementId = entity.getElementId();
        if (elementId == null && entity.getJobHandlerConfiguration() != null) {
            try {
                // 特殊情况：elementId 为空时，尝试从 JobHandlerConfiguration JSON 中提取 activityId
                // 例如：{"activityId":"Task_123", ...}
                String handlerConfig = entity.getJobHandlerConfiguration();
                if (handlerConfig.startsWith("{") && handlerConfig.contains("activityId")) {
                    elementId = new JSONObject(handlerConfig).getStr("activityId");
                }
            } catch (Exception e) {
                log.error("[timerFired][解析 entity({}) 失败]", entity, e);
                return;
            }
        }
        if (elementId == null) {
            log.error("[timerFired][解析 entity({}) elementId 为空，跳过处理]", entity);
            return;
        }

        // 1.2 通过 elementId 获取对应的 BPMN 元素
        FlowElement element = BpmnModelUtils.getFlowElementById(bpmnModel, elementId);
        if (!(element instanceof BoundaryEvent)) {
            // 非边界事件（如中间定时器等）不处理，直接返回
            return;
        }

        // 1.3 判断边界事件类型
        BoundaryEvent boundaryEvent = (BoundaryEvent) element;
        // 从扩展属性中读取自定义的边界事件类型（例如：1=用户任务超时，2=延迟定时器，3=子流程超时）
        String boundaryEventType = BpmnModelUtils.parseBoundaryEventExtensionElement(boundaryEvent,
                BpmnModelConstants.BOUNDARY_EVENT_TYPE);
        BpmBoundaryEventTypeEnum bpmTimerBoundaryEventType = BpmBoundaryEventTypeEnum.typeOf(NumberUtils.parseInt(boundaryEventType));

        // 2. 根据边界事件类型执行不同逻辑
        if (ObjectUtil.equal(bpmTimerBoundaryEventType, BpmBoundaryEventTypeEnum.USER_TASK_TIMEOUT)) {
            // 2.1 用户任务超时：例如审批超时自动拒绝/通过
            String timeoutHandlerType = BpmnModelUtils.parseBoundaryEventExtensionElement(boundaryEvent,
                    BpmnModelConstants.USER_TASK_TIMEOUT_HANDLER_TYPE);
            String taskKey = boundaryEvent.getAttachedToRefId(); // 被附加到的任务 ID（即超时任务）
            taskService.processTaskTimeout(
                    event.getProcessInstanceId(),
                    taskKey,
                    NumberUtils.parseInt(timeoutHandlerType) // 超时处理方式（如 1=自动通过，2=自动拒绝）
            );

        } else if (ObjectUtil.equal(bpmTimerBoundaryEventType, BpmBoundaryEventTypeEnum.DELAY_TIMER_TIMEOUT)) {
            // 2.2 延迟定时器触发：例如等待 2 小时后自动继续流程
            String taskKey = boundaryEvent.getAttachedToRefId();
            taskService.triggerTask(event.getProcessInstanceId(), taskKey); // 触发任务继续执行

        } else if (ObjectUtil.equal(bpmTimerBoundaryEventType, BpmBoundaryEventTypeEnum.CHILD_PROCESS_TIMEOUT)) {
            // 2.3 子流程执行超时：用于监控子流程是否在规定时间内完成
            String taskKey = boundaryEvent.getAttachedToRefId(); // 实际是子流程调用活动的 ID
            taskService.processChildProcessTimeout(event.getProcessInstanceId(), taskKey);

        }
        // 其他类型边界事件可在此扩展
    }

}