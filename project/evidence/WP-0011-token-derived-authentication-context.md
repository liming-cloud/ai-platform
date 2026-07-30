# WP-0011 令牌派生登录态边界证据

## 范围

本记录绑定提交 `fc13521f7`，将 ADR-0033 纳入 WP-0011。它只固定受认证接口的登录状态来源，不实现 ADR-0032 的结构化数据集同步、质量、发布或索引，也不表示 M3 完成或可以进入 M4。

## 术语与不变量

- `TrustedRequestContext`：经令牌会话恢复的可信登录状态，包含租户、主体、成员、凭据类型、访问渠道、权限版本和权限集合；这些事实不属于调用方业务输入。
- 业务参数：资源标识、乐观版本、筛选条件和业务内容。它们必须在可信上下文决定的租户、主体和权限范围内加载，不能替代登录状态。
- 认证失败：令牌缺失、无效、过期或会话状态不完整时失败关闭；请求中的身份、租户、权限或自定义 Header 不能作为回退来源。

## 代码图谱事实与影响

- 已探索 `SaTokenSessionGateway.resolve`、`SaTokenTrustedContextArgumentResolver.resolveArgument`、`SaTokenWebAutoConfiguration` 与 `AuthenticatedSessionGateway`。会话网关从已验证令牌对应的服务端会话恢复登录状态；MVC 参数解析器只读取 `Authorization: Bearer`，并在服务端生成请求追踪标识。
- 已对 `TrustedRequestContext` 执行影响分析。现有直接影响收敛在安全核心上下文类型；公共契约新增或变更时，主要回归面是 Sa-Token MVC 参数解析、控制器 HTTP 契约及接收可信上下文的应用用例。
- 因本提交只固化已存在的安全边界，没有修改生产代码或公共接口。ADR-0033 要求后续受认证接口变更补充“伪造登录状态字段不参与授权或归属”的 HTTP 反例，不能以兼容参数或 allowlist 例外保留。

## 可复核验证

- `git diff --check`：通过。
- `make project-verify`：通过，`currentMilestone` 为 M3、`currentFrontier` 为 WP-0011、待决策仍为 ADR-0032。

## 结论

ADR-0033 已接受并登记为全平台安全边界：登录状态从令牌恢复，公共接口只承载业务输入。M3 仍进行中，ADR-0032 尚未接受，进入 M4 仍须先完成 M3 并获得人工评审通过。
