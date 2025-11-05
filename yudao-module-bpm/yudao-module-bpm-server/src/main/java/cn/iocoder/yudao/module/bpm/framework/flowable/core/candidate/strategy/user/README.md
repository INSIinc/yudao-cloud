# 用户候选人策略

## 📖 概述

本目录包含基于用户身份、角色、岗位等维度的工作流任务候选人分配策略实现。这些策略用于在 Flowable 工作流引擎中，根据不同的业务规则动态计算审批任务的候选人。

### 核心特性

- **直接指定**：支持直接指定用户 ID 作为候选人
- **角色分配**：支持基于 RBAC 角色的候选人分配
- **岗位分配**：支持基于岗位的候选人分配
- **用户组**：支持基于自定义用户组的候选人分配
- **发起人**：支持将流程发起人本人作为候选人

---

## 📁 目录结构

```
user/
├── BpmTaskCandidateUserStrategy.java        # 指定用户策略
├── BpmTaskCandidateRoleStrategy.java        # 角色策略
├── BpmTaskCandidatePostStrategy.java        # 岗位策略
├── BpmTaskCandidateGroupStrategy.java       # 用户组策略
└── BpmTaskCandidateStartUserStrategy.java   # 发起人自己策略
```

---

## 📋 策略详解

### 1. BpmTaskCandidateUserStrategy - 指定用户

**策略类型**：`USER` (枚举值: 30)

**功能描述**：直接指定具体的用户 ID 列表作为任务的候选人

**参数格式**：
```
"1,2,3"  // 逗号分隔的用户 ID 列表
```

**使用场景**：
- 需要固定某些特定用户审批
- 特殊流程需要指定审批人
- 临时指定的审批场景

**工作流程**：
```
用户ID字符串 → 解析为用户ID集合 → 返回用户ID集合
```

**代码示例**：
```java
// 参数：\"1,2,3\"（张三、李四、王五的用户ID）
Set<Long> candidates = strategy.calculateUsers("1,2,3");
// 返回：{1L, 2L, 3L}
```

**实际场景**：
```
场景：重要合同审批
需求：必须由公司法务总监（用户ID: 100）和财务总监（用户ID: 101）共同审批
配置：USER，参数："100,101"
结果：法务总监和财务总监都会收到审批任务
```

**BPMN 配置示例**：
```xml
<userTask id="task_approve" name="指定用户审批">
  <extensionElements>
    <flowable:candidateStrategy>30</flowable:candidateStrategy>
    <flowable:candidateParam>100,101</flowable:candidateParam>
  </extensionElements>
</userTask>
```

**优缺点分析**：
| 优点 | 缺点 |
|------|------|
| 配置简单直接 | 灵活性差，人员变动需修改流程 |
| 精确控制审批人 | 无法动态计算 |
| 适合特殊流程 | 不适合大规模使用 |

---

### 2. BpmTaskCandidateRoleStrategy - 角色

**策略类型**：`ROLE` (枚举值: 10)

**功能描述**：根据系统角色（RBAC）查找拥有指定角色的所有用户作为候选人

**参数格式**：
```
"1,2,3"  // 逗号分隔的角色 ID 列表
```

**使用场景**：
- 基于角色的权限控制（RBAC）
- 需要某个角色的所有成员都可以处理的任务
- 角色驱动的审批流程

**工作流程**：
```
角色ID列表 → 查询角色下的所有用户 → 返回用户ID集合
```

**代码示例**：
```java
// 参数：\"1,2\"（财务经理和财务总监角色）
Set<Long> candidates = strategy.calculateUsers("1,2");
// 假设财务经理角色下有用户100、101
// 财务总监角色下有用户102
// 返回：{100L, 101L, 102L}
```

**实际场景**：
```
场景：财务报销审批
需求：所有拥有"财务审批"角色的用户都可以审批报销单
配置：ROLE，参数："5"（财务审批角色ID）
结果：财务部的张三、李四、王五都能看到报销审批任务
```

**BPMN 配置示例**：
```xml
<userTask id="task_finance_approve" name="财务审批">
  <extensionElements>
    <flowable:candidateStrategy>10</flowable:candidateStrategy>
    <flowable:candidateParam>5</flowable:candidateParam>
  </extensionElements>
</userTask>
```

**与其他策略对比**：
| 策略 | 基于维度 | 人员数量 | 灵活性 | 维护成本 |
|------|---------|---------|--------|---------|
| USER | 用户ID | 固定 | 低 | 高（人员变动需改流程） |
| ROLE | 角色 | 动态 | 高 | 低（调整角色成员即可） |
| POST | 岗位 | 动态 | 高 | 低（调整岗位成员即可） |

**最佳实践**：
1. **权限对齐**：确保角色权限与审批权限对齐
2. **角色粒度**：避免角色过于宽泛导致候选人过多
3. **定期审查**：定期审查角色成员，确保审批人准确

---

### 3. BpmTaskCandidatePostStrategy - 岗位

**策略类型**：`POST` (枚举值: 12)

**功能描述**：根据岗位（职位）查找担任指定岗位的所有用户作为候选人

**参数格式**：
```
"1,2,3"  // 逗号分隔的岗位 ID 列表
```

**使用场景**：
- 基于组织架构的审批流程
- 按岗位职责分配任务
- 岗位驱动的业务流程

**工作流程**：
```
岗位ID列表 → 查询岗位关联的所有用户 → 返回用户ID集合
```

**代码示例**：
```java
// 参数：\"10,20\"（财务经理岗位和财务主管岗位）
Set<Long> candidates = strategy.calculateUsers("10,20");
// 假设财务经理岗位有用户100
// 财务主管岗位有用户101、102
// 返回：{100L, 101L, 102L}
```

**实际场景**：

**场景1：采购审批流程**
```
场景：采购申请需要采购经理审批
需求：所有担任"采购经理"岗位的人都可以审批
配置：POST，参数："15"（采购经理岗位ID）
结果：张三（采购经理）和李四（代理采购经理）都能看到审批任务
```

**场景2：技术评审流程**
```
场景：技术方案需要技术专家评审
需求：技术总监和高级架构师都可以评审
配置：POST，参数："20,21"（技术总监、高级架构师岗位ID）
结果：技术总监王五和两位高级架构师赵六、孙七都能参与评审
```

**BPMN 配置示例**：
```xml
<userTask id="task_purchase_approve" name="采购经理审批">
  <extensionElements>
    <flowable:candidateStrategy>12</flowable:candidateStrategy>
    <flowable:candidateParam>15</flowable:candidateParam>
  </extensionElements>
</userTask>
```

**岗位 vs 角色对比**：
| 维度 | 岗位（POST） | 角色（ROLE） |
|------|------------|-------------|
| **定义** | 组织架构中的职位（如：财务经理） | 系统权限角色（如：管理员） |
| **数量** | 一个人可以担任多个岗位 | 一个人可以有多个角色 |
| **用途** | 业务流程、职责分工 | 权限控制、功能访问 |
| **示例** | 部门经理、项目经理、技术专家 | 系统管理员、审计员、操作员 |
| **变更频率** | 较低（与组织架构同步） | 较低（与权限设计同步） |

**最佳实践**：
1. **岗位命名**：使用清晰的岗位名称，如"财务部-财务经理"
2. **岗位层级**：配合部门层级使用，实现更精细的控制
3. **兼职处理**：一人多岗时，注意去重和优先级处理

---

### 4. BpmTaskCandidateGroupStrategy - 用户组

**策略类型**：`USER_GROUP` (枚举值: 11)

**功能描述**：根据自定义用户组查找组内的所有用户作为候选人

**参数格式**：
```
"1,2,3"  // 逗号分隔的用户组 ID 列表
```

**使用场景**：
- 自定义的业务团队（如：项目组、专项小组）
- 临时性的审批组
- 跨部门协作团队
- 灵活的人员组合

**工作流程**：
```
用户组ID列表 → 查询用户组详情 → 提取所有用户ID → 合并去重 → 返回用户ID集合
```

**用户组数据结构**：
```java
// BpmUserGroupDO 用户组实体
{
  "id": 1,
  "name": "财务审批组",
  "description": "负责所有财务审批事项",
  "userIds": [100, 101, 102],  // 组内用户ID列表
  "status": 1
}
```

**代码示例**：
```java
// 参数：\"1,2\"（财务审批组和风控审批组）
Set<Long> candidates = strategy.calculateUsers("1,2");
// 假设：
// 用户组1（财务审批组）包含用户：100, 101, 102
// 用户组2（风控审批组）包含用户：102, 103, 104
// 返回：{100L, 101L, 102L, 103L, 104L}（自动去重）
```

**实际场景**：

**场景1：项目评审**
```
场景：新项目立项需要项目评审委员会审批
需求：评审委员会成员动态变化，不适合用固定用户或角色
配置：USER_GROUP，参数："5"（项目评审委员会用户组ID）
结果：评审委员会的所有成员都能看到评审任务

用户组管理界面：
  项目评审委员会（ID: 5）
  ├─ 张三（技术总监）
  ├─ 李四（产品总监）
  ├─ 王五（财务总监）
  └─ 赵六（运营总监）
```

**场景2：应急响应团队**
```
场景：系统故障需要应急响应团队处理
需求：应急团队成员包括多个部门的人员，随时可能调整
配置：USER_GROUP，参数："10"（应急响应团队用户组ID）
结果：所有应急团队成员都能接收故障处理任务

用户组示例：
  应急响应团队（ID: 10）
  ├─ 运维团队：孙七、周八
  ├─ 开发团队：吴九、郑十
  └─ DBA团队：王十一
```

**场景3：跨部门协作**
```
场景：跨部门项目需要多个部门联合审批
需求：包括技术部、产品部、市场部的指定人员
配置：USER_GROUP，参数："15"（X项目核心组用户组ID）
结果：项目核心组的所有成员都参与审批
```

**BPMN 配置示例**：
```xml
<userTask id="task_committee_review" name="评审委员会审批">
  <extensionElements>
    <flowable:candidateStrategy>11</flowable:candidateStrategy>
    <flowable:candidateParam>5</flowable:candidateParam>
  </extensionElements>
</userTask>
```

**管理界面示例**（前端代码）：
```javascript
// 创建用户组
const userGroup = {
  name: "项目评审委员会",
  description: "负责公司所有新项目的立项评审",
  userIds: [100, 101, 102, 103],  // 选择的用户ID列表
  status: 1
};

// 修改用户组成员
const updateUserGroup = {
  id: 5,
  userIds: [100, 101, 102, 103, 104]  // 增加了新成员104
};
```

**用户组 vs 其他策略对比**：
| 策略 | 人员来源 | 灵活性 | 维护方式 | 适用场景 |
|------|---------|--------|---------|---------|
| USER | 固定用户ID | 低 | 修改流程配置 | 特定人员审批 |
| ROLE | 角色成员 | 中 | 修改角色成员 | 权限驱动审批 |
| POST | 岗位成员 | 中 | 修改岗位成员 | 组织架构驱动 |
| USER_GROUP | 用户组成员 | 高 | 修改用户组成员 | 灵活团队协作 |

**最佳实践**：
1. **命名规范**：用户组名称应清晰表明用途，如"XX项目组"、"XX评审委员会"
2. **定期维护**：定期检查用户组成员，及时调整人员
3. **权限控制**：控制用户组的创建和修改权限，避免滥用
4. **文档记录**：记录用户组的用途和变更历史
5. **避免冗余**：不要创建过多功能重复的用户组

**创建用户组的时机**：
- ✅ 临时性项目团队
- ✅ 跨部门协作组
- ✅ 特殊业务小组
- ✅ 经常变动的审批团队
- ❌ 固定的组织架构（应该用岗位或部门）
- ❌ 权限相关分组（应该用角色）

---

### 5. BpmTaskCandidateStartUserStrategy - 发起人自己

**策略类型**：`START_USER` (枚举值: 60)

**功能描述**：将流程发起人本人作为任务的候选人

**参数格式**：
```
无需参数（自动获取流程发起人）
```

**使用场景**：
- 发起人信息复核
- 发起人补充材料
- 发起人确认结果
- 闭环通知流程

**工作流程**：
```
从流程实例中获取发起人ID → 返回发起人ID作为候选人
```

**代码示例**：
```java
// 运行时计算（从流程执行上下文）
@Override
public Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
    // 获取流程实例
    ProcessInstance processInstance = processInstanceService.getProcessInstance(
        execution.getProcessInstanceId()
    );
    // 获取发起人ID并返回
    return SetUtils.asSet(Long.valueOf(processInstance.getStartUserId()));
}

// 设计时计算（从参数传入）
@Override
public Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId, String param,
                                          Long startUserId, String processDefinitionId,
                                          Map<String, Object> processVariables) {
    // 直接返回发起人ID
    return SetUtils.asSet(startUserId);
}
```

**实际场景**：

**场景1：请假流程 - 信息复核**
```
流程：员工请假申请
步骤1：员工提交请假申请（发起人：张三）
        ↓
步骤2：部门经理审批（候选人：李四）
        ↓
步骤3：人事审批（候选人：王五）
        ↓
步骤4：发起人复核结果（候选人：张三）← 使用 START_USER 策略
        ├─ 确认审批结果
        ├─ 查看审批意见
        └─ 完成流程闭环

配置：START_USER，无需参数
结果：张三看到"请假审批结果确认"任务
```

**场景2：报销流程 - 补充材料**
```
流程：费用报销申请
步骤1：员工提交报销申请（发起人：张三）
        ↓
步骤2：财务审批（候选人：李四）
        ├─ 审批通过 → 流程继续
        └─ 需要补充材料 → 退回发起人
                          ↓
步骤3：发起人补充材料（候选人：张三）← 使用 START_USER 策略
        ├─ 上传发票
        ├─ 填写说明
        └─ 重新提交
                          ↓
        返回步骤2（财务重新审批）

配置：START_USER，无需参数
结果：张三收到"补充报销材料"任务
```

**场景3：采购流程 - 确认收货**
```
流程：采购申请流程
步骤1：员工提交采购申请（发起人：张三）
        ↓
步骤2：部门经理审批
        ↓
步骤3：采购部下单
        ↓
步骤4：供应商发货
        ↓
步骤5：发起人确认收货（候选人：张三）← 使用 START_USER 策略
        ├─ 检查货物
        ├─ 确认数量和质量
        └─ 完成收货确认

配置：START_USER，无需参数
结果：张三收到"采购收货确认"任务
```

**场景4：合同审批 - 盖章取回**
```
流程：合同审批流程
步骤1：业务员提交合同审批（发起人：张三）
        ↓
步骤2：部门经理审批
        ↓
步骤3：法务审批
        ↓
步骤4：总经理审批
        ↓
步骤5：行政盖章
        ↓
步骤6：发起人取回合同（候选人：张三）← 使用 START_USER 策略
        └─ 确认已取回合同

配置：START_USER，无需参数
结果：张三收到"合同取回确认"任务
```

**BPMN 配置示例**：
```xml
<!-- 完整的请假流程示例 -->
<process id="leave_process" name="请假流程">
  <!-- 开始节点 -->
  <startEvent id="start"/>

  <!-- 发起人填写请假申请 -->
  <userTask id="task_submit" name="提交请假申请">
    <extensionElements>
      <flowable:candidateStrategy>60</flowable:candidateStrategy>
    </extensionElements>
  </userTask>

  <!-- 部门经理审批 -->
  <userTask id="task_manager_approve" name="部门经理审批">
    <extensionElements>
      <flowable:candidateStrategy>37</flowable:candidateStrategy>
      <flowable:candidateParam>1</flowable:candidateParam>
    </extensionElements>
  </userTask>

  <!-- 人事审批 -->
  <userTask id="task_hr_approve" name="人事审批">
    <extensionElements>
      <flowable:candidateStrategy>10</flowable:candidateStrategy>
      <flowable:candidateParam>5</flowable:candidateParam>
    </extensionElements>
  </userTask>

  <!-- 发起人确认结果 -->
  <userTask id="task_confirm_result" name="确认审批结果">
    <extensionElements>
      <flowable:candidateStrategy>60</flowable:candidateStrategy>
    </extensionElements>
  </userTask>

  <!-- 结束节点 -->
  <endEvent id="end"/>

  <!-- 连线 -->
  <sequenceFlow sourceRef="start" targetRef="task_submit"/>
  <sequenceFlow sourceRef="task_submit" targetRef="task_manager_approve"/>
  <sequenceFlow sourceRef="task_manager_approve" targetRef="task_hr_approve"/>
  <sequenceFlow sourceRef="task_hr_approve" targetRef="task_confirm_result"/>
  <sequenceFlow sourceRef="task_confirm_result" targetRef="end"/>
</process>
```

**前端界面示例**：
```javascript
// 任务列表展示
const taskList = [
  {
    taskId: "task_001",
    taskName: "确认审批结果",
    processName: "请假流程",
    startUser: "张三",
    createTime: "2024-01-15 14:30:00",
    message: "您的请假申请已审批通过，请确认"
  }
];

// 发起人确认界面
function confirmResult(taskId) {
  const formData = {
    taskId: taskId,
    confirmed: true,
    comment: "已知晓审批结果，感谢"
  };
  // 提交确认
  completeTask(formData);
}
```

**与其他策略组合使用**：
```xml
<!-- 示例：审批驳回后返回发起人修改 -->
<process id="approval_process" name="审批流程">
  <!-- 发起人提交 -->
  <userTask id="task_submit" name="提交申请">
    <extensionElements>
      <flowable:candidateStrategy>60</flowable:candidateStrategy>
    </extensionElements>
  </userTask>

  <!-- 领导审批 -->
  <userTask id="task_approve" name="领导审批">
    <extensionElements>
      <flowable:candidateStrategy>37</flowable:candidateStrategy>
      <flowable:candidateParam>1</flowable:candidateParam>
    </extensionElements>
  </userTask>

  <!-- 网关：判断审批结果 -->
  <exclusiveGateway id="gateway_approved"/>

  <!-- 如果驳回，返回发起人修改 -->
  <sequenceFlow sourceRef="gateway_approved" targetRef="task_modify">
    <conditionExpression>${approved == false}</conditionExpression>
  </sequenceFlow>

  <userTask id="task_modify" name="修改申请">
    <extensionElements>
      <flowable:candidateStrategy>60</flowable:candidateStrategy>
    </extensionElements>
  </userTask>

  <!-- 修改后重新提交审批 -->
  <sequenceFlow sourceRef="task_modify" targetRef="task_approve"/>
</process>
```

**策略特点**：
| 特点 | 说明 |
|------|------|
| **无需配置参数** | 自动获取发起人，无需手动配置 |
| **动态适应** | 不同流程实例的发起人不同，自动适配 |
| **闭环管理** | 帮助实现流程闭环，发起人知晓结果 |
| **简化配置** | 不需要复杂的候选人计算逻辑 |

**最佳实践**：
1. **结果通知**：审批完成后让发起人确认结果
2. **材料补充**：审批驳回时让发起人补充材料
3. **信息确认**：关键信息变更时让发起人再次确认
4. **收货验收**：采购等流程让发起人确认收货
5. **避免误用**：不要在初始提交环节使用（发起人会看到自己提交的任务）

**注意事项**：
- ✅ 适合用在流程的**中间或结尾**环节
- ✅ 适合用在**需要发起人参与**的环节
- ❌ 不要用在流程的**开始节点**（发起人已经是流程的启动者）
- ❌ 避免创建**无意义的确认**环节（增加用户操作负担）

---

## 📊 策略对比速查表

### 按候选人来源分类

| 策略 | 枚举值 | 参数示例 | 候选人来源 | 人数 | 是否需要参数 |
|------|--------|---------|-----------|------|------------|
| USER | 30 | "1,2,3" | 指定的用户ID | 固定 | 是 |
| ROLE | 10 | "1,2" | 拥有指定角色的用户 | 动态 | 是 |
| POST | 12 | "10,20" | 担任指定岗位的用户 | 动态 | 是 |
| USER_GROUP | 11 | "5,6" | 指定用户组的成员 | 动态 | 是 |
| START_USER | 60 | 无 | 流程发起人 | 1人 | 否 |

### 按应用场景分类

#### 🎯 固定规则类（预先配置）
- **USER**：需要固定的特定人员审批
- **ROLE**：基于角色的权限控制审批
- **POST**：基于岗位职责的审批

#### 🔄 动态计算类（自动获取）
- **START_USER**：流程发起人参与的环节

#### 🎭 灵活组合类（自定义分组）
- **USER_GROUP**：自定义团队或协作组

### 参数格式对比

| 策略 | 参数格式 | 参数说明 | 示例 |
|------|---------|---------|------|
| USER | `"userId1,userId2"` | 逗号分隔的用户ID | `"1,2,3"` |
| ROLE | `"roleId1,roleId2"` | 逗号分隔的角色ID | `"10,20"` |
| POST | `"postId1,postId2"` | 逗号分隔的岗位ID | `"5,6,7"` |
| USER_GROUP | `"groupId1,groupId2"` | 逗号分隔的用户组ID | `"1,2"` |
| START_USER | 无 | 无需参数，自动获取 | - |

### 维护成本对比

| 策略 | 维护方式 | 维护成本 | 变更影响 |
|------|---------|---------|---------|
| USER | 修改流程定义 | 高 | 需要重新部署流程 |
| ROLE | 修改角色成员 | 低 | 无需修改流程 |
| POST | 修改岗位成员 | 低 | 无需修改流程 |
| USER_GROUP | 修改用户组成员 | 低 | 无需修改流程 |
| START_USER | 无需维护 | 无 | 自动适配 |

---

## 💡 实战示例

### 示例1：员工请假流程

**需求**：员工请假，直属领导审批，人事备案，员工确认

**方案**：组合使用多种策略

```xml
<process id="leave_process" name="请假流程">
  <startEvent id="start"/>

  <!-- 步骤1：员工填写请假申请 -->
  <userTask id="task_submit" name="填写请假申请">
    <extensionElements>
      <flowable:candidateStrategy>60</flowable:candidateStrategy>
    </extensionElements>
  </userTask>

  <!-- 步骤2：直属领导审批（发起人的部门负责人） -->
  <userTask id="task_leader_approve" name="直属领导审批">
    <extensionElements>
      <flowable:candidateStrategy>37</flowable:candidateStrategy>
      <flowable:candidateParam>1</flowable:candidateParam>
    </extensionElements>
  </userTask>

  <!-- 步骤3：人事部门备案（人事专员角色） -->
  <userTask id="task_hr_record" name="人事备案">
    <extensionElements>
      <flowable:candidateStrategy>10</flowable:candidateStrategy>
      <flowable:candidateParam>5</flowable:candidateParam>
    </extensionElements>
  </userTask>

  <!-- 步骤4：员工确认审批结果 -->
  <userTask id="task_confirm" name="确认审批结果">
    <extensionElements>
      <flowable:candidateStrategy>60</flowable:candidateStrategy>
    </extensionElements>
  </userTask>

  <endEvent id="end"/>

  <sequenceFlow sourceRef="start" targetRef="task_submit"/>
  <sequenceFlow sourceRef="task_submit" targetRef="task_leader_approve"/>
  <sequenceFlow sourceRef="task_leader_approve" targetRef="task_hr_record"/>
  <sequenceFlow sourceRef="task_hr_record" targetRef="task_confirm"/>
  <sequenceFlow sourceRef="task_confirm" targetRef="end"/>
</process>
```

**流程示例**：
```
张三（研发部员工）提交3天年假申请
  ↓
李四（研发部经理）审批通过
  ↓
王五（人事专员）备案登记
  ↓
张三确认审批结果，流程结束
```

---

### 示例2：采购审批流程

**需求**：根据采购金额分配不同的审批人

**方案**：使用条件网关 + 多种策略

```xml
<process id="purchase_process" name="采购审批流程">
  <startEvent id="start"/>

  <!-- 步骤1：员工提交采购申请 -->
  <userTask id="task_submit" name="提交采购申请"/>

  <!-- 金额判断网关 -->
  <exclusiveGateway id="gateway_amount" name="金额判断"/>

  <!-- 小于1万：部门经理审批 -->
  <sequenceFlow sourceRef="gateway_amount" targetRef="task_manager_approve">
    <conditionExpression>${amount < 10000}</conditionExpression>
  </sequenceFlow>
  <userTask id="task_manager_approve" name="部门经理审批">
    <extensionElements>
      <flowable:candidateStrategy>37</flowable:candidateStrategy>
      <flowable:candidateParam>1</flowable:candidateParam>
    </extensionElements>
  </userTask>

  <!-- 1万-5万：采购经理审批 -->
  <sequenceFlow sourceRef="gateway_amount" targetRef="task_purchase_approve">
    <conditionExpression>${amount >= 10000 &amp;&amp; amount < 50000}</conditionExpression>
  </sequenceFlow>
  <userTask id="task_purchase_approve" name="采购经理审批">
    <extensionElements>
      <flowable:candidateStrategy>12</flowable:candidateStrategy>
      <flowable:candidateParam>15</flowable:candidateParam>
    </extensionElements>
  </userTask>

  <!-- 大于5万：采购总监审批 -->
  <sequenceFlow sourceRef="gateway_amount" targetRef="task_director_approve">
    <conditionExpression>${amount >= 50000}</conditionExpression>
  </sequenceFlow>
  <userTask id="task_director_approve" name="采购总监审批">
    <extensionElements>
      <flowable:candidateStrategy>30</flowable:candidateStrategy>
      <flowable:candidateParam>100</flowable:candidateParam>
    </extensionElements>
  </userTask>

  <!-- 汇聚网关 -->
  <exclusiveGateway id="gateway_merge"/>

  <!-- 采购部门下单 -->
  <userTask id="task_order" name="采购部门下单">
    <extensionElements>
      <flowable:candidateStrategy>10</flowable:candidateStrategy>
      <flowable:candidateParam>8</flowable:candidateParam>
    </extensionElements>
  </userTask>

  <endEvent id="end"/>

  <sequenceFlow sourceRef="start" targetRef="task_submit"/>
  <sequenceFlow sourceRef="task_submit" targetRef="gateway_amount"/>
  <sequenceFlow sourceRef="task_manager_approve" targetRef="gateway_merge"/>
  <sequenceFlow sourceRef="task_purchase_approve" targetRef="gateway_merge"/>
  <sequenceFlow sourceRef="task_director_approve" targetRef="gateway_merge"/>
  <sequenceFlow sourceRef="gateway_merge" targetRef="task_order"/>
  <sequenceFlow sourceRef="task_order" targetRef="end"/>
</process>
```

**流程示例**：
```
张三提交采购申请：8000元
  → 部门经理李四审批

张三提交采购申请：3万元
  → 采购经理王五审批

张三提交采购申请：10万元
  → 采购总监赵六审批
```

---

### 示例3：项目评审流程

**需求**：项目立项需要评审委员会成员评审

**方案**：使用 USER_GROUP 策略 + 会签

```xml
<process id="project_review_process" name="项目评审流程">
  <startEvent id="start"/>

  <!-- 步骤1：项目经理提交立项申请 -->
  <userTask id="task_submit" name="提交立项申请"/>

  <!-- 步骤2：评审委员会成员会签评审 -->
  <userTask id="task_committee_review" name="评审委员会评审">
    <extensionElements>
      <flowable:candidateStrategy>11</flowable:candidateStrategy>
      <flowable:candidateParam>5</flowable:candidateParam>
    </extensionElements>
    <!-- 多实例配置：并行会签 -->
    <multiInstanceLoopCharacteristics isSequential="false"
      flowable:collection="${candidateUsers}"
      flowable:elementVariable="assignee">
      <!-- 完成条件：所有人都审批通过 -->
      <completionCondition>${nrOfCompletedInstances == nrOfInstances}</completionCondition>
    </multiInstanceLoopCharacteristics>
  </userTask>

  <!-- 步骤3：总经理最终审批 -->
  <userTask id="task_ceo_approve" name="总经理审批">
    <extensionElements>
      <flowable:candidateStrategy>30</flowable:candidateStrategy>
      <flowable:candidateParam>1</flowable:candidateParam>
    </extensionElements>
  </userTask>

  <endEvent id="end"/>

  <sequenceFlow sourceRef="start" targetRef="task_submit"/>
  <sequenceFlow sourceRef="task_submit" targetRef="task_committee_review"/>
  <sequenceFlow sourceRef="task_committee_review" targetRef="task_ceo_approve"/>
  <sequenceFlow sourceRef="task_ceo_approve" targetRef="end"/>
</process>
```

**用户组配置**：
```java
// 项目评审委员会（用户组ID: 5）
{
  "id": 5,
  "name": "项目评审委员会",
  "description": "负责公司所有新项目的立项评审",
  "userIds": [100, 101, 102, 103],  // 技术、产品、财务、运营总监
  "status": 1
}
```

**流程示例**：
```
张三（项目经理）提交新项目立项申请
  ↓
同时分配给评审委员会所有成员（会签）：
  ├─ 李四（技术总监）审批：通过
  ├─ 王五（产品总监）审批：通过
  ├─ 赵六（财务总监）审批：通过
  └─ 孙七（运营总监）审批：通过
  ↓
所有委员都审批通过后，提交给总经理
  ↓
周八（总经理）最终审批：通过
  ↓
项目立项成功
```

---

### 示例4：合同审批流程

**需求**：销售合同需要法务、财务、总经理审批

**方案**：组合使用岗位、角色、指定用户策略

```xml
<process id="contract_approval_process" name="合同审批流程">
  <startEvent id="start"/>

  <!-- 步骤1：销售员提交合同 -->
  <userTask id="task_submit" name="提交合同"/>

  <!-- 步骤2：销售经理审批 -->
  <userTask id="task_sales_manager" name="销售经理审批">
    <extensionElements>
      <flowable:candidateStrategy>12</flowable:candidateStrategy>
      <flowable:candidateParam>20</flowable:candidateParam>
    </extensionElements>
  </userTask>

  <!-- 步骤3：法务审批 -->
  <userTask id="task_legal" name="法务审批">
    <extensionElements>
      <flowable:candidateStrategy>10</flowable:candidateStrategy>
      <flowable:candidateParam>15</flowable:candidateParam>
    </extensionElements>
  </userTask>

  <!-- 步骤4：财务审批 -->
  <userTask id="task_finance" name="财务审批">
    <extensionElements>
      <flowable:candidateStrategy>10</flowable:candidateStrategy>
      <flowable:candidateParam>12</flowable:candidateParam>
    </extensionElements>
  </userTask>

  <!-- 步骤5：总经理审批 -->
  <userTask id="task_ceo" name="总经理审批">
    <extensionElements>
      <flowable:candidateStrategy>30</flowable:candidateStrategy>
      <flowable:candidateParam>1</flowable:candidateParam>
    </extensionElements>
  </userTask>

  <!-- 步骤6：销售员确认盖章 -->
  <userTask id="task_confirm_seal" name="确认盖章">
    <extensionElements>
      <flowable:candidateStrategy>60</flowable:candidateStrategy>
    </extensionElements>
  </userTask>

  <endEvent id="end"/>

  <sequenceFlow sourceRef="start" targetRef="task_submit"/>
  <sequenceFlow sourceRef="task_submit" targetRef="task_sales_manager"/>
  <sequenceFlow sourceRef="task_sales_manager" targetRef="task_legal"/>
  <sequenceFlow sourceRef="task_legal" targetRef="task_finance"/>
  <sequenceFlow sourceRef="task_finance" targetRef="task_ceo"/>
  <sequenceFlow sourceRef="task_ceo" targetRef="task_confirm_seal"/>
  <sequenceFlow sourceRef="task_confirm_seal" targetRef="end"/>
</process>
```

**流程示例**：
```
张三（销售员）提交销售合同
  ↓
李四（销售经理，岗位ID:20）审批通过
  ↓
王五（法务专员，角色ID:15）审批通过
  ↓
赵六（财务经理，角色ID:12）审批通过
  ↓
孙七（总经理，用户ID:1）审批通过
  ↓
行政部盖章
  ↓
张三（发起人）确认已取回合同
  ↓
流程结束
```

---

### 示例5：费用报销流程（带驳回）

**需求**：费用报销，可能需要补充材料

**方案**：使用条件网关 + START_USER 策略实现驳回重填

```xml
<process id="expense_process" name="费用报销流程">
  <startEvent id="start"/>

  <!-- 步骤1：员工提交报销 -->
  <userTask id="task_submit" name="提交报销申请">
    <extensionElements>
      <flowable:candidateStrategy>60</flowable:candidateStrategy>
    </extensionElements>
  </userTask>

  <!-- 步骤2：财务审批 -->
  <userTask id="task_finance_approve" name="财务审批">
    <extensionElements>
      <flowable:candidateStrategy>10</flowable:candidateStrategy>
      <flowable:candidateParam>12</flowable:candidateParam>
    </extensionElements>
  </userTask>

  <!-- 判断是否通过 -->
  <exclusiveGateway id="gateway_approved" name="是否通过"/>

  <!-- 通过：结束流程 -->
  <sequenceFlow sourceRef="gateway_approved" targetRef="end">
    <conditionExpression>${approved == true}</conditionExpression>
  </sequenceFlow>

  <!-- 驳回：返回发起人补充材料 -->
  <sequenceFlow sourceRef="gateway_approved" targetRef="task_supplement">
    <conditionExpression>${approved == false}</conditionExpression>
  </sequenceFlow>

  <userTask id="task_supplement" name="补充报销材料">
    <extensionElements>
      <flowable:candidateStrategy>60</flowable:candidateStrategy>
    </extensionElements>
  </userTask>

  <!-- 补充完成后重新提交审批 -->
  <sequenceFlow sourceRef="task_supplement" targetRef="task_finance_approve"/>

  <endEvent id="end"/>

  <sequenceFlow sourceRef="start" targetRef="task_submit"/>
  <sequenceFlow sourceRef="task_submit" targetRef="task_finance_approve"/>
</process>
```

**流程示例**：
```
场景1：一次通过
  张三提交报销 → 财务审批通过 → 流程结束

场景2：需要补充材料
  张三提交报销
    ↓
  财务审批：发票不清晰，需要补充
    ↓
  张三收到"补充材料"任务
    ├─ 重新上传清晰发票
    ├─ 填写补充说明
    └─ 重新提交
    ↓
  财务重新审批：通过
    ↓
  流程结束
```

---

## 🎓 学习路径

### 第一阶段：理解基础概念

1. **候选人策略的作用**
   - 什么是候选人？
   - 为什么需要候选人策略？
   - 策略模式在工作流中的应用

2. **用户身份维度理解**
   ```
   用户身份的多个维度：
   ├─ 用户本身（用户ID）
   ├─ 用户角色（系统权限）
   ├─ 用户岗位（组织职位）
   ├─ 用户组（自定义分组）
   └─ 流程角色（发起人、审批人等）
   ```

3. **策略类型分类**
   - 固定规则类：USER、ROLE、POST
   - 动态计算类：START_USER
   - 灵活组合类：USER_GROUP

---

### 第二阶段：掌握基础策略

**建议学习顺序**：

1. **USER - 指定用户**（最简单）
   - 理解：直接指定用户ID列表
   - 练习：配置一个固定人员审批的流程
   - 思考：什么场景下适合用固定用户？

2. **START_USER - 发起人**（最常用）
   - 理解：获取流程发起人
   - 练习：配置发起人确认结果的流程
   - 思考：发起人策略用在哪些环节？

3. **ROLE - 角色**（RBAC基础）
   - 理解：基于角色查找用户
   - 练习：配置角色驱动的审批流程
   - 思考：角色与岗位的区别？

---

### 第三阶段：学习高级策略

4. **POST - 岗位**
   - 理解：基于组织架构的岗位
   - 区别：角色 vs 岗位
   - 练习：配置岗位驱动的审批流程

5. **USER_GROUP - 用户组**
   - 理解：自定义用户分组
   - 场景：临时团队、跨部门协作
   - 练习：创建用户组并应用到流程

---

### 第四阶段：综合应用

6. **策略组合使用**
   - 学习如何在一个流程中组合使用多种策略
   - 练习设计复杂的审批流程
   - 掌握条件网关的使用

7. **高级特性**
   - 会签配置（多实例任务）
   - 条件判断（排他网关）
   - 驳回重填（循环流程）

---

### 学习建议

#### 🧪 实践练习

**练习1：策略理解**
```
为以下场景选择合适的策略：
1. 财务部所有人都可以审批 → ?
2. 必须由总经理审批 → ?
3. 员工确认审批结果 → ?
4. 项目组成员评审 → ?
5. 技术经理岗位审批 → ?
```

**练习2：流程设计**
```
设计以下流程并选择策略：
1. 员工转正流程
2. 物资领用流程
3. 项目立项流程
4. 客户投诉处理流程
```

**练习3：问题分析**
```
分析以下场景的问题并提出改进方案：
1. 使用 USER 策略指定10个用户，导致人员变动时频繁修改流程
2. 使用 ROLE 策略，但角色成员太多导致审批效率低
3. 多个流程都需要相同的一组人审批，每次都配置很麻烦
```

#### 📝 知识巩固

**关键问题自测**：

1. 什么时候用 USER 策略，什么时候用 ROLE 策略？
2. 角色和岗位有什么区别？
3. 用户组适合什么场景？
4. START_USER 策略用在流程的哪些环节？
5. 如何实现审批驳回后返回发起人修改？

**实践题**：

设计以下场景的配置方案：
1. 员工请假：3天以内直属领导批，超过3天需要人事批
2. 采购申请：根据金额分配不同审批人
3. 项目评审：需要评审委员会所有成员都审批通过
4. 合同审批：需要法务、财务、总经理依次审批

---

## 🔧 开发指南

### 新增策略步骤

如果需要开发新的用户策略，建议步骤：

**步骤1：创建策略类**
```java
@Component
public class BpmTaskCandidateCustomUserStrategy implements BpmTaskCandidateStrategy {

    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.CUSTOM_USER;
    }

    @Override
    public void validateParam(String param) {
        // 参数校验逻辑
        Assert.notBlank(param, "参数不能为空");
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        // 候选人计算逻辑
        // ...
        return candidateUserIds;
    }
}
```

**步骤2：注册策略枚举**
```java
// 在 BpmTaskCandidateStrategyEnum 中添加
CUSTOM_USER(70, "自定义用户策略"),
```

**步骤3：编写单元测试**
```java
@Test
public void testCustomUserStrategy() {
    BpmTaskCandidateCustomUserStrategy strategy = new BpmTaskCandidateCustomUserStrategy();
    Set<Long> users = strategy.calculateUsers("test-param");
    assertNotNull(users);
    assertTrue(users.size() > 0);
}
```

---

### 策略实现要点

#### 参数验证

```java
@Override
public void validateParam(String param) {
    // 1. 检查参数是否为空
    Assert.notBlank(param, "参数不能为空");

    // 2. 检查参数格式是否正确
    Set<Long> ids = StrUtils.splitToLongSet(param);
    Assert.notEmpty(ids, "参数格式错误");

    // 3. 检查业务数据是否存在
    // 例如：检查用户是否存在、角色是否有效等
    adminUserApi.validateUserList(ids).checkError();
}
```

#### 候选人计算

```java
@Override
public Set<Long> calculateUsers(String param) {
    // 1. 解析参数
    Set<Long> ids = StrUtils.splitToLongSet(param);

    // 2. 查询业务数据
    List<UserDTO> users = userService.getUserList(ids);

    // 3. 过滤无效数据（如：已离职用户）
    users = users.stream()
        .filter(user -> user.getStatus() == UserStatus.ENABLE)
        .collect(Collectors.toList());

    // 4. 提取用户ID并返回
    return convertSet(users, UserDTO::getId);
}
```

#### 性能优化

```java
@Override
public Set<Long> calculateUsers(String param) {
    // 1. 批量查询，避免N+1问题
    List<Long> ids = StrUtils.splitToLong(param, ",");
    List<UserDTO> users = userService.getUserListByIds(ids);  // 批量查询

    // 2. 使用缓存
    return users.stream()
        .map(user -> cacheManager.getUser(user.getId()))  // 使用缓存
        .map(UserDTO::getId)
        .collect(Collectors.toSet());

    // 3. 异步处理（如果允许）
    CompletableFuture<Set<Long>> future = CompletableFuture.supplyAsync(() -> {
        return calculateUsersAsync(param);
    });
    return future.get();
}
```

---

### 常见问题

#### Q1：用户数据不存在怎么办？

```java
@Override
public Set<Long> calculateUsers(String param) {
    Set<Long> userIds = StrUtils.splitToLongSet(param);

    // 验证用户是否存在
    List<AdminUserRespDTO> users = adminUserApi.validateUserList(userIds).getCheckedData();

    // 过滤已删除或禁用的用户
    return users.stream()
        .filter(user -> user.getStatus() == CommonStatusEnum.ENABLE.getStatus())
        .map(AdminUserRespDTO::getId)
        .collect(Collectors.toSet());
}
```

#### Q2：如何处理空候选人？

```java
@Override
public Set<Long> calculateUsers(String param) {
    Set<Long> candidates = // ... 计算候选人

    // 如果候选人为空，可以：
    // 方案1：返回空集合（任务无法分配，会报错）
    if (candidates.isEmpty()) {
        return Collections.emptySet();
    }

    // 方案2：返回默认候选人（如：管理员）
    if (candidates.isEmpty()) {
        return Sets.newHashSet(1L);  // 默认分配给管理员
    }

    // 方案3：抛出异常（阻止流程继续）
    if (candidates.isEmpty()) {
        throw new ServiceException("未找到符合条件的候选人");
    }

    return candidates;
}
```

#### Q3：如何支持动态参数？

```java
@Override
public Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
    // 从流程变量中获取动态参数
    Map<String, Object> variables = execution.getVariables();

    // 例如：根据流程变量中的金额决定审批人
    BigDecimal amount = (BigDecimal) variables.get("amount");
    if (amount.compareTo(new BigDecimal("10000")) < 0) {
        // 小于1万：部门经理
        return Sets.newHashSet(100L);
    } else {
        // 大于1万：总经理
        return Sets.newHashSet(1L);
    }
}
```

#### Q4：如何实现候选人去重？

```java
@Override
public Set<Long> calculateUsers(String param) {
    // 使用 Set 自动去重
    Set<Long> candidates = new HashSet<>();

    // 添加角色用户
    Set<Long> roleUsers = getRoleUsers(param);
    candidates.addAll(roleUsers);

    // 添加岗位用户
    Set<Long> postUsers = getPostUsers(param);
    candidates.addAll(postUsers);  // Set 会自动去重

    return candidates;
}
```

#### Q5：如何支持排除某些用户？

```java
@Override
public Set<Long> calculateUsers(String param) {
    // 参数格式："includeIds|excludeIds"
    // 例如："1,2,3|2" 表示包含1,2,3，但排除2
    String[] parts = param.split("\\|");
    Set<Long> includeIds = StrUtils.splitToLongSet(parts[0]);
    Set<Long> excludeIds = parts.length > 1 ? StrUtils.splitToLongSet(parts[1]) : Collections.emptySet();

    // 计算最终候选人（包含 - 排除）
    includeIds.removeAll(excludeIds);
    return includeIds;
}
```

---

## 📚 相关文档

- [Flowable 官方文档](https://www.flowable.com/open-source/docs)
- [BPMN 2.0 规范](https://www.omg.org/spec/BPMN/2.0/)
- [芋道源码 - BPM 模块文档](https://doc.iocoder.cn/bpm/)
- [部门候选人策略文档](../dept/README.md)

---

## 🤝 贡献指南

如果您发现文档有误或需要补充，欢迎提交 PR 或 Issue。

---

## 📄 许可证

本项目遵循 MIT 许可证。
