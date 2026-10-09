import { ApiRequestError, type AccessPolicyExpected, type AccessPolicyInput, type AccessPolicyView } from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";

export const policyRoute = (value: AccessPolicyView): Extract<ConsoleRoute, { task: "policy-detail" }> => ({ navigationId: "access-policies", task: "policy-detail", policyId: value.policyId, versionId: value.versionId });
export const policyStatusLabel = (status: string) => status === "PUBLISHED" ? "已发布" : status === "SUPERSEDED" ? "已保留" : "草稿";
/** 发布只提交最后一次服务器读取的版本，不能把未保存选择混入发布命令。 */
export const policyExpected = (value: AccessPolicyView): AccessPolicyExpected => ({ expectedVersion: value.version, expectedAuthorizationRevision: value.authorizationRevision, expectedTenantFeatureVersion: value.tenantFeatureVersion });
export const policyInput = (value: AccessPolicyView): AccessPolicyInput => ({ ...policyExpected(value), assistantId: value.assistantId, membershipIds: [...value.membershipIds] });

/** 读取拒绝不确认资源是否存在；原始异常、响应和跟踪信息不能进入状态文案。 */
export function policyReadFailure(error: unknown) {
  return error instanceof ApiRequestError && error.status === 403
    ? { kind: "denied" as const, summary: "访问受限", title: "当前会话不能访问此任务", description: "页面不会确认目标资源是否存在，也不会加载正文、引用或历史消息。", action: "返回已授权入口" }
    : { kind: "error" as const, summary: "载入失败", title: "暂时无法加载访问策略版本", description: "当前任务没有完成，也没有使用过期结果。请检查连接后安全重试。", action: "重新加载" };
}
