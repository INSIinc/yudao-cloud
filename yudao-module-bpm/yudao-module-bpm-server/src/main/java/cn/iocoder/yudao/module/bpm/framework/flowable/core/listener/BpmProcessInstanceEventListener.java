package cn.iocoder.yudao.module.bpm.framework.flowable.core.listener;

import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import com.google.common.collect.ImmutableSet;
import jakarta.annotation.Resource;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener;
import org.flowable.engine.delegate.event.FlowableCancelledEvent;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 监听 Flowable 流程实例（ProcessInstance）的生命周期事件，
 * 并在其状态发生变更时，同步更新业务系统中该流程实例的 status 状态。
 *
 * 本监听器主要关注以下三种事件：
 * - 流程实例创建（PROCESS_CREATED）
 * - 流程实例完成（PROCESS_COMPLETED）
 * - 流程实例取消（PROCESS_CANCELLED）
 *
 * 通过监听这些事件，调用 {@link BpmProcessInstanceService} 更新业务表中的流程状态，
 * 保证业务数据与 Flowable 引擎状态的一致性。
 *
 * @author jason
 */
@Component
public class BpmProcessInstanceEventListener extends AbstractFlowableEngineEventListener {

    /**
     * 定义本监听器需要监听的 Flowable 引擎事件类型集合。
     * 使用 Guava 的 ImmutableSet 构建不可变集合，确保线程安全且防止误修改。
     */
    public static final Set<FlowableEngineEventType> PROCESS_INSTANCE_EVENTS = ImmutableSet.<FlowableEngineEventType>builder()
            .add(FlowableEngineEventType.PROCESS_CREATED)     // 流程实例创建事件
            .add(FlowableEngineEventType.PROCESS_COMPLETED)   // 流程实例正常结束事件
            .add(FlowableEngineEventType.PROCESS_CANCELLED)   // 流程实例被取消事件
            .build();

    /**
     * 注入流程实例业务服务，用于更新业务状态。
     * 使用 @Lazy 注解实现延迟加载，避免因循环依赖导致 Spring 容器启动失败。
     */
    @Resource
    @Lazy // 延迟加载，避免与其他依赖形成循环引用
    private BpmProcessInstanceService processInstanceService;

    /**
     * 构造函数：指定本监听器需要监听的事件类型。
     * 调用父类构造器，传入关注的事件类型集合。
     */
    public BpmProcessInstanceEventListener() {
        super(PROCESS_INSTANCE_EVENTS);
    }

    /**
     * 处理流程实例创建事件（PROCESS_CREATED）。
     *
     * 当 Flowable 引擎创建一个新的流程实例时触发。
     * 从事件中获取流程实例对象，并调用业务服务记录“流程已创建”状态。
     *
     * 注意：使用 {@link FlowableUtils#execute} 切换到流程实例所属租户上下文，
     *      确保多租户环境下操作的是正确的数据源或租户数据。
     *
     * @param event Flowable 引擎实体事件，包含流程实例信息
     */
    @Override
    protected void processCreated(FlowableEngineEntityEvent event) {
        ProcessInstance processInstance = (ProcessInstance) event.getEntity();
        FlowableUtils.execute(processInstance.getTenantId(),
                () -> processInstanceService.processProcessInstanceCreated(processInstance));
    }

    /**
     * 处理流程实例完成事件（PROCESS_COMPLETED）。
     *
     * 当流程实例正常执行完毕（到达结束节点）时触发。
     * 更新业务系统中该流程实例的状态为“已完成”。
     *
     * 同样通过 {@link FlowableUtils#execute} 切换租户上下文，保证多租户隔离。
     *
     * @param event Flowable 引擎实体事件
     */
    @Override
    protected void processCompleted(FlowableEngineEntityEvent event) {
        ProcessInstance processInstance = (ProcessInstance) event.getEntity();
        FlowableUtils.execute(processInstance.getTenantId(),
                () -> processInstanceService.processProcessInstanceCompleted(processInstance));
    }

    /**
     * 处理流程实例取消事件（PROCESS_CANCELLED）。
     *
     * 当流程被手动取消（例如调用 deleteProcessInstance）时触发。
     * 注意：Flowable 在某些情况下（如跳转到 EndEvent 后调用 delete）可能不会触发 PROCESS_COMPLETED，
     *       而是直接触发 PROCESS_CANCELLED，因此此处需特殊处理。
     *
     * 由于 FlowableCancelledEvent 中不直接包含完整的 ProcessInstance 对象，
     * 所以通过流程实例 ID 从业务服务中重新查询流程实例信息。
     *
     * 若查询到流程实例存在，则同样调用“完成”逻辑进行状态更新（业务上视为终止），
     * 因为在业务视角中，“取消”和“完成”都代表流程结束，只是原因不同。
     *
     * @param event Flowable 取消事件，包含流程实例 ID
     */
    @Override
    protected void processCancelled(FlowableCancelledEvent event) {
        // 特殊情况说明：
        // 当通过 Flowable 的 API（如 runtimeService.deleteProcessInstance）删除流程时，
        // 会触发 PROCESS_CANCELLED 事件，但此时 ProcessInstance 实体可能已被删除，
        // 因此无法从 event.getEntity() 获取有效对象。
        // 故通过业务服务根据 ID 查询流程实例（可能仍存在于业务表中）。

        ProcessInstance processInstance = processInstanceService.getProcessInstance(event.getProcessInstanceId());
        if (processInstance != null) {
            // 切换租户上下文后，调用“完成”处理逻辑（业务上统一视为结束）
            FlowableUtils.execute(processInstance.getTenantId(),
                    () -> processInstanceService.processProcessInstanceCompleted(processInstance));
        }
        // 如果为 null，说明业务数据已不存在，无需处理
    }

}