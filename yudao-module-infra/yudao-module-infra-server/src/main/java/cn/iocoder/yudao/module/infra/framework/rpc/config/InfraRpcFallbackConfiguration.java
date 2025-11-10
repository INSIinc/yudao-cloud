package cn.iocoder.yudao.module.infra.framework.rpc.config;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiAccessLogCommonApi;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.module.infra.api.logger.ApiAccessLogApiImpl;
import cn.iocoder.yudao.module.infra.api.logger.ApiErrorLogApiImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Infra 模块 RPC Fallback 配置类
 *
 * <p>用于解决单体模式下（禁用 OpenFeign）的 API 依赖注入问题
 *
 * <p>问题背景：
 * <ul>
 *   <li>在微服务模式下，ApiAccessLogCommonApi 等接口通过 @FeignClient 创建代理 Bean</li>
 *   <li>在单体模式下，yudao-server/pom.xml 排除了 OpenFeign 依赖（第155-160行）</li>
 *   <li>虽然有实现类（如 ApiAccessLogApiImpl），但它们被注册为 @RestController Bean，类型是实现类而非接口</li>
 *   <li>当其他模块需要注入接口类型（如 ApiAccessLogCommonApi）时，会找不到对应的 Bean</li>
 *   <li>生产环境开启访问日志后（yudao.access-log.enable=true），启动时会报错：
 *       "required a bean of type 'ApiAccessLogCommonApi' that could not be found"</li>
 * </ul>
 *
 * <p>解决方案：
 * <ul>
 *   <li>通过 @ConditionalOnMissingClass 检测 OpenFeign 的 FeignClient 类是否存在</li>
 *   <li>如果不存在（单体模式），手动创建接口类型的 Bean，引用实现类</li>
 *   <li>这样其他模块就可以通过接口类型注入，实际使用的是本地实现类（而非远程 RPC 调用）</li>
 * </ul>
 *
 * @author Claude Code
 * @see ApiAccessLogCommonApi
 * @see ApiErrorLogCommonApi
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingClass("org.springframework.cloud.openfeign.FeignClient")
public class InfraRpcFallbackConfiguration {

    /**
     * 在单体模式下，将 ApiAccessLogApiImpl 注册为 ApiAccessLogCommonApi 类型的 Bean
     *
     * <p>这样当 YudaoApiLogAutoConfiguration 需要注入 ApiAccessLogCommonApi 时，
     * 可以找到这个 Bean，实际调用的是本地的 ApiAccessLogApiImpl 实现。
     *
     * @param apiAccessLogApiImpl 实现类（由 Spring 自动注入）
     * @return ApiAccessLogCommonApi 接口类型的 Bean
     */
    @Bean
    public ApiAccessLogCommonApi apiAccessLogCommonApi(ApiAccessLogApiImpl apiAccessLogApiImpl) {
        return apiAccessLogApiImpl;
    }

    /**
     * 在单体模式下，将 ApiErrorLogApiImpl 注册为 ApiErrorLogCommonApi 类型的 Bean
     *
     * <p>错误日志 API 同样需要处理，避免其他地方注入 ApiErrorLogCommonApi 时找不到 Bean。
     *
     * @param apiErrorLogApiImpl 实现类（由 Spring 自动注入）
     * @return ApiErrorLogCommonApi 接口类型的 Bean
     */
    @Bean
    public ApiErrorLogCommonApi apiErrorLogCommonApi(ApiErrorLogApiImpl apiErrorLogApiImpl) {
        return apiErrorLogApiImpl;
    }

}
