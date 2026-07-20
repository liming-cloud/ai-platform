import { FormEvent, lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Avatar,
  Button,
  Field,
  FluentProvider,
  Input,
  MessageBar,
  MessageBarBody,
  Spinner,
  Text,
  Title1,
  Title3,
} from '@fluentui/react-components';
import {
  Apps24Regular,
  BuildingMultiple24Regular,
  Dismiss20Regular,
  Navigation20Regular,
  PeopleTeam24Regular,
  SignOut20Regular,
  CheckmarkCircle24Regular,
} from '@fluentui/react-icons';
import { activateUser, getCurrentUser, getTenantFeatures, login, logout, SESSION_EXPIRED_EVENT, session } from './api';
import { AsyncState } from './async-state';
import { ErrorPage } from './error-page';
import { defaultRoute, navFor, roleContextLabel, routeDecision } from './navigation';
import { themeForPreferences } from './theme';
import type { CurrentUser, TenantFeature } from './types';
import './styles.css';

const PageRouter = lazy(() => import('./pages').then((module) => ({ default: module.PageRouter })));
const MAX_EXPIRY_TIMER_DELAY = 2_147_000_000;

function asError(reason: unknown, fallback: string) {
  return reason instanceof Error ? reason : new Error(fallback);
}

function isAbortError(reason: unknown) {
  return reason instanceof Error && reason.name === 'AbortError';
}

function useMediaQuery(query: string) {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches);
  useEffect(() => {
    const media = window.matchMedia(query);
    const update = () => setMatches(media.matches);
    update();
    media.addEventListener('change', update);
    return () => media.removeEventListener('change', update);
  }, [query]);
  return matches;
}

function useLocation() {
  const [path, setPath] = useState(() => window.location.pathname);
  useEffect(() => {
    const update = () => setPath(window.location.pathname);
    window.addEventListener('popstate', update);
    return () => window.removeEventListener('popstate', update);
  }, []);
  const navigate = useCallback((next: string, replace = false) => {
    if (replace) window.history.replaceState({}, '', next);
    else window.history.pushState({}, '', next);
    setPath(next);
    window.scrollTo({ top: 0 });
  }, []);
  return { path, navigate };
}

function LoginPage({ onSuccess }: { onSuccess: (user: CurrentUser, signal: AbortSignal) => Promise<void> }) {
  const [tenantCode, setTenantCode] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>();
  const requestRef = useRef<AbortController>();

  useEffect(() => () => requestRef.current?.abort(), []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!tenantCode.trim() || !username.trim() || !password) return;
    requestRef.current?.abort();
    const controller = new AbortController();
    requestRef.current = controller;
    setSubmitting(true); setError(undefined);
    try {
      await login(tenantCode.trim(), username.trim(), password, controller.signal);
      const current = await getCurrentUser(controller.signal);
      await onSuccess(current, controller.signal);
    } catch (reason) {
      if (!controller.signal.aborted && !isAbortError(reason)) {
        setError(asError(reason, '登录失败').message);
      }
    } finally {
      if (requestRef.current === controller && !controller.signal.aborted) {
        requestRef.current = undefined;
        setSubmitting(false);
      }
    }
  }

  return <div className="login-page">
    <section className="login-context"><div className="product-mark"><Apps24Regular /><span>AI Platform</span></div><div><span className="on-brand-metadata">企业 AI 可信运行平台</span><Title1 as="h1">让模型、知识与权限在同一条业务链路中运行</Title1><Text>面向平台治理、租户管理与一线业务使用，所有敏感访问都以服务端身份和资源策略为准。</Text></div><div className="login-principles"><span>租户边界隔离</span><span>RAG 引用过滤</span><span>关键操作留痕</span></div></section>
    <main className="login-panel"><form aria-labelledby="login-title" aria-busy={submitting} onSubmit={submit}><div><Title3 as="h2" id="login-title">登录工作空间</Title3><Text>使用组织分配的租户与账号登录</Text></div>{error && <MessageBar intent="error" role="alert"><MessageBarBody>{error}</MessageBarBody></MessageBar>}<Field label="租户编码" required><Input autoFocus autoComplete="organization" value={tenantCode} onChange={(_, data) => setTenantCode(data.value)} /></Field><Field label="账号" required><Input autoComplete="username" value={username} onChange={(_, data) => setUsername(data.value)} /></Field><Field label="密码" required><Input type="password" autoComplete="current-password" value={password} onChange={(_, data) => setPassword(data.value)} /></Field><Button type="submit" appearance="primary" size="large" disabled={submitting || !tenantCode.trim() || !username.trim() || !password}>{submitting ? '正在验证' : '登录'}</Button></form></main>
  </div>;
}

function ActivationPage({ navigate }: { navigate: (path: string, replace?: boolean) => void }) {
  const token = new URLSearchParams(window.location.search).get('token') || '';
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [completed, setCompleted] = useState(false);
  const [error, setError] = useState<string>();
  async function submit(event: FormEvent) { event.preventDefault(); if (!token || password.length < 15 || password.length > 128 || password !== confirmation) return; setSubmitting(true); setError(undefined); try { await activateUser(token, password); window.history.replaceState({}, '', '/activate'); setCompleted(true); } catch (reason) { setError(reason instanceof Error ? reason.message : '账号激活失败'); } finally { setSubmitting(false); } }
  return <main className="activation-page"><section className="activation-panel">{completed ? <><CheckmarkCircle24Regular /><Title3 as="h1">账号已激活</Title3><Text>现在可以使用租户编码、登录账号和新密码进入平台。</Text><Button appearance="primary" onClick={() => navigate('/login', true)}>前往登录</Button></> : <form onSubmit={submit}><div><Title3 as="h1">激活账号</Title3><Text>设置仅由你掌握的登录密码。激活链接只能使用一次。</Text></div>{!token && <MessageBar intent="error"><MessageBarBody>激活链接缺少令牌，请联系租户管理员重新邀请。</MessageBarBody></MessageBar>}{error && <MessageBar intent="error"><MessageBarBody>{error}</MessageBarBody></MessageBar>}<Field label="新密码" hint="至少 15 个字符，最多 128 个字符" required><Input type="password" maxLength={128} value={password} onChange={(_, data) => setPassword(data.value)} /></Field><Field label="确认密码" required validationMessage={confirmation && confirmation !== password ? '两次输入的密码不一致' : undefined}><Input type="password" maxLength={128} value={confirmation} onChange={(_, data) => setConfirmation(data.value)} /></Field><Button type="submit" appearance="primary" disabled={submitting || !token || password.length < 15 || password.length > 128 || password !== confirmation}>{submitting ? '正在激活' : '激活账号'}</Button></form>}</section></main>;
}

function Shell({ user, tenantFeatures, path, navigate, onLogout }: { user: CurrentUser; tenantFeatures: TenantFeature[]; path: string; navigate: (path: string, replace?: boolean) => void; onLogout: () => void }) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const mobileViewport = useMediaQuery('(max-width: 768px)');
  const mobileMenuButton = useRef<HTMLButtonElement>(null);
  const mobileCloseButton = useRef<HTMLButtonElement>(null);
  const navigationElement = useRef<HTMLElement>(null);
  const shellMainElement = useRef<HTMLDivElement>(null);
  const wasMobileOpen = useRef(false);
  const items = useMemo(() => navFor(user, tenantFeatures), [tenantFeatures, user]);
  const sections = Array.from(new Set(items.map((item) => item.section)));
  const roleLabel = roleContextLabel(user);
  const EntryIcon = roleLabel.startsWith('平台') ? BuildingMultiple24Regular : roleLabel.startsWith('租户') ? PeopleTeam24Regular : Apps24Regular;
  function go(next: string) { wasMobileOpen.current = false; navigate(next); setMobileOpen(false); }
  useEffect(() => {
    if (mobileOpen) {
      wasMobileOpen.current = true;
      mobileCloseButton.current?.focus();
    } else if (wasMobileOpen.current) {
      wasMobileOpen.current = false;
      mobileMenuButton.current?.focus();
    }
  }, [mobileOpen]);
  useEffect(() => {
    if (!mobileOpen) return;
    const keepFocusInNavigation = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        setMobileOpen(false);
        return;
      }
      if (event.key !== 'Tab') return;
      const navigation = navigationElement.current;
      if (!navigation) return;
      const focusable = Array.from(navigation.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      ));
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const focused = document.activeElement;
      if (event.shiftKey && (focused === first || !navigation.contains(focused))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (focused === last || !navigation.contains(focused))) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', keepFocusInNavigation);
    return () => document.removeEventListener('keydown', keepFocusInNavigation);
  }, [mobileOpen]);
  useEffect(() => {
    if (!mobileViewport) setMobileOpen(false);
  }, [mobileViewport]);
  useEffect(() => {
    const navigation = navigationElement.current;
    const main = shellMainElement.current;
    if (mobileViewport && !mobileOpen) navigation?.setAttribute('inert', '');
    else navigation?.removeAttribute('inert');
    if (mobileViewport && mobileOpen) main?.setAttribute('inert', '');
    else main?.removeAttribute('inert');
  }, [mobileOpen, mobileViewport]);
  const access = routeDecision(user, path, tenantFeatures);
  return <div className="app-shell">
    <aside ref={navigationElement} id="primary-navigation" className={`sidebar ${mobileOpen ? 'is-open' : ''}`} aria-label="主导航" aria-hidden={mobileViewport && !mobileOpen ? true : undefined} aria-modal={mobileViewport && mobileOpen ? true : undefined} role={mobileViewport ? 'dialog' : undefined}>
      <div className="sidebar-brand"><EntryIcon /><div><strong>AI Platform</strong><span>{roleLabel}入口</span></div><Button ref={mobileCloseButton} className="mobile-close" appearance="subtle" icon={<Dismiss20Regular />} aria-label="关闭导航" onClick={() => setMobileOpen(false)}>关闭</Button></div>
      <nav>{sections.map((section, index) => <section key={section} aria-labelledby={`nav-section-${index}`}><Text as="h2" id={`nav-section-${index}`} className="nav-section">{section}</Text>{items.filter((item) => item.section === section).map((item) => {
        const disabledDescriptionId = `nav-disabled-${item.path.replaceAll('/', '-')}`;
        return <button
          type="button"
          aria-label={item.label}
          aria-current={path === item.path ? 'page' : undefined}
          aria-disabled={item.disabledReason ? true : undefined}
          aria-describedby={item.disabledReason ? disabledDescriptionId : undefined}
          className={path === item.path ? 'nav-item is-active' : 'nav-item'}
          key={item.path}
          onClick={() => { if (!item.disabledReason) go(item.path); }}
        ><span>{item.label}</span>{item.disabledReason && <span id={disabledDescriptionId} className="nav-disabled-reason">当前租户未启用此功能</span>}</button>;
      })}</section>)}</nav>
      <div className="sidebar-user"><Avatar name={user.displayName} size={32} /><div><strong>{user.displayName}</strong><span>{user.tenantId} · {roleLabel}</span></div><Button className="logout-button" appearance="subtle" icon={<SignOut20Regular />} aria-label="退出登录" onClick={onLogout}>退出</Button></div>
    </aside>
    {mobileOpen && <button className="scrim" tabIndex={-1} aria-label="关闭导航遮罩" onClick={() => setMobileOpen(false)} />}
    <div ref={shellMainElement} id="main-content" tabIndex={-1} className="shell-main" aria-hidden={mobileViewport && mobileOpen ? true : undefined}>
      <a className="skip-link" href="#main-content">跳到主要内容</a>
      <header className="topbar">
        <Button ref={mobileMenuButton} className="mobile-menu" appearance="subtle" icon={<Navigation20Regular />} aria-label="打开导航" aria-controls="primary-navigation" aria-expanded={mobileOpen} onClick={() => setMobileOpen(true)}>导航</Button>
        <div className="topbar-title"><strong>{items.find((item) => item.path === path)?.label || 'AI Platform'}</strong><span>{user.loginName}</span></div>
        <div className="topbar-context" aria-label="当前安全上下文">
          <span>租户 {user.tenantId}</span>
          <span>角色 {roleLabel}</span>
          <span className="security-state">安全上下文已建立</span>
        </div>
      </header>
      {access === 'allowed'
        ? <Suspense fallback={<AsyncState status="loading" loadingLabel="正在加载页面路由" />}><PageRouter path={path} user={user} navigate={go} /></Suspense>
        : <ErrorPage kind={access === 'tenant_feature_disabled' ? 'feature' : access === 'not_found' ? '404' : '403'} navigate={go} fallbackPath={defaultRoute(user, tenantFeatures)} />}
    </div>
  </div>;
}

export default function App() {
  const { path, navigate } = useLocation();
  const [user, setUser] = useState<CurrentUser>();
  const [tenantFeatures, setTenantFeatures] = useState<TenantFeature[]>([]);
  const [loading, setLoading] = useState(true);
  const [bootstrapError, setBootstrapError] = useState<Error>();
  const [bootstrapAttempt, setBootstrapAttempt] = useState(0);
  const bootstrapControllerRef = useRef<AbortController>();
  const logoutControllerRef = useRef<AbortController>();
  const prefersDark = useMediaQuery('(prefers-color-scheme: dark)');
  const forcedColors = useMediaQuery('(forced-colors: active)');
  const theme = useMemo(() => themeForPreferences({ dark: prefersDark, forcedColors }), [forcedColors, prefersDark]);

  const loadTenantFeatures = useCallback(async (current: CurrentUser, signal?: AbortSignal) => {
    if (!current.permissionCodes.includes('identity-access:iam-config:read')) return [];
    return getTenantFeatures(signal);
  }, []);

  useEffect(() => {
    const expire = () => {
      bootstrapControllerRef.current?.abort();
      logoutControllerRef.current?.abort();
      setUser(undefined);
      setTenantFeatures([]);
      setBootstrapError(undefined);
      setLoading(false);
      navigate('/login', true);
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, expire);
    const stopSynchronization = session.synchronize();
    session.expireIfNeeded();
    return () => {
      stopSynchronization();
      window.removeEventListener(SESSION_EXPIRED_EVENT, expire);
    };
  }, [navigate]);

  useEffect(() => {
    if (!user) return undefined;
    let expiryTimer: number | undefined;

    const schedule = () => {
      if (expiryTimer !== undefined) window.clearTimeout(expiryTimer);
      if (session.expireIfNeeded()) return;
      const expiresAt = session.snapshot().expiresAt || user.accessTokenExpiresAt;
      const expiry = Date.parse(expiresAt);
      if (!Number.isFinite(expiry) || expiry <= Date.now()) {
        session.clear();
        return;
      }
      expiryTimer = window.setTimeout(schedule, Math.min(expiry - Date.now(), MAX_EXPIRY_TIMER_DELAY));
    };
    const revalidateVisibleSession = () => {
      if (document.visibilityState === 'visible') schedule();
    };

    schedule();
    window.addEventListener('pageshow', schedule);
    document.addEventListener('visibilitychange', revalidateVisibleSession);
    return () => {
      if (expiryTimer !== undefined) window.clearTimeout(expiryTimer);
      window.removeEventListener('pageshow', schedule);
      document.removeEventListener('visibilitychange', revalidateVisibleSession);
    };
  }, [user]);

  useEffect(() => {
    const initialPath = window.location.pathname;
    const initialSession = session.snapshot();
    if (!initialSession.token) {
      setUser(undefined);
      setTenantFeatures([]);
      setBootstrapError(undefined);
      setLoading(false);
      if (initialPath !== '/login' && initialPath !== '/activate') navigate('/login', true);
      return;
    }

    const controller = new AbortController();
    bootstrapControllerRef.current = controller;
    let active = true;
    let committedSession = initialSession;
    const canCommit = () => (
      active && !controller.signal.aborted && session.isCurrent(committedSession)
    );

    setLoading(true);
    setBootstrapError(undefined);
    void (async () => {
      try {
        const current = await getCurrentUser(controller.signal);
        committedSession = session.snapshot();
        if (!canCommit()) return;
        const features = await loadTenantFeatures(current, controller.signal);
        if (!canCommit()) return;
        setUser(current);
        setTenantFeatures(features);
        if (initialPath === '/login' || initialPath === '/') {
          navigate(defaultRoute(current, features), true);
        }
      } catch (reason) {
        if (!canCommit() || isAbortError(reason)) return;
        setUser(undefined);
        setTenantFeatures([]);
        setBootstrapError(asError(reason, '暂时无法验证当前登录状态'));
      } finally {
        if (canCommit()) setLoading(false);
      }
    })();

    return () => {
      active = false;
      controller.abort();
      if (bootstrapControllerRef.current === controller) bootstrapControllerRef.current = undefined;
    };
  }, [bootstrapAttempt, loadTenantFeatures, navigate]);

  useEffect(() => () => {
    bootstrapControllerRef.current?.abort();
    logoutControllerRef.current?.abort();
  }, []);

  function signOut() {
    const accessToken = session.token();
    session.clear();
    bootstrapControllerRef.current?.abort();
    logoutControllerRef.current?.abort();
    const controller = new AbortController();
    logoutControllerRef.current = controller;
    setUser(undefined);
    setTenantFeatures([]);
    setBootstrapError(undefined);
    setLoading(false);
    navigate('/login', true);
    void logout(accessToken, controller.signal).catch(() => undefined).finally(() => {
      if (logoutControllerRef.current === controller) logoutControllerRef.current = undefined;
    });
  }

  async function signedIn(current: CurrentUser, signal: AbortSignal) {
    const loginSession = session.snapshot();
    const features = await loadTenantFeatures(current, signal);
    if (signal.aborted || !session.isCurrent(loginSession)) return;
    setUser(current);
    setTenantFeatures(features);
    setBootstrapError(undefined);
    navigate(defaultRoute(current, features), true);
  }

  function useAnotherAccount() {
    session.clear();
    bootstrapControllerRef.current?.abort();
    setUser(undefined);
    setTenantFeatures([]);
    setBootstrapError(undefined);
    setLoading(false);
    navigate('/login', true);
  }

  return <FluentProvider theme={theme} className="provider-root">
    {loading ? <div className="app-loading"><Spinner label="正在建立安全上下文" /></div> : bootstrapError ? <main className="auth-state-page"><section className="auth-error-panel" aria-labelledby="auth-error-title"><span className="auth-state-kicker">身份验证暂时中断</span><Title3 as="h1" id="auth-error-title">无法验证登录状态</Title3><Text>为避免展示未经确认的数据，工作台保持关闭。当前会话仍保留，可以在连接恢复后重试。</Text><AsyncState status="error" error={bootstrapError} onRetry={() => setBootstrapAttempt((attempt) => attempt + 1)} /><Button appearance="subtle" onClick={useAnotherAccount}>使用其他账号</Button></section></main> : path === '/login' ? <LoginPage onSuccess={signedIn} /> : path === '/activate' ? <ActivationPage navigate={navigate} /> : user ? <Shell user={user} tenantFeatures={tenantFeatures} path={path} navigate={navigate} onLogout={signOut} /> : <ErrorPage kind="403" navigate={navigate} fallbackPath="/login" />}
  </FluentProvider>;
}
