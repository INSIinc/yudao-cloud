package cn.iocoder.yudao.gateway.util;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.extra.servlet.ServletUtil;
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
 *
 * 本类是从 yudao-spring-boot-starter-web 模块中的 WebFrameworkUtils 类复制而来，
 * 并针对响应式（Reactive）网关环境做了适配，提供租户 ID 透传、JSON 响应、客户端 IP 获取、路由信息提取等实用功能。
 *
 * @author 芋道源码
 */
@Slf4j
public class WebFrameworkUtils {

    /**
     * 自定义 HTTP 请求头名称：用于传递租户 ID（多租户系统标识）
     */
    private static final String HEADER_TENANT_ID = "tenant-id";

    /**
     * 私有构造函数，防止外部实例化该工具类
     */
    private WebFrameworkUtils() {}

    /**
     * 将租户 ID 添加到 HTTP 请求头中，通常用于下游微服务透传租户上下文。
     *
     * <p>该方法常用于在 Gateway 使用 WebClient 调用下游服务前，设置请求头。</p>
     *
     * @param tenantId     租户编号，若为 null 则不设置
     * @param httpHeaders  要设置的 HTTP 请求头对象（例如 WebClient 的 headers）
     */
    public static void setTenantIdHeader(Long tenantId, HttpHeaders httpHeaders) {
        if (tenantId == null) {
            return;
        }
        // 将 Long 类型的租户 ID 转为字符串并设置到指定 header
        httpHeaders.set(HEADER_TENANT_ID, String.valueOf(tenantId));
    }

    /**
     * 从当前网关请求中提取租户 ID。
     *
     * <p>从请求头 {@link #HEADER_TENANT_ID} 中读取值，并校验是否为有效数字。</p>
     *
     * @param exchange 当前的 ServerWebExchange 对象，包含完整请求/响应上下文
     * @return 租户 ID（Long 类型），若 header 不存在或非数字则返回 null
     */
    public static Long getTenantId(ServerWebExchange exchange) {
        String tenantId = exchange.getRequest().getHeaders().getFirst(HEADER_TENANT_ID);
        // 使用 Hutool 的 NumberUtil 判断字符串是否为有效数字，避免 NumberFormatException
        return NumberUtil.isNumber(tenantId) ? Long.valueOf(tenantId) : null;
    }

    /**
     * 向客户端写入 JSON 格式的响应体。
     *
     * <p>该方法适用于在 Gateway 中直接返回错误信息、认证失败等场景，避免转发到下游服务。</p>
     *
     * @param exchange 当前请求/响应上下文
     * @param object   要序列化为 JSON 的 Java 对象（如错误响应 DTO）
     * @return 返回一个 Mono<Void>，表示异步写入完成
     *
     * <p><b>注意：</b> 使用 {@link MediaType#APPLICATION_JSON_UTF8} 是为了兼容旧版浏览器或客户端，
     * 防止中文乱码。尽管该常量在 Spring 5.2+ 已废弃，但实际仍有效。</p>
     */
    @SuppressWarnings("deprecation") // 必须使用 APPLICATION_JSON_UTF8_VALUE，否则会乱码
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
                // 若序列化失败，记录日志并返回空响应（避免泄露敏感信息）
                ServerHttpRequest request = exchange.getRequest();
                log.error("[writeJSON][uri({}/{}) 发生异常]", request.getURI(), request.getMethod(), ex);
                return bufferFactory.wrap(new byte[0]);
            }
        }));
    }

    /**
     * 获取客户端的真实 IP 地址。
     *
     * <p>考虑了代理、负载均衡、CDN 等多层转发场景，按优先级顺序检查多个标准 HTTP 头。</p>
     *
     * @param exchange           当前请求上下文
     * @param otherHeaderNames   可选：额外的自定义 header 名称（如公司内部自定义的 X-Custom-IP）
     * @return 客户端真实 IP 地址（已处理多级代理），若无法获取则返回 null
     *
     * <p><b>逻辑说明：</b>
     * 1. 优先从常见代理头（如 X-Forwarded-For）中提取 IP；
     * 2. 若所有 header 都无效或为 unknown，则回退到 remoteAddress（即直连网关的客户端地址）；
     * 3. 使用 Hutool 的 NetUtil.getMultistageReverseProxyIp() 处理 “IP1, IP2, IP3” 格式的多级代理 IP，
     *    返回最左侧的（即最原始的）客户端 IP。</p>
     */
    public static String getClientIP(ServerWebExchange exchange, String... otherHeaderNames) {
        // 标准代理头列表（按优先级顺序）
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };

        // 如果传入了自定义 header，合并到检查列表末尾
        if (ArrayUtil.isNotEmpty(otherHeaderNames)) {
            headers = ArrayUtil.addAll(headers, otherHeaderNames);
        }

        // 方式一：尝试从 header 中获取 IP
        String ip;
        for (String header : headers) {
            ip = exchange.getRequest().getHeaders().getFirst(header);
            // NetUtil.isUnknown(ip) 会判断 ip 是否为 null、空、"unknown"、"unKnown" 等无效值
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
     * 从当前请求上下文中获取匹配的 Gateway 路由（Route）对象。
     *
     * <p>Spring Cloud Gateway 在路由匹配完成后，会将匹配到的 Route 存入 exchange 的属性中。</p>
     *
     * @param exchange 当前请求上下文
     * @return 匹配的 Route 对象，若未匹配（如 404）则可能为 null
     *
     * <p><b>用途示例：</b>
     * - 动态获取当前路由的 metadata（如权限标识、是否鉴权等）
     * - 实现基于路由的自定义逻辑（如日志记录、限流策略）</p>
     */
    public static Route getGatewayRoute(ServerWebExchange exchange) {
        // 从属性中获取 Gateway 内置的路由对象
        return exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
    }

}