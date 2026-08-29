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

  it("创建连接按接入预设锁定权威发现协议并说明未知能力需管理员确认", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain('label="接入预设"');
    expect(source).toContain('value="VOLCENGINE_ARK"');
    expect(source).toContain('value="OPENAI_COMPATIBLE"');
    expect(source).toContain("resolveProviderPreset(providerPreset)");
    expect(source).not.toContain('name="discoveryProfile"');
    expect(source).toContain("元数据不足的候选需要管理员确认能力");
  });

  it("火山方舟预设自动填充数据面地址和签名区域", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain('endpoint: "https://ark.cn-beijing.volces.com/api/v3"');
    expect(source).toContain('region: "cn-beijing"');
    expect(source).toContain('discoveryProfile: "VOLCENGINE_ARK_CONTROL_PLANE"');
    expect(source).toContain("方舟控制面使用 Access Key 签名发现模型，API Key 只用于数据面探测和推理");
  });

  it("OpenAI兼容预设只要求基础地址和APIKey", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain('discoveryProfile: "OPENAI_MODELS_API"');
    expect(source).toContain("供应商必须实现 OpenAI Models API");
    expect(source).toContain('const arkDiscovery = providerPreset === "VOLCENGINE_ARK"');
  });

  it("方舟控制面凭据与推理APIKey分开录入且只在对应协议展示", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain('providerPreset === "VOLCENGINE_ARK"');
    expect(source).toContain('label="Access Key ID"');
    expect(source).toContain('label="Secret Access Key"');
    expect(source).toContain("仅用于模型发现");
    expect(source).toContain("仅用于推理与能力探测");
    expect(source).toContain("discoveryAccessKeyId");
    expect(source).toContain("discoverySecretAccessKey");
  });

  it("平台连接表单在 200% 缩放下保留纵向滚动和完整操作区", () => {
    const source = readFileSync(workspacePath, "utf8");
    const styles = readFileSync(stylesPath, "utf8");

    expect(source).toContain('<DialogSurface className="foundation-task-dialog"><DialogBody><DialogTitle>新建平台厂商连接</DialogTitle>');
    expect(styles).toMatch(
      /\.foundation-task-dialog\s*\{[^}]*max-height:\s*calc\(100dvh\s*-\s*2rem\)\s*!important;[^}]*overflow-y:\s*auto\s*!important;/s,
    );
  });

  it("关闭平台连接表单后把键盘焦点还给触发按钮", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain("const createConnectionTriggerRef = useRef<HTMLButtonElement>(null)");
    expect(source).toContain("ref={createConnectionTriggerRef}");
    expect(source).toContain("createConnectionTriggerRef.current?.focus()");
  });

  it("连接表格固定厂商与操作列布局", () => {
    const source = readFileSync(workspacePath, "utf8");
    const styles = readFileSync(stylesPath, "utf8");

    expect(source).toContain('className="platform-model-supply-table"');
    expect(source).toContain('className="platform-model-supply-table__provider"');
    expect(source).toContain('className="platform-model-supply-table__actions"');
    expect(styles).toMatch(/\.platform-model-supply-table__provider\s*\{[^}]*min-width:\s*16rem;[^}]*white-space:\s*nowrap;/s);
    expect(styles).toMatch(/\.platform-model-supply-table__actions\s*\{[^}]*text-align:\s*right;/s);
  });

  it("区分连接验证与候选刷新结果", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain("<th>连接验证</th>");
    expect(source).toContain("连接既有验证状态和未过期候选不会自动失效");
    expect(source).not.toContain("<th>最近探测</th>");
  });
});
