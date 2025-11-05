# yudao-spring-boot-starter-web

## 📖 模块介绍

`yudao-spring-boot-starter-web` 是芋道项目的 Web 框架核心模块，提供了 Web 应用开发所需的基础设施和最佳实践。

**模块坐标：**
```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-web</artifactId>
</dependency>
```

## ✨ 核心功能

### 1. 全局异常处理
- 统一异常处理机制
- 业务异常、系统异常分类处理
- 错误码标准化管理
- 友好的错误提示信息

### 2. API 接口日志
- 自动记录 API 访问日志
- 支持请求参数、响应结果记录
- 操作类型标注（查询、新增、修改、删除等）
- 性能监控（接口耗时统计）

### 3. 接口文档（Swagger/Knife4j）
- 基于 OpenAPI 3.0 规范
- 集成 Knife4j 增强 UI
- 支持在线调试
- 自动生成接口文档

### 4. 数据脱敏
支持多种脱敏策略：
- 手机号脱敏
- 邮箱脱敏
- 身份证号脱敏
- 银行卡号脱敏
- 中文姓名脱敏
- 固定电话脱敏
- 车牌号脱敏
- 自定义正则脱敏

### 5. XSS 防护
- 基于 Jsoup 的 XSS 清理
- 支持 JSON 反序列化时自动清理
- 可配置白名单路径
- Filter 级别的请求参数清理

### 6. 接口加解密
- 支持 API 请求参数加密
- 支持 API 响应结果加密
- 灵活的加解密配置

### 7. Jackson 配置增强
- 统一的日期时间处理
- Long 类型精度处理
- 枚举序列化增强
- 自定义序列化器

### 8. 其他功能
- CORS 跨域配置
- Demo 模式（防止误操作）
- 全局响应体包装
- Request Body 缓存
- Banner 启动信息

## 🚀 快速开始

### 1. 添加依赖

在项目的 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-web</artifactId>
</dependency>
```

### 2. 配置文件

在 `application.yml` 中添加配置：

```yaml
yudao:
  web:
    # 应用配置
    app:
      # Admin 管理后台 UI 的地址
      admin-ui:
        url: http://127.0.0.1:80
    # API 前缀（可选）
    api-prefix: /api
    
  # XSS 防护配置
  xss:
    enable: true
    exclude-urls:
      - /order/export/*
      
  # Swagger 文档配置
  swagger:
    title: 芋道管理系统
    description: 提供管理系统的所有功能
    version: ${project.version}
    author: 芋道源码
    url: https://www.iocoder.cn
    email: yunai@iocoder.cn
```

## 📝 功能详解

### 全局异常处理

使用 `GlobalExceptionHandler` 自动捕获并处理异常：

```java
@RestController
@RequestMapping("/user")
public class UserController {
    
    @GetMapping("/get")
    public CommonResult<User> getUser(@RequestParam Long id) {
        // 抛出业务异常，会被自动捕获并转换为标准响应
        if (id == null) {
            throw new ServiceException(ErrorCodeConstants.USER_NOT_EXISTS);
        }
        return success(userService.getUser(id));
    }
}
```

### API 访问日志

使用 `@ApiAccessLog` 注解记录接口访问日志：

```java
@RestController
@RequestMapping("/user")
public class UserController {
    
    @PostMapping("/create")
    @ApiAccessLog(operateType = OperateTypeEnum.CREATE)
    public CommonResult<Long> createUser(@RequestBody UserCreateReqVO createReqVO) {
        return success(userService.createUser(createReqVO));
    }
    
    @PutMapping("/update")
    @ApiAccessLog(operateType = OperateTypeEnum.UPDATE)
    public CommonResult<Boolean> updateUser(@RequestBody UserUpdateReqVO updateReqVO) {
        userService.updateUser(updateReqVO);
        return success(true);
    }
}
```

### 数据脱敏

在实体类字段上使用脱敏注解：

```java
@Data
public class UserRespVO {
    
    private Long id;
    
    @ChineseNameDesensitize  // 中文姓名脱敏：张**
    private String name;
    
    @MobileDesensitize  // 手机号脱敏：138****6789
    private String mobile;
    
    @EmailDesensitize  // 邮箱脱敏：y****@example.com
    private String email;
    
    @IdCardDesensitize  // 身份证脱敏：110***********1234
    private String idCard;
    
    @BankCardDesensitize  // 银行卡脱敏：6222 **** **** 1234
    private String bankCard;
}
```

自定义正则脱敏：

```java
@Data
public class UserRespVO {
    
    @RegexDesensitize(regex = "(\\d{3})\\d{4}(\\d{4})", replacement = "$1****$2")
    private String customField;
}
```

### XSS 防护

XSS 防护默认开启，自动清理用户输入的危险脚本：

```yaml
yudao:
  xss:
    enable: true
    # 排除的 URL（不进行 XSS 过滤）
    exclude-urls:
      - /order/export/*
      - /product/import
```

### Swagger 接口文档

访问接口文档地址：
- Swagger UI: `http://localhost:port/swagger-ui.html`
- Knife4j UI: `http://localhost:port/doc.html`（推荐）

在 Controller 中添加文档注解：

```java
@Tag(name = "用户管理")
@RestController
@RequestMapping("/user")
public class UserController {
    
    @Operation(summary = "获取用户详情")
    @Parameter(name = "id", description = "用户编号", required = true, example = "1")
    @GetMapping("/get")
    public CommonResult<UserRespVO> getUser(@RequestParam Long id) {
        return success(userService.getUser(id));
    }
    
    @Operation(summary = "创建用户")
    @PostMapping("/create")
    public CommonResult<Long> createUser(@RequestBody @Valid UserCreateReqVO createReqVO) {
        return success(userService.createUser(createReqVO));
    }
}
```

### 接口加解密

配置接口加解密：

```yaml
yudao:
  api-encrypt:
    enable: true
    # 配置需要加密的接口路径
    include-urls:
      - /api/sensitive/*
```

## 📚 配置参数

### Web 配置（yudao.web）

| 参数 | 说明 | 默认值 |
|-----|------|--------|
| `yudao.web.api-prefix` | API 接口前缀 | `/admin-api` |
| `yudao.web.app.admin-ui.url` | 管理后台 UI 地址 | - |

### XSS 配置（yudao.xss）

| 参数 | 说明 | 默认值 |
|-----|------|--------|
| `yudao.xss.enable` | 是否开启 XSS 防护 | `true` |
| `yudao.xss.exclude-urls` | 排除的 URL 列表 | `[]` |

### Swagger 配置（yudao.swagger）

| 参数 | 说明 | 默认值 |
|-----|------|--------|
| `yudao.swagger.title` | 文档标题 | - |
| `yudao.swagger.description` | 文档描述 | - |
| `yudao.swagger.version` | 文档版本 | - |
| `yudao.swagger.author` | 作者 | - |
| `yudao.swagger.url` | 联系地址 | - |
| `yudao.swagger.email` | 联系邮箱 | - |

### API 加密配置（yudao.api-encrypt）

| 参数 | 说明 | 默认值 |
|-----|------|--------|
| `yudao.api-encrypt.enable` | 是否开启接口加密 | `false` |
| `yudao.api-encrypt.include-urls` | 需要加密的 URL 列表 | `[]` |

## 🏗️ 模块结构

```
yudao-spring-boot-starter-web
├── apilog           # API 访问日志
├── banner           # 启动 Banner
├── desensitize      # 数据脱敏
├── encrypt          # 接口加解密
├── jackson          # Jackson 配置
├── swagger          # Swagger 接口文档
├── web              # Web 核心配置
└── xss              # XSS 防护
```

## 🔧 扩展开发

### 自定义脱敏处理器

实现 `DesensitizationHandler` 接口：

```java
@Component
public class CustomDesensitizationHandler implements DesensitizationHandler<CustomDesensitize> {
    
    @Override
    public String desensitize(String origin, CustomDesensitize annotation) {
        // 自定义脱敏逻辑
        return "***";
    }
}
```

### 自定义全局异常处理

继承 `GlobalExceptionHandler` 并重写方法：

```java
@ControllerAdvice
public class CustomGlobalExceptionHandler extends GlobalExceptionHandler {
    
    public CustomGlobalExceptionHandler(ApiErrorLogCommonApi apiErrorLogCommonApi) {
        super(apiErrorLogCommonApi);
    }
    
    // 自定义异常处理逻辑
}
```

## 📖 相关文档

- [《芋道 Spring Boot API 接口文档 Swagger 入门》](./《芋道%20Spring%20Boot%20API%20接口文档%20Swagger%20入门》.md)
- [《芋道 Spring Boot SpringMVC 入门》](./《芋道%20Spring%20Boot%20SpringMVC%20入门》.md)

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

本项目采用 MIT 许可证，详见 [LICENSE](../../LICENSE) 文件。

