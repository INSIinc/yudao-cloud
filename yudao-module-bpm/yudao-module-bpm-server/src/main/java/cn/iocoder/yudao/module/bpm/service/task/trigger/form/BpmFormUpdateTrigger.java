package cn.iocoder.yudao.module.bpm.service.task.trigger.form;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO.TriggerSetting.FormTriggerSetting;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmTriggerTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.SimpleModelUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.trigger.BpmTrigger;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * BPM 更新流程表单触发器
 * <p>
 * 该触发器用于在特定事件（如任务完成、节点进入等）发生后，
 * 根据预设的表单字段更新规则，动态更新流程实例的变量（即流程表单数据）。
 * </p>
 *
 * <p>
 * 触发场景示例：
 * - 用户提交任务时，根据业务规则自动填充或修改某些表单字段；
 * - 满足某些条件时，自动同步外部系统数据到流程变量中。
 * </p>
 *
 * @author jason
 */
@Component
@Slf4j
public class BpmFormUpdateTrigger implements BpmTrigger {

    @Resource
    private BpmProcessInstanceService processInstanceService;

    /**
     * 返回该触发器的类型，用于与 BPMN 模型中的触发器配置进行匹配。
     * 本类处理的是 {@link BpmTriggerTypeEnum#FORM_UPDATE} 类型的触发器。
     *
     * @return 触发器类型枚举值
     */
    @Override
    public BpmTriggerTypeEnum getType() {
        return BpmTriggerTypeEnum.FORM_UPDATE;
    }

    /**
     * 执行表单更新逻辑。
     * <p>
     * 该方法在流程运行过程中被调用，传入流程实例 ID 和触发器配置参数，
     * 根据配置条件动态更新流程变量（即表单字段）。
     * </p>
     *
     * @param processInstanceId 流程实例 ID
     * @param param             触发器配置参数（JSON 字符串），描述要更新哪些字段、在什么条件下更新
     */
    @Override
    public void execute(String processInstanceId, String param) {
        // 1. 解析触发器配置参数：将 JSON 字符串反序列化为 FormTriggerSetting 列表
        // 每个 FormTriggerSetting 表示一组表单字段更新规则（可能带条件）
        List<FormTriggerSetting> settings = JsonUtils.parseObject(param, new TypeReference<List<FormTriggerSetting>>() {});
        if (CollUtil.isEmpty(settings)) {
            log.error("[execute][流程({}) 更新流程表单触发器配置为空]", processInstanceId);
            return;
        }

        // 2. 获取当前流程实例的所有变量（即当前表单数据）
        // 这些变量将用于条件判断（如“当 status == 'approved' 时才更新字段”）
        Map<String, Object> processVariables = processInstanceService.getProcessInstance(processInstanceId).getProcessVariables();

        // 3. 遍历每一条更新规则，依次处理
        for (FormTriggerSetting setting : settings) {
            // 如果该规则未配置要更新的字段，跳过
            if (CollUtil.isEmpty(setting.getUpdateFormFields())) {
                continue;
            }

            // 4. 判断是否需要执行更新：根据配置的条件表达式进行求值
            boolean isFormUpdateNeeded = true;

            // 如果配置了条件类型（如脚本、表单字段比较等），则构建并求值条件表达式
            if (setting.getConditionType() != null) {
                // 根据条件类型、表达式内容、条件组，构建标准的 SpEL 或自定义表达式字符串
                String conditionExpression = SimpleModelUtils.buildConditionExpression(
                        setting.getConditionType(),
                        setting.getConditionExpression(),
                        setting.getConditionGroups()
                );

                // 使用当前流程变量对条件表达式进行求值（true/false）
                isFormUpdateNeeded = BpmnModelUtils.evalConditionExpress(processVariables, conditionExpression);
            }

            // 5. 如果条件满足（或未配置条件），则执行表单字段更新
            if (isFormUpdateNeeded) {
                // 调用服务方法，将 setting.getUpdateFormFields() 中的键值对合并到流程变量中
                // 注意：该操作会持久化到 Flowable 的运行时变量表中
                processInstanceService.updateProcessInstanceVariables(processInstanceId, setting.getUpdateFormFields());
            }
        }
    }
}