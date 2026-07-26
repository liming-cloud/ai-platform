import type {
  ModelUsage,
  QueryAssistant,
  QueryCitation,
  QueryEvent,
} from "../../api/query-client";

export type QueryStage =
  | "login"
  | "authenticating"
  | "loading-catalog"
  | "empty"
  | "ready"
  | "querying"
  | "answered"
  | "refused"
  | "failed"
  | "denied"
  | "session-expired";

export interface QueryWorkflow {
  stage: QueryStage;
  failureContext?: "login" | "catalog" | "query";
  assistants: QueryAssistant[];
  selectedAssistantVersionId?: string;
  question?: string;
  queryRunId?: string;
  answer?: string;
  refusalReason?: string;
  citations: QueryCitation[];
  usage?: ModelUsage;
  lastEventSequence: number;
  feedback?: {
    code: string;
    message: string;
    traceId: string;
  };
}

export type QueryWorkflowEvent =
  | { type: "authenticating" }
  | { type: "loading-catalog" }
  | { type: "catalog-loaded"; assistants: QueryAssistant[] }
  | { type: "assistant-selected"; assistantVersionId: string }
  | { type: "querying"; question: string; queryRunId?: string }
  | { type: "resuming" }
  | { type: "run-created"; queryRunId: string }
  | { type: "terminal-event"; event: QueryEvent }
  | { type: "failed"; code: string; message: string; traceId: string }
  | { type: "retry" }
  | { type: "signed-out" };

export const initialQueryWorkflow: QueryWorkflow = {
  stage: "login",
  assistants: [],
  citations: [],
  lastEventSequence: 0,
};

export function queryMainLayoutClassName(stage: QueryStage): string {
  return ["querying", "answered", "refused", "failed"].includes(stage)
    ? "query-main-layout has-result"
    : "query-main-layout";
}

/**
 * 查询页面单向状态机。只有持久化终态事件可以产生回答或拒答。
 */
export function reduceQueryWorkflow(
  state: QueryWorkflow,
  event: QueryWorkflowEvent,
): QueryWorkflow {
  switch (event.type) {
    case "authenticating":
      return {
        ...initialQueryWorkflow,
        stage: "authenticating",
      };
    case "loading-catalog":
      return {
        ...initialQueryWorkflow,
        stage: "loading-catalog",
      };
    case "catalog-loaded": {
      const assistants = [...event.assistants];
      return {
        ...initialQueryWorkflow,
        stage: assistants.length === 0 ? "empty" : "ready",
        assistants,
        selectedAssistantVersionId:
          assistants[0]?.assistantVersionId,
      };
    }
    case "assistant-selected":
      return state.assistants.some(
        (assistant) =>
          assistant.assistantVersionId === event.assistantVersionId,
      )
        ? {
            ...state,
            selectedAssistantVersionId: event.assistantVersionId,
          }
        : state;
    case "querying":
      return {
        ...state,
        stage: "querying",
        question: event.question,
        queryRunId: event.queryRunId,
        answer: undefined,
        refusalReason: undefined,
        citations: [],
        usage: undefined,
        lastEventSequence: 0,
        feedback: undefined,
      };
    case "resuming":
      return state.queryRunId === undefined
        ? state
        : {
            ...state,
            stage: "querying",
            feedback: undefined,
          };
    case "run-created":
      return state.stage === "querying"
        ? { ...state, queryRunId: event.queryRunId }
        : state;
    case "terminal-event":
      return applyQueryEvent(state, event.event);
    case "failed":
      return {
        ...state,
        stage: failedStage(event.code),
        failureContext: failureContext(state.stage),
        feedback: {
          code: event.code,
          message: event.message,
          traceId: event.traceId,
        },
        assistants: event.code === "AUTHENTICATION_REQUIRED"
          ? []
          : state.assistants,
        selectedAssistantVersionId:
          event.code === "AUTHENTICATION_REQUIRED"
            ? undefined
            : state.selectedAssistantVersionId,
      };
    case "retry":
      return state.failureContext === "login"
        ? initialQueryWorkflow
        : state.failureContext === "catalog"
          ? { ...initialQueryWorkflow, stage: "loading-catalog" }
        : {
            ...state,
            stage: "ready",
            failureContext: undefined,
            feedback: undefined,
          };
    case "signed-out":
      return initialQueryWorkflow;
  }
}

export interface QueryRequestOwner {
  revision: number;
  signal: AbortSignal;
}

/**
 * 为登录身份拥有的异步请求提供取消和迟到结果判定。
 */
export class QueryRequestScope {
  private revision = 0;
  private controller: AbortController | undefined;

  replace(): QueryRequestOwner {
    this.controller?.abort();
    this.revision += 1;
    this.controller = new AbortController();
    return {
      revision: this.revision,
      signal: this.controller.signal,
    };
  }

  cancel(): void {
    this.controller?.abort();
    this.controller = undefined;
    this.revision += 1;
  }

  isCurrent(owner: QueryRequestOwner): boolean {
    return !owner.signal.aborted
      && owner.revision === this.revision
      && owner.signal === this.controller?.signal;
  }
}

/**
 * 相同助手版本和问题在安全重试时复用幂等键。
 */
export function stableQueryRequestKey(
  registry: Map<string, { fingerprint: string; key: string }>,
  assistantVersionId: string,
  question: string,
): string {
  const fingerprint = JSON.stringify([assistantVersionId, question]);
  const existing = registry.get("query");
  if (existing?.fingerprint === fingerprint) {
    return existing.key;
  }
  const created = {
    fingerprint,
    key: crypto.randomUUID(),
  };
  registry.set("query", created);
  return created.key;
}

function applyQueryEvent(
  state: QueryWorkflow,
  event: QueryEvent,
): QueryWorkflow {
  if (event.sequence <= state.lastEventSequence) {
    return state;
  }
  if (!event.terminal) {
    return {
      ...state,
      lastEventSequence: event.sequence,
    };
  }
  if (event.status === "ANSWERED" && event.answer !== null) {
    return {
      ...state,
      stage: "answered",
      answer: event.answer,
      citations: [...event.citations],
      usage: event.usage,
      lastEventSequence: event.sequence,
      feedback: undefined,
    };
  }
  if (event.status === "REFUSED" && event.refusalReason !== null) {
    return {
      ...state,
      stage: "refused",
      refusalReason: event.refusalReason,
      citations: [],
      usage: event.usage,
      lastEventSequence: event.sequence,
      feedback: undefined,
    };
  }
  return {
    ...state,
    stage: "failed",
    failureContext: "query",
    citations: [],
    lastEventSequence: event.sequence,
    feedback: {
      code: "QUERY_FAILED",
      message: "查询未能完成，请稍后重试",
      traceId: "",
    },
  };
}

function failureContext(
  stage: QueryStage,
): NonNullable<QueryWorkflow["failureContext"]> {
  if (stage === "login" || stage === "authenticating") {
    return "login";
  }
  if (
    stage === "loading-catalog"
    || stage === "empty"
  ) {
    return "catalog";
  }
  return "query";
}

function failedStage(code: string): QueryStage {
  if (code === "ACCESS_DENIED") {
    return "denied";
  }
  if (code === "AUTHENTICATION_REQUIRED") {
    return "session-expired";
  }
  return "failed";
}
