# yudao-spring-boot-starter-excel

## 📖 模块简介

`yudao-spring-boot-starter-excel` 是芋道项目的 Excel 处理增强模块，基于 [FastExcel](https://github.com/dromara/fast-excel) 封装，提供了开箱即用的 Excel 导入导出功能，并集成了字典转换、数据验证、下拉框等实用特性。

## ✨ 功能特性

### 1. Excel 导入导出
- 🚀 简化的 API 调用，一行代码完成导入导出
- 📊 自动适配列宽，优化显示效果
- 🔢 Long 类型精度保护
- 📝 支持自定义列宽策略

### 2. 字典转换
- 🔄 **自动字典转换**：导出时自动将字典值转换为标签（如：1 → "男"）
- 📥 **导入自动识别**：导入时自动将标签转换为字典值（如："男" → 1）
- 💾 **缓存机制**：内置缓存，提升字典查询性能
- 🎯 **@DictFormat 注解**：简单标注即可实现字典转换

### 3. 数据下拉框
- 📋 **Excel 下拉选择**：支持在 Excel 列中添加下拉选项
- 🔗 **字典数据源**：可从字典数据自动生成下拉选项
- ⚙️ **自定义数据源**：支持通过方法名自定义下拉数据
- 🎨 **@ExcelColumnSelect 注解**：声明式配置下拉框

### 4. 数据类型转换
- 💰 **金额转换器**（MoneyConvert）：分 ↔️ 元自动转换
- 🌍 **地区转换器**（AreaConvert）：地区编码 ↔️ 地区名称
- 📦 **JSON 转换器**（JsonConvert）：对象 ↔️ JSON 字符串

### 5. 数据验证
- ✅ **@InDict 注解**：验证字段值是否在指定字典范围内
- 🔍 **支持单值和集合**：可验证单个值或集合中的所有值
- ⚠️ **友好错误提示**：验证失败时提供清晰的错误信息

## 🔧 技术栈

- **FastExcel**：高性能 Excel 处理库
- **Spring Boot**：自动配置
- **Guava Cache**：字典数据缓存
- **Jakarta Validation**：数据验证

## 📦 Maven 依赖

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-excel</artifactId>
</dependency>
```

## 🚀 快速开始

### 1. 基础导出

```java
@RestController
@RequestMapping("/user")
public class UserController {
    
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        // 查询数据
        List<UserExcelVO> list = userService.getUserList();
        
        // 导出 Excel
        ExcelUtils.write(response, "用户列表.xlsx", "用户数据", UserExcelVO.class, list);
    }
}
```

### 2. 基础导入

```java
@PostMapping("/import")
public void importUsers(@RequestParam("file") MultipartFile file) throws IOException {
    // 读取 Excel
    List<UserExcelVO> list = ExcelUtils.read(file, UserExcelVO.class);
    
    // 批量保存
    userService.batchSave(list);
}
```

### 3. 定义 Excel 实体类

```java
@Data
public class UserExcelVO {
    
    @ExcelProperty("用户名")
    private String username;
    
    @ExcelProperty("性别")
    @DictFormat("sys_user_sex") // 字典转换
    private Integer sex;
    
    @ExcelProperty("状态")
    @ExcelColumnSelect(dictType = "sys_normal_disable") // 下拉选择
    private Integer status;
    
    @ExcelProperty("余额")
    @NumberFormat("#.##") // 数字格式化
    private BigDecimal balance;
    
    @ExcelProperty(value = "创建时间")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
```

## 📘 核心功能详解

### 字典转换 @DictFormat

使用字典转换可以让 Excel 中显示更友好的文本，而不是数字代码。

```java
@ExcelProperty("性别")
@DictFormat("sys_user_sex") // 指定字典类型
private Integer sex;
```

**导出效果**：
- 数据库存储：`1`
- Excel 显示：`男`

**导入效果**：
- Excel 填写：`男`
- 数据库存储：`1`

### 下拉框 @ExcelColumnSelect

为 Excel 列添加下拉选择框，提升数据录入的准确性。

#### 方式一：使用字典数据

```java
@ExcelProperty("状态")
@ExcelColumnSelect(dictType = "sys_normal_disable")
private Integer status;
```

#### 方式二：自定义数据源

```java
@ExcelProperty("部门")
@ExcelColumnSelect(functionName = "getDeptList")
private Long deptId;

// 需要在类中实现对应的静态方法
public static List<String> getDeptList() {
    return Arrays.asList("研发部", "市场部", "财务部");
}
```

### 金额转换 MoneyConvert

自动将以"分"为单位的金额转换为"元"显示。

```java
@ExcelProperty(value = "订单金额", converter = MoneyConvert.class)
private Integer amount; // 数据库存储：10000（分）→ Excel 显示：100.00（元）
```

### 地区转换 AreaConvert

将地区编码转换为地区名称。

```java
@ExcelProperty(value = "所在地区", converter = AreaConvert.class)
private Integer areaId; // 110000 → "北京市"
```

### 数据验证 @InDict

确保用户输入的值在字典范围内。

```java
@InDict(type = "sys_user_sex", message = "性别值不正确")
private Integer sex;

@InDict(type = "sys_user_status")
private List<Integer> statusList; // 支持集合验证
```

## 🛠️ 高级配置

### 自定义列宽策略

```java
FastExcelFactory.write(response.getOutputStream(), UserExcelVO.class)
    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy()) // 最长匹配
    .registerWriteHandler(new ColumnWidthMatchStyleStrategy()) // 自定义策略（最大255）
    .sheet("用户数据")
    .doWrite(dataList);
```

### 字典工具类

```java
// 根据字典值获取标签
String label = DictFrameworkUtils.parseDictDataLabel("sys_user_sex", 1); // 返回 "男"

// 根据字典标签获取值
Integer value = DictFrameworkUtils.parseDictDataValue("sys_user_sex", "男"); // 返回 1

// 获取字典类型的所有标签
List<String> labels = DictFrameworkUtils.getDictDataLabelList("sys_user_sex"); // ["男", "女"]

// 清空缓存（字典数据变更后调用）
DictFrameworkUtils.clearCache();
```

## 📂 模块结构

```
yudao-spring-boot-starter-excel
├── src/main/java
│   └── cn.iocoder.yudao.framework
│       ├── dict                           # 字典相关
│       │   ├── config                     # 字典自动配置
│       │   │   ├── YudaoDictAutoConfiguration.java
│       │   │   └── YudaoDictRpcAutoConfiguration.java
│       │   ├── core                       # 字典核心工具
│       │   │   └── DictFrameworkUtils.java
│       │   └── validation                 # 字典验证注解
│       │       ├── InDict.java
│       │       ├── InDictValidator.java
│       │       └── InDictCollectionValidator.java
│       └── excel                          # Excel 相关
│           └── core
│               ├── annotations            # 注解
│               │   ├── DictFormat.java            # 字典格式化注解
│               │   └── ExcelColumnSelect.java     # 下拉框注解
│               ├── convert                # 转换器
│               │   ├── DictConvert.java           # 字典转换器
│               │   ├── MoneyConvert.java          # 金额转换器
│               │   ├── AreaConvert.java           # 地区转换器
│               │   └── JsonConvert.java           # JSON 转换器
│               ├── handler                # 处理器
│               │   ├── SelectSheetWriteHandler.java        # 下拉框写入处理器
│               │   └── ColumnWidthMatchStyleStrategy.java  # 列宽策略
│               ├── function               # 函数接口
│               │   └── ExcelColumnSelectFunction.java
│               └── util                   # 工具类
│                   └── ExcelUtils.java            # Excel 工具类
└── pom.xml
```

## 🎯 使用场景

1. **用户数据导入导出**：批量导入用户信息，导出用户列表
2. **订单数据管理**：订单导出时自动转换状态、金额等字段
3. **系统配置导出**：导出系统配置，字典值自动转换为可读文本
4. **数据模板下载**：生成带下拉框的 Excel 模板，方便用户填写

## 🤝 依赖模块

- `yudao-common`：通用工具和基础类
- `yudao-spring-boot-starter-rpc`：RPC 远程调用（可选）
- `yudao-spring-boot-starter-biz-ip`：IP 地址解析（可选，用于 AreaConvert）

## 📝 注意事项

1. **字典缓存**：字典数据有 1 分钟缓存，如需立即生效需调用 `DictFrameworkUtils.clearCache()`
2. **Long 类型精度**：模块已自动处理 Long 类型精度问题，无需额外配置
3. **文件编码**：导出文件默认使用 UTF-8 编码
4. **响应流管理**：ExcelUtils 不会自动关闭响应流，由 Servlet 容器管理

## 📚 相关文档

- [FastExcel 官方文档](https://github.com/dromara/fast-excel)
- [芋道源码官网](https://www.iocoder.cn/)

## 🔗 相关模块

- `yudao-spring-boot-starter-web`：Web 相关功能
- `yudao-spring-boot-starter-mybatis`：数据库操作
- `yudao-common`：通用工具类

## 📄 License

本模块遵循项目整体的开源协议。

