package cn.iocoder.yudao.module.system.framework.rpc.config;

import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.websocket.WebSocketSenderApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * RPC 配置类
 *
 * 通过 spring.cloud.openfeign.enabled 属性控制是否启用
 * 单体模式下设置为 false，微服务模式下设置为 true
 */
@Configuration(value = "systemRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {FileApi.class, WebSocketSenderApi.class, ConfigApi.class})
@ConditionalOnProperty(prefix = "spring.cloud.openfeign", name = "enabled", havingValue = "true", matchIfMissing = false)
public class RpcConfiguration {
}
