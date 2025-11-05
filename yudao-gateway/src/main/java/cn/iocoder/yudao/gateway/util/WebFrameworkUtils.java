package cn.iocoder.yudao.gateway.util;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.NumberUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Web 工具类（专用于 Spring Cloud Gateway 网关环境）
 * <p>
 * 本类是从 yudao-spring-boot-starter-web 模块中的 WebFrameworkUtils 类复制而来，
 * 并针对响应式（Reactive）网关环境做了适配。
 * <p>
 * 主要功能：
 * <ul>
 *   <li>租户 ID 透传：在多租户系统中传递租户标识</li>
 *   <li>JSON 响应：直接返回 JSON 格式的响应数据</li>
 *   <li>客户端 IP 获取：获取真实客户端 IP 地址（支持多级代理）</li>
 *   <li>路由信息提取：获取当前请求匹配的路由信息</li>
 * </ul>
 *
 * @author 芋道源码
 */
@Slf4j
public class WebFrameworkUtils {

    /**
     * 自定义 HTTP 请求头名称：用于传递租户 ID
     * <p>
     * 在多租户系统中，每个租户（如不同公司）都有独立的数据隔离。
     * 通过此请求头，可以在微服务之间传递当前请求的租户标识。
     */
    private static final String HEADER_TENANT_ID = "tenant-id";

    /**
     * 私有构造函数，防止工具类被实例化
     */
    private WebFrameworkUtils() {}

    /**
     * 将租户 ID 添加到 HTTP 请求头中
     * <p>
     * 该方法常用于在 Gateway 使用 WebClient 调用下游服务前设置请求头，
     * 实现租户上下文在微服务之间的透传。
     * <p>
     * <b>使用示例：</b><br>
     * 当租户 ID=100 的用户访问网关，网关转发请求到订单服务时，
     * 调用此方法将租户 ID 添加到请求头，确保订单服务能够查询正确租户的数据。
     *
     * @param tenantId     租户编号，若为 {@code null} 则不设置
     * @param httpHeaders  要设置的 HTTP 请求头对象
     */
    public static void setTenantIdHeader(Long tenantId, HttpHeaders httpHeaders) {
        if (tenantId == null) {
            return;
        }
        // 将租户 ID 转换为字符串并设置到请求头
        httpHeaders.set(HEADER_TENANT_ID, String.valueOf(tenantId));
    }

    /**
     * 从当前网关请求中提取租户 ID
     * <p>
     * 从请求头 {@value #HEADER_TENANT_ID} 中读取值，并校验是否为有效数字。
     * <p>
     * <b>使用示例：</b><br>
     * 当用户请求到达网关时，请求头中可能包含 "tenant-id: 100"，
     * 此方法将提取该值并转换为 Long 类型。
     *
     * @param exchange 当前的 {@link ServerWebExchange} 对象，包含完整请求/响应上下文
     * @return 租户 ID（Long 类型），若请求头不存在或非数字则返回 {@code null}
     */
    public static Long getTenantId(ServerWebExchange exchange) {
        // 从请求头中获取租户 ID（字符串形式）
        String tenantId = exchange.getRequest().getHeaders().getFirst(HEADER_TENANT_ID);
        // 使用 NumberUtil 判断是否为有效数字，避免 NumberFormatException
        return NumberUtil.isNumber(tenantId) ? Long.valueOf(tenantId) : null;
    }

    /**
     * 向客户端写入 JSON 格式的响应体
     * <p>
     * 该方法适用于在 Gateway 中直接返回响应的场景，如：
     * <ul>
     *   <li>返回错误信息（如认证失败、权限不足）</li>
     *   <li>返回限流、降级响应</li>
     *   <li>其他无需转发到下游服务的场景</li>
     * </ul>
     * <p>
     * <b>使用示例：</b><br>
     * 用户未登录访问网关时，直接返回 JSON 错误信息：<br>
     * {@code {"code": 401, "message": "未登录"}}
     * <p>
     * <b>技术说明：</b><br>
     * 使用 {@link MediaType#APPLICATION_JSON_UTF8} 以兼容旧版浏览器，防止中文乱码。
     * 返回 {@link Mono} 表示异步写入操作，不会阻塞线程。
     *
     * @param exchange 当前请求/响应上下文
     * @param object   要序列化为 JSON 的 Java 对象
     * @return {@link Mono}<{@link Void}>，表示异步写入完成
     */
    @SuppressWarnings("deprecation") // 必须使用 APPLICATION_JSON_UTF8，否则会乱码
    public static Mono<Void> writeJSON(ServerWebExchange exchange, Object object) {
        ServerHttpResponse response = exchange.getResponse();
        // 设置响应内容类型为 application/json; charset=UTF-8
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON_UTF8);

        // 异步写入响应体
        return response.writeWith(Mono.fromSupplier(() -> {
            DataBufferFactory bufferFactory = response.bufferFactory();
            try {
                // 将 Java 对象序列化为 UTF-8 字节数组，并包装为 DataBuffer
                return bufferFactory.wrap(JsonUtils.toJsonByte(object));
            } catch (Exception ex) {
                // 序列化失败时记录日志并返回空响应，避免泄露敏感信息
                ServerHttpRequest request = exchange.getRequest();
                log.error("[writeJSON][uri({}/{}) 发生异常]", request.getURI(), request.getMethod(), ex);
                return bufferFactory.wrap(new byte[0]);
            }
        }));
    }

    /**
     * 获取客户端的真实 IP 地址
     * <p>
     * 考虑了代理、负载均衡、CDN 等多层转发场景，按优先级顺序检查多个标准 HTTP 头。
     * <p>
     * <b>背景说明：</b><br>
     * 在生产环境中，用户请求通常经过多层转发：<br>
     * 用户 → CDN → 负载均衡 → Nginx → 网关<br>
     * 直接获取 IP 可能得到的是 Nginx 的 IP（如 192.168.1.100），而非用户真实 IP。
     * <p>
     * <b>技术原理：</b><br>
     * 代理服务器会将真实客户端 IP 添加到特定请求头中，如：<br>
     * {@code X-Forwarded-For: 123.45.67.89, 192.168.1.100}<br>
     * 其中 123.45.67.89 是用户真实 IP，192.168.1.100 是代理服务器 IP。
     * <p>
     * <b>处理逻辑：</b>
     * <ol>
     *   <li>按优先级检查常见代理头（如 X-Forwarded-For、X-Real-IP 等）</li>
     *   <li>若所有请求头都无效或为 "unknown"，则使用 remoteAddress（直连地址）</li>
     *   <li>使用 {@link NetUtil#getMultistageReverseProxyIp(String)} 处理多级代理 IP，
     *       从 "IP1, IP2, IP3" 格式中提取最左侧的原始客户端 IP</li>
     * </ol>
     *
     * @param exchange           当前请求上下文
     * @param otherHeaderNames   可选的自定义请求头名称（如公司内部自定义的 X-Custom-IP）
     * @return 客户端真实 IP 地址，若无法获取则返回 {@code null}
     */
    public static String getClientIP(ServerWebExchange exchange, String... otherHeaderNames) {
        // 标准代理头列表（按优先级顺序）
        String[] headers = {
                "X-Forwarded-For",      // Nginx 等反向代理常用
                "X-Real-IP",            // Nginx 也常用此头
                "Proxy-Client-IP",      // Apache 代理使用
                "WL-Proxy-Client-IP",   // WebLogic 使用
                "HTTP_CLIENT_IP",       // HTTP 协议头
                "HTTP_X_FORWARDED_FOR"  // 另一种转发格式
        };

        // 如果传入了自定义 header，合并到检查列表末尾
        if (ArrayUtil.isNotEmpty(otherHeaderNames)) {
            headers = ArrayUtil.addAll(headers, otherHeaderNames);
        }

        // 方式一：尝试从 header 中获取 IP
        String ip;
        for (String header : headers) {
            ip = exchange.getRequest().getHeaders().getFirst(header);
            // isUnknown() 会判断 IP 是否为 null、空字符串或 "unknown"
            if (!NetUtil.isUnknown(ip)) {
                // 处理多级代理，例如 "192.168.1.1, 10.0.0.1" 返回 "192.168.1.1"
                return NetUtil.getMultistageReverseProxyIp(ip);
            }
        }

        // 方式二：若 header 无有效 IP，则使用 remoteAddress（直连地址）
        if (exchange.getRequest().getRemoteAddress() == null) {
            return null;
        }
        ip = exchange.getRequest().getRemoteAddress().getHostString();
        return NetUtil.getMultistageReverseProxyIp(ip);
    }

    /**
     * 从当前请求上下文中获取匹配的 Gateway 路由对象
     * <p>
     * Spring Cloud Gateway 在路由匹配完成后，会将匹配到的 {@link Route}
     * 存入 {@link ServerWebExchange} 的属性中。
     * <p>
     * <b>使用场景：</b>
     * <ul>
     *   <li>动态获取当前路由的 metadata（如权限标识、是否需要鉴权等）</li>
     *   <li>实现基于路由的自定义逻辑（如日志记录、限流策略）</li>
     *   <li>获取路由 ID、目标服务地址等信息</li>
     * </ul>
     * <p>
     * <b>路由说明：</b><br>
     * 路由是网关的核心概念，定义了请求的转发规则，例如：
     * <ul>
     *   <li>访问 /api/order/** → 转发到订单服务</li>
     *   <li>访问 /api/user/** → 转发到用户服务</li>
     * </ul>
     *
     * @param exchange 当前请求上下文
     * @return 匹配的 {@link Route} 对象，若未匹配（如 404）则可能为 {@code null}
     */
    public static Route getGatewayRoute(ServerWebExchange exchange) {
        // 从 exchange 属性中获取 Gateway 内置的路由对象
        return exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
    }

}
