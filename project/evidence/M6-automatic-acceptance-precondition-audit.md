# M6 自动验收前置条件审计

## 结论

截至 `87fbbd435cf147b4391aaabe06318fd122d35743`，M6 尚不具备发起自动验收或人工评审的条件，`M7` 必须保持未激活。本记录只汇总已有证据与缺口，不接受 ADR、不开启实现，也不替代任何浏览器角色验收。

## 审计范围与基线

| 项目 | 基线 | 结论 |
| --- | --- | --- |
| 工作包 | `WP-0014` | 仍为当前前沿。 |
| 里程碑 | `M6` | 保持进行中。 |
| 自动化代码基线 | `ac1a3c93b` | `mvn test` 全后端反应堆通过；平台认证 HTTP 回归 21 项通过。 |
| 当前控制面基线 | `87fbbd435cf147b4391aaabe06318fd122d35743` | 新增 ADR-0062 提议，未改变产品源码或上述测试结果。 |

## ADR 覆盖与未满足条件

| 前置能力 | 已有可复核证据 | 自动验收状态 | 未满足条件 |
| --- | --- | --- | --- |
| ADR-0057：账户、角色权限与导航能力投影 | `WP-0014-tenant-custom-role-lifecycle-and-directory.md`、`WP-0014-platform-account-password-reset.md`、`WP-0014-control-plane-automatic-acceptance-repair.md` 记录纯领域、真实 PostgreSQL、HTTP、自动配置、服务端分页控制台与全后端回归。 | 未通过 | 历史 `membership_permission` 仍参与认证与最后访问管理员判断。ADR-0062 尚未接受，且历史模型配置权限没有获准的角色映射或撤销清单。 |
| ADR-0058：厂商连接、模型探测与可用性供应链 | `WP-0014-provider-model-deployment-supply-chain.md`、`WP-0014-model-deployment-availability-projection.md`、`WP-0014-model-deployment-disable.md` 记录真实基础设施、HTTP 分页和控制台回归。 | 未通过 | 真实浏览器角色任务尚未能在受信任 HTTPS 环境执行。 |
| ADR-0059：统一企业 SPA、服务端分页与注意力保护 | `WP-0014-console-pagination-and-attention-guard.md`、`WP-0014-platform-tenant-pagination-dialog.md`、`WP-0014-query-assistant-catalog-pagination.md` 记录 URL 可恢复分页、单一 Dialog 与前端门禁回归。 | 未通过 | 真实浏览器对统一入口、角色任务和安全 Cookie 的正式回归尚未完成。 |

## 硬阻塞与退出条件

1. **授权决策**：人工接受 ADR-0062，并明确选择：为 `model-tenant:write`、`model-personal:write`、`model-platform:write` 建立对应的已发布角色，或由租户运营负责人在显式迁移清单中批准撤销。接受后才可按清单、权限等价校验、原子会话撤销和全库零行检查开始 TDD 切片。
2. **浏览器环境**：提供使用受信任 TLS 证书的部署环境，重新执行 ADR-0048／ADR-0059 的真实浏览器角色任务。`net::ERR_CERT_AUTHORITY_INVALID` 的本地预检不能以 HTTP、`--insecure`、忽略证书错误或安装本机 System keychain CA 替代。

## 门禁判定

- 2026-08-11 在当前工作区执行 `make project-verify`：项目治理 9 项测试和控制面验证均通过。
- 同日执行 `make architecture-guard`：后端架构与前端产品门禁共 24 项测试通过；架构债务递减与分页／Dialog 规则均通过。
- 这些门禁与已有自动化证据保持有效，但不消除上述两项前置条件。
- 两项条件均完成并以当前实现基线重新汇总证据前，不得创建 M6 自动验收通过记录、不得请求 M6 人工评审、不得激活 M7。
