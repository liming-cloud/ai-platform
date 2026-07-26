# WP-0004 索引与向量检索研究基线

## 调研请求

| 字段 | 值 |
| --- | --- |
| 编号 | IR-WP0004-INDEXING |
| 问题 | 如何在 Spring AI 2.0.0、PostgreSQL 18 和 pgvector 上建立可版本化、可授权、可重建的首个检索索引 |
| 授权模式 | 只读自动研究 |
| 授权依据 | 用户明确要求相关数据通过检索查询并制定标准 |
| 资料范围 | Spring AI 官方文档与接口、pgvector 官方仓库与镜像、PostgreSQL 18 官方文档 |
| 排除范围 | 厂商宣传、社区教程、第三方基准、问答生成和重排 |
| 成本上限 | 三组定向搜索和一次正式镜像清单检查 |
| 停止条件 | 能确定嵌入公共契约、向量表形态、租户过滤、索引版本激活和 M1 检索方案 |

## 一手资料

- [Spring AI 2.0.0 EmbeddingModel 接口](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/embedding/EmbeddingModel.html)
- [Spring AI 模型接口概览](https://docs.spring.io/spring-ai/reference/api/)
- [pgvector 官方仓库](https://github.com/pgvector/pgvector)
- [pgvector 正式镜像标签](https://hub.docker.com/r/pgvector/pgvector/tags)
- [PostgreSQL 18 唯一索引](https://www.postgresql.org/docs/18/indexes-unique.html)
- [PostgreSQL 18 事务隔离](https://www.postgresql.org/docs/18/transaction-iso.html)
- [PostgreSQL 18 创建索引](https://www.postgresql.org/docs/18/sql-createindex.html)

## 直接观察

### Spring AI

- `EmbeddingModel` 是跨厂商嵌入通用接口，提供单文本、批量文本和结构化请求调用。
- 批量调用结果与输入顺序对应，平台仍需验证返回数量、下标、维度和数值有限性。
- `dimensions()` 的默认实现可能触发远程调用；已知维度的实现应显式声明，
  不能在索引事务中临时探测。
- Spring AI 类型属于模型厂商扩展，知识和查询上下文只能依赖平台嵌入契约。

### pgvector

- 默认查询执行精确最近邻，召回完整；HNSW 与 IVFFlat 用召回换取速度。
- HNSW 不需要训练，查询速度与召回权衡通常优于 IVFFlat，但构建更慢且占用更多内存。
- 带筛选条件的近似索引先扫描近似候选，再应用筛选，候选不足时可能返回少于请求数量的结果。
- 官方建议先为筛选列建立普通索引；低选择性条件再考虑部分索引、分区或迭代扫描。
- `vector` 不指定维度时可以在同一列保存不同维度；近似索引必须只覆盖同一维度，
  可使用表达式与部分索引。
- `vector` 支持最多 2000 维，元素拒绝非数和无穷值；距离计算要求两侧维度相同。
- 0.8.5 是调研时官方正式版本，PostgreSQL 18 Trixie 多架构镜像摘要为
  `sha256:9d2e61c7352b9e9f4798df5fd9a498f043f4cda1cdacc707de3d198650f4321e`。

### PostgreSQL

- 多列唯一约束由唯一 B-tree 索引强制，可作为幂等和并发去重的最终防线。
- 更新冲突会等待并重新检查条件，因此带状态和修订号的条件更新可以实现乐观状态迁移。
- 部分索引只保存满足谓词的行，适合后续按固定模型部署和维度构建近似向量索引。

## 推导结论

以下是基于一手资料和本项目安全目标作出的工程推导：

1. M1 使用精确余弦检索，不使用 HNSW 或 IVFFlat。演示数据量小，
   精确查询能提供确定性结果和完整召回，也避免近似候选先于租户过滤带来的结果不足。
2. 向量列使用无固定维度的 `vector`，每行同时保存并校验嵌入维度；
   索引版本固定模型部署、修订和维度，查询两侧必须匹配。
3. 筛选条件固定包含租户、知识空间和活动索引版本，并为这些列建立 B-tree 索引。
4. 只有容量与召回证据证明精确检索不足时，才按模型部署、修订和维度建立部分
   HNSW 表达式索引，并单独验证过滤后的 Recall@10。
5. 嵌入调用和对象读取不能放在数据库事务中。数据库只负责快照、任务认领、
   产物提交和原子激活。
6. 索引创建必须先冻结已发布来源快照；重试读取同一快照，不能悄悄吸收后续发布内容。

## 仍未覆盖

- 真实厂商嵌入模型的吞吐、批大小、费用和限流。
- 大规模数据下精确查询转向 HNSW 的容量阈值。
- 生产分区策略和在线并发建索引。

这些问题不会阻止 M1 的确定性闭环，但必须在真实厂商和性能工作包中产生新证据。
