# WP-0014 租户知识库所有权与一等工作区证据

## 结论与状态

本切片已经把“知识库”从隐含的首份文本发布步骤提升为租户工作区中的一等业务资源，并在服务端收紧管理权限：平台管理员不能读取或创建租户知识库，普通用户的 `knowledge:read` 也不能访问知识库管理目录；只有具备 `knowledge:write` 的租户管理员可以列出、创建和进入本租户知识库。

自动化 TDD、真实 PostgreSQL、随机端口 HTTP、前端全量测试、生产构建、最终源码 Compose 重建和健康检查已经通过。真实 Chrome 三角色、1024×768、390×844、真实 200% 缩放、键盘导航与 `prefers-reduced-motion` 仍未执行成功，因此本证据不申请关闭 `WP-0014`，也不声称人工验收通过。

## 所有权与权限不变量

| 角色 | 知识库能力 | 失败关闭边界 |
| --- | --- | --- |
| 平台管理员 | 仅维护租户生命周期、平台模型供应链、基础设施与脱敏聚合 | 平台浏览器会话不得进入知识库 API，不能读取目录、正文、成员详情或写入知识库 |
| 租户管理员 | 在当前租户内列出、创建、进入和维护知识库 | `tenantId` 只从受信请求上下文恢复，页面和请求体不得提交租户标识 |
| 普通用户 | 通过已发布助手进行授权问答、读取获授权引用 | `knowledge:read` 不等于知识库管理权限，不能进入目录或创建知识库 |

`KnowledgeSpace` 仍是现有领域实体和持久化结构，本轮只统一产品语言为“知识库”，没有建立第二套同义模型。每个知识库必须且只能属于一个租户，跨租户共享、平台代管正文和平台知识库目录均不在本切片范围。

## 代码图谱与影响分析

实施前先通过代码图谱探索 `KnowledgeSpaceService`、`KnowledgeSpaceController`、`KnowledgeSpaceRepository`、`KnowledgePublicationFlow`、`ConsoleApp`、`NAVIGATION` 与路由投影。关键结论如下：

- `KnowledgeSpace` 已强制持有 `TenantId`，控制器从 `TrustedRequestContext` 恢复当前租户，仓储查询带租户谓词；已有底层模型不是平台共享资源。
- 实际缺陷集中在产品任务流：知识库创建被隐藏在“首份文本发布”向导，导航以泛化“数据”呈现，且列表读取只要求 `knowledge:read`。
- 平台会话与租户会话是独立认证边界，平台会话不能被转换为租户上下文；应在服务端继续失败关闭，而不是仅靠菜单隐藏。
- `KnowledgePublicationFlow` 和 `NAVIGATION` 的直接影响均为局部 2 个调用点；`KnowledgeSpaceService` 的非局部影响分析得到 81 个潜在受影响节点，因此权限变更同时覆盖应用层和真实 HTTP 回归。

上述结论把方案从“新建平台／租户知识库体系”收敛为：保留现有租户领域模型，增加一等租户工作区，移除隐式创建，并在公共服务 seam 上收紧管理能力。

## 红绿 TDD

### 后端红灯

先修改测试，证明旧行为错误：

- 只持有 `knowledge:read` 的调用者仍可列出知识库，预期拒绝但实际放行。
- 只持有 `knowledge:write` 的租户管理员不能列出知识库，预期成功但实际拒绝。

红灯结果：`KnowledgeSpaceServiceTest` 共 4 项，1 项失败、1 项错误。

### 后端绿灯

`KnowledgeSpaceService.listKnowledgeSpaces` 统一要求 `knowledge:write`。`KnowledgeSpaceApiTest` 进一步使用真实平台浏览器 Cookie 证明：

- 会话真实投影为 `PLATFORM_ADMINISTRATOR`；
- 同一平台 Cookie 调用知识库 GET 和 POST 均返回 `401` 与安全认证错误；
- 真实 PostgreSQL 中知识库记录保持零写入；
- 只读租户 token 调用 GET 和 POST 均返回 `403`。

验证命令：

```bash
JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home \
./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -f backend/pom.xml \
  -pl contexts/knowledge-data,bootstrap/control-plane-bootstrap -am \
  -Dtest=KnowledgeSpaceServiceTest,KnowledgeSpaceApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：`KnowledgeSpaceServiceTest` 4／4、`KnowledgeSpaceApiTest` 5／5 通过。后者启动 `pgvector/pgvector:0.8.5-pg18-trixie`、Redis 8.8.1、67 项迁移和随机端口 HTTP，不使用内存数据库替代。

### 前端红灯与绿灯

前端先后建立以下可证伪测试：

- “知识库”导航和 `/knowledge-bases?page=N` 路由；
- 知识库所有权说明、目录、空状态和服务端分页；
- 快速入门必须选择现有知识库，不得隐式创建；
- 五个租户数据页面不得出现旧“知识空间”“首份文本发布”或局部“退出登录”。

红灯依次暴露 5 项导航／路由失败、新工作区模块不存在、3 项快速入门失败和 5 项跨页术语失败；实现后全部转绿。最终前端验证：

```bash
npm run lint
npm run typecheck
npm test
npm run build
```

结果：37 个测试文件、180 项测试全部通过，生产构建通过；知识库工作区懒加载 chunk 为 8.58 kB（gzip 3.39 kB）。

### 选择上下文、创建幂等与完成态复审

独立固定 diff 评审先发现一个可能把写操作导向错误知识库的 P1：第 2+ 页进入和跨子任务导航会丢失显式选择，四个子任务随后静默回退当前页首项；同时发现创建错误在 Dialog 外、幂等键未绑定规范化载荷两个 P2。先补四组可证伪测试，红灯为 4 个文件中 17 项失败、26 项通过；随后完成以下收敛：

- `knowledgeDataTaskRoute` 统一保留知识库页码、显式选择和嵌入模型上下文；
- `resolveKnowledgeSpaceIdOnPage` 只接受当前服务器页中真实存在的显式 ID，缺失、越页和无效深链都不猜测首项；
- 三个可写来源页面提供“请选择知识库”空选项，提交守卫禁止空选择写入；
- 创建 Dialog 内显示可聚焦错误，名称字段同步 `aria-invalid`；
- 创建尝试按规范化 `{name, description}` 指纹复用或更新幂等键，空说明提交 `null`；网络或 5xx 不确定结果才按完整载荷回查，确定性 4xx 不误确认成功。

该轮绿灯为 4 个文件、43 项测试通过。复审再发现快速入门完成态仍手工返回目录第 1 页；补充测试后红灯为 1 项失败、26 项通过，改用同一 `knowledgeDataTaskRoute` 后 2 个文件、27 项测试全部通过。最终代码图谱影响分析确认：路由辅助函数只影响 `ConsoleApp`、知识库目录和四个知识任务，选择解析只影响四个任务工作区；独立 `engineering-assistant:code-review` 最终结论为无活动 P0／P1／P2，技术状态可提交，人工浏览器状态独立保持待续证。

### 全量质量门禁

```bash
JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home make validate
```

结果：最终固定 diff 重新执行后退出码 `0`。项目治理 9 项、架构／产品 27 项、全 Maven 反应堆、真实 PostgreSQL／pgvector／Redis／MinIO、前端 37 个文件／180 项测试及生产构建、演示数据、真实模型验收、恢复、容量韧性与交付操作门禁全部通过。

## 产品实现

- 租户导航将“数据”任务聚焦为“知识库”，默认进入 `/knowledge-bases`。
- 新工作区提供租户所有权说明、目录、空状态、创建 Dialog、服务端分页和“进入知识库”动作。
- 创建请求不接收 `tenantId`，重试使用稳定幂等键，成功后读取服务器事实。
- “快速入门”只消费已存在知识库；无知识库时引导回目录建立，不再把上传文本等价为建立知识库。
- 受治理文件、结构化数据、对象存储与快速入门均作为选中知识库内的后续任务；页面局部退出登录入口已移除，统一会话操作留在右上角。
- 新增样式只使用 Fluent/Foundation 语义 token；响应式覆盖 1024px、390px 与 200% 重排，动效只在 `prefers-reduced-motion: no-preference` 下启用。

## Fluent UI 与视觉策略

保留 Fluent UI React v9 作为唯一视觉组件和 token 基座。当前低质感来自信息架构、构图、密度、层级和动效运用，不是必须通过替换组件库解决。设计参数调整为：

- `DESIGN_VARIANCE=7`
- `MOTION_INTENSITY=6`
- `VISUAL_DENSITY=6`

普通服务端分页列表继续使用 Fluent `Table`／`DataGrid`。只有真实性能测试证明大型数据网格的 DOM 渲染成为瓶颈时，才允许以 Fluent 视觉层叠加 TanStack Table／Virtual 的 headless 逻辑；不引入第二套 Button、Form、Dialog、Popover 或 token 体系。完整官方资料与 MUI X、Ant Design、React Aria、Radix 的对比见 `project/evidence/WP-0014-enterprise-component-library-decision.md`。

## Compose 与同源 HTTP

使用最终源码重建且保留现有数据卷：

```bash
docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml up --detach --build
make deploy-smoke
```

结果：`console-web`、`control-plane`、`ingestion-worker`、`minio`、`postgres`、`query-runtime`、`redis` 七个服务均为 healthy；`/knowledge-bases?page=1` 返回 `200`，未认证同源知识库 API 返回 `401`，且不泄露厂商响应或内部诊断。

`deploy-smoke.sh` 原先仍探测 ADR-0059 已退役的 `query-web:13001`。本轮先增加 `test_deploy_smoke_matches_the_unified_console_topology` 红灯，再删除过期探针并将成功条件改为四个应用入口；定向测试与 `make deploy-smoke` 均转绿。

## 真实 Chrome 硬门禁

已按 `browser:control-in-app-browser` 和 `chrome:control-chrome` 技能尝试接管真实浏览器，但浏览器工具在启动前被当前工作区权限配置阻断，原始错误为：

```text
Error: writable root /Users/lingou/slm/project/ai-project/ai-platform contains symlink component /Users/lingou/slm/project; symlinked writable roots are not supported
```

因此以下矩阵保持待验收，任何 HTTP、组件测试或独立 Playwright 都不替代它：

1. 平台管理员看不到知识库菜单，直接访问租户知识库任务失败关闭。
2. 租户 A 管理员可以创建、分页、进入本租户知识库；租户 B 不能看到租户 A 的知识库。
3. 普通用户只能从已发布助手进入可信问答，不能进入知识库管理目录。
4. 1024×768、390×844、真实 200% 缩放下无水平溢出或不可达操作。
5. 全键盘建立／取消／分页／进入流程可达，焦点恢复正确。
6. 系统启用 `prefers-reduced-motion` 时没有位移动画，状态变化仍可理解。

恢复条件：以规范路径 `/Volumes/slm-ssd/projects/ai-project/ai-platform` 重新打开工作区，并移除重复的软链接 writable root `/Users/lingou/slm/project/ai-project/ai-platform`，随后继续同一任务的真实 Chrome 验收。

## 技能与研究证据

- 当前运行环境：Codex Desktop。
- 显式加载 `engineering-assistant:domain-modeling`，使用战略建模、战术建模、模型产物、设计治理、场景追踪、集成一致性与 ADR 格式控制面，形成 ADR-0072 的所有权与失败关闭规则。
- 组件库复核显式使用 `engineering-assistant:research` 与 Exa Search，只采用官方一手资料；共检索 9 个角度、审阅 54 个候选并读取 14 个官方页面。
- `design-taste-frontend` 不直接适用于管理后台，因此只采用其 audit-first、anti-slop、响应式、无障碍和减少动态效果约束；组件与业务交互仍遵循现有企业控制台设计系统。
- 显式加载 `engineering-assistant:tdd`，在公共路由、知识库选择、创建幂等和 HTTP 授权 seam 上完成红绿循环。
- 独立子任务显式加载 `engineering-assistant:code-review`，使用代码图谱复核最终生产调用链并批准固定 diff；真实 Chrome 状态不包含在该批准中。
- 未生成 `StageRunResult`，阶段结果校验不适用。

## 退出结论

本切片的自动化实现与部署验证已通过，但真实 Chrome 硬门禁尚未完成。`M6` 与 `WP-0014` 保持进行中，`M7` 继续阻断；解除工作区软链接权限配置后，必须完成上述六项浏览器矩阵并将可复现证据登记到仓库，才能申请人工复验。
