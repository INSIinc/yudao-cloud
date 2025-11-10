# 芋道 yudao-cloud 通用工具类库

> 本文档详细介绍 `yudao-framework/yudao-common/util` 包下的所有工具类及其使用方法

## 目录

- [1. cache - 缓存工具](#1-cache---缓存工具)
- [2. collection - 集合工具](#2-collection---集合工具)
- [3. date - 日期时间工具](#3-date---日期时间工具)
- [4. http - HTTP 工具](#4-http---http-工具)
- [5. io - IO 工具](#5-io---io-工具)
- [6. json - JSON 工具](#6-json---json-工具)
- [7. monitor - 监控工具](#7-monitor---监控工具)
- [8. number - 数字工具](#8-number---数字工具)
- [9. object - 对象工具](#9-object---对象工具)
- [10. servlet - Servlet 工具](#10-servlet---servlet-工具)
- [11. spring - Spring 工具](#11-spring---spring-工具)
- [12. string - 字符串工具](#12-string---字符串工具)
- [13. validation - 校验工具](#13-validation---校验工具)

---

## 1. cache - 缓存工具

### CacheUtils

基于 Guava Cache 的本地缓存工具类，提供异步刷新和同步刷新两种模式。

#### 主要方法

| 方法 | 说明 | 适用场景 |
|------|------|----------|
| `buildAsyncReloadingCache(Duration, CacheLoader)` | 构建异步刷新的 LoadingCache | 全局/系统级缓存，与 ThreadLocal 无关 |
| `buildCache(Duration, CacheLoader)` | 构建同步刷新的 LoadingCache | 与用户/ThreadLocal 相关的缓存 |

#### 使用示例

```java
// 异步刷新缓存（适合全局配置缓存）
LoadingCache<Long, User> userCache = CacheUtils.buildAsyncReloadingCache(
    Duration.ofMinutes(5),
    key -> userMapper.selectById(key)
);

// 同步刷新缓存（适合用户级缓存）
LoadingCache<String, List<Permission>> permissionCache = CacheUtils.buildCache(
    Duration.ofMinutes(10),
    userId -> permissionService.getByUserId(userId)
);
```

---

## 2. collection - 集合工具

### CollectionUtils

核心集合处理工具类，提供丰富的集合操作方法。

#### 判断类方法

| 方法 | 说明 | 示例 |
|------|------|------|
| `containsAny(source, targets)` | 判断元素是否在数组中 | `containsAny(1, new Integer[]{1,2,3})` |
| `isAnyEmpty(collections...)` | 判断多个集合是否有任意一个为空 | `isAnyEmpty(list1, list2)` |
| `anyMatch(collection, predicate)` | 判断是否存在满足条件的元素 | `anyMatch(users, u -> u.getAge() > 18)` |

#### 过滤与去重

| 方法 | 说明 | 示例 |
|------|------|------|
| `filterList(from, predicate)` | 过滤集合 | `filterList(users, u -> u.isActive())` |
| `distinct(from, keyMapper)` | 根据 key 去重 | `distinct(users, User::getId)` |

#### 转换类方法

**转换为 List**

```java
// 基础转换
List<Long> ids = CollectionUtils.convertList(users, User::getId);

// 带过滤的转换
List<String> activeUserNames = CollectionUtils.convertList(
    users,
    User::getName,
    user -> user.isActive()
);

// FlatMap 转换（一对多）
List<String> allTags = CollectionUtils.convertListByFlatMap(
    articles,
    article -> article.getTags().stream()
);
```

**转换为 Set**

```java
Set<Long> userIds = CollectionUtils.convertSet(users, User::getId);
```

**转换为 Map**

```java
// Key 为 ID，Value 为对象本身
Map<Long, User> userMap = CollectionUtils.convertMap(users, User::getId);

// 自定义 Key 和 Value
Map<Long, String> idNameMap = CollectionUtils.convertMap(
    users,
    User::getId,
    User::getName
);

// 一对多 Map（Key -> List）
Map<Long, List<Order>> userOrdersMap = CollectionUtils.convertMultiMap(
    orders,
    Order::getUserId
);

// 一对多 Map（Key -> Set，去重）
Map<Long, Set<String>> userTagsMap = CollectionUtils.convertMultiMap2(
    userTags,
    UserTag::getUserId,
    UserTag::getTag
);
```

**转换分页结果**

```java
PageResult<UserVO> voPage = CollectionUtils.convertPage(
    userPage,
    user -> BeanUtils.toBean(user, UserVO.class)
);
```

#### 查找类方法

| 方法 | 说明 | 示例 |
|------|------|------|
| `getFirst(list)` | 获取第一个元素 | `getFirst(users)` |
| `findFirst(collection, predicate)` | 查找第一个满足条件的元素 | `findFirst(users, u -> u.getAge() > 18)` |
| `getMaxValue(collection, valueFunc)` | 获取最大值 | `getMaxValue(users, User::getAge)` |
| `getMinValue(collection, valueFunc)` | 获取最小值 | `getMinValue(orders, Order::getAmount)` |

#### 聚合与比较

```java
// 求和
Integer totalAmount = CollectionUtils.getSumValue(
    orders,
    Order::getAmount,
    Integer::sum
);

// 比较新旧列表差异，返回 [新增, 修改, 删除]
List<List<User>> diff = CollectionUtils.diffList(
    oldUsers,
    newUsers,
    (o1, o2) -> o1.getId().equals(o2.getId())
);
List<User> createList = diff.get(0);
List<User> updateList = diff.get(1);
List<User> deleteList = diff.get(2);
```

### ArrayUtils

数组操作工具类。

| 方法 | 说明 |
|------|------|
| `append(object, newElements)` | 合并数组 |
| `toArray(collection)` | 集合转数组 |
| `get(array, index)` | 安全获取数组元素（越界返回 null） |

### SetUtils

快速创建 Set。

```java
Set<String> roles = SetUtils.asSet("admin", "user", "guest");
```

### MapUtils

Map 操作工具。

```java
// 从 Multimap 获取多个 key 的所有 value
List<String> values = MapUtils.getList(multimap, Arrays.asList(key1, key2));

// 查找并处理
MapUtils.findAndThen(map, "userId", userId -> {
    System.out.println("User ID: " + userId);
});
```

---

## 3. date - 日期时间工具

### DateUtils

传统 Date 工具类。

| 方法 | 说明 | 示例 |
|------|------|------|
| `of(LocalDateTime)` | LocalDateTime 转 Date | `Date date = DateUtils.of(localDateTime)` |
| `of(Date)` | Date 转 LocalDateTime | `LocalDateTime ldt = DateUtils.of(date)` |
| `addTime(Duration)` | 当前时间加上指定时长 | `Date future = DateUtils.addTime(Duration.ofHours(2))` |
| `isExpired(LocalDateTime)` | 判断是否已过期 | `boolean expired = DateUtils.isExpired(endTime)` |
| `buildTime(year, month, day)` | 构建指定日期 | `Date date = DateUtils.buildTime(2025, 11, 7)` |
| `max(Date, Date)` | 返回较晚的时间 | `Date latest = DateUtils.max(date1, date2)` |
| `isToday(LocalDateTime)` | 判断是否今天 | `boolean isToday = DateUtils.isToday(time)` |
| `isYesterday(LocalDateTime)` | 判断是否昨天 | `boolean isYesterday = DateUtils.isYesterday(time)` |

### LocalDateTimeUtils

现代化的日期时间工具类（推荐使用）。

#### 时间解析与构建

```java
// 智能解析时间字符串
LocalDateTime time = LocalDateTimeUtils.parse("2025-11-07 14:30:00");

// 构建指定时间
LocalDateTime time = LocalDateTimeUtils.buildTime(2025, 11, 7);
```

#### 时间计算

```java
// 加减时间
LocalDateTime future = LocalDateTimeUtils.addTime(Duration.ofHours(2));
LocalDateTime past = LocalDateTimeUtils.minusTime(Duration.ofDays(1));

// 判断时间关系
boolean isBefore = LocalDateTimeUtils.beforeNow(someTime);
boolean isAfter = LocalDateTimeUtils.afterNow(someTime);
```

#### 时间范围判断

```java
// 判断是否在时间范围内
boolean inRange = LocalDateTimeUtils.isBetween(startTime, endTime, checkTime);

// 判断当前时间是否在范围内
boolean inRange = LocalDateTimeUtils.isBetween(startTime, endTime);

// 判断时间段是否重叠
boolean overlap = LocalDateTimeUtils.isOverlap(
    start1, end1,
    start2, end2
);
```

#### 月份与季度

```java
// 获取月初
LocalDateTime monthStart = LocalDateTimeUtils.beginOfMonth(LocalDateTime.now());

// 获取月末
LocalDateTime monthEnd = LocalDateTimeUtils.endOfMonth(LocalDateTime.now());

// 获取所在季度
int quarter = LocalDateTimeUtils.getQuarterOfYear(LocalDateTime.now());
```

#### 快捷时间获取

```java
LocalDateTime today = LocalDateTimeUtils.getToday();      // 今天 00:00:00
LocalDateTime yesterday = LocalDateTimeUtils.getYesterday(); // 昨天 00:00:00
LocalDateTime monthStart = LocalDateTimeUtils.getMonth();    // 本月初
LocalDateTime yearStart = LocalDateTimeUtils.getYear();      // 本年初
```

#### 生成时间范围列表

```java
// 按天生成时间范围
List<LocalDateTime[]> ranges = LocalDateTimeUtils.getDateRangeList(
    startTime,
    endTime,
    DateIntervalEnum.DAY.getInterval()
);

// 按月生成时间范围
List<LocalDateTime[]> monthlyRanges = LocalDateTimeUtils.getDateRangeList(
    startTime,
    endTime,
    DateIntervalEnum.MONTH.getInterval()
);
```

#### Unix 时间戳转换

```java
Long epochSecond = LocalDateTimeUtils.toEpochSecond(LocalDateTime.now());
```

---

## 4. http - HTTP 工具

### HttpUtils

HTTP 请求与 URL 处理工具。

#### URL 处理

```java
// URL 参数编码
String encoded = HttpUtils.encodeUtf8("芋道源码");

// 替换 URL 查询参数
String newUrl = HttpUtils.replaceUrlQuery(
    "https://example.com?foo=bar",
    "foo",
    "newValue"
);

// 移除 URL 查询参数和 fragment
String cleanUrl = HttpUtils.removeUrlQuery("https://example.com?foo=bar#section");
```

#### HTTP 请求

```java
// POST 请求（带自定义 headers）
Map<String, String> headers = new HashMap<>();
headers.put("Authorization", "Bearer token123");
String response = HttpUtils.post(url, headers, requestBody);

// GET 请求（带自定义 headers）
String response = HttpUtils.get(url, headers);
```

#### Basic 认证解析

```java
// 从 HttpServletRequest 中获取 Basic 认证信息
String[] auth = HttpUtils.obtainBasicAuthorization(request);
String clientId = auth[0];
String clientSecret = auth[1];
```

---

## 5. io - IO 工具

### IoUtils

```java
// 从输入流读取 UTF-8 内容
String content = IoUtils.readUtf8(inputStream, true);
```

### FileUtils

临时文件管理工具。

```java
// 创建临时文件（JVM 退出时自动删除）
File tempFile = FileUtils.createTempFile("Hello World");

// 创建临时文件（字节数组）
byte[] data = "binary data".getBytes();
File tempFile = FileUtils.createTempFile(data);

// 创建空临时文件
File tempFile = FileUtils.createTempFile();
```

---

## 6. json - JSON 工具

### JsonUtils

基于 Jackson 的 JSON 序列化/反序列化工具。

#### 序列化

```java
// 对象转 JSON 字符串
String json = JsonUtils.toJsonString(user);

// 对象转 JSON 字节数组
byte[] jsonBytes = JsonUtils.toJsonByte(user);

// 格式化输出（带缩进）
String prettyJson = JsonUtils.toJsonPrettyString(user);
```

#### 反序列化

```java
// JSON 字符串转对象
User user = JsonUtils.parseObject(json, User.class);

// JSON 字符串转列表
List<User> users = JsonUtils.parseArray(json, User.class);

// 使用 TypeReference（复杂泛型）
Map<String, List<User>> data = JsonUtils.parseObject(
    json,
    new TypeReference<Map<String, List<User>>>() {}
);

// 从 JSON 路径中提取对象
User user = JsonUtils.parseObject(json, "data.user", User.class);
```

#### 判断与解析

```java
// 判断是否为 JSON 格式
boolean isJson = JsonUtils.isJson(text);
boolean isJsonObj = JsonUtils.isJsonObject(text);

// 解析为 JsonNode（AST）
JsonNode node = JsonUtils.parseTree(json);
```

---

## 7. monitor - 监控工具

### TracerUtils

链路追踪工具（集成 SkyWalking）。

```java
// 获取当前链路追踪 ID
String traceId = TracerUtils.getTraceId();
log.info("TraceId: {}", traceId);
```

---

## 8. number - 数字工具

### MoneyUtils

金额计算工具类，所有金额单位为**分**。

#### 百分比计算

```java
// 计算百分比金额（四舍五入）
Integer discountPrice = MoneyUtils.calculateRatePrice(10000, 85.5); // 8550 分

// 向下取整
Integer floorPrice = MoneyUtils.calculateRatePriceFloor(10000, 85.5); // 8550 分

// 计算商品总价：价格 × 数量 × 折扣
Integer totalPrice = MoneyUtils.calculator(100, 5, 8000); // 100分 × 5个 × 80% = 400分
```

#### 单位转换

```java
// 分转元（BigDecimal）
BigDecimal yuan = MoneyUtils.fenToYuan(12345); // 123.45

// 分转元（字符串）
String yuanStr = MoneyUtils.fenToYuanStr(12345); // "123.45"
```

#### 高精度计算

```java
// 金额相乘
BigDecimal total = MoneyUtils.priceMultiply(
    new BigDecimal("99.99"),
    new BigDecimal("3")
); // 299.97

// 金额乘以百分比
BigDecimal discounted = MoneyUtils.priceMultiplyPercent(
    new BigDecimal("100.00"),
    new BigDecimal("85")
); // 85.00
```

### NumberUtils

通用数字处理工具。

#### 安全转换

```java
// 字符串转 Long（为空返回 null）
Long id = NumberUtils.parseLong("123");

// 字符串转 Integer
Integer count = NumberUtils.parseInt("456");
```

#### 校验

```java
// 判断列表中所有元素是否都是数字
List<String> values = Arrays.asList("123", "45.6", "-78");
boolean allNumber = NumberUtils.isAllNumber(values); // true
```

#### 地理距离计算

```java
// 计算两点间距离（单位：千米）
// 注意：参数顺序为 经度1, 纬度1, 经度2, 纬度2
double distance = NumberUtils.getDistance(
    116.4074, 39.9042,  // 北京
    121.4737, 31.2304   // 上海
); // 约 1067.5 km
```

#### 高精度乘法

```java
// null 安全的 BigDecimal 乘法
BigDecimal result = NumberUtils.mul(price, quantity, rate);
// 任一参数为 null 则返回 null
```

---

## 9. object - 对象工具

### BeanUtils

Bean 属性复制与转换工具（基于 Hutool）。

#### 对象转换

```java
// 单个对象转换
UserVO userVO = BeanUtils.toBean(userDO, UserVO.class);

// 转换后执行额外操作
UserVO userVO = BeanUtils.toBean(userDO, UserVO.class, vo -> {
    vo.setExtra("额外信息");
});
```

#### 列表转换

```java
// 列表转换
List<UserVO> voList = BeanUtils.toBean(userDOList, UserVO.class);

// 转换后对每个元素执行操作
List<UserVO> voList = BeanUtils.toBean(userDOList, UserVO.class, vo -> {
    vo.setTimestamp(System.currentTimeMillis());
});
```

#### 分页结果转换

```java
// 分页结果转换（保留总条数）
PageResult<UserVO> voPage = BeanUtils.toBean(userPage, UserVO.class);

// 带后处理
PageResult<UserVO> voPage = BeanUtils.toBean(userPage, UserVO.class, vo -> {
    vo.enrichData();
});
```

#### 属性拷贝

```java
// 将源对象属性复制到目标对象（修改已有对象）
BeanUtils.copyProperties(sourceUser, targetUser);
```

### ObjectUtils

通用对象工具。

```java
// 克隆对象并忽略 ID
User cloned = ObjectUtils.cloneIgnoreId(original, user -> {
    user.setCreatedTime(LocalDateTime.now());
});

// 返回两个 Comparable 对象中较大的
Integer max = ObjectUtils.max(100, 200); // 200

// 返回第一个非 null 的值
String value = ObjectUtils.defaultIfNull(null, null, "default"); // "default"

// 判断对象是否等于数组中任意一个
boolean matches = ObjectUtils.equalsAny("admin", "admin", "user", "guest"); // true

// 判断是否不全为空
boolean notAllEmpty = ObjectUtils.isNotAllEmpty(null, "value", null); // true
```

### PageUtils

分页工具。

```java
// 计算分页起始位置
int start = PageUtils.getStart(pageParam); // (pageNo - 1) * pageSize

// 构建排序字段（默认倒序）
SortingField sortField = PageUtils.buildSortingField(UserDO::getCreateTime);

// 构建排序字段（指定顺序）
SortingField sortField = PageUtils.buildSortingField(
    UserDO::getCreateTime,
    SortingField.ORDER_ASC
);

// 设置默认排序（仅当排序字段为空时）
PageUtils.buildDefaultSortingField(pageParam, UserDO::getCreateTime);
```

---

## 10. servlet - Servlet 工具

### ServletUtils

Servlet 请求响应处理工具。

#### 响应处理

```java
// 向客户端返回 JSON
ServletUtils.writeJSON(response, resultObject);
```

#### 请求信息获取

```java
// 获取当前请求
HttpServletRequest request = ServletUtils.getRequest();

// 获取 User-Agent
String ua = ServletUtils.getUserAgent(request);
String ua = ServletUtils.getUserAgent(); // 从当前请求获取

// 获取客户端 IP
String ip = ServletUtils.getClientIP(request);
String ip = ServletUtils.getClientIP(); // 从当前请求获取
```

#### 请求体与参数

```java
// 判断是否为 JSON 请求
boolean isJson = ServletUtils.isJsonRequest(request);

// 获取请求体（仅 JSON 请求）
String body = ServletUtils.getBody(request);
byte[] bodyBytes = ServletUtils.getBodyBytes(request);

// 获取参数 Map
Map<String, String> params = ServletUtils.getParamMap(request);

// 获取请求头 Map
Map<String, String> headers = ServletUtils.getHeaderMap(request);
```

---

## 11. spring - Spring 工具

### SpringUtils

Spring 容器工具（继承 Hutool 的 SpringUtil）。

```java
// 判断是否为生产环境
boolean isProd = SpringUtils.isProd();

// 继承 SpringUtil 的所有方法
ApplicationContext context = SpringUtils.getApplicationContext();
UserService userService = SpringUtils.getBean(UserService.class);
```

### SpringExpressionUtils

Spring EL 表达式解析工具。

#### 从切面解析

```java
// 在 AOP 切面中解析单个表达式
@Around("@annotation(operateLog)")
public Object around(ProceedingJoinPoint joinPoint, OperateLog operateLog) {
    String userIdExpr = "#user.id";
    Object userId = SpringExpressionUtils.parseExpression(joinPoint, userIdExpr);
    // ...
}

// 批量解析多个表达式
List<String> expressions = Arrays.asList("#user.id", "#user.name");
Map<String, Object> results = SpringExpressionUtils.parseExpressions(joinPoint, expressions);
Long userId = (Long) results.get("#user.id");
```

#### 从 Bean 工厂解析

```java
// 解析简单表达式
Object result = SpringExpressionUtils.parseExpression("@userService.getById(1)");

// 带变量的表达式
Map<String, Object> variables = new HashMap<>();
variables.put("userId", 123L);
Object result = SpringExpressionUtils.parseExpression("@userService.getById(#userId)", variables);
```

---

## 12. string - 字符串工具

### StrUtils

字符串处理工具。

#### 字符串截断

```java
// 限制最大长度（超出显示 ...）
String short = StrUtils.maxLength("这是一段很长的文本", 10); // "这是一段很..."
```

#### 字符串判断

```java
// 判断是否以任意字符串开头
Collection<String> prefixes = Arrays.asList("/api/", "/admin/");
boolean matches = StrUtils.startWithAny("/api/users", prefixes); // true
```

#### 字符串分割

```java
// 分割为 Long 列表
List<Long> ids = StrUtils.splitToLong("1,2,3,4", ",");

// 分割为 Long Set（去重）
Set<Long> idSet = StrUtils.splitToLongSet("1,2,3,2,1"); // [1,2,3]
Set<Long> idSet = StrUtils.splitToLongSet("1;2;3", ";");

// 分割为 Integer 列表
List<Integer> numbers = StrUtils.splitToInteger("10,20,30", ",");
```

#### 行处理

```java
// 移除包含指定字符串的行
String cleaned = StrUtils.removeLineContains(content, "DEBUG");
```

#### AOP 方法参数拼接

```java
// 在 AOP 中拼接方法参数（自动过滤 Servlet 对象）
@Around("execution(* com.example..*.*(..))")
public Object around(ProceedingJoinPoint joinPoint) {
    String args = StrUtils.joinMethodArgs(joinPoint);
    log.info("Method args: {}", args);
    // ...
}
```

---

## 13. validation - 校验工具

### ValidationUtils

数据校验工具。

#### 正则校验

```java
// 手机号校验
boolean valid = ValidationUtils.isMobile("13812345678"); // true

// URL 校验
boolean valid = ValidationUtils.isURL("https://example.com"); // true

// XML NCName 校验（标识符命名规则）
boolean valid = ValidationUtils.isXmlNCName("valid_name123"); // true
```

#### 手动触发 Bean Validation

```java
// 手动校验对象
User user = new User();
ValidationUtils.validate(user); // 抛出 ConstraintViolationException

// 分组校验
ValidationUtils.validate(user, CreateGroup.class, UpdateGroup.class);

// 使用自定义 Validator
Validator validator = getValidator();
ValidationUtils.validate(validator, user);
```

---

## 最佳实践

### 1. 集合转换优先使用 CollectionUtils

```java
// ❌ 不推荐：手动 for 循环
List<Long> ids = new ArrayList<>();
for (User user : users) {
    ids.add(user.getId());
}

// ✅ 推荐：使用 CollectionUtils
List<Long> ids = CollectionUtils.convertList(users, User::getId);
```

### 2. 使用 LocalDateTime 而非 Date

```java
// ❌ 不推荐：使用旧 Date API
Date now = new Date();

// ✅ 推荐：使用 LocalDateTime
LocalDateTime now = LocalDateTime.now();
```

### 3. 金额计算使用 MoneyUtils

```java
// ❌ 不推荐：直接使用浮点数
double total = price * 0.85; // 精度问题

// ✅ 推荐：使用 MoneyUtils
Integer total = MoneyUtils.calculateRatePrice(price, 85.0);
```

### 4. Bean 转换使用 BeanUtils

```java
// ❌ 不推荐：手动 set
UserVO vo = new UserVO();
vo.setId(user.getId());
vo.setName(user.getName());
// ...

// ✅ 推荐：使用 BeanUtils
UserVO vo = BeanUtils.toBean(user, UserVO.class);
```

### 5. JSON 处理使用 JsonUtils

```java
// ❌ 不推荐：直接使用 Jackson ObjectMapper
ObjectMapper mapper = new ObjectMapper();
String json = mapper.writeValueAsString(user);

// ✅ 推荐：使用 JsonUtils
String json = JsonUtils.toJsonString(user);
```

---

## 注意事项

1. **null 安全**：所有工具类方法都已做 null 检查，可以放心传入 null 值
2. **性能考虑**：大批量数据处理时注意内存占用，必要时使用流式处理
3. **时区问题**：DateUtils 默认使用系统时区，跨时区场景需特别注意
4. **金额计算**：MoneyUtils 单位为分，避免精度问题
5. **缓存使用**：CacheUtils 需根据场景选择异步或同步刷新模式

---

## 技术栈

- **Hutool**: 5.x+（底层工具库）
- **Guava**: 31.x+（缓存、集合工具）
- **Jackson**: 2.15+（JSON 处理）
- **Spring Framework**: 6.x+（表达式、工具类）
- **SkyWalking**: 9.x+（链路追踪）

---

## 许可证

芋道 yudao-cloud 项目，遵循 MIT License。

---

**更新时间**: 2025-11-07
**维护者**: 芋道源码团队
