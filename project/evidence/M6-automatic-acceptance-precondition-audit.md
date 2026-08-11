# M6 自动验收前置条件审计

## 结论

截至 `08da61e7d7e00b7da45ebf635b4ce7b2c7d9046c`，M6 仍不具备发起自动验收或人工评审的条件，`M7` 必须保持未激活。本记录只汇总已有证据与缺口，不替代任何浏览器角色验收。

## 审计范围与基线

| 项目 | 基线 | 结论 |
| --- | --- | --- |
| 工作包 | `WP-0014` | 仍为当前前沿。 |
| 里程碑 | `M6` | 保持进行中。 |
| 自动化代码基线 | `ac1a3c93b` | `mvn test` 全后端反应堆通过；平台认证 HTTP 回归 21 项通过。 |
| 当前控制面基线 | `08da61e7d7e00b7da45ebf635b4ce7b2c7d9046c` | ADR-0062 的领域与清单快照约束已实现；尚未连通真实 PostgreSQL。 |

## ADR 覆盖与未满足条件

| 前置能力 | 已有可复核证据 | 自动验收状态 | 未满足条件 |
| --- | --- | --- | --- |
| ADR-0057：账户、角色权限与导航能力投影 | `WP-0014-tenant-custom-role-lifecycle-and-directory.md`、`WP-0014-platform-account-password-reset.md`、`WP-0014-control-plane-automatic-acceptance-repair.md` 记录纯领域、真实 PostgreSQL、HTTP、自动配置、服务端分页控制台与全后端回归。 | 未通过 | ADR-0063 已授权本地身份基线重置；当前容器仍是旧镜像和旧种子，必须以唯一平台超级管理员基线重建并补齐角色、用户、菜单与接口权限的真实回归。 |
| ADR-0058：厂商连接、模型探测与可用性供应链 | `WP-0014-provider-model-deployment-supply-chain.md`、`WP-0014-model-deployment-availability-projection.md`、`WP-0014-model-deployment-disable.md` 记录真实基础设施、HTTP 分页和控制台回归。 | 未通过 | 需在当前镜像与本地身份基线上重跑真实基础设施、HTTP 和隔离浏览器角色任务。 |
| ADR-0059：统一企业 SPA、服务端分页与注意力保护 | `WP-0014-console-pagination-and-attention-guard.md`、`WP-0014-platform-tenant-pagination-dialog.md`、`WP-0014-query-assistant-catalog-pagination.md` 记录 URL 可恢复分页、单一 Dialog 与前端门禁回归。 | 未通过 | 需在当前镜像与本地身份基线上重跑统一入口、角色任务、服务端分页和注意力保护的浏览器功能回归。 |

## 硬阻塞与退出条件

1. **本地身份基线**：按 ADR-0063 只读确认当前本地 Compose 目标后，清理历史成员与直接权限，建立唯一平台超级管理员，并以实际角色、账户、菜单和接口权限闭合浏览器与 HTTP 回归。`project/evidence/WP-0014-local-identity-reset-preflight.md` 已确认当前成员删除会受到租户生命周期和查询事实外键阻止；在人工确认清理租户关联事实及初始管理员凭据前不得执行删除。
2. **当前镜像回归**：Docker 已恢复，但现有服务早于当前源码；必须重建当前 Compose 服务并在不新增长期容器的前提下重跑 PostgreSQL、Redis、MinIO、HTTP 和前端验收。

## 门禁判定

- 2026-08-11 在当前工作区执行 `make project-verify`：项目治理 9 项测试和控制面验证均通过。
- 同日执行 `make architecture-guard`：后端架构与前端产品门禁共 24 项测试通过；架构债务递减与分页／Dialog 规则均通过。
- 这些门禁与已有自动化证据保持有效，但不消除上述两项前置条件。
- 上述条件完成并以当前实现基线重新汇总证据前，不得创建 M6 自动验收通过记录、不得请求 M6 人工评审、不得激活 M7。ADR-0063 已将受信任 TLS 浏览器证据移出 M6 门禁。
- `http://localhost:13000` 的浏览器验收保留现有 `Secure`、`HttpOnly`、`SameSite=Lax` 与 `__Host-` cookie；当前浏览器登录失败已由同一旧控制面的无 Cookie 登录 `401` 复核为旧身份基线问题，不得误归因于 TLS 或 cookie 降级。
