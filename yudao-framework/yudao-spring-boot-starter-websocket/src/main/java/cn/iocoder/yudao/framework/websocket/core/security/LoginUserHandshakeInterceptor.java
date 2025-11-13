package cn.iocoder.yudao.framework.websocket.core.security;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.filter.TokenAuthenticationFilter;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.websocket.core.util.WebSocketFrameworkUtils;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 登录用户的 {@link HandshakeInterceptor} 实现类
 * （WebSocket 握手拦截器，用于在建立连接时验证用户身份）
 * <p>
 * 【工作流程说明】：
 * 1. 前端连接 websocket 时，会通过拼接 ?token={token} 到 ws:// 连接后，这样它可以被 {@link TokenAuthenticationFilter} 所认证通过
 * 例如：ws://localhost:8080/websocket?token=abc123
 * 这样 Token 过滤器会先验证用户身份，验证通过后才会进入握手阶段
 * <p>
 * 2. {@link LoginUserHandshakeInterceptor} 负责把 {@link LoginUser} 添加到 {@link WebSocketSession} 中
 * 在握手成功后，将登录用户信息保存到 WebSocket 会话中，方便后续消息处理时使用
 * <p>
 * 【使用场景】：
 * - 实时聊天：确保只有登录用户才能建立 WebSocket 连接发送消息
 * - 消息推送：服务端需要知道给哪个用户推送消息
 * - 在线状态：记录哪些用户在线
 *
 * @author 芋道源码
 */
public class LoginUserHandshakeInterceptor implements HandshakeInterceptor {

    /**
     * WebSocket 握手之前执行的方法
     * （在客户端和服务端建立 WebSocket 连接之前调用，用于做一些预处理工作）
     *
     * @param request    HTTP 请求对象，包含客户端发送的握手请求信息（如 URL、请求头等）
     * @param response   HTTP 响应对象，用于向客户端返回握手响应
     * @param wsHandler  WebSocket 处理器，负责处理 WebSocket 消息的核心组件
     * @param attributes WebSocket 会话属性集合，可以在这里存储自定义数据，后续在消息处理时可以获取
     * @return 返回 true 表示允许继续握手，返回 false 则拒绝握手（连接会被断开）
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 1. 从 Spring Security 上下文中获取当前登录用户信息
        //    注意：此时用户已经通过 TokenAuthenticationFilter 验证过了
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();

        // 2. 如果用户已登录，则将用户信息保存到 WebSocket 会话属性中
        //    这样在后续的消息处理中，就可以通过 WebSocketSession 获取到用户信息
        if (loginUser != null) {
            WebSocketFrameworkUtils.setLoginUser(loginUser, attributes);
        }

        // 3. 返回 true，允许握手继续进行
        //    即使 loginUser 为 null 也返回 true，因为前面的 Token 过滤器已经做过验证了
        //    如果需要强制要求登录，可以在这里返回 false
        return true;
    }

    /**
     * WebSocket 握手之后执行的方法
     * （在握手完成后调用，无论成功还是失败都会执行）
     *
     * @param request   HTTP 请求对象
     * @param response  HTTP 响应对象
     * @param wsHandler WebSocket 处理器
     * @param exception 如果握手过程中发生异常，该参数会包含异常信息；如果握手成功，则为 null
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手完成后不需要做任何处理
        // 如果需要记录日志或做其他后置处理，可以在这里实现
    }

}
