import { describe, expect, it } from "vitest";
import type { GovernedDataSourceLifecycleResult } from "../../api/control-plane-client";
import { canBuildStructuredDatasetIndex } from "./StructuredDatasetRegistrationWorkspace";

describe("canBuildStructuredDatasetIndex", () => {
  it("只允许仍处于 READY 且已有已发布产物的结构化数据集建立索引", () => {
    const source: GovernedDataSourceLifecycleResult = {
      dataSourceId: "220000000000000001",
      knowledgeSpaceId: "210000000000000001",
      status: "READY",
      version: 2,
      failedOperation: null,
      syncArtifactId: "230000000000000001",
      qualityStatus: "PASS",
    };

    expect(canBuildStructuredDatasetIndex(source, "230000000000000001")).toBe(true);
    expect(canBuildStructuredDatasetIndex(source, undefined)).toBe(false);
    expect(canBuildStructuredDatasetIndex(source, "230000000000000002")).toBe(false);
    expect(canBuildStructuredDatasetIndex({ ...source, status: "FAILED" }, "230000000000000001")).toBe(false);
  });
});
