import { expect, test } from '@playwright/test';
import { expectNoWcagAaViolations } from './fixtures/accessibility';

test('login entry is usable from the keyboard', async ({ page }) => {
  await page.goto('/login');

  await expect(page.getByText('登录工作空间')).toBeVisible();
  const tenantCode = page.getByLabel('租户编码');
  const username = page.getByLabel('账号');
  const password = page.getByLabel('密码');
  const submit = page.getByRole('button', { name: '登录' });

  await expect(tenantCode).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(username).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(password).toBeFocused();

  await tenantCode.fill('smoke-tenant');
  await username.fill('smoke-user');
  await password.fill('not-submitted');
  await expect(submit).toBeEnabled();
  await page.keyboard.press('Tab');
  await expect(submit).toBeFocused();
});

test('login entry remains within a mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/login');

  await expect(page.getByText('登录工作空间')).toBeVisible();
  await expect(page.getByLabel('租户编码')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('login entry has no WCAG A/AA violations at any impact level', async ({ page }) => {
  await page.goto('/login');
  await expectNoWcagAaViolations(page);
});
