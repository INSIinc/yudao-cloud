package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.user.BpmTaskCandidateUserStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import com.google.common.collect.Sets;
import jakarta.annotation.Resource;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 审批人自选策略实现类。
 * <p>
 * 该策略用于支持“由当前审批人手动选择下一节点审批人”的业务场景：
 * - 在流程启动或运行过程中，用户未预先指定下一节点处理人；
 * - 当前任务完成时，由当前审批人从前端界面选择下一节点的审批人；
 * - 所选审批人信息会作为流程变量存入流程实例；
 * - 本策略在 Flowable 引擎需要计算任务候选人（Candidate Users）时被调用，
 *   从流程变量中提取已选择的审批人列表并返回。
 * </p>
 * <p>
 * 注意：此类继承自 {@link AbstractBpmTaskCandidateDeptLeaderStrategy}，
 * 但实际并未使用部门领导相关逻辑，仅复用其基类结构（可能是历史原因或设计复用）。
 * 核心逻辑完全围绕“用户自选”展开。
 * </p>
 *
 * @author smallNorthLee
 */
@Component
public class BpmTaskCandidateApproveUserSelectStrategy extends AbstractBpmTaskCandidateDeptLeaderStrategy {

    /**
     * 注入流程实例服务，用于根据流程实例 ID 查询实例详情。
     * 使用 {@link Lazy} 注解避免与其他 Bean（如策略注册器）产生循环依赖。
     */
    @Resource
    @Lazy
    private BpmProcessInstanceService processInstanceService;

    /**
     * 返回本策略对应的枚举值，用于在策略工厂中匹配和路由。
     *
     * @return 策略枚举 {@link BpmTaskCandidateStrategyEnum#APPROVE_USER_SELECT}
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.APPROVE_USER_SELECT;
    }

    /**
     * 验证策略参数。
     * <p>
     * 由于“审批人自选”策略不依赖外部配置参数（如部门ID、角色编码等），
     * 所有审批人信息都来自流程运行时变量，因此无需参数校验。
     * </p>
     *
     * @param param 策略配置参数（本策略中不使用）
     */
    @Override
    public void validateParam(String param) {
        // 无需校验参数
    }

    /**
     * 判断策略是否需要配置参数。
     * <p>
     * 本策略完全依赖流程变量中的动态选择结果，因此不需要在 BPMN 或流程定义中配置额外参数。
     * </p>
     *
     * @return false，表示无需参数
     */
    @Override
    public boolean isParamRequired() {
        return false;
    }

    /**
     * 在流程实际执行过程中，根据当前任务节点计算候选人用户ID集合。
     * <p>
     * 逻辑如下：
     * 1. 通过 execution 获取当前流程实例 ID；
     * 2. 查询完整的 {@link ProcessInstance} 对象；
     * 3. 从流程实例的变量中提取“审批人自选映射”（activityId → List<userId>）；
     * 4. 根据当前活动节点ID（execution.getCurrentActivityId()）查找对应的审批人列表；
     * 5. 返回去重后的 LinkedHashSet（保持插入顺序）。
     * </p>
     * <p>
     * 注意：此方法在任务创建时被 Flowable 调用，用于设置任务的候选人。
     * </p>
     *
     * @param execution Flowable 执行上下文
     * @param param     策略参数（本策略忽略）
     * @return 审批人用户ID集合，若未选择则返回空集合
     */
    @Override
    public LinkedHashSet<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        // 获取流程实例
        ProcessInstance processInstance = processInstanceService.getProcessInstance(execution.getProcessInstanceId());
        Assert.notNull(processInstance, "流程实例({})不能为空", execution.getProcessInstanceId());

        // 从流程实例变量中提取“审批人自选”数据
        Map<String, List<Long>> approveUserSelectAssignees = FlowableUtils.getApproveUserSelectAssignees(processInstance);
        Assert.notNull(approveUserSelectAssignees, "流程实例({}) 的下一个执行节点审批人不能为空",
                execution.getProcessInstanceId());

        // 若未获取到映射（理论上不应发生，因已断言非空，但防御性处理）
        if (approveUserSelectAssignees == null) {
            return Sets.newLinkedHashSet();
        }

        // 获取当前活动节点对应的审批人列表
        List<Long> assignees = approveUserSelectAssignees.get(execution.getCurrentActivityId());
        // 若有数据则转为 LinkedHashSet，否则返回空集合
        return CollUtil.isNotEmpty(assignees) ? new LinkedHashSet<>(assignees) : Sets.newLinkedHashSet();
    }

    /**
     * 在流程预测（如流程图高亮、路径模拟）时，根据活动节点ID计算候选人。
     * <p>
     * 与 {@link #calculateUsersByTask} 不同，此方法不依赖运行中的 execution，
     * 而是直接使用传入的 processVariables（流程变量快照）进行计算。
     * </p>
     * <p>
     * 设计说明：
     * - 流程预测时审批人可能尚未选择，因此允许返回空集合；
     * - 前端在预测路径发现审批人为空时，应提示用户“请选择下一节点审批人”；
     * - 此方法不抛异常，确保预测流程可正常进行。
     * </p>
     *
     * @param bpmnModel             BPMN 模型（本策略未使用）
     * @param activityId            当前活动节点ID
     * @param param                 策略参数（忽略）
     * @param startUserId           流程发起人ID（本策略未使用）
     * @param processDefinitionId   流程定义ID（本策略未使用）
     * @param processVariables      流程变量（包含审批人选择结果）
     * @return 审批人用户ID集合，可能为空
     */
    @Override
    public LinkedHashSet<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId, String param,
                                                        Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        // 若流程变量为空，直接返回空集合
        if (processVariables == null) {
            return Sets.newLinkedHashSet();
        }

        // 从流程变量中提取“审批人自选”映射
        Map<String, List<Long>> approveUserSelectAssignees = FlowableUtils.getApproveUserSelectAssignees(processVariables);
        if (approveUserSelectAssignees == null) {
            return Sets.newLinkedHashSet();
        }

        // 获取指定活动节点的审批人列表
        List<Long> assignees = approveUserSelectAssignees.get(activityId);
        return CollUtil.isNotEmpty(assignees) ? new LinkedHashSet<>(assignees) : Sets.newLinkedHashSet();
    }

}