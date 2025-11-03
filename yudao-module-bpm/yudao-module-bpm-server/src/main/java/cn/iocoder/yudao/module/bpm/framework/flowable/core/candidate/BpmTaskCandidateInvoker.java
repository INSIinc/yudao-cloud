package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskApproveTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskAssignStartUserHandlerTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.*;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.MODEL_DEPLOY_FAIL_TASK_CANDIDATE_NOT_CONFIG;

/**
 * {@link BpmTaskCandidateStrategy} 的调用器（策略执行器）。
 * 用于根据 BPMN 模型中配置的策略，动态计算某个用户任务（UserTask）的候选人（即可能的审批人）。
 * 支持多种策略（如指定角色、部门、发起人等），通过策略模式解耦不同计算逻辑。
 *
 * @author 芋道源码
 */
@Slf4j
public class BpmTaskCandidateInvoker {

    /**
     * 策略映射表：将策略类型（枚举值）映射到具体的策略实现类。
     */
    private final Map<BpmTaskCandidateStrategyEnum, BpmTaskCandidateStrategy> strategyMap = new HashMap<>();

    /**
     * 系统用户 API，用于查询用户状态、批量获取用户信息等。
     */
    private final AdminUserApi adminUserApi;

    /**
     * 构造函数：注入所有策略实现，并注册到 strategyMap 中。
     *
     * @param strategyList 所有已注册的候选人策略实现（由 Spring 容器注入）
     * @param adminUserApi 用户服务 API
     */
    public BpmTaskCandidateInvoker(List<BpmTaskCandidateStrategy> strategyList,
                                   AdminUserApi adminUserApi) {
        // 将每个策略按其类型放入 Map，校验不能重复
        strategyList.forEach(strategy -> {
            BpmTaskCandidateStrategy oldStrategy = strategyMap.put(strategy.getStrategy(), strategy);
            Assert.isNull(oldStrategy, "策略(%s) 重复", strategy.getStrategy());
        });
        this.adminUserApi = adminUserApi;
    }

    /**
     * 校验流程定义（BPMN）中所有人工任务的候选人配置是否完整。
     * 目的：防止因配置缺失导致任务无人审批，流程卡住。
     *
     * @param bpmnBytes BPMN 文件的字节数组（XML格式）
     */
    public void validateBpmnConfig(byte[] bpmnBytes) {
        // 解析 BPMN 模型
        BpmnModel bpmnModel = BpmnModelUtils.getBpmnModel(bpmnBytes);
        assert bpmnModel != null;

        // 获取所有 UserTask 节点
        List<UserTask> userTaskList = BpmnModelUtils.getBpmnModelElements(bpmnModel, UserTask.class);

        // 遍历每个用户任务，校验其候选人配置
        userTaskList.forEach(userTask -> {
            // 1.1 若为自动审批（自动通过/拒绝），则无需配置候选人
            Integer approveType = BpmnModelUtils.parseApproveType(userTask);
            if (ObjectUtils.equalsAny(approveType,
                    BpmUserTaskApproveTypeEnum.AUTO_APPROVE.getType(),
                    BpmUserTaskApproveTypeEnum.AUTO_REJECT.getType())) {
                return; // 跳过校验
            }

            // 1.2 获取策略类型与参数
            Integer strategy = BpmnModelUtils.parseCandidateStrategy(userTask);
            String param = BpmnModelUtils.parseCandidateParam(userTask);

            // 校验：策略必须配置
            if (strategy == null) {
                throw exception(MODEL_DEPLOY_FAIL_TASK_CANDIDATE_NOT_CONFIG, userTask.getName());
            }

            // 获取对应策略实现
            BpmTaskCandidateStrategy candidateStrategy = getCandidateStrategy(strategy);

            // 校验：若策略要求参数，则 param 不能为空
            if (candidateStrategy.isParamRequired() && StrUtil.isBlank(param)) {
                throw exception(MODEL_DEPLOY_FAIL_TASK_CANDIDATE_NOT_CONFIG, userTask.getName());
            }

            // 1.3 交由具体策略校验参数合法性（如角色ID是否合法、部门是否存在等）
            candidateStrategy.validateParam(param);
        });
    }

    /**
     * 根据运行时执行上下文（DelegateExecution），计算当前任务的候选人用户ID集合。
     * 此方法在流程运行时被 Flowable 调用（如通过表达式或监听器）。
     *
     * @param execution Flowable 的执行上下文
     * @return 候选人用户ID集合（Set<Long>）
     */
    @DataPermission(enable = false) // 禁用数据权限，防止因权限过滤导致找不到候选人
    public Set<Long> calculateUsersByTask(DelegateExecution execution) {
        // 在 Flowable 异步场景下，需显式设置租户上下文（如定时器触发）
        return FlowableUtils.execute(execution.getTenantId(), () -> {
            FlowElement flowElement = execution.getCurrentFlowElement();

            // 若为自动审批任务，直接返回空集合（无需人工处理）
            Integer approveType = BpmnModelUtils.parseApproveType(flowElement);
            if (ObjectUtils.equalsAny(approveType,
                    BpmUserTaskApproveTypeEnum.AUTO_APPROVE.getType(),
                    BpmUserTaskApproveTypeEnum.AUTO_REJECT.getType())) {
                return new HashSet<>();
            }

            // 1.1 根据 BPMN 配置获取策略与参数，并计算候选人
            Integer strategy = BpmnModelUtils.parseCandidateStrategy(flowElement);
            String param = BpmnModelUtils.parseCandidateParam(flowElement);
            Set<Long> userIds = getCandidateStrategy(strategy).calculateUsersByTask(execution, param);

            // 1.2 过滤掉已被禁用的用户（状态为 DISABLE）
            removeDisableUsers(userIds);

            // 2. 若候选人为空，则使用“审批人为空”兜底策略（如指定默认审批人）
            if (CollUtil.isEmpty(userIds)) {
                userIds = getCandidateStrategy(BpmTaskCandidateStrategyEnum.ASSIGN_EMPTY.getStrategy())
                        .calculateUsersByTask(execution, param);
                // 注意：兜底策略不再移除禁用用户，避免彻底无人审批
            }

            // 3. 若配置了“发起人与审批人相同时跳过”，且候选人数量 > 1，则移除发起人
            ProcessInstance processInstance = SpringUtil.getBean(BpmProcessInstanceService.class)
                    .getProcessInstance(execution.getProcessInstanceId());
            Assert.notNull(processInstance, "流程实例({}) 不存在", execution.getProcessInstanceId());
            Long startUserId = Long.valueOf(processInstance.getStartUserId());
            removeStartUserIfSkip(userIds, flowElement, startUserId);

            return userIds;
        });
    }

    /**
     * 根据 BPMN 模型（非运行时），预计算某个活动节点的候选人。
     * 用于流程预览、校验等场景（如“模拟审批路径”）。
     *
     * @param bpmnModel            BPMN 模型
     * @param activityId           活动节点 ID
     * @param startUserId          流程发起人ID
     * @param processDefinitionId  流程定义ID
     * @param processVariables     流程变量（用于表达式计算等）
     * @return 候选人用户ID集合
     */
    public Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId,
                                              Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        // 若为子流程（CallActivity 或 SubProcess），不计算候选人
        FlowElement flowElement = BpmnModelUtils.getFlowElementById(bpmnModel, activityId);
        if (flowElement instanceof CallActivity || flowElement instanceof SubProcess) {
            return new HashSet<>();
        }

        // 自动审批任务无需计算
        Integer approveType = BpmnModelUtils.parseApproveType(flowElement);
        if (ObjectUtils.equalsAny(approveType,
                BpmUserTaskApproveTypeEnum.AUTO_APPROVE.getType(),
                BpmUserTaskApproveTypeEnum.AUTO_REJECT.getType())) {
            return new HashSet<>();
        }

        // 1.1 计算候选人
        Integer strategy = BpmnModelUtils.parseCandidateStrategy(flowElement);
        String param = BpmnModelUtils.parseCandidateParam(flowElement);
        Set<Long> userIds = getCandidateStrategy(strategy).calculateUsersByActivity(bpmnModel, activityId, param,
                startUserId, processDefinitionId, processVariables);

        // 1.2 移除禁用用户
        removeDisableUsers(userIds);

        // 2. 候选人为空时，使用兜底策略
        if (CollUtil.isEmpty(userIds)) {
            userIds = getCandidateStrategy(BpmTaskCandidateStrategyEnum.ASSIGN_EMPTY.getStrategy())
                    .calculateUsersByActivity(bpmnModel, activityId, param, startUserId, processDefinitionId, processVariables);
            // 兜底策略不移除禁用用户
        }

        // 3. 根据配置决定是否跳过发起人
        removeStartUserIfSkip(userIds, flowElement, startUserId);

        return userIds;
    }

    /**
     * 移除候选人集合中被禁用的用户（状态为 DISABLE）。
     * 仅保留状态为 ENABLE 的有效用户。
     *
     * @param assigneeUserIds 候选人ID集合（将被修改）
     */
    @VisibleForTesting
    void removeDisableUsers(Set<Long> assigneeUserIds) {
        if (CollUtil.isEmpty(assigneeUserIds)) {
            return;
        }
        // 批量查询用户信息
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(assigneeUserIds);
        // 移除 null 或状态为禁用的用户
        assigneeUserIds.removeIf(id -> {
            AdminUserRespDTO user = userMap.get(id);
            return user == null || CommonStatusEnum.isDisable(user.getStatus());
        });
    }

    /**
     * 如果当前节点配置了“审批人与发起人相同时跳过”，则从候选人中移除发起人。
     * 注意：仅当候选人数量 > 1 时才移除，避免只剩发起人导致无人审批。
     *
     * @param assigneeUserIds 候选人集合（将被修改）
     * @param flowElement     当前 BPMN 节点
     * @param startUserId     流程发起人ID
     */
    @VisibleForTesting
    void removeStartUserIfSkip(Set<Long> assigneeUserIds, FlowElement flowElement, Long startUserId) {
        // 安全兜底：只剩一人时不跳过
        if (CollUtil.size(assigneeUserIds) <= 1) {
            return;
        }

        // 获取“发起人处理方式”配置
        Integer assignStartUserHandlerType = BpmnModelUtils.parseAssignStartUserHandlerType(flowElement);
        // 仅当配置为 SKIP 时才移除
        if (ObjectUtil.notEqual(assignStartUserHandlerType, BpmUserTaskAssignStartUserHandlerTypeEnum.SKIP.getType())) {
            return;
        }

        assigneeUserIds.remove(startUserId);
    }

    /**
     * 根据策略编号获取对应的策略实现。
     *
     * @param strategy 策略编号（对应 BpmTaskCandidateStrategyEnum 的 code）
     * @return 策略实现对象
     * @throws IllegalArgumentException 若策略不存在
     */
    private BpmTaskCandidateStrategy getCandidateStrategy(Integer strategy) {
        BpmTaskCandidateStrategyEnum strategyEnum = BpmTaskCandidateStrategyEnum.valueOf(strategy);
        Assert.notNull(strategyEnum, "策略(%s) 不存在", strategy);
        BpmTaskCandidateStrategy strategyObj = strategyMap.get(strategyEnum);
        Assert.notNull(strategyObj, "策略(%s) 不存在", strategy);
        return strategyObj;
    }

}