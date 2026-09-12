# 架构说明

## 1. DDD 分层与模块职责

```
┌────────────────────────────────────────────────────────────┐
│ interfaces.rest   REST Controller · 请求/响应校验(DTO)       │
├────────────────────────────────────────────────────────────┤
│ application       用例编排(应用服务) · 端口(port) · AI 提示词 │
│                   学习分析统计 · 提醒调度(Job)                │
├────────────────────────────────────────────────────────────┤
│ domain            聚合/实体/枚举 + 仓储接口 + 领域行为方法     │
│                   （chat qa plan rag knowledge）             │
├────────────────────────────────────────────────────────────┤
│ infrastructure    持久化(JPA 实现 domain 仓储) · AI/Chat 适配 │
│                   · 向量库适配 · Tika 解析 · 文件存储        │
│                   · Redis 缓存 · RabbitMQ · Web 用户上下文    │
├────────────────────────────────────────────────────────────┤
│ shared            ApiResponse/PageResult/异常体系            │
└────────────────────────────────────────────────────────────┘
```

依赖约束：上层依赖下层接口；`infrastructure` 反向实现 `domain` 与 `application` 定义的接口（依赖倒置）。
当前按“单 Agent 单体”组织为**一个 Maven 模块 + 包级分层**，后续拆多模块/多 Agent 时可将各 bounded context 独立。

## 2. 领域模型（聚合根）

| 领域 | 聚合 | 说明 |
| --- | --- | --- |
| chat | ChatSession + ChatMessage | 会话/消息历史，供 Agent 上下文与笔记整理 |
| qa | QuestionBank / Question / WrongQuestion | 题库、题目（题型+判分）、错题本（用户-题去重，状态机 PENDING→REVIEWED→MASTERED） |
| plan | StudyPlan + PlanTask / StudyReminder | 计划、任务（日期+时段+时长）、提醒记录（调度生成） |
| rag | KnowledgeDocument | 文档元数据+解析纯文本+索引状态机 PENDING→PROCESSING→INDEXED/FAILED |
| knowledge | StudyNote | 手动 / AUTO（来源 session:或 doc:） |
| auth | User | 账号（BCrypt 口令哈希、启用开关）；令牌与吊销在基础设施层（JwtService/TokenBlacklist） |

典型状态机：文档 `PENDING → PROCESSING → INDEXED | FAILED`（摄入失败可 `/reindex` 重试）。

## 3. 单 Agent 问答流水线

```
POST /api/agent/chat {content, sessionId?}
   │
   ├─ 1. 短事务：会话（无 sessionId 自动建）；标题取首条消息
   ├─ 2. 短事务：历史取该会话「最近 10 条」（按 id 倒序取再反转），并落 USER 消息 → 提交
   ├─ 3. 事务外：query → EmbeddingModel → SimpleVectorStore topK（个人知识库，按 userId 过滤）
   ├─ 4. 组装：System(人设+检索资料+工具规则) + 历史 + 当前问题
   ├─ 5. 事务外：调用 ChatModel（OpenAI 兼容）—— 慢操作绝不占用数据库连接
   └─ 6. 短事务：ASSISTANT 消息落库（引用 json + token 用量）；更新会话时间
```

设计要点：会话状态在 MySQL（不依赖模型 provider 的会话内存），利于多端同步与笔记/分析复用；
**事务边界显式切成三段**（`TransactionTemplate`），否则一次对话会独占一条数据库连接直到模型返回，
连接池很快被耗尽管并拖垮全站；向量检索失败自动降级为关键词检索，
模型调用失败返回 `AI_UNAVAILABLE(2202)`（HTTP 503）。

## 4. 缓存、限流与消息

- **Redis**（应用层定义 `CachePort`，基础设施 `RedisCacheAdapter` 实现：StringRedisTemplate + JSON 值）：
  - `cache:todayTasks:{userId}`：今日/逾期学习清单，TTL 30s，计划任务写操作即失效
  - `cache:reminderUnread:{userId}`、`cache:wrongPending:{userId}`：常用计数，TTL 10m，写后按用户失效
  - `cache:user:id:{userId}`：**用户资料快照**（`UserSnapshot`，不含 passwordHash），TTL 5m；
    登录凭证刻意不进缓存，避免"改密/封禁在 TTL 内不生效"
  - 前缀失效用 **SCAN 游标分批删除**（不用会阻塞 Redis 的 `KEYS`）；
  - Redis 不可用时全部降级（读空/写丢/锁放行），不阻塞主流程
- **分布式锁**（`CachePort.tryLock`）：SETNX + 持有者 token，释放时用 Lua 比对 token 再 DEL，
  避免"锁过期被他人重新获取后又被前一个实例删掉"；用于多实例下的定时提醒任务串行化。
- **限流**（`RateLimitFilter`）：Redis ZSet + Lua 原子滑动窗口（1 分钟），
  对话/上传按用户、登录按**真实客户端 IP**（取 `X-Forwarded-For` 最右段，防伪造绕过）。
- **RabbitMQ**（`study-agent.mq.enabled=false` 默认关闭，应用层端口 `MqPort`）：
  - 关闭 → 文档摄入同步执行、提醒仅落库（无 MQ 也可完整运行）
  - 开启 → `study-agent.document.ingest` 队列异步摄入；`study-agent.reminder.push` 提醒事件
    （消费端当前占位日志，可扩展 WebSocket/邮件等通道）
  - 消息体为 JSON（`DocumentIngestMessage` / `ReminderPushMessage` DTO，非 `Map`），
    消费重试 3 次（指数退避）后经死信交换机进入 `study-agent.dead-letter`，
    开启 publisher confirm/returns 便于发现"没发出去"；
  - `listener.simple.auto-startup` 跟随开关，Rabbit 不在线不影响启动；
  - 派发点在数据库写事务之外（上传先落库提交再发消息），避免消费者读不到文档。

## 5. 学习提示（自动提醒）机制

`StudyReminderJob` 按 `study-agent.reminder.poll-cron`（默认每分钟）执行：
未完成 & 未提醒 & `planned_date <= today` 的任务 → 当前时刻已到 `plannedDate+plannedStart` 即生成
`StudyReminder`（逾期文案提示补学），并标 `reminder_sent` 防重复；随后可选 MQ 推送。
前端展示：`GET /api/reminders/unread`（下拉角标）＋ `GET /api/plans/today`（今日/逾期清单）。

## 6. 学习分析口径

`LearningAnalyticsService.compute(userId)`：
计划数/进行中数、任务完成率、计划 vs 已投入分钟（近 7 天）、错题三态计数、笔记数、
最近 100 条错题聚合出 Top5 高频知识点标签；`summarize()` 由 LLM 生成 200 字内改进建议。

## 7. Agent 工具循环（function calling）

应用层契约 `AgentSkill`（name / description / parametersJsonSchema / execute），
基础设施层 `SkillToolCallback` 适配为 Spring AI 的 `ToolCallback`——**应用层不依赖任何 LLM 框架类型**。

采用**显式工具循环**（不依赖框架自动执行，便于观测与控制），位于 `OpenAiChatAdapter`：
模型返回 tool_call → 记录日志 → 执行技能 → 以 `ToolResponseMessage` 回填 → 再次调用，
直到模型不再返回 tool_call 或达到轮数上限。

四层防御：

1. **提示词规则**（`AIPrompts.toolGuide`）：何时该用/不该用工具、不要重复调用、失败按建议处理；
2. **轮数上限** `MAX_TOOL_ROUNDS=3`：用尽则取最后一次文本，否则返回兜底话术；
3. **重复调用检测**：`ToolArgsNormalizer` 对参数 JSON 递归键排序生成指纹，命中则不重复执行并提示模型收尾；
4. **失败文本化**：技能异常被转换为失败文本回填（可恢复给修正建议；不可恢复明确禁止重试），循环不中断。

## 8. RAG 加固

- **检索隔离**：向量元数据写入 `userId`；`SpringAiVectorIndexAdapter.search` **候选放大 20×（下限 100、上限 500）**
  后再按 userId 过滤——候选太少时"前若干条恰好都是别人的文档"会被误判成无命中而错误降级（漏召）。
  换 pgvector 可下推为服务端 filter，届时放大倍数即可取消。
- **分块登记表**：适配器内维护 docId → chunkId 集合，删除文档时与 `chunkCount` 枚举取并集精确清理；
  否则"上次索引中途失败"残留的分块永远删不掉，还会被检索命中。
- **启动重建**：进程内 `SimpleVectorStore` 重启即失效 → `RagIndexBootstrapRunner` 用文档已保存的
  `text_content` 重新向量化（不重新解析文件）。重建在**后台单线程**执行（不阻塞启动与健康检查），
  期间检索结果可能不完整；可用 `study-agent.rag.reindex-on-startup=false` 关闭；单篇失败只告警。
- **关键词降级**：`KnowledgeKeywordSearchService` + `KeywordSnippetExtractor` 在向量检索异常或无命中时
  按关键词匹配解析文本（score = 命中词占比，仅用于排序）。该路径**有界执行**：
  SQL 侧按 id 倒序取最近 200 篇、单篇正文截断 10 万字符，避免"全量 longtext 进内存"的性能悬崖。
- **解析加固**：抽取结果上限 2,000,000 字符（超出截断）、关闭 PDF 内联图片、上传体积上限可配
  （`study-agent.file.max-file-size-mb`）；用 `WriteLimitReachedException.isWriteLimitReached` 区分截断与真失败。
- 文档状态机仍为 PENDING → PROCESSING → INDEXED/FAILED，失败可 `/reindex` 重试；
  摄入失败时 FAILED 状态与原因一定落库（派发与摄入都在事务之外执行）。

## 9. 部署与验证

- **镜像**：多阶段 `Dockerfile`（maven 构建层 + temurin-21 JRE 运行层）；
  `docker compose --profile app up -d --build` 一键起中间件 + 应用本体。
- **单测**：`./mvnw test` 共 36 项，覆盖分块器/判分器/工具参数指纹/关键词片段、限流判定与客户端 IP 解析、
  JWT 签发与吊销、异常到 HTTP 状态映射、Agent 编排（会话历史顺序 + 检索降级链，含回归守卫）。
- **冒烟**：`scripts/smoke-test.ps1` 覆盖 health、登录/注册、RAG 降级检索、计划/今日学习(Redis)、
  题库判分→错题本、学习分析、未读提醒；有失败项则以非 0 退出。
- **CI**：`.github/workflows/ci.yml`（JDK 21 + `./mvnw test` + package；前端 `npm ci` + build）。
- **真实启动踩坑记录**（详见 README 第七节）：
  ① Boot 4 默认 Jackson 3，项目仍用 Jackson 2，需显式 `ObjectMapper` Bean 过渡；
  ② 领域仓储接口签名必须兼容 Spring Data 内建方法（`saveAll` 曾因签名不匹配被当作派生查询解析而启动失败）；
  ③ Spring Data Redis 仓储扫描需关闭（`spring.data.redis.repositories.enabled=false`）；
  ④ 迁移脚本里的控制字符会让 Flyway 永久拒绝启动，需 repair→migrate 自愈；
  ⑤ 大模型调用必须移出数据库事务，否则连接池会被慢请求吃干。
  **结论：编译通过与单测全绿都不能替代"至少真实启动一次"。**

## 10. 已知边界与后续演进

**已知边界（当前未解决或有界降级）**

1. 令牌存前端 `localStorage`（XSS 可窃取）；已补 jti 吊销与登出，但 HttpOnly Cookie + CSRF 未做；
2. 关键词降级检索是有界扫描而非精确检索，根治要换倒排索引（MySQL FULLTEXT / ES）；
3. 向量库不持久化，重启后后台重建，期间检索不完整；
4. 上传解析无内容嗅探、无病毒扫描、无解析超时，解析在应用进程内进行；
5. 多实例提醒任务用"Redis 锁 + @Version"防重复；进程在标记成功后、推送前崩溃会漏一条（需 outbox 补偿）；
6. 没有集成测试 / Testcontainers，数据库与迁移行为依赖真实启动 + 冒烟脚本。

**后续演进**

1. 认证加固：HttpOnly Cookie + CSRF、刷新令牌、密码重置/封禁与缓存失效联动；
2. 向量库持久化与增量索引（pgvector/Redis Vector），租户过滤下推到服务端；
3. 多 Agent 编排：把当前单 Agent 拆为 planner/teacher/reviewer（技能与端口已就绪）；
4. 课程表 → 学习计划：解析后按周模板批量生成 PlanTask；
5. MCP：将学习能力暴露为 MCP Server，或接入外部 MCP 工具；
6. 流式输出（SSE）与通知渠道（站内信/邮件）；
7. 集成测试（Testcontainers）与迁移脚本回归；统一迁移到 Jackson 3。
