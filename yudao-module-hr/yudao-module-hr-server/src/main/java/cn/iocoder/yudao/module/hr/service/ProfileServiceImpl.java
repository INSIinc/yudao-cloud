package cn.iocoder.yudao.module.hr.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.SortablePageParam;
import cn.iocoder.yudao.module.hr.controller.definition.vo.ProfileSaveReqVO;

import java.util.List;

public class ProfileServiceImpl implements ProfileService{
    @Override
    public Long createProfile(ProfileSaveReqVO createReqVO){
        return null;
    }

    @Override
    public Long updateProfile(ProfileSaveReqVO updateReqVO) {
        return null;
    }

    @Override
    public List<Long> softDeleteByIds(List<Long> ids) {
        return null;
    }

    @Override
    public PageResult<ProfileSaveReqVO> findManyWithPagination(SortablePageParam pageParam){
        return null;
    }
}
