# 决策索引

| 编号 | 状态 | 决策 | ADR | 里程碑 |
| --- | --- | --- | --- | --- |
| ADR-0001 | 已接受 | 绿地重建，不兼容和迁移旧系统 | `docs/adr/ADR-0001-greenfield-replacement.md` | M0-M8 |
| ADR-0002 | 已接受 | 使用 DDD 模块和三个运行进程，暂不拆分微服务 | `docs/adr/ADR-0002-modular-foundation-runtime.md` | M0-M8 |
| ADR-0003 | 已接受 | 使用能力描述的模型部署和 Spring AI 适配器 | `docs/adr/ADR-0003-capability-driven-model-runtime.md` | M0-M5 |
| ADR-0004 | 已接受 | Git 仓库控制面作为跨会话事实来源 | `docs/adr/ADR-0004-repository-project-control-plane.md` | M0-M8 |
| ADR-0005 | 已接受 | 使用任务导向的 Fluent UI v9 和页面复杂度预算 | `docs/adr/ADR-0005-task-first-fluent-ui.md` | M0-M8 |
| ADR-0006 | 已接受 | 固定正式 GA 技术基线，禁止预览依赖 | `docs/adr/ADR-0006-stable-ga-technology-baseline.md` | M0-M8 |
| ADR-0007 | 已接受 | 在设计分支删除可由 Git 恢复的旧实现，只保留绿地控制面与设计 | `docs/adr/ADR-0007-clean-design-branch.md` | M0 |
| ADR-0008 | 已接受 | 使用限界上下文单模块、独立扩展、三运行入口和多前端应用的详细目录 | `docs/adr/ADR-0008-scalable-repository-layout.md` | M0-M8 |
| ADR-0009 | 已接受 | Java 根命名空间使用 `com.aiplatform.foundation` | `docs/adr/ADR-0009-java-namespace.md` | M1-M8 |
| ADR-0010 | 已接受 | 统一模型供应商密钥生命周期和出站访问边界 | `docs/adr/ADR-0010-secret-and-egress-boundary.md` | M1-M8 |
| ADR-0011 | 已接受 | 租户来自可信会话，持久化使用显式租户条件和纵深防御 | `docs/adr/ADR-0011-tenant-auth-and-persistence-boundary.md` | M1-M8 |
| ADR-0012 | 已接受 | 人员密码使用可升级的 PBKDF2-HMAC-SHA256 摘要和拒绝上限 | `docs/adr/ADR-0012-password-hashing-boundary.md` | M1-M8 |
| ADR-0013 | 已接受 | 知识空间的租户、幂等、配额和插入使用可信上下文与单一数据库事务 | `docs/adr/ADR-0013-knowledge-space-transaction-boundary.md` | M1-M8 |
| ADR-0014 | 已接受 | 文本内容经受限暂存和内容寻址对象写入后登记，并以乐观锁发布 | `docs/adr/ADR-0014-text-content-object-publication.md` | M1-M8 |
| ADR-0015 | 已接受 | 索引版本冻结来源、分块和嵌入配置，异步构建并原子激活，M1 使用精确向量检索 | `docs/adr/ADR-0015-versioned-vector-index-lifecycle.md` | M1-M8 |
| ADR-0016 | 已接受 | 按配置修订构建动态模型运行时，以不可变助手版本和平台后校验生成可信引用回答 | `docs/adr/ADR-0016-model-runtime-assistant-and-trusted-answer.md` | M1-M8 |
| ADR-0017 | 已接受 | 查询渠道只读取最新已发布助手版本，浏览器使用内存会话和可取消的事件续传 | `docs/adr/ADR-0017-query-channel-catalog-and-browser-state.md` | M1-M8 |
| ADR-0018 | 已接受 | 查询接受时原子占用演示配额，运营接口只读取脱敏查询事实 | `docs/adr/ADR-0018-demo-query-quota-and-operations-projection.md` | M1-M8 |
| ADR-0019 | 已接受 | 在授权向量候选池内执行查询感知的混合重排 | `docs/adr/ADR-0019-query-aware-candidate-selection.md` | M1-M4 |
| ADR-0020 | 已接受 | PostgreSQL 与 MinIO 组成业务备份集，Redis 会话不恢复，并在独立 Docker 项目中验收恢复 | `docs/adr/ADR-0020-demo-backup-and-isolated-recovery.md` | M1 |

## 已确认人工决策

- `HITL-0001-M0-DESIGN-APPROVAL`：产品负责人已批准 M0 设计，批准基线与范围见 `project/evidence/M0-design-approval.md`。
