# WP-0014 火山方舟权威模型发现与独立 AK/SK 证据

## 范围与产品逻辑

- 平台连接选择 `VOLCENGINE_ARK_CONTROL_PLANE` 后，推理 API Key 与发现 AK/SK 作为三个独立用途密文保存；
  发现适配器不能通过租户通用 `SecretResolver` 读取平台凭据。
- 方舟发现只调用官方 `ListEndpoints` 控制面接口，候选标识取自 `Result.Items[].Id`，不读取名称推断
  `CHAT`／`EMBEDDING`。
- 所有方舟候选先进入 `REQUIRES_ADMIN_CONFIRMATION`；管理员确认后仍须沿用既有真实能力探测、部署和目录发布门禁。
- 页面只说明 API Key 与 AK/SK 的用途分离；写入成功后清空三个输入，不回显凭据、厂商响应、厂商错误码、
  RequestId 或内部凭据引用。

## 权威契约

- 来源：火山引擎官方 Python SDK `volcenginesdkark/api/ark_api.py` 与 SignerV4 实现。
- 请求：`POST /?Action=ListEndpoints&Version=2024-01-01`，JSON 分页字段为 `PageNumber`、`PageSize`。
- 响应：读取 `Result.Items`、`PageNumber`、`PageSize`、`TotalCount`，Endpoint 候选标识只取 `Id`。
- 地址：生产控制面固定解析为 `https://ark.{region}.volcengineapi.com`，并在解析凭据前执行出站策略校验。
- 签名：`HMAC-SHA256`、`X-Date`、`X-Content-Sha256`，scope 为
  `{date}/{region}/ark/request`；固定向量签名为
  `d1d9f633b8a9d5dd732c573cdea1ae1e74497aaa7ac81f03e73acc2d5999b46d`。

## 红灯

1. 真实 PostgreSQL 测试先要求同一连接保存推理 API Key、发现 Access Key ID 与 Secret Access Key 三个用途，
   旧单行主键和旧引用解析按预期失败。
2. 方舟本地 HTTP 测试先引用不存在的 `VolcengineArkPlatformProviderModelDiscoverer` 与签名器，测试编译按预期失败。
3. SPA 静态测试先要求方舟协议条件显示独立 AK/SK 字段，旧表单断言按预期失败。

## 绿灯

- `V202608240004` 将平台连接凭据主键改为连接与用途组合，既有行回填 `INFERENCE_API_KEY`；真实
  PostgreSQL 18.4 从空库执行 20 个迁移并验证三种凭据互不覆盖、密文存储和严格引用命名空间。
- 本地真实 HTTP 服务验证请求方法、Action/Version、分页请求体、签名头、两页聚合、未知能力不猜测以及
  厂商错误、RequestId 和响应内容不泄露。
- 应用与 HTTP 测试验证方舟连接在同一事务写入三个凭据、发现请求只携带受控引用和区域、公开创建响应不含
  API Key／AK／SK。
- 自动配置同时装配 OpenAI-compatible 和火山方舟两个平台发现适配器；运行时推理仍只依赖租户通用
  `SecretResolver`，平台发现只依赖 `PlatformProviderCredentialResolver`。

## 自动验证

- 实现提交：`7c7b70962`（`feat: 实现火山方舟权威模型发现`）。

- 后端整组：
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -f backend/pom.xml -pl contexts/model-runtime,extensions/model-providers/provider-openai-compatible -am test`。
- 真实 PostgreSQL 定向：
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -f backend/pom.xml -pl contexts/model-runtime -am -Dtest=PostgresEncryptedPlatformProviderConnectionCredentialStoreTest -Dsurefire.failIfNoSpecifiedTests=false test`，退出码 `0`。
- 前端：`cd frontend && npm test`，31 个测试文件、142 项测试通过；`npm run lint`、
  `npm run typecheck` 和 `npm run build --workspace @foundation/console-web` 均退出 `0`。
- Compose：使用当前源码重建 `control-plane` 与 `console-web`，两个容器均为 `healthy`；
  `GET http://127.0.0.1:18080/actuator/health` 返回 `UP`，
  `GET http://127.0.0.1:13000/platform/model-supply?page=1` 返回当前 SPA，未认证发现请求稳定返回 `401`。
- 工程助手六维质量快照与固定 diff 双轴评审通过；性能只声明本地两页 workload 与确定资源上界，详见
  `project/evidence/artifacts/WP-0014-ark-discovery-performance-profile.md` 和
  `project/evidence/artifacts/WP-0014-ark-discovery-code-review.md`。

## Chrome 续证状态

受控 Chrome 已打开本地平台管理登录页。为避免 Agent 读取或传输账户密码，已把登录步骤交还人工；登录后的
1024×768、390×844、真实 200% 缩放、键盘导航与 `prefers-reduced-motion` 证据必须在同一页面会话完成后
补入本节。完成前，本证据不声明 Chrome 门禁通过，也不关闭 `WP-0014` 或进入 M7。

## 结论

火山方舟权威模型发现、凭据用途分离、管理员确认边界以及前后端安全投影已经实现并通过自动与真实基础设施
验证。Chrome 登录后全链路仍是本切片唯一未闭环证据；`M6 / WP-0014` 保持进行中。
