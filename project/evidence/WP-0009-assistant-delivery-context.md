# WP-0009 助手交付上下文迁移证据

## 结论

助手交付上下文已完成经典四层迁移，旧 `api`、`adapter` 目录中的源文件全部删除。
助手身份、不可变版本、发布目录和查询目录形成清晰能力边界；控制面管理服务与查询面
只读门面分别装配，查询进程不再被迫依赖管理能力。

实施提交：

`6965430afc0abe42731e774fc1b63a875d6b23c1`

## 结构与领域证据

- `Assistant` 实现共享聚合根标记，`AssistantVersion` 实现共享实体标记。
- 助手、版本、创建者标识以及策略和状态类型实现共享值对象标记。
- 领域层只依赖 JDK 与 `shared-kernel`，创建者标识不再依赖安全平台技术类型。
- 仓储契约归属助手聚合，跨上下文只公开已发布版本查询门面。
- 错误统一登记到 `AssistantDeliveryErrorCode`，领域和应用异常使用统一异常基类。
- 主要目录均提供简体中文包职责、依赖和公开边界说明。

## 分页与持久化证据

- 助手治理列表和最终用户助手目录统一使用 `PageQuery`、`PageResult`。
- 助手单表列表使用 `BaseMapper.selectPage` 与 `LambdaQueryWrapper`。
- 已发布助手目录使用 `IPage` 作为 XML 方法首参数，由分页插件生成分页与总数查询。
- 删除两个列表的游标编码、`limit + 1`、手工截断和手工 `LIMIT`。
- 助手和助手版本分别使用带表注解的持久化实体及 `BaseMapper`。
- 版本查询、计数、插入、聚合推进和发布更新使用 Lambda 条件构造器。
- XML 只保留事务锁、幂等事实、加锁读取和跨表最新发布版本查询。

## 自动装配证据

- 按持久化、核心、控制面管理和 Web 四类能力提供自动配置。
- 自动配置通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  注册，不使用宽泛组件扫描。
- 管理服务只在控制面角色和助手能力同时开启时注册；查询进程只装配已发布版本门面。
- bootstrap 中原有助手仓储、应用服务和控制器集中 Bean 声明已删除。
- `ApplicationContextRunner` 验证能力开启、关闭和用户实现覆盖。

## 影响分析与回归

修改前通过代码图检查 `PublishedAssistantVersionLookup` 的 77 个受影响符号，据此同步
修正可信查询、两个 bootstrap、两个运行模块和查询前端，避免跨上下文遗留旧包依赖。

验证结果：

| 验证 | 结果 |
| --- | --- |
| `./mvnw -q -pl backend/contexts/assistant-delivery test` | 通过，含领域、应用、架构、自动配置和真实 PostgreSQL 测试 |
| `AssistantApiTest` | 通过，控制面创建、分页、权限和统一错误响应闭环正常 |
| `QueryAssistantControllerTest` | 通过，查询目录页码与总数契约正常 |
| `./mvnw -q -pl backend/contexts/trusted-query test` | 通过，跨上下文发布版本门面回归正常 |
| 前端类型检查与测试 | 通过，7 个测试文件共 28 项测试 |
| `make architecture-guard` | 通过，助手上下文未新增旧目录、游标和手工分页债务 |
| `make project-verify` | 通过 |
| `git diff --check` | 通过 |

全反应堆回归还额外发现并修正了可信查询数据库测试缺少雪花工作节点配置，以及最小
控制面测试未关闭助手能力的问题。修正后的定向测试均已通过；全反应堆最终结果将在
后续上下文迁移全部完成后统一登记。

## 尚未关闭的范围

WP-0009 仍处于进行中。可信查询、评测运维上下文、启动模块合并、Docker 构建目标、
完整容器演练和六维代码走查尚未完成，因此本证据不批准 M1。
