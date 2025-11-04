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

import static cn.hutool.core.collection.ListUtil.toList;

/**
 * 发起人连续多级部门的负责人 {@link BpmTaskCandidateStrategy} 实现类
 *
 * <p>这个策略用于在工作流审批中，找出流程发起人所在部门的连续多级上级部门负责人作为审批候选人。</p>
 *
 * <p><b>使用场景示例：</b></p>
 * <ul>
 *   <li>假设员工张三在"研发部"，研发部的上级是"技术中心"，技术中心的上级是"总公司"</li>
 *   <li>如果参数 level=2，则会找出"研发部负责人"和"技术中心负责人"这两级的负责人</li>
 *   <li>如果参数 level=3，则会找出"研发部负责人"、"技术中心负责人"、"总公司负责人"这三级的负责人</li>
 * </ul>
 *
 * @author jason
 */
@Component // 标记为Spring组件，自动注册到Spring容器中
public class BpmTaskCandidateStartUserDeptLeaderMultiStrategy extends AbstractBpmTaskCandidateDeptLeaderStrategy {

    /**
     * 流程实例服务，用于查询流程实例信息
     *
     * @Resource 注解表示依赖注入，Spring会自动将 BpmProcessInstanceService 的实例注入到这个字段
     * @Lazy 注解表示延迟加载，避免循环依赖问题（只有在真正使用时才初始化）
     */
    @Resource
    @Lazy
    private BpmProcessInstanceService processInstanceService;

    /**
     * 返回当前策略的类型枚举
     *
     * <p>这个方法告诉系统，当前策略是"发起人连续多级部门负责人"策略</p>
     *
     * @return 策略枚举值 START_USER_DEPT_LEADER_MULTI
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.START_USER_DEPT_LEADER_MULTI;
    }

    /**
     * 验证参数是否合法
     *
     * <p>在使用这个策略时，需要传入一个参数（level），表示要找几级的部门负责人</p>
     * <p>这个方法会检查参数是否合法，必须是大于0的整数</p>
     *
     * @param param 参数字符串，表示部门的层级数（如"1"、"2"、"3"等）
     * @throws IllegalArgumentException 如果层级小于或等于0，则抛出异常
     */
    @Override
    public void validateParam(String param) {
        int level = Integer.parseInt(param); // 将字符串参数转换为整数
        Assert.isTrue(level > 0, "部门的层级必须大于 0"); // 断言：层级必须大于0，否则抛出异常
    }

    /**
     * 在任务执行时，计算哪些用户可以作为候选人（审批人）
     *
     * <p><b>执行流程：</b></p>
     * <ol>
     *   <li>解析传入的参数，得到要查找的层级数</li>
     *   <li>通过流程实例ID获取流程实例对象</li>
     *   <li>从流程实例中提取出流程发起人的用户ID</li>
     *   <li>根据发起人ID查询其所在的部门</li>
     *   <li>向上查找连续多级部门的负责人</li>
     * </ol>
     *
     * @param execution 流程执行上下文，包含当前流程的运行信息（如流程实例ID、变量等）
     * @param param     参数，表示要查找的部门层级数
     * @return 候选人用户ID的集合，如果没有找到则返回空集合
     */
    @Override
    public Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        int level = Integer.parseInt(param); // 将参数转换为整数，表示要查找的层级数

        // 步骤1：获得流程实例对象
        // execution.getProcessInstanceId() 获取当前流程的实例ID
        ProcessInstance processInstance = processInstanceService.getProcessInstance(execution.getProcessInstanceId());

        // 步骤2：从流程实例中获取发起人的用户ID
        // processInstance.getStartUserId() 返回字符串类型的用户ID，需要转换为Long类型
        Long startUserId = NumberUtils.parseLong(processInstance.getStartUserId());

        // 步骤3：根据发起人的用户ID，查询其所在的部门信息
        // super.getStartUserDept() 是父类提供的方法，用于获取用户所在的部门
        DeptRespDTO dept = super.getStartUserDept(startUserId);

        // 步骤4：如果部门不存在（用户可能没有部门），返回空集合
        if (dept == null) {
            return new HashSet<>();
        }

        // 步骤5：获取连续多级部门的负责人ID集合
        // toList(dept.getId()) 将部门ID转换为List
        // super.getMultiLevelDeptLeaderIds() 是父类提供的方法，用于向上查找连续多级部门的负责人
        // 例如：level=2 时，会找出当前部门和上一级部门的负责人
        return super.getMultiLevelDeptLeaderIds(toList(dept.getId()), level);
    }

    /**
     * 在活动节点（审批节点）上计算哪些用户可以作为候选人
     *
     * <p>这个方法通常用于流程设计阶段或预测阶段，提前计算某个节点的候选人</p>
     * <p>与 calculateUsersByTask 不同，这个方法不依赖实际运行中的流程实例，而是基于给定的参数计算</p>
     *
     * @param bpmnModel           BPMN模型对象，包含整个流程的定义信息
     * @param activityId          活动节点ID，表示要计算哪个节点的候选人
     * @param param               参数，表示要查找的部门层级数
     * @param startUserId         流程发起人的用户ID
     * @param processDefinitionId 流程定义ID
     * @param processVariables    流程变量Map，包含流程中的各种变量
     * @return 候选人用户ID的集合，如果没有找到则返回空集合
     */
    @Override
    public Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId, String param,
                                              Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        int level = Integer.parseInt(param); // 将参数转换为整数，表示要查找的层级数

        // 步骤1：根据发起人的用户ID，查询其所在的部门信息
        DeptRespDTO dept = super.getStartUserDept(startUserId);

        // 步骤2：如果部门不存在，返回空集合
        if (dept == null) {
            return new HashSet<>();
        }

        // 步骤3：获取连续多级部门的负责人ID集合
        // 逻辑与 calculateUsersByTask 方法相同，只是调用时机不同
        return super.getMultiLevelDeptLeaderIds(toList(dept.getId()), level);
    }

}
