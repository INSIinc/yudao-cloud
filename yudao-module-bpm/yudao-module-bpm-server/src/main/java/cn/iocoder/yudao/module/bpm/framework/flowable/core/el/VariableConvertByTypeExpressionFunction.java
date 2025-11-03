package cn.iocoder.yudao.module.bpm.framework.flowable.core.el;

import org.flowable.common.engine.api.variable.VariableContainer;
import org.flowable.common.engine.impl.el.function.AbstractFlowableVariableExpressionFunction;
import org.springframework.stereotype.Component;

/**
 * 自定义 EL 表达式函数组件，用于在 Flowable 流程引擎中根据流程变量的实际类型，
 * 动态转换传入的参数值（parmaValue），以保证在表达式求值时类型兼容。
 *
 * <p>
 * 该函数主要在流程条件表达式（如排他网关）中使用，例如：
 * <pre>
 * ${convertByType(execution, 'applyUserId', assigneeId) == execution.getVariable('applyUserId')}
 * </pre>
 * 其中 'applyUserId' 是流程变量名，assigneeId 是传入的参数值。
 * </p>
 *
 * <p>
 * 当前实现仅处理一种典型场景：
 * 如果流程变量（variable）在运行时实际是 {@link String} 类型，
 * 而传入的参数值（parmaValue）不是字符串，则自动将其调用 {@code toString()} 转为字符串。
 * 这样可以避免因 Java 对象类型与流程变量类型不一致导致的表达式比较失败。
 * </p>
 *
 * <p>
 * 该函数通过继承 {@link AbstractFlowableVariableExpressionFunction} 注册为名为 "convertByType" 的 EL 函数，
 * 并由 Spring 的 {@link Component} 注解自动注入到 Flowable 的表达式解析上下文中。
 * </p>
 *
 * @author jason
 */
@Component
public class VariableConvertByTypeExpressionFunction extends AbstractFlowableVariableExpressionFunction {

    /**
     * 构造函数：注册该表达式函数在 EL 中的名称为 "convertByType"。
     * 在 Flowable 的表达式（如条件表达式）中可通过 ${convertByType(...)} 调用。
     */
    public VariableConvertByTypeExpressionFunction() {
        super("convertByType");
    }

    /**
     * 核心转换逻辑：根据流程变量的实际类型，决定是否转换传入的参数值。
     *
     * @param variableContainer 包含当前流程上下文所有变量的容器（如 execution 或 task）。
     * @param variableName      要参考类型的目标流程变量名称（如 "applyUserId"）。
     * @param parmaValue        待转换的输入值（可能来自用户输入、上下文对象等）。
     * @return 如果需要类型对齐（例如变量是 String 而参数不是），则返回转换后的参数；
     *         否则原样返回 parmaValue。
     *
     * <p>
     * 当前策略：
     * - 若 variable（流程变量值）不为 null 且为 {@link String} 类型，
     * - 且 parmaValue 不为 null 且不是 {@link String} 类型，
     * - 则将 parmaValue 调用 {@link Object#toString()} 转为字符串。
     * </p>
     *
     * <p>
     * 注意：该方法为 static，便于在 EL 表达式或工具类中直接调用（Flowable 会通过反射调用）。
     * </p>
     */
    public static Object convertByType(VariableContainer variableContainer, String variableName, Object parmaValue) {
        // 从变量容器中获取目标流程变量的实际值（用于判断其运行时类型）
        Object variable = variableContainer.getVariable(variableName);

        // 仅在 variable 和 parmaValue 均非空时进行类型判断和转换
        if (variable != null && parmaValue != null) {
            // 场景：流程变量存储的是字符串（如表单提交的字符串ID），
            // 但传入的参数是 Long、Integer 等对象（如 Java 实体中的 ID 字段）。
            // 此时若直接比较（如 == 或 .equals），会因类型不一致导致条件判断失败。
            // 因此将非字符串参数转为字符串，以匹配流程变量的类型。
            if (!(parmaValue instanceof String) && variable instanceof String) {
                return parmaValue.toString();
            }
        }

        // 无需转换，直接返回原始值
        return parmaValue;
    }
}