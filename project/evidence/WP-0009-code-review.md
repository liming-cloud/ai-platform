# WP-0009 全量代码走查证据

## 1. 范围与方法

走查覆盖 WP-0009 的完整提交链，按正确性、复杂度、冗余、性能、架构和测试质量
六个维度检查，并重点复核租户隔离、异常转译、雪花标识、MyBatis-Plus 分页、
自动配置、启动角色隔离和容器验收脚本。

Codegraph 用于追踪 `CandidateSearchService`、`KnowledgeSpaceLookup`、活动索引、
模型运行时和自动配置链。`KnowledgeSpaceLookup` 的三层影响分析覆盖 72 个符号，
对应应用测试、自动配置测试、真实 PostgreSQL 隔离测试和 query-bootstrap 场景测试
均已执行。

## 2. 发现与处理

| 维度 | 发现 | 处理 |
| --- | --- | --- |
| 正确性 | 助手幂等记录没有由应用层提供雪花主键 | 补齐标识生成和数据库回归 |
| 安全 | 跨租户知识空间查询被错误归类为活动索引不可用，既不符合资源隐藏语义，也产生 503 | 增加租户内知识空间只读门面，先验证资源可见性，再解析活动索引；返回安全的资源不存在错误 |
| 安全 | Sa-Token 基础设施异常可能越过平台异常边界 | 在基础设施层转译并验证统一安全响应 |
| 性能 | 查询站点生产入口耦合全部 React 与 Fluent UI 代码 | 拆分稳定运行时资源块，应用入口降至 20.55 千字节 |
| 测试质量 | 配额验收脚本仍使用旧错误码 | 更新为统一错误码并验证原子限额和幂等重放 |
| 测试质量 | 查询运营脚本的旧游标分页分支在字段缺失时静默跳过 | 改为不可跳过的页码分页、总数、翻页无重复和旧字段缺失断言 |
| 架构 | Spring Modulith 包级命名接口与过宽的应用层框架规则冲突 | 仅豁免 `package-info` 元数据，应用类和领域类规则不放宽 |
| 工程性 | JDK 26 对 MyBatis 测试反射修改最终字段产生兼容提示 | 在测试 JVM 显式声明最终字段修改参数，生产启动参数不扩大 |

全部发现均已修复并经过定向测试、完整 `make validate` 和容器演练复验。最终没有
未修复的高优先级问题。

## 3. 结构核验

- Java MyBatis SQL 注解文件：0。
- Java UUID 文件：0。
- 旧 `api` 文件：0。
- 旧 `adapter` 文件：0。
- `backend/runtimes` 文件：0。
- 手工列表分页：0。
- 游标列表公共契约：0。
- Sa-Token 越界文件：0。
- 超预算包：0。
- 未登记复杂 SQL：0。

保留的两处 `LIMIT` 分别是事件流重放上限和单记录锁定，不属于列表查询，均有明确
允许分类。Wrapper、Page 和 IPage 均未进入 Controller、应用层、领域层或公共响应。

## 4. 能力与证据来源

- 活跃运行时：Codex。
- 能力来源：本地 `engineering-assistant` 插件。
- 使用并显式加载的能力：
  - `engineering-assistant:codebase-design`
  - `engineering-assistant:tdd`
  - `engineering-assistant:diagnosing-bugs`
  - `engineering-assistant:code-review`
- 使用的控制资料：仓库 `AGENTS.md`、代码走查安全边界资料、WP-0009、ADR-0022、
  架构债务门禁和复杂 SQL 允许清单。
- 本轮没有生成阶段运行结果，阶段结果校验器不适用。

