package cn.iocoder.yudao.framework.web.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Web 配置属性类
 *
 * 【作用说明】
 * 这个类用于从配置文件（如 application.yml）中读取 Web 相关的配置信息。
 * 通过 @ConfigurationProperties 注解，Spring 会自动将配置文件中 "yudao.web" 开头的配置项映射到这个类的属性中。
 *
 * 【核心功能】
 * 1. 管理 APP API 的统一访问前缀（如 /app-api）
 * 2. 管理 Admin API 的统一访问前缀（如 /admin-api）
 * 3. 管理后台管理界面的访问地址
 *
 * 【配置示例】
 * 在 application.yml 中的配置格式：
 * yudao:
 *   web:
 *     app-api:
 *       prefix: /app-api
 *       controller: **.controller.app.**
 *     admin-api:
 *       prefix: /admin-api
 *       controller: **.controller.admin.**
 *     admin-ui:
 *       url: http://localhost:8080
 *
 * @author 芋道源码
 */
@ConfigurationProperties(prefix = "yudao.web")  // 指定配置文件中的前缀为 "yudao.web"
@Validated  // 开启属性校验，确保配置的值符合要求
@Data  // Lombok 注解：自动生成 getter、setter、toString 等方法
public class WebProperties {

    /**
     * APP API 配置
     *
     * 【说明】用于配置移动端（APP）或前台用户访问的 API 接口
     * 【默认值】前缀为 "/app-api"，Controller 包路径为 "**.controller.app.**"
     * 【举例】用户注册、登录、商品浏览等面向普通用户的接口
     */
    @NotNull(message = "APP API 不能为空")
    private Api appApi = new Api("/app-api", "**.controller.app.**");

    /**
     * Admin API 配置
     *
     * 【说明】用于配置后台管理系统的 API 接口
     * 【默认值】前缀为 "/admin-api"，Controller 包路径为 "**.controller.admin.**"
     * 【举例】用户管理、权限管理、系统配置等面向管理员的接口
     */
    @NotNull(message = "Admin API 不能为空")
    private Api adminApi = new Api("/admin-api", "**.controller.admin.**");

    /**
     * Admin UI 配置
     *
     * 【说明】用于配置后台管理界面的访问地址
     * 【用途】前端项目的访问地址，用于跨域配置、重定向等场景
     */
    @NotNull(message = "Admin UI 不能为空")
    private Ui adminUi;

    /**
     * API 配置内部类
     *
     * 【作用】定义 API 接口的统一前缀和对应的 Controller 包路径
     * 【好处】
     * 1. 统一管理：所有 API 接口都有统一的访问路径前缀
     * 2. 安全隔离：通过前缀区分不同的 API，便于在 Nginx 等网关层面做访问控制
     * 3. 灵活配置：可以通过配置文件轻松修改 API 前缀，无需改动代码
     */
    @Data  // 自动生成 getter、setter 方法
    @AllArgsConstructor  // 自动生成全参数构造方法
    @NoArgsConstructor  // 自动生成无参构造方法
    @Valid  // 开启嵌套对象的属性校验
    public static class Api {

        /**
         * API 前缀
         *
         * 【作用】为所有 Controller 提供的 RESTful API 添加统一的前缀
         *
         * 【实际效果】
         * 假设你写了一个接口：@GetMapping("/user/info")
         * 如果 prefix 配置为 "/app-api"，那么实际访问路径会变成：/app-api/user/info
         *
         * 【安全意义】
         * 通过统一前缀，可以在 Nginx 网关配置中：
         * - 只允许外部访问 /app-api/* 和 /admin-api/* 的接口
         * - 禁止外部直接访问 Swagger 文档（/swagger-ui.html）和监控端点（/actuator）
         * - 这样可以避免内部接口被意外暴露，提高系统安全性
         *
         * 【配置示例】
         * - APP 接口前缀：/app-api（面向普通用户）
         * - Admin 接口前缀：/admin-api（面向管理员）
         *
         * @see YudaoWebAutoConfiguration#configurePathMatch(PathMatchConfigurer)
         */
        @NotEmpty(message = "API 前缀不能为空")
        private String prefix;

        /**
         * Controller 所在包的 Ant 路径规则
         *
         * 【作用】指定哪些 Controller 类需要添加上述的 {@link #prefix} 前缀
         *
         * 【Ant 路径规则说明】
         * - ** 表示匹配任意层级的包
         * - * 表示匹配单个包名或类名
         *
         * 【配置示例】
         * - "**.controller.app.**" 匹配所有包含 "controller.app" 的 Controller
         *   例如：com.example.controller.app.UserController
         *        com.example.module.controller.app.ProductController
         *
         * - "**.controller.admin.**" 匹配所有包含 "controller.admin" 的 Controller
         *   例如：com.example.controller.admin.SystemController
         *
         * 【工作原理】
         * Spring 会扫描所有匹配该规则的 Controller 类，并自动为它们的接口路径添加 prefix 前缀
         *
         * 【好处】
         * 开发者只需要按约定的包结构放置 Controller，框架会自动处理路径前缀，无需手动添加
         */
        @NotEmpty(message = "Controller 所在包不能为空")
        private String controller;

    }

    /**
     * UI 配置内部类
     *
     * 【作用】配置前端界面的访问地址
     * 【使用场景】
     * 1. 跨域配置：允许来自该地址的前端请求访问后端 API
     * 2. 重定向：某些场景下需要跳转到前端页面
     * 3. 第三方回调：如支付、OAuth 登录回调时，需要知道前端地址
     */
    @Data  // 自动生成 getter、setter 方法
    @Valid  // 开启属性校验
    public static class Ui {

        /**
         * 前端界面访问地址
         *
         * 【说明】后台管理系统的前端页面地址（包含协议、域名、端口）
         *
         * 【配置示例】
         * - 本地开发环境：http://localhost:8080
         * - 测试环境：http://admin-test.example.com
         * - 生产环境：https://admin.example.com
         *
         * 【用途】
         * 1. 跨域白名单：配置 CORS 时，允许该地址的前端页面访问后端接口
         * 2. 登录重定向：用户登录成功后跳转到该地址
         * 3. 邮件链接：发送邮件时，生成指向该地址的链接
         */
        private String url;

    }

}
