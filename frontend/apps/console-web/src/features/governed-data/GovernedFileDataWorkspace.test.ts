import { describe, expect, it } from "vitest";
import { ApiRequestError } from "../../api/control-plane-client";
import { modelRuntimeFailure } from "./GovernedFileDataWorkspace";

describe("受治理文件索引的模型运行时提示", () => {
  it("仅将创建索引时的模型配置入口不可用转为可恢复提示", () => {
    expect(modelRuntimeFailure(new ApiRequestError(404, "REQUEST_FAILED", "请求处理失败", "trc-001")))
      .toEqual({
        message: "当前环境尚未启用受控嵌入模型。请联系平台运维完成模型运行时配置后重试。",
        traceId: "trc-001",
      });
    expect(modelRuntimeFailure(new ApiRequestError(500, "REQUEST_FAILED", "请求处理失败", "trc-002")))
      .toBeUndefined();
  });
});
