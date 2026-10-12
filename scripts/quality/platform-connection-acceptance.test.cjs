/* 正式验收的模式、对象与运行版本约束；全部使用临时合成凭据。 */
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const { test } = require("node:test");
const { platformConnectionConfiguration, readConnectionInput, inspectRuntime, permittedMutation,
  verifyConnection, verifyPublication, verifyPage, assertSafeResponse, requireActionReady, directory, detail } = require("../development/acceptance-platform-connection.cjs");

const frontendRevision = "a".repeat(40);
const backendRevision = "b".repeat(40);
function fixture(action) {
  const directory = fs.mkdtempSync("/private/tmp/wp0019-entry-test-");
  fs.chmodSync(directory, 0o700);
  const file = path.join(directory, "credentials.json");
  fs.writeFileSync(file, JSON.stringify({
    platform: { loginName: "synthetic-platform", password: "synthetic-platform-input" },
    tenant: { tenantCode: "synthetic-tenant", loginName: "synthetic-tenant", password: "synthetic-tenant-input" },
  }), { mode: 0o600 });
  const environment = {
    FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE: file,
    FOUNDATION_ACCEPTANCE_FRONTEND_REVISION: frontendRevision,
    FOUNDATION_ACCEPTANCE_BACKEND_REVISION: backendRevision,
    FOUNDATION_ACCEPTANCE_CONNECTION_ID: "2093675537095458817",
    FOUNDATION_ACCEPTANCE_CONNECTION_VERSION: "3",
  };
  try { return action(environment, directory, file); }
  finally { fs.rmSync(directory, { recursive: true, force: true }); }
}

test("默认只读并固定既有对象、独立前后端运行版本", () => fixture(environment => {
  const configuration = platformConnectionConfiguration(environment);
  assert.equal(configuration.mode, "read-only");
  assert.equal(configuration.connectionId, environment.FOUNDATION_ACCEPTANCE_CONNECTION_ID);
  assert.equal(configuration.expectedVersion, 3);
  assert.equal(configuration.frontendRevision, frontendRevision);
  assert.equal(configuration.backendRevision, backendRevision);
}));

test("未知执行模式及 SETUP 在打开浏览器前失败关闭", () => fixture(environment => {
  for (const value of ["all", "", "readonly", "deploy", "vendor-discovery,publish-current"]) {
    assert.throws(() => platformConnectionConfiguration({ ...environment, FOUNDATION_ACCEPTANCE_MODE: value }), /执行模式/);
  }
  assert.throws(() => platformConnectionConfiguration({ ...environment, SETUP: "tenant" }), /不接受外部 SETUP/);
}));

test("既有对象必须是精确正 Long 标识及安全整数版本", () => fixture(environment => {
  for (const value of [undefined, "0", "-1", "01", "9223372036854775808", "1/../../publish"]) {
    assert.throws(() => platformConnectionConfiguration({ ...environment, FOUNDATION_ACCEPTANCE_CONNECTION_ID: value }), /连接标识/);
  }
  for (const value of [undefined, "-1", "01", "NaN", "9007199254740992"]) {
    assert.throws(() => platformConnectionConfiguration({ ...environment, FOUNDATION_ACCEPTANCE_CONNECTION_VERSION: value }), /连接版本/);
  }
}));

test("运行提交必须分别显式提供，不能以 HEAD 或缩写代替", () => fixture(environment => {
  for (const key of ["FOUNDATION_ACCEPTANCE_FRONTEND_REVISION", "FOUNDATION_ACCEPTANCE_BACKEND_REVISION"]) {
    for (const value of [undefined, "HEAD", "a9210efc", "g".repeat(40)]) {
      assert.throws(() => platformConnectionConfiguration({ ...environment, [key]: value }), /运行提交/);
    }
  }
}));

test("只读不接受连接写入文件，其他写入模式也不能混用创建材料", () => fixture(environment => {
  for (const mode of ["read-only", "vendor-discovery", "publish-current"]) {
    assert.throws(() => platformConnectionConfiguration({ ...environment, FOUNDATION_ACCEPTANCE_MODE: mode,
      FOUNDATION_ACCEPTANCE_CONNECTION_FILE: "/private/tmp/not-used.json" }), /创建材料/);
  }
}));

test("写入模式必须指定新的独占输出，只读不会自动生成文件", () => fixture((environment, directory) => {
  for (const mode of ["vendor-discovery", "publish-current"]) {
    assert.throws(() => platformConnectionConfiguration({ ...environment, FOUNDATION_ACCEPTANCE_MODE: mode }), /输出文件/);
    const configuration = platformConnectionConfiguration({ ...environment, FOUNDATION_ACCEPTANCE_MODE: mode,
      FOUNDATION_ACCEPTANCE_OUTPUT_FILE: path.join(directory, "result.json") });
    assert.equal(configuration.mode, mode);
    assert.equal(fs.existsSync(configuration.outputFile), false);
  }
  platformConnectionConfiguration(environment);
  assert.deepEqual(fs.readdirSync(directory), ["credentials.json"]);
}));

test("拒绝不安全凭据、符号链接与外部目标，错误不包含正文", () => fixture((environment, directory, file) => {
  const link = path.join(directory, "link.json"); fs.symlinkSync(file, link);
  assert.throws(() => platformConnectionConfiguration({ ...environment, FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE: link }), /凭据文件不可用/);
  fs.chmodSync(file, 0o644);
  assert.throws(() => platformConnectionConfiguration(environment), error =>
    !error.message.includes("synthetic-platform-input") && /凭据文件不可用/.test(error.message));
  fs.chmodSync(file, 0o600);
  assert.throws(() => platformConnectionConfiguration({ ...environment,
    FOUNDATION_ACCEPTANCE_BASE_URL: "https://example.com/" }), /本机 13000/);
}));

test("不能覆盖历史输出或写入仓库及宽权限目录", () => fixture((environment, directory, file) => {
  for (const output of [file, path.join(process.cwd(), "result.json"), "relative.json"]) {
    assert.throws(() => platformConnectionConfiguration({ ...environment, FOUNDATION_ACCEPTANCE_OUTPUT_FILE: output }), /输出文件/);
  }
  fs.chmodSync(directory, 0o755);
  assert.throws(() => platformConnectionConfiguration({ ...environment,
    FOUNDATION_ACCEPTANCE_OUTPUT_FILE: path.join(directory, "new.json") }), /输出文件/);
}));

test("创建草稿要求独立 0600 材料与新输出，不能混入既有对象", () => fixture((environment, directory) => {
  const inputFile = path.join(directory, "connection.json");
  fs.writeFileSync(inputFile, JSON.stringify({ providerPresetId: "CUSTOM", capability: "CHAT",
    endpoint: "https://models.example.invalid/v1", apiKey: "synthetic-inference-key" }), { mode: 0o600 });
  const creation = { ...environment, FOUNDATION_ACCEPTANCE_MODE: "create-draft",
    FOUNDATION_ACCEPTANCE_CONNECTION_ID: undefined, FOUNDATION_ACCEPTANCE_CONNECTION_VERSION: undefined,
    FOUNDATION_ACCEPTANCE_OUTPUT_FILE: path.join(directory, "created.json"),
    FOUNDATION_ACCEPTANCE_CONNECTION_FILE: inputFile };
  assert.equal(platformConnectionConfiguration(creation).input.providerPresetId, "CUSTOM");
  assert.throws(() => platformConnectionConfiguration({ ...creation, FOUNDATION_ACCEPTANCE_CONNECTION_ID: "1" }), /不能同时/);
  fs.chmodSync(inputFile, 0o644);
  assert.throws(() => platformConnectionConfiguration(creation), /创建材料文件不可用/);
}));

test("创建材料拒绝私有字段、无关 AK/SK、地址覆盖及工作空间注入", () => fixture((environment, directory) => {
  const file = path.join(directory, "connection.json");
  const base = { providerPresetId: "CUSTOM", capability: "CHAT", endpoint: "https://models.example.invalid/v1",
    apiKey: "synthetic-inference-key" };
  for (const input of [
    { ...base, credentialReference: "secret://not-allowed" },
    { ...base, discoveryAccessKeyId: "synthetic-ak", discoverySecretAccessKey: "synthetic-sk" },
    { ...base, providerPresetId: "DEEPSEEK" }, { ...base, workspaceId: "space.example/@evil" },
    { ...base, endpoint: "https://user:input@models.example.invalid/v1" },
    { ...base, endpoint: "https://models.example.invalid/v1?key=not-allowed" },
    { ...base, providerPresetId: "VOLCENGINE", endpoint: undefined },
  ]) {
    fs.writeFileSync(file, JSON.stringify(input), { mode: 0o600 });
    assert.throws(() => readConnectionInput(file), error => error.message.includes("创建材料")
      && !error.message.includes("synthetic-inference-key"));
  }
}));

test("创建材料允许方舟成对的独立发现认证且不允许符号链接", () => fixture((environment, directory) => {
  const file = path.join(directory, "connection.json");
  fs.writeFileSync(file, JSON.stringify({ providerPresetId: "VOLCENGINE", capability: "CHAT",
    apiKey: "synthetic-inference-key", discoveryAccessKeyId: "synthetic-discovery-ak",
    discoverySecretAccessKey: "synthetic-discovery-sk" }), { mode: 0o600 });
  assert.equal(readConnectionInput(file).providerPresetId, "VOLCENGINE");
  const link = path.join(directory, "linked-connection.json"); fs.symlinkSync(file, link);
  assert.throws(() => readConnectionInput(link), /创建材料文件不可用/);
}));

test("只读和各写入模式严格隔离，不能把发现授权扩成发布或其他对象", () => {
  const base = "/api/v1/platform/model-supply/connections";
  const cases = [
    ["read-only", undefined], ["create-draft", base],
    ["vendor-discovery", base + "/123/model-discovery"], ["publish-current", base + "/123/publish"],
  ];
  for (const [mode, permitted] of cases) {
    const configuration = { mode, connectionId: "123" };
    for (const target of [base, base + "/123/model-discovery", base + "/123/publish", base + "/124/publish",
      base + "/124/model-discovery", base + "/123/disable", "/api/v1/platform/tenants"]) {
      assert.equal(permittedMutation("POST", target, configuration), target === permitted ? "business" : undefined);
      assert.equal(permittedMutation("DELETE", target, configuration), undefined);
    }
    assert.equal(permittedMutation("GET", base, configuration), "read");
    assert.equal(permittedMutation("POST", "/api/v1/auth/browser-sessions", configuration), "authentication");
    assert.equal(permittedMutation("POST", "/api/v1/auth/browser-session", configuration), undefined);
    assert.equal(permittedMutation("POST", "/api/v1/auth/browser-session/context", configuration), undefined);
    assert.equal(permittedMutation("DELETE", "/api/v1/auth/browser-session", configuration), "authentication");
  }
});

const connection = () => ({ id: "123", version: 3, providerKind: "OPENAI_COMPATIBLE",
  discoveryProfile: "OPENAI_MODELS_API", status: "VERIFIED", published: false,
  credentialConfigured: true, discoveryCredentialsConfigured: false,
  lastProbeSucceeded: true, lastProbedAt: "2026-10-12T00:00:00Z" });

test("对象、版本及事实缺失均拒绝，不把旧布尔值猜为最新事实", () => {
  verifyConnection(connection(), "123", 3);
  for (const changed of [{ id: "124" }, { version: 4 }, { status: "PUBLISHED" },
    { credentialConfigured: undefined }, { discoveryCredentialsConfigured: undefined }, { published: undefined }]) {
    assert.throws(() => verifyConnection({ ...connection(), ...changed }, "123", 3), /不匹配/);
  }
});

test("发布投影必须属于当前对象、当前修订及当前发布状态", () => {
  const publication = { connectionId: "123", connectionRevision: 3, published: false };
  verifyPublication(publication, connection());
  for (const changed of [{ connectionId: "124" }, { connectionRevision: 2 }, { published: true }]) {
    assert.throws(() => verifyPublication({ ...publication, ...changed }, connection()), /当前连接修订/);
  }
});

test("真实发现不能用确定性结果、停用或缺失的独立认证代替", () => {
  const configuration = { mode: "vendor-discovery", connectionId: "123", expectedVersion: 3 };
  requireActionReady(configuration, connection());
  for (const changed of [{ providerKind: "DETERMINISTIC" }, { discoveryProfile: "DETERMINISTIC_CATALOG" },
    { discoveryProfile: "NONE" }, { status: "DISABLED" }, { credentialConfigured: false },
    { discoveryProfile: "VOLCENGINE_ARK_CONTROL_PLANE", discoveryCredentialsConfigured: false }]) {
    assert.throws(() => requireActionReady(configuration, { ...connection(), ...changed }), /真实厂商发现/);
  }
  requireActionReady(configuration, { ...connection(), discoveryProfile: "VOLCENGINE_ARK_CONTROL_PLANE",
    discoveryCredentialsConfigured: true });
});

test("发布拒绝失败、过期、未来时间及已经发布，不能自动刷新再发布", () => {
  const now = Date.parse("2026-10-12T01:00:00Z");
  const configuration = { mode: "publish-current", connectionId: "123", expectedVersion: 3 };
  requireActionReady(configuration, connection(), now);
  for (const changed of [{ status: "DRAFT" }, { lastProbeSucceeded: false }, { published: true },
    { lastProbedAt: "2026-10-11T01:00:00Z" }, { lastProbedAt: "2026-10-12T02:00:00Z" },
    { lastProbedAt: undefined }]) {
    assert.throws(() => requireActionReady(configuration, { ...connection(), ...changed }, now), /发布要求/);
  }
});

test("读取校验拒绝秘密字段和嵌套回显，错误仅包含安全说明", () => {
  assertSafeResponse(connection());
  for (const response of [{ apiKey: "synthetic-inference-key" }, { items: [{ wrappedDataKey: "encoded-value" }] },
    { error: "synthetic-inference-key" }, { location: "secret://not-allowed" }]) {
    assert.throws(() => assertSafeResponse(response, ["synthetic-inference-key"]),
      error => !error.message.includes("synthetic-inference-key") && /秘密|禁止披露/.test(error.message));
  }
});

test("分页保留总数及越界语义，拒绝重复 ID 与手工截断", () => {
  const key = item => item.id;
  verifyPage({ items: [{ id: "1" }], totalElements: 1, hasPrevious: false, hasNext: false }, 1, 20, key);
  verifyPage({ items: [], totalElements: 1, hasPrevious: true, hasNext: false }, 2, 20, key);
  assert.throws(() => verifyPage({ items: [], totalElements: 1, hasPrevious: false, hasNext: false }, 1, 20, key), /分页/);
  assert.throws(() => verifyPage({ items: [{ id: "1" }, { id: "1" }], totalElements: 2,
    hasPrevious: false, hasNext: false }, 1, 20, key), /分页/);
});

function runtimeFixture(change = value => value, dirty = "") {
  const services = ["console-web", "control-plane", "query-runtime", "ingestion-worker", "postgres", "redis", "minio"];
  const facts = services.map(service => ({ service, health: "healthy", image: "sha256:" + "c".repeat(64),
    revision: service === "console-web" ? frontendRevision : backendRevision }));
  return (command, args) => {
    if (command === "git") return args[0] === "status" ? dirty : "d".repeat(40);
    if (args[0] === "ps") return services.map((_, index) => String(index + 1).repeat(12)).join("\n");
    assert.equal(args[0], "inspect");
    assert.ok(args.includes("--format"));
    assert.ok(!args.some(value => value.includes(".Config.Env")));
    return change(facts).map(value => JSON.stringify(value)).join("\n");
  };
}

test("运行事实绑定四应用各自 revision 与七服务健康，不读取环境变量", () => {
  const configuration = { frontendRevision, backendRevision };
  assert.equal(inspectRuntime(configuration, runtimeFixture()).services.length, 7);
  assert.throws(() => inspectRuntime(configuration, runtimeFixture(value => value, " M tracked-file")), /干净并已提交/);
  for (const service of ["console-web", "control-plane", "query-runtime", "ingestion-worker"]) {
    assert.throws(() => inspectRuntime(configuration, runtimeFixture(values => values.map(value =>
      value.service === service ? { ...value, revision: "e".repeat(40) } : value))), /运行版本不匹配/);
  }
  assert.throws(() => inspectRuntime(configuration, runtimeFixture(values => values.map(value =>
    value.service === "redis" ? { ...value, health: "unhealthy" } : value))), /不健康/);
  assert.throws(() => inspectRuntime(configuration, runtimeFixture(values => [...values.slice(1), values[1]])), /重复/);
});

test("浏览器目录与详情沿用 SPA 正式路由，不把 API 集合路径当作页面", async () => {
  let url;
  const page = { goto: async value => { url = value; }, url: () => url,
    getByRole: () => ({ waitFor: async () => {} }) };
  const configuration = { baseUrl: "http://127.0.0.1:13000", connectionId: "123" };
  await directory(page, configuration);
  assert.equal(new URL(url).pathname, "/platform/model-supply");
  await detail(page, configuration);
  assert.equal(new URL(url).pathname, "/platform/model-supply/connections/123");
});
