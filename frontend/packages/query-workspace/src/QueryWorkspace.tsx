import {
  Badge,
  Button,
  Dropdown,
  Field,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Option,
  Skeleton,
  SkeletonItem,
  Text,
  Textarea,
  Title3,
} from "@fluentui/react-components";
import {
  BookInformation24Regular,
  CheckmarkCircle24Filled,
  LockClosed24Regular,
  Warning24Regular,
} from "@fluentui/react-icons";
import { StatePanel } from "@foundation/design-system";
import {
  type Dispatch,
  useCallback,
  useEffect,
  useMemo,
  useReducer,
  useRef,
  useState,
} from "react";
import {
  type QueryFeedbackOutcome,
  type QueryFeedbackReason,
  QueryApiError,
} from "./query-client";
import type { QueryClient } from "./query-client";
import {
  QueryRequestScope,
  initialQueryWorkflow,
  queryMainLayoutClassName,
  reduceQueryWorkflow,
  stableQueryRequestKey,
} from "./query-workflow";
import "./styles.css";

export function QueryWorkspace({
  client,
  assistantPage = 1,
  onAssistantPageChange,
}: {
  client: QueryClient;
  assistantPage?: number;
  onAssistantPageChange?: (page: number) => void;
}) {
  const requests = useMemo(() => new QueryRequestScope(), []);
  const requestKeys = useRef(
    new Map<string, { fingerprint: string; key: string }>(),
  );
  const [workflow, dispatch] = useReducer(
    reduceQueryWorkflow,
    { ...initialQueryWorkflow, stage: "loading-catalog" },
  );
  const [question, setQuestion] = useState("");

  useEffect(() => () => requests.cancel(), [requests]);

  const reloadCatalog = useCallback(async () => {
    const owner = requests.replace();
    dispatch({ type: "loading-catalog" });
    try {
      const page = await client.listAssistants(assistantPage, owner.signal);
      if (requests.isCurrent(owner)) {
        dispatch({
          type: "catalog-loaded",
          assistants: page.items,
          total: page.totalElements,
          hasPrevious: page.hasPrevious,
          hasNext: page.hasNext,
        });
      }
    } catch (error) {
      if (requests.isCurrent(owner)) {
        dispatchFailure(dispatch, error, client);
      }
    }
  }, [assistantPage, client, requests]);

  useEffect(() => {
    void reloadCatalog();
  }, [reloadCatalog]);

  async function ask() {
    const assistantVersionId = workflow.selectedAssistantVersionId;
    const normalizedQuestion = question.trim();
    if (
      assistantVersionId === undefined
      || normalizedQuestion.length === 0
    ) {
      return;
    }
    const owner = requests.replace();
    const idempotencyKey = stableQueryRequestKey(
      requestKeys.current,
      assistantVersionId,
      normalizedQuestion,
    );
    dispatch({
      type: "querying",
      question: normalizedQuestion,
    });
    try {
      const run = await client.createQuery(
        assistantVersionId,
        normalizedQuestion,
        idempotencyKey,
        owner.signal,
      );
      if (!requests.isCurrent(owner)) {
        return;
      }
      dispatch({ type: "run-created", queryRunId: run.id });
      const completed = await receiveEvents(run.id, 0, owner);
      if (completed) {
        requestKeys.current.delete("query");
      }
    } catch (error) {
      if (requests.isCurrent(owner)) {
        dispatchFailure(dispatch, error, client);
      }
    }
  }

  async function resume() {
    if (workflow.queryRunId === undefined) {
      await ask();
      return;
    }
    const owner = requests.replace();
    dispatch({ type: "resuming" });
    try {
      const completed = await receiveEvents(
        workflow.queryRunId,
        workflow.lastEventSequence,
        owner,
      );
      if (completed) {
        requestKeys.current.delete("query");
      }
    } catch (error) {
      if (requests.isCurrent(owner)) {
        dispatchFailure(dispatch, error, client);
      }
    }
  }

  async function receiveEvents(
    queryRunId: string,
    afterSequence: number,
    owner: ReturnType<QueryRequestScope["replace"]>,
  ) {
    const events = await client.listQueryEvents(
      queryRunId,
      afterSequence,
      owner.signal,
    );
    if (!requests.isCurrent(owner)) {
      return false;
    }
    for (const event of events) {
      dispatch({ type: "terminal-event", event });
    }
    const completed = events.some((event) => event.terminal);
    if (!completed) {
      throw new QueryApiError(
        503,
        "EVENT_STREAM_INCOMPLETE",
        "结果连接已中断，可以从最近进度继续接收",
        "",
      );
    }
    return true;
  }

  async function signOut() {
    requests.cancel();
    requestKeys.current.clear();
    setQuestion("");
    dispatch({ type: "signed-out" });
    try {
      await client.logout();
    } catch {
      client.discardSession();
    }
  }

  async function submitFeedback(
    outcome: QueryFeedbackOutcome,
    reason: QueryFeedbackReason,
  ) {
    if (workflow.queryRunId === undefined) {
      return;
    }
    const owner = requests.replace();
    dispatch({ type: "feedback-submitting", outcome, reason });
    try {
      await client.recordQueryFeedback(
        workflow.queryRunId,
        outcome,
        reason,
        owner.signal,
      );
      if (requests.isCurrent(owner)) {
        dispatch({ type: "feedback-recorded" });
      }
    } catch (error) {
      if (!requests.isCurrent(owner)) {
        return;
      }
      if (error instanceof QueryApiError && error.status === 401) {
        client.discardSession();
        dispatch({
          type: "failed",
          code: error.code,
          message: error.message,
          traceId: error.traceId,
        });
        return;
      }
      if (
        error instanceof QueryApiError
        && error.code === "TRUSTED_QUERY.CONFLICT.0004"
      ) {
        dispatch({ type: "feedback-already-recorded" });
        return;
      }
      dispatch({
        type: "feedback-failed",
        message: "反馈暂时未提交，请稍后重试。",
        traceId: error instanceof QueryApiError ? error.traceId : "",
      });
    }
  }

  if (workflow.stage === "loading-catalog") {
    return <CatalogSkeleton />;
  }

  if (
    workflow.stage === "failed"
    && workflow.failureContext === "catalog"
  ) {
    return (
      <div className="query-state-stack">
        <StatePanel
          kind="error"
          title="已登录，但无法加载助手"
          description={formatFeedback(
            workflow.feedback,
            "请检查网络后重新加载。",
          )}
          actionLabel="重新加载助手"
          onAction={() => void reloadCatalog()}
          icon={<Warning24Regular aria-hidden />}
        />
        <Button appearance="secondary" onClick={() => void signOut()}>
          退出当前账户
        </Button>
      </div>
    );
  }

  if (workflow.stage === "session-expired") {
    return (
      <StatePanel
        kind="stale"
        title="登录会话已经失效"
        description="当前页面已清除会话和助手数据，请返回统一登录入口。"
        actionLabel="返回登录入口"
        onAction={() => window.location.assign("/")}
        icon={<LockClosed24Regular aria-hidden />}
      />
    );
  }

  if (workflow.stage === "denied") {
    return (
      <StatePanel
        kind="denied"
        title="当前账户不能使用可信问答"
        description="需要同时具备助手查询和知识读取权限。页面不会显示无权资源是否存在。"
        actionLabel="退出当前账户"
        onAction={() => void signOut()}
        icon={<LockClosed24Regular aria-hidden />}
      />
    );
  }

  if (workflow.stage === "empty") {
    return (
      <div className="query-state-stack">
        <StatePanel
          kind="empty"
          title="当前没有可查询的已发布助手"
          description="管理员完成知识索引、助手版本冻结和发布后，这里才会开放提问。"
          icon={<BookInformation24Regular aria-hidden />}
        />
        <Button appearance="secondary" onClick={() => void signOut()}>
          退出当前账户
        </Button>
      </div>
    );
  }

  const selectedAssistant = workflow.assistants.find(
    (assistant) =>
      assistant.assistantVersionId
      === workflow.selectedAssistantVersionId,
  );
  return (
    <section className="query-workspace" aria-live="polite">
      <header className="query-session-bar">
        <div>
          <Text weight="semibold">查询会话已受保护</Text>
          <Text size={200}>
            凭据仅由 HttpOnly cookie 承载，当前页面不保存 token
          </Text>
        </div>
        <Button appearance="subtle" onClick={() => void signOut()}>
          退出登录
        </Button>
      </header>

      <div className={queryMainLayoutClassName(workflow.stage)}>
        <div className="query-task">
          <div className="query-task-heading">
            <Title3>向已发布助手提问</Title3>
            <Text>
              系统只会展示经过授权和引用校验的最终回答。
            </Text>
          </div>

          <form
            className="query-form"
            onSubmit={(event) => {
              event.preventDefault();
              void ask();
            }}
          >
            <Field label="已发布助手" required>
              <Dropdown
                value={selectedAssistant?.displayName ?? ""}
                selectedOptions={
                  workflow.selectedAssistantVersionId === undefined
                    ? []
                    : [workflow.selectedAssistantVersionId]
                }
                disabled={workflow.stage === "querying"}
                onOptionSelect={(_, data) => {
                  if (data.optionValue !== undefined) {
                    dispatch({
                      type: "assistant-selected",
                      assistantVersionId: data.optionValue,
                    });
                  }
                }}
              >
                {workflow.assistants.map((assistant) => (
                  <Option
                    key={assistant.assistantVersionId}
                    value={assistant.assistantVersionId}
                    text={`${assistant.displayName}，版本 ${assistant.versionNumber}`}
                  >
                    {assistant.displayName}
                    <Text size={200}>
                      版本 {assistant.versionNumber}
                    </Text>
                  </Option>
                ))}
              </Dropdown>
            </Field>
            <div className="query-assistant-pagination" aria-label="已发布助手分页">
              <Text size={200}>已发布助手共 {workflow.assistantTotal} 个，当前第 {assistantPage} 页。</Text>
              <div>
                <Button
                  appearance="secondary"
                  disabled={workflow.stage === "querying" || !workflow.assistantHasPrevious}
                  onClick={() => onAssistantPageChange?.(assistantPage - 1)}
                >
                  上一页
                </Button>
                <Button
                  appearance="secondary"
                  disabled={workflow.stage === "querying" || !workflow.assistantHasNext}
                  onClick={() => onAssistantPageChange?.(assistantPage + 1)}
                >
                  下一页
                </Button>
              </div>
            </div>

            <Field
              label="问题"
              hint={`${question.length} / 2000 个字符`}
              required
            >
              <Textarea
                value={question}
                maxLength={2000}
                resize="vertical"
                disabled={workflow.stage === "querying"}
                placeholder="请输入需要从已授权资料中核验的问题"
                onChange={(_, data) => setQuestion(data.value)}
              />
            </Field>

            <Button
              appearance="primary"
              type="submit"
              disabled={
                workflow.stage === "querying"
                || workflow.selectedAssistantVersionId === undefined
                || question.trim().length === 0
              }
            >
              {workflow.stage === "querying"
                ? "正在核验证据"
                : "提交问题"}
            </Button>
          </form>

          {selectedAssistant ? (
            <div className="assistant-fact">
              <Badge appearance="tint" color="informative">
                已发布
              </Badge>
              <Text>
                {selectedAssistant.displayName}，版本{" "}
                {selectedAssistant.versionNumber}
              </Text>
            </div>
          ) : null}
        </div>

        <div className="query-result-region">
          <QueryResult
            workflow={workflow}
            onRetry={workflow.queryRunId === undefined ? ask : resume}
            onReloadCatalog={reloadCatalog}
            onSubmitFeedback={submitFeedback}
          />
        </div>
      </div>
    </section>
  );
}

function CatalogSkeleton() {
  return (
    <section
      className="query-catalog-skeleton"
      aria-busy="true"
      aria-label="正在加载已发布助手"
    >
      <Skeleton>
        <SkeletonItem className="skeleton-heading" />
        <SkeletonItem className="skeleton-field" />
        <SkeletonItem className="skeleton-answer" />
      </Skeleton>
    </section>
  );
}

function QueryResult({
  workflow,
  onRetry,
  onReloadCatalog,
  onSubmitFeedback,
}: {
  workflow: ReturnType<typeof reduceQueryWorkflow>;
  onRetry: () => Promise<void>;
  onReloadCatalog: () => Promise<void>;
  onSubmitFeedback: (
    outcome: QueryFeedbackOutcome,
    reason: QueryFeedbackReason,
  ) => Promise<void>;
}) {
  if (workflow.stage === "querying") {
    return (
      <MessageBar intent="info">
        <MessageBarBody>
          <MessageBarTitle>正在查找并核验授权证据</MessageBarTitle>
          只有引用验证完成后，回答才会显示。
        </MessageBarBody>
      </MessageBar>
    );
  }

  if (workflow.stage === "answered" && workflow.answer !== undefined) {
    return (
      <article className="query-answer" aria-labelledby="answer-title">
        <div className="answer-heading">
          <CheckmarkCircle24Filled aria-hidden />
          <Title3 id="answer-title">可信回答</Title3>
        </div>
        <p className="answer-body">{workflow.answer}</p>
        <CitationList citations={workflow.citations} />
        <QueryFeedbackPanel
          feedback={workflow.feedbackSubmission}
          onSubmit={onSubmitFeedback}
        />
      </article>
    );
  }

  if (workflow.stage === "refused") {
    return (
      <section className="query-refusal">
        <Warning24Regular aria-hidden />
        <Title3>没有足够的授权证据</Title3>
        <Text>
          没有找到能够回答该问题的已授权且有效的信息。可以换一种问法，
          或联系知识管理员补充资料。
        </Text>
        <QueryFeedbackPanel
          feedback={workflow.feedbackSubmission}
          onSubmit={onSubmitFeedback}
        />
      </section>
    );
  }

  if (workflow.stage === "failed" && workflow.feedback) {
    return (
      <section className="query-failure">
        <MessageBar intent="error">
          <MessageBarBody>
            <MessageBarTitle>查询未能完成</MessageBarTitle>
            {workflow.feedback.message}
            {workflow.feedback.traceId.length > 0
              ? `，追踪编号：${workflow.feedback.traceId}`
              : ""}
          </MessageBarBody>
        </MessageBar>
        <div className="query-recovery-actions">
          <Button appearance="primary" onClick={() => void onRetry()}>
            {workflow.queryRunId === undefined
              ? "重试提问"
              : "继续接收结果"}
          </Button>
          <Button
            appearance="secondary"
            onClick={() => void onReloadCatalog()}
          >
            重新加载助手
          </Button>
        </div>
      </section>
    );
  }

  return null;
}

function QueryFeedbackPanel({
  feedback,
  onSubmit,
}: {
  feedback: ReturnType<typeof reduceQueryWorkflow>["feedbackSubmission"];
  onSubmit: (
    outcome: QueryFeedbackOutcome,
    reason: QueryFeedbackReason,
  ) => Promise<void>;
}) {
  if (feedback?.status === "recorded") {
    return (
      <MessageBar intent="success">
        <MessageBarBody>
          <MessageBarTitle>反馈已记录</MessageBarTitle>
          {feedback.message}
        </MessageBarBody>
      </MessageBar>
    );
  }
  const submitting = feedback?.status === "submitting";
  return (
    <section className="query-feedback" aria-label="查询反馈">
      <div>
        <Title3>这次查询是否有帮助？</Title3>
        <Text>请选择最符合结果的一项；不会提交问题、回答或自由文本。</Text>
      </div>
      {feedback?.status === "failed" ? (
        <MessageBar intent="error">
          <MessageBarBody>
            <MessageBarTitle>反馈未提交</MessageBarTitle>
            {feedback.traceId && feedback.traceId.length > 0
              ? `${feedback.message}，追踪编号：${feedback.traceId}`
              : feedback.message}
          </MessageBarBody>
        </MessageBar>
      ) : null}
      <div className="query-feedback-actions">
        <Button
          appearance="primary"
          disabled={submitting}
          onClick={() => void onSubmit("POSITIVE", "HELPFUL")}
        >
          有帮助
        </Button>
        <Button
          appearance="secondary"
          disabled={submitting}
          onClick={() => void onSubmit("NEGATIVE", "INCORRECT")}
        >
          不正确
        </Button>
        <Button
          appearance="secondary"
          disabled={submitting}
          onClick={() => void onSubmit("NEGATIVE", "INSUFFICIENT")}
        >
          证据不足
        </Button>
        <Button
          appearance="secondary"
          disabled={submitting}
          onClick={() => void onSubmit("NEGATIVE", "IRRELEVANT")}
        >
          不相关
        </Button>
      </div>
    </section>
  );
}

function CitationList({
  citations,
}: {
  citations: ReturnType<typeof reduceQueryWorkflow>["citations"];
}) {
  if (citations.length === 0) {
    return null;
  }
  return (
    <section className="citation-section" aria-labelledby="citation-title">
      <Title3 id="citation-title">核验来源</Title3>
      <ol className="citation-list">
        {citations.map((citation) => (
          <li
            id={`source-${citation.sequence}`}
            key={`${citation.dataSourceId}-${citation.sequence}`}
          >
            <div className="citation-heading">
              <Text weight="semibold">来源 {citation.sequence}</Text>
              <Text size={200}>内容版本 {citation.contentVersion}</Text>
            </div>
            <blockquote>{citation.quote}</blockquote>
          </li>
        ))}
      </ol>
    </section>
  );
}

function dispatchFailure(
  dispatch: Dispatch<Parameters<typeof reduceQueryWorkflow>[1]>,
  error: unknown,
  client: QueryClient,
) {
  if (error instanceof DOMException && error.name === "AbortError") {
    return;
  }
  if (error instanceof QueryApiError) {
    if (error.status === 401) {
      client.discardSession();
    }
    dispatch({
      type: "failed",
      code: error.code,
      message: error.message,
      traceId: error.traceId,
    });
    return;
  }
  dispatch({
    type: "failed",
    code: "NETWORK_FAILURE",
    message: "无法连接查询服务，请检查网络后重试",
    traceId: "",
  });
}

type QueryWorkflowFeedback =
  ReturnType<typeof reduceQueryWorkflow>["feedback"];

function formatFeedback(
  feedback: QueryWorkflowFeedback,
  fallback: string,
): string {
  if (feedback === undefined) {
    return fallback;
  }
  return feedback.traceId.length > 0
    ? `${feedback.message}，追踪编号：${feedback.traceId}`
    : feedback.message;
}
