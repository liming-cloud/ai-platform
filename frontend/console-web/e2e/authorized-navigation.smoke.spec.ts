import { expect, test } from './fixtures/authorized';

test.use({ trace: 'off', video: 'off' });

test('authorized navigation preserves one current route and can move to another allowed entry', async ({ authorizedPage }) => {
  const navigation = authorizedPage.getByRole('navigation');
  const current = navigation.locator('.nav-item[aria-current="page"]');
  await expect(current).toHaveCount(1);

  const allowedEntries = navigation.locator('.nav-item:not(:disabled)');
  expect(await allowedEntries.count()).toBeGreaterThan(0);

  const nextEntry = navigation.locator('.nav-item:not([aria-current="page"]):not(:disabled)').first();
  await expect(nextEntry, 'authorized acceptance identity must have another allowed navigation entry').toBeVisible();
  await nextEntry.click();
  await expect(nextEntry).toHaveAttribute('aria-current', 'page');
  await expect(authorizedPage).not.toHaveURL(/\/login(?:\?|$)/);
});
