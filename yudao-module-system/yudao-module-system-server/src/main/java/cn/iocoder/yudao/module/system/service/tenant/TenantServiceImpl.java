package cn.iocoder.yudao.module.system.service.tenant;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.role.RoleSaveReqVO;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO;
import cn.iocoder.yudao.module.system.convert.tenant.TenantConvert;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantPackageDO;
import cn.iocoder.yudao.module.system.dal.mysql.tenant.TenantMapper;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import cn.iocoder.yudao.module.system.enums.permission.RoleTypeEnum;
import cn.iocoder.yudao.module.system.service.permission.MenuService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.permission.RoleService;
import cn.iocoder.yudao.module.system.service.tenant.handler.TenantInfoHandler;
import cn.iocoder.yudao.module.system.service.tenant.handler.TenantMenuHandler;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import static java.util.Collections.singleton;

/**
 * 租户 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
@Slf4j
public class TenantServiceImpl implements TenantService {

    /**
     * 租户配置属性
     * 注意：使用 @Autowired(required = false) 是因为可以通过配置关闭多租户功能
     * 当多租户功能关闭时，这个 Bean 可能不存在，所以不强制注入
     */
    @SuppressWarnings("SpringJavaAutowiredFieldsWarningInspection")
    @Autowired(required = false) // 由于 yudao.tenant.enable 配置项，可以关闭多租户的功能，所以这里只能不强制注入
    private TenantProperties tenantProperties;

    /**
     * 租户数据访问对象（Mapper）
     * 用于操作租户表的增删改查
     */
    @Resource
    private TenantMapper tenantMapper;

    /**
     * 租户套餐服务
     * 用于管理租户的套餐信息（套餐决定了租户可以使用哪些菜单功能）
     */
    @Resource
    private TenantPackageService tenantPackageService;

    /**
     * 管理员用户服务
     * 使用 @Lazy 延迟加载，避免循环依赖问题
     * （因为 AdminUserService 可能也依赖 TenantService）
     */
    @Resource
    @Lazy // 延迟，避免循环依赖报错
    private AdminUserService userService;

    /**
     * 角色服务
     * 用于管理系统中的角色信息
     */
    @Resource
    private RoleService roleService;

    /**
     * 菜单服务
     * 用于管理系统中的菜单信息
     */
    @Resource
    private MenuService menuService;

    /**
     * 权限服务
     * 用于管理角色和菜单的权限关系
     */
    @Resource
    private PermissionService permissionService;

    /**
     * 获取所有租户的 ID 列表
     *
     * @return 租户 ID 列表
     */
    @Override
    public List<Long> getTenantIdList() {
        // 1. 查询所有租户
        List<TenantDO> tenants = tenantMapper.selectList();
        // 2. 将租户对象列表转换为租户 ID 列表
        return CollectionUtils.convertList(tenants, TenantDO::getId);
    }

    /**
     * 校验租户是否有效
     * 检查租户是否存在、是否被禁用、是否已过期
     *
     * @param id 租户 ID
     * @throws 如果租户不存在、被禁用或已过期，则抛出业务异常
     */
    @Override
    public void validTenant(Long id) {
        // 1. 根据 ID 获取租户信息
        TenantDO tenant = getTenant(id);

        // 2. 校验租户是否存在
        if (tenant == null) {
            throw exception(TENANT_NOT_EXISTS);
        }

        // 3. 校验租户是否被禁用
        if (tenant.getStatus().equals(CommonStatusEnum.DISABLE.getStatus())) {
            throw exception(TENANT_DISABLE, tenant.getName());
        }

        // 4. 校验租户是否已过期
        if (DateUtils.isExpired(tenant.getExpireTime())) {
            throw exception(TENANT_EXPIRE, tenant.getName());
        }
    }

    /**
     * 创建租户
     * 这是一个复杂的操作，包括：创建租户、创建管理员角色、创建管理员用户
     *
     * @param createReqVO 创建租户的请求参数
     * @return 新创建的租户 ID
     */
    @Override
    @DSTransactional // 多数据源，使用 @DSTransactional 保证本地事务，以及数据源的切换
    @DataPermission(enable = false) // 参见 https://gitee.com/zhijiantianya/ruoyi-vue-pro/pulls/1154 说明
    public Long createTenant(TenantSaveReqVO createReqVO) {
        // 1. 校验租户名称是否重复（同一个名称不能创建多个租户）
        validTenantNameDuplicate(createReqVO.getName(), null);

        // 2. 校验租户域名是否重复（同一个域名不能绑定多个租户）
        validTenantWebsiteDuplicate(createReqVO.getWebsites(), null);

        // 3. 校验套餐是否有效（租户必须关联一个有效的套餐）
        TenantPackageDO tenantPackage = tenantPackageService.validTenantPackage(createReqVO.getPackageId());

        // 4. 创建租户记录
        TenantDO tenant = BeanUtils.toBean(createReqVO, TenantDO.class);
        tenantMapper.insert(tenant);

        // 5. 在新租户的上下文中，创建租户的管理员相关信息
        TenantUtils.execute(tenant.getId(), () -> {
            // 5.1 创建租户管理员角色，并分配套餐中的菜单权限
            Long roleId = createRole(tenantPackage);

            // 5.2 创建租户管理员用户，并分配管理员角色
            Long userId = createUser(roleId, createReqVO);

            // 5.3 更新租户信息，设置联系人用户 ID
            tenantMapper.updateById(new TenantDO().setId(tenant.getId()).setContactUserId(userId));
        });

        // 6. 返回新创建的租户 ID
        return tenant.getId();
    }

    /**
     * 创建租户的管理员用户
     *
     * @param roleId 角色 ID（租户管理员角色）
     * @param createReqVO 创建租户的请求参数
     * @return 新创建的用户 ID
     */
    private Long createUser(Long roleId, TenantSaveReqVO createReqVO) {
        // 1. 创建用户（将租户创建请求中的用户信息转换为用户创建请求）
        Long userId = userService.createUser(TenantConvert.INSTANCE.convert02(createReqVO));

        // 2. 为用户分配角色（分配租户管理员角色）
        permissionService.assignUserRole(userId, singleton(roleId));

        // 3. 返回用户 ID
        return userId;
    }

    /**
     * 创建租户管理员角色
     *
     * @param tenantPackage 租户套餐（包含了该租户可以使用的菜单权限）
     * @return 新创建的角色 ID
     */
    private Long createRole(TenantPackageDO tenantPackage) {
        // 1. 构建角色创建请求
        RoleSaveReqVO reqVO = new RoleSaveReqVO();
        reqVO.setName(RoleCodeEnum.TENANT_ADMIN.getName()) // 设置角色名称：租户管理员
                .setCode(RoleCodeEnum.TENANT_ADMIN.getCode()) // 设置角色编码
                .setSort(0) // 设置排序号
                .setRemark("系统自动生成"); // 设置备注

        // 2. 创建角色（系统角色类型）
        Long roleId = roleService.createRole(reqVO, RoleTypeEnum.SYSTEM.getType());

        // 3. 为角色分配菜单权限（从租户套餐中获取菜单权限）
        permissionService.assignRoleMenu(roleId, tenantPackage.getMenuIds());

        // 4. 返回角色 ID
        return roleId;
    }

    /**
     * 更新租户信息
     *
     * @param updateReqVO 更新租户的请求参数
     */
    @Override
    @DSTransactional // 多数据源，使用 @DSTransactional 保证本地事务，以及数据源的切换
    public void updateTenant(TenantSaveReqVO updateReqVO) {
        // 1. 校验租户是否存在，以及是否可以被更新
        TenantDO tenant = validateUpdateTenant(updateReqVO.getId());

        // 2. 校验租户名称是否重复
        validTenantNameDuplicate(updateReqVO.getName(), updateReqVO.getId());

        // 3. 校验租户域名是否重复
        validTenantWebsiteDuplicate(updateReqVO.getWebsites(), updateReqVO.getId());

        // 4. 校验套餐是否有效
        TenantPackageDO tenantPackage = tenantPackageService.validTenantPackage(updateReqVO.getPackageId());

        // 5. 更新租户信息
        TenantDO updateObj = BeanUtils.toBean(updateReqVO, TenantDO.class);
        tenantMapper.updateById(updateObj);

        // 6. 如果套餐发生变化，则需要更新租户下所有角色的菜单权限
        // （因为套餐决定了租户可以使用哪些菜单，套餐变了权限也要跟着变）
        if (ObjectUtil.notEqual(tenant.getPackageId(), updateReqVO.getPackageId())) {
            updateTenantRoleMenu(tenant.getId(), tenantPackage.getMenuIds());
        }
    }

    /**
     * 校验租户名称是否重复
     *
     * @param name 租户名称
     * @param id 租户 ID（如果是更新操作，需要排除自己）
     * @throws 如果名称重复，则抛出业务异常
     */
    private void validTenantNameDuplicate(String name, Long id) {
        // 1. 根据名称查询租户
        TenantDO tenant = tenantMapper.selectByName(name);

        // 2. 如果没有查到，说明不重复
        if (tenant == null) {
            return;
        }

        // 3. 如果 id 为空，说明是新增操作，只要查到就是重复
        if (id == null) {
            throw exception(TENANT_NAME_DUPLICATE, name);
        }

        // 4. 如果 id 不为空，说明是更新操作，需要判断查到的租户是否是自己
        // 如果不是自己，说明重复了
        if (!tenant.getId().equals(id)) {
            throw exception(TENANT_NAME_DUPLICATE, name);
        }
    }

    /**
     * 校验租户域名是否重复
     *
     * @param websites 域名列表
     * @param excludeId 要排除的租户 ID（更新时排除自己）
     * @throws 如果域名重复，则抛出业务异常
     */
    private void validTenantWebsiteDuplicate(List<String> websites, Long excludeId) {
        // 1. 如果域名列表为空，不需要校验
        if (CollUtil.isEmpty(websites)) {
            return;
        }

        // 2. 遍历每个域名，检查是否被其他租户使用
        websites.forEach(website -> {
            // 2.1 查询使用该域名的所有租户
            List<TenantDO> tenants = tenantMapper.selectListByWebsite(website);

            // 2.2 如果是更新操作，需要排除自己
            if (excludeId != null) {
                tenants.removeIf(tenant -> tenant.getId().equals(excludeId));
            }

            // 2.3 如果还有其他租户使用该域名，说明重复了
            if (CollUtil.isNotEmpty(tenants)) {
                throw exception(TENANT_WEBSITE_DUPLICATE, website);
            }
        });
    }

    /**
     * 更新租户下所有角色的菜单权限
     * 当租户的套餐发生变化时，需要调用此方法更新权限
     *
     * @param tenantId 租户 ID
     * @param menuIds 新的菜单权限集合（从新套餐中获取）
     */
    @Override
    @DSTransactional
    public void updateTenantRoleMenu(Long tenantId, Set<Long> menuIds) {
        // 在指定租户的上下文中执行操作
        TenantUtils.execute(tenantId, () -> {
            // 1. 获取该租户下的所有角色
            List<RoleDO> roles = roleService.getRoleList();

            // 2. 校验所有角色都属于该租户（兜底校验，防止数据错乱）
            roles.forEach(role -> Assert.isTrue(tenantId.equals(role.getTenantId()), "角色({}/{}) 租户不匹配",
                    role.getId(), role.getTenantId(), tenantId));

            // 3. 重新分配每个角色的菜单权限
            roles.forEach(role -> {
                // 3.1 如果是租户管理员角色，直接分配新套餐的所有权限
                if (Objects.equals(role.getCode(), RoleCodeEnum.TENANT_ADMIN.getCode())) {
                    permissionService.assignRoleMenu(role.getId(), menuIds);
                    log.info("[updateTenantRoleMenu][租户管理员({}/{}) 的权限修改为({})]", role.getId(), role.getTenantId(), menuIds);
                    return;
                }

                // 3.2 如果是其他角色，取原权限和新套餐权限的交集
                // （保留原有权限中，新套餐仍然包含的那部分权限）
                Set<Long> roleMenuIds = permissionService.getRoleMenuListByRoleId(role.getId());
                roleMenuIds = CollUtil.intersectionDistinct(roleMenuIds, menuIds);
                permissionService.assignRoleMenu(role.getId(), roleMenuIds);
                log.info("[updateTenantRoleMenu][角色({}/{}) 的权限修改为({})]", role.getId(), role.getTenantId(), roleMenuIds);
            });
        });
    }

    /**
     * 删除租户
     *
     * @param id 租户 ID
     */
    @Override
    public void deleteTenant(Long id) {
        // 1. 校验租户是否存在，以及是否可以被删除
        validateUpdateTenant(id);

        // 2. 删除租户
        tenantMapper.deleteById(id);
    }

    /**
     * 批量删除租户
     *
     * @param ids 租户 ID 列表
     */
    @Override
    public void deleteTenantList(List<Long> ids) {
        // 1. 校验每个租户是否存在，以及是否可以被删除
        ids.forEach(this::validateUpdateTenant);

        // 2. 批量删除租户
        tenantMapper.deleteByIds(ids);
    }

    /**
     * 校验租户是否可以被更新或删除
     *
     * @param id 租户 ID
     * @return 租户信息
     * @throws 如果租户不存在或是系统租户，则抛出业务异常
     */
    private TenantDO validateUpdateTenant(Long id) {
        // 1. 根据 ID 查询租户
        TenantDO tenant = tenantMapper.selectById(id);

        // 2. 校验租户是否存在
        if (tenant == null) {
            throw exception(TENANT_NOT_EXISTS);
        }

        // 3. 校验是否是系统内置租户
        // 系统内置租户不允许被更新或删除
        if (isSystemTenant(tenant)) {
            throw exception(TENANT_CAN_NOT_UPDATE_SYSTEM);
        }

        // 4. 返回租户信息
        return tenant;
    }

    /**
     * 根据 ID 获取租户信息
     *
     * @param id 租户 ID
     * @return 租户信息
     */
    @Override
    public TenantDO getTenant(Long id) {
        return tenantMapper.selectById(id);
    }

    /**
     * 分页查询租户列表
     *
     * @param pageReqVO 分页查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<TenantDO> getTenantPage(TenantPageReqVO pageReqVO) {
        return tenantMapper.selectPage(pageReqVO);
    }

    /**
     * 根据租户名称获取租户信息
     *
     * @param name 租户名称
     * @return 租户信息
     */
    @Override
    public TenantDO getTenantByName(String name) {
        return tenantMapper.selectByName(name);
    }

    /**
     * 根据域名获取租户信息
     *
     * @param website 域名
     * @return 租户信息（如果有多个租户使用该域名，返回第一个）
     */
    @Override
    public TenantDO getTenantByWebsite(String website) {
        // 1. 查询使用该域名的所有租户
        List<TenantDO> tenants = tenantMapper.selectListByWebsite(website);

        // 2. 返回第一个租户（正常情况下应该只有一个）
        return CollUtil.getFirst(tenants);
    }

    /**
     * 获取使用指定套餐的租户数量
     *
     * @param packageId 套餐 ID
     * @return 租户数量
     */
    @Override
    public Long getTenantCountByPackageId(Long packageId) {
        return tenantMapper.selectCountByPackageId(packageId);
    }

    /**
     * 获取使用指定套餐的租户列表
     *
     * @param packageId 套餐 ID
     * @return 租户列表
     */
    @Override
    public List<TenantDO> getTenantListByPackageId(Long packageId) {
        return tenantMapper.selectListByPackageId(packageId);
    }

    /**
     * 根据状态获取租户列表
     *
     * @param status 租户状态（0-正常，1-停用）
     * @return 租户列表
     */
    @Override
    public List<TenantDO> getTenantListByStatus(Integer status) {
        return tenantMapper.selectListByStatus(status);
    }

    /**
     * 处理租户信息
     * 使用处理器模式，允许外部传入处理逻辑
     *
     * @param handler 租户信息处理器
     */
    @Override
    public void handleTenantInfo(TenantInfoHandler handler) {
        // 1. 如果多租户功能被禁用，则不执行处理逻辑
        if (isTenantDisable()) {
            return;
        }

        // 2. 从上下文中获取当前租户 ID，并查询租户信息
        TenantDO tenant = getTenant(TenantContextHolder.getRequiredTenantId());

        // 3. 执行处理器的处理逻辑
        handler.handle(tenant);
    }

    /**
     * 处理租户菜单权限
     * 使用处理器模式，允许外部传入处理逻辑
     *
     * @param handler 租户菜单处理器
     */
    @Override
    public void handleTenantMenu(TenantMenuHandler handler) {
        // 1. 如果多租户功能被禁用，则不执行处理逻辑
        if (isTenantDisable()) {
            return;
        }

        // 2. 从上下文中获取当前租户 ID，并查询租户信息
        TenantDO tenant = getTenant(TenantContextHolder.getRequiredTenantId());

        // 3. 获取租户的菜单权限集合
        Set<Long> menuIds;
        if (isSystemTenant(tenant)) {
            // 3.1 如果是系统租户，拥有所有菜单权限
            menuIds = CollectionUtils.convertSet(menuService.getMenuList(), MenuDO::getId);
        } else {
            // 3.2 如果是普通租户，从租户套餐中获取菜单权限
            menuIds = tenantPackageService.getTenantPackage(tenant.getPackageId()).getMenuIds();
        }

        // 4. 执行处理器的处理逻辑
        handler.handle(menuIds);
    }

    /**
     * 判断是否是系统租户
     * 系统租户是一个特殊的内置租户，拥有所有权限
     *
     * @param tenant 租户信息
     * @return true-是系统租户，false-不是系统租户
     */
    private static boolean isSystemTenant(TenantDO tenant) {
        return Objects.equals(tenant.getPackageId(), TenantDO.PACKAGE_ID_SYSTEM);
    }

    /**
     * 判断多租户功能是否被禁用
     *
     * @return true-已禁用，false-未禁用
     */
    private boolean isTenantDisable() {
        return tenantProperties == null || Boolean.FALSE.equals(tenantProperties.getEnable());
    }

}
