# WP-0011 受治理数据生命周期设计证据

## 目标

为 M3 固定能同时支持文件、对象存储和结构化数据集的最小领域边界，避免将现有 UTF-8 文本发布实现误泛化为包含全部连接器、Schema 和质量行为的巨型数据源对象。

## 已核实的现状

- `TextDataSourceService` 已在可信租户上下文中提供创建、分页列表、UTF-8 上传、对象元数据核验和显式发布。
- `TextDataSource` 使用 `DRAFT`、`CONTENT_READY`、`PUBLISHED` 表达单一文本内容版本，不能表达探测、同步、质量、退役或来源类型。
- `IndexVersion` 已在 PostgreSQL 事务中冻结已发布来源快照，并在 M2 路径中冻结 Embedding 配置、目录条目和维度。
- 企业控制台现有数据入口只服务首份文本发布，尚未形成知识管理员的数据源、同步检查和质量处置工作台。

代码图谱探索：`TextDataSourceService`、`TextDataSource`、`TextDataSourceController`、`TextDataSourceRepository`、`IndexVersion`、`PostgresIndexVersionRepository`。

## 方案比较

| 方案 | 调用方与失败语义 | 结论 |
| --- | --- | --- |
| 扩展 `TextDataSource` | 每个调用方都要处理文件、对象存储、结构化 Schema、探测、同步和质量的无关可选字段；状态机持续膨胀 | 拒绝 |
| 受治理来源身份 + 不可变同步产物 + 类型化连接器 | 列表、授权、质量和索引消费统一来源产物；各连接器局部处理自身探测与同步失败 | 采用 |

## 固定设计

ADR-0028 定义 `GovernedDataSource`、`SourceRevision`、`SyncArtifact`、`QualityReport` 和 `SchemaVersion` 的所有权与不变量。首个纵向切片仅覆盖 `FILE` 来源的登记、探测、不可变同步产物与质量事实；不自动发布，不替换既有活动索引，不实现平台范围管理。

## 下一步

在编码前为首个 `FILE` 切片制定红绿测试与迁移边界，并以真实 PostgreSQL 验证租户隔离、幂等、失败关闭和产物不可变性。
