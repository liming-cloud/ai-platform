import { defineConfig, devices } from '@playwright/test';

declare const process: { readonly env: Readonly<Record<string, string | undefined>> };

const baseURL = process.env.E2E_BASE_URL || 'http://127.0.0.1:5173';
const outputDir = process.env.E2E_OUTPUT_DIR || 'test-results';
const jsonOutput = process.env.E2E_JSON_OUTPUT;

export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.ts',
  // Deploy acceptance reuses three seeded identities and shared product data.
  // Serialize journeys so evidence is deterministic and login/session state is not contended.
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: jsonOutput
    ? [['list'], ['json', { outputFile: jsonOutput }]]
    : [['list']],
  outputDir,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL,
    colorScheme: 'light',
    locale: 'zh-CN',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  // Browser tests target an already-running deploy baseline and never start a local server.
});
