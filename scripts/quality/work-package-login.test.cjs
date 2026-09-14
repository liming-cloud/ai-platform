const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { readConfiguration } = require("../development/acceptance-login.cjs");

const directory = fs.mkdtempSync(path.join(os.tmpdir(), "wp0017-configuration-test-"));
after(() => fs.rmSync(directory, { recursive: true, force: true }));
const fixture = {
  platform: { loginName: "synthetic-platform", password: "synthetic-only-password" },
  tenant: { tenantCode: "synthetic-tenant", loginName: "synthetic-tenant-admin", password: "synthetic-only-password" },
};
function credentialFile(name, data = fixture, mode = 0o600) {
  const target = path.join(directory, name);
  fs.writeFileSync(target, JSON.stringify(data), { mode });
  return target;
}
function environment(file, extra = {}) {
  return { FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE: file, ...extra };
}

test("只接受当前账户独占的普通 0600 JSON 凭据文件", () => {
  const configuration = readConfiguration(environment(credentialFile("valid.json")));
  assert.equal(configuration.baseUrl, "http://127.0.0.1:13000");
  assert.equal(configuration.credentials.tenant.tenantCode, "synthetic-tenant");
});
test("组可读文件在任何网络请求前拒绝", () => {
  assert.throws(() => readConfiguration(environment(credentialFile("public.json", fixture, 0o640))), /0600/);
});
test("符号链接不能绕过凭据文件边界", () => {
  const source = credentialFile("source.json");
  const link = path.join(directory, "link.json");
  fs.symlinkSync(source, link);
  assert.throws(() => readConfiguration(environment(link)), /凭据文件/);
});
test("缺少租户凭据不能当成平台单角色通过", () => {
  assert.throws(() => readConfiguration(environment(credentialFile("platform-only.json", { platform: fixture.platform }))), /租户管理员凭据/);
});
test("只有显式 setup 可以暂时不提供租户且必须给出新输出文件", () => {
  const file = credentialFile("setup.json", { platform: fixture.platform });
  assert.throws(() => readConfiguration(environment(file, { SETUP: "tenant" })), /新凭据输出文件/);
  const configuration = readConfiguration(environment(file, {
    SETUP: "tenant", FOUNDATION_ACCEPTANCE_OUTPUT_FILE: path.join(directory, "created.json"),
  }));
  assert.equal(configuration.setup, true);
});
test("setup 不得覆盖现有文件", () => {
  const file = credentialFile("existing.json");
  assert.throws(() => readConfiguration(environment(file, {
    SETUP: "tenant", FOUNDATION_ACCEPTANCE_OUTPUT_FILE: file,
  })), /新凭据输出文件/);
});
test("禁止非回环、非 13000 和含凭据或查询参数的目标", () => {
  const file = credentialFile("url.json");
  for (const url of ["https://example.com", "http://127.0.0.1:13001", "http://name:private@127.0.0.1:13000", "http://127.0.0.1:13000/?token=private"]) {
    assert.throws(() => readConfiguration(environment(file, { FOUNDATION_ACCEPTANCE_BASE_URL: url })), /本机 13000/);
  }
});
