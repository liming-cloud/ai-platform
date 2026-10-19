# WP-0019 S1：页面七态与方舟零候选调查

执行日期：2026-10-19（Asia/Shanghai）。本文件按执行阶段保留原时点结论，后续阶段不改写前面的失败或未执行事实。本文件文末“五项操作证据链复核”保存当时发现 S1-C01 的依据。后续[候选名称分离闭环](WP-0019-S1-candidate-name-delivery-20261019.md)已补合同修复、最新部署与候选区域验收；[S1 正式切片收口](WP-0019-S1-slice-closure-20261019.md)再补齐真实成功 CI，原账单阻断解除，S1 已完成。真实创建、非空发现、精确授权发布及真实空目录的原始事实和下面的原时点缺口均保留。本文原时点的 S1、WP-0019、M6 未完成结论保留；最新状态为 S1 完成、WP-0019 和 M6 未完成，本轮不进入 S2～S6。

## 固定输入与保护边界

起始 main `3f520216333576b9da3c13b89215d06f2c20b4b0`，工作区干净。Charter、ROADMAP、STATE、工作包和直接 ADR 已复核，`make project-status` 通过；输入 `8ade6bbe15a53fdf73a1fea794e642632f59b835` 为祖先。起始前端实际运行 `7f05664fa77fb6aef3e948279a5657ce6d3ec610`、镜像 `sha256:b15305c253aa09f3a34b8b03d2ef602d442e5000e91e2026288f509c9d3b37b2`；三后端仍为 `e0224fa9847fdaf297b3ad896e37ae7796e29a50`，七服务健康。零候选调查子任务没有部署或重启服务；页面修复子任务的独立部署见下文。

原型为 OpenDesign Cloud 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`、已接受 `cce686e4634ca3c523e4c8aeffd95752f1a04c3a`，MCP 已恢复连接。现有数据、原型与三个精确域名排除均保留；截图仅在 `/private/tmp`。`pwd.txt`、`AccessKey.txt` 为当前用户拥有、Git 忽略的普通 0600 文件，没有打印或计算凭据摘要。

## 方舟零候选调查：已执行

用户本轮授权继续排查零候选。先用代码图谱读取生产 `VolcengineArkPlatformProviderModelDiscoverer`：它要求 `Result.Items` 为数组且三个分页计数可读取，畸形结构返回失败，不会把任意 HTTP 200 当空列表。[官方 SDK 的响应模型](https://github.com/volcengine/volcengine-python-sdk/blob/master/volcenginesdkark/models/list_endpoints_response.py)声明同一组字段；[官方认证入口](https://docs.volcengine.com/docs/ark/base-url-and-authentication?lang=zh)提供相同区域、动作与签名协议。

外部一次性 Java 诊断复用生产 `VolcengineSignerV4` 与 `PublicHttpsEndpointPolicy`，保持官方 HTTPS、TLS、禁止重定向和十秒超时；只请求 `cn-beijing` 的 `ListEndpoints` 第一页、PageSize 100。AK／SK 与当前本地来源在内存中核对后以独占 0600 文件提供；不解析推理 Key，不读取数据库加密材料。只输出计数、结构布尔值和稳定状态，原始正文、授权头、Endpoint 标识与名称均不落盘。

| 事实 | 实际结果 |
| --- | --- |
| 出站与请求 | 生产策略 ALLOWED；HTTP 请求 1；200；厂商错误字段不存在；动作匹配 |
| 响应结构 | 与生产解析器要求匹配，PageNumber 1、PageSize 100 |
| 厂商返回计数 | **Items 0、TotalCount 0** |
| 结论 | 此凭据、此区域、无筛选条件下当前可见 Endpoint 列表为空；未发现生产解析丢弃候选，不推导其他区域／权限或公共模型广场没有模型 |
| 影响 | 推理 0、业务写入 0、发布 0；秘密数组清零；正式生产端到端 NOT_RUN |

因此前轮 `AUTHENTICATED_EMPTY` 与厂商真实计数一致，非空合同仍未通过。本轮没有切换到其他动作来填充候选、扫描区域、创建云端 Endpoint 或重复旧连接发现／修订 4 发布。

正式新建方舟连接仍要求推理 API Key 与独立 AK／SK，连接凭据由连接自身拥有（ADR-0060／0070）；公开请求不接受旧凭据引用。当前受检查的 `AccessKey.txt` 只有独立发现材料，`deploy/.env` 没有匹配的方舟／模型推理 Key 字段。不得通过解密旧连接、复制引用、提交假 Key 或修改表单绕过。下一步真实非空／生产流程仍受匹配材料与可见 Endpoint 事实约束，无需重新索取平台登录密码。

证据目录 `/private/tmp/wp0019-s1-page-states-20261019-ienw2udl` 为 0700。`ark-metadata.json`（0600）的 SHA-256 为 `8324001d47e5d57b2892d28094ccf2715552a9b5ee9dec15a7ff44953c922558`；安全辅助源码为 `ArkListMetadata.java`，编译日志、输入存在性及当前运行事实分别保存。凭据文件及其摘要不进入证据清单或仓库。

## 页面与完整表单：源码和部署闭环

产品修复提交 `aca410b2b62de0eb6221fb606a1846ef43bca397`、最终 `569eb54dc66fb8cbd97ebf70076824cbf77068b2`，仅调整两页布局、局部样式及原型共用外壳的平板基线。修复正常／空态标题与状态间距、错误恢复按钮、表格文字继承、紧凑操作、详情标题及说明排版；保留路由标题焦点，以原型强调环替换浏览器蓝框，恢复平板侧栏账户分隔区。未改变业务接口、状态机、权限、数据或原型。

初始部署、源码最小反例及所有中间失败保留。最终源码状态／正文／焦点 **57 项**、表单 **60 项断言**、分页 **43 项断言**通过；前端 53 文件／310 项、类型、lint、构建通过。代码图谱读取两页、读取投影及 `ProductShell`，分别执行一层影响分析，据此将共享修改限制在外壳样式；没有借机重构或扩大到后续切片。

最终前端 OCI revision 为 **`569eb54dc66fb8cbd97ebf70076824cbf77068b2`**，镜像 **`sha256:e81664078edb16bd243a2bbcd6b679d54c82b38f404bf7ccc66325bcadab2eec`**。只从固定 Git 对象构建并更新 console-web；构建输入不含本地凭据或环境文件，临时构建源码已移除。七服务健康、四入口 smoke 通过，其他六容器 ID／镜像保持不变；三后端仍为 `e0224fa9847fdaf297b3ad896e37ae7796e29a50`，各自镜像摘要在独立部署 JSON 中逐项核对。控制面 HEAD 不能替代前后端实际版本。

OpenDesign Cloud MCP 再次确认原项目及注册资源目录；主仓库当前注册资源树与已接受 cce686e4 的 123 个受控文件无差异。该目录由主仓库历史管理，不是独立 Git 工作树；没有把主仓库 HEAD 当作原型版本。当前会话入口：[连接目录](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connections&state=success&review=1)、[连接详情](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connection-detail&state=success&review=1)；端口仅为当前服务会话地址。

## 最新部署七态与全景范围

Playwright 1.62.1／独立 Chrome，两个页面 × 七态 × 390／1024／1440 × 正常／实际 200%，共 **84 组**。生产没有评审模拟 URL；受控 GET 响应与自然生产事实分别标记。没有删除现有两条连接制造空目录。

| 页面状态 | 组数 | 触发与结论 |
| --- | --- | --- |
| success | 12 | 真实正式 GET，身份与业务数据保持真实 |
| loading | 12 | 仅挂起主读取，解除后恢复；非生产自然慢请求结论 |
| empty | 12 | 目录六组为隔离零行响应；详情六组为精确不存在 ID 的正式 404 |
| error | 12 | 主读取受控 503，不伪造自然服务故障 |
| denied | 12 | 主读取受控 403；真实平台／租户／匿名权限另由正式入口验证 |
| partial | 12 | 主读取真实成功，辅助摘要或候选／发布读取受控 503 |
| disabled | 12 | 只在浏览器预设 GET 投影 false；当前部署默认仍为 true，不宣称本轮真实暂停 |

覆盖已交付外壳、页头、状态面板、搜索、表格／手机卡片、详情事实、发现区和动作区的字体、色彩、边框、间距及固定布局几何；并检查 200% 文字、单一 h1、reduced-motion、横向溢出及文字裁切。业务写入、拦截到的业务写入、页面异常、溢出及裁切均为 **0**。

初轮部署原始矩阵 FAILED／236 项差异保留。最终原始矩阵仍保留 **FAILED／12 项结构差异**：实际旧候选均已到期，详情是单个跨两列空行，原型示例是两列数据行。十二项严格限定于详情 success／disabled、三尺寸和两倍率；实际列数 2、首行单元格 1、colspan 2 均已记录。独立分类 `S1_SCOPED_PARITY_PASSED_WITH_REAL_EMPTY_CANDIDATE_ROWS`，未解释视觉差异 **0**；所有原比较保留，没有插入原型示例或新增视觉例外。

本结论是 S1 已交付内容的样式、布局和状态验收，**不是整张截图像素相同**。真实身份、名称、数量、到期候选与原型模拟数据分别处理；“平台部署”页签和 M8 运维导航由其责任切片交付，沿用用户已接受的“未实现功能到实现阶段验证”边界，本轮不将这些入口标为已实现或已验收。真实未筛选空目录仍为 NOT_RUN，已有数据保留。

两页各六项只读恢复，共 **12 项**通过：加载解除、错误／partial 重试、拒绝态返回默认授权目录、详情 404 返回、目录空态打开并取消空白表单、暂停投影解除后完整重读。业务写入和页面异常均为 0。首次外部观察误将导航名当作目标页标题而超时；保留失败，按代码图谱确认的 `/platform/tenants` 及精确默认筛选参数复验，不修改产品迁就观察器。

## 最新部署完整表单与正式合同

十一厂商／能力组合 × 三尺寸 × 两倍率，**66 组／23,310 项比较**；840 次键盘转换、66 次必填拒绝观察，裁切／溢出问题、页面异常和业务写入均为 0。原始 FAILED／167 项差异保留；72 项严格匹配 ADR-0098 三处既有接受文案，95 项几何变化与前轮已证明的精确换行归因集合完全一致。独立分类 `PASSED_WITH_ACCEPTED_TEXT_DIFFERENCES`，未解释差异 **0**，没有扩大接受范围。

正式十项只读入口最终 **10／10 通过**：平台登录、五厂商预设、租户／匿名拒绝、精确对象、分页刷新、三尺寸／200%、表单联动、取消清密、键盘／reduced-motion、前后连接事实不变和注销后拒绝；业务写入 0。入口自身的 `prototypeParity=NOT_RUN`、`sliceComplete=false` 保留，不能用功能合同冒充独立原型验收。

首次正式入口在 1024px 因验收器遗漏表格紧凑分类失败，失败报告保留。已接受原型表格操作为桌面 32px，验收器原本只将分页识别为紧凑控件。先增加七项反例，六通过、一失败，再提交 `f507ee10f8cdb1fabd579c490b9494213bf69e0c`，仅补齐表格分类：31px 表格、32px 普通操作、43px 手机控件和被遮挡控件仍拒绝；普通 36px／手机 44px、字号、裁切及遮挡断言不变。最终验收入口 Python 29／Node 50 项通过。此提交只修改验收器和测试，产品树与 569eb54d 相同，无需重建镜像；最终正式浏览器绑定入口 f507ee10、前端 569eb54d、三后端 e022，分别记录。

人工查看桌面错误页、手机 200% 目录／partial 详情及方舟表单双端截图；焦点、字号、响应式及说明归因与矩阵一致。截图全部只在 `/private/tmp`，不进入仓库。

## 收口、剩余阻断与恢复

本轮两个子任务完成的是零候选原因调查、S1 页面／表单声明范围的部署验收及验收器修复；**S1 整片仍未完成**。剩余为：

1. 当前 AK／SK 在 cn-beijing 的可见 Endpoint 为 0，非空真实合同没有通过；不以公共模型广场、旧协议或其他区域结果替代。
2. 正式新建方舟连接尚缺匹配推理 Key 来源；旧连接不能通过公开接口补独立发现配置，当前新建 → 独立发现 → 单独发布的新协议生产端到端仍 NOT_RUN。既有修订 4 发布和旧草稿保存仅证明原时点范围；135 个旧候选到期事实不改写。材料与可见 Endpoint 具备后按既有 S1 合同继续；新发布仍需范围匹配的独立授权。
3. 真实未筛选空目录仍未执行，六组隔离目录空态不冒充自然生产空库。
4. 独立确认 [79611bd6 的 CI](https://github.com/liming-cloud/ai-platform/actions/runs/39201242656)为 failure，作业因账户付款／spending limit 未启动；不是产品测试失败。最终推送后单独核对对应提交，不将 CI 标绿或绕过流水线。

产品前沿和当前任务仍为 WP-0019，STATE、M6 实现矩阵及里程碑完成事实不提升，S2～S6、WP-0020 不启动。现有数据、默认写入可用性与安全边界保留；不重复旧发现／修订 4 发布，不创建云端资源，不修改数据库、代理、TLS、SSRF 或 OpenDesign Cloud 原型。

证据根目录为 `/private/tmp/wp0019-s1-page-states-20261019-ienw2udl`（0700）；`safe-evidence-digests.json` 显式绑定 25 项非秘密产物，SHA-256 `9180d280cf9017ddfbe2247f280a0e4952f089f0a3e86c1c314cee25bbc0c0c6`，受保护值扫描 0 命中；凭据、秘密摘要、截图和 trace 均排除。`final-deployment/`、`final-panorama/`、`final-full-form/`、分类 JSON、正式前后报告及恢复报告分别保存，不覆盖失败证据；本轮分类脚本字节码缓存已移除，可重建浏览器缓存由既有 finally 清理，证据保留。

Local Codex 使用当前工程助手安装提供的 `wayfinder`、`tdd`、`code-review`，显式加载各 SKILL.md 和 `references/security-seams.md`；固定 diff 评审无新增阻断，无 Runtime／StageRunResult，阶段结果校验不适用。CJS 正式布局函数未被图谱命中，才定向读取两个已知脚本；TS 图谱定位两页、读取投影、`ProductShell`、默认／格式化路由及方舟解析器，并分别执行页面／外壳影响分析；据此限定局部样式和验收分类，不改后端边界或后续切片。

收口命令 `make decisions-sync`、`make project-status`、`make project-verify`（81 项／30 个任务）、`make architecture-guard`（50 项及前后端守卫）、`make resume-context` 均实际通过，输出见同证据根目录 `closeout-*.log`；恢复入口 11,736 字节，低于 12 KiB。后端源码未变，本轮没有重跑 Maven 或将旧后端测试说成最新执行。最终提交推送与 CI 准确状态另以 GitHub 回执核对，外部未启动不算通过。

## 后续执行

2026-10-19 13:48–14:04（Asia/Taipei）的业务操作绑定调查基线 `a9b2cf811da324b15c5bb41fb077491b926b8661`，局部修复提交为 `5f489fcffe106170ff21dd12751e91f45ec153c7`，已正常推送。以下补充更新剩余阻断；前文原时点的调用、部署与验收数字保留，**不再把材料缺失或“整个账号没有模型”作为当前结论**。

### 正式方舟新建已通过

复用本地 `pwd.txt`、`AccessKey.txt`，并沿[真实模型说明](../../../../deploy/compose/README.real-model.md)中的精确历史路径找到已有推理 Key 文件：当前用户所有、普通 0600，内存格式检查通过。该 Key 的实际推理有效性未验证；本轮没有推理调用，不以草稿保存证明模型可用。未解密旧连接、导出密文或使用公开凭据引用，凭据内容及摘要均不记录。

以新 0700 临时目录下的 0600 创建材料，执行正式入口 `make acceptance-work-package WP=WP-0019 SLICE=platform-connection`，显式 `FOUNDATION_ACCEPTANCE_MODE=create-draft`：**PASSED，四项检查，恰好一笔业务写入**。创建时前端为 `569eb54dc66fb8cbd97ebf70076824cbf77068b2`，三后端仍为 `e0224fa9847fdaf297b3ad896e37ae7796e29a50`，不将它写成后续修复版本的创建验收。新连接 `2112058351855599618` 为 `VOLCENGINE_ARK_CONTROL_PLANE`、CHAT、DRAFT 修订 0、未发布；详情读取和退出拒绝通过，没有探测或发布该草稿。

`REPEATABLE READ READ ONLY` 独立核验：连接由 2 增至 3；凭据行由 2 增至 5，新对象恰好具备 `INFERENCE_API_KEY`、`DISCOVERY_ACCESS_KEY_ID`、`DISCOVERY_SECRET_ACCESS_KEY` 三用途。该时点新对象发现／发布均为 0，两个旧对象未变；只核对用途、计数与状态，不读取秘密字段。

### 零候选的两个不同问题

1. **请求范围需要限定。** [官方 ListEndpoints 参数](https://api.volcengine.com/api-explorer/?action=ListEndpoints&groupName=%E7%AE%A1%E7%90%86%E6%8E%A8%E7%90%86%E6%8E%A5%E5%85%A5%E7%82%B9%EF%BC%88Endpoint%EF%BC%89&serviceCode=ark&version=2024-01-01)明确省略 `ProjectName` 时默认 `default`。前文只发送页码／页大小，因此原 HTTP 200、Items／TotalCount 为 0 的结论限于当前凭据、北京区域及默认项目，未验证其他项目。官方另区分[预置 Model ID 推理与自定义接入点](https://docs.volcengine.com/docs/ark/deployment-overview?lang=zh&redirect=1)；不能由接入点空结果推断公共模型或已开通模型均为空。用户回复账号内事实“尚未核对”，本轮没有跨项目／区域扫描或更换动作填充候选。
2. **生产失败事实丢失是源码缺陷。** 新对象第一次正式 `vendor-discovery` 返回非 200（原入口未记录精确状态码），修订仍为 0、发现记录 0。代码图谱定位适配器将合法空列表返回 `succeeded=true`，而 `PlatformProviderModelDiscovery.capture` 要求成功记录非空，触发领域拒绝，发现未能保存。同期只读 DNS 核验宿主和容器各 12 个地址均公网，没有 fake-IP；七秒窗口日志无可用异常，未靠猜测重试。

先增加本地 HTTP 零列表反例：四项测试中一项实际失败，`expected false but was true`。仅在方舟适配器的成功出口增加空集合失败分支，使用稳定安全码 `VOLCENGINE_ARK_DISCOVERY_NO_MODELS`；保留领域非空约束、正式验收非空断言、签名、TLS、SSRF、分页和用途隔离。同一四项转绿，随后 `./mvnw -q test` 实际退出 0：348 套件／1,133 项，失败、错误、跳过均 0。未改公共字段、Schema、原型或后续切片，无需新增产品决策。

### 新部署和一次针对性复验

只从固定 Git 提交创建构建上下文，不带本地秘密；三个后端镜像均绑定修复提交的 OCI revision：

| 服务 | 运行镜像 |
| --- | --- |
| control-plane | `sha256:6a83d7e5025993780320a2913b150e9360d8a1b0e9a4e56c389f81bb1be4703b` |
| query-runtime | `sha256:f5dcfc4ffd23235bae1530916aa8e852fd41587f23c717a9ec044b5e3820a3e3` |
| ingestion-worker | `sha256:46ec6414b837e7799a6c0db1a46406ecff3013a8ca62045040151e3dd63ceb68` |

正常 Compose 仅更新这三个服务，七服务健康、四入口冒烟通过；前端、PostgreSQL、Redis、MinIO 四个容器标识未变。前端继续运行前文 `569eb54d…`／`sha256:e81664078edb16bd243a2bbcd6b679d54c82b38f404bf7ccc66325bcadab2eec`；前端与原型源码相对调查基线无差异，**未重跑已通过的页面矩阵、完整表单或原型对照**。OpenDesign Cloud 已接受 `cce686e4…` 和原证据范围继续有效，本次不新增视觉通过结论。

新运行版本仅对本轮精确连接执行一次 `vendor-discovery`，仍用 `pwd.txt`，不读取创建材料。正式入口确认 HTTP 200、`succeeded=false`，连接更新至修订 1、DRAFT、未发布。**原始验收仍 FAILED／退出 2**，失败原因明确为未通过真实发现或没有候选，非空门槛没有放宽。修复前后部署输入不同，没有对同一失败条件自动重试，也未重复旧连接发现／修订 4 发布。

独立只读事务确认新失败事实 `2112062317435813889`：修订 1、`succeeded=false`、`VOLCENGINE_ARK_DISCOVERY_NO_MODELS`、无有效期、模型数 0。累计发现由 11 增至 12，累计历史候选仍 785、发布仍 1；部署、目录、授权均 0，旧对象不变。因此**零候选失败保存回归 PASSED，真实非空厂商合同仍 FAILED，新连接发布未执行**；不把负态通过当作完整供应链通过。

### 当前剩余事项与证据

- 人工在[方舟北京控制台](https://console.volcengine.com/ark/region:cn-beijing)确认该 AK／SK 所属账号的 `default` 自定义接入点和读取可见性；若位于其他项目，给出项目名称，若只使用预置 Model ID，确认该事实即可。取得事实后再决定 S1 内的项目／候选来源提案，不能先增加参数、改协议或创建云端资源制造通过。无需重新提供登录密码、AK／SK 或已找到的推理 Key。
- [修复提交的 CI](https://github.com/liming-cloud/ai-platform/actions/runs/39206942173)作业步骤数 0，注解明确为付款／spending limit，须由所有者处理 Billing & plans；另一个 Ubuntu 标签迁移注解不作为产品失败依据。本地测试与外部未执行分别结论，不绕过或反复重跑。
- 真实未筛选空目录保留未执行；现有三条连接保留。当前修订没有成功验证，发布不执行；后续新发布仍需范围匹配的独立授权。S1、WP-0019、M6 未完成，不进入 S2～S6／WP-0020。

本次证据根目录 `/private/tmp/wp0019-s1-blockers-20261019-p5i22853` 为 0700，日志和报告为 0600。`safe-evidence-digests.json` 绑定 32 项非秘密产物，SHA-256 `66fa75c4043be9bdce212181c750b4da2876ec3cc55859a2cac335a89665bc93`；受保护值扫描 0 命中，凭据文件、秘密摘要、截图均排除。正式创建、修复前后发现、只读事实、红绿日志、完整后端、构建和部署分别保留；可重建构建上下文已在 finally 清除。

Local Codex 显式使用当前安装的工程助手 `wayfinder`、`diagnosing-bugs`、`tdd`、`code-review`，加载各 SKILL.md、HITL 和安全 seam 参考。图谱探索 `PlatformProviderConnectionService`、`PlatformProviderModelDiscoveryService`、`ExtensionContractMapper`、`PlatformProviderModelDiscovery.capture` 与方舟发现器，并对后者执行影响分析；据此只修改扩展的空结果分类，不改领域不变量或上下文边界。固定 diff 为调查基线至修复提交，评审未发现新增阻断。内联执行无 Runtime／StageRunResult，阶段结果校验不适用；不复用历史 Runtime 宣称通过。

后续收口实际执行 `make decisions-sync`、`make project-status`、`make project-verify`、`make architecture-guard`、`make resume-context`，均退出 0。治理 81 项／30 个任务、架构 50 项及前后端守卫通过，恢复输出 11,758 字节；日志位于本次根目录 `closeout-*.log`。重新核对 Charter、ROADMAP、STATE 与 S1 边界，产品前沿／执行任务仍为 WP-0019，STATE 和 M6 实现矩阵未提升。文档集中补充现有证据及恢复入口，不为本次排查新增任务或报告文件；最终证据提交与推送回执单独核对。

## default 项目与候选来源核查

2026-10-19 14:34（Asia/Taipei）只读调用，调查基线 `d1ed234e5c235ef850ef3488addebb107cca95c2`。本子任务响应“优先核查 default 接入点与权限、明确零候选最小方案”，没有重跑页面验收、执行生产发现或创建云端资源。前文“尚未核对”保留其原时点含义，当前可见资源范围由以下新增事实承接。

### 新事实与可排除的假设

复用 `AccessKey.txt`，普通 0600、当前用户所有、Git 忽略；内存确认既有受保护解析文件与当前源匹配，没有复制或记录秘密／秘密摘要。临时诊断复用生产 `VolcengineSignerV4` 与 `PublicHttpsEndpointPolicy`，只允许以下三个已查官方文档的 Action，每次第一页、每页一项、10 秒超时、响应至多 1 MiB、无重定向及自动重试；原始响应仅在内存处理，不写入报告。

| 只读 Action | 精确范围 | HTTP 与结果 | 证明边界 |
| --- | --- | --- | --- |
| `ListEndpoints` | cn-beijing，显式 `ProjectName=default`，无筛选 | 200；Items 0、TotalCount 0 | 当前凭据本轮可见的自定义接入点为空，不证明所有身份／项目为空 |
| `InnerDescribeModelEndpoints` | 同区域、显式 default，无筛选 | 200；Items 1、TotalCount 2；抽查行属于 default、Running，具备 ModelId 和基础模型引用 | 确认有现存预置来源；未读取第二项，不宣称两项均运行或可调用 |
| `ListModelActivations` | 精确过滤上述第一项的基础模型、Available、排除已下线；不读取价格／免费用量 | 200；Items 1、TotalCount 1；名称匹配且 State=Available | 仅证明该 AK／SK 视角下该模型已开通，不证明独立推理 Key 的权限或真实能力 |

三个响应均无厂商错误、Action 与分页结构匹配。这个结果反驳“方舟账号完全没有候选来源”和“先创建自定义接入点才能继续”的假设；当前生产零候选的直接原因已收敛为**只读取自定义接入点，未承接现存预置来源**。HTTP 200 只证明本次这三个读取被受理，不证明 IAM 全局权限、所有资源可见或推理授权。

依据：[预置列表](https://docs.volcengine.com/docs/ark/inner-describe-model-endpoints-api?lang=zh)、[开通列表](https://docs.volcengine.com/docs/ark/list-model-activations-api?lang=en)及[预置／自定义调用区别](https://docs.volcengine.com/docs/ark/online-inference-standard?lang=en)。公共模型目录不能代替当前账号资源；管理面预置 `Id` 与推理 `ModelId` 也不能混用。最小方案及备选比较已并入[既有 S1 设计](../design-and-slices.md#s1-方舟-default-预置候选补齐提案待人工接受)，等待接受，不更换 Action 填充本轮正式验收结果。

### 身份／权限核查的实际限制与人工步骤

宿主 DNS 本轮只读核验：Ark 域名 12 个地址均公网；`sts.volcengineapi.com` 和 `iam.volcengineapi.com` 各两个非公网地址，均含 fake-IP。按[官方 GetCallerIdentity](https://docs.volcengine.com/docs/IAM/GetCallerIdentity-Getrequestoridentityinformation?lang=zh)本可用 GET／sts／2018-01-01 识别 Account、User、Role；但当前不符合公网地址策略，**身份及 IAM HTTP 请求均未发出，调用身份与完整策略尚未核验**。未绕过 DNS、SSRF、TLS 或修改 Clash；这是本机网络限制，不是厂商 401／403 或缺权证据，也不阻止已取得的三项 Ark 事实。

需要完整人工核对时，以下步骤只查看，不提交权限或资源变更：

1. 打开[方舟北京控制台](https://console.volcengine.com/ark/region:cn-beijing)，确认登录的是 `AccessKey.txt` 所属账号，项目切至 default。进入“在线推理”核对自定义接入点；再进入“模型列表”或“开通管理”核对现有预置模型及开通状态。不要点“创建并接入”“开通”或发布；无需提供密钥、账号 ID 或截图。
2. 如需查明 IAM 可见性，由有查看权限的账号进入[火山引擎控制台](https://console.volcengine.com/)右上角头像 → 访问控制 → 用户管理／用户。若 AK 属于 IAM 用户，打开其详情检查直接策略、所属用户组及 default 项目限制；若为角色，检查对应角色及会话策略；若为主账号，核对账号归属即可，不必寻找不存在的子用户。只需反馈身份类别、是否覆盖 default 及拒绝条件；不要复制身份标识或秘密。
3. [官方 IAM 指南](https://docs.volcengine.com/docs/ark/use-iam-to-manage-permissions?lang=en)区分控制面与推理 API Key 权限，并给出全局基础读取与项目只读策略组合。只检查 `ark:ListEndpoints` 等相关读取的有效授权；目前三个请求均成功，**没有证据要求扩权，不建议授予 ArkFullAccess／AdministratorAccess 或立即变更策略**。若后续出现明确拒绝，再按精确 Action／default 范围处理，不泛化为全账号写权限。

本轮必须的产品决定只有是否接受预置来源最小提案；完整 IAM 策略查看是尚未验证的独立事项，不以它替代已经查明的来源缺口，也不再次索取凭据。后续若要修改仓库外 DNS 配置或发布新连接，仍须各自的范围授权。

### 零写入复核与后续门槛

`REPEATABLE READ READ ONLY` 对比上轮精确三对象与聚合，全部未变：连接 3、凭据行 5、发现 12、历史候选 785、发布 1、平台部署／目录／授权 0；新连接 `2112058351855599618` 仍修订 1、DRAFT、`VOLCENGINE_ARK_DISCOVERY_NO_MODELS`、未发布。未查询秘密列或解密；不以三个直接厂商读取冒充正式发现、连接验证或发布。

只读 Compose 检查七服务均 healthy，前端仍 `569eb54dc66fb8cbd97ebf70076824cbf77068b2`／`sha256:e81664078edb16bd243a2bbcd6b679d54c82b38f404bf7ccc66325bcadab2eec`；三后端仍 `5f489fcffe106170ff21dd12751e91f45ec153c7`，镜像分别与前节相同，没有构建或部署。前端、后端及原型源码未改；已接受 OpenDesign Cloud `cce686e4634ca3c523e4c8aeffd95752f1a04c3a` 和已有页面证据未改变，本轮不新增视觉通过结论。

证据目录 `/private/tmp/wp0019-s1-default-permissions-20261019-wo6g4ut6` 为 0700，八项非秘密产物为 0600，包含四例分页解析自检、三次真实只读响应摘要、DNS、只读数据库和运行元数据；不含原始响应、厂商资源名称／ID、身份 TRN 或秘密。`safe-evidence-digests.json` SHA-256 为 `d03cbc3dc2deca6eab110ecba000f4a76ee56496c900ebb41893f7871cca6e0d`，真实读取摘要为 `574519c0e3a54738b84596935e7a93a3f76b373b0c9b8850bbe0f17435ba4759`。临时 Java 类缓存收口时删除，保留诊断源码和证据。

Local Codex 显式加载当前工程助手 `wayfinder`、`diagnosing-bugs`、`code-review`，使用 HITL 参考；代码图谱探索生产签名器、方舟 `parsePage` 和候选确认工厂，确认现有解析只取自定义 Id、分类保持待确认，因而将方案限定在扩展，不新增模块或权限框架。本轮无源码修改，未执行非局部源码影响分析、未重跑 Maven／浏览器，不冒称源码修复已完成；接受后实施前执行影响分析。无 Runtime／StageRunResult，阶段校验不适用。

本子任务交付资源来源事实、权限核查边界、可审阅最小方案及人工查看步骤。S1 正式非空厂商合同、新修订发布和真实未筛选空目录仍未闭合；外部 CI 账单阻断未解除。S1、WP-0019、M6 未完成，不进入后续切片。

本轮 `make decisions-sync`、`make project-status`、`make project-verify`、`make architecture-guard`、`make resume-context` 实际退出均为 0；治理 81 项／30 个任务、架构 50 项及双端守卫通过，恢复输出 11,939 字节。日志为本证据目录 `closeout-*.log`，STATE 与实现矩阵未改。固定差异评审限于基线至本轮三份文档，核对事实抽样、待接受状态和权限边界后无新增阻断；提交、推送与 CI 结果以最终 GitHub 回执另核对。

## 双来源发现生产承接

用户随后回复“同意并推进任务”，接受 `ff5e7c1a5ceb239ab4cada1c1104e258bb0c2161` 的最小提案，先在 `556d6cbc17b368a6dd2f2a274001c4a2a013252e` 登记 [ADR-0099](../../../architecture/decisions/ADR-0099-ark-default-preset-candidate-discovery.md)并正常推送。该节接受前摘要仍可由原提交恢复；没有把接受写成生产验收。当前任务直接 ADR 保持 12 条，ADR-0022 经 ADR-0082 继续生效；STATE、M6 矩阵及前沿未提升。

### 红绿、实现与源码闭环

只修改方舟发现器及其测试，源码为 `41c9fa89cb5f3caef146f4b64ff9665c513dca9e`。先增加本地 HTTP 的“自定义 0、预置非空”反例：原五项中一项实际失败，`expected true but was false`，并非环境／导入错误。实现后同一类定向 36 项转绿；再补三项非运行及分页回归，最终完整后端实际执行退出 0：**348 套件／1,168 项，失败、错误、跳过均 0，其中方舟适配器 39 项**。原签名固定向量和错误脱敏保留，未降低正式非空断言。

两 Action 均显式 default、分别完整分页后合并，预置只用 Running 的官方 ModelId，自定义仍用 Id；候选统一待管理员确认，不推断能力。两个来源共享 100 次请求、10,000 候选、1 MiB 响应边界，逐次验证出站。任一来源 401／403／429／5xx、字段／项目错误、分页漂移／缺失／超限均使整次失败，不保存部分成功集合；独立 AK／SK 正常及失败均释放，不解析推理 Key。本次同时收紧触及路径的分页完整性检查，没有扩大到公共协议、Schema 或其他厂商。

图谱探索发现器与测试，对唯一装配入口 `volcengineArkPlatformProviderModelDiscoverer` 完成影响查询，命中 7 个装配符号；类名查询存在重载歧义，不冒称完整跨模块图。固定 diff 评审基线为决策提交，实际只含上述两个文件；受影响模型服务、事务、HTTP 和三启动模块由完整后端覆盖，架构 50 项及双端守卫通过，公开 facade／port 与 POM 未变。源码已正常提交推送；其 CI [39217772132](https://github.com/liming-cloud/ai-platform/actions/runs/39217772132)仍因付款／spending limit 未启动，作业步骤数 0，不是源码测试失败。

### 固定镜像与实际部署

只从固定 Git 提交取 `.mvn`、Maven 入口、backend 和既有 Dockerfile 构建，不带 `pwd.txt`、`AccessKey.txt` 或 deploy/.env。三镜像 OCI revision 均为上述源码：

| 服务 | 实际运行镜像 |
| --- | --- |
| control-plane | `sha256:8ddb2dc0c6b0b56f31f6c31a4d0ac8a5fb6482953c7bf3b594dffb2ad00c189d` |
| query-runtime | `sha256:bfdd3e7db343c4bd8ca898156ab8633f3945ec651e05340775eb2d7ecef60e7c` |
| ingestion-worker | `sha256:301e72d6f9dd7d0b6a30749ed55c938dfc1e7916348a8171ce15446fc76bca30` |

完整验证通过后，正常 Compose 仅更新这三个服务，七服务 healthy、四入口冒烟通过。前端、PostgreSQL、Redis、MinIO 容器标识不变，部署前后数据库聚合及精确三对象事实相同；不重启 Docker、清卷、重置数据或修改网络。固定 Git 上下文已由 finally 清除（Docker 报告传输 6.59 MB）；日志与证据保留。

前端仍运行 `569eb54dc66fb8cbd97ebf70076824cbf77068b2`／`sha256:e81664078edb16bd243a2bbcd6b679d54c82b38f404bf7ccc66325bcadab2eec`。相对调查基线，frontend 与原型源码差异均为空，前端容器未换；OpenDesign Cloud 原项目及已接受 `cce686e4634ca3c523e4c8aeffd95752f1a04c3a` 不变。本轮没有重跑已通过页面矩阵，没有生成截图，也不新增视觉通过结论；既有原型一致性证据保留各自原日期与范围。

### 最新部署的正式发现与独立事实

2026-10-19 15:53:53–15:54:05（Asia/Taipei），复用普通 0600 `pwd.txt`，按正式 `make acceptance-work-package WP=WP-0019 SLICE=platform-connection`、`FOUNDATION_ACCEPTANCE_MODE=vendor-discovery` 执行一次。输入精确连接 `2112058351855599618`／修订 1、前端与后端分别固定上述版本。**PASSED／退出 0，六项真实浏览器检查，一笔模型业务写入**：平台登录、平台预设及匿名／租户拒绝、当前配置与发布读取、动作前提、唯一真实发现及新修订读取、退出后受保护读取拒绝均通过。没有只读模式暗写、重新创建连接、重跑页面矩阵、自动重试、推理或发布。

`REPEATABLE READ READ ONLY` 独立确认当前连接修订 2、VERIFIED；发现 `2112089882397507585` 绑定同修订，`succeeded=true`、`VOLCENGINE_ARK_DISCOVERY_OK`，有效期至 `2026-10-20T07:54:03.352863Z`。两条候选均 `REQUIRES_ADMIN_CONFIRMATION`、模型类型未设置、能力集合为空，管理面 ep-m 编码计数为 0；这只证明真实发现成功，不能证明推理 Key 可用或 Chat／Embedding 能力。

发现从 12 增至 13、历史候选从 785 增至 787；连接 3、凭据行 5、发布 1、平台部署／目录／租户授权 0 均未变。两个旧对象及新连接修订 1 的失败历史保留，没有读取秘密列、解密旧凭据或输出实际模型编码。当前新修订未发布，已提出精确修订 2 的独立授权请求；ADR-0099 及发现授权不包含发布，不自动推进 S2。

### 证据与当前边界

本轮目录 `/private/tmp/wp0019-s1-preset-discovery-20261019-zezd2q2g` 为 0700，日志及报告为 0600。`safe-evidence-digests.json` 绑定 19 项非秘密证据，SHA-256 `a2ae8d007ab17753408af16e9805ba1e94e35bf0b2679e8859bdd8567675d741`；不含凭据及其摘要或截图。红灯、36 项定向绿灯、39 项最终适配器及 1,168 项后端、构建、部署、正式浏览器、只读事实、未变 UI 与外部 CI 各自独立保留，不互相替代。

Local Codex 显式使用当前安装工程助手 `wayfinder`、`tdd`、`code-review`，加载安全 seam 参考并执行固定 diff 内联评审；图谱结论将修复限定在扩展及其现有装配。发布用例／仓储／领域图谱另核对修订、连接状态和时效门禁，仅调查，没有修改或执行发布。无 Runtime／StageRunResult，阶段校验不适用。

方舟零候选和匹配新协议的正式非空合同已闭环。用户随后明确回复“允许仅发布该连接修订（推荐）”，单独授权连接 `2112058351855599618` 的修订 2；截至本段登记尚未执行发布，不包含模型部署、目录、租户授权或推理。剩余为该精确发布验收、真实未筛选空目录及外部 CI 账单阻断；完整 IAM 策略审计仍因假地址未执行，但两个必需 Action 已真实成功，不再把猜测缺权当作发现阻断。S1 五操作整体、WP-0019、M6 尚未完成，不进入 S2～S6，不删除现有数据制造空目录。

### 精确修订发布闭环

发现执行证据先以 `184be7e49bd86b06f0ae0dcc99dcd077e17be513` 提交推送，工作区干净后继续使用上述独立授权。2026-10-19 16:03:57–16:04:10（UTC+8），复用同一受保护账号来源，在相同前端／后端运行版本上执行 `FOUNDATION_ACCEPTANCE_MODE=publish-current`，精确输入连接 `2112058351855599618`／修订 2，正式详情页发布 **PASSED／退出 0，六项浏览器检查、一笔模型业务写入**。没有再次发现、创建连接、推理或创建云端资源。

正式页面重读为修订 2、VERIFIED、`published=true`。独立 `REPEATABLE READ READ ONLY` 事务确认新增发布 `2112092416361103361`：发布序号 1，绑定连接修订 2，记录发布人，探测时间与当前修订一致，发布时间 `2026-10-19T08:04:07.518043Z`。发布序号与连接修订分别记录，未将其混为同一版本。

发布总数由 1 增至 2；连接 3、凭据行 5、发现 13、历史候选 787 不变。两条旧连接、当前发现及两个待确认候选均未变化，平台部署／目录／租户授权仍为 0；没有读取秘密列。发布后七服务 healthy、三后端镜像／OCI 仍为源码 `41c9fa89cb5f3caef146f4b64ff9665c513dca9e`，前端仍为 `569eb54dc66fb8cbd97ebf70076824cbf77068b2`。

同一临时目录新增 `publish-current.json`／日志、只读 SQL／事实、差量核验及运行版本证据；`publication-evidence-digests.json` 绑定 12 项非秘密产物及前一清单，SHA-256 `98e7824050f99881f73ab64b302cb3d69b02763eb1b546943d813689ce04cf5b`，受保护值命中 0。目录 0700、文件 0600，未覆盖旧产物，没有凭据及其摘要或截图。

本次发布报告明确 `prototypeParity=NOT_RUN`、`sliceComplete=false`：前端及已接受原型未变，不重复已通过页面矩阵，也不把这次操作通过写成新视觉验收。新方舟连接“真实创建 → 失败事实保留 → 双来源非空发现 → 精确授权发布”已闭环；真实未筛选空目录、外部 CI 未启动以及 S1 五操作整体仍未闭合。下一前沿保持 S1，不提升 WP-0019、M6 或实现矩阵完成事实，不进入候选能力分类及后续切片。

收口实际执行 `make decisions-sync`、`make project-status`、`make project-verify`、`make architecture-guard`、`make resume-context` 均退出 0；治理 81 项／30 个任务、架构 50 项及双端守卫通过，恢复输出 11,828 字节。日志为本临时目录 `final-*.log`。固定评审基线为发现证据提交，仅更新本证据与任务恢复入口，核对独立授权、真实发布及部署版本分离后无新增阻断；外部 CI 与最终 Git 同步回执在推送后单独核对，不将本地通过冒称云端 CI 通过。

## 真实未筛选空目录独立验收

2026-10-19 17:01:10–17:01:17（Asia/Taipei），正式 `SLICE=platform-connection`、`FOUNDATION_ACCEPTANCE_MODE=empty-directory` **PASSED，五项真实浏览器检查、模型业务写入及被阻断写入均为 0**。平台登录、五厂商预设及匿名拒绝、未筛选空目录／首次新增、取消后焦点恢复及刷新、退出后拒绝均通过。没有使用搜索空结果、越界页、`state/review` 参数或响应桩，没有提交表单、探测或发布。

### 隔离、版本与独立事实

本轮基线 `c18c5783a4d7db4fbb9d64e1ce7c70f46eef5f34`；验收源码 `3a5418ada8105bbc9f1785c17a2370358b0220cf` 已正常提交推送，仅增补正式入口、对应测试及使用说明。根据 ADR-0079 的隔离数据库种子边界，在新项目 `wp0019-s1-empty-20261019-dbd8d2` 使用与原部署逐一相同的七个镜像、独立网络及全新卷；不重新构建，不导入业务数据。四应用 OCI 分别固定后端 `41c9fa89cb5f3caef146f4b64ff9665c513dca9e`、前端 `569eb54dc66fb8cbd97ebf70076824cbf77068b2`，七服务健康，实际前端发布端口核对为 `http://127.0.0.1:56631`。该临时入口验收后已关闭，人工继续使用原部署入口。

本地 profile 在新空库按既有迁移初始化平台身份，复用普通 0600 `pwd.txt` 完成正式登录；没有读取或重置原账号，没有复制密码或摘要。新库没有租户身份，因此匿名拒绝在本轮验证，租户隔离沿用原部署证据，没有伪造身份或开通租户。

正式未筛选列表 `pageNumber=1&pageSize=20` 的 `totalElements=0`、`items=[]`，独立发布摘要为 0；页面为“平台模型连接目前为空”，“新增供应商连接”可操作，打开后取消并刷新仍为空。独立 `REPEATABLE READ READ ONLY` 前后确认：新库身份 1、租户及成员 0，连接／凭据／发现／候选／发布／部署／目录／授权全部 0。原部署前后容器标识、镜像、健康及聚合事实相同：连接 3、凭据 5、发现 13、历史候选 787、发布 2、部署／目录／授权 0。

每次执行仅回收本轮新建的隔离容器、网络和卷，最终残留均为 0；原部署及历史数据保留。前端与原型相对本轮基线无修改，原 OpenDesign Cloud 项目及已接受 `cce686e4634ca3c523e4c8aeffd95752f1a04c3a` 不变；未重复已通过页面矩阵、未生成截图，报告继续明确 `prototypeParity=NOT_RUN`、`sliceComplete=false`，不新增视觉或整片通过结论。

### 反例、失败记录与证据

先新增最小入口反例，旧实现实际因不支持 `empty-directory` 失败，再实现后同一入口 33 项通过；`make work-package-acceptance-test` 54 项及 Node／Shell 语法通过。反例覆盖原部署／外部目标拒绝、实际端口绑定、无连接 ID／创建材料、禁止模型写入、非空／越界／未知摘要拒绝；普通模式的原目标及租户隔离保持不变。源码阶段 `make project-verify` 81 项／30 任务、`make architecture-guard` 50 项及双端守卫通过；本轮没有改 Java／前端，不重跑其既有全量测试。

三次准备失败原样保留：首轮临时主密钥版本误用小写，服务端按既有大写格式拒绝；第二轮停在浏览器启动前端口核对，最小 Redis 复现证明本机 `internal` 网络没有实际宿主端口；第三轮临时执行器错误假设 Compose 会保留默认 `internal=false` 字段，创建资源前退出。修正均限于临时执行配置／读取，最终使用与既有 Compose 相同的普通桥接语义，保持独立存储、127.0.0.1 端口和原 SSRF；没有放宽版本、健康、空目录或写入断言。失败轮次及最小复现资源均回收，原部署事实未变。

产物集中在 `/private/tmp/wp0019-s1-real-empty-20261019-s2_h52sa`，成功记录位于 `attempt-4/`；目录 0700、文件 0600。`evidence-digests.json` 绑定 68 项／213,510 字节的非秘密证据，SHA-256 `b958bb2a9d3d1b657f762a0ba0dbb5c22741059b0e7c6fcae92da0653d303794`，包含各轮失败、正式浏览器、只读事实、原部署未变及回收记录，不含凭据、密码摘要或截图。受保护值命中 0；未留下新增构建上下文或存储卷。

Local Codex 显式加载当前工程助手 `wayfinder`、`diagnosing-bugs`、`tdd`、`code-review` 与安全 seam 参考，依据本包控制面及 ADR-0079／0095～0099 内联推进。图谱定位 `PlatformConnectionEmptyState` 文案、正式入口调用链，并对 `platformConnectionConfiguration`、`inspectRuntime` 执行影响分析，分别命中 2、3 个符号；据此只增补验收入口，不改产品 UI、登录工具或业务契约。临时配置错误由 `EnvironmentSecretEncryptionKeyProvider.VERSION` 明确拒绝。无 Runtime／StageRunResult，阶段校验不适用。

### CI 账单阻断与人工处理步骤

验收源码的 [Actions 39226062504](https://github.com/liming-cloud/ai-platform/actions/runs/39226062504) 返回付款失败或 spending limit 消息，步骤数 **0**，任务没有启动；未因该消息重跑或修改 CI。此通用消息不能区分账号的具体付款／预算原因，本轮没有读取私人账单或改变付款设置。

1. 使用仓库所有者账号进入[个人账单](https://github.com/settings/billing)：Settings → Billing & Licensing → Payment information，查看失败付款或授权验证；存在问题时由本人更新付款信息或处理欠款。依据[官方解锁步骤](https://docs.github.com/en/billing/how-tos/troubleshooting/locked-account)。
2. 在 Budgets and alerts 检查覆盖此仓库的 Actions 产品／SKU 及账户／仓库预算是否耗尽并停止用量。预算耗尽时由本人决定等待新周期或设置可接受的明确额度；不自动解除上限。依据[官方预算说明](https://docs.github.com/en/billing/how-tos/set-up-budgets)。
3. 若付款信息、余额和预算均无异常仍被阻断，向 [GitHub Support](https://support.github.com/) 提供失败运行链接及原消息，请求检查 billing lock；无需在聊天中提供密码、Token 或付款资料。官方说明指出授权验证失败也可能阻断 Actions，即使账单页没有可处理的余额。
4. 问题解除后进入 ai-platform → Actions → 最终提交对应运行 → Re-run all jobs，确认作业实际启动并通过。依据[官方重跑说明](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs)。本地测试与本次真实空目录通过不替代该 CI 结果。

本轮 `make decisions-sync`、`make project-status`、`make project-verify`、`make architecture-guard`、`make resume-context` 均退出 0，治理 81 项／30 任务、架构 50 项及双端守卫通过，恢复输出 11,891 字节；日志为本临时目录 `closeout-*.log`。真实未筛选空目录子任务已闭环；S1 组合收口仍等待实际 CI 门禁，不提升 WP-0019、M6 或实现矩阵完成状态，不进入 S2。

## 五项操作证据链复核

本次只读审计基线为 `main@5c67c49223ad186a288bf7530e508e07ec9da8bd`，起始工作区干净、与 `origin/main` 相同；工作包输入提交为祖先。复核 Charter、ROADMAP、STATE、工作包及十二项直接 ADR，前沿仍为 WP-0019／S1。没有重新运行浏览器、调用厂商、登录、查询秘密、部署或改动业务数据。

### 交付清单与证据边界

下表按原执行范围组合证据，操作实施状态仍只在 [M6 实现矩阵](../../milestones/M6/implementation-map.json)维护。本次将五项由过时的 `pending-contract` 更正为已有的 `implemented`，补充已接受合同及验证引用；两页汇总为 `in-progress`，没有登记 `verifiedCommit` 或提升完成状态。

| 操作稳定 ID | 已有生产／源码证据 | 独立事实与剩余限制 |
| --- | --- | --- |
| `platform-model-connections:0` 新增供应商连接 | 正式 `create-draft` 4 项通过，创建 `2112058351855599618`；完整表单 66 组及取消清密由独立对照覆盖 | 原后端为 `e0224fa9847fdaf297b3ad896e37ae7796e29a50`；只读事实确认草稿及三用途凭据计数，不复写创建日期或冒称当时运行 41c9fa89 |
| `platform-model-connections:1` 查看连接 | 正式 `read-only` 10 项覆盖目录、详情刷新、分页、平台／租户／匿名边界及退出拒绝；两页七态 84 组独立对照 | 目录到精确详情的导航接线与深链接分别由 `PlatformModelSupplyWorkspace` 和既有浏览器回归覆盖；候选区的名称／编码合同仍有下述缺口 |
| `platform-model-connections:2` 厂商／能力／地址联动 | 正式只读联动、五厂商／十一组合完整表单 66 组、23,310 项比较、840 次键盘和 66 次必填拒绝 | ADR-0098 三处文案映射及其换行影响单独归因；选择能力不证明真实能力；不重复提交其他厂商表单 |
| `platform-model-connection-detail:0` 执行连接探测 | 正式 `vendor-discovery` 6 项通过；当前后端 41c9fa89 的真实双来源发现使精确连接进入修订 2／VERIFIED | 独立只读事实确认发现 `2112089882397507585` 与两个待确认候选；正式模式经浏览器同源接口执行，UI 按钮接线由源码／既有回归补充，不冒称该模式点击了按钮；候选可见名称合同未闭合 |
| `platform-model-connection-detail:1` 发布连接 | 用户精确独立授权后，正式详情 `publish-current` 6 项通过，发布 `2112092416361103361` | 发布序号 1 绑定连接修订 2；独立只读确认发布总数增加 1，其余连接／发现／候选不变，部署／目录／租户授权仍为 0；不重复发布 |

真实未筛选空目录是独立补充门禁，不是第六项操作：相同七镜像、独立新空库，正式 `empty-directory` 5 项通过，前后八类模型计数为 0，原部署不变且临时资源已回收。五份正式报告逐项核对状态、检查数、业务写入数、运行版本，并保留各自 `prototypeParity=NOT_RUN`、`sliceComplete=false`；不将模式通过合并成整片通过。

### 原始证据、源码与部署绑定

本轮新目录 `/private/tmp/wp0019-s1-evidence-review-20261019-667dg0vk` 为 0700。逐一核验前文五份安全清单的原 SHA-256，以及清单内 **156 个文件条目**的存在性、内容摘要和已声明大小，全部匹配；条目含重复引用，不冒称 156 份独立验收。没有读取、计算或保存凭据摘要。原始失败报告、截图和日志均保留原位置，没有复制到仓库。

独立解析原始全景仍为 FAILED／12 项，全部为详情 success／disabled 的真实空候选行与原型示例数据行的单元格存在性差异；完整表单仍为 FAILED／167 项，其中 72 项字面差异逐字匹配 ADR-0098 三个映射，95 项几何差异沿用原精确换行分类。分类文件与原始报告摘要匹配，不增加视觉例外，不把摘要复核称为新视觉验收。既有对照时候选为空，因此它也不能证明后来真实非空候选的显示合同正确。

本轮只读快照确认原项目七服务 running／healthy；前端仍为 `569eb54dc66fb8cbd97ebf70076824cbf77068b2`、三后端仍为 `41c9fa89cb5f3caef146f4b64ff9665c513dca9e`，四镜像逐一匹配前文摘要。`git diff 569eb54d..5c67c492 -- frontend` 为空。创建／只读当时的后端 e022 至当前 41c9fa89 只有 Ark 发现器、其测试和自动配置测试三个文件变化；创建、查询、预设、权限及持久化结构未改，据此复用原证据，不伪造同一次全链新验收。

### 除 CI 外的已确认合同缺口

**S1-C01：候选显示名称与供应商调用编码没有分离。** [ADR-0070](../../../architecture/decisions/ADR-0070-authoritative-platform-model-discovery.md)要求冻结受控显示名称，普通候选显示用户可理解名称，内部供应商标识只用作受控选择值；ADR-0099 明确继承此不变量，没有授权编码可见例外。

静态源码链已闭合：`PlatformProviderModelCandidateView` 仅有 `modelCode/modelType/capabilities/classification`，没有独立显示名称；`PlatformProviderModelCandidateReadService.list` 直接投影 `candidate.modelCode()`；`PlatformProviderConnectionDetail` 的候选单元格直接渲染 `item.modelCode`。三个文件分别与实际前端／后端运行提交中的 Git 对象完全相同。因而现有投影无法独立表达用户名称和供应商调用标识，查看及探测后候选区不满足已接受合同。本结论来自源码，不冒称新增生产浏览器失败，也不推导为秘密泄露或厂商鉴权失败。

下一 S1 局部修复应先以不同的合成显示名称／不透明供应商编码复现，再按 ADR-0070 补齐受控名称的来源、投影与页面消费，保留编码的选择／调用用途、未知分类、修订及时效约束。若来源缺失，须明确安全降级，不能用复制编码或猜测能力掩盖。涉及扩展和持久化时先做图谱影响分析，按实际范围补验；只复验受影响的非空候选及原型区域，不重跑已通过且未变的全部页面，不使用现有数据制造状态。本轮只登记缺口，不修改公共字段、模型事实或原型。

### 阻断与下一动作

| 类别 | 当前结论与负责动作 |
| --- | --- |
| S1 产品合同 | **S1-C01 未闭合**。留在 S1 局部修复；不需要人工重新提供登录或厂商凭据，也不转入 S2 |
| 外部 CI | 基线 [Actions 39227797693](https://github.com/liming-cloud/ai-platform/actions/runs/39227797693) 为 failure，唯一作业步骤数 0，原始消息为付款失败或 spending limit；按前节账单步骤由账号所有者处理。Ubuntu 26 提示只是 notice，不算产品测试失败 |
| 本轮控制面收口 | 五项合同、证据引用与恢复入口可以独立修正；本轮本地验证和最终推送另行记录，不替代云端 CI |
| 非 S1 阻断 | 真实推理 Key 可用性、模型分类后的 Chat／Embedding／RERANK 实测、平台部署及租户授权属于 S2；完整 IAM 审计未做不影响两个已成功 Action 的原结论；不为这些后续内容重复当前验收 |

本轮工程助手环境为 Local Codex 当前安装插件，显式加载 `engineering-assistant:wayfinder`、`engineering-assistant:code-review` 及 `references/evidence-and-stage-audit.md`；依本包控制面与 ADR 内联审计，未复用其他任务 Runtime。无 StageRunResult，阶段结果校验不适用。代码图谱探索 `PlatformModelSupplyWorkspace`、详情动作及候选 Controller／ReadService／View，使收口从“只待 CI”改为同时保留 S1-C01；图谱同名候选对象误定位时按精确文件补读。未修改重要服务或共享契约，本轮不需要实施影响分析。

本轮已执行 `make decisions-sync`、`make project-status`、`make project-verify`、`make architecture-guard`、`make resume-context`，退出均为 0；治理 81 项／30 任务、架构 50 项及双端守卫通过，恢复输出 11,822 字节。固定 diff 仅含任务恢复入口、本证据和实现矩阵，其他页面操作、STATE、业务代码及原型不变；差异格式检查通过。没有重跑前后端全量或已通过的业务验收。

新目录的 `evidence-digests.json` 绑定 11 项非秘密审计产物，SHA-256 为 `4e28dec083e1fb4e55a78ae08fdc6221db8c86892358a9ab0bcd8b8db8747d8d`；包含原证据摘要复核、五模式状态／版本、原始视觉分类复算、源码／运行快照、候选合同链、CI 原因、本地门禁与固定差异评审，均为 0600。此次“证据链复核与交付清单纠偏”子任务已闭环，最终提交／推送与 CI 在 GitHub 回执核对；S1-C01 和外部 CI 仍未闭合，S1 整片、WP-0019、M6 均不标记完成。
