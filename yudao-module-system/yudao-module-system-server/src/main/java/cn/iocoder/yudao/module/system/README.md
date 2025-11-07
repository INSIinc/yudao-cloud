# System 模块开发文档

## 📋 目录

- [模块概述](#模块概述)
- [项目结构](#项目结构)
- [核心功能](#核心功能)
- [架构设计](#架构设计)
- [开发指南](#开发指南)
- [数据库设计](#数据库设计)
- [API 接口](#api-接口)

---

## 模块概述

### 定位

System 模块是 yudao-cloud 的**核心基础模块**，提供通用业务功能，支撑上层业务系统。

### 核心职责

- **用户管理**：后台管理员的用户账号管理
- **组织架构**：部门和岗位的层级管理
- **权限控制**：基于 RBAC 的角色权限体系
- **数据字典**：系统配置化的枚举值管理
- **OAuth2 认证**：标准 OAuth2.0 协议实现
- **通知服务**：短信、邮件、站内信发送
- **日志管理**：操作日志和登录日志记录
- **多租户**：SaaS 多租户数据隔离
- **社交登录**：微信、QQ、支付宝等第三方登录

### 技术栈

- **Spring Boot 3.2+**：核心框架
- **Spring Security**：安全框架
- **MyBatis Plus 3.5.7**：ORM 框架
- **Redis + Redisson**：缓存和分布式锁
- **Flowable**：工作流引擎（部分功能）
- **JustAuth**：社交登录集成
- **XXL-Job**：分布式定时任务

### 统计数据

- **Java 文件数**：357 个
- **目录数**：166 个
- **API 实现**：35 个 Controller
- **业务服务**：34 个 Service
- **数据实体**：32 个 DO
- **数据库表**：32 张

---

## 项目结构

```
system/
├── SystemServerApplication.java      # 应用启动类
├── package-info.java                 # 模块说明文档
│
├── api/                              # RPC API 实现层（供其他模块调用）
├── controller/                       # RESTful API 控制层
├── convert/                          # 对象转换层（MapStruct）
├── dal/                              # 数据访问层（DO + Mapper + Redis）
├── framework/                        # 框架配置和扩展
├── job/                              # 定时任务
├── mq/                               # 消息队列
├── service/                          # 业务逻辑层
└── util/                             # 工具类
```

### 目录职能详解

#### 1. `api/` - RPC API 实现层

为其他模块提供 Feign 接口实现，支持跨模块调用。

```
api/
├── dept/                             # 部门岗位 API
│   ├── DeptApiImpl.java             # 部门查询、部门树构建
│   └── PostApiImpl.java             # 岗位查询、岗位验证
├── dict/
│   └── DictDataApiImpl.java         # 字典数据查询、字典值校验
├── logger/
│   ├── LoginLogApiImpl.java         # 创建登录日志
│   └── OperateLogApiImpl.java       # 创建操作日志
├── mail/
│   └── MailSendApiImpl.java         # 发送邮件（单个/批量）
├── notify/
│   └── NotifyMessageSendApiImpl.java # 发送站内信
├── oauth2/
│   └── OAuth2TokenApiImpl.java      # Token 验证、用户信息获取
├── permission/
│   ├── PermissionApiImpl.java       # 权限校验、菜单查询
│   └── RoleApiImpl.java             # 角色查询、角色验证
├── sms/
│   ├── SmsCodeApiImpl.java          # 验证码发送、验证
│   └── SmsSendApiImpl.java          # 发送短信
├── social/
│   ├── SocialClientApiImpl.java     # 社交客户端查询
│   └── SocialUserApiImpl.java       # 社交用户绑定
├── tenant/
│   └── TenantApiImpl.java           # 租户查询、租户验证
└── user/
    └── AdminUserApiImpl.java        # 用户查询、用户验证、密码校验
```

**关键实现**：
- 使用 `@RestController` 标注实现类（Feign 接口在 `-api` 模块定义）
- 注入对应的 Service 层实现业务逻辑
- 统一返回 `CommonResult` 封装结果

**示例**：
```java
@RestController
@Validated
public class AdminUserApiImpl implements AdminUserApi {
    @Resource
    private AdminUserService userService;

    @Override
    public CommonResult<AdminUserRespDTO> getUser(Long id) {
        AdminUserDO user = userService.getUser(id);
        return success(BeanUtils.toBean(user, AdminUserRespDTO.class));
    }
}
```

---

#### 2. `controller/` - RESTful API 控制层

处理 HTTP 请求，参数校验，调用 Service 层。

```
controller/
├── admin/                            # 管理后台接口（/admin-api/system/**）
│   ├── auth/                         # 认证授权
│   │   ├── AuthController.java      # 登录、登出、刷新Token、获取用户权限
│   │   └── vo/                      # 请求/响应 VO
│   ├── captcha/
│   │   └── CaptchaController.java   # 验证码生成、校验
│   ├── dept/
│   │   ├── DeptController.java      # 部门 CRUD、部门树查询
│   │   ├── PostController.java      # 岗位 CRUD、岗位列表
│   │   └── vo/
│   ├── dict/
│   │   ├── DictDataController.java  # 字典数据 CRUD
│   │   ├── DictTypeController.java  # 字典类型 CRUD
│   │   └── vo/
│   ├── logger/
│   │   ├── LoginLogController.java  # 登录日志查询、导出
│   │   ├── OperateLogController.java # 操作日志查询、导出
│   │   └── vo/
│   ├── mail/
│   │   ├── MailAccountController.java # 邮件账号 CRUD
│   │   ├── MailLogController.java    # 邮件日志查询
│   │   ├── MailTemplateController.java # 邮件模板 CRUD、发送测试
│   │   └── vo/
│   ├── oauth2/
│   │   ├── OAuth2ClientController.java # OAuth2 客户端 CRUD
│   │   ├── OAuth2TokenController.java  # Token 查询、删除
│   │   ├── OAuth2OpenController.java   # OAuth2 授权接口（/authorize, /token）
│   │   └── vo/
│   ├── permission/
│   │   ├── MenuController.java      # 菜单 CRUD、菜单树查询
│   │   ├── RoleController.java      # 角色 CRUD、角色列表
│   │   ├── PermissionController.java # 权限分配（角色菜单、用户角色）
│   │   └── vo/
│   ├── sms/
│   │   ├── SmsChannelController.java # 短信渠道 CRUD
│   │   ├── SmsTemplateController.java # 短信模板 CRUD、发送测试
│   │   ├── SmsLogController.java     # 短信日志查询
│   │   ├── SmsCallbackController.java # 短信回调接收
│   │   └── vo/
│   ├── social/ (注意：目录名拼写为 socail)
│   │   ├── SocialClientController.java # 社交客户端 CRUD
│   │   ├── SocialUserController.java   # 社交用户管理、绑定
│   │   └── vo/
│   ├── tenant/
│   │   ├── TenantController.java       # 租户 CRUD
│   │   ├── TenantPackageController.java # 租户套餐 CRUD
│   │   └── vo/
│   ├── user/
│   │   ├── UserController.java         # 用户 CRUD、导入导出
│   │   ├── UserProfileController.java  # 个人资料、修改密码、头像上传
│   │   └── vo/
│   ├── notice/
│   │   ├── NoticeController.java       # 通知公告 CRUD
│   │   └── vo/
│   ├── notify/
│   │   ├── NotifyTemplateController.java # 站内信模板 CRUD
│   │   ├── NotifyMessageController.java  # 站内信消息查询、标记已读
│   │   └── vo/
│   └── ip/
│       ├── AreaController.java         # 地区管理（省市区）
│       └── vo/
│
└── app/                              # 用户 APP 接口（/app-api/system/**）
    ├── dict/
    │   └── AppDictDataController.java # 字典查询
    ├── ip/
    │   └── AppAreaController.java     # 地区查询
    └── tenant/
        └── AppTenantController.java   # 租户信息查询
```

**关键设计**：
- 使用 `@PreAuthorize` 注解进行权限控制
- 使用 `@ApiAccessLog` 注解记录操作日志
- 使用 `@Valid` 进行参数校验
- 统一返回 `CommonResult` 封装

**示例**：
```java
@PostMapping("/create")
@PreAuthorize("@ss.hasPermission('system:user:create')")
@ApiAccessLog(operateType = CREATE)
public CommonResult<Long> createUser(@Valid @RequestBody UserSaveReqVO reqVO) {
    Long userId = userService.createUser(reqVO);
    return success(userId);
}
```

---

#### 3. `convert/` - 对象转换层

使用 MapStruct 进行对象转换，避免手动 setter/getter。

```
convert/
├── auth/
│   └── AuthConvert.java             # VO ↔ DO ↔ DTO 转换
├── oauth2/
│   └── OAuth2OpenConvert.java
├── tenant/
│   └── TenantConvert.java
└── user/
    └── UserConvert.java
```

**关键设计**：
- 使用 `@Mapper(componentModel = "spring")` 自动注入 Spring 容器
- 使用单例模式 `INSTANCE`
- 支持集合转换 `List<A> → List<B>`
- 支持分页对象转换

**示例**：
```java
@Mapper
public interface UserConvert {
    UserConvert INSTANCE = Mappers.getMapper(UserConvert.class);

    AdminUserDO convert(UserSaveReqVO bean);

    UserRespVO convert(AdminUserDO bean);

    List<UserRespVO> convertList(List<AdminUserDO> list);

    PageResult<UserRespVO> convertPage(PageResult<AdminUserDO> page);
}
```

---

#### 4. `dal/` - 数据访问层

包含数据库实体、Mapper 接口、Redis DAO。

##### 4.1 `dataobject/` - 数据库实体对象（DO）

```
dataobject/
├── dept/
│   ├── DeptDO.java                  # 部门实体（树形结构）
│   ├── PostDO.java                  # 岗位实体
│   └── UserPostDO.java              # 用户岗位关联表
├── dict/
│   ├── DictTypeDO.java              # 字典类型
│   └── DictDataDO.java              # 字典数据
├── logger/
│   ├── LoginLogDO.java              # 登录日志
│   └── OperateLogDO.java            # 操作日志
├── mail/
│   ├── MailAccountDO.java           # 邮件账号（SMTP 配置）
│   ├── MailTemplateDO.java          # 邮件模板
│   └── MailLogDO.java               # 邮件发送日志
├── notice/
│   └── NoticeDO.java                # 通知公告
├── notify/
│   ├── NotifyTemplateDO.java        # 站内信模板
│   └── NotifyMessageDO.java         # 站内信消息
├── oauth2/
│   ├── OAuth2ClientDO.java          # OAuth2 客户端配置
│   ├── OAuth2AccessTokenDO.java     # 访问令牌
│   ├── OAuth2RefreshTokenDO.java    # 刷新令牌
│   ├── OAuth2CodeDO.java            # 授权码
│   └── OAuth2ApproveDO.java         # 用户授权记录
├── permission/
│   ├── MenuDO.java                  # 菜单（树形结构）
│   ├── RoleDO.java                  # 角色
│   ├── RoleMenuDO.java              # 角色菜单关联
│   └── UserRoleDO.java              # 用户角色关联
├── sms/
│   ├── SmsChannelDO.java            # 短信渠道
│   ├── SmsTemplateDO.java           # 短信模板
│   ├── SmsCodeDO.java               # 短信验证码
│   └── SmsLogDO.java                # 短信发送日志
├── social/
│   ├── SocialClientDO.java          # 社交登录客户端
│   ├── SocialUserDO.java            # 第三方用户信息
│   └── SocialUserBindDO.java        # 用户绑定关系
├── tenant/
│   ├── TenantDO.java                # 租户
│   └── TenantPackageDO.java         # 租户套餐
└── user/
    └── AdminUserDO.java             # 管理员用户
```

**关键设计**：
- 继承 `BaseDO`（包含 creator、createTime、updater、updateTime、deleted）
- 继承 `TenantBaseDO`（额外包含 tenantId，支持多租户）
- 使用 `@TableName` 指定表名
- 使用 `@TableId` 指定主键策略
- 使用 `@TableField` 配置字段映射

**示例**：
```java
@TableName("system_users")
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserDO extends TenantBaseDO {

    @TableId
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private Long deptId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Set<Long> postIds;

    private String email;

    private String mobile;

    private Integer sex;

    private String avatar;

    private Integer status;

    private String loginIp;

    private LocalDateTime loginDate;
}
```

##### 4.2 `mysql/` - MyBatis Mapper 接口

```
mysql/
├── dept/
│   ├── DeptMapper.java              # 部门 Mapper
│   ├── PostMapper.java              # 岗位 Mapper
│   └── UserPostMapper.java          # 用户岗位 Mapper
├── dict/
│   ├── DictTypeMapper.java
│   └── DictDataMapper.java
├── logger/
│   ├── LoginLogMapper.java
│   └── OperateLogMapper.java
├── mail/
│   ├── MailAccountMapper.java
│   ├── MailTemplateMapper.java
│   └── MailLogMapper.java
├── oauth2/
│   ├── OAuth2ClientMapper.java
│   ├── OAuth2AccessTokenMapper.java
│   ├── OAuth2RefreshTokenMapper.java
│   ├── OAuth2CodeMapper.java
│   └── OAuth2ApproveMapper.java
├── permission/
│   ├── MenuMapper.java
│   ├── RoleMapper.java
│   ├── RoleMenuMapper.java
│   └── UserRoleMapper.java
├── sms/
│   ├── SmsChannelMapper.java
│   ├── SmsTemplateMapper.java
│   ├── SmsCodeMapper.java
│   └── SmsLogMapper.java
├── social/
│   ├── SocialClientMapper.java
│   ├── SocialUserMapper.java
│   └── SocialUserBindMapper.java
├── tenant/
│   ├── TenantMapper.java
│   └── TenantPackageMapper.java
└── user/
    └── AdminUserMapper.java
```

**关键设计**：
- 继承 `BaseMapperX`（封装了 MyBatis Plus 的 BaseMapper + 分页查询）
- 使用 `@Mapper` 注解
- 使用 `LambdaQueryWrapperX` 构建复杂查询条件

**示例**：
```java
@Mapper
public interface AdminUserMapper extends BaseMapperX<AdminUserDO> {

    default PageResult<AdminUserDO> selectPage(UserPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AdminUserDO>()
                .likeIfPresent(AdminUserDO::getUsername, reqVO.getUsername())
                .likeIfPresent(AdminUserDO::getMobile, reqVO.getMobile())
                .eqIfPresent(AdminUserDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(AdminUserDO::getCreateTime, reqVO.getCreateTime())
                .eqIfPresent(AdminUserDO::getDeptId, reqVO.getDeptId())
                .orderByDesc(AdminUserDO::getId));
    }
}
```

##### 4.3 `redis/` - Redis DAO

```
redis/
├── RedisKeyConstants.java           # Redis Key 常量定义
└── oauth2/
    └── OAuth2AccessTokenRedisDAO.java # OAuth2 Token 缓存
```

**关键设计**：
- 使用 `StringRedisTemplate` 操作 Redis
- 统一 Key 前缀管理（在 `RedisKeyConstants` 中定义）
- 使用 Redisson 实现分布式锁

**Redis Key 设计示例**：
```java
public interface RedisKeyConstants {

    // 用户 Session
    String USER_SESSION = "user_session:%s";

    // OAuth2 访问令牌
    String OAUTH2_ACCESS_TOKEN = "oauth2_access_token:%s";

    // 角色
    String ROLE = "role:%s";

    // 用户角色ID列表
    String USER_ROLE_IDS = "user_role_ids:%s";

    // 部门子部门ID列表
    String DEPT_CHILDREN_IDS = "dept_children_ids:%s";

    // 短信验证码
    String SMS_CODE = "sms_code:%s:%s";
}
```

---

#### 5. `framework/` - 框架配置和扩展

框架层的配置和自定义实现。

```
framework/
├── captcha/                          # 验证码框架集成
│   ├── config/
│   │   └── YudaoCaptchaConfiguration.java # 验证码配置类
│   └── core/
│       ├── RedisCaptchaServiceImpl.java  # Redis 存储验证码
│       └── PictureWordCaptchaServiceImpl.java # 滑块拼图验证码
├── datapermission/                   # 数据权限配置
│   └── config/
│       └── DataPermissionConfiguration.java # 数据权限规则配置
├── justauth/                         # JustAuth 社交登录集成
│   ├── config/
│   │   └── YudaoJustAuthConfiguration.java
│   └── core/
│       └── AuthRequestFactory.java   # 创建第三方登录请求
├── operatelog/                       # 操作日志配置
│   └── core/
│       └── (日志切面实现)
├── rpc/                              # RPC 配置
│   └── config/
│       └── (Feign 配置)
├── security/                         # Spring Security 配置
│   ├── config/
│   │   └── SecurityConfiguration.java # 安全配置（白名单、认证管理）
│   └── core/
└── sms/                              # 短信服务框架
    ├── config/
    │   ├── SmsConfiguration.java     # 短信配置
    │   └── SmsCodeProperties.java    # 验证码配置
    └── core/
        ├── client/
        │   ├── SmsClient.java        # 短信客户端接口
        │   ├── SmsClientFactory.java # 短信客户端工厂
        │   └── impl/
        │       ├── AbstractSmsClient.java # 抽象短信客户端
        │       ├── AliyunSmsClient.java   # 阿里云短信
        │       ├── TencentSmsClient.java  # 腾讯云短信
        │       ├── HuaweiSmsClient.java   # 华为云短信
        │       ├── QiniuSmsClient.java    # 七牛云短信
        │       └── DebugDingTalkSmsClient.java # 钉钉调试短信
        ├── enums/
        └── property/
```

**关键设计**：

##### 5.1 短信服务框架

**架构设计**：
```
SmsSendService
    ↓
SmsProducer（发送 MQ 消息）
    ↓
SmsSendConsumer（消费 MQ 消息）
    ↓
SmsClientFactory（根据渠道ID获取客户端）
    ↓
具体渠道 SmsClient 实现（AliyunSmsClient、TencentSmsClient 等）
```

**支持的短信渠道**：
- 阿里云短信
- 腾讯云短信
- 华为云短信
- 七牛云短信
- 钉钉调试短信（开发环境）

##### 5.2 验证码框架

**支持的验证码类型**：
- 滑块拼图验证码（`PictureWordCaptchaServiceImpl`）
- 传统图形验证码
- 数字验证码
- 文字点选验证码

**存储方式**：Redis

##### 5.3 数据权限框架

**支持的数据权限类型**：
- 全部数据权限
- 部门数据权限
- 部门及以下数据权限
- 仅本人数据权限
- 自定义数据权限

**实现方式**：基于 MyBatis Plus 拦截器

##### 5.4 社交登录框架

**集成 JustAuth**，支持以下第三方登录：
- 微信开放平台
- 微信公众号
- 微信小程序
- QQ
- 支付宝
- 钉钉
- 企业微信

---

#### 6. `job/` - 定时任务

```
job/
└── demo/
    └── DemoJob.java                 # 示例定时任务（使用 XXL-Job）
```

**关键设计**：
- 使用 `@XxlJob` 注解注册定时任务
- 使用 `@TenantJob` 注解支持多租户（自动遍历所有租户执行）

**示例**：
```java
@Component
public class DemoJob {

    @XxlJob("demoJob")
    @TenantJob
    public void execute() {
        // 定时任务逻辑
        // 支持多租户，会自动遍历所有租户执行
    }
}
```

---

#### 7. `mq/` - 消息队列

使用消息队列实现异步处理。

```
mq/
├── consumer/                         # 消费者
│   ├── mail/
│   │   └── MailSendConsumer.java    # 邮件发送消费者
│   └── sms/
│       └── SmsSendConsumer.java     # 短信发送消费者
├── message/                          # 消息定义
│   ├── mail/
│   │   └── MailSendMessage.java     # 邮件发送消息
│   └── sms/
│       └── SmsSendMessage.java      # 短信发送消息
└── producer/                         # 生产者
    ├── mail/
    │   └── MailProducer.java        # 邮件发送生产者
    └── sms/
        └── SmsProducer.java         # 短信发送生产者
```

**关键设计**：

##### 7.1 短信发送流程

```
SmsSendService.sendSingleSms()
    ↓
SmsProducer.sendSmsSendMessage()  # 发送 MQ 消息
    ↓
SmsSendConsumer.onMessage()       # 消费 MQ 消息
    ↓
SmsClientFactory.getSmsClient()   # 获取短信渠道客户端
    ↓
AliyunSmsClient.sendSms()         # 实际发送短信
```

##### 7.2 邮件发送流程

```
MailSendService.sendSingleMail()
    ↓
MailProducer.sendMailSendMessage()  # 发送 MQ 消息
    ↓
MailSendConsumer.onMessage()        # 消费 MQ 消息
    ↓
JavaMailSender.send()               # 实际发送邮件
```

**为什么使用 MQ**：
- 异步发送，提高响应速度
- 削峰填谷，避免短信/邮件服务器压力过大
- 失败重试，提高成功率

---

#### 8. `service/` - 业务逻辑层

业务逻辑实现，事务控制。

```
service/
├── auth/                             # 认证服务
│   ├── AdminAuthService.java        # 接口
│   └── AdminAuthServiceImpl.java    # 实现：登录、登出、刷新Token、获取权限
├── dept/                             # 部门服务
│   ├── DeptService.java
│   ├── DeptServiceImpl.java         # 部门 CRUD、部门树构建、缓存管理
│   ├── PostService.java
│   └── PostServiceImpl.java         # 岗位 CRUD、岗位校验
├── dict/                             # 字典服务
│   ├── DictDataService.java
│   ├── DictDataServiceImpl.java     # 字典数据 CRUD、缓存管理
│   ├── DictTypeService.java
│   └── DictTypeServiceImpl.java     # 字典类型 CRUD
├── logger/                           # 日志服务
│   ├── LoginLogService.java
│   ├── LoginLogServiceImpl.java     # 创建登录日志、查询导出
│   ├── OperateLogService.java
│   └── OperateLogServiceImpl.java   # 创建操作日志、查询导出
├── mail/                             # 邮件服务
│   ├── MailAccountService.java
│   ├── MailAccountServiceImpl.java  # 邮件账号 CRUD
│   ├── MailLogService.java
│   ├── MailLogServiceImpl.java      # 邮件日志查询
│   ├── MailSendService.java
│   ├── MailSendServiceImpl.java     # 发送邮件（单个/批量、同步/异步）
│   ├── MailTemplateService.java
│   └── MailTemplateServiceImpl.java # 邮件模板 CRUD、发送测试
├── member/                           # 会员服务（跨模块调用）
│   ├── MemberService.java
│   └── MemberServiceImpl.java       # 调用 member 模块的 Feign 接口
├── notice/                           # 通知公告服务
│   ├── NoticeService.java
│   └── NoticeServiceImpl.java       # 通知公告 CRUD
├── notify/                           # 站内信服务
│   ├── NotifyMessageService.java
│   ├── NotifyMessageServiceImpl.java # 站内信查询、标记已读
│   ├── NotifySendService.java
│   ├── NotifySendServiceImpl.java   # 发送站内信
│   ├── NotifyTemplateService.java
│   └── NotifyTemplateServiceImpl.java # 站内信模板 CRUD
├── oauth2/                           # OAuth2 服务
│   ├── OAuth2ApproveService.java
│   ├── OAuth2ApproveServiceImpl.java # 用户授权记录管理
│   ├── OAuth2ClientService.java
│   ├── OAuth2ClientServiceImpl.java # OAuth2 客户端 CRUD、校验
│   ├── OAuth2CodeService.java
│   ├── OAuth2CodeServiceImpl.java   # 授权码生成、消费
│   ├── OAuth2GrantService.java
│   ├── OAuth2GrantServiceImpl.java  # 授权码模式、密码模式、刷新Token
│   ├── OAuth2TokenService.java
│   └── OAuth2TokenServiceImpl.java  # Token 创建、刷新、校验、删除
├── permission/                       # 权限服务
│   ├── MenuService.java
│   ├── MenuServiceImpl.java         # 菜单 CRUD、菜单树构建、缓存管理
│   ├── PermissionService.java
│   ├── PermissionServiceImpl.java   # 权限分配、权限校验、缓存管理
│   ├── RoleService.java
│   └── RoleServiceImpl.java         # 角色 CRUD、角色校验、缓存管理
├── sms/                              # 短信服务
│   ├── SmsChannelService.java
│   ├── SmsChannelServiceImpl.java   # 短信渠道 CRUD、渠道管理
│   ├── SmsCodeService.java
│   ├── SmsCodeServiceImpl.java      # 验证码生成、发送、校验
│   ├── SmsLogService.java
│   ├── SmsLogServiceImpl.java       # 短信日志查询
│   ├── SmsSendService.java
│   ├── SmsSendServiceImpl.java      # 发送短信（单个/批量、同步/异步）
│   ├── SmsTemplateService.java
│   └── SmsTemplateServiceImpl.java  # 短信模板 CRUD、发送测试
├── social/                           # 社交登录服务
│   ├── SocialClientService.java
│   ├── SocialClientServiceImpl.java # 社交客户端 CRUD
│   ├── SocialUserService.java
│   └── SocialUserServiceImpl.java   # 社交用户绑定、授权URL获取
├── tenant/                           # 租户服务
│   ├── handler/
│   │   └── TenantMenuHandler.java   # 租户菜单处理器
│   ├── TenantPackageService.java
│   ├── TenantPackageServiceImpl.java # 租户套餐 CRUD
│   ├── TenantService.java
│   └── TenantServiceImpl.java       # 租户 CRUD、租户校验
└── user/                             # 用户服务
    ├── AdminUserService.java
    └── AdminUserServiceImpl.java    # 用户 CRUD、密码管理、导入导出
```

**关键设计**：
- 使用 `@Service` 注解
- 使用 `@Transactional` 注解进行事务控制
- 使用 `@CacheEvict`、`@Cacheable` 注解进行缓存管理
- 注入 Mapper 层进行数据库操作
- 注入其他 Service 进行业务组合

**示例**：
```java
@Service
@Validated
public class AdminUserServiceImpl implements AdminUserService {

    @Resource
    private AdminUserMapper userMapper;

    @Resource
    private DeptService deptService;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(UserSaveReqVO createReqVO) {
        // 1. 校验用户名唯一性
        validateUserExists(null, createReqVO.getUsername(), createReqVO.getMobile(),
                          createReqVO.getEmail());

        // 2. 校验部门存在
        deptService.validateDeptList(Collections.singleton(createReqVO.getDeptId()));

        // 3. 插入用户
        AdminUserDO user = UserConvert.INSTANCE.convert(createReqVO);
        user.setPassword(encodePassword(createReqVO.getPassword()));
        userMapper.insert(user);

        return user.getId();
    }

    private String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }
}
```

---

#### 9. `util/` - 工具类

```
util/
└── oauth2/
    └── OAuth2Utils.java             # OAuth2 工具类（Scope 解析等）
```

---

## 核心功能

### 1. 用户管理

**功能列表**：
- 用户 CRUD 操作
- 用户列表查询（支持部门、岗位、状态等条件）
- 用户导入（Excel）
- 用户导出（Excel）
- 重置密码
- 修改状态（启用/禁用）
- 用户个人资料管理
- 修改密码
- 头像上传

**核心文件**：
- `controller/admin/user/UserController.java` - 用户管理接口
- `controller/admin/user/UserProfileController.java` - 个人资料接口
- `service/user/AdminUserServiceImpl.java` - 用户业务逻辑
- `dal/dataobject/user/AdminUserDO.java` - 用户实体
- `dal/mysql/user/AdminUserMapper.java` - 用户 Mapper

**数据库表**：`system_users`

**关键字段**：
```sql
CREATE TABLE system_users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(30) NOT NULL UNIQUE COMMENT '用户账号',
    password VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    nickname VARCHAR(30) NOT NULL COMMENT '用户昵称',
    dept_id BIGINT COMMENT '部门ID',
    post_ids VARCHAR(255) COMMENT '岗位编号数组（JSON）',
    email VARCHAR(50) COMMENT '邮箱',
    mobile VARCHAR(11) COMMENT '手机号',
    sex TINYINT COMMENT '性别（1男 2女）',
    avatar VARCHAR(512) COMMENT '头像URL',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态（0正常 1停用）',
    login_ip VARCHAR(50) COMMENT '最后登录IP',
    login_date DATETIME COMMENT '最后登录时间',
    -- 租户字段
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    -- 通用字段
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT NOT NULL DEFAULT 0 COMMENT '是否删除'
);
```

**安全设计**：
- 密码使用 BCrypt 加密存储
- 登录失败次数限制
- 最后登录信息记录

---

### 2. 权限管理

#### 2.1 菜单管理

**功能**：
- 菜单 CRUD
- 菜单树查询
- 菜单类型（目录、菜单、按钮）
- 菜单权限标识

**核心文件**：
- `controller/admin/permission/MenuController.java`
- `service/permission/MenuServiceImpl.java`
- `dal/dataobject/permission/MenuDO.java`

**数据库表**：`system_menu`

**菜单树结构**：
```
目录（Directory）
├── 菜单（Menu）
│   ├── 按钮（Button）- 查询
│   ├── 按钮（Button）- 新增
│   ├── 按钮（Button）- 修改
│   └── 按钮（Button）- 删除
└── 菜单（Menu）
```

#### 2.2 角色管理

**功能**：
- 角色 CRUD
- 角色类型（内置角色、自定义角色）
- 角色数据范围（全部、部门、部门及以下、仅本人、自定义）
- 角色状态管理

**核心文件**：
- `controller/admin/permission/RoleController.java`
- `service/permission/RoleServiceImpl.java`
- `dal/dataobject/permission/RoleDO.java`

**数据库表**：`system_role`

#### 2.3 权限分配

**功能**：
- 给角色分配菜单权限
- 给用户分配角色
- 权限校验
- 权限缓存管理

**核心文件**：
- `controller/admin/permission/PermissionController.java`
- `service/permission/PermissionServiceImpl.java`

**关联表**：
- `system_role_menu` - 角色菜单关联表
- `system_user_role` - 用户角色关联表

**RBAC 模型**：
```
用户（User）
    ↓ N:M
角色（Role）
    ↓ N:M
菜单/权限（Menu）
```

**Redis 缓存键**：
```
role:{id}                        # 角色缓存
user_role_ids:{userId}           # 用户角色ID列表
menu_role_ids:{menuId}           # 菜单角色ID列表
permission_menu_ids:{permission} # 权限对应的菜单ID
```

**权限校验示例**：
```java
@PreAuthorize("@ss.hasPermission('system:user:create')")
public CommonResult<Long> createUser(@RequestBody UserSaveReqVO reqVO) {
    // ...
}
```

---

### 3. 部门管理

**功能**：
- 部门 CRUD
- 部门树结构查询
- 部门负责人设置
- 子部门查询缓存

**核心文件**：
- `controller/admin/dept/DeptController.java`
- `service/dept/DeptServiceImpl.java`
- `dal/dataobject/dept/DeptDO.java`

**数据库表**：`system_dept`

**树形结构**：
```sql
id, parent_id, name, sort, leader_user_id, phone, email, status
```

**Redis 缓存**：
```
dept_children_ids:{id}  # 部门所有子部门ID数组
```

---

### 4. 岗位管理

**功能**：
- 岗位 CRUD
- 岗位列表查询
- 用户岗位关联

**核心文件**：
- `controller/admin/dept/PostController.java`
- `service/dept/PostServiceImpl.java`
- `dal/dataobject/dept/PostDO.java`

**数据库表**：
- `system_post` - 岗位表
- `system_user_post` - 用户岗位关联表

---

### 5. 数据字典

**功能**：
- 字典类型管理
- 字典数据管理
- 字典缓存
- 前端下拉选项数据源

**核心文件**：
- `controller/admin/dict/DictTypeController.java`
- `controller/admin/dict/DictDataController.java`
- `service/dict/DictTypeServiceImpl.java`
- `service/dict/DictDataServiceImpl.java`

**数据库表**：
- `system_dict_type` - 字典类型表
- `system_dict_data` - 字典数据表

**两层结构**：
```
字典类型（DictType）- 如：gender（性别）
    ├── 字典数据（DictData）- 1（男）
    └── 字典数据（DictData）- 2（女）

字典类型（DictType）- 如：system_user_sex（用户性别）
    ├── 字典数据（DictData）- 0（未知）
    ├── 字典数据（DictData）- 1（男）
    └── 字典数据（DictData）- 2（女）
```

---

### 6. OAuth2 认证授权

**功能**：
- OAuth2 客户端管理
- 访问令牌管理
- 刷新令牌管理
- 授权码生成和消费
- 用户授权记录

**支持的授权模式**：
- 授权码模式（Authorization Code）
- 密码模式（Password）
- 客户端模式（Client Credentials）
- 刷新令牌（Refresh Token）

**核心文件**：
- `controller/admin/oauth2/OAuth2OpenController.java` - OAuth2 标准接口
- `controller/admin/oauth2/OAuth2ClientController.java` - 客户端管理
- `controller/admin/oauth2/OAuth2TokenController.java` - Token 管理
- `service/oauth2/OAuth2GrantServiceImpl.java` - 授权逻辑
- `service/oauth2/OAuth2TokenServiceImpl.java` - Token 逻辑

**数据库表**：
- `system_oauth2_client` - OAuth2 客户端表
- `system_oauth2_access_token` - 访问令牌表
- `system_oauth2_refresh_token` - 刷新令牌表
- `system_oauth2_code` - 授权码表
- `system_oauth2_approve` - 用户授权记录表

**OAuth2 授权码模式流程**：
```
1. 客户端请求授权
   GET /system/oauth2/authorize?response_type=code&client_id=xxx&redirect_uri=xxx&scope=xxx

2. 用户登录并授权

3. 返回授权码
   redirect_uri?code=xxx

4. 客户端用授权码换取访问令牌
   POST /system/oauth2/token
   grant_type=authorization_code&code=xxx&client_id=xxx&client_secret=xxx&redirect_uri=xxx

5. 返回访问令牌
   {
     "access_token": "xxx",
     "refresh_token": "xxx",
     "token_type": "Bearer",
     "expires_in": 3600
   }

6. 使用访问令牌访问资源
   GET /admin-api/system/user/get
   Authorization: Bearer xxx
```

**Token 存储**：
- 访问令牌：数据库 + Redis 缓存
- 刷新令牌：仅数据库
- 授权码：仅数据库

---

### 7. 短信服务

**功能**：
- 短信渠道管理
- 短信模板管理
- 短信发送（单个/批量）
- 验证码发送和校验
- 短信发送日志
- 短信回调处理

**支持的短信渠道**：
- 阿里云短信
- 腾讯云短信
- 华为云短信
- 七牛云短信
- 钉钉调试短信（开发环境）

**核心文件**：
- `controller/admin/sms/SmsChannelController.java` - 渠道管理
- `controller/admin/sms/SmsTemplateController.java` - 模板管理
- `controller/admin/sms/SmsLogController.java` - 日志查询
- `controller/admin/sms/SmsCallbackController.java` - 回调接收
- `service/sms/SmsSendServiceImpl.java` - 短信发送
- `service/sms/SmsCodeServiceImpl.java` - 验证码
- `framework/sms/core/client/impl/AliyunSmsClient.java` - 阿里云短信客户端

**数据库表**：
- `system_sms_channel` - 短信渠道表
- `system_sms_template` - 短信模板表
- `system_sms_code` - 短信验证码表
- `system_sms_log` - 短信发送日志表

**短信发送流程**：
```
SmsSendService.sendSingleSms(mobile, userId, userType, templateCode, params)
    ↓
1. 查询短信模板和渠道配置
2. 校验手机号格式
3. 构建短信内容（模板变量替换）
4. 发送到 MQ
    ↓
SmsSendConsumer.onMessage()
    ↓
1. 获取短信客户端（SmsClientFactory）
2. 调用短信API发送
3. 记录发送日志
```

**验证码流程**：
```
SmsCodeService.sendSmsCode(mobile, createReqVO)
    ↓
1. 生成6位数字验证码
2. 存储到 Redis（ttl: 10分钟）
3. 调用 SmsSendService 发送短信

SmsCodeService.useSmsCode(useReqVO)
    ↓
1. 从 Redis 获取验证码
2. 校验验证码是否正确
3. 删除验证码（一次性使用）
```

**Redis Key**：
```
sms_code:{scene}:{mobile}  # 验证码
```

---

### 8. 邮件服务

**功能**：
- 邮件账号管理（SMTP 配置）
- 邮件模板管理（支持 Freemarker）
- 邮件发送（单个/批量）
- 邮件发送日志

**核心文件**：
- `controller/admin/mail/MailAccountController.java` - 账号管理
- `controller/admin/mail/MailTemplateController.java` - 模板管理
- `controller/admin/mail/MailLogController.java` - 日志查询
- `service/mail/MailSendServiceImpl.java` - 邮件发送

**数据库表**：
- `system_mail_account` - 邮件账号表（SMTP 配置）
- `system_mail_template` - 邮件模板表
- `system_mail_log` - 邮件发送日志表

**邮件账号配置**：
```java
host: smtp.qq.com
port: 587
username: xxx@qq.com
password: 授权码
from: xxx@qq.com
```

**邮件发送流程**：
```
MailSendService.sendSingleMail(mail, userId, userType, templateCode, params)
    ↓
1. 查询邮件模板和账号配置
2. 校验邮箱格式
3. 使用 Freemarker 渲染邮件内容
4. 发送到 MQ
    ↓
MailSendConsumer.onMessage()
    ↓
1. 创建 JavaMailSender
2. 构建 MimeMessage
3. 发送邮件
4. 记录发送日志
```

---

### 9. 操作日志

**功能**：
- 操作日志记录
- 操作日志查询
- 操作日志导出

**核心文件**：
- `controller/admin/logger/OperateLogController.java`
- `service/logger/OperateLogServiceImpl.java`
- `dal/dataobject/logger/OperateLogDO.java`

**数据库表**：`system_operate_log`

**关键字段**：
```sql
trace_id VARCHAR(64),           # 链路追踪ID
user_id BIGINT,                 # 用户ID
user_type TINYINT,              # 用户类型
module VARCHAR(50),             # 操作模块
name VARCHAR(50),               # 操作名称
type INT,                       # 操作分类
content TEXT,                   # 操作内容
exts VARCHAR(512),              # 拓展字段
request_method VARCHAR(16),     # 请求方法
request_url VARCHAR(255),       # 请求URL
user_ip VARCHAR(50),            # 用户IP
user_agent VARCHAR(500),        # 浏览器UA
java_method VARCHAR(512),       # Java方法名
java_method_args VARCHAR(8000), # Java方法参数
start_time DATETIME,            # 开始时间
duration INT,                   # 执行时长（毫秒）
result_code INT,                # 结果码
result_msg VARCHAR(512),        # 结果提示
result_data VARCHAR(4000)       # 结果数据
```

**记录方式**：
- 基于 AOP 自动记录
- 使用 `@ApiAccessLog` 注解标注需要记录的接口

**示例**：
```java
@PostMapping("/create")
@PreAuthorize("@ss.hasPermission('system:user:create')")
@ApiAccessLog(operateType = CREATE)
public CommonResult<Long> createUser(@RequestBody UserSaveReqVO reqVO) {
    // ...
}
```

---

### 10. 登录日志

**功能**：
- 登录日志记录
- 登录日志查询
- 登录日志导出

**核心文件**：
- `controller/admin/logger/LoginLogController.java`
- `service/logger/LoginLogServiceImpl.java`
- `dal/dataobject/logger/LoginLogDO.java`

**数据库表**：`system_login_log`

**关键字段**：
```sql
log_type BIGINT,          # 日志类型（登录/登出）
trace_id VARCHAR(64),     # 链路追踪ID
user_id BIGINT,           # 用户ID
user_type TINYINT,        # 用户类型
username VARCHAR(50),     # 用户账号
result TINYINT,           # 登录结果（成功/失败）
user_ip VARCHAR(50),      # 用户IP
user_agent VARCHAR(512)   # 浏览器UA
```

---

### 11. 多租户

**功能**：
- 租户管理
- 租户套餐管理
- 租户数据隔离
- 租户菜单权限控制

**核心文件**：
- `controller/admin/tenant/TenantController.java`
- `controller/admin/tenant/TenantPackageController.java`
- `service/tenant/TenantServiceImpl.java`
- `service/tenant/TenantPackageServiceImpl.java`

**数据库表**：
- `system_tenant` - 租户表
- `system_tenant_package` - 租户套餐表

**多租户实现**：

#### 11.1 数据隔离

**方式**：基于 MyBatis Plus 拦截器，自动在 SQL 中添加 `tenant_id` 过滤条件。

**实体类继承**：
```java
public class AdminUserDO extends TenantBaseDO {
    // MyBatis Plus 自动添加 WHERE tenant_id = ?
}
```

**TenantBaseDO**：
```java
public abstract class TenantBaseDO extends BaseDO {
    @TableField
    private Long tenantId;
}
```

#### 11.2 忽略租户

**配置方式**：
```yaml
yudao:
  tenant:
    enable: true
    ignore-tables:
      - system_tenant
      - system_tenant_package
      - system_dict_type
      - system_dict_data
```

**注解方式**：
```java
@TenantIgnore
public List<DictDataDO> selectList() {
    // 查询时不添加 tenant_id 过滤条件
}
```

#### 11.3 租户套餐

**租户套餐定义**：
- 套餐名称
- 套餐菜单权限（`menuIds`）
- 套餐状态

**租户关联套餐**：
- 租户创建时选择套餐
- 租户只能使用套餐内的菜单权限

---

### 12. 社交登录

**功能**：
- 社交登录客户端管理
- 社交用户信息管理
- 社交用户绑定
- 获取授权 URL
- 授权回调处理

**支持的第三方平台**：
- 微信开放平台
- 微信公众号
- 微信小程序
- QQ
- 支付宝
- 钉钉
- 企业微信

**核心文件**：
- `controller/admin/social/SocialClientController.java`
- `controller/admin/social/SocialUserController.java`
- `service/social/SocialUserServiceImpl.java`
- `framework/justauth/core/AuthRequestFactory.java`

**数据库表**：
- `system_social_client` - 社交登录客户端表
- `system_social_user` - 社交用户表
- `system_social_user_bind` - 社交用户绑定表

**社交登录流程**：
```
1. 前端请求授权 URL
   GET /admin-api/system/social-user/get-authorize-url?type=WECHAT_MINI_APP&redirectUri=xxx

2. 后端返回授权 URL
   {
     "code": 0,
     "data": "https://open.weixin.qq.com/connect/oauth2/authorize?..."
   }

3. 用户在第三方平台授权

4. 第三方平台回调
   redirectUri?code=xxx&state=xxx

5. 前端调用绑定接口
   POST /admin-api/system/social-user/bind
   {
     "type": "WECHAT_MINI_APP",
     "code": "xxx",
     "state": "xxx"
   }

6. 后端创建社交用户并绑定
   - 调用第三方 API 获取用户信息
   - 存储 SocialUserDO
   - 创建 SocialUserBindDO 绑定关系
```

**集成 JustAuth**：
- `AuthRequestFactory` 统一创建第三方登录请求
- 支持动态配置客户端（从数据库读取）

---

### 13. 站内信

**功能**：
- 站内信模板管理
- 站内信发送
- 站内信查询
- 已读/未读状态管理

**核心文件**：
- `controller/admin/notify/NotifyTemplateController.java`
- `controller/admin/notify/NotifyMessageController.java`
- `service/notify/NotifySendServiceImpl.java`
- `service/notify/NotifyMessageServiceImpl.java`

**数据库表**：
- `system_notify_template` - 站内信模板表
- `system_notify_message` - 站内信消息表

**模板变量替换**：
```
模板内容：您的订单{orderNo}已发货，预计{days}天送达。

参数：
{
  "orderNo": "202301010001",
  "days": "3"
}

结果：您的订单202301010001已发货，预计3天送达。
```

---

### 14. 通知公告

**功能**：
- 通知公告 CRUD
- 公告类型（通知、公告）
- 状态管理（草稿、已发布）

**核心文件**：
- `controller/admin/notice/NoticeController.java`
- `service/notice/NoticeServiceImpl.java`

**数据库表**：`system_notice`

---

### 15. 地区管理

**功能**：
- 地区数据查询（省市区三级）
- 地区树结构

**核心文件**：
- `controller/admin/ip/AreaController.java`
- `controller/app/ip/AppAreaController.java`

---

### 16. 验证码

**功能**：
- 验证码生成
- 验证码校验
- 多种验证码类型

**支持的验证码类型**：
- 滑块拼图验证码
- 传统图形验证码
- 数字验证码
- 文字点选验证码

**核心文件**：
- `controller/admin/captcha/CaptchaController.java`
- `framework/captcha/core/RedisCaptchaServiceImpl.java`
- `framework/captcha/core/PictureWordCaptchaServiceImpl.java`

**存储方式**：Redis

---

## 架构设计

### 1. 三层架构

严格遵循三层架构模式：

```
Controller 层（控制层）
    ↓
Service 层（业务逻辑层）
    ↓
DAL 层（数据访问层）
    ├── Mapper（MyBatis Plus）
    └── Redis DAO
```

**职责划分**：
- **Controller 层**：接收 HTTP 请求，参数校验，调用 Service，返回响应
- **Service 层**：业务逻辑处理，事务控制，调用 Mapper 和其他 Service
- **DAL 层**：数据访问，封装 SQL 查询和 Redis 操作

**示例**：
```java
// Controller 层
@RestController
@RequestMapping("/system/user")
public class UserController {
    @Resource
    private AdminUserService userService;

    @PostMapping("/create")
    public CommonResult<Long> createUser(@Valid @RequestBody UserSaveReqVO reqVO) {
        return success(userService.createUser(reqVO));
    }
}

// Service 层
@Service
public class AdminUserServiceImpl implements AdminUserService {
    @Resource
    private AdminUserMapper userMapper;

    @Override
    @Transactional
    public Long createUser(UserSaveReqVO createReqVO) {
        // 业务逻辑
        AdminUserDO user = UserConvert.INSTANCE.convert(createReqVO);
        userMapper.insert(user);
        return user.getId();
    }
}

// DAL 层
@Mapper
public interface AdminUserMapper extends BaseMapperX<AdminUserDO> {
    // 数据访问
}
```

---

### 2. 对象转换分层

使用 MapStruct 进行对象转换，避免手动 setter/getter。

```
前端 VO (View Object)
    ↓ Convert.convert()
Service 层 DO (Data Object)
    ↓ Convert.toDTO()
RPC DTO (Data Transfer Object)
```

**对象类型**：
- **VO（View Object）**：视图对象，用于 Controller 层接收请求和返回响应
  - `ReqVO`：请求对象
  - `RespVO`：响应对象
  - `PageReqVO`：分页请求对象
  - `PageRespVO`：分页响应对象
- **DO（Data Object）**：数据对象，用于 Service 层和 DAL 层
- **DTO（Data Transfer Object）**：数据传输对象，用于 RPC 调用

**示例**：
```java
// Controller 接收 VO
@PostMapping("/create")
public CommonResult<Long> createUser(@RequestBody UserSaveReqVO reqVO) {
    // VO → DO
    Long userId = userService.createUser(reqVO);
    return success(userId);
}

// Service 处理 DO
@Override
public Long createUser(UserSaveReqVO createReqVO) {
    // VO → DO
    AdminUserDO user = UserConvert.INSTANCE.convert(createReqVO);
    userMapper.insert(user);
    return user.getId();
}

// RPC 返回 DTO
@Override
public CommonResult<AdminUserRespDTO> getUser(Long id) {
    AdminUserDO user = userService.getUser(id);
    // DO → DTO
    return success(UserConvert.INSTANCE.convertDTO(user));
}
```

---

### 3. 权限控制

#### 3.1 接口权限

使用 Spring Security + `@PreAuthorize` 注解。

```java
@PreAuthorize("@ss.hasPermission('system:user:create')")
public CommonResult<Long> createUser(@RequestBody UserSaveReqVO reqVO) {
    // ...
}
```

**权限标识规则**：`模块:功能:操作`
- `system:user:query` - 查询用户
- `system:user:create` - 创建用户
- `system:user:update` - 修改用户
- `system:user:delete` - 删除用户

#### 3.2 数据权限

使用 `@DataPermission` 注解。

```java
@DataPermission(enable = true, type = DeptDataPermissionTypeEnum.DEPT_ONLY)
public PageResult<AdminUserDO> getUserPage(UserPageReqVO reqVO) {
    // 自动添加数据权限过滤条件
}
```

**数据权限类型**：
- **ALL**：全部数据权限
- **DEPT_CUSTOM**：自定义部门数据权限
- **DEPT_ONLY**：仅本部门数据权限
- **DEPT_AND_CHILD**：本部门及子部门数据权限
- **SELF**：仅本人数据权限

---

### 4. 多租户设计

#### 4.1 透明化多租户

**实现方式**：基于 MyBatis Plus 拦截器，自动在 SQL 中添加 `tenant_id` 过滤条件。

**实体类继承**：
```java
public class AdminUserDO extends TenantBaseDO {
    // 继承 tenantId 字段
    // MyBatis Plus 自动添加 WHERE tenant_id = ?
}
```

**查询示例**：
```java
// Service 代码
userMapper.selectList(null);

// 实际执行的 SQL（自动添加 tenant_id 过滤）
SELECT * FROM system_users WHERE tenant_id = 1 AND deleted = 0
```

#### 4.2 忽略租户

**场景**：系统表、字典表等不需要租户隔离的表。

**配置方式**：
```yaml
yudao:
  tenant:
    enable: true
    ignore-tables:
      - system_tenant
      - system_dict_type
```

**注解方式**：
```java
@TenantIgnore
public List<DictDataDO> selectList() {
    // 查询时不添加 tenant_id 过滤条件
}
```

---

### 5. 缓存策略

#### 5.1 Redis 缓存

**缓存场景**：
- 用户 Session
- OAuth2 访问令牌
- 角色权限
- 用户角色
- 菜单权限
- 部门树
- 字典数据
- 验证码

**Redis Key 设计**：
```java
// 用户 Session
user_session:{sessionId}

// OAuth2 访问令牌
oauth2_access_token:{accessToken}

// 角色
role:{id}

// 用户角色ID列表
user_role_ids:{userId}

// 菜单角色ID列表
menu_role_ids:{menuId}

// 权限对应的菜单ID
permission_menu_ids:{permission}

// 部门子部门ID列表
dept_children_ids:{deptId}

// 字典数据
dict_data:{type}

// 短信验证码
sms_code:{scene}:{mobile}

// 验证码
captcha:{uuid}
```

#### 5.2 缓存更新

**更新时机**：
- 数据修改后立即删除缓存（Cache Aside Pattern）
- 使用 `@CacheEvict` 注解自动删除缓存

**示例**：
```java
@Override
@Transactional
@CacheEvict(cacheNames = RedisKeyConstants.ROLE, key = "#id")
public void updateRole(Long id, RoleUpdateReqVO updateReqVO) {
    // 更新角色
    roleMapper.updateById(role);
    // 缓存自动失效
}
```

---

### 6. 消息队列异步处理

#### 6.1 短信发送

**同步发送**：
```java
smsSendService.sendSingleSms(mobile, userId, userType, templateCode, params);
// 等待短信发送完成
```

**异步发送**：
```java
smsSendService.sendSingleSmsAsync(mobile, userId, userType, templateCode, params);
// 立即返回，短信在后台发送
```

**流程**：
```
SmsSendService.sendSingleSmsAsync()
    ↓
SmsProducer.sendSmsSendMessage()  # 发送 MQ 消息
    ↓ (异步)
SmsSendConsumer.onMessage()       # 消费 MQ 消息
    ↓
SmsClientFactory.getSmsClient()   # 获取短信渠道客户端
    ↓
AliyunSmsClient.sendSms()         # 实际发送短信
```

#### 6.2 邮件发送

**流程**：
```
MailSendService.sendSingleMailAsync()
    ↓
MailProducer.sendMailSendMessage()  # 发送 MQ 消息
    ↓ (异步)
MailSendConsumer.onMessage()        # 消费 MQ 消息
    ↓
JavaMailSender.send()               # 实际发送邮件
```

#### 6.3 MQ 使用场景

**为什么使用 MQ**：
- **异步处理**：提高响应速度
- **削峰填谷**：避免短信/邮件服务器压力过大
- **失败重试**：提高成功率
- **解耦**：业务逻辑与发送逻辑分离

---

### 7. API 日志记录

#### 7.1 操作日志

**使用方式**：
```java
@PostMapping("/create")
@ApiAccessLog(operateType = CREATE)
public CommonResult<Long> createUser(@RequestBody UserSaveReqVO reqVO) {
    // ...
}
```

**记录内容**：
- 操作模块、操作名称、操作类型
- 请求方法、请求 URL、请求参数
- 执行时长、结果码、结果提示
- 用户 IP、浏览器 UA
- Java 方法名、Java 方法参数
- 链路追踪 ID

#### 7.2 登录日志

**记录内容**：
- 日志类型（登录/登出）
- 用户 ID、用户账号
- 登录结果（成功/失败）
- 用户 IP、浏览器 UA
- 链路追踪 ID

---

### 8. 异常处理

**统一异常处理**：
- 使用 `@RestControllerAdvice` 全局异常处理器
- 使用 `CommonResult` 统一返回格式

**异常类型**：
- `ServiceException`：业务异常
- `GlobalException`：全局异常

**示例**：
```java
throw exception(USER_USERNAME_EXISTS);
```

**返回格式**：
```json
{
  "code": 1001001000,
  "msg": "用户账号已经存在",
  "data": null
}
```

---

## 开发指南

### 1. 添加新接口

#### 1.1 定义 VO

```java
// UserSaveReqVO.java
@Data
public class UserSaveReqVO {

    @NotBlank(message = "用户账号不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{4,30}$", message = "用户账号由字母、数字组成")
    private String username;

    @NotBlank(message = "用户昵称不能为空")
    private String nickname;

    private Long deptId;

    private Set<Long> postIds;
}
```

#### 1.2 定义 Controller 接口

```java
@RestController
@RequestMapping("/system/user")
public class UserController {

    @Resource
    private AdminUserService userService;

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('system:user:create')")
    @ApiAccessLog(operateType = CREATE)
    public CommonResult<Long> createUser(@Valid @RequestBody UserSaveReqVO reqVO) {
        Long userId = userService.createUser(reqVO);
        return success(userId);
    }
}
```

#### 1.3 实现 Service 逻辑

```java
@Service
public class AdminUserServiceImpl implements AdminUserService {

    @Resource
    private AdminUserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(UserSaveReqVO createReqVO) {
        // 1. 校验
        validateUserExists(null, createReqVO.getUsername(), null, null);

        // 2. 转换
        AdminUserDO user = UserConvert.INSTANCE.convert(createReqVO);
        user.setPassword(encodePassword(createReqVO.getPassword()));

        // 3. 插入
        userMapper.insert(user);

        return user.getId();
    }
}
```

---

### 2. 添加新的数据权限规则

```java
@Configuration
public class DataPermissionConfiguration {

    @Bean
    public DataPermissionRule deptDataPermissionRule() {
        return new DataPermissionRule("deptDataPermission",
            Collections.singletonList(AdminUserDO.class),
            DeptDataPermissionTypeEnum.class,
            "deptId");
    }
}
```

---

### 3. 添加新的短信渠道

#### 3.1 实现 SmsClient

```java
public class XxxSmsClient extends AbstractSmsClient {

    @Override
    protected void doInit() {
        // 初始化客户端
    }

    @Override
    public SmsSendRespDTO sendSms(Long logId, String mobile, String apiTemplateId,
                                  List<KeyValue<String, Object>> templateParams) throws Throwable {
        // 调用第三方短信 API
        // 返回发送结果
    }
}
```

#### 3.2 注册到 SmsClientFactory

```java
@Component
public class SmsClientFactory {

    @Override
    protected AbstractSmsClient createSmsClient(SmsChannelProperties properties) {
        SmsChannelEnum channelEnum = SmsChannelEnum.getByCode(properties.getCode());
        switch (channelEnum) {
            case XXX:
                return new XxxSmsClient(properties);
            // ...
        }
    }
}
```

---

### 4. 添加新的社交登录

#### 4.1 配置客户端

在 `system_social_client` 表中添加记录：
```sql
INSERT INTO system_social_client (name, social_type, user_type, client_id, client_secret, agent_id, status)
VALUES ('微信小程序', 34, 1, 'wx1234567890', 'secret1234567890', NULL, 0);
```

#### 4.2 前端调用

```javascript
// 1. 获取授权 URL
const response = await getSocialAuthUrl(34, 'https://xxx.com/callback');
const authorizeUrl = response.data;

// 2. 跳转到授权页面
window.location.href = authorizeUrl;

// 3. 授权回调后，获取 code 和 state

// 4. 调用绑定接口
await socialBind(34, code, state);
```

---

### 5. 使用代码生成器

System 模块使用的是内置代码生成器，位于 `yudao-module-infra` 模块。

**步骤**：
1. 访问"基础设施 > 代码生成"菜单
2. 导入数据库表
3. 配置生成选项：
   - 生成模板：CRUD（增删改查）
   - 前端类型：Vue3
   - 上级菜单：选择父菜单
4. 点击"生成代码"
5. 下载生成的代码
6. 将代码复制到对应目录

**生成的文件**：
- Controller
- Service
- ServiceImpl
- Mapper
- DO
- ReqVO
- RespVO
- Convert
- SQL 脚本（菜单、按钮权限）

---

### 6. Excel 导入导出

#### 6.1 导出

```java
@GetMapping("/export")
@PreAuthorize("@ss.hasPermission('system:user:export')")
@ApiAccessLog(operateType = EXPORT)
public void exportUserList(@Valid UserPageReqVO exportReqVO, HttpServletResponse response) throws IOException {
    // 1. 查询数据
    List<AdminUserDO> list = userService.getUserList(exportReqVO);

    // 2. 转换 VO
    List<UserExportRespVO> data = UserConvert.INSTANCE.convertList02(list);

    // 3. 导出 Excel
    ExcelUtils.write(response, "用户数据.xls", "数据", UserExportRespVO.class, data);
}
```

#### 6.2 导入

```java
@PostMapping("/import")
@PreAuthorize("@ss.hasPermission('system:user:import')")
@ApiAccessLog(operateType = IMPORT)
public CommonResult<UserImportRespVO> importUserList(@RequestParam("file") MultipartFile file) throws Exception {
    // 1. 读取 Excel
    List<UserImportExcelVO> list = ExcelUtils.read(file, UserImportExcelVO.class);

    // 2. 导入数据
    UserImportRespVO respVO = userService.importUserList(list, true);

    return success(respVO);
}
```

---

### 7. 定时任务开发

```java
@Component
public class UserJob {

    @Resource
    private AdminUserService userService;

    @XxlJob("userExpireJob")
    @TenantJob  // 支持多租户
    public void execute() {
        // 定时任务逻辑
        // 例如：处理过期用户
        userService.processExpireUsers();
    }
}
```

**配置**：
在 XXL-Job 管理后台配置定时任务：
- 执行器：默认
- JobHandler：userExpireJob
- Cron：0 0 2 * * ?（每天凌晨2点执行）

---

## 数据库设计

### 表清单

| 表名 | 说明 | 行数估计 |
|------|------|---------|
| system_users | 管理员用户表 | 1万 |
| system_dept | 部门表 | 100 |
| system_post | 岗位表 | 50 |
| system_user_post | 用户岗位关联表 | 2万 |
| system_menu | 菜单表 | 200 |
| system_role | 角色表 | 50 |
| system_role_menu | 角色菜单关联表 | 1000 |
| system_user_role | 用户角色关联表 | 2万 |
| system_dict_type | 字典类型表 | 100 |
| system_dict_data | 字典数据表 | 500 |
| system_oauth2_client | OAuth2 客户端表 | 10 |
| system_oauth2_access_token | OAuth2 访问令牌表 | 10万 |
| system_oauth2_refresh_token | OAuth2 刷新令牌表 | 10万 |
| system_oauth2_code | OAuth2 授权码表 | 1万 |
| system_oauth2_approve | OAuth2 授权记录表 | 5万 |
| system_sms_channel | 短信渠道表 | 10 |
| system_sms_template | 短信模板表 | 50 |
| system_sms_code | 短信验证码表 | 100万 |
| system_sms_log | 短信发送日志表 | 1000万 |
| system_mail_account | 邮件账号表 | 10 |
| system_mail_template | 邮件模板表 | 20 |
| system_mail_log | 邮件发送日志表 | 100万 |
| system_login_log | 登录日志表 | 1000万 |
| system_operate_log | 操作日志表 | 亿级 |
| system_notice | 通知公告表 | 1000 |
| system_notify_template | 站内信模板表 | 20 |
| system_notify_message | 站内信消息表 | 1000万 |
| system_social_client | 社交登录客户端表 | 10 |
| system_social_user | 社交用户表 | 10万 |
| system_social_user_bind | 社交用户绑定表 | 10万 |
| system_tenant | 租户表 | 1000 |
| system_tenant_package | 租户套餐表 | 10 |

### 表前缀规范

所有表以 `system_` 开头。

### 通用字段

所有表都包含以下通用字段：

```sql
creator VARCHAR(64) DEFAULT '' COMMENT '创建者',
create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
updater VARCHAR(64) DEFAULT '' COMMENT '更新者',
update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
deleted BIT NOT NULL DEFAULT 0 COMMENT '是否删除'
```

### 多租户字段

支持多租户的表包含以下字段：

```sql
tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID'
```

### 索引设计

#### 1. 唯一索引

```sql
-- 用户表
UNIQUE INDEX uk_username (username, update_time, tenant_id)
UNIQUE INDEX uk_mobile (mobile, update_time, tenant_id)

-- 字典类型表
UNIQUE INDEX uk_type (type, deleted)

-- OAuth2 客户端表
UNIQUE INDEX uk_client_id (client_id)
```

#### 2. 普通索引

```sql
-- 用户表
INDEX idx_dept_id (dept_id)
INDEX idx_status (status)

-- 角色菜单关联表
INDEX idx_role_id (role_id)
INDEX idx_menu_id (menu_id)

-- 操作日志表
INDEX idx_user_id (user_id)
INDEX idx_create_time (create_time)

-- OAuth2 访问令牌表
INDEX idx_access_token (access_token)
INDEX idx_refresh_token (refresh_token)
INDEX idx_expires_time (expires_time)
```

---

## API 接口

### 管理后台接口（32 个 Controller）

**URL 前缀**：`/admin-api/system/`

#### 认证授权

| 接口 | 方法 | 说明 |
|------|------|------|
| /auth/login | POST | 用户登录 |
| /auth/logout | POST | 用户登出 |
| /auth/refresh-token | POST | 刷新访问令牌 |
| /auth/get-permission-info | GET | 获取用户权限信息 |

#### 用户管理

| 接口 | 方法 | 说明 |
|------|------|------|
| /user/create | POST | 创建用户 |
| /user/update | PUT | 修改用户 |
| /user/delete | DELETE | 删除用户 |
| /user/get | GET | 获取用户详情 |
| /user/page | GET | 获取用户分页 |
| /user/list | GET | 获取用户列表 |
| /user/export | GET | 导出用户 |
| /user/import | POST | 导入用户 |

#### 部门管理

| 接口 | 方法 | 说明 |
|------|------|------|
| /dept/create | POST | 创建部门 |
| /dept/update | PUT | 修改部门 |
| /dept/delete | DELETE | 删除部门 |
| /dept/get | GET | 获取部门详情 |
| /dept/list | GET | 获取部门列表 |
| /dept/get-simple-list | GET | 获取部门精简信息列表 |

#### 权限管理

| 接口 | 方法 | 说明 |
|------|------|------|
| /menu/create | POST | 创建菜单 |
| /menu/update | PUT | 修改菜单 |
| /menu/delete | DELETE | 删除菜单 |
| /menu/get | GET | 获取菜单详情 |
| /menu/list | GET | 获取菜单列表 |
| /role/create | POST | 创建角色 |
| /role/update | PUT | 修改角色 |
| /role/delete | DELETE | 删除角色 |
| /permission/assign-role-menu | POST | 分配角色菜单 |
| /permission/assign-user-role | POST | 分配用户角色 |

### APP 接口（3 个 Controller）

**URL 前缀**：`/app-api/system/`

| 接口 | 方法 | 说明 |
|------|------|------|
| /dict-data/type | GET | 根据字典类型查询字典数据 |
| /area/get-by-code | GET | 获得地区 |
| /tenant/get-id-by-name | GET | 使用租户名，获得租户编号 |

---

## 总结

### System 模块特点

1. **完整性**：覆盖用户、权限、部门、字典、OAuth2、短信、邮件、日志、租户、社交登录等核心功能
2. **可扩展性**：框架层高度封装，业务层清晰分层
3. **高性能**：Redis 多级缓存，消息队列异步处理
4. **多租户**：MyBatis Plus 拦截器实现透明化多租户
5. **安全性**：Spring Security + OAuth2 + 数据权限
6. **易用性**：代码生成器、Excel 导入导出、丰富的工具类
7. **标准化**：严格的三层架构、RESTful API、统一异常处理

### 代码量统计

- **Java 文件**：357 个
- **目录数**：166 个
- **Controller**：35 个
- **Service**：34 个
- **DO**：32 个
- **Mapper**：32 个
- **数据库表**：32 张

### 技术亮点

1. **严格的三层架构**：Controller → Service → DAL
2. **MapStruct 对象转换**：避免手动 setter/getter
3. **权限控制**：接口权限 + 数据权限
4. **多租户透明化**：MyBatis Plus 拦截器自动添加 tenant_id
5. **消息队列异步处理**：短信、邮件异步发送
6. **Redis 缓存策略**：角色权限、OAuth2 Token、部门树等多级缓存
7. **API 日志记录**：操作日志 + 登录日志
8. **代码生成器**：快速生成 CRUD 代码
9. **Excel 导入导出**：封装 EasyExcel
10. **定时任务**：XXL-Job + 多租户支持

---

## 常见问题

### 1. 如何添加新的权限？

在 `system_menu` 表中添加按钮记录，设置权限标识（如 `system:user:create`），然后在 Controller 接口上使用 `@PreAuthorize("@ss.hasPermission('system:user:create')")` 注解。

### 2. 如何自定义数据权限规则？

在 `framework/datapermission/config/DataPermissionConfiguration.java` 中添加新的 `DataPermissionRule`。

### 3. 如何添加新的短信渠道？

实现 `AbstractSmsClient`，然后在 `SmsClientFactory` 中注册。

### 4. 如何添加新的社交登录？

在 `system_social_client` 表中添加记录，JustAuth 会自动识别。

### 5. 如何添加新的定时任务？

创建类，使用 `@XxlJob` 注解标注方法，然后在 XXL-Job 管理后台配置。

### 6. 如何关闭多租户？

在配置文件中设置 `yudao.tenant.enable=false`。

### 7. 如何忽略某个表的多租户？

在配置文件中添加到 `yudao.tenant.ignore-tables` 列表。

### 8. 如何自定义 Redis Key 前缀？

在 `dal/redis/RedisKeyConstants.java` 中定义。

### 9. 如何使用消息队列？

参考 `mq/` 目录下的 Producer 和 Consumer 示例。

### 10. 如何使用代码生成器？

访问"基础设施 > 代码生成"菜单，导入数据库表，配置生成选项，下载代码。

---

## 相关文档

- **官方文档**：https://doc.iocoder.cn/
- **视频教程**：https://t.zsxq.com/02Yf6M7Qn
- **源码地址**：https://gitee.com/zhijiantianya/yudao-cloud

---

**本文档最后更新时间**：2025-01-06
