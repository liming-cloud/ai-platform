# WP-0014 平台厂商连接探测

## 范围

落实 ADR-0066 的平台连接验证前置条件。平台探测使用独立请求和扩展端口，不携带 `TenantId`，不复用租户
`ProviderProbeRequest` 或租户密钥解析路径。本切片不实现平台模型发现、部署、目录快照或租户运行时绑定。

## 红绿与自动回归

- 红灯：
  `./mvnw -pl backend/contexts/model-runtime -am -Dtest=PlatformProviderConnectionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  在引入 `ProbePlatformProviderConnectionCommand` 前因目标类型不存在而编译失败。
- 绿色：
  `./mvnw -pl backend/extensions/model-providers/provider-deterministic,backend/contexts/model-runtime -am -Dtest=PlatformProviderConnectionServiceTest,PlatformProviderConnectionControllerTest,PostgresPlatformProviderConnectionRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`
  8 项通过，覆盖无平台权限不触及仓储、成功探测仅推进平台连接、真实 PostgreSQL 更新与 HTTP 平台上下文隔离。
- 前端：
  `npm run build --workspace @foundation/console-web` 通过；平台供应链页以单一 Dialog 明确收集模型代码，写后重新读取服务端分页。

## 真实基础设施与 HTTP

- `docker compose --env-file deploy/.env -f deploy/compose/compose.yml build control-plane console-web` 成功。
- 强制重建本地 `control-plane` 与 `console-web` 后，`GET /actuator/health` 为 `200`。
- 未携带平台会话的 `POST /api/v1/platform/model-supply/connections/{id}/probe` 返回
  `401 PLATFORM_SECURITY.AUTHENTICATION.0001`，确认真实控制面装配端点且未经认证失败关闭。

## 边界与后续

页面、HTTP 和结果投影不返回 Endpoint、凭据引用、API Key 或厂商原始响应。当前环境没有可调用的已登录浏览器自动化连接器，故未将静态 `localhost` 访问作为交互验收替代；已登录管理员的点击回归待同一环境可用连接器续证。平台模型发现、部署、能力验证、发布及目录冻结快照仍为后续 ADR-0066 切片；M6 继续进行中。
