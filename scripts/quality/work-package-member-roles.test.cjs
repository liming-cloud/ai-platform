const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { readRoleConfiguration } = require("../development/acceptance-member-roles.cjs");
const directory = fs.mkdtempSync("/private/tmp/wp0018-roles-test-");
after(() => fs.rmSync(directory, { recursive: true, force: true }));
const credentialsFile = path.join(directory, "credentials.json");
const credentials = {
  platform: { loginName: "synthetic-platform", password: "synthetic-only" },
  tenant: { tenantCode: "synthetic-tenant", loginName: "synthetic-admin", password: "synthetic-only" },
  member: { loginName: "synthetic-member", password: "synthetic-only" },
};
const persist = value => fs.writeFileSync(credentialsFile, JSON.stringify(value), { mode: 0o600 });
persist(credentials);
const environment = () => ({ FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE: credentialsFile,
  FOUNDATION_ACCEPTANCE_OUTPUT_FILE: path.join(directory, "role-report.json") });

test("只读角色验收复用三类身份，不接受准备且不能覆盖既有材料", () => {
  assert.equal(readRoleConfiguration(environment()).outputFile, environment().FOUNDATION_ACCEPTANCE_OUTPUT_FILE);
  assert.throws(() => readRoleConfiguration({ ...environment(), SETUP: "tenant" }), /不接受外部 SETUP/);
  assert.throws(() => readRoleConfiguration({ ...environment(), FOUNDATION_ACCEPTANCE_OUTPUT_FILE: credentialsFile }), /尚不存在/);
  try { fs.chmodSync(directory, 0o755); assert.throws(() => readRoleConfiguration(environment()), /0700/); }
  finally { fs.chmodSync(directory, 0o700); }
});
test("缺失普通成员凭据必须失败，错误不得回显输入秘密", () => {
  for (const member of [undefined, {}, { loginName: "synthetic-member" }, { loginName: "synthetic-member", password: 7 }]) {
    persist({ ...credentials, member });
    assert.throws(() => readRoleConfiguration(environment()), error => /普通成员/.test(error.message)
      && !error.message.includes("synthetic-only"));
  }
  persist(credentials);
});
