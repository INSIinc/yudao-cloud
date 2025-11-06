# BPM 任务候选人策略体系

## 📖 概述

本目录是芋道 yudao-cloud BPM 模块的**任务候选人策略体系**的核心实现，用于在 Flowable 工作流引擎中动态计算审批任务的候选人（审批人）。

### 什么是候选人策略？

在工作流审批中，每个审批节点需要确定"谁来审批"。候选人策略定义了各种计算审批人的规则，例如：
- **按角色**：将任务分配给拥有"财务经理"角色的所有用户
- **按部门**：将任务分配给"财务部"的所有成员或负责人
- **按发起人**：将任务分配给流程发起人的直属领导
- **动态选择**：由上一节点审批人在审批时动态指定下一审批人

### 核心优势

✅ **策略模式设计**：每种分配规则独立实现，易于扩展和维护
✅ **统一接口标准**：所有策略实现统一接口，调用方无需关心具体实现
✅ **灵活配置**：支持在 BPMN 流程定义中通过配置切换策略
✅ **运行时计算**：支持基于流程变量和上下文动态计算候选人
✅ **智能过滤**：自动过滤禁用用户，支持发起人跳过逻辑

---

## 🏗️ 架构设计

### 核心组件

```
candidate/
├── BpmTaskCandidateStrategy.java      # 策略接口（定义统一规范）
├── BpmTaskCandidateInvoker.java       # 策略调用器（核心调度器）
├── expression/                         # 表达式支持（Expression 策略辅助）
│   ├── BpmTaskAssignLeaderExpression.java
│   └── BpmTaskAssignStartUserExpression.java
└── strategy/                           # 策略实现（按类型分组）
    ├── user/                           # 用户相关策略
    ├── dept/                           # 部门（组织）相关策略
    ├── form/                           # 表单字段相关策略
    └── other/                          # 其他特殊策略
```

### 设计模式

本体系采用**策略模式（Strategy Pattern）**：

```
┌─────────────────────────────────────┐
│   BpmTaskCandidateInvoker (调度器)  │
│  - 管理所有策略实现                   │
│  - 根据策略类型分发任务               │
│  - 执行通用逻辑（过滤、校验）         │
└─────────────────┬───────────────────┘
                  │
                  ▼
         ┌────────────────────┐
         │   Strategy 接口    │ ◄─── 定义统一规范
         └────────┬───────────┘
                  │
         ┌────────┴────────────┐
         │                     │
    ┌────▼────┐         ┌─────▼─────┐
    │ Role    │         │ Dept      │
    │ Strategy│  ...    │ Strategy  │
    └─────────┘         └───────────┘
         ▲                     ▲
         │                     │
    具体实现类          具体实现类
```

---

## 📋 策略接口详解

### BpmTaskCandidateStrategy

所有候选人策略都必须实现此接口。

#### 核心方法

```java
public interface BpmTaskCandidateStrategy {

    // 1. 返回策略类型（用于策略注册和路由）
    BpmTaskCandidateStrategyEnum getStrategy();

    // 2. 校验策略参数是否合法（部署时调用）
    void validateParam(String param);

    // 3. 判断是否必须提供参数
    default boolean isParamRequired() {
        return true;
    }

    // 4. 基础计算方法：根据参数计算候选人（静态场景）
    default Set<Long> calculateUsers(String param) {
        throw new UnsupportedOperationException("该分配方法未实现");
    }

    // 5. 运行时计算：基于执行上下文计算候选人（流程运行时）
    default Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        return calculateUsers(param);
    }

    // 6. 模型预览：基于 BPMN 模型计算候选人（流程预览时）
    default Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId,
                                               String param, Long startUserId,
                                               String processDefinitionId,
                                               Map<String, Object> processVariables) {
        return calculateUsers(param);
    }
}
```

#### 方法调用时机

| 方法 | 调用时机 | 使用场景 |
|------|---------|---------|
| `validateParam()` | 流程部署时 | 校验配置合法性，防止部署错误流程 |
| `calculateUsers()` | 静态分析时 | 简单策略的基础实现 |
| `calculateUsersByTask()` | 流程运行时 | 任务创建时确定实际审批人 |
| `calculateUsersByActivity()` | 流程预览时 | 流程启动前展示审批路径 |

---

## 🔧 策略调用器详解

### BpmTaskCandidateInvoker

策略调用器是整个体系的核心调度器，负责：

1. **策略注册管理**：启动时自动注册所有策略实现到 Map
2. **流程定义校验**：部署时校验每个审批节点的候选人配置
3. **候选人计算**：运行时根据策略计算并返回候选人
4. **智能过滤**：自动移除禁用用户
5. **特殊处理**：处理候选人为空、发起人跳过等逻辑

#### 关键功能

##### 1. 流程部署校验

```java
// 部署流程前调用，确保配置完整
invoker.validateBpmnConfig(bpmnBytes);
```

**校验内容**：
- ✅ 每个 UserTask 是否配置了候选人策略
- ✅ 策略参数是否完整（如角色策略必须配置角色ID）
- ✅ 参数业务合法性（如角色ID是否存在）

##### 2. 运行时候选人计算

```java
// 流程运行到某个任务时调用
Set<Long> candidateUserIds = invoker.calculateUsersByTask(execution);
```

**执行流程**：
```
1. 解析节点配置（策略类型 + 参数）
   ↓
2. 调用对应策略实现计算候选人
   ↓
3. 移除已禁用的用户
   ↓
4. 候选人为空？→ 启用兜底策略
   ↓
5. 发起人跳过？→ 移除发起人
   ↓
6. 返回最终候选人集合
```

##### 3. 流程预览

```java
// 流程启动前预览审批路径
Set<Long> candidateUserIds = invoker.calculateUsersByActivity(
    bpmnModel, activityId, startUserId, processDefinitionId, processVariables
);
```

**使用场景**：
- 流程发起前展示"此流程将经过哪些审批人"
- 管理员模拟流程走向分析
- 流程优化时的候选人分布统计

---

## 📦 策略分类与实现

### 策略总览

本体系共支持 **15 种**候选人策略，分为 4 大类：

#### 1️⃣ 用户相关策略 (strategy/user/)

| 策略 | 枚举值 | 描述 | 文档 |
|------|--------|------|------|
| `USER` | 30 | 指定用户 | [详细文档](strategy/user/README.md#1-bpmtaskcandidateuserstrategy---指定用户) |
| `ROLE` | 10 | 角色 | [详细文档](strategy/user/README.md#2-bpmtaskcandidaterolesrtrategy---角色) |
| `POST` | 22 | 岗位 | [详细文档](strategy/user/README.md#3-bpmtaskcandidatepoststrategy---岗位) |
| `USER_GROUP` | 40 | 用户组 | [详细文档](strategy/user/README.md#4-bpmtaskcandidategroupstrategy---用户组) |
| `START_USER` | 36 | 发起人自己 | [详细文档](strategy/user/README.md#5-bpmtaskcandidatestartuserrtrategy---发起人自己) |

**适用场景**：基于用户身份、角色权限的固定分配

#### 2️⃣ 部门（组织）相关策略 (strategy/dept/)

| 策略 | 枚举值 | 描述 | 文档 |
|------|--------|------|------|
| `DEPT_MEMBER` | 20 | 部门成员 | [详细文档](strategy/dept/README.md#2-bpmtaskcandidatedeptmemberstrategy---部门成员) |
| `DEPT_LEADER` | 21 | 部门负责人 | [详细文档](strategy/dept/README.md#1-bpmtaskcandidatedeptleaderstrategy---指定部门负责人) |
| `MULTI_DEPT_LEADER_MULTI` | 23 | 连续多级部门负责人 | [详细文档](strategy/dept/README.md#3-bpmtaskcandidatedeptleadermultistrategy---连续多级部门负责人) |
| `START_USER_DEPT_LEADER` | 37 | 发起人部门负责人 | [详细文档](strategy/dept/README.md#4-bpmtaskcandidatestartuserrteptleaderstrategy---发起人部门负责人) |
| `START_USER_DEPT_LEADER_MULTI` | 38 | 发起人连续多级部门负责人 | [详细文档](strategy/dept/README.md#5-bpmtaskcandidatestartuserrteptleadermultistrategy---发起人多级部门负责人) |
| `START_USER_SELECT` | 35 | 发起人自选 | [详细文档](strategy/dept/README.md#6-bpmtaskcandidatestartuserselectstrategy---发起人自选) |
| `APPROVE_USER_SELECT` | 34 | 审批人自选 | [详细文档](strategy/dept/README.md#7-bpmtaskcandidateapproveuserrselectstrategy---审批人自选) |

**适用场景**：基于组织架构层级关系的动态分配

#### 3️⃣ 表单字段策略 (strategy/form/)

| 策略 | 枚举值 | 描述 |
|------|--------|------|
| `FORM_USER` | 50 | 表单内用户字段 |
| `FORM_DEPT_LEADER` | 51 | 表单内部门负责人 |

**适用场景**：根据流程表单中填写的字段动态确定审批人

**示例**：
- 报销单填写"直属领导"字段 → 由该领导审批
- 出差申请填写"所属部门"字段 → 由该部门负责人审批

#### 4️⃣ 其他特殊策略 (strategy/other/)

| 策略 | 枚举值 | 描述 |
|------|--------|------|
| `EXPRESSION` | 60 | 流程表达式 |
| `ASSIGN_EMPTY` | 1 | 审批人为空（兜底策略） |

**适用场景**：
- `EXPRESSION`：需要复杂计算逻辑（如调用自定义 Bean、访问外部服务）
- `ASSIGN_EMPTY`：自动任务或后续通过代码动态指派

---

## 🚀 使用指南

### 基本流程

#### 1. 流程设计阶段

在 BPMN 流程定义中配置候选人策略：

```xml
<userTask id="task_manager_approve" name="经理审批">
  <extensionElements>
    <!-- 策略类型：21 = 部门负责人 -->
    <flowable:candidateStrategy>21</flowable:candidateStrategy>
    <!-- 策略参数：100|1 = 部门ID 100，向上1级 -->
    <flowable:candidateParam>100|1</flowable:candidateParam>
  </extensionElements>
</userTask>
```

#### 2. 流程部署阶段

系统自动调用 `validateBpmnConfig()` 校验配置：

```java
@Service
public class BpmProcessDefinitionServiceImpl {

    @Resource
    private BpmTaskCandidateInvoker taskCandidateInvoker;

    public void deploy(byte[] bpmnBytes) {
        // 部署前校验候选人配置
        taskCandidateInvoker.validateBpmnConfig(bpmnBytes);

        // 执行部署...
    }
}
```

#### 3. 流程运行阶段

Flowable 引擎通过监听器自动调用 `calculateUsersByTask()`：

```java
@Component
public class BpmUserTaskListener implements TaskListener {

    @Resource
    private BpmTaskCandidateInvoker taskCandidateInvoker;

    @Override
    public void notify(DelegateTask task) {
        // 计算候选人
        Set<Long> candidateUserIds = taskCandidateInvoker.calculateUsersByTask(
            task.getExecution()
        );

        // 分配任务
        candidateUserIds.forEach(userId ->
            task.addCandidateUser(String.valueOf(userId))
        );
    }
}
```

#### 4. 流程预览阶段

在流程发起前展示审批路径：

```java
@Service
public class BpmProcessInstanceServiceImpl {

    @Resource
    private BpmTaskCandidateInvoker taskCandidateInvoker;

    public List<ActivityApproverVO> previewApprovers(String processDefinitionId, Long startUserId) {
        BpmnModel bpmnModel = getBpmnModel(processDefinitionId);
        List<UserTask> userTasks = BpmnModelUtils.getBpmnModelElements(bpmnModel, UserTask.class);

        return userTasks.stream().map(userTask -> {
            // 预览该节点的候选人
            Set<Long> candidateUserIds = taskCandidateInvoker.calculateUsersByActivity(
                bpmnModel,
                userTask.getId(),
                startUserId,
                processDefinitionId,
                Collections.emptyMap()
            );

            return new ActivityApproverVO(userTask.getName(), candidateUserIds);
        }).collect(Collectors.toList());
    }
}
```

---

## 🔌 扩展指南

### 如何新增自定义策略？

#### 步骤 1：定义策略枚举

编辑 `BpmTaskCandidateStrategyEnum.java`：

```java
@Getter
@AllArgsConstructor
public enum BpmTaskCandidateStrategyEnum implements ArrayValuable<Integer> {

    // ... 现有策略 ...

    /**
     * 自定义策略：VIP 客户经理
     */
    VIP_CUSTOMER_MANAGER(70, "VIP客户经理");

    // ...
}
```

#### 步骤 2：实现策略接口

创建策略实现类：

```java
package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.user;

import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * VIP 客户经理候选人策略
 */
@Component
public class BpmTaskCandidateVipManagerStrategy implements BpmTaskCandidateStrategy {

    @Resource
    private CustomerService customerService;

    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.VIP_CUSTOMER_MANAGER;
    }

    @Override
    public void validateParam(String param) {
        // 参数校验：可选，本例无需参数
    }

    @Override
    public boolean isParamRequired() {
        return false; // 不需要参数
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        // 查询所有 VIP 客户经理
        return customerService.getVipManagerIds();
    }
}
```

#### 步骤 3：注册策略（自动完成）

`BpmTaskCandidateInvoker` 会在启动时自动扫描并注册所有 `@Component` 标记的策略实现。

#### 步骤 4：配置到流程

在 BPMN 中使用新策略：

```xml
<userTask id="task_vip_approve" name="VIP客户经理审批">
  <extensionElements>
    <flowable:candidateStrategy>70</flowable:candidateStrategy>
  </extensionElements>
</userTask>
```

---

## 🧪 测试建议

### 单元测试示例

```java
@SpringBootTest
class BpmTaskCandidateInvokerTest {

    @Resource
    private BpmTaskCandidateInvoker invoker;

    @Test
    void testCalculateUsersByTask() {
        // 模拟执行上下文
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getCurrentFlowElement()).thenReturn(createUserTask());
        when(execution.getProcessInstanceId()).thenReturn("proc_123");

        // 执行计算
        Set<Long> candidates = invoker.calculateUsersByTask(execution);

        // 断言
        assertThat(candidates).isNotEmpty();
        assertThat(candidates).doesNotContain(DISABLED_USER_ID); // 已禁用用户被过滤
    }

    @Test
    void testValidateBpmnConfig_MissingCandidate_ThrowsException() {
        // 构造缺少候选人配置的 BPMN
        byte[] bpmnBytes = createInvalidBpmn();

        // 断言抛出异常
        assertThatThrownBy(() -> invoker.validateBpmnConfig(bpmnBytes))
            .hasMessageContaining("任务候选人不能为空");
    }
}
```

---

## 📌 注意事项

### 1. 性能优化

- **批量查询**：策略实现应使用批量查询避免 N+1 问题
- **缓存利用**：频繁查询的数据（如角色-用户映射）应使用 Redis 缓存
- **索引优化**：相关数据库表应建立合适索引

### 2. 安全考虑

- **数据权限**：`BpmTaskCandidateInvoker` 已禁用数据权限过滤（`@DataPermission(enable = false)`），确保能查询到所有候选人
- **禁用用户**：系统会自动过滤禁用用户，策略实现无需关心
- **租户隔离**：在多租户场景下，系统自动设置租户上下文

### 3. 兜底机制

当候选人计算结果为空时，会触发 `ASSIGN_EMPTY` 兜底策略。建议在系统配置中指定默认审批人（如管理员），避免流程卡住。

### 4. 发起人跳过逻辑

若配置了"审批人与发起人相同时跳过"，系统会自动从候选人中移除发起人。但为避免无人审批，仅在候选人数量 > 1 时生效。

---

## 📚 相关文档

### 内部文档
- [用户策略详细文档](strategy/user/README.md)
- [部门策略详细文档](strategy/dept/README.md)

### 外部资源
- [Flowable 官方文档](https://www.flowable.com/open-source/docs/)
- [芋道 BPM 开发文档](https://doc.iocoder.cn/bpm/)
- [BPMN 2.0 规范](https://www.omg.org/spec/BPMN/2.0/)

---

## 🤝 贡献指南

### 代码规范
- 所有策略实现必须添加 `@Component` 注解
- 类名必须以 `BpmTaskCandidate` 开头，`Strategy` 结尾
- 必须编写详细的 Javadoc 注释

### 文档规范
- 新增策略后需更新本 README 和对应子目录的 README
- 需提供使用示例和实际业务场景说明

---

## 📝 更新日志

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2025-01-XX | 初始版本，支持 15 种策略 |

---

## 👥 维护者

- **芋道源码**：核心架构设计与实现
- BPM 团队：策略扩展与优化

---

**⚡️ 快速导航**

| 分类 | 链接 |
|------|------|
| 核心接口 | [BpmTaskCandidateStrategy.java](BpmTaskCandidateStrategy.java:20) |
| 策略调用器 | [BpmTaskCandidateInvoker.java](BpmTaskCandidateInvoker.java:53) |
| 用户策略 | [strategy/user/README.md](strategy/user/README.md) |
| 部门策略 | [strategy/dept/README.md](strategy/dept/README.md) |
| 表单策略 | [strategy/form/](strategy/form/) |
| 其他策略 | [strategy/other/](strategy/other/) |
| 表达式支持 | [expression/](expression/) |
