package cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior;

import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import lombok.Setter;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.flowable.engine.impl.bpmn.behavior.ParallelMultiInstanceBehavior;
import org.flowable.engine.impl.bpmn.behavior.SequentialMultiInstanceBehavior;
import org.flowable.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.flowable.engine.impl.bpmn.parser.factory.DefaultActivityBehaviorFactory;

/**
 * 自定义的 ActivityBehaviorFactory 实现类。
 *
 * Flowable 引擎在解析 BPMN 流程定义时，会为每个 BPMN 元素（如 UserTask）创建对应的 Behavior 对象，
 * 用于控制该节点在流程执行时的具体行为（例如任务分配、执行逻辑等）。
 *
 * 本类通过继承 {@link DefaultActivityBehaviorFactory} 并重写关键方法，
 * 替换默认的 UserTask 及多实例行为（Parallel / Sequential Multi-Instance），
 * 以集成自定义的任务候选人（candidate）分配逻辑。
 *
 * 主要目的：
 * 1. 为普通用户任务（UserTask）注入自定义行为 {@link BpmUserTaskActivityBehavior}，
 *    实现基于业务规则（如角色、部门、岗位等）动态分配任务负责人（assignee）或候选人（candidates）。
 * 2. 为并行/串行多实例任务同样注入自定义行为（{@link BpmParallelMultiInstanceBehavior} 和
 *    {@link BpmSequentialMultiInstanceBehavior}），确保多实例场景下也能应用相同的候选人分配逻辑。
 *
 * 使用方式：
 * 该工厂类需在 Flowable 引擎配置中注册（通常通过自定义 ProcessEngineConfiguration），
 * 以替代默认的 ActivityBehaviorFactory，从而在整个流程引擎中生效。
 *
 * @author 芋道源码
 */
@Setter
public class BpmActivityBehaviorFactory extends DefaultActivityBehaviorFactory {

    /**
     * 任务候选人分配器，用于在任务创建时根据业务规则动态计算任务的 assignee 或 candidates。
     * 该组件由 Spring 容器注入，封装了具体的分配逻辑（如调用组织架构服务、解析表达式等）。
     */
    private BpmTaskCandidateInvoker taskCandidateInvoker;

    /**
     * 重写创建用户任务行为的方法。
     *
     * Flowable 在解析 BPMN 中的 <userTask> 元素时会调用此方法。
     * 返回自定义的 {@link BpmUserTaskActivityBehavior} 实例，
     * 替代默认的 {@link UserTaskActivityBehavior}，
     * 从而在任务创建阶段插入自定义的分配逻辑。
     *
     * @param userTask BPMN 模型中的用户任务定义
     * @return 自定义的用户任务行为对象
     */
    @Override
    public UserTaskActivityBehavior createUserTaskActivityBehavior(UserTask userTask) {
        return new BpmUserTaskActivityBehavior(userTask)
                .setTaskCandidateInvoker(taskCandidateInvoker);
    }

    /**
     * 重写创建并行多实例行为的方法。
     *
     * 当 BPMN 中的某个任务配置了并行多实例（parallel multi-instance）时，
     * Flowable 会调用此方法创建对应的行为对象。
     * 返回自定义的 {@link BpmParallelMultiInstanceBehavior} 实例，
     * 以确保在每个并行实例创建时都能应用统一的候选人分配逻辑。
     *
     * @param activity BPMN 活动节点（通常是 UserTask）
     * @param behavior 原始的内部行为（如 UserTask 的行为）
     * @return 自定义的并行多实例行为对象
     */
    @Override
    public ParallelMultiInstanceBehavior createParallelMultiInstanceBehavior(Activity activity,
                                                                             AbstractBpmnActivityBehavior behavior) {
        return new BpmParallelMultiInstanceBehavior(activity, behavior)
                .setTaskCandidateInvoker(taskCandidateInvoker);
    }

    /**
     * 重写创建串行多实例行为的方法。
     *
     * 当 BPMN 中的任务配置了串行多实例（sequential multi-instance）时，
     * Flowable 会调用此方法。
     * 返回自定义的 {@link BpmSequentialMultiInstanceBehavior} 实例，
     * 保证每次串行创建任务实例时都能正确执行候选人分配。
     *
     * @param activity BPMN 活动节点
     * @param behavior 原始的内部行为
     * @return 自定义的串行多实例行为对象
     */
    @Override
    public SequentialMultiInstanceBehavior createSequentialMultiInstanceBehavior(Activity activity,
                                                                                 AbstractBpmnActivityBehavior behavior) {
        return new BpmSequentialMultiInstanceBehavior(activity, behavior)
                .setTaskCandidateInvoker(taskCandidateInvoker);
    }

}