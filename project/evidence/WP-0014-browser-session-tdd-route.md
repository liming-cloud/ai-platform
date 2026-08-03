# WP-0014：统一浏览器会话与平台治理控制台 TDD 路线

## 状态与边界

| 项目 | 内容 |
| --- | --- |
| 状态 | ADR-0048 提议中；本文件只固定后续红绿路线，尚未创建或运行测试。 |
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

## 先红后绿的纵向切片

| 顺序 | 实际红灯 | 最小绿灯实现 | 回归范围 |
| --- | --- | --- | --- |
| 1 | 租户 cookie 不能解析 `PlatformRequestContext`，平台 cookie 不能解析 `TrustedRequestContext`；缺失、篡改、跨站写入和错误 CSRF 全部为 401／403。 | 在 platform-security 增加浏览器 cookie 提取与 CSRF 防护；仍委托既有两类 Sa-Token login type。 | 解析器、会话网关、自动配置与既有 Bearer 回归。 |
| 2 | 一次浏览器登录无法只在服务端生成独立的租户／平台会话；登录响应不应含 token。 | organization-access 以已验证 `SubjectIdentity` 和可选租户业务选择建立会话族，并以两个 HttpOnly cookie 写回。 | 认证应用、真实 PostgreSQL／Redis、cookie HTTP 契约与失败关闭。 |
| 3 | 客户端不能通过安全能力清单恢复导航；伪造清单也不能改变服务端授权。 | 只读 `browser-session` 能力投影和统一会话前端状态；权限仍由请求解析器再次验证。 | HTTP、控制台单元测试、类型检查和租户／平台越权反例。 |
| 4 | 同源新页签无法取得既有会话；注销、过期或 401 后其他页签仍显示受权任务。 | 两个应用只消费 cookie 会话和 `BroadcastChannel` 的失效信号；不保存或广播 token。 | 浏览器两页签、注销、过期和直达无权路由任务。 |
| 5 | 平台管理员无法完成建立、激活、挂起和恢复租户，或租户管理员／平台运营能看到不属于自己的页面。 | 最小平台租户生命周期工作区调用已存在的受限 HTTP；统一导航按能力清单显示。 | Bootstrap HTTP、真实浏览器和全文本披露检查。 |

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

真实浏览器任务还必须在 HTTPS cookie 配置与隔离测试 HTTP 配置中各自记录可复现结果。当前没有
任何红灯或绿灯结果；接受 ADR-0048 前禁止将本路线视为实现或自动验收证据。

## 六维预检

| 维度 | 当前判断 | 依据／后续门槛 |
| --- | --- | --- |
| 正确性 | 待实现 | 上述 cookie 范围隔离、401／403、注销和过期反例。 |
| 复杂度 | 可控 | 只增加会话族、能力投影和共享前端状态；不创建通用双范围上下文。 |
| 冗余 | 待清理 | 绿灯后移除各工作区独立登录表单，保留机器 Bearer 契约直到专门迁移。 |
| 性能 | 待测量 | 能力投影只能读取最小会话事实；不得在导航时加载正文或全量成员。 |
| 架构 | 已固定待验证 | 以两类既有 login type 保持 ADR-0041／0043 边界，需经架构门禁回归。 |
| 测试质量 | 待实现 | 每条反例必须先出现实际红灯，真实 Redis／PostgreSQL／浏览器不能被 mock 替代。 |
