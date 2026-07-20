import AxeBuilder from '@axe-core/playwright';
import { expect, type Page } from '@playwright/test';

const WCAG_AA_TAGS = [
  'wcag2a',
  'wcag2aa',
  'wcag21a',
  'wcag21aa',
  'wcag22a',
  'wcag22aa',
] as const;

// Fluent UI's Tabster runtime inserts focus-boundary sentinels that are
// intentionally focusable and aria-hidden. They are framework control nodes,
// not product content; exclude only that exact implementation marker while
// retaining every axe rule for the application DOM.
const TABSTER_FOCUS_SENTINEL = '[data-tabster-dummy][aria-hidden="true"]';

export async function expectNoWcagAaViolations(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags([...WCAG_AA_TAGS])
    .exclude(TABSTER_FOCUS_SENTINEL)
    .analyze();
  const summary = results.violations
    .map((violation) => `${violation.id} [${violation.impact ?? 'unclassified'}] ${violation.nodes.length} node(s)`)
    .join('\n');

  // Assert the complete WCAG A/AA result set. No impact filter means minor,
  // moderate, serious, critical and unclassified violations all fail.
  expect(results.violations, summary || 'No WCAG A/AA violations found').toEqual([]);
}

export async function expectNoFullPageHorizontalOverflow(page: Page) {
  await expect.poll(() => page.evaluate(
    () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
  )).toBe(true);
}
