package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.user;

// 导入 Hutool 工具类中的字符串常量池，用于获取常用的分隔符（如逗号）
import cn.hutool.core.text.StrPool;
// 导入项目自定义的字符串工具类，用于字符串分割和转换操作
import cn.iocoder.yudao.framework.common.util.string.StrUtils;
// 导入 BPM 任务候选人策略接口，所有策略都需要实现这个接口
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
// 导入 BPM 任务候选人策略枚举类，用于标识不同的策略类型
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
// 导入系统用户 API 接口，用于验证和查询用户信息
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
// Jakarta 注解，用于依赖注入（等同于 @Autowired）
import jakarta.annotation.Resource;
// Spring 组件注解，标识这是一个 Spring 管理的 Bean
import org.springframework.stereotype.Component;

// 导入 LinkedHashSet，它是一个有序且不重复的集合
import java.util.LinkedHashSet;

/**
 * 用户策略 - BPM 任务候选人策略实现类
 *
 * <p>功能说明：
 * 这个类实现了"指定用户"策略，即在工作流审批节点中，直接指定某些具体用户作为任务的候选人。
 *
 * <p>使用场景举例：
 * 在配置审批流程时，管理员可以直接指定用户 ID（如：1,2,3），这些用户将成为该审批节点的候选人。
 *
 * <p>工作原理：
 * 1. 参数验证阶段：检查传入的用户 ID 字符串是否合法，用户是否存在
 * 2. 计算候选人阶段：将用户 ID 字符串解析成用户 ID 集合
 *
 * @author kyle
 */
@Component  // 标记为 Spring 组件，在应用启动时自动注册到 Spring 容器中
public class BpmTaskCandidateUserStrategy implements BpmTaskCandidateStrategy {

    /**
     * 用户管理 API 接口
     *
     * <p>作用：
     * 提供用户相关的操作能力，比如验证用户是否存在、查询用户信息等
     *
     * <p>注入方式：
     * 使用 @Resource 注解，Spring 会自动将对应的实现类注入到这个字段中
     */
    @Resource
    private AdminUserApi adminUserApi;

    /**
     * 返回当前策略的类型标识
     *
     * <p>返回值说明：
     * 返回 USER 策略枚举，表示这是"指定用户"策略
     *
     * <p>应用场景：
     * 在流程引擎选择具体策略实现时，会通过这个方法返回的枚举值来匹配对应的策略类
     *
     * @return BpmTaskCandidateStrategyEnum.USER - "指定用户"策略的枚举标识
     */
    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.USER;
    }

    /**
     * 验证参数是否合法
     *
     * <p>参数说明：
     * param - 用户 ID 字符串，格式如："1,2,3" 或 "100,200"
     *
     * <p>验证逻辑：
     * 1. 将字符串按逗号分割成 Long 类型的用户 ID 集合
     * 2. 调用用户 API 验证这些用户 ID 是否都存在于系统中
     * 3. 如果有不存在的用户 ID，会抛出异常
     *
     * <p>使用时机：
     * 在流程设计器配置审批节点时，或者在流程部署前，会调用此方法验证配置的用户 ID 是否正确
     *
     * @param param 用户 ID 字符串，多个 ID 用逗号分隔，例如："1,2,3"
     * @throws IllegalArgumentException 当用户 ID 不存在时抛出异常
     */
    @Override
    public void validateParam(String param) {
        // 调用用户 API 的 validateUserList 方法验证用户列表是否有效
        // StrUtils.splitToLongSet(param) 将字符串 "1,2,3" 转换为 Set<Long> {1, 2, 3}
        // checkError() 如果验证失败会抛出异常
        adminUserApi.validateUserList(StrUtils.splitToLongSet(param)).checkError();
    }

    /**
     * 计算任务的候选用户列表
     *
     * <p>参数说明：
     * param - 用户 ID 字符串，格式如："1,2,3"
     *
     * <p>处理逻辑：
     * 将字符串参数解析成用户 ID 的集合
     * 例如："1,2,3" 会被解析成 LinkedHashSet {1L, 2L, 3L}
     *
     * <p>返回值说明：
     * 返回一个 LinkedHashSet，特点是：
     * - 保持插入顺序（按照字符串中 ID 的顺序）
     * - 不允许重复元素（即使参数中有重复 ID，集合中也只会保留一个）
     *
     * <p>使用时机：
     * 当流程引擎执行到某个审批节点时，会调用此方法获取该节点的候选用户列表
     * 这些用户将在审批系统的待办任务列表中看到这个任务
     *
     * @param param 用户 ID 字符串，多个 ID 用逗号分隔，例如："1,2,3"
     * @return LinkedHashSet<Long> 用户 ID 集合，保持顺序且不重复
     */
    @Override
    public LinkedHashSet<Long> calculateUsers(String param) {
        // StrUtils.splitToLong(param, StrPool.COMMA) 将字符串按逗号分割成 List<Long>
        // 然后用这个 List 创建一个新的 LinkedHashSet，确保结果有序且不重复
        return new LinkedHashSet<>(StrUtils.splitToLong(param, StrPool.COMMA));
    }

}