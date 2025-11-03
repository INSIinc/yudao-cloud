package cn.iocoder.yudao.framework.common.util.collection;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.google.common.collect.ImmutableMap;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cn.hutool.core.convert.Convert.toCollection;
import static java.util.Arrays.asList;

/**
 * Collection 工具类
 * <p>
 * 提供对集合（List、Set、Map 等）的增强操作，封装了常见但重复性高的集合处理逻辑，
 * 如转换、过滤、去重、分组、比较差异等，提升代码可读性与复用性。
 *
 * @author 芋道源码
 */
public class CollectionUtils {

    /**
     * 判断目标数组中是否包含指定元素（等价于 source 是否在 targets 中）
     *
     * @param source  待检查的元素
     * @param targets 用于比对的元素数组
     * @return 若 source 在 targets 中存在，返回 true，否则 false
     */
    public static boolean containsAny(Object source, Object... targets) {
        return asList(targets).contains(source);
    }

    /**
     * 判断多个集合中是否有任意一个是空的（null 或 size=0）
     *
     * @param collections 待检查的多个集合
     * @return 若任意一个为空，返回 true；否则 false
     */
    public static boolean isAnyEmpty(Collection<?>... collections) {
        return Arrays.stream(collections).anyMatch(CollectionUtil::isEmpty);
    }

    /**
     * 判断集合中是否存在满足指定条件的元素
     *
     * @param from       源集合
     * @param predicate  判断条件
     * @return 若存在任意元素满足条件，返回 true
     */
    public static <T> boolean anyMatch(Collection<T> from, Predicate<T> predicate) {
        return from.stream().anyMatch(predicate);
    }

    /**
     * 对集合进行过滤，返回满足条件的元素构成的新列表
     *
     * @param from       源集合
     * @param predicate  过滤条件
     * @return 过滤后的新列表（不为 null）
     */
    public static <T> List<T> filterList(Collection<T> from, Predicate<T> predicate) {
        if (CollUtil.isEmpty(from)) {
            return new ArrayList<>();
        }
        return from.stream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * 对集合根据指定 key 进行去重（保留第一个出现的元素）
     *
     * @param from       源集合
     * @param keyMapper  用于提取唯一键的函数
     * @param <T>        元素类型
     * @param <R>        唯一键类型
     * @return 去重后的新列表
     */
    public static <T, R> List<T> distinct(Collection<T> from, Function<T, R> keyMapper) {
        return distinct(from, keyMapper, (t1, t2) -> t1);
    }

    /**
     * 对集合根据指定 key 进行去重，并通过 merge 函数决定保留哪个元素
     *
     * @param from       源集合
     * @param keyMapper  提取唯一键的函数
     * @param cover      当 key 冲突时，用于决定保留哪个元素的合并策略（通常保留第一个）
     * @return 去重后的列表
     */
    public static <T, R> List<T> distinct(Collection<T> from, Function<T, R> keyMapper, BinaryOperator<T> cover) {
        if (CollUtil.isEmpty(from)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(convertMap(from, keyMapper, Function.identity(), cover).values());
    }

    /**
     * 将数组转换为列表，并应用转换函数（自动过滤 null 结果）
     *
     * @param from  源数组
     * @param func  转换函数
     * @param <T>   源元素类型
     * @param <U>   目标元素类型
     * @return 转换后的非空元素列表
     */
    public static <T, U> List<U> convertList(T[] from, Function<T, U> func) {
        if (ArrayUtil.isEmpty(from)) {
            return new ArrayList<>();
        }
        return convertList(Arrays.asList(from), func);
    }

    /**
     * 将集合转换为新类型的列表（自动过滤 null 结果）
     *
     * @param from  源集合
     * @param func  转换函数（T → U）
     * @return 转换后的新列表（不含 null）
     */
    public static <T, U> List<U> convertList(Collection<T> from, Function<T, U> func) {
        if (CollUtil.isEmpty(from)) {
            return new ArrayList<>();
        }
        return from.stream().map(func).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 先过滤再转换集合，返回非 null 的新列表
     *
     * @param from   源集合
     * @param func   转换函数
     * @param filter 过滤条件
     * @return 过滤并转换后的非 null 列表
     */
    public static <T, U> List<U> convertList(Collection<T> from, Function<T, U> func, Predicate<T> filter) {
        if (CollUtil.isEmpty(from)) {
            return new ArrayList<>();
        }
        return from.stream().filter(filter).map(func).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 转换分页结果中的数据列表（保留分页总数）
     *
     * @param from  源分页结果
     * @param func  转换函数
     * @param <T>   源数据类型
     * @param <U>   目标数据类型
     * @return 转换后的分页结果
     */
    public static <T, U> PageResult<U> convertPage(PageResult<T> from, Function<T, U> func) {
        if (ArrayUtil.isEmpty(from)) { // 注意：此处应为 CollUtil.isEmpty(from.getList())
            return new PageResult<>(from.getTotal());
        }
        return new PageResult<>(convertList(from.getList(), func), from.getTotal());
    }

    /**
     * 使用 flatMap 转换集合（适用于一对多场景）
     *
     * @param from  源集合
     * @param func  转换函数，返回 Stream（如 List 转 Stream）
     * @param <T>   源元素
     * @param <U>   目标元素
     * @return 合并后的非 null 列表
     */
    public static <T, U> List<U> convertListByFlatMap(Collection<T> from,
                                                      Function<T, ? extends Stream<? extends U>> func) {
        if (CollUtil.isEmpty(from)) {
            return new ArrayList<>();
        }
        return from.stream().filter(Objects::nonNull).flatMap(func).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 先映射再 flatMap 转换（两步转换）
     *
     * @param from   源集合
     * @param mapper 中间映射函数（T → U）
     * @param func   最终 flatMap 函数（U → Stream<R>）
     * @param <T>    源类型
     * @param <U>    中间类型
     * @param <R>    目标类型
     * @return 转换后的列表
     */
    public static <T, U, R> List<R> convertListByFlatMap(Collection<T> from,
                                                         Function<? super T, ? extends U> mapper,
                                                         Function<U, ? extends Stream<? extends R>> func) {
        if (CollUtil.isEmpty(from)) {
            return new ArrayList<>();
        }
        return from.stream().map(mapper).filter(Objects::nonNull).flatMap(func).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 合并 Map 中所有 List 的值为一个扁平列表
     *
     * @param map key → List<V> 的映射
     * @param <K> key 类型
     * @param <V> value 类型
     * @return 所有 value 列表合并后的扁平列表
     */
    public static <K, V> List<V> mergeValuesFromMap(Map<K, List<V>> map) {
        return map.values()
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * 将集合转换为 Set（默认使用自身作为元素）
     */
    public static <T> Set<T> convertSet(Collection<T> from) {
        return convertSet(from, v -> v);
    }

    /**
     * 将集合转换为 Set（应用转换函数，过滤 null）
     */
    public static <T, U> Set<U> convertSet(Collection<T> from, Function<T, U> func) {
        if (CollUtil.isEmpty(from)) {
            return new HashSet<>();
        }
        return from.stream().map(func).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 先过滤再转换为 Set
     */
    public static <T, U> Set<U> convertSet(Collection<T> from, Function<T, U> func, Predicate<T> filter) {
        if (CollUtil.isEmpty(from)) {
            return new HashSet<>();
        }
        return from.stream().filter(filter).map(func).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 先过滤再转为 Map（key 由 keyFunc 决定，value 为元素本身）
     */
    public static <T, K> Map<K, T> convertMapByFilter(Collection<T> from, Predicate<T> filter, Function<T, K> keyFunc) {
        if (CollUtil.isEmpty(from)) {
            return new HashMap<>();
        }
        return from.stream().filter(filter).collect(Collectors.toMap(keyFunc, v -> v));
    }

    /**
     * 使用 flatMap 转换为 Set（一对多转集合）
     */
    public static <T, U> Set<U> convertSetByFlatMap(Collection<T> from,
                                                    Function<T, ? extends Stream<? extends U>> func) {
        if (CollUtil.isEmpty(from)) {
            return new HashSet<>();
        }
        return from.stream().filter(Objects::nonNull).flatMap(func).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 两步 flatMap 转 Set
     */
    public static <T, U, R> Set<R> convertSetByFlatMap(Collection<T> from,
                                                       Function<? super T, ? extends U> mapper,
                                                       Function<U, ? extends Stream<? extends R>> func) {
        if (CollUtil.isEmpty(from)) {
            return new HashSet<>();
        }
        return from.stream().map(mapper).filter(Objects::nonNull).flatMap(func).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 将集合转换为 Map（key 由 keyFunc 决定，value 为元素本身）
     */
    public static <T, K> Map<K, T> convertMap(Collection<T> from, Function<T, K> keyFunc) {
        return convertMap(from, keyFunc, Function.identity());
    }

    /**
     * 指定 Map 实现类型（如 LinkedHashMap）进行转换
     */
    public static <T, K> Map<K, T> convertMap(Collection<T> from, Function<T, K> keyFunc, Supplier<? extends Map<K, T>> supplier) {
        return convertMap(from, keyFunc, Function.identity(), supplier);
    }

    /**
     * 通用 Map 转换（指定 key 和 value 的提取函数）
     */
    public static <T, K, V> Map<K, V> convertMap(Collection<T> from, Function<T, K> keyFunc, Function<T, V> valueFunc) {
        return convertMap(from, keyFunc, valueFunc, (v1, v2) -> v1);
    }

    /**
     * 支持 key 冲突处理的 Map 转换
     */
    public static <T, K, V> Map<K, V> convertMap(Collection<T> from, Function<T, K> keyFunc, Function<T, V> valueFunc, BinaryOperator<V> mergeFunction) {
        return convertMap(from, keyFunc, valueFunc, mergeFunction, HashMap::new);
    }

    /**
     * 指定 Map 类型且支持 key 冲突处理
     */
    public static <T, K, V> Map<K, V> convertMap(Collection<T> from, Function<T, K> keyFunc, Function<T, V> valueFunc, Supplier<? extends Map<K, V>> supplier) {
        return convertMap(from, keyFunc, valueFunc, (v1, v2) -> v1, supplier);
    }

    /**
     * 完整版 Map 转换：支持 key/value 提取、冲突合并、自定义 Map 类型
     */
    public static <T, K, V> Map<K, V> convertMap(Collection<T> from, Function<T, K> keyFunc, Function<T, V> valueFunc, BinaryOperator<V> mergeFunction, Supplier<? extends Map<K, V>> supplier) {
        if (CollUtil.isEmpty(from)) {
            return new HashMap<>();
        }
        return from.stream().collect(Collectors.toMap(keyFunc, valueFunc, mergeFunction, supplier));
    }

    /**
     * 将集合转为“一对多” Map：key → List<T>
     */
    public static <T, K> Map<K, List<T>> convertMultiMap(Collection<T> from, Function<T, K> keyFunc) {
        if (CollUtil.isEmpty(from)) {
            return new HashMap<>();
        }
        return from.stream().collect(Collectors.groupingBy(keyFunc, Collectors.mapping(t -> t, Collectors.toList())));
    }

    /**
     * 一对多 Map，且 value 经过转换
     */
    public static <T, K, V> Map<K, List<V>> convertMultiMap(Collection<T> from, Function<T, K> keyFunc, Function<T, V> valueFunc) {
        if (CollUtil.isEmpty(from)) {
            return new HashMap<>();
        }
        return from.stream()
                .collect(Collectors.groupingBy(keyFunc, Collectors.mapping(valueFunc, Collectors.toList())));
    }

    /**
     * 一对多 Map，value 为 Set（避免重复）
     */
    public static <T, K, V> Map<K, Set<V>> convertMultiMap2(Collection<T> from, Function<T, K> keyFunc, Function<T, V> valueFunc) {
        if (CollUtil.isEmpty(from)) {
            return new HashMap<>();
        }
        return from.stream().collect(Collectors.groupingBy(keyFunc, Collectors.mapping(valueFunc, Collectors.toSet())));
    }

    /**
     * 转换为不可变 Map（Guava ImmutableMap）
     */
    public static <T, K> Map<K, T> convertImmutableMap(Collection<T> from, Function<T, K> keyFunc) {
        if (CollUtil.isEmpty(from)) {
            return Collections.emptyMap();
        }
        ImmutableMap.Builder<K, T> builder = ImmutableMap.builder();
        from.forEach(item -> builder.put(keyFunc.apply(item), item));
        return builder.build();
    }

    /**
     * 比较新旧两个列表的差异，返回 [新增, 修改, 删除]
     * <p>
     * sameFunc 用于判断两个元素是否代表同一个业务实体（如 ID 相同）
     *
     * @param oldList  原始列表
     * @param newList  新列表
     * @param sameFunc 判断两个元素是否“相同”的函数（如 o1.getId().equals(o2.getId())）
     * @return List: [0]=新增, [1]=修改, [2]=删除
     */
    public static <T> List<List<T>> diffList(Collection<T> oldList, Collection<T> newList,
                                             BiFunction<T, T, Boolean> sameFunc) {
        List<T> createList = new LinkedList<>(newList);
        List<T> updateList = new ArrayList<>();
        List<T> deleteList = new ArrayList<>();

        for (T oldObj : oldList) {
            T foundObj = null;
            for (Iterator<T> it = createList.iterator(); it.hasNext(); ) {
                T newObj = it.next();
                if (!sameFunc.apply(oldObj, newObj)) continue;
                it.remove();
                foundObj = newObj;
                break;
            }
            if (foundObj != null) {
                updateList.add(foundObj);
            } else {
                deleteList.add(oldObj);
            }
        }
        return asList(createList, updateList, deleteList);
    }

    /**
     * 判断 source 集合中是否包含 candidates 中任意一个元素
     */
    public static boolean containsAny(Collection<?> source, Collection<?> candidates) {
        return org.springframework.util.CollectionUtils.containsAny(source, candidates);
    }

    /**
     * 获取列表第一个元素（若为空返回 null）
     */
    public static <T> T getFirst(List<T> from) {
        return !CollectionUtil.isEmpty(from) ? from.get(0) : null;
    }

    /**
     * 查找第一个满足条件的元素
     */
    public static <T> T findFirst(Collection<T> from, Predicate<T> predicate) {
        return findFirst(from, predicate, Function.identity());
    }

    /**
     * 查找第一个满足条件的元素，并对其执行转换
     */
    public static <T, U> U findFirst(Collection<T> from, Predicate<T> predicate, Function<T, U> func) {
        if (CollUtil.isEmpty(from)) return null;
        return from.stream().filter(predicate).findFirst().map(func).orElse(null);
    }

    /**
     * 获取集合中某属性的最大值
     */
    public static <T, V extends Comparable<? super V>> V getMaxValue(Collection<T> from, Function<T, V> valueFunc) {
        if (CollUtil.isEmpty(from)) return null;
        T t = from.stream().max(Comparator.comparing(valueFunc)).get();
        return valueFunc.apply(t);
    }

    /**
     * 获取列表中某属性的最小值
     */
    public static <T, V extends Comparable<? super V>> V getMinValue(List<T> from, Function<T, V> valueFunc) {
        if (CollUtil.isEmpty(from)) return null;
        T t = from.stream().min(Comparator.comparing(valueFunc)).get();
        return valueFunc.apply(t);
    }

    /**
     * 获取具有最小属性值的元素对象
     */
    public static <T, V extends Comparable<? super V>> T getMinObject(List<T> from, Function<T, V> valueFunc) {
        if (CollUtil.isEmpty(from)) return null;
        return from.stream().min(Comparator.comparing(valueFunc)).get();
    }

    /**
     * 对集合中某属性进行累加（支持自定义累加器）
     */
    public static <T, V extends Comparable<? super V>> V getSumValue(Collection<T> from, Function<T, V> valueFunc,
                                                                     BinaryOperator<V> accumulator) {
        return getSumValue(from, valueFunc, accumulator, null);
    }

    /**
     * 带默认值的累加
     */
    public static <T, V extends Comparable<? super V>> V getSumValue(Collection<T> from, Function<T, V> valueFunc,
                                                                     BinaryOperator<V> accumulator, V defaultValue) {
        if (CollUtil.isEmpty(from)) return defaultValue;
        return from.stream().map(valueFunc).filter(Objects::nonNull).reduce(accumulator).orElse(defaultValue);
    }

    /**
     * 仅当元素非 null 时才添加到集合中
     */
    public static <T> void addIfNotNull(Collection<T> coll, T item) {
        if (item == null) return;
        coll.add(item);
    }

    /**
     * 将单个对象转为集合（null 时返回空列表）
     */
    public static <T> Collection<T> singleton(T obj) {
        return obj == null ? Collections.emptyList() : Collections.singleton(obj);
    }

    /**
     * 将嵌套列表（List<List<T>>）扁平化为单层列表
     */
    public static <T> List<T> newArrayList(List<List<T>> list) {
        return list.stream().filter(Objects::nonNull).flatMap(Collection::stream).collect(Collectors.toList());
    }

    /**
     * 将任意值转换为 LinkedHashSet（保持插入顺序）
     *
     * @param elementType 元素类型
     * @param value       源值（可以是数组、集合、单个对象等）
     * @return 转换后的 LinkedHashSet
     */
    @SuppressWarnings("unchecked")
    public static <T> LinkedHashSet<T> toLinkedHashSet(Class<T> elementType, Object value) {
        return (LinkedHashSet<T>) toCollection(LinkedHashSet.class, elementType, value);
    }

}