package cn.iocoder.yudao.module.system.api.dept;

// 工具类导入
import cn.hutool.core.collection.CollUtil;        // Hutool 集合工具类，用于判断集合是否为空等
import cn.hutool.core.map.MapUtil;                // Hutool Map 工具类，用于创建空 Map 等
import cn.iocoder.yudao.framework.common.pojo.CommonResult; // 统一返回结果封装类（成功/失败 + 数据）
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils; // 自定义集合工具，支持 List 转 Map 等
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO; // 岗位信息的响应 DTO
import cn.iocoder.yudao.module.system.enums.ApiConstants;      // 系统模块的 Feign 客户端通用常量（如服务名、URL 前缀）
import io.swagger.v3.oas.annotations.Operation;    // OpenAPI 3.0：描述接口功能
import io.swagger.v3.oas.annotations.Parameter;    // 描述接口参数
import io.swagger.v3.oas.annotations.tags.Tag;     // 对整个接口打标签，用于分组
import org.springframework.cloud.openfeign.FeignClient; // Spring Cloud Feign 声明式 HTTP 客户端注解
import org.springframework.web.bind.annotation.GetMapping; // Spring MVC GET 请求映射
import org.springframework.web.bind.annotation.RequestParam; // 绑定 URL 查询参数

import java.util.Collection; // 泛型集合接口（如 List、Set）
import java.util.List;       // 列表接口
import java.util.Map;        // Map 接口

/**
 * 岗位（Post）模块的 Feign 远程调用接口。
 * 用于在微服务之间通过 HTTP 调用岗位相关数据（如校验、查询）。
 * 该接口由 system 模块提供实现，其他模块（如 biz、pay 等）可通过此接口消费岗位服务。
 */
@FeignClient(name = ApiConstants.NAME)
// 指定 Feign 客户端要调用的服务名称（通常为 yudao-system，定义在 ApiConstants）
// TODO 芋艿：建议补充 fallbackFactory = XxxFallbackFactory.class 实现降级逻辑，提升系统容错性

@Tag(name = "RPC 服务 - 岗位")
// Swagger 标签，便于在 OpenAPI 文档中归类为“RPC 服务 - 岗位”分组
public interface PostApi {

    /**
     * 接口请求的基础路径前缀，基于系统模块的统一前缀拼接岗位子路径。
     * 例如：若 ApiConstants.PREFIX = "/system-api"，则最终 PREFIX = "/system-api/post"
     */
    String PREFIX = ApiConstants.PREFIX + "/post";

    /**
     * 校验指定的岗位 ID 列表是否全部合法（即数据库中存在且状态有效）。
     *
     * @param ids 岗位 ID 集合，不能为空
     * @return CommonResult<Boolean> 返回校验结果：
     *         - true：所有岗位均合法
     *         - false：存在非法岗位
     *         - 异常情况（如网络失败）可通过 Feign 的 fallback 机制处理
     */
    @GetMapping(PREFIX + "/valid")
    @Operation(summary = "校验岗位是否合法") // Swagger 接口摘要
    @Parameter(name = "ids", description = "岗位编号数组", example = "1,2", required = true)
    CommonResult<Boolean> validPostList(@RequestParam("ids") Collection<Long> ids);

    /**
     * 根据岗位 ID 列表批量查询岗位信息。
     *
     * @param ids 岗位 ID 集合
     * @return CommonResult<List<PostRespDTO>> 返回岗位信息列表
     *         若某个 ID 不存在，通常不返回该记录（具体逻辑由服务端实现决定）
     */
    @GetMapping(PREFIX + "/list")
    @Operation(summary = "获得岗位列表")
    @Parameter(name = "ids", description = "岗位编号数组", example = "1,2", required = true)
    CommonResult<List<PostRespDTO>> getPostList(@RequestParam("ids") Collection<Long> ids);

    /**
     * 默认方法：将岗位 ID 集合转换为以 ID 为键、岗位信息为值的 Map。
     * 便于调用方通过 ID 快速查找岗位信息，避免重复遍历 List。
     *
     * 使用场景示例：
     *   Map<Long, PostRespDTO> map = postApi.getPostMap(Arrays.asList(1L, 2L));
     *   PostRespDTO post1 = map.get(1L);
     *
     * 实现逻辑：
     *   1. 若输入 ids 为空，则直接返回空 Map（避免远程调用）。
     *   2. 否则调用 getPostList 远程接口获取列表。
     *   3. 使用 CollectionUtils.convertMap 转换为 Map<Long, PostRespDTO>。
     *
     * 注意：该方法依赖 getPostList 的实现，若远程调用失败，会抛出 FeignException。
     */
    default Map<Long, PostRespDTO> getPostMap(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return MapUtil.empty(); // 返回空的不可变 Map（Hutool 提供）
        }

        // 调用远程接口获取岗位列表
        List<PostRespDTO> list = getPostList(ids).getData();

        // 使用工具类将 List 转为 Map，key 为 PostRespDTO.getId()
        return CollectionUtils.convertMap(list, PostRespDTO::getId);
    }

}