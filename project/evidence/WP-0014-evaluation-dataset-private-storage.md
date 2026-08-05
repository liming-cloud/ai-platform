# WP-0014：私有评测数据集真实存储与装配

## 结果

evaluation-operations 现在拥有独立的 `evaluation_operations.evaluation_dataset` 元数据表、
`PostgresEvaluationDatasetRegistrationStore` 与显式开启的持久化自动配置。表只保存租户、数据集标识、
私有对象引用、SHA-256 摘要、条目计数、分类、保留策略、状态、修订和创建时间；不含题目、期望证据、
答案、回答、候选或正文列。

`MinioEvaluationDatasetPrivateContentStore` 将正文写入 evaluation-operations 独占的私有对象路径，流式
校验 SHA-256；摘要不匹配时删除本次对象并拒绝登记。对象存储、清单存储和标识生成器同时存在时，
自动配置才注册 `EvaluationDatasetRegistrationService`；用户提供私有内容端口时 MinIO 自动配置完全退让。

ADR-0051 没有定义数据集的传输格式，故对象键和 MIME 使用格式中立的 `.content` 与
`application/octet-stream`。这不是已经完成的条目语义校验，也不授权 HTTP、正文读取或浏览器入口。

## 红绿与回归

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| PostgreSQL 红灯 | `./mvnw -q -pl backend/contexts/evaluation-operations -am test -Dtest=PostgresEvaluationDatasetRegistrationStoreTest -Dsurefire.failIfNoSpecifiedTests=false` | 因 `EvaluationDatasetMapper` 与 PostgreSQL 存储实现尚不存在而 `testCompile` 失败。 |
| PostgreSQL 绿灯 | 同一命令 | 真实 PostgreSQL 执行迁移；两个租户可用相同数据集标识分别提交清单，表结构断言不存在正文、问题或答案列。 |
| MinIO 红灯 | `./mvnw -q -pl backend/contexts/evaluation-operations -am test -Dtest=MinioEvaluationDatasetPrivateContentStoreTest -Dsurefire.failIfNoSpecifiedTests=false` | 因私有 MinIO 存储实现尚不存在而 `testCompile` 失败。 |
| MinIO 绿灯 | 同一命令 | 真实私有 MinIO 验证内容寻址、匿名 `403`、摘要不匹配删除与失败关闭。 |
| 自动配置红灯／绿灯 | `./mvnw -q -pl backend/contexts/evaluation-operations -am test -Dtest=EvaluationOperationsAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false` | 初始自定义端口仍错误创建 MinIO 客户端而失败；增加退让条件后通过。 |
| 模块回归 | `./mvnw -q -pl backend/contexts/evaluation-operations -am test` | 通过，日志含既有 Spring、Testcontainers 与预期失败夹具警告。 |
| DDD 门禁 | `python3 -m unittest scripts.quality.test_backend_architecture_guard`；`python3 scripts/quality/backend_architecture_guard.py verify` | 19 项通过；架构债务递减门禁通过。 |

## 未完成

私有内容的条目编码、语法校验和条目计数来源尚未决策；在该 ADR 接受前不得开放上传 HTTP、把调用方声明的
条目数当作可信评测事实，或启动评测运行。评测运行／决定持久化、最小事件、租户受限 HTTP、浏览器任务和
`AssistantService.publishVersion` 的授权接入仍未实施。
