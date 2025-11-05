package cn.iocoder.yudao.gateway.jackson;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.json.databind.NumberSerializer;
import cn.iocoder.yudao.framework.common.util.json.databind.TimestampLocalDateTimeDeserializer;
import cn.iocoder.yudao.framework.common.util.json.databind.TimestampLocalDateTimeSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.web.codec.CodecCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 网关层 Jackson 自动配置类
 *
 * 作用说明：
 * 1. 统一网关的 JSON 序列化和反序列化规则
 * 2. 解决前端 JavaScript 处理长整型(Long)时的精度丢失问题
 * 3. 统一时间日期类型的格式（LocalDateTime 使用时间戳毫秒数）
 *
 * 适用场景：
 * - Spring Cloud Gateway（基于 WebFlux 的响应式网关）
 * - 需要统一处理上游服务返回的 JSON 数据格式
 */
@Configuration
@Slf4j
public class GatewayJacksonAutoConfiguration {

    /**
     * 配置 Jackson ObjectMapper 构建器的定制器
     *
     * 功能说明：
     * - 这个方法会在 Spring Boot 启动时自动执行
     * - 它会影响所有由 Spring 自动创建的 ObjectMapper 实例
     * - 确保整个应用使用统一的 JSON 序列化规则
     *
     * 为什么使用 serializerByType：
     * - 可以避免泛型擦除导致的问题
     * - 确保对指定的 Java 类型全局生效
     * - 比 handledType 方式更可靠
     *
     * @return Jackson 构建器定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer ldtEpochMillisCustomizer() {
        return builder -> builder
                // 配置 Long 类型序列化器
                // 作用：将 Long 类型转为字符串，避免 JavaScript 的精度丢失
                // 原因：JavaScript 的 Number 类型最大安全整数是 2^53-1，超过会丢失精度
                // 例如：Java 的 123456789012345678L 会被序列化为 "123456789012345678"
                .serializerByType(Long.class, NumberSerializer.INSTANCE)
                .serializerByType(Long.TYPE, NumberSerializer.INSTANCE)

                // 配置 LocalDate（日期）的序列化和反序列化
                // 格式：ISO 标准日期格式，例如 "2025-11-05"
                // 说明：只包含年月日，不包含时间
                .serializerByType(LocalDate.class, LocalDateSerializer.INSTANCE)
                .deserializerByType(LocalDate.class, LocalDateDeserializer.INSTANCE)

                // 配置 LocalTime（时间）的序列化和反序列化
                // 格式：ISO 标准时间格式，例如 "14:30:45"
                // 说明：只包含时分秒，不包含日期
                .serializerByType(LocalTime.class, LocalTimeSerializer.INSTANCE)
                .deserializerByType(LocalTime.class, LocalTimeDeserializer.INSTANCE)

                // 配置 LocalDateTime（日期时间）的序列化和反序列化
                // 序列化：将 LocalDateTime 转为时间戳（毫秒数）
                // 反序列化：将时间戳（毫秒数）转为 LocalDateTime
                // 例如：2025-11-05 14:30:45 会被序列化为 1730783445000
                // 优点：时间戳是数字，便于计算和比较，且不受时区影响
                .serializerByType(LocalDateTime.class, TimestampLocalDateTimeSerializer.INSTANCE)
                .deserializerByType(LocalDateTime.class, TimestampLocalDateTimeDeserializer.INSTANCE);
    }

    /**
     * 注册自定义的 Jackson 模块
     *
     * 功能说明：
     * - 将序列化规则封装成一个 Jackson Module（模块）
     * - Spring Boot 会自动发现并注册所有 Module 类型的 Bean
     * - 这是另一种方式来确保序列化规则全局生效
     *
     * 与上面方法的关系：
     * - 两个方法配置的规则是相同的
     * - 提供两种方式是为了增强兼容性
     * - 确保在各种场景下都能应用这些规则
     *
     * @return Jackson 自定义模块
     */
    @Bean
    public Module timestampSupportModuleBean() {
        // 创建一个简单的 Jackson 模块
        SimpleModule m = new SimpleModule("TimestampSupportModule");

        // 注册 Long 类型的序列化器（转为字符串）
        m.addSerializer(Long.class, NumberSerializer.INSTANCE);
        m.addSerializer(Long.TYPE, NumberSerializer.INSTANCE);

        // 注册 LocalDate 的序列化器和反序列化器（ISO 日期格式）
        m.addSerializer(LocalDate.class, LocalDateSerializer.INSTANCE);
        m.addDeserializer(LocalDate.class, LocalDateDeserializer.INSTANCE);

        // 注册 LocalTime 的序列化器和反序列化器（ISO 时间格式）
        m.addSerializer(LocalTime.class, LocalTimeSerializer.INSTANCE);
        m.addDeserializer(LocalTime.class, LocalTimeDeserializer.INSTANCE);

        // 注册 LocalDateTime 的序列化器和反序列化器（时间戳格式）
        m.addSerializer(LocalDateTime.class, TimestampLocalDateTimeSerializer.INSTANCE);
        m.addDeserializer(LocalDateTime.class, TimestampLocalDateTimeDeserializer.INSTANCE);

        return m;
    }

    /**
     * 初始化全局 JSON 工具类
     *
     * 功能说明：
     * - JsonUtils 是项目中的一个静态工具类，提供便捷的 JSON 转换方法
     * - 这里将 Spring 管理的 ObjectMapper 注入到 JsonUtils 中
     * - 确保通过 JsonUtils 进行的 JSON 操作也使用统一的序列化规则
     *
     * 使用场景：
     * - 在代码中调用 JsonUtils.toJsonString() 转换对象为 JSON
     * - 在代码中调用 JsonUtils.parseObject() 解析 JSON 为对象
     * - 这些操作都会使用这里配置的序列化规则
     *
     * @param objectMapper Spring 容器中配置好的 ObjectMapper 实例
     * @return JsonUtils 实例（用于触发初始化）
     */
    @Bean
    @SuppressWarnings("InstantiationOfUtilityClass")
    public JsonUtils jsonUtils(ObjectMapper objectMapper) {
        JsonUtils.init(objectMapper);
        log.debug("[init][初始化 JsonUtils 成功]");
        return new JsonUtils(); // 实例化仅用于触发初始化，实际使用时调用静态方法
    }

    /**
     * 配置 WebFlux 的 JSON 编解码器
     *
     * 功能说明：
     * - Spring Cloud Gateway 基于 WebFlux（响应式框架）
     * - WebFlux 使用 Codec（编解码器）来处理 HTTP 请求和响应的 JSON 数据
     * - 默认情况下，WebFlux 会创建自己的 ObjectMapper，可能与主配置不一致
     *
     * 为什么需要这个配置：
     * - 确保网关在转发请求和响应时使用统一的 JSON 序列化规则
     * - 避免上游服务返回的 Long 或时间字段被错误处理
     * - 保证从网关出去的数据格式符合前端的要求
     *
     * 实现方式：
     * - 使用 Spring 容器中统一配置的 ObjectMapper
     * - 替换 WebFlux 默认的 JSON 编码器（Encoder）和解码器（Decoder）
     *
     * @param om Spring 容器中已配置好的 ObjectMapper
     * @return 编解码器自定义器
     */
    @Bean
    public CodecCustomizer unifyJackson(ObjectMapper om) {
        return configurer -> {
            // 创建使用统一 ObjectMapper 的 JSON 解码器（用于解析请求体）
            Jackson2JsonDecoder decoder = new Jackson2JsonDecoder(om);
            // 创建使用统一 ObjectMapper 的 JSON 编码器（用于序列化响应体）
            Jackson2JsonEncoder encoder = new Jackson2JsonEncoder(om);
            // 设置为默认的 JSON 编解码器
            configurer.defaultCodecs().jackson2JsonDecoder(decoder);
            configurer.defaultCodecs().jackson2JsonEncoder(encoder);
        };
    }
}