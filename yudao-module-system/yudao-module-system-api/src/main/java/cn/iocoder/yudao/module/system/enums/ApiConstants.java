package cn.iocoder.yudao.module.system.enums;

import cn.iocoder.yudao.framework.common.enums.RpcConstants;

/**
 * API 相关的常量定义类
 * <p>
 * 这个类用来集中管理与本模块（系统模块）对外提供的 API 接口相关的公共信息，
 * 例如服务名称、API 路径前缀、版本号等，便于统一维护和调用。
 *
 * @author 芋道源码
 */
public class ApiConstants {

    /**
     * 当前微服务的服务名称。
     * <p>
     * 这个值必须和配置文件（如 application.yml）中 spring.application.name 的值完全一致，
     * 因为在微服务架构中，服务之间通过这个名称进行相互识别和调用。
     */
    public static final String NAME = "system-server";

    /**
     * API 接口的统一路径前缀。
     * <p>
     * 它由两部分组成：
     * 1. RpcConstants.RPC_API_PREFIX：来自公共模块的 RPC API 基础路径（例如 "/api"）；
     * 2. "/system"：表示这是“系统模块”的专属路径。
     * 最终生成的完整前缀可能是 "/api/system"，所有本模块的接口都会以此开头。
     */
    public static final String PREFIX = RpcConstants.RPC_API_PREFIX + "/system";

    /**
     * API 的版本号。
     * <p>
     * 使用版本号可以方便后续对 API 进行升级或兼容旧版本，
     * 例如未来可能会有 "1.1.0" 或 "2.0.0"。
     * 当前版本为 "1.0.0"，表示初始稳定版本。
     */
    public static final String VERSION = "1.0.0";

}