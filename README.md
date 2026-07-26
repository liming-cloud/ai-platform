# 可信 RAG 基座

本仓库正在进行企业级可信 RAG 基座绿地建设。当前处于 M0 设计冻结阶段，只保留项目控制面、架构决策和可直接实施的产品设计，不包含可运行的产品源码。

## 当前事实来源

- `project/CHARTER.md`：产品使命、边界和质量原则。
- `project/STATE.yaml`：当前里程碑、工作包、决策和证据。
- `project/work-packages/WP-0001.md`：当前唯一工作包。
- `project/evidence/INDEX.md`：可复核证据索引。
- `docs/adr/`：已经生效的架构决策。
- `docs/product/`、`docs/design/`、`docs/architecture/`、`docs/planning/`：绿地产品与工程设计。

聊天记录、历史摘要和旧实现都不是当前项目事实来源。

## 恢复项目上下文

```bash
make project-status
make resume-context
```

## 验证项目控制面

```bash
make project-verify
make validate
```

## 绿地边界

- 不兼容旧接口、数据库、配置、页面和部署方式。
- 不迁移旧数据，不建设双写或并行运行。
- 旧后端、旧前端和旧部署文件已从当前分支移除，可通过 Git 历史恢复查阅。
- 本地未受版本控制的配置和密钥不会被自动删除。
- M0 人工评审通过前，不创建新的产品实现。

## 下一步

产品负责人完成 M0 设计评审后，创建 `codex/trusted-rag-foundation-v1`，激活 WP-0002，并从空源码边界建立新的前端、后端和 Docker 工程。
