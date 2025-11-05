package cn.iocoder.yudao.framework.excel.core.convert;

import cn.hutool.core.convert.Convert;
import cn.iocoder.yudao.framework.dict.core.DictFrameworkUtils;
import cn.iocoder.yudao.framework.excel.core.annotations.DictFormat;
import cn.idev.excel.converters.Converter;
import cn.idev.excel.enums.CellDataTypeEnum;
import cn.idev.excel.metadata.GlobalConfiguration;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.metadata.property.ExcelContentProperty;
import lombok.extern.slf4j.Slf4j;

/**
 * Excel 字典转换器：用于在 Excel 导入（读）和导出（写）时，自动把数据库中的“字典值”和 Excel 中的“字典标签”互相转换。
 *
 * 举例：
 * - 数据库中存的是 "1"（值），Excel 显示的是 "男"（标签）
 * - 导入时：Excel 填“男” → 自动转成 "1" 存入数据库
 * - 导出时：数据库是 "1" → Excel 显示“男”
 *
 * 使用方式：在实体类字段上加 @DictFormat("user_sex") 注解，指定字典类型。
 *
 * @author 芋道源码
 */
@Slf4j // 自动注入日志对象 log，用于记录错误
public class DictConvert implements Converter<Object> {

    /**
     * 【不使用】该方法用于指定支持的 Java 类型，但在 Easy-Excel 的某些场景下不需要实现。
     */
    @Override
    public Class<?> supportJavaTypeKey() {
        throw new UnsupportedOperationException("暂不支持，也不需要");
    }

    /**
     * 【不使用】该方法用于指定支持的 Excel 单元格类型，同样在当前场景下不需要。
     */
    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        throw new UnsupportedOperationException("暂不支持，也不需要");
    }

    /**
     * 将 Excel 单元格中的“字典标签”转换为 Java 对象中的“字典值”（用于 Excel 导入）。
     *
     * 例如：Excel 里写的是 "男"，数据库字段类型是 Integer，最终会转成 1。
     *
     * @param readCellData       Excel 中读取到的单元格数据（比如 "男"）
     * @param contentProperty    当前字段的元信息（包含字段类型、注解等）
     * @param globalConfiguration 全局配置（本方法未使用）
     * @return 转换后的 Java 对象（如 Integer 1、String "1" 等）
     */
    @Override
    public Object convertToJavaData(ReadCellData readCellData, ExcelContentProperty contentProperty,
                                    GlobalConfiguration globalConfiguration) {
        // 1. 从字段的 @DictFormat 注解中获取字典类型（比如 "user_sex"）
        String type = getType(contentProperty);

        // 2. 读取 Excel 单元格中的“标签”内容（比如 "男"）
        String label = readCellData.getStringValue();

        // 3. 根据字典类型 + 标签，查出对应的“值”（比如 "1"）
        String value = DictFrameworkUtils.parseDictDataValue(type, label);

        // 4. 如果查不到，说明标签无效，记录错误并返回 null
        if (value == null) {
            log.error("[convertToJavaData][type({}) 解析不掉 label({})]", type, label);
            return null;
        }

        // 5. 把字符串 "1" 转换成字段实际需要的 Java 类型（如 Integer、Long、String 等）
        Class<?> fieldClazz = contentProperty.getField().getType();
        return Convert.convert(fieldClazz, value); // Hutool 的类型转换工具
    }

    /**
     * 将 Java 对象中的“字典值”转换为 Excel 中的“字典标签”（用于 Excel 导出）。
     *
     * 例如：Java 对象中 sex = 1 → Excel 显示 "男"。
     *
     * @param object             Java 对象中字段的实际值（如 1）
     * @param contentProperty    当前字段的元信息
     * @param globalConfiguration 全局配置（本方法未使用）
     * @return 要写入 Excel 的单元格数据（比如 "男"）
     */
    @Override
    public WriteCellData<String> convertToExcelData(Object object, ExcelContentProperty contentProperty,
                                                    GlobalConfiguration globalConfiguration) {
        // 如果值为 null，Excel 显示空字符串
        if (object == null) {
            return new WriteCellData<>("");
        }

        // 1. 获取字典类型（如 "user_sex"）
        String type = getType(contentProperty);

        // 2. 把字段值转成字符串（如 1 → "1"）
        String value = String.valueOf(object);

        // 3. 根据字典类型 + 值，查出对应的“标签”（如 "男"）
        String label = DictFrameworkUtils.parseDictDataLabel(type, value);

        // 4. 如果查不到标签，记录错误并返回空
        if (label == null) {
            log.error("[convertToExcelData][type({}) 转换不了 label({})]", type, value);
            return new WriteCellData<>("");
        }

        // 5. 返回要写入 Excel 的标签内容
        return new WriteCellData<>(label);
    }

    /**
     * 从字段的 @DictFormat 注解中提取字典类型。
     *
     * 例如：@DictFormat("user_status") → 返回 "user_status"
     *
     * @param contentProperty 字段的 Excel 元信息
     * @return 字典类型（即注解的 value 值）
     */
    private static String getType(ExcelContentProperty contentProperty) {
        return contentProperty.getField().getAnnotation(DictFormat.class).value();
    }

}