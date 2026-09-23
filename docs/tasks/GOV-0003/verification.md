# GOV-0003 后端上下文依赖边界验证证据

| 字段 | 值 |
| --- | --- |
| 责任任务 | GOV-0003 |
| 输入提交 | f6204687bc433be93d019697e8b12796366ce0f8 |
| 验证提交 | pending |
| 验证范围 | 上下文和扩展的 Maven 编译边、Java 导入、公开契约映射、自动配置及后端回归 |
| 日期 | 2026-09-23 |
| 决策 | ADR-0022、ADR-0082 |

## 基线与红灯

输入提交存在 15 条上下文编译边和 6 条扩展编译边，Maven 图无环；跨上下文及扩展存在直接导入他方 `domain`、非 facade application、`infrastructure` 和自动配置。原 `make architecture-guard` 不检查 POM 或跨模块 Java 导入。先增加失败反例并确认原状态未通过，随后迁移公开契约和内部映射，不放宽白名单。另三条公开签名反例分别证明：模型运行规格的记录分量、模型扩展公开方法、助手目录公开构造器曾暴露领域类型；三项均先失败再修复。

## 修改与边界

- 上下文调用进入拥有者的 `application/.../facade`。模型配置、助手发布快照、知识索引候选、查询运营事实各自拥有公开值类型，内部服务负责领域映射。
- 扩展仅使用所属上下文的 `application/.../port/out` 及公开扩展契约。模型供应商请求/结果经内部映射；对象存储由知识上下文内部桥接并在读取前验证冻结对象归属。
- 跨上下文自动配置导入移除，由各上下文注册自身配置；删除无实际公开引用的 Maven 编译边。
- 守卫检查 Maven 编译图、循环、缺失与闲置依赖、上下文和扩展导入。反例包括跨上下文领域和内部端口、跨上下文自动配置、扩展领域和基础设施导入。

## 最终 Maven 编译边

上下文编译边共 11 条：`assistant-delivery -> model-runtime, knowledge-data`；`evaluation-operations -> organization-access, trusted-query, assistant-delivery, knowledge-data, model-runtime`；`knowledge-data -> model-runtime`；`trusted-query -> knowledge-data, model-runtime, assistant-delivery`。六条扩展编译边分别为三个模型供应商扩展及 `provider-spring-ai-common -> model-runtime`、`object-storage-minio -> knowledge-data`、`vector-pgvector -> knowledge-data`。边均由实际公开包引用支撑，图无环，守卫违规为 0。

`trusted-query` 仓储集成测试单独以 `test` 范围引用 `organization-access` 的 Flyway 迁移资源；此边不进入生产编译图。模型、知识、助手上下文原有的四条未使用组织上下文编译边已移除。

## 验证结果

Maven 3.9.16 对完整 `backend/pom.xml` 运行 `verify` 退出码为 0；321 份 Surefire 报告合计 968 项测试，失败、错误、跳过均为 0。架构与前端产品守卫测试 38 项通过，后端依赖边界违规 0；治理测试 68 项通过。`make project-status`、`make project-verify`、`make architecture-guard`、`make resume-context` 和 `git diff --check` 均通过。数据库与对象存储集成测试使用隔离 Testcontainers，不接触部署实例。

整改过程中的全量测试揭示了几处装配依赖：知识上下文自动配置测试曾断言他方配置顺序，已改为检查本上下文持久化顺序；移除 `trusted-query` 的组织上下文编译边使测试类路径缺少迁移资源，已改为测试范围依赖，定向仓储测试通过；OpenAI 自动配置测试仍注入旧内部属性，已改为公开调用设置，定向测试通过。随后控制面运营总览返回 404：事实源配置顺序改变使条件 Bean 跳过。评测运营仅以 `afterName` 保留条件注册顺序，不导入他方自动配置类；`OperationsApiTest` 定向重跑通过，最终完整后端验证通过。

代码图谱用于确认模型扩展、助手目录及自动配置的调用影响。图谱当前索引仍返回主仓库基线的部分旧导入，隔离工作树的精确源码与编译结果为准；没有把过期图谱摘要当作修复证据。

固定 diff 评审核对了改动路径、公开签名、模型请求映射、MinIO 冻结对象成员校验与流摘要校验、自动配置条件及 POM 范围。改动限定后端边界、守卫、ADR 与本治理任务；没有修改业务 API、数据库迁移、前端、部署或产品证据。名称排序仍依赖被排序配置类的稳定名称，控制面启动集成测试覆盖该关系；后续重命名须同步验证。

## 范围与回归限制

本次没有产品功能、部署、数据库、OpenDesign 或截图操作，也未运行会创建业务对象的验收。WP-0017 的原子开户切片历史结论不变，WP-0017 整包及 M6 均未完成。
