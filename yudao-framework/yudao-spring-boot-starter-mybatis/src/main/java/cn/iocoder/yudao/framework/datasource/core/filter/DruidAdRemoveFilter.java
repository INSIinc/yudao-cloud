package cn.iocoder.yudao.framework.datasource.core.filter;

import com.alibaba.druid.util.Utils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Druid 底部广告移除过滤器
 *
 * 作用：拦截访问 Druid 监控页面时加载的 common.js 文件，
 *      把其中包含的底部广告链接（例如 "powered by xxx"）删除，
 *      从而在页面上不显示广告。
 *
 * 注意：这个过滤器只对特定请求路径生效（通常配置在 Spring Boot 的 WebSecurity 或 FilterRegistrationBean 中），
 *      并不会影响其他正常业务请求。
 *
 * @author 芋道源码
 */
public class DruidAdRemoveFilter extends OncePerRequestFilter {

    /**
     * 指定要处理的 JavaScript 文件路径（这是 Druid 内部资源路径，不是项目中的文件）
     *
     * 说明：Druid 内置了一个 common.js 文件，里面包含了页面底部的广告代码。
     *       我们通过读取这个文件内容，修改后再返回给浏览器，达到去除广告的效果。
     */
    private static final String COMMON_JS_ILE_PATH = "support/http/resources/js/common.js";

    /**
     * 过滤器的核心处理方法
     *
     * 执行流程：
     * 1. 先让请求继续往后走（chain.doFilter），这样响应头等信息能正常设置；
     * 2. 然后清空响应体的缓冲区（但保留响应头），因为我们准备用自己的内容覆盖原响应；
     * 3. 读取 Druid 内置的 common.js 文件内容；
     * 4. 用正则表达式删除其中的广告 HTML 片段；
     * 5. 把“干净”的 JavaScript 内容写回给浏览器。
     *
     * ⚠️ 注意：该方法仅适用于请求路径正好匹配 common.js 的情况（通常通过 URL 映射配置）。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 先正常执行后续过滤器和 Servlet，确保响应头等信息已设置好
        chain.doFilter(request, response);

        // 清空响应体的缓冲区（但不重置响应头，比如 Content-Type 仍然保留）
        response.resetBuffer();

        // 从 Druid 的 JAR 包内部读取 common.js 文件的原始内容
        String text = Utils.readFromResource(COMMON_JS_ILE_PATH);

        // 使用正则表达式删除广告相关的 HTML 代码（第一处广告）
        // 例如：<a class="banner"></a><br/>
        text = text.replaceAll("<a.*?banner\"></a><br/>", "");

        // 删除第二处广告（通常是 "powered by shrek.wang" 这样的链接）
        text = text.replaceAll("powered.*?shrek.wang</a>", "");

        // 将修改后的内容写入 HTTP 响应，返回给浏览器
        response.getWriter().write(text);
    }

}