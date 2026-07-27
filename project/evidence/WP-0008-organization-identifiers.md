# WP-0008 主体与成员雪花标识整改证据

## 结论

提交 `a6415c6e2761501952e9dfdfbaf22163862b8ac3` 已将组织权限上下文的
`SubjectId` 和 `MembershipId` 从 UUID 改为正的 64 位长整型，并同步完成会话、
数据库外键、审计投影、演示数据和跨上下文创建人字段改造。

这只是全局标识迁移的第一个纵向切片。租户、知识、模型、助手、查询等实体标识仍是
UUID，不能把本证据解释为“UUID 已全部清零”。

## 领域与契约

- `SubjectId`、`MembershipId` 在构造时拒绝零和负数。
- 两个值对象只从十进制字符串解析，不接受旧 UUID 格式。
- `toString()` 固定输出十进制，避免在接口层暴露 Java 数字对象。
- 查询审计事实使用 `MembershipId`，HTTP 审计视图输出字符串，前端不接收超出
  JavaScript 安全整数范围的数字。

## 持久化

- `membership_credential.membership_id` 和 `subject_id` 改为 `BIGINT`。
- `membership_permission.membership_id` 改为 `BIGINT`，复合外键保持有效。
- 知识、助手上下文的 `created_by` 改为 `BIGINT`。
- 可信查询的 `requested_by` 改为 `BIGINT`，跨上下文成员外键保持有效。
- `MembershipCredentialEntity` 声明 MyBatis-Plus `ASSIGN_ID`；当前适配器只读取成员
  事实，后续成员写入用例必须通过 `platform-persistence` 的统一生成器装配。

系统采用绿地重建，不提供 UUID 数据迁移或兼容列。

## Sa-Token 会话

- 登录标识、成员标识和主体标识均按十进制字符串写入服务端会话。
- 恢复会话时重新构造强类型值对象，并校验会话成员与 Sa-Token 登录标识一致。
- 非法数字、旧 UUID、缺失字段或登录标识不一致均拒绝恢复可信上下文。

## 红绿与回归

新增标识测试先因值对象仍接收 UUID 而编译失败；完成实现后通过。

| 命令 | 结果 |
| --- | --- |
| `./mvnw -q -pl backend/contexts/organization-access -am -Dtest=OrganizationIdentifierTest -Dsurefire.failIfNoSpecifiedTests=false test` | 改造前按预期编译失败，改造后通过 |
| `./mvnw -q -pl backend/contexts/organization-access -am test` | 组织权限领域、会话、真实 PostgreSQL 测试通过 |
| `make backend-test` | 218 项通过，0 失败、0 错误、0 跳过 |
| `make frontend-test` | 28 项通过，类型检查、代码检查和双应用构建通过 |
| `make demo-fixtures-verify` | 9 项校验通过，2 个场景和 8 个检索用例有效 |
| `make architecture-guard` | SQL 0，UUID 主代码文件从 122 降至 119 |
| `make project-verify` | 通过，当前前沿为 WP-0008 |
| `git diff --check` | 通过 |

执行环境为 macOS、JDK 26.0.2、Maven Wrapper 3.9.16、PostgreSQL 与 Redis
Testcontainers。

## 下一步

迁移全局 `TenantId`。由于它被所有限界上下文使用，该切片必须同时改造数据库租户列、
可信上下文、Mapper 参数、公共接口和容器演示数据，不能在单一上下文内局部替换。
