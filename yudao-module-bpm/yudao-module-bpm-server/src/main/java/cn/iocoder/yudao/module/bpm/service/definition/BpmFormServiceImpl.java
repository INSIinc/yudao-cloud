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
 * @author 风里雾里
 */
@Service
@Validated // 启用参数校验（配合 JSR-303 注解）
public class BpmFormServiceImpl implements BpmFormService {

    /**
     * 注入动态表单数据访问层（Mapper），用于数据库操作。
     */
    @Resource
    private BpmFormMapper formMapper;

    /**
     * 创建一个新的动态表单。
     *
     * @param createReqVO 创建表单的请求参数（包含表单名称、状态、字段等信息）
     * @return 新创建表单的数据库主键 ID
     */
    @Override
    public Long createForm(BpmFormSaveReqVO createReqVO) {
        // 校验表单字段（目前暂未启用校验逻辑，见 validateFields 方法）
        this.validateFields(createReqVO.getFields());

        // 将请求 VO 转换为 DO（数据对象），用于持久化
        BpmFormDO form = BeanUtils.toBean(createReqVO, BpmFormDO.class);

        // 执行数据库插入操作
        formMapper.insert(form);

        // 返回新插入记录的主键 ID
        return form.getId();
    }

    /**
     * 更新已存在的动态表单。
     *
     * @param updateReqVO 更新表单的请求参数（必须包含 ID）
     */
    @Override
    public void updateForm(BpmFormSaveReqVO updateReqVO) {
        // 校验表单字段（同创建逻辑）
        validateFields(updateReqVO.getFields());

        // 校验该 ID 对应的表单是否存在，防止更新不存在的数据
        validateFormExists(updateReqVO.getId());

        // 转换请求参数为 DO
        BpmFormDO updateObj = BeanUtils.toBean(updateReqVO, BpmFormDO.class);

        // 执行数据库更新操作（根据 ID 更新）
        formMapper.updateById(updateObj);
    }

    /**
     * 删除指定 ID 的动态表单。
     *
     * @param id 要删除的表单 ID
     */
    @Override
    public void deleteForm(Long id) {
        // 校验表单是否存在
        this.validateFormExists(id);

        // 执行物理删除（根据主键）
        formMapper.deleteById(id);
    }

    /**
     * 校验指定 ID 的表单是否存在。
     * <p>
     * 若不存在，则抛出 {@link ErrorCodeConstants#FORM_NOT_EXISTS} 异常。
     * </p>
     *
     * @param id 表单 ID
     */
    private void validateFormExists(Long id) {
        if (formMapper.selectById(id) == null) {
            throw exception(ErrorCodeConstants.FORM_NOT_EXISTS);
        }
    }

    /**
     * 根据 ID 获取单个动态表单详情。
     *
     * @param id 表单 ID
     * @return 表单数据对象（{@link BpmFormDO}），若不存在则返回 null
     */
    @Override
    public BpmFormDO getForm(Long id) {
        return formMapper.selectById(id);
    }

    /**
     * 获取所有动态表单列表（不推荐在数据量大时使用）。
     *
     * @return 所有表单的列表
     */
    @Override
    public List<BpmFormDO> getFormList() {
        return formMapper.selectList();
    }

    /**
     * 根据多个 ID 批量查询动态表单。
     *
     * @param ids 表单 ID 集合
     * @return 对应 ID 的表单列表；若 ids 为空或 null，则返回空列表
     */
    @Override
    public List<BpmFormDO> getFormList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return formMapper.selectByIds(ids);
    }

    /**
     * 分页查询动态表单列表。
     *
     * @param pageReqVO 分页查询条件（包含页码、每页大小、查询条件如名称等）
     * @return 分页结果，包含数据列表和总数
     */
    @Override
    public PageResult<BpmFormDO> getFormPage(BpmFormPageReqVO pageReqVO) {
        return formMapper.selectPage(pageReqVO);
    }

    /**
     * 校验表单字段列表，确保字段的 vModel（字段标识）不重复。
     * <p>
     * 当前实现中，由于系统升级至 Vue3 新表单设计器，该校验逻辑被临时禁用（通过 if (true) return）。
     * </p>
     * <p>
     * 原始逻辑说明（供参考）：
     * 每个字段以 JSON 字符串形式存储在 List<String> 中。
     * 需解析每个字段，检查其 {@link BpmFormFieldRespDTO#getVModel()} 是否唯一。
     * 若发现重复，则抛出 {@link ErrorCodeConstants#FORM_FIELD_REPEAT} 异常。
     * </p>
     *
     * @param fields 表单字段的 JSON 字符串列表
     */
    private void validateFields(List<String> fields) {
        // TODO 芋艿：兼容 Vue3 工作流：因为采用了新的表单设计器，所以暂时不校验
        if (true) {
            return;
        }

        // 用于记录已出现的 vModel（字段名）及其对应的 label（用于错误提示）
        Map<String, String> fieldMap = new HashMap<>();

        for (String field : fields) {
            // 将 JSON 字符串反序列化为字段 DTO
            BpmFormFieldRespDTO fieldDTO = JsonUtils.parseObject(field, BpmFormFieldRespDTO.class);
            Assert.notNull(fieldDTO, "字段 JSON 解析失败");

            // 尝试将当前字段的 vModel 存入 map，若返回非 null，说明已存在
            String oldLabel = fieldMap.put(fieldDTO.getVModel(), fieldDTO.getLabel());

            if (oldLabel == null) {
                // 未重复，继续下一个
                continue;
            }

            // 发现重复字段名（vModel），抛出异常
            throw exception(ErrorCodeConstants.FORM_FIELD_REPEAT,
                    oldLabel,               // 已存在的字段中文名
                    fieldDTO.getLabel(),    // 当前字段中文名
                    fieldDTO.getVModel()    // 重复的字段标识
            );
        }
    }

}