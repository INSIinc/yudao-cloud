package cn.iocoder.yudao.module.system.service.permission;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleMenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserRoleDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMenuMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserRoleMapper;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.system.enums.permission.DataScopeEnum;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString;

/**
 * 权限 Service 实现类
 * <p>
 * 这个类负责处理系统中所有与权限相关的业务逻辑，主要包括：
 * 1. 用户权限判断（判断用户是否有某个权限）
 * 2. 用户角色判断（判断用户是否有某个角色）
 * 3. 角色-菜单关系管理（给角色分配菜单权限）
 * 4. 用户-角色关系管理（给用户分配角色）
 * 5. 数据权限管理（控制用户可以查看哪些部门的数据）
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class PermissionServiceImpl implements PermissionService {

    // ========== 数据访问层（Mapper）依赖注入 ==========

    @Resource
    private RoleMenuMapper roleMenuMapper; // 角色-菜单关系表的操作类
    @Resource
    private UserRoleMapper userRoleMapper; // 用户-角色关系表的操作类

    // ========== 业务逻辑层（Service）依赖注入 ==========

    @Resource
    private RoleService roleService; // 角色服务，用于查询角色信息
    @Resource
    private MenuService menuService; // 菜单服务，用于查询菜单信息
    @Resource
    private DeptService deptService; // 部门服务，用于查询部门信息
    @Resource
    private AdminUserService userService; // 用户服务，用于查询用户信息

    /**
     * 判断用户是否拥有任意一个权限
     * <p>
     * 使用场景：用于权限校验，判断用户能否执行某个操作
     * 例如：判断用户是否有"用户查询"或"用户新增"的权限
     *
     * @param userId      用户ID
     * @param permissions 权限标识数组（例如：system:user:query、system:user:create）
     * @return true=有权限，false=无权限
     */
    @Override
    public boolean hasAnyPermissions(Long userId, String... permissions) {
        // 步骤1：特殊情况处理 - 如果传入的权限数组为空，默认认为有权限
        // 原因：有些接口不需要权限就能访问，所以传空数组表示不校验权限
        if (ArrayUtil.isEmpty(permissions)) {
            return true;
        }

        // 步骤2：获取用户的所有启用状态的角色
        // 注意：这里会从缓存中读取，提高性能；同时会过滤掉被禁用的角色
        List<RoleDO> roles = getEnableUserRoleListByUserIdFromCache(userId);
        if (CollUtil.isEmpty(roles)) {
            // 如果用户没有任何角色，说明没有权限
            return false;
        }

        // 步骤3：遍历每个权限标识，只要有一个权限满足条件就返回true
        // 这是"或"的关系，不是"且"的关系
        for (String permission : permissions) {
            if (hasAnyPermission(roles, permission)) {
                return true; // 找到一个满足的权限就立即返回
            }
        }

        // 步骤4：如果前面都没有匹配，还要检查用户是否是超级管理员
        // 超级管理员拥有所有权限，所以直接返回true
        return roleService.hasAnySuperAdmin(convertSet(roles, RoleDO::getId));
    }

    /**
     * 判断指定角色，是否拥有该权限（私有方法）
     * <p>
     * 核心逻辑：权限 -> 菜单 -> 角色 的关联关系
     * 解释：在系统中，权限绑定在菜单上，菜单又分配给角色，所以要判断角色是否有权限，需要：
     * 1. 先找到这个权限对应的菜单
     * 2. 再找到这些菜单分配给了哪些角色
     * 3. 最后判断用户的角色是否在这些角色中
     *
     * @param roles      用户的角色列表
     * @param permission 权限标识（例如：system:user:query）
     * @return true=有权限，false=无权限
     */
    private boolean hasAnyPermission(List<RoleDO> roles, String permission) {
        // 步骤1：通过权限标识查找对应的菜单ID列表
        // 说明：一个权限可能对应多个菜单，因为同一个权限可能在多处使用
        List<Long> menuIds = menuService.getMenuIdListByPermissionFromCache(permission);

        // 步骤2：严格模式 - 如果找不到对应的菜单，认为该权限不存在，直接返回无权限
        // 这样可以避免配置错误导致的权限泄露
        if (CollUtil.isEmpty(menuIds)) {
            return false;
        }

        // 步骤3：将用户的角色列表转换为角色ID集合，方便后续比对
        Set<Long> roleIds = convertSet(roles, RoleDO::getId);

        // 步骤4：遍历每个菜单，判断用户的角色是否有权限访问
        for (Long menuId : menuIds) {
            // 4.1 获取拥有这个菜单的所有角色ID集合
            // 注意：这里通过缓存读取，提高性能
            Set<Long> menuRoleIds = getSelf().getMenuRoleIdListByMenuIdFromCache(menuId);

            // 4.2 判断用户的角色ID集合 与 菜单的角色ID集合 是否有交集
            // 只要有一个交集（即用户的某个角色拥有该菜单），就说明有权限
            if (CollUtil.containsAny(menuRoleIds, roleIds)) {
                return true; // 找到匹配就立即返回
            }
        }

        // 步骤5：所有菜单都检查完了，都没有匹配，说明无权限
        return false;
    }

    /**
     * 判断用户是否拥有任意一个角色
     * <p>
     * 使用场景：某些功能只允许特定角色访问，比如"管理员"、"审核员"等
     * 例如：判断用户是否是"管理员"或"超级管理员"
     *
     * @param userId 用户ID
     * @param roles  角色编码数组（例如：admin、auditor）
     * @return true=拥有任意一个角色，false=不拥有任何角色
     */
    @Override
    public boolean hasAnyRoles(Long userId, String... roles) {
        // 步骤1：如果传入的角色数组为空，默认认为有权限
        // 原因：空数组表示不需要角色校验
        if (ArrayUtil.isEmpty(roles)) {
            return true;
        }

        // 步骤2：获取用户的所有启用状态的角色
        // 注意：会过滤掉被禁用的角色
        List<RoleDO> roleList = getEnableUserRoleListByUserIdFromCache(userId);
        if (CollUtil.isEmpty(roleList)) {
            // 如果用户没有任何角色，返回无权限
            return false;
        }

        // 步骤3：提取用户的角色编码集合，然后判断是否与要求的角色有交集
        // 注意：这里用的是角色编码（code），不是角色ID
        Set<String> userRoles = convertSet(roleList, RoleDO::getCode);
        // 只要用户拥有其中一个角色，就返回true
        return CollUtil.containsAny(userRoles, Sets.newHashSet(roles));
    }

    // ========== 角色-菜单的相关方法  ==========

    /**
     * 给角色分配菜单权限（核心方法）
     * <p>
     * 业务场景：管理员在后台给某个角色分配可访问的菜单
     * 例如：给"财务角色"分配"财务管理"、"报表查询"等菜单的访问权限
     * <p>
     * 实现思路：增量更新策略
     * 1. 查询角色当前已有的菜单
     * 2. 对比新传入的菜单，计算出需要新增和删除的菜单
     * 3. 执行新增和删除操作（已存在的不处理，提高效率）
     *
     * @param roleId  角色ID
     * @param menuIds 要分配的菜单ID集合
     */
    @Override
    @DSTransactional // 多数据源事务注解，保证跨数据源时的事务一致性
    @Caching(evict = {
            @CacheEvict(value = RedisKeyConstants.MENU_ROLE_ID_LIST,
                    allEntries = true), // 清空"菜单-角色"缓存
            @CacheEvict(value = RedisKeyConstants.PERMISSION_MENU_ID_LIST,
                    allEntries = true) // 清空"权限-菜单"缓存，因为一次更新可能涉及多个菜单，批量清空更快
    })
    public void assignRoleMenu(Long roleId, Set<Long> menuIds) {
        // 步骤1：查询该角色在数据库中已有的菜单ID集合
        Set<Long> dbMenuIds = convertSet(roleMenuMapper.selectListByRoleId(roleId), RoleMenuDO::getMenuId);

        // 步骤2：计算差异 - 找出需要新增和删除的菜单
        Set<Long> menuIdList = CollUtil.emptyIfNull(menuIds); // 防止空指针
        Collection<Long> createMenuIds = CollUtil.subtract(menuIdList, dbMenuIds); // 新菜单 = 传入的 - 已有的
        Collection<Long> deleteMenuIds = CollUtil.subtract(dbMenuIds, menuIdList); // 要删除的 = 已有的 - 传入的

        // 步骤3：执行新增操作 - 把新增的菜单权限插入数据库
        if (CollUtil.isNotEmpty(createMenuIds)) {
            roleMenuMapper.insertBatch(CollectionUtils.convertList(createMenuIds, menuId -> {
                RoleMenuDO entity = new RoleMenuDO();
                entity.setRoleId(roleId);
                entity.setMenuId(menuId);
                return entity;
            }));
        }

        // 步骤4：执行删除操作 - 把要移除的菜单权限从数据库删除
        if (CollUtil.isNotEmpty(deleteMenuIds)) {
            roleMenuMapper.deleteListByRoleIdAndMenuIds(roleId, deleteMenuIds);
        }

        // 注意：对于已经存在的菜单权限，不做任何处理，避免不必要的数据库操作
    }

    /**
     * 处理角色删除后的清理工作
     * <p>
     * 业务场景：当管理员删除某个角色时，需要清理与该角色相关的所有关联数据
     * 包括：
     * 1. 用户-角色关系（有哪些用户被分配了这个角色）
     * 2. 角色-菜单关系（这个角色被分配了哪些菜单）
     * <p>
     * 为什么要清理：避免数据库中存在"脏数据"（指向已删除角色的关联记录）
     *
     * @param roleId 被删除的角色ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // 开启事务，任何异常都回滚
    @Caching(evict = {
            @CacheEvict(value = RedisKeyConstants.MENU_ROLE_ID_LIST,
                    allEntries = true), // 清空所有"菜单-角色"缓存
            @CacheEvict(value = RedisKeyConstants.USER_ROLE_ID_LIST,
                    allEntries = true) // 清空所有"用户-角色"缓存
            // 注意：这里用 allEntries=true 是因为无法方便地获取该角色关联的所有用户和菜单的缓存键
    })
    public void processRoleDeleted(Long roleId) {
        // 步骤1：删除所有分配了该角色的用户关系
        // 结果：之前拥有该角色的用户将失去该角色
        userRoleMapper.deleteListByRoleId(roleId);

        // 步骤2：删除该角色拥有的所有菜单权限
        // 结果：清除该角色的菜单权限配置
        roleMenuMapper.deleteListByRoleId(roleId);
    }

    /**
     * 处理菜单删除后的清理工作
     * <p>
     * 业务场景：当管理员删除某个菜单时，需要删除所有角色与该菜单的关联关系
     * 例如：删除"用户管理"菜单后，之前分配了这个菜单的所有角色都将失去对该菜单的访问权限
     *
     * @param menuId 被删除的菜单ID
     */
    @Override
    @CacheEvict(value = RedisKeyConstants.MENU_ROLE_ID_LIST, key = "#menuId") // 清除该菜单的角色缓存
    public void processMenuDeleted(Long menuId) {
        // 删除所有角色与该菜单的关联关系
        // 结果：之前拥有该菜单的所有角色将失去对该菜单的访问权限
        roleMenuMapper.deleteListByMenuId(menuId);
    }

    /**
     * 根据角色ID集合获取菜单ID集合
     * <p>
     * 业务场景：在前端展示用户菜单时，需要知道用户的角色拥有哪些菜单
     * <p>
     * 特殊处理：超级管理员可以访问所有菜单
     *
     * @param roleIds 角色ID集合
     * @return 菜单ID集合
     */
    @Override
    public Set<Long> getRoleMenuListByRoleId(Collection<Long> roleIds) {
        // 步骤1：如果角色ID集合为空，返回空集合
        if (CollUtil.isEmpty(roleIds)) {
            return Collections.emptySet();
        }

        // 步骤2：特殊情况 - 如果包含超级管理员角色，返回所有菜单
        // 原因：超级管理员拥有系统的所有权限
        if (roleService.hasAnySuperAdmin(roleIds)) {
            return convertSet(menuService.getMenuList(), MenuDO::getId);
        }

        // 步骤3：普通情况 - 从数据库查询这些角色拥有的菜单
        return convertSet(roleMenuMapper.selectListByRoleId(roleIds), RoleMenuDO::getMenuId);
    }

    /**
     * 从缓存中获取某个菜单被分配给了哪些角色
     * <p>
     * 业务场景：权限校验时需要快速判断用户的角色是否拥有某个菜单的访问权限
     * <p>
     * 性能优化：使用Spring Cache，查询结果会被缓存到Redis中，减少数据库访问
     *
     * @param menuId 菜单ID
     * @return 拥有该菜单的角色ID集合
     */
    @Override
    @Cacheable(value = RedisKeyConstants.MENU_ROLE_ID_LIST, key = "#menuId") // 缓存键格式：menu_role_id_list:{menuId}
    public Set<Long> getMenuRoleIdListByMenuIdFromCache(Long menuId) {
        // 从数据库查询该菜单关联的所有角色，并将角色ID提取为集合返回
        return convertSet(roleMenuMapper.selectListByMenuId(menuId), RoleMenuDO::getRoleId);
    }

    // ========== 用户-角色的相关方法  ==========

    /**
     * 给用户分配角色（核心方法）
     * <p>
     * 业务场景：管理员在后台给某个用户分配角色
     * 例如：给"张三"分配"财务角色"、"审核员"等角色
     * <p>
     * 实现思路：增量更新策略（与分配菜单类似）
     * 1. 查询用户当前已有的角色
     * 2. 对比新传入的角色，计算出需要新增和删除的角色
     * 3. 执行新增和删除操作
     *
     * @param userId  用户ID
     * @param roleIds 要分配的角色ID集合
     */
    @Override
    @DSTransactional // 多数据源事务注解
    @CacheEvict(value = RedisKeyConstants.USER_ROLE_ID_LIST, key = "#userId") // 清除该用户的角色缓存
    public void assignUserRole(Long userId, Set<Long> roleIds) {
        // 步骤1：查询该用户在数据库中已有的角色ID集合
        Set<Long> dbRoleIds = convertSet(userRoleMapper.selectListByUserId(userId),
                UserRoleDO::getRoleId);

        // 步骤2：计算差异 - 找出需要新增和删除的角色
        Set<Long> roleIdList = CollUtil.emptyIfNull(roleIds); // 防止空指针
        Collection<Long> createRoleIds = CollUtil.subtract(roleIdList, dbRoleIds); // 新角色 = 传入的 - 已有的
        Collection<Long> deleteMenuIds = CollUtil.subtract(dbRoleIds, roleIdList); // 要删除的 = 已有的 - 传入的

        // 步骤3：执行新增操作 - 把新增的角色分配给用户
        if (!CollectionUtil.isEmpty(createRoleIds)) {
            userRoleMapper.insertBatch(CollectionUtils.convertList(createRoleIds, roleId -> {
                UserRoleDO entity = new UserRoleDO();
                entity.setUserId(userId);
                entity.setRoleId(roleId);
                return entity;
            }));
        }

        // 步骤4：执行删除操作 - 把要移除的角色从用户身上删除
        if (!CollectionUtil.isEmpty(deleteMenuIds)) {
            userRoleMapper.deleteListByUserIdAndRoleIdIds(userId, deleteMenuIds);
        }

        // 注意：对于用户已经拥有的角色，不做任何处理
    }

    /**
     * 处理用户删除后的清理工作
     * <p>
     * 业务场景：当删除用户时，需要清理该用户的所有角色关联关系
     *
     * @param userId 被删除的用户ID
     */
    @Override
    @CacheEvict(value = RedisKeyConstants.USER_ROLE_ID_LIST, key = "#userId") // 清除该用户的角色缓存
    public void processUserDeleted(Long userId) {
        // 删除该用户的所有角色关联
        // 结果：该用户将失去所有角色和权限
        userRoleMapper.deleteListByUserId(userId);
    }

    /**
     * 获取某个用户拥有的所有角色ID（不使用缓存）
     *
     * @param userId 用户ID
     * @return 该用户的角色ID集合
     */
    @Override
    public Set<Long> getUserRoleIdListByUserId(Long userId) {
        return convertSet(userRoleMapper.selectListByUserId(userId), UserRoleDO::getRoleId);
    }

    /**
     * 从缓存中获取某个用户拥有的所有角色ID
     * <p>
     * 性能优化：使用Spring Cache，查询结果会被缓存到Redis中
     *
     * @param userId 用户ID
     * @return 该用户的角色ID集合
     */
    @Override
    @Cacheable(value = RedisKeyConstants.USER_ROLE_ID_LIST, key = "#userId") // 缓存键格式：user_role_id_list:{userId}
    public Set<Long> getUserRoleIdListByUserIdFromCache(Long userId) {
        return getUserRoleIdListByUserId(userId);
    }

    /**
     * 获取拥有某些角色的所有用户ID
     * <p>
     * 业务场景：例如查找所有拥有"管理员"或"审核员"角色的用户
     *
     * @param roleIds 角色ID集合
     * @return 拥有这些角色的用户ID集合
     */
    @Override
    public Set<Long> getUserRoleIdListByRoleId(Collection<Long> roleIds) {
        return convertSet(userRoleMapper.selectListByRoleIds(roleIds), UserRoleDO::getUserId);
    }

    /**
     * 获取用户拥有的所有启用状态的角色（从缓存读取）
     * <p>
     * 核心逻辑：
     * 1. 获取用户的所有角色ID
     * 2. 查询这些角色的详细信息
     * 3. 过滤掉被禁用的角色
     * <p>
     * 为什么要过滤禁用角色：
     * - 被禁用的角色不应该再拥有任何权限
     * - 避免管理员禁用角色后，用户仍然可以使用该角色的权限
     *
     * @param userId 用户编号
     * @return 用户拥有的启用状态的角色列表
     */
    @VisibleForTesting
    // 这个注解表示：此方法是包私有的，但在测试中可以访问
    List<RoleDO> getEnableUserRoleListByUserIdFromCache(Long userId) {
        // 步骤1：从缓存中获取用户拥有的所有角色ID
        // 注意：这里调用 getSelf() 是为了确保 Spring 缓存注解生效
        Set<Long> roleIds = getSelf().getUserRoleIdListByUserIdFromCache(userId);

        // 步骤2：根据角色ID集合，从缓存中获取角色的详细信息
        List<RoleDO> roles = roleService.getRoleListFromCache(roleIds);

        // 步骤3：过滤掉被禁用的角色（只保留启用状态的角色）
        // removeIf：移除满足条件的元素，这里移除状态不是"启用"的角色
        roles.removeIf(role -> !CommonStatusEnum.ENABLE.getStatus().equals(role.getStatus()));

        return roles;
    }

    // ========== 数据权限的相关方法  ==========

    /**
     * 给角色分配数据权限范围
     * <p>
     * 业务场景：控制角色可以查看哪些部门的数据
     * 例如：财务角色只能查看财务部门的数据，人事角色只能查看人事部门的数据
     *
     * @param roleId           角色ID
     * @param dataScope        数据权限范围（例如：全部数据、本部门、本部门及下级部门等）
     * @param dataScopeDeptIds 自定义数据权限的部门ID集合（当dataScope为自定义时使用）
     */
    @Override
    public void assignRoleDataScope(Long roleId, Integer dataScope, Set<Long> dataScopeDeptIds) {
        // 委托给角色服务更新数据权限范围
        roleService.updateRoleDataScope(roleId, dataScope, dataScopeDeptIds);
    }

    /**
     * 获取用户的部门数据权限
     * <p>
     * 核心功能：根据用户的角色，计算出用户可以查看哪些部门的数据
     * <p>
     * 数据权限的5种类型：
     * 1. ALL（全部数据）：可以查看所有部门的数据
     * 2. DEPT_CUSTOM（自定义部门）：只能查看指定的部门数据
     * 3. DEPT_ONLY（仅本部门）：只能查看自己所在部门的数据
     * 4. DEPT_AND_CHILD（本部门及下级部门）：可以查看本部门及所有下级部门的数据
     * 5. SELF（仅本人）：只能查看自己创建的数据
     * <p>
     * 重要规则：用户拥有多个角色时，取最大权限
     * 例如：用户同时拥有"财务角色"（仅本部门）和"经理角色"（本部门及下级），则最终权限为"本部门及下级"
     *
     * @param userId 用户ID
     * @return 部门数据权限信息
     */
    @Override
    @DataPermission(enable = false) // 关闭数据权限，避免递归获取数据权限导致死循环
    public DeptDataPermissionRespDTO getDeptDataPermission(Long userId) {
        // 步骤1：获取用户的所有启用状态的角色
        List<RoleDO> roles = getEnableUserRoleListByUserIdFromCache(userId);

        // 步骤2：如果用户没有任何角色，默认只能查看自己的数据
        DeptDataPermissionRespDTO result = new DeptDataPermissionRespDTO();
        if (CollUtil.isEmpty(roles)) {
            result.setSelf(true); // 设置为"仅本人"权限
            return result;
        }

        // 步骤3：使用惰性求值获取用户的部门ID
        // 说明：用 Suppliers.memoize 包装，只有真正需要时才查询数据库，且只查询一次
        Supplier<Long> userDeptId = Suppliers.memoize(() -> userService.getUser(userId).getDeptId());

        // 步骤4：遍历用户的每个角色，累积计算数据权限
        for (RoleDO role : roles) {
            // 4.1 如果角色没有设置数据权限，跳过
            if (role.getDataScope() == null) {
                continue;
            }

            // 4.2 情况一：全部数据权限
            // 说明：拥有此权限的角色可以查看所有部门的数据
            if (Objects.equals(role.getDataScope(), DataScopeEnum.ALL.getScope())) {
                result.setAll(true);
                continue;
            }

            // 4.3 情况二：自定义部门权限
            // 说明：可以查看管理员指定的部门数据
            if (Objects.equals(role.getDataScope(), DataScopeEnum.DEPT_CUSTOM.getScope())) {
                // 添加自定义的部门ID列表
                CollUtil.addAll(result.getDeptIds(), role.getDataScopeDeptIds());
                // 重要：同时添加用户自己所在的部门
                // 原因：避免某些场景下，用户因为部门过滤而查不到自己的数据（例如登录时）
                CollUtil.addAll(result.getDeptIds(), userDeptId.get());
                continue;
            }

            // 4.4 情况三：仅本部门权限
            // 说明：只能查看自己所在部门的数据
            if (Objects.equals(role.getDataScope(), DataScopeEnum.DEPT_ONLY.getScope())) {
                CollectionUtils.addIfNotNull(result.getDeptIds(), userDeptId.get());
                continue;
            }

            // 4.5 情况四：本部门及下级部门权限
            // 说明：可以查看自己所在部门及所有下级部门的数据
            if (Objects.equals(role.getDataScope(), DataScopeEnum.DEPT_AND_CHILD.getScope())) {
                // 添加所有下级部门ID
                CollUtil.addAll(result.getDeptIds(), deptService.getChildDeptIdListFromCache(userDeptId.get()));
                // 添加本部门ID
                CollUtil.addAll(result.getDeptIds(), userDeptId.get());
                continue;
            }

            // 4.6 情况五：仅本人权限
            // 说明：只能查看自己创建的数据
            if (Objects.equals(role.getDataScope(), DataScopeEnum.SELF.getScope())) {
                result.setSelf(true);
                continue;
            }

            // 4.7 未知情况：记录错误日志
            // 这种情况理论上不应该出现，如果出现说明有配置错误
            log.error("[getDeptDataPermission][LoginUser({}) role({}) 无法处理]", userId, toJsonString(result));
        }

        return result;
    }

    /**
     * 获得自身的代理对象，解决 AOP 生效问题
     * <p>
     * 技术背景：
     * Spring的AOP（如缓存、事务注解）是通过代理实现的，只有通过Spring容器获取的Bean才有AOP功能
     * 如果在类内部直接调用自己的方法（this.method()），不会触发AOP，导致缓存等注解失效
     * <p>
     * 解决方案：
     * 通过SpringUtil获取自己的代理对象，然后调用代理对象的方法，这样AOP注解就能生效了
     * <p>
     * 使用示例：
     * getSelf().getUserRoleIdListByUserIdFromCache(userId) // 缓存注解生效
     * this.getUserRoleIdListByUserIdFromCache(userId)     // 缓存注解失效
     *
     * @return 自己的Spring代理对象
     */
    private PermissionServiceImpl getSelf() {
        return SpringUtil.getBean(getClass());
    }

}
