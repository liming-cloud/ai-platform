# 50 研发实施路径

Stage: planning  
Category: stage-main  
Status: current  
Review Status: agent-reviewed  
Reviewers: product owner, architect, engineering lead, QA owner  
Approval: agent-approved  
Inputs: `docs/20-prd-and-scope.md`, `docs/40-architecture-and-tech-spec.md`, `docs/45-module-and-service-design.md`  
Outputs: 里程碑、切片合同、验证顺序、build ready 结论  
Depends On: `docs/30-project-harness.md`, `docs/31-context-loading.md`, `docs/32-control-surface.md`, `docs/40-architecture-and-tech-spec.md`, `docs/45-module-and-service-design.md`  
Load When: planning/implementation  
Next: 在 S5.1 证据保持绿色后推进 S5.2 调用配额、重试/路由策略和模型成本单价配置

## 研发策略

采用 “TDD + DDD + early runnable vertical slice + bounded context 渐进完整交付”。每个切片在自己的边界内必须完整，包括领域模型、真实持久化、接口、审计、租户、trace、测试和运行证据。

不采用 baseline-first：不先做临时骨架再未来重构。

后端技术栈固定为 Spring Boot + Spring AI + Sa-Token + MyBatis-Plus + Redisson + Caffeine，不引入 Python worker。研发第一步固定为 `deploy` 真实环境依赖，然后才进入后端 Maven 多模块和前后端纵切。

S1/S2/S2.1/S3/S4 已完成，S4.4 已补齐完整 IAM 平台管理闭环，S4.5 已完成企业级前端控制台设计系统加固。S5.1 已完成统一聊天调用、trace、usage/cost 基线记录和租户模型页最小调用验收面板，并通过真实 SiliconFlow 正式调用验证。后续 S5.2 只推进配额、重试/路由策略和模型成本单价配置，知识库、tool、数据库知识源和完整观测业务仍不得越过各自切片提前扩散。

## 里程碑

| 阶段 | 目标 | 退出条件 |
| --- | --- | --- |
| P0 规划与控制面 | 产品范围、架构、模块设计、计划、脚本摘要 | `20/30/40/45/50` 评审通过 |
| P1 Development Environment | `deploy` 真实依赖、Docker Compose、健康检查 | 已完成：PostgreSQL、Redis、MinIO 健康检查通过 |
| P2 Backend Maven Baseline | Maven 多模块、BOM、shared、boot、首个模块骨架 | 已完成：`mvn -f backend/pom.xml test` 通过，BOM 管理依赖，DDD 结构检查有入口 |
| P3 Early Runnable Vertical Slice | Spring Boot、前端、DB、Sa-Token 登录、租户、受保护页面、审计 | 已完成：前后端同时启动，登录、登出、当前用户、权限拒绝在真实依赖上可验证 |
| P4 Model Gateway | Spring AI provider adapter / 平台模型基础配置 / 平台模型选项目录 / 租户模型配置 / secret / unified invocation | 已完成 S4 Provider Catalog 和 S5.1 统一聊天调用：平台管理员可维护模型基础配置，租户可从平台启用目录下拉配置，SiliconFlow 免费模型真实连接测试和正式调用均通过，OpenAI 额度失败被统一归一 |
| P5 Document Knowledge RAG | 文档上传、解析、chunk、embedding、检索、引用问答 | 文档问答带引用，trace 可查 |
| P6 Database Knowledge Source | 只读数据库连接、语义层、SQL guard、问答 | 受控查询，无任意 SQL |
| P7 Tool Runtime | tool catalog、OpenAPI/HTTP、MCP 预留、审批、审计 | tool call 全链路可追溯 |
| P8 Observability & Eval | usage、cost、latency、quality、feedback、eval dataset | 按租户/应用/模型/知识/工具可查看指标 |
| P9 Enterprise Hardening | 配额、告警、备份、部署、回滚、安全扫描 | 发布前质量和回滚文档齐备 |

## 切片合同矩阵

| Slice | 目标 | 允许写集 | must_exist_artifacts | must_not_exist_after_slice | runtime evidence | structure evidence | blocked_by |
| --- | --- | --- | --- | --- | --- | --- | --- |
| S0 Planning Package | 当前文档和脚本控制面 | `docs`, `scripts`, `README.md`, `AGENTS.md` | `00/10/20/30/31/32/40/45/50`, generated context pack | 空洞 README、无 gate 文档 | 脚本可运行 | docs index 自洽 | 无 |
| S1 Dev Environment | 真实研发依赖 | `deploy`, `docs/facts`, `scripts` | `deploy/docker-compose.yml`, `.env.example`, `up/down/healthcheck`, environment docs | 根目录临时 compose、无健康检查 | `deploy/scripts/healthcheck.sh` 通过 | context pack 识别 deploy | Docker 不可用或端口冲突 |
| S2 Backend Maven Baseline | Maven 多模块和 BOM | `backend`, `docs/45-details`, `docs/60` | `backend/pom.xml`, `groupId=org.fujay`, `artifactId` 全部 `ai-` 开头, `ai-platform-bom`, `ai-platform-boot`, `ai-platform-shared`, `ai-platform-ai`, `ai-platform-cache`, `ai-platform-security`, `ai-platform-persistence`, `ai-platform-storage`, `ai-platform-telemetry`, Spring AI / Sa-Token / MyBatis-Plus / Redisson / Caffeine / S3 依赖版本集中管理, 首个模块骨架 | 子模块写依赖版本、非 `org.fujay` groupId、非 `ai-` artifactId、无 BOM、无 DDD 结构、基础设施模块依赖业务模块 | `mvn -f backend/pom.xml test` | Maven module / BOM / package check + infrastructure dependency check | S1 `deploy/scripts/healthcheck.sh` 真实环境证据通过 |
| S3 App Skeleton + Auth | 可启动纵切 | `backend`, `frontend`, `deploy`, `docs/60` | Spring Boot、MyBatis-Plus migration、集中 `MybatisPlusInterceptor`、Sa-Token + Redis、tenant、audit、protected UI、首个 RED 测试证据 | hardcoded tenant、无鉴权页面、无 Redis session、controller 直接调用 `StpUtil`、业务模块自建缓存 | 已完成：前后端同时启动、登录、登出、当前用户、403、页面无非预期错误 | 已完成：backend test、frontend test/build、headless Chrome UI smoke | 无；S3 证据见 `docs/evidence/s3-auth-slice-evidence-2026-05-10.md` |
| S4 Provider Catalog + IAM Admin Closure | 模型供应商管理与平台后台闭环 | `backend/business/ai-platform-modules/model`, `backend/business/ai-platform-modules/tenant`, `backend/infrastructure/ai-platform-ai`, `backend/apps/ai-platform-boot`, `frontend/console-web`, `docs/45-details/04-provider-model-gateway-design.md`, `docs/facts/iam-rbac-official-research.md`, `docs/60` | 平台 provider type / model option 目录表、平台模型基础配置 API 和页面、租户 provider 表、`id BIGINT` 雪花主键、API Key 加密入库、模型能力类型、模型参数默认值、`AiProviderType` + factory + Spring AI adapter、连接测试、调用记录、租户模型配置页面、租户创建/更新/启停、角色/权限/菜单 CRUD、角色绑定权限和菜单、登录后按 `/api/auth/me` 菜单树渲染 | 明文密钥、业务直接调 provider SDK、controller 直接注入 Spring AI 类型、facade 中写 OpenAI 专用分支、前端假 provider 数据完成态、租户自由输入平台目录字段、租户绕过平台目录自行新增 provider type/baseUrl/modelCode/capabilityType、把 `o3` 配置为 embedding、只做角色绑定却声称完整 IAM 管理、前端静态菜单绕过 `/api/auth/me` | 已完成：平台模型基础配置保存后租户下拉可选、SiliconFlow provider test、RBAC 负向恢复、provider UI smoke、租户创建/更新/启停、角色/权限/菜单保存、角色权限菜单绑定 | 已完成：adapter boundary check + Spring AI contract test + frontend test/build + capability mismatch test + migration/PO id guard + RBAC/menu/API smoke + IAM CRUD smoke | 无；S4 证据见 `docs/evidence/s4-provider-catalog-evidence-2026-05-10.md` |
| S4.5 Frontend Console Design System Hardening | 企业级控制台设计系统加固 | `frontend/console-web`, `docs/45-details/03-frontend-console-design-system.md`, `docs/31-context-loading.md`, `docs/32-control-surface.md`, `docs/33-team-topology.md`, `docs/40-architecture-and-tech-spec.md`, `docs/45-module-and-service-design.md`, `docs/50-delivery-plan.md`, `docs/60-implementation-notes.md`, `scripts` | 设计系统文档、统一 CSS token、应用壳、导航、表单、表格、按钮、消息、状态和响应式规则，现有 S3/S4 页面在不扩业务范围前提下完成企业级视觉加固 | 新业务能力、假数据页面、营销型 hero、大面积装饰视觉、脱离 `/api/auth/me` 的静态菜单、绕过真实 API 的完成态 | frontend test/build + 页面 smoke，无溢出、裁切、控制台错误或接口错误 | 文档索引、context-pack、doc guard 识别前端设计系统；`frontend/console-web` 复用统一样式规则 | 无；这是 S5.1 新前端入口前的设计系统加固 |
| S5 Model Gateway | 统一模型调用 | `backend/business/ai-platform-modules/model`, `backend/infrastructure/ai-platform-ai`, `backend/apps/ai-platform-boot`, `frontend/console-web` | 已完成 S5.1：统一 `POST /api/model-invocations/chat`、`model:invocation:execute`、trace、usage/cost 基础记录、租户模型页调用验收面板；后续再扩展 quota、retry、routing 策略和成本单价 | 绕过 gateway 的调用、把知识库/tool/观测大屏并入 S5.1、保存 prompt/completion 明文 | 已完成：真实 SiliconFlow formal invocation + usage record | 已完成：invocation application test + AI facade test + migration/PO trace/usage persistence test + boot smoke + frontend test/build | S4 |
| DDD Domain Cohesion Recovery | 领域模型与架构偏移恢复 | `backend/business/ai-platform-modules/tenant`, `backend/business/ai-platform-modules/model`, `backend/quality/ai-platform-architecture-tests`, `scripts/harness_ddd_guard.py`, `docs/evidence/*ddd*` | 已完成：Tenant/IAM 和 Model context 删除 repository port 内部 record，拆分 fat application service 和超大 repository impl，bootstrap 改为受控 application/domain 入口，model application 通过业务 port 访问 AI、密钥、当前主体和 ID 能力 | 继续在 application service 堆业务规则、repository impl 承载 seed、bootstrap 直连 Mapper/PO、domain/repository 内部 `*Record` | 已完成：`python3 scripts/harness_ddd_guard.py` 返回 pass；后端全量 Maven 测试通过 | 已完成：Java 架构测试覆盖 repository port record、fat service、bootstrap direct mapper/PO、application 直接依赖平台基础设施等规则 | S5.1 |
| S6 Document KB | 文档知识库 | `backend/business/ai-platform-modules/knowledge`, `backend/infrastructure/ai-platform-storage` | upload、parser、chunk、embedding、index job | 静态 chunk、内存索引、Python worker | 上传文档后问答带引用 | migration + Java job test | S3/S5 |
| S7 Retrieval Quality | 检索增强 | `backend/business/ai-platform-modules/retrieval`, `backend/infrastructure/ai-platform-ai` | hybrid、metadata filter、rerank port、chunk hit trace | 无引用回答 | retrieval trace 可查 | strategy tests | S6 |
| S8 DB Knowledge Source | 数据库知识源 | `backend/business/ai-platform-modules/knowledge`, `backend/business/ai-platform-modules/retrieval` | readonly connector、schema whitelist、semantic layer | 任意 SQL 生成执行 | 受控 DB QA | SQL guard tests | S3/S5 |
| S9 Tool Runtime | 企业系统工具 | tool 模块 | tool catalog、schema、credential、run audit | 模型直接拿凭证 | tool run trace | policy tests | S3/S5 |
| S10 Approval Flow | 高风险工具审批 | `backend/business/ai-platform-modules/tool` | approval state machine、audit | L3 tool 直接执行 | approve/reject demo | state tests | S9 |
| S11 Observability | AI telemetry | `backend/business/ai-platform-modules/observability`, `backend/infrastructure/ai-platform-telemetry` | trace、usage rollup、dashboard API | 只有普通 API 日志 | usage by tenant/model/kb/tool | metric schema tests | S5/S6/S9 |
| S12 Eval & Feedback | 质量评估 | `backend/business/ai-platform-modules/observability` | dataset、case、eval run、feedback score | 无样本回归 | eval run result | evaluator tests | S11 |

每个切片额外要求：

- 新后台功能必须明确属于平台管理或租户平台，并同时交付后端权限码、菜单、API contract 和前端入口。
- 新前端页面必须读取 `docs/45-details/03-frontend-console-design-system.md`，复用企业控制台 token、布局、表单、表格、按钮、消息和状态规范。
- 必须先提交失败测试记录，再提交实现。
- 必须包含 domain/application/interface/infrastructure 中至少一个与切片匹配的测试层级。
- 涉及 HTTP API 的切片必须验证统一响应和统一异常。
- 涉及持久化的切片必须验证 MyBatis-Plus repository 不泄漏 PO 到 domain。
- 涉及缓存的切片必须验证 L1/L2 命中、事务后失效、跨节点 L1 失效和 Redis 故障策略。
- 涉及基础设施框架的切片必须验证业务 domain 不依赖基础设施模块，基础设施模块不依赖业务 context。
- 涉及后端模块开发的切片必须读取 `docs/45-details/01-backend-code-organization.md`，不得默认全量读取架构主文档。
- 每个完成态都必须落到真实环境验证，不接受只跑单元测试或只启动单侧服务。

## Early Runnable Vertical Slice

S3 前后端纵切必须早于铺开大量横向业务模块；S1 `deploy` 和 S2 后端 Maven baseline 是它的前置条件。S3 最小范围已完成：

- 后端服务可启动。
- 前端服务可启动。
- PostgreSQL migration 可执行。
- Sa-Token + Redis-backed session 可用。
- 管理员登录 / 登出 / 当前用户 / 权限校验。
- 本地开发 bootstrap 创建演示租户和管理员账号。
- 普通租户访问其他租户资源返回 403。
- 审计日志记录登录、创建租户、权限拒绝。
- UI 有一个受保护页面显示当前租户上下文。

## 统一完成标准

任何横切或纵切任务完成必须同时满足：

- `deploy/scripts/healthcheck.sh` 通过。
- 后端服务连接真实 PostgreSQL、Redis、MinIO。
- 前端服务和后端服务同时启动。
- 页面正式操作无控制台错误、接口错误和白屏。
- 页面满足企业级控制台设计系统：布局密度、主操作、状态反馈、表单/表格、响应式和可访问性可复核。
- 当前功能在真实依赖上正常完成。
- 对应 TDD、DDD、权限、统一响应、统一异常、租户隔离和审计验证通过。

## 验证矩阵

| 类型 | 首期命令 | 说明 |
| --- | --- | --- |
| Repo preflight | `python3 scripts/harness_repo_preflight.py` | 当前仓库事实摘要 |
| Context pack | `python3 scripts/generate_context_pack.py` | 后续 agent 加载入口 |
| Dev environment | `deploy/scripts/up.sh && deploy/scripts/healthcheck.sh` | 真实依赖 readiness |
| Backend test | `mvn -f backend/pom.xml test` | TDD 基线，所有切片先红后绿 |
| Backend architecture check | `BackendMavenBaselineTest` | 当前已覆盖 Maven module / 类型目录 / BOM / Maven 坐标 / 平台基础设施依赖方向 / domain 禁止框架 import / `StpUtil` 只能在安全基础设施封装 / migration 与 PO 的 `id BIGINT` 雪花主键和 Long 关联键 / DDD fat service、repository record、万能 repository、禁止 persistence store、DDD marker、bootstrap 绕层和生产主配置 secret fallback；后续扩展 Spring AI gateway、retrieval、tool 边界 |
| Backend integration test | `mvn verify` 或后续等价命令 | Sa-Token + Redis、MyBatis-Plus repository、Spring AI adapter、Redisson + Caffeine 二级缓存 |
| Frontend test/build | `pnpm --dir frontend/console-web test && pnpm --dir frontend/console-web build` | Vue 3 + TypeScript + Vite 控制台；视觉系统变更必须同时复核桌面和移动布局 |
| API smoke | S3 Node fetch smoke | 登录、当前用户、当前租户、403、登出 |
| E2E smoke | Chrome headless DevTools smoke | 受保护页面、403、登出；知识问答和 tool call 属于后续切片 |

## 并行策略

S1、S2、S3 已按串行完成。S4 开始允许在 API contract 冻结、写集不重叠和真实运行证据可保留的前提下有限并行。

## 团队拓扑执行规则

团队固定为架构师、产品工程师、资深后端研发、资深前端工程师、UI 工程师，详细职责边界见 `docs/33-team-topology.md`。

| 阶段 | 主责 | 复核 | 说明 |
| --- | --- | --- | --- |
| S1 | 资深后端研发 | 架构师、产品工程师 | 只推进 `deploy` 真实环境 readiness，不创建业务代码 |
| S2 | 资深后端研发 | 架构师 | 只推进 Maven 多模块、BOM、平台基础设施骨架和结构检查入口 |
| S3 | 资深后端研发、资深前端工程师 | 架构师、产品工程师、UI 工程师 | 完成前后端可启动鉴权纵切和首个受保护页面 |
| S4-S12 | 按业务 owner 拆分 | 架构师和产品工程师必复核，前端/UI 按页面参与 | 仅在 S3 通过后按非重叠写集并行 |

S3 已通过，后续业务能力可以按以下 owner 和 gate 有限并行：

- Model owner：S4 Provider Catalog、S5 Model Gateway。
- Knowledge owner：S6 Document KB、S7 Retrieval Quality；S8 DB Knowledge Source 必须等待 query guard contract 明确。
- Tool owner：S9 Tool Runtime、S10 Approval Flow。
- Observability owner：从 S3 开始接入最小 trace，S11/S12 完成 usage、eval 和 feedback 闭环。
- Frontend owner：跟随已冻结 API contract 开发对应页面，不能先于后端 contract；所有页面必须继承 `docs/45-details/03-frontend-console-design-system.md`。

并行条件：

- 写集不重叠。
- API contract 已冻结。
- 平台基础设施模块边界已通过 architecture check。
- migration 冲突由 controller 合并。
- 每个切片都有 runtime evidence。

## 环境 readiness

S1 已完成：

- Java `21.0.10` 可用。
- Maven `3.9.9` 可用。
- Docker `29.2.1` 和 Docker Compose `v5.1.0` 可用。
- PostgreSQL、Redis、MinIO 由 `deploy/docker-compose.yml` 启动并通过健康检查。
- 向量能力首期使用 PostgreSQL + pgvector。

S3 已确认：

- 部署模式：私有化优先，本地 Docker Compose 依赖 + 前后端本机进程。
- 前端技术栈：Vue 3 + TypeScript + Vite。
- 租户隔离等级：共享库共享表 + `tenant_id` 强隔离 + MyBatis-Plus tenant interceptor。

## 当前 Build Ready 结论

`pass-for-s4`。

S1/S2/S2.1/S3/S4/S4.4/S4.5/S5.1 已完成：

- `deploy` 环境 readiness 已运行验证。
- Maven 多模块、BOM、平台基础设施骨架和结构测试已建立。
- 后端 Spring Boot 通过可执行 jar 启动并连接真实 PostgreSQL、Redis、MinIO。
- 前端 Vue 3 控制台通过 Vite 本机启动。
- 登录、登出、当前用户、当前租户、跨租户 403 在真实 API 和真实浏览器页面中通过。
- Provider Catalog、平台模型基础配置、租户模型配置、平台/租户菜单、租户创建/更新/启停、角色/权限/菜单保存、角色权限菜单绑定、SiliconFlow 真实模型连接测试和 S5.1 正式聊天调用在真实 API 中通过。

## 下一步

1. 进入 S5.2 Model Gateway Hardening：补齐租户/模型调用配额、重试/路由策略和模型成本单价配置。
2. S5.2 仍只允许复用 S4/S5.1 provider/model/invocation 基线，不新增知识库、tool、数据库知识源或完整观测大屏。
3. 成本单价配置必须落库并参与 `model_invocation_record.cost_usd` 计算；不能继续把 `模型计费单价未配置` 当成完成态。
4. 保持 `deploy/scripts/healthcheck.sh`、后端 Maven 测试、`pnpm --dir frontend/console-web test && pnpm --dir frontend/console-web build` 通过，并保留真实 API/UI smoke 证据。
