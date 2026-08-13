# WP-0014：平台模型部署、当前候选与控制台闭环

## 范围与结论

本切片实现 ADR-0066 的平台部署闭环，不改造目录快照或租户模型配置。平台模型部署是独立于租户
`ModelDeployment` 的聚合，始终为 `PLATFORM` 范围且不带 `TenantId`。它只可从平台连接当前修订的最新成功、未过期发现候选创建；草稿经真实能力探测成为 `VERIFIED`，验证期内才能发布为 `PUBLISHED`，停用后不可在同一修订恢复。

新增的候选分页与部署 API 只返回模型代码、类型、能力和部署安全摘要。Endpoint、凭据引用、密文和厂商原始响应均不进入浏览器。平台部署页与连接页分离，并把连接标识、连接修订、候选页码和部署页码写入可恢复 URL；创建、探测、发布、停用全部经过明确确认 Dialog 和写后服务端重读。

## TDD 与回归

红灯一：先在真实 PostgreSQL `PostgresPlatformProviderModelDiscoveryStoreTest` 调用尚不存在的
`findCurrentCandidates`，Maven 测试编译错误为“找不到符号”。实现分页读取端口、最新发现查询和安全候选 DTO 后转绿。

红灯二：同一连接修订连续写入两次成功发现时，候选分页错误返回两条历史候选；测试断言
`expected: <1> but was: <2>`。查询改为先确定 `discovered_at`／标识倒序的最新成功、未过期发现，再对其候选分页和计数，回归转绿。

红灯三：平台部署停用控制器测试在命令、服务和 HTTP 请求尚不存在时编译失败；补齐独立停用命令与路由后转绿。

定向后端回归：

```text
./mvnw -pl backend/contexts/model-runtime -am \
  -Dtest=PlatformModelDeploymentServiceTest,PlatformModelDeploymentTest,PostgresPlatformModelDeploymentRepositoryTest,PostgresPlatformProviderModelDiscoveryStoreTest,PlatformProviderModelCandidateControllerTest,PlatformModelDeploymentControllerTest,ModelRuntimeAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：17 项测试通过。真实 Testcontainers PostgreSQL 运行 Flyway 至 `V202608130004`，证明：

- 成功验证事实与部署版本／状态同一事务提交；旧版本的验证写入回滚且不残留审计事实。
- 平台候选与部署只写入 `platform_*` 表；租户 `model_deployment` 未被平台部署创建路径写入。
- 无 `model-platform:write` 的请求在读取连接、候选、部署或写入验证事实之前失败关闭。
- 控制器只接收 `PlatformRequestContext`；伪造的 `tenantId` 参数不能成为身份来源，响应不含秘密字段。
- 自动配置显式导入候选与部署控制器，避免接口因 Web 白名单遗漏未挂载。

前端路由和生产构建：

```text
(cd frontend && npm test -- --run apps/console-web/src/session/console-route.test.ts)
(cd frontend && npm run build --workspace @foundation/console-web)
```

结果：19 项路由测试与生产构建通过。`/platform/model-supply/deployments` 恢复连接修订、候选页码和部署页码；控制台生产包包含独立 `PlatformModelDeploymentWorkspace`。

## 真实 Compose 与 HTTP

```text
docker compose --env-file deploy/.env -f deploy/compose/compose.yml build control-plane
docker compose --env-file deploy/.env -f deploy/compose/compose.yml up -d --no-deps --force-recreate --no-build control-plane
curl --fail http://127.0.0.1:18080/actuator/health

docker compose --env-file deploy/.env -f deploy/compose/compose.yml build console-web
docker compose --env-file deploy/.env -f deploy/compose/compose.yml up -d --no-deps --force-recreate --no-build console-web
curl --fail 'http://127.0.0.1:13000/platform/model-supply/deployments?connectionId=610000000000000001&connectionVersion=1&candidatePage=1&deploymentPage=1'
```

本地镜像重建并重建 `control-plane`、`console-web` 容器；两者健康。`18080/actuator/health` 返回 `UP`，`13000` 的部署 URL 返回统一 SPA 根节点。

针对实际控制面执行未认证请求，以下新路由全部返回 `401` 与稳定
`PLATFORM_SECURITY.AUTHENTICATION.0001`，证明路由已装配且在业务逻辑前失败关闭：

- `GET /api/v1/platform/model-supply/connections/{id}/model-candidates?expectedConnectionVersion=1&pageNumber=1&pageSize=20`
- `GET /api/v1/platform/model-supply/deployments?pageNumber=1&pageSize=20`
- `POST /api/v1/platform/model-supply/deployments/{id}/disable`

## 浏览器边界与后续

当前 Codex 会话没有可调用的浏览器控制连接器，因此未进行已登录 `PLATFORM_ADMINISTRATOR` 的真实点击、截图或录像；HTTP 与静态 SPA 回退不能替代浏览器验收。目录冻结快照、租户安全目录消费和首次发布仍未实现。M6 保持进行中，自动验收与人工评审前不得进入 M7。

## 六维检查

- 正确性：候选固定到最新发现，部署状态机与过期守卫由领域测试、服务测试和真实事务测试覆盖。
- 复杂度：候选读取沿用单一读端口，部署没有复用携带租户的旧聚合或仓储。
- 冗余：连接页与部署页职责分离；共享确认弹层仅在平台部署页复用。
- 性能：候选及部署均以 SQL `LIMIT/OFFSET` 与总数返回，浏览器不保存无界历史发现。
- 架构：平台表、平台标识、平台会话和租户表／上下文保持独立；自动配置显式登记 Web 入口。
- 测试质量：红绿记录包含编译缺口、历史候选泄漏和停用入口；真实 PostgreSQL 验证事务回滚，HTTP 与前端路由／构建分别闭合。
