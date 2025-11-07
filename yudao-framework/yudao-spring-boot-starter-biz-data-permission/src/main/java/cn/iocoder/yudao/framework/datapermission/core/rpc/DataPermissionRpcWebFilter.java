package cn.iocoder.yudao.framework.datapermission.core.rpc;

import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder;
import cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * 数据权限 RPC Web 过滤器
 *
 * 针对 {@link DataPermissionRequestInterceptor} 的 RPC 调用，设置 {@link DataPermissionContextHolder} 的上下文
 *
 * 功能说明：
 * 1. 当服务 A 通过 RPC 调用服务 B 时，服务 A 会通过 DataPermissionRequestInterceptor 在请求头中传递数据权限的启用状态
 * 2. 服务 B 接收到请求后，通过本过滤器读取请求头中的数据权限配置
 * 3. 如果请求头标识数据权限被禁用，则在处理该请求时忽略数据权限规则
 * 4. 这样可以保证跨服务调用时，数据权限的状态能够正确传递和生效
 *
 * @author 芋道源码
 */
public class DataPermissionRpcWebFilter extends OncePerRequestFilter {

    /**
     * 执行过滤逻辑
     *
     * @param request  HTTP 请求对象，包含客户端发送的所有信息
     * @param response HTTP 响应对象，用于向客户端返回数据
     * @param chain    过滤器链，用于将请求传递给下一个过滤器或最终的目标资源
     * @throws ServletException 当过滤器处理过程中发生 Servlet 相关异常
     * @throws IOException      当过滤器处理过程中发生 I/O 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 从请求头中获取数据权限的启用状态
        // DataPermissionRequestInterceptor.ENABLE_HEADER_NAME 是一个常量，表示存储数据权限状态的请求头名称
        String enable = request.getHeader(DataPermissionRequestInterceptor.ENABLE_HEADER_NAME);

        // 判断数据权限是否被禁用
        // 如果请求头中的值等于 "false"（布尔值 FALSE 转为字符串），说明调用方希望禁用数据权限
        if (Objects.equals(enable, Boolean.FALSE.toString())) {
            // 使用 DataPermissionUtils.executeIgnore 方法执行后续逻辑，该方法会忽略数据权限规则
            // executeIgnore 接收一个 Runnable 参数，在执行期间会设置上下文标记，告诉系统忽略数据权限
            DataPermissionUtils.executeIgnore(() -> {
                try {
                    // 继续执行过滤器链，处理请求
                    // 在这个过滤器链执行期间，所有的数据权限检查都会被忽略
                    chain.doFilter(request, response);
                } catch (IOException | ServletException e) {
                    // 由于 Runnable 接口不允许抛出受检异常，所以需要将异常包装成运行时异常抛出
                    throw new RuntimeException(e);
                }
            });
        } else {
            // 如果数据权限未被禁用（请求头为空或为其他值），则正常执行过滤器链
            // 此时数据权限规则会正常生效，对数据访问进行权限控制
            chain.doFilter(request, response);
        }
    }

}
