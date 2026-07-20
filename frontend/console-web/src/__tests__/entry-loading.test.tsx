import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';

const protectedPages = vi.hoisted(() => ({ loaded: false }));

vi.mock('../pages', () => {
  protectedPages.loaded = true;
  return {
    ErrorPage: () => null,
    PageRouter: () => null,
  };
});

import App from '../App';

beforeEach(() => {
  localStorage.clear();
  window.history.replaceState({}, '', '/login');
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

it('does not evaluate protected page modules while rendering the login entry', async () => {
  render(<App />);

  expect(await screen.findByText('登录工作空间')).toBeInTheDocument();
  expect(protectedPages.loaded).toBe(false);
});
