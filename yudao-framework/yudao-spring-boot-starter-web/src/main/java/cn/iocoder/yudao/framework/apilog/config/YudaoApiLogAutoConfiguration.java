package cn.iocoder.yudao.framework.apilog.config;

import cn.iocoder.yudao.framework.apilog.core.filter.ApiAccessLogFilter;
import cn.iocoder.yudao.framework.apilog.core.interceptor.ApiAccessLogInterceptor;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiAccessLogCommonApi;
import cn.iocoder.yudao.framework.common.enums.WebFilterOrderEnum;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.config.YudaoWebAutoConfiguration;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API 访问日志的自动配置类
 *
 * <p>这个类的作用是配置系统的 API 访问日志功能，主要包含两部分：
 * <ul>
 *   <li>1. ApiAccessLogFilter（过滤器）：记录每次 API 请求的详细信息到数据库</li>
 *   <li>2. ApiAccessLogInterceptor（拦截器）：在开发环境打印请求和响应日志到控制台</li>
 * </ul>
 *
 * <p>注解说明：
 * <ul>
 *   <li>@AutoConfiguration：标识这是一个自动配置类，Spring Boot 启动时会自动加载</li>
 *   <li>after = YudaoWebAutoConfiguration.class：表示在 YudaoWebAutoConfiguration 配置类之后加载</li>
 *   <li>implements WebMvcConfigurer：实现 Spring MVC 配置接口，可以自定义 MVC 配置（如添加拦截器）</li>
 * </ul>
 *
 * @author 芋道源码
 */
@AutoConfiguration(after = YudaoWebAutoConfiguration.class)
public class YudaoApiLogAutoConfiguration implements WebMvcConfigurer {

    /**
     * 创建 ApiAccessLogFilter Bean，记录 API 请求日志
     *
     * <p>功能说明：
     * 这个过滤器会拦截所有的 HTTP 请求，记录以下信息到数据库：
     * <ul>
     *   <li>请求信息：URL、HTTP 方法、请求参数、请求头等</li>
     *   <li>响应信息：响应结果、响应时间等</li>
     *   <li>用户信息：当前登录用户 ID</li>
     *   <li>其他信息：应用名称、追踪 ID、IP 地址等</li>
     * </ul>
     *
     * <p>参数说明：
     * @param webProperties Web 相关配置属性（如 API 前缀、允许的 API 路径等）
     * @param applicationName 应用名称，从配置文件的 spring.application.name 读取
     * @param apiAccessLogApi 用于将日志信息保存到数据库的 API 接口
     *
     * @return FilterRegistrationBean<ApiAccessLogFilter> 过滤器注册 Bean，Spring 会自动注册到过滤器链中
     *
     * <p>注解说明：
     * <ul>
     *   <li>@Bean：将方法返回的对象注册为 Spring 容器中的 Bean</li>
     *   <li>@ConditionalOnProperty：条件注解，只有满足条件才会创建这个 Bean</li>
     *   <li>prefix = "yudao.access-log"：配置属性前缀</li>
     *   <li>value = "enable"：配置属性名称</li>
     *   <li>matchIfMissing = true：如果配置文件中没有这个属性，默认为 true（启用）</li>
     * </ul>
     *
     * <p>使用示例：
     * 可以在 application.yml 中通过以下配置禁用访问日志：
     * <pre>
     * yudao:
     *   access-log:
     *     enable: false
     * </pre>
     */
    @Bean
    @ConditionalOnProperty(prefix = "yudao.access-log", value = "enable", matchIfMissing = true)
    public FilterRegistrationBean<ApiAccessLogFilter> apiAccessLogFilter(WebProperties webProperties,
                                                                         @Value("${spring.application.name}") String applicationName,
                                                                         ApiAccessLogCommonApi apiAccessLogApi) {
        // 创建 ApiAccessLogFilter 实例，传入必要的配置和依赖
        ApiAccessLogFilter filter = new ApiAccessLogFilter(webProperties, applicationName, apiAccessLogApi);
        // 将过滤器包装成 FilterRegistrationBean，并设置执行顺序
        return createFilterBean(filter, WebFilterOrderEnum.API_ACCESS_LOG_FILTER);
    }

    /**
     * 创建过滤器注册 Bean 的辅助方法
     *
     * <p>作用：
     * 将一个 Filter 对象包装成 FilterRegistrationBean，并设置执行顺序。
     * FilterRegistrationBean 是 Spring Boot 提供的用于注册 Servlet Filter 的类。
     *
     * @param filter 要注册的过滤器实例
     * @param order 过滤器的执行顺序（数字越小，优先级越高，越先执行）
     * @param <T> 过滤器类型的泛型参数
     * @return FilterRegistrationBean<T> 过滤器注册 Bean
     *
     * <p>为什么需要设置顺序？
     * 在一个 Web 应用中可能有多个过滤器，通过 order 控制它们的执行顺序很重要。
     * 例如：认证过滤器应该在日志过滤器之前执行。
     */
    private static <T extends Filter> FilterRegistrationBean<T> createFilterBean(T filter, Integer order) {
        // 创建 FilterRegistrationBean 实例，传入过滤器
        FilterRegistrationBean<T> bean = new FilterRegistrationBean<>(filter);
        // 设置过滤器的执行顺序
        bean.setOrder(order);
        return bean;
    }

    /**
     * 添加拦截器
     *
     * <p>功能说明：
     * 这个方法会向 Spring MVC 注册 ApiAccessLogInterceptor 拦截器。
     * 拦截器的作用是在非生产环境（如开发、测试环境）下，将 API 请求和响应信息
     * 打印到控制台，方便开发人员调试。
     *
     * <p>拦截器 vs 过滤器的区别：
     * <ul>
     *   <li>过滤器（Filter）：属于 Servlet 规范，在请求进入 Spring MVC 之前执行，主要用于通用的请求处理</li>
     *   <li>拦截器（Interceptor）：属于 Spring MVC，在 Controller 处理之前/之后执行，可以访问 Controller 方法信息</li>
     * </ul>
     *
     * <p>本项目中的分工：
     * <ul>
     *   <li>ApiAccessLogFilter：负责记录日志到数据库（所有环境）</li>
     *   <li>ApiAccessLogInterceptor：负责在控制台打印日志（仅非生产环境）</li>
     * </ul>
     *
     * @param registry 拦截器注册器，用于注册拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 ApiAccessLogInterceptor 拦截器
        // 默认会拦截所有请求，拦截器内部会判断是否为生产环境
        registry.addInterceptor(new ApiAccessLogInterceptor());
    }

}
