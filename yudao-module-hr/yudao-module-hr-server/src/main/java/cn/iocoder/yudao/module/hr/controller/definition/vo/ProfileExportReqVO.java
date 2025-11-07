package cn.iocoder.yudao.module.hr.controller.definition.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 员工档案 Excel 导出 Request VO")
@Data
public class ProfileExportReqVO {

    @Schema(description = "导出范围", requiredMode = Schema.RequiredMode.REQUIRED, example = "current")
    private String exportScope; // "current" | "all"

    @Schema(description = "当前数据（当exportScope为current时使用）", example = "[]")
    private List<Map<String, Object>> currentData;

    @Schema(description = "基础过滤条件", example = "{\"name\":\"张三\"}")
    private Map<String, Object> baseWhere;

    @Schema(description = "高级过滤条件", example = "{\"age\":{\"$gt\":25}}")
    private Map<String, Object> advancedWhere;

    @Schema(description = "排序条件", example = "{\"createTime\":\"desc\"}")
    private Map<String, Object> orderByCondition;

    @Schema(description = "选中的字段", requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"name\",\"age\",\"position\"]")
    private List<String> selectedFields;

    @Schema(description = "搜索词", example = "张三")
    private String searchTerm;

    @Schema(description = "搜索字段", example = "name")
    private String searchField;

    @Schema(description = "选择状态")
    private Selection selection;

    @Schema(description = "员工档案导出选择状态")
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Selection extends PageParam {

        @Schema(description = "是否全选", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
        private Boolean isAllSelected;

        @Schema(description = "取消选择的ID列表", example = "[\"1\",\"3\",\"5\"]")
        private List<Long> deselectedIds;

        @Schema(description = "选中的行ID列表", example = "[\"2\",\"4\",\"6\"]")
        private List<Long> selectedRowIds;

    }

}
