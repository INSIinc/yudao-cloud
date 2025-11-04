package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.expression.BpmProcessExpressionPageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.expression.BpmProcessExpressionSaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessExpressionDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmProcessExpressionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_EXPRESSION_NOT_EXISTS;

/**
 * BPM 流程表达式 Service 实现类
 *
 * 功能说明：
 * 这个类负责处理BPM（业务流程管理）中流程表达式的业务逻辑
 * 流程表达式用于在工作流中动态判断条件，例如：审批人判断、流程走向判断等
 *
 * 主要功能包括：
 * 1. 创建流程表达式
 * 2. 更新流程表达式
 * 3. 删除流程表达式
 * 4. 查询单个流程表达式
 * 5. 分页查询流程表达式列表
 *
 * @author 芋道源码
 */
@Service // 标记为Spring的服务层组件，Spring会自动扫描并管理这个Bean
@Validated // 启用方法参数校验，确保传入的参数符合规范
public class BpmProcessExpressionServiceImpl implements BpmProcessExpressionService {

    /**
     * 流程表达式的数据访问对象（Mapper）
     *
     * @Resource 注解：Spring的依赖注入注解，会自动注入BpmProcessExpressionMapper的实例
     * Mapper负责与数据库交互，执行增删改查操作
     */
    @Resource
    private BpmProcessExpressionMapper processExpressionMapper;

    /**
     * 创建流程表达式
     *
     * 业务流程：
     * 1. 接收前端传来的表达式数据（VO对象）
     * 2. 将VO对象转换为数据库实体对象（DO对象）
     * 3. 调用Mapper将数据插入数据库
     * 4. 返回新创建的表达式ID
     *
     * @param createReqVO 创建请求的视图对象，包含表达式的名称、表达式内容等信息
     * @return 返回新创建的流程表达式的ID（主键）
     */
    @Override
    public Long createProcessExpression(BpmProcessExpressionSaveReqVO createReqVO) {
        // 步骤1：将前端传来的VO对象转换为数据库实体DO对象
        // BeanUtils.toBean 是工具类方法，用于对象属性拷贝，避免手动set每个属性
        BpmProcessExpressionDO processExpression = BeanUtils.toBean(createReqVO, BpmProcessExpressionDO.class);

        // 步骤2：调用Mapper的insert方法，将数据插入数据库
        // 插入成功后，主键ID会自动回填到processExpression对象中
        processExpressionMapper.insert(processExpression);

        // 步骤3：返回自动生成的主键ID
        return processExpression.getId();
    }

    /**
     * 更新流程表达式
     *
     * 业务流程：
     * 1. 先校验要更新的表达式是否存在，如果不存在则抛出异常
     * 2. 将VO对象转换为DO对象
     * 3. 调用Mapper执行更新操作
     *
     * @param updateReqVO 更新请求的视图对象，包含要更新的ID和新的表达式信息
     */
    @Override
    public void updateProcessExpression(BpmProcessExpressionSaveReqVO updateReqVO) {
        // 步骤1：校验表达式是否存在
        // 如果数据库中不存在该ID的记录，会抛出业务异常，阻止后续操作
        validateProcessExpressionExists(updateReqVO.getId());

        // 步骤2：将VO对象转换为DO对象
        BpmProcessExpressionDO updateObj = BeanUtils.toBean(updateReqVO, BpmProcessExpressionDO.class);

        // 步骤3：根据ID更新数据库中的记录
        // updateById方法会根据DO对象的ID字段，更新对应的数据库记录
        processExpressionMapper.updateById(updateObj);
    }

    /**
     * 删除流程表达式
     *
     * 业务流程：
     * 1. 先校验要删除的表达式是否存在
     * 2. 如果存在，执行删除操作
     *
     * @param id 要删除的流程表达式ID
     */
    @Override
    public void deleteProcessExpression(Long id) {
        // 步骤1：校验表达式是否存在
        // 防止删除不存在的数据，确保操作的合法性
        validateProcessExpressionExists(id);

        // 步骤2：根据ID删除数据库记录
        processExpressionMapper.deleteById(id);
    }

    /**
     * 校验流程表达式是否存在（私有方法，内部使用）
     *
     * 这是一个通用的校验方法，被创建、更新、删除等方法调用
     *
     * 工作原理：
     * 1. 根据ID从数据库查询记录
     * 2. 如果查询结果为null，说明记录不存在
     * 3. 抛出业务异常，异常信息为"流程表达式不存在"
     *
     * @param id 要校验的流程表达式ID
     * @throws 如果表达式不存在，抛出业务异常
     */
    private void validateProcessExpressionExists(Long id) {
        // 从数据库查询指定ID的记录
        if (processExpressionMapper.selectById(id) == null) {
            // 如果查询结果为null，使用异常工具类抛出业务异常
            // PROCESS_EXPRESSION_NOT_EXISTS 是预定义的错误码常量
            throw exception(PROCESS_EXPRESSION_NOT_EXISTS);
        }
    }

    /**
     * 查询单个流程表达式
     *
     * 根据ID获取流程表达式的详细信息
     *
     * @param id 流程表达式ID
     * @return 返回流程表达式DO对象，如果不存在返回null
     */
    @Override
    public BpmProcessExpressionDO getProcessExpression(Long id) {
        // 直接调用Mapper的selectById方法，根据主键ID查询
        return processExpressionMapper.selectById(id);
    }

    /**
     * 分页查询流程表达式列表
     *
     * 功能说明：
     * 支持按条件查询流程表达式列表，并进行分页显示
     * 常用于后台管理页面的列表展示
     *
     * @param pageReqVO 分页查询请求对象，包含：
     *                  - 分页参数：页码、每页数量
     *                  - 查询条件：表达式名称、状态等过滤条件
     * @return 返回分页结果对象，包含：
     *         - 符合条件的记录列表
     *         - 总记录数
     *         - 当前页码等分页信息
     */
    @Override
    public PageResult<BpmProcessExpressionDO> getProcessExpressionPage(BpmProcessExpressionPageReqVO pageReqVO) {
        // 调用Mapper的selectPage方法执行分页查询
        // Mapper会根据pageReqVO中的条件构建SQL查询语句
        return processExpressionMapper.selectPage(pageReqVO);
    }

}