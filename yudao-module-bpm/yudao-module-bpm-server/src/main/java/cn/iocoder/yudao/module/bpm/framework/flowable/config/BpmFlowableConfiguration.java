package cn.iocoder.yudao.module.bpm.framework.flowable.config;

import cn.hutool.core.collection.ListUtil;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior.BpmActivityBehaviorFactory;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.event.BpmProcessInstanceEventPublisher;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.flowable.common.engine.api.delegate.FlowableFunctionDelegate;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncListenableTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

/**
 * BPM 模块的 Flowable 配置类
 * <p>
 * 主要作用：
 * 1. 自定义 Flowable 引擎行为（如任务审批人计算、流程事件监听等）
 * 2. 集成 Spring 上下文与 Flowable 引擎
 * 3. 配置异步任务执行器，避免 Flowable 启动失败
 *
 * @author jason
 */
@Configuration(proxyBeanMethods = false)
public class BpmFlowableConfiguration {

    /**
     * 创建一个 Spring 兼容的异步任务执行器 Bean（名称为 applicationTaskExecutor），
     * 供 Flowable 内部异步任务（如定时器、异步执行等）使用。
     * <p>
     * 原因：Flowable 在 Spring Boot 环境下启动时，若未提供名为 applicationTaskExecutor 的
     * AsyncListenableTaskExecutor Bean，会因找不到 TaskExecutor 而报错。
     * <p>
     * 此 Bean 使用 ThreadPoolTaskExecutor 实现，并做了以下配置：
     * - 核心线程数：8
     * - 最大线程数：8（固定线程池）
     * - 队列容量：100（拒绝策略为 CallerRunsPolicy，默认）
     * - 线程前缀：flowable-task-Executor-
     * - 关闭时等待任务完成（最大等待 30 秒）
     * - 允许核心线程超时（提升资源利用率）
     */
    @Bean(name = "applicationTaskExecutor")
    @ConditionalOnMissingBean(name = "applicationTaskExecutor") // 仅当容器中没有同名 Bean 时才创建
    public AsyncListenableTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("flowable-task-Executor-");
        executor.setAwaitTerminationSeconds(30);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAllowCoreThreadTimeOut(true);
        executor.initialize(); // 必须调用 initialize() 才能生效
        return executor;
    }

    /**
     * 提供一个 Flowable 引擎的配置器（EngineConfigurationConfigurer），
     * 用于在 Flowable 引擎初始化时注入自定义行为。
     * <p>
     * 本配置器主要完成以下工作：
     * 1. 注册 Flowable 事件监听器（如流程启动、任务创建等事件）
     * 2. 替换默认的 ActivityBehaviorFactory，以支持自定义任务审批人逻辑
     * 3. 注册自定义的 Flowable 函数（可在 BPMN 表达式中调用）
     *
     * @param listeners                        Spring 容器中所有 FlowableEventListener 类型的 Bean
     * @param customFlowableFunctionDelegates  Spring 容器中所有自定义 Flowable 函数委托（可在 BPMN 中使用）
     * @param bpmActivityBehaviorFactory       自定义的活动行为工厂，用于控制任务节点的行为（如审批人计算）
     * @return 配置器实例
     */
    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> bpmProcessEngineConfigurationConfigurer(
            ObjectProvider<FlowableEventListener> listeners,
            ObjectProvider<FlowableFunctionDelegate> customFlowableFunctionDelegates,
            BpmActivityBehaviorFactory bpmActivityBehaviorFactory) {
        return configuration -> {
            // 设置 Flowable 事件监听器列表（例如：记录流程实例变更、发送通知等）
            configuration.setEventListeners(ListUtil.toList(listeners.iterator()));

            // 替换默认的 ActivityBehaviorFactory，使用户任务（UserTask）能使用自定义审批人逻辑
            configuration.setActivityBehaviorFactory(bpmActivityBehaviorFactory);

            // 注册可在 BPMN 表达式（如 ${myFunction()}）中调用的自定义 Java 函数
            configuration.setCustomFlowableFunctionDelegates(
                    ListUtil.toList(customFlowableFunctionDelegates.stream().iterator())
            );
        };
    }

    // =========== 审批人相关的 Bean ==========

    /**
     * 创建自定义的 ActivityBehaviorFactory Bean。
     * <p>
     * Flowable 中每个 BPMN 元素（如 UserTask）的行为由 ActivityBehavior 决定。
     * 通过自定义此工厂，可以在创建 UserTask 行为时注入审批人计算逻辑。
     *
     * @param bpmTaskCandidateInvoker 审批人计算的执行器（整合了多种策略）
     * @return 自定义的 BpmActivityBehaviorFactory 实例
     */
    @Bean
    public BpmActivityBehaviorFactory bpmActivityBehaviorFactory(BpmTaskCandidateInvoker bpmTaskCandidateInvoker) {
        BpmActivityBehaviorFactory factory = new BpmActivityBehaviorFactory();
        factory.setTaskCandidateInvoker(bpmTaskCandidateInvoker); // 注入审批人计算组件
        return factory;
    }

    /**
     * 创建审批人计算的执行器（BpmTaskCandidateInvoker）。
     * <p>
     * 它负责在流程任务节点（UserTask）分配审批人时，根据配置的策略（如角色、部门、表达式等）
     * 动态计算实际的审批人或候选用户。
     * <p>
     * 依赖：
     * - strategyList：所有实现了 BpmTaskCandidateStrategy 的策略 Bean（Spring 自动注入）
     * - adminUserApi：用于根据用户 ID 获取用户信息（如姓名、部门等），通常对接用户中心
     *
     * @param strategyList 所有审批人策略实现（如按角色、按部门、按表达式等）
     * @param adminUserApi 用户服务 API，用于查询用户详细信息
     * @return 审批人计算执行器
     */
    @Bean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") // 忽略 IDEA 对 adminUserApi 注入的警告（实际可注入）
    public BpmTaskCandidateInvoker bpmTaskCandidateInvoker(
            List<BpmTaskCandidateStrategy> strategyList,
            AdminUserApi adminUserApi) {
        return new BpmTaskCandidateInvoker(strategyList, adminUserApi);
    }

    // =========== 自己拓展的 Bean ==========

    /**
     * 创建一个流程实例事件发布器。
     * <p>
     * 该组件封装了 Spring 的 ApplicationEventPublisher，用于将 Flowable 的流程事件
     *（如流程启动、结束、任务完成等）转换为 Spring ApplicationEvent 并发布，
     * 便于其他模块监听并处理（如记录日志、发送消息、更新业务状态等）。
     *
     * @param publisher Spring 的事件发布器
     * @return 流程实例事件发布器
     */
    @Bean
    public BpmProcessInstanceEventPublisher processInstanceEventPublisher(ApplicationEventPublisher publisher) {
        return new BpmProcessInstanceEventPublisher(publisher);
    }

}