# 缓存官方资料调研

Stage: architecture-facts  
Category: facts  
Status: current  
Review Status: not-reviewed  
Reviewers: architect, backend lead  
Approval: pending  
Inputs: Redisson / Caffeine / Spring Boot cache official documentation  
Outputs: 二级缓存能力、配置落点、项目约束  
Depends On: `docs/40-architecture-and-tech-spec.md`, `docs/45-details/01-backend-code-organization.md`  
Load When: cache-design/backend-module-development  
Next: 回写架构、模块设计和控制面

## 资料范围

| 框架 | 官方资料 | 本项目关注点 |
| --- | --- | --- |
| Redisson | https://redisson.pro/docs/integration-with-spring/ | Spring Boot starter、Spring Data Redis 版本兼容、Redis 连接配置 |
| Redisson | https://redisson.pro/docs/cache/spring-cache/ | Spring Cache manager、local cache、TTL、max idle、失效同步策略 |
| Caffeine | https://github.com/ben-manes/caffeine/wiki/Eviction | size-based、time-based、reference-based eviction |
| Caffeine | https://github.com/ben-manes/caffeine/wiki/Refresh | `refreshAfterWrite` 与 eviction 的区别、异步刷新语义 |
| Spring Boot | https://docs.spring.io/spring-boot/reference/io/caching.html | Spring Cache 抽象、CacheManager、Redis / Caffeine 自动配置 |

## 官方资料结论

### Redisson

- Redisson 提供 Spring Boot starter，可与 Spring Data Redis 集成。
- Redisson Spring Cache 支持 TTL、max idle、local cache、分布式失效同步等能力。
- Redisson local cache 文档中说明同名本地缓存实例通过相同 pub/sub channel 交换 update / invalidate 事件。
- Redisson local cache options 支持 `StoreMode.LOCALCACHE_REDIS`、`SyncStrategy.INVALIDATE`、`ReconnectionStrategy`、TTL、max idle，也支持 `CacheProvider.CAFFEINE`。
- 本项目不把核心能力绑定到 Redisson PRO near-cache。二级缓存采用显式可控设计：Caffeine L1 + Redisson/Redis L2 + Redisson topic 失效广播。

### Caffeine

- Caffeine 支持按容量、按权重、按时间和按引用驱逐。
- `expireAfterAccess` 适合访问后延长生命周期的数据，`expireAfterWrite` 适合写入后固定时效的数据。
- `refreshAfterWrite` 不是 eviction；刷新期间旧值仍可返回，且只有查询到该 key 时才触发刷新。
- 本项目默认不使用弱引用/软引用作为业务缓存策略，避免 GC 行为造成可用性不可预测。

### Spring Cache

- Spring Cache 是抽象层，真正存储由 `CacheManager` 提供。
- Spring Boot 同时检测 Redis 和 Caffeine 时，需要显式指定或自定义 `CacheManager`，避免自动配置选择不符合二级缓存设计。
- Redis cache 默认 key prefix 有助于避免不同 cache region key 冲突；本项目自定义 key 也必须保留 region、tenant 和版本信息。

## 本项目二级缓存设计结论

### 基线结构

```text
caller
  -> TwoLevelCacheFacade
       -> L1 Caffeine local cache
       -> L2 Redisson / Redis distributed cache
       -> loader(repository / remote / config source)
```

读取策略：

1. 查 L1 Caffeine。
2. L1 miss 查 L2 Redisson。
3. L2 hit 回填 L1。
4. L2 miss 调 loader 读取事实源。
5. loader 成功后写 L2，再写 L1。

写入/失效策略：

1. 业务写入先提交数据库事务。
2. `AFTER_COMMIT` 事件触发 cache eviction。
3. 删除 L2。
4. 通过 Redisson topic 发布 invalidation event。
5. 所有应用实例收到事件后删除本地 L1。

### 缓存命名

key 必须包含：

```text
{env}:{app}:{cacheName}:tenant:{tenantId}:v{schemaVersion}:{businessKeyHash}
```

规则：

- 不允许跨租户共用业务 cache key。
- 平台全局配置必须显式使用 `tenant:global`，并在 cache spec 中声明。
- key 中不放明文邮箱、手机号、token、provider secret、tool credential、SQL 或 prompt。
- 需要按用户权限变化失效的数据，key 必须包含 principal / role / permission version。

### 默认缓存分类

| Cache | L1 TTL | L2 TTL | 说明 |
| --- | --- | --- | --- |
| `tenant-policy` | 30s | 5m | 租户状态、配额、隔离等级 |
| `iam-permission` | 15s | 2m | 权限码、角色、资源 ACL，权限变更必须主动失效 |
| `provider-config` | 30s | 5m | provider 元数据和 capability，不缓存明文 secret |
| `model-catalog` | 5m | 30m | 模型目录、能力矩阵 |
| `knowledge-metadata` | 1m | 10m | 知识库、文档、索引状态摘要 |
| `retrieval-strategy` | 1m | 10m | 应用检索策略配置 |
| `tool-schema` | 1m | 10m | tool schema、风险等级、审批策略，不缓存 credential |
| `rate-limit-policy` | 15s | 2m | 限流和预算策略，变更主动失效 |

默认不缓存：

- Sa-Token token/session 数据，交给 Sa-Token + Redis。
- provider secret 明文。
- tool credential 明文。
- 数据库知识源 credential。
- audit log、tool_run、conversation message。
- 模型响应正文，除非后续明确设计语义缓存并单独评审。

### 故障策略

- Redis 不可用时，鉴权、权限、租户状态、限流和工具策略默认 fail closed。
- Redis 不可用时，模型目录、只读 UI 字典等低风险数据可以短期使用 L1 stale value，但必须有最大 stale 窗口、告警和 trace。
- 缓存 loader 失败不得写入空值。负缓存默认关闭；如需开启，只允许短 TTL，并且必须区分“未找到”和“依赖失败”。
- 热点 key 必须使用 Redisson lock 或本地 singleflight 防止缓存击穿；TTL 必须带 jitter 防止同一时间雪崩。

### 观测要求

每个 cacheName 必须暴露：

- L1 hit / miss。
- L2 hit / miss。
- loader count / latency / error。
- eviction count。
- invalidation publish / receive / lag。
- stale served count。
- cache value serialization error。

## 回写要求

- `docs/32-control-surface.md` 必须增加缓存禁止项和门禁项。
- `docs/40-architecture-and-tech-spec.md` 必须增加二级缓存架构。
- `docs/45-details/01-backend-code-organization.md` 必须说明 cache module、包结构、配置、测试和完成标准。
- `scripts/generate_context_pack.py` 必须把本文件作为 `cache_design` 的按需事实源。
