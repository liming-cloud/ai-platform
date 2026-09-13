# WP-0016：原型视觉精修与验收收口

## 结论与绑定

- 实现提交：`21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`；基线：`2b4eee0719a4c212568b430154c24536a9163b53`。
- 2026-09-13 用户表示全量 UI 基本通过，并明确选择“精致紧凑”方案后要求实施。该反馈是整体方向认可、剩余视觉细节待收口，不是最终无条件验收。
- 公共控件精修与技术复验通过，等待最终视觉人工确认。工作包仍为 WP-0016，不进入 WP-0017，不修改生产前后端、API、数据库或部署。
- 保留 Claude (Anthropic) 参考风格、7/5/7、页面任务与保存范围；渠道绑定、设备凭据、邮件邀请继续暂缓。没有重置用户浏览器或恢复停用身份。

## 修改内容

正常字号下，桌面普通按钮和单行输入为 36px，表格行内操作和完整分页为 32px；登录、手机和粗指针设备至少 44px。按钮 14px、输入 16px，正文与表格字号不随操作压缩。单行表格以 48px 为基准，长内容允许增高。

输入框以实际行高计算最小高度，文字放大时不把 36px 当作裁切上限。配对字段共享标签行，控件与帮助信息归入内容区；工具栏操作对齐输入，不被帮助文本拖到底部。搜索与选择仍保留原节点、值、事件、禁用和表单归属。没有重写原型状态机。

分页数字按钮方形、各控件同高；普通按钮按内容宽度。弹窗桌面内边距 24px、手机 16px，操作区上边距 16px、按钮间距 8px；去除同一组件分散的重复覆盖，不再靠文件尾补丁叠加。手机取消和返回不无差别撑满整行，设备任务与复合任务卡片不缩成普通按钮。

## 红绿与独立审查

子 Agent 先新增 `scripts/visual-polish.browser.test.cjs` 并实际运行，首轮 17 项为 5 通过、12 失败：普通/分页仍为 44px，长标签使输入顶部相差 25.59px，帮助文本使按钮偏移 52.78px，弹窗间距仍为 12px，文字放大后 44px 输入无法容纳 48px 行高。原始红灯仅在工具终端输出，未落盘；不声称存在红灯 TAP 文件。200% 用例排除原生 select 的 `normal` 行高导致的 NaN 后再次运行，确认真实裁切失败。

主任务修改后首轮 15/17，通过精确分页属性修正数字按钮宽度，再以同一测试得到 17/17；全量浏览器为 179/179。首次主任务绿灯尝试与最终 TAP 在本轮临时目录中。

独立审查对固定三个实现文件执行额外探针：重复增强后原 input/select 引用、label、form、name、值和 data 属性保持不变，FormData 一致，disabled 不提交，隐藏业务空间仍不渲染；向厂商选择加入 30 个合成候选再搜索末项，原选择与表单未替换、已选项保留。探针仅在独立浏览器及工具终端执行，没有保存报告文件，也没有写入日常演示状态。

六维质量判断仅覆盖本次固定差异：

| 维度 | 判断与证据 |
| --- | --- |
| 正确性 | 179 项浏览器、1638 个页面场景通过；尺寸、帮助换行及文字放大有新增断言 |
| 复杂度 | 仅增加一个 23 行布局辅助函数，限制为已知字段组；不增加业务状态或接口 |
| 冗余 | 合并分页与弹窗覆盖规则，公共令牌统一密度；没有逐页尺寸补丁 |
| 性能 | 只移动既有 DOM 节点；现有大数据量目录、历史窗口及选择器压力用例继续通过，不声称生产性能提升 |
| 架构 | 变化限于可丢弃原型、设计规范及证据；生产契约、权限语义和持久化未变 |
| 测试质量 | 实际红灯后修复；测试检查渲染盒、表单行为和触控条件，不只匹配 CSS 文本 |

代码图谱先定位 `enhanceTaskComponents` 与 `render`；本轮对前者执行两级影响分析，返回 23 个符号。因此字段分区限定为保留原控件的呈现增强，并对全部 78 入口回归，不扩展为状态机或路由重构。CSS 不在图谱返回源码中，以两份确定样式文件补充检查。

## 可复现验证

环境：macOS、Node.js 22.22.3、Playwright 1.62.1、本机 OpenDesign 0.21.1。独立 Chromium 使用临时 profile，不访问用户 Chrome 数据。仓库根目录执行：

```bash
export NODE_PATH=/Users/lingou/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules
export PROTOTYPE_BASE_URL='http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html'
WP0016_POLISH_REPORT_DIR=$(mktemp -d /private/tmp/wp0016-polish-review.XXXXXX)
node --test docs/prototypes/wp-0016/scripts/visual-polish.browser.test.cjs
node --test docs/prototypes/wp-0016/scripts/*.test.mjs
node --test --test-concurrency=2 docs/prototypes/wp-0016/scripts/*.test.cjs
node docs/prototypes/wp-0016/scripts/verify-static.mjs
PROTOTYPE_ARTIFACT_DIR="$WP0016_POLISH_REPORT_DIR/matrix" node docs/prototypes/wp-0016/scripts/verify-and-capture.cjs
PROTOTYPE_CONTROL_REPORT="$WP0016_POLISH_REPORT_DIR/controls.json" node docs/prototypes/wp-0016/scripts/enumerate-controls.cjs
OPENDESIGN_SAVE_REPORT="$WP0016_POLISH_REPORT_DIR/opendesign-save.json" node docs/prototypes/wp-0016/scripts/save-opendesign.cjs
make project-status
make project-verify
make resume-context
```

OpenDesign lint 使用绝对路径，避免系统 `od` 冲突：

```bash
ELECTRON_RUN_AS_NODE=1 OD_SIDECAR_IPC_PATH=/tmp/open-design/ipc/release-stable/daemon.sock \
  '/Applications/Open Design.app/Contents/Frameworks/Open Design Helper.app/Contents/MacOS/Open Design Helper' \
  '/Applications/Open Design.app/Contents/Resources/app/prebundled/daemon/daemon-cli.mjs' \
  lint '/Volumes/slm-ssd/projects/ai-project/ai-platform/docs/prototypes/wp-0016/index.html' --fail-on p1 --json
```

| 验证 | 本轮结果 |
| --- | --- |
| Node 全量 | 169/169 |
| 浏览器全量 | 179/179，包含新增视觉专项 17/17 |
| 七态三尺寸矩阵 | 1638/1638，11035 断言，运行错误 0 |
| M6 控件接线 | 462 场景、7989 实例，未接线与运行错误 0 |
| 静态合同与可达性 | 3806 断言，343 静态及 1638 HTTP 场景 |
| OpenDesign lint | p0/p1/p2 均为 0；命令退出 0 |
| OpenDesign 保存 | MCP 保存 113 文件，预览内容逐文件 SHA-256 一致 |
| 项目治理 | 14 项治理测试及 project-verify 通过；恢复上下文低于 12 KiB |

OpenDesign lint 的 Electron 启动输出包含 macOS `task_name_for_pid` 诊断，但检查 JSON 的 ok 为 true、无 findings 且退出码为 0；不将该启动诊断误记为视觉失败。

持久机器报告为 `artifacts/visual-polish-validation-summary.json`、`artifacts/static-verification.json` 和 `artifacts/opendesign-save-visual-polish-20260913.json`。原始绿灯报告及 100 张截图仅在 `/private/tmp/wp0016-polish.F2A4F6/`：95 张矩阵截图、5 张专项截图；不进入仓库。主任务实际目视检查平台账户、平台运行、助手配置、桌面与手机供应商弹窗，不声称逐张人工审查。

## 验收与证据限制

全部原型、对应代码和设计仍由原 OpenDesign 项目保存；项目元数据 designSystemId 为空，实际 Claude 参考由 tokens 承载，不声称重新绑定或换用 Anthropic 执行模型。本轮没有启动新的设计生成任务。

机器验证覆盖 390/1024/1440、现有 1366×768 和等效重排、键盘、reduced-motion，以及新增 200% 文字放大探针。实际浏览器 200% 缩放、系统原生选择弹层及最终精致度仍由用户确认。接线检查不等于业务穷举或真实 RAG 能力。

用户已有五项 fixture 重命名保留原暂存状态，未纳入本包提交。

## 工程助手记录

运行环境为 Codex；能力来自已安装的 engineering-assistant 插件。实际路由为 `engineering-assistant:prototype`，在匹配用户已明确授权的 OpenDesign 保存动作后为 inline/proceed。显式加载 engineering-assistant、prototype、tdd、code-review，以及 UI 和授权参考；使用 Charter、STATE、WP-0016 及其引用的全部 12 个 ADR。

原型技能约束了可丢弃范围与最终人工门禁，TDD 要求真实红绿，评审技能要求固定差异和六维证据。通用营销页面设计技能不适用于控制台，未用于设计实施。未产生持久 flow、StageRunResult 或 QualitySnapshot，阶段结果校验不适用。
