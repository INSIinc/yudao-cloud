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
 * 网关层的 Jackson 自动配置类。
 * 目标：统一序列化/反序列化行为，特别是针对 Long 类型（防止前端精度丢失）和时间类型（统一使用时间戳格式）。
 * 该配置适用于 Spring WebFlux 环境（如 Spring Cloud Gateway）。
 */
@Configuration
@Slf4j
public class GatewayJacksonAutoConfiguration {

    /**
     * 通过 Jackson2ObjectMapperBuilderCustomizer 定制 ObjectMapper 的构建过程。
     * 此方式在 Spring Boot 启动时自动应用于所有由 Boot 创建的 ObjectMapper 实例。
     *
     * 使用 `serializerByType` 和 `deserializerByType`（而非 `serializerByToken` 或指定 handledType）
     * 可以避免因泛型擦除或子类问题导致的序列化器未生效，确保对指定类型全局生效。
     *
     * @return 自定义的 ObjectMapper 构建器定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer ldtEpochMillisCustomizer() {
        return builder -> builder
                // 将 Long 和 long 类型序列化为 JSON 数字（避免被转为字符串，但更重要的是：防止前端 JS 精度丢失问题）
                // 实际上，这里 NumberSerializer 的作用通常是将 Long 转为字符串（如 "1234567890123456789"），
                // 以避免 JavaScript Number 类型的最大安全整数限制（2^53 - 1）。请确认 NumberSerializer 的具体实现。
                .serializerByType(Long.class, NumberSerializer.INSTANCE)
                .serializerByType(Long.TYPE, NumberSerializer.INSTANCE)

                // 配置 LocalDate 的序列化与反序列化（默认使用 ISO 格式，如 "2025-11-03"）
                .serializerByType(LocalDate.class, LocalDateSerializer.INSTANCE)
                .deserializerByType(LocalDate.class, LocalDateDeserializer.INSTANCE)

                // 配置 LocalTime 的序列化与反序列化（默认使用 ISO 格式，如 "14:30:45"）
                .serializerByType(LocalTime.class, LocalTimeSerializer.INSTANCE)
                .deserializerByType(LocalTime.class, LocalTimeDeserializer.INSTANCE)

                // 配置 LocalDateTime 与时间戳（毫秒）的互转
                // 序列化：LocalDateTime -> 毫秒时间戳（Long）
                // 反序列化：毫秒时间戳（Long） -> LocalDateTime
                .serializerByType(LocalDateTime.class, TimestampLocalDateTimeSerializer.INSTANCE)
                .deserializerByType(LocalDateTime.class, TimestampLocalDateTimeDeserializer.INSTANCE);
    }

    /**
     * 以 Spring Bean 的形式注册一个自定义 Jackson Module。
     * Spring Boot 在创建 ObjectMapper 时会自动发现并注册所有类型为 Module 的 Bean，
     * 从而确保该 Module 中的序列化器/反序列化器被全局应用。
     *
     * 此方法与上方的 Customizer 效果类似，但提供了另一种注册方式，增强兼容性和显式性。
     * （某些场景下，如手动创建 ObjectMapper，可能依赖 Module 注册）
     *
     * @return 包含自定义序列化规则的 Jackson Module
     */
    @Bean
    public Module timestampSupportModuleBean() {
        SimpleModule m = new SimpleModule("TimestampSupportModule");

        // 注册 Long 类型的序列化器（同上）
        m.addSerializer(Long.class, NumberSerializer.INSTANCE);
        m.addSerializer(Long.TYPE, NumberSerializer.INSTANCE);

        // 注册 LocalDate 的序列化/反序列化器
        m.addSerializer(LocalDate.class, LocalDateSerializer.INSTANCE);
        m.addDeserializer(LocalDate.class, LocalDateDeserializer.INSTANCE);

        // 注册 LocalTime 的序列化/反序列化器
        m.addSerializer(LocalTime.class, LocalTimeSerializer.INSTANCE);
        m.addDeserializer(LocalTime.class, LocalTimeDeserializer.INSTANCE);

        // 注册 LocalDateTime 与时间戳互转的序列化/反序列化器
        m.addSerializer(LocalDateTime.class, TimestampLocalDateTimeSerializer.INSTANCE);
        m.addDeserializer(LocalDateTime.class, TimestampLocalDateTimeDeserializer.INSTANCE);

        return m;
    }

    /**
     * 初始化全局工具类 JsonUtils，使其内部使用的 ObjectMapper 与 Spring 容器中管理的 ObjectMapper 一致。
     * 这样可以确保通过 JsonUtils.toJSONString() 或 parseObject() 等方法时，使用的是统一的序列化规则。
     *
     * 注意：JsonUtils 通常是一个静态工具类，此处通过 init 方法注入 ObjectMapper 实例。
     *
     * @param objectMapper Spring 容器中配置好的 ObjectMapper 实例
     * @return JsonUtils 实例（虽然不被直接使用，但为了触发 init，且符合 Spring Bean 规范）
     */
    @Bean
    @SuppressWarnings("InstantiationOfUtilityClass")
    public JsonUtils jsonUtils(ObjectMapper objectMapper) {
        JsonUtils.init(objectMapper);
        log.debug("[init][初始化 JsonUtils 成功]");
        return new JsonUtils(); // 工具类实例仅用于触发初始化，实际使用静态方法
    }

    /**
     * 在 WebFlux 环境中（如 Spring Cloud Gateway），Reactor Netty 使用 Codec 进行 HTTP 请求/响应的编解码。
     * 默认情况下，Spring WebFlux 会创建自己的 ObjectMapper 实例用于 JSON 编解码，可能与主 ObjectMapper 不一致。
     *
     * 此 Customizer 强制 WebFlux 使用 Spring 容器中统一配置的 ObjectMapper，确保序列化行为一致。
     *
     * 具体做法：
     * - 创建新的 Jackson2JsonDecoder 和 Jackson2JsonEncoder，并传入统一的 ObjectMapper
     * - 替换 WebFlux 默认的 JSON 编解码器
     *
     * 这对网关尤其重要：避免上游服务返回的 Long 或时间字段在网关层被错误序列化。
     *
     * @param om Spring 容器中已配置好的 ObjectMapper
     * @return 编解码器自定义器
     */
    @Bean
    public CodecCustomizer unifyJackson(ObjectMapper om) {
        return configurer -> {
            Jackson2JsonDecoder decoder = new Jackson2JsonDecoder(om);
            Jackson2JsonEncoder encoder = new Jackson2JsonEncoder(om);
            configurer.defaultCodecs().jackson2JsonDecoder(decoder);
            configurer.defaultCodecs().jackson2JsonEncoder(encoder);
        };
    }
}