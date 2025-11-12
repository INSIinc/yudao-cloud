package cn.iocoder.yudao.framework.websocket.core.session;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * WebSocket 会话处理器装饰器
 * <p>
 * 这是一个装饰器模式的实现，用于增强 {@link WebSocketHandler} 的功能
 * <p>
 * 主要功能说明：
 * 1. 会话管理：当 WebSocket 连接建立或关闭时，使用 {@link #sessionManager} 统一管理所有的会话
 * 2. 并发支持：将普通的 {@link WebSocketSession} 包装成支持并发操作的会话，防止多线程同时发送消息时出现问题
 * <p>
 * 使用场景：
 * - 当系统需要向多个客户端同时推送消息时，需要管理所有的 WebSocket 会话
 * - 当多个线程需要向同一个客户端发送消息时，需要并发安全的会话对象
 *
 * @author 芋道源码
 */
public class WebSocketSessionHandlerDecorator extends WebSocketHandlerDecorator {

    /**
     * 发送消息的时间限制：5秒（5000毫秒）
     * <p>
     * 含义：如果发送一条消息超过5秒还没发送完成，就认为发送超时
     * 作用：防止因为网络慢或消息太大导致发送线程一直阻塞
     */
    private static final Integer SEND_TIME_LIMIT = 1000 * 5;

    /**
     * 发送消息的缓冲区大小限制：100KB（102400字节）
     * <p>
     * 含义：等待发送的消息最多可以缓存100KB
     * 作用：当消息发送较慢时，新消息会先放入缓冲区，如果缓冲区满了就不再接收新消息
     * 注意：这里写的是"上线"应该是"上限"的笔误
     */
    private static final Integer BUFFER_SIZE_LIMIT = 1024 * 100;

    /**
     * WebSocket 会话管理器
     * <p>
     * 作用：统一管理所有的 WebSocket 会话，提供以下功能：
     * - 保存所有在线的会话
     * - 根据用户ID或其他条件查找会话
     * - 向指定用户或所有用户发送消息
     */
    private final WebSocketSessionManager sessionManager;

    /**
     * 构造函数
     *
     * @param delegate       被装饰的原始 WebSocketHandler，实际处理 WebSocket 消息的对象
     * @param sessionManager 会话管理器，用于统一管理所有的 WebSocket 会话
     */
    public WebSocketSessionHandlerDecorator(WebSocketHandler delegate,
                                            WebSocketSessionManager sessionManager) {
        super(delegate); // 调用父类构造函数，保存原始的 handler
        this.sessionManager = sessionManager;
    }

    /**
     * 当 WebSocket 连接建立成功后调用此方法
     * <p>
     * 执行时机：客户端与服务器完成 WebSocket 握手后
     * <p>
     * 处理流程：
     * 1. 将普通的 session 包装成支持并发的 session
     * 2. 将包装后的 session 添加到会话管理器中
     *
     * @param session 新建立的 WebSocket 会话对象
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 步骤1：包装 session 使其支持并发操作
        // 原因：多个线程可能同时向同一个客户端发送消息，需要保证线程安全
        // ConcurrentWebSocketSessionDecorator 内部使用了锁机制来保证并发安全
        // 参考资料：https://blog.csdn.net/abu935009066/article/details/131218149
        session = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT, BUFFER_SIZE_LIMIT);

        // 步骤2：将会话添加到管理器中
        // 作用：保存这个会话，以便后续可以通过管理器找到它并发送消息
        sessionManager.addSession(session);
    }

    /**
     * 当 WebSocket 连接关闭后调用此方法
     * <p>
     * 执行时机：
     * - 客户端主动断开连接
     * - 服务器主动关闭连接
     * - 网络异常导致连接断开
     * <p>
     * 处理流程：
     * 从会话管理器中移除这个会话，释放资源
     *
     * @param session     被关闭的 WebSocket 会话对象
     * @param closeStatus 关闭状态，包含关闭原因（如正常关闭、异常关闭等）
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        // 从管理器中移除会话
        // 作用：避免向已关闭的连接发送消息，防止内存泄漏
        sessionManager.removeSession(session);
    }

}
