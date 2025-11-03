package cn.iocoder.yudao.module.bpm.service.task.trigger.form;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmTriggerTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.SimpleModelUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.trigger.BpmTrigger;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BPM 删除流程表单数据触发器
 * <p>
 * 该触发器用于在特定流程事件（例如任务完成、流程结束等）发生时，
 * 根据预设的条件配置，从流程实例的变量中删除指定的表单字段数据。
 * 它实现了 {@link BpmTrigger} 接口，由触发器调度器根据类型 {@link BpmTriggerTypeEnum#FORM_DELETE} 调用。
 *
 * @author jason
 */
@Component
@Slf4j
public class BpmFormDeleteTrigger implements BpmTrigger {

    /**
     * 注入流程实例服务，用于获取和修改流程变量
     */
    @Resource
    private BpmProcessInstanceService processInstanceService;

    /**
     * 返回该触发器的类型，用于匹配触发器执行的上下文。
     *
     * @return 触发器类型：FORM_DELETE（删除表单字段）
     */
    @Override
    public BpmTriggerTypeEnum getType() {
        return BpmTriggerTypeEnum.FORM_DELETE;
    }

    /**
     * 执行删除表单字段的触发逻辑。
     *
     * @param processInstanceId 流程实例 ID，用于标识当前流程上下文
     * @param param             触发器配置参数（JSON 字符串），描述需要删除哪些字段以及删除条件
     */
    @Override
    public void execute(String processInstanceId, String param) {
        // Step 1: 解析触发器的配置参数
        // 从 param（JSON 字符串）反序列化为 List<FormTriggerSetting> 对象，
        // 每个 FormTriggerSetting 描述一组字段及其删除条件。
        List<BpmSimpleModelNodeVO.TriggerSetting.FormTriggerSetting> settings =
                JsonUtils.parseObject(param, new TypeReference<List<BpmSimpleModelNodeVO.TriggerSetting.FormTriggerSetting>>() {});

        // 如果配置为空，记录错误并提前返回
        if (CollUtil.isEmpty(settings)) {
            log.error("[execute][流程({}) 删除流程表单数据触发器配置为空]", processInstanceId);
            return;
        }

        // Step 2: 获取当前流程实例的所有流程变量（即表单字段值）
        // 这些变量通常由用户在流程表单中填写，并在流程运行时作为上下文数据传递。
        Map<String, Object> processVariables =
                processInstanceService.getProcessInstance(processInstanceId).getProcessVariables();

        // Step 3.1: 根据配置，收集需要删除的字段名（变量名）
        Set<String> deleteFields = new HashSet<>(); // 使用 Set 避免重复字段

        // 遍历每一条删除配置
        for (BpmSimpleModelNodeVO.TriggerSetting.FormTriggerSetting setting : settings) {
            // 如果当前配置未指定要删除的字段，跳过
            if (CollUtil.isEmpty(setting.getDeleteFields())) {
                continue;
            }

            // 判断是否满足删除条件（支持条件表达式）
            boolean isFieldDeletedNeeded = true;

            // 如果配置了条件类型（即需要判断是否满足条件才删除）
            if (setting.getConditionType() != null) {
                // 根据条件类型、表达式、条件分组，构建最终的 SpEL 表达式字符串
                String conditionExpression = SimpleModelUtils.buildConditionExpression(
                        setting.getConditionType(),
                        setting.getConditionExpression(),
                        setting.getConditionGroups()
                );

                // 使用流程变量作为上下文，评估 SpEL 表达式的结果
                // 返回 true 表示满足条件，需要删除字段
                isFieldDeletedNeeded = BpmnModelUtils.evalConditionExpress(processVariables, conditionExpression);
            }

            // 如果满足删除条件（或没有配置条件），将该配置中的字段加入待删除集合
            if (isFieldDeletedNeeded) {
                deleteFields.addAll(setting.getDeleteFields());
            }
        }

        // Step 3.2: 执行实际的删除操作
        // 如果存在需要删除的字段，则调用服务从流程实例变量中移除它们
        if (CollUtil.isNotEmpty(deleteFields)) {
            processInstanceService.removeProcessInstanceVariables(processInstanceId, deleteFields);
            log.debug("[execute][流程({}) 删除了以下表单字段: {}]", processInstanceId, deleteFields);
        }
    }
}