# WP-0014 模型发现、部署与能力验证供应链证据

## 范围

本证据只覆盖 ADR-0058 的当前租户供应链切片：已验证连接的不可变发现事实、当前修订未过期候选的安全分页读取、草稿部署、真实能力探测、固定 `PT24H` 验证有效期和发布操作，以及 ADR-0059 统一控制台中的独立部署任务。它不开放平台连接、自由 Endpoint、自由模型代码、凭据、发现原始响应、健康监控或 M7／M8 能力。

## 设计与边界

- `ProviderModelDiscovery` 继续作为不可变历史事实；历史列表只返回摘要，不能成为浏览器的候选缓存。
- 新增 `ProviderModelDiscoveryCandidateReader` 和 `ProviderModelDiscoveryCandidateReadService`，只在 token 派生的当前租户、已验证连接、当前连接修订、成功且未过期的事实内按服务端页码读取模型代码、类型和安全候选能力。
- 草稿创建、能力探测和发布继续由既有 `ModelDeploymentService` 守住连接修订、候选归属、实测能力覆盖与 `PT24H` 到期不变量；应用层不接收租户、Endpoint 或凭据。
- 浏览器路由固定为 `/models/deployments`，通过 `connectionId`、候选页码和部署页码恢复任务；候选表和部署表均为服务端分页，创建、探测和发布使用单一 Dialog／高风险确认，避免整页平铺和嵌套弹窗。

## 红绿记录

先新增 `ProviderModelDiscoveryCandidateReadServiceTest`，在生产候选读取端口和服务尚不存在时执行：

```bash
./mvnw -q -pl backend/contexts/model-runtime -am -Dtest=ProviderModelDiscoveryCandidateReadServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：编译失败，缺少 `ProviderModelDiscoveryCandidateReader` 与 `ProviderModelDiscoveryCandidateReadService`，证明测试先于实现。

实现后执行：

```bash
./mvnw -q -pl backend/contexts/model-runtime -am -Dtest=ProviderModelDiscoveryCandidateReadServiceTest,ProviderConnectionControllerTest,PostgresProviderModelDiscoveryStoreTest,ModelDeploymentControllerTest,ModelRuntimeAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
cd frontend && npm test -- --run apps/console-web/src/session/console-route.test.ts apps/console-web/src/api/control-plane-client.test.ts && npm run typecheck && npm run lint
```

结果：后端目标测试通过；前端 31 项测试、类型检查和 lint 通过。前端接口测试确认候选、草稿、探测和发布请求仅传递受控业务字段，不补写 `Authorization`。

## 真实基础设施与容器回归

```bash
./mvnw -q -pl backend/contexts/model-runtime -am test
python3 scripts/quality/backend_architecture_guard.py verify
docker compose --env-file deploy/.env \
  -f deploy/compose/compose.yml \
  -f deploy/compose/compose.real-model.yml \
  -f deploy/compose/compose.browser-acceptance.yml \
  up --detach --build --force-recreate control-plane query-runtime query-web console-web
docker compose --env-file deploy/.env \
  -f deploy/compose/compose.yml \
  -f deploy/compose/compose.real-model.yml \
  -f deploy/compose/compose.browser-acceptance.yml ps --format '{{.Service}} {{.Status}}'
curl --insecure --silent --show-error --output /dev/null --write-out 'console_https=%{http_code}\\n' https://127.0.0.1:13443/
curl --insecure --silent --show-error --output /dev/null --write-out 'candidate_unauth=%{http_code}\\n' https://127.0.0.1:13443/api/v1/model-providers/1/current-model-candidates
```

结果：模型运行时全模块测试和架构门禁通过；`console-web`、`control-plane`、`query-runtime`、`query-web` 均为 `healthy`。本地 HTTPS 入口返回 `200`，未认证候选读取返回 `401`。本地验证继续使用 `--insecure`，未安装 macOS System keychain CA，也没有读取、输出或提交 `.env` 中的秘密。

全模块回归首次暴露既有迁移 `V202608100007` 的算法约束仍为 `AES/GCM/NoPadding`，而实际安全组件持久化
`AES-256-GCM`。新增不可变迁移 `V202608100008__align_provider_credential_algorithm.sql` 替换该约束，随后执行：

```bash
./mvnw -q -pl backend/contexts/model-runtime -am -Dtest=PostgresEncryptedProviderConnectionCredentialStoreTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -q -pl backend/contexts/model-runtime -am test
```

结果：真实 PostgreSQL 已应用第 9 条模型运行时迁移，凭据密文写入测试和全模块回归均通过。该修复不改变
算法、密文格式或秘密边界，只使数据库约束与既有 `AesGcmSecretCipher` 契约一致。

## 失败关闭与质量维度

| 维度 | 证据 |
| --- | --- |
| 正确性 | 无 `model:read` 不调用读取端口；未验证、跨租户或不存在连接不能读取候选；到期后真实 PostgreSQL 候选总数为零。 |
| 架构 | 读取服务位于 application，候选端口位于 application 输出端口，PostgreSQL 只负责受限查询；Web 自动配置在缺失候选端口的最小上下文中保持可启动。 |
| 性能 | 候选 SQL 使用 `LIMIT/OFFSET` 与独立总数查询，不把全部发现明细装入应用层。 |
| 安全 | HTTP 只从可信请求上下文恢复租户；候选和部署投影不包含 Endpoint、API Key、密文、凭据引用或原始供应商错误。 |
| 可用性 | 统一控制台支持深链接恢复、服务端分页、草稿表单、探测确认、发布高风险确认和验证到期显示。 |

## 人工浏览器边界

当前执行环境没有可调用的浏览器自动化连接器，因此未伪造真实登录后浏览器证据。容器 HTTPS 可达性、HTTP 身份失败关闭、前端路由与 API 合同均已自动回归；实际租户模型管理员完成“连接 → 发现 → 选择候选 → 草稿 → 探测 → 发布”的浏览器角色任务仍作为 M6 人工验收项。

## 全仓门禁收口

```bash
make validate
```

结果：项目治理 9 项、架构门禁 20 项、全后端 Maven 回归、前端类型检查、lint、16 个测试文件共 72 项测试、生产构建、演示夹具、真实模型、恢复、容量和交付运维门禁均通过。

全仓回归同步校正三类过期测试事实：连接建立仅接受写入专用 `apiKey`，发布助手缺少冻结决定时稳定拒绝，以及模型部署只能从当前未过期发现候选建立。清理夹具按真实外键顺序删除验证事实、部署、发现明细、发现根和连接；未改变任何生产领域不变量或秘密边界。
