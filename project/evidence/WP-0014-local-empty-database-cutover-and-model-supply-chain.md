# WP-0014 本地空库认证切换与模型供应链续证

## 范围

落实 ADR-0064 的纯本地空库认证切换，并在同一当前 Compose 环境续证 ADR-0057、ADR-0058 的租户登录、角色导航与确定性供应商探测。不改变共享或生产认证语义，不记录任何口令、会话、API Key 或密钥材料。

## 红绿与自动验证

红：`./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am -Dtest=LocalProfileConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test` 先证明 Compose 未注入模型凭据版本与版本化主密钥；`LocalLifecycleAuthenticationCutoverMigrationTest` 先证明空库未建立切换事实。

绿：

```text
./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am -Dtest=LocalProfileConfigurationTest,LocalLifecycleAuthenticationCutoverMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test
docker compose --env-file deploy/.env -f deploy/compose/compose.yml config --quiet
```

均通过。测试覆盖空库只建立一条切换事实、已有租户时不写入，以及 local Compose 必须提供开发密钥版本和 `M2_V1` 版本化主密钥。

## 真实基础设施与 HTTP

清理后真实 PostgreSQL 显示 `tenant_count=0`、`cutover_count=1`；控制面 Flyway 成功执行 local repeatable migration。受控根管理员的同源浏览器会话 HTTP 登录返回 `204`，仅记录已脱敏 cookie 名称。控制面重建后健康检查为 `200`，容器内确认开发密钥版本与版本化主密钥均已存在。

## 浏览器回归

使用 `http://localhost:13000`：

1. 平台根管理员建立并激活本地租户；目录显示服务端分页总数 `1`。
2. 激活租户以同一主体登录成功，成员目录显示该租户唯一初始管理员和服务端分页。
3. 页面授予“模型管理员”后旧租户会话立即失效；重新登录后导航投影出现“模型”。
4. 模型配置读取页从先前的 `404` 恢复为 0 条安全分页结果；不会显示密钥材料。
5. 建立确定性开发供应商连接，凭据状态只显示“已配置”；`memory://` 连接探测通过，模型发现历史显示成功、2 个候选与 `PT24H` 有效期摘要。

另建的非 `memory` 确定性连接保持“待验证”，证明探测失败不会被页面伪装为可用。

## 结论与剩余门禁

ADR-0064 空库认证阻断、模型配置读取 `404` 和本地凭据加密缺口均已解除。`AuthenticationApiTest` 原有断言把“不得进入租户生命周期任务”错误等同于“没有任何平台工作区”；已按 ADR-0061、ADR-0057 与已存在的无数据 `/platform` 产品契约修正为：运营员只得到 `PLATFORM_TENANT_GOVERNANCE` 工作区类别、`PLATFORM_OPERATOR` 显示角色与 `platform-session:access`，且不含租户治理或账户治理能力。真实本地浏览器与 HTTP 均复核了该边界。M6 仍进行中：首份发布的 PostgreSQL／MinIO／HTTP／浏览器续证、全量自动验收和人工评审尚未完成；不得进入 M7。
