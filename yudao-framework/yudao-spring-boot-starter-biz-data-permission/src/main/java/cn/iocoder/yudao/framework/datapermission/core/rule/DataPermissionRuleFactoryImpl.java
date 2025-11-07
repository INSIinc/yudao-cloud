package cn.iocoder.yudao.framework.datapermission.core.rule;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 默认的 DataPermissionRuleFactoryImpl 实现类
 * 支持通过 {@link DataPermissionContextHolder} 过滤数据权限
 *
 * 【功能说明】
 * 这个类是数据权限规则工厂的默认实现，主要负责管理和筛选数据权限规则。
 * 数据权限是指：根据用户的角色、部门等条件，限制用户只能查询特定范围的数据。
 * 例如：普通员工只能看到自己部门的数据，部门经理可以看到本部门及下属部门的数据。
 *
 * 【工作原理】
 * 1. 系统启动时，会将所有数据权限规则（如部门权限、用户权限等）注入到这个工厂类中
 * 2. 当执行数据库查询时，会调用这个工厂获取需要应用的权限规则
 * 3. 根据 @DataPermission 注解的配置，决定启用哪些规则或排除哪些规则
 * 4. 这些规则最终会被转换成 SQL 的 WHERE 条件，自动添加到查询语句中
 *
 * @author 芋道源码
 */
@RequiredArgsConstructor // Lombok 注解：自动生成包含 final 字段的构造函数
public class DataPermissionRuleFactoryImpl implements DataPermissionRuleFactory {

    /**
     * 数据权限规则数组
     *
     * 【说明】
     * 这个列表存储了所有可用的数据权限规则，例如：
     * - DeptDataPermissionRule: 部门数据权限规则（限制只能查看特定部门的数据）
     * - UserDataPermissionRule: 用户数据权限规则（限制只能查看特定用户的数据）
     *
     * 这些规则会在 Spring 容器启动时自动注入进来
     */
    private final List<DataPermissionRule> rules;

    /**
     * 获取所有的数据权限规则
     *
     * @return 返回系统中配置的所有数据权限规则列表
     */
    @Override
    public List<DataPermissionRule> getDataPermissionRules() {
        return rules;
    }

    /**
     * 根据 MyBatis 的 mappedStatementId 获取需要应用的数据权限规则
     *
     * 【参数说明】
     * @param mappedStatementId MyBatis 映射语句的 ID，格式通常为 "Mapper接口全限定名.方法名"
     *                          例如：cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper.selectList
     *                          注意：此参数暂时未使用，预留用于未来的优化（基于语句ID进行缓存）
     *
     * @return 返回需要应用的数据权限规则列表
     *
     * 【处理逻辑】
     * 这个方法会根据当前线程上下文中的 @DataPermission 注解配置，决定返回哪些规则：
     * 1. 如果没有配置规则，返回空列表（表示无数据权限限制）
     * 2. 如果没有 @DataPermission 注解，返回所有规则（默认启用所有权限控制）
     * 3. 如果 @DataPermission 设置 enable=false，返回空列表（明确禁用权限控制）
     * 4. 如果 @DataPermission 指定了 includeRules，只返回指定的规则（精确控制）
     * 5. 如果 @DataPermission 指定了 excludeRules，返回除了排除规则外的所有规则（反向过滤）
     * 6. 其他情况，返回所有规则（默认行为）
     */
    @Override
    public List<DataPermissionRule> getDataPermissionRule(String mappedStatementId) {
        // 1. 无数据权限规则的情况
        // 【解释】如果系统中没有配置任何数据权限规则，直接返回空列表
        // 【场景】通常不会出现这种情况，除非开发者故意不配置任何权限规则
        if (CollUtil.isEmpty(rules)) {
            return Collections.emptyList();
        }

        // 2. 未配置 @DataPermission 注解，则默认开启所有规则
        // 【解释】从线程上下文中获取当前方法上的 @DataPermission 注解配置
        // 【场景】如果方法上没有加 @DataPermission 注解，dataPermission 为 null
        // 【结果】返回所有规则，表示默认启用所有数据权限控制
        DataPermission dataPermission = DataPermissionContextHolder.get();
        if (dataPermission == null) {
            return rules; // 默认情况：应用所有数据权限规则
        }

        // 3. 已配置 @DataPermission 注解，但设置了 enable=false（禁用数据权限）
        // 【解释】检查注解的 enable 属性，如果为 false，表示开发者明确要求禁用数据权限
        // 【场景】某些特殊接口需要查询全部数据，不受权限限制，例如：
        //        - 超级管理员的管理界面
        //        - 数据导出功能
        //        - 统计报表功能
        // 【结果】返回空列表，表示不应用任何数据权限规则
        if (!dataPermission.enable()) {
            return Collections.emptyList();
        }

        // 4. 已配置 @DataPermission 注解，并指定了 includeRules（只启用特定规则）
        // 【解释】如果 includeRules 不为空，只返回指定的规则类
        // 【场景】某些接口只需要应用部分权限规则，例如：
        //        @DataPermission(includeRules = DeptDataPermissionRule.class)
        //        表示这个接口只需要部门权限控制，不需要其他权限控制
        // 【实现】使用 Stream API 过滤规则列表，只保留在 includeRules 中指定的规则
        if (ArrayUtil.isNotEmpty(dataPermission.includeRules())) {
            return rules.stream()
                    .filter(rule -> ArrayUtil.contains(dataPermission.includeRules(), rule.getClass()))
                    .collect(Collectors.toList()); // 一般规则不会太多，所以不采用 HashSet 查询
        }

        // 5. 已配置 @DataPermission 注解，并指定了 excludeRules（排除特定规则）
        // 【解释】如果 excludeRules 不为空，返回除了排除规则外的所有其他规则
        // 【场景】某些接口需要排除某个权限规则，例如：
        //        @DataPermission(excludeRules = UserDataPermissionRule.class)
        //        表示这个接口不需要用户权限控制，但需要其他权限控制（如部门权限）
        // 【实现】使用 Stream API 过滤规则列表，排除在 excludeRules 中指定的规则
        if (ArrayUtil.isNotEmpty(dataPermission.excludeRules())) {
            return rules.stream()
                    .filter(rule -> !ArrayUtil.contains(dataPermission.excludeRules(), rule.getClass()))
                    .collect(Collectors.toList()); // 一般规则不会太多，所以不采用 HashSet 查询
        }

        // 6. 已配置 @DataPermission 注解，但没有指定 includeRules 或 excludeRules
        // 【解释】这种情况下，使用默认行为，返回所有规则
        // 【场景】开发者只是为了明确标记这个方法需要数据权限控制，但不需要特殊配置
        // 【结果】返回所有规则
        return rules;
    }

}
