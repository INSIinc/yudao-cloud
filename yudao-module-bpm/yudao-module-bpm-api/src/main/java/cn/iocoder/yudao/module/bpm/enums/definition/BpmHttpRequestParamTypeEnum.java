package cn.iocoder.yudao.module.bpm.enums.definition;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * BPM HTTP 请求参数设置类型枚举。
 * <p>
 * 该枚举用于 BPM（业务流程管理）模块中 Simple 流程设计器的“任务监听器”或“触发器”配置，
 * 用于指定 HTTP 请求中某个参数值的来源方式。
 * </p>
 * <p>
 * 通过该枚举，前端或后端可以明确知道一个请求参数是通过“固定值”直接写死，
 * 还是从流程实例的“表单数据”中动态获取。
 * </p>
 *
 * @author Lesan
 */
@Getter
@AllArgsConstructor
public enum BpmHttpRequestParamTypeEnum implements ArrayValuable<Integer> {

    /**
     * 固定值类型：表示该 HTTP 参数的值在配置时直接写死（静态值），
     * 不依赖流程运行时的上下文或表单数据。
     */
    FIXED_VALUE(1, "固定值"),

    /**
     * 表单类型：表示该 HTTP 参数的值来源于流程启动或任务完成时提交的表单字段，
     * 系统会在运行时从流程变量或表单数据中动态提取对应字段的值。
     */
    FROM_FORM(2, "表单");

    /**
     * 枚举项对应的数据库存储值（通常用于持久化）。
     * <p>
     * 使用 Integer 类型便于与数据库字段（如 TINYINT、INT 等）映射。
     */
    private final Integer type;

    /**
     * 枚举项的中文描述，用于前端展示或日志输出。
     */
    private final String name;

    /**
     * 静态常量数组，包含所有枚举项的 {@link #type} 值。
     * <p>
     * 用于校验输入值是否合法，例如在反序列化、参数校验或 MyBatis 查询中使用。
     * </p>
     */
    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(BpmHttpRequestParamTypeEnum::getType)
            .toArray(Integer[]::new);

    /**
     * 实现 {@link ArrayValuable} 接口的方法。
     * <p>
     * 返回该枚举所有合法的 type 值组成的数组，便于统一校验。
     * </p>
     *
     * @return 所有合法的 type 值数组（如 [1, 2]）
     */
    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    /**
     * （可选扩展）可根据 type 值反查枚举项的静态工具方法（当前未提供，但常被需要）。
     * 示例：
     * <pre>
     * public static BpmHttpRequestParamTypeEnum valueOfType(Integer type) {
     *     for (BpmHttpRequestParamTypeEnum value : values()) {
     *         if (value.getType().equals(type)) {
     *             return value;
     *         }
     *     }
     *     throw new IllegalArgumentException("Invalid type: " + type);
     * }
     * </pre>
     */
}