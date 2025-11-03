package cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.util.collection.SetUtils;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmChildProcessMultiInstanceSourceTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import lombok.Setter;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.flowable.engine.impl.bpmn.behavior.ParallelMultiInstanceBehavior;

import java.util.List;
import java.util.Set;

/**
 * 自定义的【并行多实例】行为类，用于替代 Flowable 默认的 ParallelMultiInstanceBehavior。
 *
 * <p>核心目标：在并行多实例任务中，动态计算并设置每个任务实例的负责人（assignee）。
 * 传统方式依赖 BPMN 中的 collectionVariable 表达式，但本项目采用业务逻辑动态计算任务候选人。
 *
 * <p>关键机制：
 * 1. 当前节点为 UserTask 时，调用 {@link BpmTaskCandidateInvoker} 计算出所有可能的处理人（assigneeUserIds）。
 * 2. 将这些处理人存入 execution 的局部变量（local variable）中，变量名由活动 ID 动态生成。
 * 3. 后续 {@link BpmUserTaskActivityBehavior} 会读取该变量，为每个实例创建对应的任务，并设置 assignee。
 * 4. 若无处理人，则插入一个 null 元素，确保至少生成一个任务（用于“自动通过/拒绝”等场景）。
 *
 * <p>同时支持 CallActivity（子流程调用）的多实例场景，根据子流程来源类型（表单数字 or 表单列表）确定实例数量。
 *
 * @author kemengkai
 * @since 2022-04-21 16:57
 */
@Setter
public class BpmParallelMultiInstanceBehavior extends ParallelMultiInstanceBehavior {

    /**
     * 任务候选人计算器，由 Spring 容器注入。
     * 用于根据当前执行上下文（如流程变量、节点配置等）动态计算出任务的处理人集合。
     */
    private BpmTaskCandidateInvoker taskCandidateInvoker;

    /**
     * 构造函数，调用父类构造器。
     *
     * @param activity            当前多实例活动（如 UserTask 或 CallActivity）
     * @param innerActivityBehavior 内部活动行为（例如 UserTask 的默认行为）
     */
    public BpmParallelMultiInstanceBehavior(Activity activity,
                                            AbstractBpmnActivityBehavior innerActivityBehavior) {
        super(activity, innerActivityBehavior);
    }

    /**
     * 重写父类方法，用于确定并行多实例的任务实例数量（nrOfInstances）。
     *
     * <p>本方法的核心职责：
     * - 对于 UserTask：完全绕过 BPMN 中定义的 collectionExpression，改用业务逻辑计算处理人；
     *   并将处理人集合存入 execution 变量，供后续任务创建使用。
     * - 对于 CallActivity：根据子流程多实例的来源类型，从流程变量中读取实例数量。
     *
     * 注意：此方法必须返回实际要创建的实例数量。
     *
     * @param execution 当前执行实例（包含流程变量、当前节点等上下文信息）
     * @return 需要创建的并行实例数量
     */
    @Override
    protected int resolveNrOfInstances(DelegateExecution execution) {
        FlowElement currentElement = execution.getCurrentFlowElement();

        // ========== 情况一：当前节点是 UserTask（用户任务） ==========
        if (currentElement instanceof UserTask) {
            // Step 1: 动态设置多实例所需的变量名
            // Flowable 要求必须设置 collectionVariable（集合变量名）和 collectionElementVariable（每个元素的变量名）
            // 这里根据当前活动 ID 生成唯一变量名，避免不同节点冲突
            super.collectionExpression = null; // 清除表达式模式（与 collectionVariable 互斥）
            super.collectionVariable = FlowableUtils.formatExecutionCollectionVariable(execution.getCurrentActivityId());
            super.collectionElementVariable = FlowableUtils.formatExecutionCollectionElementVariable(execution.getCurrentActivityId());

            // Step 2: 尝试从 execution 中获取已缓存的处理人集合（防止重复计算）
            @SuppressWarnings("unchecked")
            Set<Long> assigneeUserIds = (Set<Long>) execution.getVariable(super.collectionVariable, Set.class);

            if (assigneeUserIds == null) {
                // 未缓存，则调用业务逻辑计算处理人
                assigneeUserIds = taskCandidateInvoker.calculateUsersByTask(execution);

                // 特殊处理：如果计算结果为空（例如审批人未指定），仍需生成至少一个任务实例
                // 原因：某些场景（如“自动通过”、“自动拒绝”）需要任务存在，但 assignee 为 null
                // 插入一个 null 元素，确保后续 BpmUserTaskActivityBehavior 能创建一个任务
                if (CollUtil.isEmpty(assigneeUserIds)) {
                    assigneeUserIds = SetUtils.asSet((Long) null);
                }

                // 将计算出的处理人集合存入 execution 的【局部变量】（setVariableLocal）
                // 使用局部变量而非全局变量，避免影响父流程或其他并行分支
                execution.setVariableLocal(super.collectionVariable, assigneeUserIds);
            }

            // 返回处理人数量，即需要创建的并行任务实例数
            return assigneeUserIds.size();
        }

        // ========== 情况二：当前节点是 CallActivity（调用子流程） ==========
        if (currentElement instanceof CallActivity) {
            // 从 BPMN 模型中解析子流程多实例的来源类型（数字 or 列表）
            Integer sourceType = BpmnModelUtils.parseMultiInstanceSourceType(currentElement);

            // 根据来源类型，从流程变量中读取对应的值来决定实例数量
            if (BpmChildProcessMultiInstanceSourceTypeEnum.NUMBER_FORM.getType().equals(sourceType)) {
                // 类型为“数字表单”：变量值为 Integer，直接返回该数值
                return execution.getVariable(super.collectionExpression.getExpressionText(), Integer.class);
            }

            if (BpmChildProcessMultiInstanceSourceTypeEnum.MULTIPLE_FORM.getType().equals(sourceType)) {
                // 类型为“多选表单”：变量值为 List，返回列表大小
                @SuppressWarnings("unchecked")
                List<?> list = execution.getVariable(super.collectionExpression.getExpressionText(), List.class);
                return list != null ? list.size() : 0;
            }
        }

        // ========== 其他情况：回退到父类默认逻辑 ==========
        // 理论上不会走到这里，但保留以确保兼容性
        return super.resolveNrOfInstances(execution);
    }

}