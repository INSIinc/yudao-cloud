package cn.iocoder.yudao.framework.common.util.date;

import cn.hutool.core.date.LocalDateTimeUtil;

import java.time.*;
import java.util.Calendar;
import java.util.Date;

/**
 * 时间工具类，提供 LocalDateTime 与 Date 互转、时间构建、比较、判断等常用功能。
 * <p>
 * 注意：
 * <ul>
 *   <li>本工具类默认使用系统默认时区（通常为 GMT+8，即中国标准时间）进行转换。</li>
 *   <li>在跨时区部署或高精度时间处理场景下，建议显式指定时区，避免依赖系统默认时区。</li>
 * </ul>
 *
 * @author 芋道源码
 */
public class DateUtils {

    /**
     * 默认时区标识符（字符串形式），值为 "GMT+8"。
     * 注意：此字段仅作常量标识使用，实际转换中使用的是 {@link ZoneId#systemDefault()}，
     * 因此若系统时区非 GMT+8，行为可能与此常量不一致。
     */
    public static final String TIME_ZONE_DEFAULT = "GMT+8";

    /**
     * 1 秒对应的毫秒数，即 1000。
     */
    public static final long SECOND_MILLIS = 1000;

    /**
     * 日期格式：年-月-日，例如 "2025-11-03"。
     */
    public static final String FORMAT_YEAR_MONTH_DAY = "yyyy-MM-dd";

    /**
     * 日期时间格式：年-月-日 时:分:秒，例如 "2025-11-03 14:30:00"。
     */
    public static final String FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND = "yyyy-MM-dd HH:mm:ss";

    /**
     * 将 {@link LocalDateTime} 转换为 {@link Date}。
     * <p>
     * 转换逻辑：
     * <ol>
     *   <li>将 LocalDateTime 与系统默认时区（如 Asia/Shanghai）结合，得到 ZonedDateTime；</li>
     *   <li>再转换为 UTC 时间戳（Instant）；</li>
     *   <li>最后通过 {@link Date#from(Instant)} 构造 Date 对象。</li>
     * </ol>
     * <p>
     * 注意：此方法依赖系统默认时区。若需指定时区，请勿使用本方法。
     *
     * @param date 待转换的 LocalDateTime，可为 null
     * @return 转换后的 Date 对象；若输入为 null，则返回 null
     */
    public static Date of(LocalDateTime date) {
        if (date == null) {
            return null;
        }
        ZonedDateTime zonedDateTime = date.atZone(ZoneId.systemDefault());
        Instant instant = zonedDateTime.toInstant();
        return Date.from(instant);
    }

    /**
     * 将 {@link Date} 转换为 {@link LocalDateTime}。
     * <p>
     * 转换逻辑：
     * <ol>
     *   <li>将 Date 转为 UTC 时间戳（Instant）；</li>
     *   <li>使用系统默认时区将 Instant 转换为 LocalDateTime。</li>
     * </ol>
     * <p>
     * 注意：此方法同样依赖系统默认时区。确保系统时区为期望时区（如 GMT+8）。
     *
     * @param date 待转换的 Date，可为 null
     * @return 转换后的 LocalDateTime；若输入为 null，则返回 null
     */
    public static LocalDateTime of(Date date) {
        if (date == null) {
            return null;
        }
        Instant instant = date.toInstant();
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    /**
     * 获取当前时间加上指定持续时间后的 Date 对象。
     * <p>
     * 例如：addTime(Duration.ofHours(2)) 表示当前时间加 2 小时。
     *
     * @param duration 要增加的时间长度，不能为空
     * @return 新的 Date 对象，表示当前时间 + duration
     */
    public static Date addTime(Duration duration) {
        return new Date(System.currentTimeMillis() + duration.toMillis());
    }

    /**
     * 判断指定时间是否已过期（即是否早于当前时间）。
     *
     * @param time 待判断的时间点，可为 null
     * @return 若 time 为 null 或早于当前时间，返回 true；否则返回 false
     */
    public static boolean isExpired(LocalDateTime time) {
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(time);
    }

    /**
     * 构建指定年月日的时间（时分秒毫秒均为 0）。
     * <p>
     * 示例：buildTime(2025, 11, 3) → 2025-11-03 00:00:00.000
     *
     * @param year  年，如 2025
     * @param month 月，范围 1-12（注意：不是 0-11）
     * @param day   日，范围 1-31
     * @return 对应的 Date 对象，时间为当天 00:00:00.000
     */
    public static Date buildTime(int year, int month, int day) {
        return buildTime(year, month, day, 0, 0, 0);
    }

    /**
     * 构建指定年月日时分秒的时间（毫秒部分强制设为 0）。
     * <p>
     * 注意：Calendar 的月份从 0 开始，因此需将传入的 month 减 1。
     *
     * @param year   年
     * @param month  月（1-12）
     * @param day    日
     * @param hour   小时（0-23）
     * @param minute 分钟（0-59）
     * @param second 秒（0-59）
     * @return 对应的 Date 对象，毫秒部分为 0
     */
    public static Date buildTime(int year, int month, int day,
                                 int hour, int minute, int second) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1); // Calendar 月份从 0 开始
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, second);
        calendar.set(Calendar.MILLISECOND, 0); // 一般场景下毫秒设为 0，避免精度干扰
        return calendar.getTime();
    }

    /**
     * 返回两个 Date 中较晚（较大）的一个。
     * <p>
     * null 值处理规则：若其中一个为 null，则返回另一个；若都为 null，则返回 null。
     *
     * @param a 第一个时间
     * @param b 第二个时间
     * @return 较晚的时间；若均为空则返回 null
     */
    public static Date max(Date a, Date b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) > 0 ? a : b;
    }

    /**
     * 返回两个 LocalDateTime 中较晚（较大）的一个。
     * <p>
     * null 值处理规则：若其中一个为 null，则返回另一个；若都为 null，则返回 null。
     *
     * @param a 第一个时间
     * @param b 第二个时间
     * @return 较晚的时间；若均为空则返回 null
     */
    public static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }

    /**
     * 判断给定的 LocalDateTime 是否是今天。
     * <p>
     * 使用 Hutool 的 {@link LocalDateTimeUtil#isSameDay} 进行比较，基于系统默认时区。
     *
     * @param date 要判断的日期时间，可为 null
     * @return 若 date 与当前日期（忽略时分秒）相同，则返回 true；否则返回 false
     */
    public static boolean isToday(LocalDateTime date) {
        return LocalDateTimeUtil.isSameDay(date, LocalDateTime.now());
    }

    /**
     * 判断给定的 LocalDateTime 是否是昨天。
     * <p>
     * 通过将当前时间减去 1 天后，与给定日期比较是否为同一天。
     *
     * @param date 要判断的日期时间，可为 null
     * @return 若 date 是昨天（相对于当前系统时间），则返回 true；否则返回 false
     */
    public static boolean isYesterday(LocalDateTime date) {
        return LocalDateTimeUtil.isSameDay(date, LocalDateTime.now().minusDays(1));
    }

}