# WP-0017：原子开户切片部署后原型一致性执行证据

## 结论与边界

2026-09-19，在 macOS 本机真实 Compose 上完成原子开户独立切片的生产部署验收。最终前端候选为 `670d13e58432a7f0b4dc4bd54ae75921bfd48277`：11 个开户场景、15 个新管理员登录场景、15 项只读数据库事实、固定原型的三尺寸视觉对照全部通过。这里只确认本切片的任务结构、设计基线、真实状态和可用性；不声称逐像素复制整个原型外壳。

WP-0017 仍进行中，M6 未完成，66 页未全部生产实现。管理员独立管理、配额编辑、功能开关、登录策略，以及普通会员、模型、知识接入和查询均不属于本次完成范围。概览没有五页设置导航，矩阵 `platform-tenant-detail:0` 继续待冻结/实施，不能随登录入口一起提升为完成。

本证据新增登记，不改写 `WP-0017-prototype-parity-audit-20260915.md` 的历史未通过结论。

## 固定版本与部署事实

| 对象 | 实际事实 |
| --- | --- |
| 仓库、分支 | `/Volumes/slm-ssd/projects/ai-project/ai-platform`，`codex/trusted-rag-foundation-v1` |
| 会话初始前端 | `7fb46f86703463621982d9ea1c2e77a55dfeb7da`；镜像 `sha256:f3abf9d16db0fcebb43e82ce5b7b2854d35f2ac239106faed7e0ac3d94493c8b` |
| 结构修复 | `cbf006998d9105bf2ea7cd8192687e6436fd4c9a` |
| 最终前端 OCI revision | `670d13e58432a7f0b4dc4bd54ae75921bfd48277` |
| 最终前端运行镜像 | `sha256:893363ad7e5035136e41acb8c95d34ec3d780550c4a206261f31732acbf7aaeb` |
| 后端实际 OCI revision | `5844fd3ba2b5909161b704df5fc7e1ff026d4a0a`，本轮未重建或替换 |
| 后端实际运行镜像 | `sha256:287c22b27fc900992fb3bc18242e1f517f140d60109f2e71e679a9afdcbc4108` |
| OpenDesign 项目 | `249f81f2-6f1a-46ff-988c-da3cdedb35e7` |
| 已验收原型提交 | `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f` |
| 服务与冒烟 | console-web、control-plane、ingestion-worker、query-runtime、postgres、redis、minio 全部 running/healthy；`make deploy-smoke` 四入口通过 |

构建使用 `deploy/images/frontend.Dockerfile`、`APP=console-web` 和最终 revision label。更新命令为 `docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yml up --detach --no-deps --no-build console-web`。未重启 Docker，未清缓存、重置账号、删除卷或旧验收对象。

根目录协议所需的 Charter、STATE、当前 WP、全部 12 个引用 ADR 已读取；`make project-status` 通过。初始 `lastVerifiedCommit=f9ba767bf2b20716b2c04e812b45e616fe037f53` 是初始 HEAD 的祖先。用户五项 fixture 的 R100 暂存重命名前后逐字比较一致，未纳入本次提交。

## 凭据与受控执行

`0600` 表示只有文件属主可读写；父目录使用 `0700`。验收还拒绝符号链接、非普通文件、错误属主及过大文件。旧会话指定的临时凭据和观察脚本已不存在，本轮没有打印其内容或覆盖旧文件。

当前后端运行 `local` profile，依据 ADR-0071 的本机初始化边界，从既有本地配置读取平台初始化凭据并写入新独占文件；不需要用户人工传递秘密，也没有重置已有账号。该恢复方式只适用于当前本机环境，不能作为共享或生产环境凭据回退。开户脚本自行生成新管理员凭据，秘密仅在临时独占文件和浏览器内存中使用。

本轮所有独立 Chrome、构建及 Docker 操作均通过正常受控审批。历史 `Selected model is at capacity` 是工具审批未启动命令，非产品失败；本次未重现该容量阻塞，没有因此尚未执行的本片验收项。

临时根目录：`/private/tmp/wp0017-final-20260919.n5jrxtt_`；最终轮目录：其下 `670d13e58`。凭据文件不进入版本库、不进入日志摘要清单。截图仅保留临时目录，密码控件和演示帮助均遮罩。

## 从失败到最终候选

1. `7fb46…` 的 11/15/15 业务检查通过，但目视和新增真实 DOM 结构断言证明：第三步未采用左表单、右摘要与确认布局，概览页头和登录入口归属不一致。`structure-red.log` 两项失败；`cbf006998…` 调整本片结构后，同一断言转绿。未改变后端、公共 API、数据模型或原型。
2. `cbf006998…` 再次通过业务验证；补充真实 Tab 检查发现 390px、200% 文字下 3 次焦点中心落在底部导航上。`keyboard-red.log` 的正式新增断言失败。最终 `670d13e58…` 仅为开户 input/button 增加 `scroll-margin-block: 6rem`；正常与 200% 文字的正反向 Tab 可见性检查已进入正式 11 场景，最终全部通过，独立复现的遮挡数由 3 变为 0。
3. 配额页曾出现截图中的提示文字挤压疑点；独立真实 DOM 测量显示间距为 24px，三尺寸/两倍率均通过。等待两帧布局再采样后疑点消失，没有据此修改产品或降低阈值。
4. 一次并发补充浏览器检查期间，概览观察落到了登录页；该组截图无效，保留但排除。最终视觉观察严格串行，并对每张生产截图验证页头、路由和唯一 h1；24 张生产截图全部落在预期页面。
5. Node 测试首次在 `umask 077` 下把预期 0640 的拒绝用例文件实际创建成 0600，导致 1/28 失败。空文件权限复现确认是执行环境；正常 `umask 022` 下原断言 28/28 通过，未改用例、未放宽真实凭据的 0600 要求。

## 最终执行结果

| 验证 | 结果与范围 |
| --- | --- |
| `make frontend-test` | 42 文件、216 项；typecheck、lint、build 全通过 |
| `node --test scripts/quality/work-package-login.test.cjs scripts/quality/work-package-onboarding.test.cjs` | 28 项通过 |
| `make architecture-guard` | 28 项通过；现存未触及债务为 15 个内联 SQL 文件、3 个手工分页，不声称全仓清零 |
| `./mvnw -q -pl backend/contexts/organization-access -am test` | 本轮 239 项通过；最终 UI 修复未改变被测后端源码 |
| `AuthenticationApiTest` 定向 Maven 执行 | 本轮 23 项真实 HTTP 用例通过；无失败/错误/跳过 |
| `make acceptance-work-package WP=WP-0017 SLICE=onboarding` | 最终部署 11 场景通过，包含三尺寸、正常/200% 字号、正反向 Tab、Space、Enter、reduced-motion、刷新/回退/取消和不提前写入 |
| `make acceptance-work-package WP=WP-0017 SLICE=login` | 最终新管理员 15 场景通过，平台/租户独立入口、错误角色、刷新、退出、无平台权限 |
| 只读事实 SQL | 最终精确对象 15 项全部符合，7 项为 1、8 项为 0 |
| 原型只读对照 | 4 个画面 × 3 尺寸 × 2 倍率 × 生产/原型，共 48 张；生产 24 张落点正确，横向溢出 0 |
| 补充可见性 | 390px/200% 文字的真实 Tab 遮挡数 0；安全清单逐项放大到 32px，在 390/1024/1440 均无裁切 |
| `make project-verify` | 最终源码候选 35 项通过；控制面收口后另行重跑 |

开户反例包括重复编码、重复登录名、旧请求、缺少 CSRF、密码不一致、重复编码错误返回第一步并聚焦字段、取消清除失败草稿。成功路径只产生一次开户 POST，刷新精确读取非默认配额；登录入口不暗中切换当前平台身份。

最终合成对象：tenantId `2101311718205423618`，subjectId `2101311718205423619`，tenantCode `wp17-5334f4fb8859`；冲突后缀 `5334f4fb8859`。本轮较早两次真实验收对象 `2101305984935788545/2101305984935788546` 与 `2101309318291783681/2101309318291783682` 继续保留，不覆盖、不删除。

## 原型对照与生产例外

从现有 OpenDesign 工具取得完整 17 文件；预览 HTTP 字节与 `docs/prototypes/wp-0016` 的渲染文件一致，且这些渲染文件相对已验收提交无变化。三个固定源摘要：

- `tokens.css`：`ad0cd644358eb724f3aa2261b2d86fe357bf10e7603bc3d21295132177d55385`
- `prototype.css`：`2e627c22e4a9ce1d37ce90ce95a92673b1bc13d3ddbcbf4f51f5eb33d215efa4`
- `full-platform.js`：`d650c1ae6c000263fd97be0d650f2c375ece3de8fb00998cb758955060a5adbe`

最终目视覆盖桌面前两步、第三步及概览，390/200% 的第三步与概览，1024/200% 的第三步与概览，并检查安全清单放大截图。正常正文 16px，标题 Georgia/中文宋体回退；暖色产品面板 `rgb(250, 249, 245)`、正文 `rgb(20, 20, 19)`。侧栏标识为 R、产品名“可信 RAG”、导航“租户管理”；1440 侧栏 240px（15rem），1024 为 80px 收窄模式。

正常字号短按钮取消宽 54px、上一步/下一步宽 68px，计算 min-width 为 0；桌面高度 36px，390px 高度 44px，放大文字时随内容增高。第三步桌面左右分区、移动顺序堆叠，概览唯一 h1 为“租户概览”，基本信息卡包含真实登录入口，安全保障分区真实存在。同行对齐 1px、字号与裁切原断言未放宽。

依据 ADR-0079/0080 和旧审计中的生产边界，保留真实错误、必填标识、精确容量换算、会话退出说明及只读实际配额；不复制演示密码、虚构区域/管理员资料、模拟成功状态或未实现设置按钮。原型完整全局导航、移动导航位置、页头辅助文案及少量间距不是本片逐像素复制承诺，不能据此提升其他操作或页面状态。移动 full-page 截图中固定导航出现在初始视口位置；实际交互遮挡以真实 Tab 和命中检查判定，不能单凭拼接长图判定焦点可用。

复现视觉时须串行登录生产与原型各自的独立浏览器上下文；原型使用现有 `review=0` 隐藏审查器。只填写无秘密草稿、前进三步后取消，再读取精确新租户概览，不发送新增业务请求。每次倍率还原和路由切换后等待布局帧，核验目标页头/路由后截图。最终临时脚本和截图位于 `670d13e58/visual-final/`；临时材料失效后按此流程和仓库正式验收脚本重建，不从旧截图推定当前版本通过。

## 只读事实复现

将以下 SQL 保存到新的临时文件，通过现有 postgres 容器 `psql -X -At -v ON_ERROR_STOP=1` 执行。连接账号仅从容器已有环境读取；查询只输出计数和状态判断，不选择秘密字段。注意 `tenant` 的 DISABLED 是持久化锚点事实，实际可用性由 ACTIVE 生命周期与 ENABLED 成员事实共同决定。

```sql
-- WP-0017：只复核本轮精确合成对象及冲突对象；不读取任何秘密字段，不产生写入。
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '5s';
WITH failed_subjects AS (SELECT subject_id FROM organization_access.subject_identity WHERE login_name IN ('unused-5334f4fb8859','old-5334f4fb8859')),
failed_tenants AS (SELECT tenant_id FROM organization_access.tenant WHERE tenant_code IN ('unused-5334f4fb8859','old-5334f4fb8859') UNION SELECT tenant_id FROM organization_access.tenant_lifecycle WHERE tenant_code IN ('unused-5334f4fb8859','old-5334f4fb8859')),
facts(label,actual,expected) AS (VALUES
('subject_active',(SELECT count(*) FROM organization_access.subject_identity WHERE subject_id=2101311718205423619 AND login_name='wp17-5334f4fb8859' AND status='ACTIVE'),1),
('anchor_disabled',(SELECT count(*) FROM organization_access.tenant WHERE tenant_id=2101311718205423618 AND tenant_code='wp17-5334f4fb8859' AND status='DISABLED'),1),
('lifecycle_active_v1',(SELECT count(*) FROM organization_access.tenant_lifecycle WHERE tenant_id=2101311718205423618 AND initial_administrator_subject_id=2101311718205423619 AND status='ACTIVE' AND version=1),1),
('administrator_enabled',(SELECT count(*) FROM organization_access.tenant_membership WHERE tenant_id=2101311718205423618 AND subject_id=2101311718205423619 AND status='ENABLED' AND membership_role='TENANT_ADMINISTRATOR'),1),
('administrator_role',(SELECT count(*) FROM organization_access.tenant_membership_role_binding r JOIN organization_access.tenant_membership m USING(tenant_id,membership_id) WHERE r.tenant_id=2101311718205423618 AND m.subject_id=2101311718205423619 AND r.role_code='TENANT_ADMINISTRATOR'),1),
('quota_exact_v0',(SELECT count(*) FROM organization_access.tenant_quota WHERE tenant_id=2101311718205423618 AND knowledge_bases=4 AND documents_per_knowledge_base=1234 AND bytes_per_knowledge_base=12500000000 AND max_file_bytes=75000000 AND max_text_bytes=2097152 AND version=0),1),
('lifecycle_event_active_v1',(SELECT count(*) FROM organization_access.tenant_lifecycle_outbox WHERE tenant_id=2101311718205423618 AND event_type='TENANT_LIFECYCLE_CHANGED' AND lifecycle_status='ACTIVE' AND lifecycle_version=1),1),
('no_platform_role',(SELECT count(*) FROM organization_access.platform_role_binding WHERE subject_id=2101311718205423619),0),
('no_failed_subject',(SELECT count(*) FROM failed_subjects),0),
('no_failed_anchor',(SELECT count(*) FROM organization_access.tenant WHERE tenant_code IN ('unused-5334f4fb8859','old-5334f4fb8859')),0),
('no_failed_lifecycle',(SELECT count(*) FROM organization_access.tenant_lifecycle WHERE tenant_code IN ('unused-5334f4fb8859','old-5334f4fb8859')),0),
('no_failed_membership',(SELECT count(*) FROM organization_access.tenant_membership WHERE tenant_id IN (SELECT tenant_id FROM failed_tenants) OR subject_id IN (SELECT subject_id FROM failed_subjects)),0),
('no_failed_role',(SELECT count(*) FROM organization_access.tenant_membership_role_binding WHERE tenant_id IN (SELECT tenant_id FROM failed_tenants)),0),
('no_failed_quota',(SELECT count(*) FROM organization_access.tenant_quota WHERE tenant_id IN (SELECT tenant_id FROM failed_tenants)),0),
('no_failed_event',(SELECT count(*) FROM organization_access.tenant_lifecycle_outbox WHERE tenant_id IN (SELECT tenant_id FROM failed_tenants)),0))
SELECT label,actual,expected,actual=expected AS passed FROM facts;
COMMIT;
```

## 六维评审与工程助手

评审固定范围为 `2966981dc1c8ea2bbcc3b83afbfe7a3aae525ab3..670d13e58432a7f0b4dc4bd54ae75921bfd48277` 中本片主题、外壳、开户及验收修改；本轮修复另以 `7fb46f8..670d13e58` 核对。代码图谱探索过 `PostgresTenantProvisioningStore`、`TenantProvisioningFlow`、`TenantOverview`、`ConsoleApp`。`TenantProvisioningFlow` 的 impact 只返回文件内符号，未把局部索引结果表述为完整全仓调用图；同名概览误匹配及 CJS 索引不足以精确磁盘读取补充。

| 维度 | 结论及可复核依据 |
| --- | --- |
| 正确性 | 通过；最终真实 11/15/15、结构红绿、焦点红绿及有效视觉落点证据 |
| 复杂度 | 通过；共享主题仍用现有 Provider/令牌，本轮为局部 grid/语义标题/滚动留白，无新服务或状态机 |
| 冗余 | 通过；不复制原型模拟状态、账号库或另一套公共控件；布局断言复用正式场景 |
| 性能 | 通过本片变更审查；最终修复仅 CSS 和既有 DOM 排布，无新增请求、轮询或重试；本机导航观测为毫秒级，非负载测试或生产 SLA |
| 架构 | 通过；未改公共接口、权限/租户边界及后端四层结构，架构门禁通过；未触及历史债务不冒充已消除 |
| 测试质量 | 通过；真实失败后补可见性与结构检查，保留原阈值；排除错误落点截图，明确环境失败与产品失败 |

插件环境为 Codex 本地安装的 `engineering-assistant` skills 和 MCP Runtime，未用 Hermes 替代。显式加载 `engineering-assistant:wayfinder`、`engineering-assistant:tdd`、`engineering-assistant:code-review`；使用 wayfinder 的 HITL、tdd 的 security-seams、implement flow 当前 quality 执行指导及 EVID-001/002/003。Runtime `run-5b4368307c3d4be7` 的质量结果单独保存为 `WP-0017-parity-quality-20260919.json`，以实际 schema 校验和节点完成回执为准。

## 非秘密执行日志指纹

下列 SHA-256 仅标识日志/SQL/观察报告，不包含或计算凭据文件摘要。路径均相对于本轮临时根目录。

| 文件 | SHA-256 |
| --- | --- |
| `structure-red.log` | `ba28af248616afa5f0266194947ace9b0e7d2486ce627e4bc38611b6ff2e7d5e` |
| `structure-green.log` | `6365e5b565c401468d20d51972b8ae8381b82b3ada01b2708339121873330321` |
| `keyboard-red.log` | `d1987ba2e5272142cc87343d6c14e4c39e1a775f362b40465ae1d7246729c575` |
| `keyboard-frontend-test.log` | `b820141b4b488a768a3b2f65e8a2c425c062e04bccf533d4cd9c4f732109190a` |
| `keyboard-script-tests.log` | `8d160061fa0b617bdd49eafa66f4e689d0fba76044ca885920873dee2d44b22c` |
| `keyboard-architecture.log` | `ddfc4df835164d71d55bcbf3e4c42b32977bc29fa8cceb183fdc7d4d09519cf4` |
| `backend-test.log` | `837ca2193e4f25cf64a424500611f88579f19c36ff7ae9d8b2fe64be383e4adc` |
| `authentication-api-test.log` | `07db5565d27f0df4bf56662cbfd275188ad5d7292731c5d439bbe8421bdebce7` |
| `670d13e58/runtime-images.log` | `78d3a9eddb269e530d63a69e47544a3caf48f267a19561d2a2d64bc8b804e37e` |
| `670d13e58/compose-health.json` | `534419a1d9423fd8ebae64010df11d0b0930c70b5ba6c3f56a81089da24827ad` |
| `670d13e58/onboarding.log` | `6bb98bba858b6243fc78379a7f2c3ab5e139bc3e722e236bfd197e1088b38329` |
| `670d13e58/login.log` | `88ca7be8b41de299c6299bdbc5f201f1255472c6ea36303f552ddf9b878aae96` |
| `670d13e58/deploy-facts.sql` | `e1620af28b097aadad084619c7c49d680ebda1c7be6fef362aa6e3842ea9a357` |
| `670d13e58/deploy-facts.log` | `44efc6b92b5d0d2aecded57784656dac35514564d699f7c7301b9fdfe3b540bb` |
| `670d13e58/visibility-check.log` | `1f6fab5488506aa50f5670f12025b5b22966864ffd376d247ceae6fa35f1a8b9` |
| `670d13e58/visual-final/visual-observations.json` | `6949afd3f03fe8bb8178b2a4cf0ba90da3a4f16a6f32b37bd8dcf7cbe02e0d61` |
