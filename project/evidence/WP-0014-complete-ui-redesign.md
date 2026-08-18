# WP-0014 全 SPA UI 重构证据

## 边界

- 里程碑与工作包：`M6 / WP-0014`，保持进行中。
- 唯一纵向切片：共享语义令牌、`ProductShell` 顶栏和响应式导航、`ConsoleApp` 二级任务导航、共享状态呈现，贯通登录、平台管理、租户管理和可信问答。
- 不变契约：三个角色、URL、鉴权、HTTP API 和数据库结构不变；不进入 `M7`。

## 可恢复的本地数据重置

- 精确目标：Compose 项目 `trusted-rag-foundation-v1` 的 PostgreSQL 数据库 `foundation_v1`。
- 重置前：1 个主体、1 个平台角色绑定、1 个租户、1 个成员、1 个租户角色绑定；Redis `dbsize=0`；MinIO 1 个空 bucket。
- 备份：`/tmp/wp0014-before-complete-ui-redesign-20260818.dump`，权限 `600`，大小 `243645` 字节。
- SHA-256：`005f61367afc5e52c5ac9c63aa3291151a78e43426a387cc5b4bb1fec2b0e5c6`。
- 恢复能力：`pg_restore --list` 已成功读取备份目录。
- 重置后：仅保留 `platform.root`、1 条活动 `PLATFORM_ADMINISTRATOR` 绑定和初始化切换记录；租户、成员、租户角色和权限均为 0。
- 服务恢复：control-plane、query、ingestion、PostgreSQL、Redis、MinIO、console-web 均为 healthy；控制面健康检查为 `UP`，控制台返回 HTTP 200。

## 红绿 TDD

### 红灯

命令：

```bash
cd frontend
npm test -- --run packages/design-system/src/shell/ProductShell.ui.test.ts apps/console-web/src/ConsoleApp.ui.test.ts
```

结果：2 个测试文件、4 项测试全部按预期失败，退出码为 1。失败分别证明旧实现缺少语义令牌和无渐变根背景、232px/80px/56px 壳层尺寸、Fluent `TabList` 任务导航以及中文会话角色标签。

### 绿灯

同一命令再次运行后，2 个测试文件、4 项测试全部通过，退出码为 0。随后执行：

```bash
cd frontend
npm run typecheck
npm run lint
```

TypeScript 项目构建和 ESLint 均通过。

### 登出回调回归

源码复核发现无租户工作区和权限拒绝工作区虽然显示账户菜单，但没有把 `onLogout` 传入 `ProductShell`。先扩展 `ConsoleApp.ui.test.ts` 固定三个壳层分支均须提供登出回调；红灯结果为 3 项中 1 项失败，实际只找到 1 处回调。修复后同一命令 3 项全部通过。该修复不改变会话或权限契约，只恢复已有菜单的可操作性。

## 实现结果

- 登录恢复态与登录态使用同一任务框架，显式说明平台／租户身份分离、最小权限和可信问答闭环；认证请求、URL 与会话逻辑保持不变。
- `ProductShell` 收敛为 232px 桌面侧栏、80px 紧凑侧栏和 56px 移动底栏；顶部账户区、导航 tooltip 与工作区上下文继续使用 Fluent UI React v9。
- 数据和治理工作区的重复按钮条改为 Fluent `TabList`，保留 URL 驱动路由和键盘语义。
- 共享设计令牌统一画布、表面、边界、文字、强调色、圆角和阴影；移除全屏渐变、玻璃态、装饰性卡片堆叠与虚线空状态。
- 会话角色在页面中显示为“平台管理员”“租户管理员”“租户用户”，不直接暴露内部角色枚举。

## 全量自动验证

命令：

```bash
make validate
```

结果：退出码 0。项目治理、27 项架构／产品门禁、后端架构门禁、真实 Testcontainers PostgreSQL／pgvector／Redis／MinIO 的 Maven 全反应堆、前端 typecheck、lint、26 个测试文件共 128 项测试、Vite 生产构建，以及演示评测、真实模型验收、恢复、容量和交付质量脚本全部通过。自动验证只证明实现和工程门禁，不等同于人工 UI 接受。

## 代码图谱与工程助手

- 代码图谱先探索 `ConsoleApp`、`ProductShell`、`StatePanel`、`BrowserSessionGate` 和能力投影，再对 `ProductShell`、`StatePanel` 执行影响分析。图谱未能解析 JSX 的完整调用关系，因此只对缩小后的两个真实 `TaskNavigation` 调用方补充定向复核；这使实现保持在语义令牌、共享壳与两个局部导航 seam，没有建立无调用方的通用卡片／表格／表单层。
- 当前 Codex 插件运行环境显式加载 `repo-context`、`codebase-design`、`domain-modeling`、`prototype`、`tdd`、`diagnosing-bugs` 和 `code-review`。设计流 `run-234061ce4b6942e1` 和实现流 `run-2ada5d9f8e7e43a4` 均已完成；后者的六维质量快照和规范／需求双轴审查由 Runtime 校验通过。
- 本切片不生成独立 `StageRunResult`；阶段结论由工程助手 Runtime 对持久化 flow 节点与证据摘要逐项校验。

## 真实页面回归

- 当前操作电脑插件返回 Mac 已锁定，自动解锁失败；未绕过本机锁屏。
- Chrome 在实际 `http://localhost:13443` 的匿名入口完成 1920px 桌面视觉复核：页面无横向溢出，任务目标、产品原则和登录区层级稳定；键盘 `ArrowRight` 和 `Enter` 可在平台／租户登录目标间移动焦点并切换内容。
- Chrome 能力未能改变物理视口，因此 1024×768、390×844、200% 缩放和 `prefers-reduced-motion` 不得以 CSS 推断替代，继续等待真实人工回归。
- 未向 Chrome 提交本地口令，也未建立账户、租户或模型事实；登录后完整平台管理员与租户管理员主流程仍待 Mac 解锁后使用操作电脑插件执行。

## 当前结论

共享 UI 基线和自动门禁已通过，但操作电脑主流程、窄屏、真实 200% 缩放、减少动态效果及登录后逐页审计尚未完成。`M6 / WP-0014` 必须保持进行中，不得进入 `M7`，也不得据此请求人工接受。
