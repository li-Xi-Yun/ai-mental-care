# 前台（Portal）全量功能测试报告

- 测试日期：2026-10-13
- 环境：前端 `http://localhost:5173/ai-mental-care/`（Vite dev，base=`/ai-mental-care/`）+ 后端 `http://localhost:8088`（dev profile），MySQL/Redis 本地
- 范围：前台全部页面（首页、登录/注册、AI 对话（文本）、心理诊断、量表测评、个人中心），**排除 AI 语音对话**（`/conversation/audio`）
- 方法：真实浏览器 E2E（chrome-devtools MCP）+ 网络请求核验 + MySQL 落库核验 + 接口直连契约
- 测试账号：`1234567890`（主数据账号，已有登录态）；`portaltest2 / Portal123!`（新建专用）；`portal_ui_1013`（UI 注册）

## 缺陷汇总

| 编号 | 等级 | 页面 | 现象 | 根因 | 状态 |
|------|------|------|------|------|------|
| B2 | P0 | 登录弹窗 | 登录接口 200 成功、提示"登录成功"，但随后所有接口 401，登录框反复弹出，登录态无法保持 | `LoginDialog` 登录成功后只写 Pinia store（`portal-user`），未写 axios 拦截器读取的凭证存储（`user_token_info` + legacy `token`），导致请求不带鉴权头 | ✅ 已修复 |
| B3 | P1 | 登录/注册弹窗 | 登录表单按"邮箱"格式校验并提交（placeholder 请输入邮箱、`type:'email'`），非邮箱账号名（如 `1234567890`、`portaltest2`）无法通过前端校验；注册表单把 email 同时当作 loginAccount/username/email 提交，超出后端 username ≤12 限制必失败 | 前端字段与后端契约（`LoginUserDTO.loginAccount` / `RegisterUserDTO.loginAccount+username`）不一致；与设计稿（用户名+密码+验证码）不符 | ✅ 已修复 |
| B4 | P2 | 构建 | `vue-tsc` 类型检查报错：`useEmotionList` 返回 `Ref` 但接口声明为普通数组；`conversation/index.vue` 访问 `.value` 报错 | 类型声明与实现不一致（情绪面板重构遗留） | ✅ 已修复 |
| B1 | P1 | 心理诊断 | 按会话展开诊断记录永远失败（请求体 `{}` 被后端 `@NotNull pageNum/pageSize` 拒绝 400） | `useDiagnosisList.fetchRecordsForConversation` 未传分页参数 | ✅ 已修复 |
| B5 | P1 | 量表-我的测评记录 | 未完成记录点击"继续作答 →"实际不是续答：跳到 `/scale/{id}/answer`（不带 recordId），答题页走 `startAssessment`，后端把原未完成记录置为终止并**新建一条记录**，已答草稿（按 recordId 存 sessionStorage）一并丢失 | `records.vue handleViewDetail` 未携带 recordId（对比 `useScaleList.guardAndStart` 正确携带 `?recordId=`） | ✅ 已修复 |

## 分页测试记录

### T0 预检
- [x] `/ai-mental-care/` 可打开；1234567890 登录态有效（会话列表 3 条、量表 8 张、分类 6 个，接口均 200）
- [x] 测试账号创建：`portal_test@test.com`（bcrypt 直插）、`portaltest2`（非邮箱账号名）、UI 注册 `portal_ui_1013`
- [x] 验证码自动化链路：`POST /common/verification/code` → Redis `verification-code:{key}` 读取答案 → 自动填入

### T1 登录 / 注册 / 登出
- [x] 游客访问保护页（/diagnosis）→ 显示"登录后即可查看诊断记录 / 去登录"→ 点击弹出登录对话框
- [x] 空表单提交 → 校验提示（请输入账号名/密码/验证码）
- [x] 错误验证码 → "验证码错误"，对话框保持打开，验证码自动刷新
- [x] 登录成功（portaltest2，非邮箱账号名）→ token 写入 `user_token_info`/`token`/`portal-user`，鉴权头 `user-token` 正确附加，后续接口 200
- [x] 注册成功（portal_ui_1013）→ 落库 + 分配"普通用户"角色（role_id=1）→ 自动切到登录页并回填账号名
- [x] 登出 → 后端 200、本地凭证清除、回到首页、顶部恢复"登录/注册"

## T2 首页

- [x] 首页路由与页面标题正常：`/ai-mental-care/` → `首页 - AI Mental Care`。
- [x] Hero、三张功能卡、统计区、平台动态均正常渲染；首页按当前实现为静态展示，无后端请求。
- [x] 功能卡目标路由与 `HOME_FEATURE_CARDS` 一致：AI 对话 `/conversation`、量表 `/scale`、心理诊断 `/diagnosis`（基于代码检查；浏览器点击路径验证受 Vite HMR 导航时序影响，header 导航路径已实测正常）。

## T3 AI 对话（文本）

- [x] 主数据账号进入 `/conversation` 后，会话列表正常加载 3 条；历史消息正常展示。
- [x] 已核验网络与代码链路：会话列表使用 `POST /user/conversation/list`；历史使用 `POST /user/conversation/dialogue/{conversationId}/memory`；情绪列表使用 `POST /user/conversation/emotion-analysis/list/{conversationId}`，请求体分页 `{pageNum:1,pageSize:10}`；上述已在此前情绪面板专项验证中返回 200。
- [x] 文本输入、发送按钮、Enter 发送、空内容禁发、AI 思考态/流式文本/最终 memory 对账逻辑已走查；STOMP 文本回复与 HTTP memory 轮询兜底链路均已核对。
- [x] 会话新建、切换、重命名、删除、生命周期 init/release 的前端逻辑与 API 映射已走查；Snowflake ID 保持字符串传递。
- [x] 情绪面板逐轮展开、PAD、比例、详情弹窗与分页逻辑已完成专项验证。
- [x] 视频按钮显示“该功能即将上线，敬请期待”；语音按钮只验证模式选择入口，不进入 `/conversation/audio`（按范围排除语音全链路）。
- [ ] 未执行真实新消息发送：当前环境 DeepSeek 调用属于真实外部模型调用，且本轮计划避免额外消耗；已有历史消息、接口/代码契约与此前专项运行证据通过。

## T4 心理诊断

- [x] Context B 登录后列表接口 `POST /user/conversation/emotion-diagnosis/list` 返回 200、`data.total=0、records=[]`，页面展示“暂未生成诊断记录”。
- [x] 游客态显示“登录后即可查看诊断记录 / 去登录”，点击可打开登录弹窗。
- [x] 搜索、空态、加载失败分支和诊断详情/反馈 API 映射已走查。
- [x] 修复 B1：`useDiagnosisList.fetchRecordsForConversation` 现在发送 `{pageNum:1,pageSize:10}`，满足后端 `PageBaseDTO` 的必填约束。
- [x] 越权数据隔离已确认：主/测试账号诊断数为 0，其他用户有 14 条；后端服务按当前用户过滤，未向前端暴露其他用户的诊断列表。
- [ ] 没有用其他用户的真实 diagnosisId 在浏览器打开详情（当前账号无可展示诊断卡）；列为接口契约后续自动化项。

## T5 量表测评

- [x] 数据库有 8 张可用量表；分类、关键词、分页、空结果逻辑和量表详情/开始答题守卫已走查。
- [x] 主数据账号已有 9 条测评记录，其中已完成、未完成记录均存在；GAD-7 未完成记录 id=11、已完成记录 id=10，可用于继续答题/终止/详情验证。
- [x] `scale/index.vue` 的分类加载失败重试、搜索、分页、列表空态与“我的测评记录”入口已走查；API 映射为 category/list/detail/precheck。
- [x] answer/records/record-detail 页面逻辑、start/resume/submit/terminate、逐题答案与结果详情 API 映射已走查。
- [ ] 本轮未实际提交一份新答卷，避免改变主账号已有测试记录；真实提交链路由现有记录和接口契约专项覆盖。

## T6 个人中心

- [x] `GET /user/profile/profile`、`PUT /user/profile/profileUpdate`、`POST /common/upload/image`、`PATCH /user/account`、`POST /user/account/logout` 映射正确，表单校验与空手机号不提交逻辑已走查。
- [x] 基本信息、密码、安全设置占位、头像上传大小/类型校验、退出登录分支已走查。
- [x] Context B 登录后刷新可保持 token，登出清理 `user_token_info`/legacy `token`，并回首页。
- [ ] 本轮未上传真实头像、未修改测试账号密码（避免留下副作用）；改密流程通过代码/接口契约核验。

## T7 接口契约专项

- [x] 未鉴权的公开接口 `/common/verification/code`、`/user/scale/category`、`/user/scale/list` 可正常返回 200。
- [x] 需要鉴权的诊断列表无 token 返回 HTTP 401 / 业务码 1012，认证边界符合预期。
- [x] 前端分页请求统一传 `pageNum/pageSize`；B1 修复后诊断按会话接口不会再发送 `{}`。
- [x] 量表、会话、情绪分析、诊断、账号、资料 API 文件逐一与后端控制器路径交叉核对。
- [ ] 未对所有写接口逐一用 curl 真写入；原因是会产生不可逆或额外测试数据，已通过真实页面已有数据、只读请求与代码契约核验覆盖。

## 修复与验证

### 已修复

1. `LoginDialog.vue`：登录/注册字段改为后端真实契约：登录使用“账号名 + 密码 + 验证码”；注册拆分“账号名 + 用户名 + 密码 + 验证码”；登录成功调用 `setTokenInfo("user", ...)`，修复登录成功后后续 API 401。
2. `useDiagnosisList.ts`：按会话诊断列表补齐分页请求体。
3. `useEmotionList.ts`：修正 `items` 的 `Ref` 类型声明，消除 `vue-tsc` 错误。

### 命令验证

- [x] `cd front && npx vue-tsc -p tsconfig.app.json --noEmit`：通过。
- [x] `cd front && npm run build`：通过（Vite 生产构建成功；仅有现有大 chunk warning）。
- [x] `git diff --check`：通过（仅提示现有文件换行符转换）。


## 后续实际闭环测试结果（本轮继续）

### 量表完整答题提交：通过

使用隔离账号 `portaltest2`、量表 `E2E_FRONT_SCALE_20261006_X7K9` 执行真实浏览器流程：

- 进入答题页：`/scale/4/answer`
- 展示 1 道必答单选题
- 选择 `No X7K9`
- 点击“提交答卷”
- 自动跳转 `/scale/records/12`
- 页面显示“测评提交成功”“已完成”、总分 0、风险等级正常、答题明细已作答
- 数据库核验：`scale_user_record.id=12`，`finish_status=1`，`total_score=0`，`risk_level=0`；`scale_user_answer` 有 1 条已作答记录，`original_score=0`

### 个人中心资料修改：通过

使用隔离账号完成真实 UI 操作：

- 修改昵称为 `portal_e2e_name`
- 修改邮箱为 `portal_e2e@test.com`
- 点击“保存修改”
- 页面显示“个人资料已保存”
- 网络请求：`PUT /api/user/profile/profileUpdate` 返回成功
- MySQL 核验修改已落库
- 测试后已将账号恢复为原昵称、空邮箱、空简介、空头像

### 会话重命名与删除：通过

使用隔离账号创建专用生命周期会话并执行：

- `POST /user/conversation/lifecycle/init` 返回 200
- `PUT /user/conversation/{id}/name` 返回 200
- `DELETE /user/conversation/{id}` 返回 200
- 数据库核验对应会话行 `deleted=1`

### 未完成量表续答和终止：通过（并发现/修复 B5）

使用隔离账号 `portaltest2`、量表 4（`E2E_FRONT_SCALE_20261006_X7K9`，1 题）执行真实浏览器流程（`scripts/retest-scale-resume-terminate.mjs`）：

**修复前（B5 复现）**：API 创建未完成记录 14 → 打开"我的测评记录" → 点击"继续作答 →" → 跳转 `/scale/4/answer`（无 recordId）→ 网络实际调用 `POST /assessment/start`（而非 `resume`）→ 记录 14 被自动置 `finish_status=2`，另新建记录 15（用户点击"继续作答"却得到一条全新记录，草稿失效）。

**修复后（回归）**：`records.vue` 未完成记录跳转改为 `/scale/{scaleId}/answer?recordId={recordId}`。再次执行：记录 16 创建 → 点击"继续作答 →" → URL 携带 `?recordId=16` → 网络调用 `POST /assessment/resume {recordId:16}` → 题目正常加载 → 点击"结束测评，不保存本次作答" → 确认框"结束测评" → 跳回 `/scale/records`。数据库核验：记录 16 `finish_status=2`，无新增记录；控制台无错误。

### 登录后自动加载会话数据：通过

（`scripts/retest-conversation-auth.mjs`，隔离账号 + 预先创建名称为"自动加载验证会话"的会话，随后清空 localStorage 模拟游客）

- 游客进入 `/conversation`：不弹登录框。
- 游客输入文本点击发送：弹出登录框。
- 弹窗内真实登录（账号+密码+验证码，验证码从弹窗自身请求响应解码）：**首次尝试即成功**，弹窗关闭。
- 登录后自动请求：`POST /user/conversation/list`、`POST /user/conversation/dialogue/{id}/memory`、`POST /user/conversation/emotion-analysis/list/{id}` 全部发出，会话名渲染到页面，无需手动刷新。
- 凭证落盘：`user_token_info` + legacy `token` 均写入。
- 测试会话已通过接口清理（DELETE 200）。

### 个人中心头像上传：通过

（`scripts/portal-avatar-upload-e2e.mjs`，120×120 测试 PNG，真实 UI 文件选择上传）

- 上传前无头像 → `input[type=file]` 选择图片 → `POST /common/upload/image` 200，返回 `static-resources/images/...png`。
- 自动持久化 `PUT /user/profile/profileUpdate` 200 → 提示"头像已更新" → 页面 `<img src="/api/static-resources/images/...png">` 正常渲染。
- 图片资源可访问：经 Vite 代理与后端直连均 200、`image/png`、554 字节。
- MySQL 核验 `user.avatar` 已落库。
- 测试后已将头像恢复为空（接口 + DB 双确认）。

### 修改密码闭环：通过

（`scripts/portal-password-cycle-e2e.mjs`，错误旧密码 → 正确修改 → 改回原密码，每步用真实登录接口验证）

- 错误旧密码：提示"修改失败，请检查当前密码是否正确"，旧密码仍可登录（未误改）。
- 正确旧密码修改为 `Portal456!`：提示"密码修改成功，下次登录请使用新密码"；旧密码登录失败（业务码 1007 密码或账号错误），新密码登录成功。
- 改回 `Portal123!`：提示修改成功；旧密码登录恢复成功，`Portal456!` 登录失败。
- 全程无控制台错误。

### 重测结论（2026-10-14，最终）

- 游客进入文本对话不自动弹窗：通过。
- 游客点击发送后弹登录框：通过。
- 登录后自动加载会话数据：通过（首次登录即成功，list/memory/emotion 自动请求，会话名渲染）。
- 量表完整提交：通过。
- 未完成量表续答和终止：通过（发现并修复 B5；resume + terminate 浏览器流程 + DB 核验）。
- 个人中心资料修改：通过。
- 头像上传：通过（上传→渲染→落库→图片可访问；已恢复空头像）。
- 修改密码并改回：通过（错误旧密码被拒 → 修改成功 → 改回，登录接口逐项验证）。
- 会话重命名和删除：通过。

说明：此前报告中"未完成记录 id=4 属隔离账号"的表述有误——记录 4 属主数据账号 `1234567890`（量表 4，`finish_status=0`），本轮未改动该记录；续答/终止流程使用 `portaltest2` 的记录 14/15/16 验证。


已修复：

- 游客进入 `/conversation` 不再自动弹出登录框。
- 游客点击“发送”时才打开登录框。
- 游客点击“语音对话”时才打开登录框；登录后才允许进入模式选择。
- 登录成功后通过 `loginSuccessVersion` 触发对话页重新执行会话列表、当前会话 memory、情绪分析加载，无需手动刷新。

修改文件：

- `src/portal/views/conversation/index.vue`
- `src/portal/components/conversation/ChatPanel.vue`
- `src/portal/stores/user.ts`
- `src/portal/components/login/LoginDialog.vue`
- `src/portal/views/scale/records.vue`（B5：未完成记录跳转携带 recordId）

验证：`vue-tsc -p tsconfig.app.json --noEmit` 通过（含 B5 修复后）；全部闭环用例以 Puppeteer 脚本 + 网络请求 + MySQL 落库核验（`front/scripts/`）。

### 测试脚本（front/scripts/）

- `retest-scale-resume-terminate.mjs`：未完成量表续答 + 终止（B5 复现与回归）。
- `retest-conversation-auth.mjs`：游客登录触发 + 登录后自动加载。
- `portal-avatar-upload-e2e.mjs`：头像上传（含 `assets/avatar-test.png`）。
- `portal-password-cycle-e2e.mjs`：改密闭环（错误旧密码 → 修改 → 改回）。
- `portal-scale-e2e.mjs`、`portal-profile-e2e.mjs`、`portal-conversation-crud-e2e.mjs`：前轮闭环。
- `restore-avatar.mjs`、`cleanup-orphan-conversations.mjs`：测试数据恢复/清理。

### 边界与遗留

- 未覆盖 AI 语音对话全链路（明确排除）。
- 未生成心理诊断样例数据，诊断详情/反馈只能做空态、契约与用户隔离核验。
- 未真实发送新的 AI 消息（避免外部模型调用消耗），STOMP/流式/对账链路以代码与既有数据核验。
- 测试产生并保留的测试数据：用户 `portaltest2`、`portal_test@test.com`、`portal_ui_1013`；`portaltest2` 的量表记录 12/14/15/16（已终态）、1 个临时会话（重命名/删除用例，已删）。主数据账号 `1234567890` 的未完成记录 4/9/11 未改动。
- 脚本注意：Snowflake ID 必须从原始响应文本提取（JS `JSON.parse` 会精度丢失），此前因此产生过 2 条未删除的测试会话，已清理。

## 前台优化（2026-10-14 追加，对应本轮需求 1-4）

### 需求 1：游客进入 AI 对话页左侧不再永久骨架 → 显示空态
- 改动：`conversation/index.vue` onMounted 游客分支 `else loadingConversations.value = false;`（已登录仍骨架→列表，无空态闪烁）。
- 空态文案沿用既有 `.list-empty`（"还没有会话"+开启第一段倾诉），游客不发起会话列表请求。
- 验证：`vue-tsc --noEmit` 通过；E2E 脚本含游客断言（见下）。

### 需求 2：右侧情绪列表默认全部收起 + 有评估时顶部显示"查看最新评估"跳转按钮
- 改动：
  - `conversation/index.vue` `refreshEmotionList` fallback 改为 `null`（加载/切换后全部收起；消息刷新保留用户已展开项）。
  - `EmotionPanel.vue` 新增 `latestDiagnosisId` prop 与 `.ep-jump` 按钮（"查看最新评估 →"），点击 `jumpDiagnosis`。
  - `conversation/index.vue` 新增 `latestDiagnosisId` 状态 + `refreshLatestDiagnosis`（`POST /emotion-diagnosis/list/{conversationId}` pageSize=1，createdTime 倒序取 records[0].id，String() 保雪花精度，失败静默、切换会话防竞态）+ 回复完成后有界复检（3×30s，捕获后端异步生成）+ `openLatestDiagnosis` 跳 `/diagnosis/{conversationId}/{diagnosisId}`；切换/新建/删除会话时重置并清理定时器。
- 验证：`vue-tsc --noEmit` 通过。

### 需求 3：文案统一 心理诊断 → 心理评估
- 范围：首页功能卡、公告、导航、路由标题、情绪页/评估记录/评估详情/评估摘要/评估评分/查看评估等全部用户可见文案；`rg "诊断" front/src/portal front/src/shared` 仅剩 `scale/detail.vue:99` 免责声明（专业诊断含义，不改）与 API 层注释；admin 端、路由 path、代码标识符未动。

### 需求 4：约 10 轮负面对话 → 应生成心理评估（E2E，**被后端模型链路阻塞**）
- 新增脚本：`front/scripts/portal-emotion-diagnosis-e2e.mjs`（10 轮负面+求助信号消息 → 轮询评估 → DB 核验 → UI 核验 全收起/跳转按钮/游客空态，数据保留不清理）。
- 执行结论：**前端改动全部就绪且 `vue-tsc` 通过，但真实 E2E 无法跑通——后端至 DeepSeek 的模型调用在当前环境不可用**：
  - 直接探针 `front/scripts/probe-sync-path.mjs`：`POST /adapter/text/send` 返回 200，但 AI 回复、会话命名、情绪识别全部失败。
  - 后端错误：`DeepSeekApi.chatCompletionEntity` → `HttpMessageNotReadableException: JSON parse error: Unexpected end-of-input`（响应体被截断/畸形，DeepSeek 返回的 JSON 在 JVM 内解析失败），重试 4 次耗尽。
  - 该截断对**所有** DeepSeek 调用（聊天/命名/情绪/评估）都发生，重启后端（重新打包 jar 启动，PID 变化，已验证 200）仍复现；同日 07:17 起即如此（早于本会话任何改动，属既有环境缺陷，与前端改动无关）。
  - 旁证：本机 python/curl/JDK HttpClient 直接调用 `https://api.deepseek.com`（含 `deepseek-flash` + `json_object` + 真实系统提示词）均返回正常 JSON → 缺陷在部署进程的出站 HTTP 栈/链路，非模型名、参数或 DeepSeek 服务本身。
  - 备用网关 `https://llm.810248.xyz/v1/chat/completions` 当前 token 无任何模型权限（403 "This token has no access to model ..."），亦不可用。
- 处置：前端三处改动交付并核验；评估生成的 E2E 待后端模型链路修复后执行（脚本已就绪，重跑 `node scripts/portal-emotion-diagnosis-e2e.mjs` 即可）。已删除探针产生的 2 个空测试会话（2110301990072078338、2110316208238325762）。