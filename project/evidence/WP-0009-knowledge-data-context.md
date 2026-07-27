# WP-0009 知识数据上下文迁移证据

## 结论

知识数据上下文已按 ADR-0022 完成经典四层迁移，并同步完成三个列表接口的
MyBatis-Plus 页码分页、领域对象类型声明、统一异常编码、上下文自动配置以及
MinIO、pgvector 扩展自动配置。实现提交为
`25e6070a38db6bf6978850570969418259058cec`。

## 结构证据

- 删除知识数据上下文中的 `api` 和 `adapter` 源码。
- 入口统一迁入 `interfaces/web` 和 `interfaces/job`。
- 应用服务、命令、查询、传输模型、跨上下文门面和扩展端口按能力迁入
  `application`。
- 知识空间、文本数据源、索引版本按聚合拆分 `model` 和 `repository`，
  聚合根、实体和值对象实现共享领域标记。
- MyBatis Mapper、持久化对象、仓储实现、对象存储和暂存实现迁入
  `infrastructure`。
- 知识数据、MinIO 和 pgvector 均通过
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  注册能力；bootstrap 中原有集中 Bean 声明已删除。

## 分页与持久化证据

- 知识空间、文本数据源和索引版本 HTTP 列表统一使用 `pageNumber`、
  `pageSize` 和 `PageResult`。
- MyBatis-Plus 的 `Page/IPage` 只出现在 infrastructure。
- 真实 PostgreSQL 仓储测试验证首页、中间页、末页、空页、总数、固定排序和
  租户隔离。
- 知识空间单表查询已迁移到 `BaseMapper` 和 `LambdaQueryWrapper`。
- XML 仅保留锁、幂等、批次领取和状态迁移等复杂 SQL；列表手工 `LIMIT`、
  `limit + 1`、游标编码和 `subList` 已移除。

## 缺陷回归

首次全反应堆测试发现两个装配根因：

1. 三个 bootstrap 未依赖 `platform-web`，导致知识异常未进入统一映射，
   权限拒绝和内容完整性失败错误返回 500。
2. 知识持久化自动配置在无 MyBatis 会话工厂时仍注册 Mapper，破坏最小启动。

修复后，三个 bootstrap 显式依赖 `platform-web`；持久化自动配置声明在
MyBatis-Plus 自动配置之后，并以 `SqlSessionFactory` 作为启用条件。定向回归
确认权限拒绝返回 403、依赖响应异常返回 502、无外部基础设施的最小启动通过。

## 验证记录

| 验证 | 结果 |
| --- | --- |
| `./mvnw -q test` | 290 项测试通过，失败 0，错误 0，跳过 0 |
| 知识数据、MinIO、pgvector 定向测试 | 通过 |
| 控制面异常与最小启动定向回归 | 通过 |
| `make project-verify` | 9 个工作包验证通过 |
| `make architecture-guard` | 12 项门禁通过；知识数据旧目录归零 |
| 前端控制面客户端分页测试 | 5 项通过 |
| `npm run typecheck` | 通过 |
| `git diff --check` | 通过 |

执行环境：macOS、JDK 26.0.2、Maven 3.9.16、Docker Desktop。

## 剩余边界

该证据只关闭知识数据上下文迁移切片，不代表 WP-0009 完成。模型运行时、
助手交付、可信查询、评测运维、启动模块合并、完整复杂 SQL 允许清单和最终
容器演练仍未完成，M1 继续阻塞。
