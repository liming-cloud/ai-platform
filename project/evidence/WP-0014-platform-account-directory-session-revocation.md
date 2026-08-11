# WP-0014：平台账户目录与全范围会话撤销证据

## 范围与设计边界

本切片落实已接受 ADR-0061 的最小可操作部分：持有
`platform:accounts:manage` 的 cookie／token 派生 `PlatformRequestContext` 可以读取平台账户的
服务端分页安全摘要，并停用目标账户。账户目录只返回主体标识、登录名、状态、修订和创建时间；不返回
密码摘要、明文口令、会话、完整权限或平台范围。

停用在 `PlatformAccountGovernanceApplicationService` 内先锁定账户，再由既有
`PlatformAccountGovernance` 保留“最后一个有效账户管理员不可停用”的领域不变量，随后以修订号更新
`SubjectIdentity`。更新成功后必须调用 `PersonSessionFamilyRevoker`，后者同时撤销租户
`AuthenticatedSessionGateway` 和平台 `PlatformSessionGateway` 的同一主体会话族。撤销异常向外传播并
回滚账户更新，不能返回成功。

`SaTokenSessionGateway` 以主体私有的服务端会话索引登记已签发 token；撤销时仅枚举该主体的 token，
不读取其他主体或浏览器 cookie。该索引损坏和底层会话异常均失败关闭。

统一控制台把 `platform:accounts:manage` 与 `PLATFORM_TENANT_GOVERNANCE` 工作区相交后才显示
`/platform/accounts?page=N` 导航。账户目录使用替换式服务端分页；前端仅保存当前页摘要，且不发送
`Authorization` 请求头，不将菜单当作 ACL。

账户建立、启用、受控密码重置、内置平台角色绑定及其 Dialog 仍是后续 ADR-0061 单一切片；本切片没有
提前实现平台自定义角色、个人直接授权、服务账户或 M7 功能。

## 红绿与缺陷修正

新增真实 PostgreSQL 回归后首次启用事务代理，红灯暴露
`PlatformAccountGovernanceApplicationService` 为 `final` 而无法生成 CGLIB 代理。该状态下应用上下文
不能启动，`@Transactional` 也不可能保障会话撤销失败的回滚。去除 `final` 后同一测试转绿，证明撤销器
抛出异常时 `subject_identity.status` 仍为 `ACTIVE`。

应用层回归同时固定两条反例：缺少 `platform:accounts:manage` 时不得读取或锁定目标账户；撤销器失败时
异常必须传播而不能伪造成功。HTTP MockMvc 回归固定分页参数只从 URL 读取，目标账户只从路径读取，
平台上下文只由参数解析器恢复。

## 已执行验证

```bash
/tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
  -pl contexts/organization-access -am \
  -Dtest=PostgresPlatformAccountStoreTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

/tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
  -pl platform/platform-security/platform-security-satoken,contexts/organization-access -am \
  -Dtest=SaTokenSessionGatewayTest,OrganizationAccessAutoConfigurationTest,PlatformAccountGovernanceApplicationServiceTest,PlatformAccountDirectoryApplicationServiceTest,PlatformAccountGovernanceControllerTest,PostgresPlatformAccountStoreTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

cd frontend && npm run typecheck && npm test && npm run lint
make project-verify
git diff --check
```

真实 PostgreSQL `PostgresPlatformAccountStoreTest` 共 5 项通过，覆盖行锁、乐观更新、最后管理员计数、
有序分页和撤销失败回滚。组合后端回归覆盖 Sa-Token 主体会话撤销、自动配置、授权失败关闭和 HTTP
投影。前端类型检查、83 项 Vitest 回归与 ESLint 通过，其中客户端回归固定
`GET /api/v1/platform/accounts?pageNumber=3&pageSize=20` 且没有 `Authorization` 请求头。

## 浏览器验收状态

正式浏览器 HTTPS 角色任务未完成。根据
`project/evidence/ADR-0048-local-https-deferment.md` 与
`project/evidence/WP-0014-browser-tls-preflight.md`，本地证书不受浏览器信任；本切片不安装 System
keychain CA、不忽略证书错误，也不以 HTTP 或自动化单元测试替代正式浏览器证据。受信任部署环境中仍须
复核：账户管理员导航可见、直接 URL 的服务端拒绝、停用后两种范围 cookie 均不能恢复能力。

本证据绑定本次控制面提交。M6 和 WP-0014 保持进行中；M6 自动验收和人工评审通过前不得进入 M7。
