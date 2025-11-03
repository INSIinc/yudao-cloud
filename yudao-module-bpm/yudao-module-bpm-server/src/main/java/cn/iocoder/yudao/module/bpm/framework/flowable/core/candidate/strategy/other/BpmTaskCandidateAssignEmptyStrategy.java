package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.other;

import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskAssignEmptyHandlerTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import jakarta.annotation.Resource;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 审批人为空时的候选用户策略实现类。
 * <p>
 * 当流程节点的原始审批人（如指定用户、角色、部门等）计算结果为空时，
 * 此策略根据 BPMN 模型中配置的“审批人为空处理方式”（assignEmptyHandlerType），
 * 决定由谁来接手该任务。
 * 支持两种处理方式：
 * <ul>
 *   <li>指定特定用户（ASSIGN_USER）：从 BPMN 扩展属性中读取预设的用户 ID 列表；</li>
 *   <li>流程管理员（ASSIGN_ADMIN）：使用流程定义中配置的管理员用户列表。</li>
 * </ul>
 * 若未配置或类型不匹配，则返回空集合，表示无人可处理。
 * </p>
 *
 * @author kyle
 */
@Component
public class BpmTaskCandidateAssignEmptyStrategy implements BpmTaskCandidateStrategy {

    /**
     * 注入流程定义信息服务，用于获取流程定义的管理员用户列表。
     * 使用 {@link Lazy} 注解延迟加载，避免与流程引擎或其他服务之间产生循环依赖。
     */
    @Resource
    @Lazy
    private BpmProcessDefinitionService processDefinitionService;

    /**
     * 返回本策略对应的枚举值，用于策略工厂识别。
     *
     * @return 固定返回 {@link BpmTaskCandidateStrategyEnum#ASSIGN_EMPTY}
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.ASSIGN_EMPTY;
    }

    /**
     * 参数校验方法。
     * 本策略不依赖外部传入的 param 参数（param 在 BPMN 中通常用于传递策略配置），
     * 因此无需校验，留空。
     *
     * @param param 策略参数（本策略未使用）
     */
    @Override
    public void validateParam(String param) {
        // 无需校验参数
    }

    /**
     * 在流程运行时（任务执行阶段）计算该任务的候选用户。
     * 根据当前执行实例（execution）获取流程定义 ID 和当前节点元素，
     * 然后交由 {@link #getCandidateUsers(String, FlowElement)} 处理。
     *
     * @param execution Flowable 的执行上下文
     * @param param     策略参数（本策略未使用）
     * @return 候选用户 ID 集合（Long 类型）
     */
    @Override
    public Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        return getCandidateUsers(execution.getProcessDefinitionId(), execution.getCurrentFlowElement());
    }

    /**
     * 在流程部署或预计算阶段（非运行时）计算某节点的候选用户。
     * 此方法通常用于流程启动前的预校验、可视化展示等场景。
     *
     * @param bpmnModel             BPMN 模型对象
     * @param activityId            当前用户任务节点 ID
     * @param param                 策略参数（本策略未使用）
     * @param startUserId           流程启动用户 ID（本策略未使用）
     * @param processDefinitionId   流程定义 ID
     * @param processVariables      流程变量（本策略未使用）
     * @return 候选用户 ID 集合
     */
    @Override
    public Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId, String param,
                                              Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        FlowElement flowElement = BpmnModelUtils.getFlowElementById(bpmnModel, activityId);
        return getCandidateUsers(processDefinitionId, flowElement);
    }

    /**
     * 核心逻辑：根据 BPMN 节点的扩展属性和流程定义信息，计算审批人为空时的替补用户。
     *
     * @param processDefinitionId 流程定义 ID，用于查询流程管理员
     * @param flowElement         当前 BPMN 节点元素（通常是 UserTask）
     * @return 候选用户 ID 集合；若无匹配策略，则返回空集合
     */
    private Set<Long> getCandidateUsers(String processDefinitionId, FlowElement flowElement) {
        // 从 BPMN 扩展属性中解析“审批人为空”的处理类型
        Integer assignEmptyHandlerType = BpmnModelUtils.parseAssignEmptyHandlerType(flowElement);

        // 情况一：指定用户作为替补
        if (Objects.equals(assignEmptyHandlerType, BpmUserTaskAssignEmptyHandlerTypeEnum.ASSIGN_USER.getType())) {
            // 从 BPMN 扩展属性中读取预设的用户 ID 列表（通常在流程设计器中配置）
            return new HashSet<>(BpmnModelUtils.parseAssignEmptyHandlerUserIds(flowElement));
        }

        // 情况二：使用流程管理员作为替补
        if (Objects.equals(assignEmptyHandlerType, BpmUserTaskAssignEmptyHandlerTypeEnum.ASSIGN_ADMIN.getType())) {
            // 查询流程定义的附加信息（如管理员列表）
            BpmProcessDefinitionInfoDO processDefinition = processDefinitionService.getProcessDefinitionInfo(processDefinitionId);
            Assert.notNull(processDefinition, "流程定义({})不存在", processDefinitionId);
            // 返回流程管理员用户 ID 列表
            return new HashSet<>(processDefinition.getManagerUserIds());
        }

        // 其他情况（如未配置或类型未知）：不指定替补，返回空集合
        return new HashSet<>();
    }

}