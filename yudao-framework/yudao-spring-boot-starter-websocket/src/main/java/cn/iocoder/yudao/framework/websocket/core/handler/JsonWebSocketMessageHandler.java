package cn.iocoder.yudao.framework.websocket.core.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.TypeUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.framework.websocket.core.listener.WebSocketMessageListener;
import cn.iocoder.yudao.framework.websocket.core.message.JsonWebSocketMessage;
import cn.iocoder.yudao.framework.websocket.core.util.WebSocketFrameworkUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * JSON 格式 {@link WebSocketHandler} 实现类
 * <p>
 * 这是一个处理 WebSocket 文本消息的处理器，专门用于处理 JSON 格式的消息。
 * 工作原理：
 * 1. 接收客户端发送的 JSON 格式文本消息
 * 2. 根据消息中的 type 字段，找到对应的监听器
 * 3. 将消息分发给对应的监听器进行处理
 * <p>
 * 基于 {@link JsonWebSocketMessage#getType()} 消息类型，调度到对应的 {@link WebSocketMessageListener} 监听器。
 *
 * @author 芋道源码
 */
@Slf4j // Lombok 注解，自动生成日志对象 log，用于记录日志
public class JsonWebSocketMessageHandler extends TextWebSocketHandler {

    /**
     * 消息类型与监听器的映射表
     * <p>
     * key: 消息类型（String），例如 "chat"、"notification" 等
     * value: 对应的消息监听器，用于处理该类型的消息
     * <p>
     * 例如：{"chat" -> ChatMessageListener, "notification" -> NotificationListener}
     * 当收到 type="chat" 的消息时，就会交给 ChatMessageListener 处理
     */
    private final Map<String, WebSocketMessageListener<Object>> listeners = new HashMap<>();

    /**
     * 构造方法：初始化消息处理器
     *
     * @param listenersList 所有的消息监听器列表，由 Spring 容器自动注入
     *                      <p>
     *                      工作流程：
     *                      1. 遍历传入的所有监听器
     *                      2. 获取每个监听器支持的消息类型（通过 listener.getType()）
     *                      3. 将消息类型和监听器的对应关系存入 Map 中
     *                      <p>
     *                      示例：假设有 ChatMessageListener（type="chat"）和 NotificationListener（type="notification"）
     *                      则 listeners 会变成：{"chat" -> ChatMessageListener实例, "notification" -> NotificationListener实例}
     */
    @SuppressWarnings({"rawtypes", "unchecked"}) // 抑制泛型相关的警告，因为这里需要处理不同类型的监听器
    public JsonWebSocketMessageHandler(List<? extends WebSocketMessageListener> listenersList) {
        // 使用 forEach 遍历每个监听器，并将其类型和实例存入 listeners Map
        listenersList.forEach((Consumer<WebSocketMessageListener>)
                listener -> listeners.put(listener.getType(), listener));
    }

    /**
     * 处理接收到的文本消息（核心方法）
     *
     * @param session WebSocket 会话对象，代表一个客户端连接
     * @param message 客户端发送的文本消息对象
     * @throws Exception 处理过程中可能抛出的异常
     *                   <p>
     *                   处理流程：
     *                   1. 过滤空消息
     *                   2. 处理心跳消息（ping/pong）
     *                   3. 解析 JSON 消息，获取消息类型
     *                   4. 根据消息类型找到对应的监听器
     *                   5. 在租户上下文中调用监听器处理消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // ========== 第一步：消息预处理 ==========

        // 1.1 空消息，跳过
        // 如果消息长度为 0，说明是空消息，直接返回不处理
        if (message.getPayloadLength() == 0) {
            return;
        }

        // 1.2 处理心跳消息
        // 客户端定期发送 "ping" 消息以保持连接，服务器回复 "pong"
        // 这样可以防止连接因长时间无数据传输而被关闭
        if (message.getPayloadLength() == 4 && Objects.equals(message.getPayload(), "ping")) {
            session.sendMessage(new TextMessage("pong")); // 回复 pong 消息
            return;
        }

        // ========== 第二步：解析和处理业务消息 ==========

        try {
            // 2.1 解析 JSON 消息
            // 将接收到的 JSON 字符串解析为 JsonWebSocketMessage 对象
            // JsonWebSocketMessage 包含两个主要字段：type（消息类型）和 content（消息内容）
            JsonWebSocketMessage jsonMessage = JsonUtils.parseObject(message.getPayload(), JsonWebSocketMessage.class);

            // 2.1.1 检查消息是否解析成功
            if (jsonMessage == null) {
                log.error("[handleTextMessage][session({}) message({}) 解析为空]", session.getId(), message.getPayload());
                return;
            }

            // 2.1.2 检查消息类型是否存在
            // type 字段用于标识消息的类型，例如 "chat"、"notification" 等
            if (StrUtil.isEmpty(jsonMessage.getType())) {
                log.error("[handleTextMessage][session({}) message({}) 类型为空]", session.getId(), message.getPayload());
                return;
            }

            // 2.2 根据消息类型获取对应的监听器
            // 从 listeners Map 中查找能处理该类型消息的监听器
            WebSocketMessageListener<Object> messageListener = listeners.get(jsonMessage.getType());
            if (messageListener == null) {
                // 如果找不到对应的监听器，说明该消息类型没有被注册，记录错误日志
                log.error("[handleTextMessage][session({}) message({}) 监听器为空]", session.getId(), message.getPayload());
                return;
            }

            // 2.3 处理消息
            // 2.3.1 获取监听器期望的消息类型（通过反射获取泛型类型）
            // 例如：如果监听器是 ChatMessageListener implements WebSocketMessageListener<ChatMessage>
            //      那么 type 就是 ChatMessage.class
            Type type = TypeUtil.getTypeArgument(messageListener.getClass(), 0);

            // 2.3.2 将 JSON 内容解析为具体的消息对象
            // 例如：将 content 字段的 JSON 解析为 ChatMessage 对象
            Object messageObj = JsonUtils.parseObject(jsonMessage.getContent(), type);

            // 2.3.3 获取当前会话所属的租户 ID
            // 多租户系统中，每个客户端连接都属于某个租户
            Long tenantId = WebSocketFrameworkUtils.getTenantId(session);

            // 2.3.4 在租户上下文中执行消息处理
            // TenantUtils.execute 确保在处理消息时，系统知道当前是哪个租户的请求
            // 这样可以正确地进行数据隔离和权限控制
            TenantUtils.execute(tenantId, () -> messageListener.onMessage(session, messageObj));

        } catch (Throwable ex) {
            // 捕获所有异常，防止单个消息处理失败影响整个 WebSocket 连接
            // 记录错误日志，方便排查问题
            log.error("[handleTextMessage][session({}) message({}) 处理异常]", session.getId(), message.getPayload(), ex);
        }
    }

}
