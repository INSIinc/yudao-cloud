package cn.iocoder.yudao.module.bpm.framework.flowable.core.util;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.core.KeyValue;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.form.BpmFormFieldVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelFormTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import lombok.SneakyThrows;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.common.engine.api.variable.VariableContainer;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.common.engine.impl.variable.MapDelegateVariableContainer;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.TaskInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;

/**
 * Flowable 工具类，封装流程引擎中常用的工具方法，涵盖用户认证、租户上下文、流程实例状态、任务变量、表达式求值等场景。
 *
 * @author 芋道源码
 */
public class FlowableUtils {

    // ========== 用户认证相关 ==========

    /**
     * 设置 Flowable 当前认证用户 ID（字符串形式）。
     * Flowable 内部使用字符串存储用户 ID，此处将 Long 转为 String。
     *
     * @param userId 用户 ID（Long 类型）
     */
    public static void setAuthenticatedUserId(Long userId) {
        Authentication.setAuthenticatedUserId(String.valueOf(userId));
    }

    /**
     * 清除当前 Flowable 认证用户。
     */
    public static void clearAuthenticatedUserId() {
        Authentication.setAuthenticatedUserId(null);
    }

    /**
     * 在指定用户身份下执行一段逻辑，并自动清理认证信息。
     * 适用于需要以特定用户身份启动流程、执行任务等操作。
     *
     * @param userId   用户 ID
     * @param callable 要执行的逻辑（Callable）
     * @param <V>      返回值类型
     * @return 执行结果
     */
    public static <V> V executeAuthenticatedUserId(Long userId, Callable<V> callable) {
        setAuthenticatedUserId(userId);
        try {
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            clearAuthenticatedUserId();
        }
    }

    // ========== 租户（Tenant）上下文相关 ==========

    /**
     * 获取当前线程上下文中的租户 ID（字符串形式）。
     * 若未设置租户，则返回 Flowable 定义的无租户标识（NO_TENANT_ID）。
     *
     * @return 租户 ID 字符串
     */
    public static String getTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId != null ? String.valueOf(tenantId) : ProcessEngineConfiguration.NO_TENANT_ID;
    }

    /**
     * 在指定租户上下文中执行一段无返回值的逻辑。
     * 若 tenantIdStr 为空或为 NO_TENANT_ID，则直接执行；否则切换租户上下文执行。
     *
     * @param tenantIdStr 租户 ID 字符串
     * @param runnable    要执行的逻辑
     */
    public static void execute(String tenantIdStr, Runnable runnable) {
        if (ObjectUtil.isEmpty(tenantIdStr) || Objects.equals(tenantIdStr, ProcessEngineConfiguration.NO_TENANT_ID)) {
            runnable.run(); // 无租户上下文，直接执行
        } else {
            Long tenantId = Long.valueOf(tenantIdStr);
            TenantUtils.execute(tenantId, runnable); // 在指定租户上下文中执行
        }
    }

    /**
     * 在指定租户上下文中执行一段有返回值的逻辑。
     * 若 tenantIdStr 为空或为 NO_TENANT_ID，则直接执行；否则切换租户上下文执行。
     *
     * @param tenantIdStr 租户 ID 字符串
     * @param callable    要执行的逻辑（Callable）
     * @param <V>         返回值类型
     * @return 执行结果
     */
    @SneakyThrows
    public static <V> V execute(String tenantIdStr, Callable<V> callable) {
        if (ObjectUtil.isEmpty(tenantIdStr) || Objects.equals(tenantIdStr, ProcessEngineConfiguration.NO_TENANT_ID)) {
            return callable.call();
        } else {
            Long tenantId = Long.valueOf(tenantIdStr);
            return TenantUtils.execute(tenantId, callable);
        }
    }

    // ========== 执行上下文（Execution）变量命名 ==========

    /**
     * 格式化多实例任务中“审批人列表”变量名。
     * 多实例节点（如并签、或签）会使用一个集合变量（collectionVariable）来存储所有审批人。
     * 变量名格式：{activityId}_assignees
     *
     * @param activityId 活动节点 ID（如 userTask 的 id）
     * @return 集合变量名
     */
    public static String formatExecutionCollectionVariable(String activityId) {
        return activityId + "_assignees";
    }

    /**
     * 格式化多实例任务中当前实例对应的“单个审批人”变量名。
     * Flowable 会为每个实例创建一个元素变量（collectionElementVariable）。
     * 变量名格式：{activityId}_assignee
     *
     * @param activityId 活动节点 ID
     * @return 元素变量名
     */
    public static String formatExecutionCollectionElementVariable(String activityId) {
        return activityId + "_assignee";
    }

    // ========== 流程实例（ProcessInstance）相关 ==========

    /**
     * 从运行中的流程实例中获取其状态（通过流程变量）。
     *
     * @param processInstance 运行中的流程实例
     * @return 状态码（Integer）
     */
    public static Integer getProcessInstanceStatus(ProcessInstance processInstance) {
        return getProcessInstanceStatus(processInstance.getProcessVariables());
    }

    /**
     * 从历史流程实例中获取其状态（通过流程变量）。
     *
     * @param processInstance 历史流程实例
     * @return 状态码（Integer）
     */
    public static Integer getProcessInstanceStatus(HistoricProcessInstance processInstance) {
        return getProcessInstanceStatus(processInstance.getProcessVariables());
    }

    /**
     * 从流程变量 Map 中提取流程实例状态。
     * 状态存储在名为 {@link BpmnVariableConstants#PROCESS_INSTANCE_VARIABLE_STATUS} 的变量中。
     *
     * @param processVariables 流程变量 Map
     * @return 状态码
     */
    private static Integer getProcessInstanceStatus(Map<String, Object> processVariables) {
        return (Integer) processVariables.get(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS);
    }

    /**
     * 获取流程实例的审批原因（通常由发起人填写）。
     *
     * @param processInstance 历史流程实例
     * @return 审批原因字符串
     */
    public static String getProcessInstanceReason(HistoricProcessInstance processInstance) {
        return (String) processInstance.getProcessVariables()
                .get(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_REASON);
    }

    /**
     * 获取运行中流程实例的业务表单数据（剔除系统变量）。
     *
     * @param processInstance 运行中的流程实例
     * @return 表单数据 Map
     */
    public static Map<String, Object> getProcessInstanceFormVariable(ProcessInstance processInstance) {
        Map<String, Object> processVariables = new HashMap<>(processInstance.getProcessVariables());
        return filterProcessInstanceFormVariable(processVariables);
    }

    /**
     * 获取历史流程实例的业务表单数据（剔除系统变量）。
     *
     * @param processInstance 历史流程实例
     * @return 表单数据 Map
     */
    public static Map<String, Object> getProcessInstanceFormVariable(HistoricProcessInstance processInstance) {
        Map<String, Object> processVariables = new HashMap<>(processInstance.getProcessVariables());
        return filterProcessInstanceFormVariable(processVariables);
    }

    /**
     * 过滤流程变量，移除系统保留字段（如状态、审批原因等），仅保留用户填写的业务表单字段。
     * 目前仅移除状态字段，若有其他系统字段需过滤，可在此扩展。
     *
     * @param processVariables 原始流程变量
     * @return 过滤后的业务表单数据
     */
    public static Map<String, Object> filterProcessInstanceFormVariable(Map<String, Object> processVariables) {
        processVariables.remove(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS);
        // 注意：审批原因（reason）通常也属于业务字段，是否保留取决于业务需求，此处未移除
        return processVariables;
    }

    /**
     * 获取流程发起时用户手动选择的审批人映射（节点ID → 审批人列表）。
     *
     * @param processInstance 流程实例
     * @return 审批人映射 Map
     */
    public static Map<String, List<Long>> getStartUserSelectAssignees(ProcessInstance processInstance) {
        return processInstance != null ? getStartUserSelectAssignees(processInstance.getProcessVariables()) : null;
    }

    /**
     * 从流程变量中解析发起人选择的审批人映射。
     * 存储在 {@link BpmnVariableConstants#PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES} 中。
     *
     * @param processVariables 流程变量
     * @return 审批人映射 Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<Long>> getStartUserSelectAssignees(Map<String, Object> processVariables) {
        if (processVariables == null) {
            return new HashMap<>();
        }
        return (Map<String, List<Long>>) processVariables.get(
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES);
    }

    /**
     * 获取审批过程中用户动态选择的下一节点审批人（用于自由跳转或动态指派）。
     *
     * @param processInstance 流程实例
     * @return 审批人映射 Map
     */
    public static Map<String, List<Long>> getApproveUserSelectAssignees(ProcessInstance processInstance) {
        return processInstance != null ? getApproveUserSelectAssignees(processInstance.getProcessVariables()) : null;
    }

    /**
     * 从流程变量中解析审批人动态选择的下一节点审批人。
     * 存储在 {@link BpmnVariableConstants#PROCESS_INSTANCE_VARIABLE_APPROVE_USER_SELECT_ASSIGNEES} 中。
     *
     * @param processVariables 流程变量
     * @return 审批人映射 Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<Long>> getApproveUserSelectAssignees(Map<String, Object> processVariables) {
        if (processVariables == null) {
            return new HashMap<>();
        }
        return (Map<String, List<Long>>) processVariables.get(
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_APPROVE_USER_SELECT_ASSIGNEES);
    }

    /**
     * 根据流程定义信息和流程变量，生成流程实例的“摘要”信息（用于列表页展示关键字段）。
     * 仅当流程表单类型为 NORMAL（即内置表单）时生成摘要。
     *
     * 摘要生成逻辑：
     * 1. 若配置了自定义摘要字段，则按配置顺序展示；
     * 2. 否则，默认展示表单前三个字段。
     *
     * @param processDefinitionInfo 流程定义扩展信息（含表单配置）
     * @param processVariables      流程实例变量
     * @return 摘要字段列表（键值对：标题 -> 值）
     */
    public static List<KeyValue<String, String>> getSummary(BpmProcessDefinitionInfoDO processDefinitionInfo,
                                                            Map<String, Object> processVariables) {
        // 仅 NORMAL 表单支持摘要
        if (ObjectUtil.isNull(processDefinitionInfo)
                || !BpmModelFormTypeEnum.NORMAL.getType().equals(processDefinitionInfo.getFormType())) {
            return null;
        }

        // 解析表单字段配置：field → BpmFormFieldVO
        Map<String, BpmFormFieldVO> formFieldsMap = new HashMap<>();
        processDefinitionInfo.getFormFields().forEach(formFieldStr -> {
            BpmFormFieldVO formField = JsonUtils.parseObject(formFieldStr, BpmFormFieldVO.class);
            if (formField != null) {
                formFieldsMap.put(formField.getField(), formField);
            }
        });

        // 情况一：使用自定义摘要配置
        if (ObjectUtil.isNotNull(processDefinitionInfo.getSummarySetting())
                && Boolean.TRUE.equals(processDefinitionInfo.getSummarySetting().getEnable())) {
            return convertList(processDefinitionInfo.getSummarySetting().getSummary(), item -> {
                BpmFormFieldVO formField = formFieldsMap.get(item); // item 为字段名
                if (formField != null) {
                    String value = processVariables.getOrDefault(item, "").toString();
                    return new KeyValue<>(formField.getTitle(), value);
                }
                return null;
            });
        }

        // 情况二：默认展示前三个字段
        return formFieldsMap.entrySet().stream()
                .limit(3)
                .map(entry -> new KeyValue<>(
                        entry.getValue().getTitle(),
                        MapUtil.getStr(processVariables, entry.getValue().getField(), "")
                ))
                .collect(Collectors.toList());
    }

    // ========== 任务（Task）相关 ==========

    /**
     * 从任务本地变量中获取任务状态。
     *
     * @param task 任务信息（TaskInfo）
     * @return 任务状态码
     */
    public static Integer getTaskStatus(TaskInfo task) {
        return (Integer) task.getTaskLocalVariables().get(BpmnVariableConstants.TASK_VARIABLE_STATUS);
    }

    /**
     * 获取任务的审批原因（由审批人填写）。
     *
     * @param task 任务信息
     * @return 审批原因
     */
    public static String getTaskReason(TaskInfo task) {
        return (String) task.getTaskLocalVariables().get(BpmnVariableConstants.TASK_VARIABLE_REASON);
    }

    /**
     * 获取任务的电子签名图片 URL（如手写签名）。
     *
     * @param task 任务信息
     * @return 签名图片 URL
     */
    public static String getTaskSignPicUrl(TaskInfo task) {
        return (String) task.getTaskLocalVariables().get(BpmnVariableConstants.TASK_SIGN_PIC_URL);
    }

    /**
     * 获取任务的业务表单数据（剔除系统变量）。
     *
     * @param task 任务信息
     * @return 表单数据 Map
     */
    public static Map<String, Object> getTaskFormVariable(TaskInfo task) {
        Map<String, Object> formVariables = new HashMap<>(task.getTaskLocalVariables());
        filterTaskFormVariable(formVariables);
        return formVariables;
    }

    /**
     * 过滤任务本地变量，移除系统字段（状态、原因等），保留业务字段。
     *
     * @param taskLocalVariables 任务本地变量
     * @return 过滤后的业务表单数据
     */
    public static Map<String, Object> filterTaskFormVariable(Map<String, Object> taskLocalVariables) {
        taskLocalVariables.remove(BpmnVariableConstants.TASK_VARIABLE_STATUS);
        taskLocalVariables.remove(BpmnVariableConstants.TASK_VARIABLE_REASON);
        // TASK_SIGN_PIC_URL 通常作为业务数据保留，故未移除
        return taskLocalVariables;
    }

    // ========== 表达式求值（Expression Evaluation） ==========

    /**
     * 在指定流程引擎配置和变量容器下，求值一个 EL 表达式。
     *
     * @param variableContainer           变量容器（提供表达式求值所需的上下文变量）
     * @param expressionString            表达式字符串（如 ${assignee == '1001'}）
     * @param processEngineConfiguration  流程引擎配置（用于获取 ExpressionManager）
     * @return 表达式求值结果
     */
    private static Object getExpressionValue(VariableContainer variableContainer, String expressionString,
                                             ProcessEngineConfigurationImpl processEngineConfiguration) {
        assert processEngineConfiguration != null;
        ExpressionManager expressionManager = processEngineConfiguration.getExpressionManager();
        assert expressionManager != null;
        Expression expression = expressionManager.createExpression(expressionString);
        return expression.getValue(variableContainer);
    }

    /**
     * 求值 EL 表达式，自动从当前 Flowable 上下文中获取引擎配置。
     * 若在 Flowable 命令上下文外调用（如 Controller 层），则通过 ManagementService 间接获取。
     *
     * @param variableContainer 变量容器
     * @param expressionString  表达式字符串
     * @return 表达式结果
     */
    public static Object getExpressionValue(VariableContainer variableContainer, String expressionString) {
        ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration();
        if (processEngineConfiguration != null) {
            // 在 Flowable 命令上下文中，直接使用
            return getExpressionValue(variableContainer, expressionString, processEngineConfiguration);
        }
        // 不在 Flowable 上下文中，通过 ManagementService 执行命令获取
        ManagementService managementService = SpringUtil.getBean(ManagementService.class);
        assert managementService != null;
        return managementService.executeCommand(context ->
                getExpressionValue(variableContainer, expressionString, CommandContextUtil.getProcessEngineConfiguration()));
    }

    /**
     * 对 Map 形式的变量进行 EL 表达式求值。
     * 内部将 Map 包装为 VariableContainer。
     *
     * @param variable         变量 Map
     * @param expressionString 表达式字符串
     * @return 表达式结果
     */
    public static Object getExpressionValue(Map<String, Object> variable, String expressionString) {
        VariableContainer variableContainer = new MapDelegateVariableContainer(variable, VariableContainer.empty());
        return getExpressionValue(variableContainer, expressionString);
    }

}