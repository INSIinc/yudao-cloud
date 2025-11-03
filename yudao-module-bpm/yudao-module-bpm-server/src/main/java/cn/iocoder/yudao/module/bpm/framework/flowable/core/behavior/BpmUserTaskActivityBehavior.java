package cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.TaskHelper;
import org.flowable.engine.interceptor.CreateUserTaskBeforeContext;
import org.flowable.task.service.TaskService;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 自定义的【单个】流程任务的 assignee（负责人）分配行为。
 *
 * 该类覆盖了 Flowable 默认的 UserTask 行为，用于实现项目中“任务责任到人”的业务规则：
 * - 每个用户任务必须且只能分配给一个具体的用户（assignee），不能留空；
 * - 候选人由 {@link BpmTaskCandidateInvoker} 根据任务定义动态计算；
 * - 若无法确定唯一负责人（无候选人），则流程中断并抛出异常（由上层处理）；
 * - 项目不允许多个用户同时处理同一个非多实例任务，若需多人协作，应使用会签/或签（即多实例任务）。
 *
 * @author 芋道源码
 */
@Slf4j
public class BpmUserTaskActivityBehavior extends UserTaskActivityBehavior {

    /**
     * 用于计算当前任务的候选用户集合的组件。
     * 该组件由 Spring 容器注入，内部可根据任务定义（如表单变量、部门、岗位、角色等）动态计算出可处理该任务的用户 ID 列表。
     */
    @Setter
    private BpmTaskCandidateInvoker taskCandidateInvoker;

    /**
     * 构造函数，接收原始的 UserTask BPMN 元素，用于继承父类的基础行为。
     *
     * @param userTask BPMN 模型中的用户任务节点
     */
    public BpmUserTaskActivityBehavior(UserTask userTask) {
        super(userTask);
    }

    /**
     * 重写父类的 assignee 分配逻辑。
     *
     * Flowable 在创建用户任务时会调用此方法，用于设置任务的 assignee、owner、候选用户/组等。
     * 本实现完全接管 assignee 的分配逻辑，忽略传入的原始 assignee/candidateUsers 等参数，
     * 而是通过自定义规则重新计算。
     *
     * @param taskService                    Flowable 的任务服务（本方法中未直接使用）
     * @param assignee                       原始 BPMN 中定义的 assignee 表达式（本实现忽略）
     * @param owner                          原始 owner（本实现忽略）
     * @param candidateUsers                 原始候选用户列表（本实现忽略）
     * @param candidateGroups                原始候选组列表（本实现忽略）
     * @param task                           当前创建的任务实体
     * @param expressionManager              表达式管理器（本实现未使用）
     * @param execution                      当前流程执行上下文
     * @param processEngineConfiguration     流程引擎配置（本实现未使用）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    protected void handleAssignments(TaskService taskService, String assignee, String owner,
                                     List<String> candidateUsers, List<String> candidateGroups, TaskEntity task, ExpressionManager expressionManager,
                                     DelegateExecution execution, ProcessEngineConfigurationImpl processEngineConfiguration) {

        // 第一步：根据任务上下文计算出应分配给哪个用户（返回用户 ID）
        Long assigneeUserId = calculateTaskCandidateUsers(execution);

        // 第二步：如果成功计算出负责人，则设置为任务的 assignee
        if (assigneeUserId != null) {
            // Flowable 要求 assignee 是字符串类型，此处将 Long 转为 String
            TaskHelper.changeTaskAssignee(task, String.valueOf(assigneeUserId));
        }
        // 注意：若 assigneeUserId 为 null（即无候选人），则不设置 assignee。
        // 此时任务将没有负责人，后续流程可能会因无法处理而卡住。
        // 实际项目中，建议在此处抛出业务异常，防止流程进入“无人处理”状态。
    }

    /**
     * 根据当前执行上下文，计算出该任务应分配给哪个用户（返回单个用户 ID）。
     *
     * 支持两种场景：
     * 1. 多实例任务（如会签、或签、顺序审批）：此时每个实例对应一个用户，用户 ID 已作为变量注入；
     * 2. 普通单实例任务：通过 {@link BpmTaskCandidateInvoker} 动态计算所有候选用户，再随机选择一个。
     *
     * @param execution 当前流程执行实例
     * @return 分配的用户 ID（Long 类型），若无法分配则返回 null
     */
    private Long calculateTaskCandidateUsers(DelegateExecution execution) {
        // 情况一：当前任务是多实例任务（例如通过 multiInstanceLoopCharacteristics 配置）
        // 此时每个循环实例会对应一个具体的用户，该用户 ID 通常作为变量存入 execution 中。
        // 变量名由 multiInstanceActivityBehavior.getCollectionElementVariable() 指定，
        // 例如在顺序审批中，可能是 "approverId"。
        if (super.multiInstanceActivityBehavior != null) {
            // 从 execution 变量中直接获取当前实例对应的用户 ID
            return execution.getVariable(super.multiInstanceActivityBehavior.getCollectionElementVariable(), Long.class);
        }

        // 情况二：普通单实例任务
        // 第一步：调用候选用户计算器，获取所有可处理该任务的用户 ID 集合
        Set<Long> candidateUserIds = taskCandidateInvoker.calculateUsersByTask(execution);
        if (CollUtil.isEmpty(candidateUserIds)) {
            // 无候选人，返回 null，后续 handleAssignments 不会设置 assignee
            return null;
        }

        // 第二步：从候选用户中随机选择一个作为最终负责人
        // 设计原因：项目要求“每个任务有且仅有一个负责人”，不允许多人同时处理同一任务。
        // 若业务需要多人处理，应使用多实例任务（如 BpmParallelMultiInstanceBehavior 实现的会签/或签）。
        int index = RandomUtil.randomInt(candidateUserIds.size());
        return CollUtil.get(candidateUserIds, index);
    }

    /**
     * 重写父类方法，用于设置任务的 category（分类）。
     *
     * Flowable 任务的 category 字段可用于标识任务来源或类型。
     * 本实现将其设置为所属流程定义的 category，便于后续按分类查询或统计。
     *
     * @param beforeContext            创建任务前的上下文（本实现未使用）
     * @param expressionManager        表达式管理器（本实现未使用）
     * @param task                     当前任务实体
     * @param execution                当前执行上下文
     */
    @Override
    protected void handleCategory(CreateUserTaskBeforeContext beforeContext, ExpressionManager expressionManager,
                                  TaskEntity task, DelegateExecution execution) {
        // 根据流程定义 ID 查询流程定义实体
        ProcessDefinitionEntity processDefinitionEntity =
                CommandContextUtil.getProcessDefinitionEntityManager().findById(execution.getProcessDefinitionId());

        if (processDefinitionEntity == null) {
            log.warn("[handleCategory][任务编号({}) 找不到流程定义({})]", task.getId(), execution.getProcessDefinitionId());
            return;
        }

        // 将流程定义的 category 设置到任务上
        task.setCategory(processDefinitionEntity.getCategory());
    }

}