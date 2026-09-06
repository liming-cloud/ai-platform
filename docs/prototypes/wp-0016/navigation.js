import { DEFAULT_TENANT_ID } from "./prototype-state.js";

const ICON_ROOT = "./assets/icons";

export const NAVIGATION_GROUPS = Object.freeze({
  platform: [
    {
      label: "平台治理",
      items: [
        ["platform-overview", "平台治理概览", "shield-task.svg"],
        ["platform-tenants", "租户管理", "building-people.svg"],
        ["platform-accounts", "平台账户", "people-community.svg"],
      ],
    },
    {
      label: "治理",
      items: [
        ["platform-model-connections", "模型治理", "brain-circuit.svg"],
      ],
    },
    {
      label: "运行",
      items: [
        ["platform-health", "平台运行状态", "pulse.svg"],
        ["backup-recovery", "备份与恢复", "arrow-sync-circle.svg"],
      ],
    },
  ],
  tenant: [
    {
      label: "租户工作区",
      items: [
        ["tenant-overview", "工作区概览", "home.svg"],
        ["tenant-members", "成员管理", "people-community.svg"],
      ],
    },
    {
      label: "内容",
      items: [
        ["knowledge-directory", "知识管理", "book-database.svg", "knowledgeManagement"],
        ["knowledge-sources", "文档与来源", "document-text.svg", "knowledgeManagement"],
      ],
    },
    {
      label: "治理",
      items: [
        ["access-policies", "访问治理", "shield-task.svg"],
        ["model-providers", "模型治理", "brain-circuit.svg", "modelGovernance"],
      ],
    },
    {
      label: "交付",
      items: [
        ["assistants-directory", "助手交付", "bot.svg", "assistantDelivery"],
        ["evaluation-datasets", "评测治理", "data-usage.svg", "evaluationGovernance"],
      ],
    },
  ],
});

const PARENT_SCREEN = Object.freeze({
  "platform-tenant-create": "platform-tenants",
  "platform-tenant-detail": "platform-tenants",
  "platform-roles": "platform-accounts",
  "platform-model-connection-detail": "platform-model-connections",
  "platform-model-deployments": "platform-model-connections",
  "platform-model-deployment-detail": "platform-model-connections",
  "tenant-roles": "tenant-members",
  "access-policy-detail": "access-policies",
  "device-credentials": "access-policies",
  "knowledge-overview": "knowledge-directory",
  "source-add": "knowledge-sources",
  "source-detail": "knowledge-sources",
  "processing-quality": "knowledge-sources",
  "sync-result": "knowledge-sources",
  "dataset-schema": "knowledge-directory",
  "object-storage-scope": "knowledge-directory",
  "quick-publication": "knowledge-directory",
  "index-versions": "knowledge-directory",
  "index-detail": "knowledge-directory",
  "index-rebuild": "knowledge-directory",
  "model-provider-detail": "model-providers",
  "model-configurations": "model-providers",
  "model-configuration-detail": "model-providers",
  "model-deployments": "model-providers",
  "model-deployment-detail": "model-providers",
  "routing-policies": "model-providers",
  "assistant-create": "assistants-directory",
  "assistant-draft": "assistants-directory",
  "assistant-test": "assistants-directory",
  "assistant-evaluation": "assistants-directory",
  "assistant-release": "assistants-directory",
  "channel-binding": "assistants-directory",
  "evaluation-dataset-detail": "evaluation-datasets",
  "evaluation-run-create": "evaluation-datasets",
  "evaluation-run-detail": "evaluation-datasets",
  "evaluation-comparison": "evaluation-datasets",
  "evaluation-failure-analysis": "evaluation-datasets",
  "governance-query": "evaluation-datasets",
  "usage-quotas": "evaluation-datasets",
  "audit-records": "evaluation-datasets",
  "audit-record-detail": "evaluation-datasets",
  "query-diagnostic": "evaluation-datasets",
  "ingestion-queue": "platform-health",
  "model-health": "platform-health",
  "capacity-planning": "platform-health",
  "release-operations": "platform-health",
});

export function navigationParent(screen) {
  return PARENT_SCREEN[screen] || screen;
}

function tenantFeatureState(snapshot) {
  return snapshot?.tenants?.find((tenant) => tenant.id === DEFAULT_TENANT_ID)
    ?.availableFeatures;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function renderItem(item, activeScreen) {
  const [screen, label, icon] = item;
  const current = navigationParent(activeScreen) === screen;
  return [
    `<button class="nav-item" type="button" data-nav-screen="${escapeHtml(screen)}" data-od-id="nav-${escapeHtml(screen)}"${current ? ' aria-current="page"' : ""}>`,
    `<img class="nav-icon" src="${ICON_ROOT}/${escapeHtml(icon)}" width="24" height="24" alt="" aria-hidden="true" />`,
    `<span class="nav-label">${escapeHtml(label)}</span>`,
    "</button>",
  ].join("");
}

/** 同一人员身份驱动两种控制台外壳；账户菜单只展示本人并提供真实退出动作。 */
export function renderAccountMenu(snapshot) {
  const session = snapshot?.query?.session;
  const account = snapshot?.accounts?.find(item => item.id === session?.accountId);
  if (!account) return "";
  return `<details class="account-menu account-actions-menu"><summary class="button subtle account-button">${account.platformAdministrator ? "平台管理员" : "租户管理员"} / 账户菜单</summary><div class="account-actions-popover"><strong>${escapeHtml(account.name)}</strong><small>${escapeHtml(account.loginName)}</small><button class="button subtle" type="button" data-console-logout>退出登录</button></div></details>`;
}

/** 专属页与共享页使用同一导航来源；功能投影只决定展示，不代表后端授权。 */
export function renderConsoleNavigation({
  screen,
  scope,
  snapshot,
  tenantLabel = "云杉服务示例组织",
}) {
  const groups = NAVIGATION_GROUPS[scope] || NAVIGATION_GROUPS.tenant;
  const featureState = scope === "tenant" ? tenantFeatureState(snapshot) : null;
  const filteredGroups = groups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !item[3] || featureState?.[item[3]] !== false),
    }))
    .filter((group) => group.items.length > 0);
  const scopeLabel =
    scope === "platform" ? "平台范围 / 脱敏治理" : `租户范围 / ${tenantLabel}`;
  return [
    `<nav class="admin-navigation" aria-label="${scope === "platform" ? "平台范围" : "租户范围"}导航" data-od-id="${scope}-navigation">`,
    `<span class="navigation-scope">${escapeHtml(scopeLabel)}</span>`,
    filteredGroups
      .map(
        (group) =>
          `<section class="nav-group" aria-label="${escapeHtml(group.label)}"><span class="nav-group-label">${escapeHtml(group.label)}</span>${group.items.map((item) => renderItem(item, screen)).join("")}</section>`,
      )
      .join(""),
    "</nav>",
  ].join("");
}
