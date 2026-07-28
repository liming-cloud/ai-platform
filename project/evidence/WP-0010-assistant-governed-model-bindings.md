# WP-0010：助手受控模型配置绑定与同源发布

实现提交：`b7ce49410`（`feat: 冻结助手受控模型配置绑定`）。

## 范围与结果

新建助手版本请求显式提交 `chatModelConfigurationId` 与
`embeddingModelConfigurationId`，不再接受 M1 的 `ModelDeployment` 标识。
创建草稿和发布前均通过 `UsableModelConfigurationLookup` 重新验证当前主体
可用的 `CHAT` 与 `EMBEDDING` 配置。

活动索引快照改为携带完整的 `EmbeddingConfiguration`。新版本只有在嵌入配置
标识、配置修订、目录条目、目录修订和嵌入维度均与活动索引完全一致时才能
创建或发布。配置被停用或授权被撤销后，发布失败关闭。

`assistant_delivery.assistant_version` 新增受控绑定快照字段。数据库约束保证
一条助手版本只能选择历史 M1 部署事实或一对受控配置快照，不能混用。历史
版本保持原字段与确定性运行路径，未建立旧部署到新配置的隐式映射。

## 可复现验证

```bash
./mvnw -q -pl backend/contexts/assistant-delivery -am test \
  -Dtest=AssistantServiceTest,AssistantVersionTest,PostgresAssistantRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false

make architecture-guard
make validate
make project-verify
```

结果：全部通过。定向测试包含受控 Chat/Embedding 快照冻结、活动索引嵌入
配置不一致拒绝、发布前撤销拒绝，以及真实 PostgreSQL/Flyway 中历史部署列
为空和受控字段持久化的反例。

## 六维自动评审

- 正确性：创建与发布两处复核相同冻结快照，并在索引版本变化后拒绝发布。
- 安全：只传递无凭据的配置快照；撤销或停用失败关闭，不回退到旧部署。
- 架构：助手上下文只依赖模型运行时安全门面与知识数据活动索引门面。
- 性能：只读取两条具体配置和一条活动索引，不引入列表扫描或客户端缓存。
- 复杂度：以两类小值对象表达 Chat 与 Embedding 的不同能力，未增加厂商抽象。
- 测试质量：覆盖领域不变量、应用反例、真实 PostgreSQL 迁移和全仓门禁。

本证据只完成助手版本受控配置与同源发布边界。M2 仍缺受控 Chat 查询运行时、
查询后二次可用性复核、真实火山方舟调用验收及 M2 人工评审；不得进入 M3。
