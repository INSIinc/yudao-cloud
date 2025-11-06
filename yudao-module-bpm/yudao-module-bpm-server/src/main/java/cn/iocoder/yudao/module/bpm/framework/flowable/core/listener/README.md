# BPM 监听器与委托（Listeners & Delegates）

## 📖 概述

本目录包含 Flowable 工作流引擎的**事件监听器**和**任务委托**实现，用于监听流程生命周期事件、任务状态变化，以及处理特殊业务逻辑（如抄送、触发器等）。

### 什么是 Flowable 监听器？

**监听器（Listener）** 是 Flowable 提供的扩展机制，允许在流程执行的关键节点插入自定义逻辑。通过监听器，可以：
- **监控流程状态**：实时感知流程创建、完成、取消等事件
- **同步业务数据**：将流程引擎状态同步到业务系统
- **实现业务逻辑**：在任务创建、分配、完成时执行业务操作
- **发送通知**：任务分配后自动通知审批人
- **记录审计日志**：跟踪流程执行轨迹

### 什么是 JavaDelegate？

**JavaDelegate** 是 Flowable 中用于执行自定义 Java 代码的接口，通常用于 ServiceTask（服务任务）节点。通过实现 `JavaDelegate` 接口，可以在流程执行到特定节点时执行业务逻辑。

---

## 🏗️ 架构设计

### 监听器类型

Flowable 支持两种主要的监听器类型：

#### 1. 全局事件监听器

- **注册方式**：在 Flowable 引擎配置中全局注册
- **作用范围**：监听所有流程实例和任务的事件
- **实现基类**：`AbstractFlowableEngineEventListener`
- **本项目实现**：
  - `BpmProcessInstanceEventListener` - 流程实例事件监听器
  - `BpmTaskEventListener` - 任务事件监听器

#### 2. BPMN 节点监听器

- **注册方式**：在 BPMN XML 中配置到具体节点
- **作用范围**：仅监听配置了该监听器的节点
- **实现接口**：
  - `ExecutionListener` - 执行监听器（监听流程元素执行）
  - `TaskListener` - 任务监听器（监听任务生命周期）
- **配置方式**：
  ```xml
  <userTask id="task1">
    <extensionElements>
      <flowable:taskListener event="create" class="com.example.MyTaskListener"/>
    </extensionElements>
  </userTask>
  ```

### 委托类型

#### JavaDelegate

- **用途**：在 ServiceTask 节点执行自定义 Java 代码
- **配置方式**：
  ```xml
  <serviceTask id="service1" flowable:delegateExpression="${myDelegate}"/>
  ```
- **本项目实现**：
  - `BpmCopyTaskDelegate` - 抄送委托
  - `BpmTriggerTaskDelegate` - 触发器委托

---

## 📦 组件列表

| 类名 | 类型 | 功能描述 | 监听的事件 |
|------|------|---------|-----------|
| `BpmProcessInstanceEventListener` | 全局监听器 | 监听流程实例生命周期事件 | PROCESS_CREATED, PROCESS_COMPLETED, PROCESS_CANCELLED |
| `BpmTaskEventListener` | 全局监听器 | 监听任务生命周期和定时器事件 | TASK_CREATED, TASK_ASSIGNED, TASK_COMPLETED, ACTIVITY_CANCELLED, TIMER_FIRED |
| `BpmCopyTaskDelegate` | JavaDelegate | 处理流程抄送逻辑 | - |
| `BpmTriggerTaskDelegate` | JavaDelegate | 处理触发器节点逻辑 | - |
| `demo/` | 示例代码 | 演示如何编写自定义监听器 | - |

---

## 🔧 组件详解

### 1. BpmProcessInstanceEventListener

**功能**：监听流程实例的创建、完成、取消事件，同步更新业务系统中的流程状态。

#### 监听的事件

| 事件类型 | 触发时机 | 处理逻辑 |
|---------|---------|---------|
| `PROCESS_CREATED` | 流程实例创建时 | 调用 `processInstanceService.processProcessInstanceCreated()` |
| `PROCESS_COMPLETED` | 流程实例正常完成时 | 调用 `processInstanceService.processProcessInstanceCompleted()` |
| `PROCESS_CANCELLED` | 流程实例被取消时 | 同样调用 `processProcessInstanceCompleted()`（业务上视为结束） |

#### 核心代码

```java
@Component
public class BpmProcessInstanceEventListener extends AbstractFlowableEngineEventListener {

    public static final Set<FlowableEngineEventType> PROCESS_INSTANCE_EVENTS = ImmutableSet.of(
        FlowableEngineEventType.PROCESS_CREATED,
        FlowableEngineEventType.PROCESS_COMPLETED,
        FlowableEngineEventType.PROCESS_CANCELLED
    );

    @Resource
    @Lazy
    private BpmProcessInstanceService processInstanceService;

    public BpmProcessInstanceEventListener() {
        super(PROCESS_INSTANCE_EVENTS);
    }

    @Override
    protected void processCreated(FlowableEngineEntityEvent event) {
        ProcessInstance processInstance = (ProcessInstance) event.getEntity();
        FlowableUtils.execute(processInstance.getTenantId(),
            () -> processInstanceService.processProcessInstanceCreated(processInstance));
    }

    @Override
    protected void processCompleted(FlowableEngineEntityEvent event) {
        ProcessInstance processInstance = (ProcessInstance) event.getEntity();
        FlowableUtils.execute(processInstance.getTenantId(),
            () -> processInstanceService.processProcessInstanceCompleted(processInstance));
    }

    @Override
    protected void processCancelled(FlowableCancelledEvent event) {
        ProcessInstance processInstance = processInstanceService.getProcessInstance(
            event.getProcessInstanceId()
        );
        if (processInstance != null) {
            FlowableUtils.execute(processInstance.getTenantId(),
                () -> processInstanceService.processProcessInstanceCompleted(processInstance));
        }
    }
}
```

#### 注册方式

在 Flowable 配置类中注册：

```java
@Configuration
public class FlowableConfiguration {

    @Autowired
    private BpmProcessInstanceEventListener processInstanceEventListener;

    @Bean
    public SpringProcessEngineConfiguration processEngineConfiguration() {
        SpringProcessEngineConfiguration config = new SpringProcessEngineConfiguration();

        // 注册全局事件监听器
        List<FlowableEventListener> eventListeners = new ArrayList<>();
        eventListeners.add(processInstanceEventListener);
        config.setEventListeners(eventListeners);

        return config;
    }
}
```

#### 业务场景

**场景 1：流程创建时初始化业务数据**

```
用户提交请假申请
    ↓
Flowable 创建流程实例
    ↓
触发 PROCESS_CREATED 事件
    ↓
BpmProcessInstanceEventListener.processCreated()
    ↓
processInstanceService.processProcessInstanceCreated()
    - 在业务表中创建记录
    - 设置状态为 "审批中"
    - 记录发起人、发起时间
```

**场景 2：流程完成时更新业务状态**

```
最后一个审批人通过
    ↓
Flowable 完成流程实例
    ↓
触发 PROCESS_COMPLETED 事件
    ↓
BpmProcessInstanceEventListener.processCompleted()
    ↓
processInstanceService.processProcessInstanceCompleted()
    - 更新业务表状态为 "已完成"
    - 执行业务逻辑（如扣减请假天数）
    - 发送通知给申请人
```

**场景 3：流程取消时清理数据**

```
管理员取消流程
    ↓
runtimeService.deleteProcessInstance(processInstanceId)
    ↓
触发 PROCESS_CANCELLED 事件
    ↓
BpmProcessInstanceEventListener.processCancelled()
    ↓
查询流程实例 → 调用完成逻辑
    - 更新状态为 "已取消"
    - 回滚业务数据
```

---

### 2. BpmTaskEventListener

**功能**：监听任务的创建、分配、完成、取消事件，以及定时器触发事件（如审批超时）。

#### 监听的事件

| 事件类型 | 触发时机 | 处理逻辑 |
|---------|---------|---------|
| `TASK_CREATED` | 任务创建时（assignee 可能为空） | `taskService.processTaskCreated()` |
| `TASK_ASSIGNED` | 任务分配时（assignee 被设置） | `taskService.processTaskAssigned()` |
| `TASK_COMPLETED` | 任务完成时 | `taskService.processTaskCompleted()` |
| `ACTIVITY_CANCELLED` | 活动取消时（如流程回退） | `taskService.processTaskCanceled()` |
| `TIMER_FIRED` | 定时器触发时（如审批超时） | 根据边界事件类型执行不同逻辑 |

#### 核心代码

```java
@Component
@Slf4j
public class BpmTaskEventListener extends AbstractFlowableEngineEventListener {

    public static final Set<FlowableEngineEventType> TASK_EVENTS = ImmutableSet.of(
        FlowableEngineEventType.TASK_CREATED,
        FlowableEngineEventType.TASK_ASSIGNED,
        FlowableEngineEventType.TASK_COMPLETED,
        FlowableEngineEventType.ACTIVITY_CANCELLED,
        FlowableEngineEventType.TIMER_FIRED
    );

    @Resource
    @Lazy
    private BpmTaskService taskService;

    @Override
    protected void taskCreated(FlowableEngineEntityEvent event) {
        Task entity = (Task) event.getEntity();
        FlowableUtils.execute(entity.getTenantId(),
            () -> taskService.processTaskCreated(entity));
    }

    @Override
    protected void taskAssigned(FlowableEngineEntityEvent event) {
        Task entity = (Task) event.getEntity();
        FlowableUtils.execute(entity.getTenantId(),
            () -> taskService.processTaskAssigned(entity));
    }

    @Override
    protected void taskCompleted(FlowableEngineEntityEvent event) {
        Task entity = (Task) event.getEntity();
        FlowableUtils.execute(entity.getTenantId(),
            () -> taskService.processTaskCompleted(entity));
    }

    @Override
    protected void activityCancelled(FlowableActivityCancelledEvent event) {
        List<HistoricActivityInstance> activityList =
            taskService.getHistoricActivityListByExecutionId(event.getExecutionId());

        activityList.forEach(activity -> {
            if (StrUtil.isNotEmpty(activity.getTaskId())) {
                taskService.processTaskCanceled(activity.getTaskId());
            }
        });
    }

    @Override
    protected void timerFired(FlowableEngineEntityEvent event) {
        // 解析定时器类型和参数
        // 根据边界事件类型执行不同逻辑
        // 详见源码
    }
}
```

#### 业务场景

**场景 1：任务创建时发送通知**

```
流程运行到审批节点
    ↓
Flowable 创建任务
    ↓
触发 TASK_CREATED 事件
    ↓
BpmTaskEventListener.taskCreated()
    ↓
taskService.processTaskCreated()
    - 在业务表中创建任务记录
    - 发送待办通知给候选人
    - 记录任务创建日志
```

**场景 2：任务分配时推送消息**

```
系统将任务分配给张三
    ↓
task.setAssignee("zhangsan")
    ↓
触发 TASK_ASSIGNED 事件
    ↓
BpmTaskEventListener.taskAssigned()
    ↓
taskService.processTaskAssigned()
    - 更新任务负责人
    - 推送站内信/短信/邮件给张三
    - "您有一个新的审批任务待处理"
```

**场景 3：任务完成时记录日志**

```
审批人点击"通过"按钮
    ↓
taskService.complete(taskId, variables)
    ↓
触发 TASK_COMPLETED 事件
    ↓
BpmTaskEventListener.taskCompleted()
    ↓
taskService.processTaskCompleted()
    - 记录审批结果
    - 记录审批时间、意见
    - 更新流程进度
```

**场景 4：审批超时自动处理**

```
任务超过 24 小时未处理
    ↓
边界定时器（Boundary Timer）触发
    ↓
触发 TIMER_FIRED 事件
    ↓
BpmTaskEventListener.timerFired()
    ↓
解析边界事件类型 = USER_TASK_TIMEOUT
    ↓
taskService.processTaskTimeout()
    - 根据配置：自动通过 / 自动拒绝 / 转交上级
    - 发送超时通知
```

#### 定时器事件处理

**支持的边界事件类型**：

| 类型 | 枚举值 | 说明 | 处理方式 |
|------|--------|------|---------|
| 用户任务超时 | `USER_TASK_TIMEOUT` | 审批任务超时 | 根据配置自动通过/拒绝/转交 |
| 延迟定时器 | `DELAY_TIMER_TIMEOUT` | 延迟一段时间后继续 | 触发任务继续执行 |
| 子流程超时 | `CHILD_PROCESS_TIMEOUT` | 子流程执行超时 | 记录超时，可能终止子流程 |

**BPMN 配置示例**：

```xml
<userTask id="task_approve" name="经理审批">
  <extensionElements>
    <flowable:timeoutHandlerType>1</flowable:timeoutHandlerType> <!-- 1=自动通过 -->
  </extensionElements>
</userTask>

<!-- 边界定时器：24小时超时 -->
<boundaryEvent id="timeout_event" attachedToRef="task_approve" cancelActivity="true">
  <extensionElements>
    <flowable:boundaryEventType>1</flowable:boundaryEventType> <!-- 1=USER_TASK_TIMEOUT -->
  </extensionElements>
  <timerEventDefinition>
    <timeDuration>PT24H</timeDuration>
  </timerEventDefinition>
</boundaryEvent>
```

---

### 3. BpmCopyTaskDelegate

**功能**：处理流程中的**抄送节点**，将流程信息抄送给指定用户供其查阅。

#### 工作原理

```
流程执行到抄送节点（ServiceTask）
    ↓
Flowable 调用 BpmCopyTaskDelegate.execute()
    ↓
1. 调用 taskCandidateInvoker 计算抄送人
    ↓
2. 调用 processInstanceCopyService 创建抄送记录
    ↓
3. 抄送人在"已抄送"列表中可以查看流程
```

#### 核心代码

```java
@Component(BEAN_NAME)
public class BpmCopyTaskDelegate implements JavaDelegate {

    public static final String BEAN_NAME = "bpmCopyTaskDelegate";

    @Resource
    private BpmTaskCandidateInvoker taskCandidateInvoker;

    @Resource
    private BpmProcessInstanceCopyService processInstanceCopyService;

    @Override
    public void execute(DelegateExecution execution) {
        // 1. 计算抄送人
        Set<Long> userIds = taskCandidateInvoker.calculateUsersByTask(execution);
        if (CollUtil.isEmpty(userIds)) {
            return;
        }

        // 2. 获取当前节点信息
        FlowElement currentFlowElement = execution.getCurrentFlowElement();

        // 3. 创建抄送记录
        processInstanceCopyService.createProcessInstanceCopy(
            userIds,
            null, // taskId
            execution.getProcessInstanceId(),
            currentFlowElement.getId(),
            currentFlowElement.getName(),
            null // reason
        );
    }
}
```

#### BPMN 配置

```xml
<serviceTask id="copy_task_1" name="抄送给项目负责人"
             flowable:delegateExpression="${bpmCopyTaskDelegate}">
  <extensionElements>
    <flowable:candidateStrategy>30</flowable:candidateStrategy> <!-- 30=指定用户 -->
    <flowable:candidateParam>1001,1002</flowable:candidateParam> <!-- 用户ID -->
  </extensionElements>
</serviceTask>
```

#### 业务场景

**场景：请假申请抄送给 HR 和部门负责人**

```
流程图：
提交申请 → 直属领导审批 → [抄送节点] → 总经理审批 → 结束
                            ↓
                    抄送给 HR 和部门负责人
```

**执行流程**：

```
直属领导审批通过
    ↓
流程执行到抄送节点
    ↓
BpmCopyTaskDelegate.execute()
    ↓
1. 计算抄送人：{HR: 2001, 部门负责人: 3001}
    ↓
2. 创建抄送记录
    ↓
3. HR 和部门负责人在"已抄送"列表中可以查看该请假申请
    - 仅查看，不需要审批
    - 了解员工请假情况
```

---

### 4. BpmTriggerTaskDelegate

**功能**：处理流程中的**触发器节点**，根据配置的触发器类型执行不同的操作（如 HTTP 调用、消息发送等）。

#### 工作原理

```
流程执行到触发器节点（ServiceTask）
    ↓
Flowable 调用 BpmTriggerTaskDelegate.execute()
    ↓
1. 解析节点配置的触发器类型
    ↓
2. 从 triggerMap 中查找对应的 BpmTrigger 实现
    ↓
3. 调用 bpmTrigger.execute(processInstanceId, param)
    ↓
4. 执行具体的触发器逻辑（如 HTTP 请求）
```

#### 核心代码

```java
@Component(BEAN_NAME)
@Slf4j
public class BpmTriggerTaskDelegate implements JavaDelegate {

    public static final String BEAN_NAME = "bpmTriggerTaskDelegate";

    @Resource
    private List<BpmTrigger> triggers;

    private final EnumMap<BpmTriggerTypeEnum, BpmTrigger> triggerMap = new EnumMap<>(BpmTriggerTypeEnum.class);

    @PostConstruct
    private void init() {
        triggers.forEach(trigger -> triggerMap.put(trigger.getType(), trigger));
    }

    @Override
    public void execute(DelegateExecution execution) {
        FlowElement flowElement = execution.getCurrentFlowElement();

        // 1. 解析触发器类型
        BpmTriggerTypeEnum bpmTriggerType = BpmnModelUtils.parserTriggerType(flowElement);

        // 2. 查找对应的触发器实现
        BpmTrigger bpmTrigger = triggerMap.get(bpmTriggerType);
        if (bpmTrigger == null) {
            log.error("[execute][FlowElement({}), {} 找不到匹配的触发器]",
                execution.getCurrentActivityId(), flowElement);
            return;
        }

        // 3. 执行触发器逻辑
        bpmTrigger.execute(
            execution.getProcessInstanceId(),
            BpmnModelUtils.parserTriggerParam(flowElement)
        );
    }
}
```

#### 触发器类型

| 类型 | 说明 | 典型用途 |
|------|------|---------|
| HTTP | 调用外部 HTTP 接口 | 通知外部系统、同步数据 |
| MESSAGE | 发送消息 | 发送邮件、短信、站内信 |
| WEBHOOK | Webhook 回调 | 触发第三方系统事件 |

#### BPMN 配置

```xml
<serviceTask id="trigger_1" name="通知外部系统"
             flowable:delegateExpression="${bpmTriggerTaskDelegate}">
  <extensionElements>
    <flowable:triggerType>1</flowable:triggerType> <!-- 1=HTTP -->
    <flowable:triggerParam>
      {
        "url": "https://api.example.com/notify",
        "method": "POST",
        "body": {
          "processInstanceId": "${execution.processInstanceId}",
          "status": "APPROVED"
        }
      }
    </flowable:triggerParam>
  </extensionElements>
</serviceTask>
```

#### 业务场景

**场景：审批通过后调用外部 ERP 系统**

```
流程图：
提交采购申请 → 部门审批 → 财务审批 → [触发器节点] → 结束
                                      ↓
                              调用 ERP 系统 API
```

**执行流程**：

```
财务审批通过
    ↓
流程执行到触发器节点
    ↓
BpmTriggerTaskDelegate.execute()
    ↓
1. 解析触发器类型 = HTTP
    ↓
2. 查找 HttpBpmTrigger 实现
    ↓
3. 执行 HTTP 请求
    - POST https://erp.example.com/api/purchase/create
    - Body: {processInstanceId, amount, items, ...}
    ↓
4. ERP 系统接收请求，创建采购订单
```

---

## 📁 demo/ 目录

`demo/` 目录包含示例代码，演示如何编写自定义监听器：

### 目录结构

```
demo/
├── exection/                          # 执行监听器示例
│   ├── DemoDelegateClassExecutionListener.java
│   ├── DemoDelegateExpressionExecutionListener.java
│   └── DemoSpringExpressionExecutionListener.java
└── task/                              # 任务监听器示例
    ├── DemoDelegateClassTaskListener.java
    ├── DemoDelegateExpressionTaskListener.java
    └── DemoSpringExpressionTaskListener.java
```

### 监听器配置方式

Flowable 支持三种方式配置监听器：

#### 1. Class 方式

直接指定监听器的完整类名：

```xml
<userTask id="task1">
  <extensionElements>
    <flowable:taskListener event="create" class="com.example.MyTaskListener"/>
  </extensionElements>
</userTask>
```

**优点**：简单直接
**缺点**：无法使用 Spring 依赖注入

#### 2. Delegate Expression 方式

通过 Spring Bean 名称引用：

```xml
<userTask id="task1">
  <extensionElements>
    <flowable:taskListener event="create" delegateExpression="${myTaskListener}"/>
  </extensionElements>
</userTask>
```

**优点**：可以使用 Spring 依赖注入
**缺点**：需要确保 Bean 名称正确

#### 3. Expression 方式

直接执行 Spring 表达式：

```xml
<userTask id="task1">
  <extensionElements>
    <flowable:taskListener event="create" expression="${myService.doSomething(task)}"/>
  </extensionElements>
</userTask>
```

**优点**：灵活，可直接调用 Spring Bean 的方法
**缺点**：复杂逻辑不适合写在表达式中

---

## 🚀 使用指南

### 1. 注册全局监听器

在 Flowable 配置类中注册：

```java
@Configuration
public class FlowableConfiguration {

    @Autowired
    private BpmProcessInstanceEventListener processInstanceEventListener;

    @Autowired
    private BpmTaskEventListener taskEventListener;

    @Bean
    public SpringProcessEngineConfiguration processEngineConfiguration() {
        SpringProcessEngineConfiguration config = new SpringProcessEngineConfiguration();

        // 注册全局事件监听器
        List<FlowableEventListener> eventListeners = new ArrayList<>();
        eventListeners.add(processInstanceEventListener);
        eventListeners.add(taskEventListener);
        config.setEventListeners(eventListeners);

        return config;
    }
}
```

---

### 2. 在 BPMN 中配置节点监听器

**任务监听器示例**：

```xml
<userTask id="task_approve" name="审批">
  <extensionElements>
    <!-- 任务创建时 -->
    <flowable:taskListener event="create" delegateExpression="${myTaskCreateListener}"/>

    <!-- 任务分配时 -->
    <flowable:taskListener event="assignment" expression="${myService.onTaskAssigned(task)}"/>

    <!-- 任务完成时 -->
    <flowable:taskListener event="complete" class="com.example.MyTaskCompleteListener"/>
  </extensionElements>
</userTask>
```

**执行监听器示例**：

```xml
<serviceTask id="service1" name="服务任务">
  <extensionElements>
    <!-- 开始执行时 -->
    <flowable:executionListener event="start" delegateExpression="${myExecutionListener}"/>

    <!-- 执行结束时 -->
    <flowable:executionListener event="end" expression="${myService.onEnd(execution)}"/>
  </extensionElements>
</serviceTask>
```

---

### 3. 实现自定义任务监听器

```java
@Component("myTaskListener")
public class MyTaskListener implements TaskListener {

    @Resource
    private MyService myService;

    @Override
    public void notify(DelegateTask delegateTask) {
        // 任务事件类型
        String eventName = delegateTask.getEventName();

        switch (eventName) {
            case TaskListener.EVENTNAME_CREATE:
                // 任务创建时的逻辑
                myService.onTaskCreated(delegateTask);
                break;

            case TaskListener.EVENTNAME_ASSIGNMENT:
                // 任务分配时的逻辑
                myService.onTaskAssigned(delegateTask);
                break;

            case TaskListener.EVENTNAME_COMPLETE:
                // 任务完成时的逻辑
                myService.onTaskCompleted(delegateTask);
                break;
        }
    }
}
```

---

### 4. 实现自定义执行监听器

```java
@Component("myExecutionListener")
public class MyExecutionListener implements ExecutionListener {

    @Resource
    private MyService myService;

    @Override
    public void notify(DelegateExecution execution) {
        String eventName = execution.getEventName();

        switch (eventName) {
            case ExecutionListener.EVENTNAME_START:
                // 开始执行时
                myService.onStart(execution);
                break;

            case ExecutionListener.EVENTNAME_END:
                // 执行结束时
                myService.onEnd(execution);
                break;
        }
    }
}
```

---

### 5. 实现自定义 JavaDelegate

```java
@Component("myDelegate")
public class MyDelegate implements JavaDelegate {

    @Resource
    private MyService myService;

    @Override
    public void execute(DelegateExecution execution) {
        // 获取流程变量
        String businessKey = execution.getProcessInstanceBusinessKey();
        Object variable = execution.getVariable("myVariable");

        // 执行业务逻辑
        myService.doSomething(businessKey, variable);

        // 设置流程变量
        execution.setVariable("result", "success");
    }
}
```

**BPMN 配置**：

```xml
<serviceTask id="service1" flowable:delegateExpression="${myDelegate}"/>
```

---

## 📌 典型场景

### 场景 1：审批通过后发送通知

**需求**：任务完成时，发送通知给流程发起人。

**实现方式**：使用 `BpmTaskEventListener` 的 `taskCompleted` 事件

```java
@Service
public class BpmTaskServiceImpl implements BpmTaskService {

    @Resource
    private NotificationService notificationService;

    @Override
    public void processTaskCompleted(Task task) {
        // 1. 记录任务完成日志
        saveTaskLog(task);

        // 2. 查询流程发起人
        String startUserId = getProcessStartUserId(task.getProcessInstanceId());

        // 3. 发送通知
        notificationService.sendNotification(
            startUserId,
            "您的流程已审批通过",
            "流程: " + task.getName() + " 已完成审批"
        );
    }
}
```

---

### 场景 2：审批超时自动通过

**需求**：任务超过 24 小时未处理，自动通过。

**实现方式**：边界定时器 + `BpmTaskEventListener` 的 `timerFired` 事件

**BPMN 配置**：

```xml
<userTask id="task_approve" name="经理审批">
  <extensionElements>
    <flowable:timeoutHandlerType>1</flowable:timeoutHandlerType> <!-- 1=自动通过 -->
  </extensionElements>
</userTask>

<boundaryEvent id="timeout_event" attachedToRef="task_approve" cancelActivity="true">
  <extensionElements>
    <flowable:boundaryEventType>1</flowable:boundaryEventType> <!-- 1=USER_TASK_TIMEOUT -->
  </extensionElements>
  <timerEventDefinition>
    <timeDuration>PT24H</timeDuration>
  </timerEventDefinition>
</boundaryEvent>
```

**处理逻辑**（已在 `BpmTaskEventListener` 中实现）：

```java
// timerFired() 方法中
if (bpmTimerBoundaryEventType == BpmBoundaryEventTypeEnum.USER_TASK_TIMEOUT) {
    String timeoutHandlerType = ...;
    String taskKey = boundaryEvent.getAttachedToRefId();

    taskService.processTaskTimeout(
        event.getProcessInstanceId(),
        taskKey,
        NumberUtils.parseInt(timeoutHandlerType) // 1=自动通过
    );
}
```

---

### 场景 3：抄送给相关人员

**需求**：审批通过后，抄送给 HR 和财务部门。

**实现方式**：使用 `BpmCopyTaskDelegate`

**BPMN 配置**：

```xml
<!-- 审批节点 -->
<userTask id="task_approve" name="经理审批" />

<!-- 抄送节点 -->
<serviceTask id="copy_task" name="抄送给HR和财务"
             flowable:delegateExpression="${bpmCopyTaskDelegate}">
  <extensionElements>
    <flowable:candidateStrategy>10</flowable:candidateStrategy> <!-- 10=按角色 -->
    <flowable:candidateParam>HR,FINANCE</flowable:candidateParam> <!-- 角色编码 -->
  </extensionElements>
</serviceTask>

<!-- 流程结束 -->
<endEvent id="end" />

<!-- 连线 -->
<sequenceFlow sourceRef="task_approve" targetRef="copy_task" />
<sequenceFlow sourceRef="copy_task" targetRef="end" />
```

---

### 场景 4：调用外部 API

**需求**：审批通过后，调用外部系统创建订单。

**实现方式**：使用 `BpmTriggerTaskDelegate`

**BPMN 配置**：

```xml
<serviceTask id="trigger_erp" name="通知ERP系统"
             flowable:delegateExpression="${bpmTriggerTaskDelegate}">
  <extensionElements>
    <flowable:triggerType>1</flowable:triggerType> <!-- 1=HTTP -->
    <flowable:triggerParam>
      {
        "url": "https://erp.example.com/api/orders/create",
        "method": "POST",
        "headers": {
          "Authorization": "Bearer ${token}"
        },
        "body": {
          "processInstanceId": "${execution.processInstanceId}",
          "amount": "${amount}",
          "items": "${items}"
        }
      }
    </flowable:triggerParam>
  </extensionElements>
</serviceTask>
```

---

## 🔍 技术细节

### 1. 租户上下文切换

在多租户环境中，监听器需要切换到正确的租户上下文：

```java
FlowableUtils.execute(tenantId, () -> {
    // 在租户上下文中执行业务逻辑
    processInstanceService.processProcessInstanceCreated(processInstance);
});
```

**`FlowableUtils.execute()` 的作用**：
- 设置当前线程的租户上下文
- 执行业务逻辑
- 清理租户上下文

---

### 2. 延迟加载避免循环依赖

使用 `@Lazy` 注解避免循环依赖：

```java
@Resource
@Lazy
private BpmTaskService taskService;
```

**原因**：
- 监听器在 Flowable 引擎启动时注册
- 业务服务可能依赖 Flowable 的 RuntimeService、TaskService
- 如果不延迟加载，会形成循环依赖导致启动失败

---

### 3. 事件过滤

全局监听器可以选择性监听事件：

```java
public BpmTaskEventListener() {
    super(TASK_EVENTS); // 只监听指定的事件类型
}
```

**好处**：
- 提高性能（不处理不关心的事件）
- 代码更清晰（明确监听范围）

---

### 4. 异常处理

监听器中的异常会导致流程执行失败，需要谨慎处理：

```java
@Override
protected void taskCompleted(FlowableEngineEntityEvent event) {
    try {
        Task entity = (Task) event.getEntity();
        FlowableUtils.execute(entity.getTenantId(),
            () -> taskService.processTaskCompleted(entity));
    } catch (Exception e) {
        log.error("[taskCompleted][任务({}) 完成处理失败]", event.getEntity(), e);
        // 根据业务需求决定是否重新抛出异常
        // throw e; // 抛出会导致流程回滚
    }
}
```

---

## 🧪 测试建议

### 单元测试

```java
@SpringBootTest
class BpmTaskEventListenerTest {

    @MockBean
    private BpmTaskService taskService;

    @Autowired
    private RuntimeService runtimeService;

    @Test
    void testTaskCreated() {
        // 启动流程
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("test_process");

        // 查询任务
        Task task = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .singleResult();

        // 验证：taskCreated 事件已触发
        verify(taskService, times(1)).processTaskCreated(any(Task.class));
    }

    @Test
    void testTaskCompleted() {
        // 启动流程并获取任务
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("test_process");
        Task task = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .singleResult();

        // 完成任务
        taskService.complete(task.getId());

        // 验证：taskCompleted 事件已触发
        verify(taskService, times(1)).processTaskCompleted(any(Task.class));
    }
}
```

---

## 📚 相关文档

### 内部文档
- [任务候选人策略](../candidate/README.md)
- [任务行为扩展](../behavior/README.md)
- [BPMN 常量与枚举](../enums/README.md)

### 外部资源
- [Flowable 事件监听器官方文档](https://www.flowable.com/open-source/docs/bpmn/ch07-BPMN-Constructs#event-listeners)
- [Flowable JavaDelegate 官方文档](https://www.flowable.com/open-source/docs/bpmn/ch07-BPMN-Constructs#java-service-task)
- [Flowable 边界事件官方文档](https://www.flowable.com/open-source/docs/bpmn/ch07-BPMN-Constructs#boundary-events)

---

## 📝 更新日志

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2025-01-XX | 初始版本，实现核心监听器和委托 |

---

## 👥 维护者

- **jason**：核心实现
- **芋道源码**：架构设计与文档

---

**⚡️ 快速导航**

| 组件 | 链接 |
|------|------|
| 流程实例监听器 | [BpmProcessInstanceEventListener.java](BpmProcessInstanceEventListener.java:17) |
| 任务监听器 | [BpmTaskEventListener.java](BpmTaskEventListener.java:33) |
| 抄送委托 | [BpmCopyTaskDelegate.java](BpmCopyTaskDelegate.java:17) |
| 触发器委托 | [BpmTriggerTaskDelegate.java](BpmTriggerTaskDelegate.java:20) |
| 示例代码 | [demo/](demo/) |
