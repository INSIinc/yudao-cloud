package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.user.BpmTaskCandidateUserStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import com.google.common.collect.Sets;
import jakarta.annotation.Resource;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 审批人自选策略实现类
 *
 * ========== 初学者必读：这个类是做什么的？==========
 *
 * 在企业的审批流程中，有时候需要"人工选择下一个审批人"，而不是系统自动指定。
 * 比如：张三提交了一个报销申请，领导李四审批时，可以选择让财务王五或者赵六来处理下一步。
 *
 * 这个类就是负责实现这种"审批人自选"功能的核心组件。
 *
 * ========== 工作流程说明 ==========
 *
 * 1. 【提交阶段】用户在前端界面选择下一节点的审批人（可以选一个或多个）
 * 2. 【保存阶段】选择的审批人ID会被保存到流程变量中（类似于一个Map：节点ID -> 审批人列表）
 * 3. 【执行阶段】当流程走到某个节点时，Flowable引擎会调用这个类
 * 4. 【查找阶段】这个类从流程变量中找出该节点对应的审批人列表
 * 5. 【返回阶段】把审批人ID列表返回给Flowable，由它创建待办任务
 *
 * ========== 为什么继承 AbstractBpmTaskCandidateDeptLeaderStrategy？==========
 *
 * 虽然这个类继承了"部门领导策略"的抽象类，但实际上并不使用部门领导的逻辑。
 * 这样做是为了：
 * - 复用父类中的一些通用方法和接口定义
 * - 保持策略体系的统一性（所有策略都实现相同的接口）
 *
 * @author smallNorthLee
 */
@Component // 告诉Spring这是一个Bean，会自动创建实例并管理
public class BpmTaskCandidateApproveUserSelectStrategy extends AbstractBpmTaskCandidateDeptLeaderStrategy {

    /**
     * 流程实例服务
     *
     * ========== 这个服务是做什么的？==========
     *
     * 这是一个工具类，用来查询流程实例的信息。
     * 流程实例就是一次具体的审批流程，比如：
     * - 张三在2024年1月5日提交的报销申请（这就是一个流程实例）
     * - 李四在2024年1月6日提交的请假申请（这又是另一个流程实例）
     *
     * ========== 为什么要用 @Lazy？==========
     *
     * @Lazy 表示"懒加载"，即"用到的时候再初始化"。
     * 这样做是为了避免"循环依赖"问题：
     * - A类需要B类才能创建
     * - B类需要A类才能创建
     * - 结果谁也创建不了，程序启动就报错
     *
     * 使用@Lazy后，Spring会先创建一个"代理对象"，等真正调用时再初始化真实对象。
     */
    @Resource // 告诉Spring自动注入这个依赖
    @Lazy // 延迟加载，避免循环依赖
    private BpmProcessInstanceService processInstanceService;

    /**
     * 返回策略类型标识
     *
     * ========== 这个方法是做什么的？==========
     *
     * 系统中可能有很多种审批人选择策略，比如：
     * - 按部门选择
     * - 按角色选择
     * - 按职位选择
     * - 审批人自选（就是当前这个类）
     *
     * 每个策略都有一个唯一的"标识符"（枚举值），用来区分不同的策略。
     * 这个方法就是返回"审批人自选"这个策略的标识符。
     *
     * 当Flowable引擎需要计算审批人时，会根据这个标识符找到对应的策略类。
     *
     * @return 策略类型枚举：APPROVE_USER_SELECT（审批人自选）
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.APPROVE_USER_SELECT;
    }

    /**
     * 验证策略参数是否合法
     *
     * ========== 为什么这个方法是空的？==========
     *
     * 不同的策略可能需要不同的配置参数，比如：
     * - "按部门选择"策略需要配置部门ID
     * - "按角色选择"策略需要配置角色编码
     *
     * 但是"审批人自选"策略不需要任何配置参数，因为：
     * - 审批人是用户在运行时手动选择的
     * - 选择结果保存在流程变量中
     * - 不需要提前在系统中配置
     *
     * 所以这个方法什么也不做（但必须实现，因为父类要求）。
     *
     * @param param 策略参数（对于本策略来说用不到）
     */
    @Override
    public void validateParam(String param) {
        // 无需校验参数，因为审批人自选策略不依赖任何配置参数
    }

    /**
     * 判断这个策略是否需要配置参数
     *
     * ========== 返回值的含义 ==========
     *
     * - true：表示这个策略需要配置参数（比如部门ID、角色编码等）
     * - false：表示这个策略不需要配置参数
     *
     * "审批人自选"策略不需要预先配置参数，所以返回 false。
     *
     * ========== 这个方法有什么用？==========
     *
     * 前端界面会根据这个方法的返回值，决定是否显示"参数配置"输入框。
     * 如果返回 false，前端就不会显示配置框，因为没必要。
     *
     * @return false - 不需要配置参数
     */
    @Override
    public boolean isParamRequired() {
        return false;
    }

    /**
     * 【核心方法1】在任务创建时计算审批人列表
     *
     * ========== 这个方法什么时候被调用？==========
     *
     * 当流程执行到某个审批节点时，Flowable引擎需要创建一个"待办任务"。
     * 创建任务前，引擎会调用这个方法，询问：这个任务应该分配给谁？
     *
     * ========== 方法的执行步骤 ==========
     *
     * 第1步：根据流程实例ID，查询完整的流程实例对象
     * 第2步：从流程实例的变量中，提取"审批人自选映射表"
     *       （这个映射表是前端传过来的，格式是：节点ID -> 审批人ID列表）
     * 第3步：根据当前节点ID，从映射表中找出对应的审批人列表
     * 第4步：把审批人ID列表转成 LinkedHashSet 返回
     *       （LinkedHashSet 既能去重，又能保持插入顺序）
     *
     * ========== 参数说明 ==========
     *
     * @param execution Flowable执行上下文（包含流程实例ID、当前节点ID等信息）
     * @param param     策略参数（本策略用不到，所以忽略）
     *
     * @return 审批人用户ID集合（LinkedHashSet确保顺序且不重复）
     */
    @Override
    public LinkedHashSet<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        // 第1步：通过流程实例ID查询流程实例对象
        // execution.getProcessInstanceId() 获取当前流程的实例ID（类似订单号）
        ProcessInstance processInstance = processInstanceService.getProcessInstance(execution.getProcessInstanceId());

        // 断言：流程实例必须存在，否则抛出异常
        // 这是一种防御性编程，确保数据完整性
        Assert.notNull(processInstance, "流程实例({})不能为空", execution.getProcessInstanceId());

        // 第2步：从流程实例的变量中提取"审批人自选映射表"
        // 这个映射表的结构：Map<节点ID, List<用户ID>>
        // 例如：{"task1" -> [100, 200], "task2" -> [300]}
        // 表示 task1 节点由用户100和200处理，task2 节点由用户300处理
        Map<String, List<Long>> approveUserSelectAssignees = FlowableUtils.getApproveUserSelectAssignees(processInstance);

        // 断言：映射表必须存在，否则说明前端没有选择审批人
        Assert.notNull(approveUserSelectAssignees, "流程实例({}) 的下一个执行节点审批人不能为空",
                execution.getProcessInstanceId());

        // 第3步：双重检查，虽然上面已经断言非空，但为了代码健壮性再检查一次
        // （防御性编程：宁可多写几行代码，也不能让程序崩溃）
        if (approveUserSelectAssignees == null) {
            return Sets.newLinkedHashSet(); // 返回空集合（不是null）
        }

        // 第4步：根据当前节点ID，从映射表中查找对应的审批人列表
        // execution.getCurrentActivityId() 获取当前活动节点的ID
        List<Long> assignees = approveUserSelectAssignees.get(execution.getCurrentActivityId());

        // 第5步：如果找到了审批人列表，转成LinkedHashSet返回；否则返回空集合
        // CollUtil.isNotEmpty() 判断集合是否非空（hutool工具类提供）
        // LinkedHashSet 的优点：既能去重，又能保持顺序
        return CollUtil.isNotEmpty(assignees) ? new LinkedHashSet<>(assignees) : Sets.newLinkedHashSet();
    }

    /**
     * 【核心方法2】在流程预测时计算审批人列表
     *
     * ========== 这个方法什么时候被调用？==========
     *
     * 这个方法用于"流程预测"或"流程模拟"场景，比如：
     * - 用户想在提交申请前，预览一下整个审批流程会经过哪些人
     * - 管理员想查看某个流程定义的审批路径
     * - 前端需要高亮显示流程图中的节点
     *
     * 这时候流程还没真正运行，所以不能用 execution 对象。
     * 只能根据传入的"流程变量快照"来模拟计算。
     *
     * ========== 与 calculateUsersByTask 的区别 ==========
     *
     * calculateUsersByTask：
     * - 用于真实流程执行时
     * - 通过 execution 对象获取实时数据
     * - 如果找不到审批人会抛异常（因为流程不能继续）
     *
     * calculateUsersByActivity：
     * - 用于流程预测/模拟时
     * - 通过传入的 processVariables 参数获取数据
     * - 如果找不到审批人返回空集合（允许预测继续）
     *
     * ========== 参数说明 ==========
     *
     * @param bpmnModel             BPMN流程模型（本策略用不到）
     * @param activityId            要查询的活动节点ID
     * @param param                 策略参数（本策略用不到）
     * @param startUserId           流程发起人ID（本策略用不到）
     * @param processDefinitionId   流程定义ID（本策略用不到）
     * @param processVariables      流程变量快照（包含审批人选择结果）
     *
     * @return 审批人用户ID集合（可能为空，不会抛异常）
     */
    @Override
    public LinkedHashSet<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId, String param,
                                                        Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        // 第1步：检查流程变量是否为空
        // 如果为空，说明没有任何数据，直接返回空集合
        if (processVariables == null) {
            return Sets.newLinkedHashSet(); // 返回空集合，不抛异常（允许预测继续）
        }

        // 第2步：从流程变量中提取"审批人自选映射表"
        // 这个映射表是前端在发起流程时传入的
        Map<String, List<Long>> approveUserSelectAssignees = FlowableUtils.getApproveUserSelectAssignees(processVariables);

        // 如果映射表为空，说明用户还没选择审批人，返回空集合
        // （在预测场景下，这是允许的，前端会提示用户"请选择审批人"）
        if (approveUserSelectAssignees == null) {
            return Sets.newLinkedHashSet();
        }

        // 第3步：根据指定的节点ID，从映射表中查找审批人列表
        List<Long> assignees = approveUserSelectAssignees.get(activityId);

        // 第4步：如果找到了审批人，转成LinkedHashSet返回；否则返回空集合
        return CollUtil.isNotEmpty(assignees) ? new LinkedHashSet<>(assignees) : Sets.newLinkedHashSet();
    }

}