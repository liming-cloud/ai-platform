# WP-0014 本地平台管理员与数据完全重制证据

## 结果

- ADR-0071 已落地：纯本地空库固定初始化唯一 `admin`，公开开发密码为 `admin@123`。
- 当前 Compose 的 PostgreSQL 已从可恢复备份后完全重建；Redis 旧会话已清空；MinIO、Git 文件及非本地
  环境未修改。
- 重建后数据库仅有 `admin`、平台授权修订、唯一活动 `PLATFORM_ADMINISTRATOR` 绑定和本地认证切换
  四类初始化事实；租户、成员和全部产品业务表均为零。

## 控制面与影响范围

- 仓库：`/Volumes/slm-ssd/projects/ai-project/ai-platform`
- 分支：`codex/trusted-rag-foundation-v1`
- 实施前 HEAD：`4a6ceef457348aa7b82c43b82632bd767eaa0c0b`
- 当前工作包：`WP-0014`，M6 保持进行中。
- 代码图谱先定位了 `PostgresPlatformAccountStore`、`SaTokenPlatformSessionGateway` 与主体会话关系，确认
  固定 `subject_id=1` 重建时必须同步清理 Redis；修改最终收敛到 local repeatable migration、对应测试和
  公开 Compose 示例，没有修改账户存储、会话实现或非本地 profile。由于没有公共接口或非局部源码重构，
  本切片不需要额外 impact 分析。

## 可恢复备份

执行破坏性操作前，在精确容器 `trusted-rag-foundation-v1-postgres-1` 内生成 PostgreSQL 18.4 custom dump，
复制到：

```text
/private/tmp/ai-platform-before-admin-reset-20260828.dump
```

- 权限：`0600`
- `pg_restore --list`：通过，379 条 TOC
- SHA-256：`abc213ae5c71f6804fc7a08c57826cdb04f2c2f02a8d61b9b82cf41e37397f48`
- 恢复边界：该文件可恢复重制前 PostgreSQL 数据；Redis 旧会话不恢复，MinIO 原本未删除。

## 红绿 TDD

使用 Codex 当前运行环境提供的 `engineering-assistant:tdd`，并显式加载其
`references/security-seams.md`。选择的公共 seam 是 local Flyway 初始化与真实同源登录；未生成
`StageRunResult`，因此阶段结果校验器不适用。

红灯命令：

```bash
JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home \
./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -f backend/pom.xml \
  -pl bootstrap/control-plane-bootstrap -am \
  -Dtest=LocalPlatformAdministratorInitializationMigrationTest,LocalProfileConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：退出码 `1`；7 项中 2 项失败，分别证明旧 SQL 查不到 `admin`、公开 Compose 配置仍是旧凭据；标准
迁移、已有租户和已有主体三条失败关闭反例均通过。

绿灯执行同一命令，结果：退出码 `0`，7 项全部通过。首次绿灯运行还发现不符合适配器契约的盐长度与
Base64 形式，未放宽校验，而是按 `Pbkdf2PasswordAdapter` 的 16 字节盐、URL-safe Base64 和 600000 次
PBKDF2-HMAC-SHA256 契约重新生成摘要后转绿。

关联回归：

```bash
JAVA_HOME=/Users/lingou/Library/Java/JavaVirtualMachines/openjdk-26.0.2/Contents/Home \
./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -f backend/pom.xml \
  -pl bootstrap/control-plane-bootstrap -am test
```

结果：退出码 `0`；覆盖真实 PostgreSQL／pgvector、Redis、MinIO、Spring 装配和 HTTP 测试。

## Compose 重建与事实核对

1. 新 `control-plane` 镜像构建成功，manifest list 为
   `sha256:88c3e584ff75ac048a4a05296950f39ec56f79d0d11fe7ba011cc1e89331bf7d`。
2. 停止 `console-web`、`control-plane`、`query-runtime`、`ingestion-worker` 后，精确执行
   `DROP DATABASE foundation_v1 WITH (FORCE)` 与同名数据库重建；返回 `DROP DATABASE`、
   `CREATE DATABASE`。
3. 当前项目 Redis 执行 `FLUSHDB` 返回 `OK`。重建后、登录前 `DBSIZE=0`；验收会话注销后再次为 `0`。
4. 新镜像启动后 Flyway 从空库完成迁移；`console-web`、`control-plane`、`ingestion-worker`、MinIO、
   PostgreSQL、`query-runtime` 和 Redis 七个服务均为 healthy。
5. 对除 Flyway 历史外的所有用户表逐表执行精确 `count(*)`，只有以下非零事实：

```text
organization_access.subject_identity = 1
organization_access.platform_subject_authorization = 1
organization_access.platform_role_binding = 1
organization_access.tenant_lifecycle_authentication_cutover = 1
```

主体查询为唯一 `admin / ACTIVE / version=0`；活动平台管理员绑定为 1；旧 `platform.root`、租户和成员均为 0。

## 同源 HTTP 与安全回归

入口：`http://localhost:13000`。验收请求先取得服务端 CSRF Cookie，并显式发送正确 `Origin`、
`Sec-Fetch-Site: same-origin`、Cookie 和 `X-CSRF-Token`；保持 `Secure` Cookie 配置不变。

| 场景 | 结果 |
| --- | --- |
| `admin` 与新口令登录 | `204`，响应体 0 字节 |
| 恢复新会话 | `200`，角色仅 `PLATFORM_ADMINISTRATOR` |
| 旧账号与旧口令 | `401` |
| 旧账号与新口令 | `401` |
| 新账号与旧口令 | `401` |
| 注销验收会话 | `204`，响应体 0 字节 |

三条失败响应均使用同一稳定错误类别、通用中文消息和空 `details`，不区分账号是否存在。数据库不保存
明文；成功登录响应不含 token，浏览器会话只由 HttpOnly Cookie 承载。

## 六维检查

- 正确性：新账号、密码摘要、平台授权和固定角色原子建立，真实 HTTP 可登录。
- 边界：标准 migration、已有租户、已有主体均不初始化或覆盖本地管理员。
- 错误：旧账号与旧密码三种组合统一 `401`，没有账户枚举差异。
- 可观察性：Compose 健康、HTTP 状态和数据库事实可复核，不在日志或证据记录 Cookie 与摘要。
- 安全：破坏前备份、PBKDF2 契约、CSRF、HttpOnly 会话和 Redis 旧会话清理均已验证。
- 性能：只验证本地迁移与启动完成，不声明生产吞吐或延迟改进。

## 未扩展范围

本次没有删除 MinIO、修改生产密码策略、迁移非本地数据、改变公共 HTTP 契约、执行 M6 人工验收或激活
M7。Chrome 视觉与响应式续证不属于本凭据／数据重制切片。
