# 芋道 yudao-cloud 自定义校验注解

> 本文档详细介绍 `yudao-framework/yudao-common/validation` 包下的自定义校验注解及其使用方法

## 目录

- [1. 概述](#1-概述)
- [2. @InEnum - 枚举值校验](#2-inenum---枚举值校验)
- [3. @Mobile - 手机号校验](#3-mobile---手机号校验)
- [4. @Telephone - 电话号码校验](#4-telephone---电话号码校验)
- [5. 自定义校验注解开发指南](#5-自定义校验注解开发指南)
- [6. 最佳实践](#6-最佳实践)
- [7. 常见问题](#7-常见问题)

---

## 1. 概述

### 为什么需要自定义校验注解？

虽然 Jakarta Validation 提供了丰富的标准注解（如 `@NotNull`、`@Size`、`@Email` 等），但在实际业务开发中，我们经常遇到一些特定场景的校验需求：

| 标准注解的局限 | 自定义注解的优势 |
|---------------|-----------------|
| 无法校验业务枚举值 | `@InEnum` 自动校验枚举范围 |
| `@Pattern` 正则复杂难维护 | `@Mobile` 语义清晰，易于理解 |
| 需要重复编写校验逻辑 | 注解一次定义，到处复用 |
| 错误信息不够友好 | 自定义提示信息，提升用户体验 |

### 本包提供的校验注解

| 注解 | 说明 | 适用类型 |
|------|------|----------|
| `@InEnum` | 校验值是否在枚举范围内 | 单个值、集合 |
| `@Mobile` | 校验中国大陆手机号格式 | String |
| `@Telephone` | 校验电话号码（固定电话 + 手机） | String |

---

## 2. @InEnum - 枚举值校验

### 功能说明

`@InEnum` 注解用于校验字段值是否在指定枚举的有效范围内，解决了业务中常见的"枚举值校验"问题。

### 设计亮点

- ✅ **类型安全**：基于枚举类，避免硬编码魔法值
- ✅ **双模式支持**：支持单个值和集合（List、Set）
- ✅ **友好提示**：自动生成包含有效范围的错误信息
- ✅ **空值兼容**：null 值默认校验通过（配合 `@NotNull` 使用）

### 注解定义

```java
@Target({ElementType.FIELD, ElementType.PARAMETER, ...})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {InEnumValidator.class, InEnumCollectionValidator.class})
public @interface InEnum {
    /**
     * 枚举类（必须实现 ArrayValuable 接口）
     */
    Class<? extends ArrayValuable<?>> value();

    /**
     * 错误提示信息（{value} 会被替换为有效枚举值）
     */
    String message() default "必须在指定范围 {value}";

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

### 前置条件：实现 ArrayValuable 接口

枚举类必须实现 `ArrayValuable<T>` 接口，提供 `array()` 方法返回所有有效值。

#### 标准写法

```java
import cn.iocoder.yudao.framework.common.core.ArrayValuable;

/**
 * 用户状态枚举
 */
@Getter
@AllArgsConstructor
public enum UserStatusEnum implements ArrayValuable<Integer> {

    ENABLE(1, "启用"),
    DISABLE(2, "禁用"),
    DELETED(3, "已删除");

    /**
     * 状态码
     */
    private final Integer status;

    /**
     * 状态名
     */
    private final String name;

    /**
     * 返回所有有效的状态码
     */
    @Override
    public Integer[] array() {
        return Arrays.stream(values())
            .map(UserStatusEnum::getStatus)
            .toArray(Integer[]::new);
    }
}
```

#### 简化写法（使用 Hutool）

```java
@Getter
@AllArgsConstructor
public enum GenderEnum implements ArrayValuable<Integer> {

    MALE(1, "男"),
    FEMALE(2, "女"),
    UNKNOWN(0, "未知");

    private final Integer value;
    private final String label;

    /**
     * 使用 ArrayUtil.map 简化代码
     */
    @Override
    public Integer[] array() {
        return ArrayUtil.map(values(), Integer.class, GenderEnum::getValue);
    }
}
```

### 使用示例

#### 2.1 基础用法 - 单个值校验

```java
@Data
public class UserUpdateStatusReqVO {

    @NotNull(message = "用户 ID 不能为空")
    private Long id;

    @NotNull(message = "状态不能为空")
    @InEnum(value = UserStatusEnum.class, message = "用户状态必须是 {value}")
    private Integer status;
}
```

**校验效果**：

| 输入值 | 校验结果 | 错误信息 |
|--------|----------|----------|
| `1` | ✅ 通过 | - |
| `2` | ✅ 通过 | - |
| `3` | ✅ 通过 | - |
| `999` | ❌ 失败 | "用户状态必须是 [1, 2, 3]" |
| `null` | ✅ 通过 | （由 `@NotNull` 处理） |

#### 2.2 集合校验 - 多个值校验

```java
@Data
public class UserBatchUpdateReqVO {

    @NotEmpty(message = "用户 ID 列表不能为空")
    private List<Long> ids;

    @NotNull(message = "状态不能为空")
    @InEnum(value = UserStatusEnum.class, message = "状态列表必须在 {value} 范围内")
    private List<Integer> statuses;
}
```

**校验效果**：

```java
// ✅ 通过：所有值都在枚举范围内
statuses = [1, 2, 1, 3]

// ❌ 失败：包含无效值 999
statuses = [1, 2, 999]
// 错误信息："状态列表必须在 1,2,999 范围内"
```

#### 2.3 实战场景：订单状态流转

```java
/**
 * 订单状态枚举
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum implements ArrayValuable<Integer> {

    PENDING(0, "待支付"),
    PAID(1, "已支付"),
    SHIPPED(2, "已发货"),
    COMPLETED(3, "已完成"),
    CANCELLED(4, "已取消");

    private final Integer value;
    private final String label;

    @Override
    public Integer[] array() {
        return ArrayUtil.map(values(), Integer.class, OrderStatusEnum::getValue);
    }
}

// VO 定义
@Data
public class OrderUpdateReqVO {

    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;

    @NotNull(message = "订单状态不能为空")
    @InEnum(value = OrderStatusEnum.class, message = "订单状态不合法，有效值为 {value}")
    private Integer status;
}

// Controller
@PutMapping("/update-status")
public CommonResult<Void> updateOrderStatus(@Validated @RequestBody OrderUpdateReqVO reqVO) {
    orderService.updateStatus(reqVO.getOrderId(), reqVO.getStatus());
    return CommonResult.success(null);
}
```

#### 2.4 结合其他注解使用

```java
@Data
public class ProductSaveReqVO {

    private Long id;

    @NotBlank(message = "商品名称不能为空")
    @Length(max = 100, message = "商品名称不能超过 100 个字符")
    private String name;

    @NotNull(message = "商品分类不能为空")
    @InEnum(value = ProductCategoryEnum.class, message = "商品分类必须是 {value}")
    private Integer category;

    @NotNull(message = "商品状态不能为空")
    @InEnum(value = ProductStatusEnum.class, message = "商品状态必须是 {value}")
    private Integer status;

    @NotEmpty(message = "商品标签不能为空")
    @Size(max = 5, message = "商品标签最多 5 个")
    @InEnum(value = ProductTagEnum.class, message = "商品标签必须在 {value} 范围内")
    private List<Integer> tags;
}
```

### 校验器实现原理

#### InEnumValidator（单个值校验）

```java
public class InEnumValidator implements ConstraintValidator<InEnum, Object> {

    private List<?> values;

    @Override
    public void initialize(InEnum annotation) {
        // 获取枚举类的所有常量
        ArrayValuable<?>[] enums = annotation.value().getEnumConstants();
        // 调用 array() 方法获取有效值列表
        this.values = Arrays.asList(enums[0].array());
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        // null 值默认通过（交给 @NotNull 处理）
        if (value == null) {
            return true;
        }

        // 校验通过
        if (values.contains(value)) {
            return true;
        }

        // 校验失败，自定义错误信息（将 {value} 替换为实际枚举值）
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
            context.getDefaultConstraintMessageTemplate()
                .replaceAll("\\{value}", values.toString())
        ).addConstraintViolation();

        return false;
    }
}
```

#### InEnumCollectionValidator（集合校验）

```java
public class InEnumCollectionValidator implements ConstraintValidator<InEnum, Collection<?>> {

    private List<?> values;

    @Override
    public void initialize(InEnum annotation) {
        ArrayValuable<?>[] enums = annotation.value().getEnumConstants();
        this.values = Arrays.asList(enums[0].array());
    }

    @Override
    public boolean isValid(Collection<?> list, ConstraintValidatorContext context) {
        if (list == null) {
            return true;
        }

        // 校验集合中所有元素是否都在枚举范围内
        if (CollUtil.containsAll(values, list)) {
            return true;
        }

        // 校验失败，提示无效的值
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
            context.getDefaultConstraintMessageTemplate()
                .replaceAll("\\{value}", CollUtil.join(list, ","))
        ).addConstraintViolation();

        return false;
    }
}
```

---

## 3. @Mobile - 手机号校验

### 功能说明

`@Mobile` 注解用于校验中国大陆手机号格式，底层复用 `ValidationUtils.isMobile()` 方法。

### 注解定义

```java
@Target({ElementType.FIELD, ElementType.PARAMETER, ...})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MobileValidator.class)
public @interface Mobile {

    String message() default "手机号格式不正确";

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

### 使用示例

#### 3.1 基础用法

```java
@Data
public class UserRegisterReqVO {

    @NotBlank(message = "手机号不能为空")
    @Mobile(message = "手机号格式不正确")
    private String mobile;

    @NotBlank(message = "验证码不能为空")
    @Length(min = 4, max = 6, message = "验证码长度为 4-6 位")
    private String code;
}
```

#### 3.2 可选手机号（允许为空）

```java
@Data
public class UserUpdateReqVO {

    @NotNull(message = "用户 ID 不能为空")
    private Long id;

    // 手机号可选，但如果填写则必须合法
    @Mobile(message = "手机号格式不正确")
    private String mobile;

    @Email(message = "邮箱格式不正确")
    private String email;
}
```

#### 3.3 实战场景：绑定手机号

```java
@Data
public class UserBindMobileReqVO {

    @NotBlank(message = "手机号不能为空")
    @Mobile(message = "请输入正确的手机号")
    private String mobile;

    @NotBlank(message = "验证码不能为空")
    private String code;
}

@Service
public class UserServiceImpl implements UserService {

    @Override
    @Transactional
    public void bindMobile(UserBindMobileReqVO reqVO) {
        // 参数已经过 @Mobile 校验，这里无需再次校验格式

        // 校验验证码
        if (!smsCodeService.verify(reqVO.getMobile(), reqVO.getCode())) {
            throw new ServiceException(ErrorCodeConstants.SMS_CODE_ERROR);
        }

        // 检查手机号是否已被绑定
        if (userMapper.selectByMobile(reqVO.getMobile()) != null) {
            throw new ServiceException(ErrorCodeConstants.MOBILE_ALREADY_BOUND);
        }

        // 绑定手机号
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        userMapper.updateMobile(userId, reqVO.getMobile());
    }
}
```

### 校验器实现

```java
public class MobileValidator implements ConstraintValidator<Mobile, String> {

    @Override
    public void initialize(Mobile annotation) {
        // 无需初始化
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 空值默认通过（交给 @NotBlank 处理）
        if (StrUtil.isEmpty(value)) {
            return true;
        }
        // 调用 ValidationUtils 进行校验
        return ValidationUtils.isMobile(value);
    }
}
```

### 支持的手机号格式

| 格式 | 示例 | 是否支持 |
|------|------|----------|
| 标准 11 位 | `13812345678` | ✅ |
| 带 +86 前缀 | `+8613812345678` | ✅ |
| 带 0086 前缀 | `008613812345678` | ✅ |
| 带空格/中划线 | `138-1234-5678` | ❌ |
| 座机号码 | `010-12345678` | ❌（使用 @Telephone） |

---

## 4. @Telephone - 电话号码校验

### 功能说明

`@Telephone` 注解用于校验电话号码格式，支持：
- 中国大陆手机号（11 位）
- 固定电话号码（区号 + 号码，如 010-12345678）

### 注解定义

```java
@Target({ElementType.FIELD, ElementType.PARAMETER, ...})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TelephoneValidator.class)
public @interface Telephone {

    String message() default "电话格式不正确";

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

### 使用示例

#### 4.1 基础用法

```java
@Data
public class CompanyInfoReqVO {

    @NotBlank(message = "公司名称不能为空")
    private String companyName;

    @NotBlank(message = "联系电话不能为空")
    @Telephone(message = "请输入正确的联系电话（手机或座机）")
    private String telephone;

    @Email(message = "邮箱格式不正确")
    private String email;
}
```

#### 4.2 实战场景：用户资料

```java
@Data
public class UserProfileReqVO {

    private Long userId;

    @NotBlank(message = "姓名不能为空")
    private String name;

    // 手机号（必填）
    @NotBlank(message = "手机号不能为空")
    @Mobile(message = "手机号格式不正确")
    private String mobile;

    // 备用联系电话（可选，可以是座机或手机）
    @Telephone(message = "备用电话格式不正确")
    private String alternateTelephone;

    // 办公电话（可选，座机为主）
    @Telephone(message = "办公电话格式不正确")
    private String officeTelephone;
}
```

#### 4.3 企业信息场景

```java
@Data
public class EnterpriseInfoReqVO {

    @NotBlank(message = "企业名称不能为空")
    private String enterpriseName;

    // 企业联系电话（支持座机和手机）
    @NotBlank(message = "企业电话不能为空")
    @Telephone(message = "企业电话格式不正确")
    private String telephone;

    // 传真号（可选）
    @Telephone(message = "传真号格式不正确")
    private String fax;

    @NotBlank(message = "企业地址不能为空")
    private String address;
}
```

### 校验器实现

```java
public class TelephoneValidator implements ConstraintValidator<Telephone, String> {

    @Override
    public void initialize(Telephone annotation) {
        // 无需初始化
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 空值默认通过
        if (CharSequenceUtil.isEmpty(value)) {
            return true;
        }
        // 使用 Hutool 的 PhoneUtil 校验
        // isTel() - 校验固定电话
        // isPhone() - 校验手机号
        return PhoneUtil.isTel(value) || PhoneUtil.isPhone(value);
    }
}
```

### 支持的电话格式

| 类型 | 格式示例 | 是否支持 |
|------|----------|----------|
| 手机号 | `13812345678` | ✅ |
| 手机号（带区号） | `+8613812345678` | ✅ |
| 座机（带区号） | `010-12345678` | ✅ |
| 座机（不带区号） | `12345678` | ✅ |
| 座机（带分机） | `010-12345678-123` | ✅ |
| 400 电话 | `400-123-4567` | ✅ |
| 800 电话 | `800-123-4567` | ✅ |

---

## 5. 自定义校验注解开发指南

### 5.1 开发步骤

#### 步骤 1：定义注解

```java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = IdCardValidator.class) // 指定校验器
public @interface IdCard {

    String message() default "身份证号格式不正确";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
```

#### 步骤 2：实现校验器

```java
public class IdCardValidator implements ConstraintValidator<IdCard, String> {

    @Override
    public void initialize(IdCard annotation) {
        // 初始化逻辑（可选）
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 空值默认通过
        if (StrUtil.isEmpty(value)) {
            return true;
        }

        // 使用 Hutool 的身份证校验工具
        return IdcardUtil.isValidCard(value);
    }
}
```

#### 步骤 3：使用注解

```java
@Data
public class UserRealNameReqVO {

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    @NotBlank(message = "身份证号不能为空")
    @IdCard(message = "请输入正确的身份证号")
    private String idCard;
}
```

### 5.2 复杂场景：注解参数

```java
// 定义带参数的注解
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateRangeValidator.class)
public @interface DateRange {

    String message() default "日期范围不正确";

    /**
     * 最早日期（ISO 8601 格式，如 "2020-01-01"）
     */
    String min() default "";

    /**
     * 最晚日期
     */
    String max() default "";

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// 校验器实现
public class DateRangeValidator implements ConstraintValidator<DateRange, LocalDate> {

    private LocalDate minDate;
    private LocalDate maxDate;

    @Override
    public void initialize(DateRange annotation) {
        // 解析注解参数
        if (StrUtil.isNotEmpty(annotation.min())) {
            this.minDate = LocalDate.parse(annotation.min());
        }
        if (StrUtil.isNotEmpty(annotation.max())) {
            this.maxDate = LocalDate.parse(annotation.max());
        }
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        // 校验最小日期
        if (minDate != null && value.isBefore(minDate)) {
            return false;
        }

        // 校验最大日期
        if (maxDate != null && value.isAfter(maxDate)) {
            return false;
        }

        return true;
    }
}

// 使用
@Data
public class EventCreateReqVO {

    @NotNull(message = "活动日期不能为空")
    @DateRange(min = "2025-01-01", max = "2025-12-31", message = "活动日期必须在 2025 年内")
    private LocalDate eventDate;
}
```

### 5.3 类级别校验

```java
// 定义类级别注解
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordMatchValidator.class)
public @interface PasswordMatch {

    String message() default "两次密码输入不一致";

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// 校验器实现
public class PasswordMatchValidator implements ConstraintValidator<PasswordMatch, PasswordDTO> {

    @Override
    public void initialize(PasswordMatch annotation) {
    }

    @Override
    public boolean isValid(PasswordDTO dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }

        // 校验两次密码是否一致
        return Objects.equals(dto.getPassword(), dto.getConfirmPassword());
    }
}

// 使用
@Data
@PasswordMatch // 类级别注解
public class PasswordDTO {

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
```

---

## 6. 最佳实践

### 6.1 注解命名规范

```java
// ✅ 推荐：使用形容词或名词
@Mobile      // 形容词：移动的
@IdCard      // 名词：身份证
@InEnum      // 介词短语：在枚举中
@DateRange   // 名词短语：日期范围

// ❌ 不推荐：使用动词
@ValidateMobile
@CheckIdCard
```

### 6.2 空值处理策略

```java
// ✅ 推荐：空值默认通过，交给 @NotNull/@NotBlank 处理
@Override
public boolean isValid(String value, ConstraintValidatorContext context) {
    if (StrUtil.isEmpty(value)) {
        return true; // 空值通过
    }
    return doValidate(value);
}

// ❌ 不推荐：在自定义校验器中处理空值
@Override
public boolean isValid(String value, ConstraintValidatorContext context) {
    if (StrUtil.isEmpty(value)) {
        return false; // 职责混乱
    }
    return doValidate(value);
}
```

**原因**：
- 单一职责原则：`@NotNull` 负责非空校验，自定义注解负责格式校验
- 灵活性：可以单独使用 `@Mobile`（允许为空），也可以组合 `@NotBlank` + `@Mobile`（不允许为空）

### 6.3 错误信息友好化

```java
// ✅ 推荐：提供默认信息，支持占位符
@InEnum(value = UserStatusEnum.class, message = "用户状态必须是 {value}")
// 错误信息："用户状态必须是 [1, 2, 3]"

// ✅ 推荐：明确的提示
@Mobile(message = "请输入正确的手机号")

// ❌ 不推荐：模糊的提示
@Mobile(message = "格式错误")
@Mobile(message = "invalid format") // 英文提示
```

### 6.4 组合使用多个注解

```java
@Data
public class UserRegisterReqVO {

    // 先校验非空，再校验格式
    @NotBlank(message = "手机号不能为空")
    @Mobile(message = "手机号格式不正确")
    private String mobile;

    // 先校验非空，再校验长度，再校验强度
    @NotBlank(message = "密码不能为空")
    @Length(min = 6, max = 20, message = "密码长度为 6-20 位")
    @StrongPassword(message = "密码必须包含字母和数字")
    private String password;

    // 先校验非空，再校验枚举
    @NotNull(message = "性别不能为空")
    @InEnum(value = GenderEnum.class, message = "性别必须是 {value}")
    private Integer gender;
}
```

### 6.5 复用工具类

```java
// ✅ 推荐：复用已有工具类
public class MobileValidator implements ConstraintValidator<Mobile, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (StrUtil.isEmpty(value)) {
            return true;
        }
        return ValidationUtils.isMobile(value); // 复用工具类
    }
}

// ❌ 不推荐：重复实现校验逻辑
public class MobileValidator implements ConstraintValidator<Mobile, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (StrUtil.isEmpty(value)) {
            return true;
        }
        // 重复编写正则校验逻辑...
        return value.matches("^1[3-9]\\d{9}$");
    }
}
```

### 6.6 @InEnum 枚举设计规范

```java
// ✅ 推荐：枚举值使用 Integer/String，便于存储和传输
@Getter
@AllArgsConstructor
public enum OrderStatusEnum implements ArrayValuable<Integer> {
    PENDING(0, "待支付"),
    PAID(1, "已支付");

    private final Integer value; // 存储到数据库的值
    private final String label;  // 显示给用户的文本

    @Override
    public Integer[] array() {
        return ArrayUtil.map(values(), Integer.class, OrderStatusEnum::getValue);
    }
}

// ❌ 不推荐：使用 name() 或 ordinal()
@Override
public String[] array() {
    return Arrays.stream(values())
        .map(Enum::name)  // "PENDING", "PAID"（不稳定）
        .toArray(String[]::new);
}
```

---

## 7. 常见问题

### Q1: @Mobile 和 @Telephone 有什么区别？

**A**:

| 特性 | @Mobile | @Telephone |
|------|---------|-----------|
| 支持手机号 | ✅ | ✅ |
| 支持固定电话 | ❌ | ✅ |
| 支持 400/800 电话 | ❌ | ✅ |
| 使用场景 | 用户手机号、验证码发送 | 企业电话、客服电话、多类型联系方式 |

**选择建议**：
- 明确只需要手机号 → 使用 `@Mobile`
- 可能是座机或手机 → 使用 `@Telephone`

### Q2: 为什么 @InEnum 需要实现 ArrayValuable 接口？

**A**:
因为 Java 注解的 `value()` 参数只能是常量，无法直接获取枚举的所有值。

**技术原因**：
```java
// ❌ 无法实现：注解参数必须是编译期常量
@InEnum(values = {1, 2, 3}) // 每次都要手动维护

// ✅ 可行方案：通过接口动态获取
@InEnum(value = UserStatusEnum.class) // 枚举自动提供所有值
```

**ArrayValuable 接口的作用**：
1. 规范枚举类必须提供 `array()` 方法
2. 校验器通过反射调用 `array()` 获取有效值列表
3. 保持枚举定义和校验逻辑同步

### Q3: @InEnum 是否支持 String 类型？

**A**:
完全支持！`ArrayValuable<T>` 是泛型接口，支持任意类型。

```java
@Getter
@AllArgsConstructor
public enum LanguageEnum implements ArrayValuable<String> {

    ZH_CN("zh_CN", "简体中文"),
    EN_US("en_US", "英语");

    private final String code;
    private final String name;

    @Override
    public String[] array() {
        return ArrayUtil.map(values(), String.class, LanguageEnum::getCode);
    }
}

// 使用
@Data
public class UserSettingReqVO {
    @InEnum(value = LanguageEnum.class, message = "语言必须是 {value}")
    private String language; // "zh_CN" 或 "en_US"
}
```

### Q4: 校验失败的错误信息如何自定义？

**A**:

**方式 1：使用注解的 message 属性**

```java
@Mobile(message = "请输入正确的手机号")
private String mobile;

@InEnum(value = UserStatusEnum.class, message = "用户状态不合法，有效值：{value}")
private Integer status;
```

**方式 2：国际化消息**

```properties
# messages.properties
Mobile.message=手机号格式不正确
InEnum.message=必须在指定范围内：{value}
```

```java
@Mobile // 自动读取国际化配置
private String mobile;
```

### Q5: 如何在 Swagger 文档中显示校验注解？

**A**:
Swagger 3.x 会自动识别部分标准注解，自定义注解需要额外配置。

```java
// 使用 @Schema 描述字段
@Data
@Schema(description = "用户注册请求")
public class UserRegisterReqVO {

    @Schema(description = "手机号", example = "13812345678", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "手机号不能为空")
    @Mobile(message = "手机号格式不正确")
    private String mobile;

    @Schema(description = "用户状态", example = "1", allowableValues = {"1", "2", "3"})
    @NotNull(message = "状态不能为空")
    @InEnum(value = UserStatusEnum.class)
    private Integer status;
}
```

### Q6: @InEnum 集合校验时，如何校验集合大小？

**A**:
组合使用 `@Size` 和 `@InEnum`：

```java
@Data
public class BatchUpdateReqVO {

    @NotEmpty(message = "ID 列表不能为空")
    @Size(min = 1, max = 100, message = "单次最多处理 100 条数据")
    private List<Long> ids;

    @NotEmpty(message = "状态列表不能为空")
    @Size(max = 100, message = "状态列表最多 100 个元素")
    @InEnum(value = UserStatusEnum.class, message = "状态必须在 {value} 范围内")
    private List<Integer> statuses;
}
```

### Q7: 如何禁用某个字段的校验（测试场景）？

**A**:

**方式 1：使用分组校验**

```java
public interface TestGroup {}

@Data
public class UserCreateReqVO {
    @Mobile(groups = {Default.class}) // 默认分组才校验
    private String mobile;
}

// 测试时不校验
ValidationUtils.validate(user, TestGroup.class);
```

**方式 2：使用 @ConditionalOnProperty**

```java
@Configuration
@ConditionalOnProperty(name = "validation.enabled", havingValue = "true", matchIfMissing = true)
public class ValidationConfig {
    // 生产环境启用校验
}
```

---

## 附录：完整示例

### 用户管理模块

```java
// 枚举定义
@Getter
@AllArgsConstructor
public enum UserStatusEnum implements ArrayValuable<Integer> {
    ENABLE(1, "启用"),
    DISABLE(2, "禁用");

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return Arrays.stream(values())
            .map(UserStatusEnum::getStatus)
            .toArray(Integer[]::new);
    }
}

// 注册请求 VO
@Data
@Schema(description = "用户注册请求")
public class UserRegisterReqVO {

    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "手机号不能为空")
    @Mobile(message = "手机号格式不正确")
    private String mobile;

    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空")
    @Length(min = 6, max = 20, message = "密码长度为 6-20 位")
    private String password;

    @Schema(description = "验证码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "验证码不能为空")
    @Length(min = 4, max = 6, message = "验证码长度为 4-6 位")
    private String code;
}

// 更新请求 VO
@Data
@Schema(description = "用户更新请求")
public class UserUpdateReqVO {

    @Schema(description = "用户 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "用户 ID 不能为空")
    private Long id;

    @Schema(description = "用户状态", example = "1")
    @InEnum(value = UserStatusEnum.class, message = "用户状态必须是 {value}")
    private Integer status;

    @Schema(description = "备用电话")
    @Telephone(message = "备用电话格式不正确")
    private String alternateTelephone;
}

// Controller
@RestController
@RequestMapping("/users")
@Tag(name = "用户管理")
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public CommonResult<Long> register(@Validated @RequestBody UserRegisterReqVO reqVO) {
        Long userId = userService.register(reqVO);
        return CommonResult.success(userId);
    }

    @PutMapping("/update")
    @Operation(summary = "更新用户")
    public CommonResult<Void> update(@Validated @RequestBody UserUpdateReqVO reqVO) {
        userService.update(reqVO);
        return CommonResult.success(null);
    }
}
```

---

## 技术栈

- **Jakarta Validation**: 3.0+（Bean Validation 规范）
- **Hibernate Validator**: 8.0+（参考实现）
- **Hutool**: 5.x+（工具库，PhoneUtil）

---

## 参考资料

- [Jakarta Validation 官方文档](https://jakarta.ee/specifications/bean-validation/)
- [Hibernate Validator 文档](https://hibernate.org/validator/)
- [Hutool PhoneUtil 文档](https://hutool.cn/docs/#/core/工具类/手机号工具-PhoneUtil)

---

**更新时间**: 2025-11-07
**维护者**: 芋道源码团队
