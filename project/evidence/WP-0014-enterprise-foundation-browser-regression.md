# WP-0014 企业产品基础真实回归

## 范围

本证据续证 ADR-0057、ADR-0058、ADR-0059 已实现切片在当前纯本地研发环境的真实闭环：平台管理员角色与导航投影、平台范围确定性模型供应链、租户模型配置读取，以及统一 SPA 中首份文本的索引建立和发布。M6 保持进行中；本证据不构成 M6 自动验收或人工评审，也不进入 M7。

验收使用已由本地研发基线确定的 `platform.root` 账号直接登录；为使现存本地数据库与该已确定口令重新一致，只更新了该本地验收账户的 PBKDF2 摘要和修订。没有创建临时凭据，未读取、输出或提交任何口令、会话、API Key、主密钥或密文。

## 红绿与自动化回归

模型配置列表是辅助读取，不能因加载失败重置首份发布主任务。先新增
`embedding-model-configuration-page.test.ts`，执行：

```text
(cd frontend && npm test -- --run apps/console-web/src/features/shared/embedding-model-configuration-page.test.ts)
```

初始结果为 `Cannot find module './embedding-model-configuration-page'`。随后以最小页面状态模型将失败收敛在选择器并显式区分已过期会话；发布流程不再把普通读取异常归类为全局网络失败。

摄取工作器真实索引首先返回 `EMBEDDING_RESULT_INVALID`。数据库事实显示平台目录、模型配置、版本和维度均一致；Compose 检查确认工作器没有注入本地模型凭据主密钥，解密短生命周期凭据时失败关闭。先新增 `IngestionWorkerApplicationTest.本地摄取工作器必须接收受控模型凭据开发密钥`，红灯为断言失败；再在工作器容器仅注入与控制面相同的开发密钥版本和版本化主密钥，不增加任何运行时接口或自由凭据路径。

绿色回归：

```text
./mvnw -q -pl backend/contexts/model-runtime -am \
  -Dtest=ModelConfigurationServiceTest,ModelConfigurationAccessServiceTest,ControlledModelCatalogServiceTest,PostgresModelConfigurationRepositoryTest,ControlledModelCatalogEntryTest,ControlledModelCatalogControllerTest,PostgresEncryptedPlatformProviderConnectionCredentialStoreTest,ModelRuntimeAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -q -pl backend/contexts/organization-access -am \
  -Dtest=TenantCustomRoleBindingRequestTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -q -pl backend/bootstrap/ingestion-bootstrap -am \
  -Dtest=IngestionWorkerApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test
(cd frontend && npm test -- --run apps/console-web/src/features/shared/embedding-model-configuration-page.test.ts apps/console-web/src/features/knowledge-publication/publication-workflow.test.ts apps/console-web/src/session/navigation-capability.test.ts packages/design-system/src/shell/ProductShell.test.ts)
(cd frontend && npm run typecheck && npm run lint)
docker compose --env-file deploy/.env -f deploy/compose/compose.yml config --quiet
```

全部通过；前端 4 个文件、18 项测试通过。模型运行时 Testcontainers PostgreSQL 执行至 `V202608140002`，证明新增迁移可从空数据库建立。

## 真实基础设施与 HTTP

```text
docker compose --env-file deploy/.env -f deploy/compose/compose.yml build ingestion-worker
docker compose --env-file deploy/.env -f deploy/compose/compose.yml up -d --no-deps --wait ingestion-worker
curl --fail --silent --show-error http://localhost:18080/actuator/health
curl --fail --silent --show-error http://localhost:18082/actuator/health
```

控制面与摄取工作器均返回 `UP`。工作器成功启动且不装配控制平面 Controller；本次重建没有删除现有 `query-web` 孤儿容器。真实 PostgreSQL 最终查询为 `ACTIVE|1` 和 `FAILED|1`：失败版本保留审计事实，重建创建并发布了唯一活动版本。

## 浏览器回归

使用统一控制台 `http://localhost:13000`，不使用 TLS 代理端口：

1. 以本地 `platform.root` 登录 `m6-local` 后，顶层导航为“成员与权限、数据、模型”，平台治理收纳为下拉子项；平台管理员可见平台租户、账户和模型供应链入口。
2. `/data/publication` 的模型配置选择器以服务端页显示“第 1 页，共 1 条”和“本地嵌入配置”；辅助读取不再清空发布主任务。
3. 创建“`M6 索引恢复验收文本`”并选择该配置。第一次失败记录保持 `EMBEDDING_RESULT_INVALID`，修复后点击“开始构建”得到“构建就绪”、4 个文本分块与允许发布。
4. 点击“发布检索索引”后终态显示“首份知识数据已可检索”，文本已进入“`M6 本地验收知识空间`”。

浏览器任务与健康端点共同证明当前供应链可从平台目录经租户配置到异步索引运行时；服务器授权仍为最终边界，导航只作能力投影。

## 代码图谱与复核

代码图谱追踪了 `IndexBuildWorker`、`ModelConfigurationRuntimeService`、`DeterministicEmbeddingModelFactory`、`KnowledgeDataJobAutoConfiguration` 和 `EmbeddingModelConfigurationPicker`。它确认索引错误码在工作器中是受控归类、模型工厂按冻结配置标识构造，且选择器还有三个非发布调用方；因此保留显式 `onSessionExpired` seam，而不把任意读取错误升级为全局失败。`ModelRuntimeWebAutoConfiguration` 的影响检查也促成 Web Controller 只在控制面启用，避免工作器错误装配 HTTP 入口。

## 结论

本地企业基础的账户／角色导航、平台模型供应链、服务器分页与首份发布真实回归均已续证。后续仍须按工作包完成 M6 自动验收汇总和人工评审；在二者完成前，`currentMilestone` 继续为 M6，M7 不激活。
