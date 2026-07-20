import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const matrixUrl = new URL('./journey-visual-matrix.acceptance.spec.ts', import.meta.url);
const roleJourneysUrl = new URL('./role-journeys.acceptance.spec.ts', import.meta.url);
const applicationGovernanceUrl = new URL('./application-governance.acceptance.spec.ts', import.meta.url);
const accessibilityUrl = new URL('./fixtures/accessibility.ts', import.meta.url);
const dialogMotionEvidenceUrl = new URL('./fixtures/dialog-motion-evidence.ts', import.meta.url);
const dialogMotionRuntimeUrl = new URL('../src/dialog-motion.ts', import.meta.url);

test('J-01/J-02/J-04/J-07/J-08 own the complete five-viewport evidence matrix', async () => {
  const source = await readFile(matrixUrl, 'utf8');

  for (const journeyId of ['J-01', 'J-02', 'J-04', 'J-07', 'J-08']) {
    assert.match(source, new RegExp(`id: '${journeyId}'`));
  }
  for (const viewport of [
    "width: 1440, height: 1000",
    "width: 1280, height: 800",
    "width: 1024, height: 768",
    "width: 768, height: 1024",
    "width: 390, height: 844",
  ]) {
    assert.match(source, new RegExp(viewport.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  }
  assert.match(source, /for \(const journey of journeyEvidenceCases\)/);
  assert.match(source, /for \(const profile of viewportEvidenceMatrix\)/);
});

test('every matrix cell executes a keyboard task, overflow, axe, screenshot, and task trace', async () => {
  const source = await readFile(matrixUrl, 'utf8');

  assert.match(source, /activateWithKeyboard/);
  assert.match(source, /expectNoFullPageHorizontalOverflow\(page\)/);
  assert.match(source, /expectNoWcagAaViolations\(page\)/);
  assert.match(source, /page\.screenshot\(\{ fullPage: true \}\)/);
  assert.match(source, /journey-task-trace/);
  assert.match(source, /expectAllVisibleCriticalTouchTargets/);
  assert.match(source, /expectDialogFitsViewport/);
  assert.match(source, /installVisualMutationFence/);
  assert.match(source, /blockedbyclient/);
});

test('axe excludes only Fluent UI Tabster focus sentinels without disabling accessibility rules', async () => {
  const source = await readFile(accessibilityUrl, 'utf8');

  assert.match(source, /\[data-tabster-dummy\]\[aria-hidden="true"\]/);
  assert.match(source, /\.exclude\(TABSTER_FOCUS_SENTINEL\)/);
  assert.doesNotMatch(source, /disableRules|\.withRules\(/);
});

test('task traces describe the executed navigation and only claim profile-specific evidence', async () => {
  const source = await readFile(matrixUrl, 'utf8');
  const runJ08 = source.slice(
    source.indexOf('async function runJ08'),
    source.indexOf('const journeyEvidenceCases'),
  );
  const j08Case = source.slice(
    source.indexOf("id: 'J-08'"),
    source.indexOf('];', source.indexOf("id: 'J-08'")),
  );

  assert.match(runJ08, /page\.goto\(/);
  assert.match(j08Case, /navigationMode: 'preprovisioned-direct'/);
  assert.doesNotMatch(j08Case, /从授权目录进入/);
  for (const assertion of [
    'media-preferences',
    'mutation-fence',
    'reduced-motion',
    'touch-target-44px',
    'dialog-scroll-reachability',
  ]) {
    assert.match(source, new RegExp(`'${assertion}'`));
  }
  assert.match(source, /profile\.reducedMotion === 'reduce'/);
  assert.match(source, /profile\.width === 390/);
  assert.match(source, /task\.dialog/);
});

test('dialog evidence checks real internal scrolling and keyboard reachability of the terminal action', async () => {
  const source = await readFile(matrixUrl, 'utf8');

  assert.match(source, /scrollHeight/);
  assert.match(source, /overflowY/);
  assert.match(source, /primaryControl/);
  assert.match(source, /keyboard\.press\('Tab'/);
  assert.match(source, /toBeFocused\(\)/);
});

test('real deploy browser owns modal focus trap, Escape, backdrop dismissal, and focus restoration', async () => {
  const source = await readFile(roleJourneysUrl, 'utf8');
  const dialogEvidence = source.slice(
    source.indexOf('应急代管 Dialog 支持真实焦点圈'),
    source.indexOf("test('1440x1000", source.indexOf('应急代管 Dialog 支持真实焦点圈')),
  );

  assert.match(dialogEvidence, /const firstFocusable = dialog\.getByLabel\('代管动作'\)/);
  assert.match(dialogEvidence, /const lastFocusable = dialog\.getByRole\('button', \{ name: '取消' \}\)/);
  assert.match(
    dialogEvidence,
    /await lastFocusable\.focus\(\);\s*await page\.keyboard\.press\('Tab'\);\s*await expect\(firstFocusable\)\.toBeFocused\(\);/,
  );
  assert.match(
    dialogEvidence,
    /await firstFocusable\.focus\(\);\s*await page\.keyboard\.press\('Shift\+Tab'\);\s*await expect\(lastFocusable\)\.toBeFocused\(\);/,
  );
  assert.match(
    dialogEvidence,
    /await trigger\.evaluate\(\(element\) => element\.focus\(\)\);\s*await expect\(trigger\)\.not\.toBeFocused\(\);\s*await expect\.poll\(\(\) => dialog\.evaluate\(\(element\) => element\.contains\(document\.activeElement\)\)\)\.toBe\(true\);/,
  );
  assert.match(dialogEvidence, /keyboard\.press\('Escape'\)/);
  assert.match(dialogEvidence, /\.fui-DialogSurface__backdrop/);
  assert.match(dialogEvidence, /backdrop\.click\(\{ position: \{ x: 4, y: 4 \} \}\)/);
  assert.equal((dialogEvidence.match(/expect\(trigger\)\.toBeFocused\(\)/g) || []).length, 2);
  assert.equal((dialogEvidence.match(/expectDialogMotionEvidence\(page, 'reduced'\)/g) || []).length, 1);
  assert.equal((dialogEvidence.match(/expectDialogMotionEvidence\(page, 'animated'\)/g) || []).length, 1);
  assert.match(
    dialogEvidence,
    /emulateMedia\(\{ reducedMotion: 'reduce' \}\)[\s\S]*expectDialogMotionEvidence\(page, 'reduced'\)[\s\S]*emulateMedia\(\{ reducedMotion: 'no-preference' \}\)[\s\S]*expectDialogMotionEvidence\(page, 'animated'\)/,
  );
});

test('runtime Dialog motion follows media changes, settles active motion, and restores default slots', async () => {
  const source = await readFile(dialogMotionRuntimeUrl, 'utf8');
  const updatePreference = source.slice(
    source.indexOf('function applyReducedMotionPreference'),
    source.indexOf("reducedMotionQuery?.addEventListener('change'"),
  );

  assert.match(source, /export let dialogSurfaceMotion/);
  assert.match(source, /export let dialogBackdropMotion/);
  assert.match(source, /function finishRunningDialogMotion/);
  assert.match(source, /animation\.finish\(\)/);
  assert.match(updatePreference, /const motion = !supportsWebAnimations \|\| reduced \? null : undefined/);
  assert.match(updatePreference, /dialogSurfaceMotion = motion;\s*dialogBackdropMotion = motion/);
  assert.match(source, /applyReducedMotionPreference\(event\.matches\)/);
});

test('dialog motion evidence instruments WAAPI before navigation and rejects every positive reduced-motion start', async () => {
  const [matrix, evidence] = await Promise.all([
    readFile(matrixUrl, 'utf8'),
    readFile(dialogMotionEvidenceUrl, 'utf8'),
  ]);
  const executeCell = matrix.slice(
    matrix.indexOf('async function executeJourneyEvidenceCell'),
    matrix.indexOf('// Visual cells must not submit destructive mutations'),
  );
  const installIndex = executeCell.indexOf('installDialogMotionEvidence(page)');
  const profileIndex = executeCell.indexOf('applyEvidenceProfile(page, profile)');
  const journeyIndex = executeCell.indexOf('journey.run(page, profile)');

  assert.notEqual(installIndex, -1);
  assert.ok(installIndex < profileIndex);
  assert.ok(profileIndex < journeyIndex);
  assert.match(evidence, /Element\.prototype\.animate = function instrumentDialogAnimate/);
  assert.match(evidence, /originalAnimate\.call\(this, keyframes, options\)/);
  assert.match(evidence, /target: 'surface' \| 'backdrop'/);
  assert.match(evidence, /positiveStarts/);
  assert.match(
    evidence,
    /expect\(\s*positiveStarts,\s*'reduced-motion Dialog\/backdrop WAAPI must never start with a positive duration',\s*\)\.toEqual\(\[\]\)/,
  );
  assert.match(evidence, /expect\(animatedTargets\)\.toEqual\(expect\.arrayContaining\(\['surface', 'backdrop'\]\)\)/);
});

test('mobile and compact desktop evidence cover all critical targets and responsive master-detail behavior', async () => {
  const source = await readFile(matrixUrl, 'utf8');
  const runJ07 = source.slice(source.indexOf('async function runJ07'), source.indexOf('async function runJ08'));

  assert.match(source, /expectAllVisibleCriticalTouchTargets\(page\)/);
  assert.match(source, /expectResponsiveMasterDetail/);
  assert.match(source, /gridTemplateColumns/);
  assert.match(source, /profile\.width <= 1100/);
  assert.match(source, /master-detail-responsive/);
  assert.match(source, /focus-restoration/);
  assert.match(runJ07, /responsiveMasterDetail/);
  assert.match(runJ07, /masterDetail/);
  assert.match(runJ07, /detail/);
});

test('the mutation fence remains active through cleanup before evidence is attached', async () => {
  const source = await readFile(matrixUrl, 'utf8');
  const executeCell = source.slice(
    source.indexOf('async function executeJourneyEvidenceCell'),
    source.indexOf('// Visual cells must not submit destructive mutations'),
  );
  const cleanupIndex = executeCell.indexOf('task?.cleanup?.()');
  const mutationAssertionIndex = executeCell.lastIndexOf('expectNoUnexpectedMutations()');
  const evidenceAttachmentIndex = executeCell.lastIndexOf('attachJourneyEvidence');

  assert.notEqual(cleanupIndex, -1);
  assert.notEqual(mutationAssertionIndex, -1);
  assert.notEqual(evidenceAttachmentIndex, -1);
  assert.ok(cleanupIndex < mutationAssertionIndex);
  assert.ok(mutationAssertionIndex < evidenceAttachmentIndex);
});

test('themes, forced colors, reduced motion, and per-journey 200 percent evidence are executable', async () => {
  const source = await readFile(matrixUrl, 'utf8');

  assert.match(source, /colorScheme: 'light'/);
  assert.match(source, /colorScheme: 'dark'/);
  assert.match(source, /forcedColors: 'active'/);
  assert.match(source, /reducedMotion: 'reduce'/);
  assert.match(source, /prefers-reduced-motion: reduce/);
  assert.match(source, /activeAnimations/);
  assert.match(source, /durationViolations/);
  assert.match(source, /animationDuration/);
  assert.match(source, /transitionDuration/);
  assert.match(source, /id: '200-percent'/);
  assert.match(source, /for \(const journey of journeyEvidenceCases\)[\s\S]*200%/);
});

test('visual cells do not duplicate destructive semantics and retain the governed persistent proofs', async () => {
  const [matrix, roleJourneys, applicationGovernance] = await Promise.all([
    readFile(matrixUrl, 'utf8'),
    readFile(roleJourneysUrl, 'utf8'),
    readFile(applicationGovernanceUrl, 'utf8'),
  ]);

  assert.match(matrix, /single shared empty J-08 session/i);
  assert.match(matrix, /visual cells must not submit destructive mutations/i);
  assert.match(matrix, /J-08 visual matrix session catalog/);
  assert.match(matrix, /candidate\.requiresNewSession/);
  assert.match(matrix, /J-08 reusable visual session history/);
  assert.match(matrix, /messages\.length === 0/);
  assert.doesNotMatch(matrix, /确认替换权限.*click/s);
  assert.doesNotMatch(matrix, /确认并发布.*click/s);
  assert.doesNotMatch(matrix, /确认并回滚.*click/s);
  assert.match(roleJourneys, /runCandidateMutation/);
  assert.match(roleJourneys, /restoreTenantName/);
  assert.match(roleJourneys, /restoreRolePermissions/);
  assert.match(roleJourneys, /completeBusinessChatJourney/);
  assert.match(applicationGovernance, /候选申请人经批准后仅进入新发布 v2，回滚 v1 生成 v3 后再次失去访问/);
});
