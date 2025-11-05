# yudao-server

## 📖 模块介绍

`yudao-server` 是芋道项目的**单体应用启动模块**，作为整个后端系统的主入口。

本模块本质上是一个**空壳容器**，通过引入各个 `yudao-module-xxx` 业务模块的依赖，将所有功能整合到一个应用中，以单体模式运行，对外提供 RESTful API 服务。

## ✨ 功能特性

### 核心特性

- 🚀 **单体应用模式**：将所有业务模块集成在一个应用中，简化部署和运维
- 🔌 **模块化设计**：通过 Maven 依赖灵活控制启用的业务模块
- 🎯 **统一入口**：提供统一的 API 入口，支持前后端分离架构
- 📦 **按需加载**：可通过注释依赖来控制模块加载，优化编译速度
- 🐳 **容器化支持**：提供 Dockerfile，支持 Docker 容器化部署

### 集成模块

#### 默认启用模块

- ✅ **yudao-module-system-server**：系统管理（用户、角色、权限、菜单等）
- ✅ **yudao-module-infra-server**：基础设施（文件、定时任务、API 日志等）
- ✅ **yudao-module-bpm-server**：工作流程（流程设计、流程实例、任务审批等）

#### 可选模块（默认注释）

- 📊 **yudao-module-report-server**：数据报表
- 👥 **yudao-module-member-server**：会员中心
- 💰 **yudao-module-pay-server**：支付服务
- 📱 **yudao-module-mp-server**：微信公众号
- 🛒 **yudao-module-product-server**：商品管理
- 🎁 **yudao-module-promotion-server**：营销活动
- 📈 **yudao-module-trade-server**：交易订单
- 📉 **yudao-module-statistics-server**：数据统计
- 🤝 **yudao-module-crm-server**：客户关系管理
- 🏭 **yudao-module-erp-server**：企业资源计划
- 🤖 **yudao-module-ai-server**：AI 大模型
- 🌐 **yudao-module-iot-server**：物联网

## 🚀 快速开始

### 前置要求

- JDK 8+ (推荐 JDK 17/21)
- Maven 3.6+
- MySQL 5.7+
- Redis 3.0+
- Nacos 2.0+ (用于配置中心和服务注册)

### 本地启动

1. **启动基础服务**
   ```bash
   # 启动 MySQL、Redis、Nacos 等基础服务
   # 可使用项目根目录的 docker-compose.yml
   docker-compose up -d
   ```

2. **导入数据库**
   ```bash
   # 执行项目根目录 sql/mysql/ 下的 SQL 脚本
   ```

3. **配置 Nacos**
   ```bash
   # 在 Nacos 中配置应用的配置文件
   # 参考：https://cloud.iocoder.cn/quick-start/
   ```

4. **启动应用**
   ```bash
   # 方式一：通过 Maven 启动
   mvn clean spring-boot:run
   
   # 方式二：通过 IDE 启动
   # 运行 cn.iocoder.yudao.server.YudaoServerApplication 的 main 方法
   ```

5. **访问应用**
   ```
   应用启动成功后，默认访问地址：http://localhost:48080
   ```

### 启动问题排查

如果遇到启动问题，请认真阅读官方文档：https://doc.iocoder.cn/quick-start/

## ⚙️ 配置说明

### 端口配置

- 默认端口：`48080`
- 可在 Nacos 配置中心修改 `server.port` 配置项

### 模块启用/禁用

在 `pom.xml` 中通过注释/取消注释依赖来控制模块：

```xml
<!-- 启用模块：取消注释 -->
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-module-member-server</artifactId>
    <version>${revision}</version>
</dependency>

<!-- 禁用模块：添加注释 -->
<!--
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-module-member-server</artifactId>
    <version>${revision}</version>
</dependency>
-->
```

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `JAVA_OPTS` | JVM 参数 | `-Xms512m -Xmx512m` |
| `TZ` | 时区设置 | `Asia/Shanghai` |
| `ARGS` | 应用启动参数 | 空 |

## 📦 构建部署

### Maven 构建

```bash
# 编译打包
mvn clean package -DskipTests

# 生成的 jar 包位置
# target/yudao-server.jar
```

### Docker 部署

```bash
# 1. 构建镜像
docker build -t yudao-server:latest .

# 2. 运行容器
docker run -d \
  --name yudao-server \
  -p 48080:48080 \
  -e JAVA_OPTS="-Xms512m -Xmx512m" \
  yudao-server:latest

# 3. 查看日志
docker logs -f yudao-server
```

### 生产环境建议

1. **资源配置**：根据实际业务量调整 JVM 内存参数
2. **服务监控**：接入 Spring Boot Admin 或 Prometheus 进行监控
3. **日志管理**：配置日志持久化，建议使用 ELK 或阿里云日志服务
4. **高可用部署**：建议部署多实例，通过负载均衡实现高可用

## 🏗️ 项目结构

```
yudao-server/
├── src/
│   └── main/
│       ├── java/
│       │   └── cn/iocoder/yudao/server/
│       │       ├── YudaoServerApplication.java    # 应用启动类
│       │       └── controller/
│       │           └── DefaultController.java     # 默认控制器
│       └── resources/                              # 配置资源
├── target/                                         # 构建输出目录
├── Dockerfile                                      # Docker 镜像构建文件
├── pom.xml                                         # Maven 项目配置
└── README.md                                       # 项目说明文档
```

## 🔗 相关链接

- 📚 [项目文档](https://cloud.iocoder.cn/)
- 🎬 [视频教程](https://cloud.iocoder.cn/video/)
- 🌐 [在线演示](http://dashboard-vue3.yudao.iocoder.cn)
- 💬 [问题反馈](https://gitee.com/zhijiantianya/yudao-cloud/issues)
- 📖 [迁移文档](https://cloud.iocoder.cn/migrate-module/)

## 📋 版本说明

本模块支持两个 JDK 版本：

| 分支 | JDK 版本 | Spring Boot 版本 |
|------|----------|------------------|
| `master` | JDK 8 | 2.7.x |
| `master-jdk17` | JDK 17/21 | 3.2.x |

## ⚠️ 注意事项

1. **单体 vs 微服务**
   - 本模块适用于单体应用部署
   - 如需微服务架构，请使用各个独立的 module-server 模块

2. **模块依赖管理**
   - 启用新模块后，需要执行相应的数据库脚本
   - 禁用模块时，确保没有其他模块依赖它

3. **性能优化**
   - 开发环境建议只启用必要的模块，提高编译速度
   - 生产环境根据实际业务需求选择启用的模块

4. **配置中心**
   - 本模块依赖 Nacos 配置中心，启动前确保 Nacos 已正确配置
   - 配置文件格式：`application-{profile}.yaml`

## 🤝 参与贡献

欢迎提交 Issue 或 Pull Request 参与项目贡献！

## 📄 开源协议

本项目采用 MIT 开源协议，个人与企业可 100% 免费使用。

---

💡 **提示**：更多详细信息请查看[官方文档](https://cloud.iocoder.cn/)

