package cn.iocoder.yudao.framework.swagger.config;

import com.github.xiaoymin.knife4j.spring.configuration.Knife4jAutoConfiguration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiBuilderCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.customizers.ServerBaseUrlCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.providers.JavadocProvider;
import org.springdoc.core.service.OpenAPIService;
import org.springdoc.core.service.SecurityService;
import org.springdoc.core.utils.PropertyResolverUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils.HEADER_TENANT_ID;

/**
 * Swagger 自动配置类，基于 OpenAPI + Springdoc 实现。
 *
 * 【什么是 Swagger？】
 * Swagger 是一个用于生成、描述、调用和可视化 RESTful 风格的 Web 服务的工具。
 * 它可以自动生成接口文档，并提供在线测试功能，方便前后端开发人员对接。
 *
 * 【什么是 OpenAPI？】
 * OpenAPI 是 Swagger 规范的新名称（2015年改名），两者本质上是同一个东西。
 * 它定义了一套标准的 API 描述格式。
 *
 * 友情提示：
 * 1. Springdoc 文档地址：<a href="https://github.com/springdoc/springdoc-openapi">仓库</a>
 * 2. Swagger 规范，于 2015 更名为 OpenAPI 规范，本质是一个东西
 *
 * @author 芋道源码
 */
// 【注解说明】@AutoConfiguration - 标识这是一个自动配置类，Spring Boot 启动时会自动加载
// before 参数：确保在 Knife4jAutoConfiguration 之前执行，保证配置的优先级
@AutoConfiguration(before = Knife4jAutoConfiguration.class) // before 原因，保证覆写的 Knife4jOpenApiCustomizer 先生效！相关 https://github.com/YunaiV/ruoyi-vue-pro/issues/954 讨论
// 【注解说明】@ConditionalOnClass - 只有当 classpath 中存在 OpenAPI.class 时，这个配置类才会生效
// 这样可以避免在没有引入相关依赖时报错
@ConditionalOnClass({OpenAPI.class})
// 【注解说明】@EnableConfigurationProperties - 启用 SwaggerProperties 配置属性类
// 该类会读取 application.yml 中的 swagger 相关配置
@EnableConfigurationProperties(SwaggerProperties.class)
// 【注解说明】@ConditionalOnProperty - 根据配置文件中的属性值决定是否启用
// 当 springdoc.api-docs.enabled=false 时，整个 Swagger 功能将被禁用
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true", matchIfMissing = true) // 设置为 false 时，禁用
// 【注解说明】@Import - 导入其他配置类，这里导入 Knife4j 的自定义配置
@Import(Knife4jOpenApiCustomizer.class)
public class YudaoSwaggerAutoConfiguration {

    // ========== 全局 OpenAPI 配置 ==========

    /**
     * 创建 OpenAPI 对象（Swagger 文档的核心配置）
     *
     * 【方法作用】
     * 这个方法创建并配置了 Swagger 文档的全局信息，包括：
     * 1. API 基本信息（标题、描述、版本等）
     * 2. 安全认证配置（如何传递 token）
     *
     * @param properties Swagger 配置属性，从配置文件中读取
     * @return OpenAPI 对象，包含完整的 API 文档配置
     */
    @Bean // 将返回的对象注册为 Spring Bean，由 Spring 容器管理
    public OpenAPI createApi(SwaggerProperties properties) {
        // 第一步：构建安全认证方案（如何验证用户身份）
        Map<String, SecurityScheme> securitySchemas = buildSecuritySchemes();

        // 第二步：创建 OpenAPI 对象并进行配置
        OpenAPI openAPI = new OpenAPI()
                // 设置 API 基本信息（标题、描述、作者等）
                .info(buildInfo(properties))
                // 设置安全认证组件（告诉 Swagger 如何处理认证）
                .components(new Components().securitySchemes(securitySchemas))
                // 添加全局安全要求：所有接口都需要在 Authorization 请求头中传递 token
                .addSecurityItem(new SecurityRequirement().addList(HttpHeaders.AUTHORIZATION));

        // 第三步：为每个安全方案添加安全要求（遍历所有配置的认证方式）
        securitySchemas.keySet().forEach(key -> openAPI.addSecurityItem(new SecurityRequirement().addList(key)));

        return openAPI;
    }

    /**
     * 构建 API 基本信息
     *
     * 【方法作用】
     * 设置 Swagger 文档页面显示的基本信息，如标题、描述、作者联系方式等。
     * 这些信息会在 Swagger UI 页面的顶部显示。
     *
     * @param properties 配置属性对象，包含从配置文件读取的值
     * @return Info 对象，包含 API 的基本描述信息
     */
    private Info buildInfo(SwaggerProperties properties) {
        return new Info()
                .title(properties.getTitle())           // API 文档标题（如："芋道管理系统"）
                .description(properties.getDescription()) // API 文档描述（如："提供管理后台的所有功能"）
                .version(properties.getVersion())        // API 版本号（如："1.0.0"）
                // 作者联系方式（姓名、网址、邮箱）
                .contact(new Contact().name(properties.getAuthor()).url(properties.getUrl()).email(properties.getEmail()))
                // 许可证信息（开源协议名称和链接）
                .license(new License().name(properties.getLicense()).url(properties.getLicenseUrl()));
    }

    /**
     * 构建安全认证方案配置
     *
     * 【方法作用】
     * 配置 API 的安全认证方式，告诉 Swagger：
     * 1. 使用什么方式进行认证（这里是 APIKEY 方式）
     * 2. 认证信息放在哪里（这里是 HTTP 请求头）
     * 3. 请求头的名称是什么（这里是 Authorization）
     *
     * 【为什么需要这个配置？】
     * 大部分接口都需要登录后才能访问，需要在请求时携带用户的 token。
     * 这个配置让 Swagger 知道如何在测试接口时自动添加 token。
     *
     * @return 安全方案的 Map，key 是方案名称，value 是具体配置
     */
    private Map<String, SecurityScheme> buildSecuritySchemes() {
        // 创建一个 Map 用于存储安全方案
        Map<String, SecurityScheme> securitySchemes = new HashMap<>();

        // 创建一个安全方案对象
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)      // 认证类型：API Key（通过特定的 key 进行认证）
                .name(HttpHeaders.AUTHORIZATION)        // 参数名称：Authorization（标准的 HTTP 认证请求头）
                .in(SecurityScheme.In.HEADER);          // 参数位置：放在 HTTP 请求��中

        // 将方案添加到 Map 中，key 为 "Authorization"
        securitySchemes.put(HttpHeaders.AUTHORIZATION, securityScheme);

        return securitySchemes;
    }

    /**
     * 创建自定义的 OpenAPI 服务处理器
     *
     * 【方法作用】
     * 创建一个 OpenAPIService Bean，负责处理 OpenAPI 相关的所有操作。
     *
     * 【为什么要自定义？】
     * 标注 @Primary 注解，确保使用我们自己创建的 Bean，而不是 Spring Boot 自动配置的。
     * 这样可以避免在一键改包（修改包名）后，因为 Bean 冲突导致启动报错。
     *
     * @param openAPI OpenAPI 配置对象（可选）
     * @param securityParser 安全解析器，用于解析安全相关配置
     * @param springDocConfigProperties Springdoc 配置属性
     * @param propertyResolverUtils 属性解析工具
     * @param openApiBuilderCustomizers OpenAPI 构建自定义器列表（可选）
     * @param serverBaseUrlCustomizers 服务器基础 URL 自定义器列表（可选）
     * @param javadocProvider Javadoc 提供者（可选），用于读取 Java 注释生成文档
     * @return OpenAPIService 实例
     */
    @Bean
    @Primary // 【重要】标记为主要的 Bean，当有多个同类型 Bean 时，优先使用这个
    public OpenAPIService openApiBuilder(Optional<OpenAPI> openAPI,
                                         SecurityService securityParser,
                                         SpringDocConfigProperties springDocConfigProperties,
                                         PropertyResolverUtils propertyResolverUtils,
                                         Optional<List<OpenApiBuilderCustomizer>> openApiBuilderCustomizers,
                                         Optional<List<ServerBaseUrlCustomizer>> serverBaseUrlCustomizers,
                                         Optional<JavadocProvider> javadocProvider) {
        // 创建并返回 OpenAPIService 实例，传入所有必要的依赖
        return new OpenAPIService(openAPI, securityParser, springDocConfigProperties,
                propertyResolverUtils, openApiBuilderCustomizers, serverBaseUrlCustomizers, javadocProvider);
    }

    // ========== 分组 OpenAPI 配置 ==========

    /**
     * 创建"所有模块"的 API 分组
     *
     * 【什么是 API 分组？】
     * 在 Swagger UI 中，可以将不同的 API 按模块分组显示。
     * 例如：用户模块、订单模块、商品模块等。
     * 这样可以让文档更清晰，方便开发人员快速找到需要的接口。
     *
     * 【这个方法的作用】
     * 创建一个名为 "all" 的分组，包含系统中所有的 API 接口。
     *
     * @return GroupedOpenApi 对象，代表一个 API 分组
     */
    @Bean
    public GroupedOpenApi allGroupedOpenApi() {
        // 调用构建方法，分组名为 "all"，路径为空（表示匹配所有路径）
        return buildGroupedOpenApi("all", "");
    }

    /**
     * 构建 API 分组（简化版本，group 和 path 相同）
     *
     * 【方法作用】
     * 当分组名和路径名相同时，可以使用这个简化方法。
     * 例如：buildGroupedOpenApi("user") 会创建一个用户模块的分组，匹配 /admin-api/user/** 和 /app-api/user/** 路径。
     *
     * @param group 分组名称，也作为 URL 路径的一部分
     * @return GroupedOpenApi 对象
     */
    public static GroupedOpenApi buildGroupedOpenApi(String group) {
        return buildGroupedOpenApi(group, group);
    }

    /**
     * 构建 API 分组（完整版本）
     *
     * 【方法作用】
     * 创建一个 API 分组，并配置以下内容：
     * 1. 分组名称（在 Swagger UI 的下拉框中显示）
     * 2. 匹配的路径规则（哪些 URL 的接口属于这个分组）
     * 3. 自动添加公共参数（租户ID、认证Token）
     * 4. 自定义接口ID的生成规则
     *
     * 【路径匹配说明】
     * - /admin-api/：后台管理接口的前缀
     * - /app-api/：移动端/前台接口的前缀
     * - /{path}/**：具体模块的路径，** 表示匹配该路径下的所有子路径
     *
     * 【举例】
     * 如果 path="user"，则会匹配：
     * - /admin-api/user/list
     * - /admin-api/user/get
     * - /app-api/user/profile
     * 等等
     *
     * @param group 分组名称（如 "user"、"order"）
     * @param path URL 路径（如 "user"、"order"），如果为空则匹配所有
     * @return GroupedOpenApi 对象
     */
    public static GroupedOpenApi buildGroupedOpenApi(String group, String path) {
        return GroupedOpenApi.builder()
                // 设置分组名称
                .group(group)
                // 设置路径匹配规则：匹配管理后台和移动端的指定模块路径
                .pathsToMatch("/admin-api/" + path + "/**", "/app-api/" + path + "/**")
                // 添加操作自定义器：为每个接口自动添加租户ID和认证Token参数
                // Lambda 表达式：(operation, handlerMethod) -> { ... }
                // operation: 当前接口的操作对象，可以修改接口的参数、返回值等
                // handlerMethod: 对应的 Controller 方法信息
                .addOperationCustomizer((operation, handlerMethod) -> operation
                        .addParametersItem(buildTenantHeaderParameter())    // 添加租户ID请求头参数
                        .addParametersItem(buildSecurityHeaderParameter())) // 添加认证Token请求头参数
                // 添加另一个自定义器：自定义接口ID的生成规则
                .addOperationCustomizer(buildOperationIdCustomizer())
                // 构建并返回 GroupedOpenApi 对象
                .build();
    }

    /**
     * 构建租户ID请求头参数
     *
     * 【什么是多租户？】
     * 多租户是指一个系统可以服务于多个独立的客户（租户）。
     * 每个租户的数据是隔离的，通过租户ID来区分不同租户的数据。
     *
     * 【为什么需要这个参数？】
     * 在多租户系统中，几乎所有接口都需要知道当前操作是哪个租户发起的。
     * 这个配置会在 Swagger 测试页面自动添加租户ID输入框，方便测试。
     *
     * 【举例】
     * 租户A（租户ID=1）和租户B（租户ID=2）都使用同一个系统：
     * - 租户A查询用户列表时，只能看到自己租户的用户
     * - 租户B查询用户列表时，只能看到自己租户的用户
     * 系统通过请求头中的租户ID来区分
     *
     * @return Parameter 对象，表示一个 HTTP 请求参数
     */
    private static Parameter buildTenantHeaderParameter() {
        return new Parameter()
                .name(HEADER_TENANT_ID)                   // 参数名称（常量，通常是 "tenant-id"）
                .description("租户编号")                   // 参数描述，会在 Swagger UI 中显示
                .in(String.valueOf(SecurityScheme.In.HEADER)) // 参数位置：在 HTTP 请求头中
                // 参数的 Schema（数据模型）配置
                .schema(new IntegerSchema()
                        ._default(1L)                      // 默认值：1（测试时默认使用租户1）
                        .name(HEADER_TENANT_ID)            // Schema 名称
                        .description("租户编号"));          // Schema 描述
    }

    /**
     * 构建认证Token请求头参数
     *
     * 【什么是认证Token？】
     * Token 是用户登录后系统分配的一个令牌（类似于通行证）。
     * 用户访问需要权限的接口时，必须在请求头中携带这个 Token。
     * 服务器通过验证 Token 来确认用户身份和权限。
     *
     * 【为什么需要这个配置？】
     * 解决 Knife4j 的一个已知问题：有时 Authorize 按钮配置的 Token 不生效。
     * 通过这个配置，在 Swagger 测试页面直接显示 Authorization 输入框，更稳定可靠。
     *
     * 【Token 格式说明】
     * 标准格式：Bearer {token值}
     * - Bearer：认证类型（持有者认证）
     * - 空格
     * - 实际的 token 字符串
     *
     * 【举例】
     * Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     *
     * @return Parameter 对象，表示认证参数
     */
    private static Parameter buildSecurityHeaderParameter() {
        return new Parameter()
                .name(HttpHeaders.AUTHORIZATION)          // 参数名称："Authorization"（HTTP 标准认证请求头）
                .description("认证 Token")                 // 参数描述
                .in(String.valueOf(SecurityScheme.In.HEADER)) // 参数位置：在 HTTP 请求头中
                // 参数的 Schema（数据模型）配置
                .schema(new StringSchema()
                        ._default("Bearer test1")          // 默认值：用于测试的 Token（test1 是测试用户的 token）
                        .name(HEADER_TENANT_ID)            // Schema 名称
                        .description("认证 Token"));        // Schema 描述
    }

    /**
     * 构建自定义的 OperationId 生成器
     *
     * 【什么是 OperationId？】
     * OperationId 是每个 API 接口的唯一标识符。
     * 在生成前端 API 调用代码时，通常会使用 OperationId 作为函数名。
     *
     * 【为什么要自定义？】
     * 默认的 OperationId 生成规则可能不够清晰，容易重复。
     * 自定义规则：类名前缀 + 下划线 + 方法名，让 ID 更有意义。
     *
     * 【生成规则示例】
     * Controller 类：UserController
     * 方法名：list
     * 生成的 OperationId：User_list
     *
     * Controller 类：OrderController
     * 方法名：create
     * 生成的 OperationId：Order_create
     *
     * 【解决的问题】
     * 避免 admin-api 和 app-api 的前缀不生效问题。
     * 详见：https://github.com/YunaiV/ruoyi-vue-pro/issues/957
     *
     * @return OperationCustomizer 自定义器，用于修改每个接口的配置
     */
    private static OperationCustomizer buildOperationIdCustomizer() {
        // 返回一个 Lambda 表达式，定义如何自定义 Operation
        // operation: 当前接口的操作对象
        // handlerMethod: 对应的 Controller 方法信息
        return (operation, handlerMethod) -> {
            // 步骤1：获取控制器的完整类名（如：UserController）
            String className = handlerMethod.getBeanType().getSimpleName();

            // 步骤2：提取类名前缀（去除 "Controller" 后缀）
            // 使用正则表达式：Controller$ 表示匹配结尾的 "Controller"
            // 例如：UserController -> User
            String classPrefix = className.replaceAll("Controller$", "");

            // 步骤3：获取方法名
            // 例如：list, create, update, delete 等
            String methodName = handlerMethod.getMethod().getName();

            // 步骤4：组合生成 operationId（格式：类前缀_方法名）
            // 例如：User_list, Order_create
            String operationId = classPrefix + "_" + methodName;

            // 步骤5：将生成的 operationId 设置到 operation 对象中
            operation.setOperationId(operationId);

            // 步骤6：返回修改后的 operation 对象
            return operation;
        };
    }

}

