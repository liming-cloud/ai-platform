# WP-0017：平台身份与租户开通

| 字段 | 值 |
| --- | --- |
| ID | WP-0017 |
| 状态 | 进行中 |
| 负责人 | 平台身份与租户开通 |
| 分支 | main |
| 里程碑 | M6 |
| ADR | ADR-0012, ADR-0022, ADR-0033, ADR-0041, ADR-0048, ADR-0059, ADR-0067, ADR-0073, ADR-0074, ADR-0079, ADR-0083, ADR-0084 |
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

原子开户按照 ADR-0080 及 2026-09-20 补充通过。平台与租户独立登录已通过。管理员独立页在实际 Compose 镜像上完成 9 组管理员浏览器场景、15 项本轮新管理员登录回归、18 项只读数据库事实；弹窗尺寸与固定原型三档误差约 0.2px，200% 文字与键盘复核通过。用户 2026-09-24 接受生产安全说明替代原型演示密码说明、未实现功能的原型一致性留到对应切片，以及 390px 固定原型“更多”被分页遮挡时生产页保持可操作的单点布局例外；见[管理员切片收口证据](evidence/WP-0017-admin-settings-prototype-parity-closure-20260924.md)和 ADR-0083。管理员独立设置切片在上述范围内闭合，`platform-tenant-admins:0`、`:1` 已验证。资源配额独立设置按 ADR-0084 在最终 Compose 镜像上完成 9 组浏览器验收、15 项本轮新管理员登录回归、只读数据库事实与三尺寸固定原型对照；见[配额切片收口证据](evidence/WP-0017-quota-settings-prototype-parity-closure-20260928.md)，`platform-tenant-quotas:0` 已验证。功能开关、登录策略仍待各自冻结契约。不创建普通会员，不把局部通过计为 WP-0017 整包或 M6 通过。

## 验证

整包按前述验收标准执行 project-status、project-verify、architecture-guard、组织上下文 Maven、frontend-test 和真实整包验收。独立切片使用 acceptance-work-package 的明确 SLICE；缺少切片不得放行整包。

## 证据

- docs/tasks/WP-0017/evidence/WP-0017-quota-settings-prototype-parity-closure-20260928.md（资源配额独立设置切片的最终部署、功能、登录、只读事实与固定原型收口）
- docs/tasks/WP-0017/evidence/WP-0017-admin-settings-prototype-parity-closure-20260924.md（管理员独立设置切片的部署、功能、数据与限定原型例外收口）
- docs/tasks/WP-0017/evidence/WP-0017-admin-settings-dialog-geometry-deploy-20260924.md（当前前端镜像、弹窗三档几何、浏览器与数据库复验；移动“更多”差异待决定）
- docs/tasks/WP-0017/evidence/WP-0017-admin-settings-deploy-retest-20260924.md（管理员独立设置切片的功能复验与未关闭原型差异）
- docs/tasks/WP-0017/evidence/WP-0017-prototype-parity-closure-20260920.md
- docs/tasks/WP-0017/evidence/WP-0017-parity-decisions-20260920.md
- docs/tasks/WP-0017/evidence/WP-0017-strict-parity-retest-20260920.md
- docs/tasks/WP-0017/evidence/WP-0017-independent-login-deploy-20260914.md

## 下一前沿

管理员独立设置和资源配额独立设置切片均已完成部署后原型对照、功能、登录及只读事实收口；前沿仍为 WP-0017。下一独立切片为 `platform-tenant-features:0`，先冻结功能可用性、跨会话撤销和原型操作合同，再实施；本次不自动开始。登录策略、平台账户目录及普通会员仍在各自后续边界。概览导航操作 `platform-tenant-detail:0` 仍为 `pending-contract`，概览入口仍 `in-progress`，WP-0017 整包提交和证据保持 `pending`，M6 未完成。

## 固定输入与工作区

依据输入提交的 Charter、ROADMAP、STATE、上述12项当前前沿 ADR 和固定 OpenDesign 版本恢复；后续功能控制切片仍受已接受的 ADR-0075 约束。原子开户被验前端为 0a46a64b9105574a86b64facb513be20be20f01e，实际后端为 5844fd3ba2b5909161b704df5fc7e1ff026d4a0a；管理员独立页最新被验前端为 dc6dea4dd27797c1119f23a4659dc13c6dc1d999，验收脚本提交为 40f17a08577c741eba3230e2a10860d06caba501，实际后端为 02298633ba4ee38669bf46431494505d607ef8df。配额页本轮被验前端为 078af0110d3e70b41fb708ae20792633cfe79795，实际后端为 dba24ab30e7c1748c412fba157c482d1da3c6cdb。各自部署摘要和范围以对应证据为准，当前源码 HEAD 不能替代运行版本。五项人工 fixture 重命名已在 GOV-0002 完成验证与提交；本包历史验收不受影响。
