# BPM 任务行为（Behavior）扩展

## 📖 概述

本目录是芋道 yudao-cloud BPM 模块对 **Flowable 任务行为体系**的核心扩展，用于自定义工作流任务节点在执行时的具体行为，特别是任务候选人（Candidate）和负责人（Assignee）的分配逻辑。

### 什么是 Behavior？

在 Flowable 工作流引擎中，每个 BPMN 元素（如 UserTask、ServiceTask、CallActivity 等）在流程执行时都由一个对应的 **Behavior 对象**控制其具体行为。

- **UserTaskActivityBehavior**：控制用户任务的创建、分配、完成等行为
- **MultiInstanceBehavior**：控制多实例任务（会签、或签）的循环执行逻辑
- **ActivityBehaviorFactory**：负责为每个 BPMN 元素创建对应的 Behavior 实例

### 为什么要自定义 Behavior？

Flowable 默认的任务分配机制基于 BPMN 静态配置（如 `assignee="${userId}"`、`candidateGroups="managers"`），但实际业务中：

❌ **默认方式的局限**：
- 需要在 BPMN 中硬编码表达式，不够灵活
- 无法实现复杂的动态分配规则（如"发起人的直属领导的部门负责人"）
- 多实例任务（会签/或签）的审批人集合难以动态计算
- 候选人为空时缺乏兜底机制

✅ **自定义 Behavior 的优势**：
- 完全接管任务分配逻辑，与 BPMN 解耦
- 支持复杂的候选人策略（基于角色、部门、岗位、发起人等）
- 强制"任务责任到人"：每个任务必须有 assignee，不留空
- 统一处理单实例和多实例场景
- 自动过滤禁用用户、实现发起人跳过等智能逻辑

---

## 🏗️ 架构设计

### 核心组件

```
behavior/
├── BpmActivityBehaviorFactory.java           # 行为工厂（注册自定义 Behavior）
├── BpmUserTaskActivityBehavior.java          # 单实例用户任务行为
├── BpmParallelMultiInstanceBehavior.java     # 并行多实例行为（会签/并行审批）
└── BpmSequentialMultiInstanceBehavior.java   # 串行多实例行为（或签/顺序审批）
```

### 工作原理

#### 1. Flowable 引擎解析 BPMN 流程

```
┌──────────────────────────────────────────┐
│  流程部署：上传 BPMN XML 文件             │
└───────────────┬──────────────────────────┘
                │
                ▼
┌──────────────────────────────────────────┐
│  BpmnParser 解析 XML，识别各种节点        │
│  - <userTask>                             │
│  - <serviceTask>                          │
│  - <multiInstanceLoopCharacteristics>     │
└───────────────┬──────────────────────────┘
                │
                ▼
┌──────────────────────────────────────────┐
│  ActivityBehaviorFactory 创建 Behavior   │  ◄─── 这里被替换！
│  - 默认：DefaultActivityBehaviorFactory   │
│  - 自定义：BpmActivityBehaviorFactory     │
└───────────────┬──────────────────────────┘
                │
                ▼
┌──────────────────────────────────────────┐
│  为每个节点创建对应的 Behavior 实例       │
│  - UserTask → BpmUserTaskActivityBehavior │
│  - Parallel MI → BpmParallel...Behavior   │
└──────────────────────────────────────────┘
```

#### 2. 任务创建流程

```
流程运行到 UserTask 节点
        │
        ▼
┌─────────────────────────────────────────────┐
│ BpmUserTaskActivityBehavior.execute()       │
│ - 调用 handleAssignments() 分配任务         │
└───────────┬─────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────┐
│ calculateTaskCandidateUsers()               │
│ 1. 判断是否为多实例？                        │
│    - 是：从变量中获取当前实例的用户ID        │
│    - 否：调用 taskCandidateInvoker 计算      │
│ 2. 从候选人集合中随机选择一个                 │
└───────────┬─────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────┐
│ TaskHelper.changeTaskAssignee()             │
│ - 设置任务的 assignee 字段                   │
│ - 任务正式创建                               │
└─────────────────────────────────────────────┘
```

#### 3. 多实例任务流程

**并行多实例（会签）**：

```
流程运行到多实例 UserTask
        │
        ▼
┌──────────────────────────────────────────────┐
│ BpmParallelMultiInstanceBehavior             │
│ .resolveNrOfInstances()                      │
│ - 调用 taskCandidateInvoker 计算候选人集合   │
│ - 将集合存入 execution 变量                   │
│ - 返回集合大小（实例数量）                    │
└───────────┬──────────────────────────────────┘
            │
            ▼
┌──────────────────────────────────────────────┐
│ Flowable 引擎创建 N 个任务实例                │
│ 每个实例对应候选人集合中的一个用户             │
└───────────┬──────────────────────────────────┘
            │
            ▼
┌──────────────────────────────────────────────┐
│ BpmUserTaskActivityBehavior                  │
│ - 从变量中获取当前实例的用户ID                │
│ - 直接设置为 assignee                         │
└──────────────────────────────────────────────┘
```

**串行多实例（或签）**：

```
流程运行到串行多实例 UserTask
        │
        ▼
┌──────────────────────────────────────────────┐
│ BpmSequentialMultiInstanceBehavior           │
│ .resolveNrOfInstances()                      │
│ - 计算候选人集合（同并行）                    │
│ - 返回实例数量                                │
└───────────┬──────────────────────────────────┘
            │
            ▼
┌──────────────────────────────────────────────┐
│ Flowable 引擎串行创建任务                     │
│ 第1个实例完成 → 第2个实例 → ... → 第N个        │
└───────────┬──────────────────────────────────┘
            │
            ▼
┌──────────────────────────────────────────────┐
│ executeOriginalBehavior()                    │
│ - 每次执行前重置变量（防止回退异常）          │
│ - 创建当前实例的任务                          │
└──────────────────────────────────────────────┘
```

---

## 📦 组件详解

### 1. BpmActivityBehaviorFactory

**职责**：自定义的行为工厂，替换 Flowable 默认的工厂实现

**核心功能**：
- 注册 `BpmUserTaskActivityBehavior` 作为用户任务的默认行为
- 注册 `BpmParallelMultiInstanceBehavior` 处理并行多实例
- 注册 `BpmSequentialMultiInstanceBehavior` 处理串行多实例
- 将 `BpmTaskCandidateInvoker` 注入到各个 Behavior 中

**关键代码**：

```java
@Setter
public class BpmActivityBehaviorFactory extends DefaultActivityBehaviorFactory {

    private BpmTaskCandidateInvoker taskCandidateInvoker;

    @Override
    public UserTaskActivityBehavior createUserTaskActivityBehavior(UserTask userTask) {
        // 替换默认的 UserTaskActivityBehavior
        return new BpmUserTaskActivityBehavior(userTask)
                .setTaskCandidateInvoker(taskCandidateInvoker);
    }

    @Override
    public ParallelMultiInstanceBehavior createParallelMultiInstanceBehavior(
            Activity activity, AbstractBpmnActivityBehavior behavior) {
        // 替换默认的并行多实例行为
        return new BpmParallelMultiInstanceBehavior(activity, behavior)
                .setTaskCandidateInvoker(taskCandidateInvoker);
    }

    @Override
    public SequentialMultiInstanceBehavior createSequentialMultiInstanceBehavior(
            Activity activity, AbstractBpmnActivityBehavior behavior) {
        // 替换默认的串行多实例行为
        return new BpmSequentialMultiInstanceBehavior(activity, behavior)
                .setTaskCandidateInvoker(taskCandidateInvoker);
    }
}
```

**配置方式**：

在 Flowable 配置类中注册：

```java
@Configuration
public class FlowableConfiguration {

    @Bean
    public ProcessEngineConfiguration processEngineConfiguration(
            BpmTaskCandidateInvoker taskCandidateInvoker) {

        SpringProcessEngineConfiguration config = new SpringProcessEngineConfiguration();

        // 创建自定义行为工厂
        BpmActivityBehaviorFactory behaviorFactory = new BpmActivityBehaviorFactory();
        behaviorFactory.setTaskCandidateInvoker(taskCandidateInvoker);

        // 注册到引擎配置
        config.setActivityBehaviorFactory(behaviorFactory);

        return config;
    }
}
```

---

### 2. BpmUserTaskActivityBehavior

**职责**：控制单个用户任务的执行行为，特别是 assignee 的分配

**核心设计理念**：**任务责任到人**

项目要求每个任务必须有且仅有一个 assignee，不允许多人同时处理同一任务。如果业务需要多人处理，应使用多实例任务（会签/或签）。

**关键方法**：

#### handleAssignments()

完全接管 Flowable 的任务分配逻辑，忽略 BPMN 中的 `assignee`、`candidateUsers` 等配置。

```java
@Override
protected void handleAssignments(TaskService taskService, String assignee, String owner,
                                 List<String> candidateUsers, List<String> candidateGroups,
                                 TaskEntity task, ExpressionManager expressionManager,
                                 DelegateExecution execution,
                                 ProcessEngineConfigurationImpl processEngineConfiguration) {

    // 计算出一个用户ID
    Long assigneeUserId = calculateTaskCandidateUsers(execution);

    // 设置为任务的 assignee
    if (assigneeUserId != null) {
        TaskHelper.changeTaskAssignee(task, String.valueOf(assigneeUserId));
    }
}
```

#### calculateTaskCandidateUsers()

计算任务应分配给哪个用户。

```java
private Long calculateTaskCandidateUsers(DelegateExecution execution) {
    // 情况1：多实例任务 → 从变量中获取当前实例对应的用户ID
    if (super.multiInstanceActivityBehavior != null) {
        return execution.getVariable(
            super.multiInstanceActivityBehavior.getCollectionElementVariable(),
            Long.class
        );
    }

    // 情况2：普通单实例任务 → 计算候选人并随机选择一个
    Set<Long> candidateUserIds = taskCandidateInvoker.calculateUsersByTask(execution);
    if (CollUtil.isEmpty(candidateUserIds)) {
        return null; // 无候选人
    }

    // 从候选人中随机选择一个作为 assignee
    int index = RandomUtil.randomInt(candidateUserIds.size());
    return CollUtil.get(candidateUserIds, index);
}
```

#### handleCategory()

设置任务的 category 字段（用于任务分类）。

```java
@Override
protected void handleCategory(CreateUserTaskBeforeContext beforeContext,
                              ExpressionManager expressionManager,
                              TaskEntity task, DelegateExecution execution) {
    // 从流程定义中获取 category，设置到任务上
    ProcessDefinitionEntity processDefinition =
        CommandContextUtil.getProcessDefinitionEntityManager()
            .findById(execution.getProcessDefinitionId());

    if (processDefinition != null) {
        task.setCategory(processDefinition.getCategory());
    }
}
```

**使用场景**：

| 场景 | 处理方式 |
|------|---------|
| 普通审批任务 | 计算候选人 → 随机选择一个 → 设置为 assignee |
| 会签任务（多实例） | 从变量中获取当前实例对应的用户 → 设置为 assignee |
| 候选人为空 | assignee 为 null（任务无负责人，可能卡住流程） |

**注意事项**：

⚠️ **候选人为空时的风险**

如果计算出的候选人为空，`assigneeUserId` 为 null，任务将没有负责人，可能导致流程卡住。建议在业务层增加校验或兜底机制。

```java
// 建议增加异常处理
if (assigneeUserId == null) {
    log.error("[handleAssignments][任务({}) 无候选人，流程可能卡住]", task.getId());
    // 可选：分配给默认管理员
    // assigneeUserId = DEFAULT_ADMIN_USER_ID;
    // 或抛出异常阻止任务创建
    // throw new BpmException("任务无候选人，无法创建");
}
```

---

### 3. BpmParallelMultiInstanceBehavior

**职责**：控制并行多实例任务（会签、并行审批）的执行

**业务场景**：
- **会签**：多个审批人同时审批，全部通过才继续流程
- **或签**：多个审批人同时审批，一个通过即可继续
- **并行审批**：多个部门负责人并行审批

**核心方法**：

#### resolveNrOfInstances()

确定并行实例的数量，并为每个实例准备对应的用户ID。

```java
@Override
protected int resolveNrOfInstances(DelegateExecution execution) {
    FlowElement currentElement = execution.getCurrentFlowElement();

    // ========== 情况一：UserTask ==========
    if (currentElement instanceof UserTask) {
        // 1. 设置多实例变量名
        super.collectionExpression = null;
        super.collectionVariable =
            FlowableUtils.formatExecutionCollectionVariable(execution.getCurrentActivityId());
        super.collectionElementVariable =
            FlowableUtils.formatExecutionCollectionElementVariable(execution.getCurrentActivityId());

        // 2. 计算候选人集合
        Set<Long> assigneeUserIds = (Set<Long>) execution.getVariable(
            super.collectionVariable, Set.class
        );

        if (assigneeUserIds == null) {
            // 调用候选人计算器
            assigneeUserIds = taskCandidateInvoker.calculateUsersByTask(execution);

            // 特殊处理：候选人为空时，插入一个 null 元素
            // 确保至少创建一个任务（用于自动通过/拒绝场景）
            if (CollUtil.isEmpty(assigneeUserIds)) {
                assigneeUserIds = SetUtils.asSet((Long) null);
            }

            // 存入局部变量（不影响父流程）
            execution.setVariableLocal(super.collectionVariable, assigneeUserIds);
        }

        // 3. 返回实例数量
        return assigneeUserIds.size();
    }

    // ========== 情况二：CallActivity（子流程） ==========
    if (currentElement instanceof CallActivity) {
        Integer sourceType = BpmnModelUtils.parseMultiInstanceSourceType(currentElement);

        if (BpmChildProcessMultiInstanceSourceTypeEnum.NUMBER_FORM.getType().equals(sourceType)) {
            // 数字表单：变量值为 Integer
            return execution.getVariable(
                super.collectionExpression.getExpressionText(), Integer.class
            );
        }

        if (BpmChildProcessMultiInstanceSourceTypeEnum.MULTIPLE_FORM.getType().equals(sourceType)) {
            // 多选表单：变量值为 List
            List<?> list = execution.getVariable(
                super.collectionExpression.getExpressionText(), List.class
            );
            return list != null ? list.size() : 0;
        }
    }

    // ========== 其他情况：回退到父类 ==========
    return super.resolveNrOfInstances(execution);
}
```

**变量命名规则**：

```java
// 集合变量名：collection_{activityId}
// 例如：collection_task_manager_approve
super.collectionVariable = "collection_" + activityId;

// 元素变量名：element_{activityId}
// 例如：element_task_manager_approve
super.collectionElementVariable = "element_" + activityId;
```

**执行流程示例**：

```
假设：财务部有3个负责人（ID: 101, 102, 103）

1. resolveNrOfInstances() 计算候选人
   → assigneeUserIds = {101, 102, 103}
   → 返回 3

2. Flowable 引擎创建3个任务实例
   ┌───────────────────────────────────┐
   │ 实例1: element_xxx = 101          │
   │ 实例2: element_xxx = 102          │
   │ 实例3: element_xxx = 103          │
   └───────────────────────────────────┘

3. BpmUserTaskActivityBehavior 为每个实例创建任务
   → 任务1: assignee = 101
   → 任务2: assignee = 102
   → 任务3: assignee = 103

4. 三个审批人同时收到任务，并行审批
```

**会签 vs 或签**：

由 BPMN 的 `completionCondition` 配置决定：

```xml
<!-- 会签：全部通过才继续 -->
<multiInstanceLoopCharacteristics isSequential="false">
  <completionCondition>
    ${nrOfCompletedInstances == nrOfInstances}
  </completionCondition>
</multiInstanceLoopCharacteristics>

<!-- 或签：一人通过即可 -->
<multiInstanceLoopCharacteristics isSequential="false">
  <completionCondition>
    ${nrOfCompletedInstances >= 1}
  </completionCondition>
</multiInstanceLoopCharacteristics>
```

---

### 4. BpmSequentialMultiInstanceBehavior

**职责**：控制串行多实例任务（顺序审批、依次审批）

**业务场景**：
- **逐级审批**：部门负责人 → 分管副总 → 总经理
- **顺序会签**：多个审批人依次审批（必须全部通过）
- **传阅审批**：依次传阅，每人审批后传给下一人

**核心方法**：

#### resolveNrOfInstances()

与并行多实例逻辑类似，但审批人集合应保持顺序。

```java
@Override
protected int resolveNrOfInstances(DelegateExecution execution) {
    if (execution.getCurrentFlowElement() instanceof UserTask) {
        // 设置变量名
        super.collectionExpression = null;
        super.collectionVariable =
            FlowableUtils.formatExecutionCollectionVariable(execution.getCurrentActivityId());
        super.collectionElementVariable =
            FlowableUtils.formatExecutionCollectionElementVariable(execution.getCurrentActivityId());

        // 从本地变量读取（避免回退后重复计算）
        Set<Long> assigneeUserIds = (Set<Long>) execution.getVariableLocal(
            super.collectionVariable, Set.class
        );

        if (assigneeUserIds == null) {
            assigneeUserIds = taskCandidateInvoker.calculateUsersByTask(execution);

            // 候选人为空时，插入 null
            if (CollUtil.isEmpty(assigneeUserIds)) {
                assigneeUserIds = SetUtils.asSet((Long) null);
            }

            // 缓存到本地变量
            execution.setVariableLocal(super.collectionVariable, assigneeUserIds);
        }

        return assigneeUserIds.size();
    }

    // CallActivity 处理逻辑同并行
    if (execution.getCurrentFlowElement() instanceof CallActivity) {
        // ... 同 BpmParallelMultiInstanceBehavior
    }

    return super.resolveNrOfInstances(execution);
}
```

#### executeOriginalBehavior()

**关键修复**：防止流程回退后变量未正确重置导致的异常。

```java
@Override
protected void executeOriginalBehavior(DelegateExecution execution,
                                      ExecutionEntity multiInstanceRootExecution,
                                      int loopCounter) {
    // 子流程节点直接走父类逻辑
    if (execution.getCurrentFlowElement() instanceof CallActivity
            || execution.getCurrentFlowElement() instanceof SubProcess) {
        super.executeOriginalBehavior(execution, multiInstanceRootExecution, loopCounter);
        return;
    }

    // 修复关键点：每次执行前重置变量配置
    // 参考：https://gitee.com/zhijiantianya/yudao-cloud/issues/IC239F
    super.collectionExpression = null;
    super.collectionVariable =
        FlowableUtils.formatExecutionCollectionVariable(execution.getCurrentActivityId());
    super.collectionElementVariable =
        FlowableUtils.formatExecutionCollectionElementVariable(execution.getCurrentActivityId());

    // 执行原始行为（创建任务）
    super.executeOriginalBehavior(execution, multiInstanceRootExecution, loopCounter);
}
```

**为什么需要重置变量？**

在流程回退（Reject）场景下，如果审批人驳回到某个多实例任务节点，Flowable 会重新执行该节点。但此时内部的 `collectionExpression` 可能未正确清理，导致执行异常。因此每次执行前显式重置。

**执行流程示例**：

```
假设：需要3级审批（ID: 201, 202, 203）

1. resolveNrOfInstances() 返回 3

2. Flowable 引擎串行执行
   ┌──────────────────────────────┐
   │ 第1次循环: loopCounter = 0    │
   │ element_xxx = 201             │
   │ → 创建任务，assignee = 201    │
   │ → 201 审批完成                │
   ├──────────────────────────────┤
   │ 第2次循环: loopCounter = 1    │
   │ element_xxx = 202             │
   │ → 创建任务，assignee = 202    │
   │ → 202 审批完成                │
   ├──────────────────────────────┤
   │ 第3次循环: loopCounter = 2    │
   │ element_xxx = 203             │
   │ → 创建任务，assignee = 203    │
   │ → 203 审批完成                │
   └──────────────────────────────┘

3. 所有实例完成，流程继续
```

**并行 vs 串行对比**：

| 对比项 | 并行多实例 | 串行多实例 |
|--------|-----------|-----------|
| 执行方式 | 同时创建所有任务 | 依次创建任务 |
| 完成条件 | 由 `completionCondition` 决定 | 全部完成才继续 |
| 使用场景 | 会签、或签 | 逐级审批、顺序审批 |
| 变量作用域 | 局部变量（setVariableLocal） | 局部变量 |
| 回退处理 | 较简单 | 需要重置变量 |

---

## 🚀 使用指南

### 基本配置

#### 1. 注册自定义行为工厂

在 Flowable 配置类中注册 `BpmActivityBehaviorFactory`：

```java
@Configuration
public class FlowableConfiguration {

    @Autowired
    private BpmTaskCandidateInvoker taskCandidateInvoker;

    @Bean
    public SpringProcessEngineConfiguration processEngineConfiguration(
            DataSource dataSource,
            PlatformTransactionManager transactionManager) {

        SpringProcessEngineConfiguration config = new SpringProcessEngineConfiguration();
        config.setDataSource(dataSource);
        config.setTransactionManager(transactionManager);

        // 创建并注册自定义行为工厂
        BpmActivityBehaviorFactory behaviorFactory = new BpmActivityBehaviorFactory();
        behaviorFactory.setTaskCandidateInvoker(taskCandidateInvoker);
        config.setActivityBehaviorFactory(behaviorFactory);

        return config;
    }
}
```

#### 2. BPMN 配置示例

**普通用户任务**：

```xml
<userTask id="task_manager_approve" name="经理审批">
  <extensionElements>
    <!-- 候选人策略：21 = 部门负责人 -->
    <flowable:candidateStrategy>21</flowable:candidateStrategy>
    <!-- 策略参数：100|1 = 部门ID 100，向上1级 -->
    <flowable:candidateParam>100|1</flowable:candidateParam>
  </extensionElements>
</userTask>
```

**并行多实例（会签）**：

```xml
<userTask id="task_parallel_approve" name="财务部会签">
  <extensionElements>
    <flowable:candidateStrategy>20</flowable:candidateStrategy>
    <flowable:candidateParam>200</flowable:candidateParam>
  </extensionElements>

  <!-- 并行多实例配置 -->
  <multiInstanceLoopCharacteristics isSequential="false">
    <!-- 完成条件：全部通过 -->
    <completionCondition>
      ${nrOfCompletedInstances == nrOfInstances}
    </completionCondition>
  </multiInstanceLoopCharacteristics>
</userTask>
```

**串行多实例（逐级审批）**：

```xml
<userTask id="task_sequential_approve" name="逐级审批">
  <extensionElements>
    <flowable:candidateStrategy>38</flowable:candidateStrategy>
    <!-- 38 = 发起人连续多级部门负责人，参数：3 = 向上3级 -->
    <flowable:candidateParam>3</flowable:candidateParam>
  </extensionElements>

  <!-- 串行多实例配置 -->
  <multiInstanceLoopCharacteristics isSequential="true">
    <!-- 串行默认全部完成 -->
  </multiInstanceLoopCharacteristics>
</userTask>
```

---

## 🔍 典型场景

### 场景 1：普通审批任务

**需求**：报销申请需要财务部门负责人审批

**配置**：

```xml
<userTask id="task_finance_approve" name="财务审批">
  <extensionElements>
    <flowable:candidateStrategy>21</flowable:candidateStrategy>
    <flowable:candidateParam>300</flowable:candidateParam> <!-- 财务部门ID -->
  </extensionElements>
</userTask>
```

**执行流程**：

```
1. 流程运行到该节点
   ↓
2. BpmUserTaskActivityBehavior.handleAssignments()
   ↓
3. taskCandidateInvoker.calculateUsersByTask()
   → 查询财务部门负责人 → 返回 {userId: 1001}
   ↓
4. 从候选人中随机选择一个 → 1001
   ↓
5. TaskHelper.changeTaskAssignee(task, "1001")
   ↓
6. 任务创建成功，assignee = 1001
```

---

### 场景 2：财务部会签

**需求**：重要合同需要财务部所有成员会签，全部通过才继续

**配置**：

```xml
<userTask id="task_finance_countersign" name="财务部会签">
  <extensionElements>
    <flowable:candidateStrategy>20</flowable:candidateStrategy>
    <flowable:candidateParam>300</flowable:candidateParam> <!-- 财务部门ID -->
  </extensionElements>

  <multiInstanceLoopCharacteristics isSequential="false">
    <completionCondition>
      ${nrOfCompletedInstances == nrOfInstances}
    </completionCondition>
  </multiInstanceLoopCharacteristics>
</userTask>
```

**执行流程**：

```
1. BpmParallelMultiInstanceBehavior.resolveNrOfInstances()
   ↓
2. taskCandidateInvoker.calculateUsersByTask()
   → 查询财务部门所有成员 → {1001, 1002, 1003}
   ↓
3. 存入变量：collection_task_finance_countersign = {1001, 1002, 1003}
   ↓
4. 返回实例数量：3
   ↓
5. Flowable 创建3个并行任务
   - 任务1: assignee = 1001
   - 任务2: assignee = 1002
   - 任务3: assignee = 1003
   ↓
6. 三人同时审批，全部通过后流程继续
```

---

### 场景 3：逐级审批（连续3级部门负责人）

**需求**：张三的请假申请需要逐级审批：部门经理 → 事业部总监 → 公司副总

**配置**：

```xml
<userTask id="task_level_approve" name="逐级审批">
  <extensionElements>
    <flowable:candidateStrategy>38</flowable:candidateStrategy>
    <flowable:candidateParam>3</flowable:candidateParam> <!-- 向上3级 -->
  </extensionElements>

  <multiInstanceLoopCharacteristics isSequential="true" />
</userTask>
```

**执行流程**：

```
假设组织架构：研发一组 → 研发部 → 技术中心 → 公司

1. BpmSequentialMultiInstanceBehavior.resolveNrOfInstances()
   ↓
2. taskCandidateInvoker.calculateUsersByTask()
   → 查询张三所在部门向上3级的负责人
   → {研发部经理: 2001, 技术中心总监: 2002, 公司副总: 2003}
   ↓
3. 返回实例数量：3
   ↓
4. Flowable 串行执行
   ┌─────────────────────────────┐
   │ 第1次循环                    │
   │ element_xxx = 2001           │
   │ → 创建任务，assignee = 2001  │
   │ → 研发部经理审批             │
   │ → 通过后进入第2次循环        │
   ├─────────────────────────────┤
   │ 第2次循环                    │
   │ element_xxx = 2002           │
   │ → 创建任务，assignee = 2002  │
   │ → 技术中心总监审批           │
   │ → 通过后进入第3次循环        │
   ├─────────────────────────────┤
   │ 第3次循环                    │
   │ element_xxx = 2003           │
   │ → 创建任务，assignee = 2003  │
   │ → 公司副总审批               │
   │ → 通过后流程继续             │
   └─────────────────────────────┘
```

---

## 🔧 高级特性

### 1. 候选人为空的处理

**问题**：如果计算出的候选人为空（如部门负责人未配置），会发生什么？

**处理方式**：

在多实例行为中，会插入一个 `null` 元素：

```java
if (CollUtil.isEmpty(assigneeUserIds)) {
    assigneeUserIds = SetUtils.asSet((Long) null);
}
```

**目的**：
- 确保至少创建一个任务实例
- 适用于"自动通过"、"自动拒绝"等场景
- 任务的 assignee 为 null，可通过代码逻辑处理

**建议**：

在业务层增加校验，防止候选人为空：

```java
// 在 BpmTaskCandidateInvoker.calculateUsersByTask() 中
Set<Long> candidateUserIds = strategy.calculateUsersByTask(execution);
if (CollUtil.isEmpty(candidateUserIds)) {
    // 启用兜底策略
    candidateUserIds = assignEmptyStrategy.calculateUsersByTask(execution);

    if (CollUtil.isEmpty(candidateUserIds)) {
        throw new BpmException("任务候选人为空，无法继续流程");
    }
}
```

---

### 2. 随机选择 Assignee 的原因

在 `BpmUserTaskActivityBehavior.calculateTaskCandidateUsers()` 中，从候选人集合中**随机选择一个**作为 assignee。

**为什么随机？**

1. **任务责任到人**：项目要求每个任务必须有一个明确的负责人
2. **避免任务积压**：如果设置多个候选人，可能出现"都能处理，但都不处理"的情况
3. **负载均衡**：随机分配可以让任务相对均匀地分配给各个候选人
4. **业务需要多人处理**：应使用多实例任务（会签/或签），而非单任务多候选人

**如果需要轮询分配？**

可以修改随机逻辑为轮询：

```java
// 替换：int index = RandomUtil.randomInt(candidateUserIds.size());
// 使用 Redis 记录每个节点的分配索引
String key = "bpm:assignee:index:" + execution.getCurrentActivityId();
Long index = redisTemplate.opsForValue().increment(key, 1);
int actualIndex = (int) (index % candidateUserIds.size());
return CollUtil.get(candidateUserIds, actualIndex);
```

---

### 3. 多实例变量的作用域

**局部变量 vs 全局变量**：

```java
// 局部变量（推荐）：仅在当前节点及其子节点可见
execution.setVariableLocal(varName, value);

// 全局变量：整个流程实例可见，可能被其他节点修改
execution.setVariable(varName, value);
```

**为什么使用局部变量？**

- 避免变量名冲突（不同节点可能有相同的变量名）
- 防止影响父流程或并行分支
- 作用域更清晰，便于调试

---

### 4. 流程回退时的变量重置

在串行多实例中，`executeOriginalBehavior()` 每次执行前都会重置变量配置：

```java
super.collectionExpression = null;
super.collectionVariable = FlowableUtils.formatExecutionCollectionVariable(...);
super.collectionElementVariable = FlowableUtils.formatExecutionCollectionElementVariable(...);
```

**为什么需要？**

- **流程回退**：审批人驳回到某个多实例节点时，Flowable 会重新执行该节点
- **变量未清理**：如果 `collectionExpression` 未清理，Flowable 可能尝试用表达式解析，导致异常
- **强制使用变量模式**：显式设置为 null，确保使用 `collectionVariable` 模式

**参考问题**：
- [知识星球问题](https://t.zsxq.com/53Meo)
- [Gitee Issue IC239F](https://gitee.com/zhijiantianya/yudao-cloud/issues/IC239F)

---

## 📌 注意事项

### 1. 性能优化

**问题**：每次计算候选人都会查询数据库，可能影响性能。

**优化建议**：

- 在 `BpmTaskCandidateInvoker` 中增加缓存
- 使用 Redis 缓存角色-用户映射、部门-用户映射等
- 批量查询优化（如一次查询多个部门的用户）

### 2. 事务管理

`handleAssignments()` 方法已标记为事务：

```java
@Override
@Transactional(rollbackFor = Exception.class)
protected void handleAssignments(...) {
    // ...
}
```

确保任务分配与流程执行在同一事务中，失败时自动回滚。

### 3. 多租户支持

在多租户环境中，需要确保：

- 候选人计算时考虑租户隔离
- 变量存储时包含租户信息
- `BpmTaskCandidateInvoker` 中已处理租户上下文

### 4. 审计日志

建议在关键节点增加日志：

```java
log.info("[calculateTaskCandidateUsers][任务({}) 计算候选人: {}]",
    task.getId(), candidateUserIds);
```

便于问题排查和审计追溯。

---

## 🧪 测试建议

### 单元测试

```java
@SpringBootTest
class BpmUserTaskActivityBehaviorTest {

    @MockBean
    private BpmTaskCandidateInvoker taskCandidateInvoker;

    @Test
    void testHandleAssignments_SingleCandidate() {
        // 准备数据
        Set<Long> candidates = Collections.singleton(1001L);
        when(taskCandidateInvoker.calculateUsersByTask(any()))
            .thenReturn(candidates);

        // 执行测试
        // ... 模拟任务创建

        // 断言
        assertThat(task.getAssignee()).isEqualTo("1001");
    }

    @Test
    void testHandleAssignments_MultipleCandidates() {
        // 多个候选人，验证随机选择
        Set<Long> candidates = Sets.newHashSet(1001L, 1002L, 1003L);
        when(taskCandidateInvoker.calculateUsersByTask(any()))
            .thenReturn(candidates);

        // 执行测试
        // ...

        // 断言：assignee 是候选人之一
        assertThat(task.getAssignee()).isIn("1001", "1002", "1003");
    }

    @Test
    void testHandleAssignments_NoCandidates() {
        // 无候选人，验证 assignee 为 null
        when(taskCandidateInvoker.calculateUsersByTask(any()))
            .thenReturn(Collections.emptySet());

        // 执行测试
        // ...

        // 断言
        assertThat(task.getAssignee()).isNull();
    }
}
```

### 集成测试

```java
@SpringBootTest
class BpmMultiInstanceIntegrationTest {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Test
    void testParallelMultiInstance() {
        // 启动流程
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
            "test_parallel_process"
        );

        // 查询任务
        List<Task> tasks = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .list();

        // 断言：创建了3个并行任务
        assertThat(tasks).hasSize(3);

        // 完成所有任务
        tasks.forEach(task -> taskService.complete(task.getId()));

        // 断言：流程继续
        assertThat(runtimeService.createExecutionQuery()
            .processInstanceId(processInstance.getId())
            .count()).isZero();
    }
}
```

---

## 📚 相关文档

### 内部文档
- [任务候选人策略体系](../candidate/README.md)
- [Flowable 工具类](../util/README.md)

### 外部资源
- [Flowable 自定义 Behavior 官方文档](https://www.flowable.com/open-source/docs/bpmn/ch07-BPMN-Constructs#custom-task-behavior)
- [Flowable 多实例官方文档](https://www.flowable.com/open-source/docs/bpmn/ch07-BPMN-Constructs#multi-instance)
- [芋道 BPM 模块文档](https://doc.iocoder.cn/bpm/)

---

## 🤝 贡献指南

### 代码规范
- 继承 Flowable 的 Behavior 类，重写关键方法
- 必须注入 `BpmTaskCandidateInvoker` 并调用其方法
- 增加详细的注释，说明为什么要这样实现

### 常见扩展点
- 修改 assignee 选择策略（随机 → 轮询 → 优先级）
- 增加任务分配的审计日志
- 支持更多的多实例数据源（如外部 API）

---

## 📝 更新日志

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2025-01-XX | 初始版本，支持单实例和多实例任务 |

---

## 👥 维护者

- **芋道源码**：核心架构设计与实现
- **kemengkai**：多实例行为实现与优化

---

**⚡️ 快速导航**

| 组件 | 文件 |
|------|------|
| 行为工厂 | [BpmActivityBehaviorFactory.java](BpmActivityBehaviorFactory.java:14) |
| 单实例任务 | [BpmUserTaskActivityBehavior.java](BpmUserTaskActivityBehavior.java:25) |
| 并行多实例 | [BpmParallelMultiInstanceBehavior.java](BpmParallelMultiInstanceBehavior.java:22) |
| 串行多实例 | [BpmSequentialMultiInstanceBehavior.java](BpmSequentialMultiInstanceBehavior.java:20) |
| 候选人策略 | [../candidate/README.md](../candidate/README.md) |
