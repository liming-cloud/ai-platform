# S2 三类能力编排与验证事务交付

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0019／S2 |
| 输入提交 | e45393fcc8ffd9b044e623499782780029766e9f |
| 验证源码与四应用运行提交 | 73fc9ee035d0b157861439f422f5de5af6a40bdd |
| 日期 | 2026-10-21 |
| 结论 | 编排源码、隔离真实事务、部署及定向失败反馈通过；真实厂商合同与原子发布未完成 |
| 产品进度 | S2 2／4，WP-0019 7／29；整片、整包和 M6 未完成 |

## 已交付行为

依据 ADR-0095／0100／0101，Chat／Embedding 通过 Spring AI 和受控传输，RERANK 使用独立端口及固定协议；三样本、128 个 Unicode 码点、64 输出 Token、整轮 30 秒、零自动重试不放宽。默认没有账户报价，缺价格、独立输入 Token 上界或完整预算即零外呼；预留不是账单或平台月度硬预算。

首次发送前的短事务冻结整轮预留、精确部署／连接修订、操作者权限修订、声明／计划／报价及固定样本摘要；同一部署修订仅一活动尝试。网络不持有业务事务；取消和剩余期限覆盖凭据解析、响应头及正文读取。最终短事务复核当前权限／平台会话、来源发布／发现根、报价、期限及 CAS，原子写验证事实、成功状态、预留终态及独立审计；末尾审计失败回滚成功事实，外呼前预留保留，未知用量不退款或重发。只有精确三个固定样本全部通过才可成功。

推理 Key 由一条安全投影绑定当前已发布连接修订，不回退新 Key、不读取方舟发现 AK/SK。组织上下文通过现有 security-core 事实端口核对主体／权限，未形成反向 Maven 依赖。移除旧平台最小探测并行路径，仍使用中的租户探测保留。原子供应目录／授权尚未接入，所有部署发布仍失败关闭。

修复 HTTP 200／`verified=false` 静默刷新：页面保留草稿并显示白名单中文原因，不输出厂商正文或未知错误。不改变公开 JSON 键、页面导航或已接受原型。

## 反例、源码及真实事务

| 检查 | 实际结果及本轮外部材料 |
| --- | --- |
| 协议与安全 | 先复现缺编排、未绑定整轮预留、工具内容混入、错型参数、超大用量截断及正文取消分类；修复后通过。`red-capability-protocol.log`、`red-before-send-plan.log`、`red-sample-parameter-and-tool-content.log`、`red-usage-overflow-and-stream-cancellation.log` |
| 当前身份与固定样本 | 权限类型比较错误在真实 PG 正例暴露并修复；任意三摘要不能冒充固定清单。`integration-tests-final-targeted-2.log`、`red-fixed-sample-manifest-valid.log` |
| 隔离 PostgreSQL | 完整预留、并发排斥、轮换／过期／撤销／迟到拒绝、CAS、末尾审计失败回滚、网络不持有事务及三样本成功提交均通过；不是生产业务对象或厂商结果 |
| 完整后端 | `./mvnw -q clean test`：360 类／1,286 项，失败、错误、跳过均 0。`backend-final-all-3.log`、`backend-final-summary.json` |
| 前端与脚本 | `make frontend-test`：55 文件／336 项及类型、lint、构建通过；缓存与 S2 分派脚本 23 项通过。`frontend-all.log`、`browser-cache-and-s2-regression.log` |
| 架构与治理 | 50 项架构测试及双端守卫、81 项治理、30 任务和 650 项历史对象校验通过；内联 SQL 文件 12→11，包规模、依赖及安全断言未放宽 |
| 云端 CI | 源码 [Actions 39545445270](https://github.com/liming-cloud/ai-platform/actions/runs/39545445270) 九步骤 success；仅治理 job，不冒称云端执行 Maven 或浏览器。`source-ci.json` |

测试装配缺新 Mapper、错误测试文件名及 ArchUnit 测试分层问题的中间失败保留；最终修复没有删除反例或排除测试。固定 60 文件 diff 内联自审修正当前权限、固定样本、超量和失败反馈，见 `fixed-diff-review.json`、`reviewed-source.patch`、`source-files.json`；不冒充独立人工评审。

## 最新部署与受影响浏览器

真实 Compose 只更新四应用；七服务 healthy，`make deploy-smoke` 四入口通过。以下实际镜像与容器的 OCI revision 均为前述源码提交，分别核对，未把后续文档提交当成运行源码：

| 服务 | 实际镜像 SHA-256 |
| --- | --- |
| console-web | 8c8874707ed1f4460ae26f16940bfc6ed0d609c64a97bccec8d49e1b30125e17 |
| control-plane | c230828d7cc9c2336010700482097568247b0f909b0fa6b22e0f3daa5c1f9bbc |
| query-runtime | b7a1f1e5853363d1e685da6b1e918a97f394b0a1cbb24fd6eb52b7027ea19eff |
| ingestion-worker | 1294808cf335176ed9bf8c5a0bc1e82761a1e593f8436da89d96b9f7d17e7d45 |

复用普通 0600、Git 忽略的 `pwd.txt`，正式登录 HTTP 201。新增浏览器脚本只对精确已有草稿隔离注入一次未通过响应，其余业务写入全部阻断；旧部署缺失败提示的红灯保留，最新部署转绿。390／1024／1440、100%／200% 共六次布局观察无横向裁切。全页截图的粘性导航疑点通过额外 390／200% 实际视口核查排除：提示和执行按钮中心均命中自身，实际视口完整可见。`feedback-green/feedback-browser.json`、`feedback-overlay/feedback-browser.json`；这不是正式能力验证或厂商合同通过。

OpenDesign Cloud 唯一原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，接受版本 `f3216dffe717e194e96fa20b4afee7983a9ff420`，[平台部署入口](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-deployments&state=success&review=1)。原项目 `prototype.js`、`prototype.css`、`object-workspaces.js`、`directory-pagination.js` 均 HTTP 200 并与接受版本逐字节相同。人工查看实际截图，受影响失败反馈可见、可操作；未修改或复制原型，未重跑 S1、草稿或整页七态矩阵，不外推为全部 S2 原型验收。截图全部在 `/private/tmp`。

## 数据保持与剩余阻断

前后 REPEATABLE READ READ ONLY：连接 3、发现 13、候选 787；唯一部署 `2112408560880058369` 保持 DRAFT／版本 0；能力验证、目录、配置和配置授权均为 0。新预留／审计表也为 0；新增迁移仅建立空表。PostgreSQL／Redis／MinIO 容器 ID 前后相同，没有重启 Docker、清卷、重置或改写旧业务行。`facts-before.json`、`facts-after.json`、`runtime-before.json`、`runtime-after.json`。

正式候选接口当前为 0；只读数据库的连接预设为 CUSTOM、VOLCENGINE、未声明各一，不从缺失预设猜测厂商。API 检查摘要未提取嵌套预设，其 UNKNOWN 不是业务分类，准确预设以 `source-presets-readonly.json` 为准。现有 CHAT 草稿选择多模态 embedding，仅证明保存，不能用于真实 Chat 验证。

剩余工作及人工输入：

- 有效来源：在平台模型连接页刷新可用发现；新 SF 连接须通过正式表单建立并分别发现／发布，不改旧连接凑数据。需要 SF 受保护凭据来源，不能以 `AccessKey.txt` 的方舟发现 AK/SK 替代。
- 真实执行：固定账户适用价格、含协议开销的输入 Token 上界、正确模型草稿和精确修订／次数／费用，再请求单独调用授权；本轮外呼与真实推理均为 0，不重新索取平台密码。
- 原子发布：供应目录、实时租户授权、审计和现有消费者安全边界仍待实施；部署发布与赋权另行精确授权。
- 搜索：原型标注名称／摘要，演示代码实际整行匹配；用户询问一致性后已说明差异，等待聚焦选择，没有把询问登记为接受。

当前没有已确认的登录、云端账单或自动审批容量阻断。一项忽略凭据修订的临时适配提议曾被自动审批拒绝，未落盘；直接改为真实修订查询后已通过源码及部署验证，不绕过拒绝。不能因其他工作通过而把真实厂商、发布或 S2 标记完成；S3～S6 不启动。

## 复核定位

外部目录 `/private/tmp/wp0019-s2-capability-production-20261021-WOeFoe` 为 0700，证据文件 0600；固定清单 `evidence-digests.json` 47 项／3,331,124 字节，SHA-256 `2777483880f89053d2c38df15635f5e05c88f07181915fe6d434f835d2281c61`。不含凭据、受保护运行配置、自身及此后文档收口／推送回执，不重写历史清单。

当前 Local Codex 个人包 `2.2.0-rc.1+codex.local-20260929-051321` 已显式加载 `engineering-assistant:wayfinder`、`codebase-design`、`tdd`、`code-review`，使用 HITL／安全 seam 与固定 diff 评审；内联执行，无 Runtime／StageRunResult，阶段结果校验不适用。图谱探索并影响分析 `PlatformModelProbeProviderService`、`PlatformRoleBindingMapper`、`PostgresEncryptedPlatformProviderConnectionCredentialStore`、`PlatformCapabilitySafetySuite`，据此使用安全事实端口避免上下文环、绑定精确凭据修订、移除旧最小路径，并将事务断言放入真实基础设施组合测试。
