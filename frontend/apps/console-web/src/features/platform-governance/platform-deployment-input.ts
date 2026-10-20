import type { PlatformDeployableModel, PlatformModelDeploymentCreateInput } from "../../api/control-plane-client";

/** 原型完整表单的显式声明；不猜类型、参数或授权，也不把管理员声明当成实测。 */
export function platformDeploymentInput(values: FormData, model: PlatformDeployableModel | undefined): PlatformModelDeploymentCreateInput {
  if (!model) throw new Error("请选择已发布连接的模型。");
  const displayName = String(values.get("name") ?? "").trim();
  if (Array.from(displayName).length < 2 || Array.from(displayName).length > 80) throw new Error("部署名称须为 2～80 个字符。");
  const modelType = model.modelType ?? String(values.get("capability") ?? "");
  if (!["CHAT", "EMBEDDING", "RERANK"].includes(modelType) || (!model.modelType && modelType !== model.declaredCapability))
    throw new Error("请明确确认当前连接声明的模型类型。");
  const contextWindow = positive(values.get("contextWindow"));
  const outputLimit = modelType === "CHAT" ? positive(values.get("outputLimit")) : null;
  const embeddingDimension = modelType === "EMBEDDING" ? positive(values.get("embeddingDimension")) : null;
  const rerankCandidateLimit = modelType === "RERANK" ? positive(values.get("rerankCandidateLimit")) : null;
  if (outputLimit !== null && outputLimit > contextWindow) throw new Error("最大输出不能超过上下文窗口。");
  const parameterSource = String(values.get("parameterSource") ?? "").trim();
  if (!parameterSource || Array.from(parameterSource).length > 160 || /[\p{Cc}\u2028\u2029]/u.test(parameterSource))
    throw new Error("请填写有界、单行且不含秘密的参数来源。");
  if (values.get("modelConfirmed") !== "on") throw new Error("请明确确认模型类型、能力和参数来源。");
  const plannedTenantIds = [...new Set(values.getAll("authorizeTenant").map(String))].sort();
  if (!plannedTenantIds.length || plannedTenantIds.some(id => !/^[1-9][0-9]*$/.test(id))) throw new Error("请明确选择拟授权租户。");
  return { providerConnectionId: model.connectionId, expectedConnectionVersion: model.connectionRevision, modelCode: model.modelCode,
    displayName, modelType: modelType as PlatformModelDeploymentCreateInput["modelType"], capabilities: model.modelType ? model.capabilities : [modelType],
    contextWindow, outputLimit, embeddingDimension, rerankCandidateLimit, parameterSource, plannedTenantIds, modelConfirmed: true };
}

function positive(value: FormDataEntryValue | null): number {
  const text = typeof value === "string" ? value : "";
  if (!/^[1-9][0-9]*$/.test(text) || !Number.isSafeInteger(Number(text)) || Number(text) > 2_147_483_647)
    throw new Error("请填写当前模型类型所需的正整数参数。");
  return Number(text);
}
