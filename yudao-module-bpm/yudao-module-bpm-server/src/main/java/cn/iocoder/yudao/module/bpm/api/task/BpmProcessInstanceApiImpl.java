// 声明当前类所属的包，用于组织项目结构
package cn.iocoder.yudao.module.bpm.api.task;

// 导入通用响应类，用于封装 API 返回结果（如成功/失败、数据等）
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
// 导入创建流程实例的请求数据传输对象（DTO），用于接收前端传入的参数
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
// 导入流程实例的服务类，包含具体的业务逻辑（如启动流程）
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
// 导入 Spring 的参数校验注解，用于开启方法级别参数校验
import org.springframework.validation.annotation.Validated;
// 导入 Spring MVC 的注解，将该类标记为 REST 风格的控制器（即处理 HTTP 请求）
import org.springframework.web.bind.annotation.RestController;

// 导入资源注入注解（Jakarta 版本），用于注入 Spring 容器中的 Bean
import jakarta.annotation.Resource;
// 导入 Bean 校验注解（Jakarta 版本），用于对传入的 DTO 对象进行合法性校验
import jakarta.validation.Valid;

// 静态导入 CommonResult 的 success 方法，方便直接调用 success(...) 返回成功结果
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * Flowable 流程实例 API 实现类
 * <p>
 * 该类实现了 BpmProcessInstanceApi 接口，提供启动（创建）一个流程实例的 HTTP 接口。
 * 它接收用户 ID 和流程创建请求参数，调用服务层完成流程启动，并返回流程实例 ID。
 * </p>
 *
 * @author 芋道源码
 * @author jason
 */
@RestController             // 表示这是一个 RESTful 控制器，Spring 会自动将其注册为 Bean 并处理 Web 请求
@Validated                 // 启用类级别的参数校验（配合方法上的 @Valid 使用）
public class BpmProcessInstanceApiImpl implements BpmProcessInstanceApi {

    // 使用 @Resource 注解从 Spring 容器中注入 BpmProcessInstanceService 的实现类
    // 这样就可以在本类中调用业务逻辑方法（如创建流程实例）
    @Resource
    private BpmProcessInstanceService processInstanceService;

    /**
     * 创建（启动）一个新的流程实例
     *
     * @param userId  当前操作用户的 ID，用于标识流程由谁发起
     * @param reqDTO  包含创建流程所需信息的数据对象（如流程定义 Key、业务表单数据等）
     *                使用 @Valid 注解表示 Spring 会对该对象的字段进行自动校验（如非空、格式等）
     * @return 返回封装后的通用结果，其中数据部分是新创建的流程实例 ID（String 类型）
     */
    @Override
    public CommonResult<String> createProcessInstance(Long userId, @Valid BpmProcessInstanceCreateReqDTO reqDTO) {
        // 调用服务层方法创建流程实例，该方法会返回流程实例的唯一标识 ID
        String processInstanceId = processInstanceService.createProcessInstance(userId, reqDTO);

        // 使用 CommonResult.success() 封装成功响应，将流程实例 ID 作为返回数据
        return success(processInstanceId);
    }

}