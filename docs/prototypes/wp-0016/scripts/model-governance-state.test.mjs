import test from "node:test";
import assert from "node:assert/strict";
import {
  PROTOTYPE_STATE_KEY,
  createMemoryStorage,
  createPrototypeStore,
} from "../prototype-state.js";

const PLATFORM_ACTOR = "account-platform-admin";
const TENANT_ACTOR = "account-qiao";
const TENANT_ID = "tenant-spruce";

function fresh() {
  const storage = createMemoryStorage();
  return { storage, store: createPrototypeStore(storage) };
}

test("平台连接只接受安全 HTTPS 目标，探测发布后仍不保存访问密钥", () => {
  const { storage, store } = fresh();
  const unsafe = store.createPlatformModelConnection({
    name: "不安全连接",
    runtimeBaseUrl: "http://127.0.0.1:8080/v1",
    capability: "CHAT",
    accessKey: "must-not-persist",
  }, PLATFORM_ACTOR);
  assert.equal(unsafe.reason, "UNSAFE_RUNTIME_ENDPOINT");

  const created = store.createPlatformModelConnection({
    name: "华北对话连接",
    runtimeBaseUrl: "https://models.example.invalid/v1",
    capability: "CHAT",
    accessKey: "ephemeral-key-123",
  }, PLATFORM_ACTOR);
  assert.equal(created.ok, true);
  assert.equal(created.connection.status, "DRAFT");
  assert.doesNotMatch(JSON.stringify(store.getSnapshot()), /ephemeral-key-123|accessKey/);
  const probed = store.probePlatformModelConnection(created.connection.id, PLATFORM_ACTOR);
  assert.equal(probed.connection.status, "VERIFIED");
  assert.equal(probed.connection.discoveredModels.length, 1);
  const published = store.publishPlatformModelConnection(created.connection.id, PLATFORM_ACTOR);
  assert.equal(published.connection.status, "PUBLISHED");
  const restored = createPrototypeStore(storage).getSnapshot();
  assert.equal(restored.models.platformConnections.find(item => item.id === created.connection.id).status, "PUBLISHED");

  const publishedModelId = store.getSnapshot().models.platformConnections
    .find(item => item.id === "pc-chat").discoveredModels[0].id;
  store.probePlatformModelConnection("pc-chat", PLATFORM_ACTOR);
  assert.equal(
    store.getSnapshot().models.platformConnections.find(item => item.id === "pc-chat").discoveredModels[0].id,
    publishedModelId,
    "重新探测已发布连接时不得替换正在被部署引用的目录模型标识",
  );
});

test("平台部署只有连接探测通过后才能发布并明确授权租户", () => {
  const { store } = fresh();
  const created = store.createPlatformModelDeployment({
    name: "企业对话模型 v7",
    connectionId: "pc-chat",
    modelId: "model-chat-v6",
    capability: "CHAT", contextWindow: 4096, outputLimit: 64,
    parameterSource: "合成厂商资料 v1", modelConfirmed: true,
    tenantIds: [TENANT_ID],
  }, PLATFORM_ACTOR);
  assert.equal(created.deployment.status, "DRAFT");
  assert.equal(store.publishPlatformModelDeployment(created.deployment.id, PLATFORM_ACTOR).reason, "DEPLOYMENT_NOT_VERIFIED");
  assert.equal(store.verifyPlatformModelDeployment(created.deployment.id, PLATFORM_ACTOR).deployment.status, "VERIFIED");
  const published = store.publishPlatformModelDeployment(created.deployment.id, PLATFORM_ACTOR);
  assert.equal(published.deployment.status, "PUBLISHED");
  assert.deepEqual(published.deployment.tenantIds, [TENANT_ID]);
});

test("租户发现只返回已发布且已授权模型，配置验证后才能启用", () => {
  const { store } = fresh();
  const discovery = store.discoverTenantModels(TENANT_ID, "pc-chat", TENANT_ACTOR);
  assert.equal(discovery.ok, true);
  assert.ok(discovery.models.every(item => item.status === "PUBLISHED" && item.tenantIds.includes(TENANT_ID)));
  const created = store.createTenantModelConfiguration(TENANT_ID, {
    name: "知识咨询对话配置",
    platformDeploymentId: "pd-chat-v6",
  }, TENANT_ACTOR);
  assert.equal(created.configuration.status, "DRAFT");
  assert.equal(store.enableTenantModelConfiguration(created.configuration.id, TENANT_ACTOR).reason, "CONFIGURATION_NOT_VERIFIED");
  assert.equal(store.verifyTenantModelConfiguration(created.configuration.id, TENANT_ACTOR).configuration.pendingRevision.status, "VERIFIED");
  const enabled = store.enableTenantModelConfiguration(created.configuration.id, TENANT_ACTOR);
  assert.equal(enabled.configuration.status, "ACTIVE");
  assert.equal(enabled.configuration.activeRevision, 1);
});

test("访问密钥轮换在验证通过前保留旧版本且不保存密钥", () => {
  const { storage, store } = fresh();
  const rotated = store.rotateTenantModelAccessKey("cfg-chat", "rotation-key-456", TENANT_ACTOR);
  assert.equal(rotated.configuration.activeRevision, 6);
  assert.equal(rotated.configuration.pendingRevision.status, "DRAFT");
  assert.doesNotMatch(JSON.stringify(store.getSnapshot()), /rotation-key-456|accessKey/);
  store.verifyTenantModelConfiguration("cfg-chat", TENANT_ACTOR);
  const enabled = store.enableTenantModelConfiguration("cfg-chat", TENANT_ACTOR);
  assert.equal(enabled.configuration.activeRevision, 7);
  assert.equal(enabled.configuration.pendingRevision, null);
  assert.deepEqual(enabled.effects.map(item => item.type), ["ASSISTANT_REEVALUATION_REQUIRED"]);
  assert.equal(createPrototypeStore(storage).getSnapshot().models.configurations.find(item => item.id === "cfg-chat").activeRevision, 7);
});

test("Embedding 关键配置启用后自动创建重建候选但不切换活动索引", () => {
  const { store } = fresh();
  const revised = store.createTenantModelConfigurationRevision("cfg-embedding", {
    embeddingDimension: 1536,
    distanceMetric: "COSINE",
  }, TENANT_ACTOR);
  assert.equal(revised.configuration.pendingRevision.status, "DRAFT");
  store.verifyTenantModelConfiguration("cfg-embedding", TENANT_ACTOR);
  const enabled = store.enableTenantModelConfiguration("cfg-embedding", TENANT_ACTOR);
  assert.ok(enabled.effects.some(item => item.type === "FULL_INDEX_REBUILD_CREATED"));
  const handbook = store.getSnapshot().knowledgeBases.find(item => item.id === "kb-handbook");
  assert.equal(handbook.activeIndex, "idx-v11");
  assert.equal(handbook.indexVersions[0].status, "BUILDING");
  assert.equal(handbook.indexVersions[0].config.embeddingDimension, 1536);
});

test("租户配置启用前重新检查平台发布状态和租户授权", () => {
  const { storage, store } = fresh();
  const snapshot = store.getSnapshot();
  snapshot.models.platformDeployments.find(item => item.id === "pd-rerank-v2").tenantIds = [];
  storage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(snapshot));
  const reloaded = createPrototypeStore(storage);
  assert.equal(
    reloaded.enableTenantModelConfiguration("cfg-rerank-candidate", TENANT_ACTOR).reason,
    "MODEL_NOT_AUTHORIZED",
  );
});

test("租户部署经验证发布，停用前必须确认助手影响", () => {
  const { store } = fresh();
  const created = store.createTenantModelDeployment(TENANT_ID, {
    name: "员工服务 Chat 候选",
    configurationId: "cfg-chat",
  }, TENANT_ACTOR);
  assert.equal(store.publishTenantModelDeployment(created.deployment.id, TENANT_ACTOR).reason, "DEPLOYMENT_NOT_VERIFIED");
  store.verifyTenantModelDeployment(created.deployment.id, TENANT_ACTOR);
  assert.equal(store.publishTenantModelDeployment(created.deployment.id, TENANT_ACTOR).deployment.status, "PUBLISHED");
  assert.equal(store.deactivateTenantModelDeployment("td-chat-v6", false, TENANT_ACTOR).reason, "IMPACT_ACK_REQUIRED");
  assert.equal(store.deactivateTenantModelDeployment("td-chat-v6", true, TENANT_ACTOR).deployment.status, "OFFLINE");

  const routeImpact = store.deactivateTenantModelDeployment("td-chat-backup", false, TENANT_ACTOR);
  assert.equal(routeImpact.reason, "IMPACT_ACK_REQUIRED");
  assert.deepEqual(routeImpact.routingPolicyIds, ["route-employee"]);
});

test("路由只允许同能力已发布部署，越权与存储失败整体回滚", () => {
  const { store } = fresh();
  assert.equal(store.simulateRoutingPolicy("route-employee", {
    primaryDeploymentId: "td-chat-v6",
    fallbackDeploymentId: "td-embedding-v4",
    monthlyBudget: 10000,
  }, TENANT_ACTOR).reason, "ROUTING_CAPABILITY_MISMATCH");
  assert.equal(store.simulateRoutingPolicy("route-employee", {
    primaryDeploymentId: "td-chat-v6",
    fallbackDeploymentId: "td-chat-v6",
    monthlyBudget: 10000,
  }, TENANT_ACTOR).reason, "ROUTING_FALLBACK_DUPLICATE");
  const simulated = store.simulateRoutingPolicy("route-employee", {
    primaryDeploymentId: "td-chat-v6",
    fallbackDeploymentId: "td-chat-backup",
    monthlyBudget: 10000,
  }, TENANT_ACTOR);
  assert.equal(simulated.policy.status, "READY_FOR_RELEASE");
  assert.equal(store.publishRoutingPolicy("route-employee", TENANT_ACTOR).policy.status, "PUBLISHED");
  assert.equal(store.createTenantModelConfiguration(TENANT_ID, {
    name: "越权配置",
    platformDeploymentId: "pd-chat-v6",
  }, "account-shen").reason, "FORBIDDEN");

  const memory = createMemoryStorage();
  let rejectWrites = false;
  const guardedStorage = {
    ...memory,
    setItem(key, value) {
      if (rejectWrites) throw new Error("合成存储故障");
      memory.setItem(key, value);
    },
  };
  const guarded = createPrototypeStore(guardedStorage);
  const before = guarded.getSnapshot();
  rejectWrites = true;
  assert.equal(guarded.discoverTenantModels(TENANT_ID, "pc-chat", TENANT_ACTOR).reason, "STORAGE_UNAVAILABLE");
  assert.deepEqual(guarded.getSnapshot(), before);

  const crossTenantStorage = createMemoryStorage();
  const crossTenantStore = createPrototypeStore(crossTenantStorage);
  const crossTenantSnapshot = crossTenantStore.getSnapshot();
  crossTenantSnapshot.models.deployments.push({
    ...crossTenantSnapshot.models.deployments.find(item => item.id === "td-chat-v6"),
    id: "td-cross-tenant",
    tenantId: "tenant-other",
  });
  crossTenantStorage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(crossTenantSnapshot));
  assert.equal(createPrototypeStore(crossTenantStorage).simulateRoutingPolicy("route-employee", {
    primaryDeploymentId: "td-cross-tenant",
    fallbackDeploymentId: "td-chat-backup",
    monthlyBudget: 10000,
  }, TENANT_ACTOR).reason, "ROUTING_DEPLOYMENT_NOT_AVAILABLE");
});
