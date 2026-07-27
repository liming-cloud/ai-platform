# WP-0008 全仓雪花标识整改证据

## 结论

全仓 Java 主代码和测试代码中的 UUID 已清零：

- 索引构建租约和事务消息事件标识改为 64 位雪花标识。
- 对应 PostgreSQL 字段由 `UUID` 改为 `BIGINT`。
- 知识数据持久化适配器统一依赖 `EntityIdGenerator`，不自行生成标识。
- 控制面和在线查询的追踪标识统一由雪花标识派生。
- HTTP 追踪标识保持 `trc_` 前缀，不接受调用方提供的值。
- 在线查询错误入口与查询功能开关绑定，关闭查询功能时不产生额外运行依赖。
- 测试隔离标识也改用递增长整型，不保留 UUID 例外。

## 验证

绑定提交：

`b26a21070a610205c95324a5b758168ddc383a34`

已通过：

```text
./mvnw -q clean test-compile：通过
知识数据领域工作者及两组 PostgreSQL 持久化测试：通过
受影响平台、上下文和运行模块测试：查询运行最小上下文问题修复前发现 1 项真实失败
QueryRuntimeApplicationTest、QueryApiExceptionHandlerTest、CandidateScenarioTest 修复后回归：通过
TraceIdsTest：通过
make architecture-guard：通过
git diff --check：通过
```

架构债务变化：

- Java SQL 注解文件：保持为 0。
- Java UUID 文件：从 10 降至 0。
- 运行时非启动文件：从 47 降至 46。
- Sa-Token 越界文件：仍为 3，进入下一切片处理。
- 超预算包：仍为 8，进入后续目录整改切片处理。

## 未完成项

本证据只关闭全仓雪花标识问题，不代表 WP-0008 完成。安全适配归位、目录拆分、
领域增强和运行时启动包瘦身仍必须继续。
