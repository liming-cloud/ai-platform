# WP-0016：平台易用性与租户开通整改

## 结论与绑定

- 实现提交：`1d670bbce3a807bd92cc70228d332a8ae8f72584`。基线 HEAD 为 `4ebaa9a2a25e2f6821f24e41ac8951f97ff1e6e1`，起点 lastVerifiedCommit `c506b0f52f95f2ccfd9c5d249af737f3c91f9bf0` 为其祖先。
- 2026-09-07 用户再次人工拒绝。本轮只推进 WP-0016，可丢弃原型技术回归通过不代表人工通过，也不代表剩余功能已闭环。
- 原 OpenDesign 项目保存全部 91 个原型、代码、设计、夹具、脚本与资源文件，逐文件比较原始预览 SHA-256 一致。未新建平行项目，未保存截图到仓库。
- 不修改生产前后端、API、数据库或部署，不进入 WP-0017。渠道绑定、设备凭据和邮件邀请继续暂缓。用户其他任务暂存的 5 个 fixture 迁移保持原样并排除提交。
- 独立 Chromium 使用合成上下文；没有操作用户个人 Chrome，也未重置演示数据或自动恢复其手动停用的 qiao.demo。

## 用户问题与实际结果

| 问题 | 本轮结果 |
| --- | --- |
| 租户事实裸文字 | 共享键值网格、基本信息卡、安全保障清单；移动端单列 |
| 租户长页及混合保存 | 概览、管理员、资源配额、可用功能、登录安全五个独立页面；固定租户标识，保存/取消不混合字段 |
| 平台人员职责不清 | 平台目录只展示和创建平台管理员；租户管理员由对应租户页治理；认证版本保留内部失效机制，不再占日常列表列 |
| 非标准分页 | 真实夹具行分页、数字页码、10/20/50/100、总数/范围、跳转、筛选回首页、刷新和返回恢复；来源错误附行跟随所属行 |
| 新租户必须选已有人员 | 改“租户信息 → 配额设置 → 管理员账号”；直接填写账号与合成口令，组织、身份、成员资格原子写入，失败不留半成品 |
| 厂商类型与预填 | 尚未实现。研究技能要求先选择调查方式，已请求只读官方资料核对的确认；未凭记忆编造区域、地址或 rerank 兼容性 |
| 运行状态与长记录 | 业务链路步骤、影响、时间、复查次数分层；最新 5 条、每次加载更早 5 条、可聚焦有限高度时间线；异常顶部标记使用警告色 |
| 恢复布局 | 恢复点、隔离目标、生产边界与检查清单分层，阶段提示按真实选中/检查/运行/报告状态显示，不提供生产覆盖恢复 |
| 租户入口不可发现 | 新增 tenant-login，与平台/普通用户入口互相可发现；租户概览提供带租户编码的入口，新租户登录自己的空工作区 |

平台创建入口与人员身份模型不能混为一谈。ADR-0078 只替代 ADR-0042/0047 的原型开通方式，继续使用统一 SubjectIdentity、全局唯一登录名、固定三角色及平台/租户互斥，不建立第二套密码库。

## 红绿循环与测试调整

1. 原分页没有真实切页 seam；先增加目录边界和浏览器用例，缺模块/页码操作时失败。实现后验证 43 个租户的 10/20 条切页、末页、跳转越界、筛选和刷新。
2. 原事实块 computed display 为 block；35 条记录没有有限展示。失败用例促成共享网格、前 5 条和分批加载。首次修复中旧 display:grid 覆盖 hidden 的问题再次被用例捕获，补充专属隐藏规则后通过。
3. 先测试原子新建租户输入、平台创建资格及新入口拒绝。原实现仍要求 administratorAccountId、平台创建后未获得平台资格，测试失败；再以 prepareAccount 私有构造 seam 实现同一快照提交。
4. 浏览器先证明租户设置导航不存在，再拆五页并验证独立保存、刷新和对象上下文。新增租户从真实 UI 填表并登录自己的空工作区，不用测试后门代替此主链路。
5. 巡查发现模型分页容器位于表格外，实际读出 0 行；评测样本只渲染前 6 条。统一容器、移除硬截取，证明全部 104 条冻结样本可分页查看。
6. 目视发现“仍异常”顶部使用 success 色。针对真实 data-tone 先失败（expected warning，actual success），再修正状态色；用户新增处理记录不再预填一条像已执行的措施。
7. 三档宽度不仅验证前两步，还验证管理员账号步骤提交按钮直接可达、无横向溢出；1024/1440 同排密码输入顶部和高度一致。
8. 原有测试中的“平台先创建普通人员再加成员”不再符合已接受的职责拆分。成员授权及普通用户登录使用既有 account-lin 夹具；已轮换普通用户口令通过状态前置验证登录，不能解释成租户管理员已有普通成员重置 UI。相关新权限仍待确认，未悄悄扩大实施范围。

原有跨租户拒绝、跨页签会话失效、写入失败回滚、取消、刷新、键盘、secret 不入状态、索引/模型/助手/评测与问答专项继续运行，没有用删除安全断言换取通过。

## 机器与视觉证据

| 检查 | 结果 |
| --- | --- |
| 全部 Node | 141/141，0 失败、0 跳过 |
| 全部浏览器 | 139/139，0 失败、0 跳过 |
| 页面矩阵 | 73 × 7 × 3 = 1,533 场景；10,339 断言、14 阶段、运行异常 0 |
| M6 控件枚举 | 61 × 7 = 427 场景，6,448 实例；页面错配、未接线、运行异常均为 0 |
| 静态 | 3,491 断言；页面、fixture、URL 和账本一致 |
| OpenDesign lint | P0/P1/P2 均为 0；校验工具正常退出 |
| OpenDesign 保存 | 91 文件，通过 MCP write_file 保存并逐文件比较 raw 响应 SHA-256 |
| 截图与目视 | 矩阵 90 张仅在临时目录；另目视概览、恢复、运行状态及新建租户桌面/平板，三档表单由浏览器几何断言覆盖 |
| 项目治理 | project-status、project-verify 通过；14 个治理测试、20 个工作包；resume-context 为 6,759 字节，小于 12 KiB |

机器报告位于：

- `docs/prototypes/wp-0016/artifacts/usability-remediation-verification-20260907.json`
- `docs/prototypes/wp-0016/artifacts/opendesign-usability-save-20260907.json`
- `docs/prototypes/wp-0016/artifacts/static-verification.json`

控件枚举证明接线与页面身份，不穷举所有输入，也不等同于未决功能可用。矩阵包含 M7/M8 的布局检查，不因此授予这些生产能力。尚未运行真实模型或生产性能、安全验收。

从仓库根目录复现：

```sh
export PROTOTYPE_BASE_URL='http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html'
export NODE_PATH=/private/tmp/wp0016-playwright/node_modules
node --test docs/prototypes/wp-0016/scripts/*.test.mjs
node --test --test-concurrency=2 docs/prototypes/wp-0016/scripts/*.test.cjs
node docs/prototypes/wp-0016/scripts/verify-static.mjs
PROTOTYPE_ARTIFACT_DIR=/private/tmp/wp0016-usability-matrix node docs/prototypes/wp-0016/scripts/verify-and-capture.cjs
PROTOTYPE_CONTROL_REPORT=/private/tmp/wp0016-usability-controls.json node docs/prototypes/wp-0016/scripts/enumerate-controls.cjs
OPENDESIGN_SAVE_REPORT=/private/tmp/wp0016-usability-save.json node docs/prototypes/wp-0016/scripts/save-opendesign.cjs
ELECTRON_RUN_AS_NODE=1 OD_SIDECAR_IPC_PATH=/tmp/open-design/ipc/release-stable/daemon.sock '/Applications/Open Design.app/Contents/Frameworks/Open Design Helper.app/Contents/MacOS/Open Design Helper' '/Applications/Open Design.app/Contents/Resources/app/prebundled/daemon/daemon-cli.mjs' lint '/Volumes/slm-ssd/projects/ai-project/ai-platform/docs/prototypes/wp-0016/index.html' --fail-on p1 --json
make project-status
make project-verify
make resume-context
```

OpenDesign 0.21.1，项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`。MCP get_project 与保存脚本核对同一项目目录，未调用系统 /usr/bin/od。Playwright 1.62.1 使用临时依赖目录及独立 Chromium。首次静态复跑忘记设置预览地址导致 4176 连接失败，修正为项目 raw 地址后才记录通过；环境错误不记为产品红灯。

## 工程助手、代码图谱与质量判断

- 运行环境：Codex。能力来源：已安装 engineering-assistant 2.0.0-rc.1+codex.local-20260802-015922 的限定技能。
- 路由：engineering-assistant:wayfinder 维持当前前沿，engineering-assistant:prototype 约束可丢弃原型，engineering-assistant:tdd 驱动公共 seam 红绿循环，engineering-assistant:decision-interview 处理职责边界。
- 显式加载 engineering-assistant:engineering-assistant、wayfinder、prototype（ui/logic 引用）、tdd（security-seams）、decision-interview、research。research 要求先确认调查方式，因此厂商调查暂停；本轮未执行 Exa 或厂商网络请求，不继承历史搜索数量为本轮证据。
- 已使用控制面：CHARTER、STATE、WP-0016、12 个活动 ADR、ADR-0042/0047/0073、ACTION-LEDGER、页面清单和 fixtures。没有启动 Hermes 或平行代理。
- 未产生 StageRunResult、持久运行流或 QualitySnapshot，阶段结果校验不适用；浏览器脚本的 stages 只是测试分组，不冒充插件阶段产物。

图谱探索覆盖 renderTenantDetail、renderTenantCreate、renderPlatformAccounts、renderPlatformHealth、renderRecovery、renderConsoleLogin、renderInventoryPage 和 bindDirectorySearch；读取 createTenant、createAccount、loginConsole、setAccountStatus。影响分析分别检查 renderTenantDetail（4）、createTenant（6）、createAccount（7）、loginConsole（16 个汇总符号）。图谱包含生产同名对象，但本轮仅修改原型与测试；这使账户/登录调整同步到调用方和回归助手，而不是复制一条兼容分支或修改生产链路。

| 维度 | 判断 |
| --- | --- |
| 正确性 | 原子开通、重复输入、角色边界、独立保存与分页恢复均有正反例；机器通过范围明确 |
| 复杂度 | 复用私有身份构造与共享分页，不建立第二身份体系；五页按任务拆分，不混合保存 |
| 冗余 | 去掉已触及的已有账号选择流程与评测硬截取；未做全仓清理。旧 accountScope helper 及 tenantSetup.administratorAccountId 草稿字段当前无新 UI 调用，按用户约束仅登记；由 WP-0016 原型负责人在成员账号边界确认后决定移除，退出条件为新流程不再依赖这两项并经回归 |
| 性能 | 仅合成小型原型行切页和有限高度时间线；不声称客户端分页可替代生产服务端分页或满足 RAG 性能指标 |
| 架构 | 接受 ADR-0078 后才改开通语义；安全不变量与生产边界保持，代码图谱限定影响 |
| 测试质量 | 先失败再实现，139 浏览器覆盖关键用户链路与 141 状态测试互补；全页矩阵/接线检查不替代人工 |

## 真实未决与下一前沿

1. 是否允许租户管理员在本租户内直接创建、重置、停用普通成员账号，不管理其他管理员、不跨租户；未确认前不新增这项权限。
2. 厂商类型和自动预填尚未实现。等待用户选择只读官方资料调查方式，再核对地址、区域和能力，尤其不能将非标准 rerank 伪装成统一 OpenAI 接口。
3. 完成两项后重新保存原项目并复验，再请求三角色人工验收。当前 ACTION-LEDGER 仍登记 2 个范围内未闭环操作组，readyForFullAcceptance=false，人工拒绝保持有效。

可先复核已改页面，但不能据此标记 WP-0016 完成。用户曾手动停用 qiao.demo，应由平台管理员在“租户管理 → 云杉服务示例组织 → 管理员 → 更多 → 恢复账户”明确恢复后再测试该身份，不能自动解除停用。
