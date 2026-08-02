# M5 人工评审通过记录

## 决定

| 字段 | 值 |
| --- | --- |
| 决定编号 | HITL-0006-M5-RELEASE-APPROVAL |
| 决定类型 | 人工里程碑评审与后续范围确认 |
| 结论 | 通过 M5，进入 M6 设计 |
| 批准人 | 用户，代表产品负责人 |
| 批准时间 | 2026-08-02 |
| 自动验收基线 | `b38ec7387` |
| 评审请求 | `65e58deae` |

## 批准范围

- 接受 `AssistantVersion` 的冻结有序聊天候选、仅输出前标准瞬态失败可回退，以及嵌入模型不回退的边界。
- 接受 `ChatRouteExecutor` 的唯一回退执行职责，及 `QueryRun`、`AssistantTestRun` 终态与最小 `ModelInvocationTrace` 的原子提交和最小留存语义。
- 接受真实模型覆盖层的本地密文恢复路径；该路径只新建个人密文配置，不覆盖历史记录，且不暴露或提交 API Key、令牌或主密钥。
- 确认最终用户浏览器只展示受权回答与引用；候选路由、模型配置、供应商、端点、密钥、提示词和原始错误继续保持最小披露与失败关闭。
- 批准后续仅启动 M6 企业治理与发布制度设计；公共契约、领域边界、权限语义、模型能力和持久化结构变更必须先取得独立已接受 ADR。

## 评审依据

| 项目 | 结论 | 证据 |
| --- | --- | --- |
| 冻结路由与原子调用轨迹 | 通过 | `project/evidence/WP-0013-m5-tdd-test-route.md`、`project/evidence/WP-0013-m5-model-invocation-trace.md` |
| 真实运行与最小披露 | 通过 | `project/evidence/WP-0013-m5-browser-runtime-acceptance.md` |
| 自动验收与工程门禁 | 通过 | `project/evidence/M5-automatic-acceptance-review.md` 中的 `make project-verify`、`make validate` 与定向回归结论 |

## 决定影响

- `WP-0013` 可以关闭，M5 退出。
- 当前前沿切换到仅限设计的 `WP-0014`；M6 不得在 ADR 接受前实现生产代码、数据库迁移、公开接口或浏览器功能。
- M7 至 M8 仍不激活、不实现。
