package cn.iocoder.yudao.module.bpm.framework.flowable.core.enums;

import org.flowable.engine.runtime.ProcessInstance;

/**
 * BPMN 流程变量通用常量类，用于统一管理流程实例（Process Instance）和任务（Task）中使用的变量名称。
 * 所有变量名均以 PROCESS_ 或 TASK_ 为前缀，明确区分作用域（流程实例级别 vs 任务级别）。
 *
 * @author 芋道源码
 */
public class BpmnVariableConstants {

    /**
     * 流程实例变量：表示当前流程实例的整体状态（如：审批中、已通过、已驳回等）。
     * 该变量存储于流程实例级别，可通过 {@link ProcessInstance#getProcessVariables()} 获取。
     */
    public static final String PROCESS_INSTANCE_VARIABLE_STATUS = "PROCESS_STATUS";

    /**
     * 流程实例变量：记录流程被拒绝（驳回）时的说明理由。
     * 注意：通常仅在审批不通过时设置；审批通过时一般不记录理由。
     * 该变量对整个流程可见，可用于流程跟踪或日志展示。
     */
    public static final String PROCESS_INSTANCE_VARIABLE_REASON = "PROCESS_REASON";

    /**
     * 流程实例变量：发起人在启动流程时手动选择的审批人映射表。
     * 键为任务节点定义 ID（如 userTask1），值为被选中的用户 ID 列表。
     * 适用于“发起人指定审批人”策略（对应 {@link BpmTaskCandidateStrategyEnum#START_USER_SELECT}）。
     */
    public static final String PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES = "PROCESS_START_USER_SELECT_ASSIGNEES";

    /**
     * 流程实例变量：某审批人在审批过程中动态指定的后续审批人映射表。
     * 键为下一个任务节点定义 ID，值为被指定的用户 ID 列表。
     * 适用于“审批人指定下一级审批人”策略（对应 {@link BpmTaskCandidateStrategyEnum#APPROVE_USER_SELECT}）。
     */
    public static final String PROCESS_INSTANCE_VARIABLE_APPROVE_USER_SELECT_ASSIGNEES = "PROCESS_APPROVE_USER_SELECT_ASSIGNEES";

    /**
     * 流程实例变量：记录流程实际发起人的用户 ID。
     * 用于权限控制、通知、或流程日志追踪。
     */
    public static final String PROCESS_INSTANCE_VARIABLE_START_USER_ID = "PROCESS_START_USER_ID";

    /**
     * 流程实例变量模板：用于标识某个节点是否为“被驳回后退回”的节点。
     * 实际变量名为 "RETURN_FLAG_{节点ID}"，例如 "RETURN_FLAG_userTask2"。
     * 场景：当流程被驳回至发起节点，且发起人与审批人为同一人时，
     *      为避免系统自动通过该任务，可通过此标志判断是否应跳过自动审批逻辑。
     */
    public static final String PROCESS_INSTANCE_VARIABLE_RETURN_FLAG = "RETURN_FLAG_%s";

    /**
     * 流程实例变量前缀：用于退回操作中辅助节点预测。
     * 实际变量名为 "NEED_SIMULATE_TASK_{节点定义ID}"，例如 "NEED_SIMULATE_TASK_userTask3"。
     * 背景：Flowable 在执行退回（jump）操作时，路径预测可能不准确；
     *      通过在流程变量中显式记录“需要模拟执行到的目标节点”，可提升路径计算准确性。
     */
    public static final String PROCESS_INSTANCE_VARIABLE_NEED_SIMULATE_PREFIX = "NEED_SIMULATE_TASK_";

    /**
     * 流程实例变量：是否启用 Flowable 的 SkipExpression（跳过表达式）功能。
     * 值必须为 "true" 才能激活 BPMN 图中配置的 skipExpression。
     * 默认情况下 Flowable 不启用该功能，需显式设置此变量为 true。
     * 参考：https://blog.csdn.net/weixin_42065235/article/details/126039993
     */
    public static final String PROCESS_INSTANCE_SKIP_EXPRESSION_ENABLED = "_FLOWABLE_SKIP_EXPRESSION_ENABLED";

    /**
     * 流程实例变量：是否跳过“发起人节点”（如首节点为发起人自审）。
     * 适用于某些业务场景中，发起人无需审批自己提交的流程，
     * 可通过此变量配合 SkipExpression 实现自动跳过。
     */
    public static final String PROCESS_INSTANCE_VARIABLE_SKIP_START_USER_NODE = "PROCESS_SKIP_START_USER_NODE";

    /**
     * 流程实例变量（非持久化）：流程实际开始时间（格式化后的时间字符串，如 "2025-11-03 10:00:00"）。
     * 主要用于流程标题模板等展示场景（例如：请假申请_张三_2025-11-03）。
     * 注意：该变量通常不会持久化到数据库，仅在运行时临时使用。
     */
    public static final String PROCESS_START_TIME = "PROCESS_START_TIME";

    /**
     * 流程实例变量：流程定义的名称（即 BPMN 文件中 process 的 name 属性）。
     * 用于前端展示或日志记录，避免每次查询流程定义表。
     */
    public static final String PROCESS_DEFINITION_NAME = "PROCESS_DEFINITION_NAME";

    /**
     * 任务本地变量：表示当前任务的状态（如：待处理、已处理、已撤回等）。
     * 该变量仅作用于单个任务，通过 {@link org.flowable.task.api.Task#getTaskLocalVariables()} 获取。
     * 使用“本地变量”而非流程变量，避免不同任务间状态互相干扰。
     */
    public static final String TASK_VARIABLE_STATUS = "TASK_STATUS";

    /**
     * 任务本地变量：记录当前任务的审批意见或理由（如“资料不全”、“同意”等）。
     * 无论审批通过或驳回，均可记录理由，便于审计和追溯。
     */
    public static final String TASK_VARIABLE_REASON = "TASK_REASON";

    /**
     * 任务本地变量：电子签名图片的访问 URL。
     * 用于支持需要手写签名的审批场景，签名完成后上传图片并保存 URL。
     */
    public static final String TASK_SIGN_PIC_URL = "TASK_SIGN_PIC_URL";

}