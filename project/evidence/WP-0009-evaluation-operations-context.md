# WP-0009 评测运维上下文迁移证据

## 结论

评测运维上下文已完成经典四层迁移，六个限界上下文的旧 `api`、`adapter` 主代码文件
全部归零。运营查询不再保留一对一用例接口，权限判定从领域层迁回应用层，领域层只保留
报表周期和配额快照值对象。

实施提交：

`49868ba7199d0cb1934f3147a1b594c3407d851a`

## 结构与领域证据

- `ReportingPeriod` 和 `OperationsQuotaPolicy` 实现共享值对象标记。
- 领域层只依赖 JDK 与 `shared-kernel`，不再依赖安全平台的可信请求上下文和权限类型。
- Controller 直接依赖具体 `OperationsQueryService`，删除一对一
  `OperationsQueryUseCase`。
- 用量与审计查询、响应、权限策略和服务按运营能力归组，不再平铺在模块根包。
- 主要目录提供简体中文职责、允许依赖、禁止依赖、公开边界和安全边界说明。
- ArchUnit 同时约束应用层技术框架依赖和领域层跨上下文依赖。

## 异常与自动装配证据

- 运营权限异常统一为 `ApplicationException`，稳定编码为
  `EVALUATION_OPERATIONS.AUTHORIZATION.0001`。
- 控制面删除大型 `GlobalApiExceptionHandler` 与重复错误响应，认证、权限、请求和业务异常
  全部由 `platform-web` 统一安全映射。
- 平台 Web 补齐缺少请求参数和参数类型错误的统一请求错误映射。
- 控制台、查询站点和后端接口测试同步使用正式错误码，不保留旧短码兼容分支。
- 核心服务和 Web 入口通过两个自动配置及 `META-INF` 注册，只在控制面角色开启。
- 配额报表以类型安全属性读取可信查询执行侧的同一配置命名空间。
- `ApplicationContextRunner` 覆盖存在、关闭、用户实现覆盖和必需事实源缺失场景。
- control-plane bootstrap 中原有运营 Bean、Controller 和异常处理器集中声明全部删除。

## 工程门禁增强

- 架构门禁增加错误码格式和全仓唯一性硬校验。
- 新增两个红绿测试，分别证明非法类别或非四位序号、重复错误码均会阻止合并。
- 当前主代码旧 `api` 文件、旧 `adapter` 文件、SQL 注解、UUID 和 Sa-Token 越界均为零。

## 影响分析与回归

修改前通过代码图检查 `OperationsQueryUseCase`、两个 Controller、应用服务、权限策略、
可信查询运营事实门面和 bootstrap 的调用关系，确认八个直接调用方后同步更新。

验证结果：

| 验证 | 结果 |
| --- | --- |
| `./mvnw -q -pl backend/contexts/evaluation-operations -am test` | 通过，含领域、应用、架构和自动配置测试 |
| 评测运维结构、自动装配、接口与控制面启动定向验收 | 通过 |
| `./mvnw -q -pl backend/runtimes/control-plane -am test` | 通过，控制面及全部依赖回归正常 |
| 前端 `typecheck`、`lint`、全量测试和构建 | 通过，7 个测试文件共 28 项测试 |
| `make architecture-guard` | 通过，14 项门禁测试；六上下文旧目录文件归零 |
| `make project-verify` | 通过 |
| `git diff --check` | 通过 |

## 尚未关闭的范围

WP-0009 仍处于进行中。全仓简单 SQL 的 Wrapper 收口与复杂 SQL 允许清单、扩展模块
装配复核、三个 bootstrap 可执行化、`backend/runtimes` 删除、Docker 构建更新、完整
容器演练和六维代码走查尚未完成，因此本证据不批准 M1。
