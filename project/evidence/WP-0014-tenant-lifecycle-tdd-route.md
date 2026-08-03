# WP-0014：租户生命周期持久化 TDD 路线

## 前提与范围

本路线只在 ADR-0044 被接受后生效。它固定 `TenantLifecycle`、`TenantProvisioning`、
`TenantMembership` 与最小生命周期事件的实现顺序，不创建测试、迁移、HTTP 契约或前端页面。

历史 `organization_access.tenant` 没有初始管理员事实，因而任何写入前都必须接收经过人工
复核的 `tenantId → SubjectId` 映射；映射不是请求登录态，也不能从现有成员顺序、权限或角色推导。

## 公共 seam 与允许修改范围

| seam | 职责 | 允许实现位置 |
| --- | --- | --- |
| `TenantLifecycleRepository` | 读取、锁定并原子保存生命周期根。 | organization-access domain port、PostgreSQL adapter。 |
| `TenantProvisioning` | 验证初始管理员并返回不可变 `TenantProvisioningResult`。 | 已有纯领域服务；不引入 Spring 或 I/O。 |
| 平台租户治理应用服务 | 从 `PlatformRequestContext` 授权，编排事务、加载主体与根、提交操作结果。 | organization-access application。 |
| 生命周期发件箱 | 在同一事务固化最小 `TenantLifecycleChanged` 事实。 | organization-access infrastructure。 |
| 平台 HTTP 路由 | 仅将业务目标转换为命令；操作者只来自平台 Bearer 会话。 | organization-access interfaces。 |

## 红绿顺序

1. **纯领域反例**：`PROVISIONING` 以外的激活、重复挂起、非活动初始管理员与生命周期主体不一致均拒绝；保持现有 `TenantProvisioningTest` 为基线。
2. **历史回填迁移红灯**：真实 PostgreSQL 分别构造缺失映射、重复租户映射、跨租户主体、禁用主体和非成员主体。每种情况必须使整批事务回滚，且不遗留生命周期、成员角色或事件。
3. **历史回填绿灯**：完整的一对一映射只创建一个 `TenantLifecycle`，将被映射的成员确认成 `TENANT_ADMINISTRATOR`，并通过合法迁移达到历史目标状态：`ENABLED` 为 `PROVISIONING → ACTIVE`，`DISABLED` 为 `PROVISIONING → ACTIVE → SUSPENDED`；每个合法操作写入最小事件，原租户标识、创建时间和会话语义保持不变。
4. **新租户建立红绿**：平台应用服务只接受 `PlatformRequestContext`，使用 `TenantProvisioningResult` 在单一事务写生命周期、管理员成员资格与事件；同租户／编码冲突失败方不覆盖成功方。
5. **状态迁移真实事务**：激活、挂起、恢复均使用范围锁或乐观修订；过期版本、错误状态和事件写入失败必须回滚根状态。
6. **自动配置与 HTTP**：独立平台 Bearer 才能调用生命周期路由；租户 Bearer、伪造 Header、请求体中的操作者字段和无 `tenant-governance:write` 均失败关闭。
7. **浏览器角色任务**：平台管理员完成新租户建立与状态变更；租户管理员不出现平台入口，也不能以地址或令牌访问。该步骤只在 HTTP 合约被 ADR-0044 接受后的实现中执行。

## 已完成首个严格状态回填红绿

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| 红灯 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=TenantLifecycleBackfillTest -Dsurefire.failIfNoSpecifiedTests=false` | 旧服务只接受主体标识，无法接收已验证主体、既有成员、目标状态和双时间事实；测试编译失败。 |
| 绿灯 | 同一命令 | 通过；`ENABLED` 经 `PROVISIONING → ACTIVE`，`DISABLED` 经 `PROVISIONING → ACTIVE → SUSPENDED`；主体、成员归属、启用状态和准备中目标均失败关闭。 |

该切片只固定领域转换与不可变操作结果，不读取数据库、不提交成员角色或发件箱，也不代替后续真实 PostgreSQL 回填合同。

## 已完成真实 PostgreSQL 回填红绿

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| 红灯 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=PostgresTenantLifecycleBackfillStoreTest -Dsurefire.failIfNoSpecifiedTests=false` | 缺少回填应用服务、映射值对象、PostgreSQL Store、Mapper 与迁移表，测试编译失败。 |
| 绿灯 | 同一命令 | 通过；完整映射在单一真实 PostgreSQL 事务中写入两个生命周期根、两个管理员成员资格和五条最小状态事实。缺失、重复、跨租户、禁用与非成员映射均零残留；发件箱故障回滚已写入的根和成员角色。 |
| 自动配置红绿 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=OrganizationAccessAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false` | 先因缺少 `TenantLifecycleBackfillService` Bean 失败；补齐 Store 与服务装配后通过。 |

本实现不创建 HTTP、配置、令牌或日志映射入口。人工映射只作为受控部署调用 `TenantLifecycleBackfillService`
的短暂内存输入；当前下一切片仍是新租户建立、生命周期状态迁移和平台 HTTP 受权，不得把映射改造成
公共参数。

## 已完成生命周期认证切换红绿

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| 红灯 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=PostgresTenantLifecycleBackfillStoreTest,PostgresSubjectAuthenticationRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false` | 缺少 `tenant_lifecycle_authentication_cutover` 表，真实 PostgreSQL 夹具无法清理或插入切换事实。 |
| 绿灯 | 同一命令 | 通过；完整回填与单行切换事实同一事务提交，映射／发件箱失败无切换残留；切换前保留旧 `tenant.status`，切换后只允许 `TenantLifecycle.ACTIVE` 签发租户会话。 |

切换事实不含租户、主体、成员或凭据详情，且没有 HTTP、令牌、配置或浏览器写入口。`tenant.status`
在切换后仅是迁移兼容列，认证不再读取它。

## 已完成新租户原子建立红绿

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| 红灯 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=TenantProvisioningServiceTest -Dsurefire.failIfNoSpecifiedTests=false` | 缺少建立命令、原子存储端口和应用服务，测试编译失败。 |
| 绿灯 | 同一命令 | 通过；令牌派生的平台上下文只作授权前提，目标主体不可用时不提交任何结果。 |
| 红灯 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=PostgresTenantProvisioningStoreTest -Dsurefire.failIfNoSpecifiedTests=false` | 缺少 PostgreSQL 建立 Mapper 和原子存储实现，测试编译失败。 |
| 绿灯 | 同一命令 | 通过；真实 PostgreSQL 同一事务写入固定 `DISABLED` 的兼容租户行、`PROVISIONING` 生命周期、初始管理员成员资格和最小状态事实；发件箱失败无残留。 |
| 红灯 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=OrganizationAccessAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false` | 自动配置未注册租户建立服务，启用上下文时缺少 Bean。 |
| 绿灯 | 同一命令 | 通过；Mapper、原子存储与建立用例由 organization-access 自动配置装配。 |

`initialAdministratorSubjectId` 只是 ADR-0047 定义的业务资源目标；平台操作者、权限、渠道、令牌和
追踪标识继续只从 `PlatformRequestContext` 恢复。兼容列固定为 `DISABLED`，从不用于认证或生命周期
状态迁移；`PROVISIONING` 租户不能签发租户会话。

## 已完成生命周期状态迁移与受限 HTTP 红绿

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| 领域／应用红灯 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=TenantLifecycleTransitionServiceTest -Dsurefire.failIfNoSpecifiedTests=false` | 缺少迁移命令、存储端口、不可变结果和领域服务，测试无法编译。 |
| PostgreSQL 红灯 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=PostgresTenantLifecycleTransitionStoreTest -Dsurefire.failIfNoSpecifiedTests=false` | 缺少生命周期迁移 Mapper 与 PostgreSQL Store，测试无法编译。 |
| 自动配置红灯 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=OrganizationAccessAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false` | 新增迁移 Store 后，夹具没有 Mapper mock，启用上下文无法启动。 |
| HTTP 红灯 | `./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am test -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false` | 平台租户路由尚未注册，平台建立和租户会话隔离断言得到 `404`。 |
| 分层红灯 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=OrganizationAccessDependencyRulesTest -Dsurefire.failIfNoSpecifiedTests=false` | 初版 Controller 直接依赖领域动作、标识和聚合，ArchUnit 拒绝接口层越过 application。 |
| 绿灯 | 上述定向命令及 `./mvnw -q -pl backend/contexts/organization-access -am test` | 命名领域服务只调用聚合合法迁移；PostgreSQL 使用根锁与修订比较，在同一事务写最小发件箱；自动配置、应用视图与 HTTP 入口均通过分层门禁。 |
| Bootstrap HTTP 绿灯 | 同一 `AuthenticationApiTest` 命令 | 平台 Bearer 可建立、激活、挂起和恢复租户；`PROVISIONING`／`SUSPENDED` 均不能取得新租户会话，恢复为 `ACTIVE` 后恢复；租户 Bearer 与请求中的伪造 `subjectId` 分别得到 `401` 与 `400`。 |

`TenantLifecycleTransition` 返回根与最小状态事实一致的不可变结果。应用层负责授权、事务、路径标识
翻译和应用视图；PostgreSQL Store 只重建根、执行锁与版本比较、提交结果，不解释动作、不重写遗留
`tenant.status`。接口层只依赖 application DTO，避免 HTTP 直接拥有领域模型。

演示种子现在明确表示两条历史演示租户已完成 `PROVISIONING → ACTIVE` 的合法事件链、管理员确认和
认证切换；它仅是可重建的演示基线，不替代 ADR-0044 规定的生产人工映射与原子回填。

### 六维评估

| 维度 | 结论 | 依据 |
| --- | --- | --- |
| 正确性 | 通过 | 纯领域反例、应用授权、真实 PostgreSQL 发件箱回滚及 Bootstrap 状态机回归覆盖同一状态事实。 |
| 安全性 | 通过 | `PlatformRequestContext` 只由 Bearer 恢复；租户令牌无法使用平台路由，严格请求白名单拒绝伪造当前主体字段。 |
| 复杂度 | 可接受 | 只增加一个迁移端口、一个应用视图和四个明确路由；未引入通用状态机或租户切换框架。 |
| 冗余 | 可接受 | 兼容列保留但不参与迁移和切换后认证；领域根、应用视图和 HTTP 响应各自仅服务一个边界。 |
| 性能 | 可接受 | 单根 `FOR UPDATE`、固定次数版本更新与发件箱写入；没有扫描主体、成员或正文。 |
| 测试质量 | 通过 | 保存实际缺符号、缺 Bean、缺路由、分层违规四类红灯，以及真实 PostgreSQL、Bearer HTTP 和模块回归绿灯。 |

## 仍未完成的浏览器角色任务

当前首条切片已具备真实 Bearer HTTP 闭环，但控制台尚无租户生命周期治理页面，因而无法把平台管理员
创建／状态迁移及租户管理员不可见性作为浏览器任务复核。该页面和浏览器证据仍是 WP-0014 的未完成
范围；在补齐前不得申请 M6 自动或人工评审，也不得激活 M7。

## 真实 PostgreSQL 合同

- 回填输入只在受控部署事务中使用，不写入版本库、日志、会话、HTTP 请求或普通应用配置。
- 一个历史租户必须恰有一条有效映射；每个映射主体必须是目标租户现有、已启用成员。
- 任何批次失败都不允许一部分租户拥有生命周期根，或一部分成员被升级为管理员。
- `TenantLifecycle` 与成员资格是独立聚合；跨根一致性只通过 `TenantProvisioningResult` 和原子提交实现，仓储不补写业务规则。
- 最小事件只含租户标识、状态、版本和发生时间，不含主体凭据、成员详情、正文、令牌或密钥；不得直接构造 `ACTIVE` 或 `SUSPENDED` 持久化状态。

## 验证命令

实现开始后先运行新增测试的同一红绿命令，再依次执行：

```bash
./mvnw -q -pl backend/contexts/organization-access -am test
./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am test -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false
python3 scripts/quality/backend_architecture_guard.py verify
make project-verify
```

## 未决边界

ADR-0044 至 ADR-0047 已接受并已完成相应后端切片。生产历史映射的真实租户与主体值仍仅由部署
责任人于执行窗口提供；本仓库只保存机制、验证和不含敏感数据的审计摘要要求。平台生命周期控制台、
实际浏览器角色任务和其余 M6 评测／预算／发布审批／审计导出切片仍未完成。
