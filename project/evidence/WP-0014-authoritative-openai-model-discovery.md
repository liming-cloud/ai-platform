# WP-0014 OpenAI-compatible 权威模型发现与管理员确认证据

## 范围

- 平台连接显式冻结 `PlatformModelDiscoveryProfile`，不再由 Endpoint 或模型名称推断发现协议。
- OpenAI-compatible 使用权威 `GET /v1/models` 获取候选，只读取 `data[].id`。
- 元数据不足的候选保持类型为空、能力为空，并进入管理员确认。
- 管理员确认作为独立不可变事实记录确认主体与时间；确认前不得建立部署，确认后仍须真实能力探测。
- HTTP 与浏览器投影不包含厂商原始响应、内部标识、Endpoint 或凭据引用。

## 红灯

1. OpenAI-compatible 适配器测试先引用尚不存在的发现协议、候选分类与适配器，`test-compile` 失败于缺失符号。
2. 平台连接 HTTP 测试先断言 `discoveryProfile`，`test-compile` 失败于命令没有该字段。
3. 真实 PostgreSQL 测试先调用管理员确认端口，`test-compile` 失败于端口不存在。
4. 候选确认 HTTP 与 SPA 测试先引用尚不存在的确认命令、服务和交互，分别在 Java 编译与 Vitest 断言处失败。

## 绿灯

- OpenAI-compatible 本地真实 HTTP 契约：Bearer API Key、`/v1/models`、未知名称不猜能力、SSRF 前置拒绝、鉴权原文不泄露，测试通过。
- 真实 PostgreSQL 18.4：Flyway 迁移至 `V202608240003`；待确认候选可分页但不能部署，管理员确认审计及确认后部署查询通过。
- HTTP：连接显式发现协议、候选确认安全响应及平台权限边界测试通过。
- 后端整组回归：
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -f backend/pom.xml -pl contexts/model-runtime,extensions/model-providers/provider-deterministic,extensions/model-providers/provider-openai-compatible -am test`
  退出码为 `0`。
- 前端全量测试：`cd frontend && npm test`，31 个测试文件、141 项测试全部通过。
- 前端生产构建：`cd frontend && npm run build --workspace @foundation/console-web`，退出码为 `0`。
- TypeScript：`cd frontend && npm run typecheck`，退出码为 `0`。
- 工作区格式检查：`git diff --check`，退出码为 `0`。

## 安全与未闭环项

- 适配器只投影模型标识和平台稳定结果码，未持久化或返回厂商响应体。
- 本证据不覆盖火山方舟 AK/SK 签名控制面适配、Compose 同源 HTTP 或 Chrome 响应式续证；这些仍属于 WP-0014 下一切片。
