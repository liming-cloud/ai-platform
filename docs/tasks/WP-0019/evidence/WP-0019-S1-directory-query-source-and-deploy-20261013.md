# WP-0019：S1 目录查询源码与部署记录

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0019 / S1 平台连接与厂商预设 |
| 验证日期 | 2026-10-13；运行事实采集于 09:43:30 UTC |
| 本批输入 | 220cf68663564aef8a0d444419f3917d957a2848 |
| 合同接受提交 | 419b1456de7e99db615bf53f9bf0599d0c9ddd20；ADR-0096 |
| 后端验证及运行提交 | 7906451e3f2e04a03e098e601b37425e29857cb5 |
| 最终前端验证及运行提交 | 753aec9250821541fd87b3c9c89e689d773b8e2a |
| 原型固定版本 | d5deddaa1955814c5f08795b0b1ca58a7e6ac784 |
| 本批结论 | 查询源码及最新 Compose 部署通过声明的检查；新候选登录后生产浏览器与完整视觉验收未执行 |
| 完成边界 | S1、WP-0019、M6 均未完成，不进入 S2 |

## 接受依据与实现范围

用户于 2026-10-13 明确回复“接受提案，按此继续实现”，接受服务端有界字面搜索、全目录当前修订发布计数及原权限边界。唯一合同为 [ADR-0096](../../../architecture/decisions/ADR-0096-platform-connection-directory-query.md)，原提案只保留接受定位，不构成第二份正文。

两个现有列表增加可选 `q`：平台连接及精确连接当前修订的模型候选。Unicode 长度上限 64，空白规范化；只搜索公开名称／摘要，SQL 绑定并转义 `%`、`_` 和反斜杠，不匹配地址、业务空间或秘密引用。接口拒绝重复、未知和身份范围字段，原平台授权先于查询。候选继续限制当前连接修订、最近成功且未过期发现，以及明确的管理员分类，不从名称猜能力。

新增只读 `GET /api/v1/platform/model-supply/connections/summary`，仅返回 `publishedConnections`。计数来自全目录当前修订、VERIFIED 和独立发布记录，不受分页／搜索影响；旧 VERIFIED 不冒充发布或模型可用。两类分页均在 infrastructure 使用 MyBatis-Plus `Page`，数据与同范围总数在只读 REPEATABLE READ 快照内取得。只迁移触及的候选手工分页，不迁移租户供应或配置切片。

前端两页承接搜索、10／20／50／100、页码、跳转、总数与范围、刷新和返回位置。真实服务端页响应驱动列表，不取全量或仅过滤当前页；请求拥有者阻止旧查询／旧身份覆盖新状态。目录页头消费全目录摘要；详情页头消费精确对象状态，发现标题保留未筛选总数。越界恢复复用既有替换导航，避免无效页留在后退链中。分页与搜索复用 36／32／44px 尺寸变量，文字增长不被固定盒高截断。

构建准备另复现 `.dockerignore` 未排除环境文件：纯合成上下文的原规则为红，新增精确环境文件排除后同一断言转绿。没有发送真实环境文件、读取容器环境或清理缓存。没有修改 OpenDesign Cloud、业务数据结构、fixture、其他切片或 STATE。

## 真实红绿与最终验证

| 检查 | 结果与边界 |
| --- | --- |
| 路由与初始分页反例 | 初始路由 1 项失败，浏览器缺失完整分页失败；实现后保留刷新、后退和目录返回断言 |
| 数据库搜索反例 | 15 项中 2 失败、1 错误，捕获未过滤和摘要缺失；修复数据／计数同谓词后通过 |
| HTTP 反例 | 14 项中 4 失败，捕获丢弃 q、未知参数放行和摘要缺失；增加匿名／租户／重复／超长拒绝 |
| 查询与返回位置反例 | 2 失败、87 通过；客户端转发 q、路由恢复搜索和页大小后通过 |
| 越界历史反例 | 隔离浏览器第 7 项失败；强制处于历史末端后证明 push 追加无效记录，改用 replace 后两页通过。最初未清除 forward 干扰的反例不能作为红灯证据 |
| 移动端尺寸反例 | 正常字号实测 32px，原型要求 44px；新增精确搜索／分页断言在第 13 项失败，复用现有尺寸变量后通过，不放宽容差 |
| 完整后端 | `./mvnw -q verify` 退出 0；1132 项、失败／错误／跳过均为 0。最终前端提交相对后端提交没有 backend／deploy 差异 |
| 完整前端 | `make frontend-test` 退出 0；52 文件、277 项，类型、lint、构建通过 |
| 搜索分页浏览器 | `node scripts/quality/platform-connection-pagination.browser.cjs` 退出 0；29 项。包括 21／101 条、四页大小、无匹配、真实刷新、越界历史、Unicode、身份竞态、三尺寸、200% 文字及键盘，`productionAcceptance=false` |
| 原厂商表单浏览器 | 最终候选 `node scripts/quality/platform-connection-form.browser.cjs` 退出 0；32 项，`productionAcceptance=false` |
| 验收入口合同 | `make work-package-acceptance-test`：Python 29 项、Node 22 项及语法检查通过；不表示正式浏览器执行 |
| 架构 | `make architecture-guard` 退出 0；50 项及前后端门禁通过。后端依赖违规 0、SQL 文件 0、Java 内联 SQL 文件 14、手工列表分页 4 |
| 项目治理 | 源码提交及证据登记后执行 `make project-status`、`make decisions-sync`、`make project-verify`、`make resume-context`；81 项、30 个任务和 650 项历史定位保持有效 |

其他失败保留原日志：初次完整 Maven 暴露自动配置测试的 mock SqlSessionFactory 未装配新增候选 Mapper，补齐测试装配后完整复验通过；没有删除真实生产依赖。原厂商回归曾把页头和聚合区的同一状态误作重复，改为分别严格断言各 1 项。SQL 守卫拒绝 latest CTE 的 LIMIT 写法，改用语义等价的 DISTINCT ON；DTO 放入实际目录职责子包，未放宽阈值。仅登记五条必要多表查询，不扩大通用白名单。

## 实际部署与只读事实

从干净提交正常构建四应用，后端先更新，最终前端另按其精确提交更新。使用原 `deploy/.env` 与 Compose；只重建四个应用容器，未重启 Docker daemon、基础服务或删除镜像、卷、缓存及历史对象。七服务 healthy，`make deploy-smoke` 四入口通过。

| 应用 | 实际 OCI revision | 运行镜像 |
| --- | --- | --- |
| console-web | 753aec9250821541fd87b3c9c89e689d773b8e2a | sha256:78d17a4cb8bfde38fdcc7498d6069e36cff9e583674a6c8e81a19ae635df9e84 |
| control-plane | 7906451e3f2e04a03e098e601b37425e29857cb5 | sha256:2ca605aa2befc0db61be4fcc9a23341d7cc7dc8264639a23e47325fe2fb5917e |
| query-runtime | 7906451e3f2e04a03e098e601b37425e29857cb5 | sha256:a8d9c542b5f7245e1c3d13f9bf99df940dc7aa63aa1197b37f42ed04abaf9b42 |
| ingestion-worker | 7906451e3f2e04a03e098e601b37425e29857cb5 | sha256:1e8094693b20c93c49cde73853925b5f507cedfdf9f491832255b53b0c88bfc8 |

沿用精确连接 `2093675537095458817`／修订 `3` 的 REPEATABLE READ READ ONLY SQL，只查计数和非秘密状态。部署前后输出逐字节一致：仍为旧 VERIFIED，探测时间 `2026-08-29T13:55:09.957892+00:00`；推理用途槽 1，独立发现用途未配置，预设来源／能力为空，连接及当前修订发布数均为 0。未读秘密列、解密密钥或改写旧对象。首次猜测数据库角色被拒绝，随后使用容器既有数据库身份正常执行；该失败不是产品查询失败。

## 原型来源与未执行门禁

唯一权威仍为 OpenDesign Cloud 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 与上表固定接受版本。当前会话入口：[平台连接目录](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connections&state=success&review=1)、[连接详情](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connection-detail&state=success&review=1)。端口仅当前服务会话有效，长期定位使用项目／入口／固定版本。

本轮先通过项目工具取得原注册目录及原型；随后 MCP 连接返回 Transport closed，但原应用与两个 HTTP 服务仍运行，预览为 200。直接从原项目预览读取 20 个实际引用资产，与接受提交逐项摘要相等；没有生成替代副本或改原型。只用原型自己的合成身份观察两页及方舟弹窗三尺寸，共 6 组页面指标、9 张临时截图；人工查看桌面目录、390 详情和弹窗。这只能确认参考来源及原型观察，不能代替新生产候选的视觉结论。

新候选正式只读浏览器尚未执行。上轮有效 0600 平台凭据文件已经不存在；经既有授权，仅核对 `/private/tmp` 的 WP-0017～0019 目录中 295 个安全 JSON，无平台登录对象，进程也没有相应可用变量。剩余创建材料只有租户／成员，另一个文件仅为验收报告，不能当作平台凭据。已请用户指出其他受控来源，仅需路径；没有重新索要明文密码、重置账户、探查浏览器会话或导出数据库秘密。

本轮没有审批容量拒绝。当前输入缺失与 MCP 连接问题不记作产品测试失败，也不记作通过。上轮 10 项生产只读与 36 项标题对照只证明上轮运行版本，不能证明本轮新增搜索／分页／摘要。真实厂商成功合同、草稿／发现／独立发布生产闭环、七态和两页／弹窗完整视觉仍待完成；旧 DNS 保留网段和方舟发现用途缺口未在本轮再次外呼复现，继续按原证据处理，不能放宽 SSRF。

拿到有效受控凭据后，串行运行正式 `make acceptance-work-package WP=WP-0019 SLICE=platform-connection`，分别固定本表前后端 revision、精确连接及版本，显式指定新的临时输出；继而完成原型对照和事实复验。搜索、分页及摘要源码存在不构成 S1 五项操作完成，不进入 S2。

## 产物与工具追溯

安全产物位于当前用户独占的 0700 目录 `/private/tmp/wp0019-s1-pagination.Ynu1mA`。35 项选定安全产物的独占清单 `safe-artifact-manifest.json` 摘要为 `8e60cf7c8f7c36cef7a92e8e58d1cba1685635bd9f30d7381c41294963de142d`，排除凭据、秘密、会话和截图。临时文件可能被清理；仓库文字和固定源码承担长期恢复，不将临时文件存在当作验收通过。

| 安全产物 | SHA-256 |
| --- | --- |
| backend-full-result.json | 9195547f94435fe86e33161e784b8554f30e22765b8e7217eae7b55fd5e04aa8 |
| frontend-touch-final.log | 231b475103762dbca771628449067715e923f39227d22c534351efc57dd35be5 |
| mobile-controls-green.log | b5d071fda000fc376f96392b6b360d519750fa62d74f4aedb56fffe24be75fac |
| deploy-runtime-facts.json | 6445c8d975d7ccabb61b1b77fbaafbad3bf33b5ee1aa53aabecf8d66dbfc860f |
| connection-facts-after.log | 97a386d85f167f25f65a9f04f7d3c0a589dc8298971c6a0a6bb146ed8b683aae |
| prototype-http-source-binding.json | b5da51286c2e97f687574938990dedbf970ab8153aa67e67f5f66ddd155465b4 |

当前工程助手环境为 Local Codex，能力来自个人插件 `2.2.0-rc.1+codex.local-20260929-051321`。显式加载 wayfinder、tdd、code-review、相关 security-seams 和 wayfinder 的人工决策参考；用于目标恢复、接受依据、真实红绿和固定 diff 评审。未调用 Hermes、未复用旧 Runtime；内联执行无 StageRunResult，阶段校验不适用。主源码固定 diff 摘要 `12e5bc59e9dc364b75eeae808c65e0672742828b0d6f3050b88e172c3b3d26b6`，移动端局部 diff 摘要 `e15bf41b2452ec089a3eb7eea392d3d85666bedb7bc400b60f6acfc48f801503`。

代码图谱探索两页、ConsoleApp 的既有 navigate、路由／客户端、连接与候选控制器、读取服务、仓储、公开 lookup 和共享页码算法。服务影响分析涉及 72 个符号、仓储 221 个、lookup 3 个、路由 4 个、详情 4 个；目录读取聚合影响 139 个符号的补充分析发生在局部实施后，不冒充全部事前分析。图谱促成复用导航／页码、隔离目录 DTO、只迁移平台候选分页及保留租户合同；真实编译与运行结论来自上述检查。

STATE、M6 实现矩阵及工作包完成状态不提升。下一动作仍为 S1 的新部署正式浏览器、剩余原型对照及受控真实厂商／写模式闭环。
