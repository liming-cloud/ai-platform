# WP-0016 原型完整性与人工验收准备检查

## 结论与范围

**当前不具备整套原型人工验收条件。** 评审对象为 `553098099b8ba64928e48f0bde13e90e5e412233`。现有页面与专项测试可运行，但补充的跨页、角色、输入保留和异常入口调查复现了 8 类 P1 阻断，另有 2 类 P2 问题。2026-09-03 的人工拒绝继续有效，不能仅因原有自动测试全绿而改为“待最终签字”。

2026-09-05 用户明确暂缓渠道绑定、设备凭据、邮件邀请。这三项排除出本次验收，不删掉既有设计、不标记实现，也不影响后续重新纳入。M6 本次范围为其余 56 个页面任务；M7/M8 的另外 10 页仍按既有工作包只核对信息架构和视觉前置设计。

原型内容与视觉仍统一由 OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 管理。MCP 返回的 `resolvedDir` 与仓库原型目录一致，全部浏览器检查直接访问该项目原始预览端点。本轮未修改原型业务实现；只登记范围、调查脚本、机器报告、账本与控制面。截图仅在系统临时目录，未入库。

## 已有验证与额外调查

| 检查 | 当前结果 | 能支持的结论 |
| --- | --- | --- |
| Node 状态与合同测试 | 100/100 | 已声明的局部状态规则通过 |
| 原有浏览器专项 | 101/101 | 已声明的局部交互通过 |
| 静态检查 | 3,177 项断言通过 | 页面、夹具、静态合同一致 |
| 全页面浏览器矩阵 | 68 页 × 7 状态 × 3 尺寸，1,428 场景、9,642 断言、14 阶段通过 | 已声明布局与有限操作通过；包含暂缓页的回归，不要求其本次业务验收 |
| OpenDesign lint | P0/P1/P2 均为 0 | 静态设计检查无发现 |
| 新增验收调查 | 66 个页面入口、首轮 380 个可见控件线索、13 项行为探针，探针执行异常与页面运行异常均为 0 | 实际复现下列缺陷；探针执行成功不等于产品通过 |
| 登记后复验 | Node 100/100、静态 3,177 项、调查脚本语法检查、`git diff --check`、治理单测 14/14 与 `make project-verify` 通过 | 证据和控制面一致；不改变业务调查失败结论 |

普通用户助手目录和聊天页在初轮无登录扫描中按预期返回登录；随后通过真实原型登录与创建会话补查。没有把这些跳转误算成对应页面成功。事件监听记录只用于发现疑点，缺陷结论均结合实际点击、输入、刷新结果和源码。

临时视觉核对查看了建立租户、创建助手、结构化数据桌面页和普通用户聊天移动页。建立租户三步布局与创建助手输入对齐已改善；这是局部视觉观察，不是全平台美观度认可。全矩阵生成的 85 张核对图均未复制到项目。

## 已复现的验收阻断

<a id="A01"></a>
### A01 · P1 · 管理端登录与真实原型角色未接通

- 在 `console-login` 输入平台演示名、普通成员名或不存在的登录名，配合错误合成口令点击登录，均进入 `platform-overview`。
- 使用普通用户有效演示账户登录后，直接进入 `knowledge-directory`，页面仍为 `success`，显示管理目录，并能创建“普通成员创建的验收库”。
- 源码：`full-platform.js:23` 的登录主操作读取 `nextScreen`；`prototype-state.js:3905` 的 `authorizeScreen` 对管理页只检查租户功能；`prototype.js:2253` 的创建操作固定使用 `TENANT_ADMIN_ACTOR_ID`。
- 退出条件：人员入口按选定合成身份恢复三角色；无效登录有纠错反馈；普通成员直接路由和写操作均拒绝，测试必须从浏览器登录到操作贯通。该发现是原型角色模拟缺陷，不据此推断生产后端漏洞。

<a id="A02"></a>
### A02 · P1 · 关闭历史功能导致新会话也无法使用

- 在成员页保留“选择助手”“发起与继续对话”，关闭“本人历史”，保存并重新登录普通用户。选择已授权助手后进入 `conversation` 的 `denied` 状态，无法开始新的问答。
- 源码：`prototype-state.js:4369` 开始的 `queryProjection` 从需要 `ownHistory` 的历史集合查找当前会话，并再次要求 `ownHistory`；新的当前会话和历史恢复没有分开。
- 退出条件：历史权限关闭后仍能使用已授权的新会话；历史目录与历史恢复继续拒绝。若产品要强制依赖，配置界面必须明确且阻止矛盾组合，不能保存后才拒绝主任务。

<a id="A03"></a>
### A03 · P1 · 知识库选择未进入可恢复 URL，跨标签页发生对象漂移

- 标签页 A 从“员工服务手册”进入数据来源；标签页 B 选择“客户支持知识”；刷新 A，同一个 URL 的内容由“员工服务手册 / 4 个来源”变为“客户支持知识 / 0 个来源”。
- 此时打开知识库概览，又显示固定的“员工服务手册 / idx-v11”，与当前选择不一致。
- 源码：`prototype.js:220` 的 `navigate` 只给会话写对象 ID；知识库选择只写共享原型状态。`page-fixtures.json:24` 的知识库概览仍是固定夹具，通用渲染不消费当前知识库。
- 退出条件：知识库、来源、索引等详情 URL 固定对象；刷新、前进后退、跨标签页不会改换对象；相关页面从同一状态投影读取名称与指标。

<a id="A04"></a>
### A04 · P1 · 目录控件有外观但没有查询或行操作结果

- 知识库目录输入不存在的名称后仍显示两个知识库，选择“已就绪”仍显示索引中的知识库。
- 审计目录输入不存在的记录、改变状态筛选，三行内容均不变；第二行“查看”点击后无跳转、弹层或反馈。
- 源码：`prototype.js:3350` 的 `bindDirectorySearch` 只匹配 `data-search-text`，知识库行使用 `data-directory-row`；`full-platform.js:125` 的通用搜索/筛选无相应处理，`:114` 只给首行写导航。
- 退出条件：所有在本次范围内显示的查询、筛选与行操作都有结果及无结果状态，页码/筛选能恢复；逐目录回归，不能只测试第一个对象。

<a id="A05"></a>
### A05 · P1 · 主操作文案与真实结果不一致

- `processing-quality` 的“重试失败项”只跳到 `sync-result`，没有改变失败来源状态。
- `quick-publication` 选择“返回修复来源”并输入新批次后点“开始发布”，仍进入固定 `idx-v12` 详情，输入和质量选择均未被采用。
- `audit-records` 的“导出受控记录”进入审计事件详情，未产生下载或导出任务。
- 源码：`full-platform.js:40` 按 `fixture.nextScreen` 将这些动作统一实现为导航；对应夹具为 `page-fixtures.json:28`、`:32`、`:58`。
- 退出条件：执行动作必须更新当前任务的合成状态并提供成功/失败/取消结果；仅导航的控件使用明确的导航名称，不得借跳转声明重试、发布或导出已完成。

<a id="A06"></a>
### A06 · P1 · 空态无法启动主要创建任务

- `platform-accounts&state=empty` 点击“创建人员账户”无响应。
- `knowledge-directory&state=empty` 点击“创建知识库”无响应，打开的弹窗数量为 0。
- 源码：`full-platform.js:68` 的空态按钮落到通用对话框入口，而平台账户页不渲染该对话框；`prototype.js:1135` 在 generic 状态省略知识库创建对话框。
- 退出条件：空目录和有数据目录共用有效创建入口；取消、校验失败、创建成功后的目录更新都可从空态完成。

<a id="A07"></a>
### A07 · P1 · 未保存编辑在预检时被静默丢弃

- 结构化数据页把 `floor` 的业务含义改成“楼层说明更新”，直接点击“运行抽样预检”，输入恢复为“楼层”；随后发布成功的仍是旧值。
- 源码：`prototype.js:3999` 的预检和 `:4005` 的发布只消费 store 中的已保存版本，操作前未检查表单是否有未保存变更，随后重新渲染覆盖输入。
- 退出条件：预检/发布明确消费当前编辑版本，或阻止操作并引导保存；用户输入不得静默丢失，预检结果必须与发布内容绑定。

<a id="A08"></a>
### A08 · P1 · 对象范围登记后无法继续知识接入

- 登记“验收对象来源”成功，在登记列表显示待探测；该行没有后续操作，进入数据来源目录找不到该对象。
- 合成状态里登记数为 1，知识库来源中匹配数为 0。
- 源码：`prototype-state.js:3166` 只写 `dataGovernance.objectScopeRegistrations`；`prototype.js:1145` 的数据来源目录只读当前知识库 `sources`。
- 退出条件：登记后能进入同一来源详情，继续探测、同步、质量检查、失败恢复，并衔接该知识库的索引流程。上一轮“登记闭环”结论只适用于局部登记操作，不代表接入流程闭环。

## 补充问题与证据缺口

<a id="A09"></a>
### A09 · P2 · 管理端账户菜单无行为，部分术语仍需统一

在租户概览点击右上角“账户菜单”，页面、对话框与状态均不变。共享账户菜单未形成身份查看与退出任务。创建助手页正文仍有“创建稳定助手”“模型修订”等技术措辞，应按原人工意见统一为自然业务名称。保留 7/5/7 参数不能替代操作和文案验收。

<a id="A10"></a>
### A10 · P2 · 原有账本与测试覆盖不足以作为完整性门禁

旧账本只登记 118 项，将 33 项导航与实际业务完成分开，但汇总“3 个未闭环”没有涵盖导航文案误导、空态、编辑未保存、跨页和权限组合等缺口。`business-closure.test.mjs:302` 与 `verify-static.mjs:219` 只要求仍存在未完成项，不能证明哪些操作已经可用。原浏览器矩阵在本轮 13 项探针确认缺陷时仍全部通过。

账本已增补到 131 项：17 个范围内未闭环控件、3 个用户暂缓项；A01–A08 为 8 类阻断。这仍是已确认最低欠账，不宣称所有控件已穷举。实际失败与技术矩阵通过分别记录，不能互相覆盖。

六维判断：行为正确性与健壮性未通过；共享渲染及固定操作者造成上下文耦合；通用文案/模板虽减少重复但未承担真实任务；生产性能不在本次合成原型测量范围；测试质量存在上述漏检。没有足够依据批准整套原型。

## 修复顺序与重新提验条件

1. 先收口 A01/A02：三角色从登录到直接路由、写操作与对话功能组合完整演示。
2. 再收口 A03/A08：对象 URL、页面投影和来源生命周期贯通，验证跨标签页与刷新。
3. 修复 A04/A05/A06/A07/A09：查询与行操作、主操作真实结果、空态创建、编辑保留和账户菜单。
4. 按实际任务补齐账本与回归：主路径、取消、错误、无权限、刷新、前后退、空数据与多对象；再做三档宽度、键盘、缩放、减少动态效果和自然中文复核。
5. 所有本次范围内可见控件都有清晰结果，8 类阻断归零并提交可复现证据后，再邀请用户进行整套人工验收。可以提前查看局部页面，但不将局部意见当作整体验收通过。

## 可复现命令与产物

使用当前 OpenDesign 预览地址设置 `PROTOTYPE_BASE_URL`；端口变化时由 `get_project` 重新取得，不猜测新地址。运行时复用 Playwright 1.62.1 的隔离 Chromium。

```sh
node --test docs/prototypes/wp-0016/scripts/*.test.mjs
PROTOTYPE_BASE_URL=http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html NODE_PATH=/private/tmp/wp0016-playwright/node_modules node --test docs/prototypes/wp-0016/scripts/*.test.cjs
PROTOTYPE_BASE_URL=http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html NODE_PATH=/private/tmp/wp0016-playwright/node_modules node docs/prototypes/wp-0016/scripts/audit-acceptance.cjs
PROTOTYPE_BASE_URL=http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html node docs/prototypes/wp-0016/scripts/verify-static.mjs
PROTOTYPE_BASE_URL=http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html PROTOTYPE_ARTIFACT_DIR=/private/tmp/wp0016-acceptance-20260905-full NODE_PATH=/private/tmp/wp0016-playwright/node_modules node docs/prototypes/wp-0016/scripts/verify-and-capture.cjs
make project-status
make project-verify
```

机器摘要：`docs/prototypes/wp-0016/artifacts/acceptance-preflight-20260905.json`。调查脚本默认把详细结果写入系统临时目录；其退出码只表示调查执行情况，不是验收门禁。原型后续修复后应把相应探针改成期望行为断言。

最初在沙箱内启动 Chromium 被 macOS 进程限制阻止，101 个浏览器用例未执行；取得所需进程权限后，直接访问 OpenDesign 重跑，101/101 通过。没有将环境启动失败记作产品缺陷。

## 插件与源码证据

当前环境为 Codex，使用已安装插件的 `engineering-assistant:code-review`，显式读取该技能并按固定评审对象、原需求、失败路径和六维结论执行。控制面包括 Charter、STATE、WP-0016、该工作包 11 份活动 ADR、设计规范、页面清单、操作账本与当前报告。

代码图谱探索 `renderInventoryPage`、`primaryAction`、`renderList`、`bindInteractions`、`renderScreen`，并定向读取 `authorizeScreen`、`queryProjection`、`bindDirectorySearch`、`renderKnowledgeDirectory`、`renderKnowledgeSources`、`bindKnowledgeManagement`、`bindStructuredObjectGovernance`、`registerObjectStorageScope`、`renderTenantMembers` 和 `renderMemberFeatureEditor`。这些源码把问题定位到固定操作者、通用导航、状态投影与持久状态分离，未开展生产重构，因而未执行非局部变更影响分析。

本轮未使用 Runtime flow，未产生 `StageRunResult` 或 Runtime `QualitySnapshot`，相应阶段结果校验不适用。审计报告不是人工批准，不改变 WP-0016 的进行中状态。
