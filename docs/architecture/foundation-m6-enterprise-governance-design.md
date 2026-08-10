# 可信 RAG 基座 M6 企业治理与发布制度领域设计

## 状态、负责人和范围

| 项目 | 内容 |
| --- | --- |
| 状态 | ADR-0041 至 ADR-0059 已接受；租户治理、统一浏览器会话、评测与发布切片已有实现，企业访问控制、模型供应链与统一控制台进入设计重定标。 |
| 日期 | 2026-08-02 |
| 业务负责人 | 产品、架构、安全、平台与租户运营负责人。 |
| 建模目的 | 在不削弱令牌派生身份、租户隔离、冻结回答事实和最小披露的前提下，明确 M6 平台治理、租户治理、评测、预算、发布审批与审计导出的所有权。 |
| 首条切片 | 平台管理可信上下文与租户生命周期。 |
| 排除范围 | ADR 接受前的生产代码、迁移、接口和页面；M7 渠道／同意；M8 运维工作台、容量、灾备、生产签署；租户销毁与正文导出。 |

本设计是 `docs/architecture/foundation-domain-design.md` 的 M6 细化设计卡，不创建第二套全平台通用模型。发生冲突时，已接受 ADR、基础领域设计规约和当前工作包优先。

### 设计源登记

| 来源 | 位置 | 状态 | 用途 |
| --- | --- | --- | --- |
| 工作包与路线图 | `project/work-packages/WP-0014.md`、`docs/architecture/foundation-m4-m8-design-map.md` | 可用 | M6 范围、退出条件与前后里程碑边界。 |
| 已接受决策 | ADR-0016、ADR-0023、ADR-0027、ADR-0031、ADR-0033、ADR-0036、ADR-0038、ADR-0040 | 可用 | 可信会话、平台范围、领域边界、冻结模型、最小运行事实。 |
| 影响分析 | `project/evidence/WP-0014-m6-impact-analysis.md` | 可用 | 现有调用方、受保护 seam、两种接口方案与验证合同。 |
| 源码与测试 | organization-access、evaluation-operations、assistant-delivery、trusted-query、platform-security | 可用 | 当前聚合、受认证入口、运营查询和发布状态机事实。 |
| 企业产品基础影响 | `project/evidence/WP-0014-enterprise-product-foundation-impact.md` | 可用 | 账户角色、导航能力、厂商模型供应链、统一 SPA、分页与注意力保护的当前差距和非局部影响。 |
| 外部法规、驻留规则、预算阈值 | 未选择 | 不适用 | 不在本轮猜测法定保留、地区或质量阈值。 |

## 上下文地图

| 上游 | 下游 | 关系 | 模型所有者与翻译责任 | 兼容责任 |
| --- | --- | --- | --- |
| platform-security | organization-access | `open host service` | platform-security 从已验证会话生成 `PlatformRequestContext`；organization-access 只接收该可信上下文与生命周期业务目标。 | 租户 `TrustedRequestContext` 语义和已有 Controller 保持不变。 |
| organization-access | 其他租户上下文 | `published language` | 组织访问发布最小 `TenantLifecycleChanged` 事件；消费者只处理租户标识、状态、版本和发生时间。 | 消费者不得读取组织访问内部表，也不得由事件取得正文访问。 |
| assistant-delivery | evaluation-operations | `published language` | assistant-delivery 后续发布不可变助手版本与草稿测试最小快照；evaluation-operations 翻译为评测输入。 | 测试题目、回答、提示词和引用正文不跨越该契约。 |
| trusted-query | evaluation-operations | `open host service` | trusted-query 提供受控的汇总、反馈、诊断可用性和调用轨迹最小事实；evaluation-operations 不读取 `QueryRun` 持久化实体。 | 普通查询的配额、事件、诊断和正文读取权限不改变。 |
| model-runtime | evaluation-operations | `published language` | model-runtime 后续提供受控成本与能力修订事实；evaluation-operations 只消费冻结标识与数值。 | 不传递凭据、端点、供应商原始错误或可调用模型对象。 |
| evaluation-operations | assistant-delivery | `open host service` | 后续发布决定以版本化、最小 `ReleaseDecision` 公开；assistant-delivery 通过命名领域服务复核。 | 不能从当前运行、当前阈值或页面状态补算历史决定。 |
| organization-access | 统一企业控制台 | `open host service` | organization-access 与 platform-security 提供有效能力和允许工作区的最小投影；前端只翻译为路由、菜单和操作可见性。 | 菜单不能成为授权事实，直接 URL 和 API 继续由服务端失败关闭。 |
| model-runtime | 统一企业控制台 | `open host service` | model-runtime 提供厂商连接、模型探测、部署与配置的分页任务投影；前端只显示无秘密状态与下一动作。 | 不向浏览器返回密钥、原始供应商错误或可调用客户端。 |

平台运营是 M8 的拥有能力，不是 M6 读取权限。它未来只能消费脱敏平台影响摘要，不能以运营角色穿透 trusted-query、knowledge-data 或对象存储的正文读取边界。

## 统一语言

| 术语 | 上下文 | 规范含义 | 不表示什么 | 别名 | 正反例 | 来源 |
| --- | --- | --- | --- | --- | --- | --- |
| 平台请求上下文 | platform-security | 从已验证会话恢复的平台管理身份、凭据、渠道、权限版本、权限集合与服务端追踪标识。 | 可由请求参数指定的目标租户或前端范围状态。 | `PlatformRequestContext` | 正：平台管理员 Bearer 会话；反：`X-Platform-Role` Header。 | ADR-0027、ADR-0033、ADR-0041 提案。 |
| 租户请求上下文 | platform-security | 当前令牌会话恢复的租户、主体、成员、凭据、渠道和权限事实。 | 平台管理会话或可切换的空租户上下文。 | `TrustedRequestContext` | 正：同租户知识读取；反：平台管理员带租户参数。 | ADR-0033。 |
| 租户生命周期 | organization-access | 平台建立、激活、挂起和恢复一个租户治理主体的状态机。 | 租户数据的立即删除、跨租户内容访问或平台运维任务。 | `TenantLifecycle` | 正：`ACTIVE` 租户被挂起；反：删除所有知识正文。 | ADR-0027、ADR-0041 提案。 |
| 初始租户管理员 | organization-access | 创建租户时被显式指定、获得该租户管理职责的既有主体。 | 登录态字段、平台管理员自身的默认复制或未验证邮箱。 | 初始管理员 | 正：由平台管理员在平台范围选定的既有主体；反：HTTP 传入 `membershipId` 伪造当前身份。 | ADR-0027、ADR-0033。 |
| 历史状态目标 | organization-access | 由基础设施将已验证的历史 `tenant.status` 翻译为 `ACTIVE` 或 `SUSPENDED` 的回填目标。 | 可直接写入生命周期根的当前状态，或第二套生命周期状态机。 | 历史状态映射 | 正：`ENABLED` 目标为 `ACTIVE`；反：直接构造 `SUSPENDED` 根。 | ADR-0045。 |
| 生命周期认证切换事实 | organization-access | 标识完整历史回填已原子完成的无敏感单行事实。 | 用户、租户或平台会话参数，或第二个租户状态机。 | 认证切换 | 正：切换后只认 `TenantLifecycle.ACTIVE`；反：永久同时判断旧状态与生命周期。 | ADR-0046。 |
| 初始管理员业务资源目标 | organization-access | 创建租户时被平台操作者显式选择、再由服务端验证的既有 `SubjectIdentity` 标识。 | 当前平台操作者、令牌派生登录态或主体详情读取权限。 | 初始管理员选择 | 正：已启用主体作为目标；反：请求体覆盖当前操作者。 | ADR-0047 提议。 |
| 评测数据集 | evaluation-operations | 版本化的评测问题、期望证据、允许答案、拒答期望和权限场景集合。 | 草稿测试请求或普通查询日志的聚合副本。 | 数据集 | 正：固定版本的批准集；反：从历史正文临时抓取。 | 基础领域设计第 9 节。 |
| 评测任务 | evaluation-operations | 固定助手版本、数据集版本、策略版本、模型路由和指标实现版本的一次可复核运行。 | 当前模型或当前配置的即时演示。 | `EvaluationRun` | 正：被冻结的版本组合；反：只保留一个平均分。 | 基础领域设计第 9 节。 |
| 发布门禁 | evaluation-operations | 对冻结评测结论、预算和安全条件作出通过／拒绝／例外结论的规则。 | `AssistantVersion.publish` 内的页面布尔值。 | 发布策略 | 正：指标失败即拒绝；反：缺少数据时默认通过。 | 基础领域设计第 9 节。 |
| 发布决定 | evaluation-operations | 对一个固定助手版本的不可变通过、拒绝或有期限例外事实。 | 当前版本的可修改状态或模型调用轨迹。 | `ReleaseDecision` | 正：含证据版本和批准人；反：用当前阈值重算历史。 | 基础领域设计第 9 节。 |
| 预算策略 | evaluation-operations | 在明确作用域、周期、币种／计量单位和版本下约束可接受消耗的规则。 | 将单次模型令牌用量直接当成平台预算。 | 预算 | 正：冻结周期限额；反：请求参数中的限额。 | ADR-0027、M6 范围。 |
| 审计导出 | evaluation-operations | 由明确授权、范围和保留策略控制的最小审计事实导出任务。 | 平台运营读取所有租户正文的万能接口。 | 导出任务 | 正：只含允许字段的异步产物；反：查询日志全文下载。 | ADR-0027、ADR-0038。 |

## 场景追溯

| 场景 | 业务结果 | 限界上下文 | 命令／查询／事件 | 前置条件 | 成功／失败／非法结果 | 不变量所有者 | 模型元素 | 外部协作 | 证据 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 平台管理员建立租户 | 创建一个尚未可用的治理主体并指定初始管理员。 | platform-security、organization-access | 建立租户／`TenantProvisioned` | 平台上下文具有租户建立权限；目标租户标识和初始管理员完整。 | 成功进入 `PROVISIONING`；重复标识、缺失管理员、租户令牌或伪造范围拒绝。 | `TenantLifecycle` 与跨根 `TenantProvisioning`。 | 生命周期根、初始管理员值事实、操作结果。 | 会话恢复、组织成员仓储、发件箱。 | ADR-0041 接受后红绿证据。 |
| 激活租户 | 仅在安全基线与初始管理员已完成时使租户可承接租户会话。 | organization-access | 激活租户／`TenantActivated` | `PROVISIONING`、初始化完成、并发版本匹配。 | 成功为 `ACTIVE`；重复激活或未完成初始化拒绝。 | `TenantLifecycle`。 | 生命周期状态、并发版本。 | 最小租户状态事件。 | ADR-0041 接受后红绿证据。 |
| 挂起与恢复租户 | 阻断或恢复新的租户业务操作，不删除数据。 | organization-access | 挂起／恢复租户 | 平台管理员；当前状态分别为 `ACTIVE`／`SUSPENDED`。 | 成功变更并写最小审计；非法迁移失败关闭。 | `TenantLifecycle`。 | 状态机、恢复关联。 | 最小状态事件、会话／授权消费方。 | ADR-0041 接受后红绿证据。 |
| 回填历史租户 | 在不改变既有会话语义的前提下，使历史租户取得可证明的生命周期和初始管理员。 | organization-access | 回填历史租户／最小状态事实 | 完整人工映射、主体与成员均可用、历史状态可识别。 | `ENABLED` 经建立与激活到 `ACTIVE`；`DISABLED` 经建立、激活与挂起到 `SUSPENDED`；任一映射或写入失败整批回滚。 | `TenantLifecycleBackfill`。 | 生命周期根、既有成员根、不可变回填结果。 | 原子持久化与发件箱。 | ADR-0044、ADR-0045。 |
| 租户管理员访问平台入口 | 不获得平台范围或其他租户信息。 | platform-security | 解析平台上下文 | 令牌只有租户角色。 | 失败关闭，无平台上下文、无回退 Header。 | platform-security。 | `PlatformRequestContext`。 | 会话网关。 | 解析器和 HTTP 反例。 |
| 平台运营查看问题正文 | 不因运行权限获得内容读取。 | platform-security、trusted-query、knowledge-data | 内容读取请求 | 只有平台运营权限。 | 失败关闭；平台影响摘要不包含正文。 | 目标资源上下文。 | 最小影响摘要。 | 后续 M8。 | ADR-0027 人工／浏览器验收。 |
| 评测固定版本 | 产生不可由后续配置覆盖的评测结论。 | evaluation-operations | 运行评测／`EvaluationCompleted` | 助手、数据集、策略、路由和指标版本均冻结。 | 成功固化结论；任一缺失或计算失败拒绝通过。 | `EvaluationRun`。 | 数据集、任务、指标结果。 | assistant-delivery、trusted-query、model-runtime 公共门面。 | 后续 M6 ADR。 |
| 依据证据发布 | 只在有效发布决定下发布固定助手版本。 | evaluation-operations、assistant-delivery | 作出发布决定／发布版本 | 冻结评测通过；预算与安全条件满足，或有未过期的批准例外。 | 成功批准；未知、过期、失败或不匹配版本拒绝。 | `ReleaseDecision` 与命名跨根领域服务。 | 发布门禁、决定、例外。 | 版本化公开门面／事件。 | 后续 M6 ADR。 |
| 导出审计 | 在受权范围内生成最小、可过期的导出产物。 | evaluation-operations | 请求导出／`AuditExportCompleted` | 明确导出权限、范围与保留策略。 | 成功生成最小产物；无权限、跨范围、正文列或保留规则缺失拒绝。 | `AuditExport`。 | 导出请求、字段清单、保留策略。 | 安全存储、审计事实源。 | 后续 M6 ADR。 |

## 战略模型

| 子域 | 分类 | 业务目标 | 限界上下文 | 边界理由 |
| --- | --- | --- | --- | --- |
| 平台与租户治理 | 核心 | 用独立平台范围安全地建立、激活、挂起和恢复租户。 | organization-access、platform-security | 生命周期与组织访问状态共同变化；认证上下文仍属于安全平台。 |
| 评测与发布制度 | 核心 | 使版本质量、预算、安全与批准事实可复核并能阻止错误发布。 | evaluation-operations | 数据集、评测、预算、例外和决定具有独立状态、保留与演进压力。 |
| 助手版本意图 | 核心 | 维护助手版本和冻结知识／模型／路由意图。 | assistant-delivery | 发布请求属于助手版本，但评测制度不是版本内部状态。 |
| 可信查询与运行事实 | 核心 | 保持普通查询、反馈、诊断和调用轨迹的最小可审计事实。 | trusted-query、model-runtime | 运行完成语义与治理读取模型不同，不能反向控制正文路径。 |
| 平台运营 | 支撑，M8 | 处理健康、队列、容量、备份、发布与影响摘要。 | 后续平台运营上下文 | 需要运行遥测与恢复，且不得隐含内容读取。 |

## 战术模型与生命周期

### 首条切片：platform-security 与 organization-access

| 聚合根／领域服务 | 成员或输入 | 身份 | 受保护不变量 | 一致性边界 | 外部引用与协作 |
| --- | --- | --- | --- | --- | --- |
| `TenantLifecycle` | `TenantLifecycleStatus`、初始管理员引用、版本、创建与变更审计事实。 | `TenantId`。 | 同一租户仅一个生命周期根；只允许 `PROVISIONING → ACTIVE → SUSPENDED → ACTIVE`；挂起不等于删除。 | 单一租户生命周期事务与发件箱。 | 只发布最小状态事件；其他上下文只以租户标识和状态消费。 |
| `TenantProvisioning` | 已加载的 `TenantLifecycle`、`InitialTenantAdministrator`、操作时间。 | 无身份、无状态。 | 建立和初始化管理员的跨根业务规则不能由应用层拼接。 | 返回不可变 `TenantProvisioningResult`，由应用层在一个事务中提交。 | 不依赖 Spring、数据库、HTTP 或会话。 |
| `TenantLifecycleBackfill` | 已验证历史租户事实、主体、既有成员资格、目标状态和回填时间。 | 无身份、无状态。 | 历史状态只决定目标；必须由 `PROVISIONING` 经合法迁移达到目标，且成员归属、主体和成员启用状态均可证明。 | 返回不可变 `TenantLifecycleBackfillResult`，由应用层在一个事务中提交根、成员资格与最小状态事实。 | 不依赖 Spring、数据库、HTTP、会话或原始历史状态字符串。 |
| `PlatformRequestContext` | 主体、凭据、渠道、权限版本、权限集合、追踪标识。 | 不作为聚合。 | 仅能由已验证平台会话恢复；不能携带租户、成员、目标资源或调用方追踪标识。 | 单次 HTTP 请求。 | 仅由平台安全适配器创建，Controller 注入后传给应用层。 |

`TenantLifecycle` 不持有知识、助手、模型配置、成员对象图、正文或无限历史。初始管理员只能保留稳定主体／成员标识；成员写入通过 `TenantProvisioning` 的操作结果协调。未定义租户销毁状态，直到保留与删除 ADR 接受。

| 当前状态 | 命令 | 前置条件／守卫 | 不变量 | 下一状态 | 事件 | 非法原因 |
| --- | --- | --- | --- | --- | --- |
| 不存在 | 建立租户 | 平台上下文具有治理权限；标识唯一；初始管理员完整。 | 建立后还不能承接租户业务。 | `PROVISIONING` | `TenantProvisioned` | 租户请求、重复标识或缺失管理员。 |
| `PROVISIONING` | 激活租户 | 初始管理员和安全基线完成；版本匹配。 | 只能一次激活，不能绕过初始化。 | `ACTIVE` | `TenantActivated` | 未完成初始化、重复激活或过期版本。 |
| `ACTIVE` | 挂起租户 | 平台上下文具有治理权限；版本匹配。 | 不删除业务数据；新租户业务请求必须失败关闭。 | `SUSPENDED` | `TenantSuspended` | 重复挂起或平台运营角色。 |
| `SUSPENDED` | 恢复租户 | 平台上下文具有治理权限；恢复前置条件仍满足。 | 原租户标识和审计链保持。 | `ACTIVE` | `TenantRestored` | 非挂起状态、过期版本或无权限。 |

### 后续 M6 切片：evaluation-operations

| 聚合根／领域服务 | 成员或输入 | 身份 | 受保护不变量 | 一致性边界 | 外部引用与协作 |
| --- | --- | --- | --- | --- | --- |
| `EvaluationDataset` | 不可变清单、私有正文引用、租户、内容摘要、条目数、`SYNTHETIC` 分类和保留策略标识。 | `EvaluationDatasetId`。 | 运行只能引用已发布的同租户数据集；题目、期望证据和答案正文不进入事件、日志、普通表或读取投影。 | 数据集版本事务。 | 只向评测任务公开版本、摘要和私有内容能力。 |
| `EvaluationRun` | 目标助手版本及修订、数据集版本及摘要、政策与指标实现版本、计数、指标和终态。 | `EvaluationRunId`。 | 被测版本组合不可变；空样本、跨租户、错误引用、错误拒答、执行失败或不可计算指标均不能通过。 | 单次评测终态与发件箱。 | 只消费公开门面，不读取其他上下文表。 |
| `ReleaseDecision` | 目标助手版本及修订、通过评测运行引用、通过或拒绝结论与完成时间。 | `ReleaseDecisionId`。 | 决定必须绑定完全相同的草稿修订和通过运行；未定义例外时只能拒绝。 | 决定事务。 | 向 assistant-delivery 公开只读可验证结论。 |
| `ReleaseAuthorization` | 已加载的目标版本、对应 `ReleaseDecision` 和运行事实。 | 无身份、无状态领域服务。 | 不能以当前策略或当前运行替代历史决定；不匹配或拒绝结论不能发布。 | 返回不可变授权结果。 | assistant-delivery 负责最终版本状态改变。 |
| `BudgetPolicy` | 作用域、周期、计量、版本与限额。 | 值对象／策略根取决于后续 ADR。 | 未定义币种、计量、分配与超额恢复前不得假装为发布门禁。 | 待 ADR 决定。 | 只提供版本化结论，不向模型调用路径注入阈值。 |

后续评测与发布切片不得把 `EvaluationRun`、`ReleaseDecision` 或 `BudgetPolicy` 伪装为 `AssistantVersion` 子实体；它们可独立定位、审计、保留、并发控制和撤销，必须为独立聚合根或明确的值对象快照。

## 选定 seam、失败流与迁移切片

| seam | 选定接口 | 数据流与失败流 | 迁移切片 |
| --- | --- | --- | --- |
| 平台认证 | 平台路由专用的 `PlatformRequestContext` 参数解析器。 | Bearer 令牌 → 已验证平台会话 → 平台上下文；缺令牌、租户会话、权限不足和伪造字段均在资源读取前失败。 | ADR-0041 首条切片。 |
| 租户治理 | `TenantLifecycleRepository`、`TenantProvisioning` 与 `TenantLifecycleBackfill`。 | 平台应用服务或受控部署加载根与初始管理员引用 → 领域服务返回操作结果 → 基础设施原子保存根、成员结果和发件箱。 | ADR-0041、ADR-0044、ADR-0045 首条切片。 |
| 评测输入 | evaluation-operations 的私有数据集内容端口和受控助手版本门面。 | 私有正文在受控执行时读取，运行只保存摘要、计数和结论；读取失败或版本不完整即拒绝启动。 | ADR-0050、ADR-0051。 |
| 发布复核 | `ReleaseAuthorization`。 | 已加载版本＋已验证决定与运行 → 不可变授权结果；任一不匹配或拒绝不调用发布状态迁移。 | ADR-0050、ADR-0051。 |
| 平台运营摘要 | 独立最小影响摘要门面。 | 已脱敏运行状态 → 摘要；内容字段请求直接拒绝。 | M8，不在 M6 首条切片。 |

方案选择遵循小接口原则：平台请求上下文只服务真实的平台路由调用方；评测和发布的两个独立 seam 各自隐藏运行事实、阈值演进和批准细节，不提前创建无调用方的“治理总线”。

## 实现对照

| 领域概念 | 代码／API／数据／文档指针 | 状态 | 差异与处理 |
| --- | --- | --- | --- |
| 租户请求上下文 | `TrustedRequestContext`、`SaTokenTrustedContextArgumentResolver` | 已实施 | 仅服务租户范围，禁止扩展为双范围可空对象。 |
| 平台请求上下文 | `PlatformRequestContext`、`SaTokenPlatformContextArgumentResolver` | 已实施 | 只由独立平台 Bearer 会话恢复；不含租户、成员或目标资源。 |
| 租户生命周期根与建立结果 | `TenantLifecycle`、`TenantLifecycleBackfill`、`TenantLifecycleBackfillResult`、`TenantProvisioning`、`TenantProvisioningResult`、`TenantLifecycleTransition`、平台 HTTP 路由 | 部分实施 | 历史回填、认证切换、新租户原子建立、状态迁移、最小发件箱、自动配置和受限 Bearer HTTP 已完成；控制台治理页面及浏览器角色任务仍待实现。 |
| 同租户运营读取 | `OperationsQueryService` | 已实施 | 保持租户汇总与审计投影读取，不增加平台正文权限。 |
| 草稿测试、反馈与轨迹 | `AssistantTestRun`、`QueryFeedback`、`ModelInvocationTrace` | 已实施 | 只提供后续治理的最小受控读取输入。 |
| 私有评测、发布决定与授权 | `EvaluationDataset`、`EvaluationRun`、`ReleaseDecision`、`ReleaseAuthorization` | 设计可实施，未实现 | ADR-0050、ADR-0051 已冻结所有权、合成数据与质量门禁；仍须按红绿路线补齐存储、执行、授权和契约。 |
| 预算与导出 | 无 | 未设计为可实施切片 | 仍需要独立 ADR 确定计量、作用域、保留和例外授权。 |

## 决策与未知项

- ADR-0041 已接受独立 `PlatformRequestContext`，并把可恢复租户生命周期作为首条纵向切片；它不授权租户正文读取、评测门禁或平台运营工作台。
- ADR-0042 已选择全局主体与凭据聚合：当前 `subject_id` 迁移为可独立验证的 `SubjectIdentity`，成员资格只引用主体。不得复制密码哈希或暗自引入邀请／密码重置流程。
- ADR-0044 要求历史初始管理员只来自人工确认的 `tenantId → SubjectId` 映射；ADR-0045 要求历史状态只经合法生命周期迁移达到最终目标，绝不直接写入根状态。
- ADR-0046 已接受：完整历史回填在同一事务激活认证切换；切换后租户认证只能读取 `TenantLifecycle.ACTIVE`，旧 `tenant.status` 不再参与认证。
- ADR-0047 已接受：新租户必须选择既有初始管理员；该选择是业务资源目标而非登录态，当前平台操作者仍只从令牌恢复。
- ADR-0048 已接受：统一浏览器入口和跨页签连续性必须由服务端签发的双范围 HttpOnly 会话实现；前端能力清单不作为授权事实。实现仍须先保留实际红灯并完成浏览器角色回归。
- ADR-0050 与 ADR-0051 已冻结 `SYNTHETIC` 私有数据集、`Recall@10`、引用正确率、正确拒答率、失败关闭和无例外的发布决定。非合成数据、预算计量、例外批准、审计导出字段白名单和保留期限仍是未决策项；须先以独立 ADR 接受。
- 平台管理员对租户生命周期的目标 `TenantId` 是业务资源目标，不是登录状态；它只能在已验证平台上下文内加载和授权，不能由其取得该租户正文、成员详情或普通查询结果。
- 每个后续切片都必须先补领域设计卡、反例、受权 HTTP 和实际角色浏览器任务；M6 自动与人工评审通过前不得激活 M7。

## ADR-0051 实施设计卡

| 项目 | 设计结论 |
| --- | --- |
| 语言与所有权 | `EvaluationDataset`、`EvaluationRun`、`ReleaseDecision` 与 `ReleaseAuthorization` 均由 evaluation-operations 拥有。上游为 assistant-delivery 的受控版本读取门面；下游为 assistant-delivery 的发布授权端口。明确排除 `AssistantTestRun`、`QueryRun`、反馈与调用轨迹作为数据集来源，也不把评测正文交给浏览器或运营读取。 |
| 聚合 | 数据集根的本地事务只冻结清单和私有内容引用；运行根从 `RUNNING` 终态为 `PASSED` 或 `FAILED`，并发版本只保护自身；决定根只引用同租户、目标草稿修订完全匹配的通过运行。三个根都只引用其他根的稳定标识和冻结修订。 |
| 协作 | `EvaluationExecution` 领域服务接收不含正文的执行观察值并返回不可变终态；私有内容端口只在应用层受控调用。`ReleaseAuthorization` 接收冻结决定与目标版本标识／修订，返回不可变授权结果。发布授权端口版本化、幂等读取；运行和决定事件只含标识、摘要、计数、结论与时间。 |
| 安全 | 运行启动前先校验租户、数据集发布状态与目标版本；跨租户、空样本、未知引用、超过十个候选、空回答引用、错误拒答、缺失或不可计算指标均失败关闭。正文、回答、候选、凭据、令牌和端点不得进入根、事件、错误响应、日志或普通查询表。 |
| 持久化 | PostgreSQL 保存三个根及其并发版本；私有内容放在 evaluation-operations 管理的私有对象存储，数据库只保存内容引用与摘要。根、最小事件和操作结果同事务提交；内容写入失败或事务失败都不得留下可发布数据集或通过运行。 |
| 证据 | 先证明纯领域反例，再覆盖应用编排、真实 PostgreSQL 原子性和租户隔离、自动配置、令牌派生 HTTP 以及无正文的角色浏览器任务。真实部署 HTTPS Cookie、跨页签注销和自然到期仍依 ADR-0048 单独验收。 |

## ADR-0057 首条实施设计卡：导航能力投影

### 目标、边界与场景

本切片只建立已验证会话到导航能力码的稳定公开 seam。它不把现有直接成员授权误称为已完成的
RBAC，也不提前创建没有真实写入任务的角色聚合、权限目录或账户管理接口。

| 场景 | 结果 | 所有者 | 成功与失败边界 |
| --- | --- | --- | --- |
| 已登录成员进入控制台 | 浏览器取得当前租户会话实际权限的最小能力码，控制台仅显示满足该能力的入口。 | `organization-access` 提供投影；`console-web` 翻译为导航。 | 无租户会话时不返回租户能力；能力码不含主体、成员、凭据、token、租户标识或完整策略。 |
| 已登录平台管理员进入控制台 | 浏览器取得当前平台会话实际权限的最小能力码，并只在已有治理权限时显示平台治理入口。 | `organization-access` 读取 `PlatformRequestContext`。 | 平台会话不能产生租户能力；租户会话不能产生平台能力；伪造浏览器能力、菜单或 URL 不改变任何服务端授权。 |
| 角色或授权将来变更 | 新会话从组织访问的授权事实得到新的能力码；旧会话撤销与权限版本失效由后续角色绑定写入切片负责。 | 角色、权限目录与绑定仍属于 `organization-access`。 | 本切片不新增写入路径，因而不承诺尚不存在的撤销流程。 |

### 战术模型、协作与不变量

| 模型元素 | 归属 | 责任与不变量 |
| --- | --- | --- |
| `TrustedRequestContext`、`PlatformRequestContext` | platform-security | 只由已验证的不同范围会话恢复；分别提供当前有效 `PermissionCode` 集合。 |
| `BrowserSessionCapability` | organization-access 应用层 DTO | 不可变最小读取投影；工作区、显示角色和能力码均为集合快照，能力码必须来自已验证上下文，且会话期限为正数。 |
| `BrowserSessionCapabilityService` | organization-access 应用服务 | 只编排两个会话端口，合并两种已验证范围的能力；不读取数据库、不授予权限、不处理角色写入。 |
| 路由需求表 | console-web | 每个已发布导航入口声明已有后端授权点所需能力；前端取交集隐藏入口，不能替代 Controller 或应用服务的授权判断。 |

所选 seam 是在既有 `GET /api/v1/auth/browser-session` 上增加 `capabilityCodes`。它有现成的双范围会话
调用方，且不泄漏角色绑定、成员详情或策略结构。相比同时改写 `tenant_membership`、
`membership_permission`、登录和所有受权接口，该 seam 将变化局部化，并保留后续以角色绑定替换直接成员授权的空间。

### 反例与验证合同

| 反例 | 预期 |
| --- | --- |
| 租户会话不存在或失效 | 能力码不得包含任何租户权限。 |
| 平台会话不存在或失效 | 能力码不得包含任何平台权限或治理工作区。 |
| 租户与平台会话同时有效 | 只合并两者的实际 `PermissionCode`；不生成额外权限。 |
| 响应缺失或畸形能力码 | 前端解析失败关闭，不把会话视为可用。 |
| 浏览器伪造能力码或直达 URL | 不改变服务端从令牌恢复的授权结果。 |

本切片先保留 `BrowserSessionCapabilityServiceTest` 与浏览器会话客户端解析红绿证据；随后补充
HTTP 契约和控制台导航回归。真实 PostgreSQL 角色绑定、会话撤销、管理员账户与自定义角色管理由
ADR-0057 的后续单一写入切片实现，不能以本投影替代。
