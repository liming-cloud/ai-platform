/// <reference types="node" />
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const workspacePath = new URL("./PlatformModelSupplyWorkspace.tsx", import.meta.url).pathname;
const stylesPath = new URL("../../styles.css", import.meta.url).pathname;

describe("平台模型供应链呈现", () => {
  it("失败发现使用警告语义且不伪装成成功", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain("intent={discoverySummary.intent}");
    expect(source).toContain("模型发现未完成");
    expect(source).not.toContain('<MessageBar intent="success"><MessageBarBody><MessageBarTitle>模型发现已记录');
  });

  it("统一资源表格占满可用宽度并在窄视口横向滚动", () => {
    const styles = readFileSync(stylesPath, "utf8");

    expect(styles).toMatch(/\.member-directory-table\s*\{[^}]*overflow-x:\s*auto;[^}]*width:\s*100%;/s);
    expect(styles).toMatch(/\.member-directory-table table\s*\{[^}]*width:\s*100%;[^}]*min-width:/s);
  });

  it("厂商 Endpoint 字段解释需要填写 API 基础地址", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain("API 基础地址");
    expect(source).toContain("https://ark.cn-beijing.volces.com/api/v3");
    expect(source).not.toContain('placeholder="https://…/v1"');
  });

  it("创建连接显式选择权威发现协议并说明未知能力需管理员确认", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain('name="discoveryProfile"');
    expect(source).toContain('value="OPENAI_MODELS_API"');
    expect(source).toContain('value="VOLCENGINE_ARK_CONTROL_PLANE"');
    expect(source).toContain("元数据不足的候选需要管理员确认能力");
  });

  it("方舟控制面凭据与推理APIKey分开录入且只在对应协议展示", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain('discoveryProfile === "VOLCENGINE_ARK_CONTROL_PLANE"');
    expect(source).toContain('label="Access Key ID"');
    expect(source).toContain('label="Secret Access Key"');
    expect(source).toContain("仅用于模型发现");
    expect(source).toContain("仅用于推理与能力探测");
    expect(source).toContain("discoveryAccessKeyId");
    expect(source).toContain("discoverySecretAccessKey");
  });
});
