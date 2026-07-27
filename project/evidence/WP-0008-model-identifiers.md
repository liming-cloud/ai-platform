# WP-0008 模型目录雪花标识整改证据

## 结论

提交 `4fe3552ff4cef64f18563d2af2d343a418594636` 已将供应商连接和模型部署
业务标识从 UUID 改为正的 64 位长整型，并同步改造知识索引、助手版本、可信查询、
模型运行时缓存、供应商扩展、数据库迁移、HTTP 契约和前端测试数据。

本证据只覆盖模型目录标识及其跨上下文引用。助手、助手版本、查询运行等业务实体
仍有 UUID，事件和租约标识也仍按原语义保留。

## 领域与边界

- `ProviderConnectionId` 和 `ModelDeploymentId` 只接受正整数。
- 两个值对象的 `parse` 只接受十进制文本，拒绝旧 UUID 格式。
- 供应商连接与模型部署应用服务统一使用 `EntityIdGenerator`，不再直接创建 UUID。
- 模型目录 HTTP 请求和响应使用十进制字符串，避免浏览器数字精度损失。
- 跨上下文的已发布模型快照、对话引用和嵌入引用使用 `long`。
- 模型运行时对象缓存键同时包含租户、供应商标识与修订、部署标识与修订。
- 缓存淘汰接口和运行时解析接口不再泄漏 UUID。

## 持久化与扩展

- `provider_connection.id`、`model_deployment.id` 以及关联请求表外键均为 `BIGINT`。
- 知识索引和助手版本冻结的模型部署列均为 `BIGINT`。
- Mapper、XML 映射、行对象、JDBC 与 pgvector 参数统一使用长整型。
- 确定性、OpenAI 兼容、Ollama 和 Spring AI 公共适配测试统一使用长整型模型引用。
- 控制面通过 `platform-persistence` 提供的雪花生成器装配两个模型目录用例。

系统采用绿地重建，不提供 UUID 数据迁移、兼容列或双写。

## 红绿与回归

`ModelCatalogIdentifierTest` 先定义十进制解析、正数约束和旧 UUID 拒绝行为；旧实现
无法编译。完成迁移后执行从零编译、真实 PostgreSQL、pgvector 和全量回归。

| 命令 | 结果 |
| --- | --- |
| `./mvnw -q clean test-compile` | 全工程主代码与测试代码从零编译通过 |
| `make backend-test` | 223 项通过，0 失败、0 错误、0 跳过 |
| `make frontend-test` | 28 项通过，类型检查、代码检查和双应用构建通过 |
| `make demo-fixtures-verify` | 9 项通过，2 个场景和 8 个检索用例有效 |
| `make architecture-guard` | SQL 注解文件 0；UUID 主代码文件从 83 降至 42 |
| `make project-verify` | 通过，当前前沿为 WP-0008 |
| `git diff --check` | 通过 |

前端构建仍有查询站点单分块大于 500 千字节的既有警告，尚未达到最终前端性能门禁。

## 下一步

迁移助手、助手版本和查询运行等剩余业务实体标识。所有业务实体完成后，重新分类并
限制允许保留的事件、幂等和租约 UUID，随后进入 Sa-Token 适配边界、包结构与纯启动
运行模块整改。
