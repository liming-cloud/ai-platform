# WP-0019 / S1：辅助读取合同接受与原型候选

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0019 / S1 |
| 日期 | 2026-10-16 |
| 调查基线 | f3b25b511e195b646fa53d9427f1731d6bc8a8ac |
| 验证提交／原型候选 | e7cd0ec22b3539033f374f4a8ca53c6b77d98ccc |
| 已接受原型 | 877a813652da2c96f960b71e29ed7a14eccb89c9；新候选尚待单独接受 |
| 人工决定 | 用户回复“接受该方向（推荐）”，接受有限辅助读取降级，登记 ADR-0097 |
| 结果范围 | 失败语义已接受；原项目候选与回归通过；既有部署只读通过；生产 partial 未实施 |

本记录不表示候选人工接受、生产新状态实现、S1、WP-0019 或 M6 完成。STATE 的里程碑、产品前沿和执行任务保持不变，不进入 S2～S6。

## 依据与原型候选

[ADR-0097](../../../architecture/decisions/ADR-0097-platform-connection-auxiliary-read-degradation.md)固定目录主读取成功、仅发布摘要网络／5xx 失败，以及详情主读取成功、仅候选／发布辅助网络／5xx 失败的降级边界。401／403／404、非法响应、修订不一致和主读取失败不得降级；迟到结果不能恢复旧身份事实，未知不能补零或沿用旧值，写操作恢复仍需新一轮完整读取。未改变后端 API、权限、持久化或厂商调用合同。

唯一项目为 OpenDesign Cloud `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，注册目录仍为 `docs/prototypes/wp-0016`。实施前官方读取、磁盘、固定已接受 Git 和实际 HTTP 的 17 个渲染依赖逐字节相同；候选提交后再次验证官方读取／磁盘／候选 Git／HTTP 全部相同。与已接受版本比较仅 `prototype.js` 改变，其余十六个依赖未变，没有创建副本或修改历史测试。

原型原有 partial 仍显示完整演示候选和“连接已发布”，不能准确承接辅助事实缺失。最小候选只调整两页：

- 目录 partial 明确“发布摘要暂未取得”，保留已知列表、查询与查看，禁用新增。
- 详情 partial 分别表现候选、独立发布或两者未取得；已成功辅助事实保留，未取得的候选不显示空表或零总数，发布未知不显示“连接已发布”。禁用探测／发布，保留返回及重新载入。
- 重新载入复用原导航事件，只在原型中恢复演示成功状态，不改连接或发布存储。详情 `auxiliary=candidates / publication / both` 只用于 partial 评审；其他状态忽略，禁止迁入生产路由。
- 原型演示快照将发布与验证合并在 PUBLISHED 中；发布未知时只展示演示连接已确认的验证事实，不改变快照。生产必须使用正式连接与独立发布响应，不能复制此演示推断。

当前预览：[目录 partial](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connections&state=partial&review=1)、[仅候选未取得](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connection-detail&state=partial&auxiliary=candidates&review=1)、[仅发布未取得](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connection-detail&state=partial&auxiliary=publication&review=1)、[两者未取得](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connection-detail&state=partial&auxiliary=both&review=1)。端口是当轮会话地址，后续从原项目重新取得；固定来源以项目和完整提交为准。

## 失败测试与验证

新增 `scripts/quality/prototype-platform-connection-partial.browser.cjs`，先在已接受版本实际运行目录、详情两项最小反例，分别因缺少“发布摘要暂未取得”和仍显示完整候选／已发布而失败。随后局部修复。新恢复按钮的初次事件绑定未生效，也被同一键盘恢复用例捕获并修正为已有导航合同。初版测试混淆了两类拒绝页标题，按已接受版本改为精确“当前会话不能访问此任务”，拒绝页实现未改动，没有放宽断言。

候选完整提交上正式执行：

| 检查 | 结果与限制 |
| --- | --- |
| 新增原型浏览器 | 15 项通过；三尺寸、缺失组合、只读搜索、新增／探测／发布禁用、键盘重载及不改变演示存储 |
| 七态及文字 | 两页 × 七态 × 三尺寸 × 正常／200% = 84 次观察；另有四类缺失组合的 24 次观察；1px 既有布局容差不变，reduced-motion 生效 |
| 既有原型浏览器 | 历史模型治理 12 项、当前重排／地址／拒绝恢复 9 项，与新增测试合计 36 项通过 |
| 原型状态／预设 | 14 项通过；历史模型浏览器文件未改动 |
| 架构门禁 | 50 项及前后端检查通过，后端依赖边界违规 0 |
| 控制面 | `make decisions-sync`、`make project-status`、`make project-verify`、`make resume-context` 通过；81 项、30 个任务，历史 650 项校验保持；恢复输出低于 12 KiB |

人工查看了目录 390px／200%、详情两项缺失 390px／200%、仅发布缺失 1440px 的临时截图；新增说明、未知事实与重载入口可读。本轮仅是候选质量确认，不代替用户接受，也不将原型模拟状态当生产故障或实际撤权。

## 现有部署的独立只读复验

没有修改 `frontend/`、`backend/`、`deploy/` 或 fixture，也没有构建或更新容器。正式只读入口在干净提交 `e7cd0ec22b3539033f374f4a8ca53c6b77d98ccc` 上，于 `2026-10-16T02:49:18.170Z`～`02:49:44.386Z` 执行；七服务均健康，四应用入口健康。

| 运行对象 | 实际 OCI revision | 镜像 |
| --- | --- | --- |
| console-web | 6296a13db72d60da8712eda1200a1d9f1c92c583 | sha256:f620de5b3b8a49c5081543973563b28d9912ecbf6203cde13d776e24c76e153b |
| control-plane | 7906451e3f2e04a03e098e601b37425e29857cb5 | sha256:2ca605aa2befc0db61be4fcc9a23341d7cc7dc8264639a23e47325fe2fb5917e |
| query-runtime | 7906451e3f2e04a03e098e601b37425e29857cb5 | sha256:a8d9c542b5f7245e1c3d13f9bf99df940dc7aa63aa1197b37f42ed04abaf9b42 |
| ingestion-worker | 7906451e3f2e04a03e098e601b37425e29857cb5 | sha256:1e8094693b20c93c49cde73853925b5f507cedfdf9f491832255b53b0c88bfc8 |

复用既有当前用户拥有的 0600 登录来源，执行 `make acceptance-work-package WP=WP-0019 SLICE=platform-connection` 默认只读模式：十项全部通过，业务写入及阻止的写入均为 0，`prototypeParity=NOT_RUN`、`sliceComplete=false`。实际覆盖平台登录、租户／匿名拒绝、分页与详情刷新、三尺寸／200%、五厂商联动、秘密取消清理、键盘、reduced-motion 和退出。它不证明新 partial 的生产实现或完整原型一致性；原生输入专项仍由[既有固定运行证据](WP-0019-S1-native-input-and-state-audit-20261016.md)证明。

精确连接 `2093675537095458817` 修订 4 的独立发布、135 个历史候选、两条连接及未探测草稿事实，使用既有 `REPEATABLE READ READ ONLY` SQL 前后核对。八行事实除观察时间外完全相同，部署／目录／授权均为 0；没有读取秘密字段或产生厂商调用。旧发现已到期，当前有效性为 false，不把历史发布当作当前可调用性。

## 证据、评审与下一动作

详细日志、工具与截图均在 `/private/tmp/wp0019-s1-partial-20261016-64eyxjx9`（目录 0700、文件 0600）。核心十六项摘要清单为 `evidence-digests.json`，SHA-256 `b16cf0b8fd7c604fd14cfc36fa8ccf2ad1bfc7b24ba1955441206bab81678304`；不包含凭据或截图。仓库只登记本文、候选、ADR 和可复用浏览器测试。

Local Codex 使用当前个人版工程助手插件限定 `wayfinder`、`tdd`、`code-review`，显式读取导航、HITL、红绿及安全 seam 规则，固定 base `f3b25b511e195b646fa53d9427f1731d6bc8a8ac` 与候选 `e7cd0ec22b3539033f374f4a8ca53c6b77d98ccc` 评审。代码图谱探索两项原型渲染器与两项生产工作区：原型影响分别为 4／2 个符号，生产入口分别为 4／4 个。由此限制修改在原渲染器，不改全局导航、后端合同或生产行为。未产生 Runtime／StageRunResult，阶段产物校验不适用；没有借用旧工作包 Runtime。

下一步等待原型候选单独人工接受，再针对并发安全拒绝、辅助故障和恢复补生产失败测试并实施、部署、验收；当前生产继续失败关闭。页面 `disabled` 合法触发、未筛选真实空目录、整体表单文案／布局和新方舟真实认证仍阻断 S1。旧连接无独立 AK/SK，旧协议发现已过期，不能由推理 Key 推导方舟认证；本轮不重新发现、不重复发布、不索取平台密码。不把本次设计与候选子任务闭环描述为 S1 或 M6 完成。
