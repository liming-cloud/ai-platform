# ADR-0022：经典领域驱动分层、自动装配与分页契约

## 状态和日期

- 状态：已接受
- 日期：2026-07-27
- 决策负责人：产品负责人、后端负责人

## 背景和难以逆转的约束

WP-0008 已消除注解 SQL、UUID、Sa-Token 越界和运行模块承载业务代码等第一阶段
架构问题，但进一步复核发现模块语言和工程所有权仍不清晰：

- 上下文 `api` 同时承载应用用例、跨上下文门面、扩展端口、传输模型和领域枚举。
- `adapter/in` 与 `adapter/out` 不能清晰表达团队采用的经典领域驱动分层。
- MyBatis-Plus 分页插件已经存在，列表查询仍使用游标解析、`limit + 1`、手工截断
  和 XML `LIMIT`。
- 异常处理器直接识别具体异常和临时字符串编码，错误契约无法统一治理。
- 上下文、平台和扩展模块没有自行提供能力装配，业务 Bean 仍集中在 bootstrap。
- bootstrap 与 runtimes 是两层相邻启动壳，增加依赖和部署理解成本。

项目是绿地建设，不兼容旧分页、包名、模块路径、数据库或页面契约，因此应直接建立
目标结构，不保留转发层。

## 决策内容

### 1. 经典四层和依赖方向

每个限界上下文采用：

```text
interfaces -> application -> domain
infrastructure -> application/domain
```

- `interfaces` 保存控制器、消息消费者、调度入口和协议传输对象。
- `application` 保存按能力组织的命令、查询、结果、具体应用服务、跨上下文门面和
  外部技术端口。
- `domain` 保存按聚合组织的模型、值对象、仓储契约、策略、领域服务、事件和异常。
- `infrastructure` 保存持久化、外部客户端、缓存、消息和所属上下文的自动配置。
- 领域层只依赖 JDK 和框架无关的 `shared-kernel`。
- 控制器默认依赖具体应用服务；只有跨上下文门面、多实现能力和外部技术端口保留接口。
- 跨上下文只公开 `application/.../facade` 和明确的扩展端口。

删除上下文中的 `api`、`adapter`，不保留兼容包、转发类或类型别名。

### 2. 共享内核与公共契约

新增：

```text
backend/shared/
├── shared-kernel/
└── shared-contract/
```

`shared-kernel` 只保存领域标记、通用标识、租户值对象和框架无关异常基础类型；
`shared-contract` 只保存分页和统一接口错误响应等公共传输契约。

领域标记使用泛型接口：

```java
DomainObject
ValueObject extends DomainObject
Entity<ID extends ValueObject> extends DomainObject
AggregateRoot<ID extends ValueObject> extends Entity<ID>
```

这些接口只用于编译期类型约束、架构测试和领域语义，不参与反射创建、自动复制或
持久化映射。

### 3. 统一异常和错误编码

共享内核定义 `ErrorCode`、`ErrorType`、`FoundationException`、
`DomainException`、`ApplicationException` 和 `InfrastructureException`。

错误码格式固定为：

```text
上下文.类别.四位序号
```

类别只允许 `REQUEST`、`AUTHENTICATION`、`AUTHORIZATION`、`RESOURCE`、
`CONFLICT`、`QUOTA`、`DEPENDENCY_UNAVAILABLE`、`DEPENDENCY_RESPONSE`
和 `SYSTEM`。每个错误码声明安全默认消息、是否可重试和允许返回的详情字段。

`platform-web` 只按 `ErrorType` 映射 HTTP 状态。未识别异常返回
`COMMON.SYSTEM.0001`，原始异常只写服务端日志。基础设施必须先转译数据库、框架、
厂商和网络异常。

### 4. 页码分页与 MyBatis-Plus

外部列表统一使用 `pageNumber`、`pageSize` 和总数：

- 页码从 1 开始，默认每页 20 条，单页最多 100 条。
- 默认最大偏移量为 100000，平台配置只能收窄。
- 排序由接口固定，不接受任意字段名或 SQL 片段。
- `Page` 和 `IPage` 只存在于 infrastructure。
- 简单单表分页使用 `BaseMapper.selectPage` 和 `LambdaQueryWrapper`。
- 复杂查询的 Mapper 方法以 `IPage<?>` 为第一个参数，由分页插件生成分页和总数 SQL。
- `PaginationInnerInterceptor` 位于拦截器链最后，固定 PostgreSQL 方言、
  `overflow=false` 和 `maxLimit=100`。

查询事件序号、向量检索候选数量、任务领取和内部批次不属于页码分页。

### 5. Wrapper 与 XML

单表主键、租户、状态、名称、存在性、计数、固定排序、普通写入和单表分页使用
MyBatis-Plus 与 Lambda Wrapper。Wrapper 只在 infrastructure 创建。

Mapper XML 只允许多表连接、聚合、锁、幂等写入、原子配额、公共表表达式、窗口函数、
PostgreSQL 或 pgvector 专属操作、批量写入、发件箱和需要 `RETURNING` 的状态迁移。
复杂 SQL 必须进入允许清单。领域标识由应用层雪花生成器生成，持久化层不得改写。

### 6. Spring Boot 自动装配

每个上下文、平台和扩展模块按实际能力提供少量 `@AutoConfiguration`，并通过：

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

注册。自动配置禁止宽泛组件扫描；使用条件注解、类型安全属性和显式 `@Import`。
可替换技术端口使用 `@ConditionalOnMissingBean`，必要依赖缺失时启动失败。
自动配置顺序必须显式声明。

bootstrap 不集中声明上下文应用服务、仓储、控制器和扩展工厂。

### 7. Bootstrap 与 runtimes 合并

保留三个 bootstrap 模块，并使其成为三个独立可执行 Spring Boot 应用：

- `control-plane-bootstrap`
- `query-bootstrap`
- `ingestion-bootstrap`

每个模块主代码只保留唯一启动类；应用资源和应用级集成测试归该模块。删除整个
`backend/runtimes`。Docker 服务名、镜像名、端口和进程隔离保持不变。

三种角色分别由 `foundation.control-plane.enabled`、`foundation.query.enabled`
和 `foundation.ingestion.enabled` 启用，启动校验保证一次只启用一种角色。

### 8. 目录说明

每个主要目录和业务子目录必须提供简体中文 `package-info.java`，说明本包职责、
允许依赖、禁止依赖、公开边界以及事务或安全边界。

## 替代关系

本决策替代 ADR-0021 的以下内容：

- “上下文内部结构”中保留 `api` 和 `adapter` 的决定。
- “适配器与基础设施”中禁止 `infrastructure` 的决定。
- “启动与装配”中保留独立 runtime 启动壳的决定。
- “SQL 与 MyBatis-Plus”中把普通列表游标分页视为复杂 SQL 的决定。

ADR-0021 关于禁止注解 SQL、雪花标识、领域强度、平台安全和平台持久化的决定继续有效。

## 主要备选方案及拒绝原因

### 保留 api 作为所有公开类型目录

公开类型的调用者和变化原因并不相同。把用例、门面、扩展端口和传输对象放在同一层
会扩大跨上下文可见面，因此不采用。

### 保留 adapter 并用 in/out 区分方向

该表达可以实现端口适配器架构，但与团队采用的经典领域驱动分层语言不一致，且现有
代码已把大量持久化细节平铺在 adapter/out。继续保留会掩盖所有权，因此不采用。

### 同时保留 bootstrap 和 runtimes

两者没有独立变化压力；额外启动壳只增加 POM、测试和镜像构建路径，因此不采用。

### 继续使用游标分页

当前后台管理列表需要总数、页码跳转和稳定固定排序，页码分页更符合产品任务。
事件续传和检索候选仍保留各自协议，不把所有限制参数误归为分页。

## 后果和后续证据

- 激活 WP-0009，WP-0007 继续受阻，M1 技术验收再次撤回。
- 包移动会造成大范围编译变化，必须先试点一个上下文并冻结模板。
- 旧 `api`、`adapter`、`runtimes`、游标列表和手工分页使用递减门禁控制。
- 每个自动配置必须使用 `ApplicationContextRunner` 验证条件、覆盖和关闭场景。
- 每个列表必须验证总数、页边界、固定排序和租户隔离，并观测真实分页 SQL。
- 全量测试和六维代码走查完成前不得关闭 WP-0009。
