# WP-0016 原型人工评审拒绝与视觉整改

## 人工结论

- 日期：2026-09-03。
- 评审人：用户。
- 结果：不通过。被评审全平台候选为 `18d9acdd168e4d2bcc81f379669fb8e19d7e3051`，本轮开始 HEAD 为 `ba0b62bf382fdd9dfd1d862ae4b9ad5639041d44`。
- 原始意见：说明、解释性内容过多；UI 存在不对齐、美观度过低问题；需要上调 `DESIGN_VARIANCE=4`、`MOTION_INTENSITY=2`、`VISUAL_DENSITY=6`。
- 此结论取代旧候选的“等待人工确认”，不是有条件通过；WP-0016 保持进行中，禁止推进 WP-0017 和修改生产前端。

## 可复核问题

代码图谱探索 `full-platform.js` 的 `contractStrip`、`renderWizard`、`renderDetail`、`renderRisk` 和 `decorateExistingPage` 后发现：

1. 目标路由、唯一任务、主要操作等评审信息进入产品正文，和标题、任务段落重复。
2. 详情页通过 `summaryFacts` 和 `factList` 重复呈现同组事实；侧栏使用大段通用设计说明。
3. 不同业务向导统一使用“名称或范围”“关联对象：员工服务手册”，没有真实任务区分。
4. 高风险确认用通用规则代替具体对象、影响、恢复方式和审计责任。

`contractStrip` 的影响分析覆盖 7 个符号，`renderInventoryPage` 覆盖 3 个符号，包括静态验证脚本。因此整改必须同步渲染和验证契约，不能仅隐藏文字来维持旧测试。

## 本轮方案与门禁

参数候选上调为 `7 / 5 / 7`，含义是提高任务布局区分度、克制的交互反馈和有效业务信息密度，不增加文字密度，不引入营销布局或第二套组件系统。具体要求见 `docs/prototypes/wp-0016/VISUAL-REMEDIATION.md`。

旧浏览器截图只属于旧候选；不得用于证明整改后的视觉质量。新候选仍须通过真实渲染、三档宽度、键盘、reduced-motion 与人工复验。上一轮本地浏览器访问被拒绝，本轮已请求重新授权；未获授权前不以替代通道绕过。

## 工程助手证据

运行环境为 Codex，能力来自已安装的 engineering-assistant 插件；直接路由并完整加载 `engineering-assistant:prototype` 与其 UI reference，使用 Charter、STATE、WP-0016 和六份引用 ADR。`design-taste-frontend` 的范围排除管理后台，本轮不采用其营销页面工作流。未产生 `StageRunResult`，阶段校验不适用。
