package cn.iocoder.yudao.framework.redis.core;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.time.Duration;

/**
 * 支持自定义过期时间的 {@link RedisCacheManager} 实现类
 *
 * 在 {@link Cacheable#cacheNames()} 格式为 "key#ttl" 时，# 后面的 ttl 为过期时间。
 * 单位为最后一个字母（支持的单位有：d 天，h 小时，m 分钟，s 秒），默认单位为 s 秒
 *
 * 使用示例：
 * @Cacheable(cacheNames = "user#30m") - 表示缓存名为 user，过期时间为 30 分钟
 * @Cacheable(cacheNames = "product#2h") - 表示缓存名为 product，过期时间为 2 小时
 * @Cacheable(cacheNames = "config#7d") - 表示缓存名为 config，过期时间为 7 天
 * @Cacheable(cacheNames = "token#3600") - 表示缓存名为 token，过期时间为 3600 秒
 *
 * @author 芋道源码
 */
public class TimeoutRedisCacheManager extends RedisCacheManager {

    // 定义分隔符常量，用于分隔缓存名称和过期时间，例如：user#30m 中的 #
    private static final String SPLIT = "#";

    /**
     * 构造方法
     *
     * @param cacheWriter Redis 缓存写入器，负责实际的 Redis 读写操作
     * @param defaultCacheConfiguration 默认的缓存配置，包含默认的过期时间等配置信息
     */
    public TimeoutRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfiguration) {
        // 调用父类构造方法，初始化 RedisCacheManager
        super(cacheWriter, defaultCacheConfiguration);
    }

    /**
     * 重写父类方法：创建 RedisCache 对象
     * 这个方法是核心方法，用于解析缓存名称中的过期时间，并创建对应的 RedisCache
     *
     * @param name 缓存名称，可能包含过期时间配置，格式如：user#30m
     * @param cacheConfig 缓存配置对象，包含序列化方式、过期时间等配置
     * @return 创建好的 RedisCache 对象
     */
    @Override
    protected RedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfig) {
        // 第一步：校验缓存名称是否为空
        // 如果缓存名称为空，直接调用父类方法，使用默认配置
        if (StrUtil.isEmpty(name)) {
            return super.createRedisCache(name, cacheConfig);
        }

        // 第二步：按照 # 分隔符拆分缓存名称
        // 例如："user#30m" 会被拆分成 ["user", "30m"]
        String[] names = StrUtil.splitToArray(name, SPLIT);

        // 第三步：判断是否需要自定义过期时间
        // 如果分隔后的数组长度不等于 2，说明没有使用 # 分隔符，或者格式不正确
        // 那么就不使用自定义过期时间，直接调用父类方法使用默认配置
        if (names.length != 2) {
            return super.createRedisCache(name, cacheConfig);
        }

        // 第四步：解析并设置自定义过期时间（核心逻辑）
        if (cacheConfig != null) {
            // 4.1 提取时间部分
            // 为了支持更复杂的缓存名称格式，比如 "user#30m:123"（后面可能还有冒号和其他内容）
            // 这里先获取冒号之前的部分，也就是纯时间部分 "30m"
            String ttlStr = StrUtil.subBefore(names[1], StrUtil.COLON, false); // 获得 ttlStr 时间部分

            // 4.2 从 names[1] 中移除时间部分，保留剩余部分（如果有的话）
            // 例如："30m:123" -> ":123"，如果没有冒号，则为空字符串
            names[1] = StrUtil.subAfter(names[1], ttlStr, false); // 移除掉 ttlStr 时间部分

            // 4.3 解析时间字符串，转换为 Duration 对象
            // 例如："30m" -> Duration.ofMinutes(30)
            Duration duration = parseDuration(ttlStr);

            // 4.4 设置缓存的过期时间
            // 通过 entryTtl 方法修改缓存配置中的过期时间
            cacheConfig = cacheConfig.entryTtl(duration);
        }

        // 第五步：创建并返回 RedisCache 对象
        // 注意：这里拼接的缓存名称已经移除了时间部分
        // 例如：原始 name = "user#30m"，最终创建时使用的是 "user" + "" = "user"
        // 或者：原始 name = "user#30m:123"，最终创建时使用的是 "user" + ":123" = "user:123"
        return super.createRedisCache(names[0] + names[1], cacheConfig);
    }

    /**
     * 解析过期时间字符串，转换为 Duration 对象
     *
     * 支持的格式：
     * - "30m" 表示 30 分钟
     * - "2h" 表示 2 小时
     * - "7d" 表示 7 天
     * - "60s" 表示 60 秒
     * - "3600" 纯数字表示秒数（默认单位）
     *
     * @param ttlStr 过期时间字符串，例如："30m"、"2h"、"7d"
     * @return 解析后的 Duration 对象，表示一段时间
     */
    private Duration parseDuration(String ttlStr) {
        // 获取字符串的最后一个字符，判断时间单位
        // 例如："30m" -> "m"
        String timeUnit = StrUtil.subSuf(ttlStr, -1);

        // 根据不同的时间单位，创建对应的 Duration 对象
        return switch (timeUnit) {
            case "d" -> // 天（day）
                // 移除单位字符，获取数字部分，然后创建以天为单位的 Duration
                // 例如："7d" -> 7 -> Duration.ofDays(7)
                    Duration.ofDays(removeDurationSuffix(ttlStr));
            case "h" -> // 小时（hour）
                // 例如："2h" -> 2 -> Duration.ofHours(2)
                    Duration.ofHours(removeDurationSuffix(ttlStr));
            case "m" -> // 分钟（minute）
                // 例如："30m" -> 30 -> Duration.ofMinutes(30)
                    Duration.ofMinutes(removeDurationSuffix(ttlStr));
            case "s" -> // 秒（second）
                // 例如："60s" -> 60 -> Duration.ofSeconds(60)
                    Duration.ofSeconds(removeDurationSuffix(ttlStr));
            default -> // 默认情况：没有单位字符，纯数字
                // 直接将整个字符串解析为秒数
                // 例如："3600" -> Duration.ofSeconds(3600)
                    Duration.ofSeconds(Long.parseLong(ttlStr));
        };
    }

    /**
     * 移除时间字符串的单位后缀，返回纯数字部分
     *
     * 例如：
     * - "30m" -> 30
     * - "2h" -> 2
     * - "7d" -> 7
     *
     * @param ttlStr 带单位的时间字符串，例如："30m"
     * @return 解析后的数字（Long 类型）
     */
    private Long removeDurationSuffix(String ttlStr) {
        // 截取字符串，从索引 0 到倒数第二个字符（不包含最后一个字符，也就是单位字符）
        // 然后将截取的字符串解析为 Long 类型的数字
        // 例如："30m" -> "30" -> 30L
        return NumberUtil.parseLong(StrUtil.sub(ttlStr, 0, ttlStr.length() - 1));
    }

}
