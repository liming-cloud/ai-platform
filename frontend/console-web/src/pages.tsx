import { lazy, Suspense, useEffect, type ReactNode } from 'react';
import { AsyncState } from './async-state';
import { ErrorPage } from './error-page';
import { defaultRoute, routeCapabilityFor } from './navigation';
import type { Navigate } from './page-shared';
import type { CurrentUser } from './types';

const PlatformOverviewPage = lazy(() => import('./overview-pages').then((module) => ({ default: module.PlatformOverviewPage })));
const TenantOverviewPage = lazy(() => import('./overview-pages').then((module) => ({ default: module.TenantOverviewPage })));
const AuditPage = lazy(() => import('./overview-pages').then((module) => ({ default: module.AuditPage })));

const DelegatedTenantPage = lazy(() => import('./governance-pages').then((module) => ({ default: module.DelegatedTenantPage })));
const ModelHubPage = lazy(() => import('./governance-pages').then((module) => ({ default: module.ModelHubPage })));
const ApprovalCenterPage = lazy(() => import('./governance-pages').then((module) => ({ default: module.ApprovalCenterPage })));
const RolePermissionPage = lazy(() => import('./governance-pages').then((module) => ({ default: module.RolePermissionPage })));
const AppStudioPage = lazy(() => import('./app-studio-page').then((module) => ({ default: module.AppStudioPage })));
const PermissionRequestPage = lazy(() => import('./governance-pages').then((module) => ({ default: module.PermissionRequestPage })));

const KnowledgeBasePage = lazy(() => import('./knowledge-pages').then((module) => ({ default: module.KnowledgeBasePage })));
const TenantUsersPage = lazy(() => import('./identity-pages').then((module) => ({ default: module.TenantUsersPage })));

const WorkspaceHome = lazy(() => import('./workspace-pages').then((module) => ({ default: module.WorkspaceHome })));
const ApplicationCatalogPage = lazy(() => import('./workspace-pages').then((module) => ({ default: module.ApplicationCatalogPage })));
const ChatPage = lazy(() => import('./workspace-pages').then((module) => ({ default: module.ChatPage })));

function resolvePage(path: string, user: CurrentUser, navigate: Navigate) {
  const fallbackPath = defaultRoute(user);
  if (path === '/admin/platform-overview') return <PlatformOverviewPage user={user} navigate={navigate} />;
  if (path === '/admin/tenants') return <DelegatedTenantPage />;
  if (path === '/admin/model-hub') return <ModelHubPage user={user} />;
  if (path === '/admin/approvals' || path === '/tenant/approvals') return <ApprovalCenterPage user={user} />;
  if (path === '/admin/audit') return <AuditPage />;
  if (path === '/tenant/overview') return <TenantOverviewPage user={user} navigate={navigate} />;
  if (path === '/tenant/roles') return <RolePermissionPage />;
  if (path === '/tenant/knowledge-bases' || path === '/workspace/knowledge-bases') {
    const capability = routeCapabilityFor(user, path);
    return capability
      ? <KnowledgeBasePage capability={capability} permissionCodes={user.permissionCodes} />
      : <ErrorPage kind="403" navigate={navigate} fallbackPath={fallbackPath} />;
  }
  if (path === '/tenant/applications') return <AppStudioPage key={`${user.tenantId}:${user.userId}`} user={user} />;
  if (path === '/workspace/apps') return <ApplicationCatalogPage navigate={navigate} />;
  if (path === '/workspace/home') return <WorkspaceHome user={user} navigate={navigate} />;
  if (path.includes('/chat') || path.startsWith('/workspace/sessions/')) return <ChatPage sessionId={path.startsWith('/workspace/sessions/') ? path.split('/').at(-1) : undefined} navigate={navigate} />;
  if (path === '/403') return <ErrorPage kind="403" navigate={navigate} fallbackPath={fallbackPath} />;
  if (path === '/feature-disabled') return <ErrorPage kind="feature" navigate={navigate} fallbackPath={fallbackPath} />;
  if (path === '/tenant/permission-requests') return <PermissionRequestPage user={user} />;
  if (path === '/tenant/users') return <TenantUsersPage />;
  return <ErrorPage kind="404" navigate={navigate} fallbackPath={fallbackPath} />;
}

function FocusedRoute({ children }: { children: ReactNode }) {
  useEffect(() => {
    const heading = document.querySelector<HTMLElement>('#main-content h1');
    if (!heading) return;
    heading.tabIndex = -1;
    heading.focus();
  }, []);
  return children;
}

export function PageRouter({ path, user, navigate }: { path: string; user: CurrentUser; navigate: Navigate }) {
  return <Suspense fallback={<AsyncState status="loading" loadingLabel="正在加载页面" />}>
    <FocusedRoute key={path}>{resolvePage(path, user, navigate)}</FocusedRoute>
  </Suspense>;
}
