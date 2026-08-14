import { describe, expect, it } from "vitest";
import {
  buildBrowserLoginCredentials,
  isLoginReady,
} from "./login-workspace-choice";

describe("登录工作区选择", () => {
  it("平台管理登录不提交租户编码", () => {
    expect(buildBrowserLoginCredentials({
      workspace: "platform",
      tenantCode: "demo-store",
      loginName: "platform.root",
      password: "not-a-real-password",
    })).toEqual({
      tenantCode: undefined,
      loginName: "platform.root",
      password: "not-a-real-password",
    });
  });

  it("租户工作区必须填写租户编码才能提交", () => {
    expect(isLoginReady({
      workspace: "tenant",
      tenantCode: "   ",
      loginName: "admin",
      password: "not-a-real-password",
    })).toBe(false);

    expect(isLoginReady({
      workspace: "tenant",
      tenantCode: " demo-store ",
      loginName: " admin ",
      password: "not-a-real-password",
    })).toBe(true);
  });
});
