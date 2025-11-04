package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept;

import cn.iocoder.yudao.framework.common.util.string.StrUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * 【任务候选人策略】部门负责人策略
 *
 * 这个类的作用是：当流程中的某个任务指定了“部门负责人”作为候选人时，
 * 系统会调用这个类，根据传入的部门 ID 列表，找出这些部门的负责人（即用户 ID），
 * 并将他们作为该任务的可处理人。
 *
 * 举个例子：
 * 如果流程配置说“让销售部和市场部的负责人审批”，那么这个类就会查出这两个部门的负责人是谁，
 * 然后把他们的用户 ID 返回给流程引擎，让他们成为审批人。
 *
 * @author kyle
 */
@Component // 表示这是一个 Spring 管理的组件，会被自动扫描并注入到容器中
public class BpmTaskCandidateDeptLeaderStrategy implements BpmTaskCandidateStrategy {

    // 自动注入部门服务接口，用于查询部门信息
    @Resource
    private DeptApi deptApi;

    /**
     * 返回该策略对应的枚举类型。
     * 这样系统就知道这个类对应的是“部门负责人”这种候选人类型。
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.DEPT_LEADER;
    }

    /**
     * 校验传入的参数是否合法。
     *
     * 参数 param 是一个字符串，格式如 "10,20,30"，表示多个部门 ID。
     * 本方法会：
     * 1. 把字符串拆分成部门 ID 集合（Set<Long>）
     * 2. 调用部门服务，验证这些部门是否存在
     * 3. 如果有无效的部门 ID，会抛出异常（checkError() 会处理错误）
     */
    @Override
    public void validateParam(String param) {
        // 将逗号分隔的字符串（如 "1,2,3"）转换成 Set<Long>
        Set<Long> deptIds = StrUtils.splitToLongSet(param);
        // 调用部门 API 校验这些部门 ID 是否都有效
        deptApi.validateDeptList(deptIds).checkError();
    }

    /**
     * 根据参数计算出实际的候选人用户 ID 列表。
     *
     * 步骤说明：
     * 1. 将传入的参数（如 "10,20"）解析成部门 ID 集合
     * 2. 调用部门服务，查询这些部门的详细信息（包括负责人是谁）
     * 3. 从每个部门信息中提取“负责人用户 ID”
     * 4. 返回所有负责人的用户 ID 集合
     *
     * 注意：如果某个部门没有设置负责人，则 getLeaderUserId() 可能返回 null，
     * convertSet 工具方法通常会自动过滤掉 null 值（具体取决于其实现）。
     */
    @Override
    public Set<Long> calculateUsers(String param) {
        // 解析出部门 ID 集合
        Set<Long> deptIds = StrUtils.splitToLongSet(param);
        // 根据部门 ID 查询部门详情（已校验过，所以这里认为都是有效的）
        List<DeptRespDTO> depts = deptApi.getDeptList(deptIds).getCheckedData();
        // 提取每个部门的负责人用户 ID，组成一个 Set 并返回
        return convertSet(depts, DeptRespDTO::getLeaderUserId);
    }

}