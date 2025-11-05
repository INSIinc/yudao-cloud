package com.fhs.trans.service;

import com.fhs.core.trans.vo.VO;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动翻译接口：只有实现了这个接口的类，才能被系统自动“翻译”（比如将用户ID自动转成用户名）。
 *
 * 为什么要把这个接口复制到 yudao-common 包下？
 * 因为它原本属于 easy-trans-service 模块，而 yudao 的 API 模块（如 yudao-module-system-api）无法直接依赖 service 模块。
 * 为了在实体类（VO）中方便地使用这个接口，就把它移到了公共基础模块（common）里，让所有模块都能用。
 *
 * @author jackwang
 * @since 2020-05-19 10:26:15
 */
public interface AutoTransable<V extends VO> {

    /**
     * 【已废弃】根据 ID 列表查询对应的翻译数据（VO 对象列表）。
     *
     * 这个方法已经不推荐使用了，请改用下面的 selectByIds 方法。
     * 提供默认实现（返回空列表），子类可以根据需要重写。
     *
     * @param ids 要查询的 ID 列表（可以是 Long、String 等类型）
     * @return 对应的翻译数据列表（例如：[用户VO1, 用户VO2, ...]）
     */
    @Deprecated
    default List<V> findByIds(List<? extends Object> ids) {
        // 默认返回空列表，避免子类必须实现
        return new ArrayList<>();
    }

    /**
     * 根据 ID 列表查询对应的翻译数据（VO 对象列表）。
     *
     * 这是推荐使用的方法。默认调用已废弃的 findByIds，但子类可以单独重写它。
     * 实际项目中，通常会由具体的服务类（如 UserService）实现这个方法，
     * 从数据库批量查询数据并返回。
     *
     * @param ids 要查询的 ID 列表（如 [1, 2, 3]）
     * @return 对应的 VO 数据列表
     */
    default List<V> selectByIds(List<? extends Object> ids) {
        // 默认委托给旧方法（为了兼容），但建议子类直接重写此方法
        return this.findByIds(ids);
    }

    /**
     * 查询数据库中所有的翻译数据。
     *
     * 用于某些场景需要“全量缓存”或“下拉选项”时（比如所有部门、所有字典项）。
     * 默认返回空列表，子类可按需实现。
     *
     * @return 所有数据的 VO 列表
     */
    default List<V> select() {
        return new ArrayList<>();
    }

    /**
     * 根据单个 ID 查询对应的翻译数据（VO 对象）。
     *
     * 这是核心方法，必须由实现类提供具体逻辑（不能用默认实现）。
     * 系统在自动翻译时（比如把 user_id=1001 转成 username="张三"），
     * 会调用此方法获取单条数据。
     *
     * @param primaryValue 主键值（如 1001）
     * @return 对应的 VO 对象（如 UserVO）
     */
    V selectById(Object primaryValue);

}