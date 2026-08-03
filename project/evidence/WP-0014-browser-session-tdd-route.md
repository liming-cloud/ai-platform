# WP-0014：统一浏览器会话与平台治理控制台 TDD 路线

## 状态与边界

| 项目 | 内容 |
| --- | --- |
| 状态 | ADR-0048 已接受；范围 cookie、统一认证编排、受限 HTTP／CSRF 和无 token 能力清单切片已完成红绿，前端与真实浏览器任务仍未实施。 |
| 工作包 | WP-0014，M6。 |
| 前提 | 产品、架构和安全负责人接受 ADR-0048 方案 B，并将其移入 `confirmedDecisions`。 |
| 目标 | 一个同源登录入口跨页签恢复角色允许的工作区，同时保持平台与租户会话不可互用。 |
| 范围外 | 成员列表、邀请、密码重置、授权委派、正文导出、评测、预算、发布审批和 M7／M8。 |

当前 `ControlPlaneClient` 的影响分析覆盖 42 个符号；`query-web` 与 `console-web` 分别保存内存
Bearer token。迁移必须从平台安全会话入口开始，不能先让页面存储 token 或创建平台登录表单。

## 切片一：范围 cookie 解析器红绿记录

先在 `SaTokenTrustedContextArgumentResolverTest` 与
`SaTokenPlatformContextArgumentResolverTest` 新增“正确范围 cookie 恢复、另一范围 cookie 拒绝”反例。
以下命令在尚未实现 cookie 提取时实际失败，两个失败都停在 `BearerTokens.require`，因为当前解析器只
读取 `Authorization`：

```bash
./mvnw -q -pl backend/platform/platform-security/platform-security-satoken -am test \
  -Dtest=SaTokenTrustedContextArgumentResolverTest,SaTokenPlatformContextArgumentResolverTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

最小绿灯新增固定的租户／平台 cookie 名称和受限 token 提取器；Bearer 仍优先用于机器调用，缺少
Bearer 时解析器只读取各自 cookie 名称。相同定向命令转绿；随后实际执行：

```bash
./mvnw -q -pl backend/platform/platform-security/platform-security-satoken -am test
python3 scripts/quality/backend_architecture_guard.py verify
git diff --check
```

三项均通过。该切片尚未签发 cookie、改变登录响应或引入 CSRF；这些仍由后续红绿切片负责。

## 切片二：统一浏览器认证编排红绿记录

先添加 `BrowserAuthenticationServiceTest`，要求一次口令验证后按已验证成员资格与平台授权分别签发
租户／平台会话。以下命令实际因 `BrowserLoginCommand` 与 `BrowserAuthenticationService` 不存在而在
测试编译阶段失败：

```bash
./mvnw -q -pl backend/contexts/organization-access -am test \
  -Dtest=BrowserAuthenticationServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

最小绿灯创建私有口令命令、脱敏不可变会话签发结果和应用服务。服务只做一次等价口令验证，显式提供
但无效的租户编码失败关闭；租户与平台会话分别由既有端口签发，平台签发失败时撤销已签发租户会话。
相同定向命令转绿，随后实际执行：

```bash
./mvnw -q -pl backend/contexts/organization-access -am test
python3 scripts/quality/backend_architecture_guard.py verify
git diff --check
```

三项均通过。此切片只建立应用编排，不注册 Bean、不写 HTTP cookie，也不向浏览器返回任何 token。

## 切片三：受限 HTTP cookie 与 CSRF 红绿记录

先在真实 PostgreSQL／Redis 的 `AuthenticationApiTest` 增加统一浏览器登录、跨站拒绝反例。以下命令实际
失败：新入口尚不存在，正常登录预期 `204` 实际为 `404`，跨站写入预期 `403` 实际也为 `404`：

```bash
./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am test \
  -Dtest=AuthenticationApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

最小绿灯在 organization-access 注册独立浏览器入口，只以 `Set-Cookie` 写入租户／平台范围会话和
非身份 CSRF 值；浏览器登录响应为 `204` 空体。platform-security 在携带范围 cookie 的非安全请求上
同时校验精确同源 `Origin`、`Sec-Fetch-Site` 与匹配的 `X-CSRF-Token`；旧 Bearer 请求没有范围 cookie，
保持既有机器契约。首次登录先通过只读 `GET /api/v1/auth/browser-csrf` 获得不可作身份使用的防护值，
因此登录本身也不允许缺失 CSRF 的跨站写入。

相同定向命令转绿，实际 8 项通过；其中验证：

- 会话 cookie 具有根路径、`Secure`、`HttpOnly`、`SameSite=Lax`，CSRF cookie 没有 `HttpOnly`；响应体为空；
- 跨站浏览器登录与持有平台会话但缺少 CSRF 请求头的租户建立均为 `403`；
- 既有租户和平台 Bearer 登录、生命周期 HTTP 回归仍通过。

`HttpHeaders.getValuesAsList` 会拆分 `Set-Cookie` 属性，测试改用原始多值头读取，避免把 `Secure`、
`HttpOnly` 或 `SameSite` 误判为不存在。随后实际执行平台安全、组织访问完整模块、上述 Bootstrap HTTP
测试、`python3 scripts/quality/backend_architecture_guard.py verify` 和 `git diff --check`，均通过。

当前只签发生产属性的 `Secure` cookie；HTTPS 部署和隔离测试 HTTP 的浏览器行为仍须在后续浏览器任务中
分别记录，不能把 TestRestTemplate 手工 Cookie 视为真实浏览器证据。

## 切片四：无 token 能力清单红绿记录

先在真实 PostgreSQL／Redis 的 `AuthenticationApiTest` 增加浏览器能力入口反例。以下命令实际失败：
`GET /api/v1/auth/browser-session` 尚未注册，预期 `200` 实际为 `404`：

```bash
./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am test \
  -Dtest=AuthenticationApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

最小绿灯新增只读 `BrowserSessionCapabilityService` 和 `GET /api/v1/auth/browser-session`。服务仅独立恢复
两个既有范围会话并投影固定的工作区类别、显示角色和最大会话年龄提示；绝不序列化 token、主体、成员、
租户、权限或会话原始事实。无会话或只有失效会话返回 `401`，并只清除失效的对应范围 cookie；另一个有效
范围仍可用，不能被无效 cookie 降级。查询参数不参与能力判定，接口授权仍由每个受限请求的解析器再次恢复
会话并验证。

相同定向命令转绿，实际 11 项通过；其中验证租户工作区、平台治理工作区、伪造查询参数无效、身份与 token
字段不泄露、无效租户 cookie 被定向清除，以及有效平台 cookie 不会被另一失效范围 cookie 影响。随后实际执行：

```bash
./mvnw -q -pl backend/contexts/organization-access -am test
./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am test \
  -Dtest=AuthenticationApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false
python3 scripts/quality/backend_architecture_guard.py verify
git diff --check
```

四项均通过。前端尚未消费该投影，不能把该 HTTP 回归当作跨页签或注销传播的浏览器证据。

## 切片五：浏览器会话族注销红绿记录

`AuthenticationApiTest` 先新增当前范围注销反例；`DELETE /api/v1/auth/browser-session` 缺失时，预期
`204` 实际为 `500`。最小绿灯由 `BrowserSessionTerminationService` 只使用两个既有范围会话端口撤销
当前 cookie 中的对应 token，控制器在 CSRF 过滤器通过后清除两枚范围 cookie 和 CSRF cookie。真实
PostgreSQL／Redis 回归证明注销后原租户 cookie 不能再恢复能力清单；自动配置同样验证该服务随上下文
启用和关闭。完整 `AuthenticationApiTest` 实际 12 项通过。

这只完成服务端注销语义。前端尚未发出统一注销请求或通过 `BroadcastChannel` 通知其他页签，因此不能
把它当作跨页签注销完成证据。

## 切片六：前端会话客户端红绿记录

先在新建的 `@foundation/browser-session` 公共 seam 写入不存在模块的红灯；Vitest 实际报出
`Cannot find module './browser-session-client'`。最小绿灯只实现同源 CSRF bootstrap、空体浏览器登录、
能力恢复、会话族注销以及非安全请求的 `X-CSRF-Token` 注入；它不暴露或缓存 token。`console-web` 和
`query-web` 的业务客户端均依赖该 seam，删除 `Authorization: Bearer` 拼接；请求序号确保旧请求的迟到
`401` 不会清除新会话的本地状态。

实际执行 `npm test && npm run typecheck && npm run lint && npm run build`，11 个测试文件、47 项测试均通过。
这只证明浏览器客户端契约和两个 API 客户端迁移；各页面仍有局部登录视图，部署也仍需收敛为单一公开
origin，故不能将本切片视为“一次登录跨工作区”完成证据。

## 先红后绿的纵向切片

| 顺序 | 实际红灯 | 最小绿灯实现 | 回归范围 |
| --- | --- | --- | --- |
| 1 | 已完成：正确范围 cookie 恢复、另一范围 cookie 拒绝的实际红绿。 | platform-security 仅读取对应范围 cookie，Bearer 继续优先。 | 解析器、会话网关、自动配置与既有 Bearer 回归。 |
| 2 | 已完成：一次浏览器登录的独立会话编排实际红绿。 | organization-access 以已验证 `SubjectIdentity` 和可选租户业务选择建立会话族。 | 认证应用、失败补偿与自动配置。 |
| 3 | 已完成：新入口缺失导致的 HTTP 红灯、cookie 空体与跨站／缺失 CSRF 拒绝绿灯。 | 只用 HttpOnly 范围 cookie 交付 token；同源与 CSRF 过滤器只约束浏览器会话写入。 | 真实 PostgreSQL／Redis、cookie HTTP 契约与机器 Bearer 回归。 |
| 4 | 已完成：浏览器无法安全恢复导航，或伪造查询参数改变服务端授权。 | 只读 `browser-session` 固定能力投影；权限仍由请求解析器再次验证。 | 真实 PostgreSQL／Redis HTTP 的租户／平台、无泄露、定向清除与越权反例。 |
| 5 | 已完成：浏览器注销只清本地状态，或未清除另一范围 cookie。 | `browser-session` 注销通过范围会话端口撤销，并清除整个 cookie 会话族。 | 真实 PostgreSQL／Redis HTTP 的撤销、cookie 清除和自动配置回归。 |
| 6 | 已完成：业务客户端自行保存或拼接 Bearer token，或遗漏浏览器写入 CSRF。 | 两个应用共用 cookie 会话 seam，不保存 token；非安全请求统一读取 CSRF cookie。 | 47 项前端测试、类型检查、lint 与生产构建。 |
| 7 | 待实现：各页面仍渲染局部登录视图，且公开部署尚非同一 origin。 | 统一根登录入口、能力驱动导航和 `/query/` 同源发布。 | 控制台 UI 反例、Nginx／Compose 配置和直达路由任务。 |
| 8 | 待实现：同源新页签无法取得既有会话；注销、过期或 401 后其他页签仍显示受权任务。 | 两个应用以 `BroadcastChannel` 传播失效信号；不保存或广播 token。 | 浏览器两页签、注销、过期和直达无权路由任务。 |
| 9 | 待实现：平台管理员无法完成建立、激活、挂起和恢复租户，或租户管理员／平台运营能看到不属于自己的页面。 | 最小平台租户生命周期工作区调用已存在的受限 HTTP；统一导航按能力清单显示。 | Bootstrap HTTP、真实浏览器和全文本披露检查。 |

每一行都必须先保留实际失败命令及失败原因，再实施相同行为的最小绿灯。不得用 mock 替代
Sa-Token、Redis、PostgreSQL、cookie 属性或浏览器页签事实。

## 角色浏览器验收合同

| 参与者 | 前置条件 | 任务 | 必须观察到的结果 | 禁止结果 |
| --- | --- | --- | --- | --- |
| 租户管理员 | 有效租户会话，无平台治理授权。 | 从统一入口登录，打开数据、模型、查询和治理许可页面，再直达平台治理地址。 | 不重复登录；只见租户导航；平台地址失败关闭。 | 平台导航、平台 cookie、其他租户或正文访问。 |
| 平台管理员 | 有效平台治理授权，且可选地有一个受权租户会话。 | 一次登录后在两个页签建立、激活、挂起、恢复租户。 | 生命周期状态与受限 HTTP 一致；两页签共享失效状态。 | 通过平台页面读取知识、问答或成员详情。 |
| 平台运营 | 没有 `tenant-governance:write`。 | 登录并尝试直达平台治理及租户正文地址。 | 平台治理与正文请求均失败关闭。 | 因角色导航、能力清单或 cookie 获得额外读取权限。 |
| 任意已登录主体 | 两个同源页签都有可见许可任务。 | 在一个页签注销，或使一个会话过期。 | 两个页签立即移除受权导航并返回统一登录入口。 | 页面继续显示旧身份，或把 token 写入浏览器存储。 |

## 验证命令与完成门槛

接受 ADR-0048 后，每个切片至少执行其定向红绿命令；完成前必须执行：

```bash
./mvnw -q -pl backend/platform/platform-security/platform-security-satoken -am test
./mvnw -q -pl backend/contexts/organization-access -am test
./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am test
cd frontend && npm test && npm run typecheck && npm run lint && npm run build
make validate
make project-verify
git diff --check
```

真实浏览器任务还必须在 HTTPS cookie 配置与隔离测试 HTTP 配置中各自记录可复现结果。前三个切片的
红绿结果不能替代能力清单、前端迁移、注销传播和两页签浏览器验收；M6 仍未完成。

## 六维预检

| 维度 | 当前判断 | 依据／后续门槛 |
| --- | --- | --- |
| 正确性 | 已部分验证 | 实际 `404` 红灯后，真实 PostgreSQL／Redis HTTP 验证空体 cookie、CSRF、固定能力投影、范围隔离与定向清除；注销与过期待验证。 |
| 复杂度 | 可控 | 仅增加一个浏览器入口、一个只读能力投影、一个防护过滤器和一个非身份 CSRF bootstrap；不创建通用双范围上下文。 |
| 冗余 | 已识别待清理 | 旧两类 Bearer 登录仍是机器兼容契约；工作区独立登录表单和内存 token 待统一前端状态后移除。 |
| 性能 | 已局部验证 | 登录只进行一次口令验证；能力读取最多恢复两个固定范围会话，不读取成员列表或正文。 |
| 架构 | 已验证 | 两类既有 login type 和限定 cookie 名称仍独立，能力服务只依赖会话端口，架构门禁通过。 |
| 测试质量 | 已部分验证 | 已有实际红绿、真实 PostgreSQL／Redis、自动配置和 HTTP 反例；HTTPS 浏览器、跨页签和注销仍待实际任务。 |
