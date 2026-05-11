# AI Platform

企业级多租户 AI 平台规划仓库。

当前阶段是 S1/S2/S2.1/S3/S4 已完成后的 S5.1 准备。交付目标默认按 `production-targeted` 处理，因为目标是企业级平台、多租户、模型治理、知识库、工具调用和全链路监控。

## 产品定位

AI Platform 是一个企业 AI 控制面和运行时平台，目标能力包括：

- 统一接入多厂家、多类型、多型号模型。
- 管理企业知识库，支持文档、网页、对象存储、数据库等知识来源。
- 提供企业级知识库问答、应用编排、工具调用和系统集成。
- 监控模型、知识库、知识条目、工具调用、成本、延迟、质量和使用频率。
- 支持多租户隔离、RBAC、审计、密钥管理、配额和合规治理。

## 规划文档

从 [docs/00-index.md](docs/00-index.md) 开始阅读。

核心文档：

- [竞品分析与产品发现](docs/10-discovery-brief.md)
- [PRD 与范围边界](docs/20-prd-and-scope.md)
- [项目研发控制面](docs/30-project-harness.md)
- [上下文加载规则](docs/31-context-loading.md)
- [控制面与门禁](docs/32-control-surface.md)
- [架构与技术方案](docs/40-architecture-and-tech-spec.md)
- [模块与服务详细设计](docs/45-module-and-service-design.md)
- [研发实施路径](docs/50-delivery-plan.md)
- [实施记录](docs/60-implementation-notes.md)

辅助事实和证据：

- [仓库预检事实](docs/facts/repo-preflight.md)
- [环境 readiness](docs/facts/environment-readiness.md)
- [命令目录](docs/facts/command-catalog.md)
- [竞品事实源](docs/facts/market-sources.md)
- [框架官方资料](docs/facts/framework-official-research.md)
- [缓存官方资料](docs/facts/cache-official-research.md)
- [阶段门禁结论](docs/evidence/gate-verdicts.md)
- [偏差日志](docs/evidence/deviation-log.md)
- [S1/S2 实施证据](docs/evidence/s1-s2-implementation-evidence-2026-05-10.md)
- [S3 鉴权纵切实施证据](docs/evidence/s3-auth-slice-evidence-2026-05-10.md)
- [S4 Provider Catalog 证据](docs/evidence/s4-provider-catalog-evidence-2026-05-10.md)

## 当前结论

- 当前 S4 Provider Catalog 已经 build ready。
- S1 `deploy` 真实依赖已通过健康检查。
- S2 后端 Maven 多模块、BOM 和平台基础设施骨架已通过 `mvn -f backend/pom.xml test`。
- S3 后端 Spring Boot、真实 PostgreSQL/Redis/MinIO、Vue 3 控制台和页面操作 smoke 已通过。
- S4 已完成平台模型基础配置、租户模型配置、RBAC 菜单闭环和 SiliconFlow 真实模型调用证据。
- 后端技术栈已确认：Spring Boot + Spring AI + Sa-Token + MyBatis-Plus + Redisson + Caffeine，按 TDD + DDD 推进，不引入 Python worker。
- 下一步是 S5.1 统一模型调用、trace 和 usage 基线；部署模式、前端方案和租户隔离等级已确认。

## 本地辅助脚本

```bash
python3 scripts/harness_repo_preflight.py
python3 scripts/generate_context_pack.py
deploy/scripts/up.sh
deploy/scripts/healthcheck.sh
mvn -f backend/pom.xml test
pnpm --dir frontend/console-web test
pnpm --dir frontend/console-web build
```

脚本会生成 `docs/_generated/repo-preflight.json` 和 `docs/_generated/context-pack.json`，供后续研发阶段优先读取。
