# BPM 常量与枚举

## 📖 概述

本目录包含 BPM 模块中使用的**常量定义**和**枚举类型**，用于统一管理 BPMN 流程定义、流程变量、扩展属性等关键配置项。

### 核心功能

✅ **统一管理**：集中定义所有常量，避免魔法值（Magic Number/String）
✅ **提高可维护性**：修改常量只需在一处更新
✅ **增强可读性**：常量名具有明确的业务含义
✅ **类型安全**：枚举类提供编译期类型检查
✅ **文档化**：详细的注释说明每个常量的用途和场景

---

## 📦 文件列表

| 文件名 | 类型 | 功能描述 |
|--------|------|---------|
| `BpmnModelConstants` | 常量接口 | BPMN 模型扩展属性常量 |
| `BpmnVariableConstants` | 常量类 | 流程变量和任务变量名称常量 |
| `BpmTaskCandidateStrategyEnum` | 枚举类 | 任务候选人策略类型 |

---

## 🏗️ BpmnModelConstants

### 功能说明

定义 BPMN XML 中使用的**扩展元素**和**自定义属性**常量，这些属性通过 Flowable 的 Extension Elements 机制注入到流程定义中。

### 常量分类

#### 1. 基础配置

```java
// BPMN 文件后缀
String BPMN_FILE_SUFFIX = ".bpmn";

// Flowable 命名空间
String NAMESPACE = "http://flowable.org/bpmn";
```

**用途**：
- 文件识别和验证
- BPMN XML 命名空间声明

---

#### 2. 用户任务扩展属性

**候选人策略配置**：

```java
// 候选人策略类型
String USER_TASK_CANDIDATE_STRATEGY = "candidateStrategy";

// 候选人策略参数
String USER_TASK_CANDIDATE_PARAM = "candidateParam";
```

**BPMN 配置示例**：

```xml
<userTask id="task_approve" name="经理审批">
  <extensionElements>
    <flowable:candidateStrategy>21</flowable:candidateStrategy>
    <flowable:candidateParam>100|1</flowable:candidateParam>
  </extensionElements>
</userTask>
```

**说明**：
- `candidateStrategy`：指定候选人计算策略（如按角色、部门、岗位等）
- `candidateParam`：策略所需的参数（如角色ID、部门ID等）

---

**审批类型与方式**：

```java
// 审批类型（普通审批、会签、或签等）
String USER_TASK_APPROVE_TYPE = "approveType";

// 审批方式（逐级审批、并行审批等）
String USER_TASK_APPROVE_METHOD = "approveMethod";
```

**BPMN 配置示例**：

```xml
<userTask id="task_countersign" name="财务会签">
  <extensionElements>
    <flowable:approveType>2</flowable:approveType>
    <flowable:approveMethod>1</flowable:approveMethod>
  </extensionElements>
</userTask>
```

---

**签名与审批意见**：

```java
// 是否启用电子签名
String SIGN_ENABLE = "signEnable";

// 是否强制填写审批意见
String REASON_REQUIRE = "reasonRequire";
```

**BPMN 配置示例**：

```xml
<userTask id="task_sign" name="合同签署">
  <extensionElements>
    <flowable:signEnable>true</flowable:signEnable>
    <flowable:reasonRequire>true</flowable:reasonRequire>
  </extensionElements>
</userTask>
```

**使用场景**：
- 电子签名：重要合同、协议签署
- 强制意见：关键审批节点（如拒绝时必须说明理由）

---

**节点类型**：

```java
// 节点类型（仅 Simple 模式使用）
String NODE_TYPE = "nodeType";
```

**说明**：
- 在简化流程模式（Simple BPMN）中区分"审批节点"和"办理节点"
- 影响前端 UI 渲染和后端业务逻辑

---

#### 3. 任务分配与异常处理

**发起人相同处理**：

```java
// 审批人与发起人相同时的处理策略
String USER_TASK_ASSIGN_START_USER_HANDLER_TYPE = "assignStartUserHandlerType";
```

**处理策略**：
- **跳过（SKIP）**：自动跳过该节点
- **发起人审批（START_USER_APPROVE）**：仍需发起人审批
- **转交他人（ASSIGN_USER）**：转交给指定用户

**BPMN 配置示例**：

```xml
<userTask id="task_manager" name="部门经理审批">
  <extensionElements>
    <flowable:assignStartUserHandlerType>1</flowable:assignStartUserHandlerType>
    <!-- 1 = SKIP -->
  </extensionElements>
</userTask>
```

---

**候选人为空处理**：

```java
// 候选人为空时的处理策略
String USER_TASK_ASSIGN_EMPTY_HANDLER_TYPE = "assignEmptyHandlerType";

// 指定的替补用户ID列表
String USER_TASK_ASSIGN_USER_IDS = "assignEmptyUserIds";
```

**处理策略**：
- **自动通过（AUTO_APPROVE）**
- **自动拒绝（AUTO_REJECT）**
- **转交指定用户（ASSIGN_USER）**
- **转交流程管理员（ASSIGN_ADMIN）**

**BPMN 配置示例**：

```xml
<userTask id="task_approve" name="审批">
  <extensionElements>
    <flowable:assignEmptyHandlerType>3</flowable:assignEmptyHandlerType>
    <flowable:assignEmptyUserIds>1001,1002</flowable:assignEmptyUserIds>
  </extensionElements>
</userTask>
```

---

**超时处理**：

```java
// 任务超时处理策略
String USER_TASK_TIMEOUT_HANDLER_TYPE = "timeoutHandlerType";
```

**处理策略**：
- **自动通过（AUTO_APPROVE）**
- **自动拒绝（AUTO_REJECT）**
- **发送通知（NOTIFY）**
- **转交上级（ASSIGN_UPPER）**

**BPMN 配置示例**：

```xml
<userTask id="task_approve" name="审批">
  <extensionElements>
    <flowable:timeoutHandlerType>1</flowable:timeoutHandlerType>
  </extensionElements>

  <!-- 结合边界定时事件使用 -->
  <boundaryEvent id="timeout_event" cancelActivity="true" attachedToRef="task_approve">
    <timerEventDefinition>
      <timeDuration>PT24H</timeDuration> <!-- 24小时超时 -->
    </timerEventDefinition>
  </boundaryEvent>
</userTask>
```

---

#### 4. 拒绝/退回逻辑

```java
// 拒绝处理策略
String USER_TASK_REJECT_HANDLER_TYPE = "rejectHandlerType";

// 退回目标节点ID
String USER_TASK_REJECT_RETURN_TASK_ID = "rejectReturnTaskId";
```

**处理策略**：
- **终止流程（FINISH）**
- **退回发起节点（RETURN_START）**
- **退回上一节点（RETURN_PREVIOUS）**
- **退回指定节点（RETURN_USER_TASK）**

**BPMN 配置示例**：

```xml
<userTask id="task_ceo_approve" name="总经理审批">
  <extensionElements>
    <flowable:rejectHandlerType>3</flowable:rejectHandlerType>
    <flowable:rejectReturnTaskId>task_manager_approve</flowable:rejectReturnTaskId>
  </extensionElements>
</userTask>
```

---

#### 5. 子流程与多实例

```java
// 子流程多实例数据来源类型
String CHILD_PROCESS_MULTI_INSTANCE_SOURCE_TYPE = "childProcessMultiInstanceSourceType";
```

**数据来源类型**：
- **数字表单（NUMBER_FORM）**：从表单读取一个数字，作为子流程实例数量
- **多选表单（MULTIPLE_FORM）**：从表单读取一个列表，每个元素对应一个子流程实例

**BPMN 配置示例**：

```xml
<callActivity id="sub_process" name="调用子流程">
  <extensionElements>
    <flowable:childProcessMultiInstanceSourceType>2</flowable:childProcessMultiInstanceSourceType>
  </extensionElements>

  <multiInstanceLoopCharacteristics isSequential="false">
    <loopCardinality>${userList.size()}</loopCardinality>
  </multiInstanceLoopCharacteristics>
</callActivity>
```

---

#### 6. 表单字段权限控制

```java
// 表单字段权限扩展元素
String FORM_FIELD_PERMISSION_ELEMENT = "fieldsPermission";

// 字段名属性
String FORM_FIELD_PERMISSION_ELEMENT_FIELD_ATTRIBUTE = "field";

// 权限类型属性
String FORM_FIELD_PERMISSION_ELEMENT_PERMISSION_ATTRIBUTE = "permission";
```

**权限类型**：
- `readonly`：只读
- `hidden`：隐藏
- `editable`：可编辑

**BPMN 配置示例**：

```xml
<userTask id="task_hr_review" name="HR复核">
  <extensionElements>
    <flowable:fieldsPermission field="salary" permission="readonly" />
    <flowable:fieldsPermission field="bonus" permission="hidden" />
    <flowable:fieldsPermission field="comment" permission="editable" />
  </extensionElements>
</userTask>
```

**业务场景**：

```
审批流程：报销申请
├─ 提交节点：所有字段可编辑
├─ 部门经理审批：金额只读，备注可编辑
├─ 财务审批：金额只读，账户信息可编辑
└─ 归档节点：所有字段只读
```

---

#### 7. 节点操作按钮配置

```java
// 按钮配置扩展元素
String BUTTON_SETTING_ELEMENT = "buttonsSetting";

// 按钮ID属性
String BUTTON_SETTING_ELEMENT_ID_ATTRIBUTE = "id";

// 按钮显示名称属性
String BUTTON_SETTING_ELEMENT_DISPLAY_NAME_ATTRIBUTE = "displayName";

// 按钮是否启用属性
String BUTTON_SETTING_ELEMENT_ENABLE_ATTRIBUTE = "enable";
```

**BPMN 配置示例**：

```xml
<userTask id="task_approve" name="审批">
  <extensionElements>
    <flowable:buttonsSetting id="approve" displayName="同意" enable="true" />
    <flowable:buttonsSetting id="reject" displayName="拒绝" enable="true" />
    <flowable:buttonsSetting id="transfer" displayName="转办" enable="false" />
    <flowable:buttonsSetting id="delegate" displayName="委托" enable="false" />
  </extensionElements>
</userTask>
```

**常见按钮类型**：

| 按钮ID | 显示名称 | 功能 |
|--------|---------|------|
| `approve` | 同意 | 审批通过 |
| `reject` | 拒绝 | 审批驳回 |
| `transfer` | 转办 | 转交他人处理 |
| `delegate` | 委托 | 委托他人审批 |
| `addSign` | 加签 | 增加审批人 |
| `return` | 退回 | 退回上一节点 |

---

#### 8. 触发器配置

```java
// 触发器类型
String TRIGGER_TYPE = "triggerType";

// 触发器参数
String TRIGGER_PARAM = "triggerParam";
```

**触发器类型**：
- **定时触发（TIMER）**：按时间周期触发
- **消息触发（MESSAGE）**：接收外部消息触发
- **信号触发（SIGNAL）**：接收信号事件触发

**BPMN 配置示例**：

```xml
<boundaryEvent id="timer_event" attachedToRef="task_approve">
  <extensionElements>
    <flowable:triggerType>1</flowable:triggerType>
    <flowable:triggerParam>PT24H</flowable:triggerParam> <!-- 24小时 -->
  </extensionElements>
  <timerEventDefinition>
    <timeDuration>PT24H</timeDuration>
  </timerEventDefinition>
</boundaryEvent>
```

---

#### 9. 特殊节点ID

```java
// 起始事件节点ID
String START_EVENT_NODE_ID = "StartEvent";

// 发起人节点ID
String START_USER_NODE_ID = "StartUserNode";
```

**用途**：
- 流程解析时快速定位起始节点
- 流程图渲染时识别特殊节点

---

#### 10. 边界事件扩展

```java
// 边界事件类型
String BOUNDARY_EVENT_TYPE = "boundaryEventType";
```

**边界事件类型**：
- **超时事件（TIMEOUT）**
- **错误事件（ERROR）**
- **取消事件（CANCEL）**

**BPMN 配置示例**：

```xml
<boundaryEvent id="error_event" attachedToRef="service_task">
  <extensionElements>
    <flowable:boundaryEventType>2</flowable:boundaryEventType>
  </extensionElements>
  <errorEventDefinition errorRef="error_payment_failed" />
</boundaryEvent>
```

---

### 使用示例

#### 读取扩展属性

```java
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants.*;

public class BpmnModelUtils {

    /**
     * 解析用户任务的候选人策略
     */
    public static Integer parseCandidateStrategy(FlowElement flowElement) {
        if (!(flowElement instanceof UserTask)) {
            return null;
        }

        UserTask userTask = (UserTask) flowElement;
        Map<String, List<ExtensionElement>> extensionElements = userTask.getExtensionElements();

        // 使用常量读取扩展属性
        List<ExtensionElement> strategyElements = extensionElements.get(USER_TASK_CANDIDATE_STRATEGY);
        if (CollUtil.isEmpty(strategyElements)) {
            return null;
        }

        String strategyStr = strategyElements.get(0).getElementText();
        return Integer.parseInt(strategyStr);
    }

    /**
     * 解析候选人策略参数
     */
    public static String parseCandidateParam(FlowElement flowElement) {
        if (!(flowElement instanceof UserTask)) {
            return null;
        }

        UserTask userTask = (UserTask) flowElement;
        Map<String, List<ExtensionElement>> extensionElements = userTask.getExtensionElements();

        // 使用常量读取扩展属性
        List<ExtensionElement> paramElements = extensionElements.get(USER_TASK_CANDIDATE_PARAM);
        if (CollUtil.isEmpty(paramElements)) {
            return null;
        }

        return paramElements.get(0).getElementText();
    }
}
```

---

## 📊 BpmnVariableConstants

### 功能说明

定义流程实例和任务中使用的**变量名称常量**，统一管理流程变量和任务变量的命名规范。

### 常量分类

#### 1. 流程实例变量

**流程状态与原因**：

```java
// 流程实例状态
public static final String PROCESS_INSTANCE_VARIABLE_STATUS = "PROCESS_STATUS";

// 流程拒绝原因
public static final String PROCESS_INSTANCE_VARIABLE_REASON = "PROCESS_REASON";
```

**使用示例**：

```java
// 设置流程状态
execution.setVariable(PROCESS_INSTANCE_VARIABLE_STATUS, BpmProcessInstanceStatusEnum.RUNNING.getStatus());

// 记录拒绝原因
execution.setVariable(PROCESS_INSTANCE_VARIABLE_REASON, "资料不全，需补充身份证复印件");
```

---

**审批人选择**：

```java
// 发起人自选审批人
public static final String PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES = "PROCESS_START_USER_SELECT_ASSIGNEES";

// 审批人指定下一审批人
public static final String PROCESS_INSTANCE_VARIABLE_APPROVE_USER_SELECT_ASSIGNEES = "PROCESS_APPROVE_USER_SELECT_ASSIGNEES";
```

**数据结构**：

```java
// Map<节点ID, 用户ID列表>
Map<String, List<Long>> assignees = new HashMap<>();
assignees.put("task_manager_approve", Arrays.asList(1001L, 1002L));
assignees.put("task_ceo_approve", Arrays.asList(2001L));

execution.setVariable(PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES, assignees);
```

**业务场景**：

```
场景：请假申请
├─ 发起人选择：张三选择直属领导"李四"审批
│  → PROCESS_START_USER_SELECT_ASSIGNEES = {"task_leader": [1001]}
│
└─ 审批人选择：李四指定"王五"作为下一审批人
   → PROCESS_APPROVE_USER_SELECT_ASSIGNEES = {"task_hr": [2001]}
```

---

**发起人信息**：

```java
// 流程发起人ID
public static final String PROCESS_INSTANCE_VARIABLE_START_USER_ID = "PROCESS_START_USER_ID";
```

**使用示例**：

```java
// 启动流程时设置发起人
Map<String, Object> variables = new HashMap<>();
variables.put(PROCESS_INSTANCE_VARIABLE_START_USER_ID, currentUserId);

ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
    "leave_process", variables
);
```

---

**流程退回标志**：

```java
// 退回标志模板（RETURN_FLAG_{节点ID}）
public static final String PROCESS_INSTANCE_VARIABLE_RETURN_FLAG = "RETURN_FLAG_%s";
```

**使用场景**：

防止审批人与发起人相同时，退回后自动通过。

**示例**：

```java
// 标记 task_start 节点为退回状态
String returnFlag = String.format(PROCESS_INSTANCE_VARIABLE_RETURN_FLAG, "task_start");
execution.setVariable(returnFlag, true);

// 判断是否为退回节点
String returnFlag = String.format(PROCESS_INSTANCE_VARIABLE_RETURN_FLAG, execution.getCurrentActivityId());
Boolean isReturn = execution.getVariable(returnFlag, Boolean.class);
if (Boolean.TRUE.equals(isReturn)) {
    // 不自动跳过，需人工处理
}
```

---

**流程模拟相关**：

```java
// 需要模拟的任务节点前缀（NEED_SIMULATE_TASK_{节点ID}）
public static final String PROCESS_INSTANCE_VARIABLE_NEED_SIMULATE_PREFIX = "NEED_SIMULATE_TASK_";
```

**用途**：

在流程退回（Jump）时，辅助 Flowable 正确计算执行路径。

---

**SkipExpression 功能**：

```java
// 启用 SkipExpression
public static final String PROCESS_INSTANCE_SKIP_EXPRESSION_ENABLED = "_FLOWABLE_SKIP_EXPRESSION_ENABLED";

// 跳过发起人节点
public static final String PROCESS_INSTANCE_VARIABLE_SKIP_START_USER_NODE = "PROCESS_SKIP_START_USER_NODE";
```

**使用示例**：

```java
// 启用 SkipExpression 功能
Map<String, Object> variables = new HashMap<>();
variables.put(PROCESS_INSTANCE_SKIP_EXPRESSION_ENABLED, true);
variables.put(PROCESS_INSTANCE_VARIABLE_SKIP_START_USER_NODE, true);

ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
    "leave_process", variables
);
```

**BPMN 配置**：

```xml
<userTask id="task_start_user" name="发起人节点">
  <extensionElements>
    <flowable:skipExpression>
      ${execution.getVariable('PROCESS_SKIP_START_USER_NODE') == true}
    </flowable:skipExpression>
  </extensionElements>
</userTask>
```

---

**流程元数据**：

```java
// 流程开始时间（格式化字符串）
public static final String PROCESS_START_TIME = "PROCESS_START_TIME";

// 流程定义名称
public static final String PROCESS_DEFINITION_NAME = "PROCESS_DEFINITION_NAME";
```

**使用示例**：

```java
// 设置流程元数据
SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
variables.put(PROCESS_START_TIME, sdf.format(new Date()));
variables.put(PROCESS_DEFINITION_NAME, "请假申请流程");
```

---

#### 2. 任务本地变量

```java
// 任务状态
public static final String TASK_VARIABLE_STATUS = "TASK_STATUS";

// 任务审批意见
public static final String TASK_VARIABLE_REASON = "TASK_REASON";

// 电子签名图片URL
public static final String TASK_SIGN_PIC_URL = "TASK_SIGN_PIC_URL";
```

**使用示例**：

```java
// 完成任务时设置本地变量
taskService.setVariableLocal(taskId, TASK_VARIABLE_STATUS, BpmTaskStatusEnum.APPROVE.getStatus());
taskService.setVariableLocal(taskId, TASK_VARIABLE_REASON, "同意，符合要求");
taskService.setVariableLocal(taskId, TASK_SIGN_PIC_URL, "https://cdn.example.com/sign/12345.png");

// 完成任务
taskService.complete(taskId);
```

**本地变量 vs 流程变量**：

| 对比项 | 本地变量（Local Variable） | 流程变量（Process Variable） |
|--------|---------------------------|---------------------------|
| 作用域 | 仅当前任务可见 | 整个流程实例可见 |
| 存储位置 | `ACT_RU_VARIABLE`（type=task） | `ACT_RU_VARIABLE`（type=execution） |
| 使用场景 | 任务级别的状态和数据 | 流程级别的共享数据 |
| 生命周期 | 任务完成后仍可查询历史 | 流程结束后可查询历史 |

---

### 使用示例

#### 完整的任务审批流程

```java
@Service
public class BpmTaskService {

    @Autowired
    private TaskService taskService;

    @Autowired
    private RuntimeService runtimeService;

    /**
     * 审批通过
     */
    public void approve(String taskId, String reason, String signPicUrl) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();

        // 设置任务本地变量
        taskService.setVariableLocal(taskId, TASK_VARIABLE_STATUS, BpmTaskStatusEnum.APPROVE.getStatus());
        taskService.setVariableLocal(taskId, TASK_VARIABLE_REASON, reason);

        if (StrUtil.isNotBlank(signPicUrl)) {
            taskService.setVariableLocal(taskId, TASK_SIGN_PIC_URL, signPicUrl);
        }

        // 完成任务
        taskService.complete(taskId);
    }

    /**
     * 审批拒绝
     */
    public void reject(String taskId, String reason) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();

        // 设置任务本地变量
        taskService.setVariableLocal(taskId, TASK_VARIABLE_STATUS, BpmTaskStatusEnum.REJECT.getStatus());
        taskService.setVariableLocal(taskId, TASK_VARIABLE_REASON, reason);

        // 设置流程变量（记录流程级别的拒绝原因）
        runtimeService.setVariable(task.getProcessInstanceId(), PROCESS_INSTANCE_VARIABLE_STATUS, BpmProcessInstanceStatusEnum.REJECT.getStatus());
        runtimeService.setVariable(task.getProcessInstanceId(), PROCESS_INSTANCE_VARIABLE_REASON, reason);

        // 完成任务
        taskService.complete(taskId);
    }
}
```

---

## 🔍 BpmTaskCandidateStrategyEnum

### 功能说明

定义任务候选人的**分配策略类型**，详细文档请参考：
- [BpmTaskCandidateStrategyEnum 源码](BpmTaskCandidateStrategyEnum.java:11)
- [任务候选人策略详细文档](../candidate/README.md)

### 策略列表

| 策略编码 | 策略名称 | 说明 |
|---------|---------|------|
| 10 | ROLE | 按角色分配 |
| 20 | DEPT_MEMBER | 部门成员 |
| 21 | DEPT_LEADER | 部门负责人 |
| 22 | POST | 岗位 |
| 23 | MULTI_DEPT_LEADER_MULTI | 连续多级部门负责人 |
| 30 | USER | 指定用户 |
| 34 | APPROVE_USER_SELECT | 审批人自选 |
| 35 | START_USER_SELECT | 发起人自选 |
| 36 | START_USER | 发起人自己 |
| 37 | START_USER_DEPT_LEADER | 发起人部门负责人 |
| 38 | START_USER_DEPT_LEADER_MULTI | 发起人连续多级部门负责人 |
| 40 | USER_GROUP | 用户组 |
| 50 | FORM_USER | 表单内用户字段 |
| 51 | FORM_DEPT_LEADER | 表单内部门负责人 |
| 60 | EXPRESSION | 流程表达式 |
| 1 | ASSIGN_EMPTY | 审批人为空 |

---

## 🚀 最佳实践

### 1. 统一使用常量

**❌ 不推荐**：

```java
// 魔法字符串
String status = execution.getVariable("PROCESS_STATUS", String.class);
execution.setVariable("TASK_STATUS", "APPROVE");
```

**✅ 推荐**：

```java
// 使用常量
String status = execution.getVariable(PROCESS_INSTANCE_VARIABLE_STATUS, String.class);
taskService.setVariableLocal(taskId, TASK_VARIABLE_STATUS, BpmTaskStatusEnum.APPROVE.getStatus());
```

---

### 2. 变量命名规范

**流程变量前缀**：`PROCESS_`
**任务变量前缀**：`TASK_`

**好处**：
- 清晰区分作用域
- 避免变量名冲突
- 便于调试和问题排查

---

### 3. 枚举类型转换

```java
// 获取策略枚举
Integer strategyCode = 21;
BpmTaskCandidateStrategyEnum strategy = BpmTaskCandidateStrategyEnum.valueOf(strategyCode);

// 枚举转整数
Integer code = BpmTaskCandidateStrategyEnum.DEPT_LEADER.getStrategy();
```

---

### 4. 扩展属性读取

```java
/**
 * 工具类：解析 BPMN 扩展属性
 */
public class BpmnModelUtils {

    /**
     * 获取扩展元素的文本内容
     */
    public static String getExtensionElementText(FlowElement flowElement, String elementName) {
        Map<String, List<ExtensionElement>> extensionElements = flowElement.getExtensionElements();
        List<ExtensionElement> elements = extensionElements.get(elementName);

        if (CollUtil.isEmpty(elements)) {
            return null;
        }

        return elements.get(0).getElementText();
    }

    /**
     * 获取扩展元素的属性值
     */
    public static String getExtensionElementAttribute(FlowElement flowElement, String elementName, String attributeName) {
        Map<String, List<ExtensionElement>> extensionElements = flowElement.getExtensionElements();
        List<ExtensionElement> elements = extensionElements.get(elementName);

        if (CollUtil.isEmpty(elements)) {
            return null;
        }

        return elements.get(0).getAttributeValue(NAMESPACE, attributeName);
    }
}
```

---

## 📌 注意事项

### 1. 常量修改影响

修改常量值会影响所有已部署的流程定义，需要谨慎操作：

- **只增不改**：新增常量不影响现有流程
- **谨慎修改**：修改常量需评估影响范围
- **版本兼容**：考虑新旧版本的兼容性

### 2. 变量大小写

Flowable 的变量名**区分大小写**，建议统一使用大写：

```java
// ✅ 正确
execution.setVariable("PROCESS_STATUS", status);

// ❌ 错误（可能导致查询不到）
execution.setVariable("process_status", status);
```

### 3. 变量序列化

流程变量会被序列化存储到数据库，需注意：

- **简单类型优先**：String、Integer、Long、Date 等
- **复杂对象需实现 Serializable**：自定义类需实现序列化接口
- **避免大对象**：大对象会影响性能，考虑存储 ID 引用

---

## 📚 相关文档

### 内部文档
- [任务候选人策略体系](../candidate/README.md)
- [任务行为扩展](../behavior/README.md)
- [表达式函数](../el/README.md)

### 外部资源
- [Flowable BPMN 2.0 规范](https://www.flowable.com/open-source/docs/bpmn/ch07-BPMN-Constructs)
- [Flowable 扩展属性文档](https://www.flowable.com/open-source/docs/bpmn/ch07-BPMN-Constructs#custom-extensions)

---

## 📝 更新日志

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2025-01-XX | 初始版本，定义核心常量和枚举 |

---

## 👥 维护者

- **芋道源码**：架构设计与实现
- BPM 团队：常量维护与文档

---

**⚡️ 快速导航**

| 文件 | 链接 |
|------|------|
| BPMN 模型常量 | [BpmnModelConstants.java](BpmnModelConstants.java:1) |
| 流程变量常量 | [BpmnVariableConstants.java](BpmnVariableConstants.java:1) |
| 候选人策略枚举 | [BpmTaskCandidateStrategyEnum.java](BpmTaskCandidateStrategyEnum.java:1) |
| 候选人策略文档 | [../candidate/README.md](../candidate/README.md) |
