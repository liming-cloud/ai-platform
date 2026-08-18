# WP-0014 角色目录、详情与界面边界整改证据

## 结论

本切片补齐平台与租户角色目录、角色详情、成员管理入口和平台账户安全详情，并修复模型连接／部署 Dialog 只占左半区及登录失败后输入控件越出卡片的可复现缺陷。视觉语言在既有 Fluent UI v9 体系内增强为深墨灰与低饱和电青的高密度治理界面，不引入紫色渐变、光晕、Emoji 或装饰性卡片堆叠。

角色能力编辑没有被伪装为已实现。ADR-0067 已接受的是三个固定角色及固定能力投影，真实编辑会改变公共授权契约、会话撤销和租户隔离语义，必须先形成并接受新的 ADR。当前页面如实显示固定模板状态，M6／WP-0014 继续进行中，不构成人工接受，也不激活 M7。

## 基线与控制面

- 仓库：`/Volumes/slm-ssd/projects/ai-project/ai-platform`。
- 分支：`codex/trusted-rag-foundation-v1`。
- 基线提交：`669c56536c0bac2aabaaabd744082ae44ed0b8b6`，提交说明为 `bug: 修复紧凑导航并补齐浏览器主流程证据`。
- 开始时工作区干净；本文件所列变更均属于本次纵向切片。
- `project/STATE.yaml` 的 `lastVerifiedCommit` 为 `e7ed97d77`，该提交是基线 HEAD 的祖先。
- `make project-status` 返回 `M6 / WP-0014 / in-progress`，没有控制面 blocker；本次不修改里程碑和当前前沿。
- 开始和回归时真实 PostgreSQL、Redis、MinIO、`control-plane`、`query-runtime`、`ingestion-worker` 与 `console-web` 共七个项目容器均为 healthy。

## ADR 与产品边界

- ADR-0059 约束模型供应链的受控写入、失败关闭和用户信息边界；Dialog 修复不改变这些 HTTP 契约。
- ADR-0067 把人员产品限制为 `PLATFORM_ADMINISTRATOR`、`TENANT_ADMINISTRATOR` 和 `TENANT_USER` 三种固定角色，并退役租户自定义角色。
- ADR-0068 禁止向用户呈现后端消息、错误码、追踪标识、异常或内部定位信息；新增详情只呈现登录名、业务状态、中文角色和业务影响。
- ADR-0069 只定义纯本地 `platform.root` 初始化，不允许把该身份变成租户管理员或在页面暴露内部账户标识。

权限编辑至少存在三种实质不同的授权模型：全局修改三种角色模板、只允许每个租户调整两种租户角色、或只保留角色与成员绑定而不开放能力编辑。平台管理员能力能否编辑还会影响平台失锁保护。未接受新 ADR 前，本切片只解释固定模板并链接到现有成员绑定操作，不新增客户端假配置、旧自定义角色接口或数据库写入。

## 代码图谱与影响分析

- 聚焦探索覆盖 `PlatformRole`、`TenantBuiltInRole`、`PlatformRoleBinding`、`TenantMemberWorkspace`、`PlatformAccountDirectoryWorkspace`、`BrowserSessionGate`、`ConsoleRoute` 与平台模型供应链 Dialog。
- 图谱确认服务端授权来自固定枚举和角色绑定，`PlatformRoleBinding` 不承载可编辑权限；因此没有恢复已经退役的自定义角色路径。
- 对 `ConsoleRoute`、`PlatformAccountDirectoryWorkspace`、`BrowserSessionGate` 与 `PlatformModelSupplyWorkspace` 执行影响分析。路由变化会影响 `ConsoleApp` 和各工作区，Dialog 与登录布局变化只需落在共享 CSS seam；实现据此限制在呈现、路由和前端测试层，没有新增后端、迁移或授权写入口。

## 红绿 TDD

红灯先证明以下缺口：

```text
cd frontend
npm test -- --run \
  apps/console-web/src/features/access/RoleDirectoryWorkspace.test.ts \
  apps/console-web/src/features/platform-governance/PlatformAccountDirectoryWorkspace.ui.test.ts \
  apps/console-web/src/features/shared/task-dialog-layout.ui.test.ts \
  apps/console-web/src/session/BrowserSessionGate.ui.test.ts \
  apps/console-web/src/session/console-route.test.ts

结果：5 个测试文件失败；缺少角色目录模块／路由／账户详情，Dialog 表单没有跨越 Fluent `DialogBody` 网格，登录输入没有最小宽度约束。
```

最小实现后：

```text
上述 5 个测试文件共 28 项测试通过。
npm run typecheck：通过。
npm run lint：通过。
npm test：29 个测试文件、135 项测试通过。
npm run build：生产构建通过。
```

最终全量结果以本文件“自动门禁”记录为准。

## 实现结果

- `/platform/roles` 提供平台角色目录；`/access/roles` 提供当前租户角色目录。两个目录都支持查看角色详情和前往对应成员／账户目录。
- 角色详情展示任务目的、用户可理解的能力、范围边界和固定模板状态；不展示内部权限码。
- 平台账户列表把行级操作收敛为“查看详情”和“更多操作”；详情 Drawer 不展示 SubjectId、账户修订、密码摘要、会话或权限快照。
- 四个带表单的模型连接／部署 Dialog 使用统一 `.foundation-dialog-form`，跨越 Fluent `DialogBody` 的完整内容网格并保持 `min-width: 0`。
- 登录卡片、表单和 Fluent Input 增加宽度收缩边界；失败消息出现后控件不再由固有最小宽度撑出登录卡片。
- 角色目录使用单一 Fluent 图标族、精细分隔、深色信任条、状态徽标和克制电青强调，继续复用现有设计系统和无障碍语义。

## 真实基础设施、HTTP 与浏览器

最新 `console-web` 镜像已从当前源码构建并只重建该容器，未重建数据卷，未提交账户、租户、成员、模型或数据库写操作。回归时：

```text
GET http://localhost:13443/                         -> 200
GET http://localhost:13443/platform/roles           -> 200
GET http://localhost:13443/platform/accounts?page=1 -> 200
GET http://localhost:18080/actuator/health           -> 200
GET http://localhost:18081/actuator/health           -> 200
```

使用真实 Chrome 和操作电脑插件完成：

- 平台账户目录原先没有详情入口；整改后可打开安全详情 Drawer，`platform.root` 保持原始大小写，详情不泄露内部字段。
- “新建平台厂商连接”原先稳定复现左侧表单／右侧空白；整改后表单占据 Dialog 完整内容宽度，页面背景和上下文仍可辨认。
- 平台登录输入错误密码后，失败提示和输入框均保持在登录卡片内。
- 地址栏明确显示 Chrome `200%` 时，平台角色目录可滚动、操作按钮可到达，角色详情 Drawer 可读且关闭按钮获得键盘焦点，没有横向溢出。

macOS 在继续执行 200% 登录失败复验时自动锁屏，因此“错误密码 + 真实 200%”组合不能登记为已通过；普通缩放下的错误密码回归已通过。物理 390×844 仍受当前 Chrome 最小窗口宽度限制，继续保持待人工验证，未用 CSS 推断或旧截图替代。

## 自动门禁

```bash
make validate
make project-verify
git diff --check
```

`make validate` 退出码为 0：项目治理和架构／产品门禁通过；Maven 全量测试覆盖真实 PostgreSQL、pgvector、Redis 与 MinIO 路径；前端 typecheck、lint、29 个测试文件共 135 项测试及 Vite 生产构建通过；演示数据、真实模型、恢复、容量韧性和交付运维质量脚本均通过。`make project-verify` 退出码为 0，9 项治理测试和 14 个工作包校验通过，当前前沿仍为 `WP-0014`；`git diff --check` 退出码为 0。

## 技能与运行环境

- 当前插件运行环境为 Codex Desktop，能力来源为本机 `engineering-assistant` 插件技能、代码图谱 MCP 和 `computer-use` 插件。
- 显式加载并使用 `design-taste-frontend`、`engineering-assistant:repo-context`、`engineering-assistant:codebase-design`、`engineering-assistant:diagnosing-bugs`、`engineering-assistant:tdd` 与 `computer-use:computer-use`。
- Design Taste Frontend 使实现继续使用成熟 Fluent UI v9 组件语义，并把科技感限制为高信息密度、深墨灰与低饱和电青、精细边界和明确状态，而不是渐变、光晕或模板化卡片。
- 工程助手控制面使用了各技能的 `SKILL.md`，并按 TDD 安全 seam 与缺陷诊断流程完成复现、最小修复和回归。本次没有产生独立 `StageRunResult`，阶段结果校验不适用。

## 未闭环与下一切片

1. 角色权限编辑需要先接受新的授权 ADR，并明确全局模板、租户级模板或仅成员绑定三种模型中的一种；不得用前端本地状态假装保存成功。
2. 助手建立／版本冻结／测试／发布入口、首份文本恢复路径和可信问答完整 UI 主流程仍按既有 WP-0014 前沿继续研发。
3. 真实 200% 下的错误密码组合、物理 390×844 和后续逐页视觉审计仍待人工续证。
4. M6 自动验收和人工明确接受前，WP-0014 保持进行中，M7 不激活。
