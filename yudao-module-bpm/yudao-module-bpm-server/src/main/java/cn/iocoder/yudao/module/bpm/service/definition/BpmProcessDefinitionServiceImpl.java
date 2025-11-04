package cn.iocoder.yudao.module.bpm.service.definition;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.object.PageUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelMetaInfoVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.process.BpmProcessDefinitionPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmProcessDefinitionInfoMapper;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.impl.db.SuspensionState;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.Model;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.addIfNotNull;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;
import static java.util.Collections.emptyList;

/**
 * 流程定义服务实现类
 * <p>
 * 负责与 Flowable 引擎交互，管理流程定义（ProcessDefinition）和部署（Deployment），
 * 同时维护业务扩展信息（BpmProcessDefinitionInfoDO），实现流程定义的查询、创建、状态控制等核心功能。
 * </p>
 *
 * @author yunlongn
 * @author ZJQ
 * @author 芋道源码
 */
@Service
@Validated
@Slf4j
public class BpmProcessDefinitionServiceImpl implements BpmProcessDefinitionService {

    /**
     * Flowable 的 RepositoryService，用于管理流程定义、部署、模型等静态资源。
     */
    @Resource
    private RepositoryService repositoryService;

    /**
     * 流程定义扩展信息的数据库操作 Mapper。
     */
    @Resource
    private BpmProcessDefinitionInfoMapper processDefinitionMapper;

    /**
     * 系统用户服务 API，用于获取用户/部门信息，判断流程启动权限。
     */
    @Resource
    private AdminUserApi adminUserApi;

    // ==================== 流程定义（ProcessDefinition）查询 ====================

    @Override
    public ProcessDefinition getProcessDefinition(String id) {
        // 根据流程定义 ID 获取 Flowable 的 ProcessDefinition 对象
        return repositoryService.getProcessDefinition(id);
    }

    @Override
    public List<ProcessDefinition> getProcessDefinitionList(Set<String> ids) {
        // 批量根据流程定义 ID 查询
        return repositoryService.createProcessDefinitionQuery().processDefinitionIds(ids).list();
    }

    @Override
    public ProcessDefinition getProcessDefinitionByDeploymentId(String deploymentId) {
        // 根据部署 ID 获取对应的流程定义（一个部署通常对应一个流程定义）
        if (StrUtil.isEmpty(deploymentId)) {
            return null;
        }
        return repositoryService.createProcessDefinitionQuery().deploymentId(deploymentId).singleResult();
    }

    @Override
    public List<ProcessDefinition> getProcessDefinitionListByDeploymentIds(Set<String> deploymentIds) {
        // 批量根据部署 ID 查询流程定义
        if (CollUtil.isEmpty(deploymentIds)) {
            return emptyList();
        }
        return repositoryService.createProcessDefinitionQuery().deploymentIds(deploymentIds).list();
    }

    @Override
    public ProcessDefinition getActiveProcessDefinition(String key) {
        // 获取当前租户下、指定 key 的最新激活状态的流程定义（用于启动流程）
        return repositoryService.createProcessDefinitionQuery()
                .processDefinitionTenantId(FlowableUtils.getTenantId()) // 多租户隔离
                .processDefinitionKey(key)
                .active() // 仅查询未挂起的
                .singleResult();
    }

    // ==================== 流程启动权限校验 ====================

    @Override
    public boolean canUserStartProcessDefinition(BpmProcessDefinitionInfoDO processDefinition, Long userId) {
        // 判断用户是否有权限启动该流程定义
        if (processDefinition == null) {
            return false;
        }

        // 1. 如果指定了可启动用户，则必须包含当前用户
        if (CollUtil.isNotEmpty(processDefinition.getStartUserIds())) {
            return processDefinition.getStartUserIds().contains(userId);
        }

        // 2. 如果指定了可启动部门，则用户所在部门必须在列表中
        if (CollUtil.isNotEmpty(processDefinition.getStartDeptIds())) {
            AdminUserRespDTO user = adminUserApi.getUser(userId).getCheckedData();
            return user != null
                    && user.getDeptId() != null
                    && processDefinition.getStartDeptIds().contains(user.getDeptId());
        }

        // 3. 未限制用户或部门，则默认允许任何人启动
        return true;
    }

    // ==================== 部署（Deployment）查询 ====================

    @Override
    public List<Deployment> getDeploymentList(Set<String> ids) {
        // 批量查询部署信息，跳过空 ID
        if (CollUtil.isEmpty(ids)) {
            return emptyList();
        }
        List<Deployment> list = new ArrayList<>(ids.size());
        for (String id : ids) {
            addIfNotNull(list, getDeployment(id));
        }
        return list;
    }

    @Override
    public Deployment getDeployment(String id) {
        // 根据部署 ID 查询部署对象
        if (StrUtil.isEmpty(id)) {
            return null;
        }
        return repositoryService.createDeploymentQuery().deploymentId(id).singleResult();
    }

    // ==================== 创建流程定义 ====================

    @Override
    public String createProcessDefinition(Model model, BpmModelMetaInfoVO modelMetaInfo,
                                          byte[] bpmnBytes, String simpleJson, BpmFormDO form) {
        // 将 BPMN 文件部署到 Flowable 引擎，生成 Deployment 和 ProcessDefinition

        Deployment deploy = repositoryService.createDeployment()
                .key(model.getKey())           // 部署的 key（应与模型一致）
                .name(model.getName())         // 部署名称
                .category(model.getCategory()) // 分类
                .addBytes(model.getKey() + BpmnModelConstants.BPMN_FILE_SUFFIX, bpmnBytes) // 添加 BPMN 字节流
                .tenantId(FlowableUtils.getTenantId()) // 多租户支持
                .disableSchemaValidation()     // 禁用 BPMN XML Schema 验证（允许自定义属性）
                .deploy();                     // 执行部署

        // 查询刚刚部署生成的流程定义
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deploy.getId()).singleResult();

        // 设置流程定义的分类（Flowable 默认不会从 deployment 继承 category，需手动设置）
        repositoryService.setProcessDefinitionCategory(definition.getId(), model.getCategory());

        // 校验流程定义的 key 和 name 是否与模型一致（业务层面要求三者 key 一致：Model、Deployment、ProcessDefinition）
        if (!Objects.equals(definition.getKey(), model.getKey())) {
            throw exception(PROCESS_DEFINITION_KEY_NOT_MATCH, model.getKey(), definition.getKey());
        }
        if (!Objects.equals(definition.getName(), model.getName())) {
            throw exception(PROCESS_DEFINITION_NAME_NOT_MATCH, model.getName(), definition.getName());
        }

        // 保存流程定义的业务扩展信息到数据库
        BpmProcessDefinitionInfoDO definitionDO = BeanUtils.toBean(modelMetaInfo, BpmProcessDefinitionInfoDO.class)
                .setModelId(model.getId())                  // 关联的模型 ID
                .setCategory(model.getCategory())
                .setProcessDefinitionId(definition.getId()) // Flowable 流程定义 ID
                .setModelType(modelMetaInfo.getType())
                .setSimpleModel(simpleJson);                // 简化版流程模型 JSON（用于前端展示）

        // 若关联了表单，则保存表单配置
        if (form != null) {
            definitionDO.setFormFields(form.getFields()).setFormConf(form.getConf());
        }

        processDefinitionMapper.insert(definitionDO);
        return definition.getId(); // 返回 Flowable 的流程定义 ID
    }

    // ==================== 流程定义状态（激活/挂起）管理 ====================

    @Override
    public void updateProcessDefinitionState(String id, Integer state) {
        // 根据状态码激活或挂起流程定义
        ProcessDefinition processDefinition = repositoryService.getProcessDefinition(id);
        if (processDefinition == null) {
            throw exception(PROCESS_DEFINITION_NOT_EXISTS);
        }

        // 激活状态
        if (Objects.equals(SuspensionState.ACTIVE.getStateCode(), state)) {
            if (processDefinition.isSuspended()) {
                // 仅当当前是挂起状态时才激活
                repositoryService.activateProcessDefinitionById(id, false, null);
            }
            return;
        }

        // 挂起状态
        if (Objects.equals(SuspensionState.SUSPENDED.getStateCode(), state)) {
            if (!processDefinition.isSuspended()) {
                // 挂起流程定义，但不挂起正在运行的实例（suspendProcessInstances = false）
                // 目的是禁止新流程启动，但允许已有流程继续执行
                repositoryService.suspendProcessDefinitionById(id, false, null);
            }
            return;
        }

        // 未知状态，记录错误日志
        log.error("[updateProcessDefinitionState][流程定义({}) 修改未知状态({})]", id, state);
    }

    // ==================== 流程定义排序更新 ====================

    @Override
    public void updateProcessDefinitionSortByModelId(String modelId, Long sort) {
        // 根据模型 ID 更新流程定义信息的排序字段（用于前端列表排序）
        processDefinitionMapper.updateByModelId(modelId, new BpmProcessDefinitionInfoDO().setSort(sort));
    }

    // ==================== BPMN 模型获取 ====================

    @Override
    public BpmnModel getProcessDefinitionBpmnModel(String id) {
        // 获取流程定义对应的 BPMN 模型对象（用于解析节点、连线等）
        return repositoryService.getBpmnModel(id);
    }

    // ==================== 流程定义扩展信息查询 ====================

    @Override
    public BpmProcessDefinitionInfoDO getProcessDefinitionInfo(String id) {
        // 根据 Flowable 流程定义 ID 查询业务扩展信息
        return processDefinitionMapper.selectByProcessDefinitionId(id);
    }

    @Override
    public List<BpmProcessDefinitionInfoDO> getProcessDefinitionInfoList(Collection<String> ids) {
        // 批量查询流程定义的业务扩展信息
        return processDefinitionMapper.selectListByProcessDefinitionIds(ids);
    }

    // ==================== 流程定义分页查询 ====================

    @Override
    public PageResult<ProcessDefinition> getProcessDefinitionPage(BpmProcessDefinitionPageReqVO pageVO) {
        // 构造查询条件：当前租户 + 可选 key 过滤
        ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
        query.processDefinitionTenantId(FlowableUtils.getTenantId());
        if (StrUtil.isNotBlank(pageVO.getKey())) {
            query.processDefinitionKey(pageVO.getKey());
        }

        // 执行分页查询：按版本降序（最新版本在前）
        long count = query.count();
        if (count == 0) {
            return PageResult.empty(count);
        }
        List<ProcessDefinition> list = query.orderByProcessDefinitionVersion().desc()
                .listPage(PageUtils.getStart(pageVO), pageVO.getPageSize());
        return new PageResult<>(list, count);
    }

    // ==================== 按状态批量查询流程定义 ====================

    @Override
    public List<ProcessDefinition> getProcessDefinitionListBySuspensionState(Integer suspensionState) {
        // 构造查询：根据激活/挂起状态过滤
        ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
        if (Objects.equals(SuspensionState.SUSPENDED.getStateCode(), suspensionState)) {
            query.suspended(); // 仅挂起
        } else if (Objects.equals(SuspensionState.ACTIVE.getStateCode(), suspensionState)) {
            query.active();    // 仅激活
        }
        // 限定当前租户
        query.processDefinitionTenantId(FlowableUtils.getTenantId());
        return query.list();
    }

}