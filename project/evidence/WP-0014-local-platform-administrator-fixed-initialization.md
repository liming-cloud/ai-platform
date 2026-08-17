# WP-0014 本地平台管理员固定凭据初始化证据

## 结论

- ADR-0069 已落地：纯本地空数据库固定初始化唯一平台管理员 `platform.root`，公开本地开发口令为 `admin123456`。
- 初始化由隔离的 local Flyway repeatable migration 完成，只在主体、租户和成员均为空时原子写入主体、平台授权修订和 `PLATFORM_ADMINISTRATOR` 绑定。
- 数据库只保存 PBKDF2 摘要；共享、测试、预发布和生产 profile 不加载该迁移，也不得复用该口令。
- 当前本地数据库已在受保护事务中更新为同一口令。部署初始化迁移后，既有主体未被覆盖，主体版本保持为 `3`。
- 本切片不关闭 M6，不进入 M7；登录后的完整 Chrome 从零产品流程仍须人工确认浏览器填写口令后续证。

## 红绿 TDD

红灯命令：

```bash
./mvnw -pl backend/bootstrap/control-plane-bootstrap -am \
  -Dtest=LocalPlatformAdministratorInitializationMigrationTest,LocalProfileConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

红灯结果：空库不存在 `platform.root`，`.env.example` 也未声明固定本地登录名和口令，共两项失败。

绿灯命令：

```bash
./mvnw -pl backend/bootstrap/control-plane-bootstrap -am \
  -Dtest=LocalPlatformAdministratorInitializationMigrationTest,LocalLifecycleAuthenticationCutoverMigrationTest,LocalProfileConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

绿灯结果：9 项测试通过，覆盖空库建立、标准迁移隔离、已有租户不建立、已有主体不建立、PBKDF2 真实匹配及本地配置投影。

全量门禁：

```bash
make validate
```

结果：退出码 `0`；后端 Maven 全量测试、前端 typecheck／lint、22 个测试文件共 119 项测试、生产构建及项目质量门禁全部通过。

## 当前数据库受控更新

更新前备份：

```text
/tmp/wp0014-local-platform-root-before-fixed-password.dump
```

备份权限为 `0600`，已在 PostgreSQL 容器内使用 `pg_restore --list` 验证可读。更新事务要求同时满足：唯一活动主体为 `platform.root`、存在活动平台管理员绑定、租户和成员均为零；条件不满足即回滚。事务只替换密码摘要并将主体版本从 `2` 推进到 `3`。

## 真实基础设施与 HTTP

实现提交：`e7ed97d77`（`feat: 初始化本地平台管理员固定凭据`）。

部署命令：

```bash
docker compose --env-file deploy/compose/.env.example \
  -f deploy/compose/compose.yml \
  -f deploy/compose/compose.browser-acceptance.yml \
  up --detach --build --no-deps control-plane
```

部署结果：

- `control-plane` 健康状态为 `healthy`，`/actuator/health` 返回 `UP`。
- `http://localhost:13443/` 返回 `200`。
- Flyway 历史包含成功的 `initialize local platform administrator` repeatable migration。
- 数据库为一个主体、一个平台授权修订、一个活动平台管理员绑定、零租户、零成员。
- 知识空间、数据来源、平台模型连接、平台模型部署、助手、评测运行和查询运行均为零。

同源浏览器会话 HTTP 回归：

- 使用 `platform.root`／`admin123456` 登录返回 `204`。
- 会话读取返回 `200`，显示角色为 `PLATFORM_ADMINISTRATOR`。
- 会话能力包含 `platform-session:access`、`platform-operations:read`、`tenant-governance:write`、`model-platform:write` 和 `platform:accounts:manage`。
- 未登录读取平台运营总览返回 `401`，登录后返回 `200`。
- 回归输出未记录会话 Cookie、CSRF 值或密码摘要。

## 边界

- 该固定口令是公开的纯本地研发初始化数据，不属于生产凭据。
- 浏览器自动填写属于敏感数据操作，必须在实际提交前取得人工当次确认。
- M6 和 `WP-0014` 保持进行中；自动验收及人工明确接受前不得进入 M7。
