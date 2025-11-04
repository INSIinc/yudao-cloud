package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.user.BpmTaskCandidateUserStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import com.google.common.collect.Sets;
import jakarta.annotation.Resource;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 发起人自选 {@link BpmTaskCandidateUserStrategy} 实现类
 *
 * 功能说明：
 * 这是一个流程审批人选择策略的实现类，用于处理"发起人自选审批人"的场景。
 *
 * 业务场景举例：
 * 当员工发起请假申请时，可以自己选择由哪个领导来审批这个请假，
 * 而不是系统自动指定审批人。这样更加灵活。
 *
 * 工作原理：
 * 1. 流程发起时，发起人会选择一个或多个审批人
 * 2. 这些审批人信息会保存在流程变量中
 * 3. 当流程执行到某个审批节点时，从流程变量中读取发起人选择的审批人
 * 4. 将这些审批人作为当前任务的候选人
 *
 * @author 芋道源码
 */
@Component // Spring组件注解，表示这是一个Spring管理的Bean
public class BpmTaskCandidateStartUserSelectStrategy extends AbstractBpmTaskCandidateDeptLeaderStrategy {

    // 流程实例服务，用于查询流程实例信息
    @Resource // 依赖注入注解，Spring会自动注入这个服务
    @Lazy // 延迟加载，避免循环依赖（即两个Bean互相依赖导致的初始化问题）
    private BpmProcessInstanceService processInstanceService;

    /**
     * 获取当前策略的类型
     *
     * @return 返回"发起人自选"策略枚举
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.START_USER_SELECT;
    }

    /**
     * 验证策略参数是否有效
     *
     * 说明：由于"发起人自选"策略不需要额外的配置参数，所以这个方法为空
     * 例如：其他策略可能需要配置"部门ID"或"角色ID"等参数，但自选策略不需要
     *
     * @param param 策略参数（本策略中不使用）
     */
    @Override
    public void validateParam(String param) {}

    /**
     * 判断策略是否需要参数
     *
     * @return false 表示不需要参数配置
     */
    @Override
    public boolean isParamRequired() {
        return false;
    }

    /**
     * 根据执行中的任务计算审批人列表
     *
     * 使用场景：当流程正在执行，到达某个审批节点时调用此方法
     *
     * 执行步骤：
     * 1. 根据流程实例ID获取流程实例对象
     * 2. 从流程实例中获取发起人选择的所有审批人信息（Map结构）
     * 3. 根据当前活动节点ID，从Map中获取对应的审批人列表
     * 4. 返回审批人ID集合
     *
     * @param execution 流程执行对象，包含当前流程的执行信息（如流程实例ID、当前节点ID等）
     * @param param 策略参数（本策略中不使用）
     * @return 审批人用户ID集合（使用LinkedHashSet保证顺序且不重复）
     */
    @Override
    public LinkedHashSet<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        // 第一步：根据流程实例ID查询流程实例对象
        ProcessInstance processInstance = processInstanceService.getProcessInstance(execution.getProcessInstanceId());
        // 断言：流程实例必须存在，否则抛出异常
        Assert.notNull(processInstance, "流程实例({})不能为空", execution.getProcessInstanceId());

        // 第二步：从流程实例中获取发起人自选的审批人信息
        // 数据结构：Map<节点ID, 审批人ID列表>
        // 例如：{"task1": [1, 2, 3], "task2": [4, 5]}
        Map<String, List<Long>> startUserSelectAssignees = FlowableUtils.getStartUserSelectAssignees(processInstance);
        // 断言：自选审批人信息必须存在，否则抛出异常
        Assert.notNull(startUserSelectAssignees, "流程实例({}) 的发起人自选审批人不能为空",
                execution.getProcessInstanceId());

        // 第三步：根据当前活动节点ID，获取该节点对应的审批人列表
        List<Long> assignees = startUserSelectAssignees.get(execution.getCurrentActivityId());

        // 第四步：将List转换为LinkedHashSet返回
        // 如果审批人列表不为空，则转换为LinkedHashSet；否则返回空的LinkedHashSet
        return CollUtil.isNotEmpty(assignees) ? new LinkedHashSet<>(assignees) : Sets.newLinkedHashSet();
    }

    /**
     * 根据活动节点计算审批人列表（用于流程预测或模拟）
     *
     * 使用场景：在流程还未执行到某个节点时，需要预先知道该节点的审批人
     * 例如：显示流程预览图，展示每个节点可能的审批人
     *
     * 执行步骤：
     * 1. 检查流程变量是否为空
     * 2. 从流程变量中获取发起人选择的所有审批人信息
     * 3. 根据指定的活动节点ID，获取对应的审批人列表
     * 4. 返回审批人ID集合
     *
     * @param bpmnModel BPMN模型对象（本方法中未使用，但接口要求）
     * @param activityId 活动节点ID（要查询的节点）
     * @param param 策略参数（本策略中不使用）
     * @param startUserId 流程发起人ID（本方法中未使用）
     * @param processDefinitionId 流程定义ID（本方法中未使用）
     * @param processVariables 流程变量Map，包含发起人选择的审批人信息
     * @return 审批人用户ID集合（使用LinkedHashSet保证顺序且不重复）
     */
    @Override
    public LinkedHashSet<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId, String param,
                                                        Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        // 第一步：检查流程变量是否为空，如果为空则返回空集合
        if (processVariables == null) {
            return Sets.newLinkedHashSet();
        }

        // 第二步：从流程变量中获取发起人自选的审批人信息
        // 数据结构：Map<节点ID, 审批人ID列表>
        Map<String, List<Long>> startUserSelectAssignees = FlowableUtils.getStartUserSelectAssignees(processVariables);
        // 如果获取不到自选审批人信息，返回空集合
        if (startUserSelectAssignees == null) {
            return Sets.newLinkedHashSet();
        }

        // 第三步：根据指定的活动节点ID，获取该节点对应的审批人列表
        List<Long> assignees = startUserSelectAssignees.get(activityId);

        // 第四步：将List转换为LinkedHashSet返回
        // 如果审批人列表不为空，则转换为LinkedHashSet；否则返回空的LinkedHashSet
        return CollUtil.isNotEmpty(assignees) ? new LinkedHashSet<>(assignees) : Sets.newLinkedHashSet();
    }

}
