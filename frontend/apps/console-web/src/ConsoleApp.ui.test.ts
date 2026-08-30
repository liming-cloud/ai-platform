/// <reference types="node" />
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const appPath = new URL("./ConsoleApp.tsx", import.meta.url).pathname;

describe("控制台共享任务导航", () => {
  it("数据与治理任务复用 Fluent 标签导航", () => {
    const source = readFileSync(appPath, "utf8");

    expect(source).toContain("TabList");
    expect(source).toContain("function TaskNavigation");
    expect(source.match(/<TaskNavigation/g)?.length).toBeGreaterThanOrEqual(2);
    expect(source).not.toContain('className="data-task-switcher"');
  });

  it("知识库子任务切换复用可恢复路由上下文", () => {
    const source = readFileSync(appPath, "utf8");

    expect(source).toContain("knowledgeDataTaskRoute(route, task)");
    expect(source).not.toContain('task, knowledgeSpacePage: 1, embeddingModelConfigurationPage: 1');
  });

  it("账户区展示当前浏览器会话的中文角色", () => {
    const source = readFileSync(appPath, "utf8");

    expect(source).toContain("displayRoles");
    expect(source).toContain('PLATFORM_ADMINISTRATOR: "平台管理员"');
    expect(source).toContain('TENANT_ADMINISTRATOR: "租户管理员"');
    expect(source).toContain('TENANT_USER: "租户用户"');
    expect(source).not.toContain('accountLabel="已验证浏览器会话"');
  });

  it("无工作区和拒绝访问页面的账户菜单仍可退出登录", () => {
    const source = readFileSync(appPath, "utf8");

    expect(source.match(/onLogout=\{\(\) => void onLogout\(\)\}/g)?.length).toBeGreaterThanOrEqual(3);
  });
});
