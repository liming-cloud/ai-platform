# WP-0014：私有评测数据集登记应用边界

## 结果

`EvaluationDatasetRegistrationService` 只从 `TrustedRequestContext` 取得租户和 `assistant:write` 权限。它将一次性
私有内容流交给 evaluation-operations 内容端口，领域根只接收返回的私有引用、摘要、条目数与保留策略；元数据
提交失败时删除已写对象。该切片不提供正文读取、HTTP、数据库或真实对象存储实现。

## 红绿与回归

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| 红灯 | `./mvnw -q -pl backend/contexts/evaluation-operations -am test -Dtest=EvaluationDatasetRegistrationServiceTest -Dsurefire.failIfNoSpecifiedTests=false` | 因应用端口、一次性内容请求和服务尚不存在而 `testCompile` 失败。 |
| 绿灯 | 同一命令 | 无 `assistant:write` 不写入；成功仅提交元数据；提交失败删除私有对象。 |
| 模块回归 | `./mvnw -q -pl backend/contexts/evaluation-operations -am test` | 通过，含既有 Spring／Testcontainers 警告。 |
| 门禁 | `python3 scripts/quality/backend_architecture_guard.py verify`；`make project-verify` | 通过。 |

## 未完成

存储端口尚未接入真实私有 MinIO，登记存储尚未接入 PostgreSQL、事务或发件箱；运行、决定、授权 HTTP 及
`AssistantService.publishVersion` 接入也尚未实施。
