# 可信 RAG 基座

面向企业授权知识与数据的可信问答平台。后端为六个限界上下文、三个 Spring Boot 运行进程；人员前端为唯一 React/Fluent SPA。

主仓库为 [GitHub 私有仓库](https://github.com/liming-cloud/ai-platform)，主分支为 `main`；停止向 Gitee 同步。

从 [文档入口](docs/README.md) 阅读目标、规范和设计，从 [任务入口](docs/tasks/README.md) 恢复当前工作。产品进展只以任务控制面和可复核证据为准，源码存在不表示产品闭环完成。

```sh
make project-status
make resume-context
make project-verify
make architecture-guard
```

开发、部署和验收入口见 [仓库结构](docs/architecture/repository.md)；运行条件与恢复步骤见 [运行设计](docs/design/operations/demo-delivery.md)。本地秘密不提交；截图只放 `/private/tmp`。
