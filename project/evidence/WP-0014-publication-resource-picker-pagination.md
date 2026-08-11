# WP-0014 首份发布资源选择器分页与显式模型选择

## 范围与设计

本切片落实 ADR-0059 对 `/data/publication` 中两个普通资源选择集合的要求：知识空间和嵌入模型配置都以服务端分页读取，并把页码和当前选择写入统一控制台 URL。完整设计卡位于 `docs/architecture/foundation-m6-enterprise-governance-design.md` 的“ADR-0059 实施设计卡：首份发布选择器分页与显式嵌入模型”。

代码图谱探索 `EmbeddingModelConfigurationPicker PagedResourceNavigation KnowledgePublicationFlow publication workflow` 确认可复用的分页选择器与路由 seam；影响分析确认 `ConsoleRoute` 的调用范围仅为 `ConsoleApp`、路由解析／格式化和 `KnowledgePublicationFlow`。

## TDD

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| 红灯 | `cd frontend && npm exec vitest run apps/console-web/src/session/console-route.test.ts` | 新增深链接期望失败：旧 `/data/publication` 只返回任务名，缺少 `knowledgeSpacePage`、`embeddingModelConfigurationPage` 和选择标识。 |
| 绿灯 | 同一命令 | 16 项通过；路由解析和规范 URL 均保留知识空间页、选择标识、模型页和模型标识。 |

## 已实现行为

- `ConsoleRoute` 将首份发布的两类页码与选择标识作为可恢复路由状态；切换知识空间页时清除旧空间选择。
- `KnowledgePublicationFlow` 按 `knowledgeSpacePage` 请求服务端空间页，显示总数和前后页；空间不再在浏览器端全量读取或本地插入。
- 索引构建改为复用 `EmbeddingModelConfigurationPicker`：只接受当前页中活动、已配置凭据的 `EMBEDDING` 配置。URL 伪造或跨页模型标识在选择器未确认前保持禁用，后端仍在创建时重验。
- 索引状态轮询只跟踪刚创建的单个任务，不作为普通资源目录或分页例外的规避手段。

## 自动化验证

| 命令 | 结果 |
| --- | --- |
| `cd frontend && npm run typecheck` | 通过。 |
| `cd frontend && npm run lint` | 通过。 |
| `cd frontend && npm exec vitest run apps/console-web/src/session/console-route.test.ts apps/console-web/src/features/knowledge-publication/publication-workflow.test.ts` | 24 项通过。 |
| `cd frontend && npm exec vitest run apps/console-web/src/api/control-plane-client.test.ts` | 30 项通过，覆盖知识空间、模型配置和索引版本的 HTTP 客户端分页契约。 |
| `cd frontend && npm run test && npm run build` | 16 个测试文件、88 项测试和生产构建通过。 |

## 六维质量复核

| 维度 | 结论 | 依据 |
| --- | --- | --- |
| 正确性 | 通过 | 红绿路由测试证明深链接不再丢失两个页码和选择标识；当前页之外或 URL 伪造的模型标识不能启用构建。 |
| 复杂度 | 通过 | 复用 `PagedResourceNavigation` 和 `EmbeddingModelConfigurationPicker`，没有增加第二套分页协议或业务状态机。 |
| 冗余 | 通过 | 移除首份发布流程中“读取模型配置第一页并选择第一项”的重复选择路径。 |
| 性能 | 通过 | 空间与模型配置均只读取标准 `PageResult` 当前页；索引轮询只读取单个刚创建任务的状态。 |
| 架构 | 通过 | 代码图谱影响分析将改动限制在 `ConsoleRoute`、`ConsoleApp` 与首份发布流程；架构／前端产品门禁通过。 |
| 测试质量 | 通过 | 先观察到旧路由的可证伪红灯，再以同一测试绿灯；定向、全量前端和 HTTP 客户端测试均通过。 |

## 未完成的真实环境验证

- `make acceptance-index-search` 在现有控制面容器的登录阶段返回 HTTP `401`。容器创建时间早于 `ac1a3c93b` 的认证种子修复，不能代表当前源码。
- 重建当前 Compose 服务时，Docker Buildx 持续阻塞在基础镜像元数据读取，未产生新镜像；为避免保留重复构建进程已停止该验证构建。真实 PostgreSQL／MinIO／HTTP 验收必须在可完成构建的环境中重跑。
- 标准浏览器仍因本地证书返回 `net::ERR_CERT_AUTHORITY_INVALID`；按 ADR-0048 不安装 System keychain CA、不使用 HTTP、`--insecure` 或证书忽略。受信任 TLS 环境中的真实角色浏览器回归仍待执行。

因此本切片已实现并完成前端 TDD／自动化验证，但未标记为真实基础设施或浏览器验收通过；M6 保持进行中，M7 不得激活。
