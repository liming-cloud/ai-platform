# 团队角色启动复核

Stage: evidence  
Category: review-evidence  
Status: current  
Review Status: agent-reviewed  
Reviewers: architect, product engineer, senior backend engineer, senior frontend engineer, UI engineer  
Approval: agent-approved  
Inputs: `docs/33-team-topology.md`, `docs/50-delivery-plan.md`, `docs/32-control-surface.md`, `docs/20-prd-and-scope.md`, `deploy/*`  
Outputs: 5 角色启动结论、职责边界、当前下一步  
Depends On: `docs/33-team-topology.md`  
Load When: team-coordination/evidence  
Next: 先完成前端控制台企业级设计系统加固，再启动 S5.1 统一模型调用、trace 和 usage 基线

## 复核结论

固定团队已按 5 个角色启动：架构师、产品工程师、资深后端研发、资深前端工程师、UI 工程师。

结论：团队拓扑可用，职责边界清晰，无职责冲突类 blocking。S1/S2/S2.1/S3/S4 已完成，部署模式、前端方案和租户隔离等级已确认。用户指出前端视觉和规范未达到企业级控制台标准，因此当前先执行 S4.5 前端控制台设计系统加固，再启动 S5.1 统一模型调用；Build Ready 对 S4 已通过真实运行证据。

## 角色结论

| 角色 | Blocking | 当前下一步 |
| --- | --- | --- |
| 架构师 | S4 无 blocking；S5.1 可启动 | 维护 S5.1 model gateway 切片合同，避免知识库/tool/完整观测范围扩散 |
| 产品工程师 | PRD 仍 pending-review；业务优先级不扩散 | 保持 S5.1 验收只覆盖统一模型调用、trace 和 usage 基线 |
| 资深后端研发 | S4 pass | 保持 `deploy` healthcheck、Maven test、auth/API smoke 和 provider smoke 通过 |
| 资深前端工程师 | S4 控制台 pass；S4.5 需要加固设计系统 | 将现有 `frontend/console-web` 收敛到统一 token、布局、表单、表格、消息和状态规则 |
| UI 工程师 | S4 页面 pass；企业级视觉规范需补强 | 按 `docs/45-details/03-frontend-console-design-system.md` 复核密度、层级、响应式和状态表达 |

## 当前协同边界

- S1/S2/S3/S4 已完成。
- 当前先允许受控实施 S4.5 前端控制台设计系统加固，随后再实施 S5.1 统一模型调用。
- S5.1 必须继续使用私有化优先、本地 Docker Compose 依赖 + 前后端本机进程、Vue 3 + TypeScript + Vite、共享库共享表 + `tenant_id` 强隔离方案。
- 前端和 UI 必须先复用企业控制台设计系统；后续跟随冻结 API contract 开发统一调用操作入口，但不能产出脱离真实 API 的正式完成态。
- 产品工程师不频繁要求人工复审；只有产品原则、验收边界、业务优先级或商业策略变化才请求人工介入。
- 架构师发现 drift 时直接回写 deviation log 和 guard，不默认转人工复审。

## 后续触发条件

以下变化需要更新 `docs/33-team-topology.md` 并重新运行文档守卫：

- 固定团队角色变化。
- 角色职责边界变化。
- 切片主责或必复核角色变化。
- 人工介入边界变化。
- 完成态责任归属变化。
