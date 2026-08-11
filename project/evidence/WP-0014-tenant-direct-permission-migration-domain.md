# WP-0014 历史直接权限迁移领域等价约束

## 范围

本次仅固定 ADR-0062 的纯领域迁移不变量，不读取数据库、不接收 HTTP 输入、不建立角色、也不删除
`membership_permission`。迁移清单中的实际成员、目标角色和批准撤销项仍由租户运营负责人提供。

`TenantDirectPermissionMigration` 只接受已经由应用层锁定并投影的权限事实：历史直接权限、既有角色
权限、清单指定角色产生的迁移后角色权限，以及批准撤销项。它要求迁移后集合精确等于“历史直接权限与
既有角色权限的并集，扣除批准撤销项”；同时禁止丢失既有角色权限、未批准收权和额外扩权。只有启用成员
可以生成下一权限修订。

## TDD

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| 红灯 | `./mvnw -q -pl backend/contexts/organization-access -am -Dtest=TenantDirectPermissionMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test` | 测试编译失败：`TenantDirectPermissionMigration` 尚不存在。 |
| 绿灯 | 同一命令 | 4 项通过：批准撤销的等价替换、缺失映射、未批准撤销、额外权限和停用成员均被覆盖。 |

## 清单应用编排 TDD

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| 红灯 | `./mvnw -q -pl backend/contexts/organization-access -am -Dtest=TenantDirectPermissionMigrationApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 测试编译失败：清单、受锁迁移事实、迁移端口与应用服务均不存在。 |
| 绿灯 | `./mvnw -q -pl backend/contexts/organization-access -am -Dtest=TenantDirectPermissionMigrationTest,TenantDirectPermissionMigrationApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 6 项通过：成功路径只在受锁快照一致后提交并撤销目标成员会话；直接权限摘要或成员权限修订漂移时不提交、不撤销。 |

清单是受控部署输入，不是 HTTP、浏览器或 token 契约。端口把角色解析、锁定读取和原子写入隔离到专用
`directpermissionmigration` 包，避免突破既有 `authorization.port.out` 的包规模门禁。

## 六维质量复核

| 维度 | 结论 | 依据 |
| --- | --- | --- |
| 正确性 | 通过 | 领域等价测试与应用层快照测试共同固定完整等价和下一权限修订。 |
| 复杂度 | 有界 | 服务只比较已加载的集合，不引入角色推断、仓储或通用规则引擎。 |
| 冗余 | 通过 | 既有角色绑定、会话撤销和持久化契约保持复用，未复制其实现。 |
| 性能 | 有界 | 单成员集合拷贝与比较；没有扫描全租户成员或权限表。 |
| 架构 | 通过 | 领域代码只依赖领域模型和 JDK；迁移端口收敛在专用应用包，`make architecture-guard` 通过。 |
| 测试质量 | 通过 | 保存两轮实际缺符号红灯，并用同一测试绿灯；PostgreSQL、会话撤销、HTTP 与浏览器证据属于后续清单驱动事务切片，尚未宣称完成。 |

## 后续前置条件

应用服务必须先获得逐成员迁移清单，才能安全实现锁定读取、角色解析、审计、原子删除与会话撤销。受信任
TLS 的正式浏览器证据继续按 ADR-0048 延期；本记录不改变 M6 和 M7 门禁。
