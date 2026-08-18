/// <reference types="node" />
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const stylesPath = new URL("../../styles.css", import.meta.url).pathname;
const dialogFiles = [
  "../platform-governance/PlatformModelSupplyWorkspace.tsx",
  "../platform-governance/PlatformModelDeploymentWorkspace.tsx",
  "../model-configuration/ModelProviderWorkspace.tsx",
  "../model-configuration/ModelDeploymentWorkspace.tsx",
].map((relativePath) => new URL(relativePath, import.meta.url).pathname);

describe("任务弹窗表单布局", () => {
  it("直接位于 DialogBody 内的表单占满网格而不是停在左半列", () => {
    const sources = dialogFiles.map((path) => readFileSync(path, "utf8")).join("\n");
    const styles = readFileSync(stylesPath, "utf8");

    expect(sources).not.toMatch(/<DialogTitle>[^<]+<\/DialogTitle><form(?![^>]*foundation-dialog-form)/);
    expect(styles).toMatch(
      /\.foundation-dialog-form\s*\{[^}]*grid-column:\s*1\s*\/\s*-1;[^}]*width:\s*100%;[^}]*min-width:\s*0;/s,
    );
  });
});
