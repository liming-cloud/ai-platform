import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { SpacePicker } from "./PublicationViews";

const baseProps = {
  selectedId: "",
  busy: false,
  page: 1,
  total: 0,
  hasPrevious: false,
  hasNext: false,
  onSelected: () => undefined,
  onPrevious: () => undefined,
  onNext: () => undefined,
  onContinue: () => undefined,
  onManage: () => undefined,
};

describe("知识发布快速入门的知识库选择", () => {
  it("空目录引导租户管理员返回知识库目录建库", () => {
    const html = renderToStaticMarkup(createElement(SpacePicker, {
      ...baseProps,
      spaces: [],
    }));

    expect(html).toContain("尚未建立知识库");
    expect(html).toContain("返回知识库目录");
    expect(html).not.toContain("创建知识空间");
  });

  it("只允许从当前租户已有知识库中选择", () => {
    const html = renderToStaticMarkup(createElement(SpacePicker, {
      ...baseProps,
      selectedId: "space-1",
      total: 1,
      spaces: [{
        id: "space-1",
        name: "产品知识库",
        description: "当前租户维护",
        status: "DRAFT",
        version: 0,
        createdAt: "2026-08-30T03:00:00Z",
      }],
    }));

    expect(html).toContain("选择知识库");
    expect(html).toContain("使用选中知识库");
    expect(html).toContain("管理知识库");
    expect(html).not.toContain("创建新空间");
  });
});
