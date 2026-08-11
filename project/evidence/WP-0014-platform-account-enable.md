# WP-0014：平台账户启用与会话撤销证据

## 范围与设计边界

本切片落实 ADR-0061 的已禁用账户启用闭环。拥有 `platform:accounts:manage` 的 token／cookie
派生 `PlatformRequestContext` 只能启用路径中的目标账户；请求不接收平台范围、操作者、权限、会话或
菜单能力，且 HTTP 请求体为空。

`SubjectIdentity.enable()` 只允许从 `DISABLED` 进入 `ACTIVE`，重复启用活动账户失败关闭。应用服务在
锁定目标主体后，以乐观修订更新账户，并在同一事务调用 `PersonSessionFamilyRevoker`。撤销器失败时，
账户状态与修订均回滚；成功时账户状态变为 `ACTIVE`、修订递增，原有平台和租户范围会话均失效。

统一控制台的服务端分页账户目录只会为 `DISABLED` 账户显示“启用账户”。操作必须通过单一 Fluent UI
`Dialog` 确认，成功后重读服务器页；不修改既有停用、角色绑定或密码重置边界。账户建立和受控密码重置
涉及新的凭据写入与秘密生命周期，保留为后续独立切片。

## 代码图谱与影响

代码图谱探索 `SubjectIdentity`、`PlatformAccountGovernance`、
`PlatformAccountGovernanceApplicationService`、`PlatformAccountGovernanceController`、
`PlatformAccountMapper`、`PlatformAccountDirectoryWorkspace` 与 `ControlPlaneClient`，确认启用可复用现有
领域守卫、账户行锁、乐观更新和全范围会话撤销端口。影响分析限定到账户治理应用服务、真实 PostgreSQL
存储测试、自动配置和控制器测试；未改变认证、密码编码或导航授权事实。

## TDD 红绿记录

1. 红：`PlatformAccountGovernanceTest` 新增重复启用反例后执行：

   ```bash
   /tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
     -pl contexts/organization-access -am \
     -Dtest=PlatformAccountGovernanceTest \
     -Dsurefire.failIfNoSpecifiedTests=false test
   ```

   失败原因为活动账户未抛出 `IllegalStateException`。

2. 绿：在 `SubjectIdentity.enable()` 拒绝活动账户后，同一测试 5 项通过。

3. 红：新增应用服务启用回归后，编译失败，`PlatformAccountGovernanceApplicationService.enable` 尚不存在。

4. 红：新增 MockMvc 回归后，`POST /api/v1/platform/accounts/102/enable` 返回 404。

5. 红：新增前端客户端回归后，`enablePlatformAccount` 缺失，调用抛出 `TypeError`。

6. 绿：补齐应用、HTTP 与前端最小路径契约后，相关后端 30 项、前端 85 项回归全部通过。

## 已执行验证

```bash
/tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
  -pl contexts/organization-access -am \
  -Dtest=OrganizationAccessAutoConfigurationTest,PlatformAccountGovernanceTest,PlatformAccountGovernanceApplicationServiceTest,PlatformAccountDirectoryApplicationServiceTest,PlatformAccountGovernanceControllerTest,PostgresPlatformAccountStoreTest,PlatformRoleBindingGovernanceTest,PlatformRoleBindingApplicationServiceTest,PostgresPlatformRoleBindingStoreTest,PlatformRoleBindingControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

cd frontend && npm test && npm run typecheck && npm run lint
make architecture-guard
make project-verify
```

`PostgresPlatformAccountStoreTest` 的 7 项真实 PostgreSQL Testcontainers 回归同时证明：启用成功会写入
`ACTIVE` 与递增修订；会话撤销失败时不提交启用，也不提交停用。MockMvc 固定无请求体的路径目标和
`PlatformRequestContext` 参数解析；客户端回归固定同源 `POST`、空请求体与无 `Authorization` 请求头。

`make architecture-guard` 通过 24 项门禁测试、复杂 SQL 审计和前端服务端分页／嵌套 Dialog 门禁。

## 浏览器验收状态

正式 HTTPS 浏览器角色任务仍待受信任部署环境。依据
`project/evidence/ADR-0048-local-https-deferment.md` 与
`project/evidence/WP-0014-browser-tls-preflight.md`，本地标准 TLS 校验拒绝未受信任证书；本切片不安装
System keychain CA、不忽略证书错误，也不以 HTTP 代替浏览器证据。受信任环境仍需复核：仅禁用账户可见
启用操作、确认后目录状态刷新、目标主体旧范围 cookie 不能恢复能力、重复启用被服务端拒绝。

## 六维审查

| 维度 | 结论 |
| --- | --- |
| 正确性 | 状态机、应用、真实 PostgreSQL、HTTP 和客户端回归覆盖启用、重复拒绝、提交及回滚。 |
| 复杂度 | 复用既有账户治理、行锁和会话撤销端口，只新增同类 `enable` 编排与入口。 |
| 冗余 | 未复制凭据、会话或授权模型；禁用与启用保留清晰的独立动作。 |
| 性能 | 单主体锁定与乐观修订保持既有写路径；目录仍为替换式服务端分页。 |
| 架构 | 领域只表达状态机，应用层持有事务与授权，接口层只解析受管路径资源。 |
| 测试质量 | 记录实际断言、编译、404 与客户端红灯，并以真实数据库提交/回滚而非 mock 顺序作为持久化证据。 |

本证据绑定本次提交。WP-0014 与 M6 保持进行中；M6 自动验收和人工评审通过前不得进入 M7。
