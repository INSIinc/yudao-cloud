package cn.iocoder.yudao.module.bpm.service.task.listener;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmChildProcessStartUserEmptyTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmChildProcessStartUserTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import jakarta.annotation.Resource;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.impl.el.FixedValue;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * BPM 子流程监听器：用于在调用子流程（Call Activity）时动态设置子流程的发起人（authenticated user）。
 * <p>
 * 此监听器通过 Flowable 的 ExecutionListener 机制，在子流程启动前执行，根据配置决定使用哪个用户作为子流程的启动者。
 * 支持三种发起人来源：
 * 1. 主流程发起人（默认行为）
 * 2. 表单字段指定的用户 ID
 *     - 若表单字段为空，可进一步指定兜底策略（主流程发起人 / 子流程管理员 / 主流程管理员）
 * <p>
 * 配置通过 Flowable 模型设计器中的扩展属性（extension attributes）传入，以 JSON 字符串形式存储在 {@link #listenerConfig} 中。
 *
 * @author Lesan
 */
@Component
@Slf4j
public class BpmCallActivityListener implements ExecutionListener {

    /**
     * Flowable 中用于引用该监听器的表达式，通常在 BPMN 的 callActivity 节点上配置：
     * <pre>
     * {@code <flowable:executionListener event="start" delegateExpression="${bpmCallActivityListener}" />}
     * </pre>
     */
    public static final String DELEGATE_EXPRESSION = "${bpmCallActivityListener}";

    /**
     * 从 BPMN 模型的扩展属性中读取的监听器配置（JSON 字符串），由 Flowable 自动注入。
     * 例如：{"type":2,"formField":"approver","emptyType":1}
     */
    @Setter
    private FixedValue listenerConfig;

    @Resource
    private BpmProcessDefinitionService processDefinitionService;

    @Resource
    private BpmProcessInstanceService processInstanceService;

    /**
     * Flowable 在执行到 Call Activity 节点时触发此方法。
     * 本方法的核心职责：解析配置，确定子流程应由哪个用户发起，并通过 {@link FlowableUtils#setAuthenticatedUserId(Long)} 设置。
     *
     * @param execution Flowable 执行上下文，包含当前流程实例、变量、定义 ID 等信息
     */
    @Override
    public void notify(DelegateExecution execution) {
        // 从扩展属性中获取配置的 JSON 字符串
        String expressionText = listenerConfig.getExpressionText();
        Assert.notNull(expressionText, "监听器扩展字段({})不能为空", expressionText);

        // 将 JSON 字符串反序列化为配置对象
        BpmSimpleModelNodeVO.ChildProcessSetting.StartUserSetting startUserSetting = JsonUtils.parseObject(
                expressionText, BpmSimpleModelNodeVO.ChildProcessSetting.StartUserSetting.class);

        // 获取主流程（根流程）实例，用于读取其发起人等信息
        ProcessInstance processInstance = processInstanceService.getProcessInstance(execution.getRootProcessInstanceId());

        // ========== 情况 1：未配置 或 配置为“使用主流程发起人” ==========
        if (startUserSetting == null
                || startUserSetting.getType().equals(BpmChildProcessStartUserTypeEnum.MAIN_PROCESS_START_USER.getType())) {
            // 直接使用主流程的启动用户作为子流程的认证用户
            FlowableUtils.setAuthenticatedUserId(Long.parseLong(processInstance.getStartUserId()));
            return;
        }

        // ========== 情况 2：配置为“从表单字段取值” ==========
        if (startUserSetting.getType().equals(BpmChildProcessStartUserTypeEnum.FROM_FORM.getType())) {
            // 从主流程的流程变量中获取指定表单字段的值（预期为用户 ID 字符串）
            String formFieldValue = MapUtil.getStr(processInstance.getProcessVariables(), startUserSetting.getFormField());

            // ------ 2.1 表单字段值为空的情况 ------
            if (StrUtil.isEmpty(formFieldValue)) {
                // 根据配置的“空值处理策略”决定兜底用户

                // 2.1.1 使用主流程发起人
                if (startUserSetting.getEmptyType().equals(BpmChildProcessStartUserEmptyTypeEnum.MAIN_PROCESS_START_USER.getType())) {
                    FlowableUtils.setAuthenticatedUserId(Long.parseLong(processInstance.getStartUserId()));
                    return;
                }

                // 2.1.2 使用子流程的管理员（取第一个）
                if (startUserSetting.getEmptyType().equals(BpmChildProcessStartUserEmptyTypeEnum.CHILD_PROCESS_ADMIN.getType())) {
                    // 获取当前子流程的定义信息（即 Call Activity 所指向的流程定义）
                    BpmProcessDefinitionInfoDO processDefinition = processDefinitionService.getProcessDefinitionInfo(execution.getProcessDefinitionId());
                    List<Long> managerUserIds = processDefinition.getManagerUserIds();
                    // 安全起见：此处假设管理员列表非空（实际业务中应校验）
                    FlowableUtils.setAuthenticatedUserId(managerUserIds.get(0));
                    return;
                }

                // 2.1.3 使用主流程的管理员（取第一个）
                if (startUserSetting.getEmptyType().equals(BpmChildProcessStartUserEmptyTypeEnum.MAIN_PROCESS_ADMIN.getType())) {
                    // 获取主流程的定义信息
                    BpmProcessDefinitionInfoDO processDefinition = processDefinitionService.getProcessDefinitionInfo(processInstance.getProcessDefinitionId());
                    List<Long> managerUserIds = processDefinition.getManagerUserIds();
                    FlowableUtils.setAuthenticatedUserId(managerUserIds.get(0));
                    return;
                }
            }

            // ------ 2.2 表单字段有值，尝试解析为用户 ID ------
            try {
                // 尝试直接转换为 Long（适用于单个用户 ID 字符串，如 "1001"）
                FlowableUtils.setAuthenticatedUserId(Long.parseLong(formFieldValue));
            } catch (NumberFormatException ex) {
                // 转换失败：可能是 JSON 数组格式（如多选用户场景 "[1001,1002]"）
                try {
                    // 尝试解析为 Long 列表，并取第一个用户作为发起人
                    List<Long> formFieldValues = JsonUtils.parseArray(formFieldValue, Long.class);
                    FlowableUtils.setAuthenticatedUserId(formFieldValues.get(0));
                } catch (Exception e) {
                    // 所有解析均失败：记录日志，并降级使用主流程发起人
                    log.error("[notify][监听器：{}，子流程监听器设置流程的发起人字符串转 Long 失败，字符串：{}]",
                            DELEGATE_EXPRESSION, formFieldValue);
                    FlowableUtils.setAuthenticatedUserId(Long.parseLong(processInstance.getStartUserId()));
                }
            }
        }
    }
}