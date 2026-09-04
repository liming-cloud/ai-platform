# WP-0016 平台人员账户生命周期原型证据

## 结论

实现提交 `64b95170f` 在 OpenDesign 同源高保真原型中闭环平台人员账户建立、一次性初始密码、他人密码重置、平台管理员设置、账户停用与恢复。创建、取消、错误定位、刷新恢复和三档视口已有真实 Chromium 结果；密码不进入页面反馈、合成状态或浏览器存储。

这只关闭 `platform-accounts` 的已登记切片。`ACTION-LEDGER.json` 当前登记 99 项操作或待设计任务，仍有 15 项未实现或待决策，且尚未穷举全部控件。2026-09-03 的人工拒绝继续有效，`WP-0016` 保持进行中，不推进生产前端或 `WP-0017`。

## 产品与安全边界

- 账户是全局人员身份，不建立“租户共用账号”。平台管理员创建人员账户后，租户流程只选择符合资格的账户。
- 登录名沿用生产公开契约的 3–64 位规则；密码只沿用现有非空和最大 1,024 字符边界，不在原型中虚构新的生产密码策略。
- 初始密码和重置密码提交后不回显、不返回、不持久化；界面只记录非秘密认证修订。
- 密码重置、平台管理员设置和账户停用明确撤销既有会话；恢复账户不会恢复旧会话。
- 当前账户不能自重置或自停用；租户成员不能从账户页直接提升为平台管理员；最后一个可用平台管理员不能被停用。
- 邮件邀请、自助激活与用户自行设置密码仍是待决策项，本切片没有伪造入口。

## 红绿证据

先新增 `account-lifecycle-state.test.mjs` 与 `account-lifecycle.browser.test.cjs`：

- 状态红灯 `artifacts/account-lifecycle-state-red.tap`：0/5，证明账户状态能力不存在。
- 浏览器红灯 `artifacts/account-lifecycle-browser-red.tap`：0/7，证明创建、重置和治理入口不可操作；失败截图位于 `artifacts/account-lifecycle-browser-red-20260904/`。
- 状态绿灯 `artifacts/account-lifecycle-node-green.tap`：54/54，包含账户 5 项及既有租户、问答、安全投影回归。
- 浏览器绿灯 `artifacts/account-lifecycle-browser-green.tap`：45/45，其中账户 7、租户 10、问答 15、布局 13。

账户浏览器用例实际覆盖：

1. 建立账户后刷新可见，初始密码不回显且不落 `localStorage`。
2. 密码重置取消恢复焦点，确认时强制勾选撤销会话，认证修订刷新恢复。
3. 租户成员禁升、平台管理员设置、停用和恢复均产生确定结果。
4. 重复登录名和两次密码不一致时定位首个错误。
5. 390、1024、1440 宽度无横向溢出，主操作和弹窗可用。

## 全量与 OpenDesign 验证

- `node docs/prototypes/wp-0016/scripts/verify-static.mjs`：PASS；68 页、7 状态、3 视口、427 个共享渲染场景、1,428 个 HTTP 场景、4,036 项断言。
- `NODE_PATH=/private/tmp/wp0016-playwright/node_modules PROTOTYPE_ARTIFACT_DIR=docs/prototypes/wp-0016/artifacts/account-lifecycle-final-browser-20260904 node docs/prototypes/wp-0016/scripts/verify-and-capture.cjs`：14/14 阶段 PASS；1,428/1,428 场景、9,662 项断言、85 张截图、0 浏览器异常。
- OpenDesign 0.21.1 应用内绝对 CLI 执行 `lint <index.html> --fail-on p1 --json`：P0/P1/P2 均为 0，报告为 `artifacts/account-lifecycle-opendesign-lint.json`；未调用 `/usr/bin/od`。
- 对 1440 和 390 截图进行人工目视复核；根据移动端截图将弹窗标题改为纵向层级，修正窄屏挤压后再次通过账户 7/7、全平台矩阵和 OpenDesign lint。
- `make project-status`、`make project-verify` 与 `git diff --check`：通过。

上述浏览器结论只覆盖报告声明的 DOM、布局、特定交互、导航和截图，不代表操作账本已穷举或人工验收通过。

## 工程方法与范围

- 当前运行环境为 Codex；能力来自已安装的 `engineering-assistant` 插件。
- 使用 `engineering-assistant:wayfinder` 约束当前前沿，使用 `engineering-assistant:prototype` 建立可丢弃界面闭环，使用 `engineering-assistant:tdd` 形成红绿证据，使用 `engineering-assistant:decision-interview` 复核既有决定是否足够，最后用 `engineering-assistant:code-review` 做固定 diff 六维审查。
- 读取 Charter、STATE、WP-0016、当前工作包 8 个 ADR，以及账号规则相关 ADR-0042、ADR-0047、ADR-0061；既有决定已足够，无需新增 ADR。
- 代码图谱先探索 `createPrototypeStore`、`renderInventoryPage`、`renderTenantMembers`、`primaryAction`、`contentFor` 和 `pageDialog`，并核对生产 `PlatformAccountEstablishmentRequest`、`PlatformAccountPasswordResetRequest`、`SubjectIdentity`、`Pbkdf2PasswordAdapter`。影响分析显示 `createPrototypeStore` 影响 6 个符号、`renderInventoryPage` 影响 2 个、`primaryAction` 影响 5 个，因此修改限定在原型状态、账户页渲染、交互、测试和元数据，没有触碰生产代码。
- 固定 diff 评审未发现未解决的任务内缺陷；未新增依赖、网络请求、真实凭据或生产接口。
- 未启动持久化 Runtime flow，未产生 `StageRunResult` 或 `QualitySnapshot`，阶段结构校验不适用。

## 下一切片

仍在 `WP-0016` 内先闭环知识库创建和多类型数据接入，再处理索引激活、模型、助手发布与评测。人工确认前不修改生产前端，不进入 `WP-0017`。
