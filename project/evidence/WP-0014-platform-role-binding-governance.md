# WP-0014：平台固定角色绑定治理证据

## 范围与设计边界

本切片落实已接受 ADR-0061 的固定平台角色绑定与撤销闭环。只有持有
`platform:accounts:manage` 的 token／cookie 派生 `PlatformRequestContext` 能为路径中的目标
`SubjectIdentity` 绑定或撤销 `PLATFORM_ACCOUNT_ADMINISTRATOR`、`PLATFORM_TENANT_GOVERNOR` 与
`PLATFORM_OPERATOR`。请求体只接受绑定操作的 `role`；撤销只接受路径中的固定角色代码，不接受平台范围、
当前主体、权限、会话或导航能力。

`PlatformRoleBindingGovernance` 只允许对活动账户建立未存在的绑定，并在撤销活动账户的
`PLATFORM_ACCOUNT_ADMINISTRATOR` 时保护最后一个有效账户管理员。应用服务先锁定目标主体，再在同一事务
写入绑定状态和平台授权修订，并调用 `PersonSessionFamilyRevoker` 撤销该主体全部平台及租户会话。撤销器
异常会回滚角色绑定和授权修订，不能返回成功。

账户目录仍为页码、页大小和总数的服务端投影；每个账户只额外返回有界的活动固定角色代码，既不返回
完整权限快照，也不返回凭据或会话。统一控制台逐项显示固定角色，绑定与撤销均使用单一 Fluent UI
`Dialog` 确认，成功后重新读取当前服务器页。导航说明仅解释固定角色的已发布工作区投影，不构成授权。

平台自定义角色、个人直接授权、服务账户、账户建立／启用和受控密码重置不在本切片范围内。

## TDD 红绿记录

1. 红：新增 `PlatformRoleBindingGovernanceTest` 后，执行：

   ```bash
   /tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
     -pl contexts/organization-access -am \
     -Dtest=PlatformRoleBindingGovernanceTest \
     -Dsurefire.failIfNoSpecifiedTests=false test
   ```

   因 `PlatformRoleBindingGovernance` 尚不存在而编译失败。

2. 绿：同一领域测试通过，覆盖未启用账户、重复绑定、最后活动账户管理员和绑定归属不一致的失败关闭。

3. 红：新增 `PlatformRoleBindingApplicationServiceTest` 后，因
   `PlatformRoleBindingStore` 与应用服务尚不存在而编译失败。

4. 绿：应用层测试通过，证明无账户治理权限时不读取目标主体；绑定和撤销均推进授权修订并撤销全范围
   会话。

5. 红：新增 `PostgresPlatformRoleBindingStoreTest` 后，因真实 PostgreSQL 存储尚不存在而编译失败。

6. 绿：真实 PostgreSQL 测试通过，证明绑定、授权修订和全范围会话撤销处于同一事务；撤销器抛出异常时
   不残留绑定行或授权修订。

## 已执行验证

```bash
/tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
  -pl contexts/organization-access -am \
  -Dtest=OrganizationAccessAutoConfigurationTest,PlatformAccountGovernanceApplicationServiceTest,PlatformAccountDirectoryApplicationServiceTest,PlatformAccountGovernanceControllerTest,PostgresPlatformAccountStoreTest,PlatformRoleBindingGovernanceTest,PlatformRoleBindingApplicationServiceTest,PostgresPlatformRoleBindingStoreTest,PlatformRoleBindingControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

cd frontend && npm test -- --run apps/console-web/src/api/control-plane-client.test.ts \
  && npm run typecheck && npm run lint

make architecture-guard
make project-verify
```

后端共 21 项通过，其中 `PostgresPlatformAccountStoreTest` 与
`PostgresPlatformRoleBindingStoreTest` 在真实 PostgreSQL Testcontainers 中通过；自动配置、MockMvc HTTP
投影和最后管理员保护均被覆盖。前端 27 项控制平面客户端回归、类型检查与 ESLint 通过，固定 POST／DELETE
路径、最小 JSON 请求与无 `Authorization` 请求头。

新增与既有平台账户 XML 语句已登记到
`project/architecture-debt/complex-sql-allowlist.json`；`make architecture-guard` 通过 24 项门禁测试、
后端复杂 SQL 登记和前端分页／嵌套 Dialog 门禁。

## 浏览器验收状态

正式 HTTPS 浏览器角色任务仍未完成。标准 TLS 校验拒绝本地未受信任证书；依据
`project/evidence/ADR-0048-local-https-deferment.md` 与
`project/evidence/WP-0014-browser-tls-preflight.md`，本切片不安装 System keychain CA、不忽略证书错误，
也不以 HTTP 代替浏览器证据。受信任部署环境仍须复核：账户管理员可见角色操作、写后角色与导航投影刷新、
直接 URL 无权拒绝，以及角色变更后平台与租户 cookie 均不可恢复能力。

## 六维审查

| 维度 | 结论 |
| --- | --- |
| 正确性 | 领域、应用、真实 PostgreSQL 与 HTTP 回归覆盖角色不变量、授权修订及会话撤销。 |
| 复杂度 | 复用既有角色绑定模型和会话撤销端口，只增加平台端的最小写入存储与服务。 |
| 冗余 | 账户目录通过单次有界聚合读取活动角色代码，不增加权限快照或 N+1 查询。 |
| 性能 | 单主体行锁串行化角色写入；目录维持服务端分页。 |
| 架构 | 接口层只委派应用服务，`infrastructure` 实现持久化端口，导航继续是能力投影。 |
| 测试质量 | 记录实际编译红灯，并使用真实 PostgreSQL、事务回滚和 HTTP 投影，不只验证 mock 调用顺序。 |

本证据绑定本次提交。WP-0014 与 M6 保持进行中；M6 自动验收和人工评审通过前不得进入 M7。
