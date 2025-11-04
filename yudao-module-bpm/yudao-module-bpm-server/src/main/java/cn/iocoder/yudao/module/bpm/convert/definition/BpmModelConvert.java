package cn.iocoder.yudao.module.bpm.convert.definition;

// 工具类导入
import cn.hutool.core.util.ArrayUtil; // 数组工具类，用于判断数组是否为空等
import cn.iocoder.yudao.framework.common.util.date.DateUtils; // 日期格式转换工具类
import cn.iocoder.yudao.framework.common.util.json.JsonUtils; // JSON 序列化/反序列化工具类
import cn.iocoder.yudao.framework.common.util.object.BeanUtils; // 对象属性拷贝工具（类似 Spring BeanUtils，但更安全）
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils; // 集合工具类（如 convertList）
import cn.iocoder.yudao.module.bpm.controller.admin.base.dept.DeptSimpleBaseVO; // 部门简要信息 VO（用于前端展示）
import cn.iocoder.yudao.module.bpm.controller.admin.base.user.UserSimpleBaseVO; // 用户简要信息 VO
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelMetaInfoVO; // 模型的元信息（自定义字段，如表单ID、启动用户等）
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelRespVO; // 返回给前端的模型信息
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO; // 前端保存模型时传入的请求参数
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO; // 简化的流程节点信息
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.process.BpmProcessDefinitionRespVO; // 流程定义信息（部署后的流程）
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmCategoryDO; // 流程分类实体
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO; // 表单实体
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils; // BPMN 模型工具类（用于解析 BPMN XML）
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO; // 系统模块返回的部门完整信息
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO; // 系统模块返回的用户完整信息

// Flowable 引擎相关类
import org.flowable.common.engine.impl.db.SuspensionState; // 流程挂起状态枚举
import org.flowable.engine.repository.Deployment; // Flowable 部署信息
import org.flowable.engine.repository.Model; // Flowable 模型信息（未部署的设计阶段模型）
import org.flowable.engine.repository.ProcessDefinition; // Flowable 流程定义（已部署的流程）

// MapStruct 注解（用于生成对象映射代码）
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

// Java 标准库
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;

/**
 * 流程模型（Model）转换器（Convert）
 * <p>
 * 作用：将 Flowable 引擎中的 Model 对象，以及关联的表单、分类、部署、用户等数据，
 * 转换为前端所需的响应对象（VO），或将前端请求参数回填到 Model 对象中。
 * <p>
 * 使用 MapStruct 提供基础映射能力，同时通过 default 方法实现复杂逻辑。
 *
 * @author yunlongn
 */
@Mapper // 告诉 MapStruct 这是一个转换接口
public interface BpmModelConvert {

    // 单例实例，供外部直接调用（无需注入）
    BpmModelConvert INSTANCE = Mappers.getMapper(BpmModelConvert.class);

    /**
     * 将多个 Flowable 的 Model 对象 转换为 BpmModelRespVO 列表（用于列表页展示）
     * <p>
     * 此方法需要传入多个预加载的 Map，避免 N+1 查询，提升性能。
     *
     * @param list                    Flowable 的 Model 列表（原始数据）
     * @param formMap                 表单 ID -> BpmFormDO 的映射（key: formId）
     * @param categoryMap             分类 code -> BpmCategoryDO 的映射（Flowable 的 category 字段对应分类 code）
     * @param deploymentMap           部署 ID -> Deployment 的映射
     * @param processDefinitionMap    部署 ID -> ProcessDefinition 的映射（注意：一个部署对应一个流程定义）
     * @param userMap                 用户 ID -> AdminUserRespDTO 的映射（用于 startUsers）
     * @param deptMap                 部门 ID -> DeptRespDTO 的映射（用于 startDepts）
     * @return 转换后的 BpmModelRespVO 列表，按 sort 字段升序排序
     */
    default List<BpmModelRespVO> buildModelList(
            List<Model> list,
            Map<Long, BpmFormDO> formMap,
            Map<String, BpmCategoryDO> categoryMap,
            Map<String, Deployment> deploymentMap,
            Map<String, ProcessDefinition> processDefinitionMap,
            Map<Long, AdminUserRespDTO> userMap,
            Map<Long, DeptRespDTO> deptMap) {

        // 将每个 Model 转换为 BpmModelRespVO
        List<BpmModelRespVO> result = convertList(list, model -> {
            // 1. 从 model.metaInfo 字段解析出自定义元信息（JSON 字符串）
            BpmModelMetaInfoVO metaInfo = parseMetaInfo(model);

            // 2. 根据 metaInfo 中的 formId 获取对应的表单
            BpmFormDO form = (metaInfo != null) ? formMap.get(metaInfo.getFormId()) : null;

            // 3. 根据 model.category 获取分类（注意：category 存的是分类 code，不是 ID）
            BpmCategoryDO category = categoryMap.get(model.getCategory());

            // 4. 获取部署信息（只有已部署的模型才有 deploymentId）
            Deployment deployment = (model.getDeploymentId() != null) ?
                    deploymentMap.get(model.getDeploymentId()) : null;

            // 5. 获取流程定义（通过 deploymentId 关联）
            ProcessDefinition processDefinition = (model.getDeploymentId() != null) ?
                    processDefinitionMap.get(model.getDeploymentId()) : null;

            // 6. 获取允许启动流程的用户列表（根据 metaInfo 中的用户 ID 列表）
            List<AdminUserRespDTO> startUsers = (metaInfo != null) ?
                    convertList(metaInfo.getStartUserIds(), userMap::get) : null;

            // 7. 获取允许启动流程的部门列表
            List<DeptRespDTO> startDepts = (metaInfo != null) ?
                    convertList(metaInfo.getStartDeptIds(), deptMap::get) : null;

            // 8. 调用内部方法组装单个 VO
            return buildModel0(model, metaInfo, form, category, deployment, processDefinition, startUsers, startDepts);
        });

        // 按照 metaInfo 中的 sort 字段排序（若未设置，则使用 createTime 作为兜底）
        // 注意：这里假设所有 model 都有 metaInfo（parseMetaInfo 会兜底）
        result.sort(Comparator.comparing(BpmModelMetaInfoVO::getSort));

        return result;
    }

    /**
     * 构建单个模型详情（用于详情页）
     * <p>
     * 与 buildModelList 不同，此方法额外传入了 BPMN 字节数据和简化节点信息。
     *
     * @param model        Flowable 的 Model 对象
     * @param bpmnBytes    BPMN 文件的字节数组（用于生成 XML 展示）
     * @param simpleModel  简化的流程节点信息（前端流程图用）
     * @return 包含完整信息的 BpmModelRespVO
     */
    default BpmModelRespVO buildModel(Model model, byte[] bpmnBytes, BpmSimpleModelNodeVO simpleModel) {
        // 解析元信息
        BpmModelMetaInfoVO metaInfo = parseMetaInfo(model);

        // 先构建基础 VO（其他关联数据为 null）
        BpmModelRespVO modelVO = buildModel0(model, metaInfo, null, null, null, null, null, null);

        // 如果有 BPMN 字节数据，转为 XML 字符串（用于前端展示流程图）
        if (ArrayUtil.isNotEmpty(bpmnBytes)) {
            modelVO.setBpmnXml(BpmnModelUtils.getBpmnXml(bpmnBytes));
        }

        // 设置简化节点信息（用于流程图渲染）
        modelVO.setSimpleModel(simpleModel);

        return modelVO;
    }

    /**
     * 私有方法：实际组装单个 BpmModelRespVO 的核心逻辑
     * <p>
     * 所有转换最终都会调用此方法。
     */
    default BpmModelRespVO buildModel0(
            Model model,
            BpmModelMetaInfoVO metaInfo,
            BpmFormDO form,
            BpmCategoryDO category,
            Deployment deployment,
            ProcessDefinition processDefinition,
            List<AdminUserRespDTO> startUsers,
            List<DeptRespDTO> startDepts) {

        // 创建响应对象，并设置 Flowable Model 基础字段
        BpmModelRespVO modelRespVO = new BpmModelRespVO()
                .setId(model.getId())          // 模型 ID（Flowable 生成）
                .setName(model.getName())      // 模型名称
                .setKey(model.getKey())        // 模型 Key（唯一标识）
                .setCategory(model.getCategory()) // 分类 code
                .setCreateTime(DateUtils.of(model.getCreateTime())); // 创建时间（转为 LocalDateTime）

        // 将 metaInfo 中的字段（如 formId、startUserIds 等）拷贝到 VO 中
        // 注意：metaInfo 是 BpmModelMetaInfoVO 类型，modelRespVO 也包含这些字段
        if (metaInfo != null) {
            BeanUtils.copyProperties(metaInfo, modelRespVO);
        }

        // 设置表单名称（如果存在）
        if (form != null) {
            modelRespVO.setFormName(form.getName());
        }

        // 设置分类名称
        if (category != null) {
            modelRespVO.setCategoryName(category.getName());
        }

        // 设置流程定义信息（仅当模型已部署）
        if (processDefinition != null) {
            // 使用 BeanUtils 将 ProcessDefinition 转为 BpmProcessDefinitionRespVO
            modelRespVO.setProcessDefinition(
                    BeanUtils.toBean(processDefinition, BpmProcessDefinitionRespVO.class)
            );

            // 设置流程状态：挂起（SUSPENDED）或激活（ACTIVE）
            modelRespVO.getProcessDefinition().setSuspensionState(
                    processDefinition.isSuspended() ?
                            SuspensionState.SUSPENDED.getStateCode() :
                            SuspensionState.ACTIVE.getStateCode()
            );

            // 设置部署时间（从 Deployment 对象获取）
            if (deployment != null) {
                modelRespVO.getProcessDefinition().setDeploymentTime(
                        DateUtils.of(deployment.getDeploymentTime())
                );
            }
        }

        // 转换启动用户列表为简要 VO（只保留 id、name 等基本信息）
        modelRespVO.setStartUsers(
                BeanUtils.toBean(startUsers, UserSimpleBaseVO.class)
        );

        // 转换启动部门列表为简要 VO
        modelRespVO.setStartDepts(
                BeanUtils.toBean(startDepts, DeptSimpleBaseVO.class)
        );

        return modelRespVO;
    }

    /**
     * 将前端保存请求（BpmModelSaveReqVO）的数据复制到 Flowable 的 Model 对象中
     * <p>
     * 注意：metaInfo 字段是以 JSON 字符串形式存储在 Model 中的。
     *
     * @param model   Flowable 的 Model 对象（将被修改）
     * @param reqVO   前端传入的保存请求
     */
    default void copyToModel(Model model, BpmModelSaveReqVO reqVO) {
        model.setName(reqVO.getName());
        model.setKey(reqVO.getKey());
        model.setCategory(reqVO.getCategory());

        // 将 reqVO 转为 BpmModelMetaInfoVO，再序列化为 JSON 字符串存入 metaInfo
        model.setMetaInfo(JsonUtils.toJsonString(
                BeanUtils.toBean(reqVO, BpmModelMetaInfoVO.class)
        ));
    }

    /**
     * 从 Flowable Model 的 metaInfo 字段（JSON 字符串）解析出 BpmModelMetaInfoVO 对象
     * <p>
     * 同时进行空值兜底处理，避免 NPE。
     *
     * @param model Flowable 的 Model 对象
     * @return 解析后的元信息对象，可能为 null（如果 metaInfo 为空或解析失败）
     */
    default BpmModelMetaInfoVO parseMetaInfo(Model model) {
        // 尝试将 metaInfo 字符串反序列化为 VO
        BpmModelMetaInfoVO vo = JsonUtils.parseObject(model.getMetaInfo(), BpmModelMetaInfoVO.class);
        if (vo == null) {
            return null;
        }

        // 避免空指针：如果列表为 null，设为空列表
        if (vo.getManagerUserIds() == null) {
            vo.setManagerUserIds(Collections.emptyList());
        }
        if (vo.getStartUserIds() == null) {
            vo.setStartUserIds(Collections.emptyList());
        }

        // 如果 sort 字段未设置，使用创建时间的时间戳作为排序依据（保证有值）
        if (vo.getSort() == null) {
            vo.setSort(model.getCreateTime().getTime());
        }

        return vo;
    }

}