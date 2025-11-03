package cn.iocoder.yudao.module.bpm.framework.flowable.core.enums;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * BPM 任务的候选人策略枚举
 * <p>
 * 该枚举定义了在 Flowable 工作流中，如何确定某个任务节点的审批人（候选人）的策略。
 * 每种策略对应一种人员分配逻辑，可用于流程定义或动态分配。
 * </p>
 *
 * <h3>使用场景示例</h3>
 * <ul>
 *   <li>分配给指定角色的所有用户</li>
 *   <li>分配给某个部门的所有成员或负责人</li>
 *   <li>由当前审批人或流程发起人动态选择</li>
 *   <li>根据表单字段动态指定审批人</li>
 *   <li>通过表达式动态计算审批人</li>
 * </ul>
 *
 * @author 芋道源码
 */
@Getter
@AllArgsConstructor
public enum BpmTaskCandidateStrategyEnum implements ArrayValuable<Integer> {

    // ========== 固定角色/组织架构相关策略 ==========

    /**
     * 按角色分配：将任务分配给拥有指定角色的所有用户。
     */
    ROLE(10, "角色"),

    /**
     * 部门成员：将任务分配给指定部门的所有成员（包含普通成员和负责人）。
     */
    DEPT_MEMBER(20, "部门的成员"),

    /**
     * 部门负责人：将任务分配给指定部门的负责人（通常为部门主管）。
     */
    DEPT_LEADER(21, "部门的负责人"),

    /**
     * 连续多级部门负责人：从指定部门开始，向上查找多级父部门，并将任务分配给每一级的负责人。
     * 例如：员工所在部门 → 所属事业部 → 所属集团，逐级负责人。
     */
    MULTI_DEPT_LEADER_MULTI(23, "连续多级部门的负责人"),

    /**
     * 岗位：将任务分配给拥有指定岗位的所有用户。
     */
    POST(22, "岗位"),

    // ========== 用户相关策略 ==========

    /**
     * 指定用户：将任务直接分配给一个或多个具体用户（由用户 ID 列表指定）。
     */
    USER(30, "用户"),

    /**
     * 审批人自身选择：当前审批人在审批任务时，可动态选择下一个节点的审批人。
     * 通常用于“转办”或“指定下一人”场景。
     */
    APPROVE_USER_SELECT(34, "审批人自身"),

    /**
     * 发起人自选：流程发起人在提交申请时，可自行选择该节点的审批人。
     * 常用于请假、报销等需要申请人指定主管的场景。
     */
    START_USER_SELECT(35, "发起人自选"),

    /**
     * 发起人自己：任务直接分配给流程发起人。
     * 通常用于流程开始后第一个用户任务（如“信息确认”节点）。
     */
    START_USER(36, "发起人自己"),

    /**
     * 发起人所在部门的负责人：任务分配给流程发起人所在部门的负责人。
     */
    START_USER_DEPT_LEADER(37, "发起人部门负责人"),

    /**
     * 发起人连续多级部门的负责人：从发起人所在部门开始，向上逐级查找父部门，并分配给每一级的负责人。
     */
    START_USER_DEPT_LEADER_MULTI(38, "发起人连续多级部门的负责人"),

    // ========== 用户组与表单字段策略 ==========

    /**
     * 用户组：将任务分配给指定用户组内的所有成员。
     */
    USER_GROUP(40, "用户组"),

    /**
     * 表单内用户字段：任务审批人由流程表单中的某个用户类型字段动态决定。
     * 例如：报销单中的“直属领导”字段。
     */
    FORM_USER(50, "表单内用户字段"),

    /**
     * 表单内部门负责人：根据表单中指定的部门字段，取该部门的负责人作为审批人。
     */
    FORM_DEPT_LEADER(51, "表单内部门负责人"),

    // ========== 动态表达式与特殊策略 ==========

    /**
     * 流程表达式：通过 Flowable 的表达式（Expression）动态计算审批人。
     * 表达式可访问流程变量、Spring Bean 等，灵活性高。
     * 例如：${userService.getApprover(execution)}
     */
    EXPRESSION(60, "流程表达式"),

    /**
     * 审批人为空：该任务节点不指定任何候选人，通常用于自动任务或后续通过代码动态指派。
     * 注意：若用于用户任务，可能导致任务无人处理，需谨慎使用。
     */
    ASSIGN_EMPTY(1, "审批人为空");

    // ========== 静态常量与工具方法 ==========

    /**
     * 所有策略值的数组缓存，用于快速校验或枚举遍历。
     */
    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(BpmTaskCandidateStrategyEnum::getStrategy)
            .toArray(Integer[]::new);

    /**
     * 策略编码（数据库或流程配置中存储的值）
     */
    private final Integer strategy;

    /**
     * 策略的中文描述，用于前端展示或日志说明
     */
    private final String description;

    /**
     * 根据策略编码获取对应的枚举项。
     *
     * @param strategy 策略编码
     * @return 对应的枚举项，若不存在则返回 null
     */
    public static BpmTaskCandidateStrategyEnum valueOf(Integer strategy) {
        return ArrayUtil.firstMatch(o -> o.getStrategy().equals(strategy), values());
    }

    /**
     * 实现 {@link ArrayValuable} 接口，用于参数校验（如 @InEnum 注解）
     *
     * @return 所有合法策略值的数组
     */
    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}