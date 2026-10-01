import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { LoginEntryLinks, type LoginEntry } from "./LoginEntryLinks";

describe("原型三类登录入口导航", () => {
  it.each([
    ["platform", [["/tenant/login", "租户管理员登录"], ["/ask", "普通用户问答登录"]]],
    ["tenant", [["/login", "平台管理登录"], ["/ask", "普通用户问答登录"]]],
    ["query", [["/login", "平台管理登录"], ["/tenant/login", "租户管理员登录"]]],
  ] as [LoginEntry, string[][]][])("%s 渲染另外两个原生链接并保留原型顺序", (entry, expected) => {
    const html = renderToStaticMarkup(createElement(LoginEntryLinks, { entry, onNavigate: () => undefined }));
    const links = [...html.matchAll(/<a href="([^"]+)">([^<]+)<\/a>/g)]
      .map(match => [match[1], match[2]]);
    expect(links).toEqual(expected);
    expect(html).toContain('aria-label="其他登录入口"');
    expect(html).not.toContain("role=\"tab\"");
  });
});
