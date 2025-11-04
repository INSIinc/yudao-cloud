package cn.iocoder.yudao.framework.tenant.core.context;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.DocumentEnum;
import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 多租户上下文持有器（Holder）
 * <p>
 * 作用：在当前线程中临时保存“租户编号”和“是否忽略租户”的信息，
 *      以便在同一个请求处理过程中，其他代码可以随时获取当前属于哪个租户。
 * <p>
 * 为什么叫“上下文”？——可以理解为“当前操作的环境信息”，比如：现在是哪个租户在操作？
 * 为什么用 ThreadLocal？——因为每个用户请求通常由一个独立线程处理，用 ThreadLocal
 *      可以让每个线程拥有自己独立的租户信息，互不干扰。
 * 为什么用 TransmittableThreadLocal（TTL）？
 *      因为在使用线程池（比如异步任务）时，普通 ThreadLocal 无法自动传递上下文，
 *      而 TTL 能在线程间“透传”这些租户信息，保持上下文一致性。
 *
 * @author 芋道源码
 */
public class TenantContextHolder {

    /**
     * 存储当前线程的租户编号（tenantId）
     * <p>
     * 例如：租户 A 的 tenantId = 1001，租户 B 的 tenantId = 1002。
     * 在处理租户 A 的请求时，这里会存 1001。
     */
    private static final ThreadLocal<Long> TENANT_ID = new TransmittableThreadLocal<>();

    /**
     * 标记当前线程是否应“忽略租户隔离”
     * <p>
     * 通常用于系统内部操作（如定时任务、数据初始化等），
     * 这些操作可能需要绕过多租户的限制，访问所有租户的数据。
     * 如果设为 true，则表示“不区分租户，查所有数据”。
     */
    private static final ThreadLocal<Boolean> IGNORE = new TransmittableThreadLocal<>();

    /**
     * 获取当前线程中的租户编号
     * <p>
     * 如果当前没有设置租户编号，会返回 null。
     * 适用于“可以没有租户”的场景（但一般业务中都应该有）。
     *
     * @return 当前租户编号，可能为 null
     */
    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * 获取当前租户编号，但要求必须存在
     * <p>
     * 如果发现没有设置租户编号（即为 null），则立即抛出异常，
     * 并附带文档链接，帮助开发者快速排查问题。
     * <p>
     * 适用于“必须有租户”的业务逻辑（比如用户登录后的操作）。
     *
     * @return 当前租户编号（一定不为 null）
     * @throws NullPointerException 如果未设置租户编号
     */
    public static Long getRequiredTenantId() {
        Long tenantId = getTenantId();
        if (tenantId == null) {
            throw new NullPointerException("TenantContextHolder 不存在租户编号！可参考文档："
                    + DocumentEnum.TENANT.getUrl());
        }
        return tenantId;
    }

    /**
     * 设置当前线程的租户编号
     * <p>
     * 通常在用户登录、或请求进入系统时调用，比如从 Token 中解析出 tenantId 后设置进来。
     *
     * @param tenantId 要设置的租户编号，可以为 null（但一般不建议）
     */
    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * 设置是否忽略租户隔离
     * <p>
     * 设为 true：后续数据库查询等操作将不加租户过滤条件（查所有租户数据）。
     * 设为 false 或 null：正常按租户隔离。
     *
     * @param ignore 是否忽略租户
     */
    public static void setIgnore(Boolean ignore) {
        IGNORE.set(ignore);
    }

    /**
     * 判断当前是否应忽略租户隔离
     * <p>
     * 使用 Boolean.TRUE.equals(...) 是为了安全处理 null 值：
     *   - 如果 IGNORE 中是 null，默认视为 false（不忽略）
     *   - 只有明确设为 true 时，才返回 true
     *
     * @return true 表示忽略租户，false 表示按租户隔离
     */
    public static boolean isIgnore() {
        return Boolean.TRUE.equals(IGNORE.get());
    }

    /**
     * 清空当前线程中存储的租户信息
     * <p>
     * 非常重要！在请求结束时（如通过拦截器或过滤器）必须调用此方法，
     * 否则 ThreadLocal 中的数据可能被线程池复用，导致“租户信息泄露”（A 用户看到 B 用户的数据）。
     */
    public static void clear() {
        TENANT_ID.remove();
        IGNORE.remove();
    }

}