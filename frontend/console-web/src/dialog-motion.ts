const supportsWebAnimations = typeof Element !== 'undefined'
  && typeof Element.prototype.animate === 'function';
const reducedMotionQuery = typeof window !== 'undefined'
  && typeof window.matchMedia === 'function'
  ? window.matchMedia('(prefers-reduced-motion: reduce)')
  : undefined;
type DialogMotionSlot = null | undefined;

export let dialogSurfaceMotion: DialogMotionSlot = !supportsWebAnimations || reducedMotionQuery?.matches
  ? null
  : undefined;
export let dialogBackdropMotion: DialogMotionSlot = dialogSurfaceMotion;

function finishRunningDialogMotion() {
  if (typeof document === 'undefined') return;
  document.querySelectorAll<HTMLElement>('.fui-DialogSurface, .fui-DialogSurface__backdrop')
    .forEach((element) => {
      if (typeof element.getAnimations !== 'function') return;
      element.getAnimations().forEach((animation) => {
        try {
          animation.finish();
        } catch {
          animation.cancel();
        }
      });
    });
}

function applyReducedMotionPreference(reduced: boolean) {
  const motion = !supportsWebAnimations || reduced ? null : undefined;
  dialogSurfaceMotion = motion;
  dialogBackdropMotion = motion;
  if (reduced) finishRunningDialogMotion();
}

reducedMotionQuery?.addEventListener('change', (event) => {
  applyReducedMotionPreference(event.matches);
});
