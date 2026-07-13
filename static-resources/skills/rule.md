# Skill 规则与使用指南

## 一、Skill 文件组织结构

### 1.1 基本原则

- **多级层级支持**：支持一级Skill和子Skill的多级文件夹结构
- **唯一标识**：每个Skill文件夹名称作为唯一标识（name字段），不可重复
- **核心文件**：每个Skill文件夹内必须包含 `SKILL.md` 文件，定义核心能力和审查规则

### 1.2 文件夹层级结构

```
skills/
├── code-reviewer/          # 一级Skill
│   ├── SKILL.md           # Skill元数据和核心规则
│   ├── examples/          # 补充资料目录（可选）
│   │   ├── demo1.py
│   │   └── demo2.java
│   └── security/          # 子Skill（二级Skill）
│       ├── SKILL.md       # 子Skill元数据和规则
│       └── checklist.md   # 补充资料
│
├── python-developer/       # 一级Skill
│   ├── SKILL.md
│   └── web-scraper/        # 子Skill
│       ├── SKILL.md
│       └── templates/
│           └── config.yml
│
└── java-architect/         # 一级Skill
    ├── SKILL.md
    └── microservices/      # 子Skill
        ├── SKILL.md
        └── spring-boot/    # 三级Skill（孙Skill）
            ├── SKILL.md
            └── config.properties
```

### 1.3 文件命名规范

- **Skill元数据文件**：必须命名为 `SKILL.md`（固定文件名）
- **文件夹命名**：小写英文 + 连字符，如 `code-reviewer`、`web-scraper`
- **补充资料**：可自由命名，支持 `.md`、`.txt`、`.py`、`.java`、`.yml` 等格式

---

## 二、SKILL.md 元数据编写规则

### 2.1 YAML Front Matter 格式

头部与尾部必须用 `---` 首尾包裹，为标准 YAML 格式：

```markdown
---
name: "skill-name"
version: "1.0.0"
description: "一句话描述核心能力"
author: "author-name"
tags: ["tag1", "tag2", "tag3"]
isIndex: 0
deleteFlag: 0
---

# Skill Title

## 角色定义
...
```

### 2.2 固定字段说明

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| `name` | String | ✅ | Skill唯一标识，小写英文+连字符，与文件夹名一致 | `"code-reviewer"` |
| `version` | String | ✅ | 语义化版本 `x.y.z` | `"1.0.0"` |
| `description` | String | ✅ | 一句话描述核心能力（简洁精准） | `"严格的代码审查技能，聚焦安全和性能"` |
| `author` | String | ✅ | 作者标识 | `"lixiyun"` |
| `tags` | Array[String] | ✅ | 领域标签，小写英文，用于分类和检索 | `["code-review", "security", "performance"]` |
| `isIndex` | Integer | ✅ | 索引文件标识（0-实际Skill文件，1-Skill索引文件） | `0` |
| `deleteFlag` | Integer | ✅ | 删除标识（0-未删除，1-已删除） | `0` |

### 2.3 系统自动填充字段

以下字段由系统自动管理，**无需在 SKILL.md 中手动定义**：

| 字段 | 类型 | 说明 | 来源 |
|------|------|------|------|
| `url` | String | Skill相对路径（如 `/skills/code-reviewer/SKILL.md`） | 系统自动计算 |


### 2.4 元数据编写示例

```markdown
---
name: "code-reviewer"
version: "1.0.0"
description: "严格的代码审查技能，聚焦安全和性能"
author: "lixiyun"
tags: ["code-review", "security", "performance"]
isIndex: 0
deleteFlag: 0
---

# Code Reviewer Skill

## 角色定义
你是一位资深代码审查者，拥有10年以上大型项目经验。

## 审查规则
1. **安全优先**：检查SQL注入、XSS、CSRF等安全漏洞
2. **性能敏感**：关注N+1查询、内存泄漏、无限循环
3. **可维护性**：函数不超过50行，圈复杂度不超过10

## 输出格式
- 🔴 P0：必须修复（安全/数据丢失风险）
- 🟡 P1：建议修复（性能/可维护性）
- 🟢 P2：可选优化（代码风格/命名）

## 使用场景
- 代码提交前的自动审查
- 技术债务识别和优先级排序
- 安全合规性检查
```

---

## 三、Skill 工具方法说明

### 3.1 AI模型可调用的工具方法

系统为AI模型提供以下工具方法，用于动态加载和操作Skill：

#### **工具1：loadFirstLevelSkills**

**用途**：加载一级Skill元数据，构建会话映射表

**调用时机**：会话开始时，AI模型首次需要访问Skill时

**返回信息**：
- 一级Skill列表（name、description、version、tags）
- context_id（用于后续操作的唯一标识）
- 会话映射表已构建提示

**示例调用**：
```
AI模型调用：loadFirstLevelSkills(conversationId=12345)
返回：{
  "success": true,
  "skills": [
    {"context_id": "abc123", "name": "code-reviewer", "description": "..."},
    {"context_id": "def456", "name": "python-developer", "description": "..."}
  ],
  "message": "一级Skill加载成功，会话映射表已构建"
}
```

#### **工具2：loadChildrenSkills**

**用途**：加载指定Skill的子Skill元数据

**参数**：
- `context_id`：父Skill的context_id（来自loadFirstLevelSkills返回）
- `conversationId`：当前会话ID

**返回信息**：
- 子Skill列表（name、description、层级关系）
- 新的context_id（用于访问子Skill）

**示例调用**：
```
AI模型调用：loadChildrenSkills(contextId="abc123", conversationId=12345)
返回：{
  "success": true,
  "children": [
    {"context_id": "xyz789", "name": "security", "description": "安全审查子技能"},
    {"context_id": "uvw012", "name": "performance", "description": "性能优化子技能"}
  ],
  "parentName": "code-reviewer"
}
```

#### **工具3：getSkillContent**

**用途**：获取Skill的具体内容（SKILL.md完整内容）

**参数**：
- `context_id`：Skill的context_id
- `conversationId`：当前会话ID

**返回信息**：
- SKILL.md完整内容（Markdown格式）
- Skill元数据
- 补充资料文件列表

**示例调用**：
```
AI模型调用：getSkillContent(contextId="abc123", conversationId=12345)
返回：{
  "success": true,
  "content": "# Code Reviewer Skill\n\n## 角色定义\n...",
  "metadata": {"name": "code-reviewer", "version": "1.0.0"},
  "supplementaryFiles": ["examples/demo1.py", "examples/demo2.java"]
}
```

#### **工具4：getSupplementaryMaterial**

**用途**：获取Skill的补充资料内容

**参数**：
- `context_id`：Skill的context_id
- `fileName`：补充资料文件名（相对路径）
- `conversationId`：当前会话ID

**返回信息**：
- 文件完整内容
- 文件类型和格式

**示例调用**：
```
AI模型调用：getSupplementaryMaterial(
  contextId="abc123", 
  fileName="examples/demo1.py",
  conversationId=12345
)
返回：{
  "success": true,
  "content": "def example_function():\n    ...",
  "fileType": "python",
  "fileName": "demo1.py"
}
```

#### **工具5：executeTerminalCommand**

**用途**：在Skill的Docker容器环境中安全执行终端命令

**参数**：
- `context_id`：Skill的context_id
- `terminalCommand`：要执行的命令（如 `python main.py`）
- `conversationId`：当前会话ID

**安全机制**：
1. **黑名单过滤**：拦截危险命令（rm -rf、sudo、chmod 777等）
2. **AI模型安全检查**：调用大模型进行语义级安全审计
3. **分布式锁保护**：防止多实例并发冲突
4. **Docker容器隔离**：完全隔离的执行环境
5. **资源限制**：CPU、内存、超时控制

**返回信息**：
- 执行结果（成功/失败）
- 标准输出（stdout）
- 错误输出（stderr）
- 退出码（exitCode）
- 执行耗时（executionTime）

**示例调用**：
```
AI模型调用：executeTerminalCommand(
  contextId="abc123",
  terminalCommand="python examples/demo1.py",
  conversationId=12345
)
返回：{
  "success": true,
  "exitCode": 0,
  "stdout": "执行成功，输出结果...",
  "stderr": "",
  "executionTime": 1234,
  "containerName": "skill-exec-abc123-1234567890"
}
```

---

## 四、会话映射表与全局影子表

### 4.1 全局影子表（Global Shadow Table）

**定义**：服务启动时构建的Skill树形结构缓存

**存储位置**：Redis（Hash类型）

**Key格式**：`skill:global:shadow:table`

**Value结构**：
```json
{
  "/skills/code-reviewer": {
    "metadata": {"name": "code-reviewer", "version": "1.0.0"},
    "absolutePath": "/absolute/path/to/code-reviewer",
    "parentPath": null,
    "childrenPaths": ["/skills/code-reviewer/security"]
  },
  "/skills/code-reviewer/security": {
    "metadata": {"name": "security", "version": "1.0.0"},
    "absolutePath": "/absolute/path/to/security",
    "parentPath": "/skills/code-reviewer",
    "childrenPaths": []
  }
}
```

**生命周期**：
- **创建**：服务启动时，通过深度优先遍历构建
- **更新**：手动触发刷新（通过API或管理界面）
- **销毁**：服务停止时自动清理

**并发控制**：使用分布式锁防止多实例重复构建

### 4.2 会话映射表（Session Mapping Table）

**定义**：每个AI会话私有的Skill访问映射

**存储位置**：
- Redis（临时缓存）
- 数据库（持久化，会话表的JSON字段）

**Key格式**：`skill:session:mapping:{conversationId}`

**Value结构**：
```json
{
  "abc123": "/skills/code-reviewer",
  "def456": "/skills/python-developer",
  "xyz789": "/skills/code-reviewer/security"
}
```

**作用**：
- 将工具生成的 `context_id` 映射到全局影子表中的Skill路径
- 不同会话完全隔离，避免数据混淆
- 支持会话恢复和历史查询

**生命周期**：
- **创建**：AI模型首次调用 `loadFirstLevelSkills` 时
- **更新**：每轮对话结束后更新会话缓存和数据库
- **销毁**：会话结束时清理Redis缓存

---

## 五、终端命令执行安全机制

### 5.1 安全流程（符合流程图）

```
参数传入
  ↓
黑名单命令过滤（静态规则）
  ↓
获取映射表value（context_id → Skill路径）
  ↓
AI模型动态安全检查（语义级审计）
  ↓
争夺Skill节点分布式锁（轮询重试）
  ↓
启动临时Docker容器（完全隔离）
  ↓
切换到Skill文件夹目录
  ↓
执行终端命令（资源限制监控）
  ↓
判断执行成功 → 返回结果
```

### 5.2 黑名单命令列表

以下命令将被直接拦截，不执行后续检查：

| 命令模式 | 说明 |
|---------|------|
| `rm -rf /` | 删除根目录 |
| `sudo *` | 提权命令 |
| `chmod 777 *` | 危险权限设置 |
| `dd if=/dev/zero of=/dev/sda` | 磁盘擦除 |
| `:(){ :|:& };:` | Fork炸弹 |
| `wget * | sh` | 远程脚本执行 |
| `curl * | bash` | 远程脚本执行 |
| `$(command)` | 命令注入模式 |
| `` `command` `` | 命令注入模式 |

### 5.3 AI模型安全检查

**审计标准**：
1. 命令是否会修改系统关键文件或目录
2. 命令是否会泄露敏感信息
3. 命令是否会消耗过多系统资源
4. 命令是否有隐藏的恶意意图
5. 命令是否符合正常的开发/构建/运行操作

**响应格式**：
- **安全**：回复 `SAFE`
- **不安全**：回复 `UNSAFE: [具体原因]`

**配置项**（可通过yaml调整）：
```yaml
skill:
  terminal:
    security:
      enabled: true                    # 是否启用AI检查
      timeout-seconds: 30             # 检查超时时间
      default-allow-on-failure: true   # 失败时的默认行为
```

### 5.4 Docker容器隔离配置

**默认配置**：
```yaml
skill:
  terminal:
    docker:
      base-image: alpine:latest       # 轻量级镜像（~5MB）
      memory-limit: 512m              # 内存限制
      cpu-limit: 1.0                  # CPU限制（核数）
      timeout-seconds: 120            # 执行超时
      network-mode: none              # 网络隔离（无网络访问）
      read-only-fs: true              # 只读根文件系统
```

**容器特性**：
- **自动删除**：`--rm` 参数，执行完毕后自动清理
- **卷挂载**：Skill目录挂载到 `/workspace`（读写）
- **工作目录**：自动切换到 `/workspace`
- **资源限制**：CPU、内存、超时控制
- **网络隔离**：禁止网络访问（最安全）
- **只读文件系统**：除挂载点外，根文件系统只读

### 5.5 分布式锁机制

**用途**：防止多实例并发执行同一Skill的命令

**配置项**：
```yaml
skill:
  terminal:
    docker:
      lock-key-prefix: "skill:terminal:lock:"
      lock-max-retries: 30            # 最大重试次数
      lock-retry-interval-ms: 1000    # 重试间隔（毫秒）
      lock-lease-time-seconds: 300    # 锁持有时间（秒）
```

**锁Key格式**：`skill:terminal:lock:{normalizedSkillPath}`

**工作流程**：
1. 尝试获取锁（最多重试30次）
2. 成功获取 → 执行命令
3. 执行完成 → 释放锁
4. 获取失败 → 等待重试或放弃

---

## 六、配置化支持

### 6.1 配置文件位置

**配置示例文件**：`common-agent/src/main/resources/skill-terminal-example.yml`

**实际配置文件**：`application.yml` 或 `application-{profile}.yml`

### 6.2 主要配置项

#### **AI安全审计配置**
```yaml
skill:
  terminal:
    security:
      enabled: true                    # 是否启用AI检查
      timeout-seconds: 30             # 检查超时
      default-allow-on-failure: true   # 失败默认行为
      system-prompt: "..."            # 自定义Prompt（可选）
      safe-response: "SAFE"           # 安全响应标识
      unsafe-response-prefix: "UNSAFE:" # 不安全响应前缀
```

#### **Docker容器配置**
```yaml
skill:
  terminal:
    docker:
      base-image: alpine:latest       # 基础镜像
      memory-limit: 512m              # 内存限制
      cpu-limit: 1.0                  # CPU限制
      timeout-seconds: 120            # 执行超时
      network-mode: none              # 网络模式
      read-only-fs: true              # 只读文件系统
      workspace-dir: /workspace       # 工作目录
```

#### **分布式锁配置**
```yaml
skill:
  terminal:
    docker:
      lock-key-prefix: "skill:terminal:lock:"
      lock-max-retries: 30            # 最大重试次数
      lock-retry-interval-ms: 1000    # 重试间隔
      lock-lease-time-seconds: 300    # 锁持有时间
```

### 6.3 多环境差异化配置

**开发环境**（application-dev.yml）：
```yaml
skill:
  terminal:
    security:
      default-allow-on-failure: true   # 可用性优先
    docker:
      network-mode: bridge             # 允许网络访问（调试需要）
      timeout-seconds: 300             # 更长超时（调试）
```

**生产环境**（application-prod.yml）：
```yaml
skill:
  terminal:
    security:
      default-allow-on-failure: false  # 安全性优先
    docker:
      network-mode: none               # 严格网络隔离
      timeout-seconds: 120             # 标准超时
      lock-max-retries: 50             # 高并发支持
```

---

## 七、最佳实践建议

### 7.1 Skill编写建议

1. **元数据简洁精准**：description不超过50字，tags不超过5个
2. **规则清晰分层**：按优先级（P0/P1/P2）组织审查规则
3. **示例丰富**：补充资料提供实际案例和模板
4. **版本管理**：重大变更时更新version字段

### 7.2 文件组织建议

1. **一级Skill**：定义核心能力，作为入口
2. **子Skill**：细化专项能力，继承父Skill规则
3. **补充资料**：按类型分类存放（examples、templates、docs）
4. **命名规范**：文件夹名与name字段保持一致

### 7.3 安全执行建议

1. **优先使用白名单命令**：明确允许的命令类型
2. **避免复杂命令链**：减少管道符和变量替换
3. **监控执行耗时**：超时命令及时优化
4. **定期清理容器**：避免资源累积

### 7.4 配置优化建议

**高并发场景**：
```yaml
lock-max-retries: 50           # 增加重试次数
lock-retry-interval-ms: 2000   # 降低Redis压力
```

**长任务场景**：
```yaml
timeout-seconds: 600           # 支持长时间任务
lock-lease-time-seconds: 900   # 锁持有时间同步增加
```

**低延迟场景**：
```yaml
lock-max-retries: 10           # 减少等待
lock-retry-interval-ms: 500    # 快速重试
```

---

## 八、常见问题与解决方案

### Q1：context_id找不到数据怎么办？

**原因**：会话映射表中无对应记录

**解决方案**：
1. 检查context_id参数是否正确
2. 如果参数正确，重新调用 `loadFirstLevelSkills` 从头开始查询

### Q2：命令执行超时怎么办？

**原因**：命令运行时间超过配置的超时限制

**解决方案**：
1. 检查命令是否有死循环或阻塞操作
2. 调整 `timeout-seconds` 配置（yaml）
3. 优化命令逻辑，减少执行时间

### Q3：分布式锁获取失败怎么办？

**原因**：其他实例正在执行同一Skill的命令

**解决方案**：
1. 等待当前执行完成（自动重试）
2. 调整 `lock-max-retries` 和 `lock-retry-interval-ms` 配置
3. 检查是否有长时间未释放的锁（异常情况）

### Q4：Docker容器启动失败怎么办？

**原因**：Docker服务未启动或镜像不存在

**解决方案**：
1. 检查Docker服务状态：`docker ps`
2. 检查镜像是否存在：`docker images | grep alpine`
3. 拉取所需镜像：`docker pull alpine:latest`
4. 检查权限：确保用户有Docker执行权限

---

## 九、版本更新记录

### v1.0.0（2026-07-12）

**新增功能**：
- ✅ 多级Skill支持（一级Skill和子Skill）
- ✅ 会话映射表机制（context_id）
- ✅ 全局影子表（Redis缓存）
- ✅ 终端命令执行工具（Docker容器隔离）
- ✅ AI模型动态安全检查
- ✅ 分布式锁并发控制
- ✅ 完全配置化支持（yaml）

**优化改进**：
- ✅ Docker执行结果实体类封装（类型安全）
- ✅ 分布式锁常量配置化
- ✅ AI安全检查参数可配置
- ✅ 日志增强（包含配置信息）

**废弃功能**：
- ❌ 主机直接执行方式（已完全替换为Docker容器）

---

## 十、附录

### 附录A：完整Skill示例

**文件夹结构**：
```
skills/
└── code-reviewer/
    ├── SKILL.md
    ├── examples/
    │   ├── good-code.py
    │   └── bad-code.java
    └── security/
        ├── SKILL.md
        └── checklist.md
```

**一级Skill：SKILL.md**
```markdown
---
name: "code-reviewer"
version: "1.0.0"
description: "严格的代码审查技能，聚焦安全和性能"
author: "lixiyun"
tags: ["code-review", "security", "performance"]
---

# Code Reviewer Skill

## 角色定义
你是一位资深代码审查者，拥有10年以上大型项目经验。

## 审查规则
1. **安全优先**：检查SQL注入、XSS、CSRF等安全漏洞
2. **性能敏感**：关注N+1查询、内存泄漏、无限循环
3. **可维护性**：函数不超过50行，圈复杂度不超过10

## 输出格式
- 🔴 P0：必须修复（安全/数据丢失风险）
- 🟡 P1：建议修复（性能/可维护性）
- 🟢 P2：可选优化（代码风格/命名）

## 使用场景
- 代码提交前的自动审查
- 技术债务识别和优先级排序
- 安全合规性检查
```

**子Skill：security/SKILL.md**
```markdown
---
name: "security"
version: "1.0.0"
description: "安全专项审查子技能"
author: "lixiyun"
tags: ["security", "vulnerability", "compliance"]
---

# Security Review Skill

## 角色定义
专注于安全漏洞识别和合规性检查的子技能。

## 审查清单
1. SQL注入检测
2. XSS漏洞识别
3. CSRF防护验证
4. 敏感信息泄露检查
5. 权限控制审查

## 输出格式
- 🔴 Critical：高危漏洞（立即修复）
- 🟠 High：中危漏洞（优先修复）
- 🟡 Medium：低危漏洞（计划修复）
```

### 附录B：配置文件完整示例

**application.yml**
```yaml
skill:
  terminal:
    security:
      enabled: true
      timeout-seconds: 30
      default-allow-on-failure: true
      system-prompt: |
        你是一个终端命令安全审计专家。你的任务是分析用户提交的终端命令是否存在安全风险。
        
        审计标准：
        1. 命令是否会修改系统关键文件或目录
        2. 命令是否会泄露敏感信息
        3. 命令是否会消耗过多系统资源
        4. 命令是否有隐藏的恶意意图
        5. 命令是否符合正常的开发/构建/运行操作
        
        当前Skill目录：%s
        
        请严格按以下格式回复，不要输出其他内容：
        - 如果命令安全，回复：SAFE
        - 如果命令不安全，回复：UNSAFE: [具体原因]
      user-message-template: |
        请审计以下终端命令的安全性：
        
        命令内容：%s
        
        工作目录：%s
      safe-response: "SAFE"
      unsafe-response-prefix: "UNSAFE:"

    docker:
      base-image: alpine:latest
      memory-limit: 512m
      cpu-limit: 1.0
      memory-swap: 512m
      timeout-seconds: 120
      network-mode: none
      read-only-fs: true
      workspace-dir: /workspace
      cleanup-timeout-seconds: 10
      container-name-prefix: skill-exec-
      lock-key-prefix: "skill:terminal:lock:"
      lock-max-retries: 30
      lock-retry-interval-ms: 1000
      lock-lease-time-seconds: 300
```

---

**文档版本**：v1.0.0  
**最后更新**：2026-07-12  
**维护者**：lixiyun