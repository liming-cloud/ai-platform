# WP-0016 访问策略与查询治理原型证据

## 结论

实现提交 `6ffa04c83ecac217388bf1ec12e20b49b466c2d4` 在既有 OpenDesign 高保真原型中闭环访问策略版本、查询配额查看、脱敏运行目录、冻结诊断事实和去重修复事项。截图治理提交 `c18774c93bfd5625167c45537d2731d5636f6351` 按用户明确要求移除 `artifacts/` 下 548 张历史核对截图，并补齐 PNG、JPG、JPEG、WebP 忽略规则；原型实际使用的 SVG 图片资源不受影响。

本证据只关闭 `WP-0016` 的访问策略与查询治理切片。结构化数据版本、对象存储范围、渠道绑定和设备凭据等操作仍未闭环；邮件邀请仍待独立决策。2026-09-03 的人工拒绝继续有效，`WP-0016` 保持进行中，不修改生产前端，不进入 `WP-0017`。

## 产品与安全边界

- 访问策略只向活动普通成员授予一个已发布助手；知识库从助手的永久绑定自动派生，不允许独立选择或形成助手与知识库的交叉授权。
- 发布前必须生成允许和拒绝样本预览。策略版本发布后不可变；更换助手时先移除旧助手范围再授予新助手，避免授权累积。
- ADR-0018 将查询配额定义为受控部署配置，因此页面只读展示 UTC 日配额、并发和租约，不提供在线修改入口。
- 查询目录只展示合成运行编号、助手、终态和耗时；问题、答案、引用、租户内部标识和供应商原始错误均不披露。
- 查询诊断读取终态原子提交的历史事实，不重新运行检索，也不以当前授权或配置推断历史结果。已回答运行没有修复入口；拒答和失败运行可建立只含最小引用字段的幂等修复事项。
- 本原型不执行真实授权查询、配额扣减、模型调用、查询重放或工单创建，不能作为生产安全、容量或性能结论。

## 红绿与回归证据

- 首轮状态测试为 0/7，证明三个页面原本没有策略发布、配额读取、冻结诊断和修复事项状态能力；先保留红灯再实现。
- `node --test docs/prototypes/wp-0016/scripts/*.test.mjs`：92/92 通过，其中访问策略与查询治理状态测试 7/7。
- 使用锁定 Playwright 1.62.1 执行全部 `*.test.cjs`：94/94 通过，其中访问策略与查询治理 8/8。
- `node docs/prototypes/wp-0016/scripts/verify-static.mjs`：PASS；68 页、10 个导航组、7 状态、3 视口、273 个共享静态渲染场景、1,428 个 HTTP 场景、3,177 项断言。
- `verify-and-capture.cjs`：14/14 阶段 PASS；1,428/1,428 场景、9,642 项断言、0 浏览器异常。85 张当次核对图只生成在 `/private/tmp`，机器报告为 `docs/prototypes/wp-0016/artifacts/access-query-governance-browser-verification.json`。
- OpenDesign 0.21.1 绝对应用内 CLI 执行 `lint docs/prototypes/wp-0016/index.html --fail-on p1 --json`：P0/P1/P2 均为 0；报告为 `docs/prototypes/wp-0016/artifacts/access-query-governance-opendesign-lint.json`。
- 直接以 OpenDesign 原始项目端点运行 `access-query-governance.browser.test.cjs`：8/8 通过。OpenDesign 项目目录直接指向仓库原型；`prototype.js`、`prototype-state.js`、`prototype.css`、`page-inventory.json` 和 `page-fixtures.json` 的 SHA-256 分别为 `1b22d690449767327c0e072d50e5e1589cb41801f14ede0a0a87d03e34e4e888`、`728f3238490761bd8652837eb77bd9c0d9fae4b9d74b22305a3acf3e4199edfb`、`5e9ad2e2a428d7f0df98f4413ece20ee1cea0c17e4e1196f15a55dec95ed3d68`、`13966af151f65a81b2e60c557254b397db87344860821b7ebede0f62933b0260`、`52656a7af80a0de953aecb9a25eb6112ac5bfb586a39fba356bc878f08023ace`。
- `ACTION-LEDGER.json` 声明值与逐项计算一致：118 个已登记操作，其中 33 个导航、72 个状态变更、7 个读取、1 个待决策、4 个未实现和 1 个禁止项；5 个未闭环项且清单仍未穷举全部控件。
- `git ls-files docs/prototypes/wp-0016/artifacts` 中 PNG、JPG、JPEG、WebP 数量为 0；本轮机器报告不包含图片文件。

## 工程方法与影响范围

- 当前运行环境为 Codex；能力来自已安装的 `engineering-assistant` 插件。
- 使用 `engineering-assistant:wayfinder` 约束唯一前沿，使用 `engineering-assistant:prototype` 建立可丢弃交互，使用 `engineering-assistant:tdd` 先复现再实现，使用 `engineering-assistant:code-review` 做固定差异审查；视觉预检使用 `design-taste-frontend`，保持用户确认的 `7/5/7` 参数。
- 读取 Charter、STATE、WP-0016 及其活动 ADR，并以 ADR-0018、ADR-0027、ADR-0038、ADR-0057、ADR-0075、ADR-0076、ADR-0077 约束查询配额、诊断披露、访问授权、事实源和截图边界。
- 代码图谱先确认三个页面仍落入通用 `renderInventoryPage`，修改后影响分析显示 `renderAccessQueryGovernance` 影响 4 个原型符号，`publishAccessPolicyVersion` 与 `getQueryGovernanceProjection` 各影响 5 个原型符号。修改限定在原型状态、三个页面、交互、测试和控制面，没有触碰生产代码。
- 固定差异评审修正了“在线调整查询配额”与 ADR-0018 的冲突，并补强更换助手撤销旧授权、知识库派生、已回答运行无修复入口和修复事项不复制正文等边界。
- 未启动持久化 Runtime flow，未产生 `StageRunResult` 或 `QualitySnapshot`，阶段结果校验不适用。

## 下一切片

仍在 `WP-0016` 内闭环结构化数据版本与对象存储范围，再评估渠道绑定和设备凭据是否已有足够决策依据。人工确认前不修改生产前端，不进入 `WP-0017`。
