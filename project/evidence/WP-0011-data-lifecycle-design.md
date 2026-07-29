# WP-0011 受治理数据生命周期设计证据

## 目标

为 M3 固定能同时支持文件、对象存储和结构化数据集的最小领域边界，避免将现有 UTF-8 文本发布实现误泛化为包含全部连接器、Schema 和质量行为的巨型数据源对象。

## 已核实的现状

- `TextDataSourceService` 已在可信租户上下文中提供创建、分页列表、UTF-8 上传、对象元数据核验和显式发布。
- `TextDataSource` 使用 `DRAFT`、`CONTENT_READY`、`PUBLISHED` 表达单一文本内容版本，不能表达探测、同步、质量、退役或来源类型。
- `IndexVersion` 已在 PostgreSQL 事务中冻结已发布来源快照，并在 M2 路径中冻结 Embedding 配置、目录条目和维度。
- 企业控制台现有数据入口只服务首份文本发布，尚未形成知识管理员的数据源、同步检查和质量处置工作台。

代码图谱探索：`TextDataSourceService`、`TextDataSource`、`TextDataSourceController`、`TextDataSourceRepository`、`IndexVersion`、`PostgresIndexVersionRepository`。

## 方案比较

| 方案 | 调用方与失败语义 | 结论 |
| --- | --- | --- |
| 扩展 `TextDataSource` | 每个调用方都要处理文件、对象存储、结构化 Schema、探测、同步和质量的无关可选字段；状态机持续膨胀 | 拒绝 |
| 受治理来源身份 + 不可变同步产物 + 类型化连接器 | 列表、授权、质量和索引消费统一来源产物；各连接器局部处理自身探测与同步失败 | 采用 |

## 固定设计

ADR-0028 定义 `GovernedDataSource`、`SourceRevision`、`SyncArtifact`、`QualityReport` 和 `SchemaVersion` 的所有权与不变量。首个纵向切片仅覆盖 `FILE` 来源的登记、探测、不可变同步产物与质量事实；不自动发布，不替换既有活动索引，不实现平台范围管理。

## 下一步

继续为 `FILE` 来源补齐登记、探测、不可变同步产物和质量事实的持久化与应用闭环，并以真实 PostgreSQL 验证租户隔离、幂等、失败关闭和产物不可变性。

## 首个领域红绿切片

- 红灯：使用 JDK 26.0.2 和 Maven Wrapper 3.9.16 运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -pl backend/contexts/knowledge-data -am -Dtest=GovernedDataSourceTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  `GovernedDataSourceTest` 因缺少 `GovernedDataSource`、`GovernedDataSourceType` 和 `GovernedDataSourceStatus` 编译失败，证明测试先于实现存在。
- 绿灯：同一命令在最小领域实现后通过；随后运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am test`，知识数据及其依赖上下文回归通过。

### 六维检查

- 正确性：测试覆盖文件来源的探测、同步、退役与乐观修订拒绝；状态机不处理尚未实现的产物发布。
- 安全：领域对象不接收连接字符串、密钥、对象正文或租户请求参数；租户和知识空间归属保持必填。
- 复杂度：状态迁移集中于一个不可变聚合，未增加服务或连接器层。
- 性能：仅执行常量时间的值校验与对象复制，不进行 I/O、数据库或外部调用。
- 架构：实现位于 `knowledge-data` 领域层，仅依赖共享内核和本上下文值对象；未引入框架类型。
- 测试质量：先获得缺类编译红灯，再以相同命令转绿；完整知识数据上下文回归通过。

## 同步产物与质量事实红绿切片

- 红灯：运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=SyncArtifactTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  测试因 `SyncArtifact`、`SyncArtifactId`、`QualityReport` 和 `QualityStatus` 缺失而编译失败。
- 绿灯：同一命令在领域实现后通过，并额外执行 `git diff --check` 通过。

### 六维检查

- 正确性：`SyncArtifactTest` 验证来源修订、SHA-256 摘要、记录数和同步时间冻结；`BLOCK` 质量结论拒绝发布判断，`PASS` 与 `WARN` 可继续。
- 安全：产物仅保存摘要和计数，不保存正文、对象存储认证材料或连接配置；质量报告不包含问题正文。
- 复杂度：四个值对象/实体类型只承担不可变事实与发布判断，没有新增服务、仓储或通用规则引擎。
- 性能：构造与发布判断为常量时间内存操作；摘要只验证格式，不重复读取内容或计算哈希。
- 架构：类型位于 `knowledge-data` 领域层，只有 JDK、共享内核与本上下文值对象依赖；持久化和审计仍留在后续 adapter 切片。
- 测试质量：先以缺类编译红灯证明 seam，再用相同测试命令转绿；下一持久化切片须补充真实 PostgreSQL 提交、回滚、隔离与并发反例。

## 受治理来源真实 PostgreSQL 持久化切片

- 红灯：先新增 `PostgresGovernedDataSourceRepositoryTest`，运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=PostgresGovernedDataSourceRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  测试因 `SourceRevision`、受治理数据源仓储、四个持久化 Mapper 与 PostgreSQL 适配器均不存在而编译失败；测试辅助标识遗漏已在实现前修正，未改变待验证行为。
- 绿灯：在提交 `e214637192ec7919c2cffb7a3a4d25c8c5bb3a9e` 上，同一真实 PostgreSQL 测试与
  `KnowledgeDataAutoConfigurationTest` 通过。随后运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am test` 通过，`git diff --check` 通过。

### 已验证事实

- 新迁移 `V202607290015__create_governed_data_source_tables.sql` 只新增 `governed_data_source`、`source_revision`、`sync_artifact` 和 `quality_report`；M1 `text_data_source` 表和路径没有修改。
- `SourceRevision` 固定来源类型、定义 SHA-256 摘要、创建者和时间；`SyncArtifact` 通过外键只能引用已登记修订；质量报告以同步产物为唯一归属。
- 所有读取显式带 `tenant_id` 和 `knowledge_space_id`。真实容器测试证明跨租户不可见；质量报告与待写入产物不一致时，已插入的产物和质量事实都由同一数据库事务回滚。
- 自动配置新增受治理来源仓储；同时修复装配测试，显式提供 M2 `UsableModelConfigurationLookup`，避免旧部署查询 mock 掩盖当前受控嵌入依赖。

### 六维检查

- 正确性：Flyway 外键和检查约束保证修订、产物和质量状态的关系；真实 PostgreSQL 测试覆盖登记、读取、质量告警、跨租户不可见和失败回滚。
- 安全：表和领域对象只保存摘要、计数、规则版本与状态，不保存文件正文、连接地址或凭据；查询不接受调用方传入的任意租户边界。
- 复杂度：以一个小仓储端口和四张事实表完成首个持久化 seam，未改造 M1 文本聚合，也未提前引入连接器、任务队列或索引发布流程。
- 性能：事务只登记短小元数据；外部文件读取、连接探测和嵌入调用仍不在该事务内。来源修订使用显式复合键 SQL，避免为 ORM 方便增加无业务含义的代理标识。
- 架构：代码图谱聚焦 `PostgresTextDataSourceRepository` 和 `KnowledgeDataPersistenceAutoConfiguration`，结论是旧文本路径保持隔离；新增端口在领域层、MyBatis 与 PostgreSQL 实现在 infrastructure，依赖方向符合 ADR-0022。
- 测试质量：先记录缺少公共 seam 的编译红灯，再以同一容器测试转绿；自动装配回归与知识数据依赖上下文全量测试均通过。

## 来源失败恢复与乐观状态更新切片

- 红灯：先扩展 `GovernedDataSourceTest` 和 `PostgresGovernedDataSourceRepositoryTest`，运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=GovernedDataSourceTest,PostgresGovernedDataSourceRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  测试因缺少 `DataSourceOperation`、失败迁移、按失败操作重试和乐观 `update` 仓储契约而编译失败。
- 绿灯：在提交 `64b95dfec0be21437b8f1f72e5c97a5784842324` 上，同一命令通过；随后知识数据及其依赖上下文完整回归命令
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am test` 通过，`git diff --check` 通过。

### 六维检查

- 正确性：领域模型只允许 `PROBING`／`SYNCING` 进入 `FAILED`，并依最后失败操作重试；真实 PostgreSQL 测试验证失败标识持久化、重试清除失败标识和最终状态一致。
- 安全：失败记录只有固定操作标识，不保存外部异常、文件正文、对象位置、连接参数或凭据；更新条件包含租户、知识空间、来源标识和预期修订。
- 复杂度：以一个二值枚举、一个可空持久化列和一个乐观更新方法补齐恢复语义，没有提前引入任务表、错误详情表或连接器抽象。
- 性能：更新为单条带修订条件的 SQL；外部探测、文件读取和嵌入仍不进入事务。
- 架构：代码图谱检查 `GovernedDataSource`、`PostgresGovernedDataSourceRepository` 与既有文本数据源用例后，保持领域状态机、仓储端口和 MyBatis adapter 的分层；ADR-0028 增加明确的失败恢复规则。
- 测试质量：红灯来自缺少契约，绿灯使用同一领域和真实 PostgreSQL 测试；并发测试通过两个相同预期修订的更新证明只有一个数据库提交成功，失败方不能覆盖最终事实。

## 同步产物、质量与来源状态原子完成切片

- 红灯：将原 `record` 契约替换为 `completeSynchronization`，先运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=PostgresGovernedDataSourceRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  测试因原仓储没有原子完成同步契约而编译失败。
- 绿灯：在提交 `00bb64625ece656f59721d94193ed15105d70c7e` 上，同一真实 PostgreSQL 测试通过；完整知识数据及其依赖上下文回归命令
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am test` 通过。

### 六维检查

- 正确性：仓储先锁定来源，再由领域状态机验证 `SYNCING` 与预期修订；产物、质量结论和来源 `READY` 状态在同一事务提交。
- 安全：产物所属的租户、知识空间、来源和修订均由锁定来源与外键复核；无效质量归属回滚产物，并保持来源为 `SYNCING`。
- 复杂度：移除可绕过状态机的通用 `record` 方法，以一个语义明确的完成方法替代，没有引入额外服务层。
- 性能：`SELECT ... FOR UPDATE` 只包围短元数据登记；外部同步 I/O 必须在调用该事务前完成。
- 架构：影响分析表明仅受治理仓储、Mapper 与其真实适配器测试受影响，旧文本数据源和索引路径保持不变。
- 测试质量：新增同步成功、质量归属失败回滚和两个竞争完成请求的真实 PostgreSQL 反例；并发失败方既不插入重复产物，也不覆盖最终来源状态。

## 可信生命周期应用用例切片

- 红灯：先增加 `GovernedDataSourceLifecycleServiceTest`，运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=GovernedDataSourceLifecycleServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  测试因生命周期命令、动作枚举、应用服务和自动装配均不存在而编译失败。
- 绿灯：在提交 `6a6829edb5e7281e58ebd14c206414a4d134988a` 上，同一命令与 `KnowledgeDataAutoConfigurationTest` 通过；知识数据及其依赖上下文完整回归通过。

### 六维检查

- 正确性：服务从当前租户与知识空间加载来源，先核对预期修订、再调用领域状态机，并把 PostgreSQL 乐观更新失败转换为稳定并发错误。
- 安全：只接受 `TrustedRequestContext`，缺少 `knowledge:write` 时在读取资源前拒绝；命令没有租户字段，跨租户来源按不存在处理。
- 复杂度：一个生命周期命令、一个动作枚举和一个服务覆盖短状态动作；没有将外部探测、对象读取或嵌入 I/O 混入数据库事务。
- 性能：服务只做内存校验与一次读取、一次条件更新；耗时同步仍通过原子完成契约在事务外执行后登记。
- 架构：代码图谱对照既有 `TextDataSourceService` 的可信上下文与权限边界；新增服务依赖领域仓储而非 MyBatis 或 HTTP 类型，并由核心自动配置装配。
- 测试质量：红灯来自缺失应用 seam；绿灯覆盖有权限成功、无权限读取前拒绝和乐观更新失败不返回成功状态。尚未开放 Web 控制器，避免在实际 FILE 探测器接入前由浏览器伪造成功结果。

## 受治理文件修订对象事实切片

- 红灯：先增加 `SourceRevisionTest`，并扩展真实 PostgreSQL
  `PostgresGovernedDataSourceRepositoryTest`，运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=SourceRevisionTest,PostgresGovernedDataSourceRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  测试按预期因缺少 `FileSourceObjectReference`、`currentRevision()` 和 `fileObject()` 编译失败。
- 绿灯：在提交 `5eb5759429de4174748851f9041ac4c7c8615d0c` 上，同一命令通过；随后运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am test` 通过。
  综合 `make validate` 初次发现新增 SQL 注解与超规模平铺包；在提交
  `a55f6a406e3c76e6dedb0a9d404f7ae696ec169c` 中迁移为 Mapper XML、登记复杂 SQL、并按修订/质量职责拆包后，`make validate` 与 `git diff --check` 通过。

### 已验证事实

- ADR-0028 明确首个 `FILE` 仅接受已由 ADR-0014 验证的严格 UTF-8 纯文本；PDF、Office 等格式没有被虚假宣称为已支持。
- `FileSourceObjectReference` 冻结平台对象键、SHA-256、字节数和媒体类型，并由四项事实共同生成来源定义摘要；对象键不携带桶名、访问凭据或正文。
- `GovernedDataSource` 在登记时固定当前修订 `1`，`SourceRevision.file` 只接受 `FILE` 类型，且修订定义摘要必须与文件对象事实一致。
- Flyway `V202607290017` 在真实 PostgreSQL 中持久化当前修订与文件对象事实，并用检查约束拒绝文件字段缺失、非文件类型伪带文件字段和非 UTF-8 媒体类型。

### 六维检查

- 正确性：领域测试证明首个修订与文件对象定义绑定；真实 PostgreSQL 测试证明来源当前修订和文件对象字段可读取且保持租户隔离。
- 安全：调用方不能提交桶名或对象键；持久化只保存受控对象定位、摘要、长度和类型，不保存正文、连接地址或凭据。真实 adapter 测试覆盖提交后的读取事实，满足持久化 seam 的事务可见性要求。
- 复杂度：仅增加一个文件对象值对象与四个必要字段；未抽象尚无调用方的通用文件连接器，也未改造 M1 `TextDataSource`。
- 性能：对象摘要基于短元数据计算，不重新读取文件；数据库迁移与登记只保存元数据，外部文件上传、探测和同步仍在事务外。
- 架构：代码图谱分析 `TextDataSourceService`、`MinioTextContentObjectStorageAdapter`、`GovernedDataSourceRepository` 与其影响范围后，M3 只在新受治理来源边界建立对象事实，旧文本路径保持独立；影响分析限定为来源修订、仓储、Mapper 和真实 PostgreSQL 测试。综合门禁发现并修复 SQL 注解与平铺包超限，最终超预算包和 SQL 注解数均为零。
- 测试质量：同一红绿命令先因缺少契约失败，后以领域与真实 PostgreSQL 测试转绿；完整知识数据依赖上下文回归已通过。

## FILE 来源真实对象探测切片

- 红灯：先新增 `FileDataSourceProbeServiceTest` 与
  `TextContentFileSourceObjectProbeTest`，运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=FileDataSourceProbeServiceTest,TextContentFileSourceObjectProbeTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  测试因 `ProbeFileDataSourceCommand`、`FileSourceObjectProbe`、探测应用服务和对象存储适配器均不存在而编译失败。
- 绿灯：在提交 `1e19f63ed` 上，同一命令通过；随后运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=FileDataSourceProbeServiceTest,TextContentFileSourceObjectProbeTest,PostgresGovernedDataSourceRepositoryTest,KnowledgeDataAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`、
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home make validate` 和 `git diff --check`，均通过。

### 已验证事实

- 仅拥有 `knowledge:write` 的可信上下文可触发探测；命令不接收租户、对象键、桶名、正文或凭据。无权限请求在来源读取和对象访问前被拒绝。
- 服务先以乐观修订持久化 `PROBING`，再读取冻结的当前修订并执行对象元数据核验；开始状态写入失败时不访问对象。对象读取或元数据不一致时，只登记 `FAILED(PROBE)`，不返回原始异常。
- 首个适配器复用既有私有对象存储端口，仅比对冻结对象键、SHA-256、字节数与 UTF-8 媒体类型；未复用 M1 `TextDataSource` 的状态或发布路径。
- 既有真实 PostgreSQL 测试持续验证 `PROBING`、`FAILED(PROBE)`、重试和 `READY` 的持久化、隔离与乐观更新；本切片应用测试验证对象 I/O 位于两次短元数据更新之间。

### 六维检查

- 正确性：成功探测按 `DRAFT → PROBING → READY` 返回版本 `2`；对象失败按 `DRAFT → PROBING → FAILED(PROBE)` 保留既有恢复语义。应用测试断言开始持久化、修订读取、对象核验、最终持久化的顺序。
- 安全：租户边界只来自 `TrustedRequestContext`，无权限调用不触碰仓储或对象；失败响应不携带对象异常、正文或凭据。跨租户不可见和状态持久化由真实 PostgreSQL 契约测试覆盖。
- 复杂度：增加一个命令、一个小端口、一个编排服务与一个复用现有存储端口的适配器；没有增加连接器注册表、任务队列或通用文件抽象。
- 性能：外部 `metadata` 调用在 `PROBING` 已持久化之后执行，应用服务不包围数据库事务；数据库路径保持两次条件更新和一次修订读取。
- 架构：代码图谱聚焦 `GovernedDataSourceLifecycleService`、`GovernedDataSourceRepository`、`SourceRevision`、`TextContentObjectStoragePort` 及其调用边界，确认新应用服务仅依赖领域仓储和出站端口，基础设施适配器不接触旧文本聚合。
- 测试质量：先以缺少公共 seam 的编译失败形成红灯；绿灯覆盖有权限成功、无权限读取前拒绝、对象失败关闭、开始状态并发失败不访问对象、元数据摘要不一致以及真实 PostgreSQL 状态持久化回归。
