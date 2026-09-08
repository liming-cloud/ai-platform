/** 详情页定位使用 URL；选择只是当前标签页视图，不是共享业务事实。 */
export const DETAIL_CONTEXT = [
  { parameter: "platformTenantId", collection: ["tenants"], selection: ["selectedTenantId"], screens: ["platform-tenant-detail", "platform-tenant-admins", "platform-tenant-quotas", "platform-tenant-features", "platform-tenant-security"] },
  { parameter: "connectionId", collection: ["models", "platformConnections"], selection: ["models", "selectedPlatformConnectionId"], screens: ["platform-model-connection-detail"] },
  { parameter: "platformDeploymentId", collection: ["models", "platformDeployments"], selection: ["models", "selectedPlatformDeploymentId"], screens: ["platform-model-deployment-detail"] },
  { parameter: "providerId", collection: ["models", "platformConnections"], selection: ["models", "selectedTenantConnectionId"], screens: ["model-provider-detail"] },
  { parameter: "configurationId", collection: ["models", "configurations"], selection: ["models", "selectedConfigurationId"], screens: ["model-configuration-detail"] },
  { parameter: "deploymentId", collection: ["models", "deployments"], selection: ["models", "selectedDeploymentId"], screens: ["model-deployment-detail"] },
  { parameter: "routingPolicyId", collection: ["models", "routingPolicies"], selection: ["models", "selectedRoutingPolicyId"], screens: ["routing-policies"] },
  { parameter: "assistantVersionId", collection: ["assistantDelivery", "assistants"], children: "versions", selection: ["assistantDelivery", "selectedVersionId"], screens: ["assistant-overview", "assistant-draft", "assistant-test", "assistant-evaluation", "assistant-release"] },
  { parameter: "datasetId", collection: ["evaluation", "datasets"], selection: ["evaluation", "selectedDatasetId"], screens: ["evaluation-dataset-detail"] },
  { parameter: "datasetVersionId", collection: ["evaluation", "datasets"], children: "versions", selection: ["evaluation", "selectedDatasetVersionId"], screens: ["evaluation-dataset-detail"] },
  { parameter: "evaluationRunId", collection: ["evaluation", "runs"], selection: ["evaluation", "selectedRunId"], screens: ["evaluation-run-detail", "evaluation-failure-analysis"] },
  { parameter: "comparisonId", collection: ["evaluation", "comparisons"], selection: ["evaluation", "selectedComparisonId"], screens: ["evaluation-comparison"] },
  { parameter: "policyId", collection: ["accessGovernance", "accessPolicies"], selection: ["accessGovernance", "selectedPolicyId"], screens: ["access-policy-detail"] },
  { parameter: "queryRunId", collection: ["accessGovernance", "queryRuns"], selection: ["accessGovernance", "selectedQueryRunId"], screens: ["query-diagnostic"] },
];

export const valueAt = (value, path) => path.reduce((current, key) => current?.[key], value);

/** 先全部验证再应用，混合合法与越界参数不能留下部分选择。 */
export function restoreDetailContext(state, scoped, context) {
  const requested = DETAIL_CONTEXT.filter(item => Object.hasOwn(context, item.parameter));
  for (const item of requested) {
    const collection = valueAt(scoped, item.collection) || [];
    const candidates = item.children ? collection.flatMap(parent => parent[item.children]) : collection;
    if (!candidates.some(candidate => candidate.id === context[item.parameter])) return { ok: false, reason: "OBJECT_NOT_AVAILABLE" };
  }
  if (context.datasetId && context.datasetVersionId && !scoped.evaluation.datasets.find(item => item.id === context.datasetId)?.versions.some(item => item.id === context.datasetVersionId)) return { ok: false, reason: "OBJECT_NOT_AVAILABLE" };
  for (const item of requested) {
    valueAt(state, item.selection.slice(0, -1))[item.selection.at(-1)] = context[item.parameter];
  }
  return { ok: true };
}
