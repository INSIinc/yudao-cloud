package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.user;

import cn.iocoder.yudao.framework.common.util.string.StrUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 角色 {@link BpmTaskCandidateStrategy} 实现类
 *
 * 功能说明：
 * 这个类用于处理工作流任务中基于角色的候选人策略。
 * 当一个工作流任务需要分配给某些角色的用户时，就会使用这个策略。
 *
 * 例如：如果任务需要分配给"财务经理"角色，这个类会找出所有拥有"财务经理"角色的用户。
 *
 * @author kyle
 */
@Component  // Spring注解：将这个类注册为Spring容器中的一个组件，可以被自动注入到其他类中使用
public class BpmTaskCandidateRoleStrategy implements BpmTaskCandidateStrategy {

    // @Resource注解：自动注入Spring容器中的RoleApi实例
    // RoleApi：角色相关的API接口，用于验证角色是否存在
    @Resource
    private RoleApi roleApi;

    // @Resource注解：自动注入Spring容器中的PermissionApi实例
    // PermissionApi：权限相关的API接口，用于查询角色下的用户
    @Resource
    private PermissionApi permissionApi;

    /**
     * 获取当前策略的类型
     *
     * 返回值说明：
     * 返回 BpmTaskCandidateStrategyEnum.ROLE，表示这是一个"角色"类型的候选人策略
     *
     * @return 策略类型枚举值
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.ROLE;
    }

    /**
     * 验证参数是否合法
     *
     * 参数说明：
     * @param param 角色ID字符串，多个角色ID用逗号分隔，例如："1,2,3"
     *
     * 执行流程：
     * 1. 将参数字符串转换为角色ID集合（例如："1,2,3" -> Set[1, 2, 3]）
     * 2. 调用roleApi验证这些角色ID是否都存在于系统中
     * 3. 如果有不存在的角色ID，会抛出异常
     *
     * 使用场景：
     * 在配置工作流任务的审批人时，需要先验证配置的角色是否有效
     */
    @Override
    public void validateParam(String param) {
        // 使用工具类将字符串分割成Long类型的Set集合
        // 例如："1,2,3" 转换为 Set包含[1L, 2L, 3L]
        Set<Long> roleIds = StrUtils.splitToLongSet(param);

        // 调用RoleApi验证所有角色ID是否有效（是否存在于系统中）
        roleApi.validRoleList(roleIds);
    }

    /**
     * 计算符合条件的用户ID集合
     *
     * 参数说明：
     * @param param 角色ID字符串，多个角色ID用逗号分隔，例如："1,2,3"
     *
     * 返回值说明：
     * @return 用户ID集合，包含所有拥有指定角色的用户ID
     *
     * 执行流程：
     * 1. 将参数字符串转换为角色ID集合（例如："1,2,3" -> Set[1, 2, 3]）
     * 2. 通过permissionApi查询拥有这些角色的所有用户ID
     * 3. 返回用户ID集合
     *
     * 使用场景：
     * 当工作流引擎需要确定任务的候选人时，调用此方法获取所有符合角色条件的用户
     *
     * 示例：
     * 如果角色ID为"1"代表"财务经理"，该角色下有用户100、101、102
     * 则返回的Set为 [100L, 101L, 102L]
     */
    @Override
    public Set<Long> calculateUsers(String param) {
        // 步骤1：将字符串参数解析为角色ID集合
        Set<Long> roleIds = StrUtils.splitToLongSet(param);

        // 步骤2：调用权限API根据角色ID集合查询用户ID集合
        return permissionApi.getUserRoleIdListByRoleIds(roleIds).getCheckedData();
    }

}