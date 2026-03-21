# AI Mental Care - AI 心理健康护理系统

## 项目简介

AI Mental Care 是一个基于 Spring AI 框架构建的 AI 心理健康护理系统，通过集成多种大语言模型（LLM），提供智能化的心理健康咨询、情绪识别与分析服务。

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 3.5.0 | 基础框架 |
| Spring AI | 1.1.2 | AI 集成框架 |
| Spring AI Alibaba | 1.1.2.0 | 阿里云 AI 扩展 |
| MyBatis Plus | 3.5.15 | ORM 框架 |
| MySQL | 8.0.33 | 关系型数据库 |
| Redis | - | 缓存中间件 |
| Redisson | 3.20.1 | 分布式锁 |

### 支持的 AI 模型

- **Ollama**: 本地部署的大模型
- **DeepSeek**: 国产大模型
- **DashScope**: 阿里云通义千问

## 项目结构

```
ai-mental-care
├── pojo                          # 实体模块
│   ├── dto/                      # 数据传输对象
│   ├── entity/                   # 数据库实体
│   ├── tool/                     # 工具类实体
│   └── vo/                       # 视图对象
├── common                        # 通用模块
│   ├── common-aop                # AOP 切面
│   ├── common-authentication     # 认证模块
│   ├── common-bom                # 依赖管理
│   ├── common-core               # 核心工具
│   ├── common-json               # JSON 处理
│   ├── common-mail               # 邮件服务
│   ├── common-oss                # 对象存储
│   ├── common-redis              # Redis 缓存
│   ├── common-sensitive          # 敏感词过滤
│   ├── common-sms                # 短信服务
│   ├── common-sql                # 数据库配置
│   ├── common-translation        # 国际化翻译
│   ├── common-validation         # 参数校验
│   ├── common-vector             # 向量数据库
│   ├── common-verification-code  # 验证码
│   └── common-web                # Web 配置
└── server                        # 服务模块
    ├── config/                   # 配置类
    ├── controller/               # 控制器
    ├── service/                  # 业务逻辑
    ├── mapper/                   # 数据访问
    ├── node/                     # AI 节点
    ├── tool/                     # AI 工具
    └── infrastructure/           # 基础设施
```

## 核心功能

### 1. 情绪识别与分析

基于 AI 大模型的多维度情绪识别系统，支持：
- 情绪标签识别（愤怒、开心、中性、不满等）
- 情绪细分标签分析
- 情绪诊断报告生成

### 2. 智能对话系统

- 多轮对话管理
- 对话历史存储
- 会话状态持久化
- 流式响应输出

### 3. 用户管理

- 用户注册/登录
- OAuth2 第三方登录（GitHub）
- 用户资料管理
- 权限角色管理

### 4. AI Agent 架构

采用 ReactAgent 架构，实现：
- 情绪识别节点
- 情绪诊断节点
- 总结节点
- 最终回答节点

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

### 配置文件

在启动前，需要配置以下环境变量：

```yaml
# 数据库配置
db.host: localhost
db.port: 3306
db.database: ai_mental_care
db.username: your_username
db.password: your_password

# Redis 配置
redis.host: localhost
redis.port: 6379
redis.database: 0
```

### 启动命令

```bash
# 编译项目
mvn clean install -DskipTests

# 启动服务
cd server
mvn spring-boot:run
```

服务默认端口：`8088`

## API 文档

启动服务后访问 Swagger 文档：
- 地址：`http://localhost:8088/doc.html`

## 开发指南

### 模块依赖关系

```
server → pojo → common-*
```

### 添加新的 AI 模型

1. 在 `server/config/model/` 下创建模型配置类
2. 实现 `NodeActionWithConfig` 接口创建处理节点
3. 在 `GraphConstant` 中注册节点

### 添加新的通用模块

1. 在 `common/` 下创建新模块
2. 在 `common/pom.xml` 中添加模块引用
3. 在 `common-bom/pom.xml` 中声明依赖

## 许可证

本项目仅供学习交流使用。
