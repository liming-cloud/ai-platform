# WP-0014 三角色授权与能力投影证据

## 范围

- 落实 ADR-0067，只发布 `PLATFORM_ADMINISTRATOR`、`TENANT_ADMINISTRATOR`、`TENANT_USER`。
- 平台会话和租户会话只投影三种显示角色；导航仍由服务端权限能力过滤。
- 新普通成员在同一事务内写入 `TENANT_USER` 成员资格和固定角色绑定。
- 自定义角色 HTTP 入口与 SPA 入口退役；领域和持久化遗留代码留待冗余接口审计切片给出删除证据。
- M6 保持进行中，不触发 M7。

## 红绿 TDD

红灯：

```text
./mvnw -pl backend/contexts/organization-access -am -Dtest=TenantBuiltInRoleTest,PlatformRoleBindingTest -Dsurefire.failIfNoSpecifiedTests=false test
结果：测试编译失败，旧枚举不满足三角色契约。

./mvnw -pl backend/contexts/organization-access -am -Dtest=TenantMembershipTest -Dsurefire.failIfNoSpecifiedTests=false test
结果：期望 TENANT_ADMINISTRATOR/TENANT_USER，实际 MEMBER/TENANT_ADMINISTRATOR。

./mvnw -pl backend/contexts/organization-access -am -Dtest=PostgresTenantMemberEstablishmentStoreTest -Dsurefire.failIfNoSpecifiedTests=false test
结果：成员建立后缺少 TENANT_USER 固定角色绑定。
```

绿灯：

```text
./mvnw -pl backend/contexts/organization-access -am test
结果：166 项通过；包含真实 PostgreSQL 18.4、Flyway v202608160001 和角色存储回归。

./mvnw -pl backend/bootstrap/control-plane-bootstrap -am -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false test
结果：20 项通过；包含真实 PostgreSQL、Redis、Tomcat、三角色登录／授权／会话撤销以及自定义角色路由 404。

cd frontend && npm run typecheck && npm test
结果：TypeScript 通过，18 个测试文件、97 项测试通过。

make project-status
结果：M6／WP-0014 进行中、无阻断、无待决策。
```

## 安全与产品边界

- 平台管理员固定权限包含平台会话、账户、租户、模型供应链和脱敏运营读取能力。
- 租户管理员拥有租户管理、数据、模型、助手、运营、审计和问答能力；普通用户只拥有可信问答和授权知识读取。
- 数据库迁移把旧本地角色事实收敛为三角色并推进权限修订，旧会话必须重新认证。
- 自定义角色控制器不再装配，统一 SPA 不再提供自定义角色页面或路由。

## 后续

- 运营聚合切片实现 `platform-operations:read` 的真实查询和两级运营页。
- 冗余接口审计切片在调用方、HTTP 和外部契约证据齐全后删除未装配的自定义角色服务、存储及客户端方法。
