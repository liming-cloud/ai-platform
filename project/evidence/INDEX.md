# 证据索引

所有证据必须可复现、绑定 Git 提交并由对应工作包引用。

## WP-0001

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- | --- |
| 治理单元测试 | `python3 -m unittest scripts.quality.test_project_governance` | `e1e5ccced4457af85ac84ddea2db9a0663e3173b` | 4 项通过 |
| 仓库控制面验证 | `python3 scripts/project_governance.py verify` | `e1e5ccced4457af85ac84ddea2db9a0663e3173b` | 通过，识别 2 个工作包，当前前沿为 WP-0001 |
| 跨会话恢复输出 | `make resume-context` | `e1e5ccced4457af85ac84ddea2db9a0663e3173b` | 通过，输出 89 行最小恢复上下文 |
| 既有交付契约 | `make delivery-contract` | `e1e5ccced4457af85ac84ddea2db9a0663e3173b` | 5 项测试与 4 个脚本语法检查通过 |
| 流水线配置语法 | Ruby 标准 YAML 解析器读取 `.github/workflows/ci.yml` | `e1e5ccced4457af85ac84ddea2db9a0663e3173b` | 通过 |
| M0 治理回归 | `make validate` | `f1eb43bb4fb74fc2678b9bb38791f8393aeb4548` | 5 项治理测试和控制面验证通过 |
| 旧实现清理 | 检查受控文件和 `project/evidence/WP-0001-cleanup.md` | `f1eb43bb4fb74fc2678b9bb38791f8393aeb4548` | 删除 1438 个旧实现文件，禁止旧路径剩余 0 个 |
| 前端设计完整性 | 检查 `docs/design/foundation-frontend-design.md` 的标题与禁止样式 | `f1eb43bb4fb74fc2678b9bb38791f8393aeb4548` | 27 个二级章节、24 个三级章节、长破折号 0 个 |
| 规则文档中文化 | 检查根 `AGENTS.md` 的自然语言行 | `f1eb43bb4fb74fc2678b9bb38791f8393aeb4548` | 未发现整行英文说明候选 |
| 流水线配置语法 | Ruby 标准 YAML 解析器读取 `.github/workflows/ci.yml` | `f1eb43bb4fb74fc2678b9bb38791f8393aeb4548` | 通过 |
| 可扩展研发目录规划 | `make validate` 与目录章节门禁 | `8b801f405e7651b506c681b5dfcc5c9e162dbbdb` | 6 项治理测试通过，26 个二级章节和 28 个目录树完整 |
| 目录方案评审证据 | `project/evidence/WP-0001-repository-layout.md` | `8b801f405e7651b506c681b5dfcc5c9e162dbbdb` | 完成两种方案比较、依赖矩阵、扩展路径和 M1 最小落地范围 |
| 产品与架构设计评审 | `project/evidence/M0-design-approval.md` | `66930880d2e1f0b872d7f2600b82a8cee2e4fe2e` | 产品负责人已批准 M0 |

最新自动证据执行时间为 2026-07-26，执行环境为当前 macOS 工作区。WP-0001 已通过人工评审并关闭。

## WP-0002

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- | --- |
| 工作包激活 | `project/evidence/M0-design-approval.md` | `f2f285c676c29d680d58c65b46cecd78b3267cc1` | M0 已通过，实施分支已创建 |
| 后端工程基线 | `project/evidence/WP-0002-backend-baseline.md` | `8f2275a0fec8468fef5326b13a9eb18c179edf57` | 固定工具链、三运行入口和确定性供应商契约验证通过 |
| 前端工程基线 | `project/evidence/WP-0002-frontend-baseline.md` | `ddaf319980968611f95d6367845c80d7724023c7` | 双应用、Fluent UI、状态设计和响应式浏览器验收通过 |
| 容器化运行基线 | `project/evidence/WP-0002-docker-baseline.md` | `ccc61569ca1560c7fc9b8cb0081330ab10e4c664` | 八个服务健康，五个应用入口及容器安全属性验证通过 |
| 供应商安全探测 | `project/evidence/WP-0002-provider-security.md` | `e3101ee5cb0a1e4b62b46f668f349cc6b9dc36a3` | 密钥生命周期、出站地址策略和 Spring AI 兼容协议探测通过 |
| 限界上下文边界 | `project/evidence/WP-0002-context-boundaries.md` | `925590273f718f5c72361224c634b86a457b6407` | M1 六个上下文模块及治理门禁验证通过 |
| 工作包最终验收 | `project/evidence/WP-0002-acceptance.md` | `5dad356bda6a0f51a0957705ce1c8ae6ff28820a` | 全量代码门禁和最新容器重建通过，WP-0002 关闭 |

## WP-0003

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- | --- |
| 框架使用基线 | `docs/research/WP-0003-framework-baseline.md` | `8a3a59575` | Sa-Token、MyBatis-Plus、Flyway 与租户隔离边界已固定 |
| 可信请求上下文 | `project/evidence/WP-0003-trusted-context.md` | `47cd20cb995212f3cb0d11781e06a853af2b7866` | 框架无关认证上下文红绿测试和全后端回归通过 |
| Sa-Token 可信会话 | `project/evidence/WP-0003-sa-token-session.md` | `a619aac7e1ec9804a41fddf26358f609405716f1` | 真实 Sa-Token 登录态、上下文恢复、撤销和框架隔离测试通过 |
| 演示租户认证基座 | `project/evidence/WP-0003-authentication-foundation.md` | `4e0c258b2205a7ecb21d606860366bbf28b69810` | PostgreSQL 成员事实、密码校验、Redis 会话、HTTP 登录注销和 Docker 组合部署通过 |
| 知识空间纵向切片 | `project/evidence/WP-0003-knowledge-space.md` | `02e4a64aea30c6acc485c93e6c5371a1cc9c2fa7` | 领域权限、真实事务、幂等、并发配额、游标、受控接口和 Docker 增量部署通过 |
| 文本数据源与暂存边界 | `project/evidence/WP-0003-text-staging.md` | `d66a78e761c461db2c57434ed6607c569b462e5a` | 数据源状态机、流式大小限制、严格 UTF-8、摘要和临时文件清理通过 |
| 文本数据源数据库事务 | `project/evidence/WP-0003-text-data-source-transaction.md` | `8821c0b92758878eec954969ad5e5d857786d07a` | 历史幂等重放、并发配额、租户隔离、乐观锁、回滚和事务事件通过 |
| 文本数据源受控接口 | `project/evidence/WP-0003-text-data-source-api.md` | `b7474dd72a5218f8b2160066ebe7607c38cc9546` | 应用权限、创建、游标列表、发布、错误结构和租户隔离接口测试通过 |
| 文本内容上传编排 | `project/evidence/WP-0003-text-upload-orchestration.md` | `c79ec9c4d7d214aace7781852bb004e6e2e010d3` | 流式上传、历史幂等、对象元数据核验、失败关闭和发布原子性通过 |
| 知识数据发布控制台 | `project/evidence/WP-0003-console-publication-flow.md` | `288dbc0a43b2abf2047b2b12736b2d5e119eb935` | 单主任务、浏览器状态、稳定幂等重试、生产代理和渠道隔离通过 |
| 门店与校园合成检索数据 | `project/evidence/WP-0003-demo-fixtures.md` | `8078142163c64841fc59e548cc50df453676bb96` | 2 个隔离场景、8 个检索用例、内容摘要和答案要点标准校验通过 |
| 真实对象存储与知识发布闭环 | `project/evidence/WP-0003-minio-publication.md` | `5a5a0ad83e5879979725b60304441568211aaeb4` | 真实 MinIO、全新卷、双租户接口验收、浏览器终态和容器安全回归通过 |

## WP-0004

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- | --- |
| 索引生命周期研究与决策 | `docs/research/WP-0004-indexing-baseline.md`、ADR-0015 | `a3251d1c5` | 固定索引来源、分块、嵌入、异步构建和原子激活边界 |
| 版本化向量索引内核 | `./mvnw -q -pl backend/extensions/vector-stores/vector-pgvector -am test` | `05767095d` | 领域、应用、数据库、工作进程和真实 pgvector 合同通过 |
| 授权检索纵向闭环 | `project/evidence/WP-0004-versioned-index-search.md` | `d78047a9bb58137c6910dbcaac98ac606fbc4867` | 108 项后端测试、双租户场景、容器、前端、权限隔离和性能冒烟通过 |

## WP-0005

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- | --- |
| 模型运行时研究与决策 | `docs/research/WP-0005-spring-ai-runtime.md`、ADR-0016 | `3bc910864` | 固定动态模型对象、不可变助手版本、显式查询流水线和引用后校验边界 |
| 模型目录领域内核 | `project/evidence/WP-0005-model-catalog-domain.md` | `8b5c8e438` | 9 项领域测试及模块架构回归通过，密钥、能力、修订和发布不变量已固定 |
| 供应商连接治理闭环 | `project/evidence/WP-0005-provider-connections.md` | `205838492` | 129 项后端测试、真实 PostgreSQL、租户权限、幂等配额、探测、脱敏和容器验收通过 |
| 模型部署治理闭环 | `project/evidence/WP-0005-model-deployments.md` | `e89b19a10` | 140 项后端测试、冻结修订、能力探测、发布状态机、迁移升级和可重复容器验收通过 |
| 多供应商模型运行时 | `project/evidence/WP-0005-model-runtime-factories.md` | `3c8cb1a52` | 159 项后端测试、独立对话与嵌入工厂、修订缓存、密钥和出站策略、容器重建与纵向回归通过 |
| 助手不可变版本与发布闭环 | `project/evidence/WP-0005-assistant-version-publication.md` | `700419e82` | 171 项后端测试、版本冻结、发布幂等、乐观并发、租户隔离、增量迁移和容器验收通过 |
| 可信查询内核与引用拒答闭环 | `project/evidence/WP-0005-trusted-query-core.md` | `02580199f` | 184 项后端测试、动态嵌入部署链、冻结索引检索、双租户引用回答、拒答、幂等计量及连续两轮容器验收通过 |
| 查询事件序列与可重连事件流 | `project/evidence/WP-0005-query-event-stream.md` | `cf7a14cf4` | 188 项后端测试、13 项全新迁移、既有数据库增量升级、单调事件、断点重放、幂等和跨租户隔离通过 |
| 最终用户可信问答站点闭环 | `project/evidence/WP-0005-query-web-flow.md` | `cabcda733`、`656eafab3` | 192 项后端和 24 项前端测试、同源登录、最小助手目录、浏览器身份隔离、带引用回答、拒答恢复及连续两轮完整容器验收通过 |

## WP-0006

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- | --- |
| 原子查询配额边界 | `project/evidence/WP-0006-query-quota.md` | `29050035252d88681b311ca984f1b68251378f33` | 198 项后端测试、真实 PostgreSQL 并发竞争、旧卷增量迁移、稳定 429、幂等重放和完整容器验收通过 |
| 查询运营与治理控制台 | `project/evidence/WP-0006-query-operations.md` | `9a59c41fc807aa073159397f7e2548c6197ff597` | 205 项后端、27 项前端测试、租户用量实数、脱敏审计、权限、游标、完整状态和容器验收通过 |
| 确定性演示评测与混合重排 | `project/evidence/WP-0006-demo-evaluation.md` | `cfa36bffdd9330329e3c1d13049a9e540d812bac` | 207 项后端、27 项前端测试，8 个固定问题、2 个跨租户和 2 个拒答用例全部通过，二十条候选池性能达标 |
| 查询与治理真实浏览器验收 | `project/evidence/WP-0006-browser-acceptance.md` | `be2ecd65faa4ec62a6c8d7864d4c23a73322a3aa` | 210 项后端、28 项前端测试，查询与治理桌面任务通过，最终评测全部达标且跨租户泄漏为零 |

## WP-0007

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- | --- |
| 备份与隔离恢复演练 | `project/evidence/WP-0007-backup-recovery.md` | `9656d70e732b75472710a1c3e28a2a9ae0bf7fd7`、`c0f2906352e82905bae999cba7369928dc24d8c5` | PostgreSQL 与 3 个 MinIO 对象在隔离环境恢复，事实和摘要一致，Redis 空恢复，租户隔离、原环境健康和资源清理通过，恢复阶段 18 秒 |
| 端到端容量与故障恢复 | `project/evidence/WP-0007-capacity-resilience.md` | `ea40372ae224d6898318ee1262d2b432c0f88022`、`9bb74304861db4a34d330cf3f733ccb2aff54848` | 双租户总并发 8，40 次采样零错误零重放，95 分位 50.201 毫秒、每秒 219.873 次；四个关键服务重启与重启后查询通过 |
| 空环境交付与历史 M1 技术验收 | `project/evidence/WP-0007-M1-technical-acceptance.md` | `a1bc72c8749314687bfd160b8fd06949f1a97f2f`、`fb02924ad96b03b95f970b7b4dc69f0543dd90bb` | 整改前运行结果已保留，但因人工架构复核发现八项阻断问题而撤回，不得用于批准 M1 |
| 既有数据卷受控升级 | `project/evidence/artifacts/WP-0007-upgrade-drill.json` | `d0fc7aa649bc6869205cde49e8c071abe57deef6`、`d9605a59b7379a78deeb0f1b721b443e1d5a3a45` | 一致回滚备份后重建应用，升级前后事实摘要和 18 条迁移记录一致，8 题评测与五入口通过，成功后机密备份已删除 |
| M1 人工发布批准 | `project/evidence/M1-release-approval.md` | `d025a141e27d770cb10eee1e4e973100cf8c7a20` | 用户代表产品、测试和运维职责批准 M1 退出；历史技术验收不作为批准依据 |

## WP-0008

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- | --- |
| 后端架构整改研究基线 | `docs/research/WP-0008-architecture-remediation-baseline.md` | `597fe687f5d91c7f378cb2fdbde59ebcb909e7a7` | 8 个阻断问题、两种结构方案和官方框架依据已固定；实现证据待产生 |
| 平台持久化与雪花标识基座 | `project/evidence/WP-0008-persistence-foundation.md` | `29e9cc098cb03582ee1ad89e409160eb97fe8fd9` | 新增统一雪花标识、MyBatis-Plus 配置和债务递减门禁；216 项后端测试和 7 项门禁测试通过 |
| MyBatis 映射文件整改 | `project/evidence/WP-0008-mybatis-xml.md` | `8104ea62c251eec102bf8fe0de5c858d42ad8448` | 96 条 SQL 迁入 8 个 Mapper XML，注解 SQL 归零，216 项后端测试通过 |
| 主体与成员雪花标识 | `project/evidence/WP-0008-organization-identifiers.md` | `a6415c6e2761501952e9dfdfbaf22163862b8ac3` | 主体、成员、会话、审计和跨上下文成员列改为 64 位标识；218 项后端和 28 项前端测试通过 |
| 全局租户雪花标识 | `project/evidence/WP-0008-tenant-identifier.md` | `0f375f40a436a38ab66eaeb8362bb8dcd7ac4f62` | 六个上下文、向量扩展、数据库与验收脚本统一使用 64 位租户标识；219 项后端和 28 项前端测试通过 |
| 知识聚合雪花标识 | `project/evidence/WP-0008-knowledge-identifiers.md` | `c51337b109716f94a2910e29d6dc5338f3a38e40` | 知识空间、数据源、索引版本及跨上下文引用统一使用 64 位标识；221 项后端和 28 项前端测试通过 |
| 模型目录雪花标识 | `project/evidence/WP-0008-model-identifiers.md` | `4fe3552ff4cef64f18563d2af2d343a418594636` | 供应商连接、模型部署及全部跨上下文模型引用统一使用 64 位标识；223 项后端和 28 项前端测试通过 |
| 助手与查询雪花标识 | `project/evidence/WP-0008-assistant-query-identifiers.md` | `7d6d9e9202830971a3f2a01d1db989c0334df37b` | 助手、查询、事件、审计和前端字符串契约统一使用 64 位标识 |
| 全仓雪花标识收口 | `project/evidence/WP-0008-snowflake-identifiers-complete.md` | `b26a21070a610205c95324a5b758168ddc383a34` | 索引租约、事务事件和追踪标识完成迁移，全仓 Java UUID 文件归零 |
| 安全平台边界 | `project/evidence/WP-0008-security-platform-boundary.md` | `834dddd2adad0db33b37afb309c26ee7502f74d4` | 安全核心与 Sa-Token 实现分层，Sa-Token 越界文件归零 |
| 运行入口与装配边界 | `project/evidence/WP-0008-runtime-bootstrap-boundary.md` | `d9edc1fb0eeffea1d1df81dfc918500eb447ca07` | 三个独立装配模块建立，运行时非启动文件从 41 降至 0 |
| 后端架构整改最终验收 | `project/evidence/WP-0008-final-acceptance.md` | `f88ae6493279b114a7238433d71c2103a2126d37` | 八项阻断问题全部关闭，全量测试、代码走查和最终空环境交付通过，架构债务计数归零 |

## WP-0009

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- | --- |
| 二次重构架构门禁红绿证据 | `project/evidence/WP-0009-architecture-guard-red-green.md` | `2302eb35c` | 新增六项预期红灯后实现递减门禁，12 项门禁测试转绿 |
| 共享基础与平台 Web | `project/evidence/WP-0009-shared-foundation.md` | `b3724db7d` | 共享标记、统一异常、页码契约、Web 自动配置和分页拦截器顺序完成红绿验证 |
| 组织权限经典四层试点 | `project/evidence/WP-0009-organization-access-pilot.md` | `6946c6bc1` | 经典四层、领域边界、能力自动配置和真实认证闭环通过全反应堆验证 |
| 知识数据上下文迁移 | `project/evidence/WP-0009-knowledge-data-context.md` | `25e6070a3` | 经典四层、三个真实页码列表、统一异常、上下文及 MinIO、pgvector 自动配置通过 290 项全反应堆验证 |
| 模型运行时上下文迁移 | `project/evidence/WP-0009-model-runtime-context.md` | `98e97ab0d` | 经典四层、两个真实页码列表、统一异常、跨上下文门面及三类模型扩展自动配置通过 335 项全反应堆验证 |
| 助手交付上下文迁移 | `project/evidence/WP-0009-assistant-delivery-context.md` | `6965430af` | 经典四层、助手及发布目录页码分页、统一异常、发布版本门面和分角色自动配置通过纵向回归 |
| 可信查询上下文迁移 | `project/evidence/WP-0009-trusted-query-context.md` | `3d3090cdf` | 经典四层、审计真实页码分页、统一异常、脱敏运营门面和查询自动配置通过纵向回归 |
| 评测运维上下文迁移 | `project/evidence/WP-0009-evaluation-operations-context.md` | `49868ba71` | 最后一个上下文完成经典四层、统一异常和自主装配，六上下文旧目录文件归零 |
| 持久化与启动模块收口 | `project/evidence/WP-0009-persistence-and-bootstrap.md` | `eb2fb1f49` 至 `bec76c94b` | 普通单表操作迁入 MyBatis-Plus、31 条复杂 SQL 全量登记、三个 bootstrap 独立可执行、runtimes 文件归零 |
| 全量代码与门禁验证 | `project/evidence/WP-0009-full-validation.md` | `d025a141e` | 309 项后端、28 项前端及全部治理、架构和运维脚本测试通过 |
| 容器交付与运维演练 | `project/evidence/WP-0009-delivery-drills.md` | `d554b1fa5`、`fb4371ced` | 空环境交付、固定评测、18 秒隔离恢复、40 次容量样本、四服务恢复和既有卷升级通过 |
| 六维代码走查 | `project/evidence/WP-0009-code-review.md` | `d025a141e` | 正确性、安全、复杂度、性能、架构和测试质量问题全部修复，无未关闭高优先级问题 |

## WP-0010

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- | --- |
| M2 工作包激活与真实模型调用授权 | `project/evidence/M1-release-approval.md` | 待产生 | 用户已批准火山方舟用于受控研发和测试调用；不得记录或提交 API Key |
| 加密模型凭据安全基座 | `project/evidence/WP-0010-encrypted-credential-foundation.md` | `2bba83074` | 信封加密、版本化本地研发主密钥与篡改失败关闭测试通过；尚未完成持久化、权限或真实调用 |
| 受控模型目录与分层配置领域基线 | `project/evidence/WP-0010-model-configuration-domain.md` | `d8c3b6cb3`、`2666dfafe`、`9c9972448` | 平台、租户、个人所有权、受控目录不变量、平台目录管理和受控自动装配通过模块回归；尚未完成授权撤销或真实调用 |
| 模型配置与加密凭据数据库基线 | `project/evidence/WP-0010-model-configuration-persistence.md` | `928e568d2`、`b68cfb6f1`、`71e484790`、`6b64ccf71`、`fa1a2b392`、`b38e8b22c`、`2997db59d`、`a21a80a9b`、`376ccb533`、`3951a5dd0`、`0f97bba52` | 真实 PostgreSQL 验证三层所有权、端点约束、无 API Key 明文字段、密文写入/短生命周期解密、受控配置仓储、原子写入、显式本地研发密钥装配、脱敏创建/轮换、授权撤销、显式启用、停用与脱敏读取；尚未完成真实调用 |
| 受控模型目录能力快照 | `project/evidence/WP-0010-controlled-model-catalog-capabilities.md` | `e3b89585d` | 平台确认的上下文、输出上限或嵌入维度随目录条目冻结；真实 PostgreSQL 验证写回、发布保留和旧条目缺快照失效关闭 |
| 可用模型配置安全查询门面 | `project/evidence/WP-0010-usable-model-configuration-lookup.md` | `c120cd256` | 已启用、已授权、已配置凭据且目录已发布的模型配置才投影为无秘密快照；跨成员、无凭据、未发布目录与类型不匹配均失败关闭 |
| 受控嵌入短生命周期运行时 | `project/evidence/WP-0010-governed-embedding-runtime.md` | `ed5f41c5f` | 单次调用重验配置、目录和请求快照；受控配置不走长期凭据引用或模型缓存 |
| 受控模型配置选择与索引创建契约 | `project/evidence/WP-0010-controlled-model-configuration-selection.md` | `1d720a692` | 当前成员仅能分页读取个人、租户和已授权平台配置；控制台仅选择已启用、已配置凭据的嵌入配置创建新索引 |
| 助手受控模型配置绑定与同源发布 | `project/evidence/WP-0010-assistant-governed-model-bindings.md` | `b7ce49410` | 新助手版本显式冻结 Chat/Embedding 配置；创建和发布均拒绝失效配置、旧索引与任何嵌入快照差异 |
| 受控对话短生命周期运行时 | `project/evidence/WP-0010-governed-chat-runtime.md` | `9608d15fa` | Chat 配置在每次调用内重验、短生命周期解密和建模；配置修订变化在工厂调用前失败关闭 |
| 受控模型查询运行时 | `project/evidence/WP-0010-governed-query-runtime.md` | `e65024300` | 受控助手与活动索引在配额后重验配置快照；查询不回退历史部署，pgvector 按完整配置冻结检索 |
| 受控真实模型验收装配 | `project/evidence/WP-0010-real-model-runtime-assembly.md` | `0cb172350` | 查询进程装配受控运行时，独立 compose 覆盖层传递主密钥、端点和超时，不传递 API Key |
| 受控模型配置控制台 | `project/evidence/WP-0010-model-configuration-console.md` | `e587fb823` | 已发布目录分页、受控创建、启用与凭据轮换在控制台闭环；API Key 不回显、不保留在页面状态 |
| 真实模型受控验收编排 | `project/evidence/WP-0010-real-model-acceptance-orchestration.md` | `f014be163`、`2ac742be1`、`4ee13980b` | 目录、加密配置、索引、助手和查询的真实模型脚本已具备；Mock 闭环、无密钥和过宽文件权限失败关闭通过，外部调用待受控终端执行 |
| 火山方舟真实模型验证切换 | `project/evidence/WP-0010-volcengine-ark-provider-transition.md` | `adf0a1a34` | 标准 `/api/v3` Chat 与多模态 Embedding 协议、2048 维存储上限、真实索引和带引用回答通过；全量门禁通过，等待 M2 人工评审 |
| M2 自动验收人工评审请求 | `project/evidence/M2-automatic-acceptance-review.md` | `c8e66787d` | 自动验收、产品经理浏览器验收、范围与复核要点已登记；后续人工结论已通过 |
| M2 产品经理浏览器验收与蓝图复核 | `project/evidence/WP-0010-product-manager-browser-review.md` | `c8e66787d` | 受控模型配置、可信问答和脱敏治理已实际操作；密钥不回显和创建状态缺陷已修复，M3 至 M8 产品覆盖计划已校准 |
| M2 人工评审通过 | `project/evidence/M2-release-approval.md` | `99f16bc87` | 用户批准 M2 退出并进入 M3；三前端应用、SDK/API 渠道与平台/租户双范围产品边界已确认 |
| M3 数据生命周期设计 | `project/evidence/WP-0011-data-lifecycle-design.md` | `6d84f6a38` | 当前文本数据源与索引事实已核实；ADR-0028 固定受治理来源、同步产物、质量和类型化连接器边界 |
| M3 文件来源生命周期领域切片 | `project/evidence/WP-0011-data-lifecycle-design.md` | `c7b8b60ca` | 文件来源的草稿、探测、同步、退役和乐观修订状态机先红后绿；知识数据依赖上下文回归通过 |
| M3 同步产物与质量领域切片 | `project/evidence/WP-0011-data-lifecycle-design.md` | `aa07527cc` | 不可变同步产物冻结来源修订和摘要；质量阻断拒绝发布判断，知识数据依赖上下文回归通过 |
| M3 受治理来源真实持久化切片 | `project/evidence/WP-0011-data-lifecycle-design.md` | `e214637192ec7919c2cffb7a3a4d25c8c5bb3a9e` | 独立 Flyway 事实表、真实 PostgreSQL 租户隔离、来源修订外键和质量失败事务回滚通过 |
| M3 来源失败恢复与并发状态更新 | `project/evidence/WP-0011-data-lifecycle-design.md` | `64b95dfec0be21437b8f1f72e5c97a5784842324` | 失败操作最小审计、按操作恢复、修订乐观锁和真实 PostgreSQL 并发失败方无覆盖通过 |
| M3 同步原子完成 | `project/evidence/WP-0011-data-lifecycle-design.md` | `00bb64625ece656f59721d94193ed15105d70c7e` | 来源锁定、同步产物、质量结论和来源就绪状态同一事务提交；质量与并发失败均不残留半成品 |
| M3 可信来源生命周期应用用例 | `project/evidence/WP-0011-data-lifecycle-design.md` | `6a6829edb5e7281e58ebd14c206414a4d134988a` | 可信上下文、知识写权限、租户边界和乐观修订被应用服务统一执行；外部 I/O 未进入事务 |
| M3 受治理文件修订对象事实 | `project/evidence/WP-0011-data-lifecycle-design.md` | `5eb5759429de4174748851f9041ac4c7c8615d0c`、`a55f6a406e3c76e6dedb0a9d404f7ae696ec169c` | 首个 UTF-8 FILE 修订冻结平台对象键、摘要、长度和类型；真实 PostgreSQL 验证当前修订与对象事实持久化，架构门禁收口 |
| M3 FILE 来源真实对象探测 | `project/evidence/WP-0011-data-lifecycle-design.md` | `1e19f63ed` | 可信上下文、权限前置、持久化开始探测、事务外对象元数据核验、失败恢复与自动装配通过；全量门禁通过 |
| M3 FILE 同步与质量事实闭环 | `project/evidence/WP-0011-data-lifecycle-design.md` | `ddf23caf5ce23c29528163d041af2e7b605270ec` | 流式摘要核验、同步失败恢复、原子产物与 PASS 质量事实，以及真实控制面 HTTP／PostgreSQL 闭环通过；M3 尚未完成 |
| M3 已发布同步产物持久化 | `project/evidence/WP-0011-data-lifecycle-design.md` | `73cd917e3` | 数据库质量复核、`BLOCK` 反绕过、来源版本原子递增、发布事实冻结、租户隔离和自动配置回归通过；M3 尚未完成 |
| M3 FILE 同步产物受控发布入口 | `project/evidence/WP-0011-data-lifecycle-design.md` | `92f9440f7` | 知识写权限前置、来源—产物归属、受控 HTTP 委派、发布质量复核与自动配置回归通过；M3 尚未完成 |
| M3 类型化索引来源快照 | `project/evidence/WP-0011-data-lifecycle-design.md` | `a43e6e93f`、`658e12d13`、`62a4812f7` | 历史文本兼容、已发布 FILE 产物选择、质量和产物冻结、混合来源租约、实际构建器与退役拒绝回归通过；M3 尚未完成 |
| M3 FILE 控制台全生命周期与工作器恢复 | `project/evidence/WP-0011-data-lifecycle-design.md` | `96637b173`、`10ac2d6aa`、`8d7f19996` | 知识管理员浏览器完成 FILE 的探测、同步、质量、显式发布和退役；受控模型运行时缺失时工作器安全启动且索引入口给出可恢复提示；真实 Embedding 索引构建仍受本地研发主密钥前置条件限制，M3 尚未完成 |
| M3 已发布同步产物 SQL 门禁回归 | `project/evidence/WP-0011-data-lifecycle-design.md` | `2eb465404` | 补齐发布事实写入与租户限定读取的 XML SQL 架构登记；`make validate` 通过，M3 尚未完成 |
| M3 助手受控模型 API 全量门禁恢复 | `project/evidence/WP-0011-data-lifecycle-design.md` | `f9e94b4c4` | 将 API 集成夹具迁移至 M2 受控 Chat／Embedding 配置与同源索引绑定；定向回归、`make project-verify` 与 `make validate` 均通过，M3 尚未完成 |
| M3 FILE 控制台会话过期恢复 | `project/evidence/WP-0011-data-lifecycle-design.md` | `300458ed8` | 当前 401 清除会话、旧请求 401 不覆盖新会话，FILE 工作区清除受保护状态并回到登录入口；36 项前端测试与生产构建通过，M3 尚未完成 |
| M3 FILE 刷新后受控重建 | `project/evidence/WP-0011-data-lifecycle-design.md` | `cc72f3564` | 已发布 FILE 来源的无敏感摘要分页读取、刷新后选择与仅 `READY` 来源重建通过；真实 PostgreSQL、浏览器验收与最终全量门禁通过，M3 尚未完成 |
| M3 ADR-0031 领域聚合操作边界整改 | `project/evidence/WP-0011-domain-aggregate-operation-boundary.md` | `cfccfe3f5` | 全平台领域设计规约和两项硬门禁生效；FILE 同步／发布不变量收回领域，领域、应用、真实 PostgreSQL、HTTP 与全仓门禁通过；M3 尚未完成 |
| M3 OBJECT_STORAGE 范围与不可变对象集合修订 | `project/evidence/WP-0011-data-lifecycle-design.md` | `d3a960ce7` | ADR-0030 的不透明范围、选择修订和冻结对象集合完成领域、真实 PostgreSQL 与架构门禁验证；登记、探测、同步、发布和浏览器闭环尚未完成，M3 仍进行中 |
| M3 OBJECT_STORAGE 受控登记与范围探测 | `project/evidence/WP-0011-data-lifecycle-design.md` | `0535afb6a` 至 `777c0dbf4` | 部署静态范围解析、权限登记、HTTP 入口、范围一致性探测和失败关闭正文读取端口通过模块、治理与架构门禁；同步、质量、发布和浏览器闭环尚未完成 |
| M3 OBJECT_STORAGE 私有正文同步与发布 | `project/evidence/WP-0011-object-storage-synchronization.md` | `5094c7323`、`932b84c65` | 真实 MinIO 映射与流式摘要核验、固定 `\n\n` 分隔、PASS 质量发布、HTTP 委派、真实 PostgreSQL 原子提交及自动配置回归通过；M3 仍进行中 |
| M3 OBJECT_STORAGE 控制台闭环 | `project/evidence/WP-0011-object-storage-console.md` | `60dc61c63` | 平台签发范围选择登记、冻结集合同步、显式发布和受控索引创建／发布均接入控制台；43 项前端测试与全量验证通过，真实浏览器操作仍等待可写会话 |
| M3 STRUCTURED_DATASET 私有 CSV 修订 | `project/evidence/WP-0011-data-lifecycle-design.md` | `de84e6280` | 私有 UTF-8 CSV、冻结字段映射、受控行过滤、真实 PostgreSQL 有序写后读／租户隔离和自动配置回归通过；登记、解析、质量、发布、索引和浏览器闭环尚未完成 |
| M3 STRUCTURED_DATASET 受控私有 CSV 登记 | `project/evidence/WP-0011-data-lifecycle-design.md` | `6d45f59a9` | 权限前置、严格 UTF-8 暂存、私有内容寻址 MinIO 写入、失败补偿、HTTP 与自动配置回归通过；解析、质量、发布、索引和浏览器闭环尚未完成 |
| M3 STRUCTURED_DATASET 控制台登记入口 | `project/evidence/WP-0011-data-lifecycle-design.md` | `48d68d243` | 冻结映射和私有 CSV multipart 客户端、登录／会话恢复、受控展示与前端 39 项回归、生产构建通过；同步、质量、发布与索引仍待 ADR-0032 接受 |
| M3 STRUCTURED_DATASET 登记即绪、同步与发布 | `project/evidence/WP-0011-structured-dataset-synchronization.md` | `36a185124` | 私有 CSV 原子登记后直接就绪，领域同步／质量与显式发布、真实 PostgreSQL、HTTP、自动配置及前端 40 项回归通过；真实浏览器操作仍等待可写演示会话，M3 尚未完成 |
| M3 STRUCTURED_DATASET 索引控制台 | `project/evidence/WP-0011-structured-dataset-index-console.md` | `c509413ad` | 仅当前已发布同步产物可创建索引，复用受控 Embedding、索引构建轮询与显式发布；前端反例和全量验证通过，真实浏览器操作仍等待可写会话，M3 尚未完成 |
| M3 令牌派生登录态边界 | `project/evidence/WP-0011-token-derived-authentication-context.md` | `fc13521f7`、`15e0ce021` | ADR-0033 固定租户、主体、成员、凭据、渠道与权限仅从令牌会话恢复；解析器与真实 HTTP 回归证明伪造参数和 Header 不改变归属，M3 仍进行中 |
| M3 历史同步产物升级与对象存储条件装配 | `project/evidence/WP-0011-legacy-sync-artifact-upgrade.md` | `efe6f5cd3`、`7454d58c3` | 历史产物不伪造冻结内容且不可进入索引候选；默认无对象正文映射部署安全启动、控制台无凭据入口回归通过，M3 仍进行中 |
| M3 浏览器验收续证与运行前置条件 | `project/evidence/WP-0011-browser-runtime-acceptance.md` | `3f116ba34`、`469ceb8b9` | FILE 完成登记至显式发布、STRUCTURED_DATASET 浏览器 `text/csv` 登记通过；OBJECT_STORAGE 完成登记、探测、同步、PASS 与显式发布；没有可授权的 `PLATFORM`／`TENANT` `EMBEDDING` 配置，索引失败关闭，WP-0011 受阻 |
| M3 自动验收与人工评审请求 | `project/evidence/M3-automatic-acceptance-review.md` | `dcec78e19` | ADR-0035 来源快照分块身份、真实 PostgreSQL、真实模型 OBJECT_STORAGE 索引发布／重建、全量门禁均通过；当前仅待人工评审，不得进入 M4 |
| M3 人工评审通过 | `project/evidence/M3-release-approval.md` | `918ee7d91` | 用户批准 M3 退出并进入 M4 设计；M5 至 M8 仍只保留路线图，不提前激活或实现 |

## WP-0012

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- |
| M4 草稿测试与查询反馈影响分析 | `project/evidence/WP-0012-m4-impact-analysis.md` | `fadab27d9` | 代码图谱确认普通查询的已发布版本边界、草稿读取 seam、查询事件／运营投影影响和三个待决策项；ADR-0036 接受前不实施 |
| M4 草稿测试 TDD 路线 | `project/evidence/WP-0012-m4-tdd-test-route.md` | `25663a013` | 首个红绿切片、真实 PostgreSQL、自动配置、HTTP、浏览器与最终验证层级已固定；等待 ADR-0036 接受 |
| ADR-0036 人工接受 | `project/evidence/ADR-0036-approval.md` | `d97ba580b` | 用户接受草稿测试、独立测试配额、最小反馈和受权诊断边界；M4 可开始实现 |
| ADR-0037 人工接受 | `project/evidence/ADR-0037-approval.md` | 本次控制面提交 | 用户接受草稿与普通查询共用可信回答执行门面；M4 可开始共享执行红绿切片 |
| ADR-0038 人工接受 | `project/evidence/ADR-0038-approval.md` | 本次控制面提交 | 用户接受查询诊断最小事实投影与 `audit:read` 受权读取；M4 可开始诊断红绿切片 |
| M4 草稿助手版本受限读取门面 | `project/evidence/WP-0012-draft-assistant-lookup.md` | `f9465dc45` | 草稿只在同租户且 `assistant:write` 时投影为测试快照；普通查询保持只读 `PUBLISHED` 版本 |
| M4 独立草稿测试运行领域基础 | `project/evidence/WP-0012-assistant-test-run-domain.md` | `74d2da026` | 独立根、冻结依赖、题目摘要、独立测试配额和不含正文的终态事实已红绿验证；持久化与执行编排尚未实施 |
| M4 独立草稿测试运行持久化 | `project/evidence/WP-0012-assistant-test-run-persistence.md` | `be8a16daa` | 真实 PostgreSQL 证明独立表、租户原子日／并发配额、终态乐观提交、最小引用恢复和题目／回答正文列归零；可信执行编排尚未实施 |
| M4 查询反馈领域基础 | `project/evidence/WP-0012-query-feedback-domain.md` | `32c3a87e1` | 独立不可变反馈根、受控正负原因与正文存储归零已红绿验证；完成态、唯一持久化、保留边界和接口尚未实施 |
| M4 查询反馈持久化与受控接口 | `project/evidence/WP-0012-query-feedback-persistence-api.md` | `7cb00b563` | 真实 PostgreSQL 原子去重、请求成员三列外键、查询删除级联、自动配置和令牌派生 HTTP 入口回归通过；草稿可信执行仍等待 ADR-0037 |
| M4 查询反馈前端入口 | `project/evidence/WP-0012-query-feedback-browser-entry.md` | `894324933` | 固定枚举反馈、仅内存令牌、重复反馈稳定显示和前端 12 项回归及生产构建通过；真实角色浏览器验收仍待完整 M4 环境 |
| M4 统一草稿助手可信回答执行 | `project/evidence/WP-0012-assistant-trusted-answer-execution.md` | `d22c3e65a` | 普通查询和草稿测试共享唯一可信执行门面；独立配额／根、真实 PostgreSQL、自动配置、Bearer HTTP 回归和全反应堆验证通过；ADR-0038 诊断投影尚未实施 |
| M4 查询诊断事实原子投影 | `project/evidence/WP-0012-query-diagnostic-projection.md` | `8fcd66e61` | ADR-0038／ADR-0039 的候选可得性、原子终态提交、真实 PostgreSQL 回滚与级联、`audit:read` 租户投影、Bearer HTTP 回归和 `make validate` 均通过；M4 仍待人工评审 |
| M4 自动验收与人工评审请求 | `project/evidence/M4-automatic-acceptance-review.md` | 本次控制面提交 | 固定比较点技术评审、领域与架构门禁、真实 PostgreSQL／Bearer HTTP 与全量工程门禁均通过；当前仅待人工评审，不得进入 M5 |
| M4 人工评审通过 | `project/evidence/M4-release-approval.md` | `c2f681e62` | 用户批准 M4 退出并进入 M5 设计；M6 至 M8 仍只保留路线图，不提前激活或实现 |

## WP-0013

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- |
| M5 模型扩展影响分析 | `project/evidence/WP-0013-m5-impact-analysis.md` | 本次控制面提交 | 代码图谱确认模型运行时影响面、可信回答唯一 seam 与查询终态原子边界；ADR-0040 提议在用户接受前不实施 |
| M5 冻结聊天路由 TDD 路线 | `project/evidence/WP-0013-m5-tdd-test-route.md` | 本次控制面提交 | 首条切片的红绿顺序、失败分类、真实 PostgreSQL、HTTP、浏览器和全量证据合同已固定；ADR-0040 接受前不创建测试或实现 |
| ADR-0040 人工接受 | `project/evidence/ADR-0040-approval.md` | 本次控制面提交 | 用户接受冻结聊天模型路由、有限回退和内部调用轨迹边界；M5 可按红绿路线开始首条切片 |
| M5 调用轨迹 ADR 对齐 | `project/evidence/WP-0013-m5-model-invocation-trace.md` | 本次提交 | 每次候选保留冻结配置与目录修订、标准化结果、令牌用量和时延；查询与草稿测试均在终态事务原子写入，真实 PostgreSQL、HTTP、全量模块测试和架构门禁通过；M5 仍进行中 |
| M5 真实运行与浏览器验收续证 | `project/evidence/WP-0013-m5-browser-runtime-acceptance.md` | 本次提交 | 真实模型覆盖层、密文恢复开关、真实索引与带引用查询、最终用户浏览器最小披露均通过；M5 仍待自动验收汇总和人工评审 |
| M5 自动验收与人工评审请求 | `project/evidence/M5-automatic-acceptance-review.md` | `b38ec7387` | 自动验收、真实模型运行、最终用户浏览器最小披露、领域和架构门禁均通过；当前仅待人工评审，不得进入 M6 |
| M5 人工评审通过 | `project/evidence/M5-release-approval.md` | 本次控制面提交 | 用户批准 M5 退出并进入 M6 设计；M6 首条切片仍须先完成设计和 ADR，M7 至 M8 不激活 |

## WP-0014

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- | --- |
| M6 企业治理影响分析 | `project/evidence/WP-0014-m6-impact-analysis.md` | 本次控制面提交 | 代码图谱确认令牌派生租户上下文、同租户运营读取、助手发布和最小运行事实的边界；选定独立平台上下文方案。 |
| M6 企业治理与发布制度领域设计 | `docs/architecture/foundation-m6-enterprise-governance-design.md` | 本次控制面提交 | 统一语言、上下文地图、场景、聚合、状态机、后续评测／发布 seam 和首条切片证据合同已登记；等待 ADR-0041 人工接受。 |
| ADR-0041 平台请求上下文与租户生命周期 | `docs/adr/ADR-0041-platform-request-context-and-tenant-lifecycle.md` | 本次控制面提交 | 提议独立且令牌派生的平台上下文、可恢复租户生命周期和跨根操作结果；未接受前不实施。 |
| ADR-0041 人工接受 | `project/evidence/ADR-0041-approval.md` | 本次控制面提交 | 用户接受平台请求上下文与租户生命周期边界；仅首条切片获实施授权。 |
| ADR-0042 初始管理员身份提案 | `docs/adr/ADR-0042-initial-tenant-administrator-identity.md` | 本次控制面提交 | 现有租户成员凭据没有全局主体／凭据边界；提议全局身份聚合，未接受前不复制密码哈希或实施生命周期链路。 |
| ADR-0042 人工接受 | `project/evidence/ADR-0042-approval.md` | 本次控制面提交 | 用户接受全局主体与凭据聚合；首条切片可按红绿路线实施。 |
| M6 全局主体与生命周期领域红绿 | `project/evidence/WP-0014-global-identity-domain.md` | 本次提交 | `SubjectIdentity` 与 `TenantLifecycle` 的实际编译红灯和组织访问模块绿灯已登记；持久化、会话和接口仍未实施。 |
| M6 全局主体与成员资格迁移 | `project/evidence/WP-0014-global-identity-migration.md` | 本次提交 | 历史成员凭据拆分为唯一全局主体与 `tenant_membership`，真实 PostgreSQL 歧义失败关闭、主体禁用、双租户隔离和 Bearer 登录回归通过；平台会话与生命周期编排仍未实施。 |
| M6 租户建立领域服务 | `project/evidence/WP-0014-tenant-provisioning-domain.md` | 本次提交 | 初始管理员成员资格根和不可变建立结果的红绿测试通过；持久化、平台会话和 HTTP 入口仍未实施。 |
| ADR-0043 平台租户治理授权 | `docs/adr/ADR-0043-platform-tenant-governance-grant.md` | 本次提交 | 提议独立平台授权事实与 `tenant-governance:write`，等待用户接受后才实施平台会话与治理路由。 |
| ADR-0043 人工接受 | `project/evidence/ADR-0043-approval.md` | 本次提交 | 用户接受独立平台授权事实与 `tenant-governance:write`；平台会话可按红绿路线实施。 |
| M6 平台治理授权与独立会话 | `project/evidence/WP-0014-platform-governance-session.md` | 本次提交 | 平台授权读取投影、独立 Sa-Token 会话、自动配置、真实 PostgreSQL 与 Redis HTTP 回归通过；租户令牌无法作为平台令牌解析。 |
| ADR-0044 历史初始管理员回填边界 | `docs/adr/ADR-0044-historical-tenant-initial-administrator-backfill.md` | 本次控制面提交 | 用户接受由人工运营方提供显式 `tenantId → SubjectId` 映射，拒绝以成员顺序、角色或权限猜测历史初始管理员。 |
| ADR-0044 人工接受 | `project/evidence/ADR-0044-approval.md` | 本次控制面提交 | 用户授权历史回填、新租户原子建立、最小事件和受限平台治理路由实现。 |
| ADR-0045 历史状态合法回填 | `docs/adr/ADR-0045-historical-tenant-lifecycle-state-reconciliation.md` | `b657f0227` | 用户接受严格方案 A：历史状态只决定目标，必须经合法生命周期迁移达到最终状态。 |
| M6 租户生命周期持久化 TDD 路线 | `project/evidence/WP-0014-tenant-lifecycle-tdd-route.md` | 本次提交 | 固定领域、真实 PostgreSQL、原子提交、平台 Bearer 与浏览器角色的红绿顺序；ADR-0044、ADR-0045 已接受。 |
| M6 历史租户状态回填领域红绿 | `project/evidence/WP-0014-tenant-lifecycle-tdd-route.md` | 本次提交 | 实际编译红灯后，`ENABLED` 只经建立与激活到 `ACTIVE`，`DISABLED` 只经建立、激活与挂起到 `SUSPENDED`；主体、成员归属和启用状态均失败关闭。 |
| M6 历史租户生命周期真实 PostgreSQL 回填 | `project/evidence/WP-0014-tenant-lifecycle-tdd-route.md` | 本次提交 | 实际编译红灯后，真实 PostgreSQL 原子提交根、管理员角色和最小状态事实；缺失、重复、跨租户、禁用、非成员及发件箱失败均无残留，自动配置回归通过。 |
| ADR-0046 人工接受 | `project/evidence/ADR-0046-approval.md` | 本次控制面提交 | 用户接受回填原子认证切换：切换后只以 `TenantLifecycle.status = ACTIVE` 签发租户会话。 |
| M6 租户生命周期认证切换 | `project/evidence/WP-0014-tenant-lifecycle-tdd-route.md` | 本次提交 | 真实 PostgreSQL 回填成功才原子激活无敏感切换事实；失败无残留，切换后认证只接受 `ACTIVE` 生命周期根。 |
| ADR-0047 初始管理员选择契约 | `docs/adr/ADR-0047-initial-tenant-administrator-selection-contract.md` | 本次控制面提交 | 提议仅将既有 `SubjectId` 作为创建租户的业务资源目标；当前操作者、权限与令牌仍只由 `PlatformRequestContext` 恢复。未接受前不实施。 |
| ADR-0047 人工接受 | `project/evidence/ADR-0047-approval.md` | 本次控制面提交 | 用户接受以既有 `SubjectId` 作为初始管理员业务资源目标；新租户建立可按红绿路线实施。 |
| M6 新租户原子建立 | `project/evidence/WP-0014-tenant-lifecycle-tdd-route.md` | 本次提交 | 平台上下文授权前提、不可用初始管理员失败关闭、固定不可认证兼容行、准备中生命周期、初始管理员成员资格与发件箱真实 PostgreSQL 原子提交及自动配置回归通过。 |
| M6 租户生命周期状态迁移与受限 HTTP | `project/evidence/WP-0014-tenant-lifecycle-tdd-route.md` | 本次提交 | 合法激活／挂起／恢复、根锁与修订原子提交、发件箱回滚、应用层 DTO、严格业务目标请求、演示认证切换与平台／租户 Bearer HTTP 闭环通过；控制台浏览器任务仍待完成。 |
| ADR-0048 统一浏览器登录与角色工作区 | `docs/adr/ADR-0048-unified-browser-login-and-role-workspace.md` | 本次控制面提交 | 代码图谱确认现有多工作区独立内存会话；提议同源 HttpOnly 双范围会话和只读能力清单，等待人工接受前不实施。 |
| ADR-0048 人工接受 | `project/evidence/ADR-0048-approval.md` | 本次控制面提交 | 用户接受同源 HttpOnly 双范围浏览器会话与最小能力清单；可按红绿路线实施，不扩大到 M7／M8。 |
| ADR-0048 本地 HTTPS 验收延期 | `project/evidence/ADR-0048-local-https-deferment.md` | 本次提交 | 用户确认本地不安装 System keychain CA；仅延后正式部署的真实浏览器 HTTPS cookie、注销与自然过期证据，不降低安全契约或 M6／M7 门禁。 |
| M6 统一浏览器会话 TDD 路线 | `project/evidence/WP-0014-browser-session-tdd-route.md` | 本次提交 | 范围 cookie、统一认证编排、`HttpOnly` 空体登录、CSRF、能力清单、会话族注销、无 Bearer 的两套前端 API 客户端、同源公开入口、控制台／query 唯一根登录入口、无 token 跨页签失效传播及平台生命周期工作区红绿已完成；夹具外键清理与恢复演练单一公开入口断言已对齐，`make validate` 全绿；租户角色与平台管理员单页签生命周期、两类会话跨页签注销已验收。平台跨页签生命周期读取和无写权限运营主体不存在，须先由新 ADR 定义边界；会话自然过期与 HTTPS Cookie 仍待实现。 |
| ADR-0049 平台生命周期读取投影与角色夹具 | `docs/adr/ADR-0049-platform-lifecycle-read-projection.md` | 本次控制面提交 | 提议服务器权威、分页、最小生命周期投影和无写权限运营夹具；未接受前不新增接口、迁移、前端路由或演示主体。 |
| ADR-0049 人工接受 | `project/evidence/ADR-0049-approval.md` | 本次控制面提交 | 用户接受方案 B；可按红绿路线实施受限读取投影与运营拒绝夹具，不扩大到成员、正文或后续 M6／M7 能力。 |
| M6 平台生命周期受限读取投影 | `project/evidence/WP-0014-platform-lifecycle-read-projection.md` | 本次提交 | 服务器权威分页投影、写后重读、无写权限平台运营会话与读取／写入失败关闭，应用、真实 PostgreSQL、自动配置、HTTP 与前端回归通过；真实浏览器两页签、HTTPS Cookie 与自然过期仍待完成。 |
| ADR-0050 人工接受 | `project/evidence/ADR-0050-approval.md` | 本次控制面提交 | 用户接受冻结评测运行、发布决定与授权结果边界；指标、预算、例外和导出规则仍待独立 ADR。 |
| ADR-0051 人工接受 | `project/evidence/ADR-0051-approval.md` | 本次控制面提交 | 用户接受私有合成评测数据集与冻结质量门禁；可按红绿路线实现最小评测与发布决定闭环。 |
| ADR-0052 人工接受 | `project/evidence/ADR-0052-approval.md` | 本次控制面提交 | 用户接受复用 `assistant:write` 的评测与发布授权边界；可实现私有内容、应用编排和受限接口。 |
| M6 私有评测与发布门禁设计／TDD 路线 | `project/evidence/WP-0014-evaluation-release-design.md` | 本次设计提交 | 代码图谱确认发布授权 seam、影响面与私有正文边界；红绿顺序已固定，尚未开始生产实现。 |
| M6 私有评测与发布门禁领域基础 | `project/evidence/WP-0014-evaluation-release-domain.md` | 本次提交 | 两条纯领域红绿、失败关闭和发布授权匹配回归通过；包预算违规已通过结构拆分整改，持久化与应用层仍未开始。 |
| M6 私有评测数据集登记应用边界 | `project/evidence/WP-0014-evaluation-registration-application.md` | 本次提交 | 令牌派生 `assistant:write`、私有内容端口、失败补偿和无正文元数据提交红绿回归通过；真实存储与 PostgreSQL 尚未实施。 |
| M6 私有评测数据集真实存储与装配 | `project/evidence/WP-0014-evaluation-dataset-private-storage.md` | 本次提交 | 真实 PostgreSQL 只保存清单元数据，独占私有 MinIO 流式校验摘要且匿名读取被拒绝；自动配置仅在完整端口存在时注册登记服务。条目编码与 HTTP 尚未获 ADR 授权。 |
| ADR-0053 私有评测数据集条目编码 | `docs/adr/ADR-0053-private-evaluation-dataset-entry-encoding.md` | 本次控制面提交 | 严格 JSON Lines、服务端摘要与条目计数已冻结；受控上传与评测运行可按红绿路线实施。 |
| ADR-0053 人工接受 | `project/evidence/ADR-0053-approval.md` | 本次控制面提交 | 用户接受严格 JSON Lines、服务端摘要与计数；可实施受控校验、私有存储补偿和 multipart HTTP，仍不开放正文读取或 M7／M8。 |
| ADR-0054 人工接受 | `project/evidence/ADR-0054-approval.md` | 本次控制面提交 | 用户接受由 assistant-delivery 提供确定性受控模型路由摘要；可用于冻结评测运行，不含端点、密钥或正文。 |
| ADR-0055 人工接受 | `project/evidence/ADR-0055-approval.md` | 本次控制面提交 | 用户接受仅内存、逐条且不持久化正文的受限评测执行门面；执行结果只可缩减为无正文事实。 |
| ADR-0056 人工接受 | `project/evidence/ADR-0056-approval.md` | 本次控制面提交 | 用户接受 `chunkId` 作为评测期望证据与受限执行结果的唯一稳定标识。 |
| M6 评测证据稳定标识红绿 | `project/evidence/WP-0014-evaluation-evidence-stable-identifier.md` | 本次提交 | 私有 JSON Lines 在写入对象前拒绝任意字符串，只接受 64 位小写 SHA-256 `chunkId`；定向和全模块回归通过。 |
| M6 受限评测助手执行门面 | `project/evidence/WP-0014-evaluation-assistant-execution.md` | 本次提交 | 仅内存逐条输入、草稿修订与路由摘要复核、无正文 `chunkId` 结果和失败缩减通过；评测运行应用层尚未连接。 |
| M6 私有评测 JSON Lines 受控登记 | `project/evidence/WP-0014-evaluation-dataset-json-lines-registration.md` | 本次提交 | 严格 UTF-8 JSON Lines、服务端摘要与条目计数、私有暂存清理、multipart HTTP、控制面迁移与对象存储部署接线均通过；不开放正文读取或评测执行。 |
| M6 受限评测运行应用与持久化 | `project/evidence/WP-0014-evaluation-run-execution.md` | 本次提交 | 同租户逐条内存执行、失败关闭、冻结观察/指标、真实 PostgreSQL、MinIO 读取和条件自动配置回归通过；HTTP、浏览器与发布授权仍未实施。 |
| M6 受限评测运行 HTTP 与控制台入口 | `project/evidence/WP-0014-evaluation-run-http-browser.md` | 本次提交 | 令牌派生上下文、只含两个业务标识的 HTTP 请求、无正文终态投影、自动配置和统一登录控制台任务回归通过；正式 HTTPS 浏览器证据仍按 ADR-0048 延后。 |
| M6 发布决定与助手发布授权 | `project/evidence/WP-0014-release-decision-authorization.md` | 本次提交 | 冻结 `policyVersion`、固定枚举 `reasonCode`、真实 PostgreSQL、无请求体 HTTP 投影和助手草稿发布失败关闭回归通过。 |
| ADR-0057 人工接受 | `project/evidence/ADR-0057-approval.md` | 本次控制面提交 | 用户接受企业账户、角色权限和菜单能力投影；菜单不成为独立授权事实。 |
| ADR-0058 人工接受 | `project/evidence/ADR-0058-approval.md` | 本次控制面提交 | 用户接受厂商连接、模型发现、真实能力探测、发布与健康供应链。 |
| ADR-0059 人工接受 | `project/evidence/ADR-0059-approval.md` | 本次控制面提交 | 用户接受统一人员企业 SPA，并新增服务端分页、弹层任务边界和注意力保护硬规范。 |
| M6 企业产品基础重定标影响 | `project/evidence/WP-0014-enterprise-product-foundation-impact.md` | 本次控制面提交 | 代码图谱登记账户权限、模型供应链、分页和统一产品壳的当前差距及非局部影响；本切片不修改生产代码。 |
| M6 浏览器导航能力投影 | `project/evidence/WP-0014-browser-navigation-capability-projection.md` | 本次提交 | 已验证双范围会话的实际权限码经 `browser-session` 投影为导航能力；控制台只显示交集入口，服务端授权不变；角色绑定与账户管理仍未实施。 |
| M6 租户角色绑定与旧会话撤销 | `project/evidence/WP-0014-tenant-role-binding-session-revocation.md` | 本次提交 | 内置访问管理角色绑定、权限版本推进、Sa-Token 按成员撤销、真实 PostgreSQL 回滚与 token 派生 HTTP 闭环通过；账户、角色撤销和自定义角色仍未实施。 |
| M6 租户角色目录与自定义角色建立 | `project/evidence/WP-0014-tenant-role-directory-and-custom-role.md` | 本次提交 | 六个内置角色由领域目录投影，认证 SQL 不再硬编码角色权限；自定义角色建立、真实 PostgreSQL、受限 HTTP 与跨上下文 Flyway 版本门禁通过；成员治理和角色绑定仍待实施。 |
| M6 自定义角色绑定与权限会话投影 | `project/evidence/WP-0014-tenant-role-directory-and-custom-role.md` | 本次提交 | 同租户活动自定义角色以不可变绑定事实原子写入，成员权限版本推进并撤销旧租户会话；认证只投影活动角色权限，纯领域、应用、PostgreSQL、自动配置和 HTTP 回归通过。 |
| M6 租户成员目录与生命周期 | `project/evidence/WP-0014-tenant-member-directory-lifecycle.md` | 本次提交 | token 派生成员治理、无隐式权限建立、启用／停用、真实 PostgreSQL、HTTP、服务端分页控制台和本机浏览器弹窗回归通过；M6 仍进行中。 |
| M6 租户内置角色撤销与成员权限弹窗 | `project/evidence/WP-0014-tenant-role-revocation.md` | 本次提交 | 最后访问管理员失败关闭、权限修订、旧会话撤销、真实 PostgreSQL 回滚、受限 HTTP、服务端分页角色摘要和本机浏览器确认弹窗回归通过；M6 仍进行中。 |
| M6 租户厂商连接凭据生命周期 | `project/evidence/WP-0014-provider-connection-credential-lifecycle.md` | 本次提交 | API Key 仅写入、连接自有密文、轮换失效、真实 PostgreSQL 回滚、无秘密 HTTP、自动配置和服务端分页弹窗控制台通过；本机 HTTPS 入口健康，人工浏览器操作仍待可调用连接器，M6 仍进行中。 |
| M6 模型发现、部署与能力验证供应链 | `project/evidence/WP-0014-provider-model-deployment-supply-chain.md` | 本次提交 | 当前修订、未过期候选服务端分页，草稿、探测、`PT24H` 有效期和发布控制台闭环通过；容器 HTTPS 健康与未认证失败关闭已复核，真实浏览器角色任务仍待人工验收，M6 仍进行中。 |
| M6 自定义角色生命周期、目录与导航预览 | `project/evidence/WP-0014-tenant-custom-role-lifecycle-and-directory.md` | 本次提交 | 修改／停用／解绑、最后管理员保护、权限修订和旧会话撤销，真实 PostgreSQL、HTTP、自动配置、服务端分页和模态控制台均通过；ADR-0061 的全局账户治理仍未接受。 |
| M6 浏览器 TLS 前置条件复核 | `project/evidence/WP-0014-browser-tls-preflight.md` | 本次提交 | 浏览器在标准 TLS 校验下拒绝本地未受信任证书；不安装 CA、不绕过 HTTPS，正式浏览器角色证据待受信任部署环境续证。 |
| M6 控制台分页与注意力保护门禁 | `project/evidence/WP-0014-console-pagination-and-attention-guard.md` | 本次提交 | 模型配置／目录和查询审计改为 URL 可恢复的替换式服务端分页；模型配置写操作收敛为单一 Dialog，24 项架构门禁及 76 项前端回归通过；正式 TLS 浏览器证据待续证。 |
| M6 平台租户分页与受控操作 | `project/evidence/WP-0014-platform-tenant-pagination-dialog.md` | 本次提交 | 平台租户目录采用 URL 页码、服务端替换式摘要与建立／状态迁移确认 Dialog；77 项前端回归通过，正式 TLS 浏览器证据待续证。 |
| M6 受治理文件资源选择器分页 | `project/evidence/WP-0014-governed-file-resource-picker-pagination.md` | 本次提交 | 知识空间与文件来源选择器改为 URL 恢复的服务端分页，跨页来源状态失败关闭、写后重读；78 项前端回归通过。 |
| M6 结构化数据集资源选择器分页 | `project/evidence/WP-0014-structured-dataset-resource-picker-pagination.md` | 本次提交 | 知识空间选择器改为 URL 恢复的服务端分页，跨页任务状态失败关闭；24 项架构门禁和 79 项前端回归通过。 |
| M6 对象存储资源选择器分页 | `project/evidence/WP-0014-object-storage-resource-picker-pagination.md` | 本次提交 | 知识空间选择器改为 URL 恢复的服务端分页，跨页冻结范围状态失败关闭；24 项架构门禁和 80 项前端回归通过。 |
| M6 索引模型显式选择 | `project/evidence/WP-0014-index-model-explicit-selection.md` | 本次提交 | 三类数据任务不再隐式选择第一个嵌入模型，模型页码与选择标识进入 URL；24 项架构门禁和 80 项前端回归通过。 |
| M6 统一控制台查询入口退役 | `project/evidence/WP-0014-unified-console-query-retirement.md` | 本次提交 | `query-web` 独立入口、服务、代理和构建分支已退役；控制台 `/ask` 与查询运行时 API 代理保持，6 项部署门禁和唯一控制台镜像构建通过。 |
| M6 统一问答助手目录分页 | `project/evidence/WP-0014-query-assistant-catalog-pagination.md` | 本次提交 | `/ask` 的助手目录使用 URL 可恢复的服务器分页；第三页请求、类型、lint、81 项前端回归、唯一控制台构建与全量门禁通过。 |
| ADR-0061 人工接受 | `project/evidence/ADR-0061-approval.md` | 本次控制面提交 | 用户接受全局账户生命周期、内置平台角色和会话族撤销；不授权平台自定义角色、个人直接授权、服务账户、M7 或 M8。 |
| M6 平台账户目录与全范围会话撤销 | `project/evidence/WP-0014-platform-account-directory-session-revocation.md` | 本次提交 | 账户目录服务端分页、目标停用、主体租户／平台会话族撤销、真实 PostgreSQL 回滚、自动配置、HTTP 与统一控制台回归通过；正式 HTTPS 浏览器角色任务仍待受信任部署环境续证。 |
| M6 平台固定角色绑定治理 | `project/evidence/WP-0014-platform-role-binding-governance.md` | 本次提交 | 固定平台角色绑定／撤销、最后账户管理员保护、授权修订、全范围会话撤销、真实 PostgreSQL、HTTP 与统一控制台服务端分页 Dialog 回归通过；正式 HTTPS 浏览器角色任务仍待受信任部署环境续证。 |
| M6 平台账户启用与会话撤销 | `project/evidence/WP-0014-platform-account-enable.md` | 本次提交 | 禁用账户启用、重复启用拒绝、全范围会话撤销、真实 PostgreSQL 提交／回滚、HTTP 与统一控制台单一确认 Dialog 回归通过；正式 HTTPS 浏览器角色任务仍待受信任部署环境续证。 |
| M6 平台账户建立 | `project/evidence/WP-0014-platform-account-establishment.md` | 本次提交 | 受权初始口令摘要、严格 HTTP、数据库并发唯一登录名、真实 PostgreSQL、自动配置与统一控制台单一 Dialog 回归通过；正式 HTTPS 浏览器角色任务仍待受信任部署环境续证。 |
| M6 平台账户受控密码重置 | `project/evidence/WP-0014-platform-account-password-reset.md` | 本次提交 | 禁止自重置、PBKDF2 摘要、全范围会话撤销、真实 PostgreSQL 提交／回滚、HTTP 与统一控制台 Dialog 回归通过；正式 HTTPS 浏览器角色任务仍待受信任部署环境续证。 |
| M6 模型部署可用性投影 | `project/evidence/WP-0014-model-deployment-availability-projection.md` | 本次提交 | 当前连接修订、部署状态和验证有效期推导 `HEALTHY`／`DEGRADED`／`UNAVAILABLE` 与唯一建议下一步；真实 PostgreSQL、HTTP 分页和统一控制台回归通过，正式 HTTPS 浏览器角色任务仍待受信任部署环境续证。 |
| M6 模型部署不可逆停用 | `project/evidence/WP-0014-model-deployment-disable.md` | 本次提交 | `DISABLED` 生命周期、验证事实约束迁移、运行时失败关闭、真实 PostgreSQL、可信 HTTP 与统一控制台确认操作回归通过；正式 HTTPS 浏览器角色任务仍待受信任部署环境续证。 |
| M6 控制面自动验收修复 | `project/evidence/WP-0014-control-plane-automatic-acceptance-repair.md` | 本次提交 | 修复事务 CGLIB 代理与迁移后平台角色投影演示种子；真实 PostgreSQL、Redis、Flyway、Tomcat 的平台认证 21 项回归和全后端 Maven 反应堆通过；正式 HTTPS 浏览器角色任务仍待受信任部署环境续证。 |
| ADR-0062 租户历史直接权限退役 | `docs/adr/ADR-0062-tenant-direct-permission-retirement.md` | 本次控制面提交 | 已接受清单驱动、逐成员原子迁移；未提供清单的模型配置权限仍不得自动建角色或静默撤销。 |
| ADR-0062 人工接受 | `project/evidence/ADR-0062-approval.md` | 本次控制面提交 | 用户接受方案 A；逐成员角色映射和批准撤销仍是迁移运行输入，认证兼容读取与表结构删除未获授权。 |
| M6 历史直接权限迁移领域等价约束 | `project/evidence/WP-0014-tenant-direct-permission-migration-domain.md` | 本次提交 | 已以红绿测试固定迁移权限等价、批准撤销、既有角色保留和成员修订；清单驱动的 PostgreSQL、会话撤销、HTTP 与浏览器证据尚未完成。 |
| ADR-0063 本地身份基线与浏览器验收边界 | `docs/adr/ADR-0063-m6-local-identity-baseline-and-browser-evidence.md` | 本次控制面提交 | 仅本地 Compose 可清理历史成员并建立唯一平台超级管理员；受信任 TLS 不再阻断 M6，`localhost` 验收继续保留生产同等 cookie 属性。 |
| ADR-0063 人工接受 | `project/evidence/ADR-0063-approval.md` | 本次控制面提交 | 用户确认 Docker 已启动、禁止无必要新增容器，并授权本地成员基线重置与 M6 TLS 门禁调整。 |
| ADR-0064 本地空数据库生命周期认证切换 | `docs/adr/ADR-0064-local-empty-database-lifecycle-authentication-cutover.md` | 本次控制面提交 | 纯本地空库以隔离的 local Flyway repeatable migration 建立切换事实；已有租户时不写入，生产与共享环境保持 ADR-0046 语义。 |
| ADR-0064 人工接受 | `project/evidence/ADR-0064-approval.md` | 本次控制面提交 | 用户确认无历史迁移包袱，接受仅本地空库初始化路径；不改变公共认证语义。 |
| ADR-0065 平台模型目录管理与部署快照绑定 | `docs/adr/ADR-0065-platform-model-catalog-administration-and-deployment-snapshot.md` | 本次控制面提交 | 新增平台管理员固定角色；目录只可绑定已发布、可用部署的冻结能力快照，不能由浏览器提交 Endpoint 或模型能力。 |
| ADR-0065 人工接受 | `project/evidence/ADR-0065-approval.md` | 本次控制面提交 | 用户明确平台管理员拥有目录管理权限，目录条目显式绑定已发布部署的冻结能力快照。 |
| M6 平台管理员固定角色与导航投影 | `project/evidence/WP-0014-platform-administrator-role-projection.md` | 本次提交 | ADR-0065 的平台管理员、四项固定权限、PostgreSQL 角色约束、HTTP 会话撤销与统一 SPA 角色徽标均已回归；平台范围供应链仍待 ADR-0066 接受。 |
| M6 自动验收前置条件审计 | `project/evidence/M6-automatic-acceptance-precondition-audit.md` | 本次控制面提交 | ADR-0063 的平台身份基线与当前镜像回归已完成；ADR-0062 不适用于本地 M6。新租户登录被 ADR-0046 生命周期认证切换空白阻断，须先明确本地空历史库的激活方式。 |
| M6 本地身份重置只读预检 | `project/evidence/WP-0014-local-identity-reset-preflight.md` | 本次提交 | 精确确认当前 Compose 数据库、成员／主体数量、外键及旧凭据 `401`；人工确认清理范围与初始凭据前未执行删除。 |
| M6 本地身份基线与企业角色浏览器回归 | `project/evidence/WP-0014-local-identity-baseline-and-browser-regression.md` | 本次提交 | 本地关联数据清理、页面建立租户／账户／角色、真实 PostgreSQL 修订、平台运营员无数据工作区与管理员分页治理入口回归通过；新租户真实登录揭示生命周期认证切换尚未激活。 |
| M6 首份发布资源选择器分页与显式模型选择 | `project/evidence/WP-0014-publication-resource-picker-pagination.md` | 本次提交 | 首份发布路由、知识空间和嵌入模型选择已完成前端 TDD 与生产构建；当前 Docker Buildx 无法完成重建，真实 PostgreSQL／MinIO／HTTP 与受信任 TLS 浏览器回归待续证。 |
| M6 本地空库认证切换与模型供应链续证 | `project/evidence/WP-0014-local-empty-database-cutover-and-model-supply-chain.md` | 本次提交 | ADR-0064 local 切换、真实 PostgreSQL／HTTP／浏览器租户登录、角色导航、确定性供应商探测与发现均已续证；M6 自动验收与人工评审仍待完成。 |
| M6 平台运营员无数据工作区契约 | `project/evidence/WP-0014-local-empty-database-cutover-and-model-supply-chain.md` | 本次提交 | 修正过期 HTTP 断言：运营员只进入无数据平台工作区，不含租户治理或账户治理能力；真实本地 HTTP 与浏览器直达拒绝均通过。 |
| M6 平台范围模型供应链连接基础 | `project/evidence/WP-0014-platform-model-supply-connection-foundation.md` | 本次提交 | ADR-0066 的独立平台连接、密文和受限 HTTP／SPA 入口已完成；真实 Compose 装配和未认证失败关闭已复核，已登录浏览器与发现／部署仍待后续切片。 |
| M6 平台厂商连接探测 | `project/evidence/WP-0014-platform-provider-connection-probe.md` | 本次提交 | 平台专属探测契约、确定性扩展、真实 PostgreSQL、HTTP 平台上下文与单一 SPA Dialog 已回归；模型发现／部署及已登录浏览器续证仍待后续切片。 |
| M6 平台模型发现 | `project/evidence/WP-0014-platform-provider-model-discovery.md` | 本次提交 | ADR-0066 的独立平台发现端口、不可变发现事实、真实 PostgreSQL、HTTP 路由与统一 SPA 已回归；平台部署、目录快照和已登录浏览器续证仍待后续切片。 |
| M6 平台部署、当前候选与控制台闭环 | `project/evidence/WP-0014-platform-model-deployment.md` | 本次提交 | 平台专属当前候选分页、草稿／真实探测／发布／不可逆停用、真实 PostgreSQL 回滚、受限 HTTP、URL 恢复的统一 SPA 与本地 Compose 已回归；目录快照、租户消费和已登录浏览器续证仍待后续切片。 |
| M6 平台目录部署快照与当前可用性选择 | `project/evidence/WP-0014-platform-model-catalog-snapshot.md` | 本次提交 | 目录仅从已发布健康平台部署冻结事实，已删除自由 Endpoint／旧租户上下文旁路；创建／发布／运行时重验来源，选择器在 PostgreSQL 的同一分页／计数谓词排除修订漂移、到期和失效连接。Compose 同源 HTTP 失败关闭与当前浏览器登录边界已复核；已登录完整链路仍待续证。 |
| M6 企业产品基础真实回归 | `project/evidence/WP-0014-enterprise-foundation-browser-regression.md` | 本次提交 | 修复模型配置辅助读取隔离、工作器 Web 装配与受控凭据主密钥接线；真实 PostgreSQL／MinIO、HTTP 和统一 SPA 已完成平台目录到租户索引发布闭环，M6 仍进行中。 |
| M6 自动验收与人工评审请求 | `project/evidence/M6-automatic-acceptance-review.md` | 本次提交 | ADR-0057 至 ADR-0059 的账户权限、模型供应链和统一 SPA 自动验收均通过；真实 Compose、HTTP、浏览器与全量门禁已复核，当前仅待人工评审，不得进入 M7。 |
| M6 人工评审拒绝：登录页视觉与任务引导 | `project/evidence/M6-human-review-rejection.md` | 本次提交 | 人工拒绝当前登录页的企业产品质量；自动技术验收不关闭工作包，须完成登录页视觉整改并再次人工复核。 |
| M6 登录入口视觉与可用性整改设计 | `project/evidence/WP-0014-login-visual-remediation-design.md` | 本次提交 | 统一入口改为显式平台／租户目标选择，并通过最小请求组装模型保持原有双范围认证边界；TDD、前端构建和本地 HTTP 已通过，浏览器视觉复核待人工完成。 |
| M6 平台管理员干净基线与首次可操作入口 | `project/evidence/WP-0014-platform-administrator-initial-entry.md` | 本次提交 | 本地仅保留一个 `PLATFORM_ADMINISTRATOR`；运营员仍进入无数据工作区，管理员首次登录改为平台租户治理页，并以真实浏览器确认账户、租户与平台模型供应链菜单可见。 |
| ADR-0067 三角色产品边界人工接受 | `project/evidence/ADR-0067-approval.md` | 本次控制面提交 | 产品端到端只保留平台管理员、租户管理员和普通用户；旧平台角色与租户自定义角色按红绿切片退役。 |
| ADR-0068 运营投影与用户信息边界人工接受 | `project/evidence/ADR-0068-approval.md` | 本次控制面提交 | 接受两级运营总览、脱敏平台租户摘要、手动刷新、固定时间范围、名称选择器和禁止渲染后端信息。 |
| ADR-0069 本地固定平台管理员初始化凭据 | `project/evidence/ADR-0069-approval.md` | 本次控制面提交 | 纯本地空库固定初始化 `platform.root` 与公开开发口令；共享、测试、预发布和生产环境不得加载或复用。 |
| ADR-0070 平台供应商权威模型发现 | `docs/adr/ADR-0070-authoritative-platform-model-discovery.md`、`project/evidence/ADR-0070-approval.md` | 本次控制面提交 | 用户接受 OpenAI-compatible／火山方舟权威发现、推理 API Key 与 Ark AK/SK 用途分离、未知候选管理员确认及前端脱敏边界。 |
| ADR-0071 本地平台管理员完全重制 | `docs/adr/ADR-0071-local-platform-administrator-full-reset.md`、`project/evidence/ADR-0071-approval.md` | 本次控制面提交 | 用户授权备份后重建当前本地 PostgreSQL 并清空本地会话，固定初始化 `admin` 与公开开发口令；非本地环境和 MinIO 不在删除范围。 |
| ADR-0072 租户知识库所有权与工作区边界 | `docs/adr/ADR-0072-tenant-owned-knowledge-base-workspace.md`、`project/evidence/ADR-0072-approval.md` | 本次控制面提交 | 用户确认知识库只归属单一租户并由租户管理员维护；平台只治理基础设施与脱敏聚合，真实浏览器三角色验收为不可替代门禁。 |
| M6 三角色授权与能力投影 | `project/evidence/WP-0014-three-role-authorization-and-projection.md` | 本次提交 | 平台／租户角色收敛为三种，真实 PostgreSQL 迁移、会话能力、HTTP 和统一 SPA 回归通过；自定义角色入口退役，遗留实现待冗余接口审计切片清理。 |
| M6 API 冗余审计 | `docs/audits/WP-0014-m6-api-redundancy-audit.md` | `294c510da` | 自定义角色及绑定 HTTP 契约已从运行时和浏览器客户端退休；用量、审计、运营聚合和资源标识路径按独立职责保留。 |
| M6 OpenAI 风格企业产品重构自动回归 | `project/evidence/WP-0014-openai-style-enterprise-product-regression.md` | `d152e0747` | 三角色、两级运营聚合、名称选择器、统一 SPA、健康感知统计及部分失败恢复通过真实基础设施和 `make validate`；新增门禁阻断无标签内部标识编辑与审计响应泄露，登录后全流程及真实 200% 缩放待人工续证。 |
| M6 本地平台管理员固定凭据初始化 | `project/evidence/WP-0014-local-platform-administrator-fixed-initialization.md` | `e7ed97d77` | 纯本地空库原子初始化唯一 `platform.root` 和平台管理员绑定；固定开发口令 PBKDF2 匹配、真实 PostgreSQL、Flyway、HTTP 会话与全量门禁通过，Chrome 从零流程待人工确认填写口令。 |
| M6 平台界面连续性与租户入驻整改 | `project/evidence/WP-0014-platform-ui-continuity-and-tenant-onboarding.md` | 本次提交 | 排除平台管理员和停用账户候选，租户向导原地建立专用管理员，修复 Fluent Portal 整页遮蔽并重构统一壳与平台账户目录；真实 PostgreSQL、HTTP、Chrome 桌面回归通过，窄屏与 200% 人工复核待续证。 |
| M6 全 SPA 共享视觉基线与可恢复空库 | `project/evidence/WP-0014-complete-ui-redesign.md` | 本次提交 | 精确备份并重置本地数据库为唯一 `platform.root`；共享登录框架、产品壳、任务导航和语义令牌完成红绿重构，工程助手六维质量快照、真实基础设施、26 个前端测试文件共 128 项和生产构建通过。操作电脑主流程、窄屏、真实 200% 缩放与减少动态效果仍待 Mac 解锁后续证，M6 保持进行中。 |
| M6 操作电脑主流程与响应式续证 | `project/evidence/WP-0014-computer-use-primary-flow-and-responsive-regression.md` | 本次提交 | 从唯一平台管理员完成账户、活动租户、普通成员、确定性平台模型目录、租户模型配置、文本发布和 6 分块活动索引；物理 1024×768、真实 200%、键盘和 reduced-motion 通过。助手管理入口、首份文本恢复路径和物理 390×844 未闭环，M6 保持进行中。 |
| M6 角色目录、详情与界面边界整改 | `project/evidence/WP-0014-role-directory-details-and-ui-containment.md` | 本次提交 | 补齐三种固定角色目录、角色／平台账户详情和成员管理入口，修复模型表单半屏与登录失败溢出；权限编辑需新 ADR，真实 200% 错误密码组合和物理 390×844 仍待续证，M6 保持进行中。 |
| M6 平台治理契约与模型供应链错误恢复 | `project/evidence/WP-0014-platform-governance-contract-and-model-supply-recovery.md` | 本次提交 | 重复账户和模型目录稳定返回冲突，平台角色目录投影三种固定角色并纠正账户授权语义，退役无调用方平台工作区，改进发现反馈与统一宽表；真实 PostgreSQL、HTTP、1024×768、390×844 和全量门禁通过，200% 与 reduced-motion 仍待续证。 |
| M6 OpenAI-compatible 权威模型发现与管理员确认 | `project/evidence/WP-0014-authoritative-openai-model-discovery.md` | `63edeed87` | 显式发现协议、OpenAI Models API、本地真实 HTTP、未知能力不猜测、独立管理员确认审计、真实 PostgreSQL、受限 HTTP、前端全量测试与生产构建通过；方舟闭环见紧邻证据。 |
| M6 火山方舟权威模型发现与独立 AK/SK | `project/evidence/WP-0014-authoritative-volcengine-ark-model-discovery.md` | `7c7b70962` | 官方 ListEndpoints、SignerV4、推理 API Key 与发现 AK/SK 分槽密文、未知能力管理员确认、真实 PostgreSQL／HTTP／Compose 与前端门禁通过；Chrome 登录后响应式与无障碍续证待完成。 |
| M6 火山方舟发现性能 profile | `project/evidence/artifacts/WP-0014-ark-discovery-performance-profile.md` | `7c7b70962` | 实际两页本地 HTTP workload 通过，冻结 100 页、10000 候选、单响应 1 MiB 与 timeout 上界；不声明生产吞吐。 |
| M6 火山方舟发现固定 diff 评审 | `project/evidence/artifacts/WP-0014-ark-discovery-code-review.md` | `7c7b70962` | `63edeed87..7c7b70962` 的规范轴、需求轴及秘密扇出评审通过，未发现未解决源码缺陷；Chrome 人工门禁独立保留。 |
| M6 本地平台管理员与数据完全重制 | `project/evidence/WP-0014-local-platform-administrator-full-reset.md` | 本次提交 | PostgreSQL 可恢复备份、红绿 TDD、空库重建、Redis 旧会话清理、唯一 `admin`、新旧凭据 HTTP、Compose 全健康和全表零业务事实均已复核；MinIO 未删除。 |
| M6 平台权威模型发现 Chrome 与无障碍续证 | `project/evidence/WP-0014-authoritative-model-discovery-chrome-accessibility-regression.md` | `2d5b0135c` | 1024×768、390×844、真实 200% 缩放、键盘和 reduced-motion 通过；修复登录语义、Dialog 可达性与焦点恢复，真实 PostgreSQL、全量门禁和最终源码 Compose 七服务均通过。 |
| M6 模型发现阻断修复 | `project/evidence/WP-0014-model-discovery-blocker-remediation.md` | `d7cf86fdd` | Compose 传递受控 Endpoint 与超时，接入预设阻止协议错配；真实同源 HTTP 返回 130 个待管理员确认候选且无内部字段泄漏，旧修订安全 409 不含错误码、消息、追踪标识和详情。 |
| M6 企业组件库选型复核 | `project/evidence/WP-0014-enterprise-component-library-decision.md` | 本次提交 | 保留 Fluent UI v9 为唯一视觉系统，固定 7／6／6 设计参数，并以性能、包体、INP、响应式和无障碍量化门槛约束专项 Grid。 |
| M6 租户知识库所有权与一等工作区 | `project/evidence/WP-0014-tenant-owned-knowledge-base-workspace.md` | 本次控制面提交 | 知识库固定归属单一租户，平台会话和只读租户会话均无法管理；后端、真实 PostgreSQL／HTTP、前端全量测试、最终源码 Compose 已通过，真实 Chrome 三角色与响应式硬门禁因工作区软链接权限配置待续证。 |
| M6 全产品人工评审拒绝 | `project/evidence/M6-full-product-human-review-rejection-20260829.md` | 本次提交 | `HITL-0008-M6-REJECTION` 完整登记 19 项缺口；M6 与 WP-0014 保持进行中，M7 阻断，公共权限与生命周期整改须先有新 ADR。 |
| M6 租户成员候选内联 SQL 门禁与真实分页 | `project/evidence/WP-0014-tenant-member-candidate-inline-sql-guard.md` | `ceb946aa9` | Java 内联 SQL 架构门禁红绿、Mapper XML 迁移、真实 PostgreSQL 服务端分页、自动配置、完整上下文和 `make validate` 通过；权限互斥语义留待新 ADR。 |
| ADR-0073 租户管理员所有权与人员范围资格设计 | `docs/adr/ADR-0073-platform-owned-tenant-administrator-governance.md`、`project/evidence/WP-0014-tenant-administrator-ownership-domain-design.md` | 本次控制面提交 | 固定平台独占租户管理员生命周期、租户只治理普通成员，并以共同主体锁保护活动平台角色与启用租户成员资格互斥；生产实现由接受后的红绿切片完成。 |
| ADR-0073 人工接受 | `project/evidence/ADR-0073-approval.md` | 本次控制面提交 | 用户接受方案 A；授权共同主体锁下的双向范围互斥、平台拥有租户管理员和租户只治理普通成员进入红绿实现。 |
| M6 人员范围互斥与租户普通成员治理 | `project/evidence/WP-0014-personnel-scope-exclusivity-and-ordinary-member-governance.md` | `790db7b17` | 共同主体锁下的平台／租户活动范围双向互斥、租户只治理普通成员、升级预检、安全 HTTP、真实 PostgreSQL 并发和全量门禁通过；平台侧管理员生命周期 UI 与 Chrome 为下一切片。 |
| M6 平台租户管理员生命周期与三角色 Chrome 验收 | `project/evidence/WP-0014-platform-tenant-administrator-lifecycle.md` | `93c3a77fc` | 平台独占目录、候选、分配／提升、原子替换、停用／恢复、租户级并发归零保护与会话撤销通过领域、真实 PostgreSQL、HTTP、Compose 和 Chrome 三角色验收；390×844 溢出修复、真实 200%、键盘与 reduced-motion 规则续证完成。无已发布助手继续阻断全产品人工复验。 |

| WP-0015 控制面与架构基线 | project/evidence/WP-0015-control-plane-and-architecture-baseline.md | 22a23a612 | ADR 元数据生成决策索引、活动证据／ADR／恢复上下文硬上限、连续工作包和可信 RAG 架构债务基线已完成红绿与治理门禁；不包含生产前后端实现。 |

## WP-0016

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- | --- |
| OpenDesign 高保真原型候选 | `project/evidence/WP-0016-open-design-prototype-candidate.md` | `895757313` | OpenDesign 0.21.1 签名、绝对路径 MCP、项目导入和 P1 lint 通过；922 条浏览器断言、147 个页面状态组合及 15 张截图通过。人工确认尚未完成，WP-0016 保持进行中。 |
| OpenDesign 全平台高保真原型候选 | `project/evidence/WP-0016-open-design-full-platform-candidate.md` | `18d9acdd1` | 原型扩展为 68 个实际页面；3,929 条静态断言与 1,428 个 HTTP 场景通过，最终 P1 lint 无发现。真实 DOM、响应式、交互和 83 张截图因浏览器权限拒绝未执行，不得以旧 15 张截图替代；WP-0016 保持进行中。 |
| 原型人工评审拒绝 | `project/evidence/WP-0016-prototype-human-review-rejection-20260903.md` | 评审对象 `18d9acdd1` | 2026-09-03 明确不通过：说明过多、对齐与美观度不足。整改后须重新验证，WP-0016 保持进行中。 |
| 原型视觉整改候选 | `project/evidence/WP-0016-visual-remediation-candidate-20260903.md` | 待绑定候选提交 | 68 页采用 7 / 5 / 7 参数；4,028 条静态断言和 4 项补充回归通过；本轮浏览器未授权，新截图为 0，人工拒绝状态保留。 |

## 证据规则

- 命令证据记录完整命令、提交、环境和结果。
- 性能证据记录数据集、硬件、并发、预热和分位值。
- RAG 证据记录数据集版本、模型部署版本、索引版本和指标。
- 安全证据记录攻击场景和被保护的信息边界。
- 截图和录像只能补充自动证据，不能替代自动证据。
