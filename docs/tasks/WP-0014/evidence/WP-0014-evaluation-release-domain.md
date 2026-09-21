# WP-0014：私有评测与发布门禁领域基础

## 结果

evaluation-operations 已新增三个独立聚合根：`EvaluationDataset`、`EvaluationRun` 与
`ReleaseDecision`。`EvaluationExecution` 将评测完成不变量收回领域，`ReleaseAuthorization`
仅根据冻结运行事实和发布决定返回不可变授权结果；两者均不接收或输出题目、回答、候选、引用正文、凭据或令牌。

`EvaluationDataset` 仅接受 `SYNTHETIC` 分类及私有内容引用／摘要。`EvaluationRun` 冻结目标草稿修订、
数据集快照、质量门禁快照和指标实现版本；未发布或跨租户数据集、零分母、低阈值、错误引用、错误拒答、
第十一候选、执行失败或未授权证据都会稳定终态为 `FAILED`。`ReleaseDecision` 只从运行事实导出
`AUTHORIZED` 或 `REJECTED`，没有例外路径；发布授权要求租户、运行标识和目标版本修订完全匹配。

## 红绿证据

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| `EvaluationExecution` 红灯 | `./mvnw -q -pl backend/contexts/evaluation-operations -am test -Dtest=EvaluationExecutionTest -Dsurefire.failIfNoSpecifiedTests=false` | 因 `domain.evaluation` 领域类型尚不存在而 `testCompile` 失败。 |
| `ReleaseAuthorization` 红灯 | `./mvnw -q -pl backend/contexts/evaluation-operations -am test -Dtest=ReleaseAuthorizationTest -Dsurefire.failIfNoSpecifiedTests=false` | 因冻结运行事实、发布决定和授权服务尚不存在而 `testCompile` 失败。 |
| 两条领域绿灯 | `./mvnw -q -pl backend/contexts/evaluation-operations -am test -Dtest=EvaluationExecutionTest,ReleaseAuthorizationTest -Dsurefire.failIfNoSpecifiedTests=false` | 通过。 |
| 模块回归 | `./mvnw -q -pl backend/contexts/evaluation-operations -am test` | 通过；日志含既有 Spring、Testcontainers 和预期失败夹具警告。 |

## 架构门禁整改

初始实现使 `domain/evaluation/model` 达到 14 个类型，`backend_architecture_guard.py verify` 正确拒绝超过
12 的包预算。没有新增债务基线或 allowlist；已将“执行观察与指标”迁至 `domain/evaluation/observation`，使
模型包只保留数据集与运行的身份、快照、状态和根。整改后运行：

```bash
python3 -m unittest scripts.quality.test_backend_architecture_guard
python3 scripts/quality/backend_architecture_guard.py verify
```

两项均通过（19 项 Python 测试；架构债务递减门禁通过）。

## 未完成

本切片尚未新增私有正文存储、应用编排、真实 PostgreSQL、迁移、发件箱、自动配置、受限 HTTP、浏览器任务，
也尚未将授权端口接入 `AssistantService.publishVersion`。这些后续层必须继续按红绿顺序实施；本证据不构成
M6 自动验收或人工评审通过。
