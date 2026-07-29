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

## FILE 登记、探测 HTTP 与企业控制台切片

### 已实现边界

- `POST /api/v1/governed-data-sources/files` 继续只接收受控 UTF-8 纯文本；调用方不能提交桶名、对象键、租户或凭据。
- 新增 `POST /api/v1/governed-data-sources/files/{dataSourceId}/probe`，请求只携带知识空间和预期版本；应用服务仍从 `TrustedRequestContext` 提取租户与主体，并在读取对象前检查 `knowledge:write`。
- 企业控制台的“数据”区域增加独立的“文件来源登记”任务，和 M1 的“首份文本发布”并列切换但不会复用或改写后者的状态机。任务只展示数据源标识与生命周期状态；正文、对象位置、摘要和凭据不反显。
- 没有可用 `UsableModelConfigurationLookup`（例如本地未配置模型主密钥）时，索引服务与 HTTP 入口条件化关闭，知识空间和 FILE 任务仍可启动；模型配置完整时索引服务和入口保持装配。

### 红绿记录

- HTTP 红灯：新增 `GovernedFileDataSourceControllerTest` 后运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=GovernedFileDataSourceControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  测试按预期因控制器尚未接收探测服务且不存在 `probe` 方法编译失败。
- 客户端红灯：运行
  `npm --prefix frontend run test -- --run apps/console-web/src/api/control-plane-client.test.ts`。
  新用例按预期报出 `client.probeFileDataSource is not a function`。
- 装配红灯：新增“无受控模型查询仍可启动”和“无索引用例不注册索引入口”测试后运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=KnowledgeDataAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  测试按预期暴露索引用例与 HTTP 控制器对缺失模型查询的强耦合。
- 绿灯：运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=GovernedFileDataSourceRegistrationServiceTest,GovernedFileDataSourceControllerTest,FileDataSourceProbeServiceTest,TextContentFileSourceObjectProbeTest,KnowledgeDataAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`、
  `make project-verify`、`make architecture-guard`、
  `npm --prefix frontend run test -- --run apps/console-web/src/api/control-plane-client.test.ts`、
  `npm --prefix frontend run typecheck`、`npm --prefix frontend run lint` 与
  `npm --prefix frontend run build`，均通过；`git diff --check` 通过。

### 六维检查

- 正确性：控制器测试验证探测命令中的可信上下文、知识空间、来源标识和乐观版本保持完整；客户端测试验证真实 HTTP 路径、会话头与预期版本。
- 安全：控制器不接收对象定位、正文以外的文件事实或租户字段；权限判断、跨租户不可见与失败脱敏继续保留在应用服务。控制台不回显正文、对象键或凭据。
- 复杂度：新增一个端点、一个客户端方法和一个独立控制台任务；未把 M3 生命周期并入 M1 文本发布状态机。
- 性能：探测仍由已持久化 `PROBING` 状态后的事务外对象核验完成；前端不轮询也不传输对象元数据。
- 架构：代码图谱检查 `FileDataSourceProbeService`、`GovernedFileDataSourceController`、`ConsoleApp`、`ControlPlaneClient` 与 `IndexVersionService` 的影响范围；索引入口只在其跨上下文依赖实际存在时装配。
- 测试质量：三个新行为均先观察到实际红灯，再以相同 seam 转绿；完整浏览器验收尚未执行，不能以这些自动化结果代替。

### 浏览器状态

本地控制台与控制面正在重建时 Docker Desktop 守护进程返回“unable to start”，截至本记录尚不能形成浏览器成功、失败、无权限和恢复证据。该环境故障不改变上述自动化结论；Docker 恢复后必须重新构建并执行真实浏览器操作，再补充本节并推进后续 M3 切片。

### 浏览器正向验收（2026-07-29）

- 运行环境：以提交 `2d3248feed2ecd49e1d2806baa9431125e2d0a03` 重建本地 Compose 的 `control-plane` 与 `console-web`；`docker compose ... ps` 显示两项服务均为 `healthy`，控制面已完成 27 条 Flyway 迁移校验。
- 操作：以本地演示租户的知识管理员登录企业控制台，选择知识空间“测试”，提交合成的 UTF-8 文本并登记“浏览器文件来源验收”。结果区出现数据源编号、`DRAFT` 状态及“开始探测”。执行探测后，状态变为 `READY`，并出现“探测已通过”。
- 保密验证：登记完成后的结果区未显示提交的合成文本；页面仅显示数据源标识和生命周期结果，未反显正文、对象位置、摘要或凭据。
- 运行差异：初次打开的浏览器标签命中旧的静态资源缓存，未出现 M3 入口。以查询参数重新打开本地控制台后加载当前构建，入口与上述验收路径正常可用；本次以当前构建完成验收。

本记录只覆盖 FILE 来源的正向“登记 → 探测”浏览器闭环。M3 的失败、未授权、恢复及更完整生命周期验收仍未完成，因此不得据此进入 M4 或申请 M3 人工评审。

## FILE 探测失败后的浏览器恢复切片（2026-07-29）

### 红绿记录

- 红灯：先在 `FileDataSourceProbeServiceTest` 增加“探测失败后重新探测”用例，运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=FileDataSourceProbeServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  用例按预期抛出 `DataSourceStateException`：原实现只能从 `DRAFT` 开始探测，不能恢复 `FAILED(PROBE)`。
- 绿灯：仅在 `FileDataSourceProbeService` 中将 `FAILED(PROBE)` 映射为领域对象的 `retry`，并明确拒绝 `FAILED(SYNCHRONIZATION)`。运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=FileDataSourceProbeServiceTest,GovernedFileDataSourceControllerTest,PostgresGovernedDataSourceRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test` 与 `git diff --check`，均通过。真实 PostgreSQL 容器已完成迁移并通过状态持久化回归。

### 浏览器失败与恢复验收

- 以本地演示租户知识管理员在企业控制台创建新的合成 UTF-8 文件来源。
- 仅执行可恢复的本地 `docker compose stop minio`，不删除对象或数据卷；在同一页面点击“开始探测”后，来源状态变为 `FAILED`，操作按钮变为“重新探测”。
- 执行 `docker compose start minio`，确认服务恢复 `healthy`；在同一来源点击“重新探测”后，状态变为 `READY`，页面显示“探测已通过”。
- 这是浏览器经由 HTTP 和真实对象存储完成的恢复路径，不是直接调用应用服务或伪造前端状态。

### 六维检查

- 正确性：`FAILED(PROBE) → PROBING → READY` 使用两次乐观状态更新；测试校验恢复后先写入 `PROBING`、再读取冻结修订和访问对象，最后写入 `READY`。浏览器验收复现同一闭环。
- 安全：只允许此前失败于 `PROBE` 的来源重试；同步失败明确拒绝且不读取对象。浏览器页面仍只显示状态与数据源编号，不回显正文、对象位置、摘要或凭据。
- 复杂度：复用既有领域 `retry` 规则，只增加一个私有状态选择方法和两个针对性测试，没有引入新端点、队列或重试框架。
- 性能：恢复探测保持原有两次短条件更新和一次对象核验；对象 I/O 不进入数据库事务，失败不会重试循环。
- 架构：代码图谱探索 `FileDataSourceProbeService`、`GovernedDataSource`、`GovernedFileDataSourceController` 与企业控制台任务；影响分析限于 FILE 探测服务、控制器、自动装配和对应测试，未波及旧文本发布路径。
- 测试质量：先获得“失败来源不能重新探测”的实际红灯，再以同一测试转绿；另有同步失败反例保证错误恢复范围不扩张，并以真实浏览器和本地对象存储完成失败—恢复验证。

### 全量门禁状态

本切片的针对性测试、真实 PostgreSQL 回归和 `git diff --check` 均通过。2026-07-29 两次执行 `make project-verify`／`make validate` 均在本切片之外的 `AssistantApiTest.租户管理员可创建助手冻结引用发布并隔离列表` 停止：助手版本响应的 `chatDeploymentRevision` 为 `null`，而测试契约要求 `long`。本切片没有修改助手、模型部署或控制面响应代码，因此该全量门禁失败已登记为独立风险，不能被解释为 FILE 恢复切片通过，也不能据此标记 WP-0011 完成。

本切片补齐 FILE 探测的失败恢复，不覆盖 M3 尚未交付的同步、质量、退役、索引等能力；M3 仍不得进入人工评审或 M4。

## FILE 同步、质量事实与 HTTP 闭环切片（2026-07-29）

### 红绿记录

- 红灯：先新增 `FileDataSourceSynchronizationServiceTest`，运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=FileDataSourceSynchronizationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  测试按预期因 `SynchronizeFileDataSourceCommand` 与 `FileDataSourceSynchronizationService` 尚不存在而编译失败。
- 绿灯：代码提交 `ddf23caf5ce23c29528163d041af2e7b605270ec` 后运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=FileDataSourceSynchronizationServiceTest,FileDataSourceProbeServiceTest,GovernedFileDataSourceRegistrationServiceTest,GovernedFileDataSourceControllerTest,TextContentFileSourceObjectProbeTest,PostgresGovernedDataSourceRepositoryTest,KnowledgeDataAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test` 与 `git diff --check`，均通过。
- 真实控制面绿灯：运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/bootstrap/control-plane-bootstrap -am -Dtest=TextDataSourceApiTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  Testcontainers PostgreSQL、Redis 与完整 HTTP 链路通过：FILE 登记、探测、同步后真实数据库各有一个 `sync_artifact` 与 `PASS` 质量报告。

### 已验证事实

- `POST /api/v1/governed-data-sources/files/{dataSourceId}/synchronization` 只接受知识空间和预期修订；租户、权限、对象键与对象正文不由调用方提交。
- 同步先以乐观修订持久化 `SYNCING`，随后在事务外流式读取当前冻结文件对象，重算 SHA-256 与字节数。任何读取或完整性失败只登记 `FAILED(SYNCHRONIZATION)`，不保存产物或质量半成品。
- 成功同步创建不可变 `SyncArtifact`，首个规则集 `file-integrity-v1` 只确认冻结对象完整性并生成 `PASS`；产物、质量结论与来源回到 `READY` 仍复用仓储的同一事务完成契约。
- 仅 `FAILED(SYNCHRONIZATION)` 可以从同步入口恢复到 `SYNCING`；探测失败不会被误作同步重试。无 `knowledge:write` 权限时在来源与对象读取前拒绝。

### 六维检查与评审

- 正确性：单元测试覆盖成功、摘要不一致失败、同步恢复和权限前置；真实 HTTP 测试覆盖 `DRAFT → READY → SYNCING → READY` 及产物、质量事实提交。
- 安全：对象位置和正文不反显，失败路径不返回存储异常；所有查询仍使用可信租户与知识空间边界。
- 复杂度：新增一个命令、一个应用服务和一个端点，复用既有对象存储端口与原子仓储契约，未引入连接器注册表或后台任务。
- 性能：按 8 KiB 缓冲流式计算摘要；外部对象 I/O 位于短数据库状态更新之外。
- 架构：代码图谱探索 `TextContentObjectStoragePort`、`GovernedDataSourceRepository`、`GovernedDataSource` 与 `FileDataSourceProbeService`，影响分析确认同步服务仅依赖领域仓储与出站对象端口；`interfaces → application → domain` 方向保持不变。
- 测试质量：红灯为缺少公共 seam 的实际编译失败，绿灯包含 Mockito 行为反例、真实 PostgreSQL 原子仓储契约和完整控制面 HTTP 测试。固定变更集按 `tdd`、`code-review` 复核，无未关闭可执行问题。

本切片仅完成首个 FILE 的完整性同步与 `PASS` 质量事实；尚未实现可配置质量规则、对象存储与结构化数据集连接器、来源发布／退役和索引重建，因此 M3 继续进行，不能进入人工评审或 M4。

## 已发布同步产物的原子持久化切片（2026-07-29）

### 红绿记录

- 红灯：先为 `GovernedDataSourceRepository` 增加 `publishArtifact` 契约，并在
  `PostgresGovernedDataSourceRepositoryTest` 增加“数据库为 `BLOCK` 而调用方伪造
  `WARN`”的反绕过用例。运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=PostgresGovernedDataSourceRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  编译按预期失败：PostgreSQL 仓储尚未覆盖新发布契约。
- 绿灯：在提交 `73cd917e3` 上运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=PostgresGovernedDataSourceRepositoryTest,KnowledgeDataAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test` 与
  `git diff --check`，均通过。测试容器实际应用 `V202607290018`，且自动配置测试验证新增 Mapper 的显式替身与仓储装配。

### 已验证事实

- 发布操作先锁定来源，再从数据库按租户、知识空间和产物标识重取不可变同步产物及质量报告；不信任调用方传入的质量状态。
- 数据库存储为 `BLOCK` 时，即使调用方构造同一产物的 `WARN` 发布对象，事务会拒绝且来源版本和发布表均不变化。
- `PASS` 与 `WARN` 发布会在同一事务内将来源保持为 `READY`、版本加一，并冻结产物、质量状态、规则版本和发布时间至 `published_sync_artifact`。
- Mapper XML 避免复合主键实体进入 MyBatis-Plus 通用 `BaseMapper`；真实 PostgreSQL 回归验证 XML 映射、Flyway、租户隔离和自动配置共同可用。

### 六维检查

- 正确性：发布以数据库实际质量重新构建结论，来源乐观修订写入成功后才插入同一事务的发布事实；任一步失败均回滚。
- 安全：发布不接收正文、对象键、租户或质量明细作为可被信任的持久化依据；跨租户查找返回空，伪造非阻断质量不能绕过阻断事实。
- 复杂度：复用既有来源锁、同步产物和质量 Mapper，仅增加一个显式仓储动作和专用 XML Mapper，没有引入通用发布框架或新队列。
- 性能：发布仅执行来源行锁与两次受限索引查询，再完成一次条件更新和一次插入；不读取对象正文，不调用模型或外部服务。
- 架构：代码图谱聚焦 `GovernedDataSourceRepository`、`PostgresGovernedDataSourceRepository`、`QualityReport` 与自动配置影响范围；领域只声明发布契约，基础设施完成 MyBatis 与事务细节，依赖方向保持 `interfaces → application → domain` 和 `infrastructure → application/domain`。
- 测试质量：先以缺失仓储覆盖形成编译红灯，再以真实 PostgreSQL 验证正向冻结、跨租户不可见和数据库 `BLOCK` 反绕过；自动配置回归额外捕获并修复新增 Mapper 未替身的装配缺口。

本切片只建立“同步产物已发布”的持久化事实，尚未开放知识管理员发布入口、来源选择列表或索引构建对该事实的消费；M3 仍不得进入人工评审或 M4。

## FILE 同步产物受控发布入口切片（2026-07-29）

### 红绿记录

- 红灯：新增 `FileSyncArtifactPublicationServiceTest`，运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=FileSyncArtifactPublicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  测试按预期因缺少发布命令和应用服务而编译失败。
- 控制器红灯：扩展 `GovernedFileDataSourceControllerTest` 后运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=GovernedFileDataSourceControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  测试按预期因控制器尚未接收发布用例而编译失败。
- 绿灯：在提交 `92f9440f7` 上运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=FileSyncArtifactPublicationServiceTest,GovernedFileDataSourceControllerTest,KnowledgeDataAutoConfigurationTest,PostgresGovernedDataSourceRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test` 与
  `git diff --check`，均通过。

### 已验证事实

- 新增 `POST /api/v1/governed-data-sources/files/{dataSourceId}/artifacts/{artifactId}/publish`；请求只携带知识空间、来源、产物和预期版本。
- 发布应用服务在任何来源、产物或质量读取前检查 `knowledge:write`，并将产物不属于目标来源的情况按不存在处理。
- 服务从仓储重取质量报告来创建发布事实，最终仍由已验证的原子仓储动作复核持久化质量；HTTP 入口无法传入质量、对象位置或正文。
- 核心自动配置只在受治理数据源仓储存在时提供发布用例；Web 控制器也只有在所有 FILE 生命周期用例完整时装配。

### 六维检查

- 正确性：正向测试断言 `WARN` 产物发布后返回同一来源的新 `READY` 版本；控制器完整转发可信上下文、来源、产物和预期版本。
- 安全：无权限时不触碰仓储；跨来源产物不继续读取质量报告或发起发布；数据库层保留 `BLOCK` 反绕过和租户隔离复核。
- 复杂度：只增加一个命令、一个应用服务与一个受控 HTTP 动作，复用既有生命周期返回 DTO，未新增通用任务或发布框架。
- 性能：发布路径只读取来源、产物和质量结论，再进入短事务；没有对象 I/O、模型调用或轮询。
- 架构：代码图谱检查 FILE 同步服务、Web 控制器和核心自动配置，发布服务仅依赖领域仓储与时钟，HTTP 层不接触持久化或对象存储。
- 测试质量：应用用例、控制器委派、自动配置与真实 PostgreSQL 仓储回归共同通过；两个公共 seam 均先观察到缺失实现的红灯。

本切片尚未提供可选择的已发布产物列表，也未让索引创建冻结 `GOVERNED_SYNC_ARTIFACT` 快照；这些仍是 M3 后续工作，不能申请人工评审或进入 M4。

## 类型化索引来源快照切片（2026-07-29）

### 红绿记录

- 红灯：新增类型化来源迁移后运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=PostgresIndexVersionPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false test`。
  真实 PostgreSQL 按预期拒绝旧创建路径未写入的 `source_revision`；补齐旧文本实体写入后转绿。
- 第二个红灯：新增 FILE 来源夹具后，数据库先拒绝不匹配的文件定义摘要；修正为领域对象计算的摘要后，构建队列又暴露 M1 文本读取端口与 FILE 小写媒体类型的不兼容。
- 绿灯：在提交 `658e12d13` 上运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=PostgresIndexVersionPersistenceTest,IndexBuildWorkerTest -Dsurefire.failIfNoSpecifiedTests=false test` 与 `git diff --check`，均通过。真实容器按顺序应用 V202607290019、V202607290020。

### 已验证事实

- `index_version_source` 以 `LEGACY_TEXT_CONTENT` 和 `GOVERNED_SYNC_ARTIFACT` 区分来源；后者冻结来源修订、同步产物、质量状态、规则版本、发布时间和对象事实。
- 单条索引选择 SQL 在同一读取快照中合并 M1 已发布文本与每个非退役 FILE 来源的最新发布产物；`BLOCK` 无法通过发布表约束进入候选。
- 构建租约保留 FILE 来源类型、长整型修订、产物标识和质量事实。构建器仅把 FILE 已冻结的小写媒体事实适配到既有 M1 文本读取端口常量，不修改数据库快照。
- 真实测试证明索引版本可冻结两条历史文本和一条 `WARN` FILE 产物，且租约能读取该类型化来源。

### 六维检查

- 正确性：迁移和实体同时维护旧文本 `source_revision`，新来源由类型化校验约束其不可变事实；快照摘要纳入类型、修订、产物和质量规则。
- 安全：选择查询限定租户、知识空间、非退役来源与最新发布事实；构建不重新读取可变来源配置或质量结果。
- 复杂度：仅演进一个快照表、一个查询投影和构建端口 DTO，保留历史两参数快照构造，未复制 M1 文本聚合。
- 性能：来源合并在一个受限 SQL 内完成；构建仍只读取冻结行，无额外模型或对象元数据查询。
- 架构：代码图谱影响分析覆盖 `IndexSourceEntity`、创建仓储与构建队列；领域快照表达类型化事实，MyBatis、SQL 与媒体适配保留在基础设施层。
- 测试质量：两次真实数据库红灯分别覆盖迁移完整性和文件对象不变量，最终验证历史索引兼容、混合来源冻结和租约映射。

### 后续构建与退役回归

- 在提交后的补强测试中运行
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=IndexBuildWorkerTest,PostgresIndexVersionPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false test` 与 `git diff --check`，均通过。
- FILE 类型化快照已由 `IndexBuildWorker` 实际完成对象核验、分块、嵌入和批次提交；测试断言构建器只消费冻结对象引用及产物质量事实。
- 退役 FILE 的发布审计行保留，但选择 SQL 明确排除 `RETIRED` 来源；真实 PostgreSQL 测试证明新索引只保留历史文本候选。

本切片仍未完成索引重建入口、真实 HTTP／对象存储 FILE 构建容器验收和知识管理员浏览器任务；M3 继续进行，不能申请人工评审或进入 M4。

## FILE 控制台全生命周期与工作器启动恢复（2026-07-29）

### 实现与自动化验证

- 提交 `96637b173` 为生命周期返回投影增加失败操作、同步产物标识和质量结论；控制台只据此展示下一可执行动作，不回显对象位置、正文、摘要或凭据。它补齐 FILE 的同步、质量处置、显式发布、索引创建／发布和退役入口。
- 提交 `10ac2d6aa` 修复默认无本地研发主密钥时 `ingestion-worker` 因缺少受控模型运行时 Bean 无法启动的问题。工作器在缺少受控运行时时仍能启动以处理历史部署索引；受控配置索引在执行阶段保持失败关闭，绝不回退为旧部署。
- 在提交 `10ac2d6aa` 上运行：
  `JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home ./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -pl backend/contexts/knowledge-data -am -Dtest=FileDataSourceSynchronizationServiceTest,FileDataSourceProbeServiceTest,FileSyncArtifactPublicationServiceTest,GovernedFileDataSourceControllerTest,PostgresIndexVersionPersistenceTest,IndexBuildWorkerTest,KnowledgeDataAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`，通过；真实 PostgreSQL 容器应用至 `V202607290020`。
- 前端在 `frontend/` 工作区运行
  `npm test -- --run apps/console-web/src/api/control-plane-client.test.ts && npm run typecheck && npm run lint -- --quiet`，9 项客户端测试、类型检查和静态检查均通过。
- 提交 `8d7f19996` 将默认安全部署中模型配置读取入口未装配的 `404` 转为“联系平台运维完成模型运行时配置后重试”的可恢复提示，不暴露路由或装配细节；运行
  `npm test -- --run apps/console-web/src/features/governed-data/GovernedFileDataWorkspace.test.ts && npm run typecheck && npm run lint -- --quiet`，1 项定向测试、类型检查和静态检查均通过。该映射只适用于创建索引任务，其他请求错误仍保留原失败语义。
- 本地 Compose 的 `control-plane`、`ingestion-worker` 与 `console-web` 均为 `healthy`；工作器已不再重启。

### 浏览器验收

- 知识管理员在企业控制台创建合成 FILE 来源“`M3 文件闭环浏览器验收`”，实际经历 `DRAFT → READY → 同步完成(PASS) → 显式发布 → RETIRED`。全过程只展示数据源编号、状态、质量结论和下一步动作，未展示文件正文、对象位置、摘要或密钥。
- 同步成功后页面显示“同步产物已生成，质量结论：PASS”，且仅在该状态显示“发布同步产物”；发布成功后才出现“创建索引版本”，证明同步不会隐式变为可索引发布。
- 对同一合成来源执行退役后页面显示 `RETIRED`，不再提供同步、发布或索引创建动作。真实 PostgreSQL 回归另已证明退役来源的历史发布事实保留、但不会进入新索引选择。
- 在默认 Compose（未启用 `compose.real-model.yml`）上点击“创建索引版本”时，控制面因未装配 `model-configurations` 读取端点返回受控错误，浏览器仅显示追踪标识 `trc_2082419561583345666`，未泄露内部异常。该环境没有本地研发主密钥，不能作为 M2 受控模型验收环境；必须在已提供的本地研发主密钥下启用真实模型覆盖层后，重新完成 FILE 的真实 Embedding 构建、索引发布和重建验收。

### 当前结论

此切片补齐并验证了 FILE 的控制台登记、探测、同步、质量、显式发布和退役，以及无密钥工作器安全启动。真实受控 Embedding 索引构建仍缺少本地研发主密钥这一运行前置条件；OBJECT_STORAGE、STRUCTURED_DATASET、Schema／敏感级别、质量规则配置、浏览器未授权／过期路径及 M3 全量门禁尚未完成。因此 WP-0011 继续进行，不能申请 M3 人工评审或进入 M4。
