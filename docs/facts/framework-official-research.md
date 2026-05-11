# 框架官方资料调研

Stage: architecture-facts  
Category: facts  
Status: current  
Review Status: not-reviewed  
Reviewers: architect, backend lead  
Approval: pending  
Inputs: Spring AI / Sa-Token / MyBatis-Plus official documentation  
Outputs: 框架能力、配置落点、项目约束  
Depends On: `docs/40-architecture-and-tech-spec.md`, `docs/45-details/01-backend-code-organization.md`  
Load When: framework-integration/backend-module-development  
Next: 回写架构、模块设计和控制面

## 资料范围

本文件只记录对本项目设计有约束力的官方资料结论，不替代官方文档。后续框架升级时，先更新本文件，再回写 `docs/32-control-surface.md`、`docs/40-architecture-and-tech-spec.md` 和 `docs/45-details/01-backend-code-organization.md`。

| 框架 | 官方资料 | 本项目关注点 |
| --- | --- | --- |
| Spring AI | https://docs.spring.io/spring-ai/reference/getting-started.html | BOM、Spring Boot 版本、starter、Maven Central |
| Spring AI | https://docs.spring.io/spring-ai/reference/api/chatclient.html | `ChatClient`、streaming、structured output、prompt template |
| Spring AI | https://docs.spring.io/spring-ai/reference/api/advisors.html | Advisor 链、chat memory、RAG advisor |
| Spring AI | https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html | `QuestionAnswerAdvisor`、`RetrievalAugmentationAdvisor`、metadata filter |
| Spring AI | https://docs.spring.io/spring-ai/reference/api/tools.html | `ToolCallback`、tool definition、tool execution lifecycle |
| Spring AI | https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html | pgvector starter、schema 初始化、metadata filter、schema validation |
| Spring AI | https://docs.spring.io/spring-ai/reference/observability/index.html | ChatClient / ChatModel / EmbeddingModel / VectorStore observability |
| Sa-Token | https://sa-token.cc/doc.html | 当前在线文档入口和版本 |
| Sa-Token | https://github.com/dromara/Sa-Token | 框架定位、Spring Boot 集成、Boot 3 支持、功能边界 |
| Sa-Token | https://raw.githubusercontent.com/dromara/Sa-Token/dev/sa-token-doc/api/stp-util.md | `StpUtil` 登录、登出、会话、权限和角色 API |
| Sa-Token | https://raw.githubusercontent.com/dromara/Sa-Token/dev/sa-token-doc/use/login-auth.md | 登录认证流程和 token/session 语义 |
| Sa-Token | https://raw.githubusercontent.com/dromara/Sa-Token/dev/sa-token-doc/use/jur-auth.md | 权限认证流程 |
| MyBatis-Plus | https://baomidou.com/en/getting-started/install/ | Spring Boot 3 starter、BOM、`mybatis-plus-jsqlparser` |
| MyBatis-Plus | https://baomidou.com/en/reference/ | Spring Boot 配置、`mapper-locations`、多模块 XML 扫描 |
| MyBatis-Plus | https://baomidou.com/en/plugins/ | `MybatisPlusInterceptor` 和插件顺序 |
| MyBatis-Plus | https://baomidou.com/en/plugins/tenant/ | `TenantLineInnerInterceptor` 和 `tenant_id` |
| MyBatis-Plus | https://baomidou.com/en/plugins/pagination/ | 分页插件、`mybatis-plus-jsqlparser`、`DbType` |
| MyBatis-Plus | https://baomidou.com/en/plugins/optimistic-locker/ | 乐观锁插件和 `@Version` |
| MyBatis-Plus | https://baomidou.com/en/plugins/block-attack/ | 防全表更新/删除 |
| MyBatis-Plus | https://baomidou.com/en/guides/auto-fill-field/ | `MetaObjectHandler` 和审计字段自动填充 |

## Spring AI 设计结论

### 版本和依赖

- Spring AI 当前稳定文档显示支持 Spring Boot 3.4.x 和 3.5.x。
- 依赖版本必须通过 `org.springframework.ai:spring-ai-bom` 纳入 `ai-platform-bom`，业务子模块不得直接写 Spring AI 版本。
- Spring AI 1.0+ 正式版本使用 Maven Central，不需要为稳定版本配置 milestone / snapshot repository。
- provider 和 vector store 使用 starter 依赖，例如 `spring-ai-starter-model-openai`、`spring-ai-starter-vector-store-pgvector`；具体 starter 只允许在 BOM 和对应 infrastructure module 中出现。

### ChatClient 和 Model Gateway

- `ChatClient` 是业务调用 chat model 的主要 fluent API，支持普通调用、streaming、prompt template、structured output。
- 本项目不能让 controller 或业务模块直接使用 `ChatClient`。应用层只能依赖 `ModelGatewayPort`、`EmbeddingPort`、`RerankPort` 等端口。
- `ChatClient` 的创建、provider 路由、model option、超时、重试、错误归一和观测标签统一放在 `model` context 的 `infrastructure/ai`。
- provider API key 不写入 `application.yml`。`application.yml` 只允许写本地开发默认项和非密钥开关；租户级 provider credential 存在数据库/secret store，由 adapter 运行时装配。

### RAG 和 Advisor

- Spring AI 对 RAG 提供两层能力：使用 Advisor 的开箱 RAG，以及基于模块的 `RetrievalAugmentationAdvisor`。
- `QuestionAnswerAdvisor` 和 `VectorStoreDocumentRetriever` 支持 metadata filter；本项目必须用 filter 表达 tenant、knowledge base、document ACL 和 source 状态。
- RAG prompt template、query rewrite、query compression、translation、rerank 等策略归属 Retrieval Runtime，不归属业务 controller。
- 检索未命中时必须保留 trace 和质量样本，不允许让模型基于空上下文编造答案作为成功态。

### Tool Calling

- Spring AI 的 tool calling 以 tool definition、`ToolCallback`、`ToolCallingManager` 为核心；模型请求中只暴露被授权 tool 的名称、描述和参数 schema。
- 本项目的 tool 不允许直接由模型访问企业凭证。Tool Runtime 先完成租户授权、应用授权、风险等级、审批策略和审计，再向 Model Gateway 提供可调用 `ToolCallback`。
- 高风险写操作默认不使用框架自动直接执行，必须进入审批或 user-controlled execution 语义，由 Tool Runtime 记录 `tool_run` 和 `audit_log`。
- Tool 参数和执行结果可能包含敏感信息，默认不进入 span content；需要排障时只能在脱敏、短期和受控环境开启。

### VectorStore / PGvector

- 首期使用 Spring AI PGvector starter 对接 pgvector；本地依赖由 `deploy/docker-compose.yml` 提供 PostgreSQL + pgvector。
- PGvector schema 初始化在新版 Spring AI 中需要显式开启，不能假设默认建表。
- `spring.ai.vectorstore.pgvector.dimensions` 必须与 embedding model 维度一致；维度变更需要重建 vector table/index。
- 自定义 schema/table 时必须开启 schema validation，降低配置错误和 SQL 注入风险。
- metadata filter 是租户隔离和知识 ACL 的关键控制点；任何 retrieval 查询都不能缺失 tenant/filter。

### Observability

- Spring AI observability 需要 Spring Boot Actuator，覆盖 `ChatClient`、Advisor、`ChatModel`、`EmbeddingModel`、ImageModel、`VectorStore`。
- token usage、模型请求/响应 model、tool names、vector store 操作延迟等是平台 usage/trace 的基础信号。
- prompt、completion、tool argument、tool result 和 vector query response 默认不得记录明文；生产默认保持 `log-prompt=false`、`log-completion=false`、tool content export disabled。
- 平台自己的 Observability module 以 Spring AI observation 为底层信号，再补充租户、应用、知识库、tool、成本和业务错误码维度。

## Sa-Token 设计结论

### 版本和依赖

- Sa-Token 官方仓库定位为 Java 权限认证框架，核心能力包括登录认证、权限认证、Session 会话、SSO、OAuth2、微服务鉴权等。
- Spring Boot 3 项目使用 `cn.dev33:sa-token-spring-boot3-starter`，版本由 `ai-platform-bom` 管理。
- Redis session 是生产默认项。项目必须引入 Sa-Token Redis 集成并连接 `deploy` Redis；不得以内存 session 作为完成态。

### 登录和会话

- 登录流程的本质是系统先完成账号密码或企业身份源校验，得到唯一 `userId` / principal id，再调用 `StpUtil.login(...)` 创建 token 和 session。
- `StpUtil` 是 Sa-Token 的主要工具类，覆盖 login、logout、isLogin、checkLogin、getLoginId、session、token timeout、kickout、role、permission 等能力。
- 本项目不在 controller 中散落 `StpUtil` 调用。统一由 `AuthApplicationService` 和 `SaTokenAuthAdapter` 封装，controller 只表达登录、登出、当前主体等 HTTP 契约。
- token login id 只保存 `tenantId`、`actorType`、`actorId` 的可解析主体引用，不保存角色、权限、租户名称、密钥或个人敏感资料。

### 权限和路由

- 权限码和角色来自 IAM 事实源，通过 Sa-Token 的权限查询桥接到 `StpUtil.checkPermission(...)` / `checkRole(...)`。
- `/api/**` 默认受路由拦截保护；登录、健康检查和公开资源必须显式白名单。
- 平台管理员跨租户权限、租户管理员资源权限和普通用户应用权限必须使用不同 permission code 前缀，避免字符串权限失控。
- Sa-Token 异常统一转换为平台 `AUTH_*` / `PERMISSION_*` 错误码和 `ApiResponse<Void>`。

## MyBatis-Plus 设计结论

### 配置基线

- MyBatis-Plus 通过 `application.yml` 的 `mybatis-plus` 配置接入 Spring Boot。
- 多 Maven module 下 Mapper XML 扫描必须使用 `classpath*:/mapper/**/*.xml`，否则无法加载多 JAR 包中的 XML。
- 下划线转驼峰保持开启，数据库字段命名统一 snake_case，Java 属性统一 camelCase。
- 逻辑删除、主键策略、枚举处理和 type handler 必须集中在 boot/shared infrastructure 配置，不由业务模块自行漂移。

### 插件基线

- `MybatisPlusInterceptor` 是插件入口；官方推荐插件顺序为：多租户/动态表名先，分页/乐观锁居中，非法 SQL / 防全表更新删除最后。
- 多租户插件 `TenantLineInnerInterceptor` 默认字段名是 `tenant_id`；本项目除明确全局字典表外，业务表必须具备 `tenant_id`。
- 分页插件 `PaginationInnerInterceptor` 支持 PostgreSQL；从 MyBatis-Plus 3.5.9 起需单独引入 `mybatis-plus-jsqlparser`。
- 乐观锁通过 `OptimisticLockerInnerInterceptor` 和实体 `@Version` 字段实现；需要并发修改的配置类聚合必须启用。
- 防全表更新/删除通过 `BlockAttackInnerInterceptor` 启用；任何绕过必须有审计和设计说明。

### 持久化边界

- MyBatis-Plus `Mapper`、PO、`IPage`、Wrapper 只能存在于 `infrastructure/persistence` 和 query side，不得进入 domain。
- Repository implementation 负责 PO 与 aggregate 映射；domain repository port 不暴露 MyBatis-Plus 类型。
- `MetaObjectHandler` 统一填充 `tenant_id`、`created_at`、`created_by`、`updated_at`、`updated_by`、`deleted`、`version` 等审计字段。
- 自定义 SQL 必须放在 mapper XML 或 mapper interface，保留 tenant/filter/permission 约束，禁止 application 直接拼接 SQL。

## 回写要求

- `docs/32-control-surface.md` 必须把上述内容固化为禁止项和门禁项。
- `docs/40-architecture-and-tech-spec.md` 必须说明三套框架在架构中的正式落点。
- `docs/45-details/01-backend-code-organization.md` 必须给出后端模块开发时的包路径、配置类、依赖、测试和完成标准。
- `scripts/generate_context_pack.py` 必须把本文件作为 `framework_integration` 的按需事实源。
