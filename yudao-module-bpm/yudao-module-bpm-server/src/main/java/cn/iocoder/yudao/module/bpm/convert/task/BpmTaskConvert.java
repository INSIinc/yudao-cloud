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
 * 【初学者必读】什么是转换器（Converter）？
 * 在实际项目中，数据库实体对象（DO）、业务对象（BO）、前端展示对象（VO）通常是不同的。
 * 转换器的作用就是：将 Flowable 工作流引擎的任务对象（Task、HistoricTaskInstance 等）
 * 转换为前端需要的 BpmTaskRespVO 响应对象，并填充用户、部门、表单、流程定义等关联信息。
 * <p>
 * 【技术说明】
 * - 使用 MapStruct 框架实现对象映射（自动生成转换代码，性能高）
 * - 采用接口 + default 方法的形式，既可以使用 MapStruct 自动映射，也可以手动编写复杂逻辑
 * - Flowable 是一个开源的工作流引擎，用于管理业务流程（如请假审批、报销审批等）
 *
 * @author 芋道源码
 */
@Mapper
public interface BpmTaskConvert {

    /**
     * 【单例实例】通过 MapStruct 工厂创建转换器实例
     *
     * 使用说明：在代码中通过 BpmTaskConvert.INSTANCE 调用转换方法
     * 例如：BpmTaskConvert.INSTANCE.buildTodoTaskPage(...)
     */
    BpmTaskConvert INSTANCE = Mappers.getMapper(BpmTaskConvert.class);

    /**
     * 构建"待办任务"分页结果（运行中的任务）
     *
     * 【业务场景】用户登录后，查看"我的待办"任务列表
     * 【核心逻辑】
     * 1. 将 Flowable 的 Task 对象转换为前端需要的 BpmTaskRespVO
     * 2. 填充流程实例信息（流程名称、业务Key等）
     * 3. 填充流程发起人信息（姓名、头像等）
     * 4. 填充流程摘要（根据模板动态生成，如"张三申请请假3天"）
     *
     * @param pageResult                Flowable 的 Task 分页结果（包含当前运行中的任务列表）
     * @param processInstanceMap        流程实例映射（key: processInstanceId，value: ProcessInstance）
     *                                  为什么要传 Map？因为批量查询数据库后放到 Map 中，可以快速根据 ID 查找，避免 N+1 查询问题
     * @param userMap                   用户映射（key: userId，value: AdminUserRespDTO）
     *                                  存储所有涉及的用户信息，供后续填充使用
     * @param processDefinitionInfoMap  流程定义扩展信息映射（key: processDefinitionId，value: BpmProcessDefinitionInfoDO）
     *                                  包含流程的名称、图标、摘要模板等扩展配置
     * @return 包含 BpmTaskRespVO 的分页结果（前端可直接使用）
     */
    default PageResult<BpmTaskRespVO> buildTodoTaskPage(
            PageResult<Task> pageResult,
            Map<String, ProcessInstance> processInstanceMap,
            Map<Long, AdminUserRespDTO> userMap,
            Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap) {
        // 使用 BeanUtils.toBean 进行对象转换（底层使用了 MapStruct 或反射）
        return BeanUtils.toBean(pageResult, BpmTaskRespVO.class, taskVO -> {
            // 【步骤1】根据流程实例ID，从 Map 中获取对应的流程实例对象
            ProcessInstance processInstance = processInstanceMap.get(taskVO.getProcessInstanceId());
            if (processInstance == null) {
                return; // 若无流程实例，跳过填充（防止空指针异常）
            }

            // 【步骤2】填充流程实例基础信息（ID、名称、业务Key 等）
            taskVO.setProcessInstance(BeanUtils.toBean(processInstance, BpmTaskRespVO.ProcessInstance.class));

            // 【步骤3】设置流程发起人信息
            // processInstance.getStartUserId() 返回的是字符串类型的用户ID，需要转换为 Long
            AdminUserRespDTO startUser = userMap.get(NumberUtils.parseLong(processInstance.getStartUserId()));
            taskVO.getProcessInstance().setStartUser(BeanUtils.toBean(startUser, UserSimpleBaseVO.class));

            // 【步骤4】设置流程创建时间（Flowable 返回的是 Instant，需转为 Date）
            taskVO.getProcessInstance().setCreateTime(DateUtils.of(processInstance.getStartTime()));

            // 【步骤5】设置流程摘要（动态生成）
            // 例如：模板 "{{userName}}申请请假{{days}}天" + 流程变量 {userName: "张三", days: 3}
            // => 最终生成："张三申请请假3天"
            taskVO.getProcessInstance().setSummary(
                    FlowableUtils.getSummary(
                            processDefinitionInfoMap.get(processInstance.getProcessDefinitionId()),
                            processInstance.getProcessVariables() // 流程变量（Map 形式）
                    )
            );
        });
    }

    /**
     * 构建"历史任务"分页结果（已完成/已终止的任务）
     *
     * 【业务场景】用户查看"已办任务"、"审批记录"等历史数据
     * 【与待办任务的区别】
     * - 待办任务用 Task（运行中的任务）
     * - 历史任务用 HistoricTaskInstance（已归档的历史数据，包含完成时间、审批结果等）
     *
     * @param pageResult                Flowable 的 HistoricTaskInstance 分页结果
     * @param processInstanceMap        历史流程实例映射（key: processInstanceId，value: HistoricProcessInstance）
     * @param userMap                   用户映射（key: userId，value: AdminUserRespDTO）
     * @param deptMap                   部门映射（key: deptId，value: DeptRespDTO）
     *                                  需要显示用户所属部门名称，如"研发部"、"财务部"等
     * @param processDefinitionInfoMap  流程定义扩展信息映射（key: processDefinitionId）
     * @return 包含 BpmTaskRespVO 的分页结果
     */
    default PageResult<BpmTaskRespVO> buildTaskPage(
            PageResult<HistoricTaskInstance> pageResult,
            Map<String, HistoricProcessInstance> processInstanceMap,
            Map<Long, AdminUserRespDTO> userMap,
            Map<Long, DeptRespDTO> deptMap,
            Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap) {
        // 遍历历史任务列表，逐个转换为 BpmTaskRespVO
        List<BpmTaskRespVO> taskVOList = CollectionUtils.convertList(pageResult.getList(), task -> {
            // 【步骤1】基础对象转换
            BpmTaskRespVO taskVO = BeanUtils.toBean(task, BpmTaskRespVO.class);

            // 【步骤2】设置任务状态与审批意见
            // 任务状态如：1-审批中、2-已通过、3-已拒绝、4-已取消等
            taskVO.setStatus(FlowableUtils.getTaskStatus(task))
                    // 审批意见如："同意"、"拒绝，理由是..."等
                    .setReason(FlowableUtils.getTaskReason(task));

            // 【步骤3】填充任务处理人信息（assignee = 审批人）
            AdminUserRespDTO assignUser = userMap.get(NumberUtils.parseLong(task.getAssignee()));
            if (assignUser != null) {
                taskVO.setAssigneeUser(BeanUtils.toBean(assignUser, UserSimpleBaseVO.class));
                // 关联部门名称：从 deptMap 中查找该用户的部门，并设置部门名称
                findAndThen(deptMap, assignUser.getDeptId(), dept -> taskVO.getAssigneeUser().setDeptName(dept.getName()));
            }

            // 【步骤4】填充流程实例信息（历史）
            HistoricProcessInstance processInstance = processInstanceMap.get(taskVO.getProcessInstanceId());
            if (processInstance != null) {
                // 获取流程发起人
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
        // 返回分页结果（包含转换后的列表和总数）
        return new PageResult<>(taskVOList, pageResult.getTotal());
    }

    /**
     * 根据流程实例 ID 查询其下所有历史任务，并转换为 VO 列表
     *
     * 【业务场景】查看某个流程的详细审批记录（如"请假流程"的所有审批节点）
     * 【关键点】会过滤掉"已取消"状态的任务（如流程撤回后产生的取消任务，不需要展示给用户）
     *
     * @param taskList         历史任务列表（一个流程实例下可能有多个审批节点）
     * @param formMap          表单映射（key: formId，value: BpmFormDO）
     *                        有些任务节点需要填写表单（如"请假申请表"、"报销单"等）
     * @param userMap          用户映射
     * @param deptMap          部门映射
     * @return 转换后的任务列表，**排除"已取消"状态的任务**
     */
    default List<BpmTaskRespVO> buildTaskListByProcessInstanceId(
            List<HistoricTaskInstance> taskList,
            Map<Long, BpmFormDO> formMap,
            Map<Long, AdminUserRespDTO> userMap,
            Map<Long, DeptRespDTO> deptMap) {
        return CollectionUtils.convertList(taskList, task -> {
            // 【步骤1】基础转换
            BpmTaskRespVO taskVO = BeanUtils.toBean(task, BpmTaskRespVO.class);
            Integer taskStatus = FlowableUtils.getTaskStatus(task);

            // 【步骤2】过滤掉"已取消"任务
            // 为什么要过滤？当用户撤回流程时，原任务会被标记为"已取消"，
            // 但这些任务对用户来说没有意义，不应该展示在审批记录中
            if (BpmTaskStatusEnum.isCancelStatus(taskStatus)) {
                return null; // 返回 null，后续会被 convertList 自动过滤
            }
            taskVO.setStatus(taskStatus).setReason(FlowableUtils.getTaskReason(task));

            // 【步骤3】填充表单信息（若任务绑定了表单）
            // task.getFormKey() 存储的是表单ID（字符串形式）
            BpmFormDO form = MapUtil.get(formMap, NumberUtils.parseLong(task.getFormKey()), BpmFormDO.class);
            if (form != null) {
                taskVO.setFormId(form.getId())
                        .setFormName(form.getName())             // 表单名称，如"请假申请表"
                        .setFormConf(form.getConf())             // 表单配置（JSON 格式，前端渲染用）
                        .setFormFields(form.getFields())         // 表单字段定义
                        // 提取任务级别的表单变量（用于回显已填写的数据）
                        .setFormVariables(FlowableUtils.getTaskFormVariable(task));
            }

            // 【步骤4】填充处理人（assignee）和委托人（owner）信息
            // assignee = 实际审批人
            // owner = 原审批人（当发生任务委托时，owner 是原来的人，assignee 是被委托的人）
            buildTaskAssignee(taskVO, task.getAssignee(), userMap, deptMap);
            buildTaskOwner(taskVO, task.getOwner(), userMap, deptMap);
            return taskVO;
        });
    }

    /**
     * 构建子任务列表（由父任务 ID 查询的子任务）
     *
     * 【业务场景】"加签"功能（会签/或签）
     * 例如：原本只需要部门经理审批，但经理认为需要财务总监也审批，
     * 就可以"加签"一个子任务给财务总监，两人都审批通过后，才能进入下一节点。
     *
     * @param taskList 当前父任务下的子任务列表（Task 类型，运行中的任务）
     * @param userMap  用户映射
     * @param deptMap  部门映射
     * @return 转换后的子任务 VO 列表
     */
    default List<BpmTaskRespVO> buildTaskListByParentTaskId(
            List<Task> taskList,
            Map<Long, AdminUserRespDTO> userMap,
            Map<Long, DeptRespDTO> deptMap) {
        return convertList(taskList, task -> BeanUtils.toBean(task, BpmTaskRespVO.class, taskVO -> {
            // 【填充处理人信息】
            AdminUserRespDTO assignUser = userMap.get(NumberUtils.parseLong(task.getAssignee()));
            if (assignUser != null) {
                taskVO.setAssigneeUser(BeanUtils.toBean(assignUser, UserSimpleBaseVO.class));
                DeptRespDTO dept = deptMap.get(assignUser.getDeptId());
                if (dept != null) {
                    taskVO.getAssigneeUser().setDeptName(dept.getName());
                }
            }

            // 【填充委托人（owner）信息】
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
     * 【业务场景】用户点击某个待办任务，进入审批页面
     * 【返回内容】包含任务详情、可填写的表单、可执行的操作按钮（如"通过"、"拒绝"、"转办"等）
     *
     * @param todoTask       当前待办任务
     * @param childrenTasks  该任务的子任务列表（如加签产生的任务）
     * @param buttonsSetting 操作按钮配置（不同任务节点可配置不同的操作按钮）
     *                      例如：某些节点只能"通过/拒绝"，某些节点还可以"转办/委托/加签"
     * @param form           关联的表单信息（若任务需要填写表单）
     * @return 完整的 BpmTaskRespVO（前端可直接渲染审批页面）
     */
    default BpmTaskRespVO buildTodoTask(
            Task todoTask,
            List<Task> childrenTasks,
            Map<Integer, BpmTaskRespVO.OperationButtonSetting> buttonsSetting,
            BpmFormDO form) {
        // 【步骤1】基础转换 + 设置状态
        BpmTaskRespVO bpmTaskRespVO = BeanUtils.toBean(todoTask, BpmTaskRespVO.class)
                .setStatus(FlowableUtils.getTaskStatus(todoTask))
                .setReason(FlowableUtils.getTaskReason(todoTask))
                .setButtonsSetting(buttonsSetting) // 设置操作按钮配置
                // 【步骤2】转换子任务列表，仅保留基本信息与状态
                .setChildren(convertList(childrenTasks, childTask ->
                        BeanUtils.toBean(childTask, BpmTaskRespVO.class)
                                .setStatus(FlowableUtils.getTaskStatus(childTask))
                ));

        // 【步骤3】填充表单信息（若存在）
        if (form != null) {
            bpmTaskRespVO.setFormId(form.getId())
                    .setFormName(form.getName())         // 表单名称
                    .setFormConf(form.getConf())         // 表单配置（前端渲染用）
                    .setFormFields(form.getFields());    // 表单字段定义
            // 注意：此处未设置 formVariables（表单数据），
            // 因为待办任务的表单数据通常在流程启动时获取或动态加载
        }
        return bpmTaskRespVO;
    }

    /**
     * 转换为"任务创建"消息通知 DTO
     *
     * 【业务场景】当流程流转到新节点时，需要通知下一个审批人
     * 【通知方式】站内信、邮件、短信、钉钉、企业微信等
     * 【通知内容】如"您有一条新的待办任务：张三的请假申请，请及时处理"
     *
     * @param processInstance Flowable 流程实例
     * @param startUser       流程发起人
     * @param task            新创建的任务
     * @return 消息通知请求 DTO（传递给消息服务进行发送）
     */
    default BpmMessageSendWhenTaskCreatedReqDTO convert(
            ProcessInstance processInstance,
            AdminUserRespDTO startUser,
            Task task) {
        BpmMessageSendWhenTaskCreatedReqDTO reqDTO = new BpmMessageSendWhenTaskCreatedReqDTO();
        reqDTO.setProcessInstanceId(processInstance.getProcessInstanceId())
                .setProcessInstanceName(processInstance.getName())      // 流程名称，如"请假流程"
                .setStartUserId(startUser.getId())                     // 发起人ID
                .setStartUserNickname(startUser.getNickname())         // 发起人昵称
                .setTaskId(task.getId())                               // 任务ID
                .setTaskName(task.getName())                           // 任务名称，如"部门经理审批"
                .setAssigneeUserId(NumberUtils.parseLong(task.getAssignee())); // 审批人ID（接收通知的人）
        return reqDTO;
    }

    /**
     * 填充任务的"委托人"（owner）信息
     *
     * 【概念说明】什么是委托人（owner）？
     * 当任务发生"委托"操作时：
     * - owner：原审批人（委托方）
     * - assignee：被委托人（实际处理人）
     * 例如：张三请假，原本由李四审批，但李四出差了，委托给王五审批。
     * 此时 owner=李四，assignee=王五
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
        // 将字符串类型的用户ID转为 Long，然后从 userMap 中查找用户信息
        AdminUserRespDTO ownerUser = userMap.get(NumberUtils.parseLong(taskOwner));
        if (ownerUser != null) {
            task.setOwnerUser(BeanUtils.toBean(ownerUser, UserSimpleBaseVO.class));
            // 填充委托人的部门名称
            findAndThen(deptMap, ownerUser.getDeptId(), dept -> task.getOwnerUser().setDeptName(dept.getName()));
        }
    }

    /**
     * 填充任务的"子任务"列表（用于展示任务树）
     *
     * 【业务场景】"加签"功能产生的子任务
     * 【显示效果】前端可以树形结构展示：
     * ├─ 父任务：部门经理审批（李四）
     *    ├─ 子任务1：财务总监审批（王五，加签）
     *    └─ 子任务2：人事经理审批（赵六，加签）
     *
     * @param task              当前父任务 VO
     * @param childrenTaskMap   子任务映射（key: parentId，value: 子任务列表）
     * @param userMap           用户映射
     * @param deptMap           部门映射
     */
    default void buildTaskChildren(
            BpmTaskRespVO task,
            Map<String, List<Task>> childrenTaskMap,
            Map<Long, AdminUserRespDTO> userMap,
            Map<Long, DeptRespDTO> deptMap) {
        // 根据当前任务ID，从 childrenTaskMap 中获取子任务列表
        List<Task> childTasks = childrenTaskMap.get(task.getId());
        if (CollUtil.isNotEmpty(childTasks)) {
            task.setChildren(
                    convertList(childTasks, childTask -> {
                        // 转换子任务为 VO
                        BpmTaskRespVO childTaskVO = BeanUtils.toBean(childTask, BpmTaskRespVO.class);
                        childTaskVO.setStatus(FlowableUtils.getTaskStatus(childTask));
                        // 填充子任务的委托人和处理人信息
                        buildTaskOwner(childTaskVO, childTask.getOwner(), userMap, deptMap);
                        buildTaskAssignee(childTaskVO, childTask.getAssignee(), userMap, deptMap);
                        return childTaskVO;
                    })
            );
        }
    }

    /**
     * 填充任务的"处理人"（assignee）信息
     *
     * 【概念说明】什么是处理人（assignee）？
     * assignee 是任务的实际审批人，负责处理该任务（通过/拒绝等操作）
     *
     * 【与 owner 的区别】
     * - 正常情况：assignee = 审批人，owner = null
     * - 委托情况：assignee = 被委托人（实际处理），owner = 原审批人
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
        // 将字符串类型的用户ID转为 Long，然后从 userMap 中查找用户信息
        AdminUserRespDTO assignUser = userMap.get(NumberUtils.parseLong(taskAssignee));
        if (assignUser != null) {
            task.setAssigneeUser(BeanUtils.toBean(assignUser, UserSimpleBaseVO.class));
            // 填充处理人的部门名称
            findAndThen(deptMap, assignUser.getDeptId(), dept -> task.getAssigneeUser().setDeptName(dept.getName()));
        }
    }

    /**
     * 将父任务的属性拷贝到子任务（用于"加签"场景创建新任务）
     *
     * 【为何不用 MapStruct 自动映射？】
     * TaskEntityImpl 是 Flowable 内部的实体类，包含大量运行时状态和嵌套对象。
     * 如果使用 MapStruct 深拷贝所有字段，可能会：
     * 1. 拷贝不必要的运行时状态（如数据库连接、事务状态等）
     * 2. 引发 Flowable 内部状态异常（如主键冲突、版本号错误等）
     * 3. 性能损耗（深拷贝大对象）
     *
     * 因此，这里只手动拷贝必要的业务属性，保证加签任务的正确性和独立性。
     *
     * @param parentTask 父任务（原始任务）
     * @param childTask  子任务（新创建的加签任务）
     */
    default void copyTo(TaskEntityImpl parentTask, TaskEntityImpl childTask) {
        childTask.setName(parentTask.getName());                             // 任务名称
        childTask.setDescription(parentTask.getDescription());               // 任务描述
        childTask.setCategory(parentTask.getCategory());                     // 任务分类
        childTask.setParentTaskId(parentTask.getId());                       // 【关键】设置父子关系
        childTask.setProcessDefinitionId(parentTask.getProcessDefinitionId()); // 流程定义ID
        childTask.setProcessInstanceId(parentTask.getProcessInstanceId());   // 流程实例ID
        childTask.setTaskDefinitionKey(parentTask.getTaskDefinitionKey());   // 任务定义Key（BPMN 中的节点ID）
        childTask.setTaskDefinitionId(parentTask.getTaskDefinitionId());     // 任务定义ID
        childTask.setPriority(parentTask.getPriority());                     // 任务优先级
        childTask.setCreateTime(new Date());                                 // 【重要】创建时间重置为当前时间
        childTask.setTenantId(parentTask.getTenantId());                     // 租户ID（多租户场景）

        // 【注意】以下字段不拷贝，由加签逻辑单独指定：
        // - assignee：审批人（加签时需要指定给谁）
        // - owner：委托人（加签场景下通常为空）
        // - id：任务ID（数据库自动生成）
        // - claimTime：签收时间（新任务还未签收）
        // - dueDate：截止时间（可能需要重新计算）
    }

}

