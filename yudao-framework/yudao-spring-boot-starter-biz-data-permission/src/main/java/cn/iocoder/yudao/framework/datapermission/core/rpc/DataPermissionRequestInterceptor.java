package cn.iocoder.yudao.framework.datapermission.core.rpc;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * 数据权限的 Feign 请求拦截器
 *
 * 作用说明：
 * 当使用 Feign 调用其他微服务时，这个拦截器会自动将当前线程的数据权限配置传递到被调用的服务
 *
 * 工作原理：
 * 1. 在发起 Feign 请求前，从当前线程上下文中获取数据权限配置
 * 2. 如果数据权限被禁用（enable=false），则在 HTTP 请求头中添加标记
 * 3. 被调用的服务接收到请求后，会读取这个请求头，从而保持数据权限的一致性
 *
 * 使用场景：
 * 比如在服务 A 中禁用了数据权限（使用了 @DataPermission(enable=false)），
 * 当服务 A 通过 Feign 调用服务 B 时，服务 B 也应该禁用数据权限，避免出现数据不一致的问题
 *
 * 注意：由于 {@link DataPermission} 注解不支持序列化和反序列化，所以暂时只能传递它的 enable 属性
 *
 * @author 芋道源码
 */
public class DataPermissionRequestInterceptor implements RequestInterceptor {

    /**
     * HTTP 请求头的名称，用于传递数据权限的启用状态
     * 当值为 "false" 时，表示数据权限被禁用
     */
    public static final String ENABLE_HEADER_NAME = "data-permission-enable";

    /**
     * 拦截 Feign 请求，在请求发送前添加数据权限信息到请求头
     *
     * 执行流程：
     * 1. 从线程上下文中获取当前的数据权限配置（DataPermission 注解）
     * 2. 判断数据权限是否存在且被禁用（enable=false）
     * 3. 如果被禁用，则在 HTTP 请求头中添加 "data-permission-enable: false" 标记
     * 4. 如果数据权限启用或不存在，则不添加任何请求头（默认行为是启用数据权限）
     *
     * @param requestTemplate Feign 请求模板对象，用于设置请求的各种参数（包括请求头）
     */
    @Override
    public void apply(RequestTemplate requestTemplate) {
        // 从当前线程的上下文中获取数据权限配置
        // DataPermissionContextHolder 是一个基于 ThreadLocal 的容器，存储当前线程的数据权限信息
        DataPermission dataPermission = DataPermissionContextHolder.get();

        // 检查两个条件：
        // 1. dataPermission != null：确保数据权限配置存在
        // 2. Boolean.FALSE.equals(dataPermission.enable())：确保数据权限被明确禁用
        // 注意：这里使用 Boolean.FALSE.equals() 而不是 == false，是为了避免空指针异常
        if (dataPermission != null && Boolean.FALSE.equals(dataPermission.enable())) {
            // 在请求头中添加标记，告诉被调用的服务："我这边禁用了数据权限，你也要禁用"
            requestTemplate.header(ENABLE_HEADER_NAME, "false");
        }
        // 如果数据权限启用（enable=true）或者没有配置，则不需要添加请求头
        // 因为默认情况下，被调用的服务会启用数据权限
    }

}
