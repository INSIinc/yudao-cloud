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
 * {@link BpmTaskCandidateStrategy} 的调用器（策略执行器）
 *
 * <p>核心职责：
 * 在流程审批中，自动计算每个审批节点应该由谁来审批（即"候选人"或"审批人"）。
 *
 * <p>业务场景举例：
 * - 报销流程：第一步由直属领导审批，第二步由财务部门审批，第三步由总经理审批
 * - 请假流程：根据请假天数，自动分配给不同级别的领导审批
 *
 * <p>技术实现：
 * 使用"策略模式"，支持多种候选人计算方式（如按角色、部门、发起人、表达式等），
 * 每种方式对应一个策略实现类，本类负责统一管理和调用这些策略。
 *
 * <p>主要功能：
 * 1. 校验流程定义时，检查每个审批节点是否配置了候选人策略
 * 2. 流程运行时，根据策略计算出具体的审批人用户ID列表
 * 3. 过滤掉已禁用的用户，确保只分配给有效用户
 * 4. 处理特殊情况：候选人为空时的兜底策略、发起人与审批人相同时的跳过逻辑
 *
 * @author 芋道源码
 */
@Slf4j
public class BpmTaskCandidateInvoker {

    /**
     * 策略映射表：存储所有可用的候选人计算策略
     *
     * Key: 策略类型枚举（如：按角色、按部门、按发起人等）
     * Value: 对应的策略实现类（负责具体的候选人计算逻辑）
     *
     * 作用：根据 BPMN 模型中配置的策略类型，快速找到对应的实现类来执行计算
     */
    private final Map<BpmTaskCandidateStrategyEnum, BpmTaskCandidateStrategy> strategyMap = new HashMap<>();

    /**
     * 系统用户 API 接口
     *
     * 作用：查询用户相关信息，例如：
     * - 批量查询用户详情（用于过滤禁用用户）
     * - 检查用户状态（是否已被禁用）
     */
    private final AdminUserApi adminUserApi;

    /**
     * 构造函数：初始化策略映射表
     *
     * 工作流程：
     * 1. Spring 容器会自动将所有 BpmTaskCandidateStrategy 接口的实现类注入到 strategyList 中
     * 2. 遍历这些策略实现，根据其 getStrategy() 返回的策略类型，放入 strategyMap
     * 3. 校验不能有重复的策略类型（每种策略只能有一个实现）
     *
     * @param strategyList 所有候选人策略的实现类列表（由 Spring 自动注入）
     * @param adminUserApi 用户服务 API（用于查询用户信息）
     */
    public BpmTaskCandidateInvoker(List<BpmTaskCandidateStrategy> strategyList,
                                   AdminUserApi adminUserApi) {
        // 将每个策略实现注册到 Map 中，方便后续通过策略类型快速查找
        strategyList.forEach(strategy -> {
            // 尝试放入 Map，如果该策略类型已存在，会返回旧值
            BpmTaskCandidateStrategy oldStrategy = strategyMap.put(strategy.getStrategy(), strategy);
            // 断言旧值为 null，确保不会有重复的策略类型注册
            Assert.isNull(oldStrategy, "策略(%s) 重复", strategy.getStrategy());
        });
        this.adminUserApi = adminUserApi;
    }

    /**
     * 校验流程定义（BPMN 文件）中所有人工审批任务的候选人配置是否完整
     *
     * <p>调用时机：
     * 在部署流程定义时（发布流程前），需要先校验配置的合法性，防止部署有问题的流程。
     *
     * <p>校验内容：
     * 1. 检查每个人工审批节点（UserTask）是否配置了候选人策略
     * 2. 检查策略所需的参数是否完整（如"按角色"策略必须配置角色ID）
     * 3. 调用各策略的 validateParam() 方法，校验参数的业务合法性（如角色ID是否存在）
     *
     * <p>为什么要校验：
     * 如果审批节点没有配置候选人，流程运行到该节点时会找不到审批人，导致流程卡住无法继续。
     * 提前校验可以在部署阶段就发现问题，避免运行时出错。
     *
     * @param bpmnBytes BPMN 文件的字节数组（XML 格式的流程定义文件）
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 如果配置不完整或不合法
     */
    public void validateBpmnConfig(byte[] bpmnBytes) {
        // 步骤1：将 BPMN 文件的字节数组解析成结构化的 BpmnModel 对象
        // BpmnModel 是 Flowable 框架提供的，表示整个流程定义的内存模型
        BpmnModel bpmnModel = BpmnModelUtils.getBpmnModel(bpmnBytes);
        assert bpmnModel != null;

        // 步骤2：从 BpmnModel 中提取所有的 UserTask 节点（人工审批任务节点）
        // UserTask 就是流程图中需要人工处理的审批环节
        List<UserTask> userTaskList = BpmnModelUtils.getBpmnModelElements(bpmnModel, UserTask.class);

        // 步骤3：遍历每个 UserTask，逐个校验其候选人配置
        userTaskList.forEach(userTask -> {
            // 校验点1：检查该任务是否为"自动审批"类型
            // 自动审批（自动通过或自动拒绝）不需要人工处理，因此无需配置候选人
            Integer approveType = BpmnModelUtils.parseApproveType(userTask);
            if (ObjectUtils.equalsAny(approveType,
                    BpmUserTaskApproveTypeEnum.AUTO_APPROVE.getType(),  // 自动通过
                    BpmUserTaskApproveTypeEnum.AUTO_REJECT.getType())) { // 自动拒绝
                return; // 跳过自动审批任务的候选人校验
            }

            // 校验点2：提取该任务配置的候选人策略类型和参数
            // strategy：策略类型，如 10=按角色、20=按部门、30=按用户等
            // param：策略参数，如角色ID、部门ID、用户ID 等（具体含义由策略类型决定）
            Integer strategy = BpmnModelUtils.parseCandidateStrategy(userTask);
            String param = BpmnModelUtils.parseCandidateParam(userTask);

            // 校验点3：策略类型不能为空（每个人工任务必须配置候选人策略）
            if (strategy == null) {
                throw exception(MODEL_DEPLOY_FAIL_TASK_CANDIDATE_NOT_CONFIG, userTask.getName());
            }

            // 校验点4：根据策略类型，获取对应的策略实现对象
            BpmTaskCandidateStrategy candidateStrategy = getCandidateStrategy(strategy);

            // 校验点5：如果该策略要求必须配置参数，则参数不能为空
            // 例如"按角色"策略必须配置角色ID，"按部门"策略必须配置部门ID
            if (candidateStrategy.isParamRequired() && StrUtil.isBlank(param)) {
                throw exception(MODEL_DEPLOY_FAIL_TASK_CANDIDATE_NOT_CONFIG, userTask.getName());
            }

            // 校验点6：调用策略的 validateParam() 方法，校验参数的业务合法性
            // 例如检查角色ID是否存在、部门ID是否合法等
            candidateStrategy.validateParam(param);
        });
    }

    /**
     * 根据运行时执行上下文，计算当前审批任务的候选人用户ID集合
     *
     * <p>调用时机：
     * 在流程运行过程中，当流程引擎执行到某个 UserTask（人工审批节点）时，
     * Flowable 会调用此方法来确定哪些用户可以审批该任务。
     *
     * <p>执行流程：
     * 1. 判断是否为自动审批任务，如果是则返回空（无需人工处理）
     * 2. 根据 BPMN 配置的策略类型和参数，调用对应策略计算候选人
     * 3. 过滤掉已被禁用的用户（状态为 DISABLE 的用户）
     * 4. 如果计算结果为空，启用"候选人为空"的兜底策略（防止无人审批）
     * 5. 如果配置了"发起人与审批人相同时跳过"，则移除发起人（避免自己审批自己）
     *
     * <p>特殊处理：
     * - 使用 @DataPermission(enable = false) 禁用数据权限，确保能查询到所有候选人
     * - 在异步场景下（如定时器触发），需要手动设置租户上下文
     *
     * @param execution Flowable 的执行上下文，包含流程实例、当前节点、流程变量等信息
     * @return 候选人用户ID集合（Set<Long>），这些用户都可以认领并审批该任务
     */
    @DataPermission(enable = false) // 关闭数据权限过滤，防止因权限问题导致查不到候选人
    public Set<Long> calculateUsersByTask(DelegateExecution execution) {
        // 在异步执行场景下（如定时任务、异步作业），需要显式设置租户上下文
        // 租户上下文：多租户系统中，标识当前操作属于哪个租户，确保数据隔离
        return FlowableUtils.execute(execution.getTenantId(), () -> {
            // 步骤1：获取当前正在执行的流程节点（FlowElement）
            FlowElement flowElement = execution.getCurrentFlowElement();

            // 步骤2：判断是否为自动审批任务
            // 自动审批任务：系统自动通过或拒绝，不需要人工参与
            Integer approveType = BpmnModelUtils.parseApproveType(flowElement);
            if (ObjectUtils.equalsAny(approveType,
                    BpmUserTaskApproveTypeEnum.AUTO_APPROVE.getType(),  // 自动通过
                    BpmUserTaskApproveTypeEnum.AUTO_REJECT.getType())) { // 自动拒绝
                return new HashSet<>(); // 返回空集合，表示无需人工处理
            }

            // 步骤3：从 BPMN 模型中解析出该节点配置的候选人策略和参数
            Integer strategy = BpmnModelUtils.parseCandidateStrategy(flowElement);
            String param = BpmnModelUtils.parseCandidateParam(flowElement);

            // 步骤4：根据策略类型和参数，调用对应的策略实现来计算候选人
            // 例如：如果策略是"按角色"，则查询该角色下的所有用户
            Set<Long> userIds = getCandidateStrategy(strategy).calculateUsersByTask(execution, param);

            // 步骤5：移除已被禁用的用户
            // 原因：即使用户在角色或部门中，但如果已被禁用（如离职、停用），不应该再分配任务
            removeDisableUsers(userIds);

            // 步骤6：如果计算出的候选人为空，使用"候选人为空"兜底策略
            // 兜底策略：防止没有候选人导致流程卡住，通常会指定一个默认审批人（如管理员）
            if (CollUtil.isEmpty(userIds)) {
                userIds = getCandidateStrategy(BpmTaskCandidateStrategyEnum.ASSIGN_EMPTY.getStrategy())
                        .calculateUsersByTask(execution, param);
                // 注意：兜底策略返回的用户不再移除禁用状态，避免最终完全无人可审批
            }

            // 步骤7：获取流程实例信息，提取发起人ID
            // 流程实例（ProcessInstance）：代表一次具体的流程执行，如"张三的请假申请"
            ProcessInstance processInstance = SpringUtil.getBean(BpmProcessInstanceService.class)
                    .getProcessInstance(execution.getProcessInstanceId());
            Assert.notNull(processInstance, "流程实例({}) 不存在", execution.getProcessInstanceId());
            Long startUserId = Long.valueOf(processInstance.getStartUserId());

            // 步骤8：根据配置决定是否需要移除发起人
            // 场景：如果配置了"审批人与发起人相同时跳过"，则从候选人中移除发起人
            // 原因：避免"自己审批自己提交的流程"（如自己审批自己的请假申请）
            removeStartUserIfSkip(userIds, flowElement, startUserId);

            return userIds; // 返回最终的候选人ID集合
        });
    }

    /**
     * 根据 BPMN 模型（非运行时），预计算某个活动节点的候选人
     *
     * <p>使用场景：
     * 1. 流程预览：在流程启动前，展示"这个流程会经过哪些审批人"
     * 2. 流程模拟：管理员可以模拟流程走向，查看每个节点的审批人
     * 3. 流程分析：统计分析某个流程定义中各节点的审批人分布
     *
     * <p>与 calculateUsersByTask 的区别：
     * - calculateUsersByTask：在流程实际运行时调用，有完整的执行上下文（DelegateExecution）
     * - calculateUsersByActivity：在流程未运行或模拟场景下调用，仅基于流程定义和变量计算
     *
     * <p>计算逻辑：
     * 与运行时计算基本相同，但因为没有实际的执行上下文，需要手动传入必要的参数。
     *
     * @param bpmnModel            BPMN 流程模型对象
     * @param activityId           活动节点的ID（如 "task_manager_approve"）
     * @param startUserId          流程发起人的用户ID（用于判断是否需要跳过发起人）
     * @param processDefinitionId  流程定义ID（标识是哪个流程定义）
     * @param processVariables     流程变量（用于表达式计算、条件判断等）
     * @return 候选人用户ID集合
     */
    public Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId,
                                              Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        // 步骤1：根据活动ID从 BPMN 模型中获取对应的流程元素
        FlowElement flowElement = BpmnModelUtils.getFlowElementById(bpmnModel, activityId);

        // 步骤2：如果是子流程（CallActivity 或 SubProcess），不计算候选人
        // 原因：子流程本身不是审批节点，它内部包含的节点才需要计算候选人
        if (flowElement instanceof CallActivity || flowElement instanceof SubProcess) {
            return new HashSet<>();
        }

        // 步骤3：如果是自动审批任务，无需计算候选人
        Integer approveType = BpmnModelUtils.parseApproveType(flowElement);
        if (ObjectUtils.equalsAny(approveType,
                BpmUserTaskApproveTypeEnum.AUTO_APPROVE.getType(),
                BpmUserTaskApproveTypeEnum.AUTO_REJECT.getType())) {
            return new HashSet<>();
        }

        // 步骤4：解析候选人策略和参数，调用策略计算候选人
        Integer strategy = BpmnModelUtils.parseCandidateStrategy(flowElement);
        String param = BpmnModelUtils.parseCandidateParam(flowElement);
        Set<Long> userIds = getCandidateStrategy(strategy).calculateUsersByActivity(bpmnModel, activityId, param,
                startUserId, processDefinitionId, processVariables);

        // 步骤5：移除已禁用的用户
        removeDisableUsers(userIds);

        // 步骤6：候选人为空时，启用兜底策略
        if (CollUtil.isEmpty(userIds)) {
            userIds = getCandidateStrategy(BpmTaskCandidateStrategyEnum.ASSIGN_EMPTY.getStrategy())
                    .calculateUsersByActivity(bpmnModel, activityId, param, startUserId, processDefinitionId, processVariables);
            // 兜底策略不移除禁用用户，确保最终有人可审批
        }

        // 步骤7：根据配置决定是否移除发起人
        removeStartUserIfSkip(userIds, flowElement, startUserId);

        return userIds;
    }

    /**
     * 移除候选人集合中状态为"禁用"的用户
     *
     * <p>业务场景：
     * 某个用户可能因为离职、休假、停用等原因被管理员禁用，
     * 虽然该用户还在角色或部门中，但不应该再分配审批任务给他。
     *
     * <p>实现逻辑：
     * 1. 如果候选人集合为空，直接返回（无需处理）
     * 2. 批量查询候选人的用户信息（包含状态字段）
     * 3. 遍历候选人ID，移除以下情况的用户：
     *    - 用户不存在（用户信息为 null）
     *    - 用户状态为禁用（status = DISABLE）
     *
     * <p>注意事项：
     * 此方法会直接修改传入的集合（参数是引用传递），调用后原集合已被过滤。
     *
     * @param assigneeUserIds 候选人ID集合（会被修改：移除禁用用户）
     */
    @VisibleForTesting // 标记为测试可见，方便单元测试
    void removeDisableUsers(Set<Long> assigneeUserIds) {
        // 快速判断：如果候选人集合为空，无需查询和过滤，直接返回
        if (CollUtil.isEmpty(assigneeUserIds)) {
            return;
        }

        // 步骤1：批量查询候选人的用户详细信息
        // 返回 Map<用户ID, 用户信息>，方便后续根据ID快速查找
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(assigneeUserIds);

        // 步骤2：遍历候选人ID集合，移除不符合条件的用户
        assigneeUserIds.removeIf(id -> {
            AdminUserRespDTO user = userMap.get(id);
            // 移除条件1：用户信息不存在（可能已被删除）
            // 移除条件2：用户状态为禁用（CommonStatusEnum.DISABLE）
            return user == null || CommonStatusEnum.isDisable(user.getStatus());
        });
    }

    /**
     * 如果配置了"审批人与发起人相同时跳过"，则从候选人中移除发起人
     *
     * <p>业务场景：
     * 在某些审批流程中，可能会出现"发起人恰好也是审批人"的情况。
     * 例如：张三是部门经理，他提交了一个报销申请，但该报销流程配置为"部门经理审批"，
     * 这样就会出现"张三审批自己提交的报销"，不符合审批的独立性原则。
     *
     * <p>解决方案：
     * 在流程配置中可以启用"审批人与发起人相同时跳过"选项，
     * 系统会自动从候选人中移除发起人，让其他符合条件的人审批。
     *
     * <p>安全机制：
     * 为了避免移除发起人后导致"无人可审批"的情况，
     * 只有在候选人数量 > 1 时才会移除发起人（至少保留一个候选人）。
     *
     * <p>实现步骤：
     * 1. 判断候选人数量是否 > 1（如果只有1人，不移除，避免无人审批）
     * 2. 从 BPMN 节点配置中读取"发起人处理方式"
     * 3. 如果配置为 SKIP（跳过），则从候选人中移除发起人
     *
     * @param assigneeUserIds 候选人用户ID集合（会被修改：可能移除发起人）
     * @param flowElement     当前 BPMN 流程节点（包含配置信息）
     * @param startUserId     流程发起人的用户ID
     */
    @VisibleForTesting // 标记为测试可见，方便单元测试
    void removeStartUserIfSkip(Set<Long> assigneeUserIds, FlowElement flowElement, Long startUserId) {
        // 安全检查1：如果候选人数量 <= 1，不移除发起人
        // 原因：如果只剩1个候选人，再移除就没人审批了，会导致流程卡住
        if (CollUtil.size(assigneeUserIds) <= 1) {
            return; // 保留唯一的候选人，即使他是发起人
        }

        // 步骤1：从 BPMN 节点的扩展属性中读取"发起人处理方式"配置
        // 可能的值：
        // - SKIP: 跳过（从候选人中移除发起人）
        // - START_USER_APPROVE: 转交给发起人审批
        // - ASSIGN_USER: 转交给指定用户审批
        Integer assignStartUserHandlerType = BpmnModelUtils.parseAssignStartUserHandlerType(flowElement);

        // 步骤2：只有配置为 SKIP 时，才移除发起人
        if (ObjectUtil.notEqual(assignStartUserHandlerType, BpmUserTaskAssignStartUserHandlerTypeEnum.SKIP.getType())) {
            return; // 不是 SKIP 模式，不移除发起人
        }

        // 步骤3：从候选人集合中移除发起人ID
        // 效果：发起人将不会出现在该任务的候选人列表中，不能审批自己发起的流程
        assigneeUserIds.remove(startUserId);
    }

    /**
     * 根据策略编号获取对应的策略实现对象
     *
     * <p>工作流程：
     * 1. 根据策略编号（Integer）查找对应的策略枚举（BpmTaskCandidateStrategyEnum）
     * 2. 根据策略枚举从 strategyMap 中查找具体的策略实现类
     * 3. 进行断言校验，确保策略存在（防止配置了不存在的策略）
     *
     * <p>异常情况：
     * 如果传入的策略编号不存在或未注册，会抛出 IllegalArgumentException 异常。
     *
     * @param strategy 策略编号（对应 BpmTaskCandidateStrategyEnum 的 code 值）
     * @return 策略实现对象（用于执行候选人计算逻辑）
     * @throws IllegalArgumentException 如果策略不存在或未注册
     */
    private BpmTaskCandidateStrategy getCandidateStrategy(Integer strategy) {
        // 步骤1：根据策略编号查找对应的枚举对象
        BpmTaskCandidateStrategyEnum strategyEnum = BpmTaskCandidateStrategyEnum.valueOf(strategy);
        // 断言：确保枚举对象存在（防止传入无效的策略编号）
        Assert.notNull(strategyEnum, "策略(%s) 不存在", strategy);

        // 步骤2：从策略映射表中查找具体的实现类
        BpmTaskCandidateStrategy strategyObj = strategyMap.get(strategyEnum);
        // 断言：确保实现类已注册（防止枚举存在但未注册实现类）
        Assert.notNull(strategyObj, "策略(%s) 不存在", strategy);

        return strategyObj; // 返回策略实现对象
    }

}

