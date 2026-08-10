# WP-0014 控制台分页与注意力保护门禁

## 目标与范围

落实 ADR-0059 的两个不可回退规则：普通资源集合不能在浏览器累加页面；同一时刻不能嵌套主 `Dialog`。本切片将模型配置／平台模型目录和脱敏查询审计改为 URL 可恢复的服务端页码，并将模型配置的创建、启用和凭据轮换收敛到单一 Fluent UI `Dialog`。

不改变模型配置、受控目录、审计的 HTTP 契约和服务端授权；不实现待接受 ADR-0061 的全局账户治理，也不处理待决的历史直授权限迁移。

## 场景与不变量

| 场景 | 业务结果 | 所有者 | 失败关闭或不变量 |
| --- | --- | --- | --- |
| 刷新或深链接模型配置页面 | 独立恢复配置页和目录页 | `ConsoleRoute` | 非正页码归一为第 1 页，不能由页签内存决定位置 |
| 翻阅模型配置、目录或审计 | 只替换当前服务器页 | `ControlPlaneClient` 与工作区 | 禁止把 `page.items` 追加到浏览器状态 |
| 建立、启用或轮换模型配置 | 弹层完成单一写入并重读服务器事实 | `ModelConfigurationWorkspace` | API Key 不回显、不保留；本地对象不作为最终事实 |
| 同时操作多个弹层 | 操作人员一次只面对一个主任务 | Fluent UI `Dialog` | 禁止嵌套 `Dialog`；关闭后由组件恢复焦点 |

## TDD 与回归证据

1. 红灯：先扩展 `console-route.test.ts`，要求 `/models/configurations?configurationPage=3&catalogPage=2` 和 `/governance/query?auditPage=4` 往返恢复。旧实现缺少字段，`npm test -- --run src/session/console-route.test.ts` 实际出现 2 项、随后 1 项断言失败。
2. 绿灯：`ConsoleRoute` 以规范 query 参数保存三个资源页码，两个工作区均以当前页重新调用已有 `PageResult` HTTP 契约；模型配置写入成功后调用 `load()` 重读服务器事实。
3. 新增 `scripts/quality/frontend_product_guard.py` 及 4 个反例单测。它无基线、无 allowlist 地拒绝 `loadMore`／`loadingMore`、`page.items` 累加和嵌套 `Dialog`，并接入 `make architecture-guard`。

| 命令 | 结果 |
| --- | --- |
| `npm test -- --run src/session/console-route.test.ts` | 红灯如上，随后 9 项路由测试通过 |
| `npm run typecheck && npm test -- --run src/session/console-route.test.ts src/features/query-governance/query-governance-state.test.ts` | 11 项通过 |
| `make architecture-guard` | 24 项门禁测试通过；后端架构门禁和前端产品门禁均通过 |
| `cd frontend && npm run lint && npm test && npm run build` | lint 通过、76 项测试通过、两个前端工作区生产构建通过 |

## 代码图谱影响

代码图谱的 `ConsoleRoute` 影响分析确认路由改动作用于解析、格式化、授权路由和 `ConsoleApp`；`ModelConfigurationWorkspace` 与 `QueryGovernanceWorkspace` 分别只通过现有 `ControlPlaneClient` 的 `PageResult` 方法读取数据。实现因此没有新增后端端点、授权旁路或浏览器端全量缓存。

## 质量复核

- 正确性：路由红绿、类型检查和 76 项前端回归覆盖页码恢复、写后重读与现有会话失败关闭。
- 复杂度：两个工作区只保留当前页及分页元数据；没有引入全局前端状态或第二套权限模型。
- 冗余：删除两个“加载更多”分支和内联凭据表单，统一由当前页重读。
- 性能：每次只请求最多 20 条已有服务端分页结果，不累积前页记录。
- 架构：前端产品门禁接入既有 `architecture-guard`，不使用债务基线或豁免。
- 测试：新增路由反例和质量门禁反例；HTTP 投影未变，复用既有客户端契约回归。

## 浏览器证据边界

本切片未把构建或组件测试替代真实浏览器验收。`https://localhost:13443/` 仍因未受信任本地证书返回 `ERR_CERT_AUTHORITY_INVALID`；依 ADR-0048 和已登记的 TLS 前置条件，不安装 CA、不忽略证书错误。正式受信任部署环境就绪后，需续证：模型配置深链接分页、创建弹层焦点恢复、凭据轮换写后重读，以及无权直接 URL 的失败关闭。
