# WP-0016：旧版原型登录失败与保留数据修复

## 结论与绑定

- 实现提交：`c506b0f52f95f2ccfd9c5d249af737f3c91f9bf0`；基线：`34e42a334e04feeca4ac6ce7ea284f76813f0a9b`；起点 `lastVerifiedCommit=10fd8eaaa71bc66cc8f3fe96f20c7dac58f7fd1c` 为 HEAD 祖先。
- 用户在既有 Chrome 标签页按说明输入 `demo-only`，确实被拒绝；不是把用户输入错误当作默认解释。刷新后的只读诊断确认本浏览器存在可修复的旧版内置登录配置。
- 登录认证在找不到 `loginScenario` 时失败关闭，旧快照读取不会补齐该字段；页面说明却仍给出初始口令，且此前只提供清空全部数据的恢复。这是演示版本升级与恢复交付缺口。
- 新增明确确认的保留数据修复，并修正文案。仅处理 ID/登录名匹配、可用、未重置、没有场景字段的内置演示账户；拒绝自动覆盖自建、改名、停用、已重置、非法配置及重复身份。角色、成员关系、授权、知识库、会话及草稿不变；认证版本推进，旧会话不复活，修复不自动登录。
- 当前个人 Chrome 只完成复现、刷新和修复入口核对；未替用户确认修复，未清空数据。代码技术回归通过不等于用户当前浏览器已登录，更不等于原型人工验收通过。
- 只推进 WP-0016。生产认证、API、数据库、三个暂缓项与 WP-0017 均未修改；用户其他任务暂存的 5 个 fixture 路径迁移保持原样并排除提交。

## 可证伪调查与红绿证据

1. 排查“输入错误/帮助不明确”：直接在用户当前可见页面输入公开初始夹具，仍出现拒绝；帮助列举多个公开口令也未说明它们不是任选。未读取用户输入的密码或尝试猜测当前重置口令。
2. 排查“当前账户已停用或重置”：源码确认这些状态必须继续拒绝；新增反例证明修复不覆盖它们，不以默认口令绕过认证。
3. 排查“仅有旧标签页代码”：刷新后仍检测到旧版可修复配置；结合 `initialState`、`authenticationAccounts` 和 `authenticateAccount` 证明持久化旧登录元数据缺口。未通过隐藏浏览器存储读取获取用户原始数据。
4. 原有测试只证明缺少场景时拒绝，缺少完整的用户恢复路径。新增 6 个 Node 用例先红（缺少修复 seam），4 个隔离浏览器用例先红（修复入口不存在），实现后全绿。
5. 首次浏览器执行因临时 Playwright 目录缺失失败，恢复固定 1.62.1 后才记录真实产品红灯；依赖错误不算缺陷复现。npm 缓存使用临时目录，未改变用户缓存权限。

## 验证

| 检查 | 结果 |
| --- | --- |
| 新增状态专项 | 6/6：确认、原数据逐字段保留、三角色登录、非法输入、写入回滚、最新页签数据与旧会话失效 |
| 新增浏览器专项 | 4/4：1440 平台、1024 租户、390 普通成员，Escape/焦点、手动登录与刷新、存储失败和重试 |
| 全部 Node / 浏览器 | 137/137、134/134，失败 0 |
| 页面矩阵 | 68 × 7 × 3 = 1,428 场景、9,644 断言、14 阶段通过，异常 0 |
| 当前控件枚举 | 56 × 7 = 392 场景、5,733 实例，页面错配、未接线、异常均为 0；干净数据枚举不替代旧快照专项 |
| 静态 / OpenDesign lint | 3,177 断言通过；P0/P1/P2 均为 0 |
| OpenDesign 保存 | 同一项目保存 87 个代码、设计、夹具、脚本及资源文件，原始预览 SHA-256 全部一致 |
| 截图 | 矩阵 85 张仅在系统临时目录，不提交项目 |
| 项目治理 | `project-status`、`project-verify` 通过：14 个治理测试、20 个工作包；`resume-context` 为 8,913 字节，低于 12 KiB |

机器报告：

- `docs/prototypes/wp-0016/artifacts/login-repair-verification-20260907.json`
- `docs/prototypes/wp-0016/artifacts/opendesign-login-repair-save-20260907.json`
- `docs/prototypes/wp-0016/artifacts/static-verification.json`

完整可复现命令（从仓库根目录执行，浏览器使用独立合成数据）：

```sh
export PROTOTYPE_BASE_URL='http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html'
export NODE_PATH=/private/tmp/wp0016-playwright/node_modules
node --test docs/prototypes/wp-0016/scripts/*.test.mjs
node --test docs/prototypes/wp-0016/scripts/*.test.cjs
node docs/prototypes/wp-0016/scripts/verify-static.mjs
PROTOTYPE_ARTIFACT_DIR=/private/tmp/wp0016-login-repair-matrix-20260907 node docs/prototypes/wp-0016/scripts/verify-and-capture.cjs
PROTOTYPE_CONTROL_REPORT=/private/tmp/wp0016-login-repair-controls-20260907.json node docs/prototypes/wp-0016/scripts/enumerate-controls.cjs
OPENDESIGN_SAVE_REPORT=/private/tmp/wp0016-login-repair-opendesign-save-20260907.json node docs/prototypes/wp-0016/scripts/save-opendesign.cjs
ELECTRON_RUN_AS_NODE=1 OD_SIDECAR_IPC_PATH=/tmp/open-design/ipc/release-stable/daemon.sock '/Applications/Open Design.app/Contents/Frameworks/Open Design Helper.app/Contents/MacOS/Open Design Helper' '/Applications/Open Design.app/Contents/Resources/app/prebundled/daemon/daemon-cli.mjs' lint '/Volumes/slm-ssd/projects/ai-project/ai-platform/docs/prototypes/wp-0016/index.html' --fail-on p1 --json
make project-status
make project-verify
make resume-context
```

OpenDesign 0.21.1，项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`。当前 MCP `get_project` 与保存脚本都确认原项目目录，未另建项目或使用系统 `/usr/bin/od`。

## 工程助手、图谱与六维判断

- 运行环境 Codex；能力来自已安装 `engineering-assistant` 插件。当前限定缺陷直接选择最小技能 `engineering-assistant:diagnosing-bugs`，并显式加载 `engineering-assistant:tdd`、`engineering-assistant:prototype`；遵循 TDD 的 `security-seams.md` 与原型 UI/逻辑参考。未使用 Hermes，也未启动其他 Agent。
- 控制面：Charter、STATE、WP-0016 及其全部 11 个 ADR。原型身份修复不是生产密码恢复，不改变 ADR-0075/0076 的权限或安全策略合同。
- 图谱探索登录区域；探索结果有闭包匹配过宽限制，随后读取精确未覆盖区间。节点 `initialState` 确认旧快照加载；`renderConsoleLogin` 影响分析为 6 个符号，`authenticateAccount` 为 4 个符号。由此把修复限制在原型状态和两个既有登录入口，不修改生产认证或自动迁移全部数据。
- 正确性：新增 10 个专项红绿与全量回归证明保留业务数据、登录与拒绝语义。
- 复杂度：使用现有存储、公开场景和弹窗；3 个局部状态函数与一个共享展示函数，无新架构层。
- 冗余：两个登录入口共享修复展示和事件处理；内置身份复用现有 fixtures，没有第二套默认账户列表。
- 性能：仅登录页读一次本地演示快照；修复按钮确认时一次同步原子写入，无轮询、网络认证或后台迁移。筛选的内置候选最多 5 个，不在聊天增量链路执行。
- 架构：所有变更在可丢弃原型目录；生产四层依赖、Spring AI、权限和 API 不受影响。
- 测试质量：既测试干净环境也构造旧快照；覆盖三角色、旧口令失效、跨页签最新写入、存储失败、取消和键盘，避免仅断言按钮接线。个人 Chrome 只做非破坏诊断，与隔离测试分开报告。
- 安全 seam：本轮涉及合成身份、存储与错误投影；未知/停用/重置状态继续失败关闭。真实 HTTP、SSRF、密钥加解密、数据库授权、模型调用未触及，不假装增加对应安全证明。
- 未产生 `StageRunResult`，无活动持久化质量节点；阶段结果校验与 Runtime `QualitySnapshot` 不适用，六维判断直接登记于本证据。

## 人工下一步

在原登录页选择“修复演示登录 → 确认修复”，然后用 `platform.admin.demo / demo-only` 登录；用户确认前不代为执行。技术回归允许再次复核，原人工拒绝仍有效，WP-0016 保持进行中。
