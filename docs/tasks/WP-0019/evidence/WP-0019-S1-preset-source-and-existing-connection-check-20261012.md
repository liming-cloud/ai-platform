# WP-0019 S1：预设来源源码验证与既有连接复验

| 字段 | 值 |
| --- | --- |
| 责任任务／范围 | WP-0019／S1 的预设来源、创建校验和仓储子任务；既有平台连接受控复验 |
| 调查基线 | e0bc70e23ce00976be7da6af9423f29d5245f104 |
| 被验证源码提交 | c52669a9bdba52b25aba2efdbd7694c617df6aa1 |
| 原型项目 | OpenDesign Cloud 原项目 249f81f2-6f1a-46ff-988c-da3cdedb35e7 |
| 已接受原型 | d5deddaa1955814c5f08795b0b1ca58a7e6ac784，见[人工接受记录](WP-0019-S1-prototype-acceptance-20261012.md) |
| 原型入口 | platform-model-connections、platform-model-connection-detail；通过原项目取得当前预览地址 |
| 执行日期 | 2026-10-12 |
| 结论 | 本批次源码验证通过；既有连接发现失败关闭；S1 功能与生产原型一致性未完成 |

## 本批次源码

五厂商、十一能力组合的非秘密预设由后端目录提供。创建必须提交预设 ID、版本和能力意图；后端拒绝未知／过期预设、能力／发现协议／区域／固定地址不一致及非法业务空间。CUSTOM 草稿同样执行现有公网 HTTPS 出站策略，探测仍须再次检查，不能复用保存时的 DNS 结论。

连接修订持久化预设来源和能力意图；旧记录保留空来源，不按地址猜测或补造已验证事实。公开摘要只增加预设 ID／版本／能力，不返回地址、工作空间或凭据。RERANK 在本批次只保存能力意图，独立 Port 和真实执行验证仍归 S2，不能视为已可用能力。

修正非方舟请求携带单个 AK 或 SK 未被拒绝的问题；HTTP 请求对象禁止通过默认 record 字符串回显三个秘密输入。前端仅同步新创建契约及清除一次性输入，尚未完成五厂商表单、详情和发布交互的原型承接。

触及的连接仓储从内联 SQL 改为 MyBatis-Plus，分页保留同范围总数与 `created_at DESC, id DESC` 排序。原全局名称／幂等咨询锁迁入 XML，幂等、重名、CAS 和回滚约束保留；Java 内联 SQL 精确基线从 15 减为 14，未放宽其他门禁。新增迁移只在隔离数据库测试执行，没有应用于当前 Compose 数据库。

## RED、回归与评审

| 反例 | 修复前实际结果 | 修复后 |
| --- | --- | --- |
| 读取预设目录 | 路由落入连接标识解析，测试报错 | 正式静态目录路由通过，无秘密或网络读取 |
| 创建缺少预设来源 | 旧请求仍返回 201，拒绝断言失败 | HTTP 拒绝，未进入创建服务 |
| 保存来源再恢复 | 数据库恢复后的预设为 null，断言失败 | 来源与能力往返一致，探测更新不丢失，过期 CAS 拒绝 |
| 私网 CUSTOM 草稿 | 创建未拒绝，断言失败 | 出站策略拒绝且不保存凭据 |
| 非方舟部分 AK/SK | 构造命令未拒绝，断言失败 | 任一单独字段均拒绝 |

| 验证 | 本次结果 |
| --- | --- |
| 模型上下文及 OpenAI-compatible 扩展 clean test | model-runtime 216 项、扩展 16 项，均无失败／错误／跳过 |
| 完整 `./mvnw -q verify` | 347 个测试类、1116 项，失败／错误／跳过均为 0 |
| `make frontend-test` | 52 个文件、268 项通过；类型、lint 和构建通过 |
| `make architecture-guard` | 50 项测试及前后端守卫通过；后端依赖违规 0、超预算包 0、内联 SQL 文件 14 |
| `make project-status` | 产品前沿与当前执行任务均为 WP-0019；M6 未完成 |
| `make project-verify` | 81 项通过；30 个任务及 650 项历史资料可校验 |
| 固定差异与秘密检查 | 38 个文件精确评审、暂存；真实登录凭据值未命中；fixture 未修改 |

复现源码回归命令：

```sh
./mvnw -q -pl backend/contexts/model-runtime,backend/extensions/model-providers/provider-openai-compatible -am clean test
./mvnw -q verify
make frontend-test
make architecture-guard
make project-status
make project-verify
make resume-context
```

Local Codex 显式加载当前安装包的 `engineering-assistant:wayfinder`、`engineering-assistant:tdd`、`engineering-assistant:code-review` 及相关安全边界参考，按固定差异内联评审正确性、复杂度、冗余、性能、架构与测试。未创建 Runtime 或 StageRunResult，阶段结果校验不适用；没有复用 WP-0017 Runtime 宣称本次质量通过。

代码图谱对 `PlatformModelSupplyWorkspace`、`PlatformProviderConnection`、`ModelRuntimeCoreAutoConfiguration` 执行探索及影响分析，分别涉及 4、149、27 个符号，据此同步创建调用方、恢复路径和装配测试。没有新增跨上下文依赖、扩展内部包引用或运行进程；图谱不作为编译或生产证据。

详细日志与固定差异清单仅存 `/private/tmp/wp0019-s1-production.dSgSrg`，其中 `evidence-manifest.json` 保存非秘密日志摘要，`source-diff-review.md` 保存评审范围。RED 和中间失败日志未覆盖；首次隔离分页装配缺少拦截器及新 Mapper 的自动配置测试失败也保留，修复后才执行最终 clean 与完整回归。临时文件不作为长期完成门禁的唯一依据，源码和测试均可从上表固定提交复现。

## 既有登录与连接的受控复验

用户明确授权复用已有平台连接，并限定在 `/private/tmp` 的 WP-0017～WP-0019 目录内查找既有 0600 验收输入。限定查找找到有效的现有平台登录文件；只在进程内存使用，没有复制密码、Cookie、Token 或秘密摘要。无需人工再次提供登录凭据。

首次广泛查找及浏览器会话探查提议被自动审批拒绝，命令未启动。随后按上述用户明确授权缩小范围，不探查浏览器会话；此审批问题已解决，不是产品测试失败或当前容量阻塞。此前精确登记路径已不存在、初始化密码登录 401、脚本 CSRF 403 及分页字段读取失败均保留，不计为厂商调用。

2026-10-12T02:30:39Z，正式 CSRF／登录接口成功；读取会话和连接目录均为 200。仅对既有连接 `2093675537095458817`、输入版本 `3` 调用一次 `model-discovery`；HTTP 200，业务结果 `succeeded=false`、候选数量 0；随后正式退出会话成功。没有创建连接或模型、推理、发布或重试厂商请求。

`REPEATABLE READ READ ONLY` 事实核对只查询计数、类型、状态和时间：该连接是 `OPENAI_COMPATIBLE / OPENAI_MODELS_API`；仅有 `INFERENCE_API_KEY` 槽，没有 `DISCOVERY_ACCESS_KEY_ID`／`DISCOVERY_SECRET_ACCESS_KEY`。运行地址与已接受的方舟北京地址匹配，但旧发现协议并非方舟独立控制面协议；不能从推理 Key 推导缺失 AK/SK，也不能将旧记录补认为合规新连接。未读取秘密字段。

本次失败码为 `OPENAI_COMPATIBLE_DISCOVERY_ENDPOINT_REJECTED`。容器内 DNS 查询成功，但只解析出 1 个位于 `198.18.0.0/15` 的保留网段地址，公网地址 0；现有出站策略明确拒绝该网段。源码调用顺序证明拒绝发生在秘密解析与厂商 HTTP 之前，因此本轮没有到达厂商，不能报告真实厂商合同通过。未放宽 SSRF、域名或网络策略。

旧连接仍显示 `VERIFIED`，最后成功探测是 `2026-08-29T13:55:09.957892Z`；本次失败发现时间为 `2026-10-12T02:30:39.400643Z`，没有有效期。这确认了 S1 还需处理已验证连接重发现的状态／时效刷新，以及连接 CAS 和发现记录的一致提交；旧状态不作为当前可用性证据。

## 当前部署对象与剩余门禁

本轮只读确认七个 Compose 服务健康，没有部署、重启 Docker、清缓存、清数据卷或删除历史验收对象。前端运行提交为 `8ade6bbe15a53fdf73a1fea794e642632f59b835`，镜像为 `sha256:7652c5de0335b5ffbf8f94a59ab7d587c7d446cc154482389bb74efac1356e54`。

后端三个进程实际运行提交均为 `46fb0dd5f0149e7bac155b87a8aa9a9f21d16914`，与本次源码提交分别记录：

| 运行进程 | 镜像摘要 |
| --- | --- |
| control-plane | sha256:a4bf11ac45b22546c655bf1860216f4f2dc08d43fbe6a01e34f40a89a80c02dc |
| query-runtime | sha256:e86ae969f45e8422cc0088ec0be328be94fc64488c3d76201f2528e80218ead0 |
| ingestion-worker | sha256:0c76fe72dddf8835107b74eeabc2a6730e26eb8d7140002499dcb5b576f7a347 |

S1 剩余门禁是五厂商生产表单及详情／探测／发现／发布交互、状态时效与原子 CAS 反例、正式验收入口、可用出站环境及真实厂商发现认证、最新 Compose 会话浏览器、两页及弹窗七态三尺寸和专项原型一致性。本轮 CUA 入口因工作区符号链接配置在启动前失败，未发生浏览器操作；同时前端尚未完整承接，因此不能将浏览器未执行仅归因于环境，也不能以源码回归替代视觉验收。没有生成或提交截图，没有修改原型。

本证据不提升二十九项生产操作状态，不标记 S1、WP-0019 或 M6 完成，不进入 S2～S6。
