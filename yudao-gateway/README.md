# yudao-gateway

## 📖 模块简介

`yudao-gateway` 是芋道云平台的 **API 服务网关模块**，基于 **Spring Cloud Gateway** 实现，作为整个微服务架构的统一入口，负责请求路由、负载均衡、权限校验、日志记录等核心功能。

## ✨ 核心特性

### 🔀 路由转发
- **动态路由配置**：支持通过配置文件或 Nacos 动态配置路由规则
- **路径重写**：自动将 API 文档路径重写，保证 Swagger 文档正常访问
- **多服务支持**：统一管理所有微服务的路由规则

### 🎯 负载均衡
- **灰度发布支持**：通过 `grayLb://` 协议支持灰度发布功能
- **服务发现**：基于 Nacos 实现服务自动发现和负载均衡
- **智能路由**：根据请求特征智能选择后端服务实例

### 🔐 安全认证
- **Token 认证**：统一的 Token 校验机制
- **权限控制**：在网关层进行权限预校验
- **CORS 处理**：统一的跨域资源共享配置

### 📝 日志记录
- **访问日志**：记录所有通过网关的请求和响应信息
- **链路追踪**：支持分布式链路追踪
- **性能监控**：集成监控指标收集

### 🛡️ 容错保护
- **全局异常处理**：统一的异常处理和错误响应格式
- **熔断降级**：防止服务雪崩
- **限流控制**：保护后端服务不被过载

## 🏗️ 技术架构

### 核心依赖

```xml
<!-- Spring Cloud Gateway -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
</dependency>

<!-- Nacos 服务发现 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>

<!-- Nacos 配置中心 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>

<!-- Knife4j 接口文档 -->
<dependency>
    <groupId>com.github.xiaoymin</groupId>
    <artifactId>knife4j-gateway-spring-boot-starter</artifactId>
</dependency>
```

### 项目结构

```
yudao-gateway/
├── src/main/java/cn/iocoder/yudao/gateway/
│   ├── GatewayServerApplication.java    # 应用启动类
│   ├── filter/                          # 过滤器
│   │   ├── cors/                        # CORS 跨域处理
│   │   ├── grey/                        # 灰度发布
│   │   ├── logging/                     # 访问日志
│   │   └── security/                    # 安全认证
│   ├── handler/                         # 处理器
│   │   └── GlobalExceptionHandler.java  # 全局异常处理
│   ├── jackson/                         # Jackson 配置
│   ├── route/                           # 路由配置
│   └── util/                            # 工具类
├── src/main/resources/
│   ├── application.yaml                 # 应用配置
│   ├── application-dev.yaml             # 开发环境配置
│   ├── application-local.yaml           # 本地环境配置
│   ├── banner.txt                       # 启动 Banner
│   └── logback-spring.xml               # 日志配置
├── Dockerfile                           # Docker 镜像构建文件
└── pom.xml                              # Maven 配置文件
```

## 🚀 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- Nacos 2.x（用于服务注册与配置管理）

### 本地启动

1. **启动 Nacos 服务**
   ```bash
   # 请确保 Nacos 已启动并可访问
   ```

2. **修改配置文件**
   
   编辑 `application-local.yaml`，配置 Nacos 地址等信息

3. **启动网关服务**
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

4. **访问服务**
   - 网关地址：http://localhost:48080
   - API 文档：http://localhost:48080/doc.html

### Docker 部署

```bash
# 构建镜像
mvn clean package
docker build -t yudao-gateway:latest .

# 运行容器
docker run -d \
  -p 48080:48080 \
  -e JAVA_OPTS="-Xms512m -Xmx512m" \
  --name yudao-gateway \
  yudao-gateway:latest
```

## 🔧 路由配置

### 路由规则示例

```yaml
spring:
  cloud:
    gateway:
      routes:
        # System 模块
        - id: system-admin-api
          uri: grayLb://system-server
          predicates:
            - Path=/admin-api/system/**
          filters:
            - RewritePath=/admin-api/system/v3/api-docs, /v3/api-docs
        
        # Infra 模块
        - id: infra-admin-api
          uri: grayLb://infra-server
          predicates:
            - Path=/admin-api/infra/**
          filters:
            - RewritePath=/admin-api/infra/v3/api-docs, /v3/api-docs
```

### 支持的微服务

网关目前支持以下微服务的路由转发：

| 服务名称 | API 前缀 | 说明 |
|---------|---------|------|
| system-server | /admin-api/system、/app-api/system | 系统管理 |
| infra-server | /admin-api/infra、/app-api/infra | 基础设施 |
| member-server | /admin-api/member、/app-api/member | 会员中心 |
| bpm-server | /admin-api/bpm | 工作流 |
| report-server | /admin-api/report | 数据报表 |
| pay-server | /admin-api/pay、/app-api/pay | 支付模块 |
| mp-server | /admin-api/mp | 微信公众号 |
| product-server | /admin-api/product、/app-api/product | 商品管理 |
| promotion-server | /admin-api/promotion、/app-api/promotion | 营销活动 |
| trade-server | /admin-api/trade、/app-api/trade | 交易订单 |
| statistics-server | /admin-api/statistics | 统计分析 |
| erp-server | /admin-api/erp | ERP 管理 |
| crm-server | /admin-api/crm | CRM 管理 |
| ai-server | /admin-api/ai | AI 服务 |
| iot-server | /admin-api/iot | 物联网 |

## 📊 监控管理

### 健康检查

```bash
curl http://localhost:48080/actuator/health
```

### 查看路由信息

```bash
curl http://localhost:48080/actuator/gateway/routes
```

### 性能指标

网关集成了 Spring Boot Actuator，可以通过以下端点查看性能指标：
- `/actuator/metrics` - 查看各项指标
- `/actuator/prometheus` - Prometheus 格式的指标

## 🔍 过滤器说明

### 内置过滤器

| 过滤器 | 功能 | 说明 |
|-------|------|------|
| CorsFilter | 跨域处理 | 统一处理跨域请求 |
| TokenAuthenticationFilter | Token 认证 | 验证请求的 Token 有效性 |
| AccessLogFilter | 访问日志 | 记录请求和响应信息 |
| GrayLoadBalancer | 灰度路由 | 支持灰度发布和流量控制 |

## 🐛 故障排查

### 常见问题

1. **服务无法访问**
   - 检查 Nacos 服务是否正常
   - 确认后端服务是否已注册到 Nacos
   - 查看网关日志是否有错误信息

2. **路由转发失败**
   - 检查路由配置是否正确
   - 确认服务名称是否与 Nacos 注册名称一致
   - 验证断言（Predicates）配置是否匹配请求路径

3. **性能问题**
   - 调整 JVM 参数（-Xms、-Xmx）
   - 检查后端服务响应时间
   - 查看网关连接池配置

## 📝 配置说明

### 核心配置项

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          # 调整缓冲区大小
          max-in-memory-size: 10MB
      
      # 禁用 X-Forwarded 前缀（避免 Swagger 路径重复）
      x-forwarded:
        prefix-enabled: false

server:
  port: 48080
```

## 📚 相关文档

- [Spring Cloud Gateway 官方文档](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
- [芋道云开发文档](https://cloud.iocoder.cn)
- [Nacos 官方文档](https://nacos.io/zh-cn/docs/quick-start.html)

## 🤝 参与贡献

欢迎提交 Issue 或 Pull Request 来改进本模块！

## 📄 开源协议

本项目采用 MIT 开源协议，详见 [LICENSE](../LICENSE) 文件。

---

💡 **提示**：更多信息请参考[芋道云开发文档](https://cloud.iocoder.cn)

