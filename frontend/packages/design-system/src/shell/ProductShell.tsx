import {
  Button,
  Menu,
  MenuItem,
  MenuList,
  MenuPopover,
  MenuTrigger,
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
  children?: readonly NavigationItem[];
}

export interface ProductShellProps {
  productName: string;
  pageTitle: string;
  pageSummary: string;
  navigation: readonly NavigationItem[];
  activeNavigationId: string;
  onNavigationChange?: (navigationId: string) => void;
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
  onNavigationChange,
  children,
  accountLabel = "演示租户",
}: ProductShellProps) {
  assertNavigationBudget(navigation);
  const directNavigation = navigation.filter((item) => item.children === undefined);
  const groupedNavigation = navigation.filter((item) => item.children !== undefined);

  return (
    <div className="product-shell">
      <header className="product-header">
        <div className="product-brand">
          <Text weight="semibold">{productName}</Text>
        </div>
        <div className="desktop-navigation" aria-label="主要导航">
          <TabList
            selectedValue={activeNavigationId}
            onTabSelect={(_, data) => {
              onNavigationChange?.(String(data.value));
            }}
          >
            {directNavigation.map((item) => (
              <Tab key={item.id} value={item.id}>
                {item.label}
              </Tab>
            ))}
          </TabList>
          {groupedNavigation.map((item) => (
            <Menu key={item.id}>
              <MenuTrigger disableButtonEnhancement>
                <Button appearance={item.children?.some((child) => child.id === activeNavigationId)
                  ? "primary"
                  : "subtle"}>
                  {item.label}
                </Button>
              </MenuTrigger>
              <MenuPopover>
                <MenuList aria-label={item.label}>
                  {item.children?.map((child) => (
                    <MenuItem key={child.id} onClick={() => onNavigationChange?.(child.id)}>
                      {child.label}
                    </MenuItem>
                  ))}
                </MenuList>
              </MenuPopover>
            </Menu>
          ))}
        </div>
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
