# 本地部署

`deploy/docker-compose.yml` 是项目唯一的本地部署入口。

## 启动

```bash
make deploy-env
make deploy-up
make deploy-status
make deploy-smoke
```

`make deploy-env` 只在 `deploy/.env` 不存在时生成随机秘密。首次登录使用：

- 租户 ID：`2001`
- 用户：`admin`
- 密码：`deploy/.env` 中的 `AIP_BOOTSTRAP_ADMIN_PASSWORD`

## 常用命令

```bash
make deploy-config
make deploy-initialize
make deploy-down
```

删除当前 Compose project 的数据卷必须显式确认：

```bash
make ALLOW_DEPLOY_RESET=yes deploy-reset
```

指定独立 project 或端口：

```bash
make \
  DEPLOY_PROJECT=ai-platform-dev2 \
  DEPLOY_BACKEND_PORT=18080 \
  DEPLOY_FRONTEND_PORT=15173 \
  deploy-up
```

## 镜像

PostgreSQL/pgvector、Redis、SeaweedFS、RocketMQ 和辅助 Alpine 容器直接使用公开版本标签。仓库不再为这些开源基础设施维护 Dockerfile。只有 backend 和 frontend 从本项目源码构建。

## 数据库

数据库初始化内容：

1. `postgres/init/01-schema.sql`：全新数据库的完整最终结构
2. `postgres/init/02-seed-base.sql`：平台基础元数据和 bootstrap 管理员
3. `postgres/configure-runtime-role.sql`：后端运行账号最小权限
4. `postgres/verify-schema.sql`：初始化结果校验

基础 seed 只创建平台 bootstrap 管理员和系统权限/菜单元数据，不创建租户管理员、普通用户或业务样例。
`make deploy-initialize` 可重复执行，用于校验结构、刷新 bootstrap 管理员密码和运行账号授权；它不会重建已有数据库。

不得执行 Docker 全局 prune。
