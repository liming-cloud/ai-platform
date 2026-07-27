# WP-0009 可信查询上下文迁移证据

## 结论

可信查询上下文已完成经典四层迁移，旧 `api`、`adapter` 目录中的源文件全部删除。
候选检索、查询运行、事件流和运营事实形成独立能力边界；事件序号、向量候选数量和
内部租约仍保持原有专用语义，没有被错误改造成页码分页。

实施提交：

`3d3090cdf1f04e0b17e18514f97baa0c0d4aba53`

## 结构与领域证据

- `QueryRun` 实现共享聚合根标记，查询标识、请求者、引用、模型用量、事件和配额快照
  实现共享值对象标记。
- 查询领域层不再依赖模型运行时用量类型或安全平台成员类型，只依赖 JDK 与
  `shared-kernel`。
- 查询仓储归属查询聚合；面向评测运维上下文只公开脱敏运营事实门面。
- Controller 依赖具体应用服务，删除三个一对一用例接口。
- 主要目录均提供简体中文包职责、允许依赖、禁止依赖、公开边界和事务或安全边界说明。
- ArchUnit 同时约束应用层技术框架依赖和领域层跨上下文依赖。

## 分页与持久化证据

- 查询审计接口统一使用 `PageQuery`、`PageResult` 以及
  `pageNumber`、`pageSize`。
- 审计 Mapper 以 `IPage<?>` 作为首参数，由 MyBatis-Plus 分页拦截器生成分页和总数
  查询，XML 不再编写审计列表 `LIMIT`。
- 删除查询审计游标、游标编码、`limit + 1`、手工截断和 `nextCursor`。
- PostgreSQL 集成测试覆盖首页、中间页、末页、越界空页、总数、总页数、前后页状态、
  固定倒序和跨租户空结果。
- 查询事件流仍按单调事件序号和 `Last-Event-ID` 恢复，事件批量限制不属于列表分页。
- 控制台审计客户端同步改用页码协议，加载更多按下一页页码请求。

## 异常与自动装配证据

- 可信查询错误统一登记到 `TrustedQueryErrorCode`，应用异常使用统一异常基类。
- 查询配额只返回登记过的 `limitType`、`limit`、`retryAt`，错误编码为
  `TRUSTED_QUERY.QUOTA.0001`。
- 失效会话纳入 `PLATFORM_SECURITY.AUTHENTICATION.0001`，删除查询启动包中的大型专用
  异常处理器，统一由 `platform-web` 安全映射。
- 按持久化、核心和 Web 三类能力提供自动配置，并通过
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  注册。
- `ApplicationContextRunner` 验证能力开启、关闭、用户实现覆盖、Web 入口条件和
  MyBatis 会话工厂缺失场景。
- 查询 bootstrap 中集中声明的仓储、应用服务、控制器和异常处理器均已删除。

## 影响分析与回归

修改前通过代码图检查 `QueryOperationsFactSource` 的跨上下文影响，并对
`InvalidAuthenticatedSessionException` 执行三层影响分析，覆盖安全核心、Sa-Token、
两个应用入口和相关测试后再修改统一异常继承关系。

验证结果：

| 验证 | 结果 |
| --- | --- |
| `./mvnw -q -pl backend/contexts/trusted-query -am test` | 通过，含领域、应用、架构、自动配置和真实 PostgreSQL 测试 |
| 可信查询结构、分页、异常、接口与启动定向验收 | 通过 |
| 查询统一异常、自动装配和最小启动测试 | 通过 |
| 控制面 `OperationsApiTest` | 通过，页码参数与统一响应契约正确 |
| 前端 `typecheck`、`lint`、全量测试和构建 | 通过，7 个测试文件共 28 项测试 |
| `make architecture-guard` | 通过，游标列表契约归零且未增加手工分页债务 |
| `make project-verify` | 通过 |
| `git diff --check` | 通过 |

## 尚未关闭的范围

WP-0009 仍处于进行中。评测运维上下文迁移、简单 SQL 的全仓 Wrapper 收口、
启动模块与运行模块合并、Docker 构建目标、完整容器演练和六维代码走查尚未完成，
因此本证据不批准 M1。
