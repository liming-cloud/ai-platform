/// <reference types="node" />
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const knowledgeSelectionFiles = [
  "../governed-data/GovernedFileDataWorkspace.tsx",
  "../governed-data/StructuredDatasetRegistrationWorkspace.tsx",
  "../governed-data/ObjectStorageDataWorkspace.tsx",
  "../knowledge-publication/KnowledgePublicationFlow.tsx",
];

describe("租户知识库显式选择边界", () => {
  it.each(knowledgeSelectionFiles)("%s 不把未命中的知识库静默替换为当前页首项", (sourceFile) => {
    const source = readFileSync(new URL(sourceFile, import.meta.url), "utf8");

    expect(source).not.toContain("page.items[0]?.id");
    expect(source).toContain("resolveKnowledgeSpaceIdOnPage");
  });

  it.each([
    "../governed-data/GovernedFileDataWorkspace.tsx",
    "../governed-data/StructuredDatasetRegistrationWorkspace.tsx",
    "../governed-data/ObjectStorageDataWorkspace.tsx",
  ])("%s 在没有显式选择时保留关闭状态", (sourceFile) => {
    const source = readFileSync(new URL(sourceFile, import.meta.url), "utf8");

    expect(source).toContain('<option value="">请选择知识库</option>');
  });

  it("目录进入子任务也使用统一可恢复路由", () => {
    const source = readFileSync(new URL("./KnowledgeSpaceWorkspace.tsx", import.meta.url), "utf8");

    expect(source).toContain("knowledgeDataTaskRoute(route,");
  });
});
