# WP-0003 文本数据源事务证据

## 证据边界

本证据只验收文本数据源的 PostgreSQL 事务、幂等、并发控制、租户隔离和事务事件，不代表对象存储、内容上传接口或控制台任务流已经完成。

- 实现提交：`8821c0b92758878eec954969ad5e5d857786d07a`
- 关联决策：`ADR-0014`
- 验证日期：2026-07-27
- 验证环境：macOS、JDK 26.0.2、Maven Wrapper 3.9.16、Docker Desktop、PostgreSQL 18.4 测试容器

## 红灯证据

```bash
./mvnw -pl backend/contexts/knowledge-data -am \
  -Dtest=PostgresTextDataSourceRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

首次执行因事务端口、持久化适配器、数据库映射器和异常类型尚不存在而编译失败。

```bash
./mvnw -q -pl backend/contexts/knowledge-data -am \
  -Dtest=TextDataSourceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

新增“内容就绪状态必须包含尚未发布的新版本”反例后，首次执行因领域模型未拒绝错误状态而失败。

## 绿灯证据

```bash
./mvnw -q -pl backend/contexts/knowledge-data -am \
  -Dtest=TextDataSourceTest,PostgresTextDataSourceRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
make backend-test
make project-verify
```

结果：

- 文本数据源表、不可变内容版本表、历史幂等结果表和事务事件表可从空数据库完成迁移。
- 创建操作使用租户事务锁，三个并发请求在配额为二时只有两个提交成功。
- 同一租户、操作和幂等键只产生一次事实；异参重放被拒绝。
- 历史重放返回原操作完成时的状态，不会被后续发布状态污染。
- 所有数据源读取和更新同时约束租户、知识空间和数据源标识。
- 内容关联和发布使用数据库行锁及乐观版本；两个并发关联只有一个成功。
- 并发失败方不产生内容版本、幂等成功记录或事务事件。
- 无内容发布和跨租户关联均失败，且事务完整回滚。
- 内容就绪、已发布状态关系同时受领域不变量与数据库检查约束保护。
- 全后端清理测试、真实 PostgreSQL 集成测试、架构规则、7 项治理测试和控制面一致性校验通过。

## 当前结论

文本数据源的数据库事实和事务边界已经可以承接对象存储写入后的内容引用。下一步必须先确定对象存储客户端的正式版本与安全调用规范，再实现“暂存、对象写入、数据库登记”应用编排和受控接口。
