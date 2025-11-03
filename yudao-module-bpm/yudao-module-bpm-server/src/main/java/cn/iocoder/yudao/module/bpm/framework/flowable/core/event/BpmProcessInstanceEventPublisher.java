package cn.iocoder.yudao.module.bpm.framework.flowable.core.event;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.validation.annotation.Validated;

/**
 * BPM 流程实例状态变更事件的发布者。
 *
 * <p>该类封装了 Spring 的 {@link ApplicationEventPublisher}，用于以类型安全、职责清晰的方式
 * 向 Spring 应用上下文发布 {@link BpmProcessInstanceStatusEvent} 事件。
 *
 * <p>典型使用场景：在 Flowable 流程引擎中，当流程实例状态发生变化（例如启动、完成、终止等）时，
 * 业务代码调用本类的方法，将状态事件广播出去，供其他监听器（如日志记录、通知推送、状态同步等）
 * 进行异步或同步处理。
 *
 * <p>该类使用 Lombok 的 {@link lombok.AllArgsConstructor} 自动生成构造函数，
 * 并通过 Spring 的依赖注入机制传入 {@link ApplicationEventPublisher}。
 *
 * <p>同时启用了 Spring 的 Bean Validation（JSR-380）：
 * - 类级别使用 {@link Validated} 注解，表示该 Bean 的方法参数需要进行校验。
 * - 方法参数使用 {@link Valid} 注解，触发对传入事件对象的约束验证（如 @NotNull、@NotBlank 等）。
 *
 * @author 芋道源码
 */
@AllArgsConstructor
@Validated
public class BpmProcessInstanceEventPublisher {

    /**
     * Spring 应用事件发布器，用于将事件发布到 Spring 的事件监听机制中。
     *
     * <p>Spring 会自动将实现了 {@link org.springframework.context.ApplicationListener}
     * 接口或使用 {@link org.springframework.context.event.EventListener} 注解的方法
     * 作为该事件的监听器进行调用。
     */
    private final ApplicationEventPublisher publisher;

    /**
     * 发布一个 BPM 流程实例状态变更事件。
     *
     * <p>该方法会对传入的 {@code event} 参数进行 Bean Validation 校验（由 {@link Valid} 触发），
     * 确保事件对象的字段符合预定义的约束条件（例如非空、格式正确等），避免发布无效事件。
     *
     * <p>事件发布后，所有监听 {@link BpmProcessInstanceStatusEvent} 类型的监听器
     * 都会收到通知并执行相应逻辑。
     *
     * @param event 要发布的流程实例状态事件，不能为空且需符合验证规则。
     *              通常包含流程实例 ID、状态（如进行中、已完成、已取消等）、
     *              变更时间、操作人等上下文信息。
     */
    public void sendProcessInstanceResultEvent(@Valid BpmProcessInstanceStatusEvent event) {
        publisher.publishEvent(event);
    }

}