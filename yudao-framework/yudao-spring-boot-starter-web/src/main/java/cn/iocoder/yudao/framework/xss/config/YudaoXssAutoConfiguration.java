package cn.iocoder.yudao.framework.xss.config;

import cn.iocoder.yudao.framework.common.enums.WebFilterOrderEnum;
import cn.iocoder.yudao.framework.xss.core.clean.JsoupXssCleaner;
import cn.iocoder.yudao.framework.xss.core.clean.XssCleaner;
import cn.iocoder.yudao.framework.xss.core.filter.XssFilter;
import cn.iocoder.yudao.framework.xss.core.json.XssStringJsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.util.PathMatcher;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static cn.iocoder.yudao.framework.web.config.YudaoWebAutoConfiguration.createFilterBean;

/**
 * XSS 跨站脚本攻击防护自动配置类
 *
 * <p>什么是 XSS 攻击？
 * XSS（Cross-Site Scripting）跨站脚本攻击是一种常见的 Web 安全漏洞。
 * 攻击者通过在网页中注入恶意脚本代码（如 JavaScript），当其他用户浏览该网页时，
 * 恶意脚本会被执行，从而窃取用户信息、劫持会话等。
 *
 * <p>例如：用户在评论框输入 {@code <script>alert('XSS攻击')</script>}，
 * 如果不做防护，这段脚本会在其他用户浏览评论时执行。
 *
 * <p>本配置类的作用：
 * 自动配置 XSS 防护功能，通过过滤器和 JSON 反序列化器清理用户输入的危险脚本，
 * 防止恶意代码注入到系统中。
 *
 * @author 芋道源码
 */
@AutoConfiguration // Spring Boot 自动配置注解，应用启动时会自动加载此配置类
@EnableConfigurationProperties(XssProperties.class) // 启用 XSS 配置属性类，可以从 application.yml 读取配置
@ConditionalOnProperty(prefix = "yudao.xss", name = "enable", havingValue = "true", matchIfMissing = true) // 条件注解：只有当配置文件中 yudao.xss.enable=true 时才生效，默认为 true（matchIfMissing=true 表示配置缺失时默认启用）
public class YudaoXssAutoConfiguration implements WebMvcConfigurer {

    /**
     * 创建 XSS 清理器 Bean
     *
     * <p>XSS 清理器的作用：
     * 负责清理和过滤用户输入中的危险内容，例如 {@code <script>} 标签、
     * {@code <iframe>} 标签、{@code onclick} 等事件属性。
     *
     * <p>使用 Jsoup 库实现：
     * Jsoup 是一个强大的 HTML 解析和清理库，可以安全地移除危险的 HTML 标签和属性，
     * 同时保留安全的内容格式。
     *
     * @return XssCleaner 实例，使用 Jsoup 实现的清理器
     */
    @Bean // 将方法返回值注册为 Spring 容器中的 Bean
    @ConditionalOnMissingBean(XssCleaner.class) // 条件注解：只有当容器中不存在 XssCleaner 类型的 Bean 时才创建，允许用户自定义实现
    public XssCleaner xssCleaner() {
        return new JsoupXssCleaner(); // 返回基于 Jsoup 库实现的 XSS 清理器
    }

    /**
     * 注册 Jackson JSON 反序列化定制器，用于处理 JSON 格式请求参数的 XSS 过滤
     *
     * <p>应用场景：
     * 当前端通过 Ajax 发送 JSON 格式的数据时（Content-Type: application/json），
     * Spring Boot 使用 Jackson 库将 JSON 字符串转换为 Java 对象。
     * 此定制器会在反序列化过程中，对所有 String 类型的字段进行 XSS 清理。
     *
     * <p>举例说明：
     * 前端发送 JSON：{"name": "<script>alert('xss')</script>", "age": 18}
     * 经过此定制器处理后，name 字段的值会被清理为安全内容。
     *
     * <p>技术细节：
     * - 使用反序列化器（Deserializer）在数据进入系统时进行过滤（推荐方式）
     * - 也可以使用序列化器（Serializer）在数据输出时过滤，但不如反序列化时过滤安全
     *
     * @param properties XSS 配置属性，包含启用开关、排除路径等配置
     * @param pathMatcher 路径匹配器，用于判断当前请求路径是否需要进行 XSS 过滤
     * @param xssCleaner XSS 清理器，执行具体的内容清理工作
     * @return Jackson2ObjectMapperBuilderCustomizer 定制器实例
     */
    @Bean // 将方法返回值注册为 Spring 容器中的 Bean
    @ConditionalOnMissingBean(name = "xssJacksonCustomizer") // 条件注解：只有当容器中不存在名为 xssJacksonCustomizer 的 Bean 时才创建
    @ConditionalOnProperty(value = "yudao.xss.enable", havingValue = "true") // 条件注解：只有当配置 yudao.xss.enable=true 时才生效
    public Jackson2ObjectMapperBuilderCustomizer xssJacksonCustomizer(XssProperties properties,
                                                                      PathMatcher pathMatcher,
                                                                      XssCleaner xssCleaner) {
        // 返回一个定制器，为 Jackson 的 ObjectMapper 添加自定义的 String 类型反序列化器
        // 在反序列化时进行 xss 过滤，可以替换使用 XssStringJsonSerializer，在序列化时进行处理
        return builder ->
                builder.deserializerByType(String.class, new XssStringJsonDeserializer(properties, pathMatcher, xssCleaner));
    }

    /**
     * 创建 XSS 过滤器 Bean，用于过滤 HTTP 请求中的 XSS 攻击内容
     *
     * <p>过滤器的作用：
     * 拦截所有 HTTP 请求，对请求参数（如表单参数、URL 参数）进行 XSS 清理，
     * 防止恶意脚本通过请求参数注入到系统中。
     *
     * <p>应用场景：
     * 主要处理表单提交（Content-Type: application/x-www-form-urlencoded）
     * 和 URL 查询参数（如 ?name=<script>alert('xss')</script>）。
     *
     * <p>与 JSON 反序列化器的区别：
     * - XssFilter：处理表单参数和 URL 参数
     * - XssStringJsonDeserializer：处理 JSON 格式的请求体
     * 两者配合使用，实现全面的 XSS 防护。
     *
     * <p>过滤器顺序：
     * 通过 WebFilterOrderEnum.XSS_FILTER 指定过滤器的执行顺序，
     * 确保在合适的时机进行 XSS 过滤。
     *
     * @param properties XSS 配置属性，包含启用开关、排除路径等配置
     * @param pathMatcher 路径匹配器，用于判断当前请求路径是否需要进行 XSS 过滤
     * @param xssCleaner XSS 清理器，执行具体的内容清理工作
     * @return FilterRegistrationBean 过滤器注册对象，用于将过滤器注册到 Servlet 容器中
     */
    @Bean // 将方法返回值注册为 Spring 容器中的 Bean
    @ConditionalOnBean(XssCleaner.class) // 条件注解：只有当容器中存在 XssCleaner 类型的 Bean 时才创建此过滤器
    public FilterRegistrationBean<XssFilter> xssFilter(XssProperties properties, PathMatcher pathMatcher, XssCleaner xssCleaner) {
        // 创建并注册 XSS 过滤器，指定过滤器执行顺序
        return createFilterBean(new XssFilter(properties, pathMatcher, xssCleaner), WebFilterOrderEnum.XSS_FILTER);
    }

}
