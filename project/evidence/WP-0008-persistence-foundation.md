# WP-0008 平台持久化与雪花标识基座证据

## 结论

提交 `29e9cc098cb03582ee1ad89e409160eb97fe8fd9` 建立了框架无关的实体标识端口、
统一的 MyBatis-Plus 雪花标识实现和平台持久化自动配置。该提交通过全量后端回归与
新增架构门禁，但只完成 WP-0008 的第一个实施切片，不代表八项架构债务已经清零。

## 实现边界

- `platform-kernel` 仅声明 `EntityIdGenerator`，不依赖 MyBatis-Plus。
- `platform-persistence` 同时向领域应用和 MyBatis-Plus 提供同一个雪花生成器实例。
- 工作节点和数据中心编号必须显式配置，范围均为 0 至 31。
- MyBatis-Plus 拦截器顺序固定为乐观锁、PostgreSQL 分页、危险全表操作阻断。
- 显式引入 MyBatis-Plus 分页插件所需的 `mybatis-plus-jsqlparser`。
- 架构门禁禁止新增注解 SQL、UUID、运行时业务类、Sa-Token 越界、平铺包和
  `infrastructure` 目录。

租户拦截器暂未加入统一配置。只有在可信租户上下文迁入安全平台并具备真实 PostgreSQL
隔离测试后才允许启用，避免用不可信请求参数构造租户条件。

## 红绿验证

先编写失败测试，再补实现：

- 缺少雪花生成器时，标识唯一性测试编译失败。
- 缺少自动配置时，配置装配测试编译失败。
- 缺少 `mybatis-plus-jsqlparser` 时，分页拦截器测试因类缺失失败。
- 缺少架构门禁脚本时，七项门禁测试加载失败。

补齐实现和显式依赖后，以上测试全部通过。

## 已执行命令

| 命令 | 结果 |
| --- | --- |
| `./mvnw -q -pl backend/platform/platform-persistence -am test` | 通过 |
| `make backend-test` | 通过，216 项测试，0 失败、0 错误、0 跳过 |
| `make architecture-guard` | 7 项门禁单元测试通过，债务基线比较通过 |
| `make project-verify` | 通过，8 个工作包，当前前沿为 WP-0008 |
| `git diff --check` | 通过 |

执行环境为 macOS、JDK 26.0.2、Maven Wrapper 3.9.16。

## 当前债务基线

| 类别 | 数量 | 本切片结果 |
| --- | ---: | --- |
| 含 SQL 注解的 Java 文件 | 8 | 禁止新增，待逐个迁移 XML |
| 主代码 UUID 文件 | 122 | 禁止新增，待按上下文迁移雪花标识 |
| 运行时非启动 Java 文件 | 47 | 禁止新增，待迁入上下文适配器或启动装配模块 |
| Sa-Token 越界文件 | 3 | 禁止新增，待迁入专用适配模块 |
| 超过目录预算的包 | 8 | 禁止增长，待按聚合和用例拆分 |

机器可读清单位于 `project/architecture-debt/WP-0008-backend.json`。WP-0008 完成前，
以上五类计数必须全部归零，不能把“基线内违规”解释为合格。

## 下一步

迁移组织权限纵向切片：先强化租户、成员、会话和可信上下文领域语义，再把其标识改为
雪花长整型、把定制 SQL 移入 Mapper XML，并以真实 PostgreSQL 和权限反例验证。
