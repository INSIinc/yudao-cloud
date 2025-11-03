package cn.iocoder.yudao.gateway.util;

import cn.hutool.core.map.MapUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.gateway.filter.security.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 安全服务工具类
 * <p>
 * 该工具类用于在 Spring WebFlux 网关环境中处理用户认证和授权相关操作，
 * 包括从请求中提取 Token、设置/获取当前登录用户信息、在请求头中传递用户信息等。
 * <p>
 * 此类是从 yudao-spring-boot-starter-security 模块的 SecurityFrameworkUtils 类复制而来，
 * 专用于网关（Gateway）层，以支持无状态认证（如 JWT）和用户上下文传递。
 *
 * @author 芋道源码
 */
@Slf4j
public class SecurityFrameworkUtils {

    /** HTTP 请求头中用于携带认证信息的字段名 */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /** Bearer Token 的固定前缀 */
    private static final String AUTHORIZATION_BEARER = "Bearer";

    /**
     * 自定义请求头名称，用于在网关内部向下游服务传递已认证的用户信息。
     * 该头的值为经过 URL 编码的 JSON 字符串，表示 {@link LoginUser} 对象。
     */
    private static final String LOGIN_USER_HEADER = "login-user";

    /** 保存在 ServerWebExchange 属性中的当前登录用户 ID 的键 */
    private static final String LOGIN_USER_ID_ATTR = "login-user-id";

    /** 保存在 ServerWebExchange 属性中的当前登录用户类型的键 */
    private static final String LOGIN_USER_TYPE_ATTR = "login-user-type";

    // 私有构造函数，防止实例化
    private SecurityFrameworkUtils() {}

    /**
     * 从当前请求中提取 Bearer Token。
     * <p>
     * 该方法会从请求头 "Authorization" 中查找以 "Bearer " 开头的值，
     * 并返回其后的 Token 字符串（已去除前后空格）。
     * <p>
     * 若请求头不存在或格式不正确，则返回 null。
     *
     * @param exchange 当前的 {@link ServerWebExchange}，包含 HTTP 请求信息
     * @return 提取出的 Token 字符串；若无法提取则返回 null
     */
    public static String obtainAuthorization(ServerWebExchange exchange) {
        // 从请求头中获取 Authorization 字段
        String authorization = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authorization)) {
            return null; // 无 Authorization 头
        }
        // 检查是否以 "Bearer " 开头（注意：后面有一个空格）
        int index = authorization.indexOf(AUTHORIZATION_BEARER + " ");
        if (index == -1) { // 未找到合法的 Bearer 前缀
            return null;
        }
        // 截取 "Bearer " 之后的部分作为 Token
        return authorization.substring(index + 7).trim();
    }

    /**
     * 将登录用户信息保存到当前请求的上下文（ServerWebExchange 的 attributes）中。
     * <p>
     * 后续可以通过 {@link #getLoginUserId(ServerWebExchange)} 和
     * {@link #getLoginUserType(ServerWebExchange)} 方法读取这些信息。
     * <p>
     * 该方法通常在认证成功后调用，用于在当前请求生命周期内缓存用户身份。
     *
     * @param exchange 当前请求上下文
     * @param user 已认证的登录用户对象
     */
    public static void setLoginUser(ServerWebExchange exchange, LoginUser user) {
        exchange.getAttributes().put(LOGIN_USER_ID_ATTR, user.getId());
        exchange.getAttributes().put(LOGIN_USER_TYPE_ATTR, user.getUserType());
    }

    /**
     * 从请求中移除自定义的 "login-user" 请求头。
     * <p>
     * 网关在将用户信息通过 "login-user" 头传递给下游服务后，
     * 为避免信息泄露或重复传递，应主动移除该头。
     * <p>
     * 此方法通过构建新的 {@link ServerHttpRequest} 实现头的移除，
     * 并返回更新后的 {@link ServerWebExchange}。
     *
     * @param exchange 原始请求上下文
     * @return 移除了 "login-user" 头的新请求上下文；若原本无该头则直接返回原 exchange
     */
    public static ServerWebExchange removeLoginUser(ServerWebExchange exchange) {
        // 如果请求中不包含 login-user 头，直接返回原 exchange
        if (!exchange.getRequest().getHeaders().containsKey(LOGIN_USER_HEADER)) {
            return exchange;
        }
        // 使用 mutate() 构建新请求，并移除 login-user 头
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(httpHeaders -> httpHeaders.remove(LOGIN_USER_HEADER))
                .build();
        // 构建并返回新的 exchange
        return exchange.mutate().request(request).build();
    }

    /**
     * 从当前请求上下文中获取已认证用户的 ID。
     * <p>
     * 该值由 {@link #setLoginUser(ServerWebExchange, LoginUser)} 方法设置。
     *
     * @param exchange 当前请求上下文
     * @return 用户 ID（Long 类型）；若未设置或类型不匹配，则返回 null
     */
    public static Long getLoginUserId(ServerWebExchange exchange) {
        return MapUtil.getLong(exchange.getAttributes(), LOGIN_USER_ID_ATTR);
    }

    /**
     * 从当前请求上下文中获取已认证用户的类型。
     * <p>
     * 用户类型通常用于区分不同角色或系统（如：1=管理员，2=普通用户等）。
     * 该值由 {@link #setLoginUser(ServerWebExchange, LoginUser)} 方法设置。
     *
     * @param exchange 当前请求上下文
     * @return 用户类型（Integer 类型）；若未设置或类型不匹配，则返回 null
     */
    public static Integer getLoginUserType(ServerWebExchange exchange) {
        return MapUtil.getInt(exchange.getAttributes(), LOGIN_USER_TYPE_ATTR);
    }

    /**
     * 将 {@link LoginUser} 对象序列化为 JSON 字符串，
     * 并以 URL 编码形式设置到 {@link ServerHttpRequest.Builder} 的 "login-user" 请求头中。
     * <p>
     * 该方法用于网关向下游微服务传递用户上下文。
     * 由于 HTTP 头不支持直接传输复杂对象，故采用 JSON + URL 编码的方式安全传递。
     * <p>
     * 注意：调用方需确保后续通过 {@link #removeLoginUser(ServerWebExchange)} 移除此头，
     * 避免敏感信息泄露或重复传递。
     *
     * @param builder 用于构建新请求的 Builder 对象
     * @param user 要传递的登录用户对象
     * @throws RuntimeException 若序列化或编码过程中发生异常
     */
    public static void setLoginUserHeader(ServerHttpRequest.Builder builder, LoginUser user) {
        try {
            // 1. 将 LoginUser 对象转为 JSON 字符串
            String userStr = JsonUtils.toJsonString(user);
            // 2. 对 JSON 字符串进行 URL 编码，防止特殊字符（如引号、空格、中文）破坏 HTTP 头
            userStr = URLEncoder.encode(userStr, StandardCharsets.UTF_8);
            // 3. 添加到 login-user 请求头
            builder.header(LOGIN_USER_HEADER, userStr);
        } catch (Exception ex) {
            log.error("[setLoginUserHeader][序列化 user({}) 发生异常]", user, ex);
            throw new RuntimeException("设置 login-user 请求头失败", ex);
        }
    }

}