const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { readPackageConfiguration, classifyRequest } = require("../development/acceptance-member-package.cjs");
const directory = fs.mkdtempSync("/private/tmp/wp0018-package-test-");
after(() => fs.rmSync(directory, { recursive: true, force: true }));
const credentialsFile = path.join(directory, "credentials.json");
const credentials = {
  platform: { loginName: "synthetic-platform", password: "synthetic-only" },
  tenant: { tenantCode: "synthetic-tenant", loginName: "synthetic-admin", password: "synthetic-only" },
  member: { membershipId: "123", loginName: "synthetic-member", password: "synthetic-only" },
};
const persist = value => fs.writeFileSync(credentialsFile, JSON.stringify(value), { mode: 0o600 });
const environment = () => ({ FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE: credentialsFile,
  FOUNDATION_ACCEPTANCE_OUTPUT_FILE: path.join(directory, "package-report.json") });
persist(credentials);

test("整包只复用现有三类身份与精确成员，拒绝准备和不安全输出", () => {
  assert.equal(readPackageConfiguration(environment()).credentials.member.membershipId, "123");
  assert.throws(() => readPackageConfiguration({ ...environment(), SETUP: "tenant" }), /不接受外部 SETUP/);
  assert.throws(() => readPackageConfiguration({ ...environment(), FOUNDATION_ACCEPTANCE_OUTPUT_FILE: credentialsFile }), /尚不存在/);
  try { fs.chmodSync(directory, 0o755); assert.throws(() => readPackageConfiguration(environment()), /0700/); }
  finally { fs.chmodSync(directory, 0o700); }
});
test("缺失或不精确的成员标识失败，错误不回显秘密", () => {
  for (const membershipId of [undefined, 123, "0", "-1", "9223372036854775808", "123/other"]) {
    persist({ ...credentials, member: { ...credentials.member, membershipId } });
    assert.throws(() => readPackageConfiguration(environment()), error => /成员标识/.test(error.message)
      && !error.message.includes("synthetic-only"));
  }
  persist(credentials);
});
test("业务写入按方法判定，只有同源精确登录与退出可单独计数", () => {
  const base = "http://127.0.0.1:13000";
  for (const method of ["GET", "HEAD", "OPTIONS"]) {
    assert.equal(classifyRequest(method, base + "/api/v1/tenant/members", base), "read");
  }
  assert.equal(classifyRequest("POST", base + "/api/v1/auth/browser-sessions", base), "authentication");
  assert.equal(classifyRequest("DELETE", base + "/api/v1/auth/browser-session", base), "authentication");
  for (const [method, target] of [["POST", "/api/v1/tenant/members"], ["PUT", "/api/v1/tenant/members/123/features"],
    ["POST", "/api/v1/auth/unapproved"], ["DELETE", "/api/v1/auth/browser-sessions"],
    ["POST", "/api/v1/auth/browser-sessions?scope=other"], ["PATCH", "/api/v1/auth/browser-session"]]) {
    assert.equal(classifyRequest(method, base + target, base), "business");
  }
  assert.equal(classifyRequest("POST", "http://localhost:13000/api/v1/auth/browser-sessions", base), "business");
  assert.equal(classifyRequest("POST", "https://example.invalid/api/v1/auth/browser-sessions", base), "business");
});
