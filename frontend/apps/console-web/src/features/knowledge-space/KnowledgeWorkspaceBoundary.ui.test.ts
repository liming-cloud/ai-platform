/// <reference types="node" />
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const sourceFiles = [
  "../governed-data/GovernedFileDataWorkspace.tsx",
  "../governed-data/StructuredDatasetRegistrationWorkspace.tsx",
  "../governed-data/ObjectStorageDataWorkspace.tsx",
  "../knowledge-publication/PublicationViews.tsx",
  "../operations/OperationsOverviewWorkspace.tsx",
];

describe("租户知识库工作区一致性", () => {
  it.each(sourceFiles)("%s 使用知识库术语且退出只由全局账户菜单承载", (sourceFile) => {
    const source = readFileSync(new URL(sourceFile, import.meta.url), "utf8");

    expect(source).not.toContain("知识空间");
    expect(source).not.toContain("首份文本发布");
    expect(source).not.toContain("退出登录");
  });
});
