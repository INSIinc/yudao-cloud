package cn.iocoder.yudao.framework.common.util.number;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;

import java.math.BigDecimal;
import java.util.List;

/**
 * 数字的工具类，用于补充 Hutool 的 {@link NumberUtil} 中缺少的功能。
 * 本类提供了一些常用但 Hutool 未覆盖的数字处理方法，适合项目中复用。
 *
 * @author 芋道源码
 */
public class NumberUtils {

    /**
     * 将字符串安全地转换为 Long 类型。
     * 如果字符串为空或 null，则返回 null，避免抛出异常。
     *
     * @param str 待转换的字符串（如 "123"）
     * @return 转换后的 Long 值，若字符串为空则返回 null
     */
    public static Long parseLong(String str) {
        return StrUtil.isNotEmpty(str) ? Long.valueOf(str) : null;
    }

    /**
     * 将字符串安全地转换为 Integer 类型。
     * 如果字符串为空或 null，则返回 null，避免抛出异常。
     *
     * @param str 待转换的字符串（如 "456"）
     * @return 转换后的 Integer 值，若字符串为空则返回 null
     */
    public static Integer parseInt(String str) {
        return StrUtil.isNotEmpty(str) ? Integer.valueOf(str) : null;
    }

    /**
     * 判断一个字符串列表中的所有元素是否都是合法的数字（包括整数、小数、负数等）。
     * 如果列表为空或包含任意非数字字符串，则返回 false。
     *
     * @param values 字符串列表，例如 ["123", "45.6", "-78"]
     * @return 如果所有元素都是数字，返回 true；否则返回 false
     */
    public static boolean isAllNumber(List<String> values) {
        if (CollUtil.isEmpty(values)) {
            return false; // 空列表视为不全为数字
        }
        for (String value : values) {
            if (!NumberUtil.isNumber(value)) {
                return false; // 只要有一个不是数字，就返回 false
            }
        }
        return true; // 全部是数字
    }

    /**
     * 根据两个地点的经纬度，计算它们之间的地球表面距离（单位：千米）。
     * 使用的是“球面余弦定理”的近似公式（Haversine 公式的等效形式），适用于中短距离计算。
     *
     * 注意：参数顺序容易混淆！
     * - lat1, lat2 表示 经度（longitude）
     * - lng1, lng2 表示 纬度（latitude）
     * 但通常地理坐标习惯是 (纬度, 经度)，此处方法签名与 Hutool 原版保持一致，需特别注意！
     *
     * @param lat1 第一个点的经度（例如：116.4074）
     * @param lng1 第一个点的纬度（例如：39.9042）
     * @param lat2 第二个点的经度
     * @param lng2 第二个点的纬度
     * @return 两点之间的距离，单位为 千米（km），保留小数点后4位后四舍五入
     */
    public static double getDistance(double lat1, double lng1, double lat2, double lng2) {
        // 将角度（度）转换为弧度，因为三角函数需要弧度作为输入
        double radLat1 = lat1 * Math.PI / 180.0;
        double radLat2 = lat2 * Math.PI / 180.0;
        // 计算纬度和经度的弧度差值
        double a = radLat1 - radLat2;                 // 纬度差
        double b = lng1 * Math.PI / 180.0 - lng2 * Math.PI / 180.0; // 经度差

        // Haversine 公式核心部分：
        // sin²(Δφ/2) + cos(φ1) * cos(φ2) * sin²(Δλ/2)
        double distance = 2 * Math.asin(Math.sqrt(
                Math.pow(Math.sin(a / 2), 2) +
                        Math.cos(radLat1) * Math.cos(radLat2) *
                                Math.pow(Math.sin(b / 2), 2)
        ));

        // 乘以地球半径（单位：千米），得到实际距离
        distance = distance * 6378.137; // 地球平均半径约为 6378.137 km

        // 保留小数点后4位（四舍五入）
        distance = Math.round(distance * 10000d) / 10000d;

        return distance;
    }

    /**
     * 提供安全的高精度乘法运算（使用 BigDecimal）。
     * 与 Hutool 的 {@link NumberUtil#mul(BigDecimal...)} 不同：
     * - 如果传入的任意一个参数为 null，则直接返回 null，避免 NPE。
     * - 否则，调用 Hutool 的 mul 方法进行精确乘法。
     *
     * 适用于金融、财务等需要精确计算且可能有 null 值的场景。
     *
     * @param values 多个 BigDecimal 类型的数值（如金额）
     * @return 所有数值的乘积；若任意参数为 null，则返回 null
     */
    public static BigDecimal mul(BigDecimal... values) {
        // 先检查是否有 null，有则提前返回 null
        for (BigDecimal value : values) {
            if (value == null) {
                return null;
            }
        }
        // 全部非 null，调用 Hutool 的精确乘法
        return NumberUtil.mul(values);
    }

}