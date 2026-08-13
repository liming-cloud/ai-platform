# WP-0014 平台范围模型供应链连接基础

## 范围

落实 ADR-0066 的首个窄切片：平台连接为独立聚合、独立密文表和独立平台 HTTP 路径；不迁移或读取租户连接，不实现发现、部署、目录发布或多环境功能。

## 可复现证据

- 领域／应用／PostgreSQL Testcontainers／HTTP 控制器红绿：
  `./mvnw -pl backend/contexts/model-runtime -am -Dtest=PlatformProviderConnectionTest,PlatformProviderConnectionServiceTest,PostgresPlatformProviderConnectionRepositoryTest,PlatformProviderConnectionControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`，8 项通过。
- 自动配置与 HTTP 控制器回归：
  `./mvnw -pl backend/contexts/model-runtime -am -Dtest=PlatformProviderConnectionControllerTest,ModelRuntimeAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`，8 项通过。
- 前端路由与能力投影：
  `npm test -- --run apps/console-web/src/session/console-route.test.ts apps/console-web/src/session/navigation-capability.test.ts`，24 项通过；`npm run build --workspace @foundation/console-web` 通过。
- 本地 Compose 真实 PostgreSQL 已执行 `V202608130002`，控制面重建后：`GET http://127.0.0.1:18080/api/v1/platform/model-supply/connections?pageNumber=1&pageSize=20` 返回 `401 PLATFORM_SECURITY.AUTHENTICATION.0001`，证明控制器已装配且未经平台会话失败关闭；`http://localhost:13000/platform/model-supply?page=1` 返回 `200`。

## 安全与未完成项

平台入口仅由 `model-platform:write` 能力投影可见，服务端仍以 `PlatformRequestContext` 授权。浏览器不接收 Endpoint、凭据引用或 API Key；平台密文存储不被租户运行时解析器复用。

本执行环境未提供可调用浏览器自动化连接器，因此尚未产生已登录平台管理员的交互截图／操作证据；不能以本地 HTTP 静态路由替代该项。发现、真实能力探测、部署、可用性供应链和目录快照绑定仍是后续 ADR-0066 切片，M6 继续进行中。
