package cn.iocoder.yudao.module.bpm.service.definition;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.form.BpmFormPageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.form.BpmFormSaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmFormMapper;
import cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.bpm.service.definition.dto.BpmFormFieldRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 动态表单（BpmForm）的业务逻辑实现类。
 * <p>
 * 负责处理表单的增删改查、字段校验、分页查询等操作。
 * 表单的字段信息以 JSON 字符串形式存储在 {@link BpmFormDO#fields} 字段中。
 * </p>
 *
 * 【核心职责】
 * 1. 管理流程表单的 CRUD 操作
 * 2. 校验表单字段的合法性（避免字段重复）
 * 3. 为流程模型提供表单配置支持
 *
 * 【表单类型】
 * - 普通表单（NORMAL）：使用系统的动态表单设计器
 *   - 表单数据存储在 bpm_form 表
 *   - 字段配置以 JSON 数组形式存储
 *   - 支持可视化表单设计
 *
 * 【数据存储】
 * - 表单基本信息存储在 bpm_form 表
 * - 字段配置存储在 fields 字段（JSON 数组格式）
 * - 每个字段包含：vModel（字段标识）、label（字段名称）、type（字段类型）等
 *
 * 【使用场景】
 * - 流程发起时的表单配置
 * - 审批任务的表单展示
 * - 流程实例的表单数据查看
 *
 * @author 风里雾里
 */
@Service
@Validated // 启用参数校验（配合 JSR-303 注解）
public class BpmFormServiceImpl implements BpmFormService {

    /**
     * 注入动态表单数据访问层（Mapper），用于数据库操作。
     */
    @Resource
    private BpmFormMapper formMapper; // 表单数据访问层

    /**
     * 创建流程表单
     *
     * 【执行流程】
     * 1. 校验表单字段的合法性（避免 vModel 重复）
     * 2. 将请求对象转换为 DO 对象
     * 3. 插入数据库
     *
     * 【字段说明】
     * - name：表单名称
     * - conf：表单配置（整体配置 JSON）
     * - fields：字段列表（JSON 数组，每个元素是一个字段配置）
     * - status：表单状态（0=禁用，1=启用）
     * - remark：备注
     *
     * @param createReqVO 创建请求参数
     * @return 表单ID
     */
    @Override
    public Long createForm(BpmFormSaveReqVO createReqVO) {
        // 1. 校验表单字段（避免字段标识重复）
        this.validateFields(createReqVO.getFields());

        // 2. 转换为 DO 对象并插入数据库
        BpmFormDO form = BeanUtils.toBean(createReqVO, BpmFormDO.class);

        // 执行数据库插入操作
        formMapper.insert(form);

        // 3. 返回表单ID
        return form.getId();
    }

    /**
     * 更新流程表单
     *
     * 【执行流程】
     * 1. 校验表单字段的合法性
     * 2. 校验表单是否存在
     * 3. 更新数据库
     *
     * 【注意事项】
     * - 已被流程模型使用的表单仍可更新
     * - 表单更新后，需要重新部署流程模型才能生效
     *
     * @param updateReqVO 更新请求参数
     */
    @Override
    public void updateForm(BpmFormSaveReqVO updateReqVO) {
        // 1. 校验表单字段（避免字段标识重复）
        validateFields(updateReqVO.getFields());

        // 2. 校验表单是否存在
        validateFormExists(updateReqVO.getId());

        // 3. 转换为 DO 对象并更新数据库
        BpmFormDO updateObj = BeanUtils.toBean(updateReqVO, BpmFormDO.class);

        // 执行数据库更新操作（根据 ID 更新）
        formMapper.updateById(updateObj);
    }

    /**
     * 删除流程表单
     *
     * 【注意事项】
     * - 删除前会校验表单是否存在
     * - 如果表单已被流程模型使用，删除后流程模型将无法正常使用
     * - 建议删除前先检查表单的使用情况
     *
     * @param id 表单ID
     */
    @Override
    public void deleteForm(Long id) {
        // 1. 校验表单是否存在
        this.validateFormExists(id);

        // 2. 执行删除操作（物理删除）
        formMapper.deleteById(id);
    }

    /**
     * 校验表单是否存在
     *
     * @param id 表单ID
     * @throws 业务异常 如果表单不存在
     */
    private void validateFormExists(Long id) {
        if (formMapper.selectById(id) == null) {
            throw exception(ErrorCodeConstants.FORM_NOT_EXISTS);
        }
    }

    /**
     * 根据ID查询表单
     *
     * @param id 表单ID
     * @return 表单对象；null（如果不存在）
     */
    @Override
    public BpmFormDO getForm(Long id) {
        return formMapper.selectById(id);
    }

    /**
     * 查询所有表单列表
     *
     * 【使用场景】
     * - 表单选择器（下拉框）
     * - 表单管理列表
     *
     * @return 表单列表
     */
    @Override
    public List<BpmFormDO> getFormList() {
        return formMapper.selectList();
    }

    /**
     * 批量查询表单列表
     *
     * 【使用场景】
     * - 批量获取流程模型关联的表单信息
     * - 表单数据的批量查询
     *
     * @param ids 表单ID集合
     * @return 表单列表
     */
    @Override
    public List<BpmFormDO> getFormList(Collection<Long> ids) {
        // 如果ID集合为空，直接返回空列表
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return formMapper.selectByIds(ids);
    }

    /**
     * 分页查询表单列表
     *
     * 【查询条件】
     * - name：表单名称（模糊查询）
     * - status：表单状态
     * - createTime：创建时间范围
     *
     * @param pageReqVO 分页查询参数
     * @return 分页结果
     */
    @Override
    public PageResult<BpmFormDO> getFormPage(BpmFormPageReqVO pageReqVO) {
        return formMapper.selectPage(pageReqVO);
    }

    /**
     * 校验表单字段配置的合法性
     *
     * 【校验规则】
     * - 检查字段的 vModel（字段标识）是否重复
     * - vModel 是字段的唯一标识，用于表单数据绑定
     *
     * 【字段结构示例】
     * ```json
     * {
     *   "vModel": "userName",        // 字段标识（必须唯一）
     *   "label": "用户名",            // 字段名称
     *   "type": "input",             // 字段类型（input、select、date等）
     *   "required": true,            // 是否必填
     *   "placeholder": "请输入用户名" // 提示文字
     * }
     * ```
     *
     * 【兼容性说明】
     * - 当前版本采用了新的表单设计器（Vue3）
     * - 暂时禁用了字段重复校验（TODO 待后续调整）
     *
     * @param fields 表单字段配置列表（JSON 字符串数组）
     * @throws 业务异常 如果存在重复的 vModel
     */
    private void validateFields(List<String> fields) {
        // TODO 芋艿：兼容 Vue3 工作流 - 因为采用了新的表单设计器，所以暂时不校验
        if (true) {
            return;
        }

        // 使用 Map 检测 vModel 重复
        // key: vModel（字段标识）, value: label（字段名称）
        Map<String, String> fieldMap = new HashMap<>();

        for (String field : fields) {
            // 解析字段 JSON 配置
            BpmFormFieldRespDTO fieldDTO = JsonUtils.parseObject(field, BpmFormFieldRespDTO.class);
            Assert.notNull(fieldDTO);

            // 尝试将字段加入 Map，如果返回旧值说明存在重复
            String oldLabel = fieldMap.put(fieldDTO.getVModel(), fieldDTO.getLabel());

            // 如果是第一次出现该 vModel，继续下一个
            if (oldLabel == null) {
                // 未重复，继续下一个
                continue;
            }

            // 如果已存在相同的 vModel，抛出异常
            throw exception(ErrorCodeConstants.FORM_FIELD_REPEAT,
                    oldLabel, fieldDTO.getLabel(), fieldDTO.getVModel());
        }
    }

}