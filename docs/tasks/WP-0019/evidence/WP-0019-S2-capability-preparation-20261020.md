# S2 受控能力验证准备与最小协议基础

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0019 |
| 验证提交 | dbf482ce1d9eee0af2cc1716ec00d648113b8e6b |
| 验证范围 | 厂商／模型／预算／样本的可审阅执行方案，以及平台专用最小协议源码与离线反例；不证明完整能力验证或生产部署通过 |
| 输入提交 | b4c8088d1b4197fdb235906cb3c40c820c3fa51f |
| 日期 | 2026-10-20 |
| 原型 | OpenDesign Cloud 原项目 249f81f2-6f1a-46ff-988c-da3cdedb35e7，f3216dffe717e194e96fa20b4afee7983a9ff420 |
| 产物根目录 | /private/tmp/wp0019-s2-verification-20261020-dSCrBJ，0700；详细日志留在仓库外 |

## 本轮闭环与仍未交付的部分

[受控能力验证方案](../s2-capability-verification.md)明确推荐“方舟 Chat + 硅基流动 Embedding／Rerank”，包含三个精确模型、三类请求与拒绝条件、九个版本化合成样本、每部署三次／128 字符／64 输出 Token／30 秒及费用上限建议。建议不是执行授权，硅基流动当前适用费率与来源未就绪，RERANK 预设为 `NONE` 还须闭合受控目录候选来源合同，不能假定免费或借用方舟发现 AK/SK。

本轮源码补齐既有 `PlatformModelDeploymentProbe` 的 OpenAI-compatible 最小实现，平台入口仅依赖 `PlatformProviderCredentialResolver`，不构造虚假租户或注入租户密钥解析器。标准 Chat／Embedding 继续使用 Spring AI，共享协议细节而隔离凭据拥有者。精确单能力才允许进入网络：RERANK、混合或未验证流式意图在出站及秘密解析之前拒绝；HTTP 200 的空正文、空向量、非有限值与全零向量不能报成功；Chat 显式发送 `max_completion_tokens=64`，SDK 状态码重试为 0。

这只是最小协议基础：没有实现 thinking 参数、精确维度／索引数量与 usage 绑定、三样本编排、整轮截止时间／计数／费用预留或独立 RERANK 适配，不将 SDK `maxRetries=0` 扩张解释为所有底层网络行为均已核验。厂商不支持受控输出参数时失败关闭，不退回无界请求。网络重定向、总资源边界和完整安全反例仍须在后续完整验证中闭合。

新完整草稿的 `PLATFORM_DEPLOYMENT_CONTRACT_PENDING` 保护没有解除。真实验证、原子发布和生效授权还需工程实施与精确执行许可；本轮没有新增 HTTP／CLI 参数、持久化字段、Maven 依赖或权限框架。独立准备子任务完成，产品操作计数不变：S2 2／4、WP-0019 7／29，工作包与 M6 未完成，不进入 S3。

## 调查依据与现有对象

复用根目录受保护 `pwd.txt`，通过正式平台登录读取供应与精确草稿，没有复制或输出凭据、Cookie、Token、密码摘要或内部秘密引用。只读调查不是重复产品验收，没有发起发现或推理，没有创建草稿或修改业务数据。登录建立会话不计作业务资源写入。

`current-model-facts-v2.json` 确认方舟连接 `2112058351855599618`／修订 2 的两个候选。其中 `doubao-seed-2-1-turbo-260628` 仍待管理员确认，官方模型表也列出其文本生成与关闭思考支持，作为拟选 Chat 模型；实际调用权限与执行时来源有效性尚未证明。

既有合成草稿 `2112408560880058369` 仍为 DRAFT／版本 0，选择的是 `doubao-embedding-vision-251215` 并声明 CHAT。它用于前轮保存／详情验收，不能作为真实 Chat 验证对象。原草稿及分类审计不改写；正确来源、分类与新草稿后续另行准备。最初调查遗漏候选读取所需 `expectedConnectionVersion`，HTTP 400 日志保留，随后补齐参数后读取 200，不把错误查询当作零候选事实。

另一个未选用的旧 CUSTOM 草稿连接候选读取返回 500，记录在同一只读事实文件；没有依据把它当作有效来源或宣称所有历史连接健康。该旧草稿的缺失发现恢复问题保留给 WP-0019 来源恢复子任务，不自动刷新、删除或扩展本轮修复。

## 真实红绿与完整源码验证

| 验证 | 可复核结果 |
| --- | --- |
| 最初测试准备 | `red-bounded-probe.log`、`test-compilation-diagnostic.log`：测试使用 Jackson 3 已弃用方法，`-Werror` 拒绝；修正测试 API，不作为业务红灯 |
| 实际行为红灯 | `red-bounded-probe-behavior.log`：12 项中 7 个失败；空正文、三类无效向量、错误意图分派、缺输出限制及平台探测 Bean 缺失 |
| 同组绿灯 | `green-bounded-probe.log`：同一 12 项全部通过 |
| 平台回归 | `provider-regression.log`：补平台专用解析器、出站先拒绝、RERANK 不误路由、429 恰好一次及成功／失败秘密关闭；三个测试类共 16 项通过 |
| 完整后端 | `backend-full.log`／`backend-full.exit`／`backend-test-summary.json`：`./mvnw -q test` 退出 0；353 类、1211 项，失败、错误、跳过均为 0；353 份报告均为本轮运行生成，无旧红灯报告混入 |
| 架构 | `architecture-first.log`：50 项及双端守卫通过，后端边界违规 0，无 POM 修改、依赖环或阈值放宽 |
| 控制面 | `preparation-governance.log`：`make decisions-sync project-status project-verify architecture-guard resume-context` 退出 0；81 项治理测试、30 任务及 650 项历史资料通过；最终恢复入口修改后再独立校验 |

HTTP 协议只访问本机隔离服务，使用测试内合成秘密；真实厂商推理为 0。新增四项平台回归是在最初红绿后补充，未伪称全部测试均先写后实现。没有重复 S1、草稿创建、七态页面或截图验收；前端未修改，不重跑无关前端与浏览器矩阵。

## 原型、运行版本与评审

`prototype-binding.json` 对原项目实际 HTTP 九份入口依赖资源与接受提交逐字节比较，一致；未修改原型、未创建副本或截图。MCP 元数据通道返回 `Transport closed`，但原项目实际 HTTP 读取正常；不将工具通道故障描述为原型不可用，也未重启应用。

`runtime-unchanged.json` 独立核对七服务 healthy。前端仍为 `98ffdd5b0fa6cc388cef82d8ca8fca0782fd4671`／`sha256:ed800fb0a9225d550d561e05b3473b4710b0c013cde0df9a01f02b84e172f0a7`，三后端仍为 `e28aa34572b1894908ae538f7dc74071a00fedf9`，镜像分别为 control-plane `sha256:9aa1125f21b3bef864ef8ff9adb8d5b690ed2ee68846e8ff81429da6bb7693e4`、query-runtime `sha256:9d0f531bf420a05b72715a4c869e25e584ebb94868b4f66fc3a6164af1f74830`、ingestion-worker `sha256:251c4bc395e3dfb20dc1fa7203ad1d4c1afb4fece04751440ce2f3b1f8a6e463`。本轮没有部署源码准备提交，不把当前 HEAD 当作后端运行版本，不提升前轮部署验收事实。

固定 diff 为输入提交至验证提交，保留 `reviewed-source.diff`。图谱探索 `PlatformModelProbeProviderService`、`OpenAiCompatibleConnectionProbe` 与 `PlatformProviderPresets`，对现有协议类执行二层影响分析，返回 23 个符号；据此复用既有端口和协议实现，只在平台凭据拥有者处增加小适配器，不改变核心类型或上下文依赖。同名探测 DTO 的图谱消歧返回了 HTTP 类型，随后精确读取扩展 DTO 确认没有修改公共输入。

Local Codex 使用当前安装个人版工程助手，显式加载 `engineering-assistant:wayfinder`、`research`、`codebase-design`、`tdd`、`code-review` 及安全 seam 规则；按 ADR-0095／0100 的已接受范围内联执行。本子任务未创建或推进 Runtime，未产生 StageRunResult 或 QualitySnapshot，阶段结果校验不适用，不借旧 Runtime 宣称本轮完成。评审确认最小实现与完整验证之间的限制，未将真实厂商合同、价格或后续工程标绿。

源码验证提交仅绑定六个 Java 文件；方案与本证据一同作为控制面提交交付。方案全文 SHA-256 为 `425ccbabe82880d4b1f7777a8b002ad35dbbad31753e1460535241f96e73306d`。`fixed-diff-review.txt` 保存评审结论；`evidence-digests-final.json` 记录本轮 23 个文件、1,756,466 字节，清单 SHA-256 为 `56b356ff98eb3e67cdc644bc109909d60c3d5888f7d279df92b18801c90042f9`；前次方案摘要及清单原样保留；不含自身、之后的控制面提交和云端 CI 回执。日志及报告 0600、根目录 0700，浏览器可重建缓存已回收，没有截图。

## 下一动作

沿本方案继续三样本、总预算和完整协议实现，再准备精确真实调用材料。后两类已发布来源、适用费率、正确 Chat 新草稿和三类真实合同均未就绪；本轮不要求用户重新发送平台密码，不发起云端资源创建。目录搜索和原子发布／赋权仍在 S2 后续，S3 未启动。最终控制面提交推送后须单独核对其实际 CI，不沿用前轮成功。
