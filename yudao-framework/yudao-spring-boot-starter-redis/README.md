# yudao-spring-boot-starter-redis

## 📖 模块简介

`yudao-spring-boot-starter-redis` 是芋道项目的 Redis 封装增强模块，基于 Spring Data Redis 和 Redisson 提供了开箱即用的 Redis 配置和 Spring Cache 缓存支持。

该模块主要解决以下问题：
- ✅ 统一 Redis 序列化配置（使用 JSON 序列化，支持 LocalDateTime 等 Java 8 时间类型）
- ✅ 优化 Spring Cache 的 Key 前缀格式（使用单冒号分隔，避免 Redis 可视化工具显示空节点）
- ✅ 支持在 `@Cacheable` 注解中动态指定缓存过期时间
- ✅ 集成 Redisson 分布式锁和其他高级特性

## 🏗️ 模块结构

```
yudao-spring-boot-starter-redis/
├── src/main/java/cn/iocoder/yudao/framework/redis/
│   ├── config/
│   │   ├── YudaoRedisAutoConfiguration.java      # Redis 核心配置（RedisTemplate）
│   │   ├── YudaoCacheAutoConfiguration.java      # Spring Cache 配置（RedisCacheManager）
│   │   └── YudaoCacheProperties.java             # 缓存配置属性
│   └── core/
│       └── TimeoutRedisCacheManager.java         # 支持动态过期时间的缓存管理器
└── src/main/resources/
    └── META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports  # 自动配置声明
```

## 🚀 核心功能

### 1. RedisTemplate 配置增强

**自动配置的 RedisTemplate Bean：**
- **Key 序列化**：使用 String 序列化（支持中文 key）
- **Value 序列化**：使用 JSON 序列化（Jackson），可读性强，跨语言兼容
- **时间类型支持**：自动注册 `JavaTimeModule`，完美支持 `LocalDateTime`、`LocalDate` 等 Java 8 时间类型

```java
@Autowired
private RedisTemplate<String, Object> redisTemplate;

public void example() {
    // 存储对象（自动 JSON 序列化）
    User user = new User(1L, "张三", LocalDateTime.now());
    redisTemplate.opsForValue().set("user:1", user);
    
    // 读取对象（自动 JSON 反序列化）
    User cachedUser = (User) redisTemplate.opsForValue().get("user:1");
}
```

### 2. Spring Cache 缓存支持

**优化的 Redis 缓存配置：**

#### Key 前缀格式优化
默认情况下，Spring Data Redis 使用双冒号 `::` 作为分隔符（如 `user::123`），在 Redis Desktop Manager 等工具中会显示一个空层级节点。本模块优化为单冒号 `:` 格式（如 `user:123`），结构更清晰。

```yaml
# application.yml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 1h           # 默认缓存过期时间（1小时）
      cache-null-values: true    # 是否缓存 null 值（防止缓存穿透）
      use-key-prefix: true       # 是否使用缓存名作为 Key 前缀
      key-prefix: "myapp:"       # 自定义全局 Key 前缀（可选）
```

#### 基本使用示例

```java
@Service
public class UserService {
    
    /**
     * 缓存用户信息（使用默认过期时间）
     * Key 格式：user:123
     */
    @Cacheable(cacheNames = "user", key = "#id")
    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }
    
    /**
     * 更新缓存
     */
    @CachePut(cacheNames = "user", key = "#user.id")
    public User updateUser(User user) {
        userMapper.updateById(user);
        return user;
    }
    
    /**
     * 清除缓存
     */
    @CacheEvict(cacheNames = "user", key = "#id")
    public void deleteUser(Long id) {
        userMapper.deleteById(id);
    }
}
```

### 3. 动态过期时间支持 ⭐

**核心特性：** 通过 `TimeoutRedisCacheManager`，支持在 `@Cacheable` 的 `cacheNames` 中使用 `#` 符号指定缓存过期时间。

#### 语法格式
```
cacheNames = "缓存名#过期时间单位"
```

#### 支持的时间单位
| 单位 | 说明 | 示例 | 等价于 |
|------|------|------|--------|
| `d`  | 天   | `config#7d` | 7 天后过期 |
| `h`  | 小时 | `product#2h` | 2 小时后过期 |
| `m`  | 分钟 | `user#30m` | 30 分钟后过期 |
| `s`  | 秒   | `code#60s` | 60 秒后过期 |
| 无   | 秒（默认） | `token#3600` | 3600 秒后过期 |

#### 使用示例

```java
@Service
public class CacheExampleService {
    
    /**
     * 用户信息缓存 30 分钟
     * Key: user:1
     * TTL: 30 分钟
     */
    @Cacheable(cacheNames = "user#30m", key = "#userId")
    public User getUserInfo(Long userId) {
        return userMapper.selectById(userId);
    }
    
    /**
     * 商品信息缓存 2 小时
     */
    @Cacheable(cacheNames = "product#2h", key = "#productId")
    public Product getProduct(Long productId) {
        return productMapper.selectById(productId);
    }
    
    /**
     * 系统配置缓存 7 天
     */
    @Cacheable(cacheNames = "config#7d", key = "#configKey")
    public String getConfig(String configKey) {
        return configMapper.selectByKey(configKey);
    }
    
    /**
     * 验证码缓存 60 秒
     */
    @Cacheable(cacheNames = "captcha#60s", key = "#phone")
    public String getCaptcha(String phone) {
        return generateCaptcha();
    }
    
    /**
     * 短期令牌缓存 3600 秒（无单位，默认为秒）
     */
    @Cacheable(cacheNames = "token#3600", key = "#tokenId")
    public String getToken(String tokenId) {
        return generateToken();
    }
}
```

### 4. Redisson 集成

本模块依赖 `redisson-spring-boot-starter`，自动提供分布式锁、分布式集合等高级功能。

```java
@Autowired
private RedissonClient redissonClient;

public void distributedLockExample() {
    RLock lock = redissonClient.getLock("myLock");
    try {
        // 尝试加锁，最多等待 10 秒，锁定 30 秒后自动释放
        if (lock.tryLock(10, 30, TimeUnit.SECONDS)) {
            try {
                // 执行业务逻辑
                doSomething();
            } finally {
                lock.unlock();
            }
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}
```

## ⚙️ 配置说明

### Spring Boot 配置（application.yml）

```yaml
spring:
  # Redis 连接配置
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: your_password
      database: 0
      timeout: 5000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: -1ms

  # Spring Cache 配置
  cache:
    type: redis
    redis:
      time-to-live: 1h              # 默认过期时间
      cache-null-values: true       # 是否缓存 null 值
      use-key-prefix: true          # 是否使用前缀
      key-prefix: "myapp:"          # 自定义前缀（可选）

# 芋道扩展配置
yudao:
  cache:
    redis-scan-batch-size: 30       # Redis SCAN 命令一次返回的数量（用于批量删除缓存）
```

### 配置项说明

#### Spring Cache Redis 配置
| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `spring.cache.redis.time-to-live` | Duration | 无 | 缓存默认过期时间 |
| `spring.cache.redis.cache-null-values` | boolean | true | 是否缓存 null 值（防止缓存穿透） |
| `spring.cache.redis.use-key-prefix` | boolean | true | 是否使用缓存名作为 Key 前缀 |
| `spring.cache.redis.key-prefix` | String | 无 | 自定义全局 Key 前缀 |

#### 芋道扩展配置
| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `yudao.cache.redis-scan-batch-size` | Integer | 30 | Redis SCAN 批量大小 |

## 📦 Maven 依赖

在业务模块的 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-redis</artifactId>
</dependency>
```

## 🔧 技术栈

- Spring Boot 3.x
- Spring Data Redis
- Redisson 3.x
- Jackson（JSON 序列化）
- Lettuce（Redis 客户端）

## 📝 常见问题

### 1. 为什么使用 JSON 序列化而不是 JDK 序列化？

**优点：**
- ✅ 可读性强：在 Redis 中直接看到 JSON 格式数据
- ✅ 跨语言：其他语言（如 Python、Go）也能解析
- ✅ 体积小：相比 JDK 序列化，JSON 通常更紧凑

**缺点：**
- ⚠️ 性能略低：JSON 序列化/反序列化比 JDK 稍慢（但差异很小）

### 2. 缓存 null 值有什么用？

启用 `cache-null-values: true` 可以缓存方法返回的 `null` 值，防止**缓存穿透**攻击：
- 攻击者查询不存在的数据（如 `userId=-1`）
- 缓存中没有，每次都查数据库
- 启用后，`null` 也会被缓存，第二次查询直接返回，不再访问数据库

### 3. Key 前缀的作用是什么？

Key 前缀用于隔离不同应用的缓存数据：
- 开发环境：`dev:user:123`
- 测试环境：`test:user:123`
- 生产环境：`prod:user:123`

### 4. 如何查看 Redis 中的缓存数据？

推荐使用以下工具：
- **Redis Desktop Manager**（支持 Windows/Mac/Linux）
- **RedisInsight**（官方可视化工具）
- **命令行**：`redis-cli` + `KEYS *`（生产环境禁用 KEYS 命令）

## 📚 参考资料

- [Spring Data Redis 官方文档](https://spring.io/projects/spring-data-redis)
- [Spring Cache 抽象](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- [Redisson 官方文档](https://redisson.org/)
- [芋道 Spring Boot Redis 入门](./《芋道 Spring Boot Redis 入门》.md)
- [芋道 Spring Boot Cache 入门](./《芋道 Spring Boot Cache 入门》.md)

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 License

本项目遵循 [MIT License](../../LICENSE)

