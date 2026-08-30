/// <reference types="node" />
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const flowPath = new URL("./KnowledgePublicationFlow.tsx", import.meta.url).pathname;

describe("知识发布快速入门边界", () => {
  it("不在向导内部创建知识库或退出全局会话", () => {
    const source = readFileSync(flowPath, "utf8");

    expect(source).not.toContain("createKnowledgeSpace");
    expect(source).not.toContain("SpaceForm");
    expect(source).not.toContain("client.logout");
    expect(source).toContain('onReturn={() => onRouteChange(knowledgeDataTaskRoute(route, "knowledge-bases"))}');
    expect(source).not.toContain('onReturn={() => onRouteChange({');
  });
});
