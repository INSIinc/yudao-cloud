# 芋道 yudao-cloud 通用枚举与常量

> 本文档详细介绍 `yudao-framework/yudao-common/enums` 包下的通用枚举类和常量接口及其使用方法

## 目录

- [1. 概述](#1-概述)
- [2. CommonStatusEnum - 通用状态枚举](#2-commonstatusenum---通用状态枚举)
- [3. DateIntervalEnum - 时间间隔枚举](#3-dateintervalenum---时间间隔枚举)
- [4. TerminalEnum - 终端类型枚举](#4-terminalenum---终端类型枚举)
- [5. UserTypeEnum - 用户类型枚举](#5-usertypeenum---用户类型枚举)
- [6. RpcConstants - RPC 常量接口](#6-rpcconstants---rpc-常量接口)
- [7. WebFilterOrderEnum - Web 过滤器顺序](#7-webfilterorderenum---web-过滤器顺序)
- [8. DocumentEnum - 文档地址枚举](#8-documentenum---文档地址枚举)
- [9. 枚举设计最佳实践](#9-枚举设计最佳实践)
- [10. 常见问题](#10-常见问题)

---

## 1. 概述

### 为什么需要通用枚举？

在企业级应用开发中，许多业务概念（如状态、类型、终端等）会在多个模块中复用。通过定义通用枚举，可以：

| 优势 | 说明 |
|------|------|
| **统一标准** | 避免不同模块使用不同的魔法值（如有的用 0/1，有的用 true/false） |
| **类型安全** | 编译期检查，避免传入非法值 |
| **易于维护** | 状态变更只需修改枚举定义，所有引用处自动生效 |
| **语义清晰** | `CommonStatusEnum.ENABLE` 比 `0` 更易理解 |
| **支持校验** | 配合 `@InEnum` 注解实现参数校验 |

### 枚举分类

| 类型 | 枚举类 | 说明 |
|------|--------|------|
| **业务枚举** | CommonStatusEnum, UserTypeEnum, TerminalEnum | 直接用于业务逻辑 |
| **工具枚举** | DateIntervalEnum | 用于时间处理等工具方法 |
| **配置常量** | RpcConstants, WebFilterOrderEnum | 系统配置、框架级常量 |
| **文档枚举** | DocumentEnum | 文档链接集中管理 |

---

## 2. CommonStatusEnum - 通用状态枚举

### 功能说明

`CommonStatusEnum` 是系统中最常用的枚举，表示通用的"启用/禁用"状态，适用于菜单、用户、角色、配置等几乎所有需要状态控制的实体。

### 枚举定义

```java
@Getter
@AllArgsConstructor
public enum CommonStatusEnum implements ArrayValuable<Integer> {

    ENABLE(0, "开启"),
    DISABLE(1, "关闭");

    private final Integer status;  // 状态值
    private final String name;     // 状态名

    // 实现 ArrayValuable 接口，用于 @InEnum 校验
    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    // 工具方法：判断是否启用
    public static boolean isEnable(Integer status) {
        return ObjUtil.equal(ENABLE.status, status);
    }

    // 工具方法：判断是否禁用
    public static boolean isDisable(Integer status) {
        return ObjUtil.equal(DISABLE.status, status);
    }
}
```

### 值映射

| 枚举常量 | status 值 | name | 说明 |
|----------|-----------|------|------|
| `ENABLE` | 0 | "开启" | 启用状态 |
| `DISABLE` | 1 | "关闭" | 禁用状态 |

### 使用示例

#### 2.1 数据库实体定义

```java
@Data
@TableName("system_menu")
public class MenuDO extends BaseDO {

    private Long id;

    private String name;

    /**
     * 菜单状态
     * 0 - 开启
     * 1 - 关闭
     */
    private Integer status;

    // getter/setter...
}
```

#### 2.2 VO 层校验

```java
@Data
public class MenuSaveReqVO {

    private Long id;

    @NotBlank(message = "菜单名称不能为空")
    private String name;

    @NotNull(message = "状态不能为空")
    @InEnum(value = CommonStatusEnum.class, message = "状态必须是 {value}")
    private Integer status;
}
```

#### 2.3 Service 层判断

```java
@Service
public class MenuServiceImpl implements MenuService {

    @Override
    public void updateStatus(Long menuId, Integer status) {
        // ✅ 推荐：使用枚举的工具方法
        if (CommonStatusEnum.isDisable(status)) {
            // 禁用菜单时，需要同时禁用其所有子菜单
            List<MenuDO> children = menuMapper.selectByParentId(menuId);
            children.forEach(child -> child.setStatus(CommonStatusEnum.DISABLE.getStatus()));
            menuMapper.updateBatch(children);
        }

        menuMapper.updateById(new MenuDO().setId(menuId).setStatus(status));
    }

    @Override
    public List<MenuVO> listMenus() {
        // ✅ 推荐：使用枚举常量过滤
        return menuMapper.selectList(
            new LambdaQueryWrapper<MenuDO>()
                .eq(MenuDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
        );
    }
}
```

#### 2.4 前端交互示例

```json
// 请求示例
POST /admin-api/system/menu/save
{
  "name": "用户管理",
  "status": 0  // 0 表示启用
}

// 响应示例
{
  "code": 0,
  "data": {
    "id": 1,
    "name": "用户管理",
    "status": 0,
    "statusName": "开启"  // 后端可通过枚举获取
  }
}
```

#### 2.5 实战场景：批量启用/禁用

```java
@Service
public class UserServiceImpl implements UserService {

    @Override
    @Transactional
    public void batchUpdateStatus(List<Long> userIds, Integer status) {
        // 参数校验已通过 @InEnum 完成

        // 禁用用户时，需要踢出在线用户
        if (CommonStatusEnum.isDisable(status)) {
            userIds.forEach(userId -> {
                // 踢出在线用户
                loginService.kickOutUser(userId);
            });
        }

        // 批量更新状态
        userMapper.update(null,
            new LambdaUpdateWrapper<UserDO>()
                .in(UserDO::getId, userIds)
                .set(UserDO::getStatus, status)
        );
    }
}
```

### 常见使用场景

| 实体 | 字段 | 说明 |
|------|------|------|
| 用户（User） | status | 用户是否可登录 |
| 菜单（Menu） | status | 菜单是否显示 |
| 角色（Role） | status | 角色是否可用 |
| 配置（Config） | status | 配置是否生效 |
| 字典（Dict） | status | 字典项是否启用 |
| 租户（Tenant） | status | 租户是否可用 |
| 公告（Notice） | status | 公告是否发布 |

---

## 3. DateIntervalEnum - 时间间隔枚举

### 功能说明

`DateIntervalEnum` 用于定义时间区间粒度，常用于数据统计、报表生成、日期范围选择等场景。

### 枚举定义

```java
@Getter
@AllArgsConstructor
public enum DateIntervalEnum implements ArrayValuable<Integer> {

    HOUR(0, "小时"),     // 按小时统计
    DAY(1, "天"),        // 按天统计
    WEEK(2, "周"),       // 按周统计
    MONTH(3, "月"),      // 按月统计
    QUARTER(4, "季度"),  // 按季度统计
    YEAR(5, "年");       // 按年统计

    private final Integer interval;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static DateIntervalEnum valueOf(Integer interval) {
        return ArrayUtil.firstMatch(
            item -> item.getInterval().equals(interval),
            DateIntervalEnum.values()
        );
    }
}
```

### 值映射

| 枚举常量 | interval 值 | name | 说明 |
|----------|-------------|------|------|
| `HOUR` | 0 | "小时" | 按小时维度（注意：字典中暂无此项） |
| `DAY` | 1 | "天" | 按天维度 |
| `WEEK` | 2 | "周" | 按周维度 |
| `MONTH` | 3 | "月" | 按月维度 |
| `QUARTER` | 4 | "季度" | 按季度维度 |
| `YEAR` | 5 | "年" | 按年维度 |

### 使用示例

#### 3.1 数据统计场景

```java
@Data
public class SalesStatisticsReqVO {

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @NotNull(message = "统计间隔不能为空")
    @InEnum(value = DateIntervalEnum.class, message = "统计间隔必须是 {value}")
    private Integer interval; // 1-按天, 3-按月, 5-按年
}

@Service
public class SalesServiceImpl implements SalesService {

    @Override
    public List<SalesStatisticsRespVO> getStatistics(SalesStatisticsReqVO reqVO) {
        // 根据时间间隔生成时间范围列表
        List<LocalDateTime[]> timeRanges = LocalDateTimeUtils.getDateRangeList(
            reqVO.getStartTime(),
            reqVO.getEndTime(),
            reqVO.getInterval()
        );

        // 逐个时间段统计数据
        return timeRanges.stream().map(range -> {
            BigDecimal amount = orderMapper.sumAmountBetween(range[0], range[1]);

            return new SalesStatisticsRespVO()
                .setTimeRange(LocalDateTimeUtils.formatDateRange(
                    range[0], range[1], reqVO.getInterval()
                ))
                .setAmount(amount);
        }).collect(Collectors.toList());
    }
}
```

#### 3.2 报表生成场景

```java
@Service
public class ReportServiceImpl implements ReportService {

    @Override
    public void generateReport(ReportGenerateReqVO reqVO) {
        // 获取时间间隔枚举
        DateIntervalEnum intervalEnum = DateIntervalEnum.valueOf(reqVO.getInterval());

        // 根据不同间隔生成不同报表
        switch (intervalEnum) {
            case DAY:
                generateDailyReport(reqVO);
                break;
            case WEEK:
                generateWeeklyReport(reqVO);
                break;
            case MONTH:
                generateMonthlyReport(reqVO);
                break;
            case QUARTER:
                generateQuarterlyReport(reqVO);
                break;
            case YEAR:
                generateYearlyReport(reqVO);
                break;
            default:
                throw new IllegalArgumentException("不支持的时间间隔: " + intervalEnum);
        }
    }
}
```

#### 3.3 前端图表配置

```javascript
// Vue 3 示例
const intervalOptions = [
  { label: '按天', value: 1 },
  { label: '按周', value: 2 },
  { label: '按月', value: 3 },
  { label: '按季度', value: 4 },
  { label: '按年', value: 5 }
]

const loadChartData = async (interval) => {
  const response = await axios.get('/api/statistics', {
    params: {
      startTime: '2025-01-01',
      endTime: '2025-12-31',
      interval: interval  // 1/2/3/4/5
    }
  })

  // 渲染图表...
}
```

### 配合 LocalDateTimeUtils 使用

```java
// 生成时间范围列表
List<LocalDateTime[]> ranges = LocalDateTimeUtils.getDateRangeList(
    startTime,
    endTime,
    DateIntervalEnum.MONTH.getInterval()
);

// 输出：
// [2025-01-01 00:00:00, 2025-01-31 23:59:59]
// [2025-02-01 00:00:00, 2025-02-28 23:59:59]
// ...

// 格式化时间范围
String rangeText = LocalDateTimeUtils.formatDateRange(
    LocalDateTime.now(),
    LocalDateTime.now(),
    DateIntervalEnum.MONTH.getInterval()
);
// 输出："2025-11"
```

---

## 4. TerminalEnum - 终端类型枚举

### 功能说明

`TerminalEnum` 用于标识用户访问系统的终端类型，常用于多端登录、统计分析、权限控制等场景。

### 枚举定义

```java
@RequiredArgsConstructor
@Getter
public enum TerminalEnum implements ArrayValuable<Integer> {

    UNKNOWN(0, "未知"),              // 无法识别时的默认值
    WECHAT_MINI_PROGRAM(10, "微信小程序"),
    WECHAT_WAP(11, "微信公众号"),
    H5(20, "H5 网页"),
    APP(31, "手机 App");

    private final Integer terminal;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
```

### 值映射

| 枚举常量 | terminal 值 | name | 说明 |
|----------|-------------|------|------|
| `UNKNOWN` | 0 | "未知" | 默认值，无法解析终端时使用 |
| `WECHAT_MINI_PROGRAM` | 10 | "微信小程序" | 微信小程序端 |
| `WECHAT_WAP` | 11 | "微信公众号" | 微信公众号网页 |
| `H5` | 20 | "H5 网页" | 移动端网页 |
| `APP` | 31 | "手机 App" | 原生 App |

### 使用示例

#### 4.1 登录场景

```java
@Data
public class AuthLoginReqVO {

    @NotBlank(message = "手机号不能为空")
    @Mobile
    private String mobile;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotNull(message = "终端不能为空")
    @InEnum(value = TerminalEnum.class, message = "终端类型必须是 {value}")
    private Integer terminal; // 10-小程序, 11-公众号, 20-H5, 31-App
}

@Service
public class AuthServiceImpl implements AuthService {

    @Override
    public AuthLoginRespVO login(AuthLoginReqVO reqVO) {
        // 校验账号密码
        MemberDO member = validateUser(reqVO.getMobile(), reqVO.getPassword());

        // 创建访问令牌（包含终端信息）
        String accessToken = tokenService.createAccessToken(
            member.getId(),
            reqVO.getTerminal()
        );

        // 记录登录日志
        loginLogService.createLoginLog(
            member.getId(),
            reqVO.getTerminal(),
            LoginResultEnum.SUCCESS
        );

        return new AuthLoginRespVO()
            .setAccessToken(accessToken)
            .setUserId(member.getId());
    }
}
```

#### 4.2 Token 管理

```java
@Data
public class OAuth2AccessTokenDO extends BaseDO {

    private Long userId;

    private String accessToken;

    /**
     * 终端类型
     */
    private Integer terminal;

    private LocalDateTime expiresTime;
}

@Service
public class TokenServiceImpl implements TokenService {

    @Override
    public void kickOutByTerminal(Long userId, Integer terminal) {
        // 踢出指定用户在指定终端的所有登录
        oauth2AccessTokenMapper.delete(
            new LambdaQueryWrapper<OAuth2AccessTokenDO>()
                .eq(OAuth2AccessTokenDO::getUserId, userId)
                .eq(OAuth2AccessTokenDO::getTerminal, terminal)
        );
    }

    @Override
    public List<UserSessionRespVO> getUserSessions(Long userId) {
        // 获取用户在各终端的登录会话
        List<OAuth2AccessTokenDO> tokens = oauth2AccessTokenMapper.selectList(
            new LambdaQueryWrapper<OAuth2AccessTokenDO>()
                .eq(OAuth2AccessTokenDO::getUserId, userId)
        );

        return tokens.stream().map(token -> {
            TerminalEnum terminal = Arrays.stream(TerminalEnum.values())
                .filter(t -> t.getTerminal().equals(token.getTerminal()))
                .findFirst()
                .orElse(TerminalEnum.UNKNOWN);

            return new UserSessionRespVO()
                .setTerminal(terminal.getTerminal())
                .setTerminalName(terminal.getName())
                .setAccessToken(token.getAccessToken())
                .setExpireTime(token.getExpiresTime());
        }).collect(Collectors.toList());
    }
}
```

#### 4.3 统计分析

```java
@Service
public class StatisticsServiceImpl implements StatisticsService {

    @Override
    public Map<Integer, Long> getUserCountByTerminal() {
        // 统计各终端的活跃用户数
        List<OAuth2AccessTokenDO> tokens = oauth2AccessTokenMapper.selectList(
            new LambdaQueryWrapper<OAuth2AccessTokenDO>()
                .gt(OAuth2AccessTokenDO::getExpiresTime, LocalDateTime.now())
        );

        return tokens.stream()
            .collect(Collectors.groupingBy(
                OAuth2AccessTokenDO::getTerminal,
                Collectors.counting()
            ));
    }
}
```

#### 4.4 前端示例

```javascript
// 小程序登录
wx.login({
  success: (res) => {
    axios.post('/app-api/member/auth/login', {
      code: res.code,
      terminal: 10  // 微信小程序
    })
  }
})

// H5 登录
axios.post('/app-api/member/auth/login', {
  mobile: '13812345678',
  password: '123456',
  terminal: 20  // H5 网页
})
```

---

## 5. UserTypeEnum - 用户类型枚举

### 功能说明

`UserTypeEnum` 用于区分系统中的用户角色，典型的 B 端（管理后台）和 C 端（普通用户）分离架构。

### 枚举定义

```java
@AllArgsConstructor
@Getter
public enum UserTypeEnum implements ArrayValuable<Integer> {

    MEMBER(1, "会员"),    // C 端用户
    ADMIN(2, "管理员");   // B 端用户

    private final Integer value;
    private final String name;

    public static UserTypeEnum valueOf(Integer value) {
        return ArrayUtil.firstMatch(
            userType -> userType.getValue().equals(value),
            UserTypeEnum.values()
        );
    }

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
```

### 值映射

| 枚举常量 | value 值 | name | 说明 |
|----------|----------|------|------|
| `MEMBER` | 1 | "会员" | 面向 C 端的普通用户（消费者） |
| `ADMIN` | 2 | "管理员" | 面向 B 端的管理员（运营人员） |

### 使用示例

#### 5.1 登录 Token 区分

```java
@Data
public class LoginUserInfo {

    private Long userId;

    private String username;

    /**
     * 用户类型
     * 1 - 会员
     * 2 - 管理员
     */
    private Integer userType;

    // getter/setter...
}

@Service
public class AuthServiceImpl implements AuthService {

    @Override
    public AuthLoginRespVO memberLogin(MemberLoginReqVO reqVO) {
        // 会员登录
        MemberDO member = memberService.validateLogin(reqVO);

        // 创建 Token（标记为会员类型）
        String accessToken = tokenService.createAccessToken(
            member.getId(),
            UserTypeEnum.MEMBER.getValue()
        );

        return new AuthLoginRespVO().setAccessToken(accessToken);
    }

    @Override
    public AuthLoginRespVO adminLogin(AdminLoginReqVO reqVO) {
        // 管理员登录
        AdminUserDO admin = adminUserService.validateLogin(reqVO);

        // 创建 Token（标记为管理员类型）
        String accessToken = tokenService.createAccessToken(
            admin.getId(),
            UserTypeEnum.ADMIN.getValue()
        );

        return new AuthLoginRespVO().setAccessToken(accessToken);
    }
}
```

#### 5.2 权限校验

```java
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从 Token 中获取用户信息
        LoginUserInfo loginUser = SecurityFrameworkUtils.getLoginUser();

        // 判断用户类型
        if (UserTypeEnum.MEMBER.getValue().equals(loginUser.getUserType())) {
            // 会员类型：只能访问 /app-api/** 接口
            if (!request.getRequestURI().startsWith("/app-api/")) {
                throw new ServiceException(ErrorCodeConstants.FORBIDDEN);
            }
        } else if (UserTypeEnum.ADMIN.getValue().equals(loginUser.getUserType())) {
            // 管理员类型：只能访问 /admin-api/** 接口
            if (!request.getRequestURI().startsWith("/admin-api/")) {
                throw new ServiceException(ErrorCodeConstants.FORBIDDEN);
            }
        }

        return true;
    }
}
```

#### 5.3 多用户体系数据隔离

```java
@Service
public class OrderServiceImpl implements OrderService {

    @Override
    public PageResult<OrderDO> getOrderPage(OrderPageReqVO reqVO) {
        LoginUserInfo loginUser = SecurityFrameworkUtils.getLoginUser();

        // 根据用户类型查询不同的订单
        if (UserTypeEnum.MEMBER.getValue().equals(loginUser.getUserType())) {
            // 会员：只能查看自己的订单
            reqVO.setUserId(loginUser.getUserId());
        } else if (UserTypeEnum.ADMIN.getValue().equals(loginUser.getUserType())) {
            // 管理员：可以查看所有订单（reqVO.userId 可选填）
        }

        return orderMapper.selectPage(reqVO);
    }
}
```

#### 5.4 前端路由示例

```javascript
// 根据用户类型跳转不同页面
const loginSuccess = (userType) => {
  if (userType === 1) {
    // 会员登录成功 → 跳转到会员中心
    router.push('/member/home')
  } else if (userType === 2) {
    // 管理员登录成功 → 跳转到管理后台
    router.push('/admin/dashboard')
  }
}
```

---

## 6. RpcConstants - RPC 常量接口

### 功能说明

`RpcConstants` 定义了微服务架构下 RPC 调用的统一路径前缀和服务名称，确保服务之间的正确识别和调用。

### 常量定义

```java
public interface RpcConstants {

    /**
     * 所有 RPC 接口的统一路径前缀
     */
    String RPC_API_PREFIX = "/rpc-api";

    /**
     * system 服务名称（必须与 application.yml 中的 spring.application.name 一致）
     */
    String SYSTEM_NAME = "system-server";

    /**
     * system 服务的 RPC 路径前缀
     */
    String SYSTEM_PREFIX = RPC_API_PREFIX + "/system";

    /**
     * infra 服务名称
     */
    String INFRA_NAME = "infra-server";

    /**
     * infra 服务的 RPC 路径前缀
     */
    String INFRA_PREFIX = RPC_API_PREFIX + "/infra";
}
```

### 常量映射

| 常量 | 值 | 说明 |
|------|-----|------|
| `RPC_API_PREFIX` | `/rpc-api` | RPC 接口统一前缀 |
| `SYSTEM_NAME` | `system-server` | 系统服务名称 |
| `SYSTEM_PREFIX` | `/rpc-api/system` | 系统服务 RPC 路径前缀 |
| `INFRA_NAME` | `infra-server` | 基础设施服务名称 |
| `INFRA_PREFIX` | `/rpc-api/infra` | 基础设施服务 RPC 路径前缀 |

### 使用示例

#### 6.1 Feign Client 定义

```java
@FeignClient(name = RpcConstants.SYSTEM_NAME, path = RpcConstants.SYSTEM_PREFIX)
public interface UserApi {

    String PREFIX = "/user";

    /**
     * 根据 ID 获取用户
     * 完整路径：/rpc-api/system/user/get
     */
    @GetMapping(PREFIX + "/get")
    CommonResult<UserRespDTO> getUser(@RequestParam("id") Long id);

    /**
     * 批量获取用户
     * 完整路径：/rpc-api/system/user/list-by-ids
     */
    @GetMapping(PREFIX + "/list-by-ids")
    CommonResult<List<UserRespDTO>> getUserList(@RequestParam("ids") Collection<Long> ids);
}
```

#### 6.2 Controller 实现

```java
@RestController
@RequestMapping(RpcConstants.SYSTEM_PREFIX + "/user")
public class UserApiController implements UserApi {

    @Resource
    private UserService userService;

    @Override
    @GetMapping("/get")
    public CommonResult<UserRespDTO> getUser(@RequestParam("id") Long id) {
        UserDO user = userService.getUser(id);
        return CommonResult.success(BeanUtils.toBean(user, UserRespDTO.class));
    }

    @Override
    @GetMapping("/list-by-ids")
    public CommonResult<List<UserRespDTO>> getUserList(@RequestParam("ids") Collection<Long> ids) {
        List<UserDO> users = userService.getUserList(ids);
        return CommonResult.success(BeanUtils.toBean(users, UserRespDTO.class));
    }
}
```

#### 6.3 服务调用示例

```java
@Service
public class OrderServiceImpl implements OrderService {

    @Resource
    private UserApi userApi; // Feign 客户端

    @Override
    public OrderDetailVO getOrderDetail(Long orderId) {
        // 查询订单
        OrderDO order = orderMapper.selectById(orderId);

        // 远程调用 system 服务获取用户信息
        UserRespDTO user = userApi.getUser(order.getUserId()).getCheckedData();

        // 组装返回数据
        return new OrderDetailVO()
            .setOrderId(orderId)
            .setUserName(user.getNickname())
            .setAmount(order.getAmount());
    }
}
```

#### 6.4 Gateway 路由配置

```yaml
spring:
  cloud:
    gateway:
      routes:
        # System 服务路由
        - id: system-rpc
          uri: lb://system-server  # 与 RpcConstants.SYSTEM_NAME 一致
          predicates:
            - Path=/rpc-api/system/**
          filters:
            - StripPrefix=1

        # Infra 服务路由
        - id: infra-rpc
          uri: lb://infra-server
          predicates:
            - Path=/rpc-api/infra/**
          filters:
            - StripPrefix=1
```

### 设计原则

1. **服务名称一致性**：`SYSTEM_NAME` 必须与 `application.yml` 中的 `spring.application.name` 一致
2. **路径前缀规范**：所有 RPC 接口都以 `/rpc-api` 开头，便于网关路由和鉴权
3. **集中管理**：所有服务名称和路径集中定义，避免硬编码分散

---

## 7. WebFilterOrderEnum - Web 过滤器顺序

### 功能说明

`WebFilterOrderEnum` 定义了 Spring Web 过滤器（Filter）的执行顺序，确保各过滤器按正确的顺序执行，避免因顺序错乱导致功能异常。

### 设计原则

- 使用 `Integer.MIN_VALUE` 作为起点，逐步递增
- 关键框架过滤器（如 Spring Security）的默认 order 已知，围绕其安排业务过滤器
- 依赖关系通过注释明确说明

### 常量定义

```java
public interface WebFilterOrderEnum {

    // ========== 早期过滤器（最先执行） ==========

    /**
     * CORS 过滤器：最先执行，处理跨域请求
     */
    int CORS_FILTER = Integer.MIN_VALUE;

    /**
     * Trace 过滤器：初始化链路追踪上下文
     */
    int TRACE_FILTER = CORS_FILTER + 1;

    /**
     * 环境标签过滤器：注入环境标识
     */
    int ENV_TAG_FILTER = TRACE_FILTER + 1;

    /**
     * 请求体缓存过滤器：包装 Request，支持多次读取 Body
     */
    int REQUEST_BODY_CACHE_FILTER = Integer.MIN_VALUE + 500;

    /**
     * API 加解密过滤器：依赖缓存后的请求体
     */
    int API_ENCRYPT_FILTER = REQUEST_BODY_CACHE_FILTER + 1;

    // ========== 中期过滤器 ==========

    /**
     * 多租户上下文过滤器：从请求中提取租户 ID
     */
    int TENANT_CONTEXT_FILTER = -104;

    /**
     * API 访问日志过滤器：记录请求日志
     */
    int API_ACCESS_LOG_FILTER = -103;

    /**
     * XSS 防护过滤器：过滤恶意脚本
     */
    int XSS_FILTER = -102;

    // ========== Spring Security（默认 -100） ==========

    /**
     * 多租户安全过滤器：在 Spring Security 之后执行
     */
    int TENANT_SECURITY_FILTER = -99;

    /**
     * Flowable 过滤器：同步用户信息到工作流引擎
     */
    int FLOWABLE_FILTER = -98;

    // ========== 晚期过滤器（最后执行） ==========

    /**
     * 演示模式过滤器：最后执行，拦截写操作
     */
    int DEMO_FILTER = Integer.MAX_VALUE;
}
```

### 执行顺序图

```
请求流入
    ↓
[CORS_FILTER]                    (-2147483648) 处理跨域
    ↓
[TRACE_FILTER]                   (-2147483647) 初始化 TraceId
    ↓
[ENV_TAG_FILTER]                 (-2147483646) 注入环境标识
    ↓
[REQUEST_BODY_CACHE_FILTER]      (-2147483148) 缓存请求体
    ↓
[API_ENCRYPT_FILTER]             (-2147483147) 解密请求
    ↓
[TENANT_CONTEXT_FILTER]          (-104) 设置租户上下文
    ↓
[API_ACCESS_LOG_FILTER]          (-103) 记录访问日志
    ↓
[XSS_FILTER]                     (-102) XSS 过滤
    ↓
[Spring Security FilterChain]    (-100) 认证授权
    ↓
[TENANT_SECURITY_FILTER]         (-99) 租户权限校验
    ↓
[FLOWABLE_FILTER]                (-98) 工作流集成
    ↓
Controller
    ↓
[DEMO_FILTER]                    (2147483647) 演示模式拦截
    ↓
响应返回
```

### 使用示例

#### 7.1 定义自定义过滤器

```java
@Component
@Order(WebFilterOrderEnum.TENANT_CONTEXT_FILTER)
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 从请求头中获取租户 ID
        String tenantId = request.getHeader("tenant-id");

        try {
            // 设置到 ThreadLocal
            TenantContextHolder.setTenantId(Long.valueOf(tenantId));

            // 继续执行后续过滤器
            filterChain.doFilter(request, response);
        } finally {
            // 清理 ThreadLocal
            TenantContextHolder.clear();
        }
    }
}
```

#### 7.2 注册过滤器 Bean

```java
@Configuration
public class WebFilterConfig {

    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilter() {
        FilterRegistrationBean<TenantContextFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TenantContextFilter());
        bean.setOrder(WebFilterOrderEnum.TENANT_CONTEXT_FILTER);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    public FilterRegistrationBean<ApiAccessLogFilter> apiAccessLogFilter() {
        FilterRegistrationBean<ApiAccessLogFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new ApiAccessLogFilter());
        bean.setOrder(WebFilterOrderEnum.API_ACCESS_LOG_FILTER);
        bean.addUrlPatterns("/admin-api/*", "/app-api/*");
        return bean;
    }
}
```

### 常见过滤器顺序

| 过滤器 | Order 值 | 说明 |
|--------|----------|------|
| CORS | -2147483648 | 最早执行，处理跨域 |
| Trace | -2147483647 | 初始化链路追踪 |
| Request Body Cache | -2147483148 | 缓存请求体 |
| Tenant Context | -104 | 设置租户上下文 |
| Access Log | -103 | 记录访问日志 |
| XSS | -102 | XSS 防护 |
| Spring Security | -100 | 认证授权（框架默认） |
| Tenant Security | -99 | 租户权限校验 |
| Demo Mode | 2147483647 | 最后执行，拦截写操作 |

---

## 8. DocumentEnum - 文档地址枚举

### 功能说明

`DocumentEnum` 集中管理系统中常用的文档链接，便于统一维护和引用。

### 枚举定义

```java
@Getter
@AllArgsConstructor
public enum DocumentEnum {

    REDIS_INSTALL("https://gitee.com/zhijiantianya/ruoyi-vue-pro/issues/I4VCSJ",
                  "Redis 安装文档"),

    TENANT("https://doc.iocoder.cn",
           "SaaS 多租户文档");

    private final String url;
    private final String memo;
}
```

### 使用示例

#### 8.1 异常提示中引用文档

```java
@Service
public class RedisServiceImpl implements RedisService {

    @Override
    public void connect() {
        try {
            redisTemplate.getConnectionFactory().getConnection();
        } catch (Exception e) {
            throw new ServiceException(
                ErrorCodeConstants.REDIS_CONNECTION_ERROR,
                "Redis 连接失败，请参考文档：" + DocumentEnum.REDIS_INSTALL.getUrl()
            );
        }
    }
}
```

#### 8.2 帮助页面

```java
@GetMapping("/help")
public CommonResult<List<DocumentVO>> getHelpDocuments() {
    List<DocumentVO> documents = Arrays.stream(DocumentEnum.values())
        .map(doc -> new DocumentVO()
            .setTitle(doc.getMemo())
            .setUrl(doc.getUrl())
        )
        .collect(Collectors.toList());

    return CommonResult.success(documents);
}
```

#### 8.3 前端展示

```vue
<template>
  <div class="help-docs">
    <h3>帮助文档</h3>
    <ul>
      <li v-for="doc in documents" :key="doc.url">
        <a :href="doc.url" target="_blank">{{ doc.title }}</a>
      </li>
    </ul>
  </div>
</template>
```

---

## 9. 枚举设计最佳实践

### 9.1 实现 ArrayValuable 接口

```java
// ✅ 推荐：实现 ArrayValuable 接口，支持 @InEnum 校验
@Getter
@AllArgsConstructor
public enum OrderStatusEnum implements ArrayValuable<Integer> {

    PENDING(0, "待支付"),
    PAID(1, "已支付");

    private final Integer value;
    private final String label;

    private static final Integer[] ARRAYS = Arrays.stream(values())
        .map(OrderStatusEnum::getValue)
        .toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}

// ❌ 不推荐：不实现接口，无法使用 @InEnum
public enum OrderStatusEnum {
    PENDING(0, "待支付"),
    PAID(1, "已支付");

    private final Integer value;
    private final String label;
}
```

### 9.2 提供静态工具方法

```java
// ✅ 推荐：提供 valueOf() 方法，根据 value 查找枚举
@Getter
@AllArgsConstructor
public enum UserTypeEnum implements ArrayValuable<Integer> {

    MEMBER(1, "会员"),
    ADMIN(2, "管理员");

    private final Integer value;
    private final String name;

    /**
     * 根据 value 查找枚举
     */
    public static UserTypeEnum valueOf(Integer value) {
        return ArrayUtil.firstMatch(
            userType -> userType.getValue().equals(value),
            UserTypeEnum.values()
        );
    }

    // 提供 isXxx() 判断方法
    public static boolean isMember(Integer value) {
        return MEMBER.getValue().equals(value);
    }

    public static boolean isAdmin(Integer value) {
        return ADMIN.getValue().equals(value);
    }

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
```

### 9.3 枚举值设计规范

```java
// ✅ 推荐：使用 Integer 类型，便于存储和传输
@Getter
@AllArgsConstructor
public enum StatusEnum implements ArrayValuable<Integer> {
    ENABLE(0, "启用"),
    DISABLE(1, "禁用");

    private final Integer status;
    private final String name;
}

// ⚠️ 谨慎使用：String 类型（仅当业务确实需要时）
public enum LanguageEnum implements ArrayValuable<String> {
    ZH_CN("zh_CN", "简体中文"),
    EN_US("en_US", "英语");

    private final String code;
    private final String name;
}

// ❌ 不推荐：使用 ordinal() 或 name()
// 原因：ordinal 会随枚举顺序变化，name 不利于国际化
```

### 9.4 字段命名规范

```java
// ✅ 推荐：统一使用 value/status/code 作为值字段，name/label 作为名称字段
private final Integer value;
private final String name;

// 或
private final Integer status;
private final String name;

// 或
private final String code;
private final String label;

// ❌ 不推荐：不统一的命名
private final Integer statusValue;
private final String statusName;
```

### 9.5 缓存 ARRAYS 数组

```java
// ✅ 推荐：缓存 array() 结果，避免重复计算
public static final Integer[] ARRAYS = Arrays.stream(values())
    .map(UserStatusEnum::getStatus)
    .toArray(Integer[]::new);

@Override
public Integer[] array() {
    return ARRAYS;
}

// ❌ 不推荐：每次调用都重新计算
@Override
public Integer[] array() {
    return Arrays.stream(values())
        .map(UserStatusEnum::getStatus)
        .toArray(Integer[]::new);
}
```

---

## 10. 常见问题

### Q1: 为什么 CommonStatusEnum 的 ENABLE 是 0，DISABLE 是 1？

**A**:
这是一种常见的数据库设计惯例：

| 设计原则 | 说明 |
|----------|------|
| 默认值友好 | 数据库默认值通常为 0，创建记录时自动为"启用"状态 |
| 位运算友好 | 0 表示正常，1 表示异常，便于位运算 |
| 布尔映射 | 0 ≈ false，1 ≈ true，语义上 0 表示"正常/开启" |

**但也有使用 1=启用、0=禁用 的项目**，关键是团队内部统一。

### Q2: DateIntervalEnum 为什么没有"分钟"选项？

**A**:
因为大多数业务统计场景（如销售报表、用户增长）通常按天、周、月维度，"分钟"级别的统计需求较少。

如果确实需要，可以自行扩展：

```java
MINUTE(6, "分钟"),
```

### Q3: TerminalEnum 的值为什么不是连续的？

**A**:
| terminal 值 | 说明 |
|-------------|------|
| 0 | 未知 |
| 10, 11 | 微信系列（小程序、公众号） |
| 20 | H5 网页 |
| 31 | App |

**设计考虑**：
- 预留扩展空间（如 12 可以是"微信企业号"）
- 按类别分段（10-19 微信，20-29 网页，30-39 App）

### Q4: 如何扩展 RpcConstants 添加新服务？

**A**:

```java
public interface RpcConstants {

    // ... 已有常量 ...

    /**
     * 新增：商城服务
     */
    String MALL_NAME = "mall-server";
    String MALL_PREFIX = RPC_API_PREFIX + "/mall";

    /**
     * 新增：支付服务
     */
    String PAY_NAME = "pay-server";
    String PAY_PREFIX = RPC_API_PREFIX + "/pay";
}
```

**配置 application.yml**：

```yaml
spring:
  application:
    name: mall-server  # 必须与 RpcConstants.MALL_NAME 一致
```

### Q5: WebFilterOrderEnum 的值为什么用负数？

**A**:
| 值范围 | 说明 |
|--------|------|
| Integer.MIN_VALUE ~ -100 | 自定义过滤器 |
| -100 | Spring Security 默认值 |
| -99 ~ Integer.MAX_VALUE | 业务过滤器、演示模式等 |

**负数的好处**：
- 确保在大多数框架过滤器之前执行
- Spring 默认过滤器通常使用正数，避免冲突

### Q6: 为什么要单独定义 DocumentEnum？

**A**:

**好处**：
1. **集中管理**：所有文档链接在一处，便于维护
2. **避免硬编码**：修改 URL 只需改枚举，所有引用处自动生效
3. **类型安全**：编译期检查，避免拼写错误

**对比**：

```java
// ❌ 硬编码（分散在各处，难以维护）
throw new ServiceException("Redis 连接失败，请参考：https://...");

// ✅ 使用枚举（集中管理，易于修改）
throw new ServiceException("Redis 连接失败，请参考：" + DocumentEnum.REDIS_INSTALL.getUrl());
```

### Q7: 如何在前端字典中展示枚举？

**A**:

**后端提供字典接口**：

```java
@GetMapping("/dict/common-status")
public CommonResult<List<DictDataVO>> getCommonStatusDict() {
    List<DictDataVO> list = Arrays.stream(CommonStatusEnum.values())
        .map(status -> new DictDataVO()
            .setValue(status.getStatus().toString())
            .setLabel(status.getName())
        )
        .collect(Collectors.toList());

    return CommonResult.success(list);
}
```

**前端使用**：

```vue
<template>
  <el-select v-model="form.status">
    <el-option
      v-for="item in statusOptions"
      :key="item.value"
      :label="item.label"
      :value="Number(item.value)"
    />
  </el-select>
</template>

<script>
export default {
  data() {
    return {
      statusOptions: []
    }
  },
  async created() {
    const { data } = await this.$axios.get('/api/dict/common-status')
    this.statusOptions = data.data
  }
}
</script>
```

---

## 附录：枚举一览表

### 业务枚举

| 枚举类 | 值类型 | 枚举值 | 说明 |
|--------|--------|--------|------|
| CommonStatusEnum | Integer | 0-启用, 1-禁用 | 通用状态 |
| UserTypeEnum | Integer | 1-会员, 2-管理员 | 用户类型 |
| TerminalEnum | Integer | 0-未知, 10-小程序, 11-公众号, 20-H5, 31-App | 终端类型 |
| DateIntervalEnum | Integer | 0-小时, 1-天, 2-周, 3-月, 4-季度, 5-年 | 时间间隔 |

### 配置常量

| 接口 | 常量数量 | 说明 |
|------|----------|------|
| RpcConstants | 6 | RPC 服务名称和路径前缀 |
| WebFilterOrderEnum | 11 | Web 过滤器执行顺序 |

### 文档枚举

| 枚举类 | 枚举值数量 | 说明 |
|--------|-----------|------|
| DocumentEnum | 2 | 常用文档链接（可扩展） |

---

## 技术栈

- **Lombok**: 1.18+（减少样板代码）
- **Hutool**: 5.x+（ArrayUtil 工具）
- **Jakarta Validation**: 3.0+（@InEnum 校验）

---

## 参考资料

- [Java 枚举最佳实践](https://www.baeldung.com/java-enum)
- [Spring Filter 顺序管理](https://docs.spring.io/spring-framework/reference/web/webmvc/filters.html)
- [芋道源码官方文档](https://doc.iocoder.cn/)

---

**更新时间**: 2025-11-07
**维护者**: 芋道源码团队
