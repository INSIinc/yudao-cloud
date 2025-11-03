package cn.iocoder.yudao.module.bpm.controller.admin.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.base.user.UserSimpleBaseVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.*;
import cn.iocoder.yudao.module.bpm.convert.task.BpmProcessInstanceConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmCategoryDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.service.definition.BpmCategoryService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_NOT_EXISTS;

/**
 * 管理后台 - 流程实例控制器
 * <p>
 * 流程实例（Process Instance）：由某个流程定义（Process Definition）启动的一次具体执行。
 * 例如：用户提交的一次请假申请，即为一个流程实例。
 */
@Tag(name = "管理后台 - 流程实例")
@RestController
@RequestMapping("/bpm/process-instance")
@Validated
public class BpmProcessInstanceController {

    @Resource
    private BpmProcessInstanceService processInstanceService;
    @Resource
    private BpmTaskService taskService;
    @Resource
    private BpmProcessDefinitionService processDefinitionService;
    @Resource
    private BpmCategoryService categoryService;

    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;

    // ==================== 我的流程 ====================

    /**
     * 获取当前登录用户的“我的流程”分页列表（即自己发起或参与的流程实例）
     *
     * @param pageReqVO 分页查询条件（包含流程定义 key、状态、时间范围等）
     * @return 分页结果，包含流程实例及其关联的任务、发起人、流程定义、分类等信息
     */
    @GetMapping("/my-page")
    @Operation(summary = "获得我的实例分页列表", description = "在【我的流程】菜单中调用，仅显示当前用户发起或参与的流程")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    public CommonResult<PageResult<BpmProcessInstanceRespVO>> getProcessInstanceMyPage(
            @Valid BpmProcessInstancePageReqVO pageReqVO) {
        // 1. 查询当前用户相关的流程实例（包括自己发起的和自己参与的）
        Long loginUserId = getLoginUserId();
        PageResult<HistoricProcessInstance> pageResult = processInstanceService.getProcessInstancePage(loginUserId, pageReqVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty(pageResult.getTotal()));
        }

        // 2. 批量获取关联数据，减少数据库/远程调用次数
        List<String> instanceIds = convertList(pageResult.getList(), HistoricProcessInstance::getId);
        Set<String> definitionIds = convertSet(pageResult.getList(), HistoricProcessInstance::getProcessDefinitionId);

        // 当前流程实例下所有任务（用于展示待办/已办状态）
        Map<String, List<Task>> taskMap = taskService.getTaskMapByProcessInstanceIds(instanceIds);
        // 流程定义信息（名称、key 等）
        Map<String, ProcessDefinition> processDefinitionMap = processDefinitionService.getProcessDefinitionMap(definitionIds);
        // 分类信息（如：人事、财务等）
        Set<String> categoryKeys = convertSet(processDefinitionMap.values(), ProcessDefinition::getCategory);
        Map<String, BpmCategoryDO> categoryMap = categoryService.getCategoryMap(categoryKeys);
        // 流程定义扩展信息（如表单配置）
        Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap = processDefinitionService.getProcessDefinitionInfoMap(definitionIds);

        // 3. 收集所有涉及的用户 ID（发起人 + 所有任务处理人）
        Set<Long> userIds = convertSet(pageResult.getList(), processInstance ->
                NumberUtils.parseLong(processInstance.getStartUserId()));
        userIds.addAll(convertSetByFlatMap(taskMap.values(),
                tasks -> tasks.stream()
                        .map(Task::getAssignee)
                        .filter(StrUtil::isNotBlank)
                        .map(Long::parseLong)));

        // 4. 获取用户与部门信息
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(userIds);
        Set<Long> deptIds = convertSet(userMap.values(), AdminUserRespDTO::getDeptId);
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(deptIds);

        // 5. 转换为前端 VO
        return success(BpmProcessInstanceConvert.INSTANCE.buildProcessInstancePage(
                pageResult, processDefinitionMap, categoryMap, taskMap, userMap, deptMap, processDefinitionInfoMap));
    }

    // ==================== 管理员查看所有流程 ====================

    /**
     * 获取所有流程实例的分页列表（仅限管理员或有权限人员查看）
     *
     * @param pageReqVO 分页查询条件
     * @return 所有流程实例（不受当前用户限制）
     */
    @GetMapping("/manager-page")
    @Operation(summary = "获得管理流程实例的分页列表", description = "在【流程实例】菜单中调用，可查看全量流程")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:manager-query')")
    public CommonResult<PageResult<BpmProcessInstanceRespVO>> getProcessInstanceManagerPage(
            @Valid BpmProcessInstancePageReqVO pageReqVO) {
        // 注意：userId 传 null 表示不限制用户
        PageResult<HistoricProcessInstance> pageResult = processInstanceService.getProcessInstancePage(null, pageReqVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty(pageResult.getTotal()));
        }

        List<String> instanceIds = convertList(pageResult.getList(), HistoricProcessInstance::getId);
        Set<String> definitionIds = convertSet(pageResult.getList(), HistoricProcessInstance::getProcessDefinitionId);

        Map<String, List<Task>> taskMap = taskService.getTaskMapByProcessInstanceIds(instanceIds);
        Map<String, ProcessDefinition> processDefinitionMap = processDefinitionService.getProcessDefinitionMap(definitionIds);
        Set<String> categoryKeys = convertSet(processDefinitionMap.values(), ProcessDefinition::getCategory);
        Map<String, BpmCategoryDO> categoryMap = categoryService.getCategoryMap(categoryKeys);
        Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap = processDefinitionService.getProcessDefinitionInfoMap(definitionIds);

        // 仅需发起人信息（管理员视角通常不关心所有任务处理人细节）
        Set<Long> userIds = convertSet(pageResult.getList(), processInstance ->
                NumberUtils.parseLong(processInstance.getStartUserId()));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(userIds);
        Set<Long> deptIds = convertSet(userMap.values(), AdminUserRespDTO::getDeptId);
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(deptIds);

        return success(BpmProcessInstanceConvert.INSTANCE.buildProcessInstancePage(
                pageResult, processDefinitionMap, categoryMap, taskMap, userMap, deptMap, processDefinitionInfoMap));
    }

    // ==================== 新建流程实例 ====================

    /**
     * 新建一个流程实例（即发起一个流程）
     *
     * @param createReqVO 包含流程定义 key、业务 key、表单变量等
     * @return 返回新创建的流程实例 ID
     */
    @PostMapping("/create")
    @Operation(summary = "新建流程实例")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    public CommonResult<String> createProcessInstance(@Valid @RequestBody BpmProcessInstanceCreateReqVO createReqVO) {
        String processInstanceId = processInstanceService.createProcessInstance(getLoginUserId(), createReqVO);
        return success(processInstanceId);
    }

    // ==================== 获取单个流程实例 ====================

    /**
     * 根据 ID 获取指定流程实例详情
     *
     * @param id 流程实例 ID
     * @return 流程实例详情（含流程定义、发起人、部门等）
     */
    @GetMapping("/get")
    @Operation(summary = "获得指定流程实例", description = "用于【流程详细】页面")
    @Parameter(name = "id", description = "流程实例的编号", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    public CommonResult<BpmProcessInstanceRespVO> getProcessInstance(@RequestParam("id") String id) {
        HistoricProcessInstance processInstance = processInstanceService.getHistoricProcessInstance(id);
        if (processInstance == null) {
            return success(null); // 实例不存在，返回 null 而非异常（便于前端处理）
        }

        // 获取关联信息
        ProcessDefinition processDefinition = processDefinitionService.getProcessDefinition(
                processInstance.getProcessDefinitionId());
        BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService.getProcessDefinitionInfo(
                processInstance.getProcessDefinitionId());
        AdminUserRespDTO startUser = adminUserApi.getUser(NumberUtils.parseLong(processInstance.getStartUserId())).getCheckedData();
        DeptRespDTO dept = null;
        if (startUser != null && startUser.getDeptId() != null) {
            dept = deptApi.getDept(startUser.getDeptId()).getCheckedData();
        }

        return success(BpmProcessInstanceConvert.INSTANCE.buildProcessInstance(
                processInstance, processDefinition, processDefinitionInfo, startUser, dept));
    }

    // ==================== 取消流程（用户） ====================

    /**
     * 当前用户取消自己发起的流程实例（如撤回申请）
     *
     * @param cancelReqVO 包含流程实例 ID 和取消原因
     */
    @DeleteMapping("/cancel-by-start-user")
    @Operation(summary = "用户取消流程实例", description = "仅允许发起人取消")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:cancel')")
    public CommonResult<Boolean> cancelProcessInstanceByStartUser(
            @Valid @RequestBody BpmProcessInstanceCancelReqVO cancelReqVO) {
        processInstanceService.cancelProcessInstanceByStartUser(getLoginUserId(), cancelReqVO);
        return success(true);
    }

    // ==================== 取消流程（管理员） ====================

    /**
     * 管理员强制取消任意流程实例（如异常处理）
     *
     * @param cancelReqVO 包含流程实例 ID 和取消原因
     */
    @DeleteMapping("/cancel-by-admin")
    @Operation(summary = "管理员取消流程实例", description = "管理员可撤回任意流程")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:cancel-by-admin')")
    public CommonResult<Boolean> cancelProcessInstanceByManager(
            @Valid @RequestBody BpmProcessInstanceCancelReqVO cancelReqVO) {
        processInstanceService.cancelProcessInstanceByAdmin(getLoginUserId(), cancelReqVO);
        return success(true);
    }

    // ==================== 审批详情 ====================

    /**
     * 获取流程审批详情（用于审批页面动态展示表单和节点）
     *
     * @param reqVO 包含流程实例 ID、节点 ID、流程变量（JSON 字符串）
     * @return 审批详情，包括当前节点、历史节点、表单数据等
     */
    @GetMapping("/get-approval-detail")
    @Operation(summary = "获得审批详情")
    @Parameter(name = "id", description = "流程实例的编号", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    @SuppressWarnings("unchecked")
    public CommonResult<BpmApprovalDetailRespVO> getApprovalDetail(@Valid BpmApprovalDetailReqVO reqVO) {
        // 如果前端传的是 JSON 字符串，则解析为 Map
        if (StrUtil.isNotEmpty(reqVO.getProcessVariablesStr())) {
            reqVO.setProcessVariables(JsonUtils.parseObject(reqVO.getProcessVariablesStr(), Map.class));
        }
        return success(processInstanceService.getApprovalDetail(getLoginUserId(), reqVO));
    }

    // ==================== 下一个审批节点 ====================

    /**
     * 根据当前流程状态和变量，预测下一个可能执行的审批节点（用于动态表单预览或校验）
     *
     * @param reqVO 请求参数，含流程实例 ID 和变量
     * @return 可能的下一个节点列表
     */
    @GetMapping("/get-next-approval-nodes")
    @Operation(summary = "获取下一个执行的流程节点")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    @SuppressWarnings("unchecked")
    public CommonResult<List<BpmApprovalDetailRespVO.ActivityNode>> getNextApprovalNodes(@Valid BpmApprovalDetailReqVO reqVO) {
        if (StrUtil.isNotEmpty(reqVO.getProcessVariablesStr())) {
            reqVO.setProcessVariables(JsonUtils.parseObject(reqVO.getProcessVariablesStr(), Map.class));
        }
        return success(processInstanceService.getNextApprovalNodes(getLoginUserId(), reqVO));
    }

    // ==================== BPMN 模型视图 ====================

    /**
     * 获取流程实例对应的 BPMN 模型（XML 转换为前端可渲染的 JSON 结构）
     *
     * @param id 流程实例 ID
     * @return BPMN 模型视图数据
     */
    @GetMapping("/get-bpmn-model-view")
    @Operation(summary = "获取流程实例的 BPMN 模型视图", description = "用于流程图展示")
    @Parameter(name = "id", description = "流程实例的编号", required = true)
    public CommonResult<BpmProcessInstanceBpmnModelViewRespVO> getProcessInstanceBpmnModelView(
            @RequestParam(value = "id") String id) {
        return success(processInstanceService.getProcessInstanceBpmnModelView(id));
    }

    // ==================== 打印数据 ====================

    /**
     * 获取流程实例的打印数据（用于生成 PDF 或打印审批单）
     *
     * @param processInstanceId 流程实例 ID
     * @return 包含发起人、部门、所有已完成任务及处理人信息的打印数据
     */
    @GetMapping("/get-print-data")
    @Operation(summary = "获得流程实例的打印数据")
    @Parameter(name = "id", description = "流程实例的编号", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    public CommonResult<BpmProcessPrintDataRespVO> getProcessInstancePrintData(
            @RequestParam("processInstanceId") String processInstanceId) {
        HistoricProcessInstance historicProcessInstance = processInstanceService.getHistoricProcessInstance(processInstanceId);
        if (historicProcessInstance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS); // 打印必须存在实例，故抛异常
        }

        // 获取发起人及部门
        AdminUserRespDTO startUser = adminUserApi.getUser(Long.valueOf(historicProcessInstance.getStartUserId())).getCheckedData();
        DeptRespDTO dept = deptApi.getDept(startUser.getDeptId()).getCheckedData();

        // 获取所有已完成任务（排除已取消的）
        List<HistoricTaskInstance> tasks = taskService.getFinishedTaskListByProcessInstanceIdWithoutCancel(processInstanceId);

        // 获取所有任务处理人信息
        Set<Long> assigneeUserIds = convertSet(tasks, item -> Long.valueOf(item.getAssignee()));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(assigneeUserIds);

        // 构建打印 VO
        UserSimpleBaseVO starter = new UserSimpleBaseVO()
                .setNickname(startUser.getNickname())
                .setDeptName(dept.getName());

        return success(BpmProcessInstanceConvert.INSTANCE.buildProcessInstancePrintData(
                historicProcessInstance,
                processDefinitionService.getProcessDefinitionInfo(historicProcessInstance.getProcessDefinitionId()),
                tasks, userMap, starter));
    }

}