# yudao-module-ai

## 📚 模块简介

AI 模块是芋道项目的人工智能集成模块，提供全方位的 AI 能力支持。本模块接入多种 LLM 大语言模型，支持聊天对话、图像生成、音乐创作、AI 写作、思维导图等丰富功能。

### 🎯 核心特性

- **多模型支持**：集成国内外主流 AI 模型
  - 国内：通义千问、文心一言、讯飞星火、智谱 GLM、DeepSeek
  - 国外：OpenAI、Ollama、Midjourney、Stable Diffusion、Suno

- **丰富的 AI 功能**
  - 💬 智能对话：支持多轮对话、上下文记忆
  - 🎨 AI 绘画：Midjourney、Stable Diffusion 图像生成
  - 🎵 AI 音乐：Suno 音乐创作
  - ✍️ AI 写作：智能文案、文章生成
  - 🧠 思维导图：AI 辅助思维导图生成
  - 📚 知识库：文档管理、向量检索、RAG 增强
  - 🔧 AI 工具：可扩展的 AI 工具集成
  - 🔄 工作流：自定义 AI 工作流编排

## 📦 模块结构

```
yudao-module-ai
├── yudao-module-ai-api          # API 接口模块（对外暴露）
│   └── src/main/java
│       └── cn/iocoder/yudao/module/ai
│           └── enums/           # 枚举定义
└── yudao-module-ai-server       # 服务实现模块
    └── src/main/java
        └── cn/iocoder/yudao/module/ai
            ├── controller/      # 控制层
            │   └── admin/
            │       ├── chat/           # 聊天对话
            │       ├── image/          # 图像生成
            │       ├── music/          # 音乐创作
            │       ├── write/          # AI 写作
            │       ├── mindmap/        # 思维导图
            │       ├── knowledge/      # 知识库管理
            │       ├── model/          # 模型管理
            │       └── workflow/       # 工作流管理
            ├── service/         # 服务层
            ├── dal/             # 数据访问层
            ├── framework/       # 框架集成
            ├── job/             # 定时任务
            └── util/            # 工具类
```

## 🚀 主要功能

### 1️⃣ 智能对话（Chat）
- 支持创建多个对话会话
- 对话历史记录管理
- 支持流式响应
- 多模型切换

### 2️⃣ AI 绘画（Image）
- Midjourney 图像生成
- Stable Diffusion 绘图
- 图像变体、放大等操作
- 绘图历史管理

### 3️⃣ AI 音乐（Music）
- Suno 音乐生成
- 支持自定义音乐风格
- 音乐管理和下载

### 4️⃣ AI 写作（Write）
- 多种写作场景支持
- 智能文案生成
- 内容优化建议

### 5️⃣ 思维导图（MindMap）
- AI 辅助生成思维导图
- 支持多种主题和样式
- 导图历史管理

### 6️⃣ 知识库（Knowledge）
- 文档上传和管理
- 文档分段和向量化
- 智能检索和问答
- RAG（检索增强生成）支持

### 7️⃣ 模型管理（Model）
- AI 模型配置管理
- API Key 管理
- AI 工具集成

### 8️⃣ AI 工作流（Workflow）
- 可视化工作流编排
- 多节点任务串联
- 工作流测试和执行

## 🔧 技术栈

- **Spring AI**：Spring 官方 AI 集成框架
- **向量数据库**：支持文档向量化存储和检索
- **流式处理**：支持 SSE（Server-Sent Events）流式响应
- **异步任务**：定时同步 Midjourney、Suno 任务状态

## 📝 使用说明

### 1. 配置 API Key

在系统管理后台配置各个 AI 平台的 API Key：

- 进入「AI 管理」→「模型管理」→「API Key 管理」
- 添加对应平台的 API Key
- 配置模型参数

### 2. 创建聊天会话

```
POST /admin-api/ai/chat/conversation/create-my
```

### 3. 发送消息

```
POST /admin-api/ai/chat/message/send
```

### 4. 生成图像

```
POST /admin-api/ai/image/imagine
```

更多 API 接口请参考各个 Controller 的 Swagger 文档。

## 🔐 权限说明

模块接口需要以下权限：

- `ai:chat:*` - 聊天对话权限
- `ai:image:*` - 图像生成权限
- `ai:music:*` - 音乐创作权限
- `ai:write:*` - AI 写作权限
- `ai:mindmap:*` - 思维导图权限
- `ai:knowledge:*` - 知识库管理权限
- `ai:model:*` - 模型管理权限
- `ai:workflow:*` - 工作流管理权限

## 📊 数据库表

主要数据表包括：

- `ai_chat_conversation` - 聊天会话表
- `ai_chat_message` - 聊天消息表
- `ai_image` - AI 图像表
- `ai_music` - AI 音乐表
- `ai_write` - AI 写作表
- `ai_mind_map` - 思维导图表
- `ai_knowledge` - 知识库表
- `ai_knowledge_document` - 知识库文档表
- `ai_knowledge_segment` - 知识库分段表
- `ai_model` - AI 模型表
- `ai_api_key` - API Key 表
- `ai_tool` - AI 工具表
- `ai_workflow` - AI 工作流表

## 🔄 定时任务

- **AiMidjourneySyncJob**：同步 Midjourney 图像生成任务状态
- **AiSunoSyncJob**：同步 Suno 音乐生成任务状态

## 📖 相关文档

- [芋道 Cloud 快速开始](https://cloud.iocoder.cn/quick-start/)
- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [OpenAI API 文档](https://platform.openai.com/docs/api-reference)

## 🤝 参与贡献

欢迎提交 Issue 和 Pull Request！

## 📄 开源协议

本模块基于 [MIT 协议](../LICENSE) 开源，可以免费用于商业和个人项目。

