export type PublicationStage =
  | "login"
  | "space"
  | "content"
  | "publish"
  | "index"
  | "complete";

export type FeedbackKind =
  | "error"
  | "denied"
  | "partial"
  | "stale";

export interface PublicationWorkflow {
  stage: PublicationStage;
  busy: boolean;
  space?: {
    id: string;
    name: string;
  };
  source?: {
    id: string;
    name: string;
    version: number;
  };
  indexVersion?: {
    id: string;
    status: "BUILDING" | "READY" | "ACTIVE" | "FAILED";
    version: number;
    chunkCount: number;
    failureCode: string | null;
  };
  feedback?: {
    kind: FeedbackKind;
    message: string;
    traceId: string;
  };
}

export type PublicationEvent =
  | { type: "submitting" }
  | { type: "authenticated" }
  | { type: "space-ready"; spaceId: string; spaceName: string }
  | {
      type: "source-ready";
      sourceId: string;
      sourceName: string;
      version: number;
    }
  | { type: "content-ready"; version: number }
  | { type: "source-published"; version: number }
  | {
      type: "index-updated";
      indexVersion: NonNullable<PublicationWorkflow["indexVersion"]>;
    }
  | {
      type: "index-published";
      indexVersion: NonNullable<PublicationWorkflow["indexVersion"]>;
    }
  | {
      type: "failed";
      code: string;
      message: string;
      traceId: string;
    }
  | { type: "retry" }
  | { type: "signed-out" };

export const initialWorkflow: PublicationWorkflow = {
  stage: "login",
  busy: false,
};

/**
 * 知识发布的单向状态机，失败只改变反馈，不暗中推进业务阶段。
 */
export function reduceWorkflow(
  state: PublicationWorkflow,
  event: PublicationEvent,
): PublicationWorkflow {
  switch (event.type) {
    case "submitting":
      return { ...state, busy: true, feedback: undefined };
    case "authenticated":
      return { stage: "space", busy: false };
    case "space-ready":
      return {
        stage: "content",
        busy: false,
        space: { id: event.spaceId, name: event.spaceName },
      };
    case "source-ready":
      return {
        ...state,
        stage: "content",
        busy: false,
        source: {
          id: event.sourceId,
          name: event.sourceName,
          version: event.version,
        },
        feedback: undefined,
      };
    case "content-ready":
      return state.source === undefined
        ? state
        : {
            ...state,
            stage: "publish",
            busy: false,
            source: { ...state.source, version: event.version },
            feedback: undefined,
          };
    case "source-published":
      return state.source === undefined
        ? state
        : {
            ...state,
            stage: "index",
            busy: false,
            source: { ...state.source, version: event.version },
            feedback: undefined,
          };
    case "index-updated":
      return {
        ...state,
        stage: "index",
        busy: false,
        indexVersion: event.indexVersion,
        feedback: undefined,
      };
    case "index-published":
      return event.indexVersion.status !== "ACTIVE"
        ? state
        : {
            ...state,
            stage: "complete",
            busy: false,
            indexVersion: event.indexVersion,
            feedback: undefined,
          };
    case "failed":
      return {
        ...state,
        busy: false,
        feedback: {
          kind: feedbackKind(event.code, state.stage),
          message: event.message,
          traceId: event.traceId,
        },
      };
    case "retry":
      return { ...state, busy: false, feedback: undefined };
    case "signed-out":
      return initialWorkflow;
  }
}

function feedbackKind(
  code: string,
  stage: PublicationStage,
): FeedbackKind {
  if (code === "KNOWLEDGE_DATA.AUTHORIZATION.0001") {
    return "denied";
  }
  if (code === "DATA_SOURCE_VERSION_CONFLICT") {
    return "stale";
  }
  if (stage === "publish" || stage === "index") {
    return "partial";
  }
  return "error";
}

/**
 * 同一操作和载荷在重试时复用幂等键，载荷变化后生成新键。
 */
export function stableRequestKey(
  registry: Map<string, { fingerprint: string; key: string }>,
  operation: string,
  payload: unknown,
): string {
  const fingerprint = JSON.stringify(payload);
  const existing = registry.get(operation);
  if (existing?.fingerprint === fingerprint) {
    return existing.key;
  }
  const created = {
    fingerprint,
    key: crypto.randomUUID(),
  };
  registry.set(operation, created);
  return created.key;
}
