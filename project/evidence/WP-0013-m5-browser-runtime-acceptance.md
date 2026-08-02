# WP-0013：M5 真实运行与浏览器验收续证

## 目的

在 ADR-0040 已接受的冻结聊天路由首条切片上，复核本地真实模型覆盖层、旧密文恢复路径和最终用户浏览器的最小披露。本文不记录 API Key、本地研发主密钥、令牌、原始供应商错误或模型调用正文。

## 环境与安全边界

- 本地 `deploy/.env` 保持未提交且权限为 `0600`；仅向真实模型覆盖层提供版本化本地研发主密钥和受控 HTTPS 端点。
- API Key 仅由 `FOUNDATION_M2_API_KEY_FILE` 指向的 `0600` 本地文件供验收脚本短时读取，未进入环境变量、镜像、日志、证据或版本控制。
- 当历史 `M2_V1` 密文不可解密时，`FOUNDATION_M2_FORCE_NEW_PERSONAL_CONFIGURATIONS=true` 只新建个人 Chat 与 Embedding 配置，继续复用已发布目录，避免覆盖历史配置。

## 可复现命令与结果

| 命令 | 结果 |
| --- | --- |
| `docker compose --env-file deploy/compose/.env.example --env-file deploy/.env -f deploy/compose/compose.yml -f deploy/compose/compose.real-model.yml config --quiet` | 真实模型覆盖层配置通过；不输出环境变量值。 |
| `docker compose --env-file deploy/compose/.env.example --env-file deploy/.env -f deploy/compose/compose.yml -f deploy/compose/compose.real-model.yml up --detach` | 控制面、查询运行时和摄取工作者以覆盖层重新创建并健康。 |
| `make deploy-smoke` | 五个应用入口健康检查通过。 |
| `python3 -m unittest scripts.quality.test_real_model_acceptance` | 5 项通过；覆盖默认复用、显式新建个人配置、密钥文件缺失和权限过宽时的失败关闭。 |
| `FOUNDATION_M2_API_KEY_FILE=<仅本地可读文件> FOUNDATION_M2_FORCE_NEW_PERSONAL_CONFIGURATIONS=true scripts/development/acceptance-real-model.sh` | 退出码为 0；真实索引新增 `ACTIVE` 终态，带引用真实查询新增 `ANSWERED` 终态。 |

## 浏览器复核

以本地受限演示租户的最终用户会话操作查询站点：

1. 登录后页面仅显示已发布助手、问题输入和引用校验说明；未展示模型配置标识、候选路由、供应商、端点、密钥或原始错误。
2. 对合成知识提出工作日服务时间问题后，页面完成“正在核验证据”状态，展示带引用的可信回答和一个可核验来源。
3. 先前聊天候选输出前超时只形成受控失败与追踪标识；页面未泄露供应商错误、路由或凭据，符合 ADR-0040 的失败关闭边界。

## 结论

真实模型运行时、密文恢复和最终用户最小披露已具备可复核证据。WP-0013 仍处于进行中：必须完成 M5 自动验收汇总并获得人工评审通过，方可激活 M6。
