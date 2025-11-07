package cn.iocoder.yudao.module.hr.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_profile") // 确保与数据库表名一致
public class ProfileDO extends BaseDO {

    /**
     * id: cuid() 字符串，非自增
     */
    @TableId(value = "id", type = IdType.NONE)
    private String id;

    /**
     * isLeaved -> is_leaved
     */
    @TableField("is_leaved")
    private Boolean isLeaved = false;

    /**
     * 基本信息
     */
    private String name; // 姓名
    private String gender; // 性别
    private Integer age; // 年龄

    @TableField(value = "id_number",exist = true)
    private String idNumber; // 身份证号（唯一）

    private Float order = 0F; // 排序

    @TableField("certificate_number")
    private String certificateNumber; // 证件号

    private String avatar; // 头像

    @TableField("formation_command")
    private String formationCommand; // 编制命令

    @TableField("birth_date")
    private LocalDateTime birthDate; // 生日

    @TableField("political_status")
    private String politicalStatus; // 政治面貌

    private String education; // 学历

    @TableField("treatment_level")
    private String treatmentLevel; // 待遇级别

    /**
     * 籍贯信息
     */
    @TableField("native_province")
    private String nativeProvince;

    @TableField("native_city")
    private String nativeCity;

    @TableField("native_county")
    private String nativeCounty;

    /**
     * 入职信息
     */
    @TableField("hire_date")
    private LocalDateTime hireDate;

    /**
     * 身份信息
     */
    @TableField("identity_category")
    private String identityCategory; // 身份类别

    @TableField("police_rank_level")
    private String policeRankLevel; // 警衔等级

    @TableField("police_rank_date")
    private LocalDateTime policeRankDate; // 警衔等级时间

    /**
     * 岗位信息
     */
    @TableField("position_code")
    private String positionCode; // 职务代码

    @TableField("position_level")
    private String positionLevel; // 职务级别

    @TableField("position_name")
    private String positionName; // 职务名称

    /**
     * 专业信息：存储为 JSON 字符串（如 ["专业1", "专业2"]）
     */
    @TableField("specialties")
    private String specialties; // 建议用 String 存 JSON，或自定义 TypeHandler

    /**
     * 元数据：低频详细信息，JSON 字符串
     */
    @TableField("metadata")
    private String metadata;

    // 时间戳字段已由 BaseDO 提供：createTime, updateTime, creator, updater, deleted
}