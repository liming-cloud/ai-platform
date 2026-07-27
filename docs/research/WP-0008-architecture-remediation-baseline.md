# WP-0008 后端架构整改研究基线

## 研究问题

确认 M1 当前实现是否满足以下不可妥协约束：

1. Java 源码不使用 MyBatis SQL 注解。
2. 持久化标识使用雪花算法，不使用 UUID。
3. 包结构按业务能力和聚合组织，不横向平铺。
4. 领域层承载聚合、不变量、策略和领域事件。
5. `platform-security` 真实承载 Sa-Token 平台适配。
6. `platform-persistence` 真实承载 MyBatis-Plus 平台机制。
7. `adapter`、平台机制、扩展和装配边界没有含义重叠。
8. 运行模块只保留启动类和启动资源。

停止条件是取得当前源码统计、既有设计约束和 MyBatis、MyBatis-Plus、Sa-Token
官方资料，并能够形成可验证的整改决策。

## 资料范围

- 当前分支 `codex/trusted-rag-foundation-v1` 的受控源码和设计文档。
- MyBatis 官方文档。
- MyBatis-Plus 官方文档。
- Sa-Token 1.45.0 官方文档。
- 不使用博客、聚合转载或非官方示例作为规范依据。

## 当前源码事实

| 问题 | 当前事实 | 结论 |
| --- | --- | --- |
| SQL 位置 | 8 个 Mapper Java 文件包含 96 个 `@Select`、`@Insert`、`@Update` 等 SQL 注解 | 不符合新约束 |
| 标识类型 | 122 个主代码文件引用 UUID，存在 13 个运行时随机 UUID 生成点；数据库业务标识广泛使用 `UUID` 列 | 不符合新约束 |
| 包平铺 | 单包达到 24 个出站端口、22 个接口模型、18 个持久化类、17 个目录模型、16 个应用类 | 需要按能力和聚合拆分 |
| 领域层 | `organization-access` 与 `evaluation-operations` 没有领域包，其余上下文的应用和接口模型数量明显高于领域模型 | 领域所有权不足 |
| 平台安全 | `platform-security` 只包含密钥和出站策略；Sa-Token 网关、解析器和装配分散在上下文及两个运行模块 | 与目录设计不一致 |
| 平台持久化 | Maven 平台聚合仅包含 `platform-kernel`、`platform-security`，不存在 `platform-persistence` | 与目录设计不一致 |
| 适配器边界 | 当前没有 `infrastructure` 目录，但文档没有明确其与适配器、平台机制和装配的互斥规则 | 容易再次产生歧义 |
| 运行模块 | 三个运行模块共有 50 个主代码文件；除 3 个启动类外，还包含控制器、请求模型、异常处理、调度器和装配配置 | 不符合“纯启动包”要求 |

## 官方资料结论

### MyBatis

MyBatis 官方把 `resultMap`、可复用 `sql` 片段以及
`select`、`insert`、`update`、`delete` 映射定义为 Mapper XML 的一等元素。
复杂映射可以通过 XML 与 Mapper 接口方法绑定：

- [MyBatis Mapper XML 官方文档](https://mybatis.org/mybatis-3/sqlmap-xml.html)

本项目据此规定：Java Mapper 只保留类型安全的方法签名和参数声明；通用单表操作使用
MyBatis-Plus Mapper 能力；确需定制的 SQL、结果映射和批量语句全部进入 XML。

### MyBatis-Plus

MyBatis-Plus 官方说明：

- 多模块工程的 Mapper XML 应使用 `classpath*:` 扫描。
- 自定义 Mapper 方法需要配置 XML 位置。
- `ASSIGN_ID` 默认通过 `IdentifierGenerator.nextId` 生成雪花标识。
- `IdentifierGenerator` 可通过 Spring Bean 统一注入。

资料：

- [MyBatis-Plus 配置官方文档](https://baomidou.com/reference/)
- [MyBatis-Plus 自定义标识生成器官方文档](https://baomidou.com/guides/id-generator/)

本项目据此采用 64 位雪花标识、数据库 `BIGINT` 和统一标识生成器。由于浏览器不能安全
表示全部 64 位整数，HTTP 契约使用十进制字符串传输标识。

### Sa-Token

Sa-Token 1.45.0 官方将登录认证、权限认证、分布式会话、Redis 持久化、多账号体系和
自定义持久化策略作为同一安全框架能力：

- [Sa-Token 1.45.0 官方文档](https://sa-token.cc/doc.html)

因此 Sa-Token 不能继续散落在运行时装配中。框架类型必须集中在
`platform-security-satoken`，其他上下文只依赖框架无关的可信身份和会话端口。

## 设计两次

### 方案一：每个层独立 Maven 模块

每个上下文拆成领域、应用、入站适配器、出站适配器四到六个 Maven 模块。

优点：

- 编译期依赖隔离最强。
- 框架依赖无法进入领域模块。

缺点：

- 六个上下文会迅速增加二十多个模块。
- 一个纵向用例会修改多个 POM，六人团队维护成本过高。
- 当前主要问题是职责放错位置，而不是缺少 Maven 模块数量。

### 方案二：上下文单模块、平台与装配独立模块

上下文继续保持一个 Maven 模块，但内部按聚合和用例严格分包，并用 ArchUnit 保证
依赖方向。跨上下文技术机制进入平台模块，厂商能力进入扩展模块，运行组合进入
独立 `bootstrap` 模块，运行模块只保留启动类。

优点：

- 纵向功能修改保持局部。
- Sa-Token、MyBatis-Plus 和运行装配具有独立变化边界。
- 模块数量与六人团队规模匹配。
- 可以通过架构测试阻止领域层依赖框架和大包平铺。

缺点：

- 上下文内部隔离依赖自动门禁，而不是完全依赖 Maven。

## 研究结论

选择方案二。整改必须同时完成源码迁移和自动门禁，不能只修改目录文档。

本研究没有证明生产环境雪花节点编号的最终分配策略。M1 使用显式、可测试的单节点
配置；进入多副本部署前必须由运维分配并验证节点编号冲突、时钟回拨和容量边界。

