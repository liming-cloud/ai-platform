# WP-0018：成员账户与统一授权生产契约提案

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0018 |
| 状态 | 已接受，生产决定见 ADR-0093；实现与验收尚未完成 |
| 日期 | 2026-10-06 |
| 验证提交 | 5a6789c13a2a693bf77e8e83be7e197076e79ec8 |
| 验证范围 | 当前源码、Maven 方向、原项目原型与 9 页／19 项操作的设计调查；不包含生产实现或部署验收 |
| 提案范围 | 普通成员直接开户、资格与密码治理、独立功能保存、助手／策略共用授权事实 |
| 决策去向 | 2026-10-08 登记 ADR-0093；接受不等于实现或验收，逐项验证状态仍待真实证据 |

## 固定输入与证据边界

本提案承接[工作包](README.md)，产品前沿仍为 WP-0018，M6 未完成。输入及实施基线 `2cca286848e174f5f7d3561712a9b1b86b382914` 是调查提交的祖先；调查开始时 main、origin/main 同指上述验证提交，工作区与暂存区干净。五项 fixture 已在 GOV-0002 提交，本次不改变测试资产。

| 来源 | 位置与固定对象 | 可用性及用途 |
| --- | --- | --- |
| 目标与范围 | [Charter](../../foundation/charter.md)、[ROADMAP](../../foundation/roadmap.md)、[STATE](../STATE.yaml)、本包及其全部 ADR | 可用；限定 M6、三角色和本包责任 |
| 规范模型 | [领域与公共契约基线](../../design/domain-and-contracts.md) | 已接受；本提案只记录 organization-access 的增量，不复制第二份规范模型 |
| 源码 | 验证提交中的 organization-access、assistant-delivery、trusted-query、platform-security、console-web | 可用；用于确认现有行为、数据所有者和影响范围，不把源码存在当作产品验收 |
| 原型 | OpenDesign Cloud 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，[项目入口](http://127.0.0.1:49175/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html) | MCP 核实原项目并一次读取 index.html 依赖包，17 文件，未截断；未创建副本或修改原型 |
| 原型版本 | 入口／操作 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`，共享布局 `47c4f6f31823617633ce0bac98074208781f5565`，最新目录 `d36c7f46d0187c0421d4989c12f601d5c190d73f` | 最新接受版本至调查提交的原型资产差异为零；以实际渲染模块为准，不能只抄 page-fixtures.json |
| 接口与数据 | 既有 `/api/v1/tenant/members`、成员资格／身份／角色源码，租户功能事实及公开助手目录 | 可用；新契约尚未接受，未查询秘密或准备业务数据 |
| 部署 | [WP-0017 收口证据](../WP-0017/evidence/WP-0017-adjusted-scope-closure-20261006.md) | 历史已验证部署记录；本轮不部署，不能作为新成员功能证据 |

现有决定继续有效：ADR-0012、0022、0067、0073、0075、0078、0079、0082、0083、0085、0092。普通成员可以有多个租户资格的既有事实不被改写；ADR-0083 的“只能管理一个租户，停用仍归属原租户”仅限制租户管理员。ADR-0078 只接受原型，不替代本生产事务提案。

## 已查明的差距与影响

| 当前源码／原型 | 查明事实 | 设计影响 |
| --- | --- | --- |
| `TenantMemberWorkspace`、`TenantMemberStatusController`、`TenantMemberEstablishmentApplicationService` | 当前从全局候选选择既有主体；POST 只接收 subjectId，不创建身份。生产 `PostgresTenantMemberEstablishmentStore.insert` 已在同一事务写入资格和固定 TENANT_USER 绑定，尚未写成员功能、助手范围和开户审计 | 新开户替代候选入口，复用已有资格／固定角色原子写入，不复制密码或保留两个可编辑的新建流程 |
| `TenantMemberGovernance`、`TenantMemberStatusApplicationService`、`TenantMembership` | 已有管理员保护、成员锁、资格状态、permissionVersion 和会话撤销；状态 HTTP 未携带预期版本 | 复用领域与会话机制，补当前资源版本拒绝；不从前端隐藏推断授权已成立 |
| `SubjectIdentity`、`PlatformTenantAdministratorCredentialService` | 身份与凭据全局唯一，已有密码端口、主体锁、全范围撤销及字符数组清理 | 普通成员重置另核对唯一归属；不能直接复用平台权限入口或另传主体标识 |
| `TenantFeature`、`TenantFeatureAvailabilityService`、`BrowserSessionCapabilityService` | 已有八项租户上限，尚无本包四项成员配置事实 | 成员只保存后四项，不能新增管理权限或把租户开关当作成员授权 |
| `TenantBuiltInRole`、`DefaultQueryAssistantCatalogService` | 普通角色已有 assistant:query／knowledge:read；当前目录按租户返回已发布助手，没有成员授权集合过滤 | 无助手负例必须落到后端；过滤发生在分页和总数计算前，不采用取完一页再删行 |
| `SaTokenSessionGateway.resolve`、`SaTokenTrustedContextArgumentResolver` | 恢复的是签发时的服务端范围与权限版本；不是成员功能或对象授权的实时事实查询 | 新成员授权读取检查当前事实与版本；撤销范围及正在输出的访问另有反例，不只验证重新登录 |
| 原项目 `member-workspace.js`、`prototype-state.js` | 三步成员开户；账户、功能、助手独立页；重置统计全部资格；策略草稿／预览／发布写入成员助手集合 | 维持独立保存边界和单一授权事实，拒绝旧策略覆盖后续撤权 |

CodeGraph 已执行 `TenantMemberEstablishmentApplicationService`、`DefaultQueryAssistantCatalogService`、`TenantMemberWorkspace` 深度 2 影响分析，分别返回 16、31、4 个关联符号；关联到组织自动配置、助手目录测试及 ConsoleApp。图谱没有覆盖到测试不等于不存在测试，编译与实际测试仍是实施证明。

当前 Maven 边为 organization-access → knowledge-data，assistant-delivery → model-runtime／knowledge-data，trusted-query → organization-access／knowledge-data／model-runtime／assistant-delivery，evaluation-operations → 其五个被依赖上下文。新增组织到助手的公开 facade 使用不形成环；不得再增加反向助手到组织依赖。此处只提出方向，未修改 POM、治理矩阵或已接受架构正文。

## 业务语言、所有者与拟定决定

参与者是当前租户的管理员，业务结果是“普通成员以自己的身份登录，只有明确授予的对话功能和助手可用”。`SubjectIdentity` 是全局凭据所有者，`TenantMembership` 是租户资格所有者；成员功能与成员—助手授权归 organization-access，助手及其发布／永久知识库关系归 assistant-delivery，问答／历史／引用业务资源归 trusted-query。限界上下文和数据所有者不因进程装配改变。

1. **直接开户与资格范围。** 登录名沿用全局 `[a-z][a-z0-9._-]{2,63}`。管理员提交新登录名、初始密码、四项对话功能及已发布助手选择；身份、ENABLED 普通资格、TENANT_USER 绑定、功能、助手授权及业务审计一次提交，全成或全败。数据库裁决并发同名；既有登录名冲突，不复用或覆盖其身份。取消／失败零残留，不允许提交角色、操作者、tenantId 或 subjectId。不把新建唯一身份推导成普通成员终身单租户限制。
2. **密码重置。** 目标仅由当前租户的成员资格定位，在共同主体锁内重读全部资格，含停用资格。必须只有本租户这一条普通资格，不存在平台角色绑定或租户管理员资格；归属不足或共享身份拒绝，不能透露其他组织详情。使用身份和资格预期版本；两次输入一致由表单校验，服务端校验真实密码契约。重置不启用身份／资格，撤销全范围旧会话；撤销失败不提交。密码只进入现有哈希／验证端口和短生命周期字符数组，各路径清理；不进入响应、URL、草稿、日志、审计或幂等摘要。
3. **停用与恢复。** 只修改当前租户普通资格；管理员条目只读，不能提升、降级、治理同级管理员或全局账户。请求带 expectedVersion，旧版本拒绝；资格和 permissionVersion 同步推进，撤销本资格的旧会话。保存的功能和助手集合保留，恢复后须重新登录且按当时租户上限和发布状态计算，不恢复旧会话。被撤销的外部会话不在数据库回滚时重新签发。
4. **四项成员功能。** 固定 assistantSelection、conversation、ownHistory、authorizedCitations；初始勾选沿原型为前两项，后两项关闭，服务端只保存显式选择。保存完整四布尔配置，独立 featureVersion，携带租户功能读取版本；未知／缺失／非布尔、越过上限新增授权或旧版本拒绝。上级关闭时保留已保存值，但有效值关闭；恢复上级不新增从未授予的功能。普通资格停用时仍可修改其保存配置，不能因此启用资格；不能直接复用仅允许 ENABLED 的 revisePermissions 来遗漏此场景。ownHistory 关闭不隐含关闭新对话；历史读取与继续旧会话拒绝，新会话仍按 conversation 与对象授权判断。取消／刷新不写业务事实。
5. **单一助手授权事实。** organization-access 保存唯一的成员—助手关系及租户 authorizationRevision。成员助手页与策略发布是此事实的两个写入口，没有权限并集、平行 ACL 或“策略优先”回退。每次有效集合变化原子推进 revision 和受影响成员 permissionVersion，撤销相应资格会话；撤销失败回滚。成员助手页仅替换该成员集合，不改功能和其他成员。新建／新增关系只接受本租户已发布助手，知识库由拥有者 facade 返回，不允许单独选择。
6. **策略生命周期与过期保护。** 一策略最多一个 DRAFT；已发布版本只读，新版本从其建立草稿，旧发布在再次发布前继续有效。保存／预览固定草稿版本、authorizationRevision、涉及成员资格／功能、租户功能、助手发布事实的版本；编辑或任一依赖变化使预览失效。发布须在事务内核对当前版本，再替换对应助手的成员范围；替换助手时同时移除原助手范围，其他助手不受影响。历史发布版本只证明当时发布，不能在成员页撤权后被重放为当前授权。重新赋权必须创建新版本、保存和基于当前事实重新预览。
7. **真实预览与访问交集。** 预览调用与运行入口一致的授权判定，结果和数量由实际事实计算，不能复制原型的固定通过数或写死零泄漏；事实缺失时失败关闭。预览证明权限判定，不冒称真实问答或历史 HTTP 已验收。有效访问同时要求活动身份、可用租户、有效资格、固定角色、对应租户／成员功能、本租户对象授权和合法发布版本。正在运行的输出、目录、直接路由、历史和引用均复查当前事实；角色权限或浏览器勾选不能单独放行。
8. **最小跨上下文集成。** 组织的候选与提交核验调用助手拥有者的公开 facade；trusted-query 先通过组织公开 facade 取得成员授权范围，再交给助手目录的公开查询合同，在仓储分页前施加范围。助手目录不反向依赖组织。读取适配器转换公开 DTO，不引用别的上下文 domain、内部 application、infrastructure 或自动配置类。新增编译边必须有实际调用方、治理记录、反例与启动测试；不创建通用权限框架或无调用方模块。
9. **并发、审计与幂等。** 共同主体锁仍保护跨范围归属裁决；多主体操作采用确定顺序，所有新写路径统一锁顺序并用真实并发测试排除死锁及先查后写竞态。独立配置版本与租户授权 revision 分开，功能保存不提交助手草稿。操作标识绑定操作者、租户和命令；幂等只重放同一已提交结果，异参拒绝。密码命令不另存密码指纹，必要的同参校验使用已有唯一身份凭据的验证端口；身份已变化时拒绝旧重放。审计／事件和业务同事务，仅记录非秘密定位、范围、操作和版本，不记录请求正文或秘密。
10. **迁移与原型。** 新事实属于组织上下文自己的迁移；既有普通成员明确回填四项关闭、助手集合为空，不复制角色的宽泛权限为对象授权。迁移保留既有身份、状态、角色和多租户关系，变更前核对受影响范围并撤销旧资格会话；缺失或损坏事实失败关闭，不在运行时补默认值。生产沿用原型布局、标题、按钮、七态、分页和草稿行为；仅非秘密 UI 草稿按操作者／租户／登录代次隔离。密码回退、取消、失败、成功及退出时清理。演示口令不进入生产，真实安全语义按已接受 ADR 处理，不扩展视觉例外。

共同主体锁、授权版本、角色和凭据均有既有所有者；不新增“全局成员账户类型”。平台资格与租户资格互斥、管理员单租户、最后管理员保护仍由原 ADR 执行。查询进程只装配组织拥有的只读授权事实，不注册成员写接口。

## 拟定 HTTP 保存边界

以下合同已由 ADR-0093 接受，端点存在不代表已按此实现。当前租户始终从服务端会话恢复；所有写入要求租户管理员、CSRF、严格字段、正整数资源引用及当前版本，无权与错租户不泄露对象存在。

| 入口 | 输入与独立边界 |
| --- | --- |
| GET /api/v1/tenant/members | 扩展既有目录：q、status、pageNumber、pageSize；同范围筛选与总数，稳定排序，默认 20、10／20／50／100；Page/IPage 只在 infrastructure |
| GET /api/v1/tenant/members/create-options | 当前租户四项功能上限、tenantFeatureVersion 与 authorizationRevision；不返回全局身份候选或秘密，助手候选另有标准分页 |
| POST /api/v1/tenant/members | 替代既有 subjectId 请求：loginName、password、conversationFeatures、assistantIds、expectedTenantFeatureVersion、expectedAuthorizationRevision；身份与普通资格完整原子结果，无凭据回显 |
| GET /api/v1/tenant/members/{membershipId} | 当前成员账号投影、资格／身份读取版本、功能与助手摘要；不返回全局主体标识或凭据，管理员只读 |
| POST .../{membershipId}/disable 或 enable | expectedVersion；只修改当前资格，不更改账户、功能或助手集合 |
| POST .../{membershipId}/password-reset | password、expectedVersion、expectedIdentityVersion；唯一归属检查与全范围会话撤销 |
| GET／PUT .../{membershipId}/features | 四布尔值、expectedFeatureVersion、expectedTenantFeatureVersion；仅功能配置和必要的权限版本／撤销 |
| GET／PUT .../{membershipId}/assistants | 完整 assistantIds、expectedAuthorizationRevision；仅对象授权，不提交功能草稿 |
| GET /api/v1/tenant/member-assistants | q 与标准分页；管理员可选择的本租户已发布助手，名称选择，不输入内部标识 |
| /api/v1/tenant/access-policies | 标准目录／创建；仅当前租户，名称、已发布助手、普通成员及读取版本；创建 DRAFT 不立即赋权 |
| .../access-policies/{policyId}/versions | 读取及创建唯一新草稿；草稿保存、preview、publish 各自独立，已发布正文不可原地改写 |

错误类型沿 ADR-0022：字段无效、同名或版本冲突、无权／不可见资源、事实源／撤销不可用分别有稳定脱敏编码；不把数据库错误、内部类名和其他租户事实透出。新请求冻结后移除被替代的成员候选入口及其本范围调用；保留其他任务实际使用的能力，不为收口批量删除旧源码。

开户和密码重置使用现有 Idempotency-Key 协议，不新增通用授权参数。前端独立路径拟定为 `/tenant/members`、`/tenant/members/new`、`/tenant/members/{membershipId}/account`、`/features`、`/assistants`；末两段完整路径仍位于同一成员对象下。旧成员导航若保留，只重定向唯一目录，不保留候选开户。成员引用用于资源定位，不能作为操作者身份。账户页只读，重置与资格操作仍在目录，功能／助手各有自己的取消和保存。

## 场景与模型追溯

全部场景归 organization-access；助手发布事实通过 assistant-delivery facade 协作，问答／历史／引用的请求由 trusted-query 负责。本表是拟定验收，不把场景标为已通过。

| 场景与业务结果 | 命令／查询及前置条件 | 成功／失败／非法结果 | 不变量所有者与模型 | 后续证据 |
| --- | --- | --- | --- | --- |
| 创建普通成员并能以本人登录 | CreateMember；当前租户管理员、唯一登录名、合法功能与助手 | 原子建立完整事实；故障零残留；重复、管理员角色、跨范围参数拒绝 | SubjectIdentity／TenantMembership／组织开户编排；普通固定角色 | 同名并发、每一写阶段故障、正式 HTTP、真实登录、精确 SQL |
| 唯一归属成员重置密码 | ResetMemberPassword；共同主体锁、资格与身份版本 | 只轮换凭据并撤销旧会话；共享／停用跨租户资格／管理员／平台绑定拒绝 | SubjectIdentity；组织归属裁决与会话端口 | 新旧密码、其他租户不变、撤销故障回滚、秘密不外泄 |
| 停用后可显式恢复本资格 | ChangeMemberStatus；普通目标和当前版本 | 本租户失效、保存授权保留；旧会话不恢复、其他资格不改 | TenantMembership 的状态／permissionVersion | 并发版本、另一租户会话、管理员保护、刷新取消 |
| 独立功能保存与关闭本人历史 | SaveMemberFeatures；四项与上限版本 | 仅本功能变更；旧草稿拒绝；历史拒绝而新对话可按授权继续 | 组织成员功能与资格权限版本 | 两页交错保存、直接入口、正在访问撤权；WP-0024 正向旅程联验 |
| 成员页和策略管理同一助手范围 | SaveMemberAssistants／PublishPolicy；对象及授权快照版本 | 一份有效集合；旧策略／预览不能复活撤权；跨租户／未发布对象拒绝 | 组织关系／authorizationRevision／策略版本；助手发布事实属于助手上下文 | 交叉覆盖、两策略竞争、移除助手、预览失效、真实 HTTP 与 SQL |
| 普通成员初始无助手且无管理能力 | ReadEffectiveAccess；成员自己的范围会话 | 后端目录空、直达管理或未授权对象拒绝；不得冒用管理员会话 | 组织固定角色与授权事实；查询消费明确范围 | 本人 Cookie 会话、API 和查询启动测试；WP-0023 发布后真实赋权 |

## 独立切片、19 项操作与收口条件

所有操作 ID 继续以[实现矩阵](../milestones/M6/implementation-map.json)为唯一细项事实源，本次 9 页／19 项仍全部 pending-contract。以下只划分实施及联验责任，不转移操作、减少整包范围或更改顺排。

| 切片 | 本次拟定交付 | 对应操作与退出条件 |
| --- | --- | --- |
| 1．普通成员账户闭环 | 成员目录、三步原子开户、账户独立页、唯一归属重置、资格停用／恢复；开户的初始功能／助手选择和基础访问交集也必须真实生效 | tenant-member-create:0、tenant-member-account:0，tenant-members:0／1／4／5／8；首次触及 TenantMemberDirectoryMapper 时清理精确分页债务。包含无助手本人登录负例、真实数据库／Redis、HTTP、部署浏览器和本片原型对照，不能把基础访问拒绝推迟到授权编辑片或交付一个未接线的向导 |
| 2．对话功能独立保存 | 四项功能、取消刷新、交错编辑和直接访问撤权 | tenant-member-features:0、tenant-members:2 的功能部分；ownHistory 关闭不阻止有权新对话。tenant-members:7 的完整新会话旅程留联验，不能提前把组合操作标 verified |
| 3．助手与策略统一授权 | 助手独立页、策略目录／版本、真实预览及原子发布／撤销 | tenant-member-assistants:0、access-policies:0、access-policy-detail:0／1／2、tenant-members:2 的助手部分；真实隔离种子可检验权限，不冒称生产助手已发布。WP-0023 负责真实发布后赋权正例 |
| 4．固定角色与租户工作区承接 | 固定两种租户角色、成员导航、自己的工作区、退出；概览使用当前租户真实知识／文档／来源／发布事实 | tenant-roles:0、tenant-overview:0／1／2；空工作区来自真实零计数。阻断对象及目标页依赖分别由 WP-0020／0022／0023 承接，缺失时不伪造计数、入口或成功；未来依赖未齐的完整操作保持未验证 |

概览真实渲染源是 `workspace-journeys.js#workspaceJourney`，标题为“工作区概览”，四项摘要为知识库／有效文档／待处理来源／已发布助手；不能用 page-fixtures.json 中旧预算摘要另造设计。存在待处理来源时进入 processing-quality，空环境进入 knowledge-directory；选定知识库与对象属于导航契约。没有新接口事实的阶段不能据原型模拟状态声称这些目标已生产交付。

本包不启用自定义角色；TenantCustomRoleDirectoryMapper 的既有债务单独保留，必须有实际调用者和退役依据才清理。千成员／二百助手采用隔离环境真实种子，目录筛选／分页／总数及已选集合跨搜索不丢失均验收，不灌入生产历史对象来凑样本。

每片按真实红绿测试 → 局部实现与固定 diff 评审 → 当前源码验证 → 固定镜像与 Compose → 生产浏览器功能 → 只读精确数据库事实 → 原项目原型对照 → 绑定完整提交的证据收口执行。三视口 390／1024／1440、200% 文字、键盘、焦点与 reduced-motion 均验证，截图仅 `/private/tmp`。只验证了片内操作时不得更新整个页面／组合操作为已验证。

整包完成必须实现并执行 `make acceptance-work-package WP=WP-0018`，补齐每项生产操作及跨包直接访问证据，执行本包全部验证。上游可使用隔离数据库真实种子检验本包边界；真实验收会员的发布／问答正向旅程仍按 ADR-0079 在 WP-0023／0024／0026 联验。依赖、生产结果和模拟／契约测试分别记录，未经新的接受决定不绕过 WP-0018 完成门禁、转移责任或自动激活 WP-0019。

## 2026-10-06 调查结论与当时的下一动作

源码及原型调查完成，生产契约与切片边界可评审；本提案不是已接受 ADR，没有交付普通成员新功能，也没有执行本包生产开户、重置、部署或视觉验收。未知的是新事务／版本／撤销／启动集成的实际执行结果，必须通过上述测试与真实验收获得，不能由设计推断。

本轮提案检查：`make decisions-sync` 显式同步；`make project-status` 仍为 M6／WP-0018；`make project-verify` 77 项通过，校验 30 个任务及 650 项历史资料；`make architecture-guard` 48 项通过，依赖违规 0、精确分页债务仍 5；`make resume-context` 可恢复提案状态及首片下一动作，小于 12 KiB。固定 diff 检查通过；fixture／原型差异均 0，业务代码、STATE 与实现矩阵没有改变。这些是提案及现行仓库验证，不证明提案中尚未实现的事务已经安全或通过测试。

非秘密日志仅位于新建 0700 目录 `/private/tmp/wp0018-contract-review-wg5pSdmk/`，各日志 0600。固定 diff 内联评审复查已接受决定、归属／角色／凭据扇出、资格状态与独立保存、双入口授权、依赖方向、跨包责任及技术／人工状态的区分；本轮没有生产行为变更，红绿事务与安全测试按上述切片执行，不能以设计走查代替。人工接受仍待业务与架构负责人给出。

业务负责人和架构负责人接受本提案后，将决定登记为正式 ADR 并更新当前任务引用，再从切片 1 的最小失败用例实施。没有接受前仅推进提案检查、控制面恢复和可复核证据，不更改业务接口、领域或持久化结构。实施中发现实质契约冲突先恢复决定一致性。

本轮使用 Local Codex 当前提供的个人工程助手 `2.2.0-rc.1+codex.local-20260929-051321`，显式加载 wayfinder、domain-modeling、code-review 及相关领域建模／场景追溯／设计治理、安全 seam／证据审计引用；内联设计未建立 Runtime 或 StageRunResult，阶段校验不适用。仓库和已接受 ADR 是控制输入，工具结果仅支持静态调查结论，不复用旧运行证明本包完成。

## 当前接受与执行前沿

2026-10-08，用户在收到提案确认项及首片边界后要求“继续推进任务”，据此登记 [ADR-0093](../../architecture/decisions/ADR-0093-member-account-and-effective-access.md)。上节保留调查时点的未接受与验证事实；当前设计已接受，下一动作是按切片 1 执行真实红绿测试、实现和生产验收。接受不改变历史验证日期，不将上述静态检查转换为新生产证据，也不提前完成工作包或 M6。

同日实施前复核发现上述初稿遗漏生产 adapter 已有的普通固定角色写入；已按 `PostgresTenantMemberEstablishmentStore.insert`、`TenantMemberEstablishmentMapper.insertDefaultRole` 和现有真实 PostgreSQL 角色断言纠正事实。原子新身份、初始配置及授权、审计的增量契约不变，既有资格／角色写入应复用，不另造平行实现。
