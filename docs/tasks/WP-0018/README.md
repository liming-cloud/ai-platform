# WP-0018：成员与访问授权

| 字段 | 值 |
| --- | --- |
| ID | WP-0018 |
| 状态 | 进行中 |
| 负责人 | 成员与访问授权 |
| 分支 | main |
| 里程碑 | M6 |
| ADR | ADR-0012, ADR-0022, ADR-0067, ADR-0073, ADR-0075, ADR-0078, ADR-0079, ADR-0082, ADR-0083, ADR-0085, ADR-0092, ADR-0093 |
| 证据 | docs/tasks/WP-0018/production-contract-review.md, docs/tasks/WP-0018/evidence/WP-0018-account-production-verification-20261008.md, docs/tasks/WP-0018/evidence/WP-0018-account-prototype-parity-closure-20261008.md, docs/tasks/WP-0018/evidence/WP-0018-features-production-parity-closure-20261008.md, docs/tasks/WP-0018/evidence/WP-0018-authorization-production-parity-closure-20261008.md |
| 提交 | pending |
| 输入提交 | 2cca286848e174f5f7d3561712a9b1b86b382914 |
| 基线提交 | 2cca286848e174f5f7d3561712a9b1b86b382914 |
| 人工验收 | 不适用 |

## 问题

普通成员账户、独立功能及助手／策略统一授权三个切片已闭合；固定角色／完整租户工作区、跨包正向旅程和整包验收尚未完成。原 WP-0018 检索目标按 ADR-0079 转入 WP-0022。

## 本工作包唯一结果

租户管理员创建与治理本租户普通会员，分别保存账户、对话功能与助手范围，由直接 URL/API 证明权限生效。

## 范围内

原型入口：tenant-overview、tenant-members、tenant-member-create、tenant-member-account、tenant-member-features、tenant-member-assistants、tenant-roles、access-policies、access-policy-detail。

- 依赖 WP-0017 完成；身份与普通成员资格原子创建，登录名全局唯一。
- 重置仅允许目标身份唯一归属于本租户普通成员，不影响其他组织；停用仅撤销当前租户成员资格。
- 保持三角色和管理员只读；账户、功能、助手授权独立保存，版本冲突拒绝。
- 助手选择和访问策略共用同一版本化授权事实，有效权限取交集，旧策略不能覆盖撤权。
- 建立实际平台验收会员：合成身份、真实接口和数据库，初始无助手；WP-0023 发布后授权，WP-0024 问答联验。

## 范围外

邮件邀请、自助注册、自定义角色、同级管理员治理、跨租户重置、平台账户治理和提前实现问答。

## 公共接口与数据

复用 organization-access 的身份/成员/授权；唯一归属密码重置、普通开户、功能目录和版本化策略由已接受 ADR-0093 冻结，不把 ADR-0078 原型契约视为生产事务已经通过验收。

## 约束

目标从当前租户范围读取；密码不写浏览器持久化。审计、幂等、会话撤销随业务交付；撤销失败不得成功。未发布助手不可伪装为可授权对象。

承接[分页守卫盘点](../WP-0017/evidence/WP-0017-pagination-guard-closure-20261002.md)中 `TenantMemberDirectoryMapper#selectPage` 与 `TenantCustomRoleDirectoryMapper#selectPage` 的既有基础设施债务：触及目录时改用 `Page/IPage`，删除外层手工分页，验证租户筛选、固定排序、页边界与同范围总数后递减精确基线。自定义角色项仅登记既有目录债务，不新增或启用范围外的自定义角色；退役须另有调用者、替代和契约依据，不能直接删除。

## 验收标准

- 原子开户、重复账号、唯一归属重置、停用恢复、跨租户和管理员保护通过。
- 功能交集、助手/策略同一事实、并发覆盖、撤权及直接访问反例通过。
- 真实验收会员存在且无管理能力；无助手负例通过，后续正向旅程明确联验责任。
- 9 页真实保存、取消刷新、标准分页、千成员/二百助手、三尺寸和键盘通过。

## 验证

    make project-status
    make project-verify
    make architecture-guard
    ./mvnw -q -pl backend/contexts/organization-access -am test
    make frontend-test

账户独立切片入口为 `make acceptance-work-package WP=WP-0018 SLICE=account`，源码 `71e3b42a93ece6874a19009483952e267b7e4d58` 的最终部署 9 组真实开户、本人登录、重置、停用恢复、无助手访问拒绝及布局场景通过，登录回归 15 组、只读事实与本片原型一致性分别通过。切片 1 已闭合，整包仍进行中。本包完成前仍须补齐全部切片的真实数据库／会话、HTTP、生产浏览器及 Compose 验收；缺少依赖明确登记，不静默跳过。

第 2 片独立验收入口为 `make acceptance-work-package WP=WP-0018 SLICE=features`，仅通过正式接口创建本轮隔离租户及普通成员，用于上限关闭／恢复、停用编辑、并发覆盖和会话撤销；不调整历史验收对象，不删除数据。最终源码／前端 `6206dec074854ae253d555a389b582d78091aade`、后端运行 `024779fe15a10ecf5ff7afbffaba3b2b6e58298a` 的 13 组通过，账户回归 9 组、登录 15 组、只读事实及原型对照分别通过。凭据和新结果仅在独占临时目录，不能覆盖既有文件；整包仍进行中。

第 3 片入口为 `make acceptance-work-package WP=WP-0018 SLICE=assistants`。最终源码／前端 `9255ef44bd521a76bd4a1fea22a7f2c1aee8f217`、后端运行 `46fb0dd5f0149e7bac155b87a8aa9a9f21d16914` 的生产 8 组、登录 15 组及只读事实通过；真实隔离数据库的一千成员／二百已发布助手 7 组证明保存、预览、发布和撤权正例，不能代替 WP-0023／0024 的生产发布与完整问答联验。原型七状态三尺寸／两倍文字和生产真实空态分别对照通过。

## 证据

[生产契约提案与静态调查](production-contract-review.md)固定调查提交 `5a6789c13a2a693bf77e8e83be7e197076e79ec8`，核对原项目 9 个入口、19 项生产操作、成员／身份／授权源码及依赖方向。2026-10-08 用户在收到具体提案和确认项后指示继续推进，接受依据登记于 [ADR-0093](../../architecture/decisions/ADR-0093-member-account-and-effective-access.md)。历史调查只证明其当时范围。

[账户生产验证与待决事项](evidence/WP-0018-account-production-verification-20261008.md)分别绑定当时后端 `a726b323cf1f7d8ce9a4a674f5c76596128ac12f`、前端 `f8fdd238d9e1c3e94e2539d5024c0bb05497a3d1` 及原项目固定版本，只证明当时功能验证和原型门禁未通过。该证据发现的导航／租户名称范围冲突与原型小屏裁切，已由用户在 2026-10-08 委托选择方案并授权确认缺陷后先修原型，接受依据补入 ADR-0093；不是新视觉例外，历史证据不改写。

[账户切片部署与原型一致性收口](evidence/WP-0018-account-prototype-parity-closure-20261008.md)绑定最终源码／前端 `71e3b42a93ece6874a19009483952e267b7e4d58`、后端运行 `d71542a2675a1042c0a1051e5b9f0b78a5e563df` 和原项目修复 `73d631a91e23306b46234133a87fd0714b10d533`。完整后端 1,062 项、前端 253 项、当前生产账户 9 组／登录 15 组、精确只读事实、42 个键盘布局及 21 个完整页面放大文字场景通过；原项目与生产 84 次只读观测、严格样式比较及逐图复核通过。实际镜像分别记录，截图与秘密留在仓库外。矩阵仅六项完整操作标 verified，账户页组合操作保持 contracted。

[独立功能切片部署与原型一致性收口](evidence/WP-0018-features-production-parity-closure-20261008.md)绑定源码／前端 `6206dec074854ae253d555a389b582d78091aade` 和三个后端运行 `024779fe15a10ecf5ff7afbffaba3b2b6e58298a`，原型固定版本保持不变。后端 1,074 项、前端 258 项、最终生产功能 13 组、账户 9 组和登录 15 组通过；只读事实确认独立功能 version 6、资格／权限 version 8、授权 revision 0，历史对象未变。原项目与生产 24 次只读观测、严格样式比较及逐图查看通过。前两轮真实失败保留，先补失败用例再局部修复历史访问裁决与 SSE 错误响应；仅独立功能完整操作新增 verified，组合操作不提前通过。

[助手／策略统一授权切片收口](evidence/WP-0018-authorization-production-parity-closure-20261008.md)分别固定最终前端、三个后端、原项目和镜像摘要。完整后端 1,087 项、前端 265 项、工具 46 项、最终生产 8 组／登录 15 组及精确只读事实通过；隔离真实浏览器 7 组、4,000 次裁决允许 8／拒绝 3,992／泄漏 0。原项目两项获授权缺陷先复现再修复，固定于 `35571b0f823390bd6b80f5771e512b11be9403a4`；96 次观测、严格比较和代表截图复核通过，截图仅在 `/private/tmp`。仅新增七项 verified，旧证据的时间和版本不改写。

原型人审依据 docs/tasks/WP-0016/evidence/WP-0016-human-acceptance-20260913.md，不代替生产验证；不得记录密码、摘要或 token。

## 下一前沿

前置 WP-0017 已按 ADR-0092 的调整范围完成，依据[完整范围收口](../WP-0017/evidence/WP-0017-adjusted-scope-closure-20261006.md)，固定验证提交为 `2cca286848e174f5f7d3561712a9b1b86b382914`。本包前三片的源码、部署、生产功能、只读事实与各自原型门禁均已通过；整体状态仍为进行中。

已按 ADR-0093 接受[生产契约](production-contract-review.md)。四片中的账户、独立功能、助手／策略统一授权已交付；第 4 片固定角色／完整租户工作区未交付，只有真实名称和已交付导航外壳提前承接。9 页／19 项中十四项 verified、五项 contracted；三页进入与独立交错保存已验证，整包和跨包正向旅程不提前标验证。

下一动作：按 ADR-0093 设计第 4 片“固定角色／完整租户工作区”，先核对租户概览真实摘要、阻断对象及目标页依赖，保持既有固定角色边界；未齐备的发布对象及问答正向旅程保持跨包责任。本轮只收口第 3 片，不开始第 4 片实施。当前没有审批服务容量阻塞，不为文档检查重复创建业务对象。切片证据及控制面验证通过后按用户授权自动推送。

顺序和本包生产决定均已接受，不重新索取已冻结三角色／原型边界的确认。租户概览、历史／引用的后续依赖明确登记在契约，未经决定不转移责任、伪造完成或绕过顺排门禁。普通成员验收数据和部署只用于本片实际已实现的行为。

WP-0018 四片及整包门禁全部闭合后进入 WP-0019；已发布助手与问答正向旅程分别在 WP-0023/WP-0024 联验，WP-0027 承接平台概览，WP-0026 最终复验。M6 未完成。
