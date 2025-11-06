# yudao-dependencies

## 📖 模块简介

`yudao-dependencies` 是芋道项目的依赖管理模块，采用 Maven BOM（Bill of Materials）机制，统一管理整个项目的依赖版本。

通过集中式的依赖版本管理，确保项目各模块使用一致的依赖版本，避免版本冲突，简化依赖管理。

## 🎯 核心功能

- **统一版本管理**：集中管理所有第三方依赖和内部模块的版本号
- **依赖冲突解决**：通过 BOM 机制避免依赖版本冲突
- **简化配置**：子模块无需指定依赖版本，继承父模块的版本管理
- **版本控制**：便于统一升级和维护依赖版本

## 📦 依赖分类

### 1. 核心框架
- **Spring Boot**: `3.5.5`
- **Spring Cloud**: `2025.0.0`
- **Spring Cloud Alibaba**: `2023.0.3.3`

### 2. Web 相关
- **Knife4j**: `4.5.0` - API 文档工具
- **Springdoc**: `2.8.11` - OpenAPI 规范

### 3. 数据库相关
- **MyBatis**: `3.5.19`
- **MyBatis-Plus**: `3.5.14` - MyBatis 增强工具
- **Dynamic-Datasource**: `4.3.1` - 动态多数据源
- **Druid**: `1.2.27` - 数据库连接池
- **Redisson**: `3.51.0` - Redis 客户端
- **国产数据库支持**：
  - DM8（达梦）: `8.1.3.140`
  - KingBase（人大金仓）: `8.6.0`
  - OpenGauss（华为）: `5.1.0`
  - TDengine: `3.7.3`

### 4. 消息队列
- **RocketMQ**: `2.3.4`

### 5. 定时任务
- **XXL-Job**: `2.4.0`

### 6. 工作流
- **Flowable**: `7.0.1`

### 7. 服务保障
- **Lock4j**: `2.2.7` - 分布式锁

### 8. 监控相关
- **SkyWalking**: `9.5.0` - 分布式追踪
- **Spring Boot Admin**: `3.5.2` - 服务监控
- **OpenTracing**: `0.33.0` - 链路追踪

### 9. 工具类库
- **Lombok**: `1.18.38` - 简化 Java 代码
- **MapStruct**: `1.6.3` - Bean 映射工具
- **Hutool 5.x**: `5.8.40` - Java 工具库
- **Hutool 6.x**: `6.0.0-M22` - Java 工具库新版本
- **Guava**: `33.4.8-jre` - Google 核心库
- **FastJSON**: `1.2.83` - JSON 处理
- **FastExcel**: `1.3.0` - Excel 处理
- **Velocity**: `2.4.1` - 模板引擎
- **JSoup**: `1.21.2` - HTML 解析
- **IP2Region**: `2.7.0` - IP 地址定位
- **Tika**: `3.2.2` - 文件类型识别

### 10. 第三方集成
- **微信开发工具**: `4.7.7` - 支持公众号、小程序、支付等
- **JustAuth**: `1.16.7` - 社交登录
- **AWS SDK**: `2.30.14` - 对象存储
- **积木报表**: `2.1.1` - 报表工具
- **验证码**: `1.4.0` - AJ-Captcha

### 11. IoT 相关
- **Vert.x**: `4.5.13` - 高性能异步框架
- **Netty**: `4.2.4.Final` - 网络通信框架
- **MQTT**: `1.2.5` - 物联网协议
- **PF4J**: `0.9.0` - 插件框架

### 12. 测试相关
- **Mockito-Inline**: `5.2.0` - Mock 框架
- **Jedis-Mock**: `1.1.11` - Redis Mock
- **PODAM**: `8.0.2.RELEASE` - 测试数据生成

### 13. 业务组件
- **BizLog**: `3.0.6` - 操作日志
- **Easy-Trans**: `3.0.6` - 数据翻译

## 🔧 使用方式

### 在父 POM 中引入

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>cn.iocoder.cloud</groupId>
            <artifactId>yudao-dependencies</artifactId>
            <version>${revision}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 在子模块中使用

子模块只需声明依赖的 `groupId` 和 `artifactId`，无需指定版本号：

```xml
<dependencies>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
    <dependency>
        <groupId>cn.hutool</groupId>
        <artifactId>hutool-all</artifactId>
    </dependency>
</dependencies>
```

## 📋 版本说明

当前版本：`2025.10-SNAPSHOT`

版本号格式：`年份.月份-SNAPSHOT`

## ⚠️ 注意事项

1. **版本统一**：所有子模块应通过此 BOM 管理依赖版本，不要单独指定版本
2. **依赖排除**：某些依赖已配置排除项，避免版本冲突，详见 `pom.xml`
3. **国产化支持**：已集成多种国产数据库驱动，支持信创环境
4. **升级策略**：升级依赖版本时需在此模块统一修改，并充分测试

## 🔗 相关链接

- [芋道项目主页](https://github.com/YunaiV/ruoyi-vue-pro)
- [在线文档](https://cloud.iocoder.cn)
- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [Spring Cloud 官方文档](https://spring.io/projects/spring-cloud)

## 📝 维护说明

更新依赖版本时，请遵循以下步骤：

1. 在 `<properties>` 中修改对应的版本属性
2. 确保新版本与其他依赖兼容
3. 运行完整的测试套件
4. 更新本 README 中的版本号信息
5. 提交变更并说明升级原因

---

**提示**：本模块仅用于依赖管理，不包含任何业务代码。

