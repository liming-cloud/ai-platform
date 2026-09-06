import test from "node:test";
import assert from "node:assert/strict";
import { createMemoryStorage, createPrototypeStore, DEFAULT_TENANT_ID } from "../prototype-state.js";

const ACTOR = "account-qiao";
const fresh = () => {
  const storage = createMemoryStorage();
  return { storage, store: createPrototypeStore(storage) };
};

test("租户管理员创建知识库后刷新恢复，重复名称、越权和配额超限失败关闭", () => {
  const { storage, store } = fresh();
  const created = store.createKnowledgeBase(
    { name: "服务运营手册", description: "面向一线服务团队的已审核流程。" },
    ACTOR,
    DEFAULT_TENANT_ID,
  );
  assert.equal(created.ok, true);
  assert.equal(created.knowledgeBase.status, "EMPTY");
  assert.equal(createPrototypeStore(storage).getSnapshot().knowledgeBases.at(-1).name, "服务运营手册");

  const before = store.getSnapshot();
  assert.equal(store.createKnowledgeBase({ name: "服务运营手册" }, ACTOR, DEFAULT_TENANT_ID).reason, "KNOWLEDGE_BASE_NAME_CONFLICT");
  assert.equal(store.createKnowledgeBase({ name: "第四个知识库" }, ACTOR, DEFAULT_TENANT_ID).reason, "KNOWLEDGE_BASE_QUOTA_EXCEEDED");
  assert.equal(store.createKnowledgeBase({ name: "越权知识库" }, "account-shen", DEFAULT_TENANT_ID).reason, "FORBIDDEN");
  assert.deepEqual(store.getSnapshot(), before);

  assert.equal(store.setAccountStatus(ACTOR, "suspended", "account-platform-admin").ok, true);
  assert.equal(store.createKnowledgeBase({ name: "停用账户不可操作" }, ACTOR, DEFAULT_TENANT_ID).reason, "FORBIDDEN");
});

test("粘贴文本按 UTF-8 字节校验并拒绝远程 URL", () => {
  const { store } = fresh();
  const added = store.addKnowledgeSource(
    "kb-handbook",
    { mode: "paste", name: "差旅补充说明", text: "差旅标准以审批结果为准。" },
    ACTOR,
  );
  assert.equal(added.ok, true);
  assert.equal(added.source.kind, "粘贴文本");
  assert.equal(added.source.stage, "RECEIVED");
  assert.equal(added.source.rawContentStored, false);

  assert.equal(store.addKnowledgeSource("kb-handbook", { mode: "url", remoteUrl: "https://example.com/a.pdf" }, ACTOR).reason, "REMOTE_URL_FORBIDDEN");
  const tooLarge = "中".repeat(350_000);
  assert.equal(store.addKnowledgeSource("kb-handbook", { mode: "paste", name: "超限", text: tooLarge }, ACTOR).reason, "PASTED_TEXT_TOO_LARGE");
});

test("本地文件只接受白名单类型并在解析前拒绝危险或不一致内容", () => {
  const { store } = fresh();
  const accepted = store.addKnowledgeSource(
    "kb-handbook",
    { mode: "file", name: "办公室联系表.xlsx", sizeMb: 2.4, mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" },
    ACTOR,
  );
  assert.equal(accepted.ok, true);
  assert.equal(accepted.source.stage, "RECEIVED");

  const cases = [
    [{ mode: "file", name: "脚本.exe", sizeMb: 1, mimeType: "application/octet-stream" }, "UNSUPPORTED_FILE_TYPE"],
    [{ mode: "file", name: "超限.pdf", sizeMb: 51, mimeType: "application/pdf" }, "FILE_TOO_LARGE"],
    [{ mode: "file", name: "加密.pdf", sizeMb: 1, mimeType: "application/pdf", encrypted: true }, "ENCRYPTED_FILE"],
    [{ mode: "file", name: "宏.docx", sizeMb: 1, mimeType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document", containsMacros: true }, "OFFICE_MACRO_DETECTED"],
    [{ mode: "file", name: "损坏.xlsx", sizeMb: 1, mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", damagedArchive: true }, "DAMAGED_ARCHIVE"],
    [{ mode: "file", name: "伪装.pdf", sizeMb: 1, mimeType: "image/png" }, "MIME_MISMATCH"],
  ];
  for (const [input, reason] of cases) {
    assert.equal(store.addKnowledgeSource("kb-handbook", input, ACTOR).reason, reason);
  }
});

test("来源按统一阶段推进，失败不写索引并可从安全阶段重试", () => {
  const { storage, store } = fresh();
  const before = store.getSnapshot().knowledgeBases.find(item => item.id === "kb-handbook");
  const added = store.addKnowledgeSource(
    "kb-handbook",
    { mode: "file", name: "报销流程扫描件.pdf", sizeMb: 8, mimeType: "application/pdf", simulatedFailure: "OCR_TIMEOUT" },
    ACTOR,
  );
  const id = added.source.id;
  assert.equal(store.advanceKnowledgeSource("kb-handbook", id, ACTOR).source.stage, "SCANNING");
  assert.equal(store.advanceKnowledgeSource("kb-handbook", id, ACTOR).source.stage, "EXTRACTING");
  const failed = store.advanceKnowledgeSource("kb-handbook", id, ACTOR);
  assert.equal(failed.source.stage, "FAILED");
  assert.equal(failed.source.error.code, "OCR_TIMEOUT");
  assert.equal(failed.source.indexed, false);

  assert.equal(store.retryKnowledgeSource("kb-handbook", id, ACTOR).source.stage, "EXTRACTING");
  for (const stage of ["EXTRACTED", "CHUNKING", "INDEXING", "READY"]) {
    assert.equal(store.advanceKnowledgeSource("kb-handbook", id, ACTOR).source.stage, stage);
  }
  const restored = createPrototypeStore(storage).getSnapshot().knowledgeBases.find(item => item.id === "kb-handbook");
  assert.equal(restored.sources.find(source => source.id === id).stage, "READY");
  assert.equal(restored.documents, before.documents + 1);
  assert.ok(restored.storageGb > before.storageGb);
});

test("单文档重新处理建立新版本且活动索引继续服务", () => {
  const { store } = fresh();
  const before = store.getSnapshot().knowledgeBases.find(item => item.id === "kb-handbook");
  const result = store.reprocessKnowledgeSource("kb-handbook", "src-policy-pdf", ACTOR);
  assert.equal(result.ok, true);
  assert.equal(result.source.revision, "rev-0008");
  assert.equal(result.source.stage, "EXTRACTING");
  const after = store.getSnapshot().knowledgeBases.find(item => item.id === "kb-handbook");
  assert.equal(after.activeIndex, before.activeIndex);
  assert.equal(after.sources.find(source => source.id === "src-policy-pdf").indexed, false);
});

test("知识库与来源写入在存储失败时回滚", () => {
  const storage = createMemoryStorage();
  const store = createPrototypeStore({ ...storage, setItem() { throw new Error("合成存储故障"); } });
  const before = store.getSnapshot();
  assert.equal(store.createKnowledgeBase({ name: "不能落库" }, ACTOR, DEFAULT_TENANT_ID).reason, "STORAGE_UNAVAILABLE");
  assert.equal(store.addKnowledgeSource("kb-handbook", { mode: "paste", name: "不能落库", text: "正文" }, ACTOR).reason, "STORAGE_UNAVAILABLE");
  assert.deepEqual(store.getSnapshot(), before);
});
