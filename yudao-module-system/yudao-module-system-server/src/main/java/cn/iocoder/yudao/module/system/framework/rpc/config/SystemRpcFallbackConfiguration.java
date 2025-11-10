package cn.iocoder.yudao.module.system.framework.rpc.config;

import cn.iocoder.yudao.framework.common.biz.system.dict.DictDataCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.logger.OperateLogCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.tenant.TenantCommonApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApiImpl;
import cn.iocoder.yudao.module.system.api.logger.OperateLogApiImpl;
import cn.iocoder.yudao.module.system.api.oauth2.OAuth2TokenApiImpl;
import cn.iocoder.yudao.module.system.api.permission.PermissionApiImpl;
import cn.iocoder.yudao.module.system.api.tenant.TenantApiImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * System 模块 RPC Fallback 配置类
 *
 * <p>用于解决单体模式下（禁用 OpenFeign）的 API 依赖注入问题
 *
 * <p>问题背景：
 * <ul>
 *   <li>在微服务模式下，PermissionCommonApi 等接口通过 @FeignClient 创建代理 Bean</li>
 *   <li>在单体模式下，yudao-server/pom.xml 排除了 OpenFeign 依赖（第155-160行）</li>
 *   <li>虽然有实现类（如 PermissionApiImpl），但它们被注册为 @RestController Bean，类型是实现类而非 CommonApi 接口</li>
 *   <li>当其他模块需要注入 CommonApi 接口类型时，会找不到对应的 Bean</li>
 *   <li>典型错误：
 *       "required a bean of type 'PermissionCommonApi' that could not be found"
 *       "required a bean of type 'OAuth2TokenCommonApi' that could not be found"
 *       "required a bean of type 'TenantCommonApi' that could not be found"</li>
 * </ul>
 *
 * <p>解决方案：
 * <ul>
 *   <li>通过 @ConditionalOnMissingClass 检测 OpenFeign 的 FeignClient 类是否存在</li>
 *   <li>如果不存在（单体模式），手动创建 CommonApi 接口类型的 Bean，引用实现类</li>
 *   <li>这样其他模块就可以通过 CommonApi 接口类型注入，实际使用的是本地实现类（而非远程 RPC 调用）</li>
 * </ul>
 *
 * <p>涉及的核心功能：
 * <ul>
 *   <li>权限验证：PermissionCommonApi - 被 SecurityFrameworkService 依赖</li>
 *   <li>令牌管理：OAuth2TokenCommonApi - 被认证和授权模块依赖</li>
 *   <li>租户管理：TenantCommonApi - 被租户拦截器依赖</li>
 *   <li>字典服务：DictDataCommonApi - 被字典相关功能依赖</li>
 *   <li>操作日志：OperateLogCommonApi - 被日志记录功能依赖</li>
 * </ul>
 *
 * @author Claude Code
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingClass("org.springframework.cloud.openfeign.FeignClient")
public class SystemRpcFallbackConfiguration {

    /**
     * 在单体模式下，将 PermissionApiImpl 注册为 PermissionCommonApi 类型的 Bean
     *
     * <p>PermissionCommonApi 用于权限验证、角色验证和数据权限查询，
     * 被 SecurityFrameworkService 等安全相关组件依赖。
     *
     * <p>注意：PermissionApiImpl 已经标注了 @Primary 注解，这是因为它同时实现了
     * PermissionApi 和 PermissionCommonApi 两个接口，需要明确优先级。
     *
     * @param permissionApiImpl 实现类（由 Spring 自动注入）
     * @return PermissionCommonApi 接口类型的 Bean
     */
    @Bean
    public PermissionCommonApi permissionCommonApi(PermissionApiImpl permissionApiImpl) {
        return permissionApiImpl;
    }

    /**
     * 在单体模式下，将 DictDataApiImpl 注册为 DictDataCommonApi 类型的 Bean
     *
     * <p>DictDataCommonApi 用于字典数据的查询和缓存管理，提供系统字典功能。
     *
     * @param dictDataApiImpl 实现类（由 Spring 自动注入）
     * @return DictDataCommonApi 接口类型的 Bean
     */
    @Bean
    public DictDataCommonApi dictDataCommonApi(DictDataApiImpl dictDataApiImpl) {
        return dictDataApiImpl;
    }

    /**
     * 在单体模式下，将 OperateLogApiImpl 注册为 OperateLogCommonApi 类型的 Bean
     *
     * <p>OperateLogCommonApi 用于记录用户的操作日志，包括操作人、操作内容、操作时间等信息。
     *
     * @param operateLogApiImpl 实现类（由 Spring 自动注入）
     * @return OperateLogCommonApi 接口类型的 Bean
     */
    @Bean
    public OperateLogCommonApi operateLogCommonApi(OperateLogApiImpl operateLogApiImpl) {
        return operateLogApiImpl;
    }

    /**
     * 在单体模式下，将 OAuth2TokenApiImpl 注册为 OAuth2TokenCommonApi 类型的 Bean
     *
     * <p>OAuth2TokenCommonApi 用于 OAuth2 令牌的创建、校验、刷新和删除，
     * 是认证和授权系统的核心组件之一。
     *
     * @param oauth2TokenApiImpl 实现类（由 Spring 自动注入）
     * @return OAuth2TokenCommonApi 接口类型的 Bean
     */
    @Bean
    public OAuth2TokenCommonApi oauth2TokenCommonApi(OAuth2TokenApiImpl oauth2TokenApiImpl) {
        return oauth2TokenApiImpl;
    }

    /**
     * 在单体模式下，将 TenantApiImpl 注册为 TenantCommonApi 类型的 Bean
     *
     * <p>TenantCommonApi 用于多租户的校验和管理，确保数据隔离和租户权限控制。
     * 被租户拦截器（TenantContextWebInterceptor）等组件依赖。
     *
     * @param tenantApiImpl 实现类（由 Spring 自动注入）
     * @return TenantCommonApi 接口类型的 Bean
     */
    @Bean
    public TenantCommonApi tenantCommonApi(TenantApiImpl tenantApiImpl) {
        return tenantApiImpl;
    }

}
