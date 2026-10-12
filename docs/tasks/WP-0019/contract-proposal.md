# WP-0019：生产契约接受记录

状态：已接受设计，未表示生产实现通过。

用户于 2026-10-12 回复“人工接受，继续推进任务”，接受提案 D1～D5。唯一生效决定见 [ADR-0095](../../architecture/decisions/ADR-0095-model-supply-and-tenant-use-contract.md)；不在此维护第二份决定正文。原提案可从 `ac1859cc66078bb5c1d1a89dd16e342ef4ef3a71:docs/tasks/WP-0019/contract-proposal.md` 精确恢复，原设计与执行证据绑定 `531a4bd4f2680deda86ac66c4b875af87fe485c3`。

实施按[设计与切片计划](design-and-slices.md)从 S1 开始。仅在所属完整验证通过后更新对应实现矩阵，S2～S6、WP-0019 整包和 M6 均未完成。

## S1 具体请求与来源绑定

依据 ADR-0095，创建平台连接新增必填的 `providerPresetId`、`providerPresetVersion`、`capability`；仅阿里云 Chat／Rerank 提交 `workspaceId`。预设目录由 `GET /api/v1/platform/model-supply/connections/presets` 返回。创建沿用现有幂等键、显式协议、区域、地址和专用凭据字段；后端拒绝未知／过期预设、能力／协议／区域／地址不一致以及非法空间标识，不能由浏览器选择推导授权。

连接修订持久化预设来源和能力意图。公开摘要只返回预设 ID／版本／能力，不返回工作空间、运行地址或凭据。旧记录缺少来源时保留空值，不按主机名猜测厂商或能力，不补造已验证事实。RERANK 意图的真实执行能力仍由 S2 验证；本次不扩充运行能力枚举。
