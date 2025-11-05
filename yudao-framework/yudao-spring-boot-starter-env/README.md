# yudao-spring-boot-starter-env

## 📖 模块简介

多环境隔离组件，实现类似阿里云的「特性环境」能力。支持在微服务架构中，通过环境标签（Tag）实现不同环境之间的服务隔离和路由，适用于开发、测试等场景。

**核心能力：**
- 🏷️ 基于环境标签的服务隔离
- 🔄 支持请求链路的环境标签透传
- 🎯 智能负载均衡：优先路由到相同标签的服务实例
- 🌐 支持 Web 和 RPC 调用链路

## 🎯 使用场景

### 典型场景示例

1. **开发环境隔离**：多个开发者各自启动带有不同 tag 的服务实例，互不影响
2. **特性分支测试**：为特定功能分支打上独立标签，进行端到端测试
3. **灰度发布**：通过 tag 标识灰度版本，实现精准流量控制
4. **本地调试**：本地服务实例打上个人标签，连接远程环境其他服务

### 工作原理图

```
客户端请求 (Header: tag=dev1)
    ↓
Gateway (tag=dev1) 
    ↓
Service A (tag=dev1) → 调用 → Service B (优先选择 tag=dev1)
    ↓                              ↓
Service C (tag=dev1)        Service B (tag=dev1) ✓
                            Service B (无tag) ✗
```

## 🚀 快速开始

### 1. 添加依赖

在需要使用多环境隔离的模块中引入：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-env</artifactId>
</dependency>
```

### 2. 配置环境标签

在 `application.yml` 中配置：

```yaml
yudao:
  env:
    tag: dev1  # 设置当前服务实例的环境标签
```

**动态配置支持：**

使用主机名作为标签（适合容器化部署）：

```yaml
yudao:
  env:
    tag: ${HOSTNAME}  # 自动使用主机名作为标签
```

### 3. 发起带标签的请求

#### HTTP 请求

在 HTTP 请求头中添加 `tag` 参数：

```http
GET /api/users/list
Host: gateway.example.com
tag: dev1
```

#### 使用 IDEA HTTP Client

```http
### 请求示例
GET http://localhost:8080/api/users/list
tag: ${HOSTNAME}
```

## 🔧 核心组件说明

### 1. Web 组件

**EnvWebFilter**
- 从 HTTP 请求头中提取 `tag` 参数
- 将标签设置到 `EnvContextHolder` 上下文中
- 自动在请求结束后清理上下文

### 2. RPC 组件

**EnvRequestInterceptor**
- Feign 请求拦截器
- 自动将上下文中的 tag 添加到下游请求头
- 实现环境标签的链路透传

**EnvLoadBalancerClient**
- 自定义负载均衡客户端
- 优先选择与当前 tag 匹配的服务实例
- 无 tag 时过滤掉有 tag 的实例，避免误调用

### 3. 上下文管理

**EnvContextHolder**
- 基于 `TransmittableThreadLocal` 实现
- 支持异步线程的上下文传递
- 支持多层设置和清理

### 4. 自动配置

**EnvEnvironmentPostProcessor**
- 自动将 `yudao.env.tag` 同步到 Nacos 等注册中心的 metadata
- 支持 `${HOSTNAME}` 占位符的自动解析

## 📋 配置说明

### 配置项

| 配置项 | 说明 | 默认值 | 示例 |
|--------|------|--------|------|
| `yudao.env.tag` | 环境标签 | 无 | `dev1`, `test`, `${HOSTNAME}` |

### 自动同步配置

配置 `yudao.env.tag` 后，会自动同步到以下配置项（如未手动配置）：

- `spring.cloud.nacos.discovery.metadata.tag`：Nacos 服务注册元数据

## 📝 最佳实践

### 1. 开发环境配置建议

```yaml
# application-local.yml
yudao:
  env:
    tag: ${HOSTNAME}  # 使用主机名，确保每个开发者实例唯一

spring:
  cloud:
    nacos:
      discovery:
        register-enabled: true  # 注册到 Nacos
```

### 2. 生产环境配置

```yaml
# application-prod.yml
yudao:
  env:
    tag:  # 不配置 tag，表示稳定环境
```

### 3. 灰度发布配置

```yaml
# 灰度实例配置
yudao:
  env:
    tag: gray-v2  # 灰度版本标签
```

### 4. 注意事项

⚠️ **重要提示：**

1. **标签一致性**：确保调用链路中的服务使用相同的 tag 值
2. **Nacos 配置**：确保 Nacos 已正确配置服务注册
3. **负载均衡**：当没有匹配 tag 的实例时，会降级到无 tag 的实例
4. **性能影响**：增加了负载均衡的判断逻辑，对性能影响极小

## 🏗️ 架构设计

### 模块结构

```
yudao-spring-boot-starter-env
├── config/                          # 配置类
│   ├── EnvProperties               # 环境配置属性
│   ├── EnvEnvironmentPostProcessor # 环境后置处理器
│   ├── YudaoEnvWebAutoConfiguration # Web 自动配置
│   └── YudaoEnvRpcAutoConfiguration # RPC 自动配置
├── core/
│   ├── context/
│   │   └── EnvContextHolder        # 环境上下文持有者
│   ├── web/
│   │   └── EnvWebFilter            # Web 过滤器
│   ├── feign/
│   │   ├── EnvRequestInterceptor   # Feign 请求拦截器
│   │   ├── EnvLoadBalancerClient   # 负载均衡客户端
│   │   └── EnvLoadBalancerClientFactory # 负载均衡客户端工厂
│   └── util/
│       └── EnvUtils                # 环境工具类
```

### 技术栈

- Spring Boot 自动配置
- Spring Cloud LoadBalancer
- OpenFeign
- Alibaba Nacos
- TransmittableThreadLocal（TTL）

## 🔗 参考资料

- [阿里云微服务引擎-特性环境](https://segmentfault.com/a/1190000018022987)
- [Spring Cloud LoadBalancer 官方文档](https://spring.io/guides/gs/spring-cloud-loadbalancer/)

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](../../LICENSE) 文件

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

**作者：** 芋道源码  
**项目地址：** https://github.com/YunaiV/ruoyi-vue-pro

