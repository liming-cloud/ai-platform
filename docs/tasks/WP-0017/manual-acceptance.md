# WP-0017：本机部署与人工验收清单

本清单交付已完成 WP-0017 的人工操作入口，不推进 WP-0018 的公共契约或实现。核验日期为 2026-10-06；产品前沿仍为 WP-0018，生产契约提案仍为 reviewing，M6 未完成。

## 最新部署

四个应用均从干净的固定提交 `460a3c76375da312d1f030abbce6a0d82b2001cf` 按现有 Dockerfile 真实构建、更新并检查 OCI revision。交接文档的后续提交不替代该运行版本。

| 服务 | 实际 OCI revision | 实际镜像摘要 |
| --- | --- | --- |
| console-web | 460a3c76375da312d1f030abbce6a0d82b2001cf | sha256:8f457e1b9646830254cf3da24306a4773d7db3a65b9a2028fb750c228997dd38 |
| control-plane | 460a3c76375da312d1f030abbce6a0d82b2001cf | sha256:eefc7161bae00837ca6257a836d9021ffa8397f0bcf6c5e928e9f72c406b3a4c |
| query-runtime | 460a3c76375da312d1f030abbce6a0d82b2001cf | sha256:041591f5df811fbc4dcaddcfb785583eb935c7cfdd196dcb67daea5db116ea6b |
| ingestion-worker | 460a3c76375da312d1f030abbce6a0d82b2001cf | sha256:d9bc317230e42e6ff084b725706a4b4eb6d0f00904f3ea32b9920816dc45e19f |

7 个服务 running／healthy，4 个应用入口健康检查通过。PostgreSQL、Redis、MinIO 的容器 ID 保持不变，未重启 Docker、清理缓存、删除卷、重置账户或创建验收业务对象。

构建采用固定 revision label，应用更新使用现有 Compose 的 `up --detach --no-deps --no-build --wait`。更新前逐值比较 Compose 环境与运行容器，确认 `deploy/compose/.env.example` 与四个应用的现行配置一致，沿用该配置。`deploy/.env` 与当前后端的模型密钥、Endpoint 配置不同，本轮未将其替换到后端，不记录这些值。控制面、查询与摄取各自承担现有运行职责，未删除任何应用进程。

每次后续功能交付都应固定实际构建提交、核对四个应用的实际运行版本、执行相关部署验收后更新此清单。不能用仅包含文档变化的 HEAD 宣称产品有新功能，也不能用镜像标签代替健康与浏览器验证。

## 登录信息

| 身份 | 登录地址 | 登录名 | 租户编码 |
| --- | --- | --- | --- |
| 平台管理员 | [平台管理登录](http://127.0.0.1:13000/login) | admin | 不填写 |
| 租户管理员 | [租户管理员登录](http://127.0.0.1:13000/tenant/login) | tenant-admin | demo-tenant |

两套真实凭据已在上述部署通过实际登录、角色检查、刷新恢复与退出。准确密码按用户本轮要求交付到独立 0700 临时目录中的 0600 本地文件；不写入此文档、Git、日志或摘要清单，不回读数据库密码摘要。交付入口由本轮用户回复中的本地文件链接提供。

切换身份先退出；并行验证采用不同浏览器或独立无痕窗口，避免同源 Cookie 保持已有身份而触发“当前登录身份与入口不匹配”。当前租户管理员可验收独立登录及导航；普通成员与访问授权工作区尚未形成 WP-0018 生产闭环。

## 可人工操作的功能与对应原型

原型唯一权威为 OpenDesign Cloud 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`：[打开原项目](http://127.0.0.1:49175/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html)。下列原型链接直接使用原项目 raw 入口，已验证入口、脚本、样式和页面数据可用；没有原型副本。

原型使用页内“演示账户／演示密码说明”所提供的合成身份，不使用生产密码。首次打开管理页可能先要求原型登录，完成后再次点击目标链接即可。链接端口属于当前 OpenDesign Cloud 会话，服务重启后按上述项目重新取得入口，不能回退到独立副本。

生产详情链接对应已只读核实为 ACTIVE 的既有 `demo-tenant`，不是本轮新建的对象。可从租户目录选择其他自己建立的合成租户，再执行对应设置操作。

| 已交付功能 | 生产入口 | 原项目原型 | 人工操作与预期 |
| --- | --- | --- | --- |
| 平台管理员登录 | [登录](http://127.0.0.1:13000/login) | [console-login](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=console-login&state=success&review=0) | 真实登录、刷新恢复、退出；租户身份不能从此入口获得平台能力。 |
| 租户管理员登录与三入口切换 | [登录](http://127.0.0.1:13000/tenant/login) | [tenant-login](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-login&state=success&review=0) | 填写租户编码并登录；切换平台／租户／普通用户登录入口不继承密码或自动换身份。普通用户入口只证明导航。 |
| 租户目录 | [租户管理](http://127.0.0.1:13000/platform/tenants) | [platform-tenants](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenants&state=success&review=0) | 搜索、状态筛选、10／20／50／100 每页数量、跳页、刷新及前后退恢复；查看详情目标一致。 |
| 租户与初始管理员原子开户 | [建立新租户](http://127.0.0.1:13000/platform/tenants/new?step=1) | [platform-tenant-create](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-create&state=success&review=0) | 三步输入、上一步／下一步、校验及取消；最终确认一次建立租户和初始管理员，冲突整体拒绝，成功后进入新管理员登录入口。 |
| 租户概览 | [概览](http://127.0.0.1:13000/platform/tenants/2093674235049279490) | [platform-tenant-detail](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-detail&state=success&review=0) | 查看真实基本信息、安全保障和五页设置导航；租户管理员登录链接携带对应编码。 |
| 租户管理员治理 | [管理员](http://127.0.0.1:13000/platform/tenants/2093674235049279490/admins) | [platform-tenant-admins](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-admins&state=success&review=0) | 本租户目录、搜索、筛选、分页；重置密码、停用／恢复管理员资格；最后一名可用管理员不得停用，停用后仍归属原租户。 |
| 资源配额 | [资源配额](http://127.0.0.1:13000/platform/tenants/2093674235049279490/quotas) | [platform-tenant-quotas](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-quotas&state=success&review=0) | 五项配额编辑、取消、保存及刷新；低于核实用量或旧版本的保存拒绝。配额配置不代表后续知识接入已经交付。 |
| 可用功能上限 | [可用功能](http://127.0.0.1:13000/platform/tenants/2093674235049279490/features) | [platform-tenant-features](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-features&state=success&review=0) | 平台设置八项租户功能上限，取消不保存，独立保存后刷新保持；停用功能使目标旧租户会话失效。 |
| 登录安全策略 | [登录安全](http://127.0.0.1:13000/platform/tenants/2093674235049279490/security) | [platform-tenant-security](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-security&state=success&review=0) | 会话时长、失败阈值、锁定时长独立编辑／取消／保存；越界与旧版本拒绝，保存后目标旧会话失效。 |
| 平台账户完整操作 | [平台账户](http://127.0.0.1:13000/platform/accounts) | [platform-accounts](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-accounts&state=success&review=0) | 创建平台账户、首次登录、目录筛选与分页、重置其他账户密码、停用／恢复；当前操作者和最后可用管理员受保护，取消零写入。 |
| 平台固定角色 | [平台角色](http://127.0.0.1:13000/platform/roles) | [platform-roles](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-roles&state=success&review=0) | 唯一固定平台管理员模板、职责说明、“管理账户”导航；不新增或编辑自定义角色。 |

创建、最终保存、停用和重置会修改真实数据。人工写场景应使用专门的合成账户／租户；先确认取消与刷新，再提交。最后管理员保护可在仅一名可用管理员的租户验证拒绝，正常停用／恢复使用至少两名可用管理员的合成租户。功能、策略或密码变更后的旧会话失效属于预期，需要重新登录。

11 个入口对应 20 项已交付生产操作，唯一逐项状态来源仍为 [M6 实现矩阵](../milestones/M6/implementation-map.json)。真实业务写操作沿用[完整范围收口](evidence/WP-0017-adjusted-scope-closure-20261006.md)逐项绑定的原切片证据，本轮未重跑创建、保存、重置或停用验收，不将人工清单冒称新的写操作执行结果。

## 本轮实际验证与限制

- `make deploy-smoke`：四入口通过；Compose 七服务健康，四应用实际运行 revision 与构建提交相同。
- `make acceptance-work-package WP=WP-0017 SLICE=login`：15 项真实 Chrome 场景通过；`SLICE=login-entries`：18 项通过，业务写入 0。
- 11 个入口在 390／1024／1440px 的正常与 200% 文字视图，加 390px reduced-motion，共 77 组公共主题／标题／画布／侧栏／横向溢出对照通过，差异、业务写入、阻断业务请求和页面异常均为 0。不将公共指标等价为全部状态或整页像素一致。
- 原项目原型入口与脚本、样式、页面数据均 HTTP 200；前端代理的匿名会话、租户目录与查询助手接口均为预期 401。
- 数据库使用 `REPEATABLE READ READ ONLY`，11 组业务／迁移计数及 `transaction_read_only=on` 共 12 项；更新前后输出逐字一致，租户 70、身份 189、成员 88、平台绑定 101、迁移 74。只读取计数和上述验收租户的编码／状态，不读取秘密字段。
- `make architecture-guard`：48 项通过，后端依赖边界违规 0；既有五条手工分页债务仍归后续工作包。

人工查看初次移动端全页截图发现前区块显示异常。新增三次独立上下文的定向观测：截图前正常视口均完整显示“管理租户与模型供应”及说明；调用 `fullPage: true` 后图像才异常，而截图前后标题矩形、所有祖先的滚动、焦点与可见性均未变化。三次正常视口图片及 1024px／200% 配额图已经人工查看。证据支持截图调用触发绘制异常，尚未定位具体 Chromium 机制；保留全部异常原图和探针，不以重拍修改旧结果、不修改产品或原型、不新增视觉例外，不使用这些全页截图证明整页像素结论。内嵌 UI 自动化因执行器的符号链接工作区限制未启动，本轮功能与上述观测由正常受控启动的真实 Chrome 完成，不是产品验收失败或审批容量拒绝。

原型固定入口／操作提交 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`、共享移动布局 `47c4f6f31823617633ce0bac98074208781f5565`、目录 `d36c7f46d0187c0421d4989c12f601d5c190d73f`。已接受版本至构建提交的原型路径差异为零；仅沿用 ADR-0083 等已接受的限定差异。没有修订 OpenDesign Cloud 原型来迁就生产。

所有日志、诊断及截图位于独立 0700 目录 `/private/tmp/ai-platform-manual-acceptance-3mfdwsfa/`，日志和非秘密指标为 0600。主要事实入口为 `build-results.json`、`deployment-after.json`、`login-verification-results.json`、`database-verification.json`、`scope-visual-paint-final-metrics.json`、`login-paint-probe-metrics.json`。密码交付文件不进入任何摘要清单。此目录为本机临时证据，持久版本事实与复验方法保存在本清单；临时目录丢失时重新执行对应只读验证，不能补造结果。

| 非秘密产物 | SHA-256 |
| --- | --- |
| deployment-after.json | 9084048855857847bad9139a82ef96c30aba5c91f31191f8503f01ae5f15e0f6 |
| login-verification-results.json | 52f63e58eeab95d79d27f419eeda2f16f1d95e53d77a0bf74dfebeab7e2a0d22 |
| scope-visual-paint-final-metrics.json | 9280d1df4bcf94e09b590ec130b606a7d7c581a9392d581a647cf8de8579e435 |
| login-paint-probe-metrics.json | 601c85db9cd3bef8da07be9d1c0c6aa1d6f2a7eb3be0618b932723b38c38d3d5 |
| business-counts-before.txt／business-counts-after.txt | 5988690e22fca3d194b176128c74b87743f3ef8768c3e54dc833ce991cc591b5 |

Local Codex 显式加载工程助手 `wayfinder`、`diagnosing-bugs`、`code-review` 与偶发故障参考，复用 Charter、ROADMAP、STATE、WP-0018／0017 及其已接受 ADR。固定 diff 以构建提交为 base，仅涉及本清单和任务恢复入口，复核 11 个原型链接、实际生产路由、秘密边界与历史结果不改写。本轮采用内联评审，未绑定或推进持久 quality 节点，未创建 Runtime 或 StageRunResult，阶段结果校验不适用。CodeGraph 探索 `ConsoleApp`、`ConsoleRoute`、`formatConsoleRoute`、`buildBrowserLoginCredentials`、`LoginFrame`，确认人工链接和登录范围；图谱使交付限定于真实路由与局部截图观测，没有进行业务源码或非局部结构修改。

## 尚未交付的范围

- WP-0018：普通成员原子开户、账户、功能、助手授权与访问策略；当前仅生产契约提案，未接受、未部署这些新能力。
- WP-0019～0025：模型、知识接入、检索、助手、问答及后续产品闭环，不以历史可运行代码或原型模拟状态替代 M6 生产验收。
- WP-0027：平台概览及其完整依赖；原型不变，按 ADR-0092 在 WP-0025 后、WP-0026 前交付。
- WP-0026：M6 全量复验与 WP-0014 人工接受。M6 未完成。

治理与仓库恢复验证采用 `make project-status`、`make project-verify`、`make architecture-guard`、`make resume-context`：治理 77 项与 30 个任务验证通过，索引同步及状态恢复通过。本轮不改变任务前沿、实现矩阵、人工接受或历史完成事实。
