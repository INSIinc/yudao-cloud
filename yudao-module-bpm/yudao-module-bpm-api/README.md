# yudao-module-bpm-api

## 📖 模块简介

`yudao-module-bpm-api` 是芋道工作流（BPM）模块的 **API 接口层**，专门用于对外暴露服务接口，供其他微服务模块通过 RPC 方式调用。

该模块基于 **Flowable 7.0.0** 工作流引擎，提供完整的流程管理能力，包括流程实例创建、状态变更通知等核心功能。

## 🏗️ 模块职责

本模块作为 BPM 系统的**对外契约层**，主要职责包括：

1. **定义 Feign 接口**：提供 RPC 调用接口，供其他微服务模块远程调用
2. **定义数据传输对象（DTO）**：规范跨服务调用的数据格式
3. **定义事件机制**：通过 Spring Event 实现流程状态变更的异步通知
4. **定义枚举和常量**：统一各类业务枚举、错误码、API 常量等

## 📦 依赖说明

```xml
<dependencies>
    <!-- 芋道通用模块 -->
    <dependency>
        <groupId>cn.iocoder.cloud</groupId>
        <artifactId>yudao-common</artifactId>
    </dependency>

    <!-- OpenAPI 文档：Swagger 模型定义 -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    </dependency>

    <!-- 参数校验 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- OpenFeign：微服务 RPC 调用 -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
    </dependency>
</dependencies>
```

## 📁 目录结构

```
yudao-module-bpm-api/
├── pom.xml
└── src/main/java/cn/iocoder/yudao/module/bpm/
    ├── api/                              # API 接口定义
    │   ├── event/                        # 事件机制
    │   │   ├── BpmProcessInstanceStatusEvent.java          # 流程实例状态变更事件
    │   │   └── BpmProcessInstanceStatusEventListener.java  # 事件监听器接口
    │   └── task/                         # 流程任务 API
    │       ├── BpmProcessInstanceApi.java                  # 流程实例 Feign 接口
    │       └── dto/                                        # 数据传输对象
    │           └── BpmProcessInstanceCreateReqDTO.java     # 创建流程实例请求 DTO
    └── enums/                            # 枚举和常量
        ├── ApiConstants.java             # API 常量（服务名、路径前缀等）
        ├── DictTypeConstants.java        # 字典类型常量
        ├── ErrorCodeConstants.java       # 错误码常量（1-009-xxx-xxx）
        ├── definition/                   # 流程定义相关枚举（20+ 个）
        │   ├── BpmModelTypeEnum.java                       # 流程模型类型
        │   ├── BpmModelFormTypeEnum.java                   # 表单类型
        │   ├── BpmSimpleModelNodeTypeEnum.java             # 简易模型节点类型
        │   ├── BpmUserTaskApproveTypeEnum.java             # 审批类型
        │   ├── BpmUserTaskApproveMethodEnum.java           # 审批方式
        │   ├── BpmFieldPermissionEnum.java                 # 字段权限
        │   └── ...                                         # 更多流程定义枚举
        ├── task/                         # 流程任务相关枚举
        │   ├── BpmProcessInstanceStatusEnum.java           # 流程实例状态
        │   ├── BpmTaskStatusEnum.java                      # 任务状态
        │   ├── BpmTaskSignTypeEnum.java                    # 加签类型
        │   ├── BpmCommentTypeEnum.java                     # 评论类型
        │   └── BpmReasonEnum.java                          # 审批原因
        └── message/                      # 消息相关枚举
            └── BpmMessageEnum.java                         # 消息类型枚举
```

## 🔌 核心 API 接口

### 1. BpmProcessInstanceApi（流程实例接口）

用于创建和管理流程实例的 Feign 客户端接口。

```java
@FeignClient(name = ApiConstants.NAME) // name = "bpm-server"
@Tag(name = "RPC 服务 - 流程实例")
public interface BpmProcessInstanceApi {

    /**
     * 创建流程实例（提供给内部模块调用）
     *
     * @param userId 发起流程的用户 ID
     * @param reqDTO 流程实例创建请求（包含流程定义 key、变量、业务 key 等）
     * @return 流程实例编号（processInstanceId）
     */
    @PostMapping(PREFIX + "/create")
    CommonResult<String> createProcessInstance(
        @RequestParam("userId") Long userId,
        @Valid @RequestBody BpmProcessInstanceCreateReqDTO reqDTO
    );
}
```

**调用示例**：

```java
// 在其他模块中注入使用
@Resource
private BpmProcessInstanceApi bpmProcessInstanceApi;

public void startLeaveProcess(Long userId, LeaveRequest leaveRequest) {
    // 构造请求参数
    BpmProcessInstanceCreateReqDTO reqDTO = new BpmProcessInstanceCreateReqDTO();
    reqDTO.setProcessDefinitionKey("leave");  // 流程定义 key
    reqDTO.setBusinessKey(leaveRequest.getId().toString());  // 业务唯一标识
    reqDTO.setVariables(Map.of(
        "days", leaveRequest.getDays(),
        "reason", leaveRequest.getReason()
    ));

    // RPC 调用创建流程实例
    CommonResult<String> result = bpmProcessInstanceApi.createProcessInstance(userId, reqDTO);
    String processInstanceId = result.getData();
}
```

## 📤 事件机制

### BpmProcessInstanceStatusEvent（流程实例状态变更事件）

当流程实例的状态发生变化时（如审批通过、驳回、取消），BPM 模块会发布此事件，其他模块可以通过监听该事件来感知流程状态变化。

**事件字段**：

```java
public class BpmProcessInstanceStatusEvent extends ApplicationEvent {
    private String id;                    // 流程实例 ID
    private String processDefinitionKey;  // 流程定义 key（如 "leave"）
    private Integer status;               // 流程状态（见 BpmProcessInstanceStatusEnum）
    private String reason;                // 流程结束原因
    private String businessKey;           // 业务唯一标识（如请假申请 ID）
}
```

**监听事件示例**：

```java
@Component
public class LeaveProcessListener implements BpmProcessInstanceStatusEventListener {

    @EventListener
    public void onProcessStatusChange(BpmProcessInstanceStatusEvent event) {
        if (!"leave".equals(event.getProcessDefinitionKey())) {
            return; // 只处理请假流程
        }

        // 根据流程状态更新业务数据
        switch (BpmProcessInstanceStatusEnum.valueOf(event.getStatus())) {
            case APPROVE:
                // 审批通过，更新请假单状态
                leaveService.approve(event.getBusinessKey());
                break;
            case REJECT:
                // 审批驳回
                leaveService.reject(event.getBusinessKey(), event.getReason());
                break;
            case CANCEL:
                // 流程取消
                leaveService.cancel(event.getBusinessKey());
                break;
        }
    }
}
```

## 📊 核心枚举说明

### 流程实例状态（BpmProcessInstanceStatusEnum）

| 状态值 | 枚举名称 | 说明 |
|-------|---------|------|
| -1 | NOT_START | 未开始 |
| 1 | RUNNING | 审批中 |
| 2 | APPROVE | 审批通过 |
| 3 | REJECT | 审批不通过 |
| 4 | CANCEL | 已取消 |

### 任务状态（BpmTaskStatusEnum）

定义流程任务的各种状态（待审批、已完成、已驳回等）。

### 审批类型（BpmUserTaskApproveTypeEnum）

定义审批节点的类型：
- 人工审批
- 自动通过
- 自动拒绝

### 节点类型（BpmSimpleModelNodeTypeEnum）

支持仿钉钉/飞书的简易流程设计器节点类型：
- 开始节点
- 审批节点
- 抄送节点
- 条件分支
- 并行分支
- 结束节点

## 🔢 错误码规范

BPM 模块使用 `1-009-xxx-xxx` 段错误码：

| 模块 | 错误码段 | 说明 |
|------|---------|------|
| 通用流程处理 | 1-009-000-000 | 通用错误 |
| OA 流程 | 1-009-001-000 | 请假、出差等 OA 流程 |
| 流程模型 | 1-009-002-000 | 流程模型 CRUD、部署等 |
| 流程定义 | 1-009-003-000 | 流程定义相关 |
| 流程实例 | 1-009-004-000 | 流程实例创建、取消等 |
| 流程任务 | 1-009-005-000 | 任务审批、委派、转办等 |
| 动态表单 | 1-009-010-000 | 表单配置 |
| 用户组 | 1-009-011-000 | 用户分组管理 |
| 流程分类 | 1-009-012-000 | 流程分类管理 |
| 流程监听器 | 1-009-013-000 | 监听器配置 |
| 流程表达式 | 1-009-014-000 | 表达式管理 |

**常见错误码示例**：

```java
ErrorCode PROCESS_INSTANCE_NOT_EXISTS = new ErrorCode(1_009_004_000, "流程实例不存在");
ErrorCode TASK_OPERATE_FAIL_ASSIGN_NOT_SELF = new ErrorCode(1_009_005_001, "操作失败，原因：该任务的审批人不是你");
ErrorCode MODEL_NOT_EXISTS = new ErrorCode(1_009_002_001, "流程模型不存在");
```

## 🚀 使用指南

### 步骤 1：添加 Maven 依赖

在需要调用 BPM 服务的模块中，添加以下依赖：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-module-bpm-api</artifactId>
    <version>${revision}</version>
</dependency>
```

### 步骤 2：启用 Feign 客户端（微服务模式）

确保你的启动类上有 `@EnableFeignClients` 注解：

```java
@SpringBootApplication
@EnableFeignClients(basePackages = "cn.iocoder.yudao.module.*.api") // 扫描所有 API 模块
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### 步骤 3：注入并使用 API

```java
@Service
public class YourBusinessService {

    @Resource
    private BpmProcessInstanceApi bpmProcessInstanceApi;

    public void startProcess() {
        BpmProcessInstanceCreateReqDTO reqDTO = new BpmProcessInstanceCreateReqDTO();
        reqDTO.setProcessDefinitionKey("your-process-key");
        reqDTO.setBusinessKey("business-unique-id");
        reqDTO.setVariables(Map.of("key", "value"));

        CommonResult<String> result = bpmProcessInstanceApi.createProcessInstance(userId, reqDTO);
        if (result.isSuccess()) {
            String processInstanceId = result.getData();
            // 处理流程实例 ID
        }
    }
}
```

### 步骤 4：监听流程状态变更（可选）

```java
@Component
public class YourProcessListener {

    @EventListener
    public void handleProcessStatusChange(BpmProcessInstanceStatusEvent event) {
        // 处理流程状态变更
        log.info("流程 {} 状态变更为: {}", event.getProcessDefinitionKey(), event.getStatus());
    }
}
```

## 📝 重要说明

### 单体模式 vs 微服务模式

- **微服务模式**：通过 Feign 进行 RPC 调用，需要启动 Nacos 注册中心
- **单体模式**：模块间直接调用，Feign 接口会被自动降级为本地调用

### API 路径规范

所有 BPM 模块的 API 路径统一使用前缀：

```java
ApiConstants.PREFIX = "/rpc-api/bpm"  // RPC API 前缀
ApiConstants.NAME = "bpm-server"       // 服务名（需与 spring.application.name 一致）
```

### DTO 命名规范

- `XxxReqDTO`：请求参数对象
- `XxxRespDTO`：响应结果对象
- `XxxCreateReqDTO`：创建请求对象
- `XxxUpdateReqDTO`：更新请求对象

## 🔗 相关模块

- **yudao-module-bpm-server**：BPM 模块的服务端实现，包含 Controller、Service、DAL 等完整业务逻辑
- **yudao-framework**：框架拓展封装，提供各种 starter
- **yudao-common**：通用工具类、基础模型等

## 📚 参考文档

- [芋道工作流文档](https://doc.iocoder.cn/bpm/)
- [Flowable 官方文档](https://www.flowable.com/open-source/docs/bpmn/ch01-Introduction)
- [Spring Cloud OpenFeign 文档](https://spring.io/projects/spring-cloud-openfeign)

## 💡 最佳实践

1. **接口设计原则**：API 接口应保持稳定，避免频繁修改参数结构
2. **版本控制**：通过 `ApiConstants.VERSION` 管理 API 版本
3. **异常处理**：统一使用 `CommonResult` 封装返回结果，避免抛出业务异常
4. **事件驱动**：对于需要解耦的业务场景，优先使用事件机制而非直接 RPC 调用
5. **参数校验**：DTO 对象使用 JSR-303 注解进行参数校验

## ⚠️ 注意事项

1. **循环依赖**：API 模块应保持轻量，避免依赖其他业务模块
2. **枚举同步**：枚举定义应与数据库字典保持一致
3. **错误码管理**：新增错误码时，确保错误码不冲突
4. **服务降级**：生产环境建议配置 Feign 的降级策略（fallbackFactory）

---

**版本**: ${revision}
**最后更新**: 2025-11-06
**维护者**: 芋道源码
