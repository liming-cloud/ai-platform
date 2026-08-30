# WP-0014 企业组件库选型决策与性能评估门槛

## 状态

- 结论：保留 Fluent UI React v9 作为唯一视觉组件系统。
- 范围：M6 全 SPA 产品界面重构、平台治理、租户治理、可信问答和后续专项数据网格。
- 依据：已接受的 ADR-0005、本地前端影响审计、官方组件文档与当前人工验收问题。
- 性质：选型证据，不修改 ADR-0005，不授权直接引入新的生产依赖。
- 设计参数：`DESIGN_VARIANCE=7`、`MOTION_INTENSITY=6`、`VISUAL_DENSITY=6`。

## 结论

Fluent UI React v9 仍是当前产品最合适的核心组件库，不进行全量替换。

本轮人工验收暴露的主要问题是信息架构、任务流、表单栅格、列表层级、空状态、角色边界和视觉完成度不足，不是 Fluent UI 缺少基础组件。当前前端已经广泛使用 Fluent 的表单、按钮、Dialog、MessageBar、图标、主题和可访问性语义。全量替换会同时重写设计 tokens、焦点管理、弹层、表单、状态反馈、测试选择器和响应式行为，却不能自动修复模型发现、租户角色、知识库生命周期和问答入口等产品缺口。

现代感与科技感通过语义 tokens、版式层级、精细边界、状态表达、数据密度和受控动效建立，不通过叠加第二套视觉组件库实现。正常动效环境允许 160 至 300 毫秒的任务反馈、面板切换和状态过渡；不使用循环动画、装饰性粒子、持续漂浮或影响阅读的视差。`prefers-reduced-motion` 下关闭非必要位移、缩放和关键帧动画。

数据表格采取“Fluent 视觉层 + 可替换数据状态引擎”的专项 seam。普通服务端分页列表继续使用 Fluent `Table` 或 `DataGrid`。只有达到本文量化触发条件后，才评估 TanStack Table、TanStack Virtual、MUI X 或 React Aria Virtualizer。任何生产依赖引入都必须先完成定向原型、基准测试、许可证核对和 ADR-0005 的窄范围补充决策。

## 本地影响事实

- 统计基线：实现提交 `d7cf86fdd`。
- 28 个 TSX 文件中有 25 个直接使用 Fluent UI，覆盖约 89% 的页面实现。
- 已有约 187 个 `Button`、61 个 `Field`、36 个 `Input`、16 个 `Select`、20 个 `Dialog` 和 24 个 `MessageBar` 调用。
- 当前 7 个文件包含 10 个原生表格，尚未形成独立数据网格依赖。
- `FoundationProvider`、`ProductShell` 和 `StatePanel` 已构成主题、产品壳和页面状态 seam。
- 当前视觉问题主要集中在约 2700 行页面 CSS、表格列约束、表单栅格、弹层层级和任务流表达，可以在 Fluent 体系内局部整改。
- 引入全套替代组件库会形成第二套 tokens、图标、焦点和弹层语义，并与 ADR-0005“只使用 Fluent UI React v9”的已接受决策冲突。

## 候选方案边界

| 方案 | 当前定位 | 适用边界 | 当前决定 |
| --- | --- | --- | --- |
| Fluent UI React v9 | 唯一视觉组件系统 | 企业控制台壳、导航、表单、Dialog、Drawer、MessageBar、普通表格、状态、主题和键盘语义 | 保留并深化 |
| TanStack Table / Virtual | 无视觉的数据状态和虚拟化引擎 | Fluent 普通表格经优化后仍无法满足复杂列状态、行列虚拟化或大规模交互性能 | 第一专项候选，未达门槛不引入 |
| MUI X Data Grid | 完整高级数据网格 | 明确需要分组、聚合、树数据、固定列、复杂编辑、Excel 导出等成套能力，且接受 Pro/Premium 许可证和独立视觉适配成本 | 仅专项评估，不作为全站替代 |
| React Aria / Virtualizer | 无头可访问性交互和虚拟化能力 | Fluent 缺少某项可访问性交互，且定向原型证明不会产生重复焦点模型或键盘语义冲突 | 仅行为补丁，不建立第二套视觉系统 |
| Ant Design | 中文后台、CRUD 和复杂表单能力成熟 | 新建且以通用 CRUD 为主、没有既有 Fluent 资产和 ADR 约束的产品 | 当前产品不替换 |
| Carbon Design System | IBM 风格、数据密集型企业界面 | 企业已经采用 Carbon 作为统一品牌和设计标准，并愿意整体迁移 tokens、图标和组件语义 | 当前产品不替换 |
| Mantine | 灵活的通用 React 组件与开发体验 | 中小型产品、工具型应用或需要快速自定义的绿地界面 | 当前产品不替换；其官方大表格示例同样借助 TanStack Virtual |
| AG Grid | 重型企业数据分析网格 | 百万级数据浏览、复杂透视、聚合、分组、单元格编辑和企业导出成为核心产品能力 | 不进入当前默认候选，仅在独立分析工作台立项时评估 |

Fluent UI v9 提供企业产品需要的主题、组件语义和可访问性基础；官方已有 React 19 兼容工作和虚拟化 DataGrid 扩展方向。[Fluent UI React 19 支持](https://github.com/microsoft/fluentui/pull/35145)、[Fluent UI v9 主题机制](https://learn.microsoft.com/en-us/shows/fluent-ui-insights/fluent-ui-insights-theming-in-v9)、[Fluent UI Contrib 虚拟化 DataGrid](https://github.com/microsoft/fluentui-contrib/blob/main/packages/react-data-grid-react-window-grid/README.md)

TanStack Table 是无头状态引擎，不提供第二套视觉组件；虚拟化由 TanStack Virtual 独立负责，而且官方明确说明虚拟化不能替代服务端分页、筛选和排序。[TanStack Table 概览](https://tanstack.com/table/latest/docs/overview)、[TanStack Table 虚拟化指南](https://tanstack.com/table/latest/docs/framework/react/guide/virtualization)、[客户端与服务端边界](https://tanstack.com/table/latest/docs/guide/client-side-vs-server-side)

MUI X Data Grid 适合高级数据网格，但虚拟化、可访问性和许可证层级必须一起评估，不能只比较演示效果。[MUI X 虚拟化](https://mui.com/x/react-data-grid/virtualization/)、[MUI X 可访问性](https://mui.com/x/react-data-grid/accessibility/)、[MUI X 许可证](https://mui.com/x/introduction/licensing/)

React Aria 适合补充无头可访问性交互和虚拟化，不适合作为当前 Fluent 视觉系统的并行替代。[React Aria](https://react-aria.adobe.com/)、[React Aria Table](https://react-aria.adobe.com/Table)、[React Aria Virtualizer](https://react-aria.adobe.com/Virtualizer)

Ant Design 的表格、表单和 SSR 能力适合通用后台，但替换现有 Fluent 资产仍会产生完整迁移成本。[Ant Design Table](https://ant.design/components/table/)、[Ant Design 服务端渲染](https://ant.design/docs/react/server-side-rendering/)、[Ant Design React 19 兼容说明](https://5x.ant.design/docs/react/v5-for-19/)

Carbon 提供成熟的企业 Data Table，但采用它意味着接受 Carbon 的完整视觉语言，并不构成对当前 Fluent 实现的局部性能升级。[Carbon Data Table](https://carbondesignsystem.com/components/data-table/code/)

Mantine 提供通用 `Table`，其官方数千行虚拟化示例需要配合 `@tanstack/react-virtual`，说明普通组件库与高性能数据引擎本来就是两个不同决策。[Mantine Table](https://mantine.dev/core/table/)

## 为何不全量替换

1. 当前缺陷不是组件缺失。模型供应链中断、角色归属错误、知识库流程缺失和问答入口不可达属于业务与领域问题。
2. 替换组件库不能改善服务端分页、权限隔离、受控错误响应或数据来源。
3. 现有 Fluent 覆盖率高，迁移会扩大回归面，并破坏已经具备的 Dialog 焦点恢复、表单标签、状态反馈和主题能力。
4. 没有可信的官方跨库统一性能基准能够证明 Ant、MUI、Carbon 或 Mantine 在本产品真实任务中必然优于 Fluent。
5. 当前列表数据采用服务端分页。资源总数很大不代表浏览器必须持有或渲染全部行，不能仅凭数据库总数引入虚拟化。
6. 第二套视觉系统会增加 tokens、图标、CSS、弹层层级和键盘模型的长期维护成本。
7. ADR-0005 已接受 Fluent 为唯一视觉组件系统。没有新的可复核失败证据，不建立替代 ADR。

## 专项数据网格的量化触发条件

资源总数本身不是触发条件。服务端存在十万条记录，但当前页面只渲染 20 或 50 行时，继续使用 Fluent 普通表格。

只有满足以下任一硬条件，才进入专项网格原型。

### 功能触发

- 单个已接受任务必须同时显示 100 列以上，或者必须进行横向列虚拟化。
- 单个任务必须在浏览器内连续浏览 10,000 行以上，服务端分页会破坏已接受的比较、选择或编辑任务。
- 必须提供树数据、行分组、聚合、透视、固定列、跨页选择、单元格编辑或 Excel 导出中的三项以上。
- Fluent `Table`、`DataGrid` 及 Fluent Contrib 虚拟化方案无法在保持键盘语义和当前视觉系统的前提下完成已接受业务任务。
- 普通服务端分页不能满足已接受的连续选择、批量编辑或实时监控需求，并已有领域与 API 契约支持该任务。

### 性能触发

在生产构建、真实数据形态和目标 Chrome 环境下，完成一次 Fluent 优化后仍出现以下任一结果：

- 单页 200 行或 2,000 个可见单元格时，交互样本的 p75 INP 高于 200 毫秒。
- 排序、筛选、展开、行选择或翻页的单次交互超过 500 毫秒。
- 连续滚动期间可见空白或主线程长任务反复超过 50 毫秒。
- DOM 中持续存在超过 500 个数据行节点，且性能分析证明节点数量是主要瓶颈。
- 390×844 或真实 200% 缩放下，为保持可操作性必须一次挂载大量不可见行或列。

INP 采用生产遥测的移动端和桌面端 p75 分开判断，目标为不高于 200 毫秒；200 至 500 毫秒为需整改，超过 500 毫秒为失败。[web.dev INP 官方阈值](https://web.dev/articles/inp)

## 专项网格选型顺序

1. 先优化服务端分页、稳定 React key、memo、列定义和单元格渲染。
2. 评估 Fluent `DataGrid` 或 Fluent Contrib 虚拟化方案。
3. 若需要复杂表格状态但仍要保持 Fluent DOM 和视觉，原型评估 TanStack Table；需要虚拟化时单独加入 TanStack Virtual。
4. 若已接受任务依赖成套企业网格能力，原型评估 MUI X，并完成许可证、主题隔离、可访问性和包体影响审查。
5. React Aria 只用于一个清晰、可测试的交互缺口，不与 Fluent 同时拥有同一控件的焦点和选择状态。
6. Ant、Carbon、Mantine 不作为数据网格性能补丁，也不作为本轮全站替换候选。

## 性能和包体门槛

专项网格原型必须满足：

- 网格依赖按路由懒加载，登录、可信问答和不含网格的治理路由初始资源增量为 0。
- 受影响路由的 JavaScript gzip 增量不超过 100 KiB；超过时必须提交业务收益、替代方案和真实交互数据供单独决策。
- 虚拟化后 DOM 数据行数量不超过当前可见行的 3 倍，且不能通过过大 overscan 掩盖滚动空白。
- 生产构建下，目标任务交互 p75 不高于 200 毫秒，且没有超过 500 毫秒的已知关键交互。
- 服务端分页、筛选和排序仍是权威事实；虚拟化不得把完整租户数据静默下载到浏览器。
- 不向普通用户暴露内部标识、厂商响应、权限标识或隐藏列中的敏感信息。
- 新依赖必须有明确删除 seam，不允许业务接口直接依赖网格库专属类型。

## 浏览器与无障碍验收门槛

### 1024×768

- 使用真实物理视口，不以 DevTools CSS override 代替。
- 页面正文无横向滚动。
- 表格可以在自身容器内横向滚动，但名称、状态和主操作必须可发现。
- 顶栏、导航、分页和主操作不重叠、不截断。
- Dialog 不超出可视区域，提交与取消操作无需滚动到不可达位置。

### 390×844

- 页面使用单列任务流，不复制桌面多列表格布局。
- 页面无横向滚动；宽表使用摘要行、详情路由或受控内部滚动。
- 主要触控目标不小于 44×44 CSS 像素。
- 固定顶栏、底部导航、键盘和安全区域不遮挡主操作。
- 错误、空状态和恢复动作在首屏可理解。

### 真实 200% 缩放

- 使用 Chrome 实际 200% 页面缩放续证。
- 文本不重叠、不裁剪，字段标签和错误说明保持关联。
- 主操作、分页、账户菜单和 Dialog 关闭操作均可见且可聚焦。
- 页面不得依赖缩小字体、隐藏关键列或横向拖动才能完成主要任务。

### 键盘

- 从浏览器地址栏进入页面后，全部交互可通过 Tab、Shift+Tab、Enter、Space、方向键和 Escape 完成。
- 焦点顺序与视觉顺序一致，焦点样式始终可见。
- Dialog 打开后焦点进入任务，关闭后返回触发控件。
- 数据网格若采用复合控件语义，方向键负责单元格导航，Tab 不被滥用为逐单元格遍历。
- 排序、选择、展开、分页和行操作均有可访问名称和状态。
- 不存在键盘陷阱，也不通过 hover 才显示唯一操作。

### `prefers-reduced-motion`

- `reduce` 下关闭非必要的 transform、缩放、视差和关键帧动画。
- 状态变化立即完成或只保留不超过 80 毫秒的非位移反馈。
- 骨架、进度和刷新不得无限闪烁或循环移动。
- 取消动画后，焦点、状态、完成与失败语义仍完整。
- 自动测试断言媒体查询存在，真实 Chrome 切换系统减少动态效果后续证。

### INP

- 生产遥测按移动端和桌面端分别统计 p75。
- p75 不高于 200 毫秒。
- 任何已知关键任务交互不超过 500 毫秒。
- 没有生产样本时，以生产构建、相同数据集和相同交互脚本至少运行 10 次，记录交互延迟分布；实验室结果只能作为发布门禁，不能替代后续真实用户遥测。

## 高设计方差与动效强度的边界

`DESIGN_VARIANCE=7` 允许更明确的空间层级、不对称任务摘要、分区色面、精细网格和更强的视觉节奏，但不改变以下约束：

- 只使用一个低饱和冷绿强调色。
- 不引入紫色渐变、玻璃拟态、装饰性光晕、Emoji 或卡片墙。
- 数据和表单优先于装饰。
- 每页最多一个主操作。
- 科技感服务于状态可信、任务可读和操作反馈，不制造不代表业务状态的视觉噪声。

`MOTION_INTENSITY=6` 允许在页面层级进入、用户触发的状态变化、任务切换、展开收起和成功反馈中使用短时位移或缩放。所有动效必须可中断、无循环，并在 `prefers-reduced-motion` 下安全退化。

## 重新评估全量替换的条件

只有同时满足以下条件，才创建替代 ADR：

1. 至少三个已接受的核心业务流程在完成 Fluent 局部整改和专项 seam 后仍无法达到功能或可访问性标准。
2. 失败由组件系统能力直接导致，而不是 API、领域模型、信息架构、CSS 或页面实现缺陷。
3. 候选库在同一生产构建、相同数据、相同设备和相同交互脚本中有可复核优势。
4. 已评估许可证、SSR、React 版本、包体、主题、图标、表单、Dialog、键盘和自动化测试迁移成本。
5. 已制定逐路由迁移、双系统最短共存期和可回滚方案。
6. 用户明确接受替代 ADR 和迁移成本。

## 最终决策

当前产品不替换 Fluent UI。

本阶段执行：

- Fluent UI v9 继续承担唯一视觉组件系统。
- 通过设计 tokens、壳层、任务导航、表单栅格、列表密度、Dialog 结构、状态面板和受控动效完成现代化重构。
- 普通数据列表继续使用服务端分页与 Fluent 表格。
- 达到量化触发条件后，优先评估 Fluent Contrib 或 TanStack Table/TanStack Virtual 的局部 seam。
- MUI X、React Aria 只在明确专项缺口下原型验证。
- Ant Design、Carbon 和 Mantine 不进入本轮全量替换。
- 未经新的已接受 ADR，不增加第二套视觉组件系统。
