# 个人简历

---

## 基本信息

| 项目 | 内容 |
|------|------|
| 姓名 |  |
| 性别 |  |
| 出生年月 |  |
| 手机 |  |
| 邮箱 |  |
| 求职意向 | Java后端开发实习生 |
| 期望城市 |  |
| 到岗时间 |  |
| 实习时长 |  |

---

## 教育背景

| 项目 | 内容 |
|------|------|
| 学校 |  |
| 学历 |  |
| 专业 |  |
| 入学时间 |  |
| 毕业时间 |  |
| GPA/排名 |  |

---

## 技术技能

### 编程语言
- Java 17（熟练）、SQL（熟练）

### 框架与中间件
- **Spring 生态**：Spring Boot 3.x、Spring Security、Spring AI、Spring WebSocket、Spring Mail
- **ORM 与数据层**：MyBatis-Plus、MyBatis、PageHelper、Dynamic DataSource（多数据源）
- **缓存与分布式锁**：Redis、Redisson、Lock4j
- **AI 框架**：Spring AI Alibaba（DashScope）、Spring AI DeepSeek、Spring AI Ollama、Alibaba Cloud AI Graph（StateGraph 工作流引擎）
- **向量数据库**：Milvus（向量存储与检索）、Spring AI Vector Store

### 数据库
- MySQL 8.0（索引优化、事务管理、分库分表设计）

### 前端与接口
- RESTful API 设计、SpringDoc OpenAPI / Knife4j 接口文档

### 工具库
- Hutool、Lombok、MapStruct-Plus、OkHttp、Apache HttpClient 5、Apache POI、EasyExcel、Velocity 模板引擎

### 云服务
- **对象存储**：AWS S3、腾讯云 COS
- **短信**：阿里云 SMS、腾讯云 SMS
- **语音**：阿里云 DashScope ASR（实时语音识别）、DashScope TTS（CosyVoice 语音合成）
- **OAuth2**：GitHub 第三方登录

### 工程化
- Maven 多模块项目管理、Git 版本控制、Logback 日志、P6Spy SQL 性能分析

### 设计模式与架构
- 装饰器模式（Decorator）、工厂模式（Factory）、钩子/拦截器模式（Hook/Interceptor）、状态图工作流（StateGraph）、响应式编程（Reactor Flux）、CompletableFuture 异步编程、ForkJoinPool 并行流

---

## 项目经历

### AI 心理健康关怀系统（ai-mental-care）

**项目时间**：2026.03 – 至今

**项目角色**：独立开发

**技术栈**：Spring Boot 3.5 / Spring AI / Alibaba Cloud AI Graph / MyBatis-Plus / Redis / Redisson / Milvus / MySQL / WebSocket / DashScope ASR&TTS / JWT / OAuth2

**项目描述**：
基于 AI 大模型的心理健康关怀平台，整合多模型对话、多阶段心理诊断工作流、RAG 检索增强生成、实时语音交互、心理量表测评等核心能力，为用户提供智能化的心理健康评估与干预建议服务。

**核心模块与职责**：

1. **AI 心理诊断工作流引擎**
   - 基于 Alibaba Cloud AI Graph 的 StateGraph 构建四阶段诊断流水线：输入侧 → 知识侧 → 处理侧 → 持久化
   - **输入侧**：数据预清洗、消息结构化处理、情绪统计分析、历史诊断摘要聚合、症状语义归一化，三个节点通过 ForkJoinPool 并行执行
   - **知识侧**：查询变换层 → 并行检索（症状知识库 / 诊断标准库 / 干预方案库）→ 重排层，实现条件边重试机制，空结果自动回溯重试查询变换
   - **处理侧**：六节点并行执行（情绪综合分析、病程归因、心理状态评估、社会功能影响评估、保护性因素分析、风险评估）→ 干预建议生成 → 总结生成
   - 设计自定义 StateSerializer 实现图状态的序列化与反序列化，支持诊断流程的断点恢复

2. **RAG 检索增强生成**
   - 构建 RAG 工作流图：查询压缩 + 查询重写（并行）→ 多查询扩展 → Milvus 向量检索 → 重排序 → 文档拼接
   - 实现条件边重试与中断机制，检索结果为空时自动重试查询变换，超过阈值返回兜底内容
   - 集成 Milvus 向量数据库，支持 PDF / Markdown / Tika 多格式文档的 Embedding 写入与相似度检索

3. **实时语音对话**
   - 集成阿里云 DashScope ASR（paraformer-realtime-8k-v2）实现实时语音识别，支持中间识别结果弹幕推送
   - 集成 DashScope TTS（CosyVoice v3）实现语音合成，通过 WebSocket 流式推送音频二进制数据
   - 设计 ASR → 文本处理 → TTS 的完整语音对话链路

4. **多模型对话与流式处理**
   - 实现 ChatModelFactory 工厂模式，支持 DeepSeek / DashScope / Ollama 三模型动态切换
   - 基于 Reactor Flux 构建流式处理管道，设计 AgentStreamProcessor 接口与 Decorator 装饰器链：思考内容聚合 → 日志记录 → 消息持久化 → 监听器分发
   - 实现 AI Agent Hook / Interceptor 机制，支持对话前后的自定义拦截与增强

5. **心理量表测评系统**
   - 设计量表分类、题目、选项模板、评分规则的完整数据模型
   - 实现用户答题、自动评分、结果规则匹配、答题记录管理

6. **通用基础架构（16 个 Common 模块）**
   - 认证模块：JWT 令牌 + OAuth2 GitHub 登录 + Spring Security 权限控制
   - AOP 模块：基于 Redis + Lua 脚本的分布式限流、SQL 自动填充
   - 缓存模块：Redis + Redisson + Lock4j 分布式锁
   - 文件模块：AWS S3 + 腾讯云 COS 对象存储
   - 通信模块：邮件（Spring Mail）、短信（阿里云 / 腾讯云）、WebSocket 实时推送
   - 其他：脱敏、翻译、校验、验证码、JSON 序列化等

**项目亮点**：
- 采用 StateGraph 工作流引擎实现复杂 AI 诊断流程的编排，支持并行节点、条件边路由、中断与重试机制
- 基于装饰器模式设计流式处理管道，实现思考内容聚合、日志、持久化等能力的灵活叠加
- 多模型适配（DeepSeek / Qwen / Ollama），支持本地模型与云端模型的无缝切换
- 16 个通用模块的模块化设计，实现认证、限流、缓存、存储等基础能力的复用与解耦

---

## 专业能力总结

- 熟悉 Java 17 新特性，掌握 Spring Boot 3.x 全栈开发，具备独立从零搭建项目的能力
- 深入理解 Spring AI 与 Alibaba Cloud AI Graph 工作流引擎，具备 AI Agent 应用开发经验
- 掌握 RAG 检索增强生成全流程：文档解析 → Embedding → 向量存储 → 检索 → 重排序
- 熟悉 MySQL 数据库设计与优化，掌握 Redis 缓存策略与分布式锁实现
- 具备 WebSocket 实时通信与流式数据处理经验（Reactor Flux 响应式编程）
- 熟悉设计模式在实际项目中的应用：装饰器、工厂、钩子/拦截器、状态图等
- 具备多模块 Maven 项目架构能力，注重代码分层、模块解耦与复用

---

## 自我评价

- 对 AI 应用开发有浓厚兴趣，持续关注 Spring AI、LangChain 等框架的发展
- 具备较强的独立开发能力，从需求分析到架构设计到编码实现全流程独立完成
- 注重代码质量与工程规范，善用设计模式解决复杂业务场景
- 学习能力强，能快速掌握新技术并应用到实际项目中

---