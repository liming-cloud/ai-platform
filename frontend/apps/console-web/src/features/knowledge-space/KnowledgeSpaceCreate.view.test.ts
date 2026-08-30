import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import {
  findConfirmedKnowledgeSpace,
  KnowledgeSpaceCreateForm,
  prepareKnowledgeSpaceCreateAttempt,
} from "./KnowledgeSpaceWorkspace";

describe("租户知识库创建", () => {
  it("创建失败在模态框内以可聚焦错误反馈呈现", () => {
    const html = renderToStaticMarkup(createElement(KnowledgeSpaceCreateForm, {
      name: "客户服务知识库",
      description: "当前租户客服资料",
      busy: false,
      failure: {
        title: "新建知识库未完成",
        description: "相关资源已变化或存在同名项，请刷新后核对并重试。",
      },
      onNameChange: () => undefined,
      onDescriptionChange: () => undefined,
      onSubmit: () => undefined,
    }));

    expect(html).toContain('role="alert"');
    expect(html).toContain("新建知识库未完成");
    expect(html).toContain("相关资源已变化或存在同名项");
    expect(html).toContain('aria-invalid="true"');
  });

  it("响应丢失后的相同规范化载荷复用同一幂等键", () => {
    let sequence = 0;
    const identifierFactory = () => {
      sequence += 1;
      return String(sequence);
    };
    const first = prepareKnowledgeSpaceCreateAttempt(
      undefined,
      { name: " 客户服务知识库 ", description: " 当前租户客服资料 " },
      identifierFactory,
    );
    const retry = prepareKnowledgeSpaceCreateAttempt(
      first,
      { name: "客户服务知识库", description: "当前租户客服资料" },
      identifierFactory,
    );

    expect(first.input).toEqual({
      name: "客户服务知识库",
      description: "当前租户客服资料",
    });
    expect(retry.key).toBe(first.key);
    expect(sequence).toBe(1);
  });

  it("失败后修改载荷建立新的幂等尝试", () => {
    let sequence = 0;
    const identifierFactory = () => {
      sequence += 1;
      return String(sequence);
    };
    const first = prepareKnowledgeSpaceCreateAttempt(
      undefined,
      { name: "客户服务知识库", description: "旧说明" },
      identifierFactory,
    );
    const changed = prepareKnowledgeSpaceCreateAttempt(
      first,
      { name: "客户服务知识库", description: "新说明" },
      identifierFactory,
    );

    expect(changed.key).not.toBe(first.key);
    expect(sequence).toBe(2);
  });

  it("可选说明为空时按后端契约发送 null", () => {
    const attempt = prepareKnowledgeSpaceCreateAttempt(
      undefined,
      { name: "客户服务知识库", description: "   " },
      () => "attempt-1",
    );

    expect(attempt.input.description).toBeNull();
  });

  it("不确定响应只用完整载荷回查已建立知识库", () => {
    const spaces = [{
      id: "410000000000000001",
      name: "客户服务知识库",
      description: "当前租户客服资料",
      status: "DRAFT" as const,
      version: 0,
      createdAt: "2026-08-30T03:00:00Z",
    }];

    expect(findConfirmedKnowledgeSpace(spaces, {
      name: "客户服务知识库",
      description: "当前租户客服资料",
    })?.id).toBe("410000000000000001");
    expect(findConfirmedKnowledgeSpace(spaces, {
      name: "客户服务知识库",
      description: "其他说明",
    })).toBeUndefined();
  });
});
