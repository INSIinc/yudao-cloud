package cn.iocoder.yudao.framework.websocket.config;

import cn.iocoder.yudao.framework.mq.redis.config.YudaoRedisMQConsumerAutoConfiguration;
import cn.iocoder.yudao.framework.mq.redis.core.RedisMQTemplate;
import cn.iocoder.yudao.framework.websocket.core.handler.JsonWebSocketMessageHandler;
import cn.iocoder.yudao.framework.websocket.core.listener.WebSocketMessageListener;
import cn.iocoder.yudao.framework.websocket.core.security.LoginUserHandshakeInterceptor;
import cn.iocoder.yudao.framework.websocket.core.security.WebSocketAuthorizeRequestsCustomizer;
import cn.iocoder.yudao.framework.websocket.core.sender.kafka.KafkaWebSocketMessageConsumer;
import cn.iocoder.yudao.framework.websocket.core.sender.kafka.KafkaWebSocketMessageSender;
import cn.iocoder.yudao.framework.websocket.core.sender.local.LocalWebSocketMessageSender;
import cn.iocoder.yudao.framework.websocket.core.sender.rabbitmq.RabbitMQWebSocketMessageConsumer;
import cn.iocoder.yudao.framework.websocket.core.sender.rabbitmq.RabbitMQWebSocketMessageSender;
import cn.iocoder.yudao.framework.websocket.core.sender.redis.RedisWebSocketMessageConsumer;
import cn.iocoder.yudao.framework.websocket.core.sender.redis.RedisWebSocketMessageSender;
import cn.iocoder.yudao.framework.websocket.core.sender.rocketmq.RocketMQWebSocketMessageConsumer;
import cn.iocoder.yudao.framework.websocket.core.sender.rocketmq.RocketMQWebSocketMessageSender;
import cn.iocoder.yudao.framework.websocket.core.session.WebSocketSessionHandlerDecorator;
import cn.iocoder.yudao.framework.websocket.core.session.WebSocketSessionManager;
import cn.iocoder.yudao.framework.websocket.core.session.WebSocketSessionManagerImpl;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;

/**
 * WebSocket 自动配置类
 * <p>
 * 这个类负责配置整个 WebSocket 的核心功能，包括：
 * 1. WebSocket 连接的建立和管理
 * 2. 消息的发送和接收
 * 3. 用户会话的管理
 * 4. 消息在集群环境下的广播（支持 Redis、RocketMQ、RabbitMQ、Kafka）
 * <p>
 * 使用场景：
 * - 实时聊天功能
 * - 系统通知推送
 * - 实时数据更新（如股票行情、订单状态等）
 *
 * @author xingyu4j
 */
@AutoConfiguration(before = YudaoRedisMQConsumerAutoConfiguration.class) // 在 Redis 消息队列自动配置之前执行，确保 WebSocket 消息消费者先创建
@EnableWebSocket // 启用 Spring WebSocket 功能
@ConditionalOnProperty(prefix = "yudao.websocket", value = "enable", matchIfMissing = true)
// 条件装配：可以通过配置 yudao.websocket.enable=false 来关闭 WebSocket 功能，默认为开启
@EnableConfigurationProperties(WebSocketProperties.class) // 启用 WebSocket 配置属性类，读取配置文件中的 yudao.websocket.* 配置
public class YudaoWebSocketAutoConfiguration {

    /**
     * 配置 WebSocket 的核心参数
     * <p>
     * 这个方法创建 WebSocket 配置器，负责：
     * 1. 设置 WebSocket 的访问路径（例如：/ws）
     * 2. 添加握手拦截器（用于在建立连接前进行身份验证）
     * 3. 配置跨域访问（允许前端从不同域名连接）
     *
     * @param handshakeInterceptors WebSocket 握手拦截器数组，用于在连接建立前进行拦截处理
     * @param webSocketHandler      WebSocket 处理器，负责处理连接建立、消息接收、连接关闭等事件
     * @param webSocketProperties   WebSocket 配置属性，包含路径等配置信息
     * @return WebSocket 配置器
     */
    @Bean
    public WebSocketConfigurer webSocketConfigurer(HandshakeInterceptor[] handshakeInterceptors,
                                                   WebSocketHandler webSocketHandler,
                                                   WebSocketProperties webSocketProperties) {
        return registry -> registry
                // 注册 WebSocket 处理器，并指定访问路径（例如：ws://localhost:8080/ws）
                .addHandler(webSocketHandler, webSocketProperties.getPath())
                // 添加握手拦截器，用于在连接建立前验证用户身份
                .addInterceptors(handshakeInterceptors)
                // 设置允许跨域的来源，"*" 表示允许所有来源访问
                // 注意：生产环境建议配置具体的允许域名，避免安全风险
                .setAllowedOriginPatterns("*");
    }

    /**
     * 创建握手拦截器
     * <p>
     * 作用：在 WebSocket 连接建立之前，从 HTTP 请求中提取登录用户信息，
     * 并将用户信息存储到 WebSocket 会话属性中，后续可以根据用户 ID 发送消息。
     * <p>
     * 例如：当用户访问 ws://localhost:8080/ws?token=xxx 时，
     * 拦截器会验证 token，并将用户 ID 保存到会话中。
     *
     * @return 登录用户握手拦截器
     */
    @Bean
    public HandshakeInterceptor handshakeInterceptor() {
        return new LoginUserHandshakeInterceptor();
    }

    /**
     * 创建 WebSocket 处理器
     * <p>
     * 这是 WebSocket 的核心处理器，负责：
     * 1. 处理客户端发送的消息（通过 JsonWebSocketMessageHandler）
     * 2. 管理 WebSocket 连接的生命周期（通过 WebSocketSessionHandlerDecorator）
     * 3. 维护用户会话信息（通过 WebSocketSessionManager）
     * <p>
     * 工作流程：
     * - 用户连接：将用户会话保存到 SessionManager 中
     * - 收到消息：根据消息类型分发给对应的 MessageListener 处理
     * - 用户断开：从 SessionManager 中移除用户会话
     *
     * @param sessionManager   WebSocket 会话管理器，用于存储和管理所有在线用户的会话
     * @param messageListeners 消息监听器列表，每个监听器处理特定类型的消息（如聊天消息、通知消息等）
     * @return WebSocket 处理器
     */
    @Bean
    public WebSocketHandler webSocketHandler(WebSocketSessionManager sessionManager,
                                             List<? extends WebSocketMessageListener<?>> messageListeners) {
        // 1. 创建 JSON 消息处理器，负责解析 JSON 格式的消息，并分发给对应的监听器处理
        JsonWebSocketMessageHandler messageHandler = new JsonWebSocketMessageHandler(messageListeners);
        // 2. 创建会话处理装饰器，负责在消息处理前后管理用户会话（如连接时保存会话，断开时移除会话）
        return new WebSocketSessionHandlerDecorator(messageHandler, sessionManager);
    }

    /**
     * 创建 WebSocket 会话管理器
     * <p>
     * 作用：管理所有在线用户的 WebSocket 会话，提供以下功能：
     * 1. 保存用户连接时的会话信息
     * 2. 根据用户 ID 查找对应的会话
     * 3. 向指定用户发送消息
     * 4. 移除断开连接的用户会话
     * <p>
     * 数据结构：内部使用 Map<Long, WebSocketSession> 存储，key 为用户 ID，value 为会话对象
     *
     * @return WebSocket 会话管理器实现
     */
    @Bean
    public WebSocketSessionManager webSocketSessionManager() {
        return new WebSocketSessionManagerImpl();
    }

    /**
     * 创建 WebSocket 安全配置定制器
     * <p>
     * 作用：配置 Spring Security，允许 WebSocket 路径无需认证即可访问。
     * 因为 WebSocket 的身份验证已经在握手拦截器中处理，不需要 Spring Security 再次验证。
     * <p>
     * 例如：如果 WebSocket 路径是 /ws，则该路径会被添加到 Spring Security 的白名单中。
     *
     * @param webSocketProperties WebSocket 配置属性
     * @return WebSocket 安全配置定制器
     */
    @Bean
    public WebSocketAuthorizeRequestsCustomizer webSocketAuthorizeRequestsCustomizer(WebSocketProperties webSocketProperties) {
        return new WebSocketAuthorizeRequestsCustomizer(webSocketProperties);
    }

    // ==================== 消息发送器相关配置 ====================
    //
    // 什么是消息发送器？
    // WebSocket 消息发送器负责将消息发送给指定的用户。在集群环境下，用户可能连接到不同的服务器节点，
    // 因此需要通过消息队列（如 Redis、RocketMQ、RabbitMQ、Kafka）来实现跨节点的消息广播。
    //
    // 使用场景举例：
    // 假设有 3 台服务器（A、B、C），用户张三连接到服务器 A，用户李四连接到服务器 B。
    // 当管理员在服务器 C 上发送一条通知给所有人时，需要通过消息队列广播到 A 和 B，
    // 这样张三和李四才能收到消息。
    //
    // 支持的发送器类型：
    // 1. local：本地发送器，仅适用于单机环境，不支持集群
    // 2. redis：基于 Redis 发布/订阅，适合中小型集群
    // 3. rocketmq：基于 RocketMQ 消息队列，适合大型分布式系统
    // 4. rabbitmq：基于 RabbitMQ 消息队列，功能丰富
    // 5. kafka：基于 Kafka 消息队列，适合高吞吐量场景
    //
    // 配置方式：在配置文件中设置 yudao.websocket.sender-type=redis（或其他类型）

    /**
     * 本地消息发送器配置
     * <p>
     * 使用场景：单机环境，不需要集群支持时使用
     * <p>
     * 工作原理：
     * 直接从本地的 SessionManager 中查找用户会话并发送消息，
     * 由于用户会话只存在于当前服务器，所以无法发送给连接到其他服务器的用户。
     * <p>
     * 优点：简单高效，无需依赖外部消息队列
     * 缺点：不支持集群环境，只能在单机部署时使用
     * <p>
     * 配置示例：
     * yudao:
     * websocket:
     * sender-type: local
     */
    @Configuration
    @ConditionalOnProperty(prefix = "yudao.websocket", name = "sender-type", havingValue = "local")
    public class LocalWebSocketMessageSenderConfiguration {

        /**
         * 创建本地消息发送器
         *
         * @param sessionManager 会话管理器，用于查找本机上的用户会话
         * @return 本地消息发送器
         */
        @Bean
        public LocalWebSocketMessageSender localWebSocketMessageSender(WebSocketSessionManager sessionManager) {
            return new LocalWebSocketMessageSender(sessionManager);
        }

    }

    /**
     * Redis 消息发送器配置
     * <p>
     * 使用场景：集群环境，需要跨服务器节点发送消息时使用
     * <p>
     * 工作原理：
     * 1. 发送消息时，先尝试从本地 SessionManager 查找用户会话
     * 2. 如果本地找不到，说明用户连接在其他服务器上，通过 Redis 发布消息
     * 3. 所有服务器节点都订阅 Redis 频道，收到消息后检查用户是否在本机
     * 4. 如果用户在本机，则通过本地会话发送消息
     * <p>
     * 优点：
     * - 实现简单，Redis 部署方便
     * - 适合中小型集群（几十台服务器以内）
     * - 消息实时性好，延迟低
     * <p>
     * 缺点：
     * - Redis 发布/订阅不保证消息可靠性，服务器重启可能丢失消息
     * - 不适合大规模集群（上百台服务器）
     * <p>
     * 配置示例：
     * yudao:
     * websocket:
     * sender-type: redis
     */
    @Configuration
    @ConditionalOnProperty(prefix = "yudao.websocket", name = "sender-type", havingValue = "redis")
    public class RedisWebSocketMessageSenderConfiguration {

        /**
         * 创建 Redis 消息发送器
         *
         * @param sessionManager  会话管理器，用于查找本机上的用户会话
         * @param redisMQTemplate Redis 消息队列模板，用于发布消息到 Redis
         * @return Redis 消息发送器
         */
        @Bean
        public RedisWebSocketMessageSender redisWebSocketMessageSender(WebSocketSessionManager sessionManager,
                                                                       RedisMQTemplate redisMQTemplate) {
            return new RedisWebSocketMessageSender(sessionManager, redisMQTemplate);
        }

        /**
         * 创建 Redis 消息消费者
         * <p>
         * 作用：订阅 Redis 频道，接收其他服务器节点发布的消息，
         * 并转发给连接在本机的用户。
         *
         * @param redisWebSocketMessageSender Redis 消息发送器
         * @return Redis 消息消费者
         */
        @Bean
        public RedisWebSocketMessageConsumer redisWebSocketMessageConsumer(
                RedisWebSocketMessageSender redisWebSocketMessageSender) {
            return new RedisWebSocketMessageConsumer(redisWebSocketMessageSender);
        }

    }

    /**
     * RocketMQ 消息发送器配置
     * <p>
     * 使用场景：大型分布式系统，需要高可靠性和消息持久化时使用
     * <p>
     * 工作原理：
     * 1. 发送消息时，先尝试从本地 SessionManager 查找用户会话
     * 2. 如果本地找不到，通过 RocketMQ 发送消息到指定 Topic
     * 3. 所有服务器节点都订阅该 Topic，收到消息后检查用户是否在本机
     * 4. 如果用户在本机，则通过本地会话发送消息
     * <p>
     * 优点：
     * - 消息可靠性高，支持消息持久化和重试机制
     * - 适合大规模集群部署（上百台服务器）
     * - 支持消息追踪和监控
     * <p>
     * 缺点：
     * - 部署复杂，需要搭建 RocketMQ 集群
     * - 消息延迟略高于 Redis（但仍在毫秒级）
     * <p>
     * 配置示例：
     * yudao:
     * websocket:
     * sender-type: rocketmq
     * sender-rocketmq:
     * topic: websocket-message  # RocketMQ Topic 名称
     */
    @Configuration
    @ConditionalOnProperty(prefix = "yudao.websocket", name = "sender-type", havingValue = "rocketmq")
    public class RocketMQWebSocketMessageSenderConfiguration {

        /**
         * 创建 RocketMQ 消息发送器
         *
         * @param sessionManager   会话管理器，用于查找本机上的用户会话
         * @param rocketMQTemplate RocketMQ 模板，用于发送消息到 RocketMQ
         * @param topic            RocketMQ Topic 名称，从配置文件中读取
         * @return RocketMQ 消息发送器
         */
        @Bean
        public RocketMQWebSocketMessageSender rocketMQWebSocketMessageSender(
                WebSocketSessionManager sessionManager, RocketMQTemplate rocketMQTemplate,
                @Value("${yudao.websocket.sender-rocketmq.topic}") String topic) {
            return new RocketMQWebSocketMessageSender(sessionManager, rocketMQTemplate, topic);
        }

        /**
         * 创建 RocketMQ 消息消费者
         * <p>
         * 作用：订阅 RocketMQ Topic，接收其他服务器节点发送的消息，
         * 并转发给连接在本机的用户。
         *
         * @param rocketMQWebSocketMessageSender RocketMQ 消息发送器
         * @return RocketMQ 消息消费者
         */
        @Bean
        public RocketMQWebSocketMessageConsumer rocketMQWebSocketMessageConsumer(
                RocketMQWebSocketMessageSender rocketMQWebSocketMessageSender) {
            return new RocketMQWebSocketMessageConsumer(rocketMQWebSocketMessageSender);
        }

    }

    /**
     * RabbitMQ 消息发送器配置
     * <p>
     * 使用场景：需要灵活的消息路由和丰富的消息队列功能时使用
     * <p>
     * 工作原理：
     * 1. 发送消息时，先尝试从本地 SessionManager 查找用户会话
     * 2. 如果本地找不到，通过 RabbitMQ Topic Exchange 发送消息
     * 3. 所有服务器节点都绑定队列到该 Exchange，收到消息后检查用户是否在本机
     * 4. 如果用户在本机，则通过本地会话发送消息
     * <p>
     * 优点：
     * - 功能丰富，支持多种消息路由模式（Topic、Fanout、Direct 等）
     * - 消息可靠性高，支持持久化和确认机制
     * - 管理界面友好，便于监控和运维
     * <p>
     * 缺点：
     * - 性能略低于 Kafka（但对于 WebSocket 场景足够用）
     * - 学习曲线较陡峭，概念较多（Exchange、Queue、Binding 等）
     * <p>
     * 配置示例：
     * yudao:
     * websocket:
     * sender-type: rabbitmq
     * sender-rabbitmq:
     * exchange: websocket.topic  # RabbitMQ Exchange 名称
     */
    @Configuration
    @ConditionalOnProperty(prefix = "yudao.websocket", name = "sender-type", havingValue = "rabbitmq")
    public class RabbitMQWebSocketMessageSenderConfiguration {

        /**
         * 创建 RabbitMQ 消息发送器
         *
         * @param sessionManager         会话管理器，用于查找本机上的用户会话
         * @param rabbitTemplate         RabbitMQ 模板，用于发送消息到 RabbitMQ
         * @param websocketTopicExchange WebSocket Topic Exchange，用于消息路由
         * @return RabbitMQ 消息发送器
         */
        @Bean
        public RabbitMQWebSocketMessageSender rabbitMQWebSocketMessageSender(
                WebSocketSessionManager sessionManager, RabbitTemplate rabbitTemplate,
                TopicExchange websocketTopicExchange) {
            return new RabbitMQWebSocketMessageSender(sessionManager, rabbitTemplate, websocketTopicExchange);
        }

        /**
         * 创建 RabbitMQ 消息消费者
         * <p>
         * 作用：监听 RabbitMQ 队列，接收其他服务器节点发送的消息，
         * 并转发给连接在本机的用户。
         *
         * @param rabbitMQWebSocketMessageSender RabbitMQ 消息发送器
         * @return RabbitMQ 消息消费者
         */
        @Bean
        public RabbitMQWebSocketMessageConsumer rabbitMQWebSocketMessageConsumer(
                RabbitMQWebSocketMessageSender rabbitMQWebSocketMessageSender) {
            return new RabbitMQWebSocketMessageConsumer(rabbitMQWebSocketMessageSender);
        }

        /**
         * 创建 Topic Exchange
         * <p>
         * Topic Exchange 是 RabbitMQ 中的一种交换机类型，支持通过路由键（Routing Key）进行消息路由。
         * 例如：可以将消息发送到 "user.123" 路由键，只有订阅了 "user.*" 或 "user.123" 的队列才会收到消息。
         *
         * @param exchange Exchange 名称，从配置文件中读取
         * @return Topic Exchange 对象
         */
        @Bean
        public TopicExchange websocketTopicExchange(@Value("${yudao.websocket.sender-rabbitmq.exchange}") String exchange) {
            return new TopicExchange(exchange,
                    true,  // durable: 是否持久化，true 表示服务器重启后 Exchange 不会丢失
                    false);  // autoDelete: 是否自动删除，false 表示没有队列绑定时也不删除 Exchange
        }

    }

    /**
     * Kafka 消息发送器配置
     * <p>
     * 使用场景：超大规模集群，需要极高吞吐量和水平扩展能力时使用
     * <p>
     * 工作原理：
     * 1. 发送消息时，先尝试从本地 SessionManager 查找用户会话
     * 2. 如果本地找不到，通过 Kafka 发送消息到指定 Topic
     * 3. 所有服务器节点都订阅该 Topic，收到消息后检查用户是否在本机
     * 4. 如果用户在本机，则通过本地会话发送消息
     * <p>
     * 优点：
     * - 吞吐量极高，可以处理百万级 TPS（每秒事务数）
     * - 水平扩展能力强，支持数千个分区
     * - 消息持久化，数据安全可靠
     * - 适合大数据场景，可以与其他大数据组件集成
     * <p>
     * 缺点：
     * - 部署和运维复杂度较高（需要 Zookeeper 或 KRaft）
     * - 消息延迟略高于 Redis（通常在几十毫秒）
     * - 功能相对单一，不如 RabbitMQ 灵活
     * <p>
     * 配置示例：
     * yudao:
     * websocket:
     * sender-type: kafka
     * sender-kafka:
     * topic: websocket-message  # Kafka Topic 名称
     */
    @Configuration
    @ConditionalOnProperty(prefix = "yudao.websocket", name = "sender-type", havingValue = "kafka")
    public class KafkaWebSocketMessageSenderConfiguration {

        /**
         * 创建 Kafka 消息发送器
         *
         * @param sessionManager 会话管理器，用于查找本机上的用户会话
         * @param kafkaTemplate  Kafka 模板，用于发送消息到 Kafka
         * @param topic          Kafka Topic 名称，从配置文件中读取
         * @return Kafka 消息发送器
         */
        @Bean
        public KafkaWebSocketMessageSender kafkaWebSocketMessageSender(
                WebSocketSessionManager sessionManager, KafkaTemplate<Object, Object> kafkaTemplate,
                @Value("${yudao.websocket.sender-kafka.topic}") String topic) {
            return new KafkaWebSocketMessageSender(sessionManager, kafkaTemplate, topic);
        }

        /**
         * 创建 Kafka 消息消费者
         * <p>
         * 作用：订阅 Kafka Topic，接收其他服务器节点发送的消息，
         * 并转发给连接在本机的用户。
         *
         * @param kafkaWebSocketMessageSender Kafka 消息发送器
         * @return Kafka 消息消费者
         */
        @Bean
        public KafkaWebSocketMessageConsumer kafkaWebSocketMessageConsumer(
                KafkaWebSocketMessageSender kafkaWebSocketMessageSender) {
            return new KafkaWebSocketMessageConsumer(kafkaWebSocketMessageSender);
        }

    }

}