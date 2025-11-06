# yudao-spring-boot-starter-mq

## 📖 模块简介

`yudao-spring-boot-starter-mq` 是芋道项目的消息队列（Message Queue）集成模块，提供统一的消息队列抽象和实现。

**支持的消息队列类型：**
- ✅ Redis（基于 Redis Stream 和 Pub/Sub）
- ✅ RocketMQ
- ✅ RabbitMQ
- ✅ Kafka

## 🎯 核心功能

### 1. Redis 消息队列

#### 1.1 Redis Stream（集群消费）
- 基于 Redis Stream 实现的消息队列
- 支持消费者分组，实现集群消费模式
- 自动 ACK 确认机制
- 支持消息重发和清理

#### 1.2 Redis Pub/Sub（广播消费）
- 基于 Redis Pub/Sub 实现的消息发布订阅
- 支持广播模式，所有订阅者都能收到消息
- 适用于实时通知、缓存同步等场景

### 2. 消息拦截器
- 提供 `RedisMessageInterceptor` 接口
- 支持消息发送前后、消费前后的拦截处理
- 可用于多租户、日志记录、性能监控等场景

### 3. 定时任务
- **消息重发任务**：自动重发 Pending 状态的消息
- **消息清理任务**：定时清理过期的消息记录

### 4. RabbitMQ 集成
- 自动配置 Jackson2 JSON 消息转换器
- 简化消息的序列化和反序列化

## 📦 Maven 依赖

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-mq</artifactId>
</dependency>
```

## 🚀 快速开始

### 方式一：使用 Redis Stream（推荐-集群消费）

#### 1. 定义消息

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class UserRegisterMessage extends AbstractRedisStreamMessage {
    
    private Long userId;
    private String username;
    
    @Override
    public String getStreamKey() {
        return "user.register"; // 自定义 Stream Key
    }
}
```

#### 2. 发送消息

```java
@Service
@AllArgsConstructor
public class UserService {
    
    private final RedisMQTemplate redisMQTemplate;
    
    public void registerUser(String username) {
        // 业务逻辑...
        
        // 发送消息
        UserRegisterMessage message = new UserRegisterMessage();
        message.setUserId(userId);
        message.setUsername(username);
        redisMQTemplate.send(message);
    }
}
```

#### 3. 消费消息

```java
@Component
public class UserRegisterMessageListener extends AbstractRedisStreamMessageListener<UserRegisterMessage> {
    
    @Override
    public void onMessage(UserRegisterMessage message) {
        log.info("收到用户注册消息：userId={}, username={}", 
            message.getUserId(), message.getUsername());
        // 处理业务逻辑...
    }
}
```

### 方式二：使用 Redis Pub/Sub（广播消费）

#### 1. 定义消息

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class CacheRefreshMessage extends AbstractRedisChannelMessage {
    
    private String cacheKey;
    
    @Override
    public String getChannel() {
        return "cache.refresh"; // 自定义 Channel
    }
}
```

#### 2. 发送消息

```java
@Service
@AllArgsConstructor
public class CacheService {
    
    private final RedisMQTemplate redisMQTemplate;
    
    public void refreshCache(String cacheKey) {
        // 刷新本地缓存...
        
        // 广播通知其他节点
        CacheRefreshMessage message = new CacheRefreshMessage();
        message.setCacheKey(cacheKey);
        redisMQTemplate.send(message);
    }
}
```

#### 3. 消费消息

```java
@Component
public class CacheRefreshMessageListener extends AbstractRedisChannelMessageListener<CacheRefreshMessage> {
    
    @Override
    public void onMessage(CacheRefreshMessage message) {
        log.info("收到缓存刷新消息：cacheKey={}", message.getCacheKey());
        // 刷新本地缓存...
    }
}
```

### 方式三：使用 RabbitMQ

```yaml
spring:
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest
```

自动配置会启用 Jackson2 JSON 消息转换器，直接使用 Spring AMQP 的 API 即可。

### 方式四：使用 RocketMQ

```yaml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: my-producer-group
```

引入 RocketMQ 依赖后，使用 Spring RocketMQ 的 API。

### 方式五：使用 Kafka

```yaml
spring:
  kafka:
    bootstrap-servers: 127.0.0.1:9092
    consumer:
      group-id: my-consumer-group
```

引入 Kafka 依赖后，使用 Spring Kafka 的 API。

## 🔧 高级特性

### 自定义消息拦截器

```java
@Component
public class CustomMessageInterceptor implements RedisMessageInterceptor {
    
    @Override
    public void sendMessageBefore(AbstractRedisMessage message) {
        log.info("消息发送前：{}", message);
        // 可以添加租户信息、追踪信息等
    }
    
    @Override
    public void consumeMessageBefore(AbstractRedisMessage message) {
        log.info("消息消费前：{}", message);
        // 可以设置租户上下文等
    }
    
    @Override
    public void consumeMessageAfter(AbstractRedisMessage message) {
        log.info("消息消费后：{}", message);
        // 可以清理上下文等
    }
}
```

### 配置消费者分组

Redis Stream 消费者默认使用 `spring.application.name` 作为分组名称。

```yaml
spring:
  application:
    name: yudao-server  # 作为消费者分组名
```

## 📝 设计说明

### 1. 消息类型层次结构

```
AbstractRedisMessage (抽象消息基类)
├── AbstractRedisChannelMessage (Channel 消息 - Pub/Sub)
└── AbstractRedisStreamMessage (Stream 消息 - Stream)
```

### 2. 监听器层次结构

```
AbstractRedisChannelMessageListener (Channel 监听器 - 广播消费)
AbstractRedisStreamMessageListener (Stream 监听器 - 集群消费)
```

### 3. Redis Stream vs Pub/Sub 选择

| 特性 | Redis Stream | Redis Pub/Sub |
|------|--------------|---------------|
| 消费模式 | 集群消费（负载均衡） | 广播消费（所有订阅者都收到） |
| 消息持久化 | ✅ 支持 | ❌ 不支持 |
| 消息重试 | ✅ 支持 | ❌ 不支持 |
| 消费确认 | ✅ 支持 ACK | ❌ 无需确认 |
| 适用场景 | 异步任务、订单处理 | 缓存同步、实时通知 |

## 📚 参考文档

项目中包含的参考文档：
- 《芋道 Spring Boot 事件机制 Event 入门》.md
- 《芋道 Spring Boot 消息队列 Kafka 入门》.md
- 《芋道 Spring Boot 消息队列 RabbitMQ 入门》.md
- 《芋道 Spring Boot 消息队列 RocketMQ 入门》.md

## ⚙️ 自动配置类

- `YudaoRedisMQProducerAutoConfiguration` - Redis MQ 生产者配置
- `YudaoRedisMQConsumerAutoConfiguration` - Redis MQ 消费者配置
- `YudaoRabbitMQAutoConfiguration` - RabbitMQ 配置

## 🔗 依赖关系

```
yudao-spring-boot-starter-mq
├── yudao-spring-boot-starter-redis (必需)
├── spring-kafka (可选)
├── spring-rabbit (可选)
└── rocketmq-spring-boot-starter (可选)
```

## 💡 最佳实践

1. **优先使用 Redis Stream**：对于大多数异步任务场景，Redis Stream 提供了良好的性能和可靠性
2. **消息幂等性**：消费者需要自行保证消息处理的幂等性
3. **异常处理**：在监听器中捕获异常，避免影响其他消息的消费
4. **消息体积**：控制消息大小，避免传输大对象
5. **合理分组**：同一业务的消费者使用相同的分组名，实现负载均衡

## 📄 License

本模块遵循项目整体的开源协议。

