package cn.iocoder.yudao.module.hr.service;

import cn.iocoder.yudao.module.hr.controller.definition.vo.ProfileExportReqVO;
import cn.iocoder.yudao.module.hr.controller.definition.vo.ProfileSaveReqVO;

import java.util.List;

public interface ProfileTransferService {
    /**
     * 批量插入/更新人员 - 导入
     * @param saveReqVOList
     */
    void batchUpsertProfile(List<ProfileSaveReqVO> saveReqVOList);

    /**
     * 批量导出人员信息
     * @param exportReqVO
     */
    void exportProfile(ProfileExportReqVO exportReqVO);
}
