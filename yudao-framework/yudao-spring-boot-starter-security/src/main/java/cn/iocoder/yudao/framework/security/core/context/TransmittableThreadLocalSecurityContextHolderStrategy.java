package cn.iocoder.yudao.framework.security.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.util.Assert;

/**
 * 基于 TransmittableThreadLocal 实现的 Security Context 持有者策略
 * 目的是，避免 @Async 等异步执行时，原生 ThreadLocal 的丢失问题
 *
 * 【背景知识】
 * 1. SecurityContext：Spring Security 中用于存储当前登录用户信息的上下文对象
 * 2. ThreadLocal：Java 提供的线程本地变量，每个线程都有自己独立的副本
 * 3. 问题：当使用线程池或异步任务时，ThreadLocal 无法自动传递到子线程
 * 4. 解决方案：使用阿里巴巴的 TransmittableThreadLocal，可以在线程池场景下自动传递上下文
 *
 * 【使用场景】
 * - 异步任务（@Async）中需要获取当前登录用户信息
 * - 线程池执行的任务中需要访问安全上下文
 * - 跨线程传递用户认证信息
 *
 * @author 芋道源码
 */
public class TransmittableThreadLocalSecurityContextHolderStrategy implements SecurityContextHolderStrategy {

    /**
     * 使用 TransmittableThreadLocal 作为上下文存储容器
     *
     * 【说明】
     * - static final：全局唯一的静态常量，所有线程共享这个容器对象
     * - TransmittableThreadLocal：阿里巴巴开源的增强版 ThreadLocal
     * - 特点：可以在使用线程池等会池化复用线程的执行组件情况下，提供ThreadLocal值的传递功能
     * - SecurityContext：存储的值类型，包含用户的认证和授权信息
     */
    private static final ThreadLocal<SecurityContext> CONTEXT_HOLDER = new TransmittableThreadLocal<>();

    /**
     * 清除当前线程的安全上下文
     *
     * 【作用】
     * - 从当前线程中移除 SecurityContext 对象
     * - 通常在请求结束、用户登出或需要清理资源时调用
     *
     * 【注意事项】
     * - 调用后，当前线程将无法获取到之前设置的用户信息
     * - 防止内存泄漏，特别是在使用线程池的场景下
     */
    @Override
    public void clearContext() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 获取当前线程的安全上下文
     *
     * 【逻辑说明】
     * 1. 首先尝试从 ThreadLocal 中获取 SecurityContext
     * 2. 如果获取不到（返回 null），说明当前线程还没有设置过上下文
     * 3. 则创建一个空的 SecurityContext 并存储到 ThreadLocal 中
     * 4. 最终保证返回的 SecurityContext 不为 null
     *
     * 【应用场景】
     * - Spring Security 框架在处理请求时会调用此方法获取当前用户信息
     * - 业务代码中通过 SecurityContextHolder 获取当前登录用户时会调用
     *
     * @return 当前线程的 SecurityContext，永远不会返回 null
     */
    @Override
    public SecurityContext getContext() {
        // 从 ThreadLocal 中获取当前线程存储的 SecurityContext
        SecurityContext ctx = CONTEXT_HOLDER.get();

        // 如果当前线程还没有 SecurityContext，则创建一个新的空上下文
        if (ctx == null) {
            ctx = createEmptyContext();
            CONTEXT_HOLDER.set(ctx);
        }

        return ctx;
    }

    /**
     * 设置当前线程的安全上下文
     *
     * 【参数说明】
     * @param context 要设置的 SecurityContext 对象，包含用户的认证信息
     *
     * 【调用时机】
     * - 用户登录成功后，Spring Security 会调用此方法保存认证信息
     * - 从 Token 或 Session 中恢复用户信息时
     * - 手动设置当前线程的用户上下文时
     *
     * 【注意事项】
     * - context 参数不能为 null，否则会抛出 IllegalArgumentException 异常
     * - 设置后，该 SecurityContext 只在当前线程及其子线程中有效（TransmittableThreadLocal 特性）
     */
    @Override
    public void setContext(SecurityContext context) {
        // 断言：确保传入的 context 不为 null，如果为 null 则抛出异常
        Assert.notNull(context, "Only non-null SecurityContext instances are permitted");

        // 将 SecurityContext 存储到当前线程的 ThreadLocal 中
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 创建一个空的安全上下文对象
     *
     * 【说明】
     * - SecurityContextImpl 是 Spring Security 提供的 SecurityContext 接口的默认实现
     * - 空上下文表示当前没有认证信息（未登录状态）
     *
     * 【使用场景】
     * - 初始化线程的安全上下文
     * - 清除用户信息后需要一个干净的上下文对象
     *
     * @return 新创建的空 SecurityContext 实例
     */
    @Override
    public SecurityContext createEmptyContext() {
        return new SecurityContextImpl();
    }

}
