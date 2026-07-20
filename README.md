# 企业级 AI 平台

面向多租户企业场景的 AI SaaS 平台，包含身份与权限、审批与审计、模型治理、知识库、AI 应用及运营汇总能力。

## 技术栈

- 后端：Java 21、Spring Boot、MyBatis-Plus、PostgreSQL/pgvector、Redis、RocketMQ、Spring AI。
- 前端：React 18、TypeScript、Fluent UI v9、Vite。
- 本地部署：Docker Compose，唯一入口为 `deploy/`。

## 快速开始

```bash
make deploy-env
make deploy-up
make deploy-smoke
```

首次生成的 `deploy/.env` 包含随机 bootstrap 管理员密码。登录租户 ID 为 `2001`，用户名为 `admin`。请勿提交 `deploy/.env`。

停止服务：

```bash
make deploy-down
```

删除当前 Compose project 的数据卷：

```bash
make ALLOW_DEPLOY_RESET=yes deploy-reset
```

## 开发验证

```bash
make validate
```

完整交付范围、部署结构、初始化数据和运维说明见 [项目交付文档](docs/PROJECT_DELIVERY.md) 与 [部署说明](deploy/README.md)。
