import {
  Tab,
  TabList,
  Text,
  Title2,
} from "@fluentui/react-components";
import type { ReactNode } from "react";
import "../styles/shell.css";
import { assertNavigationBudget } from "./navigation-budget";

export interface NavigationItem {
  id: string;
  label: string;
}

export interface ProductShellProps {
  productName: string;
  pageTitle: string;
  pageSummary: string;
  navigation: readonly NavigationItem[];
  activeNavigationId: string;
  children: ReactNode;
  accountLabel?: string;
}

/**
 * 控制台和问答应用共用的产品外壳，页面内容仍由各应用独立拥有。
 */
export function ProductShell({
  productName,
  pageTitle,
  pageSummary,
  navigation,
  activeNavigationId,
  children,
  accountLabel = "演示租户",
}: ProductShellProps) {
  assertNavigationBudget(navigation);

  return (
    <div className="product-shell">
      <header className="product-header">
        <div className="product-brand">
          <Text weight="semibold">{productName}</Text>
        </div>
        <TabList
          aria-label="主要导航"
          className="desktop-navigation"
          selectedValue={activeNavigationId}
        >
          {navigation.map((item) => (
            <Tab key={item.id} value={item.id}>
              {item.label}
            </Tab>
          ))}
        </TabList>
        <Text className="account-label" size={200}>
          {accountLabel}
        </Text>
      </header>

      <main className="product-main">
        <section className="page-heading" aria-labelledby="page-title">
          <Title2 id="page-title">{pageTitle}</Title2>
          <Text className="page-summary">{pageSummary}</Text>
        </section>
        {children}
      </main>
    </div>
  );
}
