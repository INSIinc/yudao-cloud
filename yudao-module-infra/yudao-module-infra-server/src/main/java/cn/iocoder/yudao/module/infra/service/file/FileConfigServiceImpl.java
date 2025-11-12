package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.config.FileConfigPageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.config.FileConfigSaveReqVO;
import cn.iocoder.yudao.module.infra.convert.file.FileConfigConvert;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileConfigDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileConfigMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClientConfig;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClientFactory;
import cn.iocoder.yudao.module.infra.framework.file.core.enums.FileStorageEnum;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.cache.CacheUtils.buildAsyncReloadingCache;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_DELETE_FAIL_MASTER;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_NOT_EXISTS;

/**
 * 文件配置 Service 实现类
 * <p>
 * 这个类负责管理文件存储配置，例如：阿里云OSS、腾讯云COS、本地存储等
 * 主要功能：
 * 1. 创建、更新、删除文件存储配置
 * 2. 管理主配置（系统默认使用的文件存储）
 * 3. 缓存文件客户端，提高访问性能
 * 4. 测试文件配置是否可用
 *
 * @author 芋道源码
 */
@Service  // 标记为Spring服务类，由Spring容器管理
@Validated  // 启用方法参数校验
@Slf4j  // 自动生成日志对象log，用于记录日志
public class FileConfigServiceImpl implements FileConfigService {

    /**
     * 主配置的缓存ID
     * 使用固定值0L作为主配置的标识，方便在缓存中查找主配置
     */
    private static final Long CACHE_MASTER_ID = 0L;
    /**
     * 文件客户端工厂
     * 负责创建和管理各种类型的文件存储客户端（阿里云、腾讯云、本地等）
     */
    @Resource
    private FileClientFactory fileClientFactory;
    /**
     * 文件配置的数据库操作对象
     * 用于对文件配置表进行增删改查操作
     */
    @Resource
    private FileConfigMapper fileConfigMapper;
    /**
     * 文件客户端缓存
     * <p>
     * 作用：避免每次操作文件都要重新创建客户端，提高性能
     * 特点：
     * 1. 每10秒自动刷新一次缓存
     * 2. 当缓存中没有数据时，会自动调用load方法加载
     * 3. 键是配置ID，值是对应的FileClient客户端对象
     */
    @Getter
    private final LoadingCache<Long, FileClient> clientCache = buildAsyncReloadingCache(Duration.ofSeconds(10L),
            new CacheLoader<Long, FileClient>() {

                /**
                 * 加载文件客户端的方法
                 * 当缓存中没有对应ID的客户端时，会自动调用此方法
                 *
                 * @param id 配置ID，如果是CACHE_MASTER_ID(0L)则加载主配置
                 * @return 文件客户端对象
                 */
                @Override
                public FileClient load(Long id) {
                    // 判断是加载主配置还是普通配置
                    // 如果id等于0L，则查询主配置；否则根据id查询
                    FileConfigDO config = Objects.equals(CACHE_MASTER_ID, id) ?
                            fileConfigMapper.selectByMaster() : fileConfigMapper.selectById(id);

                    // 如果配置存在，则创建或更新文件客户端
                    if (config != null) {
                        fileClientFactory.createOrUpdateFileClient(config.getId(), config.getStorage(), config.getConfig());
                    }

                    // 从工厂中获取文件客户端并返回
                    // 如果config为null，使用传入的id；否则使用config的id
                    return fileClientFactory.getFileClient(null == config ? id : config.getId());
                }

            });
    /**
     * JSR-303 参数校验器
     * 用于校验配置参数是否符合要求（如必填项、格式等）
     */
    @Resource
    private Validator validator;

    /**
     * 创建文件配置
     * <p>
     * 使用场景：当管理员想要添加一个新的文件存储方式时调用
     * 例如：添加一个阿里云OSS配置或者腾讯云COS配置
     *
     * @param createReqVO 创建请求参数，包含存储类型、配置信息等
     * @return 新创建的配置ID
     */
    @Override
    public Long createFileConfig(FileConfigSaveReqVO createReqVO) {
        // 步骤1：转换请求参数为数据库对象
        // 步骤2：解析并校验客户端配置（如accessKey、secretKey等）
        // 步骤3：设置为非主配置（新建的配置默认不是主配置）
        FileConfigDO fileConfig = FileConfigConvert.INSTANCE.convert(createReqVO)
                .setConfig(parseClientConfig(createReqVO.getStorage(), createReqVO.getConfig()))
                .setMaster(false); // 默认非 master

        // 步骤4：将配置保存到数据库
        fileConfigMapper.insert(fileConfig);

        // 步骤5：返回新创建的配置ID
        return fileConfig.getId();
    }

    /**
     * 更新文件配置
     * <p>
     * 使用场景：当管理员需要修改现有的文件存储配置时调用
     * 例如：修改阿里云OSS的accessKey或者修改存储桶名称
     *
     * @param updateReqVO 更新请求参数，包含配置ID和新的配置信息
     */
    @Override
    public void updateFileConfig(FileConfigSaveReqVO updateReqVO) {
        // 步骤1：校验配置是否存在，如果不存在会抛出异常
        FileConfigDO config = validateFileConfigExists(updateReqVO.getId());

        // 步骤2：转换请求参数并解析客户端配置
        // 注意：这里使用的是config.getStorage()而不是updateReqVO.getStorage()
        // 因为存储类型一般不允许修改（阿里云不能改成腾讯云）
        FileConfigDO updateObj = FileConfigConvert.INSTANCE.convert(updateReqVO)
                .setConfig(parseClientConfig(config.getStorage(), updateReqVO.getConfig()));

        // 步骤3：更新数据库中的配置
        fileConfigMapper.updateById(updateObj);

        // 步骤4：清空缓存，下次使用时会重新加载新配置
        clearCache(config.getId(), null);
    }

    /**
     * 更新主配置（设置某个配置为默认使用的文件存储）
     * <p>
     * 使用场景：当管理员想切换系统默认的文件存储方式时调用
     * 例如：从本地存储切换到阿里云OSS
     * <p>
     * 注意：@Transactional表示这个方法在事务中执行，
     * 如果出现异常会自动回滚，保证数据一致性
     *
     * @param id 要设置为主配置的配置ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFileConfigMaster(Long id) {
        // 步骤1：校验配置是否存在
        validateFileConfigExists(id);

        // 步骤2：将所有配置的master字段设置为false
        // 因为只能有一个主配置，所以要先把其他的都取消
        fileConfigMapper.updateBatch(new FileConfigDO().setMaster(false));

        // 步骤3：将指定的配置设置为主配置（master=true）
        fileConfigMapper.updateById(new FileConfigDO().setId(id).setMaster(true));

        // 步骤4：清空主配置的缓存，下次获取主配置时会使用新的
        clearCache(null, true);
    }

    /**
     * 解析并校验客户端配置
     * <p>
     * 使用场景：将前端传来的配置Map转换为对应的配置对象，并进行校验
     * 例如：将阿里云OSS的配置转换为AliyunOssClientConfig对象
     *
     * @param storage 存储类型（例如：10=阿里云OSS, 20=腾讯云COS）
     * @param config  配置信息的Map（包含accessKey、secretKey等）
     * @return 解析后的配置对象
     */
    private FileClientConfig parseClientConfig(Integer storage, Map<String, Object> config) {
        // 步骤1：根据存储类型获取对应的配置类
        // 例如：阿里云OSS对应AliyunOssClientConfig.class
        Class<? extends FileClientConfig> configClass = FileStorageEnum.getByStorage(storage)
                .getConfigClass();

        // 步骤2：将Map转换为JSON字符串，再解析为对应的配置对象
        // 这是一种常见的对象转换方式
        FileClientConfig clientConfig = JsonUtils.parseObject2(JsonUtils.toJsonString(config), configClass);

        // 步骤3：校验配置参数是否符合要求
        // 例如：检查必填字段是否为空、格式是否正确等
        ValidationUtils.validate(validator, clientConfig);

        // 步骤4：返回校验通过的配置对象
        return clientConfig;
    }

    /**
     * 删除文件配置
     * <p>
     * 使用场景：当管理员不再需要某个文件存储配置时调用
     * 注意：主配置不能被删除，以保证系统始终有一个可用的文件存储
     *
     * @param id 要删除的配置ID
     */
    @Override
    public void deleteFileConfig(Long id) {
        // 步骤1：校验配置是否存在
        FileConfigDO config = validateFileConfigExists(id);

        // 步骤2：检查是否为主配置
        // 如果是主配置，抛出异常，不允许删除
        if (Boolean.TRUE.equals(config.getMaster())) {
            throw exception(FILE_CONFIG_DELETE_FAIL_MASTER);
        }

        // 步骤3：从数据库中删除配置
        fileConfigMapper.deleteById(id);

        // 步骤4：清空该配置的缓存
        clearCache(id, null);
    }

    /**
     * 批量删除文件配置
     * <p>
     * 使用场景：当管理员想要一次性删除多个文件存储配置时调用
     * 注意：主配置不能被删除，如果列表中包含主配置会抛出异常
     *
     * @param ids 要删除的配置ID列表
     */
    @Override
    public void deleteFileConfigList(List<Long> ids) {
        // 步骤1：根据ID列表查询所有配置
        List<FileConfigDO> configs = fileConfigMapper.selectByIds(ids);

        // 步骤2：检查是否包含主配置
        // 遍历每个配置，如果发现主配置则抛出异常
        for (FileConfigDO config : configs) {
            if (Boolean.TRUE.equals(config.getMaster())) {
                throw exception(FILE_CONFIG_DELETE_FAIL_MASTER);
            }
        }

        // 步骤3：批量删除配置
        fileConfigMapper.deleteByIds(ids);

        // 步骤4：清空所有被删除配置的缓存
        // forEach：对每个id执行清空缓存的操作
        ids.forEach(id -> clearCache(id, null));
    }

    /**
     * 清空指定文件配置的缓存
     * <p>
     * 使用场景：当配置被修改或删除后，需要清空缓存以便下次使用时加载最新配置
     *
     * @param id     配置编号，如果不为null则清空该配置的缓存
     * @param master 是否主配置，如果为true则清空主配置的缓存
     */
    private void clearCache(Long id, Boolean master) {
        // 如果指定了配置ID，则从缓存中移除该配置
        if (id != null) {
            clientCache.invalidate(id);
        }

        // 如果master为true，则清空主配置的缓存
        // 主配置使用固定ID CACHE_MASTER_ID (0L)
        if (Boolean.TRUE.equals(master)) {
            clientCache.invalidate(CACHE_MASTER_ID);
        }
    }

    /**
     * 校验文件配置是否存在
     * <p>
     * 使用场景：在执行修改、删除等操作前，先检查配置是否存在
     * 这是一种防御性编程的做法，避免操作不存在的数据
     *
     * @param id 配置ID
     * @return 文件配置对象
     * @throws 如果配置不存在，会抛出FILE_CONFIG_NOT_EXISTS异常
     */
    private FileConfigDO validateFileConfigExists(Long id) {
        // 根据ID从数据库查询配置
        FileConfigDO config = fileConfigMapper.selectById(id);

        // 如果查询结果为null，说明配置不存在，抛出异常
        if (config == null) {
            throw exception(FILE_CONFIG_NOT_EXISTS);
        }

        // 返回查询到的配置对象
        return config;
    }

    /**
     * 获取文件配置详情
     * <p>
     * 使用场景：当需要查看某个文件存储配置的详细信息时调用
     * 例如：在配置列表页面点击某个配置查看详情
     *
     * @param id 配置ID
     * @return 文件配置对象，如果不存在返回null
     */
    @Override
    public FileConfigDO getFileConfig(Long id) {
        return fileConfigMapper.selectById(id);
    }

    /**
     * 获取文件配置分页列表
     * <p>
     * 使用场景：在管理后台显示文件存储配置列表时调用
     * 例如：显示所有阿里云OSS、腾讯云COS等配置的列表，支持分页和搜索
     *
     * @param pageReqVO 分页查询参数（包含页码、每页条数、搜索条件等）
     * @return 分页结果（包含总数和当前页的配置列表）
     */
    @Override
    public PageResult<FileConfigDO> getFileConfigPage(FileConfigPageReqVO pageReqVO) {
        return fileConfigMapper.selectPage(pageReqVO);
    }

    /**
     * 测试文件配置是否可用
     * <p>
     * 使用场景：当管理员添加或修改配置后，可以测试配置是否正确
     * 例如：测试阿里云OSS的accessKey和secretKey是否有效
     * <p>
     * 工作原理：
     * 1. 读取一个测试图片（二维码）
     * 2. 尝试使用该配置上传这个图片
     * 3. 如果上传成功，说明配置正确；否则会抛出异常
     *
     * @param id 配置ID
     * @return 上传成功后返回文件的访问URL
     * @throws Exception 如果配置错误或上传失败会抛出异常
     */
    @Override
    public String testFileConfig(Long id) throws Exception {
        // 步骤1：校验配置是否存在
        validateFileConfigExists(id);

        // 步骤2：读取测试文件（一张二维码图片）
        byte[] content = ResourceUtil.readBytes("file/erweima.jpg");

        // 步骤3：使用该配置的文件客户端上传测试文件
        // IdUtil.fastSimpleUUID()：生成一个随机的文件名
        // "image/jpeg"：指定文件的MIME类型
        return getFileClient(id).upload(content, IdUtil.fastSimpleUUID() + ".jpg", "image/jpeg");
    }

    /**
     * 获取指定ID的文件客户端
     * <p>
     * 使用场景：根据配置ID获取对应的文件客户端，用于上传、下载文件
     * <p>
     * 工作原理：
     * 1. 首先从缓存中查找
     * 2. 如果缓存中没有，会自动调用load方法加载
     * 3. getUnchecked()：获取缓存值，如果加载失败会抛出异常
     *
     * @param id 配置ID
     * @return 文件客户端对象
     */
    @Override
    public FileClient getFileClient(Long id) {
        return clientCache.getUnchecked(id);
    }

    /**
     * 获取主（默认）文件客户端
     * <p>
     * 使用场景：当系统需要上传文件但没有指定使用哪个配置时，使用主配置
     * 例如：用户上传头像时，系统自动使用默认的文件存储方式
     * <p>
     * 工作原理：
     * 使用固定ID CACHE_MASTER_ID (0L) 从缓存中获取主配置的客户端
     *
     * @return 主配置的文件客户端对象
     */
    @Override
    public FileClient getMasterFileClient() {
        return clientCache.getUnchecked(CACHE_MASTER_ID);
    }

}
