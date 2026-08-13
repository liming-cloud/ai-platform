# WP-0014：平台管理员固定角色与导航投影

## 范围

落实 ADR-0065 已接受的最小角色切片：发布 `PLATFORM_ADMINISTRATOR` 固定角色及其四项平台权限，
使其可由真实 PostgreSQL 角色绑定持久化，并投影到统一 SPA 的角色管理与浏览器会话显示。平台范围
连接、部署和目录实现仍受待接受的 ADR-0066 约束，不属于本切片。

## 红绿过程

1. 新增 `PlatformRoleBindingTest.平台管理员绑定应派生目录治理所需的固定权限` 后执行：

   ```bash
   ./mvnw -pl backend/contexts/organization-access -am \
     -Dtest=PlatformRoleBindingTest -Dsurefire.failIfNoSpecifiedTests=false test
   ```

   红灯：`PlatformRole.PLATFORM_ADMINISTRATOR` 尚未定义。
2. 增加角色模板、`model-platform:write`、账户目录角色选项及 Flyway
   `V202608130001__add_platform_administrator_role.sql` 后，同一用例绿灯。
3. 本地 Compose HTTP 首次绑定返回 500；控制面日志确认 PostgreSQL
   `ck_oa_platform_role_binding_role` 未包含新角色。这是新增迁移前的预期真实基础设施红灯。
4. 新迁移后，`./mvnw -pl backend/contexts/organization-access -am test` 通过：162 项测试，
   Testcontainers PostgreSQL 在每个持久化测试实例中应用至 `202608130001`。
5. HTTP 回归发现会话 `capabilityCodes` 已包含 `model-platform:write`，但 `displayRoles` 漏掉
   `PLATFORM_ADMINISTRATOR`。新增
   `BrowserSessionCapabilityServiceTest.平台管理员必须以独立显示角色投影全部固定平台权限` 后，
   红灯为显示角色枚举尚未定义；补齐枚举与能力投影后，定向 3 项测试通过。

## 真实基础设施与 HTTP

在现有本地 Compose（PostgreSQL、Redis、MinIO、control-plane、console-web）中重建
`control-plane` 和 `console-web`，未重置数据库、Redis 或 MinIO。为本地 `platform.root` 验收账户
更新了非提交的 PBKDF2 摘要，未记录口令、摘要、Cookie 或令牌。

通过 `http://localhost:13000` 执行同源 CSRF 登录、平台角色绑定和重新登录。结果：

- `POST /api/v1/platform/accounts/{subjectId}/role-bindings` 以
  `PLATFORM_ADMINISTRATOR` 返回 200，并使旧会话失效；
- 新会话 `GET /api/v1/auth/browser-session` 返回
  `platform-session:access`、`platform:accounts:manage`、`tenant-governance:write`、
  `model-platform:write`；
- `displayRoles` 同时包含已有的账户管理员、租户治理员及新增的平台管理员，未把任一权限提升为
  租户正文、凭据或探测结果读取能力。

## 浏览器回归

使用本地统一控制台登录同一平台主体（不选择租户范围）：

- 主导航显示“平台工作区”“平台租户”“平台账户”；
- 进入平台账户目录后，`platform.root` 的固定角色徽标显示“平台管理员”；
- 页面保持服务器分页，角色操作仍位于单一“管理角色”菜单；未显示凭据、会话或权限快照。

## 六维检查

- 正确性：领域模板、会话投影、Flyway 约束、HTTP 与浏览器均闭合。
- 复杂度：只增加固定枚举值、一个权限常量和已有投影分支，没有新增抽象或路由。
- 冗余：没有复制角色绑定或会话撤销流程，继续复用既有绑定应用服务。
- 性能：角色投影只在已有会话权限集合上作常数次检查；目录仍是服务器分页。
- 架构：角色与权限语言保留在 organization-access／platform-security；迁移仅变更该上下文的表约束。
- 测试质量：记录了角色定义、真实约束和显示投影的两次红绿，并由 Testcontainers、Compose HTTP 与浏览器补强。
