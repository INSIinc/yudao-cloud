package cn.iocoder.yudao.module.infra.framework.file.core.client.s3;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.module.infra.framework.file.core.client.AbstractFileClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URL;
import java.time.Duration;

/**
 * 基于 S3 协议的文件客户端，实现 MinIO、阿里云、腾讯云、七牛云、华为云等云服务
 * <p>
 * 什么是 S3 协议？
 * S3（Simple Storage Service）是亚马逊 AWS 提供的对象存储服务协议，现已成为业界标准。
 * 很多云服务商（如阿里云 OSS、腾讯云 COS、MinIO 等）都兼容 S3 协议，这样可以使用统一的代码操作不同的云存储。
 * <p>
 * 这个类的作用：
 * - 提供文件上传、下载、删除等基础功能
 * - 支持生成预签名 URL（让用户可以直接访问私有文件，而无需暴露访问密钥）
 * - 兼容多种云存储服务商
 *
 * @author 芋道源码
 */
public class S3FileClient extends AbstractFileClient<S3FileClientConfig> {

    /**
     * 默认的 URL 过期时间：24 小时
     * 当生成预签名 URL 时，如果没有指定过期时间，就使用这个默认值
     * 预签名 URL 可以让没有权限的用户在限定时间内访问私有文件
     */
    private static final Duration EXPIRATION_DEFAULT = Duration.ofHours(24);

    /**
     * S3 客户端对象
     * 用于执行实际的文件操作（上传、下载、删除等）
     */
    private S3Client client;

    /**
     * S3 预签名器对象
     * 用于生成预签名 URL，让用户可以临时访问私有文件
     */
    private S3Presigner presigner;

    /**
     * 构造函数：创建一个 S3 文件客户端实例
     *
     * @param id     客户端唯一标识 ID
     * @param config S3 配置信息（包含访问密钥、bucket 名称、endpoint 等）
     */
    public S3FileClient(Long id, S3FileClientConfig config) {
        super(id, config);
    }

    /**
     * 初始化 S3 客户端和预签名器
     * <p>
     * 这个方法在客户端创建后会被自动调用，用于：
     * 1. 补全 domain 访问地址
     * 2. 配置访问凭证（AccessKey 和 AccessSecret）
     * 3. 创建 S3Client 和 S3Presigner 实例
     */
    @Override
    protected void doInit() {
        // 步骤1：补全 domain 访问地址
        // 如果配置中没有指定 domain，就根据 bucket 和 endpoint 自动构建
        // domain 是文件的访问地址前缀，例如：https://my-bucket.oss-cn-hangzhou.aliyuncs.com
        if (StrUtil.isEmpty(config.getDomain())) {
            config.setDomain(buildDomain());
        }

        // 步骤2：配置区域（Region）
        // S3 协议要求必须指定区域，但对于兼容 S3 的服务（如 MinIO），填什么都可以
        // 这里统一使用 "us-east-1"，这是 AWS 的默认区域
        Region region = Region.of("us-east-1"); // 必须填，但填什么都行，常见的值有 "us-east-1"，不填会报错

        // 步骤3：配置访问凭证
        // AccessKey 和 AccessSecret 就像用户名和密码，用于验证身份
        AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.getAccessKey(), config.getAccessSecret()));

        // 步骤4：构建完整的服务端点地址
        // 例如：将 "oss-cn-hangzhou.aliyuncs.com" 补全为 "https://oss-cn-hangzhou.aliyuncs.com"
        URI endpoint = URI.create(buildEndpoint());

        // 步骤5：配置 S3 服务的特殊选项
        S3Configuration serviceConfiguration = S3Configuration.builder()
                // Path-style 访问模式：决定 URL 格式是 "endpoint/bucket/key" 还是 "bucket.endpoint/key"
                // MinIO 等服务需要启用 Path-style，而阿里云等默认使用 Virtual-hosted-style
                .pathStyleAccessEnabled(Boolean.TRUE.equals(config.getEnablePathStyleAccess()))
                // 禁用分块编码：解决某些场景下的兼容性问题
                // 如果不禁用，某些服务可能会出现上传失败的情况
                .chunkedEncodingEnabled(false) // 禁用分块编码，参见 https://t.zsxq.com/kBy57
                .build();

        // 步骤6：创建 S3Client 实例
        // 这是实际执行文件操作的客户端对象
        client = S3Client.builder()
                .credentialsProvider(credentialsProvider)  // 设置访问凭证
                .region(region)                             // 设置区域
                .endpointOverride(endpoint)                 // 设置服务端点
                .serviceConfiguration(serviceConfiguration) // 设置服务配置
                .build();

        // 步骤7：创建 S3Presigner 实例
        // 用于生成预签名 URL，让用户可以临时访问私有文件
        presigner = S3Presigner.builder()
                .credentialsProvider(credentialsProvider)  // 设置访问凭证
                .region(region)                             // 设置区域
                .endpointOverride(endpoint)                 // 设置服务端点
                .serviceConfiguration(serviceConfiguration) // 设置服务配置
                .build();
    }

    /**
     * 上传文件到 S3 存储
     *
     * @param content 文件内容的字节数组（例如图片、文档等）
     * @param path    文件存储路径（相对于 bucket 的路径，例如：2024/01/01/avatar.jpg）
     * @param type    文件的 MIME 类型（例如：image/jpeg、application/pdf）
     * @return 文件的访问 URL（可能是公开 URL 或预签名 URL）
     */
    @Override
    public String upload(byte[] content, String path, String type) {
        // 步骤1：构造上传请求对象
        // 包含文件要存储到哪个 bucket、存储路径、文件类型和大小等信息
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(config.getBucket())        // 设置存储桶名称（例如：my-files）
                .key(path)                         // 设置文件存储路径（例如：2024/01/01/avatar.jpg）
                .contentType(type)                 // 设置文件类型（例如：image/jpeg）
                .contentLength((long) content.length) // 设置文件大小（字节数）
                .build();

        // 步骤2：执行上传操作
        // 将字节数组内容上传到 S3 存储
        client.putObject(putRequest, RequestBody.fromBytes(content));

        // 步骤3：生成并返回文件的访问 URL
        // 如果是公开访问，返回普通 URL；如果是私有访问，返回预签名 URL
        return presignGetUrl(path, null);
    }

    /**
     * 删除 S3 存储中的文件
     *
     * @param path 要删除的文件路径（相对于 bucket 的路径）
     */
    @Override
    public void delete(String path) {
        // 构造删除请求对象，指定要删除哪个 bucket 中的哪个文件
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(config.getBucket())  // 指定存储桶
                .key(path)                   // 指定文件路径
                .build();

        // 执行删除操作
        client.deleteObject(deleteRequest);
    }

    /**
     * 获取文件内容（下载文件）
     *
     * @param path 文件路径（相对于 bucket 的路径）
     * @return 文件内容的字节数组
     */
    @Override
    public byte[] getContent(String path) {
        // 构造下载请求对象
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(config.getBucket())  // 指定存储桶
                .key(path)                   // 指定文件路径
                .build();

        // 从 S3 获取文件流，并使用工具类将其转换为字节数组
        return IoUtil.readBytes(client.getObject(getRequest));
    }

    /**
     * 生成预签名的上传 URL
     * <p>
     * 应用场景：客户端（如前端）可以直接使用这个 URL 上传文件到 S3，无需通过后端服务器中转
     * 好处：减轻服务器压力，加快上传速度
     *
     * @param path 文件将要存储的路径
     * @return 预签名的上传 URL，客户端可以直接 PUT 请求到这个 URL 上传文件
     */
    @Override
    public String presignPutUrl(String path) {
        // 使用预签名器生成一个临时的上传 URL
        // 这个 URL 在 24 小时内有效（EXPIRATION_DEFAULT）
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(EXPIRATION_DEFAULT)  // 设置 URL 有效期
                        .putObjectRequest(b -> b.bucket(config.getBucket()).key(path)).build())  // 指定上传目标
                .url().toString();
    }

    /**
     * 生成文件的访问 URL（支持公开和私有两种模式）
     *
     * @param url               文件的完整 URL 或相对路径
     * @param expirationSeconds URL 的过期时间（秒），null 则使用默认的 24 小时
     * @return 文件访问 URL
     * - 如果是公开访问模式：返回普通的 HTTP URL
     * - 如果是私有访问模式：返回带签名参数的预签名 URL
     */
    @Override
    public String presignGetUrl(String url, Integer expirationSeconds) {
        // 步骤1：将完整的 URL 转换为相对路径（path）
        // 例如：https://bucket.oss.com/2024/01/avatar.jpg?query=1 -> 2024/01/avatar.jpg
        String path = StrUtil.removePrefix(url, config.getDomain() + "/");  // 去掉域名前缀
        path = HttpUtils.removeUrlQuery(path);  // 去掉 URL 查询参数

        // 步骤2.1：情况一 - 公开访问模式
        // 如果配置为公开访问（或兼容老版本配置），直接返回普通的 URL，无需签名
        // 考虑到老版本的兼容，所以必须是 config.getEnablePublicAccess() 为 false 时，才进行签名
        if (!BooleanUtil.isFalse(config.getEnablePublicAccess())) {
            return config.getDomain() + "/" + path;
        }

        // 步骤2.2：情况二 - 私有访问模式
        // 生成带签名的预签名 URL，只有持有这个 URL 的人才能在有效期内访问文件
        String finalPath = path;
        // 确定过期时间：如果指定了就用指定的，否则使用默认的 24 小时
        Duration expiration = expirationSeconds != null ? Duration.ofSeconds(expirationSeconds) : EXPIRATION_DEFAULT;
        // 使用预签名器生成临时访问 URL
        URL signedUrl = presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(expiration)  // 设置 URL 有效期
                        .getObjectRequest(b -> b.bucket(config.getBucket()).key(finalPath)).build())  // 指定要访问的文件
                .url();
        return signedUrl.toString();
    }

    /**
     * 基于 bucket + endpoint 构建访问的 Domain 地址
     * <p>
     * 不同云服务商的 URL 格式不同：
     * - MinIO：http://localhost:9000/my-bucket （endpoint/bucket）
     * - 阿里云：https://my-bucket.oss-cn-hangzhou.aliyuncs.com （bucket.endpoint）
     * - 腾讯云：https://my-bucket.cos.ap-guangzhou.myqcloud.com （bucket.endpoint）
     *
     * @return Domain 地址，用于拼接完整的文件访问 URL
     */
    private String buildDomain() {
        // 情况1：如果 endpoint 已经是完整的 HTTP/HTTPS 地址（通常是 MinIO）
        // 则采用 Path-style 格式：endpoint/bucket
        // 例如：http://localhost:9000/my-bucket
        if (HttpUtil.isHttp(config.getEndpoint()) || HttpUtil.isHttps(config.getEndpoint())) {
            return StrUtil.format("{}/{}", config.getEndpoint(), config.getBucket());
        }

        // 情况2：如果 endpoint 只是域名（通常是公有云服务商）
        // 则采用 Virtual-hosted-style 格式：https://bucket.endpoint
        // 例如：https://my-bucket.oss-cn-hangzhou.aliyuncs.com
        // 注意：阿里云、腾讯云、华为云都适合这种格式。七牛云比较特殊，必须使用自定义域名
        return StrUtil.format("https://{}.{}", config.getBucket(), config.getEndpoint());
    }

    /**
     * 节点地址补全协议头
     * <p>
     * 确保 endpoint 是完整的 URL 格式（包含 http:// 或 https://）
     *
     * @return 完整的节点地址
     */
    private String buildEndpoint() {
        // 如果 endpoint 已经包含协议头（http:// 或 https://），直接返回
        if (HttpUtil.isHttp(config.getEndpoint()) || HttpUtil.isHttps(config.getEndpoint())) {
            return config.getEndpoint();
        }

        // 否则，给 endpoint 添加 https:// 协议头
        // 例如：oss-cn-hangzhou.aliyuncs.com -> https://oss-cn-hangzhou.aliyuncs.com
        return StrUtil.format("https://{}", config.getEndpoint());
    }

}
