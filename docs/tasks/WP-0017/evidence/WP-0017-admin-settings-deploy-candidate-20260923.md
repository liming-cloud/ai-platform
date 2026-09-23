# WP-0017 管理员设置切片：部署候选与待验事项

本记录仅固定已发生的源码与部署事实，**不构成管理员设置切片完成证据**。WP-0017 整包与 M6 仍在进行中。

## 固定对象

| 对象 | 实际值 |
| --- | --- |
| 源码提交 | `02298633ba4ee38669bf46431494505d607ef8df` |
| control-plane 运行镜像 | `sha256:14215659a095012ed6e60a8cc10c222eafafdfba268f9c06802010cf307225d8`；OCI revision 为上述源码提交 |
| console-web 运行镜像 | `sha256:f595df68971699b36032109aea9168d38e3a95dc1f3216403a416e37b3138493`；OCI revision 为上述源码提交 |
| 原型 | OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，已验收提交 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`，入口 `platform-tenant-admins`；[项目入口](http://127.0.0.1:64093/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html) 仅在本机原型服务会话内有效 |

## 已完成的验证

- 完整后端 `./mvnw -q clean test` 通过。演示种子曾复用同一管理员覆盖两个租户，按单租户资格决定修正后重跑通过；先前的 Testcontainers SSL 建连失败单独重跑通过。
- `make frontend-test`：42 个文件、221 项通过，并完成类型检查、lint 和生产构建。
- `make architecture-guard`、`make project-status`、`make project-verify`、`make resume-context` 通过；管理员验收脚本语法及包装器 8 项测试通过。
- Compose 原有七项服务均健康后，使用固定源码提交分别构建并更新 control-plane、console-web。更新后七项服务均 healthy，两个运行容器的 OCI revision 精确匹配源码提交；`make deploy-smoke` 四入口通过。
- 部署后以 `REPEATABLE READ READ ONLY` 查询真实 PostgreSQL：一人拥有多份租户管理员资格的主体数为 `0`，`uk_oa_single_tenant_administrator_subject` 索引数为 `1`。该查询未读取凭据字段。

## 尚未完成的验收

当前没有可用的 0600 平台管理员验收凭据文件。旧验收文件已不存在，本机 bootstrap 凭据未通过真实平台登录。另一个本机变量的使用请求被自动审批拒绝，原因是它不是已批准的验收凭据来源；未绕过该拒绝。待用户提供现存有效的 0600 JSON 文件路径，或明确批准该变量作为本次本机验收来源后，才通过正常受控审批执行。

尚须以正式接口和独立 Chrome 运行 `make acceptance-work-package WP=WP-0017 SLICE=admins`，覆盖新合成对象、单租户归属、错租户、旧版本、CSRF、状态和密码重置、真实登录、分页及响应式；再按新对象标识只读核对数据库事实，执行固定原型的部署后视觉、键盘、焦点和 reduced-motion 对照。截图只保存于 `/private/tmp`，不得进入仓库。未完成这些步骤前，不更新切片完成事实，也不推送本候选提交。
