# WP-0013：M5 模型扩展影响分析

## 范围与比较点

| 项目 | 内容 |
| --- | --- |
| 工作包 | `WP-0013` |
| 比较点 | `a20867c89` |
| 结论状态 | ADR-0040 已接受；M5 实现进行中。 |
| 方法 | 代码图谱探索与影响分析；不读取或输出任何凭据。 |

## 代码图谱事实

| 关注点 | 关键符号或文件 | 事实 | 设计影响 |
| --- | --- | --- | --- |
| 可信回答顺序 | `AssistantTrustedAnswerExecutor` | 已按嵌入、受权检索、证据选择、受控对话、引用核验的顺序执行，且不持久化正文。 | 路由只能替换单一聊天调用 seam，不能复制或绕过整条执行链。 |
| 运行时影响面 | `ModelRuntimeResolver` | 影响 273 个符号，包括索引构建、候选检索、查询运行、自动配置和回归测试。 | 不扩张或重定义该宽泛历史解析器；新能力应采用更窄的受控聊天路由门面。 |
| 可信回答调用方 | `AssistantTrustedAnswerExecutor` | 直接影响面收敛到 `QueryRunService` 及其执行路径。 | 普通查询与草稿测试必须继续共享同一执行门面。 |
| 模型冻结复核 | `GovernedChatRuntime`、`GovernedEmbeddingRuntime`、`UsableModelConfigurationLookup` | 当前每次执行复核配置、目录修订与能力。 | 回退候选必须冻结并逐项复核，不能按当前目录替换。 |
| 查询终态与诊断 | `QueryRunTerminalCommitter`、`QueryDiagnosticFact` | 终态、引用、事件和诊断事实已原子提交，诊断最小字段不含模型标识。 | 调用轨迹必须是内部事实并纳入同一终态事务，不能扩张诊断 API。 |
| 查询轨迹提交 seam | `PostgresQueryRunRepository.commit` | 既有单事务已提交 `QueryRun` 终态、引用、事件及 `QueryDiagnosticFact`。 | `ModelInvocationTrace` 应作为同一提交契约的内部附属事实；不得由独立异步写入替代。 |
| 草稿测试轨迹提交 seam | `PostgresAssistantTestRunRepository.complete`、`AssistantTestRunService` | 草稿运行已通过独立仓储提交终态和最小引用，不与 `QueryRun` 共用表或配额。 | 为保持根与配额独立，轨迹须由草稿测试仓储在同一终态事务提交，不能复用查询表。 |
| 路由执行边界 | `ChatRouteExecutor`、`ChatRouteExecution`、`ChatRouteExhaustedException`、`AssistantTrustedAnswerExecutor` | model-runtime 集中候选循环和瞬态失败分类；assistant-delivery 仅映射冻结候选及配置快照。 | 成功和候选耗尽均返回有序尝试事实；调用方不得重新推断或自行重试。 |
| 草稿测试终态提交 | `AssistantTestRunTerminalCommitter`、`PostgresAssistantTestRunRepository.commit` | 草稿测试的轨迹、终态和引用使用同一事务提交。 | 不能把应用 DTO 放入领域仓储；应用 port 负责携带内部轨迹，基础设施实现原子写入。 |

## 反例矩阵

| 风险 | 必须拒绝或保持 | 首条切片的验证 |
| --- | --- | --- |
| 当前配置替换冻结候选 | 历史运行不可复核。 | 单元测试与真实 PostgreSQL 验证仅使用发布时路由。 |
| 权限、检索或引用失败触发回退 | 可能绕过可信失败关闭。 | 纯领域／应用反例证明仅标准化瞬态调用失败可回退。 |
| 嵌入回退 | 索引维度与候选空间改变。 | 路由目标只包含聊天候选，嵌入绑定仍为单值。 |
| 轨迹扩大敏感留存 | 泄漏正文、端点或密钥。 | 映射、SQL、HTTP 和事件反例检查禁止字段。 |
| 轨迹与终态分离写入 | 留下不可审计半成品。 | 真实 PostgreSQL 验证查询与草稿测试均将终态和逐次轨迹同一事务提交。 |

## 待用户决定

ADR-0040 已接受“助手版本冻结、最多两次后备、仅聊天、有限瞬态回退、内部调用轨迹”为首条 M5 切片。查询与草稿测试的逐次轨迹已原子持久化；`WP-0013` 与 M5 仍须完成其余验收及人工评审，不能提前标记完成。
