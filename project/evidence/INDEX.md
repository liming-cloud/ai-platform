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

## 证据规则

- 命令证据记录完整命令、提交、环境和结果。
- 性能证据记录数据集、硬件、并发、预热和分位值。
- RAG 证据记录数据集版本、模型部署版本、索引版本和指标。
- 安全证据记录攻击场景和被保护的信息边界。
- 截图和录像只能补充自动证据，不能替代自动证据。
