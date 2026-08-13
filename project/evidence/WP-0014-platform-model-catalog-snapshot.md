# WP-0014 平台模型目录快照

## 范围

依据 ADR-0065、ADR-0066，把受控模型目录的自由 Endpoint 输入收敛为平台已发布部署的冻结快照；不引入多环境复制或租户部署提升。

## TDD 与实现

- 红灯：`ControlledModelCatalogServiceTest.目录服务不得暴露自由端点创建或旧租户上下文发布入口` 检出两个残留公共旁路：接收 `ProviderKind`／`URI` 的 `create`，以及接收 `TrustedRequestContext` 的 `publish`。
- 绿灯：删除上述两个服务层公共入口；目录唯一创建与发布入口均要求 `PlatformRequestContext` 和平台部署来源。
- 绿灯：目录创建与发布重验平台权限、部署发布状态／修订／验证期限、连接修订／健康状态；不满足即失败关闭。
- `V202608130005__add_controlled_model_catalog_deployment_snapshot.sql` 保存来源部署标识和修订；真实 PostgreSQL 回归通过迁移与映射。
- 浏览器请求仅包含部署标识与版本，目录视图、租户客户端不含 Endpoint；生产模型配置建立／启用和运行时都会复核快照来源，来源部署不存在时分别在持久化前、启用前和模型工厂调用前拒绝。
- 红灯：`PostgresModelConfigurationRepositoryTest.分页选择目录只返回来源当前可用的已发布条目` 在当前实现返回四条条目，断言为 `expected: <1> but was: <4>`；其中分别覆盖来源部署修订失配、验证到期和连接修订漂移。
- 绿灯：受控目录的 PostgreSQL 页码查询与 `total` 计数在同一个关联谓词中筛选来源部署修订、部署发布状态、验证到期时间、连接修订和连接验证状态；失效条目保留审计事实，但不再进入租户选择器。

## 可复现验证

```bash
./mvnw -q -pl backend/contexts/model-runtime -am \
  -Dtest=ControlledModelCatalogServiceTest,ControlledModelCatalogControllerTest,ControlledModelCatalogEntryTest,PostgresModelConfigurationRepositoryTest,ModelConfigurationRuntimeServiceTest,ModelRuntimeAutoConfigurationTest,PlatformModelDeploymentServiceTest,PostgresPlatformModelDeploymentRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
(cd frontend && npm run build --workspace @foundation/console-web)
docker compose --env-file deploy/.env -f deploy/compose/compose.yml build control-plane console-web
docker compose --env-file deploy/.env -f deploy/compose/compose.yml up -d --no-deps --force-recreate --no-build control-plane console-web
curl --fail --silent http://127.0.0.1:18080/actuator/health
curl --silent --output /tmp/wp0014-catalog-unauth.json --write-out '%{http_code}\n' \
  --request POST http://127.0.0.1:18080/api/v1/controlled-model-catalog \
  --header 'Content-Type: application/json' \
  --data '{"platformDeploymentId":"620000000000000001","expectedDeploymentVersion":3}'
curl --silent --output /tmp/wp0014-catalog-page-unauth.json --write-out '%{http_code}\n' \
  'http://127.0.0.1:18080/api/v1/controlled-model-catalog?pageNumber=1&pageSize=20'
```

Maven 目标套件与完整 `make validate` 均通过；真实 PostgreSQL 已执行 `V202608130005` 并验证来源部署标识／修订往返及页码／总数的当前可用性筛选。Compose 已用本切片镜像重建 `control-plane`，并与 `console-web` 同时 healthy，健康端点返回 `UP`；无会话目录读取返回 `401 PLATFORM_SECURITY.AUTHENTICATION.0001`，未把未认证拒绝误记为目录页成功。统一控制台浏览器在 `http://localhost:13000` 实测只显示租户编码、登录名、密码和禁用的登录按钮；当前没有可授权使用的登录凭据，因此未伪造会话，也不以该登录页代替平台管理员建立目录、租户模型配置启用和首份发布的已登录回归。

## 六维检查

- 正确性：目录来源、发布、模型配置建立／启用及运行时均复核平台部署和连接事实。
- 复杂度：复用现有平台部署页与分页，不增加第二套目录导航。
- 冗余：删除浏览器侧目录 Endpoint 契约，不保留自由输入路径。
- 性能：目录与部署仍为服务端分页；运行时仅按稳定标识读取两个聚合。
- 架构：租户运行时仅消费目录安全快照；平台凭据不跨边界。
- 测试质量：包含红绿服务测试、选择器 PostgreSQL 页码／总数失效筛选、控制器契约、运行时失效回归、真实 PostgreSQL 迁移／映射、Compose HTTP 失败关闭和当前浏览器登录边界检查。
