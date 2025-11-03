package cn.iocoder.yudao.module.bpm.framework.flowable.core.enums;

import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelTypeEnum;

/**
 * BPMN XML 中使用的各类自定义扩展常量定义。
 * 该接口集中管理 Flowable BPMN 模型中通过 Extension Elements（扩展元素）或自定义属性（Custom Properties）
 * 注入的元数据字段，用于在流程运行时或建模阶段支持高级审批逻辑、权限控制、超时处理、按钮配置等功能。
 *
 * 这些常量通常用于：
 * - 在 BPMN 设计器中保存自定义配置；
 * - 在流程解析或执行时读取扩展属性，实现业务逻辑（如动态指派审批人、处理拒绝流程等）；
 * - 与前端表单、审批界面联动（如字段权限、按钮显示等）。
 *
 * @author 芋道源码
 */
public interface BpmnModelConstants {

    /**
     * BPMN 文件的标准后缀名，用于标识 Flowable 流程定义文件。
     */
    String BPMN_FILE_SUFFIX = ".bpmn";

    /**
     * Flowable BPMN 模型的默认命名空间。
     * 此命名空间用于识别符合 Flowable 规范的 BPMN 2.0 XML 文件。
     */
    String NAMESPACE = "http://flowable.org/bpmn";

    // ==================== 用户任务（UserTask）扩展属性 ====================

    /**
     * 用户任务中用于指定“候选人策略”的自定义属性名。
     * 该策略决定如何动态计算该任务的候选人（如：按角色、部门、表达式等）。
     * 示例值可能为："role", "dept", "expression", "custom" 等。
     */
    String USER_TASK_CANDIDATE_STRATEGY = "candidateStrategy";

    /**
     * 与 {@link #USER_TASK_CANDIDATE_STRATEGY} 配合使用的参数。
     * 用于传递策略所需的具体参数，例如角色编码、部门 ID、表达式字符串等。
     */
    String USER_TASK_CANDIDATE_PARAM = "candidateParam";

    /**
     * 用户任务中用于标识“审批类型”的属性。
     * 例如：普通审批、会签、或签等。
     * 可与 {@link BpmModelTypeEnum#SIMPLE} 配合，区分审批节点与普通办理节点。
     */
    String USER_TASK_APPROVE_TYPE = "approveType";

    /**
     * 用户任务中用于标识“审批方式”的属性。
     * 例如：逐级审批、并行审批等，影响任务分配与完成逻辑。
     */
    String USER_TASK_APPROVE_METHOD = "approveMethod";

    /**
     * 用户任务是否启用“电子签名”功能。
     * 若值为 "true"，则在任务完成时需收集用户签名（通常用于合同、重要审批场景）。
     */
    String SIGN_ENABLE = "signEnable";

    /**
     * 用户任务是否强制要求填写“审批意见”。
     * 若值为 "true"，则用户在完成任务时必须输入理由，否则拒绝提交。
     */
    String REASON_REQUIRE = "reasonRequire";

    /**
     * 节点类型标识。
     * 仅在 {@link BpmModelTypeEnum#SIMPLE} 类型的流程中，用于区分该 UserTask 是“审批节点”还是“办理节点”。
     * 可用于前端渲染不同 UI，或后端执行不同逻辑。
     */
    String NODE_TYPE = "nodeType";

    // ==================== 任务分配与异常处理 ====================

    /**
     * 当用户任务的审批人与流程发起人相同时，应如何处理。
     * 例如：自动跳过、仍需处理、转交他人等。
     * 该属性指定处理策略的类型。
     */
    String USER_TASK_ASSIGN_START_USER_HANDLER_TYPE = "assignStartUserHandlerType";

    /**
     * 当用户任务的审批人为空（即动态分配后无有效用户）时的处理策略。
     * 例如：自动完成、转交管理员、指定替补人员等。
     */
    String USER_TASK_ASSIGN_EMPTY_HANDLER_TYPE = "assignEmptyHandlerType";

    /**
     * 配合 {@link #USER_TASK_ASSIGN_EMPTY_HANDLER_TYPE} 使用，
     * 当处理策略为“指定用户”时，此属性存储替补用户的 ID 列表（以逗号分隔或 JSON 数组形式）。
     */
    String USER_TASK_ASSIGN_USER_IDS = "assignEmptyUserIds";

    /**
     * 用户任务的超时处理策略类型。
     * 例如：自动通过、自动拒绝、转交他人、触发通知等。
     */
    String USER_TASK_TIMEOUT_HANDLER_TYPE = "timeoutHandlerType";

    // ==================== 拒绝/退回逻辑 ====================

    /**
     * 用户任务被拒绝时的处理策略。
     * 例如：退回上一节点、终止流程、转交发起人等。
     */
    String USER_TASK_REJECT_HANDLER_TYPE = "rejectHandlerType";

    /**
     * 当拒绝处理策略为“退回指定节点”时，此属性记录目标任务节点的 ID（BPMN 元素 ID）。
     * 用于流程引擎执行跳转逻辑。
     */
    String USER_TASK_REJECT_RETURN_TASK_ID = "rejectReturnTaskId";

    // ==================== 子流程与多实例 ====================

    /**
     * 子流程（CallActivity 或嵌入子流程）的多实例数据来源类型。
     * 用于动态决定子流程应被实例化的次数及输入数据来源（如：来自变量、接口返回等）。
     */
    String CHILD_PROCESS_MULTI_INSTANCE_SOURCE_TYPE = "childProcessMultiInstanceSourceType";

    // ==================== 表单字段权限控制 ====================

    /**
     * BPMN 扩展元素名称，用于定义表单字段的权限规则。
     * 通常作为 UserTask 下的 Extension Element 存在。
     * 例如：
     * <flowable:executionListener>
     *   <flowable:fieldsPermission field="salary" permission="readonly"/>
     * </flowable:executionListener>
     */
    String FORM_FIELD_PERMISSION_ELEMENT = "fieldsPermission";

    /**
     * {@link #FORM_FIELD_PERMISSION_ELEMENT} 元素中的属性，表示受控的表单字段名。
     */
    String FORM_FIELD_PERMISSION_ELEMENT_FIELD_ATTRIBUTE = "field";

    /**
     * {@link #FORM_FIELD_PERMISSION_ELEMENT} 元素中的属性，表示对该字段的权限类型。
     * 常见值如："readonly", "hidden", "editable" 等。
     */
    String FORM_FIELD_PERMISSION_ELEMENT_PERMISSION_ATTRIBUTE = "permission";

    // ==================== 节点操作按钮配置 ====================

    /**
     * BPMN 扩展元素名称，用于配置审批节点的操作按钮（如：同意、拒绝、转办、加签等）。
     * 每个按钮可独立配置是否显示、名称、是否启用等。
     */
    String BUTTON_SETTING_ELEMENT = "buttonsSetting";

    /**
     * 按钮配置元素中的按钮唯一标识（如："approve", "reject", "transfer"）。
     */
    String BUTTON_SETTING_ELEMENT_ID_ATTRIBUTE = "id";

    /**
     * 按钮在前端界面上显示的中文/本地化名称。
     */
    String BUTTON_SETTING_ELEMENT_DISPLAY_NAME_ATTRIBUTE = "displayName";

    /**
     * 控制该按钮是否启用（"true"/"false"）。
     * 可用于动态隐藏某些操作（如：仅允许拒绝，不允许转办）。
     */
    String BUTTON_SETTING_ELEMENT_ENABLE_ATTRIBUTE = "enable";

    // ==================== 触发器（事件/定时等） ====================

    /**
     * 扩展属性，用于指定触发器的类型。
     * 例如：定时触发、HTTP 回调触发、WebSocket 通知等。
     */
    String TRIGGER_TYPE = "triggerType";

    /**
     * 触发器所需的参数，如定时表达式（cron）、回调 URL、事件类型等。
     */
    String TRIGGER_PARAM = "triggerParam";

    // ==================== 特殊节点 ID ====================

    /**
     * BPMN 流程中起始事件（Start Event）的标准节点 ID。
     * 用于流程启动逻辑识别或流程图解析。
     */
    String START_EVENT_NODE_ID = "StartEvent";

    /**
     * 自定义的“发起人节点”ID，通常用于标识流程中代表“流程发起人”的虚拟 UserTask。
     * 该节点可能不实际执行，仅用于流程图展示或权限计算。
     */
    String START_USER_NODE_ID = "StartUserNode";

    // ==================== 边界事件扩展 ====================

    /**
     * 边界事件（Boundary Event）的扩展属性，用于标识其类型。
     * 例如：超时边界事件、错误边界事件等，便于运行时区分处理逻辑。
     */
    String BOUNDARY_EVENT_TYPE = "boundaryEventType";

}