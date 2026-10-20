import { describe, expect, it } from "vitest";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { PlatformModelDeploymentForm } from "./PlatformModelDeploymentForm";
import type { ControlPlaneClient } from "../../api/control-plane-client";

describe("S2 表单初态", () => {
  it("加载前不预选模型或租户，不提供猜测的能力参数", () => {
    const html = renderToStaticMarkup(createElement(PlatformModelDeploymentForm, { client: {} as ControlPlaneClient, onClose() {}, onCreated() {}, onSavingChange() {} }));
    expect(html).toContain("建立可授权模型部署");
    expect(html).toContain("请选择已发布连接的模型");
    expect(html).not.toContain('value="8192"');
    expect(html).not.toContain('value="1024"');
    expect(html).not.toContain('checked=""');
    expect(html).toContain("草稿仅保存拟授权范围");
  });
});
