package cn.iocoder.yudao.framework.mybatis.core.query;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.yudao.framework.common.util.collection.ArrayUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.springframework.util.StringUtils;

import java.util.Collection;

/**
 * 扩展 MyBatis-Plus 的 LambdaQueryWrapper 类，提供“仅当参数有值时才添加查询条件”的便捷方法。
 * <p>
 * 主要用途：避免在参数为空/无效时错误地拼接 SQL 条件，从而简化业务代码中的 if 判断。
 * <p>
 * 使用示例：
 *   new LambdaQueryWrapperX<User>()
 *       .eqIfPresent(User::getId, userId)      // 如果 userId 不为 null，才添加 id = ? 条件
 *       .likeIfPresent(User::getName, name);   // 如果 name 非空非空白，才添加 name LIKE '%...%' 条件
 *
 * @param <T> 实体类类型（例如 User、Order 等）
 */
public class LambdaQueryWrapperX<T> extends LambdaQueryWrapper<T> {

    /**
     * 模糊查询（LIKE），仅当 val 非空且非空白字符串时才添加条件。
     * 例如：name LIKE '%张三%'
     *
     * @param column 实体类的字段（如 User::getName）
     * @param val    要匹配的字符串值
     * @return 当前查询包装器，支持链式调用
     */
    public LambdaQueryWrapperX<T> likeIfPresent(SFunction<T, ?> column, String val) {
        if (StringUtils.hasText(val)) { // hasText 表示：非 null 且非空（至少包含一个非空白字符）
            return (LambdaQueryWrapperX<T>) super.like(column, val);
        }
        return this; // 无值则跳过，不影响后续条件
    }

    /**
     * IN 查询，仅当 values 集合非空且所有元素都不为 null 时才添加条件。
     * 例如：id IN (1, 2, 3)
     *
     * @param column 实体类的字段（如 User::getId）
     * @param values 要匹配的值集合
     * @return 当前查询包装器
     */
    public LambdaQueryWrapperX<T> inIfPresent(SFunction<T, ?> column, Collection<?> values) {
        if (ObjectUtil.isAllNotEmpty(values) && !ArrayUtil.isEmpty(values)) {
            return (LambdaQueryWrapperX<T>) super.in(column, values);
        }
        return this;
    }

    /**
     * IN 查询（支持可变参数），仅当 values 数组非空且所有元素都不为 null 时才添加条件。
     * 例如：status IN (1, 2)
     *
     * @param column 实体类的字段
     * @param values 要匹配的值（多个）
     * @return 当前查询包装器
     */
    public LambdaQueryWrapperX<T> inIfPresent(SFunction<T, ?> column, Object... values) {
        if (ObjectUtil.isAllNotEmpty(values) && !ArrayUtil.isEmpty(values)) {
            return (LambdaQueryWrapperX<T>) super.in(column, values);
        }
        return this;
    }

    /**
     * 等于（=）查询，仅当 val 不为 null 且不为“空对象”（如空字符串、空集合等）时才添加条件。
     * 例如：age = 25
     *
     * @param column 实体类的字段
     * @param val    要比较的值
     * @return 当前查询包装器
     */
    public LambdaQueryWrapperX<T> eqIfPresent(SFunction<T, ?> column, Object val) {
        if (ObjectUtil.isNotEmpty(val)) {
            return (LambdaQueryWrapperX<T>) super.eq(column, val);
        }
        return this;
    }

    /**
     * 不等于（!=）查询，仅当 val 有效时才添加条件。
     * 例如：status != 0
     *
     * @param column 实体类的字段
     * @param val    要比较的值
     * @return 当前查询包装器
     */
    public LambdaQueryWrapperX<T> neIfPresent(SFunction<T, ?> column, Object val) {
        if (ObjectUtil.isNotEmpty(val)) {
            return (LambdaQueryWrapperX<T>) super.ne(column, val);
        }
        return this;
    }

    // ========== 以下为数值/日期等可比较类型的条件 ==========
    // 注意：这些方法仅检查 val 是否为 null，因为 0、false、空字符串等在业务中可能是有效值

    /**
     * 大于（>）查询，仅当 val 不为 null 时才添加条件。
     * 例如：create_time > '2025-01-01'
     *
     * @param column 字段
     * @param val    比较值
     * @return 当前查询包装器
     */
    public LambdaQueryWrapperX<T> gtIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaQueryWrapperX<T>) super.gt(column, val);
        }
        return this;
    }

    /**
     * 大于等于（>=）查询，仅当 val 不为 null 时才添加。
     */
    public LambdaQueryWrapperX<T> geIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaQueryWrapperX<T>) super.ge(column, val);
        }
        return this;
    }

    /**
     * 小于（<）查询，仅当 val 不为 null 时才添加。
     */
    public LambdaQueryWrapperX<T> ltIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaQueryWrapperX<T>) super.lt(column, val);
        }
        return this;
    }

    /**
     * 小于等于（<=）查询，仅当 val 不为 null 时才添加。
     */
    public LambdaQueryWrapperX<T> leIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaQueryWrapperX<T>) super.le(column, val);
        }
        return this;
    }

    /**
     * BETWEEN 查询（闭区间），根据 val1 和 val2 是否为 null 分别处理：
     * - 两个都不为 null：添加 BETWEEN 条件
     * - 只有 val1：添加 >= val1
     * - 只有 val2：添加 <= val2
     * - 都为 null：不添加任何条件
     *
     * @param column 字段
     * @param val1   起始值（包含）
     * @param val2   结束值（包含）
     * @return 当前查询包装器
     */
    public LambdaQueryWrapperX<T> betweenIfPresent(SFunction<T, ?> column, Object val1, Object val2) {
        if (val1 != null && val2 != null) {
            return (LambdaQueryWrapperX<T>) super.between(column, val1, val2);
        }
        if (val1 != null) {
            return (LambdaQueryWrapperX<T>) ge(column, val1); // 注意：这里调用的是本类的 ge，保持链式
        }
        if (val2 != null) {
            return (LambdaQueryWrapperX<T>) le(column, val2);
        }
        return this;
    }

    /**
     * BETWEEN 查询（通过数组传参），数组第0个是起始值，第1个是结束值。
     * 内部会调用上面的 betweenIfPresent(column, val1, val2)。
     *
     * @param column  字段
     * @param values  长度为2的数组：[开始值, 结束值]
     * @return 当前查询包装器
     */
    public LambdaQueryWrapperX<T> betweenIfPresent(SFunction<T, ?> column, Object[] values) {
        Object val1 = ArrayUtils.get(values, 0); // 安全获取数组第0项（若越界则返回 null）
        Object val2 = ArrayUtils.get(values, 1); // 安全获取数组第1项
        return betweenIfPresent(column, val1, val2);
    }

    // ========== 重写父类方法，确保返回类型是 LambdaQueryWrapperX 而非 LambdaQueryWrapper ==========
    // 这样可以保证链式调用时仍然使用我们扩展后的方法

    @Override
    public LambdaQueryWrapperX<T> eq(boolean condition, SFunction<T, ?> column, Object val) {
        super.eq(condition, column, val);
        return this;
    }

    @Override
    public LambdaQueryWrapperX<T> eq(SFunction<T, ?> column, Object val) {
        super.eq(column, val);
        return this;
    }

    @Override
    public LambdaQueryWrapperX<T> orderByDesc(SFunction<T, ?> column) {
        super.orderByDesc(true, column);
        return this;
    }

    @Override
    public LambdaQueryWrapperX<T> last(String lastSql) {
        super.last(lastSql);
        return this;
    }

    @Override
    public LambdaQueryWrapperX<T> in(SFunction<T, ?> column, Collection<?> coll) {
        super.in(column, coll);
        return this;
    }

}