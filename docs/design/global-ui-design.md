# 全局 UI 设计规范

- 状态：约束性设计规范
- 设计系统：Fluent UI v9
- 设计读：信任优先、低干扰、高信息密度的企业 SaaS 控制台

## 设计方向

- 设计方差：3/10。结构稳定、页面可预测，在任务关键处用明确层级和少量品牌特征。
- 动效强度：2/10。只为状态变化、上下文连续性和操作反馈服务。
- 信息密度：7/10。支持专业用户快速扫描，同时给错误解释、高风险确认和可恢复动作保留空间。
- 视觉语言：冷静、证据导向、精确；不模仿营销落地页，不用 AI 紫色渐变表达“智能”。

`design-taste-frontend` 用于 redesign 审计、anti-slop 和适用表面的 pre-flight；其 landing page 组合规则不直接套用到数据表、控制台、复杂表单和多步骤产品界面。

## 设计系统约束

- 只使用 Fluent UI v9 组件和图标。原生控件仅在 Fluent 无等价能力且经过可访问性封装时使用。
- 通过 `FluentProvider` 注入品牌主题和语义 token；业务组件禁止散落十六进制颜色和阴影常量，间距经 Fluent spacing/nudge token 或登记过的语义布局变量使用。
- 字体采用离线可用的 `Segoe UI Variable, Segoe UI, system-ui, sans-serif`；不依赖远程字体加载。
- 间距以 Fluent v9 的 `spacingHorizontal*` / `spacingVertical*` 为事实源，允许 `SNudge`、`MNudge` 等为紧凑控件提供 6px、10px 的受控微调；不得为了“严格 4px 网格”改坏组件原生节奏。
- 圆角保持克制：控件 4-6px，面板 8px；卡片不默认全圆角，状态标签才可使用 pill。
- 阴影只表达浮层层级；内容分组优先用间距、背景和细边框。
- 禁止渐变、glassmorphism、发光边框、装饰性巨型标题、emoji 图标、无意义圆角卡片海、悬浮位移动画。

## 语义 token

必须提供并在 light/dark/high-contrast 设计中保持含义：

| 类别 | Token 语义 |
|---|---|
| 背景 | canvas、surface、surface-subtle、surface-raised、overlay |
| 文字 | text-primary、text-secondary、text-muted、text-on-brand、text-danger |
| 边框 | border-subtle、border-default、border-strong、focus-ring |
| 品牌 | brand-rest、brand-hover、brand-pressed、brand-selected |
| 状态 | success、warning、danger、info、neutral，以及各自 subtle background |
| 数据 | data-1 到 data-8；颜色外必须有标签/形状/纹理冗余 |

对比度：正文不低于 4.5:1，大字和非文本 UI 不低于 3:1；焦点环在相邻背景上可见。

### 当前 Fluent v9 映射

业务代码只使用右列 Fluent token。品牌 ramp 和主题覆盖集中在 `frontend/console-web/src/theme.ts`；下表描述语义，不允许业务页面复制色值。

| 产品语义 | Fluent v9 token | 使用边界 |
|---|---|---|
| app canvas | `colorNeutralBackground2` | shell 和页面底色 |
| primary surface | `colorNeutralBackground1` | 表格、详情、表单和任务区 |
| subtle surface | `colorNeutralBackground3` | 表头、分组和非交互背景 |
| primary text | `colorNeutralForeground1` | 标题、关键事实和输入值 |
| secondary text | `colorNeutralForeground2` | 说明、正文和非主事实 |
| muted text | `colorNeutralForeground3` | 时间、ID、辅助 metadata，仍需达对比度 |
| default border | `colorNeutralStroke1` | 面板和控件边界 |
| subtle divider | `colorNeutralStroke2` | 行分隔和组内层次 |
| accessible border | `colorNeutralStrokeAccessible` | 文件区、空态和需明确边界的控件 |
| brand action | `colorBrandBackground` / `colorBrandForeground1` | 单一主操作、当前导航和品牌文字 |
| focus | Fluent focus tokens/组件默认 focus ring | 禁止用 box-shadow 覆盖或移除 |
| success/warning/danger/info | 对应 `colorStatus*` / `colorPalette*` token | 状态必须同时有文字或图标语义 |
| overlay | `colorBackgroundOverlay` | Dialog、Drawer、移动导航遮罩 |

品牌主轴采用 Fluent blue ramp，锚点为 `#0f6cbd`。该色值只在 theme 定义出现；页面不得建立第二 accent 或以渐变、发光扩大品牌存在感。

### 间距、圆角与层级 token

| 类别 | 允许值 | 规则 |
|---|---|---|
| 间距 | Fluent `XS/S/M/L/XL/XXL/XXXL` 与 `SNudge/MNudge` | 优先直接使用 Fluent token；跨组件布局通过少量 `--layout-space-*` 语义变量映射，固定触控尺寸、sticky offset 和 breakpoint 需单独命名并接受静态合同检查 |
| 控件圆角 | 4px | 原生封装控件和紧凑输入 |
| 默认圆角 | 6px | Fluent `borderRadiusMedium`，用于表面、消息和常规 Dialog 内容 |
| 大圆角 | 8px | Fluent `borderRadiusLarge`，仅大容器或认证面板 |
| pill | full | 只用于 Badge、离散状态或 Fluent 明确规定的控件 |
| elevation 0 | 无阴影 | 页面、表格、卡片和常规内容默认 |
| elevation 1 | Fluent `shadow4` | 认证状态面板、轻浮层 |
| elevation 2 | Fluent `shadow8` 及以上 | Dialog、Drawer、skip link 等系统浮层 |

z-index 只按系统层级使用：页面内容 < sticky topbar < sidebar < scrim < Dialog/Popover < skip link。业务组件不得用任意大数解决局部遮挡。

`frontend/console-web/src/styles.css` 当前登记 `--layout-space-compact/related/default/section/panel/page`，分别映射 Fluent spacing 或 nudge token；`--control-touch-target-min` 与 `--layout-detail-sticky-offset` 是有业务语义的固定几何量，不伪装成 spacing token。`frontend/console-web/src/__tests__/theme.test.ts` 静态约束这些映射及本轮触及的 master-detail、触控和登录标题规则。该合同只约束已登记区域，不声称一次性消灭仓库全部历史裸值。

主题在应用根 `FluentProvider` 一次注入，业务页面不自行切换主题：

| 用户偏好/平台能力 | 主题 | 验收要求 |
|---|---|---|
| 常规 light | enterprise light | 正文、表单、品牌按钮、错误和 focus 均达 AA |
| `prefers-color-scheme: dark` | enterprise dark | 层级与状态含义不依赖 light 色值 |
| `forced-colors: active` | Fluent high contrast | 系统色、边框、焦点、禁用和选中状态仍可辨认 |

主题实现存在不等于主题验收通过；三种模式都必须在最终 deploy 浏览器证据中复核。

## 布局与信息层级

- 桌面 shell：左侧任务导航 + 顶部租户/角色/全局状态 + 主内容；页面级操作位于标题区右侧。
- 页面标题使用普通页面尺度，不超过约 32px；标题、说明、状态、主操作和关键筛选形成首屏任务摘要。
- 数据治理页优先 master-detail、表格 + 详情抽屉或证据侧栏，不用一屏多组 KPI 卡片代替工作流。
- 主操作每个任务区域最多一个；次级操作进入工具栏或 overflow menu。
- 长表单按业务决策分段，危险设置靠后并使用明确 confirmation；不要把每个字段放入独立卡片。
- 内容最大宽度由任务决定：数据表可占满；阅读/配置表单控制行长；不使用一刀切居中窄列。

### 类型与密度

- 字体栈固定为 `Segoe UI Variable, Segoe UI, system-ui, sans-serif`，数字使用 tabular numerals；ID、trace 和 code 可使用 Fluent monospace token。
- 页面 H1 建议 25-32px，单页只出现一个；区域标题 18-22px；正文 13-14px；metadata 11-12px。控制台工作页不得使用 48px 以上营销标题；登录品牌标题是受控例外且最大 48px。
- 正文建议 1.5-1.65 行高；错误说明和长理由最大行宽约 65ch；表格值可折行但不能截断关键 ID、reason 或 action。
- 桌面导航项最小高 38px，表格与分页行保持可扫描密度；在触控或 390px 视口中所有交互目标至少 44x44px。
- 密集页面不以卡片数量表达模块。优先使用标题、间距、细分隔线、master-detail 和 evidence rail。

### 标准页面骨架

```text
Shell
  Topbar: 当前任务 + 登录名 + tenant + role + security state
  Page heading: H1 + 一句任务说明 + 可选状态 marker + 单一主操作
  Task toolbar: 筛选、搜索、分页/批量入口
  Primary work area: table/list/form/work queue
  Context area: detail drawer、evidence rail 或 recovery panel
  Persistent feedback: success/error/trace，靠近产生该结果的对象
```

任务首页例外使用“主任务队列 + 次级运行事实”，不使用等权 KPI 卡片墙。登录页可采用品牌色分栏，但仍只使用同一 Fluent theme、一个 accent 和真实产品文案。

## 组件契约

### 导航与上下文

- 当前租户、当前角色和安全上下文必须在 topbar 中始终可辨认；不得只依赖侧边栏或隐藏 tooltip。
- 390px 窄视口可换行显示租户、角色和安全状态，不用删除文字的绿点代替安全含义。
- 权限不足的导航默认隐藏；若有申请流程，可显示禁用项并解释所需权限。
- 导航图标使用 Fluent icon；图标不能替代文字表达核心任务。

### 任务首页

- 平台管理员和租户管理员首页使用“主任务队列 + 次级运行上下文”，不并排等权 KPI 卡片。
- 队列行固定为数量、任务名、影响说明和一个可选操作；操作只指向真实授权路由。
- 业务首页使用单一授权应用工作面，优先展示应用名称、说明、类型、状态和“开始使用”；无最近会话 API 时不制造“继续最近工作”。
- 数据时间、新鲜度、部分成功与 `traceId` 靠近任务面显示；错误时不保留可能过期的队列，并提供明确重试。
- 数据字段缺失与真实 `0` 必须区分；缺失显示“数据不可用”，不做通用数值求和或伪造零值。

### 表格与筛选

- 列宽按任务优先级分配；关键动作不可被裁切。窄视口采用局部横向滚动或行详情，不让整页溢出。
- 筛选有可见 label 或 `aria-label`，支持清除、键盘操作和结果计数；placeholder 不充当唯一标签。
- 行点击与行内按钮不冲突；图标按钮必须有 Tooltip 和 accessible name。
- 空、加载、错误和权限状态占据表格内容区并保留上下文，不通过空白推断。
- 服务端分页必须显示 total、当前页和 pageSize；改变 pageSize/筛选回到第一页，并接受服务端更正后的末页。
- 表头只在真实可排序时呈现排序 affordance；不能用纯视觉箭头暗示不存在的服务端排序。
- 1100px 及以下 master-detail 的详情转 Drawer 或堆叠区；行选择、详情标题和关闭后的焦点必须保持对应。

### 表单与 Dialog

- Label、帮助、校验和错误与控件关联；错误既有文本也有视觉状态。
- Dialog 支持 Esc/遮罩关闭，除非操作不可安全中断；关闭后焦点返回触发控件。
- 不可逆动作使用具体对象名、影响和理由确认；不能只问“是否确定”。
- 提交中防止重复操作，但保留取消/离开策略；结果必须在页面状态中可追踪。

表单字段顺序遵循“身份/对象 -> 业务配置 -> 约束与影响 -> reason/确认”。Label 始终在控件上方；placeholder 只能示例格式，不能替代 label。帮助文本说明输入如何影响业务，错误文本说明如何修复。

### 危险操作确认

停用租户/provider/model、替换角色权限、撤销邀请、审批、发布、人工重放等高风险操作统一使用以下流程：

1. 触发按钮使用“动词 + 对象”，并在行内保持可识别的 accessible name。
2. 打开确认时冻结 target ID、显示名称、tenant、当前状态和当前版本；列表刷新不得偷偷替换 target。
3. 明确列出直接影响、下游影响、是否可逆和恢复入口；不能只显示“是否确定”。
4. 显示 before/after 或新增/移除差异；未知影响写“尚无法确认”，不能省略。
5. reason 为必填并由后端再次校验；高风险提交按钮在有效 reason 前保持禁用。
6. 提交期间锁定同一操作并保留取消策略；不得因重复点击产生新幂等键。
7. 失败留在 Dialog 内，保留非秘密输入、target 和 traceId，提供精确重试；秘密字段关闭时清空。
8. 成功后刷新服务端事实并在持久反馈中显示结果和 traceId；若下一项操作需要新幂等键，Dialog 保持当前成功证据，完成权威 GET 对账后才清空快照并进入新意图。

Esc/遮罩关闭只用于尚未提交或可安全中断的状态。涉及不可取消服务端命令时，关闭界面不等于取消任务，必须向用户说明后台仍在运行。

提交后网络结果未知时，target、reason、规范化 payload 与幂等键视为一个不可拆分的意图快照。只有能够在不持久化秘密的前提下恢复同一非秘密快照和原键时，界面才可以暂时关闭；否则在权威结果或原键对账完成前必须阻止取消、Esc 与遮罩关闭。没有权威结果和 GET 对账时不得通过改字段、切对象或刷新局部表单创建第二个意图。

焦点是操作真相的一部分：确认框取消或未提交关闭后返回原触发控件；提交失败后焦点进入当前 Dialog 的稳定错误/提交区域，不能因内容重挂载逃到页面背后；成功导致原触发器消失时，焦点进入持久结果区或该对象仍存在的稳定标题。Dialog 必须使用 Fluent/Tabster 的模态激活与 focus trap，不以手写 `tabIndex` 循环替代。

秘密输入与可重试意图分开治理。秘密只允许在同一打开的表单意图中短暂保留，供应商类型切换、取消、关闭或成功后立即清空；恢复快照、结果消息、trace 和浏览器存储都不得包含秘密。允许跨取消/reload 恢复的快照必须使用版本化、tenant+user scoped、严格字段 allowlist、长度上限和损坏即删除的 session storage，只冻结原非秘密 command 与 requestId；恢复时秘密重新输入且 command 仍只读。任一业务字段（包括秘密值）改变都表示新意图，必须生成新 requestId；网络超时、408/425/429、5xx、幂等冲突或结构畸形的 SUCCESS 响应属于结果不确定，界面不得显示成功，也不得静默换键后重投。

### 证据与状态

- 状态 Badge 只用于离散状态，不把所有元数据做成 pill。
- 审批、审计、RAG 引用和运行调试使用统一证据侧栏：事实、来源、时间、actor、traceId、影响、可用动作。
- 敏感字段默认遮蔽，复制/显示需权限并产生审计。

证据区最小字段：`resource`、`tenant`、`actor/requester`、`source/version`、`status/result`、`time`、`traceId`。RAG 引用额外显示 knowledgeBaseId、documentId、chunkId、locator、score/indexVersion；模型调试额外显示 provider/model、invocation status、token、latency 和净化后的 error code。

详情披露采用字段 allowlist。不能把后端任意 JSON、异常 cause、消息 payload 或审计 detail 直接循环渲染到 UI。

## 页面状态

每个远程数据页面必须实现：initial、loading、success、empty、filtered-empty、permission-denied、error、degraded、refreshing。创建/更新页面另需 dirty、validating、submitting、conflict、success、failed。异步任务另需 queued、running、retrying、cancelled、succeeded、failed 和恢复动作。

远程状态遵循“最近一次请求拥有提交权”：页面切换、重试或卸载会取消旧请求；旧请求的成功或失败不得覆盖新页面。加载新资源时先清除旧数据或将其明确标为 stale，错误状态默认 fail-closed；动作错误与列表读取错误分开呈现，避免一次写失败抹去仍可信的读取事实。

HTTP 2xx 或 `header.code=SUCCESS` 不是可以直接渲染的成功。写操作还必须校验业务响应中的目标 ID、provider/resource 绑定、允许状态、版本推进、request/evidence digest，以及连通结果内部一致性；缺失、错绑或矛盾统一视为 `INVALID_RESPONSE`。此时服务端是否落库未知，UI 保留当前意图和不确定说明，先权威重读或按原幂等键精确重试，禁止用乐观本地状态制造成功。

### 状态文案与操作矩阵

| 状态 | 用户必须知道 | 推荐组件/位置 | 可用动作 |
|---|---|---|---|
| initial/idle | 尚未选择什么 | detail empty / inline hint | 选择对象或开始创建 |
| loading | 正在加载什么 | 与目标结构匹配的 skeleton/AsyncState | 可安全取消时提供取消 |
| refreshing | 旧事实是否仍可信 | 原内容 + 明确刷新状态 | 保持只读，避免重复写 |
| empty | 该范围尚无对象 | 内容区域 empty state | 有权限时创建第一项 |
| filtered-empty | 当前筛选无结果 | 表格内容区 | 清除筛选 |
| permission-denied | 缺什么权限或为何隐藏 | 403/上下文说明 | 返回、申请权限或联系管理员 |
| feature-disabled | 租户为何不可用 | 禁用导航说明/feature page | 返回可用任务 |
| partial/degraded | 哪些事实成功、哪些失败、数据时间 | MessageBar + 保留成功事实 | 重试失败部分 |
| error | 发生什么、影响、下一步、trace | 当前任务区域 | 精确重试或升级支持 |
| conflict | 哪个版本已变化 | Dialog/表单顶部 | 刷新比较后重新提交 |
| success | 服务端改变了什么、后续影响 | 持久 MessageBar/结果区 | 查看对象、继续下一步 |
| failed async | 阶段、失败码、预算、是否可恢复 | recovery panel/evidence rail | 重试、取消、人工恢复 |

Toast 只用于不需要后续行动的短暂通知。发布、审批、停用、恢复和权限变更必须使用页面内持久反馈，不能只显示自动消失的绿色提示。

## 响应式规则

- 1440/1280：完整导航与主次面板；避免固定列让操作区裁切。
- 1024：master-detail 使用单列堆叠，详情紧随主工作区；主任务不消失。
- 768：导航为可关闭 overlay，工具栏允许分行，表格局部滚动。
- 390：按任务优先级堆叠；触控目标至少 44×44px；高风险确认和核心提交仍可见。
- 200% 文本缩放下不得丢失信息或功能；不以 hover 作为唯一入口。

响应式不是把桌面页面缩小。每个页面设计时必须声明：主任务、可折叠信息、详情移动方式、表格局部滚动边界、主操作位置和焦点恢复对象。移动导航打开时主内容 `inert`，关闭时导航 `inert`；焦点始终留在当前可交互层。

## 无障碍

- 目标 WCAG 2.2 AA。语义结构、landmark、heading 层级和表单 label 正确。
- 全部功能可键盘完成；可见 focus、合理 tab order、skip link、Dialog focus trap/return。
- 动态状态使用合适 live region，避免重复播报；错误聚焦到摘要并链接字段。
- 尊重 `prefers-reduced-motion`；不使用闪烁或自动轮播。
- 图表和颜色状态提供文本/形状冗余；截图和装饰图正确设置替代文本策略。
- 触发网络请求的状态变化使用克制的 `aria-live="polite"`；阻断提交的错误摘要使用可聚焦容器并链接到第一个错误字段。
- 表格有可理解 accessible name；分页按钮说明目标页；图标按钮同时具备 Tooltip 和 aria-label。
- 会话消息、恢复结果和审批决定不得因 live region 重渲染而重复朗读整个历史。
- `forced-colors: active` 下不依赖 background color 区分选中、禁用、危险和成功；保留系统边框与焦点。

## 动效

- 仅使用 opacity、transform 等合成友好属性；常规过渡 120-200ms，复杂面板不超过 280ms。
- 列表 loading 不做连续脉冲干扰；状态变化可使用一次性淡入或高度过渡。
- 不使用 hover 位移、弹簧式装饰动画或大面积背景运动。

动效理由只允许反馈、层级、状态过渡和上下文连续性。当前移动导航 180ms transform 属于上下文过渡；Dialog 和 Drawer 进入/退出不得遮蔽焦点变化。`prefers-reduced-motion: reduce` 在页面运行期间发生变化时也必须立即切换，结束已运行的 Dialog/backdrop 动效，后续进入/退出不得启动正时长 WAAPI；恢复 `no-preference` 后重新启用受控状态过渡，不以延时模拟动画。

生产构建同时保留可持续体积余量：entry gzip 不超过 115 KiB、单个异步 chunk 不超过 32 KiB、CSS 不超过 8 KiB；全部 JS/CSS 的 216 KiB 硬上限中必须至少保留 4 KiB 未使用空间，即有效发布上限为 212 KiB。不得在只剩数十字节时继续宣称预算健康，也不得删除无障碍或错误恢复能力换取体积。

## 内容规范

- 用用户语言命名任务，如“发布应用”“恢复入库”，不直接暴露类名、topic 或数据库状态。
- 错误格式：发生了什么 → 影响什么 → 用户能做什么 → traceId/支持入口。
- 高风险按钮使用动词 + 对象，如“停用租户”，不用模糊“确认”。
- 日期、数量、成本和状态格式一致；过期/延迟事实要说明数据时间。

### 术语表

| 代码/领域事实 | 用户语言 | 禁止替代词 |
|---|---|---|
| `PENDING_APPROVAL` | 待审批 | 已创建、即将可用 |
| `ACTIVE` | 已启用/可用，按对象语义选择 | 成功 |
| `DEPRECATED` / disabled | 已停用 | 已删除 |
| publish preflight | 发布预检 | 自动检查一下 |
| dead letter | 需要人工恢复的失败运行 | 垃圾任务 |
| manual replay | 人工重放 | 再试一次 |
| traceId | 操作追踪号 | 技术编号 |
| filtered citation | 已过滤未授权引用数量 | 被隐藏的文档名称 |
| partial summary | 部分数据可用 | 正常 |

英文 code、ID、sourceContext 可作为证据辅助信息，但主标签和操作使用稳定中文任务词。文案不使用“赋能、无缝、颠覆、下一代”等空洞营销词。

## 设计系统可持续演进

- 新组件先证明 Fluent v9 无等价能力，再建立可访问封装、token 映射、状态矩阵和测试；禁止页面内复制一套自定义 primitive。
- token 变更必须在 light/dark/forced-colors、五视口和 200% 文本下做回归；品牌调整不改变状态语义。
- 公共模式优先沉淀为小接口深组件：AsyncState、Pagination、危险确认、EvidenceRail、MasterDetail，而不是复制页面 JSX。
- 设计评审按真实角色任务采样，不按组件截图采样。任何视觉优化都不能隐藏权限、错误、影响或恢复。
- route、field name、analytics/audit 语义和已有可访问性行为不静默修改；破坏性变化需要 ADR、迁移和验收矩阵更新。
- 每次发布保存 token 版本、依赖版本、截图基线、axe 结果和 reviewer；旧截图不能自动继承到新 SHA。

## Redesign pre-flight

适用页面进入验收前逐项确认：

- [ ] 使用 Fluent UI v9 与语义 token，无第二设计系统或散落视觉常量。
- [ ] 无渐变、glassmorphism、营销 hero、巨型装饰标题、emoji 图标和随意 pill。
- [ ] 主操作、信息层级和真实任务在首屏明确。
- [ ] loading/empty/error/degraded/permission 状态完整且有恢复路径。
- [ ] 服务端分页、筛选与最近请求提交权成立，不用客户端切片或迟到响应伪造事实。
- [ ] 危险操作冻结对象和影响、要求 reason、支持取消与失败精确重试，成功结果可追踪。
- [ ] 审批、审计、引用和调试详情只显示 allowlist 字段，秘密与越权资源名称不可达。
- [ ] 1440 到 390px 无关键动作裁切；键盘、焦点、Esc、缩放和 reduced-motion 正常。
- [ ] 所有图标按钮有 Fluent icon、Tooltip 和 accessible name。
- [ ] 页面没有用静态目标态、假数据或“即将上线”冒充交付。
- [ ] Playwright、axe、视觉截图与人工角色旅程绑定同一提交和 deploy 环境。

## P2 证据状态（2026-07-21）

| 检查 | 状态 | 证据边界 |
|---|---|---|
| Fluent v9 单设计系统、任务层级、无渐变/营销 hero/占位导航 | IMPLEMENTED_LOCAL | 源码与 `design-taste-frontend` 静态审计检查点；需固定 SHA 复核依赖和可见文案 |
| light/dark/high-contrast、移动导航、远程状态和危险动作确认 | IMPLEMENTED_LOCAL | 当前工作树有组件测试与实现；精确测试数由最终质量报告生成 |
| TypeScript lint、行为测试、production build、bundle budget | IMPLEMENTED_LOCAL | 实施检查点曾成功；必须在最终候选 SHA 连续重跑并记录 artifact hash |
| Playwright 套件可发现 | IMPLEMENTED_LOCAL | 只证明配置和测试可解析，不证明浏览器行为 |
| deploy 已登录三角色任务、axe、键盘、200% 缩放、390/768/1024/1280/1440 截图 | PENDING | 必须基于隔离 `deploy/docker-compose.yml` project，并绑定最终候选 SHA |
| light/dark/high-contrast 人工视觉与产品使用者复核 | PENDING | 需要逐角色逐任务记录发现、修复与复验，不能由静态 pre-flight 替代 |
