package cn.iocoder.yudao.module.hr.service;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.SortablePageParam;
import cn.iocoder.yudao.module.hr.controller.definition.vo.ProfileSaveReqVO;

import java.util.List;

/**
 * 人员信息Service接口
 */
public interface ProfileService {
    /**
     * 创建人员信息
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createProfile(ProfileSaveReqVO createReqVO);

    /**
     * 更新人员信息
     *
     * @param updateReqVO 更新信息
     * @return 编号
     */
    Long updateProfile(ProfileSaveReqVO updateReqVO);

    /**
     * 批量软删除
     *
     * @param idList
     * @return 编号
     */
    List<Long> softDeleteByIds(List<Long>  idList);

    /**
     *  分页检索
     *
     * @param pageParam 带排序条件的分页参数
     *                    
     * @return 分页后的人员数据
     */
    PageResult<ProfileSaveReqVO> findManyWithPagination(SortablePageParam pageParam);
}
