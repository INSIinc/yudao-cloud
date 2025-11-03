package cn.iocoder.yudao.module.bpm.convert.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.MapUtils;
import cn.iocoder.yudao.framework.common.util.collection.SetUtils;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.base.user.UserSimpleBaseVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelMetaInfoVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.process.BpmProcessDefinitionRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceBpmnModelViewRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessPrintDataRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRespVO;
import cn.iocoder.yudao.module.bpm.convert.definition.BpmProcessDefinitionConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmCategoryDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceApproveReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceRejectReqDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.*;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * 流程实例 (Process Instance) 转换器接口
 * 用于将 Flowable 引擎中的流程实例、任务等原始对象转换为前端需要的 VO 对象。
 *
 * @author 芋道源码
 */
@Mapper
public interface BpmProcessInstanceConvert {

    /**
     * MapStruct 实例，用于自动映射简单字段。
     */
    BpmProcessInstanceConvert INSTANCE = Mappers.getMapper(BpmProcessInstanceConvert.class);

    /**
     * 将分页查询结果（历史流程实例）转换为返回给前端的分页 VO。
     *
     * @param pageResult                 Flowable 查询的历史流程实例分页结果
     * @param processDefinitionMap       流程定义 Map，key 为 processDefinitionId
     * @param categoryMap                流程分类 Map，key 为 categoryId
     * @param taskMap                    当前流程实例关联的待办任务列表 Map，key 为 processInstanceId
     * @param userMap                    用户信息 Map，key 为 userId
     * @param deptMap                    部门信息 Map，key 为 deptId
     * @param processDefinitionInfoMap   流程定义扩展信息 Map，key 为 processDefinitionId
     * @return 转换后的分页 VO
     */
    default PageResult<BpmProcessInstanceRespVO> buildProcessInstancePage(
            PageResult<HistoricProcessInstance> pageResult,
            Map<String, ProcessDefinition> processDefinitionMap,
            Map<String, BpmCategoryDO> categoryMap,
            Map<String, List<Task>> taskMap,
            Map<Long, AdminUserRespDTO> userMap,
            Map<Long, DeptRespDTO> deptMap,
            Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap) {

        // 1. 基础字段映射
        PageResult<BpmProcessInstanceRespVO> vpPageResult = BeanUtils.toBean(pageResult, BpmProcessInstanceRespVO.class);

        // 2. 逐条处理，填充关联信息
        for (int i = 0; i < pageResult.getList().size(); i++) {
            BpmProcessInstanceRespVO respVO = vpPageResult.getList().get(i);
            HistoricProcessInstance historicProcessInstance = pageResult.getList().get(i);

            // 设置流程状态（根据结束时间等判断）
            respVO.setStatus(FlowableUtils.getProcessInstanceStatus(historicProcessInstance));

            // 填充流程定义信息
            MapUtils.findAndThen(processDefinitionMap, respVO.getProcessDefinitionId(), processDefinition -> {
                respVO.setCategory(processDefinition.getCategory())
                        .setProcessDefinition(BeanUtils.toBean(processDefinition, BpmProcessDefinitionRespVO.class));
            });

            // 填充分类名称
            MapUtils.findAndThen(categoryMap, respVO.getCategory(), category ->
                    respVO.setCategoryName(category.getName()));

            // 填充当前流程实例关联的待办任务（注意：此处可能为运行中任务）
            respVO.setTasks(BeanUtils.toBean(taskMap.get(respVO.getId()), BpmProcessInstanceRespVO.Task.class));

            // 填充发起人信息
            if (userMap != null) {
                AdminUserRespDTO startUser = userMap.get(NumberUtils.parseLong(historicProcessInstance.getStartUserId()));
                if (startUser != null) {
                    respVO.setStartUser(BeanUtils.toBean(startUser, UserSimpleBaseVO.class));
                    // 补充发起人部门名称
                    MapUtils.findAndThen(deptMap, startUser.getDeptId(), dept ->
                            respVO.getStartUser().setDeptName(dept.getName()));
                }

                // 填充每个任务的处理人信息
                if (CollUtil.isNotEmpty(respVO.getTasks())) {
                    respVO.getTasks().forEach(task -> {
                        AdminUserRespDTO assigneeUser = userMap.get(task.getAssignee());
                        if (assigneeUser != null) {
                            task.setAssigneeUser(BeanUtils.toBean(assigneeUser, UserSimpleBaseVO.class));
                            MapUtils.findAndThen(deptMap, assigneeUser.getDeptId(), dept ->
                                    task.getAssigneeUser().setDeptName(dept.getName()));
                        }
                    });
                }
            }

            // 设置流程摘要（基于表单配置和流程变量）
            respVO.setSummary(FlowableUtils.getSummary(
                    processDefinitionInfoMap.get(respVO.getProcessDefinitionId()),
                    historicProcessInstance.getProcessVariables()));

            // 设置流程变量（表单数据）
            respVO.setFormVariables(historicProcessInstance.getProcessVariables());
        }
        return vpPageResult;
    }

    /**
     * 构建单个流程实例的 VO（用于详情页等）
     */
    default BpmProcessInstanceRespVO buildProcessInstance(
            HistoricProcessInstance processInstance,
            ProcessDefinition processDefinition,
            BpmProcessDefinitionInfoDO processDefinitionInfo,
            AdminUserRespDTO startUser,
            DeptRespDTO dept) {

        BpmProcessInstanceRespVO respVO = BeanUtils.toBean(processInstance, BpmProcessInstanceRespVO.class);
        // 设置状态和表单变量
        respVO.setStatus(FlowableUtils.getProcessInstanceStatus(processInstance))
                .setFormVariables(FlowableUtils.getProcessInstanceFormVariable(processInstance));

        // 流程定义信息
        if (processDefinition != null) {
            respVO.setProcessDefinition(BeanUtils.toBean(processDefinition, BpmProcessDefinitionRespVO.class));
            // 补充流程定义扩展信息（如表单配置）
            copyTo(processDefinitionInfo, respVO.getProcessDefinition());
        }

        // 发起人信息
        if (startUser != null) {
            respVO.setStartUser(BeanUtils.toBean(startUser, UserSimpleBaseVO.class));
            if (dept != null) {
                respVO.getStartUser().setDeptName(dept.getName());
            }
        }
        return respVO;
    }

    /**
     * 将 BpmProcessDefinitionInfoDO 中的扩展字段复制到 BpmProcessDefinitionRespVO
     * （注意：不复制 id，防止覆盖）
     */
    @Mapping(source = "from.id", target = "to.id", ignore = true)
    void copyTo(BpmProcessDefinitionInfoDO from, @MappingTarget BpmProcessDefinitionRespVO to);

    /**
     * 构建流程状态变更事件（用于事件驱动，如发送消息）
     */
    default BpmProcessInstanceStatusEvent buildProcessInstanceStatusEvent(
            Object source, ProcessInstance instance, Integer status, String reason) {
        return new BpmProcessInstanceStatusEvent(source)
                .setId(instance.getId())
                .setStatus(status)
                .setReason(reason)
                .setProcessDefinitionKey(instance.getProcessDefinitionKey())
                .setBusinessKey(instance.getBusinessKey());
    }

    /**
     * 构建“流程通过”时发送消息的 DTO
     */
    default BpmMessageSendWhenProcessInstanceApproveReqDTO buildProcessInstanceApproveMessage(ProcessInstance instance) {
        return new BpmMessageSendWhenProcessInstanceApproveReqDTO()
                .setStartUserId(NumberUtils.parseLong(instance.getStartUserId()))
                .setProcessInstanceId(instance.getId())
                .setProcessInstanceName(instance.getName());
    }

    /**
     * 构建“流程驳回”时发送消息的 DTO
     */
    default BpmMessageSendWhenProcessInstanceRejectReqDTO buildProcessInstanceRejectMessage(
            ProcessInstance instance, String reason) {
        return new BpmMessageSendWhenProcessInstanceRejectReqDTO()
                .setProcessInstanceName(instance.getName())
                .setProcessInstanceId(instance.getId())
                .setReason(reason)
                .setStartUserId(NumberUtils.parseLong(instance.getStartUserId()));
    }

    /**
     * 构建 BPMN 流程图展示所需的 VO（含高亮信息）
     */
    default BpmProcessInstanceBpmnModelViewRespVO buildProcessInstanceBpmnModelView(
            HistoricProcessInstance processInstance,
            List<HistoricTaskInstance> taskInstances,
            BpmnModel bpmnModel,
            BpmSimpleModelNodeVO simpleModel,
            Set<String> unfinishedTaskActivityIds,    // 未完成任务对应的 Activity ID
            Set<String> finishedTaskActivityIds,      // 已完成任务对应的 Activity ID
            Set<String> finishedSequenceFlowActivityIds, // 已走过的连线（SequenceFlow）
            Set<String> rejectTaskActivityIds,        // 被驳回的任务节点
            Map<Long, AdminUserRespDTO> userMap,
            Map<Long, DeptRespDTO> deptMap) {

        BpmProcessInstanceBpmnModelViewRespVO respVO = new BpmProcessInstanceBpmnModelViewRespVO();

        // 1. 流程实例基本信息（含发起人）
        respVO.setProcessInstance(
                BeanUtils.toBean(processInstance, BpmProcessInstanceRespVO.class, o ->
                                o.setStatus(FlowableUtils.getProcessInstanceStatus(processInstance)))
                        .setStartUser(buildUser(processInstance.getStartUserId(), userMap, deptMap)));

        // 2. 历史任务列表（含状态、原因、签名人等）
        respVO.setTasks(convertList(taskInstances, task ->
                BeanUtils.toBean(task, BpmTaskRespVO.class)
                        .setStatus(FlowableUtils.getTaskStatus(task))
                        .setReason(FlowableUtils.getTaskReason(task))
                        .setAssigneeUser(buildUser(task.getAssignee(), userMap, deptMap))
                        .setOwnerUser(buildUser(task.getOwner(), userMap, deptMap))));

        // 3. BPMN XML 字符串（用于前端渲染流程图）
        respVO.setBpmnXml(BpmnModelUtils.getBpmnXml(bpmnModel));

        // 4. 简化模型（用于流程图节点展示）
        respVO.setSimpleModel(simpleModel);

        // 5. 高亮信息
        respVO.setUnfinishedTaskActivityIds(unfinishedTaskActivityIds)
                .setFinishedTaskActivityIds(finishedTaskActivityIds)
                .setFinishedSequenceFlowActivityIds(finishedSequenceFlowActivityIds)
                .setRejectedTaskActivityIds(rejectTaskActivityIds);

        return respVO;
    }

    /**
     * 根据 userId 字符串构建 UserSimpleBaseVO（支持 null 和空字符串）
     */
    default UserSimpleBaseVO buildUser(String userIdStr,
                                       Map<Long, AdminUserRespDTO> userMap,
                                       Map<Long, DeptRespDTO> deptMap) {
        if (StrUtil.isEmpty(userIdStr)) {
            return null;
        }
        Long userId = NumberUtils.parseLong(userIdStr);
        return buildUser(userId, userMap, deptMap);
    }

    /**
     * 根据 userId 构建 UserSimpleBaseVO（含部门名称）
     */
    default UserSimpleBaseVO buildUser(Long userId,
                                       Map<Long, AdminUserRespDTO> userMap,
                                       Map<Long, DeptRespDTO> deptMap) {
        if (userId == null) {
            return null;
        }
        AdminUserRespDTO user = userMap.get(userId);
        if (user == null) {
            return null;
        }
        UserSimpleBaseVO userVO = BeanUtils.toBean(user, UserSimpleBaseVO.class);
        DeptRespDTO dept = user.getDeptId() != null ? deptMap.get(user.getDeptId()) : null;
        if (dept != null) {
            userVO.setDeptName(dept.getName());
        }
        return userVO;
    }

    /**
     * 构建审批详情中的任务信息（含状态、审批意见、签名图片等）
     */
    default BpmApprovalDetailRespVO.ActivityNodeTask buildApprovalTaskInfo(HistoricTaskInstance task) {
        if (task == null) {
            return null;
        }
        return BeanUtils.toBean(task, BpmApprovalDetailRespVO.ActivityNodeTask.class)
                .setStatus(FlowableUtils.getTaskStatus(task))
                .setReason(FlowableUtils.getTaskReason(task))
                .setSignPicUrl(FlowableUtils.getTaskSignPicUrl(task));
    }

    /**
     * 从流程实例、审批节点、待办任务中提取所有涉及的用户 ID（用于批量查询用户信息）
     */
    default Set<Long> parseUserIds(
            HistoricProcessInstance processInstance,
            List<BpmApprovalDetailRespVO.ActivityNode> activityNodes,
            BpmTaskRespVO todoTask) {
        Set<Long> userIds = new HashSet<>();
        if (processInstance != null) {
            userIds.add(NumberUtils.parseLong(processInstance.getStartUserId()));
        }
        for (BpmApprovalDetailRespVO.ActivityNode activityNode : activityNodes) {
            // 处理人、任务负责人
            CollUtil.addAll(userIds, convertSet(activityNode.getTasks(), BpmApprovalDetailRespVO.ActivityNodeTask::getAssignee));
            CollUtil.addAll(userIds, convertSet(activityNode.getTasks(), BpmApprovalDetailRespVO.ActivityNodeTask::getOwner));
            // 候选人
            CollUtil.addAll(userIds, activityNode.getCandidateUserIds());
        }
        if (todoTask != null) {
            CollUtil.addIfAbsent(userIds, todoTask.getAssignee());
            CollUtil.addIfAbsent(userIds, todoTask.getOwner());
            if (CollUtil.isNotEmpty(todoTask.getChildren())) {
                CollUtil.addAll(userIds, convertSet(todoTask.getChildren(), BpmTaskRespVO::getAssignee));
                CollUtil.addAll(userIds, convertSet(todoTask.getChildren(), BpmTaskRespVO::getOwner));
            }
        }
        return userIds;
    }

    /**
     * 更简化的用户 ID 提取方式（仅从流程实例和历史任务中提取）
     */
    default Set<Long> parseUserIds02(HistoricProcessInstance processInstance, List<HistoricTaskInstance> tasks) {
        Set<Long> userIds = SetUtils.asSet(Long.valueOf(processInstance.getStartUserId()));
        tasks.forEach(task -> {
            CollUtil.addIfAbsent(userIds, NumberUtils.parseLong(task.getAssignee()));
            CollUtil.addIfAbsent(userIds, NumberUtils.parseLong(task.getOwner()));
        });
        return userIds;
    }

    /**
     * 构建完整的审批详情 VO（含流程定义、实例、节点、待办任务、字段权限等）
     */
    default BpmApprovalDetailRespVO buildApprovalDetail(
            BpmnModel bpmnModel,
            ProcessDefinition processDefinition,
            BpmProcessDefinitionInfoDO processDefinitionInfo,
            HistoricProcessInstance processInstance,
            Integer processInstanceStatus,
            List<BpmApprovalDetailRespVO.ActivityNode> activityNodes,
            BpmTaskRespVO todoTask,
            Map<String, String> formFieldsPermission, // 表单字段权限（如 read/write/hide）
            Map<Long, AdminUserRespDTO> userMap,
            Map<Long, DeptRespDTO> deptMap) {

        // 1.1 构建流程实例 VO
        BpmProcessInstanceRespVO processInstanceResp = null;
        if (processInstance != null) {
            AdminUserRespDTO startUser = userMap.get(NumberUtils.parseLong(processInstance.getStartUserId()));
            DeptRespDTO dept = startUser != null ? deptMap.get(startUser.getDeptId()) : null;
            processInstanceResp = buildProcessInstance(processInstance, null, null, startUser, dept);
        }

        // 1.2 构建流程定义 VO（包含表单配置等）
        BpmProcessDefinitionRespVO definitionResp = BpmProcessDefinitionConvert.INSTANCE.buildProcessDefinition(
                processDefinition, null, processDefinitionInfo, null, null, bpmnModel);

        // 1.3 补充审批节点中的用户信息
        activityNodes.forEach(approveNode -> {
            if (approveNode.getTasks() != null) {
                approveNode.getTasks().forEach(task -> {
                    task.setAssigneeUser(buildUser(task.getAssignee(), userMap, deptMap));
                    task.setOwnerUser(buildUser(task.getOwner(), userMap, deptMap));
                });
            }
            // 候选人列表
            approveNode.setCandidateUsers(convertList(approveNode.getCandidateUserIds(),
                    userId -> buildUser(userId, userMap, deptMap)));
        });

        // 1.4 补充待办任务的用户信息（含子任务）
        if (todoTask != null) {
            todoTask.setAssigneeUser(buildUser(todoTask.getAssignee(), userMap, deptMap));
            todoTask.setOwnerUser(buildUser(todoTask.getOwner(), userMap, deptMap));
            if (CollUtil.isNotEmpty(todoTask.getChildren())) {
                todoTask.getChildren().forEach(childTask -> {
                    childTask.setAssigneeUser(buildUser(childTask.getAssignee(), userMap, deptMap));
                    childTask.setOwnerUser(buildUser(childTask.getOwner(), userMap, deptMap));
                });
            }
        }

        // 2. 拼装返回对象
        return new BpmApprovalDetailRespVO()
                .setStatus(processInstanceStatus)
                .setProcessDefinition(definitionResp)
                .setProcessInstance(processInstanceResp)
                .setFormFieldsPermission(formFieldsPermission)
                .setTodoTask(todoTask)
                .setActivityNodes(activityNodes);
    }

    /**
     * 构建流程打印数据（用于生成 PDF 或 HTML 打印）
     */
    default BpmProcessPrintDataRespVO buildProcessInstancePrintData(
            HistoricProcessInstance historicProcessInstance,
            BpmProcessDefinitionInfoDO processDefinitionInfo,
            List<HistoricTaskInstance> tasks,
            Map<Long, AdminUserRespDTO> userMap,
            UserSimpleBaseVO startUser) {

        BpmModelMetaInfoVO.PrintTemplateSetting printTemplateSetting = processDefinitionInfo.getPrintTemplateSetting();
        BpmProcessPrintDataRespVO printData = new BpmProcessPrintDataRespVO();

        // 是否启用自定义打印模板
        printData.setPrintTemplateEnable(printTemplateSetting != null && Boolean.TRUE.equals(printTemplateSetting.getEnable()));

        // 流程基本信息
        BpmProcessInstanceRespVO processInstance = new BpmProcessInstanceRespVO()
                .setId(historicProcessInstance.getId())
                .setName(historicProcessInstance.getName())
                .setBusinessKey(historicProcessInstance.getBusinessKey())
                .setStartTime(DateUtils.of(historicProcessInstance.getStartTime()))
                .setEndTime(DateUtils.of(historicProcessInstance.getEndTime()))
                .setStartUser(startUser)
                .setStatus(FlowableUtils.getProcessInstanceStatus(historicProcessInstance))
                .setFormVariables(historicProcessInstance.getProcessVariables())
                .setProcessDefinition(BeanUtils.toBean(processDefinitionInfo, BpmProcessDefinitionRespVO.class));
        printData.setProcessInstance(processInstance);

        // 审批历史（每条记录描述：用户 / 任务名 / 时间 / 状态 / 意见）
        List<BpmProcessPrintDataRespVO.Task> approveTasks = new ArrayList<>(tasks.size());
        tasks.forEach(item -> {
            Map<String, Object> taskLocalVariables = item.getTaskLocalVariables();
            BpmProcessPrintDataRespVO.Task approveTask = new BpmProcessPrintDataRespVO.Task();
            approveTask.setName(item.getName());
            approveTask.setId(item.getId());
            approveTask.setSignPicUrl((String) taskLocalVariables.get(BpmnVariableConstants.TASK_SIGN_PIC_URL));

            // 获取处理人昵称
            String nickname = "未知";
            if (StrUtil.isNotEmpty(item.getAssignee())) {
                AdminUserRespDTO assigneeUser = userMap.get(Long.valueOf(item.getAssignee()));
                if (assigneeUser != null) {
                    nickname = assigneeUser.getNickname();
                }
            }

            // 获取状态名称
            Integer status = (Integer) taskLocalVariables.get(BpmnVariableConstants.TASK_VARIABLE_STATUS);
            String statusName = status != null ? BpmTaskStatusEnum.valueOf(status).getName() : "未知";

            // 拼接描述
            approveTask.setDescription(StrUtil.format("{} / {} / {} / {} / {}",
                    nickname,
                    item.getName(),
                    DateUtil.formatDateTime(item.getEndTime()),
                    statusName,
                    taskLocalVariables.get(BpmnVariableConstants.TASK_VARIABLE_REASON)));

            approveTasks.add(approveTask);
        });
        printData.setTasks(approveTasks);

        // 设置自定义打印模板
        if (printData.getPrintTemplateEnable() && printTemplateSetting != null) {
            printData.setPrintTemplateHtml(printTemplateSetting.getTemplate());
        }

        return printData;
    }

}