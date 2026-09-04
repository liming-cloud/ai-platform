# WP-0016 模型治理原型证据

## 结论

实现提交 `b328085d0b9e76e6d5709815b8438fe6d89d7048` 在同一 OpenDesign 高保真原型中闭环平台模型供应链、租户模型发现、版本化配置、租户部署和有限回退路由。OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 继续作为原型内容与视觉事实源；仓库只保留原型契约、状态代码、测试和机器报告，本轮没有新增截图。

本证据只关闭 `WP-0016` 的模型治理切片。助手创建、版本测试、发布和评测仍未全部形成可操作闭环；2026-09-03 的人工拒绝继续有效，`WP-0016` 保持进行中，不修改生产前端，不进入 `WP-0017`。

## 产品与安全边界

- 平台管理员建立供应商连接，提交一次性访问密钥，探测通过后发布连接；再从发现目录建立平台部署，冻结模型能力、驻留和租户授权，验证通过后发布。密钥不进入状态、页面反馈或浏览器存储。
- 租户管理员只能发现平台已发布且显式授权给当前租户的目录模型；配置启用时再次检查发布与租户授权，不能仅信任创建时快照。
- 配置和部署使用显式版本状态。新配置验证与启用前旧活动版本持续服务；平台或租户部署发布都不自动切换助手。
- Embedding 维度、规范化或距离度量变化只为同租户知识库建立不可变全库索引候选，不自动激活；Chat/Rerank 只记录助手重新评测要求，不重建向量。
- 路由只允许当前租户、同能力、同驻留的两个不同已发布部署。停用被助手或路由引用的部署必须确认影响；之后调用失败关闭，不跨能力静默回退。
- 标准 Chat/Embedding 在产品语义中使用 OpenAI 兼容运行接口；Rerank 明确走独立检索后处理适配。本原型不执行真实供应商调用、凭据托管或生产 SSRF 防护，不把合成校验冒充生产安全证明。

## 红绿与回归证据

- 首轮模型状态测试为 0/7，浏览器为 7/11，证明原页面仅能跳转，缺少连接、配置、部署和路由的持续状态与真实结果。
- 提交前评审追加“已发布连接重新探测不得替换目录模型标识”“路由引用也必须确认停用影响”“配置启用重新检查平台授权”“路由拒绝跨租户与相同主备”失败用例，修正后模型状态为 8/8。
- `node --test docs/prototypes/wp-0016/scripts/*.test.mjs`：73/73 通过。
- 使用锁定的 Playwright 1.62.1 执行全部 `*.test.cjs`：71/71 通过，其中模型治理 11/11、索引 7/7、知识接入 8/8、账户 7/7、租户 10/10、问答 15/15、布局 13/13。
- `node docs/prototypes/wp-0016/scripts/verify-static.mjs`：PASS；68 页、7 状态、3 视口、350 个静态渲染场景、1,428 个 HTTP 场景、3,607 项断言。
- `verify-and-capture.cjs`：14/14 阶段 PASS；1,428/1,428 场景、9,650 项断言、0 浏览器异常。85 张视觉核对图只在系统临时目录生成，仓库机器报告为 `docs/prototypes/wp-0016/artifacts/model-governance-browser-verification.json`。
- OpenDesign 0.21.1 绝对 CLI 执行 `lint docs/prototypes/wp-0016/index.html --fail-on p1 --json`：P0/P1/P2 均为 0；报告为 `docs/prototypes/wp-0016/artifacts/model-governance-opendesign-lint.json`。
- 直接以 OpenDesign 原始项目端点运行 `model-governance.browser.test.cjs`：11/11 通过。仓库与 OpenDesign 端点中的 `prototype.js` SHA-256 均为 `07b3574b9a142ab7b3e42cf204b45d857786d8d7e354fa944fe08ca66210a9da`，`prototype-state.js` 均为 `c0105d141b411a4e12dfb57928a11c211d3cc9dee0039f4ffc8f4854703c898e`，无需重新导入。
- `ACTION-LEDGER.json` 声明值与逐项计算一致：118 个已登记操作，其中 42 个导航、59 个状态变更、6 个读取、1 个待决策、9 个未实现和 1 个禁止项；10 个未闭环项且清单仍未穷举全部控件。
- `git diff --check`、`make project-status` 与 `make project-verify`：通过。

## 工程方法与影响范围

- 当前运行环境为 Codex；能力来自已安装的 `engineering-assistant` 插件。
- 使用 `engineering-assistant:wayfinder` 约束唯一前沿，使用 `engineering-assistant:prototype` 建立可丢弃交互，使用 `engineering-assistant:tdd` 先复现再实现，使用 `engineering-assistant:code-review` 做提交前固定差异审查；视觉预检使用 `design-taste-frontend`，保持 Fluent 企业管理台方向和用户确认的 `7/5/7` 参数。
- 读取 Charter、STATE、WP-0016 及其全部活动 ADR；以 `DESIGN.md`、`BUSINESS-CLOSURE.md`、`ACTION-LEDGER.json`、ADR-0075、ADR-0076 和 ADR-0077 约束权限语义、事实源及截图边界。
- 代码图谱先探索 `createPrototypeStore`、`renderInventoryPage`、`renderScreen` 与 `bindInteractions`；影响分析显示 `createPrototypeStore`、`renderInventoryPage`、`renderScreen` 分别影响 2、6、16 个符号。因此修改限定在原型状态、11 个模型治理页面、交互、测试和控制面，没有触碰生产代码。
- 固定差异评审关闭目录标识漂移、跨租户路由、授权失效和停用路由影响缺陷；未新增生产依赖、真实模型请求、凭据、公共 API 或数据库结构。
- 未启动持久化 Runtime flow，未产生 `StageRunResult` 或 `QualitySnapshot`，阶段结构校验不适用。

## 下一切片

仍在 `WP-0016` 内把助手创建、固定知识库、版本测试、冻结评测和发布改为可操作闭环，再收口评测治理。人工确认前不修改生产前端，不进入 `WP-0017`。
