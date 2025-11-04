package cn.iocoder.yudao.framework.ip.core.utils;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.iocoder.yudao.framework.ip.core.Area;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;

import java.io.IOException;

/**
 * IP 工具类
 *
 * 功能说明：用于根据 IP 地址查询对应的地理位置信息（如国家、省份、城市等）
 *
 * 实现原理：
 * 1. 在类加载时，会自动读取 ip2region.xdb 数据库文件到内存中
 * 2. ip2region.xdb 是一个离线的 IP 地址库，包含了 IP 地址与地区的映射关系
 * 3. 通过 Searcher 查询器可以快速查找 IP 对应的地区编号
 * 4. 再通过地区编号获取详细的地区信息
 *
 * IP 数据源来自 ip2region.xdb 精简版，基于 <a href="https://gitee.com/zhijiantianya/ip2region"/> 项目
 *
 * @author wanglhup
 */
@Slf4j // Lombok 注解，自动生成日志对象 log，用于打印日志信息
public class IPUtils {

    /**
     * 单例实例
     *
     * 说明：这是一个饿汉式单例模式的实现
     * - 在类加载时就会创建 IPUtils 实例
     * - 创建实例时会调用私有构造方法，自动初始化 SEARCHER（IP 查询器）
     * - 这样可以确保 IP 数据库只加载一次，提高性能
     *
     * @SuppressWarnings("InstantiationOfUtilityClass") 用于抑制编译器警告
     * 因为工具类通常不应该被实例化，但这里是为了初始化 SEARCHER
     */
    @SuppressWarnings("InstantiationOfUtilityClass")
    private final static IPUtils INSTANCE = new IPUtils();

    /**
     * IP 查询器，启动加载到内存中
     *
     * 说明：
     * - Searcher 是 ip2region 库提供的查询器
     * - 将整个 IP 数据库加载到内存中，查询速度非常快（微秒级）
     * - static 修饰，所有方法共享同一个查询器实例
     */
    private static Searcher SEARCHER;

    /**
     * 私有化构造方法
     *
     * 作用：
     * 1. 防止外部直接 new IPUtils()，这是工具类的标准做法
     * 2. 在构造方法中初始化 SEARCHER，加载 IP 数据库到内存
     *
     * 执行流程：
     * 1. 记录开始时间
     * 2. 读取 classpath 下的 ip2region.xdb 文件内容到字节数组
     * 3. 使用字节数组创建 Searcher 查询器（全部加载到内存）
     * 4. 记录加载成功日志和耗时
     * 5. 如果加载失败，记录错误日志
     */
    private IPUtils() {
        try {
            // 记录开始时间，用于计算加载耗时
            long now = System.currentTimeMillis();

            // 从 classpath（类路径）读取 ip2region.xdb 文件内容
            // ResourceUtil 是 Hutool 工具库提供的资源读取工具
            byte[] bytes = ResourceUtil.readBytes("ip2region.xdb");

            // 创建 Searcher 查询器，将整个数据库加载到内存缓冲区
            // 这种方式查询最快，但会占用一定内存（约 10MB 左右）
            SEARCHER = Searcher.newWithBuffer(bytes);

            // 打印成功日志，显示加载耗时
            log.info("启动加载 IPUtils 成功，耗时 ({}) 毫秒", System.currentTimeMillis() - now);
        } catch (IOException e) {
            // 如果文件读取失败或 Searcher 创建失败，记录错误日志
            log.error("启动加载 IPUtils 失败", e);
        }
    }

    /**
     * 查询 IP 对应的地区编号（字符串格式的 IP 地址）
     *
     * 使用场景：当你有一个 IP 地址字符串（如 "192.168.1.1"），想知道它对应的地区编号
     *
     * 工作流程：
     * 1. 去除 IP 地址前后的空格
     * 2. 使用 SEARCHER 在 IP 数据库中查找该 IP
     * 3. 返回的结果是字符串格式的地区编号，转换为 Integer 返回
     *
     * @param ip IP 地址，格式为 127.0.0.1
     * @return 地区id（整数类型的地区编号）
     *
     * @SneakyThrows 注解说明：
     * - 这是 Lombok 注解，会自动将检查异常转换为运行时异常
     * - 简化了异常处理代码，但调用者要注意可能抛出运行时异常
     */
    @SneakyThrows
    public static Integer getAreaId(String ip) {
        // trim() 去除 IP 字符串前后的空格，防止格式问题
        // SEARCHER.search() 返回的是字符串格式的地区编号
        // Integer.parseInt() 将字符串转换为整数
        return Integer.parseInt(SEARCHER.search(ip.trim()));
    }

    /**
     * 查询 IP 对应的地区编号（长整型格式的 IP 地址）
     *
     * 使用场景：当 IP 地址已经转换为长整型格式（数字形式）时使用
     *
     * IP 地址的长整型表示：
     * - 例如：IP "192.168.1.1" 可以转换为一个 long 类型的数字
     * - 转换公式：192*256³ + 168*256² + 1*256¹ + 1*256⁰ = 3232235777
     * - 这种格式便于存储和比较，占用空间更小
     *
     * @param ip IP 地址的长整型表示，格式参考 {@link Searcher#checkIP(String)} 的返回
     * @return 地区编号（整数类型）
     */
    @SneakyThrows
    public static Integer getAreaId(long ip) {
        // 直接使用长整型 IP 进行查询
        // SEARCHER.search() 支持两种格式：字符串和长整型
        return Integer.parseInt(SEARCHER.search(ip));
    }

    /**
     * 查询 IP 对应的地区详细信息（字符串格式的 IP 地址）
     *
     * 使用场景：当你想获取 IP 的完整地区信息，而不仅仅是地区编号
     *
     * 工作流程：
     * 1. 先调用 getAreaId(ip) 获取地区编号
     * 2. 再调用 AreaUtils.getArea() 根据编号获取详细的地区信息
     * 3. 返回 Area 对象，包含国家、省份、城市等详细信息
     *
     * @param ip IP 地址，格式为 127.0.0.1（如 "192.168.1.1"）
     * @return 地区对象（Area），包含完整的地理位置信息
     *
     * 示例：
     * Area area = IPUtils.getArea("114.114.114.114");
     * // 可以获取到：area.getCountry()、area.getProvince()、area.getCity() 等信息
     */
    public static Area getArea(String ip) {
        // 两步查询：IP -> 地区编号 -> 地区详细信息
        return AreaUtils.getArea(getAreaId(ip));
    }

    /**
     * 查询 IP 对应的地区详细信息（长整型格式的 IP 地址）
     *
     * 使用场景：当 IP 地址是长整型格式时，获取完整的地区信息
     *
     * 工作流程：与上面的方法相同，只是入参格式不同
     * 1. 先调用 getAreaId(ip) 获取地区编号
     * 2. 再调用 AreaUtils.getArea() 根据编号获取详细的地区信息
     *
     * @param ip IP 地址的长整型表示，格式参考 {@link Searcher#checkIP(String)} 的返回
     * @return 地区对象（Area），包含完整的地理位置信息
     */
    public static Area getArea(long ip) {
        // 两步查询：IP -> 地区编号 -> 地区详细信息
        return AreaUtils.getArea(getAreaId(ip));
    }
}
