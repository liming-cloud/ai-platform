# WP-0014：平台租户治理授权与独立会话证据

## 范围

本切片实施 ADR-0043 已接受的最小平台治理认证边界：已启用全局主体取得
`tenant-governance:write` 授权后，才可签发不含租户和成员资格的平台会话。
它不提供授权管理接口、租户正文读取、租户生命周期持久化或运营工作台。

## 代码图谱影响

- `PlatformSessionGateway` 的影响面限定于 Sa-Token 适配器、平台上下文参数解析器、自动配置和平台认证应用服务；没有租户业务上下文依赖它。
- `AuthenticationController` 仅负责原有租户会话。新增 `PlatformAuthenticationController` 使用独立路径和 `PlatformSessionGateway`，避免把租户令牌解释为平台令牌。
- `PlatformAccessGrantRepository` 只在组织权限上下文读取已启用主体的最小治理授权投影；不形成授权管理聚合或跨上下文可变对象。

## TDD 红绿

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| 红灯 | `./mvnw -q -pl backend/platform/platform-security/platform-security-core -am test -Dtest=PlatformRequestContextTest -Dsurefire.failIfNoSpecifiedTests=false` | 缺少 `PlatformRequestContext` 与平台治理权限常量，无法编译。 |
| 绿灯 | 同一命令 | 通过。 |
| 红灯 | `./mvnw -q -pl backend/platform/platform-security/platform-security-satoken -am test -Dtest=SaTokenPlatformSessionGatewayTest -Dsurefire.failIfNoSpecifiedTests=false` | 缺少独立平台 Sa-Token 会话网关，无法编译。 |
| 绿灯 | 同一命令 | 通过。 |
| 红灯 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=PlatformAuthenticationServiceTest -Dsurefire.failIfNoSpecifiedTests=false` | 缺少平台登录命令、授权读取投影与认证服务，无法编译。 |
| 绿灯 | 同一命令 | 通过。 |
| 红灯 | `./mvnw -q -pl backend/contexts/organization-access -am test -Dtest=OrganizationAccessAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false` | 新平台授权 Mapper 未被测试夹具提供，自动配置无法启动。 |
| 绿灯 | 同一命令 | 注入 Mapper 与独立平台会话后通过，并确认平台认证服务与入口装配。 |
| 红灯 | `./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am test -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false` | 平台登录路径未注册，返回资源不存在响应。 |
| 绿灯 | 同一命令 | 真实 PostgreSQL、Redis、Flyway 和 Sa-Token 环境通过平台登录与令牌隔离回归。 |

## 已验证事实

- `PlatformRequestContext` 与 `PlatformAccessGrant` 都是不可变 `ValueObject`；前者没有租户或成员资格字段，后者必须包含 `tenant-governance:write`，不能以 `model-platform:write` 替代。
- `PlatformSessionGateway` 使用独立 Sa-Token login type。真实 HTTP 回归证明 `platform-admin` 可签发平台令牌，原租户令牌不能由平台网关解析。
- 真实 PostgreSQL 迁移创建平台授权与权限集合；授权投影同时检查主体和授权启用状态。禁用／不存在主体或授权不会被读取为可签发平台会话的事实。
- `/api/v1/platform/auth/sessions` 只接受登录名和可清除密码数组，不接收租户、成员资格、权限或令牌参数；令牌由服务端签发。
- `complex-sql-allowlist.json` 登记两条 `SECURITY_PROJECTION` XML 查询，是复杂 SQL 的强制审计台账，不是 DDD 违规豁免。

## 六维评估

| 维度 | 结论 | 依据 |
| --- | --- | --- |
| 正确性 | 通过 | 平台授权、独立会话、自动配置和 HTTP 回归均通过。 |
| 安全性 | 通过 | 没有租户／成员资格请求参数；密码使用 `char[]` 并清除；租户令牌跨命名空间失败关闭。 |
| 复杂度 | 可接受 | 仅新增独立平台网关和最小认证用例；未抽象为会混淆范围的共享会话类型。 |
| 冗余 | 可接受 | Sa-Token 状态保存与租户会话有意分别实现，避免权限范围相互转换。 |
| 性能 | 可接受 | 登录只读取主体、授权和最小权限集合；未增加正文或跨租户扫描。 |
| 测试质量 | 通过 | 包含纯领域反例、应用服务、真实 PostgreSQL、自动配置及真实 HTTP／Redis／Sa-Token 回归。 |

## 回归

```bash
./mvnw -q -pl backend/platform/platform-security/platform-security-satoken -am test
./mvnw -q -pl backend/contexts/organization-access -am test
./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am test -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false
python3 scripts/quality/backend_architecture_guard.py verify
```

已通过。运行日志包含既有 MyBatis 测试夹具警告、JDK 类数据共享警告和 Netty macOS DNS 提示，均不影响测试结果。

## 后续缺口

租户生命周期持久化及原子建立仍需历史租户初始管理员的显式 `tenantId → SubjectId` 映射决策；不得从现有成员资格数据猜测或回填。M6 尚未完成，M7／M8 未激活。
