import { describe, expect, it } from "vitest";
import {
  failedModelConfigurationPage,
  initialModelConfigurationPage,
} from "./embedding-model-configuration-page";

describe("嵌入模型配置页状态", () => {
  it("读取失败只清空当前页并保留本地可重试状态", () => {
    expect(failedModelConfigurationPage()).toEqual({
      ...initialModelConfigurationPage,
      loading: false,
      failed: true,
    });
  });
});
