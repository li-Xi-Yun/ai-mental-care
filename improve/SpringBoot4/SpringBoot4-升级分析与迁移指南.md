# Spring Boot 4.0.0 升级分析与迁移指南

> 项目：ai-mental-care  
> 当前版本：Spring Boot 3.5.0 / Java 17  
> 目标版本：Spring Boot 4.0.0 / Java 21  
> 生成日期：2026-09-10

---

## 目录

- [1. Spring Boot 4.0.0 核心变更概览](#1-spring-boot-400-核心变更概览)
- [2. 项目当前技术栈分析](#2-项目当前技术栈分析)
- [3. Maven 组件兼容性矩阵](#3-maven-组件兼容性矩阵)
- [4. 必须重构项（阻断性变更）](#4-必须重构项阻断性变更)
- [5. 需要重构项（高概率需改动）](#5-需要重构项高概率需改动)
- [6. 可能需调整项（低风险需验证）](#6-可能需调整项低风险需验证)
- [7. Spring AI 2.0 迁移专题](#7-spring-ai-20-迁移专题)
- [8. 推荐迁移步骤](#8-推荐迁移步骤)
- [9. 风险评估与建议](#9-风险评估与建议)

---

## 1. Spring Boot 4.0.0 核心变更概览

### 1.1 基础框架版本对照

| 组件 | Spring Boot 3.x | Spring Boot 4.0.0 |
|------|------------------|---------------------|
| Spring Framework | 6.x | **7.0** |
| Spring Security | 6.x | **7.0** |
| Spring Data | 2023.x | **2025.x** |
| Jakarta EE | 10 | **11** |
| Jackson | 2.x | **3.x** |
| Java 最低版本 | 17 | **21** |

### 1.2 关键破坏性变更

- **Java 21 成为最低要求**
- **Jackson 3.x**：序列化/反序列化 API 变更，`JsonProcessingException` 包路径变化
- **Jakarta EE 11**：Servlet 6.2、JPA 3.3、Validation 3.1
- **Spring Security 7.0**：部分 API 移极移除
- **废弃 API 全部移除**：3.x 生命周期内所有 `@Deprecated` 方法被删除
- **配置属性收紧**：部分松散绑定规则不再支持

### 1.3 新特性

- **Virtual Threads 一等公民支持**：Tomcat/Undertow 可在虚拟线程上运行
- **CDS（Class Data Sharing）**：官方支持，显著加速 JVM 启动
- **GraalVM Native Image 增强**：AOT 处理引擎优化
- **RestClient** 成为推荐同步 HTTP 客户端，`RestTemplate` 进入维护模式

---

## 2. 项目当前技术栈分析

### 2.1 项目模块结构

```
ai-mental-care (根 POM)
├── pojo                    — 实体/DTO/VO 层
├── common                  — 通用模块
│   ├── common-bom          — 依赖 BOM
│   ├── common-core         — 核心工具/配置
│   ├── common-json         — JSON 序列化
│   ├── common-redis        — Redis/Redisson
│   ├── common-sql          — 数据库/MyBatis-Plus
│   ├── common-auth         — 安全/JWT/OAuth2
│   ├── common-aop          — AOP/限流
│   ├── common-web          — Web 配置/拦截器
│   ├── common-websocket    — WebSocket
│   ├── common-agent        — AI Agent 框架
│   ├── common-vector       — 向量存储/RAG
│   ├── common-oss          — 对象存储
│   ├── common-sms          — 短信
│   ├── common-mail         — 邮件
│   ├── common-file         — 文件处理
│   ├── common-translation  — 翻译
│   ├── common-sensitive    — 脱敏
│   ├── common-validation   — 校验
│   └── common-verification-code — 验证码
└── server                  — 业务服务模块
```

### 2.2 当前关键依赖版本

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.0 | 基础框架 |
| Java | 17 | 运行时 |
| MyBatis-Plus | 3.5.15 | ORM |
| Druid | 1.2.20 | 连接池 |
| Dynamic-DataSource | 4.3.1 | 多数据源 |
| Spring AI BOM | 1.1.2 | AI 模型调用 |
| Spring AI Alibaba | 1.1.2.0 | Agent + DashScope |
| Redisson | 3.20.1 | 分布式锁/缓存 |
| SpringDoc | 2.6.0/2.8.9 | OpenAPI 文档 |
| Knife4j | 4.5.0 | API 文档 UI |
| Hutool | 5.8.35 | 工具库 |
| PageHelper | 2.1.0 | 分页 |
| MapStruct-Plus | 1.2.1 | 对象映射 |
| Lock4j | 2.2.4 | 分布式锁 |
| JJWT | 0.12.6 | JWT |

---

## 3. Maven 组件兼容性矩阵

### 3.1 已有 Boot 4 对应版本（可直接替换）

| # | 组件 | 当前依赖 | 当前版本 | Boot 4 依赖 | Boot 4 版本 | 备注 |
|---|------|----------|----------|-------------|-------------|------|
| 1 | MyBatis-Plus | `mybatis-plus-spring-boot3-starter` | 3.5.15 | `mybatis-plus-spring-boot4-starter` | **3.5.17** | 3.5.13 起支持 Boot 4 |
| 2 | Dynamic-DataSource | `dynamic-datasource-spring-boot3-starter` | 4.3.1 | `dynamic-datasource-spring-boot4-starter` | **4.5.0** | 4.5.0 起支持 Boot 4 |
| 3 | Druid | `druid-spring-boot-3-starter` | 1.2.20 | `druid-spring-boot-4-starter` | **1.2.28** | 1.2.28 起支持 Boot 4 |
| 4 | Spring AI | `spring-ai-bom` | 1.1.2 | `spring-ai-bom` | **2.0.0 GA** | API 有破坏性变更 |
| 5 | SpringDoc | `springdoc-openapi-starter-webmvc-ui` | 2.8.9 | 同名 | **3.0.1** | 3.x 对应 Boot 4 |
| 6 | Knife4j | `knife4j-openapi3-jakarta-spring-boot-starter` | 4.5.0 | `knife4j-openapi3-boot4-spring-boot-starter` | **5.6.0** | 需换 Maven 坐标至 `com.baizhukui` |
| 7 | Redisson | `redisson-spring-boot-starter` | 3.20.1 | 同名 | **4.0.0+** | 4.x 对应 Boot 4，最新 4.7.0 |
| 8 | PageHelper | `pagehelper-spring-boot-starter` | 2.1.0 | 同名 | **4.0.0+** | 4.x 对应 Boot 4，最新 4.1.1 |

### 3.2 需确认/可能兼容

| # | 组件 | 当前版本 | Boot 4 状态 | 建议 |
|---|------|----------|-------------|------|
| 9 | Lock4j | 2.2.4 | 无官方 Boot 4 声明 | 排除旧 Redisson 依赖，配合 Redisson 4.x 验证 |
| 10 | MapStruct-Plus | 1.2.1 | 无官方声明，但有实战验证 | 升级到 1.5.2 测试 |
| 11 | Captcha Starter | 2.2.5 | 无 Boot 4 版本 | 逻辑简单，大概率兼容；不兼容则手动配置 |
| 12 | Hutool | 5.8.35 | 5.x 兼容 / 推荐 6.x | 先升级到 5.8.47，长期迁移 6.x |
| 13 | JJWT | 0.12.6 | 需验证 | 纯 JWT 库，大概率兼容 |
| 14 | P6Spy | 3.9.1 | 纯 JDBC 代理，版本无关 | 可继续使用 |
| 15 | BouncyCastle | 1.72 | 需升级 | 升级到 1.78+ 支持 JDK 21 |
| 16 | EasyExcel | 3.2.1 | 需验证 | 关注 Jackson 3 兼容性 |
| 17 | Apache POI | 5.2.3 | 需验证 | 纯 IO 库，大概率兼容 |
| 18 | AWS S3 SDK | 1.12.400 | 需验证 | 较旧版本，建议升级 |
| 19 | DashScope SDK | 2.22.12 | 需验证 | 阿里云 SDK，关注兼容性 |
| 20 | Milvus SDK | 2.6.18 | 需验证 | gRPC 客户端，大概率兼容 |
| 21 | Alibaba NLS SDK | 2.2.19 | 需验证 | 语音 SDK，关注兼容性 |

### 3.3 无 Boot 4 版本（阻断项）

| # | 组件 | 当前版本 | 状态 | 影响 |
|---|------|----------|------|------|
| 22 | Spring AI Alibaba BOM | 1.1.2.0 | 🔴 2.x 开发中，无 GA | Agent 框架 + DashScope 无法迁移 |
| 23 | Spring AI Alibaba Agent Framework | 1.1.2.0 | 🔴 无 Boot 4 版本 | AI Agent 核心功能阻断 |
| 24 | Spring AI Alibaba DashScope Starter | 1.1.2.0 | 🔴 无 Boot 4 版本 | 通义千问集成阻断 |

---

## 4. 必须重构项（阻断性变更）

### 4.1 Java 版本升级 17 → 21

**影响范围**：全项目所有模块

需修改的文件：

| 文件 | 修改内容 |
|------|----------|
| `../../pom.xml` (根) | `<java.version>17</java.version>` → `21` |
| `../../common/common-bom/pom.xml` | `maven.compiler.source/target=17` → `21` |
| `../../server/pom.xml` | `<java.version>17</java.version>` → `21` |
| 所有子模块 `../../pom.xml` | `maven.compiler.source/target=17` → `21` |

同时需确保：
- CI/CD 环境升级到 JDK 21+
- IDE 配置更新
- 部署环境 JDK 升级

### 4.2 Spring Boot Parent POM 版本

```xml
<!-- 当前 -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.0</version>
</parent>
<spring-boot.version>3.5.0</spring-boot.version>

<!-- 修改为 -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.0</version>
</parent>
<spring-boot.version>4.0.0</spring-boot.version>
```

### 4.3 Boot 3 专属 Starter 替换

```xml
<!-- MyBatis-Plus -->
- <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
+ <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
  <version>3.5.17</version>

<!-- Dynamic-DataSource -->
- <artifactId>dynamic-datasource-spring-boot3-starter</artifactId>
+ <artifactId>dynamic-datasource-spring-boot4-starter</artifactId>
  <version>4.5.0</version>

<!-- Druid -->
- <artifactId>druid-spring-boot-3-starter</artifactId>
+ <artifactId>druid-spring-boot-4-starter</artifactId>
  <version>1.2.28</version>
```

### 4.4 Spring AI 生态升级

```xml
<!-- Spring AI BOM -->
- <version>1.1.2</version>
+ <version>2.0.0</version>

<!-- Spring AI Alibaba BOM — 🔴 尚无 GA 版本，当前阻断 -->
- <version>1.1.2.0</version>
+ <!-- 等待 2.x GA 发布 -->
```

### 4.5 Jackson 3.x 迁移

Spring Boot 4.0 使用 Jackson 3.x，这是**隐性阻断项**：

- `com.fasterxml.jackson.core.JsonProcessingException` → 包路径变更
- 自定义序列化/反序列化器需适配
- `common-json` 模块中的 Jackson 配置需全面检查
- MyBatis-Plus 的 JSON 字段类型处理器需适配 Jackson 3

---

## 5. 需要重构项（高概率需改动）

### 5.1 Spring Security 7.0 API 变更

**涉及文件**：

| 文件 | 模块 | 需检查内容 |
|------|------|------------|
| `SecurityConfig.java` | common-authentication | `@EnableMethodSecurity`、`SecurityFilterChain`、OAuth2 配置 |
| `JwtAuthenticationTokenFilter.java` | common-authentication | Filter 链 API |
| `AuthenticationHandler.java` | common-authentication | 认证异常处理 |
| `PermissionDeniedHandler.java` | common-authentication | 授权异常处理 |
| `LoginSuccessHandler.java` | common-authentication | OAuth2 登录成功处理 |
| `CustomOAuth2UserService.java` | common-authentication | OAuth2 用户服务 |

**关键变更点**：

```java
// SecurityConfig.java 中需验证的配置
http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
    .authorizeHttpRequests(authorize -> authorize
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .anyRequest().permitAll()
    );
http.oauth2Login(oauth2 -> oauth2.userInfoEndpoint(
    userInfo -> userInfo.userService(
        (OAuth2UserService<OAuth2UserRequest, OAuth2User>) customOAuth2UserService
    ))
    .successHandler(loginSuccessHandler));
```

- `@EnableMethodSecurity` 在 Security 7.0 中行为可能变更
- OAuth2 客户端配置 API 可能调整
- `HttpSecurity` 部分配置器方法签名变化

### 5.2 SpringDoc / Knife4j 升级

```xml
<!-- SpringDoc -->
- <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
- <version>2.8.9</version>
+ <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
+ <version>3.0.1</version>

<!-- Knife4j — 需换 Maven 坐标 -->
- <groupId>com.github.xiaoymin</groupId>
- <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
- <version>4.5.0</version>
+ <groupId>com.baizhukui</groupId>
+ <artifactId>knife4j-openapi3-boot4-spring-boot-starter</artifactId>
+ <version>5.6.0</version>
```

### 5.3 Redisson 升级

```xml
- <version>3.20.1</version>
+ <version>4.0.0</version>
```

**注意事项**：
- `RedissonAutoConfigurationV2` → `RedissonAutoConfigurationV4`
- 默认绑定 `redisson-spring-data-41`
- YAML 配置中可能需添加排除：

```yaml
spring:
  autoconfigure:
    exclude:
      - org.redisson.spring.starter.RedissonAutoConfigurationV4
```

### 5.4 PageHelper 升级

```xml
- <version>2.1.0</version>
+ <version>4.0.0</version>
```

PageHelper 4.x 最低 JDK 要求为 21，与 Boot 4 对齐。

### 5.5 AutoConfiguration.imports 路径修正

当前 11 个模块使用了非标准路径 `META-INF.spring/`，应修正为 Spring Boot 标准路径 `META-INF/spring/`：

| 模块 | 当前路径 | 修正路径 |
|------|----------|----------|
| common-web | `META-INF.spring/` | `META-INF/spring/` |
| common-verification-code | `META-INF.spring/` | `META-INF/spring/` |
| common-validation | `META-INF.spring/` | `META-INF/spring/` |
| common-translation | `META-INF.spring/` | `META-INF/spring/` |
| common-sql | `META-INF.spring/` | `META-INF/spring/` |
| common-sms | `META-INF.spring/` | `META-INF/spring/` |
| common-redis | `META-INF.spring/` | `META-INF/spring/` |
| common-mail | `META-INF.spring/` | `META-INF/spring/` |
| common-json | `META-INF.spring/` | `META-INF/spring/` |
| common-core | `META-INF.spring/` | `META-INF/spring/` |
| common-authentication | `META-INF.spring/` | `META-INF/spring/` |

---

## 6. 可能需调整项（低风险需验证）

### 6.1 `javax.*` 包引用

项目中的 `javax.*` 引用均为 **JDK 内置包**，不受 Jakarta EE 迁移影响，**无需修改**：

| 文件 | 引用 | 说明 |
|------|------|------|
| `JwtUtil.java` | `javax.crypto.SecretKey` | JDK 加密 API |
| `OllamaModelConfig.java` | `javax.sql.DataSource` | JDBC API |
| `DataBaseHelper.java` | `javax.sql.DataSource` | JDBC API |
| `MailAccount.java` | `javax.net.ssl.SSLSocketFactory` | 字符串常量 |
| `Main.java` / `Main1.java` | `javax.sound.sampled.*` | 音频 API |

### 6.2 `WebMvcConfigurer` 接口实现

3 处实现需验证兼容性：

| 文件 | 用途 | 验证方法 |
|------|------|----------|
| `WebConfig.java` (common-core) | 静态资源映射 | `addResourceHandlers()` |
| `ResourcesConfig.java` (common-web) | 拦截器注册 | `addInterceptors()` |
| `ThreadPoolConfig.java` (common-core) | 异步支持配置 | `configureAsyncSupport()` |

### 6.3 `@ConfigurationProperties` 类

17 个配置属性类需验证属性绑定兼容性：

- `JwtProperties`、`PermitUrlProperties`
- `ThreadPoolProperties`、`WebProperties`、`FileUrlProperties`、`WeatherProperties`
- `WebSocketProperties`
- `XssProperties`
- `VerificationCodeProperties`
- `SmsProperties`
- `RedissonProperties`
- `MilvusProperties`、`TtsProperties`、`AsrProperties`
- `TerminalDockerProperties`、`TerminalSecurityProperties`

**重点检查**：`spring.data.redis.*`、`spring.security.oauth2.*`、`spring.ai.*` 配置属性 key 是否在 Boot 4 中变更。

### 6.4 YAML 配置文件

`application.yaml` 和 `application-dev.yml` 中的配置属性 key 需逐一验证：

```yaml
spring:
  datasource:
    dynamic: ...        # dynamic-datasource 配置（升级后可能变化）
  data:
    redis: ...          # Redis 配置
  security:
    oauth2: ...         # OAuth2 配置
  ai: ...               # Spring AI 配置（2.0 后大量变更）
  servlet:
    multipart: ...      # 文件上传配置
server:
  undertow: ...         # Undertow 配置
```

### 6.5 其他第三方依赖版本建议

| 依赖 | 当前版本 | 建议版本 | 说明 |
|------|----------|----------|------|
| Hutool | 5.8.35 | **5.8.47** | 最新 5.x 补丁 |
| BouncyCastle | 1.72 | **1.78+** | JDK 21 支持 |
| Netty | 4.1.121.Final | **4.1.x 最新** | 补丁升级 |
| AWS S3 SDK | 1.12.400 | **1.12.770+** | 较旧，建议升级 |

---

## 7. Spring AI 2.0 迁移专题

### 7.1 版本对照

| 组件 | Boot 3.x 版本 | Boot 4.x 版本 | 状态 |
|------|----------------|----------------|------|
| Spring AI BOM | 1.1.2 | **2.0.0 GA** (2026-06-12) | ✅ 已发布 |
| Spring AI Alibaba | 1.1.2.0 | **2.x** | 🔴 开发中，无 GA |

### 7.2 Spring AI 2.0 破坏性变更

- **模块拆分**：autoconfigure 模块拆分，Starter 命名可能变更
- **Jackson 3**：底层序列化切换到 Jackson 3.x
- **Chat Client API**：`ChatClient.Builder` 等接口重构
- **Advisor API**：Advisor 链式调用方式变更
- **Vector Store API**：部分方法签名调整
- **Model 配置属性**：`spring.ai.*` 下大量属性 key 变更

### 7.3 项目中受影响的 Spring AI 依赖

| 依赖 | 模块 | 用途 | 迁移难度 |
|------|------|------|----------|
| `spring-ai-starter-model-ollama` | common-agent | Ollama 模型 | 🟡 中 |
| `spring-ai-starter-model-deepseek` | common-agent | DeepSeek 模型 | 🟡 中 |
| `spring-ai-starter-model-chat-memory-repository-jdbc` | common-agent | 对话记忆 | 🟡 中 |
| `spring-ai-alibaba-agent-framework` | common-agent | Agent 框架 | 🔴 高 |
| `spring-ai-alibaba-starter-dashscope` | common-agent | 通义千问 | 🔴 高 |
| `spring-ai-tika-document-reader` | common-vector | 文档读取 | 🟢 低 |
| `spring-ai-markdown-document-reader` | common-vector | Markdown 读取 | 🟢 低 |
| `spring-ai-pdf-document-reader` | common-vector | PDF 读取 | 🟢 低 |
| `spring-ai-advisors-vector-store` | common-vector | RAG Advisor | 🟡 中 |
| `spring-ai-starter-vector-store-milvus` | common-vector | Milvus 向量库 | 🟡 中 |
| `spring-ai-rag` | common-vector | RAG 核心 | 🟡 中 |

### 7.4 Spring AI Alibaba 迁移状态

| SAA 版本 | Spring AI | Spring Boot | 说明 |
|----------|-----------|-------------|------|
| 1.1.2.0（当前） | 1.1.2 | 3.5.x | 当前推荐，支持 Agent Skills |
| 2.x.x（开发中） | 2.0.0 | 4.0.x | GitHub Issue #91 已启动，基于 Spring AI 2.0.0-M1 |

**结论**：Spring AI Alibaba 2.x 是本项目升级到 Boot 4 的**最大阻碍**。在它发布 GA 之前，AI Agent 和 DashScope 功能无法迁移。

---

## 8. 推荐迁移步骤

### 阶段一：前置准备（当前可做）

```
1. 升级 JDK 到 21（开发环境）
2. 将 MyBatis-Plus 升级到 3.5.17（兼容 Boot 3/4 的最新版）
3. 将 Druid 升级到 1.2.28（兼容 Boot 3/4 的最新版）
4. 将 Dynamic-DataSource 升级到 4.5.0（需确认 Boot 3 兼容性）
5. 将 Redisson 升级到 3.27.x（Boot 3 最新兼容版）
6. 将 Hutool 升级到 5.8.47
7. 将 BouncyCastle 升级到 1.78+
8. 修正 AutoConfiguration.imports 目录路径
9. 修复所有 deprecation 警告
```

### 阶段二：等待生态就绪

```
1. 等待 Spring AI Alibaba 2.x GA 发布
2. 等待 Lock4j 发布 Boot 4 兼容版本（或确认手动兼容方案）
3. 确认 MapStruct-Plus、Captcha Starter 等小众依赖兼容性
```

### 阶段三：核心升级

```
1. 修改 spring-boot-starter-parent 版本为 4.0.0
2. 修改 java.version 为 21
3. 替换所有 Boot 3 专属 Starter：
   - mybatis-plus-spring-boot3-starter → spring-boot4-starter
   - dynamic-datasource-spring-boot3-starter → spring-boot4-starter
   - druid-spring-boot-3-starter → spring-boot-4-starter
4. 升级 Spring AI BOM 到 2.0.0
5. 升级 Spring AI Alibaba 到 2.x GA
6. 升级 SpringDoc 到 3.0.1
7. 替换 Knife4j 为 knife4j-next 5.6.0（换 Maven 坐标）
8. 升级 Redisson 到 4.0.0+
9. 升级 PageHelper 到 4.0.0+
```

### 阶段四：代码适配

```
1. 适配 Security 7.0 API 变更（SecurityConfig、Filter、Handler）
2. 适配 Spring AI 2.0 API 变更（ChatClient、Advisor、VectorStore）
3. 适配 Spring AI Alibaba 2.x API 变更（Agent Framework、DashScope）
4. 适配 Jackson 3.x 变更（common-json 模块、自定义序列化器）
5. 验证 WebMvcConfigurer 实现
6. 验证 @ConfigurationProperties 绑定
7. 验证 YAML 配置属性 key
8. 验证 OAuth2 配置
```

### 阶段五：测试与部署

```
1. 全模块编译通过
2. 单元测试 / 集成测试全通过
3. AI 功能端到端测试（对话、诊断、RAG、语音）
4. 安全功能测试（JWT、OAuth2、权限）
5. 性能基准测试
6. CI/CD 环境升级到 JDK 21
7. 部署环境升级
```

---

## 9. 风险评估与建议

### 9.1 风险等级汇总

| 类别 | 风险 | 说明 |
|------|------|------|
| Java 17 → 21 | 🔴 高 | 强制升级，影响全项目 |
| Spring AI Alibaba 2.x | 🔴 高 | **最大阻碍**，无 GA 则无法升级 |
| Spring AI 2.0 API | 🔴 高 | 大版本升级，破坏性变更多 |
| Jackson 3.x | 🔴 高 | 隐性影响面广，序列化/反序列化全链路 |
| Boot 3 专属 Starter | 🟡 中 | 均已有 Boot 4 版本，替换即可 |
| Spring Security 7.0 | 🟡 中 | API 变更需调整 SecurityConfig |
| SpringDoc / Knife4j | 🟡 中 | 需升级大版本 + 换 Maven 坐标 |
| Redisson 4.x | 🟡 中 | 自动配置类重命名 |
| AutoConfiguration 路径 | 🟡 中 | 需修正 11 个模块的目录结构 |
| WebMvcConfigurer | 🟢 低 | 大概率兼容 |
| Configuration Properties | 🟢 低 | 部分 key 可能变更 |
| javax.* 引用 | 🟢 低 | JDK 内置包，无需修改 |

### 9.2 关键结论

1. **当前可以升级吗？** — ❌ **不可以**。Spring AI Alibaba 2.x 尚未发布 GA，这是 AI 核心功能的硬依赖。

2. **何时可以升级？** — 当 Spring AI Alibaba 2.x 发布 GA 后。建议关注：
   - [spring-ai-alibaba/spring-ai-extensions Issue #91](https://github.com/spring-ai-alibaba/spring-ai-extensions/issues/91)
   - [Spring AI Alibaba 官方文档版本说明](https://agentic-spring-ai.github.io/website/docs/versions/)

3. **现在能做什么？** — 执行阶段一的前置准备工作：
   - 升级各依赖到 Boot 3/4 兼容的最新版本
   - 修正 AutoConfiguration 路径
   - 修复 deprecation 警告
   - 在分支上尝试 Boot 4 升级，验证非 AI 模块的兼容性

4. **升级工作量估算**：
   - POM 依赖替换：**2-3 天**
   - Security 7.0 适配：**1-2 天**
   - Spring AI 2.0 适配：**5-7 天**（API 变更多）
   - Spring AI Alibaba 2.x 适配：**3-5 天**（待 GA 后评估）
   - Jackson 3 适配：**2-3 天**
   - 配置/测试/调试：**3-5 天**
   - **总计：约 16-25 个工作日**

---

> ⚠️ 本文档基于 2026-09-10 的信息生成，第三方库兼容状态可能随时间变化，建议升级前重新确认各组件最新版本。