const fs = require("node:fs");
const path = require("node:path");
const os = require("node:os");
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { configuration, permittedMutation, readDraftInput } = require("../development/acceptance-platform-supply.cjs");

test("S2 默认不扩大发现、推理或发布授权", () => {
  for (const mode of ["probe", "verify", "publish-current", "grant", "all"]) assert.throws(() => configuration({ FOUNDATION_ACCEPTANCE_MODE: mode }));
  for (const pathname of ["/api/v1/platform/model-supply/deployments/11/probe", "/api/v1/platform/model-supply/deployments/11/publish", "/api/v1/platform/tenants", "/api/v1/model-configurations"])
    assert.equal(permittedMutation("POST", pathname, "create-draft", true, 0), false);
  assert.equal(permittedMutation("POST", "/api/v1/platform/model-supply/deployments", "read-only", true, 0), false);
  assert.equal(permittedMutation("POST", "/api/v1/platform/model-supply/deployments", "directory-search", true, 0), false);
  assert.equal(permittedMutation("POST", "/api/v1/platform/model-supply/deployments/11/probe", "directory-search", true, 0), false);
  assert.equal(permittedMutation("POST", "/api/v1/platform/model-supply/deployments", "create-draft", false, 0), false);
  assert.equal(permittedMutation("POST", "/api/v1/platform/model-supply/deployments", "create-draft", true, 0), true);
  assert.equal(permittedMutation("POST", "/api/v1/platform/model-supply/deployments", "create-draft", true, 1), false);
});
test("草稿材料需要精确身份来源、类型参数和非空拟授权", () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "wp0019-s2-config-test-")); fs.chmodSync(directory, 0o700);
  const file = path.join(directory, "input.json");
  const input = { connectionId: "11", connectionRevision: 2, modelCode: "synthetic", displayName: "合成草稿", modelType: "RERANK",
    contextWindow: 128, rerankCandidateLimit: 20, parameterSource: "合成文档 v1", plannedTenantIds: ["21"] };
  const write = value => fs.writeFileSync(file, JSON.stringify(value), { mode: 0o600 });
  try {
    write(input); assert.deepEqual(readDraftInput(file), input);
    for (const delta of [{ plannedTenantIds: [] }, { connectionId: "9223372036854775808" }, { rerankCandidateLimit: 0 }, { modelType: "UNKNOWN" }, { apiKey: "synthetic-not-a-credential" }]) {
      write({ ...input, ...delta }); assert.throws(() => readDraftInput(file));
    }
    write(input); fs.chmodSync(file, 0o644); assert.throws(() => readDraftInput(file));
    fs.chmodSync(file, 0o600); const link = path.join(directory, "link.json"); fs.symlinkSync(file, link); assert.throws(() => readDraftInput(link));
  } finally { fs.rmSync(directory, { recursive: true }); }
});
