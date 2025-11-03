package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.form;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept.AbstractBpmTaskCandidateDeptLeaderStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 表单内部门负责人作为任务候选人的策略实现。
 *
 * <p>
 * 该策略适用于 BPMN 流程中，某任务的审批人需要动态指定为“表单中某个部门字段”对应的部门的指定层级负责人。
 * 例如：表单中有一个“所属部门”字段（值为部门 ID），流程配置该任务使用本策略，并传参 "deptId|2"，
 * 则系统会查找该部门及其向上第 2 级部门的负责人（如二级部门的部门负责人），作为任务候选人。
 * </p>
 *
 * <p>
 * 参数格式：{@code 表单字段名|部门层级}
 * - 表单字段名：流程启动或运行时，存在于流程变量中的字段名，其值应为部门 ID 或部门 ID 列表。
 * - 部门层级：表示向上查找多少级部门的负责人（1 表示当前部门负责人，2 表示上级部门负责人，以此类推）。
 * </p>
 *
 * @author jason
 */
@Component
public class BpmTaskCandidateFormDeptLeaderStrategy extends AbstractBpmTaskCandidateDeptLeaderStrategy {

    /**
     * 返回本策略对应的枚举值。
     * 此值用于在 BPMN XML 或流程配置中标识使用的是“表单内部门负责人”策略。
     *
     * @return 策略枚举 {@link BpmTaskCandidateStrategyEnum#FORM_DEPT_LEADER}
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.FORM_DEPT_LEADER;
    }

    /**
     * 验证传入的策略参数格式是否合法。
     *
     * <p>
     * 参数格式必须为：{@code 表单字段名|部门层级}，例如：{@code applyDeptId|1}
     * - 分隔符为竖线 {@code |}
     * - 表单字段名不能为空
     * - 部门层级必须是大于 0 的整数
     * </p>
     *
     * @param param 策略参数字符串，由 BPMN 配置传入
     * @throws IllegalArgumentException 如果参数格式不合法
     */
    @Override
    public void validateParam(String param) {
        // 使用 | 分割参数：左边为表单中的部门字段名，右边为要查找的部门层级（1 表示本级，2 表示上级等）
        String[] params = param.split("\\|");
        // 必须且仅能有两个部分
        Assert.isTrue(params.length == 2, "参数格式不匹配，应为 '表单字段名|部门层级'");
        // 表单字段名不能为空
        Assert.notEmpty(params[0], "表单内部门字段不能为空");
        // 解析层级并校验
        int level = Integer.parseInt(params[1]);
        Assert.isTrue(level > 0, "部门层级必须大于 0");
    }

    /**
     * 在流程执行任务时，根据当前流程变量动态计算任务候选人（用户 ID 集合）。
     *
     * <p>
     * 从当前执行上下文（execution）中获取表单字段对应的部门 ID（或列表），
     * 然后调用父类方法查找这些部门向上指定层级的负责人。
     * </p>
     *
     * @param execution Flowable 流程执行上下文对象
     * @param param     策略参数，格式为 {@code 表单字段名|部门层级}
     * @return 任务候选人的用户 ID 集合（Long 类型）
     */
    @Override
    public Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        String[] params = param.split("\\|");
        // 从流程变量中获取表单字段对应的值（可能是单个部门 ID 或部门 ID 列表）
        Object result = execution.getVariable(params[0]);
        int level = Integer.parseInt(params[1]);
        // 将结果统一转换为 Long 类型的 List，然后交由父类计算多级部门负责人
        return super.getMultiLevelDeptLeaderIds(Convert.toList(Long.class, result), level);
    }

    /**
     * 在流程定义解析阶段（如部署或预览），根据流程变量预计算任务候选人。
     *
     * <p>
     * 与 {@link #calculateUsersByTask} 类似，但此方法用于非运行时场景（如流程图渲染、预计算），
     * 通过传入的 {@code processVariables} 获取表单字段值。
     * </p>
     *
     * @param bpmnModel           BPMN 模型对象
     * @param activityId          当前任务节点 ID
     * @param param               策略参数，格式为 {@code 表单字段名|部门层级}
     * @param startUserId         流程发起人 ID（本策略未直接使用，保留接口兼容）
     * @param processDefinitionId 流程定义 ID
     * @param processVariables    流程变量映射（用于获取表单字段值）
     * @return 任务候选人的用户 ID 集合
     */
    @Override
    public Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId,
                                              String param, Long startUserId, String processDefinitionId,
                                              Map<String, Object> processVariables) {
        String[] params = param.split("\\|");
        // 从传入的流程变量中获取表单字段值
        Object result = processVariables == null ? null : processVariables.get(params[0]);
        int level = Integer.parseInt(params[1]);
        // 转换并计算多级部门负责人
        return super.getMultiLevelDeptLeaderIds(Convert.toList(Long.class, result), level);
    }

}