# WP-0010：真实模型受控验收编排

实现提交：`f014be1631516df004ea2a31a8b1a6355c878706`（`feat: 增加真实模型受控验收编排`）。

## 范围

新增 `scripts/development/acceptance-real-model.sh`，用于在显式启动的 M2 compose 覆盖层上验证完整受控路径：平台发布目录、个人模型配置的加密写入与启用、合成知识发布、真实 Embedding 索引、受控助手版本发布，以及带引用的真实查询。

脚本只接受本地文件路径 `FOUNDATION_M2_API_KEY_FILE`。密钥只在脚本进程内用于创建模型配置请求，随后清除变量；标准输出只保存模型名称、耗时、引用数和不可逆摘要，不保存 API Key、正文、回答、会话令牌、配置标识或端点原文。

默认模型为 `qwen-plus` 和 `text-embedding-v4`。前者的上下文与输出上限、后者可选的 1024 维输出依据运行当日阿里云百炼官方文档设置；端点和模型名均可由受控环境变量覆盖，仍必须通过平台出站白名单。

## 可复现验证

```bash
bash -n scripts/development/acceptance-real-model.sh

FOUNDATION_M2_API_KEY_FILE=/tmp/foundation-m2-key-does-not-exist \
  scripts/development/acceptance-real-model.sh

make project-verify
```

结果：语法通过；无密钥预检在任何网络请求前因文件不可读失败，标准输出为空；项目治理验证通过。

## 尚未完成的真实调用

本提交不执行外部模型调用。当前工作区未设置 M2 本地研发主密钥或仅本地可读的 API Key 文件，因此不能把“验收脚本已具备”误报为“真实模型已通过”。

执行真实验收前，人工需在受控终端完成以下准备：

1. 用 `compose.real-model.yml` 启动受控端点和本地研发主密钥覆盖层；
2. 以不可记录的本地临时文件提供 API Key；
3. 运行脚本并仅保存其 JSON 摘要；
4. 复核摘要、数据库密文状态和容器日志无密钥泄露；
5. 在 M2 的全部自动与真实调用证据完备后，先登记人工评审通过，才可进入 M3。
