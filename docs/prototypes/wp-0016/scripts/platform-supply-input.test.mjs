import test from "node:test";
import assert from "node:assert/strict";
import { createMemoryStorage, createPrototypeStore, DEFAULT_TENANT_ID, PROTOTYPE_STATE_KEY } from "../prototype-state.js";

const ACTOR = "account-platform-admin";
function fresh() { return createPrototypeStore(createMemoryStorage()); }
function input(overrides = {}) {
  return {
    name: "平台对话部署", connectionId: "pc-chat", modelId: "model-chat-v6",
    tenantIds: [DEFAULT_TENANT_ID], capability: "CHAT", contextWindow: 4096,
    outputLimit: 64, parameterSource: "合成厂商资料 v1", modelConfirmed: true, ...overrides,
  };
}
function restored(snapshot) {
  const storage = createMemoryStorage();
  storage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(snapshot));
  return createPrototypeStore(storage);
}

test("部署草稿拒绝未明确提供的能力参数，不从模型名称猜测", () => {
  const result = fresh().createPlatformModelDeployment(input({ contextWindow: undefined }), ACTOR);
  assert.equal(result.ok, false); assert.equal(result.reason, "MODEL_PARAMETERS_REQUIRED");
});
test("已知模型类型不能由草稿输入改成其他能力", () => {
  const result = fresh().createPlatformModelDeployment(input({ capability: "EMBEDDING" }), ACTOR);
  assert.equal(result.ok, false); assert.equal(result.reason, "MODEL_CAPABILITY_MISMATCH");
});
test("声明输出上限不得超过上下文窗口", () => {
  assert.equal(fresh().createPlatformModelDeployment(input({ contextWindow: 32, outputLimit: 64 }), ACTOR).reason, "MODEL_PARAMETERS_REQUIRED");
});
test("没有明确来源和确认不得登记任何草稿或确认事实", () => {
  const store = fresh(), before = store.getSnapshot();
  assert.equal(store.createPlatformModelDeployment(input({ parameterSource: "" }), ACTOR).reason, "MODEL_CONFIRMATION_REQUIRED");
  assert.equal(store.createPlatformModelDeployment(input({ modelConfirmed: false }), ACTOR).reason, "MODEL_CONFIRMATION_REQUIRED");
  assert.deepEqual(store.getSnapshot(), before);
});
test("未知候选必须显式选择类型而非继承连接意图", () => {
  const store = fresh();
  assert.equal(store.createPlatformModelDeployment(input({ modelId: "model-unconfirmed-s2", capability: "" }), ACTOR).reason, "MODEL_CONFIRMATION_REQUIRED");
});
test("未知候选保存独立分类事实且不改写原始候选", () => {
  const store = fresh(), result = store.createPlatformModelDeployment(input({ modelId: "model-unconfirmed-s2" }), ACTOR);
  assert.equal(result.ok, true);
  assert.equal(result.deployment.modelConfirmation.origin, "CONFIRMED_BY_ADMIN");
  assert.equal(result.deployment.modelConfirmation.confirmedBy, ACTOR);
  const snapshot = store.getSnapshot();
  assert.equal(snapshot.models.platformCandidateConfirmations.length, 1);
  assert.equal(snapshot.models.platformConnections.find(x => x.id === "pc-chat").discoveredModels.find(x => x.id === "model-unconfirmed-s2").capability, "待确认");
  assert.deepEqual(restored(snapshot).getSnapshot().models.platformCandidateConfirmations, snapshot.models.platformCandidateConfirmations);
});
test("草稿冻结来源版本与参数，拟授权在发布前不生效", () => {
  const store = fresh(), result = store.createPlatformModelDeployment(input(), ACTOR);
  assert.equal(result.ok, true);
  assert.equal(result.deployment.connectionVersion, 4);
  assert.equal(result.deployment.residency, "华东受控区");
  assert.equal(result.deployment.parameters.outputLimit, 64);
  assert.equal(result.deployment.authorizationStatus, "PLANNED");
  assert.equal(result.deployment.status, "DRAFT");
  assert.equal(store.publishPlatformModelDeployment(result.deployment.id, ACTOR).reason, "DEPLOYMENT_NOT_VERIFIED");
});
test("不猜测或静默丢弃非法租户授权", () => {
  assert.equal(fresh().createPlatformModelDeployment(input({ tenantIds: [] }), ACTOR).reason, "TENANT_AUTHORIZATION_REQUIRED");
  assert.equal(fresh().createPlatformModelDeployment(input({ tenantIds: [DEFAULT_TENANT_ID, "missing-tenant"] }), ACTOR).reason, "TENANT_AUTHORIZATION_INVALID");
});
test("向量模型必须显式确认维度", () => {
  const value = input({ connectionId: "pc-embedding", modelId: "model-embedding-v4", capability: "EMBEDDING", embeddingDimension: 1024 });
  assert.equal(fresh().createPlatformModelDeployment({ ...value, embeddingDimension: 0 }, ACTOR).reason, "MODEL_PARAMETERS_REQUIRED");
  assert.equal(fresh().createPlatformModelDeployment(value, ACTOR).deployment.parameters.embeddingDimension, 1024);
});
test("重排模型必须显式确认候选上限", () => {
  const value = input({ connectionId: "pc-rerank", modelId: "model-rerank-v2", capability: "RERANK", rerankCandidateLimit: 30 });
  assert.equal(fresh().createPlatformModelDeployment({ ...value, rerankCandidateLimit: null }, ACTOR).reason, "MODEL_PARAMETERS_REQUIRED");
  assert.equal(fresh().createPlatformModelDeployment(value, ACTOR).deployment.parameters.rerankCandidateLimit, 30);
});
test("预算或安全样本失败不能发布，重试不伪造月度费用核验", () => {
  for (const options of [{ budgetExceeded: true }, { safetyFailure: true }]) {
    const store = fresh(), created = store.createPlatformModelDeployment(input(), ACTOR);
    assert.equal(store.verifyPlatformModelDeployment(created.deployment.id, ACTOR, options).deployment.status, "FAILED");
    assert.equal(store.publishPlatformModelDeployment(created.deployment.id, ACTOR).reason, "DEPLOYMENT_NOT_VERIFIED");
    const checked = store.verifyPlatformModelDeployment(created.deployment.id, ACTOR);
    assert.equal(checked.deployment.status, "VERIFIED");
    assert.equal(checked.deployment.verification.monthlyCostVerified, false);
    assert.equal(checked.deployment.verification.mode, "PROTOTYPE_SIMULATION");
    assert.equal(checked.deployment.verification.requestLimit, 3);
  }
});
test("连接修订漂移后旧验证不能发布", () => {
  const store = fresh(), created = store.createPlatformModelDeployment(input(), ACTOR);
  store.verifyPlatformModelDeployment(created.deployment.id, ACTOR);
  const snapshot = store.getSnapshot();
  snapshot.models.platformConnections.find(x => x.id === "pc-chat").version++;
  assert.equal(restored(snapshot).publishPlatformModelDeployment(created.deployment.id, ACTOR).reason, "CONNECTION_CHANGED");
});
test("验证后独立发布才使已选择租户授权生效", () => {
  const store = fresh(), created = store.createPlatformModelDeployment(input(), ACTOR);
  store.verifyPlatformModelDeployment(created.deployment.id, ACTOR);
  const result = store.publishPlatformModelDeployment(created.deployment.id, ACTOR);
  assert.equal(result.ok, true); assert.equal(result.deployment.authorizationStatus, "EFFECTIVE");
  assert.deepEqual(result.deployment.tenantIds, [DEFAULT_TENANT_ID]);
});
test("租户身份不能确认或创建平台部署", () => {
  const store = fresh(), before = store.getSnapshot();
  assert.equal(store.createPlatformModelDeployment(input(), "account-qiao").reason, "FORBIDDEN");
  assert.deepEqual(store.getSnapshot(), before);
});

test("确认必须是明确布尔值而非真假字符串", () => {
  assert.equal(fresh().createPlatformModelDeployment(input({ modelConfirmed: "false" }), ACTOR).reason, "MODEL_CONFIRMATION_REQUIRED");
});
