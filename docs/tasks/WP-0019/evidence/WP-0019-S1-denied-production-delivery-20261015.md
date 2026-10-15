# WP-0019 S1：拒绝态恢复生产承接

| 字段 | 值 |
| --- | --- |
| 结论 | 拒绝态恢复子任务闭环；S1、WP-0019、M6 未完成 |
| 输入 | 355cc853decd60b326970429942ae6bb3ae10f35 |
| 人工接受登记 | 8ea0533b18349b2d6373e14decac3247ee642d4c；[接受记录](WP-0019-S1-prototype-acceptance-20261012.md)登记用户明确接受 877a8136 |
| 唯一原型 | OpenDesign Cloud 原项目 249f81f2-6f1a-46ff-988c-da3cdedb35e7 |
| 接受版本 | 877a813652da2c96f960b71e29ed7a14eccb89c9 |
| 最终前端源码及实际 OCI revision | c9647a8b767e3080dc82c2e84908b37576956a60 |
| 实际前端镜像 | sha256:1800b723766aeceb04c22ecb1b00d88e778c30a69fcb1c3e7a4e383343287b16 |
| 三个后端实际 OCI revision | 7906451e3f2e04a03e098e601b37425e29857cb5；没有重建或部署后端 |
| 验证日期 | 2026-10-15；正式最终浏览器执行 14:36:31～14:36:57 UTC，随后执行独立原型对照及数据库复核 |

本报告只承接两页读取 HTTP 403 的拒绝展示和恢复，以及本路径实际发现的页头、按钮和手机顶栏差异。没有修改原型、权限、公共 API、持久化或其他切片。源码、前后端运行版本、原型接受与本次证据提交分别绑定，不使用文档 HEAD 代替运行版本。

## 实现与真实红绿

目录、详情及其必要的预设、摘要、候选和发布读取遇到原始 HTTP 403 时，清除对象投影，展示“当前会话不能访问此任务”，不确认对象是否存在，不展示旧正文、创建、探测或发布动作。“返回已授权入口”复用 `defaultConsoleRoute("platform")`，进入既有平台租户目录。

会话失效仍优先使用原登录边界；只有精确连接读取的既有资源 404 才进入详情空态，辅助读取 404 继续安全重试。没有将所有错误改为拒绝，也没有改变真正的租户／会员路由守卫。写入动作失败的原安全反馈保持原合同。

- 新增目录三类、详情四类 403 及 404／403 并发反例；原源码实际失败于缺少拒绝标题，随后同一反例转绿。保留精确 404、迟到响应、身份替换、取消、秘密清理和重试回归。
- 原型属性预检发现恢复按钮的字重、内边距、间隙和最小宽度四项差异；先固化失败，再局部对齐 620、6px／12px、8px 和内容宽度，不恢复 96px 固定下限。
- 人工查看部署截图发现页头缺少“访问受限”；增加反例后补齐读取状态摘要。进一步量测发现次要文字色及标签结构差异，先红后改，保留真实 200% 可见文字，不放宽字号或裁切阈值。
- 最终人工复核发现 390px／200% 顶栏账户菜单换行靠右。真实 `ProductShell` 回归先失败；只移除窄屏账户按钮的自动左边距，同一断言及原有 37 项转绿，最终部署增加对应几何对照。

完整回归曾检出隔离夹具取消／身份替换后的 Fluent 隐藏范围残留：同一挂起序列原夹具 16 次操作中失败 2 次，挂载生产实际使用的稳定 `ProductShell` 后 16 次均通过。图谱和已安装 Fluent／Tabster 源码确认，原夹具会在页面替换时销毁整份焦点管理器；修正仅在测试挂载，不据此重构生产共享组件。后续标题检查隔离虚拟时钟，等待实际计算字号收敛后仍执行原精确断言。

临时观察程序还曾因缺少原型地址参数、诊断分支语法和误写目标页标题而失败；日志保留，未记为产品通过。恢复目标的实际路径为 `/platform/tenants`，现有生产标题为“租户目录”；原项目恢复目的地为 `platform-tenants`。没有修改目标业务页来满足脚本假设。

## 源码与最新 Compose 验收

| 检查 | 实际结果 |
| --- | --- |
| `make frontend-test` | 52 文件、277 项通过，类型与构建通过 |
| 表单源码浏览器 | 51 项通过，含拒绝、空态、取消、身份及秘密反例 |
| 分页／真实外壳浏览器 | 37 项通过，含新增换行位置断言 |
| `make architecture-guard` | 50 项及前后端守卫通过 |
| `make work-package-acceptance-test` | Python 29 项、Node 22 项及语法检查通过 |
| 实际部署 | 只更新 console-web；七服务健康，四入口烟测通过 |
| 最终正式生产只读浏览器 | 十项通过，绑定最终前端 c9647a8b7、后端 7906451e；业务写入和被拦业务写入均为 0 |

正式入口仍为 `make acceptance-work-package WP=WP-0019 SLICE=platform-connection`，使用现有已授权 0600 登录来源及精确连接／修订参数，未要求人工再次提供密码。最终报告保留 `prototypeParity=NOT_RUN`、`sliceComplete=false`，原型结论由下一节独立证明。

实际后端镜像分别为：control-plane `sha256:2ca605aa2befc0db61be4fcc9a23341d7cc7dc8264639a23e47325fe2fb5917e`；query-runtime `sha256:a8d9c542b5f7245e1c3d13f9bf99df940dc7aa63aa1197b37f42ed04abaf9b42`；ingestion-worker `sha256:1e8094693b20c93c49cde73853925b5f507cedfdf9f491832255b53b0c88bfc8`。正式验收包含平台登录、匿名／租户范围拒绝、搜索分页／刷新、三尺寸及文字放大、五厂商表单、秘密取消、键盘、reduced-motion、退出后的受保护读取拒绝；没有厂商外呼、新草稿或重复发布。

## 已接受原型的一致性

原项目仍注册于 `docs/prototypes/wp-0016`。官方 MCP、原目录、接受提交 877a8136 与实际 HTTP 的 17 个渲染依赖逐字节一致，无截断、跳过或副本。冻结历史模型浏览器脚本 SHA-256 保持 `56bc4a0976b46eab7b04f577806149dd6311299669d26907fcae712de2b72622`；本轮没有修改任何原型文件。

[目录拒绝态](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connections&state=denied&review=1)、[详情拒绝态](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connection-detail&state=denied&review=1)。端口仅为本次原项目服务会话地址，持久身份仍以上述项目及固定接受版本为准。

最新部署两页 × 390／1024／1440 × 正常／200% 共 12 组：1,428 项样式、84 项页头／面板／账户位置几何量测及状态文案均匹配，差异 0；文字裁切、重叠、横向溢出及页面异常均为 0。另核对真实初始标题焦点，基准截图双方去焦；随后键盘 Tab 到恢复按钮、检查吸顶导航未遮挡、Enter 返回平台目录。生产十二次返回保持真实平台身份，原型十二次返回到 `platform-tenants`。截图只在临时目录，人工复核最终手机 200%、1024 常规及 1440 放大共三对截图。

这些拒绝页由浏览器响应边界精确注入 GET HTTP 403，三尺寸分别覆盖标准 JSON、畸形 JSON 和非 JSON 包络；使用实际部署资源、正式登录及真实恢复读取，不改产品状态、角色或权限。此为受控负态，不宣称服务器实际撤权或发生故障。正式真实权限反例由上节匿名／租户接口拒绝另行证明。未实现的后续导航目标仍由原责任包承接，不隐藏原型入口，也不宣称全外壳、完整七态或 S1 一致性已通过；观察报告保持 `fullPrototypeParity=NOT_ESTABLISHED`、`sliceComplete=false`。

## 只读数据库事实

验收前后均执行既有 `REPEATABLE READ READ ONLY` 精确 SQL，八行非秘密事实通过。除观测时间外全部相同：连接仍 2 条；精确连接 `2093675537095458817` 修订 4、原发布事实 1 条；合成草稿 `2110269575189757954` 仍修订 0、DRAFT、无探测／发现／发布；部署、目录及授权计数均为 0。没有读取秘密列、修改数据或清理历史对象。

旧发现仍保留 135 个历史候选，已于 `2026-10-15T03:55:24.971802Z` 到期；前后 `latestDiscoveryValid=false`。本轮不重发现或重发布，不把历史成功当成当前有效验证，更不以旧 OPENAI_MODELS_API 连接证明新方舟独立认证合同。

## 工具、证据和收口边界

环境为 Local Codex 个人工程助手 `2.2.0-rc.1+codex.local-20260929-051321`。显式加载 `wayfinder`、`tdd`、`diagnosing-bugs`、`code-review` 的 SKILL.md 及相关安全 seam／偶发缺陷参考，依据根 AGENTS、Charter、ROADMAP、WP-0019 和十二项引用 ADR 执行。没有创建 Runtime 或 StageRunResult；阶段结果校验不适用，不复用 WP-0017 Runtime。

代码图谱探索 `PlatformModelSupplyWorkspace`、`PlatformProviderConnectionDetail`、`PlatformConnectionReadState`、`defaultConsoleRoute`、`ProductShell`／`AccountMenu` 及原型 `adminShell`／`pageStatus`，完成两页、读取状态与外壳影响分析。结论使恢复复用现有平台路由、403 处理留在本功能内，测试保留真实外壳；CSS 共享消费不只依赖符号计数，补全前端与平台／租户顶栏回归。固定 diff 评审未发现未解决阻断；认证解析、秘密及持久化未改动，原边界回归继续有效。

日志、观察程序和截图位于新建 0700 目录 `/private/tmp/wp0019-s1-denied-production-yg9hb_r9`，文件 0600。22 项安全清单 `delivery-safe-evidence-digests.json` 的 SHA-256 为 `7a5ccb564ef231af3ba04a5e4098c9ae8eb9fbe6ae0dd9a75e9ab6e868fce197`，排除凭据、秘密材料和截图。不提交截图、fixture 或用户无关修改。本轮没有审批服务容量阻断。

`make decisions-sync`、`make project-status`、`make project-verify` 和 `make resume-context` 已实际通过；治理为 81 项／30 个任务，README 10275 字节、完整恢复输出 11457 字节，未提升 12 KiB 预算。STATE 仍为 M6／WP-0019，进行中任务仍为 WP-0014 与 WP-0019。文档提交与后续精确 GitHub Actions 记录分别验证，不以本报告预告远端 CI 成功。

本子任务功能、原型范围及只读事实闭环，当前产品前沿、STATE、M6 实现矩阵及整包完成字段不提升。仍需在 S1 内调查 partial／disabled 的合法真实触发与对照、未筛选真实空目录及原生输入值的文字放大范围，并完成匹配新预设的真实厂商合同。新方舟仍缺匹配真实推理 Key 与独立 AK/SK；当前旧连接未配置该认证，不能由旧 Key 推导或导出替代。继续复用有效平台登录来源，不再索取平台密码。不进入 S2～S6、WP-0020 或 M6 人工收口。
