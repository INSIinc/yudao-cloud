// 包路径：该转换器位于 bpm 模块的 definition 包下，用于处理流程定义相关的数据转换
package cn.iocoder.yudao.module.bpm.convert.definition;

// 引入常用工具类
import cn.hutool.core.date.LocalDateTimeUtil;       // 日期时间工具类，用于时间格式转换
import cn.hutool.core.map.MapUtil;                  // Map 工具类，安全地从 Map 中获取值
import cn.iocoder.yudao.framework.common.pojo.PageResult; // 分页结果封装类
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils; // 集合工具类
import cn.iocoder.yudao.framework.common.util.object.BeanUtils; // Bean 对象拷贝工具
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.process.BpmProcessDefinitionRespVO; // 前端返回的 VO（View Object）
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmCategoryDO; // 流程分类数据对象
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;     // 表单数据对象
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO; // 自定义的流程定义扩展信息
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils; // 工具类：用于解析 BPMN 模型
import org.flowable.bpmn.model.BpmnModel;           // Flowable 的 BPMN 模型对象
import org.flowable.common.engine.impl.db.SuspensionState; // 流程挂起状态枚举
import org.flowable.engine.repository.Deployment;   // Flowable 部署对象
import org.flowable.engine.repository.ProcessDefinition; // Flowable 流程定义对象
import org.mapstruct.Mapper;                       // MapStruct 注解，用于编译期生成映射代码
import org.mapstruct.Mapping;                      // 字段映射配置
import org.mapstruct.MappingTarget;                // 指定目标对象用于更新（而非新建）
import org.mapstruct.factory.Mappers;              // MapStruct 工厂类，用于获取转换器实例

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Bpm 流程定义的 Convert（数据转换器）
 * <p>
 * 作用：将 Flowable 引擎中的 {@link ProcessDefinition} 对象，
 * 转换为前端需要的 {@link BpmProcessDefinitionRespVO} 对象。
 * 同时关联部署信息、表单、分类、自定义扩展信息等，组装成完整响应数据。
 *
 * @author yunlong.li
 */
@Mapper
public interface BpmProcessDefinitionConvert {

    // 使用 MapStruct 自动生成实现类，并通过静态字段提供全局单例访问
    BpmProcessDefinitionConvert INSTANCE = Mappers.getMapper(BpmProcessDefinitionConvert.class);

    /**
     * 将分页的 Flowable {@link ProcessDefinition} 列表，转换为分页的 VO 列表。
     *
     * @param page                      Flowable 返回的分页流程定义（原始数据）
     * @param deploymentMap             部署信息 Map，key = deploymentId，用于快速查找部署时间等
     * @param processDefinitionInfoMap  自定义流程定义信息 Map，key = processDefinitionId
     * @param formMap                   表单信息 Map，key = formId（Long）
     * @param categoryMap               流程分类信息 Map，key = categoryId（String）
     * @return 封装好的分页 VO 结果
     */
    default PageResult<BpmProcessDefinitionRespVO> buildProcessDefinitionPage(
            PageResult<ProcessDefinition> page,
            Map<String, Deployment> deploymentMap,
            Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap,
            Map<Long, BpmFormDO> formMap,
            Map<String, BpmCategoryDO> categoryMap) {
        // 先将分页中的每一条 ProcessDefinition 转换成 VO
        List<BpmProcessDefinitionRespVO> list = buildProcessDefinitionList(
                page.getList(), deploymentMap, processDefinitionInfoMap, formMap, categoryMap);
        // 返回新的分页结果，保持总条数不变
        return new PageResult<>(list, page.getTotal());
    }

    /**
     * 将 {@link ProcessDefinition} 列表转换为 {@link BpmProcessDefinitionRespVO} 列表。
     *
     * @param list                      流程定义列表
     * @param deploymentMap             部署信息缓存（减少数据库查询）
     * @param processDefinitionInfoMap  自定义流程信息缓存
     * @param formMap                   表单信息缓存
     * @param categoryMap               分类信息缓存
     * @return 转换后的 VO 列表
     */
    default List<BpmProcessDefinitionRespVO> buildProcessDefinitionList(
            List<ProcessDefinition> list,
            Map<String, Deployment> deploymentMap,
            Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap,
            Map<Long, BpmFormDO> formMap,
            Map<String, BpmCategoryDO> categoryMap) {
        // 使用工具类将 ProcessDefinition 列表转换为 VO 列表
        List<BpmProcessDefinitionRespVO> result = CollectionUtils.convertList(list, definition -> {
            // 1. 从缓存中获取部署信息（Deployment）
            Deployment deployment = MapUtil.get(deploymentMap, definition.getDeploymentId(), Deployment.class);

            // 2. 获取自定义的流程定义扩展信息（比如关联的表单 ID、排序等）
            BpmProcessDefinitionInfoDO processDefinitionInfo = MapUtil.get(
                    processDefinitionInfoMap, definition.getId(), BpmProcessDefinitionInfoDO.class);

            // 3. 根据扩展信息中的 formId 获取表单对象
            BpmFormDO form = null;
            if (processDefinitionInfo != null) {
                form = MapUtil.get(formMap, processDefinitionInfo.getFormId(), BpmFormDO.class);
            }

            // 4. 获取流程分类（如“请假流程”、“报销流程”）
            BpmCategoryDO category = MapUtil.get(categoryMap, definition.getCategory(), BpmCategoryDO.class);

            // 5. 调用核心转换方法，构建单个 VO
            return buildProcessDefinition(definition, deployment, processDefinitionInfo, form, category, null);
        });

        // 6. 按照 VO 中的 sort 字段进行排序（通常用于前端展示顺序）
        result.sort(Comparator.comparing(BpmProcessDefinitionRespVO::getSort));

        return result;
    }

    /**
     * 将单个 {@link ProcessDefinition} 及其关联对象，转换为一个 {@link BpmProcessDefinitionRespVO}。
     *
     * @param definition            Flowable 原生流程定义对象
     * @param deployment            对应的部署信息
     * @param processDefinitionInfo 自定义流程信息（用于补充 Flowable 不支持的字段）
     * @param form                  关联的表单
     * @param category              流程分类
     * @param bpmnModel             BPMN 模型（可选，用于导出 XML）
     * @return 构建好的 VO 对象
     */
    default BpmProcessDefinitionRespVO buildProcessDefinition(
            ProcessDefinition definition,
            Deployment deployment,
            BpmProcessDefinitionInfoDO processDefinitionInfo,
            BpmFormDO form,
            BpmCategoryDO category,
            BpmnModel bpmnModel) {

        // 1. 使用 BeanUtils 将 ProcessDefinition 的同名字段自动拷贝到 VO
        BpmProcessDefinitionRespVO respVO = BeanUtils.toBean(definition, BpmProcessDefinitionRespVO.class);

        // 2. 设置流程挂起状态（Flowable 中 isSuspended() 返回 boolean，这里转为状态码）
        respVO.setSuspensionState(
                definition.isSuspended() ? SuspensionState.SUSPENDED.getStateCode() : SuspensionState.ACTIVE.getStateCode()
        );

        // 3. 设置部署时间（从 Deployment 对象中获取）
        if (deployment != null) {
            // Flowable 的 deploymentTime 是 Date 类型，这里转换为 LocalDateTime
            respVO.setDeploymentTime(LocalDateTimeUtil.of(deployment.getDeploymentTime()));
        }

        // 4. 如果存在自定义流程信息，则拷贝扩展字段（如 sort、formId 等）
        if (processDefinitionInfo != null) {
            // 使用 MapStruct 的 copyTo 方法，将 processDefinitionInfo 的字段复制到 respVO
            copyTo(processDefinitionInfo, respVO);

            // 5. 如果关联了表单，则设置表单名称（用于前端展示）
            if (form != null) {
                respVO.setFormName(form.getName());
            }
        }

        // 6. 如果有分类信息，设置分类名称（如“人事类”、“财务类”）
        if (category != null) {
            respVO.setCategoryName(category.getName());
        }

        // 7. 如果传入了 BPMN 模型，将其转换为 XML 字符串（用于流程图展示或下载）
        if (bpmnModel != null) {
            respVO.setBpmnXml(BpmnModelUtils.getBpmnXml(bpmnModel));
        }

        return respVO;
    }

    /**
     * 使用 MapStruct 将 {@link BpmProcessDefinitionInfoDO} 的字段复制到 {@link BpmProcessDefinitionRespVO}。
     * <p>
     * 注意：这里忽略 id 字段的复制，因为 respVO 的 id 来自 Flowable 的 ProcessDefinition（即 definition.getId()），
     * 而 processDefinitionInfo 的 id 是数据库主键，两者含义不同。
     *
     * @param from 源对象：自定义流程定义信息
     * @param to   目标对象：要填充的 VO
     */
    @Mapping(source = "from.id", target = "to.id", ignore = true)
    void copyTo(BpmProcessDefinitionInfoDO from, @MappingTarget BpmProcessDefinitionRespVO to);

}