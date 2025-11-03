package cn.iocoder.yudao.framework.common.enums;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Web 过滤器顺序的枚举类，用于统一管理 Spring Web 过滤器（Filter）的执行顺序。
 *
 * <p>在 Spring Boot 应用中，多个过滤器的执行顺序由其 {@code order} 值决定：值越小，越早执行。
 * 本接口通过定义常量，确保各业务或框架过滤器按预期顺序执行，避免因顺序错乱导致功能异常（如安全、多租户、日志等）。
 *
 * <p>考虑到每个 starter 模块都可能依赖该顺序定义，因此将其置于 {@code common} 模块的 {@code enum} 包下，以便复用。
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>使用 {@link Integer#MIN_VALUE} 作为最早执行的起点，逐步递增。</li>
 *   <li>关键框架过滤器（如 Spring Security）的默认 order 已知，需围绕其安排业务过滤器。</li>
 *   <li>依赖关系通过注释明确说明，便于维护。</li>
 * </ul>
 *
 * @author 芋道源码
 */
public interface WebFilterOrderEnum {

    /**
     * CORS（跨域资源共享）过滤器的顺序。
     *
     * <p>设置为 {@link Integer#MIN_VALUE}，确保在所有过滤器中最先执行。
     * 跨域预检请求（Preflight）不应被其他逻辑干扰，应尽早处理并返回响应。
     */
    int CORS_FILTER = Integer.MIN_VALUE;

    /**
     * 分布式链路追踪（Trace）过滤器的顺序。
     *
     * <p>紧随 CORS 过滤器之后执行（+1），用于在请求入口处初始化 Trace 上下文（如 TraceId、SpanId）。
     * 尽早初始化可确保后续所有操作都能正确关联到当前请求链路。
     */
    int TRACE_FILTER = CORS_FILTER + 1;

    /**
     * 环境标签（Env Tag）过滤器的顺序。
     *
     * <p>在 Trace 过滤器之后执行（+1），用于注入环境标识（如 dev/test/prod）到请求上下文或日志中。
     * 通常用于多环境隔离或灰度发布场景。
     */
    int ENV_TAG_FILTER = TRACE_FILTER + 1;

    /**
     * 请求体缓存（Request Body Cache）过滤器的顺序。
     *
     * <p>设置为 {@code Integer.MIN_VALUE + 500}，属于较早但非最优先的阶段。
     * 该过滤器用于包装 {@link HttpServletRequest}，使其支持多次读取请求体（例如 JSON Body），
     * 为后续需要重复解析 Body 的过滤器（如加解密、XSS）提供支持。
     */
    int REQUEST_BODY_CACHE_FILTER = Integer.MIN_VALUE + 500;

    /**
     * API 加解密过滤器的顺序。
     *
     * <p>紧接在 {@link #REQUEST_BODY_CACHE_FILTER} 之后执行（+1）。
     * 依赖缓存后的请求体进行解密（请求进入时）或加密（响应返回时）。
     * 必须在请求体被缓存后才能安全读取原始数据。
     */
    int API_ENCRYPT_FILTER = REQUEST_BODY_CACHE_FILTER + 1;

    // ========== 以下过滤器顺序围绕 Spring 内置及常用过滤器安排 ==========

    /**
     * 多租户上下文（Tenant Context）过滤器的顺序。
     *
     * <p>设置为 {@code -104}，需满足两个条件：
     * <ol>
     *   <li>在 {@link #API_ACCESS_LOG_FILTER}（-103）之前执行，确保访问日志能记录租户信息；</li>
     *   <li>在 {@link #REQUEST_BODY_CACHE_FILTER} 之后执行，以便从请求头/Body 中解析租户标识。</li>
     * </ol>
     * 该过滤器负责从请求中提取租户 ID，并设置到当前线程上下文（如 ThreadLocal）。
     */
    int TENANT_CONTEXT_FILTER = -104;

    /**
     * API 访问日志（Access Log）过滤器的顺序。
     *
     * <p>设置为 {@code -103}，需满足：
     * <ul>
     *   <li>在 {@link #REQUEST_BODY_CACHE_FILTER} 之后，以便能安全读取请求体内容（用于记录）；</li>
     *   <li>在 {@link #TENANT_CONTEXT_FILTER} 之后，确保日志包含租户信息。</li>
     * </ul>
     * 通常记录请求路径、参数、耗时、用户、租户等信息。
     */
    int API_ACCESS_LOG_FILTER = -103;

    /**
     * XSS（跨站脚本攻击）防护过滤器的顺序。
     *
     * <p>设置为 {@code -102}，需在 {@link #REQUEST_BODY_CACHE_FILTER} 之后，
     * 以便对缓存后的请求参数或 Body 进行 XSS 恶意脚本过滤或转义。
     */
    int XSS_FILTER = -102;

    // ========== Spring Security 相关 ==========
    // Spring Security 的默认过滤器链 order 为 -100，
    // 参见：org.springframework.boot.autoconfigure.security.SecurityProperties.DEFAULT_FILTER_ORDER

    /**
     * 多租户安全（Tenant Security）过滤器的顺序。
     *
     * <p>设置为 {@code -99}，确保在 Spring Security 过滤器（-100）<strong>之后</strong>执行。
     * 通常用于在用户认证完成后，结合租户上下文进行权限二次校验或数据隔离。
     */
    int TENANT_SECURITY_FILTER = -99;

    /**
     * Flowable（工作流引擎）集成过滤器的顺序。
     *
     * <p>设置为 {@code -98}，在 Spring Security 之后执行。
     * 用于将当前认证用户信息同步到 Flowable 的安全上下文中，确保工作流操作具备正确的身份。
     */
    int FLOWABLE_FILTER = -98;

    /**
     * 演示（Demo）模式过滤器的顺序。
     *
     * <p>设置为 {@link Integer#MAX_VALUE}，确保在所有过滤器中最后执行。
     * 通常用于拦截并阻止演示环境下的写操作（如增删改），保护演示数据。
     */
    int DEMO_FILTER = Integer.MAX_VALUE;

}