package cn.iocoder.yudao.module.bpm.framework.flowable.core.listener;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceCopyService;
import jakarta.annotation.Resource;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Set;

import static cn.iocoder.yudao.module.bpm.framework.flowable.core.listener.BpmCopyTaskDelegate.BEAN_NAME;

/**
 * 处理流程中“抄送”逻辑的 {@link JavaDelegate} 实现类。
 * <p>
 * 该类用于在 Flowable 流程引擎执行到特定“抄送节点”时，动态计算应被抄送的用户，并创建对应的抄送记录。
 * 目前仅适用于仿钉钉/飞书风格的 BPMN 流程设计中的【抄送节点】（通常是一个 ServiceTask 节点，其 delegateExpression 指向本 Bean）。
 *
 * <h3>使用场景说明</h3>
 * 在 BPMN 流程定义中，若某节点被配置为抄送节点（例如通过扩展属性标记），则该节点的执行会委托给本类。
 * 本类不产生审批任务，仅将流程当前状态信息“抄送”给指定用户，供其查阅。
 *
 * @author jason
 */
@Component(BEAN_NAME) // 注册为 Spring Bean，Bean 名称为 bpmCopyTaskDelegate，供 BPMN 中引用
public class BpmCopyTaskDelegate implements JavaDelegate {

    /**
     * 本类作为 Spring Bean 的名称常量，便于在 BPMN 文件中通过 ${bpmCopyTaskDelegate} 或 delegateExpression 引用。
     */
    public static final String BEAN_NAME = "bpmCopyTaskDelegate";

    /**
     * 用于根据当前流程执行上下文（execution）计算出该抄送节点应抄送给哪些用户。
     * 通常会读取 BPMN 节点上的扩展属性（如 assignee、candidateUsers、表达式等），
     * 并结合组织架构、角色、岗位等业务规则动态解析出用户 ID 集合。
     */
    @Resource
    private BpmTaskCandidateInvoker taskCandidateInvoker;

    /**
     * 抄送服务，用于持久化抄送记录。
     * 调用该服务将为指定用户创建与当前流程实例关联的抄送通知，便于后续在“已抄送”或“待阅”列表中展示。
     */
    @Resource
    private BpmProcessInstanceCopyService processInstanceCopyService;

    /**
     * Flowable 引擎在执行到关联本 Delegate 的 BPMN 节点时，会调用此方法。
     *
     * @param execution 当前流程执行上下文，包含流程实例 ID、当前节点信息、流程变量等。
     */
    @Override
    public void execute(DelegateExecution execution) {
        // 步骤 1：根据当前执行上下文，计算出需要抄送给哪些用户
        Set<Long> userIds = taskCandidateInvoker.calculateUsersByTask(execution);
        // 如果没有计算出任何抄送人，则直接退出，不创建抄送记录
        if (CollUtil.isEmpty(userIds)) {
            return;
        }

        // 步骤 2：获取当前 BPMN 元素（即抄送节点）的信息，用于记录抄送来源
        FlowElement currentFlowElement = execution.getCurrentFlowElement();

        // 步骤 3：调用抄送服务，为每个用户创建抄送记录
        // 参数说明：
        // - userIds: 抄送目标用户 ID 集合
        // - 第二个参数（taskId）为 null，因为抄送不关联具体任务（非审批节点）
        // - processInstanceId: 当前流程实例 ID
        // - currentFlowElement.getId(): 抄送节点的 BPMN 元素 ID（如 "copyTask_1"）
        // - currentFlowElement.getName(): 抄送节点的名称（如 "项目负责人抄送"）
        // - 最后一个参数（reason）为 null，可根据业务需要扩展
        processInstanceCopyService.createProcessInstanceCopy(
                userIds,
                null, // taskId
                execution.getProcessInstanceId(),
                currentFlowElement.getId(),
                currentFlowElement.getName(),
                null // reason
        );
    }
}