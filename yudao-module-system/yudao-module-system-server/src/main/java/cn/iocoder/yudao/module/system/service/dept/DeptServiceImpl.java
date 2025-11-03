package cn.iocoder.yudao.module.system.service.dept;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

/**
 * 部门 Service 实现类
 * <p>
 * 负责处理部门相关的业务逻辑，包括增删改查、数据校验、缓存管理、树形结构遍历等。
 * 本类通过 Spring Cache 对“子部门 ID 列表”进行缓存，提升权限判断等高频操作的性能。
 *
 * @author 芋道源码
 */
@Service
@Validated
@Slf4j
public class DeptServiceImpl implements DeptService {

    @Resource
    private DeptMapper deptMapper;

    /**
     * 创建部门
     * <p>
     * 1. 若未指定父部门 ID，自动设为根部门（-1）。
     * 2. 验证父部门是否存在且合法（防止自引用、环路）。
     * 3. 验证同级部门名称唯一性。
     * 4. 插入数据库。
     * 5. 清空所有部门子节点缓存（因为新增节点会影响多个上级部门的子树缓存）。
     *
     * @param createReqVO 创建请求参数
     * @return 新创建部门的 ID
     */
    @Override
    @CacheEvict(cacheNames = RedisKeyConstants.DEPT_CHILDREN_ID_LIST, allEntries = true)
    public Long createDept(DeptSaveReqVO createReqVO) {
        // 默认父部门为根节点
        if (createReqVO.getParentId() == null) {
            createReqVO.setParentId(DeptDO.PARENT_ID_ROOT);
        }
        // 校验父部门（新增时 id 为 null）
        validateParentDept(null, createReqVO.getParentId());
        // 校验部门名称在同级下是否唯一
        validateDeptNameUnique(null, createReqVO.getParentId(), createReqVO.getName());

        // 转换并插入
        DeptDO dept = BeanUtils.toBean(createReqVO, DeptDO.class);
        deptMapper.insert(dept);
        return dept.getId();
    }

    /**
     * 更新部门信息
     * <p>
     * 1. 若未指定父部门，设为根部门。
     * 2. 验证当前部门是否存在。
     * 3. 验证新父部门是否合法（防止循环引用等）。
     * 4. 验证新部门名在新父部门下是否唯一。
     * 5. 更新数据库。
     * 6. 清空所有子部门缓存（因结构可能变化）。
     *
     * @param updateReqVO 更新请求参数
     */
    @Override
    @CacheEvict(cacheNames = RedisKeyConstants.DEPT_CHILDREN_ID_LIST, allEntries = true)
    public void updateDept(DeptSaveReqVO updateReqVO) {
        if (updateReqVO.getParentId() == null) {
            updateReqVO.setParentId(DeptDO.PARENT_ID_ROOT);
        }
        validateDeptExists(updateReqVO.getId()); // 部门必须存在
        validateParentDept(updateReqVO.getId(), updateReqVO.getParentId());
        validateDeptNameUnique(updateReqVO.getId(), updateReqVO.getParentId(), updateReqVO.getName());

        DeptDO updateObj = BeanUtils.toBean(updateReqVO, DeptDO.class);
        deptMapper.updateById(updateObj);
    }

    /**
     * 删除单个部门
     * <p>
     * 1. 校验部门存在。
     * 2. 校验该部门下无子部门（不允许删除非叶子节点）。
     * 3. 执行物理删除。
     * 4. 清空缓存。
     *
     * @param id 部门 ID
     */
    @Override
    @CacheEvict(cacheNames = RedisKeyConstants.DEPT_CHILDREN_ID_LIST, allEntries = true)
    public void deleteDept(Long id) {
        validateDeptExists(id);
        if (deptMapper.selectCountByParentId(id) > 0) {
            throw exception(DEPT_EXITS_CHILDREN); // 存在子部门，禁止删除
        }
        deptMapper.deleteById(id);
    }

    /**
     * 批量删除部门
     * <p>
     * 对每个部门执行“是否有子部门”的校验，有任一存在子部门则整体失败。
     * 全部通过后执行批量删除。
     *
     * @param ids 部门 ID 列表
     */
    @Override
    @CacheEvict(cacheNames = RedisKeyConstants.DEPT_CHILDREN_ID_LIST, allEntries = true)
    public void deleteDeptList(List<Long> ids) {
        for (Long id : ids) {
            if (deptMapper.selectCountByParentId(id) > 0) {
                throw exception(DEPT_EXITS_CHILDREN);
            }
        }
        deptMapper.deleteByIds(ids);
    }

    /**
     * 校验部门是否存在（用于内部验证）
     *
     * @param id 部门 ID（可为 null，此时直接返回）
     */
    @VisibleForTesting
    void validateDeptExists(Long id) {
        if (id == null) return;
        DeptDO dept = deptMapper.selectById(id);
        if (dept == null) {
            throw exception(DEPT_NOT_FOUND);
        }
    }

    /**
     * 校验父部门的合法性
     * <p>
     * 包括以下校验：
     * 1. 不能将自己设为父部门（自引用）。
     * 2. 父部门必须存在。
     * 3. 防止形成环路：从父部门向上递归，检查是否最终回到当前部门（即父部门是自己的子节点）。
     *
     * @param id       当前部门 ID（新增时为 null）
     * @param parentId 拟设置的父部门 ID
     */
    @VisibleForTesting
    void validateParentDept(Long id, Long parentId) {
        // 根部门无需校验
        if (parentId == null || DeptDO.PARENT_ID_ROOT.equals(parentId)) {
            return;
        }
        // 1. 不能自引用
        if (Objects.equals(id, parentId)) {
            throw exception(DEPT_PARENT_ERROR);
        }
        // 2. 父部门必须存在
        DeptDO parentDept = deptMapper.selectById(parentId);
        if (parentDept == null) {
            throw exception(DEPT_PARENT_NOT_EXITS);
        }
        // 3. 防止环路（仅更新时校验，新增时 id=null 不校验）
        if (id == null) {
            return;
        }
        // 最多向上查 32767 层（防止死循环）
        for (int i = 0; i < Short.MAX_VALUE; i++) {
            parentId = parentDept.getParentId();
            // 若某级父部门等于当前部门 ID，则构成环路
            if (Objects.equals(id, parentId)) {
                throw exception(DEPT_PARENT_IS_CHILD);
            }
            // 到达根节点或父部门为空，退出
            if (parentId == null || DeptDO.PARENT_ID_ROOT.equals(parentId)) {
                break;
            }
            parentDept = deptMapper.selectById(parentId);
            if (parentDept == null) {
                break; // 理论上不会发生，但防御性编程
            }
        }
    }

    /**
     * 校验部门名称在指定父部门下是否唯一
     *
     * @param id       当前部门 ID（更新时传入，新增时为 null）
     * @param parentId 父部门 ID
     * @param name     部门名称
     */
    @VisibleForTesting
    void validateDeptNameUnique(Long id, Long parentId, String name) {
        DeptDO dept = deptMapper.selectByParentIdAndName(parentId, name);
        if (dept == null) {
            return; // 无重复
        }
        // 若是新增（id 为 null），直接报重名
        if (id == null) {
            throw exception(DEPT_NAME_DUPLICATE);
        }
        // 若是更新，但查到的重复部门不是自己，则报重名
        if (ObjectUtil.notEqual(dept.getId(), id)) {
            throw exception(DEPT_NAME_DUPLICATE);
        }
    }

    /**
     * 获取单个部门详情
     */
    @Override
    public DeptDO getDept(Long id) {
        return deptMapper.selectById(id);
    }

    /**
     * 批量获取部门信息（按 ID 列表）
     */
    @Override
    public List<DeptDO> getDeptList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return deptMapper.selectByIds(ids);
    }

    /**
     * 根据查询条件获取部门列表，并按排序字段升序排列
     *
     * @param reqVO 查询条件（如状态、父部门等）
     * @return 排序后的部门列表
     */
    @Override
    public List<DeptDO> getDeptList(DeptListReqVO reqVO) {
        List<DeptDO> list = deptMapper.selectList(reqVO);
        list.sort(Comparator.comparing(DeptDO::getSort));
        return list;
    }

    /**
     * 递归获取指定部门 ID 集合的所有子部门（包括多级子部门）
     * <p>
     * 使用广度优先方式逐层查询，避免深度递归导致栈溢出。
     * 最大循环次数限制为 Short.MAX_VALUE 防止死循环。
     *
     * @param ids 父部门 ID 集合
     * @return 所有子部门（不包含入参中的部门本身）
     */
    @Override
    public List<DeptDO> getChildDeptList(Collection<Long> ids) {
        List<DeptDO> children = new LinkedList<>();
        Collection<Long> parentIds = ids;

        for (int i = 0; i < Short.MAX_VALUE; i++) {
            List<DeptDO> depts = deptMapper.selectListByParentId(parentIds);
            if (CollUtil.isEmpty(depts)) {
                break; // 无更多子部门，结束
            }
            children.addAll(depts);
            // 下一层的父 ID = 当前层所有子部门 ID
            parentIds = convertSet(depts, DeptDO::getId);
        }
        return children;
    }

    /**
     * 根据负责人用户 ID 查询其负责的部门列表
     */
    @Override
    public List<DeptDO> getDeptListByLeaderUserId(Long id) {
        return deptMapper.selectListByLeaderUserId(id);
    }

    /**
     * 从缓存中获取指定部门的所有子部门 ID 集合（含多级）
     * <p>
     * 使用 Spring Cache 缓存结果，key 为部门 ID。
     * 禁用数据权限注解（@DataPermission），避免因用户权限不同导致缓存污染。
     *
     * @param id 部门 ID
     * @return 所有子孙部门 ID 的 Set 集合
     */
    @Override
    @DataPermission(enable = false)
    @Cacheable(cacheNames = RedisKeyConstants.DEPT_CHILDREN_ID_LIST, key = "#id")
    public Set<Long> getChildDeptIdListFromCache(Long id) {
        List<DeptDO> children = getChildDeptList(Collections.singleton(id));
        return convertSet(children, DeptDO::getId);
    }

    /**
     * 校验部门 ID 列表中的每个部门：
     * 1. 是否存在；
     * 2. 是否处于启用状态（status = ENABLE）。
     * <p>
     * 常用于权限校验、数据引用前的合法性检查。
     *
     * @param ids 部门 ID 列表
     */
    @Override
    public void validateDeptList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        Map<Long, DeptDO> deptMap = getDeptMap(ids);
        ids.forEach(id -> {
            DeptDO dept = deptMap.get(id);
            if (dept == null) {
                throw exception(DEPT_NOT_FOUND);
            }
            if (!CommonStatusEnum.ENABLE.getStatus().equals(dept.getStatus())) {
                throw exception(DEPT_NOT_ENABLE, dept.getName());
            }
        });
    }

}