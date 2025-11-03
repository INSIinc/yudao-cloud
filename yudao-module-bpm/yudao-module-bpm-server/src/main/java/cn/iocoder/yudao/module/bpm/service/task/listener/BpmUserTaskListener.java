package cn.iocoder.yudao.module.bpm.service.task.listener;

import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmHttpRequestParamTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import jakarta.annotation.Resource;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.engine.impl.el.FixedValue;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.parseListenerConfig;

/**
 * BPM 用户任务通用监听器
 *
 * <p>该监听器用于在 Flowable 流程引擎中用户任务（User Task）的生命周期事件（如创建、分配、完成等）发生时，
 * 自动触发配置的 HTTP 请求回调，实现与外部系统的集成。</p>
 *
 * <p>使用方式：在 BPMN 模型的用户任务节点上配置监听器（taskListener），设置 delegateExpression 为：
 * {@code ${bpmUserTaskListener}}，并通过字段注入（field injection）传入监听器配置（listenerConfig）。</p>
 *
 * <p>该类被声明为 {@code @Scope("prototype")}，确保每次任务触发监听器时都创建新实例，避免多线程或状态污染问题。</p>
 *
 * @author Lesan
 */
@Component
@Slf4j
@Scope("prototype") // 每次使用时创建新实例，避免状态共享问题
public class BpmUserTaskListener implements TaskListener {

    /**
     * Flowable BPMN 中用于引用该监听器的表达式。
     * 在 BPMN 文件中配置 delegateExpression 时应使用该值，例如：
     * <pre>
     * <flowable:taskListener event="create" delegateExpression="${bpmUserTaskListener}">
     *   <flowable:field name="listenerConfig">
     *     <flowable:string>{"path":"/api/xxx","header":[],"body":[]}</flowable:string>
     *   </flowable:field>
     * </flowable:taskListener>
     * </pre>
     */
    public static final String DELEGATE_EXPRESSION = "${bpmUserTaskListener}";

    /**
     * 注入流程实例服务，用于根据流程实例 ID 查询完整的流程实例信息（如业务 key、变量等），
     * 以便在发起 HTTP 请求时传递上下文。
     */
    @Resource
    private BpmProcessInstanceService processInstanceService;

    /**
     * 通过 Flowable 的字段注入机制（<flowable:field>）传入的监听器配置。
     * 该配置通常是一个 JSON 字符串，描述了要调用的 HTTP 接口路径、请求头、请求体等。
     * 使用 Lombok 的 @Setter 自动生成 setter 方法，供 Flowable 反射调用。
     */
    @Setter
    private FixedValue listenerConfig;

    /**
     * 监听器回调方法，在用户任务的指定事件（如 create、assignment、complete）触发时执行。
     *
     * <p>执行流程如下：</p>
     * <ol>
     *   <li>根据任务获取对应的流程实例信息；</li>
     *   <li>解析 listenerConfig 配置，得到要调用的 HTTP 接口信息（路径、头、体）；</li>
     *   <li>向请求体中注入关键任务上下文参数（流程实例ID、任务ID、任务定义Key、处理人）；</li>
     *   <li>调用 {@link BpmHttpRequestUtils#executeBpmHttpRequest} 发起 HTTP 请求；</li>
     * </ol>
     *
     * @param delegateTask Flowable 提供的任务上下文对象，包含当前任务的元数据（如 ID、assignee、processInstanceId 等）
     */
    @Override
    public void notify(DelegateTask delegateTask) {
        // 1. 获取所需基础信息
        // 根据任务关联的流程实例 ID 查询完整的流程实例（包含业务 key、启动时间、变量等）
        ProcessInstance processInstance = processInstanceService.getProcessInstance(delegateTask.getProcessInstanceId());

        // 解析从 BPMN 配置中传入的 listenerConfig 字符串（通常为 JSON），转换为结构化的监听器处理器对象
        BpmSimpleModelNodeVO.ListenerHandler listenerHandler = parseListenerConfig(listenerConfig);

        // 2. 向 HTTP 请求体中注入 Flowable 任务上下文的关键字段（作为固定值 FIXED_VALUE 传入）
        // 这些字段可用于外部系统识别当前流程任务上下文
        listenerHandler.getBody().add(new BpmSimpleModelNodeVO.HttpRequestParam()
                .setKey("processInstanceId")
                .setType(BpmHttpRequestParamTypeEnum.FIXED_VALUE.getType()) // 类型为固定值
                .setValue(delegateTask.getProcessInstanceId()));

        listenerHandler.getBody().add(new BpmSimpleModelNodeVO.HttpRequestParam()
                .setKey("assignee")
                .setType(BpmHttpRequestParamTypeEnum.FIXED_VALUE.getType())
                .setValue(delegateTask.getAssignee())); // 任务处理人（可能为 null）

        listenerHandler.getBody().add(new BpmSimpleModelNodeVO.HttpRequestParam()
                .setKey("taskDefinitionKey")
                .setType(BpmHttpRequestParamTypeEnum.FIXED_VALUE.getType())
                .setValue(delegateTask.getTaskDefinitionKey())); // BPMN 中用户任务的 id

        listenerHandler.getBody().add(new BpmSimpleModelNodeVO.HttpRequestParam()
                .setKey("taskId")
                .setType(BpmHttpRequestParamTypeEnum.FIXED_VALUE.getType())
                .setValue(delegateTask.getId())); // Flowable 生成的任务唯一 ID

        // 3. 执行 HTTP 请求
        // 调用工具类发起同步 HTTP 请求，传入流程实例用于获取变量等上下文，
        // false 表示不启用异步执行（当前为同步调用），最后一个参数为回调处理（暂未使用）
        BpmHttpRequestUtils.executeBpmHttpRequest(
                processInstance,
                listenerHandler.getPath(),     // 请求路径，如 /api/bpm/task/created
                listenerHandler.getHeader(),   // 请求头列表
                listenerHandler.getBody(),     // 请求体参数列表（含上述上下文）
                false,                         // 是否异步执行（当前为同步）
                null                           // 异步回调处理器（暂未使用）
        );

        // TODO @芋艿：是否需要根据 HTTP 响应结果做进一步处理？例如失败重试、记录日志、抛出异常中断流程等？
        // 目前设计为“尽力而为”式回调，不阻断流程执行。
    }
}