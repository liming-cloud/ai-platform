# WP-0014：平台范围模型发现

## 范围与结论

本证据覆盖 ADR-0066 的平台模型发现窄切片：`PLATFORM_ADMINISTRATOR` 仅从已验证的平台连接发现候选，连接修订、发现结果和候选只落入平台专用表。该切片不创建平台部署、目录快照或多环境抽象，也不提升任何租户资源。

`PlatformProviderModelDiscoverer` 使用独立平台请求，禁止 `TenantId`；`PlatformProviderModelDiscovery` 固化平台连接标识及修订，成功记录有效期与候选，失败记录稳定结果码但不携带候选或有效期。浏览器只收到候选数量和结果码，不能读取 Endpoint、凭据、密文或厂商原始响应。

## 红绿与自动化回归

先执行领域红灯：

```text
./mvnw -pl backend/contexts/model-runtime -am \
  -Dtest=PlatformProviderModelDiscoveryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

初始断言错误地要求 `capture` 拒绝失败观测传入的有效期；失败输出表明实现会按聚合契约清除该值。测试随后更正为验证最终不可变事实的 `expiresAt == null`，不改变生产语义。

绿色回归：

```text
./mvnw -pl backend/extensions/model-providers/provider-deterministic,backend/contexts/model-runtime -am \
  -Dtest=PlatformProviderModelDiscoveryTest,PlatformProviderModelDiscoveryServiceTest,PlatformProviderModelDiscoveryControllerTest,PostgresPlatformProviderModelDiscoveryStoreTest,PlatformProviderConnectionServiceTest,PlatformProviderConnectionControllerTest,PostgresPlatformProviderConnectionRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：14 项测试全部通过。其中：

- 无 `model-platform:write` 的平台会话不能读取连接或写入发现；成功路径没有租户上下文。
- 控制器只从 `PlatformRequestContext` 取身份，忽略伪造 `tenantId` 参数，响应不含 Endpoint、凭据引用或 API Key。
- Testcontainers PostgreSQL 执行 Flyway `V202608130003`，发现根和候选只写入 `model_runtime.platform_provider_model_discovery`、`model_runtime.platform_discovered_model`；租户 `provider_model_discovery` 保持为零。

自动配置回归：

```text
./mvnw -pl backend/contexts/model-runtime -am \
  -Dtest=ModelRuntimeAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

通过。该测试明确验证 `ModelRuntimeWebAutoConfiguration` 导入平台模型发现入口，防止 Controller 白名单遗漏再次造成路由未注册。

前端生产构建：

```text
(cd frontend && npm run build --workspace @foundation/console-web)
```

通过。平台连接页在当前 URL 服务端页中仅允许对 `VERIFIED` 连接打开“发现模型”确认 Dialog；提交后重新读取服务端连接页，并只显示候选数量及结果码。

## 真实 Compose 与 HTTP

```text
docker compose --env-file deploy/.env -f deploy/compose/compose.yml build control-plane console-web
docker compose --env-file deploy/.env -f deploy/compose/compose.yml up -d --no-deps --force-recreate --no-build control-plane console-web
curl --fail http://127.0.0.1:18080/actuator/health
curl --request POST \
  http://127.0.0.1:18080/api/v1/platform/model-supply/connections/610000000000000001/model-discovery \
  --header 'Content-Type: application/json' \
  --data '{"expectedVersion":1,"timeoutMillis":3000}'
```

镜像重建、控制面和统一控制台均健康；健康端点返回 `UP`。最初 HTTP 返回 `404`，定位为 `ModelRuntimeWebAutoConfiguration` 未显式导入新增 Controller；补充白名单与自动配置回归、重建后，同一未认证请求返回 `401` 和稳定 `PLATFORM_SECURITY.AUTHENTICATION.0001`。这证明实际控制面已装配新路由并在调用发现逻辑前失败关闭。

## 浏览器边界

当前 Codex 会话没有可调用的浏览器控制连接器，未执行已登录浏览器操作、截图或录像；静态 HTTP 不替代浏览器验收。本切片的统一 SPA 已完成类型检查和生产构建，已登录平台管理员的发现 Dialog 回归仍待具备浏览器连接器时续证。M6 继续进行中，不能据此进入 M7。
