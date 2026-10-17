# WP-0019 S1：写入可用性决定与源码验证

| 字段 | 值 |
| --- | --- |
| 责任与结论 | WP-0019／S1；决策登记与源码验证阶段已完成，最新部署和生产一致性未执行；S1、WP-0019、M6 未完成 |
| 输入提交 | e1e1459c8bd8f161aa477d36289c0cbcb1a89d49；main 起始干净；工作包输入 8ade6bbe15a53fdf73a1fea794e642632f59b835 为祖先 |
| 已接受决定提交 | fa8f22e6803c8358e9c7d511b8f9df5c701a74b3；ADR-0098、三处说明及原型接受先于实施 |
| 产品候选源码 | e0224fa9847fdaf297b3ad896e37ae7796e29a50 |
| 补充测试／验收工具 | b061d8c64785d09139c06c46780b3596c9985257；只新增暂停授权反例、修正验收报告原型版本，前后端产品代码与候选无差异 |
| 唯一原型 | OpenDesign Cloud 原项目 249f81f2-6f1a-46ff-988c-da3cdedb35e7；已接受 cce686e4634ca3c523e4c8aeffd95752f1a04c3a |
| 执行日期 | 2026-10-17，Asia/Shanghai；不改写上一轮验收日期 |

用户本轮明确接受 disabled 最小触发提案、三处原型专用说明的生产表达及错误色候选。接受登记见[唯一人工接受记录](WP-0019-S1-prototype-acceptance-20261012.md)，公共合同先登记 [ADR-0098](../../../architecture/decisions/ADR-0098-platform-connection-write-availability.md)再提交源码。[上一轮完整表单报告](WP-0019-S1-disabled-proposal-and-form-parity-20261016.md)中待接受与原始失败保持当时事实；本轮不再重复询问这三项决定，不把接受直接变成部署通过。

## 已实现边界

- 启动配置 foundation.model-runtime.platform-connection-writes-enabled 默认 true，在实例装配时转换为不可变 PlatformProviderConnectionWritePolicy。既有预设响应增加必需布尔 writesAvailable；不增加读取请求、管理页、动态 API、数据库表或权限框架。
- 创建、旧 probe、独立模型发现、独立发布均先授权，再检查同一策略。暂停以 MODEL_RUNTIME.CONFLICT.0011／安全消息“平台连接写入暂不可用”拒绝，发生于凭据解密、出站和业务持久化之前。既有连接、发现与发布事实不改写。
- 两页完整读取且明确 false 时显示“暂不可用”，保留搜索、分页、查看和返回，禁止主写操作；重读撤下旧表单并清空秘密。表单自身也拒绝不可写目录提交。缺失／非法布尔失败关闭，partial、401／403、精确资源错误及请求所有者语义保持 ADR-0097；恢复需完整重读，true 不覆盖对象停用、缺凭据或验证到期。
- Compose 仅为 control-plane 暴露该启动值，默认 true；其他运行进程及模型推理、租户配置没有新增暂停开关。当前配置不会热更新或中断旧实例已受理命令。
- 三处环境说明按 ADR-0098 接受现有真实生产表达，原型继续保留演示警告。原型错误色候选已接受，本轮未修改原型。正式验收脚本的固定版本同步为 cce，仍保留 prototypeParity=NOT_RUN 和 sliceComplete=false。

## 真实红绿与验证

先增加自动装配中的暂停反例，以及前端缺失／非法字段、暂停／partial／拒绝优先级反例，实际观察失败，再实现。后端红灯证明原配置被忽略、预设缺少字段、四命令继续推进；前端新增 5 项失败、原有 26 项通过。修复后相同自动装配与 31 项读取测试通过，补充暂停下四命令仍先返回原权限拒绝，未用兼容构造器绕过必需策略。

| 验证 | 实际结果 |
| --- | --- |
| ./mvnw -q verify | 348 套件、1,132 项，失败／错误／跳过均 0；含真实隔离 PostgreSQL、HTTP、扩展和三个启动应用；产品代码随后固定于 e022 |
| 补充暂停授权优先 | ModelRuntimeAutoConfigurationTest 定向通过；补充提交只改测试及验收元数据 |
| make frontend-test | 53 文件、310 项，类型、lint、构建通过 |
| 源码浏览器 | 表单 60、partial／disabled 30、分页 37 项通过；真实客户端配合隔离 HTTP，不访问生产或厂商 |
| 架构与治理 | 50 项及前后端守卫、81 项／30 个任务通过；后端依赖边界违规 0 |
| 正式验收入口合同 | Python 29、Node 22 项及语法检查通过；不代表生产验收 |
| 离线 fixture | 9 项及 2 场景／8 检索用例通过；未改 fixture |
| make deploy-config | 默认 Compose 语法及插值通过，未输出环境内容 |
| 原项目绑定 | 17 个入口资源 MCP／磁盘／固定 cce Git／实际 HTTP 逐字节一致，无副本 |

首次同时启动三个隔离 Vite／Chrome 程序时，表单和分页启动等待超时，其余 30 项通过；保留日志，按原代码、原断言和原 8 秒超时顺序复验，两程序分别 60／37 项通过。该现象与并发启动相关，未将其认定为产品断言失败，未放宽超时、字号、1px 容差或裁切断言。

新增浏览器覆盖两页三尺寸、实际 200%、键盘、暂停读取／恢复、partial 与安全拒绝优先，以及旧表单秘密卸载。单对象停用及过期反例继续由既有 60 项覆盖；不从单对象状态或故障推导页面暂停。详细日志、源码浏览器产物及观察程序仅在 /private/tmp。

## 构建与外部阻断

四镜像以干净候选 e022 和显式 OCI revision 构建。console-web 成功，镜像 sha256:ba49240be84a83e47dc205bdff36346c6549a5b1b3a6c19edc4eef0cbff1a815；ingestion-worker 成功，镜像 sha256:5d14467dc8b02f18d8874e728f564022bd18fb19e236dc424e0179e880603cb0。两者只是候选，未替换运行容器。control-plane 与 query-runtime 构建均以“failed to receive status…Unavailable…EOF”结束。

并发构建期间 Docker Desktop 变为不可用：诊断容器请求返回“Docker Desktop is unable to start”，desktop status 无法取得状态，build history 返回 EOF；make deploy-smoke 在 18080 readiness 失败。根因未确认，不能将其写成 Maven／产品测试失败，也不能确认当前四应用仍健康。没有自动重启 Docker、清缓存／数据卷或删除历史对象；失败客户端均已自行结束，没有遗留构建进程。已请求用户恢复到 Running，期间继续完成不依赖 Docker 的工作。

最近一次独立确认的运行版本仍是前端 4e47d97ed3e330450f841a89c16c407d3a961ba4／镜像 sha256:2888cc378cdf2653a3a2b4bfae30b57d8b47dd2cec4e89a0c54cba5e94c52920；三后端 7906451e3f2e04a03e098e601b37425e29857cb5，各自镜像从上一轮证据与本轮 initial-runtime.json 恢复。这是中断前事实，不冒称当前已恢复。没有执行 compose up、临时暂停或运行服务替换，不能以新 HEAD 或镜像标签替代实际 OCI 证据。

本轮已执行既有 REPEATABLE READ READ ONLY SQL 的部署前检查，只读计数、状态、修订和时间；没有秘密读取、厂商调用、重新发现、重复发布或业务对象创建。中断后未执行数据库后查，不声称完成新一轮前后对照。

既有精确连接 2093675537095458817 仍缺独立发现 AK/SK；旧 135 个候选已于 2026-10-15T03:55:24.971802Z 到期，新 VOLCENGINE_ARK_CONTROL_PLANE 最小 ListEndpoints 合同仍未执行。平台凭据原来源存在、属当前用户且为 0600，本轮没有要求新密码；未启动生产浏览器登录，不把源文件存在当作本轮已登录。

已推送决策的 [CI 39035195711](https://github.com/liming-cloud/ai-platform/actions/runs/39035195711)为 failure，任务 steps=[]；官方 annotation 明确账户近期付款失败或需要提高 spending limit，任务没有启动。最终源码／证据推送后另核对精确提交，不预填远端通过。本地 verify 不替代此门禁，也不绕过流水线或修改账单。

## 恢复步骤与质量来源

1. Docker 恢复后先只读确认七服务、原运行镜像和候选镜像。按固定 e022 的精确 Git 源码构建剩余镜像，改为顺序执行；已有候选先核对摘要与 revision，不清缓存，不重置数据。
2. 仅更新四应用到同一已验证候选，等待健康并记录四个独立镜像／revision。临时覆盖层只将控制面的 S1 写命令设为 false；正式 HTTP 四请求必须返回精确 CONFLICT.0011，保留实际读取事实。
3. 执行两页 12 组 disabled 状态／控件对照及恢复，恢复默认 true 后执行正式十项只读、完整表单 66 组和交互 18 组对照，数据库前后事实另查。三处接受映射只单独解释精确文字与自然换行，原始差异仍保留；不能将准备好的程序当作已执行。
4. 新方舟匹配发现材料、未筛选真实空目录及其余完整七态仍单独收口；不因为源码阶段交付而进入 S2～S6。S1、WP-0019、M6 完成事实不变。

Local Codex 使用当前个人工程助手 2.2.0-rc.1+codex.local-20260929-051321 的 wayfinder、tdd、code-review，读取安全 seam；依据 Charter、ROADMAP、STATE、当前任务与 12 项引用 ADR。内联固定 diff 评审覆盖决定 fa8 至补充 b061d8c64785d09139c06c46780b3596c9985257；没有 Runtime／StageRunResult，阶段校验不适用，不复用旧 Runtime。

代码图谱探索三类连接服务、预设 DTO、自动配置、两页、表单和读取协调；对连接服务、发现、发布及预设执行影响分析。结果收敛为一个不可变应用策略、既有 DTO 的必需字段及局部装配，不增加平台权限框架或上下文依赖。结构结果不替代编译、事务和真实部署证据。

证据目录为 /private/tmp/wp0019-s1-writes-20261017-2b5lsyh6，0700；文件 0600。源代码阶段与实际生产分别结论，未完成的正式浏览器、原型一致性和只读后查保持 NOT_RUN，不将环境失败或旧结果改成通过。

最终 make decisions-sync、project-status、project-verify、architecture-guard、resume-context 通过，恢复输出 9,584 字节；未提高 12 KiB 限制。核心清单 core-evidence-digests.json 共 31 项，SHA-256 为 11b270a510b6a3f8ea0a00b442b3f9c41a4988b24a1ad7f72dce42c616488ba3，不含凭据、截图、后续控制面与最终远端 CI。源码阶段交付不表示新部署或完整 S1 通过。
