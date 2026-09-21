import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { renderInventoryPage } from "../full-platform.js";

const read = (name) => JSON.parse(readFileSync(new URL(`../${name}`, import.meta.url), "utf8"));
const inventory = read("page-inventory.json");
const fixtures = read("page-fixtures.json").pages;
const tenant = read("fixtures.json").tenant;
const pages = inventory.groups.flatMap((group) => group.pages.map((page) => ({ group, page })));
const adminPages = pages.filter(({ group, page }) => group.shell === "console" && !["console-login", "tenant-login"].includes(page.screen) && fixtures[page.screen].mode);
const prototypeSource = readFileSync(new URL("../prototype.js", import.meta.url), "utf8");

// 只验证可丢弃原型的范围投影，不把这些断言当作服务端授权证据。
test("管理原型必须使用清单分组确定平台和租户范围", () => {
  const violations = adminPages.flatMap(({ group, page }) => {
    const expected = ["platform-governance", "platform-operations"].includes(group.id) ? "platform" : "tenant";
    const html = renderInventoryPage(page, group, fixtures[page.screen], "success", tenant);
    return html.includes(`data-scope="${expected}"`) ? [] : [`${page.screen}: ${expected}`];
  });
  assert.ok(adminPages.length > 40, "必须实际覆盖管理页面");
  assert.deepEqual(violations, []);
});

test("拒绝态不渲染对象状态行或携带对象事实的确认弹层", () => {
  const violations = adminPages.flatMap(({ group, page }) => {
    const html = renderInventoryPage(page, group, fixtures[page.screen], "denied", tenant);
    return /class="page-object-line"|<dialog\b/.test(html) ? [page.screen] : [];
  });
  assert.deepEqual(violations, []);
});

test("产品正文不解释页面实现方式和错误脱敏机制", () => {
  const violations = adminPages.flatMap(({ group, page }) => ["success", "error", "denied"].flatMap((state) => {
    const html = renderInventoryPage(page, group, fixtures[page.screen], state, tenant);
    return /当前页面只保留完成本次操作|安全重试不会暴露|页面不会确认/.test(html) ? [`${page.screen}/${state}`] : [];
  }));
  assert.deepEqual(violations, []);
});

test("普通用户文案不暴露工程术语，专属页头不固定为成功状态", () => {
  for (const phrase of ["凭据由同源 HttpOnly cookie 承载", "当前账户没有 QUERY_USE", "不会全部注入模型上下文", "不超过 3,000 tokens 做问题改写", "escapeHtml(pageStatus(active))"]) {
    assert.ok(!prototypeSource.includes(phrase), `残留文案或固定状态：${phrase}`);
  }
});
