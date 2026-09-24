import { describe, expect, it } from "vitest";
import { tenantAdministratorPageNumbers } from "./tenant-administrator-pagination";

describe("租户管理员目录页码", () => {
  it("七页内全部显示，更多页保留首尾、当前邻页与省略号", () => {
    expect(tenantAdministratorPageNumbers(1, 1)).toEqual([1]);
    expect(tenantAdministratorPageNumbers(4, 7)).toEqual([1, 2, 3, 4, 5, 6, 7]);
    expect(tenantAdministratorPageNumbers(5, 12)).toEqual([1, "…", 4, 5, 6, "…", 12]);
    expect(tenantAdministratorPageNumbers(1, 12)).toEqual([1, 2, "…", 12]);
    expect(tenantAdministratorPageNumbers(12, 12)).toEqual([1, "…", 11, 12]);
  });
});
