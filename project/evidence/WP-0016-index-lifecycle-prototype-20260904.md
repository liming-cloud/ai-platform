# WP-0016 全库索引生命周期原型证据

## 结论

实现提交 `cd455776d590858bc5d0c8f2770eff56015a2385` 在同一 OpenDesign 高保真原型中闭环全库索引重建、五阶段构建、冻结质量门禁、候选取消和授权管理员原子激活。OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 继续作为原型内容与视觉事实源；仓库只保留原型契约、状态代码、测试和机器报告，本轮没有新增截图。

本证据只关闭 `WP-0016` 的索引生命周期切片。模型治理、助手发布和评测仍未全部形成可操作闭环；2026-09-03 的人工拒绝继续有效，`WP-0016` 保持进行中，不修改生产前端，不进入 `WP-0017`。

## 产品与安全边界

- 租户管理员从索引版本目录进入全库重建；每个页面只承担版本比较、创建候选或处理单个候选版本中的一个主要任务，版本目录只有一套普通分页。
- 重建冻结活动来源清单、Embedding、维度、规范化、距离度量、解析器版本、分块策略和检索策略；配置变化必须声明变化字段。候选版本独立持久化，不原地改写活动索引。
- 构建按 `MANIFEST_FREEZING -> CHUNKING -> EMBEDDING -> HYBRID_INDEXING -> EVALUATING` 推进；构建与评测期间旧活动版本持续服务，同一知识库不能并发启动第二个构建任务。
- 冻结门禁为 `Recall@10 >= 0.85`、引用正确率 `>= 0.95`、拒答准确率 `>= 0.98`、越权泄漏 `= 0`、检索 p95 `< 800 ms`。指标不完整或任一门禁失败时不得激活。
- 授权管理员确认后，候选版本、旧版本状态和知识库活动指针在一次合成持久化提交中切换；取消、越权、存储失败或质量失败均保持旧活动索引。候选评测结束或取消后，知识库状态恢复为活动版本可服务状态。
- 本原型不执行真实分块、Embedding、pgvector、FTS、RRF、rerank 或数据库事务；安全边界和原子性是待生产工作包实现与证明的合同，不是生产能力声明。

## 红绿与回归证据

- 首轮状态红灯为 0/5，证明索引重建、阶段推进、评测、激活和取消能力不存在；浏览器红灯为 3/7，证明目录跳转存在但没有可持续状态和可操作结果。
- 提交前固定差异评审发现“评测结束或取消后知识库仍显示正在索引”的矛盾；新增断言后为 2/5，再把知识库服务状态与候选状态同次提交，最终 5/5。
- `node --test docs/prototypes/wp-0016/scripts/*.test.mjs`：65/65 通过，其中索引生命周期 5/5。
- 使用锁定的 Playwright 1.62.1 执行全部 `*.test.cjs`：60/60 通过，其中索引 7/7、知识接入 8/8、账户 7/7、租户 10/10、问答 15/15、布局 13/13。索引专项覆盖重建、阶段推进、评测、激活取消、构建取消、拒绝态最小披露及 390、1024、1440 三档无横向溢出。
- `node docs/prototypes/wp-0016/scripts/verify-static.mjs`：PASS；68 页、7 状态、3 视口、427 个静态渲染场景、1,428 个 HTTP 场景、4,036 项断言。
- `verify-and-capture.cjs`：14/14 阶段 PASS；1,428/1,428 场景、9,662 项断言、0 浏览器异常。85 张视觉核对图只在系统临时目录生成，仓库机器报告为 `docs/prototypes/wp-0016/artifacts/index-lifecycle-browser-verification.json`。
- OpenDesign 0.21.1 绝对 CLI 执行 `lint docs/prototypes/wp-0016/index.html --fail-on p1 --json`：P0/P1/P2 均为 0；报告为 `docs/prototypes/wp-0016/artifacts/index-lifecycle-opendesign-lint.json`。
- 仓库与 OpenDesign 原始项目端点中的 `prototype.js` SHA-256 均为 `2420ecfdc636be1d0d34e5bf539ce6db34272097ae14949d896cbca4ff148336`，`prototype-state.js` 均为 `1531e5fe2c326e34e37815e7accbd85b5844c18a1dd4d4b72bada26c0929131e`，无需重新导入。
- `ACTION-LEDGER.json` 声明值与逐项计算一致：104 个已登记操作，其中 46 个导航、40 个状态变更、6 个读取、1 个待决策、10 个未实现和 1 个禁止项；11 个未闭环项且清单仍未穷举全部控件。
- `git diff --check`、`make project-status` 与 `make project-verify`：通过。

## 工程方法与影响范围

- 当前运行环境为 Codex；能力来自已安装的 `engineering-assistant` 插件。
- 使用 `engineering-assistant:wayfinder` 约束唯一前沿，使用 `engineering-assistant:prototype` 建立可丢弃交互，使用 `engineering-assistant:tdd` 先复现再实现，使用 `engineering-assistant:code-review` 做提交前固定差异审查。设计预检沿用企业管理台、Fluent 视觉语言和用户确认的 `7/5/7` 参数。
- 读取 Charter、STATE、WP-0016 及其全部活动 ADR；以 `DESIGN.md`、`BUSINESS-CLOSURE.md`、`ACTION-LEDGER.json` 和 ADR-0077 约束 OpenDesign 事实源及截图边界。
- 代码图谱先探索 `renderIndexDetail`、`renderInventoryPage`、`createPrototypeStore` 和 `bindInteractions`；影响分析显示前两者分别影响 4 和 6 个符号，`createPrototypeStore` 影响 3 个符号。因此修改限定在原型状态、三个索引页面、交互、测试和控制面，没有触碰生产代码。
- 固定差异评审关闭知识库状态复位缺陷；未新增生产依赖、真实模型调用、网络上传、凭据或公共 API。
- 未启动持久化 Runtime flow，未产生 `StageRunResult` 或 `QualitySnapshot`，阶段结构校验不适用。

## 下一切片

仍在 `WP-0016` 内把模型供应商、模型配置、连接测试、停用影响和 Chat／Embedding／Rerank 使用边界改为可操作闭环，再推进助手发布与评测。人工确认前不修改生产前端，不进入 `WP-0017`。
