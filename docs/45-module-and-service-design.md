# 45 模块与服务详细设计

Stage: module-design  
Category: stage-main  
Status: current  
Review Status: agent-reviewed  
Reviewers: architect, engineering lead  
Approval: agent-approved  
Inputs: `docs/40-architecture-and-tech-spec.md`  
Outputs: 模块职责、实体、接口、事件和验证点  
Depends On: `docs/40-architecture-and-tech-spec.md`  
Load When: planning/implementation  
Next: `docs/50-delivery-plan.md`

## 设计原则

- 控制面先于体验层。
- 本文件是模块设计主文档和共性规则，不承载所有后端代码组织细节；后端模块开发细则见 `docs/45-details/01-backend-code-organization.md`。
- 前端控制台设计系统见 `docs/45-details/03-frontend-console-design-system.md`；任何前端页面或样式调整都必须先复用该系统，不得以临时 smoke 页面作为企业级完成态。
- 启用 TDD + DDD：每个行为先有失败测试，domain 先表达不变量，再落 application / infrastructure。
- 租户、权限、审计、trace 是所有模块的横切不变量。
- 后台业务必须先判定归属：平台管理功能归平台菜单和平台权限，租户平台功能归租户菜单和租户权限；接口、页面、菜单和权限缺一不可。
- provider、vector store、parser、tool executor 必须通过 port / adapter 扩展；AI 能力通过 Spring AI adapter 接入。
- 持久化实现使用 MyBatis-Plus，但 domain 不依赖 MyBatis-Plus、PO、Mapper。
- 登录、会话和权限使用 Sa-Token + Redis-backed session。
- 高频只读配置和目录类数据使用 Redisson + Caffeine 二级缓存，统一通过 cache infrastructure，不能各模块自建缓存。
- 高风险工具和数据库知识源默认保守。
- 框架集成细节以 `docs/facts/framework-official-research.md` 和 `docs/45-details/01-backend-code-organization.md` 为事实源；本文件只保留模块边界和验证点。

## 统一工程规范

### 注释规范

- domain 层注释解释业务不变量、状态流转原因、边界和风险。
- application 层注释解释事务边界、幂等、权限和跨 context 协作。
- infrastructure 层注释解释外部协议、供应商差异、重试和降级原因。
- 禁止给显而易见的 getter、setter、简单赋值和 CRUD 流程添加流水账注释。

### 统一异常规范

- `DomainException`：领域不变量或业务状态不允许。
- `ApplicationException`：用例编排、幂等、权限或状态转换失败。
- `InfrastructureException`：provider、数据库、Redis、对象存储、向量库或企业系统失败。
- `ErrorCode` 必须稳定、可搜索、可映射 HTTP 状态。
- controller 只做异常翻译，不吞异常、不拼接供应商敏感信息。

### 统一响应规范

- 所有 HTTP API 返回 `ApiResponse<T>`。
- 成功响应 `code=0`，失败响应使用稳定错误码。
- 所有响应必须包含 `traceId`。
- 分页响应使用统一 `PageResponse<T>`，不得直接返回 MyBatis-Plus 分页对象。

### 前端控制台规范

- 页面必须先判定属于平台管理、租户平台或运行时入口，并绑定对应菜单、权限、API contract 和后端保护。
- 控制台使用统一 token、布局、表单、表格、按钮、消息和状态规则；禁止页面内临时堆叠卡片或营销型视觉。
- 表单保存、连接测试、权限拒绝、provider 失败和 S5.1 调用失败必须在操作区域附近展示中文诊断信息。
- readonly 的平台目录字段、API Key last4、权限禁用按钮和跨租户 403 必须有明确视觉状态。
- 前端完成态必须包含 `pnpm --dir frontend/console-web test`、`pnpm --dir frontend/console-web build` 和真实页面 smoke 证据。

### 框架落点共性规范

- Spring AI：`ChatClient`、Advisor、`ToolCallback`、`VectorStore` 只出现在 infrastructure adapter 或 boot configuration，模块间通过 port 交互。
- Sa-Token：`StpUtil` 只出现在 IAM/auth infrastructure；其他模块通过 `CurrentPrincipalPort`、`PermissionChecker` 或 application service 获取主体和权限。
- MyBatis-Plus：`Mapper`、PO、Wrapper、`IPage` 只出现在 `infrastructure/persistence`；query side 返回平台 DTO，不返回 MyBatis-Plus 类型。
- Redisson + Caffeine：二级缓存通过统一 cache port/facade 使用，Caffeine 只做 L1，Redisson/Redis 做 L2 和失效广播。
- 多租户：所有 module 的查询和写入必须由 tenant context 驱动；MyBatis-Plus tenant plugin 是底线，业务权限仍需在 application 层显式校验。

## DDD 包结构模板

```text
<bounded-context>/
  interfaces/
    controller/
    request/
    response/
    assembler/
  application/
    command/
    query/
    service/
    event/
  domain/
    aggregate/
    entity/
    valueobject/
    service/
    repository/
    event/
  infrastructure/
    persistence/
      mapper/
      po/
      repository/
    ai/
    satoken/
    remote/
    messaging/
    scheduler/
    cache/
```

依赖方向：

- `interfaces` 调用 `application`。
- `application` 调用 `domain` 和端口接口。
- `infrastructure` 实现 `domain` / `application` 定义的端口。
- `domain` 不依赖 Spring、Sa-Token、Spring AI、MyBatis-Plus 或 HTTP 类型。

## Tenant & IAM Module

职责：

- 租户、用户、组织、角色、权限、菜单、资源 ACL。
- 平台管理员和租户管理员分层。
- API key / app key 的创建、禁用、轮换和审计。

核心对象：

- `Tenant`
- `User`
- `Role`
- `Permission`
- `Menu`
- `ResourceAcl`
- `ApiCredential`
- `AuthPrincipal`
- `ActorType`
- `TenantSession`

关键接口：

- 创建租户、更新租户、启停租户、配置配额；创建租户必须同事务初始化管理员、默认角色、默认权限、默认菜单和审计。
- 启用、禁用租户账户，并让禁用状态影响登录和租户资源访问。
- 用户加入租户、分配角色、分配角色权限和角色菜单。
- 角色、权限点、菜单的创建、更新、启用、禁用和审计。
- 菜单目录维护 `menuCode`、`parentCode`、`routePath`、`componentKey`、`platformScope`、`sortOrder` 和状态；菜单只控制前端导航可见性，不能替代接口权限。
- 权限目录维护可授权 permission code，平台管理和租户平台写接口必须能追溯到权限目录。
- 校验资源访问。
- 登录、登出、token 续期、当前主体解析。
- 通过 Sa-Token permission bridge 查询角色和权限。
- 当前用户接口返回角色、权限和菜单，作为前端控制台唯一导航事实源；前端本地只维护 `componentKey -> 页面组件` 注册表。

验证点：

- 任意业务查询必须有 tenant scope。
- 平台管理员跨租户操作必须写审计。
- 禁止普通租户用户访问其他租户资源。
- Sa-Token login id 只携带雪花 Long 型 `tenantId`、`actorType`、雪花 Long 型 `actorId`，不携带角色、权限、租户名称、租户编码或用户名快照。
- Redis session 失效后 token 不得继续访问受保护 API。
- 菜单可见性不能替代接口权限；所有平台管理和租户管理写接口仍必须使用 Sa-Token 权限注解或等价 application permission check。
- 平台管理端首期必须形成租户、角色、权限、菜单完整闭环；不能把 `GET /api/admin/roles` + 绑定接口误认为完整 IAM 管理完成态。

## Provider & Model Gateway Module

职责：

- 统一平台模型选项、租户模型供应商配置、租户模型目录、密钥、调用、路由、限流、成本和错误归一。
- 平台管理员维护 provider type、baseUrl、模型型号、能力类型和默认参数选项；租户只能基于平台选项保存自己的 provider credential 和模型启用配置。

核心对象：

- `Provider`
- `PlatformProviderType`
- `PlatformModelOption`
- `ProviderCredential`
- `ModelDefinition`
- `ModelCapability`
- `RoutingPolicy`
- `ModelInvocation`
- `ModelCostRecord`
- `SpringAiModelAdapter`

关键接口：

- provider CRUD 和连接测试。
- 平台模型选项查询，供租户模型配置页面下拉选择。
- model catalog 同步与手工维护。
- chat / embedding / rerank / image / speech 等统一调用 port。
- Spring AI adapter 负责对接 provider 能力差异，业务模块只依赖模型端口。新增厂商必须通过 `AiProviderType` + `AiGatewayClientFactory` + `AiGatewayClient` 扩展，不得把厂商分支写进 facade 或 application service。

失败语义：

- provider 不可用：返回可诊断错误并记录 provider health。
- 限流：返回租户或 provider 级 throttle。
- 模型不支持工具调用或多模态：在 gateway 层拒绝，而不是运行时失败。
- Spring AI 调用失败必须归一为稳定错误码和 provider health 信号。

## Knowledge Management Module

职责：

- 管理知识库、知识源、文档、chunk、metadata、ACL、索引任务和同步策略。

核心对象：

- `KnowledgeBase`
- `KnowledgeSource`
- `Document`
- `DocumentVersion`
- `KnowledgeChunk`
- `IngestionJob`
- `IndexJob`
- `DatabaseKnowledgeSource`
- `SemanticEntity`
- `ParserAdapter`
- `EmbeddingIndexTask`

关键接口：

- 创建知识库。
- 添加文档源、网页源、对象存储源、数据库源。
- 触发同步、重建索引、停用文档。
- 查询 ingestion 状态和质量报告。
- 由 Java async job 执行解析、切分、embedding 和索引，不引入 Python worker。

数据库知识源边界：

- 只能读。
- 必须配置 schema / table / column 白名单。
- 必须配置业务语义描述。
- SQL 由 query guard 校验后执行。
- 查询结果进入上下文前要脱敏和限行。

## Retrieval Runtime Module

职责：

- 执行知识检索策略，生成可解释、可审计、可评估的上下文。

核心对象：

- `RetrievalStrategy`
- `RetrievalRequest`
- `RetrievalTrace`
- `ChunkHit`
- `Citation`
- `RerankResult`
- `SpringAiEmbeddingPort`
- `RerankerPort`

关键流程：

1. 解析用户问题和应用配置。
2. 应用 tenant、knowledge base、document、metadata ACL。
3. 执行 keyword / vector / hybrid search。
4. 通过 Spring AI adapter 或自有 port 执行 embedding / rerank 和 score threshold。
5. 返回 context package 和 citation。

验证点：

- 检索结果必须可追溯到知识库、文档、chunk。
- 未命中问题必须进入后续知识优化数据集。

## App & Conversation Runtime Module

职责：

- 管理 AI 应用、应用版本、prompt、参数、发布、会话、消息和反馈。

核心对象：

- `AiApp`
- `AppVersion`
- `PromptTemplate`
- `Conversation`
- `Message`
- `Feedback`
- `AppPublication`
- `ToolCallPlan`

关键流程：

1. 解析 app version。
2. 校验用户访问。
3. 调用 Retrieval Runtime 获取上下文。
4. 调用 Model Gateway。
5. 处理 tool call。
6. 返回回答、引用和 trace id。

TDD 起点：

- 应先写 “无权限用户访问应用返回统一权限错误” 的 interface test。
- 再写 “有知识库命中时回答包含 citation” 的 application test。
- 再写 “模型 provider 失败时返回可诊断错误且记录 trace” 的 infrastructure/application test。

## Tool Runtime Module

职责：

- 管理工具目录、schema、凭证、执行策略、审批和审计。

核心对象：

- `Tool`
- `ToolSchema`
- `ToolCredential`
- `ToolPolicy`
- `ToolRun`
- `ToolApproval`

工具类型：

- HTTP / OpenAPI。
- MCP server。
- 数据库只读查询。
- 内部系统动作。

风险等级：

- L0：纯读，无敏感数据。
- L1：读敏感数据。
- L2：写入但可回滚。
- L3：高影响写入或外部动作，必须审批。

验证点：

- 模型不能直接拿到工具凭证。
- tool run 必须有输入输出快照、执行人、应用、租户和 trace id。
- L3 工具在未审批前只能生成 pending run，不得执行外部动作。

## Observability & Evaluation Module

职责：

- 汇总模型、检索、工具、应用、知识库、用户和租户维度的 AI telemetry。

核心对象：

- `AiTrace`
- `InvocationMetric`
- `UsageRollup`
- `EvalDataset`
- `EvalCase`
- `EvalRun`
- `QualityScore`
- `AlertRule`

监控维度：

- 模型调用次数、token、成本、延迟、错误。
- 知识库和文档命中次数。
- chunk 命中、低分检索、无答案。
- tool 调用、失败、审批、风险动作。
- 用户反馈和人工评分。

## Governance & Billing Module

职责：

- 管理配额、预算、成本分摊、日志保留、导出和合规策略。

核心对象：

- `QuotaPolicy`
- `Budget`
- `CostAllocation`
- `RetentionPolicy`
- `ComplianceExport`
- `AuditRecord`

验证点：

- 租户超过预算或配额后进入限流或审批。
- 关键日志保留策略可配置。
- 审计日志是合规事实源，由 Governance & Billing 拥有；其他模块只能通过 audit port 写入审计事件。

## Cache Infrastructure

职责：

- 提供 Caffeine L1 + Redisson/Redis L2 的统一二级缓存能力。
- 管理 cache spec、key builder、serializer、失效事件、热点保护和 metrics。
- 为 IAM、Provider、Knowledge、Tool、Governance 等模块提供 cache port，不承载业务事实源。

核心对象：

- `CacheName`
- `CacheSpec`
- `CacheKey`
- `CacheValueEnvelope`
- `CacheInvalidationEvent`
- `TwoLevelCacheFacade`
- `CacheMetricsRecorder`

关键规则：

- L1 TTL 必须短于 L2 TTL。
- 写操作必须在事务提交后失效缓存。
- 跨节点 L1 失效必须由 Redisson topic 或等价分布式事件完成。
- 缓存 key 必须带 tenant namespace 和 schema version。
- 鉴权、租户状态、限流、工具策略 Redis 不可用时 fail closed。

默认接入模块：

- Tenant & IAM：`tenant-policy`、`iam-permission`。
- Provider & Model Gateway：`provider-config`、`model-catalog`。
- Knowledge Management / Retrieval：`knowledge-metadata`、`retrieval-strategy`。
- Tool Runtime：`tool-schema`。
- Governance & Billing：`rate-limit-policy`。

## Cross-Module Events

| Event | 发布者 | 消费者 | 语义 |
| --- | --- | --- | --- |
| `ProviderCredentialUpdated` | Provider | Governance Audit Ledger / Gateway cache | 刷新 provider 状态 |
| `CacheInvalidated` | Cache Infrastructure | All modules | 删除本地 L1 或记录失效结果 |
| `DocumentUploaded` | Knowledge | Java ingestion job | 启动文档解析 |
| `ChunkIndexed` | Java ingestion job | Observability | 记录索引结果 |
| `ConversationCompleted` | App Runtime | Observability / Eval | 统计会话质量 |
| `ToolRunCompleted` | Tool Runtime | Observability / Governance Audit Ledger | 记录工具执行 |
| `QuotaExceeded` | Governance | Gateway / App Runtime | 限流或阻断 |

## Aggregate Catalog

| Aggregate | Context | 关键不变量 | Repository Port | MyBatis-Plus 落点 |
| --- | --- | --- | --- | --- |
| `Tenant` | Tenant & IAM | disabled tenant 不允许新建资源或登录 | `TenantRepository` | `TenantMapper`, `TenantPO` |
| `UserAccount` | Tenant & IAM | 用户必须属于至少一个有效租户才能登录 | `UserAccountRepository` | `UserAccountMapper`, `UserAccountPO` |
| `ProviderConfig` | Provider | enabled provider 必须有加密 API Key、有效能力类型和至少一个 model；`o3` 不得作为 embedding | `ModelProviderRepository` | `ModelProviderMapper`, `ModelProviderPO` |
| `KnowledgeBase` | Knowledge | 未完成索引的文档不能参与正式检索 | `KnowledgeBaseRepository` | `KnowledgeBaseMapper`, `KnowledgeBasePO` |
| `AiApp` | App Runtime | published app 只能引用 enabled model 和可访问知识库 | `AiAppRepository` | `AiAppMapper`, `AiAppPO` |
| `ToolDefinition` | Tool Runtime | L3 tool 必须绑定审批策略 | `ToolRepository` | `ToolMapper`, `ToolPO` |
| `EvalDataset` | Observability | eval run 必须绑定固定 app version / model config | `EvalRepository` | `EvalDatasetMapper`, `EvalDatasetPO` |
| `AuditRecord` | Governance | 审计记录不可变更，只能追加和按保留策略归档 | `AuditRepository` | `AuditRecordMapper`, `AuditRecordPO` |

## Transaction Boundary

| 用例 | Application Service | 事务边界 | 事件 / 补偿 |
| --- | --- | --- | --- |
| 创建租户 | `TenantCommandService` | tenant、默认角色、审计同事务 | `TenantCreated` |
| 登录 | `AuthApplicationService` | 用户状态校验和审计同事务；Sa-Token session 写 Redis | 登录失败只写审计 |
| 保存 provider | `ProviderCommandService` | provider metadata 与 credential ref 同事务 | `ProviderCredentialUpdated` |
| 上传文档 | `KnowledgeCommandService` | document metadata 与 job 同事务 | `DocumentUploaded` |
| 发布应用 | `AppPublishService` | app version 与 publication 同事务 | `AppPublished` |
| 执行工具 | `ToolExecutionService` | tool_run、审计和审批状态同事务 | `ToolRunCompleted` 或 `ToolApprovalRequested` |

## Testing Baseline

- 每个 aggregate 至少有 domain test 覆盖不变量。
- 每个 command service 至少有 application test 覆盖权限、事务和事件。
- MyBatis-Plus repository 必须有 mapper / repository integration test。
- Sa-Token 登录、登出、当前用户、权限校验必须有 integration test，Redis session 不可用时必须失败明确。
- Spring AI adapter 必须有 contract test，验证 provider capability 不匹配、超时、限流和错误归一。
- controller 必须有统一响应和统一异常测试。

## Detail Review 结论

`agent-approved / pass-for-s4`。模块职责、边界、事件、审计归属和基础设施接入规则已通过 agent 复审；S3 鉴权纵切与 S4 Provider Catalog 已形成真实运行证据。进入后续具体编码切片时仍需在 `docs/60-implementation-notes.md` 补齐该切片的包名、表结构、DTO、错误码表和首批测试清单。
