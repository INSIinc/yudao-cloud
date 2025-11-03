package cn.iocoder.yudao.framework.common.pojo;

import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

/**
 * 通用响应结果封装类，用于统一接口返回格式。
 *
 * <p>该类遵循 RESTful 风格，包含三个核心字段：
 * <ul>
 *   <li>code：业务状态码（非 HTTP 状态码），成功时为 {@link GlobalErrorCodeConstants#SUCCESS} 的 code</li>
 *   <li>msg：人类可读的提示信息</li>
 *   <li>data：业务数据，泛型支持任意类型</li>
 * </ul>
 *
 * <p>设计目标：
 * <ul>
 *   <li>前后端约定统一的返回格式</li>
 *   <li>便于异常处理与数据校验</li>
 *   <li>支持与 {@link ServiceException} 异常体系无缝集成</li>
 * </ul>
 *
 * @param <T> 响应数据的泛型类型
 */
@Data
public class CommonResult<T> implements Serializable {

    /**
     * 业务状态码。
     * <p>参考 {@link ErrorCode#getCode()}。
     * 成功时应为 {@link GlobalErrorCodeConstants#SUCCESS#getCode()}（通常为 0）。
     */
    private Integer code;

    /**
     * 人类可读的错误/提示信息。
     * <p>成功时通常为空字符串，错误时为具体提示。
     * 参考 {@link ErrorCode#getMsg()}。
     */
    private String msg;

    /**
     * 业务返回的实际数据。
     * 成功时包含有效数据，失败时通常为 null。
     */
    private T data;

    // ==================== 静态工厂方法：构建错误结果 ====================

    /**
     * 将一个已有的 {@link CommonResult} 转换为新的泛型类型的错误结果。
     *
     * <p>典型场景：A 方法返回 {@code CommonResult<User>}，但 B 方法需要 {@code CommonResult<Order>}，
     * 此时若 A 调用失败，可将错误信息“透传”给 B 的返回类型。
     *
     * @param result 原始的 CommonResult（通常为错误状态）
     * @param <T>    目标泛型类型
     * @return 新的 CommonResult<T>，code 和 msg 与原 result 一致，data 为 null
     */
    public static <T> CommonResult<T> error(CommonResult<?> result) {
        return error(result.getCode(), result.getMsg());
    }

    /**
     * 使用指定的错误码和消息创建错误结果。
     *
     * @param code    错误码（不能是成功码）
     * @param message 错误提示
     * @param <T>     泛型类型
     * @return 错误结果对象
     * @throws IllegalArgumentException 如果 code 是成功码（防止误用）
     */
    public static <T> CommonResult<T> error(Integer code, String message) {
        // 断言：不能传入成功码，避免逻辑混乱
        Assert.notEquals(GlobalErrorCodeConstants.SUCCESS.getCode(), code, "code 必须是错误的！");
        CommonResult<T> result = new CommonResult<>();
        result.code = code;
        result.msg = message;
        return result;
    }

    /**
     * 使用 {@link ErrorCode} 枚举 + 动态参数创建错误结果。
     * 支持消息格式化（如：用户 {0} 不存在）。
     *
     * @param errorCode 错误码枚举
     * @param params    格式化参数，用于替换 msg 中的占位符 {0}, {1}...
     * @param <T>       泛型类型
     * @return 错误结果对象
     * @throws IllegalArgumentException 如果 errorCode 是成功码
     */
    public static <T> CommonResult<T> error(ErrorCode errorCode, Object... params) {
        Assert.notEquals(GlobalErrorCodeConstants.SUCCESS.getCode(), errorCode.getCode(), "code 必须是错误的！");
        CommonResult<T> result = new CommonResult<>();
        result.code = errorCode.getCode();
        result.msg = ServiceExceptionUtil.doFormat(errorCode.getCode(), errorCode.getMsg(), params);
        return result;
    }

    /**
     * 使用 {@link ErrorCode} 枚举创建错误结果（无参数格式化）。
     *
     * @param errorCode 错误码枚举
     * @param <T>       泛型类型
     * @return 错误结果对象
     */
    public static <T> CommonResult<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(), errorCode.getMsg());
    }

    /**
     * 使用已捕获的 {@link ServiceException} 创建错误结果。
     * 常用于异常捕获后转换为返回值。
     *
     * @param serviceException 业务异常对象
     * @param <T>              泛型类型
     * @return 错误结果对象
     */
    public static <T> CommonResult<T> error(ServiceException serviceException) {
        return error(serviceException.getCode(), serviceException.getMessage());
    }

    // ==================== 静态工厂方法：构建成功结果 ====================

    /**
     * 创建成功结果。
     *
     * @param data 业务数据（可为 null）
     * @param <T>  泛型类型
     * @return 成功结果对象，code 为 SUCCESS，msg 为空字符串
     */
    public static <T> CommonResult<T> success(T data) {
        CommonResult<T> result = new CommonResult<>();
        result.code = GlobalErrorCodeConstants.SUCCESS.getCode(); // 通常为 0
        result.data = data;
        result.msg = ""; // 成功时不返回提示信息
        return result;
    }

    // ==================== 工具方法 ====================

    /**
     * 判断给定的 code 是否表示成功。
     *
     * @param code 业务状态码
     * @return true 表示成功，false 表示失败
     */
    public static boolean isSuccess(Integer code) {
        return Objects.equals(code, GlobalErrorCodeConstants.SUCCESS.getCode());
    }

    /**
     * 判断当前结果是否成功。
     *
     * <p>使用 {@link JsonIgnore} 避免在 JSON 序列化时输出该字段（前端无需此布尔值）。
     *
     * @return true 表示成功
     */
    @JsonIgnore
    public boolean isSuccess() {
        return isSuccess(code);
    }

    /**
     * 判断当前结果是否为错误。
     *
     * <p>同样被 {@link JsonIgnore} 标记，避免序列化。
     *
     * @return true 表示错误
     */
    @JsonIgnore
    public boolean isError() {
        return !isSuccess();
    }

    // ==================== 与异常体系集成 ====================

    /**
     * 检查当前结果是否为错误。
     * 如果是错误，则抛出 {@link ServiceException} 异常。
     *
     * <p>典型用途：在调用远程服务或内部方法后，对返回的 CommonResult 进行校验。
     *
     * <pre>{@code
     * CommonResult<User> result = userService.getUser(id);
     * result.checkError(); // 若失败，直接抛出异常，中断流程
     * User user = result.getData();
     * }</pre>
     *
     * @throws ServiceException 如果当前结果为错误状态
     */
    public void checkError() throws ServiceException {
        if (isSuccess()) {
            return;
        }
        // 抛出业务异常，携带 code 和 msg
        throw new ServiceException(code, msg);
    }

    /**
     * 安全获取 data 数据。
     *
     * <p>先调用 {@link #checkError()}，若失败则抛异常；若成功则返回 data。
     *
     * <p>常用于链式调用或简化代码：
     * <pre>{@code
     * User user = userService.getUser(id).getCheckedData();
     * }</pre>
     *
     * @return data 字段（仅当成功时）
     * @throws ServiceException 如果当前结果为错误状态
     */
    @JsonIgnore // 避免被 JSON 序列化（这不是数据，而是工具方法）
    public T getCheckedData() {
        checkError();
        return data;
    }

}