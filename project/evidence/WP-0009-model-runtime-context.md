# WP-0009 模型运行时上下文迁移证据

## 结论

模型运行时上下文已完成经典四层迁移，旧 `api`、`adapter` 目录已删除。供应商连接、
模型部署、连接探测和模型运行时解析分别形成清晰能力边界，跨上下文只公开门面与扩展
端口，Spring AI 及厂商类型不进入领域层和公共接口。

实施提交：

`98e97ab0d049ae10fc261232066ab0a7ab74772e`

## 结构与领域证据

- `ProviderConnection`、`ModelDeployment` 实现共享聚合根标记。
- 标识、状态、模型类型、供应商类型和凭据引用实现共享值对象标记。
- 领域层只依赖 JDK 与 `shared-kernel`，凭据引用已从安全平台技术类型中解耦。
- 错误统一登记到 `ModelRuntimeErrorCode`，领域、应用和基础设施异常使用统一异常基类。
- 供应商、部署、探测、运行时分别建立能力目录，主要目录均提供简体中文包职责说明。
- Spring Modulith 命名接口只公开部署门面、供应商门面、探测端口、运行时门面和模型工厂端口。

## 分页与持久化证据

- 供应商连接和模型部署列表统一接收 `PageQuery` 并返回 `PageResult`。
- 供应商连接使用 `BaseMapper.selectPage` 与 `LambdaQueryWrapper`。
- 模型部署因 PostgreSQL 数组投影保留 XML，并以 `IPage` 作为首参数交由分页插件生成分页和总数查询。
- `Page`、`IPage` 和 Wrapper 仅存在于基础设施层。
- 删除模型运行时上下文中的 `cursor`、`nextCursor`、手工 `LIMIT`、`limit + 1` 与列表截断。
- 单表查询、计数、插入和普通更新已迁移到 MyBatis-Plus；XML 只保留锁、幂等、数组和专属投影操作。
- 持久化对象统一使用 `@TableName`、`@TableId(type = INPUT)`、`@TableField` 与 `@Version`。

## 自动装配证据

- 上下文按持久化、核心、执行和 Web 四种能力提供自动配置。
- 自动配置通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册。
- bootstrap 中原模型目录和模型运行时集中 Bean 声明已删除。
- 确定性、Ollama、OpenAI 兼容扩展分别提供独立自动配置和上下文测试。
- 必需依赖、条件关闭、用户覆盖及扩展工厂装配均由 `ApplicationContextRunner` 验证。

## 影响分析与回归

代码图在修改前检查了模型运行时结构与关键公开边界：

- `ModelRuntimeResolver` 影响 141 个符号。
- `PublishedEmbeddingDeploymentLookup` 影响 52 个符号。

据此同步修正知识数据、助手交付、可信查询、三个 bootstrap、三个运行模块和四个模型扩展，
避免仅移动本模块而留下跨上下文旧包依赖。

验证结果：

| 验证 | 结果 |
| --- | --- |
| `./mvnw -pl backend/contexts/model-runtime -am test` | 通过，模型运行时及依赖模块 50 项测试通过 |
| 供应商连接与模型部署接口定向测试 | 通过，页码、总数、权限和安全错误响应符合契约 |
| 模型扩展自动配置与架构测试 | 通过 |
| `./mvnw -q test` | 通过，全反应堆 335 项测试，0 失败、0 错误 |
| 前端类型检查 | 通过 |
| 前端测试 | 通过，7 个测试文件共 28 项测试 |
| `make project-verify` | 通过 |
| `make architecture-guard` | 通过，模型运行时未新增旧目录、游标和手工分页债务 |
| `git diff --check` | 通过 |

## 尚未关闭的范围

WP-0009 仍处于进行中。助手交付、可信查询和评测运维上下文尚未完成迁移；运行模块、
Docker 构建目标、最终容器演练和六维代码走查也尚未收口，因此本证据不批准 M1。
