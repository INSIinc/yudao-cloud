package cn.iocoder.yudao.framework.redis.config;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.redis.core.TimeoutRedisCacheManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.util.StringUtils;

import java.util.Objects;

import static cn.iocoder.yudao.framework.redis.config.YudaoRedisAutoConfiguration.buildRedisSerializer;

/**
 * 基于 Redis 的 Spring Cache 缓存自动配置类。
 *
 * Spring Cache 是 Spring 提供的统一缓存抽象，本类将它底层实现切换为 Redis，
 * 并做了以下优化：
 * 1. 缓存 Key 使用单冒号 ":" 作为分隔符（避免 Redis 可视化工具显示多余空节点）
 * 2. 缓存值使用 JSON 序列化（方便跨语言、可读性强）
 * 3. 支持自定义过期时间、是否缓存 null 值等
 */
@AutoConfiguration // Spring Boot 自动配置类
@EnableConfigurationProperties({CacheProperties.class, YudaoCacheProperties.class}) // 启用配置属性绑定
@EnableCaching // 开启 Spring 缓存注解支持（如 @Cacheable）
public class YudaoCacheAutoConfiguration {

    /**
     * 创建 Redis 缓存的核心配置对象 RedisCacheConfiguration。
     *
     * 这个对象决定了：
     * - 缓存 Key 的前缀格式
     * - 缓存 Value 的序列化方式
     * - 是否缓存 null
     * - 默认过期时间等
     *
     * @param cacheProperties Spring Boot 自带的缓存配置（application.yml 中的 spring.cache.redis 配置）
     * @return 配置好的 RedisCacheConfiguration 实例
     */
    @Bean
    @Primary // 当存在多个 RedisCacheConfiguration 时，优先使用这个
    public RedisCacheConfiguration redisCacheConfiguration(CacheProperties cacheProperties) {
        // 1. 从默认配置开始
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig();

        // 2. 【关键修复】将缓存 Key 前缀从默认的 "cacheName::" 改为 "cacheName:"
        // 原因：Spring Data Redis 默认使用双冒号 "::"，会导致 Redis Desktop Manager 等工具显示一个空节点（体验差）
        // 参考：https://blog.csdn.net/chuixue24/article/details/103928965
        // 项目 Issues：https://gitee.com/zhijiantianya/yudao-cloud/issues/I86VY2
        config = config.computePrefixWith(cacheName -> {
            // 先读取用户是否在配置文件中自定义了 key 前缀（如 spring.cache.redis.key-prefix = myapp）
            String keyPrefix = cacheProperties.getRedis().getKeyPrefix();
            if (StringUtils.hasText(keyPrefix)) {
                // 确保前缀以冒号结尾（避免写成 "myappuser" 而不是 "myapp:user"）
                if (keyPrefix.lastIndexOf(StrUtil.COLON) == -1) {
                    keyPrefix += StrUtil.COLON; // 补冒号
                }
                // 最终 Key 格式：myapp:cacheName:
                return keyPrefix + cacheName + StrUtil.COLON;
            }
            // 未设置前缀时，Key 格式：cacheName:
            return cacheName + StrUtil.COLON;
        });

        // 3. 设置缓存值（Value）使用 JSON 序列化（而不是默认的 JDK 二进制序列化）
        // 优点：Redis 中可直接看到 JSON 内容，且兼容其他语言
        config = config.serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(buildRedisSerializer())
        );

        // 4. 应用 Spring Boot 配置文件中的其他 Redis 缓存设置
        CacheProperties.Redis redisProperties = cacheProperties.getRedis();

        // 设置缓存默认过期时间（TTL）
        if (redisProperties.getTimeToLive() != null) {
            config = config.entryTtl(redisProperties.getTimeToLive());
        }

        // 是否缓存 null 值？默认 true，这里按配置决定
        if (!redisProperties.isCacheNullValues()) {
            config = config.disableCachingNullValues(); // 不缓存 null
        }

        // 是否使用缓存名作为 Key 前缀？默认 true，关闭则 Key 不带前缀
        if (!redisProperties.isUseKeyPrefix()) {
            config = config.disableKeyPrefix();
        }

        return config;
    }

    /**
     * 创建 Redis 缓存管理器（RedisCacheManager）。
     *
     * 它负责：
     * - 管理所有缓存实例（如 userCache、orderCache）
     * - 使用 RedisTemplate 执行实际的 set/get 操作
     * - 支持批量扫描（用于清理缓存）
     *
     * @param redisTemplate          Redis 操作模板（已配置好连接和序列化）
     * @param redisCacheConfiguration 上面定义的缓存配置
     * @param yudaoCacheProperties    自定义的缓存扩展配置（如批量扫描大小）
     * @return 支持超时控制的 Redis 缓存管理器
     */
    @Bean
    public RedisCacheManager redisCacheManager(RedisTemplate<String, Object> redisTemplate,
                                               RedisCacheConfiguration redisCacheConfiguration,
                                               YudaoCacheProperties yudaoCacheProperties) {
        // 1. 获取 Redis 连接工厂（从 redisTemplate 中提取）
        RedisConnectionFactory connectionFactory = Objects.requireNonNull(redisTemplate.getConnectionFactory());

        // 2. 创建 Redis 缓存写入器（支持批量操作）
        // scan 批量大小来自自定义配置 yudao.cache.redis-scan-batch-size
        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(
                connectionFactory,
                BatchStrategies.scan(yudaoCacheProperties.getRedisScanBatchSize())
        );

        // 3. 使用自定义的 TimeoutRedisCacheManager（可能支持动态过期等扩展功能）
        return new TimeoutRedisCacheManager(cacheWriter, redisCacheConfiguration);
    }

}