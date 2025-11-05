package cn.iocoder.yudao.gateway.filter.grey;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.gateway.util.EnvUtils;
import com.alibaba.cloud.nacos.balancer.NacosBalancer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 灰度 {@link GrayLoadBalancer} 实现类
 *
 * 根据请求的 header[version] 匹配，筛选满足 metadata[version] 相等的服务实例列表，然后随机 + 权重进行选择一个
 * 1. 假如请求的 header[version] 为空，则不进行筛选，所有服务实例都进行选择
 * 2. 如果 metadata[version] 都不相等，则不进行筛选，所有服务实例都进行选择
 *
 * 注意，考虑到实现的简易，它的权重是使用 Nacos 的 nacos.weight，所以随机 + 权重也是基于 {@link NacosBalancer} 筛选。
 * 也就是说，如果你不使用 Nacos 作为注册中心，需要微调一下筛选的实现逻辑
 *
 * 【什么是灰度发布？】
 * 灰度发布是一种平滑过渡的发布方式，可以让新版本逐步上线，降低风险。
 * 例如：你有一个服务的新版本 v2.0，你可以先让 10% 的用户访问 v2.0，90% 的用户继续访问 v1.0。
 * 如果 v2.0 没问题，再逐步增加流量，直到所有用户都访问 v2.0。
 *
 * 【这个类的作用】
 * 这个类是一个负载均衡器（Load Balancer），负责在多个服务实例中选择一个来处理请求。
 * 它的特殊之处在于：会根据请求头中的 version 和 tag 来选择对应版本和环境的服务实例。
 *
 * @author 芋道源码
 */
@RequiredArgsConstructor  // Lombok 注解：自动生成包含所有 final 字段的构造函数
@Slf4j  // Lombok 注解：自动生成日志对象 log
public class GrayLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    // 常量：请求头中版本号的 key 名称
    private static final String VERSION = "version";

    /**
     * 用于获取 serviceId 对应的服务实例的列表
     *
     * 【什么是 ObjectProvider？】
     * ObjectProvider 是 Spring 提供的一个延迟获取 Bean 的工具类。
     * 它可以在需要的时候才去 Spring 容器中获取对象，而不是在创建这个类的时候就获取。
     *
     * 【什么是 ServiceInstanceListSupplier？】
     * 它是服务实例列表的提供者，负责从注册中心（如 Nacos）获取某个服务的所有实例列表。
     */
    private final ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

    /**
     * 需要获取的服务实例名
     *
     * 例如：如果你要调用 "user-service"，这个字段就是 "user-service"
     * 暂时用于打印 logger 日志，方便排查问题
     */
    private final String serviceId;

    /**
     * 选择一个服务实例来处理请求
     *
     * 【方法流程】
     * 1. 从请求中获取 HTTP 请求头（包含 version 和 tag 等信息）
     * 2. 获取所有可用的服务实例列表
     * 3. 根据请求头中的 version 和 tag 进行筛选
     * 4. 从筛选后的实例中，使用随机+权重算法选择一个实例
     *
     * 【为什么返回 Mono？】
     * Mono 是 Reactor 框架中的响应式类型，表示"可能会返回 0 个或 1 个结果"。
     * 因为 Gateway 是基于响应式编程的，所以这里使用 Mono 来异步处理。
     *
     * @param request 请求对象，包含请求的上下文信息
     * @return Mono<Response<ServiceInstance>> 返回选中的服务实例（异步）
     */
    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        // 获得 HttpHeaders 属性，实现从 header 中获取 version
        // RequestDataContext 包含了请求的详细信息，通过 getClientRequest().getHeaders() 可以获取请求头
        HttpHeaders headers = ((RequestDataContext) request.getContext()).getClientRequest().getHeaders();

        // 选择实例
        // 从 Spring 容器中获取 ServiceInstanceListSupplier，如果获取不到就使用空实现
        ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider.getIfAvailable(NoopServiceInstanceListSupplier::new);

        // supplier.get(request) 返回 Flux<List<ServiceInstance>>（可能多次发射服务实例列表）
        // .next() 只取第一次发射的列表
        // .map() 对列表进行处理，调用 getInstanceResponse 方法选择一个实例
        return supplier.get(request).next().map(list -> getInstanceResponse(list, headers));
    }

    /**
     * 从服务实例列表中选择一个实例
     *
     * 【选择逻辑】
     * 1. 如果没有可用实例，直接返回空响应
     * 2. 根据请求头中的 version 筛选实例（灰度发布的核心逻辑）
     * 3. 根据请求头中的 tag 筛选实例（环境隔离的核心逻辑）
     * 4. 使用随机+权重算法从筛选后的实例中选择一个
     *
     * @param instances 所有可用的服务实例列表
     * @param headers 请求头，包含 version 和 tag 等信息
     * @return Response<ServiceInstance> 返回选中的服务实例
     */
    private Response<ServiceInstance> getInstanceResponse(List<ServiceInstance> instances, HttpHeaders headers) {
        // 如果服务实例为空，则直接返回
        // 这种情况说明注册中心中没有可用的服务实例，可能是服务都挂了或者还没启动
        if (CollUtil.isEmpty(instances)) {
            log.warn("[getInstanceResponse][serviceId({}) 服务实例列表为空]", serviceId);
            return new EmptyResponse();  // 返回空响应，表示没有可用实例
        }

        // ============ 第一步：筛选满足 version 条件的实例列表 ============
        // 从请求头中获取 version 参数（例如：v1.0、v2.0）
        String version = headers.getFirst(VERSION);
        List<ServiceInstance> chooseInstances;

        if (StrUtil.isEmpty(version)) {
            // 情况1：如果请求头中没有指定 version，则使用所有实例
            // 这种情况下，不做灰度控制，所有版本的实例都可以被选中
            chooseInstances = instances;
        } else {
            // 情况2：如果请求头中指定了 version，则只选择 metadata 中 version 匹配的实例
            // 例如：请求头 version=v2.0，则只选择元数据中 version=v2.0 的服务实例
            chooseInstances = CollectionUtils.filterList(instances,
                instance -> version.equals(instance.getMetadata().get("version")));

            // 如果没有找到匹配的实例，降级处理：使用所有实例
            // 这样做是为了保证服务可用性，避免因为配置错误导致请求失败
            if (CollUtil.isEmpty(chooseInstances)) {
                log.warn("[getInstanceResponse][serviceId({}) 没有满足版本({})的服务实例列表，直接使用所有服务实例列表]", serviceId, version);
                chooseInstances = instances;
            }
        }

        // ============ 第二步：基于 tag 过滤实例列表 ============
        // tag 用于环境隔离，例如：dev、test、prod 或者个人开发环境标识
        chooseInstances = filterTagServiceInstances(chooseInstances, headers);

        // ============ 第三步：随机 + 权重获取实例 ============
        // 使用 Nacos 提供的带权重的随机算法选择一个实例
        // 权重越高的实例，被选中的概率越大（例如：性能好的机器可以设置更高的权重）
        // TODO 芋艿：目前直接使用 Nacos 提供的方法，如果替换注册中心，需要重新实现该方法
        return new DefaultResponse(NacosBalancer.getHostByRandomWeight3(chooseInstances));
    }

    /**
     * 基于 tag 请求头，过滤匹配 tag 的服务实例列表
     *
     * 【tag 的作用】
     * tag 主要用于环境隔离，常见场景：
     * 1. 多人开发时，每个人可以有自己的 tag（例如：tag=zhangsan），这样可以把请求路由到自己本地启动的服务
     * 2. 测试环境和生产环境隔离（例如：tag=test 或 tag=prod）
     * 3. 灰度发布时的特殊环境标识
     *
     * 【过滤逻辑】
     * 情况1：请求头没有 tag
     *   - 优先选择没有 tag 的服务实例（正常的生产环境实例）
     *   - 如果没有不带 tag 的实例，则使用所有实例（降级处理）
     *   - 目的：避免测试环境的请求，误打到本地开发的有 tag 的实例
     *
     * 情况2：请求头有 tag
     *   - 只选择 tag 匹配的服务实例
     *   - 如果没有匹配的实例，则使用所有实例（降级处理）
     *   - 目的：实现环境隔离，让带 tag 的请求只打到对应 tag 的服务
     *
     * copy from EnvLoadBalancerClient
     *
     * @param instances 服务实例列表
     * @param headers 请求头
     * @return 过滤后的服务实例列表
     */
    private List<ServiceInstance> filterTagServiceInstances(List<ServiceInstance> instances, HttpHeaders headers) {
        // 从请求头中获取 tag 参数
        String tag = EnvUtils.getTag(headers);

        if (StrUtil.isEmpty(tag)) {
            // 情况一：请求头中没有 tag 时，过滤掉有 tag 的节点
            // 例如：测试环境发起的普通请求，应该只打到测试环境的正常实例，不应该打到开发人员本地的实例
            List<ServiceInstance> chooseInstances = CollectionUtils.filterList(instances,
                instance -> StrUtil.isEmpty(EnvUtils.getTag(instance)));

            // 【重要】补充说明：如果希望在 chooseInstances 为空时，不允许打到有 tag 的实例，可以取消注释下面的代码
            // 当前实现：如果没有不带 tag 的实例，会降级使用所有实例（包括带 tag 的）
            // 严格模式：如果没有不带 tag 的实例，宁可失败也不使用带 tag 的实例
            if (CollUtil.isEmpty(chooseInstances)) {
                log.warn("[filterTagServiceInstances][serviceId({}) 没有不带 tag 的服务实例列表，直接使用所有服务实例列表]", serviceId);
                chooseInstances = instances;
            }
            return chooseInstances;
        }

        // 情况二：请求头中有 tag 时，使用 tag 匹配服务实例
        // 例如：请求头中 tag=zhangsan，则只选择元数据中 tag=zhangsan 的服务实例
        List<ServiceInstance> chooseInstances = CollectionUtils.filterList(instances,
            instance -> tag.equals(EnvUtils.getTag(instance)));

        // 如果没有找到匹配的实例，降级处理：使用所有实例
        // 这样做是为了保证服务可用性，避免因为配置错误导致请求失败
        if (CollUtil.isEmpty(chooseInstances)) {
            log.warn("[filterTagServiceInstances][serviceId({}) 没有满足 tag({}) 的服务实例列表，直接使用所有服务实例列表]", serviceId, tag);
            chooseInstances = instances;
        }
        return chooseInstances;
    }

}
