import { describe, expect, it } from "vitest";
import { BrowserSessionClient } from "./browser-session-client";

describe("BrowserSessionClient", () => {
  it("只通过同源 cookie 登录并为写请求加入 CSRF 防护值", async () => {
    const requests: Request[] = [];
    const responses = [
      new Response(null, { status: 204 }),
      new Response(null, { status: 204 }),
      new Response(JSON.stringify({
        workspaceCategories: ["TENANT"],
        displayRoles: ["TENANT_MEMBER"],
        maximumSessionAgeSeconds: 3600,
      }), { status: 200, headers: { "Content-Type": "application/json" } }),
      new Response(null, { status: 204 }),
    ];
    const client = new BrowserSessionClient(
      async (input, init) => {
        requests.push(new Request(input, init));
        return responses.shift() ?? new Response(null, { status: 500 });
      },
      "http://localhost/api/v1",
      () => "csrf-value",
    );

    const capability = await client.login({
      tenantCode: "demo-store",
      loginName: "admin",
      password: "demo-password",
    });
    await client.request("/query-runs", { method: "POST", body: "{}" });

    expect(capability.workspaceCategories).toEqual(["TENANT"]);
    expect(requests.map((request) => request.url)).toEqual([
      "http://localhost/api/v1/auth/browser-csrf",
      "http://localhost/api/v1/auth/browser-sessions",
      "http://localhost/api/v1/auth/browser-session",
      "http://localhost/api/v1/query-runs",
    ]);
    expect(requests[1]?.headers.get("Authorization")).toBeNull();
    expect(requests[1]?.credentials).toBe("same-origin");
    expect(requests[1]?.headers.get("X-CSRF-Token")).toBe("csrf-value");
    expect(requests[3]?.headers.get("Authorization")).toBeNull();
    expect(requests[3]?.headers.get("X-CSRF-Token")).toBe("csrf-value");
  });

  it("能力端点返回 401 时不把浏览器视为已登录", async () => {
    const client = new BrowserSessionClient(
      async () => new Response(null, { status: 401 }),
      "http://localhost/api/v1",
      () => "csrf-value",
    );

    await expect(client.restore()).resolves.toBeNull();
  });
});
