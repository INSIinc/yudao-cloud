package cn.iocoder.yudao.framework.excel.core.handler;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.iocoder.yudao.framework.common.core.KeyValue;
import cn.iocoder.yudao.framework.dict.core.DictFrameworkUtils;
import cn.iocoder.yudao.framework.excel.core.annotations.ExcelColumnSelect;
import cn.iocoder.yudao.framework.excel.core.function.ExcelColumnSelectFunction;
import cn.idev.excel.annotation.ExcelIgnore;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.write.handler.SheetWriteHandler;
import cn.idev.excel.write.metadata.holder.WriteSheetHolder;
import cn.idev.excel.write.metadata.holder.WriteWorkbookHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFDataValidation;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;

/**
 * 基于固定 sheet 实现下拉框
 *
 * 功能说明：这个类用于在导出 Excel 时，为指定的列添加下拉选择框
 * 使用场景：当某些列需要用户从固定选项中选择时（如：性别、状态等），可以使用此功能
 * 实现原理：
 * 1. 创建一个隐藏的字典 sheet 页，存储所有下拉选项的数据
 * 2. 在主 sheet 页中，为指定列添加数据验证，引用字典 sheet 中的数据
 * 3. 用户在 Excel 中点击单元格时，就会显示下拉选项
 *
 * @author HUIHUI
 */
@Slf4j
public class SelectSheetWriteHandler implements SheetWriteHandler {

    /**
     * 数据起始行从 0 开始
     *
     * 约定：本项目第一行有标题所以从 1 开始如果您的 Excel 有多行标题请自行更改
     *
     * 解释：FIRST_ROW = 1 表示从第二行开始添加下拉框（第一行是标题行）
     * 如果你的 Excel 有多行标题，比如两行标题，那么应该设置为 2
     */
    public static final int FIRST_ROW = 1;

    /**
     * 下拉列需要创建下拉框的行数，默认两千行如需更多请自行调整
     *
     * 解释：LAST_ROW = 2000 表示为前 2000 行添加下拉框
     * 如果你的数据可能超过 2000 行，需要增加这个值
     */
    public static final int LAST_ROW = 2000;

    /**
     * 字典 sheet 页的名称
     *
     * 解释：这个 sheet 页用于存储所有下拉选项的数据，用户通常不需要看到这个页面
     */
    private static final String DICT_SHEET_NAME = "字典sheet";

    /**
     * 存储每列的下拉数据
     *
     * key: 列的索引（第几列，从 0 开始计数）
     * value: 该列的下拉选项列表（例如：["男", "女"] 或 ["启用", "禁用"]）
     *
     * 例如：{0: ["男", "女"], 1: ["启用", "禁用"]}
     * 表示第 0 列（A列）的下拉选项是 ["男", "女"]，第 1 列（B列）的下拉选项是 ["启用", "禁用"]
     */
    private final Map<Integer, List<String>> selectMap = new HashMap<>();

    /**
     * 构造方法：在创建对象时，解析传入的 Excel 实体类，找出需要添加下拉框的列
     *
     * @param head Excel 实体类的 Class 对象（例如：UserExcelVO.class）
     *
     * 工作流程：
     * 1. 遍历实体类的所有字段（属性）
     * 2. 找出带有 @ExcelColumnSelect 注解的字段
     * 3. 获取这些字段对应的下拉数据
     * 4. 将列索引和下拉数据存入 selectMap
     */
    public SelectSheetWriteHandler(Class<?> head) {
        // 初始化列索引，从 0 开始（对应 Excel 的 A 列）
        int colIndex = 0;

        // 检查类上是否有 @ExcelIgnoreUnannotated 注解
        // 如果有此注解，表示只处理带 @ExcelProperty 注解的字段，其他字段都忽略
        boolean ignoreUnannotated = head.isAnnotationPresent(ExcelIgnoreUnannotated.class);

        // 遍历实体类的所有声明字段（包括 private 字段）
        for (Field field : head.getDeclaredFields()) {
            // 步骤1：过滤掉不需要处理的字段

            // 1.1 忽略 static final 或 transient 的字段
            // static final：静态常量字段（如：public static final String CONSTANT = "value"）
            // transient：临时字段，不会被序列化的字段
            // 这些字段不需要导出到 Excel
            if (isStaticFinalOrTransient(field)) {
                continue;
            }

            // 1.2 根据注解规则，忽略某些字段
            // 情况1：如果类有 @ExcelIgnoreUnannotated 注解，且字段没有 @ExcelProperty 注解，则忽略
            // 情况2：如果字段有 @ExcelIgnore 注解，则忽略
            if ((ignoreUnannotated && !field.isAnnotationPresent(ExcelProperty.class))
                    || field.isAnnotationPresent(ExcelIgnore.class)) {
                continue;
            }

            // 步骤2：核心逻辑 - 处理有 @ExcelColumnSelect 注解的字段
            // 只有带这个注解的字段，才会添加下拉框
            if (field.isAnnotationPresent(ExcelColumnSelect.class)) {
                // 检查字段是否有 @ExcelProperty 注解，并且指定了列索引
                ExcelProperty excelProperty = field.getAnnotation(ExcelProperty.class);
                if (excelProperty != null && excelProperty.index() != -1) {
                    // 如果指定了列索引，使用指定的索引（优先级更高）
                    colIndex = excelProperty.index();
                }
                // 获取该列的下拉数据，并添加到 selectMap 中
                getSelectDataList(colIndex, field);
            }
            // 列索引递增，处理下一列
            colIndex++;
        }
    }

    /**
     * 判断字段是否是静态的、最终的、 transient 的
     *
     * 原因：FastExcel 默认是忽略 static final 或 transient 的字段，所以需要判断
     *
     * 详细说明：
     * - static final：静态常量，属于类而不是对象，所有对象共享，不需要导出
     * - transient：临时字段，Java 序列化时会忽略，导出 Excel 时也应该忽略
     *
     * @param field 字段对象
     * @return true-需要忽略，false-不需要忽略
     */
    private boolean isStaticFinalOrTransient(Field field) {
        // 获取字段的修饰符（public、private、static、final 等）
        int modifiers = field.getModifiers();

        // 判断是否同时是 static 和 final，或者是 transient
        return (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers))
                || Modifier.isTransient(modifiers);
    }

    /**
     * 获得下拉数据，并添加到 {@link #selectMap} 中
     *
     * 功能说明：根据 @ExcelColumnSelect 注解的配置，获取下拉选项数据
     *
     * 支持两种方式获取下拉数据：
     * 方式1：从数据字典获取（dictType）- 适用于系统中已有的字典数据
     * 方式2：从自定义函数获取（functionName）- 适用于需要动态生成的下拉数据
     *
     * @param colIndex 列索引（第几列）
     * @param field    字段对象（包含注解信息）
     */
    private void getSelectDataList(int colIndex, Field field) {
        // 获取字段上的 @ExcelColumnSelect 注解
        ExcelColumnSelect columnSelect = field.getAnnotation(ExcelColumnSelect.class);

        // 从注解中获取配置项
        String dictType = columnSelect.dictType();         // 字典类型，如："system_user_sex"
        String functionName = columnSelect.functionName(); // 自定义函数名称，如："getDeptOptions"

        // 校验：dictType 和 functionName 至少要配置一个
        // 否则无法知道从哪里获取下拉数据
        Assert.isTrue(ObjectUtil.isNotEmpty(dictType) || ObjectUtil.isNotEmpty(functionName),
                "Field({}) 的 @ExcelColumnSelect 注解，dictType 和 functionName 不能同时为空", field.getName());

        // 情况一：使用 dictType 从数据字典获得下拉数据
        // 例如：dictType="system_user_sex" 会获取 ["男", "女"] 这样的字典数据
        if (StrUtil.isNotEmpty(dictType)) {
            // 调用字典工具类，根据字典类型获取字典标签列表
            // 例如：getDictDataLabelList("system_user_sex") 返回 ["男", "女"]
            selectMap.put(colIndex, DictFrameworkUtils.getDictDataLabelList(dictType));
            return; // 获取成功，直接返回
        }

        // 情况二：使用 functionName 从自定义函数获得下拉数据
        // 适用于下拉数据需要动态生成的场景

        // 从 Spring 容器中获取所有 ExcelColumnSelectFunction 类型的 Bean
        // 这些 Bean 是用户自定义的获取下拉数据的函数
        Map<String, ExcelColumnSelectFunction> functionMap = SpringUtil.getApplicationContext()
                .getBeansOfType(ExcelColumnSelectFunction.class);

        // 根据函数名称，找到对应的函数对象
        // 例如：functionName="getDeptOptions"，就会找到名称为 "getDeptOptions" 的函数
        ExcelColumnSelectFunction function = CollUtil.findOne(functionMap.values(),
                item -> item.getName().equals(functionName));

        // 校验：函数必须存在，否则抛出异常
        Assert.notNull(function, "未找到对应的 function({})", functionName);

        // 调用函数的 getOptions() 方法，获取下拉选项
        // 并将列索引和下拉数据存入 selectMap
        selectMap.put(colIndex, function.getOptions());
    }

    /**
     * 在 Sheet 创建后执行的回调方法
     *
     * 功能说明：这是 EasyExcel 提供的钩子方法，在创建完 Sheet 后会自动调用
     * 在这个方法中，我们会：
     * 1. 创建一个字典 sheet 页，存储所有下拉选项
     * 2. 为主 sheet 的指定列添加数据验证（下拉框）
     *
     * @param writeWorkbookHolder 工作簿持有者（包含整个 Excel 文件的信息）
     * @param writeSheetHolder Sheet 持有者（包含当前 Sheet 页的信息）
     */
    @Override
    public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
        // 如果没有需要添加下拉框的列，直接返回
        if (CollUtil.isEmpty(selectMap)) {
            return;
        }

        // 步骤1：获取相应操作对象

        // 获取数据验证助手，用于创建下拉框的验证规则
        DataValidationHelper helper = writeSheetHolder.getSheet().getDataValidationHelper();

        // 获取工作簿对象，用于创建新的 sheet 页和单元格
        Workbook workbook = writeWorkbookHolder.getWorkbook();

        // 将 selectMap 转换为 KeyValue 列表，方便后续处理
        // 例如：{0: ["男", "女"], 1: ["启用", "禁用"]}
        // 转换为：[KeyValue(0, ["男", "女"]), KeyValue(1, ["启用", "禁用"])]
        List<KeyValue<Integer, List<String>>> keyValues = convertList(selectMap.entrySet(),
                entry -> new KeyValue<>(entry.getKey(), entry.getValue()));

        // 按下拉选项数量升序排序
        // 原因：必须按升序排列，否则创建下拉框时可能会报错
        // 例如：先处理选项少的列，再处理选项多的列
        keyValues.sort(Comparator.comparing(item -> item.getValue().size()));

        // 步骤2：创建数据字典的 sheet 页
        // 这个 sheet 页用于存储所有下拉选项的数据
        Sheet dictSheet = workbook.createSheet(DICT_SHEET_NAME);

        // 遍历每一列的下拉数据
        for (KeyValue<Integer, List<String>> keyValue : keyValues) {
            // 获取当前列的下拉选项数量
            int rowLength = keyValue.getValue().size();

            // 步骤2.1：在字典 sheet 页中填充数据
            // 每一列对应一个字段的下拉选项
            // 例如：第 0 列存储性别选项（男、女），第 1 列存储状态选项（启用、禁用）
            for (int i = 0; i < rowLength; i++) {
                // 获取或创建当前行
                Row row = dictSheet.getRow(i);
                if (row == null) {
                    row = dictSheet.createRow(i);
                }
                // 在指定列创建单元格，并设置值
                // 例如：第 0 行第 0 列设置为 "男"，第 1 行第 0 列设置为 "女"
                row.createCell(keyValue.getKey()).setCellValue(keyValue.getValue().get(i));
            }

            // 步骤2.2：为主 sheet 的当前列设置下拉框
            setColumnSelect(writeSheetHolder, workbook, helper, keyValue);
        }
    }

    /**
     * 设置单元格下拉选择
     *
     * 功能说明：为指定列的单元格添加下拉框验证
     *
     * 实现步骤：
     * 1. 创建命名区域，引用字典 sheet 中的数据（如：字典sheet!$A$1:$A$2）
     * 2. 创建数据验证约束，使用命名区域作为下拉选项
     * 3. 将验证应用到指定列的所有行（从 FIRST_ROW 到 LAST_ROW）
     *
     * @param writeSheetHolder Sheet 持有者
     * @param workbook 工作簿对象
     * @param helper 数据验证助手
     * @param keyValue 键值对，包含列索引和下拉选项列表
     */
    private static void setColumnSelect(WriteSheetHolder writeSheetHolder, Workbook workbook,
                                        DataValidationHelper helper, KeyValue<Integer, List<String>> keyValue) {
        // 步骤1：创建命名区域（Named Range）
        // 命名区域是 Excel 中的一个功能，可以给一个单元格区域起个名字，方便引用

        // 1.1 创建一个命名对象
        Name name = workbook.createName();

        // 将列索引转换为 Excel 列名
        // 例如：0 -> A, 1 -> B, 2 -> C
        String excelColumn = ExcelUtil.indexToColName(keyValue.getKey());

        // 1.2 构造引用公式，指向字典 sheet 中的数据区域
        // 格式：字典sheet!$A$1:$A$2
        // 例如：第 0 列（A列）有 2 个选项，则引用 "字典sheet!$A$1:$A$2"
        // $A 表示绝对引用列，$1 表示绝对引用行
        String refers = DICT_SHEET_NAME + "!$" + excelColumn + "$1:$"
                + excelColumn + "$" + keyValue.getValue().size();

        // 设置命名区域的名称
        // 例如：dict0, dict1, dict2...（每列一个命名区域）
        name.setNameName("dict" + keyValue.getKey());

        // 设置命名区域引用的公式
        name.setRefersToFormula(refers);

        // 步骤2：创建数据验证（Data Validation）

        // 2.1 创建基于公式列表的约束
        // 使用刚才创建的命名区域作为下拉选项的数据源
        DataValidationConstraint constraint = helper.createFormulaListConstraint("dict" + keyValue.getKey());

        // 设置下拉框应用的单元格范围
        // 参数：首行、末行、首列、末列
        // 例如：(1, 2000, 0, 0) 表示 A2:A2001 单元格都有下拉框
        CellRangeAddressList rangeAddressList = new CellRangeAddressList(
                FIRST_ROW,           // 起始行（从第 2 行开始，因为第 1 行是标题）
                LAST_ROW,            // 结束行（到第 2001 行）
                keyValue.getKey(),   // 起始列（当前列）
                keyValue.getKey()    // 结束列（当前列）
        );

        // 创建数据验证对象
        DataValidation validation = helper.createValidation(constraint, rangeAddressList);

        // 根据 Excel 文件格式（xls 或 xlsx），设置不同的属性
        if (validation instanceof HSSFDataValidation) {
            // HSSFDataValidation 是 xls 格式（Excel 2003 及以前）
            validation.setSuppressDropDownArrow(false); // 显示下拉箭头
        } else {
            // XSSFDataValidation 是 xlsx 格式（Excel 2007 及以后）
            validation.setSuppressDropDownArrow(true);  // 隐藏下拉箭头（xlsx 格式会自动显示）
            validation.setShowErrorBox(true);           // 显示错误提示框
        }

        // 2.2 设置错误提示，阻止输入非下拉框的值
        // 当用户输入的值不在下拉选项中时，显示错误提示
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP); // 错误样式：停止（阻止输入）
        validation.createErrorBox("提示", "此值不存在于下拉选择中！");  // 错误提示框的标题和内容

        // 2.3 将数据验证应用到 sheet 页
        // 此时，指定列的单元格就有了下拉框功能
        writeSheetHolder.getSheet().addValidationData(validation);
    }

}