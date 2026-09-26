# 仓库结构与能力归属

状态：已接受。依据：ADR-0002、0022、0059、0079、0081。以实际 POM、工作区 manifest 和 Compose 配置核对；不预建没有调用方的模块。

## 根目录

| 区域 | 职责 | 禁止 |
| --- | --- | --- |
| backend | Maven 聚合、六上下文、平台、共享内核、扩展及三个启动模块 | 用启动壳承载业务，恢复旧 runtimes/api/adapter 层 |
| frontend | 唯一 console-web SPA 与有实际调用方的共享包 | 再建人员登录、问答产品壳或复制原型模拟状态 |
| deploy | Compose、镜像、代理与环境装配 | 写入秘密、把本地部署等同 M8 |
| scripts | 质量、开发验收和运维执行；quality/baselines 是机器门禁资产 | 把运行日志当稳定规范 |
| docs | 目标、规范、架构、接受设计、任务恢复及历史定位 | 逐会话新增重复正文、截图、秘密 |
| project/fixtures | 合成参考验收数据及其清单；GOV-0002 接受路径迁移 | 存放真实用户数据、凭据或重复的旧路径副本 |

已验收 OpenDesign 项目的可执行原型资产保留在其不可改绑的导入根 `docs/prototypes/wp-0016/`；任务目录仅提供定位入口，不保存第二份原型源码。该目录不承载新的现行规范正文。

根 contracts 目录当前并不存在；公共契约由上下文接口、shared-contract、对应 ADR 及契约测试共同约束。若需要独立生成契约制品，以实际消费者和专属 ADR 建立，不凭旧目录树生成空文件。

## 后端和运行边界

backend 聚合 shared、platform、contexts、extensions、bootstrap。shared-kernel 为框架无关领域基础，shared-contract 为公共传输结构；platform-web/persistence/security 承载明确的技术边界。扩展按真实供应商或存储实现编译，不动态加载不受信任 JAR。

control-plane-bootstrap、query-bootstrap、ingestion-bootstrap 是三个可执行 Spring Boot 模块。Compose 对应 control-plane、query-runtime、ingestion-worker；bootstrap 主代码只保留启动类，技术装配由所属模块自动配置提供。运行配置和应用级集成测试归启动模块。

## 能力与所有权

| 能力 | 后端上下文及数据所有权 | 前端功能/共享边界 | 运行进程与验收 |
| --- | --- | --- | --- |
| 身份、租户、成员授权 | organization-access：SubjectIdentity、成员资格、角色、租户生命周期与配额事实 | session、platform-governance；browser-session 不拥有业务权限 | control-plane；AuthenticationApiTest、WP-0017 login/onboarding，后续 WP-0018 |
| 知识来源、产物、索引 | knowledge-data：来源修订、私有同步产物、知识空间和 IndexVersion | governed-data、knowledge-space、knowledge-publication | control-plane/ingestion-worker；WP-0020/0022 |
| 模型供应与冻结运行 | model-runtime：连接、部署、能力、路由及运行指纹 | 平台模型供应与租户模型功能 | 三进程按声明能力装配；WP-0019/0021 |
| 助手交付 | assistant-delivery：Assistant、AssistantVersion 与发布依赖 | 助手目录、草稿、测试、发布任务 | control-plane/query-runtime；WP-0023 |
| 可信问答 | trusted-query：查询运行、事件、引用、会话事实与权限重验 | query-workspace；未来会员旅程归 WP-0024 | query-runtime；WP-0024/0025/0026 |
| 评测和运营 | evaluation-operations：数据集、评测目标、门禁和运营读取 | 评测、诊断、审计功能 | control-plane 及共用执行端口；WP-0021/0025/0026 |

表中所有权与责任包不表示未来生产功能已经实现。当前完成事实只读任务和实现矩阵。跨上下文只调用已公开 application facade/Port，不读取对方表或内部包；迁移、事务和仓储由拥有该数据的上下文负责。

## 前端依赖

console-web 负责路由、外壳和 feature 组合；browser-session 负责会话恢复/失效边界；design-system 负责主题、通用控件和状态表达；query-workspace 负责既有查询体验。保留当前实际依赖，不因消费者数量直接删除已有包。功能内状态靠近 feature，公共 API 客户端及共享包变更必须追踪调用方。

目标导航、文案、布局、控件和响应式见 [产品与 UI](../design/product-and-ui.md)，不把历史路由树当新产品蓝图。

## 验证与变更

单元和上下文测试靠近源码；跨进程验收由 scripts/development 执行，运维演练由 scripts/operations 执行。治理和质量门禁独立，不以文档存在证明运行正确。

make project-verify / architecture-guard 为本次文档迁移门禁。业务修改按所属 WP 运行后端、前端、真实 HTTP/浏览器与 Compose 验收。源码、运行镜像和原型版本分别绑定。后续代码问题见 [风险与整改责任](../tasks/milestones/risks.md)。
