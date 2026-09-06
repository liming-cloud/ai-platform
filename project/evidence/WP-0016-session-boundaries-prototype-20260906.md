# WP-0016 角色登录与当前会话授权原型

## 结论与提交

- 实现提交：`1e1dc4f1bee510e76c3bd086903091de50a3737f`；基线为 `514920394648e3c359d040b9991e3d54e9bc5bab`。
- 关闭上一轮 A01 的三角色演示登录与管理访问、A02 的历史权限误伤；A09 仅关闭账户菜单、本人身份和退出子项，术语问题仍保留。
- 本轮仅修正可丢弃原型及验证。OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 的 `resolvedDir` 与仓库原型目录一致，没有另建原型副本，没有修改生产前端、后端、API 或数据库。
- 复核另确认 A11：账户创建/密码重置结果没有接通登录。它必须在 WP-0016 内继续补齐，不作为“生产以后实现”而从原型账本排除。
- 账本现登记 132 项：本轮关闭 4 个控件、补登记 1 项，范围内已知未闭环从 17 项净减为 14 项；7 类 P1 未关闭，控件仍非穷举。渠道绑定、设备凭据和邮件邀请继续暂缓。
- 人工拒绝继续有效，不能申请整套原型验收，不推进 WP-0017。

## 已验证行为

1. 平台演示账户和租户演示账户分别进入唯一对应工作区；错误口令、普通成员或不可用管理身份不能通过管理端登录。
2. 普通用户没有管理导航；直接打开管理页的全部七种状态或修改 `review` 都不能显示管理目录或创建操作。平台身份不能读租户知识，租户身份不能访问平台账户。
3. 两种管理外壳共用本人账户菜单。支持展开、退出、刷新、前后退、键盘打开和 Escape 关闭；退出后不恢复管理页面。
4. 所有原型页面操作从当前合成身份取操作者，不再传固定管理员常量。事件执行前重验身份；跨标签页替换身份后，旧页面和旧接收器失效。
5. 登录只提交独立登录记录，不改写聊天快照。登录记录保存失败时，原身份与已有聊天保持不变；旧轮次由身份 epoch 投影为暂停，明确继续后才更新持久状态。
6. 关闭本人历史后，本次登录中新建且仍选中的会话可以提问、刷新和续问；历史目录、更早历史加载、旧会话以及退出后重登恢复仍拒绝。关闭对话、助手或引用权限不会因为这一例外而扩大授权。
7. 没有已授权发布助手的普通用户无法建立问答入口。账户认证修订与成员状态重新校验；口令不进入状态、日志或浏览器存储。

这里的登录仅为公开脱敏夹具，不是生产认证。现有字段 `query.session` 复用为单一人员身份来源，两类入口不维护并行身份。HTTP 401、真实令牌、真实密码保存、登录限流与会话到期不属于本次原型实现。

## 红绿与回归证据

| 验证 | 实际观察 |
| --- | --- |
| 首轮状态红灯 | 6/6 失败：新身份 seam 尚不存在，以及当前会话被历史权限阻断 |
| 首轮浏览器红灯 | 6/6 失败：错误登录仍跳转、租户进入平台、角色越界等；沙箱启动失败另行排除后重跑 |
| 补充状态红灯 | 7 项中 2 项失败：无历史仍允许更早加载、无发布助手仍允许登录 |
| 键盘红灯 | Escape 未关闭账户菜单，1/1 失败 |
| 原子登录红灯 | 登录记录单独保存失败时业务快照仍被改写，1/1 失败 |
| 最终新增专项 | 状态 8/8，浏览器 7/7 通过 |
| 最终全部测试 | Node 108/108、浏览器 108/108 通过 |
| 全页面矩阵 | 68 页 × 7 状态 × 3 尺寸，1,428 场景、9,643 断言、14 阶段通过，浏览器异常为 0 |
| 静态与 OpenDesign lint | 3,177 断言通过；P0/P1/P2 均为 0 |
| 控制面收尾 | `project-status` 正常、`project-verify` 的 14 项治理测试及 20 个工作包检查通过；恢复上下文为 8,921 字节，低于 12 KiB；`git diff --check` 通过 |

机器报告：`docs/prototypes/wp-0016/artifacts/session-boundaries-verification-20260906.json`。它的 `PASS` 仅表示声明的检查通过；`readyForFullAcceptance=false` 和剩余问题同时保留。

角色收紧后，原管理专项都经可见登录表单准备正向身份；`browser-session.cjs` 的 `openForReview` 只用于正向页面矩阵，身份反例不使用它。重置后重新登录，不能保留隐含管理员身份。独立管理员修改功能的测试通过两个隔离浏览器会话和业务快照广播模拟服务端通知，不复制登录记录；接收端仍验证为原普通用户，防止把身份替换误报为权限撤销。

三档宽度的账户菜单边界均在视口内、无文档级横向溢出；390 与 1440 的临时截图已目视核对。矩阵 85 张及额外 3 张核对图只在系统临时目录，不提交图片。7/5/7 令牌保持不变；这不是全平台视觉认可。

## 尚未关闭与下一切片

旧报告 A03–A08 的对象 URL、搜索/筛选/行操作、主操作假结果、空态创建、未保存编辑和对象来源接入保持未关闭。A09 的术语、A10 的覆盖不足也不因本轮绿灯而取消。

<a id="A11"></a>
### A11 · P1 · 人员账户交付没有贯通登录

`resetAccountPassword` 只更新非秘密认证修订；`loginConsole` 只比较公开演示口令。使用合成账户 `account-qiao` 确认撤销会话并重置后，结果为 `resetSaved=true`、`newPasswordLoginAllowed=false`、`publicDemoPasswordStillAllowed=true`。新建或新授权账户同样不在当前固定登录夹具内。

这是原型跨任务缺口，不是生产漏洞结论。退出条件：在不记录或回显真实密码的前提下，明确且可重复地模拟创建/重置、首次登录、旧认证失效与失败恢复；界面反馈和实际登录结果一致，并补跨页浏览器回归。下一切片先完成此账户交付链，再处理 A03/A08 的对象上下文与来源接入，不能扩展邮件邀请。

## 可复现命令

从 OpenDesign `get_project` 取得当前原始预览 URL；本轮为下列地址，端口变化时重新读取。独立 Chromium 不读取用户 Chrome 配置。

```sh
node --test docs/prototypes/wp-0016/scripts/*.test.mjs
PROTOTYPE_BASE_URL=http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html NODE_PATH=/private/tmp/wp0016-playwright/node_modules node --test docs/prototypes/wp-0016/scripts/*.test.cjs
PROTOTYPE_BASE_URL=http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html node docs/prototypes/wp-0016/scripts/verify-static.mjs
PROTOTYPE_BASE_URL=http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html PROTOTYPE_ARTIFACT_DIR=/private/tmp/wp0016-session-matrix-20260906 NODE_PATH=/private/tmp/wp0016-playwright/node_modules node docs/prototypes/wp-0016/scripts/verify-and-capture.cjs
ELECTRON_RUN_AS_NODE=1 OD_SIDECAR_IPC_PATH=/tmp/open-design/ipc/release-stable/daemon.sock '/Applications/Open Design.app/Contents/Frameworks/Open Design Helper.app/Contents/MacOS/Open Design Helper' '/Applications/Open Design.app/Contents/Resources/app/prebundled/daemon/daemon-cli.mjs' lint '/Volumes/slm-ssd/projects/ai-project/ai-platform/docs/prototypes/wp-0016/index.html' --fail-on p1 --json
make project-status
make project-verify
make resume-context
```

A11 的最小复现：

```js
const store = createPrototypeStore();
store.resetAccountPassword("account-qiao", {
  password: "Reset-only-2026!", passwordConfirmation: "Reset-only-2026!", revokeSessions: true,
}, "account-platform-admin");
store.loginConsole({ username: "qiao.demo", password: "Reset-only-2026!" }); // 当前返回失败。
```

`createPrototypeStore` 从 `docs/prototypes/wp-0016/prototype-state.js` 导入；上述仅使用公开合成字符串。原始调查脚本与 2026-09-05 失败证据按其固定提交复现；本轮 A01/A02 已转为期望行为测试，不用旧探针执行异常冒充通过。

## 技能、图谱与六维判断

当前环境是 Codex，能力来自已安装的 `engineering-assistant` 插件；按最小充分原则选择并显式加载 `engineering-assistant:prototype`、`engineering-assistant:tdd`，读取 UI、逻辑及 security-seams 参考。技能使本轮先保存真实红灯，并在收尾时增加登录单键失败反例，没有跨入生产实现。控制面为 Charter、STATE、WP-0016、11 份活动 ADR、DESIGN、操作账本与前轮调查。

图谱先探索原型登录和授权，读取 `authorizeScreen`、`queryProjection`、`publishedAssistant`；对前两者各进行两层影响分析，均落在原型状态与页面文件的 4 个符号。结论促使共享授权入口和查询投影一起修复，不只隐藏按钮；固定操作者调用点做定向替换，没有生产代码影响。

- 正确性：新增 8 项状态与 7 项浏览器测试覆盖 A01/A02，原有 201 项回归未删减。
- 复杂度：一个合成身份、一个登录记录提交，不增加并行登录状态或虚构 HTTP adapter。
- 冗余：两个固定管理员常量被移除；管理外壳共用账户菜单，正向测试共用表单登录准备。
- 性能：只读取小型登录记录和现有内存夹具；没有新增网络模型调用或后台轮询。不以合成原型证明生产性能。
- 架构：全部变更位于 OpenDesign 原型与验证；三角色、租户上限、成员与对象授权相交，不放宽生产边界。
- 测试质量：权限反例不走正向角色准备；存储单键失败、旧流式 epoch、全状态直达和独立管理会话通知都有实际证据。A11 明确记为未关闭，不能据本轮专项判定全部认证闭环。

未使用 Runtime flow，未产生 `StageRunResult` 或 Runtime `QualitySnapshot`，阶段结果校验不适用。
