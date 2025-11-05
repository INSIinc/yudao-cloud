# yudao-spring-boot-starter-security

## 📖 模块简介

`yudao-spring-boot-starter-security` 是芋道项目的安全认证组件，基于 Spring Security 框架封装，提供统一的身份认证、权限校验和操作日志功能。

### 核心功能

1. **安全认证（Security）**：用户的身份认证、权限校验，实现「谁」可以做「什么事」
2. **操作日志（OperateLog）**：业务操作日志记录，实现「谁」在「什么时间」对「什么」做了「什么事」

## ✨ 主要特性

### 🔐 安全认证功能

- **Token 认证**：支持基于 OAuth2 Token 的无状态认证
- **多渠道 Token 传递**：支持 Header、Parameter 两种方式传递 Token
- **权限校验**：基于注解的权限、角色校验
- **多租户隔离**：内置租户隔离机制
- **Mock 模式**：支持开发调试的 Mock 认证
- **跨服务传递**：支持微服务间用户信息透传
- **异常处理**：统一处理认证失败（401）和权限不足（403）

### 📝 操作日志功能

- **声明式日志**：基于 `@LogRecord` 注解自动记录操作日志
- **SpEL 表达式**：支持 Spring 表达式语言，灵活定义日志内容
- **自动解析**：自动记录操作人、操作时间、操作结果等信息
- **业务扩展**：支持自定义函数和变量解析

## 🏗️ 架构设计

### 模块结构

```
yudao-spring-boot-starter-security/
├── src/main/java/
│   └── cn/iocoder/yudao/framework/
│       ├── security/                          # 安全认证模块
│       │   ├── config/                        # 配置类
│       │   │   ├── SecurityProperties.java    # 安全配置属性
│       │   │   ├── YudaoSecurityAutoConfiguration.java  # 自动配置
│       │   │   ├── YudaoSecurityRpcAutoConfiguration.java  # RPC配置
│       │   │   ├── YudaoWebSecurityConfigurerAdapter.java  # Security核心配置
│       │   │   └── AuthorizeRequestsCustomizer.java  # 请求授权定制器
│       │   ├── core/                          # 核心功能
│       │   │   ├── LoginUser.java             # 登录用户信息
│       │   │   ├── filter/                    # 过滤器
│       │   │   │   └── TokenAuthenticationFilter.java  # Token认证过滤器
│       │   │   ├── handler/                   # 异常处理器
│       │   │   │   ├── AccessDeniedHandlerImpl.java    # 权限不足处理
│       │   │   │   └── AuthenticationEntryPointImpl.java  # 认证失败处理
│       │   │   ├── service/                   # 服务接口
│       │   │   │   ├── SecurityFrameworkService.java   # 权限服务接口
│       │   │   │   └── SecurityFrameworkServiceImpl.java  # 权限服务实现
│       │   │   ├── util/                      # 工具类
│       │   │   │   └── SecurityFrameworkUtils.java  # 安全工具类
│       │   │   ├── context/                   # 上下文
│       │   │   │   └── TransmittableThreadLocalSecurityContextHolderStrategy.java
│       │   │   └── rpc/                       # RPC支持
│       │   │       └── LoginUserRequestInterceptor.java  # 用户信息拦截器
│       └── operatelog/                        # 操作日志模块
│           ├── config/                        # 配置类
│           │   ├── YudaoOperateLogConfiguration.java  # 操作日志配置
│           │   └── YudaoOperateLogRpcAutoConfiguration.java  # RPC配置
│           └── core/                          # 核心功能
│               └── service/
│                   └── LogRecordServiceImpl.java  # 日志记录服务实现
└── src/main/resources/
    └── META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 核心组件

#### 1. TokenAuthenticationFilter（Token 认证过滤器）

拦截所有 HTTP 请求，验证 Token 的有效性：

- 从 Header 或 Parameter 中提取 Token
- 调用 OAuth2 服务验证 Token
- 将登录用户信息设置到 Spring Security 上下文
- 支持 Gateway 网关的用户信息透传

#### 2. SecurityFrameworkService（权限服务）

提供权限和角色校验的核心服务：

- `hasPermission(String permission)`：判断是否有指定权限
- `hasAnyPermissions(String... permissions)`：判断是否有任一权限
- `hasRole(String role)`：判断是否有指定角色
- `hasAnyRoles(String... roles)`：判断是否有任一角色
- `hasScope(String scope)`：判断是否有指定授权范围

#### 3. LoginUser（登录用户）

封装当前登录用户的信息：

```java
- id: 用户编号
- userType: 用户类型（管理员、会员等）
- tenantId: 租户编号
- scopes: 授权范围
- info: 额外的用户信息（昵称、部门等）
- context: 上下文信息（临时缓存）
```

## 📝 使用指南

### 1. 添加依赖

在业务模块的 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-security</artifactId>
</dependency>
```

### 2. 配置文件

在 `application.yml` 中添加安全配置：

```yaml
yudao:
  security:
    # Token 请求头名称
    token-header: Authorization
    # Token 请求参数名称（用于 WebSocket 等场景）
    token-parameter: token
    # Mock 模式开关（仅用于开发测试）
    mock-enable: false
    # Mock 模式密钥
    mock-secret: test
    # 免登录的 URL 列表
    permit-all-urls:
      - /app-api/*/auth/login
      - /app-api/*/auth/logout
      - /admin-api/*/auth/login
      - /admin-api/*/auth/logout
    # 密码加密强度（4-31，越高越安全但越慢）
    password-encoder-length: 4
```

### 3. 获取当前登录用户

```java
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;

// 获取当前登录用户 ID
Long userId = SecurityFrameworkUtils.getLoginUserId();

// 获取当前登录用户信息
LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();

// 获取用户昵称
String nickname = loginUser.getInfo().get(LoginUser.INFO_KEY_NICKNAME);

// 获取用户部门 ID
String deptId = loginUser.getInfo().get(LoginUser.INFO_KEY_DEPT_ID);
```

### 4. 权限校验

#### 方式一：使用注解（推荐）

```java
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/system/user")
public class UserController {

    // 需要有 system:user:query 权限
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    @GetMapping("/get")
    public CommonResult<UserVO> getUser(@RequestParam Long id) {
        // ...
    }

    // 需要有 system:user:create 权限
    @PreAuthorize("@ss.hasPermission('system:user:create')")
    @PostMapping("/create")
    public CommonResult<Long> createUser(@RequestBody UserCreateReqVO reqVO) {
        // ...
    }

    // 需要有管理员角色
    @PreAuthorize("@ss.hasRole('admin')")
    @DeleteMapping("/delete")
    public CommonResult<Boolean> deleteUser(@RequestParam Long id) {
        // ...
    }

    // 需要有任一权限
    @PreAuthorize("@ss.hasAnyPermissions('system:user:query', 'system:user:export')")
    @GetMapping("/export")
    public void exportUser() {
        // ...
    }
}
```

#### 方式二：编程式校验

```java
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private SecurityFrameworkService securityFrameworkService;

    public void deleteUser(Long id) {
        // 检查是否有删除权限
        if (!securityFrameworkService.hasPermission("system:user:delete")) {
            throw new ServiceException(FORBIDDEN);
        }
        // 执行删除操作
        // ...
    }
}
```

### 5. 操作日志记录

#### 基本使用

```java
import com.mzt.logapi.starter.annotation.LogRecord;

@Service
public class OrderServiceImpl implements OrderService {

    /**
     * 创建订单
     * - success: 操作成功时记录的日志内容
     * - type: 日志类型/业务模块
     * - bizNo: 业务编号（用于关联）
     * - subType: 子类型
     */
    @LogRecord(
        success = "创建订单，订单号：{{#order.orderNo}}，金额：{{#order.amount}}",
        type = "订单", 
        bizNo = "{{#order.id}}",
        subType = "创建订单"
    )
    public Order createOrder(OrderCreateReqVO reqVO) {
        Order order = // ... 创建订单
        return order;
    }

    /**
     * 更新订单状态
     * - fail: 操作失败时记录的日志内容
     */
    @LogRecord(
        success = "订单 {{#orderId}} 状态从 {{#oldStatus}} 变更为 {{#newStatus}}",
        fail = "订单 {{#orderId}} 状态变更失败",
        type = "订单", 
        bizNo = "{{#orderId}}",
        subType = "状态变更"
    )
    public void updateOrderStatus(Long orderId, Integer newStatus) {
        // 获取旧状态（可以通过 LogRecordContext 传递给日志模板）
        Order order = getOrder(orderId);
        LogRecordContext.putVariable("oldStatus", order.getStatus());
        LogRecordContext.putVariable("newStatus", newStatus);
        
        // 更新状态
        // ...
    }
}
```

#### 高级用法

```java
import com.mzt.logapi.context.LogRecordContext;

@Service
public class UserServiceImpl implements UserService {

    /**
     * 使用自定义函数解析用户名
     * 注：需要实现自定义的 IParseFunction
     */
    @LogRecord(
        success = "更新用户 {USERNAME{#userId}} 的信息",
        type = "用户管理", 
        bizNo = "{{#userId}}",
        subType = "更新用户"
    )
    public void updateUser(Long userId, UserUpdateReqVO reqVO) {
        // 业务逻辑
        // ...
    }

    /**
     * 条件记录日志
     * - condition: SpEL 表达式，返回 true 才记录日志
     */
    @LogRecord(
        success = "删除用户 {{#userId}}",
        type = "用户管理", 
        bizNo = "{{#userId}}",
        subType = "删除用户",
        condition = "{{#isAdmin}}"  // 只有管理员操作才记录
    )
    public void deleteUser(Long userId, boolean isAdmin) {
        // 业务逻辑
        // ...
    }
}
```

## 🔧 核心配置详解

### SecurityProperties（安全配置属性）

| 属性 | 类型 | 默认值 | 说明 |
|-----|------|-------|------|
| `token-header` | String | Authorization | HTTP 请求头中 Token 的字段名 |
| `token-parameter` | String | token | URL 参数中 Token 的字段名 |
| `mock-enable` | Boolean | false | 是否启用 Mock 模式（开发测试用） |
| `mock-secret` | String | test | Mock 模式的密钥 |
| `permit-all-urls` | List<String> | [] | 无需认证的 URL 列表 |
| `password-encoder-length` | Integer | 4 | BCrypt 密码加密强度（4-31） |

### 自定义免登录 URL

如果需要在代码中动态配置免登录 URL，可以实现 `AuthorizeRequestsCustomizer` 接口：

```java
import cn.iocoder.yudao.framework.security.config.AuthorizeRequestsCustomizer;

@Component
public class MyAuthorizeRequestsCustomizer implements AuthorizeRequestsCustomizer {

    @Override
    public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
        // 添加自定义的免登录 URL
        registry.requestMatchers("/my-api/public/**").permitAll();
    }
}
```

## 🔍 技术实现

### 1. 密码加密

使用 BCrypt 算法加密密码：

```java
import org.springframework.security.crypto.password.PasswordEncoder;

@Resource
private PasswordEncoder passwordEncoder;

// 加密密码
String encodedPassword = passwordEncoder.encode("123456");

// 验证密码
boolean matches = passwordEncoder.matches("123456", encodedPassword);
```

### 2. 跨线程用户信息传递

使用 `TransmittableThreadLocal` 实现跨线程的用户信息传递：

```java
// 在父线程中设置用户信息
SecurityFrameworkUtils.setLoginUser(loginUser, request);

// 子线程自动继承用户信息
executor.execute(() -> {
    Long userId = SecurityFrameworkUtils.getLoginUserId();
    // 可以正常获取到父线程的用户信息
});
```

### 3. 微服务间用户信息透传

通过 Feign 拦截器自动传递用户信息：

```java
// LoginUserRequestInterceptor 会自动将当前用户信息放入请求头
// 目标服务的 TokenAuthenticationFilter 会自动解析并设置用户信息
```

## 📚 依赖说明

### 核心依赖

- **Spring Boot Starter Security**：Spring Security 核心框架
- **Spring Boot Starter AOP**：用于权限注解的切面支持
- **bizlog-sdk**：操作日志框架（mzt-log-api）
- **yudao-common**：芋道通用模块
- **yudao-spring-boot-starter-web**：Web 支持
- **yudao-spring-boot-starter-rpc**：RPC 支持（可选）

## ⚠️ 注意事项

1. **Mock 模式安全性**：Mock 模式仅用于开发测试，生产环境必须设置 `mock-enable: false`
2. **Token 传递优先级**：Header > Parameter，优先从 Header 中获取 Token
3. **密码加密强度**：`password-encoder-length` 越大越安全，但加密时间越长，建议生产环境使用 10 以上
4. **免登录 URL**：配置 `permit-all-urls` 时，URL 支持 Ant 风格匹配（如 `/api/**`）
5. **操作日志性能**：大量操作日志会影响性能，建议异步处理或批量入库
6. **跨域问题**：如果前端跨域访问，需要配置 CORS 允许 `Authorization` 请求头

## 🤝 集成示例

完整的集成示例可参考项目中的各个业务模块：

- **yudao-module-system**：系统管理模块，展示了完整的权限控制
- **yudao-module-bpm**：流程管理模块，展示了操作日志的使用
- **yudao-gateway**：网关模块，展示了用户信息的透传

## 📖 扩展阅读

- [Spring Security 官方文档](https://docs.spring.io/spring-security/reference/index.html)
- [《芋道 Spring Boot 安全框架 Spring Security 入门》](./《芋道%20Spring%20Boot%20安全框架%20Spring%20Security%20入门》.md)
- [bizlog-sdk 操作日志框架](https://github.com/mouzt/mzt-biz-log)

## 📄 许可证

本项目采用 MIT 许可证，详见 [LICENSE](../../LICENSE) 文件。

## 👥 贡献者

感谢所有为本项目做出贡献的开发者！

---

**Project:** [ruoyi-vue-pro](https://github.com/YunaiV/ruoyi-vue-pro)  
**Author:** 芋道源码  
**Last Updated:** 2025-11-05

