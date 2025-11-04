package cn.iocoder.yudao.module.bpm.controller.admin.definition;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.*;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelUpdateReqVO;
import cn.iocoder.yudao.module.bpm.convert.definition.BpmModelConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmCategoryDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;
import cn.iocoder.yudao.module.bpm.service.definition.BpmCategoryService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmFormService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.Model;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 流程模型管理控制器（管理后台使用）
 * 负责对 Flowable 流程模型（Model）进行增删改查、部署、状态管理等操作。
 * Flowable 是一个开源的工作流引擎，Model 代表流程的设计草稿。
 */
@Tag(name = "管理后台 - 流程模型") // Swagger 分组标签
@RestController
@RequestMapping("/bpm/model") // 所有接口的基础路径
@Validated // 启用参数校验（配合 @Valid 使用）
public class BpmModelController {

    // 注入需要的服务类（业务逻辑层）
    @Resource
    private BpmModelService modelService;           // 流程模型核心服务
    @Resource
    private BpmFormService formService;             // 表单服务（流程关联的表单）
    @Resource
    private BpmCategoryService categoryService;     // 流程分类服务
    @Resource
    private BpmProcessDefinitionService processDefinitionService; // 流程定义服务（部署后的流程）

    // 系统模块的远程 API：用于获取用户和部门信息
    @Resource
    private AdminUserApi adminUserApi; // 获取用户信息
    @Resource
    private DeptApi deptApi;           // 获取部门信息

    // ==================== 查询操作 ====================

    /**
     * 获取流程模型列表（支持根据名称模糊查询）
     * 此方法不仅返回 Flowable 的 Model 对象，还会关联查询：
     * - 表单信息
     * - 分类信息
     * - 部署信息（Deployment）
     * - 流程定义信息（ProcessDefinition）
     * - 启动人（用户）和启动部门信息
     */
    @GetMapping("/list")
    @Operation(summary = "获得模型分页")
    @Parameter(name = "name", description = "模型名称", example = "请假流程")
    public CommonResult<List<BpmModelRespVO>> getModelList(@RequestParam(value = "name", required = false) String name) {
        // 1. 调用服务层获取 Flowable 的 Model 列表
        List<Model> list = modelService.getModelList(name);
        if (CollUtil.isEmpty(list)) {
            return success(Collections.emptyList()); // 无数据则返回空列表
        }

        // 2. 提取所有模型关联的表单 ID，并查询表单信息
        Set<Long> formIds = convertSet(list, model -> {
            BpmModelMetaInfoVO metaInfo = BpmModelConvert.INSTANCE.parseMetaInfo(model);
            return metaInfo != null ? metaInfo.getFormId() : null;
        });
        Map<Long, BpmFormDO> formMap = formService.getFormMap(formIds);

        // 3. 提取所有模型的 category（分类编码），并查询分类信息
        Map<String, BpmCategoryDO> categoryMap = categoryService.getCategoryMap(
                convertSet(list, Model::getCategory)
        );

        // 4. 提取所有模型的 deploymentId（部署ID），并查询部署信息
        Map<String, Deployment> deploymentMap = processDefinitionService.getDeploymentMap(
                convertSet(list, Model::getDeploymentId)
        );

        // 5. 根据 deploymentId 查询对应的流程定义（ProcessDefinition）
        List<ProcessDefinition> processDefinitions = processDefinitionService.getProcessDefinitionListByDeploymentIds(
                deploymentMap.keySet()
        );
        Map<String, ProcessDefinition> processDefinitionMap = convertMap(processDefinitions, ProcessDefinition::getDeploymentId);

        // 6. 提取所有“可启动该流程的用户ID”，并查询用户信息
        Set<Long> userIds = convertSetByFlatMap(list, model -> {
            BpmModelMetaInfoVO metaInfo = BpmModelConvert.INSTANCE.parseMetaInfo(model);
            return metaInfo != null ? metaInfo.getStartUserIds().stream() : Stream.empty();
        });
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(userIds);

        // 7. 提取所有“可启动该流程的部门ID”，并查询部门信息
        Set<Long> deptIds = convertSetByFlatMap(list, model -> {
            BpmModelMetaInfoVO metaInfo = BpmModelConvert.INSTANCE.parseMetaInfo(model);
            return metaInfo != null && metaInfo.getStartDeptIds() != null ? metaInfo.getStartDeptIds().stream() : Stream.empty();
        });
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(deptIds);

        // 8. 使用转换器将原始数据组装成前端需要的 VO 对象
        return success(BpmModelConvert.INSTANCE.buildModelList(
                list, formMap, categoryMap, deploymentMap, processDefinitionMap, userMap, deptMap
        ));
    }

    /**
     * 根据 ID 获取单个流程模型的详细信息
     * 返回包含 BPMN XML 内容和简化版节点结构的数据
     */
    @GetMapping("/get")
    @Operation(summary = "获得模型")
    @Parameter(name = "id", description = "模型 ID（Flowable 的 Model ID）", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('bpm:model:query')") // 需要 'bpm:model:query' 权限
    public CommonResult<BpmModelRespVO> getModel(@RequestParam("id") String id) {
        Model model = modelService.getModel(id);
        if (model == null) {
            // 注意：这里原代码返回 null，但应返回 success(null) 或抛异常更合理
            // 实际项目中建议改为 return success(null); 或抛出业务异常
            return success(null);
        }
        // 获取 BPMN 的 XML 字节数据（用于流程图展示）
        byte[] bpmnBytes = modelService.getModelBpmnXML(id);
        // 获取简化版流程节点结构（用于类似钉钉的流程设计器）
        BpmSimpleModelNodeVO simpleModel = modelService.getSimpleModel(id);
        // 转换并返回结果
        return success(BpmModelConvert.INSTANCE.buildModel(model, bpmnBytes, simpleModel));
    }

    // ==================== 增删改操作 ====================

    /**
     * 新建一个流程模型（草稿）
     */
    @PostMapping("/create")
    @Operation(summary = "新建模型")
    @PreAuthorize("@ss.hasPermission('bpm:model:create')") // 需要创建权限
    public CommonResult<String> createModel(@Valid @RequestBody BpmModelSaveReqVO createRetVO) {
        // 调用服务层创建模型，返回新模型的 ID
        return success(modelService.createModel(createRetVO));
    }

    /**
     * 更新流程模型（修改模型基本信息，如名称、分类、启动人等）
     */
    @PutMapping("/update")
    @Operation(summary = "修改模型")
    @PreAuthorize("@ss.hasPermission('bpm:model:update')") // 需要更新权限
    public CommonResult<Boolean> updateModel(@Valid @RequestBody BpmModelSaveReqVO modelVO) {
        // 传入当前登录用户 ID，用于记录操作人
        modelService.updateModel(getLoginUserId(), modelVO);
        return success(true);
    }

    /**
     * 批量更新模型的排序（按传入的 ID 顺序依次设为 1, 2, 3...）
     */
    @PutMapping("/update-sort-batch")
    @Operation(summary = "批量修改模型排序")
    @Parameter(name = "ids", description = "模型 ID 列表，顺序即排序顺序", required = true, example = "id1,id2,id3")
    public CommonResult<Boolean> updateModelSortBatch(@RequestParam("ids") List<String> ids) {
        modelService.updateModelSortBatch(getLoginUserId(), ids);
        return success(true);
    }

    /**
     * 部署流程模型：将设计好的模型发布为可运行的流程定义
     */
    @PostMapping("/deploy")
    @Operation(summary = "部署模型")
    @Parameter(name = "id", description = "模型 ID", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('bpm:model:deploy')") // 需要部署权限
    public CommonResult<Boolean> deployModel(@RequestParam("id") String id) {
        modelService.deployModel(getLoginUserId(), id);
        return success(true);
    }

    /**
     * 修改已部署流程的状态（如启用/停用）
     * 注意：实际修改的是 ProcessDefinition 的状态，不是 Model
     */
    @PutMapping("/update-state")
    @Operation(summary = "修改模型的状态", description = "实际更新的是已部署流程定义的状态")
    @PreAuthorize("@ss.hasPermission('bpm:model:update')")
    public CommonResult<Boolean> updateModelState(@Valid @RequestBody BpmModelUpdateStateReqVO reqVO) {
        modelService.updateModelState(getLoginUserId(), reqVO.getId(), reqVO.getState());
        return success(true);
    }

    /**
     * 【已废弃】直接更新模型的 BPMN XML 内容
     * 不推荐使用，因为会绕过流程设计器的校验和转换逻辑
     */
    @Deprecated
    @PutMapping("/update-bpmn")
    @Operation(summary = "修改模型的 BPMN")
    @PreAuthorize("@ss.hasPermission('bpm:model:update')")
    public CommonResult<Boolean> updateModelBpmn(@Valid @RequestBody BpmModeUpdateBpmnReqVO reqVO) {
        modelService.updateModelBpmnXml(reqVO.getId(), reqVO.getBpmnXml());
        return success(true);
    }

    /**
     * 删除流程模型（草稿状态）
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除模型")
    @Parameter(name = "id", description = "模型 ID", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('bpm:model:delete')") // 需要删除权限
    public CommonResult<Boolean> deleteModel(@RequestParam("id") String id) {
        modelService.deleteModel(getLoginUserId(), id);
        return success(true);
    }

    /**
     * 清理模型：删除模型及相关部署、流程定义等（谨慎操作！）
     */
    @DeleteMapping("/clean")
    @Operation(summary = "清理模型")
    @Parameter(name = "id", description = "模型 ID", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('bpm:model:clean')") // 需要清理权限
    public CommonResult<Boolean> cleanModel(@RequestParam("id") String id) {
        modelService.cleanModel(getLoginUserId(), id);
        return success(true);
    }

    // ========== 仿钉钉/飞书的精简模型 ==========

    /**
     * 获取简化版流程模型结构（用于类似钉钉的可视化流程设计器）
     * 返回树形结构的节点数据，便于前端渲染
     */
    @GetMapping("/simple/get")
    @Operation(summary = "获得仿钉钉流程设计模型")
    @Parameter(name = "modelId", description = "流程模型编号", required = true, example = "a2c5eee0-eb6c-11ee-abf4-0c37967c420a")
    public CommonResult<BpmSimpleModelNodeVO> getSimpleModel(@RequestParam("id") String modelId) {
        return success(modelService.getSimpleModel(modelId));
    }

    /**
     * 【已废弃】保存简化版流程模型
     * 后续可能被更完善的设计器接口替代
     */
    @Deprecated
    @PostMapping("/simple/update")
    @Operation(summary = "保存仿钉钉流程设计模型")
    @PreAuthorize("@ss.hasPermission('bpm:model:update')")
    public CommonResult<Boolean> updateSimpleModel(@Valid @RequestBody BpmSimpleModelUpdateReqVO reqVO) {
        modelService.updateSimpleModel(getLoginUserId(), reqVO);
        return success(Boolean.TRUE);
    }

}