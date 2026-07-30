import { describe, expect, it } from "vitest";
import type { GovernedDataSourceLifecycleResult } from "../../api/control-plane-client";
import { canBuildObjectStorageIndex, canPublishObjectStorageArtifact, canSynchronizeObjectStorage } from "./ObjectStorageDataWorkspace";

describe("对象存储来源控制台状态门禁", () => {
  const source: GovernedDataSourceLifecycleResult = {
    dataSourceId: "220000000000000001",
    knowledgeSpaceId: "210000000000000001",
    status: "READY",
    version: 2,
    failedOperation: null,
    syncArtifactId: "230000000000000001",
    qualityStatus: "PASS",
  };

  it("只使用当前同步产物继续发布和建立索引", () => {
    expect(canSynchronizeObjectStorage({ ...source, syncArtifactId: null })).toBe(true);
    expect(canSynchronizeObjectStorage(source)).toBe(false);
    expect(canPublishObjectStorageArtifact(source, undefined)).toBe(true);
    expect(canPublishObjectStorageArtifact(source, source.syncArtifactId ?? undefined)).toBe(false);
    expect(canBuildObjectStorageIndex(source, source.syncArtifactId ?? undefined)).toBe(true);
    expect(canBuildObjectStorageIndex(source, "230000000000000002")).toBe(false);
  });
});
