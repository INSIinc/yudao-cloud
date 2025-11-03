package cn.iocoder.yudao.module.bpm.framework.web.core;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Flowable Web 过滤器，用于在每个 HTTP 请求处理过程中，
 * 将当前登录用户的 ID 设置到 Flowable 工作流引擎的认证上下文中。
 * <p>
 * Flowable 引擎在执行流程操作（如启动流程、完成任务等）时，
 * 会从其内部的 {@link org.flowable.common.engine.impl.identity.Authentication}
 * 中获取当前操作用户 ID，用于记录流程操作日志（如任务的 assignee、历史记录等）。
 * 本过滤器的作用就是将系统当前登录用户（通过 SecurityFrameworkUtils 获取）
 * 自动同步到 Flowable 的认证上下文中，避免在业务代码中手动设置。
 * </p>
 *
 * <p>
 * 该过滤器继承自 {@link OncePerRequestFilter}，确保在整个请求生命周期中仅执行一次，
 * 避免在包含转发（forward）或包含（include）等场景下被重复调用。
 * </p>
 *
 * @author jason
 */
public class FlowableWebFilter extends OncePerRequestFilter {

    /**
     * 核心过滤逻辑：在请求开始时设置当前用户 ID 到 Flowable 上下文，
     * 并在请求结束（无论成功或异常）后清理该上下文，防止线程污染。
     *
     * @param request  当前 HTTP 请求对象
     * @param response 当前 HTTP 响应对象
     * @param chain    过滤器链，用于继续执行后续的过滤器或目标 Controller
     * @throws ServletException 如果在过滤过程中发生 Servlet 相关异常
     * @throws IOException      如果在读写请求/响应时发生 I/O 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            // 1. 从安全框架中获取当前登录用户的 ID（通常来自 JWT Token 或 Session）
            //    如果用户未登录，则 userId 为 null
            Long userId = SecurityFrameworkUtils.getLoginUserId();

            // 2. 如果用户已登录（userId 不为 null），则将其 ID 设置到 Flowable 的认证上下文中
            //    FlowableUtils 内部会调用 Flowable 引擎的 IdentityService.setAuthenticatedUserId()
            //    这样后续所有 Flowable 操作（如启动流程、完成任务）都会自动关联该用户
            if (userId != null) {
                FlowableUtils.setAuthenticatedUserId(userId);
            }

            // 3. 继续执行过滤器链，将请求交给下一个过滤器或最终的 Controller 处理
            chain.doFilter(request, response);

        } finally {
            // 4. 【关键】无论请求处理是否成功或抛出异常，都必须在 finally 块中
            //    清理 Flowable 的认证上下文（即清除线程本地变量中的用户 ID），
            //    防止在使用线程池（如 Tomcat 的工作线程复用）时发生用户信息“串号”问题。
            FlowableUtils.clearAuthenticatedUserId();
        }
    }
}