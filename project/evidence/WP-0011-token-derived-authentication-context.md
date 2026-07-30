# WP-0011 令牌派生登录态边界证据

## 范围

本记录绑定提交 `fc13521f7` 与 `15e0ce021`，将 ADR-0033 纳入 WP-0011，并为令牌恢复边界补齐解析器与真实 HTTP 回归。它不实现 ADR-0032 的结构化数据集同步、质量、发布或索引，也不表示 M3 完成或可以进入 M4。

## 术语与不变量

- `TrustedRequestContext`：经令牌会话恢复的可信登录状态，包含租户、主体、成员、凭据类型、访问渠道、权限版本和权限集合；这些事实不属于调用方业务输入。
- 业务参数：资源标识、乐观版本、筛选条件和业务内容。它们必须在可信上下文决定的租户、主体和权限范围内加载，不能替代登录状态。
- 认证失败：令牌缺失、无效、过期或会话状态不完整时失败关闭；请求中的身份、租户、权限或自定义 Header 不能作为回退来源。

## 代码图谱事实与影响

- 已探索 `SaTokenSessionGateway.resolve`、`SaTokenTrustedContextArgumentResolver.resolveArgument`、`SaTokenWebAutoConfiguration` 与 `AuthenticatedSessionGateway`。会话网关从已验证令牌对应的服务端会话恢复登录状态；MVC 参数解析器只读取 `Authorization: Bearer`，并在服务端生成请求追踪标识。
- 已对 `TrustedRequestContext` 执行影响分析。现有直接影响收敛在安全核心上下文类型；公共契约新增或变更时，主要回归面是 Sa-Token MVC 参数解析、控制器 HTTP 契约及接收可信上下文的应用用例。
- 影响分析将实现范围收敛到安全平台解析器与一个受治理数据真实 HTTP 入口：未发现当前控制器、请求对象或应用命令把登录状态声明为业务参数，因此不增加第二套身份传递、全局扫描或兼容层。
- `SaTokenTrustedContextArgumentResolverTest` 证明解析器只使用 `Authorization: Bearer` 恢复令牌会话事实，并忽略伪造的租户、主体、成员、权限、渠道查询参数和 Header；缺少 Bearer 令牌时不会回退到伪造 Header。
- `KnowledgeSpaceApiTest` 使用真实 PostgreSQL、Redis、Spring MVC 与 Sa-Token 运行时，证明携带伪造登录状态查询参数和 Header 的创建请求仍只在有效令牌所属租户中可见，另一租户不可见。

## 可复核验证

- `git diff --check`：通过。
- `make project-verify`：通过，`currentMilestone` 为 M3、`currentFrontier` 为 WP-0011、待决策仍为 ADR-0032。
- `./mvnw -q -pl backend/platform/platform-security/platform-security-satoken,backend/bootstrap/control-plane-bootstrap -am -Dtest=SaTokenTrustedContextArgumentResolverTest,KnowledgeSpaceApiTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过；真实 Testcontainers PostgreSQL 与 Redis 回归通过，日志仅含既有 Spring、MyBatis、Netty DNS 和 Testcontainers 提示。

## 结论

ADR-0033 已接受并以解析器、真实 HTTP 与租户隔离回归落实：登录状态从令牌恢复，公共接口只承载业务输入。ADR-0032 与 ADR-0034 均已接受；M3 仍进行中，进入 M4 仍须先完成 M3 并获得人工评审通过。
