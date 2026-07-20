import { afterEach, describe, expect, it, vi } from 'vitest';

const originalAnimateDescriptor = Object.getOwnPropertyDescriptor(Element.prototype, 'animate');

afterEach(() => {
  vi.resetModules();
  vi.unstubAllGlobals();
  document.body.replaceChildren();
  if (originalAnimateDescriptor) {
    Object.defineProperty(Element.prototype, 'animate', originalAnimateDescriptor);
  } else {
    Reflect.deleteProperty(Element.prototype, 'animate');
  }
});

describe('dialog motion preference', () => {
  it('switches both motion slots at runtime and finishes motion already running under reduce', async () => {
    const listeners = new Set<(event: MediaQueryListEvent) => void>();
    let matches = false;
    const mediaQuery = {
      get matches() {
        return matches;
      },
      media: '(prefers-reduced-motion: reduce)',
      onchange: null,
      addEventListener: (_type: 'change', listener: (event: MediaQueryListEvent) => void) => {
        listeners.add(listener);
      },
      removeEventListener: (_type: 'change', listener: (event: MediaQueryListEvent) => void) => {
        listeners.delete(listener);
      },
      addListener: () => undefined,
      removeListener: () => undefined,
      dispatchEvent: () => false,
    } as unknown as MediaQueryList;
    vi.stubGlobal('matchMedia', vi.fn(() => mediaQuery));
    Object.defineProperty(Element.prototype, 'animate', {
      configurable: true,
      value: vi.fn(),
    });

    const finish = vi.fn();
    const surface = document.createElement('div');
    surface.className = 'fui-DialogSurface';
    Object.defineProperty(surface, 'getAnimations', {
      configurable: true,
      value: () => [{ finish }],
    });
    document.body.append(surface);

    const motion = await import('../dialog-motion');
    expect(motion.dialogSurfaceMotion).toBeUndefined();
    expect(motion.dialogBackdropMotion).toBeUndefined();

    matches = true;
    listeners.forEach((listener) => listener({ matches: true } as MediaQueryListEvent));
    expect(motion.dialogSurfaceMotion).toBeNull();
    expect(motion.dialogBackdropMotion).toBeNull();
    expect(finish).toHaveBeenCalledOnce();

    matches = false;
    listeners.forEach((listener) => listener({ matches: false } as MediaQueryListEvent));
    expect(motion.dialogSurfaceMotion).toBeUndefined();
    expect(motion.dialogBackdropMotion).toBeUndefined();
  });
});
