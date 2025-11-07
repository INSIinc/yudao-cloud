# yudao-module-system

系统模块，提供整个微服务平台的基础核心功能，包括用户管理、权限控制、部门组织、数据字典、租户管理、消息通知等通用业务能力。

## 模块概述

system 模块是芋道微服务架构的核心基础设施模块，为所有其他业务模块提供统一的用户体系、权限体系、组织架构、配置管理等基础服务。

**核心职责**：
- 用户与认证：管理员用户、OAuth2 认证、社交登录
- 权限控制：菜单管理、角色管理、权限分配、数据权限
- 组织架构：部门管理、岗位管理
- 系统配置：数据字典、参数配置
- 多租户：租户管理、租户套餐
- 消息通信：短信发送、邮件发送、站内通知
- 日志审计：登录日志、操作日志
- 其他工具：验证码、IP 地区解析

## 模块结构

```
yudao-module-system/
├── pom.xml                              # 父级 POM，聚合 api 和 server 子模块
├── README.md                            # 本文档
├── yudao-module-system-api/             # API 接口定义模块（供其他模块 RPC 调用）
│   └── src/main/java/.../system/
│       ├── api/                         # Feign 客户端接口定义
│       │   ├── dept/                    # 部门、岗位 API
│       │   ├── dict/                    # 数据字典 API
│       │   ├── logger/                  # 日志 API（登录日志、操作日志）
│       │   ├── mail/                    # 邮件发送 API
│       │   ├── notify/                  # 站内通知 API
│       │   ├── permission/              # 权限 API（角色、权限检查）
│       │   ├── sms/                     # 短信 API（短信发送、验证码）
│       │   ├── social/                  # 社交登录 API
│       │   └── user/                    # 用户 API（用户信息查询、校验）
│       └── enums/                       # 枚举类（API 常量、错误码等）
└── yudao-module-system-server/          # 服务端实现模块
    └── src/main/java/.../system/
        ├── SystemServerApplication.java # 微服务启动类（仅微服务模式使用）
        ├── api/                         # RPC API 实现（实现 api 模块定义的接口）
        ├── controller/                  # REST API 控制器
        │   ├── admin/                   # 管理后台接口 (/admin-api/system/**)
        │   └── app/                     # 用户端接口 (/app-api/system/**)
        ├── service/                     # 业务逻辑层
        ├── dal/                         # 数据访问层
        │   ├── dataobject/              # 数据库实体对象 (DO)
        │   ├── mysql/                   # MyBatis Mapper 接口
        │   └── redis/                   # Redis DAO
        ├── convert/                     # MapStruct 对象转换器
        ├── framework/                   # 框架配置
        │   ├── captcha/                 # 验证码配置
        │   ├── datapermission/          # 数据权限配置
        │   ├── justauth/                # 第三方社交登录配置
        │   ├── operatelog/              # 操作日志配置
        │   ├── rpc/                     # RPC 配置
        │   ├── security/                # Spring Security 配置
        │   └── sms/                     # 短信配置
        ├── job/                         # 定时任务
        ├── mq/                          # 消息队列生产者/消费者
        └── util/                        # 工具类
```

## 功能模块详解

### 1. 用户与认证 (User & Auth)

#### 目录位置
- API 接口：`api/user/AdminUserApi.java`
- REST API：`controller/admin/user/UserController.java`
- 业务层：`service/user/AdminUserService.java`
- 数据层：`dal/dataobject/user/AdminUserDO.java`

#### 核心功能
- **用户管理**：用户 CRUD、用户导入导出、用户状态控制
- **用户认证**：登录、登出、Token 管理（见 OAuth2 模块）
- **个人中心**：个人资料修改、头像上传、密码修改
- **用户查询**：支持按部门、角色、状态等条件查询
- **用户校验**：校验用户是否存在、是否有效等（RPC 接口）

#### 关键类说明
- `AdminUserApi`：提供给其他微服务调用的 Feign 接口，查询用户信息
- `UserController`：管理后台的用户管理接口
- `AdminUserService`：用户业务逻辑，包括 CRUD、密码加密、头像处理等
- `AdminUserDO`：用户数据库实体，对应 `system_users` 表

### 2. OAuth2 认证 (OAuth2)

#### 目录位置
- API 接口：`api/oauth2/OAuth2TokenApi.java`
- REST API：`controller/admin/auth/AuthController.java`
- 业务层：`service/oauth2/OAuth2TokenService.java`
- 数据层：`dal/dataobject/oauth2/OAuth2AccessTokenDO.java`

#### 核心功能
- **Token 管理**：AccessToken 生成、刷新、撤销
- **登录认证**：账号密码登录、短信登录、社交登录
- **Token 校验**：校验 Token 有效性、获取用户信息
- **在线用户**：查询在线用户、强制下线

#### 关键类说明
- `OAuth2TokenApi`：提供 Token 校验、用户信息查询的 RPC 接口
- `AuthController`：登录、登出、刷新 Token 等认证接口
- `OAuth2TokenService`：Token 生成、校验、刷新逻辑
- `OAuth2AccessTokenDO`：访问令牌实体，存储在 Redis 中

### 3. 权限控制 (Permission)

#### 目录位置
- API 接口：`api/permission/PermissionApi.java`、`api/permission/RoleApi.java`
- REST API：`controller/admin/permission/`
  - `MenuController.java` - 菜单管理
  - `RoleController.java` - 角色管理
  - `PermissionController.java` - 权限分配
- 业务层：`service/permission/`
- 数据层：`dal/dataobject/permission/`

#### 核心功能
- **菜单管理**：菜单树形结构、菜单权限标识、路由配置
- **角色管理**：角色 CRUD、角色状态控制、角色类型（系统内置/自定义）
- **权限分配**：
  - 用户-角色分配（批量分配角色给用户）
  - 角色-菜单分配（配置角色可访问的菜单）
  - 角色-数据权限分配（配置角色可访问的数据范围）
- **权限校验**：校验用户是否有某个权限（RPC 接口）

#### 关键类说明
- `PermissionApi`：权限校验 RPC 接口，供网关和其他服务调用
- `RoleApi`：角色查询 RPC 接口
- `MenuService`：菜单管理业务逻辑，支持树形结构构建
- `RoleService`：角色管理业务逻辑
- `PermissionService`：权限分配业务逻辑，处理用户-角色-菜单的关联关系
- `MenuDO`：菜单实体，支持目录、菜单、按钮三种类型
- `RoleDO`：角色实体
- `UserRoleDO`：用户-角色关联表
- `RoleMenuDO`：角色-菜单关联表

### 4. 数据权限 (Data Permission)

#### 目录位置
- 配置：`framework/datapermission/`
- 业务层：集成在 `service/permission/PermissionService.java`

#### 核心功能
- **数据范围控制**：
  - 全部数据权限
  - 本部门数据权限
  - 本部门及以下数据权限
  - 仅本人数据权限
  - 自定义部门数据权限
- **规则配置**：通过注解和配置类实现数据权限过滤
- **自动过滤**：基于 MyBatis 拦截器自动添加数据权限 SQL 条件

#### 关键类说明
- `DataPermissionConfiguration`：数据权限配置类
- `DeptDataPermissionRule`：部门数据权限规则实现

### 5. 部门与岗位 (Dept & Post)

#### 目录位置
- API 接口：`api/dept/DeptApi.java`、`api/dept/PostApi.java`
- REST API：`controller/admin/dept/`
- 业务层：`service/dept/`
- 数据层：`dal/dataobject/dept/`

#### 核心功能
- **部门管理**：部门树形结构、部门 CRUD、部门状态控制
- **岗位管理**：岗位 CRUD、岗位状态控制、岗位排序
- **组织查询**：查询部门树、查询用户所属部门、查询部门下的用户

#### 关键类说明
- `DeptApi`/`PostApi`：部门/岗位查询 RPC 接口
- `DeptService`：部门业务逻辑，支持树形结构构建
- `PostService`：岗位业务逻辑
- `DeptDO`：部门实体，支持父子级联
- `PostDO`：岗位实体

### 6. 数据字典 (Dict)

#### 目录位置
- API 接口：`api/dict/DictDataApi.java`
- REST API：`controller/admin/dict/`
  - `DictTypeController.java` - 字典类型
  - `DictDataController.java` - 字典数据
- 业务层：`service/dict/`
- 数据层：`dal/dataobject/dict/`

#### 核心功能
- **字典类型**：字典类型 CRUD、类型编码唯一性校验
- **字典数据**：字典数据 CRUD、数据排序、数据状态控制
- **字典查询**：按类型查询字典列表（RPC 接口）
- **缓存机制**：字典数据缓存在 Redis，提高查询性能

#### 关键类说明
- `DictDataApi`：字典查询 RPC 接口
- `DictTypeService`：字典类型业务逻辑
- `DictDataService`：字典数据业务逻辑，包含缓存处理
- `DictTypeDO`：字典类型实体
- `DictDataDO`：字典数据实体

### 7. 租户管理 (Tenant)

#### 目录位置
- REST API：`controller/admin/tenant/`
- 业务层：`service/tenant/`
- 数据层：`dal/dataobject/tenant/`

#### 核心功能
- **租户管理**：租户 CRUD、租户套餐配置、租户状态控制
- **租户套餐**：套餐 CRUD、套餐菜单配置
- **租户隔离**：基于 MyBatis Plus 拦截器实现透明多租户
- **租户用户**：租户下的用户管理、租户管理员

#### 关键类说明
- `TenantService`：租户业务逻辑
- `TenantPackageService`：租户套餐业务逻辑
- `TenantDO`：租户实体
- `TenantPackageDO`：租户套餐实体

### 8. 短信服务 (SMS)

#### 目录位置
- API 接口：`api/sms/`
  - `SmsSendApi.java` - 短信发送接口
  - `SmsCodeApi.java` - 短信验证码接口
- REST API：`controller/admin/sms/`
- 业务层：`service/sms/`
- 数据层：`dal/dataobject/sms/`
- 配置：`framework/sms/`

#### 核心功能
- **短信发送**：支持阿里云、腾讯云等短信平台
- **短信模板**：模板 CRUD、模板参数配置、模板测试
- **短信渠道**：多渠道配置、渠道切换
- **短信验证码**：验证码生成、发送、校验
- **短信日志**：发送记录、发送状态跟踪
- **短信回调**：接收短信平台回调，更新发送状态

#### 关键类说明
- `SmsSendApi`：短信发送 RPC 接口
- `SmsCodeApi`：短信验证码 RPC 接口
- `SmsSendService`：短信发送业务逻辑
- `SmsCodeService`：短信验证码业务逻辑
- `SmsTemplateService`：短信模板管理
- `SmsChannelService`：短信渠道管理

### 9. 邮件服务 (Mail)

#### 目录位置
- API 接口：`api/mail/MailSendApi.java`
- REST API：`controller/admin/mail/`
- 业务层：`service/mail/`
- 数据层：`dal/dataobject/mail/`

#### 核心功能
- **邮件发送**：支持文本邮件、HTML 邮件、附件邮件
- **邮件模板**：模板 CRUD、模板参数替换
- **邮件账号**：SMTP 账号配置、多账号支持
- **邮件日志**：发送记录、发送状态跟踪

#### 关键类说明
- `MailSendApi`：邮件发送 RPC 接口
- `MailSendService`：邮件发送业务逻辑
- `MailTemplateService`：邮件模板管理
- `MailAccountService`：邮件账号管理

### 10. 站内通知 (Notify)

#### 目录位置
- API 接口：`api/notify/NotifyMessageSendApi.java`
- REST API：`controller/admin/notify/`
- 业务层：`service/notify/`
- 数据层：`dal/dataobject/notify/`

#### 核心功能
- **消息发送**：站内信发送、消息模板
- **消息管理**：消息 CRUD、消息状态（已读/未读）
- **消息查询**：用户消息列表、未读消息数量
- **消息模板**：模板 CRUD、模板参数配置

#### 关键类说明
- `NotifyMessageSendApi`：消息发送 RPC 接口
- `NotifyMessageService`：消息管理业务逻辑
- `NotifyTemplateService`：消息模板管理

### 11. 社交登录 (Social)

#### 目录位置
- API 接口：`api/social/`
- REST API：`controller/admin/socail/` (注意：目录名拼写为 socail)
- 业务层：`service/social/`
- 数据层：`dal/dataobject/social/`
- 配置：`framework/justauth/`

#### 核心功能
- **社交登录**：支持微信、钉钉、飞书等第三方登录
- **社交绑定**：用户绑定/解绑社交账号
- **多应用支持**：同一社交平台支持多个应用配置
- **基于 JustAuth**：集成 JustAuth 第三方登录库

#### 关键类说明
- `SocialUserApi`：社交用户 RPC 接口
- `SocialClientApi`：社交应用配置 RPC 接口
- `SocialUserService`：社交用户业务逻辑
- `SocialClientService`：社交应用配置管理

### 12. 日志审计 (Logger)

#### 目录位置
- API 接口：`api/logger/`
  - `LoginLogApi.java` - 登录日志
  - `OperateLogApi.java` - 操作日志
- REST API：`controller/admin/logger/`
- 业务层：`service/logger/`
- 数据层：`dal/dataobject/logger/`
- 配置：`framework/operatelog/`

#### 核心功能
- **登录日志**：记录用户登录、登出、登录失败等操作
- **操作日志**：记录用户的增删改操作，支持自动记录和手动记录
- **日志查询**：支持按用户、时间、操作类型等条件查询
- **日志导出**：支持导出日志数据

#### 关键类说明
- `LoginLogApi`/`OperateLogApi`：日志记录 RPC 接口
- `LoginLogService`：登录日志业务逻辑
- `OperateLogService`：操作日志业务逻辑
- `LoginLogDO`：登录日志实体
- `OperateLogDO`：操作日志实体

### 13. 验证码 (Captcha)

#### 目录位置
- REST API：`controller/admin/captcha/`
- 配置：`framework/captcha/`

#### 核心功能
- **图形验证码**：滑块拼图、文字点选等类型
- **验证码校验**：验证码生成、校验
- **Redis 缓存**：验证码数据缓存在 Redis

#### 关键类说明
- `CaptchaController`：验证码接口
- `CaptchaConfiguration`：验证码配置类

### 14. 通知公告 (Notice)

#### 目录位置
- REST API：`controller/admin/notice/`
- 业务层：`service/notice/`
- 数据层：`dal/dataobject/notice/`

#### 核心功能
- **公告管理**：公告 CRUD、公告状态控制
- **公告发布**：公告发布、撤回
- **公告查询**：支持按状态、时间等条件查询

#### 关键类说明
- `NoticeService`：公告业务逻辑
- `NoticeDO`：公告实体

### 15. IP 与地区 (IP & Area)

#### 目录位置
- REST API：`controller/admin/ip/AreaController.java`
- 业务层：`service/ip/`
- 数据层：`dal/dataobject/ip/`

#### 核心功能
- **IP 解析**：解析 IP 地址对应的地区信息
- **地区管理**：地区数据 CRUD、地区树形结构
- **地区查询**：查询省市区三级联动数据

#### 关键类说明
- `AreaService`：地区业务逻辑
- `AreaDO`：地区实体

## 架构设计

### 三层架构

System 模块严格遵循三层架构模式：

```
┌─────────────────────────────────────────────────────────┐
│                    Controller 层                         │
│  - 处理 HTTP 请求                                        │
│  - 参数校验（@Validated）                               │
│  - 调用 Service 层                                       │
│  - 返回统一响应格式 CommonResult                         │
└─────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│                     Service 层                           │
│  - 业务逻辑处理                                          │
│  - 事务控制（@Transactional）                           │
│  - 权限校验（@PreAuthorize）                            │
│  - 调用 DAL 层和其他 Service                             │
└─────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│                      DAL 层                              │
│  - MyBatis Mapper：数据库操作（mysql/）                 │
│  - Redis DAO：缓存操作（redis/）                         │
│  - DataObject (DO)：数据库实体（dataobject/）           │
└─────────────────────────────────────────────────────────┘
```

### 对象转换策略

使用 MapStruct 进行对象转换，避免手动赋值：

```
DO (DataObject)           ←→  BO (Business Object - 可选)
       ↓                             ↓
VO (View Object)                Service 层处理
  ├─ ReqVO (请求)           ←→  DTO (Data Transfer Object)
  └─ RespVO (响应)              ↓
                          RPC 接口返回
```

**对象说明**：
- **DO (DataObject)**：数据库实体对象，与数据库表一一对应
- **VO (View Object)**：视图对象，用于 Controller 层的请求/响应
  - ReqVO：请求参数对象，包含校验注解
  - RespVO：响应结果对象
- **DTO (Data Transfer Object)**：数据传输对象，用于 RPC 接口
- **BO (Business Object)**：业务对象，用于 Service 层复杂业务逻辑（可选）

**转换器位置**：`convert/` 包下，使用 MapStruct 自动生成转换代码

### 缓存设计

System 模块大量使用 Redis 缓存来提高性能：

#### 缓存策略
1. **字典数据**：缓存所有字典数据，按类型分组
2. **用户权限**：缓存用户的角色、权限列表
3. **菜单树**：缓存菜单树形结构
4. **部门树**：缓存部门树形结构
5. **OAuth2 Token**：所有 Token 存储在 Redis
6. **验证码**：验证码数据存储在 Redis
7. **短信验证码**：验证码及发送记录缓存

#### 缓存更新
- **主动更新**：修改、删除数据时主动清除相关缓存
- **定时刷新**：部分数据通过定时任务刷新缓存
- **延迟双删**：关键数据采用延迟双删策略保证一致性

#### 缓存键设计
缓存键统一使用常量定义，位于 `dal/redis/` 包下，格式：
```
{模块}:{业务}:{标识}
例如：system:dict:type:user_type
```

### 权限控制设计

#### 菜单权限
基于 Spring Security + 注解实现：

```java
@PreAuthorize("@ss.hasPermission('system:user:create')")
public void createUser(UserSaveReqVO reqVO) {
    // 业务逻辑
}
```

权限标识格式：`{模块}:{业务}:{操作}`

#### 数据权限
基于 MyBatis 拦截器实现，自动添加 SQL 过滤条件：

```java
@DataPermission(
    deptAlias = "u", // 表别名
    deptColumnName = "dept_id" // 部门字段
)
public List<AdminUserDO> getUserList(UserPageReqVO reqVO) {
    // 会自动添加部门数据权限过滤
}
```

支持的数据权限范围：
- 全部数据
- 本部门数据
- 本部门及以下数据
- 仅本人数据
- 自定义部门数据

### 多租户设计

#### 租户隔离
基于 MyBatis Plus 多租户插件实现透明化多租户：

```java
@TableName(value = "system_users", autoResultMap = true)
public class AdminUserDO extends BaseDO {
    @TableField("tenant_id")
    private Long tenantId; // 租户 ID，自动填充和过滤
}
```

#### 租户配置
- **忽略租户的表**：配置在 `yudao.tenant.ignore-tables`
- **忽略租户的缓存**：使用 `@TenantIgnore` 注解

#### 租户获取
从当前登录用户的上下文中获取租户 ID（LoginUser）

### RPC 接口设计

System 模块作为基础服务，提供大量 RPC 接口供其他模块调用：

#### 接口定义（api 模块）
```java
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 管理员用户")
public interface AdminUserApi {
    String PREFIX = ApiConstants.PREFIX + "/user";

    @GetMapping(PREFIX + "/get")
    CommonResult<AdminUserRespDTO> getUser(@RequestParam("id") Long id);
}
```

#### 接口实现（server 模块）
```java
@RestController
@Validated
public class AdminUserApiImpl implements AdminUserApi {
    @Resource
    private AdminUserService userService;

    @Override
    public CommonResult<AdminUserRespDTO> getUser(Long id) {
        AdminUserDO user = userService.getUser(id);
        return success(AdminUserConvert.INSTANCE.convert(user));
    }
}
```

#### 接口调用（其他模块）
```java
@Service
public class SomeService {
    @Resource
    private AdminUserApi adminUserApi;

    public void doSomething(Long userId) {
        CommonResult<AdminUserRespDTO> result = adminUserApi.getUser(userId);
        AdminUserRespDTO user = result.getData();
        // 业务逻辑
    }
}
```

### 消息队列设计

位于 `mq/` 包下，用于异步处理和模块间解耦：

#### 生产者
```java
@Component
public class SmsProducer {
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    public void sendSmsMessage(SmsMessage message) {
        rocketMQTemplate.asyncSend("sms-send-topic", message, ...);
    }
}
```

#### 消费者
```java
@Component
@RocketMQMessageListener(topic = "sms-send-topic", consumerGroup = "sms-consumer")
public class SmsConsumer implements RocketMQListener<SmsMessage> {
    @Override
    public void onMessage(SmsMessage message) {
        // 处理短信发送
    }
}
```

### 定时任务设计

位于 `job/` 包下，使用 Spring @Scheduled 或 XXL-Job：

```java
@Component
public class TokenCleanJob {
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨 2 点执行
    public void execute() {
        // 清理过期 Token
    }
}
```

## 配置说明

### 数据库配置

System 模块涉及的主要数据表：

| 表名 | 说明 |
|------|------|
| system_users | 用户表 |
| system_role | 角色表 |
| system_menu | 菜单表 |
| system_user_role | 用户-角色关联表 |
| system_role_menu | 角色-菜单关联表 |
| system_dept | 部门表 |
| system_post | 岗位表 |
| system_dict_type | 字典类型表 |
| system_dict_data | 字典数据表 |
| system_tenant | 租户表 |
| system_tenant_package | 租户套餐表 |
| system_oauth2_access_token | OAuth2 访问令牌表 |
| system_oauth2_refresh_token | OAuth2 刷新令牌表 |
| system_login_log | 登录日志表 |
| system_operate_log | 操作日志表 |
| system_sms_template | 短信模板表 |
| system_sms_channel | 短信渠道表 |
| system_sms_log | 短信日志表 |
| system_sms_code | 短信验证码表 |
| system_mail_account | 邮件账号表 |
| system_mail_template | 邮件模板表 |
| system_mail_log | 邮件日志表 |
| system_notify_template | 站内信模板表 |
| system_notify_message | 站内信消息表 |
| system_social_user | 社交用户表 |
| system_social_client | 社交应用配置表 |
| system_notice | 通知公告表 |

### Redis 配置

System 模块使用的 Redis 键前缀：

```yaml
# 用户相关
system:user:*
system:permission:user:*

# 权限相关
system:permission:menu:*
system:permission:role:*

# OAuth2 相关
system:oauth2:access_token:*
system:oauth2:refresh_token:*

# 字典相关
system:dict:type:*
system:dict:data:*

# 验证码相关
captcha:*
system:sms:code:*

# 其他缓存
system:dept:*
system:post:*
```

### 应用配置

主要配置项（application.yaml）：

```yaml
yudao:
  # 多租户配置
  tenant:
    enable: true
    ignore-tables:
      - system_tenant
      - system_tenant_package
      - system_dict_type
      - system_dict_data

  # 验证码配置
  captcha:
    enable: true
    type: SLIDER # SLIDER-滑块 / CLICK-文字点选

  # XSS 防护
  xss:
    enable: false
    exclude-urls: # 排除的 URL

  # API 加密
  api-encrypt:
    enable: false
```

## 开发指南

### 添加新的 RPC 接口

1. **在 api 模块定义接口**：
```java
// yudao-module-system-api/src/.../api/xxx/XxxApi.java
@FeignClient(name = ApiConstants.NAME)
public interface XxxApi {
    String PREFIX = ApiConstants.PREFIX + "/xxx";

    @GetMapping(PREFIX + "/get")
    CommonResult<XxxRespDTO> getXxx(@RequestParam("id") Long id);
}
```

2. **定义 DTO 对象**：
```java
// yudao-module-system-api/src/.../api/xxx/dto/XxxRespDTO.java
@Data
public class XxxRespDTO {
    private Long id;
    private String name;
}
```

3. **在 server 模块实现接口**：
```java
// yudao-module-system-server/src/.../api/xxx/XxxApiImpl.java
@RestController
@Validated
public class XxxApiImpl implements XxxApi {
    @Resource
    private XxxService xxxService;

    @Override
    public CommonResult<XxxRespDTO> getXxx(Long id) {
        // 业务逻辑
    }
}
```

### 添加新的业务功能

1. **定义 DO**：
```java
// dal/dataobject/xxx/XxxDO.java
@TableName("system_xxx")
@Data
@EqualsAndHashCode(callSuper = true)
public class XxxDO extends BaseDO {
    @TableId
    private Long id;
    private String name;
}
```

2. **定义 Mapper**：
```java
// dal/mysql/xxx/XxxMapper.java
@Mapper
public interface XxxMapper extends BaseMapperX<XxxDO> {
    default PageResult<XxxDO> selectPage(XxxPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<XxxDO>()
            .likeIfPresent(XxxDO::getName, reqVO.getName())
            .orderByDesc(XxxDO::getId));
    }
}
```

3. **定义 VO**：
```java
// controller/admin/xxx/vo/XxxRespVO.java
@Data
public class XxxRespVO {
    private Long id;
    private String name;
}

// controller/admin/xxx/vo/XxxSaveReqVO.java
@Data
public class XxxSaveReqVO {
    @NotBlank(message = "名称不能为空")
    private String name;
}
```

4. **定义 Convert**：
```java
// convert/xxx/XxxConvert.java
@Mapper
public interface XxxConvert {
    XxxConvert INSTANCE = Mappers.getMapper(XxxConvert.class);

    XxxRespVO convert(XxxDO bean);
    XxxDO convert(XxxSaveReqVO bean);
}
```

5. **定义 Service**：
```java
// service/xxx/XxxService.java
public interface XxxService {
    Long createXxx(XxxSaveReqVO createReqVO);
    void updateXxx(XxxSaveReqVO updateReqVO);
    void deleteXxx(Long id);
    XxxDO getXxx(Long id);
    PageResult<XxxDO> getXxxPage(XxxPageReqVO pageReqVO);
}

// service/xxx/XxxServiceImpl.java
@Service
@Validated
public class XxxServiceImpl implements XxxService {
    @Resource
    private XxxMapper xxxMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createXxx(XxxSaveReqVO createReqVO) {
        // 业务逻辑
        XxxDO xxx = XxxConvert.INSTANCE.convert(createReqVO);
        xxxMapper.insert(xxx);
        return xxx.getId();
    }
}
```

6. **定义 Controller**：
```java
// controller/admin/xxx/XxxController.java
@RestController
@RequestMapping("/system/xxx")
@Tag(name = "管理后台 - XXX")
@Validated
public class XxxController {
    @Resource
    private XxxService xxxService;

    @PostMapping("/create")
    @Operation(summary = "创建 XXX")
    @PreAuthorize("@ss.hasPermission('system:xxx:create')")
    public CommonResult<Long> createXxx(@Valid @RequestBody XxxSaveReqVO createReqVO) {
        return success(xxxService.createXxx(createReqVO));
    }
}
```

### 使用数据权限

在 Service 方法上添加 `@DataPermission` 注解：

```java
@DataPermission(
    deptAlias = "u", // 表别名（如果有 JOIN，需要指定）
    deptColumnName = "dept_id" // 部门字段名
)
public List<AdminUserDO> getUserList(UserPageReqVO reqVO) {
    return userMapper.selectList(reqVO);
}
```

忽略数据权限：

```java
@DataPermission(enable = false)
public List<AdminUserDO> getAllUsers() {
    return userMapper.selectList();
}
```

### 使用操作日志

在 Controller 方法上添加 `@OperateLog` 注解：

```java
@PostMapping("/create")
@OperateLog(type = OPERATE_LOG_CREATE, module = "用户", name = "创建用户")
public CommonResult<Long> createUser(@Valid @RequestBody UserSaveReqVO createReqVO) {
    return success(userService.createUser(createReqVO));
}
```

## 注意事项

### 安全相关

1. **密码加密**：用户密码使用 BCrypt 加密存储，不可逆
2. **Token 安全**：Token 存储在 Redis，支持强制下线
3. **权限校验**：关键操作必须添加 `@PreAuthorize` 注解
4. **SQL 注入**：使用 MyBatis Plus 参数化查询，避免 SQL 注入
5. **XSS 防护**：可选开启 XSS 过滤器

### 性能相关

1. **缓存使用**：查询频繁的数据（字典、权限等）使用 Redis 缓存
2. **分页查询**：列表查询必须分页，避免一次性查询大量数据
3. **索引优化**：重要查询字段添加数据库索引
4. **异步处理**：耗时操作（短信、邮件发送）使用消息队列异步处理

### 数据一致性

1. **事务控制**：涉及多表操作的方法添加 `@Transactional` 注解
2. **缓存一致性**：修改数据时必须同步清除相关缓存
3. **乐观锁**：并发更新使用版本号或时间戳乐观锁

### 多租户相关

1. **租户隔离**：新增表默认包含 `tenant_id` 字段
2. **忽略租户**：系统级别的表（如租户表本身）需要配置忽略租户
3. **租户上下文**：获取当前租户 ID 使用 `TenantContextHolder.getTenantId()`

## 常见问题

### Q1: 如何新增一个菜单权限？

在系统管理 > 菜单管理中新增菜单，设置权限标识（如 `system:user:create`），然后在代码中使用：

```java
@PreAuthorize("@ss.hasPermission('system:user:create')")
```

### Q2: 如何在其他模块调用 System 模块的接口？

1. 添加 `yudao-module-system-api` 依赖
2. 注入对应的 API 接口（如 `AdminUserApi`）
3. 直接调用方法

### Q3: 如何自定义数据权限规则？

继承 `DeptDataPermissionRule` 并重写过滤逻辑，然后注册为 Spring Bean。

### Q4: 短信/邮件发送失败如何排查？

1. 查看 `system_sms_log` 或 `system_mail_log` 表的错误信息
2. 检查渠道配置是否正确（AccessKey、SecretKey 等）
3. 查看应用日志中的异常堆栈

### Q5: 如何实现单点登录？

System 模块的 OAuth2 Token 存储在 Redis，天然支持分布式单点登录。只需保证多个应用实例共享同一个 Redis。

## 相关文档

- [芋道官方文档](https://doc.iocoder.cn)
- [Spring Cloud Alibaba 文档](https://spring-cloud-alibaba-group.github.io/github-pages/2023/zh-cn/index.html)
- [MyBatis Plus 文档](https://baomidou.com/)
- [Flowable 文档](https://www.flowable.com/open-source/docs/)

## 联系方式

- 项目地址：https://gitee.com/zhijiantianya/ruoyi-vue-pro
- 问题反馈：https://gitee.com/zhijiantianya/ruoyi-vue-pro/issues
- 官方文档：https://doc.iocoder.cn
