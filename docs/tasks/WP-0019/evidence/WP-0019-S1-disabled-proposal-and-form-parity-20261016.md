# WP-0019 S1：页面禁用提案与完整表单验收

| 字段 | 值 |
| --- | --- |
| 责任／范围 | WP-0019／S1；页面 disabled 触发调查、完整创建表单及交互修复；S1、WP-0019、M6 未完成 |
| 本轮输入 | 46e37094955df0aa1c1bf68bd4143bd284954d6b；main 干净；工作包输入 8ade6bbe15a53fdf73a1fea794e642632f59b835 为祖先 |
| disabled 提案提交 | 4c0c3dcfb346b88b1635f6ce45668d230b66a1b3；待人工接受，未实施 |
| 最终前端源码／实际 OCI revision | 4e47d97ed3e330450f841a89c16c407d3a961ba4 |
| 实际前端镜像 | sha256:2888cc378cdf2653a3a2b4bfae30b57d8b47dd2cec4e89a0c54cba5e94c52920 |
| 三后端实际 OCI revision | 7906451e3f2e04a03e098e601b37425e29857cb5；后端源码无差异，未重建 |
| 原型唯一来源／已接受版本 | OpenDesign Cloud 原项目 249f81f2-6f1a-46ff-988c-da3cdedb35e7／e7cd0ec22b3539033f374f4a8ca53c6b77d98ccc |
| 原型错误色候选 | cce686e4634ca3c523e4c8aeffd95752f1a04c3a；技术验证通过，待人工接受，不替换上项基线 |
| 执行日期 | 2026-10-16；最终正式只读浏览器 09:54:20.273～09:54:45.790 UTC |

[上一轮辅助读取降级](WP-0019-S1-partial-production-delivery-20261016.md)已闭环，不重新解释 partial。本轮将剩余问题拆成真实触发、完整表单、原型缺陷和厂商合同，分别保留结论。没有进入 S2～S6，没有改变 API、权限、厂商协议或持久化结构；STATE 和实现矩阵完成事实不提升。

## 已交付与待决定事项

| 子任务 | 本轮结果与下一条件 |
| --- | --- |
| 页面 disabled 真实触发调查 | 已完成调查和[最小提案](../page-state-proposal.md)，待人工接受后登记 ADR；未增加生产开关 |
| 表单操作区和交互修复 | 已完成红绿、源码验证及最新部署复验；提交 09dfee55e9650cce11f6f8c63aec476dfa68becd、ce339ba29330d430dfb58a72bcace82ed1e5f10f、4e47d97ed3e330450f841a89c16c407d3a961ba4 |
| 完整表单与已接受原型 | 全部 11 组合已实际执行；原始结果 FAILED，剩余三处说明文字及其换行几何，不能报告整体一致 |
| 原型校验错误色 | 已证明原型错误继承成功绿色，原项目形成两行修正候选；候选对照通过，待人工接受 |
| 新方舟真实合同 | 既有精确连接缺独立发现 AK/SK，最小 ListEndpoints 尚未执行；旧发现已过期，不复跑或重复发布 |
| 远端交付质量 | 提案已推送，但 GitHub Actions 因账户账单／额度限制没有启动任务；最终推送另核对精确提交，不用本地通过冒充远端 CI |

现行读取没有页面暂停事实；MODEL_PLATFORM_WRITE 同时用于读取和写入，403 不是只读角色。单连接 DISABLED、过期、缺凭据、搜索结果全停用或厂商故障都不能推导全页 disabled。推荐方案仅由部署配置暂停 S1 连接写命令，在既有预设响应增加可信 writesAvailable，并在创建、发现、旧 probe 和发布入口统一强制检查；无新管理页、动态开关 API、数据库表或全平台维护框架。该方案改变合同，仍需人工决定和已接受 ADR，不以提案存在代替实施。

完整表单的三处说明差异已在[设计与切片计划](../design-and-slices.md)逐字列出：原型“不保存到原型”对应生产“保存后不会回显”，以及方舟说明中的“不使用真实凭据测试原型”。它们描述原型与真实产品的不同存储／验收环境，不能把不保存的声明复制到生产。本轮仅提出精确边界，未自行接受为例外；旧登录演示密码说明例外不自动适用。

## 修复与测试证据

原始失败测试先捕获按钮字号／字重、Fluent 手机全行布局、键盘黑色焦点框、输入悬停边框及反馈段默认外边距，再修复 S1 对话框局部 CSS。按钮保持内容宽度、14px／620／20px 文字及 6×12px 内边距；原生文字 200% 时实际变为 28px／40px。输入焦点、取消与提交的悬停／键盘边框均按原型；错误反馈保持生产既有正确红色并恢复相邻 16px 间距。没有修改共享主题或业务状态机。

永久表单浏览器测试由 54 增至 60 项，保留原断言和 1px 几何容差。早期观测曾把原生与组合控件的不同盒模型、标签容器宽度、BODY 强制焦点及尚未完成的 0.85 弹窗动画当作差异；分别改为等价文本／边界测量、真实字段点击和字体／动画就绪等待。旧失败日志保留，不把观测器问题算成产品缺陷，不放宽字号或裁切断言。

| 验证 | 本轮实际结果 |
| --- | --- |
| make frontend-test | 53 文件、305 项；类型、lint、构建通过 |
| 最终源码表单／partial／分页浏览器 | 60／22／37 项通过，保留秘密取消、对象停用、受控辅助故障和权限反例 |
| 工作包验收入口 | Python 29 项、Node 22 项及脚本语法通过 |
| 离线 fixture | 9 项校验及 2 场景、8 检索用例通过；未修改 fixture |
| 架构／治理 | 50 项架构测试及前后端守卫、81 项治理／30 个任务通过；650 项历史定位仍由 project-verify 校验 |
| Compose／smoke | 七服务健康；仅更新 console-web；四入口通过 |
| 最终部署正式只读浏览器 | make acceptance-work-package WP=WP-0019 SLICE=platform-connection，10 项通过；登录／范围恢复、预设、精确连接／发布、目录详情刷新、三尺寸、实际 200%、键盘、退出及业务事实不变 |
| 完整表单对照 | 11 厂商／能力组合 × 3 尺寸 × 正常／200% = 66 组、132 份观察、23,310 项比较；840 次表单内 Tab 转移、66 次缺密钥校验 |
| 最终部署对已接受 e7 的交互对照 | 18 组，18 项差异均为原型校验错误绿色与生产正确红色；其余悬停、焦点和错误几何无差异 |
| 最终部署对原型 cce 候选 | 同 18 组，差异 0；这是候选技术结论，不是已接受基线通过 |
| 原型最小错误色反例 | 修改前 3 项失败；候选新旧浏览器合计 39 项、状态／预设 14 项通过 |

三尺寸为 390／1024／1440，200% 为实际计算字号，原生输入从 16px 到 32px；使用 reduced-motion。完整表单覆盖 CUSTOM 三能力、DeepSeek 对话、百炼三能力、SiliconFlow 三能力及方舟对话。原始完整报告有 167 项差异：72 项属于上述三处待决定文案，95 项为这些文字换行造成的高度和下游位置差；解释后的未知差异为 0，原始 FAILED 与 fullFormParity=false 均保留。分类仅定位原因，不批准例外，不将 167 改为 0。

原始完整报告 SHA-256 为 aa8fdd19079c7a3dfec0025cd862f114bf0bc53e4daaa0aef6d769bb7fbde829。正式十项入口的 prototypeParity=NOT_RUN，完整与交互报告独立执行，不能用入口通过代替一致性。上述部署浏览器均绑定前端 4e47d97ed3e330450f841a89c16c407d3a961ba4／后端 7906451e3f2e04a03e098e601b37425e29857cb5，页面错误、裁切问题、业务写入及被阻止业务写入均为 0。已人工查看手机 200% 和桌面完整表单，以及原型／生产红色校验截图；截图只在 /private/tmp。

## 原项目候选与版本绑定

原型 bindModelGovernance 在 !result.ok 时只写反馈正文，没有设置已有 data-tone=danger，因此继承默认成功绿色。先用三个尺寸的真实提交反例证明，再仅增加中文原因注释与 danger 标记两行；复用既有错误样式，不改三处说明、表单布局或成功路径。生产错误本来就是红色，没有为迁就候选改变生产语义。

[原项目入口](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connections&state=success&review=1)：新增供应商连接 → DeepSeek → 留空密钥并保存，可查看错误色候选。原项目当前内容为 cce686e4634ca3c523e4c8aeffd95752f1a04c3a，已接受版本仍为 e7cd0ec22b3539033f374f4a8ca53c6b77d98ccc，依据 ADR-0079 待单独人工接受。没有创建原型副本。

内置 OpenDesign Cloud MCP 后来返回 Transport closed；原项目预览仍可用。通过同产品官方 MCP 的独立会话操作同一项目，修改前 17 个渲染资源与固定 e7 的 MCP／磁盘／Git／HTTP 逐字节一致，修改后 17 个资源与 cce 一致，只有 prototype.js 改变。旧临时 helper 只允许 CSS，首次 JS 请求在发出服务调用前被其本地断言拒绝；保留原 helper，使用同项目且只允许 prototype.js 的独立 helper。未修改服务权限或绕过审批，不把内置传输问题描述为已修复。

## 只读业务事实与厂商阻断

| 后端进程 | 本轮实际镜像 |
| --- | --- |
| control-plane | sha256:2ca605aa2befc0db61be4fcc9a23341d7cc7dc8264639a23e47325fe2fb5917e |
| query-runtime | sha256:a8d9c542b5f7245e1c3d13f9bf99df940dc7aa63aa1197b37f42ed04abaf9b42 |
| ingestion-worker | sha256:1e8094693b20c93c49cde73853925b5f507cedfdf9f491832255b53b0c88bfc8 |

复用已有授权、当前用户拥有的 0600 登录来源，平台／租户正式登录成功，没有索取新密码、打印或导出秘密。真实账号浏览器顺序运行。数据库仅执行既有 REPEATABLE READ READ ONLY SQL，读取计数、状态、修订和时间；09:14:49.178701～10:08:52.740521 UTC 前后八项事实一致。

连接 2093675537095458817 仍为 VERIFIED／修订 4，独立发布修订 1 仍绑定修订 4；共 2 条连接，合成草稿 2110269575189757954 仍为未探测、未发布 DRAFT；部署、目录项及授权均为 0。正常登录签发／退出属于会话活动，不据此宣称全数据库零写入。

该精确连接仅配置推理凭据，discoveryCredentialsConfigured=false。旧 OPENAI_MODELS_API 的 135 个候选已于 2026-10-15T03:55:24.971802Z 到期，latestDiscoveryValid=false。新 VOLCENGINE_ARK_CONTROL_PLANE 需要匹配的独立发现 AK/SK，不能由推理 Key 推导或从已发布事实倒推。最小 ListEndpoints 真实合同未执行；没有新厂商调用、重发现、重复发布、推理或业务对象创建。解除条件是配置匹配发现材料并核对现有最小调用授权，平台登录不再是阻断。

## 证据、评审与下一动作

本轮使用 Local Codex 当前个人工程助手 2.2.0-rc.1+codex.local-20260929-051321，显式加载 wayfinder、repo-context、tdd、code-review，以及人工门禁／安全参考；控制依据为 Charter、ROADMAP、STATE、WP-0019 及其 12 个 ADR。内联固定 diff 评审覆盖输入 46e37094955df0aa1c1bf68bd4143bd284954d6b 至候选 cce686e4634ca3c523e4c8aeffd95752f1a04c3a 的源码、测试、提案和候选，未发现待修代码问题。没有 Runtime／StageRunResult，阶段结果校验不适用，不复用 WP-0017 Runtime。

代码图谱探索 PlatformProviderConnectionForm、两生产页面、PlatformProviderConnectionService.requirePermission、原型两页渲染和 bindModelGovernance；对表单及原型事件绑定执行影响分析。结果将修复限定于 S1 对话框局部 CSS 和原型既有错误反馈标记，确认没有现成的只读角色或页面暂停事实，没有引入通用框架。图谱不代替编译、测试和人工语义接受。

详细证据在 /private/tmp/wp0019-s1-disabled-form-20261016-j6zt3mc1，目录 0700／文件 0600。41 项核心清单 closure-safe-evidence-digests.json 的 SHA-256 为 58c67ca75e299a02eefa16cf32c3bdfd87021d2fe36e6031f7b8c64c86564b04；排除凭据、截图、后续控制面与最终远端 CI。原始失败与修复后结果均保留；不提交截图或秘密。

提案提交 4c0c3dcfb346b88b1635f6ce45668d230b66a1b3 的 [GitHub Actions 38877151090](https://github.com/liming-cloud/ai-platform/actions/runs/38877151090) 为 failure，但任务 steps=[]、没有执行日志；官方 annotation 明确近期账户付款失败或需要提高 spending limit。属于账户侧未启动，不能描述为测试失败或 CI 通过，也不修改账单设置／门禁绕过。最终提交推送后再次核对实际结果。

下一动作只处理三个已提交人工事项：disabled 最小合同、三处精确说明边界、错误色候选。收到相应接受后登记决策／原型基线，再执行对应实施或有界复验，不重复原生字号、完整地址、partial 或旧发布验收。未筛选真实空目录、其余完整七态和新方舟真实合同仍未闭合；两类门禁全部通过才进入 S2，WP-0019 与 M6 均保持未完成。

最终 make decisions-sync、make project-status、make project-verify、make architecture-guard、make resume-context 均通过；治理 81 项／30 个任务、架构 50 项及守卫通过，恢复输出 10,298 字节，未提高 12 KiB 上限。后端未改变，本轮未重跑 Maven。核心 41 项摘要复核无差异；STATE、ADR 接受记录及实现矩阵未修改。最终文档提交只保存该证据及工作包恢复入口，推送结果与精确 CI 在交付时核对，不预填成功。
