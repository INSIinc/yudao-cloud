package cn.iocoder.yudao.module.system.service.oauth2;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ApproveDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2ApproveMapper;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * OAuth2 授权批准（Approval）服务实现类
 * <p>
 * 该服务用于管理用户对客户端（Client）请求的 OAuth2 Scope 的授权批准记录。
 * 主要功能包括：
 * 1. 预检查（pre-approval）：在用户授权前判断是否可自动通过；
 * 2. 更新用户手动授权后的批准记录；
 * 3. 查询用户当前有效的批准记录。
 * </p>
 *
 * @author 芋道源码
 */
@Service
@Validated
public class OAuth2ApproveServiceImpl implements OAuth2ApproveService {

    /**
     * 用户批准记录的默认过期时间：30 天（单位：秒）
     * 超过该时间后，用户需重新授权。
     */
    private static final Integer TIMEOUT = 30 * 24 * 60 * 60; // 30天，单位：秒

    @Resource
    private OAuth2ClientService oauth2ClientService;

    @Resource
    private OAuth2ApproveMapper oauth2ApproveMapper;

    /**
     * 预检查：判断用户是否已经批准或可自动批准所请求的 scopes。
     * <p>
     * 逻辑步骤：
     * 1. 获取客户端信息，并校验其存在性；
     * 2. 若客户端配置了自动批准（autoApproveScopes），且请求的所有 scopes 均在自动批准范围内，
     *    则自动创建批准记录（approved = true），并返回 true；
     * 3. 否则，查询用户当前有效的批准记录，检查所请求的 scopes 是否全部已被用户手动批准。
     * </p>
     *
     * @param userId           用户ID
     * @param userType         用户类型（如：系统用户、会员等）
     * @param clientId         客户端ID
     * @param requestedScopes  当前授权请求中客户端申请的 scopes 集合
     * @return true 表示无需用户交互即可通过授权，false 表示需要用户确认
     */
    @Override
    @Transactional
    public boolean checkForPreApproval(Long userId, Integer userType, String clientId, Collection<String> requestedScopes) {
        // 第一步：获取客户端信息（从缓存中），并做非空校验
        OAuth2ClientDO clientDO = oauth2ClientService.validOAuthClientFromCache(clientId);
        Assert.notNull(clientDO, "客户端不能为空"); // 防御性编程，确保客户端存在

        // 第二步：检查是否所有请求的 scopes 都在客户端配置的“自动批准”范围内
        if (CollUtil.containsAll(clientDO.getAutoApproveScopes(), requestedScopes)) {
            // 注意：即使自动批准，仍需将批准记录持久化到数据库（参考 Spring Security OAuth2 的行为）
            // 这是为了后续审计、撤销或查询用户授权状态提供依据（见 gh-877 问题修复说明）

            LocalDateTime expireTime = LocalDateTime.now().plusSeconds(TIMEOUT);
            for (String scope : requestedScopes) {
                // 为每个 scope 保存一条已批准（approved = true）的记录
                saveApprove(userId, userType, clientId, scope, true, expireTime);
            }
            return true; // 表示自动通过，无需用户确认
        }

        // 第三步：若无法自动批准，则查询用户历史上对该客户端的有效批准记录
        List<OAuth2ApproveDO> approveDOs = getApproveList(userId, userType, clientId);

        // 从批准记录中提取：未过期 且 已批准（approved = true）的 scopes 集合
        Set<String> scopes = convertSet(approveDOs, OAuth2ApproveDO::getScope,
                OAuth2ApproveDO::getApproved); // 内部会过滤掉未批准或已过期的记录（由 getApproveList 保证未过期）

        // 判断用户是否已手动批准了所有请求的 scopes
        return CollUtil.containsAll(scopes, requestedScopes);
    }

    /**
     * 用户完成授权页面操作后，更新其对 scopes 的批准状态。
     * <p>
     * 此方法会为每个 scope 保存一条批准记录（无论同意或拒绝），并设置相同的过期时间。
     * 只要有一个 scope 被同意（approved = true），就认为本次授权成功。
     * </p>
     *
     * @param userId           用户ID
     * @param userType         用户类型
     * @param clientId         客户端ID
     * @param requestedScopes  scope 与用户选择（true=同意, false=拒绝）的映射
     * @return true 表示至少有一个 scope 被同意，授权流程可继续；false 表示全部拒绝，授权失败
     */
    @Override
    @Transactional
    public boolean updateAfterApproval(Long userId, Integer userType, String clientId, Map<String, Boolean> requestedScopes) {
        // 边界情况：若没有请求任何 scope，视为通过（通常不会发生）
        if (CollUtil.isEmpty(requestedScopes)) {
            return true;
        }

        boolean success = false; // 标记是否有至少一个 scope 被用户同意
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(TIMEOUT); // 所有记录使用统一过期时间

        // 遍历每个 scope 及其用户选择，保存批准记录
        for (Map.Entry<String, Boolean> entry : requestedScopes.entrySet()) {
            String scope = entry.getKey();
            Boolean approved = entry.getValue();

            if (Boolean.TRUE.equals(approved)) {
                success = true; // 记录存在至少一个同意
            }

            // 保存/更新该 scope 的批准状态（无论同意或拒绝）
            saveApprove(userId, userType, clientId, scope, approved, expireTime);
        }

        return success;
    }

    /**
     * 获取用户对指定客户端当前有效的批准记录列表。
     * <p>
     * 该方法从数据库查询后，会过滤掉已过期的记录。
     * </p>
     *
     * @param userId     用户ID
     * @param userType   用户类型
     * @param clientId   客户端ID
     * @return 有效的批准记录列表（已过期的已被移除）
     */
    @Override
    public List<OAuth2ApproveDO> getApproveList(Long userId, Integer userType, String clientId) {
        // 从数据库查询所有相关记录
        List<OAuth2ApproveDO> approveDOs = oauth2ApproveMapper.selectListByUserIdAndUserTypeAndClientId(
                userId, userType, clientId);

        // 移除已过期的记录（expiresTime <= now）
        approveDOs.removeIf(o -> DateUtils.isExpired(o.getExpiresTime()));

        return approveDOs;
    }

    /**
     * 保存或更新一条 OAuth2 批准记录。
     * <p>
     * 采用“先尝试更新，失败则插入”的策略实现 upsert（更新或插入）。
     * 注意：实际更新逻辑依赖于数据库的唯一约束（如：user_id + user_type + client_id + scope 的联合唯一索引）。
     * </p>
     *
     * @param userId      用户ID
     * @param userType    用户类型
     * @param clientId    客户端ID
     * @param scope       scope 名称
     * @param approved    是否批准（true=同意，false=拒绝）
     * @param expireTime  过期时间
     */
    @VisibleForTesting
    void saveApprove(Long userId, Integer userType, String clientId,
                     String scope, Boolean approved, LocalDateTime expireTime) {
        // 构建 DO 对象
        OAuth2ApproveDO approveDO = new OAuth2ApproveDO()
                .setUserId(userId)
                .setUserType(userType)
                .setClientId(clientId)
                .setScope(scope)
                .setApproved(approved)
                .setExpiresTime(expireTime);

        // 先尝试执行更新（假设记录已存在）
        if (oauth2ApproveMapper.update(approveDO) == 1) {
            return; // 更新成功，直接返回
        }

        // 若更新影响行数为 0，说明记录不存在，执行插入
        oauth2ApproveMapper.insert(approveDO);
    }

}