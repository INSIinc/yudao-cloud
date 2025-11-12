package cn.iocoder.yudao.module.infra.framework.file.core.client;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ReflectUtil;
import cn.iocoder.yudao.module.infra.framework.file.core.enums.FileStorageEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 文件客户端的工厂实现类
 *
 * <p>这个类的作用是管理和创建不同类型的文件存储客户端（如阿里云OSS、腾讯云COS、本地存储等）</p>
 * <p>使用工厂模式，统一管理所有文件客户端的创建、更新和获取</p>
 *
 * @author 芋道源码
 */
@Slf4j  // Lombok注解，自动生成日志对象log，用于记录日志信息
public class FileClientFactoryImpl implements FileClientFactory {

    /**
     * 文件客户端缓存容器
     *
     * <p>使用ConcurrentHashMap存储所有已创建的文件客户端</p>
     * <p>key：配置编号（configId），每个文件存储配置都有唯一的ID</p>
     * <p>value：对应的文件客户端实例（AbstractFileClient）</p>
     * <p>使用ConcurrentMap是为了保证在多线程环境下的线程安全</p>
     */
    private final ConcurrentMap<Long, AbstractFileClient<?>> clients = new ConcurrentHashMap<>();

    /**
     * 根据配置ID获取对应的文件客户端
     *
     * @param configId 配置编号，用于唯一标识一个文件存储配置
     * @return 返回对应的文件客户端，如果找不到则返回null
     */
    @Override
    public FileClient getFileClient(Long configId) {
        // 从缓存Map中获取对应的客户端
        AbstractFileClient<?> client = clients.get(configId);

        // 如果客户端不存在，记录错误日志
        // 这种情况通常是配置还没有初始化或者配置ID不正确
        if (client == null) {
            log.error("[getFileClient][配置编号({}) 找不到客户端]", configId);
        }
        return client;
    }

    /**
     * 创建或更新文件客户端
     *
     * <p>这个方法会根据配置ID判断：</p>
     * <ul>
     *   <li>如果客户端不存在，则创建新的客户端并初始化</li>
     *   <li>如果客户端已存在，则使用新配置刷新客户端</li>
     * </ul>
     *
     * @param configId 配置编号
     * @param storage  存储类型（如：1=本地存储、10=阿里云OSS、11=腾讯云COS等）
     * @param config   文件存储的具体配置信息（如访问密钥、bucket名称等）
     * @param <Config> 配置类的泛型，必须继承自FileClientConfig
     */
    @Override
    @SuppressWarnings("unchecked")  // 抑制类型转换的警告
    public <Config extends FileClientConfig> void createOrUpdateFileClient(Long configId, Integer storage, Config config) {
        // 尝试从缓存中获取已存在的客户端
        AbstractFileClient<Config> client = (AbstractFileClient<Config>) clients.get(configId);

        if (client == null) {
            // 客户端不存在的情况：创建新客户端

            // 1. 调用私有方法创建客户端实例
            client = this.createFileClient(configId, storage, config);

            // 2. 初始化客户端（建立连接、验证配置等）
            client.init();

            // 3. 将新创建的客户端放入缓存Map中，方便后续使用
            clients.put(client.getId(), client);
        } else {
            // 客户端已存在的情况：刷新配置
            // 这种情况通常发生在管理员修改了文件存储配置
            client.refresh(config);
        }
    }

    /**
     * 创建文件客户端的私有方法
     *
     * <p>根据存储类型，使用反射机制动态创建对应的客户端实例</p>
     * <p>例如：如果storage=10（阿里云OSS），则创建AliyunOssFileClient实例</p>
     *
     * @param configId 配置编号
     * @param storage  存储类型编号
     * @param config   配置信息
     * @param <Config> 配置类的泛型
     * @return 返回创建好的文件客户端实例（还未初始化）
     */
    @SuppressWarnings("unchecked")  // 抑制类型转换的警告
    private <Config extends FileClientConfig> AbstractFileClient<Config> createFileClient(
            Long configId, Integer storage, Config config) {
        // 1. 根据存储类型编号获取对应的枚举对象
        //    枚举对象中包含了该存储类型对应的客户端类信息
        FileStorageEnum storageEnum = FileStorageEnum.getByStorage(storage);

        // 2. 断言验证：确保找到了对应的存储类型枚举
        //    如果storageEnum为null，说明传入的storage值不合法，会抛出异常
        Assert.notNull(storageEnum, String.format("文件配置(%s) 为空", storageEnum));

        // 3. 使用反射创建客户端实例
        //    storageEnum.getClientClass() 获取客户端类的Class对象
        //    ReflectUtil.newInstance() 通过反射调用构造函数创建实例
        //    传入参数：configId（配置ID）和 config（配置对象）
        return (AbstractFileClient<Config>) ReflectUtil.newInstance(storageEnum.getClientClass(), configId, config);
    }

}
