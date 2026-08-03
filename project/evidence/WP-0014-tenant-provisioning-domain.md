# WP-0014：租户建立领域服务证据

## 范围

本证据只覆盖 ADR-0041／0042 已授权的纯领域协作：`TenantProvisioning` 接收已加载
`TenantLifecycle` 与 `SubjectIdentity`，返回应被同一事务提交的不可变
`TenantProvisioningResult`。它不实现数据库写入、发件箱、平台会话或 HTTP 接口。

## TDD 红绿

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| 红灯 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=TenantProvisioningTest -Dsurefire.failIfNoSpecifiedTests=false` | `testCompile` 报缺少 `TenantProvisioning`、`TenantMembershipRole` 和初始管理员成员资格根。 |
| 绿灯 | 同一命令 | 通过。 |
| 架构回归 | `python3 scripts/quality/backend_architecture_guard.py verify` | 通过。 |

## 已验证不变量

- `TenantMembership` 是以 `MemberId` 标识的聚合根，成员资格只引用全局 `SubjectId`，不复制登录名或凭据摘要。
- 只有已启用、且与生命周期初始管理员引用完全一致的主体可得到 `TENANT_ADMINISTRATOR` 初始成员资格。
- 结果强制保持 `PROVISIONING` 生命周期、同租户／同主体引用、管理员角色和同一发生时间；禁用主体或引用不一致不返回半成品。

## 后续缺口

需要平台治理授权决策后，才可安全实现平台会话、生命周期持久化／原子提交、发件箱和 HTTP 入口。
