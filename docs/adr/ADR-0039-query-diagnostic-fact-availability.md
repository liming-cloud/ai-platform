# ADR-0039：查询诊断事实可得性补充

## 状态

待接受

## 日期

2026-07-31

## 决策负责人

待用户确认，代表产品与架构负责人。

## 背景和矛盾

ADR-0038 要求 `QueryRun` 进入任意终态时写入内部 `QueryDiagnosticFact`，其中包含“经受权
候选检索得到的候选数量”，并禁止用零或其他猜测值填充缺失事实。

ADR-0037 的固定执行顺序先调用嵌入模型，随后才进行候选检索。受控模型配置复核、嵌入调用或
嵌入响应校验可能在候选检索之前失败。此类 `FAILED` 运行已占用配额并已创建 `QueryRun`，但
没有发生候选检索，因而不存在可复核的候选数量。把数量写为 `0` 会把“未收集”伪造为“检索到
零个候选”，与 ADR-0038 的禁止猜测约束冲突。

## 决策提案

1. `QueryDiagnosticFact` 的候选事实拆为可空的 `candidateCount` 和固定枚举
   `candidateCollection`：`COLLECTED` 或 `NOT_COLLECTED`。
2. 仅当已完成授权候选检索时，`candidateCollection` 为 `COLLECTED` 且
   `candidateCount` 为非负精确数量；即使最终因对话或引用校验失败，该数量仍保留。
3. 在候选检索开始前失败的 `FAILED` 运行，`candidateCollection` 为 `NOT_COLLECTED` 且
   `candidateCount` 为 `null`。该状态不表示空候选，不可用于检索质量、拒答率或运营统计。
4. `retrievalAuthorization` 仍仅可为 `AUTHORIZED`：只有已经通过普通查询权限、占用配额且
   创建 `QueryRun` 的请求才可产生诊断事实。`citationValidation` 保持 ADR-0038：
   `ANSWERED` 为 `VALIDATED`；`REFUSED` 与 `FAILED` 为 `NOT_APPLICABLE`。
5. 聊天与嵌入模型修订继续来自运行开始时冻结的助手目标，即使检索未开始也不得读取当前配置。
   这不增加候选正文、资源标识、问题、回答、提示词、供应商错误、密钥或令牌字段。
6. 终态、最小引用和诊断事实仍须在同一数据库事务中提交，诊断读取权限、失败关闭和删除级联
   保持 ADR-0038 不变。

## 备选方案及拒绝原因

### 方案 A：将未检索写为零个候选

拒绝。零是一个检索结果，而检索前失败根本没有结果；该写法会伪造审计事实。

### 方案 B：检索前失败不写诊断事实

拒绝。它违背 ADR-0038 对已创建并进入终态的 `QueryRun` 的完整诊断记录要求，也会让失败审计
出现不可解释空洞。

### 方案 C：在失败后重新检索以补齐数量

拒绝。会读取当前索引或当前模型配置，可能触发外部 I/O，并把历史失败替换为新状态。

## 接受后的实现证据

接受后，WP-0012 需先补反例：不允许 `NOT_COLLECTED` 携带数量，不允许 `COLLECTED` 缺少
数量，不允许 `ANSWERED` 使用非 `VALIDATED`，且无 `audit:read` 的读取失败关闭。随后补
PostgreSQL 原子终态提交、删除级联、自动配置与受权 HTTP 回归。M4 未完成前不得进入 M5。
