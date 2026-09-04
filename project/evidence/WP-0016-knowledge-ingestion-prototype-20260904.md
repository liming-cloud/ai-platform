# WP-0016 知识库与资料接入原型证据

## 结论

实现提交 `64e4234453a0e4ab7501057ea16b0a02bf6181ca` 在同一 OpenDesign 高保真原型中闭环知识库创建、粘贴文本与本地文件接入、统一处理阶段、失败安全重试和单文档重新处理。OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 继续作为唯一视觉事实源；仓库只保留原型契约、状态代码、测试和机器报告，不保存本轮截图。

本证据只关闭 `WP-0016` 的知识接入切片。全库索引重建与原子激活、模型治理、助手发布和评测仍未全部形成可操作闭环；2026-09-03 的人工拒绝继续有效，`WP-0016` 保持进行中，不修改生产前端，不进入 `WP-0017`。

## 产品与安全边界

- 租户管理员在平台分配的知识库、有效文档、原始内容、单文件和粘贴文本配额内操作；全局账户停用、租户停用、成员停用、非租户管理员或知识管理功能关闭时均失败关闭。
- 创建知识库支持取消、名称校验、重复名称、配额到顶、刷新恢复和存储失败回滚；目录与页头使用同一动态状态，不保留固定数量文案。
- 数据来源只接受粘贴文本或本地文件，不接受远程 URL。首版白名单为 TXT、Markdown、DOC/DOCX、XLS/XLSX、CSV、PDF、PNG、JPEG 和 WebP。
- 扩展名、MIME、大小、加密文件、Office 宏和损坏压缩包在进入处理队列前失败关闭；原型不上传真实内容，不把粘贴正文写入浏览器状态。
- 新资料在接收时预留文档与存储配额，进入 `READY` 时转为有效文档计数；失败重试不会绕过预留，存储失败时整个变更回滚。
- 处理阶段固定为 `RECEIVED -> SCANNING -> EXTRACTING -> EXTRACTED -> CHUNKING -> INDEXING -> READY`。失败保留结构化错误码并从安全阶段重试，未完成内容不进入活动索引。
- 就绪来源重新处理会创建新修订；活动索引和上一活动修订继续服务，不原地覆盖。该交互只验证产品语义，不执行真实解析、OCR、分块、Embedding 或索引写入。

## 红绿与回归证据

- 首轮状态测试在能力不存在时为 0/6；浏览器红灯证明创建弹窗、资料表单、可操作来源行、失败重试和重新处理均不存在。提交前审查又以 4/6 复现全局停用账户未拦截和有效文档计数未增长，修复后全部转绿。
- `node --test docs/prototypes/wp-0016/scripts/*.test.mjs`：60/60 通过，其中知识接入 6/6；绿色 TAP 为 `docs/prototypes/wp-0016/artifacts/knowledge-ingestion-node-green.tap`。
- 使用 Codex 工作区 Playwright 1.62.1 执行 `knowledge-ingestion.browser.test.cjs`：8/8 通过，覆盖创建取消与刷新、粘贴阶段推进、非法文件拒绝、失败重试、重新处理、拒绝态最小披露，以及 390、1024、1440 宽度无横向溢出。
- `node --test docs/prototypes/wp-0016/scripts/*.test.cjs`：53/53 通过，包括知识接入 8、账户 7、租户 10、问答 15、布局 13。
- `node docs/prototypes/wp-0016/scripts/verify-static.mjs`：PASS；68 页、7 状态、3 视口、427 个静态渲染场景、1,428 个 HTTP 场景、4,036 项断言。
- `verify-and-capture.cjs`：14/14 阶段 PASS；1,428/1,428 场景、9,662 项断言、0 浏览器异常。85 张视觉核对图仅生成在系统临时目录，JSON 报告为 `docs/prototypes/wp-0016/artifacts/knowledge-ingestion-browser-verification.json`。
- OpenDesign 0.21.1 绝对 CLI 执行 `lint docs/prototypes/wp-0016/index.html --fail-on p1 --json`：P0/P1/P2 均为 0；未调用 `/usr/bin/od`。
- 仓库 `prototype.js` 与 OpenDesign 原始项目端点的 SHA-256 均为 `60e2354b461ba1351970d228307fd7bbd38c615377a6635e1638fabe59cd63a4`，无需重新导入项目。
- `git status --short --untracked-files=all` 中新增 PNG 为 0；实现提交移除上一账户切片新增的 97 张受版本控制截图，并以 `.gitignore` 阻止后续原型 PNG 进入仓库。更早的历史证据未在本切片中扩大清理。
- `make project-status`、`make project-verify` 与 `git diff --check`：通过。

## 工程方法与影响范围

- 当前运行环境为 Codex；能力来自已安装的 `engineering-assistant` 插件。
- 使用 `engineering-assistant:wayfinder` 约束唯一前沿，使用 `engineering-assistant:prototype` 建立可丢弃的真实交互，使用 `engineering-assistant:tdd` 先复现再实现，使用 `engineering-assistant:code-review` 做提交前固定差异审查。设计预检采用企业管理台、Fluent 视觉语言和用户确认的 `7/5/7` 参数；通用营销页规则不扩张到管理台。
- 读取 Charter、STATE、WP-0016 及 ADR-0005、ADR-0027、ADR-0059、ADR-0067、ADR-0072、ADR-0074、ADR-0075、ADR-0076、ADR-0077。
- 代码图谱先探索 `createPrototypeStore`、`renderKnowledgeDirectory`、`renderKnowledgeSources` 和 `bindInteractions`；影响分析显示这些原型符号影响范围局部，因此修改限定在原型状态、四个知识接入页面、交互、测试和控制面，没有触碰生产代码。
- 固定差异评审发现并关闭页头固定计数、有效文档配额计数和全局账户停用检查三处缺陷；未新增依赖、网络上传、真实凭据或生产接口。
- 未启动持久化 Runtime flow，未产生 `StageRunResult` 或 `QualitySnapshot`，阶段结构校验不适用。

## 下一切片

仍在 `WP-0016` 内把全库索引重建、评测门禁和授权管理员原子激活改为可操作闭环，并明确 Embedding、维度、距离度量、解析器版本和分块策略变化只自动创建重建任务、不自动激活。人工确认前不修改生产前端，不进入 `WP-0017`。
