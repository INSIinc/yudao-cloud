package cn.iocoder.yudao.module.bpm.service.definition;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.listener.BpmProcessListenerPageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.listener.BpmProcessListenerSaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessListenerDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmProcessListenerMapper;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmProcessListenerTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmProcessListenerValueTypeEnum;
import jakarta.annotation.Resource;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.delegate.TaskListener;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;

/**
 * BPM 流程监听器 Service 实现类
 *
 * 功能说明：
 * 这个类负责管理工作流（BPM）中的流程监听器
 * 监听器可以在流程执行的特定时刻自动触发某些操作
 * 例如：任务创建时发送通知、流程结束时更新数据等
 *
 * @author 芋道源码
 */
@Service  // 标记为Spring服务层组件，Spring会自动管理这个类的实例
@Validated  // 启用方法参数校验功能
public class BpmProcessListenerServiceImpl implements BpmProcessListenerService {

    // 注入流程监听器的数据访问对象，用于操作数据库
    @Resource
    private BpmProcessListenerMapper processListenerMapper;

    /**
     * 创建流程监听器
     *
     * 业务流程：
     * 1. 先校验监听器的配置是否正确（类是否存在、表达式格式是否正确等）
     * 2. 将前端传来的数据转换为数据库对象
     * 3. 插入到数据库中
     * 4. 返回新创建的监听器ID
     *
     * @param createReqVO 创建请求对象，包含监听器的所有配置信息
     * @return 新创建的监听器ID
     */
    @Override
    public Long createProcessListener(BpmProcessListenerSaveReqVO createReqVO) {
        // 校验：检查监听器的配置值是否合法
        validateCreateProcessListenerValue(createReqVO);
        // 插入：将请求对象转换为数据库实体对象
        BpmProcessListenerDO processListener = BeanUtils.toBean(createReqVO, BpmProcessListenerDO.class);
        // 插入到数据库
        processListenerMapper.insert(processListener);
        // 返回数据库自动生成的ID
        return processListener.getId();
    }

    /**
     * 更新流程监听器
     *
     * 业务流程：
     * 1. 先检查要更新的监听器是否存在
     * 2. 校验新的配置值是否正确
     * 3. 更新数据库中的记录
     *
     * @param updateReqVO 更新请求对象，包含要更新的ID和新的配置信息
     */
    @Override
    public void updateProcessListener(BpmProcessListenerSaveReqVO updateReqVO) {
        // 校验存在：检查数据库中是否有这条记录
        validateProcessListenerExists(updateReqVO.getId());
        // 校验值：检查新的配置是否合法
        validateCreateProcessListenerValue(updateReqVO);
        // 更新：将请求对象转换为数据库实体对象
        BpmProcessListenerDO updateObj = BeanUtils.toBean(updateReqVO, BpmProcessListenerDO.class);
        // 根据ID更新数据库记录
        processListenerMapper.updateById(updateObj);
    }

    /**
     * 校验流程监听器的配置值是否正确
     *
     * 监听器有两种配置方式：
     * 1. Class类型：直接指定一个Java类，系统会调用这个类的方法
     * 2. 表达式类型：使用Spring EL表达式，格式必须是 ${...}
     *
     * 校验规则：
     * - 如果是Class类型：
     *   a) 检查类是否存在
     *   b) 检查类是否实现了正确的接口（执行监听器要实现JavaDelegate，任务监听器要实现TaskListener）
     * - 如果是表达式类型：
     *   a) 检查表达式格式是否正确（必须以 ${ 开头，以 } 结尾）
     *
     * @param createReqVO 包含监听器配置的请求对象
     */
    private void validateCreateProcessListenerValue(BpmProcessListenerSaveReqVO createReqVO) {
        // 情况1：class 类型的监听器
        if (createReqVO.getValueType().equals(BpmProcessListenerValueTypeEnum.CLASS.getType())) {
            try {
                // 尝试加载指定的类
                Class<?> clazz = Class.forName(createReqVO.getValue());

                // 如果是执行监听器（EXECUTION类型），必须实现JavaDelegate接口
                if (createReqVO.getType().equals(BpmProcessListenerTypeEnum.EXECUTION.getType())
                        && !JavaDelegate.class.isAssignableFrom(clazz)) {
                    // 抛出异常：类没有实现正确的接口
                    throw exception(PROCESS_LISTENER_CLASS_IMPLEMENTS_ERROR, createReqVO.getValue(),
                            JavaDelegate.class.getName());
                }
                // 如果是任务监听器（TASK类型），必须实现TaskListener接口
                else if (createReqVO.getType().equals(BpmProcessListenerTypeEnum.TASK.getType())
                        && !TaskListener.class.isAssignableFrom(clazz)) {
                    // 抛出异常：类没有实现正确的接口
                    throw exception(PROCESS_LISTENER_CLASS_IMPLEMENTS_ERROR, createReqVO.getValue(),
                            TaskListener.class.getName());
                }
            } catch (ClassNotFoundException e) {
                // 如果类不存在，抛出异常
                throw exception(PROCESS_LISTENER_CLASS_NOT_FOUND, createReqVO.getValue());
            }
            // 校验通过，直接返回
            return;
        }

        // 情况2：表达式类型的监听器
        // 表达式必须以 ${ 开头，并以 } 结尾，这是Spring EL表达式的标准格式
        if (!StrUtil.startWith(createReqVO.getValue(), "${") || !StrUtil.endWith(createReqVO.getValue(), "}")) {
            // 格式不正确，抛出异常
            throw exception(PROCESS_LISTENER_EXPRESSION_INVALID, createReqVO.getValue());
        }
    }

    /**
     * 删除流程监听器
     *
     * 业务流程：
     * 1. 先检查监听器是否存在
     * 2. 从数据库中删除
     *
     * @param id 要删除的监听器ID
     */
    @Override
    public void deleteProcessListener(Long id) {
        // 校验存在：如果不存在会抛出异常
        validateProcessListenerExists(id);
        // 删除：根据ID从数据库中删除记录
        processListenerMapper.deleteById(id);
    }

    /**
     * 校验流程监听器是否存在
     *
     * 这是一个私有方法，被其他方法调用来检查记录是否存在
     * 如果不存在，会抛出业务异常
     *
     * @param id 监听器ID
     */
    private void validateProcessListenerExists(Long id) {
        // 根据ID查询数据库，如果返回null说明记录不存在
        if (processListenerMapper.selectById(id) == null) {
            // 抛出异常：监听器不存在
            throw exception(PROCESS_LISTENER_NOT_EXISTS);
        }
    }

    /**
     * 查询单个流程监听器
     *
     * 根据ID从数据库中查询监听器的详细信息
     *
     * @param id 监听器ID
     * @return 监听器对象，如果不存在返回null
     */
    @Override
    public BpmProcessListenerDO getProcessListener(Long id) {
        return processListenerMapper.selectById(id);
    }

    /**
     * 分页查询流程监听器列表
     *
     * 用于前端展示监听器列表，支持分页和条件查询
     *
     * @param pageReqVO 分页查询请求对象，包含页码、每页条数、查询条件等
     * @return 分页结果，包含监听器列表和总记录数
     */
    @Override
    public PageResult<BpmProcessListenerDO> getProcessListenerPage(BpmProcessListenerPageReqVO pageReqVO) {
        // 调用Mapper执行分页查询
        return processListenerMapper.selectPage(pageReqVO);
    }

}

