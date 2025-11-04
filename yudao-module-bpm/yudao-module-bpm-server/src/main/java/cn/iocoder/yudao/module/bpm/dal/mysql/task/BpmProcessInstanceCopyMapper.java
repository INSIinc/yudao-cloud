package cn.iocoder.yudao.module.bpm.dal.mysql.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCopyPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceCopyDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * BPM 流程实例抄送记录 Mapper 接口
 *
 * <p>用于操作数据库表 `bpm_process_instance_copy`，该表用于记录流程实例的抄送信息，
 * 即某个流程实例被抄送给哪些用户（或角色等）。</p>
 *
 * <p>继承自 {@link BaseMapperX}，提供了基础的 CRUD 能力。</p>
 */
@Mapper
public interface BpmProcessInstanceCopyMapper extends BaseMapperX<BpmProcessInstanceCopyDO> {

    /**
     * 分页查询当前登录用户的流程实例抄送记录列表
     *
     * <p>该方法用于在“我的抄送”页面中，根据用户输入的查询条件（如流程名称、创建时间范围等），
     * 查询该用户收到的抄送记录，并按 ID 降序分页返回。</p>
     *
     * @param loginUserId 当前登录用户的 ID，用于限定只查询该用户的抄送记录
     * @param reqVO 分页查询请求参数，包含流程实例名称、创建时间范围等可选条件
     * @return 分页结果，包含符合条件的抄送记录列表和总数量
     *
     * <p>查询条件说明：
     * <ul>
     *   <li>userId 必须等于 loginUserId（即只查当前用户的抄送）</li>
     *   <li>流程实例名称支持模糊匹配（如果 reqVO 中提供了）</li>
     *   <li>创建时间支持范围查询（如果 reqVO 中提供了开始和结束时间）</li>
     *   <li>结果按主键 ID 降序排列（即最新抄送在前）</li>
     * </ul>
     * </p>
     */
    default PageResult<BpmProcessInstanceCopyDO> selectPage(Long loginUserId, BpmProcessInstanceCopyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BpmProcessInstanceCopyDO>()
                // 仅查询当前登录用户的抄送记录
                .eqIfPresent(BpmProcessInstanceCopyDO::getUserId, loginUserId)
                // 如果提供了流程实例名称，则进行模糊匹配
                .likeIfPresent(BpmProcessInstanceCopyDO::getProcessInstanceName, reqVO.getProcessInstanceName())
                // 如果提供了创建时间范围，则按该范围过滤
                .betweenIfPresent(BpmProcessInstanceCopyDO::getCreateTime, reqVO.getCreateTime())
                // 按 ID 降序排序，最新抄送靠前
                .orderByDesc(BpmProcessInstanceCopyDO::getId));
    }

    /**
     * 根据流程实例 ID 删除所有相关的抄送记录
     *
     * <p>当某个流程实例被删除或作废时，需要同步清理其对应的抄送记录，避免脏数据。</p>
     *
     * @param processInstanceId 流程实例 ID（字符串类型，通常来自流程引擎如 Flowable/Activiti）
     */
    default void deleteByProcessInstanceId(String processInstanceId) {
        // 调用父类 delete 方法，根据字段 processInstanceId 精确匹配并删除
        delete(BpmProcessInstanceCopyDO::getProcessInstanceId, processInstanceId);
    }

}