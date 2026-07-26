export type OperationsViewState =
  | "unauthenticated"
  | "loading"
  | "ready"
  | "partial"
  | "denied"
  | "error";

export function resolveOperationsViewState(
  usageLoaded: boolean,
  auditLoaded: boolean,
  failureCodes: readonly string[],
): OperationsViewState {
  if (usageLoaded && auditLoaded) {
    return "ready";
  }
  if (usageLoaded || auditLoaded) {
    return "partial";
  }
  if (failureCodes.includes("AUTHENTICATION_REQUIRED")) {
    return "unauthenticated";
  }
  if (
    failureCodes.length > 0
    && failureCodes.every((code) => code === "ACCESS_DENIED")
  ) {
    return "denied";
  }
  return "error";
}

export function isOperationsStale(
  generatedAt: string,
  loadedAt: number,
): boolean {
  return loadedAt - Date.parse(generatedAt) > 5 * 60 * 1000;
}
