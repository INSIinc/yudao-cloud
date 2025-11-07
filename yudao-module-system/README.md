# yudao-module-system

## 📖 模块简介

**yudao-module-system** 是芋道项目的系统管理模块，提供通用业务功能，支撑上层的核心业务。

该模块包含了系统运行所需的基础功能，如用户管理、权限控制、部门管理、数据字典、日志记录等核心功能。

## 📁 模块结构

```
yudao-module-system
├── yudao-module-system-api          # API 模块，暴露给其它模块调用
│   ├── src/main/java
│   │   └── cn/iocoder/yudao/module/system
│   │       ├── api/                 # Feign API 接口定义
│   │       │   ├── dept/           # 部门 API
│   │       │   ├── dict/           # 字典 API
│   │       │   ├── logger/         # 日志 API
│   │       │   ├── mail/           # 邮件 API
│   │       │   ├── notify/         # 通知 API
│   │       │   ├── permission/     # 权限 API
│   │       │   ├── sms/            # 短信 API
│   │       │   ├── social/         # 社交 API
│   │       │   └── user/           # 用户 API
│   │       └── enums/              # 枚举定义
│   └── pom.xml
└── yudao-module-system-server       # 服务实现模块
    ├── src/main/java
    │   └── cn/iocoder/yudao/module/system
    │       ├── SystemServerApplication.java  # 启动类
    │       ├── controller/         # 控制器层
    │       │   ├── admin/          # 管理后台接口
    │       │   └── app/            # 移动端接口
    │       ├── service/            # 业务逻辑层
    │       ├── dal/                # 数据访问层
    │       ├── convert/            # 对象转换层
    │       ├── api/                # API 实现
    │       ├── framework/          # 框架配置
    │       ├── job/                # 定时任务
    │       ├── mq/                 # 消息队列
    │       └── util/               # 工具类
    ├── src/main/resources
    ├── Dockerfile
    └── pom.xml
```

## 🎯 核心功能

### 1. 用户管理 (User)
- 用户信息的增删改查
- 用户状态管理（启用/禁用）
- 用户密码重置
- 用户个人中心
- 用户头像上传
- 用户导入导出

### 2. 权限管理 (Permission)
- **角色管理**：角色的创建、编辑、删除、权限分配
- **菜单管理**：系统菜单、按钮权限的配置
- **权限控制**：基于 RBAC 的权限控制模型

### 3. 组织架构 (Dept & Post)
- **部门管理**：树形结构的部门管理
- **岗位管理**：岗位的增删改查

### 4. 数据字典 (Dict)
- 字典类型管理
- 字典数据管理
- 支持前端动态获取字典数据

### 5. 认证授权 (Auth & OAuth2)
- 用户登录认证
- 验证码校验
- 短信验证码登录
- 社交登录（微信、QQ、钉钉等）
- OAuth2.0 授权（客户端、令牌管理）
- 第三方用户绑定

### 6. 租户管理 (Tenant)
- 多租户支持
- 租户套餐管理
- 租户信息管理
- 数据隔离

### 7. 短信服务 (SMS)
- 短信渠道配置
- 短信模板管理
- 短信发送记录
- 短信回调处理

### 8. 邮件服务 (Mail)
- 邮箱账号配置
- 邮件模板管理
- 邮件发送记录

### 9. 站内通知 (Notify)
- 通知模板管理
- 站内消息发送
- 消息记录查询

### 10. 通知公告 (Notice)
- 公告发布管理
- 公告查看

### 11. 日志管理 (Logger)
- **登录日志**：记录用户登录、登出日志
- **操作日志**：记录用户操作行为日志

### 12. 其他功能
- **验证码**：图形验证码生成
- **IP 地址**：IP 归属地查询
- **地区管理**：省市区数据管理

## 🔌 API 接口分类

### 管理后台接口 (Admin)
位于 `controller/admin` 包下，提供给管理后台使用的接口：

- `/admin-api/system/auth/**` - 认证相关
- `/admin-api/system/user/**` - 用户管理
- `/admin-api/system/role/**` - 角色管理
- `/admin-api/system/menu/**` - 菜单管理
- `/admin-api/system/permission/**` - 权限管理
- `/admin-api/system/dept/**` - 部门管理
- `/admin-api/system/post/**` - 岗位管理
- `/admin-api/system/dict/**` - 字典管理
- `/admin-api/system/tenant/**` - 租户管理
- `/admin-api/system/sms/**` - 短信管理
- `/admin-api/system/mail/**` - 邮件管理
- `/admin-api/system/notify/**` - 通知管理
- `/admin-api/system/notice/**` - 公告管理
- `/admin-api/system/logger/**` - 日志管理
- `/admin-api/system/oauth2/**` - OAuth2.0 管理
- `/admin-api/system/social/**` - 社交登录管理

### 移动端接口 (App)
位于 `controller/app` 包下，提供给移动端使用的接口：

- `/app-api/system/dict/**` - 字典查询
- `/app-api/system/area/**` - 地区查询
- `/app-api/system/tenant/**` - 租户查询

## 🔧 技术栈

- **基础框架**：Spring Boot 3.x / Spring Cloud
- **数据访问**：MyBatis-Plus
- **安全框架**���Spring Security
- **缓存**：Redis
- **消息队列**：RocketMQ / Kafka / RabbitMQ（可选）
- **API 文档**：Knife4j (Swagger)
- **对象转换**：MapStruct
- **工具库**：Hutool、Lombok

## 📦 依赖关系

```xml
<!-- API 模块 -->
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-module-system-api</artifactId>
</dependency>
```

其他业务模块可以通过引入 `yudao-module-system-api` 来调用系统模块提供的服务。

## 🚀 快速开始

### 1. 数据库初始化

执行 SQL 脚本：
```bash
# MySQL
/sql/mysql/system.sql
```

### 2. 配置文件

修改 `application.yaml` 配置：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ruoyi-vue-pro
    username: root
    password: 123456
  
  redis:
    host: localhost
    port: 6379
```

### 3. 启动服务

运行 `SystemServerApplication` 启动类：
```bash
mvn spring-boot:run
```

或使用 IDE 直接运行 `SystemServerApplication.java`

### 4. 访问接口文档

启动成功后，访问 Swagger 文档：
```
http://localhost:48081/doc.html
```

## 📝 核心配置说明

### 租户配置
```yaml
yudao:
  tenant:
    enable: true  # 是否开启多租户
    ignore-urls:  # 忽略租户的 URL
      - /admin-api/system/tenant/**
```

### 验证码配置
```yaml
yudao:
  captcha:
    enable: true  # 是否开启验证码
    timeout: 5    # 验证码过期时间（分钟）
```

### OAuth2 配置
```yaml
yudao:
  oauth2:
    authorization-code-timeout: 5m  # 授权码过期时间
    access-token-timeout: 30d       # 访问令牌过期时间
    refresh-token-timeout: 30d      # 刷新令牌过期时间
```

## 🔐 权限设计

### RBAC 权限模型
系统采用基于角色的访问控制（RBAC）模型：

```
用户 (User) → 角色 (Role) → 菜单/权限 (Menu/Permission)
```

- 一个用户可以拥有多个角色
- 一个角色可以拥有多个菜单权限
- 支持按钮级别的细粒度权限控制

### 数据权限
支持多种数据权限范围：
- 全部数据权限
- 指定部门数据权限
- 部门及以下数据权限
- 仅本人数据权限
- 自定义数据权限

## 🌐 多租户设计

系统支持 SaaS 多租户架构：

- **租户隔离**：基于租户 ID 的数据隔离
- **租户套餐**：不同租户可以配置不同的功能套餐
- **自动过滤**：通过 MyBatis 拦截器自动添加租户条件

## 📞 联系方式

- 项目地址：https://gitee.com/zhijiantianya/yudao-cloud
- 官方文档：https://cloud.iocoder.cn
- 视频教程：https://cloud.iocoder.cn/video/

## 📄 许可证

本项目采用 MIT 许可证，详见 [LICENSE](../../LICENSE) 文件。

---

**注意**：本模块是芋道项目的核心基础模块，其他业务模块都依赖于此模块提供的用户、权限、租户等基础服务。

