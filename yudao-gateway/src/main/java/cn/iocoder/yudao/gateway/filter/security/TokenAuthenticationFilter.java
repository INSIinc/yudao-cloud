package cn.iocoder.yudao.gateway.filter.security;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.core.KeyValue;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.date.LocalDateTimeUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.gateway.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.gateway.util.WebFrameworkUtils;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

import static cn.iocoder.yudao.framework.common.util.cache.CacheUtils.buildAsyncReloadingCache;

/**
 * Token 鉴权过滤器，用于验证请求中的访问令牌（Access Token）是否有效。
 * <p>
 * 功能说明：
 * 1. 若 Token 有效，则从远程认证服务获取用户信息（userId、userType、tenantId 等），并通过 Header 透传给下游微服务；
 * 2. 若 Token 无效或缺失，仍会继续放行请求，由下游服务自行决定是否需要登录态（即“服务自治”原则）。
 * <p>
 * 设计考虑：
 * - 使用 WebClient（而非 Feign）调用认证服务，因为 Spring Cloud OpenFeign 目前不支持 Reactive 编程模型；
 * - 支持多租户，通过 Header 传递 tenant-id；
 * - 本地缓存用户信息，避免每次请求都远程校验 Token，提升性能；
 * - 安全性：清除可能被伪造的 login-user 请求头。
 *
 * @author 芋道源码
 */
@Component
public class TokenAuthenticationFilter implements GlobalFilter, Ordered {

    /**
     * 用于解析远程校验 Token 接口返回的 JSON 响应体的类型引用。
     * 目标结构为：CommonResult<OAuth2AccessTokenCheckRespDTO>
     */
    private static final TypeReference<CommonResult<OAuth2AccessTokenCheckRespDTO>> CHECK_RESULT_TYPE_REFERENCE
            = new TypeReference<CommonResult<OAuth2AccessTokenCheckRespDTO>>() {};

    /**
     * 表示“空用户”的特殊占位对象。
     * <p>
     * 用途：
     * 1. 在 {@link #getLoginUser} 方法中，当远程调用返回无效 Token 时，避免返回 Mono.empty()，
     *    从而确保后续的 flatMap 能继续执行（否则会中断链式调用，导致网关返回空响应）；
     * 2. 在 {@link #buildUser} 中，若 Token 已过期（401），返回此对象，防止无效 Token 被缓存。
     */
    private static final LoginUser LOGIN_USER_EMPTY = new LoginUser();

    /**
     * 用于发起远程 Token 校验请求的 WebClient 实例。
     * <p>
     * 通过注入的 {@link ReactorLoadBalancerExchangeFilterFunction} 实现服务发现与负载均衡。
     */
    private final WebClient webClient;

    /**
     * 登录用户信息的本地缓存（基于 Guava Cache）。
     * <p>
     * 缓存键：{@link KeyValue}，其中：
     * - key：租户 ID（Long），实现多租户隔离；
     * - value：访问令牌（String）；
     * <p>
     * 缓存值：{@link LoginUser} 用户信息对象；
     * <p>
     * 缓存策略：异步自动刷新，有效期 1 分钟（防止 stale 数据长期滞留）。
     */
    private final LoadingCache<KeyValue<Long, String>, LoginUser> loginUserCache = buildAsyncReloadingCache(Duration.ofMinutes(1),
            new CacheLoader<KeyValue<Long, String>, LoginUser>() {
                @Override
                public LoginUser load(KeyValue<Long, String> cacheKey) {
                    // 注意：Guava Cache 的 load 方法是非异步的（同步阻塞），但在 buildAsyncReloadingCache 中会被包装为异步加载
                    String body = checkAccessToken(cacheKey.getKey(), cacheKey.getValue()).block();
                    return buildUser(body);
                }
            });

    /**
     * 构造函数，注入负载均衡的 WebClient 过滤器函数。
     * <p>
     * 不使用 Feign 的原因：
     * - Spring Cloud OpenFeign 官方暂未支持 Reactive（见文档：https://docs.spring.io/spring-cloud-openfeign/docs/current/reference/html/#reactive-support）；
     * - Token 校验接口需传递 tenant-id Header，若用 Feign 需额外实现 RequestInterceptor，复杂度高；
     * - 故选用 WebClient + ReactorLoadBalancerExchangeFilterFunction 实现声明式负载均衡调用。
     *
     * @param lbFunction Spring Cloud LoadBalancer 提供的 Reactive 负载均衡过滤器
     */
    public TokenAuthenticationFilter(ReactorLoadBalancerExchangeFilterFunction lbFunction) {
        this.webClient = WebClient.builder().filter(lbFunction).build();
    }

    /**
     * 全局过滤器核心逻辑。
     *
     * @param exchange 当前请求上下文
     * @param chain    网关过滤器链
     * @return 返回 Mono<Void>，表示异步操作完成
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 【安全加固】清除请求中可能存在的 login-user Header，防止客户端伪造用户身份
        exchange = SecurityFrameworkUtils.removeLoginUser(exchange);

        // 1. 尝试从请求中获取访问令牌（通常在 Authorization Header 中）
        String token = SecurityFrameworkUtils.obtainAuthorization(exchange);
        if (StrUtil.isEmpty(token)) {
            // 情况一：无 Token，直接放行（由下游服务决定是否需要登录）
            return chain.filter(exchange);
        }

        // 2. 有 Token，尝试解析用户信息
        ServerWebExchange finalExchange = exchange; // 用于 Lambda 表达式中引用
        return getLoginUser(exchange, token)
                .defaultIfEmpty(LOGIN_USER_EMPTY) // 关键：确保即使远程返回空，也能进入 flatMap
                .flatMap(user -> {
                    // 判断用户是否有效
                    if (user == LOGIN_USER_EMPTY ||
                            user.getExpiresTime() == null ||
                            LocalDateTimeUtils.beforeNow(user.getExpiresTime())) {
                        // 情况二：Token 无效（空、过期等），仍放行请求（服务端自行校验）
                        return chain.filter(finalExchange);
                    }

                    // 情况三：Token 有效
                    // 2.1 设置当前上下文的登录用户（供后续 Filter 或 Handler 使用）
                    SecurityFrameworkUtils.setLoginUser(finalExchange, user);
                    // 2.2 将用户信息序列化为 JSON，放入 login-user Header，透传给下游服务
                    ServerWebExchange newExchange = finalExchange.mutate()
                            .request(builder -> SecurityFrameworkUtils.setLoginUserHeader(builder, user))
                            .build();
                    return chain.filter(newExchange);
                });
    }

    /**
     * 根据租户 ID 和 Token，获取对应的登录用户信息。
     * <p>
     * 优先从本地缓存读取，缓存未命中则远程调用认证服务。
     *
     * @param exchange 当前请求上下文
     * @param token    访问令牌
     * @return 返回包含 LoginUser 的 Mono（可能为空）
     */
    private Mono<LoginUser> getLoginUser(ServerWebExchange exchange, String token) {
        // 1. 从请求中提取租户 ID
        Long tenantId = WebFrameworkUtils.getTenantId(exchange);
        // 2. 构造缓存键
        KeyValue<Long, String> cacheKey = new KeyValue<Long, String>().setKey(tenantId).setValue(token);
        // 3. 尝试从缓存中获取
        LoginUser localUser = loginUserCache.getIfPresent(cacheKey);
        if (localUser != null) {
            return Mono.just(localUser);
        }

        // 4. 缓存未命中，远程校验 Token
        return checkAccessToken(tenantId, token)
                .flatMap((Function<String, Mono<LoginUser>>) body -> {
                    LoginUser remoteUser = buildUser(body);
                    if (remoteUser != null) {
                        // 远程返回有效用户，写入缓存（注意：LOGIN_USER_EMPTY 也会被缓存，防止频繁无效请求）
                        loginUserCache.put(cacheKey, remoteUser);
                        return Mono.just(remoteUser);
                    }
                    // 返回 empty，触发 defaultIfEmpty(LOGIN_USER_EMPTY)
                    return Mono.empty();
                });
    }

    /**
     * 调用远程认证服务，校验 Token 是否有效。
     *
     * @param tenantId 租户 ID
     * @param token    访问令牌
     * @return 返回接口原始响应体（String 类型 JSON）
     */
    private Mono<String> checkAccessToken(Long tenantId, String token) {
        return webClient.get()
                // 请求路径：/oauth2/token/check?accessToken=xxx
                .uri(OAuth2TokenCommonApi.URL_CHECK, uriBuilder ->
                        uriBuilder.queryParam("accessToken", token).build())
                // 设置租户 ID 到请求头，供认证服务识别租户上下文
                .headers(httpHeaders -> WebFrameworkUtils.setTenantIdHeader(tenantId, httpHeaders))
                .retrieve()
                .bodyToMono(String.class); // 返回原始 JSON 字符串，便于后续统一解析
    }

    /**
     * 将远程接口返回的 JSON 字符串解析为 LoginUser 对象。
     *
     * @param body 远程接口返回的 JSON 字符串
     * @return 解析后的 LoginUser，若解析失败或 Token 无效则返回 null 或 LOGIN_USER_EMPTY
     */
    private LoginUser buildUser(String body) {
        // 1. 解析 JSON 响应
        CommonResult<OAuth2AccessTokenCheckRespDTO> result = JsonUtils.parseObject(body, CHECK_RESULT_TYPE_REFERENCE);
        if (result == null) {
            return null; // 解析失败
        }

        // 2. 判断业务是否成功
        if (result.isError()) {
            // 特殊处理：401 表示 Token 过期，返回 LOGIN_USER_EMPTY 避免缓存污染
            if (Objects.equals(result.getCode(), HttpStatus.UNAUTHORIZED.value())) {
                return LOGIN_USER_EMPTY;
            }
            return null; // 其他错误（如 500）不缓存
        }

        // 3. 构造 LoginUser 对象
        OAuth2AccessTokenCheckRespDTO tokenInfo = result.getData();
        return new LoginUser()
                .setId(tokenInfo.getUserId())
                .setUserType(tokenInfo.getUserType())
                .setInfo(tokenInfo.getUserInfo()) // 额外用户信息（如姓名、部门等）
                .setTenantId(tokenInfo.getTenantId())
                .setScopes(tokenInfo.getScopes())
                .setExpiresTime(tokenInfo.getExpiresTime());
    }

    /**
     * 设置过滤器执行顺序。
     * <p>
     * 返回 -100，与 Spring Security 的过滤器顺序对齐，确保在安全上下文初始化前完成 Token 解析。
     *
     * @return 过滤器顺序值（越小越先执行）
     */
    @Override
    public int getOrder() {
        return -100;
    }

}