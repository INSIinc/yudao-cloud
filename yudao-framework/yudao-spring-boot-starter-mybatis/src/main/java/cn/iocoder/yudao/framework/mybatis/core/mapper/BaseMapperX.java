package cn.iocoder.yudao.framework.mybatis.core.mapper;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.SortablePageParam;
import cn.iocoder.yudao.framework.common.pojo.SortingField;
import cn.iocoder.yudao.framework.mybatis.core.util.JdbcUtils;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.github.yulichang.base.MPJBaseMapper;
import com.github.yulichang.interfaces.MPJBaseJoin;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 在 MyBatis Plus 的 {@link BaseMapper} 基础上进一步扩展，提供更便捷、更强大的通用 CRUD 和分页查询能力。
 *
 * <p>主要功能包括：
 * <ul>
 *   <li>集成 MyBatis-Plus 的基础 CRUD（继承自 {@link BaseMapper}）</li>
 *   <li>集成 MyBatis-Plus-Join（MPJ）的连表查询能力（继承自 {@link MPJBaseMapper}）</li>
 *   <li>提供统一的分页查询入口，支持排序字段、不分页查询等场景</li>
 *   <li>简化单条件/多条件查询、计数、批量插入/更新/删除等高频操作</li>
 * </ul>
 *
 * @param <T> 实体类型
 */
public interface BaseMapperX<T> extends MPJBaseMapper<T> {

    // =================== 分页查询（普通单表） ===================

    /**
     * 分页查询（带排序字段）
     *
     * @param pageParam     分页参数，包含当前页码、每页大小
     * @param queryWrapper  查询条件包装器（支持 Lambda 或普通 QueryWrapper）
     * @return 分页结果 {@link PageResult}
     */
    default PageResult<T> selectPage(SortablePageParam pageParam, @Param("ew") Wrapper<T> queryWrapper) {
        return selectPage(pageParam, pageParam.getSortingFields(), queryWrapper);
    }

    /**
     * 分页查询（无排序字段）
     *
     * @param pageParam     分页参数
     * @param queryWrapper  查询条件包装器
     * @return 分页结果
     */
    default PageResult<T> selectPage(PageParam pageParam, @Param("ew") Wrapper<T> queryWrapper) {
        return selectPage(pageParam, null, queryWrapper);
    }

    /**
     * 通用分页查询方法（支持排序字段和不分页场景）
     *
     * @param pageParam      分页参数（若 pageSize == -1 表示不分页，查全部）
     * @param sortingFields  排序字段集合（可为 null）
     * @param queryWrapper   查询条件包装器
     * @return 分页结果或全量结果
     */
    default PageResult<T> selectPage(PageParam pageParam, Collection<SortingField> sortingFields, @Param("ew") Wrapper<T> queryWrapper) {
        // 特殊处理：pageSize = -1（PAGE_SIZE_NONE）表示不分页，查全部数据
        if (PageParam.PAGE_SIZE_NONE.equals(pageParam.getPageSize())) {
            MyBatisUtils.addOrder(queryWrapper, sortingFields); // 添加排序
            List<T> list = selectList(queryWrapper);
            return new PageResult<>(list, (long) list.size()); // 总数 = 列表长度
        }

        // 正常分页：构建 MyBatis-Plus 的 IPage 对象
        IPage<T> mpPage = MyBatisUtils.buildPage(pageParam, sortingFields);
        selectPage(mpPage, queryWrapper); // 执行分页查询
        return new PageResult<>(mpPage.getRecords(), mpPage.getTotal());
    }

    // =================== 分页查询（连表 Join） ===================

    /**
     * 连表分页查询（使用 MPJLambdaWrapper，支持 Lambda 表达式构建 JOIN）
     *
     * @param pageParam     分页参数
     * @param clazz         返回 DTO 的类型（非实体类）
     * @param lambdaWrapper MPJ 的 Lambda 查询包装器，用于构建 JOIN 逻辑
     * @param <D>           返回的 DTO 类型
     * @return 分页结果
     */
    default <D> PageResult<D> selectJoinPage(PageParam pageParam, Class<D> clazz, MPJLambdaWrapper<T> lambdaWrapper) {
        if (PageParam.PAGE_SIZE_NONE.equals(pageParam.getPageSize())) {
            // 不分页：直接查全部
            List<D> list = selectJoinList(clazz, lambdaWrapper);
            return new PageResult<>(list, (long) list.size());
        }

        // 正常分页
        IPage<D> mpPage = MyBatisUtils.buildPage(pageParam);
        mpPage = selectJoinPage(mpPage, clazz, lambdaWrapper);
        return new PageResult<>(mpPage.getRecords(), mpPage.getTotal());
    }

    /**
     * 连表分页查询（使用 MPJBaseJoin 接口，更底层的 JOIN 构建方式）
     *
     * @param pageParam         分页参数
     * @param resultTypeClass   返回结果的 DTO 类型
     * @param joinQueryWrapper  MPJ 的 JOIN 查询包装器
     * @param <DTO>             返回 DTO 类型
     * @return 分页结果
     */
    default <DTO> PageResult<DTO> selectJoinPage(PageParam pageParam, Class<DTO> resultTypeClass, MPJBaseJoin<T> joinQueryWrapper) {
        IPage<DTO> mpPage = MyBatisUtils.buildPage(pageParam);
        selectJoinPage(mpPage, resultTypeClass, joinQueryWrapper);
        return new PageResult<>(mpPage.getRecords(), mpPage.getTotal());
    }

    // =================== 单条查询（selectOne） ===================

    /**
     * 根据单个字段值查询唯一记录（使用字段名 + 值）
     *
     * @param field 字段名（数据库字段，非 Java 属性名）
     * @param value 字段值
     * @return 唯一实体，若不存在或存在多个则抛异常（MyBatis-Plus 行为）
     */
    default T selectOne(String field, Object value) {
        return selectOne(new QueryWrapper<T>().eq(field, value));
    }

    /**
     * 根据单个字段值查询唯一记录（使用 Lambda 表达式，类型安全）
     */
    default T selectOne(SFunction<T, ?> field, Object value) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field, value));
    }

    /**
     * 根据两个字段值查询唯一记录（字段名方式）
     */
    default T selectOne(String field1, Object value1, String field2, Object value2) {
        return selectOne(new QueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    /**
     * 根据两个字段值查询唯一记录（Lambda 表达式）
     */
    default T selectOne(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    /**
     * 根据三个字段值查询唯一记录（Lambda 表达式）
     */
    default T selectOne(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2,
                        SFunction<T, ?> field3, Object value3) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2).eq(field3, value3));
    }

    // =================== 安全的单条查询（selectFirstOne） ===================

    /**
     * 获取满足条件的第一条记录（不报错，适用于并发插入后查多条的场景）
     *
     * <p>与 {@link #selectOne} 不同：即使查询到多条记录也不会抛异常，而是返回第一条。
     * 适用于“理论上唯一但可能因并发导致多条”的场景（如根据业务ID查配置）。
     *
     * @param field 字段（Lambda 表达式）
     * @param value 值
     * @return 第一条记录，若无则返回 null
     */
    default T selectFirstOne(SFunction<T, ?> field, Object value) {
        List<T> list = selectList(new LambdaQueryWrapper<T>().eq(field, value));
        return CollUtil.getFirst(list);
    }

    /**
     * 两个字段条件下的安全单条查询（取第一条）
     */
    default T selectFirstOne(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2) {
        List<T> list = selectList(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
        return CollUtil.getFirst(list);
    }

    /**
     * 三个字段条件下的安全单条查询（取第一条）
     */
    default T selectFirstOne(SFunction<T,?> field1, Object value1, SFunction<T,?> field2, Object value2,
                             SFunction<T,?> field3, Object value3) {
        List<T> list = selectList(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2).eq(field3, value3));
        return CollUtil.getFirst(list);
    }

    // =================== 计数查询 ===================

    /**
     * 统计总记录数（无条件）
     */
    default Long selectCount() {
        return selectCount(new QueryWrapper<>());
    }

    /**
     * 根据字段值统计记录数（字符串字段名方式）
     */
    default Long selectCount(String field, Object value) {
        return selectCount(new QueryWrapper<T>().eq(field, value));
    }

    /**
     * 根据字段值统计记录数（Lambda 表达式）
     */
    default Long selectCount(SFunction<T, ?> field, Object value) {
        return selectCount(new LambdaQueryWrapper<T>().eq(field, value));
    }

    // =================== 列表查询 ===================

    /**
     * 查询所有记录
     */
    default List<T> selectList() {
        return selectList(new QueryWrapper<>());
    }

    /**
     * 根据单个字段值查询列表（字段名）
     */
    default List<T> selectList(String field, Object value) {
        return selectList(new QueryWrapper<T>().eq(field, value));
    }

    /**
     * 根据单个字段值查询列表（Lambda）
     */
    default List<T> selectList(SFunction<T, ?> field, Object value) {
        return selectList(new LambdaQueryWrapper<T>().eq(field, value));
    }

    /**
     * 根据字段和值集合查询（IN 查询，字段名方式）
     *
     * @param field  字段名
     * @param values 值集合（若为空，返回空列表）
     */
    default List<T> selectList(String field, Collection<?> values) {
        if (CollUtil.isEmpty(values)) {
            return CollUtil.newArrayList();
        }
        return selectList(new QueryWrapper<T>().in(field, values));
    }

    /**
     * 根据字段和值集合查询（IN 查询，Lambda 方式）
     */
    default List<T> selectList(SFunction<T, ?> field, Collection<?> values) {
        if (CollUtil.isEmpty(values)) {
            return CollUtil.newArrayList();
        }
        return selectList(new LambdaQueryWrapper<T>().in(field, values));
    }

    /**
     * 根据两个字段值查询列表（Lambda）
     */
    default List<T> selectList(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2) {
        return selectList(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    // =================== 批量插入 ===================

    /**
     * 批量插入实体（自动适配数据库类型）
     *
     * <p>注意事项：
     * <ul>
     *   <li>对于 SQL Server，由于批量插入后无法正确返回自增 ID，故降级为循环单条插入</li>
     *   <li>其他数据库（如 MySQL、PostgreSQL）使用 MyBatis-Plus 的 saveBatch，性能更高</li>
     * </ul>
     *
     * @param entities 实体集合
     * @return 是否成功（非空集合即为 true）
     */
    default Boolean insertBatch(Collection<T> entities) {
        DbType dbType = JdbcUtils.getDbType();
        if (JdbcUtils.isSQLServer(dbType)) {
            entities.forEach(this::insert);
            return CollUtil.isNotEmpty(entities);
        }
        return Db.saveBatch(entities);
    }

    /**
     * 批量插入（指定每批大小）
     *
     * @param entities 实体集合
     * @param size     每批插入数量（默认 1000）
     */
    default Boolean insertBatch(Collection<T> entities, int size) {
        DbType dbType = JdbcUtils.getDbType();
        if (JdbcUtils.isSQLServer(dbType)) {
            entities.forEach(this::insert);
            return CollUtil.isNotEmpty(entities);
        }
        return Db.saveBatch(entities, size);
    }

    // =================== 批量更新 ===================

    /**
     * 全表更新（将 update 对象中非空字段更新到所有记录）
     *
     * @param update 更新用的实体对象（非空字段生效）
     * @return 受影响行数
     */
    default int updateBatch(T update) {
        return update(update, new QueryWrapper<>());
    }

    /**
     * 根据 ID 批量更新（实体必须包含有效 ID）
     *
     * @param entities 实体集合
     * @return 是否成功
     */
    default Boolean updateBatch(Collection<T> entities) {
        return Db.updateBatchById(entities);
    }

    /**
     * 根据 ID 批量更新（指定每批大小）
     */
    default Boolean updateBatch(Collection<T> entities, int size) {
        return Db.updateBatchById(entities, size);
    }

    // =================== 删除 ===================

    /**
     * 根据字段值删除（字段名方式）
     */
    default int delete(String field, String value) {
        return delete(new QueryWrapper<T>().eq(field, value));
    }

    /**
     * 根据字段值删除（Lambda 方式）
     */
    default int delete(SFunction<T, ?> field, Object value) {
        return delete(new LambdaQueryWrapper<T>().eq(field, value));
    }

    /**
     * 根据字段和值集合批量删除（IN 删除）
     *
     * @param field  字段（Lambda）
     * @param values 值集合（若为空，不执行删除）
     * @return 删除行数
     */
    default int deleteBatch(SFunction<T, ?> field, Collection<?> values) {
        if (CollUtil.isEmpty(values)) {
            return 0;
        }
        return delete(new LambdaQueryWrapper<T>().in(field, values));
    }
}