package cn.iocoder.yudao.module.system.service.auth;

import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.system.api.logger.dto.LoginLogCreateReqDTO;
import cn.iocoder.yudao.module.system.api.sms.SmsCodeApi;
import cn.iocoder.yudao.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserBindReqDTO;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.*;
import cn.iocoder.yudao.module.system.convert.auth.AuthConvert;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.yudao.module.system.enums.logger.LoginResultEnum;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import cn.iocoder.yudao.module.system.enums.sms.SmsSceneEnum;
import cn.iocoder.yudao.module.system.service.logger.LoginLogService;
import cn.iocoder.yudao.module.system.service.member.MemberService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.yudao.module.system.service.social.SocialUserService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.CaptchaService;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

/**
 * 管理后台（Admin）认证服务实现类
 * 负责处理：用户名密码登录、手机验证码登录、社交登录、注册、重置密码、登出、刷新 Token 等核心认证逻辑
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class AdminAuthServiceImpl implements AdminAuthService {

    // 用户服务
    @Resource
    private AdminUserService userService;
    // 登录日志服务
    @Resource
    private LoginLogService loginLogService;
    // OAuth2 Token 服务
    @Resource
    private OAuth2TokenService oauth2TokenService;
    // 社交用户绑定/查询服务
    @Resource
    private SocialUserService socialUserService;
    // 会员服务（用于登出时获取用户名）
    @Resource
    private MemberService memberService;
    // 参数校验器
    @Resource
    private Validator validator;
    // 图形验证码服务（基于 Anji Captcha）
    @Resource
    private CaptchaService captchaService;
    // 短信验证码服务（Feign 远程调用）
    @Resource
    private SmsCodeApi smsCodeApi;

    /**
     * 是否启用图形验证码校验，默认为 true（开启）
     * 通过 @Setter 提供单测时关闭验证码的能力
     */
    @Value("${yudao.captcha.enable:true}")
    @Setter
    private Boolean captchaEnable;

    /**
     * 根据用户名和密码进行身份认证
     * 仅验证用户是否存在、密码是否匹配、是否被禁用，不创建 Token
     *
     * @param username 用户名
     * @param password 密码（明文）
     * @return 认证通过的用户信息
     * @throws ServiceException 认证失败时抛出异常（如：账号不存在、密码错误、账号禁用）
     */
    @Override
    public AdminUserDO authenticate(String username, String password) {
        final LoginLogTypeEnum logTypeEnum = LoginLogTypeEnum.LOGIN_USERNAME;

        // 1. 校验账号是否存在
        AdminUserDO user = userService.getUserByUsername(username);
        if (user == null) {
            createLoginLog(null, username, logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }

        // 2. 校验密码是否匹配（使用 BCrypt 加密比对）
        if (!userService.isPasswordMatch(password, user.getPassword())) {
            createLoginLog(user.getId(), username, logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }

        // 3. 校验账号是否被禁用
        if (CommonStatusEnum.isDisable(user.getStatus())) {
            createLoginLog(user.getId(), username, logTypeEnum, LoginResultEnum.USER_DISABLED);
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }

        return user;
    }

    /**
     * 管理员账号密码登录
     * 支持同时绑定第三方社交账号（如微信、QQ）
     *
     * @param reqVO 登录请求参数
     * @return 登录成功后的 Token 响应
     */
    @Override
    @DataPermission(enable = false) // 登录时不启用数据权限
    public AuthLoginRespVO login(AuthLoginReqVO reqVO) {
        // 校验图形验证码（若开启）
        validateCaptcha(reqVO);

        // 调用 authenticate 完成核心认证
        AdminUserDO user = authenticate(reqVO.getUsername(), reqVO.getPassword());

        // 若存在社交绑定信息，则执行绑定（首次用社交登录后，再用账号密码登录时自动绑定）
        if (reqVO.getSocialType() != null) {
            socialUserService.bindSocialUser(new SocialUserBindReqDTO(
                    user.getId(),
                    getUserType().getValue(),  // 用户类型：ADMIN
                    reqVO.getSocialType(),
                    reqVO.getSocialCode(),
                    reqVO.getSocialState()
            ));
        }

        // 登录成功：记录日志 + 生成 Token
        return createTokenAfterLoginSuccess(user.getId(), reqVO.getUsername(), LoginLogTypeEnum.LOGIN_USERNAME);
    }

    /**
     * 发送短信验证码（用于登录或重置密码）
     *
     * @param reqVO 包含手机号和场景的请求参数
     * @throws ServiceException 场景非法或手机号不存在时抛出异常
     */
    @Override
    public void sendSmsCode(AuthSmsSendReqVO reqVO) {
        // 重置密码场景：需先校验图形验证码（防刷）
        if (Objects.equals(SmsSceneEnum.ADMIN_MEMBER_RESET_PASSWORD.getScene(), reqVO.getScene())) {
            ResponseModel response = doValidateCaptcha(reqVO);
            if (!response.isSuccess()) {
                throw exception(AUTH_REGISTER_CAPTCHA_CODE_ERROR, response.getRepMsg());
            }
        }

        // 登录场景：校验手机号是否已注册（仅管理员）
        if (userService.getUserByMobile(reqVO.getMobile()) == null) {
            throw exception(AUTH_MOBILE_NOT_EXISTS); // 手机号未注册
        }

        // 调用 SMS 服务发送验证码
        smsCodeApi.sendSmsCode(AuthConvert.INSTANCE.convert(reqVO).setCreateIp(getClientIP()));
    }

    /**
     * 手机号 + 短信验证码登录
     *
     * @param reqVO 包含手机号和验证码的请求
     * @return 登录成功后的 Token 响应
     */
    @Override
    public AuthLoginRespVO smsLogin(AuthSmsLoginReqVO reqVO) {
        // 1. 验证短信验证码是否正确、是否过期、是否已被使用
        smsCodeApi.useSmsCode(AuthConvert.INSTANCE.convert(reqVO,
                        SmsSceneEnum.ADMIN_MEMBER_LOGIN.getScene(),
                        getClientIP()))
                .checkError(); // checkError() 会自动抛出异常

        // 2. 根据手机号查询用户
        AdminUserDO user = userService.getUserByMobile(reqVO.getMobile());
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }

        // 3. 登录成功：记录日志 + 生成 Token
        return createTokenAfterLoginSuccess(user.getId(), reqVO.getMobile(), LoginLogTypeEnum.LOGIN_MOBILE);
    }

    /**
     * 创建登录日志，并在登录成功时更新用户最后登录时间/IP
     *
     * @param userId 用户ID（可为 null，如账号不存在时）
     * @param username 用户名或手机号
     * @param logTypeEnum 登录类型（账号/手机/社交等）
     * @param loginResult 登录结果（成功/失败原因）
     */
    private void createLoginLog(Long userId, String username,
                                LoginLogTypeEnum logTypeEnum, LoginResultEnum loginResult) {
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(logTypeEnum.getType());
        reqDTO.setTraceId(TracerUtils.getTraceId()); // 链路追踪 ID
        reqDTO.setUserId(userId);
        reqDTO.setUserType(getUserType().getValue()); // ADMIN
        reqDTO.setUsername(username);
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(ServletUtils.getClientIP());
        reqDTO.setResult(loginResult.getResult());
        loginLogService.createLoginLog(reqDTO);

        // 仅当登录成功且 userId 有效时，更新最后登录信息
        if (userId != null && LoginResultEnum.SUCCESS.getResult().equals(loginResult.getResult())) {
            userService.updateUserLogin(userId, ServletUtils.getClientIP());
        }
    }

    /**
     * 第三方社交登录（如微信扫码）
     * 要求该社交账号已绑定到某个管理员账号
     *
     * @param reqVO 包含社交类型（如 WECHAT）、授权 code 和 state
     * @return 登录成功后的 Token 响应
     */
    @Override
    public AuthLoginRespVO socialLogin(AuthSocialLoginReqVO reqVO) {
        // 1. 使用 code 换取社交用户信息（并自动绑定？实际是查询已绑定的用户）
        SocialUserRespDTO socialUser = socialUserService.getSocialUserByCode(
                UserTypeEnum.ADMIN.getValue(),
                reqVO.getType(),
                reqVO.getCode(),
                reqVO.getState()
        );

        // 2. 若未绑定或返回空，则抛出“未绑定”异常
        if (socialUser == null || socialUser.getUserId() == null) {
            throw exception(AUTH_THIRD_LOGIN_NOT_BIND);
        }

        // 3. 查询绑定的管理员账号是否存在
        AdminUserDO user = userService.getUser(socialUser.getUserId());
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }

        // 4. 登录成功：记录日志 + 生成 Token
        return createTokenAfterLoginSuccess(user.getId(), user.getUsername(), LoginLogTypeEnum.LOGIN_SOCIAL);
    }

    /**
     * 用于测试的验证码校验方法（用户名密码登录）
     */
    @VisibleForTesting
    void validateCaptcha(AuthLoginReqVO reqVO) {
        ResponseModel response = doValidateCaptcha(reqVO);
        if (!response.isSuccess()) {
            // 记录验证码错误日志
            createLoginLog(null, reqVO.getUsername(), LoginLogTypeEnum.LOGIN_USERNAME, LoginResultEnum.CAPTCHA_CODE_ERROR);
            throw exception(AUTH_LOGIN_CAPTCHA_CODE_ERROR, response.getRepMsg());
        }
    }

    /**
     * 通用图形验证码校验逻辑（用于登录、注册、重置密码等）
     * 若系统配置关闭验证码，则直接返回成功
     *
     * @param reqVO 包含 captchaVerification 字段的请求对象
     * @return 验证结果
     */
    private ResponseModel doValidateCaptcha(CaptchaVerificationReqVO reqVO) {
        if (!captchaEnable) {
            return ResponseModel.success(); // 跳过校验
        }

        // 校验 captchaVerification 字段非空（使用 JSR-303 分组校验）
        ValidationUtils.validate(validator, reqVO, CaptchaVerificationReqVO.CodeEnableGroup.class);

        // 调用 Anji Captcha 服务验证
        CaptchaVO captchaVO = new CaptchaVO();
        captchaVO.setCaptchaVerification(reqVO.getCaptchaVerification());
        return captchaService.verification(captchaVO);
    }

    /**
     * 登录成功后统一处理：
     * - 创建登录日志
     * - 生成 Access Token
     * - 返回响应
     */
    private AuthLoginRespVO createTokenAfterLoginSuccess(Long userId, String username, LoginLogTypeEnum logType) {
        // 1. 记录成功登录日志
        createLoginLog(userId, username, logType, LoginResultEnum.SUCCESS);

        // 2. 创建 OAuth2 Access Token（关联默认客户端）
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.createAccessToken(
                userId,
                getUserType().getValue(), // ADMIN
                OAuth2ClientConstants.CLIENT_ID_DEFAULT, // 默认客户端 ID
                null // scope 为 null
        );

        // 3. 转换为响应 VO
        return AuthConvert.INSTANCE.convert(accessTokenDO);
    }

    /**
     * 刷新 Access Token（使用 Refresh Token）
     *
     * @param refreshToken 旧的刷新令牌
     * @return 新的 Token 响应
     */
    @Override
    public AuthLoginRespVO refreshToken(String refreshToken) {
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.refreshAccessToken(
                refreshToken,
                OAuth2ClientConstants.CLIENT_ID_DEFAULT
        );
        return AuthConvert.INSTANCE.convert(accessTokenDO);
    }

    /**
     * 用户登出：删除 Access Token 并记录登出日志
     *
     * @param token       要删除的 Access Token
     * @param logType     登出日志类型（由前端传入，如 101 表示主动登出）
     */
    @Override
    public void logout(String token, Integer logType) {
        // 删除 Token（若存在）
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.removeAccessToken(token);
        if (accessTokenDO == null) {
            return; // Token 无效，直接返回
        }

        // 记录登出日志
        createLogoutLog(accessTokenDO.getUserId(), accessTokenDO.getUserType(), logType);
    }

    /**
     * 创建登出日志（区分 ADMIN 和 MEMBER 类型）
     */
    private void createLogoutLog(Long userId, Integer userType, Integer logType) {
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(logType);
        reqDTO.setTraceId(TracerUtils.getTraceId());
        reqDTO.setUserId(userId);
        reqDTO.setUserType(userType);
        // 根据用户类型获取用户名：ADMIN 查用户名，MEMBER 查手机号
        if (ObjectUtil.equal(getUserType().getValue(), userType)) {
            reqDTO.setUsername(getUsername(userId));
        } else {
            reqDTO.setUsername(memberService.getMemberUserMobile(userId)); // 会员用手机号
        }
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(ServletUtils.getClientIP());
        reqDTO.setResult(LoginResultEnum.SUCCESS.getResult());
        loginLogService.createLoginLog(reqDTO);
    }

    /**
     * 根据用户 ID 获取 ADMIN 用户名（用于日志）
     */
    private String getUsername(Long userId) {
        if (userId == null) return null;
        AdminUserDO user = userService.getUser(userId);
        return user != null ? user.getUsername() : null;
    }

    /**
     * 返回当前服务的用户类型（固定为 ADMIN）
     */
    private UserTypeEnum getUserType() {
        return UserTypeEnum.ADMIN;
    }

    /**
     * 管理员注册（需验证码）
     * 注意：通常管理后台不开放注册，此方法可能用于某些特殊场景（如自建内部系统）
     *
     * @param registerReqVO 注册参数
     * @return 注册并登录后的 Token 响应
     */
    @Override
    public AuthLoginRespVO register(AuthRegisterReqVO registerReqVO) {
        // 1. 校验图形验证码
        validateCaptcha(registerReqVO);

        // 2. 注册用户（内部会校验用户名/手机号是否重复）
        Long userId = userService.registerUser(registerReqVO);

        // 3. 自动登录：生成 Token + 记录日志
        return createTokenAfterLoginSuccess(userId, registerReqVO.getUsername(), LoginLogTypeEnum.LOGIN_USERNAME);
    }

    /**
     * 用于测试的验证码校验方法（注册）
     */
    @VisibleForTesting
    void validateCaptcha(AuthRegisterReqVO reqVO) {
        ResponseModel response = doValidateCaptcha(reqVO);
        if (!response.isSuccess()) {
            throw exception(AUTH_REGISTER_CAPTCHA_CODE_ERROR, response.getRepMsg());
        }
    }

    /**
     * 重置管理员密码（通过手机验证码）
     *
     * @param reqVO 包含手机号、验证码、新密码
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(AuthResetPasswordReqVO reqVO) {
        // 1. 校验手机号是否注册
        AdminUserDO userByMobile = userService.getUserByMobile(reqVO.getMobile());
        if (userByMobile == null) {
            throw exception(USER_MOBILE_NOT_EXISTS);
        }

        // 2. 验证短信验证码（场景：重置密码）
        smsCodeApi.useSmsCode(new SmsCodeUseReqDTO()
                .setCode(reqVO.getCode())
                .setMobile(reqVO.getMobile())
                .setScene(SmsSceneEnum.ADMIN_MEMBER_RESET_PASSWORD.getScene())
                .setUsedIp(getClientIP())
        ).checkError();

        // 3. 更新密码（内部会加密）
        userService.updateUserPassword(userByMobile.getId(), reqVO.getPassword());
    }
}