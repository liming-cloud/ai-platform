# WP-0014：浏览器导航能力投影

## 范围与提交边界

本证据只覆盖 ADR-0057 的第一条已实施纵向切片：服务端已验证的租户／平台会话向
`browser-session` 投影最小能力码，控制台将已发布入口的所需能力与该投影相交后决定可见导航。
服务端接口的令牌派生授权保持原状。

本切片不实施账户建立、启停、凭据重置、角色目录、角色绑定、自定义角色、权限撤销或统一 SPA
迁移；现有 `membership_permission` 仍是待后续写入切片替换的遗留直接授权事实，不能称为完成 RBAC。

## 设计与代码图谱影响

- 领域设计卡：`docs/architecture/foundation-m6-enterprise-governance-design.md` 的
  “ADR-0057 首条实施设计卡：导航能力投影”。
- 代码图谱探索：`SubjectIdentity`、`TenantMembership`、`PermissionGrantSet`、
  `PersonAuthenticationService`、`BrowserSessionCapabilityService`、
  `BrowserSessionCapability`、`ConsoleApp` 与 `TenantMembershipMapper`。
- 影响分析：`TenantMembershipRepository` 影响 80 个符号，
  `BrowserSessionCapability` 影响 47 个符号，`TenantMembershipMapper` 影响 16 个符号。
  因此前者的角色化持久化改造不与本切片混做，实施仅经过后者的稳定会话投影 seam。

## TDD 红绿记录

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| 红灯 | `./mvnw -q -pl backend/contexts/organization-access -am -Dtest=BrowserSessionCapabilityServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 预期失败：`BrowserSessionCapability` 缺少 `capabilityCodes()`。 |
| 红灯 | `cd frontend && npm test -- --run packages/browser-session/src/browser-session-client.test.ts` | 预期失败：响应包含能力码时，客户端返回 `undefined`。 |
| 红灯 | `cd frontend && npm test -- --run apps/console-web/src/session/navigation-capability.test.ts` | 预期失败：`navigation-capability` seam 尚不存在。 |
| 绿灯 | 上述 organization-access Maven 定向命令 | 通过：双范围会话只投影各自实际权限码。 |
| 绿灯 | 上述 browser-session 前端定向命令 | 通过：能力码是必填且非空的响应契约。 |
| 绿灯 | 上述 console-web 前端定向命令 | 通过：租户只见实际能力入口；无治理写权限的平台运营主体不见平台租户入口。 |

## 回归结果

| 命令 | 结果 |
| --- | --- |
| `./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false test` | 通过：真实 PostgreSQL／Sa-Token HTTP 回归确认 `browser-session` 返回能力码而不返回 token、主体、成员或租户标识。 |
| `./mvnw -q -pl backend/contexts/organization-access -am test` | 通过：organization-access 全模块回归。日志仅有既有 MyBatis/Testcontainers 环境告警。 |
| `cd frontend && npm test` | 通过：15 个文件、59 项测试。 |
| `cd frontend && npm run typecheck` | 通过。 |
| `cd frontend && npm run build` | 通过：console-web 与 query-web 生产构建完成。 |
| `python3 scripts/quality/backend_architecture_guard.py verify` | 通过。 |
| `cd frontend && npm run lint` | 未通过，唯一剩余错误为范围外既有文件 `frontend/apps/console-web/src/features/evaluation/EvaluationRunWorkspace.tsx:44` 的 `react-hooks/set-state-in-effect`；本切片新增 lint 错误已修复。 |

## 安全与后续

- 能力码来自服务端已验证 `TrustedRequestContext` 与 `PlatformRequestContext`；前端不能提交、修改或用其
  替代接口授权。
- 投影不包含 token、凭据、主体、成员、租户标识或完整角色／策略。
- 后续必须先完成角色绑定的领域设计卡与反例，再替换直接成员授权，并补齐权限变更撤销会话、账户管理、
  自定义角色与正式 HTTPS 多页签浏览器证据。
