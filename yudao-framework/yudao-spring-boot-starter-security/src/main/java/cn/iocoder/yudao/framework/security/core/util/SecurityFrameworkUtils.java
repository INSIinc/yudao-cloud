package cn.iocoder.yudao.framework.security.core.util;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;

/**
 * 安全服务工具类
 *
 * 【中文说明】
 * 这个工具类是整个系统安全认证的核心工具类，主要功能包括：
 * 1. 从HTTP请求中提取用户的认证Token（令牌）
 * 2. 获取当前登录用户的信息（用户ID、昵称、部门等）
 * 3. 设置当前登录用户到Spring Security上下文中
 * 4. 判断是否需要跳过权限校验
 *
 * 使用场景：
 * - 在过滤器（Filter）中验证用户身份
 * - 在业务代码中获取当前登录用户信息
 * - 在权限控制中判断用户权限
 *
 * @author 芋道源码
 */
public class SecurityFrameworkUtils {

    /**
     * HEADER 认证头 value 的前缀
     *
     * 【中文说明】
     * 这是HTTP请求头中Authorization字段的标准前缀
     * 完整格式：Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * 其中 "Bearer " 就是这个前缀，后面跟着实际的Token
     */
    public static final String AUTHORIZATION_BEARER = "Bearer";

    /**
     * 【中文说明】
     * 登录用户信息在HTTP Header中的字段名
     * 用于在微服务之间传递用户信息
     */
    public static final String LOGIN_USER_HEADER = "login-user";

    /**
     * 【中文说明】
     * 私有构造函数，防止这个工具类被实例化
     * 因为所有方法都是静态的（static），不需要创建对象
     */
    private SecurityFrameworkUtils() {}

    /**
     * 从请求中，获得认证 Token
     *
     * 【中文说明】
     * 这个方法从HTTP请求中提取用户的认证令牌（Token）
     *
     * 查找顺序：
     * 1. 先从请求头（Header）中查找
     * 2. 如果Header中没有，再从请求参数（Parameter）中查找
     * 3. 如果找到的Token包含"Bearer "前缀，会自动去除
     *
     * 例如：
     * - 输入：Authorization: Bearer abc123
     * - 输出：abc123
     *
     * @param request 请求
     * @param headerName 认证 Token 对应的 Header 名字
     * @param parameterName 认证 Token 对应的 Parameter 名字
     * @return 认证 Token
     */
    public static String obtainAuthorization(HttpServletRequest request,
                                             String headerName, String parameterName) {
        // 1. 获得 Token。优先级：Header > Parameter
        // 【中文说明】首先尝试从请求头中获取Token
        String token = request.getHeader(headerName);
        if (StrUtil.isEmpty(token)) {
            // 【中文说明】如果请求头中没有，则从请求参数中获取（例如URL参数：?token=abc123）
            token = request.getParameter(parameterName);
        }
        if (!StringUtils.hasText(token)) {
            // 【中文说明】如果两处都没找到，返回null
            return null;
        }
        // 2. 去除 Token 中带的 Bearer
        // 【中文说明】查找"Bearer "字符串的位置
        int index = token.indexOf(AUTHORIZATION_BEARER + " ");
        // 【中文说明】如果找到"Bearer "前缀，截取后面的部分；否则直接返回原Token
        return index >= 0 ? token.substring(index + 7).trim() : token;
    }

    /**
     * 获得当前认证信息
     *
     * 【中文说明】
     * 从Spring Security的安全上下文中获取当前用户的认证信息
     *
     * Spring Security使用ThreadLocal存储每个线程（请求）的用户信息
     * 这样不同的用户请求不会互相干扰
     *
     * Authentication对象包含：
     * - principal：用户主体信息（通常是LoginUser对象）
     * - credentials：用户凭证（通常是密码，但认证后会被清空）
     * - authorities：用户权限列表
     *
     * @return 认证信息
     */
    public static Authentication getAuthentication() {
        // 【中文说明】获取Spring Security的安全上下文（SecurityContext）
        SecurityContext context = SecurityContextHolder.getContext();
        if (context == null) {
            // 【中文说明】如果上下文不存在，说明用户未登录或不在Web请求环境中
            return null;
        }
        // 【中文说明】从上下文中获取认证信息
        return context.getAuthentication();
    }

    /**
     * 获取当前用户
     *
     * 【中文说明】
     * 获取当前登录的用户对象（LoginUser）
     *
     * 这是最常用的方法之一，用于：
     * - 判断用户是否已登录
     * - 获取用户的详细信息
     * - 进行权限判断
     *
     * 如果用户未登录，返回null
     *
     * @return 当前用户
     */
    @Nullable
    public static LoginUser getLoginUser() {
        // 【中文说明】先获取认证信息
        Authentication authentication = getAuthentication();
        if (authentication == null) {
            // 【中文说明】认证信息为空，说明用户未登录
            return null;
        }
        // 【中文说明】从认证信息中获取principal（用户主体），并判断是否为LoginUser类型
        // instanceof：Java关键字，用于判断对象是否是某个类的实例
        return authentication.getPrincipal() instanceof LoginUser ? (LoginUser) authentication.getPrincipal() : null;
    }

    /**
     * 获得当前用户的编号，从上下文中
     *
     * 【中文说明】
     * 快捷方法：直接获取当前登录用户的ID
     *
     * 这是最常用的方法，因为很多业务操作都需要知道"是谁在操作"
     *
     * 例如：
     * - 创建订单时，记录是哪个用户创建的
     * - 修改数据时，记录是谁修改的
     * - 查询数据时，只查询属于当前用户的数据
     *
     * @return 用户编号
     */
    @Nullable
    public static Long getLoginUserId() {
        // 【中文说明】先获取登录用户对象
        LoginUser loginUser = getLoginUser();
        // 【中文说明】如果用户已登录，返回用户ID；否则返回null
        return loginUser != null ? loginUser.getId() : null;
    }

    /**
     * 获得当前用户的昵称，从上下文中
     *
     * 【中文说明】
     * 快捷方法：获取当前登录用户的昵称（显示名称）
     *
     * 昵称通常用于：
     * - 页面显示"欢迎您，XXX"
     * - 操作日志中显示操作人名称
     * - 评论、留言时显示用户名
     *
     * @return 昵称
     */
    @Nullable
    public static String getLoginUserNickname() {
        // 【中文说明】先获取登录用户对象
        LoginUser loginUser = getLoginUser();
        // 【中文说明】从用户信息Map中获取昵称字段
        // MapUtil.getStr()：Hutool工具类方法，安全地从Map中获取String值
        return loginUser != null ? MapUtil.getStr(loginUser.getInfo(), LoginUser.INFO_KEY_NICKNAME) : null;
    }

    /**
     * 获得当前用户的部门编号，从上下文中
     *
     * 【中文说明】
     * 快捷方法：获取当前登录用户所属的部门ID
     *
     * 部门ID通常用于：
     * - 数据权限控制（只能看本部门的数据）
     * - 工作流审批（按部门流转）
     * - 统计报表（按部门统计）
     *
     * @return 部门编号
     */
    @Nullable
    public static Long getLoginUserDeptId() {
        // 【中文说明】先获取登录用户对象
        LoginUser loginUser = getLoginUser();
        // 【中文说明】从用户信息Map中获取部门ID字段
        // MapUtil.getLong()：Hutool工具类方法，安全地从Map中获取Long值
        return loginUser != null ? MapUtil.getLong(loginUser.getInfo(), LoginUser.INFO_KEY_DEPT_ID) : null;
    }

    /**
     * 设置当前用户
     *
     * 【中文说明】
     * 将用户信息设置到Spring Security的上下文中，表示该用户已登录
     *
     * 调用时机：
     * - 用户登录成功后
     * - Token验证通过后
     * - 微服务间传递用户信息后
     *
     * 这个方法做了两件事：
     * 1. 设置到Spring Security上下文（供Spring Security使用）
     * 2. 设置到Request属性（供日志过滤器等使用）
     *
     * 为什么要设置两次？
     * 因为Spring Security的过滤器执行顺序靠后，
     * 某些前置过滤器（如访问日志过滤器）需要提前获取用户信息
     *
     * @param loginUser 登录用户
     * @param request 请求
     */
    public static void setLoginUser(LoginUser loginUser, HttpServletRequest request) {
        // 创建 Authentication，并设置到上下文
        // 【中文说明】构建Spring Security的认证对象
        Authentication authentication = buildAuthentication(loginUser, request);
        // 【中文说明】将认证对象设置到安全上下文中（核心步骤！）
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 额外设置到 request 中，用于 ApiAccessLogFilter 可以获取到用户编号；
        // 原因是，Spring Security 的 Filter 在 ApiAccessLogFilter 后面，在它记录访问日志时，线上上下文已经没有用户编号等信息
        // 【中文说明】同时将用户信息设置到Request的属性中，方便其他组件使用
        if (request != null) {
            // 【中文说明】设置用户ID到Request属性
            WebFrameworkUtils.setLoginUserId(request, loginUser.getId());
            // 【中文说明】设置用户类型到Request属性（如：管理员、普通用户等）
            WebFrameworkUtils.setLoginUserType(request, loginUser.getUserType());
        }
    }

    /**
     * 【中文说明】
     * 私有方法：构建Spring Security的认证对象（Authentication）
     *
     * 这个方法将我们的LoginUser对象包装成Spring Security能识别的格式
     *
     * 参数说明：
     * - 第一个参数：principal（用户主体） - 就是我们的LoginUser对象
     * - 第二个参数：credentials（凭证） - 传null，因为认证后不需要保存密码
     * - 第三个参数：authorities（权限列表） - 传空集合，权限由其他机制控制
     */
    private static Authentication buildAuthentication(LoginUser loginUser, HttpServletRequest request) {
        // 创建 UsernamePasswordAuthenticationToken 对象
        // 【中文说明】UsernamePasswordAuthenticationToken是Spring Security的标准认证令牌
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                loginUser, null, Collections.emptyList());
        // 【中文说明】设置认证详情（包含IP地址、Session ID等信息）
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return authenticationToken;
    }

    /**
     * 是否条件跳过权限校验，包括数据权限、功能权限
     *
     * 判断当前请求是否需要跳过权限检查
     *
     * 跳过权限检查的场景：
     * - 用户正在跨租户访问数据
     *
     * 什么是跨租户访问？
     * 在多租户系统中（SaaS系统），每个租户的数据是隔离的。
     * 但系统管理员可能需要访问其他租户的数据（例如：技术支持、数据迁移）。
     * 此时就是"跨租户访问"。
     *
     * 为什么跨租户访问要跳过权限检查？
     * 因为用户的权限配置是基于自己租户的，无法验证对其他租户数据的权限。
     * 只有具备跨租户访问能力的用户才能触发这个逻辑。
     *
     * @return 是否跳过
     */
    public static boolean skipPermissionCheck() {
        // 【中文说明】先获取当前登录用户
        LoginUser loginUser = getLoginUser();
        if (loginUser == null) {
            // 【中文说明】用户未登录，不跳过（会被拦截）
            return false;
        }
        if (loginUser.getVisitTenantId() == null) {
            // 【中文说明】没有访问其他租户，是正常访问自己租户的数据，不跳过
            return false;
        }
        // 重点：跨租户访问时，无法进行权限校验
        // 【中文说明】
        // 比较"正在访问的租户ID"和"用户自己的租户ID"
        // 如果两者不相等，说明是跨租户访问，返回true（跳过权限检查）
        // ObjUtil.notEqual()：判断两个对象是否不相等
        return ObjUtil.notEqual(loginUser.getVisitTenantId(), loginUser.getTenantId());
    }

}
