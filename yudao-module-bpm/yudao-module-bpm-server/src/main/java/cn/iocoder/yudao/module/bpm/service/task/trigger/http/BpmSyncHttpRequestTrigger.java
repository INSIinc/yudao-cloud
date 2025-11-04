package cn.iocoder.yudao.module.bpm.service.task.trigger.http;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO.TriggerSetting.HttpRequestTriggerSetting;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmTriggerTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * BPM 发送同步 HTTP 请求触发器
 * <p>
 * 该触发器用于在流程实例执行过程中，根据预设的 HTTP 请求配置（如 URL、Header、Body 等），
 * 同步发起一个 HTTP 请求，并（可选）将响应结果回写到流程变量中。
 * </p>
 * <p>
 * 此触发器属于“同步”类型，意味着流程引擎会等待 HTTP 请求完成后再继续执行后续节点，
 * 因此需注意避免在高延迟或不可靠的 HTTP 服务上使用，以免阻塞流程执行。
 * </p>
 *
 * @author jason
 */
@Component
@Slf4j
public class BpmSyncHttpRequestTrigger extends BpmAbstractHttpRequestTrigger {

    /**
     * 注入流程实例服务，用于根据流程实例 ID 获取完整的流程实例信息（如业务 key、变量等）。
     */
    @Resource
    private BpmProcessInstanceService processInstanceService;

    /**
     * 返回该触发器的类型枚举。
     * <p>
     * 该方法用于在触发器工厂或调度器中识别并路由到具体的触发器实现。
     * 当前实现固定返回 {@link BpmTriggerTypeEnum#HTTP_REQUEST}，
     * 表示这是一个 HTTP 请求类型的触发器。
     * </p>
     *
     * @return 触发器类型，固定为 HTTP_REQUEST
     */
    @Override
    public BpmTriggerTypeEnum getType() {
        return BpmTriggerTypeEnum.HTTP_REQUEST;
    }

    /**
     * 执行同步 HTTP 请求触发逻辑。
     * <p>
     * 该方法由流程引擎在满足触发条件时调用，传入当前流程实例 ID 和触发器配置参数（JSON 字符串）。
     * 主要步骤包括：
     * 1. 解析传入的配置参数为 {@link HttpRequestTriggerSetting} 对象；
     * 2. 根据流程实例 ID 查询完整的 {@link ProcessInstance}；
     * 3. 调用工具类发起实际的 HTTP 请求，并根据配置决定是否处理响应结果。
     * </p>
     *
     * @param processInstanceId 流程实例 ID，用于标识当前正在执行的流程
     * @param param             触发器配置参数，JSON 格式，包含 URL、Header、Body、是否回写响应等信息
     */
    @Override
    public void execute(String processInstanceId, String param) {
        // 1. 解析 HTTP 请求配置
        // 将传入的 JSON 字符串反序列化为 HttpRequestTriggerSetting 对象
        HttpRequestTriggerSetting setting = JsonUtils.parseObject(param, HttpRequestTriggerSetting.class);
        if (setting == null) {
            // 若解析失败（如 param 为 null 或格式错误），记录错误日志并提前返回
            log.error("[execute][流程({}) HTTP 触发器请求配置为空或格式错误]", processInstanceId);
            return;
        }

        // 2. 获取流程实例对象
        // 用于在 HTTP 请求上下文中携带流程相关信息（如变量、业务键等）
        ProcessInstance processInstance = processInstanceService.getProcessInstance(processInstanceId);
        if (processInstance == null) {
            log.error("[execute][流程({}) 未找到对应的流程实例]", processInstanceId);
            return;
        }

        // 3. 执行 HTTP 请求
        // 调用封装好的工具方法，传入流程实例、请求配置，并指定为同步执行（isAsync = false）
        // setting.getResponse() 指示是否需要将 HTTP 响应体回写到流程变量中（由前端配置决定）
        BpmHttpRequestUtils.executeBpmHttpRequest(
                processInstance,
                setting.getUrl(),          // 请求地址
                setting.getHeader(),       // 请求头（Map<String, String>）
                setting.getBody(),         // 请求体（支持变量占位符，如 ${variable}）
                true,                      // 同步执行（此处为 true，与类名“Sync”一致）
                setting.getResponse()      // 是否处理响应（如回写到流程变量）
        );
    }

}