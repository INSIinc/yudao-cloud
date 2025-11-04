package cn.iocoder.yudao.framework.mybatis.core.util;

import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;
import cn.iocoder.yudao.framework.common.util.spring.SpringUtils;
import cn.iocoder.yudao.framework.mybatis.core.enums.DbTypeEnum;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.mybatisplus.annotation.DbType;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * JDBC 工具类：提供一些与数据库连接和类型判断相关的便捷方法。
 *
 * @author 芋道源码
 */
public class JdbcUtils {

    /**
     * 测试数据库连接是否能成功建立。
     * 给定数据库的连接地址（URL）、用户名和密码，尝试连接数据库。
     * 如果连接成功，返回 true；如果失败（比如密码错误、网络不通等），返回 false。
     *
     * @param url      数据库连接地址，例如：jdbc:mysql://localhost:3306/test
     * @param username 登录数据库的用户名
     * @param password 登录数据库的密码
     * @return 连接是否成功（true 表示成功，false 表示失败）
     */
    public static boolean isConnectionOK(String url, String username, String password) {
        try (Connection ignored = DriverManager.getConnection(url, username, password)) {
            // DriverManager 是 Java 内置的数据库驱动管理器
            // 如果能成功获取 Connection（连接对象），说明连接没问题
            return true;
        } catch (Exception ex) {
            // 如果连接过程中发生任何异常（比如账号密码错误、驱动没加载等），就认为连接失败
            return false;
        }
    }

    /**
     * 根据数据库连接 URL 自动识别数据库类型（比如 MySQL、PostgreSQL、SQL Server 等）。
     * 该方法借助 MyBatis-Plus 提供的工具类来完成识别。
     *
     * @param url 数据库连接地址，例如：jdbc:mysql://... 或 jdbc:postgresql://...
     * @return 识别出的数据库类型（DbType 枚举值，如 DbType.MYSQL）
     */
    public static DbType getDbType(String url) {
        // 直接调用 MyBatis-Plus 内置的工具方法进行判断
        return com.baomidou.mybatisplus.extension.toolkit.JdbcUtils.getDbType(url);
    }

    /**
     * 获取当前项目正在使用的数据库类型（不需要传 URL，自动从 Spring 容器中获取数据源）。
     * 适用于项目已经配置好数据源的情况（比如使用了 dynamic-datasource 多数据源框架）。
     *
     * @return 当前数据库的类型（如 DbType.MYSQL、DbType.POSTGRE_SQL 等）
     */
    public static DbType getDbType() {
        DataSource dataSource;
        try {
            // 尝试获取多数据源路由对象（dynamic-datasource 框架提供的）
            DynamicRoutingDataSource dynamicRoutingDataSource = SpringUtils.getBean(DynamicRoutingDataSource.class);
            // 根据当前上下文（比如 @DS 注解）决定使用哪个具体的数据源
            dataSource = dynamicRoutingDataSource.determineDataSource();
        } catch (NoSuchBeanDefinitionException e) {
            // 如果项目没有使用 dynamic-datasource，就直接获取默认的 DataSource Bean
            dataSource = SpringUtils.getBean(DataSource.class);
        }

        try (Connection conn = dataSource.getConnection()) {
            // 通过连接获取数据库的“产品名称”（例如 "MySQL", "PostgreSQL", "Microsoft SQL Server"）
            String productName = conn.getMetaData().getDatabaseProductName();
            // 使用自定义的 DbTypeEnum 枚举进行匹配转换
            return DbTypeEnum.find(productName);
        } catch (SQLException e) {
            // 如果获取连接或元数据失败，抛出异常
            throw new IllegalArgumentException("无法获取当前数据库类型：" + e.getMessage(), e);
        }
    }

    /**
     * 判断给定的数据库连接 URL 对应的数据库是否是 SQL Server。
     *
     * @param url JDBC 连接字符串，如 jdbc:sqlserver://...
     * @return 如果是 SQL Server（包括 2005 版本），返回 true；否则返回 false
     */
    public static boolean isSQLServer(String url) {
        // 先根据 URL 识别数据库类型
        DbType dbType = getDbType(url);
        // 再判断该类型是否属于 SQL Server
        return isSQLServer(dbType);
    }

    /**
     * 判断给定的数据库类型是否是 SQL Server。
     * 支持 SQL Server 和 SQL Server 2005 两种类型（MyBatis-Plus 中它们是两个枚举值）。
     *
     * @param dbType 数据库类型枚举（来自 MyBatis-Plus 的 DbType）
     * @return 如果是 SQL Server 或 SQL Server 2005，返回 true；否则返回 false
     */
    public static boolean isSQLServer(DbType dbType) {
        // ObjectUtils.equalsAny 用于判断 dbType 是否等于后面的任意一个值
        return ObjectUtils.equalsAny(dbType, DbType.SQL_SERVER, DbType.SQL_SERVER2005);
    }

}