# WP-0010：受控真实模型验收装配

## 范围

提交 `0cb1723508bb8a3d1f5fa993156e2a76a25f0923` 为 M2 增加独立的真实模型验收覆盖层。默认 M1 演示 compose 不变，覆盖层只在显式组合时向控制面和查询进程传入本地研发主密钥、受控出站端点和调用超时。

## 关闭的装配缺口

- `query-runtime` 启用模型目录治理，才能装配模型配置查询和受控 Chat、Embedding 运行时。
- 查询进程与控制面共同使用受控端点白名单与超时配置；受控配置凭据仍从数据库密文按调用短生命周期解密。
- 覆盖层固定 `M2_V1` 主密钥版本，只要求本地未提交的同版本主密钥。API Key 不进入容器环境变量、compose 文件或文档。

## 验证

| 命令 | 结果 |
| --- | --- |
| `FOUNDATION_SECRET_ENCRYPTION_KEY_M2_V1=<测试主密钥> MODEL_ALLOWED_ENDPOINTS=https://ark.cn-beijing.volces.com/api/v3 docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml -f deploy/compose/compose.real-model.yml config --quiet` | 通过。仅使用固定测试主密钥验证 compose 合并与变量约束，不连接外部模型。 |
| `./mvnw -q -pl backend/bootstrap/query-bootstrap -am test -Dtest=QueryRuntimeApplicationTest -Dsurefire.failIfNoSpecifiedTests=false` | 通过。查询启动基线保持可用。 |
| `make project-verify` | 通过。当前前沿仍为 `WP-0010`。 |

## 当前前沿

真实调用尚未开始。工作区未设置 M2 本地研发主密钥，因此没有把用户提供的 API Key 复制到命令、环境文件或日志。

下一次受控验收需要人工在本地受控终端提供 `FOUNDATION_SECRET_ENCRYPTION_KEY_M2_V1`，并通过平台创建接口将 API Key 写入数据库密文。随后执行最小 Chat 与 Embedding 调用，记录脱敏错误码、耗时与用量摘要；M2 自动证据完成后仍必须登记人工评审通过，才能进入 M3。
