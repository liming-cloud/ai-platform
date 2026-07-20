import '@testing-library/jest-dom/vitest';
import type { DialogProps } from '@fluentui/react-components';
import { disposeTabster, getTabster } from 'tabster';
import { afterAll, vi } from 'vitest';

// jsdom cannot deterministically arbitrate overlapping Tabster modalizers while
// controlled dialogs switch state in the same workflow. Unit tests retain the
// real Fluent Dialog implementation but run it as non-modal, so they own form,
// error, retry, and submission semantics. Deploy Playwright journeys own the
// real modal focus trap, backdrop isolation, Escape behavior, and portal motion.
vi.mock('@fluentui/react-components', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@fluentui/react-components')>();
  const React = await import('react');
  const UnitTestDialog = (props: DialogProps) => React.createElement(actual.Dialog, {
    ...props,
    modalType: 'non-modal',
  });
  UnitTestDialog.displayName = 'UnitTestDialog';
  return { ...actual, Dialog: UnitTestDialog };
});

// Fluent creates a document-scoped focus manager. Test suites that exercise
// many dialogs mount portals in an isolated per-test host; dispose the shared
// Tabster singleton only after the worker has unmounted the complete file.
afterAll(() => {
  const tabster = getTabster(window);
  if (tabster) disposeTabster(tabster, true);
});

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  }),
});

class ResizeObserverMock {
  observe() {
    return undefined;
  }

  unobserve() {
    return undefined;
  }

  disconnect() {
    return undefined;
  }
}

Object.defineProperty(window, 'ResizeObserver', {
  writable: true,
  value: ResizeObserverMock,
});

Object.defineProperty(window, 'scrollTo', {
  writable: true,
  value: () => undefined,
});
