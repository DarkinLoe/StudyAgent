# StudyAgent —— 个人学习 Agent（DDD 单体，单 Agent）

基于 **Spring Boot 4.1 / Java 21 / Maven / MySQL / Redis / RabbitMQ(可选) / Spring AI(OpenAI 兼容)** 的
个人学习 Agent 后端。当前为**单 Agent** 实现：一个统一「学习 Agent」问答入口，自动使用个人 RAG 知识库回答，
并可通过**工具调用（function calling）**真正操作学习数据（记错题、查今日计划）；
外部扩展点（planner/teacher/reviewer、MCP、多 Agent）可在此架构上演进。

## 一、功能（对应需求）

| 需求 | 落地 |
| --- | --- |
| 个人 RAG 库：课程表 / 学习安排 / PPT / 题目等，并从中提取学习内容 | `/api/rag/**`：上传文档（PDF/PPT(X)/Word/Excel/TXT/MD）→ Tika 解析 → 分块 → Embedding → 向量检索；**按用户隔离检索**、**启动自动重建索引**、**无 Embedding 能力时关键词降级**；`/api/notes/auto-from-document` 一键学习整理，`/api/questions/from-document` 一键出题 |
| 单 Agent 工具调用（function calling） | `AgentSkill` 契约 + 技能（`register_wrong_question` 记错题、`query_today_plan` 查今日安排）；显式工具循环：≤3 轮上限、重复调用检测、失败文本回填、跨轮 token 累计 |
| 基础问答式教学；创建题库与错题整理 | `/api/agent/chat` 教学问答（RAG + 会话历史）；`/api/banks` 题库、`/api/questions` 题目、`/api/wrongs` 错题本（自动判分沉淀错题） |
| 学习计划；按学习时间与内容自动提示 | `/api/plans/**` 计划与任务；`StudyReminderJob` 每分钟扫描到期任务生成提醒（`/api/reminders`），可选 MQ 推送 |
| 知识管理：学习整理、自动笔记与学习分析 | `/api/notes/**`（手动 + 会话/文档自动整理）；`/api/analytics/learning` 统计与 `/learning/summary` AI 文字分析 |

> 向量检索当前使用进程内 `SimpleVectorStore`（Spring AI）。重启后由摄入流程按文档 `text_content` 重建；
> 数据量大后可换 pgvector/Redis Vector 等（见 [docs/architecture.md](docs/architecture.md)）。

## 二、技术栈与分层

- Spring Boot 4.1.1 · Java 21 · Maven（仓库内 Maven 3.9.16，无需系统安装 Maven）
- Spring Web MVC + Spring Security(全放行脚手架) · Spring Data JPA + MySQL 8
- Spring Data Redis（缓存：今日任务/未读提醒数/待复习错题数等，带 TTL）
- Spring AMQP + RabbitMQ（**默认关闭**；开启后文档摄入与提醒推送走队列，关闭则同步回退）
- Spring AI（OpenAI 兼容端点）＋ Apache Tika（文档解析）
- Docker / docker compose（MySQL+Redis+RabbitMQ，可选一键起应用本体）· GitHub Actions（CI：测试+打包）

目录按 DDD 分层（详见 [docs/architecture.md](docs/architecture.md)）：

```
src/main/java/studio/lingrui/studyagent
├── domain/        领域层：chat / qa / plan / rag / knowledge 聚合 + 仓储接口 + 领域行为
├── application/   应用层：用例编排（服务）、端口(port)接口、AI 提示词、分析统计
├── infrastructure/ 基础设施：持久化(JPA)、AI 适配、向量库适配、Tika、文件存储、Redis 缓存、RabbitMQ、Web 上下文
├── interfaces/    接口层：REST Controller + 请求/响应 DTO
└── shared/        共享：统一响应/分页/错误码/全局异常
```

依赖方向：`interfaces → application → domain`，`infrastructure` 实现 `domain` 仓储接口与 `application` 端口。

### 模块概览（源码包 → 职责）

| 模块 | 职责 | 代表性内容 |
| --- | --- | --- |
| `domain` | 领域模型：聚合/实体/枚举 + 仓储接口 + 领域行为 | `chat`（会话/消息）、`qa`（题库/题目/错题）、`plan`（计划/任务/提醒）、`rag`（知识文档）、`knowledge`（笔记） |
| `application` | 用例编排（应用服务）、端口接口、AI 提示词、学习分析 | `agent`（单 Agent 主循环）、`rag`（摄入/文档）、`qa`（判分/错题）、`plan`（计划/提醒 Job）、`knowledge`（自动笔记）、`analytics`、`port`（AiChatPort/VectorIndexPort/FileStorePort） |
| `infrastructure` | 技术实现：持久化/缓存/消息/外部服务 | `persistence`（JPA 仓储）、`ai`（OpenAI 兼容适配）、`rag`（Tika/向量库）、`cache`（Redis）、`mq`（RabbitMQ）、`file`、`web`（用户上下文） |
| `interfaces` | HTTP 适配层 | REST Controller + 请求/响应 DTO（`/api/agent`、`/api/rag`、`/api/questions`、`/api/plans` 等） |
| `shared` | 跨层共享 | 统一响应 `ApiResponse`、分页、错误码、全局异常处理 |

## 三、快速开始

### 1. 启动基础中间件（MySQL/Redis/RabbitMQ）

先启动 **Docker Desktop**，然后：

```bash
docker compose up -d          # 启动 mysql:8.4 redis:7 rabbitmq:4(15672 管理台)
docker compose ps             # 等待 mysql healthy
```

### 2. 配置 AI（OpenAI 兼容）

编辑环境变量或直接改 `src/main/resources/application.yml` 的 `spring.ai.openai.*`。
常见供应商示例（DeepSeek 无 embedding 时可将 `AI_EMBEDDING_MODEL` 指向其他兼容网关）：

```powershell
$env:AI_BASE_URL='https://api.deepseek.com'
$env:AI_API_KEY='sk-你的key'
$env:AI_CHAT_MODEL='deepseek-chat'
# $env:AI_EMBEDDING_MODEL='text-embedding-3-small'   # 需要 embedding 能力的供应商
```

### 3. 运行

```bash
.\scripts\mvn.ps1 spring-boot:run     # 等价于 mvnw，只是把 Maven 发行版/仓库放到项目内 .m2
```

启动后：`GET http://localhost:8080/actuator/health`

> 说明：首次构建会向中央仓库下载依赖（项目内 `.m2/repository`）。PowerShell 自带 TLS 在部分环境不可用，
> `scripts/mvn.ps1` 已绕过 wrapper，直接调用由 JVM 下载好的本地 Maven。

### 4. 用户身份

登录体系未接入前，通过请求头指定用户（缺省为配置的默认用户 1）：

```
X-User-Id: 1
```

### 5. 可选：开启 RabbitMQ 异步

```powershell
$env:STUDY_AGENT_MQ_ENABLED='true'
```

开启后：上传文档 → 发布摄入任务 → 消费者异步解析索引；学习提醒另发提醒队列。关闭时文档摄入在当前线程同步完成。

### 6. 一键容器化运行（可选）

```bash
docker compose --profile app up -d --build   # 中间件 + 应用本体一起起（应用镜像多阶段构建）
```

### 7. 冒烟验证

应用启动后执行（覆盖 health / RAG 降级检索 / 计划与今日学习 / 题库判分入错题本 / 学习分析 / 未读提醒）：

```powershell
.\scripts\smoke-test.ps1
```

## 四、主要 API

统一响应 `{code,message,data}`，业务错误码见 `shared/exception/ErrorCode.java`。

**Agent / 问答**
- `POST /api/agent/chat` `{sessionId?, content}` → 回答+引用
- `GET/PATCH /api/chat/sessions`、`GET /api/chat/sessions/{id}/messages`、`POST /api/chat/sessions/{id}/archive`

**RAG 知识库**
- `POST /api/rag/documents`（multipart: file, sourceType, tags?）
- `GET/DELETE /api/rag/documents[/{id}]`、`POST /api/rag/documents/{id}/reindex`
- `GET /api/rag/search?q=&topK=`

**题库 / 刷题 / 错题**
- `/api/banks` CRUD
- `/api/questions` CRUD；`POST /api/questions/{id}/practice`；`POST /api/questions/from-document`
- `/api/wrongs`：列表(可按状态)、新增错题、状态流转(PENDING/REVIEWED/MASTERED)、删除、`pending-count`

**学习计划 / 提醒**
- `/api/plans` CRUD；`/api/plans/today`；`/api/plans/{planId}/tasks` 与任务完成/删除
- `/api/reminders`：列表/未读/未读数/标记已读

**知识管理 / 分析**
- `/api/notes` CRUD；`POST /api/notes/auto-from-session|auto-from-document`
- `GET /api/analytics/learning`；`GET /api/analytics/learning/summary`(AI 总结)

## 五、验证

```bash
.\scripts\mvn.ps1 test                  # 单元测试（分块器/判分器/工具参数归一化/关键词片段抽取）
.\scripts\mvn.ps1 -DskipTests package   # 打 jar
java -jar target\StudyAgent-0.0.1-SNAPSHOT.jar
.\scripts\smoke-test.ps1                # 端到端冒烟（需应用已启动）
```

CI：`.github/workflows/ci.yml` 在 push/PR 时执行 `./mvnw test` + `package`。

## 六、真实启动踩坑记录（编译与单测发现不了）

1. **Boot 4 默认 Jackson 3，项目仍用 Jackson 2**：容器里没有 `com.fasterxml.jackson.databind.ObjectMapper` Bean，
   注入失败。当前由 `JacksonConfig` 显式提供过渡 Bean（含 `JavaTimeModule`），后续应统一迁移到 Jackson 3。
2. **领域仓储接口签名与 Spring Data 内建方法冲突**：`void saveAll(List<PlanTask>)` 与 `JpaRepository.saveAll(Iterable)`
   不匹配，被 Spring Data 当作派生查询解析而启动失败——已删除冗余声明。
3. **Spring Data Redis 仓储扫描**：`spring.data.redis.repositories.enabled=false`，避免启动刷
   "Could not safely identify store assignment" 日志。

> 教训：Agent/后端类项目必须**至少真实启动一次**，编译通过与单测全绿都替代不了它。

## 七、说明与取舍

- 表结构由 Hibernate `ddl-auto=update` 自动生成；生产建议切 Flyway + `validate`。
- 安全默认全放行；接入登录后收紧 `/api/**` 并做用户体系（现仅 `X-User-Id` 头）。
- 实体即聚合（JPA 注解落在领域对象上并保留行为方法）；后续如需严格分离持久化模型可加映射层。
- 领域仓储接口部分方法使用 Spring Data 分页类型，属务实取舍。
- RabbitMQ 关闭时无任何连接尝试；开启但 Rabbit 不在线仅记录日志，不影响主流程。
- 向量检索为进程内 `SimpleVectorStore`：启动时按 `text_content` 重建；用户隔离在适配器内按元数据过滤，
  换成 pgvector 等可下推为服务端过滤。

## 八、后续演进（Roadmap）

1. 统一迁移到 Jackson 3，删除过渡配置；
2. 登录体系（JWT/OAuth2）替换 `X-User-Id`，向量库检索按真实租户隔离；
3. 向量库持久化（pgvector/Redis Vector）+ 增量索引；
4. MCP：把学习能力暴露为 MCP Server / 接入外部 MCP 工具；
5. 多 Agent 编排（planner/teacher/reviewer）与流式输出（SSE）；
6. 数据库迁移改 Flyway + 集成测试（Testcontainers）。

更多设计细节见 [docs/architecture.md](docs/architecture.md)。
