# 32 控制面与门禁

Stage: harness-control  
Category: stage-main  
Status: current  
Review Status: agent-reviewed  
Reviewers: architect, engineering lead  
Approval: agent-approved  
Inputs: `docs/30-project-harness.md`, `docs/31-context-loading.md`  
Outputs: gate、规则、脚本入口、manual gate  
Depends On: `docs/30-project-harness.md`, `docs/31-context-loading.md`  
Load When: before-implementation/quality  
Next: 先完成前端控制台企业级设计系统加固，再启动 S5.1 统一模型调用、trace 和 usage 基线

## 已建立控制面

| 控制点 | 载体 | 状态 |
| --- | --- | --- |
| 文档入口 | `README.md`, `AGENTS.md`, `docs/00-index.md` | current |
| 文档工程守卫 | `docs/01-document-system.md`, `scripts/harness_doc_guard.py` | current |
| 阶段文档顺序 | `docs/00-index.md` | draft |
| 仓库预检 | `scripts/harness_repo_preflight.py` | current |
| context pack | `scripts/generate_context_pack.py` | current |
| gate verdict | `docs/evidence/gate-verdicts.md` | draft |
| 偏差日志 | `docs/evidence/deviation-log.md` | draft |
| 团队拓扑 | `docs/33-team-topology.md` | current |
| 架构 profile | `springboot-enterprise-ddd` in `docs/40/45/50` | draft |
| 研发环境 | `deploy/docker-compose.yml`, `deploy/scripts/*` | current |
| 后端组织规范 | `docs/45-details/01-backend-code-organization.md` | current |
| DDD 领域内聚守卫 | `scripts/harness_ddd_guard.py`, `docs/evidence/ddd-architecture-drift-review-2026-05-11.md` | current-required |
| 前端控制台设计系统 | `docs/45-details/03-frontend-console-design-system.md`, `frontend/console-web/src/styles.css` | current |
| 后端 Maven 基线 | `backend/pom.xml`, `backend/infrastructure/ai-platform-bom`, `BackendMavenBaselineTest` | current |
| S3 可运行纵切 | `backend/apps/ai-platform-boot`, `frontend/console-web`, `docs/evidence/s3-auth-slice-evidence-2026-05-10.md` | current |
| S4 Provider Catalog | `backend/business/ai-platform-modules/model`, `frontend/console-web`, `docs/evidence/s4-provider-catalog-evidence-2026-05-10.md` | current |
| S4.4 IAM Admin Closure | `backend/business/ai-platform-modules/tenant`, `frontend/console-web`, `docs/facts/iam-rbac-official-research.md` | current |

## Gate 定义

| Gate | 当前结论 | 进入下一阶段条件 |
| --- | --- | --- |
| Route Ready | pass | 当前项目态、目标和事实源明确 |
| Document Engineering Ready | pass | 文档体系、机器摘要、守卫脚本、多角色复审证据齐备 |
| Team Topology Ready | pass | 5 个固定角色、职责边界、切片参与规则和人工介入边界明确 |
| PRD Ready | pending-review | `20` 被产品和架构评审通过 |
| Harness Ready | agent-approved | `30/31/32` 与脚本产物通过文档守卫 |
| Design Review Passed | agent-approved | `40/45/45-details/50` 多角色复审 blocking 已整改 |
| Environment Ready | pass | S1 `deploy` 真实依赖健康检查通过 |
| Backend Baseline Ready | pass | S2 Maven 多模块、BOM 和结构测试通过 |
| Build Ready | pass-for-s4 | S4 Provider Catalog、平台模型基础配置、RBAC/menu、真实模型调用和页面操作证据已形成 |
| Quality Gate | pass-for-s4 | S5.1 需要新增统一模型调用、trace 和 usage 对应验证证据 |
| Release Ready | not-started | 质量 gate 通过且发布/回滚方案齐备 |

## 架构约束草案

- 后端必须按 bounded context / module 划分，禁止 controller 直接拼接外部 provider、数据库 SQL 或工具调用。
- DDD 分层必须遵守 `interfaces -> application -> domain <- infrastructure` 的依赖方向；domain 不依赖 Spring Web、MyBatis-Plus、Sa-Token 或 Spring AI 类型。
- 领域规则必须内聚在聚合、实体、值对象和领域服务中；application service 只做用例编排、事务边界和上下文协调。
- shared kernel 必须提供 `AggregateRoot`、`Entity`、`ValueObject` 标记接口；domain 类型必须显式声明聚合根、实体或值对象语义。
- `domain/repository` 禁止声明对外传播的内部 `record`，尤其是 `*Record`；repository port 必须以聚合、领域对象或明确 query projection 为边界。
- `application/service` 禁止构造 repository record、PO 或领域持久化快照，禁止承载状态流转、能力校验、唯一性规则、绑定规则、默认目录规则和领域 ID 生成。
- `infrastructure/persistence/repository` 禁止成为超大实现类；不得同时承载多个聚合、bootstrap seed、默认目录、业务绑定策略和大量私有转换方法。
- repository 已按业务拆分后，禁止再通过 `infrastructure/persistence/store` 包做二次转发或重新聚合持久化能力；PO 转换只能进入纯 converter。
- 只允许 `interfaces/request/response`、`application/command/query/dto`、值对象或局部私有不可跨层传播结构使用 `record`；repository port 内部 `record` 不作为完成态。
- 所有功能切片必须执行 TDD：先写失败测试，确认红灯，再写实现，再重构。
- 所有后台功能必须先分类为平台管理或租户平台；平台管理功能必须绑定平台权限和平台菜单，租户平台功能必须绑定租户权限和租户菜单。
- 模型调用必须经过 model gateway，禁止业务模块直接调用 provider SDK。
- AI 基础能力必须通过 Spring AI adapter / port 接入，禁止业务层散落 provider SDK 调用。
- 知识检索必须经过 retrieval service，禁止问答应用直接访问向量库。
- 工具执行必须经过 tool runtime，禁止模型直接访问企业系统凭证。
- 每张业务表主键必须统一为 `id BIGINT PRIMARY KEY`，由平台雪花 Long ID 生成；`tenant_id`、`user_id`、`provider_id`、`model_id` 只能作为 Long 关联键，禁止用名称、编码或字符串业务值承担主键。
- `tenant_code`、`username`、`provider_name`、`model_code` 是业务唯一键或展示/配置键，不得作为跨表关联主键。
- 每个租户资源必须有 `tenant_id` 或等价隔离键；跨租户操作必须显式授权。
- 所有高风险写操作必须审计。
- 所有 model / retrieval / tool 调用必须写 trace。
- 登录、登出、当前用户、权限校验和角色查询必须通过 Sa-Token + Redis-backed session 实现。
- 平台管理和租户管理接口必须使用 Sa-Token 权限注解或 application permission check；前端菜单隐藏不能作为唯一授权控制。
- 前端控制台只能渲染 `/api/auth/me` 返回的菜单，禁止用静态路由或本地常量作为功能可见性事实源。
- 前端控制台必须遵守 `docs/45-details/03-frontend-console-design-system.md`；页面完成态不能只满足可点击 smoke，还必须满足企业控制台布局密度、表单/表格/状态/响应式和视觉一致性。
- 前端样式必须通过统一 token、layout primitive、button、form、table、message 和 status 规则表达；禁止每个页面临时堆叠卡片、营销型文案、大面积渐变或与当前切片无关的展示视觉。
- 多租户平台管理端的完整 IAM 最小闭环必须包含租户创建/更新/启停、角色创建/更新/启停、权限点创建/更新/启停、菜单创建/更新/启停、角色绑定权限和菜单，以及登录后按 `/api/auth/me` 菜单树渲染。
- 菜单目录维护页面导航元数据，权限目录维护接口授权语义；任何新增平台/租户业务页都必须同时规划 `menuCode`、`componentKey`、`routePath`、`permissionCode` 和后端权限注解。
- 持久化实现使用 MyBatis-Plus，但 MyBatis-Plus PO / Mapper 不得进入 domain 层。
- API 必须统一响应结构、统一异常语义和统一错误码。
- 注释只解释业务不变量、边界、风险和非显而易见的设计原因，禁止流水账注释。
- 代码中的注释、面向用户或调用方的异常描述、响应描述必须使用中文。
- 缓存必须采用 Caffeine L1 + Redisson/Redis L2 的显式二级缓存设计；禁止业务模块各自创建孤立缓存。
- Spring AI、Sa-Token、MyBatis-Plus、Redisson/Caffeine、MinIO/S3、OpenTelemetry 等非业务框架能力必须优先落到平台基础设施模块，业务模块只能通过自身端口和 infrastructure adapter 使用。
- 研发环境脚本必须放在 `deploy/`，不得在根目录或模块目录散落临时 Docker 脚本。
- 后端必须采用 Maven 多模块，`ai-platform-bom` 是唯一第三方依赖版本管理入口。
- 后端 Maven `groupId` 固定为 `org.fujay`，所有后端模块 `artifactId` 必须以 `ai-` 开头。
- 子模块禁止声明第三方依赖版本，版本必须来自 BOM。
- 任务完成统一标准是前后端同时启动、页面正式操作无报错、功能正常且连接真实环境。
- 团队拓扑固定为架构师、产品工程师、资深后端研发、资深前端工程师、UI 工程师；角色职责和切片参与规则以 `docs/33-team-topology.md` 为准。

## 框架官方用法约束

以下约束来自 `docs/facts/framework-official-research.md`，属于实现前门禁，而不是建议项。

### 平台基础设施模块

- `ai-platform-ai/cache/security/persistence/storage/telemetry` 只能依赖 `ai-platform-shared` 和必要第三方框架，禁止依赖任何业务 context。
- 业务 context 的 `domain` 禁止依赖平台基础设施模块。
- 业务 context 的 `infrastructure` 可以依赖平台基础设施模块，用来实现 application/domain 定义的业务 port。
- `ai-platform-boot` 是最终装配点，业务模块不得私自重复创建同类框架配置。
- 框架能力不得吞掉业务语义：IAM 事实仍归 Tenant/IAM，Provider 配置仍归 Model，文档和知识 ACL 仍归 Knowledge，Tool 审批仍归 Tool Runtime。

### Spring AI

- 业务层只能依赖平台定义的 model / embedding / retrieval / tool port，禁止 controller、application service 或 domain 直接注入 `ChatClient`、`ChatModel`、`EmbeddingModel`、`VectorStore`。
- `ChatClient`、Advisor、RAG、ToolCallback、VectorStore 的通用装配只能出现在 `ai-platform-ai` 或 boot configuration；业务 context 的 `infrastructure/ai` 只做业务 port 适配。
- Provider API key、tool credential、database credential 不得写入 `application.yml`；租户级密钥由配置库/secret store 加密保存并由 adapter 运行时装配。
- Provider API Key 是模型供应商配置项，必须通过 Provider Catalog 入库并加密保存；响应只允许返回 last4，禁止返回明文或密文。
- 平台 provider type、baseUrl、模型编码、能力类型和默认参数必须来自平台模型目录；租户模型配置页面只能下拉选择平台目录项，不能自由输入目录字段。
- 模型能力类型必须在保存前校验，chat/reasoning 型号不得配置为 embedding，embedding 型号不得配置为 chat。
- RAG metadata filter 必须包含 tenant / knowledge base / document ACL 约束；缺少 filter 的检索实现不得合入。
- Tool calling 只能暴露 Tool Runtime 审批后的 `ToolCallback` 或 tool name；高风险写工具必须经过策略和审批，禁止模型直接持有企业系统凭证。
- Spring AI prompt、completion、tool argument、tool result、vector query response 生产默认不得明文记录；如需排障，必须有脱敏、短期和环境限定。

### Sa-Token

- 登录、登出、当前主体、token 查询、kickout、权限和角色校验必须通过 `AuthApplicationService` / `SaTokenAuthAdapter` 统一封装，禁止在 controller 和业务模块中散落 `StpUtil`。
- 生产完成态必须使用 Sa-Token Redis-backed session；内存 session 只能用于单元测试，不能作为验收证据。
- token login id 只能保存雪花 Long 型 `tenantId`、`actorType`、雪花 Long 型 `actorId` 主体引用，不得保存角色、权限、租户名称、租户编码、用户名、密钥或个人敏感资料。
- 权限码和角色必须从 IAM 事实源通过 permission bridge / `StpInterface` 查询，不得硬编码在 token payload 或前端路由里。
- 菜单必须从 IAM 事实源查询并随当前用户接口返回；新增页面未注册菜单和权限时，不得作为完成态。
- `/api/**` 默认受 Sa-Token 路由拦截保护；登录、健康检查和公开资源必须显式白名单。

### MyBatis-Plus

- MyBatis-Plus 配置必须集中在 `ai-platform-persistence` / boot；业务模块不得私自定义一套 `MybatisPlusInterceptor`。
- 多模块 Mapper XML 扫描必须使用 `classpath*:/mapper/**/*.xml`。
- 插件顺序必须遵守：多租户/动态表名先，分页/乐观锁居中，非法 SQL / 防全表更新删除最后。
- `TenantLineInnerInterceptor` 是共享库字段隔离的默认控制点；业务表必须有 Long 型 `tenant_id`，平台级全局字典表必须列入显式 ignore list。
- 分页插件需要 `PaginationInnerInterceptor(DbType.POSTGRE_SQL)`，并在 BOM 中引入 `mybatis-plus-jsqlparser`。
- 需要并发修改保护的配置类聚合必须使用乐观锁字段和 `@Version`。
- `BlockAttackInnerInterceptor` 默认启用；任何绕过全表更新/删除保护的能力必须有独立用例、审计和架构评审。
- Mapper、PO、Wrapper、`IPage` 不得进入 domain；controller 不得直接返回 MyBatis-Plus 类型。

### Redisson + Caffeine 缓存

- 二级缓存必须通过平台统一 `TwoLevelCacheFacade` / `TwoLevelCacheManager` 使用，禁止业务模块直接 new Caffeine cache 或直接把 Redisson map 当业务仓储。
- L1 只能使用 Caffeine 进程内缓存；L2 使用 Redisson 访问 Redis，缓存数据不得绕过统一 key builder、serializer、tenant namespace 和 metrics。
- 缓存 key 必须包含 env、app、cacheName、tenantId、schemaVersion 和业务 key hash；禁止包含明文 token、手机号、邮箱、provider secret、tool credential、SQL 或 prompt。
- 写操作必须在数据库事务提交后再失效缓存；禁止在事务提交前发布失效事件。
- 跨节点 L1 失效必须通过 Redisson topic 或等价分布式事件实现；只删本机 Caffeine 不算完成。
- Sa-Token token/session、provider secret 明文、tool credential 明文、audit log、tool_run、conversation message 默认禁止进入业务二级缓存。
- 鉴权、权限、租户状态、限流和工具策略缓存 Redis 不可用时默认 fail closed；只读低风险字典才允许有明确 stale 窗口。
- 所有 cacheName 必须声明 L1 TTL、L2 TTL、max size、是否允许负缓存、是否允许 stale、失效事件名和数据责任模块。
- 缓存命中率、loader 延迟、失效延迟、stale served、序列化失败必须进入 observability。

## 待机械化约束

进入编码前需要补齐：

- 架构边界检查脚本。
- TDD 检查清单和测试命名规范。
- DDD 分层 ArchUnit 检查；`scripts/harness_ddd_guard.py` 已纳入强制质量门禁，任何 blocker 级命中都必须阻断功能开发。
- API contract 检查。
- migration 检查。
- 禁止 `InMemory*` / `Fake*` 完成态的扫描。
- secret 和敏感配置扫描。
- Spring AI 直接注入扫描：禁止 `ChatClient` / `ChatModel` / `VectorStore` 出现在非 adapter / configuration 包。
- Sa-Token 直接调用扫描：禁止非 `satoken` adapter / auth service 使用 `StpUtil`。
- MyBatis-Plus 插件配置检查：tenant、pagination、optimistic lock、block attack 是否按顺序注册。
- 二级缓存边界检查：禁止非 cache infrastructure 直接创建 Caffeine cache / Redisson map。
- 平台基础设施依赖检查：禁止 `ai-platform-ai/cache/security/persistence/storage/telemetry` 依赖业务 context。
- 存储 SDK 直接调用扫描：禁止非 `ai-platform-storage` 直接使用 MinIO / S3 SDK。
- 缓存 key 检查：禁止缺少 tenant namespace 或包含敏感明文字段。
- 缓存失效测试：验证 `AFTER_COMMIT` 后 L2 删除和跨节点 L1 失效。
- 统一异常 / 统一响应结构扫描或契约测试。
- MyBatis-Plus mapper / PO 泄漏到 domain 的扫描。
- 前后端 lint / test / build 命令。
- `deploy/scripts/healthcheck.sh` 运行证据。
- Maven 多模块和 BOM 结构检查。
- Maven 坐标检查：`groupId=org.fujay`，`artifactId` 以 `ai-` 开头。

## Drift Escalation

以下问题同类出现 2 次后，先补控制面再继续实现：

- 阶段文档和代码事实冲突。
- 未经评审直接编码。
- 直接绕过 model gateway / retrieval service / tool runtime。
- 未按 TDD 红绿重构推进。
- domain 依赖 Spring AI / Sa-Token / MyBatis-Plus 等基础设施类型。
- 重复缺少 tenant / audit / trace。
- 为赶进度引入临时 fake 机制。
- 跳过 `deploy` 真实依赖，直接使用本机已有服务或内存依赖。
- 后端模块新增依赖未进入 `ai-platform-bom`。
- 后端模块使用非 `org.fujay` groupId 或非 `ai-` 开头 artifactId。
- 业务模块绕过统一二级缓存 facade 或缓存敏感明文。
- 业务模块绕过平台基础设施模块直接装配 Spring AI、Sa-Token、MyBatis-Plus、S3/MinIO 或 OpenTelemetry。

## 统一完成标准

任何横切或纵切任务完成时必须满足：

- `deploy/scripts/healthcheck.sh` 通过，或有明确环境阻塞结论。
- 后端服务连接真实 PostgreSQL、Redis、MinIO 等依赖。
- 前端服务和后端服务同时启动。
- 页面正式操作无控制台错误、无接口错误、无白屏。
- 页面满足企业级控制台设计系统：关键流程可扫描、主操作明确、状态反馈贴近操作区、桌面/移动无溢出和裁切。
- 当前功能在真实环境中正常完成，不使用 fake、mock、in-memory 或静态数据作为完成态。
- 相关 TDD、DDD、统一响应、统一异常、权限和租户隔离验证通过。

处理方式：

1. 更新 `docs/evidence/deviation-log.md`。
2. 新增或更新规则 / 脚本 / CI。
3. 重新生成 context pack。
4. gate 回退为 `blocked` 或 `pending-review`。

## Manual Gate 边界

只在以下情况请求用户人工介入：

- 修改产品原则、架构原则、技术栈原则、模块所有权原则。
- 修改固定团队角色、角色职责边界或最终质量责任归属。
- 修改统一完成标准、质量门禁、安全/合规风险接受标准。
- 接受无法由脚本、测试或真实运行证据证明的风险。
- 需要用户选择业务优先级、商业部署策略或首批供应商优先级。

不需要人工介入，默认由 agent 和脚本闭环：

- 文档索引同步。
- context pack / preflight 生成。
- 非原则性措辞修正。
- drift detector 补强。
- 多角色只读复核和整改。
- S1/S2 范围内不改变原则的环境和后端基线推进。

不允许 manual gate 替代：

- 鉴权、租户隔离、审计和密钥安全。
- 核心链路真实运行证据。
- 数据 migration。
- 质量验证。
