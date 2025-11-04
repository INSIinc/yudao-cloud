package cn.iocoder.yudao.framework.common.util.object;

import cn.hutool.core.bean.BeanUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;

import java.util.List;
import java.util.function.Consumer;

/**
 * Bean 工具类 —— 用于简化 Java 对象之间的属性复制和类型转换
 *
 * 说明：
 * 1. 默认使用 Hutool 的 {@link cn.hutool.core.bean.BeanUtil} 来实现对象转换。
 *    虽然不同工具（如 MapStruct、Dozer）在性能上有差异，但对绝大多数普通项目来说，这点性能差异可以忽略。
 * 2. 如果你的对象转换逻辑非常复杂（比如字段名不同、需要特殊处理等），
 *    建议使用 MapStruct 配合接口中的 default 方法实现（可参考项目中的 AuthConvert 类）。
 *
 * @author 芋道源码
 */
public class BeanUtils {

    /**
     * 将一个对象转换为指定类型的对象（浅拷贝，属性名需一致）
     *
     * 例如：UserDTO userDTO = BeanUtils.toBean(userEntity, UserDTO.class);
     *
     * @param source 源对象（要转换的对象）
     * @param targetClass 目标类型（你想转成什么类）
     * @param <T> 目标类型泛型
     * @return 转换后的新对象
     */
    public static <T> T toBean(Object source, Class<T> targetClass) {
        return BeanUtil.toBean(source, targetClass);
    }

    /**
     * 将一个对象转换为目标类型，并在转换后对新对象进行额外操作（比如设置额外字段）
     *
     * 例如：转换后想自动设置当前时间，可以用 peek 参数处理
     *
     * @param source 源对象
     * @param targetClass 目标类型
     * @param peek 转换完成后对目标对象进行的操作（Consumer 接收一个参数，无返回值）
     * @param <T> 目标类型泛型
     * @return 转换后的对象（可能已被 peek 修改）
     */
    public static <T> T toBean(Object source, Class<T> targetClass, Consumer<T> peek) {
        T target = toBean(source, targetClass); // 先转换
        if (target != null) {
            peek.accept(target); // 如果转换成功，执行额外操作
        }
        return target;
    }

    /**
     * 将一个对象列表中的每个元素都转换为目标类型的新对象列表
     *
     * 例如：List<UserDTO> = BeanUtils.toBean(userEntityList, UserDTO.class);
     *
     * @param source 源对象列表（如 List<UserEntity>）
     * @param targetType 目标元素的类型（如 UserDTO.class）
     * @param <S> 源列表元素类型
     * @param <T> 目标列表元素类型
     * @return 转换后的新列表（元素类型为 T）
     */
    public static <S, T> List<T> toBean(List<S> source, Class<T> targetType) {
        if (source == null) {
            return null; // 源列表为 null，直接返回 null
        }
        // 使用 CollectionUtils 遍历列表，并对每个元素调用 toBean 转换
        return CollectionUtils.convertList(source, s -> toBean(s, targetType));
    }

    /**
     * 将对象列表转换为目标类型列表，并对每个转换后的对象执行额外操作
     *
     * @param source 源列表
     * @param targetType 目标元素类型
     * @param peek 对每个转换后的对象执行的操作
     * @param <S> 源类型
     * @param <T> 目标类型
     * @return 转换并处理后的列表
     */
    public static <S, T> List<T> toBean(List<S> source, Class<T> targetType, Consumer<T> peek) {
        List<T> list = toBean(source, targetType); // 先转换列表
        if (list != null) {
            list.forEach(peek); // 对每个元素执行 peek 操作
        }
        return list;
    }

    /**
     * 将分页结果中的数据列表转换为目标类型（保留总条数不变）
     *
     * 适用于：你从数据库查到了 PageResult<UserEntity>，但想返回 PageResult<UserDTO>
     *
     * @param source 源分页结果（如 PageResult<UserEntity>）
     * @param targetType 目标数据类型（如 UserDTO.class）
     * @param <S> 源数据类型
     * @param <T> 目标数据类型
     * @return 新的分页结果（数据已转换，总条数不变）
     */
    public static <S, T> PageResult<T> toBean(PageResult<S> source, Class<T> targetType) {
        return toBean(source, targetType, null); // 调用带 peek 的重载方法，peek 为 null
    }

    /**
     * 将分页结果转换为目标类型，并对每条数据执行额外操作
     *
     * @param source 源分页结果
     * @param targetType 目标数据类型
     * @param peek 对每条转换后数据的操作
     * @param <S> 源数据类型
     * @param <T> 目标数据类型
     * @return 转换并处理后的分页结果
     */
    public static <S, T> PageResult<T> toBean(PageResult<S> source, Class<T> targetType, Consumer<T> peek) {
        if (source == null) {
            return null; // 源分页结果为 null，直接返回 null
        }
        // 先转换数据列表
        List<T> list = toBean(source.getList(), targetType);
        // 如果提供了 peek，就对每个元素处理
        if (peek != null) {
            list.forEach(peek);
        }
        // 构造新的 PageResult，总条数沿用原来的
        return new PageResult<>(list, source.getTotal());
    }

    /**
     * 将源对象的属性值复制到目标对象中（属性名相同的字段会被覆盖）
     *
     * 注意：这是“拷贝”而不是“转换”——不会创建新对象，而是修改已存在的 target 对象
     *
     * 例如：BeanUtils.copyProperties(userEntity, userDTO);
     *       这会把 userEntity 中同名字段的值复制到 userDTO 中
     *
     * @param source 源对象（提供数据）
     * @param target 目标对象（接收数据）
     */
    public static void copyProperties(Object source, Object target) {
        if (source == null || target == null) {
            return; // 任一为 null 就不操作
        }
        // 使用 Hutool 的 copyProperties，false 表示：忽略 null 值（即源对象为 null 的字段不覆盖目标）
        BeanUtil.copyProperties(source, target, false);
    }

}