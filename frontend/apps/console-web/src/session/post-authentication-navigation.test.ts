import { describe, expect, it } from "vitest";
import { resolvePostAuthenticationDestination } from "./post-authentication-navigation";

describe("登录后工作区回跳", () => {
  it("只允许已获得租户工作区的会话返回固定查询路径", () => {
    expect(resolvePostAuthenticationDestination("?next=%2Fquery%2F", true)).toBe("/query/");
    expect(resolvePostAuthenticationDestination("?next=%2Fquery%2F", false)).toBeUndefined();
  });

  it("拒绝调用方提供的任意其他回跳地址", () => {
    expect(resolvePostAuthenticationDestination("?next=https%3A%2F%2Fevil.example", true)).toBeUndefined();
    expect(resolvePostAuthenticationDestination("?next=%2Fplatform%2F", true)).toBeUndefined();
  });
});
