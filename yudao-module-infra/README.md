# Infra 基础设施模块

## 📖 模块简介

`yudao-module-infra` 是芋道项目的基础设施模块，主要提供两大核心能力：

1. **基础设施运维与管理**：支撑上层业务的通用基础能力
   - 定时任务管理
   - 服务器监控
   - 数据源管理
   - Redis 监控
   
2. **研发效能工具**：提升研发效率与质量
   - 代码生成器
   - API 日志管理
   - 文件存储管理
   - 系统配置管理

## 🏗️ 模块结构

```
yudao-module-infra
├── yudao-module-infra-api          # API 模块（供其他模块依赖）
│   ├── api/                        # Feign 接口定义
│   │   ├── config/                 # 配置服务 API
│   │   ├── file/                   # 文件服务 API
│   │   └── websocket/              # WebSocket 服务 API
│   └── enums/                      # 枚举定义
│       ├── codegen/                # 代码生成相关枚举
│       ├── config/                 # 配置相关枚举
│       └── logger/                 # 日志相关枚举
│
└── yudao-module-infra-server       # 服务端模块（实现具体业务逻辑）
    ├── controller/                 # 控制器层
    │   ├── admin/                  # 管理后台接口
    │   │   ├── codegen/            # 代码生成
    │   │   ├── config/             # 参数配置
    │   │   ├── db/                 # 数据源管理
    │   │   ├── file/               # 文件管理
    │   │   ├── logger/             # 日志管理
    │   │   ├── redis/              # Redis 监控
    │   │   └── demo/               # 示例代码
    │   └── app/                    # 移动端接口
    ├── service/                    # 业务逻辑层
    ├── dal/                        # 数据访问层
    ├── job/                        # 定时任务
    ├── mq/                         # 消息队列
    └── websocket/                  # WebSocket 实现
```

## ✨ 核心功能

### 1. 代码生成器（Codegen）

强大的代码生成工具，支持从数据库表快速生成完整的 CRUD 代码。

**功能特性**：
- 📋 支持多种数据库（MySQL、PostgreSQL、Oracle、SQL Server 等）
- 🎨 支持多种前端框架（Vue2、Vue3）
- 🔧 支持自定义模板
- 📦 支持主子表生成
- 🌲 支持树形结构生成

**核心枚举**：
- `CodegenSceneEnum`: 代码生成场景（单表、主子表、树表）
- `CodegenTemplateTypeEnum`: 模板类型（标准、树形、主子表）
- `CodegenFrontTypeEnum`: 前端类型（Vue2、Vue3）

### 2. 文件管理（File）

统一的文件存储管理服务，支持多种存储方式。

**功能特性**：
- 📁 支持本地存储
- ☁️ 支持云存储（阿里云 OSS、腾讯云 COS、七牛云、MinIO 等）
- 🔐 支持预签名 URL
- 📊 文件管理与监控

**API 接口**：
- `FileApi`: 文件服务 API（供其他模块调用）
- `FileCreateReqDTO`: 文件创建请求 DTO

### 3. 参数配置（Config）

灵活的系统参数配置管理。

**功能特性**：
- ⚙️ 系统参数动态配置
- 🔒 区分系统内置与自定义配置
- 👁️ 支持可见性控制
- 🔄 配置实时生效

**API 接口**：
- `ConfigApi`: 配置服务 API（供其他模块调用）

**核心枚举**：
- `ConfigTypeEnum`: 配置类型（系统内置、自定义）

### 4. API 日志管理（Logger）

完善的 API 访问与错误日志管理。

**功能特性**：
- 📝 API 访问日志记录
- ❌ API 错误日志记录
- 🔍 日志查询与分析
- ✅ 错误日志处理状态管理

**核心枚举**：
- `ApiErrorLogProcessStatusEnum`: API 错误日志处理状态（未处理、已处理、已忽略）

### 5. 数据源管理（DB）

多数据源动态管理。

**功能特性**：
- 🗄️ 多数据源配置
- 🔌 数据源动态切换
- 📊 数据库表信息查询
- ✅ 数据源连接测试

### 6. Redis 监控（Redis）

Redis 服务监控与管理。

**功能特性**：
- 📊 Redis 服务器信息监控
- 🔍 缓存键值查询
- 🗑️ 缓存清理

### 7. WebSocket 服务（WebSocket）

统一的 WebSocket 消息推送服务。

**API 接口**：
- `WebSocketSenderApi`: WebSocket 消息发送 API
- `WebSocketSendReqDTO`: WebSocket 消息发送请求 DTO

## 📦 依赖说明

### API 模块依赖

其他模块如需调用 Infra 服务，需在 `pom.xml` 中引入：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-module-infra-api</artifactId>
    <version>${revision}</version>
</dependency>
```

### 错误码范围

Infra 模块使用错误码段：`1-001-000-000`

主要错误码分类：
- `1-001-000-xxx`: 参数配置相关
- `1-001-001-xxx`: 定时任务相关
- `1-001-002-xxx`: API 错误日志相关
- `1-001-003-xxx`: 数据源相关
- `1-001-004-xxx`: 文件管理相关
- `1-001-005-xxx`: 代码生成相关

## 🚀 快速开始

### 1. 启动服务

```bash
# 进入服务端模块目录
cd yudao-module-infra/yudao-module-infra-server

# 运行启动类
# cn.iocoder.yudao.module.infra.InfraServerApplication
```

### 2. 访问管理后台

启动后，通过网关访问以下功能：

- 代码生成：系统管理 -> 研发工具 -> 代码生成
- 文件管理：基础设施 -> 文件管理
- 参数配置：系统管理 -> 参数配置
- API 日志：系统管理 -> 日志管理
- 数据源管理：基础设施 -> 数据源管理

## 🔧 配置说明

### 文件存储配置

在 `application.yml` 中配置文件存储方式：

```yaml
yudao:
  file:
    # 文件存储类型：local-本地存储、db-数据库存储、ftp-FTP存储
    # s3-S3存储（阿里云、腾讯云、七牛云、MinIO等）
    type: local
```

### 代码生成配置

代码生成器支持的数据库类型：
- MySQL
- PostgreSQL
- Oracle
- SQL Server
- DM（达梦）
- KingBase（人大金仓）
- OpenGauss

## 📝 常见问题

### Q1: 如何使用代码生成器？

1. 配置数据源（基础设施 -> 数据源管理）
2. 导入数据表（研发工具 -> 代码生成 -> 导入）
3. 配置生成选项（表名、类名、模板等）
4. 预览并下载代码

### Q2: 如何切换文件存储方式？

在文件配置管理中新增存储配置，并设置为主配置即可。支持热切换，无需重启服务。

### Q3: API 日志如何清理？

- API 访问日志：可配置自动清理策略，定期清理历史日志
- API 错误日志：处理完成后可手动删除

## 🤝 参与贡献

欢迎提交 Issue 或 Pull Request！

## 📄 开源协议

本项目采用 MIT 协议，完全开源免费。

## 🔗 相关链接

- 项目官网：https://cloud.iocoder.cn
- 在线演示：http://dashboard-vue3.yudao.iocoder.cn
- 开发文档：https://cloud.iocoder.cn/quick-start/
- 视频教程：https://cloud.iocoder.cn/video/

