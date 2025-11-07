package cn.iocoder.yudao.module.bpm.framework.flowable.core.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO.ConditionGroups;
import cn.iocoder.yudao.module.bpm.enums.definition.*;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.listener.BpmCopyTaskDelegate;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.listener.BpmTriggerTaskDelegate;
import cn.iocoder.yudao.module.bpm.service.task.listener.BpmCallActivityListener;
import cn.iocoder.yudao.module.bpm.service.task.listener.BpmUserTaskListener;
import org.flowable.bpmn.BpmnAutoLayout;
import org.flowable.bpmn.constants.BpmnXMLConstants;
import org.flowable.bpmn.model.*;
import org.flowable.bpmn.model.Process;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.delegate.TaskListener;

import java.util.*;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants.*;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.*;
import static java.util.Arrays.asList;

/**
 * 仿钉钉/飞书的模型相关的工具方法
 * <p>
 * 1. 核心的逻辑实现，可见 {@link #buildBpmnModel(String, String, BpmSimpleModelNodeVO)} 方法
 * 2. 所有的 BpmSimpleModelNodeVO 转换成 BPMN FlowNode 元素，可见 {@link NodeConvert} 实现类
 *
 * @author jason
 */
public class SimpleModelUtils {
    // NODE_CONVERTS - 节点类型到转换器的映射表
    private static final Map<BpmSimpleModelNodeTypeEnum, NodeConvert> NODE_CONVERTS = MapUtil.newHashMap();

    static {
        List<NodeConvert> converts = asList(
                new StartNodeConvert(),
                new EndNodeConvert(),
                new StartUserNodeConvert(),  // 发起人节点
                new ApproveNodeConvert(),  // 审批节点
                new CopyNodeConvert(),  // 抄送节点
                new TransactorNodeConvert(), // 办理人节点
                new DelayTimerNodeConvert(),  // 定时器节点
                new TriggerNodeConvert(),  // 触发器节点
                new ConditionBranchNodeConvert(),   // 排他网关
                new ParallelBranchNodeConvert(),  // 并行网关
                new InclusiveBranchNodeConvert(),  // 包容网关
                new RouteBranchNodeConvert(),  // 路由分支
                new ChildProcessConvert()
        ); // 子流程节点
        converts.forEach(convert -> NODE_CONVERTS.put(convert.getType(), convert));
    }

    /**
     * 仿钉钉流程设计模型数据结构（json）转换成 Bpmn Model
     * <p>
     * 整体逻辑如下：
     * 1. 创建：BpmnModel、Process 对象
     * 2. 转换：将 BpmSimpleModelNodeVO 转换成 BPMN FlowNode 元素
     * 3. 连接：构建并添加节点之间的连线 Sequence Flow
     *
     * @param processId       流程标识
     * @param processName     流程名称
     * @param simpleModelNode 仿钉钉流程设计模型数据结构
     * @return Bpmn Model
     */
    public static BpmnModel buildBpmnModel(String processId, String processName, BpmSimpleModelNodeVO simpleModelNode) {
        // 1. 创建 BpmnModel
        BpmnModel bpmnModel = new BpmnModel();
        bpmnModel.setTargetNamespace(BpmnXMLConstants.BPMN2_NAMESPACE); // 设置命名空间。不加这个，解析 Message 会报 NPE 异常
        // 创建 Process 对象
        /**
         * - 设置流程 ID（唯一标识）
         * - 设置流程名称
         * - 设置为可执行（必须为 true，否则引擎不会执行）
         * - 将流程添加到模型中
         * */
        Process process = new Process();
        process.setId(processId);
        process.setName(processName);
        process.setExecutable(Boolean.TRUE);
        bpmnModel.addProcess(process);

        // 2.1 创建 StartNode 节点
        // 原因是：目前前端的第一个节点是“发起人节点”，所以这里构建一个 StartNode，用于创建 Bpmn 的 StartEvent 节点
        // ⚠️ 重要：前端第一个节点是"发起人节点"，但 BPMN 必须以 StartEvent 开始
        // 所以这里手动创建一个 StartNode，将前端的 simpleModelNode 作为其子节点
        BpmSimpleModelNodeVO startNode = buildStartNode();
        startNode.setChildNode(simpleModelNode);
        // 2.2 将前端传递的 simpleModelNode 数据结构（json），转换成从 BPMN FlowNode 元素，并添加到 Main Process 中
        traverseNodeToBuildFlowNode(startNode, process);

        // 3. 构建并添加节点之间的连线 Sequence Flow
        // 遍历节点树，将每个简单节点转换为 BPMN FlowNode，并添加到 process
        EndEvent endEvent = getEndEvent(bpmnModel);
        traverseNodeToBuildSequenceFlow(process, startNode, endEvent.getId());

        // 4. 自动布局，使用 Flowable 的自动布局算法，计算每个节点的坐标位置
        new BpmnAutoLayout(bpmnModel).execute();
        return bpmnModel;
    }

    private static BpmSimpleModelNodeVO buildStartNode() {
        return new BpmSimpleModelNodeVO().setId(START_EVENT_NODE_ID)
                .setName(BpmSimpleModelNodeTypeEnum.START_NODE.getName())
                .setType(BpmSimpleModelNodeTypeEnum.START_NODE.getType());
    }

    /**
     * 遍历节点，构建 FlowNode 元素（节点转换）
     *
     * @param node    SIMPLE 节点
     * @param process BPMN 流程
     */
    private static void traverseNodeToBuildFlowNode(BpmSimpleModelNodeVO node, Process process) {
        // =============== 第一阶段：将『简单模型节点』转换成 Flowable 可识别的 BPMN 元素 ===============
        // 说明：
        // 前端拖拽形成的是一个自定义的简单数据结构(simple model)，这里需要把它逐个“翻译”成 BPMN 里的节点对象（如：StartEvent、UserTask、Gateway 等），
        // 才能让 Flowable 引擎理解并执行。这个方法只做“节点创建”，不处理节点之间的连线；连线在 traverseNodeToBuildSequenceFlow 中处理。

        // 1. 判断是否有效节点
        if (!isValidNode(node)) {
            return;
        }
        // 将节点类型字符串转换为枚举，并断言不为空
        BpmSimpleModelNodeTypeEnum nodeType = BpmSimpleModelNodeTypeEnum.valueOf(node.getType());
        Assert.notNull(nodeType, "模型节点类型({})不支持", node.getType());

        // 2. 处理当前节点
        NodeConvert nodeConvert = NODE_CONVERTS.get(nodeType); // 从集合中找支持的对应类型
        Assert.notNull(nodeConvert, "模型节点类型的转换器({})不存在", node.getType());
        // 调用转换器，将简单节点转换为 BPMN FlowElement（可能是多个，如审批节点+超时边界事件）
        List<? extends FlowElement> flowElements = nodeConvert.convertList(node);
        // 将转换后的元素添加到 process 中
        flowElements.forEach(process::addFlowElement);

        // 3.1 情况一：如果当前是分支节点，并且存在条件节点，则处理每个条件的子节点
        if (BpmSimpleModelNodeTypeEnum.isBranchNode(node.getType())
                && CollUtil.isNotEmpty(node.getConditionNodes())) {
            // 注意：这里的 item.getChildNode() 处理的是每个条件的子节点，不是处理条件
            node.getConditionNodes().forEach(item -> traverseNodeToBuildFlowNode(item.getChildNode(), process));
        }

        // 3.2 情况二：如果有“子”节点，则递归处理子节点
        traverseNodeToBuildFlowNode(node.getChildNode(), process);
    }

    /**
     * 遍历节点，构建 SequenceFlow 元素
     *
     * @param process      Bpmn 流程
     * @param node         当前节点
     * @param targetNodeId 目标节点 ID
     */
    private static void traverseNodeToBuildSequenceFlow(Process process, BpmSimpleModelNodeVO node, String targetNodeId) {
        // =============== 第二阶段：为前面创建的 BPMN 节点补上『连线』(SequenceFlow) ===============
        // 思路：
        // 1. 递归从当前节点向后看，决定它应该指向哪个下一个节点。
        // 2. 普通节点：直接连到它的 childNode；如果没有 childNode，则连到外层传入的 targetNodeId（通常是结束节点或分支的汇聚点）。
        // 3. 分支节点：分类型处理（条件、并行、包容、路由），为每个分支建立连线，并在必要时创建“汇聚”网关的连线。
        // 注意：这里不再创建节点对象，只是补全节点之间的连接关系。
        // 1.1 无效节点返回
        if (!isValidNode(node)) {
            return;
        }
        // 1.2 END_NODE 直接返回
        BpmSimpleModelNodeTypeEnum nodeType = BpmSimpleModelNodeTypeEnum.valueOf(node.getType());
        Assert.notNull(nodeType, "模型节点类型不支持");
        if (nodeType == BpmSimpleModelNodeTypeEnum.END_NODE) {
            return;
        }

        // 2.1 情况一：普通节点
        if (!BpmSimpleModelNodeTypeEnum.isBranchNode(node.getType())) {
            traverseNormalNodeToBuildSequenceFlow(process, node, targetNodeId);
        } else {
            // 2.2 情况二：分支节点
            traverseBranchNodeToBuildSequenceFlow(process, node, targetNodeId);
        }
    }

    /**
     * 遍历普通（非条件）节点，构建 SequenceFlow 元素
     *
     * @param process      Bpmn 流程
     * @param node         当前节点
     * @param targetNodeId 目标节点 ID
     */
    private static void traverseNormalNodeToBuildSequenceFlow(Process process, BpmSimpleModelNodeVO node, String targetNodeId) {
        // 普通节点（非分支网关）：
        // 目标判定规则：
        //   有合法的 childNode => 线指向 childNode
        //   没有 childNode => 线指向外部传入的 targetNodeId（例如：后续的结束节点或上层设定的“分支终点”）
        // 附加节点(attachNode)：某些业务节点需要“挂”一个边界等待/回调节点，流程走法变成：当前节点 -> 附加节点 -> 下一个节点
        BpmSimpleModelNodeVO childNode = node.getChildNode();
        boolean isChildNodeValid = isValidNode(childNode);
        // 情况一：有“子”节点，则建立连线
        // 情况二：没有“子节点”，则直接跟 targetNodeId 建立连线。例如说，结束节点、条件分支（分支节点的孩子节点或聚合节点）的最后一个节点
        String finalTargetNodeId = isChildNodeValid ? childNode.getId() : targetNodeId;

        // 如果没有附加节点：则直接建立连线
        if (StrUtil.isEmpty(node.getAttachNodeId())) {
            SequenceFlow sequenceFlow = buildBpmnSequenceFlow(node.getId(), finalTargetNodeId);
            process.addFlowElement(sequenceFlow);
        } else {
            // 如果有附加节点：需要先建立和附加节点的连线，再建立附加节点和目标节点的连线。例如说，触发器节点（HTTP 回调）
            List<SequenceFlow> sequenceFlows = buildAttachNodeSequenceFlow(node.getId(), node.getAttachNodeId(), finalTargetNodeId);
            sequenceFlows.forEach(process::addFlowElement);
        }

        // 因为有子节点，递归调用后续子节点
        if (isChildNodeValid) {
            traverseNodeToBuildSequenceFlow(process, childNode, targetNodeId);
        }
    }

    /**
     * 构建有附加节点的连线
     *
     * @param nodeId       当前节点 ID
     * @param attachNodeId 附属节点 ID
     * @param targetNodeId 目标节点 ID
     */
    private static List<SequenceFlow> buildAttachNodeSequenceFlow(String nodeId, String attachNodeId, String targetNodeId) {
        // 对于带“附加”行为的节点（如：触发器 HTTP 回调，需等待外部通知）：
        // 建两条线：
        //   1) 主节点(nodeId) -> 附加等待节点(attachNodeId)
        //   2) 附加等待节点(attachNodeId) -> 后续正常流转节点(targetNodeId)
        SequenceFlow sequenceFlow = buildBpmnSequenceFlow(nodeId, attachNodeId, null, null, null);
        SequenceFlow attachSequenceFlow = buildBpmnSequenceFlow(attachNodeId, targetNodeId, null, null, null);
        return CollUtil.newArrayList(sequenceFlow, attachSequenceFlow);
    }

    /**
     * 遍历条件节点，构建 SequenceFlow 元素
     *
     * @param process      Bpmn 流程
     * @param node         当前节点
     * @param targetNodeId 目标节点 ID
     */
    private static void traverseBranchNodeToBuildSequenceFlow(Process process, BpmSimpleModelNodeVO node, String targetNodeId) {
        // 分支节点处理：根据不同网关类型构造多条出口连线，并确定这些分支最终如何汇合。
        // 主要类型：
        //  - CONDITION_BRANCH_NODE(排它)：只走满足条件的那一条 (若无匹配则走默认)
        //  - PARALLEL_BRANCH_NODE(并行)：所有分支都同时执行，使用包容网关实现，出口条件强制为 true
        //  - INCLUSIVE_BRANCH_NODE(包容)：满足条件的可同时走，若都不满足走默认
        //  - ROUTER_BRANCH_NODE(路由)：类似排它但使用自定义路由配置 routerGroups
        // branchEndNodeId：分支汇聚的“终点”节点 ID；不同类型决定不同生成策略。
        // 获取节点类型和子节点信息
        BpmSimpleModelNodeTypeEnum nodeType = BpmSimpleModelNodeTypeEnum.valueOf(node.getType());
        BpmSimpleModelNodeVO childNode = node.getChildNode();
        List<BpmSimpleModelNodeVO> conditionNodes = node.getConditionNodes();
        // TODO @芋艿 路由分支没有conditionNodes 这里注释会影响吗？@jason：一起帮忙瞅瞅！
//        Assert.notEmpty(conditionNodes, "分支节点的条件节点不能为空");
        // 分支终点节点 ID
        String branchEndNodeId = null;
        if (nodeType == BpmSimpleModelNodeTypeEnum.CONDITION_BRANCH_NODE
                || nodeType == BpmSimpleModelNodeTypeEnum.ROUTER_BRANCH_NODE) { // 条件分支或路由分支
            // 分两种情况 1. 分支节点有孩子节点为孩子节点 Id 2. 分支节点孩子为无效节点时 (分支嵌套且为分支最后一个节点) 为分支终点节点 ID
            branchEndNodeId = isValidNode(childNode) ? childNode.getId() : targetNodeId;
        } else if (nodeType == BpmSimpleModelNodeTypeEnum.PARALLEL_BRANCH_NODE
                || nodeType == BpmSimpleModelNodeTypeEnum.INCLUSIVE_BRANCH_NODE) {  // 并行分支或包容分支
            // 分支节点：分支终点节点 Id 为程序创建的网关集合节点。目前不会从前端传入。
            branchEndNodeId = buildGatewayJoinId(node.getId());
        }
        Assert.notEmpty(branchEndNodeId, "分支终点节点 Id 不能为空");

        // 3. 遍历分支节点
        if (nodeType == BpmSimpleModelNodeTypeEnum.ROUTER_BRANCH_NODE) {
            // 路由分支遍历
            for (BpmSimpleModelNodeVO.RouterSetting router : node.getRouterGroups()) {
                // 每个路由配置都生成一条连线，条件表达式来源于 routerSetting
                SequenceFlow sequenceFlow = RouteBranchNodeConvert.buildSequenceFlow(node.getId(), router);
                process.addFlowElement(sequenceFlow);
            }
        } else {
            // 下面的注释，以如下情况举例子。分支 1：A->B->C->D->E，分支 2：A->D->E。其中，A 为分支节点, D 为 A 孩子节点
            for (BpmSimpleModelNodeVO item : conditionNodes) {
                Assert.isTrue(Objects.equals(item.getType(), BpmSimpleModelNodeTypeEnum.CONDITION_NODE.getType()),
                        "条件节点类型({})不符合", item.getType());
                BpmSimpleModelNodeVO conditionChildNode = item.getChildNode();
                // 3.1 分支有后续节点。即分支 1: A->B->C->D 的情况
                if (isValidNode(conditionChildNode)) {
                    // 3.1.1 建立与后续的节点的连线。例如说，建立 A->B 的连线
                    SequenceFlow sequenceFlow = ConditionNodeConvert.buildSequenceFlow(node.getId(), conditionChildNode.getId(), nodeType, item);
                    process.addFlowElement(sequenceFlow);
                    // 3.1.2 递归调用后续节点连线。例如说，建立 B->C->D 的连线
                    traverseNodeToBuildSequenceFlow(process, conditionChildNode, branchEndNodeId);
                } else {
                    // 3.2 分支没有后续节点。例如说，建立 A->D 的连线
                    SequenceFlow sequenceFlow = ConditionNodeConvert.buildSequenceFlow(node.getId(), branchEndNodeId, nodeType, item);
                    process.addFlowElement(sequenceFlow);
                }
            }
        }

        // 4.1 如果是并行分支、包容分支，由于是程序创建的聚合网关，需要手工创建聚合网关和下一个节点的连线
        if (nodeType == BpmSimpleModelNodeTypeEnum.PARALLEL_BRANCH_NODE
                || nodeType == BpmSimpleModelNodeTypeEnum.INCLUSIVE_BRANCH_NODE) {
            String nextNodeId = isValidNode(childNode) ? childNode.getId() : targetNodeId;
            // 并行/包容：程序自动创建“汇聚”网关 (branchEndNodeId)，需要再接一条线到下一个节点
            SequenceFlow sequenceFlow = buildBpmnSequenceFlow(branchEndNodeId, nextNodeId);
            process.addFlowElement(sequenceFlow);
            // 4.2 如果是路由分支，需要连接后续节点为默认路由
        } else if (nodeType == BpmSimpleModelNodeTypeEnum.ROUTER_BRANCH_NODE) {
            // 路由分支：再补一条默认路由的连线（无条件表达式）供未匹配时走
            SequenceFlow sequenceFlow = buildBpmnSequenceFlow(node.getId(), branchEndNodeId, node.getRouterDefaultFlowId(),
                    null, null);
            process.addFlowElement(sequenceFlow);
        }

        // 5. 递归调用后续节点 继续递归。例如说，建立 D->E 的连线
        traverseNodeToBuildSequenceFlow(process, childNode, targetNodeId);
    }

    private static SequenceFlow buildBpmnSequenceFlow(String sourceId, String targetId) {
        // 构建最常见的连线（不带 id / 名称 / 条件表达式）。
        // Flowable 会在未显式设置 id 时自动生成一个，但为了后续排查问题，有需要时可以传入自定义 id。
        return buildBpmnSequenceFlow(sourceId, targetId, null, null, null);
    }

    private static SequenceFlow buildBpmnSequenceFlow(String sourceId, String targetId,
                                                      String sequenceFlowId, String sequenceFlowName,
                                                      String conditionExpression) {
        Assert.notEmpty(sourceId, "sourceId 不能为空");
        Assert.notEmpty(targetId, "targetId 不能为空");
        // 可选参数说明：
        // sequenceFlowId: 手动指定连线 ID（可方便日志 & 排错）
        // sequenceFlowName: 流程图里展示的名称（通常由用户自己在设计器里填写）
        // conditionExpression: 条件表达式，网关分支上使用；格式通常是 ${...}
        // TODO @jason：如果 sequenceFlowId 不存在的时候，是不是要生成一个默认的 sequenceFlowId？ @芋艿： 貌似不需要,Flowable 会默认生成；TODO @jason：建议还是搞一个，主要是后续好排查问题。
        // TODO @jason：如果 name 不存在的时候，是不是要生成一个默认的 name？ @芋艿： 不需要生成默认的吧？ 这个会在流程图展示的， 一般用户填写的。不好生成默认的吧；TODO @jason：建议还是搞一个，主要是后续好排查问题。
        SequenceFlow sequenceFlow = new SequenceFlow(sourceId, targetId);
        if (StrUtil.isNotEmpty(sequenceFlowId)) {
            sequenceFlow.setId(sequenceFlowId);
        }
        if (StrUtil.isNotEmpty(sequenceFlowName)) {
            sequenceFlow.setName(sequenceFlowName);
        }
        if (StrUtil.isNotEmpty(conditionExpression)) {
            sequenceFlow.setConditionExpression(conditionExpression);
        }
        return sequenceFlow;
    }

    public static boolean isValidNode(BpmSimpleModelNodeVO node) {
        // 是否为一个“可用”的节点：不为空且已经分配了 id
        // 说明：在前端构建流程时，某些占位/未配置完成的节点可能没有 id，这里直接视为无效跳过
        return node != null && node.getId() != null;
    }

    public static boolean isSequentialApproveNode(BpmSimpleModelNodeVO node) {
        // 判断一个审批节点是否为『顺序审批』模式（SEQUENTIAL）
        // 用于后续对多实例审批的特殊处理逻辑
        return BpmSimpleModelNodeTypeEnum.APPROVE_NODE.getType().equals(node.getType())
                && BpmUserTaskApproveMethodEnum.SEQUENTIAL.getMethod().equals(node.getApproveMethod());
    }

    // ========== 各种 convert 节点的方法: BpmSimpleModelNodeVO => BPMN FlowElement ==========

    /**
     * 节点转换器接口
     * <p>
     * 定义了将简单模型节点转换为 BPMN 元素的标准接口
     * 每种节点类型都有一个对应的转换器实现
     */
    private interface NodeConvert {

        /**
         * 转换节点为 FlowElement 列表
         * <p>
         * 默认实现：返回单个元素的列表
         * 某些节点需要返回多个元素（如：审批节点 = UserTask + BoundaryEvent）
         *
         * @param node 简单模型节点
         * @return BPMN 流程元素列表
         */
        default List<? extends FlowElement> convertList(BpmSimpleModelNodeVO node) {
            return Collections.singletonList(convert(node));
        }

        /**
         * 转换单个节点
         * <p>
         * 子类根据需要实现此方法或 convertList 方法
         *
         * @param node 简单模型节点
         * @return BPMN 流程元素
         */
        default FlowElement convert(BpmSimpleModelNodeVO node) {
            throw new UnsupportedOperationException("请实现该方法");
        }

        /**
         * 获取转换器支持的节点类型
         *
         * @return 节点类型枚举
         */
        BpmSimpleModelNodeTypeEnum getType();

    }

    /**
     * 开始节点转换器
     * <p>
     * Simple Model: START_NODE
     * BPMN 元素: StartEvent（开始事件）
     * <p>
     * 作用：每个 BPMN 流程必须有且仅有一个开始事件作为入口
     */
    private static class StartNodeConvert implements NodeConvert {

        @Override
        public StartEvent convert(BpmSimpleModelNodeVO node) {
            // 创建 BPMN 开始事件
            StartEvent startEvent = new StartEvent();
            startEvent.setId(node.getId());        // 设置节点 ID
            startEvent.setName(node.getName());    // 设置节点名称（流程图上显示）
            return startEvent;
        }

        @Override
        public BpmSimpleModelNodeTypeEnum getType() {
            return BpmSimpleModelNodeTypeEnum.START_NODE;
        }

    }

    /**
     * 结束节点转换器
     * <p>
     * Simple Model: END_NODE
     * BPMN 元素: EndEvent（结束事件）
     * <p>
     * 作用：标记流程的结束点，流程执行到此节点后完成
     */
    private static class EndNodeConvert implements NodeConvert {

        @Override
        public EndEvent convert(BpmSimpleModelNodeVO node) {
            // 创建 BPMN 结束事件
            EndEvent endEvent = new EndEvent();
            endEvent.setId(node.getId());         // 设置节点 ID
            endEvent.setName(node.getName());     // 设置节点名称
            // TODO @芋艿 + jason：要不要加一个终止定义？
            // 注：可以添加 TerminateEventDefinition 来终止所有执行流
            return endEvent;
        }

        @Override
        public BpmSimpleModelNodeTypeEnum getType() {
            return BpmSimpleModelNodeTypeEnum.END_NODE;
        }

    }

    /**
     * 发起人节点转换器
     * <p>
     * Simple Model: START_USER_NODE
     * BPMN 元素: UserTask（用户任务）
     * <p>
     * 作用：这是前端第一个可见节点，代表流程发起人填写表单的环节
     * 特点：
     * 1. 候选人固定为发起人自己（START_USER 策略）
     * 2. 自动通过（使用 SKIP 策略，不需要人工审批）
     * 3. 可配置表单字段权限和按钮
     */
    private static class StartUserNodeConvert implements NodeConvert {

        @Override
        public UserTask convert(BpmSimpleModelNodeVO node) {
            // 创建用户任务
            UserTask userTask = new UserTask();
            userTask.setId(node.getId());
            userTask.setName(node.getName());

            // 设置审批类型为"人工审批"（虽然会自动通过，但类型标记为人工）
            // 人工审批
            addExtensionElement(userTask, USER_TASK_APPROVE_TYPE, BpmUserTaskApproveTypeEnum.USER.getType());

            // 设置候选人策略为"发起人自己"
            // 这样任务会自动分配给流程发起人
            // 候选人策略为发起人自己
            addCandidateElements(BpmTaskCandidateStrategyEnum.START_USER.getStrategy(), null, userTask);

            // 添加表单字段权限配置
            // 控制哪些字段可编辑、只读、隐藏
            // 添加表单字段权限属性元素
            addFormFieldsPermission(node.getFieldsPermission(), userTask);

            // 添加按钮配置（如：提交、保存草稿等）
            // 添加操作按钮配置属性元素
            addButtonsSetting(node.getButtonsSetting(), userTask);

            // 使用自动通过策略
            // SKIP 策略：当任务分配给发起人时，自动通过，不需要人工点击
            // 这样发起人填写完表单提交后，这个节点会自动完成
            // 使用自动通过策略
            // TODO @芋艿 复用了SKIP， 是否需要新加一个策略；TODO @芋艿：【回复】是不是应该类似飞书，搞个草稿状态。待定；还有一种策略，不标记自动通过，而是首次发起后，第一个节点，自动通过；
            addAssignStartUserHandlerType(BpmUserTaskAssignStartUserHandlerTypeEnum.SKIP.getType(), userTask);

            return userTask;
        }

        @Override
        public BpmSimpleModelNodeTypeEnum getType() {
            return BpmSimpleModelNodeTypeEnum.START_USER_NODE;
        }

    }

    /**
     * 审批节点转换器
     * <p>
     * Simple Model: APPROVE_NODE
     * BPMN 元素: UserTask（用户任务） + BoundaryEvent（边界事件，可选）
     * <p>
     * 作用：这是最常用的节点，代表需要人工审批的环节
     * <p>
     * 核心功能：
     * 1. 候选人配置：支持多种策略（角色、部门、用户、表达式等）
     * 2. 多实例审批：会签（ALL）、或签（ANY）、顺序审批（SEQUENTIAL）、按比例（RATIO）
     * 3. 超时处理：配置超时时间和超时动作（提醒、自动通过、自动拒绝）
     * 4. 拒绝处理：配置拒绝后的流程走向
     * 5. 特殊情况处理：审批人为空、审批人=发起人等
     * 6. 任务监听器：CREATE、ASSIGNMENT、COMPLETE 事件
     */
    private static class ApproveNodeConvert implements NodeConvert {

        @Override
        public List<FlowElement> convertList(BpmSimpleModelNodeVO node) {
            List<FlowElement> flowElements = new ArrayList<>(2);

            // 1. 构建核心的用户任务
            // 1. 构建用户任务
            UserTask userTask = buildBpmnUserTask(node);
            flowElements.add(userTask);

            // 2. 如果配置了超时处理，添加定时器边界事件
            // Timer Boundary Event（定时器边界事件）附加在用户任务上
            // 当任务超时时触发，可以执行提醒、自动通过等动作
            // 2. 添加用户任务的 Timer Boundary Event, 用于任务的审批超时处理
            if (node.getTimeoutHandler() != null && node.getTimeoutHandler().getEnable()) {
                BoundaryEvent boundaryEvent = buildUserTaskTimeoutBoundaryEvent(userTask, node.getTimeoutHandler());
                flowElements.add(boundaryEvent);
            }

            return flowElements;
        }

        @Override
        public BpmSimpleModelNodeTypeEnum getType() {
            return BpmSimpleModelNodeTypeEnum.APPROVE_NODE;
        }

        /**
         * 添加 UserTask 用户的审批超时 BoundaryEvent 事件
         *
         * @param userTask       审批任务
         * @param timeoutHandler 超时处理器
         * @return BoundaryEvent 超时事件
         */
        private BoundaryEvent buildUserTaskTimeoutBoundaryEvent(UserTask userTask,
                                                                BpmSimpleModelNodeVO.TimeoutHandler timeoutHandler) {
            // 1. 创建定时器边界事件
            // 1. 创建 Timeout Boundary Event
            String timeCycle = null;

            // 如果是"提醒"类型，且配置了多次提醒，则使用重复定时器
            // 格式：R3/PT1H 表示每1小时提醒一次，最多3次
            if (Objects.equals(BpmUserTaskTimeoutHandlerTypeEnum.REMINDER.getType(), timeoutHandler.getType()) &&
                    timeoutHandler.getMaxRemindCount() != null && timeoutHandler.getMaxRemindCount() > 1) {
                timeCycle = String.format("R%d/%s",
                        timeoutHandler.getMaxRemindCount(), timeoutHandler.getTimeDuration());
            }

            // 构建边界事件
            // timeDuration: 超时时长（如 PT1H = 1小时）
            // timeCycle: 重复周期（如 R3/PT1H = 每小时重复，共3次）
            BoundaryEvent boundaryEvent = buildTimeoutBoundaryEvent(userTask, BpmBoundaryEventTypeEnum.USER_TASK_TIMEOUT.getType(),
                    timeoutHandler.getTimeDuration(), timeCycle, null);

            // 2. 添加超时执行动作的扩展属性
            // 超时后的处理类型：REMINDER（提醒）、AUTO_APPROVE（自动通过）、AUTO_REJECT（自动拒绝）
            // 2 添加超时执行动作元素
            addExtensionElement(boundaryEvent, USER_TASK_TIMEOUT_HANDLER_TYPE, timeoutHandler.getType());
            return boundaryEvent;
        }

        /**
         * 构建 BPMN 用户任务
         * <p>
         * 这是审批节点转换的核心方法，处理所有审批相关的配置
         *
         * @param node 简单模型节点
         * @return 配置完整的 UserTask
         */
        private UserTask buildBpmnUserTask(BpmSimpleModelNodeVO node) {
            UserTask userTask = new UserTask();
            userTask.setId(node.getId());
            userTask.setName(node.getName());

            // ========== 第一步：设置审批类型 ==========
            // 审批类型：USER（人工审批）、AUTO_APPROVE（自动通过）、AUTO_REJECT（自动拒绝）
            // 如果不是审批人节点，则直接返回
            addExtensionElement(userTask, USER_TASK_APPROVE_TYPE, node.getApproveType());

            // 如果不是"人工审批"类型，则无需配置后续的候选人、多实例等
            // 直接返回基础的 UserTask
            if (ObjectUtil.notEqual(node.getApproveType(), BpmUserTaskApproveTypeEnum.USER.getType())) {
                return userTask;
            }

            // ========== 第二步：配置候选人 ==========
            // 候选人策略：角色、部门、用户、岗位、用户组、表达式等
            // 候选人参数：根据策略类型，存储具体的ID或表达式
            // 添加候选人元素
            addCandidateElements(node.getCandidateStrategy(), node.getCandidateParam(), userTask);

            // ========== 第三步：配置表单字段权限 ==========
            // 控制审批时哪些字段可编辑、只读、隐藏
            // 例如：{"field1": "WRITE", "field2": "READ", "field3": "NONE"}
            // 添加表单字段权限属性元素
            addFormFieldsPermission(node.getFieldsPermission(), userTask);

            // ========== 第四步：配置操作按钮 ==========
            // 配置审批页面显示哪些按钮：通过、拒绝、转办、加签等
            // 添加操作按钮配置属性元素
            addButtonsSetting(node.getButtonsSetting(), userTask);

            // ========== 第五步：处理多实例（核心功能）==========
            // 当有多个审批人时，如何处理：
            // - ANY（或签）：任意一人通过即可
            // - ALL（会签）：所有人都要通过
            // - SEQUENTIAL（顺序审批）：按顺序一个个审批
            // - RATIO（按比例）：通过人数达到指定比例即可
            // - RANDOM（随机选一人）：从候选人中随机分配给一人
            // 处理多实例（审批方式）
            processMultiInstanceLoopCharacteristics(node.getApproveMethod(), node.getApproveRatio(), userTask);

            // ========== 第六步：配置拒绝处理 ==========
            // 当审批被拒绝时，流程如何处理：
            // - FINISH_PROCESS：直接结束流程
            // - RETURN_USER_TASK：退回到指定节点
            // - RETURN_PRE_USER_TASK：退回到上一个审批节点
            // 添加任务被拒绝的处理元素
            addTaskRejectElements(node.getRejectHandler(), userTask);

            // ========== 第七步：处理特殊情况 ==========
            // 情况1：审批人与发起人相同
            // 策略：SKIP（自动跳过）、TRANSFER_DEPT_LEADER（转给部门负责人）等
            // 添加用户任务的审批人与发起人相同时的处理元素
            addAssignStartUserHandlerType(node.getAssignStartUserHandlerType(), userTask);

            // 情况2：审批人为空
            // 策略：APPROVE（自动通过）、REJECT（自动拒绝）、TRANSFER_DEPT_LEADER（转给部门负责人）
            // 添加用户任务的空处理元素
            addAssignEmptyHandlerType(node.getAssignEmptyHandler(), userTask);

            // ========== 第八步：设置截止时间 ==========
            // 审批任务的截止时间（DueDate），用于任务提醒和超时处理
            //  设置审批任务的截止时间
            if (node.getTimeoutHandler() != null && node.getTimeoutHandler().getEnable()) {
                userTask.setDueDate(node.getTimeoutHandler().getTimeDuration());
            }

            // ========== 第九步：设置任务监听器 ==========
            // 监听任务生命周期事件：CREATE、ASSIGNMENT、COMPLETE
            // 可在这些时机触发自定义逻辑（如：发送通知、记录日志等）
            // 设置监听器
            addUserTaskListener(node, userTask);

            // ========== 第十步：其他配置 ==========
            // 是否需要手写签名
            // 添加是否需要签名
            addSignEnable(node.getSignEnable(), userTask);

            // 审批意见是否必填
            // 审批意见
            addReasonRequire(node.getReasonRequire(), userTask);

            // 节点类型标记（用于运行时识别）
            // 节点类型
            addNodeType(node.getType(), userTask);

            // ========== 第十一步：跳过表达式 ==========
            // 满足条件时自动跳过此节点，不需要审批
            // 例如：${amount < 1000} 表示金额小于1000时自动跳过
            // 添加跳过表达式
            if (StrUtil.isNotEmpty(node.getSkipExpression())) {
                userTask.setSkipExpression(node.getSkipExpression());
            }

            return userTask;
        }

        /**
         * 添加用户任务监听器
         * <p>
         * 监听器可以在任务生命周期的不同阶段触发自定义逻辑
         * <p>
         * 支持的事件类型：
         * - CREATE（任务创建时）：任务刚被创建，还未分配给具体人员
         * - ASSIGNMENT（任务分配时）：任务被分配给具体的审批人
         * - COMPLETE（任务完成时）：审批人完成审批操作
         * <p>
         * 典型应用场景：
         * - CREATE 事件：发送待办通知、记录创建日志
         * - ASSIGNMENT 事件：发送任务分配通知、更新任务状态
         * - COMPLETE 事件：记录审批结果、触发下一步流程
         *
         * @param node     简单模型节点
         * @param userTask 用户任务
         */
        private void addUserTaskListener(BpmSimpleModelNodeVO node, UserTask userTask) {
            List<FlowableListener> flowableListeners = new ArrayList<>(3);

            // 1. CREATE 事件监听器（任务创建时触发）
            if (node.getTaskCreateListener() != null
                    && Boolean.TRUE.equals(node.getTaskCreateListener().getEnable())) {
                FlowableListener flowableListener = new FlowableListener();
                flowableListener.setEvent(TaskListener.EVENTNAME_CREATE);  // 监听 CREATE 事件
                flowableListener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
                flowableListener.setImplementation(BpmUserTaskListener.DELEGATE_EXPRESSION);  // 委托给监听器 Bean
                addListenerConfig(flowableListener, node.getTaskCreateListener());  // 添加监听器配置参数
                flowableListeners.add(flowableListener);
            }

            // 2. ASSIGNMENT 事件监听器（任务分配时触发）
            if (node.getTaskAssignListener() != null
                    && Boolean.TRUE.equals(node.getTaskAssignListener().getEnable())) {
                FlowableListener flowableListener = new FlowableListener();
                flowableListener.setEvent(TaskListener.EVENTNAME_ASSIGNMENT);  // 监听 ASSIGNMENT 事件
                flowableListener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
                flowableListener.setImplementation(BpmUserTaskListener.DELEGATE_EXPRESSION);
                addListenerConfig(flowableListener, node.getTaskAssignListener());
                flowableListeners.add(flowableListener);
            }

            // 3. COMPLETE 事件监听器（任务完成时触发）
            if (node.getTaskCompleteListener() != null
                    && Boolean.TRUE.equals(node.getTaskCompleteListener().getEnable())) {
                FlowableListener flowableListener = new FlowableListener();
                flowableListener.setEvent(TaskListener.EVENTNAME_COMPLETE);  // 监听 COMPLETE 事件
                flowableListener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
                flowableListener.setImplementation(BpmUserTaskListener.DELEGATE_EXPRESSION);
                addListenerConfig(flowableListener, node.getTaskCompleteListener());
                flowableListeners.add(flowableListener);
            }

            // 如果有监听器，则设置到 UserTask
            if (CollUtil.isNotEmpty(flowableListeners)) {
                userTask.setTaskListeners(flowableListeners);
            }
        }

        /**
         * 处理多实例循环特性
         * <p>
         * 多实例（Multi-Instance）：当一个审批节点有多个审批人时，如何处理审批逻辑
         * <p>
         * 支持的审批方式：
         * 1. RANDOM（随机）：从多个候选人中随机选一人审批，不使用多实例
         * 2. ANY（或签）：所有人并行审批，任意一人通过即可
         * 3. SEQUENTIAL（顺序）：按顺序一个个审批，所有人都要审批
         * 4. RATIO（按比例）：并行审批，达到指定通过比例即可（如 60% 通过）
         *
         * @param approveMethod 审批方式枚举值
         * @param approveRatio  通过比例（仅 RATIO 方式使用，如 60 表示 60%）
         * @param userTask      用户任务
         */
        private void processMultiInstanceLoopCharacteristics(Integer approveMethod, Integer approveRatio, UserTask userTask) {
            BpmUserTaskApproveMethodEnum approveMethodEnum = BpmUserTaskApproveMethodEnum.valueOf(approveMethod);
            Assert.notNull(approveMethodEnum, "审批方式({})不能为空", approveMethodEnum);

            // 添加审批方式的扩展属性（用于运行时识别）
            // 添加审批方式的扩展属性
            addExtensionElement(userTask, USER_TASK_APPROVE_METHOD, approveMethod);

            // 随机审批不需要设置多实例，直接返回
            // 因为随机模式只会从候选人中选一人，不涉及多人审批
            if (approveMethodEnum == BpmUserTaskApproveMethodEnum.RANDOM) {
                // 随机审批，不需要设置多实例属性
                return;
            }

            // ========== 创建多实例循环特性对象 ==========
            // 处理多实例审批方式
            MultiInstanceLoopCharacteristics multiInstanceCharacteristics = new MultiInstanceLoopCharacteristics();

            // 设置集合变量（必填，否则 Flowable 校验报错）
            // 实际使用时，候选人列表会动态计算，这里只是占位
            // 设置 collectionVariable。本系统用不到，仅仅为了 Flowable 校验不报错
            multiInstanceCharacteristics.setInputDataItem("${coll_userList}");

            // ========== 根据不同的审批方式设置多实例参数 ==========

            if (approveMethodEnum == BpmUserTaskApproveMethodEnum.ANY) {
                // ===== 或签（ANY）模式 =====
                // 完成条件：任意一人通过即完成
                // 执行方式：并行（所有人同时收到任务）
                multiInstanceCharacteristics.setCompletionCondition(approveMethodEnum.getCompletionCondition());
                multiInstanceCharacteristics.setSequential(false);  // 并行执行

            } else if (approveMethodEnum == BpmUserTaskApproveMethodEnum.SEQUENTIAL) {
                // ===== 顺序审批（SEQUENTIAL）模式 =====
                // 完成条件：所有人都要审批
                // 执行方式：串行（一个审批完才到下一个）
                // 循环基数：每次只分配给 1 人
                multiInstanceCharacteristics.setCompletionCondition(approveMethodEnum.getCompletionCondition());
                multiInstanceCharacteristics.setSequential(true);   // 串行执行
                multiInstanceCharacteristics.setLoopCardinality("1");  // 每次只有1人审批

            } else if (approveMethodEnum == BpmUserTaskApproveMethodEnum.RATIO) {
                // ===== 按比例通过（RATIO）模式 =====
                // 完成条件：通过人数达到指定比例
                // 执行方式：并行
                // 例如：3人审批，比例60%，则至少2人通过即可
                Assert.notNull(approveRatio, "通过比例不能为空");
                multiInstanceCharacteristics.setCompletionCondition(
                        String.format(approveMethodEnum.getCompletionCondition(),
                                String.format("%.2f", approveRatio / 100D)));  // 将百分比转为小数（60 -> 0.60）
                multiInstanceCharacteristics.setSequential(false);  // 并行执行
            }

            // 将多实例特性设置到 UserTask
            userTask.setLoopCharacteristics(multiInstanceCharacteristics);
        }

    }

    /**
     * 办理人节点转换器
     * <p>
     * Simple Model: TRANSACTOR_NODE
     * BPMN 元素: UserTask（用户任务） + BoundaryEvent（边界事件，可选）
     * <p>
     * 作用：与审批节点类似，但语义上更偏向于"办理"而非"审批"
     * 典型场景：填写信息、提交资料、完成某项工作等
     * <p>
     * 实现：直接继承 ApproveNodeConvert，复用所有审批节点的功能
     */
    private static class TransactorNodeConvert extends ApproveNodeConvert {

        @Override
        public BpmSimpleModelNodeTypeEnum getType() {
            return BpmSimpleModelNodeTypeEnum.TRANSACTOR_NODE;
        }

    }

    /**
     * 抄送节点转换器
     * <p>
     * Simple Model: COPY_NODE
     * BPMN 元素: ServiceTask（服务任务）
     * <p>
     * 作用：将流程进度抄送给相关人员，让他们知晓流程状态
     * 特点：
     * 1. 不需要人工操作，流程自动执行
     * 2. 使用 ServiceTask 而非 UserTask
     * 3. 委托给 BpmCopyTaskDelegate 处理抄送逻辑
     * 4. 可配置抄送人和字段权限（抄送人能看到哪些字段）
     */
    private static class CopyNodeConvert implements NodeConvert {

        @Override
        public ServiceTask convert(BpmSimpleModelNodeVO node) {
            // 创建服务任务
            ServiceTask serviceTask = new ServiceTask();
            serviceTask.setId(node.getId());
            serviceTask.setName(node.getName());

            // 设置委托表达式，指向抄送任务处理器
            // 使用 Spring Bean 表达式，运行时会调用 BpmCopyTaskDelegate
            serviceTask.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
            serviceTask.setImplementation("${" + BpmCopyTaskDelegate.BEAN_NAME + "}");

            // 添加抄送候选人配置
            // 支持多种策略：角色、部门、用户等
            // 添加抄送候选人元素
            addCandidateElements(node.getCandidateStrategy(), node.getCandidateParam(), serviceTask);

            // 添加表单字段权限
            // 控制抄送人能看到哪些字段
            // 添加表单字段权限属性元素
            addFormFieldsPermission(node.getFieldsPermission(), serviceTask);

            return serviceTask;
        }

        @Override
        public BpmSimpleModelNodeTypeEnum getType() {
            return BpmSimpleModelNodeTypeEnum.COPY_NODE;
        }

    }

    /**
     * 条件分支节点转换器（排他网关）
     * <p>
     * Simple Model: CONDITION_BRANCH_NODE
     * BPMN 元素: ExclusiveGateway（排他网关）
     * <p>
     * 作用：根据条件选择唯一一条分支执行（类似 if-else）
     * 特点：
     * 1. 只会走满足条件的第一条分支
     * 2. 如果没有分支满足条件，则走默认分支
     * 3. 必须配置一个默认分支（兜底）
     * <p>
     * 示例：
     * 请假天数 <= 3 → 走分支1（部门主管审批）
     * 请假天数 > 3  → 走分支2（总经理审批）
     * 默认         → 走分支2
     */
    private static class ConditionBranchNodeConvert implements NodeConvert {

        @Override
        public ExclusiveGateway convert(BpmSimpleModelNodeVO node) {
            // 创建排他网关
            ExclusiveGateway exclusiveGateway = new ExclusiveGateway();
            exclusiveGateway.setId(node.getId());
            // TODO @jason：setName

            // 设置默认序列流（默认分支）
            // 当所有分支条件都不满足时，走默认分支
            // 设置默认的序列流（条件）
            BpmSimpleModelNodeVO defaultSeqFlow = CollUtil.findOne(node.getConditionNodes(),
                    item -> BooleanUtil.isTrue(item.getConditionSetting().getDefaultFlow()));
            Assert.notNull(defaultSeqFlow, "条件分支节点({})的默认序列流不能为空", node.getId());

            // 设置默认流的 ID
            exclusiveGateway.setDefaultFlow(defaultSeqFlow.getId());

            return exclusiveGateway;
        }

        @Override
        public BpmSimpleModelNodeTypeEnum getType() {
            return BpmSimpleModelNodeTypeEnum.CONDITION_BRANCH_NODE;
        }

    }

    /**
     * 并行分支节点转换器
     * <p>
     * Simple Model: PARALLEL_BRANCH_NODE
     * BPMN 元素: InclusiveGateway（包容网关） × 2（分叉 + 汇聚）
     * <p>
     * 作用：所有分支同时执行，全部完成后才继续
     * 特点：
     * 1. 不管条件，所有分支都会执行
     * 2. 需要等待所有分支都完成
     * 3. 程序自动创建分叉网关和汇聚网关
     * 4. 使用包容网关实现，所有出口条件强制为 true
     * <p>
     * 注意：
     * 并行分支使用包容网关实现，原因是解决某些场景下的并行问题
     * 所有出口条件表达式的值都设为 true
     *
     * @see ConditionNodeConvert#buildSequenceFlow
     */
    private static class ParallelBranchNodeConvert implements NodeConvert {

        /**
         * 并行分支使用包容网关。需要设置所有出口条件表达式的值为 true 。原因是，解决 https://t.zsxq.com/m6GXh 反馈问题
         *
         * @see {@link ConditionNodeConvert#buildSequenceFlow}
         */
        @Override
        public List<InclusiveGateway> convertList(BpmSimpleModelNodeVO node) {
            // 创建分叉网关（Fork Gateway）
            // 流程执行到这里时，会同时激活所有分支
            InclusiveGateway inclusiveGateway = new InclusiveGateway();
            inclusiveGateway.setId(node.getId());
            // TODO @jason：setName

            // 创建汇聚网关（Join Gateway）
            // 程序自动创建，前端不需要传入
            // 所有分支执行完后，在这里汇聚
            // 合并网关 由程序创建，前端不需要传入
            InclusiveGateway joinParallelGateway = new InclusiveGateway();
            joinParallelGateway.setId(buildGatewayJoinId(node.getId()));  // ID 格式：原网关ID + "_join"
            // TODO @jason：setName

            // 返回两个网关：分叉 + 汇聚
            return CollUtil.newArrayList(inclusiveGateway, joinParallelGateway);
        }

        @Override
        public BpmSimpleModelNodeTypeEnum getType() {
            return BpmSimpleModelNodeTypeEnum.PARALLEL_BRANCH_NODE;
        }

    }

    /**
     * 包容分支节点转换器
     * <p>
     * Simple Model: INCLUSIVE_BRANCH_NODE
     * BPMN 元素: InclusiveGateway（包容网关） × 2（分叉 + 汇聚）
     * <p>
     * 作用：执行所有满足条件的分支，如果都不满足则走默认分支
     * 特点：
     * 1. 可以同时执行多条分支（与并行分支的区别：有条件判断）
     * 2. 只执行满足条件的分支
     * 3. 等待所有激活的分支完成后才继续
     * 4. 必须配置默认分支
     * <p>
     * 示例：
     * 金额 > 10000  → 执行分支1（财务审批）
     * 涉及合同     → 执行分支2（法务审批）
     * 如果两个条件都满足，则两个分支都执行
     */
    private static class InclusiveBranchNodeConvert implements NodeConvert {

        @Override
        public List<InclusiveGateway> convertList(BpmSimpleModelNodeVO node) {
            // 创建分叉包容网关
            InclusiveGateway inclusiveGateway = new InclusiveGateway();
            inclusiveGateway.setId(node.getId());

            // 设置默认序列流
            // 当所有分支条件都不满足时，走默认分支
            // 设置默认的序列流（条件）
            BpmSimpleModelNodeVO defaultSeqFlow = CollUtil.findOne(node.getConditionNodes(),
                    item -> BooleanUtil.isTrue(item.getConditionSetting().getDefaultFlow()));
            Assert.notNull(defaultSeqFlow, "包容分支节点({})的默认序列流不能为空", node.getId());
            inclusiveGateway.setDefaultFlow(defaultSeqFlow.getId());
            // TODO @jason：setName

            // 创建汇聚包容网关
            // 等待所有激活的分支完成
            // 并行聚合网关由程序创建，前端不需要传入
            InclusiveGateway joinInclusiveGateway = new InclusiveGateway();
            joinInclusiveGateway.setId(buildGatewayJoinId(node.getId()));
            // TODO @jason：setName

            // 返回两个网关
            return CollUtil.newArrayList(inclusiveGateway, joinInclusiveGateway);
        }

        @Override
        public BpmSimpleModelNodeTypeEnum getType() {
            return BpmSimpleModelNodeTypeEnum.INCLUSIVE_BRANCH_NODE;
        }

    }

    /**
     * 条件节点转换器
     * <p>
     * Simple Model: CONDITION_NODE
     * BPMN 元素: 无（不直接转换为 BPMN 元素）
     * <p>
     * 特殊说明：
     * 条件节点不是一个独立的 BPMN 元素，而是分支网关的"出口连线"
     * 它的作用是：
     * 1. 存储分支的条件表达式
     * 2. 指向该分支的第一个节点
     * <p>
     * 转换过程：
     * - 条件节点本身不转换为 FlowElement
     * - 而是在建立连线时，将条件表达式附加到 SequenceFlow 上
     * - 参见 traverseBranchNodeToBuildSequenceFlow 方法
     */
    public static class ConditionNodeConvert implements NodeConvert {

        @Override
        public List<? extends FlowElement> convertList(BpmSimpleModelNodeVO node) {
            // 条件节点不转换为 FlowElement，而是在建立连线时使用
            // 正常流程不会调用此方法，如果调用说明逻辑有问题
            // 原因是：正常情况下，它不会被调用到
            throw new UnsupportedOperationException("条件节点不支持转换");
        }

        @Override
        public BpmSimpleModelNodeTypeEnum getType() {
            return BpmSimpleModelNodeTypeEnum.CONDITION_NODE;
        }

        /**
         * 构建分支的 SequenceFlow（带条件表达式）
         * <p>
         * 这是条件节点的核心方法，用于创建从网关到目标节点的连线
         *
         * @param sourceId   源节点 ID（通常是网关 ID）
         * @param targetId   目标节点 ID（分支的第一个节点或汇聚点）
         * @param nodeType   分支网关类型
         * @param node       条件节点（包含条件表达式）
         * @return 带条件表达式的 SequenceFlow
         */
        public static SequenceFlow buildSequenceFlow(String sourceId, String targetId,
                                                     BpmSimpleModelNodeTypeEnum nodeType, BpmSimpleModelNodeVO node) {
            String conditionExpression;

            // 并行分支的特殊处理
            // 并行分支使用包容网关实现，所有出口条件都强制为 true
            // 这样可以确保所有分支都被激活
            // 并行分支，使用包容网关实现，强制设置条件表达式为 true
            if (BpmSimpleModelNodeTypeEnum.PARALLEL_BRANCH_NODE == nodeType) {
                conditionExpression ="${true}";  // 强制为 true，所有分支都走
            } else {
                // 其他分支类型：根据条件配置构建表达式
                // 条件分支：根据条件判断，只走满足条件的分支
                // 包容分支：根据条件判断，走所有满足条件的分支
                conditionExpression = buildConditionExpression(node.getConditionSetting());
            }

            // 构建 SequenceFlow
            // node.getId()：条件节点的 ID，用作连线的 ID
            // node.getName()：条件节点的名称，显示在流程图上
            // conditionExpression：条件表达式，如 ${amount > 1000}
            return buildBpmnSequenceFlow(sourceId, targetId, node.getId(), node.getName(), conditionExpression);
        }
    }

    /**
     * 构造条件表达式（ConditionSetting 版本）
     * <p>
     * 将前端传递的条件配置转换为 Flowable 可识别的 EL 表达式
     *
     * @param conditionSetting 条件设置对象
     * @return EL 表达式字符串，如：${amount > 1000}
     */
    public static String buildConditionExpression(BpmSimpleModelNodeVO.ConditionSetting conditionSetting) {
        if (conditionSetting == null) {
            return null;
        }
        return buildConditionExpression(conditionSetting.getConditionType(), conditionSetting.getConditionExpression(),
                conditionSetting.getConditionGroups());
    }

    /**
     * 构造条件表达式（RouterSetting 版本）
     * <p>
     * 用于路由分支节点的条件表达式构建
     *
     * @param routerSetting 路由设置对象
     * @return EL 表达式字符串
     */
    public static String buildConditionExpression(BpmSimpleModelNodeVO.RouterSetting routerSetting) {
        return buildConditionExpression(routerSetting.getConditionType(), routerSetting.getConditionExpression(),
                routerSetting.getConditionGroups());
    }

    /**
     * 构造条件表达式（核心方法）
     * <p>
     * 支持两种条件类型：
     * 1. EXPRESSION（表达式）：直接使用用户编写的表达式
     * 2. RULE（规则构建器）：根据可视化配置的规则动态生成表达式
     * <p>
     * 规则构建器示例：
     * 条件组1：(amount > 1000 && dept == "财务部")
     * 条件组2：(level == "高级")
     * 最终表达式：${(amount > 1000 && dept == "财务部") || (level == "高级")}
     *
     * @param conditionType       条件类型（1=表达式，2=规则）
     * @param conditionExpression 直接表达式（仅类型1使用）
     * @param conditionGroups     条件组（仅类型2使用）
     * @return EL 表达式字符串
     */
    public static String buildConditionExpression(Integer conditionType, String conditionExpression, ConditionGroups conditionGroups) {
        BpmSimpleModeConditionTypeEnum conditionTypeEnum = BpmSimpleModeConditionTypeEnum.valueOf(conditionType);

        // ========== 类型1：直接使用表达式 ==========
        if (conditionTypeEnum == BpmSimpleModeConditionTypeEnum.EXPRESSION) {
            // 用户直接编写的 EL 表达式，如：${amount > 1000}
            return conditionExpression;
        }

        // ========== 类型2：规则构建器 ==========
        if (conditionTypeEnum == BpmSimpleModeConditionTypeEnum.RULE) {
            if (conditionGroups == null || CollUtil.isEmpty(conditionGroups.getConditions())) {
                return null;
            }

            // 遍历每个条件组，构建表达式
            List<String> strConditionGroups = convertList(conditionGroups.getConditions(), item -> {
                if (CollUtil.isEmpty(item.getRules())) {
                    return "";
                }

                // ===== 构造单个条件组内的规则表达式 =====
                // 例如：条件组包含 [age > 18, city == "北京"]
                // 构造规则表达式
                List<String> list = convertList(item.getRules(), (rule) -> {
                    // 处理右侧值：如果是数字，直接使用；否则加引号
                    // 例如：1000 → 1000，"北京" → "\"北京\""
                    String rightSide = NumberUtil.isNumber(rule.getRightSide()) ? rule.getRightSide()
                            : "\"" + rule.getRightSide() + "\""; // 如果非数值类型加引号

                    // 构建单个规则的表达式
                    // 格式：vars:getOrDefault(变量名, null) 操作符 var:convertByType(变量名, 值)
                    // 例如：vars:getOrDefault(age, null) > var:convertByType(age, 18)
                    //
                    // vars:getOrDefault - 从变量中获取值，不存在则返回 null
                    // var:convertByType - 将右侧值转换为与左侧变量相同的类型，用于比较
                    return String.format(" vars:getOrDefault(%s, null) %s var:convertByType(%s,%s) ",
                            rule.getLeftSide(), // 左侧：读取变量
                            rule.getOpCode(), // 中间：操作符，比较
                            rule.getLeftSide(), rightSide); // 右侧：转换变量，VariableConvertByTypeExpressionFunction
                });

                // 用 && 或 || 连接条件组内的规则
                // 构造条件组的表达式
                Boolean and = item.getAnd();
                return "(" + CollUtil.join(list, and ? " && " : " || ") + ")";
            });

            // 用 && 或 || 连接多个条件组，并包装为 ${...}
            // 例如：${(age > 18 && city == "北京") || (level == "高级")}
            return String.format("${%s}", CollUtil.join(strConditionGroups, conditionGroups.getAnd() ? " && " : " || "));
        }

        return null;
    }

    /**
     * 延时定时器节点转换器
     * <p>
     * Simple Model: DELAY_TIMER_NODE
     * BPMN 元素: ReceiveTask（接收任务） + BoundaryEvent（定时器边界事件）
     * <p>
     * 作用：让流程在此节点等待一段时间后自动继续
     * 使用场景：
     * - 等待3天后自动流转到下一节点
     * - 等到指定日期（如2024-12-31 18:00:00）后继续
     * <p>
     * 实现原理：
     * 1. ReceiveTask - 创建一个接收任务，流程会在此"卡住"
     * 2. Timer Boundary Event - 附加在接收任务上的定时器
     * 3. 当定时器到期时，触发边界事件，流程继续
     * <p>
     * 支持两种延时类型：
     * 1. FIXED_TIME_DURATION（固定时长）：如 PT1H（1小时）、P3D（3天）
     * 2. FIXED_DATE_TIME（固定日期时间）：如 2024-12-31T18:00:00
     */
    public static class DelayTimerNodeConvert implements NodeConvert {

        @Override
        public List<FlowElement> convertList(BpmSimpleModelNodeVO node) {
            List<FlowElement> flowElements = new ArrayList<>(2);

            // 1. 构建接收任务（ReceiveTask）
            // 接收任务会"卡住"流程，等待外部信号或定时器触发
            // 1. 构建接收任务，通过接收任务可卡住节点
            ReceiveTask receiveTask = new ReceiveTask();
            receiveTask.setId(node.getId());
            receiveTask.setName(node.getName());
            flowElements.add(receiveTask);

            // 2. 添加定时器边界事件
            // 边界事件附加在接收任务上，到期后触发流程继续
            // 2. 添加接收任务的 Timer Boundary Event
            if (node.getDelaySetting() != null) {
                BoundaryEvent boundaryEvent = null;

                // 情况1：固定日期时间
                // 例如：等到 2024-12-31 18:00:00
                if (node.getDelaySetting().getDelayType().equals(BpmDelayTimerTypeEnum.FIXED_DATE_TIME.getType())) {
                    boundaryEvent = buildTimeoutBoundaryEvent(receiveTask, BpmBoundaryEventTypeEnum.DELAY_TIMER_TIMEOUT.getType(),
                            null, null, node.getDelaySetting().getDelayTime());  // timeDate 参数

                // 情况2：固定时长
                // 例如：等待 PT1H（1小时）、P3D（3天）
                } else if (node.getDelaySetting().getDelayType().equals(BpmDelayTimerTypeEnum.FIXED_TIME_DURATION.getType())) {
                    boundaryEvent = buildTimeoutBoundaryEvent(receiveTask, BpmBoundaryEventTypeEnum.DELAY_TIMER_TIMEOUT.getType(),
                            node.getDelaySetting().getDelayTime(), null, null);  // timeDuration 参数

                } else {
                    throw new UnsupportedOperationException("不支持的延迟类型：" + node.getDelaySetting());
                }

                flowElements.add(boundaryEvent);
            }

            return flowElements;
        }

        @Override
        public BpmSimpleModelNodeTypeEnum getType() {
            return BpmSimpleModelNodeTypeEnum.DELAY_TIMER_NODE;
        }
    }

    /**
     * 触发器节点转换器
     * <p>
     * Simple Model: TRIGGER_NODE
     * BPMN 元素: ServiceTask（服务任务） + ReceiveTask（接收任务，可选）
     * <p>
     * 作用：在流程中触发外部动作，如发送 HTTP 请求、更新表单等
     * <p>
     * 支持的触发器类型：
     * 1. HTTP_CALLBACK（HTTP 回调）：
     *    - 发送 HTTP 请求
     *    - 等待外部系统回调通知
     *    - 需要附加 ReceiveTask 等待回调
     * 2. FORM_UPDATE（表单更新）：
     *    - 更新流程表单的字段值
     *    - 同步执行，不需要等待
     * <p>
     * 实现机制：
     * - ServiceTask：执行触发器逻辑（委托给 BpmTriggerTaskDelegate）
     * - ReceiveTask：等待 HTTP 回调（仅 HTTP_CALLBACK 类型）
     * - 流程走向：ServiceTask → ReceiveTask（等待） → 下一个节点
     */
    public static class TriggerNodeConvert implements NodeConvert {

        @Override
        public List<? extends FlowElement> convertList(BpmSimpleModelNodeVO node) {
            Assert.notNull(node.getTriggerSetting(), "触发器节点设置不能为空");
            List<FlowElement> flowElements = new ArrayList<>(2);

            // ========== 第一步：处理 HTTP 回调类型的特殊需求 ==========
            // HTTP 回调需要附加一个等待节点
            // 流程：发起 HTTP 请求 → 等待外部回调 → 继续流程
            // HTTP 回调请求。需要附加一个 ReceiveTask、发起请求后、等待回调执行
            if (BpmTriggerTypeEnum.HTTP_CALLBACK.getType().equals(node.getTriggerSetting().getType())) {
                Assert.notNull(node.getTriggerSetting().getHttpRequestSetting(), "触发器 HTTP 回调请求设置不能为空");

                // 创建接收任务，用于等待 HTTP 回调
                ReceiveTask receiveTask = new ReceiveTask();
                receiveTask.setId("Activity_" + IdUtil.fastUUID());  // 生成唯一 ID
                receiveTask.setName("HTTP 回调");
                node.setAttachNodeId(receiveTask.getId());  // 设置附加节点 ID，用于后续建立连线
                flowElements.add(receiveTask);

                // 设置 callbackTaskDefineKey
                // 外部系统回调时，需要通过这个 Key 找到对应的接收任务
                // 重要：设置 callbackTaskDefineKey，用于 HTTP 回调
                node.getTriggerSetting().getHttpRequestSetting().setCallbackTaskDefineKey(receiveTask.getId());
            }

            // ========== 第二步：创建触发器服务任务 ==========
            // 所有触发器都使用 ServiceTask 来执行
            // 触发器使用 ServiceTask 来实现
            ServiceTask serviceTask = new ServiceTask();
            serviceTask.setId(node.getId());
            serviceTask.setName(node.getName());

            // 设置委托表达式，指向触发器处理器 Bean
            serviceTask.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
            serviceTask.setImplementation("${" + BpmTriggerTaskDelegate.BEAN_NAME + "}");

            // 添加触发器类型扩展属性
            // 运行时根据类型决定执行哪种触发器逻辑
            addExtensionElement(serviceTask, TRIGGER_TYPE, node.getTriggerSetting().getType());

            // 添加 HTTP 请求配置（如果是 HTTP 类型）
            if (node.getTriggerSetting().getHttpRequestSetting() != null) {
                addExtensionElementJson(serviceTask, TRIGGER_PARAM, node.getTriggerSetting().getHttpRequestSetting());
            }

            // 添加表单更新配置（如果是表单更新类型）
            if (node.getTriggerSetting().getFormSettings() != null) {
                addExtensionElementJson(serviceTask, TRIGGER_PARAM, node.getTriggerSetting().getFormSettings());
            }

            flowElements.add(serviceTask);
            return flowElements;
        }

        @Override
        public BpmSimpleModelNodeTypeEnum getType() {
            return BpmSimpleModelNodeTypeEnum.TRIGGER_NODE;
        }
    }

    /**
     * 路由分支节点转换器
     * <p>
     * Simple Model: ROUTER_BRANCH_NODE
     * BPMN 元素: ExclusiveGateway（排他网关）
     * <p>
     * 作用：根据配置的路由规则，将流程路由到指定节点
     * 与 CONDITION_BRANCH_NODE 的区别：
     * 1. CONDITION_BRANCH_NODE：根据条件选择分支，走分支内的节点
     * 2. ROUTER_BRANCH_NODE：根据条件直接跳转到指定节点，不限于分支内
     * <p>
     * 使用场景：
     * - 根据表单字段值，直接跳转到任意节点
     * - 实现复杂的流程跳转逻辑
     * <p>
     * 配置方式：
     * - routerGroups：路由配置列表，每个配置包含条件和目标节点
     * - routerDefaultFlowId：默认路由（无条件匹配时使用）
     */
    public static class RouteBranchNodeConvert implements NodeConvert {

        @Override
        public ExclusiveGateway convert(BpmSimpleModelNodeVO node) {
            // 创建排他网关
            ExclusiveGateway exclusiveGateway = new ExclusiveGateway();
            exclusiveGateway.setId(node.getId());

            // 设置默认序列流 ID
            // 程序自动生成默认流的 ID，用于后续创建默认路由连线
            // 设置默认的序列流（条件）
            node.setRouterDefaultFlowId("Flow_" + IdUtil.fastUUID());
            exclusiveGateway.setDefaultFlow(node.getRouterDefaultFlowId());

            return exclusiveGateway;
        }

        @Override
        public BpmSimpleModelNodeTypeEnum getType() {
            return BpmSimpleModelNodeTypeEnum.ROUTER_BRANCH_NODE;
        }

        /**
         * 构建路由分支的 SequenceFlow
         * <p>
         * 根据路由配置创建到目标节点的连线
         *
         * @param nodeId 路由网关节点 ID
         * @param router 路由配置（包含条件和目标节点）
         * @return 带条件表达式的 SequenceFlow
         */
        public static SequenceFlow buildSequenceFlow(String nodeId, BpmSimpleModelNodeVO.RouterSetting router) {
            // 构建路由条件表达式
            String conditionExpression = SimpleModelUtils.buildConditionExpression(router);

            // 创建连线：路由网关 → 目标节点
            // router.getNodeId()：目标节点 ID（可以是任意节点）
            return buildBpmnSequenceFlow(nodeId, router.getNodeId(), null, null, conditionExpression);
        }

    }

    private static class ChildProcessConvert implements NodeConvert {

        @Override
        public List<FlowElement> convertList(BpmSimpleModelNodeVO node) {
            List<FlowElement> flowElements = new ArrayList<>(2);
            BpmSimpleModelNodeVO.ChildProcessSetting childProcessSetting = node.getChildProcessSetting();
            List<IOParameter> inVariables = childProcessSetting.getInVariables() == null ?
                    new ArrayList<>() : new ArrayList<>(childProcessSetting.getInVariables());
            CallActivity callActivity = new CallActivity();
            callActivity.setId(node.getId());
            callActivity.setName(node.getName());
            callActivity.setCalledElementType("key");
            // 1. 是否异步
            if (node.getChildProcessSetting().getAsync()) {
                callActivity.setAsynchronous(true);
            }

            // 2. 调用的子流程
            callActivity.setCalledElement(childProcessSetting.getCalledProcessDefinitionKey());
            callActivity.setProcessInstanceName(childProcessSetting.getCalledProcessDefinitionName());

            // 3. 是否自动跳过子流程发起节点
            IOParameter ioParameter = new IOParameter();
            ioParameter.setSourceExpression(childProcessSetting.getSkipStartUserNode().toString());
            ioParameter.setTarget(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_SKIP_START_USER_NODE);
            inVariables.add(ioParameter);

            // 4. 【默认需要传递的一些变量】流程状态
            ioParameter = new IOParameter();
            ioParameter.setSource(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS);
            ioParameter.setTarget(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS);
            inVariables.add(ioParameter);

            // 5. 主→子变量传递、子->主变量传递
            callActivity.setInParameters(inVariables);
            if (ArrayUtil.isNotEmpty(childProcessSetting.getOutVariables()) && ObjUtil.notEqual(childProcessSetting.getAsync(), Boolean.TRUE)) {
                callActivity.setOutParameters(childProcessSetting.getOutVariables());
            }

            // 6. 子流程发起人配置
            List<FlowableListener> executionListeners = new ArrayList<>();
            FlowableListener flowableListener = new FlowableListener();
            flowableListener.setEvent(ExecutionListener.EVENTNAME_START);
            flowableListener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
            flowableListener.setImplementation(BpmCallActivityListener.DELEGATE_EXPRESSION);
            FieldExtension fieldExtension = new FieldExtension();
            fieldExtension.setFieldName("listenerConfig");
            fieldExtension.setStringValue(JsonUtils.toJsonString(childProcessSetting.getStartUserSetting()));
            flowableListener.getFieldExtensions().add(fieldExtension);
            executionListeners.add(flowableListener);
            callActivity.setExecutionListeners(executionListeners);

            // 7. 超时设置
            if (childProcessSetting.getTimeoutSetting() != null && Boolean.TRUE.equals(childProcessSetting.getTimeoutSetting().getEnable())) {
                BoundaryEvent boundaryEvent = null;
                if (childProcessSetting.getTimeoutSetting().getType().equals(BpmDelayTimerTypeEnum.FIXED_DATE_TIME.getType())) {
                    boundaryEvent = buildTimeoutBoundaryEvent(callActivity, BpmBoundaryEventTypeEnum.DELAY_TIMER_TIMEOUT.getType(),
                            childProcessSetting.getTimeoutSetting().getTimeExpression(), null, null);
                } else if (childProcessSetting.getTimeoutSetting().getType().equals(BpmDelayTimerTypeEnum.FIXED_TIME_DURATION.getType())) {
                    boundaryEvent = buildTimeoutBoundaryEvent(callActivity, BpmBoundaryEventTypeEnum.CHILD_PROCESS_TIMEOUT.getType(),
                            null, null, childProcessSetting.getTimeoutSetting().getTimeExpression());
                }
                flowElements.add(boundaryEvent);
            }

            // 8. 多实例
            if (childProcessSetting.getMultiInstanceSetting() != null && Boolean.TRUE.equals(childProcessSetting.getMultiInstanceSetting().getEnable())) {
                MultiInstanceLoopCharacteristics multiInstanceCharacteristics = new MultiInstanceLoopCharacteristics();
                multiInstanceCharacteristics.setSequential(childProcessSetting.getMultiInstanceSetting().getSequential());
                if (childProcessSetting.getMultiInstanceSetting().getSourceType().equals(BpmChildProcessMultiInstanceSourceTypeEnum.FIXED_QUANTITY.getType())) {
                    multiInstanceCharacteristics.setLoopCardinality(childProcessSetting.getMultiInstanceSetting().getSource());
                }
                if (childProcessSetting.getMultiInstanceSetting().getSourceType().equals(BpmChildProcessMultiInstanceSourceTypeEnum.NUMBER_FORM.getType()) ||
                        childProcessSetting.getMultiInstanceSetting().getSourceType().equals(BpmChildProcessMultiInstanceSourceTypeEnum.MULTIPLE_FORM.getType())) {
                    multiInstanceCharacteristics.setInputDataItem(childProcessSetting.getMultiInstanceSetting().getSource());
                }
                multiInstanceCharacteristics.setCompletionCondition(String.format(BpmUserTaskApproveMethodEnum.RATIO.getCompletionCondition(),
                        String.format("%.2f", childProcessSetting.getMultiInstanceSetting().getApproveRatio() / 100D)));
                callActivity.setLoopCharacteristics(multiInstanceCharacteristics);
                addExtensionElement(callActivity, CHILD_PROCESS_MULTI_INSTANCE_SOURCE_TYPE, childProcessSetting.getMultiInstanceSetting().getSourceType());
            }

            // 添加节点类型
            addNodeType(node.getType(), callActivity);
            flowElements.add(callActivity);
            return flowElements;
        }

        @Override
        public BpmSimpleModelNodeTypeEnum getType() {
            return BpmSimpleModelNodeTypeEnum.CHILD_PROCESS;
        }

    }

    private static String buildGatewayJoinId(String id) {
        return id + "_join";
    }

    private static BoundaryEvent buildTimeoutBoundaryEvent(Activity attachedToRef, Integer type,
                                                           String timeDuration, String timeCycle, String timeDate) {
        // 1.1 定时器边界事件
        BoundaryEvent boundaryEvent = new BoundaryEvent();
        boundaryEvent.setId("Event-" + IdUtil.fastUUID());
        boundaryEvent.setCancelActivity(false); // 设置关联的任务为不会被中断
        boundaryEvent.setAttachedToRef(attachedToRef);
        // 1.2 定义超时时间表达式
        TimerEventDefinition eventDefinition = new TimerEventDefinition();
        if (ObjUtil.isNotNull(timeDuration)) {
            eventDefinition.setTimeDuration(timeDuration);
        }
        if (ObjUtil.isNotNull(timeDuration)) {
            eventDefinition.setTimeCycle(timeCycle);
        }
        if (ObjUtil.isNotNull(timeDate)) {
            eventDefinition.setTimeDate(timeDate);
        }
        boundaryEvent.addEventDefinition(eventDefinition);

        // 2. 添加定时器边界事件类型
        addExtensionElement(boundaryEvent, BOUNDARY_EVENT_TYPE, type);
        return boundaryEvent;
    }

    // ========== SIMPLE 流程预测相关的方法 ==========

    public static List<BpmSimpleModelNodeVO> simulateProcess(BpmSimpleModelNodeVO rootNode, Map<String, Object> variables) {
        List<BpmSimpleModelNodeVO> resultNodes = new ArrayList<>();

        // 从头开始遍历
        simulateNextNode(rootNode, variables, resultNodes);
        return resultNodes;
    }

    private static void simulateNextNode(BpmSimpleModelNodeVO currentNode, Map<String, Object> variables,
                                         List<BpmSimpleModelNodeVO> resultNodes) {
        // 如果不合法（包括为空），则直接结束
        if (!isValidNode(currentNode)) {
            return;
        }
        BpmSimpleModelNodeTypeEnum nodeType = BpmSimpleModelNodeTypeEnum.valueOf(currentNode.getType());
        Assert.notNull(nodeType, "模型节点类型不支持");

        // 情况：START_NODE/START_USER_NODE/APPROVE_NODE/COPY_NODE/END_NODE/TRANSACTOR_NODE
        if (nodeType == BpmSimpleModelNodeTypeEnum.START_NODE
                || nodeType == BpmSimpleModelNodeTypeEnum.START_USER_NODE
                || nodeType == BpmSimpleModelNodeTypeEnum.APPROVE_NODE
                || nodeType == BpmSimpleModelNodeTypeEnum.TRANSACTOR_NODE
                || nodeType == BpmSimpleModelNodeTypeEnum.COPY_NODE
                || nodeType == BpmSimpleModelNodeTypeEnum.CHILD_PROCESS
                || nodeType == BpmSimpleModelNodeTypeEnum.END_NODE) {
            // 添加此节点
            resultNodes.add(currentNode);
        }

        // 情况：CONDITION_BRANCH_NODE 排它，只有一个满足条件的。如果没有，就走默认的
        if (nodeType == BpmSimpleModelNodeTypeEnum.CONDITION_BRANCH_NODE) {
            // 查找满足条件的 BpmSimpleModelNodeVO 节点
            BpmSimpleModelNodeVO matchConditionNode = CollUtil.findOne(currentNode.getConditionNodes(),
                    conditionNode -> !BooleanUtil.isTrue(conditionNode.getConditionSetting().getDefaultFlow())
                            && evalConditionExpress(variables, conditionNode.getConditionSetting()));
            if (matchConditionNode == null) {
                matchConditionNode = CollUtil.findOne(currentNode.getConditionNodes(),
                        conditionNode -> BooleanUtil.isTrue(conditionNode.getConditionSetting().getDefaultFlow()));
            }
            Assert.notNull(matchConditionNode, "找不到条件节点({})", currentNode);
            // 遍历满足条件的 BpmSimpleModelNodeVO 节点
            simulateNextNode(matchConditionNode.getChildNode(), variables, resultNodes);
        }

        // 情况：INCLUSIVE_BRANCH_NODE 包容，多个满足条件的。如果没有，就走默认的
        if (nodeType == BpmSimpleModelNodeTypeEnum.INCLUSIVE_BRANCH_NODE) {
            // 查找满足条件的 BpmSimpleModelNodeVO 节点
            Collection<BpmSimpleModelNodeVO> matchConditionNodes = CollUtil.filterNew(currentNode.getConditionNodes(),
                    conditionNode -> !BooleanUtil.isTrue(conditionNode.getConditionSetting().getDefaultFlow())
                            && evalConditionExpress(variables, conditionNode.getConditionSetting()));
            if (CollUtil.isEmpty(matchConditionNodes)) {
                matchConditionNodes = CollUtil.filterNew(currentNode.getConditionNodes(),
                        conditionNode -> BooleanUtil.isTrue(conditionNode.getConditionSetting().getDefaultFlow()));
            }
            Assert.isTrue(!matchConditionNodes.isEmpty(), "找不到条件节点({})", currentNode);
            // 遍历满足条件的 BpmSimpleModelNodeVO 节点
            matchConditionNodes.forEach(matchConditionNode ->
                    simulateNextNode(matchConditionNode.getChildNode(), variables, resultNodes));
        }

        // 情况：PARALLEL_BRANCH_NODE 并行，都满足，都走
        if (nodeType == BpmSimpleModelNodeTypeEnum.PARALLEL_BRANCH_NODE) {
            // 遍历所有 BpmSimpleModelNodeVO 节点
            currentNode.getConditionNodes().forEach(matchConditionNode ->
                    simulateNextNode(matchConditionNode.getChildNode(), variables, resultNodes));
        }

        // 遍历子节点
        simulateNextNode(currentNode.getChildNode(), variables, resultNodes);
    }

    /**
     * 根据跳过表达式，判断是否跳过此节点
     */
    public static boolean isSkipNode(BpmSimpleModelNodeVO currentNode, Map<String, Object> variables) {
        if (StrUtil.isEmpty(currentNode.getSkipExpression())) {
            return false;
        }
        return BpmnModelUtils.evalConditionExpress(variables, currentNode.getSkipExpression());
    }

    public static boolean evalConditionExpress(Map<String, Object> variables, BpmSimpleModelNodeVO.ConditionSetting conditionSetting) {
        return BpmnModelUtils.evalConditionExpress(variables, buildConditionExpression(conditionSetting));
    }

}
