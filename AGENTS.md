# AI Platform Agent Guide

本仓库当前处于绿地项目的产品规划、架构设计和实施路径阶段。所有 agent 必须先读取 [docs/00-index.md](docs/00-index.md)，再按当前任务加载对应阶段文档。

## 默认规则

- 文档和评审说明使用简体中文。
- 交付目标默认为 `production-targeted`。
- 后端技术栈已确认：Spring Boot + Spring AI + Sa-Token + MyBatis-Plus。
- 后端架构 profile 启用 `springboot-enterprise-ddd`，所有核心功能按 TDD 推进。
- `30` / `40` / `45` / `50` 未评审通过前，不进入业务代码实现。
- 不允许以 `InMemory*`、`Fake*`、硬编码租户、伪 provider、伪索引、伪监控作为正式完成态。
- 不引入 Python worker；AI 能力优先通过 Spring AI 适配，必要的文档解析或异步任务也由 Java 模块承载。
- 框架用法先看 `docs/facts/framework-official-research.md`；禁止把 Spring AI、Sa-Token、MyBatis-Plus 的泛泛经验替代本项目已回写的约束。
- `ChatClient` / `ChatModel` / `VectorStore` 只允许在 Spring AI adapter 或 boot configuration；`StpUtil` 只允许在 auth adapter / auth service；MyBatis-Plus PO / Mapper / Wrapper / `IPage` 不得进入 domain 或 HTTP 响应。
- 多租户 IAM、RBAC、菜单和前端授权渲染先看 `docs/facts/iam-rbac-official-research.md`；菜单只控制导航可见性，接口必须由权限码和 Sa-Token/application check 保护。
- 缓存设计先看 `docs/facts/cache-official-research.md`；二级缓存统一使用 Caffeine L1 + Redisson/Redis L2，业务模块不得自建缓存或缓存敏感明文。
- 平台基础设施模块边界先看 `docs/45-details/02-platform-infrastructure-design.md`；Spring AI、Sa-Token、MyBatis-Plus、Redisson/Caffeine、S3/MinIO、OpenTelemetry 等非业务框架能力必须落到同级基础设施模块，业务 domain 不得依赖这些模块。
- 前端控制台设计、页面样式和交互状态先看 `docs/45-details/03-frontend-console-design-system.md`；企业控制台不得以能启动的 smoke 页面、营销型展示、大面积装饰视觉或临时单页样式作为完成态。
- 任何涉及模型密钥、租户数据、知识库数据、工具凭证的动作必须有审计和权限边界。
- 后续实现必须先消费 `docs/_generated/context-pack.json`；不存在或过期时先运行 `python3 scripts/generate_context_pack.py`。
- 团队拓扑固定为架构师、产品工程师、资深后端研发、资深前端工程师、UI 工程师；角色职责和切片参与规则见 `docs/33-team-topology.md`。

## 最小加载路径

1. `docs/00-index.md`
2. `docs/_generated/context-pack.json`
3. 当前阶段主文档
4. 相关 facts / evidence

## 当前禁止动作

- 不直接生成后端或前端业务骨架。
- 不选择云厂商锁定实现作为唯一架构。
- 不把竞品功能清单直接等同于产品范围。
- 不跳过评审直接进入编码。
