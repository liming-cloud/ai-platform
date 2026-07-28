# WP-0010：受控模型查询运行时

## 范围

提交 `e6502430089de25a5d2e4ef38e609e8a1d0e5805` 将受控模型配置接入查询执行与候选检索。该提交不发起外部模型调用，不读取、输出或持久化任何 API Key。

## 设计与安全边界

- 已发布助手快照显式区分 M1 历史部署与受控 Chat、Embedding 配置，二者不能混用。
- 受控查询在配额领取成功后重新查询同一配置、配置修订、目录条目修订和能力快照；任一不一致、未授权或依赖缺失均失败关闭。
- 受控路径只调用短生命周期的 `GovernedEmbeddingRuntime` 与 `GovernedChatRuntime`，不会回退到历史 `ModelRuntimeResolver`。
- pgvector 检索以配置、目录、修订和向量维度为联合条件；历史部署向量与受控配置向量相互隔离。
- 自动配置以可选受控依赖保留 M1 查询进程启动能力；一旦载入受控助手，则缺少依赖仍以不可用失败关闭。

## 红绿与定向验证

| 验证 | 命令 | 结果 |
| --- | --- | --- |
| 受控助手查询 | `./mvnw -q -pl backend/contexts/trusted-query -am test -Dtest=TrustedQueryAutoConfigurationTest,QueryRunServiceTest,CandidateSearchServiceTest -Dsurefire.failIfNoSpecifiedTests=false` | 通过。验证配额领取先于受控调用、双配置重验、受控索引检索和 M1 自动配置兼容。 |
| pgvector 合同 | `./mvnw -q -pl backend/extensions/vector-stores/vector-pgvector -am test -Dtest=PgVectorIndexStoreTest -Dsurefire.failIfNoSpecifiedTests=false` | 通过。真实 Testcontainers pgvector 验证受控配置向量写入和检索。 |
| 全量构建与测试 | `make validate` | 通过。治理、架构门禁和 `./mvnw -q clean test` 通过；测试日志中的预期异常与 Spring/MyBatis 警告由现有反例测试产生。 |
| 项目控制面 | `make project-verify` | 通过。当前前沿保持 `WP-0010`。 |

## 定向代码评审

按工程助手 `code-review` 的固定 diff 审查了 `QueryRunService`、`CandidateSearchService`、`IndexCandidateQuery`、`PgVectorIndexStore` 与自动配置。适用反例包括受控路径回退、旧配置或目录修订、向量维度错配、M1 运行时缺少新 Bean 和密钥泄漏。

- 未发现密钥写入、日志输出、事件扇出或明文持久化的新增路径。
- 查询和候选检索测试分别使旧部署运行时抛出断言，从执行证据证明受控分支不回退。
- M1 历史构造器和分支仍被保留，且自动配置测试覆盖缺少受控 Bean 的启动场景。

## 六维结论

| 维度 | 结论 |
| --- | --- |
| 正确性 | 已通过冻结配置重验、索引绑定和 pgvector 精确检索验证。 |
| 安全性 | 失败关闭，无新增秘密暴露路径，受控与历史向量隔离。 |
| 复杂度 | 仅在既有查询边界增加受控分支，未引入厂商专属领域接口。 |
| 性能 | 沿用一次查询一次嵌入和既有候选上限；配置复核为常数次安全查询。 |
| 架构 | 依赖经既有跨上下文门面和自动配置注入，未突破上下文依赖方向。 |
| 测试质量 | 单元、自动配置和真实 pgvector 合同均已覆盖；真实供应商调用留待受控环境验证。 |

## 未完成项

M2 尚未完成。真实火山方舟 Chat 与 Embedding 调用、预算与脱敏审计证据、以及 M2 人工评审仍未登记；在人工评审通过前不得进入 M3。
