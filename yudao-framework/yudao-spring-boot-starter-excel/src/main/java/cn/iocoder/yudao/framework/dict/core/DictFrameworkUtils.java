package cn.iocoder.yudao.framework.dict.core;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.biz.system.dict.DictDataCommonApi;
import cn.iocoder.yudao.framework.common.util.cache.CacheUtils;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;

/**
 * 字典工具类
 *
 * 作用说明：
 * 这个类是一个字典数据的工具类，用于在系统中快速获取和转换字典数据。
 * 什么是字典？例如：性别（男=1，女=2），状态（启用=1，禁用=0）等固定的键值对数据。
 *
 * 主要功能：
 * 1. 根据字典值（value）查询字典标签（label），例如：输入 1 返回 "男"
 * 2. 根据字典标签（label）查询字典值（value），例如：输入 "男" 返回 1
 * 3. 获取某个字典类型的所有标签或值列表
 * 4. 使用缓存机制提高查询性能，避免频繁访问数据库
 *
 * @author 芋道源码
 */
@Slf4j // Lombok 注解，自动生成日志对象 log，用于记录日志信息
public class DictFrameworkUtils {

    /**
     * 字典数据 API 接口
     * 这是一个静态变量，用于调用远程服务或数据库获取字典数据
     * static 修饰表示这是类级别的变量，所有实例共享同一个对象
     */
    private static DictDataCommonApi dictDataApi;

    /**
     * 字典数据缓存
     *
     * 缓存说明：
     * - Key（键）：字典类型（dictType），例如 "sys_user_sex"（用户性别）
     * - Value（值）：该字典类型下的所有字典数据列表
     *
     * 为什么需要缓存？
     * 字典数据通常不经常变化，每次都查询数据库会影响性能。
     * 使用缓存可以将数据存在内存中，提高访问速度。
     *
     * 缓存特性：
     * - 过期时间：1分钟，过期后自动重新加载最新数据
     * - 异步刷新：在后台自动更新缓存，不会阻塞查询请求
     */
    private static final LoadingCache<String, List<DictDataRespDTO>> GET_DICT_DATA_CACHE = CacheUtils.buildAsyncReloadingCache(
            Duration.ofMinutes(1L), // 过期时间 1 分钟
            new CacheLoader<String, List<DictDataRespDTO>>() {

                @Override
                public List<DictDataRespDTO> load(String dictType) {
                    // 当缓存中没有数据时，会调用这个方法从远程服务或数据库加载数据
                    return dictDataApi.getDictDataList(dictType).getCheckedData();
                }

            });

    /**
     * 初始化方法
     *
     * 在系统启动时调用，设置字典数据 API 接口
     *
     * @param dictDataApi 字典数据 API 接口实例
     */
    public static void init(DictDataCommonApi dictDataApi) {
        DictFrameworkUtils.dictDataApi = dictDataApi;
        log.info("[init][初始化 DictFrameworkUtils 成功]");
    }

    /**
     * 清空缓存
     *
     * 使用场景：
     * 当字典数据发生变更（新增、修改、删除）时，需要清空缓存，
     * 这样下次查询时会重新从数据库加载最新数据
     */
    public static void clearCache() {
        GET_DICT_DATA_CACHE.invalidateAll();
    }

    /**
     * 根据字典值（Integer类型）获取字典标签
     *
     * 使用示例：
     * parseDictDataLabel("sys_user_sex", 1) 返回 "男"
     *
     * @param dictType 字典类型，例如 "sys_user_sex"（用户性别）
     * @param value 字典值，例如 1
     * @return 字典标签，例如 "男"；如果找不到则返回 null
     */
    @SneakyThrows // Lombok 注解，自动处理检查型异常，简化代码
    public static String parseDictDataLabel(String dictType, Integer value) {
        // 如果传入的值为 null，直接返回 null
        if (value == null) {
            return null;
        }
        // 将 Integer 转换为 String，然后调用另一个重载方法
        return parseDictDataLabel(dictType, String.valueOf(value));
    }

    /**
     * 根据字典值（String类型）获取字典标签
     *
     * 核心方法：通过字典类型和字典值，查找对应的字典标签
     *
     * 执行流程：
     * 1. 从缓存中获取该字典类型的所有字典数据
     * 2. 遍历查找匹配的字典值
     * 3. 返回对应的字典标签
     *
     * @param dictType 字典类型
     * @param value 字典值（String类型）
     * @return 字典标签；如果找不到则返回 null
     */
    @SneakyThrows
    public static String parseDictDataLabel(String dictType, String value) {
        // 从缓存中获取该字典类型的所有数据
        List<DictDataRespDTO> dictDatas = GET_DICT_DATA_CACHE.get(dictType);
        // 使用 hutool 工具类查找匹配的字典项
        // lambda 表达式：data -> Objects.equals(data.getValue(), value)
        // 意思是：在列表中查找 value 值等于传入参数 value 的那一项
        DictDataRespDTO dictData = CollUtil.findOne(dictDatas, data -> Objects.equals(data.getValue(), value));
        // 三元运算符：如果找到了返回 label，否则返回 null
        return dictData != null ? dictData.getLabel(): null;
    }

    /**
     * 获取某个字典类型的所有标签列表
     *
     * 使用示例：
     * getDictDataLabelList("sys_user_sex") 返回 ["男", "女", "未知"]
     *
     * 应用场景：
     * 在下拉框或选择器中显示所有可选项的标签
     *
     * @param dictType 字典类型
     * @return 该字典类型下所有字典项的标签列表
     */
    @SneakyThrows
    public static List<String> getDictDataLabelList(String dictType) {
        // 从缓存中获取该字典类型的所有数据
        List<DictDataRespDTO> dictDatas = GET_DICT_DATA_CACHE.get(dictType);
        // 将字典数据列表转换为只包含 label 的字符串列表
        // DictDataRespDTO::getLabel 是方法引用，等价于 data -> data.getLabel()
        return convertList(dictDatas, DictDataRespDTO::getLabel);
    }

    /**
     * 根据字典标签获取字典值（反向查询）
     *
     * 使用示例：
     * parseDictDataValue("sys_user_sex", "男") 返回 "1"
     *
     * 应用场景：
     * Excel 导入时，用户输入的是中文标签，需要转换为系统中的数字值
     *
     * @param dictType 字典类型
     * @param label 字典标签，例如 "男"
     * @return 字典值，例如 "1"；如果找不到则返回 null
     */
    @SneakyThrows
    public static String parseDictDataValue(String dictType, String label) {
        // 从缓存中获取该字典类型的所有数据
        List<DictDataRespDTO> dictDatas = GET_DICT_DATA_CACHE.get(dictType);
        // 查找 label 等于传入参数的字典项
        DictDataRespDTO dictData = CollUtil.findOne(dictDatas, data -> Objects.equals(data.getLabel(), label));
        // 返回找到的字典值，找不到返回 null
        return dictData!= null ? dictData.getValue(): null;
    }

    /**
     * 获取某个字典类型的所有值列表
     *
     * 使用示例：
     * getDictDataValueList("sys_user_sex") 返回 ["1", "2", "0"]
     *
     * 应用场景：
     * 需要获取某个字典类型的所有可选值，用于数据验证或查询条件
     *
     * @param dictType 字典类型
     * @return 该字典类型下所有字典项的值列表
     */
    @SneakyThrows
    public static List<String> getDictDataValueList(String dictType) {
        // 从缓存中获取该字典类型的所有数据
        List<DictDataRespDTO> dictDatas = GET_DICT_DATA_CACHE.get(dictType);
        // 将字典数据列表转换为只包含 value 的字符串列表
        return convertList(dictDatas, DictDataRespDTO::getValue);
    }
}
