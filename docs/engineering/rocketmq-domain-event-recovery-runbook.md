# RocketMQ 领域事件诊断与受控恢复手册

状态：当前实现对应的人工运行手册；只适用于 `deploy/` 基线。它不是自动重放能力，也不替代变更审批、数据库备份或事故复盘。

## 1. 边界与禁止事项

领域事件的事实源是 PostgreSQL 业务事务及其 outbox，RocketMQ 只是至少一次传输层。当前五个 consumer binding 把 Spring Cloud Stream 的应用内重试固定为 `max-attempts=1`，失败交给 RocketMQ 原生 push consumer；`deploy/` 的原生 `max-reconsume-times=5`、延迟级别 `3` 可由受控环境变量覆盖。预算耗尽后的 `%DLQ%<consumer-group>` 只用于隔离和诊断。

必须遵守以下边界：

- **禁止**使用 `queryMsgByKey`、`printMsg`、`consumeMessage` 或控制台导出读取、复制、下载或粘贴 broker 消息体。
- **禁止**把 `%DLQ%...` 中的消息体直接发送回主 topic 或 retry topic；这样会绕过业务事实源、租户核对、变更审计和当前 schema 生成逻辑。
- **禁止**清空 topic、删除 broker volume、重置整个 consumer group offset，或以修改 payload、tenantId、eventId 的方式“修复”消息。
- **禁止**把 readiness `UP` 当作消费链路健康。当前 readiness 同时要求 Spring Cloud Stream `binders` 启动健康，并要求至少一个 name-server 对只读 `GET_NAMESRV_CONFIG`、至少一个 broker 对只读 `GET_BROKER_RUNTIME_INFO` 返回成功且与请求相关联；探针不发送业务消息、不读取消息体。它仍不证明 consumer 已连接、lag 已收敛或业务事件已成功处理，这些事实必须独立取证。
- 只有 source outbox 中精确存在且唯一的原事件，根因已经通过新候选部署或配置修复，并有事故单、变更单、独立审批人和不可变外部操作审计时，才允许重新派发同一 `event_id`。当前没有通用 broker DLQ 自动重放 API 或运营 UI。

## 2. 三类恢复不能混用

| 类型 | 事实源与已实现入口 | 正确恢复 | 不允许 |
|---|---|---|---|
| RocketMQ 领域事件 DLQ | PostgreSQL 业务事实及 transactional outbox；consumer inbox/receipt 幂等 | 修复消费者或依赖后，从精确 source outbox 行受控重新派发同一 eventId | 复制 broker 消息体、重写 envelope、整组 offset 回退 |
| 知识入库作业 | `kb_embedding_job`；`DocumentIngestRequested` 只是 best-effort wake，`PENDING` 仍由 DB poller 发现 | `PENDING/RETRYING` 等待 worker；只有当前租户当前文档的 `FAILED` 解析作业可通过 `POST /api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/parse` 显式重排 | 从 broker DLQ 恢复作业、后台自动重排 `FAILED`、跨租户改 job |
| 对话最终化死信 | `app_chat_run` 的 `DEAD_LETTER + RESULT_RECEIVED` 持久化状态 | 双权限管理员使用 `POST /api/v1/chat-runs/{runId}/dead-letter/replay`，携带稳定 `replayRequestId` 与非空 reason；服务端按当前租户 claim 并写 HIGH 风险审计 | 使用 RocketMQ 命令、重调 provider、跳过租户/RBAC/审计 |

领域事件 source 映射：

| 事件 | source outbox | 需要核对的 consumer group / receipt |
|---|---|---|
| `ModelConfigApprovalRequested`、`ModelApprovalCancellationRequested` | `mdl_approval_request_outbox` | `cg-ai-platform-approval`；审批任务或撤销事实必须仍属于同一租户/requestId |
| `ApprovalDecisionRecorded` | `appr_decision_outbox` | `cg-ai-platform-model-hub`；`mdl_approval_decision_inbox` |
| `ModelUsagePolicyPublished` | `mdl_model_usage_policy_outbox` | `cg-ai-platform-app-studio` 与 `cg-ai-platform-knowledge`；`app_model_usage_policy_event_receipt` 与 `kb_model_usage_policy_event_receipt` |
| `ApplicationReleasePublished`、`ApplicationReleaseRolledBack` | `app_application_release_outbox` | 当前配置没有专用 application-release consumer binding；不得把“没有下游副作用”诊断为 DLQ 并盲目重派发，必须先补齐并验收消费契约 |
| `DocumentIngestRequested` | `kb_embedding_job`，不是 transactional outbox | `cg-ai-platform-knowledge-ingestion`；按上表的数据库作业入口恢复 |

## 3. 触发条件、角色与停止条件

进入本手册的信号是：业务事实已提交但预期投影缺失、consumer lag 持续增长、retry/DLQ offset 增长，或依赖恢复后同一 group 仍不能收敛。事故指挥人负责影响判断；应用 owner 判断 schema/业务根因；RocketMQ operator 只做无正文诊断；DBA 执行 source outbox 变更；独立审批人不得与执行人相同。

出现任一条件立即停止，不执行重新派发：

1. 无法唯一确定 tenant、eventId、eventType、source table 或预期 consumer group。
2. source outbox 查不到行、命中多行、状态为未到期的 `IN_FLIGHT`，或 payload/envelope 必须修改才能被当前消费者接受。
3. 消费者代码、schema、权限、数据库约束或下游依赖根因尚未修复并通过同候选验证。
4. 已存在正确 inbox/receipt 和业务投影，或无法证明重复消费对计费、外部调用及状态推进无副作用。
5. 需要读取/导出 broker 消息体、清空队列、重置整组 offset、跨租户查询或扩大到多个 eventId。
6. 没有变更单、独立审批、不可变外部操作审计、回滚窗口或明确 owner；共享平台出现无关 group lag 同时增长时升级为平台事故。

## 4. 无消息体只读诊断

以下命令中的 `<project>` 必须是已登记的候选 Compose project；不得省略 `--env-file deploy/.env -f deploy/docker-compose.yml` 转去其他本地基线。证据记录候选 SHA、UTC 时间、命令、退出码、group、topic、queueId、broker offset、consumer offset 和 diff，但不得记录消息正文、凭据或完整环境。

```sh
docker compose -p <project> --env-file deploy/.env -f deploy/docker-compose.yml ps

docker compose -p <project> --env-file deploy/.env -f deploy/docker-compose.yml \
  exec -T rocketmq-broker timeout 8 sh mqadmin consumerConnection \
  -g <allowlisted-consumer-group> -n rocketmq-namesrv:9876

docker compose -p <project> --env-file deploy/.env -f deploy/docker-compose.yml \
  exec -T rocketmq-broker timeout 8 sh mqadmin consumerProgress \
  -g <allowlisted-consumer-group> -n rocketmq-namesrv:9876

docker compose -p <project> --env-file deploy/.env -f deploy/docker-compose.yml \
  exec -T rocketmq-broker timeout 8 sh mqadmin topicStatus \
  -t '%RETRY%<allowlisted-consumer-group>' -n rocketmq-namesrv:9876

docker compose -p <project> --env-file deploy/.env -f deploy/docker-compose.yml \
  exec -T rocketmq-broker timeout 8 sh mqadmin topicStatus \
  -t '%DLQ%<allowlisted-consumer-group>' -n rocketmq-namesrv:9876
```

同时访问 `/actuator/health/readiness`：binder 启动不健康，或 name-server/broker 任一组没有端点对各自只读管理命令返回成功时，应返回 503；binder 与两组协议响应恢复后应回到 200。仅能建立 TCP 连接但不返回合法 RocketMQ 成功响应的假服务也必须为 503。该结果不能替代上述 consumer/offset 证据。

从 API、审计或 trace 取得预期 eventId 后，只查询 source 元数据，不选择 `payload_json`：

```sql
SELECT event_id, event_type, tenant_id::text AS tenant_id,
       status, attempts, next_attempt_at, lease_until,
       published_at, last_error, updated_at
FROM <one-allowlisted-source-outbox-table>
WHERE event_id = :'event_id'
  AND tenant_id::text = :'tenant_id';
```

结果必须恰好一行。随后以 eventId + tenantId 核对对应 inbox/receipt、业务资源与审计事实；任何查询都必须同时带租户谓词。不得因为 eventId 看似全局唯一而省略租户边界。

## 5. 决策与恢复路径

1. **name-server/broker 不可达**：只恢复受影响服务和网络；source outbox 为 `PENDING` 时由 lease/退避 dispatcher 自动重试，不做人工状态变更。
2. **consumer 未连接或 lag 增长**：修复消费者部署/依赖。原生重试预算尚未耗尽时观察 retry offset 收敛，不创建第二条消息。
3. **poison/schema/envelope 失败**：保持 DLQ 隔离；先用新候选修复消费者或兼容策略。不得修改原 source payload。若原事实本身非法，走领域补偿/新业务命令，不重新派发非法事件。
4. **知识 wake 失败**：检查 `kb_embedding_job`。`PENDING/RETRYING` 由 DB worker 恢复，`FAILED` 使用租户认证的显式 parse 入口；不操作 broker DLQ。
5. **对话最终化死信**：使用产品 dead-letter API；不进入本节 source outbox SQL。
6. **已修复且 source outbox 为 `PUBLISHED`，下游事实确实缺失**：才进入下一节的单事件受控重新派发。

## 6. 单事件 source outbox 重新派发

变更单必须冻结：候选 SHA、事故号、tenantId、eventId、eventType、source table、目标 group、根因修复证据、预期唯一副作用、验证查询、回滚时限和执行/审批人。数据库变更和 Compose 操作输出写入权限为 `0600` 的仓库外证据目录；不导出 payload。

当前 dispatcher 与 API 同进程，没有独立暂停开关。为保留提交后的回滚窗口，默认在批准的维护窗口停止候选 backend，再变更精确一行：

```sh
docker compose -p <project> --env-file deploy/.env -f deploy/docker-compose.yml stop backend
```

用容器内已有的 PostgreSQL 身份打开审计会话，不在命令行展开密码；进入后先用 `\set event_id '...'` 与 `\set tenant_id '...'` 设置已批准的两个值：

```sh
docker compose -p <project> --env-file deploy/.env -f deploy/docker-compose.yml \
  exec postgres sh -ec 'exec psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"'
```

在 `psql` 会话中选择上表中的**一个字面量表名**，不得拼接用户输入或一次处理多表。以下模板只允许把 `<one-allowlisted-source-outbox-table>` 替换为该固定表名：

```sql
BEGIN;
SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '5s';

SELECT event_id, event_type, tenant_id::text AS tenant_id,
       status, attempts, published_at, lease_owner, lease_until
FROM <one-allowlisted-source-outbox-table>
WHERE event_id = :'event_id'
  AND tenant_id::text = :'tenant_id'
FOR UPDATE;

UPDATE <one-allowlisted-source-outbox-table>
SET status = 'PENDING',
    next_attempt_at = NOW(),
    lease_owner = NULL,
    lease_until = NULL,
    updated_at = NOW()
WHERE event_id = :'event_id'
  AND tenant_id::text = :'tenant_id'
  AND status = 'PUBLISHED'
  AND lease_owner IS NULL
  AND lease_until IS NULL
RETURNING event_id, event_type, tenant_id::text AS tenant_id,
          status, attempts, published_at, updated_at;

-- 只有 SELECT 与 UPDATE 各返回精确一行且元数据与批准单完全一致时，
-- 执行人才手工输入 COMMIT；否则手工输入 ROLLBACK。不得预先批量执行 COMMIT。
```

backend 尚未启动前，如发现元数据或批准范围错误，保持服务停止并在新事务中执行下列精确回滚；返回行不是 1 时必须 `ROLLBACK` 并升级，不得继续尝试其他 eventId：

```sql
BEGIN;
SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '5s';

UPDATE <one-allowlisted-source-outbox-table>
SET status = 'PUBLISHED',
    lease_owner = NULL,
    lease_until = NULL,
    updated_at = NOW()
WHERE event_id = :'event_id'
  AND tenant_id::text = :'tenant_id'
  AND status = 'PENDING'
  AND lease_owner IS NULL
  AND lease_until IS NULL
RETURNING event_id, event_type, tenant_id::text AS tenant_id,
          status, attempts, published_at, updated_at;

-- 只有 UPDATE 返回精确一行时，执行人才手工输入 COMMIT；否则输入 ROLLBACK。
```

确认无误后启动 backend：

```sh
docker compose -p <project> --env-file deploy/.env -f deploy/docker-compose.yml up -d --no-deps backend
```

dispatcher 会以同一 eventId 再次发送，consumer 依靠 inbox/receipt 和领域幂等收敛。一旦 backend 启动并重新发布，就不能“召回”消息；若出现非预期副作用，立即停止 backend，保留 DB/MQ 证据，并按领域补偿流程处理，禁止删除 broker/DB 事实。

## 7. 验收、租户隔离与结案

恢复只有同时满足以下条件才通过：

- readiness 回到 200，并已分别证明 binder 启动失败、name-server `GET_NAMESRV_CONFIG` 失败、broker `GET_BROKER_RUNTIME_INFO` 失败及 TCP-only 假服务都会返回 503；consumer connection 仍须独立存在。
- 主 topic 与 `%RETRY%<group>` 的 consumer diff 收敛为 0；观察窗口内 `%DLQ%<group>` offset 不再增长。历史 DLQ 保留不是失败，不得为“清零”删除它。
- source outbox 回到 `PUBLISHED`，eventId 未变化，attempts 只随本次 claim 增加；对应 inbox/receipt 与业务投影只出现一个最终事实。
- 用批准单中的 tenantId 查询得到预期事实；抽查另一个租户的计数、版本与投影均未变化。任何跨租户差异立即按安全事件升级。
- 不重复外部模型调用、不重复计费、不跳过审批、不复活旧策略；失败关闭路径在恢复前后保持成立。
- 变更单包含 before/after 元数据、offset/consumer/readiness 证据、执行人、独立审批人、候选 SHA、时间、结果与回滚判断；日志和证据中消息体/秘密命中为 0。

## 8. 当前能力缺口

- 当前没有 broker DLQ 的租户安全检索 API、通用 source-outbox re-dispatch API、应用级 re-dispatch 审计记录或自动 replay；本手册依赖不可变外部变更审计和双人操作，不能宣传为产品化自动恢复。
- 当前 `deploy/` 没有声明 RocketMQ Prometheus exporter 或自动告警规则；验收证据来自 readiness 与有界 `mqadmin` offset/connection 输出。接入指标前不得声称已有 broker Prometheus 告警。
- `app_application_release_outbox` 已发布事件，但当前五个 consumer binding 没有专用 application-release 消费者；在消费契约交付前，不能用重复派发代替缺失的下游能力。
- 生产级 RTO/RPO、告警阈值和长期保留策略仍须用固定候选 SHA 的故障演练校准；本手册完成不等于 SLO 已达标。
