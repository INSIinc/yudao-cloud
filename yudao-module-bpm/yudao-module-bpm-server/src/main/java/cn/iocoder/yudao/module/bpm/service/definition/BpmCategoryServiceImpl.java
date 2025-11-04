package cn.iocoder.yudao.module.bpm.service.definition;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.category.BpmCategoryPageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.category.BpmCategorySaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmCategoryDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.category.BpmCategoryMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;

/**
 * BPM 流程分类 Service 实现类
 *
 * 业务说明：
 * 该类负责处理工作流（BPM）分类的所有业务逻辑，包括：
 * 1. 分类的增删改查操作
 * 2. 分类数据的校验（唯一性、存在性等）
 * 3. 分类排序的批量更新
 * 4. 防止删除被使用的分类
 *
 * @author 芋道源码
 */
@Service  // 标记为 Spring 的服务层组件，由 Spring 容器管理
@Validated  // 启用方法参数校验功能
public class BpmCategoryServiceImpl implements BpmCategoryService {

    /**
     * 分类数据访问对象
     * 用于操作数据库中的 bpm_category 表
     */
    @Resource
    private BpmCategoryMapper bpmCategoryMapper;

    /**
     * 流程模型服务
     * 用于检查分类是否被流程模型使用
     */
    @Resource
    private BpmModelService modelService;

    /**
     * 创建流程分类
     *
     * 业务流程：
     * 1. 校验分类名称是否重复（保证名称唯一性）
     * 2. 校验分类编码是否重复（保证编码唯一性）
     * 3. 将请求对象转换为数据库实体对象
     * 4. 插入数据库
     * 5. 返回新创建的分类 ID
     *
     * @param createReqVO 创建请求参数，包含分类名称、编码、状态等信息
     * @return 新创建的分类 ID
     */
    @Override
    public Long createCategory(BpmCategorySaveReqVO createReqVO) {
        // 校验唯一性：确保分类名称和编码在系统中不重复
        validateCategoryNameUnique(createReqVO);
        validateCategoryCodeUnique(createReqVO);

        // 将前端传来的 VO（视图对象）转换为 DO（数据库实体对象）
        BpmCategoryDO category = BeanUtils.toBean(createReqVO, BpmCategoryDO.class);

        // 插入数据库，MyBatis-Plus 会自动填充 ID 到 category 对象中
        bpmCategoryMapper.insert(category);

        // 返回新创建的分类 ID
        return category.getId();
    }

    /**
     * 更新流程分类
     *
     * 业务流程：
     * 1. 校验分类是否存在（防止更新不存在的数据）
     * 2. 校验分类名称唯一性（排除自身）
     * 3. 校验分类编码唯一性（排除自身）
     * 4. 将请求对象转换为数据库实体对象
     * 5. 更新数据库
     *
     * @param updateReqVO 更新请求参数，包含分类 ID 及要更新的字段
     */
    @Override
    public void updateCategory(BpmCategorySaveReqVO updateReqVO) {
        // 校验要更新的分类是否存在
        validateCategoryExists(updateReqVO.getId());

        // 校验唯一性：名称和编码不能与其他分类重复（但可以与自己相同）
        validateCategoryNameUnique(updateReqVO);
        validateCategoryCodeUnique(updateReqVO);

        // 将前端传来的 VO 转换为 DO
        BpmCategoryDO updateObj = BeanUtils.toBean(updateReqVO, BpmCategoryDO.class);

        // 根据 ID 更新数据库记录
        bpmCategoryMapper.updateById(updateObj);
    }

    /**
     * 校验分类名称的唯一性
     *
     * 校验逻辑：
     * 1. 根据名称查询数据库，看是否已存在同名分类
     * 2. 如果不存在，校验通过
     * 3. 如果存在，判断是否是当前分类自己（通过 ID 比较）
     * 4. 如果是其他分类，抛出异常提示名称重复
     *
     * @param updateReqVO 保存请求参数（创建或更新）
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 如果名称重复
     */
    private void validateCategoryNameUnique(BpmCategorySaveReqVO updateReqVO) {
        // 根据名称查询是否已存在同名分类
        BpmCategoryDO category = bpmCategoryMapper.selectByName(updateReqVO.getName());

        // 如果查询结果为空，说明名称可用
        // 或者查询到的是当前分类自己（ID 相同），也允许使用
        if (category == null
                || ObjUtil.equal(category.getId(), updateReqVO.getId())) {
            return;
        }

        // 名称重复，抛出业务异常
        throw exception(CATEGORY_NAME_DUPLICATE, updateReqVO.getName());
    }

    /**
     * 校验分类编码的唯一性
     *
     * 校验逻辑：
     * 1. 根据编码查询数据库，看是否已存在同编码分类
     * 2. 如果不存在，校验通过
     * 3. 如果存在，判断是否是当前分类自己（通过 ID 比较）
     * 4. 如果是其他分类，抛出异常提示编码重复
     *
     * @param updateReqVO 保存请求参数（创建或更新）
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 如果编码重复
     */
    private void validateCategoryCodeUnique(BpmCategorySaveReqVO updateReqVO) {
        // 根据编码查询是否已存在同编码分类
        BpmCategoryDO category = bpmCategoryMapper.selectByCode(updateReqVO.getCode());

        // 如果查询结果为空，说明编码可用
        // 或者查询到的是当前分类自己（ID 相同），也允许使用
        if (category == null
                || ObjUtil.equal(category.getId(), updateReqVO.getId())) {
            return;
        }

        // 编码重复，抛出业务异常
        throw exception(CATEGORY_CODE_DUPLICATE, updateReqVO.getCode());
    }

    /**
     * 删除流程分类
     *
     * 业务流程：
     * 1. 校验分类是否存在
     * 2. 校验分类是否被流程模型使用（防止删除正在使用的分类）
     * 3. 如果未被使用，执行删除操作
     *
     * 为什么要检查是否被使用？
     * 如果删除了正在使用的分类，会导致流程模型失去分类关联，造成数据不一致
     *
     * @param id 要删除的分类 ID
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 如果分类不存在或正在被使用
     */
    @Override
    public void deleteCategory(Long id) {
        // 校验分类是否存在，如果不存在会抛出异常
        BpmCategoryDO category = validateCategoryExists(id);

        // 查询该分类下有多少个流程模型在使用
        Long count = modelService.getModelCountByCategory(category.getCode());
        if (count > 0) {
            // 如果有流程模型在使用，不允许删除，抛出异常
            throw exception(CATEGORY_DELETE_FAIL_MODEL_USED, category.getName());
        }

        // 校验通过，执行删除操作
        bpmCategoryMapper.deleteById(id);
    }

    /**
     * 校验分类是否存在
     *
     * 这是一个通用的校验方法，在更新、删除等操作前调用
     *
     * @param id 分类 ID
     * @return 存在则返回分类对象
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 如果分类不存在
     */
    private BpmCategoryDO validateCategoryExists(Long id) {
        // 根据 ID 查询分类
        BpmCategoryDO category = bpmCategoryMapper.selectById(id);

        // 如果查询结果为空，说明分类不存在
        if (category == null) {
            throw exception(CATEGORY_NOT_EXISTS);
        }

        // 返回查询到的分类对象
        return category;
    }

    /**
     * 根据 ID 获取单个分类
     *
     * 这是一个简单的查询方法，不做任何校验
     *
     * @param id 分类 ID
     * @return 分类对象，如果不存在则返回 null
     */
    @Override
    public BpmCategoryDO getCategory(Long id) {
        return bpmCategoryMapper.selectById(id);
    }

    /**
     * 分页查询流程分类列表
     *
     * 支持的查询条件（在 pageReqVO 中）：
     * - 分类名称（模糊查询）
     * - 分类编码（模糊查询）
     * - 状态（精确查询）
     * - 创建时间范围
     *
     * @param pageReqVO 分页查询参数，包含页码、每页大小、查询条件等
     * @return 分页结果，包含总数和当前页的数据列表
     */
    @Override
    public PageResult<BpmCategoryDO> getCategoryPage(BpmCategoryPageReqVO pageReqVO) {
        return bpmCategoryMapper.selectPage(pageReqVO);
    }

    /**
     * 根据编码集合批量查询分类
     *
     * 使用场景：
     * 当需要根据多个分类编码查询对应的分类信息时使用
     * 例如：流程模型列表中需要显示分类名称，可以批量查询避免 N+1 问题
     *
     * @param codes 分类编码集合
     * @return 分类列表，如果 codes 为空则返回空列表
     */
    @Override
    public List<BpmCategoryDO> getCategoryListByCode(Collection<String> codes) {
        // 如果传入的编码集合为空，直接返回空列表，避免无效的数据库查询
        if (CollUtil.isEmpty(codes)) {
            return Collections.emptyList();
        }

        // 根据编码集合查询分类列表（使用 IN 查询）
        return bpmCategoryMapper.selectListByCode(codes);
    }

    /**
     * 根据状态查询分类列表
     *
     * 使用场景：
     * 查询所有启用或停用的分类
     * 例如：前端下拉框只显示启用状态的分类供用户选择
     *
     * @param status 分类状态（0=停用，1=启用）
     * @return 指定状态的分类列表
     */
    @Override
    public List<BpmCategoryDO> getCategoryListByStatus(Integer status) {
        return bpmCategoryMapper.selectListByStatus(status);
    }

    /**
     * 批量更新分类排序
     *
     * 业务场景：
     * 前端拖拽调整分类顺序后，需要批量更新排序值
     *
     * 实现原理：
     * 1. 传入的 ids 列表顺序就是期望的排序顺序
     * 2. 将列表中的位置索引作为排序值（第一个元素 sort=0，第二个 sort=1...）
     * 3. 批量更新到数据库
     *
     * 事务说明：
     * 使用 @Transactional 保证所有更新要么全部成功，要么全部回滚
     *
     * @param ids 分类 ID 列表，列表顺序即为排序顺序
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 如果存在不存在的分类 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)  // 开启事务，任何异常都回滚
    public void updateCategorySortBatch(List<Long> ids) {
        // 先校验所有分类是否都存在
        List<BpmCategoryDO> categories = bpmCategoryMapper.selectByIds(ids);
        if (categories.size() != ids.size()) {
            // 如果查询到的数量与传入的 ID 数量不一致，说明有不存在的 ID
            throw exception(CATEGORY_NOT_EXISTS);
        }

        // 使用 Java Stream 批量构建更新对象
        // IntStream.range(0, ids.size()) 生成索引序列：0, 1, 2, 3...
        // 索引即为新的排序值
        List<BpmCategoryDO> updateList = IntStream.range(0, ids.size())
                .mapToObj(index -> new BpmCategoryDO().setId(ids.get(index)).setSort(index))
                .collect(Collectors.toList());

        // 批量更新排序值到数据库
        bpmCategoryMapper.updateBatch(updateList);
    }

}