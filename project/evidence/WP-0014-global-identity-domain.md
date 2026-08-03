# WP-0014：全局主体与租户生命周期领域红绿证据

## 范围

本证据只覆盖 ADR-0042 的全局 `SubjectIdentity` 根和 ADR-0041 的 `TenantLifecycle` 根。它不声称已完成身份迁移、平台会话、成员资格、持久化、HTTP 或浏览器角色任务。

## 实际红灯

| 命令 | 预期失败 | 实际结果 |
| --- | --- | --- |
| `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=SubjectIdentityTest -Dsurefire.failIfNoSpecifiedTests=false` | 尚未存在 `SubjectIdentity`。 | `testCompile` 报“找不到符号：SubjectIdentity”。 |
| `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=TenantLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false` | 尚未存在 `TenantLifecycle` 和生命周期状态。 | `testCompile` 报“找不到符号：TenantLifecycle／TenantLifecycleStatus”。 |

## 实际绿灯

| 命令 | 结果 |
| --- | --- |
| `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=SubjectIdentityTest,TenantLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false` | 通过。 |
| `./mvnw -q -pl backend/contexts/organization-access -am test` | 通过，包含既有组织访问架构、自动配置和真实 PostgreSQL 回归。 |
| `git diff --check` | 通过。 |

## 已验证不变量

- `SubjectIdentity` 是以既有 `SubjectId` 为标识的聚合根，凭据摘要只保存于该根；禁用生成新修订且重复禁用不再改变事实。
- 全局登录名仅接受受控 ASCII 标识，主体标识、凭据、修订与创建时间均不能为空或非法。
- `TenantLifecycle` 只允许 `PROVISIONING → ACTIVE → SUSPENDED → ACTIVE`；挂起、恢复与非法状态转换不允许由外层绕过。
- 生命周期根只引用初始管理员 `SubjectId`，不持有成员对象、知识、助手、模型或正文。

## 后续缺口

- 以真实 PostgreSQL 把历史成员凭据迁移为全局主体与成员资格，且对登录名／主体冲突失败关闭。
- 以 `TenantMembership` 角色建立初始租户管理员，接入 `TenantProvisioning` 不可变操作结果和原子发件箱。
- 实现 `PlatformRequestContext`、平台会话恢复、平台与租户 HTTP 反例、角色浏览器任务与全量验收。
