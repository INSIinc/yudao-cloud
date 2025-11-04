package cn.iocoder.yudao.module.bpm.api.task;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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