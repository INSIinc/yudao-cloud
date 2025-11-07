package cn.iocoder.yudao.framework.datapermission.config;

import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionAnnotationAdvisor;
import cn.iocoder.yudao.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.iocoder.yudao.framework.datapermission.core.rule.DataPermissionRule;
import cn.iocoder.yudao.framework.datapermission.core.rule.DataPermissionRuleFactory;
import cn.iocoder.yudao.framework.datapermission.core.rule.DataPermissionRuleFactoryImpl;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 数据权限自动配置类
 *
 * 【这个类是做什么的？】
 * 这是一个Spring Boot的自动配置类，它会在应用启动时自动运行，负责初始化数据权限功能所需的各个组件。
 *
 * 【什么是数据权限？】
 * 数据权限是指控制用户能看到哪些数据。比如：
 * - 普通员工只能看到自己部门的数据
 * - 部门经理能看到本部门所有人的数据
 * - 总经理能看到所有部门的数据
 *
 * 【工作原理】
 * 当你执行数据库查询（如：SELECT * FROM user）时，系统会自动在SQL后面加上权限过滤条件，
 * 比如变成：SELECT * FROM user WHERE dept_id IN (1,2,3)
 * 这样用户就只能查到有权限的数据，而不需要在每个查询方法里手动添加权限判断。
 *
 * 【这个类配置了哪些组件？】
 * 1. DataPermissionRuleFactory - 规则工厂，管理所有数据权限规则（如部门规则、角色规则）
 * 2. DataPermissionRuleHandler - 规则处理器，负责在SQL执行前添加权限过滤条件
 * 3. DataPermissionAnnotationAdvisor - AOP切面，识别和处理 @DataPermission 注解
 *
 * @author 芋道源码
 */
@AutoConfiguration // Spring Boot 2.4+ 的自动配置注解，让这个类在应用启动时自动加载
public class YudaoDataPermissionAutoConfiguration {

    /**
     * 创建数据权限规则工厂
     *
     * 【这个方法的作用】
     * 创建一个"规则工厂"对象，用来管理和提供各种数据权限规则。
     *
     * 【什么是规则工厂？】
     * 可以把它想象成一个"规则仓库"，里面存放着各种数据权限规则。
     * 比如：
     * - 部门权限规则：只能看本部门的数据
     * - 角色权限规则：只能看特定角色的数据
     * - 自定义规则：根据业务需求定制的规则
     *
     * 【参数说明】
     * @param rules 所有的数据权限规则列表。
     *              这个列表由Spring自动注入，包含了系统中所有实现了DataPermissionRule接口的规则Bean。
     *              比如：[部门权限规则, 角色权限规则, 自定义规则1, 自定义规则2...]
     *
     * 【返回值】
     * @return 返回一个规则工厂对象，后续其他组件会使用这个工厂来获取需要的权限规则
     */
    @Bean
    public DataPermissionRuleFactory dataPermissionRuleFactory(List<DataPermissionRule> rules) {
        return new DataPermissionRuleFactoryImpl(rules);
    }

    /**
     * 创建数据权限处理器，并注册为MyBatis Plus的拦截器
     *
     * 【这个方法的作用】
     * 这是整个数据权限功能的核心！它创建了一个"SQL守门员"，在每条SQL执行之前自动添加权限过滤条件。
     *
     * 【工作流程】
     * 1. 创建规则处理器（handler）：负责根据权限规则生成SQL过滤条件
     * 2. 创建MyBatis Plus拦截器：在SQL执行前拦截，调用handler添加权限条件
     * 3. 把拦截器插入到拦截器链的最前面（第0个位置）
     *
     * 【为什么要插入到第一个位置？】
     * 这一点非常重要！原因如下：
     *
     * 场景：假设你要查询用户列表，并且使用了分页功能
     *
     * 错误顺序（如果数据权限拦截器在后面）：
     * 1. 原始SQL：SELECT * FROM user
     * 2. 分页插件先执行：SELECT COUNT(*) FROM user  （计算总数，此时没有权限过滤！）
     * 3. 数据权限插件后执行：SELECT * FROM user WHERE dept_id IN (1,2,3)
     * 结果：分页总数是错的！因为COUNT查询没有加权限条件
     *
     * 正确顺序（数据权限拦截器在最前面）：
     * 1. 原始SQL：SELECT * FROM user
     * 2. 数据权限插件先执行：SELECT * FROM user WHERE dept_id IN (1,2,3)
     * 3. 分页插件后执行：SELECT COUNT(*) FROM user WHERE dept_id IN (1,2,3)
     * 结果：正确！所有SQL（包括COUNT）都带上了权限条件
     *
     * 【参数说明】
     * @param interceptor MyBatis Plus的拦截器链，由Spring自动注入
     * @param ruleFactory 规则工厂，用于获取权限规则
     *
     * 【返回值】
     * @return 返回处理器对象，供其他组件使用
     */
    @Bean
    public DataPermissionRuleHandler dataPermissionRuleHandler(MybatisPlusInterceptor interceptor,
                                                               DataPermissionRuleFactory ruleFactory) {
        // 步骤1：创建数据权限规则处理器，传入规则工厂
        // 这个handler负责根据规则生成SQL过滤条件，比如 "WHERE dept_id IN (1,2,3)"
        DataPermissionRuleHandler handler = new DataPermissionRuleHandler(ruleFactory);

        // 步骤2：创建MyBatis Plus官方的数据权限拦截器，把我们的handler传进去
        // 这个拦截器会在SQL执行前调用handler的方法，获取并添加权限过滤条件
        DataPermissionInterceptor inner = new DataPermissionInterceptor(handler);

        // 步骤3：把拦截器添加到拦截器链的最前面（索引0的位置）
        // 【重要】必须放在第一个位置，确保在分页、多租户等其他拦截器之前执行
        // 这样可以保证所有SQL（包括分页的COUNT查询）都会带上权限条件
        MyBatisUtils.addInterceptor(interceptor, inner, 0);

        return handler;
    }

    /**
     * 创建数据权限注解的AOP切面
     *
     * 【这个方法的作用】
     * 创建一个AOP切面，用来识别和处理代码中的 @DataPermission 注解。
     *
     * 【什么是AOP切面？】
     * AOP（面向切面编程）就像给方法加"拦截器"。当方法执行时，切面会先执行一些额外操作。
     * 这个切面专门拦截标注了 @DataPermission 注解的方法。
     *
     * 【工作原理】
     * 1. 你在Service方法上加了 @DataPermission 注解
     * 2. 当这个方法被调用时，切面会先执行
     * 3. 切面读取注解的配置（如：是否启用部门过滤、是否启用用户过滤）
     * 4. 把这些配置存到一个"上下文"中
     * 5. 然后执行方法，方法里的SQL会被拦截器处理
     * 6. 拦截器从"上下文"中读取配置，决定要加哪些权限过滤条件
     *
     * 【使用示例】
     * <pre>
     * // 示例1：启用部门和用户数据权限
     * &#64;DataPermission(enable = true)  // 默认启用部门和用户权限
     * public List<User> getUserList() {
     *     // 执行SQL：SELECT * FROM user
     *     // 实际执行：SELECT * FROM user WHERE dept_id IN (1,2,3) AND user_id = 100
     *     return userMapper.selectList(null);
     * }
     *
     * // 示例2：禁用数据权限（查询所有数据，不受权限限制）
     * &#64;DataPermission(enable = false)
     * public List<User> getAllUsers() {
     *     // 执行SQL：SELECT * FROM user （不会添加任何权限过滤）
     *     return userMapper.selectList(null);
     * }
     * </pre>
     *
     * 【返回值】
     * @return 返回AOP切面对象，Spring会自动应用到所有标注了 @DataPermission 的方法上
     */
    @Bean
    public DataPermissionAnnotationAdvisor dataPermissionAnnotationAdvisor() {
        return new DataPermissionAnnotationAdvisor();
    }

}