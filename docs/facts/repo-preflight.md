# 仓库预检事实

Stage: facts  
Category: facts  
Status: current  
Review Status: agent-reviewed  
Reviewers: engineering lead  
Approval: agent-approved  
Inputs: filesystem, git status  
Outputs: 仓库事实摘要  
Depends On: none  
Load When: always-entry  
Next: `docs/30-project-harness.md`

## 当前仓库状态

- 仓库路径：`/Users/lingou/slm/project/ai-project/ai-platform`
- 当前分支：`master`
- 当前业务代码：已有 S3 鉴权纵切，包括后端登录/登出/当前用户/当前租户/跨租户 403 和前端控制台。
- 当前构建配置：`backend/pom.xml` Maven 多模块、`backend/infrastructure/ai-platform-bom`、`backend/quality/ai-platform-architecture-tests`、`frontend/console-web/package.json`。
- 当前 README：已替换为规划入口。
- 当前文档系统：已建立 `docs/00-index.md`、`01/10/20/30/31/32/40/45/50/60`。
- 当前详细设计分层：已建立 `docs/45-details/00-index.md`、后端代码组织规范和平台基础设施模块设计。
- 当前研发环境目录：已建立 `deploy/`，包含 Docker Compose 与启停/健康检查脚本。
- 当前研发环境状态：PostgreSQL、Redis、MinIO 已通过 `deploy/scripts/healthcheck.sh`；前后端本机进程和页面 smoke 已通过。
- 当前脚本：`scripts/harness_repo_preflight.py`、`scripts/generate_context_pack.py`。
- 本地 IDE 元数据：`.idea/` 已通过 `.gitignore` 排除，不纳入规划交付。

## 缺失事实

- 后端技术栈已由用户确认：Spring Boot + Spring AI + Sa-Token + MyBatis-Plus + Redisson + Caffeine。
- 用户明确要求 TDD + DDD 架构，不引入 Python worker。
- 部署模式已确认：首期私有化优先，本地开发采用 Docker Compose 依赖 + 前后端本机进程。
- PostgreSQL、Redis、MinIO/对象存储和 pgvector 是当前本地研发环境基线，真实启动验证已通过。
- 首批模型供应商和知识来源优先级尚未确认。
- CI、container、生产发布和回滚策略尚未建立；本地 test/build/migration 基线已建立。

## 初始建议

- 后端控制面和 AI 运行时：Java 21 + Spring Boot 3 + Spring AI + Sa-Token + MyBatis-Plus + Redisson + Caffeine + PostgreSQL + Redis。
- AI 相关基础能力不引入 Python worker；文档解析、embedding、rerank、eval、异步 ingestion 均优先由 Java 模块和 Spring AI adapter 承载。
- 前端：Vue 3 + TypeScript + Vite，当前入口为 `frontend/console-web`。
- 租户隔离：首期共享库共享表 + `tenant_id` 强隔离 + MyBatis-Plus tenant interceptor。
- 基础设施：PostgreSQL、pgvector 或独立向量库、Redis、MinIO/S3、OpenSearch 可选、Kafka/RabbitMQ 可选；平台基础设施模块规划为 `backend/infrastructure/ai-platform-ai/cache/security/persistence/storage/telemetry`。

其中后端、前端技术栈、部署模式、租户隔离等级、`deploy` 本地研发依赖和平台基础设施模块边界已进入执行基线。
