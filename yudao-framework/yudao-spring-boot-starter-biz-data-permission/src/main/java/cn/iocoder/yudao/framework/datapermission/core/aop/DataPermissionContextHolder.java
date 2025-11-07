package cn.iocoder.yudao.framework.datapermission.core.aop;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.LinkedList;
import java.util.List;

/**
 * {@link DataPermission} 注解的 Context 上下文
 *
 * 【类作用说明】
 * 这个类用来管理数据权限注解的上下文信息。
 * 简单来说，就是存储和管理当前线程中正在使用的数据权限配置。
 *
 * 【为什么需要这个类】
 * 当我们在代码中使用 @DataPermission 注解时，需要一个地方来临时存储这些注解的信息，
 * 这样在执行数据库查询时，就可以根据这些信息来过滤数据，实现数据权限控制。
 *
 * 【设计思路】
 * 1. 使用 ThreadLocal 保证线程安全：每个线程都有自己独立的数据权限上下文
 * 2. 使用 LinkedList 实现栈结构：支持方法的嵌套调用（方法A调用方法B，每个方法都有自己的 @DataPermission）
 * 3. 使用 TransmittableThreadLocal：支持在线程池、异步调用等场景下传递上下文
 *
 * @author 芋道源码
 */
public class DataPermissionContextHolder {

    /**
     * 存储 DataPermission 注解的 ThreadLocal 变量
     *
     * 【为什么使用 LinkedList】
     * 因为可能存在方法的嵌套调用。例如：
     * methodA() 有 @DataPermission(enable=true)
     *   -> methodB() 有 @DataPermission(enable=false)
     *      -> methodC() 有 @DataPermission(enable=true)
     * 这种情况下，需要用栈（LinkedList）来保存每一层的配置
     *
     * 【为什么使用 TransmittableThreadLocal】
     * 普通 ThreadLocal 在线程池、异步调用时会丢失上下文
     * TransmittableThreadLocal 可以在父子线程、线程池之间传递数据
     */
    private static final ThreadLocal<LinkedList<DataPermission>> DATA_PERMISSIONS =
            TransmittableThreadLocal.withInitial(LinkedList::new);

    /**
     * 获得当前的 DataPermission 注解
     *
     * 【方法作用】
     * 从栈顶获取最近添加的 DataPermission 注解，但不移除它
     *
     * 【使用场景】
     * 在执行数据库查询时，需要知道当前是否启用了数据权限过滤
     * 通过这个方法可以获取最内层方法的 @DataPermission 配置
     *
     * 【实现原理】
     * peekLast() 查看栈顶元素但不移除
     * 如果栈为空，返回 null
     *
     * 【举例说明】
     * methodA() {@code @DataPermission(enable=true)}
     *   -> methodB() {@code @DataPermission(enable=false)}  // 此时 get() 返回 enable=false
     *
     * @return DataPermission 注解，如果栈为空则返回 null
     */
    public static DataPermission get() {
        return DATA_PERMISSIONS.get().peekLast();
    }

    /**
     * 入栈 DataPermission 注解
     *
     * 【方法作用】
     * 将一个新的 DataPermission 注解添加到栈顶
     *
     * 【调用时机】
     * 当方法执行前，AOP 拦截器会调用这个方法，把方法上的 @DataPermission 注解信息压入栈中
     *
     * 【实现原理】
     * addLast() 将元素添加到链表末尾（栈顶）
     *
     * 【执行流程】
     * 1. 进入 methodA() -> add(methodA的注解)
     * 2. 进入 methodB() -> add(methodB的注解)  // 此时栈中有2个元素
     * 3. methodB() 执行完 -> remove()         // 栈中剩1个元素
     * 4. methodA() 执行完 -> remove()         // 栈为空
     *
     * @param dataPermission DataPermission 注解对象
     */
    public static void add(DataPermission dataPermission) {
        DATA_PERMISSIONS.get().addLast(dataPermission);
    }

    /**
     * 出栈 DataPermission 注解
     *
     * 【方法作用】
     * 从栈顶移除并返回最近添加的 DataPermission 注解
     *
     * 【调用时机】
     * 当方法执行完毕后，AOP 拦截器会调用这个方法，把之前压入的注解弹出
     * 必须在 finally 块中调用，确保无论方法是否异常都能正确清理
     *
     * 【为什么要清空 ThreadLocal】
     * 当栈中没有元素时，调用 ThreadLocal.remove() 可以防止内存泄漏
     * 特别是在使用线程池的场景下，线程会被重复使用，如果不清空可能导致脏数据
     *
     * 【实现原理】
     * 1. removeLast() 移除并返回栈顶元素
     * 2. 如果栈为空，调用 remove() 清空 ThreadLocal，避免内存泄漏
     *
     * @return 被移除的 DataPermission 注解对象
     */
    public static DataPermission remove() {
        DataPermission dataPermission = DATA_PERMISSIONS.get().removeLast();
        // 无元素时，清空 ThreadLocal
        if (DATA_PERMISSIONS.get().isEmpty()) {
            DATA_PERMISSIONS.remove();
        }
        return dataPermission;
    }

    /**
     * 获得所有 DataPermission 注解
     *
     * 【方法作用】
     * 返回当前栈中所有的 DataPermission 注解列表
     *
     * 【使用场景】
     * 某些复杂场景下需要查看整个调用链路的数据权限配置
     * 例如：需要同时考虑多层方法的权限配置时
     *
     * 【返回值说明】
     * 返回的 LinkedList 从头到尾分别是：最早添加的注解 -> 最近添加的注解
     * 栈顶元素在列表的末尾
     *
     * @return DataPermission 注解列表，如果没有则返回空列表（不会返回 null）
     */
    public static List<DataPermission> getAll() {
        return DATA_PERMISSIONS.get();
    }

    /**
     * 清空上下文
     *
     * 【方法作用】
     * 直接清空当前线程中的所有 DataPermission 注解信息
     *
     * 【使用场景】
     * 目前仅仅用于单元测试，确保测试用例之间互不影响
     *
     * 【注意事项】
     * 生产环境不要调用这个方法！
     * 正常情况下应该通过 add() 和 remove() 成对调用来管理栈
     * 直接 clear() 会破坏栈结构，可能导致其他方法的权限配置丢失
     *
     * 【实现原理】
     * ThreadLocal.remove() 会删除当前线程的 ThreadLocal 变量
     * 下次调用 get() 时会重新初始化一个空的 LinkedList
     */
    public static void clear() {
        DATA_PERMISSIONS.remove();
    }

}
