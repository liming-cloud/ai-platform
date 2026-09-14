# WP-0017 原子开户真实部署验收

## 边界与版本

唯一切片：平台管理员填写租户信息、非默认配额和新管理员凭据，一次创建可用租户；刷新详情并使用新管理员登录。合同为 ADR-0080。独立管理员、配额编辑、功能、安全设置及普通会员不在本次交付内。

实现提交 `5844fd3ba2b5909161b704df5fc7e1ff026d4a0a`；验收脚本与视觉修正提交 `e008338e53bffd81def84f5de21dd0028e16c0f0`。前一记录为 `WP-0017-atomic-onboarding-build-blocked-20260914.md`，本记录不改写故障发生时的事实。视觉修正尚未部署，不能宣布切片最终通过。

## 恢复与部署

用户明确允许正常恢复 Docker。正常启动未恢复引擎后执行 Docker Desktop 正常重启，未清缓存、卷、数据或配置，也未再查询 BuildKit history。Maven 下载首次重试失败，经宿主和无卷临时容器验证官方包完整下载后，原命令构建成功；没有更换依赖版本。

仅更新 `control-plane` 和 `console-web`。PostgreSQL、Redis、MinIO 数据卷及其他服务保持。启动验证 70 个迁移，通过后应用 `V202609140001__create_tenant_quota.sql`，没有修复校验和或重置数据。

| 运行服务 | 镜像 ID | OCI revision |
| --- | --- | --- |
| control-plane | `sha256:287c22b27fc900992fb3bc18242e1f517f140d60109f2e71e679a9afdcbc4108` | `5844fd3ba2b5909161b704df5fc7e1ff026d4a0a` |
| console-web | `sha256:6609b55ec2c306f28235bad9251134f736aec512f35ec49eb8d9a699bf5d507a` | `5844fd3ba2b5909161b704df5fc7e1ff026d4a0a` |

7 个 Compose 服务均 healthy，`make deploy-smoke` 四个应用入口通过。不声称未重建的查询和接入服务已部署当前 HEAD。事前数据库备份保留在本机独占 0600 文件中，未复制入库。

## 实际验证

| 命令或观察 | 结果 |
| --- | --- |
| `./mvnw -q -pl backend/contexts/organization-access -am test` | 本轮重跑：85 组、239 项，0 失败、错误、跳过 |
| `./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false test` | 本轮真实 PostgreSQL/Redis/HTTP 23 项通过 |
| `make frontend-test` | 本轮 41 文件、209 项，类型检查、lint 和构建通过 |
| `node --test scripts/quality/work-package-onboarding.test.cjs scripts/quality/work-package-login.test.cjs` | 最终候选 25 项通过 |
| `python3 -m unittest scripts.quality.test_work_package_acceptance` | 7 项通过 |
| `make architecture-guard` | 28 项及前后端门禁通过；其他路径 15 处内联 SQL、3 处手工分页未冒称清零 |
| `make project-verify` | 35 项及 26 个工作包治理通过 |
| `SLICE=onboarding` | 首轮完整真实浏览器 8 场景通过；最终视觉修正后须复验 |
| `SLICE=login` | 使用本次新管理员复验 15 场景通过 |
| 当前部署只读事务 | 15 项限定对象事实检查全通过 |

浏览器使用现有锁定 Playwright 1.62.1、真实 localhost:13000 API，不拦截网络、不导入原型模拟状态。开户覆盖 390/1024/1440 的每一步正常及 200% 文字、标签/控件/帮助信息逻辑行对齐、双向 Tab、Enter、Space、reduced-motion、回退、刷新、取消、密码清除；再验证单次 201、非默认配额 UI/API 读回、重复编码/登录名 409、旧请求 400、缺少 CSRF 403、错误定位及新管理员无平台权限。

### 失败先复现

- 严格标签选择器未包含 Fluent 的必填 `*`，首次在“填写组织名称”超时，未发出开户请求。安全子步骤定位后以实际 DOM 证实原因；改为精确名称加可选单星号，增加相似名称、正则字符和秘密异常反例，未修改产品字段语义。
- 原对齐检查按错位后的坐标分组，可能将错位同行拆成单元素组；改用 DOM 顺序和已知列数。补齐三步键盘遍历、200% 样式恢复和控件包装器裁切反例。
- 目视发现详情状态 Badge 被通用直系 `div` 规则拉伸。旧部署三尺寸及两档字号的 6 个宽度反例全部失败；200% 时还测得文字高 28px、容器固定高 20px。修正为只让身份文字区域伸展，徽标按内容宽度及字号自增高；未改布局结构、公共组件或 API。正式脚本新增三尺寸详情宽高和实际字号倍率断言，测试从 23 增为 25。独立诊断中曾观察到未实际放大的 12px，保留倍率断言并等待最终部署反馈环，不用放宽断言消除该未决观察。
- 更新矩阵中已交付登录事实时，治理测试 fixture 沿用真实 `verifiedCommit`，导致其模拟 contracted/implemented 状态失败；fixture 先清理真实验证字段再构造反例，21 项矩阵测试通过，生产校验规则未放宽。

## 合成数据与事务复核

本轮第一份成功开户：租户 `2099390369895546882`、主体 `2099390369895546883`、编码/登录名 `wp17-a31b7efca365`。只保留该合成租户和管理员，不创建普通会员、不修改原有用户密码。凭据位于本机 0600 验收文件，不在日志、URL、截图或本记录中展示。

以这两个精确 ID 和冲突前缀 `unused-a31b7efca365`、`old-a31b7efca365`，在 `REPEATABLE READ READ ONLY` 事务中查询计数：

- 主体 ACTIVE、兼容外键锚点 DISABLED、生命周期 ACTIVE/version 1、ENABLED 管理员资格、固定角色绑定、version 0 配额、ACTIVE/version 1 发件箱事件各恰好一条。
- 配额精确为 4 / 1234 / 12500000000 / 75000000 / 2097152，与 UI 非默认提交一致。
- 新主体平台角色为 0；失败身份、锚点、生命周期及关联资格、角色、配额、发件箱残留全为 0。

该部署复核不替代故障注入或并发事务测试；后者由真实 PostgreSQL 的服务入口、发件箱失败回滚及唯一约束并发测试证明，未向现有部署注入故障。

## 复验方式与回退边界

```bash
NODE_PATH=/Users/lingou/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules \
FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE=/绝对路径/受控凭据.json \
FOUNDATION_ACCEPTANCE_OUTPUT_FILE=/绝对路径/尚不存在的新凭据.json \
make acceptance-work-package WP=WP-0017 SLICE=onboarding

NODE_PATH=/Users/lingou/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules \
FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE=/绝对路径/上述新凭据.json \
make acceptance-work-package WP=WP-0017 SLICE=login
```

凭据文件须由当前用户拥有且权限为 0600；开户禁止覆盖已有输出。测试数据保持可追溯，不自动删除。旧应用镜像及数据库备份保留；新增配额迁移已应用，不能用回退镜像等同于回退数据库，也不自动执行旧代码/新结构组合或破坏性恢复。

## 当前工具执行阻塞与下一步

构建视觉修正镜像的执行请求被自动审批服务拒绝，返回 `Selected model is at capacity`。该命令尚未启动，不是 Docker 构建、npm 或业务测试失败。只读复核固定提交无前端/部署差异、Dockerfile 为锁定静态前端、本机目标不涉及数据卷后重试，仍返回同一审批容量错误；未改用其他工具绕过。

当前运行镜像仍为上表的 `5844fd3ba…`，服务健康；源码和测试修正已提交，但不能把未部署的 `e008338e5…` 宣称已在线。工具执行恢复后，唯一前沿为：

1. 从固定候选构建前端镜像，OCI revision 使用完整提交；仅更新 `console-web`，保留后端及数据。
2. 使用新独占输出文件执行最终 `SLICE=onboarding`（新增后共 11 场景），保留目录真实进入、详情三尺寸正常/200%实际字号断言。如倍率仍失败，用一次只读 DOM 观察区分目标范围、样式写入与节点刷新，不猜测、不删断言。
3. 用新合成管理员复验 15 个登录场景，并只读核对该轮精确对象事实；不删除旧测试记录。
4. 固定 diff、最终目视、六维质量、Runtime 校验与治理提交闭合后，再推进下一独立切片。

## 工程助手与质量边界

运行环境 Codex；使用已安装 engineering-assistant 技能及 MCP，恢复 `run-5b4368307c3d4be7` 的 implement 流程，显式加载 engineering-assistant、tdd、diagnosing-bugs、code-review 及授权/安全 seam。控制面为 Charter、STATE、WP-0017、其活动 ADR、ADR-0080 和原型承接矩阵。未完成的部署与实际字号观察使质量节点不能通过，不提交全绿快照。

图谱调查延续原子开户的 TenantProvisioningService、SubjectIdentity、PostgresTenantProvisioningStore、ControlPlaneClient 及原影响分析；本轮聚焦 TenantOverview 和验收 seam。图谱在同名 TenantOverview 上返回其他上下文，最终以定向组件/CSS读取及真实 DOM 证实直系 `div` 的误匹配，修复仅限该卡片。独立评审补上逻辑同行、键盘和包装器裁切反例，并把浏览器结果与真实事务测试分开证明。

原型不变、截图只临时目视不入库、用户既有 5 项 fixture 暂存重命名不纳入提交。WP-0017 整包及 M6 人工门禁保持未完成。
