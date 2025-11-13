package cn.iocoder.yudao.module.infra.framework.file.core.client;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件客户端的抽象类，提供模板方法，减少子类的冗余代码
 * <p>
 * 设计模式说明：
 * 1. 这是一个【抽象模板类】，使用了模板方法设计模式
 * 2. 它定义了文件操作的通用流程和公共方法
 * 3. 子类（如本地文件、阿里云OSS、腾讯云COS等）只需实现特定的方法即可
 * <p>
 * 作用：
 * - 避免每个文件存储实现都写重复的初始化、配置刷新等代码
 * - 统一管理文件客户端的ID和配置信息
 * - 提供统一的URL格式化方法
 *
 * @author 芋道源码
 */
@Slf4j // Lombok注解，自动生成日志对象log，用于记录日志
public abstract class AbstractFileClient<Config extends FileClientConfig> implements FileClient {

    /**
     * 配置编号
     * 说明：每个文件存储配置都有一个唯一的ID，用于区分不同的存储配置
     * 例如：ID为1可能是阿里云OSS配置，ID为2可能是本地文件存储配置
     * final修饰：表示一旦赋值后不能再修改，保证配置ID的唯一性和稳定性
     */
    private final Long id;

    /**
     * 文件配置
     * 说明：存储具体的配置信息，不同的存储方式有不同的配置
     * 例如：OSS需要accessKey、secretKey、bucket等
     * 本地存储需要basePath、domain等
     * protected修饰：允许子类直接访问配置信息
     */
    protected Config config;

    /**
     * 构造方法
     * 说明：创建文件客户端时，必须传入配置ID和配置对象
     *
     * @param id     配置编号，用于唯一标识这个文件客户端
     * @param config 文件配置对象，包含存储所需的各种配置信息
     */
    public AbstractFileClient(Long id, Config config) {
        this.id = id;
        this.config = config;
    }

    /**
     * 初始化方法
     * 说明：这是一个【模板方法】，定义了初始化的标准流程
     * final修饰：子类不能重写这个方法，保证初始化流程的统一性
     * <p>
     * 执行流程：
     * 1. 先调用子类实现的doInit()方法，进行具体的初始化操作
     * 2. 然后记录初始化完成的日志
     * <p>
     * 使用场景：
     * - 创建文件客户端后需要调用此方法完成初始化
     * - 配置更新后也会调用此方法重新初始化
     */
    public final void init() {
        doInit(); // 调用子类实现的具体初始化逻辑
        log.debug("[init][配置({}) 初始化完成]", config);
    }

    /**
     * 自定义初始化（抽象方法，子类必须实现）
     * 说明：这是留给子类实现的【钩子方法】
     * <p>
     * 子类需要在这个方法中实现：
     * - 本地文件存储：创建存储目录
     * - 阿里云OSS：创建OSS客户端连接
     * - FTP存储：建立FTP连接
     * 等等...
     * <p>
     * 为什么是抽象方法：
     * 因为不同的存储方式初始化逻辑完全不同，所以必须由子类自己实现
     */
    protected abstract void doInit();

    /**
     * 刷新配置
     * 说明：当文件存储的配置发生变化时，调用此方法更新配置并重新初始化
     * final修饰：子类不能重写，保证刷新流程的统一性
     * <p>
     * 执行流程：
     * 1. 先比较新配置和旧配置是否相同
     * 2. 如果相同则不做任何操作（避免无意义的重新初始化）
     * 3. 如果不同，则记录日志、更新配置、重新初始化
     * <p>
     * 使用场景：
     * - 管理员在后台修改了存储配置（如修改了OSS的bucket名称）
     * - 系统检测到配置变化后，会调用此方法让客户端使用新配置
     *
     * @param config 新的配置对象
     */
    public final void refresh(Config config) {
        // 判断是否更新：使用equals方法比较新旧配置是否相同
        if (config.equals(this.config)) {
            return; // 配置没变化，直接返回，不需要重新初始化
        }
        // 配置发生了变化，记录日志
        log.info("[refresh][配置({})发生变化，重新初始化]", config);
        this.config = config; // 更新配置
        // 重新初始化：使用新配置重新建立连接或重新设置参数
        this.init();
    }

    /**
     * 获取配置编号
     * 说明：实现FileClient接口的方法，返回这个文件客户端的唯一ID
     *
     * @return 配置编号
     */
    @Override
    public Long getId() {
        return id;
    }

    /**
     * 格式化文件的 URL 访问地址
     * 说明：将文件路径转换为可以通过HTTP访问的完整URL地址
     * <p>
     * 使用场景：
     * 1. 本地文件存储：文件存在服务器磁盘上，需要通过后端接口来访问
     * 2. FTP存储：文件存在FTP服务器上，也需要通过后端接口代理访问
     * 3. 数据库存储：文件内容存在数据库中，需要通过后端接口读取
     * <p>
     * 注意：
     * - 阿里云OSS、腾讯云COS等云存储通常有自己的访问域名，不需要用这个方法
     * - 这个方法主要用于需要通过本系统后端接口来获取文件的场景
     * <p>
     * URL格式示例：
     * https://www.example.com/admin-api/infra/file/1/get/2024/01/15/abc.jpg
     * 其中：
     * - www.example.com 是自定义域名
     * - 1 是配置ID
     * - 2024/01/15/abc.jpg 是文件路径
     *
     * @param domain 自定义域名，例如：https://www.example.com
     * @param path   文件路径，例如：2024/01/15/abc.jpg
     * @return URL 访问地址，例如：https://www.example.com/admin-api/infra/file/1/get/2024/01/15/abc.jpg
     */
    protected String formatFileUrl(String domain, String path) {
        // 使用Hutool工具类的format方法拼接URL
        // {} 是占位符，会被后面的参数依次替换
        return StrUtil.format("{}/admin-api/infra/file/{}/get/{}", domain, getId(), path);
    }

}
