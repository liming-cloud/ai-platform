# 01 后端整体代码组织规范

Stage: module-design-detail  
Category: design-detail  
Status: current  
Review Status: agent-reviewed  
Reviewers: architect, backend lead  
Approval: agent-approved  
Inputs: `docs/40-architecture-and-tech-spec.md`, `docs/45-module-and-service-design.md`  
Outputs: 后端 Maven 多模块、DDD 分层、模块开发步骤和代码规范  
Depends On: `docs/32-control-surface.md`, `docs/45-details/00-index.md`  
Load When: backend-module-development  
Next: S3 鉴权纵切中的后端实现

## 目标

本文件是后端模块开发的主要规范。后续开发任一后端模块时，优先读取本文件，而不是全量读取架构和模块设计主文档。

本规范覆盖：

- Maven 多模块组织。
- Maven 坐标规范：`groupId=org.fujay`，`artifactId` 必须以 `ai-` 开头。
- BOM 统一依赖管理。
- 平台基础设施模块与业务模块的边界。
- DDD 包结构和依赖方向。
- Spring AI、Sa-Token、MyBatis-Plus 的落点。
- 统一响应、统一异常、统一注释。
- TDD 开发顺序。
- 新增某个业务模块时的最小步骤。

## Maven 多模块目录

目标目录：

```text
backend/
  pom.xml
  infrastructure/
    ai-platform-bom/
      pom.xml
    ai-platform-shared/
      pom.xml
      src/main/java/org/fujay/ai/platform/shared/
    ai-platform-ai/
      pom.xml
      src/main/java/org/fujay/ai/platform/ai/
      src/test/java/org/fujay/ai/platform/ai/
    ai-platform-cache/
      pom.xml
      src/main/java/org/fujay/ai/platform/cache/
      src/test/java/org/fujay/ai/platform/cache/
    ai-platform-security/
      pom.xml
      src/main/java/org/fujay/ai/platform/security/
      src/test/java/org/fujay/ai/platform/security/
    ai-platform-persistence/
      pom.xml
      src/main/java/org/fujay/ai/platform/persistence/
      src/test/java/org/fujay/ai/platform/persistence/
    ai-platform-storage/
      pom.xml
      src/main/java/org/fujay/ai/platform/storage/
      src/test/java/org/fujay/ai/platform/storage/
    ai-platform-telemetry/
      pom.xml
      src/main/java/org/fujay/ai/platform/telemetry/
      src/test/java/org/fujay/ai/platform/telemetry/
  apps/
    ai-platform-boot/
      pom.xml
      src/main/java/org/fujay/ai/platform/boot/
  business/
    ai-platform-modules/
      tenant/
        pom.xml
        src/main/java/org/fujay/ai/platform/tenant/
        src/test/java/org/fujay/ai/platform/tenant/
      model/
      knowledge/
      retrieval/
      conversation/
      tool/
      observability/
      governance/
  quality/
    ai-platform-architecture-tests/
      pom.xml
      src/test/java/org/fujay/ai/platform/architecture/
```

模块目录必须先按类型归档，再放具体 Maven module，禁止所有模块直接平铺在 `backend/` 下。

### 模块职责

所有 Maven 模块坐标必须满足：

- `groupId`: `org.fujay`
- `artifactId`: 以 `ai-` 开头
- `version`: 由根工程统一管理，子模块不单独漂移

| Maven 模块 | packaging | 职责 |
| --- | --- | --- |
| `backend/pom.xml` / `artifactId=ai-platform-parent` | `pom` | 聚合所有后端模块，只管理 modules、插件和构建入口 |
| `backend/infrastructure/ai-platform-bom` / `artifactId=ai-platform-bom` | `pom` | 唯一依赖版本管理入口，统一 Spring Boot、Spring AI、Sa-Token、MyBatis-Plus、测试依赖版本 |
| `backend/apps/ai-platform-boot` / `artifactId=ai-platform-boot` | `jar` | Spring Boot 启动应用、配置装配、模块扫描和运行入口 |
| `backend/infrastructure/ai-platform-ai` / `artifactId=ai-platform-ai` | `jar` | Spring AI 基础设施：client factory、advisor、tool callback bridge、vector store helper、AI observation |
| `backend/infrastructure/ai-platform-cache` / `artifactId=ai-platform-cache` | `jar` | Redisson + Caffeine 二级缓存基础设施、cache spec、key builder、失效事件和 metrics |
| `backend/infrastructure/ai-platform-security` / `artifactId=ai-platform-security` | `jar` | Sa-Token 基础设施：principal codec、route interceptor、session helper、auth exception mapping |
| `backend/infrastructure/ai-platform-persistence` / `artifactId=ai-platform-persistence` | `jar` | MyBatis-Plus 基础设施：interceptor、tenant line、pagination、optimistic lock、meta object handler |
| `backend/infrastructure/ai-platform-storage` / `artifactId=ai-platform-storage` | `jar` | MinIO / S3 基础设施：object client、object key、pre-signed URL、multipart、checksum |
| `backend/infrastructure/ai-platform-telemetry` / `artifactId=ai-platform-telemetry` | `jar` | OpenTelemetry、metrics、日志脱敏和 framework observation bridge |
| `backend/infrastructure/ai-platform-shared` / `artifactId=ai-platform-shared` | `jar` | 稳定共享内核：DDD 标记接口、typed id、统一响应、统一异常、错误码、分页、trace、tenant 基础类型 |
| `backend/business/ai-platform-modules/<context>` / `artifactId=ai-platform-<context>` | `jar` | 各 bounded context 的领域、应用、接口和基础设施实现 |
| `backend/quality/ai-platform-architecture-tests` / `artifactId=ai-platform-architecture-tests` | `jar` | 架构与结构约束测试 |

### 依赖规则

- 所有子模块通过 `ai-platform-bom` 获取依赖版本，子模块禁止写第三方依赖版本号。
- `ai-platform-boot` 可以依赖各业务模块。
- 业务模块可以依赖 `ai-platform-shared`。
- 业务模块的 `infrastructure` 包可以依赖 `ai-platform-ai/cache/security/persistence/storage/telemetry`，用来实现业务 port。
- 业务模块之间默认不直接依赖；跨 context 只能通过 application facade、published language、integration event 或 ACL。
- 平台基础设施模块禁止依赖 `ai-platform-modules/<context>`。
- `domain` 包不依赖 Spring Web、Sa-Token、Spring AI、MyBatis-Plus、Redisson、Caffeine、S3/MinIO SDK、HTTP request/response 或 PO。
- `infrastructure` 负责 MyBatis-Plus mapper / PO / repository implementation、Spring AI adapter、Sa-Token adapter、object storage adapter、外部系统 adapter。
- 更完整基础设施模块边界见 `docs/45-details/02-platform-infrastructure-design.md`。

### BOM 依赖管理基线

`ai-platform-bom` 是唯一第三方版本管理入口，必须集中管理：

| 能力 | dependencyManagement 入口 | 使用模块 |
| --- | --- | --- |
| Spring Boot | `org.springframework.boot:spring-boot-dependencies` | root / bom |
| Spring AI | `org.springframework.ai:spring-ai-bom` | `ai-platform-ai`, boot |
| Sa-Token | `cn.dev33:sa-token-spring-boot3-starter`, Redis 集成依赖 | `ai-platform-security`, boot |
| MyBatis-Plus | `com.baomidou:mybatis-plus-spring-boot3-starter`, `com.baomidou:mybatis-plus-jsqlparser` | `ai-platform-persistence`, boot |
| Redisson | `org.redisson:redisson-spring-boot-starter`, `org.redisson:redisson-spring-cache` | `ai-platform-cache`, boot |
| Caffeine | `com.github.ben-manes.caffeine:caffeine` | `ai-platform-cache` |
| Spring Cache | `org.springframework.boot:spring-boot-starter-cache` | `ai-platform-cache`, boot |
| S3 / MinIO SDK | AWS S3 SDK / MinIO Java SDK，具体实现待 S2 前确认 | `ai-platform-storage`, boot |
| OpenTelemetry | Spring Boot Actuator / Micrometer / OpenTelemetry bridge | `ai-platform-telemetry`, boot |
| PostgreSQL | `org.postgresql:postgresql` | boot / persistence integration test |
| Testcontainers | PostgreSQL / Redis / MinIO 相关测试依赖 | integration tests |

规则：

- 子模块 `dependencies` 可以声明依赖坐标，但禁止声明 `<version>`。
- Spring AI provider starter 只允许放在 `ai-platform-ai` 或 boot module，不允许业务模块随意引入。
- Sa-Token starter 和 Redis 集成只允许由 `ai-platform-security` / boot 装配，其他业务模块不直接依赖。
- MyBatis-Plus starter 和 jsqlparser 由 `ai-platform-persistence` / boot 装配，业务模块的 mapper interface 可以编译依赖 MyBatis-Plus，但 domain 不可见。
- Redisson 和 Caffeine 只允许由 `ai-platform-cache` 和 boot 装配；业务模块只依赖平台 cache facade。
- S3/MinIO SDK 只允许由 `ai-platform-storage` 和 boot 装配；业务模块通过 storage port 使用。
- 稳定版本默认只使用 Maven Central；snapshot / milestone repository 需要单独架构评审。

## 单模块包结构

每个 bounded context 必须使用相同结构：

```text
org.fujay.ai.platform.<context>/
  interfaces/
    controller/
    request/
    response/
    assembler/
  application/
    command/
    query/
    service/
    event/
    port/
  domain/
    aggregate/
    entity/
    model/
    valueobject/
    value/
    service/
    repository/
    event/
  infrastructure/
    persistence/
      converter/
      mapper/
      po/
      repository/
      query/
    ai/
    satoken/
    remote/
    messaging/
    scheduler/
```

## 命名规范

| 类型 | 后缀 | 示例 | 位置 |
| --- | --- | --- | --- |
| HTTP 请求 | `Request` | `CreateProviderRequest` | `interfaces/request` |
| HTTP 响应 | `Response` | `ProviderResponse` | `interfaces/response` |
| 应用命令 | `Command` | `CreateProviderCommand` | `application/command` |
| 查询对象 | `Query` | `ProviderPageQuery` | `application/query` |
| 应用服务 | `ApplicationService` 或 `CommandService` | `ProviderCommandService` | `application/service` |
| 聚合根 | 业务名 | `ProviderConfig` | `domain/aggregate` |
| 值对象 | 业务名 | `TenantId`, `ProviderId` | `domain/valueobject` |
| 仓储端口 | `Repository` | `ProviderRepository` | `domain/repository` |
| 持久化对象 | `PO` | `ProviderConfigPO` | `infrastructure/persistence/po` |
| MyBatis-Plus Mapper | `Mapper` | `ProviderConfigMapper` | `infrastructure/persistence/mapper` |
| 仓储实现 | `RepositoryImpl` | `MybatisPlusProviderRepository` | `infrastructure/persistence/repository` |
| 装配器 | `Assembler` | `ProviderAssembler` | `interfaces/assembler` |

### DDD 领域内聚红线

`springboot-enterprise-ddd` profile 下，代码组织不只检查包名和依赖方向，还必须检查业务行为归属：

- `application/service` 只允许编排用例、打开事务、读取当前主体、调用领域对象和仓储端口；不得把状态流转、能力校验、唯一性规则、绑定规则、默认目录规则和领域 ID 生成写成私有 helper。
- shared kernel 必须提供 `AggregateRoot`、`Entity`、`ValueObject` 标记接口；domain 下的聚合、实体和值对象必须显式实现对应标记，禁止 `domain/model` 下出现无语义的贫血 record/class。
- `domain/repository` 是领域端口，不是数据传输结构容器；禁止在 repository interface 内声明对外传播的内部 `record`，尤其是 `*Record`。
- `Repository` 方法应围绕聚合保存/加载或明确 query projection 设计；不能用 `ProviderRecord`、`ModelRecord`、`TenantRecord` 这类贫血数据快照替代聚合、实体和值对象。
- `infrastructure/persistence/repository` 只实现持久化端口和结构映射；禁止成为超大实现类，禁止承载 bootstrap seed、默认菜单/权限、业务绑定策略和多个聚合的规则。
- repository 已按聚合或业务能力拆分后，禁止再增加 `infrastructure/persistence/store` 包；store 会把巨型 repository 复杂度转移到另一层，不能作为完成态。
- PO 到领域对象的转换应拆到专门 mapper/converter，并保持纯结构映射；转换方法不得成为隐藏业务规则的私有方法堆。
- `record` 可用于 HTTP request/response、application command/query/dto、不可变值对象或局部私有不可跨层传播结构；不能用 repository 内部 `record` 作为领域模型。

实现前必须运行：

```bash
python3 scripts/harness_ddd_guard.py
```

当前该脚本结论为 `pass`，后续功能开发必须保持该脚本绿色；任何 blocker 级回归都必须先恢复 DDD 领域内聚。

## 统一响应规范

共享模块提供：

```java
public record ApiResponse<T>(
    String code,
    String message,
    T data,
    String traceId
) {}
```

规则：

- controller 只能返回 `ApiResponse<T>` 或 `PageResponse<T>`。
- 成功响应 `code=0`。
- 失败响应使用稳定错误码。
- 成功、失败、异常等面向调用方的描述必须使用中文。
- 不直接返回 MyBatis-Plus `IPage`、PO、entity 或 aggregate。
- `traceId` 必须贯穿 HTTP、model invocation、retrieval、tool run。

## 统一异常规范

共享模块提供异常基类：

- `DomainException`
- `ApplicationException`
- `InfrastructureException`

规则：

- domain 只能抛 `DomainException` 或其子类。
- application 可抛 `ApplicationException`，并保留领域异常语义。
- infrastructure 将外部依赖异常转换为 `InfrastructureException`。
- controller advice 统一转换为 `ApiResponse<Void>`。
- 错误码按领域前缀组织，例如 `TENANT_DISABLED`、`PROVIDER_UNAVAILABLE`、`AUTH_PERMISSION_DENIED`。
- 异常 message 必须使用中文，不能把英文框架异常原文直接透出给 API 调用方。

## 注释规范

代码注释必须使用中文。

必须写注释的情况：

- 聚合不变量。
- 事务边界和幂等策略。
- 外部供应商差异。
- 安全、权限、审计和数据脱敏原因。
- 不容易从代码直接看出的设计取舍。

禁止写注释的情况：

- 重复方法名含义。
- 给简单 getter / setter / mapper 字段搬运写注释。
- 用注释掩盖不清晰命名。

## Spring AI 落点

### 包和类命名

```text
org.fujay.ai.platform.model/
  application/port/
    ChatCompletionPort.java
    EmbeddingPort.java
    ModelInvocationRecorder.java
  infrastructure/ai/
    SpringAiChatAdapter.java
    SpringAiEmbeddingAdapter.java
    PlatformAiProviderBridge.java

org.fujay.ai.platform.ai/
  chat/
    SpringAiProviderClientFactory.java
  embedding/
  vectorstore/
  advisor/
  tool/
  observation/
    SpringAiObservationCustomizer.java

org.fujay.ai.platform.knowledge/
  application/port/
    VectorIndexPort.java
    RetrievalPort.java
  infrastructure/ai/
    PgVectorStoreAdapter.java
    SpringAiRetrievalAdvisorFactory.java

org.fujay.ai.platform.tool/
  application/port/
    ToolExecutionPort.java
  infrastructure/ai/
    PlatformToolCallbackFactory.java
```

### 开发规则

- `model` context 定义模型调用 port，`infrastructure/ai` 实现 Spring AI adapter。
- `ai-platform-ai` 提供 Spring AI 基础设施能力，业务 context 的 `infrastructure/ai` 只做业务 port 到基础设施 facade 的适配。
- `ChatClient`、`ChatModel`、`EmbeddingModel`、`VectorStore`、Advisor、`ToolCallback` 不得出现在 `interfaces`、`application`、`domain`。
- `SpringAiProviderClientFactory` 根据租户 provider config 和 secret ref 构建 provider client；provider API key 不进入 `application.yml`。
- Chat streaming 由 adapter 转换为平台事件模型，application 不直接暴露 Spring Reactor 类型，除非接口合同明确支持 SSE。
- Provider capability 差异在 adapter 层归一，例如 tool calling、多模态、structured output、streaming、embedding dimension。
- RAG metadata filter 必须包含 tenant、knowledge base、document ACL 和 source 状态。
- Tool Runtime 只向 Model Gateway 提供已授权的 tool names / `ToolCallback`，高风险写工具默认不交给 Spring AI 自动直接执行。
- Spring AI prompt、completion、tool 参数和结果的明文观测默认关闭；平台 trace 只记录脱敏摘要、token、latency、provider、model、tool names、错误码。

### 配置基线

`ai-platform-boot` 的配置类建议按能力拆分：

```text
org.fujay.ai.platform.boot.config.ai/
  SpringAiModelConfiguration.java
  SpringAiVectorStoreConfiguration.java
  SpringAiObservationConfiguration.java
```

`application.yml` 只允许出现非密钥项，例如：

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1536
        initialize-schema: false
        schema-validation: true
```

禁止在配置文件中出现：

- `spring.ai.openai.api-key` 的生产明文值。
- 任意租户 provider secret。
- tool credential。
- 数据库知识源 credential。

### 测试要求

- `SpringAiChatAdapterTest`：provider 不可用时归一为 `PROVIDER_UNAVAILABLE`。
- `SpringAiEmbeddingAdapterTest`：embedding dimension 与 pgvector 配置不匹配时失败。
- `SpringAiRetrievalAdvisorFactoryTest`：metadata filter 必须包含 tenant 和 knowledge base。
- `PlatformToolCallbackFactoryTest`：未授权 tool 不会出现在 tool callback 集合。
- `SpringAiObservationTest`：prompt / completion 明文默认不进入 observation。

## Sa-Token 落点

### 包和类命名

```text
org.fujay.ai.platform.tenant/
  application/service/
    AuthApplicationService.java
    PermissionApplicationService.java
  application/port/
    CurrentPrincipalPort.java
    PermissionQueryPort.java
  domain/valueobject/
    AuthPrincipal.java
    ActorType.java
    LoginId.java
  infrastructure/satoken/
    TenantAuthAdapter.java

org.fujay.ai.platform.security/
  satoken/
    SaTokenAuthAdapter.java
    SaTokenPrincipalCodec.java
    SaTokenPermissionBridge.java
    SaTokenRouteConfiguration.java
```

### 开发规则

- `tenant` 或 `iam` context 定义认证应用服务、当前主体端口和权限查询端口。
- `ai-platform-security` 封装 Sa-Token 技术能力，`tenant` / IAM 的 `infrastructure/satoken` 只适配业务端口。
- `StpUtil` 只能出现在 `ai-platform-security` 或 auth adapter 的极薄适配处；controller 不直接调用。
- Redis-backed session 是必需项，`deploy` Redis 是本地验收事实源。
- `LoginId` 编解码只包含 `tenantId`、`actorType`、`actorId`，不得包含角色、权限、租户名称、密钥或个人敏感信息。
- 权限和角色从 IAM 事实查询，经 `SaTokenPermissionBridge` / `StpInterface` 供 Sa-Token 校验。
- `/api/**` 默认要求登录；登录、健康检查、静态资源、公开回调必须在 `SaTokenRouteConfiguration` 中显式白名单。
- Sa-Token 异常统一转换为 `AUTH_NOT_LOGIN`、`AUTH_TOKEN_EXPIRED`、`PERMISSION_DENIED` 等稳定错误码。

### 配置基线

```yaml
sa-token:
  token-name: satoken
  timeout: 86400
  active-timeout: 7200
  is-concurrent: true
  is-share: false
  token-style: uuid
  is-log: false

spring:
  data:
    redis:
      host: ${AI_PLATFORM_REDIS_HOST:localhost}
      port: ${AI_PLATFORM_REDIS_PORT:6379}
      password: ${AI_PLATFORM_REDIS_PASSWORD:}
```

说明：

- token 有效期和活跃期需要在 S3 auth 切片中结合企业安全策略确认。
- 开发默认值可以放 `.env.example`，生产值必须由部署环境注入。
- 如果未来启用独立 Redis，必须在 `deploy` 和架构文档中明确权限缓存与业务缓存隔离策略。

### 测试要求

- `AuthApplicationServiceTest`：账号验证成功后生成 token，失败返回统一错误码。
- `SaTokenPrincipalCodecTest`：login id 可逆解析，且不包含角色权限。
- `SaTokenPermissionBridgeTest`：权限码从 IAM 查询，角色/权限变更后立即生效。
- `ProtectedApiTest`：未登录访问 `/api/**` 返回统一 `AUTH_NOT_LOGIN`。
- `RedisSessionIntegrationTest`：Redis session 删除后 token 不再可访问受保护 API。

## MyBatis-Plus 落点

### 包和类命名

```text
org.fujay.ai.platform.<context>/
  domain/repository/
    ProviderRepository.java
  infrastructure/persistence/
    mapper/
      ProviderConfigMapper.java
    po/
      ProviderConfigPO.java
    repository/
      MybatisPlusProviderRepository.java
    query/
      ProviderQueryRepository.java

org.fujay.ai.platform.boot.config.persistence/
  MybatisPlusConfiguration.java
  TenantLineConfiguration.java
  AuditMetaObjectHandler.java

org.fujay.ai.platform.persistence/
  mybatis/
  tenant/
  audit/
  pagination/
  typehandler/
  sqlguard/
```

### 开发规则

- Mapper 和 PO 只在 `infrastructure/persistence`。
- `ai-platform-persistence` 提供 MyBatis-Plus 全局配置、插件、审计填充和 type handler；业务 Mapper / PO / XML 仍归各 context。
- 所有持久化表主键统一为 `id BIGINT PRIMARY KEY`，PO 字段统一为 `private Long id`；禁止 `tenant_id`、`user_id`、`provider_id`、`model_id` 或 `audit_id` 作为主键字段名。
- `tenant_id`、`user_id`、`provider_id`、`model_id` 是 Long 型关联键；`tenant_code`、`username`、`provider_name`、`model_code` 只能作为业务唯一键或展示/配置键。
- Repository port 在 `domain/repository`。
- Repository implementation 做 PO 与 aggregate 映射，不把 PO 传回 domain/application。
- Query side 可以使用 MyBatis-Plus 做列表、分页和报表，但只能返回平台 DTO。
- MyBatis-Plus `Wrapper`、`IPage`、`BaseMapper` 不得出现在 `domain`。
- 多租户查询必须带 tenant 条件；跨租户查询必须是平台管理员显式用例，并有审计。
- 业务表默认包含 Long 型 `tenant_id`、`created_at`、`created_by`、`updated_at`、`updated_by`、`deleted`、`version`。

### 配置基线

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

`MybatisPlusConfiguration` 必须集中注册：

1. `TenantLineInnerInterceptor`
2. `PaginationInnerInterceptor(DbType.POSTGRE_SQL)`
3. `OptimisticLockerInnerInterceptor`
4. `IllegalSQLInnerInterceptor` 或等价 SQL 安全检查
5. `BlockAttackInnerInterceptor`

规则：

- 插件顺序不得由业务模块调整。
- `TenantLineHandler.ignoreTable(...)` 只允许忽略平台全局字典、migration 元数据、框架内部表；每个 ignore table 必须有注释说明。
- 分页插件需要 `mybatis-plus-jsqlparser`，版本由 BOM 管理。
- 并发敏感聚合必须使用 `@Version`。
- `AuditMetaObjectHandler` 统一填充租户和审计字段。

### 测试要求

- `MybatisPlusConfigurationTest`：插件按顺序注册。
- `TenantLineIntegrationTest`：普通查询自动追加 tenant 条件。
- `BlockAttackIntegrationTest`：无条件 update/delete 被阻断。
- `OptimisticLockIntegrationTest`：旧版本更新失败。
- `RepositoryMappingTest`：PO 不泄漏到 domain/application response。

## Redisson + Caffeine 二级缓存落点

### 包和类命名

```text
org.fujay.ai.platform.cache/
  api/
    TwoLevelCacheFacade.java
    CacheLoader.java
    CacheInvalidationPublisher.java
  model/
    CacheName.java
    CacheKey.java
    CacheSpec.java
    CacheValueEnvelope.java
    CacheInvalidationEvent.java
    CacheFailurePolicy.java
  caffeine/
    CaffeineL1Cache.java
    CaffeineCacheRegistry.java
  redisson/
    RedissonL2Cache.java
    RedissonInvalidationTopic.java
    RedissonCacheLock.java
  support/
    CacheKeyBuilder.java
    CacheValueSerializer.java
    CacheMetricsRecorder.java
    CacheSpecRegistry.java

org.fujay.ai.platform.boot.config.cache/
  TwoLevelCacheConfiguration.java
  RedissonConfiguration.java
  CaffeineConfiguration.java
  CacheSpecConfiguration.java
```

### 开发规则

- 业务模块只能通过 `TwoLevelCacheFacade` 或更窄的 cache port 使用缓存。
- 禁止业务模块直接 `Caffeine.newBuilder()`、直接注入 `RedissonClient.getMap*()` 或把 Redis map 当 repository。
- Caffeine 是 L1 进程内缓存，必须配置 `maximumSize` 或 `maximumWeight`，不得无界。
- Redisson/Redis 是 L2 分布式缓存，必须配置 TTL；不允许永久缓存业务数据。
- L1 TTL 必须短于 L2 TTL，默认 L1 TTL 不超过 L2 TTL 的 20%。
- 写操作必须通过 `@TransactionalEventListener(phase = AFTER_COMMIT)` 或等价 after-commit hook 发布失效。
- 跨节点 L1 失效必须通过 Redisson topic；仅删除本机 Caffeine 不算完成。
- cache key 必须由 `CacheKeyBuilder` 生成，包含 env、app、cacheName、tenantId、schemaVersion、businessKeyHash。
- 不缓存明文 provider secret、tool credential、数据库 credential、Sa-Token token/session、audit log、conversation message、tool_run。
- Redis 不可用时，权限、租户状态、限流、工具策略 fail closed；只读低风险字典允许按 spec 返回有限 stale。

### 默认 CacheSpec

| Cache | L1 | L2 | Failure policy |
| --- | --- | --- | --- |
| `tenant-policy` | 30s / 10k | 5m | fail closed |
| `iam-permission` | 15s / 20k | 2m | fail closed |
| `provider-config` | 30s / 10k | 5m | fail closed for invocation |
| `model-catalog` | 5m / 20k | 30m | allow stale 10m |
| `knowledge-metadata` | 1m / 20k | 10m | fail closed for private KB |
| `retrieval-strategy` | 1m / 10k | 10m | fail closed |
| `tool-schema` | 1m / 10k | 10m | fail closed for execution |
| `rate-limit-policy` | 15s / 10k | 2m | fail closed |

### 配置基线

```yaml
spring:
  cache:
    type: none

ai-platform:
  cache:
    namespace: ${AI_PLATFORM_CACHE_NAMESPACE:local}
    l1:
      provider: caffeine
      default-maximum-size: 10000
    l2:
      provider: redisson
      key-prefix: ai-platform
    invalidation:
      topic: ai-platform:cache:invalidation
```

说明：

- `spring.cache.type=none` 表示不使用 Spring Boot 自动挑选的单层 CacheManager；项目提供自定义二级缓存入口。
- 如果后续需要兼容 `@Cacheable`，只能由 `ai-platform-cache` 提供 `TwoLevelCacheManager`，并复用同一套 `CacheSpecRegistry`。
- Redisson 连接复用 `spring.data.redis.*` 和 `deploy` Redis。

### 测试要求

- `TwoLevelCacheFacadeTest`：L1 hit 不访问 L2 和 loader。
- `TwoLevelCacheFacadeTest`：L2 hit 回填 L1。
- `TwoLevelCacheFacadeTest`：L2 miss 调 loader，并写入 L2 和 L1。
- `CacheKeyBuilderTest`：key 必须包含 tenant namespace 和 schema version，不包含敏感明文。
- `CacheInvalidationIntegrationTest`：事务提交后删除 L2 并通过 topic 失效另一个实例 L1。
- `CacheFailurePolicyTest`：权限/租户/限流类缓存 Redis 不可用时 fail closed。
- `CacheMetricsTest`：L1/L2 hit miss、loader latency、invalidation lag 有指标。

## TDD 模块开发步骤

开发某个模块时按以下顺序：

1. 读取 `docs/_generated/context-pack.json`、当前切片和本文件；涉及 Spring AI / Sa-Token / MyBatis-Plus 配置或升级时，同时读取 `docs/facts/framework-official-research.md`；涉及缓存时读取 `docs/facts/cache-official-research.md`。
2. 明确 bounded context、aggregate、不变量和事务边界。
3. 写第一个失败测试，优先 domain test 或 application test。
4. 实现最小 domain 行为。
5. 写 application test，覆盖权限、事务和事件。
6. 实现 application service 和 repository port。
7. 写 infrastructure test，覆盖 MyBatis-Plus mapper/repository 或 Spring AI/Sa-Token adapter。
8. 写 interface test，覆盖统一响应、统一异常和参数校验。
9. 运行架构边界测试，确认 domain 没有依赖基础设施。
10. 更新 `docs/60-implementation-notes.md` 和 gate 证据。

## 示例：开发 Provider 模块

最小实现顺序：

1. Domain test：`ProviderConfig` 启用前必须存在有效 credential ref 和至少一个 model。
2. Domain 实现：`ProviderConfig`、`ProviderCredentialRef`、`ModelDefinition`。
3. Application test：创建 provider 时写审计、校验租户权限、发布 `ProviderCredentialUpdated`。
4. Repository port：`ProviderRepository`。
5. MyBatis-Plus integration test：保存并读取 `ProviderConfig`，PO 不外泄。
6. Spring AI contract test：连接测试失败时返回 `PROVIDER_UNAVAILABLE`。
7. Controller test：`POST /api/model-providers` 返回统一 `ApiResponse<ProviderResponse>`。
8. Trace test：provider test call 生成 invocation trace。

完成后不得残留：

- 明文 provider secret。
- controller 直接调用 Spring AI。
- domain 依赖 MyBatis-Plus PO。
- 未经 Sa-Token/IAM 校验的 provider 管理接口。

## 模块完成标准

单个后端模块完成必须满足：

- 相关 domain/application/infrastructure/interface 测试通过。
- MyBatis-Plus migration 和 mapper 真实执行通过。
- Sa-Token 权限路径真实生效。
- Spring AI adapter 使用真实或可配置 provider endpoint 验证，不以 fake provider 作为完成态。
- 涉及缓存的模块必须验证 L1/L2 命中、事务后失效、跨节点 L1 失效和故障策略。
- 前后端同时启动后，页面正式操作无报错，功能在真实依赖上正常完成。
