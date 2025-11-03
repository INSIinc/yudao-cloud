package cn.iocoder.yudao.module.bpm.framework.flowable.core.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.core.KeyValue;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.spring.SpringUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmHttpRequestParamTypeEnum;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils.HEADER_TENANT_ID;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_HTTP_CALL_ERROR;

/**
 * 工作流发起 HTTP 请求工具类
 * <p>
 * 用于在 Flowable 流程执行过程中，根据配置自动发起 HTTP 请求（如回调外部系统），
 * 并可选地将响应结果解析后回写到流程变量中。
 * 支持从流程变量或固定值中提取参数填充到请求头或请求体。
 *
 * @author 芋道源码
 */
@Slf4j
public class BpmHttpRequestUtils {

    /**
     * 执行 BPM HTTP 请求（主流程调用）
     * <p>
     * 适用于在流程节点中配置了 HTTP 请求动作的情况。
     * - 构建请求头（含租户 ID 和用户自定义头）
     * - 构建请求体（含流程实例 ID 和用户自定义参数）
     * - 发起 POST 请求
     * - 若启用了 handleResponse，则解析响应并更新流程变量
     *
     * @param processInstance 当前流程实例（用于获取租户ID、流程变量等）
     * @param url             目标请求 URL
     * @param headerParams    请求头参数配置列表（类型为固定值或来自表单变量）
     * @param bodyParams      请求体参数配置列表
     * @param handleResponse  是否处理响应并更新变量（true=处理，false=忽略响应）
     * @param response        响应映射规则：Key 为流程变量名，Value 为响应 JSON 中的字段路径
     */
    public static void executeBpmHttpRequest(ProcessInstance processInstance,
                                             String url,
                                             List<BpmSimpleModelNodeVO.HttpRequestParam> headerParams,
                                             List<BpmSimpleModelNodeVO.HttpRequestParam> bodyParams,
                                             Boolean handleResponse,
                                             List<KeyValue<String, String>> response) {
        BpmProcessInstanceService processInstanceService = SpringUtils.getBean(BpmProcessInstanceService.class);

        // 1.1 构建 HTTP 请求头（包含租户 ID 和用户配置的头参数）
        MultiValueMap<String, String> headers = buildHttpHeaders(processInstance, headerParams);
        // 1.2 构建 HTTP 请求体（包含 processInstanceId 和用户配置的体参数）
        MultiValueMap<String, String> body = buildHttpBody(processInstance, bodyParams);

        // 2. 使用 RestTemplate 发起 HTTP POST 请求
        RestTemplate restTemplate = SpringUtils.getBean(RestTemplate.class);
        ResponseEntity<String> responseEntity = sendHttpRequest(url, headers, body, restTemplate);

        // 3. 如果不需要处理响应，直接返回
        if (Boolean.FALSE.equals(handleResponse)) {
            return;
        }

        // 3.1 初步校验响应：非空、2xx 成功状态、且有响应映射规则
        if (responseEntity == null
                || StrUtil.isEmpty(responseEntity.getBody())
                || !responseEntity.getStatusCode().is2xxSuccessful()
                || CollUtil.isEmpty(response)) {
            return;
        }

        // 3.2 尝试将响应体解析为 CommonResult<Map<String, Object>> 格式
        // 要求外部接口返回结果符合 CommonResult 规范（如 { "code": 200, "data": { ... } }）
        CommonResult<Map<String, Object>> respResult = JsonUtils.parseObjectQuietly(responseEntity.getBody(),
                new TypeReference<CommonResult<Map<String, Object>>>() {});
        if (respResult == null || !respResult.isSuccess()) {
            // 解析失败或业务失败，不抛异常，仅跳过变量更新（避免阻塞流程）
            return;
        }

        // 3.3 根据 response 映射规则，从 respResult.getData() 中提取需要回写的变量
        Map<String, Object> updateVariables = getNeedUpdatedVariablesFromResponse(respResult.getData(), response);
        // 3.4 如果有变量需要更新，则调用服务更新流程变量
        if (CollUtil.isNotEmpty(updateVariables)) {
            processInstanceService.updateProcessInstanceVariables(processInstance.getId(), updateVariables);
        }
    }

    /**
     * 执行 BPM HTTP 请求（用于流程状态变更事件）
     * <p>
     * 在流程实例状态变更（如完成、终止）时，通知外部系统。
     * 请求头包含租户 ID，请求体为 {@link BpmProcessInstanceStatusEvent} 对象（JSON 格式）。
     *
     * @param event 流程实例状态事件（包含 ID、状态、业务键等信息）
     * @param url   通知目标 URL
     */
    public static void executeBpmHttpRequest(BpmProcessInstanceStatusEvent event,
                                             String url) {
        // 1.1 构建请求头：设置 Content-Type 为 application/json，并添加租户 ID
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            // 优先使用当前上下文中的租户 ID
            headers.add(HEADER_TENANT_ID, String.valueOf(tenantId));
        } else {
            // 若上下文无租户，尝试从流程实例中获取（兜底逻辑，但此处代码有误，见注释）
            BpmProcessInstanceService processInstanceService = SpringUtils.getBean(BpmProcessInstanceService.class);
            ProcessInstance processInstance = processInstanceService.getProcessInstance(event.getId());
            if (processInstance != null) {
                // ⚠️ 此处应使用 processInstance.getTenantId()，而非重复获取上下文中的 null 值
                headers.add(HEADER_TENANT_ID, String.valueOf(processInstance.getTenantId()));
            }
        }

        // 1.2 请求体直接使用 event 对象（将被序列化为 JSON）

        // 2. 发起 HTTP POST 请求
        RestTemplate restTemplate = SpringUtils.getBean(RestTemplate.class);
        sendHttpRequest(url, headers, event, restTemplate);
    }

    /**
     * 通用方法：使用 RestTemplate 发起 HTTP POST 请求
     *
     * @param url          请求 URL
     * @param headers      请求头（MultiValueMap 或 HttpHeaders）
     * @param body         请求体（支持任意对象，将由 RestTemplate 自动序列化）
     * @param restTemplate RestTemplate 实例
     * @return 响应实体（响应体为 String）
     * @throws ServiceException 当 HTTP 请求发生异常时（如网络错误、超时等）
     */
    public static ResponseEntity<String> sendHttpRequest(String url,
                                                         MultiValueMap<String, String> headers,
                                                         Object body,
                                                         RestTemplate restTemplate) {
        // 构造 HttpEntity，自动处理头和体
        HttpEntity<Object> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> responseEntity;
        try {
            responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            log.info("[sendHttpRequest][HTTP 请求，请求头：{}，请求体：{}，响应结果：{}]", headers, body, responseEntity);
        } catch (RestClientException e) {
            // 记录错误日志，并抛出业务异常（中断流程）
            log.error("[sendHttpRequest][HTTP 请求，请求头：{}，请求体：{}，请求出错：{}]", headers, body, e.getMessage(), e);
            throw exception(PROCESS_INSTANCE_HTTP_CALL_ERROR); // 对应错误码：HTTP 调用失败
        }
        return responseEntity;
    }

    /**
     * 构建 HTTP 请求头
     * <p>
     * - 自动添加租户 ID（来自 processInstance.getTenantId()）
     * - 根据 headerSettings 配置，追加自定义头（固定值或从流程变量取值）
     *
     * @param processInstance 流程实例
     * @param headerSettings  头参数配置列表
     * @return 构建好的请求头
     */
    public static MultiValueMap<String, String> buildHttpHeaders(ProcessInstance processInstance,
                                                                 List<BpmSimpleModelNodeVO.HttpRequestParam> headerSettings) {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        // 必须添加租户 ID，用于多租户隔离
        headers.add(HEADER_TENANT_ID, processInstance.getTenantId());
        // 添加用户配置的头参数
        addHttpRequestParam(headers, headerSettings, processInstance.getProcessVariables());
        return headers;
    }

    /**
     * 构建 HTTP 请求体（表单格式：application/x-www-form-urlencoded）
     * <p>
     * - 自动添加 processInstanceId（除非已存在）
     * - 根据 bodySettings 配置，追加自定义参数（固定值或从流程变量取值）
     *
     * @param processInstance 流程实例
     * @param bodySettings    体参数配置列表
     * @return 构建好的请求体（MultiValueMap，适合表单提交）
     */
    public static MultiValueMap<String, String> buildHttpBody(ProcessInstance processInstance,
                                                              List<BpmSimpleModelNodeVO.HttpRequestParam> bodySettings) {
        Map<String, Object> processVariables = processInstance.getProcessVariables();
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        // 添加用户配置的体参数
        addHttpRequestParam(body, bodySettings, processVariables);
        // 确保包含流程实例 ID（便于外部系统关联）
        if (!body.containsKey("processInstanceId")) {
            body.add("processInstanceId", processInstance.getId());
        }
        return body;
    }

    /**
     * 从 HTTP 响应的 data 部分提取需要更新到流程变量的字段
     * <p>
     * 示例：responseSettings = [{ key="userName", value="user.name" }]
     * 若 result = { "user": { "name": "张三" } }，则返回 { "userName": "张三" }
     *
     * @param result           HTTP 响应的 data 部分（Map 结构）
     * @param responseSettings 响应映射规则列表：key=流程变量名，value=响应 JSON 中的字段路径（当前仅支持一级 key）
     * @return 需要更新的流程变量 Map
     */
    public static Map<String, Object> getNeedUpdatedVariablesFromResponse(Map<String, Object> result,
                                                                          List<KeyValue<String, String>> responseSettings) {
        Map<String, Object> updateVariables = new HashMap<>();
        if (CollUtil.isEmpty(result)) {
            return updateVariables;
        }
        // 遍历映射规则
        for (KeyValue<String, String> responseSetting : responseSettings) {
            String variableName = responseSetting.getKey();     // 流程变量名
            String responseField = responseSetting.getValue();  // 响应中的字段名（仅支持顶层 key）
            if (StrUtil.isNotEmpty(variableName) && result.containsKey(responseField)) {
                updateVariables.put(variableName, result.get(responseField));
            }
        }
        return updateVariables;
    }

    /**
     * 通用方法：向 MultiValueMap 中添加 HTTP 参数（头或体）
     * <p>
     * 支持两种参数类型：
     * - 固定值（FIXED_VALUE）：直接使用配置的 value
     * - 来自表单（FROM_FORM）：从 processVariables 中按 key 获取值并转为字符串
     *
     * @param params           目标参数容器（请求头或请求体）
     * @param paramSettings    参数配置列表
     * @param processVariables 当前流程实例的变量集合
     */
    public static void addHttpRequestParam(MultiValueMap<String, String> params,
                                           List<BpmSimpleModelNodeVO.HttpRequestParam> paramSettings,
                                           Map<String, Object> processVariables) {
        if (CollUtil.isEmpty(paramSettings)) {
            return;
        }
        for (BpmSimpleModelNodeVO.HttpRequestParam item : paramSettings) {
            String key = item.getKey();
            String value = item.getValue();
            Integer type = item.getType();
            if (BpmHttpRequestParamTypeEnum.FIXED_VALUE.getType().equals(type)) {
                // 类型为固定值：直接使用配置的 value
                params.add(key, value);
            } else if (BpmHttpRequestParamTypeEnum.FROM_FORM.getType().equals(type)) {
                // 类型为从流程变量取值：需确保变量存在且不为 null
                Object variableValue = processVariables.get(value);
                if (variableValue != null) {
                    params.add(key, variableValue.toString());
                }
                // 若变量不存在或为 null，则跳过（不添加该参数）
            }
        }
    }

}