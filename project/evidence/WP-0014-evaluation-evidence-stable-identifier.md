# WP-0014：评测证据稳定标识红绿

## 范围

落实 ADR-0056 方案 A：私有 JSON Lines 的 `expectedEvidenceStableIds` 只能使用知识索引 `chunkId` 的 64 位小写 SHA-256。此切片不开放正文读取、评测执行、发布决定或 HTTP 新接口。

## 红灯

```bash
./mvnw -q -pl backend/contexts/evaluation-operations -am test -Dtest=LocalEvaluationDatasetContentStagingAdapterTest -Dsurefire.failIfNoSpecifiedTests=false
```

在新增 `evidence-001` 反例后失败：期望 `IllegalArgumentException`，但暂存器接受了任意字符串。

## 绿灯

```bash
./mvnw -q -pl backend/contexts/evaluation-operations -am test -Dtest=LocalEvaluationDatasetContentStagingAdapterTest -Dsurefire.failIfNoSpecifiedTests=false
./mvnw -q -pl backend/contexts/evaluation-operations -am test
```

两条命令均通过。非 `chunkId` 值在写入私有对象前失败关闭；格式正确但未知的值仍只在后续冻结运行中作为未命中处理，避免登记阶段读取或泄漏索引内容。

## 六维检查

- 正确性：定向红绿与全模块测试覆盖允许和拒绝路径。
- 复杂度：仅在现有 JSON Lines 校验器增加一个格式守卫。
- 冗余：复用 `IndexCandidate` 已有的 64 位 SHA-256 语法，不新增编码器。
- 性能：逐项正则匹配，随既有流式校验线性执行。
- 架构：数据集继续只由 evaluation-operations 私有暂存，未读取知识表。
- 测试质量：反例直接对应 ADR-0056 的任意字符串缺陷，未依赖 mock 调用顺序。
