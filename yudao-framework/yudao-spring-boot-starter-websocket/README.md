# WebSocket 组件

## 📖 模块简介

`yudao-spring-boot-starter-websocket` 是芋道框架提供的 WebSocket 组件，基于 Spring WebSocket 封装，提供了**多节点消息广播**能力，支持分布式环境下的实时通信。

### 核心特性

- ✅ **多节点广播**：支持分布式环境下的消息广播，可在多个服务节点间同步消息
- ✅ **多种消息队列**：支持 Local（单机）、Redis、RabbitMQ、RocketMQ、Kafka 多种消息发送器
- ✅ **会话管理**：自动管理 WebSocket 连接会话，支持按用户类型、用户ID查询会话
- ✅ **安全集成**：集成 Spring Security，自动识别登录用户身份
- ✅ **多租户支持**：支持多租户环境，消息广播自动按租户隔离
- ✅ **消息监听器**：基于消息类型的监听器机制，方便扩展业务逻辑
- ✅ **JSON 消息格式**：统一的 JSON 消息格式，易于前后端对接

## 🚀 快速开始

### 1. 添加依赖

在你的模块 `pom.xml` 中引入依赖：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-websocket</artifactId>
</dependency>
```

### 2. 配置文件

在 `application.yml` 中添加配置：

```yaml
yudao:
  websocket:
    enable: true                 # 是否启用 WebSocket，默认为 true
    path: /ws                    # WebSocket 连接路径，默认为 /ws
    sender-type: local           # 消息发送器类型：local、redis、rocketmq、rabbitmq、kafka
```

### 3. 实现消息监听器

创建自定义消息监听器来处理前端发送的消息：

```java
@Component
public class DemoMessageListener implements WebSocketMessageListener<DemoMessage> {

    @Override
    public void onMessage(WebSocketSession session, DemoMessage message) {
        // 处理前端发送的消息
        log.info("[onMessage][session({}) 接收到消息：{}]", session.getId(), message);
    }

    @Override
    public String getType() {
        return "demo"; // 消息类型标识
    }
}
```

### 4. 发送消息

注入 `WebSocketMessageSender` 来发送消息给前端：

```java
@Resource
private WebSocketMessageSender webSocketMessageSender;

// 发送消息给指定用户
webSocketMessageSender.sendObject(userType, userId, "demo", messageContent);

// 发送消息给指定用户类型的所有用户
webSocketMessageSender.sendObject(userType, "demo", messageContent);

// 发送消息给指定 Session
webSocketMessageSender.sendObject(sessionId, "demo", messageContent);
```

## ⚙️ 配置说明

### 基础配置

| 配置项 | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| `yudao.websocket.enable` | Boolean | `true` | 是否启用 WebSocket 组件 |
| `yudao.websocket.path` | String | `/ws` | WebSocket 连接路径 |
| `yudao.websocket.sender-type` | String | `local` | 消息发送器类型 |

### 消息发送器类型

#### Local（单机模式）

适用于单实例部署，消息只在本地节点广播。

```yaml
yudao:
  websocket:
    sender-type: local
```

#### Redis（推荐）

适用于分布式部署，基于 Redis Pub/Sub 实现跨节点消息广播。

```yaml
yudao:
  websocket:
    sender-type: redis
```

#### RocketMQ

适用于分布式部署，基于 RocketMQ 实现跨节点消息广播。

```yaml
yudao:
  websocket:
    sender-type: rocketmq
    sender-rocketmq:
      topic: websocket_topic  # RocketMQ Topic
```

#### RabbitMQ

适用于分布式部署，基于 RabbitMQ 实现跨节点消息广播。

```yaml
yudao:
  websocket:
    sender-type: rabbitmq
```

#### Kafka

适用于分布式部署，基于 Kafka 实现跨节点消息广播。

```yaml
yudao:
  websocket:
    sender-type: kafka
    sender-kafka:
      topic: websocket_topic  # Kafka Topic
```

## 📝 使用示例

### 前端连接示例

```javascript
// 连接 WebSocket
const ws = new WebSocket('ws://localhost:8080/ws?token=your_access_token');

// 连接成功
ws.onopen = function() {
    console.log('WebSocket 连接成功');
};

// 接收消息
ws.onmessage = function(event) {
    const message = JSON.parse(event.data);
    console.log('收到消息：', message);
    // message 格式：{ type: 'demo', content: {...} }
};

// 发送消息
ws.send(JSON.stringify({
    type: 'demo',
    content: { text: 'Hello Server' }
}));

// 连接关闭
ws.onclose = function() {
    console.log('WebSocket 连接关闭');
};

// 连接错误
ws.onerror = function(error) {
    console.error('WebSocket 错误：', error);
};
```

### 后端发送消息示例

```java
@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final WebSocketMessageSender webSocketMessageSender;
    
    /**
     * 发送通知给指定用户
     */
    public void sendNotification(Long userId, String content) {
        NotificationMessage message = new NotificationMessage();
        message.setContent(content);
        message.setTime(LocalDateTime.now());
        
        // 发送给指定用户（支持该用户的多个会话）
        webSocketMessageSender.sendObject(
            UserTypeEnum.ADMIN.getValue(), 
            userId, 
            "notification", 
            message
        );
    }
    
    /**
     * 广播消息给所有管理员
     */
    public void broadcastToAdmins(String content) {
        NotificationMessage message = new NotificationMessage();
        message.setContent(content);
        message.setTime(LocalDateTime.now());
        
        // 发送给所有管理员类型的用户
        webSocketMessageSender.sendObject(
            UserTypeEnum.ADMIN.getValue(), 
            "notification", 
            message
        );
    }
}
```

### 自定义消息监听器示例

```java
@Component
@Slf4j
public class ChatMessageListener implements WebSocketMessageListener<ChatMessage> {
    
    @Resource
    private WebSocketMessageSender webSocketMessageSender;
    
    @Override
    public void onMessage(WebSocketSession session, ChatMessage message) {
        log.info("[onMessage][session({}) 收到聊天消息：{}]", session.getId(), message);
        
        // 获取当前用户信息
        LoginUser loginUser = WebSocketFrameworkUtils.getLoginUser(session);
        if (loginUser == null) {
            return;
        }
        
        // 处理业务逻辑（保存消息、推送通知等）
        // ...
        
        // 回复消息
        ChatMessage reply = new ChatMessage();
        reply.setContent("收到你的消息：" + message.getContent());
        webSocketMessageSender.sendObject(session.getId(), "chat", reply);
    }
    
    @Override
    public String getType() {
        return "chat";
    }
}

@Data
class ChatMessage {
    private String content;
    private Long fromUserId;
    private Long toUserId;
}
```

## 🏗️ 架构设计

### 核心组件

```
yudao-spring-boot-starter-websocket
├── config                          # 配置层
│   ├── YudaoWebSocketAutoConfiguration    # 自动配置类
│   └── WebSocketProperties               # 配置属性
├── core                            # 核心层
│   ├── handler                     # 消息处理器
│   │   └── JsonWebSocketMessageHandler   # JSON 消息处理
│   ├── listener                    # 消息监听器
│   │   └── WebSocketMessageListener      # 监听器接口
│   ├── message                     # 消息模型
│   │   └── JsonWebSocketMessage          # JSON 消息格式
│   ├── security                    # 安全相关
│   │   ├── LoginUserHandshakeInterceptor # 登录用户拦截器
│   │   └── WebSocketAuthorizeRequestsCustomizer # 权限配置
│   ├── sender                      # 消息发送器
│   │   ├── WebSocketMessageSender        # 发送器接口
│   │   ├── local/                        # 本地发送器
│   │   ├── redis/                        # Redis 发送器
│   │   ├── rabbitmq/                     # RabbitMQ 发送器
│   │   ├── rocketmq/                     # RocketMQ 发送器
│   │   └── kafka/                        # Kafka 发送器
│   ├── session                     # 会话管理
│   │   ├── WebSocketSessionManager       # 会话管理器接口
│   │   ├── WebSocketSessionManagerImpl   # 会话管理器实现
│   │   └── WebSocketSessionHandlerDecorator # 会话处理装饰器
│   └── util                        # 工具类
│       └── WebSocketFrameworkUtils       # WebSocket 工具类
```

### 消息流程

#### 接收消息流程

```
前端发送消息
    ↓
WebSocketSessionHandlerDecorator (会话处理)
    ↓
JsonWebSocketMessageHandler (消息解析)
    ↓
WebSocketMessageListener (业务处理)
```

#### 发送消息流程（分布式）

```
业务代码调用 WebSocketMessageSender
    ↓
发送消息到消息队列 (Redis/RabbitMQ/RocketMQ/Kafka)
    ↓
所有节点的 Consumer 接收消息
    ↓
WebSocketSessionManager 查找目标会话
    ↓
通过 WebSocketSession 发送消息到前端
```

### 会话管理

- **会话存储**：内存中维护 Session 映射关系（sessionId → Session）
- **用户索引**：支持按用户类型、用户ID查询对应的所有会话
- **多会话支持**：同一用户可以建立多个 WebSocket 连接（如多端登录）
- **自动清理**：连接断开时自动清理会话信息

### 安全机制

- **身份认证**：通过 `LoginUserHandshakeInterceptor` 在握手阶段验证用户身份
- **Token 传递**：支持通过 URL 参数传递访问令牌（`?token=xxx`）
- **权限集成**：自动配置 Spring Security 放行 WebSocket 路径
- **租户隔离**：多租户环境下自动按租户过滤消息接收者

## 🔧 高级特性

### 获取登录用户信息

在消息监听器中获取当前 WebSocket 连接的登录用户：

```java
@Override
public void onMessage(WebSocketSession session, DemoMessage message) {
    LoginUser loginUser = WebSocketFrameworkUtils.getLoginUser(session);
    Long userId = loginUser.getId();
    Integer userType = loginUser.getUserType();
    Long tenantId = loginUser.getTenantId();
    
    // 使用用户信息进行业务处理
}
```

### 会话管理

通过 `WebSocketSessionManager` 管理会话：

```java
@Resource
private WebSocketSessionManager sessionManager;

// 获取指定 Session
WebSocketSession session = sessionManager.getSession(sessionId);

// 获取指定用户类型的所有会话
Collection<WebSocketSession> sessions = sessionManager.getSessionList(userType);

// 获取指定用户的所有会话
Collection<WebSocketSession> userSessions = sessionManager.getSessionList(userType, userId);
```

### 自定义配置

如需自定义 WebSocket 配置，可以实现 `WebSocketConfigurer`：

```java
@Configuration
public class CustomWebSocketConfig implements WebSocketConfigurer {
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 自定义配置
    }
}
```

## 📚 参考资料

- [芋道 Spring Boot WebSocket 入门](http://www.iocoder.cn/Spring-Boot/WebSocket/?yudao)
- [Spring WebSocket 官方文档](https://docs.spring.io/spring-framework/docs/current/reference/html/web.html#websocket)

## 🤝 技术支持

如有问题，请提交 Issue 或查看项目文档。

---

**注意事项**：
1. 生产环境建议使用 Redis、RabbitMQ 等消息队列实现分布式消息广播
2. 需要在前端连接时传递访问令牌进行身份认证
3. 消息内容建议使用 JSON 格式，便于前后端解析
4. 多租户环境下，系统会自动按租户隔离消息

