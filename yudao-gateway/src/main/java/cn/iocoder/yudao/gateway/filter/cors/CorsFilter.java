package cn.iocoder.yudao.gateway.filter.cors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 跨域 Filter
 *
 * CORS（跨域资源共享）过滤器
 *
 * 作用说明：
 * 1. 什么是跨域？
 *    当浏览器的前端页面（例如：http://localhost:8080）试图访问不同域名/端口的后端接口（例如：http://localhost:48080）时，
 *    浏览器会因为安全策略阻止这种请求，这就是"跨域问题"。
 *
 * 2. 为什么需要这个过滤器？
 *    这个过滤器通过在响应头中添加特定的CORS相关头信息，告诉浏览器允许跨域访问，从而解决跨域问题。
 *
 * 3. 工作原理：
 *    - 拦截所有经过网关的请求
 *    - 判断是否为跨域请求
 *    - 如果是跨域请求，添加允许跨域的响应头
 *    - 特别处理OPTIONS预检请求（浏览器在发送真正请求前的探测请求）
 *
 * @author 芋道源码
 */
@Component  // 标记为Spring组件，会被自动扫描并注册到容器中
public class CorsFilter implements WebFilter {  // 实现WebFilter接口，这是Spring WebFlux提供的过滤器接口

    // "*" 表示允许所有来源、所有方法、所有请求头
    private static final String ALL = "*";

    // 预检请求的结果可以被缓存的时间（秒），3600秒 = 1小时
    // 在这个时间内，浏览器不需要再发送预检请求
    private static final String MAX_AGE = "3600L";

    /**
     * 过滤器的核心方法
     *
     * @param exchange 服务器Web交换对象，包含请求和响应信息
     * @param chain 过滤器链，用于将请求传递给下一个过滤器或最终的处理器
     * @return Mono<Void> 响应式编程的返回类型，表示异步操作
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // ============ 步骤1：判断是否为跨域请求 ============
        // 获取请求对象
        ServerHttpRequest request = exchange.getRequest();

        // 使用Spring提供的工具类判断是否为跨域请求
        // 非跨域请求，直接放行，不需要添加跨域响应头
        if (!CorsUtils.isCorsRequest(request)) {
            return chain.filter(exchange);  // 继续执行后续的过滤器链
        }

        // ============ 步骤2：设置跨域响应头 ============
        // 如果是跨域请求，需要添加CORS相关的响应头
        ServerHttpResponse response = exchange.getResponse();
        HttpHeaders headers = response.getHeaders();

        // Access-Control-Allow-Origin: 允许哪些域名访问，"*" 表示允许所有域名
        // 生产环境建议配置具体的域名，例如：http://www.example.com
        headers.add("Access-Control-Allow-Origin", ALL);

        // Access-Control-Allow-Methods: 允许哪些HTTP方法，"*" 表示允许所有方法（GET、POST、PUT、DELETE等）
        headers.add("Access-Control-Allow-Methods", ALL);

        // Access-Control-Allow-Headers: 允许哪些请求头，"*" 表示允许所有请求头
        // 例如：Content-Type、Authorization、X-Requested-With等
        headers.add("Access-Control-Allow-Headers", ALL);

        // Access-Control-Max-Age: 预检请求的结果可以被缓存多久（秒）
        // 设置后，在这个时间内浏览器不会再发送OPTIONS预检请求，可以提高性能
        headers.add("Access-Control-Max-Age", MAX_AGE);

        // ============ 步骤3：处理OPTIONS预检请求 ============
        // 什么是OPTIONS预检请求？
        // 当浏览器发送跨域请求时，会先发送一个OPTIONS方法的"预检请求"，询问服务器是否允许跨域
        // 如果预检通过，浏览器才会发送真正的请求（GET、POST等）
        if (request.getMethod() == HttpMethod.OPTIONS) {
            // 直接返回200 OK状态码，告诉浏览器允许跨域
            response.setStatusCode(HttpStatus.OK);
            return Mono.empty();  // 返回空响应，不再继续执行后续逻辑
        }

        // ============ 步骤4：继续处理正常请求 ============
        // 如果不是OPTIONS预检请求，继续执行后续的过滤器链和业务逻辑
        return chain.filter(exchange);
    }

}
