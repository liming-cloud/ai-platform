import {
  Button,
  Menu,
  MenuItem,
  MenuList,
  MenuPopover,
  MenuTrigger,
  Text,
  Title2,
} from "@fluentui/react-components";
import { ChevronDown16Regular, SignOut20Regular } from "@fluentui/react-icons";
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
  onLogout?: () => void;
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
  onLogout,
}: ProductShellProps) {
  assertNavigationBudget(navigation);
  const directNavigation = navigation.filter((item) => item.children === undefined);
  const groupedNavigation = navigation.filter((item) => item.children !== undefined);

  return (
    <div className="product-shell">
      <aside className="product-sidebar">
        <div className="product-brand">
          <span aria-hidden="true">知</span>
          <Text weight="semibold">{productName}</Text>
        </div>
        <nav className="product-navigation" aria-label="主要导航">
          {directNavigation.map((item) => (
            <Button
              key={item.id}
              appearance="subtle"
              className={item.id === activeNavigationId ? "is-active" : undefined}
              aria-current={item.id === activeNavigationId ? "page" : undefined}
              onClick={() => onNavigationChange?.(item.id)}
            >
              {item.label}
            </Button>
          ))}
          {groupedNavigation.map((item) => (
            <section key={item.id} className="product-navigation__group" aria-label={item.label}>
              <Text size={200}>{item.label}</Text>
              {item.children?.map((child) => (
                <Button
                  key={child.id}
                  appearance="subtle"
                  className={child.id === activeNavigationId ? "is-active" : undefined}
                  aria-current={child.id === activeNavigationId ? "page" : undefined}
                  onClick={() => onNavigationChange?.(child.id)}
                >
                  {child.label}
                </Button>
              ))}
            </section>
          ))}
        </nav>
        <Menu>
          <MenuTrigger disableButtonEnhancement>
            <Button className="product-account" appearance="subtle" iconPosition="after" icon={<ChevronDown16Regular />}>
              <span aria-hidden="true">{accountLabel.slice(0, 1)}</span>
              <Text size={200}>{accountLabel}</Text>
            </Button>
          </MenuTrigger>
          <MenuPopover>
            <MenuList>
              <MenuItem icon={<SignOut20Regular />} onClick={onLogout}>退出登录</MenuItem>
            </MenuList>
          </MenuPopover>
        </Menu>
      </aside>

      <div className="product-workspace">
        <header className="product-mobile-header"><Text weight="semibold">{productName}</Text></header>
        <main className="product-main">
          <section className="page-heading" aria-labelledby="page-title">
            <Title2 id="page-title">{pageTitle}</Title2>
            <Text className="page-summary">{pageSummary}</Text>
          </section>
          {children}
        </main>
      </div>
    </div>
  );
}
