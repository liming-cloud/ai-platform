# M3 人工评审通过记录

## 决定

| 字段 | 值 |
| --- | --- |
| 决定编号 | HITL-0004-M3-RELEASE-APPROVAL |
| 决定类型 | 人工里程碑评审与后续范围确认 |
| 结论 | 通过 M3，进入 M4 设计 |
| 批准人 | 用户，代表产品负责人 |
| 批准时间 | 2026-07-30 |
| 自动验收基线 | `dcec78e19` |
| 评审请求 | `b3657f5e6` |

## 批准范围

- 接受 FILE、OBJECT_STORAGE 与 STRUCTURED_DATASET 的受治理来源、冻结修订、同步、质量、显式发布、索引和角色化浏览器闭环证据。
- 接受 ADR-0031 的领域设计规约、同步完成与产物发布操作边界，以及 ADR-0033 的令牌派生登录态安全边界。
- 接受 ADR-0035 的来源快照分块身份语义：相同正文但来源不同的分块不得相互复用身份。
- 确认真实模型调用、凭据与主密钥仍严格遵守既有安全边界；本决定不授权暴露、复制或提交任何密钥。
- 批准后续先完成 M4 的设计冻结，并仅在新的人工评审结论提交后再推进对应实现和后续里程碑。

## 评审依据

| 项目 | 结论 | 证据 |
| --- | --- | --- |
| 数据生命周期与索引闭环 | 通过 | `project/evidence/M3-automatic-acceptance-review.md` 与 WP-0011 分项领域、持久化、HTTP、浏览器和运行证据 |
| 领域边界与安全 | 通过 | `project/evidence/WP-0011-domain-aggregate-operation-boundary.md`、`project/evidence/WP-0011-token-derived-authentication-context.md` |
| 跨来源分块身份 | 通过 | `project/evidence/M3-automatic-acceptance-review.md` 中 ADR-0035 的真实 PostgreSQL、自动配置、HTTP、前端与浏览器续证 |
| 全量工程门禁 | 通过 | 自动验收记录中的 `make project-verify` 与 `make validate` 结果 |

## 决定影响

- `WP-0011` 可以关闭，M3 退出。
- 当前前沿切换到仅限设计的 M4 工作包；M5 至 M8 仍不激活、不实现。
- M4 设计必须保留已接受的助手不可变版本、可信引用、令牌派生登录态、索引冻结和模型配置同源绑定，新增不可逆公共契约前必须先取得已接受 ADR。
