# 可信 RAG 基座后端研发结构

## 1. 文档地位

状态：已接受。本文承接 ADR-0022 和 ADR-0082 的工程约束，目录事实经 GOV-0001 与 GOV-0003 按实际 POM 校正。出现冲突时，
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
│   └── platform-security/
│       ├── pom.xml
│       ├── platform-security-core/
│       └── platform-security-satoken/
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
└── bootstrap/
    ├── pom.xml
    ├── control-plane-bootstrap/
    ├── query-bootstrap/
    └── ingestion-bootstrap/
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
| context application | 本上下文 domain、shared-kernel、必要的 shared-contract；跨上下文仅公开 facade | interfaces、infrastructure、厂商实现、其他上下文内部包 |
| context interfaces | 本上下文 application、shared-contract、platform-web 契约 | Mapper、持久化对象、厂商实现 |
| context infrastructure | 本上下文 application/domain、platform、框架 | 其他上下文内部包 |
| extension | 所属上下文公开 `application/.../port/out` 与明确的 facade 扩展契约、platform、厂商框架 | Controller、上下文 domain、内部 application、infrastructure |
| bootstrap | 各模块自动配置和应用属性 | 业务 Bean 的手工实现与集中扫描 |

上下文之间存在 Maven 编译依赖本身不违规，但每条边必须有实际的公开 facade 使用且图无环。业务引用只能进入被依赖上下文的 `application/.../facade`；扩展只能进入所需的公开端口或专用 facade 契约。公开请求、结果和值类型不能泄露拥有者的领域类型。各上下文自行注册自动配置，调用方不导入他方 `infrastructure`；条件装配确需排序时使用类名字符串并由启动集成测试验证。`scripts/quality/backend_architecture_guard.py` 检查 POM 编译图与主代码 Java 导入；自动配置与公开契约还需后端测试证明行为。不能只以 Maven 无环或旧守卫通过宣称边界合规。

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

### 4.1 领域聚合操作规约

ADR-0031 为后续领域开发增加以下可执行约束：

- 聚合根是外部可操作和并发一致性的唯一入口；有独立标识、读取、持久化或生命周期的可变对象必须是聚合根。
- 值对象只表达不可变业务事实；由聚合状态派生且需持久化的质量、发布、修订等事实由聚合根或显式领域服务创建。
- 应用服务负责权限、幂等、I/O 与事务编排，不得直接创建或变更聚合成员，也不得把多个聚合的状态迁移散落在自身条件分支中。
- 跨聚合不变量使用命名领域服务和不可变操作结果表达；仓储只提交该结果，不在基础设施补写业务判断。
- 应用服务构造器不得依赖 `Entity`、`AggregateRoot` 或普通 `ValueObject`。可复用的无状态规则通过专用 `DomainPolicy` 契约注入。

`scripts/quality/backend_architecture_guard.py` 与领域测试共同验证以上分类和构造器边界；违反者不得以债务基线豁免。

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
    │   ├── role/
    │   │   ├── ApplicationRoleProperties.java
    │   │   ├── ApplicationRoleValidator.java
    │   │   └── ApplicationRoleAutoConfiguration.java
    │   ├── trace/
    │   └── package-info.java
    └── resources/META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

只处理通用 Web 机制和可执行应用角色校验，不识别具体上下文异常类。上下文错误码通过
`ErrorCode` 自描述。三个启动模块必须启用角色校验，且控制面、查询、摄取只能启用一个。

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

### 6.4 可观测性与事务发件箱

当前 Maven 反应堆没有独立 platform-observability、platform-outbox、test-support 或 architecture-tests 模块，不保留空聚合声明。观测、脱敏和发件箱遵循已有技术与上下文所有权；需要提取共享机制时先证明真实调用方和变化边界，不改变事件的领域含义。

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
- facade 是稳定跨上下文契约，包含被调用方实际承诺的输入、输出和值类型；内部 DTO 不能因 `public` 修饰符而成为跨上下文契约。
- `port/out` 保存模型、对象存储、缓存、时钟等外部技术能力端口；扩展所需的请求和结果必须使用公开契约，在上下文内部完成领域映射。
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

`make architecture-guard` 按 Mapper 独立语句及引用片段检测 `LIMIT/OFFSET/FETCH`，不依赖方法名或参数名。普通子查询的分页仍计入；关联 `JOIN LATERAL` 的固定单行投影、已登记的事件续传和任务领取不属于目录分页。现有七条普通列表债务按精确 `路径#statementId` 递减，不能用旧数量预算换入新语句；责任及退出条件见 [WP-0017 分页守卫证据](../tasks/WP-0017/evidence/WP-0017-pagination-guard-closure-20261002.md)。该统计覆盖上下文 Mapper XML，不证明 Java 内联 SQL 或全后端无债务。

## 12. 包说明规范

每个主要包的 `package-info.java` 至少回答：

1. 本包拥有什么业务或技术职责。
2. 允许依赖哪些层和模块。
3. 禁止依赖哪些层、框架或内部实现。
4. 哪些类型是公开边界。
5. 事务、安全、幂等或数据隔离边界在哪里。

不允许只写“保存某某类”的同义反复说明。

## 13. 变更顺序

当前已采用经典四层；不重启 WP-0009 历史迁移。后续按所属工作包一次处理一个有证据的边界，先代码图谱、最小失败测试，再局部实现和架构/入口回归。未触及债务只登记，不批量移动六个上下文。

## 14. 架构验收

- 旧 `api`、`adapter` 和 `backend/runtimes` 文件数为零。
- domain 的外部框架依赖为零。
- 错误码格式正确且全仓唯一。
- 普通列表游标契约禁止新增；手工列表分页与 Java 内联 SQL 存量按 scripts/quality/baselines 递减，触及路径清理后必须验证，不能把旧验收目标写成当前零债务事实。
- MyBatis-Plus 类型不越过 infrastructure。
- 跨上下文内部包引用为零。
- 跨上下文 Maven 依赖无环、无闲置边，所有边有公开 facade 使用；扩展不得导入上下文领域或基础设施。
- bootstrap 非启动 Java 文件为零。
- 自动配置注册缺失和宽泛组件扫描为零。
- 每个包规模不超过门禁，超限必须拆分或由 ADR 说明。
