# yudao-server 可以单体启动而不需要 Nacos 的原因

## 📋 核心原理

`yudao-server` 项目是一个**特殊设计的单体应用启动器**，它可以在不依赖 Nacos 等微服务组件的情况下运行。主要通过以下几个关键机制实现：

---

## 🔑 关键机制

### 1. **禁用 Spring Cloud 组件**

在 `application.yaml` 配置文件中，显式禁用了 Nacos 的注册发现和配置中心功能：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        enabled: false # 禁用 Nacos 的注册发现
      config:
        enabled: false # 禁用 Nacos 的配置中心
```

**作用：** 即使 classpath 中存在 Nacos 相关的依赖，Spring Boot 也不会启动这些组件。

---

### 2. **排除 OpenFeign 依赖**

在 `yudao-server/pom.xml` 中，通过 Maven exclusion 排除了 OpenFeign：

```xml
<!-- RPC 远程调用相关 -->
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-rpc</artifactId>
    <!-- 目的：yudao-server 单体启动，禁用 openfeign -->
    <exclusions>
        <exclusion>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

**作用：**
- OpenFeign 是用于微服务间 HTTP 远程调用的组件
- 排除后，所有 `@FeignClient` 注解的接口不会被创建为远程调用代理
- 但保留了 RPC 模块的其他功能（如接口定义）

---

### 3. **启动类中注释掉 RPC 配置**

在 `YudaoServerApplication.java` 中，通过 `excludeName` 预留了排除 RPC 配置的选项（当前已注释）：

```java
@SpringBootApplication(
    scanBasePackages = {
        "${yudao.info.base-package}.server", 
        "${yudao.info.base-package}.module"
    },
    excludeName = {
        // RPC 相关（已注释，说明目前通过 pom.xml 排除）
        // "org.springframework.cloud.openfeign.FeignAutoConfiguration",
        // "cn.iocoder.yudao.module.system.framework.rpc.config.RpcConfiguration"
    }
)
```

---

### 4. **直接依赖模块实现**

`yudao-server` 通过 Maven 依赖直接引入了各个业务模块的 **server** 实现：

```xml
<dependencies>
    <!-- 系统模块 -->
    <dependency>
        <groupId>cn.iocoder.cloud</groupId>
        <artifactId>yudao-module-system-server</artifactId>
    </dependency>
    
    <!-- 基础设施模块 -->
    <dependency>
        <groupId>cn.iocoder.cloud</groupId>
        <artifactId>yudao-module-infra-server</artifactId>
    </dependency>
    
    <!-- BPM 工作流模块 -->
    <dependency>
        <groupId>cn.iocoder.cloud</groupId>
        <artifactId>yudao-module-bpm-server</artifactId>
    </dependency>
    
    <!-- 其他模块... -->
</dependencies>
```

**关键点：**
- 所有模块的 **Service 实现类**都在同一个 JVM 进程中
- 模块间调用通过 **本地方法调用**，而不是远程 HTTP 调用
- 扫描包路径 `${yudao.info.base-package}.module` 会加载所有模块的 Bean

---

## 🔄 微服务 vs 单体模式对比

| 特性 | 微服务模式（Gateway + 各模块独立部署） | 单体模式（yudao-server） |
|------|--------------------------------------|------------------------|
| **部署方式** | 每个模块独立运行（system、infra、bpm 等） | 所有模块打包在一个 jar 中 |
| **服务注册** | 需要 Nacos Discovery | 禁用（enabled: false） |
| **配置中心** | 需要 Nacos Config | 禁用（enabled: false） |
| **模块间调用** | 通过 OpenFeign HTTP 远程调用 | 本地 Spring Bean 方法调用 |
| **网关** | 需要 Gateway 路由转发 | 不需要，直接暴露 REST API |
| **数据库连接** | 每个服务独立连接池 | 共享连接池 |
| **适用场景** | 大规模分布式系统，需要独立扩展 | 中小型项目，快速开发和部署 |

---

## 🎯 为什么这样设计？

### 优势：

1. **降低开发复杂度**
    - 不需要启动多个服务进程
    - 不需要配置 Nacos、Gateway 等基础设施
    - 开发调试更方便

2. **灵活部署选择**
    - 小型项目：使用 `yudao-server` 单体部署
    - 大型项目：拆分为微服务独立部署
    - **同一套代码支持两种模式**

3. **降低资源消耗**
    - 单个 JVM 进程，内存占用更少
    - 无需额外的中间件（Nacos、Gateway）
    - 适合资源有限的环境

---

## 📦 技术实现细节

### RPC 接口如何工作？

以 `system` 模块调用 `infra` 模块的文件上传接口为例：

**微服务模式：**
```
system-server (端口8001) 
    -> OpenFeign HTTP 请求
    -> infra-server (端口8002) 
    -> FileServiceImpl.uploadFile()
```

**单体模式：**
```
system-controller
    -> 直接注入 FileServiceImpl (同一 JVM)
    -> fileService.uploadFile()
```

### 各模块的 `RpcConfiguration` 被忽略

虽然每个模块都有 `RpcConfiguration` 类（带 `@EnableFeignClients`），但由于：
- OpenFeign 依赖被排除
- `FeignAutoConfiguration` 不会执行
- 这些配置类即使被扫描到，也无法创建 Feign 客户端代理

---

## 🚀 启动流程

```
1. SpringApplication.run(YudaoServerApplication.class)
   ↓
2. 扫描包: cn.iocoder.yudao.server + cn.iocoder.yudao.module
   ↓
3. 加载所有模块的 @Service、@Controller、@Configuration
   ↓
4. 检测到 spring.cloud.nacos.discovery.enabled=false
   → 跳过 Nacos 服务注册
   ↓
5. 检测不到 spring-cloud-starter-openfeign
   → 跳过 Feign 客户端创建
   ↓
6. 所有模块的 Bean 在同一 ApplicationContext 中
   ↓
7. 启动完成，监听端口 48080（dev 环境）
```

---

## 📝 总结

`yudao-server` 的核心设计思想是：

> **通过配置禁用微服务组件 + 排除远程调用依赖 + 直接依赖模块实现**，将原本的微服务架构退化为单体应用，但**保留了模块化的代码结构**，实现了"一套代码，两种部署模式"的架构弹性。

这种设计让开发者可以：
- ✅ 本地开发时使用单体模式，快速启动调试
- ✅ 生产环境根据规模选择微服务或单体部署
- ✅ 无需维护两套代码
