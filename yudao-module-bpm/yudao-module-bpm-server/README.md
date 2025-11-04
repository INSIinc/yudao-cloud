# yudao-module-bpm-server

> 基于 Flowable 7.0.0 的企业级工作流管理模块

## 📋 模块概述

BPM (Business Process Management) 模块是芋道项目的工作流引擎核心，提供完整的流程设计、审批、监控能力。支持 BPMN 2.0 标准流程和类钉钉/飞书的 SIMPLE 简易流程设计器。

### 核心特性

- ✅ **双设计器支持**: BPMN 专业设计器 + SIMPLE 简易设计器
- ✅ **动态表单**: 基于 JSON 配置的可视化表单设计
- ✅ **丰富的审批操作**: 同意/拒绝/退回/委派/转派/加减签/撤回/抄送
- ✅ **灵活的候选人策略**: 用户/部门/角色/表单字段/表达式等多种方式
- ✅ **流程监听器**: 支持任务创建/完成、流程启动/结束事件
- ✅ **HTTP 触发器**: 流程/任务启动前后的 HTTP 回调
- ✅ **多租户支持**: 基于 MyBatis Plus 拦截器的租户隔离
- ✅ **权限控制**: 细粒度的流程发起/审批权限
- ✅ **工单打印**: 自定义打印模板配置

## 🏗️ 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 核心运行环境 |
| Spring Boot | 3.2+ | 基础框架 |
| Spring Cloud Alibaba | 2023.0.1 | 微服务组件 |
| Flowable | 7.0.0 | 工作流引擎 |
| MyBatis Plus | 3.5.7 | ORM 框架 |
| Redis + Redisson | 5.0/6.0 + 3.32.0 | 缓存 |
| MySQL | 5.7/8.0+ | 数据库 |
| MapStruct | - | 对象转换 |

## 📁 项目结构

```
yudao-module-bpm-server/
├── src/main/java/.../
│   ├── BpmServerApplication.java           # 启动类
│   │
│   ├── controller/admin/                   # RESTful API (11个 Controller)
│   │   ├── definition/                     # 流程定义相关
│   │   │   ├── BpmProcessDefinitionController   # 流程定义管理
│   │   │   ├── BpmModelController              # 流程模型设计
│   │   │   ├── BpmFormController               # 动态表单管理
│   │   │   ├── BpmCategoryController           # 流程分类
│   │   │   ├── BpmProcessListenerController    # 监听器配置
│   │   │   ├── BpmUserGroupController          # 用户组管理
│   │   │   └── BpmProcessExpressionController  # 流程表达式
│   │   ├── task/                           # 流程任务相关
│   │   │   ├── BpmTaskController               # 任务操作核心
│   │   │   ├── BpmProcessInstanceController    # 流程实例管理
│   │   │   └── BpmProcessInstanceCopyController # 流程抄送
│   │   └── oa/                             # OA 示例
│   │       └── BpmOALeaveController            # 请假申请示例
│   │
│   ├── service/                            # 业务逻辑层 (12个 Service, ~5,600行)
│   │   ├── definition/                     # 流程定义服务
│   │   ├── task/                           # 任务服务
│   │   │   ├── BpmTaskService              # 核心任务服务 (1,656行)
│   │   │   ├── BpmProcessInstanceService   # 流程实例服务 (1,064行)
│   │   │   └── listener/                   # 任务监听器
│   │   ├── message/                        # 消息服务
│   │   └── oa/                             # OA 服务
│   │
│   ├── dal/                                # 数据访问层
│   │   ├── dataobject/                     # 数据库实体 (8个 DO)
│   │   │   ├── definition/                 # 流程定义实体
│   │   │   │   ├── BpmFormDO              # 动态表单
│   │   │   │   ├── BpmCategoryDO          # 流程分类
│   │   │   │   ├── BpmProcessDefinitionInfoDO # 流程定义扩展信息
│   │   │   │   ├── BpmProcessListenerDO    # 流程监听器
│   │   │   │   ├── BpmProcessExpressionDO  # 流程表达式
│   │   │   │   └── BpmUserGroupDO          # 用户组
│   │   │   ├── task/
│   │   │   │   └── BpmProcessInstanceCopyDO # 流程抄送记录
│   │   │   └── oa/
│   │   │       └── BpmOALeaveDO            # 请假申请
│   │   ├── mysql/                          # MyBatis Mapper (8个)
│   │   └── redis/                          # Redis DAO
│   │
│   ├── convert/                            # MapStruct 转换器
│   │
│   ├── framework/                          # 框架扩展
│   │   ├── flowable/                       # Flowable 集成
│   │   │   ├── config/
│   │   │   │   └── BpmFlowableConfiguration # 引擎配置
│   │   │   └── core/
│   │   │       ├── behavior/               # 自定义流程行为
│   │   │       ├── candidate/              # 任务候选人策略
│   │   │       │   ├── BpmTaskCandidateInvoker
│   │   │       │   ├── BpmTaskCandidateStrategy
│   │   │       │   └── strategy/           # 策略实现
│   │   │       │       ├── user/           # 用户策略
│   │   │       │       ├── dept/           # 部门策略
│   │   │       │       ├── form/           # 表单字段策略
│   │   │       │       └── other/          # 其他策略
│   │   │       ├── el/                     # 表达式语言支持
│   │   │       ├── event/                  # 事件发布
│   │   │       ├── listener/               # 事件监听器
│   │   │       └── util/                   # 工具类
│   │   │           ├── BpmnModelUtils      # BPMN 工具 (1,099行)
│   │   │           ├── SimpleModelUtils    # SIMPLE 工具 (1,035行)
│   │   │           ├── FlowableUtils       # Flowable 工具 (362行)
│   │   │           └── BpmHttpRequestUtils # HTTP 请求工具
│   │   ├── rpc/                            # RPC 配置
│   │   ├── security/                       # 安全配置
│   │   └── web/                            # Web 配置
│   │
│   └── api/                                # Feign API 实现
│       └── task/
│           └── BpmProcessInstanceApiImpl   # 流程实例 API
│
└── src/main/resources/
    ├── application.yaml                    # 主配置文件
    ├── application-local.yaml              # 本地开发配置
    └── application-dev.yaml                # 开发环境配置
```

## 🚀 快速开始

### 1. 环境准备

确保以下服务已启动:

- **MySQL 5.7/8.0+**: 执行 `sql/mysql/ruoyi-vue-pro.sql` 初始化数据库
- **Redis 5.0+**: 默认连接 `localhost:6379`
- **Nacos 2.3.2** (可选，仅微服务模式需要)

### 2. 配置数据库

修改 `application-local.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/ruoyi-vue-pro?useUnicode=true&characterEncoding=UTF-8&autoReconnect=true
    username: root
    password: 123456
```

### 3. 启动服务

**方式一: IDEA 启动**
```
直接运行 BpmServerApplication.java
```

**方式二: Maven 启动**
```bash
cd yudao-module-bpm/yudao-module-bpm-server
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**方式三: JAR 包启动**
```bash
mvn clean package -DskipTests
java -jar target/yudao-module-bpm-server.jar --spring.profiles.active=local
```

默认端口: **48083**

### 4. 访问接口文档

启动后访问 Swagger UI:
```
http://localhost:48083/swagger-ui.html
```

## 📚 核心功能

### 1️⃣ 流程定义管理

**BpmProcessDefinitionController** (`/admin-api/bpm/process-definition`)

- 获取流程定义列表、分页、详情
- 获取流程 BPMN 模型
- 权限检查（用户是否可发起流程）

### 2️⃣ 流程模型设计

**BpmModelController** (`/admin-api/bpm/model`)

支持两种设计器:

| 设计器类型 | 枚举值 | 说明 |
|-----------|--------|------|
| BPMN | 10 | 基于 bpmn.io 的专业设计器，完整支持 BPMN 2.0 标准 |
| SIMPLE | 20 | 仿钉钉/飞书的简易拖拽设计器 |

主要操作:
- 创建/编辑/删除模型
- 保存草稿/发布流程
- 模型版本管理

### 3️⃣ 动态表单管理

**BpmFormController** (`/admin-api/bpm/form`)

- 基于 form-generator 的可视化表单设计
- 表单配置存储为 JSON 格式
- 支持字段权限控制（隐藏/只读/编辑）

**表单类型:**
- **NORMAL**: 动态表单（推荐）
- **CUSTOM**: 自定义 Vue 路由
- **NO_FORM**: 无表单

### 4️⃣ 任务审批操作

**BpmTaskController** (`/admin-api/bpm/task`)

提供 11 种核心操作:

| API 路径 | 功能 | 说明 |
|---------|------|------|
| `GET /todo-page` | 待办任务列表 | 当前用户的待审批任务 |
| `GET /done-page` | 已办任务列表 | 已处理的任务 |
| `GET /manager-page` | 所有任务列表 | 管理员视图 |
| `PUT /approve` | 审批通过 | 同意并流转到下一节点 |
| `PUT /reject` | 审批拒绝 | 拒绝申请 |
| `PUT /return` | 任务退回 | 退回到指定节点 |
| `PUT /delegate` | 委派任务 | 委托给他人代审 |
| `PUT /transfer` | 转派任务 | 转移给其他人 |
| `PUT /create-sign` | 加签 | 前加签/后加签 |
| `DELETE /delete-sign` | 减签 | 移除多实例任务 |
| `POST /copy` | 抄送 | 抄送给其他人 |
| `PUT /withdraw` | 撤回 | 审批人撤回已提交的任务 |

### 5️⃣ 流程实例管理

**BpmProcessInstanceController** (`/admin-api/bpm/process-instance`)

- 启动流程实例
- 查询流程实例详情、分页、列表
- 获取流程历史信息
- 取消流程申请

**流程实例状态:**
- `NOT_START (-1)`: 未开始
- `RUNNING (1)`: 审批中
- `APPROVE (2)`: 审批通过
- `REJECT (3)`: 审批拒绝
- `CANCEL (4)`: 已取消

### 6️⃣ 任务候选人策略

**核心组件**: `BpmTaskCandidateInvoker` + `BpmTaskCandidateStrategy`

支持的策略:

| 策略 | 说明 |
|------|------|
| **用户策略** | 指定具体用户 |
| **部门策略** | 部门主管、高级主管、负责人 |
| **角色策略** | 指定角色成员 |
| **表单字段策略** | 从申请表单字段中读取 |
| **流程发起人** | 流程的创建者 |
| **前审批人** | 上一节点的审批人 |
| **自定义表达式** | 支持 SpEL 表达式 |

### 7️⃣ 流程监听器

**BpmProcessListenerController** (`/admin-api/bpm/process-listener`)

支持的事件类型:
- `TASK_CREATE`: 任务创建时
- `TASK_COMPLETE`: 任务完成时
- `PROCESS_START`: 流程启动时
- `PROCESS_END`: 流程结束时

监听器值类型:
- `BEAN`: Spring Bean 名称
- `CLASS`: Java 类全名
- `EXPRESSION`: 动态表达式
- `DELEGATE_EXPRESSION`: 委托表达式

### 8️⃣ HTTP 触发器

支持在流程/任务启动前后发送 HTTP 回调:

- `processBeforeTriggerSetting`: 流程启动前 HTTP 通知
- `processAfterTriggerSetting`: 流程结束后 HTTP 通知
- `taskBeforeTriggerSetting`: 任务创建前 HTTP 通知
- `taskAfterTriggerSetting`: 任务完成后 HTTP 通知

**用途**: 与外部系统集成（工单系统、钉钉、企业微信等）

## 🔌 与其他模块集成

### RPC 接口

**BpmProcessInstanceApi** (供其他模块调用)

```java
@FeignClient(name = "bpm-server")
public interface BpmProcessInstanceApi {

    /**
     * 创建流程实例
     * @param userId 用户 ID
     * @param createReqDTO 创建请求
     * @return 流程实例编号
     */
    @PostMapping("/bpm/process-instance/create")
    String createProcessInstance(@RequestParam("userId") Long userId,
                                  @RequestBody BpmProcessInstanceCreateReqDTO createReqDTO);
}
```

### 事件机制

**BpmProcessInstanceStatusEvent** - 流程实例状态变更事件

其他模块可监听此事件进行业务处理:

```java
@Component
public class CrmContractStatusListener implements BpmProcessInstanceStatusEventListener {

    @Override
    public void onEvent(BpmProcessInstanceStatusEvent event) {
        // 处理合同审批结果
    }
}
```

**示例监听器:**
- `CrmContractStatusListener`: CRM 合同状态监听
- `CrmReceivableStatusListener`: CRM 应收款状态监听

## 🗄️ 数据库表

### 自定义表 (8张)

| 表名 | 说明 |
|------|------|
| `bpm_form` | 动态表单定义 |
| `bpm_category` | 流程分类 |
| `bpm_process_definition_info` | 流程定义扩展信息 |
| `bpm_process_listener` | 流程监听器配置 |
| `bpm_process_expression` | 流程表达式 |
| `bpm_user_group` | 用户组 |
| `bpm_process_instance_copy` | 流程抄送记录 |
| `bpm_oa_leave` | OA 请假申请示例 |

### Flowable 原生表 (25+张)

- `act_re_*`: Repository 表（流程定义、模型）
- `act_ru_*`: Runtime 表（运行时数据）
- `act_hi_*`: History 表（历史数据）
- `act_ge_*`: General 表（通用数据）

## ⚙️ 配置说明

### Flowable 配置

```yaml
flowable:
  database-schema-update: true      # 自动创建/更新表
  db-history-used: true             # 生成历史表
  check-process-definitions: false  # 不自动部署 BPMN
  history-level: audit              # 完整历史记录
  async-executor:
    enabled: true
    core-pool-size: 8
    max-pool-size: 8
    queue-capacity: 100
```

### 多租户配置

```yaml
yudao:
  tenant:
    enable: true                     # 启用多租户
    ignore-tables:                   # 忽略租户隔离的表
      - act_*                        # Flowable 表不需要租户隔离
```

## 📖 业务流程示例

### OA 请假流程

**BpmOALeaveController** (`/admin-api/bpm/oa/leave`)

演示了如何与 BPM 模块集成:

1. 用户创建请假申请 → `BpmOALeaveService.createLeave()`
2. 触发流程启动 → `BpmProcessInstanceService.startProcessInstance()`
3. 创建待办任务 → 分配给审批人
4. 审批操作 → `BpmTaskService.approveTask()` / `rejectTask()`
5. 流程结束 → 发布 `BpmProcessInstanceStatusEvent`
6. 监听器更新请假状态 → `BpmOALeaveStatusListener.onEvent()`

## 🛠️ 开发指南

### 1. 集成工作流到业务模块

**步骤一: 创建业务表**

```sql
CREATE TABLE your_business_table (
    id BIGINT PRIMARY KEY,
    process_instance_id VARCHAR(64),  -- 流程实例 ID
    status TINYINT,                   -- 审批状态
    ...
);
```

**步骤二: 调用 BPM API 启动流程**

```java
@Resource
private BpmProcessInstanceApi bpmProcessInstanceApi;

public void createApply(YourApplyDTO dto) {
    // 1. 保存业务数据
    YourBusinessDO entity = saveBusinessData(dto);

    // 2. 启动流程
    BpmProcessInstanceCreateReqDTO createReqDTO = new BpmProcessInstanceCreateReqDTO()
        .setProcessDefinitionKey("your_process_key")
        .setVariables(Map.of("applyId", entity.getId()));

    String processInstanceId = bpmProcessInstanceApi.createProcessInstance(
        SecurityUtils.getUserId(), createReqDTO);

    // 3. 更新业务数据
    entity.setProcessInstanceId(processInstanceId);
    entity.setStatus(RUNNING);
    updateById(entity);
}
```

**步骤三: 监听流程结束事件**

```java
@Component
public class YourBusinessStatusListener implements BpmProcessInstanceStatusEventListener {

    @Override
    public void onEvent(BpmProcessInstanceStatusEvent event) {
        if (!"your_process_key".equals(event.getProcessDefinitionKey())) {
            return;
        }

        // 根据流程状态更新业务数据
        Long applyId = (Long) event.getVariables().get("applyId");
        Integer status = convertStatus(event.getStatus());

        updateApplyStatus(applyId, status);
    }
}
```

### 2. 自定义任务监听器

```java
@Component("yourTaskListener")
public class YourTaskListener implements TaskListener {

    @Override
    public void notify(DelegateTask delegateTask) {
        // 任务创建/完成时的自定义逻辑
        String taskName = delegateTask.getName();
        // ...
    }
}
```

在流程定义中配置:
```xml
<userTask id="task1" name="审批">
    <extensionElements>
        <flowable:taskListener event="create" delegateExpression="${yourTaskListener}" />
    </extensionElements>
</userTask>
```

### 3. 自定义候选人策略

```java
@Component
public class YourCandidateStrategy implements BpmTaskCandidateStrategy {

    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.CUSTOM;
    }

    @Override
    public void validateParam(String param) {
        // 参数校验
    }

    @Override
    public Set<Long> calculateUsers(DelegateExecution execution, String param) {
        // 计算候选人
        return calculateUserIds(param);
    }
}
```

## 📊 代码统计

| 分类 | 数量 | 代码行数 |
|------|------|---------|
| Controller | 11 个 | ~1,400 |
| Service | 12 个 | ~5,600 |
| Mapper | 8 个 | - |
| DataObject | 8 个 | - |
| 工具类 | BpmnModelUtils | 1,099 |
| 工具类 | SimpleModelUtils | 1,035 |
| 核心服务 | BpmTaskServiceImpl | 1,656 |
| 核心服务 | BpmProcessInstanceServiceImpl | 1,064 |
| **总计** | **189 个 Java 文件** | **~15,000+** |

## 🔗 相关资源

- **官方文档**: https://doc.iocoder.cn/bpm/
- **Flowable 官方文档**: https://www.flowable.com/open-source/docs/
- **BPMN 2.0 规范**: https://www.omg.org/spec/BPMN/2.0/
- **前端 Vue3 版本**: https://gitee.com/yudaocode/yudao-ui-admin-vue3
- **前端 Vben 版本**: https://gitee.com/yudaocode/yudao-ui-admin-vben

## 🤝 贡献

欢迎提交 Issue 和 Pull Request!

## 📄 开源协议

MIT License

---

**最后更新**: 2025-11-03
