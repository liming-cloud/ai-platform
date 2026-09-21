# M1 演示环境备份与隔离恢复运行手册

## 目的

本手册用于证明 M1 单机 Docker 演示环境可以生成一致备份，并在不复用源容器、
网络、卷和端口的隔离环境中恢复。它不构成生产级灾备方案。

恢复契约由 `docs/architecture/decisions/ADR-0020-demo-backup-and-isolated-recovery.md` 固定。

## 数据保护边界

| 状态 | 事实语义 | 是否备份 | 恢复行为 |
| --- | --- | --- | --- |
| PostgreSQL | 领域、权限、幂等、索引、模型、助手、查询、配额和用量事实 | 是 | 完整逻辑恢复 |
| MinIO | 受治理原始文本及其摘要元数据 | 是 | 按数据库内容引用恢复并逐对象校验 |
| Redis | 一小时认证会话 | 否 | 创建空实例，所有旧会话失效 |
| 模型运行时缓存 | 可重建基础设施对象 | 否 | 应用启动后按需重建 |
| `/tmp` 暂存内容 | 未提交的短期上传文件 | 否 | 不恢复 |
| 容器文件系统 | 不可变运行产物 | 否 | 按镜像摘要重新创建 |
| 部署密钥 | 数据库、Redis、MinIO 和模型密钥值 | 否 | 从独立环境配置重新注入 |

完整备份包含企业原文、密码摘要、回答和引用，是机密运行产物：

- 只能写入仓库外的绝对临时目录。
- 目录权限必须为 `0700`，文件权限必须为 `0600`。
- 不得提交 Git、附加到普通任务或写入日志。
- 可提交证据只能包含不可逆摘要、计数、耗时和真假验证结果。

## 前置条件

- 当前分支为 `codex/trusted-rag-foundation-v1`。
- 源环境使用 `deploy/compose/.env.example` 启动，八个服务均为健康状态。
- 本机可执行 Docker、`curl`、`jq`、`shasum` 和 Bash。
- 恢复端口 `28080`、`28081`、`28082`、`23000`、`23001` 未被占用。
- 恢复目标不存在同名网络或卷。

先执行：

```bash
make project-status
make deploy-smoke
make recovery-operations-test
```

## 一键恢复演练

标准入口：

```bash
make recovery-demo
```

该命令按以下顺序执行：

1. 在系统临时目录创建权限受限的随机备份目录。
2. 确认源环境运行八个服务。
3. 停止五个应用入口，保持 PostgreSQL、Redis 和 MinIO 运行。
4. 使用 PostgreSQL 18 镜像内 `pg_dump` 生成自定义格式归档。
5. 从数据库生成内容引用清单，下载每个受引用对象并校验大小和 SHA-256。
6. 立即恢复源应用并检查五个应用入口。
7. 创建带 `trusted-rag-foundation-v1-recovery-` 前缀的独立项目、网络和三个卷。
8. 恢复 PostgreSQL、MinIO 对象及 `sha256` 用户元数据，确认 Redis 键数为零。
9. 启动隔离环境的八个服务。
10. 比较恢复前后表级事实计数，逐对象复核摘要和元数据。
11. 让门店和校园管理员重新登录，验证助手目录互相不可见，并读取用量和脱敏审计。
12. 检查源环境仍然健康。
13. 显式删除隔离恢复环境及其三个卷。
14. 将脱敏报告写入
    `/private/tmp/ai-platform-operations/WP-0007-recovery-drill.json`，删除临时完整备份。

任何一步失败都会返回非零退出码。失败时源应用由退出陷阱恢复；已经创建的隔离环境
由演练退出陷阱按受控项目名清理。

## 分步操作

只有需要保留隔离环境排查时才使用分步命令。

创建仓库外空目录：

```bash
backup_directory="$(mktemp -d /tmp/trusted-rag-foundation-v1-backup.manual.XXXXXX)"
chmod 700 "${backup_directory}"
```

生成备份：

```bash
scripts/operations/demo-backup.sh "${backup_directory}"
```

选择符合固定前缀的恢复项目名：

```bash
recovery_name="trusted-rag-foundation-v1-recovery-manual"
```

恢复并启动隔离环境：

```bash
scripts/operations/demo-restore.sh "${backup_directory}" "${recovery_name}"
```

验证恢复：

```bash
scripts/operations/demo-recovery-verify.sh "${backup_directory}" "${recovery_name}"
```

显式清理：

```bash
scripts/operations/demo-recovery-cleanup.sh "${recovery_name}"
```

清理完整备份前，必须再次确认变量指向受控临时目录：

```bash
printf '%s\n' "${backup_directory}"
```

本手册不提供通用递归删除命令。人工操作人员应通过受控文件管理流程销毁机密备份。

## 成功判据

- PostgreSQL 归档能被 `pg_restore --list` 读取。
- 数据库内容引用的每个对象都有相同大小、SHA-256 和 MinIO 用户元数据。
- 恢复前后表级事实计数与不可逆摘要一致。
- 恢复应用启动前 Redis 键数为零。
- 门店与校园管理员可以重新登录，助手目录互相不可见。
- 用量和审计接口只返回当前租户脱敏事实。
- 源环境和隔离环境的五个应用入口全部健康。
- 隔离恢复环境清理后，源网络和三个源卷仍存在且源环境健康。
- 恢复时间不超过 3600 秒。

## 故障处置

### 源环境不是八个运行服务

停止备份，不尝试创建或修复数据。先运行：

```bash
docker compose \
  --env-file deploy/compose/.env.example \
  -f deploy/compose/compose.yml \
  ps
```

根据不健康服务日志处理后重新执行 `make deploy-smoke`。

### 数据库归档或对象校验失败

该备份不可用。不要绕过摘要、大小或归档目录校验，也不要只恢复数据库。
先恢复源服务，再检查 PostgreSQL、MinIO 健康和数据库内容引用。

### 恢复目标卷已经存在

脚本会失败关闭。不要改用源卷或强行覆盖。选择一个新的受控恢复项目名，或者先确认
旧目标确实属于失败演练，再使用清理脚本。

### 恢复后应用启动失败

保留隔离环境，检查控制面 Flyway、数据库、对象元数据和端口状态。禁止在源环境中
尝试恢复命令。

### 源环境没有恢复健康

停止清理备份和登记成功。执行 `make deploy-smoke` 并检查源服务日志；只有五个入口
恢复健康后，备份演练才允许继续。

## 目标与生产边界

- M1 恢复点目标：不超过 24 小时。
- M1 恢复时间目标：不超过 60 分钟。
- M1 仅证明当前单机、合成数据和确定性模型环境。
- 生产发布前仍需补充备份加密、外部密钥托管、离站副本、保留周期、恢复授权、
  自动调度、告警、容量和定期演练。
