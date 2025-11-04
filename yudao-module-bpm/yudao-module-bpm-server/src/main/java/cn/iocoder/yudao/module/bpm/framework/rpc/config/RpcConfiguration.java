package cn.iocoder.yudao.module.bpm.framework.rpc.config;

import cn.iocoder.yudao.module.bpm.api.event.CrmContractStatusListener;
import cn.iocoder.yudao.module.bpm.api.event.CrmReceivableStatusListener;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.sms.SmsSendApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * BPM 模块的 RPC 配置类
 *
 * 作用说明：
 * 1. 配置 Feign 客户端，用于调用其他微服务的接口（远程过程调用）
 * 2. 注册事件监听器，用于监听 CRM 模块的业务状态变化
 *
 * 什么是 RPC？
 * RPC（Remote Procedure Call，远程过程调用）是一种通信协议，允许程序调用另一个地址空间
 * （通常是另一个服务器）的过程或函数，就像调用本地方法一样简单。
 *
 * 为什么需要这个配置类？
 * 在微服务架构中，不同的功能模块被拆分成独立的服务，服务之间需要相互调用。
 * 这个配置类就是为了让 BPM（业务流程管理）模块能够调用其他模块（如系统模块）的服务。
 */
@Configuration(value = "bpmRpcConfiguration", proxyBeanMethods = false)
// @Configuration：标记这是一个配置类，Spring 会在启动时加载这个类
// value = "bpmRpcConfiguration"：给这个配置类指定一个名称，避免与其他模块的配置类冲突
// proxyBeanMethods = false：性能优化选项，表示不需要代理 @Bean 方法，提高启动速度

@EnableFeignClients(clients = {RoleApi.class, DeptApi.class, PostApi.class, AdminUserApi.class, SmsSendApi.class, DictDataApi.class,
        PermissionApi.class})
// @EnableFeignClients：启用 Feign 客户端功能，Feign 是一个声明式的 HTTP 客户端，简化远程调用
// clients：指定需要扫描的 Feign 客户端接口列表
//   - RoleApi：角色管理接口
//   - DeptApi：部门管理接口
//   - PostApi：岗位管理接口
//   - AdminUserApi：管理员用户接口
//   - SmsSendApi：短信发送接口
//   - DictDataApi：字典数据接口
//   - PermissionApi：权限管理接口
public class RpcConfiguration {

    // ========== 特殊：解决微 yudao-cloud 微服务场景下，跨服务（进程）无法 Listener 的问题 ==========
    // 问题说明：
    // 在微服务架构中，不同的服务运行在不同的进程中，Spring 的事件监听机制默认只能在同一个进程内工作。
    // 当 CRM 模块发布事件时，BPM 模块如果在另一个进程中，就无法直接监听到这些事件。
    // 解决方案：
    // 在 BPM 模块中手动注册这些监听器，配合消息队列（如 RocketMQ）实现跨服务的事件通知。

    /**
     * 创建 CRM 回款状态监听器
     *
     * 功能说明：
     * 监听 CRM 模块中回款单的状态变化事件，例如：
     * - 回款单创建
     * - 回款单审核通过
     * - 回款单审核拒绝
     *
     * 为什么需要监听？
     * BPM 模块负责业务流程审批，当回款单的审批状态发生变化时，
     * 需要同步更新 CRM 模块中回款单的状态。
     *
     * @return CrmReceivableStatusListener 回款状态监听器实例
     */
    @Bean
    // @Bean：将方法返回的对象注册为 Spring 容器中的一个 Bean（组件），供其他类使用

    @ConditionalOnMissingBean(name = "crmReceivableStatusListener")
    // @ConditionalOnMissingBean：条件注解，只有当 Spring 容器中不存在名为 "crmReceivableStatusListener" 的 Bean 时，
    // 才会创建这个 Bean。这样做是为了避免重复创建，并允许其他配置覆盖此默认配置。
    public CrmReceivableStatusListener crmReceivableStatusListener() {
        return new CrmReceivableStatusListener();
    }

    /**
     * 创建 CRM 合同状态监听器
     *
     * 功能说明：
     * 监听 CRM 模块中合同的状态变化事件，例如：
     * - 合同创建
     * - 合同审核通过
     * - 合同审核拒绝
     *
     * 为什么需要监听？
     * 当合同的审批流程完成后，需要将审批结果同步到 CRM 模块，
     * 更新合同的状态，确保数据一致性。
     *
     * @return CrmContractStatusListener 合同状态监听器实例
     */
    @Bean
    // @Bean：将方法返回的对象注册为 Spring 容器中的一个 Bean（组件）

    @ConditionalOnMissingBean(name = "crmContractStatusListener")
    // @ConditionalOnMissingBean：条件注解，只有当容器中不存在该 Bean 时才创建，
    // 提供了灵活性，允许在其他配置中自定义实现。
    public CrmContractStatusListener crmContractStatusListener() {
        return new CrmContractStatusListener();
    }

}
