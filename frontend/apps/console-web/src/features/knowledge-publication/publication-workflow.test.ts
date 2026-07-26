import { describe, expect, it } from "vitest";
import {
  initialWorkflow,
  reduceWorkflow,
  stableRequestKey,
} from "./publication-workflow";

describe("知识发布流程", () => {
  it("只沿登录知识空间内容发布的主线前进", () => {
    const authenticated = reduceWorkflow(initialWorkflow, {
      type: "authenticated",
    });
    const spaceReady = reduceWorkflow(authenticated, {
      type: "space-ready",
      spaceId: "space-001",
      spaceName: "门店商品知识",
    });
    const sourceReady = reduceWorkflow(spaceReady, {
      type: "source-ready",
      sourceId: "source-001",
      sourceName: "商品说明",
      version: 0,
    });
    const contentReady = reduceWorkflow(sourceReady, {
      type: "content-ready",
      version: 1,
    });
    const published = reduceWorkflow(contentReady, {
      type: "published",
      version: 2,
    });

    expect(authenticated.stage).toBe("space");
    expect(spaceReady.stage).toBe("content");
    expect(sourceReady.source?.version).toBe(0);
    expect(contentReady.stage).toBe("publish");
    expect(published.stage).toBe("complete");
  });

  it.each([
    ["ACCESS_DENIED", "denied"],
    ["DATA_SOURCE_VERSION_CONFLICT", "stale"],
    ["OBJECT_STORAGE_UNAVAILABLE", "partial"],
    ["UNEXPECTED_FAILURE", "partial"],
  ] as const)("把 %s 映射为 %s 状态", (code, expected) => {
    const state = reduceWorkflow(
      {
        ...initialWorkflow,
        stage: "publish",
        source: {
          id: "source-001",
          name: "商品说明",
          version: 1,
        },
      },
      {
        type: "failed",
        code,
        message: "操作失败",
        traceId: "trace-001",
      },
    );

    expect(state.feedback?.kind).toBe(expected);
    expect(state.stage).toBe("publish");
  });

  it("内容阶段的普通失败保持为可重试错误", () => {
    const state = reduceWorkflow(
      { ...initialWorkflow, stage: "content" },
      {
        type: "failed",
        code: "UNEXPECTED_FAILURE",
        message: "操作失败",
        traceId: "trace-002",
      },
    );

    expect(state.feedback?.kind).toBe("error");
    expect(state.stage).toBe("content");
  });

  it("相同操作和载荷复用幂等键，载荷变化后换键", () => {
    const registry = new Map<
      string,
      { fingerprint: string; key: string }
    >();
    const first = stableRequestKey(registry, "upload", {
      sourceId: "source-001",
      content: "第一版",
    });
    const replay = stableRequestKey(registry, "upload", {
      sourceId: "source-001",
      content: "第一版",
    });
    const changed = stableRequestKey(registry, "upload", {
      sourceId: "source-001",
      content: "第二版",
    });

    expect(replay).toBe(first);
    expect(changed).not.toBe(first);
  });
});
