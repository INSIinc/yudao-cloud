# yudao-module-report

## 📚 模块简介

Report 报表模块是芋道项目的数据可视化报表模块，提供企业级的报表设计、大屏展示、数据分析等能力。

本模块集成了两大报表解决方案：
- **积木报表（JimuReport）**：企业级报表平台，支持打印设计、报表设计、图形设计、大屏设计等
- **GoView**：开源的数据可视化大屏项目，支持拖拽式大屏设计

## ✨ 核心特性

### 🎨 双报表引擎支持
- **积木报表**：功能强大的企业级报表平台
  - 打印设计器
  - 报表设计器
  - 图形设计器
  - 大屏设计器（商业版）
  
- **GoView 大屏**：开源的数据可视化解决方案
  - 拖拽式大屏设计
  - 丰富的图表组件
  - 灵活的数据源配置
  - 实时数据展示

### 📊 数据查询能力
- **SQL 查询**：支持通过 SQL 语句直接查询数据
- **HTTP 查询**：支持通过 HTTP 接口获取数据
- **多数据源支持**：可配置多个数据源进行数据查询
- **数据安全**：基于权限的数据访问控制

### 🔐 安全特性
- **权限控制**：基于 RBAC 的细粒度权限管理
- **租户隔离**：支持多租户数据隔离
- **SQL 注入防护**：SQL 查询安全校验
- **数据脱敏**：敏感数据自动脱敏

## 📦 模块结构

```
yudao-module-report
├── yudao-module-report-api          # API 接口模块（对外暴露）
│   └── src/main/java
│       └── cn/iocoder/yudao/module/report
│           └── enums/               # 枚举定义
└── yudao-module-report-server       # 服务实现模块
    └── src/main/java
        └── cn/iocoder/yudao/module/report
            ├── controller/          # 控制层
            │   └── admin/
            │       ├── goview/             # GoView 相关接口
            │       │   ├── GoViewProjectController.java    # 项目管理
            │       │   └── GoViewDataController.java       # 数据查询
            │       └── ajreport/           # 积木报表相关（预留）
            ├── service/             # 服务层
            │   └── goview/
            │       ├── GoViewProjectService.java           # 项目服务
            │       └── GoViewDataService.java              # 数据服务
            ├── dal/                 # 数据访问层
            │   ├── dataobject/
            │   │   ├── goview/             # GoView 数据对象
            │   │   └── ajreport/           # 积木报表数据对象（预留）
            │   └── mysql/
            ├── convert/             # 对象转换
            │   ├── goview/
            │   └── ajreport/
            └── framework/           # 框架集成
                ├── security/               # 安全配置
                └── jmreport/               # 积木报表集成
```

## 🚀 主要功能

### 1️⃣ GoView 项目管理
- **项目创建**：创建新的可视化大屏项目
- **项目编辑**：在线编辑大屏内容和布局
- **项目发布**：发布大屏供用户访问
- **项目删除**：删除不需要的项目
- **项目分页**：查看我的项目列表
- **项目详情**：获取项目完整信息

**相关接口：**
```
POST   /report/go-view/project/create     # 创建项目
PUT    /report/go-view/project/update     # 更新项目
DELETE /report/go-view/project/delete     # 删除项目
GET    /report/go-view/project/get        # 获取项目详情
GET    /report/go-view/project/my-page    # 我的项目分页
```

### 2️⃣ GoView 数据查询
- **SQL 查询**：通过 SQL 语句查询业务数据
- **HTTP 查询**：通过 HTTP 接口获取数据
- **数据转换**：自动将查询结果转换为前端所需格式
- **权限校验**：基于权限的数据访问控制

**相关接口：**
```
POST /report/go-view/data/get-by-sql      # SQL 查询数据
POST /report/go-view/data/get-by-http     # HTTP 查询数据
```

### 3️⃣ 积木报表（JimuReport）
- **打印设计**：设计各类打印模板（合同、发票、单据等）
- **报表设计**：设计统计报表、明细报表
- **图形设计**：设计各类图表和可视化组件
- **大屏设计**：设计数据大屏（商业版功能）

**访问路径：**
```
积木报表相关功能通过 /jmreport/** 路径访问
数据表前缀：jimu_*
```

## 🛠 技术栈

### 核心框架
- **Spring Boot 3.x**：核心框架
- **Spring Cloud**：微服务架构
- **MyBatis-Plus**：数据持久化

### 报表引擎
- **JimuReport（积木报表）**：企业级报表平台
- **GoView**：开源数据可视化大屏

### 数据库
- **MySQL**：主数据库
- 支持多数据源配置

## 📝 数据库表

### GoView 相关表
```sql
-- GoView 项目表
report_go_view_project
  - id: 项目 ID
  - name: 项目名称
  - status: 项目状态
  - content: 项目内容（JSON）
  - creator_id: 创建人 ID
  - create_time: 创建时间
  - update_time: 更新时间
  - deleted: 是否删除
```

### 积木报表相关表
```sql
-- 积木报表相关表（表名前缀：jimu_*）
jimu_report         # 报表主表
jimu_report_db      # 数据源表
jimu_report_data    # 报表数据表
...
```

## 🔒 权限配置

模块使用以下权限标识：

### GoView 权限
```
report:go-view-project:create         # 创建项目
report:go-view-project:update         # 更新项目
report:go-view-project:delete         # 删除项目
report:go-view-project:query          # 查询项目

report:go-view-data:get-by-sql        # SQL 查询数据
report:go-view-data:get-by-http       # HTTP 查询数据
```

## 🚀 快速开始

### 1. 启动模块

模块作为独立服务启动，启动类：
```java
cn.iocoder.yudao.module.report.ReportServerApplication
```

### 2. 配置数据源

在 `application.yml` 中配置数据源：
```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/ruoyi-vue-pro?useSSL=false
    username: root
    password: 123456
```

### 3. 访问积木报表

启动后访问：
```
http://localhost:48080/jmreport/list
```

### 4. 使用 GoView

通过前端页面访问 GoView 大屏设计器，使用相关接口进行项目管理和数据查询。

## 📖 使用示例

### 创建 GoView 项目
```java
// 1. 构建创建请求
GoViewProjectCreateReqVO createReqVO = new GoViewProjectCreateReqVO();
createReqVO.setName("销售数据大屏");
createReqVO.setRemark("展示销售相关数据");

// 2. 调用接口创建
Long projectId = goViewProjectService.createProject(createReqVO);
```

### SQL 查询数据
```java
// 1. 构建 SQL 查询请求
String sql = "SELECT DATE_FORMAT(create_time, '%Y-%m-%d') as date, " +
             "COUNT(*) as count FROM system_users GROUP BY date";

// 2. 执行查询
GoViewDataRespVO data = goViewDataService.getDataBySQL(sql);

// 3. 返回结果包含：
//    - dimensions: 数据维度 ["date", "count"]
//    - source: 明细数据 [["2024-01-01", 100], ["2024-01-02", 150]]
```

## 🔧 扩展开发

### 添加自定义数据查询

1. 在 `GoViewDataController` 中添加新的查询接口
2. 实现对应的 Service 方法
3. 配置相应的权限标识
4. 在前端配置数据源

### 集成新的报表引擎

1. 在 `framework` 包下创建对应的配置类
2. 实现安全认证集成
3. 添加相应的 Controller 和 Service
4. 配置路由和权限

## ⚠️ 注意事项

1. **SQL 注入防护**：使用 SQL 查询功能时，务必做好参数校验和 SQL 注入防护
2. **性能优化**：大数据量查询时注意分页和索引优化
3. **权限控制**：确保所有接口都配置了相应的权限校验
4. **数据安全**：敏感数据查询时做好脱敏处理
5. **商业授权**：积木报表的大屏设计器是商业版功能，需要购买授权

## 🤝 相关模块

- **yudao-module-system**：提供用户、角色、权限等基础功能
- **yudao-module-infra**：提供基础设施支持
- **yudao-gateway**：提供网关路由

## 📞 技术支持

- 文档地址：https://cloud.iocoder.cn
- 视频教程：https://cloud.iocoder.cn/video/
- 积木报表官网：http://www.jimureport.com
- GoView 项目：https://gitee.com/dromara/go-view

## 📄 许可证

本模块遵循 MIT 开源协议。

