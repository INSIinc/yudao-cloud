package cn.iocoder.yudao.framework.redis.config;

import cn.hutool.core.util.ReflectUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Redis 配置类
 *
 * 该类为 Redis 提供了自动化配置。
 */
@AutoConfiguration(before = RedissonAutoConfigurationV2.class) // 确保这个自动化配置在 Redisson 之前加载，以便我们自定义的 RedisTemplate Bean 生效。
public class YudaoRedisAutoConfiguration {

    /**
     * 创建 RedisTemplate Bean，用于操作 Redis。
     * 我们通过自定义 RedisTemplate，设置了 Key 和 Value 的序列化方式。
     *
     * @param factory Redis 连接工厂，由 Spring Boot 自动配置。
     * @return RedisTemplate 实例。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        // 1. 创建 RedisTemplate 对象
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        // 2. 设置连接工厂
        // RedisConnectionFactory 负责创建和管理 Redis 连接。
        template.setConnectionFactory(factory);
        // 3. 设置 Key 的序列化方式
        // 我们使用 StringRedisSerializer，将 Key 序列化为字符串，方便在 Redis Desktop Manager 等工具中查看。
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());
        // 4. 设置 Value 的序列化方式
        // 我们使用 Jackson2JsonRedisSerializer，将 Value 序列化为 JSON 格式。
        // 这种方式具有良好的可读性，并且可以序列化复杂的 Java 对象。
        template.setValueSerializer(buildRedisSerializer());
        template.setHashValueSerializer(buildRedisSerializer());
        return template;
    }

    /**
     * 构建 Redis 的 JSON 序列化器。
     *
     * @return RedisSerializer 实例。
     */
    public static RedisSerializer<?> buildRedisSerializer() {
        // 使用 Spring Boot 默认的 JSON 序列化器
        RedisSerializer<Object> json = RedisSerializer.json();
        // 解决 Jackson2JsonRedisSerializer 序列化 LocalDateTime 的问题
        // ObjectMapper 是 Jackson 库的核心，用于 JSON 和 Java 对象之间的转换。
        ObjectMapper objectMapper = (ObjectMapper) ReflectUtil.getFieldValue(json, "mapper");
        // 注册 JavaTimeModule 模块，以便正确序列化和反序列化 Java 8 的日期和时间 API（如 LocalDateTime）。
        objectMapper.registerModules(new JavaTimeModule());
        return json;
    }

}
