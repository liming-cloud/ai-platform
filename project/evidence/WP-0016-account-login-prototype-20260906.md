# WP-0016 账户口令交付与演示登录

## 结论与范围

- 实现提交：`c896c30898d9ea73f01cf52648a9cdf938974046`；起点：`b3c96b93e5bb004ccb34681475e8b36b6347d0ea`，开始时工作区干净，前次验证提交为其祖先。
- A11 从未实现推进为部分闭环：平台人员首次登录、已有租户普通成员建账授权与首次登录、口令重置、旧口令拒绝、刷新恢复、旧身份失效均有状态与浏览器证据。
- 新建租户的管理页面尚有默认组织上下文，依赖 A03；当前明确拒绝进入，不回落示例组织。A11 不关闭，账本仍为 132 项、14 个已知未闭环、7 类 P1、3 个用户暂缓；尚未穷举全部操作。
- WP-0016 继续进行，人工拒绝有效。不推进 WP-0017，不改生产代码、API、数据库或部署；渠道绑定、设备凭据、邮件邀请仍不实施。

## 原型设计选择

采用三种公开演示口令：`demo-only`、`Initial-only-2026!`、`Reset-only-2026!`。原型只记录对应的公开场景码和非秘密认证修订，输入字符串不进入业务快照、登录记录、错误结果或账户反馈。场景码可查表还原公开夹具，**不是**密码加密、密码哈希或生产认证方案。真实或任意自定义口令直接拒绝；界面折叠说明列出全部夹具，不标识任何账户当前选择。

未选择持久化任意密码或自建认证后端，也未用仅内存 Map 伪装刷新恢复。此选择只回答可丢弃原型的交互问题，不限制生产密码策略。重置保存失败保持旧场景和原身份，用户可重新输入后重试；不一致或非法输入反馈不重复输入材料。

人员目录是唯一登录名来源；普通用户由旧演示别名统一为 `shen.demo`。不保留 `demo.user` 兼容认证分支。动态新建账户不默认获得管理权；平台分配角色或租户添加成员并授权助手后，才能进入对应范围。问答同时检查组织编码、普通成员身份、功能及同租户发布助手。

同一管理员存在多个活动管理组织时，验证口令后只返回本人有权组织供显式选择，重新输入口令后才能登录；不自动选择列表第一项。没有新组织页面隔离证据时返回明确的原型未接通结果。

旧快照没有登录场景码时失败关闭，不能恢复固定旧口令。没有执行任何用户数据清除；若需要重新验收，可由用户在审查器手动重置，**该操作会清除当前浏览器的合成演示数据**。

## 实际验证

| 检查 | 结果 |
| --- | --- |
| 初始状态红灯 | 7/7 失败，复现口令未接通、固定账户白名单、成员别名、非公开输入、存储失败后重试、延迟通知旧身份和错误工作区结果 |
| 初始浏览器红灯 | 6/6 失败，复现重置后登录、新建平台身份、普通用户登录及三档口令说明缺失 |
| 多组织追加红灯 | 1/1 失败；矩阵同时复现创建第二组织后缺少组织选择 |
| 旧快照反例 | 在固定基线 `b3c96b93e` 内存加载状态模块，缺少场景的快照仍登录成功；期望为拒绝，真实复现成立 |
| 最终账户专项 | 状态 9/9、浏览器 7/7 通过 |
| 最终全部回归 | Node 117/117、浏览器 115/115 通过；原有回归未删减 |
| 全页面矩阵 | 68 × 7 × 3，共 1,428 场景、9,643 断言、14 阶段通过；异常与失败均为 0 |
| 静态 / OpenDesign lint | 3,177 断言通过；P0/P1/P2 均为 0 |
| 控制面收尾 | `project-status` 正常；14 项治理测试和 20 个工作包检查通过；恢复输出 8,961 字节，低于 12 KiB；`git diff --check` 通过 |

报告：`docs/prototypes/wp-0016/artifacts/account-login-verification-20260906.json`。矩阵初次失败不能覆盖最终结果或被隐藏：原因是已有测试给乔宁分配第二组织后仍假定唯一组织；补显式选择及反例后重跑通过。正向浏览器夹具通过可见选择器选择 `spruce-demo`，不注入身份或令牌。

新增普通成员浏览器路径实际使用平台创建表单、租户添加成员表单、授权助手和本人登录表单，没有直接修改浏览器状态来伪造成功。另覆盖新建平台账户授权登录、重置后的控制台/问答登录、刷新、非法口令清理、取消和三档宽度。

OpenDesign 同一项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 继续管理原型。MCP 元数据调用返回 `Transport closed`；未重启用户应用或修改连接配置。既有原始预览返回 HTTP 200，服务端 `prototype-state.js` 与磁盘 SHA-256 同为 `69f41ed576aab7f74022d29d9e80360a721a78c694733fa5ceed4236383d0f7e`，绝对路径 CLI lint 成功，说明验证访问的是当前原型而非另一份副本。

85 张矩阵截图与 3 张新增口令帮助截图均只在系统临时目录；已目视核对 390 与 1440 宽度，1024 有浏览器断言与临时截图。未修改 7/5/7 令牌，未提交截图，视觉结果不替代人工认可。

<a id="A11"></a>
## 剩余退出条件

下一切片优先 A03：移除管理页面和操作对默认组织的依赖，恢复租户及知识库 URL 上下文，证明新增租户管理员不会读到示例组织数据。完成新建租户从登录到所属工作区的浏览器闭环后，才能关闭 A11。随后处理 A08 对象来源接入；其他目录、主操作、空态、编辑保留和术语缺口仍按账本推进。

## 可复现命令

先确认 OpenDesign 当前原始预览端口；下列为本轮地址。Playwright 使用现有独立 Chromium，不读取用户 Chrome 资料。

```sh
node --test docs/prototypes/wp-0016/scripts/*.test.mjs
PROTOTYPE_BASE_URL=http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html NODE_PATH=/private/tmp/wp0016-playwright/node_modules node --test docs/prototypes/wp-0016/scripts/*.test.cjs
PROTOTYPE_BASE_URL=http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html node docs/prototypes/wp-0016/scripts/verify-static.mjs
PROTOTYPE_BASE_URL=http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html PROTOTYPE_ARTIFACT_DIR=/private/tmp/wp0016-account-login-matrix-20260906 NODE_PATH=/private/tmp/wp0016-playwright/node_modules node docs/prototypes/wp-0016/scripts/verify-and-capture.cjs
ELECTRON_RUN_AS_NODE=1 OD_SIDECAR_IPC_PATH=/tmp/open-design/ipc/release-stable/daemon.sock '/Applications/Open Design.app/Contents/Frameworks/Open Design Helper.app/Contents/MacOS/Open Design Helper' '/Applications/Open Design.app/Contents/Resources/app/prebundled/daemon/daemon-cli.mjs' lint '/Volumes/slm-ssd/projects/ai-project/ai-platform/docs/prototypes/wp-0016/index.html' --fail-on p1 --json
make project-status
make project-verify
make resume-context
```

原始红绿 TAP 在当次系统临时目录，重现原始失败可用起点提交与本轮新增测试；旧快照反例从固定提交的 `prototype-state.js` 经内存模块导入运行，不恢复旧文件或复制生产代码。其余历史审计保持固定提交，不在新登录门禁下误用旧探针。

## 技能、图谱与质量判断

当前环境 Codex，能力来自已安装 engineering-assistant 插件；最小充分路由为 `engineering-assistant:prototype` 与 `engineering-assistant:tdd`，本轮显式读取两份技能及 UI、逻辑、security-seams 参考。技能要求促使先保存真实失败，再做公开夹具模拟与浏览器回归。控制面使用 Charter、STATE、当前 WP、全部 11 份活动 ADR、上轮证据、DESIGN 和操作账本。

图谱探索账户创建、重置与登录；精确读取 `resetAccountPassword`、`loginConsole`、`renderPlatformAccounts`。两层影响分析显示重置同名聚合结果 6 个符号（含未修改的生产同名函数）、登录 8 个符号、`getSession` 10 个符号。结论使本轮同时处理状态、页面及登录测试夹具，并明确不触及生产同名实现。

- 正确性：117/115 全量回归与 1,428 场景通过；A11 的新租户部分保留未完成。
- 复杂度：三种公开场景、同一人员目录、既有两类入口，不增加加密库、API 或并行身份状态。
- 冗余：移除固定登录白名单和普通用户别名分支；四处输入界面共享折叠口令说明。
- 性能：身份检查重读本机合成账户事实以覆盖延迟通知；仅用于小型原型，不能证明生产认证性能，不新增网络轮询或模型调用。
- 架构：所有实现属于 OpenDesign 可丢弃原型；角色、功能、助手与租户授权相交，未接通组织失败关闭。
- 测试质量：正向身份经实际表单；反例覆盖旧口令、未授权身份、保存失败、延迟通知、旧快照和组织选择。没有真实 HTTP 认证、秘密编码解析或生产持久化 adapter，本轮相应 security-seams 单元格不适用。

没有 Runtime flow 或 active persisted quality node；未产生 StageRunResult/QualitySnapshot，阶段结果校验不适用。
