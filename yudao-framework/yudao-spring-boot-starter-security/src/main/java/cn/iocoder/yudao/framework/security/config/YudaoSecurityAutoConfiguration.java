package cn.iocoder.yudao.framework.security.config;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.security.core.context.TransmittableThreadLocalSecurityContextHolderStrategy;
import cn.iocoder.yudao.framework.security.core.filter.TokenAuthenticationFilter;
import cn.iocoder.yudao.framework.security.core.handler.AccessDeniedHandlerImpl;
import cn.iocoder.yudao.framework.security.core.handler.AuthenticationEntryPointImpl;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkServiceImpl;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.config.MethodInvokingFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Spring Security 自动配置类，主要用于相关组件的配置
 *
 * 注意，不能和 {@link YudaoWebSecurityConfigurerAdapter} 用一个，原因是会导致初始化报错。
 * 参见 https://stackoverflow.com/questions/53847050/spring-boot-delegatebuilder-cannot-be-null-on-autowiring-authenticationmanager 文档。
 *
 * @author 芋道源码
 */
// @AutoConfiguration: 标记这是一个自动配置类，Spring Boot 会自动加载它
// 作用：当项目启动时，这个类会被自动扫描并执行，无需手动配置
@AutoConfiguration
// @AutoConfigureOrder(-1): 设置自动配置的加载顺序，-1 表示优先级最高
// 原因：需要在 Spring Security 的默认配置之前加载，确保我们的自定义配置能够生效
@AutoConfigureOrder(-1) // 目的：先于 Spring Security 自动配置，避免一键改包后，org.* 基础包无法生效
// @EnableConfigurationProperties: 启用配置属性类
// 作用：让 SecurityProperties 类能够读取 application.yml 中 yudao.security 开头的配置项
@EnableConfigurationProperties(SecurityProperties.class)
public class YudaoSecurityAutoConfiguration {

    // @Resource: Spring 的依赖注入注解，自动装配 SecurityProperties 对象
    // SecurityProperties: 安全配置属性类，包含了从配置文件读取的各种安全相关配置
    @Resource
    private SecurityProperties securityProperties;

    /**
     * 认证失败处理类 Bean
     *
     * 作用：当用户未登录或 Token 无效时，这个处理器会被调用
     * 使用场景：用户访问需要登录的接口，但没有提供有效的登录凭证
     * 返回结果：通常返回 401 状态码和错误提示信息
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return new AuthenticationEntryPointImpl();
    }

    /**
     * 权限不够处理器 Bean
     *
     * 作用：当用户已登录，但没有足够权限访问某个资源时，这个处理器会被调用
     * 使用场景：普通用户尝试访问管理员才能访问的接口
     * 返回结果：通常返回 403 状态码和"权限不足"的提示信息
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return new AccessDeniedHandlerImpl();
    }

    /**
     * Spring Security 加密器
     * 考虑到安全性，这里采用 BCryptPasswordEncoder 加密器
     *
     * 作用：用于加密和验证用户密码
     * BCrypt 算法特点：
     * 1. 单向加密，无法解密，只能验证
     * 2. 同一个密码每次加密结果不同（因为有随机盐值）
     * 3. 加密强度可调节（通过 passwordEncoderLength 参数）
     *
     * 使用场景：
     * - 用户注册时：对明文密码进行加密后存入数据库
     * - 用户登录时：将输入的明文密码加密后与数据库中的密文比对
     *
     * @see <a href="http://stackabuse.com/password-encoding-with-spring-security/">Password Encoding with Spring Security</a>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 参数说明：passwordEncoderLength 是加密复杂度（默认为 4）
        // 数值越大，加密越安全，但计算开销也越大，登录验证会越慢
        return new BCryptPasswordEncoder(securityProperties.getPasswordEncoderLength());
    }

    /**
     * Token 认证过滤器 Bean
     *
     * 作用：这是一个过滤器，会拦截所有 HTTP 请求，检查请求中的 Token
     * 工作流程：
     * 1. 从请求的 Header 或参数中提取 Token（如：Authorization: Bearer xxx）
     * 2. 验证 Token 是否有效（是否过期、是否被篡改等）
     * 3. 如果 Token 有效，从 Token 中解析出用户信息，并设置到 Spring Security 上下文中
     * 4. 如果 Token 无效，则拒绝请求或交给认证失败处理器处理
     *
     * 参数说明：
     * - globalExceptionHandler: 全局异常处理器，用于统一处理过滤器中的异常
     * - oauth2TokenApi: Token 操作接口，用于验证和解析 Token
     */
    @Bean
    public TokenAuthenticationFilter authenticationTokenFilter(GlobalExceptionHandler globalExceptionHandler,
                                                               OAuth2TokenCommonApi oauth2TokenApi) {
        return new TokenAuthenticationFilter(securityProperties, globalExceptionHandler, oauth2TokenApi);
    }

    /**
     * Security 框架服务 Bean
     *
     * @Bean("ss"): 将这个 Bean 命名为 "ss"（Spring Security 的缩写）
     * 作用：提供权限校验的便捷方法，可以在代码中方便地检查用户权限
     *
     * 使用示例：
     * - 在 Controller 方法上使用：@PreAuthorize("@ss.hasPermission('system:user:list')")
     * - 在代码中使用：if (ss.hasPermission("system:user:delete")) { ... }
     *
     * 参数说明：
     * - permissionApi: 权限操作接口，用于查询用户是否具有某个权限
     */
    @Bean("ss") // 使用 Spring Security 的缩写，方便使用
    public SecurityFrameworkService securityFrameworkService(PermissionCommonApi permissionApi) {
        return new SecurityFrameworkServiceImpl(permissionApi);
    }

    /**
     * 配置 Spring Security 的“上下文存储方式”——让它支持在异步线程中也能获取当前登录用户信息。
     *
     * 为什么需要这样做？
     * - 默认情况下，Spring Security 把用户信息（SecurityContext）存在 ThreadLocal 中。
     * - ThreadLocal 的特点是：只在当前线程有效，线程之间不共享。
     * - 但当我们使用线程池、@Async 异步方法、或消息队列消费者时，会创建新的子线程。
     * - 这些子线程默认拿不到父线程里的用户信息，导致 SecurityContext 为空。
     *
     * 怎么解决？
     * - 我们换一种存储方式：使用 TransmittableThreadLocal（可传递的 ThreadLocal）。
     * - 它是阿里开源的工具（TTL），能在创建子线程时，自动把父线程的用户上下文“传递”过去。
     * - 这样，异步任务里也能通过 SecurityContextHolder 获取到当前登录用户了。
     *
     * 具体怎么配置？
     * - Spring 不能直接调用静态方法，所以我们借助 MethodInvokingFactoryBean。
     * - 它的作用是在 Spring 启动时，自动调用下面这个方法：
     *       SecurityContextHolder.setStrategyName("XXX")
     * - 这里传入的是 TransmittableThreadLocalSecurityContextHolderStrategy 的类名，
     *   告诉 Spring Security：“请用这个策略来存储上下文”。
     *
     * 常见使用场景：
     * ✅ 在 @Async 异步方法中获取当前用户
     * ✅ 线程池处理任务时记录“谁操作的”
     * ✅ 消息队列消费者需要知道触发操作的用户是谁
     */
    @Bean
    public MethodInvokingFactoryBean securityContextHolderMethodInvokingFactoryBean() {
        // 创建一个 Spring 提供的“方法调用工具”，用于调用静态方法
        MethodInvokingFactoryBean methodInvokingFactoryBean = new MethodInvokingFactoryBean();

        // 告诉它：我们要调用的是 SecurityContextHolder 这个类
        methodInvokingFactoryBean.setTargetClass(SecurityContextHolder.class);

        // 调用它的 setStrategyName 静态方法
        methodInvokingFactoryBean.setTargetMethod("setStrategyName");

        // 传入参数：指定使用支持线程传递的上下文策略
        methodInvokingFactoryBean.setArguments(TransmittableThreadLocalSecurityContextHolderStrategy.class.getName());

        return methodInvokingFactoryBean;
    }

}
