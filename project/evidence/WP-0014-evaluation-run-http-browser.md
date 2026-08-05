# WP-0014：受限评测运行 HTTP 与控制台入口证据

## 范围

本证据只覆盖 ADR-0050、ADR-0051、ADR-0052、ADR-0055 与 ADR-0056 已接受的无正文运行入口：
令牌派生租户上下文、数据集与助手版本业务目标、终态指标投影及同源控制台任务。它不提供私有数据集、
输入、模型输出、证据正文、对象位置、路由摘要或发布决定。

## 红绿路线

1. `EvaluationRunControllerTest` 先引用不存在的 HTTP 控制器、请求和投影；编译失败，证明入口尚未存在。
2. 补齐 `EvaluationRunController` 后，直接调用测试证明应用层只收到 `datasetId`、`assistantVersionId` 与
   由解析器提供的 `TrustedRequestContext`；MockMvc 回归证明 `POST /api/v1/evaluation-runs` 只绑定两个业务目标，
   返回 `201` 和终态指标，且不含私有内容位置、输入、回答或路由摘要。
3. 控制台客户端测试先引用不存在的 `executeEvaluationRun`；实现后证明同源浏览器会话发送的 JSON 只有
   两个标识且不带 Bearer，响应类型不含任何私有字段。生产构建证明治理工作区可在统一登录会话内切换到
   “受限评测运行”任务。

## 实现与边界

- `EvaluationRunExecuteRequest` 只接受正数 `datasetId` 与 `assistantVersionId`。身份、租户和权限绝不绑定为
  HTTP 参数，继续由 `TrustedRequestContext` 和应用服务恢复。
- `EvaluationRunView` 只投影冻结版本、计数、三项指标、可计算标记、质量门结论和时间。指标不可计算时数值为
  `0`，由 `metricsComputable = false` 明确标记；不会以 `0` 误称为通过。
- `ControlPlaneClient` 复用 `browser-session` 的 HttpOnly 同源会话和 CSRF 机制，控制台没有第二个登录入口，
  也不保存或发送 Bearer token。角色与权限的最终判定仍在服务端 `assistant:write`。
- 本地正式 HTTPS Cookie／跨页签浏览器证据继续按 ADR-0048 延后，不安装 System keychain CA，且不以 HTTP
  或忽略证书错误替代该证据。

## 可复现验证

```bash
./mvnw -q -pl backend/contexts/evaluation-operations -am test
cd frontend && npm run typecheck && npm run lint && npm run test -- --run apps/console-web/src/api/control-plane-client.test.ts
cd frontend && npm run build
git diff --check
```

以上命令通过。Maven 日志有既有 Spring 上下文预期失败分支、MyBatis Mapper 扫描与 Testcontainers 警告；
没有测试失败。前端定向客户端回归 19 项通过，两个前端生产构建均通过。

## 后续

下一切片只可按 ADR-0050 实现发布决定与 `assistant:write` 授权的闭环；不得开放正文读取、评测运行列表、
预算例外、导出或 M7／M8 能力。
