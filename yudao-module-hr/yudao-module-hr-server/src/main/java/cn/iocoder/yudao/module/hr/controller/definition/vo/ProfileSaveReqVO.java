package cn.iocoder.yudao.module.hr.controller.definition.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.util.Date;

@Schema(description = "管理后台 - 员工档案创建/修改 Request VO")
@Data
public class ProfileSaveReqVO {

    @Schema(description = "员工档案编号", example = "1024")
    private Long id;

    @Schema(description = "是否离职", example = "false")
    private Boolean isLeaved;

    @Schema(description = "姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名不能超过50个字符")
    private String name;

    @Schema(description = "性别", requiredMode = Schema.RequiredMode.REQUIRED, example = "M")
    @NotBlank(message = "性别不能为空")
    @Size(max = 10, message = "性别不能超过10个字符")
    private String gender;

    @Schema(description = "年龄", requiredMode = Schema.RequiredMode.REQUIRED, example = "25")
    @NotNull(message = "年龄不能为空")
    private Integer age;

    @Schema(description = "身份证号", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456789012345678")
    @NotBlank(message = "身份证号不能为空")
    @Size(max = 18, message = "身份证号不能超过18个字符")
    private String idNumber;

    @Schema(description = "排序", example = "1")
    private Integer order;

    @Schema(description = "证书编号", example = "CERT123456")
    @Size(max = 50, message = "证书编号不能超过50个字符")
    private String certificateNumber;

    @Schema(description = "头像地址", example = "https://www.iocoder.cn/1.png")
    @Size(max = 200, message = "头像地址不能超过200个字符")
    private String avatar;

    @Schema(description = "任职命令", example = "任命为科长")
    @Size(max = 200, message = "任职命令不能超过200个字符")
    private String formationCommand;

    @Schema(description = "出生日期", example = "2023-01-01")
    @Past(message = "出生日期必须是过去的日期")
    private Date birthDate;

    @Schema(description = "政治面貌", example = "党员")
    @Size(max = 50, message = "政治面貌不能超过50个字符")
    private String politicalStatus;

    @Schema(description = "学历", example = "本科")
    @Size(max = 50, message = "学历不能超过50个字符")
    private String education;

    @Schema(description = "待遇级别", example = "高级")
    @Size(max = 50, message = "待遇级别不能超过50个字符")
    private String treatmentLevel;

    @Schema(description = "籍贯-省份", example = "广东省")
    @Size(max = 50, message = "籍贯省份不能超过50个字符")
    private String nativeProvince;

    @Schema(description = "籍贯-市", example = "深圳市")
    @Size(max = 50, message = "籍贯市不能超过50个字符")
    private String nativeCity;

    @Schema(description = "籍贯-县", example = "南山区")
    @Size(max = 50, message = "籍贯县不能超过50个字符")
    private String nativeCounty;

    @Schema(description = "入职日期", example = "2023-01-01")
    private Date hireDate;

    @Schema(description = "身份类别", example = "正式员工")
    @Size(max = 50, message = "身份类别不能超过50个字符")
    private String identityCategory;

    @Schema(description = "警衔级别", example = "一级警司")
    @Size(max = 50, message = "警衔级别不能超过50个字符")
    private String policeRankLevel;

    @Schema(description = "警衔评定日期", example = "2023-01-01")
    private Date policeRankDate;

    @Schema(description = "职位编码", example = "POS001")
    @Size(max = 50, message = "职位编码不能超过50个字符")
    private String positionCode;

    @Schema(description = "职位等级", example = "高级")
    @Size(max = 50, message = "职位等级不能超过50个字符")
    private String positionLevel;

    @Schema(description = "职位名称", example = "开发工程师")
    @Size(max = 100, message = "职位名称不能超过100个字符")
    private String positionName;

    @Schema(description = "专长技能", example = "{\"technical\":\"Java\",\"language\":\"English\"}")
    private String specialties;

    @Schema(description = "元数据信息", example = "{\"customField1\":\"value1\",\"customField2\":\"value2\"}")
    private String metadata;

    @Schema(description = "创建时间")
    private Date createdAt;

    @Schema(description = "更新时间")
    private Date updatedAt;

    @Schema(description = "删除时间")
    private Date deletedAt;

}
