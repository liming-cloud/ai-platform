# WP-0016 OpenDesign 高保真原型候选证据

## 结论

WP-0016 已形成可供人工确认的高保真候选原型。设计契约、设计令牌、脱敏 fixtures、七个关键页面、七类状态、跨尺寸自动验收和 15 张截图均已落盘。

本证据不宣告工作包完成。人工尚未确认产品任务、信息架构和视觉基线，因此 WP-0016 保持进行中，生产前端和 WP-0017 仍被门禁阻断。

## 交付边界

- 原型目录：[docs/prototypes/wp-0016](../../docs/prototypes/wp-0016/)。
- 设计契约：[DESIGN.md](../../docs/prototypes/wp-0016/DESIGN.md)。
- 设计令牌：[tokens.css](../../docs/prototypes/wp-0016/tokens.css)。
- 脱敏数据：[fixtures.json](../../docs/prototypes/wp-0016/fixtures.json)。
- 交互入口：[index.html](../../docs/prototypes/wp-0016/index.html)。
- 机器验收摘要：[verification.json](artifacts/WP-0016-open-design/verification.json)。
- 截图目录：[WP-0016-open-design](artifacts/WP-0016-open-design/)。

原型只使用本地合成数据，不访问真实 API、模型、对象存储、租户正文或成员信息，也未修改任何生产前端、后端 API 或数据库。

## OpenDesign 锁定与实际使用

- 官方稳定版：`open-design-v0.21.1`，本机 App 版本 `0.21.1`。
- 应用标识：`io.open-design.desktop`。
- 签名主体：`Developer ID Application: Wei Huang (236R69AWW2)`。
- Gatekeeper：`accepted`，来源为 `Notarized Developer ID`。
- Codex MCP：`open-design` 已启用，命令使用签名 App 内绝对路径，未调用 PATH 中的 `/usr/bin/od`。
- OpenDesign 项目：`249f81f2-6f1a-46ff-988c-da3cdedb35e7`。
- 项目技能：`emil-design-eng`。
- 入口文件：`index.html`。
- anti-slop lint：以 `p1` 为失败阈值，P0/P1/P2 均为 0。

OpenDesign 独立 HTML 导出与 `files list` 在各等待 60 秒后未返回，已人工终止，且未生成不完整文件。该工具问题不影响本工作包明确要求的项目导入、lint、可操作原型和验收截图；状态已如实记录在 `open-design-project.json`，不得宣称导出成功。

## 浏览器自动验收

运行时为临时目录中的 Playwright 1.62.1 与 Chromium 151.0.7922.34，不修改仓库前端依赖。

    NODE_PATH=/private/tmp/wp0016-playwright/node_modules node docs/prototypes/wp-0016/scripts/verify-and-capture.cjs

结果：

- 状态：PASS。
- 断言：922 条。
- 页面状态组合：147 个，即 7 个页面 × 7 类状态 × 3 档宽度。
- 截图：15 张。
- 浏览器控制台、页面异常和失败请求：0。
- 普通分页：所有页面最多 1 个；会话页为 0。
- 文档级横向滚动：0 个场景。
- 重复 DOM ID：0 个场景。

交互验收覆盖登录进入助手目录、助手搜索和方向键、固定助手版本进入会话、引用授权展开、`beforeSequence` 加载更早消息、确定性 `TOKEN_DELTA` 到 `COMPLETED`、移动端历史抽屉与焦点恢复、跳转链接、索引激活确认、助手发布确认和安全门禁失败时按钮禁用。

## 视觉抽查

人工视觉抽查了登录、助手目录、流式会话、数据来源部分失败、索引激活、助手发布阻断和移动端拒绝态。抽查确认：

- 保持 Fluent UI 企业产品语言、深绿色强调、细边界和稳定信息密度。
- 未使用渐变、玻璃拟态、AI 紫、营销式大标题或装饰性卡片堆叠。
- 会话页为左侧历史、中央消息流、顶部助手信息；移动端历史进入抽屉。
- 数据来源、索引激活和助手发布拆为独立主任务。
- denied 状态不加载目标正文或引用，disabled 状态给出相邻原因。
- 390 宽度把管理表格转为字段标注行，并保留唯一底部任务导航。

截图清单：

- `login-success-desktop.png`、`login-success-mobile.png`。
- `assistants-success-desktop.png`、`assistants-empty-mobile.png`。
- `conversation-partial-desktop.png`、`conversation-success-tablet.png`、`conversation-success-mobile.png`。
- `conversation-tablet-reduced-motion.png`、`conversation-mobile-keyboard-focus.png`。
- `knowledge-directory-success-desktop.png`。
- `knowledge-sources-partial-desktop.png`、`knowledge-sources-error-tablet.png`、`knowledge-sources-denied-mobile.png`。
- `index-detail-success-desktop.png`。
- `assistant-release-disabled-desktop.png`。

## 现有产品审计如何影响原型

代码图谱聚焦探索了 `ProductShell` 与 `QueryWorkspace`。原型保留现有统一 SPA、Fluent UI v9、浏览器会话和 URL 可恢复状态；退役 `QueryWorkspace` 中助手下拉框、助手分页、一次性问题和结果区混杂的任务结构，改为独立助手目录与会话聚合。

本工作包没有修改生产符号或公共契约，因此没有执行非局部影响分析，也没有用原型类名建立第二套生产组件库。

## 人工确认入口

    python3 -m http.server 4176 --bind 127.0.0.1 --directory docs/prototypes/wp-0016

建议按下列顺序确认：

1. `/?screen=login&state=success`
2. `/?screen=assistants&state=success`
3. `/?screen=conversation&state=partial`
4. `/?screen=knowledge-directory&state=success`
5. `/?screen=knowledge-sources&state=partial`
6. `/?screen=index-detail&state=success`
7. `/?screen=assistant-release&state=disabled`

人工需要明确确认 DESIGN.md 末尾八项清单。确认前不得关闭 WP-0016、切换当前前沿或开始生产业务开发。
