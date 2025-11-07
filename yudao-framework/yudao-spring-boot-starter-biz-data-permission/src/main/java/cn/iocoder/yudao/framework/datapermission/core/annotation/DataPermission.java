package cn.iocoder.yudao.framework.datapermission.core.annotation;

import cn.iocoder.yudao.framework.datapermission.core.rule.DataPermissionRule;

import java.lang.annotation.*;

/**
 * 数据权限注解
 * <p>
 * 【作用说明】
 * 这个注解用于控制数据访问权限，确保用户只能看到和操作自己权限范围内的数据。
 * 例如：普通员工只能看到自己部门的数据，部门经理可以看到整个部门的数据。
 * <p>
 * 【使用位置】
 * 可以声明在类上（对整个类的所有方法生效）或方法上（只对该方法生效）
 * <p>
 * 【使用场景示例】
 * 1. 在 Mapper 接口上使用：控制查询时自动过滤数据
 * 2. 在 Service 方法上使用：对特定业务方法进行权限控制
 * 3. 禁用权限：某些方法需要查询全部数据时，可以设置 enable = false
 * <p>
 * 【工作原理】
 * 系统会在执行 SQL 查询时，根据配置的规则自动添加 WHERE 条件，实现数据过滤
 *
 * @author 芋道源码
 */
@Target({ElementType.TYPE, ElementType.METHOD})  // 表示这个注解可以用在类上或方法上
@Retention(RetentionPolicy.RUNTIME)              // 表示这个注解在运行时保留，可以通过反射读取
@Documented                                       // 表示这个注解会被包含在 JavaDoc 文档中
public @interface DataPermission {

    /**
     * 是否开启数据权限检查
     * <p>
     * 【默认行为】
     * - 不加 @DataPermission 注解：默认开启数据权限
     * - 加了 @DataPermission 注解但不设置 enable：默认开启数据权限
     * <p>
     * 【何时设置为 false】
     * 当某个方法需要查询所有数据，不受权限限制时使用，例如：
     * - 管理员查看全部用户列表
     * - 统计报表需要全局数据
     * - 导出所有数据
     * <p>
     * 【使用示例】
     *
     * @return true-开启数据权限（默认），false-关闭数据权限
     * @DataPermission(enable = false)  // 关闭数据权限，可以查询全部数据
     * List<User> selectAllUsers();
     */
    boolean enable() default true;

    /**
     * 指定要生效的数据权限规则
     * <p>
     * 【作用】
     * 明确指定当前方法/类要使用哪些数据权限规则，只有指定的规则会生效
     * <p>
     * 【优先级】
     * 最高优先级，如果设置了 includeRules，则只有这里指定的规则会生效，
     * 其他规则（包括 excludeRules）都会被忽略
     * <p>
     * 【使用场景】
     * 当系统有多个数据权限规则，但某个方法只需要应用特定规则时使用
     * 例如：只需要部门权限，不需要角色权限
     * <p>
     * 【使用示例】
     *
     * @return 要生效的数据权限规则类数组，默认为空（使用系统所有已配置的规则）
     * @DataPermission(includeRules = {DeptDataPermissionRule.class})  // 只使用部门权限规则
     * List<User> selectUsersByDept();
     */
    Class<? extends DataPermissionRule>[] includeRules() default {};

    /**
     * 指定要排除的数据权限规则
     * <p>
     * 【作用】
     * 排除不需要的数据权限规则，系统会应用所有规则，但排除这里指定的规则
     * <p>
     * 【优先级】
     * 最低优先级，如果同时设置了 includeRules，则 excludeRules 不会生效
     * <p>
     * 【使用场景】
     * 当某个方法不需要某些特定权限规则时使用
     * 例如：查询公共数据时，不需要部门权限限制
     * <p>
     * 【使用示例】
     *
     * @return 要排除的数据权限规则类数组，默认为空（不排除任何规则）
     * @DataPermission(excludeRules = {DeptDataPermissionRule.class})  // 排除部门权限规则
     * List<Dict> selectPublicDict();  // 查询公共字典数据，不需要部门过滤
     */
    Class<? extends DataPermissionRule>[] excludeRules() default {};

}
