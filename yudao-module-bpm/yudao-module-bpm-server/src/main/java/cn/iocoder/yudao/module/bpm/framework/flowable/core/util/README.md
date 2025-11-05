# Flowable 核心工具类

本目录包含芋道工作流(BPM)模块中 Flowable 流程引擎相关的核心工具类,用于简化流程定义、流程执行、BPMN模型操作等常见任务。

## 工具类列表

### 1. BpmHttpRequestUtils

**工作流 HTTP 请求工具类**

用于在 Flowable 流程执行过程中自动发起 HTTP 请求(如回调外部系统),并可选地将响应结果解析后回写到流程变量中。

**主要功能:**

- **流程触发HTTP调用**: 在流程节点配置 HTTP 请求动作,自动发起 POST 请求
- **请求参数构建**: 支持从流程变量或固定值中提取参数填充到请求头或请求体
- **响应结果处理**: 可将外部系统响应的数据映射回流程变量,供后续节点使用
- **租户隔离**: 自动在请求头中携带租户ID,支持多租户场景
- **流程事件通知**: 支持在流程实例状态变更时(如完成、终止)通知外部系统

**核心方法:**

```java
// 执行BPM HTTP请求(主流程调用)
executeBpmHttpRequest(ProcessInstance processInstance, String url,
                     List<HttpRequestParam> headerParams, List<HttpRequestParam> bodyParams,
                     Boolean handleResponse, List<KeyValue<String, String>> response)

// 执行BPM HTTP请求(流程状态变更事件)
executeBpmHttpRequest(BpmProcessInstanceStatusEvent event, String url)

// 构建HTTP请求头
buildHttpHeaders(ProcessInstance processInstance, List<HttpRequestParam> headerSettings)

// 构建HTTP请求体
buildHttpBody(ProcessInstance processInstance, List<HttpRequestParam> bodySettings)
```

**使用场景:**

- 流程节点触发外部接口调用(如发送通知、更新业务数据)
- 等待外部系统响应后继续流程
- 流程完成后自动回调业务系统

---

### 2. BpmnModelUtils

**BPMN 模型操作工具类**

提供 BPMN 模型的创建、修改、解析、遍历、预测等全方位操作能力。是操作 Flowable BPMN 模型的核心工具类。

**功能模块:**

#### 2.1 BPMN 修改 + 解析元素

为 BPMN 节点添加和解析扩展元素(ExtensionElement),用于存储业务自定义属性。

**核心方法:**

```java
// 添加扩展元素(字符串/整数/JSON对象/属性Map)
addExtensionElement(FlowElement element, String name, String value)
addExtensionElement(FlowElement element, String name, Integer value)
addExtensionElementJson(FlowElement element, String name, Object value)
addExtensionElement(FlowElement element, String name, Map<String, String> attributes)

// 解析扩展元素
parseExtensionElement(FlowElement flowElement, String elementName)

// 候选人配置
addCandidateElements(Integer candidateStrategy, String candidateParam, FlowElement flowElement)
parseCandidateStrategy(FlowElement userTask)
parseCandidateParam(FlowElement userTask)

// 表单字段权限
addFormFieldsPermission(List<Map<String, String>> fieldsPermissions, FlowElement flowElement)
parseFormFieldsPermission(BpmnModel bpmnModel, String flowElementId)

// 操作按钮配置
addButtonsSetting(List<OperationButtonSetting> buttonsSetting, UserTask userTask)
parseButtonsSetting(BpmnModel bpmnModel, String flowElementId)

// 审批类型、拒绝处理、空处理等
parseApproveType(FlowElement userTask)
parseRejectHandlerType(FlowElement userTask)
parseAssignEmptyHandlerType(FlowElement userTask)
```

#### 2.2 BPMN 简单查找

快速查找 BPMN 模型中的元素。

**核心方法:**

```java
// 根据ID获取流程元素
getFlowElementById(BpmnModel model, String flowElementId)

// 获取指定类型的所有元素
getBpmnModelElements(BpmnModel model, Class<T> clazz)

// 获取节点的入口/出口连线
getElementIncomingFlows(FlowElement source)
getElementOutgoingFlows(FlowElement source)

// 获取开始/结束节点
getStartEvent(BpmnModel model)
getEndEvent(BpmnModel model)

// BPMN模型与XML互转
getBpmnModel(byte[] bpmnBytes)
getBpmnXml(BpmnModel model)
```

#### 2.3 BPMN 复杂遍历

用于回退、撤回等复杂场景的节点遍历。

**核心方法:**

```java
// 查找当前节点之前的所有用户任务(向前遍历)
getPreviousUserTaskList(FlowElement source, Set<String> hasSequenceFlow, List<UserTask> userTaskList)

// 迭代获取子流程用户任务
findChildProcessUserTaskList(FlowElement source, Set<String> hasSequenceFlow, List<UserTask> userTaskList)

// 判断目标节点相对于当前节点是否串行可达(用于判断是否可回退)
isSequentialReachable(FlowElement source, FlowElement target, Set<String> visitedElements)

// 查找当前节点之后的用户任务(向后遍历)
iteratorFindChildUserTasks(FlowElement source, List<String> runTaskKeyList,
                          Set<String> hasSequenceFlow, List<UserTask> userTaskList)

// 获取下一个节点列表
getNextUserTasks(FlowElement source)
```

#### 2.4 BPMN 流程预测

根据流程变量预测流程走向,用于流程模拟、预测审批人等场景。

**核心方法:**

```java
// 流程预测(返回 StartEvent、UserTask、ServiceTask、EndEvent)
simulateProcess(BpmnModel bpmnModel, Map<String, Object> variables)

// 根据当前节点获取下一个节点
getNextFlowNodes(FlowElement currentElement, BpmnModel bpmnModel, Map<String, Object> variables)

// 条件表达式求值
evalConditionExpress(Map<String, Object> variables, String expression)

// 判断是否跳过节点
isSkipNode(FlowElement flowNode, Map<String, Object> variables)

// 判断是否为顺序审批的用户任务
isSequentialUserTask(FlowElement flowElement)
```

**使用场景:**

- 发起流程前预测审批人
- 实现流程撤回、回退功能
- 流程图高亮显示(显示已完成节点)
- 流程设计器(添加/修改节点属性)

---

### 3. FlowableUtils

**Flowable 通用工具类**

封装 Flowable 流程引擎中常用的工具方法,涵盖用户认证、租户上下文、流程实例状态、任务变量、表达式求值等场景。

**功能模块:**

#### 3.1 用户认证

```java
// 设置当前认证用户ID
setAuthenticatedUserId(Long userId)

// 清除当前认证用户
clearAuthenticatedUserId()

// 在指定用户身份下执行逻辑
executeAuthenticatedUserId(Long userId, Callable<V> callable)
```

#### 3.2 租户上下文

```java
// 获取当前租户ID(字符串形式)
getTenantId()

// 在指定租户上下文中执行逻辑
execute(String tenantIdStr, Runnable runnable)
execute(String tenantIdStr, Callable<V> callable)
```

#### 3.3 执行上下文变量命名

```java
// 格式化多实例任务审批人列表变量名: {activityId}_assignees
formatExecutionCollectionVariable(String activityId)

// 格式化多实例任务当前审批人变量名: {activityId}_assignee
formatExecutionCollectionElementVariable(String activityId)
```

#### 3.4 流程实例相关

```java
// 获取流程实例状态
getProcessInstanceStatus(ProcessInstance processInstance)
getProcessInstanceStatus(HistoricProcessInstance processInstance)

// 获取审批原因
getProcessInstanceReason(HistoricProcessInstance processInstance)

// 获取流程表单数据(剔除系统变量)
getProcessInstanceFormVariable(ProcessInstance processInstance)
getProcessInstanceFormVariable(HistoricProcessInstance processInstance)

// 获取发起人选择的审批人映射
getStartUserSelectAssignees(ProcessInstance processInstance)

// 获取审批人动态选择的下一节点审批人
getApproveUserSelectAssignees(ProcessInstance processInstance)

// 生成流程实例摘要(用于列表页展示)
getSummary(BpmProcessDefinitionInfoDO processDefinitionInfo, Map<String, Object> processVariables)
```

#### 3.5 任务相关

```java
// 获取任务状态
getTaskStatus(TaskInfo task)

// 获取任务审批原因
getTaskReason(TaskInfo task)

// 获取任务电子签名图片URL
getTaskSignPicUrl(TaskInfo task)

// 获取任务表单数据(剔除系统变量)
getTaskFormVariable(TaskInfo task)
```

#### 3.6 表达式求值

```java
// 对EL表达式求值
getExpressionValue(VariableContainer variableContainer, String expressionString)
getExpressionValue(Map<String, Object> variable, String expressionString)
```

**使用场景:**

- 多租户流程隔离
- 流程启动时指定发起人
- 获取流程/任务的业务数据
- 条件网关表达式计算
- 流程变量管理

---

### 4. SimpleModelUtils

**简单模型转换工具类**

将仿钉钉/飞书风格的简单流程模型(JSON)转换为标准的 BPMN Model,支持可视化流程设计器。

**核心功能:**

将前端传递的树状结构流程节点(`BpmSimpleModelNodeVO`)转换成 Flowable 可识别的 BPMN 元素,并自动构建节点间的连线(`SequenceFlow`)。

**支持的节点类型:**

- **START_NODE**: 开始节点 → `StartEvent`
- **START_USER_NODE**: 发起人节点 → `UserTask`
- **APPROVE_NODE**: 审批节点 → `UserTask` + 可选的超时边界事件
- **TRANSACTOR_NODE**: 经办节点 → `UserTask`
- **COPY_NODE**: 抄送节点 → `ServiceTask`
- **CONDITION_BRANCH_NODE**: 条件分支 → `ExclusiveGateway`
- **PARALLEL_BRANCH_NODE**: 并行分支 → `InclusiveGateway`(分叉+汇聚)
- **INCLUSIVE_BRANCH_NODE**: 包容分支 → `InclusiveGateway`(分叉+汇聚)
- **ROUTER_BRANCH_NODE**: 路由分支 → `ExclusiveGateway`
- **DELAY_TIMER_NODE**: 延迟定时器节点 → `ReceiveTask` + `BoundaryEvent`
- **TRIGGER_NODE**: 触发器节点 → `ServiceTask` + 可选的 `ReceiveTask`(HTTP回调)
- **CHILD_PROCESS**: 子流程节点 → `CallActivity`
- **END_NODE**: 结束节点 → `EndEvent`

**核心方法:**

```java
// 简单模型转 BPMN Model
buildBpmnModel(String processId, String processName, BpmSimpleModelNodeVO simpleModelNode)

// 流程预测(基于简单模型)
simulateProcess(BpmSimpleModelNodeVO rootNode, Map<String, Object> variables)

// 判断是否为有效节点
isValidNode(BpmSimpleModelNodeVO node)

// 判断是否为顺序审批节点
isSequentialApproveNode(BpmSimpleModelNodeVO node)

// 判断是否跳过节点
isSkipNode(BpmSimpleModelNodeVO currentNode, Map<String, Object> variables)

// 构造条件表达式
buildConditionExpression(BpmSimpleModelNodeVO.ConditionSetting conditionSetting)
buildConditionExpression(BpmSimpleModelNodeVO.RouterSetting routerSetting)
```

**转换过程:**

1. **第一阶段 - 构建节点**: 遍历简单模型树,将每个节点转换为对应的 BPMN 元素(如 UserTask、Gateway等)
2. **第二阶段 - 构建连线**: 遍历简单模型树,为每个节点建立与下一节点的 SequenceFlow 连线
3. **自动布局**: 使用 `BpmnAutoLayout` 自动计算节点坐标和连线路径

**使用场景:**

- 可视化流程设计器(仿钉钉/飞书风格)
- 简化流程配置(无需手写BPMN XML)
- 流程模板快速创建
- 流程预测和模拟

---

## 整体架构说明

这四个工具类在工作流引擎中的分工:

```
┌─────────────────────────────────────────────────────────┐
│                   BPM 业务层                              │
├─────────────────────────────────────────────────────────┤
│ SimpleModelUtils          │  简化流程设计(前端JSON→BPMN)│
├───────────────────────────┼─────────────────────────────┤
│ BpmnModelUtils            │  BPMN模型操作(增删改查遍历) │
├───────────────────────────┼─────────────────────────────┤
│ FlowableUtils             │  流程引擎通用能力封装        │
├───────────────────────────┼─────────────────────────────┤
│ BpmHttpRequestUtils       │  流程与外部系统集成(HTTP)    │
├─────────────────────────────────────────────────────────┤
│                Flowable Engine (核心引擎)                 │
└─────────────────────────────────────────────────────────┘
```

## 使用建议

1. **流程设计阶段**: 使用 `SimpleModelUtils` 将可视化配置转为BPMN
2. **流程定义阶段**: 使用 `BpmnModelUtils` 修改和解析BPMN模型
3. **流程执行阶段**: 使用 `FlowableUtils` 处理流程实例和任务
4. **流程集成阶段**: 使用 `BpmHttpRequestUtils` 对接外部系统

## 相关文档

- [Flowable 官方文档](https://www.flowable.com/open-source/docs/)
- [芋道工作流文档](https://doc.iocoder.cn/bpm/)
- BPMN 2.0 规范

## 注意事项

1. 所有工具类方法均为静态方法,可直接调用
2. 涉及租户的操作需确保上下文中已设置租户ID
3. 表达式求值使用 Flowable 内置的 EL 引擎
4. 扩展元素的命名请参考 `BpmnModelConstants` 常量类
