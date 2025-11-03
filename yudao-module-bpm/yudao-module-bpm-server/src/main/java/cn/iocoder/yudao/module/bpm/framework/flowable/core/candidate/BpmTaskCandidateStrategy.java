package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate;

import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;

import java.util.Map;
import java.util.Set;

/**
 * BPM 任务候选人策略接口
 * <p>
 * 该接口用于定义在 Flowable 流程中如何动态计算某个用户任务（UserTask）的候选用户（即可能被分配执行该任务的用户）。
 * 不同的策略对应不同的分配逻辑，例如按角色、部门、发起人、自定义规则等。
 * <p>
 * 实现该接口的类应注册为 Spring Bean，并通过策略枚举 {@link BpmTaskCandidateStrategyEnum} 进行唯一标识。
 *
 * @author 芋道源码
 */
public interface BpmTaskCandidateStrategy {

    /**
     * 返回当前策略实现所对应的枚举值
     * <p>
     * 该枚举值用于在流程定义或运行时匹配具体的策略实现（如通过策略工厂根据枚举获取对应 Strategy）。
     *
     * @return 对应的策略枚举项，不可为 null
     */
    BpmTaskCandidateStrategyEnum getStrategy();

    /**
     * 验证传入的策略参数是否合法
     * <p>
     * 该参数通常来自流程定义中任务节点的扩展属性（例如 XML 中配置的 assignee 或 candidateUsers 的表达式或标识符）。
     * 若参数无效（如格式错误、ID 不存在等），应抛出异常（如 IllegalArgumentException）。
     *
     * @param param 策略所需的输入参数（如角色编码、部门 ID、表达式字符串等）
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    void validateParam(String param);

    /**
     * 判断当前策略是否要求必须提供参数
     * <p>
     * 默认为 true，即大多数策略都需要参数（例如“按角色分配”需要角色编码）。
     * 若策略可无参运行（例如“分配给流程发起人”），可重写此方法返回 false。
     *
     * @return true 表示参数必须提供；false 表示参数可选
     */
    default boolean isParamRequired() {
        return true;
    }

    /**
     * 核心方法：根据策略参数计算出候选用户 ID 集合
     * <p>
     * 这是最基础的计算逻辑，仅依赖静态参数（如角色ID、部门ID等），不依赖流程上下文。
     * 默认实现抛出异常，强制子类实现具体逻辑。
     * <p>
     * 注意：此方法适用于可静态解析的场景（如预览候选人、静态分配规则）。
     *
     * @param param 策略参数（由 validateParam 验证过）
     * @return 候选用户的用户 ID 集合（Long 类型），不能为空，但可为空集合
     * @throws UnsupportedOperationException 若子类未实现此方法且未重写其他 calculateUsers 方法
     */
    default Set<Long> calculateUsers(String param) {
        throw new UnsupportedOperationException("该分配方法未实现，请检查！");
    }

    /**
     * 根据当前任务执行上下文（DelegateExecution）动态计算候选用户
     * <p>
     * 使用场景：在流程运行时，任务即将创建时调用（例如在任务监听器中）。
     * 此时可访问流程变量、当前执行路径、用户信息等动态上下文。
     * <p>
     * 默认实现委托给 {@link #calculateUsers(String)}，适用于无需上下文的策略。
     * 若策略需依赖流程变量或执行上下文（如“上一审批人”、“发起人直属领导”），应重写此方法。
     *
     * @param execution Flowable 的执行上下文对象，可获取流程变量、当前任务等信息
     * @param param     策略参数（可能为表达式，如 "${applyUserId}"，但通常已在上游解析）
     * @return 候选用户 ID 集合
     */
    default Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        return calculateUsers(param);
    }

    /**
     * 根据流程模型和活动定义（非运行时）计算候选用户
     * <p>
     * 使用场景：流程尚未启动或任务未创建，但需预览某节点的潜在审批人（如流程图渲染、审批人预览功能）。
     * 因无运行时上下文，需额外传入流程定义信息和模拟变量。
     * <p>
     * 默认实现同样委托给 {@link #calculateUsers(String)}，适用于静态策略。
     * 若策略依赖流程发起人或模拟变量（如“发起人部门负责人”），应重写此方法并利用传入参数。
     *
     * @param bpmnModel           BPMN 流程模型对象（可解析节点属性）
     * @param activityId          当前活动（用户任务）的 ID（对应 BPMN XML 中的 id）
     * @param param               节点配置的策略参数
     * @param startUserId         流程拟发起人的用户 ID（用于模拟场景）
     * @param processDefinitionId 流程定义 ID（可用于查询流程定义扩展属性）
     * @param processVariables    模拟的流程变量（Map），用于表达式求值或逻辑判断
     * @return 候选用户 ID 集合
     */
    @SuppressWarnings("unused")
    default Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId, String param,
                                               Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        return calculateUsers(param);
    }

}