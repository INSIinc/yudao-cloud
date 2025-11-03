package cn.iocoder.yudao.module.bpm.dal.mysql.category;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.category.BpmCategoryPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmCategoryDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * BPM 流程分类 Mapper 接口
 * <p>
 * 该接口继承自 {@link BaseMapperX}，提供对 {@link BpmCategoryDO} 实体的基本 CRUD 操作，
 * 并扩展了针对流程分类业务场景的查询方法。
 * </p>
 *
 * @author 芋道源码
 */
@Mapper
public interface BpmCategoryMapper extends BaseMapperX<BpmCategoryDO> {

    /**
     * 根据分页查询条件，获取流程分类分页列表
     * <p>
     * 支持按名称、编码、状态、创建时间范围进行条件过滤，并按排序字段升序排列。
     * </p>
     *
     * @param reqVO 分页查询参数对象，包含以下可选条件：
     *              <ul>
     *                  <li>{@code name}：流程分类名称（模糊匹配）</li>
     *                  <li>{@code code}：流程分类编码（模糊匹配）</li>
     *                  <li>{@code status}：状态（精确匹配）</li>
     *                  <li>{@code createTime}：创建时间范围（起止时间）</li>
     *              </ul>
     * @return 符合条件的流程分类分页结果
     */
    default PageResult<BpmCategoryDO> selectPage(BpmCategoryPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BpmCategoryDO>()
                .likeIfPresent(BpmCategoryDO::getName, reqVO.getName())        // 名称模糊匹配（如果存在）
                .likeIfPresent(BpmCategoryDO::getCode, reqVO.getCode())        // 编码模糊匹配（如果存在）
                .eqIfPresent(BpmCategoryDO::getStatus, reqVO.getStatus())      // 状态精确匹配（如果存在）
                .betweenIfPresent(BpmCategoryDO::getCreateTime, reqVO.getCreateTime()) // 创建时间范围（如果存在）
                .orderByAsc(BpmCategoryDO::getSort));                          // 按 sort 字段升序排序
    }

    /**
     * 根据流程分类名称查询唯一的流程分类记录
     *
     * @param name 流程分类名称（唯一性约束）
     * @return 对应的流程分类 DO 对象，若不存在则返回 null
     */
    default BpmCategoryDO selectByName(String name) {
        return selectOne(BpmCategoryDO::getName, name);
    }

    /**
     * 根据流程分类编码查询唯一的流程分类记录
     *
     * @param code 流程分类编码（唯一性约束）
     * @return 对应的流程分类 DO 对象，若不存在则返回 null
     */
    default BpmCategoryDO selectByCode(String code) {
        return selectOne(BpmCategoryDO::getCode, code);
    }

    /**
     * 根据多个流程分类编码批量查询对应的流程分类记录
     *
     * @param codes 流程分类编码集合（不允许为 null，但可为空集合）
     * @return 匹配的流程分类 DO 对象列表（无匹配时返回空列表）
     */
    default List<BpmCategoryDO> selectListByCode(Collection<String> codes) {
        return selectList(BpmCategoryDO::getCode, codes);
    }

    /**
     * 根据状态查询所有处于该状态的流程分类列表
     *
     * @param status 流程分类状态（例如：启用/禁用）
     * @return 所有状态匹配的流程分类 DO 对象列表
     */
    default List<BpmCategoryDO> selectListByStatus(Integer status) {
        return selectList(BpmCategoryDO::getStatus, status);
    }

}