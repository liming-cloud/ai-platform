# 整体文档与架构复审报告

Stage: evidence  
Category: review-evidence  
Status: current  
Review Status: agent-reviewed  
Reviewers: architecture reviewer, document-system reviewer, delivery-gate reviewer  
Approval: agent-approved  
Inputs: `docs/00-index.md`, `docs/01-document-system.md`, `docs/31-context-loading.md`, `docs/32-control-surface.md`, `docs/40-architecture-and-tech-spec.md`, `docs/45-module-and-service-design.md`, `docs/45-details/*`, `docs/50-delivery-plan.md`  
Outputs: 多角色复审结论、blocking issue、整改结果、gate 建议  
Depends On: planning docs  
Load When: design-review/evidence  
Next: 先完成前端控制台企业级设计系统加固，再启动 S5.1 统一模型调用、trace 和 usage 基线

## 复审目标

本轮复审目标是确认当前文档系统是否存在架构设计偏差、任务扩散风险，以及是否能保障后续代码和文档按照同一架构事实源演进。

## 复审角色

| 角色 | 关注点 | 结论 |
| --- | --- | --- |
| 架构一致性 reviewer | 平台基础设施模块、业务 bounded context、artifactId、审计事实源 | 首轮不通过，整改后主要 blocking 已闭合 |
| 文档体系与上下文加载 reviewer | 编号体系、context pack、facts、孤儿文档、token 加载 | 首轮不通过，整改后机器摘要已同步 |
| 交付门禁与任务秩序 reviewer | build ready、切片顺序、并行策略、完成标准 | 首轮不通过，整改后切片编号和并行规则已收敛 |

## Blocking Issues 与整改

| Issue | 风险 | 整改 |
| --- | --- | --- |
| `40` 与 `50` 切片编号冲突 | 后续 agent 按错误顺序推进 | `40` 改为 Delivery Handoff Table，并声明切片编号以 `50` 为唯一事实源 |
| `50` 并行 owner 映射错位 | 业务模块过早并行，造成无序扩散 | 改为 S1/S2/S3 串行，S3 之后按 S4-S12 业务能力并行 |
| 前端/部署/运行时依赖状态混入“已确认技术栈” | build-ready 被误判 | `40` 改为“技术基线状态”，区分后端已确认项与待评审/待验证项 |
| `01-document-system.md` 未进入机器摘要 | 后续任务读旧事实源 | 更新 `00/31/gate/context-pack/preflight`，重新生成 `_generated` |
| `ai-platform-observability` 与业务 observability artifact 冲突 | Maven artifactId 和边界冲突 | 基础设施模块改名为 `ai-platform-telemetry`，业务模块保留 `ai-platform-modules/observability` |
| bounded context 到 Maven module 映射不一致 | S2 创建错误骨架 | 增加 `retrieval` 模块，`model` 承载 Provider & Model Gateway，`tool` 承载 Tool Runtime + Approval，`observability` 承载 Observability + Eval |
| 审计事实源多头 | 审计链路责任不清 | `audit_log` / `AuditRecord` 归 Governance & Billing，其他模块通过 audit port 追加 |
| preflight 只能检测存在性 | 不能早期发现文档漂移 | 增加 drift detector：旧切片编号、旧并行口径、旧技术栈标题、旧 observability 命名、tool/approval 模块拆分 |
| 前端设计系统缺位 | S5 继续新增页面会放大“能用但不企业级”的视觉和交互债务 | 新增 `docs/45-details/03-frontend-console-design-system.md`，并回写上下文加载、控制面、团队拓扑和交付计划 |

## 当前机器摘要状态

- `docs/_generated/context-pack.json` 已包含 `docs/01-document-system.md`。
- `context-pack` 已包含 `document_engineering_ready` gate。
- `context-pack` 和 `repo-preflight` 已包含 `ai-platform-telemetry`。
- `repo-preflight` 已输出 `drift_signals`，当前为空。

## Gate 建议

| Gate | 建议状态 | 原因 |
| --- | --- | --- |
| Document Engineering Ready | pass | 多角色 blocking 已整改，文档守卫脚本已建立 |
| Harness Ready | agent-approved | 控制面已增强，后续以脚本和 agent 复核闭环 |
| Design Review Passed | agent-approved | 架构和交付主要偏差已收敛；原则变更才需要人工介入 |
| Environment Ready | pass | S1 已通过 `deploy/scripts/healthcheck.sh` 真实依赖检查 |
| Backend Baseline Ready | pass | S2 已通过 Maven reactor 和 `BackendMavenBaselineTest` |
| Build Ready | pass-for-s4 | S4 已形成 Provider Catalog、RBAC/menu、真实模型调用和页面正式操作证据 |
| Auto-Flow | controlled-agent-flow-for-s5-model-gateway | agent 先完成 S4.5 前端控制台设计系统加固，再按 S5.1 统一模型调用切片合同继续推进，不得扩散知识库、tool、数据库知识源或完整观测范围 |

## 唯一下一步

运行 `scripts/harness_doc_guard.py`、`deploy/scripts/healthcheck.sh`、`mvn -f backend/pom.xml test`、`pnpm --dir frontend/console-web test` 和 `pnpm --dir frontend/console-web build` 保持 S4 证据有效；前端设计系统加固通过后启动 S5.1 统一模型调用。
