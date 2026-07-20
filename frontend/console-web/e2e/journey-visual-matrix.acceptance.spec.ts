import {
  expect,
  test,
  type APIRequestContext,
  type APIResponse,
  type Locator,
  type Page,
  type TestInfo,
} from '@playwright/test';
import {
  expectNoFullPageHorizontalOverflow,
  expectNoWcagAaViolations,
} from './fixtures/accessibility';
import {
  roleCredentials,
  type AcceptanceRole,
} from './fixtures/authorized';
import {
  DEPLOY_APPLICATION_NAME,
  DEPLOY_ROLE_JOURNEY_RUN_ID,
} from './fixtures/deploy-provision';
import {
  expectDialogMotionEvidence,
  installDialogMotionEvidence,
} from './fixtures/dialog-motion-evidence';

// Login credentials must never enter Playwright network/video artifacts. The
// suite attaches only terminal-state screenshots and secret-free task traces.
test.use({ trace: 'off', video: 'off' });

type RuntimeEnvironment = Readonly<Record<string, string | undefined>>;
declare const process: { readonly env: RuntimeEnvironment };

type JourneyId = 'J-01' | 'J-02' | 'J-04' | 'J-07' | 'J-08';

type ViewportEvidenceProfile = {
  id: string;
  label: string;
  width: number;
  height: number;
  colorScheme: 'light' | 'dark';
  forcedColors: 'none' | 'active';
  reducedMotion: 'no-preference' | 'reduce';
};

type CoreTaskEvidence = {
  role: AcceptanceRole;
  route: string;
  state: string;
  surface: Locator;
  primaryControl: Locator;
  dialog?: Locator;
  responsiveMasterDetail?: {
    container: Locator;
    detail: Locator;
  };
  focusReturnControl?: Locator;
  cleanup?: () => Promise<void>;
};

type JourneyEvidenceCase = {
  id: JourneyId;
  label: string;
  coreTask: string;
  navigationMode: 'ui-task' | 'preprovisioned-direct';
  run: (page: Page, profile: ViewportEvidenceProfile) => Promise<CoreTaskEvidence>;
};

type ApiEnvelope<T> = {
  header?: { code?: string; message?: string; traceId?: string };
  body?: T;
};

type ApplicationView = {
  applicationId: string;
  name: string;
};

type ChatSessionView = {
  sessionId: string;
  requiresNewSession: boolean;
  title: string;
};

const viewportEvidenceMatrix = [
  {
    id: '1440x1000-light',
    label: '宽屏桌面浅色',
    width: 1440, height: 1000,
    colorScheme: 'light', forcedColors: 'none', reducedMotion: 'no-preference',
  },
  {
    id: '1280x800-dark',
    label: '标准笔记本深色',
    width: 1280, height: 800,
    colorScheme: 'dark', forcedColors: 'none', reducedMotion: 'no-preference',
  },
  {
    id: '1024x768-forced-colors',
    label: '紧凑桌面系统强制色',
    width: 1024, height: 768,
    colorScheme: 'light', forcedColors: 'active', reducedMotion: 'no-preference',
  },
  {
    id: '768x1024-reduced-motion',
    label: '平板竖屏减少动态',
    width: 768, height: 1024,
    colorScheme: 'light', forcedColors: 'none', reducedMotion: 'reduce',
  },
  {
    id: '390x844-dark-reduced-motion',
    label: '移动竖屏深色减少动态',
    width: 390, height: 844,
    colorScheme: 'dark', forcedColors: 'none', reducedMotion: 'reduce',
  },
] as const satisfies readonly ViewportEvidenceProfile[];

const twoHundredPercentProfile = {
  id: '200-percent',
  label: '1440x1000 物理视窗的 200% 等效 CSS 视窗',
  width: 720,
  height: 500,
  colorScheme: 'light',
  forcedColors: 'none',
  reducedMotion: 'reduce',
} as const satisfies ViewportEvidenceProfile;

const rolePresentation: Record<AcceptanceRole, {
  label: string;
  route: RegExp;
  heading: RegExp;
}> = {
  'platform-admin': {
    label: '平台管理员',
    route: /\/admin\/platform-overview$/,
    heading: /^平台任务首页$/,
  },
  'tenant-admin': {
    label: '租户管理员',
    route: /\/tenant\/overview$/,
    heading: /^租户任务首页$/,
  },
  'business-user': {
    label: '业务用户',
    route: /\/workspace\/home$/,
    heading: /^你好，/,
  },
};

const j01RoleByProfile: Record<string, AcceptanceRole> = {
  '1440x1000-light': 'platform-admin',
  '1280x800-dark': 'tenant-admin',
  '1024x768-forced-colors': 'business-user',
  '768x1024-reduced-motion': 'tenant-admin',
  '390x844-dark-reduced-motion': 'business-user',
  '200-percent': 'platform-admin',
};

let sharedBusinessSessionId = '';

async function apiBody<T>(response: APIResponse, label: string): Promise<T> {
  const raw = await response.text();
  let envelope: ApiEnvelope<T>;
  try {
    envelope = JSON.parse(raw) as ApiEnvelope<T>;
  } catch {
    throw new Error(`${label} returned non-JSON HTTP ${response.status()}: ${raw.slice(0, 300)}`);
  }
  if (!response.ok() || envelope.header?.code !== 'SUCCESS' || envelope.body === undefined) {
    throw new Error(
      `${label} failed: HTTP ${response.status()}, code=${envelope.header?.code || 'missing'}, `
      + `message=${envelope.header?.message || 'missing'}, trace=${envelope.header?.traceId || 'missing'}`,
    );
  }
  return envelope.body;
}

async function createSharedBusinessSession(request: APIRequestContext) {
  const credentials = roleCredentials('business-user');
  if (!credentials) throw new Error('Missing deploy business user credentials.');
  const login = await apiBody<{ token: string }>(
    await request.post('/api/v1/auth/login', { data: credentials }),
    'J-08 visual matrix business login',
  );
  const headers = { Authorization: `Bearer ${login.token}` };
  const applications = await apiBody<ApplicationView[]>(
    await request.get('/api/v1/ai-applications', { headers }),
    'J-08 visual matrix application catalog',
  );
  const application = applications.find((candidate) => candidate.name === DEPLOY_APPLICATION_NAME);
  if (!application) throw new Error(`J-08 visual matrix application is missing: ${DEPLOY_APPLICATION_NAME}`);
  const sessionTitle = `视觉矩阵空会话 ${DEPLOY_ROLE_JOURNEY_RUN_ID}`;
  const sessionsPath = `/api/v1/ai-applications/${encodeURIComponent(application.applicationId)}/chat-sessions`;
  const sessions = await apiBody<ChatSessionView[]>(
    await request.get(sessionsPath, { headers }),
    'J-08 visual matrix session catalog',
  );
  for (const candidate of sessions) {
    if (candidate.title !== sessionTitle || candidate.requiresNewSession) continue;
    const messages = await apiBody<unknown[]>(
      await request.get(`/api/v1/chat-sessions/${encodeURIComponent(candidate.sessionId)}/messages`, { headers }),
      'J-08 reusable visual session history',
    );
    if (messages.length === 0) return candidate.sessionId;
  }
  const session = await apiBody<{ sessionId: string }>(
    await request.post(sessionsPath, { headers, data: { title: sessionTitle } }),
    'J-08 single shared empty session',
  );
  if (!session.sessionId) throw new Error('J-08 visual matrix session response has no sessionId.');
  return session.sessionId;
}

async function applyEvidenceProfile(page: Page, profile: ViewportEvidenceProfile) {
  await page.setViewportSize({ width: profile.width, height: profile.height });
  await page.emulateMedia({
    colorScheme: profile.colorScheme,
    forcedColors: profile.forcedColors,
    reducedMotion: profile.reducedMotion,
  });
}

async function installVisualMutationFence(page: Page) {
  const blockedMutations: string[] = [];
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const method = request.method().toUpperCase();
    const pathname = new URL(request.url()).pathname;
    const isReadOnly = method === 'GET' || method === 'HEAD' || method === 'OPTIONS';
    const isLogin = method === 'POST' && pathname === '/api/v1/auth/login';
    if (isReadOnly || isLogin) {
      await route.continue();
      return;
    }
    blockedMutations.push(`${method} ${pathname}`);
    await route.abort('blockedbyclient');
  });
  return () => {
    expect(
      blockedMutations,
      'visual evidence cells must not reach a non-login API mutation',
    ).toEqual([]);
  };
}

async function expectMediaPreferences(page: Page, profile: ViewportEvidenceProfile) {
  const media = await page.evaluate(() => ({
    dark: window.matchMedia('(prefers-color-scheme: dark)').matches,
    light: window.matchMedia('(prefers-color-scheme: light)').matches,
    forcedColors: window.matchMedia('(forced-colors: active)').matches,
    reducedMotion: window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  }));
  expect(media.forcedColors).toBe(profile.forcedColors === 'active');
  expect(media.reducedMotion).toBe(profile.reducedMotion === 'reduce');
  if (profile.forcedColors === 'none') {
    expect(media.dark).toBe(profile.colorScheme === 'dark');
    expect(media.light).toBe(profile.colorScheme === 'light');
  }
}

async function expectReducedMotionContract(page: Page, profile: ViewportEvidenceProfile) {
  if (profile.reducedMotion !== 'reduce') return;
  await expect.poll(() => page.evaluate(() => {
    const positiveDuration = (value: string) => value.split(',').some((entry) => {
      const duration = entry.trim();
      if (duration.endsWith('ms')) return Number.parseFloat(duration) > 0;
      if (duration.endsWith('s')) return Number.parseFloat(duration) > 0;
      return false;
    });
    const durationViolations = Array.from(document.querySelectorAll<HTMLElement>(
      '.sidebar, .fui-DialogSurface, .fui-DialogSurface__backdrop',
    )).flatMap((element) => {
      const style = window.getComputedStyle(element);
      return positiveDuration(style.animationDuration) || positiveDuration(style.transitionDuration)
        ? [element.className || element.getAttribute('role') || element.tagName]
        : [];
    });
    const activeAnimations = document.getAnimations().filter((animation) => (
      animation.pending || animation.playState === 'running'
    )).length;
    return { activeAnimations, durationViolations };
  })).toEqual({ activeAnimations: 0, durationViolations: [] });
}

async function activateWithKeyboard(page: Page, control: Locator) {
  await control.scrollIntoViewIfNeeded();
  await expect(control).toBeVisible();
  await expect(control).toBeEnabled();
  await control.focus();
  await expect(control).toBeFocused();
  await page.keyboard.press('Enter');
}

async function navigateWithKeyboard(page: Page, label: string) {
  const mobileTrigger = page.getByRole('button', { name: '打开导航' });
  let navigation: Locator;
  if (await mobileTrigger.isVisible()) {
    await activateWithKeyboard(page, mobileTrigger);
    const drawer = page.getByRole('dialog', { name: '主导航' });
    await expect(drawer).toBeVisible();
    navigation = drawer.getByRole('navigation');
  } else {
    navigation = page.getByRole('navigation');
  }
  await expect(navigation).toBeVisible();
  await activateWithKeyboard(page, navigation.getByRole('button', { name: label, exact: true }));
}

async function signInWithKeyboard(page: Page, role: AcceptanceRole) {
  const credentials = roleCredentials(role);
  if (!credentials) throw new Error(`Missing deploy credentials for ${role}.`);
  await page.goto('/login');
  const tenant = page.getByLabel('租户编码');
  const username = page.getByLabel('账号');
  const password = page.getByLabel('密码');
  const submit = page.getByRole('button', { name: '登录' });

  await expect(tenant).toBeFocused();
  await tenant.fill(credentials.tenantCode);
  await page.keyboard.press('Tab');
  await expect(username).toBeFocused();
  await username.fill(credentials.username);
  await page.keyboard.press('Tab');
  await expect(password).toBeFocused();
  await password.fill(credentials.password);
  await page.keyboard.press('Tab');
  await expect(submit).toBeFocused();
  await page.keyboard.press('Enter');

  const presentation = rolePresentation[role];
  await expect(page).toHaveURL(presentation.route);
  await expect(page.getByRole('heading', { level: 1, name: presentation.heading })).toBeVisible();
  const securityContext = page.getByLabel('当前安全上下文');
  await expect(securityContext).toContainText(`角色 ${presentation.label}`);
  await expect(securityContext).toContainText('安全上下文已建立');
}

async function expectDialogFitsViewport(page: Page, dialog: Locator) {
  await expect(dialog).toBeVisible();
  await expect.poll(() => dialog.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(true);
  const bounds = await dialog.boundingBox();
  const viewport = page.viewportSize();
  expect(bounds, 'active journey dialog must expose layout bounds').not.toBeNull();
  expect(viewport, 'journey viewport must be configured').not.toBeNull();
  expect(bounds!.x).toBeGreaterThanOrEqual(0);
  expect(bounds!.y).toBeGreaterThanOrEqual(0);
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(viewport!.width + 1);
  expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(viewport!.height + 1);
}

async function expectDialogScrollAndTerminalActionReachability(
  page: Page,
  dialog: Locator,
  primaryControl: Locator,
) {
  await expectDialogFitsViewport(page, dialog);
  const scrollContract = await dialog.evaluate((root) => {
    const candidates = [root, ...root.querySelectorAll<HTMLElement>('*')]
      .filter((candidate): candidate is HTMLElement => candidate instanceof HTMLElement);
    const scrollable = candidates.find((candidate) => {
      const overflowY = window.getComputedStyle(candidate).overflowY;
      return /^(auto|scroll|overlay)$/.test(overflowY)
        && candidate.scrollHeight > candidate.clientHeight + 1;
    });
    if (!scrollable) return { mode: 'content-fits', moved: true } as const;
    const initialScrollTop = scrollable.scrollTop;
    scrollable.scrollTop = scrollable.scrollHeight;
    return {
      mode: 'internal-scroll',
      moved: scrollable.scrollTop > initialScrollTop || scrollable.scrollTop > 0,
    } as const;
  });
  expect(scrollContract.moved, `${scrollContract.mode} must reach the dialog end`).toBe(true);

  await primaryControl.scrollIntoViewIfNeeded();
  await expect(primaryControl).toBeVisible();
  await expect(primaryControl).toBeEnabled();
  const focusContract = await primaryControl.evaluate((element) => {
    const root = element.closest<HTMLElement>('[role="dialog"]');
    if (!root) return { reachable: false, focusableCount: 0 };
    const focusables = Array.from(root.querySelectorAll<HTMLElement>([
      'button:not([disabled])',
      'input:not([disabled])',
      'select:not([disabled])',
      'textarea:not([disabled])',
      'a[href]',
      '[tabindex]:not([tabindex="-1"])',
    ].join(','))).filter((candidate) => candidate.tabIndex >= 0
      && candidate.getAttribute('aria-disabled') !== 'true'
      && candidate.getClientRects().length > 0);
    const targetIndex = focusables.indexOf(element as HTMLElement);
    if (targetIndex < 0 || focusables.length === 0) {
      return { reachable: false, focusableCount: focusables.length };
    }
    const predecessor = focusables[(targetIndex - 1 + focusables.length) % focusables.length];
    predecessor.focus();
    return { reachable: true, focusableCount: focusables.length };
  });
  expect(focusContract.reachable, 'dialog terminal action must participate in keyboard order').toBe(true);
  await page.keyboard.press('Tab');
  await expect(primaryControl).toBeFocused();
}

async function expectAllVisibleCriticalTouchTargets(page: Page) {
  const result = await page.locator('body').evaluate((root) => {
    const selector = [
      'button',
      'input:not([type="hidden"])',
      'select',
      'textarea',
      '[role="button"]',
      '[role="checkbox"]',
      '[role="combobox"]',
      '[role="switch"]',
    ].join(',');
    const effectiveTargetSelector = [
      '.fui-Button',
      '.fui-Input',
      '.fui-Select',
      '.fui-Combobox',
      '.fui-Textarea',
      '.fui-Checkbox',
      '.fui-Switch',
    ].join(',');
    const targets = new Set<HTMLElement>();
    root.querySelectorAll<HTMLElement>(selector).forEach((element) => {
      if (element.matches(':disabled, [aria-disabled="true"]')) return;
      const target = element.closest<HTMLElement>(effectiveTargetSelector) || element;
      if (target.closest('[aria-hidden="true"], [inert]')) return;
      const style = window.getComputedStyle(target);
      const bounds = target.getBoundingClientRect();
      const intersectsViewport = bounds.right > 0 && bounds.bottom > 0
        && bounds.left < window.innerWidth && bounds.top < window.innerHeight;
      if (style.display === 'none' || style.visibility === 'hidden'
        || bounds.width === 0 || bounds.height === 0 || !intersectsViewport) return;
      targets.add(target);
    });
    return {
      count: targets.size,
      undersized: Array.from(targets).flatMap((target) => {
        const bounds = target.getBoundingClientRect();
        if (bounds.width >= 44 && bounds.height >= 44) return [];
        return [{
          label: (target.getAttribute('aria-label') || target.textContent || target.tagName).trim().slice(0, 80),
          width: Math.round(bounds.width * 10) / 10,
          height: Math.round(bounds.height * 10) / 10,
        }];
      }),
    };
  });
  expect(result.count, 'mobile task surface must expose critical controls').toBeGreaterThan(0);
  expect(result.undersized, 'all visible critical mobile targets must be at least 44x44px').toEqual([]);
}

async function expectResponsiveMasterDetail(
  profile: ViewportEvidenceProfile,
  evidence: NonNullable<CoreTaskEvidence['responsiveMasterDetail']>,
) {
  await expect(evidence.container).toBeVisible();
  await expect(evidence.detail).toBeVisible();
  const layout = await evidence.container.evaluate((container) => {
    const primary = container.firstElementChild as HTMLElement | null;
    const detail = container.querySelector<HTMLElement>('.detail-panel');
    const primaryBounds = primary?.getBoundingClientRect();
    const detailBounds = detail?.getBoundingClientRect();
    const gridTemplateColumns = window.getComputedStyle(container).gridTemplateColumns;
    return {
      gridTemplateColumns,
      columnCount: gridTemplateColumns.trim().split(/\s+/).filter(Boolean).length,
      detailFollowsPrimary: primaryBounds && detailBounds
        ? detailBounds.top >= primaryBounds.bottom - 1
        : false,
    };
  });
  if (profile.width <= 1100) {
    expect(layout.columnCount, `compact master-detail columns: ${layout.gridTemplateColumns}`).toBe(1);
    expect(layout.detailFollowsPrimary, 'compact detail must follow the primary work region').toBe(true);
  } else {
    expect(layout.columnCount, `wide master-detail columns: ${layout.gridTemplateColumns}`).toBe(2);
  }
}

async function runJ01(page: Page, profile: ViewportEvidenceProfile): Promise<CoreTaskEvidence> {
  const role = j01RoleByProfile[profile.id] || 'platform-admin';
  await signInWithKeyboard(page, role);
  const mobileNavigation = page.getByRole('button', { name: '打开导航' });
  const visibleSessionControl = await mobileNavigation.isVisible()
    ? mobileNavigation
    : page.getByRole('button', { name: '退出登录' });
  return {
    role,
    route: new URL(page.url()).pathname,
    state: '可信会话、安全上下文和服务端默认任务首页均已建立',
    surface: page.locator('#main-content'),
    primaryControl: visibleSessionControl,
  };
}

async function runJ02(page: Page): Promise<CoreTaskEvidence> {
  await signInWithKeyboard(page, 'platform-admin');
  await navigateWithKeyboard(page, '租户与代管');
  await expect(page).toHaveURL(/\/admin\/tenants$/);
  await expect(page.getByRole('heading', { level: 1, name: '租户管理' })).toBeVisible();
  const table = page.getByRole('table', { name: '租户列表' });
  const masterDetail = page.locator('.master-detail');
  const detail = masterDetail.locator('.detail-panel');
  await expect(masterDetail).toBeVisible();
  await expect(detail).toBeVisible();
  const modify = table.getByRole('button', { name: /^修改 / }).first();
  await activateWithKeyboard(page, modify);
  const dialog = page.getByRole('dialog', { name: '修改租户' });
  await expect(dialog).toBeVisible();
  const name = dialog.getByLabel('租户名称');
  const original = await name.inputValue();
  expect(original).not.toBe('');
  await name.fill(`${original} 视觉草稿`);
  const confirm = dialog.getByRole('button', { name: '确认', exact: true });
  await expect(confirm).toBeEnabled();
  return {
    role: 'platform-admin',
    route: '/admin/tenants',
    state: '真实租户已定位，修改草稿可提交；矩阵单元在提交前取消',
    surface: dialog,
    primaryControl: confirm,
    dialog,
    responsiveMasterDetail: { container: masterDetail, detail },
    focusReturnControl: modify,
    cleanup: async () => {
      await page.keyboard.press('Escape');
      await expect(dialog).toBeHidden();
      await expect(modify).toBeFocused();
    },
  };
}

async function runJ04(page: Page, profile: ViewportEvidenceProfile): Promise<CoreTaskEvidence> {
  await signInWithKeyboard(page, 'tenant-admin');
  await navigateWithKeyboard(page, '用户管理');
  await expect(page).toHaveURL(/\/tenant\/users$/);
  await expect(page.getByRole('heading', { level: 1, name: '用户管理' })).toBeVisible();

  const invite = page.getByRole('button', { name: '邀请用户', exact: true });
  await activateWithKeyboard(page, invite);
  const invitationDialog = page.getByRole('dialog', { name: '邀请用户' });
  await expect(invitationDialog).toBeVisible();
  await invitationDialog.getByLabel('姓名').fill('视觉验收使用者');
  await invitationDialog.getByLabel('登录账号').fill(`visual-${profile.id}`);
  await invitationDialog.getByLabel('邮箱').fill(`visual-${profile.id}@example.invalid`);
  await expect(invitationDialog.getByRole('button', { name: '发送邀请' })).toBeEnabled();
  await page.keyboard.press('Escape');
  await expect(invitationDialog).toBeHidden();
  await expect(invite).toBeFocused();

  await navigateWithKeyboard(page, '角色权限');
  await expect(page).toHaveURL(/\/tenant\/roles$/);
  await expect(page.getByRole('heading', { level: 1, name: '角色权限' })).toBeVisible();
  const permission = page.locator('.permission-groups').getByRole('checkbox').first();
  await expect(permission).toBeEnabled();
  const originallyChecked = await permission.isChecked();
  await permission.focus();
  await expect(permission).toBeFocused();
  await page.keyboard.press('Space');
  await expect(permission).toBeChecked({ checked: !originallyChecked });

  if (profile.width === 390) await expectAllVisibleCriticalTouchTargets(page);

  const save = page.getByRole('button', { name: '保存权限', exact: true });
  await activateWithKeyboard(page, save);
  const dialog = page.getByRole('dialog', { name: '确认替换角色权限' });
  await expect(dialog.getByLabel('权限变更差异')).toBeVisible();
  await dialog.getByLabel('变更原因').fill('视觉矩阵仅验证完整替换确认态，不提交权限变更');
  const confirm = dialog.getByRole('button', { name: '确认替换权限' });
  await expect(confirm).toBeEnabled();
  return {
    role: 'tenant-admin',
    route: '/tenant/roles',
    state: '邀请草稿已键盘取消；角色权限差异和危险确认完整可见但未提交',
    surface: dialog,
    primaryControl: confirm,
    dialog,
    focusReturnControl: permission,
    cleanup: async () => {
      await page.keyboard.press('Escape');
      await expect(dialog).toBeHidden();
      await permission.focus();
      await page.keyboard.press('Space');
      await expect(permission).toBeChecked({ checked: originallyChecked });
    },
  };
}

async function runJ07(page: Page): Promise<CoreTaskEvidence> {
  await signInWithKeyboard(page, 'tenant-admin');
  await navigateWithKeyboard(page, 'AI应用');
  await expect(page).toHaveURL(/\/tenant\/applications$/);
  await expect(page.getByRole('heading', { level: 1, name: '应用工作室' })).toBeVisible();
  const row = page.getByRole('table', { name: '应用治理列表' })
    .getByRole('row')
    .filter({ hasText: DEPLOY_APPLICATION_NAME });
  await expect(row).toHaveCount(1);
  const govern = row.getByRole('button', { name: '治理', exact: true });
  await activateWithKeyboard(page, govern);
  const detail = page.getByLabel('应用工作室详情');
  const masterDetail = detail.locator('..');
  await expect(detail.getByRole('heading', { level: 2, name: DEPLOY_APPLICATION_NAME })).toBeVisible();
  const releases = detail.getByRole('table', { name: '应用发布版本' });
  await expect(releases).toBeVisible();
  await expect(releases.getByText('当前活动')).toHaveCount(1);

  const edit = detail.getByRole('button', { name: '编辑配置', exact: true });
  await activateWithKeyboard(page, edit);
  const dialog = page.getByRole('dialog', { name: '编辑应用配置' });
  await expect(dialog).toBeVisible();
  const description = dialog.getByLabel('应用说明');
  const original = await description.inputValue();
  expect(original).not.toBe('');
  await description.fill(`${original} 仅用于本地视觉草稿`);
  const save = dialog.getByRole('button', { name: '保存配置' });
  await expect(save).toBeEnabled();
  return {
    role: 'tenant-admin',
    route: '/tenant/applications',
    state: '活动 release 与版本证据可见，完整配置草稿可编辑；矩阵单元不触发发布写入',
    surface: dialog,
    primaryControl: save,
    dialog,
    responsiveMasterDetail: { container: masterDetail, detail },
    focusReturnControl: edit,
    cleanup: async () => {
      await page.keyboard.press('Escape');
      await expect(dialog).toBeHidden();
      await expect(edit).toBeFocused();
    },
  };
}

async function runJ08(page: Page, profile: ViewportEvidenceProfile): Promise<CoreTaskEvidence> {
  if (!sharedBusinessSessionId) throw new Error('J-08 shared visual session was not provisioned.');
  await signInWithKeyboard(page, 'business-user');
  await navigateWithKeyboard(page, 'AI应用');
  await expect(page).toHaveURL(/\/workspace\/apps$/);
  const application = page.locator('article.application-item').filter({
    has: page.getByRole('heading', { level: 2, name: DEPLOY_APPLICATION_NAME }),
  });
  const start = application.getByRole('button', { name: '开始使用' });
  await expect(start).toBeEnabled();
  await start.focus();
  await expect(start).toBeFocused();

  await page.goto(`/workspace/sessions/${encodeURIComponent(sharedBusinessSessionId)}`);
  await expect(page.getByRole('heading', { level: 1, name: 'RAG 对话' })).toBeVisible();
  const composer = page.getByLabel('输入问题');
  await expect(composer).toBeEnabled();
  await composer.focus();
  await expect(composer).toBeFocused();
  await page.keyboard.insertText(`视觉矩阵草稿 ${profile.id}`);
  const send = page.getByRole('button', { name: '发送', exact: true });
  await expect(send).toBeEnabled();
  return {
    role: 'business-user',
    route: `/workspace/sessions/${sharedBusinessSessionId}`,
    state: '授权应用与真实 release-pinned 空会话可用，消息草稿可提交但矩阵单元不调用模型',
    surface: page.locator('main.page-stack'),
    primaryControl: send,
    cleanup: async () => {
      await composer.fill('');
      await expect(send).toBeDisabled();
    },
  };
}

const journeyEvidenceCases: readonly JourneyEvidenceCase[] = [
  {
    id: 'J-01',
    label: '可信登录与会话',
    coreTask: '三类真实身份通过键盘建立会话并到达服务端默认任务首页',
    navigationMode: 'ui-task',
    run: runJ01,
  },
  {
    id: 'J-02',
    label: '平台租户治理',
    coreTask: '定位真实租户、键盘打开修改草稿并取消，证明无意外写入',
    navigationMode: 'ui-task',
    run: runJ02,
  },
  {
    id: 'J-04',
    label: '成员与角色治理',
    coreTask: '准备并取消邀请，再审阅角色权限完整替换差异而不提交',
    navigationMode: 'ui-task',
    run: runJ04,
  },
  {
    id: 'J-07',
    label: '应用构建与发布治理',
    coreTask: '查看活动 release、历史证据并准备完整配置草稿而不发布',
    navigationMode: 'ui-task',
    run: runJ07,
  },
  {
    id: 'J-08',
    label: '授权应用对话',
    coreTask: '核对授权目录目标应用可操作，随后直接打开预建空会话并准备消息，不调用模型',
    navigationMode: 'preprovisioned-direct',
    run: runJ08,
  },
];

function journeyEvidenceAssertions(
  profile: ViewportEvidenceProfile,
  task: CoreTaskEvidence,
) {
  const assertions = [
    'keyboard',
    'overflow',
    'axe-wcag-a-aa',
    'visible-terminal-state',
    'media-preferences',
    'mutation-fence',
  ];
  if (profile.reducedMotion === 'reduce') assertions.push('reduced-motion');
  if (profile.width === 390) assertions.push('touch-target-44px');
  if (task.dialog) assertions.push('dialog-scroll-reachability');
  if (task.responsiveMasterDetail) assertions.push('master-detail-responsive');
  if (task.focusReturnControl) assertions.push('focus-restoration');
  return assertions;
}

async function attachJourneyEvidence(
  testInfo: TestInfo,
  journey: JourneyEvidenceCase,
  profile: ViewportEvidenceProfile,
  task: CoreTaskEvidence,
  screenshot: Awaited<ReturnType<Page['screenshot']>>,
) {
  const evidenceId = `${journey.id.toLowerCase()}-${profile.id}`;
  await testInfo.attach(`${evidenceId}-journey-screenshot`, {
    body: screenshot,
    contentType: 'image/png',
  });
  await testInfo.attach(`${evidenceId}-journey-task-trace`, {
    body: JSON.stringify({
      schema: 'journey-visual-evidence/v1',
      candidateSha: process.env.AIP_SOURCE_REVISION || 'unbound-working-tree',
      runId: DEPLOY_ROLE_JOURNEY_RUN_ID,
      journeyId: journey.id,
      journeyLabel: journey.label,
      role: task.role,
      coreTask: journey.coreTask,
      navigationMode: journey.navigationMode,
      route: task.route,
      terminalState: task.state,
      viewport: { width: profile.width, height: profile.height },
      media: {
        colorScheme: profile.colorScheme,
        forcedColors: profile.forcedColors,
        reducedMotion: profile.reducedMotion,
      },
      assertions: journeyEvidenceAssertions(profile, task),
      completedAt: new Date().toISOString(),
    }, null, 2),
    contentType: 'application/json',
  });
}

async function executeJourneyEvidenceCell(
  page: Page,
  testInfo: TestInfo,
  journey: JourneyEvidenceCase,
  profile: ViewportEvidenceProfile,
) {
  await installDialogMotionEvidence(page);
  await applyEvidenceProfile(page, profile);
  const expectNoUnexpectedMutations = await installVisualMutationFence(page);
  let task: CoreTaskEvidence | undefined;
  let screenshot: Awaited<ReturnType<Page['screenshot']>> | undefined;
  try {
    task = await journey.run(page, profile);
    await expect(task.surface).toBeVisible();
    await expect(task.primaryControl).toBeVisible();
    await expectMediaPreferences(page, profile);
    await expectReducedMotionContract(page, profile);
    if (profile.reducedMotion === 'reduce') {
      await expectDialogMotionEvidence(page, 'reduced');
    } else if (task.dialog) {
      await expectDialogMotionEvidence(page, 'animated');
    }
    await expectNoFullPageHorizontalOverflow(page);
    if (task.dialog) {
      await expectDialogScrollAndTerminalActionReachability(page, task.dialog, task.primaryControl);
    }
    if (task.responsiveMasterDetail) {
      await expectResponsiveMasterDetail(profile, task.responsiveMasterDetail);
    }
    if (profile.width === 390) await expectAllVisibleCriticalTouchTargets(page);
    await expectNoWcagAaViolations(page);
    screenshot = await page.screenshot({ fullPage: true });
  } finally {
    try {
      await task?.cleanup?.();
    } finally {
      try {
        if (task?.focusReturnControl) await expect(task.focusReturnControl).toBeFocused();
        await page.evaluate(() => new Promise<void>((resolve) => {
          window.requestAnimationFrame(() => resolve());
        }));
      } finally {
        expectNoUnexpectedMutations();
      }
    }
  }
  if (!task || !screenshot) throw new Error('Journey evidence capture did not complete.');
  await attachJourneyEvidence(testInfo, journey, profile, task, screenshot);
}

// Visual cells must not submit destructive mutations. J-02/J-04/J-07/J-08
// persistent semantics remain in the lease/recovery and v1/v2/v3 acceptance
// suites. A single shared empty J-08 session is created for all visual cells.
for (const journey of journeyEvidenceCases) {
  test.describe(`${journey.id} ${journey.label} deploy 视觉与可用性矩阵`, () => {
    if (journey.id === 'J-08') {
      test.describe.configure({ mode: 'serial' });
      test.beforeAll(async ({ request }) => {
        sharedBusinessSessionId = await createSharedBusinessSession(request);
      });
    }

    for (const profile of viewportEvidenceMatrix) {
      test(`${profile.label}完成核心任务、overflow、axe 与截图`, async ({ page }, testInfo) => {
        testInfo.setTimeout(90_000);
        await executeJourneyEvidenceCell(page, testInfo, journey, profile);
      });
    }

    test('200% 等效 CSS 视口完成同一核心任务且不丢失操作', async ({ page }, testInfo) => {
      testInfo.setTimeout(90_000);
      await executeJourneyEvidenceCell(page, testInfo, journey, twoHundredPercentProfile);
    });
  });
}
