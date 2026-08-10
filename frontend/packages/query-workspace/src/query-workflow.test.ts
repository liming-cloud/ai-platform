import { describe, expect, it } from "vitest";
import {
  QueryRequestScope,
  initialQueryWorkflow,
  reduceQueryWorkflow,
  stableQueryRequestKey,
} from "./query-workflow";

describe("查询站点状态机", () => {
  it("只沿登录、助手目录、提问和可信终态主线推进", () => {
    const authenticating = reduceQueryWorkflow(initialQueryWorkflow, {
      type: "authenticating",
    });
    const ready = reduceQueryWorkflow(authenticating, {
      type: "catalog-loaded",
      assistants: [{
        assistantId: "assistant-001",
        displayName: "门店导购助手",
        assistantVersionId: "version-001",
        versionNumber: 1,
        publishedAt: "2026-07-27T06:00:00Z",
      }],
    });
    const querying = reduceQueryWorkflow(ready, {
      type: "querying",
      question: "牛奶在哪里？",
      queryRunId: "run-001",
    });
    const answered = reduceQueryWorkflow(querying, {
      type: "terminal-event",
      event: {
        sequence: 2,
        type: "QUERY_ANSWERED",
        status: "ANSWERED",
        answer: "牛奶位于一层。",
        refusalReason: null,
        citations: [],
        usage: { inputTokens: 1, outputTokens: 1, totalTokens: 2 },
        occurredAt: "2026-07-27T06:00:00Z",
        terminal: true,
      },
    });

    expect(authenticating.stage).toBe("authenticating");
    expect(ready.stage).toBe("ready");
    expect(ready.selectedAssistantVersionId).toBe("version-001");
    expect(querying.stage).toBe("querying");
    expect(answered.stage).toBe("answered");
    expect(answered.answer).toBe("牛奶位于一层。");
    expect(answered.lastEventSequence).toBe(2);
  });

  it("明确区分空目录、拒答、无权限和会话失效", () => {
    const empty = reduceQueryWorkflow(
      { ...initialQueryWorkflow, stage: "loading-catalog" },
      { type: "catalog-loaded", assistants: [] },
    );
    const refused = reduceQueryWorkflow(
      {
        ...initialQueryWorkflow,
        stage: "querying",
        selectedAssistantVersionId: "version-001",
        queryRunId: "run-001",
      },
      {
        type: "terminal-event",
        event: {
          sequence: 2,
          type: "QUERY_REFUSED",
          status: "REFUSED",
          answer: null,
          refusalReason: "INSUFFICIENT_EVIDENCE",
          citations: [],
          usage: { inputTokens: 0, outputTokens: 0, totalTokens: 0 },
          occurredAt: "2026-07-27T06:00:00Z",
          terminal: true,
        },
      },
    );
    const denied = reduceQueryWorkflow(
      { ...initialQueryWorkflow, stage: "loading-catalog" },
      {
        type: "failed",
        code: "TRUSTED_QUERY.AUTHORIZATION.0001",
        message: "没有权限",
        traceId: "trace-denied",
      },
    );
    const expired = reduceQueryWorkflow(
      { ...initialQueryWorkflow, stage: "ready" },
      {
        type: "failed",
        code: "PLATFORM_SECURITY.AUTHENTICATION.0001",
        message: "会话已失效",
        traceId: "trace-expired",
      },
    );

    expect(empty.stage).toBe("empty");
    expect(refused.stage).toBe("refused");
    expect(denied.stage).toBe("denied");
    expect(expired.stage).toBe("session-expired");
  });

  it("保留登录、目录和查询三类失败上下文并给出正确恢复入口", () => {
    const loginFailed = reduceQueryWorkflow(
      { ...initialQueryWorkflow, stage: "authenticating" },
      {
        type: "failed",
        code: "INVALID_CREDENTIALS",
        message: "用户名或密码错误",
        traceId: "trace-login",
      },
    );
    const catalogFailed = reduceQueryWorkflow(
      { ...initialQueryWorkflow, stage: "loading-catalog" },
      {
        type: "failed",
        code: "NETWORK_FAILURE",
        message: "目录加载失败",
        traceId: "trace-catalog",
      },
    );
    const queryFailed = reduceQueryWorkflow(
      {
        ...initialQueryWorkflow,
        stage: "querying",
        assistants: [{
          assistantId: "assistant-001",
          displayName: "门店导购助手",
          assistantVersionId: "version-001",
          versionNumber: 1,
          publishedAt: "2026-07-27T06:00:00Z",
        }],
        selectedAssistantVersionId: "version-001",
      },
      {
        type: "failed",
        code: "NETWORK_FAILURE",
        message: "查询失败",
        traceId: "trace-query",
      },
    );

    expect(loginFailed.failureContext).toBe("login");
    expect(catalogFailed.failureContext).toBe("catalog");
    expect(queryFailed.failureContext).toBe("query");
    expect(reduceQueryWorkflow(loginFailed, { type: "retry" }).stage)
      .toBe("login");
    expect(reduceQueryWorkflow(catalogFailed, { type: "retry" }).stage)
      .toBe("loading-catalog");
    expect(reduceQueryWorkflow(queryFailed, { type: "retry" }).stage)
      .toBe("ready");
  });

  it("身份替换和注销会取消旧请求且旧拥有者不能提交结果", () => {
    const scope = new QueryRequestScope();
    const first = scope.replace();
    const second = scope.replace();

    expect(first.signal.aborted).toBe(true);
    expect(scope.isCurrent(first)).toBe(false);
    expect(scope.isCurrent(second)).toBe(true);

    scope.cancel();
    expect(second.signal.aborted).toBe(true);
    expect(scope.isCurrent(second)).toBe(false);
  });

  it("相同助手和问题的安全重试复用幂等键", () => {
    const registry = new Map<
      string,
      { fingerprint: string; key: string }
    >();
    const first = stableQueryRequestKey(
      registry,
      "version-001",
      "牛奶在哪里？",
    );
    const retry = stableQueryRequestKey(
      registry,
      "version-001",
      "牛奶在哪里？",
    );
    const changed = stableQueryRequestKey(
      registry,
      "version-001",
      "面包在哪里？",
    );

    expect(retry).toBe(first);
    expect(changed).not.toBe(first);
  });

  it("反馈状态只绑定当前终态查询并在下一次查询时清除", () => {
    const terminal = reduceQueryWorkflow(
      {
        ...initialQueryWorkflow,
        stage: "answered",
        queryRunId: "430000000000000001",
      },
      {
        type: "feedback-submitting",
        outcome: "NEGATIVE",
        reason: "INSUFFICIENT",
      },
    );
    const recorded = reduceQueryWorkflow(terminal, {
      type: "feedback-recorded",
    });
    const nextQuery = reduceQueryWorkflow(recorded, {
      type: "querying",
      question: "下一题",
    });

    expect(terminal.feedbackSubmission?.status).toBe("submitting");
    expect(recorded.feedbackSubmission?.status).toBe("recorded");
    expect(nextQuery.feedbackSubmission).toBeUndefined();
  });
});
