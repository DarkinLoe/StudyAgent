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

典型状态机：文档 `PENDING → PROCESSING → INDEXED | FAILED`（摄入失败可 `/reindex` 重试）。

## 3. 单 Agent 问答流水线

```
POST /api/agent/chat {content, sessionId?}
   │
   ├─ 1. 会话：无 sessionId 自动建会话；标题取首条消息
   ├─ 2. 历史：取该会话最近 10 条（MySQL）
   ├─ 3. 检索：query → EmbeddingModel → SimpleVectorStore topK（个人知识库）
   ├─ 4. 组装：System(人设+检索资料) + 历史 + 当前问题
   ├─ 5. 调用：ChatModel（OpenAI 兼容，ChatClient/ChatModel）
   └─ 6. 落库：USER/ASSISTANT 两条消息；引用(json)写入 ASSISTANT 消息；更新会话时间
```

设计要点：会话状态在 MySQL（不依赖模型 provider 的会话内存），利于多端同步与笔记/分析复用；
向量检索失败自动降级为“无资料回答”并提示上传资料，模型调用失败返回 `AI_UNAVAILABLE(2202)`。

## 4. 缓存与消息

- **Redis**（`RedisCacheHelper`：StringRedisTemplate + JSON 值，避免泛型序列化类型坑）：
  - `cache:todayTasks:{userId}`：今日/逾期学习清单，TTL 30s，计划任务写操作即失效
  - `cache:reminderUnread:{userId}`、`cache:wrongPending:{userId}`：常用计数，TTL 10m，写后按用户失效
  - Redis 不可用时降级直查数据库，不阻塞主流程
- **RabbitMQ**（`study-agent.mq.enabled=false` 默认关闭）：
  - 关闭 → 文档摄入同步执行、提醒仅落库（无 MQ 也可完整运行）
  - 开启 → `study-agent.document.ingest` 队列异步摄入；`study-agent.reminder.push` 提醒事件
    （消费端当前占位日志，可扩展 WebSocket/邮件等通道）
  - `listener.simple.auto-startup` 跟随开关，Rabbit 不在线不影响启动

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

- **检索隔离**：向量元数据写入 `userId`；`SpringAiVectorIndexAdapter.search` 多取候选后按 userId 过滤，
  保证只能命中本人知识库（换 pgvector 可下推为服务端 filter）。
- **启动重建**：进程内 `SimpleVectorStore` 重启即失效 → `RagIndexBootstrapRunner` 用文档已保存的
  `text_content` 重新向量化（不重新解析文件）；可用 `study-agent.rag.reindex-on-startup=false` 关闭；
  单篇失败只告警，不影响启动。
- **关键词降级**：`KnowledgeKeywordSearchService` + `KeywordSnippetExtractor` 在向量检索异常或无命中时
  按关键词匹配解析文本（score = 命中词占比，仅用于排序），避免"供应商无 embedding"导致整链路失败。
- 文档状态机仍为 PENDING → PROCESSING → INDEXED/FAILED，失败可 `/reindex` 重试。

## 9. 部署与验证

- **镜像**：多阶段 `Dockerfile`（maven 构建层 + temurin-21 JRE 运行层）；
  `docker compose --profile app up -d --build` 一键起中间件 + 应用本体。
- **冒烟**：`scripts/smoke-test.ps1` 覆盖 health、RAG 降级检索、计划/今日学习(Redis)、
  题库判分→错题本、学习分析、未读提醒；有失败项则以非 0 退出。
- **CI**：`.github/workflows/ci.yml`（JDK 21 + `./mvnw test` + package）。
- **真实启动踩坑记录**：① Boot 4 默认 Jackson 3，项目仍用 Jackson 2，需显式 `ObjectMapper` Bean 过渡；
  ② 领域仓储接口签名必须兼容 Spring Data 内建方法（`saveAll` 曾因签名不匹配被当作派生查询解析而启动失败）；
  ③ Spring Data Redis 仓储扫描需关闭（`spring.data.redis.repositories.enabled=false`）。
  **结论：编译通过与单测全绿都不能替代"至少真实启动一次"。**

## 10. 已知边界与后续演进

1. 多 Agent 编排：把当前单 Agent 拆为 planner/teacher/reviewer（技能与端口已就绪）；
2. 统一迁移到 Jackson 3，删除过渡配置；
3. 向量库持久化与增量索引（pgvector/Redis Vector），租户过滤下推到服务端；
4. 用户体系：JWT/OAuth2 替换 `X-User-Id`，错题/计划/文档按真实用户分片；
5. 数据库迁移：Flyway + `ddl-auto=validate`，集成测试引入 Testcontainers；
6. 课程表 → 学习计划：解析后按周模板批量生成 PlanTask；
7. MCP：将学习能力暴露为 MCP Server，或接入外部 MCP 工具；
8. 流式输出（SSE）与通知渠道（站内信/邮件）。
