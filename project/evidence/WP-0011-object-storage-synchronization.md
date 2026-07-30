# WP-0011 OBJECT_STORAGE 私有正文同步与发布证据

## 范围

本记录绑定提交 `5094c7323` 与 `932b84c65`，落实 ADR-0034 的平台对象正文读取、同步完成、通过质量发布和 HTTP 委派边界。它不表示 WP-0011 或 M3 已完成，也不构成进入 M4 的人工评审结论。

## 已落实的不变量

- `PlatformObjectContentReaderPort` 只接收冻结 `SourceRevision` 及其成员对象；MinIO 内部对象键只保存于对象存储扩展的部署映射，绝不进入领域、PostgreSQL、HTTP、事件、日志或错误响应。
- MinIO 适配器在读取前核验冻结成员、映射唯一性、严格媒体类型和长度；流读尽时核验 SHA-256。映射缺失、成员不匹配、媒体类型、长度、摘要和读取故障均关闭失败。
- 应用服务严格按冻结顺序合并多个 UTF-8 正文，记录之间固定使用 `\n\n`；任一失败使来源进入 `FAILED(SYNCHRONIZATION)`，已写但未提交的私有正文会删除。
- `ObjectStorageSynchronization` 负责 `OBJECT_STORAGE` 的同步状态、当前修订、产物记录数与 `object-storage-text-v1/PASS` 质量事实；其内聚的 `ArtifactPublishing` 只允许当前修订、归属一致且 `PASS` 的产物发布。
- 发布使用通用不可变 `DataSourceSyncArtifactPublication` 结果原子提交；应用层只执行令牌上下文授权、范围加载、事务外 I/O 和提交。HTTP 同步与发布入口只接收资源标识和乐观版本。

## 代码图谱与影响

- 探索 `PlatformObjectContentReaderPort`、`MinioKnowledgeDataAutoConfiguration`、`KnowledgeDataMinioProperties`、`ObjectStorageSynchronizationService`，确认真实内部位置只能位于 MinIO 扩展，核心层仅依赖端口。
- 对 `PlatformObjectContentReaderPort` 的影响分析确认调用面收敛在失败关闭适配器、核心自动配置与对象同步服务。
- 探索并影响分析 `DataSourceSyncArtifactPublication`、结构化数据集发布用例、对象存储控制器和核心自动配置；据此将对象发布内聚到对象同步领域服务，避免增加超出包规模门禁的重复顶级结果类型。

## 可复现验证

- `./mvnw -q -pl backend/extensions/object-storage/object-storage-minio -am -Dtest=MinioTextContentObjectStorageAdapterTest,MinioAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过；真实 MinIO 覆盖映射读取与流式摘要反例，自动配置覆盖部署映射绑定。
- `./mvnw -q -pl backend/contexts/knowledge-data -am -Dtest=ObjectStorageSynchronizationServiceTest,ObjectStorageSyncArtifactPublishingTest,ObjectStorageSyncArtifactPublicationServiceTest,GovernedObjectStorageDataSourceControllerTest,KnowledgeDataAutoConfigurationTest,PostgresGovernedDataSourceRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过；覆盖固定分隔符、权限、PASS 发布、HTTP 委派、自动配置与真实 PostgreSQL 原子提交。
- `python3 scripts/quality/backend_architecture_guard.py verify`：通过；不使用债务基线或 allowlist，领域 service 包规模回到门禁上限内。
- `make validate`：通过，包含 `make project-verify`、19 项架构门禁测试、全后端反应堆测试、前端 39 项测试与生产构建、演示数据、真实模型验收、恢复、容量和交付脚本测试。

## 未完成事项

- 本切片未提供知识管理员浏览器任务闭环；M3 仍需完成所有剩余数据源类型的索引和浏览器验收后，才能提交人工 M3 评审。
- ADR-0033 的登录状态解析器已由既有 `resolveArgument` 和安全回归落实；本切片未新增任何接口登录状态参数或兼容回退。
