# AI Platform 文档索引

Stage: route / document-system / discovery / prd / harness / architecture / planning  
Category: entrypoint  
Status: current  
Review Status: agent-reviewed  
Reviewers: product owner, architect, engineering lead  
Approval: agent-approved  
Inputs: 用户产品愿景、仓库预检、公开竞品资料  
Outputs: 阶段文档导航、当前 gate、下一步  
Depends On: README.md  
Load When: always-entry  
Next: 先完成前端控制台企业级设计系统加固，再启动 S5.1 统一模型调用、trace 和 usage 基线；产品原则或架构原则变化时再进入人工决策

## 当前状态

- 项目态：greenfield。
- 交付目标：production-targeted。
- 当前阶段：S1/S2/S2.1/S3/S4 已完成，S4.4 完整 IAM 平台管理闭环已补齐，进入 S5 Model Gateway 的最小切片准备。
- 当前 gate：`s5-model-gateway-entry`。
- 下一步唯一动作：完成前端控制台企业级设计系统加固并保持真实页面 smoke 证据，然后启动 S5.1 统一模型调用、trace 和 usage 基线；不得把知识库、tool、数据库知识源或完整观测大屏并入 S5.1。

## 文档顺序

| 顺序 | 路径 | 类别 | 状态 | 评审 | 用途 | 上游输入 | 下游输出 | load-when | 优先级 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 00 | `docs/00-index.md` | entrypoint | current | agent-reviewed | 文档导航、阶段状态、加载规则摘要 | 仓库事实 | 当前任务加载路径 | always-entry | P0 |
| 01 | `docs/01-document-system.md` | stage-main | current | agent-reviewed | 文档分层、编号规则、写回目标、防扩散规则 | 文档体系现状 | 后续任务加载和回写秩序 | document-system-review/before-implementation | P0 |
| 10 | `docs/10-discovery-brief.md` | stage-main | draft | not-reviewed | 竞品分析、产品机会、定位 | 用户愿景、竞品事实源 | PRD | discovery/prd | P0 |
| 20 | `docs/20-prd-and-scope.md` | stage-main | draft | not-reviewed | 用户、范围、验收、非目标 | discovery | 架构方案 | prd/architecture | P0 |
| 30 | `docs/30-project-harness.md` | stage-main | current | agent-reviewed | 项目研发控制面语义母本 | repo facts、PRD | context / control surface | before-architecture | P0 |
| 31 | `docs/31-context-loading.md` | stage-main | current | agent-reviewed | 上下文最小加载规则 | harness | 实施/质量加载策略 | before-implementation | P0 |
| 32 | `docs/32-control-surface.md` | stage-main | current | agent-reviewed | 门禁、规则、脚本和 manual gate | harness | 质量和实施控制 | before-implementation | P0 |
| 33 | `docs/33-team-topology.md` | stage-main | current | agent-reviewed | 五角色团队拓扑、职责边界、切片参与规则 | harness、delivery plan | 实施协同和角色复核 | team-coordination/implementation-planning | P0 |
| 40 | `docs/40-architecture-and-tech-spec.md` | stage-main | current | agent-reviewed | 架构设计和技术方案 | PRD、harness | 模块设计、交付计划 | architecture/planning | P0 |
| 45 | `docs/45-module-and-service-design.md` | stage-main | current | agent-reviewed | 模块与服务详细设计 | tech spec | delivery plan | planning/implementation | P0 |
| 45-details-index | `docs/45-details/00-index.md` | design-detail-index | current | agent-reviewed | 详细设计按需加载矩阵 | module design | 单模块详细设计 | module-detail | P0 |
| 45-details-backend | `docs/45-details/01-backend-code-organization.md` | design-detail | current | agent-reviewed | 后端 Maven 多模块和 DDD 代码组织规范 | module design | 后端模块开发 | backend-module-development | P0 |
| 45-details-infra | `docs/45-details/02-platform-infrastructure-design.md` | design-detail | current | agent-reviewed | 平台基础设施模块边界和依赖规则 | module design | 基础设施模块开发 | platform-infrastructure/backend-module-development | P0 |
| 45-details-frontend | `docs/45-details/03-frontend-console-design-system.md` | design-detail | current | agent-reviewed | 企业级控制台设计系统、页面布局、交互状态和视觉验收标准 | team topology、frontend facts | 前端页面开发和 S5 UI 准入 | frontend-console-design/frontend-page-development | P0 |
| 45-details-provider-model | `docs/45-details/04-provider-model-gateway-design.md` | design-detail | current | agent-reviewed | Provider Catalog、模型目录、API Key 加密和模型能力校验 | module design、delivery plan | S4/S5 provider model gateway 实现 | provider-model-gateway-development | P0 |
| 50 | `docs/50-delivery-plan.md` | stage-main | current | agent-reviewed | 研发实施路径和切片合同 | tech spec、module design | implementation notes | planning | P0 |
| 60 | `docs/60-implementation-notes.md` | stage-main | current | agent-reviewed | S1/S2/S3/S4 实施证据、当前状态和下一步 | delivery plan、deploy、backend、frontend | quality / S5 prep | implementation/quality/S5-prep | P0 |
| facts-01 | `docs/facts/repo-preflight.md` | facts | current | not-reviewed | 仓库当前事实 | 文件系统和 git 状态 | harness | always-entry | P0 |
| facts-02 | `docs/facts/market-sources.md` | facts | current | not-reviewed | 竞品资料来源和摘要 | 公开官方文档 | discovery | discovery/prd | P1 |
| facts-03 | `docs/facts/environment-readiness.md` | facts | current | not-reviewed | 研发环境依赖和 runtime gate | deploy | implementation preflight | environment-setup | P0 |
| facts-04 | `docs/facts/command-catalog.md` | facts | current | not-reviewed | 可复用命令入口 | scripts、deploy | implementation / quality | before-implementation | P0 |
| facts-05 | `docs/facts/framework-official-research.md` | facts | current | not-reviewed | Spring AI、Sa-Token、MyBatis-Plus 官方资料校准结论 | 官方文档 | architecture / backend module design | framework-integration/backend-module-development | P0 |
| facts-06 | `docs/facts/cache-official-research.md` | facts | current | not-reviewed | Redisson、Caffeine 二级缓存官方资料校准结论 | 官方文档 | architecture / backend module design | cache-design/backend-module-development | P0 |
| facts-07 | `docs/facts/iam-rbac-official-research.md` | facts | current | not-reviewed | 多租户 IAM、RBAC、菜单和前端授权渲染官方资料校准结论 | 官方文档 | Tenant & IAM / frontend menu design | iam-rbac-development/frontend-menu-development | P0 |
| evidence-01 | `docs/evidence/gate-verdicts.md` | evidence | current | agent-reviewed | 阶段门禁结论 | 全部阶段文档 | 下一步判断 | always-entry | P0 |
| evidence-02 | `docs/evidence/deviation-log.md` | evidence | current | not-reviewed | 偏差和漂移记录 | 执行过程 | 回写控制面 | only-when-drift | P1 |
| evidence-03 | `docs/evidence/team-startup-review-2026-05-10.md` | evidence | current | agent-reviewed | 5 角色团队启动复核 | 团队拓扑、交付计划 | 实施协同 | team-coordination/evidence | P0 |
| evidence-04 | `docs/evidence/s1-s2-implementation-evidence-2026-05-10.md` | evidence | current | agent-reviewed | S1/S2 真实环境和后端基线证据 | deploy、backend、Maven test | S3 准入判断 | implementation/quality/S3-prep | P0 |
| evidence-05 | `docs/evidence/s3-auth-slice-evidence-2026-05-10.md` | evidence | current | agent-reviewed | S3 前后端真实运行证据 | backend、frontend、deploy、UI smoke | S4 准入判断 | implementation/quality/S4-prep | P0 |
| evidence-06 | `docs/evidence/s4-provider-catalog-evidence-2026-05-10.md` | evidence | current | agent-reviewed | S4 provider catalog、RBAC/menu、真实模型调用和页面操作证据 | backend、frontend、deploy、UI smoke | S5 准入判断 | implementation/quality/S5-prep | P0 |

## 未来阶段入口

不创建空文档。进入对应阶段时再创建：

- `docs/70-quality-report.md`
- `docs/80-release-and-rollback.md`
- `docs/90-retrospective.md`

## Gate 摘要

- Route Ready: pass。
- Document Engineering Ready: pass。
- Team Topology Ready: pass。
- Environment Ready: pass。
- Backend Baseline Ready: pass。
- PRD Ready: pending-review。
- Harness Ready: agent-approved。
- Design Review Passed: agent-approved。
- Build Ready: pass-for-s4。
- Quality Gate Exit: pass-for-s4。
