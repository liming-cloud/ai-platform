# WP-0019 S1：拒绝态恢复入口原型候选

| 字段 | 值 |
| --- | --- |
| 输入 | 5456d51923ab55344cf629d2b19a1cbeba2eb485；[完整地址子任务](WP-0019-S1-address-production-delivery-20261015.md)已闭环并推送，治理 CI 38705951957 通过 |
| 候选 | 877a813652da2c96f960b71e29ed7a14eccb89c9；技术验证通过，待人工接受 |
| 仍有效的已接受版本 | dc470d95d8dd905d65f2c3fadeb865e78b0d20a9 |
| 生产前端 | 12bbb1615e718714bb6ea2ba6f2ca3f3ebfb0e22；本候选没有部署或承接生产 403 |
| 生产后端 | 7906451e3f2e04a03e098e601b37425e29857cb5；未修改 |
| 范围 | 仅原项目两页拒绝态的恢复目的地及现行质量反例，不改变权限、数据、布局或其他切片 |

## 原因、方案与真实红绿

此前[状态排查](WP-0019-S1-address-candidate-and-state-audit-20261015.md)已实际确认：平台模型连接和供应商连接探测的“返回已授权入口”进入 `assistants`，平台身份再次被拒绝，恢复操作形成死路。原型 `genericState` 对这两页落到了普通会员的默认目的地。

以已接受的既有“租户管理”作为平台范围恢复入口，不新增页面或权限。图谱同时核对生产 `defaultConsoleRoute("platform")` 的既有租户目录入口，以及原型 `renderAccessDenied` 按身份返回工作区的守卫；候选不改变后者，不将平台入口交给租户或会员。

先增加三尺寸浏览器反例，在真实 200% 可见文字下点击两页恢复按钮，原版本三项均因实际目的地 `assistants` 不符而失败。只为两个精确 screen 增加 `platform-tenants` 分支，其余原条件保持不变；同一三项转绿。另通过原型正式租户登录表单复验：直接进入平台连接页仍被拒绝、没有平台数据或新增恢复按钮，返回自己的租户工作区正常。不注入身份或改写权限状态。

## 验证与原项目绑定

- 冻结模型浏览器及现行 S1 回归共 21 项通过，包含已接受的完整地址、手机重排和本次拒绝态；冻结历史脚本摘要保持原值。
- 模型状态机／预设 14 项通过；两页七态、三尺寸、正常／200% 可见文本 84 次观察通过，页面异常及单元格／分页重叠为 0。
- `make architecture-guard` 50 项及守卫、`make project-verify` 81 项／30 个任务通过；不以原型结果证明生产拒绝态一致。
- 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 仍注册于 `docs/prototypes/wp-0016`。官方 MCP、原目录、候选 Git 提交与实际 HTTP 的 17 项渲染依赖逐字节一致；相对接受版本仅 `prototype.js` 变化，无副本、截断或跳过。

[目录拒绝态](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connections&state=denied&review=1)、[详情拒绝态](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connection-detail&state=denied&review=1)。原项目当前显示候选；依据 ADR-0079，已发起单独人工接受，收到决定前不更新接受指针或生产 403 页面。原型操作修订授权不等于候选接受。

## 工具、证据与下一步

沿用 Local Codex 个人工程助手的 `wayfinder`、`tdd`、`code-review`，按对应 SKILL.md、根 AGENTS、任务与 ADR-0079 执行内联诊断、红绿和固定 diff 评审；没有 Runtime／StageRunResult，阶段结果校验不适用。图谱对 `genericState` 做一层影响分析，涉及 20 个符号，因此限定两个精确 screen，并补平台恢复正例和租户隔离反例，不批量更改其他页面。

收口新增摘要后，`project-status` 正确拒绝超过 12 KiB 的恢复上下文。保留失败日志，仅将 README 的重复历史摘要缩为原证据链接，不修改历史报告或校验阈值；README 为 10192 字节、完整恢复输出为 11374 字节，索引同步、状态、81 项治理测试及恢复检查随后通过。

证据、日志和截图在新的 0700 目录 `/private/tmp/wp0019-s1-denied-return-xu42uiwd`，文件 0600；保留原 8 项清单，包含治理恢复结果的 12 项 `closure-safe-evidence-digests.json` SHA-256 为 `84d491d17aed493bac3116153c5d788055c01eba2870c071a8d0c7aec69010c8`，不含凭据、材料或截图摘要。无 fixture 或截图入库。

下一步在接受后承接生产的 403 分类及同一恢复入口，再部署、真实只读验证及原型对照；本轮不冒称已经完成这些步骤。partial／disabled 的真实触发条件、未筛选真实空目录、全部文字放大和新方舟独立认证合同仍待闭合；S1、WP-0019、M6 未完成，不进入 S2。
