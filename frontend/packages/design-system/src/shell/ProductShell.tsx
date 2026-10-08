import {
  Button,
  Menu,
  MenuItem,
  MenuList,
  MenuPopover,
  MenuTrigger,
  Text,
  Tooltip,
} from "@fluentui/react-components";
import {
  BrainCircuit24Regular,
  BookDatabase24Regular,
  BuildingPeople24Regular,
  Chat20Regular,
  CloudArrowUp20Regular,
  DataTrending20Regular,
  DataUsage24Regular,
  Database20Regular,
  Grid20Regular,
  PeopleTeam20Regular,
  PeopleCommunity24Regular,
  ShieldTask24Regular,
  SignOut20Regular,
} from "@fluentui/react-icons";
import type { ReactNode } from "react";
import "../styles/shell.css";
import { PageHeading } from "./PageHeading";
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
  | "knowledge"
  | "evaluation"
  | "models"
  | "governance"
  | "query"
  | "platform"
  | "accounts"
  | "supply"
  | "workspace";

export interface ProductShellProps {
  productName: string;
  /** 对象页可在读取数据后自行使用 PageHeading；避免外壳持有业务读取状态。 */
  pageTitle?: string;
  pageSummary?: string;
  pageStatus?: { label: string; tone: "warning" | "success" | "info" };
  workspaceScope?: "platform" | "tenant";
  /** 仅来自当前租户只读接口，不使用原型示例名称或本地推断。 */
  tenantDisplayName?: string;
  /** 平板上的重复任务标题视觉收起，一级标题仍供辅助技术识别。 */
  compactHeading?: boolean;
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
  pageStatus,
  workspaceScope = "tenant",
  tenantDisplayName,
  compactHeading,
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

  const platform = workspaceScope === "platform";
  return (
    <div className="product-shell" data-scope={workspaceScope}>
      <aside className="product-sidebar">
        <div className="product-brand">
          <span aria-hidden="true">R</span>
          <div><Text weight="semibold">{productName}</Text><small>{platform ? "平台范围" : tenantDisplayName ? "租户工作区" : "租户范围"}</small></div>
        </div>
        <nav className={tenantDisplayName ? "product-navigation product-navigation--tenant-context" : "product-navigation"} aria-label={tenantDisplayName ? "租户范围导航" : "主要导航"}>
          {platform ? <p className="product-navigation__scope">平台范围 / 脱敏治理</p> : null}
          {!platform && tenantDisplayName ? <p className="product-navigation__scope">租户范围 / {tenantDisplayName}</p> : null}
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
        <div className="product-sidebar-account"><strong>{accountLabel}</strong><small>{platform ? "仅脱敏聚合" : tenantDisplayName ?? "当前租户"}</small></div>
      </aside>

      <div className="product-workspace">
        <header className="product-topbar">
          <Text className="product-topbar__context">{platform ? "平台管理 / 不含租户内容" : tenantDisplayName ? `${tenantDisplayName} / 租户管理` : contextLabel}</Text>
          <AccountMenu accountLabel={accountLabel} onLogout={onLogout} />
        </header>
        <main data-compact-heading={compactHeading} className="product-main">
          <div className="product-canvas">
            {pageTitle ? <PageHeading title={pageTitle} context={contextLabel} summary={pageSummary} status={pageStatus} /> : null}
            <div className={pageTitle ? "product-content" : "product-content product-content--owns-heading"}>{children}</div>
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
        aria-label={item.label}
        aria-current={active ? "page" : undefined}
        onClick={onClick}
      >
        <span className="product-navigation__label">{item.label}</span>
      </Button>
    </Tooltip>
  );
}

function AccountMenu({ accountLabel, onLogout }: { accountLabel: string; onLogout?: () => void }) {
  return <Menu><MenuTrigger disableButtonEnhancement>
    <Button className="product-account" appearance="subtle">{accountLabel} / 账户菜单</Button>
  </MenuTrigger><MenuPopover><MenuList>
    <MenuItem icon={<SignOut20Regular />} onClick={onLogout}>退出登录</MenuItem>
  </MenuList></MenuPopover></Menu>;
}

function navigationIcon(icon?: NavigationIcon) {
  switch (icon) {
    case "overview": return <DataTrending20Regular />;
    case "people": return <PeopleTeam20Regular />;
    case "data": return <Database20Regular />;
    case "knowledge": return <BookDatabase24Regular />;
    case "evaluation": return <DataUsage24Regular />;
    case "models": return <BrainCircuit24Regular />;
    case "governance": return <ShieldTask24Regular />;
    case "query": return <Chat20Regular />;
    case "platform": return <BuildingPeople24Regular />;
    case "accounts": return <PeopleCommunity24Regular />;
    case "supply": return <CloudArrowUp20Regular />;
    default: return <Grid20Regular />;
  }
}
