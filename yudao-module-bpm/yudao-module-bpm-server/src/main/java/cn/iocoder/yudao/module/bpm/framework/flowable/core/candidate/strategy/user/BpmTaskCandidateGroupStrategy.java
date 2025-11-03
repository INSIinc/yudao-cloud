package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.user;

import cn.iocoder.yudao.framework.common.util.string.StrUtils;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmUserGroupDO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.service.definition.BpmUserGroupService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSetByFlatMap;

/**
 * 用户组类型的流程任务候选人策略实现类。
 * <p>
 * 该策略用于在 Flowable 流程引擎的任务分配中，根据指定的“用户组 ID 列表”来确定任务的候选人（即哪些用户有资格领取或处理该任务）。
 * 任务定义中若配置了此策略，并传入一组用户组 ID（如 "1,2,3"），则系统会查询这些用户组中包含的所有用户 ID，并将其作为任务的候选人集合。
 * </p>
 *
 * @author kyle
 */
@Component
public class BpmTaskCandidateGroupStrategy implements BpmTaskCandidateStrategy {

    /**
     * 注入用户组服务，用于查询和校验用户组信息。
     */
    @Resource
    private BpmUserGroupService userGroupService;

    /**
     * 返回本策略对应的枚举值，用于标识该策略类型为“用户组”策略。
     *
     * @return {@link BpmTaskCandidateStrategyEnum#USER_GROUP}
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.USER_GROUP;
    }

    /**
     * 校验传入的策略参数（即用户组 ID 字符串）是否合法。
     * <p>
     * 参数格式应为以逗号分隔的长整型 ID 字符串（如 "1,2,3"）。
     * 本方法会将字符串解析为 Set<Long>，然后调用用户组服务校验这些用户组是否存在。
     * 若任一用户组 ID 无效（如不存在、已删除等），则会抛出异常。
     * </p>
     *
     * @param param 用户组 ID 字符串，格式如 "1,2,3"
     */
    @Override
    public void validateParam(String param) {
        // 将字符串参数（如 "1,2,3"）解析为 Long 类型的 Set
        Set<Long> groupIds = StrUtils.splitToLongSet(param);
        // 调用服务校验这些用户组是否有效
        userGroupService.validUserGroups(groupIds);
    }

    /**
     * 根据传入的用户组 ID 字符串，计算出所有属于这些用户组的用户 ID 集合。
     * <p>
     * 步骤：
     * 1. 解析参数字符串为用户组 ID 集合；
     * 2. 查询这些用户组的完整信息（包含每个组内的用户 ID 列表）；
     * 3. 将所有用户组中的用户 ID 合并为一个去重的 Set。
     * </p>
     * <p>
     * 该方法返回的结果将作为 Flowable 任务的候选人（candidate users）。
     * </p>
     *
     * @param param 用户组 ID 字符串，格式如 "1,2,3"
     * @return 所有相关用户 ID 的去重集合（Set），用于任务候选人分配
     */
    @Override
    public Set<Long> calculateUsers(String param) {
        // 1. 解析参数为用户组 ID 集合
        Set<Long> groupIds = StrUtils.splitToLongSet(param);
        // 2. 根据 ID 查询对应的用户组实体列表
        List<BpmUserGroupDO> groups = userGroupService.getUserGroupList(groupIds);
        // 3. 将每个用户组中的 userIds 列表展开（flatMap），并合并为一个 Set<Long>
        //    使用工具方法 convertSetByFlatMap 实现：对每个 BpmUserGroupDO，
        //    获取其 getUserIds()（List<Long>），再将其流式展开并收集为 Set
        return convertSetByFlatMap(groups, BpmUserGroupDO::getUserIds, Collection::stream);
    }

}