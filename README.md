<div align="center">

# 🧠 AI Mental Care

**AI 心理健康关怀平台 · 基于 Spring Boot + Spring AI 的前后端分离智能心理服务系统**

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://www.oracle.com/java/)
[![Vue](https://img.shields.io/badge/Vue-3.x-42b883.svg)](https://vuejs.org/)
[![Vite](https://img.shields.io/badge/Vite-6.x-646cff.svg)](https://vitejs.dev/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479a1.svg)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-6+-dc382d.svg)](https://redis.io/)
[![Milvus](https://img.shields.io/badge/Milvus-2.x-00a1e9.svg)](https://milvus.io/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

---

## 📖 平台简介

AI Mental Care 是一套**融合 AI 大模型与专业心理量表的心理健康关怀平台**，面向有情绪困扰、压力大、睡眠不佳等心理需求的用户，提供从「**倾诉 → 情绪分析 → 量表评估 → 诊断报告 → 分级建议**」的完整心理健康服务闭环。

平台采用 **AI Graph 工作流** 的可编排节点架构，每个 AI 环节均可独立配置、灵活组装；输入侧支持文本 / 语音，输出侧支持文本 / 语音 / 卡片交互，通过**会话级适配器与输出管道**实现一次对话、全链路智能化处理。

产品包含 **用户端（Portal）** 与 **管理端（Admin）** 两套前端：

| 端 | 面向人群 | 主要能力 | 访问路径 |
|---|---|---|---|
| **Portal 用户端** | 普通用户 | AI 对话、情绪分析、量表测评、诊断报告、个人中心 | `/` |
| **Admin 管理端** | 心理师 / 管理员 | 量表编排、题库管理、AI 节点配置、知识库、权限管控 | `/admin` |

## ✨ 特性

- 🧠 **AI 大模型对话**：多轮记忆、长对话自动压缩，支持 DeepSeek / Qwen / Llama 多模型按需切换
- 🎯 **实时情绪识别**：核心情绪 + 三维量化模型 + 变化趋势，每轮对话自动追踪
- 📋 **专业量表测评**：多题型作答引擎、条件跳题、限时答题、未完成自动续答、常模计分与风险分级
- 🩺 **结构化诊断书**：情绪画像 + 症状评估 + 风险危机预警 + 分级干预建议，按时间线沉淀
- 🔄 **量表 × AI 深度联动**：AI 对话中智能推荐量表 → 一键作答 → 结合对话上下文生成个性化解读
- 📚 **RAG 知识增强**：向量库检索 + 查询重写 / 多路扩展 / 重排序，回答更专业可信
- 🎙️ **语音交互**：语音识别（ASR）与语音合成（TTS）能力预留，全语音对话体验
- 🔐 **完整权限体系**：JWT 双端认证、图形验证码、RBAC 角色权限、临时授权

## 📋 内置功能

1. **AI 智能对话**：多轮自然语言对话，历史按轮次记忆并自动压缩，WebSocket 流式推送，实时思考状态展示
2. **实时情绪分析**：提取对话中的核心情绪、置信度、强度与趋势，输出负向 / 中性 / 正向占比与关键词，右侧面板实时可视化
3. **量表测评**：焦虑、抑郁、压力、睡眠、症状自评等分类量表库，支持单选 / 多选 / 填空，作答进度网格、限时倒计时、草稿防刷新丢失
4. **测评记录**：查看历史测评、「正常 / 低 / 中 / 高」四级风险分级、原始分 / 标准分 / 百分位、维度得分与结果解读
5. **情绪诊断书**：按会话与轮次生成结构化诊断报告，含情绪画像、症状评估、自伤 / 自杀风险预警、情绪调节与专业干预建议
6. **诊断反馈**：用户可就诊断结论与建议的真实性、有用性进行评分反馈，帮助模型持续优化
7. **知识库管理（Admin）**：文档上传、解析、切分、向量化入库，构建机构专属心理知识库
8. **量表编排管理（Admin）**：量表、分类、维度、题目、选项、跳题规则、计分规则、常模、版本全流程可视化管理
9. **AI 节点配置（Admin）**：对「情绪分析、量表解读、知识检索、对话生成」等每个 AI 节点独立配置模型与提示词
10. **用户与权限（Admin）**：用户管理、管理员管理、角色 / 菜单权限、动态临时授权
11. **症状字典（Admin）**：标准化症状标签库，支撑诊断书症状归纳
12. **文件管理（Admin）**：文件分类、上传下载、离线向量化加载

## 🖼️ 界面预览

> 📸 界面设计稿存放于 `front-picture/` 目录，包含 **Portal 用户端**（对话、情绪分析、量表作答、诊断报告、测评记录、个人中心）与 **Admin 管理端**（量表编排、AI 节点、知识库、权限管理等）的完整页面设计，可直接用浏览器打开 HTML 文件预览。

## 🔧 系统架构

```
┌────────────┐   ┌────────────┐   ┌────────────┐   ┌────────────┐   ┌────────────┐
│ 用户消息    │→│ 语音识别    │→│ 情绪识别    │→│ 对话记忆    │→│ 知识检索    │
└────────────┘   └────────────┘   └────────────┘   └────────────┘   └────────────┘
                                                                          │
┌────────────┐   ┌────────────┐   ┌────────────┐   ┌────────────┐          │
│ 用户接收    │←│ 语音合成    │←│ 流式输出    │←│ AI 生成    │←────────────┘
└────────────┘   └────────────┘   └────────────┘   └────────────┘
        │                                                   ▲
        └──────────── 量表推荐卡片 / 诊断书反馈 ──────────────┘
```

核心设计：

- **AI Graph 工作流**：将心理服务拆解为可独立配置、灵活编排的节点，通过状态图串联成完整链路
- **会话级适配器**：每个会话独立装配输入适配器（文本 / 语音）与输出管道，切换 / 销毁时自动释放资源
- **量表闭环联动**：AI 推荐量表 → 作答 → 结合对话上下文生成解读 → 回写对话，量表与对话深度耦合

## 🛠️ 技术栈

| 类别 | 技术 |
|------|------|
| **后端框架** | Spring Boot 3.5 · Spring AI（Alibaba Agent Framework） |
| **开发语言** | Java |
| **前端框架** | Vue 3 · TypeScript · Vite · Element Plus · Pinia · Tailwind CSS |
| **AI 模型** | DeepSeek · 阿里 DashScope (Qwen) · Ollama (Llama) |
| **向量数据库** | Milvus |
| **数据库** | MySQL 8.0 · MyBatis-Plus · 多数据源 |
| **缓存** | Redis · Redisson · 分布式锁 |
| **认证权限** | Spring Security · JWT 双端 Token · OAuth2 · 图形验证码 |
| **实时通信** | WebSocket · STOMP |
| **语音服务** | 阿里云 NLS（ASR 识别 / TTS 合成） |
| **API 文档** | Knife4j · OpenAPI 3 |
| **工具** | Hutool · Lombok · MapStruct |
| **部署** | Docker · Maven 多环境 profile |

## 📁 项目结构

```
ai-mental-care
├── server                 # 核心业务服务（控制器 / AI 工作流 / 基础设施 / 定时任务）
├── pojo                   # 实体 / DTO / VO / BO 数据模型
├── common                 # 通用基础模块（19 个能力子模块）
│   ├── common-core        # 核心工具与配置
│   ├── common-authentication  # JWT 认证与 OAuth2
│   ├── common-aop         # AOP 切面（限流 / 字段自动填充）
│   ├── common-agent       # AI Agent 与提示词管理
│   ├── common-sql         # MyBatis-Plus 数据库访问
│   ├── common-redis       # Redis 缓存
│   ├── common-vector      # 向量数据库
│   ├── common-websocket   # WebSocket 通信
│   ├── common-oss / common-file / common-mail / common-sms   # 云服务与文件
│   └── ...                # 其他能力子模块
├── front                  # 前端应用（Portal + Admin 双端）
└── front-picture          # 界面设计稿（可用浏览器直接预览）
```

## 🚀 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- Milvus 2.x
- Maven 3.8+
- Node.js 18+（前端开发，可选）

### 1. 克隆项目

```bash
git clone <repository-url>
cd ai-mental-care
```

### 2. 配置数据库

创建 `ai_mental_care` 数据库，并执行初始化脚本。

### 3. 修改后端配置

编辑 `server/src/main/resources/application-dev.yml`，配置：

- 数据库连接（`db`）
- Redis 连接（`redis`）
- Milvus 连接（`milvus`）
- AI 模型 API Key（`ai-model-config`）
- 邮件服务（`mail`）
- OAuth2 客户端（`oauth2`）

### 4. 启动后端

```bash
mvn clean install -DskipTests
cd server
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

服务启动后，访问 API 文档：`http://localhost:8088/doc.html`

### 5. 启动前端（可选）

```bash
cd front
npm install
npm run dev
```

## ❤️ 参与共建

欢迎以 Issue / Pull Request 形式参与本项目。如有任何意见建议，可在仓库中提出。

## 📄 License

本项目基于 [MIT](LICENSE) 协议开源。

---
*最后更新：2026-10-09*