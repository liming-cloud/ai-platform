# WP-0014：本地身份重置只读预检

## 目的与边界

本记录落实 ADR-0063 的删除前只读确认要求。目标仅为当前本机 Compose 项目
`trusted-rag-foundation-v1` 的 PostgreSQL 服务；未创建、停止、重建或删除任何 Docker 容器、卷、对象，
也未执行任何数据删除。

## 当前事实

容器内连接显示数据库为 `foundation_v1`、角色为 `foundation_app`。读取
`organization_access` schema 得到以下数量：

| 事实表 | 数量 |
| --- | ---: |
| `tenant` | 3 |
| `tenant_membership` | 4 |
| `subject_identity` | 3 |
| `tenant_membership_role_binding` | 3 |
| `tenant_custom_role_binding` | 0 |
| `membership_permission` | 23 |
| `platform_access_permission` | 3 |
| `tenant_lifecycle` | 3 |

当前主体为 `admin`、`platform-admin`、`platform-operator`；租户为两个演示租户和一个遗留的
`m6-browser-*` 租户。该清单只用于证明范围，未记录口令、摘要、会话令牌或连接凭据。

## 约束与判定

PostgreSQL 外键表明，不能把“仅清除现有成员”实现为直接删除 `tenant_membership` 与
`subject_identity`：

- `organization_access.tenant_lifecycle.initial_administrator_subject_id` 引用 `subject_identity`；
- `organization_access.tenant_membership` 引用 `subject_identity` 与 `tenant`；
- `trusted_query.query_run(tenant_id, requested_by)` 引用 `tenant_membership`；
- 平台授权事实也引用 `subject_identity`。

因此，若目标严格为“仅初始化平台超级管理员”，必须由人工确认同时删除本地租户及其关联事实；若保留租户，
则必须保留每个租户的初始管理员主体，不能宣称唯一主体基线。两种结果有实质不同，实施不得猜测。

## 浏览器与 HTTP 复核

`http://localhost:13000` 的统一登录页已实际提交一次现有本地租户管理员登录。页面只显示统一失败提示；
同一旧控制面的无 Cookie 登录接口也返回 `401`，故失败发生在身份验证前，不能归因于浏览器 Cookie。

浏览器对 localhost 的 `Secure` Cookie 有本地开发例外；本次不降低 `Secure`、`HttpOnly`、
`SameSite=Lax` 或 `__Host-` 属性。依据：[MDN：Using HTTP cookies](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Cookies)。

## 复现命令

以下命令只读，不输出数据库口令：

```bash
docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml \
  exec -T postgres sh -lc \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -F "|" \
    -c "SELECT current_database(), current_user;"'

docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml \
  exec -T postgres sh -lc \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At \
    -c "SELECT count(*) FROM organization_access.tenant_membership" \
    -c "SELECT count(*) FROM organization_access.subject_identity" \
    -c "SELECT count(*) FROM organization_access.tenant" \
    -c "SELECT count(*) FROM organization_access.tenant_lifecycle"'
```

## 后续条件

人工答复以下两项后，才可执行 ADR-0063 的本地重置：

1. 确认是否同时清理当前本地租户及关联业务事实；
2. 提供唯一平台超级管理员的登录名和通过未纳入版本控制的本地配置交付的初始口令。
