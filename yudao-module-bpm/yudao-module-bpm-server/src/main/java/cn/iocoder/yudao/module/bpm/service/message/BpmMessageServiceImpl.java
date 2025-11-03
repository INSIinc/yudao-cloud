package cn.iocoder.yudao.module.bpm.service.message;

import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.bpm.convert.message.BpmMessageConvert;
import cn.iocoder.yudao.module.bpm.enums.message.BpmMessageEnum;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceApproveReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceRejectReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskCreatedReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskTimeoutReqDTO;
import cn.iocoder.yudao.module.system.api.sms.SmsSendApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * BPM（业务流程管理）消息通知服务的实现类。
 * 负责在流程实例或任务的关键节点（如审批通过、驳回、任务分配、超时等）发送短信通知给相关用户。
 *
 * 本类通过调用系统模块提供的短信 API（SmsSendApi）进行实际发送，
 * 并结合 Web 配置（WebProperties）生成流程详情页的跳转链接。
 *
 * @author 芋道源码
 */
@Service
@Validated // 启用方法参数的 JSR-303/380 校验（如果 DTO 中有校验注解）
@Slf4j // 自动生成日志记录器（log）
public class BpmMessageServiceImpl implements BpmMessageService {

    /**
     * 注入系统模块提供的短信发送 API，用于向指定用户发送短信。
     */
    @Resource
    private SmsSendApi smsSendApi;

    /**
     * 注入 Web 相关配置，主要用于获取管理后台前端地址，
     * 以便在短信中拼接流程实例详情页的跳转链接。
     */
    @Resource
    private WebProperties webProperties;

    /**
     * 当流程实例被审批通过时，向流程发起人发送短信通知。
     *
     * @param reqDTO 包含流程实例相关信息的请求 DTO
     */
    @Override
    public void sendMessageWhenProcessInstanceApprove(BpmMessageSendWhenProcessInstanceApproveReqDTO reqDTO) {
        // 构建短信模板所需的动态参数
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", reqDTO.getProcessInstanceName()); // 流程名称
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId())); // 详情页链接

        // 调用转换器将请求信息转为短信发送参数，并发送短信给发起人（startUserId）
        smsSendApi.sendSingleSmsToAdmin(
                BpmMessageConvert.INSTANCE.convert(
                        reqDTO.getStartUserId(), // 接收短信的用户 ID
                        BpmMessageEnum.PROCESS_INSTANCE_APPROVE.getSmsTemplateCode(), // 短信模板编码（审批通过）
                        templateParams
                )
        ).checkError(); // 检查短信发送是否失败，若失败则抛出异常
    }

    /**
     * 当流程实例被驳回时，向流程发起人发送短信通知，包含驳回原因。
     *
     * @param reqDTO 包含流程实例及驳回原因的请求 DTO
     */
    @Override
    public void sendMessageWhenProcessInstanceReject(BpmMessageSendWhenProcessInstanceRejectReqDTO reqDTO) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", reqDTO.getProcessInstanceName());
        templateParams.put("reason", reqDTO.getReason()); // 驳回原因
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId()));

        smsSendApi.sendSingleSmsToAdmin(
                BpmMessageConvert.INSTANCE.convert(
                        reqDTO.getStartUserId(),
                        BpmMessageEnum.PROCESS_INSTANCE_REJECT.getSmsTemplateCode(), // 驳回模板
                        templateParams
                )
        ).checkError();
    }

    /**
     * 当任务被分配（创建）时，向任务处理人（assignee）发送短信通知。
     *
     * @param reqDTO 包含任务和流程实例信息的请求 DTO
     */
    @Override
    public void sendMessageWhenTaskAssigned(BpmMessageSendWhenTaskCreatedReqDTO reqDTO) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", reqDTO.getProcessInstanceName());
        templateParams.put("taskName", reqDTO.getTaskName()); // 当前任务名称
        templateParams.put("startUserNickname", reqDTO.getStartUserNickname()); // 流程发起人的昵称
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId()));

        smsSendApi.sendSingleSmsToAdmin(
                BpmMessageConvert.INSTANCE.convert(
                        reqDTO.getAssigneeUserId(), // 任务处理人 ID
                        BpmMessageEnum.TASK_ASSIGNED.getSmsTemplateCode(), // 任务分配模板
                        templateParams
                )
        ).checkError();
    }

    /**
     * 当任务处理超时时，向任务处理人发送超时提醒短信。
     *
     * @param reqDTO 包含超时任务信息的请求 DTO
     */
    @Override
    public void sendMessageWhenTaskTimeout(BpmMessageSendWhenTaskTimeoutReqDTO reqDTO) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", reqDTO.getProcessInstanceName());
        templateParams.put("taskName", reqDTO.getTaskName());
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId()));

        smsSendApi.sendSingleSmsToAdmin(
                BpmMessageConvert.INSTANCE.convert(
                        reqDTO.getAssigneeUserId(),
                        BpmMessageEnum.TASK_TIMEOUT.getSmsTemplateCode(), // 任务超时模板
                        templateParams
                )
        ).checkError();
    }

    /**
     * 根据流程实例 ID 生成前端管理后台中该流程实例的详情页 URL。
     * URL 格式示例：http://admin.yudao.cn/bpm/process-instance/detail?id=xxx
     *
     * @param taskId 流程实例 ID（此处参数名为 taskId，但实际应为 processInstanceId，可能存在命名不一致）
     * @return 完整的详情页跳转链接
     */
    private String getProcessInstanceDetailUrl(String taskId) {
        // 从 WebProperties 中获取管理后台前端地址（如 http://admin.yudao.cn），拼接固定路径
        return webProperties.getAdminUi().getUrl() + "/bpm/process-instance/detail?id=" + taskId;
    }

}