# WP-0014：本地身份基线与企业角色浏览器回归

## 目的与范围

本记录落实 ADR-0063 的本地身份基线和浏览器验收边界，并回归 ADR-0057、ADR-0058、ADR-0059
已实现的账户、固定平台角色、导航能力投影、服务端分页与注意力保护。本轮只使用现有 Compose
服务 `postgres`、`redis`、`minio`、`control-plane`、`query-runtime` 与 `console-web`；未新增应用服务、
未降低生产 Cookie 属性，也未记录口令、摘要、Cookie、会话令牌或厂商凭据。

## 本地基线重置

人工已明确授权清理当前本地租户及其关联事实，并在未纳入版本控制的操作输入中提供唯一平台超级
管理员凭据。按预检结论，精确重建本机 Compose 的 `foundation_v1` 数据库，清空当前 Redis database，
并删除当前本机 MinIO 的 `foundation-v1-private` bucket；这些数据不在当前卷中保留可恢复副本。

正式 Flyway 迁移完成后，只建立一个活动的平台超级管理员和必要的固定角色绑定。随后完全通过控制台
建立一个准备中租户、激活该租户、建立一个默认无角色的平台账户，并在页面的单一确认 Dialog 中为该
账户绑定 `PLATFORM_OPERATOR`。未以 SQL 或演示种子建立租户、成员、平台运营员或角色绑定。

最终真实 PostgreSQL 事实如下：

| 表 | 数量 |
| --- | ---: |
| `organization_access.tenant` | 1 |
| `organization_access.subject_identity` | 2 |
| `organization_access.tenant_membership` | 1 |
| `organization_access.membership_permission` | 0 |
| `organization_access.platform_role_binding` | 3 |

运营员账户的活动绑定为 `PLATFORM_OPERATOR`，其 `platform_subject_authorization.permission_version` 为 `1`。
这证明角色写入推进了认证修订，且本地基线没有遗留历史直接成员权限。

## 发现与修复

### 平台角色首次绑定写入

浏览器确认平台角色后，控制面原先返回 500。控制面日志将根因定位为 MyBatis-Plus 的
`BlockAttackInnerInterceptor` 无法解析 PostgreSQL UPSERT，而不是权限拒绝或浏览器会话失败。
`PostgresPlatformRoleBindingStore` 现由已持有的主体行锁保护，先以有条件 `UPDATE` 推进认证修订，
只在首次绑定时执行 `INSERT` 建立修订事实；不再使用 UPSERT。作用域保持在平台角色绑定存储和 XML
映射，绑定、修订和全范围会话撤销仍在同一事务中。

### 平台运营员导航投影

真实浏览器进一步发现，`PLATFORM_OPERATOR` 虽可登录，却没有任何可用工作区；这与 ADR-0061 中
“仅保留平台会话进入能力”及角色确认 Dialog 的“平台工作区”说明不一致。已固定如下最小修复：

- 只要已验证的平台会话具有既有 `platform-session:access`，`browser-session` 就投影现有
  `PLATFORM_TENANT_GOVERNANCE` 工作区类别；显示角色按账户管理员、租户治理员或运营员的实际权限
  派生，绝不把运营员显示为治理员。
- 统一控制台新增 URL 可恢复的 `/platform` 无数据平台工作区，唯一要求仍是
  `platform-session:access`；它不读取租户、成员、账户、凭据或治理数据。
- `tenant-governance:write` 与 `platform:accounts:manage` 继续分别控制 `/platform/tenants` 和
  `/platform/accounts`。运营员直接访问账户 URL 被显式拒绝，不静默跳转。

该修复未新增权限、接口、数据读取或平台自定义角色；导航始终是服务器已验证能力的只读投影，而不是
ACL。

## 红绿与自动回归

先后观察到两条可复现红灯：真实浏览器角色绑定的 500，以及仅有 `platform-session:access` 时
`visibleNavigation` 为空、`defaultConsoleRoute("platform-workspace")` 不存在。修复后执行：

```bash
./mvnw -q -pl backend/contexts/organization-access -am \
  -Dtest=BrowserSessionCapabilityServiceTest,PlatformAccountGovernanceControllerTest,PlatformAccountEstablishmentControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am \
  -Dtest=LocalProfileConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test

npm --prefix frontend test -- \
  apps/console-web/src/api/control-plane-client.test.ts \
  apps/console-web/src/session/navigation-capability.test.ts \
  apps/console-web/src/session/console-route.test.ts

npm --prefix frontend run build --workspace @foundation/console-web

make project-verify
```

后端定向测试、local profile 测试、52 项前端定向测试、控制台生产构建和 `make project-verify` 均通过。

## 真实基础设施、HTTP 与浏览器回归

在现有 Compose 控制面与控制台镜像重建并健康后，浏览器以平台超级管理员的同源 HTTP 会话完成：

1. 建立并激活本地验收租户；
2. 建立默认无角色的平台账户；
3. 通过“管理角色”菜单和单一确认 Dialog 绑定平台运营员；写后重读显示正确的 64 位主体编号与活动角色；
4. 使用运营员重新登录，看到唯一“平台工作区”入口及无数据提示；
5. 运营员直接访问 `/platform/accounts` 得到“当前会话无权访问此工作区”，没有加载账户目录；
6. 平台超级管理员重新登录后同时看到“平台工作区”“平台租户”“平台账户”，账户目录保持服务端分页，
   显示两个账户及其固定角色，不显示凭据、会话或权限快照。

本机 HTTP 验收继续使用 `http://localhost:13000`，Cookie 的 `Secure`、`HttpOnly`、`SameSite=Lax`
和 `__Host-` 属性未被降级。根据 ADR-0063，受信任 TLS 不再是本地 M6 浏览器验收的阻断项；正式部署的
TLS 仍由部署阶段单独负责。

## 结论与遗留门禁

本地身份基线、平台角色写入、导航能力投影、服务端分页和实际浏览器角色任务均已以现有基础设施回归。
M6 仍进行中：ADR-0062 的历史直接权限逐成员迁移清单或明确撤销例外尚未由租户运营负责人提供，因此
不得发起 M6 自动验收、人工评审或进入 M7。
