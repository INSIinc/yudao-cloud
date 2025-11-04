package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.user;

import cn.iocoder.yudao.framework.common.util.collection.SetUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import jakarta.annotation.Resource;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 发起人自己 {@link BpmTaskCandidateUserStrategy} 实现类
 * <p>
 * 适合场景：用于需要发起人信息复核等场景
 *
 * @author jason
 */
// Spring组件注解，表示这是一个Spring管理的Bean，会被自动注册到Spring容器中
@Component
public class BpmTaskCandidateStartUserStrategy implements BpmTaskCandidateStrategy {

    // 流程实例服务，用于查询流程实例的相关信息
    // @Resource注解：用于依赖注入，Spring会自动注入BpmProcessInstanceService的实例
    // @Lazy注解：延迟加载，只有在真正使用时才会初始化，可以避免循环依赖的问题
    @Resource
    @Lazy // 延迟加载，避免循环依赖
    private BpmProcessInstanceService processInstanceService;

    /**
     * 获取策略类型
     *
     * @return 返回START_USER策略枚举，表示这是"发起人自己"的候选人策略
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.START_USER;
    }

    /**
     * 验证参数是否有效
     *
     * @param param 策略参数
     * 说明：由于"发起人自己"策略不需要额外参数，所以这个方法是空实现
     */
    @Override
    public void validateParam(String param) {
        // 发起人策略不需要参数验证，所以方法体为空
    }

    /**
     * 判断是否需要参数
     *
     * @return false表示该策略不需要参数配置
     * 说明：因为发起人是固定的，不需要额外传入参数来指定
     */
    @Override
    public boolean isParamRequired() {
        return false;
    }

    /**
     * 根据任务执行上下文计算候选用户
     * 这个方法在流程实际运行时被调用
     *
     * @param execution 流程执行上下文，包含当前流程实例的各种信息
     * @param param 策略参数（本策略中不使用）
     * @return 返回候选用户ID的集合，这里只包含流程发起人的ID
     */
    @Override
    public Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        // 1. 通过流程实例ID获取流程实例对象
        ProcessInstance processInstance = processInstanceService.getProcessInstance(execution.getProcessInstanceId());
        // 2. 获取流程发起人的用户ID，并将String类型转换为Long类型
        // 3. 使用SetUtils工具类将单个用户ID包装成Set集合返回
        return SetUtils.asSet(Long.valueOf(processInstance.getStartUserId()));
    }

    /**
     * 根据活动节点计算候选用户
     * 这个方法主要用于流程设计时的预览和模拟，或者在流程启动前的计算
     *
     * @param bpmnModel 流程模型对象，包含流程定义的结构信息
     * @param activityId 活动节点ID，表示当前要计算的任务节点
     * @param param 策略参数（本策略中不使用）
     * @param startUserId 流程发起人的用户ID
     * @param processDefinitionId 流程定义ID
     * @param processVariables 流程变量Map
     * @return 返回候选用户ID的集合，这里直接返回发起人的ID
     */
    @Override
    public Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId, String param,
                                              Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        // 因为策略是"发起人自己"，所以直接返回发起人ID组成的Set集合
        // startUserId参数已经包含了发起人ID，不需要额外查询
        return SetUtils.asSet(startUserId);
    }

}
