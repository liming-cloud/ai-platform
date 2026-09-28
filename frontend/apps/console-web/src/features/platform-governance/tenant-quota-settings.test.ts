import { describe, expect, it } from "vitest";
import { quotaFormFrom, quotasFromForm } from "./tenant-quota-settings";

const configured = {
  knowledgeBases: 3, documentsPerKnowledgeBase: 1000,
  bytesPerKnowledgeBase: "10000000000", maxFileBytes: "50000000", maxTextBytes: "1048576", version: 7,
};

describe("租户资源配额输入", () => {
  it("十进制字节和 MiB 以精确值往返", () => {
    const form = quotaFormFrom(configured);
    expect(form).toMatchObject({ storageGb: "10", fileMb: "50", textMib: "1" });
    expect(quotasFromForm(form, { knowledgeBases: 1, documentsPerKnowledgeBase: 1,
      bytesPerKnowledgeBase: "6888", complete: true }, configured)).toEqual({
      knowledgeBases: 3, documentsPerKnowledgeBase: 1000,
      bytesPerKnowledgeBase: "10000000000", maxFileBytes: "50000000", maxTextBytes: "1048576",
    });
  });

  it("拒绝低于已核实用量和不完整计量时的下调", () => {
    const form = quotaFormFrom(configured);
    expect(() => quotasFromForm({ ...form, knowledgeBases: "1" }, {
      knowledgeBases: 2, documentsPerKnowledgeBase: 1, bytesPerKnowledgeBase: "1", complete: true,
    }, configured)).toThrow(/已用量/);
    expect(() => quotasFromForm({ ...form, storageGb: "9" }, {
      knowledgeBases: 0, documentsPerKnowledgeBase: 0, bytesPerKnowledgeBase: "0", complete: false,
    }, configured)).toThrow(/未完整核实/);
  });
});
