package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept;

import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.framework.common.util.string.StrUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 【任务候选人策略】连续多级部门负责人的实现类。
 *
 * 用途：当流程任务需要指定“多个部门向上连续 N 级的负责人”作为审批人时，使用此策略。
 * 例如：指定部门 A 和 B，向上找 2 级，则会找到 A 的上级的上级负责人 + B 的上级的上级负责人。
 *
 * @author jason
 */
@Component
public class BpmTaskCandidateDeptLeaderMultiStrategy extends AbstractBpmTaskCandidateDeptLeaderStrategy {

    /**
     * 返回本策略对应的枚举值，用于系统识别这是“多部门连续多级负责人”策略。
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.MULTI_DEPT_LEADER_MULTI;
    }

    /**
     * 验证传入的参数是否合法。
     *
     * 参数格式要求：两个部分用竖线 "|" 分隔。
     *  - 左边：部门 ID 列表，多个部门用英文逗号 "," 分隔，例如 "10,20,30"
     *  - 右边：要向上查找的部门层级数（必须是大于 0 的整数），例如 "2"
     *
     * 示例完整参数："10,20|3" 表示：从部门 10 和 20 开始，分别向上找 3 级的部门负责人。
     */
    @Override
    public void validateParam(String param) {
        // 按 "|" 拆分参数
        String[] params = param.split("\\|");
        // 必须恰好拆成两部分，否则格式错误
        Assert.isTrue(params.length == 2, "参数格式不匹配，应为：部门ID列表|层级数");

        // 解析左边的部门 ID 列表
        List<Long> deptIds = StrUtils.splitToLong(params[0], ",");
        // 解析右边的层级数
        int level = Integer.parseInt(params[1]);

        // 调用部门服务，检查这些部门 ID 是否真实存在
        deptApi.validateDeptList(deptIds).checkError();
        // 层级数必须大于 0（不能是 0 或负数）
        Assert.isTrue(level > 0, "部门层级必须大于 0");
    }

    /**
     * 根据参数计算出最终的任务候选人（即用户 ID 集合）。
     *
     * 步骤：
     *  1. 解析参数，得到部门 ID 列表和层级数。
     *  2. 调用父类方法，获取这些部门向上连续 level 级的所有负责人用户 ID。
     *
     * 返回结果：所有匹配到的负责人的用户 ID 集合（去重）。
     */
    @Override
    public Set<Long> calculateUsers(String param) {
        // 拆分参数
        String[] params = param.split("\\|");
        // 获取部门 ID 列表
        List<Long> deptIds = StrUtils.splitToLong(params[0], ",");
        // 获取要向上查找的层级数
        int level = Integer.parseInt(params[1]);
        // 调用父类的通用逻辑：获取多级部门负责人
        return super.getMultiLevelDeptLeaderIds(deptIds, level);
    }

}