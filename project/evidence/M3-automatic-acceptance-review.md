# M3 自动验收与人工评审请求

## 决定状态

| 字段 | 值 |
| --- | --- |
| 决定编号 | HITL-0004-M3-REVIEW |
| 决定类型 | 里程碑人工评审 |
| 当前结论 | 自动验收通过，待人工评审；不得进入 M4 |
| 评审基线 | `dcec78e19` |
| 自动验收时间 | 2026-07-30 |

## 本次交付范围

- FILE、STRUCTURED_DATASET 与 OBJECT_STORAGE 三类来源均采用冻结修订、同步产物、质量结论、显式发布和版本化索引，不接受正文位置、外部端点或凭据作为控制台业务输入。
- 登录态仅从令牌恢复，接口参数不能伪造租户、主体、成员、渠道或权限。
- ADR-0031 的领域规约和两项 DDD 自动门禁已落实；FILE 同步完成与产物发布的不变量由命名领域服务和不可变操作结果承载。
- ADR-0035 固定来源快照作用域的 `chunk_id`：相同正文的不同已发布来源保留不同分块与实际来源引用，历史完成索引不被改写。

## 自动验收依据

| 项目 | 结论 | 证据 |
| --- | --- | --- |
| 领域边界与同步发布 | 通过 | `WP-0011-domain-aggregate-operation-boundary.md` |
| FILE、CSV 与对象存储生命周期 | 通过 | `WP-0011-data-lifecycle-design.md`、`WP-0011-object-storage-synchronization.md`、`WP-0011-structured-dataset-synchronization.md` |
| 令牌派生安全上下文 | 通过 | `WP-0011-token-derived-authentication-context.md` |
| 来源作用域分块标识 | 通过 | `ADR-0035`；`IndexBuildWorkerTest`、`IndexSourceSnapshotTest` 与 `PgVectorIndexStoreTest` |
| 真实浏览器与受控运行时 | 通过 | 本地 Compose 中 OBJECT_STORAGE 完成登记、探测、同步 `PASS`、产物发布、真实嵌入索引发布和一次重建后再次发布；内部对象位置、正文、凭据和追踪标识均未记录 |
| 自动配置与 HTTP 回归 | 通过 | `KnowledgeDataAutoConfigurationTest`；`make validate` 的控制面 HTTP 回归 |
| 全量工程门禁 | 通过 | `make project-verify`、`make validate` |

## 请人工复核

- 确认数据管理员在三类来源上只能提交业务元数据和平台签发范围，不能取得对象位置、正文、密钥或任意外部连接能力。
- 确认同步、质量、发布、索引、重建和活动切换符合产品中的显式、可追溯、可恢复语义；特别复核 CSV 多记录以 `\n\n` 分隔的规范化结果。
- 确认同一正文来自两个来源时，引用不会被内容去重吞并，且历史完成索引保持不变。
- 确认令牌派生的登录态边界以及 M3 不实施的跨页签单点登录体验仍按 ADR-0027 留给 M6。
- 如结论为通过，须另建人工批准记录并将 `WP-0011` 与 `STATE.yaml` 切换；在该记录提交前不得进入 M4。

本记录不替代人工评审结论，不关闭 WP-0011，不改变当前 `M3` 里程碑。
