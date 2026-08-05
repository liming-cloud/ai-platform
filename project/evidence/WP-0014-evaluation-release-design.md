# WP-0014：私有评测与发布门禁设计／TDD 路线

## 范围与结论

ADR-0050 与 ADR-0051 已接受后，评测与发布制度可在 M6 实施，但只限首批 `SYNTHETIC` 私有数据、
`quality-gate-v1` 和无例外的发布决定。预算、非合成数据、人工例外、审计导出、M7 与 M8 不在本切片。

代码图谱探索确认：

| 符号／影响分析 | 当前事实 | 本切片结论 |
| --- | --- | --- |
| `AssistantService.publishVersion` | 校验租户写权限和模型／索引绑定后直接调用 `AssistantVersion.publish`。 | 在 application seam 注入发布授权端口；不得把评测状态、阈值或跨上下文根放入 `AssistantVersion`。 |
| `AssistantVersion` 影响分析 | 影响 117 个符号，包括发布仓储、查询和草稿测试。 | 不改变版本聚合的状态机；只在获得不可变授权结果后继续既有状态迁移。 |
| `EvaluationOperationsCoreAutoConfiguration` 影响分析 | 当前影响 4 个符号，只装配同租户运营读取。 | 新评测装配独立增加，不能使既有运营读取因缺少私有内容端口而失效。 |
| `AssistantTestRun`、`QueryRun` | 分别保留摘要或普通查询终态，均不是评测数据集。 | 不读写、不迁移、不重放它们的历史为评测正文。 |

## 红绿顺序

1. 先新增 `EvaluationRunTest` 的纯领域反例并实际编译失败：跨租户或未发布数据集、零分母、未知或第十一条候选、
   空引用、错误拒答、执行失败、低于任一阈值都不能形成 `PASSED`；符合三项阈值的冻结观察才可通过。
2. 仅补 evaluation-operations 的根、值对象、无状态 `EvaluationExecution` 和不可变操作结果，使上述领域测试转绿。
3. 新增 `ReleaseDecisionTest` 与 `ReleaseAuthorizationTest` 红灯：非通过运行、目标助手版本或修订不匹配、跨租户和拒绝决定都不能授权；只有完全匹配的通过运行可以产生授权结果。
4. 在已存在的 `AssistantServiceTest` 先写发布授权拒绝红灯，再以应用端口接入，保持 `AssistantVersion` 不感知评测上下文。
5. 再补私有内容端口、真实 PostgreSQL、事务／发件箱、自动配置、令牌派生 HTTP 与浏览器最小投影；每层先保留实际红灯，再逐层转绿。

## 首条验证命令

```bash
./mvnw -q -pl backend/contexts/evaluation-operations -am test \
  -Dtest=EvaluationRunTest -Dsurefire.failIfNoSpecifiedTests=false
```

第一步必须因尚不存在 `EvaluationRunTest` 依赖的领域类型而失败；不得以跳过、空断言或临时 allowlist 伪造红灯。

## 当前证据状态

- 已完成：ADR 接受、领域设计卡、代码图谱探索和影响分析，以及纯领域根、失败关闭和发布授权红绿切片。
- 未开始：私有正文存储、应用编排、迁移、HTTP 或浏览器实现。
- 后续每个完成切片另行登记命令、提交、环境与结果；本文件不构成 M6 自动验收或人工评审通过证据。
