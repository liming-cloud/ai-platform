# WP-0014 模型部署不可逆停用证据

## 范围

本切片补齐 ADR-0058 已定义但尚未落地的部署生命周期终态 `DISABLED`。具有 `model:write`
权限的可信请求可停用草稿、已验证或已发布部署；停用不删除验证事实、阻断新运行时供应，且同一部署
不能重新发布。它不改变厂商连接、凭据、模型发现、历史验证事实或已发布部署以外的模型配置。

## 设计与代码图谱影响

代码图谱确认 `ModelDeployment` 原先仅有 `DRAFT`、`VERIFIED`、`PUBLISHED` 三态，
`ModelDeploymentService` 只有探测和发布写操作；`PublishedEmbeddingDeploymentLookupService` 已严格按
`PUBLISHED` 筛选，因此停用后无需新增运行时分支即可失败关闭。影响范围限定为部署聚合、应用服务、
HTTP 控制器、PostgreSQL 约束、统一控制台和它们的直接测试。

- `disable()` 保留已有验证标识与有效期，推进版本；重复停用在当前版本上幂等返回，旧版本仍由乐观锁拒绝。
- `DISABLED` 的可用性固定投影为 `UNAVAILABLE`／`NONE`，不再建议重新探测或发布，也不因连接漂移改写。
- 数据库允许停用草稿不携带验证事实，也允许停用已验证或已发布部署保留完整验证事实；禁止一半为空的事实组合。
- `POST /api/v1/model-deployments/{modelDeploymentId}/disable` 只从 `TrustedRequestContext` 取得身份和租户；控制台写后重新读取服务器分页结果。

## TDD 红绿记录

先在 `ModelDeploymentVerificationTest` 与 `ModelDeploymentServiceTest` 写入停用、版本和不可重新发布断言，执行：

```bash
/tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
  -pl contexts/model-runtime -am \
  -Dtest=ModelDeploymentVerificationTest,ModelDeploymentServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：编译失败，缺少 `ModelDeployment.disable()`、`ModelDeploymentStatus.DISABLED` 与
`DisableModelDeploymentCommand`，证明测试先于实现。

实现后定向领域、应用与 HTTP 回归通过：

```bash
/tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
  -pl contexts/model-runtime -am \
  -Dtest=ModelDeploymentVerificationTest,ModelDeploymentServiceTest,ModelDeploymentControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：15 项测试通过，覆盖验证事实保留、状态不可逆、`model:write`、乐观版本、HTTP 可信上下文和
`UNAVAILABLE`／`NONE` 投影。

## 真实基础设施、HTTP、前端与浏览器

```bash
/tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
  -pl contexts/model-runtime -am \
  -Dtest=PostgresModelDeploymentRepositoryTest,PublishedEmbeddingDeploymentLookupServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
cd frontend && npm test && npm run typecheck && npm run lint
```

结果：真实 PostgreSQL Testcontainers 回归 9 项通过。Flyway 从空库应用 10 条迁移，验证停用状态可持久化、
保留验证事实并以版本拒绝旧写入；运行时嵌入部署查找对 `DISABLED` 返回空。MockMvc HTTP 回归验证停用端点
忽略伪造租户参数，只传入可信上下文。前端 16 个测试文件共 87 项通过，并完成类型检查和 lint；控制台新增
单一高风险确认 Dialog，停用后写后重读，未新增累加分页或嵌套 Dialog。

本机没有运行中的统一控制台／API 受信任 TLS 验收环境，浏览器技能检查后未发现可访问的本地前端或后端开发端口。
依据 ADR-0048 安全边界，未安装 CA、未忽略证书错误，也没有把 HTTP 或静态页面代替正式浏览器角色任务。该
正式 HTTPS 浏览器验收保持 M6 未完成项；上述 Vitest 是浏览器代码回归，不能替代它。

额外执行全后端 `mvn test` 时，`model-runtime` 模块的 139 个测试通过；反应堆随后在未改动的
`control-plane-bootstrap` 测试启动阶段失败。根因是 Spring CGLIB 无法代理 `final` 的
`TenantCustomRoleDirectoryApplicationService`，导致 43 个测试中 42 个报错。该类和失败测试均不属于本切片
修改范围，故仅登记为 M6 自动验收阻塞项，不在本切片内修复，也不进入 M7。

## 六维检查

| 维度 | 结论与证据 |
| --- | --- |
| 正确性 | 领域、应用、HTTP 与真实 PostgreSQL 共同覆盖停用、验证事实和乐观版本。 |
| 安全 | 写操作仅接受可信上下文的 `model:write`；运行时只接受 `PUBLISHED`，停用即失败关闭。 |
| 架构 | 生命周期留在部署聚合，供应链读取沿既有跨上下文门面，没有新增跨层依赖。 |
| 性能 | 停用是一条按租户、标识和版本约束的更新；控制台继续只读取当前服务器页。 |
| 可用性 | 终态稳定显示不可用且无误导性下一步，用户可新建并重新验证部署恢复供应。 |
| 复杂度 | 仅增加一个状态、一个命令、一个端点、一个迁移与局部 Dialog，没有后台任务或第二套状态机。 |

M6 与 WP-0014 继续进行中；本证据不构成 M6 自动验收、人工评审或 M7 准入。
