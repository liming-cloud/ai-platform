# 产品与 UX 设计

- 状态：实施中；产品事实以真实 API、自动门禁和 deploy 环境验收为准
- 产品形态：企业级多租户 AI 构建、治理与运行平台

## 产品承诺

平台让企业在同一套可审计、可授权、可恢复的控制面中完成 AI 能力选型、知识治理、应用构建、发布、使用和运营。产品价值不是“页面齐全”，而是让使用者知道当前事实、为什么失败、下一步能做什么，以及每次高风险变化由谁在何租户内完成。

## 产品设计结论

Design Read：面向受监管或高治理要求企业的多租户 AI SaaS 控制台，采用信任优先、证据导向、低干扰的 Fluent UI v9 语言。

- `DESIGN_VARIANCE=3`：页面结构可预测，变化用于任务优先级而非装饰。
- `MOTION_INTENSITY=2`：动效只表达反馈、状态变化和上下文连续性。
- `VISUAL_DENSITY=7`：支持专业用户快速扫描，同时为错误、影响和恢复保留足够空间。
- redesign 模式为目标重构，但必须保留已经成立的 URL、权限、可访问性和运行事实；信息架构变化必须由真实任务和后端 seam 驱动。
- `design-taste-frontend` 的 landing-page 组合规则不适用于密集后台；本产品采用其 audit、anti-slop 和 pre-flight 原则，并以 Fluent v9 作为唯一产品设计系统。

## 产品原则

1. **任务先于模块**：用户看到“审批模型”“恢复文档”“发布应用”，不需要理解 Maven context、MQ topic 或数据库表。
2. **事实先于乐观文案**：服务端未确认时不显示成功、0、ACTIVE 或“已发布”；未知、部分成功和陈旧数据必须明确标识。
3. **授权先于导航**：真实路由必须同时满足服务端 menu、permission 和 tenant feature；前端角色标签只用于解释，不参与放行。
4. **恢复是功能**：错误体验必须说明发生了什么、影响什么、能否重试、如何重试和 traceId；永久 pending 是产品缺陷。
5. **危险操作可审计**：对象、当前事实、目标变化、影响、理由、幂等键和结果形成一个连续确认链。
6. **证据贴近任务**：引用、审批、审计和调试证据在当前对象旁呈现，避免用户在多个页面拼接上下文。
7. **多租户默认关闭**：租户来自认证主体，资源不可见返回 404；任何浏览器 tenant 字段都不能扩大访问范围。
8. **能力真实性**：没有领域、API、权限、持久化、UI、恢复和验收的能力不进入导航、营销口径或发布范围。

## 当前真实身份入口与首要任务

生产产品当前只承认身份服务真实返回的三类入口：

| 身份入口 | 首要问题 | 首页真实数据 | 首页首要任务 |
|---|---|---|---|
| 平台管理员 | 平台和租户是否可控 | `/api/v1/summary/platform-overview` | 待审批、异常租户、缺少租户管理员、模型失败、RAG 授权拒绝、应用发布阻断 |
| 租户管理员 | 团队能否安全使用 | `/api/v1/summary/tenant-workbench` | 权限申请、用户角色缺口、知识索引异常、应用发布阻断 |
| 业务用户 | 如何可靠开始当前工作 | `/api/v1/ai-applications` | 从当前账号已授权应用直接创建会话；不伪造最近会话或跨用户待办 |

安全审计、审批运营、知识运营、应用构建和平台运维是权限驱动的任务画像，不是伪造的独立登录角色。只有当身份契约、权限码、真实路由、后端纵向 seam 和测试同时成立时，对应任务才可显示。一个人可拥有多个后端角色码，但导航仍由实际权限与租户 feature 共同裁剪；未授权功能 fail-closed。

### 最终浏览器必须完成的三条真实任务

以下是三类登录入口的最小产品验收，不是“打开首页并截图”：

| 身份入口 | 真实任务 | 成功结果 | 必测失败/恢复 |
|---|---|---|---|
| 平台管理员 | 登录 → 从平台总览识别一个治理事项 → 进入模型中心 → 对明确 provider/config 发起停用确认并填写原因 | 对象、影响、理由、提交状态和 trace 可见；刷新后服务端事实一致 | 无权限菜单消失；取消不写入；失败保留对象上下文并可重试；秘密不回显 |
| 租户管理员 | 登录 → 用户管理查看/重试邀请 → 切换角色并安全编辑权限 → 进入知识库查看文档状态并触发允许的恢复动作 | 切换角色不会提交上一个角色的陈旧权限；列表/动作反馈分离；文档失败有原因和恢复入口 | 网络失败关闭陈旧数据；快速切换忽略旧响应；越权 403/不可见 404；租户 feature 关闭时不可操作 |
| 业务用户 | 登录 → 从授权应用列表进入应用 → 新建会话 → 发送消息 → 在可重试失败后重试同一 `clientMessageId` → 查看引用/反馈 | 只有一条用户消息与一个最终结果；重试不重复乐观消息；引用仅来自授权知识库 | 刷新/进程恢复不重复计费；失败可放弃并编辑；跨租户/未授权引用为 0 |

每条任务都要记录账号/tenant、前置数据、操作、API/DB 事实、截图、axe、键盘路径和 traceId。若最终 seed 无法提供所需对象，先通过正式产品 seam 建立 fixture，不能把按钮可见当成任务完成。

## 信息架构

顶层任务域固定为：

1. **总览**：当前角色最重要的待办、异常和最近工作，不使用泛化 KPI 墙。
2. **治理**：租户、成员与角色、审批、审计、模型与秘密、策略。
3. **构建**：模型、知识与应用；只呈现有真实后端 seam、权限和状态机的能力。
4. **运行**：业务工作台、应用执行、会话、任务、引用和反馈。
5. **运营**：健康、事件、失败任务、成本、配额、恢复和报告。

每个导航项必须同时具备权限标识、租户 feature、真实路由、页面状态、后端能力和测试。默认还必须由服务端 menu 授权；服务端暂缺 menu 时，只能通过带稳定 policy id、角色桶范围、理由和测试的 `registered-permission` 策略登记，禁止以缺少 `menuCodes` 的方式形成隐式例外。

### 当前生产路由地图

以下 15 个路由来自当前 `frontend/console-web/src/navigation.ts` 和 `pages.tsx`。服务端菜单名称可替换显示 label，但不能改变冻结的产品路径和授权语义。

| 身份任务域 | 路由 | 任务 | 主要权限/feature |
|---|---|---|---|
| 平台治理 | `/admin/platform-overview` | 平台任务首页 | `summary-ops:platform-overview:read` |
| 平台治理 | `/admin/tenants` | 租户与代管 | `identity-access:tenant-delegation:manage` |
| AI 资源 | `/admin/model-hub` | 供应商、模型和连通性 | `model-hub:provider:read` + `model-hub` |
| 风险控制 | `/admin/approvals` | 平台审批任务 | `approval-center:task:read` |
| 风险控制 | `/admin/audit` | 安全审计 | `observability-audit:security-log:read` |
| 租户治理 | `/tenant/overview` | 租户任务首页 | `summary-ops:tenant-workbench:read` |
| 组织与权限 | `/tenant/users` | 用户与邀请 | `identity-access:tenant-setting:manage` |
| 组织与权限 | `/tenant/roles` | 角色权限完整替换 | `identity-access:tenant-setting:manage` |
| 风险控制 | `/tenant/approvals` | 租户审批任务 | `approval-center:task:read` |
| 风险控制 | `/tenant/permission-requests` | 权限申请决策 | `identity-access:permission-request:manage` |
| AI 资产 | `/tenant/knowledge-bases` | 知识治理与恢复 | `knowledge-base:knowledge-base:read` + `knowledge-base` |
| AI 资产 | `/tenant/applications` | 应用构建、访问策略和发布 | `app-studio:application:read` + `app-studio` |
| 业务使用 | `/workspace/home` | 授权工作台 | 服务端 `business.my-workspace` 菜单 |
| 业务使用 | `/workspace/apps` | 授权应用目录 | `app-studio:application:read` + `app-studio` |
| 业务使用 | `/workspace/knowledge-bases` | 授权知识目录 | `knowledge-base:knowledge-base:read` + `knowledge-base` |

`/workspace/apps/:applicationId/chat` 与 `/workspace/sessions/:sessionId` 是授权应用路由的受控子路径。历史 `/target-state/*` 必须返回 404；Prompt、Tool、Agent、Workflow、Evaluation 不在本版本导航和能力范围。

当前 deploy 数据未登记 `/admin/audit` 与 `/tenant/approvals` 的对应服务端 menu code。前端因此只登记两个可审计例外：`security-audit-menu-gap` 要求 `observability-audit:security-log:read`，并允许专职审计员脱离管理员角色桶；`tenant-approval-menu-gap` 同时要求 tenant-admin 角色桶和 `approval-center:task:read`，不允许跨角色桶。其余路由全部使用 `server-menu` policy，缺菜单时 fail-closed。服务端补齐菜单后必须删除对应 gap policy，不能长期并存两套授权源。

### 导航决策

```text
auth/me 主体
  -> server-menu policy: 服务端 menus ∩ permissionCodes
  -> registered-permission policy: policy id ∩ 角色桶范围 ∩ permissionCodes
  -> tenant features
  -> 可见或带解释的禁用入口
  -> routeDecision 再次 fail-closed
  -> 页面内资源授权
```

权限不满足时隐藏入口。租户明确关闭 feature 时，可以保留键盘可读的禁用入口并解释原因，但点击和直接 URL 都不能执行页面行为。多角色用户获得授权入口并集；默认首页只选择其中第一个未禁用且与服务端 defaultRoute 一致的路由。

### 三类任务首页契约

- 首屏先显示待办、异常和可执行操作，运行规模与数据新鲜度作为次级上下文，不使用等权 KPI 卡片墙。
- 数值只来自明确的 API 字段；字段缺失时显示数据不可用，不回退为 `0`。
- 处理按钮只在当前账号具备对应权限和真实路由时出现；跨角色的空跳转不得出现。
- 平台与租户首页显示服务端 `asOf`、新鲜度和 `traceId`；业务首页因当前列表契约无 `asOf`，明确标注客户端“读取时间”。
- 加载保留结构预期；网络失败关闭工作面并提供重试；部分汇总保留成功事实并明确列出失败指标。
- 历史 `/target-state/*` 地址返回明确 404；评测、成本和工作流在无后端纵向 seam 时不进入生产导航。

## 关键 P0 用户旅程（目标验收，非交付声明）

### 平台开通租户

平台管理员创建租户 → 指定初始管理员与能力包 → 查看预检 → 确认高风险动作 → 租户进入 provisioning → 依赖初始化成功后 active → 审计记录完整。失败必须显示失败阶段、重试/回滚动作和影响。

### 租户邀请与授权

租户管理员发起邀请 → 设置角色/有效期 → 受邀者接受 → 会话建立租户主体 → 权限生效。重复邀请、过期、禁用租户、越权角色和会话撤销均有明确结果。

### 模型供应商发布

具备模型中心写权限的平台操作员录入供应商地址和秘密 → 提交供应商审批 → 与申请人不同的独立平台审批人审阅安全影响并批准 → 平台操作员创建模型配置并提交审批 → 与申请人不同的独立平台审批人审阅模型限制、成本和影响并批准 → 模型回到“待启用”并清空所有审批前连通事实 → 平台操作员重新执行连通性测试 → 测试通过且可靠策略投影一致后才可被下游选择。两级审批均实行四眼原则，申请人不能审批自己的申请。具备 `model-hub:provider:disable` 权限的操作员可对待审批供应商执行“撤销审批”，请求必须携带当前 `expectedVersion`，成功后供应商进入 `DISABLED` 并清空审批证据。界面必须说明“审批任务已失效”；审计事件必须记录 `MODEL_PROVIDER_APPROVAL_CANCELLED`、原审批 requestId、撤销理由和 `APPROVAL_TASK_INVALIDATED` 结果。审批中心若暂时仍显示待处理，该任务也不再具有目标授权效力；任何迟到或重复的批准、拒绝事件都必须以当前状态、requestId 和 CAS 围栏成为无副作用操作，不得重新激活供应商、改写模型配置或发布模型策略。模型待审批期间不得修改或测试，被拒绝后只能通过修正并重新提交产生新的审批任务，已弃用配置不可复用。秘密永不回显，失败不泄漏账户或凭据。租户管理员的最小权限不包含模型中心或平台审批能力，不能承担上述创建与审批职责。

控制台只开放具备完整类型化字段契约的 OpenAI Compatible、Ollama 和 Mistral AI。供应商类型决定并锁定鉴权方式，任何类型切换都会清空旧地址和秘密，避免把上一供应商的连接状态带入下一次提交。真实外部供应商必须填写公网 HTTPS/443 服务地址，不能包含用户信息或查询参数，最终以服务端出站安全契约为准。内置“本地确定性运行时”是 fail-closed 的受控验收能力：普通后端和前端构建默认关闭，只有部署方同时显式启用后端 capability 与前端可见性后才可创建或管理；`deploy/` 本地验收基线开启该能力。启用后平台锁定只读运行时标识 `local://deterministic`、锁定 `no_auth`、清空并隐藏凭证输入，并明确提示它不会发起出站连接；能力关闭时，即使数据库中保留历史本地供应商，通用控制台也只显示说明而不提供模型写操作，服务端仍在 ID、持久化、审批、运行时和审计副作用前拒绝。Azure OpenAI（`api_key`）、Amazon Bedrock（`aws_credentials`）和 Google Vertex AI（`gcp_service_account`）在提供完整类型化控制台表单前仅允许通过受认证 API 接入；通过 API 创建的历史实例在通用控制台中保持可见但只读，并清楚说明由 API 管理，不暴露会丢失类型化字段的创建或编辑入口。历史 ZhiPu 类型不再提供新增入口，兼容需求迁移到 OpenAI Compatible。模型 `options.providerKind` 必须与供应商 canonical family 一致，创建和更新均在分配 ID、持久化、审批和策略发布前失败关闭。服务端再次执行同一契约，不能依赖前端禁用态或构建开关作为安全边界。

本地确定性运行时只实现 `CHAT` 与 `EMBEDDING`，未知或已由专用 strategy 管理但不支持的 provider/model 组合不得回落到通用 strategy。向量维度在表单、options strategy、factory 和 model 构造器共同限制为 `1..4096`，`options.dimensions` 必须与模型配置维度一致，并在任何数组分配前拒绝超限。知识库和应用选择器只返回 `ACTIVE` 供应商下的 `ACTIVE` 模型；待审批、待启用、已拒绝、已弃用或供应商未启用的模型不能伪装成可选资源。

### 知识入库与恢复

知识运营员上传 → 看到解析/切片/索引阶段 → 查看质量与权限 → 发布 → 业务使用者能检索并查看引用。任一阶段失败都可解释、重试、取消或人工恢复，不出现永久 pending。

### 应用构建与发布

构建者创建草稿 → 固定模型、知识库与应用运行配置快照 → 运行预检 → 发布 → 可回滚。阻断项必须定位到具体依赖和修复入口。独立 Prompt、Tool、Agent、Workflow、Evaluation 产品不在当前发布范围，不能通过占位控件或静态成功文案暗示存在。

当前工作树实现状态为 `IMPLEMENTED_LOCAL`，不等于发布验收通过：发布会创建 tenant-scoped、数据库拒绝更新/删除的 release snapshot，并原子推进唯一 active release pointer；草稿后续编辑只改变下一次待发布配置，不改变活动快照。版本历史展示版本号、PUBLISH/ROLLBACK、来源版本、模型、知识库、Prompt 摘要与 SHA-256、冻结受众数量、发布人、理由、时间和 trace。回滚不改写历史版本，而是复制目标快照形成新的单调版本；例如发布 v1、发布 v2、选择 v1 回滚后，活动版本是来源指向 v1 的 v3。

同一 `publishRequestId` 的并发或网络重试必须收敛到同一个 canonical release。即使第二个请求首次查询尚未看到版本、随后在预检或候选复核阶段看到赢家已经提交，也必须返回赢家的 releaseId/version，而不能把 `DRAFT → PUBLISHED` 的并发状态变化误报为“候选已变化”；输家不得产生第二次版本 mutation、outbox、成功审计或阻断审计。真正不同的候选或复用到其他操作仍失败关闭并要求新 requestId。

应用治理角色的读取范围与变更范围必须显式分离。应用清单由服务端在同一次逐应用访问判定中返回 `accessMode` 与 `capabilities`，前端不得把租户级 permission code 自行推导成跨 owner 能力。持有 `app-studio:application:manage-acl` 的租户治理人员可以发现租户应用，并以 `GOVERNANCE_READ_ONLY` 查看冻结发布证据，用于审计、支持和权限排障；清单投影不返回未发布说明、当前可变模型、知识库、系统提示词或更新时间，发布证据也只返回 Prompt SHA-256，不返回 Prompt 摘要或全文。若应用存在活动 release，清单名称与类型来自该不可变快照；若尚未发布，只显示稳定应用编号和“未发布应用”占位，不能借发现能力读取草稿内容。

`GOVERNANCE_READ_ONLY` 不是跨 owner 的超级写权限，其逐应用 mutation capability 必须全部为 false。应用更新、发布预检、发布、回滚和 ACL replacement 仍要求主体属于应用 owner 范围并同时具备对应动作权限；owner 的能力继续按本人真实 permission code 计算，业务用户目录继续只投影其获准使用的活动 release。UI 对治理只读项只显示“查看证据”、范围说明和冻结版本表，不渲染编辑、预检、发布、回滚或访问策略入口；服务端仍以 owner 范围失败关闭，不能依赖前端隐藏作为安全边界。

`accessMode` 与 `capabilities` 是一个不可拆分的服务端授权事实。清单与创建响应必须同时返回这两个字段；浏览器只接受与访问模式相容的完整布尔能力集合，任一字段缺失、类型错误、部分更新或业务用户携带写能力时统一降为 `BUSINESS_USER` 且所有能力为 false。创建成功也不能由客户端根据当前用户权限自授予 owner：若服务端没有确认完整能力对，控制台不得把该应用写入本地列表。业务用户响应与前端状态还必须双重清除 `modelConfigId`、`knowledgeBaseIds` 和 `systemPrompt`；页面只展示可识别的活动版本、使用状态与进入业务任务的入口，并明确说明运行配置受保护，不能渲染配置卡或借恶意响应恢复内部字段。

应用清单当前采用 200 条兼容窗口，而不是伪分页。服务端读取确定顺序的 201 条探测窗口，并批量装配知识库绑定与活动版本；正常窗口的持久化查询次数不随应用数增长。发现第 201 条时必须记录 `SECURITY_APP_CATALOG_WINDOW_EXCEEDED` 并返回明确失败，UI 保留错误与重试/联系管理员的行动说明，不能把前 200 条伪装成完整目录、显示空态或用当前数组长度充当总数。正式分页上线前必须保持这一失败关闭边界。

应用变更按同一资源行串行化。创建时应用主记录与知识库绑定在一个事务中提交；更新、ACL replacement、发布和回滚在同一事务内先锁定 tenant-scoped 应用行，再重新读取授权和候选事实后写入。任一绑定、版本指针、outbox 或审计前置写入失败都必须整体回滚，不能留下“页面显示已更新但运行版本/知识绑定仍旧”或反向的分叉事实。

`AI_APPLICATION` 的权限申请批准只写入应用草稿 ACL，不修改活动 release 已冻结的允许用户受众。权限运营员完成批准后必须把交接明确给应用管理员；应用管理员重新预检并发布新版本后，申请人才可创建使用新受众的新会话。审批成功、ACL 写入成功和运行访问生效是三个不同事实，UI 与审计不得把前两者表述成“应用已可用”。当前应用 ACL 公共契约不提供现有 userIds/roleIds、策略版本和 CAS，因此控制台整体替换入口保持只读，不能要求管理员凭记忆重建完整清单；权限申请的最小授权路径继续可用。该延迟生效规则只属于版本化的 AI 应用受众；其他资源仍按各自授权合同显示即时或延迟结果。

会话采用“创建时固定、活动版本变化后强制新建”的安全策略。创建会话会持久化并返回当时的 `releaseId/releaseVersion`；会话列表以 `requiresNewSession` 明示它是否已经落后于当前 active release。发布或回滚推进 active pointer 后，旧会话不得静默漂移到新模型、知识、Prompt 或受众，也不得继续按历史 ACL 运行：发送消息会在检索和模型调用前以 HTTP 409、`AIP-APP-CHAT-SESSION-CONFLICT` 失败，提示“应用已发布新版本，请新建会话后继续”，并记录 `SECURITY_APP_SESSION_RELEASE_CHANGED`。用户必须从授权应用重新创建会话；相同配置被回滚为 v3 也不复用 v1 会话，因为运行身份是 release，而不只是配置内容。

### 业务使用与反馈

使用者从授权应用进入 → 提交幂等任务/消息 → 查看进度、结果、引用和降级 → 反馈问题。刷新或重试不得产生重复计费或重复消息。

### 安全审计与会话处置

安全审计员按 actor/tenant/resource/trace 检索 → 查看 before/after 和关联事件 → 撤销会话或升级事件 → 导出证据。敏感正文受权限和脱敏策略约束。

### 运维故障恢复

运维收到告警 → 定位依赖/队列/任务 → 查看影响租户和 SLO → 按事实源选择数据库作业权限化重排、source outbox 受控重派发或降级 → 验证恢复 → 形成事件记录。broker DLQ 不能提供“复制消息体重放”动作。当前 broker 恢复仍是双人 runbook，不得宣传为已交付的运营 UI；产品不能只显示红灯而没有安全的下一步说明。

## 状态与反馈原则

- 每个异步对象都有有限状态机、更新时间、责任主体、可用动作和终态。
- Loading 使用与最终结构匹配的 skeleton；超过阈值显示当前阶段，不使用无限 spinner 掩盖未知。
- Empty 区分“尚未创建”“筛选无结果”“无权限”“依赖不可用”，并给出匹配的下一步。
- Error 显示可理解原因、traceId、重试条件和安全的恢复入口；不得回显秘密或堆栈。
- Success 说明事实变化和后续影响，不使用只有绿色 toast 的短暂反馈。
- Degraded 明确当前仍可做什么、数据是否可能陈旧、何时重试。

## 前后端产品契约

### 统一响应与错误

- API 响应为 `{ header: { code, message, traceId? }, body }`。HTTP 非成功或 `header.code != SUCCESS` 都进入错误路径。
- `SUCCESS` envelope 仍须经过操作专用 response guard：创建校验返回对象身份、状态和审批 requestId，更新/停用校验 provider/resource 绑定、允许终态和版本严格推进，连通性校验 `connected` 与 `connectivityStatus` 一致。字段缺失、错绑、旧版本或自相矛盾统一为 `INVALID_RESPONSE`，不显示成功。
- 错误必须提供稳定 code、面向用户的安全 message、traceId 和必要的字段问题；不能包含 SQL、堆栈、provider 原始秘密或知识正文。
- 401 终止当前 session 并回到登录；403 表示主体存在但缺动作权限；租户 feature 关闭进入明确 feature 页面；不可见资源由服务端返回 404。
- 页面写错误与列表读错误分离。写失败不抹掉仍可信的列表，读失败不保留可操作的陈旧对象。

### 身份与会话

- `/api/v1/auth/me` 是前端身份事实源，至少返回 tenantId、userId、roleCodes、permissionCodes、menus、defaultRoute 和 accessTokenExpiresAt。
- 浏览器给每次 session 生成不可复用 generation，并将请求绑定 token、tenant、user 和 expiry 快照。另一标签页换号、退出或到期会终止所有旧请求。
- 页面不得使用 role 字符串替代 permission；不得在 mutation body 中加入 tenantId 以覆盖主体上下文。

### 列表与筛选

- 服务端分页统一使用 `items/page/pageSize/total/totalPages`。UI 不得取当前页长度冒充总数。
- page 从 1 开始；改变筛选或 pageSize 回到第一页；服务端因数据收缩更正页码时，UI 接受服务端事实。
- 搜索、状态和时间筛选有稳定 query 名称；列表请求支持 AbortSignal，最近请求拥有提交权。

### 写操作与幂等

- 危险 mutation 必须包含非空 reason；发布、代管、重放和消息等有重复风险的命令使用稳定 request id。
- UI 在提交中锁定同一对象，不锁死无关任务；失败后只有在业务含义未变化时复用同一幂等键。
- 创建类表单打开时生成一次 request id；同一非秘密 payload 的网络失败、408/425/429、5xx 或 `INVALID_RESPONSE` 继续使用原键。用户改变任一业务字段代表新意图并换键；只有能证明命令未被接受的输入、权限或业务拒绝才允许为下一次提交换键。
- 未决模型创建把版本化的原非秘密 command 与 request id 保存到 tenant+user scoped session storage；只接受精确字段、UUID、枚举、字段长度和 8 KiB 总量边界，损坏或身份错配立即删除。取消、Esc、遮罩和 reload 后以持久 warning 提供“继续核对/重试”，同时禁用两类新建并锁定恢复字段；credential 始终清空且重新输入。幂等冲突不换键；成功或确定 4xx 拒绝才清理未决意图，确定拒绝后的下一次提交使用新键。
- 高风险命令的网络结果未知时冻结完整意图，关闭再打开也只能按原 request id 重试；只有原意图得到权威结果并完成服务端 GET 对账后，才允许清空快照并为下一项意图生成新 request id。
- 当前代管契约没有按 actionId/request id 查询结果的 GET；因此模糊结果不能靠列表猜测，只能以原 request id 重投，直到服务端返回同一成功动作与审计编号。收到成功证据后再读取目标租户，读取成功前仍不开放下一项意图。
- 整体替换命令若缺少当前服务端快照、版本/CAS 或 before/after 任一项，前端写入口必须 fail closed，不能用空值、本地缓存或用户记忆补齐未知事实。
- 成功响应只表示服务端已经接受或完成的事实。异步结果必须显示 job/run/status，不把 QUEUED 显示成完成。
- 秘密字段不进入意图持久化：同一打开的 Dialog 内失败可以保留以便用户判断和精确重试，但供应商类型变化、取消、关闭或成功后必须清空；错误、结果和恢复证据都不得回显。取消后焦点返回触发器，失败聚焦当前 Dialog 的稳定错误/提交区域，成功后聚焦持久结果或仍存在的对象标题。

### 证据与披露

- 审批、审计、RAG 引用、模型调用和恢复任务共享 evidence 概念：tenant、actor、resource、source/version、time、result、traceId。
- 前端只显示契约明确允许的字段；未知 JSON 字段不能直接渲染到详情区。
- 秘密永不回显；调试正文和扩展审计详情需要独立权限、reason、有限时效与新审计。
- 模型审批采用证据先行的主从工作台：在理由输入前展示摘要身份、安全边界、成本/容量、运行参数和逐字段差异；创建场景明确显示“无历史快照”。证据缺失、未知版本或绑定不一致时保留审计可见性，但通过/拒绝均禁用。
- 成本金额用 canonical decimal string 贯穿输入、传输和审批展示；UI 不把受数据库精度保护的金额转成 JavaScript 浮点数。

## 可持续演进原则

1. **先旅程后菜单**：新能力先补 ADR、角色目标、失败/恢复、权限、审计和 deploy 验收，再进入路由或导航。
2. **先契约后页面**：公共 API、事件或 schema 变更先做 codegraph impact 和兼容策略，前端不得凭临时对象推断字段。
3. **一个任务一个事实源**：状态变化由单一业务事务拥有，跨上下文由 transactional outbox/inbox 传播。
4. **发布对象不可变**：模型、知识和应用运行引用固定版本；编辑形成新版本，不静默改变正在运行的事实。
5. **设计 token 而非页面常量**：品牌、间距、圆角、状态和主题只在 Fluent theme/语义 token 演进。
6. **状态机可扩展但不可含糊**：新增状态必须定义允许进入/离开、可用动作、终态、恢复与兼容显示。
7. **遥测随行为交付**：新增高风险或异步行为同时交付 trace、审计、指标、告警和 runbook；不能事后补可观测性。
8. **证据可替代**：每次验收记录 supersedes，旧截图和旧测试报告不自动证明新 SHA。
9. **删除优于占位**：没有纵向 seam 的导航、卡片、按钮和文案直接移除，不保留“即将上线”构成虚假范围。
10. **使用者复核不可省略**：自动测试证明一致性，实际角色 reviewer 负责判断任务是否可发现、术语是否清楚、影响是否可理解。

## 产品验收指标

- 关键旅程任务成功率不低于 95%，P0 安全旅程必须 100% 正确拒绝越权。
- 新用户在无口头指导下能定位首要任务；每个 blocker 错误至少提供一个可执行恢复动作或明确责任人。
- 高风险写操作审计完整率 100%；跨租户数据泄漏、引用泄漏和客户端 tenant 覆盖为 0。
- 异步任务永久 pending 为 0；重复命令产生重复业务事实为 0。
- 生产导航占位项为 0；UI 事实与 API/状态机不一致为 blocker。

## P2 实施与验收边界（2026-07-21）

- `IMPLEMENTED_LOCAL`：三类入口首页、旧目标态 404、服务端菜单/权限 fail-closed、安全上下文、请求取消/陈旧响应保护、严格 mutation response guard、未决创建跨取消/Esc/遮罩/reload 的 tenant+user scoped 原键恢复、秘密清空与焦点真相、Fluent 三主题、移动导航焦点与高风险确认已经进入当前工作树。
- `IMPLEMENTED_LOCAL`：前端 lint、行为测试、production build、bundle budget 与 Playwright 用例发现有工作树检查点；精确测试数留给最终质量报告，避免文档随实现变化失真。
- `PENDING`：尚未在最终候选 SHA 的隔离 deploy 环境执行上表三条真实任务、axe、键盘、200% 缩放、五视口与 light/dark/high-contrast 人工复核。
- `PENDING`：产品 reviewer 尚未从实际使用人员视角签署任务成功率、错误可恢复性、术语清晰度和危险动作影响说明；静态 `design-taste-frontend` pre-flight 不能替代该结论。

关键旅程应同时验证前置数据、操作、API、数据、审计与失败恢复。
