# WP-0014：平台账户建立证据

## 范围与设计边界

本切片落实 ADR-0061 的平台账户建立闭环。仅拥有 `platform:accounts:manage` 的 token／cookie
派生 `PlatformRequestContext` 可以提交全局登录名和一次性初始口令；HTTP 请求严格拒绝主体、角色、权限、
平台范围、会话或菜单能力字段。

账户建立默认产生 `ACTIVE` 的全局 `SubjectIdentity`，修订为 `0`，且不绑定任何平台角色。初始口令只以
`char[]` 进入 `PasswordHasher`，应用服务与 HTTP 请求对象在结束时清零其副本；响应和目录投影均不包含
口令、摘要、会话或权限快照。`Pbkdf2PasswordAdapter` 复用既有 PBKDF2-HMAC-SHA256 实现作为摘要端口。

`subject_identity.login_name` 的既有唯一约束是并发事实源。`INSERT ... ON CONFLICT (login_name) DO NOTHING`
返回零行时应用服务失败关闭，避免“先查后写”竞争、第二个主体和任何隐式角色授权。建立账户不撤销会话，
因为新主体尚未拥有可撤销会话；停用、启用、角色变更和密码重置的会话语义保持各自独立切片。

统一控制台在同一 Fluent UI `Dialog` 中收集登录名和初始口令，成功后清空页面口令状态并重读服务器页；
非首页时回到第一页读取新建摘要。该切片不实现受控密码重置、邀请、服务账户或平台自定义角色。

## 代码图谱与影响

代码图谱探索 `SubjectIdentity.register`、`PlatformAccountStore`、`PostgresPlatformAccountStore`、
`PlatformAccountMapper`、`Pbkdf2PasswordAdapter`、`PlatformAccountGovernanceController`、
`PlatformAccountDirectoryWorkspace` 与 `ControlPlaneClient`。随后对 `PlatformAccountStore` 执行影响分析：新增
`insert(SubjectIdentity)` 只影响 PostgreSQL 适配器、既有账户治理测试替身、新账户建立应用服务及其自动配置；
认证、平台角色绑定和导航投影不变。

## TDD 红绿记录

1. 红：先新增账户建立应用服务、HTTP 和前端客户端用例，执行：

   ```bash
   /tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
     -pl contexts/organization-access -am \
     -Dtest=PlatformAccountEstablishmentApplicationServiceTest,PlatformAccountEstablishmentControllerTest \
     -Dsurefire.failIfNoSpecifiedTests=false test
   ```

   编译失败：`PlatformAccountEstablishmentApplicationService`、建立 HTTP 控制器和账户存储写入契约均不存在。

2. 红：前端从错误工作目录执行 `npm test`，显式失败为该 workspace 没有独立 `test` 脚本；随后改从
   `frontend` 根目录执行，以仓库规定的 workspace 脚本为准。

3. 绿：补齐受权应用服务、PBKDF2 摘要端口、严格请求、`POST /api/v1/platform/accounts`、PostgreSQL
   原子插入和客户端后，相关后端 6 项与客户端 29 项测试通过。

4. 绿：增加严格请求反例及真实 PostgreSQL 合同后，15 项后端回归通过；真实数据库证明第一次插入提交、
   重复登录名写入返回失败且仅保留第一条主体事实。

## 已执行验证

```bash
/tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
  -pl contexts/organization-access -am \
  -Dtest=OrganizationAccessAutoConfigurationTest,PlatformAccountEstablishmentApplicationServiceTest,PlatformAccountEstablishmentControllerTest,PlatformAccountEstablishmentRequestTest,PostgresPlatformAccountStoreTest,PlatformAccountGovernanceApplicationServiceTest,PlatformAccountGovernanceControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

cd frontend && npm test && npm run typecheck && npm run lint
make architecture-guard
make project-verify
```

前端全量回归为 16 个文件、86 项测试；类型检查和 lint 均通过。MockMvc 固定 `201 Created`、仅由
参数解析器恢复的平台上下文及严格业务字段。真实 PostgreSQL Testcontainers 回归固定唯一约束与最终提交
事实；自动配置回归证明账户建立服务和控制器只在组织权限上下文启用时装配。

## 浏览器验收状态

正式 HTTPS 浏览器角色任务仍待受信任部署环境。依据
`project/evidence/ADR-0048-local-https-deferment.md` 与
`project/evidence/WP-0014-browser-tls-preflight.md`，本地标准 TLS 校验拒绝未受信任证书；本切片不安装
System keychain CA、不忽略证书错误，也不以 HTTP 代替浏览器证据。受信任环境仍需复核：平台账户管理员
可建立无角色账户、Dialog 提交后页面重新读取服务端摘要、初始口令不被回显，以及新账户在赋予角色前不能
访问平台工作区。

## 六维审查

| 维度 | 结论 |
| --- | --- |
| 正确性 | 应用、严格 HTTP、真实 PostgreSQL 唯一约束、自动配置与客户端回归覆盖账户建立和重复拒绝。 |
| 复杂度 | 新增一个密码摘要端口和一个建立编排服务；未修改既有状态治理或认证流程。 |
| 冗余 | 复用 `SubjectIdentity.register`、PBKDF2 适配器、账户目录摘要与既有唯一约束，不复制凭据或角色事实。 |
| 性能 | 单次原子插入替代预查询；目录继续使用替换式服务端分页，创建后只重读目标页。 |
| 架构 | 接口层严格解析业务字段，应用层持有授权、事务和秘密副本清理，基础设施层持有 SQL 与 PBKDF2。 |
| 测试质量 | 保留服务和入口不存在的实际红灯；以 Testcontainers 最终数据库事实验证唯一约束，未以 mock 调用顺序代替持久化合同。 |

本证据绑定本次提交。WP-0014 与 M6 保持进行中；M6 自动验收和人工评审通过前不得进入 M7。
