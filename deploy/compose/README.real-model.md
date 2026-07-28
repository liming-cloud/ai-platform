# M2 真实模型受控验收

此覆盖层只用于研发验收，不能作为生产部署或默认演示配置。

1. 在受控终端设置 `FOUNDATION_SECRET_ENCRYPTION_KEY_M2_V1`。覆盖层固定使用 `M2_V1` 作为本地研发主密钥版本；该值必须是 Base64 编码的 32 字节本地研发主密钥。
2. 设置 `MODEL_ALLOWED_ENDPOINTS=https://ark.cn-beijing.volces.com/api/v3`。该值必须与平台受控目录中的方舟标准推理端点完全一致。
3. 启动时组合基础文件与覆盖层：

   ```bash
   docker compose --env-file deploy/compose/.env.example \
     -f deploy/compose/compose.yml \
     -f deploy/compose/compose.real-model.yml up --detach --build
   ```

4. 以拥有相应平台、租户或个人权限的成员调用 `POST /api/v1/model-configurations` 写入 API Key。响应只会返回 `credentialConfigured` 等安全状态，不能回显密钥。

## M2 全链路验收

服务启动后，可使用 `scripts/development/acceptance-real-model.sh` 执行一次独立的研发验收。脚本会创建平台受控目录、个人模型配置、合成知识空间、真实 Embedding 索引和受控助手版本，再发起一次带引用的真实查询；最终只输出模型名、耗时、引用数和不可逆摘要。

API Key 必须预先写入一个仅本地可读的临时文件，文件权限必须禁止组和其他用户读取，例如 `0600`。文件路径通过 `FOUNDATION_M2_API_KEY_FILE` 传给脚本；权限过宽时脚本会在任何 HTTP 请求前拒绝执行。不要把 API Key 作为环境变量、命令参数、标准输入重定向内容或日志文本。

```bash
FOUNDATION_M2_API_KEY_FILE=/Users/lingou/Desktop/huoshan.txt \
scripts/development/acceptance-real-model.sh
```

脚本默认使用已验证的标准推理目录：`doubao-seed-2-1-turbo-260628`（262144 上下文窗口、262144 最大输出）和 `doubao-embedding-vision-251215`（131072 上下文窗口、2048 维）。嵌入模型使用方舟多模态嵌入协议；如需覆盖，只允许平台管理员使用新的已批准目录事实。所选端点必须与 compose 的 `MODEL_ALLOWED_ENDPOINTS` 一致。

API Key 不得放入 `.env`、镜像、容器环境变量、命令历史、日志、证据或版本控制。覆盖层只向控制面与查询进程传递本地研发主密钥和出站端点，查询时从数据库密文短生命周期解密配置凭据。

真实验收使用方舟标准 `/api/v3` 推理端点和平台受控模型编码；不得把模型编码或 API Key 写入镜像、环境文件或版本控制。
