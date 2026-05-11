# AI Platform Deploy

`deploy/` 是本项目研发环境和后续部署资产的唯一入口。研发第一步必须先让这里的真实依赖可启动、可检查、可复现。

## 当前本地依赖

- PostgreSQL 16 + pgvector
- Redis 7
- MinIO

这些依赖覆盖首批后端切片需要的主库、Sa-Token Redis session、对象存储和向量能力。后续如果引入 OpenSearch、消息队列或独立向量库，也必须先追加到 `deploy/`，再更新 `docs/facts/environment-readiness.md` 和 `docs/facts/command-catalog.md`。

## 使用方式

```bash
cp deploy/.env.example deploy/.env
deploy/scripts/up.sh
deploy/scripts/healthcheck.sh
```

停止环境：

```bash
deploy/scripts/down.sh
```

## 规则

- 不在根目录散落 Docker Compose、临时 shell 或环境变量说明。
- 不允许应用代码绕过 `deploy/` 假设本机已经存在依赖。
- 所有服务必须有 healthcheck 或等价验证命令。
- 完成标准不是容器启动，而是后续前后端连接这些真实依赖后页面操作无报错、功能正常。
