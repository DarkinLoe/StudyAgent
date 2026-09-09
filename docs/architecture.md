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

## 7. 已知边界与后续演进

1. 多 Agent：AgentChat 抽象为 persona 工厂 + 工具调用（function calling），拆分 planner/teacher/reviewer；
2. 向量库持久化：pgvector/Redis 等替换进程内 SimpleVectorStore，并按用户隔离检索；
3. 用户体系：JWT/OAuth2 替换 `X-User-Id`，错题/计划/文档全部按真实用户分片；
4. 数据库迁移：引入 Flyway + `ddl-auto=validate`；
5. 课程表→学习计划：解析课程表后按周模板批量生成 PlanTask（目前提供上传+检索+人工建计划）；
6. 通知渠道：提醒 MQ 事件接入站内信/邮件。
