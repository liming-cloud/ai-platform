const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { readConfiguration, assertWorkspace, logoutWorkspace } = require("../development/acceptance-login.cjs");

const directory = fs.mkdtempSync(path.join(os.tmpdir(), "wp0017-configuration-test-"));
after(() => fs.rmSync(directory, { recursive: true, force: true }));
const fixture = {
  platform: { loginName: "synthetic-platform", password: "synthetic-only-password" },
  tenant: { tenantCode: "synthetic-tenant", loginName: "synthetic-tenant-admin", password: "synthetic-only-password" },
};
function credentialFile(name, data = fixture, mode = 0o600) {
  const target = path.join(directory, name);
  fs.writeFileSync(target, JSON.stringify(data), { mode });
  return target;
}
function environment(file, extra = {}) {
  return { FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE: file, ...extra };
}

test("目录只读配置拒绝任何对象准备，且不尝试读取凭据", () => {
  const { tenantsConfiguration } = require("../development/acceptance-tenants.cjs");
  assert.throws(() => tenantsConfiguration({ SETUP: "tenant" }), /目录切片只读/);
});
test("目录配置复用既有凭据边界并绑定精确验收对象", () => {
  const { tenantsConfiguration } = require("../development/acceptance-tenants.cjs");
  const file = credentialFile("directory.json");
  const configuration = tenantsConfiguration(environment(file, { FOUNDATION_ACCEPTANCE_TENANT_ID: "2104457357567197185" }));
  assert.equal(configuration.tenantId, "2104457357567197185");
  assert.equal(configuration.setup, false);
  assert.throws(() => tenantsConfiguration(environment(file)), /租户标识/);
  assert.throws(() => tenantsConfiguration(environment(file, { FOUNDATION_ACCEPTANCE_TENANT_ID: "../other" })), /租户标识/);
  assert.throws(() => tenantsConfiguration(environment(credentialFile("directory-public.json", fixture, 0o640), {
    FOUNDATION_ACCEPTANCE_TENANT_ID: "2104457357567197185",
  })), /0600/);
});
test("目录配置绑定已只读核实的多管理员精确数量，旧单管理员样本仍默认一名", () => {
  const { tenantsConfiguration } = require("../development/acceptance-tenants.cjs");
  const file = credentialFile("directory-multiple.json");
  const scope = { FOUNDATION_ACCEPTANCE_TENANT_ID: "2104457357567197185" };
  assert.equal(tenantsConfiguration(environment(file, scope)).administratorCount, 1);
  assert.equal(tenantsConfiguration(environment(file, {
    ...scope, FOUNDATION_ACCEPTANCE_ADMINISTRATOR_COUNT: "2",
  })).administratorCount, 2);
});
test("目录期望管理员数量拒绝非正整数及不安全数值，不退化成非空判断", () => {
  const { tenantsConfiguration } = require("../development/acceptance-tenants.cjs");
  const file = credentialFile("directory-count-invalid.json");
  for (const value of ["0", "-1", "1.5", "abc", "Infinity", "9007199254740992", " 2 "]) {
    assert.throws(() => tenantsConfiguration(environment(file, {
      FOUNDATION_ACCEPTANCE_TENANT_ID: "2104457357567197185",
      FOUNDATION_ACCEPTANCE_ADMINISTRATOR_COUNT: value,
    })), /管理员资格数量/);
  }
});

test("只接受当前账户独占的普通 0600 JSON 凭据文件", () => {
  const configuration = readConfiguration(environment(credentialFile("valid.json")));
  assert.equal(configuration.baseUrl, "http://127.0.0.1:13000");
  assert.equal(configuration.credentials.tenant.tenantCode, "synthetic-tenant");
});
test("组可读文件在任何网络请求前拒绝", () => {
  assert.throws(() => readConfiguration(environment(credentialFile("public.json", fixture, 0o640))), /0600/);
});
test("符号链接不能绕过凭据文件边界", () => {
  const source = credentialFile("source.json");
  const link = path.join(directory, "link.json");
  fs.symlinkSync(source, link);
  assert.throws(() => readConfiguration(environment(link)), /凭据文件/);
});
test("缺少租户凭据不能当成平台单角色通过", () => {
  assert.throws(() => readConfiguration(environment(credentialFile("platform-only.json", { platform: fixture.platform }))), /租户管理员凭据/);
});
test("只有显式 setup 可以暂时不提供租户且必须给出新输出文件", () => {
  const file = credentialFile("setup.json", { platform: fixture.platform });
  assert.throws(() => readConfiguration(environment(file, { SETUP: "tenant" })), /新凭据输出文件/);
  const configuration = readConfiguration(environment(file, {
    SETUP: "tenant", FOUNDATION_ACCEPTANCE_OUTPUT_FILE: path.join(directory, "created.json"),
  }));
  assert.equal(configuration.setup, true);
});
test("setup 不得覆盖现有文件", () => {
  const file = credentialFile("existing.json");
  assert.throws(() => readConfiguration(environment(file, {
    SETUP: "tenant", FOUNDATION_ACCEPTANCE_OUTPUT_FILE: file,
  })), /新凭据输出文件/);
});
test("禁止非回环、非 13000 和含凭据或查询参数的目标", () => {
  const file = credentialFile("url.json");
  for (const url of ["https://example.com", "http://127.0.0.1:13001", "http://name:private@127.0.0.1:13000", "http://127.0.0.1:13000/?token=private"]) {
    assert.throws(() => readConfiguration(environment(file, { FOUNDATION_ACCEPTANCE_BASE_URL: url })), /本机 13000/);
  }
});

// 仅验证 Playwright 定位契约；默认路由和菜单行为仍须由真实部署浏览器复验。
function workspacePage({ pathname, headings, passwordCount = 0, mobile = false }) {
  let menuOpen = false;
  let loggedOut = false;
  const nodes = headings.map(name => ({ role: "text", name }));
  nodes.push({ role: "button", name: "运营总览" });
  nodes.push({ role: "button", name: mobile ? "租户管理员 / 账户菜单" : "平台管理员 / 账户菜单", click: () => { menuOpen = true; } });
  nodes.push({ role: "menuitem", name: "退出登录", visible: () => menuOpen, click: () => { loggedOut = true; } });
  return {
    get loggedOut() { return loggedOut; },
    async waitForURL(predicate) {
      assert.ok(predicate(new URL(pathname, "http://127.0.0.1:13000")), "未到达预期默认工作区 URL");
    },
    getByText(name, { exact }) {
      return this.getByRole(undefined, { name, exact });
    },
    getByRole(role, { name, exact }) {
      const matches = () => nodes.filter(node => (!role || node.role === role) && (!node.visible || node.visible())
        && (name instanceof RegExp ? name.test(node.name) : exact ? node.name === name : node.name.includes(name)));
      const resolve = () => {
        assert.equal(matches().length, 1, `没有唯一可见的 ${role}：${name}`);
        return matches()[0];
      };
      return { async waitFor() { resolve(); }, async click() { resolve().click(); } };
    },
    locator(selector) {
      if (selector === "#page-title") return {
        async waitFor() { assert.ok(headings.length); },
        async innerText() { return headings[0]; },
      };
      assert.equal(selector, 'input[type="password"]');
      return { async count() { return passwordCount; } };
    },
  };
}

const workspaces = {
  platform: { pathname: "/platform/operations?range=DAY_7&page=1", headings: ["平台运营总览", "平台资源健康"] },
  tenant: { pathname: "/operations?range=DAY_7", headings: ["运营总览", "当前租户资源"] },
};

test("两个角色的默认工作区通过真实落点与内容定位，不依赖未展开的退出菜单", async () => {
  for (const [role, workspace] of Object.entries(workspaces)) {
    await assertWorkspace(workspacePage(workspace), role);
  }
});

test("错误路由、错误范围或错误默认参数不能当作登录成功", async () => {
  for (const pathname of ["/unknown", "/operations?range=DAY_7", "/platform/operations?range=DAY_1&page=1", "/platform/operations?range=DAY_7&page=2"]) {
    await assert.rejects(assertWorkspace(workspacePage({ ...workspaces.platform, pathname }), "platform"), /预期默认工作区 URL/);
  }
});

test("仅有外壳标题或仅有资源内容不能当作工作区加载成功", async () => {
  for (const headings of [["平台运营总览"], ["平台资源健康"]]) {
    await assert.rejects(assertWorkspace(workspacePage({ ...workspaces.platform, headings }), "platform"), /没有唯一可见的|未进入预期工作区/);
  }
});

test("工作区仍残留密码输入框时验收失败", async () => {
  await assert.rejects(assertWorkspace(workspacePage({ ...workspaces.platform, passwordCount: 1 }), "platform"), /凭据表单/);
});

test("桌面和移动入口都先展开账户菜单再通过菜单项退出", async () => {
  for (const mobile of [false, true]) {
    const page = workspacePage({ ...workspaces.platform, mobile });
    await logoutWorkspace(page);
    assert.equal(page.loggedOut, true);
  }
});
