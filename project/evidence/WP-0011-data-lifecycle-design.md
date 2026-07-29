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

继续为 `FILE` 来源补齐登记、探测、不可变同步产物和质量事实的持久化与应用闭环，并以真实 PostgreSQL 验证租户隔离、幂等、失败关闭和产物不可变性。

## 首个领域红绿切片

- 红灯：使用 JDK 26.0.2 和 Maven Wrapper 3.9.16 运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -pl backend/contexts/knowledge-data -am -Dtest=GovernedDataSourceTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  `GovernedDataSourceTest` 因缺少 `GovernedDataSource`、`GovernedDataSourceType` 和 `GovernedDataSourceStatus` 编译失败，证明测试先于实现存在。
- 绿灯：同一命令在最小领域实现后通过；随后运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am test`，知识数据及其依赖上下文回归通过。

### 六维检查

- 正确性：测试覆盖文件来源的探测、同步、退役与乐观修订拒绝；状态机不处理尚未实现的产物发布。
- 安全：领域对象不接收连接字符串、密钥、对象正文或租户请求参数；租户和知识空间归属保持必填。
- 复杂度：状态迁移集中于一个不可变聚合，未增加服务或连接器层。
- 性能：仅执行常量时间的值校验与对象复制，不进行 I/O、数据库或外部调用。
- 架构：实现位于 `knowledge-data` 领域层，仅依赖共享内核和本上下文值对象；未引入框架类型。
- 测试质量：先获得缺类编译红灯，再以相同命令转绿；完整知识数据上下文回归通过。
