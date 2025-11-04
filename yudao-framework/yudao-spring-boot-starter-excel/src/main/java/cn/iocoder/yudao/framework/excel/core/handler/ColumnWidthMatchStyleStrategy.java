package cn.iocoder.yudao.framework.excel.core.handler;

import cn.hutool.core.collection.CollUtil;
import cn.idev.excel.enums.CellDataTypeEnum;
import cn.idev.excel.metadata.Head;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.util.MapUtils;
import cn.idev.excel.write.metadata.holder.WriteSheetHolder;
import cn.idev.excel.write.style.column.AbstractColumnWidthStyleStrategy;
import cn.idev.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.apache.poi.ss.usermodel.Cell;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 自适应列宽处理器
 *
 * 相比 {@link LongestMatchColumnWidthStyleStrategy} 来说，额外处理了 DATE 类型！
 *
 * @see <a href="https://github.com/YunaiV/yudao-cloud/pull/196/">添加自适应列宽处理器，并替换默认列宽策略</a>
 * @author hmb
 */
public class ColumnWidthMatchStyleStrategy extends AbstractColumnWidthStyleStrategy {

    /**
     * Excel 列宽的最大值
     * 根据 Excel 规范，列宽最大不能超过 255 个字符宽度
     */
    private static final int MAX_COLUMN_WIDTH = 255;

    /**
     * 列宽缓存 Map
     * 外层 Map 的 key：Sheet 页的编号（第几个工作表）
     * 内层 Map 的 key：列的索引（第几列）
     * 内层 Map 的 value：该列的最大宽度值
     *
     * 作用：记录每个 Sheet 中每一列的最大宽度，避免重复计算，提高性能
     */
    private final Map<Integer, Map<Integer, Integer>> cache = MapUtils.newHashMapWithExpectedSize(8);

    /**
     * 设置列宽的核心方法
     * 每次写入单元格时都会调用此方法来决定是否需要调整列宽
     *
     * @param writeSheetHolder 当前正在写入的 Sheet 页持有者对象
     * @param cellDataList 单元格数据列表（包含单元格的值和类型等信息）
     * @param cell 当前正在处理的单元格对象
     * @param head 表头信息
     * @param relativeRowIndex 相对行索引
     * @param isHead 是否是表头行（true 表示当前行是表头）
     */
    @Override
    protected void setColumnWidth(WriteSheetHolder writeSheetHolder, List<WriteCellData<?>> cellDataList, Cell cell,
                                  Head head, Integer relativeRowIndex, Boolean isHead) {
        // 判断是否需要设置列宽：如果是表头行 或 单元格数据列表不为空，则需要设置
        boolean needSetWidth = isHead || CollUtil.isNotEmpty(cellDataList);
        if (!needSetWidth) {
            return; // 不需要设置列宽，直接返回
        }

        // 从缓存中获取当前 Sheet 的列宽映射表，如果不存在则创建一个新的
        Map<Integer, Integer> maxColumnWidthMap = cache.computeIfAbsent(writeSheetHolder.getSheetNo(),
                key -> new HashMap<>(16));

        // 计算当前单元格内容的长度（字节数）
        Integer columnWidth = dataLength(cellDataList, cell, isHead);
        if (columnWidth < 0) {
            return; // 如果长度小于 0（表示无法计算），则不设置列宽
        }

        // 如果计算出的列宽超过最大值，则限制为最大值
        if (columnWidth > MAX_COLUMN_WIDTH) {
            columnWidth = MAX_COLUMN_WIDTH;
        }

        // 获取该列之前记录的最大宽度
        Integer maxColumnWidth = maxColumnWidthMap.get(cell.getColumnIndex());

        // 如果该列还没有记录过宽度，或者当前内容的宽度更大，则更新列宽
        if (maxColumnWidth == null || columnWidth > maxColumnWidth) {
            // 更新缓存中的最大宽度值
            maxColumnWidthMap.put(cell.getColumnIndex(), columnWidth);

            // 设置 Excel 列宽（需要乘以 256，这是 POI 的单位要求）
            // POI 中列宽的单位是 1/256 个字符宽度
            writeSheetHolder.getSheet().setColumnWidth(cell.getColumnIndex(), columnWidth * 256);
        }
    }

    /**
     * 计算单元格数据的长度（字节数）
     * 根据不同的数据类型，采用不同的计算方式
     *
     * @param cellDataList 单元格数据列表
     * @param cell 当前单元格对象
     * @param isHead 是否是表头
     * @return 数据长度（字节数），如果无法计算则返回 -1
     */
    @SuppressWarnings("EnhancedSwitchMigration")
    private Integer dataLength(List<WriteCellData<?>> cellDataList, Cell cell, Boolean isHead) {
        // 如果是表头，直接获取单元格的字符串值并计算字节长度
        if (isHead) {
            return cell.getStringCellValue().getBytes().length;
        }

        // 获取第一个单元格数据（通常只有一个）
        WriteCellData<?> cellData = cellDataList.get(0);

        // 获取单元格数据类型
        CellDataTypeEnum type = cellData.getType();
        if (type == null) {
            return -1; // 类型为空，无法计算长度
        }

        // 根据不同的数据类型，获取对应的值并计算字节长度
        switch (type) {
            case STRING: // 字符串类型
                return cellData.getStringValue().getBytes().length;
            case BOOLEAN: // 布尔类型（true/false）
                return cellData.getBooleanValue().toString().getBytes().length;
            case NUMBER: // 数字类型
                return cellData.getNumberValue().toString().getBytes().length;
            case DATE: // 日期类型（这是相比原生 EasyExcel 策略新增的支持）
                return cellData.getDateValue().toString().getBytes().length;
            default: // 其他未知类型
                return -1;
        }
    }

}