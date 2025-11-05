# yudao-spring-boot-starter-biz-tenant

## 📖 模块简介

`yudao-spring-boot-starter-biz-tenant` 是芋道项目的多租户功能组件，提供了完整的 SaaS 多租户解决方案。该模块基于"共享数据库、独立 Schema"的设计思想，通过在数据表中添加 `tenant_id` 字段来实现租户隔离，确保不同租户的数据安全隔离。

## ✨ 核心特性

### 1. 多层面租户隔离

- **数据库层（DB）**：基于 MyBatis Plus 多租户插件，自动在 SQL 中添加 `tenant_id` 过滤条件
- **缓存层（Redis）**：通过在 Redis Key 上拼接租户编号实现隔离
- **Web 层**：解析 HTTP 请求 Header 中的 `tenant-id`，建立租户上下文
- **安全层（Security）**：校验当前登录用户，防止越权访问其他租户数据
- **定时任务（Job）**：支持按租户独立并行执行定时任务
- **消息队列（MQ）**：支持 Redis、Kafka、RabbitMQ、RocketMQ 等消息队列的租户隔离
- **RPC 调用**：支持 Feign 等 RPC 调用时自动传递租户上下文
- **异步任务（Async）**：基于 TransmittableThreadLocal 实现异步场景下的租户上下文传递

### 2. 灵活的租户忽略机制

- 支持通过 `@TenantIgnore` 注解标记方法或类，跳过租户过滤
- 支持配置忽略的 URL、表名和缓存
- 支持 SpEL 表达式动态控制是否忽略租户

### 3. 租户上下文管理

- 基于 `TransmittableThreadLocal` 实现上下文传递
- 支持临时切换租户执行特定逻辑
- 支持忽略租户执行系统级操作

## 📦 依赖关系

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-biz-tenant</artifactId>
</dependency>
```

**主要依赖模块：**
- `yudao-common`：基础工具类
- `yudao-spring-boot-starter-security`：安全框架
- `yudao-spring-boot-starter-mybatis`：数据库操作
- `yudao-spring-boot-starter-redis`：缓存操作
- `yudao-spring-boot-starter-rpc`：RPC 调用（可选）
- `yudao-spring-boot-starter-job`：定时任务（可选）
- `yudao-spring-boot-starter-mq`：消息队列（可选）

## 🚀 快速开始

### 1. 添加依赖

在需要使用多租户功能的模块中添加依赖：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-biz-tenant</artifactId>
</dependency>
```

### 2. 配置文件

在 `application.yml` 中配置多租户参数：

```yaml
yudao:
  tenant:
    enable: true  # 是否开启多租户，默认为 true
    ignore-urls:  # 需要忽略多租户的请求 URL
      - /admin-api/system/tenant/get-id-by-name  # 根据租户名获取租户编号
      - /admin-api/system/captcha/get-image  # 获取图片验证码
      - /admin-api/infra/file/*/get/**  # 获取文件
    ignore-visit-urls:  # 需要忽略跨租户访问的请求
      - /admin-api/system/user/profile/get
    ignore-tables:  # 需要忽略多租户的表
      - system_tenant
      - system_tenant_package
      - system_dict_data
      - system_dict_type
      - system_error_code
      - system_menu
      - system_sms_channel
      - system_sms_template
      - system_sms_log
      - system_sensitive_word
      - system_oauth2_client
      - system_mail_account
      - system_mail_template
      - system_mail_log
      - infra_codegen_column
      - infra_codegen_table
      - infra_test_demo
      - infra_config
      - infra_file_config
      - infra_file
      - infra_file_content
      - infra_job
      - infra_job_log
      - infra_data_source_config
    ignore-caches:  # 需要忽略多租户的缓存
      - oauth2_client
```

### 3. 数据库表设计

在需要租户隔离的表中添加 `tenant_id` 字段：

```sql
CREATE TABLE `your_table` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  -- 其他字段...
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='示例表';

-- 为 tenant_id 添加索引
ALTER TABLE `your_table` ADD INDEX `idx_tenant_id` (`tenant_id`);
```

### 4. 实体类继承

让需要租户隔离的实体类继承 `TenantBaseDO`：

```java
@TableName("your_table")
@Data
@EqualsAndHashCode(callSuper = true)
public class YourDO extends TenantBaseDO {
    
    @TableId
    private Long id;
    
    // 其他字段...
}
```

## 📚 核心 API 使用

### 1. 租户上下文操作

#### 获取当前租户 ID

```java
// 获取租户 ID（可能为 null）
Long tenantId = TenantContextHolder.getTenantId();

// 获取租户 ID（必须存在，否则抛异常）
Long tenantId = TenantContextHolder.getRequiredTenantId();

// 设置租户 ID
TenantContextHolder.setTenantId(1L);

// 清除租户 ID
TenantContextHolder.clear();
```

#### 判断是否忽略租户

```java
// 判断当前是否忽略租户
boolean ignore = TenantContextHolder.isIgnore();

// 设置忽略租户
TenantContextHolder.setIgnore(true);
```

### 2. 使用 TenantUtils 工具类

#### 临时切换租户执行

```java
// 使用指定租户执行逻辑（无返回值）
TenantUtils.execute(tenantId, () -> {
    // 这里的代码会在指定租户上下文中执行
    userService.createUser(user);
});

// 使用指定租户执行逻辑（有返回值）
User user = TenantUtils.execute(tenantId, () -> {
    return userService.getUser(userId);
});
```

#### 忽略租户执行

```java
// 忽略租户执行（适用于系统级操作）
TenantUtils.executeIgnore(() -> {
    // 这里的代码会忽略租户过滤，可以访问所有租户的数据
    List<User> allUsers = userService.getAllUsers();
});
```

### 3. 使用 @TenantIgnore 注解

#### 在方法上使用

```java
@Service
public class SystemService {
    
    /**
     * 系统级操作，忽略租户过滤
     */
    @TenantIgnore
    public List<Config> getAllSystemConfigs() {
        return configMapper.selectList();
    }
    
    /**
     * 动态控制是否忽略租户
     */
    @TenantIgnore(enable = "#ignoreFlag")
    public void dynamicIgnore(boolean ignoreFlag) {
        // 当 ignoreFlag 为 true 时忽略租户
    }
}
```

#### 在 Controller 类上使用

```java
/**
 * 该 Controller 的所有接口都会忽略租户
 * 并且所有 URL 会自动添加到 ignore-urls 配置中
 */
@TenantIgnore
@RestController
@RequestMapping("/admin-api/system/open")
public class OpenController {
    
    @GetMapping("/get")
    public CommonResult<Config> get() {
        // 这里会忽略租户过滤
        return success(configService.get());
    }
}
```

#### 在实体类上使用

```java
/**
 * 该实体对应的表会被忽略租户过滤
 * 相当于将表名添加到 ignore-tables 配置中
 */
@TenantIgnore
@TableName("system_config")
@Data
public class ConfigDO extends BaseDO {
    // 字段定义...
}
```

### 4. 定时任务多租户支持

使用 `@TenantJob` 注解实现按租户并行执行：

```java
@Component
public class TenantScheduleJob {
    
    /**
     * 该任务会为每个租户独立执行一次
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @TenantJob
    public void dailyStatistics() {
        // 这里的代码会在每个租户的上下文中分别执行
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        statisticsService.generateDailyReport(tenantId);
    }
}
```

## 🏗️ 核心组件说明

### 1. 租户上下文持有器

**TenantContextHolder**：管理当前线程的租户信息
- 使用 `TransmittableThreadLocal` 实现线程间上下文传递
- 存储租户 ID 和忽略标识

### 2. 数据库租户拦截器

**TenantDatabaseInterceptor**：基于 MyBatis Plus 实现 SQL 自动添加租户条件
- 自动在查询、更新、删除语句中添加 `tenant_id` 条件
- 支持忽略指定表
- 支持通过上下文动态控制

### 3. Redis 租户缓存管理器

**TenantRedisCacheManager**：实现 Redis 缓存的租户隔离
- 在缓存 Key 前添加租户前缀
- 支持忽略指定缓存

### 4. Web 租户过滤器

**TenantContextWebFilter**：解析 HTTP 请求头中的租户信息
- 从 `tenant-id` Header 中获取租户编号
- 设置到租户上下文中

**TenantSecurityWebFilter**：租户安全校验
- 验证用户是否有权访问当前租户数据
- 防止越权访问

### 5. 消息队列租户支持

支持多种消息队列的租户传递：
- **Redis Stream**：TenantRedisMessageInterceptor
- **Kafka**：TenantKafkaProducerInterceptor
- **RabbitMQ**：TenantRabbitMQMessagePostProcessor
- **RocketMQ**：TenantRocketMQSendMessageHook

### 6. RPC 租户支持

**TenantRequestInterceptor**：Feign 调用时自动传递租户信息
- 在 RPC 请求头中添加租户 ID
- 远程服务自动解析并设置租户上下文

## ⚙️ 配置说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `yudao.tenant.enable` | Boolean | true | 是否开启多租户功能 |
| `yudao.tenant.ignore-urls` | Set<String> | [] | 忽略多租户的 URL 列表 |
| `yudao.tenant.ignore-visit-urls` | Set<String> | [] | 忽略跨租户访问的 URL 列表 |
| `yudao.tenant.ignore-tables` | Set<String> | [] | 忽略多租户的表名列表 |
| `yudao.tenant.ignore-caches` | Set<String> | [] | 忽略多租户的缓存名称列表 |

## 🔍 常见问题

### 1. 如何禁用多租户功能？

在配置文件中设置：

```yaml
yudao:
  tenant:
    enable: false
```

### 2. 某些表不需要租户隔离怎么办？

方式一：在配置文件中添加：

```yaml
yudao:
  tenant:
    ignore-tables:
      - your_table_name
```

方式二：在实体类上添加注解：

```java
@TenantIgnore
@TableName("your_table_name")
public class YourDO extends BaseDO {
    // ...
}
```

### 3. 如何在代码中临时忽略租户？

使用 `TenantUtils.executeIgnore()`：

```java
TenantUtils.executeIgnore(() -> {
    // 这里的操作会忽略租户过滤
    systemService.doSomething();
});
```

### 4. 异步任务中租户上下文丢失怎么办？

本模块已集成 `TransmittableThreadLocal`，配合阿里的 TTL 框架，可以自动传递租户上下文。确保使用框架提供的线程池即可。

### 5. 定时任务如何支持多租户？

在定时任务方法上添加 `@TenantJob` 注解：

```java
@Scheduled(cron = "0 0 * * * ?")
@TenantJob
public void hourlyTask() {
    // 该任务会为每个租户独立执行
}
```

## 📝 最佳实践

### 1. 数据库设计规范

- 所有业务表都应添加 `tenant_id` 字段（除非确实不需要租户隔离）
- 为 `tenant_id` 字段添加索引以提升查询性能
- 组合索引时将 `tenant_id` 放在前面

### 2. 代码规范

- 实体类统一继承 `TenantBaseDO`，无需手动定义 `tenant_id` 字段
- 不要在业务代码中手动设置 `tenant_id`，由框架自动注入
- 系统级操作使用 `@TenantIgnore` 或 `TenantUtils.executeIgnore()`

### 3. 安全规范

- 重要接口务必添加租户校验
- 不要轻易使用 `@TenantIgnore`，避免数据泄露
- 定期审计跨租户访问日志

### 4. 性能优化

- 合理设计索引，将 `tenant_id` 作为索引前缀
- 大数据量场景考虑分库分表
- 缓存 Key 设计时考虑租户隔离

## 🔗 相关链接

- [芋道源码 - 多租户文档](https://doc.iocoder.cn/multi-tenant/)
- [项目主页](https://github.com/YunaiV/ruoyi-vue-pro)

## 📄 许可证

本模块遵循项目整体许可证。

