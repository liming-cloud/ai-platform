# WP-0016 普通用户问答原型闭环复验

## 结论与边界

实现与原始证据绑定提交 `02b55d794`，比较基线为 `cc0c8578a`。用户同意继续普通用户问答切片，并再次确认 `DESIGN_VARIANCE=7`、`MOTION_INTENSITY=5`、`VISUAL_DENSITY=7`。本轮仅修改 `docs/prototypes/wp-0016`，不修改生产前后端、API、模型或数据库。

普通用户专项通过，整个平台仍未通过；WP-0016 保持进行中，人工拒绝和 WP-0017 门禁不变。上一轮 WP-0016-B01（登录入口）与 B02（会话刷新丢失）在本轮合成原型范围内关闭；B03、B04 保持未关闭。

## 本轮结果

- 独立合成登录、错误输入反馈、匿名深链接拒绝与退出；公开演示账号为 `spruce-demo / demo.user / demo-only`，输入密码不持久化。
- 选择授权助手创建固定版本的空会话；会话 URL、历史筛选、切换、续问和事件序号加载均产生实际数据变化。
- 问题、轮次、接收偏移、引用与终态由同一合成状态源驱动；刷新不重复追加，退出重登后旧接收轮次先暂停，再由本人继续。
- 断线继续、失败重试、停止与继续有真实按钮路径。一般年假问题不再虚构用户有七天未休，未知问题明确拒答。
- 登录态单独保存，退出不删除聊天记录；存储满时仍可移除登录态。若浏览器连移除都拒绝，当前页面失败关闭并明确提示，不宣称跨重载安全。
- 授权投影不再通过历史、会话或运行对象夹带全文和引用；成员/权限变化及跨标签事件会阻止旧回调显示内容。
- 增量更新只修改当前正文，避免每一段都重建控件；修复停止按钮不稳定、历史筛选被 CSS 覆盖、减少动效导致抽屉遮挡、小屏登录按钮被说明挤出首屏。

## 可复现验证

复用已安装的 Playwright 1.62.1 / Chromium 和本机 4176 服务；未重新安装应用、浏览器或依赖。

```sh
python3 -m http.server 4176 --bind 127.0.0.1 --directory docs/prototypes/wp-0016
NODE_PATH=/private/tmp/wp0016-playwright/node_modules node --test docs/prototypes/wp-0016/scripts/query-journey.test.cjs docs/prototypes/wp-0016/scripts/query-resilience.test.cjs
node --test docs/prototypes/wp-0016/scripts/remediation-contract.test.mjs docs/prototypes/wp-0016/scripts/business-closure.test.mjs docs/prototypes/wp-0016/scripts/reviewer-regression.test.mjs docs/prototypes/wp-0016/scripts/query-state.test.mjs docs/prototypes/wp-0016/scripts/query-boundaries.test.mjs
NODE_PATH=/private/tmp/wp0016-playwright/node_modules PROTOTYPE_ARTIFACT_DIR=docs/prototypes/wp-0016/artifacts/query-closure-full-browser-final-20260903 node docs/prototypes/wp-0016/scripts/verify-and-capture.cjs
node docs/prototypes/wp-0016/scripts/verify-static.mjs
make project-status project-verify resume-context
git diff cc0c8578a --check
```

已有服务时不重复启动。全平台浏览器命令预期退出 1，原因是下述真实布局缺陷，不是用户权限阻断。

| 验证 | 实际结果 |
| --- | --- |
| 普通用户专项浏览器 | 15/15，通过；包含故障、停止/继续、历史筛选、三档宽度与手机引用点击 |
| Node 状态和合同 | 36/36，通过 |
| 静态合同 | 4,036 断言、427 共享渲染场景、1,428 HTTP 入口，通过；不等于浏览器通过 |
| 全平台真实浏览器 | 68 页 × 7 状态 × 3 视口，1,428 场景、9,660 断言；14 阶段中 12 通过、2 失败；运行异常 0 |
| 设计 lint | 锁定绝对路径执行，P0/P1/P2 均为 0 |
| 截图 | 全平台 86 张、问答专项 11 张；不是全部状态逐张截图或全部控件人工验收 |

最终机器报告位于 `docs/prototypes/wp-0016/artifacts/query-closure-full-browser-final-20260903/verification.json`，专项为 `query-closure-browser-green.tap`、`query-closure-node-green.tap`。目录 `query-closure-independent` 保存三档登录、助手、聊天和恢复/存储错误截图。已目视复核这些页面的代表图，确认小屏登录主操作、聊天抽屉、引用和桌面消息布局。

实现前五项旅程红灯、状态边界、重新登录、减少动效、登录密度、历史筛选和停止控件红灯均保留在本轮 `query-*-red.tap`。TAP 仅规范行末空白，不改动原因或结果；原始自动输出可由 `02b55d794` 恢复。首轮重复截图保留于本机临时目录，仓库保留首轮报告及最终截图。

## 未关闭项与覆盖限制

- WP-0016-B03：390px 平台角色页在 success/partial/disabled 下仍横向溢出。
- WP-0016-B04：1280×720 审查器展开后收起入口仍可能超出视口。故障工具的操作测试使用 1440×1300，不能据此关闭低高度缺陷。
- 操作账本为 97 个已登记项，其中 20 个未闭环，且未穷举全部控件；账号交付、知识接入、索引、模型、助手发布和评测仍有待设计/实现的原型路径。
- 只有公开合成账号和确定性答案；没有真实认证、模型 token 流、生产越权、RAG 准确性/性能、完整读屏或真实 200% 缩放验收。停止后演示继续同一轮，不额外声称支持会话删除或放弃轮次。

后续仍在 WP-0016：先关闭统一布局和审查器缺陷，再按账本逐个闭环剩余页面任务；全部关键原型经人工确认后才能推进 WP-0017。

## 工程与设计依据

- 当前运行环境为 Codex；使用已安装 `engineering-assistant` 插件，直接选择并显式加载 `engineering-assistant:prototype`、`engineering-assistant:tdd`、`engineering-assistant:code-review` 及对应 UI/安全边界参考。可丢弃原型、失败优先和六维差异评审实际约束了实现；未启动持久化 Runtime flow，也未产生 `StageRunResult`，阶段结果校验不适用。
- 主任务完整读取 Charter、STATE、WP-0016 及其 7 个 ADR，并运行状态门禁。`lastVerifiedCommit=18d9acdd1` 是基线的祖先；因全平台门禁未过，不提升它。
- 图谱探索 `renderScreen/renderLogin`、`renderConversation`、`submitQuestion`、`createPrototypeStore/queryProjection`；对 `renderConversation`、`createPrototypeStore`、`queryProjection` 和 `scheduleQueryContinuation` 执行影响分析。结合固定差异确认最小修改范围为原型状态、渲染、计时器和测试，没有触及后端重构。
- 继续使用已安装、签名稳定版 OpenDesign 0.21.1 和原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`。Local Codex 运行 `44d599a7-a277-46ce-bfa8-62b3b048c7fb` 在同一目录生成，使用 `emil-design-eng`。MCP 连接关闭后改用官方绝对路径的本地 MCP 入口，没有重装或修改配置。
- 设计运行内部无法启动 Chromium 的报告仅表示子进程限制；本机获准浏览器已实际完成上述复验。`query-closure-implementation-20260903` 只是中间阶段证据，不代表最终结论。
- lint 使用 `/Applications/Open Design.app/Contents/Resources/app/prebundled/daemon/daemon-cli.mjs lint <index.html> --fail-on p1 --json`，由 OpenDesign Helper 以 `ELECTRON_RUN_AS_NODE=1` 启动；不调用 `/usr/bin/od`。

六维判断：正确性在本切片通过、整体仍失败；复杂度保持现有状态源与少量投影；冗余方面移除临时 DOM 回答与重复敏感数据投影；性能只验证本地合成渲染且修复增量重建；架构边界保持原型隔离；测试以红绿证据和真实按钮操作约束结论。人工验收状态单独保持拒绝。
