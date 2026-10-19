# WP-0019 S1：缓存修复与运行恢复

## 范围与结论

执行日期：2026-10-19（Asia/Shanghai）。缓存缺陷已完成本地验证并推送；S1 候选已真实部署、健康及只读事实通过。认证后的生产浏览器、完整表单／disabled 原型一致性和新方舟真实合同仍未执行。S1、WP-0019、M6 未完成，不进入 S2～S6。

基线控制提交为 `e334da10a461fcf57d49cbbd31c81e497ab3d251`；缓存源码 `14914427775415a0b1686a7b312df4394a139d07`、工具证据提交 `439954423c16094dc943529284b895fef5954dd7` 已推送。产品候选仍为已验证的 `e0224fa9847fdaf297b3ad896e37ae7796e29a50`；当前控制提交在 backend／frontend／deploy 仅有一份后端测试差异，产品源码等价，不把 HEAD 当作运行版本。

## 缓存缺陷闭环

三个 `platform-connection-{form,pagination,partial}.browser.cjs` 入口显式固定本轮 `.vite` 缓存，共用 31 行生命周期辅助代码。正常结束先关闭浏览器／Vite 再删除；关闭失败仍清理。启动失败、未捕获异常和显式退出同步兜底；SIGINT／SIGTERM／SIGHUP 保留原信号对应退出码，关闭悬挂五秒兜底。只删除可重建缓存，不删除父目录、截图、日志或验收证据；SIGKILL／断电不在用户态清理保证内。

先实际运行 12 项失败反例，均因旧实现残留缓存失败；修复后 21 项退出反例全部通过，覆盖三个真实脚本入口的依赖边界替身、正常／异常／信号退出和证据字节保留。正式 Make 入口的 Python 29 项／Node 43 项通过，CI 不需要安装 Vite 或浏览器才能运行这些合成反例。

另用锁定 Playwright 1.62.1 运行真实 Chrome 源码回归：表单 60、分页 37、partial／disabled 30 项通过。分页首次在导航阶段失败、未进入断言；该次同样完成缓存清理并保留失败截图／日志。仅增加仓库外只读导航观测后，HTTP 200、无未完成请求、37 项通过，未修改断言、超时或容差。四次运行的缓存均为零，全部非缓存产物保留；原有 72 项关键证据摘要复核无变化。

复验：`make work-package-acceptance-test`；真实源码浏览器分别运行 `NODE_PATH=<锁定运行时> node scripts/quality/platform-connection-{form,pagination,partial}.browser.cjs`。日志目录 `/private/tmp/wp0019-cache-cleanup-20261019-d8_wd8i8`；`cache-evidence.json` SHA-256 为 `b32150c9457447a13505a150646fec1c12d7b1ce58a499a40b3763254dbfdcc7`。详细产物仅在仓库外，任务记录不保存截图。

## 运行恢复与实际部署

Docker Desktop 已恢复可用。先只读确认七服务健康；前端仍为 `4e47d97e`、control-plane／query-runtime 为 `7906451e`。旧 ingestion-worker 的镜像描述查询返回 No such image，未以旧报告补填本轮 OCI revision。

从精确 `e0224fa9847fdaf297b3ad896e37ae7796e29a50` Git 对象导出干净构建输入，顺序补建 control-plane（188.5 秒）与 query-runtime（175.9 秒），复用此前已完成且摘要／revision 独立匹配的前端与 ingestion-worker。结束已删除本轮可重建源码副本。未重启 Docker、清理镜像缓存或数据卷。

仅通过现行 Compose 更新四应用，28.8 秒完成；七服务健康、`make deploy-smoke` 四入口通过。三个数据服务容器 ID 保持不变。以下四个实际运行镜像的 OCI revision 均独立确认为 `e0224fa9847fdaf297b3ad896e37ae7796e29a50`：

| 实际应用 | 实际镜像摘要 |
| --- | --- |
| console-web | `sha256:ba49240be84a83e47dc205bdff36346c6549a5b1b3a6c19edc4eef0cbff1a815` |
| control-plane | `sha256:caff122b331d71f6c6f4d0e8d009187f2292ac37b4f74c59f4ffede28864a7d6` |
| ingestion-worker | `sha256:5d14467dc8b02f18d8874e728f564022bd18fb19e236dc424e0179e880603cb0` |
| query-runtime | `sha256:bcfad60ed53f43ac6469cbeb87ce9bab3266ade5bf5a8cbc7827bbc93195da88` |

启动输入的 S1 写入布尔值为默认 true，未应用临时暂停覆盖层。最新 Chrome 实际访问平台／租户独立登录入口，通过两项入口检查；匿名读取连接目录、预设和精确详情均返回 401，无页面脚本错误。该五项只证明公开入口及匿名隔离，不证明已认证功能或完整原型一致性。

部署前后执行同一 `REPEATABLE READ READ ONLY` SQL。精确连接 `2093675537095458817` 保持修订 4／VERIFIED／OPENAI_MODELS_API，发布事实 1 条；候选 135 个已于 2026-10-15 到期。总连接 2 条；草稿 `2110269575189757954` 保持修订 0、未探测／未发现／未发布；模型部署、目录与授权均为 0。除观察时间外事实逐项相同，无模型业务写入或厂商外呼。

## 原型、认证与远端门禁

OpenDesign Cloud 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 可连接，17 个入口资源的当前 HTTP／磁盘内容与已接受 `cce686e4634ca3c523e4c8aeffd95752f1a04c3a` Git 内容逐字节一致。原型未修改、未创建副本；资源绑定不替代浏览器视觉验收。当前会话入口：[连接目录](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-model-connections&state=success&review=1)。

原有效 `existing-local-credentials.json` 在缓存清理开始前已不存在，原因未确认；清理计划保存了该事实。本轮仅在已授权 WP-0017～0019 临时范围检查 169 个当前用户独占 0600 JSON，未发现可用凭据结构。仓库忽略目录中的 `deploy/.local/browser-acceptance-password` 为当前用户 0600 文件；用该旧来源对 admin 进行一次正式界面登录，返回 401（发生于运行替换前）。组织认证源码在两产品版本间无差异，未反复试密码、导出摘要、探查浏览器会话或重置账户。已询问仍有效的既有安全来源路径，不要求重新发送密码。

因此，真实暂停四命令、12 组 disabled／恢复、正式十项已认证只读验收、66 组完整表单与 18 组交互对照保持 NOT_RUN；不通过伪造租户材料、会话或模拟 URL 绕过正式验收。新方舟最小 ListEndpoints 仍缺匹配的独立发现材料，旧 OPENAI_MODELS_API 发现不能替代；不重新发现或发布旧修订，不开放后续供应切片。

缓存修复最终提交对应 [CI 39187117627](https://github.com/liming-cloud/ai-platform/actions/runs/39187117627)为 failure、steps=[]。官方注释说明账户近期付款失败或 spending limit 不足，作业未启动；不能记为测试失败或 CI 通过。当前报告推送后再核对精确提交的远端结果，本地检查不替代远端门禁。

## 验证与恢复

缓存阶段及本轮控制面更新后均通过 `make project-status`、`make project-verify`（81 项）、`make architecture-guard`（50 项及前后端守卫）、`make decisions-sync`、`make resume-context`，恢复输出低于 12 KiB。本次没有重新运行完整 Maven／frontend-test；产品候选的 10 月 17 日完整验证仍按[原证据](WP-0019-S1-write-availability-source-20261017.md)引用，不改日期或冒充今天执行。

下一步先恢复有效平台及租户的安全凭据来源，再对当前已部署 e022 执行暂停／恢复、正式只读、完整表单与已接受说明映射；四镜像不因控制面提交变化重复构建。取得匹配发现材料后独立验证新方舟合同；CI 需账户恢复作业执行条件。前沿仍为 S1，后续 S2～S6、WP-0020 及 M6 全量／人工门禁未开放。

运行证据目录 `/private/tmp/wp0019-s1-resume-20261019-w_f41_cl` 为 0700，文件 0600；`safe-evidence-digests.json` 共 18 项，SHA-256 为 `9e7f6b4ab33194ec368c72313de1a353c5772d2f8cd7a6ab0018a71dc8bf0dc8`，不含秘密、截图或浏览器 trace。Local Codex 显式加载当前工程助手的 tdd、code-review、wayfinder，使用各 SKILL.md 和 TDD mocking 约束；内联任务没有 Runtime／StageRunResult，阶段校验不适用。代码图谱定位三个脚本及登录 seam，form 入口影响分析限定自身；据此只提取被三个现有入口使用的缓存生命周期，不修改业务架构。
