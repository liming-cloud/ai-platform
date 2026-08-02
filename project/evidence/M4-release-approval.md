# M4 人工评审通过记录

## 决定

| 字段 | 值 |
| --- | --- |
| 决定编号 | HITL-0005-M4-RELEASE-APPROVAL |
| 决定类型 | 人工里程碑评审与后续范围确认 |
| 结论 | 通过 M4，进入 M5 设计 |
| 批准人 | 用户，代表产品负责人 |
| 批准时间 | 2026-08-02 |
| 自动验收基线 | `aa174e1c5` |
| 评审请求 | `aa174e1c5` |

## 批准范围

- 接受 `DRAFT` 助手版本的独立 `AssistantTestRun` 与普通 `QueryRun` 的严格隔离；两者共享可信回答执行门面，但不共用聚合、配额、事件、运营事实或历史正文。
- 接受查询反馈的不可变最小事实、同一请求成员的唯一约束、受控前端入口，以及受权诊断事实的原子终态持久化与最小披露边界。
- 接受 ADR-0037、ADR-0038 和 ADR-0039 固定的可信回答执行、诊断历史可得性和失败关闭语义。
- 确认真实模型调用、令牌、凭据与主密钥仍严格遵守既有安全边界；本决定不授权暴露、复制或提交任何密钥。
- 批准后续仅启动 M5 的模型扩展边界设计；新增模型能力、路由、回退、工具、MCP、多模态、音频或公共接口，必须先取得独立已接受 ADR。

## 评审依据

| 项目 | 结论 | 证据 |
| --- | --- | --- |
| 草稿测试与可信回答执行 | 通过 | `project/evidence/WP-0012-draft-assistant-lookup.md`、`project/evidence/WP-0012-assistant-test-run-domain.md`、`project/evidence/WP-0012-assistant-test-run-persistence.md`、`project/evidence/WP-0012-assistant-trusted-answer-execution.md` |
| 反馈与最小披露 | 通过 | `project/evidence/WP-0012-query-feedback-domain.md`、`project/evidence/WP-0012-query-feedback-persistence-api.md`、`project/evidence/WP-0012-query-feedback-browser-entry.md` |
| 诊断原子性与授权读取 | 通过 | `project/evidence/WP-0012-query-diagnostic-projection.md`、`project/evidence/M4-automatic-acceptance-review.md` |
| 全量工程门禁 | 通过 | 自动验收记录中的 `make project-verify` 与 `make validate` 结果 |

## 决定影响

- `WP-0012` 可以关闭，M4 退出。
- 当前前沿切换到仅限设计的 M5 工作包；M6 至 M8 仍不激活、不实现。
- M5 设计必须保持受控模型目录、冻结助手／查询模型快照、可信引用、令牌派生登录态和最小留存边界；所有不可逆公共契约均须先获接受 ADR。
