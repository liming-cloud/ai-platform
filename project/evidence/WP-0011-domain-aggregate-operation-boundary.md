# WP-0011 领域聚合操作边界整改证据

## 范围

本记录绑定提交 `cfccfe3f52d9b30a8368e170fc365ca5e0285733`，落实 ADR-0031 的全平台领域设计规约，并整改已确认的首批领域边界：助手版本创建、FILE 同步完成与产物发布、成员认证读取投影和配额策略。

本轮只推进 WP-0011。不新增数据源类型、外部端点、对象定位、密钥、跨上下文事件或索引发布语义。WP-0011 仍为“进行中”，M3 尚未申请或获得人工评审，不能进入 M4。

## 固定设计与实现

- `docs/architecture/foundation-domain-design.md` 第 14 节固定统一语言、上下文所有权、聚合、实体、值对象、读取投影、领域服务、仓储、应用层、事件、安全、设计卡、自动门禁、人工评审和例外治理规约。
- ADR-0031 规定非根 `Entity`、应用服务持有领域状态对象等边界偏差必须整改，不得以债务基线或 allowlist 豁免；新增 `DomainPolicy` 作为无状态策略的明确语义类型。
- 架构门禁新增两项全仓生产代码检查：领域层的 `Entity` 必须同时是 `AggregateRoot`；应用服务构造器不得注入 `Entity`、`AggregateRoot` 或普通 `ValueObject`。当前扫描结果均为零。
- `AssistantVersion` 与 `SyncArtifact` 明确为聚合根；`MemberAuthenticationProfile` 收敛为读取投影值对象；`QueryQuotaPolicy` 和 `OperationsQuotaPolicy` 收敛为 `DomainPolicy`。
- FILE 同步完成由 `FileDataSourceSynchronization` 在纯领域层同时验证来源、当前修订、状态和不可变产物／质量结论，并返回 `FileSynchronizationCompletion`；产物发布由 `FileSyncArtifactPublishing` 返回 `FileSyncArtifactPublication`。
- 应用服务只编排可信上下文授权、来源范围加载、事务外对象读取、时间和标识生成及操作结果提交。`PostgresGovernedDataSourceRepository` 只执行映射、来源锁定、乐观更新和同一事务写入，不再重演来源归属、质量或状态机判断。

## 代码图谱与影响分析

- 已探索 `FileDataSourceSynchronizationService`、`FileSyncArtifactPublicationService`、`PostgresGovernedDataSourceRepository`、`GovernedDataSource`、`SyncArtifact`、`QualityReport`、`PublishedSyncArtifact` 与 `AssistantVersioning`。
- 已对 `GovernedDataSourceRepository`、`FileDataSourceSynchronizationService` 和 `FileSyncArtifactPublicationService` 执行影响分析，确认影响集中在知识数据领域端口、应用编排、PostgreSQL 适配器、自动配置和对应领域／应用／HTTP／真实数据库测试。
- 该分析排除了泛化连接器或跨上下文抽象：本轮采用两个 FILE 专用领域服务和两个不可变操作结果，旧文本路径、对象存储范围和索引构建保持不变。

## 红绿记录

- 红灯：先添加 `FileDataSourceOperationsTest`，运行
  `./mvnw -q -pl backend/contexts/knowledge-data -am -Dtest=FileDataSourceOperationsTest -Dsurefire.failIfNoSpecifiedTests=false test`。测试按预期因缺少 `FileDataSourceSynchronization` 和 `FileSyncArtifactPublishing` 而编译失败。
- 绿灯：以相同命令在最小实现后通过。领域反例覆盖非 FILE 来源、非当前修订、非 `SYNCING` 状态、错误失败操作、阻断质量结论和跨来源产物。

## 可复核验证

- `python3 -m unittest scripts.quality.test_backend_architecture_guard`：19 项通过。
- `python3 scripts/quality/backend_architecture_guard.py verify`：通过。
- `./mvnw -q -pl backend/contexts/knowledge-data -am -Dtest=FileDataSourceOperationsTest,FileDataSourceSynchronizationServiceTest,FileSyncArtifactPublicationServiceTest,KnowledgeDataAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- `./mvnw -q -pl backend/contexts/knowledge-data -am -Dtest=PostgresGovernedDataSourceRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，使用真实 Testcontainers PostgreSQL 验证原子提交、并发和租户隔离。
- `./mvnw -q -pl backend/contexts/knowledge-data -am -Dtest=GovernedFileDataSourceControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过。
- `./mvnw -q -pl backend/contexts/knowledge-data -am test`：通过。
- `make project-verify`：通过，识别 11 个工作包，当前前沿仍为 WP-0011。
- `make validate`：通过，包含治理与架构门禁、全仓 `./mvnw -q clean test`、前端类型检查／Lint／测试／构建以及演示治理检查。
- `git diff --check`：通过。

## 六维复核

- 正确性：领域服务在创建操作结果前校验 FILE 类型、来源／修订归属、状态、质量与版本；真实 PostgreSQL 测试验证同一事务的来源、产物、质量和发布事实不会留下半成品。
- 安全：可信上下文的 `knowledge:write` 仍在读取前执行；领域结果与持久化事实不含正文、对象键、连接信息、密钥或外部异常。
- 复杂度：仅新增两个命名领域服务和两个不可变结果，没有引入连接器注册表、通用工作流或跨上下文事件。
- 性能：对象流读取保持在短数据库事务之外；提交路径只进行一次来源锁定、一次乐观更新和必要事实写入。
- 架构：依赖方向仍是 `interfaces -> application -> domain` 与 `infrastructure -> application/domain`；领域不依赖 Spring、MyBatis 或对象存储。
- 测试质量：先记录可复现的缺类编译红灯，再以同一 seam 转绿；领域、应用、真实 PostgreSQL、自动配置、HTTP 和全仓门禁均有回归证据。

## 结论

ADR-0031 的规约与 FILE 操作边界已在本提交中落实，自动门禁没有发现本轮新增的两类 DDD 违规。该结论只证明当前整改切片已验证；M3 尚未完成，仍需继续 `OBJECT_STORAGE` 等范围内工作并在全部 M3 证据齐备后进行人工评审。
