# 01 文档体系与防扩散规则

Stage: document-system  
Category: stage-main  
Status: current  
Review Status: agent-reviewed  
Reviewers: architect, engineering lead, QA owner  
Approval: agent-approved  
Inputs: `docs/00-index.md`, `docs/31-context-loading.md`, `docs/32-control-surface.md`  
Outputs: 文档分层、编号规则、写回目标、防扩散规则  
Depends On: `docs/00-index.md`  
Load When: document-system-review/before-implementation  
Next: `docs/30-project-harness.md`, `docs/31-context-loading.md`, `docs/32-control-surface.md`

## 目标

本文件定义项目文档系统的秩序，防止后续任务把架构、模块、实施、证据和临时分析混写到同一批文档里，导致代码和文档长期漂移。

## 文档分层

| 层级 | 文件 | 职责 | 禁止承载 |
| --- | --- | --- | --- |
| 入口 | `00-index.md` | 当前阶段、gate、文档导航、唯一下一步 | 详细架构、长评审结论 |
| 文档系统 | `01-document-system.md` | 编号规则、写回目标、防扩散规则 | 业务架构细节 |
| 产品定义 | `10/20` | 竞品、PRD、范围、验收 | 代码目录和实现任务 |
| Harness | `30/31/32` | 事实源、上下文加载、控制面和门禁 | 业务详细设计 |
| 架构 | `40` | 总体架构、技术选型、ADR、跨模块约束 | 单模块完整开发步骤 |
| 模块设计 | `45` | 模块总览、共性规则、跨模块事件 | 每个模块的完整细节 |
| 详细设计 | `45-details/*` | 后端组织、平台基础设施、单模块细则 | 总体 gate 状态 |
| 计划 | `50` | 切片合同、顺序、验收矩阵、owner | 原始命令长输出 |
| 实施记录 | `60` | 当前实现窗口、变更摘要、证据索引 | 长期累计大日志 |
| 质量报告 | `70` | 当前质量 gate、失败摘要、修复入口 | 完整终端输出堆积 |
| 发布 | `80` | 发布条件、回滚、监控和残余风险 | 实现过程流水账 |
| 复盘 | `90` | 经验、规则回写、后续改进 | 新需求和新范围 |
| Facts | `facts/*` | 外部资料、仓库事实、环境事实 | 设计结论的唯一载体 |
| Evidence | `evidence/*` | gate、偏差、评审、切片证据胶囊 | 新架构规则 |

## 编号规则

- 主阶段文档使用 `00/01/10/20/30/31/32/40/45/50/60/70/80/90`。
- 详细设计使用 `45-details/NN-topic.md`。
- 事实文档使用 `facts/<short-topic>.md`。
- 证据文档使用 `evidence/<short-topic>.md` 或 `evidence/s<slice>-<short-topic>-YYYY-MM-DD.md`。
- 新增主阶段文档必须更新 `00-index.md`、`31-context-loading.md`、`scripts/generate_context_pack.py`。
- 新增详细设计必须更新 `45-details/00-index.md` 和 context pack。

## 写回目标

| 变更类型 | 必须写回 | 可按需写回 |
| --- | --- | --- |
| 新技术栈 / 框架能力 | `facts/*`, `40`, `32`, `45-details/*`, context pack | `45`, `50`, `AGENTS.md` |
| 新平台基础设施模块 | `45-details/02-platform-infrastructure-design.md`, `40`, `32`, `50`, context pack | `README.md`, `30` |
| 新业务 bounded context | `45`, 对应 `45-details/*`, `50` | `40`, `32` |
| 新切片或顺序调整 | `50`, `00`, gate evidence, context pack | `40` handoff table |
| 新门禁或红线 | `32`, `evidence/gate-verdicts.md`, context pack | `AGENTS.md` |
| 运行证据 | `evidence/*`, `60/70` 索引 | `50` 只写命令入口，不粘贴长输出 |
| 偏差和整改 | `evidence/deviation-log.md`, `32`, context pack | `90` 复盘 |

## 防扩散红线

- 不为每个临时问题新增一个主阶段文档。
- 不把 implementation note 写进 `40/45`。
- 不把架构规则只写在对话或 evidence，不回写 `32`。
- 不把长命令输出粘贴到 `50/60/70` 主文档；使用 evidence 胶囊。
- 不创建第二套 stage 文档目录。
- 不让 `40` 和 `50` 各自定义不同切片编号。
- 不让 facts 文档直接成为设计约束；facts 必须回写到 `32/40/45-details/50`。
- 不让 context pack 漏掉新增的 P0 文档。

## 任务加载规则

- 每个任务先读 `docs/_generated/context-pack.json` 和 `docs/00-index.md`。
- 架构和文档体系任务再读 `01/31/32/40/45/50`。
- 单个业务模块开发默认读 `45-details/00-index.md`、`45-details/01-backend-code-organization.md`、当前模块细则和当前切片。
- 平台基础设施模块开发默认读 `45-details/02-platform-infrastructure-design.md`。
- facts 只在涉及对应框架、市场、环境或命令事实时读取。

## 证据胶囊规则

当进入实现后：

- `60` 只保留当前窗口、变更摘要、证据索引和恢复点。
- `70` 只保留当前质量 gate 摘要、失败分类、修复入口和验证结论。
- 每个切片的关键运行证据拆到 `evidence/s<slice>-<topic>-YYYY-MM-DD.md`。
- 单个 evidence 文件不粘贴大段原始日志，只保留命令、关键输出、失败摘要和复现入口。

## 当前结论

当前文档体系已经有入口、harness、架构、模块设计、详细设计、计划、facts、evidence、机器摘要和文档守卫脚本。常规文档一致性由 agent 复审和 `scripts/harness_doc_guard.py` 闭环，不再要求频繁人工复审。

需要人工介入的情况只包括：

- 修改产品原则、架构原则、技术栈原则、模块所有权原则。
- 修改统一完成标准、质量门禁、安全/合规风险接受标准。
- 接受无法由脚本或真实运行证据证明的风险。

当前文档工程完成态：

- `docs/_generated/context-pack.json` 和 `docs/_generated/repo-preflight.json` 是后续 agent 的机器入口。
- `scripts/harness_doc_guard.py` 是文档工程完成守卫。
- `docs/evidence/overall-design-review-2026-05-10.md` 是多角色复审证据。
- S1/S2/S3/S4 已完成，证据见 `docs/60-implementation-notes.md`、`docs/evidence/s1-s2-implementation-evidence-2026-05-10.md`、`docs/evidence/s3-auth-slice-evidence-2026-05-10.md` 和 `docs/evidence/s4-provider-catalog-evidence-2026-05-10.md`。
- 部署模式、前端方案和租户隔离等级已确认。
- `Build Ready` 对 S4 Provider Catalog 为 `pass-for-s4`，后续进入 S5.1 统一模型调用。
