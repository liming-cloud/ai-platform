# 31 上下文加载规则

Stage: harness-context  
Category: stage-main  
Status: current  
Review Status: agent-reviewed  
Reviewers: engineering lead  
Approval: agent-approved  
Inputs: `docs/30-project-harness.md`  
Outputs: 后续 agent 的最小上下文加载策略  
Depends On: `docs/00-index.md`, `docs/30-project-harness.md`  
Load When: before-implementation/quality/review  
Next: 先完成前端控制台企业级设计系统加固，再启动 S5.1 统一模型调用、trace 和 usage 基线

## 加载优先级

Tier 0：机器摘要

- `docs/_generated/context-pack.json`
- `docs/_generated/repo-preflight.json`
- `docs/evidence/gate-verdicts.md`
- `docs/00-index.md`
- `docs/01-document-system.md`，仅在文档体系复审、实现前控制面检查或新增文档时读取

Tier 1：当前阶段主文档

- document-system：`docs/01-document-system.md`
- discovery / prd：`docs/10-*`, `docs/20-*`
- architecture：`docs/40-*`
- module design：`docs/45-*`
- module detail：`docs/45-details/00-index.md` 和当前模块细则
- planning：`docs/50-*`
- implementation：`docs/60-*`
- quality：未来 `docs/70-*`

Tier 2：事实和证据

- `docs/facts/*`
- `docs/evidence/*`

Tier 3：代码和配置

- 只有进入实现或评审时读取具体源码、构建配置、CI、migration、测试。

## 按任务加载矩阵

| 任务类型 | 必读 | 按需 |
| --- | --- | --- |
| 文档体系复审 | `00`, `01`, `31`, `32`, `45-details/00-index`, `context-pack` | `40`, `45`, `50`, evidence |
| 产品范围评审 | `00`, `10`, `20`, `facts/market-sources` | `gate-verdicts` |
| 架构评审 | `00`, `20`, `30`, `40`, `45` | `32`, `facts/repo-preflight` |
| 研发计划评审 | `00`, `30`, `31`, `32`, `40`, `45`, `50` | `45-details/00-index`, `gate-verdicts` |
| 团队协同和角色复核 | `context-pack`, `33`, `50`, `32` | `30`, `45-details/00-index`, 当前切片证据 |
| 环境切片 | `context-pack`, `50`, `facts/environment-readiness`, `facts/command-catalog`, `deploy/README.md` | `deploy/docker-compose.yml` |
| S3 证据复核 | `context-pack`, `gate-verdicts`, `60`, `facts/command-catalog`, `docs/evidence/s3-auth-slice-evidence-2026-05-10.md` | `50`, `32`, `45-details/01-backend-code-organization` |
| S4 Provider Catalog | `context-pack`, `gate-verdicts`, `50`, `60`, `facts/command-catalog`, `45-details/01-backend-code-organization` | `40`, `45`, `45-details/02-platform-infrastructure-design`, `facts/framework-official-research` |
| 前端控制台设计系统 | `context-pack`, `docs/45-details/00-index.md`, `docs/45-details/03-frontend-console-design-system.md`, `33`, `50` | `32`, `60`, `frontend/console-web/src/App.vue`, `frontend/console-web/src/styles.css`, 当前页面测试 |
| 框架集成设计更新 | `context-pack`, `facts/framework-official-research`, `32`, `40`, `45-details/01-backend-code-organization` | 官方文档、`45`, `50` |
| 缓存设计更新 | `context-pack`, `facts/cache-official-research`, `32`, `40`, `45-details/01-backend-code-organization` | 官方文档、`45`, `50` |
| 平台基础设施模块开发 | `context-pack`, `50` 当前切片, `45-details/00-index`, `45-details/01-backend-code-organization`, `45-details/02-platform-infrastructure-design` | `facts/framework-official-research`, `facts/cache-official-research`, `32` |
| 后端业务模块开发 | `context-pack`, `50` 当前切片, `45-details/00-index`, `45-details/01-backend-code-organization` | `facts/framework-official-research`, `facts/cache-official-research`, `45-details/02-platform-infrastructure-design`, `32`, 当前模块设计、相关源码和测试 |
| 实现某个切片 | `context-pack`, `50`, 当前切片对应详细设计, `32` | 涉及前端页面时追加 `docs/45-details/03-frontend-console-design-system.md`、相关源码和测试 |
| 质量修复 | `context-pack`, `70` 或失败证据, 当前切片 `60` | 相关源码、测试、脚本 |
| 竞品更新 | `10`, `facts/market-sources` | 官方资料和最新来源 |

## 停止继续加载的条件

- 当前 blocker 已可由已加载资料解释。
- 当前任务只涉及单个切片或单个模块。
- 继续加载会重复已稳定文档。
- 机器摘要已经给出 gate 和下一步。
- 后端单模块开发已能由 `45-details/01-backend-code-organization.md` 和当前切片解释。

## 过期判断

以下情况必须重新生成 context pack：

- 新增或删除阶段主文档、`45-details/*`、`deploy/*`。
- 修改 `01-document-system.md`。
- 修改 `00/30/31/32/40/45/50/60`。
- 修改 `33-team-topology.md`。
- 新增构建脚本、CI、测试、应用代码。
- gate verdict 改变。

命令：

```bash
python3 scripts/harness_repo_preflight.py
python3 scripts/generate_context_pack.py
python3 scripts/harness_doc_guard.py
```
