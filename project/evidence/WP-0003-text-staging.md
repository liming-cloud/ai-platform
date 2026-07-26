# WP-0003 文本数据源与暂存边界证据

## 证据边界

本证据只验收文本数据源领域状态机和本地流式暂存，不代表 MinIO 对象写入、PostgreSQL 数据源事务、发布接口或控制台任务流已经完成。

- 实现提交：`d66a78e761c461db2c57434ed6607c569b462e5a`
- 关联决策：`ADR-0014`
- 验证日期：2026-07-27
- 验证环境：macOS、JDK 26.0.2、Maven Wrapper 3.9.16、Docker Desktop

## 红灯证据

```bash
./mvnw -q -pl backend/contexts/knowledge-data -am test \
  -Dtest=TextDataSourceTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

首次执行因数据源标识、状态、内容引用和文本数据源聚合不存在而编译失败。

```bash
./mvnw -q -pl backend/contexts/knowledge-data -am test \
  -Dtest=LocalTextContentStagingAdapterTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

首次执行因暂存端口、稳定内容异常和本地暂存适配器不存在而编译失败。

## 绿灯证据

```bash
./mvnw -q -pl backend/contexts/knowledge-data -am test
make backend-test
make project-verify
```

结果：

- 新建文本数据源处于无内容草稿，领域版本和内容版本均为零。
- 只有连续内容版本可以进入内容就绪状态。
- 无内容数据源不能发布；发布后固化当前内容版本。
- 已发布数据源上传新内容后重新进入内容就绪状态，旧发布版本保持不变。
- 有效 UTF-8 内容按流写入临时文件并计算稳定 SHA-256，不把完整原文保存在堆内。
- 空内容、超过配置上限和畸形 UTF-8 均失败关闭。
- 成功关闭或失败时均清理暂存文件。
- 全后端清理测试、知识模块架构规则、7 项项目治理测试和控制面一致性校验通过。

## 当前结论

文本数据源领域和安全暂存边界已经可供 PostgreSQL 事务与对象存储适配器使用。MinIO Java 客户端的选择和调用证据仍需按照工程研究批准模式查阅官方资料，当前未把未经验证的客户端假设写入代码。
