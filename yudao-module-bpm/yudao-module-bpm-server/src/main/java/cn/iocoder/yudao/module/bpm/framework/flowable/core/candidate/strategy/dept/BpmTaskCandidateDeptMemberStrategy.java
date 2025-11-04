package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept;

import cn.iocoder.yudao.framework.common.util.string.StrUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * 部门的成员 {@link BpmTaskCandidateStrategy} 实现类
 *
 * 【功能说明】
 * 这个类用于处理工作流任务的候选人策略，具体是"部门成员"策略
 * 当一个工作流任务需要分配给某个或某些部门的所有成员时，就会使用这个策略
 *
 * 【使用场景举例】
 * 比如：报销审批流程中，需要财务部的所有成员都可以审批，
 * 那么就可以配置使用这个策略，指定财务部的部门ID
 *
 * @author kyle
 */
@Component  // Spring注解：标记这是一个Spring管理的组件，会被自动扫描并注册到Spring容器中
public class BpmTaskCandidateDeptMemberStrategy implements BpmTaskCandidateStrategy {

    // 部门API接口，用于调用部门相关的服务
    // @Resource注解：由Spring自动注入实例，不需要手动创建对象
    @Resource
    private DeptApi deptApi;

    // 用户API接口，用于调用用户相关的服务
    // @Resource注解：由Spring自动注入实例，不需要手动创建对象
    @Resource
    private AdminUserApi adminUserApi;

    /**
     * 获取策略类型
     *
     * 【方法说明】
     * 返回当前策略的类型枚举值：DEPT_MEMBER（部门成员）
     * 这个方法用于标识当前策略是哪一种类型
     *
     * @return 返回部门成员策略的枚举值
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.DEPT_MEMBER;
    }

    /**
     * 验证参数是否有效
     *
     * 【方法说明】
     * 在使用这个策略之前，需要先验证传入的参数是否合法
     * 参数应该是部门ID的字符串，多个部门ID用逗号分隔，例如："1,2,3"
     *
     * 【执行流程】
     * 1. 将字符串参数解析成部门ID的集合（Set<Long>）
     * 2. 调用部门API验证这些部门ID是否存在
     * 3. 如果部门不存在，会抛出异常
     *
     * @param param 部门ID字符串，格式如："1,2,3"
     * @throws 如果部门ID不存在或无效，会抛出业务异常
     */
    @Override
    public void validateParam(String param) {
        // 步骤1：将逗号分隔的字符串转换为Long类型的Set集合
        // 例如："1,2,3" -> Set{1L, 2L, 3L}
        Set<Long> deptIds = StrUtils.splitToLongSet(param);

        // 步骤2：调用部门API验证这些部门ID是否都存在
        // checkError()方法：如果验证失败，会抛出异常，阻止后续流程
        deptApi.validateDeptList(deptIds).checkError();
    }

    /**
     * 计算任务的候选用户
     *
     * 【方法说明】
     * 根据指定的部门ID，查询出这些部门下的所有用户
     * 这些用户就是工作流任务的候选人（可以处理该任务的人）
     *
     * 【执行流程】
     * 1. 将字符串参数解析成部门ID的集合
     * 2. 根据部门ID列表查询所有部门下的用户
     * 3. 提取用户的ID，转换成Set集合返回
     *
     * 【返回值说明】
     * 返回的是用户ID的集合，这些用户都可以看到并处理该工作流任务
     *
     * @param param 部门ID字符串，格式如："1,2,3"
     * @return 返回这些部门下所有用户的ID集合
     */
    @Override
    public Set<Long> calculateUsers(String param) {
        // 步骤1：将逗号分隔的字符串转换为Long类型的Set集合
        // 例如："1,2,3" -> Set{1L, 2L, 3L}
        Set<Long> deptIds = StrUtils.splitToLongSet(param);

        // 步骤2：根据部门ID列表，查询这些部门下的所有用户信息
        // getCheckedData()方法：获取返回结果中的数据，如果调用失败会抛出异常
        List<AdminUserRespDTO> users = adminUserApi.getUserListByDeptIds(deptIds).getCheckedData();

        // 步骤3：从用户对象列表中提取用户ID，转换成Set集合
        // convertSet工具方法：将List<AdminUserRespDTO>转换为Set<Long>
        // AdminUserRespDTO::getId 是方法引用，表示获取每个用户对象的ID
        return convertSet(users, AdminUserRespDTO::getId);
    }

}