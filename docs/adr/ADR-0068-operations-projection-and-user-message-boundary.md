# ADR-0068：运营投影与用户信息边界

## 状态

已接受。

## 日期

2026-08-16

## 决策负责人

用户，代表产品、架构、安全、前端、平台运营与租户运营负责人。

## 背景

现有 `OperationsQueryService` 只提供当前租户的查询用量和脱敏审计，平台范围没有运营总览。前端多个页面又分别把后端 `message` 与 `traceId` 拼入错误提示，并要求用户输入 `SubjectId`、模型代码或其他内部标识。这些行为不能形成可验收的企业产品。

## 决策内容

1. evaluation-operations 拥有平台与租户运营只读模型。各业务上下文通过窄 `OperationsFactSource` 发布脱敏聚合；运营上下文不得跨上下文直接读取表或正文。
2. 统计范围固定为 `DAY_1`、`DAY_7`、`DAY_30`，默认 `DAY_7`，按 UTC 自然日计算。页面进入时加载一次，支持手动刷新并显示 `generatedAt`；超过五分钟标记为可能过期，不轮询、不使用实时推送。
3. 平台总览可展示全平台总量和按租户名称分组的安全摘要；租户总览只读取当前 `TrustedRequestContext` 的租户。任一投影都不得包含问题、回答、引用正文、成员明细、凭据、Endpoint、供应商原始响应或异常。
4. 普通资源明细继续服务端分页。运营总量由后端聚合，前端禁止遍历多个分页接口计算统计。
5. 页面不得要求用户输入 `SubjectId`、`tenantId`、`membershipId`、模型标识、版本号或结果码。关联资源必须使用服务端分页、可搜索的名称选择器；内部标识只作为已选资源的传输事实。
6. `ApiErrorResponse` 继续作为机器契约供客户端分类和服务端追踪，但页面不得直接渲染其 `code`、`message`、`traceId` 或 `details`。前端使用稳定分类映射当前任务的中文提示，未知错误统一提示“服务暂时不可用，请稍后重试”。
7. 成功、冲突、校验、无权限、会话失效、配额、依赖不可用和未知失败必须使用不同且可行动的用户文案。页面不得展示内部修订、结果编码、SQL、堆栈或厂商诊断。
8. `GET /api/v1/usage` 和 `GET /api/v1/audit-records` 继续承担租户查询明细，不因新增总览而退役。确认冗余的接口必须登记调用方与替代契约证据后才删除。

## 公共接口

- `GET /api/v1/platform/operations/overview?range=DAY_7`
- `GET /api/v1/platform/operations/tenants?range=DAY_7&pageNumber=1&pageSize=20`
- `GET /api/v1/operations/overview?range=DAY_7`
- `GET /api/v1/platform/accounts/candidates?purpose=TENANT_INITIAL_ADMINISTRATOR&query=&pageNumber=1&pageSize=20`

## 后果与验证

- 聚合定义、零分母、UTC 时间范围、分页排序、跨租户隔离和平台脱敏必须有应用及真实 PostgreSQL 回归。
- 浏览器必须覆盖加载、空、部分失败、无权限、过期和刷新状态，并断言渲染文本不含错误码、追踪标识、内部标识或厂商原始信息。
- 平台初始管理员候选只返回活动、非平台管理员的人员账户安全摘要；页面显示登录名，不显示主体标识。
- 本决策不引入生产监控基础设施、TLS、Certbot、M7 或 M8。
