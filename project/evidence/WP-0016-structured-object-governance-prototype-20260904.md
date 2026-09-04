# WP-0016 结构化数据与对象范围原型证据

## 结论

实现提交 `ef190e685793b96a914de49db28b7a048f6c8073` 在既有 OpenDesign 高保真原型中闭环结构化数据版本与对象范围登记。结构化数据页可保存 CSV 字段规则、执行抽样预检并发布不可变版本；对象范围页只允许把平台为当前租户签发的文本范围登记为待探测知识来源，不接收或披露桶名、对象键、端点和访问凭据。

本证据只关闭 `WP-0016` 的结构化数据与对象范围切片。渠道绑定、设备凭据和邮件邀请仍受既有决策边界约束；2026-09-03 的人工拒绝继续有效，`WP-0016` 保持进行中，不修改生产前端，不进入 `WP-0017`。

## 产品与安全边界

- CSV 标题顺序、逻辑类型、业务含义、查询资格、敏感级别、正文与标题字段、空行策略和 `dataset-csv-v1` 质量规则共同冻结为结构版本；页面和浏览器状态不保存样本行或字段值。
- 主内容与标题只能选择公开、可查询的文本字段；敏感字段不能进入查询投影。标题不一致、规则不完整、未预检、已发布版本修改、越权和存储失败均失败关闭。
- 发布新结构版本不自动重建或切换活动索引，避免把结构调整伪装成已生效检索能力。
- 对象范围由平台预先签发并冻结选择版本。租户知识管理员只能选择当前租户可用范围和目标知识库，登记结果为 `待探测`，不自动同步或发布。
- 读取投影和登记结果只包含业务名称、驻留区域、对象数量、媒体类型、范围版本与知识库，不包含对象位置、访问身份或凭据。
- 本原型不解析真实 CSV、不读取对象存储、不创建生产来源或索引，不能作为生产数据接入和安全结论。

## 红绿与回归证据

- 状态测试覆盖 8 个行为：冻结字段规则且无样本值、字段安全校验、预检与不可变发布、失败回滚、对象范围脱敏投影、同租户登记与刷新恢复、幂等及越权拒绝、功能关闭失败关闭，8/8 通过。
- 使用锁定 Playwright 1.62.1 执行专项浏览器测试：结构版本生命周期、对象范围登记、拒绝态、不可用态及 390/1024/1440 三档布局共 7/7 通过。
- `node --test docs/prototypes/wp-0016/scripts/*.test.mjs`：100/100 通过；全部专项浏览器测试：101/101 通过。
- `node docs/prototypes/wp-0016/scripts/verify-static.mjs`：PASS；68 页、10 个导航组、7 状态、3 视口、273 个共享静态渲染场景、1,428 个 HTTP 场景、3,177 项断言。
- `verify-and-capture.cjs`：14/14 阶段 PASS；1,428/1,428 场景、9,642 项断言、0 浏览器异常。85 张当次核对图只生成在 `/private/tmp`，机器报告为 `docs/prototypes/wp-0016/artifacts/structured-object-governance-browser-verification.json`。
- OpenDesign 0.21.1 绝对应用内 CLI 执行 `lint docs/prototypes/wp-0016/index.html --fail-on p1 --json`：P0/P1/P2 均为 0；报告为 `docs/prototypes/wp-0016/artifacts/structured-object-governance-opendesign-lint.json`。
- 直接以 OpenDesign 原始项目端点运行专项浏览器测试：7/7 通过。OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 直接指向仓库原型；`prototype.js`、`prototype-state.js`、`prototype.css`、`page-inventory.json` 和 `page-fixtures.json` 的 SHA-256 分别为 `f4066e9466044c8a8924da4fc0a6338882ac090b82f8fcf96479c9e7953c0a7a`、`14fb2308d5affe1a0ebd364c6a554a20ec8d01c60d06ff475c7413009d897782`、`2f6d931024b30cce8e6d3d1113ebc335f282c2631c1536f0aa6e6748438652b9`、`6db3eb471da91415be43e63458a83cb5ec3fd95eb9e300c8078ff0ff10f1ad76`、`f4766c2de7de5cfd4a268349b716d99b32f6088fa8d2a1153bd5ec7753969ec4`。
- `ACTION-LEDGER.json` 声明值与逐项计算一致：118 个已登记操作，其中 33 个导航、74 个状态变更、7 个读取、1 个待决策、2 个未实现和 1 个禁止项；剩余 3 个未闭环项且清单仍未穷举全部控件。
- `git ls-files docs/prototypes/wp-0016/artifacts` 中 PNG、JPG、JPEG、WebP 数量为 0；本轮只登记机器可读报告，不向项目写入截图。

## 工程方法与影响范围

- 当前运行环境为 Codex；能力来自已安装的 `engineering-assistant` 插件。
- 使用 `engineering-assistant:wayfinder` 约束唯一前沿，使用 `engineering-assistant:prototype` 建立可丢弃交互，使用 `engineering-assistant:tdd` 先复现再实现，使用 `engineering-assistant:code-review` 做固定差异审查；视觉预检使用 `design-taste-frontend`，保持用户确认的 `7/5/7` 参数。
- 读取 Charter、STATE、WP-0016，并以 ADR-0032、ADR-0034、ADR-0072、ADR-0074、ADR-0075、ADR-0076 和 ADR-0077 约束结构版本、对象范围私有事实、租户所有权、事实源和截图边界。
- 代码图谱修改后影响分析显示 `renderStructuredObjectGovernance` 影响 4 个原型符号，`publishStructuredSchemaVersion` 与 `registerObjectStorageScope` 各影响 4 个原型符号。修改限定在原型状态、两个页面、交互、测试和控制面，没有触碰生产代码。
- 固定差异评审统一了“对象范围登记”的业务命名，并用专项 ADR-0032、ADR-0034 替换上位 ADR 引用，活动 ADR 保持 11 条，未突破 12 条上限。
- 未启动持久化 Runtime flow，未产生 `StageRunResult` 或 `QualitySnapshot`，阶段结果校验不适用。

## 下一切片

先解决渠道绑定、设备凭据与邮件邀请的产品决策边界，再继续构建相应高保真交互。人工确认前不修改生产前端，不进入 `WP-0017`。
