# ADR-0021：后端架构质量重置

## 状态和日期

- 状态：已接受
- 日期：2026-07-27
- 决策负责人：产品负责人、后端负责人

## 背景和难以逆转的约束

M1 已形成可运行的纵向闭环，但人工架构复核发现实现偏离已批准目录设计：

- Java Mapper 内存在大量注解 SQL。
- 领域和数据库标识广泛使用 UUID。
- 类在接口模型、应用服务、出站端口和持久化包中平铺。
- 部分上下文没有领域模型，业务规则主要停留在应用服务。
- `platform-security` 没有承载 Sa-Token 适配。
- `platform-persistence` 没有实现。
- 运行模块承载控制器、请求模型、调度和依赖装配。

这些问题会固化错误依赖方向，新增厂商、连接器、权限方式或运行进程时需要继续复制
装配代码。当前系统为绿地建设，不承担旧 API、旧数据库或演示数据迁移义务，可以直接
重建标识和数据库结构。

## 决策内容

### 1. SQL 与 MyBatis-Plus

1. Java 源码禁止使用 `@Select`、`@Insert`、`@Update`、`@Delete` 及对应
   Provider 注解编写 SQL。
2. 通用单表操作优先使用 MyBatis-Plus `BaseMapper`、实体映射和类型安全 Wrapper。
3. 聚合装载、批量写入、锁、配额、游标和复杂投影等定制 SQL 放在
   `src/main/resources/mapper/<context>/` 的 Mapper XML。
4. Mapper Java 接口只声明方法、参数和返回类型，不承载 SQL 文本。
5. CI 扫描禁止 SQL 注解，并验证每个定制 Mapper 方法存在唯一 XML 映射。

### 2. 雪花标识

1. 租户、成员、知识空间、数据源、索引、模型部署、助手、查询、事件和租约等平台
   标识统一使用 64 位雪花标识。
2. Java 领域值对象内部使用 `long`，数据库使用 `BIGINT`。
3. HTTP、事件和前端契约使用十进制字符串，禁止把 64 位标识作为 JavaScript 数字。
4. `platform-persistence` 统一提供基于 MyBatis-Plus `IdentifierGenerator` 的
   标识生成实现；应用服务只依赖框架无关的标识生成端口。
5. 主代码禁止 `java.util.UUID`。追踪标识使用带前缀的雪花标识字符串。
6. M1 直接重建数据库和演示数据，不编写 UUID 到雪花标识的迁移。

### 3. 上下文内部结构

1. 上下文继续保持单 Maven 模块。
2. `domain` 按聚合组织，包含模型、值对象、不变量、策略、领域服务、领域事件和
   聚合仓储契约。
3. `application` 按业务用例组织，分为命令、查询、服务、入站端口、技术出站端口和
   结果；不得成为业务规则容器。
4. `api` 按公开能力组织，不建立全上下文共享的巨大 `model` 包。
5. `adapter/in` 只做协议到用例的转换，`adapter/out` 只实现领域仓储或应用端口。
6. 单个生产包超过 12 个类必须拆分，除非 ADR 证明这些类属于一个不可分割的模型。

### 4. 领域强度

1. 聚合根维护状态迁移、版本和跨字段不变量，应用服务不得直接拼装非法状态。
2. 聚合仓储接口归领域层；对象存储、模型调用、会话、时钟外的技术端口归应用层。
3. `organization-access` 建立租户成员与授权领域模型。
4. `evaluation-operations` 建立评测运行、用例结果和质量门禁领域模型。
5. 每个领域规则必须有纯领域测试；只有编排和事务规则留在应用测试。

### 5. 平台安全

`platform-security` 调整为安全平台聚合模块：

```text
platform-security/
├── platform-security-core/
└── platform-security-satoken/
```

- `core` 保存密钥、出站策略、可信身份、会话端口和框架无关安全值对象。
- `satoken` 保存 Sa-Token 会话实现、Redis 集成、令牌解析和 Web 身份恢复适配。
- `organization-access` 负责成员、角色和权限业务规则，不依赖 Sa-Token 类型。
- 运行模块和其他上下文不得直接导入 `cn.dev33.satoken`。

### 6. 平台持久化

新增 `platform-persistence`，唯一负责：

- MyBatis-Plus 全局配置和拦截器顺序。
- 雪花标识生成器。
- PostgreSQL 通用类型处理。
- 租户条件纵深防御。
- 乐观锁、分页和危险全表操作阻断。
- Mapper XML 扫描与启动时校验。

平台持久化不得包含业务表实体、业务 Mapper 或跨上下文查询。

### 7. 适配器与基础设施

本项目不建立 `infrastructure` 层：

- `adapter` 是端口与具体协议或技术之间的转换器。
- 可被多个上下文复用的技术机制属于 `platform`。
- 厂商或产品实现属于 `extensions`。
- 依赖选择和组合根属于 `bootstrap`。
- `runtime` 只负责启动。

无法归入上述位置的类必须先拆分职责，禁止创建 `infrastructure`、`common`、
`utils` 或 `manager` 兜底目录。

### 8. 启动与装配

新增：

```text
backend/bootstrap/
├── control-plane-bootstrap/
├── query-bootstrap/
└── ingestion-bootstrap/
```

- 入站控制器和请求映射回到所属上下文 `adapter/in/web`。
- 跨上下文依赖组合、条件装配和类型安全属性进入对应 bootstrap。
- 运行模块主代码只允许一个 `*Application.java`，并可包含启动配置资源。
- 运行模块不得定义控制器、接口模型、仓储、调度器、业务服务或 Bean 装配。

## 主要备选方案及拒绝原因

### 每层独立 Maven 模块

编译隔离更强，但会把六个上下文扩张为二十多个模块，使纵向用例跨越大量 POM。
当前团队规模和变化压力不支持该成本，因此不采用。

### 保留当前目录，只增加 ArchUnit

门禁无法修复已经错误放置的类，也无法让运行模块、Sa-Token 和持久化形成真实所有权，
因此不采用。

### 保留 UUID，仅对新表使用雪花标识

会长期保留两套标识、类型转换和前端契约，违背绿地重建原则，因此不采用。

## 后果和后续证据

- 当前 M1 技术验收撤回，WP-0007 受阻。
- 激活 WP-0008 完成架构整改；完成后必须从空卷重新执行全部 M1 验收。
- 旧演示数据、备份和容量结果只能作为历史基线，不能作为整改后的验收结论。
- 必须新增 SQL 注解、UUID、包平铺、平台依赖和纯运行时门禁。
- 必须提供领域测试、Mapper XML 集成测试、全量容器演示和新的恢复与容量证据。

