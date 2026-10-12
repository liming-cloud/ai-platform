# WP-0019：S1 正式只读复验、局部修复与部署对照

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0019 / S1 |
| 日期 | 2026-10-12；迁移、证据提交不成为新的验收日期 |
| 本批输入 | a9210efc5ce3cd6cbd6c306534909121257a2cdb |
| 验收入口提交 | dbe0d5d0f780bd867c8f2b1a2afdcd48c1d51195 |
| 最终验证源码 | 3efce6946c7af8f934a7bfaa9928c8b77df046a4 |
| 接受依据 | ADR-0095；[原型接受记录](WP-0019-S1-prototype-acceptance-20261012.md) |
| 已闭环范围 | 正式验收入口与安全合同、过期发布提示、标题排版、当前 Compose 只读复验及事实登记 |
| 完成边界 | 只读模式通过，标题局部一致；完整原型一致性未通过，S1、WP-0019、M6 均未完成 |

## 结果与安全边界

新增正式入口 `make acceptance-work-package WP=WP-0019 SLICE=platform-connection`。默认只读；`create-draft`、`vendor-discovery`、`publish-current` 分别显式选择，不能由提示、历史状态或其他模式推导写授权。创建须独立 0600 材料；发现和发布绑定精确 ID、版本及相应前提。输出仅在显式指定时写新的 `/private/tmp` 文件，父目录须为当前用户拥有的 0700 真实目录，独占创建 0600 文件，不覆盖凭据或历史证据。

入口启动前检查干净、已提交源码、本项目七个 Compose 服务及四应用实际 OCI revision；前后端版本分别提供。不读取容器环境、秘密数据库列或用户浏览器资料。真实会话只在独立 Chrome 内使用既有 0600 凭据，秘密只在内存中使用。请求拦截器只允许当前模式的精确动作；只读只允许认证及读取，不创建、发现、发布、删除或准备业务对象。报告仅保存非秘密白名单事实，始终保留 `sliceComplete=false`；工具的 `prototypeParity=NOT_RUN` 不代替本记录的人工对照结论。

生产只读对照发现旧 VERIFIED 仍被前端当作当前可发布。依据既有 ADR 与服务端发布不变量，前端拒绝缺失、无效、未来和满 24 小时的探测时间；页面停留跨过到期点也撤下发布动作。界面提示“连接探测已失效，请重新执行连接探测。”，没有修改后端授权、时效、发布事实或原型。服务端仍是最终发布判断者。

两页标题修正为原型的响应式字号、1.35 行高、500 字重及“模型治理”页头文案。此次只闭合这些精确项，不表示整页和弹窗已还原。

## 实际运行版本

先从干净的 `dbe0d5d0f780bd867c8f2b1a2afdcd48c1d51195` 构建并更新四应用，再从最终前端提交更新唯一前端容器。只使用现有 Dockerfile 和 Compose，按控制面、其他后端、前端顺序执行 `up --detach --no-deps --no-build --wait`；未重启 Docker daemon、基础设施或清理镜像、缓存及数据卷。最终检查七服务 healthy，`make deploy-smoke` 四入口通过。

| 应用 | 实际 OCI revision | 运行镜像摘要 |
| --- | --- | --- |
| console-web | 3efce6946c7af8f934a7bfaa9928c8b77df046a4 | sha256:96e67514368c016dbf9bfaef8acb17776cf099dd1d5d5fa2435c0b004d5ef4a7 |
| control-plane | dbe0d5d0f780bd867c8f2b1a2afdcd48c1d51195 | sha256:1258d5a890d75c607531caa77c920d70df6413f018f5e777e4fed63544389852 |
| query-runtime | dbe0d5d0f780bd867c8f2b1a2afdcd48c1d51195 | sha256:2f6534fa9cd25e7f2c1420e3610de742789ca0fd899bb4b3ee77e8664d886a94 |
| ingestion-worker | dbe0d5d0f780bd867c8f2b1a2afdcd48c1d51195 | sha256:eb4544437b9c7b0a8e56725eba1af954fe4166e398019977094d4c22663c13d2 |

控制面正常执行两项已接受迁移：连接独立发布表和可空预设来源。未给旧记录猜测回填厂商、能力或发布状态。`dbe0d5d0f` 至最终源码之间 `backend/`、`deploy/` 无差异；不能把后续前端或文档提交当作后端运行 revision。

## 红绿与最终验证

| 检查 | 结果与实际边界 |
| --- | --- |
| 分派反例 | 原入口不支持 WP-0019，29 项中 4 项失败；局部实现后 29 项通过，未开启整包或其他切片入口 |
| 模式及输出反例 | 旧读取配置不具备模式、版本与新输出约束，8 项中 7 项失败；补齐后通过，最终 Node 合同 22 项通过 |
| 登录路径反例 | 单数创建路径错误；改为正式 `POST /auth/browser-sessions`，未改变产品认证合同 |
| SPA 路由反例 | 实机先在目录失败；新增用例捕获把 API 集合路径误作页面。改为 `/platform/model-supply?page=1`，详情保留精确 ID 路由 |
| 模态作用域反例 | 实机 390px 把背景按钮被遮罩误判为不可操作；最小测试为红。改为只检查当前活动模态，未放宽字号、裁切或控件基线 |
| 注销时序反例 | 菜单点击后立即查匿名状态会抢在注销前；行为反例 22 项中 1 项失败。等待正式 DELETE 204 与平台登录入口后再断言 401，未修改共享登录辅助或产品注销逻辑 |
| 过期发布反例 | 14 项定向测试中 5 项失败；隔离浏览器在旧 VERIFIED 仍显示发布动作处失败。修复后覆盖满 24 小时、未来／缺失／无效时间、状态失败及页面停留到期 |
| 标题实机反例 | 原生产三尺寸都为 32px／40px；原型为 26.4／30.72／32px 及 35.64／41.472／43.2px。保留失败记录，修复后两页三尺寸共 36 项精确比对通过，不设放宽容差 |
| 完整前端 | `make frontend-test` 退出 0；52 文件、274 项，类型、lint 和构建通过 |
| 隔离浏览器 | `node scripts/quality/platform-connection-form.browser.cjs` 退出 0；32 项通过，明确 `productionAcceptance=false`，不访问生产、真实厂商或业务数据库 |
| 正式部署只读 | 最终 `deployed-read-only-6.json`：10 项通过、业务写入 0、拦截写入 0；源码与运行绑定见上表 |
| 架构与入口 | `make architecture-guard` 退出 0；50 项及前后端守卫通过，后端依赖违规 0，Java 内联 SQL 文件仍为 14。`make work-package-acceptance-test` 的 Python 29 项、Node 22 项及语法检查通过 |
| 治理 | `make project-status`、`make project-verify`、`make resume-context` 退出 0；81 项治理测试、30 个任务及 650 项历史资料有效。证据登记后再次同步、验证恢复入口 |

后端业务源码本批未改；完整 Maven 1124 项的来源仍是 [fc970f9e 源码证据](WP-0019-S1-connection-consistency-source-20261012.md)，本轮未声称重新执行完整 Maven。源码测试不替代上述实际部署复验，也不替代真实厂商合同。

其他失败同样保留：初次原型观察尚未登录原型自己的合成身份，修正观察步骤后取得两页和方舟弹窗三尺寸观察；没有使用生产秘密测试原型。新增文案中的内部术语被 SPA 信息边界拒绝，改为用户可理解的提示后完整前端通过。提前接管浏览器时钟干扰既有模态回归，改为仅到期场景接管后 32 项通过，未放宽断言。最终实机曾把观察与验收并行使用同一平台身份，触发 `SaTokenPlatformSessionGateway.issue` 的同设备不并发规则；保留第 5 轮失败，改为串行第 6 轮通过，未改变会话策略。

## 十项真实只读断言与数据库事实

最终正式入口覆盖：平台登录及范围恢复；五厂商、十一能力组合与匿名／租户拒绝；精确连接及当前修订发布读取；目录分页／越界总数和详情刷新；390、1024、1440 各自的两页／弹窗及 200% 文字；五厂商联动、取消清密、键盘与 reduced-motion；复验前后事实相同且无业务写入；正式退出后会话与受保护读取均为 401。三尺寸断言只证明可操作，不证明原型一致。

只核对连接 `2093675537095458817`、修订 `3`。部署后及最终复验后均以 `REPEATABLE READ READ ONLY` 执行精确 SQL，不查询秘密列：

- 状态仍为 VERIFIED，历史探测成功时间仍为 `2026-08-29T13:55:09.957892+00:00`，不能代表当前有效验证。
- 推理认证用途槽计数 1；没有独立发现 AK/SK。只计用途，不读取、解密或导出密钥。
- 预设 ID、版本和能力仍为空；未猜测来源。
- 独立发布表存在，此连接及当前修订发布计数均为 0。
- 最终查询输出与首次更新后的事实逐字节一致。浏览器和观察均未修改模型业务对象。

## 原型对照与剩余差异

唯一权威来源仍为 OpenDesign Cloud 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，已接受版本 `d5deddaa1955814c5f08795b0b1ca58a7e6ac784`；本轮未修改原型。项目工具取得原注册目录的 17 个产品资产，产品 HTML／JS／CSS 未为迁就生产而更改。当前服务会话入口：[连接目录](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connections&state=success&review=1)、[连接详情](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connection-detail&state=success&review=1)。端口只在当前原型服务会话有效，长期定位使用项目、入口和固定版本。

原型与生产分别观察两页及方舟弹窗的三尺寸。全部截图只在 `/private/tmp`，仓库没有新增截图。最终标题字号、行高、字体、500 字重、页头文案及 16px 正文基线共 36 项精确匹配；人工查看生产桌面详情、1024／390 目录及 390 弹窗，确认旧探测的失效提示和重新探测动作。

完整一致性结论仍为未通过，不能将下列差异自行列为例外：

| 待承接项 | 下一步验收要求 |
| --- | --- |
| 两页搜索当前目录 | 先固定所属 S1 只读过滤合同，服务端筛选与同范围总数；不能只过滤当前页或另取全量冒充分页 |
| 完整分页 | 每页 10／20／50／100、页码、跳转、总数与范围及 URL 恢复；用 21／101 条和越界反例验证 |
| 页头摘要 | 已发布连接数必须来自全目录真实当前发布事实，详情状态来自精确对象；不能把当前页计数或旧 VERIFIED 当作总数／发布 |
| 表格、状态及页面细节 | 状态标签、操作列对齐、标题程序焦点外观、间距和七态继续按原型核对；不把正文可见等同于像素和交互一致 |
| 弹窗及模型页签 | 需在同一厂商、尺寸、字体与滚动位置对照弹窗；页签按 S1／S2 边界承接，不添加伪造功能或自行宣告视觉例外 |

这些是 S1 待办，不因记录本证据而完成；平台部署功能和能力验证仍属于 S2，后续 S3～S6 同样未开始。

## 真实厂商阻断与复现

本轮未重复发送已知无法通过的厂商发现。控制面容器内对方舟公网主机只读解析得到 1 个地址，公网地址 0，`198.18.0.0/15` 基准测试地址 1，安全策略仍应拒绝。旧连接只有推理用途，发现协议仍为 OPENAI_MODELS_API、无预设来源；它不能证明新方舟控制面 ListEndpoints 的独立 AK/SK 合同。需先恢复安全公网解析，再通过正式凭据配置补齐匹配用途，不能放宽 SSRF、改全局代理／DNS、从数据库导出秘密或把推理 Key 当作 AK/SK。

当前不是审批容量不足。已执行正式浏览器与部署；未执行的草稿创建、真实发现、独立发布及完整七态／原型验收没有被登记为通过。此前用户允许复用既有连接做最小发现，不能扩张成发布该历史连接的授权。

复验必须串行使用同一验收身份。用当前有效的既有 0600 凭据文件，显式指定新的输出文件及 0700 父目录后执行：

```sh
NODE_PATH=/Users/lingou/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules \
FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE="$CREDENTIALS_FILE" \
FOUNDATION_ACCEPTANCE_FRONTEND_REVISION=3efce6946c7af8f934a7bfaa9928c8b77df046a4 \
FOUNDATION_ACCEPTANCE_BACKEND_REVISION=dbe0d5d0f780bd867c8f2b1a2afdcd48c1d51195 \
FOUNDATION_ACCEPTANCE_CONNECTION_ID=2093675537095458817 \
FOUNDATION_ACCEPTANCE_CONNECTION_VERSION=3 \
FOUNDATION_ACCEPTANCE_OUTPUT_FILE="$NEW_OUTPUT_FILE" \
make acceptance-work-package WP=WP-0019 SLICE=platform-connection
```

没有指定输出时默认只读不保存报告。已有登录凭据本轮再次正式使用成功，不要求人工重复提供；上述变量只代表本地安全路径，不在仓库复制秘密。

## 产物与工具追溯

本轮安全日志、SQL、构建输出和临时观察位于 0700 目录 `/private/tmp/wp0019-s1-entry.IFvGOw`，最终观察另用其 `production-final/`。40 项安全产物的独占清单 `safe-artifact-manifest-final.json` 摘要为 `7573e2713fb94c795b05e0cda8432847f9d4bcd73e3eb83a68805f3dd76d1434`，排除所有凭据、会话、秘密及截图。临时文件可能被系统清理；本仓库文字记录和固定源码负责长期恢复，不能把临时文件存在当作新的完成结论。

| 安全产物 | SHA-256 |
| --- | --- |
| deployed-read-only-6.json | 4d2f4b703d3188abe952af77e0e77d572fec52fcd1a495db251879478619c151 |
| typography-green.json | 4b4012b984205907266985954f5d421c76a6e12801fef073e3508fbf48a56656 |
| connection-facts-final.jsonl | 950cfb559753ad831d7373d543b11f17295aacd2ad3918a32555d39ed79c700b |
| dns-preflight.json | b6845f6cf6f04f65f85646f7f974b045d0a7387592bc4572fb8b6593ecfa2f60 |
| frontend-final-2.log | f837fb9f230ea45c0e7c13fcfae8345daabecd32ea060b1f344434f55780cc13 |
| platform-connection-browser-final-3.log | 7a6f676f667cf27738696b96f5f5067c26246bfb161ba370faa76f3c673bf6a1 |

当前插件环境是 Local Codex。显式加载当前工程助手 `wayfinder`、`tdd`、`code-review` 的 SKILL.md 及相关安全 seam 参考，能力来自当前已安装个人插件 `2.2.0-rc.1+codex.local-20260929-051321`；未调用 Hermes、未复用旧 Runtime。任务为内联执行，没有 StageRunResult，阶段结果校验不适用。依据技能完成目标恢复、真实红绿及固定 diff 评审；入口原始固定 diff 摘要为 `9fc8686be864d8ec7490746a2e9cf63efaffa20c62899c38a4c68dcaabaea9e8`，UI 固定 diff 为 `4c6539fdb6cc5a3b689774913e3cfdfd15f4b7b702fe8f0194b1cc41245794a6`。

代码图谱探索 `formatConsoleRoute`、`BrowserSessionClient`、`PlatformModelSupplyWorkspace`、`PlatformProviderConnectionDetail`、发布服务／领域门禁及 `SaTokenPlatformSessionGateway`。两页影响分析各涉及 4 个符号；共享凭据读取影响分析涉及 11 个符号，因此复用而未修改共享解析器。仓储影响分析涉及 111 个聚合符号，因此未借视觉核对批量重写后端。图谱确定 SPA／API 路径差异、24 小时独占边界与同设备会话替换，促成三项验收工具局部修复、前端时效修复及串行执行；编译与测试结论仍来自真实验证。

本批没有改变 STATE、M6 实现矩阵或工作包完成状态。下一前沿仍为 S1：先承接剩余查询与原型差异，再恢复真实厂商条件及分别授权的写模式验收；功能和完整原型分别闭环后才进入 S2。
