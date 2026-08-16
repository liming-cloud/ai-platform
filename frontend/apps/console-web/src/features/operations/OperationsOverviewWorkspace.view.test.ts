import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import {
  OperationsSkeleton,
  RefreshButton,
  StatusBlock,
  TenantSummaryTable,
} from "./OperationsOverviewWorkspace";

describe("运营总览基础状态", () => {
  it("加载状态提供可理解的忙碌说明", () => {
    const html = renderToStaticMarkup(createElement(OperationsSkeleton));

    expect(html).toContain("正在加载运营总览");
  });

  it("零数据资源和空租户页不伪造统计", () => {
    const resource = renderToStaticMarkup(createElement(StatusBlock, {
      title: "知识空间",
      value: { total: 0, statuses: {} },
    }));
    const tenants = renderToStaticMarkup(createElement(TenantSummaryTable, {
      items: [],
      total: 0,
      page: 1,
      hasPrevious: false,
      hasNext: false,
      onPageChange: () => undefined,
    }));

    expect(resource).toContain("暂无数据");
    expect(tenants).toContain("当前没有租户数据");
  });

  it("手动刷新时禁用重复操作并说明当前状态", () => {
    const html = renderToStaticMarkup(createElement(RefreshButton, {
      refreshing: true,
      onRefresh: () => undefined,
    }));

    expect(html).toContain("正在刷新");
    expect(html).toContain("disabled");
  });
});
