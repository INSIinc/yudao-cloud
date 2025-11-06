package cn.iocoder.yudao.module.system.service.mail;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.system.dal.dataobject.mail.MailAccountDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mail.MailTemplateDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.mq.message.mail.MailSendMessage;
import cn.iocoder.yudao.module.system.mq.producer.mail.MailProducer;
import cn.iocoder.yudao.module.system.service.member.MemberService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hutool.extra.mail.MailAccount;
import org.dromara.hutool.extra.mail.MailUtil;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

/**
 * 邮箱发送 Service 实现类
 *
 * 功能说明：
 * 1. 负责邮件的发送业务逻辑处理
 * 2. 包括邮件模板校验、邮箱账号校验、参数校验等
 * 3. 使用消息队列异步发送邮件，提高系统响应速度
 * 4. 记录邮件发送日志，便于追踪和问题排查
 *
 * @author wangjingyi
 * @since 2022-03-21
 */
@Service
@Validated
@Slf4j
public class MailSendServiceImpl implements MailSendService {

    // ========== 依赖注入的服务 ==========

    /**
     * 管理员用户服务 - 用于获取管理员的邮箱地址
     */
    @Resource
    private AdminUserService adminUserService;

    /**
     * 会员服务 - 用于获取会员的邮箱地址
     */
    @Resource
    private MemberService memberService;

    /**
     * 邮箱账号服务 - 用于获取和校验发件邮箱账号信息
     */
    @Resource
    private MailAccountService mailAccountService;

    /**
     * 邮件模板服务 - 用于获取和格式化邮件模板内容
     */
    @Resource
    private MailTemplateService mailTemplateService;

    /**
     * 邮件日志服务 - 用于记录邮件发送日志
     */
    @Resource
    private MailLogService mailLogService;

    /**
     * 邮件消息生产者 - 用于发送异步邮件消息到消息队列
     */
    @Resource
    private MailProducer mailProducer;

    /**
     * 发送单个邮件
     *
     * 流程说明：
     * 1. 校验邮件模板和邮箱账号是否合法
     * 2. 校验模板参数是否完整
     * 3. 收集和验证所有收件人邮箱地址
     * 4. 创建邮件发送日志
     * 5. 如果模板启用，则发送MQ消息异步发送邮件
     *
     * @param toMails 收件人邮箱列表（主要接收人）
     * @param ccMails 抄送人邮箱列表（抄送）
     * @param bccMails 密送人邮箱列表（密送，其他人看不到）
     * @param userId 用户ID
     * @param userType 用户类型（1=管理员，2=会员）
     * @param templateCode 邮件模板编码
     * @param templateParams 模板参数（用于替换模板中的变量）
     * @return 邮件发送日志ID
     */
    @Override
    public Long sendSingleMail(Collection<String> toMails, Collection<String> ccMails, Collection<String> bccMails,
                               Long userId, Integer userType,
                               String templateCode, Map<String, Object> templateParams) {
        // ========== 第一步：校验阶段 ==========

        // 1.1 校验邮箱模版是否合法（模板是否存在）
        MailTemplateDO template = validateMailTemplate(templateCode);

        // 1.2 校验邮箱账号是否合法（发件账号是否存在）
        MailAccountDO account = validateMailAccount(template.getAccountId());

        // 1.3 校验邮件参数是否缺失（检查模板所需的所有参数是否都已提供）
        validateTemplateParams(template, templateParams);

        // ========== 第二步：组装收件人邮箱地址 ==========

        // 2.1 根据用户ID和用户类型获取用户的邮箱地址
        String userMail = getUserMail(userId, userType);

        // 2.2 使用LinkedHashSet去重并保持顺序
        Collection<String> toMailSet = new LinkedHashSet<>();    // 收件人集合
        Collection<String> ccMailSet = new LinkedHashSet<>();    // 抄送人集合
        Collection<String> bccMailSet = new LinkedHashSet<>();   // 密送人集合

        // 2.3 添加用户邮箱到收件人列表（如果邮箱格式合法）
        if (Validator.isEmail(userMail)) {
            toMailSet.add(userMail);
        }

        // 2.4 过滤并添加所有收件人（只添加格式合法的邮箱）
        if (CollUtil.isNotEmpty(toMails)) {
            toMails.stream().filter(Validator::isEmail).forEach(toMailSet::add);
        }

        // 2.5 过滤并添加所有抄送人
        if (CollUtil.isNotEmpty(ccMails)) {
            ccMails.stream().filter(Validator::isEmail).forEach(ccMailSet::add);
        }

        // 2.6 过滤并添加所有密送人
        if (CollUtil.isNotEmpty(bccMails)) {
            bccMails.stream().filter(Validator::isEmail).forEach(bccMailSet::add);
        }

        // 2.7 如果没有任何收件人，抛出异常
        if (CollUtil.isEmpty(toMailSet)) {
            throw exception(MAIL_SEND_MAIL_NOT_EXISTS);
        }

        // ========== 第三步：格式化邮件内容并创建日志 ==========

        // 3.1 判断是否发送邮件（如果模板被禁用，则不发送，只记录日志）
        Boolean isSend = CommonStatusEnum.ENABLE.getStatus().equals(template.getStatus());

        // 3.2 使用模板参数格式化邮件标题（替换模板中的变量）
        String title = mailTemplateService.formatMailTemplateContent(template.getTitle(), templateParams);

        // 3.3 使用模板参数格式化邮件内容（替换模板中的变量）
        String content = mailTemplateService.formatMailTemplateContent(template.getContent(), templateParams);

        // 3.4 创建邮件发送日志记录
        Long sendLogId = mailLogService.createMailLog(userId, userType, toMailSet, ccMailSet, bccMailSet,
                account, template, content, templateParams, isSend);

        // ========== 第四步：发送MQ消息异步执行发送邮件 ==========

        // 4.1 如果模板启用，则发送MQ消息，通过消息队列异步发送邮件
        // 好处：不阻塞当前请求，提高响应速度；即使发送失败也不影响主流程
        if (isSend) {
            mailProducer.sendMailSendMessage(sendLogId, toMailSet, ccMailSet, bccMailSet,
                    account.getId(), template.getNickname(), title, content);
        }

        // 4.2 返回邮件发送日志ID，用于后续查询发送结果
        return sendLogId;
    }

    /**
     * 根据用户ID和用户类型获取用户邮箱
     *
     * @param userId 用户ID
     * @param userType 用户类型（1=管理员，2=会员）
     * @return 用户邮箱地址，如果未找到返回null
     */
    private String getUserMail(Long userId, Integer userType) {
        // 如果用户ID或用户类型为空，直接返回null
        if (userId == null || userType == null) {
            return null;
        }

        // 如果是管理��类型，从管理员服务获取邮箱
        if (UserTypeEnum.ADMIN.getValue().equals(userType)) {
            AdminUserDO user = adminUserService.getUser(userId);
            if (user != null) {
                return user.getEmail();
            }
        }

        // 如果是会员类型，从会员服务获取邮箱
        if (UserTypeEnum.MEMBER.getValue().equals(userType)) {
            return memberService.getMemberUserEmail(userId);
        }

        // 其他情况返回null
        return null;
    }

    /**
     * 执行真正的邮件发送操作
     *
     * 说明：
     * 1. 此方法由MQ消费者调用，异步执行
     * 2. 使用Hutool的MailUtil工具类发送邮件
     * 3. 发送成功或失败都会更新日志记录
     *
     * @param message 邮件发送消息对象（包含所有发送所需的信息）
     */
    @Override
    public void doSendMail(MailSendMessage message) {
        // ========== 第一步：创建发送账号配置 ==========

        // 1.1 校验并获取邮箱账号信息
        MailAccountDO account = validateMailAccount(message.getAccountId());

        // 1.2 构建Hutool的邮件账号对象（包含SMTP服务器配置等）
        MailAccount mailAccount  = buildMailAccount(account, message.getNickname());

        // ========== 第二步：发送邮件并记录结果 ==========

        try {
            // 2.1 使用Hutool工具类发送邮件
            // 参数说明：
            // - mailAccount: 邮件账号配置
            // - toMails: 收件人列表
            // - ccMails: 抄送人列表
            // - bccMails: 密送人列表
            // - title: 邮件标题
            // - content: 邮件内容
            // - true: 表示内容为HTML格式
            String messageId = MailUtil.send(mailAccount, message.getToMails(), message.getCcMails(), message.getBccMails(),
                    message.getTitle(), message.getContent(), true);

            // 2.2 邮件发送成功，更新日志记录（记录消息ID，异常为null）
            mailLogService.updateMailSendResult(message.getLogId(), messageId, null);

        } catch (Exception e) {
            // 2.3 邮件发送失败，更新日志记录（消息ID为null，记录异常信息）
            mailLogService.updateMailSendResult(message.getLogId(), null, e);
        }
    }

    /**
     * 构建Hutool的邮件账号对象
     *
     * 说明：
     * 将数据库中的邮箱账号配置转换为Hutool库所需的MailAccount对象
     *
     * @param account 数据库中的邮箱账号实体
     * @param nickname 发件人昵称（可选）
     * @return Hutool的MailAccount对象
     */
    private MailAccount buildMailAccount(MailAccountDO account, String nickname) {
        // 构建发件人显示格式
        // 如果有昵称：昵称 <邮箱地址>，例如：系统管理员 <admin@example.com>
        // 如果没昵称：直接显示邮箱地址，例如：admin@example.com
        String from = StrUtil.isNotEmpty(nickname) ? nickname + " <" + account.getMail() + ">" : account.getMail();

        // 创建并配置MailAccount对象
        return new MailAccount()
                .setFrom(from)                                          // 设置发件人
                .setAuth(true)                                          // 开启SMTP认证
                .setUser(account.getUsername())                         // SMTP用户名
                .setPass(account.getPassword().toCharArray())           // SMTP密码（转为字符数组）
                .setHost(account.getHost())                             // SMTP服务器地址
                .setPort(account.getPort())                             // SMTP服务器端口
                .setSslEnable(account.getSslEnable())                   // 是否启用SSL加密
                .setStarttlsEnable(account.getStarttlsEnable());        // 是否启用STARTTLS加密
    }

    /**
     * 校验邮件模板是否存在
     *
     * 说明：
     * 从缓存中获取邮件模板，提高查询效率
     *
     * @param templateCode 模板编码
     * @return 邮件模板对象
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 如果模板不存在
     */
    @VisibleForTesting
    MailTemplateDO validateMailTemplate(String templateCode) {
        // 从缓存中获取邮件模板（考虑到效率，优先从缓存获取）
        MailTemplateDO template = mailTemplateService.getMailTemplateByCodeFromCache(templateCode);

        // 如果邮件模板不存在，抛出业务异常
        if (template == null) {
            throw exception(MAIL_TEMPLATE_NOT_EXISTS);
        }

        return template;
    }

    /**
     * 校验邮箱账号是否存在
     *
     * 说明：
     * 从缓存中获取邮箱账号，提高查询效率
     *
     * @param accountId 账号ID
     * @return 邮箱账号对象
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 如果账号不存在
     */
    @VisibleForTesting
    MailAccountDO validateMailAccount(Long accountId) {
        // 从缓存中获取邮箱账号（考虑到效率，优先从缓存获取）
        MailAccountDO account = mailAccountService.getMailAccountFromCache(accountId);

        // 如果邮箱账号不存在，抛出业务异常
        if (account == null) {
            throw exception(MAIL_ACCOUNT_NOT_EXISTS);
        }

        return account;
    }

    /**
     * 校验邮件参数是否缺失
     *
     * 说明：
     * 检查模板中定义的所有参数是否都已提供，防止发送邮件时出现变量未替换的情况
     *
     * @param template 邮箱模板对象
     * @param templateParams 实际提供的参数列表
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 如果有参数缺失
     */
    @VisibleForTesting
    void validateTemplateParams(MailTemplateDO template, Map<String, Object> templateParams) {
        // 遍历模板中定义的所有参数
        template.getParams().forEach(key -> {
            // 检查该参数是否在提供的参数列表中
            Object value = templateParams.get(key);

            // 如果参数值为null，说明该参数缺失，抛出异常
            if (value == null) {
                throw exception(MAIL_SEND_TEMPLATE_PARAM_MISS, key);
            }
        });
    }

}
