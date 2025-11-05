package cn.iocoder.yudao.framework.mybatis.core.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.func.Func1;
import cn.hutool.core.lang.func.LambdaUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.SortingField;
import cn.iocoder.yudao.framework.mybatis.core.enums.DbTypeEnum;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * MyBatis 工具类：封装了 MyBatis-Plus 常用操作，如分页、排序、拦截器管理、SQL 字段处理等。
 */
public class MyBatisUtils {

    /**
     * MySQL 中用于转义表名或字段名的符号，例如：`user_name`
     */
    private static final String MYSQL_ESCAPE_CHARACTER = "`";

    /**
     * 根据分页参数（页码和每页大小）创建 MyBatis-Plus 的分页对象 Page。
     *
     * @param pageParam 分页参数对象（包含当前页码、每页数量）
     * @param <T>       实体类类型
     * @return MyBatis-Plus 的分页对象
     */
    public static <T> Page<T> buildPage(PageParam pageParam) {
        return buildPage(pageParam, null);
    }

    /**
     * 根据分页参数和排序字段列表创建分页对象，并设置排序规则。
     *
     * @param pageParam     分页参数（页码、每页数量）
     * @param sortingFields 排序字段列表（例如：按 createTime 升序）
     * @param <T>           实体类类型
     * @return 带排序规则的分页对象
     */
    public static <T> Page<T> buildPage(PageParam pageParam, Collection<SortingField> sortingFields) {
        // 创建分页对象：Page(当前页码, 每页条数)
        Page<T> page = new Page<>(pageParam.getPageNo(), pageParam.getPageSize());

        // 如果有排序字段，就逐个添加到分页对象中
        if (CollUtil.isNotEmpty(sortingFields)) {
            for (SortingField sortingField : sortingFields) {
                // 将驼峰字段名（如 createTime）转为下划线（create_time）
                String columnName = StrUtil.toUnderlineCase(sortingField.getField());
                boolean isAsc = SortingField.ORDER_ASC.equals(sortingField.getOrder()); // true 表示升序
                page.addOrder(new OrderItem().setAsc(isAsc).setColumn(columnName));
            }
        }
        return page;
    }

    /**
     * 为 MyBatis-Plus 的查询条件包装器（Wrapper）动态添加排序规则。
     * 支持两种包装器：
     * - QueryWrapper：直接支持字符串字段排序；
     * - LambdaQueryWrapper：不支持字符串排序，需用 SQL 拼接方式（last 方法）。
     *
     * @param wrapper        查询条件包装器（可能是 QueryWrapper 或 LambdaQueryWrapper）
     * @param sortingFields  排序字段列表
     * @param <T>            实体类类型
     */
    @SuppressWarnings("PatternVariableCanBeUsed")
    public static <T> void addOrder(Wrapper<T> wrapper, Collection<SortingField> sortingFields) {
        if (CollUtil.isEmpty(sortingFields)) {
            return; // 没有排序字段，直接返回
        }

        if (wrapper instanceof QueryWrapper<T>) {
            // QueryWrapper 支持直接传字符串字段名进行排序
            QueryWrapper<T> query = (QueryWrapper<T>) wrapper;
            for (SortingField sortingField : sortingFields) {
                String columnName = StrUtil.toUnderlineCase(sortingField.getField());
                boolean isAsc = SortingField.ORDER_ASC.equals(sortingField.getOrder());
                query.orderBy(true, isAsc, columnName); // true 表示启用该排序
            }
        } else if (wrapper instanceof LambdaQueryWrapper<T>) {
            // LambdaQueryWrapper 不支持字符串字段排序（因为它要求用 Lambda 表达式，如 User::getName）
            // 所以我们用 "last" 方法手动拼接 ORDER BY 子句
            LambdaQueryWrapper<T> lambdaQuery = (LambdaQueryWrapper<T>) wrapper;
            StringBuilder orderBy = new StringBuilder();
            for (SortingField sortingField : sortingFields) {
                if (orderBy.length() > 0) {
                    orderBy.append(", "); // 多个字段用逗号分隔
                }
                String columnName = StrUtil.toUnderlineCase(sortingField.getField());
                String order = SortingField.ORDER_ASC.equals(sortingField.getOrder()) ? "ASC" : "DESC";
                orderBy.append(columnName).append(" ").append(order);
            }
            // 使用 last() 方法在 SQL 末尾追加 "ORDER BY ..."
            lambdaQuery.last("ORDER BY " + orderBy);

            // ⚠️ 注意：这种做法绕过了 Lambda 安全检查，但能动态排序。
            // 更优雅的做法是把字段名转成 SFunction（见 CSDN 文章：根据字段名生成 SFunction），
            // 但实现较复杂，当前项目暂未采用。
        } else {
            throw new IllegalArgumentException("不支持的 Wrapper 类型: " + wrapper.getClass().getName());
        }
    }

    /**
     * 向 MyBatis-Plus 拦截器链中插入一个新的内部拦截器（InnerInterceptor）。
     * 由于 MyBatisPlusInterceptor 没有提供 addInterceptor 方法，
     * 所以需要手动取出已有拦截器列表，插入后再重新设置回去。
     *
     * @param interceptor 拦截器链（MybatisPlusInterceptor）
     * @param inner       要插入的内部拦截器
     * @param index       插入的位置（0 表示最前面）
     */
    public static void addInterceptor(MybatisPlusInterceptor interceptor, InnerInterceptor inner, int index) {
        List<InnerInterceptor> inners = new ArrayList<>(interceptor.getInterceptors());
        inners.add(index, inner);
        interceptor.setInterceptors(inners); // 重新设置整个列表
    }

    /**
     * 从 JSqlParser 的 Table 对象中提取真实的表名。
     * 有些数据库（如 MySQL）会用反引号转义表名，例如：`user`。
     * 本方法会去掉首尾的反引号，返回干净的表名。
     *
     * @param table JSqlParser 解析出的表对象
     * @return 真实表名（无转义符号）
     */
    public static String getTableName(Table table) {
        String tableName = table.getName();
        // 如果表名以 ` 开头并以 ` 结尾，说明被转义了，去掉它们
        if (tableName.startsWith(MYSQL_ESCAPE_CHARACTER) && tableName.endsWith(MYSQL_ESCAPE_CHARACTER)) {
            tableName = tableName.substring(1, tableName.length() - 1);
        }
        return tableName;
    }

    /**
     * 构造一个带有表名（或别名）前缀的字段表达式，用于 SQL 解析。
     * 例如：user.id 或 u.id（当表有别名 u 时）。
     *
     * @param tableName  原始表名
     * @param tableAlias 表的 SQL 别名（可能为 null）
     * @param column     字段名（如 "id"）
     * @return Column 对象，表示 "表名.字段名" 的结构
     */
    public static Column buildColumn(String tableName, Alias tableAlias, String column) {
        // 如果有别名，就用别名代替表名（SQL 中常用：SELECT u.name FROM user u）
        if (tableAlias != null) {
            tableName = tableAlias.getName();
        }
        // 拼接成 "表名.字段名"
        return new Column(tableName + StringPool.DOT + column);
    }

    /**
     * 跨数据库兼容的 FIND_IN_SET 函数实现。
     * FIND_IN_SET 是 MySQL 特有函数，用于判断某个值是否在逗号分隔的字符串中。
     * 其他数据库（如 PostgreSQL）需用不同语法模拟。
     * 本方法根据当前数据库类型，返回对应的 SQL 片段。
     *
     * @param column 字段名（如 "tags"）
     * @param value  要查找的值（如 "vip"）
     * @return 对应数据库的 FIND_IN_SET 等效 SQL 表达式
     */
    public static String findInSet(String column, Object value) {
        DbType dbType = JdbcUtils.getDbType(); // 获取当前数据库类型
        // 从 DbTypeEnum 中获取对应数据库的模板（如 MySQL 用 FIND_IN_SET(#{value}, #{column})）
        return DbTypeEnum.getFindInSetTemplate(dbType)
                .replace("#{column}", column)
                .replace("#{value}", StrUtil.toString(value));
    }

    /**
     * 从 Lambda 表达式中提取字段名，并转换为下划线命名格式。
     * 例如：传入 user -> user.getCreateTime()，会提取出 "createTime"，再转成 "create_time"。
     * 用途：在 SQL 别名或排序字段中使用下划线命名，避免与驼峰命名冲突。
     *
     * @param func Lambda 表达式（如 User::getCreateTime）
     * @param <T>  实体类类型
     * @return 下划线格式的字段名（如 "create_time"）
     */
    public static <T> String toUnderlineCase(Func1<T, ?> func) {
        // Hutool 的 LambdaUtil 可从 Lambda 表达式中解析出字段名（如 "createTime"）
        String fieldName = LambdaUtil.getFieldName(func);
        // 转换为下划线命名（如 "create_time"）
        return StrUtil.toUnderlineCase(fieldName);
    }

}