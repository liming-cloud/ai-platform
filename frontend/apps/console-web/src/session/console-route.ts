import type { NAVIGATION } from "./navigation-capability";

/** 统一控制台由 URL 恢复的一级工作区标识。 */
export type ConsoleNavigationId = typeof NAVIGATION[number]["id"];

/** 统一控制台的可恢复工作区状态；列表页码只描述服务端分页位置。 */
export type ConsoleRoute =
  | {
    navigationId: "data";
    task: "governed-file" | "structured-dataset" | "object-storage" | "first-publication";
  }
  | { navigationId: "models"; task: "configuration" }
  | {
    navigationId: "models";
    task: "providers";
    providerPage: number;
    connectionId?: string;
    discoveryPage: number;
  }
  | { navigationId: "governance"; task: "query" | "evaluation" }
  | { navigationId: "query"; task: "ask" }
  | { navigationId: "platform"; task: "tenants" };

/** 路由解析后的授权结果，拒绝时保留目标以便页面明确说明失败原因。 */
export type AuthorizedConsoleRoute =
  | { kind: "route"; route: ConsoleRoute }
  | { kind: "denied"; route: ConsoleRoute }
  | { kind: "not-found" };

/** 返回一级导航的稳定入口，供根路径首次进入和侧栏切换共同使用。 */
export function defaultConsoleRoute(navigationId: ConsoleNavigationId): ConsoleRoute {
  switch (navigationId) {
    case "data":
      return { navigationId, task: "governed-file" };
    case "models":
      return { navigationId, task: "configuration" };
    case "governance":
      return { navigationId, task: "query" };
    case "query":
      return { navigationId, task: "ask" };
    case "platform":
      return { navigationId, task: "tenants" };
  }
}

/**
 * 只识别控制台拥有的固定同源路由，未知路径不能隐式映射到默认工作区。
 */
export function parseConsoleRoute(pathname: string, search: string): ConsoleRoute | undefined {
  const routePath = normalizedPath(pathname);
  if (routePath === "/data/files") {
    return { navigationId: "data", task: "governed-file" };
  }
  if (routePath === "/data/datasets") {
    return { navigationId: "data", task: "structured-dataset" };
  }
  if (routePath === "/data/object-storage") {
    return { navigationId: "data", task: "object-storage" };
  }
  if (routePath === "/data/publication") {
    return { navigationId: "data", task: "first-publication" };
  }
  if (routePath === "/models/configurations") {
    return { navigationId: "models", task: "configuration" };
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
  if (routePath === "/governance/query") {
    return { navigationId: "governance", task: "query" };
  }
  if (routePath === "/governance/evaluation") {
    return { navigationId: "governance", task: "evaluation" };
  }
  if (routePath === "/ask") {
    return { navigationId: "query", task: "ask" };
  }
  if (routePath === "/platform/tenants") {
    return { navigationId: "platform", task: "tenants" };
  }
  return undefined;
}

/** 将可恢复状态编码为规范 URL，避免页内状态与浏览器地址不一致。 */
export function formatConsoleRoute(route: ConsoleRoute): string {
  if (route.navigationId === "data") {
    return route.task === "governed-file"
      ? "/data/files"
      : route.task === "structured-dataset"
        ? "/data/datasets"
        : route.task === "object-storage"
          ? "/data/object-storage"
          : "/data/publication";
  }
  if (route.navigationId === "models") {
    return route.task === "configuration"
      ? "/models/configurations"
      : formatProviderRoute(route);
  }
  if (route.navigationId === "governance") {
    return route.task === "query" ? "/governance/query" : "/governance/evaluation";
  }
  if (route.navigationId === "query") {
    return "/ask";
  }
  return "/platform/tenants";
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
