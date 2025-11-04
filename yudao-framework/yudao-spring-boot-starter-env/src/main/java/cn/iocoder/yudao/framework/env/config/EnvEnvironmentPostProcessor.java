package cn.iocoder.yudao.framework.env.config;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.collection.SetUtils;
import cn.iocoder.yudao.framework.env.core.util.EnvUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Set;

import static cn.iocoder.yudao.framework.env.core.util.EnvUtils.HOST_NAME_VALUE;

/**
 * 多环境的 {@link EnvEnvironmentPostProcessor} 实现类
 * 
 * <p>这个类的主要作用是在 Spring Boot 应用启动时，自动配置环境相关的属性。
 * 具体来说，它会将 yudao.env.tag 的值自动设置到 Nacos 等组件对应的 tag 配置项中，
 * 但只会在这些配置项不存在时才设置，避免覆盖已有的配置。
 * 
 * <p>使用场景举例：
 * 如果你配置了 yudao.env.tag=dev，这个类会自动将 spring.cloud.nacos.discovery.metadata.tag 
 * 也设置为 dev，这样服务注册到 Nacos 时就会带上 dev 标签，方便进行环境隔离。
 *
 * @author 芋道源码
 */
public class EnvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /**
     * 需要自动设置 tag 值的目标配置项集合
     * 
     * <p>这里定义了所有需要自动同步环境标签的配置项名称。
     * 目前包括：
     * - spring.cloud.nacos.discovery.metadata.tag: Nacos 服务注册时的元数据标签
     * 
     * <p>为什么要这样做？
     * 在微服务架构中，不同环境（开发、测试、生产）的服务需要相互隔离。
     * 通过给服务打上统一的 tag 标签，可以确保同一环境的服务之间才能互相调用。
     */
    private static final Set<String> TARGET_TAG_KEYS = SetUtils.asSet(
            "spring.cloud.nacos.discovery.metadata.tag" // Nacos 注册中心
            // MQ TODO
    );

    /**
     * 在 Spring Boot 环境准备好之后，应用启动之前执行的方法
     * 
     * <p>这个方法会在 Spring Boot 读取完所有配置文件后，但在创建 Bean 之前被调用。
     * 这是修改环境配置的最佳时机。
     * 
     * @param environment Spring 的环境配置对象，包含所有配置信息（application.yml 等）
     * @param application Spring Boot 应用对象
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // ========== 步骤 0：设置主机名环境变量（兜底处理）==========
        // 从 HOST_NAME_VALUE（例如 "${HOST_NAME}"）中提取变量名（HOST_NAME）
        String hostNameKey = StrUtil.subBetween(HOST_NAME_VALUE, "{", "}");
        
        // 如果系统环境变量中没有 HOST_NAME，则自动获取并设置
        // 这样可以确保后续使用 ${HOST_NAME} 占位符时不会出错
        if (!environment.containsProperty(hostNameKey)) {
            environment.getSystemProperties().put(hostNameKey, EnvUtils.getHostName());
        }

        // ========== 步骤 1：将环境标签自动同步到其他组件配置 ==========
        // 1.1 获取用户配置的环境标签（yudao.env.tag）
        // 如果用户没有配置环境标签，则不需要进行后续处理，直接返回
        String tag = EnvUtils.getTag(environment);
        if (StrUtil.isEmpty(tag)) {
            return; // 没有配置 tag，提前结束
        }
        
        // 1.2 遍历所有需要设置 tag 的目标配置项
        for (String targetTagKey : TARGET_TAG_KEYS) {
            // 检查目标配置项是否已经有值
            String targetTagValue = environment.getProperty(targetTagKey);
            if (StrUtil.isNotEmpty(targetTagValue)) {
                // 如果用户已经手动配置了这个值，则跳过，尊重用户的配置
                continue;
            }
            
            // 如果目标配置项没有值，则将 yudao.env.tag 的值设置进去
            // 这样就实现了环境标签的自动同步
            environment.getSystemProperties().put(targetTagKey, tag);
        }
    }

}
