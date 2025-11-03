package cn.iocoder.yudao.module.bpm.api.event;

import cn.hutool.core.util.StrUtil;
import org.springframework.context.ApplicationListener;

/**
 * BPM 流程实例状态变更事件的抽象监听器。
 *
 * <p>该类实现了 Spring 的 {@link ApplicationListener} 接口，用于监听 {@link BpmProcessInstanceStatusEvent} 类型的事件。
 * 它通过模板方法模式，要求子类指定要监听的流程定义 Key（processDefinitionKey），并仅在事件匹配该 Key 时触发具体处理逻辑。
 *
 * <p>设计目的：
 * <ul>
 *   <li>避免所有子类重复编写流程定义 Key 的判断逻辑；</li>
 *   <li>每个具体业务监听器只需关注自己关心的流程定义 Key 和对应的事件处理逻辑；</li>
 *   <li>提高代码复用性和可维护性。</li>
 * </ul>
 *
 * @author 芋道源码
 */
public abstract class BpmProcessInstanceStatusEventListener
        implements ApplicationListener<BpmProcessInstanceStatusEvent> {

    /**
     * Spring 容器在发布 {@link BpmProcessInstanceStatusEvent} 事件时会自动调用此方法。
     *
     * <p>本方法为 final，禁止子类重写，以确保统一的事件过滤逻辑：
     * <ol>
     *   <li>首先获取当前事件携带的流程定义 Key（{@code event.getProcessDefinitionKey()}）；</li>
     *   <li>与子类通过 {@link #getProcessDefinitionKey()} 返回的目标流程定义 Key 进行比较；</li>
     *   <li>若两者不一致，则直接忽略该事件，不进行后续处理；</li>
     *   <li>若一致，则调用 {@link #onEvent(BpmProcessInstanceStatusEvent)} 执行具体业务逻辑。</li>
     * </ol>
     *
     * <p>使用 {@link StrUtil#equals(CharSequence, CharSequence)} 进行字符串比较，
     * 该工具方法会安全处理 null 值，避免空指针异常。
     *
     * @param event 发布的流程实例状态变更事件
     */
    @Override
    public final void onApplicationEvent(BpmProcessInstanceStatusEvent event) {
        // 判断事件中的流程定义 Key 是否与当前监听器关心的 Key 一致
        if (!StrUtil.equals(event.getProcessDefinitionKey(), getProcessDefinitionKey())) {
            // 不匹配则直接返回，不处理
            return;
        }
        // 匹配则交由子类实现具体逻辑
        onEvent(event);
    }

    /**
     * 抽象方法：子类必须实现此方法，返回其希望监听的 BPMN 流程定义的 Key。
     *
     * <p>流程定义 Key 通常是在 BPMN 文件中定义的 {@code process} 元素的 {@code id} 属性值，
     * 例如："leave_apply"、"expense_approval" 等。
     *
     * <p>该方法的返回值用于在 {@link #onApplicationEvent} 中进行事件过滤。
     *
     * @return 当前监听器关心的流程定义 Key，不可为 null（建议返回明确的字符串常量）
     */
    protected abstract String getProcessDefinitionKey();

    /**
     * 抽象方法：当事件的流程定义 Key 与 {@link #getProcessDefinitionKey()} 返回值匹配时，
     * 该方法会被调用，用于执行具体的业务逻辑。
     *
     * <p>子类在此方法中可实现如：
     * <ul>
     *   <li>更新业务单据状态；</li>
     *   <li>发送通知消息；</li>
     *   <li>触发后续系统集成等。</li>
     * </ul>
     *
     * @param event 匹配成功的流程实例状态事件，包含流程实例 ID、状态、流程定义 Key 等信息
     */
    protected abstract void onEvent(BpmProcessInstanceStatusEvent event);

}