package cn.iocoder.yudao.framework.xss.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
import java.util.List;

/**
 * XSS 防护配置属性类
 *
 * <p>XSS（Cross-Site Scripting，跨站脚本攻击）是一种常见的网络安全漏洞。
 * 攻击者通过在网页中注入恶意脚本代码，当其他用户浏览该网页时，恶意代码会被执行。
 *
 * <p>本配置类用于控制系统的 XSS 防护功能，包括：
 * <ul>
 *   <li>是否启用 XSS 过滤功能</li>
 *   <li>哪些 URL 路径不需要进行 XSS 过滤</li>
 * </ul>
 *
 * <p>使用方式：在 application.yml 配置文件中配置以下属性：
 * <pre>
 * yudao:
 *   xss:
 *     enable: true                    # 是否开启 XSS 防护
 *     exclude-urls:                   # 不需要 XSS 过滤的 URL 列表
 *       - /admin/infra/file/upload    # 示例：文件上传接口
 *       - /admin/system/notice/*      # 示例：通知相关接口（支持通配符）
 * </pre>
 *
 * @author 芋道源码
 */
@ConfigurationProperties(prefix = "yudao.xss")
@Validated
@Data
public class XssProperties {

    /**
     * 是否开启 XSS 防护功能
     *
     * <p>默认值：true（开启）
     *
     * <p>说明：
     * <ul>
     *   <li>true：系统会自动过滤请求参数中的 XSS 攻击脚本，如 {@code <script>alert('xss')</script>}</li>
     *   <li>false：关闭 XSS 防护，不推荐在生产环境中关闭</li>
     * </ul>
     *
     * <p>注意：即使开启了 XSS 防护，也要注意前端页面的输出转义，实现多层防护
     */
    private boolean enable = true;

    /**
     * 需要排除 XSS 过滤的 URL 列表
     *
     * <p>默认值：空列表（所有接口都会进行 XSS 过滤）
     *
     * <p>使用场景：
     * <ul>
     *   <li>富文本编辑器接口：需要保存 HTML 标签，不能被过滤</li>
     *   <li>文件上传接口：文件内容不需要进行 XSS 过滤</li>
     *   <li>特殊业务接口：某些业务场景需要接收特殊字符或脚本</li>
     * </ul>
     *
     * <p>配置示例：
     * <pre>
     * excludeUrls:
     *   - /admin/infra/file/upload          # 精确匹配：只排除该路径
     *   - /admin/system/notice/*            # 通配符：排除该路径下的所有子路径
     *   - /admin/&#42;&#42;/editor          # 双星号：排除所有包含 editor 的路径
     * </pre>
     *
     * <p>注意：排除的 URL 不会进行 XSS 过滤，需要在业务代码中自行处理安全问题
     */
    private List<String> excludeUrls = Collections.emptyList();

}
