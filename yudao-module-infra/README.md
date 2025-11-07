# yudao-module-infra - 基础设施模块

## 模块概述

`yudao-module-infra` 是芋道 yudao-cloud 微服务架构中的基础设施模块，主要提供两大核心能力：

1. **基础设施运维与管理**：支撑上层通用与核心业务的基础设施服务
   - 配置参数管理
   - 文件存储服务
   - API 日志记录
   - 数据库管理
   - Redis 监控
   - 定时任务管理
   - WebSocket 消息推送

2. **研发工具支持**：提升研发效率与质量的开发工具
   - 代码生成器（支持多种前端框架）
   - API 接口文档
   - 数据库文档

## 主要功能

### 1. 代码生成器（Codegen）
- 基于数据库表结构自动生成 CRUD 代码
- 支持后端代码生成：Controller、Service、Mapper、DO、VO 等
- 支持多种前端框架模板：
  - Vue2 + Element UI
  - Vue3 + Element Plus
  - Vue3 + Vben (Ant Design)
  - Vue3 + Vben5 (Ant Design)
  - Vue3 + Vben5 (Element Plus)
- 支持 SQL 脚本生成
- 可自定义代码模板

### 2. 配置参数管理（Config）
- 系统参数配置的增删改查
- 参数键值对管理
- 支持通过 RPC 接口供其他模块调用
- 配置项动态刷新

### 3. 文件服务（File）
- 文件上传、下载、删除
- 支持多种存储方式：
  - 本地文件系统
  - 阿里云 OSS
  - 腾讯云 COS
  - 七牛云
  - MinIO
  - S3 兼容存储
- 文件访问权限控制
- 预签名 URL 生成
- 文件配置管理（存储器配置）

### 4. 日志管理（Logger）
- **API 访问日志**：记录所有 API 请求信息
  - 请求路径、方法、参数
  - 响应结果、耗时
  - 用户信息、IP 地址
- **API 错误日志**：记录系统异常信息
  - 异常堆栈信息
  - 错误分类统计
  - 错误处理状态
- 支持日志查询、导出
- 定时清理过期日志

### 5. 数据库管理（DB）
- 数据源配置管理
- 支持动态添加数据源
- 数据库表信息查询
- 数据库字段信息获取
- 用于代码生成器的数据库元数据服务

### 6. Redis 管理
- Redis 缓存监控
- 缓存键值查询
- 缓存清理操作
- Redis 运行状态监控

### 7. WebSocket 服务
- WebSocket 消息推送
- 支持点对点消息发送
- 支持广播消息
- 提供 RPC 接口供其他模块调用

### 8. 定时任务（Job）
- 基于 Quartz 的定时任务管理
- 定时清理 API 日志（过期数据清理）

### 9. 示例模块（Demo）
- 学生管理 CRUD 示例
- 演示标准的三层架构开发模式

## 技术栈

- **Spring Boot 3.2+**：核心框架
- **Spring Cloud Alibaba 2023.0.1**：微服务框架
- **MyBatis Plus 3.5.7**：ORM 框架
- **Redis**：缓存和消息队列
- **Quartz**：定时任务调度
- **WebSocket**：实时消息推送
- **Velocity / FreeMarker**：代码生成模板引擎
- **Druid**：数据库连接池和监控

## 模块结构

```
yudao-module-infra/
├── pom.xml                                    # 父 POM 文件
├── README.md                                  # 本文档
├── yudao-module-infra-api/                   # API 模块（供其他模块 RPC 调用）
│   └── src/main/java/.../infra/
│       ├── api/                              # Feign 客户端接口
│       │   ├── config/                       # 配置服务 API
│       │   │   └── ConfigApi.java            # 参数配置查询接口
│       │   ├── file/                         # 文件服务 API
│       │   │   ├── FileApi.java              # 文件上传、预签名 URL
│       │   │   └── dto/                      # 文件传输对象
│       │   └── websocket/                    # WebSocket API
│       │       ├── WebSocketSenderApi.java   # 消息推送接口
│       │       └── dto/                      # 消息传输对象
│       └── enums/                            # 枚举类
│           ├── codegen/                      # 代码生成器枚举
│           └── ApiConstants.java             # API 常量定义
│
└── yudao-module-infra-server/                # 服务端实现模块
    └── src/main/
        ├── java/.../infra/
        │   ├── InfraServerApplication.java   # 微服务启动类
        │   ├── api/                          # RPC 接口实现
        │   │   ├── config/                   # 配置服务实现
        │   │   ├── file/                     # 文件服务实现
        │   │   ├── logger/                   # 日志服务实现
        │   │   └── websocket/                # WebSocket 服务实现
        │   ├── controller/                   # HTTP 接口层
        │   │   ├── admin/                    # 管理后台接口
        │   │   │   ├── codegen/              # 代码生成器接口
        │   │   │   ├── config/               # 配置管理接口
        │   │   │   ├── db/                   # 数据库管理接口
        │   │   │   ├── demo/                 # 示例接口
        │   │   │   ├── file/                 # 文件管理接口
        │   │   │   ├── logger/               # 日志管理接口
        │   │   │   └── redis/                # Redis 管理接口
        │   │   └── app/                      # 移动端/用户端接口
        │   │       ├── demo/                 # 示例接口
        │   │       └── file/                 # 文件上传接口
        │   ├── service/                      # 业务逻辑层
        │   │   ├── codegen/                  # 代码生成器服务
        │   │   │   ├── CodegenService.java   # 代码生成核心服务
        │   │   │   └── inner/                # 内部辅助服务
        │   │   ├── config/                   # 配置管理服务
        │   │   │   └── ConfigService.java
        │   │   ├── db/                       # 数据库管理服务
        │   │   │   ├── DataSourceConfigService.java
        │   │   │   └── DatabaseTableService.java
        │   │   ├── demo/                     # 示例服务
        │   │   ├── file/                     # 文件服务
        │   │   │   ├── FileService.java      # 文件核心服务
        │   │   │   └── FileConfigService.java # 文件配置服务
        │   │   └── logger/                   # 日志服务
        │   │       ├── ApiAccessLogService.java
        │   │       └── ApiErrorLogService.java
        │   ├── dal/                          # 数据访问层
        │   │   ├── dataobject/               # 数据库实体对象
        │   │   │   ├── codegen/              # 代码生成器 DO
        │   │   │   ├── config/               # 配置 DO
        │   │   │   ├── db/                   # 数据源 DO
        │   │   │   ├── demo/                 # 示例 DO
        │   │   │   ├── file/                 # 文件 DO
        │   │   │   └── logger/               # 日志 DO
        │   │   └── mysql/                    # MyBatis Mapper 接口
        │   ├── convert/                      # MapStruct 对象转换器
        │   │   ├── codegen/                  # 代码生成器转换器
        │   │   ├── config/                   # 配置转换器
        │   │   ├── file/                     # 文件转换器
        │   │   └── redis/                    # Redis 转换器
        │   ├── framework/                    # 框架配置
        │   │   ├── codegen/                  # 代码生成器配置
        │   │   ├── file/                     # 文件存储配置
        │   │   ├── monitor/                  # 监控配置
        │   │   ├── rpc/                      # RPC 配置
        │   │   └── security/                 # 安全配置
        │   ├── job/                          # 定时任务
        │   │   └── logger/                   # 日志清理任务
        │   ├── mq/                           # 消息队列
        │   │   ├── consumer/                 # 消费者
        │   │   ├── message/                  # 消息定义
        │   │   └── producer/                 # 生产者
        │   └── websocket/                    # WebSocket 实现
        │       └── message/                  # WebSocket 消息定义
        │
        └── resources/
            ├── application.yaml              # 主配置文件
            ├── application-local.yaml        # 本地环境配置
            ├── application-dev.yaml          # 开发环境配置
            ├── logback-spring.xml            # 日志配置
            ├── codegen/                      # 代码生成器模板
            │   ├── java/                     # Java 代码模板
            │   │   ├── controller/           # Controller 模板
            │   │   ├── service/              # Service 模板
            │   │   ├── dal/                  # Mapper、DO 模板
            │   │   ├── test/                 # 单元测试模板
            │   │   └── enums/                # 枚举模板
            │   ├── vue/                      # Vue2 模板
            │   ├── vue3/                     # Vue3 模板
            │   ├── vue3_vben/                # Vue3 + Vben 模板
            │   ├── vue3_vben5_antd/          # Vue3 + Vben5 (AntD) 模板
            │   ├── vue3_vben5_ele/           # Vue3 + Vben5 (Element) 模板
            │   └── sql/                      # SQL 脚本模板
            └── file/                         # 文件存储相关配置
```

## 核心功能详解

### 代码生成器工作流程

1. **导入数据库表**
   - 连接到指定数据源
   - 读取数据库表结构和字段信息
   - 生成初始配置

2. **配置生成选项**
   - 选择模板类型（CRUD、树形、子表等）
   - 配置包路径、模块名、作者等
   - 设置字段显示类型、校验规则
   - 配置前端组件类型

3. **预览生成代码**
   - 实时预览生成的代码
   - 支持多文件预览

4. **下载代码**
   - 打包下载 ZIP 文件
   - 包含后端和前端完整代码

### 文件服务架构

```
文件上传流程：
用户上传 → Controller 接收 → FileService 处理 → FileClient（存储适配器） → 具体存储服务
                                                    ├─ LocalFileClient (本地)
                                                    ├─ S3FileClient (S3/MinIO)
                                                    ├─ AliyunOssFileClient (阿里云)
                                                    ├─ TencentCosFileClient (腾讯云)
                                                    └─ QiniuFileClient (七牛云)
```

文件记录存储在数据库中，包含文件路径、大小、类型、配置信息等。

### 日志记录机制

- **访问日志**：通过 Spring MVC 拦截器自动记录
- **错误日志**：通过全局异常处理器捕获
- **异步存储**：使用消息队列异步写入数据库，避免影响主流程性能
- **定期清理**：定时任务自动清理过期日志（默认保留 30 天）

## API 接口说明

### 对外提供的 RPC 接口

#### ConfigApi - 配置服务
```java
// 根据参数键查询参数值
String getConfigValueByKey(String key)
```

#### FileApi - 文件服务
```java
// 保存文件，返回访问路径
String createFile(byte[] content, String name, String directory, String type)

// 生成文件预签名地址（用于私有文件访问）
String presignGetUrl(String url, Integer expirationSeconds)
```

#### WebSocketSenderApi - WebSocket 消息服务
```java
// 发送消息给指定用户
void send(Integer userType, Long userId, String messageType, String messageContent)

// 发送消息给指定会话
void send(String sessionId, String messageType, String messageContent)
```

### 管理后台 HTTP 接口

基础路径：`/admin-api/infra/`

- `/codegen/**` - 代码生成器接口
- `/config/**` - 配置管理接口
- `/data-source-config/**` - 数据源配置接口
- `/db/**` - 数据库表信息接口
- `/demo/**` - 示例 CRUD 接口
- `/file/**` - 文件管理接口
- `/file-config/**` - 文件配置接口
- `/api-access-log/**` - API 访问日志接口
- `/api-error-log/**` - API 错误日志接口
- `/redis/**` - Redis 监控接口

## 配置说明

### 主要配置项

```yaml
# application.yaml

yudao:
  file:
    base-path: /data/files  # 本地文件存储路径

spring:
  # 数据源配置
  datasource:
    druid:
      url: jdbc:mysql://127.0.0.1:3306/ruoyi-vue-pro
      username: root
      password: 123456

  # Redis 配置
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 1
```

### 环境配置文件

- `application.yaml` - 通用配置
- `application-local.yaml` - 本地开发环境
- `application-dev.yaml` - 开发环境

## 启动方式

### 微服务模式（独立启动）

```bash
# 1. 确保 Nacos、MySQL、Redis 已启动

# 2. 进入模块目录
cd yudao-module-infra/yudao-module-infra-server

# 3. 启动服务
mvn spring-boot:run

# 或运行主类
# cn.iocoder.yudao.module.infra.InfraServerApplication
```

访问地址：`http://localhost:48083`

### 单体模式（集成启动）

在 `yudao-server` 项目的 `pom.xml` 中取消注释 infra 模块依赖：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-module-infra-server</artifactId>
    <version>${revision}</version>
</dependency>
```

然后启动 `yudao-server` 项目即可。

## 开发指南

### 添加新的文件存储类型

1. 实现 `FileClient` 接口
2. 在 `FileClientFactory` 中注册新的客户端类型
3. 在前端添加对应的配置表单

### 自定义代码生成模板

1. 在 `resources/codegen/` 目录下创建新的模板文件
2. 使用 Velocity 语法编写模板
3. 在代码生成配置中选择新模板

### 扩展 API 日志记录

可以通过实现 `ApiAccessLogFrameworkService` 接口来自定义日志处理逻辑。

## 数据库表

主要数据库表：

- `infra_codegen_table` - 代码生成表定义
- `infra_codegen_column` - 代码生成字段定义
- `infra_config` - 参数配置表
- `infra_file` - 文件记录表
- `infra_file_config` - 文件配置表
- `infra_file_content` - 文件内容表
- `infra_data_source_config` - 数据源配置表
- `infra_api_access_log` - API 访问日志表
- `infra_api_error_log` - API 错误日志表
- `infra_demo01_contact` - 示例表（学生管理）

## 依赖的其他模块

- `yudao-module-system`：用户、权限、租户等基础信息
- `yudao-framework`：框架核心功能（Web、数据库、缓存等）

## 注意事项

1. **文件存储配置**
   - 首次使用需要在"文件配置"菜单配置存储器
   - 建议生产环境使用对象存储（OSS/COS/S3）

2. **代码生成器**
   - 生成代码前请先备份，避免覆盖已修改的代码
   - 建议使用"预览"功能确认后再下载

3. **日志清理**
   - 定时任务默认每天凌晨执行
   - 日志保留天数可在配置中调整

4. **数据源配置**
   - 添加新数据源需要确保网络连通性
   - 建议使用只读账号用于代码生成

## 相关文档

- [芋道官方文档](https://doc.iocoder.cn/)
- [代码生成器使用文档](https://doc.iocoder.cn/code-generator/)
- [文件服务配置文档](https://doc.iocoder.cn/file-service/)

## 技术支持

- 官网：https://www.iocoder.cn
- 仓库：https://github.com/YunaiV/yudao-cloud
- 文档：https://doc.iocoder.cn
