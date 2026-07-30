# WP-0011 历史同步产物升级与对象存储装配证据

## 范围

本记录绑定提交 `efe6f5cd3` 与 `7454d58c3`。它修复 M3 尚未获人工评审前，在既有本地 PostgreSQL 卷升级时暴露的同步产物迁移和条件装配缺陷；不改变来源生命周期、对象范围契约或登录态边界，也不表示 WP-0011 或 M3 已完成。

## 已落实的不变量

- 新的 `SyncArtifact` 内容引用仍必须一次性冻结版本、对象标识、摘要、字节数和媒体类型；数据库约束拒绝半成品引用。
- 迁移前已存在、且没有可验证冻结正文事实的同步产物保留为全空内容引用。它们不被补写推测值，也不会被 `IndexVersionMapper.findPublishedSources` 选为索引候选，防止历史事实被伪造为 M3 可索引内容。
- `GovernedObjectStorageDataSourceController` 只有在登记、同步和发布三个应用服务都已装配时才注册。没有平台正文读取映射的部署不会公开一个构造器依赖缺失的对象存储入口。

## 代码图谱与影响

- 探索 `IndexVersionMapper.findPublishedSources`，确认它是 FILE、OBJECT_STORAGE 和 STRUCTURED_DATASET 共用的已发布产物读取投影；因此须在该读取点排除没有冻结内容版本的历史产物，而不是为旧数据补写内容事实。
- 探索 `GovernedObjectStorageDataSourceController` 与 `KnowledgeDataCoreAutoConfiguration.objectStorageSynchronizationService`，确认后者依赖 `PlatformObjectContentReaderPort`，而控制器原条件只检查登记服务。影响范围收敛为控制器条件与自动配置回归，未修改对象存储领域服务或部署映射。

## 可复现验证

- 红测后绿测：`SyncArtifactContentReferenceMigrationTest` 使用真实 PostgreSQL 与 Flyway 先迁移至 `202607300022` 并写入历史来源、产物和发布事实，再迁移至当前版本；验证全空历史引用可升级、候选读取为零，且数据库拒绝仅补写一个内容字段。
- `./mvnw -q -pl backend/contexts/knowledge-data -am -Dtest=SyncArtifactContentReferenceMigrationTest,PostgresIndexVersionPersistenceTest,PostgresGovernedDataSourceRepositoryTest,KnowledgeDataAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过；覆盖历史迁移、索引读取、真实 PostgreSQL 仓储与自动配置。
- 自动配置红测后绿测：`KnowledgeDataAutoConfigurationTest` 在未提供 `PlatformObjectContentReaderPort` 时验证 Spring 上下文可启动且不注册对象存储控制器。
- 使用保留历史卷的本地演示部署启动控制面后，Flyway 成功执行至 `202607300024`，`/actuator/health` 返回 `UP`；未创建或修改业务数据。
- 控制台无凭据回归：对象存储任务入口可达，选择“对象存储范围登记”后展示令牌会话登录边界；未提交凭据、未执行登记、同步、发布或索引写操作。

## 未完成事项

- 默认演示部署未配置平台对象正文读取映射，因此该部署按条件装配不公开对象存储 HTTP 入口。这是失败关闭，不是对象存储真实操作验收。
- FILE、STRUCTURED_DATASET 与 OBJECT_STORAGE 仍需要具有可写知识管理员会话和已配置平台范围映射的真实浏览器索引闭环。M3 完成后还必须通过人工评审，方可进入 M4。
