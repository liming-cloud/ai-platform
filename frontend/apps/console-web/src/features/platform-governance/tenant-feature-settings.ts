import type { TenantFeatureCode, TenantFeatureFlags } from "../../api/control-plane-client";

/** 与已验收 OpenDesign 页面逐项对应的租户功能目录。 */
export const TENANT_FEATURES = [
  { code: "knowledgeManagement", label: "知识管理", help: "关闭后租户内对应入口和直接访问同时受限。" },
  { code: "modelGovernance", label: "模型治理", help: "模型配置、部署与路由归入治理。" },
  { code: "assistantDelivery", label: "助手交付", help: "关闭后租户内对应入口和直接访问同时受限。" },
  { code: "evaluationGovernance", label: "评测治理", help: "关闭后租户内对应入口和直接访问同时受限。" },
  { code: "assistantSelection", label: "选择已授权助手", help: "关闭后租户内对应入口和直接访问同时受限。" },
  { code: "conversation", label: "发起与继续对话", help: "关闭后租户内对应入口和直接访问同时受限。" },
  { code: "ownHistory", label: "查看本人历史", help: "关闭后租户内对应入口和直接访问同时受限。" },
  { code: "authorizedCitations", label: "查看有权引用", help: "关闭后租户内对应入口和直接访问同时受限。" },
] as const satisfies readonly { code: TenantFeatureCode; label: string; help: string }[];

export type { TenantFeatureFlags };
