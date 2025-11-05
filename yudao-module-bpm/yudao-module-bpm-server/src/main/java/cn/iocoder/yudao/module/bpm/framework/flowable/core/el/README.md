# BPM 表达式函数（EL Functions）

## 📖 概述

本目录包含 Flowable 工作流引擎中使用的**自定义 EL 表达式函数**，用于扩展 Flowable 默认的表达式能力，解决流程条件判断、变量类型转换等业务场景。

### 什么是 EL 表达式？

**EL（Expression Language，表达式语言）** 是一种简洁的脚本语言，广泛用于 Java Web 和工作流引擎中，用于动态计算和条件判断。

在 Flowable 中，EL 表达式常用于：
- **排他网关（Exclusive Gateway）**：根据条件选择分支
  ```xml
  <sequenceFlow sourceRef="gateway" targetRef="task1">
    <conditionExpression>${amount > 10000}</conditionExpression>
  </sequenceFlow>
  ```

- **任务分配（Assignee）**：动态指定任务负责人
  ```xml
  <userTask id="task" assignee="${userId}" />
  ```

- **监听器（Listener）**：执行自定义逻辑
  ```xml
  <flowable:executionListener expression="${myBean.doSomething(execution)}" />
  ```

### 为什么需要自定义 EL 函数？

Flowable 默认的 EL 表达式能力有限，无法处理复杂的业务逻辑。通过自定义函数，可以：

✅ **解决类型不匹配问题**：流程变量是字符串，但业务对象是 Long
✅ **实现复杂计算**：调用 Spring Bean 执行业务逻辑
✅ **增强表达式可读性**：封装常用逻辑为函数，简化表达式

---

## 📦 组件列表

| 类名 | 函数名 | 功能描述 | 使用场景 |
|------|--------|---------|---------|
| `VariableConvertByTypeExpressionFunction` | `convertByType` | 根据流程变量实际类型自动转换参数类型 | 排他网关条件表达式中的变量比较 |

---

## 🔧 VariableConvertByTypeExpressionFunction

### 核心功能

**自动类型转换**：根据流程变量的实际类型，动态转换传入的参数值，确保在表达式求值时类型兼容。

### 问题场景

**问题描述**：

在 Flowable 流程中，流程变量可能以不同类型存储，而业务逻辑传入的参数类型可能不一致，导致条件表达式判断失败。

**典型案例**：

```java
// 场景：审批流程中判断当前审批人是否为流程发起人

// 流程变量：发起人ID（存储为 String）
execution.setVariable("startUserId", "1001");

// 业务传参：当前审批人ID（Long 类型）
Long currentUserId = 1001L;

// 排他网关条件表达式
${currentUserId == startUserId}
// ❌ 结果：false（因为 1001L != "1001"）
```

**根本原因**：

- 流程变量 `startUserId` 在数据库中存储为 `VARCHAR`，读取时为 `String` 类型
- 业务参数 `currentUserId` 是 `Long` 类型
- Java 中 `Long` 和 `String` 直接比较永远返回 `false`

### 解决方案

使用 `convertByType()` 函数自动进行类型对齐：

```xml
<!-- BPMN 条件表达式 -->
<conditionExpression>
  ${convertByType(execution, 'startUserId', currentUserId) == execution.getVariable('startUserId')}
</conditionExpression>
```

**工作原理**：

```
1. 读取流程变量 startUserId 的实际值："1001"（String 类型）
   ↓
2. 检测到参数 currentUserId 是 Long 类型（1001L）
   ↓
3. 自动将 currentUserId 转换为 String："1001"
   ↓
4. 比较两个字符串："1001" == "1001"
   ↓
5. 返回 true ✅
```

### 方法签名

```java
public static Object convertByType(
    VariableContainer variableContainer,  // 变量容器（execution 或 task）
    String variableName,                   // 要参考的流程变量名
    Object paramValue                      // 待转换的参数值
)
```

### 参数说明

| 参数 | 类型 | 说明 |
|------|------|------|
| `variableContainer` | `VariableContainer` | 流程上下文容器，通常是 `execution` 或 `task` 对象 |
| `variableName` | `String` | 流程变量名，用于确定目标类型（如 `"startUserId"`） |
| `paramValue` | `Object` | 待转换的参数值（如 `1001L`、`userId` 等） |

**返回值**：
- 如果需要转换（目标是 String，参数不是 String），返回转换后的字符串
- 否则返回原始参数值

### 转换规则

当前实现的转换规则：

| 流程变量类型 | 参数类型 | 转换结果 |
|------------|---------|---------|
| `String` | `Long` | `paramValue.toString()` |
| `String` | `Integer` | `paramValue.toString()` |
| `String` | 其他对象 | `paramValue.toString()` |
| `Long` | `String` | 不转换（原样返回） |
| `Integer` | `String` | 不转换（原样返回） |
| 相同类型 | 相同类型 | 不转换（原样返回） |

**核心代码**：

```java
// 仅当"流程变量是 String，参数不是 String"时才转换
if (!(paramValue instanceof String) && variable instanceof String) {
    return paramValue.toString();
}
return paramValue;
```

---

## 🚀 使用指南

### 1. 在 BPMN 排他网关中使用

**场景**：根据审批人是否为发起人，选择不同分支

```xml
<exclusiveGateway id="gateway1" name="判断是否为发起人" />

<!-- 分支1：是发起人 -->
<sequenceFlow sourceRef="gateway1" targetRef="task_auto_pass">
  <conditionExpression xsi:type="tFormalExpression">
    <![CDATA[
      ${convertByType(execution, 'startUserId', assigneeUserId) == execution.getVariable('startUserId')}
    ]]>
  </conditionExpression>
</sequenceFlow>

<!-- 分支2：不是发起人 -->
<sequenceFlow sourceRef="gateway1" targetRef="task_normal_approve">
  <conditionExpression xsi:type="tFormalExpression">
    <![CDATA[
      ${convertByType(execution, 'startUserId', assigneeUserId) != execution.getVariable('startUserId')}
    ]]>
  </conditionExpression>
</sequenceFlow>
```

### 2. 在任务监听器中使用

**场景**：根据当前任务负责人动态设置流程变量

```xml
<userTask id="task_approve" name="经理审批">
  <extensionElements>
    <flowable:taskListener event="create" expression="${execution.setVariable('currentApprover', convertByType(execution, 'userId', task.assignee))}"/>
  </extensionElements>
</userTask>
```

### 3. 在 Java 代码中使用

虽然主要用于 BPMN 表达式，但也可以在 Java 代码中直接调用：

```java
@Service
public class BpmConditionService {

    public boolean checkApprover(DelegateExecution execution, Long userId) {
        // 直接调用静态方法
        Object convertedUserId = VariableConvertByTypeExpressionFunction.convertByType(
            execution,
            "startUserId",
            userId
        );

        Object startUserId = execution.getVariable("startUserId");
        return Objects.equals(convertedUserId, startUserId);
    }
}
```

---

## 📌 典型场景

### 场景 1：审批人与发起人相同时自动跳过

**业务需求**：

如果审批人恰好是流程发起人，则自动跳过该审批节点（避免自己审批自己）。

**流程设计**：

```
启动流程 → 排他网关 → [是发起人] → 自动通过
                  ↓
                [不是发起人] → 人工审批
```

**BPMN 配置**：

```xml
<exclusiveGateway id="gateway_check_starter" name="判断是否为发起人" />

<sequenceFlow sourceRef="gateway_check_starter" targetRef="task_auto_pass">
  <conditionExpression>
    ${convertByType(execution, 'startUserId', assigneeUserId) == startUserId}
  </conditionExpression>
</sequenceFlow>

<sequenceFlow sourceRef="gateway_check_starter" targetRef="task_manual_approve">
  <conditionExpression>
    ${convertByType(execution, 'startUserId', assigneeUserId) != startUserId}
  </conditionExpression>
</sequenceFlow>
```

---

### 场景 2：根据部门ID判断审批路径

**业务需求**：

财务部门（deptId=100）提交的报销单走快速审批，其他部门走普通审批。

**流程设计**：

```
启动流程 → 排他网关 → [财务部] → 财务经理审批
                  ↓
                [其他部门] → 总经理审批
```

**BPMN 配置**：

```xml
<exclusiveGateway id="gateway_dept" name="根据部门分流" />

<sequenceFlow sourceRef="gateway_dept" targetRef="task_finance_approve">
  <conditionExpression>
    ${convertByType(execution, 'deptId', currentDeptId) == '100'}
  </conditionExpression>
</sequenceFlow>

<sequenceFlow sourceRef="gateway_dept" targetRef="task_ceo_approve">
  <conditionExpression>
    ${convertByType(execution, 'deptId', currentDeptId) != '100'}
  </conditionExpression>
</sequenceFlow>
```

---

### 场景 3：金额范围判断

**业务需求**：

报销金额大于 10000 元需要总经理审批，否则部门经理审批即可。

**注意**：金额可能以 `String` 或 `BigDecimal` 存储，需要类型对齐。

**BPMN 配置**：

```xml
<exclusiveGateway id="gateway_amount" name="根据金额分流" />

<sequenceFlow sourceRef="gateway_amount" targetRef="task_ceo_approve">
  <conditionExpression>
    ${convertByType(execution, 'amount', amount) > 10000}
  </conditionExpression>
</sequenceFlow>

<sequenceFlow sourceRef="gateway_amount" targetRef="task_manager_approve">
  <conditionExpression>
    ${convertByType(execution, 'amount', amount) <= 10000}
  </conditionExpression>
</sequenceFlow>
```

---

## 🔍 技术细节

### 注册机制

该函数通过 Flowable 的扩展机制自动注册：

1. **继承基类**：
   ```java
   extends AbstractFlowableVariableExpressionFunction
   ```

2. **指定函数名**：
   ```java
   public VariableConvertByTypeExpressionFunction() {
       super("convertByType"); // 在 EL 中使用 ${convertByType(...)}
   }
   ```

3. **Spring 注册**：
   ```java
   @Component  // 自动扫描并注册为 Spring Bean
   ```

4. **Flowable 自动识别**：
   - Flowable 启动时扫描所有 `AbstractFlowableVariableExpressionFunction` 子类
   - 将其注册到表达式解析器（Expression Manager）中
   - 在 EL 表达式中可直接调用

### 表达式解析流程

```
1. BPMN 中配置条件表达式
   ${convertByType(execution, 'userId', currentUserId) == userId}
   ↓
2. Flowable 引擎解析表达式
   - 识别函数名：convertByType
   - 匹配注册的函数：VariableConvertByTypeExpressionFunction
   ↓
3. 调用静态方法 convertByType(...)
   - 传入 execution（VariableContainer）
   - 传入变量名 "userId"
   - 传入参数值 currentUserId
   ↓
4. 执行类型转换逻辑
   - 读取 execution.getVariable("userId") → "1001"（String）
   - 检测 currentUserId 是 Long（1001L）
   - 转换为 String："1001"
   ↓
5. 返回转换后的值，继续表达式计算
   "1001" == "1001" → true
```

### 性能优化

**缓存优化**：

表达式函数在流程执行期间会被频繁调用，建议在必要时增加缓存：

```java
private static final ConcurrentHashMap<String, Class<?>> TYPE_CACHE = new ConcurrentHashMap<>();

public static Object convertByType(VariableContainer variableContainer, String variableName, Object paramValue) {
    Object variable = variableContainer.getVariable(variableName);

    if (variable != null && paramValue != null) {
        // 缓存变量类型，避免重复反射
        Class<?> variableType = TYPE_CACHE.computeIfAbsent(
            variableName,
            k -> variable.getClass()
        );

        if (!(paramValue instanceof String) && variableType == String.class) {
            return paramValue.toString();
        }
    }

    return paramValue;
}
```

---

## 🧪 测试建议

### 单元测试

```java
@Test
void testConvertByType_StringToString() {
    // 准备数据
    DelegateExecution execution = mock(DelegateExecution.class);
    when(execution.getVariable("userId")).thenReturn("1001");

    // 测试：String 参数 → 不转换
    Object result = VariableConvertByTypeExpressionFunction.convertByType(
        execution, "userId", "1001"
    );

    assertThat(result).isEqualTo("1001");
}

@Test
void testConvertByType_LongToString() {
    // 准备数据
    DelegateExecution execution = mock(DelegateExecution.class);
    when(execution.getVariable("userId")).thenReturn("1001");

    // 测试：Long 参数 → 转换为 String
    Object result = VariableConvertByTypeExpressionFunction.convertByType(
        execution, "userId", 1001L
    );

    assertThat(result).isEqualTo("1001");
}

@Test
void testConvertByType_NullVariable() {
    // 准备数据
    DelegateExecution execution = mock(DelegateExecution.class);
    when(execution.getVariable("userId")).thenReturn(null);

    // 测试：变量为 null → 不转换
    Object result = VariableConvertByTypeExpressionFunction.convertByType(
        execution, "userId", 1001L
    );

    assertThat(result).isEqualTo(1001L);
}
```

### 集成测试

```java
@SpringBootTest
class ELFunctionIntegrationTest {

    @Autowired
    private RuntimeService runtimeService;

    @Test
    void testExclusiveGatewayWithConvertByType() {
        // 启动流程，设置 String 类型的 startUserId
        Map<String, Object> variables = new HashMap<>();
        variables.put("startUserId", "1001");
        variables.put("assigneeUserId", 1001L); // Long 类型

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
            "test_convert_process", variables
        );

        // 查询当前任务
        Task task = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .singleResult();

        // 断言：走了"是发起人"分支，自动通过
        assertThat(task.getName()).isEqualTo("自动通过");
    }
}
```

---

## 📚 扩展功能

### 扩展 1：支持更多类型转换

当前仅支持 String 类型转换，可扩展支持其他类型：

```java
public static Object convertByType(VariableContainer variableContainer, String variableName, Object paramValue) {
    Object variable = variableContainer.getVariable(variableName);

    if (variable != null && paramValue != null) {
        Class<?> variableClass = variable.getClass();
        Class<?> paramClass = paramValue.getClass();

        // String → Long
        if (variableClass == Long.class && paramClass == String.class) {
            return Long.parseLong((String) paramValue);
        }

        // String → Integer
        if (variableClass == Integer.class && paramClass == String.class) {
            return Integer.parseInt((String) paramValue);
        }

        // String → BigDecimal
        if (variableClass == BigDecimal.class && paramClass == String.class) {
            return new BigDecimal((String) paramValue);
        }

        // 原有逻辑：其他类型 → String
        if (!(paramValue instanceof String) && variableClass == String.class) {
            return paramValue.toString();
        }
    }

    return paramValue;
}
```

### 扩展 2：增加更多 EL 函数

可以参考 `VariableConvertByTypeExpressionFunction` 创建更多自定义函数：

**示例：日期比较函数**

```java
@Component
public class DateCompareExpressionFunction extends AbstractFlowableVariableExpressionFunction {

    public DateCompareExpressionFunction() {
        super("dateCompare");
    }

    public static boolean isAfter(Date date1, Date date2) {
        return date1.after(date2);
    }

    public static boolean isBefore(Date date1, Date date2) {
        return date1.before(date2);
    }
}
```

**在 BPMN 中使用**：

```xml
<conditionExpression>
  ${dateCompare:isAfter(submitDate, deadline)}
</conditionExpression>
```

---

## 📌 注意事项

### 1. 类型转换的单向性

当前实现只处理"其他类型 → String"的转换，不处理反向转换。

**原因**：
- Flowable 流程变量以字符串存储最为常见（表单提交、前端传参）
- 业务对象通常使用强类型（Long、Integer、BigDecimal）
- 因此转换方向为：业务对象 → 字符串

**如果需要反向转换**：

可以在 Java 代码层面处理，而非在 EL 表达式中：

```java
// 在 Service 层转换
Long userId = Long.parseLong(execution.getVariable("userId", String.class));
```

### 2. null 值处理

函数对 null 值友好，不会抛出异常：

```java
// 变量为 null
convertByType(execution, "userId", 1001L)  → 1001L（不转换）

// 参数为 null
convertByType(execution, "userId", null)  → null（不转换）
```

### 3. 表达式性能

EL 表达式在每次条件判断时都会执行，频繁调用可能影响性能。建议：

- 尽量简化表达式逻辑
- 避免在表达式中调用复杂的数据库查询
- 考虑将复杂逻辑移到 Java 监听器中

### 4. 调试技巧

如果表达式结果不符合预期，可以增加日志：

```java
public static Object convertByType(VariableContainer variableContainer, String variableName, Object paramValue) {
    Object variable = variableContainer.getVariable(variableName);

    log.debug("[convertByType] 变量名: {}, 变量值: {} (类型: {}), 参数值: {} (类型: {})",
        variableName,
        variable, variable != null ? variable.getClass().getSimpleName() : "null",
        paramValue, paramValue != null ? paramValue.getClass().getSimpleName() : "null"
    );

    // ... 转换逻辑
}
```

---

## 📝 更新日志

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2025-01-XX | 初始版本，支持 String 类型转换 |

---

## 👥 维护者

- **jason**：初始实现
- **芋道源码**：架构设计与文档

---

**⚡️ 快速导航**

| 主题 | 链接 |
|------|------|
| 源代码 | [VariableConvertByTypeExpressionFunction.java](VariableConvertByTypeExpressionFunction.java:8) |
| 相关文档 | [BPM 任务候选人策略](../candidate/README.md) |
| Flowable EL 文档 | [官方文档](https://www.flowable.com/open-source/docs/bpmn/ch07-BPMN-Constructs#expressions) |
