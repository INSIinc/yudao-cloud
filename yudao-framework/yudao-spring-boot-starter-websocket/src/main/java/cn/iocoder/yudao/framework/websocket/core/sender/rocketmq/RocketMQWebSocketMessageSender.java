package cn.iocoder.yudao.framework.websocket.core.sender.rocketmq;

import cn.iocoder.yudao.framework.websocket.core.sender.AbstractWebSocketMessageSender;
import cn.iocoder.yudao.framework.websocket.core.sender.WebSocketMessageSender;
import cn.iocoder.yudao.framework.websocket.core.session.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

/**
 * 基于 RocketMQ 的 {@link WebSocketMessageSender} 实现类
 * <p>
 * 作用说明：
 * 这个类负责通过 RocketMQ 消息队列发送 WebSocket 消息。
 * 在分布式系统中，多个服务器实例可能都运行着 WebSocket 服务，
 * 当需要给某个用户发送消息时，我们不知道用户连接在哪个服务器上，
 * 所以通过 RocketMQ 将消息广播到所有服务器，由各服务器自行判断是否需要推送。
 * <p>
 * 使用场景：
 * - 集群环境下的 WebSocket 消息推送
 * - 需要跨服务器通知用户的场景
 * <p>
 * 工作流程：
 * 1. 调用 send() 方法发送消息
 * 2. 消息被封装成 RocketMQWebSocketMessage 对象
 * 3. 通过 RocketMQ 发送到指定的 topic（主题）
 * 4. 所有订阅该 topic 的服务器都会收到消息
 * 5. 各服务器根据 sessionId 或 userId 判断是否需要推送给客户端
 *
 * @author 芋道源码
 */
@Slf4j // Lombok 注解：自动生成日志对象 log，用于记录日志
public class RocketMQWebSocketMessageSender extends AbstractWebSocketMessageSender {

    /**
     * RocketMQ 模板对象
     * 这是 Spring 提供的 RocketMQ 操作工具类，用于发送和接收消息
     * 类似于 JdbcTemplate 操作数据库，RedisTemplate 操作 Redis
     */
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * RocketMQ 的主题（Topic）名称
     * Topic 是 RocketMQ 中消息的分类单位，类似于邮局的不同邮箱
     * 发送者将消息发送到指定 topic，订阅者从 topic 接收消息
     * 例如：websocket-message-topic
     */
    private final String topic;

    /**
     * 构造方法 - 初始化 RocketMQ WebSocket 消息发送器
     *
     * @param sessionManager   WebSocket 会话管理器
     *                         用于管理所有的 WebSocket 连接会话，可以通过它获取用户的连接信息
     * @param rocketMQTemplate RocketMQ 操作模板
     *                         Spring 提供的 RocketMQ 工具类，用于发送消息到 RocketMQ
     * @param topic            RocketMQ 主题名称
     *                         消息将被发送到这个 topic，所有订阅此 topic 的服务都会收到消息
     */
    public RocketMQWebSocketMessageSender(WebSocketSessionManager sessionManager,
                                          RocketMQTemplate rocketMQTemplate,
                                          String topic) {
        super(sessionManager); // 调用父类构造方法，传入会话管理器
        this.rocketMQTemplate = rocketMQTemplate; // 保存 RocketMQ 模板对象
        this.topic = topic; // 保存主题名称
    }

    /**
     * 发送消息给指定用户（根据用户类型和用户ID）
     * <p>
     * 使用场景：给特定的某个用户发送消息
     * 例如：给 userId=100 的管理员用户发送一条系统通知
     * <p>
     * 实现原理：
     * 通过 RocketMQ 广播消息到所有服务器，每个服务器检查该用户是否连接在本服务器上，
     * 如果是，则通过 WebSocket 推送给客户端
     *
     * @param userType       用户类型
     *                       例如：1-管理员, 2-普通用户, 3-商家
     *                       用于区分不同类型的用户，因为不同类型用户的 userId 可能重复
     * @param userId         用户编号
     *                       要发送消息的目标用户ID
     * @param messageType    消息类型
     *                       前端根据这个类型做不同的处理，例如：notice-通知, alert-警告, message-普通消息
     * @param messageContent 消息内容
     *                       实际要发送的消息体，通常是 JSON 字符串
     */
    @Override
    public void send(Integer userType, Long userId, String messageType, String messageContent) {
        // 调用内部方法发送消息，sessionId 传 null 表示根据 userId 来查找用户
        sendRocketMQMessage(null, userId, userType, messageType, messageContent);
    }

    /**
     * 广播消息给某一类型的所有用户
     * <p>
     * 使用场景：给某种类型的所有在线用户发送消息
     * 例如：给所有管理员发送系统维护通知，给所有商家发送平台公告
     * <p>
     * 实现原理：
     * 通过 RocketMQ 广播消息到所有服务器，每个服务器找出该类型的所有在线用户，
     * 然后逐个通过 WebSocket 推送消息
     *
     * @param userType       用户类型
     *                       例如：1-管理员, 2-普通用户, 3-商家
     *                       消息会发送给该类型的所有在线用户
     * @param messageType    消息类型
     *                       前端根据这个类型做不同的处理，例如：notice-通知, alert-警告
     * @param messageContent 消息内容
     *                       实际要发送的消息体，通常是 JSON 字符串
     */
    @Override
    public void send(Integer userType, String messageType, String messageContent) {
        // 调用内部方法发送消息，sessionId 和 userId 都传 null，表示广播给该类型的所有用户
        sendRocketMQMessage(null, null, userType, messageType, messageContent);
    }

    /**
     * 发送消息给指定的 WebSocket 会话
     * <p>
     * 使用场景：当你已经知道 WebSocket 的会话 ID 时，直接给这个会话发消息
     * 例如：用户从多个设备登录，你想给某个特定设备发消息（每个设备对应一个 session）
     * <p>
     * 实现原理：
     * 通过 RocketMQ 广播消息到所有服务器，每个服务器检查是否持有该 sessionId 的连接，
     * 如果有，则通过 WebSocket 推送消息
     *
     * @param sessionId      Session 编号（会话ID）
     *                       每个 WebSocket 连接都有一个唯一的 sessionId
     *                       相当于这个连接的身份证号
     * @param messageType    消息类型
     *                       前端根据这个类型做不同的处理
     * @param messageContent 消息内容
     *                       实际要发送的消息体
     */
    @Override
    public void send(String sessionId, String messageType, String messageContent) {
        // 调用内部方法发送消息，userId 和 userType 传 null，表示直接根据 sessionId 推送
        sendRocketMQMessage(sessionId, null, null, messageType, messageContent);
    }

    /**
     * 通过 RocketMQ 广播消息（内部私有方法）
     * <p>
     * 这是一个核心方法，所有的 send() 方法最终都会调用这个方法
     * <p>
     * 工作流程：
     * 1. 将所有参数封装成 RocketMQWebSocketMessage 对象
     * 2. 使用 rocketMQTemplate.syncSend() 同步发送消息到 RocketMQ
     * 3. RocketMQ 会将消息分发给所有订阅了该 topic 的服务器
     * 4. 各服务器收到消息后，会根据参数判断是否需要推送给本地的 WebSocket 客户端
     * <p>
     * 参数说明：
     * 三个参数（sessionId, userId, userType）用于确定消息接收者：
     * - 如果 sessionId 不为空：表示发给特定的 WebSocket 会话
     * - 如果 userId 和 userType 不为空：表示发给特定的用户
     * - 如果只有 userType 不为空：表示发给该类型的所有用户
     *
     * @param sessionId      Session 编号（会话ID），可以为 null
     * @param userId         用户编号，可以为 null
     * @param userType       用户类型，可以为 null
     * @param messageType    消息类型，不能为空
     * @param messageContent 消息内容，不能为空
     */
    private void sendRocketMQMessage(String sessionId, Long userId, Integer userType,
                                     String messageType, String messageContent) {
        // 创建 RocketMQ 消息对象，并设置所有属性
        // 使用了链式调用（Builder 模式），让代码更简洁易读
        RocketMQWebSocketMessage mqMessage = new RocketMQWebSocketMessage()
                .setSessionId(sessionId)      // 设置会话ID
                .setUserId(userId)            // 设置用户ID
                .setUserType(userType)        // 设置用户类型
                .setMessageType(messageType)  // 设置消息类型
                .setMessageContent(messageContent); // 设置消息内容

        // 同步发送消息到 RocketMQ
        // syncSend 表示同步发送，方法会等待 RocketMQ 确认收到消息后才返回
        // 如果需要更高性能，可以使用 asyncSend 异步发送（但可能丢失消息）
        rocketMQTemplate.syncSend(topic, mqMessage);
    }

}
