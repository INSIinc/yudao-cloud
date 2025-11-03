package cn.iocoder.yudao.module.bpm.api.event;

import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 回款审批流程状态变更的监听器实现类。
 * <p>
 * 该监听器用于监听 Flowable 工作流引擎中 "crm-receivable-audit" 流程实例的状态变化事件（如审批通过、驳回等），
 * 并在事件触发时，向 CRM 服务发起 HTTP 请求，通知其更新对应回款记录的审核状态。
 * </p>
 *
 * <p>
 * 本类继承自 {@link BpmProcessInstanceStatusEventListener}，这是系统中用于统一处理 BPM（业务流程管理）
 * 流程实例状态变更事件的抽象基类。子类只需实现特定流程的监听逻辑即可。
 * </p>
 *
 * @author 芋道源码
 */
public class CrmReceivableStatusListener extends BpmProcessInstanceStatusEventListener {

    /**
     * 获取该监听器所监听的 Flowable 流程定义的 Key。
     * <p>
     * 此 Key 必须与 BPMN 文件中定义的流程 ID（processDefinitionKey）完全一致。
     * 系统将根据此 Key 将对应的流程实例状态变更事件路由到本监听器进行处理。
     * </p>
     *
     * @return 流程定义 Key，此处为 "crm-receivable-audit"，表示“回款审批”流程。
     */
    @Override
    public String getProcessDefinitionKey() {
        return "crm-receivable-audit";
    }

    /**
     * 处理流程实例状态变更事件的回调方法。
     * <p>
     * 当 Flowable 引擎中 "crm-receivable-audit" 流程实例的状态发生变化（如完成、终止、驳回等）时，
     * 该方法会被自动触发。方法接收一个包含事件详情的 {@link BpmProcessInstanceStatusEvent} 对象。
     * </p>
     *
     * <p>
     * 本方法通过 {@link BpmHttpRequestUtils#executeBpmHttpRequest} 工具方法，
     * 向 CRM 服务的指定 RPC 接口发送 HTTP 请求，携带事件数据，用于更新回款单的审核状态。
     * </p>
     *
     * @param event 流程实例状态变更事件对象，包含流程实例 ID、业务单据 ID、审批结果等关键信息。
     *              使用 {@link RequestBody} 和 {@link Valid} 注解确保该参数通过 HTTP 请求体传入，
     *              并进行参数校验（虽然在事件监听上下文中通常由内部调用，但保留注解以保持接口一致性）。
     */
    @Override
    public void onEvent(@RequestBody @Valid BpmProcessInstanceStatusEvent event) {
        // 调用通用的 HTTP 请求工具，将事件数据转发给 CRM 服务
        // 目标 URL 为 CRM 服务中处理回款审核状态更新的 RPC 接口
        BpmHttpRequestUtils.executeBpmHttpRequest(event,
                "http://crm-server/rpc-api/crm/receivable/update-audit-status");
    }

}