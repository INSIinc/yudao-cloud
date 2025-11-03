package cn.iocoder.yudao.module.bpm.convert.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.iocoder.yudao.framework.common.core.KeyValue;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.base.user.UserSimpleBaseVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRespVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskCreatedReqDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.service.impl.persistence.entity.TaskEntityImpl;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.common.util.collection.MapUtils.findAndThen;

/**
 * Bpm 任务转换器（Convert）
 * <p>
 * 负责将 Flowable 引擎中的 Task、HistoricTaskInstance、ProcessInstance 等实体对象
 * 转换为前端所需的 BpmTaskRespVO 响应对象，并填充用户、部门、表单、流程定义等关联信息。
 *
 * @author 芋道源码
 */
@Mapper
public interface BpmTaskConvert {

    /**
     * 单例实例，通过 MapStruct 工厂创建
     */
    BpmTaskConvert INSTANCE = Mappers.getMapper(BpmTaskConvert.class);

    /**
     * 构建“待办任务”分页结果（运行中的任务）
     *
     * @param pageResult                Flowable 的 Task 分页结果
     * @param processInstanceMap        流程实例映射（key: processInstanceId）
     * @param userMap                   用户映射（key: userId）
     * @param processDefinitionInfoMap  流程定义扩展信息映射（key: processDefinitionId）
     * @return 包含 BpmTaskRespVO 的分页结果
     */
    default PageResult<BpmTaskRespVO> buildTodoTaskPage(
            PageResult<Task> pageResult,
            Map<String, ProcessInstance> processInstanceMap,
            Map<Long, AdminUserRespDTO> userMap,
            Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap) {
        return BeanUtils.toBean(pageResult, BpmTaskRespVO.class, taskVO -> {
            // 获取关联的流程实例
            ProcessInstance processInstance = processInstanceMap.get(taskVO.getProcessInstanceId());
            if (processInstance == null) {
                return; // 若无流程实例，跳过填充
            }
            // 填充流程实例基础信息（ID、名称、业务Key 等）
            taskVO.setProcessInstance(BeanUtils.toBean(processInstance, BpmTaskRespVO.ProcessInstance.class));
            // 设置流程发起人（startUserId 转为用户信息）
            AdminUserRespDTO startUser = userMap.get(NumberUtils.parseLong(processInstance.getStartUserId()));
            taskVO.getProcessInstance().setStartUser(BeanUtils.toBean(startUser, UserSimpleBaseVO.class));
            // 设置流程创建时间（转换为 Date）
            taskVO.getProcessInstance().setCreateTime(DateUtils.of(processInstance.getStartTime()));
            // 设置流程摘要（基于流程定义的摘要模板 + 流程变量动态填充）
            taskVO.getProcessInstance().setSummary(
                    FlowableUtils.getSummary(
                            processDefinitionInfoMap.get(processInstance.getProcessDefinitionId()),
                            processInstance.getProcessVariables()
                    )
            );
        });
    }

    /**
     * 构建“历史任务”分页结果（已完成/已终止的任务）
     *
     * @param pageResult                Flowable 的 HistoricTaskInstance 分页结果
     * @param processInstanceMap        历史流程实例映射（key: processInstanceId）
     * @param userMap                   用户映射（key: userId）
     * @param deptMap                   部门映射（key: deptId）
     * @param processDefinitionInfoMap  流程定义扩展信息映射（key: processDefinitionId）
     * @return 包含 BpmTaskRespVO 的分页结果
     */
    default PageResult<BpmTaskRespVO> buildTaskPage(
            PageResult<HistoricTaskInstance> pageResult,
            Map<String, HistoricProcessInstance> processInstanceMap,
            Map<Long, AdminUserRespDTO> userMap,
            Map<Long, DeptRespDTO> deptMap,
            Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap) {
        List<BpmTaskRespVO> taskVOList = CollectionUtils.convertList(pageResult.getList(), task -> {
            BpmTaskRespVO taskVO = BeanUtils.toBean(task, BpmTaskRespVO.class);
            // 设置任务状态与审批意见（如“已通过”、“已拒绝”等）
            taskVO.setStatus(FlowableUtils.getTaskStatus(task))
                    .setReason(FlowableUtils.getTaskReason(task));

            // 填充任务处理人信息（assignee）
            AdminUserRespDTO assignUser = userMap.get(NumberUtils.parseLong(task.getAssignee()));
            if (assignUser != null) {
                taskVO.setAssigneeUser(BeanUtils.toBean(assignUser, UserSimpleBaseVO.class));
                // 关联部门名称（通过 deptId 查找）
                findAndThen(deptMap, assignUser.getDeptId(), dept -> taskVO.getAssigneeUser().setDeptName(dept.getName()));
            }

            // 填充流程实例信息（历史）
            HistoricProcessInstance processInstance = processInstanceMap.get(taskVO.getProcessInstanceId());
            if (processInstance != null) {
                AdminUserRespDTO startUser = userMap.get(NumberUtils.parseLong(processInstance.getStartUserId()));
                taskVO.setProcessInstance(BeanUtils.toBean(processInstance, BpmTaskRespVO.ProcessInstance.class));
                taskVO.getProcessInstance().setStartUser(BeanUtils.toBean(startUser, UserSimpleBaseVO.class));
                // 设置摘要（同待办任务逻辑）
                taskVO.getProcessInstance().setSummary(
                        FlowableUtils.getSummary(
                                processDefinitionInfoMap.get(processInstance.getProcessDefinitionId()),
                                processInstance.getProcessVariables()
                        )
                );
            }
            return taskVO;
        });
        return new PageResult<>(taskVOList, pageResult.getTotal());
    }

    /**
     * 根据流程实例 ID 查询其下所有历史任务，并转换为 VO 列表（用于流程详情页）
     *
     * @param taskList         历史任务列表
     * @param formMap          表单映射（key: formId）
     * @param userMap          用户映射
     * @param deptMap          部门映射
     * @return 转换后的任务列表，**排除“已取消”状态的任务**
     */
    default List<BpmTaskRespVO> buildTaskListByProcessInstanceId(
            List<HistoricTaskInstance> taskList,
            Map<Long, BpmFormDO> formMap,
            Map<Long, AdminUserRespDTO> userMap,
            Map<Long, DeptRespDTO> deptMap) {
        return CollectionUtils.convertList(taskList, task -> {
            BpmTaskRespVO taskVO = BeanUtils.toBean(task, BpmTaskRespVO.class);
            Integer taskStatus = FlowableUtils.getTaskStatus(task);
            // 过滤掉“已取消”任务（如撤回后产生的取消任务）
            if (BpmTaskStatusEnum.isCancelStatus(taskStatus)) {
                return null;
            }
            taskVO.setStatus(taskStatus).setReason(FlowableUtils.getTaskReason(task));

            // 填充表单信息（若任务绑定了表单）
            BpmFormDO form = MapUtil.get(formMap, NumberUtils.parseLong(task.getFormKey()), BpmFormDO.class);
            if (form != null) {
                taskVO.setFormId(form.getId())
                        .setFormName(form.getName())
                        .setFormConf(form.getConf())
                        .setFormFields(form.getFields())
                        // 提取任务级别的表单变量（用于回显）
                        .setFormVariables(FlowableUtils.getTaskFormVariable(task));
            }

            // 填充处理人（assignee）和委托人（owner）信息
            buildTaskAssignee(taskVO, task.getAssignee(), userMap, deptMap);
            buildTaskOwner(taskVO, task.getOwner(), userMap, deptMap);
            return taskVO;
        });
    }

    /**
     * 构建子任务列表（由父任务 ID 查询的子任务，常用于“加签”场景）
     *
     * @param taskList 当前父任务下的子任务列表（Task 类型，运行中）
     * @param userMap  用户映射
     * @param deptMap  部门映射
     * @return 转换后的子任务 VO 列表
     */
    default List<BpmTaskRespVO> buildTaskListByParentTaskId(
            List<Task> taskList,
            Map<Long, AdminUserRespDTO> userMap,
            Map<Long, DeptRespDTO> deptMap) {
        return convertList(taskList, task -> BeanUtils.toBean(task, BpmTaskRespVO.class, taskVO -> {
            // 处理人信息
            AdminUserRespDTO assignUser = userMap.get(NumberUtils.parseLong(task.getAssignee()));
            if (assignUser != null) {
                taskVO.setAssigneeUser(BeanUtils.toBean(assignUser, UserSimpleBaseVO.class));
                DeptRespDTO dept = deptMap.get(assignUser.getDeptId());
                if (dept != null) {
                    taskVO.getAssigneeUser().setDeptName(dept.getName());
                }
            }
            // 委托人（owner）信息
            AdminUserRespDTO ownerUser = userMap.get(NumberUtils.parseLong(task.getOwner()));
            if (ownerUser != null) {
                taskVO.setOwnerUser(BeanUtils.toBean(ownerUser, UserSimpleBaseVO.class));
                findAndThen(deptMap, ownerUser.getDeptId(), dept -> taskVO.getOwnerUser().setDeptName(dept.getName()));
            }
        }));
    }

    /**
     * 构建单个待办任务详情（含子任务、表单、操作按钮等）
     *
     * @param todoTask       当前待办任务
     * @param childrenTasks  该任务的子任务列表（如加签产生的任务）
     * @param buttonsSetting 操作按钮配置（如“通过”、“拒绝”、“转办”等）
     * @param form           关联的表单信息
     * @return 完整的 BpmTaskRespVO
     */
    default BpmTaskRespVO buildTodoTask(
            Task todoTask,
            List<Task> childrenTasks,
            Map<Integer, BpmTaskRespVO.OperationButtonSetting> buttonsSetting,
            BpmFormDO form) {
        BpmTaskRespVO bpmTaskRespVO = BeanUtils.toBean(todoTask, BpmTaskRespVO.class)
                .setStatus(FlowableUtils.getTaskStatus(todoTask))
                .setReason(FlowableUtils.getTaskReason(todoTask))
                .setButtonsSetting(buttonsSetting)
                // 转换子任务列表，仅保留基本信息与状态
                .setChildren(convertList(childrenTasks, childTask ->
                        BeanUtils.toBean(childTask, BpmTaskRespVO.class)
                                .setStatus(FlowableUtils.getTaskStatus(childTask))
                ));
        // 填充表单信息（若存在）
        if (form != null) {
            bpmTaskRespVO.setFormId(form.getId())
                    .setFormName(form.getName())
                    .setFormConf(form.getConf())
                    .setFormFields(form.getFields());
            // 注意：此处未设置 formVariables，因待办任务变量通常在启动时获取或动态加载
        }
        return bpmTaskRespVO;
    }

    /**
     * 转换为“任务创建”消息通知 DTO（用于发送站内信/邮件等）
     *
     * @param processInstance Flowable 流程实例
     * @param startUser       流程发起人
     * @param task            新创建的任务
     * @return 消息通知请求 DTO
     */
    default BpmMessageSendWhenTaskCreatedReqDTO convert(
            ProcessInstance processInstance,
            AdminUserRespDTO startUser,
            Task task) {
        BpmMessageSendWhenTaskCreatedReqDTO reqDTO = new BpmMessageSendWhenTaskCreatedReqDTO();
        reqDTO.setProcessInstanceId(processInstance.getProcessInstanceId())
                .setProcessInstanceName(processInstance.getName())
                .setStartUserId(startUser.getId())
                .setStartUserNickname(startUser.getNickname())
                .setTaskId(task.getId())
                .setTaskName(task.getName())
                .setAssigneeUserId(NumberUtils.parseLong(task.getAssignee()));
        return reqDTO;
    }

    /**
     * 填充任务的“委托人”（owner）信息
     *
     * @param task        目标 VO
     * @param taskOwner   Flowable 中的 owner 字符串（用户 ID）
     * @param userMap     用户映射
     * @param deptMap     部门映射
     */
    default void buildTaskOwner(
            BpmTaskRespVO task,
            String taskOwner,
            Map<Long, AdminUserRespDTO> userMap,
            Map<Long, DeptRespDTO> deptMap) {
        AdminUserRespDTO ownerUser = userMap.get(NumberUtils.parseLong(taskOwner));
        if (ownerUser != null) {
            task.setOwnerUser(BeanUtils.toBean(ownerUser, UserSimpleBaseVO.class));
            findAndThen(deptMap, ownerUser.getDeptId(), dept -> task.getOwnerUser().setDeptName(dept.getName()));
        }
    }

    /**
     * 填充任务的“子任务”列表（用于展示任务树）
     *
     * @param task              当前父任务 VO
     * @param childrenTaskMap   子任务映射（key: parentId）
     * @param userMap           用户映射
     * @param deptMap           部门映射
     */
    default void buildTaskChildren(
            BpmTaskRespVO task,
            Map<String, List<Task>> childrenTaskMap,
            Map<Long, AdminUserRespDTO> userMap,
            Map<Long, DeptRespDTO> deptMap) {
        List<Task> childTasks = childrenTaskMap.get(task.getId());
        if (CollUtil.isNotEmpty(childTasks)) {
            task.setChildren(
                    convertList(childTasks, childTask -> {
                        BpmTaskRespVO childTaskVO = BeanUtils.toBean(childTask, BpmTaskRespVO.class);
                        childTaskVO.setStatus(FlowableUtils.getTaskStatus(childTask));
                        buildTaskOwner(childTaskVO, childTask.getOwner(), userMap, deptMap);
                        buildTaskAssignee(childTaskVO, childTask.getAssignee(), userMap, deptMap);
                        return childTaskVO;
                    })
            );
        }
    }

    /**
     * 填充任务的“处理人”（assignee）信息
     *
     * @param task          目标 VO
     * @param taskAssignee  Flowable 中的 assignee 字符串（用户 ID）
     * @param userMap       用户映射
     * @param deptMap       部门映射
     */
    default void buildTaskAssignee(
            BpmTaskRespVO task,
            String taskAssignee,
            Map<Long, AdminUserRespDTO> userMap,
            Map<Long, DeptRespDTO> deptMap) {
        AdminUserRespDTO assignUser = userMap.get(NumberUtils.parseLong(taskAssignee));
        if (assignUser != null) {
            task.setAssigneeUser(BeanUtils.toBean(assignUser, UserSimpleBaseVO.class));
            findAndThen(deptMap, assignUser.getDeptId(), dept -> task.getAssigneeUser().setDeptName(dept.getName()));
        }
    }

    /**
     * 将父任务的属性拷贝到子任务（用于“加签”场景创建新任务）
     * <p>
     * 为何不用 MapStruct？因为 TaskEntityImpl 内部结构复杂，包含大量运行时状态和嵌套对象。
     * 使用 MapStruct 会深拷贝所有字段，可能引发 Flowable 内部状态异常。
     * 因此只手动拷贝必要的业务属性。
     *
     * @param parentTask 父任务（原始任务）
     * @param childTask  子任务（新创建的加签任务）
     */
    default void copyTo(TaskEntityImpl parentTask, TaskEntityImpl childTask) {
        childTask.setName(parentTask.getName());
        childTask.setDescription(parentTask.getDescription());
        childTask.setCategory(parentTask.getCategory());
        childTask.setParentTaskId(parentTask.getId()); // 设置父子关系
        childTask.setProcessDefinitionId(parentTask.getProcessDefinitionId());
        childTask.setProcessInstanceId(parentTask.getProcessInstanceId());
        childTask.setTaskDefinitionKey(parentTask.getTaskDefinitionKey());
        childTask.setTaskDefinitionId(parentTask.getTaskDefinitionId());
        childTask.setPriority(parentTask.getPriority());
        childTask.setCreateTime(new Date()); // 创建时间重置为当前
        childTask.setTenantId(parentTask.getTenantId());
        // 注意：不拷贝 assignee/owner，由加签逻辑单独指定
    }

}