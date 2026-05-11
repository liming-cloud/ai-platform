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
