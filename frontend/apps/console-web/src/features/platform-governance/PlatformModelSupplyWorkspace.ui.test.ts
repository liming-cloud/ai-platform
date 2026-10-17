/// <reference types="node" />
import { readFileSync } from "node:fs";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { ControlPlaneClient, PlatformProviderConnectionSummary, PlatformProviderPresets } from "../../api/control-plane-client";
import { ConnectionStatus } from "./PlatformModelSupplyWorkspace";
import { PlatformProviderConnectionForm } from "./PlatformProviderConnectionForm";
import { hasCurrentConnectionProbe } from "./PlatformProviderConnectionDetail";

const sharedStyles = readFileSync(new URL("../../styles.css", import.meta.url), "utf8");
const styles = readFileSync(new URL("./platform-model-connections.css", import.meta.url), "utf8");
const base: PlatformProviderConnectionSummary = {
  id: "610000000000000001", displayName: "合成连接", providerKind: "OPENAI_COMPATIBLE",
  discoveryProfile: "OPENAI_MODELS_API", region: "unspecified", authenticationType: "API_KEY",
  credentialConfigured: true, discoveryCredentialsConfigured: false, published: false,
  status: "DRAFT", version: 0, createdAt: "2026-10-12T00:00:00Z",
  lastProbeSucceeded: null, lastProbeCode: null, lastProbedAt: null,
};

describe("平台供应商连接呈现", () => {
  it.each([
    ["2026-10-12T00:00:01Z", true],
    ["2026-10-12T00:00:00Z", false],
    ["2026-10-11T23:59:59Z", false],
    ["2026-10-13T00:00:01Z", false],
    [null, false],
    ["invalid-date", false],
  ] as const)("发布提示拒绝过期、缺失及未来探测：%s", (lastProbedAt, valid) => {
    expect(hasCurrentConnectionProbe({ ...base, status: "VERIFIED", lastProbeSucceeded: true, lastProbedAt },
      Date.parse("2026-10-13T00:00:00Z"))).toBe(valid);
  });
  it("有效时间不能替代当前连接的成功状态", () => {
    const current = { ...base, status: "VERIFIED" as const, lastProbeSucceeded: true, lastProbedAt: "2026-10-12T01:00:00Z" };
    const now = Date.parse("2026-10-13T00:00:00Z");
    expect(hasCurrentConnectionProbe({ ...current, status: "DISABLED" }, now)).toBe(false);
    expect(hasCurrentConnectionProbe({ ...current, status: "DRAFT" }, now)).toBe(false);
    expect(hasCurrentConnectionProbe({ ...current, lastProbeSucceeded: false }, now)).toBe(false);
  });
  it.each([
    [{ status: "DRAFT" }, "草稿"],
    [{ status: "VERIFIED", lastProbeSucceeded: true }, "验证通过"],
    [{ status: "VERIFIED", published: true, lastProbeSucceeded: true }, "已发布"],
    [{ status: "DRAFT", lastProbeSucceeded: false }, "失败"],
    [{ status: "DISABLED", lastProbeSucceeded: true }, "已停用"],
  ] as const)("状态标签仅使用当前事实：%j", (change, label) => {
    const markup = renderToStaticMarkup(createElement(ConnectionStatus, { connection: { ...base, ...change } }));
    expect(markup).toContain(label);
    if (label === "失败" || label === "已停用") expect(markup).not.toContain("验证通过");
  });

  it("表单从服务端目录呈现厂商，不把客户端地址或能力清单当成权威", () => {
    const directory: PlatformProviderPresets = { writesAvailable: true, version: "synthetic-version", items: [{
      id: "CUSTOM", name: "目录中的合成厂商", region: "unspecified", regionLabel: "自行配置",
      capabilities: ["CHAT"], discoveryProfile: "OPENAI_MODELS_API", variants: [{
        capability: "CHAT", runtimeProtocol: "OPENAI_COMPATIBLE", endpointTemplate: "", workspaceRequired: false,
        discoveryProfile: "OPENAI_MODELS_API",
      }],
    }] };
    const markup = renderToStaticMarkup(createElement(PlatformProviderConnectionForm, {
      client: {} as ControlPlaneClient, directory, onCancel: () => undefined, onCreated: () => undefined,
    }));
    expect(markup).toContain("目录中的合成厂商");
    expect(markup).not.toContain("DeepSeek");
    expect(markup).toContain("运行能力");
    expect(markup).toContain("一次性访问密钥");
    expect(markup).toContain('type="password"');
    expect(markup).toContain("保存连接草稿");
  });

  it("弹窗在文字放大时保留滚动及完整操作区，短按钮不受固定最小宽度限制", () => {
    expect(sharedStyles).toMatch(/\.foundation-task-dialog\s*\{[^}]*max-height:\s*calc\(100dvh\s*-\s*2rem\)\s*!important;[^}]*overflow-y:\s*auto\s*!important;/s);
    expect(styles).toMatch(/\.platform-connection-dialog \.fui-Button\s*\{[^}]*min-width:\s*0;/s);
    expect(styles).toContain("grid-template-columns: minmax(0, 1fr)");
  });

  it("目录与详情复用已验收暖色令牌、16px 正文及 48px 表格基线", () => {
    expect(styles).toContain("font-size: 1rem");
    expect(styles).toContain("font-family: var(--foundation-font-display)");
    expect(styles).toContain("background: var(--foundation-canvas)");
    expect(styles).toContain("height: 3rem");
    expect(styles).not.toContain("#eef6fb");
    expect(styles).toMatch(/\.platform-connection-table\s*\{[^}]*width:\s*100%;[^}]*overflow-x:\s*auto;/s);
  });
});
