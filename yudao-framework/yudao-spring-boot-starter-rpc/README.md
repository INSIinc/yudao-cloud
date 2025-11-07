# yudao-spring-boot-starter-rpc

## 📖 模块介绍

`yudao-spring-boot-starter-rpc` 是芋道项目的 RPC（远程过程调用）框架核心模块，基于 Spring Cloud OpenFeign 实现，提供了微服务之间声明式 HTTP 调用的能力。

**模块坐标：**
```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-rpc</artifactId>
</dependency>
```

## ✨ 核心功能

### 1. 声明式服务调用
- 基于 OpenFeign 的声明式 HTTP 客户端
- 简化微服务间的远程调用
- 支持接口化编程，无需手动编写 HTTP 请求代码

### 2. 负载均衡
- 集成 Spring Cloud LoadBalancer
- 支持多种负载均衡策略（轮询、随机、权重等）
- 自动服务发现与实例选择

### 3. HTTP 客户端优化
- 集成 OkHttp 作为 HTTP 客���端
- 连接池管理，提升性能
- 支持 HTTP/2 协议

### 4. 参数校验
- 集成 Jakarta Validation
- 支持对 RPC 接口的入参进行校验
- 统一异常处理

## 🚀 快速开始

### 1. 添加依赖

在服务提供方和调用方的 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-rpc</artifactId>
</dependency>
```

### 2. 定义 API 接口（服务提供方）

在 `xxx-api` 模块中定义 Feign 接口：

```java
package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 管理员用户 API 接口
 */
@FeignClient(name = ApiConstants.NAME) // 指定服务名称
@Tag(name = "RPC 服务 - 管理员用户")
public interface AdminUserApi {

    String PREFIX = ApiConstants.PREFIX + "/user";

    /**
     * 根据用户 ID 查询用户信息
     */
    @GetMapping(PREFIX + "/get")
    @Operation(summary = "通过用户 ID 查询用户")
    CommonResult<AdminUserRespDTO> getUser(@RequestParam("id") Long id);
}
```

### 3. 实现 API 接口（服务提供方）

在 `xxx-server` 模块中实现接口：

```java
package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import user.cn.iocoder.yudao.module.hr.service.AdminUserService;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理员用户 API 实现类
 */
@RestController // 提供 REST API 接口
public class AdminUserApiImpl implements AdminUserApi {

    @Resource
    private AdminUserService adminUserService;

    @Override
    public CommonResult<AdminUserRespDTO> getUser(Long id) {
        AdminUserRespDTO user = adminUserService.getUser(id);
        return success(user);
    }
}
```

### 4. 启用 Feign 客户端（服务调用方）

在服务调用方创建 RPC 配置类：

```java
package cn.iocoder.yudao.module.xxx.framework.rpc.config;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * XXX 模块的 RPC 配置类
 */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = {AdminUserApi.class}) // 指定要启用的 Feign 客户端
public class RpcConfiguration {
}
```

### 5. 调用远程服务

在业务代码中注入并调用：

```java
package cn.iocoder.yudao.module.xxx.service;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class XxxService {

    @Resource
    private AdminUserApi adminUserApi;

    public void doSomething(Long userId) {
        // 像调用本地方法一样调用远程服务
        CommonResult<AdminUserRespDTO> result = adminUserApi.getUser(userId);
        AdminUserRespDTO user = result.getData();
        // 业务逻辑...
    }
}
```

## ⚙️ 配置说明

### 1. Feign 全局配置

在 `application.yml` 中配置：

```yaml
spring:
  cloud:
    openfeign:
      # 是否启用 Feign
      enabled: true
      # HTTP 客户端配置
      httpclient:
        # 启用 OkHttp
        enabled: false
      okhttp:
        # 启用 OkHttp（推荐）
        enabled: true
      # 压缩配置
      compression:
        request:
          enabled: true
          mime-types: text/xml,application/xml,application/json
          min-request-size: 2048
        response:
          enabled: true
      # 超时配置
      client:
        config:
          default:
            # 连接超时时间（毫秒）
            connect-timeout: 5000
            # 读取超时时间（毫秒）
            read-timeout: 10000
            # 日志级别：NONE, BASIC, HEADERS, FULL
            logger-level: BASIC
```

### 2. 负载均衡配置

```yaml
spring:
  cloud:
    loadbalancer:
      # 负载均衡策略
      configurations: default
      # 健康检查
      health-check:
        initial-delay: 0
```

### 3. 服务名称常量

在 API 模块中定义服务名称常量：

```java
package cn.iocoder.yudao.module.system.enums;

/**
 * API 常量
 */
public interface ApiConstants {

    /**
     * 服务名称
     */
    String NAME = "system-server";

    /**
     * API 前缀
     */
    String PREFIX = "/system";
}
```

## 🎯 最佳实践

### 1. 接口设计规范

- **统一返回值**：使用 `CommonResult<T>` 包装返回值
- **明确 API 前缀**：每个模块定义清晰的 URL 前缀
- **添加文档注解**：使用 `@Operation`、`@Tag` 等注解
- **参数校验**：使用 `@Valid`、`@NotNull` 等校验注解

### 2. 模块结构

```
yudao-module-xxx/
├── yudao-module-xxx-api/          # API 接口定义
│   └── src/main/java/
│       └── cn/iocoder/yudao/module/xxx/
│           ├── api/                # Feign 接口
│           ├── dto/                # 数据传输对象
│           └── enums/              # 常量和枚举
└── yudao-module-xxx-server/       # 服务实现
    └── src/main/java/
        └── cn/iocoder/yudao/module/xxx/
            ├── api/                # API 实现类
            ├── framework/
            │   └── rpc/
            │       └── config/     # RPC 配置
            └── service/            # 业务服务
```

### 3. 异常处理

服务提供方统一捕获异常并返回：

```java
@Override
public CommonResult<AdminUserRespDTO> getUser(Long id) {
    try {
        AdminUserRespDTO user = adminUserService.getUser(id);
        return success(user);
    } catch (Exception e) {
        return CommonResult.error(500, "查询用户失败：" + e.getMessage());
    }
}
```

### 4. 降级熔断（预留）

当前代码中预留了 `fallbackFactory` 配置，后续可以集成 Sentinel 实现熔断降级：

```java
@FeignClient(name = ApiConstants.NAME, fallbackFactory = AdminUserApiFallbackFactory.class)
public interface AdminUserApi {
    // ...
}
```

### 5. DTO 设计原则

- **轻量化**：只包含必要字段，避免传输冗余数据
- **版本化**：考虑向后兼容性，避免随意修改字段
- **文档化**：添加清晰的字段注释

```java
@Data
public class AdminUserRespDTO {
    
    @Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;
    
    @Schema(description = "用户昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋艿")
    private String nickname;
    
    @Schema(description = "部门ID", example = "1")
    private Long deptId;
}
```

## 📚 相关文档

- [《芋道 Spring Boot 声明式调用 Feign 入门》](./《芋道 Spring Boot 声明式调用 Feign 入门》.md)
- [《芋道 Spring Cloud 声明式调用 Feign 入门》](./《芋道 Spring Cloud 声明式调用 Feign 入门》.md)
- [Spring Cloud OpenFeign 官方文档](https://spring.io/projects/spring-cloud-openfeign)

## ⚠️ 注意事项

1. **服务名称一致性**：`@FeignClient(name = "xxx")` 中的服务名必须与注册中心的服务名一致
2. **依赖顺序**：调用方需要依赖提供方的 `xxx-api` 模块
3. **循环依赖**：避免服务之间的循环调用，合理设计服务边界
4. **超时设置**：根据业务场景合理设置超时时间，避免雪崩效应
5. **版本兼容**：升级 API 接口时注意向后兼容，避免影响已有调用方

## 🔧 技术栈

- **Spring Cloud OpenFeign**：声明式 HTTP 客户端
- **Spring Cloud LoadBalancer**：客户端负载均衡
- **OkHttp**：高性能 HTTP 客户端
- **Jakarta Validation**：参数校验

## 📝 更新日志

- 支持基于 OpenFeign 的声明式服务调用
- 集成 Spring Cloud LoadBalancer 负载均衡
- 集成 OkHttp 提升 HTTP 性能
- 支持 Jakarta Validation 参数校验

