# WP-0017：资源配额独立设置切片部署后收口

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0017 |
| 验证范围 | `platform-tenant-quotas:0` 的独立查看、编辑、取消、保存、刷新和已核实用量下限 |
| 前端验证提交 | `078af0110d3e70b41fb708ae20792633cfe79795` |
| 后端验证提交 | `dba24ab30e7c1748c412fba157c482d1da3c6cdb` |
| 浏览器验收脚本提交 | `7aa54a92946dab2fa455da65cd2beae83bfe8956` |
| 决策 | ADR-0084，保留 ADR-0079、ADR-0080 的身份与安全边界 |
| 日期 | 2026-09-28 |

**资源配额独立设置切片在上述范围内通过。** 它只保存五项租户配额，并用现有知识来源的只读聚合限制下调；不代表旧写入路径已消费新配额，也不代表 WP-0020 的统一计量与并发预留已完成。WP-0017 整包、66 个 M6 生产入口和 M6 仍未完成。

## 固定版本与部署事实

| 对象 | 实际运行事实 |
| --- | --- |
| `console-web` OCI revision | `078af0110d3e70b41fb708ae20792633cfe79795` |
| `console-web` 镜像 | `sha256:4c296bcdc51d68cce50547e722828da040dc4638d03672fbd7d1fa15463ff27c` |
| `control-plane` OCI revision | `dba24ab30e7c1748c412fba157c482d1da3c6cdb` |
| `control-plane` 镜像 | `sha256:10b16c60ffa5aeccb56dd54a288c5dc5c76e624966b2e89fe7de2d08aa835a1d` |
| 固定原型 | OpenDesign 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，已验收提交 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`，入口 `platform-tenant-quotas` |

[OpenDesign 配额入口](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenant-quotas&state=success&review=0) 是本机会话预览地址；失效时按项目、入口与固定提交定位。未修改 OpenDesign。最终 Compose 前后端均为 `healthy`，`make deploy-smoke` 四个入口通过。后端运行版本不同于当前仓库 HEAD，分别核对了 OCI revision 与镜像摘要。

## 生产功能和数据库事实

最终镜像上，`make acceptance-work-package WP=WP-0017 SLICE=quotas` **9/9 组通过**。浏览器通过正式接口创建一个可追溯合成租户及两座知识库；页面展示五项配置和真实已用量，取消时零写入并恢复焦点，客户端和服务端均拒绝低于已用量，保存到已核实下限后刷新可恢复版本 1。旧版本、未知字段、缺失 CSRF 和租户身份均不能写入。随后以本轮新管理员执行 `SLICE=login`，平台与租户登录回归 **15/15 通过**。验收不删除或重置历史对象。

本轮合成租户 ID 为 `2104412284297150465`，两座知识库 ID 为 `2104412291800760322`、`2104412291909812226`。在真实 PostgreSQL 的 `REPEATABLE READ READ ONLY` 事务中只查询计数和状态：`transaction_read_only=on`；生命周期恰有一条 `ACTIVE`／版本 1；配额恰有一条、版本 1，五项值为 `2`、`1234`、`12500000000`、`50000000`、`1048576`；恰有两座属于该租户的 `DRAFT` 知识库。断言值为 `verified|1`，以 `ROLLBACK` 结束。SQL SHA-256 为 `945f277df06e59372f4ef9c6f5411fec7682f1252f160df5850fcd588c36a11d`，无秘密结果的 SHA-256 为 `2e1cb3d9413c9632e03f5fb3f05353fe4a689b6f02d63a3ec94bbdeb5899837b`。

完整后端 Maven 验证通过 993 项；最终前端 `make frontend-test` 通过 44 文件、226 项及类型、Lint、生产构建。`make architecture-guard` 通过，后端依赖边界违规为 0。上述源码检查与生产浏览器结论分开记录。

## 固定原型一致性

同一独立 Chrome 分别打开固定 OpenDesign 原型与最终运行页，在 390／1024／1440px 查看配额摘要及编辑弹窗。暖色画布、标题字体、五项分栏、真实表单、标签层级、单位与用量提示、全宽操作分隔线、聚焦描边、短按钮和移动端固定操作区均按原型复核。原型与生产在 390px 的弹窗盒尺寸同为 `352 × 828px`，在 1024／1440px 宽度同为 `768px`；桌面高度实测约 `577.7px`／`578.5px`。三档页面横向溢出均为 0；200% 文字、键盘开关弹窗、焦点恢复与 reduced-motion 均可操作。生产业务数据和租户名称来自正式服务，不复制原型示例值。尚未实施的功能与登录策略入口按用户已确认边界留待各自切片验证，不用伪按钮填补。

新增焦点断言曾在 Fluent 过渡期间读取到瞬时 `3px`，该候选验收停在视觉组；在不放宽最终 `2px` 断言的前提下等待过渡结束，最终镜像重跑 9/9 通过。失败日志、最终日志、只读 SQL、原型与生产截图都仅位于 `/private/tmp`，未进入仓库；最终验收日志 SHA-256 为 `e7226b110d2692f0ed4ba6c1cdbce0f9919a5e834b63125688dc57b828f192b0`，登录日志为 `e9319a94d64e5ef6f539dadcaa4234742c7f053b20aed5e07791e27a9ef1c2d3`。凭据文件为 0600，未在日志、仓库或本文记录口令、Cookie、Token 或摘要。

本切片关闭后，产品前沿仍为 WP-0017。下一独立切片先冻结 `platform-tenant-features:0` 的权限、跨会话撤销与原型合同；本次不实施功能开关、登录策略、平台账户、普通会员或 WP-0020 的知识计量预留。
