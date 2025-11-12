package cn.iocoder.yudao.framework.websocket.core.sender;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.websocket.core.message.JsonWebSocketMessage;
import cn.iocoder.yudao.framework.websocket.core.session.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * WebSocket 消息发送器的抽象实现类
 * <p>
 * 作用：负责向客户端发送 WebSocket 消息
 * <p>
 * 这个类提供了向不同目标发送消息的能力：
 * 1. 向指定 Session（会话）发送
 * 2. 向指定用户发送（根据用户类型和用户ID）
 * 3. 向指定用户类型的所有用户发送（广播）
 *
 * @author 芋道源码
 */
@Slf4j // Lombok 注解，自动生成日志对象 log
@RequiredArgsConstructor // Lombok 注解，自动生成包含 final 字段的构造方法
public abstract class AbstractWebSocketMessageSender implements WebSocketMessageSender {

    /**
     * WebSocket 会话管理器
     * 作用：管理所有在线的 WebSocket 连接会话
     */
    private final WebSocketSessionManager sessionManager;

    /**
     * 发送消息给指定用户类型和用户ID的用户
     * <p>
     * 使用场景举例：
     * - 给某个后台管理员（userType=1, userId=100）发送系统通知
     * - 给某个会员用户（userType=2, userId=200）发送订单更新消息
     *
     * @param userType       用户类型（例如：1=管理员，2=会员）
     * @param userId         用户ID（用户的唯一标识）
     * @param messageType    消息类型（例如："order_update", "system_notice"）
     * @param messageContent 消息内容（JSON 字符串或普通文本）
     */
    @Override
    public void send(Integer userType, Long userId, String messageType, String messageContent) {
        // 调用核心发送方法，sessionId 传 null 表示不按 sessionId 查找
        send(null, userType, userId, messageType, messageContent);
    }

    /**
     * 向指定用户类型的所有用户发送消息（广播）
     * <p>
     * 使用场景举例：
     * - 向所有在线的管理员（userType=1）广播系统维护通知
     * - 向所有在线的会员用户（userType=2）广播促销活动消息
     * <p>
     * 注意：这会给该用户类型下的所有在线用户发送消息
     *
     * @param userType       用户类型（例如：1=管理员，2=会员）
     * @param messageType    消息类型（例如："system_broadcast", "promotion"）
     * @param messageContent 消息内容（JSON 字符串或普通文本）
     */
    @Override
    public void send(Integer userType, String messageType, String messageContent) {
        // 调用核心发送方法，userId 传 null 表示不指定具体用户，向该类型所有用户发送
        send(null, userType, null, messageType, messageContent);
    }

    /**
     * 向指定会话ID发送消息
     * <p>
     * 使用场景举例：
     * - 向特定的 WebSocket 连接会话发送消息
     * - 适用于知道具体会话ID但不关心用户信息的场景
     * <p>
     * 会话ID（sessionId）：每个 WebSocket 连接都有唯一的会话ID
     *
     * @param sessionId      会话ID（WebSocket 连接的唯一标识）
     * @param messageType    消息类型（例如："chat_message", "notification"）
     * @param messageContent 消息内容（JSON 字符串或普通文本）
     */
    @Override
    public void send(String sessionId, String messageType, String messageContent) {
        // 调用核心发送方法，userType 和 userId 传 null 表示只按 sessionId 查找
        send(sessionId, null, null, messageType, messageContent);
    }

    /**
     * 核心发送方法：根据不同的条件查找会话并发送消息
     * <p>
     * 这是所有 send 方法最终调用的核心方法，负责：
     * 1. 根据 sessionId、userType、userId 查找对应的 WebSocket 会话
     * 2. 调用 doSend 方法执行实际的消息发送
     * <p>
     * 查找优先级：
     * - 如果 sessionId 不为空，只查找这个会话
     * - 如果 userType 和 userId 都不为空，查找这个用户的所有会话
     * - 如果只有 userType 不为空，查找这个用户类型的所有会话
     *
     * @param sessionId      会话编号（可选）
     * @param userType       用户类型（可选）
     * @param userId         用户编号（可选）
     * @param messageType    消息类型
     * @param messageContent 消息内容
     */
    public void send(String sessionId, Integer userType, Long userId, String messageType, String messageContent) {
        // 1. 获得 Session 列表（根据不同条件查找会话）
        List<WebSocketSession> sessions = Collections.emptyList(); // 初始化为空列表

        // 情况1：按会话ID查找（优先级最高）
        if (StrUtil.isNotEmpty(sessionId)) {
            WebSocketSession session = sessionManager.getSession(sessionId);
            if (session != null) {
                sessions = Collections.singletonList(session); // 找到单个会话，放入列表
            }
        }
        // 情况2：按用户类型和用户ID查找
        else if (userType != null && userId != null) {
            sessions = (List<WebSocketSession>) sessionManager.getSessionList(userType, userId);
        }
        // 情况3：按用户类型查找（广播给该类型的所有用户）
        else if (userType != null) {
            sessions = (List<WebSocketSession>) sessionManager.getSessionList(userType);
        }

        // 如果没有找到任何会话，输出调试日志
        if (CollUtil.isEmpty(sessions)) {
            if (log.isDebugEnabled()) {
                log.debug("[send][sessionId({}) userType({}) userId({}) messageType({}) messageContent({}) 未匹配到会话]",
                        sessionId, userType, userId, messageType, messageContent);
            }
        }

        // 2. 执行发送（调用 doSend 方法向找到的所有会话发送消息）
        doSend(sessions, messageType, messageContent);
    }

    /**
     * 执行消息发送的具体实现
     * <p>
     * 这个方法负责：
     * 1. 将消息类型和内容封装成 JSON 格式
     * 2. 遍历所有会话，向每个会话发送消息
     * 3. 处理发送过程中的异常情况
     * <p>
     * 发送流程：
     * - 检查会话是否存在
     * - 检查会话是否已关闭
     * - 执行实际发送
     * - 记录发送结果（成功或失败）
     *
     * @param sessions       要发送消息的会话列表（可能包含多个会话）
     * @param messageType    消息类型（例如："order_update", "system_notice"）
     * @param messageContent 消息内容（可以是 JSON 字符串或普通文本）
     */
    public void doSend(Collection<WebSocketSession> sessions, String messageType, String messageContent) {
        // 将消息封装成 JSON 格式
        // 例如：{"type":"order_update","content":"{\"orderId\":123}"}
        JsonWebSocketMessage message = new JsonWebSocketMessage().setType(messageType).setContent(messageContent);
        String payload = JsonUtils.toJsonString(message); // 序列化为 JSON 字符串

        // 遍历所有会话，向每个会话发送消息
        sessions.forEach(session -> {
            // 1. 各种校验，保证 Session 可以被发送

            // 校验1：会话对象不能为空
            if (session == null) {
                log.error("[doSend][session 为空, message({})]", message);
                return; // 跳过本次循环，继续处理下一个会话
            }

            // 校验2：会话必须处于打开状态（连接未断开）
            if (!session.isOpen()) {
                log.error("[doSend][session({}) 已关闭, message({})]", session.getId(), message);
                return; // 跳过本次循环，继续处理下一个会话
            }

            // 2. 执行发送
            try {
                // 创建文本消息并发送
                session.sendMessage(new TextMessage(payload));
                log.info("[doSend][session({}) 发送消息成功，message({})]", session.getId(), message);
            } catch (IOException ex) {
                // 捕获发送失败的异常（例如：网络中断、连接超时等）
                log.error("[doSend][session({}) 发送消息失败，message({})]", session.getId(), message, ex);
            }
        });
    }

}
