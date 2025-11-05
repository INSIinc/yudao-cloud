# yudao-spring-boot-starter-biz-ip

## 📖 模块介绍

`yudao-spring-boot-starter-biz-ip` 是芋道框架提供的 IP 地址和地区信息处理组件，提供 IP 地址解析和中国行政区划查询功能。

## ✨ 功能特性

### 1. IP 地址解析
- 根据 IP 地址查询对应的地理位置信息（国家、省份、城市）
- 支持 IPv4 地址解析
- 离线查询，无需依赖外部服务
- 高性能内存查询，微秒级响应

### 2. 行政区划查询
- 提供完整的中国行政区划数据（省、市、区/县）
- 支持通过区域编码查询区域信息
- 支持区域树形结构遍历
- 支持区域路径格式化输出

## 🛠 技术实现

### 依赖库

1. **ip2region** - IP 地址库
   - 项目地址：https://gitee.com/lionsoul/ip2region
   - 使用精简版 xdb 格式，约 10MB
   - 全部加载到内存，查询速度快

2. **行政区划数据**
   - 数据来源：https://github.com/modood/Administrative-divisions-of-China
   - CSV 格式存储，启动时加载到内存
   - 构建树形结构，支持父子节点查询

### 核心类说明

| 类名 | 说明 |
|------|------|
| `IPUtils` | IP 地址工具类，提供 IP 查询功能 |
| `AreaUtils` | 区域工具类，提供行政区划查询功能 |
| `Area` | 区域实体类，包含区域信息和父子关系 |
| `AreaTypeEnum` | 区域类型枚举（国家、省份、城市、地区） |

## 📦 Maven 依赖

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-biz-ip</artifactId>
</dependency>
```

## 🚀 快速开始

### 1. IP 地址查询

#### 1.1 根据 IP 字符串查询地区编号

```java
// 查询 IP 对应的地区编号
Integer areaId = IPUtils.getAreaId("120.202.4.50");
// 返回: 420600 (襄阳市的区域编号)
```

#### 1.2 根据 IP 字符串查询地区对象

```java
// 查询 IP 对应的地区信息
Area area = IPUtils.getArea("120.202.4.50");
System.out.println(area.getName());  // 输出: 襄阳市
System.out.println(area.getId());     // 输出: 420600
```

#### 1.3 根据 IP 长整型查询

```java
// 将 IP 字符串转换为长整型
long ip = Searcher.checkIP("120.203.123.250");

// 查询地区编号
Integer areaId = IPUtils.getAreaId(ip);

// 查询地区对象
Area area = IPUtils.getArea(ip);
System.out.println(area.getName());  // 输出: 宜春市
```

### 2. 行政区划查询

#### 2.1 根据区域编码查询区域信息

```java
// 查询北京市信息
Area area = AreaUtils.getArea(110100);

System.out.println(area.getId());           // 输出: 110100
System.out.println(area.getName());         // 输出: 北京市
System.out.println(area.getType());         // 输出: 3 (城市类型)

// 获取父节点
Area parent = area.getParent();
System.out.println(parent.getId());         // 输出: 110000 (北京直辖市)

// 获取子节点列表
List<Area> children = area.getChildren();
System.out.println(children.size());        // 输出: 16 (北京市下辖16个区)
```

#### 2.2 格式化区域路径

```java
// 格式化输出区域完整路径
String path1 = AreaUtils.format(110105);
System.out.println(path1);  // 输出: 北京市 北京市 朝阳区

String path2 = AreaUtils.format(1);
System.out.println(path2);  // 输出: 中国

String path3 = AreaUtils.format(2);
System.out.println(path3);  // 输出: 蒙古
```

#### 2.3 根据区域类型获取区域列表

```java
// 获取所有省份
List<Area> provinces = AreaUtils.getByType(AreaTypeEnum.PROVINCE);

// 获取所有城市
List<Area> cities = AreaUtils.getByType(AreaTypeEnum.CITY);

// 获取所有地区（区/县）
List<Area> districts = AreaUtils.getByType(AreaTypeEnum.DISTRICT);
```

#### 2.4 解析区域路径字符串

```java
// 解析区域路径，返回区域 ID
Integer areaId = AreaUtils.getAreaByPath("山东省", "青岛市", "市南区");
System.out.println(areaId);  // 输出对应的区域编号
```

## 📝 API 文档

### IPUtils 工具类

| 方法 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `getAreaId(String ip)` | IP 字符串 | Integer | 获取 IP 对应的地区编号 |
| `getAreaId(long ip)` | IP 长整型 | Integer | 获取 IP 对应的地区编号 |
| `getArea(String ip)` | IP 字符串 | Area | 获取 IP 对应的地区对象 |
| `getArea(long ip)` | IP 长整型 | Area | 获取 IP 对应的地区对象 |

### AreaUtils 工具类

| 方法 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `getArea(Integer id)` | 区域编号 | Area | 获取指定编号的区域对象 |
| `format(Integer id)` | 区域编号 | String | 格式化区域完整路径 |
| `getByType(AreaTypeEnum type)` | 区域类型 | List\<Area\> | 获取指定类型的所有区域 |
| `getAreaByPath(String... names)` | 区域名称路径 | Integer | 根据区域路径获取区域编号 |

### Area 实体类

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Integer | 区域编号 |
| `name` | String | 区域名称 |
| `type` | Integer | 区域类型（1-国家, 2-省份, 3-城市, 4-地区） |
| `parent` | Area | 父节点 |
| `children` | List\<Area\> | 子节点列表 |

### AreaTypeEnum 枚举

| 枚举值 | 类型值 | 说明 |
|--------|--------|------|
| `COUNTRY` | 1 | 国家 |
| `PROVINCE` | 2 | 省份 |
| `CITY` | 3 | 城市 |
| `DISTRICT` | 4 | 地区（区/县/镇） |

## 🎯 使用场景

1. **用户行为分析**
   - 根据用户 IP 地址分析地域分布
   - 统计不同地区的用户访问量

2. **地域限制功能**
   - 根据 IP 地址实现地域访问控制
   - 提供基于地理位置的内容推送

3. **地址选择器**
   - 省市区三级联动选择
   - 地址信息填写和验证

4. **数据统计报表**
   - 按地区统计业务数据
   - 生成地域分布图表

## ⚠️ 注意事项

1. **内存占用**
   - IP 数据库约占用 10MB 内存
   - 行政区划数据约占用 1-2MB 内存
   - 启动时一次性加载，运行期间常驻内存

2. **数据准确性**
   - IP 数据库需要定期更新以保证准确性
   - 行政区划数据可能随政策调整而变化

3. **性能考虑**
   - 所有查询均为内存操作，性能极高
   - 适合高并发场景使用
   - 单例模式，避免重复加载数据

4. **IPv6 支持**
   - 当前版本仅支持 IPv4 地址
   - IPv6 地址解析暂不支持

## 📄 数据文件

- `ip2region.xdb` - IP 地址数据库文件（位于 resources 目录）
- `area.csv` - 行政区划数据文件（位于 resources 目录）

## 🔗 相关链接

- [ip2region 项目](https://gitee.com/lionsoul/ip2region)
- [中国行政区划数据](https://github.com/modood/Administrative-divisions-of-China)
- [芋道源码](https://github.com/YunaiV/ruoyi-vue-pro)

## 📧 技术支持

如有问题或建议，请提交 Issue 或联系开发团队。

