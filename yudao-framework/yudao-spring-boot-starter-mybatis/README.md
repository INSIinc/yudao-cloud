# yudao-spring-boot-starter-mybatis

## 📖 模块简介

MyBatis 增强组件，基于 MyBatis Plus 提供数据库访问能力，集成数据库连接池、多数据源、分页查询、数据翻译等功能。

## ✨ 功能特性

### 🗄️ 1. 数据库支持

支持多种主流数据库：

- **MySQL**：默认支持
- **PostgreSQL**：支持
- **Oracle**：支持（可选依赖）
- **SQL Server**：支持（可选依赖）
- **达梦（DM）**：支持（可选依赖）
- **人大金仓（KingBase）**：支持（可选依赖）
- **OpenGauss**：支持（可选依赖）

### 🔧 2. 核心组件

#### 2.1 基础实体类（BaseDO）

提供统一的实体基类，包含通用字段：

```java
import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;

@TableName("system_user")
public class UserDO extends BaseDO {
    private Long id;
    private String username;
    // 自动包含：createTime, updateTime, creator, updater, deleted
}
```

**字段说明**：
- `createTime`：创建时间（自动填充）
- `updateTime`：更新时间（自动填充）
- `creator`：创建者（自动填充）
- `updater`：更新者（自动填充）
- `deleted`：逻辑删除标识（自动处理）

#### 2.2 增强 Mapper（BaseMapperX）

扩展 MyBatis Plus 的 BaseMapper，提供更强大的查询能力：

```java
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;

@Mapper
public interface UserMapper extends BaseMapperX<UserDO> {
    // 已内置丰富的 CRUD 方法
}
```

**核心方法**：

##### 分页查询
```java
// 1. 基础分页
PageResult<UserDO> page = userMapper.selectPage(pageParam, 
    new LambdaQueryWrapperX<UserDO>()
        .likeIfPresent(UserDO::getUsername, username)
);

// 2. 带排序的分页
PageResult<UserDO> page = userMapper.selectPage(sortablePageParam, 
    new LambdaQueryWrapperX<UserDO>()
        .eqIfPresent(UserDO::getStatus, status)
);

// 3. 不分页查询（pageSize = -1）
PageResult<UserDO> allUsers = userMapper.selectPage(
    new PageParam().setPageSize(PageParam.PAGE_SIZE_NONE), 
    new QueryWrapper<>()
);
```

##### 连表查询（基于 MyBatis-Plus-Join）
```java
// 连表分页查询
PageResult<UserDO> page = userMapper.selectJoinPage(pageParam,
    UserDO.class,
    new MPJLambdaWrapperX<UserDO>()
        .selectAll(UserDO.class)
        .leftJoin(DeptDO.class, DeptDO::getId, UserDO::getDeptId)
        .eqIfPresent(UserDO::getStatus, status)
);
```

##### 单条记录查询
```java
// 查询单条记录（多条会报错）
UserDO user = userMapper.selectOne(UserDO::getUsername, username);

// 可选的单条记录查询（多条取第一条）
UserDO user = userMapper.selectOneWithLimit(
    new LambdaQueryWrapperX<UserDO>()
        .eq(UserDO::getStatus, 1)
);
```

##### 列表查询
```java
// 简单查询
List<UserDO> users = userMapper.selectList(UserDO::getDeptId, deptId);

// 复杂条件查询
List<UserDO> users = userMapper.selectList(
    new LambdaQueryWrapperX<UserDO>()
        .inIfPresent(UserDO::getId, ids)
        .geIfPresent(UserDO::getCreateTime, startTime)
);
```

##### 统计查询
```java
// 统计总数
Long count = userMapper.selectCount(UserDO::getStatus, 1);

// 复杂条件统计
Long count = userMapper.selectCount(
    new LambdaQueryWrapperX<UserDO>()
        .eqIfPresent(UserDO::getDeptId, deptId)
);
```

##### 批量操作
```java
// 批量插入（底层使用 Db.saveBatch）
userMapper.insertBatch(userList);

// 批量更新（底层使用 Db.updateBatchById）
userMapper.updateBatch(userList);

// 批量删除
userMapper.deleteBatchIds(ids);
```

#### 2.3 增强查询包装器

##### LambdaQueryWrapperX

扩展 MyBatis Plus 的 LambdaQueryWrapper，新增条件为空时自动忽略的方法：

```java
new LambdaQueryWrapperX<UserDO>()
    .likeIfPresent(UserDO::getUsername, username)      // 为空则忽略
    .eqIfPresent(UserDO::getStatus, status)            // 为空则忽略
    .betweenIfPresent(UserDO::getCreateTime, startTime, endTime)  // 为空则忽略
    .inIfPresent(UserDO::getId, ids)                   // 集合为空则忽略
    .geIfPresent(UserDO::getCreateTime, startTime)     // 为空则忽略
    .leIfPresent(UserDO::getCreateTime, endTime);      // 为空则忽略
```

##### MPJLambdaWrapperX

扩展 MyBatis-Plus-Join 的包装器，支持连表查询：

```java
new MPJLambdaWrapperX<UserDO>()
    .selectAll(UserDO.class)
    .selectAs(DeptDO::getName, UserDO::getDeptName)
    .leftJoin(DeptDO.class, DeptDO::getId, UserDO::getDeptId)
    .eqIfPresent(UserDO::getStatus, status)
    .likeIfPresent(DeptDO::getName, deptName);
```

### 🔄 3. 数据翻译（Easy-Trans）

基于 [Easy-Trans](https://gitee.com/fhs-opensource/easy_trans) 实现的数据字典翻译功能。

#### 使用方式

```java
import com.fhs.core.trans.anno.Trans;
import com.fhs.core.trans.constant.TransType;

public class UserVO {
    private Long id;
    
    @Trans(type = TransType.SIMPLE, target = User.class, fields = "username", ref = "username")
    private Long userId;
    private String username; // 自动翻译填充
    
    @Trans(type = TransType.DICTIONARY, key = "user_status")
    private Integer status;
    private String statusName; // 字典翻译结果
}
```

**支持的翻译类型**：
- `TransType.SIMPLE`：对象属性翻译
- `TransType.DICTIONARY`：数据字典翻译
- `TransType.RPC`：远程服务翻译
- 自定义翻译器

### 🔐 4. 类型处理器（TypeHandler）

提供多种自定义类型处理器，简化数据库字段与 Java 对象的映射：

#### 4.1 集合类型处理器

```java
// List<String> 类型
@TableField(typeHandler = StringListTypeHandler.class)
private List<String> tags;

// List<Integer> 类型
@TableField(typeHandler = IntegerListTypeHandler.class)
private List<Integer> roleIds;

// List<Long> 类型  
@TableField(typeHandler = LongListTypeHandler.class)
private List<Long> deptIds;

// Set<Long> 类型
@TableField(typeHandler = LongSetTypeHandler.class)
private Set<Long> permissionIds;
```

**存储格式**：在数据库中以逗号分隔的字符串存储，如：`"1,2,3"`

#### 4.2 加密类型处理器

```java
// 敏感数据加密存储
@TableField(typeHandler = EncryptTypeHandler.class)
private String password;

@TableField(typeHandler = EncryptTypeHandler.class)
private String idCard;
```

**特性**：
- 自动加密：写入数据库前自动加密
- 自动解密：读取数据库后自动解密
- 基于 AES 加密算法

### 📊 5. 分页增强

#### 5.1 分页插件

自动配置 MyBatis Plus 分页插件，支持多种数据库方言。

#### 5.2 分页参数

```java
// 基础分页参数
PageParam pageParam = new PageParam()
    .setPageNo(1)
    .setPageSize(10);

// 带排序的分页参数
SortablePageParam sortablePageParam = new SortablePageParam()
    .setPageNo(1)
    .setPageSize(10)
    .setSortingFields(Arrays.asList(
        new SortingField("createTime", "DESC"),
        new SortingField("id", "ASC")
    ));
```

#### 5.3 分页结果

```java
PageResult<UserDO> pageResult = userMapper.selectPage(pageParam, queryWrapper);

// 获取数据
List<UserDO> list = pageResult.getList();
Long total = pageResult.getTotal();
```

### 🔑 6. 主键策略

支持多种数据库的主键生成策略：

```yaml
mybatis-plus:
  global-config:
    db-config:
      id-type: INPUT  # 使用数据库自增
```

**自动适配**：
- PostgreSQL：使用 `PostgreKeyGenerator`
- Oracle：使用 `OracleKeyGenerator`
- KingBase：使用 `KingbaseKeyGenerator`
- 达梦：使用 `DmKeyGenerator`
- H2：使用 `H2KeyGenerator`

### ⚙️ 7. 自动填充

通过 `DefaultDBFieldHandler` 自动填充公共字段：

```java
@TableField(fill = FieldFill.INSERT)
private LocalDateTime createTime;  // 插入时自动填充

@TableField(fill = FieldFill.INSERT_UPDATE)
private LocalDateTime updateTime;  // 插入和更新时自动填充

@TableField(fill = FieldFill.INSERT, jdbcType = JdbcType.VARCHAR)
private String creator;  // 插入时自动填充当前用户 ID

@TableField(fill = FieldFill.INSERT_UPDATE, jdbcType = JdbcType.VARCHAR)
private String updater;  // 插入和更新时自动填充当前用户 ID
```

### 🗑️ 8. 逻辑删除

默认启用逻辑删除：

```java
@TableLogic
private Boolean deleted;  // false=未删除，true=已删除
```

**特性**：
- 查询时自动过滤已删除数据
- 删除操作自动转为更新 deleted 字段
- 不影响物理删除方法（如 `deleteById`）

## 📦 依赖引入

在 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-mybatis</artifactId>
</dependency>
```

## ⚙️ 配置说明

### 基础配置

```yaml
# MyBatis Plus 配置
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true  # 下划线转驼峰
    cache-enabled: false                # 禁用二级缓存
  global-config:
    db-config:
      id-type: AUTO                     # 主键策略：自增
      logic-delete-field: deleted       # 逻辑删除字段
      logic-delete-value: 1             # 逻辑已删除值
      logic-not-delete-value: 0         # 逻辑未删除值
  type-aliases-package: cn.iocoder.yudao.module.**.dal.dataobject
  
# 数据源配置
spring:
  datasource:
    druid:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://127.0.0.1:3306/ruoyi-vue-pro?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&autoReconnect=true&nullCatalogMeansCurrent=true&zeroDateTimeBehavior=convertToNull
      username: root
      password: 123456

# Mapper 扫描路径（由 base-package 自动配置）
yudao:
  info:
    base-package: cn.iocoder.yudao.module
```

### 多数据源配置

```yaml
spring:
  datasource:
    dynamic:
      primary: master  # 默认数据源
      datasource:
        master:  # 主库
          driver-class-name: com.mysql.cj.jdbc.Driver
          url: jdbc:mysql://127.0.0.1:3306/db_master
          username: root
          password: 123456
        slave:   # 从库
          driver-class-name: com.mysql.cj.jdbc.Driver
          url: jdbc:mysql://127.0.0.1:3307/db_slave
          username: root
          password: 123456
```

**使用方式**：
```java
@DS("slave")  // 切换到从库
public class UserMapper extends BaseMapperX<UserDO> {
}
```

## 🎯 最佳实践

### 1. Mapper 接口定义

```java
@Mapper
public interface UserMapper extends BaseMapperX<UserDO> {
    
    // 简单查询使用默认方法
    default UserDO selectByUsername(String username) {
        return selectOne(UserDO::getUsername, username);
    }
    
    // 复杂查询使用 LambdaQueryWrapperX
    default PageResult<UserDO> selectPage(UserPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<UserDO>()
            .likeIfPresent(UserDO::getUsername, reqVO.getUsername())
            .eqIfPresent(UserDO::getStatus, reqVO.getStatus())
            .betweenIfPresent(UserDO::getCreateTime, reqVO.getCreateTime())
            .orderByDesc(UserDO::getId)
        );
    }
}
```

### 2. 实体类定义

```java
@TableName("system_user")
@Data
@EqualsAndHashCode(callSuper = true)
public class UserDO extends BaseDO {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String username;
    
    private String password;
    
    @TableField(typeHandler = IntegerListTypeHandler.class)
    private List<Integer> roleIds;
    
    private Integer status;
    
    // BaseDO 已包含：createTime, updateTime, creator, updater, deleted
}
```

### 3. 分页查询

```java
@Service
public class UserServiceImpl {
    
    @Resource
    private UserMapper userMapper;
    
    public PageResult<UserDO> getUserPage(UserPageReqVO reqVO) {
        return userMapper.selectPage(reqVO, new LambdaQueryWrapperX<UserDO>()
            .likeIfPresent(UserDO::getUsername, reqVO.getUsername())
            .eqIfPresent(UserDO::getStatus, reqVO.getStatus())
            .betweenIfPresent(UserDO::getCreateTime, reqVO.getCreateTime())
        );
    }
}
```

## 📚 参考文档

- [MyBatis Plus 官方文档](https://baomidou.com/)
- [MyBatis-Plus-Join 文档](https://gitee.com/best_handsome/mybatis-plus-join)
- [Easy-Trans 文档](https://gitee.com/fhs-opensource/easy_trans)
- [Druid 官方文档](https://github.com/alibaba/druid)

## 🔗 相关模块

- `yudao-spring-boot-starter-biz-tenant`：多租户支持
- `yudao-spring-boot-starter-biz-data-permission`：数据权限
- `yudao-spring-boot-starter-redis`：Redis 缓存

## 📝 注意事项

1. **逻辑删除**：使用 `BaseMapperX` 的查询方法会自动过滤已删除数据
2. **批量操作**：建议使用 `insertBatch`、`updateBatch` 提升性能
3. **连表查询**：优先考虑使用 `MPJLambdaWrapperX` 而非手写 SQL
4. **数据翻译**：确保实体类继承 `BaseDO` 以支持 Easy-Trans
5. **类型处理器**：集合类型字段必须指定 `typeHandler`
6. **多数据源**：使用 `@DS` 注解切换数据源，注意事务边界

