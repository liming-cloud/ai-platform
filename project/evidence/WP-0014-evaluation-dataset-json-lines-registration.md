# M6 私有评测数据集 JSON Lines 登记

## 范围

ADR-0053 已接受后，`evaluation-operations` 将上传正文在进入私有对象存储前暂存为严格 UTF-8 JSON Lines，并由服务端唯一计算 SHA-256 摘要和条目数。接口只接收 multipart `content` 与 `retentionPolicyId`；身份、租户和权限从 `TrustedRequestContext` 的 Sa-Token 会话恢复，响应不投影正文或私有对象引用。

## 可复现证据

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| 条目语法红灯 | `./mvnw -q -pl backend/contexts/evaluation-operations -am test -Dtest=LocalEvaluationDatasetContentStagingAdapterTest -Dsurefire.failIfNoSpecifiedTests=false` | 初始缺少暂存适配器，`testCompile` 失败。 |
| 条目语法绿灯 | 同上 | 严格 UTF-8、空行、未知字段、重复字段、`ANSWER` 空证据与 `REFUSE` 答案候选均失败关闭。 |
| 应用编排红绿 | `./mvnw -q -pl backend/contexts/evaluation-operations -am test -Dtest=EvaluationDatasetRegistrationServiceTest -Dsurefire.failIfNoSpecifiedTests=false` | 初始服务尚接收调用方摘要和条目数而失败；改为服务端暂存结果后通过。 |
| HTTP 回归 | `./mvnw -q -pl backend/contexts/evaluation-operations -am test -Dtest=EvaluationDatasetControllerTest -Dsurefire.failIfNoSpecifiedTests=false` | multipart 只绑定正文与保留策略，响应不包含私有对象引用或正文。 |
| 模块回归 | `./mvnw -q -pl backend/contexts/evaluation-operations -am test` | 通过；日志含既有 Spring、Testcontainers 与预期失败夹具警告。 |
| 架构与项目门禁 | `python3 -m unittest scripts.quality.test_backend_architecture_guard && python3 scripts/quality/backend_architecture_guard.py verify && make project-verify` | 19 项架构门禁、项目治理门禁均通过。 |

## 部署边界

控制面将评测迁移目录与元数据存储显式接入，部署组合环境显式启用同一受控私有 MinIO 桶的评测上下文路径。默认最小启动保持对象存储关闭；未配置私有对象存储时不注册登记服务，避免伪成功。容器继续通过 `tmpfs` 提供短生命周期暂存目录，不保存正文到本地持久卷。

## 后续限制

本切片不开放正文读取、下载或导出，不启动评测运行，也不改变发布授权。评测执行、冻结质量门禁、发布决定与正式部署浏览器证据仍须分别完成并登记。
