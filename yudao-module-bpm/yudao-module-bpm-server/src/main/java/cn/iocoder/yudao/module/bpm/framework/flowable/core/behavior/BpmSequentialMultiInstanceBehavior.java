package cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.util.collection.SetUtils;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmChildProcessMultiInstanceSourceTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import lombok.Setter;
import org.flowable.bpmn.model.*;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.flowable.engine.impl.bpmn.behavior.SequentialMultiInstanceBehavior;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;

import java.util.List;
import java.util.Set;

/**
 * 自定义的【串行】多实例任务行为类，用于 Flowable 中 UserTask 或 CallActivity 的串行多实例审批场景。
 *
 * <p>该类继承自 Flowable 内置的 {@link SequentialMultiInstanceBehavior}，主要重写了任务实例数量的解析逻辑（resolveNrOfInstances）
 * 以及原始行为的执行逻辑（executeOriginalBehavior），以支持动态分配任务审批人，并保证串行多实例流程的正确执行。</p>
 *
 * <p>与并行多实例行为类 {@link BpmParallelMultiInstanceBehavior} 在逻辑上类似，但继承的父类不同，从而适应串行执行语义。</p>
 *
 * @author 芋道源码
 */
@Setter
public class BpmSequentialMultiInstanceBehavior extends SequentialMultiInstanceBehavior {

    /**
     * 用于动态计算当前任务节点的所有候选审批人（用户ID集合）的组件。
     * 此组件会在流程运行时根据配置（如表单、角色、部门等）计算出实际的审批人列表。
     */
    private BpmTaskCandidateInvoker taskCandidateInvoker;

    /**
     * 构造方法，初始化串行多实例行为。
     *
     * @param activity 当前 BPMN 活动（如 UserTask 或 CallActivity）
     * @param innerActivityBehavior 内部的实际活动行为（如 UserTaskBehavior）
     */
    public BpmSequentialMultiInstanceBehavior(Activity activity, AbstractBpmnActivityBehavior innerActivityBehavior) {
        super(activity, innerActivityBehavior);
    }

    /**
     * 重写父类方法，用于动态解析当前多实例任务需要创建的实例数量（即审批人数量）。
     *
     * <p>关键逻辑分为两种情况：</p>
     * <ul>
     *   <li>情况一：当前节点是 {@link UserTask}（用户任务）</li>
     *   <li>情况二：当前节点是 {@link CallActivity}（调用子流程）</li>
     * </ul>
     *
     * <p>对于 UserTask，优先从本地变量中读取已分配的审批人集合，若未分配或为空，则通过 {@link #taskCandidateInvoker}
     * 动态计算审批人，并将结果缓存到本地变量中，避免重复计算。</p>
     *
     * <p>特别说明：使用 {@link SetUtils#asSet(Long)} 包含一个 null 元素，是为了确保即使无审批人，
     * Flowable 仍会创建一个空任务（防止流程自动跳过），便于后续人工干预或自动通过/拒绝逻辑处理。</p>
     *
     * <p>与并行版本的关键差异：此处虽也使用 Set，但在并行版本中可能用 HashSet，而串行版本应使用有序集合（如 LinkedHashSet）以保持审批顺序。
     * 但当前代码中并未显式指定 LinkedHashSet，实际依赖 {@link BpmTaskCandidateInvoker#calculateUsersByTask} 返回有序 Set。</p>
     *
     * @param execution 当前执行上下文
     * @return 需要创建的多实例任务数量（即审批人数量）
     */
    @Override
    protected int resolveNrOfInstances(DelegateExecution execution) {
        // 情况一：当前节点是用户任务（UserTask）
        if (execution.getCurrentFlowElement() instanceof UserTask) {
            // 清除 expression 方式，强制使用变量方式（collectionVariable）
            super.collectionExpression = null; // Flowable 中 collectionExpression 与 collectionVariable 互斥
            // 设置多实例集合变量名：格式为 "collection_{activityId}"
            super.collectionVariable = FlowableUtils.formatExecutionCollectionVariable(execution.getCurrentActivityId());
            // 设置每次迭代的元素变量名：格式为 "element_{activityId}"
            super.collectionElementVariable = FlowableUtils.formatExecutionCollectionElementVariable(execution.getCurrentActivityId());

            // 从本地变量中读取已分配的审批人 ID 集合（避免回退后重复分配问题）
            @SuppressWarnings("unchecked")
            Set<Long> assigneeUserIds = (Set<Long>) execution.getVariableLocal(super.collectionVariable, Set.class);
            if (assigneeUserIds == null) {
                // 若未分配，则动态计算审批人
                assigneeUserIds = taskCandidateInvoker.calculateUsersByTask(execution);
                if (CollUtil.isEmpty(assigneeUserIds)) {
                    // 特殊处理：若审批人为空，仍需保留一个 null 元素
                    // 目的：确保至少生成一个任务实例，防止流程自动跳过
                    // 应用于：审批人为空、或配置为“自动通过/拒绝”等场景
                    assigneeUserIds = SetUtils.asSet((Long) null);
                }
                // 将计算结果缓存到本地变量，避免后续重复计算（尤其在流程回退时）
                execution.setVariableLocal(super.collectionVariable, assigneeUserIds);
            }
            return assigneeUserIds.size();
        }

        // 情况二：当前节点是调用子流程（CallActivity）
        if (execution.getCurrentFlowElement() instanceof CallActivity) {
            FlowElement flowElement = execution.getCurrentFlowElement();
            // 解析多实例数据源类型（来自表单配置）
            Integer sourceType = BpmnModelUtils.parseMultiInstanceSourceType(flowElement);
            if (BpmChildProcessMultiInstanceSourceTypeEnum.NUMBER_FORM.getType().equals(sourceType)) {
                // 类型为“数字类型”：直接读取变量值作为实例数量
                return execution.getVariable(super.collectionExpression.getExpressionText(), Integer.class);
            }
            if (BpmChildProcessMultiInstanceSourceTypeEnum.MULTIPLE_FORM.getType().equals(sourceType)) {
                // 类型为“列表类型”：读取变量中的 List，取其 size 作为实例数量
                return execution.getVariable(super.collectionExpression.getExpressionText(), List.class).size();
            }
        }

        // 兜底：调用父类默认逻辑（理论上不会走到这里）
        return super.resolveNrOfInstances(execution);
    }

    /**
     * 重写父类方法，在每次执行单个实例（如一个审批人任务）前，重新设置多实例相关变量。
     *
     * <p>背景：在某些场景下（如流程回退后重新进入多实例任务），Flowable 内部的 collectionExpression 可能未正确重置，
     * 导致后续实例执行异常。因此每次执行前需显式清空 expression 并设置正确的变量名。</p>
     *
     * <p>特别处理：对于 CallActivity 或 SubProcess 节点，直接调用父类逻辑，因为这些节点不涉及审批人分配。</p>
     *
     * <p>修复参考：
     * - https://t.zsxq.com/53Meo （知识星球内部问题）
     * - https://gitee.com/zhijiantianya/yudao-cloud/issues/IC239F （Gitee Issue，涉及安全与变量清理问题）</p>
     *
     * @param execution 当前执行实例
     * @param multiInstanceRootExecution 多实例根执行对象
     * @param loopCounter 当前循环索引（从0开始）
     */
    @Override
    protected void executeOriginalBehavior(DelegateExecution execution, ExecutionEntity multiInstanceRootExecution, int loopCounter) {
        // 如果是调用子流程或子流程节点，直接走父类逻辑（不涉及审批人变量设置）
        if (execution.getCurrentFlowElement() instanceof CallActivity
                || execution.getCurrentFlowElement() instanceof SubProcess) {
            super.executeOriginalBehavior(execution, multiInstanceRootExecution, loopCounter);
            return;
        }

        // 修复关键点：防止因变量未清理导致的执行异常（参考 Issue IC239F）
        // 每次执行前重置多实例配置，确保使用变量方式而非表达式
        super.collectionExpression = null;
        super.collectionVariable = FlowableUtils.formatExecutionCollectionVariable(execution.getCurrentActivityId());
        super.collectionElementVariable = FlowableUtils.formatExecutionCollectionElementVariable(execution.getCurrentActivityId());

        // 执行原始行为（如创建 UserTask）
        super.executeOriginalBehavior(execution, multiInstanceRootExecution, loopCounter);
    }

}