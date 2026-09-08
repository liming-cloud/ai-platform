# WP-0016 样板方向人工验收

## 当前范围

阶段一的全页去向地图、阶段二的 Claude 公共组件、动效与连续任务样板已通过机器验证，等待样板方向人工确认。此前人工拒绝仍有效，全量送验为 false；方向确认后才迁移其余页面，最终全量确认后才进入 WP-0017。

原 73 个入口保留，新增 4 个成员页与助手概览，共 78 个入口。页面清单唯一来源为 page-inventory.json，以下表格从该清单生成。M7/M8 10 页仅信息架构与视觉，渠道绑定、设备凭据、邮件邀请暂缓。

原型、代码和设计统一保存在原 OpenDesign 项目；截图只在系统临时目录，不进入仓库。设计采用本机 Claude (Anthropic) 参考包与 7/5/7，不声称为 Anthropic 官方完整企业组件规范。

## 登录入口

| 身份 | 入口 | 未修改内置账户的演示信息 |
| --- | --- | --- |
| 平台管理员 | [平台管理登录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=console-login&state=success&review=0) | platform.admin.demo / demo-only |
| 租户管理员 | [租户管理员登录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-login&state=success&review=0) | spruce-demo / qiao.demo / demo-only |
| 普通用户 | [普通用户登录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=login&state=success&review=0) | spruce-demo / shen.demo / demo-only |

仅输入合成资料与公开演示密码，不使用真实凭据。创建或重置过的账户使用刚设置的演示密码；切换角色先退出，URL 不授予权限。

您此前手动停用的 qiao.demo 不会自动恢复。如需使用，由平台管理员进入对应租户的“管理员”页恢复，或使用您已创建的租户管理员账户。旧版登录修复只补齐未轮换的内置登录配置，不恢复停用状态；不要为验收重置全部数据。

## 三个任务目标

请按目标操作，不依赖逐按钮教程；观察入口、下一步和失败后的恢复方式是否容易理解。

| 样板 | 任务目标 | 核对结果 |
| --- | --- | --- |
| [准备知识](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=knowledge-directory&state=success&review=0) | 建立知识库并使一份资料可用于问答 | 文档位于知识库内；内容、处理记录和版本可查看；新索引经过评测及确认后才激活 |
| [发布助手](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=assistants-directory&state=success&review=0) | 为该知识库发布一个可用助手 | 概览、配置、测试、质量检查和发布路径连续；取消不发布，既有会话不换版本 |
| [开放使用](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-members&state=success&review=0) | 为普通成员开放该助手，并完成本人首次问答 | 账号、对话功能、助手范围独立；保存一个范围不夹带另一个；本人能登录并恢复历史 |

## 公共组件与异常验收

- 在“原型场景”展开“组件与动效样板”，检查按钮、内容切换、分步操作、弹窗、快速连续操作与减少动态效果。150/200/240ms 动效不延迟业务结果或撤权。
- 成员非秘密草稿可刷新恢复；离开和浏览器返回出现统一设计的保留/放弃弹窗。密码不进入草稿，不跨身份或登录代次继承输入。
- 目录有页码、每页大小和跳转；输入框与辅助说明分层对齐。长记录按序号加载，默认仅显示 20 条；长任务不塞入弹窗。
- 检查 390/1024/1440、1366×768、实际 200% 缩放、键盘与系统减少动态效果。机器已验证 720×450 等效 CSS 重排，不能代替您对实际缩放的体验判断。
- 右下审查器保留加载、空、错误、无权、部分可用、禁用、成功七态。禁用不能提交，无权不泄露正文，取消不写入。
- 1000 文档、1000 成员、200 助手、100 版本和 10000 记录的压力数据只在独立浏览器测试注入，不覆盖您的日常数据。

机器结果：161 项 Node、157 项浏览器、1638 个页面状态/尺寸场景通过；66 个范围内入口七态枚举 7779 个控件，未接线和运行异常均为零。监听器接线、合成结果和布局测试不等于真实后端能力、独立新用户研究或人工通过。

如不通过，请记录“任务目标、身份/组织、操作、预期、实际”；确认样板方向也不代表全量验收。详细设计见 DESIGN.md，操作范围见 ACTION-LEDGER.json，机器报告见 artifacts/stage-two-validation-summary.json。

## 全部页面去向

### 共享访问（M6）

| 页面 | 唯一任务 | 去向与阶段 |
| --- | --- | --- |
| [平台管理登录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=console-login&state=success&review=0) | 验证平台账户并进入平台管理 | 保留 → console-login；阶段三待迁移 |
| [租户管理员登录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-login&state=success&review=0) | 验证租户管理员并进入指定组织 | 保留 → tenant-login；阶段三待迁移 |

### 平台治理（M6）

| 页面 | 唯一任务 | 去向与阶段 |
| --- | --- | --- |
| [平台运营摘要](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-overview&state=success&review=0) | 处理全平台脱敏运营风险 | 保留 → platform-overview；阶段三待迁移 |
| [租户目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenants&state=success&review=0) | 查找并管理租户生命周期 | 保留 → platform-tenants；阶段三待迁移 |
| [建立新租户](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-create&state=success&review=0) | 分三步建立租户并设置资源配额 | 保留 → platform-tenant-create；阶段三待迁移 |
| [租户概览](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-detail&state=success&review=0) | 租户概览独立查看与维护 | 保留 → platform-tenant-detail；阶段三待迁移 |
| [租户管理员](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-admins&state=success&review=0) | 租户管理员独立查看与维护 | 保留 → platform-tenant-admins；阶段三待迁移 |
| [资源配额](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-quotas&state=success&review=0) | 资源配额独立查看与维护 | 保留 → platform-tenant-quotas；阶段三待迁移 |
| [可用功能](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-features&state=success&review=0) | 可用功能独立查看与维护 | 保留 → platform-tenant-features；阶段三待迁移 |
| [登录安全](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-security&state=success&review=0) | 登录安全独立查看与维护 | 保留 → platform-tenant-security；阶段三待迁移 |
| [平台账户](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-accounts&state=success&review=0) | 创建和管理平台管理员账户 | 保留 → platform-accounts；阶段三待迁移 |
| [平台固定角色](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-roles&state=success&review=0) | 理解平台固定角色及最小披露边界 | 保留 → platform-roles；阶段三待迁移 |
| [平台模型连接](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connections&state=success&review=0) | 管理平台模型供应商连接 | 保留 → platform-model-connections；阶段三待迁移 |
| [供应商连接探测](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connection-detail&state=success&review=0) | 探测并发布单个供应商连接版本 | 保留 → platform-model-connection-detail；阶段三待迁移 |
| [平台模型部署](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-deployments&state=success&review=0) | 管理平台可授权模型部署 | 保留 → platform-model-deployments；阶段三待迁移 |
| [平台部署发布](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-deployment-detail&state=success&review=0) | 验证并发布单个模型部署 | 保留 → platform-model-deployment-detail；阶段三待迁移 |

### 租户访问与知识（M6）

| 页面 | 唯一任务 | 去向与阶段 |
| --- | --- | --- |
| [添加成员](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-member-create&state=success&review=0) | 分三步创建并授权普通成员 | 新增样板 → tenant-member-create；阶段二样板 |
| [成员账号](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-member-account&state=success&review=0) | 查看一个成员的账号信息 | 新增样板 → tenant-member-account；阶段二样板 |
| [对话功能](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-member-features&state=success&review=0) | 保存一个成员的对话功能 | 新增样板 → tenant-member-features；阶段二样板 |
| [可用助手](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-member-assistants&state=success&review=0) | 保存一个成员的可用助手范围 | 新增样板 → tenant-member-assistants；阶段二样板 |
| [租户运营概览](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-overview&state=success&review=0) | 处理当前租户最重要的阻断项 | 保留 → tenant-overview；阶段三待迁移 |
| [成员与对话功能](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-members&state=success&review=0) | 管理普通成员生命周期与对话功能 | 拆分 → tenant-members、tenant-member-account、tenant-member-features、tenant-member-assistants、tenant-member-create；阶段二样板 |
| [租户固定角色](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-roles&state=success&review=0) | 理解租户固定角色能力边界 | 保留 → tenant-roles；阶段三待迁移 |
| [访问策略目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=access-policies&state=success&review=0) | 查找并发布资源访问策略 | 保留 → access-policies；阶段三待迁移 |
| [访问策略版本](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=access-policy-detail&state=success&review=0) | 编辑、预览并发布一个访问策略版本 | 保留 → access-policy-detail；阶段三待迁移 |
| [设备凭据目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=device-credentials&state=success&review=0) | 管理设备凭据、助手与现场范围 | 保留 → device-credentials；暂缓 |
| [知识库目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=knowledge-directory&state=success&review=0) | 查找或创建租户知识库 | 保留 → knowledge-directory；阶段二样板 |
| [知识库概览](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=knowledge-overview&state=success&review=0) | 判断知识库当前可用性和唯一下一动作 | 保留 → knowledge-overview；阶段二样板 |
| [数据来源](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=knowledge-sources&state=success&review=0) | 管理当前知识库的数据来源 | 迁移 → knowledge-sources；阶段二样板 |
| [添加数据来源](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=source-add&state=success&review=0) | 通过粘贴或本地文件登记一个来源 | 保留 → source-add；阶段二样板 |
| [文档详情](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=source-detail&state=success&review=0) | 查看文档版本、处理结果和安全重试点 | 拆分 → source-detail；阶段二样板 |
| [处理与质量](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=processing-quality&state=success&review=0) | 处理解析、分块和索引质量问题 | 迁移 → processing-quality；阶段二样板 |
| [同步运行结果](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=sync-result&state=success&review=0) | 审查一次同步的成功、失败、重复和越权结果 | 保留 → sync-result；阶段三待迁移 |

### 知识处理与索引（M6）

| 页面 | 唯一任务 | 去向与阶段 |
| --- | --- | --- |
| [结构化数据版本](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=dataset-schema&state=success&review=0) | 冻结 CSV 字段规则与敏感级别 | 保留 → dataset-schema；阶段三待迁移 |
| [对象范围登记](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=object-storage-scope&state=success&review=0) | 把平台签发的文本范围登记为知识来源 | 保留 → object-storage-scope；阶段三待迁移 |
| [首份知识快速发布](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=quick-publication&state=success&review=0) | 在已有知识库中完成首份知识快速发布 | 保留 → quick-publication；阶段三待迁移 |
| [索引版本目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=index-versions&state=success&review=0) | 比较知识库索引版本与活动状态 | 迁移 → index-versions；阶段二样板 |
| [索引版本详情](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=index-detail&state=success&review=0) | 判断一个索引版本能否激活 | 保留 → index-detail；阶段二样板 |
| [全库索引重建](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=index-rebuild&state=success&review=0) | 核对模型和分块变化后创建全库重建任务 | 迁移 → index-rebuild；阶段三待迁移 |

### 租户模型（M6）

| 页面 | 唯一任务 | 去向与阶段 |
| --- | --- | --- |
| [租户模型供应商](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=model-providers&state=success&review=0) | 查看租户可用供应商连接和模型发现 | 保留 → model-providers；阶段三待迁移 |
| [供应商发现结果](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=model-provider-detail&state=success&review=0) | 核对单个供应商连接状态和发现结果 | 保留 → model-provider-detail；阶段三待迁移 |
| [模型配置目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=model-configurations&state=success&review=0) | 查找并管理受控模型配置 | 保留 → model-configurations；阶段三待迁移 |
| [模型配置向导](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=model-configuration-detail&state=success&review=0) | 编辑、轮换并启用一个模型配置 | 保留 → model-configuration-detail；阶段三待迁移 |
| [租户模型部署](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=model-deployments&state=success&review=0) | 管理租户模型部署与能力验证 | 保留 → model-deployments；阶段三待迁移 |
| [租户部署验证](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=model-deployment-detail&state=success&review=0) | 探测并发布一个租户模型部署 | 保留 → model-deployment-detail；阶段三待迁移 |
| [模型路由策略](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=routing-policies&state=success&review=0) | 模拟并发布主模型、有限回退和预算策略 | 保留 → routing-policies；阶段三待迁移 |

### 助手交付（M6）

| 页面 | 唯一任务 | 去向与阶段 |
| --- | --- | --- |
| [助手目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=assistants-directory&state=success&review=0) | 查找草稿、待评测或已发布助手 | 保留 → assistants-directory；阶段二样板 |
| [创建助手](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=assistant-create&state=success&review=0) | 创建助手并关联一个知识库 | 保留 → assistant-create；阶段二样板 |
| [助手概览](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=assistant-overview&state=success&review=0) | 查看当前助手版本并继续交付 | 新增样板 → assistant-overview；阶段二样板 |
| [助手草稿配置](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=assistant-draft&state=success&review=0) | 配置并校验一个助手草稿版本 | 拆分 → assistant-draft；阶段二样板 |
| [助手单题测试](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=assistant-test&state=success&review=0) | 在冻结依赖下验证问题、答案和引用 | 保留 → assistant-test；阶段二样板 |
| [助手发布门禁](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=assistant-evaluation&state=success&review=0) | 判断助手版本是否达到发布门禁 | 保留 → assistant-evaluation；阶段二样板 |
| [助手发布](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=assistant-release&state=success&review=0) | 确认一个助手版本的发布影响 | 保留 → assistant-release；阶段二样板 |
| [交付渠道绑定](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=channel-binding&state=success&review=0) | 把固定助手版本绑定到受限交付渠道 | 保留 → channel-binding；暂缓 |

### 评测与治理（M6）

| 页面 | 唯一任务 | 去向与阶段 |
| --- | --- | --- |
| [评测数据集目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=evaluation-datasets&state=success&review=0) | 管理问题、证据、拒答和权限样本 | 保留 → evaluation-datasets；阶段三待迁移 |
| [评测数据集版本](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=evaluation-dataset-detail&state=success&review=0) | 维护并发布不可变评测数据集版本 | 保留 → evaluation-dataset-detail；阶段三待迁移 |
| [发起冻结评测](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=evaluation-run-create&state=success&review=0) | 选择冻结助手和数据集版本发起评测 | 保留 → evaluation-run-create；阶段三待迁移 |
| [评测运行详情](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=evaluation-run-detail&state=success&review=0) | 审查一次评测的门禁结论和指标 | 保留 → evaluation-run-detail；阶段三待迁移 |
| [评测版本比较](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=evaluation-comparison&state=success&review=0) | 比较两个冻结版本的改善与回退 | 保留 → evaluation-comparison；阶段三待迁移 |
| [评测失败分析](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=evaluation-failure-analysis&state=success&review=0) | 按失败类别定位可修复原因 | 保留 → evaluation-failure-analysis；阶段三待迁移 |
| [查询治理摘要](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=governance-query&state=success&review=0) | 查看查询用量和脱敏运行摘要 | 保留 → governance-query；阶段三待迁移 |
| [用量与配额](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=usage-quotas&state=success&review=0) | 查看合成用量与平台分配的资源配额 | 迁移 → usage-quotas；阶段三待迁移 |
| [审计记录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=audit-records&state=success&review=0) | 查询访问、配置、发布和撤销事件 | 迁移 → audit-records；阶段三待迁移 |
| [审计事件解释](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=audit-record-detail&state=success&review=0) | 解释一条审计事件的主体、范围和版本链 | 保留 → audit-record-detail；阶段三待迁移 |
| [受权查询诊断](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=query-diagnostic&state=success&review=0) | 在受权范围内解释查询失败阶段 | 迁移 → query-diagnostic；阶段三待迁移 |

### 普通用户问答（M6）

| 页面 | 唯一任务 | 去向与阶段 |
| --- | --- | --- |
| [普通用户登录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=login&state=success&review=0) | 建立普通用户 QUERY_APP 会话 | 保留 → login；阶段二样板 |
| [授权助手目录](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=assistants&state=success&review=0) | 搜索并选择一个获授权助手 | 保留 → assistants；阶段二样板 |
| [会话与历史](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=conversation&state=success&review=0) | 在固定助手版本的会话中恢复并继续问答 | 保留 → conversation；阶段二样板 |

### 设备问答（M7）

| 页面 | 唯一任务 | 去向与阶段 |
| --- | --- | --- |
| [开始现场问答](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=device-start&state=success&review=0) | 在固定设备与助手范围内开始一次问答 | 保留 → device-start；阶段三仅视觉与信息架构 |
| [确认使用范围](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=device-consent&state=success&review=0) | 理解并确认语音、画像和保留范围 | 保留 → device-consent；阶段三仅视觉与信息架构 |
| [说出或输入问题](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=device-question&state=success&review=0) | 通过语音或触控提交一个现场问题 | 保留 → device-question；阶段三仅视觉与信息架构 |
| [现场回答](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=device-answer&state=success&review=0) | 查看简洁答案、现场指引或安全拒答 | 保留 → device-answer；阶段三仅视觉与信息架构 |

### 平台运维（M8）

| 页面 | 唯一任务 | 去向与阶段 |
| --- | --- | --- |
| [平台运行状态](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-health&state=success&review=0) | 选择业务链路异常并确认处置结果 | 保留 → platform-health；阶段三仅视觉与信息架构 |
| [摄取队列](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=ingestion-queue&state=success&review=0) | 处理摄取积压、失败和死信 | 保留 → ingestion-queue；阶段三仅视觉与信息架构 |
| [模型健康](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=model-health&state=success&review=0) | 处置模型连接、限流、延迟和熔断风险 | 保留 → model-health；阶段三仅视觉与信息架构 |
| [容量规划](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=capacity-planning&state=success&review=0) | 核对容量、增长和租户影响阈值 | 保留 → capacity-planning；阶段三仅视觉与信息架构 |
| [备份与恢复](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=backup-recovery&state=success&review=0) | 检查备份并执行固定隔离恢复演练 | 保留 → backup-recovery；阶段三仅视觉与信息架构 |
| [发布与回滚](http://127.0.0.1:65153/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=release-operations&state=success&review=0) | 审查镜像、迁移和配置并执行受控回滚 | 保留 → release-operations；阶段三仅视觉与信息架构 |
