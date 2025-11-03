package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.other;

import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.impl.javax.el.PropertyNotFoundException;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 基于表达式（Expression）的流程任务候选人策略实现类。
 *
 * <p>该策略允许在 BPMN 流程定义中通过 EL 表达式动态指定任务的候选人（通常是用户 ID 列表）。
 * 例如：param 可以是 ${deptManagerIds}，Flowable 引擎在运行时会从流程上下文变量中解析该表达式，获取实际的用户 ID 集合。</p>
 *
 * @author 芋道源码
 */
@Component
@Slf4j
public class BpmTaskCandidateExpressionStrategy implements BpmTaskCandidateStrategy {

    /**
     * 返回本策略对应的枚举值，用于在系统中标识该策略类型。
     *
     * @return 枚举值 BpmTaskCandidateStrategyEnum.EXPRESSION
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.EXPRESSION;
    }

    /**
     * 校验传入的参数（即表达式字符串）是否合法。
     *
     * <p>由于表达式的内容高度动态（依赖运行时变量），无法在定义阶段进行有效校验，
     * 因此此处不做任何校验逻辑。</p>
     *
     * @param param 表达式字符串，例如 "${assigneeList}"。
     */
    @Override
    public void validateParam(String param) {
        // 表达式内容依赖运行时上下文，无法静态校验，故此处留空
    }

    /**
     * 在流程实际运行时，根据当前执行上下文（DelegateExecution）和表达式参数，动态计算任务的候选人用户 ID 集合。
     *
     * <p>调用 FlowableUtils 解析表达式，将结果统一转换为 Long 类型的用户 ID 集合。</p>
     *
     * @param execution 当前流程执行上下文，包含变量、执行路径等信息
     * @param param     表达式字符串，如 "${userIds}"
     * @return 候选用户 ID 的去重有序集合（LinkedHashSet 保证顺序）
     */
    @Override
    public Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        // 使用 Flowable 的表达式引擎，基于 execution 上下文解析表达式
        Object result = FlowableUtils.getExpressionValue(execution, param);
        // 将解析结果（可能是单个值、List、Set、数组等）统一转换为 Set<Long>
        return CollectionUtils.toLinkedHashSet(Long.class, result);
    }

    /**
     * 在流程尚未运行时（如流程设计预览、流程预测场景），根据 BPMN 模型、流程变量等静态信息，
     * 尝试预测某个任务节点的候选人用户。
     *
     * <p>由于缺少完整的执行上下文（如 execution 对象），仅能基于提供的流程变量进行表达式解析。
     * 若表达式依赖不存在的变量或 execution 相关属性（如 ${execution.getVariable('xxx')}），
     * 则会抛出 PropertyNotFoundException，此时应忽略异常并返回空集合，表示“无法预测”。</p>
     *
     * @param bpmnModel           BPMN 模型对象（本方法未直接使用，保留为扩展接口参数）
     * @param activityId          当前任务节点 ID（本方法未直接使用，保留为扩展接口参数）
     * @param param               表达式字符串
     * @param startUserId         流程启动人 ID（本方法未使用）
     * @param processDefinitionId 流程定义 ID（本方法未使用）
     * @param processVariables    流程变量快照（用于表达式解析的上下文）
     * @return 候选用户 ID 集合；若表达式无法解析（因缺少变量），则返回空集合
     */
    @Override
    public Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId, String param,
                                              Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        // 若外部未传入流程变量，则使用空 map 避免 NPE
        Map<String, Object> variables = processVariables == null ? new HashMap<>() : processVariables;
        try {
            // 使用流程变量作为上下文解析表达式
            Object result = FlowableUtils.getExpressionValue(variables, param);
            return CollectionUtils.toLinkedHashSet(Long.class, result);
        } catch (FlowableException ex) {
            // 特别处理 PropertyNotFoundException：说明表达式引用了不存在的变量（如 execution 或未设置的流程变量）
            // 此时无法预测候选人，返回空集合，表示“跳过预测”
            if (ex.getCause() != null && ex.getCause() instanceof PropertyNotFoundException) {
                return Sets.newHashSet();
            }
            // 其他异常视为严重错误，记录日志并重新抛出
            log.error("[calculateUsersByActivity][表达式({}) 变量({}) 解析报错]", param, variables, ex);
            throw ex;
        }
    }

}