# WP-0019／S2：部署目录名称／摘要搜索交付

## 结论与固定输入

2026-10-23，按用户本轮明确接受的“名称／摘要”完成搜索子任务。先提交 ADR-0102，再实现、测试、构建并更新真实 Compose；正式浏览器五项检查、六次三尺寸／文字观察和原项目搜索区域对照通过。没有业务写入、真实厂商调用、正式发布或赋权，不删除数据制造空目录，不重复 S1 或原有七态／完整表单验收。

| 事实 | 固定版本 |
| --- | --- |
| 调查输入 | 583c08aa39878f01743cf980dfe72b50b7ec4f79 |
| 后端与搜索实现 | 3ffc5851f3461ce07776a7cdc82c696c46a7b86e |
| 最终前端／手机宽度修复 | 18988da7dee89a4ad3e4434c9448463f9df1f9c5 |
| OpenDesign Cloud 原项目 | 249f81f2-6f1a-46ff-988c-da3cdedb35e7 |
| 已接受原型 | f3216dffe717e194e96fa20b4afee7983a9ff420 |

原型入口：[原项目平台部署目录](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-deployments&state=success&review=1)。只使用原项目，没有复制或修改原型。原项目 HTTP 200；MCP 通道的 `Transport closed` 不等于项目不可用。首次观察进入正常登录页，后以原项目演示登录继续，未绕过身份状态或保存口令。

## 行为与真实红绿

- 正式列表可选 `q`，仅部署公开名称与驻留摘要。去首尾空白、64 个 Unicode 码点、不区分大小写的连续字面包含；百分号、下划线、反斜线不是通配符。不搜索模型编码、能力、状态、操作、参数说明或租户内容。
- PostgreSQL 在统一只读快照中先筛选，再由 MyBatis-Plus 分页／计数，排序仍为创建时间及 ID 倒序。没有手工 LIMIT／OFFSET、新表、缓存、框架或 Maven 依赖。既有数组投影仅迁移语句名和 Wrapper 条件登记，手工分页债务仍为 3。
- 查询、页大小与目录位置可从 URL 恢复；搜索改变回第一页。刷新、前进后退及详情返回保留查询。搜索框连续输入不因 URL 重挂载中断，旧 URL／请求不能显示或恢复旧投影；平台权限不足在读取仓储前拒绝。
- 零匹配使用原型指定文案并保留清除恢复，不冒充真实空目录。独立全目录摘要不变成匹配计数，partial／disabled 沿用原合同。

`http-red.log` 证明此前超长查询被忽略；`frontend-red.log` 的两项反例证明 URL 和服务端请求缺少查询；`postgres-red.log` 的三项反例证明筛选、同范围总数和偏移边界缺失。局部修复后真实数据库、HTTP 与前端通过。中途夹具只将驻留置空违反旧声明全空约束、租户会话被错误预期为 403，均按既有存储／身份合同修正测试，没有放宽产品断言。语句改名触发的旧登记失效按精确路径同步，没有降低守卫。

原型实测另发现继承的桌面搜索宽度在手机仍为 352px，原型要求 358px。`search-width-red.log` 有真实失败；仅在 S2 专用 CSS 按原型 680px 断点全宽，`search-width-green.log` 与最终前端验证通过，没有修改 S1 共享组件或原型。

## 验证与最新部署

| 验证 | 实际结果 |
| --- | --- |
| 完整后端 | `./mvnw -q clean test` 退出 0，364 类／1,322 项；失败、错误、跳过均 0，仅统计本轮开始后生成的报告 |
| 最终前端 | `make frontend-test` 类型、lint、55 文件／346 项及构建通过 |
| 新搜索浏览器 | 两项隔离 HTTP 浏览器检查通过：连续输入、分页重置、零匹配、迟到隔离与详情返回；未运行原有七态矩阵 |
| 工具与架构 | `make work-package-acceptance-test`、`make architecture-guard` 通过；56 项 Node、50 项架构测试通过，边界违规 0 |
| 正式生产浏览器 | `directory-search` 模式五项检查通过，业务写入 0、被阻断写入 0；包括平台正式登录、服务端查询、URL／详情恢复、零匹配及六次尺寸观察 |
| 原型搜索区域 | 390／1024／1440 与正常／200% 原生文字，六对标签／输入的 11 项样式共 132 个值完全相等，无容差放宽；截图人工复核 |
| 视口命中 | 390px／200% 匹配及零匹配，原生焦点后的标签、输入中央及底部实际命中均通过；不以长截图中固定导航的位置推断遮挡 |
| 运行与数据 | 七服务 healthy，四入口 smoke 通过；三基础设施容器和镜像未变，13 类业务计数及唯一草稿状态前后相同 |
| 历史与 CI | 650 项历史 Git 对象及摘要独立校验通过；[源码 CI 39886095688](https://github.com/liming-cloud/ai-platform/actions/runs/39886095688)九步骤实际 success，仅治理 job，不代替后端或真实厂商证据 |

四应用均由正式 Dockerfile 构建后更新，OCI revision 分别绑定上述前端与后端提交，不能以控制面 HEAD 代替运行版本。

最终 `make decisions-sync`、`project-status`、`project-verify`、`architecture-guard`、`resume-context` 均退出 0；81 项治理测试、30 个任务通过。首次收口触发恢复上下文 12 KiB 上限，合并任务入口中的重复历史叙述后通过，没有提高上限或删除原证据；逐命令退出状态保存在 `control-plane-verification.json`。

| 服务 | 实际镜像 SHA-256 |
| --- | --- |
| console-web | 81fbd5ae513432fa96cdb8fb9eb635b302dab58976a9d461a3d32340a311d801 |
| control-plane | 25e012b92b4af310c35f61ad1975c707279cb06c32c9aa8f56b6eef82b58b7da |
| query-runtime | e60bc07173aca428c4f6b79e2b5f56dd34c072cf1a80d52c17f880187ab6e84e |
| ingestion-worker | b1dbe9dc6cd7d4baf66f04cc184247e0faf354327901d89a8bc4a43e0b5dbdc4 |

正式可复验入口：`FOUNDATION_ACCEPTANCE_MODE=directory-search make acceptance-work-package WP=WP-0019 SLICE=platform-supply`；同时提供受保护凭据路径、新的独占输出文件及上述分别绑定的前后端 revision。默认和新模式均禁止草稿／验证／发布写入，原 `create-draft` 显式合同不变。

只读 `REPEATABLE READ READ ONLY` 事实：连接 3、发现 13、候选 787；唯一部署 `2112408560880058369` 仍为 DRAFT／版本 0。能力验证、预算预留、验证审计、发布、供应授权、供应审计、目录、配置、配置授权均 0。复用 Git 忽略、当前用户普通 0600 `pwd.txt`，没有要求重发密码、导出密钥或记录凭据摘要。

## 材料与边界

临时材料集中 `/private/tmp/wp0019-s2-search-20261023-pizqxmum`。`acceptance-search.json`、`search-parity-comparison.json`、`prototype-source.json`、`runtime-before.jsonl`／`runtime-after.jsonl`、`business-before.json`／`business-after.json`、`search-hit-observations.json` 分别绑定浏览器、原型、运行和数据事实；八个原项目相关资源与已接受提交的原 Git 字节一致。全部截图只在临时目录，可重建缓存已清理。登录页诊断截图不保留，失败日志保留。

`evidence-digests.json` 记录 70 项／4,036,432 字节，SHA-256 为 `95f06303ecf8e8713d83e8c134dbf01a5b13f64305c5921d9a9daa8fff21a8d1`，不包含清单自身及后续控制面／最终 CI 回执。文本产物已检查不含现有受保护密码；不对凭据文件做摘要。

工程助手为 Local Codex 个人插件 `2.2.0-rc.1+codex.local-20260929-051321`，显式加载 `engineering-assistant:wayfinder`、`tdd`、`code-review` 及安全 seam；固定 diff 内联自审见 `reviewed-source.diff`／`source-review.txt`。图谱探索 `ListPlatformModelDeploymentsQuery`、`PlatformModelDeploymentService.list`、仓储／映射、`readPlatformDeployments`、客户端和路由；仓储影响分析汇总 144 个符号、前端读取 4 个符号，实际只改本查询链和对应测试。由此保持领域端口不泄漏 MyBatis、权限先于读取、不增加上下文依赖。未创建 Runtime／StageRunResult，阶段校验不适用。

## S2 剩余事项

搜索子任务已闭合，S2 四项操作仍为 2／4，WP-0019 仍为 7／29；整包与 M6 未完成，不进入 S3。以下不由搜索通过代替：

1. 真实厂商联调：账号暂缺独立保留。按已接受方案准备硅基流动 Embedding／Rerank 有效来源、更新已过期的方舟 Chat 来源、账户适用费率及独立输入 Token 上界；现有草稿模型与 CHAT 声明不匹配，不能改旧数据或直接用于推理。材料齐全后对精确对象、修订和预算另行确认真实调用，正式发布／赋权分别授权。平台登录凭据不缺，不要求重发。
2. 正式能力验证、发布／赋权及发布后的完整原型流程尚未执行，之前的隔离链路继续有效，不能冒充真实能力可用。
3. 新登记 S2-P02：原型固定版本在 390px／200% 的既有部署记录卡片文字重叠，见临时 `prototype-search-390-2.png`。搜索标签和输入本身通过，本轮没有修改该记录区或把它列为视觉例外。完整 S2 原型验收前需恢复该设计的可操作性，具体修订候选仍按 ADR-0079 人工接受；本次不扩大实施。

当前没有已确认的 CI 账单阻断。最终控制面提交仍单独核对其精确 CI，不用源码绿灯代替。
