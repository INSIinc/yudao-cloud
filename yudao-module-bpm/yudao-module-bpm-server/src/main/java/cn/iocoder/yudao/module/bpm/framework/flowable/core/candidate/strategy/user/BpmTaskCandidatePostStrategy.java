package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.user;

import cn.iocoder.yudao.framework.common.util.string.StrUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * 基于“岗位”（Post）的流程任务候选人策略实现类。
 * <p>
 * 此策略用于在 Flowable 流程引擎中，根据指定的岗位 ID 列表，动态计算出可以作为任务候选人的用户集合。
 * 典型应用场景：某个审批节点应由“财务部经理”岗位的所有在职人员处理。
 *
 * @author kyle
 */
@Component
public class BpmTaskCandidatePostStrategy implements BpmTaskCandidateStrategy {

    // 注入岗位服务 API，用于校验岗位是否存在
    @Resource
    private PostApi postApi;

    // 注入用户服务 API，用于根据岗位 ID 获取关联的用户列表
    @Resource
    private AdminUserApi adminUserApi;

    /**
     * 返回该策略对应的枚举类型。
     * 用于在流程定义或运行时标识使用的是“岗位”策略。
     *
     * @return 枚举值 BpmTaskCandidateStrategyEnum.POST
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.POST;
    }

    /**
     * 校验传入的策略参数（即岗位 ID 字符串）是否合法。
     * <p>
     * 参数格式应为逗号分隔的岗位 ID 列表（如："101,102,103"）。
     * 该方法会：
     * 1. 将字符串解析为 Long 类型的岗位 ID 集合；
     * 2. 调用 PostApi 校验这些岗位是否真实存在（防止传入无效 ID）。
     *
     * @param param 岗位 ID 字符串，例如 "1,2,3"
     * @throws IllegalArgumentException 如果包含无效岗位 ID
     */
    @Override
    public void validateParam(String param) {
        // 使用工具类将字符串按逗号分割并转为 Set<Long>
        Set<Long> postIds = StrUtils.splitToLongSet(param);
        // 调用岗位服务校验这些岗位是否有效
        postApi.validPostList(postIds);
    }

    /**
     * 根据岗位 ID 列表，计算出所有关联的用户 ID 集合。
     * <p>
     * 此方法用于在流程任务分配时，动态获取候选人列表。
     * 流程引擎会将返回的用户 ID 作为该任务的潜在处理人（candidate users）。
     *
     * @param param 岗位 ID 字符串（格式同 validateParam）
     * @return 用户 ID 的集合（Set<Long>），即所有属于指定岗位的用户
     */
    @Override
    public Set<Long> calculateUsers(String param) {
        // 解析岗位 ID 字符串为 Set<Long>
        Set<Long> postIds = StrUtils.splitToLongSet(param);
        // 调用用户服务，根据岗位 ID 列表获取关联的用户列表
        // getCheckedData() 表示直接获取有效数据（假设返回的是 CommonResult<List<AdminUserRespDTO>>）
        List<AdminUserRespDTO> users = adminUserApi.getUserListByPostIds(postIds).getCheckedData();
        // 将用户列表转换为仅包含用户 ID 的 Set
        return convertSet(users, AdminUserRespDTO::getId);
    }

}