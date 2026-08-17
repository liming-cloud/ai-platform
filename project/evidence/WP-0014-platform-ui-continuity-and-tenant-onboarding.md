# WP-0014 平台界面连续性与租户入驻整改

## 结论

人工拒绝指出的三项可复现缺陷已经在同一纵向切片整改：初始租户管理员候选不再包含 `platform.root`；租户向导可原地建立并自动选中专用账户；Fluent Portal 不再用不透明根背景遮蔽整页。统一管理壳、平台账户目录和租户三步向导完成一轮视觉重构，M6 继续进行中，不构成人工接受，也不激活 M7。

用户已确认平台管理员与租户管理员必须分离，并授权在组件能力不足时更换 Fluent UI 或整个前端技术栈。本轮审计证明白屏来自自定义 `.foundation-provider` 全屏背景被 Dialog Portal 复制，不是 Fluent 组件能力缺失；表单、焦点、键盘和 Dialog 语义仍可复用。因此当前保留 React、Vite 和 Fluent UI React v9，只有后续多尺寸基准仍不达标时才重新评估迁移。

## 产品与实现边界

- `JdbcPlatformAccountCandidateDirectory` 只返回活动且没有活动 `PLATFORM_ADMINISTRATOR` 绑定的账户，分页计数与列表使用同一资格谓词。
- 三步租户向导在第二步解释身份边界；没有候选时直接展开专用账户建立表单，建立结果自动成为当前选择。用户不输入或查看 `SubjectId`。
- 平台账户目录错误统一经 `presentApiError` 映射，页面不直接呈现后端 `message`、`code` 或追踪字段。
- `FoundationProvider` 把应用根背景移入 `.foundation-app-root`；Portal 仍获得主题，但不会获得 `100dvh` 不透明应用表面。
- 统一壳新增产品标识、单一图标族、导航层级、当前会话区、内容画布、移动端底部导航和稳定加载骨架；主操作使用近黑色，单一强调色为深青绿。
- 平台账户目录改为资源行、独立状态列和操作列；租户向导使用深色步骤轨道与浅色任务舞台，未引入新组件依赖。

## TDD 红绿证据

红灯：

```text
./mvnw -pl backend/contexts/organization-access -am \
  -Dtest=JdbcPlatformAccountCandidateDirectoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

结果：预期 1 个合格候选，旧 SQL 实际返回 3 个；`platform.root` 和停用账户被错误纳入。

cd frontend && npm test -- --run \
  apps/console-web/src/features/platform-governance/PlatformTenantLifecycleWorkspace.test.ts \
  apps/console-web/src/spa-information-boundary.test.ts

结果：2 项失败；旧向导没有 `client.createPlatformAccount`，旧 Provider 没有隔离的 `.foundation-app-root`。
```

绿灯：

```text
./mvnw -pl backend/contexts/organization-access -am \
  -Dtest=JdbcPlatformAccountCandidateDirectoryTest,PlatformAccountCandidateApplicationServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

结果：3 项通过，包含真实 PostgreSQL 18、Flyway 和候选资格事实。

cd frontend && npm run typecheck && npm run lint && npm run test && npm run build

结果：类型检查和 lint 通过；23 个测试文件、121 项测试通过；Vite 生产构建转换 2180 个模块。

make architecture-guard

结果：27 项架构门单元测试、后端结构门禁和前端产品门禁全部通过。

make validate

结果：全量后端测试、23 个前端测试文件共 121 项测试、前端生产构建、演示数据、真实模型、恢复、容量韧性和交付运维质量脚本全部通过，命令退出码为 0。
```

## 真实 HTTP 与运行基础设施

以基础 Compose 加浏览器验收覆盖层构建并运行最新 `control-plane` 和 `console-web`；PostgreSQL、Redis、MinIO、`control-plane`、`query-runtime` 和 `console-web` 均健康，`console-web` 同时保留 `13000` 与人工验收入口 `13443`。

真实浏览器会话协议 HTTP 回归携带同源、`Sec-Fetch-Site` 和 CSRF 防护值：

```text
GET /api/v1/platform/accounts/candidates
  ?purpose=TENANT_INITIAL_ADMINISTRATOR&q=&page=1&size=20

totalElements: 0
candidates: []
containsPlatformRoot: false
```

当前本地数据库已经存在上一轮人工操作建立的租户，但平台账户目录仍只有 `platform.root`；本轮没有提交账户、租户或生命周期写操作。

## Chrome 回归

在用户 Chrome 的已登录 `http://localhost:13443` 会话完成以下真实页面检查：

- 桌面视口为 `1920×873`，统一壳、平台账户目录、租户向导第一步、第二步空状态和原地账户表单均无横向溢出。
- 租户管理员候选页面正文不包含 `platform.root`，并明确说明平台管理员不会成为租户管理员候选。
- Dialog 打开时 `main.display = block`、`main.opacity = 1`、账户目录文本仍存在；Portal Provider 的 `min-height` 为 `0px`，背景遮罩后仍可见侧栏、页头和当前账户行。
- 从平台账户切换到平台租户，在 `0`、`16`、`50`、`100`、`250` ms 五个采样点中，内容画布始终可见且主区域始终包含文本，没有空白中间帧。
- 连续 Tab 后焦点落在导航按钮，Fluent 焦点轮廓可见。
- 只填写了未提交的租户名称和编码以进入第二步；没有点击“建立并选中”或“确认建立租户”。

当前 Chrome 控制扩展不提供物理视口调整接口，因此本轮没有把新版管理壳的 `1024×768`、`390×844` 或真实浏览器 200% 缩放虚报为已完成。对应 CSS 已实现三档布局并通过类型、lint、测试、构建及人工桌面审计，窄屏和 200% 仍是下一轮人工复核项。

## 代码图谱与技能证据

- 代码图谱探索覆盖 `PlatformTenantLifecycleWorkspace`、`PlatformAccountCandidateApplicationService`、`JdbcPlatformAccountCandidateDirectory`、`ConsoleApp`、`FoundationProvider` 和 `ProductShell`。
- 对 `JdbcPlatformAccountCandidateDirectory`、`PlatformTenantLifecycleWorkspace`、`FoundationProvider` 和 `ProductShell` 执行影响分析；据此把资格规则限制在组织访问候选目录，把 Portal 修复限制在设计系统根层，不改租户建立公共 HTTP 契约或可信 RAG 运行链。
- 当前运行环境为 Codex Desktop；显式使用 `design-taste-frontend` 完成审美审计、视觉方向、响应状态和预检，使用 `engineering-assistant:repo-context`、`engineering-assistant:codebase-design`、`engineering-assistant:diagnosing-bugs` 与 `engineering-assistant:tdd` 完成仓库、设计、复现和红绿实现。
- Design Taste Frontend 对实现产生的实质影响是：统一图标族、单一强调色、控制圆角层级、避免装饰性卡片堆叠、补全加载几何和减少动态效果；企业表单仍按官方 Fluent 无障碍模式实现。
- 本轮没有产生 `StageRunResult`，阶段结果校验不适用。

## 后续门禁

- 在可调整 Chrome 物理视口的人工会话复核 `1024×768`、`390×844`、200% 缩放、键盘和减少动态效果。
- 继续按相同设计基准逐页整改运营、模型、数据、助手和可信问答，不因本切片通过而宣布全 SPA 已完成人工验收。
- M6 自动验收与人工明确接受前，`project/STATE.yaml` 继续保持 M6、WP-0014 进行中，M7 不激活。
