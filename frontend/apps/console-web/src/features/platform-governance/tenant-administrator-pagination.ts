/** 与已验收目录原型一致的页码窗口；数据和总页数仍由服务端确定。 */
export function tenantAdministratorPageNumbers(page: number, totalPages: number): Array<number | "…"> {
  const pages = Math.max(1, totalPages);
  const selected = pages <= 7 ? Array.from({ length: pages }, (_, index) => index + 1)
    : [...new Set([1, ...Array.from({ length: 3 }, (_, index) =>
      Math.min(pages - 1, Math.max(2, page - 1 + index))), pages])];
  return selected.flatMap((number, index) => index && number - selected[index - 1]! > 1
    ? ["…" as const, number] : [number]);
}
