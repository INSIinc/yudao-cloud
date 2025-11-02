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
 * 数据权限的自动配置类
 * <p>
 * 该类通过 Spring Boot 的自动配置机制，自动注册数据权限相关的核心组件，
 * 实现基于注解和规则的数据权限控制能力。
 * </p>
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>管理所有 {@link DataPermissionRule} 规则实例</li>
 *   <li>构建数据权限规则工厂 {@link DataPermissionRuleFactory}</li>
 *   <li>创建并注册 MyBatis Plus 的 {@link DataPermissionInterceptor} 拦截器</li>
 *   <li>注册 AOP 切面 {@link DataPermissionAnnotationAdvisor}，用于识别 {@code @DataPermission} 注解</li>
 * </ul>
 *
 * @author 芋道源码
 */
@AutoConfiguration // Spring Boot 2.4+ 推荐使用 @AutoConfiguration 替代 @Configuration + @ConditionalOn...
public class YudaoDataPermissionAutoConfiguration {

    /**
     * 创建数据权限规则工厂 Bean。
     * <p>
     * 该工厂负责根据当前用户上下文（如租户、部门、角色等）动态生成 SQL 的 WHERE 条件，
     * 从而实现行级数据权限控制。
     * </p>
     *
     * @param rules Spring 容器中所有实现了 {@link DataPermissionRule} 接口的 Bean 列表。
     *              开发者可通过实现该接口自定义数据权限规则（例如：按部门、按角色、按租户等）。
     * @return {@link DataPermissionRuleFactoryImpl} 实例，内部持有所有规则列表。
     */
    @Bean
    public DataPermissionRuleFactory dataPermissionRuleFactory(List<DataPermissionRule> rules) {
        return new DataPermissionRuleFactoryImpl(rules);
    }

    /**
     * 创建数据权限规则处理器，并将其注册为 MyBatis Plus 的拦截器。
     * <p>
     * 该方法做了两件事：
     * 1. 创建 {@link DataPermissionRuleHandler}，它持有规则工厂，用于在 SQL 执行前动态拼接权限条件。
     * 2. 创建 MyBatis Plus 官方提供的 {@link DataPermissionInterceptor}，并将 handler 传入。
     * 3. 将该拦截器插入到 {@link MybatisPlusInterceptor} 的最前面（索引 0）。
     * </p>
     *
     * <p><strong>为什么必须插入到第一个？</strong></p>
     * <ul>
     *   <li>MyBatis Plus 的分页插件（PaginationInnerInterceptor）会先对原始 SQL 进行包装（如 SELECT -> SELECT COUNT(*)）。</li>
     *   <li>如果数据权限拦截器在分页之后，会导致 COUNT 查询未加权限条件，从而分页总数错误。</li>
     *   <li>因此，必须确保数据权限拦截器在分页插件之前执行，以保证所有 SQL（包括 COUNT）都带上权限条件。</li>
     * </ul>
     *
     * @param interceptor   MyBatis Plus 的主拦截器链（通常由 MyBatis Plus 自动配置提供）。
     * @param ruleFactory   数据权限规则工厂，用于获取当前生效的权限规则。
     * @return {@link DataPermissionRuleHandler} 实例，也可被其他组件引用（如 AOP 切面）。
     */
    @Bean
    public DataPermissionRuleHandler dataPermissionRuleHandler(MybatisPlusInterceptor interceptor,
                                                               DataPermissionRuleFactory ruleFactory) {
        // 1. 创建数据权限规则处理器，传入规则工厂
        DataPermissionRuleHandler handler = new DataPermissionRuleHandler(ruleFactory);

        // 2. 创建 MyBatis Plus 官方的数据权限拦截器，并传入自定义的 handler
        DataPermissionInterceptor inner = new DataPermissionInterceptor(handler);

        // 3. 使用工具类将拦截器添加到 MyBatis Plus 拦截器链的最前面（index = 0）
        //    确保在分页插件等其他拦截器之前执行
        MyBatisUtils.addInterceptor(interceptor, inner, 0);

        return handler;
    }

    /**
     * 创建数据权限注解的 AOP 切面。
     * <p>
     * 该 Advisor 会扫描方法上是否标注了 {@code @DataPermission} 注解（或其他自定义注解），
     * 若有，则在方法执行前设置数据权限上下文（如：允许哪些部门、哪些角色的数据可见），
     * 供 {@link DataPermissionRuleHandler} 在生成 SQL 条件时使用。
     * </p>
     *
     * <p>典型使用场景：</p>
     * <pre>
     * &#64;DataPermission(dept = true, user = true)
     * public List<User> getUserList() {
     *     return userMapper.selectList(null);
     * }
     * </pre>
     *
     * @return {@link DataPermissionAnnotationAdvisor} 实例，Spring AOP 会自动应用该切面。
     */
    @Bean
    public DataPermissionAnnotationAdvisor dataPermissionAnnotationAdvisor() {
        return new DataPermissionAnnotationAdvisor();
    }

}