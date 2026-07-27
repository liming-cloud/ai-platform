# WP-0008 助手与查询标识整改证据

## 结论

助手、助手版本、查询运行和查询事件已经统一使用 64 位雪花标识：

- 领域值对象内部使用 `long`，并拒绝非正数和旧 UUID 格式。
- HTTP 与前端契约使用十进制字符串。
- 助手、查询、幂等、引用和事件表使用 `BIGINT`。
- 助手服务与查询服务统一依赖 `EntityIdGenerator`，不直接生成 UUID。
- 助手版本与查询运行之间的数据库外键类型一致。

## 验证

绑定提交：

`7d6d9e9202830971a3f2a01d1db989c0334df37b`

已通过：

```text
助手与查询领域、应用和 PostgreSQL 仓储定向回归：通过
控制面助手接口与查询运行控制器回归：通过
PostgreSQL 空库 14 个 Flyway 迁移：通过
query-web 查询客户端：1 个测试文件、5 个测试通过
./mvnw -q clean test-compile：通过
make architecture-guard：通过
git diff --check：通过
```

架构债务变化：

- Java SQL 注解文件：保持为 0。
- 主代码 UUID 文件：从 25 降至 10。
- 助手与可信查询主代码 UUID 引用：清零。

其余 UUID 位于索引构建租约、事件标识和追踪标识，继续由 WP-0008 后续切片清理。
