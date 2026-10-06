# WP-0017：平台身份与租户开通

| 字段 | 值 |
| --- | --- |
| ID | WP-0017 |
| 状态 | 已完成 |
| 负责人 | 平台身份与租户开通 |
| 分支 | main |
| 里程碑 | M6 |
| ADR | ADR-0022, ADR-0073, ADR-0079, ADR-0080, ADR-0083, ADR-0086, ADR-0087, ADR-0088, ADR-0089, ADR-0090, ADR-0091, ADR-0092 |
| 证据 | docs/tasks/WP-0017/evidence/WP-0017-adjusted-scope-closure-20261006.md |
| 提交 | 2cca286848e174f5f7d3561712a9b1b86b382914 |
| 输入提交 | e7a1d673229acf8c7932dcae85699e517f2adab5 |
| 基线提交 | e7a1d673229acf8c7932dcae85699e517f2adab5 |
| 人工验收 | 不适用 |

## 问题

已验收原型的账户职责、原子开户、独立入口和独立设置已形成生产闭环；按 ADR-0092 调整后的 11 个入口、20 项操作完成组合证据与当前部署复核。原 WP-0017 知识接入目标按 ADR-0079 转入 WP-0020，历史绑定 83015cede7016f2aefda1f91fb388b24726c923f。

## 本工作包唯一结果

平台管理员从独立入口建立平台账户、租户及管理员，分别管理管理员、资源配额、功能和登录策略，保存与权限由真实后端保证。

## 范围内

原型入口：console-login、tenant-login、platform-tenants、platform-tenant-create、platform-tenant-detail、platform-tenant-admins、platform-tenant-quotas、platform-tenant-features、platform-tenant-security、platform-accounts、platform-roles。

ADR-0092 仅将 `platform-overview` 与 `platform-overview:0` 转交 WP-0027，在 WP-0025 后、WP-0026 前完成；其仍属 M6，未验证状态不变。本包调整为 11 个入口、20 项生产操作，转交不代表本包自动通过。

- 先提交顺排控制面、原型责任矩阵与已确认边界；具体开户、身份和设置合同由专属 ADR 接受后实施。
- 平台账户只创建/展示平台管理员；租户开户直接创建全局唯一身份、租户和初始管理员，不再先选全局账户；失败整体回滚。
- 租户概览、管理员、配额、功能和安全独立路由/保存，预期版本防止旧覆盖。
- 登录策略遵守 ADR-0079 的范围、默认值和立即会话撤销，保留角色互斥及最后管理员。
- 通过真实流程建立隔离验收租户和管理员；普通会员由 WP-0018 建立，不提前创建。
- 用实际调用方承接公共 UI 主题、控件、分页、弹窗及三尺寸，不复制原型模拟状态。

## 范围外

普通会员开户与授权、模型、知识接入、查询、WP-0027 平台治理概览、M7/M8、邮件邀请、自定义角色、生产发布和数据重置。

## 公共接口与数据

复用现有平台账户、租户生命周期和浏览器会话资源，不建第二套密码库或可空身份上下文。开户、状态、密码、管理员、配额、功能、安全各自独立命令；平台不读取租户正文。

## 约束

本包已按合同完成组合收口；账户目录框架分页的独立清理记录只证明其原范围，整包依据下述新证据。资源配额配置不代表 WP-0020 的知识接入预留已实现。先失败测试再实现，触及路径债务先清理，公共契约变化先接受专属 ADR。

表头只列当前前沿最直接的 12 项 ADR；已接受的 ADR-0012、ADR-0033、ADR-0048、ADR-0059 等历史决策仍有效，不因索引精简被撤销。

已独立交付平台 `/login` 与租户管理员 `/tenant/login`：复用现有浏览器 Cookie/CSRF 合同，承接已验收原型的登录排版，验证入口切换、角色不匹配、刷新和退出。提交 `f9ba767bf2b20716b2c04e812b45e616fe037f53` 已部署验证，15 个真实浏览器场景通过；该登录切片不包含原子开户及独立设置。原子开户独立切片已在前端 `0a46a64b9105574a86b64facb513be20be20f01e` 完成功能与原型一致性验收，依据 ADR-0080 及其 2026-09-20 用户确认补充关闭，不包含独立设置。

每个独立切片必须记录实际部署的提交、镜像、健康检查和业务断言。`make acceptance-work-package WP=WP-0017 SLICE=login` 仅代表登录切片；未指定切片时仍拒绝，任一切片不能单独证明整包通过。验收禁止删除卷、重置账户或依赖旧演示业务库，合成验收数据由正式接口建立并单独登记。

分页守卫漏检已闭合，历史反例及责任登记见[守卫证据](evidence/WP-0017-pagination-guard-closure-20261002.md)。本包两个管理员目录已改用 infrastructure `Page/IPage`，保持搜索、状态、权限、排序及同范围总数；最终部署通过只读目录、登录及原项目视觉对照，见[框架分页证据](evidence/WP-0017-administrator-framework-pagination-closure-20261004.md)。XML 普通列表精确债务由 7 递减为 5，剩余项归 WP-0018／0019／0020；不声称全仓无分页债务。

## 验收标准

- 11 个入口、20 项生产操作均关联真实接口、保存边界、反例、证据和提交；平台概览的未完成责任保留在 WP-0027，不减少 M6 覆盖。
- 原子开户、重复登录名、重置/停用、最后管理员、角色互斥、并发版本和撤销失败反例通过。
- 策略上下限、过期/锁定、错误角色入口与 CSRF 通过；密码/摘要/token 不入响应或审计。
- 独立保存、取消刷新、标准分页、七态、390/1024/1440、200% 文字、键盘、reduced-motion 对照通过。
- 真实 PostgreSQL/Redis、HTTP、生产浏览器、受控 Compose 证明闭环，不能以原型测试替代。
- 每个切片必须固定原型版本并对照实际部署页的结构、主题、控件与状态；功能测试通过不代表原型一致。产品结果必须与已验收 OpenDesign 一致，不自行豁免导航、文案、间距或响应式差异；ADR-0083 的 2026-09-24 补充仅记录用户明确接受的演示说明、未实现功能延期及 390px“更多”遮挡三项限定差异。真实数据与安全约束按已接受 ADR 承接，冲突先恢复决策一致性。截图只在 `/private/tmp`，仓库只登记原型链接和文字证据。

## 已确认决定

原子开户依据 ADR-0080 及 2026-09-20 补充通过；平台与租户独立登录已通过。管理员独立设置完成实际部署、9 组浏览器、15 项登录、18 项只读事实及三尺寸原型对照，见[管理员证据](evidence/WP-0017-admin-settings-prototype-parity-closure-20260924.md)。ADR-0083 记录用户接受的安全说明、未实现功能延期和 390px 行操作遮挡单点例外，未扩大到其他视觉项。配额按 ADR-0084、功能按 ADR-0085、安全按 ADR-0086 分别闭合，见[配额证据](evidence/WP-0017-quota-settings-prototype-parity-closure-20260928.md)、[功能证据](evidence/WP-0017-feature-settings-prototype-parity-closure-20260928.md)、[安全证据](evidence/WP-0017-security-settings-prototype-parity-closure-20260929.md)。各证据分别绑定实际部署、浏览器、登录、只读事实及固定原型；安全页保存前会话失效提示的限定差异仍按原接受范围。各独立记录只证明其原范围；本包整包结论由 2026-10-06 组合证据绑定，仍不代表普通会员、剩余分页债务或 M6 完成。

## 验证

整包按前述标准核验全部切片的真实功能、部署和原型证据，执行 project-status、project-verify、architecture-guard、组织上下文 Maven、frontend-test 及当前部署集成复验。写操作证据沿用前必须核对后续改动和回归覆盖；本次只读复验不能冒称新执行的写场景。独立切片使用 acceptance-work-package 的明确 SLICE，任一 SLICE 或未指定切片都不能单独放行整包。不开新验收租户，不降低断言，缺失项仍阻断。

## 证据

- [本机部署与人工验收清单](manual-acceptance.md)（2026-10-06 四应用最新部署、真实管理员登录、11 个可操作入口及原项目原型清单；不重跑业务写场景）
- docs/tasks/WP-0017/evidence/WP-0017-adjusted-scope-closure-20261006.md（调整后完整 11 入口／20 操作的组合核验、最新登录标题修正、真实最终部署回归与完成依据）
- docs/tasks/WP-0017/evidence/WP-0017-login-entry-navigation-closure-20261001.md（三入口导航的最终部署、18 项导航／15 项登录回归、21 组导航原型对照与只读事实；问答整页仍属 WP-0024）
- docs/tasks/WP-0017/evidence/WP-0017-tenant-directory-prototype-parity-closure-20261001.md（目录／建立入口／搜索的实际部署、原项目修订接受与最终原型一致性收口；关联查询及原型接受历史）
- docs/tasks/WP-0017/evidence/WP-0017-tenant-overview-prototype-parity-closure-20261001.md（真实概览、五页只读导航、最终部署与原型一致性收口）
- docs/tasks/WP-0017/evidence/WP-0017-platform-roles-prototype-parity-closure-20261001.md（固定角色、新版原型共享布局、实际部署与零写入收口）
- docs/tasks/WP-0017/evidence/WP-0017-platform-accounts-prototype-parity-closure-20261001.md（平台账户完整操作切片的最终部署、功能、登录、只读事实与固定原型收口）
- docs/tasks/WP-0017/evidence/WP-0017-security-settings-prototype-parity-closure-20260929.md（登录安全独立设置的最终部署、功能、登录、只读事实与固定原型收口）
- docs/tasks/WP-0017/evidence/WP-0017-feature-settings-prototype-parity-closure-20260928.md（功能上限独立设置的最终部署、功能、登录、只读事实与固定原型收口）
- docs/tasks/WP-0017/evidence/WP-0017-administrator-framework-pagination-closure-20261004.md（管理员两个目录框架分页、原型控件修正、最终部署与零业务写入验收；关联历史守卫证据）

## 下一前沿

本包调整后的完整范围已闭合，验证提交 `2cca286848e174f5f7d3561712a9b1b86b382914`；最终前端 77 组公共对照、7 组管理员严格对照、登录及只读集成、17 组业务事实通过。没有新增验收业务对象，也没有把旧写操作证据改成新的执行结果。

按 ADR-0092 激活 WP-0018 的专属生产契约设计，先确认成员归属、原子开户、唯一归属重置与版本化授权，接受 ADR 后实施。成员功能、WP-0027 平台概览、WP-0026 全量复验及 M6 人工接受仍未完成。剩余五条分页债务随 WP-0018／0019／0020 处理。

当前目录唯一原型接受版本为 `d36c7f46d0187c0421d4989c12f601d5c190d73f`；全部原型源／状态和历史证据不改写，不新增视觉例外。

## 固定输入与工作区

依据输入提交的 Charter、ROADMAP、STATE、上述 ADR 和固定 OpenDesign 版本恢复。原 66 页与操作清单仍绑定 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`；共享移动布局按 ADR-0088 采用已接受修订 `47c4f6f31823617633ce0bac98074208781f5565`。原整包收口时前端运行 `2cca286848e174f5f7d3561712a9b1b86b382914`，控制面后端运行 `a23ea28dca8dd32a36f235948a5e867ed1769def`，查询进程运行 `ef11d6aad0ffb8d8a656d5a17a3477d93b95a5c8`；历史镜像及范围保持原证据不变。2026-10-06 [人工验收交接](manual-acceptance.md)已将前端及三个后端全部真实构建、部署为 `460a3c76375da312d1f030abbce6a0d82b2001cf`，分别核实 OCI revision、镜像、健康和本轮只读验证；不以文档 HEAD 代替运行版本，也不改写原切片写操作的验证提交。五项 fixture 重命名已在 GOV-0002 验证提交，本包未修改。
