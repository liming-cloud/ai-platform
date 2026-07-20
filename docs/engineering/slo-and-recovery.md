# SLO、容量与恢复设计

状态：目标与验收合同；尚未完成负载和恢复演练前不得声称达标

## 服务等级目标

| 能力 | SLI | 初始 SLO | 统计窗口 |
|---|---|---|---|
| 已认证控制台 API | 非预期 5xx 之外的成功率 | 99.9% | 30 天 |
| 登录 | 正确凭据且依赖正常时成功率 | 99.95% | 30 天 |
| 交互查询 API | server p95 / p99 | ≤500ms / ≤1500ms | 7 天 |
| 模型调用编排 | 平台开销 p95（不含 provider） | ≤250ms | 7 天 |
| 知识检索 | 授权检索 p95 | ≤1200ms | 7 天 |
| 异步入库 | 10MB 标准文档到 AVAILABLE p95 | ≤5min | 7 天 |
| outbox | 已提交事件发送 lag p95 / max | ≤10s / ≤2min | 24 小时 |
| 审计 | 高风险写操作审计完整率 | 100% | 每次发布 |
| 租户隔离 | 越权返回数据/引用数量 | 0 | 持续 |

外部 provider 自身耗时与可用性单独计量；平台 SLO 不通过剔除慢请求伪造，但要区分平台、provider、租户配置和限流原因。

## 错误预算与发布

- 30 天 99.9% 可用性对应约 43 分钟错误预算。
- 单周消耗超过月预算 25% 时暂停非可靠性发布；超过 50% 时必须有 owner 和恢复计划。
- 租户泄漏、秘密泄漏、审计缺失、不可恢复数据损坏不使用错误预算，是立即 blocker。
- 固定 RAG/应用运行语料的质量、成本或延迟基线回归超过批准阈值时阻止对应版本发布；该门禁属于现有 `knowledge-base`/`app-studio`，不是独立 Evaluation 产品。

## 健康语义

- `/api/v1/health` 是 liveness，只表示进程可响应，不检查外部依赖。
- `/actuator/health/readiness` 是 readiness，聚合 PostgreSQL、Redis、MinIO、应用 readiness state、Spring Cloud Stream `binders` 启动健康，以及 RocketMQ 两组只读管理协议探针：至少一个 name-server 对 `GET_NAMESRV_CONFIG`、至少一个 broker 对 `GET_BROKER_RUNTIME_INFO` 返回成功且与请求相关联。binder 不健康、任一协议组无成功响应或只有 TCP 假服务时返回 503。该探针不发送/读取业务消息，也不证明 consumer 已连接、lag 已收敛或业务消费成功。
- health 输出只暴露 UP/DOWN，不暴露 endpoint、凭据、异常或组件细节。
- Compose frontend 只在 backend ready 后启动；恢复时 readiness 必须自动回到 UP。

## 容量与背压

必须按租户和全局同时设置：

- API 并发、请求体和上传大小；
- DB pool、statement/transaction timeout；
- Redis 操作 timeout 和 key TTL；
- outbox batch/lease、consumer concurrency、broker 原生重试预算和按 consumer group 隔离的 DLQ；当前五个 binding 的应用内 `max-attempts=1`，`deploy/` 默认原生 `max-reconsume-times=5`、延迟级别 `3`；
- 文档解析/embedding 队列深度；
- provider 并发、token、调用和成本预算；
- app-studio 对话运行时长、并发、token、结果收口重试和外部调用计费预算。

达到限制时返回稳定、可重试语义或排队状态，不能无界排队、无限重试或静默丢弃。负载验收记录 CPU、内存、连接池、GC、队列 lag、p95/p99 和错误分类。

## 数据保护目标

| 数据 | RPO 目标 | RTO 目标 | 恢复方式 |
|---|---:|---:|---|
| PostgreSQL 业务/审计/outbox | ≤15min | ≤60min | base backup + WAL/PITR，恢复后 schema verify |
| MinIO 知识原文 | ≤15min | ≤2h | versioning/replication 或备份，按 object hash 校验 |
| pgvector 索引 | 业务事实 0；索引可重建 | ≤4h | 由版本化 chunk/embedding 任务重建 |
| Redis session/限流/cache | 不作为长期业务事实 | ≤15min | session 重新登录；cache 重建；限流 fail-closed |
| RocketMQ 传输 | 已提交 outbox 不丢 | ≤60min | outbox 重发、consumer inbox/receipt、broker DLQ 隔离后从 source outbox 受控重新派发；禁止复制 DLQ 消息体 |
| 设计/配置版本 | PostgreSQL RPO | ≤60min | 不可变版本与引用图恢复 |

以上是初始目标，必须由业务 owner 批准并用 deploy/生产等价演练校准。

## 必做恢复演练

1. **PostgreSQL**：备份 → 新实例恢复 → verify → 核对租户/审计/outbox/版本引用；随后实际分配一个新的模型策略 revision，证明它严格大于 App/Knowledge 投影及 outbox 中的全部已持久化 revision，且 sequence 状态持久化。
2. **空库初始化**：新数据卷 → 最终 schema → 基础 seed → runtime role → verify；任一步失败时 backend 必须保持未启动。当前交付不提供旧 schema 的自动升级能力。
3. **Redis**：运行中断开，readiness DOWN、登录保护 fail-closed；恢复后计数/会话策略符合定义。
4. **MinIO**：桶不可用、对象丢失、恢复；入库任务可解释失败，引用不返回幽灵对象。
5. **RocketMQ**：binder、name-server、broker、consumer 分别中断；binder 启动健康、name-server `GET_NAMESRV_CONFIG`、broker `GET_BROKER_RUNTIME_INFO`、TCP-only 假服务负向控制、consumer connection、主/retry/DLQ offset 和 outbox lag 均可复核。毒消息按 group 进入 DLQ 后，只能依照 [领域事件恢复手册](rocketmq-domain-event-recovery-runbook.md) 从精确 source outbox 受控重新派发同一 eventId；不得复制 broker 消息体。
6. **外部模型**：timeout、429、5xx、无效凭据、慢响应；不持有 DB 长事务、不重复计费、不泄密。
7. **密钥轮换**：旧 key 解密、新 key 写入、批量重加密、回滚与审计。
8. **租户停用**：会话、API、异步任务和运行调用在目标时间内停止，不影响其他租户。

每次演练记录故障注入、开始/恢复时间、数据核对、RPO/RTO、未覆盖范围和 runbook 修订。

## 运行手册最小集合

- readiness DOWN 定位与安全恢复；
- DB pool/慢查询/初始化失败；
- [RocketMQ 领域事件无正文诊断与 source outbox 受控恢复](rocketmq-domain-event-recovery-runbook.md)；
- RAG 永久 pending/索引重建；
- provider timeout/429/credential failure；
- 登录限流误触发/Redis 不可用；
- 秘密泄漏、会话撤销和租户隔离事件；
- 备份恢复和密钥轮换。

runbook 必须给出只读诊断、影响判断、授权要求、可逆操作、停止条件和验证命令；不得以删除 volume 或清空队列作为默认恢复方式。

### PostgreSQL schema owner/runtime 身份边界恢复手册

`AIP_POSTGRES_USER` 是仅供 PostgreSQL 初始化和受控运维使用的 schema owner；
`AIP_POSTGRES_RUNTIME_USER` 是 backend 唯一数据库登录。二者用户名和随机密码必须
不同，schema owner 密码不得进入 backend。只读诊断应先查看
`database-initialize` 退出状态与日志，再由受权运维人员在 PostgreSQL 容器内执行：

```sh
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps database-initialize backend
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs --no-color database-initialize
docker compose --env-file deploy/.env -f deploy/docker-compose.yml exec postgres \
  psql --username aip_local --dbname aip --no-psqlrc --command \
  "SELECT rolname, rolcanlogin, rolsuper, rolcreatedb, rolcreaterole, rolinherit, rolreplication, rolbypassrls FROM pg_roles WHERE rolname IN ('aip_local','aip_runtime');"
docker compose --env-file deploy/.env -f deploy/docker-compose.yml exec postgres \
  psql --username aip_local --dbname aip --no-psqlrc --command \
  "SELECT privilege, has_table_privilege('aip_runtime','audit_operation_log',privilege) FROM unnest(ARRAY['INSERT','UPDATE','DELETE','TRUNCATE']) privilege;"
```

自定义用户名时用 `.env` 中两个非秘密 username 替换示例，禁止把密码写入命令、日志或
证据。任一 runtime elevated 属性、role membership、public schema CREATE、
未分类 relation/sequence/routine、任何
`SECURITY DEFINER` routine 或 object policy 外 grant 都属于启动 blocker；立即停止/保持 backend 未启动，保留 volume
和只读日志，不执行手工 GRANT、禁用触发器、TRUNCATE 或删除证据。恢复动作仅允许：备份
当前 volume，运行 `sh deploy/bin/upgrade-env.sh deploy/.env` 补齐独立 runtime 凭据，随后
核对 `deploy/postgres/runtime-object-policy.sql` 的显式分类并重跑 `database-initialize`。
只有其内置 role provisioning 与 `verify-schema.sql` 全量权限矩阵同时成功，
且 backend 以 runtime 身份 readiness UP 后才结束事件。若 schema owner 凭据疑似泄漏，
必须轮换 schema owner 与 runtime 两组密码并审计数据库对象所有权及异常授权；单独轮换
runtime 不能恢复已失去的控制面信任。

## 最终证据

- 同一 master SHA 与 deploy project 下的负载报告、故障演练、备份恢复、空卷初始化和浏览器 trace。
- SLI 计算查询/指标定义可复现，不能只提供截图。
- 每项 SLO 有 owner、告警阈值、错误预算和 runbook 链接。
- 未达到的目标留在 `open-questions.json`，不得以“环境限制”标记通过。
