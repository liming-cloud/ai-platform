# WP-0014：受限评测助手执行门面

## 范围

落实 ADR-0055、ADR-0056 的 assistant-delivery 一侧：冻结草稿版本修订和路由摘要，并在调用期内把一条私有输入缩减为候选／引用 `chunkId`、候选数和枚举结论。此切片不持久化评测正文、不创建评测运行、也不开放 HTTP。

## 红灯

```bash
./mvnw -q -pl backend/contexts/assistant-delivery -am test -Dtest=EvaluationAssistantExecutionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

新增公共 seam 测试后编译失败：缺少 `AssistantTrustedEvaluationResult`、受限评测门面、目标／结果契约和
`AssistantTrustedAnswerExecutor.executeForEvaluation`。

## 绿灯

```bash
./mvnw -q -pl backend/contexts/assistant-delivery -am test -Dtest=EvaluationAssistantExecutionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
./mvnw -q -pl backend/contexts/assistant-delivery -am test
```

两条命令均通过。草稿、受控配置、同租户和 `assistant:write` 在助手上下文复核；冻结目标包含版本修订和确定性路由摘要。成功只输出 `chunkId` 集合，执行异常转为不含原始错误的 `FAILED` 事实。

## 六维检查

- 正确性：红绿测试覆盖无正文回答与异常失败缩减；助手全模块回归通过。
- 复杂度：新增一个命名门面和两类小 DTO，未复用带配额与持久化语义的 `AssistantTestRunService`。
- 冗余：可信回答和模型调用仍复用既有执行器；评测专用结果只做边界缩减。
- 性能：逐条同步调用，不新增队列、缓存或正文副本。
- 架构：evaluation-operations 不读取助手表；assistant-delivery 不保存或投影评测正文。
- 测试质量：反例由缺失 seam 的实际编译失败开始，成功和失败断言均检查无正文输出。
