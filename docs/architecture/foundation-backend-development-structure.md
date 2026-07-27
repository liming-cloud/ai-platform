# 可信 RAG 基座后端研发结构

## 1. 文档地位

本文是 ADR-0022 的可执行目录规范，约束 WP-0009 及后续所有后端工作。出现冲突时，
按“项目章程 -> 已接受 ADR -> 本文 -> 工作包”的顺序处理。

目录不是分类展示用途，而是所有权、依赖方向、公开边界和测试边界。不能确定所有者的
类不得先放入 `common`、`utils`、`manager` 或临时目录。

## 2. 后端总目录

```text
backend/
├── pom.xml
├── shared/
│   ├── pom.xml
│   ├── shared-kernel/
│   └── shared-contract/
├── platform/
│   ├── pom.xml
│   ├── platform-web/
│   ├── platform-persistence/
│   ├── platform-security/
│   │   ├── pom.xml
│   │   ├── platform-security-core/
│   │   └── platform-security-satoken/
│   ├── platform-observability/
│   └── platform-outbox/
├── contexts/
│   ├── pom.xml
│   ├── organization-access/
│   ├── knowledge-data/
│   ├── model-runtime/
│   ├── assistant-delivery/
│   ├── trusted-query/
│   └── evaluation-operations/
├── extensions/
│   ├── pom.xml
│   ├── model-providers/
│   ├── object-storage/
│   └── vector-stores/
├── bootstrap/
│   ├── pom.xml
│   ├── control-plane-bootstrap/
│   ├── query-bootstrap/
│   └── ingestion-bootstrap/
├── test-support/
└── architecture-tests/
```

禁止重新创建：

```text
backend/runtimes/
contexts/*/api/
contexts/*/adapter/
```

## 3. 模块依赖矩阵

| 调用方 | 允许依赖 | 禁止依赖 |
| --- | --- | --- |
| shared-kernel | JDK | Spring、数据库、shared-contract、任何业务上下文 |
| shared-contract | JDK | Spring、MyBatis-Plus、Sa-Token、任何业务上下文 |
| platform | shared 模块和明确第三方框架 | 业务上下文的领域类型和业务表 |
| context domain | JDK、shared-kernel | application、interfaces、infrastructure、shared-contract、框架 |
| context application | 本上下文 domain、shared-kernel、必要的 shared-contract | interfaces、infrastructure、厂商实现 |
| context interfaces | 本上下文 application、shared-contract、platform-web 契约 | Mapper、持久化对象、厂商实现 |
| context infrastructure | 本上下文 application/domain、platform、框架 | 其他上下文内部包 |
| extension | 公开扩展端口、platform、厂商框架 | Controller、其他上下文内部包 |
| bootstrap | 各模块自动配置和应用属性 | 业务 Bean 的手工实现与集中扫描 |

Maven 和 ArchUnit 必须同时验证该矩阵，不能只依赖代码评审。

## 4. shared-kernel

```text
shared/shared-kernel/
├── pom.xml
└── src/
    ├── main/java/com/aiplatform/foundation/shared/kernel/
    │   ├── package-info.java
    │   ├── domain/
    │   │   ├── package-info.java
    │   │   ├── DomainObject.java
    │   │   ├── ValueObject.java
    │   │   ├── Entity.java
    │   │   └── AggregateRoot.java
    │   ├── identifier/
    │   │   ├── package-info.java
    │   │   └── EntityIdGenerator.java
    │   ├── tenant/
    │   │   ├── package-info.java
    │   │   └── TenantId.java
    │   └── error/
    │       ├── package-info.java
    │       ├── ErrorCode.java
    │       ├── ErrorType.java
    │       ├── FoundationException.java
    │       ├── DomainException.java
    │       ├── ApplicationException.java
    │       └── InfrastructureException.java
    └── test/java/
```

约束：

- 标记接口没有框架注解和运行期副作用。
- 通用值对象必须具有明确、跨上下文一致的语义；名称相同但规则不同的对象留在上下文。
- 不保存分页、HTTP 响应、数据库实体、工具方法集合和业务枚举。
- 错误基础类型只能保存安全错误事实，不能保存 HTTP 状态。

## 5. shared-contract

```text
shared/shared-contract/
├── pom.xml
└── src/
    ├── main/java/com/aiplatform/foundation/shared/contract/
    │   ├── package-info.java
    │   ├── paging/
    │   │   ├── package-info.java
    │   │   ├── PageQuery.java
    │   │   └── PageResult.java
    │   └── error/
    │       ├── package-info.java
    │       └── ApiErrorResponse.java
    └── test/java/
```

`PageQuery` 负责页码、页大小和最大偏移校验；`PageResult` 负责总页数和前后页派生值。
它们不认识 MyBatis-Plus。应用层可以使用公共分页契约，但领域层不得依赖该模块。

## 6. 平台模块

### 6.1 platform-web

```text
platform/platform-web/
└── src/main/
    ├── java/.../platform/web/
    │   ├── autoconfigure/
    │   │   └── PlatformWebAutoConfiguration.java
    │   ├── error/
    │   │   ├── FoundationExceptionHandler.java
    │   │   └── HttpErrorTypeMapping.java
    │   ├── trace/
    │   └── package-info.java
    └── resources/META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

只处理通用 Web 机制，不识别具体上下文异常类。上下文错误码通过 `ErrorCode` 自描述。

### 6.2 platform-persistence

```text
platform/platform-persistence/
└── src/main/
    ├── java/.../persistence/
    │   ├── autoconfigure/
    │   ├── identifier/
    │   ├── interceptor/
    │   ├── mybatis/
    │   └── typehandler/
    └── resources/META-INF/spring/
```

负责 MyBatis-Plus 全局配置、拦截器顺序、雪花生成、通用类型处理和危险操作阻断。
不保存业务 Mapper、业务表实体和跨上下文查询。

### 6.3 platform-security

- `platform-security-core`：可信身份、会话端口、密钥、出站策略和框架无关安全类型。
- `platform-security-satoken`：Sa-Token、Redis 会话、令牌解析和 Web 身份恢复。
- Sa-Token 类型不得出现在其他模块。

### 6.4 platform-observability 与 platform-outbox

- observability 统一提供指标、追踪、日志脱敏和健康指标装配。
- outbox 提供事务发件箱技术机制，不拥有任何上下文事件含义。
- 未有真实调用方前可以只保留聚合模块声明，不创建空实现类。

## 7. 限界上下文标准模板

```text
<context>/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/.../<context>/
    │   │   ├── package-info.java
    │   │   ├── domain/
    │   │   │   ├── package-info.java
    │   │   │   └── <aggregate>/
    │   │   │       ├── package-info.java
    │   │   │       ├── model/
    │   │   │       ├── repository/
    │   │   │       ├── service/
    │   │   │       ├── policy/
    │   │   │       ├── event/
    │   │   │       └── exception/
    │   │   ├── application/
    │   │   │   ├── package-info.java
    │   │   │   └── <capability>/
    │   │   │       ├── package-info.java
    │   │   │       ├── command/
    │   │   │       ├── query/
    │   │   │       ├── dto/
    │   │   │       ├── service/
    │   │   │       ├── facade/
    │   │   │       └── port/out/
    │   │   ├── interfaces/
    │   │   │   ├── package-info.java
    │   │   │   ├── web/<capability>/
    │   │   │   │   ├── controller/
    │   │   │   │   ├── request/
    │   │   │   │   └── response/
    │   │   │   ├── message/<capability>/
    │   │   │   └── job/<capability>/
    │   │   └── infrastructure/
    │   │       ├── package-info.java
    │   │       ├── persistence/<aggregate>/
    │   │       │   ├── entity/
    │   │       │   ├── mapper/
    │   │       │   ├── repository/
    │   │       │   └── converter/
    │   │       ├── client/<capability>/
    │   │       ├── cache/<capability>/
    │   │       ├── messaging/<capability>/
    │   │       └── configuration/
    │   └── resources/
    │       ├── mapper/<context>/
    │       ├── db/migration/<context>/
    │       └── META-INF/spring/
    └── test/
        ├── java/.../<context>/domain/
        ├── java/.../<context>/application/
        ├── java/.../<context>/interfaces/
        ├── java/.../<context>/infrastructure/
        └── java/.../<context>/architecture/
```

只创建有实际类型的叶子目录，禁止为了匹配模板提交空目录或占位类。

## 8. 各层研发定义

### 8.1 domain

- 聚合根是事务一致性边界，自身维护状态迁移、版本和跨字段不变量。
- 实体具有聚合内身份；值对象按值相等且创建后不可变。
- 聚合仓储接口靠近聚合，参数和返回值只使用领域类型。
- 领域服务只承载无法自然归属单个实体的领域规则。
- 领域异常只表达业务拒绝，不包含 HTTP、SQL 或厂商错误。
- 每条重要不变量使用无 Spring 的纯单元测试。

### 8.2 application

- 按用户能力而不是技术类型组织。
- 命令改变状态，查询不产生业务副作用。
- 应用服务负责鉴权后的用例编排、事务边界、幂等和调用领域行为。
- 应用服务默认是具体类，不创建同名接口与 Default 实现。
- facade 是稳定跨上下文契约；dto 只暴露调用方需要的事实。
- `port/out` 只保存模型、对象存储、缓存、时钟等外部技术能力端口。
- 不创建 Mapper、Wrapper、HTTP 请求对象和厂商参数。

### 8.3 interfaces

- Controller 只做协议校验、可信上下文取得、请求转换和响应映射。
- request/response 与应用 DTO 分开，防止 HTTP 变化污染用例。
- message 和 job 是同等入口，必须调用应用服务而非仓储。
- 安全注解只表达入口保护，资源级授权仍由应用或领域规则完成。
- 每个入口测试成功、拒绝、非法输入和安全错误映射。

### 8.4 infrastructure

- 持久化实体只映射数据库，不能承担领域行为。
- converter 显式转换领域对象与持久化对象。
- 简单单表操作使用 BaseMapper 和 Lambda Wrapper。
- 复杂 SQL 使用 XML，并登记复杂性原因。
- 外部异常在此转译为 `InfrastructureException` 或上下文定义的安全异常。
- configuration 只装配本上下文拥有的能力，不能成为全局组合根。

## 9. 自动配置目录

每个有实际能力的模块按需提供：

```text
<Context>CoreAutoConfiguration
<Context>PersistenceAutoConfiguration
<Context>WebAutoConfiguration
<Context>JobAutoConfiguration
```

约束：

- 使用 `@AutoConfiguration` 和精确的 before/after。
- 使用 `@ConditionalOnClass`、`@ConditionalOnBean`、
  `@ConditionalOnMissingBean`、`@ConditionalOnProperty`。
- 使用 `@Import` 显式导入控制器、任务和配置类。
- 禁止 `@ComponentScan` 扫描整个上下文。
- 必需依赖缺失时失败，不创建空实现或无业务意义默认值。
- 每个配置使用 `ApplicationContextRunner` 验证启用、禁用、缺失和用户覆盖。

## 10. Bootstrap 应用

```text
bootstrap/<role>-bootstrap/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/.../<role>/<Role>Application.java
    │   └── resources/application.yml
    └── test/java/.../<role>/<Role>ApplicationTest.java
```

主代码除启动类外不得新增 Java 类型。启动类不声明业务 Bean，不集中导入上下文内部
实现。应用级配置只负责选择角色、端口、外部配置和必要运行参数。

## 11. 分页持久化结构

固定调用流：

```text
HTTP pageNumber/pageSize
-> PageQuery
-> 具体应用查询服务
-> 领域仓储分页请求或应用查询端口
-> infrastructure 创建 MyBatis-Plus Page
-> BaseMapper 或 XML IPage 查询
-> PageResult
-> HTTP response
```

禁止：

- Controller 传入 `Page`、`IPage` 或 Wrapper。
- 应用层构造 SQL 字段名。
- `limit + 1`、`subList`、Base64 游标和 nextCursor。
- 在 XML 列表查询中手写 LIMIT/OFFSET。

## 12. 包说明规范

每个主要包的 `package-info.java` 至少回答：

1. 本包拥有什么业务或技术职责。
2. 允许依赖哪些层和模块。
3. 禁止依赖哪些层、框架或内部实现。
4. 哪些类型是公开边界。
5. 事务、安全、幂等或数据隔离边界在哪里。

不允许只写“保存某某类”的同义反复说明。

## 13. 迁移顺序

1. shared-kernel、shared-contract、platform-web。
2. organization-access 试点和模板冻结。
3. knowledge-data。
4. model-runtime。
5. assistant-delivery。
6. trusted-query。
7. evaluation-operations。
8. 扩展自动配置。
9. bootstrap 收口和 runtimes 删除。
10. Docker、前端、全量验证和六维走查。

一个上下文未完成编译、架构、自动配置、持久化和入口回归前，不进入下一个上下文。

## 14. 架构验收

- 旧 `api`、`adapter` 和 `backend/runtimes` 文件数为零。
- domain 的外部框架依赖为零。
- 错误码格式正确且全仓唯一。
- 普通列表游标契约和手工列表分页为零。
- MyBatis-Plus 类型不越过 infrastructure。
- 跨上下文内部包引用为零。
- bootstrap 非启动 Java 文件为零。
- 自动配置注册缺失和宽泛组件扫描为零。
- 每个包规模不超过门禁，超限必须拆分或由 ADR 说明。
