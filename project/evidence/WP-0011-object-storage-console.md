# WP-0011 OBJECT_STORAGE 控制台闭环证据

## 范围

本记录绑定提交 `60dc61c63`，将既有平台签发范围选择的登记、冻结集合同步、显式产物发布和索引版本创建／发布接入知识管理员控制台。它不表示 WP-0011 或 M3 已完成，也不构成进入 M4 的人工评审结论。

## 已落实的不变量

- 控制台只提交 `knowledgeSpaceId`、来源名称、平台签发的 `rangeId` 和正整数 `selectionRevision`。它不新增范围枚举或读取接口，也不展示或提交端点、桶名、对象键、内部位置、正文、质量结论或凭据。
- 同步、发布与索引仅使用登记返回的来源、当前同步产物和乐观版本。同步入口只在 `READY` 且尚未产生当前产物，或 `FAILED(SYNCHRONIZATION)` 时开放；发布拒绝 `BLOCK` 质量；索引创建只接受已发布且仍为当前的同步产物。
- 索引创建只选择既有 `ACTIVE` 且已配置凭据的 `EMBEDDING` 配置，最多轮询 90 次至 `READY` 或 `FAILED`，仅 `READY` 索引可显式发布。会话过期会清除受保护状态并返回登录入口。
- 平台对象实际正文仍由部署配置中的内部映射按冻结 `SourceRevision` 读取，并严格核验媒体类型、长度和 SHA-256；控制台未改变此基础设施边界。

## 代码图谱与影响

- 探索 `GovernedObjectStorageDataSourceController`、`ConfiguredPlatformObjectRangeSelectionAdapter`、`ObjectStorageSynchronizationService` 和 `MinioPlatformObjectContentReaderAdapter`，确认 HTTP 契约已接受不透明范围标识及选择修订，范围解析和内部对象位置均留在部署控制适配器。
- 对 `ControlPlaneClient` 执行影响分析，受影响范围仅为前端客户端及数据任务工作区。新增懒加载工作区，未改动领域状态机、仓储、自动配置、HTTP 控制器或对象位置映射。

## 可复现验证

- 红测：`cd frontend && npm run test -- apps/console-web/src/api/control-plane-client.test.ts` 在对象存储客户端方法缺失时失败；实现后验证登记、同步、发布请求只包含平台签发范围选择、资源标识、乐观版本和令牌。
- 红测：`cd frontend && npm run test -- apps/console-web/src/features/governed-data/ObjectStorageDataWorkspace.test.ts` 在状态门禁缺失时无法加载；实现后验证同步、发布和索引仅使用当前产物。
- `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`：通过，10 个测试文件、43 项测试通过，控制台与查询站点生产构建通过。
- `make validate`：退出码 0；包含项目治理、19 项后端架构门禁、全后端反应堆、前端生产构建、演示、真实模型、恢复、容量和交付验证。

## 浏览器检查与未完成事项

- 控制台已提供“对象存储范围登记”任务及未认证失败关闭路径。本地演示凭据仍无法建立可写知识管理员会话，故本记录不把真实范围登记、同步、发布或索引浏览器操作标记为通过。
- M3 仍需取得可写知识管理员演示会话，完成 FILE、STRUCTURED_DATASET 与 OBJECT_STORAGE 的真实浏览器索引闭环；完成全部 M3 验收后必须经过人工评审，方可进入 M4。
