package cn.iocoder.yudao.framework.datapermission.core.aop;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import lombok.Getter;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.MethodClassKey;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link DataPermission} 注解的拦截器
 *
 * 作用说明：
 * 1. 在执行方法前，将 @DataPermission 注解入栈
 * 2. 在执行方法后，将 @DataPermission 注解出栈
 *
 * 使用场景：
 * 当业务方法上标注了 @DataPermission 注解时，此拦截器会自动拦截该方法的调用，
 * 并在方法执行前后管理数据权限的上下文信息。这样可以实现细粒度的数据权限控制。
 *
 * @author 芋道源码
 */
@DataPermission // 该注解用于获取 {@link DATA_PERMISSION_NULL} 的空对象，这是一个巧妙的设计技巧
public class DataPermissionAnnotationInterceptor implements MethodInterceptor {

    /**
     * DataPermission 空对象，用于占位
     *
     * 设计原因：
     * - 当方法或类上没有 @DataPermission 注解时，为了避免重复查找，我们使用这个空对象进行占位
     * - 通过将空对象缓存起来，下次遇到同样的方法时，可以直接判断是"没有注解"而不需要再次扫描
     * - 这是一种常见的缓存优化技巧，避免了缓存穿透问题（即：无法区分"缓存中没有"和"真的没有注解"）
     */
    static final DataPermission DATA_PERMISSION_NULL = DataPermissionAnnotationInterceptor.class.getAnnotation(DataPermission.class);

    /**
     * 数据权限注解的缓存
     *
     * key：方法和类的组合键（MethodClassKey）
     * value：该方法对应的 @DataPermission 注解对象，如果没有注解则存储 DATA_PERMISSION_NULL
     *
     * 为什么要缓存：
     * - 注解的查找需要通过反射进行，性能开销较大
     * - 同一个方法的注解是固定的，不会变化，适合缓存
     * - 使用 ConcurrentHashMap 保证线程安全
     */
    @Getter
    private final Map<MethodClassKey, DataPermission> dataPermissionCache = new ConcurrentHashMap<>();

    /**
     * 拦截器的核心方法，在目标方法执行前后进行拦截处理
     *
     * 执行流程：
     * 1. 查找当前方法是否有 @DataPermission 注解
     * 2. 如果有注解，将注解信息放入上下文（入栈）
     * 3. 执行目标方法
     * 4. 无论成功或失败，都要将注解信息从上下文移除（出栈）
     *
     * @param methodInvocation 方法调用信息，包含了被拦截方法的所有信息（方法对象、参数、目标对象等）
     * @return 目标方法的返回值
     * @throws Throwable 目标方法可能抛出的异常
     */
    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        // 步骤1：查找当前方法的 @DataPermission 注解
        DataPermission dataPermission = this.findAnnotation(methodInvocation);

        // 步骤2：如果存在注解，则将其加入到当前线程的上下文中（入栈）
        // 这样在后续的 SQL 拦截器中就可以获取到这个注解，从而应用相应的数据权限规则
        if (dataPermission != null) {
            DataPermissionContextHolder.add(dataPermission);
        }

        try {
            // 步骤3：执行目标方法（被拦截的业务方法）
            return methodInvocation.proceed();
        } finally {
            // 步骤4：方法执行完毕后，将注解从上下文中移除（出栈）
            // 使用 finally 确保无论方法成功还是抛异常，都会清理上下文，避免内存泄漏和上下文污染
            if (dataPermission != null) {
                DataPermissionContextHolder.remove();
            }
        }
    }

    /**
     * 查找方法或类上的 @DataPermission 注解
     *
     * 查找优先级：
     * 1. 先从缓存中查找（提高性能）
     * 2. 如果缓存中没有，则从方法上查找
     * 3. 如果方法上没有，则从类上查找
     * 4. 最后将查找结果放入缓存（包括"没有注解"的情况）
     *
     * @param methodInvocation 方法调用信息
     * @return 找到的 @DataPermission 注解，如果没有则返回 null
     */
    private DataPermission findAnnotation(MethodInvocation methodInvocation) {
        // ============ 第1步：尝试从缓存中获取 ============

        // 获取被调用的方法对象
        Method method = methodInvocation.getMethod();
        // 获取目标对象（实际执行的对象实例）
        Object targetObject = methodInvocation.getThis();
        // 获取目标对象的类型，如果目标对象为空则使用方法声明的类
        Class<?> clazz = targetObject != null ? targetObject.getClass() : method.getDeclaringClass();

        // 构建缓存键：方法 + 类的组合，确保唯一性
        MethodClassKey methodClassKey = new MethodClassKey(method, clazz);

        // 从缓存中获取注解
        DataPermission dataPermission = dataPermissionCache.get(methodClassKey);
        if (dataPermission != null) {
            // 如果缓存中存在，需要判断是真实注解还是空对象占位符
            // 如果是空对象占位符，返回 null；否则返回真实的注解对象
            return dataPermission != DATA_PERMISSION_NULL ? dataPermission : null;
        }

        // ============ 第2步：缓存未命中，通过反射查找注解 ============

        // 2.1 首先从方法上查找 @DataPermission 注解（方法级别的注解优先级更高）
        dataPermission = AnnotationUtils.findAnnotation(method, DataPermission.class);

        // 2.2 如果方法上没有，再从类上查找（类级别的注解作为默认配置）
        if (dataPermission == null) {
            dataPermission = AnnotationUtils.findAnnotation(clazz, DataPermission.class);
        }

        // 2.3 将查找结果添加到缓存中
        // 注意：如果没找到注解（dataPermission == null），则缓存空对象 DATA_PERMISSION_NULL
        // 这样下次查找时就知道"这个方法确实没有注解"，而不需要再次进行反射查找
        dataPermissionCache.put(methodClassKey, dataPermission != null ? dataPermission : DATA_PERMISSION_NULL);

        // 返回查找结果（可能为 null）
        return dataPermission;
    }

}
