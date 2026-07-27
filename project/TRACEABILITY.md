# 目标追踪矩阵

| 目标 | 用户任务 | 领域能力 | API 或页面 | 工作包 | 验证 | 证据 |
| --- | --- | --- | --- | --- | --- | --- |
| G1 企业知识受控发布 | 以租户管理员身份创建知识空间并发布文本数据 | 组织与权限、知识与数据源 | `/api/v1/auth/sessions`、`/api/v1/knowledge-spaces`、`/api/v1/data-sources`、数据流程 | WP-0001、WP-0003 | 权限反例、真实事务、幂等、对象存储和浏览器任务流 | `project/evidence/WP-0003-minio-publication.md` |
| G2 多厂商模型 | 配置并探测模型部署 | 模型目录与运行时 | `/api/v1/model-providers`、模型治理流程 | WP-0001、WP-0002、WP-0005 | 厂商契约、密钥测试 | `project/evidence/WP-0002-provider-security.md` |
| G3 可信回答 | 登录、选择已发布助手、提问并查看可追溯回答 | 助手交付、可信查询运行时 | `/api/v1/query-assistants`、`/api/v1/query-runs`、查询站点 | WP-0001、WP-0004、WP-0005、WP-0006 | 目录隔离、浏览器身份状态、检索、重排、引用、拒答和事件续传测试 | `project/evidence/WP-0005-query-event-stream.md`、`project/evidence/WP-0005-query-web-flow.md`、`project/evidence/WP-0006-demo-evaluation.md`、`project/evidence/WP-0006-browser-acceptance.md` |
| G4 安全交付 | 将助手发布到网页、API 或设备 | 助手与交付 | `/api/v1/assistants`、助手流程 | WP-0001、WP-0005 | 角色、配额、设备凭据测试 | M1 待产生 |
| G5 可度量质量 | 运行固定评测并阻止质量回归 | 评测与运营 | `make evaluation-demo`、脱敏评测报告 | WP-0001、WP-0006 | 固定集、目标命中、引用完整、答案要点、跨租户和拒答门禁 | `project/evidence/WP-0006-demo-evaluation.md` |
| G10 可解释用量治理 | 租户管理员查看查询用量、限额和脱敏审计事实 | 可信查询、评测与运营 | `/api/v1/usage`、`/api/v1/audit-records`、治理流程 | WP-0006、WP-0009 | 原子配额、幂等重放、权限、租户隔离、页码总数和控制台状态 | `project/evidence/WP-0006-query-quota.md`；WP-0009 分页证据待产生 |
| G6 可运维产品 | 从空环境部署、在隔离环境恢复并完成门店与校园演示 | 运行时运维 | Docker、运行手册、备份恢复与演示脚本 | WP-0001、WP-0007、WP-0008 | 空环境启动、五入口冒烟、隔离恢复、故障演练、容量与最终演示验收 | 历史证据已保留；架构整改后必须重新生成 M1 技术验收 |
| G7 跨会话连续性 | 不依赖聊天记录恢复任务 | 项目控制面 | `make resume-context` | WP-0001 | `make project-verify` | `project/evidence/INDEX.md` |
| G8 绿地事实边界 | 评审时只看到新基座设计 | 项目治理 | 根目录、设计文档和 Git 历史 | WP-0001 | 旧实现清单和受控文件检查 | `project/evidence/INDEX.md#wp-0001` |
| G9 可扩展工程边界 | 新增厂商、连接器、渠道或界面时局部修改 | 模块与扩展治理 | `foundation-repository-layout.md` | WP-0001、WP-0002、WP-0008、WP-0009 | 经典四层、公开门面、自动装配、错误码、分页、SQL、包规模和单层启动门禁 | `project/evidence/WP-0008-final-acceptance.md`；WP-0009 实现证据待产生 |

每个实施工作包在激活前必须新增或细化至少一行。
