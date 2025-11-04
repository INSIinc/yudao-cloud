package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.form;

import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.user.BpmTaskCandidateUserStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 表单内用户字段 {@link BpmTaskCandidateUserStrategy} 实现类
 *
 * 功能说明：
 * 这个类用于从工作流表单中获取用户字段，并根据该字段确定任务的候选人（即谁可以处理这个任务）
 *
 * 使用场景举例：
 * 假设有一个请假审批流程，表单中有一个"审批人"字段，该字段的值是用户ID
 * 这个策略类就会读取表单中"审批人"字段的值，然后将该用户设置为任务的候选人
 *
 * @author jason
 */
@Component // Spring注解：将这个类注册为Spring容器管理的Bean（组件）
public class BpmTaskCandidateFormUserStrategy implements BpmTaskCandidateStrategy {

    /**
     * 获取策略类型
     *
     * 说明：每个策略类都有一个唯一的类型标识，用于区分不同的候选人计算策略
     * 这里返回的是"表单用户"类型，表示从表单字段中获取用户
     *
     * @return 返回策略枚举值：FORM_USER（表单用户）
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.FORM_USER;
    }

    /**
     * 校验参数是否合法
     *
     * 说明：在使用这个策略之前，需要先验证传入的参数是否正确
     * 参数param是表单中的字段名称，比如 "approver"、"reviewUser" 等
     *
     * @param param 表单字段名称，不能为空
     * @throws IllegalArgumentException 如果参数为空，则抛出异常
     */
    @Override
    public void validateParam(String param) {
        // 使用断言工具检查参数不能为空，如果为空则抛出异常并显示提示信息
        Assert.notEmpty(param, "表单内用户字段不能为空");
    }

    /**
     * 根据任务执行上下文计算候选用户
     *
     * 使用场景：在任务实际执行过程中调用
     *
     * 工作原理：
     * 1. 从任务执行上下文（execution）中获取指定字段名（param）的值
     * 2. 该值可能是单个用户ID，也可能是多个用户ID的集合
     * 3. 将获取到的值转换为用户ID的集合（Set<Long>）并返回
     *
     * 举例：
     * 假设表单中有字段 "approver": 123
     * 调用 calculateUsersByTask(execution, "approver")
     * 就会返回包含用户ID 123 的集合
     *
     * @param execution 任务执行上下文，包含了任务运行时的所有变量和状态
     * @param param 表单字段名称，用于从上下文中获取对应的值
     * @return 返回用户ID集合，这些用户将成为任务的候选人
     */
    @Override
    public Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        // 从执行上下文中获取指定字段的值（可能是Long、List<Long>、String等类型）
        Object result = execution.getVariable(param);
        // 使用工具类将结果转换为Long类型的LinkedHashSet集合
        // LinkedHashSet的特点：有序且不重复
        return CollectionUtils.toLinkedHashSet(Long.class, result);
    }

    /**
     * 根据流程定义和变量计算候选用户
     *
     * 使用场景：在流程启动前或流程分析时调用，用于预测某个节点的候选人
     *
     * 工作原理：
     * 1. 从流程变量（processVariables）中获取指定字段名（param）的值
     * 2. 如果流程变量为空，则返回空集合
     * 3. 否则将获取到的值转换为用户ID集合并返回
     *
     * 举例：
     * 在流程启动时，可以传入表单数据作为流程变量：{"approver": [123, 456]}
     * 调用此方法就能提前知道某个审批节点的候选人是用户123和456
     *
     * @param bpmnModel 流程模型，包含流程的完整定义信息
     * @param activityId 活动节点ID，表示要计算哪个节点的候选人
     * @param param 表单字段名称
     * @param startUserId 流程发起人的用户ID
     * @param processDefinitionId 流程定义ID
     * @param processVariables 流程变量Map，包含表单数据等信息
     * @return 返回用户ID集合，这些用户将成为该节点任务的候选人
     */
    @Override
    public Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId,
                                              String param, Long startUserId, String processDefinitionId,
                                              Map<String, Object> processVariables) {
        // 从流程变量中获取指定字段的值，如果流程变量为null则返回null
        Object result = processVariables == null ? null : processVariables.get(param);
        // 将结果转换为Long类型的LinkedHashSet集合
        return CollectionUtils.toLinkedHashSet(Long.class, result);
    }

}
