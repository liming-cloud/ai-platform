# 整个平台原型人工验收

## 当前候选

原型与对应代码统一保存在 OpenDesign 项目「WP-0016 可信 RAG 高保真原型」。73 页已登记；本次交互验收范围包含 61 个 M6 页面任务，渠道绑定、设备凭据和邮件邀请暂缓。M7/M8 另外 10 页保留视觉与信息架构，不声称运维和设备任务全部实现。

本轮人工拒绝仍有效。普通成员账号职责和厂商预填尚待确认，不能宣称整套可验收。验收顺序为“功能能用 → 功能好用”，7/5/7 不变。

## 从这里开始

| 角色 | 入口 | 合成登录信息 |
| --- | --- | --- |
| 平台管理员 | [平台管理登录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=console-login&state=success) | platform.admin.demo / demo-only |
| 租户管理员 | [租户管理员登录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-login&state=success) | spruce-demo / qiao.demo / demo-only |
| 普通用户 | [普通用户登录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=login&state=success) | spruce-demo / shen.demo / demo-only |

仅使用公开演示账户和口令，不输入真实凭据或资料。角色切换要先退出；页面查询参数不能授予角色。表格口令只适用于未修改过的内置账户，创建或重置过的账户必须用刚设置的演示口令。

旧浏览器如显示“检测到旧版演示数据”，点击“修复演示登录 → 确认修复”，再用原登录名和 `demo-only` 登录。此修复保留租户、知识库、会话和草稿，只补齐没有登录配置且未被重置过的内置账户；不会重新启用停用账户、恢复角色或自动登录。取消不写入，保存失败可以重试。平台账户在平台账户页重置，租户管理员在对应租户的管理员页重置；普通成员凭据管理入口待确认。

您之前手动停用的 `qiao.demo` 不会被自动恢复。需要继续测试该账户时，请由平台管理员进入“租户管理 → 云杉服务示例组织 → 管理员 → 更多 → 恢复账户”，再从租户管理员入口登录。

只有确实要从头复核时，才由您主动在原型场景审查器重置全部数据。它与“修复演示登录”是两个不同操作，重置全部数据会清除该浏览器的合成演示内容。

## 三条主路径

1. 平台：创建人员账户 → 设置管理员资格 → 三步创建租户 → 编辑非默认配额、登录策略和可用功能 → 保存并刷新 → 退出后用该管理员选择新组织登录。新组织应为空，不出现云杉示例内容。
2. 租户：知识库 → 粘贴或上传 → 扫描/解析/分块/索引 → 失败重试 → 更新文档或单文件重建 → 候选评测与确认激活 → 模型配置 → 创建助手并冻结版本 → 测试、评测、发布 → 添加成员并授权助手。另走访问策略新版本、结构预检及审计导出。
3. 普通用户：登录 → 选择已授权助手 → 新会话 → 提问、增量回答与引用 → 切换历史继续提问 → 中断与恢复 → 退出重登。撤销历史、引用或助手权限后再访问旧链接，应按权限拒绝。

## 每页统一检查

- 正常操作有准确结果，保存后刷新仍在；取消不写入。
- 搜索与状态筛选组合生效；点击不同对象进入不同详情，刷新与跨页签不换对象。
- 编辑后旧预检/预览/模拟失效，不得发布旧值；失败有安全重试，不能用导航代替完成。
- 在右下“原型场景”检查 loading、empty、error、denied、partial、disabled、success；拒绝态不泄露对象事实。
- 核对 390、1024、1440 宽度：输入顶部对齐、按钮可达、单页面最多一个普通分页，无横向溢出。
- 用 Tab/Shift+Tab、Enter、Escape 完成表单和弹窗；开启 reduced-motion 后不依赖动画获得结果。
- 如不通过，请记录“页面 + 身份/组织 + 操作 + 预期 + 实际”，不要只按截图判断闭环。

## 全页面入口

以下表格由当前页面清单展开；待补厂商预填与成员账号职责不标记完成。

### 共享访问（M6）

| 页面 | 唯一任务 | 验收范围 |
| --- | --- | --- |
| [平台管理登录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=console-login&state=success) | 验证平台账户并进入平台管理 | 逐项操作与七态复核 |
| [租户管理员登录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-login&state=success) | 验证租户管理员并进入指定组织 | 逐项操作与七态复核 |

### 平台治理（M6）

| 页面 | 唯一任务 | 验收范围 |
| --- | --- | --- |
| [平台运营摘要](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-overview&state=success) | 处理全平台脱敏运营风险 | 逐项操作与七态复核 |
| [租户目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenants&state=success) | 查找并管理租户生命周期 | 逐项操作与七态复核 |
| [建立新租户](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-create&state=success) | 分三步建立租户并设置资源配额 | 逐项操作与七态复核 |
| [租户概览](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-detail&state=success) | 租户概览独立查看与维护 | 逐项操作与七态复核 |
| [租户管理员](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-admins&state=success) | 租户管理员独立查看与维护 | 逐项操作与七态复核 |
| [资源配额](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-quotas&state=success) | 资源配额独立查看与维护 | 逐项操作与七态复核 |
| [可用功能](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-features&state=success) | 可用功能独立查看与维护 | 逐项操作与七态复核 |
| [登录安全](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-security&state=success) | 登录安全独立查看与维护 | 逐项操作与七态复核 |
| [平台账户](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-accounts&state=success) | 创建和管理平台管理员账户 | 逐项操作与七态复核 |
| [平台固定角色](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-roles&state=success) | 理解平台固定角色及最小披露边界 | 逐项操作与七态复核 |
| [平台模型连接](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connections&state=success) | 管理平台模型供应商连接 | 包含本轮未决项 |
| [供应商连接探测](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connection-detail&state=success) | 探测并发布单个供应商连接版本 | 逐项操作与七态复核 |
| [平台模型部署](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-deployments&state=success) | 管理平台可授权模型部署 | 逐项操作与七态复核 |
| [平台部署发布](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-deployment-detail&state=success) | 验证并发布单个模型部署 | 逐项操作与七态复核 |

### 租户访问与知识（M6）

| 页面 | 唯一任务 | 验收范围 |
| --- | --- | --- |
| [租户运营概览](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-overview&state=success) | 处理当前租户最重要的阻断项 | 逐项操作与七态复核 |
| [成员与对话功能](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-members&state=success) | 管理普通成员生命周期与对话功能 | 包含本轮未决项 |
| [租户固定角色](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-roles&state=success) | 理解租户固定角色能力边界 | 逐项操作与七态复核 |
| [访问策略目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=access-policies&state=success) | 查找并发布资源访问策略 | 逐项操作与七态复核 |
| [访问策略版本](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=access-policy-detail&state=success) | 编辑、预览并发布一个访问策略版本 | 逐项操作与七态复核 |
| [设备凭据目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=device-credentials&state=success) | 管理设备凭据、助手与现场范围 | 用户暂缓 |
| [知识库目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=knowledge-directory&state=success) | 查找或创建租户知识库 | 逐项操作与七态复核 |
| [知识库概览](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=knowledge-overview&state=success) | 判断知识库当前可用性和唯一下一动作 | 逐项操作与七态复核 |
| [数据来源](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=knowledge-sources&state=success) | 管理当前知识库的数据来源 | 逐项操作与七态复核 |
| [添加数据来源](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=source-add&state=success) | 通过粘贴或本地文件登记一个来源 | 逐项操作与七态复核 |
| [文档详情](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=source-detail&state=success) | 查看文档版本、处理结果和安全重试点 | 逐项操作与七态复核 |
| [处理与质量](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=processing-quality&state=success) | 处理解析、分块和索引质量问题 | 逐项操作与七态复核 |
| [同步运行结果](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=sync-result&state=success) | 审查一次同步的成功、失败、重复和越权结果 | 逐项操作与七态复核 |

### 知识处理与索引（M6）

| 页面 | 唯一任务 | 验收范围 |
| --- | --- | --- |
| [结构化数据版本](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=dataset-schema&state=success) | 冻结 CSV 字段规则与敏感级别 | 逐项操作与七态复核 |
| [对象范围登记](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=object-storage-scope&state=success) | 把平台签发的文本范围登记为知识来源 | 逐项操作与七态复核 |
| [首份知识快速发布](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=quick-publication&state=success) | 在已有知识库中完成首份知识快速发布 | 逐项操作与七态复核 |
| [索引版本目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=index-versions&state=success) | 比较知识库索引版本与活动状态 | 逐项操作与七态复核 |
| [索引版本详情](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=index-detail&state=success) | 判断一个索引版本能否激活 | 逐项操作与七态复核 |
| [全库索引重建](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=index-rebuild&state=success) | 核对模型和分块变化后创建全库重建任务 | 逐项操作与七态复核 |

### 租户模型（M6）

| 页面 | 唯一任务 | 验收范围 |
| --- | --- | --- |
| [租户模型供应商](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=model-providers&state=success) | 查看租户可用供应商连接和模型发现 | 逐项操作与七态复核 |
| [供应商发现结果](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=model-provider-detail&state=success) | 核对单个供应商连接状态和发现结果 | 逐项操作与七态复核 |
| [模型配置目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=model-configurations&state=success) | 查找并管理受控模型配置 | 逐项操作与七态复核 |
| [模型配置向导](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=model-configuration-detail&state=success) | 编辑、轮换并启用一个模型配置 | 逐项操作与七态复核 |
| [租户模型部署](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=model-deployments&state=success) | 管理租户模型部署与能力验证 | 逐项操作与七态复核 |
| [租户部署验证](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=model-deployment-detail&state=success) | 探测并发布一个租户模型部署 | 逐项操作与七态复核 |
| [模型路由策略](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=routing-policies&state=success) | 模拟并发布主模型、有限回退和预算策略 | 逐项操作与七态复核 |

### 助手交付（M6）

| 页面 | 唯一任务 | 验收范围 |
| --- | --- | --- |
| [助手目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=assistants-directory&state=success) | 查找草稿、待评测或已发布助手 | 逐项操作与七态复核 |
| [创建助手](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=assistant-create&state=success) | 创建助手并关联一个知识库 | 逐项操作与七态复核 |
| [助手草稿配置](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=assistant-draft&state=success) | 配置并校验一个助手草稿版本 | 逐项操作与七态复核 |
| [助手单题测试](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=assistant-test&state=success) | 在冻结依赖下验证问题、答案和引用 | 逐项操作与七态复核 |
| [助手发布门禁](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=assistant-evaluation&state=success) | 判断助手版本是否达到发布门禁 | 逐项操作与七态复核 |
| [助手发布](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=assistant-release&state=success) | 确认一个助手版本的发布影响 | 逐项操作与七态复核 |
| [交付渠道绑定](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=channel-binding&state=success) | 把固定助手版本绑定到受限交付渠道 | 用户暂缓 |

### 评测与治理（M6）

| 页面 | 唯一任务 | 验收范围 |
| --- | --- | --- |
| [评测数据集目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=evaluation-datasets&state=success) | 管理问题、证据、拒答和权限样本 | 逐项操作与七态复核 |
| [评测数据集版本](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=evaluation-dataset-detail&state=success) | 维护并发布不可变评测数据集版本 | 逐项操作与七态复核 |
| [发起冻结评测](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=evaluation-run-create&state=success) | 选择冻结助手和数据集版本发起评测 | 逐项操作与七态复核 |
| [评测运行详情](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=evaluation-run-detail&state=success) | 审查一次评测的门禁结论和指标 | 逐项操作与七态复核 |
| [评测版本比较](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=evaluation-comparison&state=success) | 比较两个冻结版本的改善与回退 | 逐项操作与七态复核 |
| [评测失败分析](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=evaluation-failure-analysis&state=success) | 按失败类别定位可修复原因 | 逐项操作与七态复核 |
| [查询治理摘要](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=governance-query&state=success) | 查看查询用量和脱敏运行摘要 | 逐项操作与七态复核 |
| [用量与配额](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=usage-quotas&state=success) | 查看合成用量与平台分配的资源配额 | 逐项操作与七态复核 |
| [审计记录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=audit-records&state=success) | 查询访问、配置、发布和撤销事件 | 逐项操作与七态复核 |
| [审计事件解释](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=audit-record-detail&state=success) | 解释一条审计事件的主体、范围和版本链 | 逐项操作与七态复核 |
| [受权查询诊断](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=query-diagnostic&state=success) | 在受权范围内解释查询失败阶段 | 逐项操作与七态复核 |

### 普通用户问答（M6）

| 页面 | 唯一任务 | 验收范围 |
| --- | --- | --- |
| [普通用户登录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=login&state=success) | 建立普通用户 QUERY_APP 会话 | 逐项操作与七态复核 |
| [授权助手目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=assistants&state=success) | 搜索并选择一个获授权助手 | 逐项操作与七态复核 |
| [会话与历史](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=conversation&state=success) | 在固定助手版本的会话中恢复并继续问答 | 逐项操作与七态复核 |

### 设备问答（M7）

| 页面 | 唯一任务 | 验收范围 |
| --- | --- | --- |
| [开始现场问答](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=device-start&state=success) | 在固定设备与助手范围内开始一次问答 | 信息架构与视觉前置 |
| [确认使用范围](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=device-consent&state=success) | 理解并确认语音、画像和保留范围 | 信息架构与视觉前置 |
| [说出或输入问题](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=device-question&state=success) | 通过语音或触控提交一个现场问题 | 信息架构与视觉前置 |
| [现场回答](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=device-answer&state=success) | 查看简洁答案、现场指引或安全拒答 | 信息架构与视觉前置 |

### 平台运维（M8）

| 页面 | 唯一任务 | 验收范围 |
| --- | --- | --- |
| [平台运行状态](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-health&state=success) | 选择业务链路异常并确认处置结果 | 信息架构与视觉前置 |
| [摄取队列](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=ingestion-queue&state=success) | 处理摄取积压、失败和死信 | 信息架构与视觉前置 |
| [模型健康](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=model-health&state=success) | 处置模型连接、限流、延迟和熔断风险 | 信息架构与视觉前置 |
| [容量规划](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=capacity-planning&state=success) | 核对容量、增长和租户影响阈值 | 信息架构与视觉前置 |
| [备份与恢复](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=backup-recovery&state=success) | 检查备份并执行固定隔离恢复演练 | 信息架构与视觉前置 |
| [发布与回滚](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=release-operations&state=success) | 审查镜像、迁移和配置并执行受控回滚 | 信息架构与视觉前置 |
