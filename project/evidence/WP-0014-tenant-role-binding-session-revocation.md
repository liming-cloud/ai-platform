# WP-0014：租户角色绑定与旧会话撤销

## 范围与结论

本证据只覆盖 ADR-0057 的最小写入闭环：已授权租户成员为同租户已启用成员绑定
`TENANT_ACCESS_ADMINISTRATOR`，该绑定投影为 `access:manage`，推进目标成员权限版本，并撤销其全部
租户范围会话。账户建立、角色撤销、自定义角色、角色目录、直授权限清理和前端管理工作台仍未实施。

实现保持 token 派生身份：HTTP 不接收租户、当前主体、权限或会话；`TrustedRequestContext` 由既有参数
解析器恢复。平台会话不参与也不会被撤销。

## 代码图谱与影响

- `AuthenticatedSessionGateway` 的影响分析列出认证服务、浏览器会话、Sa-Token 适配器和控制面 HTTP
  测试；因此新增按 `MembershipId` 撤销操作，并同步更新三个测试替身。
- `TenantProvisioningResult`、`TenantProvisioningMapper` 与 `PostgresTenantProvisioningStore` 的流程探索
  表明新租户初始管理员在同一事务创建成员资格；初始内置角色绑定因而纳入同一不可变建立结果。
- `TenantMembershipMapper` 的权限读取路径原只读取 `membership_permission`；现以联合投影读取角色绑定
  的 `access:manage`，但不向遗留直授权限表写入新授权。

## TDD 红绿记录

1. 红：

   ```bash
   ./mvnw -q -pl backend/platform/platform-security/platform-security-satoken -am \
     -Dtest=SaTokenSessionGatewayTest -Dsurefire.failIfNoSpecifiedTests=false test
   ```

   新增按成员撤销反例后，因 `revokeMembershipSessions(MembershipId)` 尚不存在而测试编译失败。

2. 绿：同一命令在 Sa-Token 适配器实现后通过，证明同一成员的两个租户令牌均不可再恢复上下文。

3. 绿：

   ```bash
   ./mvnw -q -pl backend/contexts/organization-access -am \
     -Dtest=TenantRoleBindingServiceTest,TenantRoleBindingApplicationServiceTest,\
PostgresTenantRoleBindingStoreTest,OrganizationAccessAutoConfigurationTest,\
OrganizationAccessDependencyRulesTest -Dsurefire.failIfNoSpecifiedTests=false test
   ```

   纯领域反例、授权前置、重复绑定、真实 PostgreSQL 提交／撤销失败回滚、自动配置和层间依赖均通过。

4. 绿：

   ```bash
   ./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am \
     -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false test
   ./mvnw -q -pl backend/platform/platform-security/platform-security-satoken -am test
   ./mvnw -q -pl backend/contexts/organization-access -am test
   python3 scripts/quality/backend_architecture_guard.py verify
   python3 -m unittest scripts.quality.test_backend_architecture_guard
   make project-verify
   ```

   控制面 HTTP 回归、平台安全模块、组织访问全模块、架构门禁（19 项）、项目治理均通过。测试日志中的
   Testcontainers、Spring 和 MyBatis Mapper 重复扫描提示为既有测试装配告警，未出现失败。

## 安全与一致性反例矩阵

| 反例 | 证据 |
| --- | --- |
| 无 `access:manage` 伪造写入 | 应用测试证明不读取目标成员、不提交也不撤销会话。 |
| 跨租户或已停用成员绑定 | 领域服务拒绝；存储查询同时限定租户和 `ENABLED`。 |
| 重复绑定 | 受锁事务内拒绝，复合主键继续保护并发重复写入。 |
| 会话撤销失败 | 真实 PostgreSQL 测试证明绑定行与权限版本同时回滚。 |
| 目标旧令牌继续使用 | Sa-Token 测试和控制面 HTTP 测试均证明令牌不可恢复。 |
| 接口伪造当前身份或租户 | HTTP 仅接收成员资源标识与角色码；上下文来自 token。 |

## 六维审查

| 维度 | 结论 |
| --- | --- |
| 正确性 | 领域、PostgreSQL 与 HTTP 回归覆盖绑定、版本推进与旧会话失效。 |
| 复杂度 | 只增加一个内置角色、一个操作结果和一个写入端口，未预建自定义角色框架。 |
| 冗余 | 保留 `membership_permission` 仅读兼容；新增授权不再写入该表。 |
| 性能 | 成员行锁与复合主键仅覆盖单成员绑定，权限投影为有界联合查询。 |
| 架构 | 接口层改为应用 DTO；组织上下文层间 ArchUnit 与后端架构门禁通过。 |
| 测试质量 | 记录实际红灯；使用真实 PostgreSQL、Redis／Sa-Token 和控制面 HTTP，不以 mock 调用顺序代替回滚或会话失效证据。 |

## 提交与状态

实现提交：`30554551c feat: 完成租户角色绑定与会话撤销闭环`。WP-0014 和 M6 保持进行中，不进入 M7。
