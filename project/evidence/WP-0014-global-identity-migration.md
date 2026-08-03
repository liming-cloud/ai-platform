# WP-0014：全局主体与成员资格迁移证据

## 范围

本切片落实 ADR-0042 的历史成员凭据拆分：全局凭据只保存于
`SubjectIdentity`，租户成员资格只引用 `SubjectId`、角色和授权修订。它不实现
平台会话、平台登录、租户生命周期编排、发件箱、HTTP 生命周期接口或浏览器角色任务。

## 影响与设计结论

代码图谱确认 `PersonAuthenticationService`、成员读取端口、MyBatis 映射、自动配置和
组织访问真实 PostgreSQL 测试是唯一认证链路。应用服务现在先验证全局主体，再以
`tenantCode + SubjectId` 读取有效成员资格；成员投影不再持有密码摘要。

版本化迁移先拒绝以下歧义历史数据：同一 `subject_id` 对应不同登录名、密码摘要或
状态，或同一登录名对应多个主体。无歧义数据才迁入 `subject_identity`，随后删除成员
表的登录名和密码摘要列，并将物理表原子改名为 `tenant_membership`。PostgreSQL 自动
更新既有跨上下文外键，未保留兼容视图或双写路径。

## TDD 红绿

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| 红灯 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=PersonAuthenticationServiceTest -Dsurefire.failIfNoSpecifiedTests=false` | `testCompile` 因缺少 `SubjectIdentityRepository`、`TenantMembershipRepository` 及无凭据成员投影构造器失败。 |
| 绿灯 | 同一命令 | 通过；验证先验主体凭据、成员资格恢复与统一失败语义。 |
| 迁移与仓储 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=GlobalSubjectIdentityMigrationTest,PostgresSubjectAuthenticationRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false` | 通过；验证历史迁移、歧义拒绝、主体禁用与双租户权限隔离。 |

## 回归命令

| 命令 | 结果 |
| --- | --- |
| `./mvnw -q -pl backend/contexts/organization-access -am test` | 通过。 |
| `./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am test -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false` | 通过；真实 PostgreSQL 全迁移、演示种子和 Bearer 登录契约通过。 |
| `python3 scripts/quality/backend_architecture_guard.py verify` | 通过；新认证 SQL 已登记，未使用 DDD 违规豁免。 |
| `python3 -m unittest scripts.quality.test_backend_architecture_guard` | 19 项通过。 |
| `git diff --check` | 通过。 |

## 六维复核

- 正确性：主体凭据唯一化、成员资格授权隔离和歧义迁移失败关闭由纯应用与真实 PostgreSQL 测试共同证明。
- 复杂度：认证流程保持两次受控读取，没有引入跨上下文认证总线或兼容双写。
- 冗余：密码摘要只在 `SubjectIdentity` 存储一次，成员投影、实体和 SQL 均已移除副本。
- 性能：登录使用登录名唯一查找与租户／主体索引条件；未引入全表扫描或正文读取。
- 架构：领域端口、应用编排和基础设施映射方向保持不变；复杂 SQL 仅作为安全投影登记。
- 测试质量：红灯、迁移成功／歧义反例、主体停用、租户隔离、自动配置和真实 HTTP 回归均覆盖。

## 后续缺口

平台授权事实、`PlatformRequestContext`、独立平台 Sa-Token 会话、`TenantProvisioning`
领域服务及生命周期原子提交仍未实施，继续受 ADR-0041／0042 和 WP-0014 范围约束。
