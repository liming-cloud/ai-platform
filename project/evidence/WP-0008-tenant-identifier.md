# WP-0008 全局租户雪花标识整改证据

## 结论

提交 `0f375f40a436a38ab66eaeb8362bb8dcd7ac4f62` 已将全局 `TenantId` 从 UUID
改为正的 64 位长整型，并同步改造六个限界上下文、向量存储扩展、运行入口测试、
数据库租户列、演示数据与验收脚本。

这只是全局标识迁移的第二个纵向切片。知识空间、数据源、索引、模型、助手和查询等
业务实体仍有 UUID，不能把本证据解释为“主代码 UUID 已全部清零”。

## 领域与契约

- `TenantId` 构造时拒绝零和负数。
- `TenantId.parse` 只接受十进制正整数，拒绝旧 UUID 格式。
- `toString()` 固定输出十进制，HTTP、会话和脚本不传递 Java 数字对象。
- 向量批次和候选查询使用强类型 `TenantId`，不再退化为裸 `long` 或 UUID。
- 租户、主体和成员三类跨上下文标识现已统一为雪花长整型语义。

## 持久化与演示数据

- 组织、知识、模型、助手和可信查询迁移脚本中的 `tenant_id` 均改为 `BIGINT`。
- 八个 Mapper 的租户参数改为 `long`，Mapper XML 不再使用 UUID 文本转换。
- PostgreSQL 行实体使用 `Long` 保存租户标识，仓储边界负责恢复 `TenantId`。
- pgvector 写入和检索使用 JDBC `setLong`，索引租约校验比较强类型租户标识。
- 门店和校园演示租户固定为十进制 64 位标识。
- 查询验收、配额、运营、评测和候选池性能脚本均使用十进制租户标识。

系统采用绿地重建，不提供 UUID 租户数据迁移、兼容列或双写。

## 红绿与回归

`TenantIdTest` 先定义十进制解析、正数约束和旧 UUID 拒绝行为；旧实现无法编译，
完成迁移后全量验证通过。迁移中发现的强类型比较和 JDBC 参数错误均由真实
PostgreSQL 测试复现并修复。

| 命令 | 结果 |
| --- | --- |
| `./mvnw -q -DskipTests test-compile` | 主代码与测试代码编译通过 |
| `make backend-test` | 219 项通过，0 失败、0 错误、0 跳过 |
| `make frontend-test` | 28 项通过，类型检查、代码检查和双应用构建通过 |
| `make demo-fixtures-verify` | 9 项校验通过，2 个场景和 8 个检索用例有效 |
| `make architecture-guard` | SQL 0，UUID 主代码文件从 119 降至 115 |
| `make project-verify` | 通过，当前前沿为 WP-0008 |
| `git diff --check` | 通过 |

执行环境为 macOS、JDK 26.0.2、Maven Wrapper 3.9.16、PostgreSQL、Redis 与
MinIO Testcontainers。

## 下一步

按聚合边界继续清除业务 UUID。先迁移知识空间、数据源和索引标识，再迁移模型、
助手、查询与评测标识；每个切片必须同时覆盖领域值对象、数据库、公共契约、前端
字符串和真实数据库测试。
