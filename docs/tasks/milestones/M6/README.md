# M6 实现与验收

细项唯一来源为 [implementation-map.json](implementation-map.json)：78 入口、155 操作，66 个 M6 生产入口。页面与跨包操作分别判定，不能以页面存在或原型通过替代真实生产。

按 [ADR-0092](../../../architecture/decisions/ADR-0092-m6-platform-overview-late-delivery.md)，工作顺序为 WP-0017 → 0018 → 0019 → 0020 → 0021 → 0022 → 0023 → 0024 → 0025 → 0027 → 0026 → WP-0014 人工复评。平台概览转交 WP-0027，仍属 M6、待冻结契约；WP-0026 只做全量复验。当前状态只读[任务索引](../../README.md)。

矩阵只接受已冻结契约、真实验证命令、责任包证据和完整源码提交。原型页面清单与操作账本从 WP-0016 冻结资产读取；原人审仅证明原型。历史 G1～G10 的时点证据由 history-index 恢复，不声称当前 M6 已通过。

M6 收口同时满足安全、可信质量、性能、三角色真实旅程、功能与原型一致性及人工接受。WP-0017 在 ADR-0092 调整后的 11 个入口、20 项操作范围完成[组合证据与最终部署收口](../../WP-0017/evidence/WP-0017-adjusted-scope-closure-20261006.md)。WP-0018 的生产契约已接受，普通成员账户完成[首片收口](../../WP-0018/evidence/WP-0018-account-prototype-parity-closure-20261008.md)，四项对话功能完成[独立保存与原型一致性收口](../../WP-0018/evidence/WP-0018-features-production-parity-closure-20261008.md)。七项完整操作 verified，其余十二项 contracted；下一动作是助手／策略统一授权，固定角色／完整工作区仍未交付。平台概览仍由 WP-0027 交付，其他生产入口按实现矩阵推进；WP-0018 整包、66 页生产实现和 M6 均未完成。
