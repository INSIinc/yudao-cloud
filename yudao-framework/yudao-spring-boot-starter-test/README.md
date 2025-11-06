# yudao-spring-boot-starter-test

## 📖 模块简介

`yudao-spring-boot-starter-test` 是芋道项目的测试组件，提供单元测试和集成测试的基础框架和工具类，帮助开发者快速编写高质量的测试代码。

## ✨ 功能特性

- **多种测试基类**：提供 Mockito、DB、Redis 等多种测试场景的基类
- **内存数据库支持**：集成 H2 内存数据库，无需依赖外部数据库
- **内存 Redis 支持**：集成 jedis-mock，提供内存级别的 Redis 测试环境
- **随机数据生成**：基于 PODAM 框架，自动生成测试用的 POJO 对象
- **增强断言工具**：提供便捷的对象比对和异常断言方法
- **自动清理数据**：测试完成后自动清理数据库，保证测试隔离性

## 📦 核心组件

### 测试基类

#### 1. BaseMockitoUnitTest
纯 Mockito 的单元测试基类，适用于不需要 Spring 容器的轻量级单元测试。

```java
public class UserServiceTest extends BaseMockitoUnitTest {
    
    @Mock
    private UserMapper userMapper;
    
    @InjectMocks
    private UserServiceImpl userService;
    
    @Test
    public void testGetUser() {
        // 测试代码
    }
}
```

#### 2. BaseDbUnitTest
依赖内存数据库（H2）的单元测试基类，适用于 Mapper 层和 Service 层的测试。

**特点：**
- 自动配置 H2 内存数据库
- 集成 MyBatis Plus 和数据源配置
- 测试结束后自动清理数据（通过 `/sql/clean.sql`）
- 使用 `application-unit-test` 配置文件

```java
public class UserMapperTest extends BaseDbUnitTest {
    
    @Resource
    private UserMapper userMapper;
    
    @Test
    public void testInsert() {
        // 数据库操作测试
    }
}
```

#### 3. BaseRedisUnitTest
依赖内存 Redis 的单元测试基类，适用于 Redis 相关功能的测试。

**特点：**
- 使用 jedis-mock 提供内存级别的 Redis
- 集成 Redisson 自动配置
- 无需启动真实的 Redis 服务

```java
public class CacheServiceTest extends BaseRedisUnitTest {
    
    @Resource
    private StringRedisTemplate redisTemplate;
    
    @Test
    public void testCache() {
        // Redis 操作测试
    }
}
```

#### 4. BaseDbAndRedisUnitTest
同时依赖内存数据库和 Redis 的单元测试基类，适用于需要同时使用 DB 和 Redis 的场景。

```java
public class OrderServiceTest extends BaseDbAndRedisUnitTest {
    
    @Resource
    private OrderMapper orderMapper;
    
    @Resource
    private StringRedisTemplate redisTemplate;
    
    @Test
    public void testCreateOrder() {
        // 同时使用 DB 和 Redis 的测试
    }
}
```

### 工具类

#### RandomUtils - 随机数据生成工具

基于 PODAM 框架，提供智能的随机对象生成功能。

**主要方法：**
```java
// 随机生成对象
UserDO user = RandomUtils.randomPojo(UserDO.class);

// 随机生成对象并自定义属性
UserDO user = RandomUtils.randomPojo(UserDO.class, o -> {
    o.setStatus(CommonStatusEnum.ENABLE.getStatus());
});

// 随机生成列表
List<UserDO> users = RandomUtils.randomPojoList(UserDO.class);

// 随机生成字符串
String randomStr = RandomUtils.randomString();

// 随机生成日期
LocalDateTime randomDate = RandomUtils.randomLocalDateTime();
```

**智能特性：**
- `status` 字段自动返回 0 或 1
- `type`、`category`、`scope`、`result` 等字段返回 tinyint 范围的值
- 字符串默认生成 10 位随机字符
- 日期在当前时间前后 30 天范围内随机

#### AssertUtils - 断言工具

提供增强的断言功能，简化测试代码。

**主要方法：**
```java
// 比对两个对象的属性是否一致
AssertUtils.assertPojoEquals(expectedUser, actualUser);

// 比对时忽略某些字段
AssertUtils.assertPojoEquals(expectedUser, actualUser, "id", "createTime");

// 断言抛出指定的 ServiceException
AssertUtils.assertServiceException(() -> {
    userService.deleteUser(999L);
}, ErrorCode.USER_NOT_EXISTS);
```

## 🔧 使用方式

### 1. 添加依赖

在需要测试的模块的 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 2. 配置测试环境

在 `src/test/resources` 目录下创建 `application-unit-test.yaml`：

```yaml
spring:
  # 数据源配置
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE
    username: sa
    password: 
    
  # SQL 初始化配置
  sql:
    init:
      mode: always
      schema-locations: classpath:sql/create_tables.sql
```

### 3. 准备 SQL 脚本

在 `src/test/resources/sql/` 目录下创建：
- `create_tables.sql` - 建表脚本
- `clean.sql` - 清理脚本

`clean.sql` 示例：
```sql
DELETE FROM system_user;
DELETE FROM system_role;
-- 清理其他表...
```

### 4. 编写测试用例

```java
@Import(UserServiceImpl.class)
public class UserServiceImplTest extends BaseDbUnitTest {
    
    @Resource
    private UserService userService;
    
    @Resource
    private UserMapper userMapper;
    
    @Test
    public void testCreateUser_success() {
        // 准备参数
        UserCreateReqVO reqVO = RandomUtils.randomPojo(UserCreateReqVO.class, o -> {
            o.setMobile("13800138000");
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        
        // 调用
        Long userId = userService.createUser(reqVO);
        
        // 断言
        assertNotNull(userId);
        UserDO user = userMapper.selectById(userId);
        assertPojoEquals(reqVO, user, "id", "createTime");
    }
}
```

## 📚 依赖说明

### 核心依赖

| 依赖 | 说明 |
|------|------|
| spring-boot-starter-test | Spring Boot 测试启动器 |
| mockito-inline | Mockito 测试框架（支持 final 类和静态方法） |
| h2 | H2 内存数据库 |
| jedis-mock | Redis 内存模拟 |
| podam | POJO 随机数据生成器 |

### 内部依赖

| 依赖 | 说明 |
|------|------|
| yudao-common | 通用工具类和异常定义 |
| yudao-spring-boot-starter-mybatis | MyBatis 配置支持 |
| yudao-spring-boot-starter-redis | Redis 配置支持 |

## 📖 最佳实践

### 1. 选择合适的测试基类

- **纯逻辑测试** → 使用 `BaseMockitoUnitTest`
- **Mapper 测试** → 使用 `BaseDbUnitTest`
- **Service 测试（仅 DB）** → 使用 `BaseDbUnitTest`
- **Redis 功能测试** → 使用 `BaseRedisUnitTest`
- **复杂业务测试（DB + Redis）** → 使用 `BaseDbAndRedisUnitTest`

### 2. Mock 外部依赖

Service 层测试时，对于其他模块的 Service，使用 Mock：

```java
@Import({UserServiceImpl.class})
@MockBean({DeptService.class, RoleService.class})
public class UserServiceImplTest extends BaseDbUnitTest {
    // ...
}
```

### 3. 测试数据隔离

- 每个测试方法使用独立的测试数据
- 依赖 `@Sql` 注解自动清理数据
- 避免测试方法之间的相互依赖

### 4. 使用随机数据

使用 `RandomUtils` 生成测试数据，减少硬编码：

```java
// ✅ 推荐
UserDO user = RandomUtils.randomPojo(UserDO.class);

// ❌ 不推荐
UserDO user = new UserDO();
user.setUsername("test");
user.setNickname("测试");
// ... 大量 set 方法
```

## 🔗 相关资源

- [芋道 Spring Boot 单元测试 Test 入门](https://www.iocoder.cn/Spring-Boot/Unit-Test/?yudao)
- [项目主页](https://github.com/YunaiV/ruoyi-vue-pro)

## 📝 注意事项

1. **H2 数据库兼容性**：H2 设置为 MySQL 模式，但某些 MySQL 特性可能不支持
2. **测试隔离性**：确保每个测试方法独立运行，不依赖其他测试的数据
3. **性能考虑**：对于大量数据的测试，考虑使用 `@Sql` 导入初始化数据
4. **Mock 范围**：只 Mock 外部依赖，本模块的 Mapper 使用真实的 H2 数据库

## 🤝 贡献指南

如需扩展测试工具类或添加新的测试基类，请遵循以下原则：
- 保持简单易用
- 提供清晰的注释和示例
- 确保与现有测试框架兼容
- 编写单元测试验证功能

---

**作者**: 芋道源码  
**许可**: MIT License

