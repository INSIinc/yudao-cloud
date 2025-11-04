package cn.iocoder.yudao.module.bpm.controller.admin.task;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.MapUtils;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.base.user.UserSimpleBaseVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.cc.BpmProcessInstanceCopyRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCopyPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceCopyDO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceCopyService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Stream;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 流程实例抄送控制器
 *
 * 这个类是一个 REST API 控制器，用于处理工作流中的"抄送"功能
 *
 * 什么是抄送？
 * 在工作流审批过程中，有时需要让某些人知道流程的进展，但不需要他们审批，
 * 这就是"抄送"。比如请假流程，可能会抄送给人事部门，让他们知道但不需要审批。
 *
 * 这个类的作用：
 * - 提供查询抄送给当前用户的流程列表的接口
 * - 返回分页数据，包含流程详情、发起人信息等
 */
@Tag(name = "管理后台 - 流程实例抄送")  // Swagger 文档标签，用于 API 文档分类
@RestController  // 表示这是一个 REST 风格的控制器，返回 JSON 数据
@RequestMapping("/bpm/process-instance/copy")  // 定义这个控制器的基础访问路径
@Validated  // 启用参数验证功能
public class BpmProcessInstanceCopyController {

    // ==================== 依赖注入的服务类 ====================
    // 使用 @Resource 注解自动注入需要的服务，类似于"拿来主义"，直接使用别人提供的功能

    /**
     * 流程实例抄送服务
     * 负责处理抄送相关的业务逻辑，比如查询抄送列表
     */
    @Resource
    private BpmProcessInstanceCopyService processInstanceCopyService;

    /**
     * 流程实例服务
     * 负责处理流程实例的相关操作，比如获取流程的历史记录
     */
    @Resource
    private BpmProcessInstanceService processInstanceService;

    /**
     * 流程定义服务
     * 负责获取流程定义的信息，比如流程名称、流程配置等
     */
    @Resource
    private BpmProcessDefinitionService processDefinitionService;

    /**
     * 用户管理 API
     * 负责获取用户信息，比如用户名、部门等
     */
    @Resource
    private AdminUserApi adminUserApi;

    // ==================== API 接口方法 ====================

    /**
     * 获取抄送流程分页列表
     *
     * 这个方法的作用：
     * 1. 查询抄送给当前登录用户的所有流程
     * 2. 获取这些流程的详细信息（流程实例、发起人、流程定义等）
     * 3. 组装成前端需要的数据格式并返回
     *
     * @param pageReqVO 分页请求参数，包含页码、每页条数、查询条件等
     * @return 返回分页结果，包含抄送流程列表和总数
     */
    @GetMapping("/page")  // 定义 GET 请求路径：/bpm/process-instance/copy/page
    @Operation(summary = "获得抄送流程分页列表")  // Swagger 文档说明
    @PreAuthorize("@ss.hasPermission('bpm:process-instance-cc:query')")  // 权限校验：必须有查询抄送流程的权限
    public CommonResult<PageResult<BpmProcessInstanceCopyRespVO>> getProcessInstanceCopyPage(
            @Valid BpmProcessInstanceCopyPageReqVO pageReqVO) {  // @Valid 表示会自动验证参数是否合法

        // 第一步：查询抄送给当前用户的流程列表（从数据库获取基础数据）
        // getLoginUserId() 获取当前登录用户的 ID
        PageResult<BpmProcessInstanceCopyDO> pageResult = processInstanceCopyService.getProcessInstanceCopyPage(
                getLoginUserId(), pageReqVO);

        // 第二步：如果查询结果为空，直接返回空列表
        // 这是一种性能优化，避免后续不必要的数据查询
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(new PageResult<>(pageResult.getTotal()));
        }

        // 第三步：组装返回数据（关联查询相关信息）
        // 因为数据库中只存储了 ID，需要根据 ID 查询完整的信息

        // 3.1 获取流程实例的历史信息
        // 将抄送记录列表转换为流程实例 ID 的集合，然后批量查询流程实例信息
        // 为什么用 Map？方便后面根据流程实例 ID 快速查找对应的流程实例对象
        Map<String, HistoricProcessInstance> processInstanceMap = processInstanceService.getHistoricProcessInstanceMap(
                convertSet(pageResult.getList(), BpmProcessInstanceCopyDO::getProcessInstanceId));

        // 3.2 获取相关用户信息（包括流程发起人和抄送创建人）
        // convertListByFlatMap：将列表中的每个抄送记录转换为两个用户 ID（发起人 ID 和创建人 ID）
        // Stream.of：创建包含这两个 ID 的流
        // 最终得到所有相关用户的 ID 列表，然后批量查询用户信息
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(convertListByFlatMap(pageResult.getList(),
                copy -> Stream.of(copy.getStartUserId(), Long.parseLong(copy.getCreator()))));

        // 3.3 获取流程定义信息
        // 流程定义包含流程的名称、配置等信息
        Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap = processDefinitionService.getProcessDefinitionInfoMap(
                convertSet(pageResult.getList(), BpmProcessInstanceCopyDO::getProcessDefinitionId));

        // 第四步：转换并组装最终返回的数据
        // convertPage：将分页对象中的每一条数据进行转换
        return success(convertPage(pageResult, copy -> {
            // 4.1 将数据库对象（DO）转换为返回对象（VO）
            // VO 是专门用于 API 返回的对象，包含前端需要的所有字段
            BpmProcessInstanceCopyRespVO copyVO = BeanUtils.toBean(copy, BpmProcessInstanceCopyRespVO.class);

            // 4.2 设置流程发起人信息
            // MapUtils.findAndThen：如果在 Map 中找到了对应的用户，就执行后面的操作
            // 这里有个字段命名问题：setStartUser 实际设置的是创建抄送的人（创建人）
            MapUtils.findAndThen(userMap, Long.valueOf(copy.getCreator()),
                    user -> copyVO.setStartUser(BeanUtils.toBean(user, UserSimpleBaseVO.class)));

            // 4.3 设置抄送创建人信息
            // 这里有个字段命名问题：setCreateUser 实际设置的是流程发起人
            MapUtils.findAndThen(userMap, copy.getStartUserId(),
                    user -> copyVO.setCreateUser(BeanUtils.toBean(user, UserSimpleBaseVO.class)));

            // 4.4 设置流程实例相关信息
            MapUtils.findAndThen(processInstanceMap, copyVO.getProcessInstanceId(),
                    processInstance -> {
                        // 4.4.1 生成流程摘要
                        // 摘要是对流程内容的简短描述，方便用户快速了解流程内容
                        // 根据流程定义和流程变量生成摘要文本
                        copyVO.setSummary(FlowableUtils.getSummary(
                                processDefinitionInfoMap.get(processInstance.getProcessDefinitionId()),
                                processInstance.getProcessVariables()));

                        // 4.4.2 设置流程实例的开始时间
                        // DateUtils.of：将 Date 对象转换为 LocalDateTime 对象
                        copyVO.setProcessInstanceStartTime(DateUtils.of(processInstance.getStartTime()));
                    });

            // 4.5 返回组装好的对象
            return copyVO;
        }));
    }

}
