# WP-0014：受限评测运行应用、持久化与装配证据

## 范围

本证据只覆盖 ADR-0050、ADR-0051、ADR-0052、ADR-0055 与 ADR-0056 已接受的评测运行闭环：同租户
`assistant:write` 触发、私有 JSON Lines 逐条读取、受限内存执行、最小观察/指标终态保存及自动配置。
不包含正文读取、普通查询、发布授权、HTTP、浏览器、预算、例外、M7 或 M8。

## 红绿路线

1. `EvaluationExecutionTest` 先要求终态 `EvaluationRun` 同时保存 `EvaluationObservation`；初始编译因缺少
   `observation()` 失败，补齐聚合不变量和领域服务后通过。
2. `EvaluationRunExecutionServiceTest` 先引用不存在的运行请求、仓储和私有读取契约；初始编译失败。实现后证明：
   无 `assistant:write` 时不打开私有对象也不调用执行门面；授权后逐条处理且只保存计数、指标和结论；未知但格式正确的
   `chunkId` 引用只形成无正文失败结论。

## 实现与边界

- `EvaluationDatasetEntryReader` 与 `JsonLinesEvaluationDatasetEntryReader` 复用严格 UTF-8 JSON Lines 规则；
  上传暂存与运行解析不再各自维护条目语义。
- `EvaluationRunExecutionService` 只负责令牌权限、同租户聚合加载、I/O 编排和终态提交；质量通过判定继续由
  `EvaluationExecution` 收回。解析、对象读取或受限执行失败仅增加失败计数，不保存或返回异常正文。
- `EvaluationDatasetRepository` 仅恢复无正文清单；`EvaluationRunRepository` 只保存已完成运行。
  `evaluation_operations.evaluation_run` 保存冻结版本、门禁、路由摘要、观察计数、指标和时间，不含输入、回答、提示词或正文列。
- `EvaluationOperationsCoreAutoConfiguration` 只有在数据集仓储、运行仓储、私有内容、条目读取器、
  `EvaluationAssistantExecution` 和标识生成器同时存在时才注册运行服务。

## 可复现验证

```bash
./mvnw -q -pl backend/contexts/evaluation-operations -am test
python3 scripts/quality/backend_architecture_guard.py verify
git diff --check
```

以上命令通过。Maven 日志中仍有既有 Spring 上下文预期失败分支、MyBatis Mapper 扫描与 Testcontainers 警告；
没有测试失败。

## 后续

下一切片才可实现无正文运行 HTTP 投影与角色浏览器回归，再进入发布决定和授权；不得开放私有正文读取。
