# WP-0013：M5 最小模型调用轨迹

## 范围

ADR-0040 首条切片为冻结聊天路由的实际候选尝试建立内部、不可变且不公开的轨迹。该切片只涉及 `model-runtime`、`assistant-delivery`、`trusted-query` 的既有执行和终态边界；不启用工具、MCP、多模态、音频、嵌入回退或新的 HTTP 字段。

## 实现事实

- `ChatRouteExecutor` 以冻结顺序记录每次候选的 `SUCCEEDED` 或 `TRANSIENT_FAILURE`；只有 `ChatInvocationTransientException` 才继续后备候选。
- `ChatRouteExecution` 在成功时携带有序尝试列表；全部候选耗尽时 `ChatRouteExhaustedException` 携带同一最小事实。
- `ModelInvocationTrace` 保存尝试序号、冻结配置标识与修订、目录条目标识与修订、标准化结果、标准化令牌用量和非负时延；不保存问题、提示词、候选正文、回答、供应商、端点、错误文本或密钥。
- `trusted_query.query_model_invocation_trace` 与 `assistant_delivery.assistant_test_run_model_invocation_trace` 均按每次候选一行写入，分别与所属运行终态、引用和既有事实在同一 PostgreSQL 事务完成。
- `QueryDiagnostic`、查询事件、草稿测试 HTTP 结果和查询 HTTP 结果未增加轨迹字段。

## 可复现验证

红灯：

```bash
./mvnw -q -pl backend/contexts/model-runtime,backend/contexts/assistant-delivery -am test \
  -Dtest=DefaultChatRouteExecutorTest,ModelInvocationTraceTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

红灯实际原因：测试引用的逐次候选类型和耗尽异常不存在，旧 `ChatRouteExecution` 只包含 `int attempts`。

绿灯及定向回归：

```bash
./mvnw -q -pl backend/contexts/trusted-query,backend/contexts/assistant-delivery -am test \
  -Dtest=DefaultChatRouteExecutorTest,ModelInvocationTraceTest,QueryRunServiceTest,AssistantTestRunServiceTest,PostgresQueryRunRepositoryTest,PostgresAssistantTestRunRepositoryTest,AssistantDeliveryAutoConfigurationTest,TrustedQueryAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false

./mvnw -q -pl backend/bootstrap/query-bootstrap -am test \
  -Dtest=AssistantTestRunHttpTest,QueryRunControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false

python3 -m unittest scripts.quality.test_backend_architecture_guard
python3 scripts/quality/backend_architecture_guard.py verify
git diff --check
```

以上命令均在 2026-08-02 本地通过。两个 PostgreSQL 回归还预插入重复的候选序号以触发轨迹唯一约束；查询或草稿测试的终态、诊断和引用均回滚，仅保留故障注入行。Testcontainers 运行日志包含既有 Mapper 扫描与 Spring 测试告警；无测试失败。

ADR-0040 对齐补充先以新增字段断言运行目标测试，得到 `ChatRouteAttempt` 缺少 `usage()` 与 `latencyMillis()` 的编译红灯；随后补齐路由结果、内部轨迹、两张轨迹表和真实 PostgreSQL 断言。补齐测试构造参数时曾出现两处括号不完整的编译红灯，修正后同一目标命令再次通过。

## 六维评估

| 维度 | 结论 | 证据 |
| --- | --- | --- |
| 正确性 | 仅标准化调用前瞬态失败可回退；成功和失败终态均保留实际尝试。 | 路由、查询和草稿测试应用回归。 |
| 复杂度 | 后备循环只在 `DefaultChatRouteExecutor`。 | 调用方只消费结果或标准异常。 |
| 冗余 | 查询与草稿测试共用可信执行门面，终态持久化各自独立。 | 两个 PostgreSQL 仓储回归。 |
| 性能 | 轨迹最大三行；不重试嵌入、检索、证据选择或配置读取。 | 路由上限与既有执行顺序。 |
| 架构 | 应用 port 承载草稿测试的终态附属事实；领域仓储不依赖调用轨迹 DTO。 | `AssistantTestRunTerminalCommitter` 自动配置回归与架构门禁。 |
| 测试质量 | 已记录实际红灯和绿色真实数据库、自动配置、HTTP 回归。 | 本文件的命令与结果。 |
