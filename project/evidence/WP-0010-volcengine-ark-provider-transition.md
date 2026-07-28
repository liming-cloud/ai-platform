# WP-0010：火山方舟真实模型验证切换

## 范围

依据 ADR-0024，M2 的真实模型验证目标切换为火山方舟。现有
`OPENAI_COMPATIBLE` 运行时适配器保持不变；供应商端点、推理接入点和能力值仍由平台受控目录冻结。

方舟官方资料确认 OpenAI 兼容基础地址为
`https://ark.cn-beijing.volces.com/api/v3`，通过方舟 API Key 鉴权，实际模型调用使用账户已授权的推理接入点。来源：[火山方舟 Responses API](https://www.volcengine.com/docs/82379/1958524)。

## 已完成的安全与实现边界

- `huoshan.txt` 仅验证为单行常规文件，已将权限从 `0644` 收紧到 `0600`；未读取、打印、复制或提交其内容。
- 验收脚本移除所有供应商专属默认模型名。Chat、Embedding 推理接入点、上下文窗口、输出上限和嵌入维度缺失时，在任何 HTTP 请求前失败关闭。
- 受控目录仍以 `OPENAI_COMPATIBLE`、固定 HTTPS 端点、模型类型和能力快照创建条目；用户不能在产品接口中自定义厂家或任意端点。
- 仓库内原供应商的端点、模型编码和名称均已移除，包括研发资料与测试夹具。

## 可复现验证

| 命令 | 结果 |
| --- | --- |
| `bash -n scripts/development/acceptance-real-model.sh` | 通过。 |
| `python3 -m unittest scripts.quality.test_real_model_acceptance` | 通过，5 项。覆盖完整 Mock 闭环、目录和配置复用、无密钥文件、过宽文件权限、缺少方舟模型能力参数均在调用前失败关闭。 |
| `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -q -pl backend/contexts/model-runtime -am test -Dtest=ControlledModelCatalogEntryTest,ControlledModelCatalogServiceTest,ModelConfigurationServiceTest,ControlledModelCatalogControllerTest,ModelConfigurationSchemaMigrationTest,PostgresEncryptedModelCredentialStoreTest,PostgresModelConfigurationRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false` | 通过。真实 PostgreSQL 迁移与受控目录、模型配置和密文持久化回归通过。 |
| `FOUNDATION_SECRET_ENCRYPTION_KEY_M2_V1=<固定测试值> MODEL_ALLOWED_ENDPOINTS=https://ark.cn-beijing.volces.com/api/v3 docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml -f deploy/compose/compose.real-model.yml config --quiet` | 通过。仅校验覆盖层合并和变量约束，不连接外部模型。 |
| `make project-verify && make validate` | 通过。项目治理、架构门禁、后端和前端全量验证通过。 |

## 未完成项与阻断条件

真实调用尚未执行，不能报告为完成。需要在方舟控制台创建并授权以下非秘密配置：一个 Chat 推理接入点 ID、一个 Embedding 推理接入点 ID，以及二者经平台确认的上下文窗口、输出上限和嵌入维度。得到这些值后，才可启动受控 compose 覆盖层，使用 `huoshan.txt` 写入数据库密文并运行一次最小真实 Chat、Embedding 和带引用查询验收。

在真实调用、全部 M2 自动化验证和人工评审均完成前，不得进入 M3。
