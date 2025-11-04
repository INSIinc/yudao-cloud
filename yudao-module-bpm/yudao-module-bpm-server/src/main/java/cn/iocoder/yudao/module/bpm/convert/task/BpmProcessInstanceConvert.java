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
 * 流程实例转换器接口
 *
 * <p>这个接口的作用是将 Flowable 工作流引擎中的原始数据对象转换成前端需要的 VO（视图对象）</p>
 *
 * <h3>什么是转换器？</h3>
 * <p>在实际开发中，数据库或第三方框架（如 Flowable）返回的数据结构往往不能直接给前端使用，
 * 需要进行格式转换、字段补充等操作。转换器就是专门做这个工作的工具类。</p>
 *
 * <h3>为什么使用 MapStruct？</h3>
 * <p>MapStruct 是一个代码生成工具，可以自动生成对象之间的转换代码，避免手写大量的 getter/setter，
 * 提高开发效率并减少出错。</p>
 *
 * <h3>主要功能：</h3>
 * <ul>
 *   <li>1. 将流程实例（ProcessInstance）转换为前端展示的 VO 对象</li>
 *   <li>2. 补充关联数据，如用户信息、部门信息、流程定义信息等</li>
 *   <li>3. 构建流程图展示所需的数据（包括高亮已完成的节点）</li>
 *   <li>4. 生成打印数据、审批详情等复杂业务对象</li>
 * </ul>
 *
 * @author 芋道源码
 */
@Mapper
public interface BpmProcessInstanceConvert {

    /**
     * MapStruct 自动生成的转换器实例
     *
     * <p>通过这个实例可以调用接口中定义的转换方法</p>
     */
    BpmProcessInstanceConvert INSTANCE = Mappers.getMapper(BpmProcessInstanceConvert.class);

    /**
     * 构建流程实例分页列表数据
     *
     * <p><b>应用场景：</b>在"我的流程"、"待办任务"等列表页面，需要展示流程实例的分页数据</p>
     *
     * <p><b>处理流程：</b></p>
     * <ol>
     *   <li>1. 将 Flowable 返回的历史流程实例分页结果转换为 VO 对象</li>
     *   <li>2. 根据流程定义 ID 补充流程定义信息（名称、版本等）</li>
     *   <li>3. 根据分类 ID 补充分类名称</li>
     *   <li>4. 补充当前流程的待办任务列表</li>
     *   <li>5. 补充发起人和处理人的用户信息、部门信息</li>
     *   <li>6. 根据配置生成流程摘要（表单字段的关键信息）</li>
     * </ol>
     *
     * @param pageResult                Flowable 查询返回的历史流程实例分页结果
     * @param processDefinitionMap      流程定义 Map，key=流程定义ID，value=流程定义对象（用于快速查找）
     * @param categoryMap               流程分类 Map，key=分类ID，value=分类对象
     * @param taskMap                   当前待办任务 Map，key=流程实例ID，value=该流程的待办任务列表
     * @param userMap                   用户信息 Map，key=用户ID，value=用户对象（用于补充发起人、处理人信息）
     * @param deptMap                   部门信息 Map，key=部门ID，value=部门对象（用于补充部门名称）
     * @param processDefinitionInfoMap  流程定义扩展信息 Map（包含表单配置、摘要配置等）
     * @return 转换后的分页 VO 对象，可直接返回给前端
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
     * 构建单个流程实例的详细信息
     *
     * <p><b>应用场景：</b>查看流程实例详情页时调用，返回完整的流程实例信息</p>
     *
     * <p><b>与分页列表的区别：</b>这个方法用于单条数据的详情展示，参数更简单直接</p>
     *
     * @param processInstance        历史流程实例对象（Flowable 原始对象）
     * @param processDefinition      流程定义对象（包含流程名称、版本等基本信息）
     * @param processDefinitionInfo  流程定义扩展信息（包含表单配置等）
     * @param startUser              流程发起人用户信息
     * @param dept                   发起人所属部门信息
     * @return 转换后的流程实例 VO 对象，包含完整的流程信息
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
     * 复制流程定义扩展信息到响应对象
     *
     * <p><b>注意：</b>使用 @Mapping 注解忽略 id 字段，避免覆盖目标对象的 id</p>
     *
     * <p><b>为什么需要这个方法？</b>流程定义扩展信息（processDefinitionInfo）中包含表单配置、
     * 按钮配置等额外信息，需要合并到流程定义响应对象中一起返回给前端</p>
     *
     * @param from 源对象：流程定义扩展信息（数据库实体）
     * @param to   目标对象：流程定义响应 VO（会被修改）
     */
    @Mapping(source = "from.id", target = "to.id", ignore = true)
    void copyTo(BpmProcessDefinitionInfoDO from, @MappingTarget BpmProcessDefinitionRespVO to);

    /**
     * 构建流程实例状态变更事件
     *
     * <p><b>应用场景：</b>当流程状态发生变化时（如通过、驳回、取消），需要发布事件通知其他模块</p>
     *
     * <p><b>事件驱动架构：</b>通过事件机制，可以解耦业务逻辑。例如：
     * <ul>
     *   <li>流程通过后，自动发送消息通知</li>
     *   <li>流程驳回后，更新业务表状态</li>
     *   <li>流程取消后，释放相关资源</li>
     * </ul>
     * </p>
     *
     * @param source 事件源对象（通常是触发事件的 Service 类）
     * @param instance 流程实例对象
     * @param status 新的流程状态（如：通过、驳回、取消等）
     * @param reason 状态变更原因（如驳回原因）
     * @return 流程实例状态变更事件对象
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
     * 构建流程审批通过时的消息发送对象
     *
     * <p><b>应用场景：</b>当流程被审批通过时，需要发送消息通知相关人员（如发起人）</p>
     *
     * <p><b>消息内容示例：</b>"您的【请假申请】已通过审批"</p>
     *
     * @param instance 流程实例对象
     * @return 消息发送请求 DTO，包含收件人、流程信息等
     */
    default BpmMessageSendWhenProcessInstanceApproveReqDTO buildProcessInstanceApproveMessage(ProcessInstance instance) {
        return new BpmMessageSendWhenProcessInstanceApproveReqDTO()
                .setStartUserId(NumberUtils.parseLong(instance.getStartUserId()))
                .setProcessInstanceId(instance.getId())
                .setProcessInstanceName(instance.getName());
    }

    /**
     * 构建流程被驳回时的消息发送对象
     *
     * <p><b>应用场景：</b>当流程被驳回时，需要发送消息通知发起人重新提交</p>
     *
     * <p><b>消息内容示例：</b>"您的【请假申请】已被驳回，原因：请假天数超出规定"</p>
     *
     * @param instance 流程实例对象
     * @param reason 驳回原因（会显示在消息中）
     * @return 消息发送请求 DTO，包含收件人、流程信息、驳回原因等
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
     * 构建流程图模型视图数据
     *
     * <p><b>应用场景：</b>在流程详情页展示流程图，需要高亮显示已完成的节点和连线</p>
     *
     * <p><b>核心功能：</b></p>
     * <ul>
     *   <li>1. 提供 BPMN XML，前端可以渲染出流程图</li>
     *   <li>2. 标记哪些节点已完成（绿色）、哪些待处理（黄色）、哪些被驳回（红色）</li>
     *   <li>3. 标记已走过的连线（用于显示流程流转路径）</li>
     *   <li>4. 补充每个任务的处理人信息、处理时间、审批意见等</li>
     * </ul>
     *
     * @param processInstance              历史流程实例对象
     * @param taskInstances                历史任务列表（包含所有已完成和待办的任务）
     * @param bpmnModel                    BPMN 模型对象（包含流程图的完整定义）
     * @param simpleModel                  简化的模型对象（用于前端快速渲染）
     * @param unfinishedTaskActivityIds    未完成任务的活动节点 ID 集合（前端会标记为黄色）
     * @param finishedTaskActivityIds      已完成任务的活动节点 ID 集合（前端会标记为绿色）
     * @param finishedSequenceFlowActivityIds 已走过的连线 ID 集合（前端会高亮显示）
     * @param rejectTaskActivityIds        被驳回的任务节点 ID 集合（前端会标记为红色）
     * @param userMap                      用户信息 Map，用于补充处理人信息
     * @param deptMap                      部门信息 Map，用于补充部门名称
     * @return 流程图模型视图 VO，包含流程图 XML、高亮信息、任务列表等
     */
    default BpmProcessInstanceBpmnModelViewRespVO buildProcessInstanceBpmnModelView(
            HistoricProcessInstance processInstance,
            List<HistoricTaskInstance> taskInstances,
            BpmnModel bpmnModel,
            BpmSimpleModelNodeVO simpleModel,
            Set<String> unfinishedTaskActivityIds,
            Set<String> finishedTaskActivityIds,
            Set<String> finishedSequenceFlowActivityIds,
            Set<String> rejectTaskActivityIds,
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
     * 根据用户 ID 字符串构建用户简单信息对象
     *
     * <p><b>为什么需要这个方法？</b>Flowable 中存储的用户 ID 是字符串类型，
     * 需要转换为 Long 类型后才能查询用户信息</p>
     *
     * <p><b>空值处理：</b>如果 userIdStr 为 null 或空字符串，直接返回 null</p>
     *
     * @param userIdStr 用户 ID 字符串（可能为 null 或空）
     * @param userMap   用户信息 Map
     * @param deptMap   部门信息 Map
     * @return 用户简单信息 VO，包含姓名、部门等；如果用户不存在则返回 null
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
     * 根据用户 ID 构建用户简单信息对象
     *
     * <p><b>核心逻辑：</b></p>
     * <ol>
     *   <li>1. 从 userMap 中查找用户信息</li>
     *   <li>2. 将用户信息转换为 VO 对象</li>
     *   <li>3. 根据用户的部门 ID，从 deptMap 中查找部门名称并补充</li>
     * </ol>
     *
     * @param userId  用户 ID
     * @param userMap 用户信息 Map
     * @param deptMap 部门信息 Map
     * @return 用户简单信息 VO，包含姓名、部门名称等；如果用户不存在则返回 null
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
     * 构建审批任务信息（用于审批详情页）
     *
     * <p><b>应用场景：</b>在审批详情页展示每个审批节点的详细信息</p>
     *
     * <p><b>包含信息：</b></p>
     * <ul>
     *   <li>任务状态（待处理、已通过、已驳回等）</li>
     *   <li>审批意见（同意/不同意的理由）</li>
     *   <li>签名图片 URL（如果启用了手写签名功能）</li>
     * </ul>
     *
     * @param task 历史任务实例对象
     * @return 审批任务信息 VO；如果 task 为 null 则返回 null
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
     * 从流程数据中提取所有相关的用户 ID
     *
     * <p><b>为什么需要这个方法？</b>在构建审批详情等复杂对象时，需要补充多处的用户信息。
     * 为了避免重复查询数据库，先把所有需要的用户 ID 收集起来，一次性批量查询，提高性能。</p>
     *
     * <p><b>收集范围：</b></p>
     * <ul>
     *   <li>流程发起人</li>
     *   <li>所有任务的处理人（assignee）</li>
     *   <li>所有任务的负责人（owner）</li>
     *   <li>所有候选人（candidateUsers）</li>
     *   <li>当前待办任务的相关人员</li>
     * </ul>
     *
     * @param processInstance 历史流程实例对象
     * @param activityNodes   审批节点列表（包含任务信息）
     * @param todoTask        当前待办任务（可能为 null）
     * @return 用户 ID 集合，用于批量查询用户信息
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
     * 从流程数据中提取用户 ID（简化版）
     *
     * <p><b>与 parseUserIds 的区别：</b>这个方法只从流程实例和历史任务中提取，
     * 适用于不需要候选人等复杂信息的场景</p>
     *
     * @param processInstance 历史流程实例对象
     * @param tasks           历史任务列表
     * @return 用户 ID 集合
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
     * 构建完整的审批详情数据
     *
     * <p><b>应用场景：</b>审批详情页是最复杂的业务场景之一，需要展示：</p>
     * <ul>
     *   <li>流程定义信息（流程名称、版本、表单配置等）</li>
     *   <li>流程实例信息（发起人、发起时间、当前状态等）</li>
     *   <li>审批节点列表（每个节点的处理人、处理时间、审批意见等）</li>
     *   <li>当前待办任务（如果有）</li>
     *   <li>表单字段权限（哪些字段可读、可写、隐藏）</li>
     * </ul>
     *
     * <p><b>数据来源：</b>这个方法会整合来自多个服务的数据，包括：</p>
     * <ul>
     *   <li>Flowable 引擎：流程实例、任务信息</li>
     *   <li>流程定义服务：流程定义、表单配置</li>
     *   <li>用户服务：用户信息、部门信息</li>
     *   <li>权限服务：字段权限配置</li>
     * </ul>
     *
     * @param bpmnModel                BPMN 模型对象
     * @param processDefinition        流程定义对象
     * @param processDefinitionInfo    流程定义扩展信息
     * @param processInstance          历史流程实例对象
     * @param processInstanceStatus    流程实例当前状态
     * @param activityNodes            审批节点列表（包含每个节点的任务、候选人等信息）
     * @param todoTask                 当前待办任务（如果存在）
     * @param formFieldsPermission     表单字段权限 Map，key=字段名，value=权限（READ/WRITE/HIDE）
     * @param userMap                  用户信息 Map
     * @param deptMap                  部门信息 Map
     * @return 审批详情 VO，包含所有需要展示的信息
     */
    default BpmApprovalDetailRespVO buildApprovalDetail(
            BpmnModel bpmnModel,
            ProcessDefinition processDefinition,
            BpmProcessDefinitionInfoDO processDefinitionInfo,
            HistoricProcessInstance processInstance,
            Integer processInstanceStatus,
            List<BpmApprovalDetailRespVO.ActivityNode> activityNodes,
            BpmTaskRespVO todoTask,
            Map<String, String> formFieldsPermission,
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
     * 构建流程打印数据
     *
     * <p><b>应用场景：</b>当用户点击"打印"按钮时，需要生成可打印的流程数据（PDF 或 HTML 格式）</p>
     *
     * <p><b>打印内容包括：</b></p>
     * <ul>
     *   <li>流程基本信息（流程名称、发起人、发起时间等）</li>
     *   <li>表单数据（申请的详细内容）</li>
     *   <li>审批历史（每个节点的处理人、处理时间、审批意见、签名图片）</li>
     *   <li>自定义打印模板（如果配置了）</li>
     * </ul>
     *
     * <p><b>模板功能：</b>管理员可以配置自定义打印模板（HTML 格式），使用变量占位符，
     * 系统会自动替换为实际数据</p>
     *
     * @param historicProcessInstance  历史流程实例对象
     * @param processDefinitionInfo    流程定义扩展信息（包含打印模板配置）
     * @param tasks                    历史任务列表（所有已完成的审批任务）
     * @param userMap                  用户信息 Map
     * @param startUser                流程发起人信息
     * @return 流程打印数据 VO，包含所有打印所需的信息
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