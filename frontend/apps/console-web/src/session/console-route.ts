import type { NAVIGATION } from "./navigation-capability";

/** 统一控制台由 URL 恢复的一级工作区标识。 */
export type ConsoleNavigationId = typeof NAVIGATION[number]["id"];

/** 统一控制台的可恢复工作区状态；列表页码只描述服务端分页位置。 */
export type ConsoleRoute =
  | { navigationId: "operations"; task: "overview"; range: OperationsRange }
  | { navigationId: "access"; task: "members"; page: number; query?: string; status?: "ALL" | "ENABLED" | "DISABLED"; pageSize?: 10 | 20 | 50 | 100 }
  | { navigationId: "access"; task: "member-create"; step: 1 | 2 | 3 }
  | { navigationId: "access"; task: "member-account"; membershipId: string }
  | { navigationId: "access"; task: "member-features"; membershipId: string }
  | { navigationId: "access"; task: "member-assistants"; membershipId: string }
  | { navigationId: "access"; task: "roles" }
  | { navigationId: "access-policies"; task: "policies"; page: number; pageSize?: 10 | 20 | 50 | 100; query?: string; status?: "ALL" | "DRAFT" | "PUBLISHED" }
  | { navigationId: "access-policies"; task: "policy-detail"; policyId: string; versionId: string }
  | { navigationId: "data"; task: "knowledge-bases"; page: number }
  | {
    navigationId: "data";
    task: "governed-file";
    knowledgeSpacePage: number;
    knowledgeSpaceId?: string;
    sourcePage: number;
    embeddingModelConfigurationPage: number;
    embeddingModelConfigurationId?: string;
  }
  | {
    navigationId: "data";
    task: "structured-dataset";
    knowledgeSpacePage: number;
    knowledgeSpaceId?: string;
    embeddingModelConfigurationPage: number;
    embeddingModelConfigurationId?: string;
  }
  | {
    navigationId: "data";
    task: "object-storage";
    knowledgeSpacePage: number;
    knowledgeSpaceId?: string;
    embeddingModelConfigurationPage: number;
    embeddingModelConfigurationId?: string;
  }
  | {
    navigationId: "data";
    task: "first-publication";
    knowledgeSpacePage: number;
    knowledgeSpaceId?: string;
    embeddingModelConfigurationPage: number;
    embeddingModelConfigurationId?: string;
  }
  | {
    navigationId: "models";
    task: "configuration";
    configurationPage: number;
    catalogPage: number;
  }
  | {
    navigationId: "models";
    task: "providers";
    providerPage: number;
    connectionId?: string;
    discoveryPage: number;
  }
  | {
    navigationId: "models";
    task: "deployments";
    connectionId?: string;
    candidatePage: number;
    deploymentPage: number;
  }
  | { navigationId: "governance"; task: "query"; auditPage: number }
  | { navigationId: "governance"; task: "evaluation" }
  | { navigationId: "query"; task: "ask"; assistantPage: number }
  | { navigationId: "platform-operations"; task: "overview"; page: number; range: OperationsRange }
  | { navigationId: "platform"; task: "tenants"; page: number; query?: string;
      status?: "ALL" | "PROVISIONING" | "ACTIVE" | "SUSPENDED"; pageSize?: 10 | 20 | 50 | 100 }
  | { navigationId: "platform"; task: "create"; step: 1 | 2 | 3 }
  | { navigationId: "platform"; task: "detail"; tenantId: string }
  | { navigationId: "platform"; task: "quotas"; tenantId: string }
  | { navigationId: "platform"; task: "features"; tenantId: string }
  | { navigationId: "platform"; task: "security"; tenantId: string }
  | { navigationId: "platform"; task: "admins"; tenantId: string; query: string; status: "ALL" | "ENABLED" | "DISABLED"; page: number; pageSize: 10 | 20 | 50 | 100 }
  | { navigationId: "platform-accounts"; task: "accounts"; query: string;
      status: "ALL" | "ACTIVE" | "DISABLED"; page: number; pageSize: 10 | 20 | 50 | 100 }
  | { navigationId: "platform-accounts"; task: "roles" }
  | { navigationId: "platform-model-supply"; task: "deployment-detail"; deploymentId: string; directoryPage?: number; pageSize?: 10 | 20 | 50 | 100; query?: string }
  | { navigationId: "platform-model-supply"; task: "connections"; page: number; pageSize?: 10 | 20 | 50 | 100; query?: string }
  | { navigationId: "platform-model-supply"; task: "connection-detail"; connectionId: string; candidatePage: number;
      pageSize?: 10 | 20 | 50 | 100; query?: string; directory?: { page: number; pageSize?: 10 | 20 | 50 | 100; query?: string } }
  | {
    navigationId: "platform-model-supply";
    task: "deployments";
    connectionId?: string;
    connectionVersion?: number;
    pageSize?: 10 | 20 | 50 | 100;
    query?: string;
    candidatePage: number;
    deploymentPage: number;
  };

/** 路由解析后的授权结果，拒绝时保留目标以便页面明确说明失败原因。 */
export type AuthorizedConsoleRoute =
  | { kind: "route"; route: ConsoleRoute }
  | { kind: "denied"; route: ConsoleRoute }
  | { kind: "not-found" };

/** 返回一级导航的稳定入口，供根路径首次进入和侧栏切换共同使用。 */
export function defaultConsoleRoute(navigationId: ConsoleNavigationId): ConsoleRoute {
  switch (navigationId) {
    case "operations":
      return { navigationId, task: "overview", range: "DAY_7" };
    case "access":
      return { navigationId, task: "members", page: 1 };
    case "access-policies":
      return { navigationId, task: "policies", page: 1 };
    case "data":
      return { navigationId, task: "knowledge-bases", page: 1 };
    case "models":
      return { navigationId, task: "configuration", configurationPage: 1, catalogPage: 1 };
    case "governance":
      return { navigationId, task: "query", auditPage: 1 };
    case "query":
      return { navigationId, task: "ask", assistantPage: 1 };
    case "platform-operations":
      return { navigationId, task: "overview", page: 1, range: "DAY_7" };
    case "platform":
      return { navigationId, task: "tenants", page: 1, query: "", status: "ALL", pageSize: 20 };
    case "platform-accounts":
      return { navigationId, task: "accounts", query: "", status: "ALL", page: 1, pageSize: 20 };
    case "platform-model-supply":
      return { navigationId, task: "connections", page: 1 };
  }
}

type KnowledgeDataRoute = Extract<ConsoleRoute, { navigationId: "data" }>;

/** 原型导航进入已交付的对应任务，不改变既有深链接及其授权。 */
export function navigationEntryRoute(navigationId: ConsoleNavigationId, tenantAdministrator: boolean): ConsoleRoute {
  if (tenantAdministrator && navigationId === "models") return { navigationId, task: "providers", providerPage: 1, discoveryPage: 1 };
  if (tenantAdministrator && navigationId === "governance") return { navigationId, task: "evaluation" };
  return defaultConsoleRoute(navigationId);
}

export type KnowledgeDataTask = KnowledgeDataRoute["task"];

/** 只接受统一控制台发布的知识库子任务标识，未知值失败关闭。 */
export function isKnowledgeDataTask(value: string): value is KnowledgeDataTask {
  return value === "knowledge-bases"
    || value === "governed-file"
    || value === "structured-dataset"
    || value === "object-storage"
    || value === "first-publication";
}

/**
 * 在知识库子任务之间保留显式选择和服务端页码。
 *
 * 目录点击可以显式覆盖知识库；普通标签切换不得猜测、清空或改选其他知识库。
 */
export function knowledgeDataTaskRoute(
  current: KnowledgeDataRoute,
  targetTask: KnowledgeDataTask,
  selectedKnowledgeSpaceId?: string,
): KnowledgeDataRoute {
  const knowledgeSpacePage = current.task === "knowledge-bases"
    ? current.page
    : current.knowledgeSpacePage;
  if (targetTask === "knowledge-bases") {
    return {
      navigationId: "data",
      task: "knowledge-bases",
      page: knowledgeSpacePage,
    };
  }

  const knowledgeSpaceId = selectedKnowledgeSpaceId
    ?? (current.task === "knowledge-bases" ? undefined : current.knowledgeSpaceId);
  const embeddingModelConfigurationPage = current.task === "knowledge-bases"
    ? 1
    : current.embeddingModelConfigurationPage;
  const embeddingModelConfigurationId = current.task === "knowledge-bases"
    ? undefined
    : current.embeddingModelConfigurationId;
  const selection = {
    navigationId: "data" as const,
    knowledgeSpacePage,
    ...(knowledgeSpaceId === undefined ? {} : { knowledgeSpaceId }),
    embeddingModelConfigurationPage,
    ...(embeddingModelConfigurationId === undefined
      ? {}
      : { embeddingModelConfigurationId }),
  };

  switch (targetTask) {
    case "governed-file":
      return {
        ...selection,
        task: "governed-file",
        sourcePage: 1,
      };
    case "structured-dataset":
      return {
        ...selection,
        task: "structured-dataset",
      };
    case "object-storage":
      return {
        ...selection,
        task: "object-storage",
      };
    case "first-publication":
      return {
        ...selection,
        task: "first-publication",
      };
  }
}

/**
 * 仅恢复当前服务端页内真实存在的显式知识库，缺失或越页选择不得回退首项。
 */
export function resolveKnowledgeSpaceIdOnPage(
  requestedId: string | undefined,
  candidates: readonly { id: string }[],
): string | undefined {
  if (requestedId === undefined) {
    return undefined;
  }
  return candidates.some((candidate) => candidate.id === requestedId)
    ? requestedId
    : undefined;
}

/**
 * 只识别控制台拥有的固定同源路由，未知路径不能隐式映射到默认工作区。
 */
export function parseConsoleRoute(pathname: string, search: string): ConsoleRoute | undefined {
  const routePath = normalizedPath(pathname);
  if (routePath === "/operations") {
    return {
      navigationId: "operations",
      task: "overview",
      range: operationsRange(new URLSearchParams(search).get("range")),
    };
  }
  if (routePath === "/knowledge-bases") {
    return {
      navigationId: "data",
      task: "knowledge-bases",
      page: positivePage(new URLSearchParams(search).get("page")),
    };
  }
  if (routePath === "/data/files") {
    const parameters = new URLSearchParams(search);
    return {
      navigationId: "data",
      task: "governed-file",
      knowledgeSpacePage: positivePage(parameters.get("knowledgeSpacePage")),
      knowledgeSpaceId: nonBlankParameter(parameters.get("knowledgeSpaceId")),
      sourcePage: positivePage(parameters.get("sourcePage")),
      embeddingModelConfigurationPage: positivePage(parameters.get("embeddingModelConfigurationPage")),
      embeddingModelConfigurationId: nonBlankParameter(parameters.get("embeddingModelConfigurationId")),
    };
  }
  if (routePath === "/access/members") {
    const parameters = new URLSearchParams(search);
    const query = parameters.get("q") ?? "";
    const status = parameters.get("status");
    const pageSize = Number(parameters.get("size"));
    const requestedPage = positivePage(parameters.get("page"));
    const effectivePageSize = [10, 20, 50, 100].includes(pageSize) ? pageSize : 20;
    // 与 PageQuery 的平台偏移上限一致；无效直接地址先规范化，不放宽服务端保护。
    const page = (requestedPage - 1) * effectivePageSize <= 100_000 ? requestedPage : 1;
    return { navigationId: "access", task: "members", page,
      ...(query ? { query: query.slice(0, 64) } : {}),
      ...(status === "ENABLED" || status === "DISABLED" ? { status } : {}),
      ...([10, 20, 50, 100].includes(pageSize) ? { pageSize: pageSize as 10 | 20 | 50 | 100 } : {}) };
  }
  if (routePath === "/tenant/members/new") {
    const step = Number(new URLSearchParams(search).get("step"));
    return { navigationId: "access", task: "member-create", step: step === 2 || step === 3 ? step : 1 };
  }
  const memberFeatures = /^\/tenant\/members\/([1-9][0-9]{0,18})\/features$/.exec(routePath);
  if (memberFeatures && BigInt(memberFeatures[1]!) <= 9223372036854775807n) {
    return { navigationId: "access", task: "member-features", membershipId: memberFeatures[1]! };
  }
  const memberAssistants = /^\/tenant\/members\/([1-9][0-9]{0,18})\/assistants$/.exec(routePath);
  if (memberAssistants && BigInt(memberAssistants[1]!) <= 9223372036854775807n) {
    return { navigationId: "access", task: "member-assistants", membershipId: memberAssistants[1]! };
  }
  const memberAccount = /^\/tenant\/members\/([1-9][0-9]{0,18})$/.exec(routePath);
  if (memberAccount && BigInt(memberAccount[1]!) <= 9223372036854775807n) {
    return { navigationId: "access", task: "member-account", membershipId: memberAccount[1]! };
  }
  if (routePath === "/access/roles") {
    return { navigationId: "access", task: "roles" };
  }
  if (routePath === "/tenant/access-policies") {
    const parameters = new URLSearchParams(search);
    const size = Number(parameters.get("size"));
    const pageSize = [10, 20, 50, 100].includes(size) ? size as 10 | 20 | 50 | 100 : 20;
    const requested = positivePage(parameters.get("page"));
    const page = (requested - 1) * pageSize <= 100_000 ? requested : 1;
    const status = parameters.get("status");
    const query = parameters.get("q")?.slice(0, 80);
    return { navigationId: "access-policies", task: "policies", page,
      ...([10, 20, 50, 100].includes(size) ? { pageSize } : {}), ...(query ? { query } : {}),
      ...(status === "DRAFT" || status === "PUBLISHED" ? { status } : {}) };
  }
  const policy = /^\/tenant\/access-policies\/([1-9][0-9]{0,18})\/versions\/([1-9][0-9]{0,18})$/.exec(routePath);
  if (policy && BigInt(policy[1]!) <= 9223372036854775807n && BigInt(policy[2]!) <= 9223372036854775807n) {
    return { navigationId: "access-policies", task: "policy-detail", policyId: policy[1]!, versionId: policy[2]! };
  }
  if (routePath === "/data/datasets") {
    const parameters = new URLSearchParams(search);
    return {
      navigationId: "data",
      task: "structured-dataset",
      knowledgeSpacePage: positivePage(parameters.get("knowledgeSpacePage")),
      knowledgeSpaceId: nonBlankParameter(parameters.get("knowledgeSpaceId")),
      embeddingModelConfigurationPage: positivePage(parameters.get("embeddingModelConfigurationPage")),
      embeddingModelConfigurationId: nonBlankParameter(parameters.get("embeddingModelConfigurationId")),
    };
  }
  if (routePath === "/data/object-storage") {
    const parameters = new URLSearchParams(search);
    return {
      navigationId: "data",
      task: "object-storage",
      knowledgeSpacePage: positivePage(parameters.get("knowledgeSpacePage")),
      knowledgeSpaceId: nonBlankParameter(parameters.get("knowledgeSpaceId")),
      embeddingModelConfigurationPage: positivePage(parameters.get("embeddingModelConfigurationPage")),
      embeddingModelConfigurationId: nonBlankParameter(parameters.get("embeddingModelConfigurationId")),
    };
  }
  if (routePath === "/data/publication") {
    const parameters = new URLSearchParams(search);
    return {
      navigationId: "data",
      task: "first-publication",
      knowledgeSpacePage: positivePage(parameters.get("knowledgeSpacePage")),
      knowledgeSpaceId: nonBlankParameter(parameters.get("knowledgeSpaceId")),
      embeddingModelConfigurationPage: positivePage(parameters.get("embeddingModelConfigurationPage")),
      embeddingModelConfigurationId: nonBlankParameter(parameters.get("embeddingModelConfigurationId")),
    };
  }
  if (routePath === "/models/configurations") {
    const parameters = new URLSearchParams(search);
    return {
      navigationId: "models",
      task: "configuration",
      configurationPage: positivePage(parameters.get("configurationPage")),
      catalogPage: positivePage(parameters.get("catalogPage")),
    };
  }
  if (routePath === "/models/providers") {
    const parameters = new URLSearchParams(search);
    return {
      navigationId: "models",
      task: "providers",
      providerPage: positivePage(parameters.get("providerPage")),
      connectionId: nonBlankParameter(parameters.get("connectionId")),
      discoveryPage: positivePage(parameters.get("discoveryPage")),
    };
  }
  if (routePath === "/models/deployments") {
    const parameters = new URLSearchParams(search);
    return {
      navigationId: "models",
      task: "deployments",
      connectionId: nonBlankParameter(parameters.get("connectionId")),
      candidatePage: positivePage(parameters.get("candidatePage")),
      deploymentPage: positivePage(parameters.get("deploymentPage")),
    };
  }
  if (routePath === "/governance/query") {
    return {
      navigationId: "governance",
      task: "query",
      auditPage: positivePage(new URLSearchParams(search).get("auditPage")),
    };
  }
  if (routePath === "/governance/evaluation") {
    return { navigationId: "governance", task: "evaluation" };
  }
  if (routePath === "/ask") {
    return {
      navigationId: "query",
      task: "ask",
      assistantPage: positivePage(new URLSearchParams(search).get("assistantPage")),
    };
  }
  if (routePath === "/platform") {
    return { navigationId: "platform-operations", task: "overview", page: 1, range: "DAY_7" };
  }
  if (routePath === "/platform/operations") {
    const parameters = new URLSearchParams(search);
    return {
      navigationId: "platform-operations",
      task: "overview",
      page: positivePage(parameters.get("page")),
      range: operationsRange(parameters.get("range")),
    };
  }
  if (routePath === "/platform/tenants") {
    const parameters = new URLSearchParams(search);
    const status = parameters.get("status");
    return {
      navigationId: "platform",
      task: "tenants",
      page: positivePage(parameters.get("page")),
      query: parameters.get("q")?.trim() ?? "",
      status: status === "PROVISIONING" || status === "ACTIVE" || status === "SUSPENDED" ? status : "ALL",
      pageSize: [10, 20, 50, 100].includes(Number(parameters.get("pageSize")))
        ? Number(parameters.get("pageSize")) as 10 | 20 | 50 | 100 : 20,
    };
  }
  if (routePath === "/platform/tenants/new") {
    return {
      navigationId: "platform",
      task: "create",
      step: provisioningStep(new URLSearchParams(search).get("step")),
    };
  }
  if (routePath === "/platform/accounts") {
    const parameters = new URLSearchParams(search);
    const status = parameters.get("status");
    return {
      navigationId: "platform-accounts",
      task: "accounts",
      query: parameters.get("q")?.trim() ?? "",
      status: status === "ACTIVE" || status === "DISABLED" ? status : "ALL",
      page: positivePage(parameters.get("page")),
      pageSize: [10, 20, 50, 100].includes(Number(parameters.get("pageSize")))
        ? Number(parameters.get("pageSize")) as 10 | 20 | 50 | 100 : 20,
    };
  }
  const tenantDetail = /^\/platform\/tenants\/([1-9]\d*)$/.exec(routePath);
  if (tenantDetail) return { navigationId: "platform", task: "detail", tenantId: tenantDetail[1]! };
  const tenantQuotas = /^\/platform\/tenants\/([1-9]\d*)\/quotas$/.exec(routePath);
  if (tenantQuotas) return { navigationId: "platform", task: "quotas", tenantId: tenantQuotas[1]! };
  const tenantFeatures = /^\/platform\/tenants\/([1-9]\d*)\/features$/.exec(routePath);
  if (tenantFeatures) return { navigationId: "platform", task: "features", tenantId: tenantFeatures[1]! };
  const tenantSecurity = /^\/platform\/tenants\/([1-9]\d*)\/security$/.exec(routePath);
  if (tenantSecurity) return { navigationId: "platform", task: "security", tenantId: tenantSecurity[1]! };
  const tenantAdministrators = /^\/platform\/tenants\/([1-9]\d*)\/admins$/.exec(routePath);
  if (tenantAdministrators) {
    const parameters = new URLSearchParams(search);
    const status = parameters.get("status");
    return {
      navigationId: "platform",
      task: "admins",
      tenantId: tenantAdministrators[1]!,
      query: parameters.get("q")?.trim() ?? "",
      status: status === "ENABLED" || status === "DISABLED" ? status : "ALL",
      page: positivePage(parameters.get("page")),
      pageSize: [10, 20, 50, 100].includes(Number(parameters.get("pageSize")))
        ? Number(parameters.get("pageSize")) as 10 | 20 | 50 | 100 : 20,
    };
  }
  if (routePath === "/platform/roles") {
    return { navigationId: "platform-accounts", task: "roles" };
  }
  if (routePath === "/platform/model-supply") {
    const parameters = new URLSearchParams(search);
    const pagination = platformConnectionPagination(parameters, "page");
    return {
      navigationId: "platform-model-supply",
      task: "connections",
      page: pagination.page,
      ...(pagination.size === undefined ? {} : { pageSize: pagination.size }),
      ...(parameters.has("q") ? { query: parameters.get("q")! } : {}),
    };
  }
  const platformDeployment = /^\/platform\/model-supply\/deployments\/([1-9][0-9]*)$/.exec(routePath);
  if (platformDeployment) {
    const parameters = new URLSearchParams(search);
    return { navigationId: "platform-model-supply", task: "deployment-detail", deploymentId: platformDeployment[1]!,
      directoryPage: positivePage(parameters.get("directoryPage")), pageSize: platformConnectionPagination(parameters, "page").size ?? 20,
      ...(parameters.has("directoryQuery") ? { query: parameters.get("directoryQuery")! } : {}) };
  }
  if (routePath === "/platform/model-supply/deployments") {
    const parameters = new URLSearchParams(search);
    const version = parameters.get("connectionVersion");
    return {
      navigationId: "platform-model-supply",
      task: "deployments",
      connectionId: nonBlankParameter(parameters.get("connectionId")),
      ...(platformConnectionPagination(parameters, "page").size ? { pageSize: platformConnectionPagination(parameters, "page").size } : {}),
      connectionVersion: version !== null && /^\d+$/.test(version) ? Number(version) : undefined,
      candidatePage: positivePage(parameters.get("candidatePage")),
      deploymentPage: positivePage(parameters.get("deploymentPage")),
      ...(parameters.has("q") ? { query: parameters.get("q")! } : {}),
    };
  }
  const platformConnection = /^\/platform\/model-supply\/connections\/([1-9][0-9]{0,18})$/.exec(routePath);
  if (platformConnection && BigInt(platformConnection[1]!) <= 9223372036854775807n) {
    const parameters = new URLSearchParams(search);
    const pagination = platformConnectionPagination(parameters, "candidatePage");
    const directoryParameters = new URLSearchParams({ page: parameters.get("directoryPage") ?? "1" });
    if (parameters.has("directorySize")) directoryParameters.set("size", parameters.get("directorySize")!);
    const directory = platformConnectionPagination(directoryParameters, "page");
    return { navigationId: "platform-model-supply", task: "connection-detail", connectionId: platformConnection[1]!,
      candidatePage: pagination.page, ...(pagination.size === undefined ? {} : { pageSize: pagination.size }),
      ...(parameters.has("q") ? { query: parameters.get("q")! } : {}),
      ...(parameters.has("directoryPage") ? { directory: { page: directory.page,
        ...(directory.size === undefined ? {} : { pageSize: directory.size }),
        ...(parameters.has("directoryQuery") ? { query: parameters.get("directoryQuery")! } : {}) } } : {}) };
  }
  return undefined;
}

/** 将可恢复状态编码为规范 URL，避免页内状态与浏览器地址不一致。 */
export function formatConsoleRoute(route: ConsoleRoute): string {
  if (route.navigationId === "operations") {
    return `/operations?range=${route.range}`;
  }
  if (route.navigationId === "access") {
    if (route.task === "member-create") return `/tenant/members/new?step=${route.step}`;
    if (route.task === "member-account") return `/tenant/members/${encodeURIComponent(route.membershipId)}`;
    if (route.task === "member-features") return `/tenant/members/${encodeURIComponent(route.membershipId)}/features`;
    if (route.task === "member-assistants") return `/tenant/members/${encodeURIComponent(route.membershipId)}/assistants`;
    if (route.task === "roles") return "/access/roles";
    const parameters = new URLSearchParams({ page: String(route.page) });
    if (route.query) parameters.set("q", route.query);
    if (route.status && route.status !== "ALL") parameters.set("status", route.status);
    if (route.pageSize) parameters.set("size", String(route.pageSize));
    return `/access/members?${parameters}`;
  }
  if (route.navigationId === "access-policies") {
    if (route.task === "policy-detail") return `/tenant/access-policies/${encodeURIComponent(route.policyId)}/versions/${encodeURIComponent(route.versionId)}`;
    const parameters = new URLSearchParams({ page: String(route.page) });
    if (route.pageSize) parameters.set("size", String(route.pageSize));
    if (route.query) parameters.set("q", route.query);
    if (route.status && route.status !== "ALL") parameters.set("status", route.status);
    return `/tenant/access-policies?${parameters}`;
  }
  if (route.navigationId === "data") {
    return route.task === "knowledge-bases"
      ? `/knowledge-bases?page=${route.page}`
      : route.task === "governed-file"
        ? formatGovernedFileRoute(route)
        : route.task === "structured-dataset"
          ? formatStructuredDatasetRoute(route)
          : route.task === "object-storage"
            ? formatObjectStorageRoute(route)
            : formatPublicationRoute(route);
  }
  if (route.navigationId === "models") {
    return route.task === "configuration"
      ? formatConfigurationRoute(route)
      : route.task === "providers"
        ? formatProviderRoute(route)
        : formatDeploymentRoute(route);
  }
  if (route.navigationId === "governance") {
    return route.task === "query"
      ? `/governance/query?auditPage=${route.auditPage}`
      : "/governance/evaluation";
  }
  if (route.navigationId === "query") {
    return `/ask?assistantPage=${route.assistantPage}`;
  }
  if (route.navigationId === "platform-operations") {
    return `/platform/operations?range=${route.range}&page=${route.page}`;
  }
  if (route.navigationId === "platform") {
    return route.task === "tenants"
      ? `/platform/tenants?${new URLSearchParams({ q: route.query ?? "", status: route.status ?? "ALL",
          page: String(route.page), pageSize: String(route.pageSize ?? 20) })}`
      : route.task === "detail" ? `/platform/tenants/${encodeURIComponent(route.tenantId)}`
        : route.task === "quotas" ? `/platform/tenants/${encodeURIComponent(route.tenantId)}/quotas`
        : route.task === "features" ? `/platform/tenants/${encodeURIComponent(route.tenantId)}/features`
        : route.task === "security" ? `/platform/tenants/${encodeURIComponent(route.tenantId)}/security`
        : route.task === "admins" ? `/platform/tenants/${encodeURIComponent(route.tenantId)}/admins?${new URLSearchParams({ q: route.query, status: route.status, page: String(route.page), pageSize: String(route.pageSize) })}`
        : `/platform/tenants/new?step=${route.step}`;
  }
  if (route.navigationId === "platform-accounts") {
    return route.task === "accounts"
      ? `/platform/accounts?${new URLSearchParams({ q: route.query, status: route.status,
        page: String(route.page), pageSize: String(route.pageSize) })}`
      : "/platform/roles";
  }
  if (route.task === "deployment-detail") {
    const parameters = new URLSearchParams({ directoryPage: String(route.directoryPage ?? 1), size: String(route.pageSize ?? 20) });
    if (route.query !== undefined) parameters.set("directoryQuery", route.query);
    return `/platform/model-supply/deployments/${route.deploymentId}?${parameters}`;
  }
  if (route.task === "deployments") return formatPlatformDeploymentRoute(route);
  const parameters = new URLSearchParams(route.task === "connections" ? { page: String(route.page) } : { candidatePage: String(route.candidatePage) });
  if (route.pageSize !== undefined) parameters.set("size", String(route.pageSize));
  if (route.query !== undefined) parameters.set("q", route.query);
  if (route.task === "connection-detail" && route.directory) {
    parameters.set("directoryPage", String(route.directory.page));
    if (route.directory.pageSize !== undefined) parameters.set("directorySize", String(route.directory.pageSize));
    if (route.directory.query !== undefined) parameters.set("directoryQuery", route.directory.query);
  }
  return `${route.task === "connections" ? "/platform/model-supply" : `/platform/model-supply/connections/${encodeURIComponent(route.connectionId)}`}?${parameters}`;
}

/**
 * 页面入口按服务端会话能力关闭；该判断只保护浏览器呈现，接口仍由服务端授权。
 */
export function resolveAuthorizedConsoleRoute(
  route: ConsoleRoute | undefined,
  allowedNavigationIds: readonly ConsoleNavigationId[],
): AuthorizedConsoleRoute {
  if (route === undefined) {
    return { kind: "not-found" };
  }
  return allowedNavigationIds.includes(route.navigationId)
    ? { kind: "route", route }
    : { kind: "denied", route };
}

function normalizedPath(pathname: string): string {
  const path = pathname.replace(/\/+$/, "");
  return path.length === 0 ? "/" : path;
}

function positivePage(value: string | null): number {
  if (value === null || !/^[1-9]\d*$/.test(value)) {
    return 1;
  }
  return Number(value);
}

/** 平台连接只恢复已接受的页大小及有界偏移，不把失控数字传给服务端。 */
function platformConnectionPagination(parameters: URLSearchParams, key: "page" | "candidatePage"): {
  page: number; size?: 10 | 20 | 50 | 100;
} {
  const rawSize = Number(parameters.get("size"));
  const size = parameters.has("size") ? ([10, 20, 50, 100].includes(rawSize) ? rawSize : 20) as 10 | 20 | 50 | 100 : undefined;
  const requested = positivePage(parameters.get(key));
  return { page: Number.isSafeInteger(requested) && (requested - 1) * (size ?? 20) <= 100_000 ? requested : 1, size };
}

function provisioningStep(value: string | null): 1 | 2 | 3 {
  return value === "2" ? 2 : value === "3" ? 3 : 1;
}

type OperationsRange = "DAY_1" | "DAY_7" | "DAY_30";

function operationsRange(value: string | null): OperationsRange {
  return value === "DAY_1" || value === "DAY_30" ? value : "DAY_7";
}

function nonBlankParameter(value: string | null): string | undefined {
  return value === null || value.trim().length === 0 ? undefined : value;
}

function formatProviderRoute(route: Extract<ConsoleRoute, { task: "providers" }>): string {
  const parameters = new URLSearchParams();
  parameters.set("providerPage", String(route.providerPage));
  if (route.connectionId !== undefined) {
    parameters.set("connectionId", route.connectionId);
  }
  parameters.set("discoveryPage", String(route.discoveryPage));
  return `/models/providers?${parameters}`;
}

function formatPlatformDeploymentRoute(route: Extract<ConsoleRoute, { navigationId: "platform-model-supply"; task: "deployments" }>): string {
  const parameters = new URLSearchParams();
  if (route.connectionId !== undefined) parameters.set("connectionId", route.connectionId);
  if (route.connectionVersion !== undefined) parameters.set("connectionVersion", String(route.connectionVersion));
  parameters.set("candidatePage", String(route.candidatePage));
  parameters.set("deploymentPage", String(route.deploymentPage));
  if (route.pageSize !== undefined) parameters.set("size", String(route.pageSize));
  if (route.query !== undefined) parameters.set("q", route.query);
  return `/platform/model-supply/deployments?${parameters}`;
}

function formatConfigurationRoute(route: Extract<ConsoleRoute, { task: "configuration" }>): string {
  const parameters = new URLSearchParams();
  parameters.set("configurationPage", String(route.configurationPage));
  parameters.set("catalogPage", String(route.catalogPage));
  return `/models/configurations?${parameters}`;
}

function formatGovernedFileRoute(route: Extract<ConsoleRoute, { task: "governed-file" }>): string {
  const parameters = new URLSearchParams();
  parameters.set("knowledgeSpacePage", String(route.knowledgeSpacePage));
  if (route.knowledgeSpaceId !== undefined) {
    parameters.set("knowledgeSpaceId", route.knowledgeSpaceId);
  }
  parameters.set("sourcePage", String(route.sourcePage));
  appendEmbeddingModelConfigurationSelection(parameters, route);
  return `/data/files?${parameters}`;
}

function formatStructuredDatasetRoute(route: Extract<ConsoleRoute, { task: "structured-dataset" }>): string {
  const parameters = new URLSearchParams();
  parameters.set("knowledgeSpacePage", String(route.knowledgeSpacePage));
  if (route.knowledgeSpaceId !== undefined) {
    parameters.set("knowledgeSpaceId", route.knowledgeSpaceId);
  }
  appendEmbeddingModelConfigurationSelection(parameters, route);
  return `/data/datasets?${parameters}`;
}

function formatObjectStorageRoute(route: Extract<ConsoleRoute, { task: "object-storage" }>): string {
  const parameters = new URLSearchParams();
  parameters.set("knowledgeSpacePage", String(route.knowledgeSpacePage));
  if (route.knowledgeSpaceId !== undefined) {
    parameters.set("knowledgeSpaceId", route.knowledgeSpaceId);
  }
  appendEmbeddingModelConfigurationSelection(parameters, route);
  return `/data/object-storage?${parameters}`;
}

function formatPublicationRoute(route: Extract<ConsoleRoute, { task: "first-publication" }>): string {
  const parameters = new URLSearchParams();
  parameters.set("knowledgeSpacePage", String(route.knowledgeSpacePage));
  if (route.knowledgeSpaceId !== undefined) {
    parameters.set("knowledgeSpaceId", route.knowledgeSpaceId);
  }
  parameters.set("embeddingModelConfigurationPage", String(route.embeddingModelConfigurationPage));
  if (route.embeddingModelConfigurationId !== undefined) {
    parameters.set("embeddingModelConfigurationId", route.embeddingModelConfigurationId);
  }
  return `/data/publication?${parameters.toString()}`;
}

function appendEmbeddingModelConfigurationSelection(
  parameters: URLSearchParams,
  route: Extract<ConsoleRoute, { task: "governed-file" | "structured-dataset" | "object-storage" }>,
): void {
  parameters.set("embeddingModelConfigurationPage", String(route.embeddingModelConfigurationPage));
  if (route.embeddingModelConfigurationId !== undefined) {
    parameters.set("embeddingModelConfigurationId", route.embeddingModelConfigurationId);
  }
}

function formatDeploymentRoute(route: Extract<ConsoleRoute, { task: "deployments" }>): string {
  const parameters = new URLSearchParams();
  if (route.connectionId !== undefined) {
    parameters.set("connectionId", route.connectionId);
  }
  parameters.set("candidatePage", String(route.candidatePage));
  parameters.set("deploymentPage", String(route.deploymentPage));
  return `/models/deployments?${parameters}`;
}
