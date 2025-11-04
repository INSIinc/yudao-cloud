package cn.iocoder.yudao.module.bpm.service.task.trigger.http;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmHttpRequestParamTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmTriggerTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * BPM HTTP 回调触发器
 *
 * <p>
 * 该触发器用于在流程运行到特定节点时，向外部系统发起一个 HTTP 回调请求。
 * 通常用于通知外部服务“流程已到达某节点”，并允许外部服务后续通过回调接口继续驱动流程（例如完成用户任务）。
 * </p>
 *
 * <p>
 * 触发器类型为 {@link BpmTriggerTypeEnum#HTTP_CALLBACK}，表示这是一个 HTTP 回调类型的触发动作。
 * </p>
 *
 * @author jason
 */
@Component
@Slf4j
public class BpmHttpCallbackTrigger extends BpmAbstractHttpRequestTrigger {

    /**
     * 注入流程实例服务，用于根据流程实例 ID 查询流程实例详细信息。
     */
    @Resource
    private BpmProcessInstanceService processInstanceService;

    /**
     * 返回该触发器的类型枚举值，用于在触发器工厂或调度逻辑中识别并路由到此类。
     *
     * @return 触发器类型：HTTP 回调
     */
    @Override
    public BpmTriggerTypeEnum getType() {
        return BpmTriggerTypeEnum.HTTP_CALLBACK;
    }

    /**
     * 执行 HTTP 回调触发逻辑。
     *
     * <p>
     * 此方法会在流程运行到配置了 HTTP 回调触发器的节点时被调用。
     * </p>
     *
     * @param processInstanceId 当前流程实例的唯一标识（由 Flowable 引擎生成）
     * @param param             触发器配置参数，以 JSON 字符串形式传入，包含目标 URL、Headers、Body 等
     */
    @Override
    public void execute(String processInstanceId, String param) {
        // 1. 将传入的 JSON 配置字符串反序列化为具体的 HTTP 请求配置对象
        BpmSimpleModelNodeVO.TriggerSetting.HttpRequestTriggerSetting setting = JsonUtils.parseObject(param,
                BpmSimpleModelNodeVO.TriggerSetting.HttpRequestTriggerSetting.class);

        // 如果配置解析失败（例如 param 为空或格式错误），记录错误日志并提前返回
        if (setting == null) {
            log.error("[execute][流程({}) HTTP 回调触发器配置为空或解析失败]", processInstanceId);
            return;
        }

        // 2. 根据流程实例 ID 获取完整的流程实例对象（用于后续请求上下文构建）
        ProcessInstance processInstance = processInstanceService.getProcessInstance(processInstanceId);

        // 3. 向请求体（Body）中动态注入一个关键参数：taskDefineKey
        //    - 该参数表示当前触发回调的用户任务节点的定义键（task definition key）
        //    - 外部系统收到此参数后，可在后续回调 Flowable 的“完成任务”接口时传回，
        //      从而让系统知道应该完成哪一个具体任务节点
        setting.getBody().add(
                new BpmSimpleModelNodeVO.HttpRequestParam()
                        .setKey("taskDefineKey") // 参数名固定为 "taskDefineKey"
                        .setType(BpmHttpRequestParamTypeEnum.FIXED_VALUE.getType()) // 类型为固定值（非表达式、非变量）
                        .setValue(setting.getCallbackTaskDefineKey()) // 值来自配置中的回调任务定义键
        );

        // 4. 使用统一的 HTTP 请求工具类发起请求
        //    - 会自动注入流程变量、认证信息（如 token）、上下文等
        //    - 此处 isAsync = false 表示同步调用（当前线程等待响应）
        //    - 最后一个参数为回调处理器（此处为 null，表示不处理响应）
        BpmHttpRequestUtils.executeBpmHttpRequest(
                processInstance,     // 当前流程实例，用于提取流程变量等上下文
                setting.getUrl(),    // 目标回调 URL
                setting.getHeader(), // 请求头（如 Content-Type、Authorization 等）
                setting.getBody(),   // 请求体参数（包含用户配置 + 注入的 taskDefineKey）
                false,               // 是否异步执行（false = 同步）
                null                 // 响应处理器（null 表示忽略响应内容）
        );
    }
}