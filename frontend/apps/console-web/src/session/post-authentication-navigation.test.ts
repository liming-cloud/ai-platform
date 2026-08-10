import { describe, expect, it } from "vitest";
import { resolvePostAuthenticationDestination } from "./post-authentication-navigation";

describe("登录后工作区回跳", () => {
  it("只允许已获得租户工作区的会话返回控制台内固定问答路径", () => {
    expect(resolvePostAuthenticationDestination("?next=%2Fask", true)).toBe("/ask");
    expect(resolvePostAuthenticationDestination("?next=%2Fask", false)).toBeUndefined();
  });

  it("拒绝调用方提供的任意其他回跳地址", () => {
    expect(resolvePostAuthenticationDestination("?next=https%3A%2F%2Fevil.example", true)).toBeUndefined();
    expect(resolvePostAuthenticationDestination("?next=%2Fquery%2F", true)).toBeUndefined();
  });
});
