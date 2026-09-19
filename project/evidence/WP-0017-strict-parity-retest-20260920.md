# WP-0017：部署后严格原型复验与待决冲突

日期：2026-09-20（会话始于 2026-09-19）。当前切片：平台管理员原子创建租户与初始租户管理员。

## 结论

前端 `e62a7682722661c5b778a74cdda51eab0ec4e568` 已实际部署，11 个开户、15 个新管理员登录场景和 15 项精确对象只读事实通过；三尺寸、正常/200% 文字、键盘与 reduced-motion 检查通过。固定原型/生产共 48 个画面完成只读观察，生产横向溢出为 0。

**严格原型一致性仍未通过，当前独立切片尚未整体关闭。** 下文的两个要求冲突仍待用户决定，不自行豁免差异，不修改原型迁就生产，不把功能门禁当成完整视觉通过。本文件新增记录本轮事实，不改写旧审计或旧执行记录。

WP-0017 整包、66 页生产实现、M6 均未完成。下一前沿仍在原子开户一致性验收，未开始独立管理员管理、配额编辑、功能开关、登录策略、普通会员、模型、知识接入或查询。整体顺排规划已回顾：WP-0017 身份与开户 → WP-0018 成员 → WP-0019 模型 → WP-0020 知识接入及后续工作包，最终回到 WP-0014 人工验收；本轮没有改变此顺序。

## 精确版本与真实部署

| 对象 | 事实 |
| --- | --- |
| 仓库与分支 | `/Volumes/slm-ssd/projects/ai-project/ai-platform`；`codex/trusted-rag-foundation-v1` |
| 最终前端 OCI revision | `e62a7682722661c5b778a74cdda51eab0ec4e568` |
| 最终前端镜像 | `sha256:2d5843b77ec2ef940e32f5fefdc73c0ca7de08b08b3f64886bd43e03252f1072` |
| 后端实际 OCI revision | `5844fd3ba2b5909161b704df5fc7e1ff026d4a0a`，本轮未重建或替换 |
| 后端实际镜像 | `sha256:287c22b27fc900992fb3bc18242e1f517f140d60109f2e71e679a9afdcbc4108` |
| Compose | console-web、control-plane、ingestion-worker、query-runtime、postgres、redis、minio 全部 running/healthy |
| 冒烟 | `make deploy-smoke` 四入口通过 |
| 固定原型 | OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，已验收提交 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f` |

[OpenDesign 项目与入口](http://127.0.0.1:64093/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html)；[本轮固定原型预览](http://127.0.0.1:64074/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html)。链接由本地 OpenDesign 工具提供，仅在服务会话内有效；失效时用项目 ID、`index.html` 和固定提交重新获取。

构建使用 `deploy/images/frontend.Dockerfile`、`APP=console-web` 及上述 revision label；仅执行 `docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml up --detach --no-deps --no-build --wait console-web`。没有重启 Docker、清理缓存/卷、重置账户或删除历史验收对象。

## 源码与部署验证

| 验证 | 本轮结果 |
| --- | --- |
| `make frontend-test` | 42 文件、218 项；类型、lint、构建通过 |
| 两个 Node 工作包验收脚本单测 | 30 项通过 |
| `make architecture-guard` | 28 项及前后端门禁通过；未触及历史债务仍为 15 个内联 SQL 文件、3 个手工分页 |
| `make project-verify` | 源码候选与控制面登记后均为 38 项通过 |
| `make acceptance-work-package WP=WP-0017 SLICE=onboarding` | 精确最终部署 11 场景通过 |
| `make acceptance-work-package WP=WP-0017 SLICE=login` | 本轮新管理员 15 场景通过 |
| `REPEATABLE READ READ ONLY` SQL | 15 项全部符合，7 项期望 1、8 项期望 0 |
| 只读视觉观察 | 4 个画面 × 3 个尺寸 × 2 个倍率 × 生产/原型；48 次，生产横向溢出 0；不等于严格一致性整体通过 |

开户场景包含原型样式与响应式结构、三步前进/回退/刷新/取消、密码清除、一次提交、非默认配额读取、重复编码/登录名/旧合同/缺少 CSRF 失败关闭、错误回到对应字段、成功入口不切换平台身份。实际 Tab/Shift-Tab/Space/Enter、触控下限、真实字号等待、裁切及 reduced-motion 均保留原断言，没有降低容差或删除步骤。

后端源码与运行镜像均未改变。本轮没有把旧 Maven 结果冒充新执行，也没有用 9 月 14 日或旧前端的浏览器结果替代上述最终候选结果。

## 差异修复与失败证据

- `8802b8708` 承接共享页头、导航、开户卡片与响应式骨架。正式浏览器在 390px/200% 的固定步骤圆圈上失败，尚未提交开户；`ac4dd9531` 使圆圈随内容增长后通过。固定原型同处仍采用 24px，形成下文待决冲突。
- `6fdd1d092` 消除 Fluent 标签/帮助的额外间距，并接入真实管理员摘要与现有导航分组。没有新增管理接口或后续页面。
- `c72ad7855` 修正被通用按钮覆盖的导航字号、图标槽和状态色。新反例先在单测和真实部署失败，再转绿；桌面/平板导航的实测字号与图标布局承接固定原型。
- `d98b42bbc` 修正概览平板单列、移动事实单列、返回按钮铺满、触控登录入口，以及标题区与事实区的结构。390/1024 的旧部署结构断言先失败，局部修复后通过。
- `86ded41ae` 修正验收脚本仅在 200% 路径等待视口布局的遗漏：正常字号先取旧尺寸导致正式验收失败。新单测 1 项真实红灯后转绿，同一浏览器的即时/稳定路径均通过；没有改宽度或高度断言。
- `b6eb68817` 修正移动摘要两列、确认控件、计数与菜单呈现，并让页头摘要作为实际文字节点随字号放大。移动摘要列数先在真实旧部署失败，再通过。
- `01c9a0ba4` 修正确认框宽度、第三步间距和只读权限外观；1024px/200% 暴露只读框 44px、同排输入框 46.796875px 的真实回归，发生在提交开户前。`405e6c2fd` 按同排正文行高约束只读框最小高度，样式实验两者均为 46.796875px，最终完整 11 场景通过。原 1px 对齐阈值不变。
- `a75b73d38` 消除 Fluent `max-width: fit-content` 将 326px 确认分区缩成 240px 的默认限制；新增完整分区宽度断言在旧部署先失败，最终完整验收通过。
- `e62a76827` 根据真实计算样式修正只读权限值 14px→16px、摘要辅助文字 13.3333px→14px、步骤数字反白色；输入行高统一为原型的 1.5，同排控件在 200% 下同步增长。创建按钮通过“创建中…”的等宽忙状态文案从 96px 回到原型 82px，保留共同预留宽度与防重入。正式断言新增三项字号/颜色与按钮尺寸检查，没有删改原有阈值。一次源码门禁指出局部引用 Fluent 品牌色，随后改用共享语义令牌转绿，未放宽门禁。

临时诊断中曾使用旧徽标 DOM 定位，另一次只读 SQL 准备时误取凭据结构中的租户编码位置；都在执行目标断言/数据库查询前停止，随后更正定位。它们不计为产品通过。失败日志与各轮对象均保留，最终结论只绑定本文件的精确版本及对象。

## 已核对的视觉事实与尚待决定的边界

正常正文为 16px，标题为 Georgia/中文宋体回退，暖色表面 `rgb(250, 249, 245)`，正文 `rgb(20, 20, 19)`；标识为 R、“可信 RAG”，入口为“租户管理”。1440px 侧栏 240px，1024px 侧栏 80px，390px 使用原型顶部导航布局。正常桌面取消宽 54px、上一步/下一步宽 68px，计算 min-width 为 0；普通控件桌面 36px、手机 44px。

三个尺寸正常字号的第一步卡片位置、宽高、内边距和间距采样与固定原型相同；移动确认摘要保持两列，桌面摘要在表单右侧。概览使用真实租户状态与管理员摘要，平板/手机的安全保障区位于基本信息下方。安全保障三条文字在生产 200% 检查中实际达到 32px，并检查溢出和裁切。已查看桌面开户、平板概览及移动正常/200% 截图；固定操作栏长截图的拼接位置不等于实际焦点遮挡，遮挡使用正式命中与键盘断言判定。

两个要求冲突已向用户提出，尚无回复：

1. **概览额外资源配额区。** 固定原型概览没有该区，旧生产验收却要求在概览逐项显示五项配额。建议按原型移除额外展示，保留开户 API、精确版本和只读数据库五项值核验；在取得确认前，界面和旧断言均保留，没有静默删除验收步骤。
2. **200% 文字下原型固定圆圈裁切。** 固定原型在 390px 仍将步骤圆圈设为 24px，放大后的文字超出；生产按“不得放宽裁切断言”要求增大圆圈。完全复制此固定尺寸与无裁切要求冲突。建议保留无裁切适配并明确登记，未经确认不将其作为自行豁免或宣布严格一致性通过。

200% 观察使用明示元素集合放大实际字体，并验证应用与恢复，不冒充浏览器整体缩放或逐像素比较。原型只读权限值与生产使用不同 DOM 控件，不能用未匹配的文字节点采样推定整页一致。原型还包含演示密码提示，生产没有默认密码，不复制该演示说明；相应高度差没有被表述为完全匹配。未实现的独立设置页签、运行/备份导航，以及无真实合同的演示区域/姓名不被伪造成已交付事实；它们仍由相应 ADR/工作包负责。

原型 `tokens.css`、`prototype.css`、`full-platform.js` 的预览 HTTP 字节与已验收仓库版本相同，未修改原型。相对固定提交，目录内历史 `ACCEPTANCE.md`、`ACTION-LEDGER.json`、`DESIGN.md` 有验收/设计记录差异；本轮 `50d15bb…` 至当前候选的整个原型目录没有修改，不把三个渲染源的一致扩大为全目录逐字节相同。源摘要如下：

- `tokens.css`：`ad0cd644358eb724f3aa2261b2d86fe357bf10e7603bc3d21295132177d55385`
- `prototype.css`：`2e627c22e4a9ce1d37ce90ce95a92673b1bc13d3ddbcbf4f51f5eb33d215efa4`
- `full-platform.js`：`d650c1ae6c000263fd97be0d650f2c375ece3de8fb00998cb758955060a5adbe`

## 精确对象、凭据与复现

最终 tenantId `2101341054295867394`，subjectId `2101341054295867395`，tenantCode `wp17-41e202be234d`，冲突后缀 `41e202be234d`。15 项 SQL 只核对主体、租户锚点/生命周期、管理员成员/角色、五项非默认配额、生命周期事件和冲突对象残留，不选择密码摘要、Cookie、Token 等秘密字段。

SQL 复现沿用 [历史记录中的只读事实模板](WP-0017-prototype-parity-deploy-20260919.md#只读事实复现)：将旧 tenantId `2101311718205423618`、subjectId `2101311718205423619`、后缀 `5334f4fb8859` 分别替换为本段精确值；保留 `BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY`、5s 超时及 15 项断言。通过既有 postgres 容器的 `psql -X -At -v ON_ERROR_STOP=1` 执行，连接账号从容器既有环境读取。最初指定的临时 SQL 路径已不存在，本轮从保留模板重建，未扩大查询字段。

`0600` 指仅文件所有者可读写，父目录使用 `0700`。本轮自动复用已有受控凭据，不需用户人工提供秘密；每轮输出到新目录，拒绝覆盖旧凭据。最终临时目录为 `/private/tmp/wp0017-strict-20260919.6wdxi976/e62a76827`；凭据不提交、不打印、不列入摘要清单。独立 Chrome 与 Docker 均通过正常受控审批；历史 `Selected model is at capacity` 本轮未重现，当前没有因此尚未执行的验收项。

重新执行时建立新的 0700 临时目录，以 `FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE` 指向仍有效的 0600 凭据；`FOUNDATION_ACCEPTANCE_OUTPUT_FILE` 指向新目录内尚不存在的文件。设置 `NODE_PATH=/Users/lingou/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules`，先执行 `make acceptance-work-package WP=WP-0017 SLICE=onboarding`，再用新输出执行 `SLICE=login`。不可复用旧 tenantId 声称新一轮通过。

视觉复现串行使用生产/原型独立上下文：三尺寸 390×844、1024×960、1440×960，三个开户步骤只填非秘密草稿后取消，再读取本轮精确概览；每个画面验证唯一 h1/路由和实际字号，遮罩密码控件、演示帮助及审查器，截图只放 `/private/tmp`。最终观察脚本为临时目录中的 `visual/observe.cjs`，对应生产源与仓库正式验收 helper；临时文件失效后按此步骤重建。

本轮早期已创建对象均保留；以下仅用于追溯，不以凭据记录代替最终只读事实：

| 轮次 | tenantId / subjectId |
| --- | --- |
| `ac4dd9531` | `2101321102985789441` / `2101321102985789442` |
| `6fdd1d092` | `2101323690330292225` / `2101323690330292226` |
| `c72ad7855` | `2101327333859262465` / `2101327333859262466` |
| `d98b42bbc` | `2101329978653147138` / `2101329978653147139` |
| `d98b42bbc-final` | `2101330774295838721` / `2101330774295838722` |
| `b6eb68817` | `2101333305377947649` / `2101333305377947650` |
| `405e6c2fd` | `2101336002185396226` / `2101336002185396227` |
| `a75b73d38` | `2101338928463216642` / `2101338928463216643` |

`8802b8708` 与 `01c9a0ba4` 在开户提交前失败，没有创建租户；不得清除其诊断日志或覆盖其凭据文件。

## 工程助手、图谱与六维复核

本轮环境为 Codex 本地安装的工程助手 skills/MCP，显式加载 `engineering-assistant:wayfinder`、`engineering-assistant:tdd`、`engineering-assistant:code-review`。wayfinder 路由采用内联推进；使用 safety-core、risk-approval、evidence-quality、document-governance、scope-control、team-standards 控制面，按 SKILL.md 进行导航、红绿和固定差异复核，未以 Hermes 命令替代现有能力。

Runtime `run-5b4368307c3d4be7` 已在前次范围完成，旧 `source-onboarding-prototype-parity-audit` 与 quality 结果不覆盖本轮新严格要求。当前为内联六维复核，未生成新的 StageRunResult/QualitySnapshot；阶段 schema 校验不适用，不写新的全绿或复用旧节点冒充通过。

图谱已探索 ProductShell、ConsoleApp、TenantProvisioningFlow、TenantOverview、管理员摘要读取及导航能力路径，并在共享外壳/导航改动前分析调用影响。由此保留单一账户菜单、两个真实调用方共用 PageHeading、复用 `listTenantAdministrators` 的页码/总数合同读取一条摘要；未新增独立管理员管理接口。图谱中通用 `verify` 同名匹配有歧义，CJS/CSS 覆盖有限，采用已定位文件精读与真实浏览器补足，不把局部边数表述为全仓影响证明。

| 维度 | 结论 |
| --- | --- |
| 正确性 | 原子开户与登录功能证据通过；严格一致性仍有两项待决冲突，不整体标绿 |
| 复杂度 | 共享页头、单一菜单和局部样式；没有新增服务、状态机或后续设置能力 |
| 冗余 | 不复制原型模拟状态或密码库；复用现有身份与管理员列表 API |
| 性能 | 概览并行读取既有资源，管理员摘要限定 pageNumber=1/pageSize=1；未新增轮询，不把本机计时当作 SLA |
| 架构 | 未改变后端合同、角色/租户隔离及四层依赖；只处理触及路径，保留未触及历史债务 |
| 测试质量 | 真实失败、局部修复、同一断言转绿；等待实际布局/字号，保留阈值与旧对象；视觉未关闭不伪造完成 |

## 截图治理与控制面

本轮已在 `c74a4eb5d` 移除 41 张历史受控截图并加入索引/工作树截图门禁，见 [恢复与截图治理](WP-0017-strict-parity-recovery-20260919.md)。当前树受控截图为 0，历史 Git 对象只用于追溯，不重写历史。此文件只保存文字、原型链接及日志摘要，不含截图。

用户五项 fixture 暂存 R100 重命名始终与会话开始的二进制暂存差异逐字节相同，不提交、撤销或删除。STATE 继续保留 `lastVerifiedCommit=670d13e58432a7f0b4dc4bd54ae75921bfd48277`，当前已部署功能事实在本文件单独绑定；严格冲突解决前不把验证前沿推进为通过，不提升实现矩阵或整包状态。

控制面登记后 `make project-verify` 38 项通过，`make resume-context` 成功，输出为 10285 字节（小于 12 KiB）；恢复上下文明确保留 WP-0017、原 `lastVerifiedCommit` 和两项待决冲突。提交只包含本证据、INDEX、STATE 与 WP-0017，部署镜像继续绑定源码提交而非后续文档提交。

## 非秘密日志指纹

路径相对于本轮临时根目录。仅计算日志、SQL 与观察报告摘要，明确排除凭据文件。

| 文件 | SHA-256 |
| --- | --- |
| `typography-red.log` | `a8a6f766cadb2fcdcaba13027cdfa451fd5a5a3bbab646a0c62bbd8c2d6b3ea1` |
| `typography-frontend-test.log` | `25670310cb0623448a8da5c2f06cd35e7b0d4cb345aeaf984e7ac8c90bb372f6` |
| `typography-final-frontend.log` | `60e70a42a3a792f6cf12484d539f53c77824baa3b7899f4cc663f1806c443f41` |
| `navigation-red.log` | `16c60681e5c7ad1b09ba8a1130b63cc44b3c166bb46c86ade83bd0ac826635b2` |
| `navigation-deploy-red.log` | `30da9f96d1683bd1def40dc9fc0bf327a5d17b4a48a642934ce0de5eb9bfffcd` |
| `overview-frame-red.log` | `1041c1a3e3a57d9c237f1c2ee656c92e3d9bb767aa10c2dbe0c35f2e4c453e25` |
| `summary-red.log` | `eb7e6d9e61b8b130dbface405540a02c1f6b05afe89e7d6cda0e8c2c4459b208` |
| `405e6c2fd/consent-width-red.log` | `920f869b6ae01cc258c9cf08b9973afe2f270b3b31c6513a6353a7a5d4d10be1` |
| `d98b42bbc/viewport-unit-red.log` | `cc46368423cca4cf65ead608c79dea2c0b55909f16b83c91a180811dd3d35a87` |
| `d98b42bbc/viewport-timing-green.log` | `49c116ae56c1aeb62ad2b76314ef6a5142c9bf55f9768f911eb2e7e328983d22` |
| `01c9a0ba4/onboarding.log` | `558de32effe1fbdc39f3d68c9adf689d956d7de6a4e844c74b7c4e707b5a4637` |
| `01c9a0ba4/readonly-height.log` | `0a3a1b10d2fd1197aeed06703f0d48f2ebfddd8462e0ad112527c5368c7509cc` |
| `01c9a0ba4/readonly-height-green.log` | `0771d1e489b8e551736426bf7dd1ce86314f8f73da017b6f1e6d31a931820dc9` |
| `e62a76827/runtime-facts.json` | `689b79660c6b590d85b74e7f5bad9de8cd50312302949ade46d35ac0515912da` |
| `e62a76827/onboarding.log` | `57e2e206bc6c8a3c01f38cbd0926946db3e7db4c4bb35ab1b97cc928a580c7db` |
| `e62a76827/login.log` | `239b2cd574ffe60268737f0d1698de5ae99b992e645f0e72f8c4fba0ff9c5884` |
| `e62a76827/deploy-facts.sql` | `65df5f7b746b79f4cb4f9909993a260f3edfc8cc3d943385660d5bfb1d013e23` |
| `e62a76827/deploy-facts.log` | `44efc6b92b5d0d2aecded57784656dac35514564d699f7c7301b9fdfe3b540bb` |
| `e62a76827/visual/visual-observations.json` | `3d23eecdb9006624698fc7d51cc4f927f84c7ab7220eb3cb0c0159cd4aac56cc` |
| `e62a76827/script-tests.log` | `bf809ce37c53f727b775603c157efd02129c2c0d82e4156afaeb39817da78c5d` |
| `e62a76827/architecture.log` | `a57cbc313adabf14f37fdd0537390962f37bcf260c8bb65aa9d52a399c560435` |
| `e62a76827/project-verify.log` | `fa727560ab6933c324896ffefe0ec1b4fea1b5e639e86235bcc0fd816ecb2d1f` |
| `e62a76827/control-project-verify.log` | `45e9a33e5971e4e52f8f49da3e0394b773c47e179090bc06bd88108f406de383` |
| `e62a76827/resume-context.log` | `b11d884f8dff7ab19c8847ff97f79f6dc0d2bf20fc324f06358f47f51cd9befb` |
