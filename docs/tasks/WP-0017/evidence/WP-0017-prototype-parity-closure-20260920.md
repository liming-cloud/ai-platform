# WP-0017：原子开户部署后原型一致性收口

日期：2026-09-20。本独立切片功能验收及原型一致性验收通过，绑定实际前端 `0a46a64b9105574a86b64facb513be20be20f01e`。结论只覆盖平台管理员原子创建租户及初始管理员、三步开户、成功概览的已交付事实和登录入口；WP-0017 整包、66 页生产实现、M6 均未完成。

## 范围与决策

开始及收口时复核 Charter、ROADMAP、STATE、WP-0017 和引用的 12 项 ADR，运行 `make project-status` 并确认原 `lastVerifiedCommit` 是 HEAD 祖先。M6 顺序仍为 WP-0017～0026，最后返回 WP-0014 人工复评；当前身份基座的结果不代表知识接入、检索、问答指标或生产就绪已经达成。

前次严格复验的两项冲突已由用户逐项确认，决定提交为 `ed46c51f1b4cf440cc8c0705b20afde27263ea1b`，见 [边界确认](WP-0017-parity-decisions-20260920.md) 和 [ADR-0080 补充](../../docs/adr/ADR-0080-atomic-tenant-onboarding.md)：

- 概览以固定原型为准，移除额外资源配额区；五项值及版本继续由正式 API 和数据库核验。
- 允许步骤圆圈在 200% 文字下随文字自适应；其他布局、裁切、键盘和触控断言不放宽。
- 真实身份、数据与安全按 ADR-0079/0080 承接，不复制原型模拟身份、演示密码或虚构驻留区域，不修改 OpenDesign。

概览的五页设置导航属于尚未冻结合同的后续操作 `platform-tenant-detail:0`，仍为 `pending-contract`，整个概览入口仍为 `in-progress`；未用不可执行按钮伪造后续设置交付。本次不把上述操作或未交付全平台导航计为通过，也不以此声明整个概览页面或全站逐像素一致。

## 实际部署与固定原型

| 对象 | 本轮核实事实 |
| --- | --- |
| 分支 | `codex/trusted-rag-foundation-v1` |
| 前端源码及 OCI revision | `0a46a64b9105574a86b64facb513be20be20f01e` |
| 前端镜像 | `sha256:5e69010249341a7d13e6eee70196410803ce5776abec3f0f8f427d3cd9f547c5` |
| control-plane 后端实际 revision | `5844fd3ba2b5909161b704df5fc7e1ff026d4a0a` |
| control-plane 后端镜像 | `sha256:287c22b27fc900992fb3bc18242e1f517f140d60109f2e71e679a9afdcbc4108` |
| Compose | console-web、control-plane、ingestion-worker、query-runtime、postgres、redis、minio 七服务均 running/healthy；验收后再次只读确认 |
| 冒烟 | `make deploy-smoke` 四入口通过 |
| 原型 | OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`；已验收提交 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f` |

[OpenDesign 项目入口](http://127.0.0.1:64093/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html)；[本轮只读预览](http://127.0.0.1:64074/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html)。对照入口为 `platform-tenant-create`、`platform-tenant-detail`。本地链接仅在该服务会话内有效，失效后凭项目 ID、入口和固定提交重新获取。

构建采用 `deploy/images/frontend.Dockerfile`、`APP=console-web` 及上述 OCI revision；部署只执行 `docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml up --detach --no-deps --no-build --wait console-web`。后端源码与 `5844fd3ba…` 的 `backend/` 无差异，后端运行版本没有被当前 HEAD 替代。没有重启 Docker、清缓存/卷、重置账户或删除历史对象。本轮受控审批成功，原 `Selected model is at capacity` 没有重现，没有因此尚未执行的验收项。

预览实际返回的三个渲染文件与已验收提交逐字节一致；本轮未修改原型。该校验只针对渲染文件，不把原型目录内后续治理文档也描述为固定提交的相同字节。

| 文件 | SHA-256 |
| --- | --- |
| `tokens.css` | `ad0cd644358eb724f3aa2261b2d86fe357bf10e7603bc3d21295132177d55385` |
| `prototype.css` | `2e627c22e4a9ce1d37ce90ce95a92673b1bc13d3ddbcbf4f51f5eb33d215efa4` |
| `full-platform.js` | `d650c1ae6c000263fd97be0d650f2c375ece3de8fb00998cb758955060a5adbe` |

## 最小修复与红绿证据

固定差异为 `ed46c51f1…` → `0a46a64b9…`，仅五个文件：`TenantOverview.tsx`、`tenant-overview-loader.ts`、对应单测、`tenant-onboarding.css` 和 `acceptance-onboarding.cjs`。

先在旧部署上用只读浏览器探针复现“概览仍包含固定原型没有的额外资源配额区”；同时新增配额服务不可用的最小用例，旧加载器错误阻断基本信息，结果为 1 失败、4 通过。随后移除额外区域及其专用格式化函数、样式和加载依赖，五项加载器用例全部通过。取消、跨目标读取归属、失效会话保护和管理员列表第 1 页/每页 1 条的边界保留。

代码图谱先探索 `TenantOverview`、`readTenantOverview`、`currentTenantOverview`，再对 `readTenantOverview` 执行深度 2 影响分析，返回加载器和组件两个文件中的四个符号。由此把过度读取的修复限制在概览调用链，没有删除公共 `getTenantQuotas` API 或变更后端合同。清理只涉及被移除区域自身的代码，不扩展到无关模块。

验收原有五项配额 UI 断言依据用户决定替换为“概览不存在额外配额区”，正式 API 的状态、五项精确值、版本及授权断言保留，数据库另行核对。未删除开户场景、降低裁切容差、放宽字号或跳过登录回归。

## 最终候选验证

| 命令/检查 | 本轮实际结果 |
| --- | --- |
| `make frontend-test` | 42 文件、219 项；类型、lint 和构建通过 |
| `node --test scripts/quality/work-package-login.test.cjs scripts/quality/work-package-onboarding.test.cjs` | 30 项通过 |
| `make architecture-guard` | 28 项及前后端门禁通过；未触及历史债务仍为 15 个 Java 内联 SQL 文件、3 个手工分页 |
| `./mvnw -q -pl backend/contexts/organization-access -am test` | 239 项组织上下文及 44 项上游测试通过，合计 283；失败、错误、跳过均为 0 |
| `./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false test` | AuthenticationApiTest 23 项通过，失败、错误、跳过均为 0 |
| `make project-verify` | 源码候选 38 项通过；控制面收口结果另见末节 |
| `make acceptance-work-package WP=WP-0017 SLICE=onboarding` | 最终部署 11 场景通过 |
| `make acceptance-work-package WP=WP-0017 SLICE=login` | 本轮新管理员及平台身份 15 场景通过 |
| `REPEATABLE READ READ ONLY` SQL | 精确对象 15 项符合，7 项期望 1、8 项期望 0 |
| 视觉观察 | 三步骤及概览 × 390/1024/1440 × 100%/200% × 生产/原型，48 个画面；24 个生产观察横向溢出均为 0，6 个生产概览额外配额区均为 0 |

11 个开户场景覆盖三尺寸前进/回退/刷新/取消、密码清除、单次提交、非默认配额、重复编码/登录名/旧请求/缺少 CSRF 失败关闭、错误回到对应字段和成功入口不切换身份。15 个登录场景覆盖两个入口互达、两角色三尺寸和文字放大、键盘、reduced-motion 与普通动效、刷新/退出/CSRF、已有身份错误入口及拒绝从错误入口建立新会话。新管理员没有平台权限。

## 本轮合成对象与只读事实

| 标识 | 值 |
| --- | --- |
| tenantId | `2101469519795978241` |
| subjectId | `2101469519795978242` |
| tenantCode | `wp17-05459f58d15d` |
| 冲突后缀 | `05459f58d15d` |

通过正式开户接口只新增本轮可追溯的合成租户及初始管理员，旧对象保留，未创建普通会员。临时目录 `/private/tmp/wp0017-close-20260920.bodqm8ad` 为 0700，新凭据文件为 0600，独占创建且没有覆盖输入文件。0600 表示仅文件所有者可读写；浏览器用它完成真实身份验证，不需要人工再次提供已有密码。证据未登记秘密内容、Cookie、Token 或密码摘要。

本轮使用前次受控凭据运行以下命令，随后以新文件执行登录。复验必须另建 0700 目录并使用尚不存在的输出路径，不原样重跑以覆盖本次凭据。

```bash
NODE_PATH=/Users/lingou/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules \
FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE=/private/tmp/wp0017-strict-20260919.6wdxi976/e62a76827/created-credentials.json \
FOUNDATION_ACCEPTANCE_OUTPUT_FILE=/private/tmp/wp0017-close-20260920.bodqm8ad/created-credentials.json \
make acceptance-work-package WP=WP-0017 SLICE=onboarding

NODE_PATH=/Users/lingou/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules \
FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE=/private/tmp/wp0017-close-20260920.bodqm8ad/created-credentials.json \
make acceptance-work-package WP=WP-0017 SLICE=login
```

SQL 使用既有[只读事实模板](WP-0017-prototype-parity-deploy-20260919.md#只读事实复现)，替换为本节精确标识及冲突后缀。本轮文件为临时目录中的 `deploy-facts.sql`，事务为 `REPEATABLE READ READ ONLY`，语句超时 5 秒，执行 `psql -X -v ON_ERROR_STOP=1 -At`，只输出计数和状态。

| 事实 | actual / expected |
| --- | --- |
| `subject_active`、`anchor_disabled`、`lifecycle_active_v1`、`administrator_enabled`、`administrator_role`、`quota_exact_v0`、`lifecycle_event_active_v1` | 各 1 / 1 |
| `no_platform_role`、`no_failed_subject`、`no_failed_anchor`、`no_failed_lifecycle`、`no_failed_membership`、`no_failed_role`、`no_failed_quota`、`no_failed_event` | 各 0 / 0 |

`quota_exact_v0` 同时核对 `4`、`1234`、`12500000000`、`75000000`、`2097152` 及版本 `0`。生命周期和事件均为 ACTIVE、版本 1；锚点 DISABLED 是 ADR-0080 第 8 项的预期事实，不是新租户禁用。失败前缀 `unused-`、`old-` 的身份及租户相关事实为零。

## 原型一致性结论与观察方法

只读观察器位于临时目录 `visual/observe.cjs`，复用生产验收的实际文字缩放函数，设置 `WP0017_PROTOTYPE_BASE` 为上述 OpenDesign raw 目录 URL，以本轮新凭据运行。观察器只填写并取消非秘密草稿、读取新租户概览，没有再次提交开户。每次捕获前等待真实 `h1`、目标路由和布局；截图屏蔽密码输入、原型演示密码说明及评审附加区。

人工对照了本轮正常字号的移动开户、桌面第三步与 1024 概览，以及 390 下 200% 第三步和概览等画面。暖色画布、16px 正文、Georgia/中文宋体标题、`R` 标识与“可信 RAG”、15rem 桌面侧栏及“租户管理”导航、三步卡片和安全保障区承接原型。桌面短按钮按内容宽度，正常“取消”54px、“上一步/下一步”68px，`min-width: 0`；移动按钮依原型布局伸展，并保留 44px 触控高度，没有 Fluent 默认 96px 最小宽度。

本片的原型一致性通过依据为固定版本文件、实际样式/结构断言、真实交互和人工画面对照共同支持，不以“48 张”本身推导通过。200% 验收针对约定文本选择器的实际字号翻倍，不是整个浏览器缩放或全 DOM 像素差分；原型的只读合成 DOM 与生产受保护控件并不相同。真实租户/管理员值替代演示值，无生产依据的驻留区域及演示密码按 ADR 不呈现，故不宣称相应卡片高度或整个页面像素相同。200% 圆圈自适应仅使用用户明确许可，不新增其他视觉例外。

裁切、触控、焦点及键盘操作以正式浏览器断言为准；全页截图中的固定操作栏不能单独证明其滚动期间无遮挡。三尺寸、200% 文字、Tab/Shift-Tab/Space/Enter 和 reduced-motion 场景均已实际通过。

## 工程助手与六维质量复核

环境为 Codex 本地已安装的工程助手 skills/MCP，显式使用 `engineering-assistant:wayfinder`、`engineering-assistant:tdd`、`engineering-assistant:code-review`，延续 safety-core、risk-approval、evidence-quality、document-governance、scope-control、team-standards 的控制面约束；未使用 Hermes 后备命令。本次按仓库规范和用户确认的结果两个轴检查上述固定差异。

本轮再次读取 Runtime `run-5b4368307c3d4be7`：revision 19、`status=completed`、`current_node=null`，quality/review 已在 9 月 19 日旧范围完成。没有可执行的活动 quality 节点；按当前 code-review skill 内联合并下列六维判断，未创建新的 StageRunResult/QualitySnapshot，阶段 schema 校验不适用。旧 `source-onboarding-prototype-parity-audit` 及旧 quality 不作为本候选全绿证据。

| 维度 | 当前差异结论与依据 |
| --- | --- |
| 正确性 | 通过：两项决定落实；真实 11/15/15、前端 219、脚本 30 项支持；配额服务故障不再阻断无关概览事实 |
| 复杂度 | 通过：删除展示和读取依赖，保留既有异步生命周期，没有新增抽象或状态 |
| 冗余 | 通过：仅移除额外区专用格式化、样式和读取，不复制配额真值或模拟身份 |
| 性能 | 通过：概览从三个并发读取缩为两个，管理员仍限 1 条；没有据此声称 SLA 或全局性能达标 |
| 架构 | 通过：图谱限定两个文件的读取链，公共 HTTP、领域、权限、持久化及会话保护不变；架构门禁通过 |
| 测试质量 | 通过：先单测及旧部署探针红灯，再相同意图绿灯；按已接受决定替换 UI 断言，正式 API、DB、裁切及安全反例保留 |

未在当前差异发现未关闭缺陷。历史内联 SQL、手工分页扫描覆盖和后续设置合同仍留在所属 WP 边界，不用本片通过宣称全仓无债务。

## 可复核日志与收口

下列文件均在上述 0700 临时目录，表中仅对非秘密材料计算 SHA-256。截图仅保存在该目录的 `visual/`，没有提交仓库，也没有对凭据文件生成或登记摘要。

| 材料 | SHA-256 |
| --- | --- |
| `onboarding.log` | `d87ceb9dcf7e671326dbea0bb08da10c58ad05c9c72f52698ed276bd9622d92c` |
| `login.log` | `2a1db99d3a8fd41da001ff37160e0dea1a514e12bea971661b8e6ff950bea9fc` |
| `deploy-facts.sql` | `f8bfcbb90f9211d4762f37cbe7aac5cd6575d83d24d61eddc8752772686ddbe2` |
| `deploy-facts.log` | `44efc6b92b5d0d2aecded57784656dac35514564d699f7c7301b9fdfe3b540bb` |
| `runtime-facts.json` | `3d3c1c260cea9eb316a889dfb9e783ae1b74eb7f6f4987771fc23a99143659d1` |
| `visual/observe.cjs` | `b4cc1ec2620025b47a10b98028b968d2aa575b191dbff3f2f3e512cf724d8175` |
| `visual/visual-observations.json` | `2caed61964eff6df494788a21363e9fc49951cb4fdbc5a81964ede76fb442632` |

只刷新矩阵中已验证的 `platform-tenant-create:0`、`platform-tenant-create:1`、`platform-tenant-detail:1` 的证据和验证提交；`platform-tenant-detail:0` 不提升。STATE 的 `lastVerifiedCommit` 更新为实际前端源码 `0a46a64b9…`，不是后续证据提交，也不是后端运行版本。历史审计及未通过记录保持原样，由本文件给出新的执行结论。

控制面登记后 `make project-verify` 38 项通过，`make resume-context` 成功，输出 10602 字节，小于 12 KiB；恢复上下文指向新源码及证据，保留 WP-0017、M6 进行中和下一切片合同前沿。结果保存在临时目录的 `closure-project-verify.log`、`closure-resume-context.log`，`git diff --check` 通过。

五项用户 fixture 暂存重命名经二进制暂存 diff 比较保持原样；当前受控光栅图片数为 0。提交只包含本证据、INDEX、STATE、WP-0017 与三个原有 verified 操作的矩阵证据更新。本切片关闭后只进入“确认下一个独立设置切片的专属 ADR、保存边界和反例”的前沿，本轮不实现后续功能；WP-0017/M6 状态保持进行中。
