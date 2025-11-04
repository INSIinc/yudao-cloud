package cn.iocoder.yudao.module.bpm.service.definition.dto;

import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelFormTypeEnum;
import lombok.Data;

/**
 * BPM 流程 MetaInfo Response DTO
 * 主要用于 { Model#setMetaInfo(String)} 的存储
 * <p>
 * 最终，它的字段和 {@link cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO} 是一致的
 * <p>
 * 类说明：BPM（业务流程管理）流程模型的元数据信息响应对象
 * 这个类用于封装流程模型的基本信息，比如图标、描述、表单配置等
 * DTO（Data Transfer Object）数据传输对象，用于在不同层之间传递数据
 *
 * @author 芋道源码
 */
@Data
public class BpmModelMetaInfoRespDTO {

    /**
     * 流程图标
     * <p>
     * 说明：用于在界面上显示的图标路径或图标名称
     * <p>
     * 示例：可以是一个图标的URL地址或者图标库中的图标名称
     */
    private String icon;

    /**
     * 流程描述
     * <p>
     * 说明：对这个流程的详细说明，用于帮助用户理解这个流程是做什么的，有什么作用
     */
    private String description;

    /**
     * 表单类型
     * <p>
     * 说明：标识使用哪种类型的表单，这是一个整数，对应 {@link BpmModelFormTypeEnum} 枚举中的值
     * <p>
     * 常见类型：普通表单（NORMAL）和自定义表单（CUSTOM）
     *
     * @see BpmModelFormTypeEnum
     */
    private Integer formType;

    /**
     * 表单编号
     * <p>
     * 说明：当使用普通表单时，这个字段存储表单的ID，通过这个ID可以找到对应的表单配置
     * <p>
     * 注意：只有当 formType 为 {@link BpmModelFormTypeEnum#NORMAL}（普通表单）时，这个字段才有值
     *
     * @see BpmModelFormTypeEnum#NORMAL
     */
    private Long formId;

    /**
     * 自定义表单的提交路径，使用 Vue 的路由地址
     * <p>
     * 说明：当使用自定义表单时，用户提交表单时跳转的页面路径
     * <p>
     * 示例：/bpm/custom/create
     * <p>
     * 注意：只有当 formType 为 {@link BpmModelFormTypeEnum#CUSTOM}（自定义表单）时，这个字段才有值
     *
     * @see BpmModelFormTypeEnum#CUSTOM
     */
    private String formCustomCreatePath;

    /**
     * 自定义表单的查看路径，使用 Vue 的路由地址
     * <p>
     * 说明：当使用自定义表单时，用户查看表单详情时跳转的页面路径
     * <p>
     * 示例：/bpm/custom/view
     * <p>
     * 注意：只有当 formType 为 {@link BpmModelFormTypeEnum#CUSTOM}（自定义表单）时，这个字段才有值
     *
     * @see BpmModelFormTypeEnum#CUSTOM
     */
    private String formCustomViewPath;

}
