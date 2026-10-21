# S2 原子供应发布与当前租户授权边界

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0019／S2 |
| 输入提交 | 88e2eb30006186cc2f9550f3b12fb2c58f93225a |
| 验证源码与四应用运行提交 | c36450c101e63eabd42db52be4c73fb9c8533c38 |
| 日期 | 2026-10-21 |
| 结论 | 源码、真实隔离事务、Compose 与授权投影浏览器通过；真实厂商、正式发布／赋权和发布后完整原型流程未验收 |
| 产品进度 | S2 2／4、WP-0019 7／29；整片、整包及 M6 未完成 |

## 本次实现

按 ADR-0095／0100，原发布入口以服务端会话、当前平台权限、精确来源修订、固定三样本及费用预留终态为前提，在短事务内保存安全目录、独立发布、全部冻结租户授权、部署 CAS 和审计；末尾再查发现与验证时效。审计失败、过期、轮换、并发 loser 均不留下半条发布或授权。RERANK 保留独立候选上限，不落入 Embedding。

精确供应事实及带双方预期版本的撤权接口保留历史；停用和撤权不受新增写入暂停限制。旧目录写入口只恢复已经原子建立、当前仍有效的目录，返回 200，不再独立造目录。当前租户的来源／授权交集在 Page／IPage 分页前计算；保存、启用、配置授权和每次新使用复核同一交集。组织拥有者通过 security-core 的事实端口提供当前活动与功能状态，没有新增反向 Maven 依赖。

前端发布／停用后只显示活动授权，实际为零显示“0 个租户”，未知保持“暂未取得”，不能回退拟授权计划。供应投影的 ID、版本、计数、集合和时间畸形均拒绝。只补 S2 必需的现有消费者边界，不交付 S3／S4 的配置版本、租户发现 UI 或新启用生命周期。

## 反例与验证

外部材料均在 `/private/tmp/wp0019-s2-publication-20261021-x6qqp5ga`。固定 60 路径 diff 为 `reviewed-source-final-2.patch`，SHA-256 `d9b0211a4a37089564a1d9128926294c029a766cc24732ca263c9a3df530ed7b`；`review-scope-final-2.json` 绑定各文件，提交前无变化。内联自审及阶段范围见 `fixed-diff-review-final-2.json`、`delivery-validation.json`，不冒称独立人工接受。

| 检查 | 实际结果 |
| --- | --- |
| 先失败后修复 | `red-supply-tenant-boundary.log` 捕获旧全局分页回退及无供应仍可用；`red-publication-late-discovery.log` 捕获末尾发现过期；`red-publication-view-binding.log` 捕获畸形投影；旧部署浏览器 `browser-red` 将计划误作活动授权 |
| 真实 PostgreSQL | 发布／目录／授权原子提交、两部署同模型、分页同范围总数、并发唯一成功、审计失败回滚、撤权／停用保留历史、源轮换／过期及旧配置不能绕过撤权均通过；只使用 Testcontainers 合成对象 |
| 完整后端 | `./mvnw -q clean test`：362 类／1,311 项，失败、错误、跳过均 0，`backend-final-all-5.log`、`backend-final-summary.json` |
| 回归暴露的问题 | 助手旧无供给夹具改为完整合成来源，保留原创建、冻结引用和租户隔离正例；正文取消偶发普通 IOException 被误判，在 `backend-final-all-4.log` 实际复现，局部修复后定向及完整测试通过；不放宽两秒结束／单次发送断言 |
| 前端与脚本 | 55 文件／343 项及类型、lint、构建通过；缓存／S2 分派 23 项、最新脚本语法通过 |
| 架构与治理 | 50 项架构测试及双端守卫；手工分页 4→3，内联 SQL 文件 11、依赖违规 0；81 项治理、30 任务和 650 项历史对象校验通过 |
| 云端 CI | [Actions 39555827798](https://github.com/liming-cloud/ai-platform/actions/runs/39555827798) 实际 success；仅治理 job，不替代本地 Maven 或部署浏览器 |

## 部署、浏览器与原型边界

四应用镜像与容器 OCI revision 分别核对，均为前述源码提交。七服务 healthy，`make deploy-smoke` 四入口通过；PostgreSQL／Redis／MinIO 容器 ID 未变化。

| 服务 | 实际镜像 SHA-256 |
| --- | --- |
| console-web | ca415c1ef00354f03809c7a66e981c6999f3731b83037ff8ff3ac50dd3068678 |
| control-plane | b94f1b9d027155d7904b01bf18fe6c701366ff27f7d822ebc9d5bbb7471db549 |
| query-runtime | ec8c148a07c6eb02973383206b2650ab13461a105b353e1fe3e45949ff880c15 |
| ingestion-worker | 4821184849f07cc0f4e2b3aebd3028252c60b0023f3ac56293603dcfad710d76 |

复用 Git 忽略的普通 0600 `pwd.txt` 完成正式平台登录。`scripts/development/acceptance-platform-supply-publication.cjs` 对唯一现有草稿先正式读取，再仅隔离 GET 发布／撤权投影；其余业务写入被阻断。新部署 `browser-green/publication-browser.json` 的 390／1024／1440、100%／200% 六次观察通过，停用后仍显示当前授权而非计划。额外只补 390／200% 实际视口，授权文本中心命中自身，排除全页截图中的粘性导航拼接疑点，见 `browser-viewport`。没有真实发布、撤权、推理或赋权。

OpenDesign Cloud 唯一原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，[平台部署详情](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-deployment-detail&state=success&review=1)，接受版本 `f3216dffe717e194e96fa20b4afee7983a9ff420`。原项目四份相关资源均 HTTP 200，并与该版本的既有绑定逐字节一致。独立匿名浏览器经原型自己的合成登录进入详情，最终参考为 `prototype-review-7`；人工查看原项目与生产 1440／100%、390／200% 的截图。未修改原型、布局或颜色，截图均只在 `/private/tmp`。

**本轮不宣称发布后全状态原型验收。** 原项目 success 详情表示“验证通过”，本轮点击其模拟发布仍反馈“部署未发布，状态未变化”，没有得到发布后／撤权后的可达参考。这里只证明固定来源和现有布局对照、生产授权事实显示与不遮挡；发布后的完整一致性须在正式流程验收中独立闭合。前期观察脚本停在登录页或缺演示准备的记录保留，不当作产品失败或一致性证据；不据此断言必须修改原型。

部署前、部署后、浏览器后分别执行 REPEATABLE READ READ ONLY：连接 3、发现 13、候选 787；唯一部署 `2112408560880058369` 仍为 DRAFT／版本 0；验证、目录、配置、配置授权、预留、验证审计均 0。新增发布／供应授权／供应审计也均 0。迁移只建立空事实表及约束，没有给历史目录补授权，没有重启 Docker、清卷或删除对象。

100 项临时材料／8,166,024 字节的固定清单为 `evidence-digests.json`，清单 SHA-256 `a698ec89284cd37d5eefee893342b6a59e2191d570e5ef9bc3fdb63cad43a283`。可重建浏览器缓存均已清理，截图、失败日志和验收材料保留；不包含凭据来源内容或其摘要。

## 剩余阻断与下一步

- 两条已发布方舟连接仍有推理 Key 配置，但验证和最新发现已过期，正式候选为零；这不是厂商没有模型或当前 Key 已验证有效的结论。无需重发平台密码或方舟 Key，须在精确授权范围内更新来源后另建正确 Chat 草稿。现有 CHAT 草稿实际选中多模态 embedding，保留原事实，不将其改写为合格 Chat。
- 三条现有连接均指向方舟，没有硅基流动来源。人工可在“模型治理→模型连接→新增供应商连接→硅基流动”配置受保护推理 Key，或提供其既有 0600 文件路径；不要在聊天发送密钥。方舟 `AccessKey.txt` 不能替代该推理 Key。
- 真实三类调用仍需账户适用费率、独立输入 Token 上界、精确目标／修订／次数／费用授权；缺材料保持零外呼。发布与供应赋权再单独授权，不能用隔离成功代替。
- 搜索仅名称／驻留摘要与原型标注一致，而原型通用演示实际上匹配整行；已询问选择，尚未接受，不实现搜索或自行扩大公共契约。
- 正式发布后与撤权后的原型参考／流程一致性仍待查明并独立验收；不把本轮局部显示通过提升为这部分通过。

当前没有已确认的 CI 账单或自动审批容量阻断。当前尚无真实供给事实时可使用此前镜像回退；首次正式发布后，最低安全回退版本必须保留本次供应读取与提交边界，不可仅换回忽略授权的旧版本。S2 余两项及 S3～S6 二十项未完成，不进入 S3，不改 WP-0019／M6 完成事实。

工程助手环境为 Local Codex 个人插件 `2.2.0-rc.1+codex.local-20260929-051321`，能力来自当前安装的技能与 MCP；显式加载 `engineering-assistant:wayfinder`、`codebase-design`、`tdd`、`code-review`，使用其安全 seam／证据审计控制文件。代码图谱探索供应服务、目录／配置仓储、当前拥有者和页面；影响分析 `ModelConfigurationAccessService` 81 符号、`PlatformModelDeploymentService` 32、`OpenAiCompatiblePlatformCapabilitySamples` 15，促使统一当前交集、避免 Maven 环并局部修复取消分类。内联任务没有 Runtime／StageRunResult，阶段结果校验不适用。
