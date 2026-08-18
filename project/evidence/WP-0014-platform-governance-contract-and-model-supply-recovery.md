# WP-0014 平台治理契约与模型供应链错误恢复证据

日期：2026-08-18

状态：生产实现、真实基础设施、HTTP 回归和局部 Chrome 响应式回归通过；M6 与 `WP-0014` 保持进行中，人工明确接受前不得进入 M7。

## 本切片目标与边界

本切片只处理人工回归暴露的平台账户语义、三角色目录、过期平台工作区、模型目录冲突、模型发现反馈和列表宽度问题。成功标准如下：

- 重复登录名和重复受控模型目录条目不再落入通用 `400`／`500`，而是稳定返回 `409`。
- 平台角色目录展示 `PLATFORM_ADMINISTRATOR`、`TENANT_ADMINISTRATOR`、`TENANT_USER` 三种固定角色，并明确租户角色只能在租户内分配。
- 平台账户目录使用“全局人员账户”和“平台治理权限”表达，不再暗示租户管理员必须绑定平台管理员。
- 已退役的平台运营员空工作区不再进入导航或路由；兼容 `/platform` 路径回到平台运营总览。
- 模型发现适配器不支持时只呈现安全、可行动的用户提示，平台连接目录复用统一宽表样式。
- 本地 `m6-review` 租户的 `tenant.admin` 能以租户管理员身份登录并得到租户工作区能力。

范围外内容：角色权限模板编辑、通过模型名称猜测模型能力、供应商专用模型发现适配器、全 SPA 人工接受、M7，以及 TLS／Certbot／生产 Nginx。

## 基线与控制面

- 仓库：`/Volumes/slm-ssd/projects/ai-project/ai-platform`
- 分支：`codex/trusted-rag-foundation-v1`
- 开始 HEAD：`fec957b154d6fa405ac3f2748cef113e7fcedb3d`
- 开始工作区：干净
- `project/STATE.yaml`：`currentMilestone: M6`、`currentFrontier: WP-0014`、`lastVerifiedCommit: e7ed97d77`
- `e7ed97d77` 是开始 HEAD 的祖先；`make project-status` 报告 M6／WP-0014 进行中，无开放决策或阻塞。
- 实际基础设施：PostgreSQL、Redis、MinIO、control-plane、query-runtime、ingestion-worker、console-web 共 7 个容器均为 `healthy`。

## 代码图谱与影响分析

先通过代码图谱而非宽泛源码搜索追踪以下路径：

- `PlatformAccountDirectoryWorkspace -> createPlatformAccount -> PlatformAccountEstablishmentRequest.from -> PlatformAccountEstablishmentApplicationService.establish`
- `BrowserAuthenticationService.authenticate`
- `ControlledModelCatalogService -> PostgresModelConfigurationRepository.saveCatalogEntry`
- `visibleNavigation -> ConsoleApp`
- `PlatformProviderModelDiscoverer` 及 OpenAI-compatible 连接探测扩展
- `ProductShell -> onNavigationChange -> AuthenticatedConsoleApp.selectNavigation -> navigate`

重要修改前分别对 `RoleDirectoryWorkspace`、`visibleNavigation`、`PlatformAccountDirectoryWorkspace`、`PlatformModelSupplyWorkspace`、`ControlledModelCatalogService` 和 `saveCatalogEntry` 执行影响分析。结论直接改变了实现边界：账户与目录重复事实在后端映射为稳定冲突；三角色只调整既有固定投影；没有把供应商连接探测错误扩展成未经设计的能力猜测；Chrome 锁屏后点击未触发不能据此修改已经完整连接的导航链。

## 诊断结论与整改

| 人工现象 | 根因 | 本切片结果 |
| --- | --- | --- |
| 建立 `tenant.admin` 返回 `COMMON.REQUEST.0001` | 登录名已存在，应用层把重复事实作为 `IllegalArgumentException` 落入通用请求错误 | 新增稳定 `ORGANIZATION_ACCESS.CONFLICT.0001`，重复建立返回 `409`；专用租户管理员仍在租户建立向导中分配 |
| 账户目录提示“尚未绑定平台角色” | 全局账户和平台授权的产品文案混淆，易被理解为租户管理员也要成为平台管理员 | 改为“尚未授予平台治理权限”，详情和操作均使用平台治理语义，并解释租户角色在租户建立或成员管理中分配 |
| 平台角色目录只有平台管理员 | 页面按当前作用域过滤掉租户固定角色 | 平台目录展示三种固定角色；两种租户角色标记“租户内分配”，不会从平台账户目录错误绑定 |
| 受控模型目录返回 `COMMON.SYSTEM.0001` | 重复目录 `POST` 触发数据库唯一约束，未映射稳定领域冲突 | 捕获 `DuplicateKeyException` 并返回 `MODEL_RUNTIME.CONFLICT.0010`／`409`；列表读取契约不变 |
| 模型发现返回 `PLATFORM_MODEL_DISCOVERY_UNSUPPORTED` | 当前只有确定性发现器，OpenAI-compatible 扩展只实现连接探测，没有平台模型发现适配器 | 页面不显示原始 `resultCode`，明确提示当前连接尚无自动发现适配器；不根据模型名猜测 CHAT／EMBEDDING 或能力 |
| 平台连接表格宽度不足 | 页面表格没有使用统一目录宽度和横向恢复规则 | 统一使用 `member-directory-table`，窄屏允许内容区受控横向滚动 |
| 平台工作区无授权治理任务 | 三角色收敛后旧 `PLATFORM_OPERATOR` 无数据工作区已无产品角色调用方 | 删除导航和页面占位；兼容 `/platform` 路由指向平台运营总览 |
| `m6-review / tenant.admin` 无法登录 | 本地验收账户口令与人工使用值不一致 | 在精确目标上重置本地验收口令后，租户登录与会话能力回归通过；没有新建或删除租户 |

## 角色权限边界

ADR-0067 明确产品只保留三种固定角色，并固定其能力投影。本切片因此只提供角色列表、能力详情和合法的成员／平台治理入口，不增加浏览器本地“可配置权限”。角色模板编辑会改变公共授权语义、会话撤销和权限版本，需要新的已接受 ADR 明确是全局模板、租户模板还是仅成员角色绑定后再实施。

## 红绿 TDD 与自动验证

红灯覆盖：

- 重复平台账户登录名必须抛出稳定冲突异常。
- 真实 PostgreSQL 重复受控目录条目必须抛出稳定冲突异常。
- 平台角色目录必须包含三种固定角色，租户角色不能跳转到平台账户授权。
- 旧平台工作区不再进入能力导航和路由联合类型。
- 模型发现失败不能渲染内部 `resultCode`，连接表必须使用统一目录样式。

绿灯与全量命令：

```text
./mvnw -pl contexts/organization-access -Dtest=PlatformAccountEstablishmentApplicationServiceTest test
./mvnw -pl contexts/model-runtime -Dtest=PostgresModelConfigurationRepositoryTest test
npm run test -- --run RoleDirectoryWorkspace PlatformAccountDirectoryWorkspace PlatformModelSupplyWorkspace navigation-capability console-route
npm run typecheck
npm run lint
make validate
```

结果：定向后端测试通过；定向前端 5 个测试文件共 37 项通过；全量 `make validate` 退出码为 `0`，前端 30 个测试文件共 139 项通过，typecheck、lint、生产构建、后端 Maven 反应堆和运维质量脚本全部通过。

## 真实基础设施与 HTTP 回归

最新 `control-plane` 和 `console-web` 镜像已部署到本地 Compose。首次 control-plane 构建因 Maven Central 暂时返回 `502` 中断，保持数据卷不变重试后成功；不能把该外部下载失败记为产品缺陷。

| 回归 | 结果 |
| --- | --- |
| `http://localhost:13443` | `200` |
| control-plane `/actuator/health` | `200` |
| `platform.root` 平台登录 | `204` |
| 平台账户目录读取 | `200` |
| 重复建立 `tenant.admin` | `409`，账户数量不增加 |
| 精确重置本地 `tenant.admin` 验收口令 | `204` |
| `m6-review / tenant.admin` 租户登录 | `204` |
| 租户浏览器会话能力 | `200`，角色仅为 `TENANT_ADMINISTRATOR`，工作区仅为 `TENANT` |
| 平台部署目录读取 | `200`，存在已发布部署 |
| 重复建立受控模型目录条目 | `409` |

本切片未创建账户、角色、租户或模型，没有执行广泛数据库删除。

## 浏览器与人工证据边界

- 已通过真实 Chrome 登录 `m6-review / tenant.admin`；从平台深链进入时正确呈现拒绝状态，直达 `/operations` 后呈现租户运营内容，未泄露平台页面。
- Chrome 物理视口 `1024×768`：角色标题、上下文、两种租户角色和详情操作均可见。
- Chrome 物理视口 `390×844`：移动顶部栏、底部导航、角色上下文、租户管理员、普通用户和“查看详情”可见，未观察到页面边界溢出。
- Mac 随后再次锁定，操作电脑无法自动解锁；锁屏后的点击只聚焦、不触发页面动作，不能据此判定应用导航缺陷。
- 当前 Chrome 控制能力不能模拟 `prefers-reduced-motion`，也未在本切片完成真实 200% 缩放、解锁后的平台角色／模型供应页和完整键盘操作。这些项目继续待人工验证，不以 CSS 推断、旧截图或自动测试代替。

因此，本证据只关闭本切片缺陷与自动门禁，不表示全 SPA 获得人工接受。

## 后续研发登记

1. 为 OpenAI-compatible／火山方舟等供应商设计专用或辅助式模型目录适配器，使用供应商权威元数据确定模型类型和能力；通用 `/models` 响应不足时不得用名称猜测。火山方舟连接的 API 基础地址提示使用 `https://ark.cn-beijing.volces.com/api/v3` 根路径，不包含具体推理端点。
2. 如需可配置角色权限，先通过新 ADR 决定权限模板所有权、允许范围、权限版本推进和旧会话撤销，再建立后端契约与纵向 TDD。
3. Mac 解锁后继续真实 200% 缩放、键盘导航、`prefers-reduced-motion`、平台端角色详情和模型供应链人工回归。

