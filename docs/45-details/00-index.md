# 45-details 详细设计索引

Stage: module-design-detail  
Category: design-detail-index  
Status: current  
Review Status: agent-reviewed  
Reviewers: architect, engineering lead  
Approval: agent-approved  
Inputs: `docs/45-module-and-service-design.md`  
Outputs: 详细设计按需加载矩阵  
Depends On: `docs/31-context-loading.md`, `docs/45-module-and-service-design.md`  
Load When: module-detail/backend-module-development/frontend-console-design  
Next: `docs/45-details/01-backend-code-organization.md`

## 加载规则

后端模块开发默认只读：

1. `docs/_generated/context-pack.json`
2. `docs/50-delivery-plan.md` 中当前切片
3. `docs/45-details/00-index.md`
4. 当前模块对应详细设计
5. 必要时再读 `docs/32-control-surface.md`
6. 涉及 Spring AI、Sa-Token、MyBatis-Plus 配置或升级时，再读 `docs/facts/framework-official-research.md`
7. 涉及 Redisson、Caffeine 或二级缓存时，再读 `docs/facts/cache-official-research.md`

禁止为了开发单个后端模块默认全量读取 `docs/40-architecture-and-tech-spec.md` 和 `docs/45-module-and-service-design.md`。

前端控制台设计、页面开发或视觉质量修复默认只读：

1. `docs/_generated/context-pack.json`
2. `docs/50-delivery-plan.md` 中当前切片
3. `docs/45-details/00-index.md`
4. `docs/45-details/03-frontend-console-design-system.md`
5. 必要时再读 `docs/32-control-surface.md` 和 `docs/33-team-topology.md`

## 详细设计清单

| 文档 | 适用任务 | 默认读取 |
| --- | --- | --- |
| `docs/45-details/01-backend-code-organization.md` | Maven 多模块、DDD 包结构、模块开发规范、统一响应/异常/注释、TDD 流程 | 后端模块开发必读 |
| `docs/45-details/02-platform-infrastructure-design.md` | Spring AI、Sa-Token、MyBatis-Plus、Redisson/Caffeine、S3/MinIO 等平台基础设施模块边界 | 基础设施模块开发必读 |
| `docs/45-details/03-frontend-console-design-system.md` | 企业级控制台信息架构、设计 token、布局、表单、表格、权限 UI、运行状态和视觉验收 | 前端控制台设计和页面开发必读 |
| `docs/45-details/04-provider-model-gateway-design.md` | Provider Catalog、模型目录、API Key 加密、Spring AI 连接测试、模型参数校验 | Provider / Model Gateway 开发必读 |

## 未来拆分

当单个 bounded context 进入实现前，再按需新增：

- `05-knowledge-retrieval-design.md`
- `06-tool-runtime-design.md`
- `07-observability-evaluation-design.md`

新增后必须更新本索引和 `scripts/generate_context_pack.py`。
