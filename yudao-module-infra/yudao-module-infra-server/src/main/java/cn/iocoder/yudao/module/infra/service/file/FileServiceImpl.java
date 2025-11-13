package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileCreateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.hutool.core.date.DatePattern.PURE_DATE_PATTERN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_NOT_EXISTS;

/**
 * 文件 Service 实现类
 * <p>
 * This service handles all file-related operations including:
 * - Uploading files to storage
 * - Generating presigned URLs for file access
 * - Managing file metadata in database
 * - Deleting files from both storage and database
 *
 * @author 芋道源码
 */
@Service
public class FileServiceImpl implements FileService {

    /**
     * 上传文件的前缀，是否包含日期（yyyyMMdd）
     * <p>
     * Whether to include date prefix (yyyyMMdd format) in file upload path.
     * 目的：按照日期，进行分目录
     * Purpose: Organize files into date-based directories for better management.
     * <p>
     * Example: If enabled, file path becomes "20251112/myfile.jpg"
     */
    static boolean PATH_PREFIX_DATE_ENABLE = true;

    /**
     * 上传文件的后缀，是否包含时间戳
     * <p>
     * Whether to include timestamp suffix in file name.
     * 目的：保证文件的唯一性，避免覆盖
     * Purpose: Ensure file uniqueness and prevent overwriting existing files.
     * 定制：可按需调整成 UUID、或者其他方式
     * Customization: Can be changed to UUID or other unique identifier schemes.
     * <p>
     * Example: If enabled, "myfile.jpg" becomes "myfile_1731398400000.jpg"
     */
    static boolean PATH_SUFFIX_TIMESTAMP_ENABLE = true;

    /**
     * 文件配置服务
     * File configuration service - manages different file storage clients (local, S3, OSS, etc.)
     */
    @Resource
    private FileConfigService fileConfigService;

    /**
     * 文件数据访问层
     * File data access layer - handles database operations for file metadata
     */
    @Resource
    private FileMapper fileMapper;

    /**
     * 分页查询文件列表
     * <p>
     * Retrieves a paginated list of files based on query criteria.
     *
     * @param pageReqVO Request object containing pagination parameters (page number, size)
     *                  and optional filter criteria (file name, type, etc.)
     * @return PageResult containing the list of files and total count
     * <p>
     * Usage example: Get files on page 1 with 10 items per page
     */
    @Override
    public PageResult<FileDO> getFilePage(FilePageReqVO pageReqVO) {
        return fileMapper.selectPage(pageReqVO);
    }

    /**
     * 创建文件（上传文件）
     * <p>
     * Uploads a file to storage and saves its metadata to the database.
     * This method handles the complete file upload lifecycle.
     *
     * @param content   The file's binary content (byte array)
     * @param name      Original file name (e.g., "photo.jpg")
     * @param directory Target directory path (e.g., "avatars" or "documents")
     * @param type      MIME type (e.g., "image/jpeg", "application/pdf")
     * @return The URL where the uploaded file can be accessed
     * <p>
     * Process flow:
     * 1. Validate and normalize file type and name
     * 2. Generate unique file path with date/timestamp
     * 3. Upload to configured storage (local disk, S3, OSS, etc.)
     * 4. Save file metadata to database
     * 5. Return the accessible URL
     */
    @Override
    @SneakyThrows
    public String createFile(byte[] content, String name, String directory, String type) {
        // Step 1.1: Handle empty type - auto-detect MIME type from file content
        // 处理 type 为空的情况 - 根据文件内容自动检测 MIME 类型
        // For example: if content is a JPEG image, type becomes "image/jpeg"
        if (StrUtil.isEmpty(type)) {
            type = FileTypeUtils.getMineType(content, name);
        }

        // Step 1.2: Handle empty name - generate hash-based name from content
        // 处理 name 为空的情况 - 使用内容的 SHA256 哈希值作为文件名
        // This ensures same content always gets the same name
        if (StrUtil.isEmpty(name)) {
            name = DigestUtil.sha256Hex(content);
        }

        // Step 1.3: Add file extension if missing
        // 如果文件名没有扩展名，根据 MIME 类型添加扩展名
        // Example: "myfile" + ".jpg" = "myfile.jpg"
        if (StrUtil.isEmpty(FileUtil.extName(name))) {
            String extension = FileTypeUtils.getExtension(type);
            if (StrUtil.isNotEmpty(extension)) {
                name = name + extension;
            }
        }

        // Step 2.1: Generate unique upload path
        // 生成上传路径，需要保证唯一性
        // Example result: "20251112/avatars/myfile_1731398400000.jpg"
        String path = generateUploadPath(name, directory);

        // Step 2.2: Upload to file storage
        // 上传到文件存储器（可能是本地磁盘、阿里云 OSS、AWS S3 等）
        // Get the master (primary) file storage client
        FileClient client = fileConfigService.getMasterFileClient();
        Assert.notNull(client, "客户端(master) 不能为空");
        // Perform the actual upload and get back the access URL
        String url = client.upload(content, path, type);

        // Step 3: Save file metadata to database
        // 保存文件的元数据到数据库
        // This allows us to track and manage files later
        fileMapper.insert(new FileDO().setConfigId(client.getId())
                .setName(name).setPath(path).setUrl(url)
                .setType(type).setSize(content.length));
        return url;
    }

    /**
     * 生成上传文件的唯一路径
     * <p>
     * Generates a unique path for uploaded files with optional date prefix and timestamp suffix.
     *
     * @param name      Original file name (e.g., "photo.jpg")
     * @param directory Target directory (e.g., "avatars", can be null)
     * @return Complete file path (e.g., "avatars/20251112/photo_1731398400000.jpg")
     * <p>
     * Path composition (from left to right):
     * 1. directory (if provided): "avatars"
     * 2. date prefix (if enabled): "20251112"
     * 3. filename: "photo"
     * 4. timestamp suffix (if enabled): "1731398400000"
     * 5. extension: ".jpg"
     * <p>
     * Final result: "avatars/20251112/photo_1731398400000.jpg"
     */
    @VisibleForTesting
    String generateUploadPath(String name, String directory) {
        // Step 1: Generate prefix (date) and suffix (timestamp)
        // 生成前缀和后缀

        // Generate date prefix if enabled: "20251112"
        // 如果启用日期前缀，生成当前日期：20251112
        String prefix = null;
        if (PATH_PREFIX_DATE_ENABLE) {
            prefix = LocalDateTimeUtil.format(LocalDateTimeUtil.now(), PURE_DATE_PATTERN);
        }

        // Generate timestamp suffix if enabled: "1731398400000"
        // 如果启用时间戳后缀，生成当前时间戳：1731398400000
        String suffix = null;
        if (PATH_SUFFIX_TIMESTAMP_ENABLE) {
            suffix = String.valueOf(System.currentTimeMillis());
        }

        // Step 2.1: Add suffix to filename (before extension)
        // 先拼接后缀到文件名（在扩展名之前）
        // Example: "photo.jpg" becomes "photo_1731398400000.jpg"
        if (StrUtil.isNotEmpty(suffix)) {
            String ext = FileUtil.extName(name);  // Get extension: "jpg"
            if (StrUtil.isNotEmpty(ext)) {
                // Split: "photo" + "_" + "1731398400000" + "." + "jpg"
                name = FileUtil.mainName(name) + StrUtil.C_UNDERLINE + suffix + StrUtil.DOT + ext;
            } else {
                // No extension, just append: "photo" + "_" + "1731398400000"
                name = name + StrUtil.C_UNDERLINE + suffix;
            }
        }

        // Step 2.2: Add date prefix to path
        // 再拼接日期前缀
        // Example: "20251112" + "/" + "photo_1731398400000.jpg"
        if (StrUtil.isNotEmpty(prefix)) {
            name = prefix + StrUtil.SLASH + name;
        }

        // Step 2.3: Add directory to path
        // 最后拼接目录
        // Example: "avatars" + "/" + "20251112/photo_1731398400000.jpg"
        if (StrUtil.isNotEmpty(directory)) {
            name = directory + StrUtil.SLASH + name;
        }

        return name;
    }

    /**
     * 生成文件上传预签名 URL
     * <p>
     * Generates a presigned URL for uploading files directly to storage.
     * This allows clients to upload large files directly to storage (S3, OSS, etc.)
     * without going through the application server.
     *
     * @param name      File name to be uploaded (e.g., "large-video.mp4")
     * @param directory Target directory (e.g., "videos")
     * @return FilePresignedUrlRespVO containing:
     * - uploadUrl: URL to PUT the file to (temporary, expires after some time)
     * - url: URL to GET/access the file after upload
     * - path: The storage path where file will be saved
     * - configId: The file storage configuration ID used
     * <p>
     * Usage scenario:
     * 1. Client calls this API to get presigned URL
     * 2. Client uploads file directly to storage using uploadUrl
     * 3. Client accesses file using url
     * <p>
     * Benefits: Reduces server load, faster uploads, suitable for large files
     */
    @Override
    @SneakyThrows
    public FilePresignedUrlRespVO presignPutUrl(String name, String directory) {
        // Step 1: Generate unique upload path
        // 生成上传的路径，需要保证唯一性
        // Example: "videos/20251112/large-video_1731398400000.mp4"
        String path = generateUploadPath(name, directory);

        // Step 2: Get presigned URLs from file storage client
        // 获取文件预签名地址
        FileClient fileClient = fileConfigService.getMasterFileClient();

        // Get temporary upload URL (for PUT request)
        // 获取上传 URL（用于 PUT 请求）- 客户端用这个 URL 上传文件
        String uploadUrl = fileClient.presignPutUrl(path);

        // Get access URL (for GET request)
        // 获取访问 URL（用于 GET 请求）- 客户端用这个 URL 访问文件
        String visitUrl = fileClient.presignGetUrl(path, null);

        // Return all the information client needs
        // 返回客户端需要的所有信息
        return new FilePresignedUrlRespVO().setConfigId(fileClient.getId())
                .setPath(path).setUploadUrl(uploadUrl).setUrl(visitUrl);
    }

    /**
     * 生成文件访问预签名 URL
     * <p>
     * Generates a temporary presigned URL for accessing/downloading a file.
     * Useful for private storage where files are not publicly accessible.
     *
     * @param url               The file URL or path to generate presigned URL for
     * @param expirationSeconds How long the URL is valid (in seconds), null for default
     * @return A temporary URL that allows access to the file for the specified duration
     * <p>
     * Usage scenario:
     * - Files stored in private buckets (not publicly accessible)
     * - Need to share files temporarily with specific users
     * - Control access duration for security
     * <p>
     * Example:
     * - Input: "avatars/20251112/user_photo.jpg", 3600
     * - Output: "https://storage.example.com/avatars/20251112/user_photo.jpg?signature=xyz&expires=1731402000"
     * - The URL will be valid for 1 hour (3600 seconds)
     */
    @Override
    public String presignGetUrl(String url, Integer expirationSeconds) {
        // Get the master file storage client
        // 获取主文件存储客户端
        FileClient fileClient = fileConfigService.getMasterFileClient();

        // Generate and return the presigned URL with expiration
        // 生成并返回带有过期时间的预签名 URL
        return fileClient.presignGetUrl(url, expirationSeconds);
    }

    /**
     * 创建文件记录（不上传实际文件）
     * <p>
     * Creates a file record in database without uploading actual file content.
     * Used when file is already uploaded directly to storage (e.g., via presigned URL).
     *
     * @param createReqVO Request object containing file metadata:
     *                    - url: The file's URL in storage
     *                    - name: File name
     *                    - path: Storage path
     *                    - type: MIME type
     *                    - size: File size in bytes
     * @return The ID of the newly created file record
     * <p>
     * Usage scenario:
     * 1. Client uploads file directly to storage using presigned URL
     * 2. Client calls this API to register the file in our system
     * 3. System saves file metadata for future reference
     * <p>
     * Note: Removes query parameters from URL (e.g., signatures) before saving
     */
    @Override
    public Long createFile(FileCreateReqVO createReqVO) {
        // Remove query parameters from URL (like signature parameters for private buckets)
        // 移除 URL 中的查询参数（例如私有桶情况下的签名参数）
        // Example: "https://example.com/file.jpg?signature=xyz" -> "https://example.com/file.jpg"
        createReqVO.setUrl(HttpUtils.removeUrlQuery(createReqVO.getUrl()));

        // Convert request VO to database entity
        // 将请求对象转换为数据库实体
        FileDO file = BeanUtils.toBean(createReqVO, FileDO.class);

        // Insert file record into database
        // 插入文件记录到数据库
        fileMapper.insert(file);

        // Return the generated ID
        // 返回生成的文件 ID
        return file.getId();
    }

    /**
     * 删除文件
     * <p>
     * Deletes a file both from storage and database.
     * This is a complete deletion - the file will be removed from physical storage.
     *
     * @param id The file ID to delete
     * @throws Exception If file doesn't exist or deletion fails
     *                   <p>
     *                   Process:
     *                   1. Validate file exists in database
     *                   2. Delete physical file from storage (S3, OSS, local disk, etc.)
     *                   3. Delete file record from database
     *                   <p>
     *                   Warning: This operation cannot be undone!
     */
    @Override
    public void deleteFile(Long id) throws Exception {
        // Step 1: Validate file exists and retrieve file information
        // 校验文件存在并获取文件信息
        FileDO file = validateFileExists(id);

        // Step 2: Delete from file storage
        // 从文件存储器中删除物理文件
        // Get the appropriate storage client based on file's configuration
        FileClient client = fileConfigService.getFileClient(file.getConfigId());
        Assert.notNull(client, "客户端({}) 不能为空", file.getConfigId());
        // Delete the actual file from storage
        client.delete(file.getPath());

        // Step 3: Delete from database
        // 从数据库中删除文件记录
        fileMapper.deleteById(id);
    }

    /**
     * 批量删除文件
     * <p>
     * Deletes multiple files in batch from both storage and database.
     * This is more efficient than calling deleteFile() multiple times.
     *
     * @param ids List of file IDs to delete
     * @throws Exception If any deletion fails
     *                   <p>
     *                   Process:
     *                   1. Query all files by IDs from database
     *                   2. Loop through each file and delete from storage
     *                   3. Delete all file records from database in one batch
     *                   <p>
     *                   Note: If one file fails to delete from storage, the method will throw an exception,
     *                   but previous files may already be deleted. Consider using transaction if needed.
     */
    @Override
    @SneakyThrows
    public void deleteFileList(List<Long> ids) {
        // Step 1: Query all files from database
        // 从数据库查询所有要删除的文件信息
        List<FileDO> files = fileMapper.selectByIds(ids);

        // Step 2: Delete each file from storage
        // 逐个从文件存储器中删除物理文件
        for (FileDO file : files) {
            // Get the appropriate storage client for this file
            // 获取该文件对应的存储客户端
            FileClient client = fileConfigService.getFileClient(file.getConfigId());
            Assert.notNull(client, "客户端({}) 不能为空", file.getPath());

            // Delete the physical file from storage
            // 从存储中删除物理文件
            client.delete(file.getPath());
        }

        // Step 3: Batch delete from database
        // 批量从数据库中删除文件记录
        fileMapper.deleteByIds(ids);
    }

    /**
     * 验证文件是否存在
     * <p>
     * Validates that a file exists in the database.
     * This is a helper method used by other operations.
     *
     * @param id The file ID to validate
     * @return The file object if it exists
     * @throws ServiceException If file doesn't exist (FILE_NOT_EXISTS error)
     *                          <p>
     *                          Purpose: Prevent operations on non-existent files
     */
    private FileDO validateFileExists(Long id) {
        // Query file from database by ID
        // 根据 ID 从数据库查询文件
        FileDO fileDO = fileMapper.selectById(id);

        // If not found, throw exception with error code
        // 如果文件不存在，抛出异常
        if (fileDO == null) {
            throw exception(FILE_NOT_EXISTS);
        }

        // Return the file if it exists
        // 返回查询到的文件对象
        return fileDO;
    }

    /**
     * 获取文件内容
     * <p>
     * Retrieves the binary content of a file from storage.
     * Downloads the file and returns it as a byte array.
     *
     * @param configId The file storage configuration ID (which client to use)
     * @param path     The file path in storage (e.g., "avatars/20251112/photo.jpg")
     * @return The file's binary content as byte array
     * @throws Exception If file doesn't exist or download fails
     *                   <p>
     *                   Usage scenario:
     *                   - Need to read file content for processing
     *                   - Download file from cloud storage to server
     *                   - Generate file previews or thumbnails
     *                   <p>
     *                   Note: Be careful with large files as they are loaded into memory
     */
    @Override
    public byte[] getFileContent(Long configId, String path) throws Exception {
        // Get the appropriate file storage client by configuration ID
        // 根据配置 ID 获取文件存储客户端
        FileClient client = fileConfigService.getFileClient(configId);
        Assert.notNull(client, "客户端({}) 不能为空", configId);

        // Download and return the file content as byte array
        // 从存储中下载文件内容并返回字节数组
        return client.getContent(path);
    }

}
