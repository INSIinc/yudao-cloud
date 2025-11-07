package cn.iocoder.yudao.framework.common.biz.system.permission;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.enums.RpcConstants;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 权限通用 API 接口
 * <p>
 * 这是一个 Feign 客户端接口，用于微服务之间的远程调用
 * 主要功能：提供权限验证、角色验证和数据权限查询的远程服务
 *
 * @FeignClient 注解说明：
 * - name: 指定要调用的服务名称，这里是系统服务(SYSTEM_NAME)
 * - primary: 设置为false，避免与其他Bean冲突
 * <p>
 * 使用场景：
 * 当其他微服务需要验证用户权限或角色时，通过这个接口远程调用系统服务
 */
@FeignClient(name = RpcConstants.SYSTEM_NAME, primary = false) // TODO 芋艿：fallbackFactory =
@Tag(name = "RPC 服务 - 权限")
public interface PermissionCommonApi {

    /**
     * API 接口的统一前缀
     * 例如：/rpc-api/system/permission
     */
    String PREFIX = RpcConstants.SYSTEM_PREFIX + "/permission";

    /**
     * 判断用户是否拥有任意一个指定的权限
     * <p>
     * 功能说明：
     * 检查指定用户是否至少拥有传入的权限列表中的一个权限
     * 只要有一个权限匹配就返回true，全部不匹配返回false
     * <p>
     * 参数说明：
     *
     * @param userId      用户的唯一标识ID，用于查询该用户的权限信息
     * @param permissions 可变长度的权限字符串数组，例如：["system:user:create", "system:user:update"]
     *                    <p>
     *                    返回值：
     *                    CommonResult<Boolean> - 统一返回结果封装类
     *                    - true: 用户至少拥有其中一个权限
     *                    - false: 用户不拥有任何传入的权限
     *                    <p>
     *                    使用示例：
     *                    hasAnyPermissions(1L, "system:user:create", "system:user:update")
     */
    @GetMapping(PREFIX + "/has-any-permissions")
    @Operation(summary = "判断是否有权限，任一一个即可")
    @Parameters({
            @Parameter(name = "userId", description = "用户编号", example = "1", required = true),
            @Parameter(name = "permissions", description = "权限", example = "read,write", required = true)
    })
    CommonResult<Boolean> hasAnyPermissions(@RequestParam("userId") Long userId,
                                            @RequestParam("permissions") String... permissions);

    /**
     * 判断用户是否拥有任意一个指定的角色
     * <p>
     * 功能说明：
     * 检查指定用户是否至少拥有传入的角色列表中的一个角色
     * 只要有一个角色匹配就返回true，全部不匹配返回false
     * <p>
     * 参数说明：
     *
     * @param userId 用户的唯一标识ID，用于查询该用户的角色信息
     * @param roles  可变长度的角色字符串数组，例如：["admin", "manager"]
     *               <p>
     *               返回值：
     *               CommonResult<Boolean> - 统一返回结果封装类
     *               - true: 用户至少拥有其中一个角色
     *               - false: 用户不拥有任何传入的角色
     *               <p>
     *               使用示例：
     *               hasAnyRoles(1L, "admin", "manager")
     */
    @GetMapping(PREFIX + "/has-any-roles")
    @Operation(summary = "判断是否有角色，任一一个即可")
    @Parameters({
            @Parameter(name = "userId", description = "用户编号", example = "1", required = true),
            @Parameter(name = "roles", description = "角色数组", example = "2", required = true)
    })
    CommonResult<Boolean> hasAnyRoles(@RequestParam("userId") Long userId,
                                      @RequestParam("roles") String... roles);

    /**
     * 获取用户的部门数据权限
     * <p>
     * 功能说明：
     * 查询指定用户在组织架构中的数据权限范围
     * 数据权限用于控制用户能够查看和操作哪些部门的数据
     * <p>
     * 常见的数据权限类型：
     * 1. 全部数据权限 - 可以查看所有部门的数据
     * 2. 本部门数据权限 - 只能查看自己所在部门的数据
     * 3. 本部门及以下数据权限 - 可以查看自己部门和下级部门的数据
     * 4. 自定义部门数据权限 - 可以查看指定部门列表的数据
     * 5. 仅本人数据权限 - 只能查看自己创建的数据
     * <p>
     * 参数说明：
     *
     * @param userId 用户的唯一标识ID
     *               <p>
     *               返回值：
     *               CommonResult<DeptDataPermissionRespDTO> - 包含用户的部门数据权限信息
     *               DeptDataPermissionRespDTO 对象包含：
     *               - 数据权限类型
     *               - 可访问的部门ID列表
     *               <p>
     *               使用场景：
     *               在查询业务数据时，根据用户的部门数据权限过滤数据范围
     *               例如：查询订单列表时，只显示用户有权限查看的部门的订单
     */
    @GetMapping(PREFIX + "/get-dept-data-permission")
    @Operation(summary = "获得登陆用户的部门数据权限")
    @Parameter(name = "userId", description = "用户编号", example = "2", required = true)
    CommonResult<DeptDataPermissionRespDTO> getDeptDataPermission(@RequestParam("userId") Long userId);

}