# WP-0011 STRUCTURED_DATASET 同步与发布证据

## 范围

本记录绑定提交 `36a185124`，落实 ADR-0031 与 ADR-0032 的私有 CSV 登记即绪、冻结修订同步、质量结论及显式发布边界。它不表示 WP-0011 或 M3 已完成，也不构成进入 M4 的人工评审结论。

## 已落实的不变量

- `GovernedDataSource.createReadyStructuredDataset` 是只适用于平台已保存私有 CSV 的聚合工厂；该类型没有外部连接需要探测，CSV 与冻结映射原子登记成功即为 `READY`，可使用当前乐观版本开始同步。FILE 与 OBJECT_STORAGE 仍保持 `DRAFT -> PROBING -> READY`。
- `StructuredDatasetCsvSynchronization` 继续独占当前修订、行过滤、`SyncArtifact`、`QualityReport` 及同步完成状态的不变量；`StructuredDatasetSyncArtifactPublishing` 继续独占产物归属、质量与发布状态的不变量。
- 浏览器客户端的同步、发布请求仅提交知识空间、数据源、产物标识和乐观版本；不提交 CSV 正文、对象位置、质量结论、外部端点或凭据。会话失效时，工作区清除来源和发布状态并返回登录入口。
- 控制台只在来源 `READY` 或可恢复的 `FAILED(SYNCHRONIZATION)` 时显示同步动作；仅对 `PASS`／`WARN` 同步产物显示发布动作，已发布产物可再次按冻结修订重新同步。

## 代码图谱与影响

- 探索 `GovernedStructuredDatasetRegistrationService`、`GovernedStructuredDatasetController`、`StructuredDatasetCsvSynchronization` 和 `GovernedDataSource`，发现登记结果固定为 `DRAFT`，但同步仅接受 `READY`，且不存在结构化数据集探测入口。
- 对 `GovernedDataSource.create` 执行影响分析，确认普通 FILE／OBJECT_STORAGE 的探测语义不可改动；因此新增类型专属聚合工厂，而非放宽通用来源状态机。
- 探索 `StructuredDatasetRegistrationWorkspace` 与 `ControlPlaneClient`，确认后端同步／发布 HTTP 端点已存在，缺口仅为客户端契约和受保护工作区操作。

## 可复现验证

- 红测：`./mvnw -q -pl backend/contexts/knowledge-data -am -Dtest=GovernedDataSourceTest,GovernedStructuredDatasetRegistrationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` 在工厂缺失时因找不到 `createReadyStructuredDataset` 编译失败；实现后同一命令通过。
- 红测：`cd frontend && npm run test -- apps/console-web/src/api/control-plane-client.test.ts` 在客户端方法缺失时失败；实现后 14 项通过。
- `./mvnw -q -pl backend/contexts/knowledge-data -am -Dtest=GovernedDataSourceTest,GovernedStructuredDatasetRegistrationServiceTest,StructuredDatasetCsvSynchronizationTest,StructuredDatasetCsvSynchronizationServiceTest,StructuredDatasetSyncArtifactPublicationServiceTest,GovernedStructuredDatasetControllerTest,PostgresGovernedDataSourceRepositoryTest,KnowledgeDataAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过；覆盖领域反例、权限和应用编排、控制器委派、自动配置与真实 PostgreSQL 原子提交。
- `cd frontend && npm run typecheck && npm run lint && npm run test -- apps/console-web/src/api/control-plane-client.test.ts`：通过；客户端请求只含资源标识和乐观版本。
- `make validate`：退出码 0；包含治理与架构门禁、全后端反应堆、前端 40 项测试与生产构建、演示、真实模型验收、恢复、容量和交付验证。

## 浏览器检查与未完成事项

- 使用本地 Vite 工作区实际进入“结构化数据集登记”页面，确认未认证状态只显示登录表单；本地控制平面返回无效演示凭据时，页面仅显示统一错误和追踪标识，不进入受保护操作。
- 当前本地演示账号无法建立可写会话，故本记录不把真实登记、同步和发布浏览器操作标记为通过。M3 仍需为结构化数据集和 OBJECT_STORAGE 提供可认证的知识管理员浏览器验收，并完成各来源类型的索引闭环后，才能申请人工 M3 评审。
