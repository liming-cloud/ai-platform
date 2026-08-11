# WP-0014 模型部署可用性投影证据

## 范围

本切片落实 ADR-0058 的运行可用性只读投影：模型部署列表现在返回 `HEALTHY`、`DEGRADED` 或 `UNAVAILABLE`，
以及唯一建议下一步。它不新增模型调用、健康写入、厂商连接、Endpoint、凭据、发现历史或发布状态迁移；
`PUBLISHED` 仍是不可变发布事实，健康读取不得将其降级或停用。

## 设计与代码图谱影响

代码图谱首先确认 `ModelDeploymentService.list` 已拥有部署仓储、连接仓储和时钟，但原 `ModelDeploymentView`
未携带可用性。`ModelDeploymentView` 的变化影响部署应用服务、HTTP 控制器、控制台客户端和部署分页工作区；
影响分析后仅修改这些直接调用方。

- 连接缺失、未验证或当前修订漂移：`UNAVAILABLE`／`VERIFY_CONNECTION`。
- 有效连接上的草稿：`UNAVAILABLE`／`PROBE_MODEL`；已验证或已发布但验证已到期：`DEGRADED`／`REPROBE_MODEL`。
- 当前连接、验证和已发布事实均有效：`HEALTHY`／`NONE`；有效但尚未发布的部署唯一建议为 `PUBLISH`。
- 列表仍是服务端分页。当前页最多 100 条，应用层只按当前租户和连接主键读取有界连接事实；投影不含 Endpoint、密钥、凭据引用、原始错误、提示词或回答。

## TDD 红绿记录

先在 `ModelDeploymentServiceTest` 增加列表可用性断言，再执行：

```bash
/tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
  -pl contexts/model-runtime -am \
  -Dtest=ModelDeploymentServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：编译失败，`ModelDeploymentView` 不存在 `availability()`，证明测试先于实现。

随后为唯一建议下一步增加断言并执行同一命令。结果：编译失败，缺少
`ModelDeploymentAvailabilityNextStep` 和 `nextStep()`；再次证明契约先于实现。

实现后，定向应用与 HTTP 回归通过：

```bash
/tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
  -pl contexts/model-runtime -am \
  -Dtest=ModelDeploymentServiceTest,ModelDeploymentControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：9 项测试通过，覆盖 `HEALTHY`、`DEGRADED`、连接修订漂移 `UNAVAILABLE`、建议下一步和 token 派生 HTTP 分页投影。

## 真实基础设施、HTTP 与前端回归

```bash
/tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
  -pl contexts/model-runtime -am \
  -Dtest=PostgresModelDeploymentRepositoryTest,ModelDeploymentServiceTest,ModelDeploymentControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
cd frontend && npm test && npm run typecheck && npm run lint
```

最终命令在提交前重跑。已通过的真实 PostgreSQL 回归使用 Testcontainers：数据库中把当前连接修订推进到 2 后，
同一已发布部署的读取投影从 `HEALTHY` 变为 `UNAVAILABLE`，而 `PUBLISHED` 状态保持不变。HTTP `GET /api/v1/model-deployments`
验证页面、总数与 `availability` 字段，且上下文只由可信参数解析器提供。统一控制台在既有服务器分页表格显示状态、可用性与建议，
无客户端全量加载、无新增主操作或嵌套 Dialog。

正式 HTTPS 浏览器角色任务仍不能由本机未受信任 TLS 代替；不会安装 CA 或忽略证书错误。前端自动测试、类型检查和 lint
属于浏览器代码回归，正式证书环境中的人工角色验收仍是 M6 未完成项。

## 六维检查

| 维度 | 结论与证据 |
| --- | --- |
| 正确性 | 单元测试覆盖三态、建议下一步和连接修订漂移；真实 PostgreSQL 保证读取不改写发布状态。 |
| 安全 | 仅 `model:read` 的可信上下文可读取；投影不携带秘密、Endpoint 或原始诊断。 |
| 架构 | 状态推导留在部署应用读取服务；领域部署、供应商连接和持久化结构均未承担健康写入职责。 |
| 性能 | 复用服务器分页，仅处理当前页并执行有界的租户主键连接读取。 |
| 可用性 | 列表直接给出稳定三态和唯一下一步，前端继续以服务器重读处理刷新。 |
| 复杂度 | 仅新增两个小型 DTO 枚举和局部映射，没有新表、后台任务、缓存或第二套状态机。 |

M6 与 WP-0014 继续进行中；本证据不构成 M6 自动验收、人工评审或 M7 准入。
