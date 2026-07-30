# WP-0011 STRUCTURED_DATASET 索引控制台证据

## 范围

本记录绑定提交 `c509413ad`，将已完成同步并显式发布的 `STRUCTURED_DATASET` 接入既有索引版本创建、构建轮询与发布控制台操作。它不表示 WP-0011 或 M3 已完成，也不构成进入 M4 的人工评审结论。

## 已落实的不变量

- 控制台只在来源为 `READY`，且本会话保存的发布产物标识与来源当前 `syncArtifactId` 完全相等时，才显示“创建索引版本”。来源状态改变、重新同步或发布标识缺失时均关闭该入口，避免以陈旧产物触发索引。
- 创建索引前仅从既有受控模型配置读取 `ACTIVE` 且已配置凭据的 `EMBEDDING` 条目；客户端请求只提交知识空间、模型配置和索引版本资源标识，不提交 CSV 正文、对象位置、字段映射、质量结论或登录状态。
- 索引构建调用既有创建接口后最多轮询 90 次，终态仅为 `READY` 或 `FAILED`；只有 `READY` 版本可显式发布。模型运行时缺失、会话过期和服务端失败保持既有可恢复错误语义，过期会话会清除受保护状态。
- `StructuredDatasetCsvSynchronization` 的多记录规范化文本固定按记录顺序以 `\n\n` 连接。该分隔符是当前领域同步契约，不由控制台参数或 CSV 输入覆盖。

## 代码图谱与影响

- 探索 `StructuredDatasetRegistrationWorkspace`、`ControlPlaneClient`、`PostgresIndexVersionRepository.createInTransaction` 与 `IndexVersionMapper.findPublishedSources`，确认索引来源选择针对通用已发布同步产物，而非仅限 FILE；STRUCTURED_DATASET 已发布产物可沿用同一索引构建链路。
- 对 `GovernedDataSource.create` 的既有影响分析已确认普通 FILE／OBJECT_STORAGE 生命周期不能因结构化数据集登记即绪而被放宽。本切片未改动领域、持久化或 HTTP 契约，仅在受保护控制台编排既有索引能力。

## 可复现验证

- 红测：`cd frontend && npm run test -- apps/console-web/src/features/governed-data/StructuredDatasetRegistrationWorkspace.test.ts` 在 `canBuildStructuredDatasetIndex` 缺失时失败；实现后验证当前同步产物、缺失发布标识、陈旧发布标识和非 `READY` 来源四种情形。
- `cd frontend && npm run typecheck && npm run lint && npm run test -- apps/console-web/src/features/governed-data/StructuredDatasetRegistrationWorkspace.test.ts apps/console-web/src/api/control-plane-client.test.ts`：通过，15 项测试通过。
- `make validate`：退出码 0；包含前后端生产构建、全反应堆测试、架构与治理门禁、演示、真实模型、恢复、容量和交付验证。前端全量为 9 个测试文件、41 项测试通过。

## 浏览器检查与未完成事项

- 本地浏览器此前已确认未认证状态失败关闭；当前本地演示凭据无法建立可写知识管理员会话，因此本记录不把真实结构化数据集索引创建或发布浏览器操作标记为通过。
- OBJECT_STORAGE 的安全范围读取契约仍缺少已接受 ADR，不能猜测或硬编码范围标识与选择修订。WP-0011 仍需补齐该 ADR 后的受控读取，再完成各来源类型的真实知识管理员浏览器索引闭环，方可申请 M3 人工评审。
