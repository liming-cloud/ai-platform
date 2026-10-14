# WP-0019 S1：原项目连接恢复与手机文字重排候选

| 字段 | 值 |
| --- | --- |
| 责任任务与日期 | WP-0019 / S1，2026-10-14 |
| 本轮输入提交 | fa8cbffe12abc2f1125053af1f3ade308529b8fd |
| 修订候选提交 | 9008c62e7524f7bd2e96c2688c845388c6ef05d1 |
| 前一已接受原型 | 616713f84d3872f5880eac47e933a2b2235013ff |
| 唯一原项目 | OpenDesign Cloud `249f81f2-6f1a-46ff-988c-da3cdedb35e7` |
| 本证据提交时状态 | 候选已人工接受；生产承接及部署验证另行执行 |
| 接受与测试定位 | 用户接受 9008c62e7524f7bd2e96c2688c845388c6ef05d1；现行测试提交 068137a0e0750651d79654ef70d947ded42d3554 |
| 当前生产运行 | 前端 e671f4dcaa01609091e6811e4f69ba4d05b0e37b；三个后端 7906451e3f2e04a03e098e601b37425e29857cb5 |
| 完成边界 | S1、WP-0019、M6 未完成；不进入 S2 |

## 目标与有效输入

用户明确要求先恢复原项目连接、修复 390px / 200% 文字重叠并形成候选供人工确认，接受后才承接生产及共享色彩。已回顾 Charter、ROADMAP、STATE、WP-0019 和十二项所引 ADR，运行 `make project-status`；输入提交与产品基线为当前 HEAD 的祖先。开始时 main 工作区干净；没有用户 fixture 暂存差异。

本轮只修复 S1 两个原型入口的手机卡片高度，公共 API、领域、后端、认证、厂商调用、数据库及生产源码均未修改。截图与详细日志只放 `/private/tmp`；不重置数据或创建原型副本。

## 工作连接恢复

当前 Local Codex 的旧 OpenDesign Cloud MCP 连接仍返回 `Transport closed`；原服务的健康与精确项目 GET 均为 HTTP 200。一次原生窗口读取因受信 Node 内核退出而失败，不证明原项目或应用不可用。

只读检查现有配置中的官方入口、数据目录和 IPC：启动文件存在，现有 socket 正常，应用进程仍在。通过相同已配置的官方入口重新建立受控 MCP stdio 工作连接，initialize 协议 `2025-06-18`、tools/list（22 项）及 get_project 均成功，正常退出 0、stderr 0。配置、应用、安装包及安全设置没有改变，也没有伪造旧连接恢复。

官方 get_project 确认原项目注册目录为 `docs/prototypes/wp-0016`，预览入口仍来自该项目。get_artifact 的 17 个完整入口依赖无截断、无跳过，修订前逐一与注册目录及前一接受提交一致。后续修改使用该官方 MCP 的 write_file，仅写原项目 `prototype.css`，并经 get_artifact 和真实 HTTP 返回再次核对；没有绕过审批、改写其他项目或复制渲染站点。

## 真实红绿与修复边界

代码图谱先探索 `enlargeText` 和 `openForReview`，确认应沿用已有真实文字放大和可见原型登录流程，而不是改字号、注入身份或使用另一个模拟页面。图谱未定位模型 CJS 测试入口，随后仅定向读取该已知文件。CSS 不在语义图谱内，按已知原项目范围核对。

手机 `.data-table td` 已转换为 grid，却继续继承 `height: var(--table-row-height)`。文本实际高度增长时，固定高度仍占据原有流位置，内容进入下一行和分页。反例检查文本 Range 是否留在单元格内、scrollHeight 是否超出及是否进入分页区，保留既有 1px 几何容差，并断言标题文字实际翻倍。

- 首次完整反例为 15 项中 14 通过、1 失败，失败源于 390px 的单元格重叠。
- 在不改变断言的情况下同时采集两种字号：目录正常文字有 6 个单元格越界；200% 有 9 个越界并进入分页区。定向重跑同一失败行为保留红灯。
- 官方 MCP 原项目修复只新增七行 CSS：限定 `platform-model-connections`、`platform-model-connection-detail` 在既有手机媒体查询下使用 `height: auto`、`min-height: var(--table-row-height)`。48px 普通行基线仍保留，多行内容按实际高度排布；未改变列、字体、按钮、状态或权限。
- 同一浏览器反例和原有模型回归 15 项全部通过。两页在三尺寸 / 正常与 200% 字号的单元格越界和分页侵入均为 0；状态 / 厂商预设定向 14 项通过。

候选首次把三项反例加在历史浏览器脚本中，`project-verify` 正确拒绝冻结正文被改写。随后恢复该脚本的原 SHA-256 `56bc4a0976b46eab7b04f577806149dd6311299669d26907fcae712de2b72622`，将相同断言迁入 `scripts/quality/prototype-platform-connection-reflow.browser.cjs`；原十二项与现行三项合计十五项重新通过。历史索引和门禁未放宽，测试迁移没有改变候选的任何渲染依赖，不修改已接受的原型版本。

候选 CSS SHA-256 为 `aa616f0d0c1e411fe35c2444d9296e93a1ab5461e67a6ee78461e639c1171c2b`，原项目 MCP、规范目录和真实 HTTP 字节一致。修订前 CSS 摘要为 `6507586c041ee2f2019cb909114694ec205dbcc6c2db997180e9d86fffe5dfd6`。这些是非秘密源码摘要，不是凭据摘要。

## 原项目观察与人工评审入口

独立 Chrome 对两页七态 `loading / empty / error / denied / partial / disabled / success`，390×844、1024×900、1440×1000，正常与 200% 文字共 84 次观察通过；无文档横向溢出、单元格 / 分页重叠、页面异常或 reduced-motion 装饰动画。没有表格的状态只验证其实际可见布局，不补造记录。人工查看两页 390px / 200% 临时截图：目录内容与分页分离，详情候选完整显示并可继续滚动。

原项目[连接目录](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connections&state=success&review=1)、[连接详情](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connection-detail&state=success&review=1)。端口只是本轮实际入口，持久身份仍为项目 ID、原目录和完整候选提交。用户针对该精确候选回复“接受该候选，继续生产承接（推荐）”，已在[人工接受记录](WP-0019-S1-prototype-acceptance-20261012.md)登记；前一接受事实保留。

这些结果证明原型候选范围，不证明生产原型一致性。接受后仍须先补生产失败测试、局部承接高度及共享色彩，再分别执行最新 Compose、真实浏览器、只读事实和完整原型对照。

## 产物、工具与剩余输入

执行产物为 0700 目录 `/private/tmp/wp0019-s1-prototype-reflow.QdHdiwMh`，报告、日志与截图 0600。八项安全报告清单摘要 `93e8fabe99621aaa9b81df90813d919c3c0c1620c67b8efc6041630809e34b7b`；不包括凭据、Cookie、Token、秘密配置或截图。仓库不保存截图或一次性客户端脚本。

工程助手环境为 Local Codex，个人插件 `2.2.0-rc.1+codex.local-20260929-051321`；显式加载 `engineering-assistant:diagnosing-bugs`、`engineering-assistant:tdd`、`engineering-assistant:code-review`，固定输入至候选 diff 评审，遵守本包及 ADR-0074 / 0079 的原型人工门禁。未创建 Runtime 或 StageRunResult，阶段结果校验不适用。没有复用旧业务 Runtime。

接受前只读准备共享外壳：图谱探索 `ProductShell` 并执行一层影响分析（工具返回 2 个符号），确认它承载统一顶栏，应先据原型实际计算样式确认差异，保留角色文字与权限边界，再作样式修复；未提前改生产。

现有已授权验收凭据文件仍存在、当前用户拥有且为普通 0600 文件，不需重新提供平台密码。限定已授权 WP-0017～0019 临时范围只检查顶层 0600 JSON 的专用连接材料 Schema，没有发现可直接执行 create-draft 的材料；未读取或输出凭据内容，也未通过数据库导出既有密钥。新方舟独立认证仍须满足真实合同，不用旧 `/models` 成功替代。

下一动作：依据本次接受继续 S1 生产修复与剩余验收。现有发现、精确修订发布和登录回归事实见[上一轮证据](WP-0019-S1-auth-vendor-publish-and-parity-20261014.md)，不重复发布或扩大到模型部署、租户授权及 S2～S6。
