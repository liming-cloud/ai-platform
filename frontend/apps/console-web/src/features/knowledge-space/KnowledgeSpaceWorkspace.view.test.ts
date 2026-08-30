import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { KnowledgeSpaceDirectoryView } from "./KnowledgeSpaceWorkspace";

describe("租户知识库目录", () => {
  it("明确当前租户所有权并展示可进入的知识库", () => {
    const html = renderToStaticMarkup(createElement(KnowledgeSpaceDirectoryView, {
      spaces: [{
        id: "410000000000000001",
        name: "客户服务知识库",
        description: "只供当前租户客服助手使用",
        status: "DRAFT",
        version: 0,
        createdAt: "2026-08-30T03:00:00Z",
      }],
      page: 1,
      total: 1,
      hasPrevious: false,
      hasNext: false,
      busy: false,
      onCreateRequest: () => undefined,
      onEnter: () => undefined,
      onPageChange: () => undefined,
    }));

    expect(html).toContain("当前租户专属");
    expect(html).toContain("客户服务知识库");
    expect(html).toContain("只供当前租户客服助手使用");
    expect(html).toContain("进入知识库");
    expect(html).not.toContain("tenantId");
    expect(html).not.toContain("平台知识库");
  });

  it("空目录给出租户管理员可执行的建库入口", () => {
    const html = renderToStaticMarkup(createElement(KnowledgeSpaceDirectoryView, {
      spaces: [],
      page: 1,
      total: 0,
      hasPrevious: false,
      hasNext: false,
      busy: false,
      onCreateRequest: () => undefined,
      onEnter: () => undefined,
      onPageChange: () => undefined,
    }));

    expect(html).toContain("还没有知识库");
    expect(html).toContain("新建知识库");
    expect(html).not.toContain("首份文本发布");
  });

  it("目录分页说明来自服务端事实", () => {
    const html = renderToStaticMarkup(createElement(KnowledgeSpaceDirectoryView, {
      spaces: [],
      page: 2,
      total: 37,
      hasPrevious: true,
      hasNext: true,
      busy: false,
      onCreateRequest: () => undefined,
      onEnter: () => undefined,
      onPageChange: () => undefined,
    }));

    expect(html).toContain("第 2 页");
    expect(html).toContain("共 37 个知识库");
    expect(html).toContain("上一页");
    expect(html).toContain("下一页");
  });
});
