# WP-0016 OpenDesign 全平台高保真原型候选证据

## 结论

OpenDesign 已在原项目中把原型从 7 个关键页面扩展为 68 个实际可渲染页面，覆盖 M6 全平台页面任务，并前置覆盖 M7 设备问答和 M8 平台运维。候选原型提交为 `18d9acdd168e4d2bcc81f379669fb8e19d7e3051`。

本证据不宣告 WP-0016 完成。68 页的清单、专属脱敏数据、页面结构、七类状态和 1,428 个 URL 组合已通过静态门禁；真实浏览器的 DOM、焦点、横向溢出、交互和 83 张截图因本地页面访问权限被拒绝而未执行。旧候选的 15 张截图只证明原 7 页，不得替代本轮 68 页视觉验收。

## 交付范围

- 页面单一事实源：[page-inventory.json](../../docs/prototypes/wp-0016/page-inventory.json)，共 10 组、68 页。
- 人工可读清单：[PAGE-INVENTORY.md](../../docs/prototypes/wp-0016/PAGE-INVENTORY.md)。
- 页面专属数据：[page-fixtures.json](../../docs/prototypes/wp-0016/page-fixtures.json)。
- 全平台渲染层：[full-platform.js](../../docs/prototypes/wp-0016/full-platform.js)。
- 原型入口：[index.html](../../docs/prototypes/wp-0016/index.html)。
- 设计契约：[DESIGN.md](../../docs/prototypes/wp-0016/DESIGN.md)。
- OpenDesign 生成提示：[OPEN-DESIGN-FULL-PLATFORM-PROMPT.md](../../docs/prototypes/wp-0016/OPEN-DESIGN-FULL-PLATFORM-PROMPT.md)。
- 静态门禁结果：[static-verification.json](../../docs/prototypes/wp-0016/artifacts/static-verification.json)。
- 浏览器门禁状态：[verification.json](../../docs/prototypes/wp-0016/artifacts/verification.json)。

没有修改 `frontend/`、`backend/`、公共 API 或数据库；M7/M8 页面统一标注“原型前置，不代表实现授权”。

## 页面与交互边界

68 页分为共享访问 1 页、平台治理 10 页、租户访问与知识 13 页、知识处理与索引 6 页、租户模型 7 页、助手交付 7 页、评测与治理 11 页、普通用户问答 3 页、设备问答 4 页、平台运维 6 页。

每页都在清单中冻结目标路由、唯一任务、主要操作、页面模式和普通分页许可。新增页面不是空白名称页，而是使用目录、详情、向导、运行结果、时间线或高风险确认六种任务结构，并绑定独立中文标题、状态、事实、列表或步骤数据。平台控制台、普通用户问答和设备问答使用三种独立外壳。

九条可审查闭环覆盖租户建立、平台模型供应链、租户模型配置、知识接入、索引重建与激活、助手评测与发布、普通用户历史问答、设备问答和平台运维。页面最多一个普通目录分页；消息、事件、步骤和候选不使用页码分页。

## OpenDesign 实际执行

- OpenDesign 项目：`249f81f2-6f1a-46ff-988c-da3cdedb35e7`。
- Workspace：`v1l0xylpndn2os1hsa17s8dy`。
- Conversation：`88968f9d-740d-4032-a1a7-6b3248d9d4d9`。
- Run：`88592505-3d9b-4f72-b3f9-90c73eab8b45`。
- 项目技能：`emil-design-eng`。
- 场景插件：`example-web-prototype`。
- 插件快照：`a22f8380-fd7c-4792-91f0-47011e14083f`。
- 运行结果：`succeeded`，`deliverableValid=true`，入口为 `index.html`。
- 未完成标记：`endedWithUnfinishedWork=true`，原因仅为浏览器权限门禁未执行。

OpenDesign 0.21.1 使用签名 App 内绝对路径。运行时技能快照是可再生成的本地缓存，已由 `.gitignore` 排除；稳定 App、技能、场景插件和快照标识记录在 `open-design.lock.json` 与 `open-design-project.json`。

## 已通过验证

静态与 HTTP 门禁：

    python3 -m http.server 4176 --bind 127.0.0.1 --directory docs/prototypes/wp-0016
    node docs/prototypes/wp-0016/scripts/verify-static.mjs

结果：

- `pageCount=68`、`groupCount=10`、`stateCount=7`、`viewportCount=3`。
- 61 个新增页 × 7 状态形成 427 个静态渲染场景。
- 68 页 × 7 状态 × 3 视口形成 1,428 个 HTTP 场景，全部返回成功。
- 共 3,929 条断言，0 失败。
- 断言覆盖清单/fixture 一致性、路由、唯一任务、主要操作、分页上限、重复 ID、无权限披露、M7/M8 授权提示和九条闭环边。

OpenDesign 最终 lint：

    daemon-cli.mjs lint docs/prototypes/wp-0016/index.html --fail-on p1 --json

结果为 `ok=true`，P0/P1/P2 均为 0。另有 `git diff --check`、`make project-status` 和 `make project-verify` 通过。

## 未通过的浏览器门禁

计划命令：

    NODE_PATH=/private/tmp/wp0016-playwright/node_modules node docs/prototypes/wp-0016/scripts/verify-and-capture.cjs

OpenDesign 受限进程启动 Chromium 时被 macOS Mach 服务权限拒绝；随后浏览器访问 `http://127.0.0.1:4176` 的授权也被拒绝。按照浏览器安全边界未切换其他方式绕过。

因此下列结论仍为待验证，不能声称通过：

- 1,428 个真实 DOM 场景的标题、分页、重复 ID、控制台错误和失败请求。
- 390、1024、1440 的文档级横向溢出。
- 键盘焦点顺序、Dialog/Drawer 焦点恢复和 reduced-motion。
- 九条闭环及原有问答、引用、索引激活、助手发布交互。
- 每页 1440 成功态和代表性响应式状态共 83 张截图。

## 人工确认入口

在 OpenDesign 中打开项目“WP-0016 可信 RAG 高保真原型”。页面右下角“原型场景”由 `page-inventory.json` 动态生成十个分组，可按阶段、中文页名或 screen 搜索，并切换七种状态。

人工确认必须覆盖 DESIGN.md 的完整清单。取得 68 页浏览器证据和用户明确确认前，WP-0016 保持进行中，当前前沿不得切换到 WP-0017。
