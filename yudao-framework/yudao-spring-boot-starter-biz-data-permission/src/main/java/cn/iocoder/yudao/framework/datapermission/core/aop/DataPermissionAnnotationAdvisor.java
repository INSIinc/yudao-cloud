package cn.iocoder.yudao.framework.datapermission.core.aop;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.aopalliance.aop.Advice;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;

import java.io.Serial;

/**
 * {@link cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission} 注解的 Advisor 实现类
 * <p>
 * 什么是 Advisor？
 * - Advisor 是 Spring AOP 中的一个概念，它将"切点(Pointcut)"和"通知(Advice)"结合在一起
 * - 切点(Pointcut)：定义在哪些方法上应用 AOP，即"在哪里切入"
 * - 通知(Advice)：定义要执行的逻辑，即"切入后做什么"
 * <p>
 * 这个类的作用：
 * - 当开发者在类或方法上使用 @DataPermission 注解时，这个 Advisor 会自动识别
 * - 然后在这些方法执行前后，自动添加数据权限的过滤逻辑
 * - 例如：确保用户只能查询自己部门的数据，而不是全公司的数据
 *
 * @author 芋道源码
 */
@Getter // Lombok 注解：自动生成 getter 方法
@EqualsAndHashCode(callSuper = true) // Lombok 注解：自动生成 equals 和 hashCode 方法，并包含父类的字段
public class DataPermissionAnnotationAdvisor extends AbstractPointcutAdvisor {

    @Serial
    private static final long serialVersionUID = -8255354530330069379L;
    /**
     * 通知(Advice)：定义拦截后要执行的具体逻辑
     * 这里使用 DataPermissionAnnotationInterceptor 来处理数据权限的拦截
     */
    private final Advice advice;

    /**
     * 切点(Pointcut)：定义哪些类或方法需要被拦截
     * 这里会拦截所有标注了 @DataPermission 注解的类和方法
     */
    private final Pointcut pointcut;

    /**
     * 构造方法：初始化 Advisor
     * 在 Spring 容器启动时会自动调用这个构造方法
     */
    public DataPermissionAnnotationAdvisor() {
        // 创建数据权限拦截器，用于执行实际的权限过滤逻辑
        this.advice = new DataPermissionAnnotationInterceptor();
        // 构建切点，定义拦截规则
        this.pointcut = this.buildPointcut();
    }

    /**
     * 构建切点(Pointcut)：定义拦截规则
     * <p>
     * 这个方法创建了两个切点并将它们合并：
     * 1. classPointcut：匹配类级别的 @DataPermission 注解
     * - 如果一个类上有 @DataPermission，则该类的所有方法都会被拦截
     * 2. methodPointcut：匹配方法级别的 @DataPermission 注解
     * - 如果一个方法上有 @DataPermission，则该方法会被拦截
     * <p>
     * union(methodPointcut)：表示"或"的关系
     * - 只要类上有注解 OR 方法上有注解，都会被拦截
     *
     * @return 组合后的切点
     */
    protected Pointcut buildPointcut() {
        // 创建类级别的切点：第一个参数是要匹配的注解类型，第二个参数 true 表示检查父类
        Pointcut classPointcut = new AnnotationMatchingPointcut(DataPermission.class, true);
        // 创建方法级别的切点：第一个参数为 null 表示不限制类，第二个参数是要匹配的方法注解，第三个参数 true 表示检查父类方法
        Pointcut methodPointcut = new AnnotationMatchingPointcut(null, DataPermission.class, true);
        // 将两个切点合并，使用 union 表示"或"的关系（满足任一条件即可）
        return new ComposablePointcut(classPointcut).union(methodPointcut);
    }

}
