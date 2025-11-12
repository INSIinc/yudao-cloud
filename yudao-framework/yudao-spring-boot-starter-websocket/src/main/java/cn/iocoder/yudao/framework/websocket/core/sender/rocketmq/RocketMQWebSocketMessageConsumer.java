package cn.iocoder.yudao.framework.websocket.core.sender.rocketmq;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;

/**
 * RocketMQ WebSocket 消息消费者
 * <p>
 * 作用说明：
 * 这是一个 RocketMQ 消息的消费者类，负责接收并处理 WebSocket 消息。
 * <p>
 * 工作流程：
 * 1. 当其他服务通过 RocketMQ 发送 WebSocket 消息时
 * 2. 这个消费者会收到消息
 * 3. 然后调用本地的 WebSocket 发送器，把消息真正推送给连接在本实例的用户
 * <p>
 * 使用场景：
 * 在分布式系统中，用户可能连接到任意一台服务器。
 * 当我们需要给某个用户发送消息时，我们不知道他连接在哪台服务器上，
 * 所以通过 RocketMQ 广播模式，让所有服务器实例都收到这条消息，
 * 每个实例检查该用户是否连接在自己这里，如果是就推送给他。
 *
 * @author 芋道源码
 */
// @RocketMQMessageListener 注解：声明这是一个 RocketMQ 消息监听器
// 参数说明：
// - topic: 监听的消息主题，从配置文件读取 yudao.websocket.sender-rocketmq.topic
// - consumerGroup: 消费者组名称，从配置文件读取 yudao.websocket.sender-rocketmq.consumer-group
// - messageModel: 消息模式，这里使用 BROADCASTING（广播模式）
//   * 广播模式：每个消费者实例都会收到消息（适用于需要所有服务器都处理的场景）
//   * 集群模式：消息只会被一个消费者实例消费（适用于负载均衡场景）
@RocketMQMessageListener(
        topic = "${yudao.websocket.sender-rocketmq.topic}",
        consumerGroup = "${yudao.websocket.sender-rocketmq.consumer-group}",
        messageModel = MessageModel.BROADCASTING // 使用广播模式，确保每个服务实例都能收到消息
)
// @RequiredArgsConstructor：Lombok 注解，自动生成包含 final 字段的构造函数
@RequiredArgsConstructor
// 实现 RocketMQListener 接口：指定消息类型为 RocketMQWebSocketMessage
public class RocketMQWebSocketMessageConsumer implements RocketMQListener<RocketMQWebSocketMessage> {

    /**
     * WebSocket 消息发送器
     * 负责将消息真正推送给连接在本服务实例上的 WebSocket 客户端
     * <p>
     * final 关键字：
     * - 表示这个字段在对象创建后不能被修改
     * - 配合 @RequiredArgsConstructor 注解，会自动通过构造函数注入
     */
    private final RocketMQWebSocketMessageSender rocketMQWebSocketMessageSender;

    /**
     * 消息处理方法
     * <p>
     * 当 RocketMQ 收到消息时，会自动调用这个方法
     *
     * @param message 接收到的 WebSocket 消息对象，包含以下信息：
     *                - sessionId: WebSocket 会话 ID（可选，用于指定特定会话）
     *                - userType: 用户类型（例如：管理员、普通用户等）
     *                - userId: 用户 ID（指定要发送给哪个用户）
     *                - messageType: 消息类型（例如：通知、聊天消息等）
     *                - messageContent: 消息内容（实际要发送的数据）
     */
    @Override
    public void onMessage(RocketMQWebSocketMessage message) {
        // 调用本地的 WebSocket 发送器，将消息推送给目标用户
        // 如果目标用户连接在本服务实例上，就会成功推送
        // 如果目标用户不在本实例上，send 方法内部会自动忽略（因为找不到对应的 WebSocket 连接）
        rocketMQWebSocketMessageSender.send(
                message.getSessionId(),      // 会话 ID
                message.getUserType(),        // 用户类型
                message.getUserId(),          // 用户 ID
                message.getMessageType(),     // 消息类型
                message.getMessageContent()   // 消息内容
        );
    }

}
