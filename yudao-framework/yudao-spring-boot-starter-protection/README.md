# yudao-spring-boot-starter-protection

## 📖 模块简介

服务保护组件，提供分布式锁、限流、幂等、API 签名验证等功能，保障系统的稳定性和安全性。

## ✨ 功能特性

### 🔒 1. 分布式锁（Distributed Lock）

基于 [Lock4j](https://gitee.com/baomidou/lock4j) 实现的分布式锁功能，使用 Redisson 作为底层实现。

#### 使用方式

```java
import com.baomidou.lock.annotation.Lock4j;

@Lock4j(keys = {"#userId"}, expire = 60000, acquireTimeout = 1000)
public void updateUserInfo(Long userId) {
    // 业务逻辑
}
```

#### 核心特性

- 支持多种锁类型：可重入锁、公平锁、读写锁等
- 自动失败策略：获取锁失败时抛出 `ServiceException` 异常
- 支持 SpEL 表达式动态生成锁的 Key

---

### 🚦 2. 接口限流（Rate Limiter）

基于 Redisson 的 `RRateLimiter` 实现的限流功能，支持多种限流策略。

#### 使用方式

```java
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;

@RateLimiter(time = 1, count = 10, message = "操作过于频繁，请稍后重试")
public void sendSms(String phone) {
    // 发送短信逻辑
}
```

#### 核心特性

- **多种限流级别**：
  - `DefaultRateLimiterKeyResolver`：全局级别限流
  - `UserRateLimiterKeyResolver`：用户级别限流
  - `ClientIpRateLimiterKeyResolver`：IP 级别限流
  - `ServerNodeRateLimiterKeyResolver`：服务器节点级别限流
  - `ExpressionRateLimiterKeyResolver`：自定义表达式限流

- **灵活配置**：
  - `time`：限流时间窗口
  - `timeUnit`：时间单位（默认秒）
  - `count`：限流次数
  - `message`：自定义提示信息
  - `keyResolver`：Key 解析器
  - `keyArg`：Key 参数（用于表达式）

#### 使用示例

```java
// 用户级别限流：每个用户 1 秒内最多调用 5 次
@RateLimiter(time = 1, count = 5, keyResolver = UserRateLimiterKeyResolver.class)
public void userOperation() {
    // 业务逻辑
}

// IP 级别限流：每个 IP 1 分钟内最多调用 100 次
@RateLimiter(time = 1, timeUnit = TimeUnit.MINUTES, count = 100, 
             keyResolver = ClientIpRateLimiterKeyResolver.class)
public void publicApi() {
    // 业务逻辑
}

// 自定义表达式限流：根据订单 ID 限流
@RateLimiter(time = 1, count = 1, 
             keyResolver = ExpressionRateLimiterKeyResolver.class,
             keyArg = "#orderId")
public void processOrder(Long orderId) {
    // 业务逻辑
}
```

---

### 🔁 3. 接口幂等（Idempotent）

参考 [it4alla/idempotent](https://github.com/it4alla/idempotent) 项目实现的幂等性保证机制。

#### 核心原理

相同参数的方法，在指定时间内有且仅能执行一次，通过这种方式保证幂等性。

#### 使用方式

```java
import cn.iocoder.yudao.framework.idempotent.core.annotation.Idempotent;

@Idempotent(timeout = 10, message = "重复请求，请稍后重试")
public void createOrder(OrderCreateReqVO reqVO) {
    // 创建订单逻辑
}
```

#### 核心特性

- **多种幂等级别**：
  - `DefaultIdempotentKeyResolver`：全局级别
  - `UserIdempotentKeyResolver`：用户级别
  - `ExpressionIdempotentKeyResolver`：自定义表达式

- **灵活配置**：
  - `timeout`：幂等超时时间（默认 1 秒）
  - `timeUnit`：时间单位（默认秒）
  - `message`：自定义提示信息
  - `keyResolver`：Key 解析器
  - `keyArg`：Key 参数
  - `deleteKeyWhenException`：异常时是否删除 Key（默认 true）

#### 使用场景

适用于防止用户快速双击按钮、前端未禁用导致的重复请求等场景。

#### 与 Lock4j 的区别

- **幂等组件**：在指定时间内拒绝重复请求，适用于防止重复提交
- **Lock4j**：分布式锁，串行执行，适用于需要互斥访问的场景

#### 使用示例

```java
// 用户级别幂等：每个用户 5 秒内只能提交一次
@Idempotent(timeout = 5, keyResolver = UserIdempotentKeyResolver.class)
public void submitForm(FormReqVO reqVO) {
    // 业务逻辑
}

// 自定义表达式幂等：根据订单号保证幂等
@Idempotent(timeout = 60, 
            keyResolver = ExpressionIdempotentKeyResolver.class,
            keyArg = "#orderNo")
public void payOrder(String orderNo) {
    // 支付逻辑
}
```

---

### ✍️ 4. API 签名验证（API Signature）

提供 HTTP API 签名验证功能，确保接口调用的安全性。参考微信支付安全规范实现。

#### 使用方式

```java
import cn.iocoder.yudao.framework.signature.core.annotation.ApiSignature;

@ApiSignature(timeout = 300, message = "签名验证失败")
@PostMapping("/open/api")
public void openApi(@RequestParam Map<String, String> params) {
    // 开放 API 逻辑
}
```

#### 核心特性

- **签名参数**：
  - `appId`：应用 ID（默认参数名：appId）
  - `timestamp`：时间戳（默认参数名：timestamp）
  - `nonce`：随机数，10 位以上（默认参数名：nonce）
  - `sign`：客户端签名（默认参数名：sign）

- **灵活配置**：
  - `timeout`：请求有效时间（默认 60 秒）
  - `timeUnit`：时间单位（默认秒）
  - `message`：自定义提示信息

#### 签名算法

1. 将所有参数（除 sign 外）按参数名升序排序
2. 将排序后的参数拼接成字符串：`key1=value1&key2=value2`
3. 拼接应用密钥（appSecret）
4. 对拼接后的字符串进行 MD5 加密生成签名

#### 使用场景

适用于开放 API、第三方接口对接等需要保证请求来源可信的场景。

---

## 📦 Maven 依赖

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-protection</artifactId>
</dependency>
```

## 🔧 技术栈

- **Lock4j**：分布式锁实现
- **Redisson**：Redis 客户端，提供限流、锁等功能
- **Spring AOP**：切面编程，拦截注解
- **Redis**：数据存储

## 📝 配置说明

### 分布式锁配置

```yaml
# Lock4j 配置
lock4j:
  acquire-timeout: 3000  # 获取锁超时时间（毫秒）
  expire: 30000          # 锁过期时间（毫秒）
```

### Redis 配置

```yaml
spring:
  redis:
    host: 127.0.0.1
    port: 6379
    database: 0
    password: 
```

## 🎯 最佳实践

### 1. 分布式锁

- ✅ 适用场景：需要互斥访问的业务，如库存扣减、订单创建
- ⚠️ 注意事项：避免锁粒度过大，注意死锁问题

### 2. 限流

- ✅ 适用场景：保护高频 API，防止恶意攻击
- ⚠️ 注意事项：合理设置限流阈值，避免影响正常用户

### 3. 幂等

- ✅ 适用场景：防止重复提交，如订单创建、支付
- ⚠️ 注意事项：超时时间应大于业务执行时间

### 4. API 签名

- ✅ 适用场景：开放 API、第三方对接
- ⚠️ 注意事项：妥善保管 appSecret，定期更新

## 📚 参考资料

- [Lock4j 官方文档](https://gitee.com/baomidou/lock4j)
- [Redisson 官方文档](https://redisson.org/)
- [微信支付安全规范](https://pay.weixin.qq.com/wiki/doc/api/jsapi.php?chapter=4_3)

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

Apache License 2.0

