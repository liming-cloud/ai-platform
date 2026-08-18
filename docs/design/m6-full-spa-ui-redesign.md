# M6 全 SPA 产品界面重构设计

## 状态与负责人

- 状态：实施中。
- 工作包：`WP-0014`。
- 负责人：产品、前端、平台运营、租户运营与可信问答体验负责人。
- 设计依据：已接受的 ADR-0067、ADR-0068、ADR-0069，以及 `docs/design/m6-openai-style-ui-brief.md`。

本文只记录 M6 已接受产品边界内的界面结构与迁移方式，不创建第二套角色、权限、路由或领域模型。

## Design Read

这是面向平台管理员与租户管理员的企业管理产品重构，采用可信、克制、高信息密度的视觉语言，继续使用 Fluent UI React v9 和系统无衬线字体。

- `DESIGN_VARIANCE=4`：信息结构稳定，允许少量不对称来建立层级。
- `MOTION_INTENSITY=2`：只保留状态反馈和必要过渡，减少动态效果时即时完成。
- `VISUAL_DENSITY=6`：适合日常运营，不用巨大数字或装饰性卡片占用画布。
- 重构模式：视觉与页面结构重构，保留现有 URL、任务信息架构、授权语义和 API 契约。

`design-taste-frontend` 不负责后台、数据表和多步向导的具体模式。本设计只采用其审计、反模板化、状态完整性、颜色与形状一致性和无障碍门禁；后台组件继续以 Fluent UI 为准。

## 现状审计

### 保留

- `ConsoleApp` 的规范 URL、服务端能力投影和懒加载工作区。
- `ProductShell` 作为唯一认证后产品壳。
- `StatePanel` 对加载、空、错误、拒绝、部分失败和过期状态的统一语义。
- 服务端分页、名称选择器、危险写入 Dialog 和写后重读。
- 单一深绿色强调色、浅色主题、系统字体和 Fluent 图标族。

### 退役

- 把整个页面包成带阴影和大圆角的画布卡片。
- 侧栏底部的泛化“已验证浏览器会话”和英文 `Enterprise` 装饰标签。
- 1024px 图标侧栏没有可见名称提示。
- 数据与治理任务分别拼装的一排 primary/subtle 按钮。
- 页面级 CSS 中相互漂移的硬编码背景、边框、圆角和阴影。
- 加载时脱离登录框架或最终页面结构的中间帧。
- 为了视觉“丰富”而新增通用卡片、伪指标、渐变、玻璃效果或循环动画。

## 产品上下文与不变量

### 平台运营

仅 `PLATFORM_ADMINISTRATOR` 使用。任务是运营总览、平台账户、租户生命周期和平台模型供应链。平台界面只呈现脱敏聚合与安全摘要，不读取租户正文、成员明细、凭据、Endpoint 或厂商原始响应。

### 租户运营

仅 `TENANT_ADMINISTRATOR` 使用。任务是成员、数据、模型、助手、评测、查询治理和可信问答。资源关系必须通过名称选择器建立，用户不输入内部标识。

### 可信问答

`TENANT_ADMINISTRATOR` 与 `TENANT_USER` 使用。界面以问题、回答、引用、拒答和反馈为单一阅读流，不暴露助手版本、追踪标识或诊断字段。

导航永远是已发布路由和服务端会话能力的交集。前端隐藏入口不是授权，直接 URL 与 API 继续由服务端拒绝。

## 选定架构

### 1. 语义视觉 tokens

`frontend/packages/design-system/src/styles/tokens.css` 拥有页面背景、侧栏、主表面、次级表面、边框、主文本、次文本、强调色、危险色、控件圆角、面板圆角和系统阴影。页面样式消费这些语义变量，不复制颜色值。

保留浅色主题。强调色只表达当前导航、焦点和主操作；成功、警告、危险色只表达真实业务状态。

### 2. `ProductShell`

- 桌面：232px 侧栏、56px 顶栏、最大 1440px 内容区。
- 中等视口：80px 图标侧栏，每个隐藏标签都有 Fluent Tooltip。
- 移动端：64px 顶栏和底部任务导航；内容使用单列，不依赖横向页面滚动。
- 顶栏拥有页面范围和账户菜单；侧栏只承担品牌与导航。
- 页面标题、任务摘要和内容在白色主表面中使用间距与分隔线建立层级，不再嵌套大画布卡片。
- 账户标签从会话三角色投影为用户语言，不展示主体、成员、租户或会话标识。

### 3. `ConsoleApp` 任务导航

数据任务与治理任务复用 `ConsoleApp` 内部的单一 `TaskNavigation`，以 Fluent `TabList` 呈现同一导航项下的互斥任务。它只接受标签、活动值和路由回调，不持有权限、业务数据或内部标识，因此不提升为新的设计系统公共模块。

### 4. 页面工作区

现有页面组件继续拥有业务表单、资源列表、分页和 Dialog。共享视觉通过语义 tokens 与少量结构选择器收敛；不建立通用 `Card`、`Table`、`Form` 包装器。只有真实任务面板使用边框和 12px 圆角，指标、摘要和资源列表优先使用留白与分隔线。

### 5. 状态生命周期

所有页面按以下状态表达：

```text
loading -> empty | content | partial | stale
content -> submitting -> reload | actionable error
any protected state -> session expired -> login
```

- loading 骨架匹配最终结构，不显示通用全页 Spinner。
- empty 说明当前事实和唯一下一步。
- partial 与 stale 保留仍可用内容并提供刷新。
- error 只说明任务、影响和恢复动作。
- 危险写入经单一 Dialog 确认，成功后重读服务端事实。

## 实施契约

### 接口

- `ProductShellProps` 保持现有公共字段，不新增业务状态或授权参数。实现内部增加桌面顶栏和 Tooltip。
- `AuthenticatedConsoleApp` 在现有会话对象中继续消费 `displayRoles`，只把它映射成“平台管理员”“租户管理员”或“普通用户”账户标签；未知值回退为“当前账户”。
- `TaskNavigation` 只存在于 `ConsoleApp.tsx`，接口为可见中文标签、活动任务值和路由回调。它不访问客户端、会话或业务数据。
- 语义变量固定为 `--foundation-canvas`、`--foundation-sidebar`、`--foundation-surface`、`--foundation-subtle-surface`、`--foundation-border`、`--foundation-text`、`--foundation-muted-text`、`--foundation-accent`、`--foundation-control-radius`、`--foundation-panel-radius` 和 `--foundation-elevation`。

### 行为

- 桌面账户菜单从侧栏底部移到 56px 顶栏；移动端仍由 64px 顶栏进入。
- 侧栏活动项使用次级表面和强调色，不使用浮起卡片阴影。
- 1024px 图标侧栏保留按钮可访问名称，并以 Tooltip 提供可见名称。
- 数据和治理任务使用 Tab 语义；URL、返回/前进和现有 `navigate` 行为保持不变。
- 页面加载、权限拒绝、路由不存在和会话失效继续复用 `StatePanel`，不新增第二条状态通路。

### 测试与回滚

- 先以静态结构测试约束 tokens、232px/56px 壳、Tooltip、角色标签和任务 Tab，再修改实现。
- 同一测试从红到绿后运行现有路由、导航、信息边界和工作区测试，再运行 typecheck、lint 和生产构建。
- 真实浏览器按角色访问每个导航项，检查 URL、页面标题、空/错/加载、Dialog 背景和键盘焦点。
- 本切片不修改数据库迁移或公共 API。代码回滚只涉及前端、设计记录和证据文件；本地零数据基线由独立 PostgreSQL 备份恢复，不与视觉回滚混合。

## 方案比较

### 方案 A：只重写 1700 余行页面 CSS

拒绝。文件数量少，但重复结构和状态语义继续散落，后续页面仍会重新发明工具栏、任务切换和容器层级。

### 方案 B：引入新的全套页面组件库

拒绝。当前只有壳、状态和两处任务切换具有真实重复；通用 `WorkspacePanel`、`ResourceTable` 或 `FormLayout` 会增加无调用方抽象，并与 Fluent 形成第二套系统。

### 方案 C：小 seam 加渐进迁移

接受。先统一 tokens、壳和任务导航，再按平台面、租户面、问答面迁移现有结构。每段都可通过真实路由、页面状态和浏览器回归独立观察。

## 失败模式与防护

| 风险 | 防护 |
| --- | --- |
| 全局 CSS 改动导致 Dialog Portal 白屏 | 根背景只绑定 `.foundation-app-root`；Portal 不继承全屏根容器 |
| 图标侧栏失去可理解名称 | 中等视口使用 Tooltip；按钮继续保留可访问名称 |
| 移动端导航挤压正文 | 底部导航独立滚动，正文宽度不得超过视口 |
| 页面切换出现空帧 | `Suspense` 继续使用匹配页面的 `StatePanel` 骨架 |
| 统一组件泄露内部字段 | 共享接口只接收用户语言、业务状态与回调 |
| 统计由浏览器合成 | 运营页只消费 ADR-0068 聚合接口 |
| 设计稿替代真实验收 | Chrome 或 Computer Use 证据必须绑定当前镜像和物理视口；CSS 断言只属于自动门禁 |

## 迁移切片

1. 登录与恢复框架：已通过红绿测试和 1920×873 当前镜像原型，正式吸收。
2. 语义 tokens、`ProductShell`、账户角色标签和任务导航。
3. 平台运营、账户、租户和模型供应链。
4. 租户运营、成员、数据、模型、助手和评测。
5. 可信问答、引用、拒答、反馈和查询治理。
6. 真实 PostgreSQL、Redis、MinIO、HTTP、Computer Use 及全量门禁。

每一段都必须保留 M6/WP-0014 进行中。自动测试和设计记录不能代替人工明确接受，也不得激活 M7。

## 验证合同

- 定向 Vitest 红绿、`npm --prefix frontend run typecheck`、`npm --prefix frontend run lint`。
- `npm --prefix frontend run build --workspace @foundation/console-web`。
- 真实 Compose 健康、同源 HTTP、PostgreSQL/Redis/MinIO 事实核对。
- 1920×873、1024×768、390×844、真实 200% 缩放、键盘和 `prefers-reduced-motion`。
- 全量 `make validate` 与 `make project-verify`。

当前 Chrome viewport override 未改变物理视口，1024×768 与 390×844 不能据此记为完成；Computer Use 又受 Mac 锁屏阻断。两项必须在 Mac 解锁后用真实桌面续证。
