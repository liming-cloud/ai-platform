# WP-0014 发布决定与助手发布授权证据

## 范围

按 ADR-0050、ADR-0051、ADR-0052 实现同租户评测终态到助手草稿发布的最小闭环。没有开放评测正文读取、运行列表、预算例外、导出或 M7／M8 能力。

## 领域与契约

- `EvaluationRunReleaseFact` 冻结 `policyVersion`；`ReleaseDecision` 同时冻结策略版本、结论和枚举原因码。
- 原因码仅为 `QUALITY_GATE_PASSED` 与 `QUALITY_GATE_FAILED`；状态与原因码不一致、跨租户、目标修订或策略版本不匹配均拒绝授权。
- 发布 HTTP 先以 `assistant:write` 校验，再把决定标识与当前草稿修订交给评测上下文复核；评测事实或决定缺失时失败关闭。
- `release_decision` 以 `(tenant_id, evaluation_run_id)` 唯一约束，且只保存运行、目标、策略、结论、原因码和时间，不保存正文。

## 可复现验证

```bash
./mvnw -q -pl backend/contexts/evaluation-operations -am test -Dsurefire.failIfNoSpecifiedTests=false
./mvnw -q -pl backend/contexts/assistant-delivery -am test -Dsurefire.failIfNoSpecifiedTests=false
python3 -m unittest scripts.quality.test_backend_architecture_guard
python3 scripts/quality/backend_architecture_guard.py verify
```

以上命令在当前 macOS 工作区通过。真实 PostgreSQL/Testcontainers 回归验证 Flyway、策略版本、固定原因码和跨租户拒绝；Spring、MyBatis 与 Testcontainers 的既有警告未导致测试失败。

## 代码图谱影响

在修改前探索 `EvaluationRunReleaseFact`、`ReleaseDecision`、`ReleaseAuthorization`、`PostgresEvaluationRunRepository`、自动配置和评测 HTTP 入口；影响分析确认策略版本需要随最小运行事实流动，不能由仓储或应用层补写。
