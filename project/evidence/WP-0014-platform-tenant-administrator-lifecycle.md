# WP-0014 平台租户管理员生命周期与三角色验收证据

## 结论

ADR-0073 方案 A 的平台侧纵切已经实现并完成自动化、真实基础设施、Compose 和 Chrome 验收：租户管理员的目录、候选、分配、原位提升、原子替换、停用与恢复只由平台管理员操作；租户管理员工作区只治理本租户普通成员，平台账户不会进入租户成员候选。停用最后一位启用管理员会以稳定业务语义失败关闭，并在浏览器显示可执行的恢复建议，不泄露内部错误码、追踪标识或异常消息。

本证据只证明租户管理员生命周期切片通过，不把 M6 或全产品判定为人工验收通过。普通用户问答入口存在，但当前演示租户没有已发布助手，尚不能完成真实 RAG 回答与引用核验；该缺口继续阻断 M6 人工复验。

## 业务与安全边界

- 所有权：平台管理员独占租户管理员生命周期，租户管理员不能停用、授权或替换同级管理员。
- 普通成员：租户成员目录与候选只包含目标租户普通成员，活动平台角色主体失败关闭。
- 最后管理员：目标是最后一位启用管理员时拒绝停用，不撤销其现有会话。
- 原子替换：新管理员先在同一事务结果中启用，旧管理员随后停用，不产生零管理员中间状态。
- 会话：分配、替换、停用和恢复推进权限版本；失效的旧租户会话不能继续使用。
- 最小披露：平台接口只返回登录名、成员引用、状态、版本和时间摘要；不返回密码、主体内部标识、权限集合、会话事实或厂商／基础设施信息。
- 并发：持久化命令在共同 `SubjectIdentity` 锁及成员锁下执行，继续满足活动平台角色与启用租户成员资格互斥。
- 最后管理员并发：所有管理员命令首先锁定目标 `tenant_lifecycle` 行，同一租户的状态判断与写入串行执行，禁止两位管理员被并发停用后归零。

公共入口固定为：

```text
GET  /api/v1/platform/tenants/{tenantId}/administrators
GET  /api/v1/platform/tenants/{tenantId}/administrators/candidates
POST /api/v1/platform/tenants/{tenantId}/administrators
POST /api/v1/platform/tenants/{tenantId}/administrators/{membershipReference}/disable
POST /api/v1/platform/tenants/{tenantId}/administrators/{membershipReference}/restore
POST /api/v1/platform/tenants/{tenantId}/administrators/{membershipReference}/replace
```

## 分层实现

- 领域：`TenantAdministratorGovernance` 只表达分配、最后管理员保护、恢复与原子替换不变量；`TenantMembership` 负责角色、状态和版本演进。
- 应用：`PlatformTenantAdministratorApplicationService` 只接受令牌派生的 `PlatformRequestContext`，校验平台权限并编排持久化与会话撤销；`PlatformTenantAdministratorQueryService` 提供服务器权威分页。
- 基础设施：`PostgresPlatformTenantAdministratorStore` 与 `PostgresPlatformTenantAdministratorDirectory` 拥有事务、锁、分页和映射；SQL 位于 `PlatformTenantAdministratorMapper.xml`，未在 Java 中新增内联 SQL。
- 接口：`PlatformTenantAdministratorController` 只接收稳定业务引用和期望版本，不接收操作者、平台角色、权限或租户上下文。
- 前端：`TenantAdministratorDialog` 由平台租户目录打开，提供目录、候选、确认和状态反馈；租户成员工作区不再暴露管理员治理动作。

`project/architecture-debt/complex-sql-allowlist.json` 的 Java 内联 SQL 历史基线保持 15 个，只可递减；本切片新增查询全部位于 Mapper XML。

## 红绿 TDD

### 领域与应用

1. 先以不存在的领域服务、应用端口和 DTO 形成编译红灯。
2. `TenantAdministratorGovernanceTest` 证明新成员分配、普通成员原位提升、最后管理员保护和原子替换。
3. `PlatformTenantAdministratorApplicationServiceTest` 证明权限前置、最后管理员拒绝不撤销会话、替换后撤销旧会话。

### 真实 PostgreSQL 与 HTTP

1. `PostgresPlatformTenantAdministratorStoreTest` 使用真实 PostgreSQL 验证分配、范围互斥、版本冲突、事务回滚与原子替换。
2. `PostgresPlatformTenantAdministratorDirectoryTest` 验证管理员目录和候选的服务器端分页、稳定排序、租户隔离与最小投影。
3. `PlatformTenantAdministratorControllerTest` 验证受限 HTTP 契约及内部字段不出站。
4. `AuthenticationApiTest` 使用真实 PostgreSQL、Redis、随机端口 Tomcat 和 HttpOnly 会话，覆盖候选、分配、替换、停用、恢复、权限版本推进和旧会话撤销。

最后管理员保护曾以 `IllegalStateException` 进入通用 500，是实际浏览器红灯。修复后使用 `OrganizationAccessRuleErrorCode.LAST_ENABLED_TENANT_ADMINISTRATOR`，页面只显示“必须保留一位启用管理员”和下一步建议；确认层会关闭，内部错误码不出现在页面。

提交前固定差异审计又发现并发反例：原实现只锁目标成员，两位管理员并发停用时都能在对方事务提交前读到“仍有另一位管理员”。真实 PostgreSQL 红灯为 `Expected TimeoutException to be thrown, but nothing was thrown`，证明第二个请求未被串行化；加入权威租户生命周期行锁后，同一用例以退出码 0 通过，第二个请求等待首个提交并重新计数，最终 1 个成功、1 个按最后管理员规则拒绝、数据库保留 1 位启用管理员。随后 organization-access 全模块回归以退出码 0 完成。

### 前端与响应式

1. 客户端与工作区测试先覆盖平台管理员接口、租户成员候选不含平台账户、弹窗焦点恢复和安全错误翻译。
2. 390×844 首轮实测发现弹窗内容区为 312 CSS px，而内部节点宽度达到 533 CSS px。先加入失败的静态回归断言，再为内容网格、目录、卡片、身份与 `MessageBar` 增加 `min-width: 0`、单列收缩和安全换行。
3. 修复后 390×844 对弹窗、正文、标题、消息条、目录、卡片、操作区和边界说明逐项测量，所有节点均满足 `scrollWidth <= clientWidth`。

前端采用 Fluent UI v9 作为唯一组件系统，没有引入第二套组件库；本切片延续 `DESIGN_VARIANCE=7`、`MOTION_INTENSITY=6`、`VISUAL_DENSITY=6`，使用分层英雄区、状态色、受控确认、移动端卡片化和粘性关闭动作提升现代感，同时保持键盘与高缩放可达性。

## 验证记录

已执行并通过：

```bash
./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q \
  -pl backend/contexts/organization-access -am \
  -Dtest=PostgresPlatformTenantAdministratorStoreTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q \
  -pl backend/contexts/organization-access -am test
npm run test -- --run src/features/platform-governance/PlatformTenantLifecycleWorkspace.test.ts
make validate
make project-verify
```

其中最后一轮前端定向回归为 2 项；`make validate` 以退出码 0 完成，包含架构门禁、后端全反应堆、真实 PostgreSQL／Redis／MinIO、控制面 HTTP、前端 38 个测试文件／184 项测试和生产构建。

Compose 先以当前前后端源码重新构建并部署 `control-plane` 与 `console-web`；加入租户级并发锁后，再以构建修订 `wp0014-tenant-admin-concurrency-r5` 单独重建并无数据卷替换 `control-plane`。最终容器状态为 `healthy`，`/actuator/health` 返回 `UP`，PostgreSQL、Redis 与 MinIO 数据服务未重建。Docker Desktop 凭据助手在早期公共镜像查询中发生 30 秒超时，临时空 `DOCKER_CONFIG` 只用于诊断；最终 `make validate`、镜像重建与 Compose 部署均在正常 Docker 配置下完成。前端构建仍报告既有的 2 个 high npm audit 风险，本切片未扩大范围执行破坏性依赖升级。

## Chrome 三角色与可达性验收

使用用户授权的本地账号在真实 Chrome 操作：

- 平台管理员：读取租户、打开管理员治理弹窗、分配第二管理员、停用／恢复／再次停用、尝试停用最后管理员并观察安全拒绝。
- 租户管理员：只能看到本租户普通成员治理；不能管理租户管理员或平台账户。
- 普通用户：仅有问答入口；直接访问 `/access/members` 被拒绝。由于无已发布助手，问答页显示明确空状态，未伪造回答成功。
- 最终演示状态：`tenant-admin` 保持启用；验收临时账户 `rag-user-acceptance` 保持停用，避免扩大长期访问面。
- 部署后冒烟：重建后端导致旧浏览器会话按预期失效；使用已授权的本地平台管理员重新登录，平台租户分页与治理弹窗重新读取到上述最终状态，全程只读且没有新的应用错误。

显示与交互续证：

- 1024×768：弹窗、状态反馈、目录、确认和边界说明完整可读。
- 390×844：修复内部横向溢出后完整通过，按钮与文本安全换行。
- 真实 Chrome 200%：通过浏览器缩放验证，设备像素比由 2 变为 4，CSS 视口宽度约减半；无横向溢出，内容可纵向滚动；验收后恢复 100%。
- 键盘：`Escape` 关闭弹窗，焦点返回“管理租户管理员”触发按钮。
- `prefers-reduced-motion`：源码包含专用媒体查询，租户管理员弹窗在 reduce 下禁用动画与过渡，前端测试验证该规则。未修改 macOS 全局辅助功能设置，因此本项为受控媒体规则续证，不冒充真实系统开关操作。

截图：

- `project/evidence/artifacts/WP-0014-tenant-administrator-governance/tenant-admin-last-admin-protection-1024x768-fixed.png`
- `project/evidence/artifacts/WP-0014-tenant-administrator-governance/tenant-admin-last-admin-protection-390x844-fixed.png`
- `project/evidence/artifacts/WP-0014-tenant-administrator-governance/tenant-admin-dialog-real-200-percent-fixed.png`
- `project/evidence/artifacts/WP-0014-tenant-administrator-governance/ordinary-user-ask-1024x768.png`

浏览器缺少第三个符合资格且不属于当前管理员目录的账户，因此没有在最终演示数据上再次执行“原子替换”按钮；真实 PostgreSQL 和真实 HTTP 已覆盖替换与旧会话撤销。该限制不影响本切片接口正确性，但作为人工演示数据边界保留。

## 代码图谱与工程助手证据

- 运行环境：Codex Desktop，本地仓库为唯一事实来源。
- 工程助手能力来源：已显式加载 `engineering-assistant:repo-context` 与 `engineering-assistant:tdd`；UI 使用 `design-taste-frontend`，真实操作使用 Chrome 与 Computer Use 技能。
- 代码图谱探索：`PlatformTenantLifecycleWorkspace`、`TenantAdministratorDialog`、`presentApiError`、`TenantAdministratorGovernance`、`OrganizationAccessRuleErrorCode`、`TenantMembershipLifecycle.disable`、`PostgresTenantLifecycleTransitionStore`、`PlatformTenantAdministratorStore` 与 `PlatformTenantAdministratorMapper`。
- 影响分析：`OrganizationAccessRuleErrorCode` 与 `presentApiError` 均影响约 20 个符号；因此稳定错误被限制在现有规则异常与统一前端翻译 seam，没有把厂商或内部消息直接透传到页面。并发修复复用权威 `tenant_lifecycle` 聚合根行锁，影响限制在管理员应用服务、存储端口、Mapper XML、自动配置与对应测试，没有引入跨上下文锁或 Java 内联 SQL。
- 控制面：遵守 ADR-0073、`project/STATE.yaml`、`project/work-packages/WP-0014.md` 及复杂 SQL 递减门禁。
- `StageRunResult`：本切片未产生该 JSON 阶段产物，校验不适用。

## 未关闭缺口

1. 演示租户没有已发布助手，普通用户无法完成“提问 → 检索 → 引用 → 反馈”的真实 RAG 闭环。
2. 可信 RAG 权限与准确率仍须以真实知识库、评测集、引用充分性和越权反例形成独立质量门禁。
3. macOS 全局 `prefers-reduced-motion` 开关未被自动修改；若人工门禁要求真实系统设置，须在明确授权后单独执行并恢复。
4. M6 全产品人工评审仍为拒绝状态；本切片通过不改变 `HITL-0008-M6-REJECTION` 的总体结论。
