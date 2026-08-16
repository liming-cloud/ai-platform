# WP-0014 M6 API 冗余审计

## 审计边界

本报告以 ADR-0067 的三角色产品边界、当前 Spring 自动配置、`ControlPlaneClient` 调用方和 HTTP 回归为依据。资源标识路径仍是服务端准确定位资源的必要契约；“不要求用户输入内部 ID”不等于删除资源路径。

## 已退休的冗余 HTTP 契约

| 路由 | 原用途 | 调用方与重叠结论 | 处理结论 |
| --- | --- | --- | --- |
| `GET /api/v1/tenant/custom-roles` | 查询租户自定义角色 | 三角色模型只允许 `TENANT_ADMINISTRATOR`、`TENANT_USER`；统一 SPA 无调用方 | 从 `OrganizationAccessWebAutoConfiguration` 撤下，浏览器客户端契约已删除 |
| `POST /api/v1/tenant/custom-roles` | 创建租户自定义角色 | 与固定租户角色目录冲突 | 从运行时撤下，浏览器客户端契约已删除 |
| `PUT /api/v1/tenant/custom-roles/{customRoleId}/permissions` | 修改自定义权限 | 权限集合必须由固定角色服务端投影 | 从运行时撤下，浏览器客户端契约已删除 |
| `POST /api/v1/tenant/custom-roles/{customRoleId}/disable` | 停用自定义角色 | 固定角色无租户自定义生命周期 | 从运行时撤下，浏览器客户端契约已删除 |
| `POST /api/v1/tenant/members/{membershipId}/custom-role-bindings` | 绑定自定义角色 | 已由固定角色绑定语义完整替代 | 从运行时撤下，浏览器客户端契约已删除 |
| `DELETE /api/v1/tenant/members/{membershipId}/custom-role-bindings/{customRoleId}` | 撤销自定义角色 | 已由固定角色撤销语义完整替代 | 从运行时撤下，浏览器客户端契约已删除 |

旧控制器和领域实现暂留为不可达源码，原因是现有自动配置回归明确断言这些 Bean 不得注册，删除全部领域持久化链属于独立清理切片。当前纯本地开发且无历史迁移负担，后续可在确认迁移脚本、测试夹具和外部契约均无引用后整体删除，不能只删一半形成残缺上下文。

## 保留的非冗余契约

| 路由 | 保留原因 |
| --- | --- |
| `GET /api/v1/usage` | 提供查询用量和配额明细；运营总览只提供时间范围内聚合，两者粒度不同 |
| `GET /api/v1/audit-records` | 提供当前租户的脱敏审计下钻；运营总览不返回事件明细 |
| `GET /api/v1/platform/operations/overview` | 全平台脱敏运营聚合，平台管理员首屏使用 |
| `GET /api/v1/platform/operations/tenants` | 按租户名称服务端分页的安全摘要，不读取租户内容 |
| `GET /api/v1/operations/overview` | 当前租户运营聚合，受租户会话和权限约束 |
| 各资源 `/{id}` 路径 | 客户端从名称选择器接收不透明引用后准确定位资源；页面不要求用户输入或理解 ID |
| 连接能力探测接口 | 部署级真实能力验证仍需保留；连接初次验证已并入模型发现流程，不再由页面要求模型代码 |

## 证据命令

```bash
rg -n 'TenantCustomRoleController|TenantCustomRoleBindingController' \
  backend/contexts/organization-access/src/main/java/com/aiplatform/foundation/organizationaccess/infrastructure/configuration/OrganizationAccessWebAutoConfiguration.java
rg -n 'listTenantCustomRoles|bindTenantMemberCustomRole' frontend
./mvnw -pl backend/contexts/organization-access -am test
npm --prefix frontend test
```
