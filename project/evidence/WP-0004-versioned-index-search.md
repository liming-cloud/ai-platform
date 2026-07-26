# WP-0004：版本化索引与授权候选检索证据

## 结论

WP-0004 已形成可部署的纵向闭环：

`已发布文本 -> 冻结索引版本 -> 异步确定性分块 -> 嵌入 -> pgvector 写入 -> 原子发布 -> 授权候选检索`

门店与校园固定场景分别命中各自内容，校园租户查询门店知识空间返回空候选。
控制台把原来的“文本已发布”终点延长为“索引已发布且可检索”，没有新增顶级导航。

## 固定实现

- 索引内核提交：`05767095d`
- 完整纵向闭环提交：`d78047a9bb58137c6910dbcaac98ac606fbc4867`
- 分支：`codex/trusted-rag-foundation-v1`
- 生命周期决策：`docs/adr/ADR-0015-versioned-vector-index-lifecycle.md`
- 研究依据：`docs/research/WP-0004-indexing-baseline.md`
- 向量镜像：
  `pgvector/pgvector:0.8.5-pg18-trixie@sha256:9d2e61c7352b9e9f4798df5fd9a498f043f4cda1cdacc707de3d198650f4321e`

## 领域与工程证据

- `IndexVersion` 冻结来源快照摘要、分块策略、嵌入部署、修订和维度。
- `paragraph-window-v1` 对相同规范化文本产生稳定分块标识和顺序。
- 只有 `READY` 版本可以原子激活；失败构建不会替换旧活动版本。
- 工作进程从已发布数据库事实读取对象引用，再从私有 MinIO 读取并校验内容。
- 构建队列使用租约和幂等替换，同一版本重试不会积累重复向量。
- 查询正文不接收租户标识，租户只从 Sa-Token 服务端会话恢复。
- pgvector 查询显式包含租户、知识空间、活动索引、嵌入部署、修订和维度条件。
- 领域与公共契约不暴露 Spring AI、MyBatis-Plus、pgvector 或 Web 类型。

## 红绿验证

新增领域、应用、数据库和向量合同测试后，首先因索引状态、分块器、嵌入端口、
持久化队列和向量实现不存在而失败。实现后以下分层验证通过：

```bash
./mvnw -q -pl backend/extensions/vector-stores/vector-pgvector -am test
./mvnw -q -pl backend/runtimes/query-runtime -am \
  -Dtest=CandidateScenarioTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -q test
```

全量 Maven 反应堆统计为 108 项测试，失败 0、错误 0、跳过 0。

## 容器故障与关闭证据

第一次真实容器验收暴露了两个仅靠单元测试不能发现的装配问题：

1. 摄取调度器以“目标 Bean 已存在”为条件，受配置解析顺序影响没有注册，
   待处理任务一直停留在数据库。
2. 在线查询控制器和可信上下文解析器使用同类条件，进程健康但查询端点返回 404。

修复后改用与各运行时一致的显式功能开关。重启工作进程时，它自动领取此前积压任务，
并把该版本构建为 6 个分块的 `READY` 索引，未手工改写任务或索引状态。

## 场景、权限与容器验收

```bash
make deploy-config
make deploy-smoke
make acceptance-index-search
```

验收结果：

- 五个应用入口健康。
- 门店与校园各有一个活动索引。
- 两类自然语言查询均命中各自固定答案要点。
- 校园会话携带门店知识空间标识查询时返回空候选。
- 数据库向量分块数量大于零，活动索引数量符合两个固定场景。
- 控制面、查询进程和摄取进程均使用 `10001:10001`、只读根文件系统、
  禁止权限提升并移除全部 Linux 能力。

固定镜像摘要：

- 控制面：`sha256:640b78e087be8009199169081bfdd1f466a246675db88926015466e0f27928bc`
- 在线查询：`sha256:e14af2f3cdc9d18f88e6ddd53ef1c87b259d8d62584457f6477c168d1d3a34eb`
- 摄取进程：`sha256:7750267812d98d9601d97ee50d914f3c7aa3455945932fc387f8acacdbceb41e`
- 控制台：`sha256:c59c92a4746c4ef5405d91c823559e6e56b1a4cf40021eea6c584cba96983f9e`

## 性能冒烟

执行：

```bash
make performance-index-search
```

当前本机 Docker 环境先预热 20 次，再顺序采样 100 次授权候选检索：

- 中位数：0.004807 秒。
- 95 分位：0.006969 秒。
- 最大值：0.025173 秒。
- 95 分位门禁：不高于 0.200 秒。

该结果只证明 M1 小数据集和本机容器的性能冒烟，不替代后续并发、容量和生产硬件报告。

## 前端实机检查

在 `http://127.0.0.1:13000/` 检查默认桌面视口和 390 × 844 窄屏视口：

- 五个业务步骤完整呈现，没有增加顶级导航或同质卡片。
- 桌面端保持进度与单一任务双栏结构。
- 窄屏自动变为单列，页面正文无横向溢出。
- 登录后只列出当前租户的知识空间。
- 构建中、就绪、失败、发布和完成文案区分服务端真实状态。
- 浏览器检查只建立短期会话，没有创建或修改业务资源。

## 全量门禁

```bash
make validate
make deploy-config
make deploy-smoke
make acceptance-index-search
make performance-index-search
git diff --check
bash -n scripts/development/acceptance-index-search.sh
bash -n scripts/development/performance-index-search.sh
```

结果：

- 项目治理 7 项测试和控制面验证通过。
- 后端 108 项测试全部通过。
- 前端 14 项测试、类型检查、代码检查和双应用生产构建通过。
- 演示数据 5 项测试、2 个场景和 8 个检索用例通过。
- Docker 配置、健康检查、双租户验收和性能冒烟通过。

## 遗留边界

本工作包只交付候选检索，不生成大模型答案，不承诺引用文案、重排、
混合检索或真实厂商语义质量。这些能力进入 WP-0005，不能把确定性嵌入结果
包装成已经完成的企业 RAG 回答。
