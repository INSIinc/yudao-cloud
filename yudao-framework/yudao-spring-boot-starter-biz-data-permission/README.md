# yudao-spring-boot-starter-biz-data-permission

## 📖 模块简介

`yudao-spring-boot-starter-biz-data-permission` 是芋道项目的数据权限组件，提供了基于角色和部门的行级数据权限控制能力。该模块通过 MyBatis Plus 拦截器和 JSqlParser 实现 SQL 重写，自动在查询 SQL 中添加 WHERE 条件，实现对数据的细粒度访问控制，确保用户只能访问其权限范围内的数据。

## ✨ 核心特性

### 1. 灵活的权限规则体系

- **部门数据权限（DeptDataPermissionRule）**：基于用户所属部门及下级部门进行数据过滤
  - 支持本部门数据
  - 支持本部门及子部门数据
  - 支持指定部门数据
  - 支持全部数据
  - 支持仅本人数据
- **自定义权限规则**：支持扩展 `DataPermissionRule` 接口实现自定义权限逻辑
- **多规则组合**：支持同时应用多个权限规则，灵活组合使用

### 2. 透明的 SQL 重写机制

- **自动 SQL 拦截**：基于 MyBatis Plus 拦截器，无需修改现有业务代码
- **智能 SQL 解析**：使用 JSqlParser 解析并重写 SQL，自动添加权限过滤条件
- **表别名支持**：正确处理 SQL 中的表别名，确保条件准确添加
- **复杂查询支持**：支持 JOIN、子查询等复杂 SQL 场景

### 3. 注解式权限控制

- **`@DataPermission`**：方法或类级别的权限控制注解
  - `enable`：控制是否启用数据权限（默认 true）
  - `includeRules`：指定生效的权限规则（优先级高）
  - `excludeRules`：排除特定的权限规则（优先级低）
- **灵活控制粒度**：可在 Service 层、Mapper 层任意位置使用

### 4. 跨服务权限传递

- **RPC 调用支持**：通过 Feign 拦截器自动传递数据权限上下文
- **Gateway 网关支持**：在网关层解析并传递权限信息
- **上下文管理**：基于 ThreadLocal 实现权限上下文的线程隔离

### 5. 便捷的工具类

- **`DataPermissionUtils`**：提供编程式数据权限控制
  - `executeIgnore()`：临时忽略数据权限执行逻辑
  - 支持 Runnable 和 Callable 两种调用方式

## 📦 依赖关系

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-biz-data-permission</artifactId>
</dependency>
```

**主要依赖模块：**
- `yudao-common`：基础工具类
- `yudao-spring-boot-starter-mybatis`：MyBatis 增强（必需）
- `yudao-spring-boot-starter-security`：安全框架（可选，使用 DeptDataPermissionRule 时必需）
- `yudao-spring-boot-starter-rpc`：RPC 调用支持（可选）

## 🚀 快速开始

### 1. 添加依赖

在需要使用数据权限的模块中添加依赖：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-biz-data-permission</artifactId>
</dependency>
```

### 2. 数据表设计

确保需要进行数据权限控制的表包含以下字段：

```sql
CREATE TABLE `system_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(30) NOT NULL,
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID',  -- 用于部门数据权限
  `creator` varchar(64) DEFAULT NULL,
  -- 其他字段...
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

### 3. 基础使用

#### 3.1 默认启用（推荐）

数据权限默认对所有 Mapper 方法生效，无需添加注解：

```java
@Service
@Slf4j
public class UserServiceImpl implements UserService {
    
    @Resource
    private UserMapper userMapper;
    
    // 自动应用数据权限，用户只能查询其权限范围内的数据
    public List<UserDO> getUserList() {
        return userMapper.selectList();
    }
}
```

#### 3.2 禁用数据权限

对于某些系统管理接口，需要查询所有数据时：

```java
@Service
public class UserServiceImpl implements UserService {
    
    @Resource
    private UserMapper userMapper;
    
    // 方法级别禁用
    @DataPermission(enable = false)
    public List<UserDO> getAllUsers() {
        return userMapper.selectList();
    }
    
    // 使用工具类禁用
    public List<UserDO> getAllUsersWithUtil() {
        return DataPermissionUtils.executeIgnore(() -> 
            userMapper.selectList()
        );
    }
}
```

#### 3.3 指定权限规则

可以显式指定使用哪些权限规则：

```java
@Service
public class UserServiceImpl implements UserService {
    
    // 只使用部门数据权限规则
    @DataPermission(includeRules = DeptDataPermissionRule.class)
    public List<UserDO> getDeptUsers() {
        return userMapper.selectList();
    }
    
    // 排除特定权限规则
    @DataPermission(excludeRules = DeptDataPermissionRule.class)
    public List<UserDO> getUsersWithoutDeptFilter() {
        return userMapper.selectList();
    }
}
```

#### 3.4 类级别控制

可在类上统一控制数据权限：

```java
@Service
@DataPermission(enable = false)  // 整个类禁用数据权限
public class SystemServiceImpl implements SystemService {
    
    // 该方法不应用数据权限
    public List<UserDO> getAllUsers() {
        return userMapper.selectList();
    }
    
    // 方法级别覆盖类级别配置
    @DataPermission(enable = true)
    public List<UserDO> getMyDeptUsers() {
        return userMapper.selectList();
    }
}
```

## 🔧 高级使用

### 1. 自定义数据权限规则

实现 `DataPermissionRule` 接口创建自定义权限规则：

```java
import cn.iocoder.yudao.framework.datapermission.core.rule.DataPermissionRule;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;

import java.util.Set;

/**
 * 示例：基于创建人的数据权限规则
 * 用户只能查看自己创建的数据
 */
@Component
public class CreatorDataPermissionRule implements DataPermissionRule {
    
    @Override
    public Set<String> getTableNames() {
        // 指定该规则适用的表
        return Sets.newHashSet("system_user", "system_role");
    }
    
    @Override
    public Expression getExpression(String tableName, Alias tableAlias) {
        // 获取当前登录用户 ID
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return null;
        }
        
        // 构建 WHERE 条件：creator = userId
        Column column = new Column(tableAlias != null ? 
            tableAlias.getName() + ".creator" : "creator");
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(column);
        equalsTo.setRightExpression(new LongValue(userId));
        
        return equalsTo;
    }
}
```

### 2. 自定义部门字段配置

如果数据表使用了非标准的字段名，可通过自定义配置：

```java
@Configuration
public class DataPermissionConfiguration {
    
    @Bean
    public DeptDataPermissionRuleCustomizer deptDataPermissionRuleCustomizer() {
        return rule -> {
            // 自定义 system_post 表的部门字段为 department_id
            rule.addDeptColumn("system_post", "department_id");
            
            // 自定义 biz_order 表的用户字段为 owner_id
            rule.addUserColumn("biz_order", "owner_id");
        };
    }
}
```

### 3. RPC 调用中的数据权限

在微服务架构中，数据权限会自动通过 RPC 传递：

```java
// 服务提供方
@Service
public class UserServiceImpl implements UserService {
    
    @Override
    public List<UserRespDTO> getUserList() {
        // 数据权限自动生效，使用调用方传递的权限上下文
        return userMapper.selectList();
    }
}

// 服务调用方
@Service
public class OrderServiceImpl implements OrderService {
    
    @Resource
    private UserApi userApi;  // Feign 客户端
    
    public void createOrder() {
        // 调用远程服务时，数据权限上下文自动传递
        List<UserRespDTO> users = userApi.getUserList();
    }
}
```

### 4. 编程式权限控制

使用 `DataPermissionUtils` 工具类进行细粒度控制：

```java
@Service
public class ReportServiceImpl implements ReportService {
    
    @Resource
    private OrderMapper orderMapper;
    
    public ReportVO generateReport() {
        // 1. 查询当前用户权限范围内的订单（应用数据权限）
        List<OrderDO> myOrders = orderMapper.selectList();
        
        // 2. 统计全部订单数量（忽略数据权限）
        Long totalCount = DataPermissionUtils.executeIgnore(() -> 
            orderMapper.selectCount()
        );
        
        return new ReportVO(myOrders, totalCount);
    }
    
    // 支持有返回值的场景
    public OrderDO getOrderById(Long id) {
        return DataPermissionUtils.executeIgnore(() -> 
            orderMapper.selectById(id)
        );
    }
}
```

## 📋 部门数据权限详解

### 数据权限范围类型

系统内置的 `DeptDataPermissionRule` 支持以下几种数据范围：

| 数据范围 | 说明 | 适用场景 |
|---------|------|---------|
| **全部数据** | 查看所有数据，不添加任何过滤条件 | 超级管理员 |
| **指定部门数据** | 查看指定部门（可多个）的数据 | 跨部门管理角色 |
| **本部门数据** | 仅查看用户所属部门的数据 | 部门经理 |
| **本部门及子部门数据** | 查看用户所属部门及所有下级部门的数据 | 分管领导 |
| **仅本人数据** | 只能查看自己创建或拥有的数据 | 普通员工 |

### SQL 重写示例

**原始 SQL：**
```sql
SELECT * FROM system_user WHERE status = 1
```

**应用部门数据权限后：**
```sql
-- 本部门数据
SELECT * FROM system_user 
WHERE status = 1 AND dept_id = 100

-- 本部门及子部门数据
SELECT * FROM system_user 
WHERE status = 1 AND dept_id IN (100, 101, 102, 103)

-- 仅本人数据
SELECT * FROM system_user 
WHERE status = 1 AND (dept_id = 100 OR user_id = 1)
```

### 部门变更场景处理

当用户调整部门时，存在历史数据的处理问题：

**方案一：不修改历史数据（默认采用）**
- 用户调整部门后，看不到原部门创建的数据
- 优点：简单，符合大部分业务场景
- 缺点：历史数据丢失可见性

**方案二：数据迁移脚本**
```sql
-- 将用户历史数据的 dept_id 更新为新部门
UPDATE system_user 
SET dept_id = 200  -- 新部门ID
WHERE creator = '1'  -- 调整部门的用户ID
  AND dept_id = 100  -- 原部门ID
```

**方案三：同时使用 dept_id 和 user_id 过滤**
- 修改 `DeptDataPermissionRule` 实现
- WHERE 条件变为：`dept_id = ? OR user_id = ?`
- 用户既能看到新部门数据，也能看到自己创建的历史数据

## 🎯 最佳实践

### 1. 注解使用建议

```java
// ✅ 推荐：默认启用，特殊情况才禁用
@Service
public class UserServiceImpl {
    
    // 大部分业务方法，默认应用数据权限
    public List<UserDO> getUserList() {
        return userMapper.selectList();
    }
    
    // 仅系统管理接口需要禁用
    @DataPermission(enable = false)
    public List<UserDO> getAllUsersForAdmin() {
        return userMapper.selectList();
    }
}

// ❌ 不推荐：在每个方法上都加注解
@Service
public class UserServiceImpl {
    
    @DataPermission(enable = true)  // 多余，默认就是 true
    public List<UserDO> getUserList() {
        return userMapper.selectList();
    }
}
```

### 2. 性能优化建议

```java
// ✅ 推荐：在 Service 层统一处理数据权限
@Service
public class UserServiceImpl {
    
    // 一次查询，应用数据权限
    public List<UserDO> getUserList() {
        return userMapper.selectList();
    }
}

// ❌ 不推荐：在 Controller 层多次调用，产生多次查询
@RestController
public class UserController {
    
    public List<UserRespVO> getUserList() {
        List<UserDO> users = new ArrayList<>();
        // 多次数据库查询，效率低
        for (Long id : userIds) {
            users.add(userService.getUserById(id));
        }
        return users;
    }
}
```

### 3. 测试建议

编写单元测试验证数据权限：

```java
@SpringBootTest
public class UserServiceTest {
    
    @Resource
    private UserService userService;
    
    @Test
    public void testDataPermission() {
        // 1. 模拟管理员登录（全部数据权限）
        mockLogin(1L, "admin", DataScopeEnum.ALL);
        List<UserDO> allUsers = userService.getUserList();
        assertEquals(100, allUsers.size());
        
        // 2. 模拟普通用户登录（本部门数据权限）
        mockLogin(2L, "user", DataScopeEnum.DEPT_ONLY);
        List<UserDO> deptUsers = userService.getUserList();
        assertTrue(deptUsers.size() < allUsers.size());
        
        // 3. 验证禁用数据权限
        List<UserDO> allUsersIgnore = userService.getAllUsersForAdmin();
        assertEquals(100, allUsersIgnore.size());
    }
}
```

## 🔍 工作原理

### 执行流程

```
1. 用户发起查询请求
   ↓
2. Spring AOP 拦截 @DataPermission 注解
   ↓
3. 设置数据权限上下文（DataPermissionContextHolder）
   ↓
4. MyBatis 执行 SQL
   ↓
5. DataPermissionInterceptor 拦截 SQL
   ↓
6. DataPermissionRuleHandler 获取生效的权限规则
   ↓
7. JSqlParser 解析原始 SQL
   ↓
8. 为每个表添加权限过滤条件（调用 rule.getExpression()）
   ↓
9. 生成新的 SQL 并执行
   ↓
10. 返回过滤后的结果
```

### 核心组件

| 组件 | 说明 |
|------|------|
| **DataPermissionRule** | 数据权限规则接口，定义权限过滤逻辑 |
| **DataPermissionRuleFactory** | 权限规则工厂，管理所有规则实例 |
| **DataPermissionRuleHandler** | 权限规则处理器，负责 SQL 重写 |
| **DataPermissionInterceptor** | MyBatis 拦截器，拦截 SQL 执行 |
| **DataPermissionAnnotationAdvisor** | AOP 切面，处理 @DataPermission 注解 |
| **DataPermissionContextHolder** | 上下文持有者，存储当前权限配置 |
| **DataPermissionUtils** | 工具类，提供编程式权限控制 |

## ⚠️ 注意事项

### 1. 字段命名规范

- 默认部门字段：`dept_id`
- 默认用户字段：`user_id`
- 如果使用其他字段名，需要通过 `DeptDataPermissionRuleCustomizer` 自定义配置

### 2. 性能考虑

- 数据权限通过 SQL 重写实现，会在 WHERE 子句中添加条件
- 确保相关字段（如 `dept_id`）建立索引
- 对于复杂查询，建议先分析 SQL 执行计划

```sql
-- 建议添加索引
ALTER TABLE system_user ADD INDEX idx_dept_id (dept_id);
ALTER TABLE system_user ADD INDEX idx_user_id (user_id);
```

### 3. 多表关联查询

对于 JOIN 查询，每个表都会独立应用数据权限规则：

```java
// 假设规则配置了 system_user 和 system_dept 两个表
// 原始 SQL
SELECT u.*, d.name 
FROM system_user u 
LEFT JOIN system_dept d ON u.dept_id = d.id

// 应用数据权限后
SELECT u.*, d.name 
FROM system_user u 
LEFT JOIN system_dept d ON u.dept_id = d.id
WHERE u.dept_id IN (100, 101) AND d.id IN (100, 101)
```

### 4. 子查询场景

子查询中的表也会自动应用数据权限：

```java
// 原始 SQL
SELECT * FROM system_user 
WHERE dept_id IN (SELECT id FROM system_dept WHERE status = 1)

// 应用数据权限后
SELECT * FROM system_user 
WHERE dept_id IN (
  SELECT id FROM system_dept 
  WHERE status = 1 AND id IN (100, 101)  -- 子查询也被过滤
) AND dept_id IN (100, 101)  -- 主查询也被过滤
```

## 🤝 与其他模块的集成

### 1. 与多租户模块集成

数据权限与多租户可以同时使用：

```java
// SQL 会同时添加租户和数据权限条件
// 原始: SELECT * FROM system_user
// 结果: SELECT * FROM system_user 
//       WHERE tenant_id = 1 AND dept_id IN (100, 101)
```

### 2. 与 Redis 缓存集成

使用缓存时需注意数据权限：

```java
@Service
public class UserServiceImpl {
    
    // ❌ 错误：缓存了无权限过滤的数据
    @Cacheable(key = "#id")
    public UserDO getUserById(Long id) {
        return userMapper.selectById(id);
    }
    
    // ✅ 正确：按用户维度缓存，或不使用缓存
    @Cacheable(key = "#userId + ':' + #id")
    public UserDO getUserById(Long userId, Long id) {
        return userMapper.selectById(id);
    }
    
    // ✅ 或者对需要权限控制的查询不使用缓存
    public UserDO getUserById(Long id) {
        return userMapper.selectById(id);
    }
}
```

## 📚 相关文档

- [MyBatis Plus 数据权限插件](https://baomidou.com/pages/2976a3/)
- [JSqlParser 官方文档](https://github.com/JSQLParser/JSqlParser)
- [芋道源码 - 数据权限设计](https://doc.iocoder.cn/)

## 📄 License

本模块遵循 MIT License 开源协议。

