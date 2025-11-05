# 部门（组织）候选人策略

## 📖 概述

本目录包含基于组织架构（部门）的工作流任务候选人分配策略实现。这些策略用于在 Flowable 工作流引擎中，根据部门层级关系动态计算审批任务的候选人。

### 核心特性

- **层级查找**：支持向上查找指定层级的部门负责人
- **多级会签**：支持连续多级部门负责人的会签场景
- **动态选择**：支持发起人或审批人动态选择下一审批人
- **灵活配置**：支持固定部门和发起人部门两种模式

---

## 📁 目录结构

```
dept/
├── AbstractBpmTaskCandidateDeptLeaderStrategy.java       # 抽象基类（工具类）
├── BpmTaskCandidateDeptLeaderStrategy.java              # 指定部门负责人策略
├── BpmTaskCandidateDeptMemberStrategy.java              # 部门成员策略
├── BpmTaskCandidateDeptLeaderMultiStrategy.java         # 连续多级部门负责人策略
├── BpmTaskCandidateStartUserDeptLeaderStrategy.java     # 发起人部门负责人策略
├── BpmTaskCandidateStartUserDeptLeaderMultiStrategy.java # 发起人多级部门负责人策略
├── BpmTaskCandidateStartUserSelectStrategy.java         # 发起人自选策略
└── BpmTaskCandidateApproveUserSelectStrategy.java       # 审批人自选策略
```

---

## 🏗️ 抽象基类

### AbstractBpmTaskCandidateDeptLeaderStrategy

**作用**：为所有部门负责人相关策略提供通用工具方法

**核心方法**：

#### 1. `getAssignLevelDeptLeaderId(dept, level)`

获取指定层级的部门负责人

```java
/**
 * @param dept  起始部门
 * @param level 层级数（1=当前部门，2=父部门，3=祖父部门...）
 * @return 负责人用户ID
 */
protected Long getAssignLevelDeptLeaderId(DeptRespDTO dept, Integer level)
```

**示例**：
```
组织架构：研发部 → 技术中心 → 公司
- getAssignLevelDeptLeaderId(研发部, 1) → 研发部负责人
- getAssignLevelDeptLeaderId(研发部, 2) → 技术中心负责人
- getAssignLevelDeptLeaderId(研发部, 3) → 公司负责人
```

#### 2. `getMultiLevelDeptLeaderIds(deptIds, level)`

获取连续多级部门负责人

```java
/**
 * @param deptIds 部门ID列表
 * @param level   向上查找的最大层级数
 * @return 所有符合条件的负责人用户ID集合
 */
protected Set<Long> getMultiLevelDeptLeaderIds(List<Long> deptIds, Integer level)
```

**示例**：
```
组织架构：研发部 → 技术中心 → 公司
- getMultiLevelDeptLeaderIds([研发部ID], 2)
  → {研发部负责人, 技术中心负责人}
```

#### 3. `getStartUserDept(startUserId)`

获取发起人所在部门

```java
/**
 * @param startUserId 流程发起人的用户ID
 * @return 发起人所属的部门信息
 */
protected DeptRespDTO getStartUserDept(Long startUserId)
```

---

## 📋 策略详解

### 1. BpmTaskCandidateDeptLeaderStrategy - 指定部门负责人

**策略类型**：`DEPT_LEADER` (枚举值: 21)

**功能描述**：根据指定的部门ID列表，查找这些部门的负责人作为候选人

**参数格式**：
```
"10,20,30"  // 逗号分隔的部门ID列表
```

**使用场景**：
- 固定让某些部门的负责人审批
- 跨部门协作审批

**配置示例**：
```xml
<!-- BPMN 流程定义 -->
<userTask id="task_approve" name="部门负责人审批">
  <extensionElements>
    <flowable:candidateStrategy>21</flowable:candidateStrategy>
    <flowable:candidateParam>10,20</flowable:candidateParam>
  </extensionElements>
</userTask>
```

**代码示例**：
```java
// 参数："10,20"（财务部、采购部）
Set<Long> candidates = strategy.calculateUsers("10,20");
// 返回：{财务部负责人ID, 采购部负责人ID}
```

**实际场景**：
```
场景：采购申请流程
需求：所有采购申请需要财务部和采购部的负责人共同审批
配置：DEPT_LEADER，参数："10,20"
结果：财务部负责人张三和采购部负责人李四都会收到审批任务
```

---

### 2. BpmTaskCandidateDeptMemberStrategy - 部门成员

**策略类型**：`DEPT_MEMBER` (枚举值: 20)

**功能描述**：根据指定的部门ID列表，查找这些部门的所有成员作为候选人

**参数格式**：
```
"1,2,3"  // 逗号分隔的部门ID列表
```

**使用场景**：
- 任务可以由部门内任何人处理
- 工单分配、客服响应等场景

**工作流程**：
```
部门ID列表 → 查询部门所有成员 → 返回成员用户ID集合
```

**代码示例**：
```java
// 参数："5"（客服部）
Set<Long> candidates = strategy.calculateUsers("5");
// 返回：{100, 101, 102, 103, 104} // 客服部的5个成员
```

**实际场景**：
```
场景：客服工单系统
需求：客服部的任何人都可以认领和处理工单
配置：DEPT_MEMBER，参数："5"
结果：客服部的10个成员都能看到这个工单，先到先得
```

**对比**：
| 维度 | DEPT_LEADER | DEPT_MEMBER |
|------|-------------|-------------|
| 候选人 | 只有负责人 | 部门所有成员 |
| 数量 | 少（通常1人） | 多（可能几十人） |
| 场景 | 需要领导决策 | 任何人可处理 |

---

### 3. BpmTaskCandidateStartUserDeptLeaderStrategy - 发起人部门负责人

**策略类型**：`START_USER_DEPT_LEADER` (枚举值: 37)

**功能描述**：根据流程发起人所在部门，向上查找第N级的部门负责人作为候选人

**参数格式**：
```
"1"  // 第1级（直属领导）
"2"  // 第2级（上级领导）
"3"  // 第3级（更上级领导）
```

**使用场景**：
- 最常见的审批场景：员工提交申请，直属领导审批
- 逐级审批：根据金额或重要性决定审批层级

**工作流程**：
```
获取发起人ID
  ↓
查询发起人所在部门
  ↓
向上查找第N级部门
  ↓
返回该部门的负责人ID
```

**代码示例**：
```java
// 发起人：张三（研发部）
// 组织架构：研发部 → 技术中心 → 公司

// 参数："1"
Set<Long> level1 = strategy.calculateUsers("1");
// 返回：{研发部负责人ID}

// 参数："2"
Set<Long> level2 = strategy.calculateUsers("2");
// 返回：{技术中心负责人ID}

// 参数："3"
Set<Long> level3 = strategy.calculateUsers("3");
// 返回：{公司负责人ID}
```

**实际场景**：

**场景1：员工请假**
```
发起人：张三（研发部）
配置：START_USER_DEPT_LEADER，参数："1"
结果：研发部经理李四审批
```

**场景2：大额报销**
```
发起人：张三（研发部 → 技术中心 → 公司）
金额：10万元
配置：START_USER_DEPT_LEADER，参数："3"
结果：直接提交给公司CEO审批
```

**场景3：根据金额动态分配**
```
报销金额 < 1000元    → level=1（部门经理）
报销金额 1000-5000元 → level=2（中心总监）
报销金额 > 5000元    → level=3（公司CEO）
```

---

### 4. BpmTaskCandidateStartUserDeptLeaderMultiStrategy - 发起人多级部门负责人

**策略类型**：`START_USER_DEPT_LEADER_MULTI` (枚举值: 38)

**功能描述**：从发起人所在部门开始，向上连续查找N级，返回所有级别的负责人作为候选人

**参数格式**：
```
"2"  // 连续2级（当前部门 + 上一级部门）
"3"  // 连续3级（当前部门 + 上一级 + 再上一级）
```

**使用场景**：
- 需要多级领导会签（所有人都要审批）
- 逐级审批流程

**工作流程**：
```
获取发起人所在部门
  ↓
向上查找连续N级部门
  ↓
收集每一级的负责人
  ↓
返回所有负责人ID集合
```

**代码示例**：
```java
// 发起人：张三（研发部）
// 组织架构：研发部 → 技术中心 → 公司

// 参数："2"（连续2级）
Set<Long> leaders = strategy.calculateUsers("2");
// 返回：{研发部负责人ID, 技术中心负责人ID}

// 参数："3"（连续3级）
Set<Long> leaders = strategy.calculateUsers("3");
// 返回：{研发部负责人ID, 技术中心负责人ID, 公司CEO的ID}
```

**实际场景**：
```
场景：大额采购申请（3万元）
发起人：张三（采购专员，所在：采购部 → 运营中心 → 公司）
配置：START_USER_DEPT_LEADER_MULTI，参数："2"
结果：采购部经理和运营中心总监都要审批（会签）

审批流程：
  1. 采购部经理李四审批（通过）
  2. 运营中心总监王五审批（通过）
  3. 两人都通过后，流程继续
```

**对比**：
| 维度 | START_USER_DEPT_LEADER | START_USER_DEPT_LEADER_MULTI |
|------|------------------------|------------------------------|
| 审批人数 | 1个（第N级） | 多个（连续N级） |
| 参数"2"含义 | 第2级负责人 | 1级+2级负责人 |
| 审批方式 | 单人审批 | 多人会签 |
| 适用场景 | 提交给某一级领导 | 需要多级领导都签字 |

---

### 5. BpmTaskCandidateDeptLeaderMultiStrategy - 连续多级部门负责人

**策略类型**：`MULTI_DEPT_LEADER_MULTI` (枚举值: 23)

**功能描述**：指定多个部门，分别向上查找连续N级，返回所有负责人

**参数格式**：
```
"10,20|3"
  ↑    ↑
  |    └─ 层级数：向上查找3级
  └────── 部门ID列表：10和20

格式：部门ID列表|层级数
分隔符：竖线 |
```

**参数解析**：
- 左边：逗号分隔的部门ID列表
- 右边：要向上查找的层级数
- 中间：用竖线 `|` 分隔

**使用场景**：
- 跨部门协作审批
- 需要多个部门的多级领导参与

**工作流程**：
```
解析参数 → 部门ID列表[10, 20] + 层级数3
  ↓
对每个部门分别处理：
  部门10 → 向上3级 → 收集负责人
  部门20 → 向上3级 → 收集负责人
  ↓
合并去重所有负责人
  ↓
返回负责人ID集合
```

**代码示例**：
```java
// 组织架构：
// 采购部(10) → 运营中心 → 公司
// 财务部(20) → 财务中心 → 公司

// 参数："10,20|2"
Set<Long> leaders = strategy.calculateUsers("10,20|2");
// 返回：{
//   采购部负责人ID, 运营中心负责人ID,
//   财务部负责人ID, 财务中心负责人ID
// }
```

**实际场景**：
```
场景：重大项目立项审批
需求：需要技术部和财务部的多级领导都参与决策
配置：MULTI_DEPT_LEADER_MULTI，参数："10,20|2"

组织架构：
  技术部(10) → 技术中心 → 公司
  财务部(20) → 财务中心 → 公司

审批流程：
  1. 技术部经理审批
  2. 技术中心总监审批
  3. 财务部经理审批
  4. 财务中心总监审批
  5. 所有人都通过后，流程继续
```

**参数校验**：
```java
// 正确格式
"10,20,30|3"  ✅  // 3个部门，向上3级
"5|1"         ✅  // 1个部门，向上1级

// 错误格式
"10,20"       ❌  // 缺少层级数
"10|0"        ❌  // 层级数必须大于0
"10|20|30"    ❌  // 只能有一个竖线
```

---

### 6. BpmTaskCandidateStartUserSelectStrategy - 发起人自选

**策略类型**：`START_USER_SELECT` (枚举值: 35)

**功能描述**：由流程发起人在启动流程时手动选择审批人

**参数格式**：
```
无需参数（审批人在流程启动时由发起人选择）
```

**使用场景**：
- 灵活的审批场景
- 发起人自主选择审批人
- 多个可选领导

**数据流转**：
```
前端界面
  ↓ (用户选择)
保存到流程变量
  ↓
{
  "startUserSelectAssignees": {
    "task_approve_1": [100, 200],  // 第1个审批节点：用户100和200
    "task_approve_2": [300]        // 第2个审批节点：用户300
  }
}
  ↓
流程执行时从变量中读取
  ↓
返回对应节点的审批人
```

**工作流程**：

**步骤1：流程启动前**
```javascript
// 前端代码
const formData = {
  title: "请假申请",
  days: 3,
  reason: "家里有事",
  // 发起人选择的审批人
  startUserSelectAssignees: {
    "task_manager_approve": [100],  // 选择了用户100作为直属领导
    "task_hr_approve": [200, 201]   // 选择了用户200和201作为人事
  }
};
```

**步骤2：流程执行时**
```java
// 流程执行到 task_manager_approve 节点
public Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
    // 从流程变量中获取发起人选择的审批人映射表
    Map<String, List<Long>> selectAssignees =
        FlowableUtils.getStartUserSelectAssignees(processInstance);

    // 当前节点ID
    String activityId = execution.getCurrentActivityId(); // "task_manager_approve"

    // 获取该节点的审批人
    List<Long> assignees = selectAssignees.get(activityId); // [100]

    return new LinkedHashSet<>(assignees);
}
```

**实际场景**：

**场景1：灵活请假**
```
流程：员工请假申请
前端界面：
  ┌─────────────────────────────┐
  │  请假申请                    │
  ├─────────────────────────────┤
  │ 请假天数：3天                │
  │ 请假原因：家里有事            │
  │                             │
  │ 选择审批领导：               │
  │  ○ 李四（直属领导）          │
  │  ○ 王五（部门经理）          │
  │  ● 赵六（代理领导）← 选中    │
  │                             │
  │ [提交申请]                   │
  └─────────────────────────────┘

结果：赵六收到审批任务
```

**场景2：多节点自选**
```
流程：报销申请
前端界面：
  ┌─────────────────────────────┐
  │  报销申请                    │
  ├─────────────────────────────┤
  │ 报销金额：5000元             │
  │                             │
  │ 第一级审批（直属领导）：      │
  │  ☑ 张三                     │
  │                             │
  │ 第二级审批（财务）：          │
  │  ☑ 李四                     │
  │  ☑ 王五                     │
  │                             │
  │ [提交申请]                   │
  └─────────────────────────────┘

流程变量：
{
  "startUserSelectAssignees": {
    "task_level1": [100],      // 张三
    "task_finance": [200, 300] // 李四和王五
  }
}
```

**代码实现细节**：
```java
@Override
public LinkedHashSet<Long> calculateUsersByTask(DelegateExecution execution, String param) {
    // 1. 获取流程实例
    ProcessInstance processInstance = processInstanceService
        .getProcessInstance(execution.getProcessInstanceId());

    Assert.notNull(processInstance, "流程实例({})不能为空", execution.getProcessInstanceId());

    // 2. 从流程变量中获取发起人选择的审批人
    Map<String, List<Long>> startUserSelectAssignees =
        FlowableUtils.getStartUserSelectAssignees(processInstance);

    Assert.notNull(startUserSelectAssignees,
        "流程实例({}) 的发起人自选审批人不能为空", execution.getProcessInstanceId());

    // 3. 根据当前节点ID获取审批人列表
    List<Long> assignees = startUserSelectAssignees.get(execution.getCurrentActivityId());

    // 4. 转换为LinkedHashSet返回（保证顺序且去重）
    return CollUtil.isNotEmpty(assignees) ?
        new LinkedHashSet<>(assignees) : Sets.newLinkedHashSet();
}
```

---

### 7. BpmTaskCandidateApproveUserSelectStrategy - 审批人自选

**策略类型**：`APPROVE_USER_SELECT` (枚举值: 34)

**功能描述**：由当前审批人在审批时选择下一个审批人（动态转交）

**参数格式**：
```
无需参数（下一审批人在审批时动态选择）
```

**使用场景**：
- 动态的审批链路
- 审批人转交给其他人
- 灵活的审批流转

**数据流转**：
```
审批界面
  ↓ (审批人选择下一审批人)
保存到流程变量
  ↓
{
  "approveUserSelectAssignees": {
    "task_next_approve": [400, 500]  // 下一节点的审批人
  }
}
  ↓
流程执行到下一节点时读取
  ↓
返回选择的审批人
```

**工作流程**：

**步骤1：当前节点审批**
```javascript
// 前端审批界面
const approveData = {
  approved: true,
  comment: "同意，转交给财务部处理",
  // 选择下一审批人
  approveUserSelectAssignees: {
    "task_finance_approve": [400, 500]  // 选择财务部的两个人
  }
};
```

**步骤2：流程流转到下一节点**
```java
// 流程执行到 task_finance_approve 节点
public Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
    // 从流程变量中获取上一审批人选择的下一审批人映射表
    Map<String, List<Long>> selectAssignees =
        FlowableUtils.getApproveUserSelectAssignees(processInstance);

    // 获取当前节点的审批人
    List<Long> assignees = selectAssignees.get(execution.getCurrentActivityId());

    return new LinkedHashSet<>(assignees);
}
```

**实际场景**：

**场景1：动态转交审批**
```
流程：报销审批
步骤1：张三提交5000元报销申请
步骤2：李四（部门经理）审批
        审批界面：
        ┌─────────────────────────────┐
        │  报销审批                    │
        ├─────────────────────────────┤
        │ 申请人：张三                 │
        │ 金额：5000元                 │
        │                             │
        │ ● 同意  ○ 拒绝              │
        │                             │
        │ 选择下一审批人：             │
        │  ○ 王五（财务经理）          │
        │  ● 赵六（财务总监）← 选中    │
        │                             │
        │ 审批意见：金额较大，转交总监  │
        │                             │
        │ [提交审批]                   │
        └─────────────────────────────┘

步骤3：赵六（财务总监）收到审批任务
        因为李四在审批时选择了赵六
```

**场景2：多人会签转交**
```
流程：合同审批
步骤1：张三提交合同审批
步骤2：李四（商务经理）审批通过
        选择下一审批人：
        ☑ 法务部-王五
        ☑ 法务部-赵六
        理由：合同条款需要法务团队共同审核

步骤3：王五和赵六都收到审批任务（会签）
        两人都审批通过后，流程继续
```

**场景3：审批链路不确定**
```
流程：复杂审批流程
特点：每个审批人根据实际情况决定下一步由谁处理

实例：
  张三提交申请
    ↓
  李四审批：觉得需要技术部意见 → 选择技术部王五
    ↓
  王五审批：觉得没问题 → 选择总经理赵六
    ↓
  赵六最终审批

说明：整个审批链路是动态生成的，不是预先固定的
```

**对比两种自选策略**：

| 维度 | START_USER_SELECT | APPROVE_USER_SELECT |
|------|-------------------|---------------------|
| **选择时机** | 流程启动时 | 审批过程中 |
| **选择人** | 流程发起人 | 当前审批人 |
| **选择次数** | 一次性选择所有节点 | 每个节点分别选择 |
| **适用场景** | 审批路径基本确定 | 审批路径动态变化 |
| **数据存储** | startUserSelectAssignees | approveUserSelectAssignees |
| **典型应用** | 请假选择领导 | 审批转交、会签 |

**完整流程对比**：

```
【发起人自选】
流程启动前：
  发起人选择：
    ├─ 第一级审批人：张三
    ├─ 第二级审批人：李四
    └─ 第三级审批人：王五

  → 路径固定，按顺序执行

【审批人自选】
流程执行中：
  张三审批 → 选择李四作为下一审批人
    ↓
  李四审批 → 选择王五和赵六作为下一审批人
    ↓
  王五和赵六会签

  → 路径动态，根据实际情况调整
```

**代码实现差异**：
```java
// 发起人自选：从 startUserSelectAssignees 中读取
Map<String, List<Long>> assignees =
    FlowableUtils.getStartUserSelectAssignees(processInstance);

// 审批人自选：从 approveUserSelectAssignees 中读取
Map<String, List<Long>> assignees =
    FlowableUtils.getApproveUserSelectAssignees(processInstance);
```

---

## 📊 策略对比速查表

### 按候选人来源分类

| 策略 | 枚举值 | 参数示例 | 审批人来源 | 人数 |
|------|--------|---------|-----------|------|
| DEPT_LEADER | 21 | "10,20" | 指定部门负责人 | 固定 |
| DEPT_MEMBER | 20 | "1,2" | 指定部门所有成员 | 多人 |
| START_USER_DEPT_LEADER | 37 | "1" | 发起人的第N级领导 | 1人 |
| START_USER_DEPT_LEADER_MULTI | 38 | "2" | 发起人的连续N级领导 | 多人 |
| MULTI_DEPT_LEADER_MULTI | 23 | "10,20\|3" | 指定部门的连续N级领导 | 多人 |
| START_USER_SELECT | 35 | 无 | 发起人自选 | 动态 |
| APPROVE_USER_SELECT | 34 | 无 | 当前审批人自选 | 动态 |

### 按应用场景分类

#### 🎯 固定规则类（预先配置）
- **DEPT_LEADER**：需要固定部门的领导审批
- **DEPT_MEMBER**：任务池模式，任何成员可处理
- **START_USER_DEPT_LEADER**：员工提交，直属领导审批

#### 🔄 动态计算类（基于发起人）
- **START_USER_DEPT_LEADER**：单级领导审批
- **START_USER_DEPT_LEADER_MULTI**：多级领导会签

#### 🎭 灵活选择类（人工选择）
- **START_USER_SELECT**：发起人选择审批人
- **APPROVE_USER_SELECT**：审批人转交给其他人

### 参数格式对比

| 策略 | 参数格式 | 参数说明 | 示例 |
|------|---------|---------|------|
| DEPT_LEADER | `"deptId1,deptId2"` | 逗号分隔的部门ID | `"10,20,30"` |
| DEPT_MEMBER | `"deptId1,deptId2"` | 逗号分隔的部门ID | `"5,6"` |
| START_USER_DEPT_LEADER | `"level"` | 单个层级数 | `"1"` 或 `"2"` |
| START_USER_DEPT_LEADER_MULTI | `"level"` | 连续层级数 | `"2"` 或 `"3"` |
| MULTI_DEPT_LEADER_MULTI | `"deptIds\|level"` | 部门列表\|层级数 | `"10,20\|3"` |
| START_USER_SELECT | 无 | 运行时从流程变量读取 | - |
| APPROVE_USER_SELECT | 无 | 运行时从流程变量读取 | - |

---

## 💡 实战示例

### 示例1：标准请假流程

**需求**：员工请假，直属领导审批

**方案**：使用 `START_USER_DEPT_LEADER`

```xml
<userTask id="task_manager_approve" name="领导审批">
  <extensionElements>
    <flowable:candidateStrategy>37</flowable:candidateStrategy>
    <flowable:candidateParam>1</flowable:candidateParam>
  </extensionElements>
</userTask>
```

**流程示例**：
```
员工张三（研发部）提交请假申请
  ↓
自动分配给研发部经理李四审批
  ↓
李四审批通过/拒绝
```

---

### 示例2：分级报销流程

**需求**：根据报销金额决定审批级别

**方案**：使用条件网关 + `START_USER_DEPT_LEADER` 不同层级

```xml
<exclusiveGateway id="gateway_amount" name="金额判断"/>

<!-- 小于1000元：部门经理审批 -->
<sequenceFlow sourceRef="gateway_amount" targetRef="task_manager">
  <conditionExpression>${amount < 1000}</conditionExpression>
</sequenceFlow>
<userTask id="task_manager" name="部门经理审批">
  <extensionElements>
    <flowable:candidateStrategy>37</flowable:candidateStrategy>
    <flowable:candidateParam>1</flowable:candidateParam>
  </extensionElements>
</userTask>

<!-- 1000-5000元：中心总监审批 -->
<sequenceFlow sourceRef="gateway_amount" targetRef="task_director">
  <conditionExpression>${amount >= 1000 &amp;&amp; amount < 5000}</conditionExpression>
</sequenceFlow>
<userTask id="task_director" name="中心总监审批">
  <extensionElements>
    <flowable:candidateStrategy>37</flowable:candidateStrategy>
    <flowable:candidateParam>2</flowable:candidateParam>
  </extensionElements>
</userTask>

<!-- 大于5000元：公司CEO审批 -->
<sequenceFlow sourceRef="gateway_amount" targetRef="task_ceo">
  <conditionExpression>${amount >= 5000}</conditionExpression>
</sequenceFlow>
<userTask id="task_ceo" name="CEO审批">
  <extensionElements>
    <flowable:candidateStrategy>37</flowable:candidateStrategy>
    <flowable:candidateParam>3</flowable:candidateParam>
  </extensionElements>
</userTask>
```

**流程示例**：
```
张三提交报销：800元 → 部门经理审批
张三提交报销：3000元 → 中心总监审批
张三提交报销：8000元 → CEO审批
```

---

### 示例3：多级会签流程

**需求**：大额采购需要多级领导都签字

**方案**：使用 `START_USER_DEPT_LEADER_MULTI` + 并行网关

```xml
<userTask id="task_multi_approve" name="多级领导会签">
  <extensionElements>
    <flowable:candidateStrategy>38</flowable:candidateStrategy>
    <flowable:candidateParam>3</flowable:candidateParam>
  </extensionElements>
  <multiInstanceLoopCharacteristics isSequential="false"
    flowable:collection="${candidateUsers}"
    flowable:elementVariable="assignee">
    <completionCondition>${nrOfCompletedInstances == nrOfInstances}</completionCondition>
  </multiInstanceLoopCharacteristics>
</userTask>
```

**流程示例**：
```
张三（采购部）提交10万元采购申请
  ↓
同时分配给：
  ├─ 采购部经理（第1级）
  ├─ 运营中心总监（第2级）
  └─ 公司CEO（第3级）
  ↓
三人都审批通过后，流程继续
```

---

### 示例4：跨部门协作流程

**需求**：项目立项需要技术部和财务部的多级领导审批

**方案**：使用 `MULTI_DEPT_LEADER_MULTI`

```xml
<userTask id="task_dept_approve" name="跨部门领导审批">
  <extensionElements>
    <flowable:candidateStrategy>23</flowable:candidateStrategy>
    <flowable:candidateParam>10,20|2</flowable:candidateParam>
  </extensionElements>
</userTask>
```

**参数说明**：
- `10`：技术部ID
- `20`：财务部ID
- `2`：向上2级

**流程示例**：
```
张三提交项目立项申请
  ↓
分配给以下审批人（会签）：
  ├─ 技术部经理
  ├─ 技术中心总监
  ├─ 财务部经理
  └─ 财务中心总监
  ↓
所有人都审批通过后，项目立项成功
```

---

### 示例5：灵活审批流程

**需求**：员工可以自己选择由哪个领导审批

**方案**：使用 `START_USER_SELECT`

```xml
<userTask id="task_selected_approve" name="自选领导审批">
  <extensionElements>
    <flowable:candidateStrategy>35</flowable:candidateStrategy>
  </extensionElements>
</userTask>
```

**前端代码**：
```javascript
// 提交流程时，传入选择的审批人
const formData = {
  title: "请假申请",
  days: 3,
  startUserSelectAssignees: {
    "task_selected_approve": [100, 200]  // 选择了用户100和200
  }
};

await startProcess(formData);
```

**流程示例**：
```
前端界面：
  ┌─────────────────────────┐
  │ 请假申请                │
  │ 天数：3天               │
  │                         │
  │ 选择审批领导：          │
  │ ☑ 李四（直属领导）      │
  │ ☐ 王五（部门经理）      │
  │ ☑ 赵六（代理领导）      │
  │                         │
  │ [提交]                  │
  └─────────────────────────┘
  ↓
李四和赵六收到审批任务（任一人审批即可）
```

---

### 示例6：动态转交流程

**需求**：审批人根据情况决定下一个审批人

**方案**：使用 `APPROVE_USER_SELECT`

```xml
<userTask id="task_current_approve" name="当前审批">
  <extensionElements>
    <flowable:candidateStrategy>10</flowable:candidateStrategy>
    <flowable:candidateParam>1</flowable:candidateParam>
  </extensionElements>
</userTask>

<userTask id="task_next_approve" name="下一审批（动态选择）">
  <extensionElements>
    <flowable:candidateStrategy>34</flowable:candidateStrategy>
  </extensionElements>
</userTask>
```

**审批界面代码**：
```javascript
// 当前审批人审批时，选择下一审批人
const approveData = {
  approved: true,
  comment: "同意，转交财务部处理",
  approveUserSelectAssignees: {
    "task_next_approve": [400, 500]  // 选择财务部的两个人
  }
};

await completeTask(taskId, approveData);
```

**流程示例**：
```
张三提交报销申请
  ↓
李四（部门经理）审批
  审批界面：
    ☑ 同意  ☐ 拒绝
    下一审批人：☑ 财务部王五 ☑ 财务部赵六
  ↓
王五和赵六收到审批任务
```

---

## 🎓 学习路径

### 第一阶段：理解基础概念

1. **组织架构层级**
   ```
   公司
     └─ 事业部
          └─ 中心
               └─ 部门
                    └─ 小组
   ```

2. **层级参数理解**
   - level = 1：当前层级
   - level = 2：向上1级
   - level = 3：向上2级

3. **单级 vs 多级**
   - 单级：只返回第N级的负责人（1人）
   - 多级：返回连续N级的所有负责人（多人）

---

### 第二阶段：掌握基础策略

**建议学习顺序**：

1. **DEPT_MEMBER**（最简单）
   - 理解：查询部门所有成员
   - 练习：配置客服工单分配

2. **DEPT_LEADER**（固定部门）
   - 理解：查询指定部门负责人
   - 练习：配置跨部门审批

3. **START_USER_DEPT_LEADER**（最常用）
   - 理解：基于发起人的层级查找
   - 练习：配置请假审批流程

---

### 第三阶段：学习高级策略

4. **START_USER_DEPT_LEADER_MULTI**
   - 理解：连续多级查找
   - 区别：与单级的对比
   - 练习：配置多级会签流程

5. **MULTI_DEPT_LEADER_MULTI**
   - 理解：跨部门多级查找
   - 参数：部门列表|层级数
   - 练习：配置项目立项流程

---

### 第四阶段：掌握动态策略

6. **START_USER_SELECT**
   - 理解：发起人自选机制
   - 数据：流程变量存储
   - 练习：实现灵活审批

7. **APPROVE_USER_SELECT**
   - 理解：审批人转交机制
   - 区别：与发起人自选的对比
   - 练习：实现动态审批链路

---

### 学习建议

#### 🧪 实践练习

**练习1：层级理解**
```
画出你公司的组织架构图
标注每个部门的负责人
模拟不同层级参数的查找结果
```

**练习2：场景匹配**
```
列出公司的5个审批流程
分析每个流程适合用哪个策略
给出配置方案
```

**练习3：参数配置**
```
尝试配置以下场景：
1. 请假流程：直属领导审批
2. 报销流程：根据金额分级审批
3. 采购流程：多级领导会签
4. 项目立项：跨部门协作审批
```

#### 📝 知识巩固

**关键问题自测**：

1. level=1 和 level=2 有什么区别？
2. 单级查找和多级查找的应用场景有何不同？
3. 发起人自选和审批人自选的数据存储在哪里？
4. 参数 "10,20|3" 表示什么含义？
5. 什么时候用 DEPT_LEADER，什么时候用 START_USER_DEPT_LEADER？

**实践题**：

设计以下场景的配置方案：
1. 员工年假申请（3天以内直属领导批，超过3天需要部门总监批）
2. 差旅费报销（国内差旅部门经理批，国际差旅需要总经理批）
3. 重大决策审批（需要运营、技术、财务三个部门的总监都签字）
4. 灵活加班申请（员工可以选择让哪个领导审批）

---

## 🔧 开发指南

### 扩展新策略

如果需要开发新的部门策略，建议步骤：

**步骤1：创建策略类**
```java
@Component
public class BpmTaskCandidateCustomDeptStrategy extends AbstractBpmTaskCandidateDeptLeaderStrategy {

    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.CUSTOM_DEPT;
    }

    @Override
    public void validateParam(String param) {
        // 参数校验逻辑
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        // 候选人计算逻辑
        return candidateUserIds;
    }
}
```

**步骤2：注册策略枚举**
```java
// 在 BpmTaskCandidateStrategyEnum 中添加
CUSTOM_DEPT(50, "自定义部门策略"),
```

**步骤3：编写单元测试**
```java
@Test
public void testCustomDeptStrategy() {
    BpmTaskCandidateCustomDeptStrategy strategy = new BpmTaskCandidateCustomDeptStrategy();
    Set<Long> users = strategy.calculateUsers("test-param");
    assertNotNull(users);
}
```

---

### 常见问题

#### Q1：发起人没有部门怎么办？
```java
DeptRespDTO dept = super.getStartUserDept(startUserId);
if (dept == null) {
    return new HashSet<>();  // 返回空集合
}
```

#### Q2：向上查找超过了组织架构最顶层？
```java
// 父类方法会自动处理，查找到根节点时停止
Long leaderId = super.getAssignLevelDeptLeaderId(dept, level);
// 如果到达根节点，返回根节点的负责人
```

#### Q3：部门负责人字段为空？
```java
// convertSet 会自动过滤掉 null 值
return convertSet(depts, DeptRespDTO::getLeaderUserId);
```

#### Q4：如何支持"跳过当前层级"？
```java
// 可以在参数中传入起始层级
// 例如："2,3" 表示从第2级开始查找第3级
String[] params = param.split(",");
int startLevel = Integer.parseInt(params[0]);
int targetLevel = Integer.parseInt(params[1]);
```

---

## 📚 相关文档

- [Flowable 官方文档](https://www.flowable.com/open-source/docs)
- [BPMN 2.0 规范](https://www.omg.org/spec/BPMN/2.0/)
- [芋道源码 - BPM 模块文档](https://doc.iocoder.cn/bpm/)

---

## 🤝 贡献指南

如果您发现文档有误或需要补充，欢迎提交 PR 或 Issue。

---

## 📄 许可证

本项目遵循 MIT 许可证。
