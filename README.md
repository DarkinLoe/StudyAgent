# StudyAgent —— 个人学习 Agent（DDD 单体，单 Agent）

基于 **Spring Boot 4.1 / Java 21 / Maven / MySQL / Redis / RabbitMQ(可选) / Spring AI(OpenAI 兼容)** 的
个人学习 Agent 后端。当前为**单 Agent** 实现：一个统一「学习 Agent」问答入口，自动使用个人 RAG 知识库回答，
并可通过**工具调用（function calling）**真正操作学习数据（记错题、查今日计划）；
外部扩展点（planner/teacher/reviewer、MCP、多 Agent）可在此架构上演进。

横切能力：**JWT 无状态鉴权（带 jti 吊销/登出）**、**Redis ZSet + Lua 滑动窗口限流**、
**Flyway 版本化迁移**、**traceId 贯穿的结构化日志**、**乐观锁并发治理**。

## 一、功能（对应需求）

| 需求 | 落地 |
| --- | --- |
| 个人 RAG 库：课程表 / 学习安排 / PPT / 题目等，并从中提取学习内容 | `/api/rag/**`：上传文档（PDF/PPT(X)/Word/Excel/TXT/MD）→ Tika 解析 → 分块 → Embedding → 向量检索；**按用户隔离检索**、**启动自动重建索引**、**无 Embedding 能力时关键词降级**；`/api/notes/auto-from-document` 一键学习整理，`/api/questions/from-document` 一键出题 |
| 单 Agent 工具调用（function calling） | `AgentSkill` 契约 + 技能（`register_wrong_question` 记错题、`query_today_plan` 查今日安排）；显式工具循环：≤3 轮上限、重复调用检测、失败文本回填、跨轮 token 累计 |
| 可视化控制台（Vue 3 + Vite） | 独立前端工程 `frontend/`：对话（引用溯源 + token 用量）、知识库上传/检索/重索引、题库刷题与错题本、计划与提醒、笔记与学习分析；生产由 Nginx 托管并反向代理后端 |
| 基础问答式教学；创建题库与错题整理 | `/api/agent/chat` 教学问答（RAG + 会话历史）；`/api/banks` 题库、`/api/questions` 题目、`/api/wrongs` 错题本（自动判分沉淀错题） |
| 学习计划；按学习时间与内容自动提示 | `/api/plans/**` 计划与任务；`StudyReminderJob` 每分钟扫描到期任务生成提醒（`/api/reminders`），可选 MQ 推送 |
| 知识管理：学习整理、自动笔记与学习分析 | `/api/notes/**`（手动 + 会话/文档自动整理）；`/api/analytics/learning` 统计与 `/learning/summary` AI 文字分析 |

> 向量检索当前使用进程内 `SimpleVectorStore`（Spring AI）。重启后由摄入流程按文档 `text_content` 重建；
> 数据量大后可换 pgvector/Redis Vector 等（见 [docs/architecture.md](docs/architecture.md)）。

## 二、技术栈与分层

- Spring Boot 4.1.1 · Java 21 · Maven（仓库内 Maven 3.9.16，无需系统安装 Maven）
- Spring Web MVC + Spring Security + JJWT（**JWT 无状态认证**：注册/登录/当前用户/登出，登出按 jti 吊销）
- Spring Data JPA + MySQL 8 + **Flyway 版本化迁移**（`ddl-auto=none`，启动时 repair→migrate 自愈）
- Spring Data Redis：缓存（今日任务/未读数/用户快照，带 TTL 与写后失效）+ **ZSet+Lua 滑动窗口限流**
  + **SETNX+Lua 归属校验的分布式锁**（多实例定时任务串行化）
- Spring AMQP + RabbitMQ（**默认关闭**；开启后文档摄入与提醒推送走队列，关闭则同步回退）
  — JSON 消息体、消费重试 3 次、死信队列兜底、publisher confirm/returns
- Spring AI（OpenAI 兼容端点）＋ Apache Tika（文档解析，带字符数上限与 PDF 内联图片关闭）
- Docker / docker compose（MySQL+Redis+RabbitMQ，可选一键起应用本体）· GitHub Actions（CI：测试+打包+前端构建）
- Vue 3 + Vite + vue-router + Nginx（前后端分离前端，见 `frontend/`）

目录按 DDD 分层（详见 [docs/architecture.md](docs/architecture.md)）：

```
src/main/java/studio/lingrui/studyagent
├── domain/        领域层：chat / qa / plan / rag / knowledge 聚合 + 仓储接口 + 领域行为
├── application/   应用层：用例编排（服务）、端口(port)接口、AI 提示词、分析统计
├── infrastructure/ 基础设施：持久化(JPA)、AI 适配、向量库适配、Tika、文件存储、Redis 缓存、RabbitMQ、Web 上下文
├── interfaces/    接口层：REST Controller + 请求/响应 DTO
└── shared/        共享：统一响应/分页/错误码/全局异常
```

依赖方向：`interfaces → application → domain`，`infrastructure` 实现 `domain` 仓储接口与
`application` 定义的**端口**（`AiChatPort` / `VectorIndexPort` / `FileStorePort` / `CachePort` /
`MqPort` / `TextExtractionPort` / `TokenPort`）——**应用层不 import 任何 infrastructure 类型**，
配置对象也下沉在 `shared.config`。

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
控制台：前端是独立工程（Vue 3），开发/构建/部署见「五、前端」章节

> 说明：首次构建会向中央仓库下载依赖（项目内 `.m2/repository`）。PowerShell 自带 TLS 在部分环境不可用，
> `scripts/mvn.ps1` 已绕过 wrapper，直接调用由 JVM 下载好的本地 Maven。

### 4. 用户身份与鉴权

登录后拿到 JWT，后续请求带 `Authorization: Bearer <token>`：

```powershell
# 注册（不存在时）；已注册则直接登录
Invoke-RestMethod -Uri http://localhost:8080/api/auth/register -Method Post `
  -ContentType 'application/json' -Body '{"username":"demo","password":"demo123456","nickname":"Demo"}'

$token = (Invoke-RestMethod -Uri http://localhost:8080/api/auth/login -Method Post `
  -ContentType 'application/json' -Body '{"username":"demo","password":"demo123456"}').data.token

Invoke-RestMethod -Uri http://localhost:8080/api/plans/today -Headers @{ Authorization = "Bearer $token" }
```

- 密码 BCrypt 存储，不落明文；登录失败统一提示"用户名或密码错误"（防用户枚举）；
- 令牌带 `jti`，`POST /api/auth/logout` 会把它加入 Redis 吊销名单（TTL = 令牌剩余有效期），
  登出立即生效；未配置 `JWT_SECRET` 时启动生成临时密钥并告警（重启后旧 token 失效）；
- 接口限流：对话/上传按用户、登录按客户端 IP，均为 Redis ZSet + Lua 原子滑动窗口（1 分钟）。

### 5. 可选：开启 RabbitMQ 异步

```powershell
$env:STUDY_AGENT_MQ_ENABLED='true'
```

开启后：上传文档 → 发布摄入任务 → 消费者异步解析索引；学习提醒另发提醒队列。关闭时文档摄入在当前线程同步完成。

### 6. 一键容器化运行（可选）

```bash
docker compose --profile app up -d --build
# 中间件 + 后端 + 前端(Nginx) 一起起：前端 http://localhost:8081/ ，后端 API :8080
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

**认证**
- `POST /api/auth/register`、`POST /api/auth/login`、`POST /api/auth/logout`、`GET /api/auth/me`

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

## 五、前端（Vue 3 + Vite + Nginx）

前端是独立工程 `frontend/`（前后端分离），后端只提供 REST API。

### 开发模式

```powershell
cd frontend
npm install
npm run dev            # http://localhost:5173
```

Vite 会把 `/api`、`/actuator` 代理到 `http://localhost:8080`（见 `frontend/vite.config.js`），
所以前端代码里只写相对路径，**开发期不需要配置 CORS**。

### 构建

```powershell
cd frontend
npm run build          # 产出 frontend/dist（按路由自动代码分割）
```

### 生产部署：Nginx

方式一（容器，推荐）：

```bash
docker compose --profile app up -d --build
# 访问 http://localhost:8081/ ；Nginx 在容器网络内把 /api、/actuator 反代到 app:8080
```

方式二（已有 Nginx）：将 `frontend/dist` 拷到站点目录，套用 `frontend/nginx.conf` 的 location 规则。

Nginx 配置要点：

| 配置 | 作用 |
| --- | --- |
| `try_files $uri $uri/ /index.html` | SPA history 路由回退（直接访问 `/qa`、`/plan` 不会 404） |
| `location /assets/` + `expires 30d` | Vite 产物文件名带 hash，可长缓存；`index.html` 明确不缓存 |
| `location /api/` → `proxy_pass http://app:8080` | 反向代理后端；`Authorization` 默认透传、`client_max_body_size 60m`、`proxy_read_timeout 120s` |
| `gzip on` | 文本资源压缩 |

### 为什么这样分层（面试常问）

- **开发与生产用同一套相对路径**：开发由 Vite dev server 代理、生产由 Nginx 反代，
  前端无需硬编码后端地址，也不依赖 CORS；
- **前端只认 HTTP 契约**（统一响应 `{code,message,data}` + `Authorization: Bearer`），后端实现可独立演进；
- **Nginx 负责静态托管/压缩/缓存/超时**，Spring Boot 专注 API 与 Agent 逻辑，各司其职。

拓扑：

```
浏览器 ──> Nginx(容器 80 → 宿主机 8081)
              ├── /               → 静态资源（Vue 构建产物，SPA 回退）
              └── /api、/actuator → Spring Boot(8080) → MySQL / Redis / RabbitMQ
```

## 六、验证

```bash
.\scripts\mvn.ps1 test                  # 单元测试（分块器/判分器/工具参数归一化/关键词片段抽取/
                                        #   限流判定与客户端 IP/JWT 签发吊销/异常映射/Agent 编排与检索降级）
.\scripts\mvn.ps1 -DskipTests package   # 打 jar
java -jar target\StudyAgent-0.0.1-SNAPSHOT.jar
.\scripts\smoke-test.ps1                # 端到端冒烟（需应用已启动，含登录与限流入口）
```

CI：`.github/workflows/ci.yml` 在 push/PR 时执行 `./mvnw test` + `package`。

## 七、真实启动踩坑记录（编译与单测发现不了）

1. **Boot 4 默认 Jackson 3，项目仍用 Jackson 2**：容器里没有 `com.fasterxml.jackson.databind.ObjectMapper` Bean，
   注入失败。当前由 `JacksonConfig` 显式提供过渡 Bean（含 `JavaTimeModule`），后续应统一迁移到 Jackson 3。
2. **领域仓储接口签名与 Spring Data 内建方法冲突**：`void saveAll(List<PlanTask>)` 与 `JpaRepository.saveAll(Iterable)`
   不匹配，被 Spring Data 当作派生查询解析而启动失败——已删除冗余声明。
3. **Spring Data Redis 仓储扫描**：`spring.data.redis.repositories.enabled=false`，避免启动刷
   "Could not safely identify store assignment" 日志。
4. **Hibernate 7 + MySQL：`@Lob` 的 `String` 会建表成 `tinytext`（仅 255 字节）**：会话消息、题干、
   笔记、文档正文一旦超过 255 字节即报
   `Data truncation: Data too long for column 'content'`（中文约 85 字就触发）。
   修复：实体显式声明 `columnDefinition = "LONGTEXT"`；**已有库**还需把已有列类型改过来——
   `ddl-auto=update` 只加表/列，**不会修改已存在列的类型**。V2 迁移里已包含这批 `MODIFY`，
   老库也可执行 `docker/mysql/fix-text-columns.sql`（与 V2 等价的历史补丁）。
5. **Spring AI 2.0 的模型解析**：聊天模型通过环境变量/外部配置仍可能不生效（回落到库内置默认模型，
   报 `404: The model \`gpt-5-mini\` does not exist`）。修复：`OpenAiChatAdapter` 构建请求时
   **显式写入模型名**（依次解析 `chat.model → chat.options.model → openai.model → AI_CHAT_MODEL`）。
6. **迁移脚本被转义符破坏**：`V2__auth_and_optimistic_lock.sql` 曾在生成环节被当作"含转义符的字符串"，
   源码里混进真实控制字符（`\n`→LF、`\a`→BEL、`\v`→VT、`\t`→TAB），把 `nickname`/`answer`/`version`/
   `text_content` 分别破坏成 `ickname`/`nswer`/`ersion`/`ext_content`。MySQL 的 DDL 不走事务，
   失败后 `flyway_schema_history` 留下 failed 记录，此后每次启动 Flyway 都拒绝启动。
   修复：脚本重写为纯文本 + 幂等写法（逐列查 `information_schema` 再 ALTER），
   并新增 `FlywayConfig` 让启动流程 **repair() → migrate()** 自动收敛。
7. **大模型调用不能套事务**：整轮问答若包在 `@Transactional` 里，一条数据库连接会被独占整个
   模型响应时间（10~60s），`maximum-pool-size=20` 意味着 20 个并发对话就打满连接池、拖垮所有接口；
   且上游超时会让用户消息随事务回滚。修复：`TransactionTemplate` 显式切成
   「短事务 → 事务外调模型 → 短事务」。
8. **MQ 消息契约与 Spring AMQP 默认行为**：生产者发 `Map.of(...)`、消费者却声明 `Long` 入参，
   类型不匹配；默认 JDK 序列化不可读；消费者 `catch` 掉异常后正常返回 = 任务被静默 ACK 丢弃。
   修复：统一 JSON 转换器 + 消息 DTO、开启重试与死信队列、消费者不再吞异常。
9. **会话历史取成"最早 10 条"**：`OrderByIdAsc + PageRequest.of(0, 10)` 拿到的是会话开头 10 条，
   会话一长模型上下文就永久冻结在最前面。修复：按 id 倒序取最近 N 条再反转（已加回归单测）。
10. **Redis 的两个生产级反例**：`KEYS prefix*` 会阻塞整个 Redis（改 SCAN 分批删除）；
    「SETNX 加锁 + 直接 DEL 解锁」会误删他人重新获取的锁（改 Lua 比对持有者 token）。
11. **固定窗口限流的永久锁死**：先 INCR 再 EXPIRE 是两条命令，EXPIRE 失败时 key 没有 TTL，
   该用户被永久限流。修复：清理/计数/写入收敛进一段 Lua 原子执行（滑动窗口）。
12. **MQ 关闭时健康检查仍然探测 RabbitMQ**：`study-agent.mq.enabled=false` 只是不启消费者，
   `RabbitHealthIndicator` 照样去连 5672，连不上就把整体状态判成 DOWN(503)——
   一个功能完好的实例会被编排探针判死。修复：`management.health.rabbit.enabled` 跟随 MQ 开关。
13. **PowerShell 5.1 按本地代码页读取无 BOM 的 `.ps1`**：脚本里的中文字符串被解成乱码后
   直接语法报错（`smoke-test.ps1` 报 `Missing ']' after array index expression`）。
   修复：`scripts/*.ps1` 写入 UTF-8 BOM，PS 5.1 与 PowerShell 7 均可正常解析。
14. **环境里 `java`/`javac` 指向 `javapath` 垫片会直接崩溃**（`0xC0000409`），
   而 Maven 内部的 JVM 正常——直接用 JDK 全路径（如
   `C:\Program Files\Microsoft\jdk-21.0.7.6-hotspot\bin`）即可绕过。
15. **`mvn.ps1` 里 `$ErrorActionPreference='Stop'` + 原生命令 stderr**：Windows PowerShell 5.1
   会把 `mvn.cmd` 写到 stderr 的**警告**（如 "Mockito is currently self-attaching..."）当成
   终止性错误，导致**构建明明成功、脚本却中断退出**。修复：调用外部进程前把 prefer 设为
   `Continue`，成败只依据 `$LASTEXITCODE`，并把退出码打印出来。

> 教训：Agent/后端类项目必须**至少真实启动一次**，编译通过与单测全绿都替代不了它；
> 而且"配置不生效"这类问题，往往要靠**把真实生效值打出来**（列类型、容器环境变量、实际请求参数）才能定位。

## 八、说明与取舍

- 表结构由 **Flyway** 版本化迁移管理（`ddl-auto=none`）。历史库（无 `flyway_schema_history`）
  自动基线到 V1；启动时先 `repair()`（清理失败记录、对齐校验和）再 `migrate()`，
  可用 `study-agent.flyway.repair-on-startup=false` 关闭。
- 安全：`/api/auth/register|login`、`/actuator/health|info` 公开，其余 `/api/**` 需认证；
  令牌放前端 `localStorage`（见「已知边界」中关于 HttpOnly Cookie 的说明）。
- 实体即聚合（JPA 注解落在领域对象上并保留行为方法）；后续如需严格分离持久化模型可加映射层。
- 领域仓储接口部分方法使用 Spring Data 分页类型，属务实取舍（换 ORM 时需改写）。
- RabbitMQ 关闭时无任何连接尝试；开启但 Rabbit 不在线仅记录日志，不影响主流程。
- 向量检索为进程内 `SimpleVectorStore`：用户隔离在适配器内按元数据过滤（候选放大 20×，上限 500），
  换成 pgvector 等可下推为服务端过滤；已索引分块 id 在适配器内有登记表，删除时精确清理。

## 九、已知边界（诚实清单）

这些是当前**没有**解决、或只做了有界降级的问题，面试/评审时应主动说明：

1. **令牌存 localStorage**：XSS 即可窃取。彻底方案是 HttpOnly + Secure Cookie 并配套 CSRF 防护
   （SameSite + 自定义头校验），但会同时改动前后端认证通道，本项目暂未做；已补 jti 吊销与登出闭环。
2. **关键词降级检索是有界扫描而非精确检索**：按上传时间取最近 200 篇、单篇正文截断 10 万字符，
   只看"命中词占比"。根治要换成倒排索引（MySQL FULLTEXT / ES），或直接依赖向量库的服务端过滤。
3. **向量库不持久化**：进程重启后由后台线程按已存正文重建（重建期间检索结果不完整）。
   文档量大时应换 pgvector/Redis Vector 并做增量索引。
4. **上传解析没有做内容嗅探、病毒扫描与解析超时**：只按扩展名白名单 + 体积上限 + 字符数上限防护，
   解析仍在应用进程内进行。生产建议放入受限子进程/独立服务并接入杀毒扫描。
5. **多实例下的提醒任务**：Redis 锁 + `@Version` 乐观锁共同防重复推送。若进程在"标记已提醒"成功后、
   推送前崩溃，该条提醒会漏发（不回滚标记）。要严格一次不多不少，需引入 outbox 表 + 定时补偿。
6. **没有集成测试与 Testcontainers**：单测覆盖纯逻辑与编排（36 项），数据库/事务/迁移行为
   仍依赖真实启动 + `scripts/smoke-test.ps1`。

## 十、后续演进（Roadmap）

1. 登录体系加固：HttpOnly Cookie + CSRF、刷新令牌、密码重置与账号封禁的缓存失效联动；
2. 向量库持久化（pgvector/Redis Vector）+ 增量索引 + 服务端租户过滤；
3. 关键词检索换成倒排索引，或引入 rerank；
4. MCP：把学习能力暴露为 MCP Server / 接入外部 MCP 工具；
5. 多 Agent 编排（planner/teacher/reviewer）与流式输出（SSE）；
6. 集成测试（Testcontainers）+ 迁移脚本的回归验证；
7. 统一迁移到 Jackson 3，删除过渡配置；
8. 前端：Pinia 状态管理、组件库、对话流式输出、打包体积与首屏优化。

更多设计细节见 [docs/architecture.md](docs/architecture.md)。
