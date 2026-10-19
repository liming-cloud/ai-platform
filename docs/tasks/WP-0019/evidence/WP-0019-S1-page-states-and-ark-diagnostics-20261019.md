# WP-0019 S1：页面七态与方舟零候选调查

执行日期：2026-10-19（Asia/Shanghai）。本轮已闭合零候选原因调查，以及下文声明范围的页面七态、全景与完整表单验收；真实厂商非空合同、正式新建流程及外部 CI 仍未闭合。S1、WP-0019、M6 未完成，不进入 S2～S6。本文件集中维护本轮两个子任务的独立结果，不改变历史验收。

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
