package cn.iocoder.yudao.framework.websocket.core.session;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.websocket.core.util.WebSocketFrameworkUtils;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 默认的 {@link WebSocketSessionManager} 实现类
 * <p>
 * 功能说明：管理所有的 WebSocket 连接会话
 * 主要职责：
 * 1. 添加和移除会话
 * 2. 根据 ID 或用户信息查询会话
 * 3. 支持多租户场景
 *
 * @author 芋道源码
 */
public class WebSocketSessionManagerImpl implements WebSocketSessionManager {

    /**
     * id 与 WebSocketSession 映射
     * <p>
     * 用途：通过会话 ID 快速查找对应的 WebSocket 连接
     * key：Session 编号（每个 WebSocket 连接的唯一标识）
     * value：WebSocketSession 对象（代表一个 WebSocket 连接）
     * <p>
     * 为什么使用 ConcurrentHashMap？
     * - 因为 WebSocket 连接的建立和断开可能在不同线程中发生，需要线程安全的集合
     */
    private final ConcurrentMap<String, WebSocketSession> idSessions = new ConcurrentHashMap<>();

    /**
     * user 与 WebSocketSession 映射（多层嵌套的 Map 结构）
     * <p>
     * 用途：通过用户信息查找该用户的所有 WebSocket 连接
     * <p>
     * 数据结构说明：
     * 第一层 Map：
     * - key1：用户类型（Integer，如：1=管理员，2=普通用户等）
     * - value1：第二层 Map
     * <p>
     * 第二层 Map：
     * - key2：用户编号（Long，用户的唯一 ID）
     * - value2：该用户的所有 WebSocket 连接列表
     * <p>
     * 为什么一个用户可以有多个连接？
     * - 因为用户可能在多个设备上登录（如：手机、电脑、平板）
     * - 或者在同一设备上打开多个浏览器标签页
     * <p>
     * 为什么使用 CopyOnWriteArrayList？
     * - 读多写少的场景，遍历连接时不需要加锁
     * - 添加/删除连接时会复制整个列表，保证线程安全
     */
    private final ConcurrentMap<Integer, ConcurrentMap<Long, CopyOnWriteArrayList<WebSocketSession>>> userSessions
            = new ConcurrentHashMap<>();

    /**
     * 添加会话到管理器中
     * <p>
     * 执行流程：
     * 1. 将会话添加到 idSessions（按 ID 索引）
     * 2. 将会话添加到 userSessions（按用户信息索引）
     *
     * @param session WebSocket 会话对象
     */
    @Override
    public void addSession(WebSocketSession session) {
        // 步骤1：添加到 idSessions 中，建立 sessionId -> session 的映射
        // 目的：可以通过会话 ID 快速找到对应的连接
        idSessions.put(session.getId(), session);

        // 步骤2：添加到 userSessions 中，建立 user -> sessions 的映射
        // 从会话中获取登录用户信息（包含用户类型、用户ID、租户ID等）
        LoginUser user = WebSocketFrameworkUtils.getLoginUser(session);
        if (user == null) {
            // 如果获取不到用户信息，说明是匿名连接，不需要建立用户维度的索引
            return;
        }

        // 步骤2.1：获取该用户类型对应的 Map（第二层 Map）
        // 例如：用户类型=1（管理员），则获取所有管理员用户的会话映射
        ConcurrentMap<Long, CopyOnWriteArrayList<WebSocketSession>> userSessionsMap = userSessions.get(user.getUserType());
        if (userSessionsMap == null) {
            // 如果该用户类型还没有任何会话，创建一个新的 Map
            userSessionsMap = new ConcurrentHashMap<>();

            // 使用 putIfAbsent 防止并发情况下重复创建
            // 如果其他线程已经创建了，就使用其他线程创建的那个
            if (userSessions.putIfAbsent(user.getUserType(), userSessionsMap) != null) {
                userSessionsMap = userSessions.get(user.getUserType());
            }
        }

        // 步骤2.2：获取该用户的会话列表（一个用户可能有多个连接）
        CopyOnWriteArrayList<WebSocketSession> sessions = userSessionsMap.get(user.getId());
        if (sessions == null) {
            // 如果该用户还没有任何会话，创建一个新的列表
            sessions = new CopyOnWriteArrayList<>();

            // 同样使用 putIfAbsent 防止并发问题
            if (userSessionsMap.putIfAbsent(user.getId(), sessions) != null) {
                sessions = userSessionsMap.get(user.getId());
            }
        }

        // 步骤2.3：将当前会话添加到该用户的会话列表中
        sessions.add(session);
    }

    /**
     * 从管理器中移除会话
     * <p>
     * 应用场景：
     * - WebSocket 连接断开时调用
     * - 用户主动登出时调用
     * <p>
     * 执行流程：
     * 1. 从 idSessions 中移除
     * 2. 从 userSessions 中移除
     * 3. 如果用户的会话列表为空，则清理整个映射关系
     *
     * @param session 要移除的 WebSocket 会话对象
     */
    @Override
    public void removeSession(WebSocketSession session) {
        // 步骤1：从 idSessions 中移除该会话
        // 移除 sessionId -> session 的映射关系
        idSessions.remove(session.getId());

        // 步骤2：从 userSessions 中移除该会话
        // 获取会话关联的用户信息
        LoginUser user = WebSocketFrameworkUtils.getLoginUser(session);
        if (user == null) {
            // 如果获取不到用户信息，说明只在 idSessions 中，已经移除完成
            return;
        }

        // 步骤2.1：获取该用户类型对应的所有用户会话映射
        ConcurrentMap<Long, CopyOnWriteArrayList<WebSocketSession>> userSessionsMap = userSessions.get(user.getUserType());
        if (userSessionsMap == null) {
            // 如果映射不存在，说明数据已经被清理，直接返回
            return;
        }

        // 步骤2.2：获取该用户的所有会话列表
        CopyOnWriteArrayList<WebSocketSession> sessions = userSessionsMap.get(user.getId());
        if (sessions == null) {
            // 如果列表不存在，直接返回
            return;
        }

        // 步骤2.3：从列表中移除当前会话
        // 使用 removeIf 方法遍历列表，找到 ID 匹配的会话并移除
        sessions.removeIf(session0 -> session0.getId().equals(session.getId()));

        // 步骤2.4：清理空的映射关系，避免内存泄漏
        // 如果该用户已经没有任何活跃的会话了，就把整个映射关系删除
        if (CollUtil.isEmpty(sessions)) {
            userSessionsMap.remove(user.getId(), sessions);
        }
    }

    /**
     * 根据会话 ID 获取 WebSocket 会话对象
     * <p>
     * 应用场景：
     * - 当需要向特定的 WebSocket 连接发送消息时
     * - 当需要检查某个连接是否还存活时
     *
     * @param id 会话 ID（WebSocket 连接的唯一标识）
     * @return WebSocketSession 对象，如果不存在则返回 null
     */
    @Override
    public WebSocketSession getSession(String id) {
        // 直接从 idSessions 这个 Map 中查找并返回
        // 时间复杂度：O(1)
        return idSessions.get(id);
    }

    /**
     * 根据用户类型获取所有 WebSocket 会话列表
     * <p>
     * 应用场景：
     * - 向某一类用户群体广播消息（如：给所有管理员发送通知）
     * - 统计某类用户的在线情况
     * <p>
     * 特殊处理：
     * - 支持多租户隔离：如果当前有租户上下文，只返回该租户下的会话
     *
     * @param userType 用户类型（如：1=管理员，2=普通用户）
     * @return 该类型用户的所有 WebSocket 会话列表
     */
    @Override
    public Collection<WebSocketSession> getSessionList(Integer userType) {
        // 步骤1：获取该用户类型对应的所有用户会话映射
        ConcurrentMap<Long, CopyOnWriteArrayList<WebSocketSession>> userSessionsMap = userSessions.get(userType);
        if (CollUtil.isEmpty(userSessionsMap)) {
            // 如果该用户类型没有任何会话，返回空列表
            return new ArrayList<>();
        }

        // 步骤2：收集所有会话到结果列表中
        LinkedList<WebSocketSession> result = new LinkedList<>(); // 使用 LinkedList 避免扩容

        // 步骤3：获取当前线程的租户 ID（用于多租户隔离）
        Long contextTenantId = TenantContextHolder.getTenantId();

        // 步骤4：遍历该用户类型下所有用户的会话
        for (List<WebSocketSession> sessions : userSessionsMap.values()) {
            if (CollUtil.isEmpty(sessions)) {
                // 如果该用户没有会话，跳过
                continue;
            }

            // 步骤5：租户隔离检查（多租户场景的关键逻辑）
            if (contextTenantId != null) {
                // 如果当前有租户上下文（说明是在租户环境下调用）
                // 获取该用户会话所属的租户 ID
                Long userTenantId = WebSocketFrameworkUtils.getTenantId(sessions.get(0));
                if (!contextTenantId.equals(userTenantId)) {
                    // 如果租户 ID 不匹配，说明不是同一个租户的用户，直接跳过
                    // 这样就实现了租户隔离：租户 A 看不到租户 B 的用户会话
                    continue;
                }
            }

            // 步骤6：将该用户的所有会话添加到结果列表中
            result.addAll(sessions);
        }

        return result;
    }

    /**
     * 根据用户类型和用户 ID 获取指定用户的所有 WebSocket 会话列表
     * <p>
     * 应用场景：
     * - 向特定用户发送消息（如：给用户 ID=123 的用户发送私信）
     * - 查询某个用户在哪些设备上在线
     * - 强制踢出某个用户的所有连接
     * <p>
     * 为什么返回的是列表而不是单个会话？
     * - 因为一个用户可能同时在多个设备或浏览器标签页上登录
     * - 例如：用户同时在手机、电脑、iPad 上登录，会有 3 个会话
     *
     * @param userType 用户类型（如：1=管理员，2=普通用户）
     * @param userId   用户 ID（用户的唯一标识）
     * @return 该用户的所有 WebSocket 会话列表（可能包含多个会话）
     */
    @Override
    public Collection<WebSocketSession> getSessionList(Integer userType, Long userId) {
        // 步骤1：获取该用户类型对应的所有用户会话映射
        ConcurrentMap<Long, CopyOnWriteArrayList<WebSocketSession>> userSessionsMap = userSessions.get(userType);
        if (CollUtil.isEmpty(userSessionsMap)) {
            // 如果该用户类型没有任何会话，返回空列表
            return new ArrayList<>();
        }

        // 步骤2：获取指定用户 ID 的所有会话
        CopyOnWriteArrayList<WebSocketSession> sessions = userSessionsMap.get(userId);

        // 步骤3：返回结果
        // 如果会话列表不为空，创建一个新的 ArrayList 返回（避免外部修改原始数据）
        // 如果会话列表为空或不存在，返回空 ArrayList
        return CollUtil.isNotEmpty(sessions) ? new ArrayList<>(sessions) : new ArrayList<>();
    }

}
