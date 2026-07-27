# WP-0008 MyBatis 映射文件整改证据

## 结论

提交 `8104ea62c251eec102bf8fe0de5c858d42ad8448` 已将 8 个 Java 映射器中的
96 条 `@Select`、`@Insert`、`@Update`、`@Delete` SQL 全部迁移到对应 Mapper XML。
Java 映射器现在只保留接口方法和 `@Param` 参数命名，SQL 注解扫描结果为零。

## 覆盖范围

| 上下文 | Mapper XML |
| --- | --- |
| 组织权限 | `TenantMemberCredentialMapper.xml` |
| 知识数据 | `KnowledgeSpaceMapper.xml`、`TextDataSourceMapper.xml`、`IndexVersionMapper.xml` |
| 模型运行时 | `ProviderConnectionMapper.xml`、`ModelDeploymentMapper.xml` |
| 助手交付 | `AssistantMapper.xml` |
| 可信查询 | `QueryRunMapper.xml` |

结果映射、锁查询、幂等查询、游标分页、乐观更新、批量写入和查询事件均保持原有数据库
语义。映射文件统一位于各上下文的 `src/main/resources/mapper/<上下文>/` 目录。

## 红绿验证

先从 `TenantMemberCredentialMapper` 删除 SQL 注解但不增加 XML，执行真实 PostgreSQL
仓储测试，两个用例均以 `Invalid bound statement` 失败。增加 Mapper XML 后，同一命令
恢复通过，证明测试确实覆盖 XML 装载，而不是只做静态文本检查。

## 已执行命令

| 命令 | 结果 |
| --- | --- |
| `./mvnw -q -pl backend/contexts/organization-access -am -Dtest=PostgresTenantMemberCredentialRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test` | 缺失 XML 时按预期失败，补齐 XML 后 2 项通过 |
| `make backend-test` | 216 项通过，0 失败、0 错误、0 跳过 |
| Python 标准 XML 解析器读取全部 Mapper XML | 8 个文件语法通过 |
| `make architecture-guard` | 7 项门禁测试通过，SQL 违规文件为 0 |
| `make project-verify` | 通过，当前前沿为 WP-0008 |
| `git diff --check` | 通过 |

执行环境为 macOS、JDK 26.0.2、Maven Wrapper 3.9.16、PostgreSQL Testcontainers。

## 防回归

`project/architecture-debt/WP-0008-backend.json` 已把 `sqlAnnotationFiles` 收紧为空集合。
后续任何主代码重新引入 SQL 注解都会使 `make architecture-guard` 失败。

## 剩余边界

本证据只关闭“Java 注解编写 SQL”这一项。UUID、运行时职责、Sa-Token 安全适配、
目录平铺和领域层整改仍未完成。
