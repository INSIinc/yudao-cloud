# yudao-spring-boot-starter-monitor

## 📖 模块简介

`yudao-spring-boot-starter-monitor` 是芋道项目的服务监控 Spring Boot Starter，提供应用程序的可观测性能力，包括链路追踪、指标收集、健康检查等功能。

## ✨ 核心功能

### 1. 链路追踪（Tracing）
- **SkyWalking 集成**：集成 Apache SkyWalking 实现分布式链路追踪
- **TraceId 传递**：自动在 HTTP 响应头中添加 TraceId，便于日志关联和问题排查
- **业务链路标记**：通过 `@BizTrace` 注解标记业务关键节点
- **日志关联**：支持在日志中自动记录 TraceId

### 2. 指标收集（Metrics）
- **Prometheus 集成**：通过 Micrometer 支持 Prometheus 指标暴露
- **应用标签**：自动为所有指标添加应用名称标签
- **JVM 监控**：支持 JVM 内存、线程、GC 等指标收集

### 3. 应用监控（Admin）
- **Spring Boot Admin**：集成 Spring Boot Admin Client，支持应用注册到监控中心
- **健康检查**：支持健康端点暴露和监控

## 📦 依赖说明

### 核心依赖

```xml
<!-- 链路追踪 -->
<dependency>
    <groupId>org.apache.skywalking</groupId>
    <artifactId>apm-toolkit-trace</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.skywalking</groupId>
    <artifactId>apm-toolkit-logback-1.x</artifactId>
</dependency>

<!-- 指标收集 -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- 应用监控 -->
<dependency>
    <groupId>de.codecentric</groupId>
    <artifactId>spring-boot-admin-starter-client</artifactId>
</dependency>
```

## 🚀 快速开始

### 1. 添加依赖

在需要监控的服务模块的 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-monitor</artifactId>
</dependency>
```

### 2. 配置文件

在 `application.yml` 中添加配置：

```yaml
spring:
  application:
    name: your-service-name  # 应用名称，会自动添加到指标中

# 链路追踪配置
yudao:
  tracer:
    enable: true  # 启用链路追踪（默认 true）
  metrics:
    enable: true  # 启用指标收集（默认 true）

# Spring Boot Admin 配置（可选）
spring:
  boot:
    admin:
      client:
        url: http://localhost:9090  # Admin Server 地址
        instance:
          prefer-ip: true

# Actuator 端点配置（可选）
management:
  endpoints:
    web:
      exposure:
        include: '*'  # 暴露所有端点
  metrics:
    export:
      prometheus:
        enabled: true  # 启用 Prometheus 指标导出
```

### 3. SkyWalking Agent 配置

启动应用时添加 SkyWalking Agent：

```bash
java -javaagent:/path/to/skywalking-agent.jar \
     -Dskywalking.agent.service_name=your-service-name \
     -Dskywalking.collector.backend_service=localhost:11800 \
     -jar your-application.jar
```

## 📝 使用指南

### 业务链路追踪

使用 `@BizTrace` 注解标记重要的业务方法：

```java
@Service
public class OrderService {
    
    @BizTrace(
        id = "#orderId",          // 业务编号，支持 SpEL 表达式
        type = "order",           // 业务类型
        operationName = "创建订单" // 操作名称（可选）
    )
    public Order createOrder(Long orderId) {
        // 业务逻辑
    }
}
```

**注意**：使用 `@BizTrace` 前需要配置 SkyWalking OAP Server，在 `application.yml` 中添加：

```yaml
core:
  default:
    searchableTracesTags: ${SW_SEARCHABLE_TAG_KEYS:biz.id,biz.type}
```

### TraceId 获取

在代码中获取当前请求的 TraceId：

```java
import cn.iocoder.yudao.framework.tracer.core.util.TracerFrameworkUtils;

String traceId = TracerFrameworkUtils.getTraceId();
```

### 自定义指标

使用 Micrometer API 添加自定义指标：

```java
@Service
public class BusinessService {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    public void processOrder() {
        // 计数器
        meterRegistry.counter("order.processed", "type", "online").increment();
        
        // 计时器
        Timer.Sample sample = Timer.start(meterRegistry);
        // 业务逻辑
        sample.stop(meterRegistry.timer("order.process.time"));
    }
}
```

## 🔧 核心组件

### 自动配置类

| 配置类 | 说明 | 条件 |
|-------|------|------|
| `YudaoTracerAutoConfiguration` | 链路追踪自动配置 | 存在 SkyWalking 和 Servlet 类 |
| `YudaoMetricsAutoConfiguration` | 指标收集自动配置 | 存在 MeterRegistry 类 |

### 核心类

| 类名 | 说明 |
|------|------|
| `TraceFilter` | TraceId 过滤器，在响应头中添加 TraceId |
| `BizTrace` | 业务链路追踪注解 |
| `BizTraceAspect` | 业务链路追踪切面 |
| `TracerFrameworkUtils` | 链路追踪工具类 |

## 📊 监控端点

启用 Actuator 后，可访问以下端点：

| 端点 | 说明 | URL |
|------|------|-----|
| 健康检查 | 应用健康状态 | `/actuator/health` |
| Prometheus 指标 | Prometheus 格式的指标数据 | `/actuator/prometheus` |
| 应用信息 | 应用基本信息 | `/actuator/info` |
| 环境信息 | 应用环境配置 | `/actuator/env` |
| JVM 线程 | 线程栈信息 | `/actuator/threaddump` |
| JVM 堆转储 | 堆内存快照 | `/actuator/heapdump` |

## 🎯 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `yudao.tracer.enable` | Boolean | `true` | 是否启用链路追踪 |
| `yudao.metrics.enable` | Boolean | `true` | 是否启用指标收集 |

## 📚 参考文档

本模块提供了详细的入门教程：

- [《芋道 Spring Boot 监控工具 Admin 入门》](./《芋道%20Spring%20Boot%20监控工具%20Admin%20入门》.md)
- [《芋道 Spring Boot 监控端点 Actuator 入门》](./《芋道%20Spring%20Boot%20监控端点%20Actuator%20入门》.md)
- [《芋道 Spring Boot 链路追踪 SkyWalking 入门》](./《芋道%20Spring%20Boot%20链路追踪%20SkyWalking%20入门》.md)

## 🔍 故障排查

### TraceId 未显示在响应头

1. 检查是否正确添加了 SkyWalking Agent
2. 确认 `yudao.tracer.enable=true`
3. 检查是否存在 Servlet Filter

### Prometheus 端点无法访问

1. 确认依赖 `micrometer-registry-prometheus` 已添加
2. 检查 Actuator 端点是否已暴露
3. 确认 `yudao.metrics.enable=true`

### SkyWalking 链路未上报

1. 检查 SkyWalking Agent 配置是否正确
2. 确认 SkyWalking OAP Server 是否可访问
3. 查看应用日志中是否有 SkyWalking 相关错误

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request 来改进本模块。

## 📄 许可证

本项目采用 MIT 许可证，详见 [LICENSE](../../LICENSE) 文件。

