package cn.iocoder.yudao.framework.common.enums;

/**
 * RPC（远程过程调用）相关的公共常量定义
 *
 * 虽然这些常量放在 yudao-spring-boot-starter-rpc 模块中更合适，
 * 但由于项目中的各个 API 模块都会频繁使用，为了方便，暂时统一放在这里。
 *
 * @author 芋道源码
 */
public interface RpcConstants {

    /**
     * 所有 RPC 接口的统一路径前缀。
     * 例如：/rpc-api/system/user/get 表示 system 服务中用户获取接口。
     */
    String RPC_API_PREFIX = "/rpc-api";

    /**
     * 系统（system）微服务的名称。
     * 注意：这个值必须和该服务的 application.yml（或 properties）中配置的 spring.application.name 完全一致，
     * 否则服务之间无法正确识别和调用。
     */
    String SYSTEM_NAME = "system-server";

    /**
     * system 服务下所有 RPC 接口的路径前缀。
     * 由基础前缀 + 服务标识组成，例如：/rpc-api/system
     */
    String SYSTEM_PREFIX = RPC_API_PREFIX + "/system";

    /**
     * 基础设施（infra）微服务的名称。
     * 同样，必须与该服务配置文件中的 spring.application.name 保持一致。
     */
    String INFRA_NAME = "infra-server";

    /**
     * infra 服务下所有 RPC 接口的路径前缀。
     * 例如：/rpc-api/infra/file/upload 表示 infra 服务中的文件上传接口。
     */
    String INFRA_PREFIX = RPC_API_PREFIX + "/infra";

}