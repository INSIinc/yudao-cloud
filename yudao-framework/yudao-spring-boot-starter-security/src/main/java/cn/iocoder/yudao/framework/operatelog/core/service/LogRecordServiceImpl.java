package cn.iocoder.yudao.framework.operatelog.core.service;

import cn.iocoder.yudao.framework.common.biz.system.logger.OperateLogCommonApi;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.common.biz.system.logger.dto.OperateLogCreateReqDTO;
import com.mzt.logapi.beans.LogRecord;
import com.mzt.logapi.service.ILogRecordService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

/**
 * 操作日志的实现类
 *
 * 这个类的作用是：当系统中发生某个“操作”（比如用户修改资料、删除订单等）时，
 * 会调用这个类，把这次操作的相关信息记录成一条“操作日志”。
 *
 * 它实现了第三方日志框架（mzt-log-api）提供的 ILogRecordService 接口，
 * 并通过我们自己封装的 OperateLogCommonApi 异步保存日志到数据库。
 *
 * @author HUIHUI
 */
@Slf4j // 自动生成日志对象，方便打印日志
public class LogRecordServiceImpl implements ILogRecordService {

    // 注入我们自己的操作日志服务接口，用于实际保存日志
    @Resource
    private OperateLogCommonApi operateLogApi;

    /**
     * 记录一条操作日志
     *
     * 这个方法会被日志框架自动调用（比如通过注解 @LogRecord）。
     * 它会把操作的详细信息封装成 OperateLogCreateReqDTO 对象，
     * 然后异步保存到数据库。
     *
     * @param logRecord 第三方日志框架传入的原始日志数据
     */
    @Override
    public void record(LogRecord logRecord) {
        // 创建一个用于保存操作日志的数据传输对象（DTO）
        OperateLogCreateReqDTO reqDTO = new OperateLogCreateReqDTO();
        try {
            // 1. 设置链路追踪ID（用于排查问题时跟踪整个请求流程）
            reqDTO.setTraceId(TracerUtils.getTraceId());

            // 2. 填充当前操作用户的信息（比如用户ID、用户类型）
            fillUserFields(reqDTO);

            // 3. 填充业务模块相关的信息（比如操作类型、具体动作、业务ID等）
            fillModuleFields(reqDTO, logRecord);

            // 4. 填充当前 HTTP 请求的信息（比如请求方式、URL、IP地址等）
            fillRequestFields(reqDTO);

            // 5. 异步调用接口，把操作日志保存到数据库（不会阻塞当前业务流程）
            operateLogApi.createOperateLogAsync(reqDTO);
        } catch (Throwable ex) {
            // 如果记录日志过程中出错了，就打印错误日志（因为是异步调用，主线程看不到异常）
            log.error("[record][url({}) log({}) 发生异常]", reqDTO.getRequestUrl(), reqDTO, ex);
        }
    }

    /**
     * 填充当前操作用户的信息
     *
     * 从安全上下文中获取当前登录用户（支持 Web、RPC、MQ、定时任务等场景）。
     * 如果没有登录用户（比如定时任务），就不设置用户信息。
     *
     * @param reqDTO 要填充的操作日志对象
     */
    private static void fillUserFields(OperateLogCreateReqDTO reqDTO) {
        // 从全局安全上下文中获取当前登录用户
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            // 没有登录用户，直接返回（比如是系统自动任务触发的操作）
            return;
        }
        // 设置用户ID和用户类型（例如：1表示管理员，2表示普通用户）
        reqDTO.setUserId(loginUser.getId());
        reqDTO.setUserType(loginUser.getUserType());
    }

    /**
     * 填充业务模块相关的信息
     *
     * 这些信息来自 @LogRecord 注解中配置的内容，用于描述“做了什么操作”。
     *
     * @param reqDTO 要填充的操作日志对象
     * @param logRecord 第三方日志框架传入的原始日志数据
     */
    public static void fillModuleFields(OperateLogCreateReqDTO reqDTO, LogRecord logRecord) {
        // 大模块类型，例如："CRM" 表示客户管理系统
        reqDTO.setType(logRecord.getType());

        // 具体操作名称，例如："转移客户"
        reqDTO.setSubType(logRecord.getSubType());

        // 业务数据的唯一编号，比如客户ID、订单ID等（注意：这里假设 bizNo 是数字字符串）
        reqDTO.setBizId(Long.parseLong(logRecord.getBizNo()));

        // 操作的具体内容描述，例如："将客户A转移给销售员B"
        reqDTO.setAction(logRecord.getAction());

        // 额外信息，通常是以 JSON 字符串形式存储，用于记录复杂业务数据
        // 例如：{"orderId": "1001", "oldOwner": "张三", "newOwner": "李四"}
        reqDTO.setExtra(logRecord.getExtra());
    }

    /**
     * 填充当前 HTTP 请求的相关信息
     *
     * 这些信息只有在 Web 请求（比如浏览器或APP调用接口）时才有。
     * 如果是在 MQ 消息或定时任务中触发的操作，request 会是 null。
     *
     * @param reqDTO 要填充的操作日志对象
     */
    private static void fillRequestFields(OperateLogCreateReqDTO reqDTO) {
        // 从当前线程中获取 HttpServletRequest 对象（代表当前 Web 请求）
        HttpServletRequest request = ServletUtils.getRequest();
        if (request == null) {
            // 不是 Web 请求（比如是 MQ 消息触发的），无法获取请求信息，直接返回
            return;
        }

        // 设置请求方法（GET / POST / PUT 等）
        reqDTO.setRequestMethod(request.getMethod());

        // 设置请求的 URL 路径（不包含域名和参数，例如：/api/crm/customer/transfer）
        reqDTO.setRequestUrl(request.getRequestURI());

        // 设置用户的真实 IP 地址（考虑了代理、Nginx 等情况）
        reqDTO.setUserIp(ServletUtils.getClientIP(request));

        // 设置用户的浏览器或客户端信息（User-Agent）
        reqDTO.setUserAgent(ServletUtils.getUserAgent(request));
    }

    /**
     * 查询操作日志（按业务编号和类型）
     *
     * 这个方法目前不支持，因为我们的系统统一通过 OperateLogApi 来查询日志。
     * 所以这里直接抛出异常，提示开发者不要使用这个方法。
     */
    @Override
    public List<LogRecord> queryLog(String bizNo, String type) {
        throw new UnsupportedOperationException("使用 OperateLogApi 进行操作日志的查询");
    }

    /**
     * 查询操作日志（按业务编号、类型和子类型）
     *
     * 同样，这个方法也不支持，理由同上。
     */
    @Override
    public List<LogRecord> queryLogByBizNo(String bizNo, String type, String subType) {
        throw new UnsupportedOperationException("使用 OperateLogApi 进行操作日志的查询");
    }
}