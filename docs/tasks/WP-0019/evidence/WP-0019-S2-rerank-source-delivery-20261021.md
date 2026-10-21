# S2 官方重排候选来源交付

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0019／S2 |
| 输入提交 | 0cb9b1b9e19d9ef09931aa9cba70b4426b23e6a2 |
| 验证源码提交 | 43681bbbd52a48f77cacd6834523aeee92844d8c |
| 验证范围 | ADR-0101 的新来源、受控映射及追溯；隔离 PostgreSQL／HTTP、当前 Compose 和只读配置传递 |
| 日期 | 2026-10-21 |
| 状态 | 来源源码与部署子任务已闭合；真实硅基流动调用未执行 |
| 产品进度 | S2 2／4，WP-0019 7／29；整片、整包和 M6 未完成 |

## 交付与边界

依据已接受 ADR-0101，新预设版本为 `2026-10-21`；只有硅基流动 RERANK 使用 `SILICONFLOW_RERANK_MODELS_API`，运行协议仍为 `VENDOR_POST_PROCESSOR`。百炼、自定义的 NONE 不变，既有连接不升级、不补候选。固定读取 [官方模型列表](https://docs.siliconflow.cn/docs/api/models-get)的 `GET https://api.siliconflow.cn/v1/models?sub_type=reranker`，使用平台推理凭据，不使用方舟 AK/SK、不从运行地址拼接发现地址。

仅实际账户列表中存在的 `BAAI/bge-reranker-v2-m3` 通过版本化映射确认 RERANK；其他返回项保留待管理员确认，名称缺失保持缺失。合法空列表可以成功，但不会从静态映射补出模型。候选的来源协议、固定列表、映射版本及规范正文摘要与发现根、连接修订和有效期共同保存；来源在扩展公开契约与模型领域之间显式映射。草稿选择恢复同一发现根的来源，旧映射版本／摘要在 SQL 分页及计数前过滤，不删除历史事实。新来源字段成组约束，旧候选保持 NULL。

出站检查先于平台密钥解析；重定向、连接恢复重试及二次实际发送关闭，总调用期限覆盖响应头后的正文读取，正文限制 1 MiB。尾随 JSON、重复 ID、畸形列表、超大正文、认证原文／编码回显均失败，不输出厂商原始响应。此处只证明模型发现，不证明 RERANK 推理能力。

## 反例与验证

| 验证 | 结果与复核资料 |
| --- | --- |
| 预设／空列表／迁移 | 实际红灯：SF 仍为 NONE、空列表被拒、缺来源列；修复后通过。`red-rerank-preset.log`、`red-rerank-facts.log` |
| 响应和重试 | 尾随 JSON 被接受、503 Retry-After: 0 导致 6 次显式动作实际发 7 次、认证回显成为候选，均先复现再拒绝。`red-rerank-strict-response.log`、`red-rerank-http-retry.log`、`red-rerank-credential-echo.log` |
| 实际 PostgreSQL | 来源往返、当前映射失效、同范围总数、过期／停用、成组约束及既有 CAS／回滚测试通过；另修复 RERANK 中文分类未进入候选搜索。`red-rerank-mapping-expiry.log`、`red-rerank-search-classification.log`、最终后端日志 |
| 完整后端 | `./mvnw -q test`：357 类、1,251 项，失败／错误／跳过 0。`backend-final-all.log`、`backend-final-counts.json` |
| 前端 | `make frontend-test`：55 文件、336 项与构建通过；仅扩展类型联合，没有改动页面布局。`source-frontend.log` |
| 治理与架构 | 81 项治理、30 任务、650 项历史对象；50 项架构测试及双端守卫通过，依赖边界违规 0，未放宽守卫。`source-project-verify.log`、`architecture-final.log`；控制面修改后再运行收口命令 |
| 云端 CI | 源码提交 [Actions 39537065506](https://github.com/liming-cloud/ai-platform/actions/runs/39537065506)实际九步骤 success；不是 Maven、浏览器或厂商证据的替代。`ci-source.json` |

中间全量失败由测试仍使用旧预设版本触发；改为当前目录版本后保留原安全断言。一次新增测试的 PageQuery 准备编译错误单独保留，不作为产品红灯。最终源码包含全部修复，没有删除反例或降低阈值。

## 当前部署与只读浏览器

通过真实 Compose 构建并更新四个应用镜像，保留原受保护运行配置；PostgreSQL／Redis／MinIO 没有重启、清卷或重置。七服务 healthy，`make deploy-smoke` 四入口通过。四个镜像及容器的 OCI revision 均实际为前述验证源码提交：

| 组件 | 运行镜像 SHA-256 |
| --- | --- |
| console-web | aeadb7d92605a59e99664aa420fb463097cfb1dc02b1a1a004ff0c33fc61addb |
| control-plane | 5b045c2c3fa8fcf0947a2c8590171ea66e8c185a35fec6b47a29950eb6cdeb03 |
| query-runtime | 8cda6ff7f01986d5a4499ea382e5d27687f651cf913caea47e3abd90b56ce91e |
| ingestion-worker | 7317d7902f4e922383251bd2e45347ee0c271e88a7e898b3a665e537bc6f6fb3 |

初次核对发现旧临时 Compose 覆盖文件将容器 revision 固定为旧值，实际新镜像已经运行。保留失败事实后，在本轮覆盖文件同时绑定镜像与容器标签，再更新并重新核对；没有将旧镜像改标签冒充新源码。见 `deployment-label-before-correction.json`、`deploy-label-correction.log`、`runtime-images-after.json`。

复用普通 0600、Git 忽略的 `pwd.txt`，正式平台登录 HTTP 201。最新镜像上三项浏览器检查通过：平台会话、新预设的精确发现配置、SF／RERANK 正式表单只读选择与取消。浏览器阻断业务写入，未填厂商 Key、未提交连接、未调用厂商；`source-browser.json`。标签修正前后实际镜像摘要相同，保留原浏览器时间，不重复通过项；标签修正后单独复核七服务与四入口。

OpenDesign Cloud 唯一原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，S2 接受版本仍为 `f3216dffe717e194e96fa20b4afee7983a9ff420`；[平台部署入口](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-deployments&state=success&review=1)。受控目录对接受版本差异为零，服务三份关键资源逐字节一致。人工查看本轮单张只读表单截图，没有本次新增的可视差异；未重跑已通过的 S1／S2 页面矩阵，不把这三项观察称为整个 S2 全景验收。截图仅在 `/private/tmp`，未进仓库。

## 数据保持及真实剩余工作

部署前后分别执行 REPEATABLE READ READ ONLY：连接 3、发现 13、候选 787；唯一草稿 `2112408560880058369` 保持 DRAFT／版本 0；能力验证、目录、配置和配置授权各为 0。计数及草稿状态完全一致，见 `deploy-facts.sql`、`db-before.json`、`db-after.json`。该草稿仍声明 CHAT 而来源为多模态 embedding，不用于真实 Chat 验证，不改写其声明或历史。

正式接口与只读数据库确认三条连接均为方舟，没有硅基流动连接。登录凭据有效，当前没有登录 401 或 CI 账单阻断；Ark 和 SF 域名本轮 DNS 均为公网，未改代理配置。SF Key 的受保护来源待用户提供路径或通过正式连接页配置，不能从不存在的配置查询，也不能用 AccessKey.txt 替代。

完整生产三样本／usage、账户价格与计量证明、预算提交及取消／迟到／审计事务、原子目录发布与生效授权仍未交付。正确 Chat 草稿、两类 SF 来源、真实调用和精确部署发布／赋权须依原接受合同准备并分别授权；新草稿旧探测／发布保护没有解除。部署目录搜索的有界字面查询合同仍待明确接受。本轮没有新增真实业务对象，不进入 S3；本报告不将来源子任务通过外推为 S2、WP-0019 或 M6 完成。

## 复核与工具

外部资料目录 `/private/tmp/wp0019-s2-completion-20261021-4eOTRp` 为 0700，文件 0600。`evidence-digests.json` 绑定验证源码，50 项、3,754,007 字节，SHA-256 `7c9d026357073e525162a90b66c66351533a862e1b6c5f4348dec1a0149ec44e`；不含自身及此后控制面收口／推送回执。浏览器复用已修复缓存清理 helper，结束后 `.vite` 不存在，截图、日志和证据保留。

当前 Local Codex 使用个人安装包 `2.2.0-rc.1+codex.local-20260929-051321`，显式加载 `engineering-assistant:wayfinder`、`codebase-design`、`tdd`、`code-review` 及 HITL／安全 seam 规则；内联执行，无 Runtime／StageRunResult，阶段校验不适用。图谱探索发现预设、候选公开契约、映射、持久化及自动配置，对 PlatformModelDiscoveryProfile 的影响分析为 124 个符号；据此增加独立平台发现适配，保持通用 OpenAI 发现、租户链和 Maven 边界不变。固定 23 文件 diff 自审及 Git 对象摘要核对见 `fixed-diff-review.txt`、`reviewed-source-complete.diff`、`source-files.json`，不冒充独立人工评审。
