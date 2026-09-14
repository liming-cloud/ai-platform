const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { readOnboardingConfiguration, onboardingRequest, verifyOnboardingResult } = require("../development/acceptance-onboarding.cjs");

const directory = fs.mkdtempSync(path.join(os.tmpdir(), "wp0017-onboarding-test-"));
after(() => fs.rmSync(directory, { recursive: true, force: true }));
const credentialsFile = path.join(directory, "credentials.json");
fs.writeFileSync(credentialsFile, JSON.stringify({ platform: { loginName: "synthetic-platform", password: "synthetic-only-password" } }), { mode: 0o600 });
const environment = () => ({ FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE: credentialsFile,
  FOUNDATION_ACCEPTANCE_OUTPUT_FILE: path.join(directory, "new-created.json") });

test("开户只需平台凭据，但必须显式指定新的独占凭据输出文件", () => {
  assert.equal(readOnboardingConfiguration(environment()).setup, true);
  assert.throws(() => readOnboardingConfiguration({ FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE: credentialsFile }), /新凭据输出文件/);
  assert.throws(() => readOnboardingConfiguration({ ...environment(), FOUNDATION_ACCEPTANCE_OUTPUT_FILE: credentialsFile }), /新凭据输出文件/);
});
test("开户验收不得通过 SETUP 绕过真实 UI，也不接受远端目标", () => {
  assert.throws(() => readOnboardingConfiguration({ ...environment(), SETUP: "tenant" }), /不接受 SETUP/);
  assert.throws(() => readOnboardingConfiguration({ ...environment(), FOUNDATION_ACCEPTANCE_BASE_URL: "https://example.com" }), /本机 13000/);
});
test("开户请求使用单次新凭据和精确字节配额，不再引用已建主体", () => {
  const request = onboardingRequest({ tenantCode: "new-tenant", loginName: "new-admin", password: "synthetic-only-password" });
  assert.deepEqual(Object.keys(request).sort(), ["administrator", "displayName", "quotas", "tenantCode"]);
  assert.equal(request.administrator.loginName, "new-admin");
  assert.deepEqual(request.quotas, { knowledgeBases: 4, documentsPerKnowledgeBase: 1234,
    bytesPerKnowledgeBase: "12500000000", maxFileBytes: "75000000", maxTextBytes: "2097152" });
});
test("不能把准备中、精度漂移或缺失字段当成原子开户完成", () => {
  const tenant = { tenantId: "123456789012345678", initialAdministratorSubjectId: "123456789012345679", status: "ACTIVE" };
  const quotas = { ...onboardingRequest({}).quotas, version: 0 };
  assert.doesNotThrow(() => verifyOnboardingResult({ status: 201, body: tenant }, { status: 200, body: quotas }));
  for (const changed of [{ ...tenant, status: "PROVISIONING" }, { ...tenant, tenantId: 123456789012345678 }, { ...tenant, initialAdministratorSubjectId: undefined }]) {
    assert.throws(() => verifyOnboardingResult({ status: 201, body: changed }, { status: 200, body: quotas }));
  }
  assert.throws(() => verifyOnboardingResult({ status: 201, body: tenant }, { status: 200, body: { ...quotas, bytesPerKnowledgeBase: 12500000000 } }));
  assert.throws(() => verifyOnboardingResult({ status: 201, body: tenant }, { status: 200, body: { ...quotas, knowledgeBases: 3 } }));
});
