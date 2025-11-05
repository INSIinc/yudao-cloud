package cn.iocoder.yudao.module.system.util.oauth2;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * OAuth2 相关的工具类
 *
 * 这个工具类提供了OAuth2授权流程中常用的方法，主要包括：
 * 1. 构建授权码模式的重定向URI
 * 2. 构建简化模式的重定向URI
 * 3. 构建授权失败时的重定向URI
 * 4. 处理授权范围(scope)的相关方法
 *
 * @author 芋道源码
 */
public class OAuth2Utils {

    /**
     * 构建授权码模式下，重定向的 URI
     *
     * 授权码模式是OAuth2中最安全的授权方式，流程如下：
     * 1. 用户同意授权后，授权服务器会生成一个授权码(code)
     * 2. 将授权码通过重定向URI返回给客户端
     * 3. 客户端再用授权码去交换访问令牌(access_token)
     *
     * 示例返回：http://example.com/callback?code=ABC123&state=xyz
     *
     * copy from Spring Security OAuth2 的 AuthorizationEndpoint 类的 getSuccessfulRedirect 方法
     *
     * @param redirectUri 重定向 URI - 用户授权后要跳转回的地址
     * @param authorizationCode 授权码 - 临时的授权凭证，用于换取访问令牌
     * @param state 状态 - 客户端传入的随机字符串，用于防止CSRF攻击
     * @return 授权码模式下的重定向 URI - 包含授权码和状态参数的完整URL
     */
    public static String buildAuthorizationCodeRedirectUri(String redirectUri, String authorizationCode, String state) {
        // 创建一个有序的Map来存储查询参数，保证参数顺序
        Map<String, String> query = new LinkedHashMap<>();
        // 添加授权码参数
        query.put("code", authorizationCode);
        // 如果有state参数，也一并返回（用于客户端验证，防止CSRF攻击）
        if (state != null) {
            query.put("state", state);
        }
        // 将参数拼接到重定向URI上，false表示参数放在查询字符串(?后面)而不是Fragment(#后面)
        return HttpUtils.append(redirectUri, query, null, false);
    }

    /**
     * 构建简化模式下，重定向的 URI
     *
     * 简化模式(Implicit Grant)适用于纯前端应用，特点：
     * 1. 省略了授权码这一步骤，直接返回访问令牌
     * 2. 访问令牌通过URL的Fragment(#后面)返回，浏览器不会发送给服务器
     * 3. 安全性较低，但使用简单
     *
     * 示例返回：http://example.com/callback#access_token=TOKEN&token_type=bearer&expires_in=3600&scope=read write
     *
     * copy from Spring Security OAuth2 的 AuthorizationEndpoint 类的 appendAccessToken 方法
     *
     * @param redirectUri 重定向 URI - 授权成功后要跳转的地址
     * @param accessToken 访问令牌 - 用于访问受保护资源的凭证
     * @param state 状态 - 防止CSRF攻击的随机字符串
     * @param expireTime 过期时间 - 令牌的失效时间
     * @param scopes 授权范围 - 令牌可以访问的权限范围，如：read、write等
     * @param additionalInformation 附加信息 - 其他需要返回给客户端的自定义信息
     * @return 简化授权模式下的重定向 URI - 包含访问令牌等信息的完整URL
     */
    public static String buildImplicitRedirectUri(String redirectUri, String accessToken, String state, LocalDateTime expireTime,
                                                  Collection<String> scopes, Map<String, Object> additionalInformation) {
        // vars存储所有要返回的参数值（可以是任意类型）
        Map<String, Object> vars = new LinkedHashMap<String, Object>();
        // keys用于映射参数名（用于特殊处理某些参数）
        Map<String, String> keys = new HashMap<String, String>();

        // 添加访问令牌
        vars.put("access_token", accessToken);
        // 添加令牌类型，bearer表示持有者令牌（谁持有谁就能用）
        vars.put("token_type", SecurityFrameworkUtils.AUTHORIZATION_BEARER.toLowerCase());

        // 如果有state参数，返回给客户端用于验证
        if (state != null) {
            vars.put("state", state);
        }

        // 如果有过期时间，计算还有多少秒过期
        if (expireTime != null) {
            vars.put("expires_in", getExpiresIn(expireTime));
        }

        // 如果有授权范围，将集合转换为空格分隔的字符串（如："read write"）
        if (CollUtil.isNotEmpty(scopes)) {
            vars.put("scope", buildScopeStr(scopes));
        }

        // 如果有附加信息，遍历添加（加上"extra_"前缀以区分）
        if (CollUtil.isNotEmpty(additionalInformation)) {
            for (String key : additionalInformation.keySet()) {
                Object value = additionalInformation.get(key);
                if (value != null) {
                    // 建立键名映射关系
                    keys.put("extra_" + key, key);
                    // 添加实际值
                    vars.put("extra_" + key, value);
                }
            }
        }

        // 注意：简化模式不返回刷新令牌(refresh_token)，即使有也不包含
        // true表示参数放在Fragment(#后面)，这样浏览器不会将令牌发送到服务器，更安全
        return HttpUtils.append(redirectUri, vars, keys, true);
    }

    /**
     * 构建授权失败时的重定向 URI
     *
     * 当OAuth2授权过程中出现错误时（如用户拒绝授权、参数错误等），
     * 需要将错误信息通过重定向返回给客户端
     *
     * 示例返回：http://example.com/callback?error=access_denied&error_description=用户拒绝授权&state=xyz
     *
     * @param redirectUri 重定向 URI - 要跳转的地址
     * @param responseType 响应类型 - 如"code"(授权码模式)或"token"(简化模式)
     * @param state 状态 - 原样返回客户端传入的state
     * @param error 错误码 - 标准的OAuth2错误码，如：access_denied、invalid_request等
     * @param description 错误描述 - 对错误的详细说明，帮助开发者定位问题
     * @return 包含错误信息的重定向 URI
     */
    public static String buildUnsuccessfulRedirect(String redirectUri, String responseType, String state,
                                                   String error, String description) {
        // 创建查询参数Map
        Map<String, String> query = new LinkedHashMap<String, String>();
        // 添加错误码
        query.put("error", error);
        // 添加错误描述
        query.put("error_description", description);
        // 如果有state，原样返回
        if (state != null) {
            query.put("state", state);
        }
        // 如果响应类型不包含"code"（即简化模式），则用Fragment方式返回，否则用查询字符串
        return HttpUtils.append(redirectUri, query, null, !responseType.contains("code"));
    }

    /**
     * 计算令牌还有多少秒过期
     *
     * OAuth2规范要求返回expires_in参数，表示令牌的有效期（单位：秒）
     * 客户端可以根据这个值来判断是否需要刷新令牌
     *
     * @param expireTime 过期时间 - 令牌的失效时刻
     * @return 剩余有效时间（秒） - 从现在到过期时间的秒数
     */
    public static long getExpiresIn(LocalDateTime expireTime) {
        // 计算当前时间到过期时间之间相差多少秒
        return LocalDateTimeUtil.between(LocalDateTime.now(), expireTime, ChronoUnit.SECONDS);
    }

    /**
     * 将授权范围集合转换为字符串
     *
     * OAuth2中的scope表示授权范围，可以有多个，用空格分隔
     * 例如：["read", "write"] -> "read write"
     *
     * @param scopes 授权范围集合 - 如：["read", "write", "admin"]
     * @return 空格分隔的授权范围字符串 - 如："read write admin"
     */
    public static String buildScopeStr(Collection<String> scopes) {
        // 使用空格连接集合中的所有元素
        return CollUtil.join(scopes, " ");
    }

    /**
     * 将授权范围字符串解析为列表
     *
     * 这是buildScopeStr的逆操作，用于解析客户端传来的scope参数
     * 例如："read write" -> ["read", "write"]
     *
     * @param scope 空格分隔的授权范围字符串 - 如："read write admin"
     * @return 授权范围列表 - 如：["read", "write", "admin"]
     */
    public static List<String> buildScopes(String scope) {
        // 按空格分割字符串，返回列表
        return StrUtil.split(scope, ' ');
    }

}
