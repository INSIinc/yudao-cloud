// 声明当前类所在的包路径，遵循 Java 的包命名规范
package cn.iocoder.yudao.module.bpm.convert.message;

// 导入将被转换的目标类：SmsSendSingleToUserReqDTO，这是一个用于发送短信请求的数据传输对象（DTO）
import cn.iocoder.yudao.module.system.api.sms.dto.send.SmsSendSingleToUserReqDTO;

// 导入 MapStruct 的核心注解 @Mapper，用于标记这是一个 MapStruct 映射器接口
import org.mapstruct.Mapper;

// 导入 @Mapping 注解，用于指定字段之间的映射规则（比如忽略某个字段、自定义源字段到目标字段的映射等）
import org.mapstruct.Mapping;

// 导入 Mappers 工具类，用于在运行时获取 MapStruct 自动生成的映射器实现类的实例
import org.mapstruct.factory.Mappers;

// 导入 Java 标准库中的 Map，用于表示短信模板参数（键值对形式）
import java.util.Map;

/**
 * BpmMessageConvert 是一个使用 MapStruct 实现的对象转换接口。
 * 它的作用是将流程引擎（BPM）中的消息相关数据（如用户ID、短信模板编码、模板参数）
 * 转换为系统模块中用于发送短信的请求对象 SmsSendSingleToUserReqDTO。
 *
 * MapStruct 是一个代码生成器，它在编译期自动生成类型安全、高性能的 bean 映射代码，
 * 避免手动编写繁琐的 setter/getter 赋值逻辑。
 */
@Mapper // 告诉 MapStruct：这是一个映射器接口，需要为它生成实现类
public interface BpmMessageConvert {

    /**
     * 通过 MapStruct 提供的工厂方法 Mappers.getMapper() 获取该接口的单例实现。
     * 编译后，MapStruct 会生成一个名为 BpmMessageConvertImpl 的类，
     * 并通过此行代码提供一个全局可访问的静态实例。
     */
    BpmMessageConvert INSTANCE = Mappers.getMapper(BpmMessageConvert.class);

    /**
     * 定义一个映射方法：将三个输入参数（userId, templateCode, templateParams）
     * 转换为一个 SmsSendSingleToUserReqDTO 对象。
     *
     * 注意：MapStruct 支持将多个参数组合映射到一个目标对象，但需要明确指定每个字段的来源。
     *
     * @param userId 用户ID，对应目标对象中的 userId 字段
     * @param templateCode 短信模板编码，对应目标对象中的 templateCode 字段
     * @param templateParams 模板参数（如 {"code": "123456"}），对应目标对象中的 templateParams 字段
     * @return 转换后的 SmsSendSingleToUserReqDTO 实例
     */
    @Mapping(target = "mobile", ignore = true)
    // 忽略目标对象中的 "mobile" 字段 —— 即不赋值（可能由系统根据 userId 自动查询手机号，无需传入）

    @Mapping(source = "userId", target = "userId")
    // 将方法参数 userId 映射到目标对象的 userId 字段（虽然名字相同，显式写出更清晰）

    @Mapping(source = "templateCode", target = "templateCode")
    // 将方法参数 templateCode 映射到目标对象的 templateCode 字段

    @Mapping(source = "templateParams", target = "templateParams")
    // 将方法参数 templateParams 映射到目标对象的 templateParams 字段

    // 方法签名：接收三个参数，返回一个 SmsSendSingleToUserReqDTO 对象
    SmsSendSingleToUserReqDTO convert(Long userId, String templateCode, Map<String, Object> templateParams);
}