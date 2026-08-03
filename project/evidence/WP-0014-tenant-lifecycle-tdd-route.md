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
3. **历史回填绿灯**：完整的一对一映射只创建一个 `TenantLifecycle`，将被映射的成员确认成 `TENANT_ADMINISTRATOR`，并写入最小事件；原租户标识、创建时间和会话语义保持不变。
4. **新租户建立红绿**：平台应用服务只接受 `PlatformRequestContext`，使用 `TenantProvisioningResult` 在单一事务写生命周期、管理员成员资格与事件；同租户／编码冲突失败方不覆盖成功方。
5. **状态迁移真实事务**：激活、挂起、恢复均使用范围锁或乐观修订；过期版本、错误状态和事件写入失败必须回滚根状态。
6. **自动配置与 HTTP**：独立平台 Bearer 才能调用生命周期路由；租户 Bearer、伪造 Header、请求体中的操作者字段和无 `tenant-governance:write` 均失败关闭。
7. **浏览器角色任务**：平台管理员完成新租户建立与状态变更；租户管理员不出现平台入口，也不能以地址或令牌访问。该步骤只在 HTTP 合约被 ADR-0044 接受后的实现中执行。

## 已完成首个红绿

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| 红灯 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=TenantLifecycleBackfillTest -Dsurefire.failIfNoSpecifiedTests=false` | 缺少 `TenantLifecycleBackfill`，测试编译失败。 |
| 绿灯 | 同一命令 | 通过；缺失映射拒绝，明确主体映射仅生成 `PROVISIONING` 生命周期根。 |

该切片只固定领域转换，不读取数据库、不改变成员资格、不写事件，也不代替后续真实 PostgreSQL 回填合同。

## 真实 PostgreSQL 合同

- 回填输入只在受控部署事务中使用，不写入版本库、日志、会话、HTTP 请求或普通应用配置。
- 一个历史租户必须恰有一条有效映射；每个映射主体必须是目标租户现有、已启用成员。
- 任何批次失败都不允许一部分租户拥有生命周期根，或一部分成员被升级为管理员。
- `TenantLifecycle` 与成员资格是独立聚合；跨根一致性只通过 `TenantProvisioningResult` 和原子提交实现，仓储不补写业务规则。
- 最小事件只含租户标识、状态、版本和发生时间，不含主体凭据、成员详情、正文、令牌或密钥。

## 验证命令

实现开始后先运行新增测试的同一红绿命令，再依次执行：

```bash
./mvnw -q -pl backend/contexts/organization-access -am test
./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am test -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false
python3 scripts/quality/backend_architecture_guard.py verify
make project-verify
```

## 未决边界

在 ADR-0044 接受前，不得创建历史回填迁移或平台生命周期 HTTP 接口。映射来源的真实租户与主体值
由部署责任人于执行窗口提供；本仓库只保存机制、验证和不含敏感数据的审计摘要要求。
