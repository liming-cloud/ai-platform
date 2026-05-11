# 33 团队拓扑与角色协作

Stage: harness-team  
Category: stage-main  
Status: current  
Review Status: agent-reviewed  
Reviewers: architect, product engineer, senior backend engineer, senior frontend engineer, UI engineer  
Approval: agent-approved  
Inputs: `docs/30-project-harness.md`, `docs/32-control-surface.md`, `docs/50-delivery-plan.md`  
Outputs: 研发团队角色、职责边界、切片参与规则、协作门禁  
Depends On: `docs/30-project-harness.md`, `docs/32-control-surface.md`  
Load When: team-coordination/implementation-planning  
Next: 先完成前端控制台企业级设计系统加固，再启动 S5.1 统一模型调用、trace 和 usage 基线

## 固定团队结构

项目研发团队固定为 5 个角色：

| 角色 | 核心责任 | 可决策事项 | 不可越界事项 |
| --- | --- | --- | --- |
| 架构师 | 架构边界、模块所有权、门禁、切片顺序、ADR、反漂移 | 技术约束执行、架构偏差阻断、合并顺序、质量门禁回退 | 不替代产品优先级，不绕过真实运行证据 |
| 产品工程师 | 产品范围、用户场景、验收标准、优先级、竞品到需求的转译 | PRD 细化、切片验收、MVP 范围排序 | 不直接修改架构原则，不以体验诉求放行安全和质量缺口 |
| 资深后端研发 | 后端 Maven 多模块、DDD/TDD、平台基础设施、业务后端、真实依赖接入 | 后端实现方案、测试策略、migration、框架适配落点 | 不直接更改产品范围，不绕过 Spring AI/Sa-Token/MyBatis-Plus/cache/storage 基础设施边界 |
| 资深前端工程师 | 前端工程架构、API contract 消费、状态管理、权限 UI、构建和 E2E | 前端技术实现、组件接入、接口联调、前端质量命令 | 不先于冻结 API contract 写假数据完成态，不绕过真实后端接口 |
| UI 工程师 | 信息架构、交互、视觉系统、可用性、一致性和响应式质量 | 设计系统、页面布局、交互状态、视觉验收标准 | 不新增产品能力，不用营销型展示替代企业控制台操作界面 |

## 角色启动状态

当前团队已完成 S1/S2/S2.1/S3/S4，进入 S5.1 统一模型调用准备状态。用户已指出现有前端视觉和规范仍不满足企业级控制台要求，因此 S5.1 新增前端入口前先完成设计系统加固。

| 角色 | 当前启动任务 | 当前不可做 |
| --- | --- | --- |
| 架构师 | 维护 `pass-for-s4 / s5-model-gateway-entry` gate，确认 S5.1 边界 | 不允许把知识库、tool、数据库知识源或完整观测范围并入 S5.1 |
| 产品工程师 | 将 S5.1 的完成标准转成可验收清单，保持产品原则变更才人工介入 | 不扩写知识库、tool 或观测业务范围 |
| 资深后端研发 | 维护 S4 验证证据，准备统一模型调用、trace 和 usage/cost 基线 | 不绕过 Spring AI adapter、secret 边界和审计要求 |
| 资深前端工程师 | 将现有 `frontend/console-web` 收敛到企业级设计系统，保留真实 API contract 和质量命令 | 不创建脱离真实后端的模型调用假数据完成态，不为视觉优化扩业务范围 |
| UI 工程师 | 维护企业级控制台设计系统，复核布局密度、表单/表格、状态反馈和响应式 | 不制作营销页或与当前切片无关的视觉稿 |

## 切片参与矩阵

| Slice | 主责角色 | 必须参与复核 | 退出条件补充 |
| --- | --- | --- | --- |
| S1 Dev Environment | 资深后端研发 | 架构师、产品工程师 | `deploy/scripts/healthcheck.sh` 真实通过，环境 facts 更新 |
| S2 Backend Maven Baseline | 资深后端研发 | 架构师 | Maven BOM、`groupId=org.fujay`、`artifactId=ai-*`、DDD 目录和基础设施边界可验证 |
| S3 App Skeleton + Auth | 资深后端研发、资深前端工程师 | 架构师、产品工程师、UI 工程师 | 前后端同时启动，真实登录/登出/当前用户/403/受保护页面可操作 |
| S4-S5 Model Gateway | 资深后端研发 | 架构师、产品工程师 | provider 配置、密钥、安全审计、Spring AI adapter 通过 contract test |
| S6-S8 Knowledge & Retrieval | 资深后端研发 | 架构师、产品工程师、资深前端工程师 | 文档/数据库知识源真实可查，引用、权限、trace 可验证 |
| S9-S10 Tool Runtime | 资深后端研发 | 架构师、产品工程师、资深前端工程师、UI 工程师 | tool 执行、审批、审计和 UI 操作闭环 |
| S11-S12 Observability & Eval | 资深后端研发、资深前端工程师 | 架构师、产品工程师、UI 工程师 | usage、trace、eval、feedback、dashboard 真实数据可查 |

## 协作协议

- 每个切片开始前，架构师用 `docs/50-delivery-plan.md` 确认 slice id、允许写集、must-exist、must-not-exist、runtime evidence 和 structure evidence。
- 产品工程师只在产品原则、验收边界、业务优先级或商业策略变化时请求人工介入；常规验收清单由 agent 维护。
- 资深后端研发按 TDD + DDD 推进，先红灯测试，再 domain/application/infrastructure/interfaces 实现；涉及框架能力时必须遵守平台基础设施边界。
- 资深前端工程师只消费冻结 API contract；没有真实后端接口时可写组件和状态测试，但不能把假数据页面作为完成态。
- 资深前端工程师和 UI 工程师必须共同遵守 `docs/45-details/03-frontend-console-design-system.md`；任何新增页面必须复用统一 token、layout、表单、表格、按钮、消息和状态规范。
- UI 工程师输出企业控制台导向的设计系统和交互验收，不输出与当前切片无关的展示型页面。
- 任何角色发现 scope 扩散、文档和代码事实冲突、真实运行证据缺失时，直接回写 `docs/evidence/deviation-log.md` 并触发 guard，而不是要求人工复审。

## 集成顺序

S1、S2、S3 必须串行。S3 通过后才允许多角色并行。

并行实施时采用以下合并顺序：

1. 架构师冻结 slice contract 和 API contract。
2. 后端先合并 domain、migration、repository、application service 和接口契约。
3. 前端消费已冻结接口，补页面、状态、错误处理和 E2E。
4. UI 工程师按 `docs/45-details/03-frontend-console-design-system.md` 复核布局、交互状态、响应式、可访问性和企业级视觉一致性。
5. 产品工程师按验收清单做功能验收。
6. 架构师运行 guard、结构检查和质量门禁，决定是否进入下一切片。

## 完成态约束

团队角色启动不改变统一完成标准。任何切片仍必须满足：

- 连接真实 PostgreSQL、Redis、MinIO 等依赖。
- 后端和前端同时启动。
- 页面正式操作无控制台错误、接口错误和白屏。
- 页面满足企业级控制台设计系统，不以“能用的 smoke 页面”作为最终视觉完成态。
- 无 `InMemory*` / `Fake*` / 静态数据作为正式完成态。
- 相关 TDD、DDD、统一响应、统一异常、权限、租户隔离、审计和 trace 证据齐备。
