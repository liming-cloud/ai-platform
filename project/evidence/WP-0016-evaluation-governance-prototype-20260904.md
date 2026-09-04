# WP-0016 独立评测治理原型证据

## 结论

实现提交 `2d40292abe943e584822c0bd04d463d337e9545f` 在同一 OpenDesign 高保真原型中闭环独立评测数据集、不可变版本发布、冻结评测运行、同版本基线对比、门禁决定和失败修复事项。OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 继续作为原型内容与视觉事实源；仓库只保留原型契约、状态代码、测试和机器报告，本轮没有新增截图。

本证据只关闭 `WP-0016` 的独立评测治理切片。访问策略发布、结构化数据版本、对象存储范围、交付渠道、查询配额与诊断修复等操作仍未全部形成可操作闭环；2026-09-03 的两次人工拒绝继续有效，`WP-0016` 保持进行中，不修改生产前端，不进入 `WP-0017`。

## 产品与安全边界

- 新数据集可以为空白建立，也可以显式复制同知识库的已发布基线；复制品始终成为独立草稿，不修改原版本。
- 数据集版本至少包含 60 个可回答、20 个证据不足拒答、10 个权限拒绝和 10 个多轮样本；可回答和多轮样本必须保存期望证据标识。发布后版本不可变，后续修改创建新草稿。
- 评测运行只接受当前租户、与助手永久知识库一致的已发布数据集，以及已经冻结索引和三类模型配置修订的助手版本。运行期间不读取活动指针。
- 运行按四个阶段推进，同时产出 Recall@10、引用正确率、拒答准确率、越权泄漏、检索 p95、首 Token p95 和五次一致性，并按召回不足、引用偏差和过度拒答聚合失败。
- 版本对比必须来自同一稳定助手和同一数据集版本。安全、质量、性能或一致性回退时不能形成放行决定。
- 修复事项以运行和失败类别去重，只引用类别、数量、事项名称和负责人，不复制评测问题、模型回答或引用正文。
- 本原型不执行真实检索、模型调用、评分、授权查询或工单创建，不把合成测试冒充生产准确率、性能与安全证明。

## 红绿与回归证据

- 首轮评测状态测试为 0/6，证明六个页面原本只有通用静态夹具、伪导航或无结果弹框，缺少可恢复业务状态。
- 状态测试追加同知识库约束和同一稳定助手对比边界；存储失败、普通成员越权和平台关闭评测功能均保持原状态。
- `node --test docs/prototypes/wp-0016/scripts/*.test.mjs`：85/85 通过。
- 使用锁定的 Playwright 1.62.1 执行全部 `*.test.cjs`：86/86 通过，其中独立评测 7/7、助手交付 8/8、模型治理 11/11、索引 7/7、知识接入 8/8、账户 7/7、租户 10/10、问答 15/15、布局 13/13。
- `node docs/prototypes/wp-0016/scripts/verify-static.mjs`：PASS；68 页、10 个导航组、7 状态、3 视口、273 个共享静态渲染场景、1,428 个 HTTP 场景、3,177 项断言。六个评测专属页面由状态测试和真实浏览器覆盖。
- `verify-and-capture.cjs`：14/14 阶段 PASS；1,428/1,428 场景、9,642 项断言、0 浏览器异常。85 张视觉核对图只在系统临时目录生成，仓库机器报告为 `docs/prototypes/wp-0016/artifacts/evaluation-governance-browser-verification.json`。
- OpenDesign 0.21.1 绝对 CLI 执行 `lint docs/prototypes/wp-0016/index.html --fail-on p1 --json`：P0/P1/P2 均为 0；报告为 `docs/prototypes/wp-0016/artifacts/evaluation-governance-opendesign-lint.json`。
- 直接以 OpenDesign 原始项目端点运行 `evaluation-governance.browser.test.cjs`：7/7 通过。仓库与 OpenDesign 端点中的 `prototype.js` SHA-256 均为 `4796f57239fc2b09e934c590436b12503a882122358c50a1b1220ff77179d9f4`，`prototype-state.js` 均为 `27c081ae1c67b48a26b797e905aaaa6194d8df5584255baf551828d6635daca7`，`prototype.css` 均为 `5d700a36dc3fe3ec56e74157a8521581f85ec5d7096392595a5b2f653d728dd7`，`fixtures.json` 均为 `2755f806d3d7550faebef3d5445a5785f927835fec179b7b5662eb12aaf03265`，无需重新导入。
- `ACTION-LEDGER.json` 声明值与逐项计算一致：118 个已登记操作，其中 33 个导航、70 个状态变更、6 个读取、1 个待决策、7 个未实现和 1 个禁止项；8 个未闭环项且清单仍未穷举全部控件。
- `git diff --check`、`make project-status` 与 `make project-verify`：通过。

## 工程方法与影响范围

- 当前运行环境为 Codex；能力来自已安装的 `engineering-assistant` 插件。
- 使用 `engineering-assistant:wayfinder` 约束唯一前沿，使用 `engineering-assistant:prototype` 建立可丢弃交互，使用 `engineering-assistant:tdd` 先复现再实现，使用 `engineering-assistant:code-review` 做提交前固定差异审查；视觉预检使用 `design-taste-frontend`，保持 Fluent 企业管理台方向和用户确认的 `7/5/7` 参数。
- 读取 Charter、STATE、WP-0016 及其全部活动 ADR；以 `DESIGN.md`、`BUSINESS-CLOSURE.md`、`ACTION-LEDGER.json`、ADR-0075、ADR-0076 和 ADR-0077 约束权限语义、事实源及截图边界。
- 代码图谱先探索六个评测 screen、`renderScreen`、`renderInventoryPage`、`bindInteractions` 和 `createPrototypeStore`，确认页面原本全部落入通用渲染且主操作未绑定真实状态。修改后影响分析显示 `renderEvaluationGovernance` 影响 4 个符号，`createEvaluationRun` 影响 5 个符号；修改限定在原型状态、六个评测页面、交互、测试和控制面，没有触碰生产代码。
- 固定差异评审补强数据集与助手知识库一致、同一稳定助手版本比较、发布版本不可变、失败事项不复制正文，并修复既有模型浏览器测试刷新后缺少 DOM 等待的问题。
- 未启动持久化 Runtime flow，未产生 `StageRunResult` 或 `QualitySnapshot`，阶段结构校验不适用。

## 下一切片

仍在 `WP-0016` 内闭环访问策略发布和查询治理，再处理结构化数据、对象范围、交付渠道与设备前置操作。人工确认前不修改生产前端，不进入 `WP-0017`。
