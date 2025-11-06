# yudao-spring-boot-starter-job

## 📖 模块介绍

`yudao-spring-boot-starter-job` 是芋道项目的任务调度框架模块，提供了分布式任务调度和异步任务执行的能力。

**核心特性：**
- 🚀 基于 XXL-Job 实现分布式任务调度
- ⚡ 支持 Spring 异步任务（@Async）
- 🔄 集成 TransmittableThreadLocal（TTL）支持线程上下文传递
- 📋 支持 Spring 自带的定时任务（@Scheduled）
- 🎯 提供统一的配置管理

## 🏗️ 模块结构

```
yudao-spring-boot-starter-job
├── src/main/java
│   └── cn.iocoder.yudao.framework.quartz
│       ├── config
│       │   ├── YudaoXxlJobAutoConfiguration.java      # XXL-Job 自动配置
│       │   ├── YudaoAsyncAutoConfiguration.java       # 异步任务自动配置
│       │   └── XxlJobProperties.java                  # XXL-Job 配置属性
│       └── package-info.java
└── src/main/resources
    └── META-INF/spring
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## 📦 依赖引入

在项目的 `pom.xml` 中添加以下依赖：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-job</artifactId>
</dependency>
```

## ⚙️ 配置说明

### XXL-Job 配置

在 `application.yaml` 中添加以下配置：

```yaml
xxl:
  job:
    # 是否开启 XXL-Job，默认为 true
    enabled: true
    # 访问令牌，用于调度中心和执行器之间的认证
    access-token: default_token
    # 调度中心配置
    admin:
      # 调度中心地址，多个地址用逗号分隔
      addresses: http://127.0.0.1:9090/xxl-job-admin
    # 执行器配置
    executor:
      # 应用名称（执行器在调度中心的唯一标识）
      app-name: ${spring.application.name}
      # 执行器 IP（可选，为空则自动获取）
      ip: 
      # 执行器端口（-1 表示自动分配）
      port: -1
      # 日志文件路径
      log-path: ${user.home}/logs/xxl-job/${spring.application.name}
      # 日志保留天数（-1 表示永久保留）
      log-retention-days: 30
```

### 配置项说明

| 配置项 | 说明 | 默认值 | 是否必填 |
|-------|------|--------|---------|
| `xxl.job.enabled` | 是否开启 XXL-Job | `true` | 否 |
| `xxl.job.access-token` | 访问令牌 | - | 否 |
| `xxl.job.admin.addresses` | 调度中心地址 | - | 是 |
| `xxl.job.executor.app-name` | 执行器应用名 | - | 是 |
| `xxl.job.executor.ip` | 执行器 IP | 自动获取 | 否 |
| `xxl.job.executor.port` | 执行器端口 | `-1`（随机） | 否 |
| `xxl.job.executor.log-path` | 日志文件路径 | - | 是 |
| `xxl.job.executor.log-retention-days` | 日志保留天数 | `30` | 否 |

## 🚀 使用示例

### 1. XXL-Job 定时任务

创建一个定时任务处理类：

```java
package cn.iocoder.yudao.module.system.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DemoJob {

    /**
     * 简单任务示例
     */
    @XxlJob("demoJobHandler")
    public void demoJobHandler() {
        log.info("[demoJobHandler][开始执行]");
        // 业务逻辑
        XxlJobHelper.log("XXL-JOB, Hello World.");
        log.info("[demoJobHandler][执行完成]");
    }

    /**
     * 带参数的任务示例
     */
    @XxlJob("demoJobWithParamHandler")
    public void demoJobWithParamHandler() {
        // 获取任务参数
        String param = XxlJobHelper.getJobParam();
        log.info("[demoJobWithParamHandler][参数：{}]", param);
        
        // 业务逻辑
        // ...
        
        // 设置任务结果
        XxlJobHelper.handleSuccess("执行成功");
    }

    /**
     * 分片广播任务示例
     */
    @XxlJob("shardingJobHandler")
    public void shardingJobHandler() {
        // 获取分片参数
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        
        log.info("[shardingJobHandler][分片参数：当前分片={}, 总分片={}]", shardIndex, shardTotal);
        
        // 根据分片参数执行不同的业务逻辑
        // ...
    }
}
```

### 2. Spring 异步任务

使用 `@Async` 注解实现异步执行：

```java
package cn.iocoder.yudao.module.system.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AsyncService {

    /**
     * 异步方法示例
     */
    @Async
    public void asyncMethod() {
        log.info("[asyncMethod][异步执行开始]");
        // 执行耗时操作
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[asyncMethod][异步执行完成]");
    }

    /**
     * 带返回值的异步方法
     */
    @Async
    public CompletableFuture<String> asyncMethodWithReturn() {
        log.info("[asyncMethodWithReturn][异步执行开始]");
        // 业务逻辑
        String result = "处理结果";
        return CompletableFuture.completedFuture(result);
    }
}
```

### 3. Spring 定时任务

使用 `@Scheduled` 注解实现定时任务：

```java
package cn.iocoder.yudao.module.system.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SpringScheduledJob {

    /**
     * 固定延迟执行（上次执行完成后延迟指定时间）
     */
    @Scheduled(fixedDelay = 5000)
    public void fixedDelayJob() {
        log.info("[fixedDelayJob][执行]");
    }

    /**
     * 固定频率执行（每隔指定时间执行一次）
     */
    @Scheduled(fixedRate = 10000)
    public void fixedRateJob() {
        log.info("[fixedRateJob][执行]");
    }

    /**
     * Cron 表达式定时执行
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void cronJob() {
        log.info("[cronJob][每天凌晨1点执行]");
    }
}
```

## 🎯 核心功能

### 1. XXL-Job 分布式任务调度

- **自动注册执行器**：应用启动时自动向调度中心注册
- **任务管理**：在调度中心可视化管理任务的启动、停止、调度等
- **执行日志**：完整的任务执行日志记录
- **任务监控**：实时查看任务执行状态
- **分片广播**：支持任务分片，提高执行效率

### 2. 异步任务支持

- **TTL 集成**：自动集成 TransmittableThreadLocal，确保线程上下文正确传递
- **线程池装饰器**：自动为 `ThreadPoolTaskExecutor` 和 `SimpleAsyncTaskExecutor` 添加 TTL 装饰器
- **透明使用**：开发者无需关心底层实现，直接使用 `@Async` 注解即可

### 3. Spring 定时任务

- **自动开启**：自动启用 Spring 的 `@EnableScheduling` 支持
- **简单易用**：使用 `@Scheduled` 注解即可实现定时任务

## 📝 注意事项

1. **XXL-Job 调度中心**：使用前需要先部署 XXL-Job 调度中心
2. **执行器注册**：确保执行器能够访问调度中心的地址
3. **端口冲突**：如果指定固定端口，注意避免端口冲突
4. **日志清理**：合理设置日志保留天数，避免磁盘空间占用过多
5. **任务幂等性**：编写任务时需要考虑幂等性，避免重复执行导致数据异常
6. **异常处理**：任务执行过程中的异常需要妥善处理，避免影响后续执行

## 🔗 相关文档

- [XXL-Job 官方文档](https://www.xuxueli.com/xxl-job/)
- [Spring 异步任务文档](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-async)
- [Spring 定时任务文档](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-scheduled)
- [《芋道 Spring Boot 定时任务入门》](./《芋道%20Spring%20Boot%20定时任务入门》.md)
- [《芋道 Spring Boot 异步任务入门》](./《芋道%20Spring%20Boot%20异步任务入门》.md)

## 🤝 技术支持

如有问题，请参考：
- 项目文档：https://github.com/YunaiV/ruoyi-vue-pro
- 视频教程：https://doc.iocoder.cn
- 技术交流群：见项目主页

## 📄 License

本项目采用 MIT 协议，详见 [LICENSE](../../LICENSE) 文件。

