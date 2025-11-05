package cn.iocoder.yudao.framework.operatelog.config;

import cn.iocoder.yudao.framework.operatelog.core.service.LogRecordServiceImpl;
import com.mzt.logapi.service.ILogRecordService;
import com.mzt.logapi.starter.annotation.EnableLogRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 操作日志配置类
 *
 * 【这个类是做什么的？】
 * 这是一个 Spring Boot 的自动配置类，用于配置系统的"操作日志"功能。
 * 操作日志就是记录用户在系统中做了什么操作，比如：创建订单、修改用户信息、删除数据等。
 *
 * 【为什么需要这个配置类？】
 * 1. 启用第三方日志记录框架（mzt-log-api），通过 @EnableLogRecord 注解
 * 2. 将我们自己实现的日志服务（LogRecordServiceImpl）注册到 Spring 容器中
 * 3. 这样其他地方就可以使用 @LogRecord 注解来自动记录操作日志了
 *
 * 【适用场景】
 * - 需要审计追踪用户操作行为
 * - 需要记录业务操作的详细信息（谁、什么时间、做了什么、结果如何）
 * - 方便后续问题排查和数据恢复
 *
 * @author HUIHUI
 */
// @EnableLogRecord：启用日志记录功能（来自第三方库 mzt-log-api）
// tenant = ""：租户参数，多租户系统中可以区分不同租户的日志，这里设置为空表示不使用此功能
@EnableLogRecord(tenant = "") // 貌似用不上 tenant 这玩意给个空好啦
// @AutoConfiguration：标识这是一个自动配置类，Spring Boot 启动时会自动加载
@AutoConfiguration
// @Slf4j：Lombok 注解，自动生成 log 日志对象，方便在类中打印日志
@Slf4j
public class YudaoOperateLogConfiguration {

    /**
     * 注册操作日志服务实现类到 Spring 容器
     *
     * 【这个方法是做什么的？】
     * 创建并返回一个日志记录服务的实例（LogRecordServiceImpl），
     * Spring 会把这个实例管理起来，其他地方需要用时就可以自动注入。
     *
     * 【@Bean 注解的作用】
     * 告诉 Spring：这个方法返回的对象需要被管理，其他地方可以通过依赖注入使用它
     *
     * 【@Primary 注解的作用】
     * 当有多个相同类型（ILogRecordService）的 Bean 时，优先使用这个
     * 这样可以确保系统使用我们自己实现的日志服务，而不是框架默认的
     *
     * 【返回值说明】
     * ILogRecordService 是日志记录服务的接口（来自 mzt-log-api 框架）
     * LogRecordServiceImpl 是我们自己的实现类，负责将日志保存到数据库
     *
     * @return 操作日志服务的实现类实例
     */
    @Bean
    @Primary
    public ILogRecordService iLogRecordServiceImpl() {
        // 创建并返回我们自己实现的日志记录服务
        // 这个服务会负责接收日志信息并保存到数据库
        return new LogRecordServiceImpl();
    }

}
