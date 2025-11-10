package cn.iocoder.yudao.module.hr.dal.mapper;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.hr.dal.dataobject.ProfileDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * HR 档案 Mapper
 *
 * @author yudao
 */
@Mapper
public interface ProfileMapper extends BaseMapperX<ProfileDO> {

    /**
     * 根据身份证号查询档案（身份证号唯一）
     *
     * @param idNumber 身份证号
     * @return 档案信息
     */
    default ProfileDO selectByIdNumber(String idNumber) {
        return selectOne(ProfileDO::getIdNumber, idNumber);
    }

    /**
     * 根据证件号查询档案
     *
     * @param certificateNumber 证件号
     * @return 档案信息
     */
    default ProfileDO selectByCertificateNumber(String certificateNumber) {
        return selectOne(ProfileDO::getCertificateNumber, certificateNumber);
    }

    /**
     * 根据姓名模糊查询档案列表
     *
     * @param name 姓名
     * @return 档案列表
     */
    default List<ProfileDO> selectListByName(String name) {
        return selectList(new LambdaQueryWrapperX<ProfileDO>()
                .likeIfPresent(ProfileDO::getName, name));
    }

    /**
     * 根据离职状态查询档案列表
     *
     * @param isLeaved 是否离职
     * @return 档案列表
     */
    default List<ProfileDO> selectListByLeaved(Boolean isLeaved) {
        return selectList(ProfileDO::getIsLeaved, isLeaved);
    }

    /**
     * 查询在职人员列表（isLeaved = false）
     *
     * @return 在职人员列表
     */
    default List<ProfileDO> selectOnJobList() {
        return selectList(new LambdaQueryWrapperX<ProfileDO>()
                .eq(ProfileDO::getIsLeaved, false)
                .orderByAsc(ProfileDO::getOrder));
    }

}
