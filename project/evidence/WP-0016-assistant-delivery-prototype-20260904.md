# WP-0016 助手交付原型证据

## 结论

实现提交 `f74d8ea0a3ef126a5cc645ceab68e4b3cb9fe05a` 在同一 OpenDesign 高保真原型中闭环稳定助手创建、知识库永久绑定、不可变助手版本、在线测试、冻结评测、发布审批和原子发布。OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 继续作为原型内容与视觉事实源；仓库只保留原型契约、状态代码、测试和机器报告，本轮没有新增截图。

本证据只关闭 `WP-0016` 的助手交付切片。独立评测数据集、评测运行、版本对比和失败分析仍未形成完整可操作闭环；2026-09-03 的两次人工拒绝继续有效，`WP-0016` 保持进行中，不修改生产前端，不进入 `WP-0017`。

## 产品与安全边界

- 稳定助手永久绑定一个知识库；只有当前租户且存在活动索引的知识库可以被选择。创建中的新索引候选不会使旧活动索引失去资格。
- 同一稳定助手同一时刻只允许一个活动候选版本。助手版本冻结知识库、活动索引、Chat／Embedding／Rerank 配置版本、检索策略、引用策略和拒答策略；修改必须创建新版本。
- 版本测试使用确定性的测试结果编号并保留问题、答案、引用与处理轨迹；测试结果可以明确加入冻结评测集，但不能绕过完整质量门禁直接发布。
- 评测门禁使用 M6 已确认阈值：跨租户或越权泄漏为 0、`Recall@10 >= 0.85`、引用正确率 `>= 0.95`、拒答准确率 `>= 0.98`、混合检索 p95 `< 800 ms`、首 token p95 `< 4 s`，并要求五次重复无事实矛盾或无依据引用。
- 发布必须经过请求、影响确认和授权管理员操作。发布只原子更新稳定助手的活动版本指针；历史会话继续使用创建时冻结的助手版本与索引版本。
- 本原型不执行真实检索、模型调用、授权校验或数据库事务，不把合成状态机测试冒充生产安全、准确率和性能证明。

## 红绿与回归证据

- 首轮助手状态测试为 0/6，证明原页面只有静态夹具或通用跳转，缺少稳定身份、版本冻结、测试、评测与发布的持续状态。
- 修复了“知识库正在构建候选索引便无法创建助手”的产品缺陷：资格改为检查活动索引存在，而不是依赖知识库聚合的瞬时 `READY` 状态。
- `node --test docs/prototypes/wp-0016/scripts/*.test.mjs`：79/79 通过。
- 使用锁定的 Playwright 1.62.1 执行全部 `*.test.cjs`：79/79 通过，其中助手交付 8/8、模型治理 11/11、索引 7/7、知识接入 8/8、账户 7/7、租户 10/10、问答 15/15、布局 13/13。
- `node docs/prototypes/wp-0016/scripts/verify-static.mjs`：PASS；68 页、10 个导航组、7 状态、3 视口、315 个静态渲染场景、1,428 个 HTTP 场景、3,411 项断言。
- `verify-and-capture.cjs`：14/14 阶段 PASS；1,428/1,428 场景、9,644 项断言、0 浏览器异常。85 张视觉核对图只在系统临时目录生成，仓库机器报告为 `docs/prototypes/wp-0016/artifacts/assistant-delivery-browser-verification.json`。
- OpenDesign 0.21.1 绝对 CLI 执行 `lint docs/prototypes/wp-0016/index.html --fail-on p1 --json`：P0/P1/P2 均为 0；报告为 `docs/prototypes/wp-0016/artifacts/assistant-delivery-opendesign-lint.json`。
- 直接以 OpenDesign 原始项目端点运行 `assistant-delivery.browser.test.cjs`：8/8 通过。仓库与 OpenDesign 端点中的 `prototype.js` SHA-256 均为 `a36d26cd3c65d7f5333608f8997c04b16194c25b486e14d3a7c47153587cbdcf`，`prototype-state.js` 均为 `ce139b758eca553746bdb5f51c816344cce3b8a6675bbb3da7b41f1caed74257`，`prototype.css` 均为 `5d700a36dc3fe3ec56e74157a8521581f85ec5d7096392595a5b2f653d728dd7`，`fixtures.json` 均为 `2755f806d3d7550faebef3d5445a5785f927835fec179b7b5662eb12aaf03265`，无需重新导入。
- `ACTION-LEDGER.json` 声明值与逐项计算一致：118 个已登记操作，其中 38 个导航、64 个状态变更、6 个读取、1 个待决策、8 个未实现和 1 个禁止项；9 个未闭环项且清单仍未穷举全部控件。
- `git diff --check`、`make project-status` 与 `make project-verify`：通过。

## 工程方法与影响范围

- 当前运行环境为 Codex；能力来自已安装的 `engineering-assistant` 插件。
- 使用 `engineering-assistant:wayfinder` 约束唯一前沿，使用 `engineering-assistant:prototype` 建立可丢弃交互，使用 `engineering-assistant:tdd` 先复现再实现，使用 `engineering-assistant:code-review` 做提交前固定差异审查；视觉预检使用 `design-taste-frontend`，保持 Fluent 企业管理台方向和用户确认的 `7/5/7` 参数。
- 读取 Charter、STATE、WP-0016 及其全部活动 ADR；以 `DESIGN.md`、`BUSINESS-CLOSURE.md`、`ACTION-LEDGER.json`、ADR-0075、ADR-0076 和 ADR-0077 约束权限语义、事实源及截图边界。
- 代码图谱先探索 `renderScreen`、`renderInventoryPage`、`bindInteractions`、`createPrototypeStore` 与 `renderAssistantRelease`；影响分析显示 `renderInventoryPage`、`createPrototypeStore`、`renderScreen` 分别影响 6、3、16 个符号。原有助手创建、草稿、测试和评测只是通用页面的伪导航，发布页只是静态夹具，因此修改限定在原型状态、六个助手页面、交互、测试和控制面，没有触碰生产代码。
- 固定差异评审纠正候选索引期间的知识库资格、历史会话版本漂移、非确定性测试编号和伪发布导航；未新增生产依赖、真实模型请求、公共 API 或数据库结构。
- 未启动持久化 Runtime flow，未产生 `StageRunResult` 或 `QualitySnapshot`，阶段结构校验不适用。

## 下一切片

仍在 `WP-0016` 内把独立评测数据集、评测运行、基线对比和失败分析改为可操作闭环。人工确认前不修改生产前端，不进入 `WP-0017`。
