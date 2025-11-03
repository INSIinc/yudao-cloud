package cn.iocoder.yudao.module.system.service.oauth2;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.string.StrUtils;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.client.OAuth2ClientPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.client.OAuth2ClientSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2ClientMapper;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

/**
 * OAuth2.0 客户端（Client）业务逻辑实现类。
 * <p>
 * 提供客户端的增删改查、校验、权限验证等功能，并通过 Spring Cache 集成 Redis 缓存。
 * 为 OAuth2 授权流程中校验客户端合法性提供支撑。
 *
 * @author 芋道源码
 */
@Service
@Validated // 开启参数校验（配合 DTO 中的 JSR-380 注解）
@Slf4j
public class OAuth2ClientServiceImpl implements OAuth2ClientService {

    @Resource
    private OAuth2ClientMapper oauth2ClientMapper;

    /**
     * 创建 OAuth2 客户端。
     *
     * @param createReqVO 创建请求参数
     * @return 新建客户端的 ID
     */
    @Override
    public Long createOAuth2Client(OAuth2ClientSaveReqVO createReqVO) {
        // 校验 clientId 是否已存在（不允许重复）
        validateClientIdExists(null, createReqVO.getClientId());
        // 将 VO 转换为 DO 并插入数据库
        OAuth2ClientDO client = BeanUtils.toBean(createReqVO, OAuth2ClientDO.class);
        oauth2ClientMapper.insert(client);
        return client.getId();
    }

    /**
     * 更新 OAuth2 客户端。
     * <p>
     * 使用 @CacheEvict 清除所有与 OAuth2 客户端相关的缓存（因为 clientId 可能变更，无法精准定位缓存 key）。
     *
     * @param updateReqVO 更新请求参数
     */
    @Override
    @CacheEvict(cacheNames = RedisKeyConstants.OAUTH_CLIENT, allEntries = true)
    public void updateOAuth2Client(OAuth2ClientSaveReqVO updateReqVO) {
        // 校验待更新的客户端是否存在
        validateOAuth2ClientExists(updateReqVO.getId());
        // 校验新的 clientId 是否已被其他客户端占用
        validateClientIdExists(updateReqVO.getId(), updateReqVO.getClientId());

        // 执行更新
        OAuth2ClientDO updateObj = BeanUtils.toBean(updateReqVO, OAuth2ClientDO.class);
        oauth2ClientMapper.updateById(updateObj);
    }

    /**
     * 删除单个 OAuth2 客户端。
     * <p>
     * 同样清除所有缓存，因为删除后无法通过 clientId 或 id 精确清除缓存。
     *
     * @param id 客户端 ID
     */
    @Override
    @CacheEvict(cacheNames = RedisKeyConstants.OAUTH_CLIENT, allEntries = true)
    public void deleteOAuth2Client(Long id) {
        validateOAuth2ClientExists(id); // 先校验存在
        oauth2ClientMapper.deleteById(id);
    }

    /**
     * 批量删除 OAuth2 客户端。
     * <p>
     * 虽然没有逐个校验存在性，但数据库操作本身会忽略不存在的 ID。
     * 依然清空全部缓存，确保一致性。
     *
     * @param ids 客户端 ID 列表
     */
    @Override
    @CacheEvict(cacheNames = RedisKeyConstants.OAUTH_CLIENT, allEntries = true)
    public void deleteOAuth2ClientList(List<Long> ids) {
        oauth2ClientMapper.deleteByIds(ids);
    }

    /**
     * 校验指定 ID 的 OAuth2 客户端是否存在。
     *
     * @param id 客户端 ID
     * @throws ServiceException 如果客户端不存在
     */
    private void validateOAuth2ClientExists(Long id) {
        if (oauth2ClientMapper.selectById(id) == null) {
            throw exception(OAUTH2_CLIENT_NOT_EXISTS);
        }
    }

    /**
     * 校验 clientId 是否已被其他客户端占用。
     * <p>
     * - 若 id 为 null：表示创建操作，只要存在即冲突。
     * - 若 id 非 null：表示更新操作，需排除自身。
     *
     * @param id       当前客户端 ID（可为 null）
     * @param clientId 待校验的 clientId
     * @throws ServiceException 如果 clientId 已被占用
     */
    @VisibleForTesting
    void validateClientIdExists(Long id, String clientId) {
        OAuth2ClientDO client = oauth2ClientMapper.selectByClientId(clientId);
        if (client == null) {
            return; // 不存在则无冲突
        }
        if (id == null) {
            // 创建时发现已存在
            throw exception(OAUTH2_CLIENT_EXISTS);
        }
        if (!client.getId().equals(id)) {
            // 更新时发现被其他客户端占用
            throw exception(OAUTH2_CLIENT_EXISTS);
        }
    }

    /**
     * 根据 ID 获取 OAuth2 客户端详情（无缓存）。
     *
     * @param id 客户端 ID
     * @return 客户端实体，可能为 null
     */
    @Override
    public OAuth2ClientDO getOAuth2Client(Long id) {
        return oauth2ClientMapper.selectById(id);
    }

    /**
     * 根据 clientId 从缓存中获取 OAuth2 客户端。
     * <p>
     * 使用 Spring Cache 机制：
     * - 缓存名：RedisKeyConstants.OAUTH_CLIENT
     * - 缓存 key：clientId
     * - unless：若查询结果为 null，则不缓存（避免缓存空值）
     *
     * @param clientId 客户端标识
     * @return 客户端实体，可能为 null
     */
    @Override
    @Cacheable(cacheNames = RedisKeyConstants.OAUTH_CLIENT, key = "#clientId", unless = "#result == null")
    public OAuth2ClientDO getOAuth2ClientFromCache(String clientId) {
        return oauth2ClientMapper.selectByClientId(clientId);
    }

    /**
     * 分页查询 OAuth2 客户端列表。
     *
     * @param pageReqVO 分页查询参数（含 clientId、status 等条件）
     * @return 分页结果
     */
    @Override
    public PageResult<OAuth2ClientDO> getOAuth2ClientPage(OAuth2ClientPageReqVO pageReqVO) {
        return oauth2ClientMapper.selectPage(pageReqVO);
    }

    /**
     * 校验并获取合法的 OAuth2 客户端（用于授权流程）。
     * <p>
     * 此方法用于 OAuth2 授权服务器在处理授权请求时，对客户端进行全方位合法性校验。
     * 校验项包括：
     * 1. 客户端是否存在且状态为启用；
     * 2. 客户端密钥（client_secret）是否匹配；
     * 3. 请求的授权类型（grant_type）是否被允许；
     * 4. 请求的 scope 是否在客户端授权范围内；
     * 5. 回调地址（redirect_uri）是否匹配客户端预设的地址（支持前缀匹配）。
     *
     * @param clientId             客户端 ID
     * @param clientSecret         客户端密钥（可选，如 client_credentials 模式需要）
     * @param authorizedGrantType  请求的授权类型（如 authorization_code、password 等）
     * @param scopes               请求的权限范围（scopes）
     * @param redirectUri          授权回调地址（仅 authorization_code 和 implicit 模式需要）
     * @return 校验通过的客户端实体
     * @throws ServiceException 若任一校验不通过
     */
    @Override
    public OAuth2ClientDO validOAuthClientFromCache(String clientId, String clientSecret, String authorizedGrantType,
                                                    Collection<String> scopes, String redirectUri) {
        // 1. 从缓存获取客户端（缓存中无则查库并回填）
        OAuth2ClientDO client = getSelf().getOAuth2ClientFromCache(clientId);
        if (client == null) {
            throw exception(OAUTH2_CLIENT_NOT_EXISTS);
        }
        if (CommonStatusEnum.isDisable(client.getStatus())) {
            throw exception(OAUTH2_CLIENT_DISABLE);
        }

        // 2. 校验客户端密钥（若提供了）
        if (StrUtil.isNotEmpty(clientSecret) && !ObjectUtil.equal(client.getSecret(), clientSecret)) {
            throw exception(OAUTH2_CLIENT_CLIENT_SECRET_ERROR);
        }

        // 3. 校验授权类型
        if (StrUtil.isNotEmpty(authorizedGrantType) &&
                !CollUtil.contains(client.getAuthorizedGrantTypes(), authorizedGrantType)) {
            throw exception(OAUTH2_CLIENT_AUTHORIZED_GRANT_TYPE_NOT_EXISTS);
        }

        // 4. 校验 scope 范围（请求的 scope 必须是客户端 scope 的子集）
        if (CollUtil.isNotEmpty(scopes) && !CollUtil.containsAll(client.getScopes(), scopes)) {
            throw exception(OAUTH2_CLIENT_SCOPE_OVER); // 超出授权范围
        }

        // 5. 校验 redirect_uri（支持前缀匹配，用于动态参数场景）
        if (StrUtil.isNotEmpty(redirectUri) && !StrUtils.startWithAny(redirectUri, client.getRedirectUris())) {
            throw exception(OAUTH2_CLIENT_REDIRECT_URI_NOT_MATCH, redirectUri);
        }

        return client;
    }

    /**
     * 获取当前类的 Spring 代理对象。
     * <p>
     * 用于解决在同一个类中调用带有 @Cacheable 注解的方法时，AOP 失效的问题。
     * Spring 的 AOP 是基于代理的，内部调用不会走代理，因此需通过 Spring 容器获取代理实例。
     *
     * @return 当前类的 Spring 代理对象
     */
    private OAuth2ClientServiceImpl getSelf() {
        return SpringUtil.getBean(getClass());
    }

}