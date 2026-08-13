# WP-0014 平台模型目录快照

## 范围

依据 ADR-0065、ADR-0066，把受控模型目录的自由 Endpoint 输入收敛为平台已发布部署的冻结快照；不引入多环境复制或租户部署提升。

## TDD 与实现

- 红灯：`ControlledModelCatalogServiceTest.目录服务不得暴露自由端点创建或旧租户上下文发布入口` 检出两个残留公共旁路：接收 `ProviderKind`／`URI` 的 `create`，以及接收 `TrustedRequestContext` 的 `publish`。
- 绿灯：删除上述两个服务层公共入口；目录唯一创建与发布入口均要求 `PlatformRequestContext` 和平台部署来源。
- 绿灯：目录创建与发布重验平台权限、部署发布状态／修订／验证期限、连接修订／健康状态；不满足即失败关闭。
- `V202608130005__add_controlled_model_catalog_deployment_snapshot.sql` 保存来源部署标识和修订；真实 PostgreSQL 回归通过迁移与映射。
- 浏览器请求仅包含部署标识与版本，目录视图、租户客户端不含 Endpoint；生产模型配置建立／启用和运行时都会复核快照来源，来源部署不存在时分别在持久化前、启用前和模型工厂调用前拒绝。

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
```

Maven 目标套件和控制台生产构建均通过，真实 PostgreSQL 已执行 `V202608130005` 并验证来源部署标识／修订往返。Compose 重建后 `control-plane` 与 `console-web` 均 healthy，健康端点返回 `UP`；无会话目录写入返回 `401 PLATFORM_SECURITY.AUTHENTICATION.0001`，未把未认证拒绝误记为成功创建。浏览器工具在本会话仍不可调用，因此已登录浏览器人工回归不以 HTTP 或构建替代，保持待执行。

## 六维检查

- 正确性：目录来源、发布、模型配置建立／启用及运行时均复核平台部署和连接事实。
- 复杂度：复用现有平台部署页与分页，不增加第二套目录导航。
- 冗余：删除浏览器侧目录 Endpoint 契约，不保留自由输入路径。
- 性能：目录与部署仍为服务端分页；运行时仅按稳定标识读取两个聚合。
- 架构：租户运行时仅消费目录安全快照；平台凭据不跨边界。
- 测试质量：包含红绿服务测试、控制器契约、运行时失效回归、真实 PostgreSQL 迁移／映射、Compose HTTP 失败关闭和前端生产构建。
