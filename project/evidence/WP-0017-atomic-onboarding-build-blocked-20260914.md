# WP-0017 原子开户实现与部署阻塞记录

## 边界与提交

生产合同：ADR-0080；基线 `111cab6056487c5dc5ddeeb3f639747851cfe0f5`，实现提交 `5844fd3ba2b5909161b704df5fc7e1ff026d4a0a`。

本切片已实现三步原子开户、独立租户概览与真实配额读取。新身份、管理员成员及角色、ACTIVE 租户、配额和事件同事务写入；未就绪与重复冲突失败关闭。前端不再选择预先建立的账户，密码不存入草稿，非秘密草稿绑定当前会话，失败定位可修正步骤，成功进入只读详情。配额编辑、登录策略、功能设置和普通会员不在本切片内。

保留用户已有 5 项 fixture 暂存重命名，未纳入实现提交。未修改原型及原型代码，未将截图带入仓库。

## 已通过的实际验证

| 命令 | 结果 |
| --- | --- |
| `./mvnw -q -pl backend/contexts/organization-access -am test` | organization-access 239 项，0 失败、错误或跳过，退出 0 |
| `./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false test` | 真实 PostgreSQL/Redis/HTTP 23 项，0 失败、错误或跳过 |
| `make frontend-test` | 41 文件 209 项，类型检查、lint、生产构建通过 |
| `node --test scripts/quality/work-package-onboarding.test.cjs scripts/quality/work-package-login.test.cjs` | 16 项通过 |
| `python3 -m unittest scripts.quality.test_work_package_acceptance` | 7 项通过 |
| `make architecture-guard` | 28 项及架构门禁通过 |
| `make project-verify` | 35 项及 26 个工作包治理验证通过 |
| `git diff --check` | 通过 |

红绿证据包括：HTTP 实际服务入口的事务自调用导致发件箱失败后残留租户，先红灯再移除重载；配额迁移缺表红灯后创建并验证回填；身份 SQL 异常可能携带密码摘要，先红灯再替换为无原始原因链的安全异常。同登录名/同编码的真实并发仅允许一个完整开户，失败者无孤儿身份或租户。

独立前后端代码评审未发现未解决的 P1/P2。评审发现验收脚本只检查 API、未检查详情实际显示的 P2，已补刷新后实际租户名称、编码与五项非默认配额 UI 断言；这些新增真实浏览器断言尚未执行，不能当作部署通过。

## 构建与环境故障

固定提交构建使用仓库两个 Dockerfile、锁定基础镜像和当前公开匿名 Docker 临时配置，未修改用户凭据助手或 Docker 配置。

- 前端镜像构建成功：`trusted-rag/console-web:foundation-v1`，镜像 ID `sha256:6609b55ec2c306f28235bad9251134f736aec512f35ec49eb8d9a699bf5d507a`，OCI revision 为实现提交。
- 后端构建在镜像内 Maven 打包期间因 BuildKit `Unavailable / EOF` 中断，退出 1；没有 Maven 编译失败证据。
- 2026-09-14 03:34:27 UTC，Docker VM 日志记录 `llbsolver.filterHistoryEvents` 空指针 panic，经过 `HistoryQueue.Listen` / `Controller.ListenBuildHistory`，进程退出 2。
- 子 Agent 为调查静默构建执行了只读 `docker buildx history ls`。其时间与调用栈吻合，**很可能由该诊断读取触发了 BuildKit 历史接口缺陷**，不是用户业务代码引起。该判断是基于时间和调用栈的归因推断；已停止历史查询。
- 随后的 Docker 请求返回 `Docker Desktop is unable to start`，状态为 stopped。没有执行引擎重启、清缓存、删卷、配置更改或 Compose 更新。

故障日志留在本机 `/private/tmp/wp0017-backend-build.log` 与 Docker VM `init.log`；后者仅检查错误及调用栈，不把用户环境或容器数据复制入库。数据库事前备份为本机独占 0600 文件，未输出或提交其内容。

本次尚未替换运行容器。故障前 control-plane、query-runtime、ingestion-worker 为原有镜像；console-web 仍为上一登录切片提交 `f9ba767bf2b20716b2c04e812b45e616fe037f53`。引擎停止后不可继续声称应用健康。未执行开户验收，因此没有新建本切片合成租户；原有数据和卷未清理。

## 恢复后的唯一前沿

恢复涉及全局 Docker 服务，需先取得用户确认，不能擅自重启其他工作负载。确认后先正常启动 Docker（不清数据/缓存），核对原服务与数据卷，再完成后端固定提交构建、仅更新 control-plane/console-web、检查迁移与健康，执行：

```bash
NODE_PATH=/Users/lingou/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules \
FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE=/绝对路径/受控平台凭据.json \
FOUNDATION_ACCEPTANCE_OUTPUT_FILE=/绝对路径/尚不存在的本次开户凭据.json \
make acceptance-work-package WP=WP-0017 SLICE=onboarding
```

随后使用创建的 0600 凭据文件复验 `SLICE=login`，完成三尺寸、文字放大、焦点、密码清除、非默认配额、冲突、CSRF 和新管理员登录的真实断言，以及临时目视检查、部署记录和最终固定 diff 质量门禁。本切片证据提交前不得实施下一切片。

当前仅为“实现已提交、部署受阻”。`lastVerifiedCommit` 保持上一已部署切片，WP-0017 保持未完成，不推进 WP-0018，也不触发 M6 完成结论。

## 工程助手和代码图谱

环境为 Codex，能力来自已安装 engineering-assistant 插件技能及 MCP。显式加载 engineering-assistant、tdd、code-review 及安全 seam，使用 Charter、STATE、WP-0017、其全部活动 ADR 与补充 ADR-0046/0064，运行 `run-5b4368307c3d4be7` 的 implement 流程。

图谱探索 TenantProvisioningService、TenantProvisioningResult、TenantLifecycle、SubjectIdentity、PostgresTenantProvisioningStore、TenantLifecycleReadPort、ControlPlaneClient、PlatformTenantLifecycleWorkspace 和 BrowserAuthenticationController。影响分析包括 TenantProvisioningService 12、SubjectIdentityMapper 13、TenantLifecycleReadPort 5、ControlPlaneClient 109 个符号；图谱不完整的结果以编译和定向文件检查补充。结论使实现保持既有认证切换语义、移除事务自调用和旧候选主体分支，并在身份持久化边界处理秘密异常。

固定差异摘要 `sha256:1be69d1dfee21d97c77beda215dafe3424d3efbbd0fda4402ef1faf29bbbf36a`。Runtime 的切片、红绿、实现阶段已校验通过，质量阶段因真实部署未完成而不能通过；不提交全绿 QualitySnapshot，不声称完整流程成功。
