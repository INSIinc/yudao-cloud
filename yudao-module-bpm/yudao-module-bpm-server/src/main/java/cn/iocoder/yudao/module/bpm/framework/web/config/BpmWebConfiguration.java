package cn.iocoder.yudao.module.bpm.framework.web.config;

import cn.iocoder.yudao.framework.common.enums.WebFilterOrderEnum;
import cn.iocoder.yudao.framework.swagger.config.YudaoSwaggerAutoConfiguration;
import cn.iocoder.yudao.module.bpm.framework.web.core.FlowableWebFilter;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * BPM 模块的 Web 组件配置类。
 * <p>
 * 该类用于在 Spring Boot 应用中注册与 BPM（业务流程管理）相关的 Web 层组件，
 * 特别是针对 Flowable 工作流引擎的 Web 过滤器，以确保在处理 BPM 相关请求时
 * 能够自动设置必要的上下文（如用户身份、租户信息等）。
 * </p>
 *
 * @author 芋道源码
 */
@Configuration(proxyBeanMethods = false)
public class BpmWebConfiguration {

    /**
     * 注册 Flowable Web 过滤器（{@link FlowableWebFilter}）。
     * <p>
     * Flowable 是一个轻量级的工作流和 BPMN 引擎。在 Web 应用中，某些操作（如启动流程、
     * 完成任务等）需要将当前登录用户信息自动绑定到 Flowable 的上下文中，以便流程实例
     * 能记录正确的操作人、审批人等信息。
     * </p>
     * <p>
     * 通过 {@link FlowableWebFilter}，可以在每次 HTTP 请求开始时自动设置当前用户信息到
     * Flowable 的安全上下文（SecurityContext），并在请求结束时清理，从而实现“透明集成”。
     * </p>
     * <p>
     * 使用 {@link FilterRegistrationBean} 将该过滤器注册为 Spring 管理的 Servlet 过滤器，
     * 并通过 {@link WebFilterOrderEnum#FLOWABLE_FILTER} 指定其执行顺序，确保它在其他相关
     * 过滤器（如认证过滤器）之后、业务逻辑之前执行。
     * </p>
     *
     * @return 配置好的 {@link FilterRegistrationBean} 实例，用于注册 FlowableWebFilter
     */
    @Bean
    public FilterRegistrationBean<FlowableWebFilter> flowableWebFilter() {
        // 创建 FilterRegistrationBean，用于将自定义过滤器注册到 Servlet 容器
        FilterRegistrationBean<FlowableWebFilter> registrationBean = new FilterRegistrationBean<>();

        // 设置实际的过滤器实例
        registrationBean.setFilter(new FlowableWebFilter());

        // 设置过滤器的执行顺序。顺序值越小，越早执行。
        // FLOWABLE_FILTER 是一个预定义的常量，确保该过滤器在认证之后、业务逻辑之前执行
        registrationBean.setOrder(WebFilterOrderEnum.FLOWABLE_FILTER);

        // 注意：此处未显式设置 urlPatterns，默认匹配所有路径（"/*"）
        // 如果未来需要限制作用路径，可调用 registrationBean.addUrlPatterns("/bpm/*") 等方法

        return registrationBean;
    }

}