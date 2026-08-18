import {
  Button,
  Menu,
  MenuItem,
  MenuList,
  MenuPopover,
  MenuTrigger,
  Text,
  Title2,
  Tooltip,
} from "@fluentui/react-components";
import {
  Bot20Regular,
  Building20Regular,
  Chat20Regular,
  ChevronDown16Regular,
  CloudArrowUp20Regular,
  DataTrending20Regular,
  Database20Regular,
  Grid20Regular,
  PeopleTeam20Regular,
  PersonAccounts20Regular,
  ShieldTask20Regular,
  SignOut20Regular,
  Sparkle20Filled,
} from "@fluentui/react-icons";
import type { ReactNode } from "react";
import "../styles/shell.css";
import { assertNavigationBudget } from "./navigation-budget";

export interface NavigationItem {
  id: string;
  label: string;
  icon?: NavigationIcon;
  children?: readonly NavigationItem[];
}

export type NavigationIcon =
  | "overview"
  | "people"
  | "data"
  | "models"
  | "governance"
  | "query"
  | "platform"
  | "accounts"
  | "supply"
  | "workspace";

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
  accountLabel = "当前账户",
  onLogout,
}: ProductShellProps) {
  assertNavigationBudget(navigation);
  const directNavigation = navigation.filter((item) => item.children === undefined);
  const groupedNavigation = navigation.filter((item) => item.children !== undefined);
  const contextLabel = groupedNavigation.find((group) => group.children?.some((item) => item.id === activeNavigationId))?.label
    ?? (directNavigation.some((item) => item.id === activeNavigationId) ? "租户工作区" : "可信企业控制台");

  return (
    <div className="product-shell">
      <aside className="product-sidebar">
        <div className="product-brand">
          <span aria-hidden="true"><Sparkle20Filled /></span>
          <div><Text weight="semibold">{productName}</Text><small>管理控制台</small></div>
        </div>
        <nav className="product-navigation" aria-label="主要导航">
          {directNavigation.map((item) => (
            <NavigationButton
              key={item.id}
              item={item}
              active={item.id === activeNavigationId}
              onClick={() => onNavigationChange?.(item.id)}
            />
          ))}
          {groupedNavigation.map((item) => (
            <section key={item.id} className="product-navigation__group" aria-label={item.label}>
              <Text size={200}>{item.label}</Text>
              {item.children?.map((child) => (
                <NavigationButton
                  key={child.id}
                  item={child}
                  active={child.id === activeNavigationId}
                  onClick={() => onNavigationChange?.(child.id)}
                />
              ))}
            </section>
          ))}
        </nav>
      </aside>

      <div className="product-workspace">
        <header className="product-topbar">
          <Text className="product-topbar__context">{contextLabel}</Text>
          <AccountMenu accountLabel={accountLabel} onLogout={onLogout} topbar />
        </header>
        <header className="product-mobile-header">
          <div><span aria-hidden="true"><Sparkle20Filled /></span><Text weight="semibold">{productName}</Text></div>
          <AccountMenu accountLabel={accountLabel} onLogout={onLogout} compact />
        </header>
        <main className="product-main">
          <div className="product-canvas">
            <section className="page-heading" aria-labelledby="page-title">
              <Text className="page-context">{contextLabel}</Text>
              <Title2 id="page-title">{pageTitle}</Title2>
              <Text className="page-summary">{pageSummary}</Text>
            </section>
            <div className="product-content">{children}</div>
          </div>
        </main>
      </div>
    </div>
  );
}

function NavigationButton({ item, active, onClick }: {
  item: NavigationItem;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <Tooltip content={item.label} relationship="description">
      <Button
        appearance="subtle"
        className={active ? "is-active" : undefined}
        icon={navigationIcon(item.icon)}
        aria-current={active ? "page" : undefined}
        onClick={onClick}
      >
        <span className="product-navigation__label">{item.label}</span>
      </Button>
    </Tooltip>
  );
}

function AccountMenu({ accountLabel, onLogout, compact = false, topbar = false }: {
  accountLabel: string;
  onLogout?: () => void;
  compact?: boolean;
  topbar?: boolean;
}) {
  const className = compact
    ? "product-account product-account--compact"
    : topbar
      ? "product-account product-account--topbar"
      : "product-account";
  return <Menu>
    <MenuTrigger disableButtonEnhancement>
      <Button className={className} appearance="subtle" iconPosition="after" icon={<ChevronDown16Regular />} aria-label={compact ? "打开账户菜单" : undefined}>
        <span aria-hidden="true">{accountLabel.slice(0, 1)}</span>
        {!compact ? <span><small>当前会话角色</small><Text size={200}>{accountLabel}</Text></span> : null}
      </Button>
    </MenuTrigger>
    <MenuPopover>
      <MenuList>
        <MenuItem icon={<SignOut20Regular />} onClick={onLogout}>退出登录</MenuItem>
      </MenuList>
    </MenuPopover>
  </Menu>;
}

function navigationIcon(icon?: NavigationIcon) {
  switch (icon) {
    case "overview": return <DataTrending20Regular />;
    case "people": return <PeopleTeam20Regular />;
    case "data": return <Database20Regular />;
    case "models": return <Bot20Regular />;
    case "governance": return <ShieldTask20Regular />;
    case "query": return <Chat20Regular />;
    case "platform": return <Building20Regular />;
    case "accounts": return <PersonAccounts20Regular />;
    case "supply": return <CloudArrowUp20Regular />;
    default: return <Grid20Regular />;
  }
}
