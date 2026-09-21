# 文档入口

本入口只导航当前有效基线，不保存任务进度。接受依据为既有 ADR 和 ADR-0081 的用户执行计划；文档版本由 Git 固定。历史决策正文保留当时语境，现行规则及取代范围见主题文档和决策索引。

| 文档 ID | 唯一主题 | 负责人 | 接受依据 |
| --- | --- | --- | --- |
| GOALS | [章程](foundation/charter.md)与[长期路线](foundation/roadmap.md) | 产品 | ADR-0001、0074、0079 |
| GOVERNANCE | [文档与任务控制](standards/document-control.md) | 治理 | ADR-0081 |
| REPOSITORY | [仓库结构与能力归属](architecture/repository.md) | 架构 | ADR-0002、0022、0059、0081 |
| BACKEND | [后端工程约束](architecture/backend.md) | 后端 | ADR-0022、0031、0074 |
| DOMAIN | [领域与公共契约](design/domain-and-contracts.md) | 产品、后端、安全 | 对应主题列出的 ADR |
| UI | [产品与前端设计](design/product-and-ui.md) | 产品、前端 | ADR-0059、0067、0073、0074、0079、0080 |
| OPERATIONS | [交付](design/operations/demo-delivery.md)、[恢复](design/operations/demo-backup-recovery.md)、[容量](design/operations/demo-capacity-resilience.md) | 运维 | ADR-0020 及原 M1 接受记录，非 M8 生产签署 |

开始任务：读取根 AGENTS、[任务索引](tasks/README.md)、当前任务 README 及其列出的基线；只读排查不创建状态或报告。决策详见[生成索引](architecture/decisions/README.md)。

历史资料通过 `python3 -B scripts/project_governance.py history <原路径>` 只读恢复，定位见 [history-index.json](tasks/history-index.json)。该命令验证原提交和摘要，不把历史内容恢复进工作树。证据原文的旧相对路径按原始位置解析，不能作为新的现行规则。
