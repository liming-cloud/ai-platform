# WP-0016 全量闭环原型与 OpenDesign 保存证据

## 结论与绑定

- 实现提交：`10fd8eaaa71bc66cc8f3fe96f20c7dac58f7fd1c`；基线：`280885682824343ffd46e577ed51d38f3bbb7a9c`。起点工作区干净，原 `lastVerifiedCommit` 为其祖先。
- 56 个范围内 M6 页面已完成本轮技术预验，可重新提请人工复核。原人工拒绝没有自动变成通过，WP-0016 仍进行中，不推进 WP-0017。
- 全部 68 页继续保留。渠道绑定、设备凭据、邮件邀请三项暂缓；M7/M8 另外 10 页只作为信息架构与视觉前置，不冒充交互全量实现。
- 只修改可丢弃原型、验证与项目控制面，没有修改生产前后端、API、数据库和部署。执行中发现其他任务已暂存 5 个 fixture 路径迁移，原样保留并排除本轮提交。

## 本轮闭环

| 问题 | 修正与证据 |
| --- | --- |
| A03 / A11 工作区与账户交付 | 新建租户管理员显式选择组织后进入自己的空工作区；跨组织对象拒绝；真实浏览器贯通新租户创建、登录、成员页、模型/评测空页、知识库与资料接入 |
| A03 详情上下文 | 知识库、来源、索引及模型、助手、评测、策略与审计以 URL 恢复；两个标签页不交换对象；无效参数整组拒绝，不回落首项 |
| A04 目录 | 知识库、平台租户与审计搜索/状态筛选接线；审计非首行详情与导出使用授权白名单记录 |
| A05 主操作 | 处理页批量重试只改当前知识库失败来源；快速发布遵守修复或候选构建选择；审计导出生成当前筛选结果且具有失败反馈 |
| A06 空态 | 人员账户、知识库空态复用正常创建表单，取消不提交 |
| A07 编辑保留 | 结构、授权策略修改后旧预检/预览不能发布；路由修改后须重新模拟；已发布访问策略可创建唯一新草稿调整 |
| A08 对象来源 | 签发范围登记与同知识库待处理来源原子保存，重复登记幂等，进入来源后继续安全阶段 |
| A09 易用性 | 保留 7/5/7；创建助手、模型版本、文档版本统一中文；输入顶部对齐；处理面板不再拉高按钮；新表格补齐手机字段标识 |
| A10 覆盖 | 除业务操作账本外，增加真实 DOM 七态枚举，包含侧栏、账户菜单和隐藏弹窗；“必须存在未完成项”的旧检查改为按范围计算缺口 |
| 补充闭环 | 文档更新不另建来源，增加存储量但不重复计文档；单文件重建冻结来源新版本及复用的活动索引标识；访问策略目录创建真实独立草稿，不跳进固定旧策略 |

原型的解析、OCR、对象下载、模型、索引、评测和审计记录均为合成数据。单文件 copy-on-write 只表达冻结与复用元数据，不执行实际存储复制。技术预验不能证明生产安全、RAG 质量或真实性能。

## 实际验证

| 检查 | 结果 |
| --- | --- |
| 未保存表单红灯 | 授权策略与路由 2/2 失败，复现可发布旧值 |
| 详情与新版本红灯 | 助手阶段错误回落 1/1 失败；访问策略新版本能力 1/1 失败 |
| 新增专项 | 14 个 Node、15 个浏览器用例；包括旧缺陷、失败回滚、深链接和新组织流程 |
| 全部 Node | 131/131 通过，0 失败 |
| 全部浏览器 | 130/130 通过，0 失败 |
| 页面矩阵 | 68 × 7 × 3 = 1,428 场景，9,644 断言，14 阶段全部通过，异常 0 |
| 控件枚举 | 56 × 7 = 392 场景，5,753 控件实例，页面错配 0、未接线 0、异常 0 |
| 静态 | 273 共享渲染场景、1,428 URL、3,177 断言通过 |
| OpenDesign lint | P0/P1/P2 均为 0 |
| 治理 | `project-status`、`project-verify` 通过；14 项治理测试、20 工作包；恢复上下文低于 12 KiB |
| 视觉 | 临时核对创建租户、创建助手、知识接入、处理结果、审计桌面图，以及租户详情平板、助手发布与会话手机图；85 张矩阵截图不进入仓库 |

最后的样式修改后重新运行整套矩阵。旧矩阵曾因尝试覆盖更新尚在处理中的来源而失败；现已明确断言处理中不可更新，推进到就绪后才测试更新入口，不删除原流程验证。

控件实例数不等于功能数：同一控件在七态重复计数。`ACTION-LEDGER.json` 是 141 个业务操作组，范围内已登记缺口 0，3 项暂缓。监听器只证明接线，正常路径、输入保留、拒绝与持久化由专项测试另行验证；没有宣称穷举全部输入及权限组合。

机器证据：

- `docs/prototypes/wp-0016/artifacts/full-closure-verification-20260906.json`
- `docs/prototypes/wp-0016/artifacts/full-control-inventory-20260906.json`
- `docs/prototypes/wp-0016/artifacts/opendesign-save-20260906.json`
- `docs/prototypes/wp-0016/artifacts/static-verification.json`

## OpenDesign 保存

复用 OpenDesign 0.21.1 与项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，未新建分散副本。内置连接的旧 transport 关闭后，使用同一已安装绝对路径 CLI 的新 stdio MCP 会话，`get_project` 确认 `resolvedDir` 指向当前原型目录；没有重启用户应用或修改连接配置。

`scripts/save-opendesign.cjs` 逐项调用 OpenDesign `write_file` 保存 85 个 HTML、JS、CSS、设计文档、JSON、验证脚本和官方图标资源，随后 GET 原始预览响应逐项比对 SHA-256，全部一致。`artifacts/` 的历史报告不重复写入，但项目链接目录可读取它们。保存报告记录每个文件的路径、字节数与 SHA-256。新增 `ACCEPTANCE.md` 提供三角色路径及全部 68 页入口，原型与代码都在同一项目文件区。

原始预览：[OpenDesign 原型](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html)。入口服务地址可能随应用重启变化，以重新 `get_project` 为准。

## 可复现命令

以下命令只访问本机合成原型，浏览器使用独立上下文而非个人 Chrome 配置。

```bash
export PROTOTYPE_BASE_URL='http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html'
export NODE_PATH=/private/tmp/wp0016-playwright/node_modules
node --test docs/prototypes/wp-0016/scripts/*.test.mjs
node --test docs/prototypes/wp-0016/scripts/*.test.cjs
node docs/prototypes/wp-0016/scripts/verify-static.mjs
PROTOTYPE_ARTIFACT_DIR=/private/tmp/wp0016-full-closure-matrix-20260906 node docs/prototypes/wp-0016/scripts/verify-and-capture.cjs
PROTOTYPE_CONTROL_REPORT=/private/tmp/wp0016-control-inventory-final.json node docs/prototypes/wp-0016/scripts/enumerate-controls.cjs
OPENDESIGN_SAVE_REPORT=/private/tmp/wp0016-opendesign-save-final.json node docs/prototypes/wp-0016/scripts/save-opendesign.cjs
ELECTRON_RUN_AS_NODE=1 OD_SIDECAR_IPC_PATH=/tmp/open-design/ipc/release-stable/daemon.sock '/Applications/Open Design.app/Contents/Frameworks/Open Design Helper.app/Contents/MacOS/Open Design Helper' '/Applications/Open Design.app/Contents/Resources/app/prebundled/daemon/daemon-cli.mjs' lint '/Volumes/slm-ssd/projects/ai-project/ai-platform/docs/prototypes/wp-0016/index.html' --fail-on p1 --json
make project-status
make project-verify
make resume-context
git diff --check
```

## 工程助手与代码图谱

- 当前插件运行环境：Codex。能力来源为已安装的 `engineering-assistant` 限定技能；最小路由为 `engineering-assistant:prototype`，缺陷修复追加 `engineering-assistant:tdd`。
- 显式完整加载上述两技能与原型 `references/ui.md`、`references/logic.md`、TDD `references/security-seams.md`；未运行 Hermes 替代命令。
- 控制面：Charter、STATE、WP-0016 及其 11 个 ADR，业务闭环、原人工拒绝和验收调查证据。按可丢弃原型和先红后绿约束实施，不触及生产代码。
- 代码图谱先探索 `currentScenario`、`navigate`、`selectKnowledgeBase`、来源登记与访问策略相关区域。`currentScenario` 深度 2 影响 38 个符号；`selectKnowledgeBase` 影响 4 个；`getAccessPolicyProjection` 影响 3 个。图谱对闭包方法有范围返回不完整的限制，之后用定向磁盘读取补足。
- 影响结论促使把页面投影、URL 定位与合成业务状态分开，用小型 `detail-context`、`workspace-journeys`、`governance-directories` 局部模块处理，而不是重写生产架构。覆盖共享渲染面的全部页面矩阵与原有专项回归。
- 没有产生工程助手 `StageRunResult`；该阶段结果校验不适用。上述 JSON 为测试/保存报告，不冒充受治理 flow 阶段结果。
- 检查过的通用设计技能明确排除密集后台和多步骤产品 UI，因此未套用其营销规则；视觉使用既定设计系统。

## 人审与剩余边界

由产品负责人按 OpenDesign `ACCEPTANCE.md` 重新确认产品任务、信息架构和视觉，或逐页反馈拒绝项。技术检查只能提出新的候选，不能替用户接受。WP-0016 保持进行中。

没有自动清除用户浏览器合成数据。旧快照缺少新字段时可能为空或拒绝登录；需要干净验收时由用户主动重置。三个暂缓项、生产认证策略上下限与真实解析/模型实现仍不在本轮完成范围。
