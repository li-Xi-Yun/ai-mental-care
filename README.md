# AI Mental Care - AI 心理健康关怀系统

基于 Spring Boot 3.5 + Spring AI 构建的智能心理健康关怀平台，融合情绪识别、AI 对话、RAG 知识检索与心理量表评估，为用户提供全方位的心理健康服务。

## 项目概览

```
ai-mental-care
├── server          # 核心业务服务模块
├── pojo            # 实体/DTO/VO 数据模型模块
└── common          # 通用基础模块
    ├── common-core               # 核心工具与配置
    ├── common-authentication     # JWT 认证与 OAuth2
    ├── common-aop               # AOP 切面（限流、自动填充）
    ├── common-agent             # AI Agent 与 Prompt 管理
    ├── common-sql               # MyBatis-Plus 数据库
    ├── common-redis             # Redis 缓存
    ├── common-vector            # 向量数据库
    ├── common-json              # JSON 序列化
    ├── common-mail              # 邮件服务
    ├── common-sms               # 短信服务
    ├── common-oss               # 对象存储
    ├── common-file              # 文件管理
    ├── common-websocket         # WebSocket 通信
    ├── common-web               # Web 配置
    ├── common-validation        # 参数校验
    ├── common-verification-code # 验证码
    ├── common-sensitive         # 数据脱敏
    ├── common-translation       # 翻译
    └── common-bom               # 依赖版本管理
```

## 核心功能

### 🧠 AI 情绪识别与诊断
- 基于多轮对话的实时情绪分析（`EmotionRecognitionNode`）
- 自动生成情绪诊断报告（`EmotionalDiagnosisNode`）
- 支持 DeepSeek / DashScope / Ollama 多模型切换

### 💬 AI 智能对话
- 多场景对话支持（心理咨询、日常陪伴等）
- 对话历史记忆与压缩（`HistoryMessageCompressionNode`）
- 流式消息推送（WebSocket）

### 📚 RAG 知识检索增强
- 基于 Milvus 向量数据库的文档检索
- 查询重写、多查询扩展、重排序（`RagGraph`）
- 知识文档管理与向量化

### 📋 心理量表评估
- 多类别心理量表管理（SAS、SDS、SCL-90 等）
- 量表答题与自动评分
- 评分规则与结果解读

### 🎙️ 语音交互
- 语音识别（ASR）与语音合成（TTS）
- 音频流式处理

### 🔐 用户与权限
- JWT 双 Token 认证（用户端 / 管理端）
- GitHub OAuth2 登录
- RBAC 角色权限管理

## 技术栈

| 类别 | 技术 |
|------|------|
| **框架** | Spring Boot 3.5.0, Spring AI |
| **AI 模型** | DeepSeek, 阿里 DashScope (Qwen), Ollama (Llama) |
| **向量数据库** | Milvus |
| **数据库** | MySQL 8.0, MyBatis-Plus |
| **缓存** | Redis, Redisson |
| **认证** | Spring Security, JWT, OAuth2 |
| **通信** | WebSocket |
| **API 文档** | Knife4j (OpenAPI 3) |
| **工具** | Hutool, MapStruct, Lombok |
| **部署** | Docker |

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- Milvus 2.x
- Maven 3.8+

### 配置

1. 克隆项目

```bash
git clone <repository-url>
cd ai-mental-care
```

2. 配置数据库，创建 `ai_mental_care` 数据库

3. 修改 `server/src/main/resources/application-dev.yml`，配置以下信息：

- 数据库连接（`db`）
- Redis 连接（`redis`）
- Milvus 连接（`milvus`）
- AI 模型 API Key（`ai-model-config`）
- 邮件服务（`mail`）
- OAuth2 客户端（`oauth2`）

### 启动

```bash
mvn clean install -DskipTests
cd server
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

服务启动后，访问 Knife4j API 文档：`http://localhost:8088/doc.html`

## 项目架构

系统核心采用 **AI Graph 工作流** 架构，将心理健康服务拆解为多个可编排的节点：

```
用户消息 → ASR语音识别 → 情绪识别 → 情绪诊断 → RAG知识检索 → AI对话生成 → TTS语音合成 → 响应
```

每个节点（`Node`）实现 `NodeActionWithConfig` 接口，支持独立配置与灵活编排，通过 `StateGraph` 串联形成完整的处理链路。
