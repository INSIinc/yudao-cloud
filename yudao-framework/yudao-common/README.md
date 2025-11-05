# yudao-common

## 📚 模块简介

`yudao-common` 是芋道项目的**核心基础模块**，提供了与业务框架无关的通用基础类、工具类、枚举、异常处理等核心功能。作为整个 yudao-framework 的基石，该模块被其他所有模块所依赖。

> 定义基础 POJO 类、枚举、工具类等等

## 🎯 设计目标

- **通用性**：提供与具体业务无关的基础工具和类型定义
- **可复用性**：封装常用功能，避免重复造轮子
- **标准化**：统一项目中的响应格式、异常处理、数据校验等规范
- **轻量级**：核心依赖使用 `provided` 作用域，按需引入，避免依赖冲突

## 📦 主要功能

### 1. 通用响应封装

- **CommonResult**：统一的 API 响应结果封装，支持成功/失败状态
- **PageResult**：分页查询结果封装
- **PageParam**：分页查询参数基类
- **SortablePageParam**：支持排序的分页参数

### 2. 异常处理体系

- **ServiceException**：业务异常类，用于业务逻辑错误
- **ServerException**：服务器异常类
- **ErrorCode**：错误码定义
- **GlobalErrorCodeConstants**：全局错误码常量
- **ServiceErrorCodeRange**：服务错误码范围定义

### 3. 数据校验

- **@InEnum**：枚举值校验注解
- **@Telephone**：手机号校验注解
- 集成 Jakarta Validation 校验框架

### 4. 工具类库

#### 集合工具
- `CollectionUtils`：集合操作工具类
- `ArrayUtils`：数组操作工具类
- `MapUtils`：Map 操作工具类
- `SetUtils`：Set 操作工具类

#### 对象工具
- `BeanUtils`：对象属性拷贝工具（基于 MapStruct）
- `ObjectUtils`：对象通用工具
- `PageUtils`：分页工具

#### 数值工具
- `NumberUtils`：数值处理工具
- `MoneyUtils`：金额处理工具

#### 字符串与日期
- 字符串处理工具
- 日期时间工具

#### IO 与网络
- `FileUtils`：文件操作工具
- `IoUtils`：IO 流工具
- `HttpUtils`：HTTP 请求工具

#### 其他
- `CacheUtils`：缓存工具
- `JsonUtils`：JSON 处理工具

### 5. 通用枚举

- **CommonStatusEnum**：通用状态枚举（启用/禁用）
- **UserTypeEnum**：用户类型枚举
- **TerminalEnum**：终端类型枚举
- **DocumentEnum**：文档类型枚举
- **DateIntervalEnum**：日期间隔枚举
- **WebFilterOrderEnum**：Web 过滤器顺序枚举

### 6. RPC 相关

- **RpcConstants**：RPC 常量定义
- OAuth2 Token 相关 DTO
- 租户相关 API 接口定义

### 7. 业务 API 接口

- **ApiAccessLogCommonApi**：API 访问日志接口
- **ApiErrorLogCommonApi**：API 错误日志接口
- **OperateLogCommonApi**：操作日志接口
- **OAuth2TokenCommonApi**：OAuth2 令牌接口
- **TenantCommonApi**：租户接口

## 📂 目录结构

```
yudao-common
├── src/main/java/cn/iocoder/yudao/framework/common
│   ├── biz/                    # 业务相关通用接口
│   │   ├── infra/             # 基础设施相关
│   │   │   └── logger/        # 日志相关 API
│   │   └── system/            # 系统相关
│   │       ├── oauth2/        # OAuth2 相关 DTO 和 API
│   │       ├── tenant/        # 租户相关 API
│   │       └── logger/        # 系统日志 API
│   ├── core/                  # 核心接口定义
│   │   ├── ArrayValuable      # 数组值接口
│   │   └── KeyValue           # 键值对类
│   ├── enums/                 # 通用枚举定义
│   ├── exception/             # 异常体系
│   │   └── enums/            # 异常相关枚举
│   ├── pojo/                  # 基础 POJO 类
│   ├── util/                  # 工具类库
│   │   ├── cache/            # 缓存工具
│   │   ├── collection/       # 集合工具
│   │   ├── date/             # 日期工具
│   │   ├── http/             # HTTP 工具
│   │   ├── io/               # IO 工具
│   │   ├── json/             # JSON 工具
│   │   ├── number/           # 数值工具
│   │   ├── object/           # 对象工具
│   │   ├── servlet/          # Servlet 工具
│   │   ├── spring/           # Spring 工具
│   │   ├── string/           # 字符串工具
│   │   └── validation/       # 校验工具
│   └── validation/            # 自定义校验注解
└── pom.xml
```

## 🚀 快速开始

### Maven 依赖

在其他模块的 `pom.xml` 中引入：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-common</artifactId>
</dependency>
```

### 使用示例

#### 1. 统一响应封装

```java
import cn.iocoder.yudao.framework.common.pojo.CommonResult;

// 成功响应
CommonResult<UserVO> result = CommonResult.success(userVO);

// 失败响应
CommonResult<Void> error = CommonResult.error(ErrorCode.USER_NOT_EXISTS);

// 分页响应
PageResult<UserVO> pageResult = new PageResult<>(userList, total);
```

#### 2. 业务异常处理

```java
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;

// 抛出业务异常
throw new ServiceException(new ErrorCode(10001, "用户不存在"));

// 使用全局错误码
throw new ServiceException(GlobalErrorCodeConstants.BAD_REQUEST);
```

#### 3. 分页查询

```java
import cn.iocoder.yudao.framework.common.pojo.PageParam;

@Data
public class UserPageReqVO extends PageParam {
    private String username;
    private Integer status;
}
```

#### 4. 数据校验

```java
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.framework.common.validation.Telephone;

public class UserCreateReqVO {
    
    @Telephone
    private String mobile;
    
    @InEnum(CommonStatusEnum.class)
    private Integer status;
}
```

#### 5. 工具类使用

```java
// 对象拷贝
UserVO userVO = BeanUtils.toBean(user, UserVO.class);
List<UserVO> userVOList = BeanUtils.toBean(userList, UserVO.class);

// 集合操作
List<Long> ids = CollectionUtils.convertList(userList, User::getId);
Map<Long, User> userMap = CollectionUtils.convertMap(userList, User::getId);

// 金额处理（分 -> 元）
String yuanStr = MoneyUtils.fenToYuanStr(10000); // "100.00"
```

## 🔧 核心依赖

### 基础框架
- Spring Core 6.x
- Spring Web 6.x
- Spring Boot 3.x

### 工具库
- **Hutool**：Java 工具类库
- **Guava**：Google 核心库
- **MapStruct**：对象映射框架
- **Lombok**：简化 Java 代码

### 其他
- **Jackson**：JSON 处理
- **SLF4J**：日志门面
- **Jakarta Validation**：参数校验
- **Apache SkyWalking**：链路追踪
- **TransmittableThreadLocal**：线程变量传递
- **Easy Trans**：VO 数据翻译

> **注意**：大部分依赖使用 `provided` 作用域，实际使用时需要在业务模块中显式引入。

## 📖 设计原则

1. **依赖最小化**：核心依赖设置为 `provided`，避免强制依赖
2. **职责单一**：每个类专注于单一职责，保持简洁
3. **向后兼容**：保持 API 稳定，谨慎修改公共接口
4. **文档完善**：关键类和方法提供详细的 JavaDoc
5. **测试覆盖**：核心工具类提供单元测试

## 🤝 与其他模块关系

- **被依赖方**：所有 yudao-framework 下的其他模块都依赖此模块
- **独立性**：不依赖任何其他 yudao 模块，保持独立性
- **扩展性**：提供接口和抽象类供其他模块扩展

## ⚠️ 注意事项

1. **不要在此模块中添加具体业务逻辑**，保持通用性
2. **新增工具类前请检查是否已存在**，避免重复
3. **修改公共接口时务必谨慎**，可能影响所有依赖模块
4. **工具类方法应该是静态的**，无状态的
5. **异常类需要继承自 ServiceException 或 ServerException**

## 📝 贡献指南

如需新增功能或修改现有功能：

1. 确保新增类/方法具有通用性，非业务特定
2. 提供完整的 JavaDoc 注释
3. 编写对应的单元测试
4. 更新本 README 文档

## 📄 License

本模块遵循项目整体许可证，详见根目录 [LICENSE](../../LICENSE) 文件。

---

> 💡 **提示**：这是一个基础模块，请保持其简洁性和通用性。如有业务相关的通用功能，请考虑放在对应的业务模块中。

