import type { AccessPolicyExpected, AccessPolicyInput, AccessPolicyView } from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";

export const policyRoute = (value: AccessPolicyView): Extract<ConsoleRoute, { task: "policy-detail" }> => ({ navigationId: "access-policies", task: "policy-detail", policyId: value.policyId, versionId: value.versionId });
export const policyStatusLabel = (status: string) => status === "PUBLISHED" ? "已发布" : status === "SUPERSEDED" ? "已保留" : "草稿";
/** 发布只提交最后一次服务器读取的版本，不能把未保存选择混入发布命令。 */
export const policyExpected = (value: AccessPolicyView): AccessPolicyExpected => ({ expectedVersion: value.version, expectedAuthorizationRevision: value.authorizationRevision, expectedTenantFeatureVersion: value.tenantFeatureVersion });
export const policyInput = (value: AccessPolicyView): AccessPolicyInput => ({ ...policyExpected(value), assistantId: value.assistantId, membershipIds: [...value.membershipIds] });
