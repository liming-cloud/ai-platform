# M6 租户自定义角色生命周期、目录与导航预览

## 结果

ADR-0057 的租户范围自定义角色补齐以下闭环：

- 修改权限和停用均由领域服务计算不可变操作结果；受影响启用成员权限修订递增，并撤销旧租户会话。
- 解绑自定义角色遵守与内置角色一致的“最后访问管理员”失败关闭规则。
- 自定义角色目录只在当前 token 租户内以服务端分页读取，返回角色、固定权限集合、状态和绑定成员计数，不读取成员身份或会话。
- 控制台以独立 `/access/roles?page=N` 路由、分页表格和模态表单提供创建、权限修改、成员绑定／解绑及停用；导航预览只由固定 `NAVIGATION` 表派生，不参与服务端授权。

## 红绿与回归证据

| 验证 | 命令 | 结果 |
| --- | --- | --- |
| 解绑领域红绿 | `./mvnw -q -pl backend/contexts/organization-access -am -Dtest=TenantCustomRoleBindingServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 先因缺少 `unbind` 失败，补齐不可变解绑结果和最后管理员保护后通过。 |
| 生命周期领域与应用 | `./mvnw -q -pl backend/contexts/organization-access -am -Dtest=TenantCustomRoleLifecycleServiceTest,TenantCustomRoleLifecycleApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 权限变更、停用、权限修订和会话撤销的反例通过。 |
| PostgreSQL、自动配置与 HTTP | `./mvnw -q -pl backend/contexts/organization-access -am -Dtest=PostgresTenantCustomRoleBindingRevocationStoreTest,PostgresTenantCustomRoleLifecycleStoreTest,PostgresTenantCustomRoleDirectoryTest,TenantCustomRoleLifecycleControllerTest,OrganizationAccessAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test` | 真实 PostgreSQL 租户隔离和稳定分页、自动配置、路径资源目标与 token 上下文 HTTP 回归通过。 |
| 组织访问全模块 | `./mvnw -q -pl backend/contexts/organization-access -am test` | 通过；仅有既有 Testcontainers 与 Mapper 测试替身告警。 |
| 前端 | `npm run typecheck && npm test && npm run lint`（在 `frontend/`） | 通过：74 项测试、TypeScript 与 ESLint 均通过。 |
| 项目级自动门禁 | `git diff --check && make validate` | 通过；复杂 SQL 已按事务和安全投影登记，无 DDD 违规豁免。 |

## 发现并修复的契约偏差

真实 PostgreSQL 回归拒绝了 `query:ask`。迁移定义的已发布权限目录实际使用 `assistant:query`，控制台默认值与 HTTP 回归已统一为后者；未放宽数据库白名单。

## 未完成边界

全局主体停用、密码重置和平台角色治理仍受 ADR-0061 约束；该 ADR 仍为提议，未纳入本切片或本次提交。直授权限清理需作为独立已确认切片处理。
