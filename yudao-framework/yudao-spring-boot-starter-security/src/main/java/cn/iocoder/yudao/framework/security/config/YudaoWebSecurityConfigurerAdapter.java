package cn.iocoder.yudao.framework.security.config;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.security.core.filter.TokenAuthenticationFilter;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;

/**
 * 自定义 Spring Security 安全配置类。
 *
 * Spring Security 是一个功能强大的安全框架，用于控制“哪些用户能访问哪些接口”。
 *
 * 本项目基于 Token（如 JWT）做认证，无需登录页面和 Session，适合前后端分离项目。
 *
 * 主要功能：
 * - 放行静态资源（如 .js、.css）
 * - 自动识别 @PermitAll 注解的接口，无需登录
 * - 支持配置文件中指定的免登录 URL（security.permit-all-urls）
 * - 添加自定义 Token 认证过滤器
 * - 全局处理“未登录”和“权限不足”的异常
 *
 * @author 芋道源码
 */
@AutoConfiguration
@AutoConfigureOrder(-1) // 优先加载，确保本配置先于 Spring Security 默认配置生效
@EnableMethodSecurity(securedEnabled = true) // 开启方法级安全注解（如 @PreAuthorize）
public class YudaoWebSecurityConfigurerAdapter {

    // 从配置文件读取 Web 相关配置（如 API 前缀）
    @Resource
    private WebProperties webProperties;

    // 从配置文件读取 Security 相关配置（如 permit-all-urls）
    @Resource
    private SecurityProperties securityProperties;

    // 自定义“未登录”处理器：返回 401 错误给前端
    @Resource
    private AuthenticationEntryPoint authenticationEntryPoint;

    // 自定义“权限不足”处理器：返回 403 错误给前端
    @Resource
    private AccessDeniedHandler accessDeniedHandler;

    // 自定义 Token 认证过滤器：从请求头中解析 Token 并验证用户身份
    @Resource
    private TokenAuthenticationFilter authenticationTokenFilter;

    // 允许其他模块自定义权限规则（例如：系统模块、订单模块各自定义自己的放行规则）
    @Resource
    private List<AuthorizeRequestsCustomizer> authorizeRequestsCustomizers;

    // Spring 应用上下文，用于获取所有 Controller 的接口信息
    @Resource
    private ApplicationContext applicationContext;

    /**
     * 将 Spring Security 的认证管理器（AuthenticationManager）暴露为 Bean，
     * 以便在其他地方（如登录接口）注入使用。
     *
     * 默认情况下，AuthenticationManager 不会自动注册为 Bean，这里手动注册。
     */
    @Bean
    public AuthenticationManager authenticationManagerBean(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * 配置 HTTP 请求的安全规则（哪些接口要登录，哪些不用）。
     *
     * 规则优先级（从上到下）：
     * 1. 静态资源、@PermitAll 接口、配置文件中指定的免登录 URL → 允许访问
     * 2. 各模块自定义的权限规则（通过 authorizeRequestsCustomizers）→ 灵活控制
     * 3. 所有其他请求 → 必须登录
     */
    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        // ===== 基础安全配置 =====
        httpSecurity
                // 启用 CORS（跨域资源共享），前端才能调用后端接口
                .cors(Customizer.withDefaults())
                // 禁用 CSRF（跨站请求伪造）保护，因为 Token 认证不需要（且 CSRF 依赖 Session）
                .csrf(AbstractHttpConfigurer::disable)
                // 设置无状态（Stateless）：不创建 Session，完全靠 Token 认证
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 允许页面嵌入 iframe（如用于报表、SSE 等场景）
                .headers(c -> c.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                // 配置异常处理器
                .exceptionHandling(c -> c
                        .authenticationEntryPoint(authenticationEntryPoint)  // 未登录时调用
                        .accessDeniedHandler(accessDeniedHandler)            // 权限不足时调用
                );

        // ===== 获取所有加了 @PermitAll 的接口路径 =====
        Multimap<HttpMethod, String> permitAllUrls = getPermitAllUrlsFromAnnotations();

        // ===== 配置 URL 访问权限 =====
        httpSecurity
                // ① 全局通用规则
                .authorizeHttpRequests(c -> c
                        // 静态资源：.html、.css、.js 可直接访问
                        .requestMatchers(HttpMethod.GET, "/*.html", "/*.css", "/*.js").permitAll()

                        // @PermitAll 注解的接口：按 HTTP 方法分别放行
                        .requestMatchers(HttpMethod.GET, permitAllUrls.get(HttpMethod.GET).toArray(new String[0])).permitAll()
                        .requestMatchers(HttpMethod.POST, permitAllUrls.get(HttpMethod.POST).toArray(new String[0])).permitAll()
                        .requestMatchers(HttpMethod.PUT, permitAllUrls.get(HttpMethod.PUT).toArray(new String[0])).permitAll()
                        .requestMatchers(HttpMethod.DELETE, permitAllUrls.get(HttpMethod.DELETE).toArray(new String[0])).permitAll()
                        .requestMatchers(HttpMethod.HEAD, permitAllUrls.get(HttpMethod.HEAD).toArray(new String[0])).permitAll()
                        .requestMatchers(HttpMethod.PATCH, permitAllUrls.get(HttpMethod.PATCH).toArray(new String[0])).permitAll()

                        // 配置文件中指定的免登录 URL（如 /admin-api/login）
                        .requestMatchers(securityProperties.getPermitAllUrls().toArray(new String[0])).permitAll()
                )
                // ② 各模块自定义权限规则（例如：系统模块加自己的规则）
                .authorizeHttpRequests(c -> authorizeRequestsCustomizers.forEach(customizer -> customizer.customize(c)))
                // ③ 兜底规则：除了上面放行的，其他所有请求都必须登录
                .authorizeHttpRequests(c -> c
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll() // 异步请求（如 SSE）放行
                        .anyRequest().authenticated()
                );

        // ===== 添加自定义 Token 认证过滤器 =====
        // 在 Spring Security 默认的用户名密码过滤器之前插入 Token 过滤器
        httpSecurity.addFilterBefore(authenticationTokenFilter, UsernamePasswordAuthenticationFilter.class);

        // 构建并返回安全过滤器链
        return httpSecurity.build();
    }

    /**
     * 拼接 App API 的完整路径（项目中可能用到，但当前未使用）
     */
    private String buildAppApi(String url) {
        return webProperties.getAppApi().getPrefix() + url;
    }

    /**
     * 通过反射扫描所有 Controller，找出哪些接口加了 @PermitAll 注解，
     * 自动收集这些接口的 URL 和请求方法（GET/POST 等），用于放行。
     *
     * 例如：
     *   @PermitAll
     *   @GetMapping("/login")
     *   public String login() { ... }
     * → 自动放行 POST /login（如果方法上没写 method，则所有方法都放行）
     *
     * @return 多值 Map：HttpMethod → [url1, url2, ...]
     */
    private Multimap<HttpMethod, String> getPermitAllUrlsFromAnnotations() {
        Multimap<HttpMethod, String> result = HashMultimap.create();

        // 1. 获取 Spring MVC 的请求映射处理器
        RequestMappingHandlerMapping mapping = (RequestMappingHandlerMapping)
                applicationContext.getBean("requestMappingHandlerMapping");

        // 2. 获取所有接口（RequestMappingInfo）和对应的方法（HandlerMethod）
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = mapping.getHandlerMethods();

        // 3. 遍历所有接口，检查是否有 @PermitAll 注解（方法级或类级）
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            HandlerMethod method = entry.getValue();

            // 如果方法或类上没有 @PermitAll，跳过
            if (!method.hasMethodAnnotation(PermitAll.class) &&
                    !method.getBeanType().isAnnotationPresent(PermitAll.class)) {
                continue;
            }

            // 4. 提取该接口的所有 URL 路径（支持 Ant 风格和 PathPattern）
            Set<String> urls = new HashSet<>();
            if (entry.getKey().getPatternsCondition() != null) {
                urls.addAll(entry.getKey().getPatternsCondition().getPatterns());
            }
            if (entry.getKey().getPathPatternsCondition() != null) {
                urls.addAll(convertList(entry.getKey().getPathPatternsCondition().getPatterns(), PathPattern::getPatternString));
            }
            if (urls.isEmpty()) continue;

            // 5. 提取 HTTP 请求方法（GET/POST 等）
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();

            // 如果没指定方法（如只写了 @RequestMapping），默认放行所有方法
            if (CollUtil.isEmpty(methods)) {
                for (HttpMethod httpMethod : new HttpMethod[]{
                        HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
                        HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.PATCH}) {
                    result.putAll(httpMethod, urls);
                }
                continue;
            }

            // 6. 按实际指定的方法放行
            for (RequestMethod requestMethod : methods) {
                switch (requestMethod) {
                    case GET:    result.putAll(HttpMethod.GET, urls); break;
                    case POST:   result.putAll(HttpMethod.POST, urls); break;
                    case PUT:    result.putAll(HttpMethod.PUT, urls); break;
                    case DELETE: result.putAll(HttpMethod.DELETE, urls); break;
                    case HEAD:   result.putAll(HttpMethod.HEAD, urls); break;
                    case PATCH:  result.putAll(HttpMethod.PATCH, urls); break;
                }
            }
        }

        return result;
    }

}