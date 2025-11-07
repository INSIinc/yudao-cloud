package cn.iocoder.yudao.framework.datapermission.core.util;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder;
import lombok.SneakyThrows;

import java.util.concurrent.Callable;

/**
 * 数据权限工具类
 * <p>
 * 作用说明：
 * 在某些业务场景下，我们需要临时忽略数据权限的检查。
 * 例如：管理员需要查看所有部门的数据，而不受当前用户所属部门的限制。
 * 这个工具类提供了方便的方法来实现这个功能。
 *
 * @author 芋道源码
 */
public class DataPermissionUtils {

    /**
     * 静态缓存的"禁用数据权限"注解对象
     * 作用：避免每次都通过反射获取注解，提高性能
     */
    private static DataPermission DATA_PERMISSION_DISABLE;

    /**
     * 获取"禁用数据权限"的注解对象
     * <p>
     * 实现原理：
     * 1. 在这个方法上标注 @DataPermission(enable = false)，表示禁用数据权限
     * 2. 通过反射获取这个方法上的注解对象
     * 3. 将这个注解对象缓存起来，后续可以重复使用
     *
     * @return 禁用数据权限的注解对象
     */
    @DataPermission(enable = false)
    @SneakyThrows // Lombok 注解：自动处理受检异常，无需显式 try-catch
    private static DataPermission getDisableDataPermissionDisable() {
        // 如果还没有缓存，则通过反射获取
        if (DATA_PERMISSION_DISABLE == null) {
            DATA_PERMISSION_DISABLE = DataPermissionUtils.class
                    .getDeclaredMethod("getDisableDataPermissionDisable") // 获取当前方法
                    .getAnnotation(DataPermission.class); // 获取方法上的 @DataPermission 注解
        }
        return DATA_PERMISSION_DISABLE;
    }

    /**
     * 忽略数据权限，执行对应的业务逻辑（无返回值）
     * <p>
     * 使用场景示例：
     * DataPermissionUtils.executeIgnore(() -> {
     * // 这里的代码执行时会忽略数据权限检查
     * userMapper.selectList(null); // 可以查询到所有用户，不受数据权限限制
     * });
     *
     * @param runnable 要执行的业务逻辑（Runnable 接口，无返回值）
     */
    public static void executeIgnore(Runnable runnable) {
        // 第一步：添加"禁用数据权限"的标记到当前线程上下文
        addDisableDataPermission();
        try {
            // 第二步：执行业务逻辑
            runnable.run();
        } finally {
            // 第三步：无论成功或失败，都要移除标记，避免影响后续代码
            removeDataPermission();
        }
    }

    /**
     * 忽略数据权限，执行对应的业务逻辑（有返回值）
     * <p>
     * 使用场景示例：
     * List<User> allUsers = DataPermissionUtils.executeIgnore(() -> {
     * // 这里的代码执行时会忽略数据权限检查
     * return userMapper.selectList(null); // 返回所有用户列表
     * });
     *
     * @param callable 要执行的业务逻辑（Callable 接口，有返回值）
     * @param <T>      返回值的类型
     * @return 业务逻辑的执行结果
     */
    @SneakyThrows // 自动处理 Callable.call() 可能抛出的异常
    public static <T> T executeIgnore(Callable<T> callable) {
        // 第一步：添加"禁用数据权限"的标记到当前线程上下文
        addDisableDataPermission();
        try {
            // 第二步：执行业务逻辑并返回结果
            return callable.call();
        } finally {
            // 第三步：无论成功或失败，都要移除标记，避免影响后续代码
            removeDataPermission();
        }
    }

    /**
     * 手动添加"忽略数据权限"的标记
     * <p>
     * 注意：使用此方法后，必须调用 removeDataPermission() 来移除标记
     * 建议使用 executeIgnore() 方法，它会自动管理标记的添加和移除
     */
    public static void addDisableDataPermission() {
        // 获取"禁用数据权限"的注解对象
        DataPermission dataPermission = getDisableDataPermissionDisable();
        // 将注解对象添加到当前线程的上下文中
        // 这样，数据权限拦截器在检查时，会发现这个标记，从而跳过权限检查
        DataPermissionContextHolder.add(dataPermission);
    }

    /**
     * 手动移除"忽略数据权限"的标记
     * <p>
     * 作用：清理当前线程上下文中的数据权限标记
     * 重要性：必须及时移除，否则会影响后续的数据权限检查
     */
    public static void removeDataPermission() {
        // 从当前线程的上下文中移除数据权限标记
        DataPermissionContextHolder.remove();
    }

}
