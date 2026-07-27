# WP-0009 容器交付与运维演练证据

## 1. 结论

经典四层、页码分页、自动配置和三个可执行 bootstrap 收口后，重新执行了空环境交付、
固定评测、隔离恢复、容量与故障恢复以及既有卷升级演练。所有报告均通过且不含机密。

## 2. 演练结果

| 演练 | 结果 |
| --- | --- |
| `make deploy-smoke` | 八个服务健康，五个应用入口通过 |
| `make evaluation-demo` | 8 个固定用例、2 个跨租户用例和 2 个拒答用例通过 |
| `make recovery-demo` | 备份 4 秒，隔离恢复 18 秒；数据库事实、2 个对象、空 Redis、重新登录和租户隔离通过 |
| `make capacity-resilience-demo` | 双租户总并发 8，40 次样本零错误；95 分位 114.933 毫秒，每秒 114.563 次 |
| 故障恢复 | PostgreSQL、MinIO、查询进程和摄取进程停止及恢复通过，恢复后查询通过 |
| `make delivery-demo` | 空业务环境部署 21 秒、验收 37 秒、总计 61 秒；完整双场景闭环通过 |
| `make upgrade-demo` | 既有卷升级 50 秒；升级前后事实摘要和 14 条迁移记录一致 |

评测指标：

- 答案要点覆盖率：1。
- 引用完整率：1。
- 预期拒答率：1。
- 目标命中率：1。
- 跨租户泄漏数：0。

## 3. 可复核报告

- `project/evidence/artifacts/WP-0007-delivery-demo.json`
- `project/evidence/artifacts/WP-0007-recovery-drill.json`
- `project/evidence/artifacts/WP-0007-capacity-resilience.json`
- `project/evidence/artifacts/WP-0007-upgrade-drill.json`

报告沿用 WP-0007 的稳定机器可读文件名，因为这些演练同时是 M1 交付验收输入。
WP-0009 通过本证据索引引用它们，不复制或伪造第二份报告。

## 4. 失败关闭记录

- 首次使用旧演示卷启动时，Flyway 正确拒绝了已重写绿地迁移的校验和不一致。
  本项目明确不迁移旧数据，因此没有执行 `repair`；仅删除经 Compose 标签确认的
  三个演示卷后，以全新数据库、Redis 和对象存储重新部署。
- 首次交付演练发现配额脚本仍断言旧错误码，已改为
  `TRUSTED_QUERY.QUOTA.0001`。
- 代码走查发现查询运营脚本仍保留可被静默跳过的旧游标分页分支，已改为强制验证
  页码、页大小、总数、总页数、前后页标志、第二页无重复，并断言不再返回
  `nextCursor`。

删除的仅为 `trusted-rag-foundation-v1` Compose 项目下、经过标签核验的旧演示
PostgreSQL、Redis 和 MinIO 卷。该演示数据不可恢复；当前绿地演示环境已重新生成并
保持健康。各隔离演练环境及其卷已在成功后清理。

