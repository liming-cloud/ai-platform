const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { readConfiguration, verifyAccount } = require("../development/acceptance-member-account.cjs");
const directory = fs.mkdtempSync("/private/tmp/wp0018-account-test-");
after(() => fs.rmSync(directory, { recursive: true, force: true }));
const credentialsFile = path.join(directory, "credentials.json");
fs.writeFileSync(credentialsFile, JSON.stringify({ platform: { loginName: "synthetic-platform", password: "synthetic-only" },
  tenant: { tenantCode: "synthetic-tenant", loginName: "synthetic-admin", password: "synthetic-only" } }), { mode: 0o600 });
const environment = () => ({ FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE: credentialsFile,
  FOUNDATION_ACCEPTANCE_OUTPUT_FILE: path.join(directory, "new-credentials.json") });
const account = () => ({ membershipId: "2108009641811251202", loginName: "synthetic-member", membershipRole: "TENANT_USER", status: "ENABLED",
  version: 0, identityVersion: 0, permissionVersion: 0, createdAt: "2026-10-08T00:00:00Z", featureVersion: 0,
  conversationFeatures: { assistantSelection: true, conversation: true, ownHistory: false, authorizedCitations: false },
  assistantIds: [], passwordResetEligible: true });

test("独占输入与新输出有效，禁止租户准备及覆盖既有凭据", () => {
  assert.equal(readConfiguration(environment()).outputFile, environment().FOUNDATION_ACCEPTANCE_OUTPUT_FILE);
  assert.throws(() => readConfiguration({ ...environment(), SETUP: "tenant" }), /不创建或重置租户/);
  assert.throws(() => readConfiguration({ ...environment(), FOUNDATION_ACCEPTANCE_OUTPUT_FILE: credentialsFile }), /尚不存在/);
  assert.throws(() => readConfiguration({ ...environment(), FOUNDATION_ACCEPTANCE_BASE_URL: "https://example.invalid" }), /本机/);
});
test("目录权限不独占或凭据可被其他用户读取必须失败", () => {
  try { fs.chmodSync(directory, 0o755); assert.throws(() => readConfiguration(environment()), /0700/); }
  finally { fs.chmodSync(directory, 0o700); }
  try { fs.chmodSync(credentialsFile, 0o644); assert.throws(() => readConfiguration(environment()), /0600/); }
  finally { fs.chmodSync(credentialsFile, 0o600); }
});
test("新普通成员与显式空授权是通过条件，额外主体和秘密字段必须拒绝", () => {
  assert.doesNotThrow(() => verifyAccount(account(), "synthetic-member"));
  for (const extra of ["subjectId", "password", "passwordHash", "tokenValue"]) {
    assert.throws(() => verifyAccount({ ...account(), [extra]: "synthetic-only" }, "synthetic-member"), /最小安全投影/);
  }
});
test("数字长整型未知功能管理员角色及隐式助手均不能作为开户完成", () => {
  for (const change of [{ membershipId: 2108009641811251202 }, { membershipRole: "TENANT_ADMINISTRATOR" },
    { status: "DISABLED" }, { identityVersion: 1 }, { assistantIds: ["123"] },
    { conversationFeatures: { ...account().conversationFeatures, unregistered: true } }]) {
    assert.throws(() => verifyAccount({ ...account(), ...change }, "synthetic-member"));
  }
});
