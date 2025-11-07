package cn.iocoder.yudao.module.system.service.oauth2;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.token.OAuth2AccessTokenPageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2AccessTokenMapper;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2RefreshTokenMapper;
import cn.iocoder.yudao.module.system.dal.redis.oauth2.OAuth2AccessTokenRedisDAO;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * OAuth2.0 Token Service 实现类
 *
 * 这个类负责管理 OAuth2.0 的访问令牌(Access Token)和刷新令牌(Refresh Token)
 * 主要功能包括：
 * 1. 创建访问令牌和刷新令牌
 * 2. 刷新访问令牌
 * 3. 校验和获取访问令牌
 * 4. 删除访问令牌
 *
 * @author 芋道源码
 */
@Service
public class OAuth2TokenServiceImpl implements OAuth2TokenService {

    // ========== 数据访问层依赖 ==========

    @Resource
    private OAuth2AccessTokenMapper oauth2AccessTokenMapper; // 访问令牌的数据库操作类
    @Resource
    private OAuth2RefreshTokenMapper oauth2RefreshTokenMapper; // 刷新令牌的数据库操作类

    @Resource
    private OAuth2AccessTokenRedisDAO oauth2AccessTokenRedisDAO; // 访问令牌的Redis缓存操作类，用于提高查询性能

    // ========== 业务服务依赖 ==========

    @Resource
    private OAuth2ClientService oauth2ClientService; // OAuth2客户端服务，用于校验客户端信息
    @Resource
    @Lazy // 懒加载，避免循环依赖（因为AdminUserService可能也依赖了本服务）
    private AdminUserService adminUserService; // 管理员用户服务，用于获取用户详细信息

    /**
     * 创建访问令牌
     *
     * 这是用户登录成功后调用的核心方法，会同时创建访问令牌和刷新令牌
     *
     * @param userId 用户ID，表示是哪个用户登录
     * @param userType 用户类型，例如：1-管理员用户，2-会员用户
     * @param clientId 客户端ID，表示是从哪个客户端登录的（如PC端、移动端等）
     * @param scopes 授权范围列表，表示该令牌具有哪些权限范围
     * @return 创建的访问令牌对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // 开启事务，如果出现异常则回滚，保证数据一致性
    public OAuth2AccessTokenDO createAccessToken(Long userId, Integer userType, String clientId, List<String> scopes) {
        // 步骤1: 从缓存中校验客户端是否合法，如果不合法会抛出异常
        OAuth2ClientDO clientDO = oauth2ClientService.validOAuthClientFromCache(clientId);

        // 步骤2: 创建刷新令牌（Refresh Token）
        // 刷新令牌的有效期通常比访问令牌长，用于在访问令牌过期后获取新的访问令牌
        OAuth2RefreshTokenDO refreshTokenDO = createOAuth2RefreshToken(userId, userType, clientDO, scopes);

        // 步骤3: 基于刷新令牌创建访问令牌（Access Token）
        // 访问令牌用于实际的API访问，有效期较短
        return createOAuth2AccessToken(refreshTokenDO, clientDO);
    }

    /**
     * 刷新访问令牌
     *
     * 当访问令牌过期后，客户端可以使用刷新令牌来获取新的访问令牌，而不需要用户重新登录
     * 这是OAuth2.0提供的一个重要机制，可以在保证安全的前提下提升用户体验
     *
     * @param refreshToken 刷新令牌字符串
     * @param clientId 客户端ID，需要与刷新令牌的客户端ID一致
     * @return 新创建的访问令牌对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // 开启事务，确保删除旧令牌和创建新令牌的操作原子性
    public OAuth2AccessTokenDO refreshAccessToken(String refreshToken, String clientId) {
        // 步骤1: 根据刷新令牌查询数据库中的刷新令牌记录
        OAuth2RefreshTokenDO refreshTokenDO = oauth2RefreshTokenMapper.selectByRefreshToken(refreshToken);
        if (refreshTokenDO == null) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "无效的刷新令牌");
        }

        // 步骤2: 校验客户端ID是否合法
        OAuth2ClientDO clientDO = oauth2ClientService.validOAuthClientFromCache(clientId);
        // 步骤3: 校验客户端ID是否与刷新令牌中存储的客户端ID一致
        // 这是为了防止客户端A使用客户端B的刷新令牌来获取访问令牌
        if (ObjectUtil.notEqual(clientId, refreshTokenDO.getClientId())) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "刷新令牌的客户端编号不正确");
        }

        // 步骤4: 删除该刷新令牌关联的所有旧访问令牌
        // 因为我们要创建新的访问令牌了，旧的就不应该再被使用
        List<OAuth2AccessTokenDO> accessTokenDOs = oauth2AccessTokenMapper.selectListByRefreshToken(refreshToken);
        if (CollUtil.isNotEmpty(accessTokenDOs)) {
            // 从数据库中删除
            oauth2AccessTokenMapper.deleteByIds(convertSet(accessTokenDOs, OAuth2AccessTokenDO::getId));
            // 从Redis缓存中删除
            oauth2AccessTokenRedisDAO.deleteList(convertSet(accessTokenDOs, OAuth2AccessTokenDO::getAccessToken));
        }

        // 步骤5: 检查刷新令牌是否已过期
        if (DateUtils.isExpired(refreshTokenDO.getExpiresTime())) {
            // 如果过期了，删除刷新令牌并抛出异常，用户需要重新登录
            oauth2RefreshTokenMapper.deleteById(refreshTokenDO.getId());
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "刷新令牌已过期");
        }

        // 步骤6: 基于刷新令牌创建新的访问令牌
        return createOAuth2AccessToken(refreshTokenDO, clientDO);
    }

    /**
     * 获取访问令牌
     *
     * 该方法会按照优先级从不同的存储中查找访问令牌：
     * 1. 首先从Redis缓存中查找（速度最快）
     * 2. 然后从MySQL数据库中查找访问令牌
     * 3. 如果找不到，还会尝试从刷新令牌中查找（兼容特殊场景）
     *
     * @param accessToken 访问令牌字符串
     * @return 访问令牌对象，如果不存在则返回null
     */
    @Override
    public OAuth2AccessTokenDO getAccessToken(String accessToken) {
        // 步骤1: 优先从 Redis 缓存中获取，因为Redis的查询速度比MySQL快得多
        OAuth2AccessTokenDO accessTokenDO = oauth2AccessTokenRedisDAO.get(accessToken);
        if (accessTokenDO != null) {
            return accessTokenDO;
        }

        // 步骤2: 如果Redis中没有，则从 MySQL 数据库中查询访问令牌
        accessTokenDO = oauth2AccessTokenMapper.selectByAccessToken(accessToken);
        if (accessTokenDO == null) {
            // 步骤3: 特殊处理 - 尝试从刷新令牌中查找
            // 为什么要这样做？
            // 原因：有些场景下客户端不方便使用刷新令牌来刷新访问令牌
            // 例如：
            // 1. 积木报表只允许传递 token 参数，不支持传递 refresh_token
            // 2. WebSocket 连接的 token 直接放在 URL 上，没有地方传递 refresh_token
            // 所以这里允许直接使用刷新令牌作为访问令牌使用
            OAuth2RefreshTokenDO refreshTokenDO = oauth2RefreshTokenMapper.selectByRefreshToken(accessToken);
            if (refreshTokenDO != null && !DateUtils.isExpired(refreshTokenDO.getExpiresTime())) {
                // 将刷新令牌转换为访问令牌格式返回
                accessTokenDO = convertToAccessToken(refreshTokenDO);
            }
        }

        // 步骤4: 如果在 MySQL 中找到了访问令牌，并且未过期，则将其写入 Redis 缓存
        // 这样下次查询就能直接从Redis中获取，提高查询效率
        if (accessTokenDO != null && !DateUtils.isExpired(accessTokenDO.getExpiresTime())) {
            oauth2AccessTokenRedisDAO.set(accessTokenDO);
        }
        return accessTokenDO;
    }

    /**
     * 校验访问令牌
     *
     * 该方法用于验证访问令牌是否有效，会进行两项检查：
     * 1. 令牌是否存在
     * 2. 令牌是否已过期
     * 如果校验不通过，会抛出异常
     *
     * @param accessToken 访问令牌字符串
     * @return 有效的访问令牌对象
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 如果令牌不存在或已过期
     */
    @Override
    public OAuth2AccessTokenDO checkAccessToken(String accessToken) {
        // 步骤1: 先获取访问令牌
        OAuth2AccessTokenDO accessTokenDO = getAccessToken(accessToken);
        if (accessTokenDO == null) {
            // 如果令牌不存在，抛出401未授权异常
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "访问令牌不存在");
        }
        // 步骤2: 检查令牌是否已过期
        if (DateUtils.isExpired(accessTokenDO.getExpiresTime())) {
            // 如果令牌已过期，抛出401未授权异常
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "访问令牌已过期");
        }
        // 校验通过，返回访问令牌对象
        return accessTokenDO;
    }

    /**
     * 移除访问令牌
     *
     * 该方法用于删除访问令牌和关联的刷新令牌，通常在用户退出登录时调用
     * 删除操作包括：
     * 1. 从MySQL数据库中删除访问令牌
     * 2. 从Redis缓存中删除访问令牌
     * 3. 从MySQL数据库中删除关联的刷新令牌
     *
     * @param accessToken 访问令牌字符串
     * @return 被删除的访问令牌对象，如果令牌不存在则返回null
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // 开启事务，确保删除操作的原子性
    public OAuth2AccessTokenDO removeAccessToken(String accessToken) {
        // 步骤1: 从数据库中查询访问令牌
        OAuth2AccessTokenDO accessTokenDO = oauth2AccessTokenMapper.selectByAccessToken(accessToken);
        if (accessTokenDO == null) {
            // 如果令牌不存在，直接返回null
            return null;
        }

        // 步骤2: 删除访问令牌
        oauth2AccessTokenMapper.deleteById(accessTokenDO.getId()); // 从MySQL数据库中删除
        oauth2AccessTokenRedisDAO.delete(accessToken); // 从Redis缓存中删除

        // 步骤3: 删除关联的刷新令牌
        // 因为访问令牌被删除了，对应的刷新令牌也就没有存在的意义了
        oauth2RefreshTokenMapper.deleteByRefreshToken(accessTokenDO.getRefreshToken());

        return accessTokenDO;
    }

    /**
     * 获取访问令牌分页列表
     *
     * 该方法用于在管理后台查询访问令牌列表，支持分页和条件查询
     * 管理员可以通过这个接口查看当前系统中有哪些用户在线，以及令牌的详细信息
     *
     * @param reqVO 分页查询请求对象，包含分页参数和查询条件
     * @return 访问令牌的分页结果
     */
    @Override
    public PageResult<OAuth2AccessTokenDO> getAccessTokenPage(OAuth2AccessTokenPageReqVO reqVO) {
        // 直接调用Mapper层的分页查询方法，返回分页结果
        return oauth2AccessTokenMapper.selectPage(reqVO);
    }

    /**
     * 创建OAuth2访问令牌（内部方法）
     *
     * 该方法负责生成访问令牌并保存到数据库和Redis缓存中
     * 访问令牌包含了用户身份信息、权限范围、过期时间等关键数据
     *
     * @param refreshTokenDO 刷新令牌对象，包含用户ID、用户类型、权限范围等信息
     * @param clientDO 客户端对象，包含客户端ID、访问令牌有效期等配置
     * @return 创建好的访问令牌对象
     */
    private OAuth2AccessTokenDO createOAuth2AccessToken(OAuth2RefreshTokenDO refreshTokenDO, OAuth2ClientDO clientDO) {
        // 步骤1: 构建访问令牌对象，设置各项属性
        OAuth2AccessTokenDO accessTokenDO = new OAuth2AccessTokenDO()
                .setAccessToken(generateAccessToken()) // 生成随机的访问令牌字符串（UUID格式）
                .setUserId(refreshTokenDO.getUserId()) // 设置用户ID
                .setUserType(refreshTokenDO.getUserType()) // 设置用户类型（管理员/会员）
                .setUserInfo(buildUserInfo(refreshTokenDO.getUserId(), refreshTokenDO.getUserType())) // 构建用户信息（昵称、部门等）
                .setClientId(clientDO.getClientId()) // 设置客户端ID
                .setScopes(refreshTokenDO.getScopes()) // 设置权限范围
                .setRefreshToken(refreshTokenDO.getRefreshToken()) // 关联刷新令牌
                .setExpiresTime(LocalDateTime.now().plusSeconds(clientDO.getAccessTokenValiditySeconds())); // 计算过期时间 = 当前时间 + 有效期秒数

        // 步骤2: 手动设置租户编号
        // 为什么要手动设置？因为缓存到Redis的时候，需要有租户编号信息，否则多租户场景下会出现数据混乱
        accessTokenDO.setTenantId(TenantContextHolder.getTenantId());

        // 步骤3: 保存到MySQL数据库
        oauth2AccessTokenMapper.insert(accessTokenDO);

        // 步骤4: 保存到Redis缓存，提高后续查询性能
        oauth2AccessTokenRedisDAO.set(accessTokenDO);

        return accessTokenDO;
    }

    /**
     * 创建OAuth2刷新令牌（内部方法）
     *
     * 刷新令牌的有效期通常比访问令牌长，用于在访问令牌过期后获取新的访问令牌
     * 刷新令牌只保存在MySQL数据库中，不需要缓存到Redis，因为它的使用频率较低
     *
     * @param userId 用户ID
     * @param userType 用户类型（1-管理员，2-会员）
     * @param clientDO 客户端对象，包含刷新令牌有效期等配置
     * @param scopes 权限范围列表
     * @return 创建好的刷新令牌对象
     */
    private OAuth2RefreshTokenDO createOAuth2RefreshToken(Long userId, Integer userType, OAuth2ClientDO clientDO, List<String> scopes) {
        // 步骤1: 构建刷新令牌对象
        OAuth2RefreshTokenDO refreshToken = new OAuth2RefreshTokenDO()
                .setRefreshToken(generateRefreshToken()) // 生成随机的刷新令牌字符串（UUID格式）
                .setUserId(userId) // 设置用户ID
                .setUserType(userType) // 设置用户类型
                .setClientId(clientDO.getClientId()) // 设置客户端ID
                .setScopes(scopes) // 设置权限范围
                .setExpiresTime(LocalDateTime.now().plusSeconds(clientDO.getRefreshTokenValiditySeconds())); // 计算过期时间

        // 步骤2: 保存到MySQL数据库
        oauth2RefreshTokenMapper.insert(refreshToken);

        return refreshToken;
    }

    /**
     * 将刷新令牌转换为访问令牌对象（内部方法）
     *
     * 这是一个特殊的转换方法，用于在某些场景下直接使用刷新令牌作为访问令牌
     * 比如：积木报表、WebSocket等场景，它们不方便使用刷新令牌机制
     *
     * @param refreshTokenDO 刷新令牌对象
     * @return 转换后的访问令牌对象（但实际上令牌字符串是刷新令牌）
     */
    private OAuth2AccessTokenDO convertToAccessToken(OAuth2RefreshTokenDO refreshTokenDO) {
        // 步骤1: 将刷新令牌对象的属性复制到访问令牌对象
        OAuth2AccessTokenDO accessTokenDO = BeanUtils.toBean(refreshTokenDO, OAuth2AccessTokenDO.class)
                .setAccessToken(refreshTokenDO.getRefreshToken()); // 关键：将刷新令牌的值设置为访问令牌

        // 步骤2: 在正确的租户上下文中构建用户信息
        // 使��TenantUtils.execute确保在正确的租户环境下查询用户信息
        TenantUtils.execute(refreshTokenDO.getTenantId(),
                        () -> accessTokenDO.setUserInfo(buildUserInfo(refreshTokenDO.getUserId(), refreshTokenDO.getUserType())));

        return accessTokenDO;
    }

    /**
     * 构建用户信息映射（内部方法）
     *
     * 该方法用于加载用户的基本信息（如昵称、部门等），并存储到访问令牌中
     * 这样在使用访问令牌时，就可以直接获取到用户信息，而不需要再次查询数据库
     *
     * 这个信息最终会被 {@link cn.iocoder.yudao.framework.security.core.LoginUser} 使用
     *
     * @param userId 用户ID
     * @param userType 用户类型（1-管理员，2-会员）
     * @return 用户信息的键值对映射，包含昵称、部门ID等信息
     */
    private Map<String, String> buildUserInfo(Long userId, Integer userType) {
        // 步骤1: 校验用户ID是否有效
        if (userId == null || userId <= 0) {
            return Collections.emptyMap(); // 如果用户ID无效，返回空Map
        }

        // 步骤2: 根据用户类型加载不同的用户信息
        if (userType.equals(UserTypeEnum.ADMIN.getValue())) {
            // 2.1 管理员用户：从管理员用户服务中获取用户信息
            AdminUserDO user = adminUserService.getUser(userId);
            // 构建包含昵称和部门ID的Map
            return MapUtil.builder(LoginUser.INFO_KEY_NICKNAME, user.getNickname()) // 设置用户昵称
                    .put(LoginUser.INFO_KEY_DEPT_ID, StrUtil.toStringOrNull(user.getDeptId())) // 设置部门ID（转为字符串）
                    .build();
        } else if (userType.equals(UserTypeEnum.MEMBER.getValue())) {
            // 2.2 会员用户：目前暂不实现，返回空Map
            // 注意：如果需要支持会员用户信息，可以在这里添加相应的逻辑
            return Collections.emptyMap();
        }

        // 步骤3: 如果是未知的用户类型，抛出异常
        throw new IllegalArgumentException("未知用户类型：" + userType);
    }

    /**
     * 生成访问令牌字符串（内部方法）
     *
     * 使用UUID生成一个唯一的随机字符串作为访问令牌
     * fastSimpleUUID会生成一个不带"-"分隔符的32位UUID字符串
     * 例如：a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6
     *
     * @return 访问令牌字符串
     */
    private static String generateAccessToken() {
        return IdUtil.fastSimpleUUID();
    }

    /**
     * 生成刷新令牌字符串（内部方法）
     *
     * 使用UUID生成一个唯一的随机字符串作为刷新令牌
     * fastSimpleUUID会生成一个不带"-"分隔符的32位UUID字符串
     * 例如：p6o5n4m3l2k1j0i9h8g7f6e5d4c3b2a1
     *
     * @return 刷新令牌字符串
     */
    private static String generateRefreshToken() {
        return IdUtil.fastSimpleUUID();
    }

}
