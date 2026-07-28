# M2 真实模型受控验收

此覆盖层只用于研发验收，不能作为生产部署或默认演示配置。

1. 在受控终端设置 `FOUNDATION_SECRET_ENCRYPTION_KEY_M2_V1`。覆盖层固定使用 `M2_V1` 作为本地研发主密钥版本；该值必须是 Base64 编码的 32 字节本地研发主密钥。
2. 设置 `MODEL_ALLOWED_ENDPOINTS` 为一个经过批准的 HTTPS 端点。阿里云百炼按量付费北京共享端点可使用 `https://dashscope.aliyuncs.com/compatible-mode/v1`；专属业务空间端点必须与 API Key 的地域和业务空间一致。
3. 启动时组合基础文件与覆盖层：

   ```bash
   docker compose --env-file deploy/compose/.env.example \
     -f deploy/compose/compose.yml \
     -f deploy/compose/compose.real-model.yml up --detach --build
   ```

4. 以拥有相应平台、租户或个人权限的成员调用 `POST /api/v1/model-configurations` 写入 API Key。响应只会返回 `credentialConfigured` 等安全状态，不能回显密钥。

## M2 全链路验收

服务启动后，可使用 `scripts/development/acceptance-real-model.sh` 执行一次独立的研发验收。脚本会创建平台受控目录、个人模型配置、合成知识空间、真实 Embedding 索引和受控助手版本，再发起一次带引用的真实查询；最终只输出模型名、耗时、引用数和不可逆摘要。

API Key 必须预先写入一个仅本地可读的临时文件，文件路径通过 `FOUNDATION_M2_API_KEY_FILE` 传给脚本。不要把 API Key 作为环境变量、命令参数、标准输入重定向内容或日志文本。

```bash
FOUNDATION_M2_API_KEY_FILE=/受控临时目录/百炼-api-key \
scripts/development/acceptance-real-model.sh
```

脚本默认使用 `qwen-plus` 与 `text-embedding-v4`，并可由 `FOUNDATION_M2_CHAT_MODEL_CODE`、`FOUNDATION_M2_EMBEDDING_MODEL_CODE` 和 `FOUNDATION_M2_MODEL_ENDPOINT` 覆盖。所选端点必须与 compose 的 `MODEL_ALLOWED_ENDPOINTS` 一致。

API Key 不得放入 `.env`、镜像、容器环境变量、命令历史、日志、证据或版本控制。覆盖层只向控制面与查询进程传递本地研发主密钥和出站端点，查询时从数据库密文短生命周期解密配置凭据。

阿里云文档说明，API Key 与 Base URL 必须使用同一计费方案和地域；共享端点仍可用于研发验证，但业务空间专属端点更适合受隔离的业务空间调用。[阿里云百炼 Base URL 总览](https://help.aliyun.com/zh/model-studio/base-url)
