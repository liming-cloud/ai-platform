const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { readConfiguration, verifyFeatures } = require("../development/acceptance-member-features.cjs");
const directory = fs.mkdtempSync("/private/tmp/wp0018-features-test-");
after(() => fs.rmSync(directory, { recursive: true, force: true }));
const credentialsFile = path.join(directory, "credentials.json");
fs.writeFileSync(credentialsFile, JSON.stringify({ platform: { loginName: "synthetic-platform", password: "synthetic-only" },
  tenant: { tenantCode: "synthetic-tenant", loginName: "synthetic-admin", password: "synthetic-only" } }), { mode: 0o600 });
const environment = () => ({ FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE: credentialsFile,
  FOUNDATION_ACCEPTANCE_OUTPUT_FILE: path.join(directory, "new-credentials.json") });
const values = { assistantSelection: true, conversation: true, ownHistory: false, authorizedCitations: false };
const facts = () => ({ membershipId: "2108009641811251202", loginName: "synthetic-member", status: "ENABLED", readOnly: false,
  conversationFeatures: values, featureVersion: 2, tenantAvailableFeatures: values, tenantFeatureVersion: 3, effectiveFeatures: values });

test("功能验收要求新0600输出和独占目录，禁止覆盖及外部准备", () => {
  assert.equal(readConfiguration(environment()).outputFile, environment().FOUNDATION_ACCEPTANCE_OUTPUT_FILE);
  assert.throws(() => readConfiguration({ ...environment(), SETUP: "tenant" }), /不接受外部 SETUP/);
  assert.throws(() => readConfiguration({ ...environment(), FOUNDATION_ACCEPTANCE_OUTPUT_FILE: credentialsFile }), /尚不存在/);
  try { fs.chmodSync(directory, 0o755); assert.throws(() => readConfiguration(environment()), /0700/); }
  finally { fs.chmodSync(directory, 0o700); }
});
test("四项保存事实和管理员只读是明确投影，拒绝额外身份与秘密字段", () => {
  assert.doesNotThrow(() => verifyFeatures(facts()));
  assert.doesNotThrow(() => verifyFeatures({ ...facts(), readOnly: true, conversationFeatures: {} }));
  for (const key of ["subjectId", "password", "passwordHash", "tokenValue", "assistantIds"]) {
    assert.throws(() => verifyFeatures({ ...facts(), [key]: "synthetic-only" }), /最小安全投影/);
  }
});
test("未知缺失功能非布尔值与不精确版本不能通过功能验收", () => {
  for (const change of [{ featureVersion: -1 }, { tenantFeatureVersion: 1.5 }, { membershipId: 2108009641811251202 },
    { conversationFeatures: { conversation: true } }, { effectiveFeatures: { ...values, conversation: "true" } },
    { tenantAvailableFeatures: { ...values, unknown: false } }]) {
    assert.throws(() => verifyFeatures({ ...facts(), ...change }));
  }
});
