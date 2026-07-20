# AI 平台项目交付文档

## 1. 交付范围

本次交付包含七个业务上下文：

- `identity-access`：登录、租户、用户、角色、权限、邀请与权限申请。
- `approval-center`：模型等高风险操作的审批。
- `observability-audit`：操作审计与安全日志。
- `model-hub`：模型供应商、模型配置、秘密与使用策略。
- `knowledge-base`：知识库、文档入库、索引、检索与恢复。
- `app-studio`：AI 应用、发布、会话与结果恢复。
- `summary-ops`：平台和租户运营汇总。

前端位于 `frontend/console-web`，后端 Maven reactor 位于 `backend`。本地部署和数据库初始化只使用 `deploy/`。

## 2. 部署结构

Docker Compose 启动以下服务：

| 服务 | 镜像或构建方式 | 用途 |
| --- | --- | --- |
| PostgreSQL | `pgvector/pgvector:0.8.2-pg16-bookworm` | 主数据库与向量扩展 |
| Redis | `public.ecr.aws/docker/library/redis:8.8.0-alpine3.23` | 会话与登录保护 |
| SeaweedFS | `chrislusf/seaweedfs:4.29` | S3 兼容对象存储 |
| RocketMQ | `apache/rocketmq:5.5.0` | 领域事件 |
| backend | 从 `backend/Dockerfile` 构建 | 后端 API |
| frontend | 从 `frontend/console-web/Dockerfile` 构建 | Web 控制台 |

开源基础设施镜像直接使用公开版本标签，不在仓库内重复构建，也不要求在 Compose 中固定镜像 digest。后端和前端是本项目交付物，仍从源码构建。

所有宿主机端口默认绑定 `127.0.0.1`。该 Compose 文件是本地部署基线，不包含生产高可用、外部密钥管理、TLS 终止或跨主机编排。

## 3. 环境准备

要求：

- Docker Desktop 或 Docker Engine，支持 `docker compose`。
- Java 21、Maven、Node.js 和 npm，仅在源码开发或执行完整验证时需要。
- `openssl` 与 Python 3，用于生成本地随机秘密和 bootstrap 管理员密码。

生成环境文件：

```bash
make deploy-env
```

该命令从 `deploy/.env.example` 生成权限为 `0600` 的 `deploy/.env`。文件包含数据库、对象存储、模型秘密、邀请签名和 bootstrap 管理员随机凭据，不得提交到 Git。

## 4. 启动与检查

```bash
make deploy-up
make deploy-status
make deploy-smoke
```

默认地址：

- 前端：`http://127.0.0.1:5173`
- 后端：`http://127.0.0.1:8080`
- SeaweedFS S3：`http://127.0.0.1:9000`

首次登录：

- 租户 ID：`2001`
- 用户名：`admin`
- 密码：读取 `deploy/.env` 中的 `AIP_BOOTSTRAP_ADMIN_PASSWORD`

bootstrap 管理员仅用于首次平台配置。平台管理员应通过产品内的租户创建和用户邀请流程建立真实租户及用户，不应把 bootstrap 租户作为业务租户使用。

## 5. 数据库初始化

数据库脚本位于 `deploy/postgres`：

- `init/01-schema.sql`：全新数据库的完整最终结构。
- `init/02-seed-base.sql`：可重复执行的基础元数据和 bootstrap 管理员初始化。
- `configure-runtime-role.sql`、`runtime-object-policy.sql`：运行账号最小权限。
- `verify-schema.sql`：初始化完成后的结构校验。

初始化数据只包含：

- 一个 `platform` 平台管理租户；
- 一个 `admin` bootstrap 用户；
- 一个 `platform-admin` 角色；
- 系统权限目录、菜单、功能开关及该管理员的必要授权。

不会初始化模型供应商、模型配置、知识库、文档、AI 应用、会话或其他业务数据，也不会创建租户管理员和普通用户样例。

本项目按全新数据库交付，不保留历史升级脚本、执行账本或兼容占位数据。已有数据卷不会被自动重建；需要重新初始化时，应先备份必要数据，再使用项目级 `deploy-reset` 明确删除该 Compose project 的数据卷。

## 6. 停止与重置

停止但保留数据：

```bash
make deploy-down
```

仅删除当前 project 的容器、网络和数据卷：

```bash
make ALLOW_DEPLOY_RESET=yes deploy-reset
```

禁止使用 Docker 全局 prune 代替项目级清理。

## 7. 交付验证

执行完整源码验证：

```bash
make validate
```

该目标检查交付目录合同、Shell 语法、后端上下文结构、完整后端测试、前端 lint、生产依赖审计、测试与构建。

部署配置可单独验证：

```bash
make deploy-config
```

## 8. 维护边界

- 不在仓库中维护第二套本地部署入口。
- 不向 `deploy/` 加入发布取证、一次性验收报告或扫描器测试夹具。
- 数据库结构变化直接更新 `init/01-schema.sql`，交付前使用空数据卷验证完整初始化。
- 基础 seed 只维护平台启动所需元数据；业务样例数据应由独立测试夹具或产品流程创建。
- 开源基础设施升级时修改 Compose 中的版本标签并执行空卷启动、数据库初始化和基本健康检查。
