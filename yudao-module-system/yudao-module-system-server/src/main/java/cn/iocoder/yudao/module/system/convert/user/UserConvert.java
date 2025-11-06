package cn.iocoder.yudao.module.system.convert.user;

import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.collection.MapUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSimpleRespVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.post.PostSimpleRespVO;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.role.RoleSimpleRespVO;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.profile.UserProfileRespVO;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.user.UserRespVO;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.user.UserSimpleRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.PostDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;

/**
 * 用户转换器接口
 *
 * 作用：将数据库实体对象（DO）转换为前端响应对象（VO）
 *
 * 为什么需要转换器？
 * 1. 数据库对象（DO）包含所有字段，有些敏感信息（如密码）不应该返回给前端
 * 2. 前端需要的数据格式可能与数据库不同，需要进行数据组装和格式化
 * 3. 可能需要关联查询其他表的数据（如部门名称），一起返回给前端
 *
 * MapStruct 介绍：
 * - @Mapper 注解表示这是一个 MapStruct 映射器
 * - MapStruct 是一个代码生成器，会在编译时自动生成转换代码，性能高效
 * - 通过 Mappers.getMapper() 获取转换器实例
 */
@Mapper
public interface UserConvert {

    /**
     * 转换器实例（单例模式）
     * 通过 MapStruct 的 Mappers.getMapper() 方法获取转换器的实现类实例
     * 在代码中可以直接使用 UserConvert.INSTANCE 来调用转换方法
     */
    UserConvert INSTANCE = Mappers.getMapper(UserConvert.class);

    /**
     * 批量转换用户列表（带部门信息）
     *
     * 使用场景：用户列表查询接口
     *
     * @param list 用户数据库对象列表（从数据库查询出来的用户数据）
     * @param deptMap 部门信息映射表（key: 部门ID, value: 部门对象）
     *                使用 Map 的原因：避免在循环中重复查询数据库，提高性能
     * @return 用户响应对象列表（返回给前端的数据）
     */
    default List<UserRespVO> convertList(List<AdminUserDO> list, Map<Long, DeptDO> deptMap) {
        // CollectionUtils.convertList 是一个工具方法，用于批量转换集合
        // 对 list 中的每个 user 对象，调用 convert 方法进行转换
        // 同时从 deptMap 中根据用户的部门ID获取对应的部门信息
        return CollectionUtils.convertList(list, user -> convert(user, deptMap.get(user.getDeptId())));
    }

    /**
     * 转换单个用户对象（带部门信息）
     *
     * 转换逻辑：
     * 1. 将用户数据库对象（AdminUserDO）的基本属性复制到响应对象（UserRespVO）
     * 2. 如果部门信息存在，设置部门名称到响应对象中
     *
     * @param user 用户数据库对象
     * @param dept 部门数据库对象（可能为 null，因为用户可能没有分配部门）
     * @return 用户响应对象
     */
    default UserRespVO convert(AdminUserDO user, DeptDO dept) {
        // BeanUtils.toBean 是对象属性复制工具
        // 将 user 对象的属性（如 id、username、nickname 等）复制到 UserRespVO 对象中
        UserRespVO userVO = BeanUtils.toBean(user, UserRespVO.class);

        // 如果部门对象不为空，设置部门名称
        // 这样前端就可以直接显示部门名称，而不需要再次查询部门信息
        if (dept != null) {
            userVO.setDeptName(dept.getName());
        }
        return userVO;
    }

    /**
     * 批量转换用户简单列表（带部门名称）
     *
     * 使用场景：下拉框、选择器等只需要用户基本信息的场景
     * 与 convertList 的区别：返回的字段更少，只包含最基本的用户信息
     *
     * @param list 用户数据库对象列表
     * @param deptMap 部门信息映射表（key: 部门ID, value: 部门对象）
     * @return 用户简单响应对象列表
     */
    default List<UserSimpleRespVO> convertSimpleList(List<AdminUserDO> list, Map<Long, DeptDO> deptMap) {
        // 遍历用户列表，对每个用户进行转换
        return CollectionUtils.convertList(list, user -> {
            // 将用户对象转换为简单响应对象
            UserSimpleRespVO userVO = BeanUtils.toBean(user, UserSimpleRespVO.class);

            // MapUtils.findAndThen 是一个工具方法
            // 作用：从 deptMap 中查找对应的部门，如果找到则执行后面的 lambda 表达式
            // 这样可以避免手动判断 null，代码更简洁
            MapUtils.findAndThen(deptMap, user.getDeptId(), dept -> userVO.setDeptName(dept.getName()));
            return userVO;
        });
    }

    /**
     * 转换用户详情对象（包含角色、部门、岗位信息）
     *
     * 使用场景：用户个人信息页面、用户详情查询接口
     * 特点：返回的信息最全面，包含用户的关联数据
     *
     * @param user 用户数据库对象
     * @param userRoles 用户拥有的角色列表（一个用户可以有多个角色）
     * @param dept 用户所属部门
     * @param posts 用户所属岗位列表（一个用户可以有多个岗位，如：经理、项目负责人）
     * @return 用户详情响应对象
     */
    default UserProfileRespVO convert(AdminUserDO user, List<RoleDO> userRoles,
                                      DeptDO dept, List<PostDO> posts) {
        // 1. 转换用户基本信息
        UserProfileRespVO userVO = BeanUtils.toBean(user, UserProfileRespVO.class);

        // 2. 转换角色列表（将 RoleDO 列表转换为 RoleSimpleRespVO 列表）
        // 角色信息用于权限控制，前端根据角色显示不同的菜单和功能
        userVO.setRoles(BeanUtils.toBean(userRoles, RoleSimpleRespVO.class));

        // 3. 转换部门信息（将 DeptDO 转换为 DeptSimpleRespVO）
        // 部门信息用于组织架构显示
        userVO.setDept(BeanUtils.toBean(dept, DeptSimpleRespVO.class));

        // 4. 转换岗位列表（将 PostDO 列表转换为 PostSimpleRespVO 列表）
        // 岗位信息用于显示用户在组织中的职位
        userVO.setPosts(BeanUtils.toBean(posts, PostSimpleRespVO.class));

        return userVO;
    }

}
