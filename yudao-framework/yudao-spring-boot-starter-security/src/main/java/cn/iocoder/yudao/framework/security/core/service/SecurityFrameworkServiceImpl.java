package cn.iocoder.yudao.framework.security.core.service;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.core.KeyValue;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static cn.iocoder.yudao.framework.common.util.cache.CacheUtils.buildCache;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.skipPermissionCheck;

/**
 * 默认的 {@link SecurityFrameworkService} 实现类
 *
 * 功能说明：
 * 这个类负责处理系统的权限校验逻辑，主要包括：
 * 1. 检查用户是否拥有指定的权限（permission）
 * 2. 检查用户是否拥有指定的角色（role）
 * 3. 检查用户是否拥有指定的权限范围（scope）
 * 4. 使用缓存机制提高权限校验的性能
 *
 * @author 芋道源码
 */
@AllArgsConstructor // Lombok注解：自动生成包含所有字段的构造方法
public class SecurityFrameworkServiceImpl implements SecurityFrameworkService {

    /**
     * 权限API接口
     * 用于调用权限服务，查询用户的角色和权限信息
     */
    private final PermissionCommonApi permissionApi;

    /**
     * 角色校验的缓存
     *
     * 为什么需要缓存？
     * - 权限校验是高频操作，每次请求可能需要多次校验
     * - 直接查询数据库会造成性能瓶颈
     * - 使用缓存可以大幅提升响应速度
     *
     * 缓存结构说明：
     * - Key: KeyValue对象，包含用户ID和角色列表
     * - Value: Boolean值，表示用户是否拥有这些角色中的任意一个
     * - 过期时间: 1分钟（避免权限变更后长时间不生效）
     */
    private final LoadingCache<KeyValue<Long, List<String>>, Boolean> hasAnyRolesCache = buildCache(
            Duration.ofMinutes(1L), // 过期时间 1 分钟
            new CacheLoader<KeyValue<Long, List<String>>, Boolean>() {

                @Override
                public Boolean load(KeyValue<Long, List<String>> key) {
                    // 当缓存中没有数据时，会自动调用这个方法加载数据
                    // key.getKey() 获取用户ID
                    // key.getValue() 获取需要校验的角色列表
                    return permissionApi.hasAnyRoles(key.getKey(), key.getValue().toArray(new String[0])).getCheckedData();
                }

            });

    /**
     * 权限校验的缓存
     *
     * 与角色缓存类似，用于缓存权限校验结果
     *
     * 权限 vs 角色的区别：
     * - 角色（Role）：如"管理员"、"普通用户"等，是一组权限的集合
     * - 权限（Permission）：如"用户:查询"、"订单:新增"等，是具体的操作权限
     *
     * 缓存结构说明：
     * - Key: KeyValue对象，包含用户ID和权限列表
     * - Value: Boolean值，表示用户是否拥有这些权限中的任意一个
     * - 过期时间: 1分钟
     */
    private final LoadingCache<KeyValue<Long, List<String>>, Boolean> hasAnyPermissionsCache = buildCache(
            Duration.ofMinutes(1L), // 过期时间 1 分钟
            new CacheLoader<KeyValue<Long, List<String>>, Boolean>() {

                @Override
                public Boolean load(KeyValue<Long, List<String>> key) {
                    // 当缓存中没有数据时，调用权限API查询用户是否拥有指定权限
                    return permissionApi.hasAnyPermissions(key.getKey(), key.getValue().toArray(new String[0])).getCheckedData();
                }

            });

    /**
     * 检查当前用户是否拥有指定权限
     *
     * @param permission 权限标识，如 "system:user:query"（系统:用户:查询）
     * @return true-有权限，false-无权限
     */
    @Override
    public boolean hasPermission(String permission) {
        // 直接调用 hasAnyPermissions 方法，因为单个权限也是"任意权限"的特殊情况
        return hasAnyPermissions(permission);
    }

    /**
     * 检查当前用户是否拥有任意一个指定权限
     *
     * 使用场景举例：
     * - 删除按钮需要"删除"或"完全控制"权限之一即可显示
     * - hasAnyPermissions("system:user:delete", "system:user:admin")
     *
     * @param permissions 权限标识数组，如 ["system:user:create", "system:user:update"]
     * @return true-拥有任意一个权限，false-一个都没有
     */
    @Override
    @SneakyThrows // Lombok注解：自动处理受检异常，将其转换为运行时异常
    public boolean hasAnyPermissions(String... permissions) {
        // 第一步：检查是否需要跳过权限校验
        // 某些特殊场景（如系统内部调用、跨租户访问）可以跳过权限检查
        if (skipPermissionCheck()) {
            return true;
        }

        // 第二步：获取当前登录用户的ID
        Long userId = getLoginUserId();
        if (userId == null) {
            // 如果用户未登录（userId为null），直接返回false
            return false;
        }

        // 第三步：从缓存中获取权限校验结果
        // 如果缓存中没有，会自动调用 CacheLoader 的 load 方法加载数据
        return hasAnyPermissionsCache.get(new KeyValue<>(userId, Arrays.asList(permissions)));
    }

    /**
     * 检查当前用户是否拥有指定角色
     *
     * @param role 角色标识，如 "admin"（管理员）
     * @return true-有该角色，false-无该角色
     */
    @Override
    public boolean hasRole(String role) {
        // 直接调用 hasAnyRoles 方法
        return hasAnyRoles(role);
    }

    /**
     * 检查当前用户是否拥有任意一个指定角色
     *
     * 使用场景举例：
     * - 某个功能需要"管理员"或"超级管理员"角色才能访问
     * - hasAnyRoles("admin", "super_admin")
     *
     * @param roles 角色标识数组，如 ["admin", "manager"]
     * @return true-拥有任意一个角色，false-一个都没有
     */
    @Override
    @SneakyThrows // 自动处理缓存可能抛出的异常
    public boolean hasAnyRoles(String... roles) {
        // 第一步：检查是否需要跳过权限校验
        if (skipPermissionCheck()) {
            return true;
        }

        // 第二步：获取当前登录用户的ID
        Long userId = getLoginUserId();
        if (userId == null) {
            // 用户未登录，返回false
            return false;
        }

        // 第三步：从缓存中获取角色校验结果
        return hasAnyRolesCache.get(new KeyValue<>(userId, Arrays.asList(roles)));
    }

    /**
     * 检查当前用户是否拥有指定的权限范围（scope）
     *
     * @param scope 权限范围标识
     * @return true-有该权限范围，false-无该权限范围
     */
    @Override
    public boolean hasScope(String scope) {
        return hasAnyScopes(scope);
    }

    /**
     * 检查当前用户是否拥有任意一个指定的权限范围（scope）
     *
     * Scope（权限范围）说明：
     * - Scope 通常用于 OAuth2.0 授权场景
     * - 例如：read（只读）、write（读写）、admin（管理）等
     * - 与角色/权限的区别：scope 更关注资源访问范围，而不是具体操作
     *
     * 使用场景举例：
     * - 第三方应用通过 OAuth2 授权访问用户数据
     * - 需要检查该应用是否拥有 "user.read" 或 "user.write" 范围
     *
     * @param scope 权限范围数组
     * @return true-拥有任意一个权限范围，false-一个都没有
     */
    @Override
    public boolean hasAnyScopes(String... scope) {
        // 第一步：检查是否需要跳过权限校验
        if (skipPermissionCheck()) {
            return true;
        }

        // 第二步：获取当前登录用户的完整信息
        // 注意：这里获取的是完整的 LoginUser 对象，而不仅仅是用户ID
        // 因为 scope 信息直接存储在 LoginUser 对象中
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null) {
            // 用户未登录，返回false
            return false;
        }

        // 第三步：检查用户的 scope 列表中是否包含任意一个指定的 scope
        // CollUtil.containsAny：工具方法，判断集合A是否包含集合B中的任意元素
        return CollUtil.containsAny(user.getScopes(), Arrays.asList(scope));
    }

}
