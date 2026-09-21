# WP-0016：成员账号与厂商预填闭环

## 结论与绑定

- 实现提交：`0ff24482316c58288f8dd6f8a7e19872fd097c87`。本轮基线 `46948d8a93c3440ba055aeeaa63987610ec9450f`；起点 lastVerifiedCommit `1d670bbce3a807bd92cc70228d332a8ae8f72584` 为其祖先。
- 用户明确允许租户管理员治理本租户普通成员账号，并允许只读调查厂商官方资料；已更新 ADR-0078，ADR-0075 引用其受限重置例外。
- 本轮只推进 WP-0016 可丢弃原型。生产前后端、API、数据库、部署均未修改；不进入 WP-0017。渠道绑定、设备凭据、邮件邀请继续暂缓，人工拒绝仍有效。
- 保留其他任务暂存的 5 个 fixture 迁移，排除本轮提交。浏览器检查使用独立 Chromium 和合成数据；没有读取或修改用户 Chrome 的登录材料，没有自动恢复 qiao.demo。

## 行为闭环

1. 租户成员由管理员直接填写登录名、公开合成口令、对话功能与助手范围。身份、普通成员资格及当前编辑对象由一次快照提交；重复登录名、口令不一致、非法能力、其他租户助手与存储失败均不留半成品。
2. 普通成员可在本租户重置密码、确认停用、恢复。管理员和平台账户不能作为目标；共享于多个租户的身份不允许租户侧重置全局密码。停用只影响当前租户成员资格，恢复不扩大授权；旧口令和会话失败关闭。
3. 手机端成员表单只有内容区滚动，取消与确认按钮始终留在视口。重置共用已有表单和私有口令轮换事务，不复制第二套凭据实现。
4. 厂商预设支持 DeepSeek、北京百炼、中国区硅基流动、北京方舟及自定义连接。仅展示已核实的文本能力；固定地址自动生成，百炼 Chat/Rerank 补业务空间 ID。地址、能力组合在状态层复核；切换、关闭、失败和成功提交不保留访问密钥。
5. 新连接保持草稿，沿用合成探测和发布；列表、详情都显示实际厂商。Rerank 始终标为厂商后处理协议，不把 SDK 自定义 POST 当作统一 OpenAI 标准。

厂商来源与约束统一记录在 OpenDesign 的 `PROVIDER-PRESETS.md`，共享 `provider-presets.js` 为唯一预设。Exa 本轮 7 次定向检索、35 条候选，只采用官方内容；不继承历史搜索数，不调用真实模型或厂商控制面。

## 红绿循环与证据质量

- 新状态用例先得到无法开户、缺少成员重置方法、无操作者检查及缺少厂商预设模块等失败，再实现并回归。
- 浏览器先证明成员登录名输入框和厂商选择器不存在；实现后验证三尺寸开户、轮换、停用、恢复、本人登录，及厂商能力切换和密钥清空。
- 评审发现详情仍硬编码“OpenAI 兼容”为厂商，先得到期望硅基流动、实际 OpenAI 兼容的失败，再统一详情展示。
- 原成员夹具调用同步迁移到直接开户契约，未保留无权限的旧添加分支；原登录、租户隔离、模型、知识、评测、问答专项继续执行。
- 运行状态测试须等待异步渲染完成，成员可见性须定位表格行而不是隐藏区域同名文本。并行检查期间修改文件产生的中间失败不列为最终通过证据，代码固定后重新全量运行。
- 控件枚举原来只等待空壳 main，曾出现实际页面为 null、但退出成功的错误证据。用实际报告先断言失败，改为等待目标页面及状态并强制错配失败。聊天七态先创建可访问的合成会话并固定 conversationId。旧计数 6,452/6,434 均作废，不作为本轮全覆盖结论。

## 验证结果

- Node：151/151，零失败、零跳过。
- 浏览器专项：143/143，零失败、零跳过。
- 完整矩阵：73 × 7 × 3 = 1,533 场景；10,339 断言、14 阶段，运行异常与失败均为零。
- 控件：61 × 7 = 427 个场景，6,517 个控件；页面/状态错配、未接线和运行异常均为零。
- 静态：3,491 断言通过。OpenDesign lint：P0/P1/P2 均为零。
- 目视：临时查看成员开户手机端及百炼重排预填桌面端；成员表单三尺寸验证底部按钮几何可达。矩阵 90 张截图和专项截图仅在系统临时目录，未加入项目。
- OpenDesign 保存：同一项目 96 个原型、代码、设计、夹具与脚本文件，通过 MCP write_file 保存并逐文件核对 raw 响应 SHA-256；截图未加入项目。
- 项目治理：project-status、project-verify 均通过，14 个治理测试、20 个工作包；resume-context 为 6,851 字节，小于 12 KiB。工作包仍进行中，人工未通过。

机器报告：`docs/prototypes/wp-0016/artifacts/member-provider-verification-20260907.json`；保存报告：`docs/prototypes/wp-0016/artifacts/opendesign-member-provider-save-20260907.json`。原型代码、设计与验收入口继续属于同一 OpenDesign 项目，不建立平行项目。

## 复现命令

从仓库根目录执行；OpenDesign 0.21.1，Playwright 1.62.1：

```sh
export PROTOTYPE_BASE_URL='http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html'
export NODE_PATH=/private/tmp/wp0016-playwright/node_modules
node --test docs/prototypes/wp-0016/scripts/*.test.mjs
node --test --test-concurrency=2 docs/prototypes/wp-0016/scripts/*.test.cjs
node docs/prototypes/wp-0016/scripts/verify-static.mjs
PROTOTYPE_ARTIFACT_DIR=/private/tmp/wp0016-member-provider-matrix-verified node docs/prototypes/wp-0016/scripts/verify-and-capture.cjs
PROTOTYPE_CONTROL_REPORT=/private/tmp/wp0016-member-provider-controls-checked.json node docs/prototypes/wp-0016/scripts/enumerate-controls.cjs
OPENDESIGN_SAVE_REPORT=/private/tmp/wp0016-member-provider-save.json node docs/prototypes/wp-0016/scripts/save-opendesign.cjs
ELECTRON_RUN_AS_NODE=1 OD_SIDECAR_IPC_PATH=/tmp/open-design/ipc/release-stable/daemon.sock '/Applications/Open Design.app/Contents/Frameworks/Open Design Helper.app/Contents/MacOS/Open Design Helper' '/Applications/Open Design.app/Contents/Resources/app/prebundled/daemon/daemon-cli.mjs' lint '/Volumes/slm-ssd/projects/ai-project/ai-platform/docs/prototypes/wp-0016/index.html' --fail-on p1 --json
make project-status
make project-verify
make resume-context
```

## 工程助手、图谱与质量评审

- 运行环境为 Codex；能力来自已安装的 engineering-assistant 限定技能。显式加载 engineering-assistant:engineering-assistant、prototype（ui/logic）、tdd（security-seams）、research、code-review（security-seams）及 Exa Search。路由以 prototype 为主，tdd 驱动红绿循环，research 按已批准的只读调查方式执行，code-review 在固定 diff 上审查。
- 使用 Charter、STATE、当前 WP 的 12 个 ADR、OpenDesign 设计和当前证据；没有创建持久 flow、StageRunResult 或 QualitySnapshot，阶段产物校验不适用。
- 图谱聚焦 addMember、resetAccountPassword、createPlatformModelConnection、renderTenantMembers、bindMemberManagement 与 setMemberStatus。影响分析分别返回 9、13、9 个符号；同名生产符号仅为排除范围依据，不据此扩大重构。结果促成替换旧开户入口、复用轮换事务及状态层授权。控件枚举图谱未精确命中目标，依据已知脚本路径与实际错误报告定位，不扩展到无关代码。
- 六维内联评审：正确性（状态与 UI 闭环）；复杂度（一个小预设表，复用现有事务）；冗余（移除已触及的旧账户候选分支）；性能（合成状态内局部处理，不增加远程调用）；架构（只模拟 Port 和固定角色边界）；测试质量（先红后绿、回滚、撤销、越界、输入及三尺寸）。以上适用项以专项和最终报告为据，不能外推到生产安全或模型质量。
- 安全反例矩阵覆盖平台/普通/空操作者、停用管理员/成员资格/租户、跨租户操作者、管理员目标、共享身份、非法助手与功能、存储异常、旧会话和输入不入存储。此 diff 不涉及生产数据库 ID、outbox、dispatcher、日志解码链或模型网络请求，相应生产合同不适用，不用 mock 声称通过。

## 人工下一步

使用 `ACCEPTANCE.md` 的三个入口复核。云杉示例租户为 spruce-demo，管理员 qiao.demo 的未修改初始公开口令为 demo-only；已重置则使用当时设置的口令。用户曾停用的账号必须由平台在对应租户管理员页明确恢复，不自动处理。机器结果不撤销之前的人工拒绝。
