package cn.iocoder.yudao.module.system.api.user;

import cn.hutool.core.convert.Convert;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.enums.ApiConstants;
import com.fhs.core.trans.anno.AutoTrans;
import com.fhs.trans.service.AutoTransable;
import feign.FeignIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.system.api.user.AdminUserApi.PREFIX;

/**
 * 管理员用户相关的 Feign RPC 接口。
 * 用于在微服务之间远程调用用户模块的用户信息查询、校验等接口。
 *
 * 该接口被 @FeignClient 注解标记，表示它是一个 Feign 客户端，会通过 HTTP 调用远程服务（服务名为 ApiConstants.NAME）。
 * 同时实现了 AutoTransable<AdminUserRespDTO>，用于支持字段自动翻译（如 nickname 字段可能需要国际化或映射到名称）。
 */
@FeignClient(name = ApiConstants.NAME) // 指定服务名称，通常为 "system-server"；预留 fallbackFactory 用于熔断降级
@Tag(name = "RPC 服务 - 管理员用户") // OpenAPI 文档标签，用于 Swagger 分组
@AutoTrans(namespace = PREFIX, fields = {"nickname"}) // 自动翻译注解：对返回 DTO 中的 nickname 字段进行翻译（如根据 id 转为真实姓名）
public interface AdminUserApi extends AutoTransable<AdminUserRespDTO> {

    /**
     * 该接口的 URL 前缀，通常为 "/system/user"
     */
    String PREFIX = ApiConstants.PREFIX + "/user"; // ApiConstants.PREFIX 一般为 "/system"

    /**
     * 根据用户 ID 查询单个管理员用户信息。
     *
     * @param id 用户编号（主键）
     * @return 包装后的用户信息（AdminUserRespDTO），通过 CommonResult 返回
     */
    @GetMapping(PREFIX + "/get")
    @Operation(summary = "通过用户 ID 查询用户")
    @Parameter(name = "id", description = "用户编号", example = "1", required = true)
    CommonResult<AdminUserRespDTO> getUser(@RequestParam("id") Long id);

    /**
     * 查询指定用户的直接下属用户列表（常用于组织架构场景）。
     *
     * @param id 用户编号
     * @return 下属用户列表
     */
    @GetMapping(PREFIX + "/list-by-subordinate")
    @Operation(summary = "通过用户 ID 查询用户下属")
    @Parameter(name = "id", description = "用户编号", example = "1", required = true)
    CommonResult<List<AdminUserRespDTO>> getUserListBySubordinate(@RequestParam("id") Long id);

    /**
     * 根据多个用户 ID 批量查询用户列表。
     *
     * @param ids 用户编号集合（如 [1,2,3]）
     * @return 对应的用户列表
     */
    @GetMapping(PREFIX + "/list")
    @Operation(summary = "通过用户 ID 查询用户们")
    @Parameter(name = "ids", description = "用户编号数组", example = "1,2", required = true)
    CommonResult<List<AdminUserRespDTO>> getUserList(@RequestParam("ids") Collection<Long> ids);

    /**
     * 根据多个部门 ID 查询这些部门下的所有用户。
     *
     * @param deptIds 部门编号集合
     * @return 所属部门的用户列表
     */
    @GetMapping(PREFIX + "/list-by-dept-id")
    @Operation(summary = "获得指定部门的用户数组")
    @Parameter(name = "deptIds", description = "部门编号数组", example = "1,2", required = true)
    CommonResult<List<AdminUserRespDTO>> getUserListByDeptIds(@RequestParam("deptIds") Collection<Long> deptIds);

    /**
     * 根据多个岗位 ID 查询这些岗位下的所有用户。
     *
     * @param postIds 岗位编号集合
     * @return 所属岗位的用户列表
     */
    @GetMapping(PREFIX + "/list-by-post-id")
    @Operation(summary = "获得指定岗位的用户数组")
    @Parameter(name = "postIds", description = "岗位编号数组", example = "2,3", required = true)
    CommonResult<List<AdminUserRespDTO>> getUserListByPostIds(@RequestParam("postIds") Collection<Long> postIds);

    /**
     * 批量查询用户，并转换为以用户 ID 为 key 的 Map，便于后续 O(1) 查找。
     *
     * @param ids 用户编号集合
     * @return 用户 Map，key 为用户 ID，value 为用户信息
     */
    default Map<Long, AdminUserRespDTO> getUserMap(Collection<Long> ids) {
        List<AdminUserRespDTO> users = getUserList(ids).getCheckedData(); // 自动校验并获取数据，若失败则抛异常
        return CollectionUtils.convertMap(users, AdminUserRespDTO::getId); // 转为 Map
    }

    /**
     * 校验单个用户是否有效（存在且未被禁用）。
     * 内部调用 validateUserList，传入单元素集合。
     *
     * @param id 用户编号
     */
    default void validateUser(Long id) {
        validateUserList(Collections.singleton(id));
    }

    /**
     * 批量校验用户是否有效。
     * 有效条件：用户存在 + 状态启用（未被禁用）。
     *
     * @param ids 用户编号集合
     * @return 校验结果（true 表示全部有效）
     */
    @GetMapping(PREFIX + "/valid")
    @Operation(summary = "校验用户们是否有效")
    @Parameter(name = "ids", description = "用户编号数组", example = "3,5", required = true)
    CommonResult<Boolean> validateUserList(@RequestParam("ids") Collection<Long> ids);

    /**
     * AutoTransable 接口的默认实现，用于根据 ID 列表查询实体。
     * 被 @FeignIgnore 标记，表示该方法不参与 Feign 远程调用（因为是 default 方法，且仅本地使用）。
     *
     * 此方法支持某些通用组件（如 AutoTrans）通过 selectByIds 自动填充关联对象。
     *
     * @param ids 对象 ID 列表（可能是 String、Integer 等，需转换为 Long）
     * @return 用户列表
     */
    @Override
    @FeignIgnore
    default List<AdminUserRespDTO> selectByIds(List<?> ids) {
        return getUserList(Convert.toList(Long.class, ids)).getCheckedData();
    }

    /**
     * AutoTransable 接口的默认实现，用于根据单个 ID 查询实体。
     * 同样被 @FeignIgnore 忽略，不暴露为 Feign 接口。
     *
     * @param id 对象 ID（任意类型，将尝试转换为 Long）
     * @return 用户信息
     */
    @Override
    @FeignIgnore
    default AdminUserRespDTO selectById(Object id) {
        return getUser(Convert.toLong(id)).getCheckedData();
    }
}