import { expect, type Page } from '@playwright/test';

type DialogMotionStart = {
  target: 'surface' | 'backdrop';
  duration: number;
  reducedMotionAtStart: boolean;
};

type DialogMotionEvidenceWindow = Window & typeof globalThis & {
  __aipDialogMotionEvidence?: {
    records: DialogMotionStart[];
  };
};

export async function installDialogMotionEvidence(page: Page) {
  await page.addInitScript(() => {
    const evidenceWindow = window as DialogMotionEvidenceWindow;
    evidenceWindow.__aipDialogMotionEvidence = { records: [] };
    const originalAnimate = Element.prototype.animate;
    if (typeof originalAnimate !== 'function') return;

    Element.prototype.animate = function instrumentDialogAnimate(keyframes, options) {
      const animation = originalAnimate.call(this, keyframes, options);
      const target = this.matches('.fui-DialogSurface')
        ? 'surface'
        : this.matches('.fui-DialogSurface__backdrop')
          ? 'backdrop'
          : undefined;
      if (target) {
        const rawDuration = typeof options === 'number' ? options : options?.duration;
        const parsedDuration = typeof rawDuration === 'number'
          ? rawDuration
          : Number.parseFloat(String(rawDuration ?? 0));
        evidenceWindow.__aipDialogMotionEvidence?.records.push({
          target,
          duration: Number.isFinite(parsedDuration) ? parsedDuration : 0,
          reducedMotionAtStart: window.matchMedia('(prefers-reduced-motion: reduce)').matches,
        });
      }
      return animation;
    };
  });
}

export async function resetDialogMotionEvidence(page: Page) {
  await page.evaluate(() => {
    const evidenceWindow = window as DialogMotionEvidenceWindow;
    if (evidenceWindow.__aipDialogMotionEvidence) {
      evidenceWindow.__aipDialogMotionEvidence.records.length = 0;
    }
  });
}

export async function expectDialogMotionEvidence(
  page: Page,
  expectation: 'reduced' | 'animated',
) {
  const records = await page.evaluate(() => (
    (window as DialogMotionEvidenceWindow).__aipDialogMotionEvidence?.records ?? []
  ));
  const positiveStarts = records.filter((record) => record.duration > 0);
  if (expectation === 'reduced') {
    expect(
      positiveStarts,
      'reduced-motion Dialog/backdrop WAAPI must never start with a positive duration',
    ).toEqual([]);
    return;
  }

  const animatedTargets = [...new Set(positiveStarts
    .filter((record) => !record.reducedMotionAtStart)
    .map((record) => record.target))];
  expect(animatedTargets).toEqual(expect.arrayContaining(['surface', 'backdrop']));
}
