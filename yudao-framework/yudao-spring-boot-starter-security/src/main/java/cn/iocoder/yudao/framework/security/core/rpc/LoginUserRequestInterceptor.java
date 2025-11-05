package cn.iocoder.yudao.framework.security.core.rpc;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * LoginUser 的 RequestInterceptor 实现类：Feign 请求时，将 {@link LoginUser} 设置到 header 中，继续透传给被调用的服务
 *
 * 功能说明（适合初学者）：
 * 1. 这是一个 Feign 请求拦截器，用于在微服务之间调用时传递用户登录信息
 * 2. 当服务A通过Feign调用服务B时，会自动把当前登录用户的信息放到HTTP请求头中
 * 3. 这样服务B就能知道是哪个用户发起的请求，实现用户信息的透传
 *
 * 使用场景举例：
 * - 用户在前端登录后，访问服务A的某个接口
 * - 服务A需要调用服务B来完成业务逻辑
 * - 通过这个拦截器，服务B也能获取到当前登录用户的信息
 * - 这样服务B就可以进行权限校验、数据过滤等操作
 *
 * @author 芋道源码
 */
@Slf4j // Lombok注解：自动生成日志对象 log，用于打印日志
public class LoginUserRequestInterceptor implements RequestInterceptor {

    /**
     * 拦截器的核心方法：在每次 Feign 发起 HTTP 请求前，都会自动执行这个方法
     *
     * 执行流程：
     * 1. 获取当前线程中的登录用户信息
     * 2. 如果用户已登录，将用户信息序列化为JSON字符串
     * 3. 对JSON字符串进行URL编码（防止中文乱码）
     * 4. 将编码后的字符串放入HTTP请求头中
     * 5. 被调用的服务就可以从请求头中解析出用户信息
     *
     * @param requestTemplate Feign的请求模板对象，包含了请求的所有信息（URL、参数、头信息等）
     */
    @Override
    public void apply(RequestTemplate requestTemplate) {
        // 步骤1：从当前线程的上下文中获取登录用户信息
        // 说明：在用户登录后，用户信息会被存储在 ThreadLocal 中，方便在同一个线程中随时获取
        LoginUser user = SecurityFrameworkUtils.getLoginUser();

        // 步骤2：判断用户是否已登录
        // 如果用户未登录（user为null），则直接返回，不需要传递用户信息
        if (user == null) {
            return;
        }

        try {
            // 步骤3：将 LoginUser 对象转换为 JSON 字符串
            // 例如：{"id":1,"username":"admin","tenantId":1}
            String userStr = JsonUtils.toJsonString(user);

            // 步骤4：对 JSON 字符串进行 URL 编码
            // 原因：HTTP 请求头中不能直接包含中文、空格等特殊字符，需要编码
            // 例如：中文"管理员"会被编码为 %E7%AE%A1%E7%90%86%E5%91%98
            userStr = URLEncoder.encode(userStr, StandardCharsets.UTF_8);

            // 步骤5：将编码后的用户信息字符串设置到 HTTP 请求头中
            // LOGIN_USER_HEADER 是请求头的名称（key），userStr 是请求头的值（value）
            // 被调用的服务会从这个请求头中取出并解码，还原成 LoginUser 对象
            requestTemplate.header(SecurityFrameworkUtils.LOGIN_USER_HEADER, userStr);

        } catch (Exception ex) {
            // 异常处理：如果序列化或编码过程中出现异常
            // 1. 记录错误日志，方便排查问题
            // 2. 重新抛出异常，让上层感知到错误（避免静默失败）
            log.error("[apply][序列化 LoginUser({}) 发生异常]", user, ex);
            throw ex; // 将异常向上抛出
        }
    }

}
