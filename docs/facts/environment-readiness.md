# 环境 Readiness

Stage: facts  
Category: facts  
Status: current  
Review Status: agent-reviewed  
Reviewers: engineering lead, DevOps owner  
Approval: agent-approved  
Inputs: `deploy/README.md`, `deploy/docker-compose.yml`  
Outputs: 研发环境依赖与 readiness 结论  
Depends On: `docs/50-delivery-plan.md`  
Load When: environment-setup/before-implementation  
Next: 启动 S5.1 统一模型调用、trace 和 usage 基线

## 结论

`environment-ready: pass`。

本地 Docker Compose 真实依赖已在当前机器启动并通过健康检查。S3/S4 已进一步证明后端和前端能在这些真实依赖上同时运行，并完成鉴权、Provider Catalog 和真实模型连接测试。

## 当前依赖

| 依赖 | 用途 | Compose 服务 | 健康检查 |
| --- | --- | --- | --- |
| PostgreSQL 16 + pgvector | 主库、migration、向量能力首期落点 | `postgres` | `pg_isready` |
| Redis 7 | Sa-Token session、缓存、限流 | `redis` | `redis-cli ping` |
| MinIO | 文档、附件、解析中间产物 | `minio` | `mc ready local` |

## 目录规则

- 所有研发环境脚本放在 `deploy/`。
- Docker Compose 文件固定为 `deploy/docker-compose.yml`。
- 环境变量样例固定为 `deploy/.env.example`。
- 启停和检查脚本固定放在 `deploy/scripts/`。

## Runtime Evidence

验证时间：2026-05-10 14:00-14:06 Asia/Shanghai。

```bash
docker --version
docker compose version
cp deploy/.env.example deploy/.env
deploy/scripts/up.sh
deploy/scripts/healthcheck.sh
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
```

结果：

- Docker `29.2.1` 可用，Docker Compose `v5.1.0` 可用。
- PostgreSQL 容器 `ai-platform-postgres` healthy，`pg_isready` accepting connections。
- Redis 容器 `ai-platform-redis` healthy，`redis-cli ping` 返回 `PONG`。
- MinIO 容器 `ai-platform-minio` healthy，`mc ready local` 返回 ready。

## Runtime Gate

进入后续业务切片前仍必须保持以下命令通过：

```bash
deploy/scripts/up.sh
deploy/scripts/healthcheck.sh
```

如果 Docker 不可用、端口冲突或任一健康检查失败，后续实现 gate 必须回退为 `blocked`。

## 完成标准

环境切片完成不等于容器启动。真正完成必须满足：

- Docker 依赖健康检查通过。
- 后端服务连接真实 PostgreSQL、Redis、MinIO。
- 前端服务和后端服务同时启动。
- 页面正式操作无报错。
- 功能在真实依赖上正常完成，不使用 fake、in-memory 或静态数据冒充。
