package cn.iocoder.yudao.framework.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cache 配置属性类
 *
 * 这个类用于映射 application.yml 或 application.properties 文件中以 "yudao.cache" 为前缀的配置项。
 * 例如，你可以在配置文件中设置 yudao.cache.redis-scan-batch-size=50。
 *
 * @author Wanwan
 */
@ConfigurationProperties("yudao.cache") // 指定配置文件中的前缀
@Data // Lombok 注解，自动生成 getter、setter、toString 等方法
@Validated // 开启对这个类中属性的校验
public class YudaoCacheProperties {

    /**
     * {@link #redisScanBatchSize} 的默认值
     *
     * 当配置文件中没有指定 yudao.cache.redis-scan-batch-size 时，会使用这个默认值。
     */
    private static final Integer REDIS_SCAN_BATCH_SIZE_DEFAULT = 30;

    /**
     * Redis SCAN 命令一次扫描的数量
     *
     * SCAN 命令用于迭代 Redis 中的键。这个配置项决定了每次迭代返回的元素数量的建议值。
     * 这个值设得太小会增加迭代次数和网络开销，设得太大会阻塞 Redis 服务器。
     *
     * 默认值是 {@link #REDIS_SCAN_BATCH_SIZE_DEFAULT}
     */
    private Integer redisScanBatchSize = REDIS_SCAN_BATCH_SIZE_DEFAULT;

}
