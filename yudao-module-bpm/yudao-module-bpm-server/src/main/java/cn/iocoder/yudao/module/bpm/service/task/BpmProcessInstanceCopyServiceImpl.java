package cn.iocoder.yudao.module.bpm.service.task;

import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCopyPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceCopyDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.task.BpmProcessInstanceCopyMapper;
import cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;

/**
 * 流程抄送（Carbon Copy）服务实现类。
 *
 * <p>“抄送”通常指在流程执行过程中，将当前任务或流程实例的信息通知给其他用户（但不参与流程审批），
 * 以供其知悉当前流程状态。本服务负责创建、查询和删除抄送记录。</p>
 *
 * @author kyle
 */
@Service
@Validated
@Slf4j
public class BpmProcessInstanceCopyServiceImpl implements BpmProcessInstanceCopyService {

    /**
     * 抄送记录的数据访问层（DAO）。
     */
    @Resource
    private BpmProcessInstanceCopyMapper processInstanceCopyMapper;

    /**
     * BPM 任务服务，用于获取任务详情。
     * 使用 @Lazy 注解避免与其他 BPM 服务（如本类引用的 processInstanceService）形成循环依赖。
     */
    @Resource
    @Lazy
    private BpmTaskService taskService;

    /**
     * BPM 流程实例服务，用于获取流程实例信息。
     * 使用 @Lazy 注解解决潜在的循环依赖问题。
     */
    @Resource
    @Lazy
    private BpmProcessInstanceService processInstanceService;

    /**
     * BPM 流程定义服务，用于获取流程定义元数据（如分类、名称等）。
     * 同样使用 @Lazy 避免循环依赖。
     */
    @Resource
    @Lazy
    private BpmProcessDefinitionService processDefinitionService;

    /**
     * 根据任务 ID 创建流程抄送记录。
     *
     * <p>该方法适用于在某个具体任务节点上触发抄送操作，系统会自动从任务中提取流程实例 ID、任务定义 Key 等信息。</p>
     *
     * @param userIds 要抄送给的用户 ID 集合（必须非空）
     * @param reason 抄送原因（可为 null 或空字符串）
     * @param taskId Flowable 中的任务 ID，用于定位当前流程节点
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 如果任务不存在
     */
    @Override
    public void createProcessInstanceCopy(Collection<Long> userIds, String reason, String taskId) {
        // 1. 根据 taskId 查询 Flowable 中的任务信息
        Task task = taskService.getTask(taskId);
        if (ObjectUtil.isNull(task)) {
            throw exception(ErrorCodeConstants.TASK_NOT_EXISTS); // 任务不存在，抛出异常
        }

        // 2. 委托给重载方法，填充更多上下文信息
        createProcessInstanceCopy(
                userIds,
                reason,
                task.getProcessInstanceId(),     // 流程实例 ID
                task.getTaskDefinitionKey(),     // 任务定义 Key（即 BPMN 中的 task id）
                task.getId(),                    // 当前任务 ID
                task.getName()                   // 任务名称（用于展示）
        );
    }

    /**
     * 根据流程实例信息创建抄送记录（底层核心方法）。
     *
     * <p>该方法接收完整的流程上下文信息，用于持久化抄送记录到数据库。</p>
     *
     * @param userIds 被抄送的用户 ID 列表
     * @param reason 抄送原因（可选）
     * @param processInstanceId Flowable 流程实例 ID
     * @param activityId 当前活动节点 ID（即任务定义 Key）
     * @param activityName 当前活动节点名称（即任务名称）
     * @param taskId 触发抄送的具体任务 ID（用于关联）
     */
    @Override
    public void createProcessInstanceCopy(Collection<Long> userIds, String reason, String processInstanceId,
                                          String activityId, String activityName, String taskId) {
        // 1.1 验证流程实例是否存在
        ProcessInstance processInstance = processInstanceService.getProcessInstance(processInstanceId);
        if (processInstance == null) {
            throw exception(ErrorCodeConstants.PROCESS_INSTANCE_NOT_EXISTS);
        }

        // 1.2 验证流程定义是否存在（确保元数据完整）
        ProcessDefinition processDefinition = processDefinitionService.getProcessDefinition(
                processInstance.getProcessDefinitionId());
        if (processDefinition == null) {
            throw exception(ErrorCodeConstants.PROCESS_DEFINITION_NOT_EXISTS);
        }

        // 2. 构建抄送记录列表
        List<BpmProcessInstanceCopyDO> copyList = convertList(userIds, userId -> {
            BpmProcessInstanceCopyDO copy = new BpmProcessInstanceCopyDO();
            copy.setUserId(userId);                                      // 被抄送人
            copy.setReason(reason);                                      // 抄送原因
            copy.setStartUserId(Long.valueOf(processInstance.getStartUserId())); // 流程发起人 ID
            copy.setProcessInstanceId(processInstanceId);                // 流程实例 ID
            copy.setProcessInstanceName(processInstance.getName());      // 流程实例名称（可能为 null）
            copy.setCategory(processDefinition.getCategory());           // 流程分类（用于业务分组）
            copy.setTaskId(taskId);                                      // 关联的任务 ID
            copy.setActivityId(activityId);                              // 当前节点 ID
            copy.setActivityName(activityName);                          // 当前节点名称
            copy.setProcessDefinitionId(processInstance.getProcessDefinitionId()); // 流程定义 ID
            return copy;
        });

        // 3. 批量插入抄送记录到数据库
        processInstanceCopyMapper.insertBatch(copyList);
    }

    /**
     * 分页查询某用户的抄送记录列表。
     *
     * <p>用于用户中心或“我的抄送”页面，展示该用户收到的所有抄送任务。</p>
     *
     * @param userId 当前登录用户的 ID
     * @param pageReqVO 分页与查询条件（如流程名称、时间范围等）
     * @return 分页结果，包含抄送记录列表
     */
    @Override
    public PageResult<BpmProcessInstanceCopyDO> getProcessInstanceCopyPage(Long userId,
                                                                           BpmProcessInstanceCopyPageReqVO pageReqVO) {
        return processInstanceCopyMapper.selectPage(userId, pageReqVO);
    }

    /**
     * 删除指定流程实例的所有抄送记录。
     *
     * <p>通常在流程实例被删除或归档时调用，保持数据一致性。</p>
     *
     * @param processInstanceId Flowable 流程实例 ID
     */
    @Override
    public void deleteProcessInstanceCopy(String processInstanceId) {
        processInstanceCopyMapper.deleteByProcessInstanceId(processInstanceId);
    }

}