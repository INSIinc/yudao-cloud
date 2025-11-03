package cn.iocoder.yudao.module.bpm.framework.flowable.core.listener;

import cn.iocoder.yudao.module.bpm.enums.definition.BpmTriggerTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.service.task.trigger.BpmTrigger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;

import static cn.iocoder.yudao.module.bpm.framework.flowable.core.listener.BpmTriggerTaskDelegate.BEAN_NAME;

/**
 * 触发器任务的 Flowable {@link JavaDelegate} 实现类。
 * <p>
 * 该类用于在 Flowable 流程执行过程中，当流程到达一个【触发器节点】（即配置了此 Delegate 的 ServiceTask 节点）时，
 * 根据 BPMN 节点上定义的触发器类型（如 HTTP、消息、定时等）调用对应的 {@link BpmTrigger} 实现类来执行具体逻辑。
 * <p>
 * 当前主要用于 Simple 流程设计器中的【触发器节点】功能。
 * <p>
 * 该 Bean 以固定名称 {@link #BEAN_NAME} 注册，以便在 BPMN XML 中通过 ${bpmTriggerTaskDelegate} 引用。
 *
 * @author jason
 */
@Component(BEAN_NAME)
@Slf4j
public class BpmTriggerTaskDelegate implements JavaDelegate {

    /**
     * 在 BPMN 文件中引用该 Delegate 时使用的固定 Bean 名称。
     * 例如：<flowable:class="${bpmTriggerTaskDelegate}"/>
     */
    public static final String BEAN_NAME = "bpmTriggerTaskDelegate";

    /**
     * 注入所有实现了 {@link BpmTrigger} 接口的 Spring Bean。
     * 每个 Bean 对应一种触发器类型（如 HTTP 触发器、消息触发器等）。
     */
    @Resource
    private List<BpmTrigger> triggers;

    /**
     * 触发器类型到具体触发器实现的映射缓存。
     * 使用 {@link EnumMap} 提高根据枚举类型查找触发器的性能。
     */
    private final EnumMap<BpmTriggerTypeEnum, BpmTrigger> triggerMap = new EnumMap<>(BpmTriggerTypeEnum.class);

    /**
     * Bean 初始化完成后执行的方法。
     * 遍历所有注入的 {@link BpmTrigger} 实例，按其支持的触发器类型构建映射表。
     * 这样在流程运行时可快速根据类型查找对应的触发器。
     */
    @PostConstruct
    private void init() {
        triggers.forEach(trigger -> triggerMap.put(trigger.getType(), trigger));
    }

    /**
     * Flowable 流程引擎在执行到绑定该 Delegate 的节点时会调用此方法。
     * <p>
     * 主要逻辑：
     * 1. 获取当前正在执行的 BPMN 元素（通常是 ServiceTask）；
     * 2. 从该元素的扩展属性（extension attributes）中解析出配置的触发器类型；
     * 3. 根据类型从缓存中找到对应的 {@link BpmTrigger} 实现；
     * 4. 调用该触发器的 {@link BpmTrigger#execute(String, Object)} 方法，传入流程实例 ID 和触发参数。
     *
     * @param execution Flowable 提供的执行上下文，包含流程实例、当前节点、变量等信息
     */
    @Override
    public void execute(DelegateExecution execution) {
        // 获取当前流程节点的 BPMN 模型元素
        FlowElement flowElement = execution.getCurrentFlowElement();

        // 从 BPMN 元素的扩展属性中解析出配置的触发器类型（例如：HTTP、MESSAGE 等）
        BpmTriggerTypeEnum bpmTriggerType = BpmnModelUtils.parserTriggerType(flowElement);

        // 根据类型查找对应的触发器实现
        BpmTrigger bpmTrigger = triggerMap.get(bpmTriggerType);

        // 如果未找到匹配的触发器，记录错误日志并跳过执行
        if (bpmTrigger == null) {
            log.error("[execute][FlowElement({}), {} 找不到匹配的触发器]",
                    execution.getCurrentActivityId(), flowElement);
            return;
        }

        // 执行触发器逻辑：
        // - 第一个参数为当前流程实例 ID，用于关联业务上下文；
        // - 第二个参数为从 BPMN 节点解析出的触发器参数（如 URL、消息体等，具体结构由设计决定）
        bpmTrigger.execute(
                execution.getProcessInstanceId(),
                BpmnModelUtils.parserTriggerParam(flowElement)
        );
    }
}