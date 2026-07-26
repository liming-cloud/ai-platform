import { describe, expect, it } from "vitest";
import { assertNavigationBudget } from "./navigation-budget";

describe("顶级导航预算", () => {
  it("接受六项以内的导航", () => {
    expect(() =>
      assertNavigationBudget(
        Array.from({ length: 6 }, (_, index) => ({
          id: String(index),
          label: `导航${index}`,
        })),
      ),
    ).not.toThrow();
  });

  it("拒绝超过六项的导航", () => {
    expect(() =>
      assertNavigationBudget(
        Array.from({ length: 7 }, (_, index) => ({
          id: String(index),
          label: `导航${index}`,
        })),
      ),
    ).toThrow("顶级导航不能超过 6 项");
  });
});
