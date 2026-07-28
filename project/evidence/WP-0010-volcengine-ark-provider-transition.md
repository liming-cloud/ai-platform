# WP-0010：火山方舟真实模型验证切换

实现基线提交：`bbdbeba18`（`feat: 切换M2火山方舟受控验收`）；本工作包的完成提交待 M2 验证收口后登记。

## 范围

依据 ADR-0024、ADR-0025 与 ADR-0026，M2 使用火山方舟标准推理基础地址
`https://ark.cn-beijing.volces.com/api/v3`。受控目录冻结
`doubao-seed-2-1-turbo-260628` 与 `doubao-embedding-vision-251215` 的端点、模型类型和能力快照。

## 已完成的安全与实现边界

- `huoshan.txt` 仅验证为单行常规文件，已将权限从 `0644` 收紧到 `0600`；未读取、打印、复制或提交其内容。
- 最小真实探针确认 `doubao-seed-2-1-turbo-260628` 在 `/chat/completions` 返回可用内容；`doubao-embedding-vision-251215` 在 `/embeddings/multimodal` 返回 2048 维向量。探针不保存密钥、输入、输出或向量。
- 多模态嵌入协议一次返回一个组合向量，新增方舟受控适配器，逐条调用文本片段、保持输入顺序并校验冻结维度；不向领域或公共契约泄漏厂商协议。
- 验收脚本使用平台批准的标准推理模型默认值，并在写入目录前验证能力参数。带说明前缀的密钥文件只接受唯一的长格式密钥值，避免把说明文字作为认证头提交。
- 全链路预检发现 `doubao-embedding-vision` 的 2048 维输出超过运行时旧的 2000 维限制；平台当前使用 pgvector 0.8.5 的无固定维度 `vector` 列和精确余弦检索，已按 ADR-0025 将运行时可存储上限修正为 16000 维。近似索引未启用，未来如需启用必须单独评审其 2000 维索引上限。
- 受控目录仍以 `OPENAI_COMPATIBLE`、固定 HTTPS 端点、模型类型和能力快照创建条目；用户不能在产品接口中自定义厂家或任意端点。
- 仓库内原供应商的端点、模型编码和名称均已移除，包括研发资料与测试夹具。

## 可复现验证

| 命令 | 结果 |
| --- | --- |
| `bash -n scripts/development/acceptance-real-model.sh` | 通过。 |
| `python3 -m unittest scripts.quality.test_real_model_acceptance` | 通过，5 项。覆盖完整 Mock 闭环、目录和配置复用、无密钥文件、过宽文件权限、缺少方舟模型能力参数均在调用前失败关闭。 |
| `./mvnw -f backend/pom.xml -pl :provider-openai-compatible,:provider-spring-ai-common -am -Dtest=ArkMultimodalEmbeddingModelTest,OpenAiCompatibleModelFactoryTest,SpringAiModelAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test` | 通过，8 项。覆盖多模态嵌入逐条请求与顺序、受控调用期密钥、OpenAI 专用选项和通用适配器。 |
| `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -q -pl backend/contexts/model-runtime -am test -Dtest=ControlledModelCatalogEntryTest,ControlledModelCatalogServiceTest,ModelConfigurationServiceTest,ControlledModelCatalogControllerTest,ModelConfigurationSchemaMigrationTest,PostgresEncryptedModelCredentialStoreTest,PostgresModelConfigurationRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false` | 通过。真实 PostgreSQL 迁移与受控目录、模型配置和密文持久化回归通过。 |
| `FOUNDATION_M2_API_KEY_FILE=… FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT=http://127.0.0.1:18080/api/v1 FOUNDATION_ACCEPTANCE_QUERY_API_ROOT=http://127.0.0.1:18081/api/v1 scripts/development/acceptance-real-model.sh` | 通过。合成知识空间、2048 维真实索引、受控助手发布与带引用查询均完成；数据库最新查询为 `ANSWERED`，引用数为 1。输出和证据未记录密钥、正文、回答或向量。 |
| `make project-verify` | 通过。9 项治理单元测试与 10 个工作包控制面校验通过，当前前沿保持 `WP-0010`。 |
| `make validate` | 通过。治理、架构门禁与全量 Maven 测试通过；测试中的预期异常和 Testcontainers 运行日志未影响结果。 |

## 未完成项与阻断条件

自动化、真实模型和全量门禁已完成。M2 仍须登记人工评审通过，才可进入 M3。
