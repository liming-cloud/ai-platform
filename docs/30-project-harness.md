# 30 项目研发控制面

Stage: harness  
Category: stage-main  
Status: current  
Review Status: agent-reviewed  
Reviewers: architect, engineering lead  
Approval: agent-approved  
Inputs: `docs/20-prd-and-scope.md`, `docs/facts/repo-preflight.md`  
Outputs: 项目执行规则、事实源优先级、门禁与证据合同  
Depends On: `docs/00-index.md`, `docs/20-prd-and-scope.md`  
Load When: before-architecture/before-implementation  
Next: `docs/31-context-loading.md`, `docs/32-control-surface.md`, `docs/40-architecture-and-tech-spec.md`

## 研发目标

建立一个可持续演进的企业级多租户 AI 平台。当前仓库已完成文档工程、真实依赖、后端基线和首个可运行鉴权纵切，后续编码必须继续受切片合同和运行证据约束。

## 已确认技术基线

- 架构方法：TDD + DDD，启用 `springboot-enterprise-ddd` profile。
- 后端框架：Spring Boot。
- AI 基础能力：Spring AI。
- 登录、会话、权限：Sa-Token，session/token 状态必须使用 Redis 承载。
- 持久化：MyBatis-Plus。
- 二级缓存：Redisson + Caffeine，Caffeine L1 + Redis L2。
- 平台基础设施模块：Spring AI、Sa-Token、MyBatis-Plus、Redisson/Caffeine、MinIO/S3、OpenTelemetry 等非业务框架能力独立成同级基础设施模块。
- 工程规范：统一注释规范、统一异常规范、统一响应规范。
- 复杂度约束：不引入 Python worker，AI 与异步任务优先在 Java / Spring 体系内落地。

## 事实源优先级

1. 仓库真实文件、构建配置、脚本和运行证据。
2. `docs/_generated/context-pack.json` 与 `docs/_generated/repo-preflight.json`。
3. `docs/00-index.md` 与当前阶段主文档。
4. `docs/facts/*` 和 `docs/evidence/*`；框架用法以 `docs/facts/framework-official-research.md` 回写后的约束为准。
5. 用户在对话中的最新明确决策。
6. 外部公开资料：竞品资料只用于产品判断；Spring AI、Sa-Token、MyBatis-Plus 等框架资料必须优先使用官方文档，并先沉淀到 facts 文档后再作为实现约束。

## Package Map

| 层 | 载体 | 责任 |
| --- | --- | --- |
| Entrypoint | `README.md`, `AGENTS.md`, `docs/00-index.md` | 入口、当前阶段、加载路径 |
| Semantic mother doc | `docs/30-project-harness.md` | 项目目标、事实源、门禁、执行合同 |
| Context loading | `docs/31-context-loading.md` | 按阶段加载最小上下文 |
| Control surface | `docs/32-control-surface.md`, scripts, evidence | 规则、脚本、manual gate、偏差日志 |
| Team topology | `docs/33-team-topology.md` | 5 个固定角色、职责边界、切片参与和协作协议 |
| Design package | `docs/40-*`, `docs/45-*`, `docs/50-*` | 架构、模块、计划 |
| Design detail | `docs/45-details/*` | 后端代码组织、平台基础设施和单模块详细设计 |
| Facts | `docs/facts/*`, `docs/_generated/*` | 仓库、市场、环境和命令事实 |
| Evidence | `docs/evidence/*` | gate verdict、偏差和后续质量证据 |
| Deploy | `deploy/*` | 本地研发环境和后续部署资产 |

## Production Baseline

核心完成态必须具备：

- 真实数据库 migration。
- 真实鉴权授权和租户隔离。
- Sa-Token 登录、登出、当前用户、权限查询、角色查询、token 续期和失效链路。
- Redisson + Caffeine 二级缓存、跨节点失效广播、缓存观测和故障策略。
- provider 密钥加密存储和审计。
- 模型调用、知识检索、工具执行的 trace。
- 可运行的 API 和 UI 证据。
- 自动化测试和结构检查。
- 配置、secret、日志、metrics、回滚策略。
- `deploy` 真实依赖健康检查通过，后续功能连接真实依赖完成。

禁止把以下内容作为正式完成态：

- `InMemory*` 仓储。
- `Fake*` provider。
- 硬编码租户和默认管理员作为生产路径。
- 静态知识命中数据。
- 只为过门存在的 mock API。
- 无审计的工具执行。
- Spring AI adapter 之外的业务模块直接调用模型 SDK。
- controller / application / domain 直接注入 Spring AI 核心类型。
- controller 或业务模块散落 `StpUtil` 调用。
- MyBatis-Plus `Mapper`、PO、Wrapper、`IPage` 泄漏到 domain 或 HTTP 响应。
- 业务模块绕过统一 cache facade 直接创建 Caffeine / Redisson 缓存。
- 缓存 token、provider secret、tool credential、audit log 或 conversation message。
- 平台基础设施模块依赖业务 context，或业务 domain 依赖平台基础设施模块。
- 业务模块绕过平台基础设施模块直接装配 Spring AI、Sa-Token、MyBatis-Plus、S3/MinIO 或 OpenTelemetry。
- controller 或 mapper 里承载领域规则。

## Implementation Preflight

任何编码切片开始前必须满足：

- `docs/30/40/45/50` 已 agent-reviewed；`20` 作为产品范围事实源，只有产品原则、业务优先级或验收边界变化时才需要人工确认。
- `python3 scripts/generate_context_pack.py` 产物存在且当前。
- 研发第一切片必须先完成 `deploy` 环境 readiness；后端和前端代码不得早于真实依赖脚本。
- 切片合同给出允许修改目录、must-exist、must-not-exist、runtime evidence、structure evidence。
- 第一个测试已按 TDD 红灯失败，且失败原因与目标行为一致。
- 如依赖 Docker / DB / Redis / object storage / vector store，必须先完成环境 readiness。

## Manual Gates

当前无法完全机械化的 gate：

- 产品原则、业务优先级或验收边界变更。
- 商业发布形态和发布节奏选择。
- 首批模型供应商和知识源的业务优先级选择。
- 模块所有权、架构原则、技术栈原则或统一完成标准变更。
- 数据安全、合规和风险接受标准变更。

不能用 manual gate 放行的内容：

- 缺少真实持久化。
- 缺少鉴权和租户隔离。
- 缺少 provider 密钥安全策略。
- 缺少核心链路运行证据。
- 缺少审计和 trace。
- 缺少 `deploy` 真实依赖健康检查。
- 前后端未同时启动或页面正式操作存在错误。

## Parallel Owner Rules

团队固定为架构师、产品工程师、资深后端研发、资深前端工程师、UI 工程师。职责边界、切片参与和人工介入边界以 `docs/33-team-topology.md` 为准。

进入实现后建议按非重叠写集拆分：

- Platform core owner：tenant / IAM / settings；需要审计时通过 audit port 写入。
- Model owner：provider catalog / model gateway / quotas。
- Knowledge owner：sources / ingestion / retrieval / vector index。
- Tool owner：tool catalog / execution / approval；工具执行审计通过 audit port 写入 Governance。
- Governance owner：quota / billing / audit ledger / retention / compliance export。
- Observability owner：trace / metrics / eval / dashboards。
- Frontend owner：admin console / tenant console / app builder / chat UI。
- Environment owner：`deploy`、Docker Compose、环境变量、健康检查。

跨 owner 改动必须经过 controller checkpoint，先合并底层 domain / persistence，再合并 API / UI。

## Readiness Verdict

`s3-build-ready / s4-provider-catalog-ready-to-start`。

当前结论：

- S1 `deploy` 真实依赖已通过健康检查。
- S2 后端 Maven 多模块、BOM、平台基础设施骨架和结构测试已完成。
- 部署模式、前端方案和租户隔离等级已确认。
- S3 前后端同时启动、页面正式操作和鉴权纵切证据已完成。
- 下一步只能进入 S4 Provider Catalog，不得把知识库、tool 或完整观测能力并入同一切片。
