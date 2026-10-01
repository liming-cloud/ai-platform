# WP-0017：平台身份与租户开通

| 字段 | 值 |
| --- | --- |
| ID | WP-0017 |
| 状态 | 进行中 |
| 负责人 | 平台身份与租户开通 |
| 分支 | main |
| 里程碑 | M6 |
| ADR | ADR-0022, ADR-0048, ADR-0073, ADR-0079, ADR-0080, ADR-0083, ADR-0086, ADR-0087, ADR-0088, ADR-0089, ADR-0090, ADR-0091 |
| 证据 | pending |
| 提交 | pending |
| 输入提交 | e7a1d673229acf8c7932dcae85699e517f2adab5 |
| 基线提交 | e7a1d673229acf8c7932dcae85699e517f2adab5 |
| 人工验收 | 不适用 |

## 问题

已验收原型的账户职责、原子开户、独立入口和独立设置尚未全部成为生产闭环。原 WP-0017 知识接入目标按 ADR-0079 转入 WP-0020，历史绑定 83015cede7016f2aefda1f91fb388b24726c923f。

## 本工作包唯一结果

平台管理员从独立入口建立平台账户、租户及管理员，分别管理管理员、资源配额、功能和登录策略，保存与权限由真实后端保证。

## 范围内

原型入口：console-login、tenant-login、platform-overview、platform-tenants、platform-tenant-create、platform-tenant-detail、platform-tenant-admins、platform-tenant-quotas、platform-tenant-features、platform-tenant-security、platform-accounts、platform-roles。

- 先提交顺排控制面、原型责任矩阵与已确认边界；具体开户、身份和设置合同由专属 ADR 接受后实施。
- 平台账户只创建/展示平台管理员；租户开户直接创建全局唯一身份、租户和初始管理员，不再先选全局账户；失败整体回滚。
- 租户概览、管理员、配额、功能和安全独立路由/保存，预期版本防止旧覆盖。
- 登录策略遵守 ADR-0079 的范围、默认值和立即会话撤销，保留角色互斥及最后管理员。
- 通过真实流程建立隔离验收租户和管理员；普通会员由 WP-0018 建立，不提前创建。
- 用实际调用方承接公共 UI 主题、控件、分页、弹窗及三尺寸，不复制原型模拟状态。

## 范围外

普通会员开户与授权、模型、知识接入、查询、M7/M8、邮件邀请、自定义角色、生产发布和数据重置。

## 公共接口与数据

复用现有平台账户、租户生命周期和浏览器会话资源，不建第二套密码库或可空身份上下文。开户、状态、密码、管理员、配额、功能、安全各自独立命令；平台不读取租户正文。

## 约束

当前已进入按合同实施与部署验证阶段；账户目录框架分页的前置清理不代表整包完成。资源配额配置不代表 WP-0020 的知识接入预留已实现。先失败测试再实现，触及路径债务先清理，公共契约变化先接受专属 ADR。

表头只列当前前沿最直接的 12 项 ADR；已接受的 ADR-0012、ADR-0033、ADR-0059 等历史决策仍有效，不因索引精简被撤销。

已独立交付平台 `/login` 与租户管理员 `/tenant/login`：复用现有浏览器 Cookie/CSRF 合同，承接已验收原型的登录排版，验证入口切换、角色不匹配、刷新和退出。提交 `f9ba767bf2b20716b2c04e812b45e616fe037f53` 已部署验证，15 个真实浏览器场景通过；该登录切片不包含原子开户及独立设置。原子开户独立切片已在前端 `0a46a64b9105574a86b64facb513be20be20f01e` 完成功能与原型一致性验收，依据 ADR-0080 及其 2026-09-20 用户确认补充关闭，不包含独立设置。

每个独立切片必须记录实际部署的提交、镜像、健康检查和业务断言。`make acceptance-work-package WP=WP-0017 SLICE=login` 仅代表登录切片；未指定切片时必须拒绝把未完成的整包记为通过。验收禁止删除卷、重置账户或依赖旧演示业务库，合成验收数据由正式接口建立并单独登记。

后续门禁事项由本包负责：手工分页正则只识别 Page/After 命名，漏计本次原 list 语句。退出条件为增加该命名反例、精确盘点，并把未触及目录登记给所属包；不以当前扫描数量声称全仓无债务。

## 验收标准

- 12 个入口每项生产操作均关联真实接口、保存边界、反例、证据和提交。
- 原子开户、重复登录名、重置/停用、最后管理员、角色互斥、并发版本和撤销失败反例通过。
- 策略上下限、过期/锁定、错误角色入口与 CSRF 通过；密码/摘要/token 不入响应或审计。
- 独立保存、取消刷新、标准分页、七态、390/1024/1440、200% 文字、键盘、reduced-motion 对照通过。
- 真实 PostgreSQL/Redis、HTTP、生产浏览器、受控 Compose 证明闭环，不能以原型测试替代。
- 每个切片必须固定原型版本并对照实际部署页的结构、主题、控件与状态；功能测试通过不代表原型一致。产品结果必须与已验收 OpenDesign 一致，不自行豁免导航、文案、间距或响应式差异；ADR-0083 的 2026-09-24 补充仅记录用户明确接受的演示说明、未实现功能延期及 390px“更多”遮挡三项限定差异。真实数据与安全约束按已接受 ADR 承接，冲突先恢复决策一致性。截图只在 `/private/tmp`，仓库只登记原型链接和文字证据。

## 已确认决定

原子开户按照 ADR-0080 及 2026-09-20 补充通过。平台与租户独立登录已通过。管理员独立页在实际 Compose 镜像上完成 9 组管理员浏览器场景、15 项本轮新管理员登录回归、18 项只读数据库事实；弹窗尺寸与固定原型三档误差约 0.2px，200% 文字与键盘复核通过。用户 2026-09-24 接受生产安全说明替代原型演示密码说明、未实现功能的原型一致性留到对应切片，以及 390px 固定原型“更多”被分页遮挡时生产页保持可操作的单点布局例外；见[管理员切片收口证据](evidence/WP-0017-admin-settings-prototype-parity-closure-20260924.md)和 ADR-0083。管理员独立设置切片在上述范围内闭合，`platform-tenant-admins:0`、`:1` 已验证。资源配额独立设置按 ADR-0084 在最终 Compose 镜像上完成 9 组浏览器验收、15 项本轮新管理员登录回归、只读数据库事实与三尺寸固定原型对照；见[配额切片收口证据](evidence/WP-0017-quota-settings-prototype-parity-closure-20260928.md)，`platform-tenant-quotas:0` 已验证。可用功能独立设置按 ADR-0085 在最终 Compose 镜像上完成 12 组功能浏览器验收、15 组本轮新管理员登录回归、只读数据库事实与三尺寸固定原型对照；见[功能切片收口证据](evidence/WP-0017-feature-settings-prototype-parity-closure-20260928.md)，`platform-tenant-features:0` 已验证。登录安全独立设置按 ADR-0086 在最终 Compose 镜像上完成 10 组安全浏览器验收、15 组本轮新管理员登录回归、只读数据库事实和三尺寸固定原型对照；ADR-0079 的保存前会话失效提示相对固定原型形成单点限定差异，见[登录安全切片收口证据](evidence/WP-0017-security-settings-prototype-parity-closure-20260929.md)，`platform-tenant-security:0` 已验证。不创建普通会员，不把局部通过计为 WP-0017 整包或 M6 通过。

## 验证

整包按前述验收标准执行 project-status、project-verify、architecture-guard、组织上下文 Maven、frontend-test 和真实整包验收。独立切片使用 acceptance-work-package 的明确 SLICE；缺少切片不得放行整包。

## 证据

- docs/tasks/WP-0017/evidence/WP-0017-login-entry-navigation-closure-20261001.md（三入口导航的最终部署、18 项导航／15 项登录回归、21 组导航原型对照与只读事实；问答整页仍属 WP-0024）
- docs/tasks/WP-0017/evidence/WP-0017-tenant-directory-prototype-parity-closure-20261001.md（目录／建立入口／搜索的实际部署、原项目修订接受与最终原型一致性收口；关联查询及原型接受历史）
- docs/tasks/WP-0017/evidence/WP-0017-tenant-overview-prototype-parity-closure-20261001.md（真实概览、五页只读导航、最终部署与原型一致性收口）
- docs/tasks/WP-0017/evidence/WP-0017-platform-roles-prototype-parity-closure-20261001.md（固定角色、新版原型共享布局、实际部署与零写入收口）
- docs/tasks/WP-0017/evidence/WP-0017-platform-accounts-prototype-parity-closure-20261001.md（平台账户完整操作切片的最终部署、功能、登录、只读事实与固定原型收口）
- docs/tasks/WP-0017/evidence/WP-0017-security-settings-prototype-parity-closure-20260929.md（登录安全独立设置的最终部署、功能、登录、只读事实与固定原型收口）
- docs/tasks/WP-0017/evidence/WP-0017-feature-settings-prototype-parity-closure-20260928.md（功能上限独立设置的最终部署、功能、登录、只读事实与固定原型收口）
- docs/tasks/WP-0017/evidence/WP-0017-quota-settings-prototype-parity-closure-20260928.md（资源配额独立设置切片的最终部署、功能、登录、只读事实与固定原型收口）

## 下一前沿

原子开户、独立设置、平台账户、固定角色、租户概览与目录均已独立收口。三入口导航 `tenant-login:1` 按 ADR-0091 在前端 `ee34520f3f00098634fd749992aa533c81eda139` 通过 18 项导航、15 项登录回归、21 组导航原型对照和只读事实复核；矩阵为 11/12 入口、20/21 操作已验证。仅导航闭合，普通成员建立／完整问答登录仍属 WP-0018／0024。用户选择保持 M6 并禁止修改原型；`platform-overview:0` 的 M8 事实及目标页继续待实现。下一独立事项为既有分页门禁反例、精确盘点与所属包登记；不自动实现运维依赖。产品前沿保持 WP-0017，整包提交和证据保持 `pending`，M6 未完成。

原项目目录修订及四列空结果纠错的最新接受版本为 `d36c7f46d0187c0421d4989c12f601d5c190d73f`，原型 10 项及浏览器 12 项通过；生产已依据该版本完成目录切片，不新增视觉例外。全库现有 70 个目录对象均有管理员资格，零资格呈现由真实生产表格 SSR 与原型反例覆盖，不宣称生产存在零资格样本。没有创建新业务对象，不在本轮自动开始后续切片。

## 固定输入与工作区

依据输入提交的 Charter、ROADMAP、STATE、上述 ADR 和固定 OpenDesign 版本恢复。原 66 页与操作清单仍绑定 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`；共享移动布局按 ADR-0088 采用已接受修订 `47c4f6f31823617633ce0bac98074208781f5565`。当前前端运行 `ee34520f3f00098634fd749992aa533c81eda139`，控制面后端运行 `90a28e1bed18fbbda628cb7831ee84982f847adb`，查询进程仍为 `ef11d6aad0ffb8d8a656d5a17a3477d93b95a5c8`；镜像和验收范围以各切片证据为准，原型及历史验证仍由实现矩阵绑定，不用 HEAD 替代。五项人工 fixture 重命名已在 GOV-0002 完成验证与提交，本包未修改其内容。
