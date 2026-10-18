# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

AI Mental Care — a Spring Boot + Spring AI mental-health platform (Chinese language codebase/comments). Users get an AI companion conversation, per-round emotion analysis, psychological scale (量表) assessments, and a structured diagnosis report (诊断书), exposed through a Vue 3 portal + admin UI. `README.md` (in Chinese) is the authoritative feature/architecture overview; this file records the working details that require reading multiple files to discover.

**Key stack:** Java 25, Spring Boot 3.5, Spring AI + Alibaba Agent Framework (`spring-ai-alibaba` graph/StateGraph), MyBatis-Plus + dynamic-datasource, Redis/Redisson, Milvus vector DB, WebSocket/STOMP, Spring Security + dual JWT. AI providers: DeepSeek, Alibaba DashScope (Qwen), Ollama, and an OpenAI-compatible gateway (default dev model: `deepseek-v4-flash-vision-exp`).

## Build & run commands

Backend (Maven multi-module: `pojo` → `common` → `server`; root pom aggregates):

```bash
mvn clean install -DskipTests        # compile all modules at repo root
cd server && mvn spring-boot:run -Dspring-boot.run.profiles=dev   # start on :8088
```

- Maven profiles `local | dev (default) | test | prod` set `profiles.active`, which is injected into `server/src/main/resources/application.yaml` via `@profiles.active@` (resource filtering must stay enabled).
- `application-dev.yml` holds real dev credentials (DB/Redis/Milvus/Volcengine ASR/TTS/weather) and is **gitignored** — do not commit changes to it. A local MySQL `ai_mental_care` + Redis are required; `server/src/main/resources/sql/initialization.sql` seeds the RBAC `permission` rows (admin permission management needs it kept in sync with `@PreAuthorize` perms added in code).
- API docs at `http://localhost:8088/doc.html` (Knife4j). Startup forces `TimeZone.setDefault(UTC)`.

Tests: surefire is configured with `<skipTests>true</skipTests>` and `<groups>${profiles.active}</groups>`, so tests are excluded by default and JUnit 5 `@Tag`-filtered by the active Maven profile. The existing tests (`server/src/test/.../ServerApplicationTests`, `AudioTest`) use JUnit 5 with no `@Tag`, so they only run when the profile group filter is overridden:

```bash
mvn test -DskipTests=false -pl server -Dgroups= -Dtest=ServerApplicationTests
```

Frontend (`front/`, Vue 3 + TS + Vite 6 + Element Plus + Pinia):

```bash
cd front
npm install
npm run dev        # Vite on :5173, base /ai-mental-care/, /api proxied to :8088
npm run build      # vue-tsc -b && vite build
npm run lint       # eslint
npm run typecheck  # vue-tsc --noEmit
```

Frontend testing is done via real-browser E2E scripts under `front/scripts/*.mjs` (puppeteer-core + Chrome/Edge) plus `front/e2e-tests/`; `front/TEST_REPORT_portal.md` documents prior full-coverage testing methodology (captcha auto-solve via Redis, interface contract verification).

## Repository layout

- `pojo/` — data models only: `entity/` (DB entities, incl. `conversation`, `emotion`, `scale`, `quota`, `config`), `dto/`, `vo/`, `bo/` (business objects; the crucial `bo.conversation.ConversationProcessContextBO` is the per-round conversation context carried through the whole pipeline).
- `common/` — 19 capability modules (`common-core`, `common-json`, `common-redis`, `common-sql`, `common-authentication`, `common-aop`, `common-websocket`, `common-vector`, `common-agent`, …) plus `common-bom`. Server depends on most of them. Error handling lives in `common-core.error` (`BusinessException` + `ExceptionGlobalHandler` + per-domain `*ExceptionEnum`); unified `Result<T>`/`PageResult<T>` in `common-core.result` / `common-sql.core.result`.
- `server/` — the actual service. Top-level packages: `ai/` (graph + nodes + models + RAG), `infrastructure/` (conversation cache/repository, interaction adapter/pipeline, audio ASR/TTS, ai config managers, quota), `controller/` (`user/`, `admin/`, `common/`, `tool/`, plus `temp/` — throwaway test controllers, not production API), `service/` (interfaces) + `service/impl/`, `mapper/`, `socket/constant/` (WebSocket destination constants), `scheduler/`, `task/` (scheduled jobs), `config/`, `login/`.
- `front/src/` — `portal/` (user app: views, composables, stores, api), `admin/` (admin app), `shared/` (axios instance/interceptors, `ws/` STOMP client + endpoint constants, utils incl. `bigint.ts`), `router/{portal,admin,index}.ts`. Path aliases `@`, `@shared`, `@portal`, `@admin`.
- `static-resources/` — `prompts/` (Markdown prompt files keyed by filename; loaded into memory by `common-agent` `PromptUtil`, mostly superseded by DB-driven `ai_node_config` prompts) and `skills/` (Skill definitions per `skills/rule.md`).
- `work/`, `temp/`, `output/`, `test/`, `example/` — scratch/experimental, not part of the build. `.claude/`, `.trae/` contain tooling config; `.trae/rules/java-develop-rule.md` is the coding-convention rulebook.

## Core architecture: AI Graph workflow

The heart of the system is an Alibaba `StateGraph`/`CompiledGraph` pipeline (class `com.alibaba.cloud.ai.graph`). Node classes implement `NodeActionWithConfig`; each graph is built in a `@PostConstruct` and stored on a static field. Custom `StateSerializer` per graph (`DiagnosisStateSerializer`, `InputStateSerializer`, …) and a `SaverConfig` backed by MySQL checkpoint saver; a `CheckpointCleaner` releases checkpoints after each run. Redis distributed locks serialize graph execution per conversation (see `RedisUtils.tryAcquireLock` in `DiagnosisGraph.executeGraph`).

Graphs (all under `ai/node/diagnosis/graph/` unless noted):

- **DiagnosisGraph** (main): `IntentRecognitionNode → InputNode → KnowledgeNode → ProcessNode → DiagnosisPersistNode`. Each `InputNode`/`IntentRecognitionNode` emits a conditional edge that checks `GraphState` interruption flags — a flagged stage short-circuits to `END`.
- **InputGraph**: pre-clean → parallel (message structured processing, emotion statistics, history-diagnosis summary) → symptom normalization.
- **KnowledgeGraph**: query-transform layer → parallel (symptom/diagnosis-standard/intervention-plan repositories) → rerank layer → conditional retry back to query-transform when empty (falls back to default content after max retries).
- **ProcessGraph**: parallel diagnosis stages (comprehensive diagnosis, disease-course attribution, psychological state, social-function impact, protective factor, risk assessment) → intervention suggestion → diagnosis summary.
- **RagGraph** (`ai/rag/graph/`) is deprecated; active RAG nodes remain under `ai/rag/node/` (rewrite/multi-query expand/compress/retrieve/rerank/concatenation).

**Node ↔ Model split:** each AI node pairs with a `Model` class in `ai/model/**` (e.g. `EmotionRecognitionNode` + `EmotionRecognitionModel`). `Model` (interface) is implemented by `BaseModel`, which adds Spring `@Retry` (exponential backoff from `AiNodeConfig`), structured-output handling, and per-vendor reasoning-key handling. `ChatModelFactory` + `ChatModelType` (OLLAMA/DEEP_SEEK/DASH_SCOPE/OPEN_AI) resolve the provider-specific `ChatModel` bean.

**Prompts/model params are data, not code:** every node reads its config from the `ai_node_config` DB table via `AiNodeConfigManager` (startup-warmed in-memory cache, admin-editable — see `AdminAiNodeConfigController`). `AiNodeConfig` holds `systemPrompt`, `modelType`, per-provider model names, temperature/maxToken/topP, retry knobs, enable flag, optimistic-lock `version`. Admin UI under `/admin/ai-node`.

**Streaming output:** `ai/model/processor/` turns raw `NodeOutput`/`StreamingOutput` into domain `AgentStreamEvent`s. `RootAgentStreamProcessor` is the only class that parses SDK raw data; decorators (`LoggingDecorator`, `ThinkAccumulateDecorator`, `MessagePersistDecorator`, `ListenerDispatchDecorator`) wrap it via `AgentStreamProcessorBuilder`.

## Conversation flow (text/voice → AI → push)

The per-round pipeline, in dependency order (read these to understand the whole conversation):

1. `controller/user/conversation/AdapterController` — HTTP `POST /user/conversation/adapter/text/send` for text (interface rate-limited via `@RateLimit`) and STOMP `/app/audio/frame` for audio. Delegates to `AdapterService`/`AdapterServiceImpl`, which validates conversation ownership (`UserInfoThreadLocalUtil`) and pulls the session's registered adapter from `InputAdapterSessionManager`.
2. `TextInputAdapter` (prototype bean, one per session, bound to `userId`/`conversationId`): persists the message to `conversation_memory` (STATE unprocessed), updates conversation liveness, writes the timestamp into a Redis ZSet queue, and triggers `ConversationAggregateScheduler.addTask` (Netty `HashedWheelTimer`, resets a ~3s debounce per conversation, submits to a business thread pool).
3. `ConversationCleanupTask` (scheduled every second) and the scheduler callback both funnel into **`ConversationMessageProcessor`** (the flow orchestrator): acquire processing token (`ConversationProcessTokenManager`, Redis, re-queue on contention) → load cache → query unprocessed messages → assemble `ConversationProcessContextBO` → submit **async** jobs on `diagnosisThreadPoolTaskExecutor` (semantic compression, emotion analysis + diagnosis via `ConversationAiService`, first-round conversation naming) → run **main-thread** `ChatMessageProcessor` (LLM call producing a cold `Flux<NodeOutput>`) → persist state → refresh cache → release token.
4. `ChatMessageProcessor` streams the LLM output; `ConversationWebSocketManager` pushes chunks to `/user/queue/text/reply/{conversationId}` (client uses idle-debounce to detect end). Output is routed through the session's `OutputPipeline` (nodes: `AvatarOutputNode`, `ModelChatOutputNode`, `TtsOutputNode`).
5. `ConversationAiService.analysisAndDiagnosis` runs emotion recognition (per round, persisted + Redis-cached) then executes `DiagnosisGraph`; `DiagnosisPersistNode` writes `EmotionDiagnosis`. New analysis/diagnosis results push `EMOTION_ANALYSIS_UPDATED` / `DIAGNOSIS_CREATED` events over WebSocket.
6. Semantic compression triggers when history > `MAX_CONTEXT_MESSAGES` (50): `HistoryMessageCompressionNode` + `HistoryAnalysisCompressionNode` summarize and write back as `conversation` summary fields.

**Conversation lifecycle:** `POST /user/conversation/lifecycle/init` (with `inputTypes`/`outputTypes`) registers input adapters and assembles the output pipeline, returning `NodeEndpoint`s; `DELETE /user/conversation/lifecycle/{id}` tears them down. The frontend `useConversationLifecycle.ts` serializes init/release calls through a promise chain and treats backend error `2802` (session not found) as the signal to re-init.

## WebSocket protocol (common-websocket)

- STOMP broker (`WebSocketConfig` in `common/common-websocket`): SockJS endpoint `/ws/ai-mental-care`, native endpoint `/ws-native` (native is required for binary TTS frames). Heartbeat 10s/20s.
- Auth: handshake requires a one-time `wsTicket` from `GET /api/get-ws-ticket` (10s expiry, single use); ticket URL query param checked by `AuthHandshakeInterceptor`. Every connect must fetch a fresh ticket — the frontend never caches it.
- Downstream destination convention: `/user/queue/{subpath}/{conversationId}`. Server-side constants in `server/socket/constant/ConversationConstant.java` (`/text/reply`, `/conversation/name`, `/audio/binary`, `/audio/asr/intermediate`, `/pending-action`, `/quota/rejected`, `/emotion/analysis`, `/diagnosis`) are mirrored one-to-one by `front/src/shared/ws/endpoints.ts` — **keep the two in sync when adding channels**. Client errors arrive on `/user/queue/error`.
- `WebSocketUtils.sendToUserBySubDestination` (`common-websocket`) is the push primitive used by `ConversationWebSocketManager`.

## Quota system (recent, important)

`@QuotaLimit(resourceType)` annotation + `QuotaLimitAspect` (`server/infrastructure/ai/`) gate AI resources: 1=TEXT, 2=ASR, 3=TTS, 4=AVATAR_2D (constants on `QuotaConfig`). Semantics:
- **Sync (TEXT)**: user id from ThreadLocal; on over-limit throw `QUOTA_EXCEEDED`/`RESOURCE_DISABLED`; business failure auto-rolls back.
- **Async node (TTS/AVATAR_2D)**: user id from `OutputContext`; on rejection push a `QUOTA_REJECTED` WebSocket event and skip/inject a downgrade flag.
- **Async handler (ASR)**: user id from a method argument index; rejection pushes the event and returns false.

`QuotaService` (server `service/ai`) implements atomic conditional-update consumption (`tryConsume`), `rollbackConsume`, and read-only `hasQuota` (used for early rejection at conversation init). `QuotaConfigManager` warms global config in memory; admin management via `AdminQuotaController` / `SysUserQuotaController` and `front/src/admin/views/quota/`.

## Conventions (from `.trae/rules/java-develop-rule.md` + codebase practice)

- Strict four-layer split: Controller (validation + delegation only) → Service interface → ServiceImpl → Mapper. Entities/DTOs/VOs/BOs are never reused across layers. Controllers are `@RestController` + Knife4j/Swagger `@Tag`/`@Operation` annotated; DTOs get `@Validated` + per-field constraints; use path params for ≤2 args, DTO bodies otherwise.
- DB access via MyBatis-Plus wrappers; batch/multi-table SQL goes in `server/src/main/resources/mapper/*.xml` (never loop-insert). Delete-queries must use native SQL (logical delete `@TableLogic`). `createdBy/updatedBy/createdTime/updatedTime` are filled automatically by the `InjectionMetaObjectHandler` meta-object handler — do not set them manually.
- Exceptions: throw `BusinessException` from a domain `*ExceptionEnum` (e.g. `ConversationExceptionEnum`, `InputAdapterExceptionEnum`), never raw error codes. Global handler in `common-core.error.handler`.
- Javadoc on interfaces, not impls; single-line `//` comments only for important logic, no numbering. Service impls extract entity fields into locals before use (except hutool copy ops). Prefer hutool utils and streams.
- All third-party secrets/URLs go in yaml config, not code. The exception is that `application-dev.yml` (gitignored) carries real dev credentials.
- **Timezone gotcha:** everything is UTC — JVM default (set in `ServerApplication.main`), JDBC `connectionTimeZone=UTC` + session `time_zone='+00:00'`, `web.timeZone: UTC`, and the frontend's `dayjs` time utilities. `LocalDateTime.now()` writes UTC. Don't "fix" a time discrepancy by switching to local time.
- **BigInt gotcha:** backend snowflake IDs exceed JS `Number.MAX_SAFE_INTEGER`. The frontend axios instance keeps raw response text (`transformResponse: [d => d]`) and parses with `parseJSONWithBigInt` (`shared/utils/bigint.ts`) — don't revert to default JSON parsing, or Long IDs get rounded.
- Frontend Vue work: Composition API + `<script setup>` + TS throughout; auto-import of Element Plus + Vue APIs configured in `vite.config.ts`; Pinia stores per app (`front/src/portal/stores`, `front/src/admin/stores`).

## Frequently-referenced entry points

- `server/src/main/java/org/lixiyun/server/ServerApplication.java` — main class; note the UTC default + `@MapperScan("org.lixiyun.server.mapper")`.
- `front/src/shared/api/config.ts` — the single source for page/API prefixes, platform branding, nav menus, colors; derives from `.env` (`VITE_APP_BASE`, `VITE_APP_BASE_API`).
- `front/src/shared/api/instance.ts` + `interceptors.ts` — axios setup, dual-token header attach, business-error `code` extraction (used for conditional retry, e.g. `2802`).
- `server/src/main/java/org/lixiyun/server/service/user/ConversationService.java` (+ impl) and `DialogueService` — conversation CRUD and round-grouped dialogue history.
- Scale subsystem: `server/service/user/Scale*Service` + `server/service/impl/admin/AdminScale*ServiceImpl`, user views `front/src/portal/views/scale/`, admin scale editor `front/src/admin/views/scale/`. AI interpretation of completed scales: `ai/node/tool/ScaleAnalysisNode` + `ScaleConversationTools`.
- Diagnostics persistence/feedback: `AssessmentFeedbackController`/`EmotionDiagnosisController` under `controller/user/conversation/`.
- Admin auth/permissions: `common-authentication` (JWT filter + `UserJwtAuthenticationStrategy`/`AdminJwtAuthenticationStrategy`), RBAC via `@PreAuthorize`-style annotations and `permission` table (see `sql/initialization.sql`).
