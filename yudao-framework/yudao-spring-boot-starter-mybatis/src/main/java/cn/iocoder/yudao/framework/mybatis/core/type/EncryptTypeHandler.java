package cn.iocoder.yudao.framework.mybatis.core.type;

import cn.hutool.core.lang.Assert;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.extra.spring.SpringUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 这是一个 Mybatis 的 TypeHandler 实现类，用于在数据库存储和读取时自动加密和解密数据。
 * 它主要处理 String 类型的字段。
 *
 * 当你将一个 Java 对象的字段（例如，用户密码、手机号）标记为使用此 TypeHandler 时，
 * Mybatis 在将数据存入数据库前，会自动调用本类的加密方法，将原始字符串加密成密文。
 * 当从数据库中读取数据时，Mybatis 会自动调用本类的解密方法，将密文解密成原始字符串。
 *
 * 加密算法使用的是 AES (高级加密标准)，这是一种对称加密算法。
 * 加密和解密需要一个密钥（password），这个密钥需要通过 Spring Boot 的配置文件来提供。
 *
 * @author 芋道源码
 */
public class EncryptTypeHandler extends BaseTypeHandler<String> {

    /**
     * 在 Spring Boot 配置文件（如 application.yml）中配置的加密密钥的属性名。
     * 例如：
     * mybatis-plus:
     *   encryptor:
     *     password: your-secret-key
     */
    private static final String ENCRYPTOR_PROPERTY_NAME = "mybatis-plus.encryptor.password";

    /**
     * AES 加密器实例。
     * `static` 关键字表示这个实例在整个应用程序中是共享的，避免重复创建，提高性能。
     * 它被声明为 `volatile` 是为了确保在多线程环境下的可见性和有序性，
     * 防止在单例模式的双重检查锁定中出现问题。
     */
    private static volatile AES aes;

    /**
     * 当需要将 Java 对象的 String 类型字段值存入数据库时，Mybatis 会调用此方法。
     * 这个方法会在 SQL 执行前被调用，用于设置 PreparedStatement 的参数。
     *
     * @param ps PreparedStatement 对象，用于执行 SQL 语句。
     * @param i 参数在 SQL 语句中的位置（从 1 开始）。
     * @param parameter 要设置的参数值（原始的、未加密的字符串）。
     * @param jdbcType JDBC 类型。
     * @throws SQLException 如果发生 SQL 错误。
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        // 在将参数设置到 PreparedStatement 之前，先对它进行加密。
        ps.setString(i, encrypt(parameter));
    }

    /**
     * 当从数据库查询结果中获取数据时，Mybatis 会调用此方法。
     * 这个方法用于将数据库返回的加密字符串解密成原始字符串。
     *
     * @param rs 数据库查询的结果集。
     * @param columnName 结果集中要获取的列的名称。
     * @return 解密后的原始字符串。
     * @throws SQLException 如果发生 SQL 错误。
     */
    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // 从结果集中获取指定列的字符串值。
        String value = rs.getString(columnName);
        // 对获取到的值进行解密。
        return decrypt(value);
    }

    /**
     * 这是 getNullableResult 方法的重载版本，通过列的索引来获取数据。
     *
     * @param rs 数据库查询的结果集。
     * @param columnIndex 结果集中要获取的列的索引（从 1 开始）。
     * @return 解密后的原始字符串。
     * @throws SQLException 如果发生 SQL 错误。
     */
    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        // 从结果集中获取指定索引的字符串值。
        String value = rs.getString(columnIndex);
        // 对获取到的值进行解密。
        return decrypt(value);
    }

    /**
     * 这是 getNullableResult 方法的另一个重载版本，用于处理存储过程的输出参数。
     *
     * @param cs CallableStatement 对象，用于执行存储过程。
     * @param columnIndex 输出参数的索引（从 1 开始）。
     * @return 解密后的原始字符串。
     * @throws SQLException 如果发生 SQL 错误。
     */
    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        // 从 CallableStatement 中获取指定索引的字符串值。
        String value = cs.getString(columnIndex);
        // 对获取到的值进行解密。
        return decrypt(value);
    }

    /**
     * 私有辅助方法，用于解密字符串。
     *
     * @param value 从数据库中读取的加密字符串。
     * @return 解密后的原始字符串，如果输入为 null，则返回 null。
     */
    private static String decrypt(String value) {
        // 如果值是 null，则不需要解密，直接返回 null。
        if (value == null) {
            return null;
        }
        // 使用 AES 加密器解密字符串。
        return getEncryptor().decryptStr(value);
    }

    /**
     * 公开的静态辅助方法，用于加密字符串。
     * 这个方法也可以在应用程序的其他地方直接调用，以保持加密逻辑的一致性。
     *
     * @param rawValue 要加密的原始字符串。
     * @return 加密后的 Base64 编码的字符串，如果输入为 null，则返回 null。
     */
    public static String encrypt(String rawValue) {
        // 如果值是 null，则不需要加密，直接返回 null。
        if (rawValue == null) {
            return null;
        }
        // 使用 AES 加密器加密字符串，并将结果编码为 Base64 格式。
        return getEncryptor().encryptBase64(rawValue);
    }

    /**
     * 获取 AES 加密器实例。
     * 这个方法使用了双重检查锁定（Double-Checked Locking）的单例模式，确保在多线程环境下只创建一个 AES 实例。
     *
     * @return AES 加密器实例。
     */
    private static AES getEncryptor() {
        // 第一次检查：如果实例已经存在，直接返回，避免进入同步块，提高性能。
        if (aes != null) {
            return aes;
        }
        // 同步块：确保在同一时间只有一个线程可以创建实例。
        synchronized (EncryptTypeHandler.class) {
            // 第二次检查：在同步块内部再次检查实例是否存在，
            // 因为可能有多个线程通过了第一次检查，但只有一个能进入同步块。
            if (aes != null) {
                return aes;
            }
            // 从 Spring 的环境中获取加密密钥。
            String password = SpringUtil.getProperty(ENCRYPTOR_PROPERTY_NAME);
            // 断言：确保密钥不为空，如果为空，则程序会抛出异常并提示错误信息。
            Assert.notEmpty(password, "配置项({}) 不能为空", ENCRYPTOR_PROPERTY_NAME);
            // 使用获取到的密钥创建 AES 加密器实例，并赋值给静态变量 aes。
            aes = SecureUtil.aes(password.getBytes());
            return aes;
        }
    }

}
