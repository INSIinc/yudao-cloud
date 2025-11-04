package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept;

import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import jakarta.annotation.Resource;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.util.collection.SetUtils.asSet;

/**
 * 发起人的部门负责人策略 - 任务候选人计算策略实现类
 *
 * <p>功能说明：
 * 这个类用于在工作流（Flowable）中，自动确定某个任务节点的审批人（候选人）。
 * 具体规则是：根据"流程发起人"所在的部门，找到该部门或其上级部门的负责人作为审批人。
 *
 * <p>使用场景举例：
 * 假设小王提交了一个请假申请：
 * - 如果参数设置为 1，则找小王所在部门的负责人（如部门经理）来审批
 * - 如果参数设置为 2，则找小王所在部门的上一级部门负责人（如总监）来审批
 * - 如果参数设置为 3，则找更上一级的负责人（如副总）来审批
 *
 * <p>技术说明：
 * 这个类继承自 AbstractBpmTaskCandidateDeptLeaderStrategy（抽象部门负责人策略基类），
 * 实现了 BpmTaskCandidateStrategy 接口，是 Flowable 工作流引擎中候选人策略的一种实现。
 *
 * @author jason
 */
@Component // Spring 注解：标记这是一个 Spring Bean 组件，会被自动扫描并注册到容器中
public class BpmTaskCandidateStartUserDeptLeaderStrategy extends AbstractBpmTaskCandidateDeptLeaderStrategy {

    /**
     * 注入流程实例服务
     *
     * <p>用途：通过这个服务可以查询正在运行的流程实例信息，比如流程的发起人是谁。
     *
     * <p>@Resource 注解：表示这是一个需要注入的依赖，Spring 会自动把对应的服务实例赋值给这个变量
     * <p>@Lazy 注解：延迟加载，避免循环依赖问题（即两个类互相依赖导致启动失败）
     */
    @Resource
    @Lazy // 避免循环依赖
    private BpmProcessInstanceService processInstanceService;

    /**
     * 获取当前策略的类型标识
     *
     * <p>返回值说明：
     * START_USER_DEPT_LEADER 表示"发起人的部门负责人"策略。
     * 这个标识会在系统中用来区分不同的候选人计算策略。
     *
     * @return 策略枚举类型
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.START_USER_DEPT_LEADER;
    }

    /**
     * 验证策略参数是否合法
     *
     * <p>参数说明：
     * param 是一个字符串形式的数字，表示"部门层级"。
     * - 比如 "1" 表示发起人所在部门（第 1 层）
     * - "2" 表示发起人所在部门的上级部门（第 2 层）
     * - "3" 表示更上一级部门（第 3 层），以此类推
     *
     * <p>验证规则：
     * 这个层级数必须大于 0，因为至少要从发起人的部门开始查找。
     * 如果传入 0 或负数，会抛出异常并提示错误信息。
     *
     * @param param 部门层级参数（字符串格式的正整数）
     * @throws IllegalArgumentException 如果参数不是正整数
     */
    @Override
    public void validateParam(String param) {
        // 参数是部门的层级，必须是大于 0 的正整数
        Assert.isTrue(Integer.parseInt(param) > 0, "部门的层级必须大于 0");
    }

    /**
     * 在任务执行时计算候选人（方式一：基于任务执行上下文）
     *
     * <p>调用时机：
     * 当工作流引擎执行到某个用户任务节点时，会调用这个方法来确定谁可以处理这个任务。
     *
     * <p>实现逻辑：
     * 1. 从执行上下文中获取流程实例 ID
     * 2. 通过流程实例 ID 查询流程实例对象，从中获取流程发起人的用户 ID
     * 3. 根据发起人 ID 和层级参数，查找对应的部门负责人
     * 4. 返回负责人的用户 ID 集合（作为任务的候选人）
     *
     * @param execution 任务执行上下文对象，包含流程实例 ID、变量等信息
     * @param param 部门层级参数（如 "1"、"2"、"3"）
     * @return 候选人用户 ID 的集合（可能包含 0 个或多个用户 ID）
     */
    @Override
    public Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        // 第一步：获得流程发起人的用户 ID
        // 先通过 execution 获取流程实例 ID，再查询流程实例对象
        ProcessInstance processInstance = processInstanceService.getProcessInstance(execution.getProcessInstanceId());
        // 流程实例中的 StartUserId 是字符串格式，需要转换为 Long 类型
        Long startUserId = NumberUtils.parseLong(processInstance.getStartUserId());

        // 第二步：根据发起人和层级参数，获取发起人的部门负责人
        return getStartUserDeptLeader(startUserId, param);
    }

    /**
     * 在流程设计时预计算候选人（方式二：基于流程定义）
     *
     * <p>调用时机：
     * 在流程设计阶段或者需要提前预览候选人时调用，此时流程还没有真正启动。
     *
     * <p>与 calculateUsersByTask 的区别：
     * - calculateUsersByTask：流程运行时调用，从实际的流程实例中获取发起人
     * - calculateUsersByActivity：流程设计时调用，需要手动传入发起人 ID
     *
     * <p>实现逻辑：
     * 直接使用传入的 startUserId 参数，结合层级参数查找部门负责人。
     *
     * @param bpmnModel BPMN 流程模型对象（包含流程图的所有节点和连线信息）
     * @param activityId 活动节点 ID（当前任务节点在流程图中的唯一标识）
     * @param param 部门层级参数（如 "1"、"2"、"3"）
     * @param startUserId 流程发起人的用户 ID（手动传入）
     * @param processDefinitionId 流程定义 ID
     * @param processVariables 流程变量（流程中的动态数据）
     * @return 候选人用户 ID 的集合
     */
    @Override
    public Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId, String param,
                                              Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        // 获取发起人的部门负责人（逻辑与 calculateUsersByTask 相同）
        return getStartUserDeptLeader(startUserId, param);
    }

    /**
     * 私有辅助方法：根据发起人 ID 和层级参数，查找部门负责人
     *
     * <p>核心业务逻辑：
     * 1. 解析层级参数（字符串转整数）
     * 2. 获取发起人所在的部门信息
     * 3. 如果发起人没有部门，返回空集合
     * 4. 根据层级向上查找对应级别的部门负责人
     * 5. 如果找到负责人，返回包含该负责人 ID 的集合；否则返回空集合
     *
     * <p>举例说明：
     * 假设组织架构为：研发部（张三为负责人）-> 技术中心（李四为负责人）-> 公司（王五为负责人）
     * 发起人小王在研发部：
     * - level = 1：返回张三（研发部负责人）
     * - level = 2：返回李四（技术中心负责人）
     * - level = 3：返回王五（公司负责人）
     *
     * @param startUserId 流程发起人的用户 ID
     * @param param 部门层级参数（字符串格式）
     * @return 部门负责人的用户 ID 集合（可能为空集合）
     */
    private Set<Long> getStartUserDeptLeader(Long startUserId, String param) {
        // 第一步：将字符串参数转换为整数，表示要找第几层的部门负责人
        int level = Integer.parseInt(param); // 参数是部门的层级

        // 第二步：获取发起人所在的部门信息
        // super.getStartUserDept() 是从父类继承的方法，用于查询用户所在部门
        DeptRespDTO dept = super.getStartUserDept(startUserId);

        // 第三步：如果发起人没有所属部门，无法查找负责人，返回空集合
        if (dept == null) {
            return new HashSet<>();
        }

        // 第四步：根据部门和层级，向上查找对应层级的部门负责人 ID
        // super.getAssignLevelDeptLeaderId() 是从父类继承的方法，用于向上查找指定层级的负责人
        Long deptLeaderId = super.getAssignLevelDeptLeaderId(dept, level);

        // 第五步：返回结果
        // 如果找到负责人（deptLeaderId 不为 null），用 asSet() 方法将其包装成集合返回
        // 如果没找到负责人（deptLeaderId 为 null），返回空的 HashSet
        return deptLeaderId != null ? asSet(deptLeaderId) : new HashSet<>();
    }

}
