# WP-0018：成员对话功能独立切片部署与原型一致性收口

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0018 |
| 日期 | 2026-10-08 |
| 状态 | 独立功能切片通过；WP-0018 整包和 M6 未完成 |
| 本片输入基线 | 238fe252106ae1d226e64aac2984bfff085b43ba |
| 验证提交 | 6206dec074854ae253d555a389b582d78091aade |
| 前端运行提交 | 6206dec074854ae253d555a389b582d78091aade |
| 后端运行提交 | 024779fe15a10ecf5ff7afbffaba3b2b6e58298a |
| 验收提示修正 | 2a11226dd9d4dffe4eb44c1ff2fa464b128d84ab；仅限定入口的结果范围，未改变验收步骤 |
| 原型固定提交 | 73d631a91e23306b46234133a87fd0714b10d533 |
| 验证范围 | 当前租户普通成员四项功能的读取、独立保存、版本冲突、取消／刷新、草稿隔离、停用编辑、上限交集、会话撤销和直接访问拒绝；账户及登录回归 |
| 未完成范围 | 助手／策略统一授权、固定角色／完整工作区、组合操作、跨包问答正向旅程、WP-0018 整包和 M6 |

## 输入、契约与范围

开始及收口复核 Charter、ROADMAP、STATE、[工作包](../README.md)和所引用 ADR。本包输入 `2cca286848e174f5f7d3561712a9b1b86b382914`、本片输入基线及上述验证提交依次可达；当前执行任务和产品前沿保持 WP-0018，M6 未完成。[ADR-0093](../../../architecture/decisions/ADR-0093-member-account-and-effective-access.md)已接受四片顺序及本片契约，没有重新索取既有授权或提前实施后两片。

原子成员账户承接[首片收口](WP-0018-account-prototype-parity-closure-20261008.md)。本片交付 `GET/PUT /api/v1/tenant/members/{membershipId}/features` 及对应前端：

- 可信会话决定当前租户，每次重核数据库管理员角色、权限版本及可访问事实；管理员目标只读，错租户和普通成员拒绝。
- 仅保存完整四布尔值，核对独立功能版本与租户上限版本；旧无变化请求仍拒绝。上限关闭保留保存值，只拒绝新增越界授权；停用资格可编辑，保存不隐式恢复资格。
- 有效变化在同一事务更新功能、资格及权限版本、审计并撤销本资格会话；撤销或审计失败回滚。无变化、取消、无效请求和并发失败方不产生额外保存审计；不改写身份、助手集合或授权 revision。
- 草稿仅含四项及原始版本，按登录代次和目标隔离；取消重新读取，退出清除。交错编辑明确冲突、保留草稿并要求显式刷新。对象导航、侧栏和 SPA 后退按原型提供保留／放弃提示，不把跨文档后退扩大为已验证行为。

租户—上限—主体—资格的锁顺序、失败关闭及公开 facade 边界继续有效；没有新增 Maven 边、权限框架或无调用方抽象。五项 fixture 已由 GOV-0002 纳入历史，本轮没有改动 fixture。

## 失败复现与局部修复

所有失败日志、合成对象及截图均保留，未删除或覆盖历史对象，未降低断言或放宽字号／裁切容差。

| 实际失败 | 复现与修复 |
| --- | --- |
| 新功能入口、前端 API／路由尚不存在 | 最小失败用例先红后绿；`885f0b681bc16b673ed8c232a5246890a3933931` 实现严格契约、事务和独立页面；初次完整测试发现接口层误用领域类型后改用应用契约 |
| 原生复选框偏移、未保存提示宽度和段落留白不一致 | 原项目与生产只读观测先失败；`ed8f4a2a9ae219100bc0b25f4517b4018be2d1bf` 局部承接原型 |
| 两倍文字按钮行高及焦点样式不一致，后续通用弹窗规则再次覆盖行高 | `0416d4f035c07bcba8ac74af4e95a7d13b57e730`、`e11807dc871e2b6369600faa478794e515467930` 和最终 `6206dec074854ae253d555a389b582d78091aade` 修复；按真实绘制稳定后读取焦点，保持原型边框和精确尺寸断言 |
| 第一轮真实浏览器第 10 组：历史关闭后不存在运行返回 404，而非 403 | 两项真实红绿服务用例证明存储查询早于功能裁决；`5eceb58a1cfd967e08d71cf8887d39d62d44dab8` 在读取运行及事件前检查历史功能，保留后续所有者、助手授权和发布版本检查 |
| 第二轮第 10 组：正确 `Accept: text/event-stream` 下拒绝响应变为 500 | 最小真实 MockMvc 请求复现媒体协商错误；`024779fe15a10ecf5ff7afbffaba3b2b6e58298a` 显式固定统一 JSON 错误类型，四类错误 HTTP 用例及原有三项脱敏测试通过；最终实际 JSON／SSE 均为 403 |

前两轮只通过第 1～9 组，不写成完整验收通过；第三轮重新使用正式接口创建新的隔离对象，13 组全部通过。诊断期间注入候选 CSS 的结果只用于定位，不作为最终验收；最终对照没有响应替换、拦截或候选样式注入。本轮没有自动审批容量拒绝，也没有因此未执行的必需验收。

## 最终源码与部署

| 验证 | 实际结果 |
| --- | --- |
| `./mvnw -q verify` | 342 份报告、1,074 项，失败／错误／跳过均 0；包含真实 PostgreSQL、Redis、事务、HTTP 及自动配置验证 |
| `make frontend-test` | 50 文件、258 项；typecheck、lint、构建通过 |
| 工作包工具 | Node 44 项、Python 19 项通过；验收结论提示修正后相关 7 项及语法检查通过 |
| `make architecture-guard` | 源码候选 48 项通过，依赖边界违规 0，手工分页精确债务仍为 4 |
| `make deploy-smoke` | 四个应用入口通过，Compose 七服务均 running／healthy |
| `make acceptance-work-package WP=WP-0018 SLICE=features` | 最终部署第三轮 13 组通过，包括并发单胜、旧版本、上限关闭／恢复、停用编辑、撤权、历史 JSON／SSE 拒绝和三尺寸布局 |
| `make acceptance-work-package WP=WP-0018 SLICE=account` | 最终部署账户回归 9 组通过，只在本轮隔离租户创建另一合成普通成员 |
| `make acceptance-work-package WP=WP-0017 SLICE=login` | 最终部署平台／租户登录回归 15 组通过，不创建租户 |

前端与后端分别按实际容器镜像及 OCI revision 核对。后端构建后至前端最终提交之间没有后端源码变化；验收提示修正仅改变结论文字，没有为该文字修改重跑开户或重建镜像。

| 服务 | OCI revision | 镜像摘要 |
| --- | --- | --- |
| console-web | 6206dec074854ae253d555a389b582d78091aade | sha256:dd87e8eb6feb7fc1a78641709e9743cc87625bc22878c0d6cb1c2605686057ad |
| control-plane | 024779fe15a10ecf5ff7afbffaba3b2b6e58298a | sha256:094f8b7a458afa2102c46e8ffbe441431f974b4bf0aeae0dbdb154c156f99502 |
| query-runtime | 024779fe15a10ecf5ff7afbffaba3b2b6e58298a | sha256:606f846fab3718a94a2b8d737a43d0a6d202f7d89383d28d4b134b400dd8f098 |
| ingestion-worker | 024779fe15a10ecf5ff7afbffaba3b2b6e58298a | sha256:420df4e7477b3dde6aba95f32171fbc653bcf99e2b211fc73565b096cd3c8fde |

没有把 HEAD 代替全部运行版本，没有重启 Docker、清缓存、数据卷或重置业务数据。

## 精确只读数据库事实

第三轮租户 `2108156322628239362`，功能成员资格 `2108156327682375682`，主体 `2108156327262945281`，冲突及对象追踪后缀 `391d2d78cf38`。事务为 `REPEATABLE READ READ ONLY`，只选择精确范围的状态、版本、计数及审计类别，最终 `ROLLBACK`，不读取秘密字段：

- 同名身份、主体资格及本租户管理员资格各 1；普通成员仅绑定 `TENANT_USER`。身份 ACTIVE／version 0；资格 ENABLED／version 8、permissionVersion 8，无待撤销标记。
- 四项保存值为 true／true／false／false，功能 version 6；上限恢复为四项 true，version 2；authorizationRevision 仍为 0。助手授权及查询运行均为 0。
- CREATE 1、FEATURES 6、DISABLE 1、ENABLE 1，匹配真实有效变化；无变化、取消、拒绝及并发失败未增加保存审计。
- 账户回归资格 `2108156565465858050` 为 ENABLED／version 2、permissionVersion 2，身份 version 1；CREATE／RESET／DISABLE／ENABLE 各 1，功能 version 0，助手 0。
- 本轮三次功能验收分别新增一个可追溯租户及其初始管理员；全局计数从 70／88 变为 73／91，符合正式开户次数，不声称全局管理员计数不变。第三轮相对其执行前只增加 1／1。前两轮失败对象和首片历史成员的范围事实逐项保持不变。

生产此处验证历史入口拒绝及无助手新查询拒绝，没有伪造已发布助手或已有问答。历史关闭不阻断有权新对话、已有运行和事件的授权正反例由 Maven 覆盖；完整真实问答／历史／引用正向旅程仍在 WP-0023／0024 联验，矩阵中的该组合操作保持 contracted。

## 原项目一致性

唯一设计来源为 OpenDesign Cloud 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`：[对话功能](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-member-features&state=success&review=1)、[项目源码入口](http://127.0.0.1:58153/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html)。端口可变，恢复依赖项目 ID、路径和固定提交。本轮未修改原型或新增原型副本。

原型入口／操作清单继续绑定 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`，布局 `47c4f6f31823617633ce0bac98074208781f5565`、目录 `d36c7f46d0187c0421d4989c12f601d5c190d73f`；当前固定源码为 `73d631a91e23306b46234133a87fd0714b10d533`。本轮实际原项目 HTTP 预览的 17 个依赖均返回 200，摘要与该已接受版本记录全部一致。收口时 MCP 元数据刷新返回 Transport closed；没有将其视为产品失败或修改原项目，最终身份核对使用仍可读取的原项目 HTTP 内容摘要，不使用生产副本替代原型。

原项目与生产分别观测功能页及未保存提示，3 宽度 × 2 文字大小 × 2 页面状态 × 2 来源，共 24 次。功能内容与提示的 200% 字号、键盘焦点／点击遮挡、reduced-motion、暖色画布、正文与标题字体、复选框偏移及行高、短按钮精确宽高、弹窗尺寸和段落留白均通过；业务写入 0。最终 24 张图逐图查看，截图仅在 `/private/tmp`。结构实现不同的自动最小尺寸不替代实际尺寸比较；没有新增视觉例外或放宽像素断言。

真实租户名称、登录名和勾选值不复制原型模拟事实；长名称按相同布局自然换行。此前接受的演示密码说明及尚未交付导航在对应功能阶段复验继续适用，不把完整九页／七态声称为本片已全部通过。此次逐图复核不是新的用户人工接受记录，也不声称完成原生桌面应用验收。

## 证据与工程过程

临时目录 `/private/tmp/wp0018-member-features.2OQfCw/` 为 0700，文件 0600。源码、生产、数据库、原型及失败复现集中清单 `evidence-manifest-closure.json` 包含 310 个非秘密文件，其中 176 张过程／最终截图，SHA-256 为 `fec39dd533d79998cd0c97c06b834f5eef62489a1c82a4d039a394f8098fe263`。最终通过依据只使用 `closure-focus` 的 24 张图，其余失败／诊断材料保留原状态。凭据及其摘要均排除；已知验收秘密对文本匹配数为 0。随后控制面检查日志单独按文件名定位。

主要结果为 `backend-verify-sse-summary.json`、`frontend-focus-final.log`、`features-browser-final-3.log`、`account-browser-final-closure.log`、`login-browser-final-closure.log`、`deployment-facts-final-closure.json`、`database-facts-validation-final.json`、`closure-focus-observations.json`、`parity-closure-focus.log`、`manual-feature-parity-review.json`、`original-source-facts-closure.json`；前两轮失败为 `features-browser-final-1.log`／`features-browser-final-2.log`。临时材料不替代本仓库的结果记录，复验使用固定源码和正式入口，不为文档检查重复创建业务对象。

当前插件环境为 Local Codex，个人工程助手 `2.2.0-rc.1+codex.local-20260929-051321`；显式加载 `engineering-assistant:wayfinder`、`codebase-design`、`tdd`、`code-review` 及 security-seams，使用工作包、ADR-0093 和固定 diff 内联执行。未创建 Runtime 或 StageRunResult，阶段结果校验不适用，不复用 WP-0017 Runtime。

CodeGraph 探索成员服务／Store、资格、有效访问 facade、客户端、路由、导航和查询历史服务；对 `MemberAccountStore`、`TenantMembership`、`ControlPlaneClient`、`AuthenticatedConsoleApp`、`QueryRunAuthorization` 及 `FoundationExceptionHandler` 执行影响追踪。结论用于复用现有组织裁决、保持独立功能／授权事实、在查询读取前拒绝历史关闭，以及识别统一异常影响三个运行进程；因此同步构建部署三个后端，而非只修浏览器页面。源码评审固定输入见 `source-review-delivery-input.json`，不以图谱或评审替代实际测试。

## 控制面验收与恢复前沿

本次只将 `tenant-member-features:0` 及该入口标 verified，绑定上述完整验证提交和本证据。WP-0018 为 7 项 verified、12 项 contracted；`tenant-members:2` 和 `tenant-member-account:0` 仍包含未交付助手页，保持 contracted，不将局部通过提升为组合操作通过。STATE 的产品前沿与执行任务未变化。

控制面提交前已执行 `make decisions-sync`、`make project-status`、`make project-verify`、实现矩阵单独测试、`make architecture-guard` 和 `make resume-context`：治理 77 项／30 个任务、矩阵 24 项、架构 48 项通过，历史资料 650 项按原 Git 对象核验；恢复输出 10,472 字节，小于 12 KiB，并明确下一动作。初次矩阵检查因本证据把“验证提交／前端运行提交”合并成字段而拒绝绑定，已拆成两个现有字段后通过，没有修改守卫。失败日志 `project-verify-closure.log`／`matrix-closure.log` 保留；通过日志为 `project-verify-closure-final.log`、`matrix-closure-final.log`、`architecture-closure.log`、`project-status-closure.log` 和 `resume-context-closure.log`。未为控制面检查创建业务对象。

本片完成后下一动作是第 3 片助手／策略统一授权的设计与最小失败用例；本轮不开始实施。第 4 片固定角色／完整租户工作区、跨包正向旅程、九页十九操作整包验收及 M6 人工接受仍未完成。WP-0018 四片和整包门禁全部闭合后才进入 WP-0019。
