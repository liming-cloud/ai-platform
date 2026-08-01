# M4 自动验收与人工评审请求

## 决定状态

| 字段 | 值 |
| --- | --- |
| 决定编号 | HITL-0005-M4-REVIEW |
| 决定类型 | 里程碑人工评审 |
| 当前结论 | 自动验收通过，待人工评审；不得进入 M5 |
| 评审基线 | `a800b61f6` |
| 自动验收时间 | 2026-08-01 |

## 本次交付范围

- 助手负责人可在不改变普通 `QueryRun` 仅接受 `PUBLISHED` 版本的边界下，对同租户 `DRAFT` 助手版本执行独立配额、冻结依赖的可信测试。
- 草稿测试与普通查询共享 ADR-0037 固定的唯一可信回答执行门面；两者仍保存各自的聚合、配额、持久化与可见性事实。
- 请求成员可对已完成查询记录一次不可变最小反馈；该事实不保存问题、回答、提示词或证据正文。
- 审计角色仅能读取原子固化的最小查询诊断事实；不重检索、不读取当前模型配置，也不暴露候选正文、相似度、对象位置、模型标识、供应商错误、问题或回答。

## 自动验收依据

| 项目 | 结论 | 证据 |
| --- | --- | --- |
| 草稿测试与普通查询隔离 | 通过 | `WP-0012-draft-assistant-lookup.md`、`WP-0012-assistant-test-run-domain.md`、`WP-0012-assistant-test-run-persistence.md` |
| 统一可信回答执行 | 通过 | `WP-0012-assistant-trusted-answer-execution.md`；普通查询与草稿测试均委派 `AssistantTrustedAnswerExecutor` |
| 查询反馈最小事实与前端入口 | 通过 | `WP-0012-query-feedback-domain.md`、`WP-0012-query-feedback-persistence-api.md`、`WP-0012-query-feedback-browser-entry.md` |
| 诊断事实原子性与读取授权 | 通过 | `WP-0012-query-diagnostic-projection.md`；真实 PostgreSQL 回滚、级联、租户隔离和 Bearer HTTP 回归通过 |
| 领域与架构门禁 | 通过 | `python3 -m unittest scripts.quality.test_backend_architecture_guard`（19 项）、`python3 scripts/quality/backend_architecture_guard.py verify` |
| 全量工程门禁 | 通过 | `make project-verify`、`make validate`；实现基线命令均以退出码 0 结束 |
| 固定比较点技术评审 | 通过 | 本记录评审基线相对 `dcec78e19`；代码图谱复核 `QueryRunTerminalCommitter` 与 `QueryDiagnosticService` 的影响面，未发现阻断问题 |

## 请人工复核

- 以助手负责人身份确认：草稿测试只能作用于当前租户的草稿版本，普通查询仍拒绝草稿版本；测试结果仅暴露当前授权响应所需的最小正文，历史运行不保存题目和回答。
- 以查询成员身份确认：只可对自己已完成的查询提交一次固定枚举反馈；重复提交、跨成员和未完成查询均被拒绝，前端不会把令牌或主体信息作为接口参数。
- 以审计角色确认：`GET /api/v1/query-runs/{queryRunId}/diagnostic` 仅在 `audit:read` 且同租户时返回最小诊断字段；无权限请求不触发数据读取，跨租户、缺失运行和缺失历史事实均不暴露资源存在性。
- 确认 M4 的草稿测试、反馈和审计诊断已形成可操作闭环，并接受其与 M5 的路由、回退、工具、MCP、多模态和新增模型能力严格隔离。
- 如结论为通过，须另建人工批准记录并将 `WP-0012` 与 `STATE.yaml` 切换；在该记录提交前不得进入 M5。

本记录不替代人工评审结论，不关闭 `WP-0012`，不改变当前 `M4` 里程碑。
