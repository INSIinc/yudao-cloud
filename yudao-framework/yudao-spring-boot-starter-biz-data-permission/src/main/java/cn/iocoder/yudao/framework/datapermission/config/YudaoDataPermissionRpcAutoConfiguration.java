package cn.iocoder.yudao.framework.datapermission.config;

import cn.iocoder.yudao.framework.datapermission.core.rpc.DataPermissionRequestInterceptor;
import cn.iocoder.yudao.framework.datapermission.core.rpc.DataPermissionRpcWebFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import static cn.iocoder.yudao.framework.common.enums.WebFilterOrderEnum.TENANT_CONTEXT_FILTER;

/**
 * 数据权限针对 RPC 的自动配置类
 * <p>
 * 【配置类的作用】
 * 这个类是 Spring Boot 的自动配置类，用于在微服务架构中实现数据权限在不同服务之间的传递和生效。
 * 它会自动注册两个核心组件，让数据权限能够在 RPC（远程过程调用）场景下正常工作。
 * <p>
 * 【为什么需要这个配置】
 * 在微服务架构中，服务 A 调用服务 B 时，如果服务 A 禁用了数据权限，服务 B 也应该禁用数据权限，
 * 否则可能导致数据不一致。这个配置类就是为了解决这个问题。
 * <p>
 * 【生效条件】
 *
 * @author 芋道源码
 * @ConditionalOnClass(name = "feign.RequestInterceptor") 表示只有当项目中存在 Feign 客户端时，
 * 这个配置类才会生效。这是因为数据权限的 RPC 传递是基于 Feign 实现的。
 * <p>
 * 【核心组件】
 * 1. DataPermissionRequestInterceptor：Feign 请求拦截器，在发起 RPC 调用时，将数据权限状态添加到请求头
 * 2. DataPermissionRpcWebFilter：Web 过滤器，在接收 RPC 请求时，从请求头读取数据权限状态并应用
 */
@AutoConfiguration // Spring Boot 自动配置注解，表示这是一个自动配置类，会在应用启动时自动加载
@ConditionalOnClass(name = "feign.RequestInterceptor") // 条件注解：只有当类路径中存在 Feign 的 RequestInterceptor 类时，这个配置才生效
public class YudaoDataPermissionRpcAutoConfiguration {

    /**
     * 注册数据权限的 Feign 请求拦截器
     * <p>
     * 【Bean 的作用】
     * 这个 Bean 会在 Feign 发起 RPC 调用前，自动将当前线程的数据权限状态（启用/禁用）添加到 HTTP 请求头中。
     * <p>
     * 【工作流程】
     * 1. 当服务 A 通过 Feign 调用服务 B 的接口时，这个拦截器会自动执行
     * 2. 拦截器从当前线程的上下文中读取数据权限配置
     * 3. 如果数据权限被禁用（@DataPermission(enable=false)），则在请求头中添加标记
     * 4. 服务 B 接收到请求后，会通过 DataPermissionRpcWebFilter 读取这个标记
     * <p>
     * 【使用场景举例】
     * 假设在服务 A 的某个方法上标注了 @DataPermission(enable=false)，表示该方法不需要数据权限过滤。
     * 当这个方法内部调用服务 B 的接口时，服务 B 也应该不进行数据权限过滤，否则可能查询不到数据。
     * 这个拦截器就是负责将"禁用数据权限"这个信息传递给服务 B。
     *
     * @return DataPermissionRequestInterceptor 实例，Feign 会自动使用这个拦截器
     */
    @Bean // Spring 的 Bean 注解，表示将这个方法的返回值注册为 Spring 容器中的一个 Bean
    public DataPermissionRequestInterceptor dataPermissionRequestInterceptor() {
        return new DataPermissionRequestInterceptor();
    }

    /**
     * 注册数据权限的 RPC Web 过滤器
     * <p>
     * 【Bean 的作用】
     * 这个 Bean 是一个 Servlet 过滤器，用于在接收 RPC 请求时，从 HTTP 请求头中读取数据权限状态并应用。
     * 它是 DataPermissionRequestInterceptor 的配对组件：一个负责发送，一个负责接收。
     * <p>
     * 【工作流程】
     * 1. 当服务接收到来自其他服务的 HTTP 请求时，这个过滤器会首先执行
     * 2. 过滤器检查请求头中是否包含数据权限标记（data-permission-enable）
     * 3. 如果标记值为 "false"，说明调用方希望禁用数据权限
     * 4. 过滤器会在当前线程上下文中设置"忽略数据权限"的标记
     * 5. 后续的业务代码在查询数据库时，就不会应用数据权限规则
     * <p>
     * 【FilterRegistrationBean 的作用】
     * FilterRegistrationBean 是 Spring Boot 提供的过滤器注册工具类，用于：
     * - 将自定义的过滤器注册到 Servlet 容器中
     * - 设置过滤器的执行顺序（order）
     * - 配置过滤器的 URL 匹配规则等
     * <p>
     * 【过滤器顺序说明】
     * setOrder(TENANT_CONTEXT_FILTER - 1) 表示这个过滤器的执行顺序在租户上下文过滤器之前。
     * 过滤器的 order 值越小，执行优先级越高。
     * 在租户过滤器之前执行是为了确保数据权限的设置能够先于租户信息的设置，这样更加稳妥。
     * <p>
     * 【使用场景举例】
     * 服务 A 调用服务 B 时，服务 A 的 DataPermissionRequestInterceptor 在请求头中添加了
     * "data-permission-enable: false"。当服务 B 接收到这个请求时，DataPermissionRpcWebFilter
     * 会读取这个请求头，并在处理请求期间忽略数据权限规则。
     *
     * @return FilterRegistrationBean 实例，包含了数据权限 RPC 过滤器的配置
     */
    @Bean // Spring 的 Bean 注解，表示将这个方法的返回值注册为 Spring 容器中的一个 Bean
    public FilterRegistrationBean<DataPermissionRpcWebFilter> dataPermissionRpcFilter() {
        // 创建过滤器注册 Bean
        // FilterRegistrationBean 是 Spring Boot 提供的过滤器注册工具，用于将自定义过滤器注册到 Servlet 容器
        FilterRegistrationBean<DataPermissionRpcWebFilter> registrationBean = new FilterRegistrationBean<>();

        // 设置要注册的过滤器实例
        // DataPermissionRpcWebFilter 继承自 OncePerRequestFilter，确保每个请求只执行一次
        registrationBean.setFilter(new DataPermissionRpcWebFilter());

        // 设置过滤器的执行顺序
        // TENANT_CONTEXT_FILTER 是租户上下文过滤器的顺序值
        // -1 表示在租户过滤器之前执行，确保数据权限的设置优先于租户信息的设置
        registrationBean.setOrder(TENANT_CONTEXT_FILTER - 1); // 顺序没有绝对的要求，在租户 Filter 前面稳妥点

        return registrationBean;
    }

}
