# 芋道 yudao-cloud 通用 POJO 类库

> 本文档详细介绍 `yudao-framework/yudao-common/pojo` 包下的所有通用 POJO 类及其使用方法

## 目录

- [1. CommonResult - 统一响应结果](#1-commonresult---统一响应结果)
- [2. PageParam - 分页参数](#2-pageparam---分页参数)
- [3. PageResult - 分页结果](#3-pageresult---分页结果)
- [4. SortablePageParam - 可排序分页参数](#4-sortablepageparam---可排序分页参数)
- [5. SortingField - 排序字段](#5-sortingfield---排序字段)
- [最佳实践](#最佳实践)
- [常见问题](#常见问题)

---

## 1. CommonResult - 统一响应结果

### 概述

`CommonResult<T>` 是项目中所有 API 接口的统一返回格式，用于封装业务响应数据、错误码和错误信息。

### 核心字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务状态码（0 表示成功，非 0 表示失败） |
| `msg` | String | 提示信息（成功时为空字符串，失败时为错误描述） |
| `data` | T | 业务数据（泛型，成功时包含有效数据） |

### 使用方法

#### 1.1 构建成功响应

```java
// 返回包含数据的成功响应
@GetMapping("/get/{id}")
public CommonResult<UserVO> getUser(@PathVariable Long id) {
    UserVO user = userService.getUser(id);
    return CommonResult.success(user);
}

// 返回空数据的成功响应
@PostMapping("/delete/{id}")
public CommonResult<Boolean> deleteUser(@PathVariable Long id) {
    userService.deleteUser(id);
    return CommonResult.success(true);
}

// 返回列表数据
@GetMapping("/list")
public CommonResult<List<UserVO>> listUsers() {
    List<UserVO> users = userService.listUsers();
    return CommonResult.success(users);
}
```

#### 1.2 构建错误响应

**方式一：使用错误码和消息**

```java
@GetMapping("/check/{id}")
public CommonResult<UserVO> checkUser(@PathVariable Long id) {
    if (id <= 0) {
        return CommonResult.error(400, "用户 ID 必须大于 0");
    }
    // ...
}
```

**方式二：使用 ErrorCode 枚举**

```java
@GetMapping("/get/{id}")
public CommonResult<UserVO> getUser(@PathVariable Long id) {
    UserVO user = userService.getUser(id);
    if (user == null) {
        return CommonResult.error(ErrorCodeConstants.USER_NOT_EXISTS);
    }
    return CommonResult.success(user);
}
```

**方式三：使用 ErrorCode + 动态参数（支持消息格式化）**

```java
// ErrorCode 定义：USER_NOT_EXISTS(1001, "用户 {0} 不存在")
@GetMapping("/get/{id}")
public CommonResult<UserVO> getUser(@PathVariable Long id) {
    UserVO user = userService.getUser(id);
    if (user == null) {
        // 消息会被格式化为："用户 123 不存在"
        return CommonResult.error(ErrorCodeConstants.USER_NOT_EXISTS, id);
    }
    return CommonResult.success(user);
}
```

**方式四：从 ServiceException 创建**

```java
@GetMapping("/get/{id}")
public CommonResult<UserVO> getUser(@PathVariable Long id) {
    try {
        UserVO user = userService.getUser(id);
        return CommonResult.success(user);
    } catch (ServiceException e) {
        return CommonResult.error(e);
    }
}
```

**方式五：错误传递（转换泛型）**

```java
@GetMapping("/getUserOrders/{userId}")
public CommonResult<List<OrderVO>> getUserOrders(@PathVariable Long userId) {
    // 调用用户服务
    CommonResult<UserVO> userResult = userService.getUser(userId);

    // 如果用户查询失败，直接传递错误信息
    if (userResult.isError()) {
        return CommonResult.error(userResult);
    }

    // 查询订单
    List<OrderVO> orders = orderService.getByUserId(userId);
    return CommonResult.success(orders);
}
```

#### 1.3 判断响应状态

```java
// 静态方法判断
if (CommonResult.isSuccess(result.getCode())) {
    // 成功处理
}

// 实例方法判断
if (result.isSuccess()) {
    // 成功处理
}

if (result.isError()) {
    // 错误处理
}
```

#### 1.4 校验响应并获取数据

**方式一：手动校验**

```java
CommonResult<UserVO> result = userService.getUser(id);
if (result.isError()) {
    throw new RuntimeException(result.getMsg());
}
UserVO user = result.getData();
```

**方式二：使用 checkError()**

```java
CommonResult<UserVO> result = userService.getUser(id);
result.checkError(); // 如果失败，自动抛出 ServiceException
UserVO user = result.getData();
```

**方式三：使用 getCheckedData()（推荐）**

```java
// 一行代码完成校验和数据获取
UserVO user = userService.getUser(id).getCheckedData();
// 如果失败，会抛出 ServiceException
```

### JSON 响应格式

**成功响应示例**

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "id": 1,
    "name": "张三",
    "email": "zhangsan@example.com"
  }
}
```

**错误响应示例**

```json
{
  "code": 1001,
  "msg": "用户不存在",
  "data": null
}
```

**列表数据示例**

```json
{
  "code": 0,
  "msg": "",
  "data": [
    {"id": 1, "name": "张三"},
    {"id": 2, "name": "李四"}
  ]
}
```

### 关键特性

1. **@JsonIgnore 标记的辅助方法**
   - `isSuccess()`, `isError()`, `getCheckedData()` 不会被序列化到 JSON 中
   - 仅用于后端代码逻辑判断

2. **防御性编程**
   - `error()` 方法会检查 code 是否为成功码，避免误用
   - 传入成功码会抛出 `IllegalArgumentException`

3. **与异常体系集成**
   - 支持从 `ServiceException` 直接创建错误响应
   - `checkError()` 可将错误响应转换回异常

---

## 2. PageParam - 分页参数

### 概述

`PageParam` 是通用的分页请求参数类，用于接收前端传递的分页信息。

### 字段说明

| 字段 | 类型 | 默认值 | 校验规则 | 说明 |
|------|------|--------|----------|------|
| `pageNo` | Integer | 1 | @NotNull, @Min(1) | 页码，从 1 开始 |
| `pageSize` | Integer | 10 | @NotNull, @Min(1), @Max(100) | 每页条数，最大 100 |

### 常量定义

| 常量 | 值 | 说明 |
|------|-----|------|
| `PAGE_SIZE_NONE` | -1 | 不分页标识（导出场景） |

### 使用示例

#### 2.1 Controller 接收分页参数

```java
@GetMapping("/page")
public CommonResult<PageResult<UserVO>> pageUsers(PageParam pageParam) {
    // pageParam 会自动接收 pageNo 和 pageSize 参数
    // 默认值：pageNo=1, pageSize=10
    PageResult<UserDO> userPage = userService.getUserPage(pageParam);
    return CommonResult.success(BeanUtils.toBean(userPage, UserVO.class));
}
```

#### 2.2 Service 使用分页参数

```java
@Service
public class UserServiceImpl implements UserService {

    @Override
    public PageResult<UserDO> getUserPage(PageParam pageParam) {
        // 方式一：使用 MyBatis Plus 分页
        Page<UserDO> mpPage = new Page<>(pageParam.getPageNo(), pageParam.getPageSize());
        Page<UserDO> result = userMapper.selectPage(mpPage, null);
        return new PageResult<>(result.getRecords(), result.getTotal());

        // 方式二：使用 PageHelper
        PageHelper.startPage(pageParam.getPageNo(), pageParam.getPageSize());
        List<UserDO> users = userMapper.selectList();
        return new PageResult<>(users, PageInfo.of(users).getTotal());
    }
}
```

#### 2.3 不分页场景（导出）

```java
@GetMapping("/export")
public void exportUsers(PageParam pageParam) {
    // 设置为不分页
    pageParam.setPageSize(PageParam.PAGE_SIZE_NONE);

    List<UserDO> allUsers = userService.getAllUsers(pageParam);
    // 导出逻辑...
}
```

### Swagger 文档示例

使用 `@Schema` 注解后，Swagger UI 会自动显示：

```yaml
parameters:
  - name: pageNo
    in: query
    description: 页码，从 1 开始
    required: true
    schema:
      type: integer
      example: 1
  - name: pageSize
    in: query
    description: 每页条数，最大值为 100
    required: true
    schema:
      type: integer
      example: 10
```

### 前端请求示例

```javascript
// GET 请求
axios.get('/api/users/page', {
  params: {
    pageNo: 1,
    pageSize: 20
  }
})

// POST 请求（查询条件 + 分页）
axios.post('/api/users/search', {
  name: '张三',
  pageNo: 1,
  pageSize: 10
})
```

---

## 3. PageResult - 分页结果

### 概述

`PageResult<T>` 是通用的分页结果封装类，用于返回分页查询的数据列表和总条数。

### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `list` | List&lt;T&gt; | 当前页的数据列表 |
| `total` | Long | 总记录数 |

### 构造方法

```java
// 构造器 1：包含数据和总数
new PageResult<>(list, total)

// 构造器 2：只有总数，数据为空列表
new PageResult<>(total)

// 构造器 3：空参构造器
new PageResult<>()
```

### 静态工厂方法

```java
// 创建空的分页结果（total = 0）
PageResult<User> emptyResult = PageResult.empty();

// 创建空列表但指定总数
PageResult<User> emptyWithTotal = PageResult.empty(100L);
```

### 使用示例

#### 3.1 基础使用

```java
@GetMapping("/page")
public CommonResult<PageResult<UserVO>> pageUsers(PageParam pageParam) {
    // 从数据库查询分页数据
    Page<UserDO> mpPage = userMapper.selectPage(
        new Page<>(pageParam.getPageNo(), pageParam.getPageSize()),
        null
    );

    // 转换为 VO
    List<UserVO> voList = BeanUtils.toBean(mpPage.getRecords(), UserVO.class);

    // 构建分页结果
    PageResult<UserVO> pageResult = new PageResult<>(voList, mpPage.getTotal());

    return CommonResult.success(pageResult);
}
```

#### 3.2 使用工具类转换

```java
@GetMapping("/page")
public CommonResult<PageResult<UserVO>> pageUsers(PageParam pageParam) {
    // 查询 DO 分页数据
    PageResult<UserDO> doPage = userService.getUserPage(pageParam);

    // 使用 BeanUtils 直接转换 PageResult
    PageResult<UserVO> voPage = BeanUtils.toBean(doPage, UserVO.class);

    return CommonResult.success(voPage);
}
```

#### 3.3 空结果处理

```java
@GetMapping("/search")
public CommonResult<PageResult<UserVO>> searchUsers(UserSearchReqVO reqVO) {
    List<UserDO> users = userMapper.selectByCondition(reqVO);

    if (CollectionUtils.isEmpty(users)) {
        // 返回空分页结果
        return CommonResult.success(PageResult.empty());
    }

    return CommonResult.success(new PageResult<>(
        BeanUtils.toBean(users, UserVO.class),
        (long) users.size()
    ));
}
```

#### 3.4 与 CollectionUtils 配合

```java
// 使用 CollectionUtils.convertPage() 转换分页结果
PageResult<UserDO> doPage = userMapper.selectPage(...);
PageResult<UserVO> voPage = CollectionUtils.convertPage(
    doPage,
    user -> BeanUtils.toBean(user, UserVO.class)
);
```

### JSON 响应格式

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "total": 100,
    "list": [
      {"id": 1, "name": "张三", "age": 25},
      {"id": 2, "name": "李四", "age": 30}
    ]
  }
}
```

### 前端使用示例

```javascript
// Vue 3 示例
const loadUsers = async () => {
  const response = await axios.get('/api/users/page', {
    params: { pageNo: 1, pageSize: 10 }
  })

  if (response.data.code === 0) {
    const pageResult = response.data.data
    tableData.value = pageResult.list  // 表格数据
    total.value = pageResult.total     // 总条数（用于分页组件）
  }
}
```

---

## 4. SortablePageParam - 可排序分页参数

### 概述

`SortablePageParam` 继承自 `PageParam`，增加了排序功能，适用于需要动态排序的分页查询场景。

### 字段说明

| 字段 | 类型 | 说明 | 继承自 |
|------|------|------|--------|
| `pageNo` | Integer | 页码 | PageParam |
| `pageSize` | Integer | 每页条数 | PageParam |
| `sortingFields` | List&lt;SortingField&gt; | 排序字段列表 | 新增 |

### 使用示例

#### 4.1 Controller 接收排序参数

```java
@GetMapping("/page")
public CommonResult<PageResult<UserVO>> pageUsers(SortablePageParam pageParam) {
    // pageParam 会接收 pageNo, pageSize, sortingFields
    PageResult<UserDO> userPage = userService.getUserPage(pageParam);
    return CommonResult.success(BeanUtils.toBean(userPage, UserVO.class));
}
```

#### 4.2 Service 处理排序

```java
@Service
public class UserServiceImpl implements UserService {

    @Override
    public PageResult<UserDO> getUserPage(SortablePageParam pageParam) {
        // 如果没有指定排序，设置默认排序
        PageUtils.buildDefaultSortingField(pageParam, UserDO::getCreateTime);

        // 构建 MyBatis Plus 查询
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();

        // 处理排序
        if (CollectionUtils.isNotEmpty(pageParam.getSortingFields())) {
            for (SortingField sortingField : pageParam.getSortingFields()) {
                boolean isAsc = SortingField.ORDER_ASC.equals(sortingField.getOrder());

                // 根据字段名动态排序
                switch (sortingField.getField()) {
                    case "createTime":
                        wrapper.orderBy(true, isAsc, UserDO::getCreateTime);
                        break;
                    case "age":
                        wrapper.orderBy(true, isAsc, UserDO::getAge);
                        break;
                    // 更多字段...
                }
            }
        }

        Page<UserDO> mpPage = userMapper.selectPage(
            new Page<>(pageParam.getPageNo(), pageParam.getPageSize()),
            wrapper
        );

        return new PageResult<>(mpPage.getRecords(), mpPage.getTotal());
    }
}
```

#### 4.3 使用 PageUtils 构建排序字段

```java
// 构建单个排序字段（默认降序）
SortingField sortField = PageUtils.buildSortingField(UserDO::getCreateTime);

// 构建单个排序字段（指定顺序）
SortingField sortField = PageUtils.buildSortingField(
    UserDO::getCreateTime,
    SortingField.ORDER_ASC
);

// 设置默认排序（仅当排序字段为空时）
SortablePageParam pageParam = new SortablePageParam();
PageUtils.buildDefaultSortingField(pageParam, UserDO::getCreateTime);
```

### 前端请求示例

```javascript
// 单字段排序
axios.get('/api/users/page', {
  params: {
    pageNo: 1,
    pageSize: 10,
    sortingFields: [
      { field: 'createTime', order: 'desc' }
    ]
  }
})

// 多字段排序
axios.get('/api/users/page', {
  params: {
    pageNo: 1,
    pageSize: 10,
    sortingFields: [
      { field: 'age', order: 'desc' },
      { field: 'createTime', order: 'asc' }
    ]
  }
})
```

### JSON 请求格式

```json
{
  "pageNo": 1,
  "pageSize": 10,
  "sortingFields": [
    {
      "field": "createTime",
      "order": "desc"
    },
    {
      "field": "age",
      "order": "asc"
    }
  ]
}
```

---

## 5. SortingField - 排序字段

### 概述

`SortingField` 是排序字段的 DTO，用于描述单个排序条件。

### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `field` | String | 排序字段名（如 "createTime", "age"） |
| `order` | String | 排序方式："asc" 或 "desc" |

### 常量定义

| 常量 | 值 | 说明 |
|------|-----|------|
| `ORDER_ASC` | "asc" | 升序 |
| `ORDER_DESC` | "desc" | 降序 |

### 使用示例

#### 5.1 手动创建排序字段

```java
// 使用构造器
SortingField sortField = new SortingField("createTime", SortingField.ORDER_DESC);

// 使用 setter
SortingField sortField = new SortingField();
sortField.setField("age");
sortField.setOrder(SortingField.ORDER_ASC);
```

#### 5.2 在查询条件 VO 中使用

```java
@Data
public class UserSearchReqVO extends SortablePageParam {

    @Schema(description = "用户名")
    private String name;

    @Schema(description = "状态")
    private Integer status;

    // sortingFields 字段继承自 SortablePageParam
}
```

#### 5.3 前端构建排序参数

```javascript
// Element Plus Table 排序示例
const handleSortChange = ({ prop, order }) => {
  const sortingFields = []

  if (order) {
    sortingFields.push({
      field: prop,           // 字段名，如 'createTime'
      order: order === 'ascending' ? 'asc' : 'desc'
    })
  }

  loadUsers({ pageNo: 1, pageSize: 10, sortingFields })
}
```

---

## 最佳实践

### 1. 统一使用 CommonResult 作为接口返回值

```java
// ✅ 推荐：所有 Controller 方法返回 CommonResult
@GetMapping("/get/{id}")
public CommonResult<UserVO> getUser(@PathVariable Long id) {
    UserVO user = userService.getUser(id);
    return CommonResult.success(user);
}

// ❌ 不推荐：直接返回对象
@GetMapping("/get/{id}")
public UserVO getUser(@PathVariable Long id) {
    return userService.getUser(id);
}
```

### 2. 使用 getCheckedData() 简化代码

```java
// ✅ 推荐：链式调用
UserVO user = userService.getUser(id).getCheckedData();

// ❌ 不推荐：手动校验
CommonResult<UserVO> result = userService.getUser(id);
if (result.isError()) {
    throw new ServiceException(result.getCode(), result.getMsg());
}
UserVO user = result.getData();
```

### 3. 分页参数使用继承

```java
// ✅ 推荐：查询 VO 继承分页参数
@Data
@EqualsAndHashCode(callSuper = true)
public class UserSearchReqVO extends SortablePageParam {
    private String name;
    private Integer status;
}

// ❌ 不推荐：重复定义分页字段
@Data
public class UserSearchReqVO {
    private String name;
    private Integer status;
    private Integer pageNo;
    private Integer pageSize;
}
```

### 4. PageResult 使用工具类转换

```java
// ✅ 推荐：使用 BeanUtils 转换
PageResult<UserVO> voPage = BeanUtils.toBean(doPage, UserVO.class);

// ❌ 不推荐：手动转换
List<UserVO> voList = new ArrayList<>();
for (UserDO userDO : doPage.getList()) {
    voList.add(BeanUtils.toBean(userDO, UserVO.class));
}
PageResult<UserVO> voPage = new PageResult<>(voList, doPage.getTotal());
```

### 5. 排序字段使用 Lambda 构建

```java
// ✅ 推荐：使用 PageUtils + Lambda（类型安全）
SortingField sortField = PageUtils.buildSortingField(UserDO::getCreateTime);

// ❌ 不推荐：硬编码字段名（容易拼写错误）
SortingField sortField = new SortingField("createTime", "desc");
```

### 6. 空结果使用 empty() 方法

```java
// ✅ 推荐：使用静态工厂方法
return PageResult.empty();

// ❌ 不推荐：手动构造
return new PageResult<>(new ArrayList<>(), 0L);
```

---

## 常见问题

### Q1: CommonResult 的 code 和 HTTP 状态码有什么区别？

**A**:
- **HTTP 状态码**：表示 HTTP 协议层面的状态（200/404/500 等）
- **CommonResult.code**：表示业务逻辑层面的状态（0 成功，非 0 为具体业务错误）

通常情况下：
- 接口调用成功且业务正常：HTTP 200 + code=0
- 接口调用成功但业务失败：HTTP 200 + code≠0（如用户不存在）
- 接口调用失败（服务器错误）：HTTP 500

### Q2: 什么时候使用 PageParam，什么时候使用 SortablePageParam？

**A**:
- **PageParam**：只需要分页，不需要动态排序（固定排序逻辑）
- **SortablePageParam**：需要前端控制排序字段和排序方式

示例：
```java
// 固定按创建时间倒序排列 → 使用 PageParam
public PageResult<User> listUsers(PageParam pageParam) {
    // 固定排序：ORDER BY create_time DESC
}

// 前端可选择按年龄/创建时间/更新时间排序 → 使用 SortablePageParam
public PageResult<User> listUsers(SortablePageParam pageParam) {
    // 动态排序：根据 sortingFields 构建 ORDER BY
}
```

### Q3: PageResult 的 total 是 Long 类型，为什么不用 Integer？

**A**:
- Long 类型支持更大的数据量（最大 2^63-1，约 922 亿亿）
- Integer 最大值仅为 2^31-1（约 21 亿），大数据场景可能溢出
- MyBatis Plus 的 `Page.getTotal()` 也是 Long 类型，保持一致性

### Q4: 为什么 CommonResult 的 isSuccess() 方法要标记 @JsonIgnore？

**A**:
- 这是一个辅助方法，仅用于后端代码逻辑判断
- 前端可以直接判断 `code === 0`，无需额外的 `isSuccess` 字段
- 减少 JSON 响应体大小，避免冗余数据

### Q5: 导出场景如何实现不分页？

**A**:
```java
@GetMapping("/export")
public void exportUsers(PageParam pageParam) {
    // 设置为不分页
    pageParam.setPageSize(PageParam.PAGE_SIZE_NONE); // -1

    // Service 层判断
    if (pageParam.getPageSize() == PageParam.PAGE_SIZE_NONE) {
        // 不使用分页插件，查询所有数据
        return userMapper.selectList(null);
    } else {
        // 正常分页查询
        return userMapper.selectPage(...);
    }
}
```

### Q6: 多字段排序如何实现？

**A**:
```java
// 前端传递多个排序字段
sortingFields: [
  { field: 'age', order: 'desc' },      // 先按年龄降序
  { field: 'createTime', order: 'asc' } // 年龄相同时按创建时间升序
]

// 后端处理
LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
for (SortingField sf : pageParam.getSortingFields()) {
    boolean isAsc = SortingField.ORDER_ASC.equals(sf.getOrder());
    if ("age".equals(sf.getField())) {
        wrapper.orderBy(true, isAsc, UserDO::getAge);
    } else if ("createTime".equals(sf.getField())) {
        wrapper.orderBy(true, isAsc, UserDO::getCreateTime);
    }
}
```

### Q7: 如何在 Service 层返回 CommonResult？

**A**:
一般**不推荐**在 Service 层返回 `CommonResult`，原因：
- Service 层应该专注于业务逻辑，异常通过 `throw ServiceException` 抛出
- Controller 层负责将 Service 返回的数据或异常转换为 `CommonResult`
- 全局异常处理器会自动捕获 `ServiceException` 并转换为统一格式

```java
// ✅ 推荐写法
@Service
public class UserServiceImpl implements UserService {
    public UserVO getUser(Long id) {
        UserDO user = userMapper.selectById(id);
        if (user == null) {
            throw new ServiceException(ErrorCodeConstants.USER_NOT_EXISTS);
        }
        return BeanUtils.toBean(user, UserVO.class);
    }
}

@RestController
public class UserController {
    @GetMapping("/get/{id}")
    public CommonResult<UserVO> getUser(@PathVariable Long id) {
        // Service 抛异常会被全局异常处理器捕获并转换为 CommonResult
        UserVO user = userService.getUser(id);
        return CommonResult.success(user);
    }
}
```

---

## 设计理念

### 1. 统一性
- 所有接口遵循相同的响应格式，降低前后端对接成本
- 统一的分页参数和结果格式，便于前端组件复用

### 2. 类型安全
- 使用泛型 `<T>` 保证编译期类型检查
- 使用 Lambda 表达式构建排序字段，避免字符串硬编码

### 3. 扩展性
- `PageParam` → `SortablePageParam` 的继承设计，满足不同场景需求
- `CommonResult` 支持任意类型的数据封装

### 4. 便利性
- 提供 `success()`, `error()`, `empty()` 等静态工厂方法
- 提供 `getCheckedData()` 等辅助方法简化代码

---

## 技术栈

- **Jakarta Validation**: 3.x+（参数校验）
- **Swagger (OpenAPI)**: 3.x+（API 文档生成）
- **Lombok**: 1.18+（减少样板代码）
- **Jackson**: 2.15+（JSON 序列化）

---

## 参考资料

- [RESTful API 设计最佳实践](https://restfulapi.net/)
- [阿里巴巴 Java 开发手册](https://github.com/alibaba/p3c)
- [芋道源码官方文档](https://doc.iocoder.cn/)

---

**更新时间**: 2025-11-07
**维护者**: 芋道源码团队
