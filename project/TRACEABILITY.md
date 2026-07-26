# 目标追踪矩阵

| 目标 | 用户任务 | 领域能力 | API 或页面 | 工作包 | 验证 | 证据 |
| --- | --- | --- | --- | --- | --- | --- |
| G1 企业知识受控发布 | 以租户管理员身份创建知识空间并发布文本数据 | 组织与权限、知识与数据源 | `/api/v1/auth/sessions`、`/api/v1/knowledge-spaces`、`/api/v1/data-sources`、数据流程 | WP-0001、WP-0003 | 权限反例、真实事务、幂等、对象存储和浏览器任务流 | `project/evidence/WP-0003-minio-publication.md` |
| G2 多厂商模型 | 配置并探测模型部署 | 模型目录与运行时 | `/api/v1/model-providers`、模型治理流程 | WP-0001、WP-0002、WP-0005 | 厂商契约、密钥测试 | `project/evidence/WP-0002-provider-security.md` |
| G3 可信回答 | 登录、选择已发布助手、提问并查看可追溯回答 | 助手交付、可信查询运行时 | `/api/v1/query-assistants`、`/api/v1/query-runs`、查询站点 | WP-0001、WP-0004、WP-0005 | 目录隔离、浏览器身份状态、检索、引用、拒答和事件续传测试 | `project/evidence/WP-0005-query-event-stream.md`，查询站点证据待产生 |
| G4 安全交付 | 将助手发布到网页、API 或设备 | 助手与交付 | `/api/v1/assistants`、助手流程 | WP-0001、WP-0005 | 角色、配额、设备凭据测试 | M1 待产生 |
| G5 可度量质量 | 评测并发布助手版本 | 评测与运营 | `/api/v1/evaluation-runs`、评测流程 | WP-0001、WP-0006 | 黄金集、发布门禁 | M1 待产生 |
| G6 可运维产品 | 部署、观测、备份和恢复 | 运行时运维 | Docker、运维页面 | WP-0001、WP-0007 | 冒烟、恢复、性能测试 | M1 待产生 |
| G7 跨会话连续性 | 不依赖聊天记录恢复任务 | 项目控制面 | `make resume-context` | WP-0001 | `make project-verify` | `project/evidence/INDEX.md` |
| G8 绿地事实边界 | 评审时只看到新基座设计 | 项目治理 | 根目录、设计文档和 Git 历史 | WP-0001 | 旧实现清单和受控文件检查 | `project/evidence/INDEX.md#wp-0001` |
| G9 可扩展工程边界 | 新增厂商、连接器、渠道或界面时局部修改 | 模块与扩展治理 | `foundation-repository-layout.md` | WP-0001、WP-0002 | 依赖、目录、契约和装配架构测试 | M1 待产生 |

每个实施工作包在激活前必须新增或细化至少一行。
