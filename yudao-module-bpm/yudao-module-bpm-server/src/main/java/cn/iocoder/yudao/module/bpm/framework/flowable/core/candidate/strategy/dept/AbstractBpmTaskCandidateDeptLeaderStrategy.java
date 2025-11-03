package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 部门负责人任务候选人策略的抽象基类。
 * <p>
 * 该类提供了通用的工具方法，用于根据流程发起人所在的部门，向上查找指定层级或连续多级的部门负责人（即任务候选人）。
 * 具体策略子类只需实现业务逻辑中“使用哪些层级”或“如何组合候选人”的细节，而无需重复编写部门遍历逻辑。
 *
 * @author jason
 */
public abstract class AbstractBpmTaskCandidateDeptLeaderStrategy implements BpmTaskCandidateStrategy {

    /**
     * 注入部门服务 API，用于查询部门信息（如父部门、负责人等）
     */
    @Resource
    protected DeptApi deptApi;

    /**
     * 注入管理员用户服务 API，用于查询用户及其所属部门信息
     */
    @Resource
    protected AdminUserApi adminUserApi;

    /**
     * 获取指定部门向上第 {@code level} 级的部门负责人 ID。
     * <p>
     * 例如：level = 1 表示当前部门负责人；level = 2 表示当前部门的父部门负责人，以此类推。
     * 如果向上查找过程中到达根部门（无父部门），则直接返回当前已找到的最高级部门的负责人。
     *
     * @param dept  起始部门（通常为流程发起人所在部门）
     * @param level 要查找的层级（必须大于 0，1 表示当前部门，2 表示父部门，...）
     * @return 部门负责人的用户 ID；若部门为 null 或负责人为空，则返回 null
     * @throws IllegalArgumentException 如果 level <= 0
     */
    protected Long getAssignLevelDeptLeaderId(DeptRespDTO dept, Integer level) {
        // 校验 level 必须为正整数
        Assert.isTrue(level > 0, "level 必须大于 0");
        // 若起始部门为空，无法查找负责人
        if (dept == null) {
            return null;
        }

        // 从当前部门开始，向上遍历 level - 1 次（因为 level=1 时不需要向上）
        DeptRespDTO currentDept = dept;
        for (int i = 1; i < level; i++) {
            // 获取父部门
            DeptRespDTO parentDept = deptApi.getDept(currentDept.getParentId()).getCheckedData();
            if (parentDept == null) {
                // 已到组织树根节点，无法继续向上，提前终止循环
                break;
            }
            currentDept = parentDept;
        }
        // 返回最终定位到的部门的负责人 ID
        return currentDept.getLeaderUserId();
    }

    /**
     * 获取多个部门从当前层级开始，向上连续 {@code level} 级的所有部门负责人 ID 集合。
     * <p>
     * 对每个部门，依次获取其自身、父部门、祖父部门……直到 level 层或到达根部门为止，
     * 收集所有非空的负责人 ID，并去重（使用 LinkedHashSet 保持插入顺序）。
     *
     * @param deptIds 需要处理的部门 ID 列表
     * @param level   向上查找的最大层级数（>=1，表示包含当前部门及其向上 level-1 级）
     * @return 所有符合条件的部门负责人用户 ID 的有序集合（去重，按部门遍历顺序 + 层级从近到远）
     * @throws IllegalArgumentException 如果 level <= 0
     */
    protected Set<Long> getMultiLevelDeptLeaderIds(List<Long> deptIds, Integer level) {
        Assert.isTrue(level > 0, "level 必须大于 0");
        if (CollUtil.isEmpty(deptIds)) {
            return new HashSet<>();
        }

        // 使用 LinkedHashSet 保证遍历顺序且去重
        Set<Long> deptLeaderIds = new LinkedHashSet<>();
        for (Long deptId : deptIds) {
            DeptRespDTO dept = deptApi.getDept(deptId).getCheckedData();
            if (dept == null) {
                continue; // 部门不存在，跳过
            }

            // 从当前部门开始，向上最多遍历 level 层
            for (int i = 0; i < level; i++) {
                // 如果当前部门有负责人，则加入集合
                if (dept.getLeaderUserId() != null) {
                    deptLeaderIds.add(dept.getLeaderUserId());
                }

                // 尝试获取父部门
                DeptRespDTO parentDept = deptApi.getDept(dept.getParentId()).getCheckedData();
                if (parentDept == null) {
                    // 已到根部门，无法继续向上，提前结束本部门的遍历
                    break;
                }
                dept = parentDept;
            }
        }
        return deptLeaderIds;
    }

    /**
     * 根据流程发起人 ID，获取其所属的部门信息。
     * <p>
     * 先通过用户 API 查询用户详情，再通过其 deptId 查询部门详情。
     *
     * @param startUserId 流程发起人的用户 ID
     * @return 发起人所属的部门信息；若用户不存在、未分配部门或部门不存在，则返回 null
     */
    protected DeptRespDTO getStartUserDept(Long startUserId) {
        // 获取发起人用户信息
        AdminUserRespDTO startUser = adminUserApi.getUser(startUserId).getCheckedData();
        if (startUser == null || startUser.getDeptId() == null) {
            // 用户不存在 或 未分配部门
            return null;
        }
        // 根据 deptId 查询部门详情
        return deptApi.getDept(startUser.getDeptId()).getCheckedData();
    }

}