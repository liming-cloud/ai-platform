# 偏差日志

Stage: evidence  
Category: evidence  
Status: current  
Review Status: not-reviewed  
Reviewers: engineering lead  
Approval: pending  
Inputs: 执行过程  
Outputs: 偏差、漂移和修复记录  
Depends On: `docs/32-control-surface.md`  
Load When: only-when-drift  
Next: 发现偏差时更新

## 当前偏差

| 日期 | 偏差 | 影响 | 处理 | 后续控制面 |
| --- | --- | --- | --- | --- |
| 2026-05-10 | 前端控制台已满足 S3/S4 smoke，但缺少独立企业级设计系统，现有样式偏最小可用页面 | S5.1 继续新增页面会放大视觉不一致、表单/表格密度不足、状态反馈不统一的问题 | 新增 `docs/45-details/03-frontend-console-design-system.md`，回写 `31/32/33/40/45/50/60`，并加固 `frontend/console-web` 样式 | 文档 guard、context-pack 和前端 test/build 必须识别并验证前端设计系统 |
| 2026-05-11 | 后端 DDD 领域内聚偏移：业务规则散落到 application service、repository port 内部 record、超大 repository impl，bootstrap 直连 Mapper/PO，model application 直接依赖平台 AI/密钥能力 | 后续 S5.2 若继续加字段和策略，会进一步扩大贫血模型，导致配额、计费、路由等核心业务不可维护，并让密钥、审计、调用生命周期继续停留在应用层 | 暂停 S5.2 功能推进，新增 `docs/evidence/ddd-architecture-drift-review-2026-05-11.md`、`docs/evidence/full-code-compliance-audit-2026-05-11.md` 和 `scripts/harness_ddd_guard.py`，当前 guard 结论为 blocked | 功能开发前必须先完成 DDD 恢复；恢复后将 `python3 scripts/harness_ddd_guard.py` 和对应 Java 架构测试纳入质量门禁 |

## 记录规则

出现以下任一情况必须新增记录：

- 文档结论与代码事实冲突。
- 未经评审直接进入编码。
- 为通过门禁引入 fake / in-memory / hardcoded 完成态。
- 忽略租户、权限、审计或 trace。
- 重复出现同类质量失败。

记录格式：

| 日期 | 偏差 | 影响 | 处理 | 后续控制面 |
| --- | --- | --- | --- | --- |
