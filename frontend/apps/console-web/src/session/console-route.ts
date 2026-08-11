import type { NAVIGATION } from "./navigation-capability";

/** 统一控制台由 URL 恢复的一级工作区标识。 */
export type ConsoleNavigationId = typeof NAVIGATION[number]["id"];

/** 统一控制台的可恢复工作区状态；列表页码只描述服务端分页位置。 */
export type ConsoleRoute =
  | { navigationId: "access"; task: "members"; page: number }
  | { navigationId: "access"; task: "roles"; page: number }
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
  | { navigationId: "platform"; task: "tenants"; page: number }
  | { navigationId: "platform-accounts"; task: "accounts"; page: number };

/** 路由解析后的授权结果，拒绝时保留目标以便页面明确说明失败原因。 */
export type AuthorizedConsoleRoute =
  | { kind: "route"; route: ConsoleRoute }
  | { kind: "denied"; route: ConsoleRoute }
  | { kind: "not-found" };

/** 返回一级导航的稳定入口，供根路径首次进入和侧栏切换共同使用。 */
export function defaultConsoleRoute(navigationId: ConsoleNavigationId): ConsoleRoute {
  switch (navigationId) {
    case "access":
      return { navigationId, task: "members", page: 1 };
    case "data":
      return {
        navigationId,
        task: "governed-file",
        knowledgeSpacePage: 1,
        sourcePage: 1,
        embeddingModelConfigurationPage: 1,
      };
    case "models":
      return { navigationId, task: "configuration", configurationPage: 1, catalogPage: 1 };
    case "governance":
      return { navigationId, task: "query", auditPage: 1 };
    case "query":
      return { navigationId, task: "ask", assistantPage: 1 };
    case "platform":
      return { navigationId, task: "tenants", page: 1 };
    case "platform-accounts":
      return { navigationId, task: "accounts", page: 1 };
  }
}

/**
 * 只识别控制台拥有的固定同源路由，未知路径不能隐式映射到默认工作区。
 */
export function parseConsoleRoute(pathname: string, search: string): ConsoleRoute | undefined {
  const routePath = normalizedPath(pathname);
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
    return { navigationId: "access", task: "members", page: positivePage(parameters.get("page")) };
  }
  if (routePath === "/access/roles") {
    const parameters = new URLSearchParams(search);
    return { navigationId: "access", task: "roles", page: positivePage(parameters.get("page")) };
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
    return { navigationId: "data", task: "first-publication" };
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
  if (routePath === "/platform/tenants") {
    return {
      navigationId: "platform",
      task: "tenants",
      page: positivePage(new URLSearchParams(search).get("page")),
    };
  }
  if (routePath === "/platform/accounts") {
    return {
      navigationId: "platform-accounts",
      task: "accounts",
      page: positivePage(new URLSearchParams(search).get("page")),
    };
  }
  return undefined;
}

/** 将可恢复状态编码为规范 URL，避免页内状态与浏览器地址不一致。 */
export function formatConsoleRoute(route: ConsoleRoute): string {
  if (route.navigationId === "access") {
    return `${route.task === "members" ? "/access/members" : "/access/roles"}?page=${route.page}`;
  }
  if (route.navigationId === "data") {
    return route.task === "governed-file"
      ? formatGovernedFileRoute(route)
      : route.task === "structured-dataset"
        ? formatStructuredDatasetRoute(route)
        : route.task === "object-storage"
          ? formatObjectStorageRoute(route)
          : "/data/publication";
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
  return route.navigationId === "platform"
    ? `/platform/tenants?page=${route.page}`
    : `/platform/accounts?page=${route.page}`;
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
