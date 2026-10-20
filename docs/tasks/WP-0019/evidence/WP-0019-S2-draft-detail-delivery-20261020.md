# S2 部署草稿与精确详情交付证据

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0019 |
| 验证提交 | 98ffdd5b0fa6cc388cef82d8ca8fca0782fd4671 |
| 验证范围 | 建立部署草稿、查看精确部署、完整声明表单；不含目录搜索、真实能力验证、部署发布或生效赋权 |
| 输入提交 | 9d2f08415e03d510b9c484db694f373646897a33 |
| 日期 | 2026-10-20 |
| 决策 | ADR-0070、ADR-0082、ADR-0095、ADR-0100；用户将搜索明确留在 S2 后续子任务 |
| 原型 | OpenDesign Cloud 原项目 249f81f2-6f1a-46ff-988c-da3cdedb35e7，已接受 f3216dffe717e194e96fa20b4afee7983a9ff420 |
| 产物根目录 | /private/tmp/wp0019-s2-draft-20261020-d5j7wns2，0700；截图及详细日志不进仓库 |

## 结果与保存边界

平台管理员已通过正式表单创建一个合成部署草稿，冻结精确连接修订、发现根、能力声明、参数来源、驻留及拟授权租户；精确详情可直达、刷新和返回目录。完整表单支持 CHAT、EMBEDDING、RERANK 及未知候选的显式分类确认。RERANK 在此只完成声明与参数校验，没有真实调用合同，也没有成为租户可用能力。

未知名称保持原始空值，展示“名称暂未提供”，不把编码保存为名称。拟授权集合不写目录或 ACL。新完整声明草稿的能力验证／发布入口返回 `PLATFORM_DEPLOYMENT_CONTRACT_PENDING`，避免沿用旧逻辑将声明当作真实验证结果；其后续工程与执行授权均未闭合。

本子任务关闭实现矩阵的 `platform-model-deployments:0`、`:1` 两项操作。S2 为 2／4，WP-0019 为 7／29；这些计数不表示整个页面所有功能、S2、工作包或 M6 完成。部署目录搜索按用户明确决定留待 S2 后续子任务，不使用当前页过滤或假搜索替代。

## 实际运行版本

`runtime-closure.json` 逐项检查容器与镜像 OCI revision 相等，七服务均 healthy；`deploy-smoke-spacing-final.log` 的四入口通过。控制面提交不能替代下面分别记录的运行版本。

| 服务 | OCI revision | 实际容器镜像 |
| --- | --- | --- |
| console-web | 98ffdd5b0fa6cc388cef82d8ca8fca0782fd4671 | sha256:ed800fb0a9225d550d561e05b3473b4710b0c013cde0df9a01f02b84e172f0a7 |
| control-plane | e28aa34572b1894908ae538f7dc74071a00fedf9 | sha256:9aa1125f21b3bef864ef8ff9adb8d5b690ed2ee68846e8ff81429da6bb7693e4 |
| query-runtime | e28aa34572b1894908ae538f7dc74071a00fedf9 | sha256:9d0f531bf420a05b72715a4c869e25e584ebb94868b4f66fc3a6164af1f74830 |
| ingestion-worker | e28aa34572b1894908ae538f7dc74071a00fedf9 | sha256:251c4bc395e3dfb20dc1fa7203ad1d4c1afb4fece04751440ce2f3b1f8a6e463 |

后端目录从运行提交至本验证提交没有差异。运行入口为 [部署目录](http://127.0.0.1:13000/platform/model-supply/deployments) 和 [本轮精确草稿](http://127.0.0.1:13000/platform/model-supply/deployments/2112408560880058369)。既有受保护 `pwd.txt` 经正式登录使用，未复制或输出凭据；它和 `AccessKey.txt` 均排除 Docker 构建上下文。

## 正式操作与独立事实

只创建了一个部署草稿 `2112408560880058369`，名称为 `S2 合成部署草稿 20261020-d5j7wns2`。来源连接 `2112058351855599618` 修订 2、发现根 `2112089882397507585`，拟授权仅包含现有活动合成租户 `2108209473691582466`；未新建租户、改密码、删除数据、推理、发布或赋权。

| 执行 | 原样保留的结果 |
| --- | --- |
| 首次正式创建尝试 | `production-create/report.json`：真实未筛选目录为零，取消清理通过；同名租户导致验收脚本定位不唯一，POST 未发出。失败报告不改成通过，空目录截图保留于原临时目录。 |
| 修正验收定位后 | `production-create-retry/report.json`：HTTP 201、全部保存字段及精确 GET 读回通过，产生上述唯一草稿；随后返回目录触发旧详情投影错误，整个运行仍保留 `completed:false`。 |
| 修复返回状态后 | `production-final-read-only/report.json`：前端 15df69e55a288a5128d126fb96a02a1d50392cab，只读恢复同一草稿，6 项通过；没有重建对象。 |
| 最终间距修复部署后 | `production-spacing-final/report.json`：前端 98ffdd5b0fa6cc388cef82d8ca8fca0782fd4671、后端 e28aa34572b1894908ae538f7dc74071a00fedf9，6 项检查、18 次观察全部通过，`completed:true`、业务 `writes:0`。 |

最终六项为：当前平台会话、目录与独立安全事实、无默认值及取消清空、精确详情刷新返回、三尺寸原生文字和布局、完整表单固定操作区与键盘。390／1024／1440、正常／200% 原生文字均无横向溢出或被裁切控件；reduced-motion 有效，79 项现有租户的表单可用键盘遍历到保存按钮，但只读模式不提交。最终读取不再要求发现根仍可用于新建，不以只读成功延长来源有效期。

独立 PostgreSQL 检查由 `post-create-facts.sql` 的 `REPEATABLE READ READ ONLY` 完成，初次结果及最终 `closure-database-facts.json` 分别保留。最终观察时间为 2026-10-20T05:16:49.583363Z：草稿 DRAFT／版本 0、CHAT／128／64、维度及重排上限为空；参数来源、连接驻留、唯一拟授权、确认主体与发现根一致。原始候选缺名仍为 2，没有补写源数据。总部署 1、草稿 1、精确分类确认 1；模型目录、配置授权、平台访问授权和能力验证均为 0；迁移 `202610200001` 成功。只读取计数和上述状态，不查询秘密字段。

## 已接受原型的一致性

原项目 [平台模型部署](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-deployments&state=success&review=1) → 建立部署草稿；[平台部署发布](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-deployment-detail&state=success&review=1)。端口变化时按同一项目与入口恢复，不创建可编辑副本。

`prototype-source-binding-final.json` 核对九份入口依赖资源：原项目实际 HTTP 内容与接受提交 `f3216dffe717e194e96fa20b4afee7983a9ff420` 的 Git 对象逐字节一致。原型源码没有修改。原项目观察为 12 次目录／表单、6 次草稿详情及两尺寸样式观察；最后按刷新后的相同阶段定向对照，避免将保存成功反馈与刷新后页面混为差异。临时浏览器内的原型合成操作不产生生产厂商事实。

生产完整表单与原型的标题／正文／原生输入、颜色、宽度、间距、fieldset、固定头尾与独立滚动已对照。桌面 surface 512px、手机 352px；正常原生输入 16px，200% 后 32px；桌面／手机控件基线分别 36／44px。最后发现的页签前多余 24px 先实际失败，再局部修正，页头→页签为 0、页签→说明为 48px、说明→详情为 24px。键盘焦点轮廓保留，真实名称、驻留和拟授权使用正式数据，未复制模拟身份或缺省值。

独立隔离 HTTP 浏览器验证为两页七态 × 三尺寸 × 两档文字共 84 次，以及三类型／未知候选完整表单 24 次；见 `source-browser-parity-final.log`、`/private/tmp/wp0019-s2-source-cXeuBg/observations.json`。它们证明真实组件的反例与状态行为，不冒充生产七种后端故障、厂商合同或正式发布。生产没有通过删除数据制造空态，也没有为了验收故意暂停服务。

## 源码验证与评审

- 完整后端：`backend-source-recheck.log`、`backend-test-summary.json`，352 类、1200 项，失败／错误／跳过均为 0。首次运行的四个旧配额迁移测试出现 JDBC EOF，原日志保留；同源码定向复验后完整重跑通过，未降低断言。
- 最终前端：`frontend-spacing-final.log`，55 文件、336 项及类型、lint、构建通过；`transition-final-checks.log` 的工作包脚本、架构守卫和项目校验通过。最终文档门禁另以本报告后续提交和云端 CI 绑定。
- 控制面：`closure-governance-final.log`，`make project-status project-verify architecture-guard resume-context` 退出 0；81 项治理测试、30 个任务、650 项历史资料、50 项架构测试及双端守卫通过。原证据元数据的责任任务带子任务说明，被严格绑定规则拒绝；改为精确 `WP-0019` 后通过，没有修改校验器。
- 红绿证据包括 RERANK 错分支、缺参数、未知确认、真实 HTTP 字符串冒充布尔、缺失原始名称、表单样式、同名租户验收定位、目录／详情往返、保存后焦点与 24px 间距。真实 PostgreSQL 九项覆盖短事务复核、幂等、分类确认和回滚，不只用 mock 证明原子性。
- 只读拥有者来自组织上下文公开事实，创建短事务内才解析必需实现；无新增 POM 上下文边或依赖环，查询启动不依赖组织写装配。内联 SQL 例外减少一处，没有提高包数量阈值；PG 数组、聚合和锁例外逐项说明。
- 固定评审范围为输入提交到验证提交，完整 diff 保留为 `reviewed-source.diff`。依据安全边界追踪声明、确认、幂等、审计、权限及迟到响应，已修复上述实际复现问题；本次交付范围没有已确认未修复阻断。真实能力验证／发布仍未实现，不能据旧接口存在声明可用。

图谱探索和影响分析覆盖 `PlatformModelDeploymentService`（30）、`PlatformModelDeployment`（75）、`ModelType`（135）、仓储（47）及页面 Workspace（4）等；据此没有新增组织→模型或模型→组织的反向依赖，没有修改查询 Nginx。目录／详情隔离以路由和会话修订重新装配页面，防止不同读取投影共用旧状态。

当前插件环境为 Local Codex，能力来自当前安装的个人版工程助手；显式加载 `engineering-assistant:wayfinder`、`tdd`、`codebase-design`、`code-review` 及安全 seam／证据审计规则。内联执行，没有创建 Runtime 或 StageRunResult，阶段结果校验不适用；未复用旧业务 Runtime 宣称本片通过。

固定 diff 评审记录为 `fixed-diff-review.txt`。`delivery-evidence-digests.json` 记录本轮 257 个文件、21,379,522 字节，SHA-256 为 `8446e716db662af40a60c54a9849028c1c9d0a9651b6cf99b1e98d7226e3c70a`；包含原失败、最终部署与浏览器、只读事实、固定原型及截图摘要，不含自身和之后的控制面提交／最终 CI 回执。可重建浏览器缓存已回收；日志、截图和验收证据保留，文件 0600、产物根目录 0700。

## 剩余工作及交付门禁

本轮不再要求提供密码或重复接受原型。草稿／详情子任务没有凭据、方舟零候选或 CI 账单的已确认阻断；源码提交 `98ffdd5b0fa6cc388cef82d8ca8fca0782fd4671` 的 [GitHub Actions](https://github.com/liming-cloud/ai-platform/actions/runs/39374085111)已实际 success，九步骤全部通过，回执为 `source-ci.json`。最终控制面提交的 CI 在推送后独立核对并保留回执，不沿用 S1 或源码 CI 代替最终结果。

S2 后续仍有：目录搜索的专属合同；三类独立受控验证协议、预算／合成安全样本和真实厂商证据；原子发布与实际租户授权。现有同名租户在人工发布审阅前还需确认可辨识性，当前原型只显示名称，本轮不擅改设计。真实推理、精确发布和生效赋权须待具体材料可审阅后另行授权；这不只是等待授权，相关工程也尚未完成。S3～S6、WP-0019 整包及 M6 继续未完成，STATE 保持 WP-0019／M6。
