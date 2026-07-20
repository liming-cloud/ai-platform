import { expect, test as base, type Page } from '@playwright/test';

type RuntimeEnvironment = Readonly<Record<string, string | undefined>>;
declare const process: { readonly env: RuntimeEnvironment };

export interface DeployCredentials {
  tenantCode: string;
  username: string;
  password: string;
}

export type AcceptanceRole = 'platform-admin' | 'tenant-admin' | 'business-user';

const DEFAULT_ROLE_USERS: Record<AcceptanceRole, string> = {
  'platform-admin': 'admin',
  'tenant-admin': 'tenant-admin',
  'business-user': 'user',
};

export function deployCredentials(env: RuntimeEnvironment = process.env): DeployCredentials | undefined {
  const tenantCode = env.E2E_TENANT_CODE?.trim();
  const username = env.E2E_USERNAME?.trim()
    || env.E2E_PLATFORM_ADMIN_USERNAME?.trim()
    || DEFAULT_ROLE_USERS['platform-admin'];
  const password = env.E2E_PASSWORD;
  if (!tenantCode || !username || !password) return undefined;
  return { tenantCode, username, password };
}

export function roleCredentials(role: AcceptanceRole, env: RuntimeEnvironment = process.env): DeployCredentials | undefined {
  const tenantCode = env.E2E_TENANT_CODE?.trim();
  const password = env.E2E_PASSWORD;
  const variable = `E2E_${role.replaceAll('-', '_').toUpperCase()}_USERNAME`;
  const username = env[variable]?.trim() || DEFAULT_ROLE_USERS[role];
  if (!tenantCode || !username || !password) return undefined;
  return { tenantCode, username, password };
}

export async function signInThroughUi(page: Page, credentials: DeployCredentials) {
  await page.goto('/login');
  await page.getByLabel('租户编码').fill(credentials.tenantCode);
  await page.getByLabel('账号').fill(credentials.username);
  await page.getByLabel('密码').fill(credentials.password);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/);
  await expect(page.getByLabel('当前安全上下文')).toBeVisible();
}

export async function navigateThroughProductMenu(page: Page, label: string) {
  const mobileTrigger = page.getByRole('button', { name: '打开导航' });
  if (await mobileTrigger.isVisible()) {
    await mobileTrigger.click();
    await expect(page.getByRole('dialog', { name: '主导航' })).toBeVisible();
  }
  const navigation = page.getByRole('navigation');
  await expect(navigation).toBeVisible();
  await navigation.getByRole('button', { name: label, exact: true }).click();
}

interface AuthorizedFixtures {
  authorizedPage: Page;
}

export const test = base.extend<AuthorizedFixtures>({
  authorizedPage: async ({ page }, provideAuthorizedPage) => {
    const credentials = deployCredentials();
    if (!credentials) {
      throw new Error('Authorized E2E requires E2E_TENANT_CODE, E2E_USERNAME and E2E_PASSWORD.');
    }
    await signInThroughUi(page, credentials);
    await provideAuthorizedPage(page);
  },
});

export { expect };
