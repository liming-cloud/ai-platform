# WP-0014：平台账户受控密码重置证据

## 范围与设计边界

本切片落实 ADR-0057 与 ADR-0061 的受控凭据重置闭环。只有拥有 `platform:accounts:manage` 的
token／cookie 派生 `PlatformRequestContext` 能重置路径目标账户的密码；领域守卫拒绝重置自身凭据，
不存在自助找回、邮件邀请、服务账户或外部身份联邦。

新口令只通过严格 HTTP 请求和 `char[]` 进入既有 PBKDF2-HMAC-SHA256 摘要端口。应用服务、HTTP 请求
对象和控制台 Dialog 结束时清除其可清除副本；响应、目录、日志和审计投影均不包含口令、摘要、token、
会话或完整权限事实。重置允许活动与已禁用目标账户，并只替换摘要、推进修订，不改变账户状态或角色。

应用层锁定目标账户后，在同一事务写入新摘要并调用 `PersonSessionFamilyRevoker` 撤销目标全部平台与
租户会话。撤销失败时密码摘要与修订必须回滚。统一控制台只使用一个 Fluent UI `Dialog`，说明会话影响；
活动账户将角色操作收纳至菜单，行级常用操作保持为重置口令与管理角色，避免按钮堆叠。

## 代码图谱与影响

代码图谱探索 `PlatformAccountGovernance.resetPassword`、`SubjectIdentity.resetPassword`、
`PlatformAccountGovernanceApplicationService`、`PersonSessionFamilyRevoker`、`Pbkdf2PasswordAdapter`、
`PlatformAccountGovernanceController`、`PlatformAccountDirectoryWorkspace` 与 `ControlPlaneClient`。影响分析
限定为治理服务构造点、自动配置、真实 PostgreSQL 账户存储测试、HTTP 控制器与现有目录；认证协议、
角色绑定模型和导航能力事实不变。

## TDD 红绿记录

1. 红：先新增应用、MockMvc 与客户端重置测试，执行：

   ```bash
   /tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
     -pl contexts/organization-access -am \
     -Dtest=PlatformAccountGovernanceApplicationServiceTest,PlatformAccountGovernanceControllerTest \
     -Dsurefire.failIfNoSpecifiedTests=false test

   cd frontend && npm test -- --run apps/console-web/src/api/control-plane-client.test.ts
   ```

   后端编译失败：治理服务尚无密码摘要依赖和 `resetPassword`；前端失败：
   `resetPlatformAccountPassword is not a function`。

2. 绿：补齐同一治理服务的受控摘要、事务编排、严格 HTTP 请求与前端命令后，后端 11 项、客户端 30 项、
   类型检查和 lint 通过。

3. 绿：增加严格请求反例和真实 PostgreSQL Testcontainers 合同后，27 项后端回归通过。生产 PBKDF2
   适配器证明新摘要可验证；会话撤销失败时旧摘要和修订仍可查询，成功时摘要替换且修订递增。

## 已执行验证

```bash
/tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
  -pl contexts/organization-access -am \
  -Dtest=OrganizationAccessAutoConfigurationTest,PlatformAccountGovernanceTest,PlatformAccountGovernanceApplicationServiceTest,PlatformAccountGovernanceControllerTest,PlatformAccountPasswordResetRequestTest,PostgresPlatformAccountStoreTest,PlatformAccountEstablishmentApplicationServiceTest,PlatformAccountEstablishmentControllerTest,PlatformAccountEstablishmentRequestTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

cd frontend && npm test && npm run typecheck && npm run lint
make architecture-guard
make project-verify
```

MockMvc 固定 `204 No Content`、目标主体只从路径读取且平台上下文只从解析器恢复。真实 PostgreSQL
Testcontainers 回归使用生产 PBKDF2 摘要器验证最终持久化事实和事务回滚；前端全量回归为 16 个文件、
87 项测试。自动配置回归证明治理服务仍仅在组织权限上下文启用时装配。

## 浏览器验收状态

正式 HTTPS 浏览器角色任务仍待受信任部署环境。依据
`project/evidence/ADR-0048-local-https-deferment.md` 与
`project/evidence/WP-0014-browser-tls-preflight.md`，本地标准 TLS 校验拒绝未受信任证书；本切片不安装
System keychain CA、不忽略证书错误，也不以 HTTP 代替浏览器证据。受信任环境仍需复核：管理员对其他
活动／禁用账户打开单一重置 Dialog、提交后新口令可重新认证、旧平台／租户 cookie 均失效、管理员无法
重置自身凭据。

## 六维审查

| 维度 | 结论 |
| --- | --- |
| 正确性 | 领域自重置拒绝、应用授权与清零、严格 HTTP、真实 PBKDF2／PostgreSQL 提交及回滚均有回归。 |
| 复杂度 | 重用已有账户治理、账户锁、乐观更新和会话撤销，只增加一个受控密码命令。 |
| 冗余 | 不复制认证、凭据或会话模型；摘要端口复用账户建立的既有 PBKDF2 实现。 |
| 性能 | 单账户锁定和单次乐观更新维持既有写路径；控制台写后只重读当前服务器页。 |
| 架构 | 领域决定自重置禁止，应用层管理授权、事务、摘要和撤销，接口层只解析严格业务字段。 |
| 测试质量 | 记录实际编译与客户端红灯，并用真实生产适配器和最终数据库事实证明回滚，未以 mock 顺序替代事务合同。 |

本证据绑定本次提交。WP-0014 与 M6 保持进行中；M6 自动验收和人工评审通过前不得进入 M7。
