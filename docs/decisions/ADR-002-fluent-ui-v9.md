# ADR-002：前端只使用 Fluent UI v9

状态：已接受
日期：2026-07-17

## 决策

管理控制台只使用 Fluent UI v9 作为组件和图标系统。品牌表达通过 Fluent theme 与语义 token 实现，不引入 Ant Design、MUI、shadcn 或第二套基础组件库。

## 原因

现有代码已经采用 Fluent UI v9，继续深化可减少迁移成本、可访问性分叉和视觉漂移。企业控制台需要稳定的表格、表单、Dialog、导航、状态与键盘行为，统一系统比页面级“风格创新”更重要。

## 约束

- `FluentProvider` 是主题入口；业务组件不得直接拼接另一套 reset/theme。
- 颜色、间距、圆角、阴影和状态使用语义 token。
- 图标来自 Fluent icons；图标按钮有 Tooltip 与 accessible name。
- 原生控件必须封装并证明 Fluent 无等价能力、视觉一致且 WCAG 2.2 AA。
- `design-taste-frontend` 用于 redesign audit、anti-slop 和适用页面 pre-flight；不把 landing-page 布局规则机械套到 dashboard、数据表或复杂工作流。
- 禁止渐变、glassmorphism、营销 hero、emoji 图标、随意 pill 和装饰性 hover 位移。

## 后果

设计选择会更克制且一致；品牌差异依赖 token、信息架构、内容和任务体验，而不是混用组件库。任何引入第二设计系统的变更是 blocker，除非新 ADR 证明不可替代性、迁移成本和可访问性影响。
