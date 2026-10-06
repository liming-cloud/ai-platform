# WP-0017：调整后完整范围的部署复核与收口

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0017 |
| 验证范围 | ADR-0092 调整后的完整 11 个入口、20 项生产操作；组合历史切片证据与当前部署集成复验 |
| 实施基线 | 8920e5d41e7ac25814a2f4386a2ca1d36b4e145b |
| 验证提交 | 2cca286848e174f5f7d3561712a9b1b86b382914 |
| 后端源码／运行提交 | a23ea28dca8dd32a36f235948a5e867ed1769def |
| 依据／日期 | ADR-0079、ADR-0080、ADR-0083～0092；2026-10-06 |
| 结论 | 调整后的 WP-0017 整包闭合；平台概览仍待 WP-0027，M6 未完成 |

## 接受边界与完整操作绑定

用户选择先冻结 M6 顺排调整，再推进 WP-0018。已接受 [ADR-0092](../../../architecture/decisions/ADR-0092-m6-platform-overview-late-delivery.md)，只将 `platform-overview`／`platform-overview:0` 的责任转交 WP-0027，仍属 M6、production、pending-contract；原型源、指针、操作文案、摘要及未验证状态没有改变。WP-0027 位于 WP-0025 后、WP-0026 前，不能靠责任转移免除平台概览或其 M8 运维事实冲突。WP-0026 仍只做全量复验。

本包完整范围如下，全部原验证提交均是当前候选祖先。实现矩阵仍保存每项合同、接口、反例及原证据，不用迁移日期改写原验收日期。

| 入口 | 生产操作数 | 原独立验收依据 |
| --- | --- | --- |
| console-login | 1 | [独立登录](WP-0017-independent-login-deploy-20260914.md)，本轮重新验证真实认证 |
| tenant-login | 2 | 独立登录、[三入口导航](WP-0017-login-entry-navigation-closure-20261001.md) |
| platform-tenants | 2 | [目录](WP-0017-tenant-directory-prototype-parity-closure-20261001.md) |
| platform-tenant-create | 2 | [原子开户](WP-0017-prototype-parity-closure-20260920.md) |
| platform-tenant-detail | 2 | 原子开户及[租户概览](WP-0017-tenant-overview-prototype-parity-closure-20261001.md) |
| platform-tenant-admins | 2 | [管理员设置](WP-0017-admin-settings-prototype-parity-closure-20260924.md)、[框架分页](WP-0017-administrator-framework-pagination-closure-20261004.md) |
| platform-tenant-quotas | 1 | [配额](WP-0017-quota-settings-prototype-parity-closure-20260928.md) |
| platform-tenant-features | 1 | [功能](WP-0017-feature-settings-prototype-parity-closure-20260928.md) |
| platform-tenant-security | 1 | [登录安全](WP-0017-security-settings-prototype-parity-closure-20260929.md) |
| platform-accounts | 5 | [平台账户完整操作](WP-0017-platform-accounts-prototype-parity-closure-20261001.md) |
| platform-roles | 1 | [固定角色](WP-0017-platform-roles-prototype-parity-closure-20261001.md) |

逐操作提交、证据路径及当前内容摘要在外部 `aggregate-operation-bindings.json` 核验。本轮核对历史切片之后的源码变化：后续专属设置、认证、目录、导航和分页变更分别由其更晚的独立生产证据承接；同时执行当前控制面 reactor 的事务／存储／自动配置／HTTP 回归与完整前端回归。相比已验收分页候选 `b33cb8e1bc536d71337c3d631df6017df9e28dd1`，当前前端仅有登录标题 CSS 的 10 行新增／6 行删除；相比运行后端 `a23ea28dca8dd32a36f235948a5e867ed1769def`，全部 backend 路径差异为零。当前没有未部署的账户、开户或设置业务源码变化。

真实写操作继续以原切片的固定版本及证据证明，本轮没有重跑开户、平台账户建立、重置或设置写操作，没有新增业务对象。以下只读集成复验不冒称新执行这些写场景；原证据的运行版本和结论原文均保留。

## 本轮反例与最小修复

- 顺排门禁先以 5 个失败测试证明漏检跳过 WP-0017、跳过 WP-0027、索引顺序及虚假整包完成；再以 1 个失败测试证明跨里程碑的已完成前置不能放行。增加固定顺序和 completed owner 的 production 操作全验证检查，生成索引将 WP-0027 排在 WP-0026 前，不改变其他完成门禁。
- 首次目录复验误用与当前凭据无关的旧租户；绑定真实资格租户后，精确 SQL 证明其有 2 名管理员，原脚本固定 1 的夹具假设仍失败。新增 2 项配置反例先失败，再增加 `FOUNDATION_ACCEPTANCE_ADMINISTRATOR_COUNT`，默认 1，显式输入必须是安全正整数。仍逐项断言精确数量，不改成非空、不制造样本或修改租户数据。
- 当前登录标题真实对照失败：生产表单 24px／650、前区块 28px／650 与原型 25px／500、移动 26.4px／500 不符。仅修正生产 `.governance-login__statement`、表单标题及移动字号，原型不变，保留真实 h1 语义。
- 对照曾错配原型前区块 h1 与生产表单 h1，以及 body 默认值与实际页面画布。按同一可见标题及实际容器纠正配对后仍保留真实字体失败。目录 `.workspace` 透明背景另与生产绘制背景错配，改为逐祖先观测第一个非透明画布，保持颜色精确相等、几何 1px 条件。一张登录截图前区块显示异常，补充矩形、visibility、内部滚动及重拍证明内容可见；未据此修改产品。所有诊断记录保留，没有把这些测量问题算产品失败或删掉真实断言。

## 实际部署与原型

| 进程 | 实际 OCI revision | 实际镜像 |
| --- | --- | --- |
| console-web | 2cca286848e174f5f7d3561712a9b1b86b382914 | `sha256:c741c9bb0067779be2f987cc5a7802e137370bb57f7b981e7d9c1418095ebd54` |
| control-plane，未更新 | a23ea28dca8dd32a36f235948a5e867ed1769def | `sha256:759e03c371cd640c041d69cb95a7a66c9c1da8139dd7b8e0c679e99df994a1ac` |
| query-runtime，未更新 | ef11d6aad0ffb8d8a656d5a17a3477d93b95a5c8 | `sha256:4a847f9631eee38204096f9ea66c159dcfcfbb25f17406ffcecab473795d5c5d` |

固定源码按现有 Dockerfile、`APP=console-web` 与 OCI revision label 构建，仅 Compose `up --detach --no-deps --no-build console-web` 更新前端；最终 7 服务 running／healthy、4 入口 smoke 通过。摄取镜像没有 revision 标签，不推断其提交。没有重启 Docker、清缓存、删卷或重置业务数据。

唯一权威为 OpenDesign Cloud 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`：[项目入口](http://127.0.0.1:49175/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html)、[正式原项目页](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=console-login&state=success&review=0)。本轮正式 MCP `get_project` 核实原项目注册及目录，真实浏览器直接读取原项目。入口／操作固定 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`、共享移动布局 `47c4f6f31823617633ce0bac98074208781f5565`、最新目录 `d36c7f46d0187c0421d4989c12f601d5c190d73f`；最新接受版本后原型资产差异为零。没有建立副本或修订原型。

## 最终候选的真实验证

| 检查 | 结果与范围 |
| --- | --- |
| `make frontend-test` | 48 文件／246 项；typecheck、lint、构建通过 |
| 控制面 Maven reactor | 18 测试模块／1015 项，失败、错误、跳过均 0；仅按本轮开始后 Surefire 报告统计，不冒称所有启动模块复验 |
| Node 配置／Python 切片入口 | 36/36、16/16；未指定或未知 SLICE 仍拒绝，保留 SETUP 和凭据边界 |
| 真实 login／login-entries | 15/15、18/18；角色、Cookie/CSRF、刷新、退出、保留身份、键盘、三尺寸及导航通过 |
| 真实 tenants／overview／roles | 15/15、15/15、11/11；真实后端范围、过滤、分页、目标、迟到／失败、导航及越权反例通过 |
| 最终管理员只读目录 | 16/16；既有 2 名管理员分页／筛选／越界／权限／候选空结果，业务写入 0 |
| 当前 11 入口的公共视觉 | 77/77：390／1024／1440 正常及 200% 文字、另 390 reduced-motion；主题、标题、正文、真实画布、侧栏与横向溢出断言通过，差异／业务写入／阻断写入／页面异常均 0 |
| 管理员严格原型复验 | 7/7；标题／导航／摘要／表头／分页／短按钮字体、颜色、内边距及几何，原 1px 容差；溢出及裁切 0，键盘与表头语义通过 |
| 人工截图 | 查看移动登录原页／生产、桌面租户登录／目录、平板 200% 配额及移动 200% 管理员原页／生产；截图只在 `/private/tmp` |
| PostgreSQL 事实 | 17 组计数／状态，REPEATABLE READ READ ONLY、`transaction_read_only=on`；前后逐字及摘要相同 |
| 架构／控制面 | architecture-guard 48 项通过、依赖违规 0、精确分页债务 5；治理 77 项与真实 30 任务／650 历史定位校验通过；status／resume 通过，恢复输出 5833 字节，小于 12KiB |

Maven 命令：`./mvnw -q -pl backend/contexts/organization-access,backend/platform/platform-persistence,backend/bootstrap/control-plane-bootstrap -am test`。最终登录和只读切片均通过 `make acceptance-work-package WP=WP-0017 SLICE=<明确切片>` 串行运行，凭据由独占普通 0600 文件读取。旧凭据路径不存在后，复用用户已经明确授权的真实身份安全输入，在新 0700 目录建立独立 0600 文件，未要求用户再次提供或覆盖旧文件。正文、日志及 Git 不记录秘密或其摘要。

目录／概览目标是当前真实凭据唯一租户的已核实事实，精确管理员数量 2；管理员视觉和分页分别复用原单管理员及双管理员样本。PG 前后确认租户 70、身份 189、平台账户审计 126、成员及资格各 88、平台绑定 101 均未改变。首次最终 SQL 使用 `-At` 导致输出格式差异，数值未改变；保留记录后按原对齐参数重跑，得到字节一致，不放宽事实比较。

77 组只证明公共主题／标题／布局；页面特有操作、七态、编辑取消、保存、撤销与完整控件一致性仍由上表各原切片及当前回归组合证明，不将单一视觉检查冒称全部功能或逐像素整页比较。原型演示身份与真实业务事实按合同区分；仅沿用 ADR-0083 明确接受的限定差异，没有新增视觉例外。本轮没有审批容量拒绝导致的必要场景未执行。

## 外部证据与方法

脚本、诊断、日志、非秘密指标和截图只在独立 0700 目录 `/private/tmp/wp0017-frontier-closure-gz9j0g15/`；秘密文件不列入摘要。

| 产物 | SHA-256 |
| --- | --- |
| order-red.log | `c059a183922fa69d496595f8517397630cb679b81adfcb8c0d26213d171e70b7` |
| milestone-red.log | `a300e0ebccdfc77d2efa6ad5d9d66dff4885c37adabb082ac1afcf00ce867f76` |
| directory-count-red.log | `d9f52fe2bcfc319299054be237ebf72f47bd77a4160f48246fd92eb889a4a1ff` |
| scope-visual-matched-regions-metrics.json | `f400d57b47dd9a7feee0a632a0ddc2bb13c86120e6f4d77fb9f05eb0346f987c` |
| frontend-login-typography.log | `56300776e609a74e7e6d1208a770efdcb9f6358c8852e388f76dd5ddc80def5b` |
| backend-summary.json | `88a1834296fb84932ca18d7028d1cab2594526e42fab8c20a9a6727ddfb16c96` |
| scope-visual-paint-final.cjs | `5d289d93e63ed7b47a8bb36973b73196f94d339ac1cd0ec682dc01d7dd8ca128` |
| scope-visual-paint-final-metrics.json | `9280d1df4bcf94e09b590ec130b606a7d7c581a9392d581a647cf8de8579e435` |
| login-postdeploy.log | `a92d4f8adecb17d1b089ec00629f7bb9bc47660fb32429ebb527ffdf8fab9244` |
| readonly-postdeploy-summary.json | `4c7d643fc8e9b1f769cdfbaa72cad7e673b7677910eeae4466a680dc24502caf` |
| parity-postdeploy-metrics.json | `22a532c1883aaa77d4440e84703137bfba7eaf1ad949a509e9a9e995c5952c3c` |
| aggregate-operation-bindings.json | `ae53b4a6d706381b3547de974ffc80fef8f571276a87109a8aff68c50fd2c429` |
| runtime-postdeploy.json | `afd8b0f0da23b844f0bd30eeebee52e4fbbda5216922596b6110a44b819db91b` |
| facts-before.txt／facts-final.txt | `5a002850d92178a1363c7a915a97e3c1ff8cc3b1f590cc9049205dd3acc8f47b` |
| postdeploy-checks.json | `9a3026f358a2f00300fddbb9aa75396991fb2b958c604f9ba82453f44d81bf42` |

Local Codex 的个人工程助手 `2.2.0-rc.1+codex.local-20260929-051321` 显式加载 `wayfinder`、`decision-interview`、`tdd`、`code-review`、`diagnosing-bugs`；控制输入为根 AGENTS、Charter、ROADMAP、STATE、本包及其全部 ADR。内联工作没有 Runtime／StageRunResult，阶段结果校验不适用，不复用旧 Runtime 宣称全绿。

CodeGraph 探索治理 `verify`、`render_task_index`、状态／任务解析以及 `BrowserSessionGate`、租户目录／读取适配器和验收配置；`render_task_index` 深度 2 影响分析 5 个关联符号、`BrowserSessionGate` 2 个关联符号。其结论使修改限定于既有完成顺序、索引、登录呈现及只读夹具，不改认证状态机、写事务或新建抽象；编译与真实浏览器仍作为证明。固定 diff 评审核对完成门禁、不同进程版本、明确源提交、严格数量及样式断言，未发现本范围剩余阻断。

## 下一前沿

调整后的 WP-0017 完成证据绑定候选提交后，STATE 转向 WP-0018 的专属生产契约设计；成员开户、账户、功能和统一助手授权尚未交付，先调查并接受 ADR 再实施。平台概览由 WP-0027 完整承接，原型保持不变；WP-0018～0025、WP-0027、WP-0026 和 WP-0014 人工复评仍未完成，不能声称 66 页生产实现或 M6 完成。

激活 WP-0018 后，历史处置校验先拒绝仍指向旧 backlog 的现行替代位置。仅将该条 `destination` 改为 `docs/tasks/WP-0018/README.md`；原 `project/work-packages/WP-0018.md`、来源提交、内容摘要及历史原因保持原值，650 项原对象仍完整校验。没有放宽历史定位门禁。

五项 fixture 已由 GOV-0002 正式提交，本轮 fixture 差异及仓库截图新增均为零。其余 5 条精确分页债务仍分别归 WP-0018／0019／0020，不借本包扩大整改。最终文档交付提交与 GitHub CI 在推送后另行核实，不以旧提交 CI 代替当前结果。
