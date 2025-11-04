# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

芋道 yudao-cloud 是基于 Spring Cloud Alibaba 微服务架构的快速开发平台，包含系统功能、基础设施、会员中心、数据报表、工作流程、商城系统、微信公众号、CRM、ERP、AI 大模型、IoT 物联网等功能模块。

- **技术栈**: JDK 17 + Spring Boot 3.2+ + Spring Cloud Alibaba 2023.0.1
- **架构模式**: 微服务架构（支持单体部署）
- **工作流引擎**: Flowable 7.0.0
- **数据库**: MySQL 5.7/8.0+（支持 Oracle、PostgreSQL、达梦等国产数据库）
- **注册/配置中心**: Nacos 2.3.2
- **服务网关**: Spring Cloud Gateway 4.1.0
- **ORM 框架**: MyBatis Plus 3.5.7
- **缓存**: Redis 5.0/6.0 + Redisson 3.32.0

## 项目结构

### 主要模块

```
yudao-cloud/
├── yudao-dependencies/          # Maven 依赖版本管理
├── yudao-framework/             # 框架拓展封装（各种 starter）
├── yudao-gateway/               # Spring Cloud Gateway 网关服务
├── yudao-server/                # 单体部署的主项目（空壳容器）
└── yudao-module-*/              # 业务模块（微服务）
    ├── yudao-module-system/     # 系统功能模块（用户、角色、权限、租户等）
    ├── yudao-module-infra/      # 基础设施模块（配置、文件、定时任务等）
    ├── yudao-module-bpm/        # 工作流模块（基于 Flowable）
    ├── yudao-module-pay/        # 支付系统模块
    ├── yudao-module-member/     # 会员中心模块
    ├── yudao-module-report/     # 数据报表模块
    ├── yudao-module-mp/         # 微信公众号模块
    ├── yudao-module-mall/       # 商城系统模块
    ├── yudao-module-crm/        # CRM 系统模块
    ├── yudao-module-erp/        # ERP 系统模块
    ├── yudao-module-ai/         # AI 大模型模块
    └── yudao-module-iot/        # IoT 物联网模块
```

### 模块内部结构

每个业务模块（yudao-module-xxx）通常包含：
```
yudao-module-xxx/
├── pom.xml
├── yudao-module-xxx-api/       # API 接口定义（供其他模块 RPC 调用）
│   └── src/main/java/.../api/  # Feign 客户端接口
└── yudao-module-xxx-server/    # 服务端实现
    └── src/main/java/.../
        ├── controller/         # Controller 层（RESTful API）
        ├── service/           # Service 业务逻辑层
        ├── dal/              # Data Access Layer
        │   ├── dataobject/   # DO 数据库实体对象
        │   ├── mysql/        # MyBatis Mapper 接口
        │   └── redis/        # Redis DAO
        ├── convert/          # MapStruct 对象转换器
        └── framework/        # 框架配置（安全、RPC等）
```

## 架构设计

### 部署模式

项目支持两种部署模式：

1. **微服务模式**：
   - 使用 Nacos 作为注册中心和配置中心
   - 通过 yudao-gateway 网关统一路由
   - 各个 module-server 独立部署
   - 启用 OpenFeign 进行服务间调用

2. **单体模式**（默认）：
   - yudao-server 作为单体应用启动
   - 禁用 Nacos 注册发现和配置中心
   - 禁用 OpenFeign（模块间直接调用）
   - 在 pom.xml 中按需注释/取消注释模块依赖

### 三层架构

严格遵循三层架构模式：
- **Controller 层**：处理 HTTP 请求，参数校验，调用 Service
- **Service 层**：业务逻辑处理，事务控制
- **DAL 层**：数据访问层，包括 MyBatis Mapper 和 Redis DAO

对象转换使用 MapStruct，位于 `convert/` 包下。

### 多租户设计

- 基于 MyBatis Plus 拦截器实现透明化多租户
- 租户配置在 `yudao.tenant` 配置项
- 部分表和缓存可通过配置忽略租户隔离

### 权限控制

- 基于 Spring Security + Token + Redis
- 支持按钮级别权限控制（`@PreAuthorize` 注解）
- 支持数据权限（部门、用户等维度）

## 常用命令

### 编译构建

```bash
# 完整编译（在项目根目录）
mvn clean install -DskipTests

# 只编译某个模块
cd yudao-module-bpm
mvn clean install -DskipTests

# 打包单体应用
cd yudao-server
mvn clean package -DskipTests
```

### 运行服务

```bash
# 方式 1：IDEA 直接运行
# 单体模式：运行 YudaoServerApplication.java
# 微服务模式：分别运行各个 XxxServerApplication.java

# 方式 2：Maven 命令运行（开发环境）
cd yudao-server
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 方式 3：JAR 包运行
java -jar yudao-server.jar --spring.profiles.active=local
```

### 测试

```bash
# 运行所有单元测试
mvn test

# 运行指定模块的测试
cd yudao-module-bpm
mvn test

# 运行单个测试类
mvn test -Dtest=XxxTest

# 运行单个测试方法
mvn test -Dtest=XxxTest#testMethod
```

### 代码格式化

项目使用 Lombok 消除冗余代码，使用 MapStruct 进行对象转换。编译时需要特别注意注解处理器配置。

## 环境配置

### 配置文件

- `application.yaml`：主配置文件（通用配置）
- `application-local.yaml`：本地开发环境配置
- `application-dev.yaml`：开发环境配置

### 环境切换

通过 `spring.profiles.active` 切换环境：
```yaml
spring:
  profiles:
    active: local  # 可选: local, dev, prod
```

### 必需的外部依赖

1. **MySQL 数据库**：
   - 执行 `sql/mysql/ruoyi-vue-pro.sql` 初始化数据库
   - 配置数据源在 `application-local.yaml`

2. **Redis**：
   - 本地启动 Redis 服务
   - 默认连接 localhost:6379

3. **Nacos**（仅微服务模式）：
   - 下载并启动 Nacos Server
   - 配置 nacos.server-addr

## 开发指南

### 添加新的业务模块

1. 在 yudao-server/pom.xml 中添加模块依赖
2. 确保模块包扫描路径包含新模块（默认扫描 `cn.iocoder.yudao.module.*`）
3. 配置模块相关的配置项

### 代码生成器

使用内置的代码生成器快速生成 CRUD 代码：
- 访问"基础设施 > 代码生成"菜单
- 导入数据库表，配置生成选项
- 一键生成 Controller、Service、Mapper、DO、VO 等代码

### 工作流开发

基于 Flowable 7.0.0：
- 流程定义使用 BPMN 2.0 标准
- 支持仿钉钉/飞书的 Simple 设计器
- 流程表单使用动态表单配置
- 详细文档：https://doc.iocoder.cn/bpm/

### API 接口文档

开发时访问 Swagger UI：
- 单体模式：http://localhost:48080/swagger-ui.html（需先启动 yudao-server）
- 微服务模式：http://localhost:48080/doc.html（Gateway 聚合文档）

### 数据库多租户开发

- 新表默认继承多租户，需包含 `tenant_id` 字段
- 忽略租户的表在 `yudao.tenant.ignore-tables` 中配置
- Service 层使用 `@TenantIgnore` 注解忽略租户过滤

### RPC 调用

模块间调用使用 Feign Client：
```java
// 在 xxx-api 模块定义接口
@FeignClient(name = "system-server")
public interface UserApi {
    // ...
}

// 在其他模块中注入使用
@Resource
private UserApi userApi;
```

## 注意事项

### 编译相关

1. **Lombok + MapStruct 组合**：
   - maven-compiler-plugin 已配置注解处理器
   - 确保 Lombok 版本与 MapStruct 兼容

2. **循环依赖**：
   - 项目允许循环依赖（三层架构特点）
   - `spring.main.allow-circular-references=true`

3. **模块裁剪**：
   - yudao-server/pom.xml 中大部分模块默认注释
   - 根据需要取消注释以启用相应功能

### 数据库相关

1. **ID 生成策略**：
   - 默认使用 "智能" 模式（NONE）
   - 自动根据数据库类型适配 AUTO 或 INPUT

2. **逻辑删除**：
   - 已删除：`deleted = 1`
   - 未删除：`deleted = 0`

3. **数据加密**：
   - 敏感字段支持加密存储
   - 加密秘钥配置：`mybatis-plus.encryptor.password`

### 安全相关

1. **API 加密**：
   - 支持 AES/RSA 加密
   - 配置：`yudao.api-encrypt.enable`

2. **XSS 防护**：
   - 默认关闭，按需启用
   - 配置：`yudao.xss.enable`

3. **验证码**：
   - 支持滑块拼图、文字点选等类型
   - 使用 Redis 缓存

### 性能相关

1. **VO 翻译**：
   - EasyTrans 全局翻译默认禁用（性能考虑）
   - 按需启用：`easy-trans.is-enable-global=true`

2. **Redis Repository**：
   - Spring Data Redis Repository 已禁用
   - 直接使用 RedisTemplate

## 前端对接

- **Vue3 + Element Plus**：https://gitee.com/yudaocode/yudao-ui-admin-vue3
- **Vue3 + Vben (AntD)**：https://gitee.com/yudaocode/yudao-ui-admin-vben
- **Vue2 + Element UI**：https://gitee.com/zhijiantianya/ruoyi-vue-pro

API 路由规则：
- 管理后台：`/admin-api/{module}/**`
- 用户 APP：`/app-api/{module}/**`

## 问题排查

启动问题请参考官方文档：https://doc.iocoder.cn/quick-start/

常见问题：
1. 数据库连接失败 → 检查 MySQL 服务和配置
2. Redis 连接失败 → 检查 Redis 服务
3. 端口冲突 → 修改 server.port 配置
4. Nacos 连接失败（微服务模式）→ 检查 Nacos 服务和配置
