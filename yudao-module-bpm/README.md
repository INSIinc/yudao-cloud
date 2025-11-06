# yudao-module-bpm

## 📖 模块简介

BPM (Business Process Management) 业务流程管理模块，基于 **Flowable 6** 工作流引擎实现，提供完整的流程管理解决方案。

本模块提供流程设计、流程部署、流程审批、流程监控等全生命周期管理功能，支持动态表单、复杂流程设计、多种任务分配方式等企业级特性。

## ✨ 功能特性

### 🎯 流程定义管理
- **流程模型设计**：可视化流程设计器，支持 BPMN 2.0 标准
- **流程分类管理**：按业务类型对流程进行分类组织
- **流程版本控制**：支持流程多版本管理和版本切换
- **流程部署发布**：一键部署流程定义到运行环境
- **流程监听器**：支持配置流程生命周期监听器
- **流程表达式**：支持 Spring EL 表达式，灵活控制流程走向

### 📝 表单管理
- **动态表单设计**：可视化表单设计器，拖拽式配置
- **表单字段验证**：支持多种数据类型和验证规则
- **表单权限控制**：字段级别的读写权限控制
- **表单版本管理**：表单支持版本管理和历史回溯

### 🔄 流程实例管理
- **流程发起**：支持多种方式发起流程申请
- **我的申请**：查看个人发起的所有流程申请
- **流程撤销**：支持申请人主动撤销流程
- **流程取消**：支持管理员取消异常流程
- **流程抄送**：支持流程抄送功能，知会相关人员
- **流程监控**：实时查看流程执行状态和流转历史

### ✅ 任务管理
- **我的待办**：查看待处理的审批任务
- **我的已办**：查看已处理的审批任务历史
- **任务审批**：同意、拒绝、退回等多种审批操作
- **任务委派**：将任务委派给其他人处理
- **任务转办**：将任务转交给其他人处理
- **任务加签**：支持前加签、后加签
- **会签/或签**：支持多人会签或或签审批模式
- **任务超时提醒**：自动提醒超时未处理的任务

### 👥 用户组管理
- **用户组配置**：定义审批用户组，灵活分配审批人
- **动态审批人**：支持多种方式指定审批人（角色、部门、用户组等）
- **审批人策略**：支持多种审批人选择策略

### 📊 OA 办公示例
- **请假申请**：提供完整的请假审批流程示例
- **业务集成**：展示流程与业务系统的集成方式

## 🏗️ 技术架构

### 核心依赖
- **Flowable 6.x**：工作流引擎核心
- **Spring Boot 2.7 / 3.2**：基础框架
- **Spring Cloud Alibaba**：微服务框架
- **MyBatis Plus**：数据持久化
- **Redis**：缓存和分布式锁

### 设计特点
- **模块化设计**：API 和 Server 分离，便于服务调用
- **多租户支持**：支持 SaaS 多租户场景
- **数据权限**：集成数据权限控制
- **消息通知**：支持站内信、短信、邮件等多种通知方式
- **高扩展性**：支持自定义监听器、表达式等扩展点

## 📁 模块结构

```
yudao-module-bpm
├── yudao-module-bpm-api          # BPM API 模块（对外接口）
│   ├── src/main/java
│   │   └── cn.iocoder.yudao.module.bpm
│   │       ├── api              # Feign 接口定义
│   │       └── enums            # 枚举类
│   └── pom.xml
│
└── yudao-module-bpm-server       # BPM Server 模块（业务实现）
    ├── src/main/java
    │   └── cn.iocoder.yudao.module.bpm
    │       ├── controller       # 控制层
    │       │   ├── admin       # 管理后台接口
    │       │   │   ├── definition   # 流程定义相关
    │       │   │   ├── task         # 任务管理相关
    │       │   │   └── oa           # OA 办公相关
    │       │   └── app         # 移动端接口
    │       ├── service          # 业务层
    │       │   ├── definition  # 流程定义服务
    │       │   ├── task        # 任务服务
    │       │   ├── message     # 消息服务
    │       │   └── oa          # OA 服务
    │       ├── dal              # 数据访问层
    │       │   ├── mysql       # MySQL Mapper
    │       │   └── redis       # Redis DAO
    │       ├── convert          # 对象转换
    │       ├── framework        # 框架配置
    │       └── api              # API 实现
    ├── src/main/resources
    │   └── processes            # 流程定义文件（BPMN）
    └── pom.xml
```

## 🚀 核心功能说明

### 1. 流程定义（Process Definition）
- **BpmModelController**：流程模型管理，支持创建、修改、部署流程模型
- **BpmProcessDefinitionController**：流程定义管理，查看已部署的流程定义
- **BpmFormController**：动态表单管理
- **BpmCategoryController**：流程分类管理
- **BpmProcessListenerController**：流程监听器配置
- **BpmProcessExpressionController**：流程表达式配置
- **BpmUserGroupController**：用户组管理

### 2. 流程实例（Process Instance）
- **BpmProcessInstanceController**：流程实例管理
  - 发起流程
  - 取消流程
  - 查询我的申请
  - 查询流程详情
  - 查询流程活动（Activity）
- **BpmProcessInstanceCopyController**：流程抄送管理

### 3. 任务管理（Task）
- **BpmTaskController**：任务管理
  - 查询待办任务
  - 查询已办任务
  - 审批任务（同意/拒绝）
  - 退回任务
  - 委派任务
  - 转办任务
  - 加签任务
  - 减签任务

### 4. OA 办公
- **BpmOALeaveController**：请假申请示例
  - 创建请假申请
  - 查询请假记录
  - 请假审批流程集成

## 🔧 快速开始

### 1. 数据库初始化
执行 BPM 模块的 SQL 脚本，初始化相关数据表：
- 流程定义表
- 流程实例表
- 任务表
- 表单表
- 用户组表等

### 2. 配置说明
在 `application.yaml` 中配置 Flowable 相关参数：
```yaml
flowable:
  # 关闭定时任务 JOB，防止集群冲突
  async-executor-activate: false
  # 流程图字体设置
  activity-font-name: 宋体
  label-font-name: 宋体
  annotation-font-name: 宋体
```

### 3. 启动服务
确保以下依赖服务已启动：
- MySQL 数据库
- Redis 缓存
- Nacos 注册中心

启动 BpmServerApplication 即可。

### 4. 访问管理后台
- 流程管理：系统管理 -> 工作流程 -> 流程管理
- 表单管理：系统管理 -> 工作流程 -> 表单管理
- 我的待办：工作流程 -> 我的待办
- 我的申请：工作流程 -> 我的申请

## 📝 API 接口

### API 模块说明
`yudao-module-bpm-api` 提供给其他模块调用的 Feign 接口：

- **BpmProcessInstanceApi**：流程实例 API
  - 创建流程实例
  - 查询流程实例状态
  - 取消流程实例

其他模块可通过依赖 `yudao-module-bpm-api` 来调用 BPM 服务。

## 🎨 流程设计器

支持通过 Web 可视化设计器设计流程：
- 支持拖拽式设计
- 支持 BPMN 2.0 标准元素
- 支持用户任务、网关、子流程等
- 支持配置任务监听器、执行监听器
- 支持条件表达式配置

## 📚 扩展开发

### 自定义监听器
继承 Flowable 监听器接口，实现自定义业务逻辑：
```java
@Component
public class MyTaskListener implements TaskListener {
    @Override
    public void notify(DelegateTask delegateTask) {
        // 自定义逻辑
    }
}
```

### 自定义服务任务
实现 JavaDelegate 接口，创建自动化任务：
```java
@Component
public class MyServiceTask implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        // 自动化逻辑
    }
}
```

## 🤝 集成说明

### 与业务系统集成
1. 创建业务表和流程实例的关联
2. 在流程发起时保存业务数据
3. 在任务审批时更新业务状态
4. 监听流程结束事件，完成业务闭环

### 消息通知集成
流程自动发送通知：
- 任务创建时通知审批人
- 任务超时时提醒审批人
- 流程完成时通知申请人
- 流程拒绝时通知申请人

## 📖 参考文档

- [Flowable 官方文档](https://www.flowable.com/open-source/docs/)
- [BPMN 2.0 规范](https://www.omg.org/spec/BPMN/2.0/)
- [芋道源码 - BPM 文档](https://cloud.iocoder.cn/bpm/)

## 🙋 常见问题

### Q: 如何自定义审批人？
A: 可以通过配置用户组、角色、部门等多种方式指定审批人，也可以通过表达式动态计算审批人。

### Q: 如何实现会签？
A: 在流程设计时，将用户任务配置为多实例任务，并设置完成条件。

### Q: 流程超时如何处理？
A: 配置边界定时事件或定时任务，在超时时触发相应的处理逻辑。

### Q: 如何查看流程执行历史？
A: 调用流程实例详情接口，可以获取完整的流程执行轨迹和审批记录。

## 📄 许可证

本模块遵循 [MIT License](../LICENSE) 开源协议。

